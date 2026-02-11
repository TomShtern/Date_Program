package datingapp.core.storage;

import datingapp.core.model.Match;
import datingapp.core.model.UserInteractions.Like;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Consolidated storage for user interactions: likes, matches, and transactional operations.
 * Merges the former {@code LikeStorage}, {@code MatchStorage}, and
 * {@code TransactionExecutor} interfaces.
 */
public interface InteractionStorage {

    // ═══ Like Operations ═══

    Optional<Like> getLike(UUID fromUserId, UUID toUserId);

    void save(Like like);

    boolean exists(UUID from, UUID to);

    boolean mutualLikeExists(UUID a, UUID b);

    Set<UUID> getLikedOrPassedUserIds(UUID userId);

    Set<UUID> getUserIdsWhoLiked(UUID userId);

    List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId);

    int countByDirection(UUID userId, Like.Direction direction);

    int countReceivedByDirection(UUID userId, Like.Direction direction);

    int countMutualLikes(UUID userId);

    int countLikesToday(UUID userId, Instant startOfDay);

    int countPassesToday(UUID userId, Instant startOfDay);

    void delete(UUID likeId);

    // ═══ Match Operations ═══

    void save(Match match);

    void update(Match match);

    Optional<Match> get(String matchId);

    boolean exists(String matchId);

    List<Match> getActiveMatchesFor(UUID userId);

    List<Match> getAllMatchesFor(UUID userId);

    default Optional<Match> getByUsers(UUID userA, UUID userB) {
        return get(Match.generateId(userA, userB));
    }

    void delete(String matchId);

    default int purgeDeletedBefore(Instant threshold) {
        return 0;
    }

    // ═══ Transaction Operations ═══

    boolean atomicUndoDelete(UUID likeId, String matchId);
}
