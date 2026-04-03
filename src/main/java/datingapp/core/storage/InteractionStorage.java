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
 * Consolidated storage for user interactions: likes, matches, and transactional
 * operations.
 * Merges the former {@code LikeStorage}, {@code MatchStorage}, and
 * {@code TransactionExecutor} interfaces.
 */
public interface InteractionStorage {

    // ═══ Like Operations ═══

    Optional<Like> getLike(UUID fromUserId, UUID toUserId);

    default Optional<Like> getLikeById(UUID likeId) {
        Objects.requireNonNull(likeId, "likeId cannot be null");
        throw new UnsupportedOperationException(
                "InteractionStorage implementation must override getLikeById(UUID) to support ownership-aware deletion");
    }

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

    int countSuperLikesToday(UUID userId, Instant startOfDay);

    int countPassesToday(UUID userId, Instant startOfDay);

    void delete(UUID likeId);

    default boolean deleteLikeOwnedBy(UUID ownerUserId, UUID likeId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId cannot be null");
        Objects.requireNonNull(likeId, "likeId cannot be null");

        synchronized (this) {
            Optional<Like> like = getLikeById(likeId);
            if (like.isEmpty() || !like.get().whoLikes().equals(ownerUserId)) {
                return false;
            }

            delete(likeId);
            return true;
        }
    }

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
     * Persists a swipe and, when it is a mutual LIKE, creates the corresponding
     * match.
     *
     * <p>
     * Default implementation preserves existing behavior for non-transactional
     * storages.
     */
    default LikeMatchWriteResult saveLikeAndMaybeCreateMatch(Like like) {
        Objects.requireNonNull(like, "like cannot be null");

        synchronized (this) {
            if (exists(like.whoLikes(), like.whoGotLiked())) {
                return LikeMatchWriteResult.duplicateLike();
            }

            save(like);
            if (!isPositiveLikeDirection(like.direction())) {
                return LikeMatchWriteResult.likeOnly();
            }

            if (!mutualLikeExists(like.whoLikes(), like.whoGotLiked())) {
                return LikeMatchWriteResult.likeOnly();
            }

            String matchId = Match.generateId(like.whoLikes(), like.whoGotLiked());
            Optional<Match> existingMatch = get(matchId);
            if (existingMatch.isPresent()) {
                Match match = existingMatch.get();
                if (match.getState() == Match.MatchState.UNMATCHED) {
                    match.reactivateFromUnmatch();
                    update(match);
                    return LikeMatchWriteResult.likeAndMatch(match);
                }
                return LikeMatchWriteResult.likeOnly();
            }

            Match match = Match.create(like.whoLikes(), like.whoGotLiked());
            save(match);
            return LikeMatchWriteResult.likeAndMatch(match);
        }
    }

    default void deletePairLikes(UUID userA, UUID userB) {
        Objects.requireNonNull(userA, "userA cannot be null");
        Objects.requireNonNull(userB, "userB cannot be null");
        synchronized (this) {
            getLike(userA, userB).ifPresent(like -> delete(like.id()));
            getLike(userB, userA).ifPresent(like -> delete(like.id()));
        }
    }

    // ═══ Match Operations ═══

    void save(Match match);

    void update(Match match);

    Optional<Match> get(String matchId);

    boolean exists(String matchId);

    List<Match> getActiveMatchesFor(UUID userId);

    List<Match> getAllMatchesFor(UUID userId);

    /**
     * Returns the set of counterpart user IDs the given user has matched with
     * (active and ended, excluding soft-deleted matches).
     *
     * <p>
     * Implementations must override this with a UUID-only projection for
     * efficiency.
     */
    default Set<UUID> getMatchedCounterpartIds(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        throw new UnsupportedOperationException(
                "InteractionStorage implementation must override getMatchedCounterpartIds(UUID) to support efficient lookup");
    }

    default Optional<Match> getByUsers(UUID userA, UUID userB) {
        return get(Match.generateId(userA, userB));
    }

    void delete(String matchId);

    // ═══ Paginated Match Operations ═══

    /**
     * Returns the total number of non-deleted matches involving {@code userId}
     * (active and ended).
     *
     * <p>
     * Implementations should prefer a direct {@code SELECT COUNT(*)} query.
     */
    int countMatchesFor(UUID userId);

    /**
     * Returns the total number of active, non-deleted matches involving
     * {@code userId}.
     *
     * <p>
     * Implementations should prefer a direct {@code SELECT COUNT(*)} query.
     */
    int countActiveMatchesFor(UUID userId);

    /**
     * Returns a single page of <em>all</em> matches (active and ended) for
     * {@code userId},
     * ordered by {@code created_at} descending (newest first).
     *
     * <p>
     * Default implementation loads all matches and slices in memory — suitable for
     * development / low-volume cases. Override with SQL
     * {@code LIMIT}/{@code OFFSET} for
     * production scalability.
     *
     * @param userId the user whose matches to fetch
     * @param offset zero-based index of the first item (must be &ge; 0)
     * @param limit  maximum items to return (must be &gt; 0)
     */
    default PageData<Match> getPageOfMatchesFor(UUID userId, int offset, int limit) {
        Objects.requireNonNull(userId, "userId cannot be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        List<Match> all = getAllMatchesFor(userId);
        int total = all.size();
        if (offset >= total) {
            return PageData.empty(limit, total);
        }
        int end = offset + Math.min(limit, total - offset);
        List<Match> page = all.subList(offset, end);
        return new PageData<>(page, total, offset, limit);
    }

    /**
     * Returns a single page of <em>active</em> matches for {@code userId},
     * ordered by {@code created_at} descending (newest first).
     *
     * <p>
     * Default implementation loads all active matches and slices in memory.
     * Override
     * with SQL {@code LIMIT}/{@code OFFSET} for production scalability.
     *
     * @param userId the user whose active matches to fetch
     * @param offset zero-based index of the first item (must be &ge; 0)
     * @param limit  maximum items to return (must be &gt; 0)
     */
    default PageData<Match> getPageOfActiveMatchesFor(UUID userId, int offset, int limit) {
        Objects.requireNonNull(userId, "userId cannot be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        List<Match> all = getActiveMatchesFor(userId);
        int total = all.size();
        if (offset >= total) {
            return PageData.empty(limit, total);
        }
        int end = offset + Math.min(limit, total - offset);
        List<Match> page = all.subList(offset, end);
        return new PageData<>(page, total, offset, limit);
    }

    default int purgeDeletedBefore(Instant threshold) {
        Objects.requireNonNull(threshold, "threshold cannot be null");
        throw new UnsupportedOperationException(
                "InteractionStorage implementation must override purgeDeletedBefore(Instant) to support cleanup");
    }

    /**
     * Indicates whether this storage can execute relationship transition writes
     * atomically across
     * all touched tables.
     */
    default boolean supportsAtomicRelationshipTransitions() {
        return false;
    }

    /**
     * Indicates whether this storage can execute block transitions atomically
     * across the touched interaction and conversation rows.
     */
    default boolean supportsAtomicBlockTransition() {
        return false;
    }

    /**
     * Atomically persists the "accept friend zone" transition (match + request +
     * notification).
     *
     * <p>
     * Default implementation returns {@code false}, signaling unsupported
     * operation.
     */
    default boolean acceptFriendZoneTransition(
            Match updatedMatch, FriendRequest acceptedRequest, Notification notification) {
        return false;
    }

    /**
     * Atomically persists the graceful-exit transition (match + optional
     * conversation archive +
     * notification).
     *
     * <p>
     * Default implementation returns {@code false}, signaling unsupported
     * operation.
     */
    default boolean gracefulExitTransition(
            Match updatedMatch, Optional<Conversation> archivedConversation, Notification notification) {
        return false;
    }

    /**
     * Atomically persists the unmatch transition (match + optional conversation
     * archive).
     *
     * <p>
     * Default implementation returns {@code false}, signaling unsupported
     * operation.
     */
    default boolean unmatchTransition(Match updatedMatch, Optional<Conversation> archivedConversation) {
        return false;
    }

    /**
     * Atomically persists a block transition (match + optional archived
     * conversation).
     *
     * <p>
     * Default implementation returns {@code false}, signaling unsupported
     * operation.
     */
    default boolean blockTransition(
            UUID blockerId, UUID blockedId, Optional<Match> updatedMatch, Optional<Conversation> archivedConversation) {
        return false;
    }

    // ═══ Transaction Operations ═══

    boolean atomicUndoDelete(UUID likeId, String matchId);

    private static boolean isPositiveLikeDirection(Like.Direction direction) {
        return direction == Like.Direction.LIKE || direction == Like.Direction.SUPER_LIKE;
    }
}
