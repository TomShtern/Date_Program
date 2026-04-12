package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.DailyLimitService;
import datingapp.core.matching.DailyPickService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for MatchingService using in-memory mock storage. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class MatchingServiceTest {

    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Users userStorage;
    private TestStorages.Undos undoStorage;
    private CandidateFinder candidateFinder;
    private MatchingService matchingService;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        userStorage = new TestStorages.Users();
        undoStorage = new TestStorages.Undos();
        candidateFinder = new CandidateFinder(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                AppConfig.defaults().safety().userTimeZone());
        RecommendationService dailyService =
                new RecommendationService(alwaysAllowDailyLimitService(), noDailyPickService(), noStandoutService());
        matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .undoService(new UndoService(interactionStorage, undoStorage, AppConfig.defaults()))
                .dailyService(dailyService)
                .candidateFinder(candidateFinder)
                .build();
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("six-argument constructor builds a default candidate finder")
    void sixArgumentConstructorBuildsDefaultCandidateFinder() {
        RecommendationService dailyService =
                new RecommendationService(alwaysAllowDailyLimitService(), noDailyPickService(), noStandoutService());

        assertDoesNotThrow(() -> new MatchingService(
                interactionStorage,
                trustSafetyStorage,
                userStorage,
                null,
                new UndoService(interactionStorage, undoStorage, AppConfig.defaults()),
                dailyService));
    }

    @Nested
    @DisplayName("Recording Likes")
    class LikeProcessing {

        @Test
        @DisplayName("First like saves without creating match")
        void firstLikeDoesNotCreateMatch() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            userStorage.save(activeUser(alice, "Alice"));
            userStorage.save(activeUser(bob, "Bob"));

            Like like = Like.create(alice, bob, Like.Direction.LIKE);
            MatchingService.RecordLikeOutcome result = matchingService.recordLike(like);

            assertTrue(result.persisted(), "First like should be recorded");
            assertTrue(result.match().isEmpty(), "First like should not create match");
            assertTrue(result.like().isPresent(), "Persisted outcome should expose the recorded like");
            assertTrue(interactionStorage.exists(alice, bob), "Like should be saved");
        }

        @Test
        @DisplayName(
                "recordLike invalidates cached candidates outside the user lock without recording metrics directly")
        void recordLikeInvalidatesCachesOutsideTheUserLockWithoutRecordingMetricsDirectly() {
            TransactionInterceptionUsers lockAwareUsers = new TransactionInterceptionUsers();
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            lockAwareUsers.save(activeUser(alice, "Alice"));
            lockAwareUsers.save(activeUser(bob, "Bob"));

            TrackingCandidateFinder trackingCandidateFinder =
                    new TrackingCandidateFinder(lockAwareUsers, interactionStorage, trustSafetyStorage);
            CountingActivityMetricsService activityMetricsService =
                    new CountingActivityMetricsService(lockAwareUsers, interactionStorage, trustSafetyStorage);
            MatchingService service = MatchingService.builder()
                    .interactionStorage(interactionStorage)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(lockAwareUsers)
                    .activityMetricsService(activityMetricsService)
                    .undoService(new UndoService(interactionStorage, undoStorage, AppConfig.defaults()))
                    .dailyService(new RecommendationService(
                            alwaysAllowDailyLimitService(), noDailyPickService(), noStandoutService()))
                    .candidateFinder(trackingCandidateFinder)
                    .build();

            MatchingService.RecordLikeOutcome result = service.recordLike(Like.create(alice, bob, Like.Direction.PASS));

            assertTrue(result.persisted(), "Pass should persist when allowed");
            assertTrue(result.match().isEmpty(), "Pass should never create match");
            assertEquals(0, activityMetricsService.recordSwipeCount());
            assertEquals(Set.of(alice, bob), trackingCandidateFinder.invalidatedUserIds());
        }

        @Test
        @DisplayName("recordLike rejects blocked users without persisting")
        void recordLikeRejectsBlockedUsersWithoutPersisting() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            userStorage.save(activeUser(alice, "Alice"));
            userStorage.save(activeUser(bob, "Bob"));
            trustSafetyStorage.save(Block.create(alice, bob));

            MatchingService.RecordLikeOutcome result =
                    matchingService.recordLike(Like.create(alice, bob, Like.Direction.LIKE));

            assertTrue(result.rejected(), "Blocked likes should be rejected");
            assertEquals(
                    "Cannot swipe on a blocked user.", result.rejectionMessage().orElseThrow());
            assertTrue(result.like().isEmpty(), "Rejected outcome should not expose a persisted like");
            assertFalse(interactionStorage.exists(alice, bob), "Blocked like should not be saved");
        }

        @Test
        @DisplayName("Pass does not create match")
        void passDoesNotCreateMatch() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            userStorage.save(activeUser(alice, "Alice"));
            userStorage.save(activeUser(bob, "Bob"));

            // Bob likes Alice first
            interactionStorage.save(Like.create(bob, alice, Like.Direction.LIKE));

            // Alice passes Bob
            Like pass = Like.create(alice, bob, Like.Direction.PASS);
            MatchingService.RecordLikeOutcome result = matchingService.recordLike(pass);

            assertTrue(result.persisted(), "Pass should persist when allowed");
            assertTrue(result.match().isEmpty(), "Pass should never create match");
        }

        @Test
        @DisplayName("Mutual likes create match")
        void mutualLikesCreateMatch() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            userStorage.save(activeUser(alice, "Alice"));
            userStorage.save(activeUser(bob, "Bob"));

            // Alice likes Bob
            Like aliceLikesBob = Like.create(alice, bob, Like.Direction.LIKE);
            matchingService.recordLike(aliceLikesBob);

            // Bob likes Alice back
            Like bobLikesAlice = Like.create(bob, alice, Like.Direction.LIKE);
            MatchingService.RecordLikeOutcome result = matchingService.recordLike(bobLikesAlice);

            assertTrue(result.persisted(), "Mutual like should persist");
            assertTrue(result.match().isPresent(), "Mutual likes should create match");
            Match match = result.match().orElseThrow();
            assertTrue(match.involves(alice), "Match should involve Alice");
            assertTrue(match.involves(bob), "Match should involve Bob");
            assertTrue(interactionStorage.exists(match.getId()), "Match should be saved");
        }

        @Test
        @DisplayName("Duplicate like is ignored")
        void duplicateLikeIsIgnored() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            userStorage.save(activeUser(alice, "Alice"));
            userStorage.save(activeUser(bob, "Bob"));

            Like like1 = Like.create(alice, bob, Like.Direction.LIKE);
            Like like2 = Like.create(alice, bob, Like.Direction.LIKE);

            MatchingService.RecordLikeOutcome first = matchingService.recordLike(like1);
            MatchingService.RecordLikeOutcome result = matchingService.recordLike(like2);

            assertTrue(first.persisted(), "First like should persist");
            assertFalse(result.persisted(), "Duplicate like should be an idempotent no-op");
            assertFalse(result.rejected(), "Duplicate like should not be treated as a rejection");
            assertTrue(result.like().isPresent(), "Duplicate outcome should expose the persisted like");
            assertEquals(
                    first.like().orElseThrow().id(), result.like().orElseThrow().id());
            assertTrue(result.match().isEmpty(), "Duplicate like should not create a match");
        }

        @Test
        @DisplayName("Match is not duplicated on repeated mutual likes")
        void matchNotDuplicated() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            userStorage.save(activeUser(alice, "Alice"));
            userStorage.save(activeUser(bob, "Bob"));

            // First mutual like
            matchingService.recordLike(Like.create(alice, bob, Like.Direction.LIKE));
            matchingService.recordLike(Like.create(bob, alice, Like.Direction.LIKE));

            // Try to re-like (should be ignored because like already exists)
            MatchingService.RecordLikeOutcome result =
                    matchingService.recordLike(Like.create(alice, bob, Like.Direction.LIKE));

            assertFalse(result.persisted(), "Re-like should stay idempotent");
            assertTrue(result.match().isEmpty(), "Re-like should not create another match");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Like-Pass-Like sequence does not create match")
        void likePassLikeNoMatch() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            userStorage.save(activeUser(alice, "Alice"));
            userStorage.save(activeUser(bob, "Bob"));

            // Alice likes Bob
            matchingService.recordLike(Like.create(alice, bob, Like.Direction.LIKE));

            // Bob passes Alice
            matchingService.recordLike(Like.create(bob, alice, Like.Direction.PASS));

            // Since there's no way to "re-swipe" in current model,
            // Bob already passed so no match possible
            assertFalse(interactionStorage.exists(Match.generateId(alice, bob)), "No match should exist after pass");
        }

        @Test
        @DisplayName("Order of mutual likes does not matter")
        void orderDoesNotMatter() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            userStorage.save(activeUser(alice, "Alice"));
            userStorage.save(activeUser(bob, "Bob"));

            // Bob likes Alice first
            matchingService.recordLike(Like.create(bob, alice, Like.Direction.LIKE));

            // Alice likes Bob back
            MatchingService.RecordLikeOutcome result =
                    matchingService.recordLike(Like.create(alice, bob, Like.Direction.LIKE));

            assertTrue(result.match().isPresent(), "Order should not matter for matching");
        }
    }

    @Nested
    @DisplayName("Process Swipe Guard")
    class ProcessSwipeGuard {

        @Test
        @DisplayName("processSwipe validates current user before config check")
        void processSwipeValidatesCurrentUserBeforeConfigCheck() {
            User candidate = User.StorageBuilder.create(UUID.randomUUID(), "Bob", AppClock.now())
                    .state(UserState.ACTIVE)
                    .build();

            NullPointerException error =
                    assertThrows(NullPointerException.class, () -> matchingService.processSwipe(null, candidate, true));

            assertEquals("currentUser cannot be null", error.getMessage());
        }

        @Test
        @DisplayName("processSwipe validates candidate before config check")
        void processSwipeValidatesCandidateBeforeConfigCheck() {
            User user = User.StorageBuilder.create(UUID.randomUUID(), "Alice", AppClock.now())
                    .state(UserState.ACTIVE)
                    .build();

            NullPointerException error =
                    assertThrows(NullPointerException.class, () -> matchingService.processSwipe(user, null, true));

            assertEquals("candidate cannot be null", error.getMessage());
        }

        @Test
        @DisplayName("processSwipe rejects paused current user")
        void processSwipeRejectsPausedCurrentUser() {
            User pausedUser = User.StorageBuilder.create(UUID.randomUUID(), "PausedAlice", AppClock.now())
                    .state(UserState.PAUSED)
                    .build();
            User candidate = User.StorageBuilder.create(UUID.randomUUID(), "Bob", AppClock.now())
                    .state(UserState.ACTIVE)
                    .build();

            MatchingService.SwipeResult result = matchingService.processSwipe(pausedUser, candidate, true);

            assertFalse(result.success());
            assertEquals("Current user must be ACTIVE to swipe.", result.message());
        }

        @Test
        @DisplayName("processSwipe rejects non-active candidate")
        void processSwipeRejectsNonActiveCandidate() {
            User activeUser = User.StorageBuilder.create(UUID.randomUUID(), "Alice", AppClock.now())
                    .state(UserState.ACTIVE)
                    .build();
            User pausedCandidate = User.StorageBuilder.create(UUID.randomUUID(), "PausedBob", AppClock.now())
                    .state(UserState.PAUSED)
                    .build();

            MatchingService.SwipeResult result = matchingService.processSwipe(activeUser, pausedCandidate, true);

            assertFalse(result.success());
            assertEquals("Candidate must be ACTIVE to receive swipes.", result.message());
        }

        @Test
        @DisplayName("processSwipe rejects blocked candidates")
        void processSwipeRejectsBlockedCandidates() {
            User activeUser = User.StorageBuilder.create(UUID.randomUUID(), "Alice", AppClock.now())
                    .state(UserState.ACTIVE)
                    .build();
            User blockedCandidate = User.StorageBuilder.create(UUID.randomUUID(), "BlockedBob", AppClock.now())
                    .state(UserState.ACTIVE)
                    .build();
            trustSafetyStorage.save(Block.create(activeUser.getId(), blockedCandidate.getId()));

            MatchingService.SwipeResult result = matchingService.processSwipe(activeUser, blockedCandidate, true);

            assertFalse(result.success());
            assertEquals("Cannot swipe on a blocked user.", result.message());
            assertFalse(interactionStorage.exists(activeUser.getId(), blockedCandidate.getId()));
        }

        @Test
        @DisplayName("duplicate processSwipe is idempotent and preserves undo state")
        void duplicateProcessSwipeIsIdempotentAndPreservesUndoState() {
            User activeUser = activeUser(UUID.randomUUID(), "Alice");
            User candidate = activeUser(UUID.randomUUID(), "Bob");
            userStorage.save(activeUser);
            userStorage.save(candidate);

            MatchingService.SwipeResult first = matchingService.processSwipe(activeUser, candidate, true);
            assertTrue(first.success());
            UUID firstLikeId = first.like().id();

            var undoAfterFirst = undoStorage.findByUserId(activeUser.getId()).orElseThrow();
            assertEquals(firstLikeId, undoAfterFirst.like().id());

            MatchingService.SwipeResult duplicate = matchingService.processSwipe(activeUser, candidate, true);
            assertTrue(duplicate.success());
            assertEquals("Already swiped.", duplicate.message());
            assertNull(duplicate.like());
            assertEquals(1, interactionStorage.countByDirection(activeUser.getId(), Like.Direction.LIKE));

            var undoAfterDuplicate =
                    undoStorage.findByUserId(activeUser.getId()).orElseThrow();
            assertEquals(firstLikeId, undoAfterDuplicate.like().id());
        }

        @Test
        @DisplayName("MatchingService builder fails fast when required services are missing")
        void buildFailsFastWithoutRequiredServices() {
            RecommendationService dailyService = new RecommendationService(
                    alwaysAllowDailyLimitService(), noDailyPickService(), noStandoutService());
            UndoService undoService =
                    new UndoService(interactionStorage, new TestStorages.Undos(), AppConfig.defaults());

            MatchingService.Builder missingUndoServiceBuilder = MatchingService.builder()
                    .interactionStorage(interactionStorage)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(userStorage)
                    .dailyService(dailyService)
                    .candidateFinder(candidateFinder);
            NullPointerException undoError = assertThrows(NullPointerException.class, missingUndoServiceBuilder::build);
            assertEquals("undoService cannot be null", undoError.getMessage());

            MatchingService.Builder missingDailyServiceBuilder = MatchingService.builder()
                    .interactionStorage(interactionStorage)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(userStorage)
                    .undoService(undoService)
                    .candidateFinder(candidateFinder);
            NullPointerException dailyError =
                    assertThrows(NullPointerException.class, missingDailyServiceBuilder::build);
            assertEquals("dailyService cannot be null", dailyError.getMessage());

            MatchingService.Builder missingCandidateFinderBuilder = MatchingService.builder()
                    .interactionStorage(interactionStorage)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(userStorage)
                    .undoService(undoService)
                    .dailyService(dailyService);
            NullPointerException finderError =
                    assertThrows(NullPointerException.class, missingCandidateFinderBuilder::build);
            assertEquals("candidateFinder cannot be null", finderError.getMessage());
        }

        // =====================================================================
        // Phase 1a Candidate Truthfulness: Failing Transactional Safety Tests
        // =====================================================================

        @Test
        @DisplayName("Phase 1a: processSwipe should reject if current user pauses between snapshot and commit")
        void phase1aRejectWhenCurrentUserPausesBetweenSnapshotAndCommit() {
            User activeUser = activeUser(UUID.randomUUID(), "Alice");
            User candidate = activeUser(UUID.randomUUID(), "Bob");
            userStorage.save(activeUser);
            userStorage.save(candidate);

            // Use slow storage that allows us to pause user between snapshot and commit
            TransactionInterceptionUsers txnUsers = new TransactionInterceptionUsers();
            txnUsers.save(activeUser);
            txnUsers.save(candidate);

            MatchingService txnService = MatchingService.builder()
                    .interactionStorage(interactionStorage)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(txnUsers)
                    .undoService(new UndoService(interactionStorage, new TestStorages.Undos(), AppConfig.defaults()))
                    .dailyService(new RecommendationService(
                            alwaysAllowDailyLimitService(), noDailyPickService(), noStandoutService()))
                    .candidateFinder(candidateFinder)
                    .build();

            txnUsers.setPauseHook(activeUser::pause);

            MatchingService.SwipeResult result = txnService.processSwipe(activeUser, candidate, true);

            // The swipe should either be rejected or not persisted when user pauses
            assertFalse(
                    result.success() && interactionStorage.exists(activeUser.getId(), candidate.getId()),
                    "Swipe should not succeed and persist when current user pauses between snapshot and commit");
        }

        @Test
        @DisplayName("Phase 1a: processSwipe should reject if candidate pauses between snapshot and commit")
        void phase1aRejectWhenCandidatePausesBetweenSnapshotAndCommit() {
            User activeUser = activeUser(UUID.randomUUID(), "Alice");
            User candidate = activeUser(UUID.randomUUID(), "Bob");
            userStorage.save(activeUser);
            userStorage.save(candidate);

            TransactionInterceptionUsers txnUsers = new TransactionInterceptionUsers();
            txnUsers.save(activeUser);
            txnUsers.save(candidate);

            MatchingService txnService = MatchingService.builder()
                    .interactionStorage(interactionStorage)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(txnUsers)
                    .undoService(new UndoService(interactionStorage, new TestStorages.Undos(), AppConfig.defaults()))
                    .dailyService(new RecommendationService(
                            alwaysAllowDailyLimitService(), noDailyPickService(), noStandoutService()))
                    .candidateFinder(candidateFinder)
                    .build();

            txnUsers.setPauseHook(candidate::pause);

            MatchingService.SwipeResult result = txnService.processSwipe(activeUser, candidate, true);

            // The swipe should either be rejected or not persisted when candidate pauses
            assertFalse(
                    result.success() && interactionStorage.exists(activeUser.getId(), candidate.getId()),
                    "Swipe should not succeed and persist when candidate pauses between snapshot and commit");
        }

        @Test
        @DisplayName("Phase 1a: processSwipe should reject if block created between snapshot and commit")
        void phase1aRejectWhenBlockCreatedBetweenSnapshotAndCommit() {
            User activeUser = activeUser(UUID.randomUUID(), "Alice");
            User candidate = activeUser(UUID.randomUUID(), "Bob");
            userStorage.save(activeUser);
            userStorage.save(candidate);

            TransactionInterceptionUsers txnUsers = new TransactionInterceptionUsers();
            txnUsers.save(activeUser);
            txnUsers.save(candidate);

            MatchingService txnService = MatchingService.builder()
                    .interactionStorage(interactionStorage)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(txnUsers)
                    .undoService(new UndoService(interactionStorage, new TestStorages.Undos(), AppConfig.defaults()))
                    .dailyService(new RecommendationService(
                            alwaysAllowDailyLimitService(), noDailyPickService(), noStandoutService()))
                    .candidateFinder(new CandidateFinder(
                            txnUsers,
                            interactionStorage,
                            trustSafetyStorage,
                            AppConfig.defaults().safety().userTimeZone()))
                    .build();

            txnUsers.setPauseHook(() -> trustSafetyStorage.save(Block.create(activeUser.getId(), candidate.getId())));

            MatchingService.SwipeResult result = txnService.processSwipe(activeUser, candidate, true);

            // The swipe should either be rejected or not persisted when block created between snapshot and commit
            assertFalse(
                    result.success() && interactionStorage.exists(activeUser.getId(), candidate.getId()),
                    "Swipe should not succeed and persist when block created between snapshot and commit");
        }
    }

    @Nested
    @DisplayName("Concurrent swipe safety")
    class ConcurrentSwipeSafety {

        @Test
        @DisplayName("concurrent same-pair swipes allow only one success path")
        void concurrentSamePairSwipesAllowOnlyOneSuccessPath() throws Exception {
            SlowInteractions slowInteractions = new SlowInteractions();
            TestStorages.Undos undos = new TestStorages.Undos();
            RecommendationService recommendationService = new RecommendationService(
                    alwaysAllowDailyLimitService(), noDailyPickService(), noStandoutService());
            MatchingService guardedService = MatchingService.builder()
                    .interactionStorage(slowInteractions)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(userStorage)
                    .undoService(new UndoService(slowInteractions, undos, AppConfig.defaults()))
                    .dailyService(recommendationService)
                    .candidateFinder(candidateFinder)
                    .build();

            User alice = activeUser(UUID.randomUUID(), "Alice");
            User bob = activeUser(UUID.randomUUID(), "Bob");
            userStorage.save(alice);
            userStorage.save(bob);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<MatchingService.SwipeResult> first = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return guardedService.processSwipe(alice, bob, true);
                });
                Future<MatchingService.SwipeResult> second = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return guardedService.processSwipe(alice, bob, true);
                });

                assertTrue(ready.await(2, TimeUnit.SECONDS));
                start.countDown();

                List<MatchingService.SwipeResult> results =
                        List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

                assertEquals(
                        1,
                        results.stream()
                                .filter(MatchingService.SwipeResult::success)
                                .count());
                assertEquals(1, slowInteractions.countByDirection(alice.getId(), Like.Direction.LIKE));
                assertTrue(
                        results.stream()
                                .anyMatch(result ->
                                        !result.success() && result.message().contains("already in progress")),
                        "One concurrent same-pair swipe should be rejected by the service-level guard");
            } finally {
                executor.shutdownNow();
                executor.awaitTermination(2, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("concurrent likes to different candidates still honor the daily limit")
        void concurrentLikesToDifferentCandidatesStillHonorTheDailyLimit() throws Exception {
            SlowInteractions slowInteractions = new SlowInteractions();
            TestStorages.Undos undos = new TestStorages.Undos();
            RecommendationService recommendationService = new RecommendationService(
                    oneLikePerDayLimitService(slowInteractions), noDailyPickService(), noStandoutService());
            MatchingService guardedService = MatchingService.builder()
                    .interactionStorage(slowInteractions)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(userStorage)
                    .undoService(new UndoService(slowInteractions, undos, AppConfig.defaults()))
                    .dailyService(recommendationService)
                    .candidateFinder(candidateFinder)
                    .build();

            User alice = activeUser(UUID.randomUUID(), "Alice");
            User bob = activeUser(UUID.randomUUID(), "Bob");
            User carol = activeUser(UUID.randomUUID(), "Carol");
            userStorage.save(alice);
            userStorage.save(bob);
            userStorage.save(carol);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<MatchingService.SwipeResult> first = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return guardedService.processSwipe(alice, bob, true);
                });
                Future<MatchingService.SwipeResult> second = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return guardedService.processSwipe(alice, carol, true);
                });

                assertTrue(ready.await(2, TimeUnit.SECONDS));
                start.countDown();

                List<MatchingService.SwipeResult> results =
                        List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

                assertEquals(
                        1,
                        results.stream()
                                .filter(MatchingService.SwipeResult::success)
                                .count());
                assertEquals(1, slowInteractions.countByDirection(alice.getId(), Like.Direction.LIKE));
                assertTrue(
                        results.stream()
                                .anyMatch(result ->
                                        !result.success() && result.message().contains("limit")),
                        "One swipe should be rejected by the daily limit guard");
            } finally {
                executor.shutdownNow();
                executor.awaitTermination(2, TimeUnit.SECONDS);
            }
        }
    }

    @Nested
    @DisplayName("Pending liker filtering")
    class PendingLikerFiltering {

        @Test
        @DisplayName("excludes users already matched including ended matches")
        void excludesAlreadyMatchedUsersIncludingEndedMatches() {
            UUID currentUserId = UUID.randomUUID();
            UUID activeMatchedLikerId = UUID.randomUUID();
            UUID endedMatchedLikerId = UUID.randomUUID();
            UUID pendingLikerId = UUID.randomUUID();

            interactionStorage.save(Like.create(activeMatchedLikerId, currentUserId, Like.Direction.LIKE));
            interactionStorage.save(Like.create(endedMatchedLikerId, currentUserId, Like.Direction.LIKE));
            interactionStorage.save(Like.create(pendingLikerId, currentUserId, Like.Direction.LIKE));

            Match activeMatch = Match.create(currentUserId, activeMatchedLikerId);
            interactionStorage.save(activeMatch);

            Match endedMatch = Match.create(currentUserId, endedMatchedLikerId);
            endedMatch.unmatch(currentUserId);
            interactionStorage.save(endedMatch);

            userStorage.save(activeUser(activeMatchedLikerId, "ActiveMatched"));
            userStorage.save(activeUser(endedMatchedLikerId, "EndedMatched"));
            userStorage.save(activeUser(pendingLikerId, "Pending"));

            List<User> pendingLikers = matchingService.findPendingLikers(currentUserId);

            assertEquals(1, pendingLikers.size());
            assertEquals(pendingLikerId, pendingLikers.getFirst().getId());
        }
    }

    private static User activeUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .build();
    }

    private static DailyLimitService alwaysAllowDailyLimitService() {
        return new DailyLimitService() {
            @Override
            public boolean canLike(UUID userId) {
                return true;
            }

            @Override
            public boolean canSuperLike(UUID userId) {
                return true;
            }

            @Override
            public boolean canPass(UUID userId) {
                return true;
            }

            @Override
            public DailyStatus getStatus(UUID userId) {
                return new DailyStatus(0, 999, 0, 999, 0, 999, AppClock.today(), AppClock.now());
            }

            @Override
            public Duration getTimeUntilReset() {
                return Duration.ZERO;
            }
        };
    }

    private static DailyLimitService oneLikePerDayLimitService(TestStorages.Interactions interactionStorage) {
        return new DailyLimitService() {
            @Override
            public boolean canLike(UUID userId) {
                return interactionStorage.countByDirection(userId, Like.Direction.LIKE) < 1;
            }

            @Override
            public boolean canSuperLike(UUID userId) {
                return true;
            }

            @Override
            public boolean canPass(UUID userId) {
                return true;
            }

            @Override
            public DailyStatus getStatus(UUID userId) {
                return new DailyStatus(0, 1, 0, 999, 0, 999, AppClock.today(), AppClock.now());
            }

            @Override
            public Duration getTimeUntilReset() {
                return Duration.ZERO;
            }
        };
    }

    private static DailyPickService noDailyPickService() {
        return new DailyPickService() {
            @Override
            public Optional<DailyPick> getDailyPick(User seeker) {
                return Optional.empty();
            }

            @Override
            public boolean hasViewedDailyPick(UUID userId) {
                return false;
            }

            @Override
            public void markDailyPickViewed(UUID userId) {
                // No-op test stub: C5 exercises processSwipe only and never touches daily-pick state.
            }

            @Override
            public int cleanupOldDailyPickViews(java.time.LocalDate before) {
                return 0;
            }
        };
    }

    private static StandoutService noStandoutService() {
        return new StandoutService() {
            @Override
            public Result getStandouts(User seeker) {
                return Result.empty("none");
            }

            @Override
            public void markInteracted(UUID seekerId, UUID standoutUserId) {
                // No-op test stub: standout side effects are irrelevant to the processSwipe concurrency guard.
            }

            @Override
            public java.util.Map<UUID, User> resolveUsers(List<Standout> standouts) {
                return java.util.Map.of();
            }
        };
    }

    private static final class TransactionInterceptionUsers extends TestStorages.Users {
        private Runnable pauseHook;
        private final AtomicInteger lockDepth = new AtomicInteger();

        void setPauseHook(Runnable hook) {
            this.pauseHook = hook;
        }

        boolean isUserLockHeld() {
            return lockDepth.get() > 0;
        }

        @Override
        public synchronized void executeWithUserLock(UUID userId, Runnable operation) {
            lockDepth.incrementAndGet();
            if (pauseHook != null) {
                pauseHook.run();
                pauseHook = null;
            }
            try {
                super.executeWithUserLock(userId, operation);
            } finally {
                lockDepth.decrementAndGet();
            }
        }
    }

    private static final class TrackingCandidateFinder extends CandidateFinder {
        private final TransactionInterceptionUsers userStorage;
        private final Set<UUID> invalidatedUserIds = new HashSet<>();

        private TrackingCandidateFinder(
                TransactionInterceptionUsers userStorage,
                TestStorages.Interactions interactionStorage,
                TestStorages.TrustSafety trustSafetyStorage) {
            super(
                    userStorage,
                    interactionStorage,
                    trustSafetyStorage,
                    AppClock.clock().getZone());
            this.userStorage = userStorage;
        }

        @Override
        public void invalidateCacheFor(UUID userId) {
            assertFalse(userStorage.isUserLockHeld(), "candidate invalidation should happen outside the user lock");
            if (userId != null) {
                invalidatedUserIds.add(userId);
            }
            super.invalidateCacheFor(userId);
        }

        private Set<UUID> invalidatedUserIds() {
            return Set.copyOf(invalidatedUserIds);
        }
    }

    private static final class CountingActivityMetricsService extends ActivityMetricsService {
        private final TransactionInterceptionUsers userStorage;
        private final AtomicInteger recordSwipeCount = new AtomicInteger();

        private CountingActivityMetricsService(
                TransactionInterceptionUsers userStorage,
                TestStorages.Interactions interactionStorage,
                TestStorages.TrustSafety trustSafetyStorage) {
            super(interactionStorage, trustSafetyStorage, new TestStorages.Analytics(), AppConfig.defaults());
            this.userStorage = userStorage;
        }

        @Override
        public ActivityMetricsService.SwipeGateResult recordSwipe(
                UUID userId, Like.Direction direction, boolean matched) {
            assertFalse(userStorage.isUserLockHeld(), "metrics should happen outside the user lock");
            recordSwipeCount.incrementAndGet();
            return super.recordSwipe(userId, direction, matched);
        }

        private int recordSwipeCount() {
            return recordSwipeCount.get();
        }
    }

    private static final class SlowInteractions extends TestStorages.Interactions {
        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            if (direction == Like.Direction.LIKE) {
                pause();
            }
            return super.countByDirection(userId, direction);
        }

        @Override
        public boolean exists(UUID whoLikes, UUID whoGotLiked) {
            pause();
            return super.exists(whoLikes, whoGotLiked);
        }

        @Override
        public void save(Like like) {
            pause();
            super.save(like);
        }

        private static void pause() {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
    }
}
