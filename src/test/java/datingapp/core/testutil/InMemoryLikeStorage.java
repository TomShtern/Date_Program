package datingapp.core.testutil;

import datingapp.core.LikeStorage;
import datingapp.core.UserInteractions.Like;
import java.time.Instant;
import java.util.*;

/**
 * In-memory LikeStorage for testing. Thread-safe and provides test helper methods.
 */
public class InMemoryLikeStorage implements LikeStorage {
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
