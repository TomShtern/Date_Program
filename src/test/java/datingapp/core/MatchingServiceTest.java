package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.UserInteractions.Like;
import datingapp.core.testutil.InMemoryLikeStorage;
import datingapp.core.testutil.InMemoryMatchStorage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for MatchingService using in-memory mock storage. */
class MatchingServiceTest {

    private InMemoryLikeStorage likeStorage;
    private InMemoryMatchStorage matchStorage;
    private MatchingService matchingService;

    @BeforeEach
    void setUp() {

        likeStorage = new InMemoryLikeStorage();
        matchStorage = new InMemoryMatchStorage();
        matchingService = new MatchingService(likeStorage, matchStorage);
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
            assertTrue(likeStorage.exists(alice, bob), "Like should be saved");
        }

        @Test
        @DisplayName("Pass does not create match")
        void passDoesNotCreateMatch() {
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            // Bob likes Alice first
            likeStorage.save(Like.create(bob, alice, Like.Direction.LIKE));

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
            assertTrue(matchStorage.exists(match.getId()), "Match should be saved");
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
            assertFalse(matchStorage.exists(Match.generateId(alice, bob)), "No match should exist after pass");
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
}
