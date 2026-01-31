package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.MatchingService.PendingLiker;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for the liker browser functionality in MatchingService. These methods allow users to see
 * who has liked them.
 */
@SuppressWarnings("unused")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class LikerBrowserServiceTest {

    @Test
    @DisplayName("Filters out interacted, blocked, matched, and non-active likers")
    void filtersCorrectly() {
        UUID currentUserId = UUID.randomUUID();

        UUID pendingLikerId = UUID.randomUUID();
        UUID alreadyInteractedId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        UUID matchedId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();

        InMemoryLikeStorage likeStorage = new InMemoryLikeStorage();
        likeStorage.addIncomingLike(pendingLikerId);
        likeStorage.addIncomingLike(alreadyInteractedId);
        likeStorage.addIncomingLike(blockedId);
        likeStorage.addIncomingLike(matchedId);
        likeStorage.addIncomingLike(inactiveId);

        likeStorage.addAlreadyInteracted(alreadyInteractedId);

        InMemoryBlockStorage blockStorage = new InMemoryBlockStorage();
        blockStorage.save(Block.create(currentUserId, blockedId));

        InMemoryMatchStorage matchStorage = new InMemoryMatchStorage();
        matchStorage.matches.add(Match.create(currentUserId, matchedId));

        InMemoryUserStorage userStorage = new InMemoryUserStorage();
        userStorage.put(activeUser(pendingLikerId, "Pending"));
        userStorage.put(activeUser(alreadyInteractedId, "Interacted"));
        userStorage.put(activeUser(blockedId, "Blocked"));
        userStorage.put(activeUser(matchedId, "Matched"));
        userStorage.put(incompleteUser(inactiveId, "Inactive"));

        MatchingService service = new MatchingService(likeStorage, matchStorage, userStorage, blockStorage);

        List<User> pending = service.findPendingLikers(currentUserId);

        assertEquals(1, pending.size());
        assertEquals(pendingLikerId, pending.getFirst().getId());
    }

    @Test
    @DisplayName("Orders pending likers by most recent like")
    void ordersByLikedAtDesc() {
        UUID currentUserId = UUID.randomUUID();

        UUID olderLikerId = UUID.randomUUID();
        UUID newerLikerId = UUID.randomUUID();

        InMemoryLikeStorage likeStorage = new InMemoryLikeStorage();
        likeStorage.addIncomingLike(olderLikerId, Instant.parse("2026-01-01T00:00:00Z"));
        likeStorage.addIncomingLike(newerLikerId, Instant.parse("2026-01-02T00:00:00Z"));

        InMemoryUserStorage userStorage = new InMemoryUserStorage();
        userStorage.put(activeUser(olderLikerId, "Older"));
        userStorage.put(activeUser(newerLikerId, "Newer"));

        MatchingService service =
                new MatchingService(likeStorage, new InMemoryMatchStorage(), userStorage, new InMemoryBlockStorage());

        List<PendingLiker> pending = service.findPendingLikersWithTimes(currentUserId);

        assertEquals(2, pending.size());
        assertEquals(newerLikerId, pending.getFirst().user().getId());
        assertEquals(olderLikerId, pending.get(1).user().getId());
    }

    private static User activeUser(UUID id, String name) {
        return baseUser(id, name, User.State.ACTIVE);
    }

    private static User incompleteUser(UUID id, String name) {
        return baseUser(id, name, User.State.INCOMPLETE);
    }

    private static User baseUser(UUID id, String name, User.State state) {
        return User.StorageBuilder.create(id, name, Instant.EPOCH)
                .birthDate(LocalDate.of(1990, 1, 1))
                .gender(User.Gender.OTHER)
                .interestedIn(EnumSet.of(User.Gender.OTHER))
                .state(state)
                .updatedAt(Instant.EPOCH)
                .verified(false)
                .build();
    }

    private static class InMemoryLikeStorage implements LikeStorage {
        private final Set<UUID> whoLikedCurrent = new HashSet<>();
        private final Set<UUID> alreadyInteracted = new HashSet<>();
        private final Map<UUID, Instant> likeTimes = new HashMap<>();

        void addIncomingLike(UUID fromUserId) {
            addIncomingLike(fromUserId, Instant.EPOCH);
        }

        void addIncomingLike(UUID fromUserId, Instant likedAt) {
            whoLikedCurrent.add(fromUserId);
            likeTimes.put(fromUserId, likedAt);
        }

        void addAlreadyInteracted(UUID toUserId) {
            alreadyInteracted.add(toUserId);
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Like like) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            return alreadyInteracted;
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            return whoLikedCurrent;
        }

        @Override
        public Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
            return new HashMap<>(likeTimes);
        }

        @Override
        public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLikedAsList(UUID userId) {
            return new ArrayList<>(getLikeTimesForUsersWhoLiked(userId).entrySet());
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countMutualLikes(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(UUID likeId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class InMemoryUserStorage implements UserStorage {
        private final Map<UUID, User> users = new HashMap<>();

        void put(User user) {
            users.put(user.getId(), user);
        }

        @Override
        public void save(User user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public User get(UUID id) {
            return users.get(id);
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users.values());
        }

        @Override
        public List<User> findActive() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(UUID id) {
            users.remove(id);
        }
    }

    private static class InMemoryMatchStorage implements MatchStorage {
        private final List<Match> matches = new ArrayList<>();

        @Override
        public void save(Match match) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(Match match) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Match> get(String matchId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String matchId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return matches;
        }

        @Override
        public void delete(String matchId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class InMemoryBlockStorage implements BlockStorage {
        private final List<Block> blocks = new ArrayList<>();

        @Override
        public void save(Block block) {
            blocks.add(block);
        }

        @Override
        public boolean isBlocked(UUID userA, UUID userB) {
            return blocks.stream()
                    .anyMatch(b -> (b.blockerId().equals(userA) && b.blockedId().equals(userB))
                            || (b.blockerId().equals(userB) && b.blockedId().equals(userA)));
        }

        @Override
        public Set<UUID> getBlockedUserIds(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Block block : blocks) {
                if (block.blockerId().equals(userId)) {
                    result.add(block.blockedId());
                } else if (block.blockedId().equals(userId)) {
                    result.add(block.blockerId());
                }
            }
            return result;
        }

        @Override
        public List<Block> findByBlocker(UUID blockerId) {
            return blocks.stream().filter(b -> b.blockerId().equals(blockerId)).toList();
        }

        @Override
        public boolean delete(UUID blockerId, UUID blockedId) {
            return blocks.removeIf(
                    b -> b.blockerId().equals(blockerId) && b.blockedId().equals(blockedId));
        }

        @Override
        public int countBlocksGiven(UUID userId) {
            return (int)
                    blocks.stream().filter(b -> b.blockerId().equals(userId)).count();
        }

        @Override
        public int countBlocksReceived(UUID userId) {
            return (int)
                    blocks.stream().filter(b -> b.blockedId().equals(userId)).count();
        }
    }
}
