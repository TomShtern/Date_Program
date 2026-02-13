package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.*;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    private MatchingService matchingService;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .build();
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("Recording Likes")
    class LikeProcessing {

        @Test
        @DisplayName("First like saves without creating match")
        void firstLikeDoesNotCreateMatch() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            Like like = Like.create(alice, bob, Like.Direction.LIKE);
            Optional<Match> result = matchingService.recordLike(like);

            assertTrue(result.isEmpty(), "First like should not create match");
            assertTrue(interactionStorage.exists(alice, bob), "Like should be saved");
        }

        @Test
        @DisplayName("Pass does not create match")
        void passDoesNotCreateMatch() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            // Bob likes Alice first
            interactionStorage.save(Like.create(bob, alice, Like.Direction.LIKE));

            // Alice passes Bob
            Like pass = Like.create(alice, bob, Like.Direction.PASS);
            Optional<Match> result = matchingService.recordLike(pass);

            assertTrue(result.isEmpty(), "Pass should never create match");
        }

        @Test
        @DisplayName("Mutual likes create match")
        void mutualLikesCreateMatch() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            // Alice likes Bob
            Like aliceLikesBob = Like.create(alice, bob, Like.Direction.LIKE);
            matchingService.recordLike(aliceLikesBob);

            // Bob likes Alice back
            Like bobLikesAlice = Like.create(bob, alice, Like.Direction.LIKE);
            Optional<Match> result = matchingService.recordLike(bobLikesAlice);

            assertTrue(result.isPresent(), "Mutual likes should create match");
            Match match = result.get();
            assertTrue(match.involves(alice), "Match should involve Alice");
            assertTrue(match.involves(bob), "Match should involve Bob");
            assertTrue(interactionStorage.exists(match.getId()), "Match should be saved");
        }

        @Test
        @DisplayName("Duplicate like is ignored")
        void duplicateLikeIsIgnored() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            Like like1 = Like.create(alice, bob, Like.Direction.LIKE);
            Like like2 = Like.create(alice, bob, Like.Direction.LIKE);

            matchingService.recordLike(like1);
            Optional<Match> result = matchingService.recordLike(like2);

            assertTrue(result.isEmpty(), "Duplicate like should be ignored");
        }

        @Test
        @DisplayName("Match is not duplicated on repeated mutual likes")
        void matchNotDuplicated() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            // First mutual like
            matchingService.recordLike(Like.create(alice, bob, Like.Direction.LIKE));
            matchingService.recordLike(Like.create(bob, alice, Like.Direction.LIKE));

            // Try to re-like (should be ignored because like already exists)
            Optional<Match> result = matchingService.recordLike(Like.create(alice, bob, Like.Direction.LIKE));

            assertTrue(result.isEmpty(), "Re-like should not create another match");
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

            // Bob likes Alice first
            matchingService.recordLike(Like.create(bob, alice, Like.Direction.LIKE));

            // Alice likes Bob back
            Optional<Match> result = matchingService.recordLike(Like.create(alice, bob, Like.Direction.LIKE));

            assertTrue(result.isPresent(), "Order should not matter for matching");
        }
    }

    @Nested
    @DisplayName("Process Swipe Guard")
    class ProcessSwipeGuard {

        @Test
        @DisplayName("processSwipe returns config error when services are missing")
        void processSwipeReturnsConfigErrorWithoutServices() {
            // Builder without dailyService/undoService
            User user = User.StorageBuilder.create(UUID.randomUUID(), "Alice", AppClock.now())
                    .state(User.UserState.ACTIVE)
                    .build();
            User candidate = User.StorageBuilder.create(UUID.randomUUID(), "Bob", AppClock.now())
                    .state(User.UserState.ACTIVE)
                    .build();

            MatchingService.SwipeResult result = matchingService.processSwipe(user, candidate, true);

            assertFalse(result.success(), "processSwipe should fail when dailyService/undoService are null");
            assertEquals(
                    "dailyService and undoService required for processSwipe", result.message(), "Unexpected message");
        }
    }
}
