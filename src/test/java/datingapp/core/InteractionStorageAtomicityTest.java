package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchState;
import datingapp.core.storage.InteractionStorage.LikeMatchWriteResult;
import datingapp.core.testutil.TestStorages;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("InteractionStorage atomicity")
class InteractionStorageAtomicityTest {

    @Test
    @Timeout(5)
    @DisplayName("default saveLikeAndMaybeCreateMatch serializes check-then-write path")
    void defaultSaveLikeAndMaybeCreateMatchIsAtomic() throws Exception {
        SlowInteractions storage = new SlowInteractions();
        UUID likerId = UUID.randomUUID();
        UUID likedId = UUID.randomUUID();

        Like likeOne = Like.create(likerId, likedId, Like.Direction.LIKE);
        Like likeTwo = Like.create(likerId, likedId, Like.Direction.LIKE);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LikeMatchWriteResult> first = executor.submit(() -> storage.saveLikeAndMaybeCreateMatch(likeOne));
            Future<LikeMatchWriteResult> second = executor.submit(() -> storage.saveLikeAndMaybeCreateMatch(likeTwo));

            LikeMatchWriteResult firstResult = first.get(2, TimeUnit.SECONDS);
            LikeMatchWriteResult secondResult = second.get(2, TimeUnit.SECONDS);
            List<LikeMatchWriteResult> results = List.of(firstResult, secondResult);

            long persistedLikes =
                    results.stream().filter(LikeMatchWriteResult::likePersisted).count();
            long duplicateLikes =
                    results.stream().filter(result -> !result.likePersisted()).count();

            assertEquals(1, persistedLikes);
            assertEquals(1, duplicateLikes);
            assertEquals(1, storage.countByDirection(likerId, Like.Direction.LIKE));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("default saveLikeAndMaybeCreateMatch can reactivate an ended unmatched pair")
    void defaultSaveLikeAndMaybeCreateMatchReactivatesEndedUnmatchedPair() {
        SlowInteractions storage = new SlowInteractions();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        Match endedMatch = Match.create(userA, userB);
        endedMatch.unmatch(userA);
        storage.save(endedMatch);

        Like firstFreshLike = Like.create(userA, userB, Like.Direction.LIKE);
        Like secondFreshLike = Like.create(userB, userA, Like.Direction.LIKE);

        LikeMatchWriteResult firstResult = storage.saveLikeAndMaybeCreateMatch(firstFreshLike);
        LikeMatchWriteResult secondResult = storage.saveLikeAndMaybeCreateMatch(secondFreshLike);

        assertTrue(firstResult.likePersisted());
        assertTrue(firstResult.createdMatch().isEmpty());
        assertTrue(secondResult.likePersisted());
        assertTrue(secondResult.createdMatch().isPresent());
        assertEquals(
                MatchState.ACTIVE,
                storage.get(Match.generateId(userA, userB)).orElseThrow().getState());
    }

    private static final class SlowInteractions extends TestStorages.Interactions {
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
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(15));
        }
    }
}
