package datingapp.core.testutil;

import datingapp.core.Match;
import datingapp.core.Match.MatchStorage;
import datingapp.core.User;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.BlockStorage;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.LikeStorage;
import java.time.Instant;
import java.util.*;

/**
 * Consolidated in-memory storage implementations for unit testing.
 * All implementations are simple HashMap-backed mocks with test helper methods.
 *
 * <p>Usage:
 * <pre>
 * var userStorage = new TestStorages.Users();
 * var likeStorage = new TestStorages.Likes();
 * var matchStorage = new TestStorages.Matches();
 * var blockStorage = new TestStorages.Blocks();
 * </pre>
 */
public final class TestStorages {
    private TestStorages() {} // Utility class - no instantiation

    /**
     * In-memory User.Storage implementation for testing.
     */
    public static class Users implements User.Storage {
        private final Map<UUID, User> users = new HashMap<>();

        @Override
        public void save(User user) {
            users.put(user.getId(), user);
        }

        @Override
        public User get(UUID id) {
            return users.get(id);
        }

        @Override
        public List<User> findActive() {
            return users.values().stream()
                    .filter(u -> u.getState() == User.State.ACTIVE)
                    .toList();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users.values());
        }

        // === Test Helpers ===

        /** Clears all users */
        public void clear() {
            users.clear();
        }

        /** Returns number of users stored */
        public int size() {
            return users.size();
        }

        @Override
        public void delete(UUID id) {
            users.remove(id);
        }
    }

    /**
     * In-memory BlockStorage implementation for testing.
     */
    public static class Blocks implements BlockStorage {
        private final Set<Block> blocks = new HashSet<>();

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

        // === Test Helpers ===

        /** Clears all blocks */
        public void clear() {
            blocks.clear();
        }

        /** Returns number of blocks stored */
        public int size() {
            return blocks.size();
        }
    }

    /**
     * In-memory MatchStorage implementation for testing.
     */
    public static class Matches implements MatchStorage {
        private final Map<String, Match> matches = new HashMap<>();

        @Override
        public void save(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public void update(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public Optional<Match> get(String matchId) {
            return Optional.ofNullable(matches.get(matchId));
        }

        @Override
        public boolean exists(String matchId) {
            return matches.containsKey(matchId);
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            return matches.values().stream()
                    .filter(m -> m.involves(userId) && m.isActive())
                    .toList();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return matches.values().stream().filter(m -> m.involves(userId)).toList();
        }

        @Override
        public void delete(String matchId) {
            matches.remove(matchId);
        }

        // === Test Helpers ===

        /** Clears all matches */
        public void clear() {
            matches.clear();
        }

        /** Returns number of matches stored */
        public int size() {
            return matches.size();
        }

        /** Returns all matches */
        public List<Match> getAll() {
            return new ArrayList<>(matches.values());
        }
    }

    /**
     * In-memory LikeStorage implementation for testing.
     */
    public static class Likes implements LikeStorage {
        private final Map<String, Like> likes = new HashMap<>();

        @Override
        public boolean exists(UUID from, UUID to) {
            return likes.containsKey(key(from, to));
        }

        @Override
        public void save(Like like) {
            likes.put(key(like.whoLikes(), like.whoGotLiked()), like);
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Like like : likes.values()) {
                if (like.whoLikes().equals(userId)) {
                    result.add(like.whoGotLiked());
                }
            }
            return result;
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (Like like : likes.values()) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.add(like.whoLikes());
                }
            }
            return result;
        }

        @Override
        public Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
            Map<UUID, Instant> result = new HashMap<>();
            for (Like like : likes.values()) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.put(like.whoLikes(), like.createdAt());
                }
            }
            return result;
        }

        @Override
        public boolean mutualLikeExists(UUID user1, UUID user2) {
            Like like1 = likes.get(key(user1, user2));
            Like like2 = likes.get(key(user2, user1));
            return like1 != null
                    && like1.direction() == Like.Direction.LIKE
                    && like2 != null
                    && like2.direction() == Like.Direction.LIKE;
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId) && l.direction() == direction)
                    .count();
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoGotLiked().equals(userId) && l.direction() == direction)
                    .count();
        }

        @Override
        public int countMutualLikes(UUID userId) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.LIKE
                            && mutualLikeExists(userId, l.whoGotLiked()))
                    .count();
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return Optional.ofNullable(likes.get(key(fromUserId, toUserId)));
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.LIKE
                            && l.createdAt().isAfter(startOfDay))
                    .count();
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            return (int) likes.values().stream()
                    .filter(l -> l.whoLikes().equals(userId)
                            && l.direction() == Like.Direction.PASS
                            && l.createdAt().isAfter(startOfDay))
                    .count();
        }

        @Override
        public void delete(UUID likeId) {
            likes.values().removeIf(like -> like.id().equals(likeId));
        }

        // === Test Helpers ===

        /** Clears all likes */
        public void clear() {
            likes.clear();
        }

        /** Returns number of likes stored */
        public int size() {
            return likes.size();
        }

        private String key(UUID from, UUID to) {
            return from.toString() + "->" + to.toString();
        }
    }
}
