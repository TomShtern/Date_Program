package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.ProcessSwipeCommand;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("MatchingTransaction")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class MatchingTransactionTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T12:00:00Z");
    private static final UUID ALICE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AppConfig config;
    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Undos undoStorage;
    private CandidateFinder candidateFinder;
    private UndoService undoService;
    private MatchingService matchingService;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_NOW);
        config = AppConfig.defaults();
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        undoStorage = new TestStorages.Undos();

        alice = TestUserFactory.createActiveUser(ALICE_ID, "Alice");
        alice.setGender(User.Gender.MALE);
        alice.setInterestedIn(Set.of(User.Gender.FEMALE));

        bob = TestUserFactory.createActiveUser(BOB_ID, "Bob");
        bob.setGender(User.Gender.FEMALE);
        bob.setInterestedIn(Set.of(User.Gender.MALE));

        userStorage.save(alice);
        userStorage.save(bob);

        candidateFinder = new CandidateFinder(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                config.safety().userTimeZone());
        undoService = new UndoService(interactionStorage, undoStorage, config);
        matchingService = createMatchingService(permissiveRecommendationService());
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("failed swipe due to daily limit does not persist like or match")
    void failedSwipeDueToDailyLimitDoesNotPersistLikeOrMatch() {
        MatchingService limitedMatchingService = createMatchingService(denyLikeRecommendationService());
        MatchingUseCases useCases = createUseCases(denyLikeDailyLimitService(), limitedMatchingService);

        var result = useCases.processSwipe(swipeCommand(alice, bob));

        assertFalse(result.success());
        assertEquals("Daily like limit reached.", result.error().message());
        assertTrue(interactionStorage.getLike(alice.getId(), bob.getId()).isEmpty());
        assertTrue(interactionStorage
                .get(Match.generateId(alice.getId(), bob.getId()))
                .isEmpty());
        assertEquals(0, interactionStorage.likeSize());
        assertEquals(0, interactionStorage.matchSize());
    }

    @Test
    @DisplayName("successful like persists interaction")
    void successfulLikePersistsInteraction() {
        MatchingUseCases useCases = createUseCases(allowAllDailyLimitService(), matchingService);

        var result = useCases.processSwipe(swipeCommand(alice, bob));

        assertTrue(result.success());
        assertTrue(result.data().success());
        assertTrue(interactionStorage.getLike(alice.getId(), bob.getId()).isPresent());
        assertEquals(1, interactionStorage.likeSize());
        assertTrue(interactionStorage
                .get(Match.generateId(alice.getId(), bob.getId()))
                .isEmpty());
        assertEquals(0, interactionStorage.matchSize());
    }

    @Test
    @DisplayName("mutual like creates a match exactly once")
    void mutualLikeCreatesAMatchExactlyOnce() {
        MatchingUseCases useCases = createUseCases(allowAllDailyLimitService(), matchingService);

        var firstLike = useCases.processSwipe(swipeCommand(alice, bob));
        var secondLike = useCases.processSwipe(swipeCommand(bob, alice));
        var duplicateLike = useCases.processSwipe(swipeCommand(bob, alice));

        assertTrue(firstLike.success());
        assertTrue(secondLike.success());
        assertTrue(duplicateLike.success());
        assertTrue(secondLike.data().matched());

        String matchId = Match.generateId(alice.getId(), bob.getId());
        Optional<Match> match = interactionStorage.get(matchId);

        assertEquals(2, interactionStorage.likeSize());
        assertEquals(1, interactionStorage.matchSize());
        assertTrue(match.isPresent());
        assertEquals(1, interactionStorage.countMatchesFor(alice.getId()));
        assertEquals(1, interactionStorage.countMatchesFor(bob.getId()));
        assertEquals(1, interactionStorage.countByDirection(bob.getId(), Like.Direction.LIKE));
    }

    private MatchingUseCases createUseCases(DailyLimitService dailyLimitService, MatchingService service) {
        return new MatchingUseCases(
                candidateFinder,
                service,
                dailyLimitService,
                noDailyPickService(),
                noStandoutService(),
                undoService,
                interactionStorage,
                userStorage,
                new MatchQualityService(userStorage, interactionStorage, config),
                null);
    }

    private static ProcessSwipeCommand swipeCommand(User currentUser, User candidate) {
        return new ProcessSwipeCommand(
                UserContext.cli(currentUser.getId()), currentUser, candidate, true, false, false);
    }

    private static DailyLimitService allowAllDailyLimitService() {
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

            @Override
            public String formatDuration(Duration duration) {
                return RecommendationService.formatDuration(duration);
            }
        };
    }

    private static DailyLimitService denyLikeDailyLimitService() {
        return new DailyLimitService() {
            @Override
            public boolean canLike(UUID userId) {
                return false;
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
                return new DailyStatus(1, 0, 0, 999, 0, 999, AppClock.today(), AppClock.now());
            }

            @Override
            public Duration getTimeUntilReset() {
                return Duration.ZERO;
            }

            @Override
            public String formatDuration(Duration duration) {
                return RecommendationService.formatDuration(duration);
            }
        };
    }

    private static RecommendationService permissiveRecommendationService() {
        return new RecommendationService(allowAllDailyLimitService(), noDailyPickService(), noStandoutService());
    }

    private static RecommendationService denyLikeRecommendationService() {
        return new RecommendationService(denyLikeDailyLimitService(), noDailyPickService(), noStandoutService());
    }

    private MatchingService createMatchingService(RecommendationService recommendationService) {
        return MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .undoService(undoService)
                .dailyService(recommendationService)
                .candidateFinder(candidateFinder)
                .build();
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
                // No-op: the regression tests only need a deterministic swipe flow.
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
                // No-op: standouts are irrelevant for the transactional matching regression tests.
            }

            @Override
            public java.util.Map<UUID, User> resolveUsers(java.util.List<Standout> standouts) {
                return java.util.Map.of();
            }
        };
    }
}
