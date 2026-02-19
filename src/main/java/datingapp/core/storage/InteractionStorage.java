package datingapp.core.storage;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.model.Match;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** Result of persisting a like and optionally creating a match. */
    record LikeMatchWriteResult(boolean likePersisted, Optional<Match> createdMatch) {
        public LikeMatchWriteResult {
            Objects.requireNonNull(createdMatch, "createdMatch cannot be null");
        }

        public static LikeMatchWriteResult duplicateLike() {
            return new LikeMatchWriteResult(false, Optional.empty());
        }

        public static LikeMatchWriteResult likeOnly() {
            return new LikeMatchWriteResult(true, Optional.empty());
        }

        public static LikeMatchWriteResult likeAndMatch(Match match) {
            return new LikeMatchWriteResult(true, Optional.of(Objects.requireNonNull(match, "match cannot be null")));
        }
    }

    /**
     * Persists a swipe and, when it is a mutual LIKE, creates the corresponding match.
     *
     * <p>Default implementation preserves existing behavior for non-transactional storages.
     */
    default LikeMatchWriteResult saveLikeAndMaybeCreateMatch(Like like) {
        Objects.requireNonNull(like, "like cannot be null");

        if (exists(like.whoLikes(), like.whoGotLiked())) {
            return LikeMatchWriteResult.duplicateLike();
        }

        save(like);
        if (like.direction() != Like.Direction.LIKE) {
            return LikeMatchWriteResult.likeOnly();
        }

        if (!mutualLikeExists(like.whoLikes(), like.whoGotLiked())) {
            return LikeMatchWriteResult.likeOnly();
        }

        String matchId = Match.generateId(like.whoLikes(), like.whoGotLiked());
        if (exists(matchId)) {
            return LikeMatchWriteResult.likeOnly();
        }

        Match match = Match.create(like.whoLikes(), like.whoGotLiked());
        save(match);
        return LikeMatchWriteResult.likeAndMatch(match);
    }

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

    /**
     * Indicates whether this storage can execute relationship transition writes atomically across
     * all touched tables.
     */
    default boolean supportsAtomicRelationshipTransitions() {
        return false;
    }

    /**
     * Atomically persists the "accept friend zone" transition (match + request + notification).
     *
     * <p>Default implementation returns {@code false}, signaling unsupported operation.
     */
    default boolean acceptFriendZoneTransition(
            Match updatedMatch, FriendRequest acceptedRequest, Notification notification) {
        return false;
    }

    /**
     * Atomically persists the graceful-exit transition (match + optional conversation archive +
     * notification).
     *
     * <p>Default implementation returns {@code false}, signaling unsupported operation.
     */
    default boolean gracefulExitTransition(
            Match updatedMatch, Optional<Conversation> archivedConversation, Notification notification) {
        return false;
    }

    // ═══ Transaction Operations ═══

    boolean atomicUndoDelete(UUID likeId, String matchId);
}
