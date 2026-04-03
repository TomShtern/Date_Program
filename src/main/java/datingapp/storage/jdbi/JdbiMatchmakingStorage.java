package datingapp.storage.jdbi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.connection.ConnectionModels;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.Match.MatchState;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.PageData;
import datingapp.storage.DatabaseManager.StorageException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/** Consolidated JDBI storage for likes and matches. */
public final class JdbiMatchmakingStorage implements InteractionStorage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PARAM_ID = "id";
    private static final String PARAM_WHO_LIKES = "whoLikes";
    private static final String PARAM_WHO_GOT_LIKED = "whoGotLiked";
    private static final String PARAM_DIRECTION = "direction";
    private static final String PARAM_CREATED_AT = "createdAt";
    private static final String PARAM_UPDATED_AT = "updatedAt";
    private static final String PARAM_STATE = "state";
    private static final String PARAM_ENDED_AT = "endedAt";
    private static final String PARAM_ENDED_BY = "endedBy";
    private static final String PARAM_END_REASON = "endReason";
    private static final String PARAM_DELETED_AT = "deletedAt";
    private static final String PARAM_CONVERSATION_ID = "conversationId";
    private static final String PARAM_ARCHIVED_AT_A = "archivedAtA";
    private static final String PARAM_ARCHIVE_REASON_A = "archiveReasonA";
    private static final String PARAM_ARCHIVED_AT_B = "archivedAtB";
    private static final String PARAM_ARCHIVE_REASON_B = "archiveReasonB";
    private static final String PARAM_VISIBLE_TO_USER_A = "visibleToUserA";
    private static final String PARAM_VISIBLE_TO_USER_B = "visibleToUserB";
    private static final String COLUMN_WHO_LIKES = "who_likes";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String ERR_USER_ID_NULL = "userId cannot be null";
    private static final String ERR_UPDATED_MATCH_NULL = "updatedMatch cannot be null";
    private static final String ERR_ARCHIVED_CONVERSATION_NULL = "archivedConversation cannot be null";

    private static final String SQL_ACTIVE_LIKE_EXISTS = """
            SELECT EXISTS (
            SELECT 1
            FROM likes
            WHERE who_likes = :whoLikes
              AND who_got_liked = :whoGotLiked
              AND deleted_at IS NULL
            )
            """;

    private static final String SQL_ACTIVE_LIKE_BY_ID = """
                        SELECT id, who_likes, who_got_liked, direction, created_at
                        FROM likes
                        WHERE id = :likeId
                            AND deleted_at IS NULL
                        """;

    private static final String SQL_SOFT_DELETE_OWNED_LIKE = """
                        UPDATE likes
                        SET deleted_at = CURRENT_TIMESTAMP
                        WHERE id = :likeId
                            AND who_likes = :ownerUserId
                            AND deleted_at IS NULL
                        """;

    private static final String SQL_UPSERT_LIKE = """
            MERGE INTO likes (id, who_likes, who_got_liked, direction, created_at, deleted_at)
            KEY (who_likes, who_got_liked)
            VALUES (:id, :whoLikes, :whoGotLiked, :direction, :createdAt, NULL)
            """;

    private static final String SQL_MUTUAL_LIKE_EXISTS = """
            SELECT EXISTS (
            SELECT 1
            FROM likes l1
            JOIN likes l2 ON l1.who_likes = l2.who_got_liked
                     AND l1.who_got_liked = l2.who_likes
            WHERE l1.who_likes = :whoLikes
              AND l1.who_got_liked = :whoGotLiked
                            AND l1.direction IN ('LIKE', 'SUPER_LIKE')
                            AND l2.direction IN ('LIKE', 'SUPER_LIKE')
              AND l1.deleted_at IS NULL
              AND l2.deleted_at IS NULL
            )
            """;

    private static final String SQL_ACTIVE_MATCH_EXISTS =
            "SELECT EXISTS(SELECT 1 FROM matches WHERE id = :id AND deleted_at IS NULL)";

    private static final String SQL_UPSERT_MATCH = """
            MERGE INTO matches (
            id, user_a, user_b, created_at, updated_at, state, ended_at, ended_by, end_reason, deleted_at
            ) KEY (id)
            VALUES (
            :id, :userA, :userB, :createdAt, :updatedAt, :state, :endedAt, :endedBy, :endReason, :deletedAt
            )
            """;

    private static final String SQL_UPDATE_MATCH_TRANSITION = """
            UPDATE matches
            SET state = :state,
            updated_at = :updatedAt,
            ended_at = :endedAt,
            ended_by = :endedBy,
            end_reason = :endReason,
            deleted_at = :deletedAt
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String SQL_ACTIVE_MATCH_STATE =
            "SELECT state FROM matches WHERE id = :id AND deleted_at IS NULL";

    private static final String SQL_REACTIVATE_UNMATCHED_MATCH = """
                UPDATE matches
                SET state = 'ACTIVE',
                updated_at = :updatedAt,
                ended_at = NULL,
                ended_by = NULL,
                end_reason = NULL,
                deleted_at = NULL
                WHERE id = :id AND state = 'UNMATCHED' AND deleted_at IS NULL
                """;

    private static final String SQL_SOFT_DELETE_PAIR_LIKES = """
                UPDATE likes
                SET deleted_at = :deletedAt
                WHERE ((who_likes = :userA AND who_got_liked = :userB)
                OR (who_likes = :userB AND who_got_liked = :userA))
                  AND deleted_at IS NULL
                """;

    private static final String SQL_ACCEPT_FRIEND_REQUEST = """
            UPDATE friend_requests
            SET status = :status,
            responded_at = :respondedAt,
            pending_marker = NULL
            WHERE id = :id AND status = 'PENDING'
            """;

    private static final String SQL_ARCHIVE_CONVERSATION = """
            UPDATE conversations
            SET archived_at_a = :archivedAtA,
                archive_reason_a = :archiveReasonA,
                archived_at_b = :archivedAtB,
                archive_reason_b = :archiveReasonB
                        WHERE id = :conversationId
                            AND deleted_at IS NULL
            """;

    private static final String SQL_UPDATE_CONVERSATION_VISIBILITY = """
            UPDATE conversations
            SET visible_to_user_a = :visibleToUserA,
                visible_to_user_b = :visibleToUserB
            WHERE id = :conversationId
                AND deleted_at IS NULL
            """;

    private static final String SQL_INSERT_NOTIFICATION = """
            INSERT INTO notifications (id, user_id, type, title, message, created_at, is_read, data_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final Jdbi jdbi;
    private final LikeDao likeDao;
    private final MatchDao matchDao;
    private final UndoDao undoDao;
    private final Undo.Storage undoStorage;

    public JdbiMatchmakingStorage(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.likeDao = jdbi.onDemand(LikeDao.class);
        this.matchDao = jdbi.onDemand(MatchDao.class);
        this.undoDao = jdbi.onDemand(UndoDao.class);
        this.undoStorage = new UndoStorageAdapter();
    }

    public Undo.Storage undoStorage() {
        return undoStorage;
    }

    @Override
    public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
        return likeDao.getLike(fromUserId, toUserId);
    }

    @Override
    public Optional<Like> getLikeById(UUID likeId) {
        Objects.requireNonNull(likeId, "likeId cannot be null");
        return likeDao.getLikeById(likeId);
    }

    @Override
    public void save(Like like) {
        likeDao.save(like);
    }

    @Override
    public LikeMatchWriteResult saveLikeAndMaybeCreateMatch(Like like) {
        Objects.requireNonNull(like, "like cannot be null");

        try {
            return jdbi.inTransaction(handle -> {
                if (activeLikeExists(handle, like)) {
                    return LikeMatchWriteResult.duplicateLike();
                }

                saveLike(handle, like);

                if (!isPositiveLikeDirection(like.direction())) {
                    return LikeMatchWriteResult.likeOnly();
                }

                if (!mutualLikeExists(handle, like)) {
                    return LikeMatchWriteResult.likeOnly();
                }

                String matchId = Match.generateId(like.whoLikes(), like.whoGotLiked());
                Optional<LikeMatchWriteResult> existingMatchResult = handleExistingMatch(handle, like, matchId);
                if (existingMatchResult.isPresent()) {
                    return existingMatchResult.get();
                }

                return createFreshMatch(handle, like);
            });
        } catch (Exception e) {
            throw new StorageException("Atomic like->match persistence failed", e);
        }
    }

    private static boolean activeLikeExists(org.jdbi.v3.core.Handle handle, Like like) {
        try (var query = handle.createQuery(SQL_ACTIVE_LIKE_EXISTS)) {
            return query.bind(PARAM_WHO_LIKES, like.whoLikes())
                    .bind(PARAM_WHO_GOT_LIKED, like.whoGotLiked())
                    .mapTo(Boolean.class)
                    .one();
        }
    }

    private static void saveLike(org.jdbi.v3.core.Handle handle, Like like) {
        try (var update = handle.createUpdate(SQL_UPSERT_LIKE)) {
            update.bind(PARAM_ID, like.id())
                    .bind(PARAM_WHO_LIKES, like.whoLikes())
                    .bind(PARAM_WHO_GOT_LIKED, like.whoGotLiked())
                    .bind(PARAM_DIRECTION, like.direction().name())
                    .bind(PARAM_CREATED_AT, like.createdAt())
                    .execute();
        }
    }

    private static boolean mutualLikeExists(org.jdbi.v3.core.Handle handle, Like like) {
        try (var query = handle.createQuery(SQL_MUTUAL_LIKE_EXISTS)) {
            return query.bind(PARAM_WHO_LIKES, like.whoLikes())
                    .bind(PARAM_WHO_GOT_LIKED, like.whoGotLiked())
                    .mapTo(Boolean.class)
                    .one();
        }
    }

    private static Optional<LikeMatchWriteResult> handleExistingMatch(
            org.jdbi.v3.core.Handle handle, Like like, String matchId) {
        boolean activeMatchExists;
        try (var query = handle.createQuery(SQL_ACTIVE_MATCH_EXISTS)) {
            activeMatchExists =
                    query.bind(PARAM_ID, matchId).mapTo(Boolean.class).one();
        }
        if (!activeMatchExists) {
            return Optional.empty();
        }

        String existingState;
        try (var query = handle.createQuery(SQL_ACTIVE_MATCH_STATE)) {
            existingState =
                    query.bind(PARAM_ID, matchId).mapTo(String.class).findOne().orElseThrow();
        }
        if (!MatchState.UNMATCHED.name().equals(existingState)) {
            return Optional.of(LikeMatchWriteResult.likeOnly());
        }

        int reactivatedRows;
        try (var update = handle.createUpdate(SQL_REACTIVATE_UNMATCHED_MATCH)) {
            reactivatedRows = update.bind(PARAM_ID, matchId)
                    .bind(PARAM_UPDATED_AT, like.createdAt())
                    .execute();
        }
        if (reactivatedRows != 1) {
            throw new StorageException("Failed to reactivate unmatched pair atomically");
        }

        Match reactivatedMatch = handle.attach(MatchDao.class)
                .get(matchId)
                .orElseThrow(() -> new StorageException("Reactivated match was not found"));
        return Optional.of(LikeMatchWriteResult.likeAndMatch(reactivatedMatch));
    }

    private static LikeMatchWriteResult createFreshMatch(org.jdbi.v3.core.Handle handle, Like like) {
        Match match = Match.create(like.whoLikes(), like.whoGotLiked());
        try (var update = handle.createUpdate(SQL_UPSERT_MATCH)) {
            update.bind(PARAM_ID, match.getId())
                    .bind("userA", match.getUserA())
                    .bind("userB", match.getUserB())
                    .bind(PARAM_CREATED_AT, match.getCreatedAt())
                    .bind(PARAM_UPDATED_AT, match.getUpdatedAt())
                    .bind(PARAM_STATE, match.getState().name())
                    .bind(PARAM_ENDED_AT, match.getEndedAt())
                    .bind(PARAM_ENDED_BY, match.getEndedBy())
                    .bind(
                            PARAM_END_REASON,
                            match.getEndReason() != null ? match.getEndReason().name() : null)
                    .bind(PARAM_DELETED_AT, match.getDeletedAt())
                    .execute();
        }
        return LikeMatchWriteResult.likeAndMatch(match);
    }

    @Override
    public boolean exists(UUID from, UUID to) {
        return likeDao.exists(from, to);
    }

    @Override
    public boolean mutualLikeExists(UUID a, UUID b) {
        return likeDao.mutualLikeExists(a, b);
    }

    @Override
    public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
        return likeDao.getLikedOrPassedUserIds(userId);
    }

    @Override
    public Set<UUID> getUserIdsWhoLiked(UUID userId) {
        return likeDao.getUserIdsWhoLiked(userId);
    }

    @Override
    public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
        return likeDao.getLikeTimesForUsersWhoLiked(userId);
    }

    @Override
    public int countByDirection(UUID userId, Like.Direction direction) {
        return likeDao.countByDirection(userId, direction);
    }

    @Override
    public int countReceivedByDirection(UUID userId, Like.Direction direction) {
        return likeDao.countReceivedByDirection(userId, direction);
    }

    @Override
    public int countMutualLikes(UUID userId) {
        return likeDao.countMutualLikes(userId);
    }

    @Override
    public int countLikesToday(UUID userId, Instant startOfDay) {
        return likeDao.countLikesToday(userId, startOfDay);
    }

    @Override
    public int countSuperLikesToday(UUID userId, Instant startOfDay) {
        return likeDao.countSuperLikesToday(userId, startOfDay);
    }

    @Override
    public int countPassesToday(UUID userId, Instant startOfDay) {
        return likeDao.countPassesToday(userId, startOfDay);
    }

    @Override
    public void delete(UUID likeId) {
        likeDao.delete(likeId);
    }

    @Override
    public boolean deleteLikeOwnedBy(UUID ownerUserId, UUID likeId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId cannot be null");
        Objects.requireNonNull(likeId, "likeId cannot be null");
        return likeDao.deleteLikeOwnedBy(ownerUserId, likeId) == 1;
    }

    @Override
    public void save(Match match) {
        matchDao.save(match);
    }

    @Override
    public void update(Match match) {
        matchDao.update(match);
    }

    @Override
    public Optional<Match> get(String matchId) {
        return matchDao.get(matchId);
    }

    @Override
    public boolean exists(String matchId) {
        return matchDao.exists(matchId);
    }

    @Override
    public List<Match> getActiveMatchesFor(UUID userId) {
        return matchDao.getActiveMatchesFor(userId);
    }

    @Override
    public List<Match> getAllMatchesFor(UUID userId) {
        return matchDao.getAllMatchesFor(userId);
    }

    @Override
    public Set<UUID> getMatchedCounterpartIds(UUID userId) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);
        return matchDao.getMatchedCounterpartIds(userId);
    }

    /**
     * Returns the total number of non-deleted matches for {@code userId} via a
     * single
     * {@code SELECT COUNT(*)} — no rows hydrated, no GC pressure.
     */
    @Override
    public int countMatchesFor(UUID userId) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);
        return matchDao.countMatchesFor(userId);
    }

    /**
     * Returns a bounded page of all matches (active + ended), newest first, using
     * SQL
     * {@code LIMIT}/{@code OFFSET} so only the requested rows are transferred from
     * H2.
     */
    @Override
    public PageData<Match> getPageOfMatchesFor(UUID userId, int offset, int limit) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        int total = matchDao.countMatchesFor(userId);
        if (offset >= total) {
            return PageData.empty(limit, total);
        }
        List<Match> page = matchDao.getPageOfMatchesFor(userId, offset, limit);
        return new PageData<>(page, total, offset, limit);
    }

    /**
     * Returns a bounded page of active matches only, newest first, using SQL
     * {@code LIMIT}/{@code OFFSET} — safe for highly-active users with thousands of
     * matches.
     */
    @Override
    public PageData<Match> getPageOfActiveMatchesFor(UUID userId, int offset, int limit) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        int total = matchDao.countActiveMatchesFor(userId);
        if (offset >= total) {
            return PageData.empty(limit, total);
        }
        List<Match> page = matchDao.getPageOfActiveMatchesFor(userId, offset, limit);
        return new PageData<>(page, total, offset, limit);
    }

    @Override
    public int countActiveMatchesFor(UUID userId) {
        Objects.requireNonNull(userId, ERR_USER_ID_NULL);
        return matchDao.countActiveMatchesFor(userId);
    }

    @Override
    public boolean supportsAtomicRelationshipTransitions() {
        return true;
    }

    @Override
    public boolean supportsAtomicBlockTransition() {
        return true;
    }

    @Override
    public boolean acceptFriendZoneTransition(
            Match updatedMatch, FriendRequest acceptedRequest, Notification notification) {
        Objects.requireNonNull(updatedMatch, ERR_UPDATED_MATCH_NULL);
        Objects.requireNonNull(acceptedRequest, "acceptedRequest cannot be null");

        try {
            return jdbi.inTransaction(handle -> {
                int matchRows = handle.createUpdate(SQL_UPDATE_MATCH_TRANSITION)
                        .bind(PARAM_ID, updatedMatch.getId())
                        .bind(PARAM_STATE, updatedMatch.getState().name())
                        .bind(PARAM_UPDATED_AT, updatedMatch.getUpdatedAt())
                        .bind(PARAM_ENDED_AT, updatedMatch.getEndedAt())
                        .bind(PARAM_ENDED_BY, updatedMatch.getEndedBy())
                        .bind(
                                PARAM_END_REASON,
                                updatedMatch.getEndReason() != null
                                        ? updatedMatch.getEndReason().name()
                                        : null)
                        .bind(PARAM_DELETED_AT, updatedMatch.getDeletedAt())
                        .execute();
                if (matchRows != 1) {
                    return false;
                }

                int requestRows = handle.createUpdate(SQL_ACCEPT_FRIEND_REQUEST)
                        .bind(PARAM_ID, acceptedRequest.id())
                        .bind("status", acceptedRequest.status().name())
                        .bind("respondedAt", acceptedRequest.respondedAt())
                        .execute();
                if (requestRows != 1) {
                    throw new StorageException("Failed to persist friend request acceptance atomically");
                }

                if (notification != null) {
                    saveNotification(handle, notification);
                }
                return true;
            });
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Atomic friend-zone acceptance failed", e);
        }
    }

    @Override
    public boolean gracefulExitTransition(
            Match updatedMatch, Optional<Conversation> archivedConversation, Notification notification) {
        Objects.requireNonNull(updatedMatch, ERR_UPDATED_MATCH_NULL);
        Objects.requireNonNull(archivedConversation, ERR_ARCHIVED_CONVERSATION_NULL);

        try {
            return jdbi.inTransaction(handle -> {
                int matchRows = handle.createUpdate(SQL_UPDATE_MATCH_TRANSITION)
                        .bind(PARAM_ID, updatedMatch.getId())
                        .bind(PARAM_STATE, updatedMatch.getState().name())
                        .bind(PARAM_UPDATED_AT, updatedMatch.getUpdatedAt())
                        .bind(PARAM_ENDED_AT, updatedMatch.getEndedAt())
                        .bind(PARAM_ENDED_BY, updatedMatch.getEndedBy())
                        .bind(
                                PARAM_END_REASON,
                                updatedMatch.getEndReason() != null
                                        ? updatedMatch.getEndReason().name()
                                        : null)
                        .bind(PARAM_DELETED_AT, updatedMatch.getDeletedAt())
                        .execute();
                if (matchRows != 1) {
                    return false;
                }

                if (archivedConversation.isPresent()) {
                    Conversation conversation = archivedConversation.get();
                    int conversationRows = handle.createUpdate(SQL_ARCHIVE_CONVERSATION)
                            .bind(PARAM_CONVERSATION_ID, conversation.getId())
                            .bind(PARAM_ARCHIVED_AT_A, conversation.getUserAArchivedAt())
                            .bind(PARAM_ARCHIVE_REASON_A, conversation.getUserAArchiveReason())
                            .bind(PARAM_ARCHIVED_AT_B, conversation.getUserBArchivedAt())
                            .bind(PARAM_ARCHIVE_REASON_B, conversation.getUserBArchiveReason())
                            .execute();
                    if (conversationRows != 1) {
                        throw new StorageException("Failed to archive conversation atomically");
                    }
                }

                if (notification != null) {
                    saveNotification(handle, notification);
                }
                return true;
            });
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Atomic graceful-exit transition failed", e);
        }
    }

    @Override
    public boolean unmatchTransition(Match updatedMatch, Optional<Conversation> archivedConversation) {
        Objects.requireNonNull(updatedMatch, ERR_UPDATED_MATCH_NULL);
        Objects.requireNonNull(archivedConversation, ERR_ARCHIVED_CONVERSATION_NULL);

        try {
            return jdbi.inTransaction(handle -> {
                int matchRows = handle.createUpdate(SQL_UPDATE_MATCH_TRANSITION)
                        .bind(PARAM_ID, updatedMatch.getId())
                        .bind(PARAM_STATE, updatedMatch.getState().name())
                        .bind(PARAM_UPDATED_AT, updatedMatch.getUpdatedAt())
                        .bind(PARAM_ENDED_AT, updatedMatch.getEndedAt())
                        .bind(PARAM_ENDED_BY, updatedMatch.getEndedBy())
                        .bind(
                                PARAM_END_REASON,
                                updatedMatch.getEndReason() != null
                                        ? updatedMatch.getEndReason().name()
                                        : null)
                        .bind(PARAM_DELETED_AT, updatedMatch.getDeletedAt())
                        .execute();
                if (matchRows != 1) {
                    return false;
                }

                handle.createUpdate(SQL_SOFT_DELETE_PAIR_LIKES)
                        .bind("userA", updatedMatch.getUserA())
                        .bind("userB", updatedMatch.getUserB())
                        .bind("deletedAt", updatedMatch.getUpdatedAt())
                        .execute();

                if (archivedConversation.isPresent()) {
                    Conversation conversation = archivedConversation.get();
                    int conversationRows = handle.createUpdate(SQL_ARCHIVE_CONVERSATION)
                            .bind(PARAM_CONVERSATION_ID, conversation.getId())
                            .bind(PARAM_ARCHIVED_AT_A, conversation.getUserAArchivedAt())
                            .bind(PARAM_ARCHIVE_REASON_A, conversation.getUserAArchiveReason())
                            .bind(PARAM_ARCHIVED_AT_B, conversation.getUserBArchivedAt())
                            .bind(PARAM_ARCHIVE_REASON_B, conversation.getUserBArchiveReason())
                            .execute();
                    if (conversationRows != 1) {
                        throw new StorageException("Failed to archive conversation during atomic unmatch");
                    }
                }

                return true;
            });
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Atomic unmatch transition failed", e);
        }
    }

    @Override
    public boolean blockTransition(
            UUID blockerId, UUID blockedId, Optional<Match> updatedMatch, Optional<Conversation> archivedConversation) {
        Objects.requireNonNull(blockerId, "blockerId cannot be null");
        Objects.requireNonNull(blockedId, "blockedId cannot be null");
        Objects.requireNonNull(updatedMatch, ERR_UPDATED_MATCH_NULL);
        Objects.requireNonNull(archivedConversation, ERR_ARCHIVED_CONVERSATION_NULL);

        try {
            return jdbi.inTransaction(handle -> {
                if (!persistBlockMatch(handle, updatedMatch)) {
                    return false;
                }

                persistBlockedConversation(handle, archivedConversation);
                return true;
            });
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Atomic block transition failed", e);
        }
    }

    private static boolean persistBlockMatch(org.jdbi.v3.core.Handle handle, Optional<Match> updatedMatch) {
        if (updatedMatch.isEmpty()) {
            return true;
        }

        Match match = updatedMatch.get();
        int matchRows;
        try (var update = handle.createUpdate(SQL_UPDATE_MATCH_TRANSITION)) {
            matchRows = update.bind(PARAM_ID, match.getId())
                    .bind(PARAM_STATE, match.getState().name())
                    .bind(PARAM_UPDATED_AT, match.getUpdatedAt())
                    .bind(PARAM_ENDED_AT, match.getEndedAt())
                    .bind(PARAM_ENDED_BY, match.getEndedBy())
                    .bind(
                            PARAM_END_REASON,
                            match.getEndReason() != null ? match.getEndReason().name() : null)
                    .bind(PARAM_DELETED_AT, match.getDeletedAt())
                    .execute();
        }
        return matchRows == 1;
    }

    private static void persistBlockedConversation(
            org.jdbi.v3.core.Handle handle, Optional<Conversation> archivedConversation) {
        if (archivedConversation.isEmpty()) {
            return;
        }

        Conversation conversation = archivedConversation.get();
        int conversationRows;
        try (var update = handle.createUpdate(SQL_ARCHIVE_CONVERSATION)) {
            conversationRows = update.bind(PARAM_CONVERSATION_ID, conversation.getId())
                    .bind(PARAM_ARCHIVED_AT_A, conversation.getUserAArchivedAt())
                    .bind(PARAM_ARCHIVE_REASON_A, conversation.getUserAArchiveReason())
                    .bind(PARAM_ARCHIVED_AT_B, conversation.getUserBArchivedAt())
                    .bind(PARAM_ARCHIVE_REASON_B, conversation.getUserBArchiveReason())
                    .execute();
        }
        if (conversationRows != 1) {
            throw new StorageException("Failed to archive conversation atomically during block transition");
        }

        int visibilityRows;
        try (var update = handle.createUpdate(SQL_UPDATE_CONVERSATION_VISIBILITY)) {
            visibilityRows = update.bind(PARAM_CONVERSATION_ID, conversation.getId())
                    .bind(PARAM_VISIBLE_TO_USER_A, conversation.isVisibleToUserA())
                    .bind(PARAM_VISIBLE_TO_USER_B, conversation.isVisibleToUserB())
                    .execute();
        }
        if (visibilityRows != 1) {
            throw new StorageException("Failed to update conversation visibility atomically during block transition");
        }
    }

    @Override
    public void delete(String matchId) {
        matchDao.delete(matchId);
    }

    @Override
    public int purgeDeletedBefore(Instant threshold) {
        return matchDao.purgeDeletedBefore(threshold);
    }

    private void saveNotification(org.jdbi.v3.core.Handle handle, Notification notification) {
        String dataJson = serializeNotificationData(notification.data());
        handle.execute(
                SQL_INSERT_NOTIFICATION,
                notification.id(),
                notification.userId(),
                notification.type().name(),
                notification.title(),
                notification.message(),
                notification.createdAt(),
                notification.isRead(),
                dataJson);
    }

    private String serializeNotificationData(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to serialize notification data", e);
        }
    }

    private static boolean isPositiveLikeDirection(Like.Direction direction) {
        return direction == Like.Direction.LIKE || direction == Like.Direction.SUPER_LIKE;
    }

    @Override
    public boolean atomicUndoDelete(UUID likeId, String matchId) {
        try {
            return jdbi.inTransaction(handle -> {
                int likesDeleted = handle.createUpdate(
                                "UPDATE likes SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id AND deleted_at IS NULL")
                        .bind("id", likeId)
                        .execute();

                if (likesDeleted == 0) {
                    return false;
                }

                if (matchId != null) {
                    handle.createUpdate(
                                    "UPDATE matches SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id AND deleted_at IS NULL")
                            .bind("id", matchId)
                            .execute();
                }

                return true;
            });
        } catch (Exception e) {
            throw new StorageException("Atomic undo delete failed", e);
        }
    }

    @SuppressWarnings("unused") // Accessed reflectively by @BindBean
    private static final class UndoStateBindings {

        private final UUID userId;
        private final UUID likeId;
        private final UUID whoLikes;
        private final UUID whoGotLiked;
        private final String direction;
        private final Instant likeCreatedAt;
        private final String matchId;
        private final Instant expiresAt;

        private UndoStateBindings(Undo state) {
            Objects.requireNonNull(state, "state cannot be null");
            Like like = state.like();
            this.userId = state.userId();
            this.likeId = like.id();
            this.whoLikes = like.whoLikes();
            this.whoGotLiked = like.whoGotLiked();
            this.direction = like.direction().name();
            this.likeCreatedAt = like.createdAt();
            this.matchId = state.matchId();
            this.expiresAt = state.expiresAt();
        }

        public UUID getUserId() {
            return userId;
        }

        public UUID getLikeId() {
            return likeId;
        }

        public UUID getWhoLikes() {
            return whoLikes;
        }

        public UUID getWhoGotLiked() {
            return whoGotLiked;
        }

        public String getDirection() {
            return direction;
        }

        public Instant getLikeCreatedAt() {
            return likeCreatedAt;
        }

        public String getMatchId() {
            return matchId;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }
    }

    private final class UndoStorageAdapter implements Undo.Storage {

        @Override
        public void save(Undo state) {
            undoDao.upsert(new UndoStateBindings(state));
        }

        @Override
        public Optional<Undo> findByUserId(UUID userId) {
            return Optional.ofNullable(undoDao.findByUserId(userId));
        }

        @Override
        public boolean delete(UUID userId) {
            return undoDao.deleteByUserId(userId) > 0;
        }

        @Override
        public int deleteExpired(Instant now) {
            return undoDao.deleteExpired(now);
        }

        @Override
        public List<Undo> findAll() {
            return undoDao.findAll();
        }
    }

    @RegisterRowMapper(LikeMapper.class)
    @RegisterRowMapper(LikeTimeEntryMapper.class)
    private interface LikeDao {

        @SqlQuery("""
                    SELECT id, who_likes, who_got_liked, direction, created_at
                    FROM likes
                WHERE who_likes = :fromUserId
                  AND who_got_liked = :toUserId
                  AND deleted_at IS NULL
                    """)
        Optional<Like> getLike(@Bind("fromUserId") UUID fromUserId, @Bind("toUserId") UUID toUserId);

        @SqlQuery(SQL_ACTIVE_LIKE_BY_ID)
        Optional<Like> getLikeById(@Bind("likeId") UUID likeId);

        @SqlUpdate("""
                MERGE INTO likes (id, who_likes, who_got_liked, direction, created_at, deleted_at)
                KEY (who_likes, who_got_liked)
                VALUES (:id, :whoLikes, :whoGotLiked, :direction, :createdAt, NULL)
                """)
        void save(@BindMethods Like like);

        @SqlQuery("""
                SELECT EXISTS (
                    SELECT 1 FROM likes
                WHERE who_likes = :from
                  AND who_got_liked = :to
                  AND deleted_at IS NULL
                )
                """)
        boolean exists(@Bind("from") UUID from, @Bind("to") UUID to);

        @SqlQuery("""
                SELECT EXISTS (
                    SELECT 1 FROM likes l1
                    JOIN likes l2 ON l1.who_likes = l2.who_got_liked
                                 AND l1.who_got_liked = l2.who_likes
                    WHERE l1.who_likes = :a AND l1.who_got_liked = :b
                            AND l1.direction IN ('LIKE', 'SUPER_LIKE')
                            AND l2.direction IN ('LIKE', 'SUPER_LIKE')
                            AND l1.deleted_at IS NULL AND l2.deleted_at IS NULL
                )
                """)
        boolean mutualLikeExists(@Bind("a") UUID a, @Bind("b") UUID b);

        @SqlQuery("SELECT who_got_liked FROM likes WHERE who_likes = :userId AND deleted_at IS NULL")
        Set<UUID> getLikedOrPassedUserIds(@Bind("userId") UUID userId);

        @SqlQuery("""
                SELECT who_likes FROM likes
            WHERE who_got_liked = :userId
              AND direction IN ('LIKE', 'SUPER_LIKE')
              AND deleted_at IS NULL
                """)
        Set<UUID> getUserIdsWhoLiked(@Bind("userId") UUID userId);

        @SqlQuery("""
                SELECT who_likes, created_at FROM likes
            WHERE who_got_liked = :userId
              AND direction IN ('LIKE', 'SUPER_LIKE')
              AND deleted_at IS NULL
                """)
        List<LikeTimeEntry> getLikeTimesInternal(@Bind("userId") UUID userId);

        default List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
            return getLikeTimesInternal(userId).stream()
                    .map(e -> Map.entry(e.userId(), e.likedAt()))
                    .toList();
        }

        @SqlQuery("""
                SELECT COUNT(*) FROM likes
                WHERE who_likes = :userId AND direction = :direction AND deleted_at IS NULL
                """)
        int countByDirection(@Bind("userId") UUID userId, @Bind("direction") Like.Direction direction);

        @SqlQuery("""
                SELECT COUNT(*) FROM likes
                WHERE who_got_liked = :userId AND direction = :direction AND deleted_at IS NULL
                """)
        int countReceivedByDirection(@Bind("userId") UUID userId, @Bind("direction") Like.Direction direction);

        @SqlQuery("""
            SELECT COUNT(*) FROM likes l1
            JOIN likes l2 ON l1.who_likes = l2.who_got_liked
                     AND l1.who_got_liked = l2.who_likes
            WHERE l1.who_likes = :userId
              AND l1.direction IN ('LIKE', 'SUPER_LIKE')
              AND l2.direction IN ('LIKE', 'SUPER_LIKE')
              AND l1.deleted_at IS NULL
              AND l2.deleted_at IS NULL
            """)
        int countMutualLikes(@Bind("userId") UUID userId);

        @SqlQuery("""
            SELECT COUNT(*) FROM likes
            WHERE who_likes = :userId
              AND direction = 'LIKE'
              AND created_at >= :startOfDay
              AND deleted_at IS NULL
            """)
        int countLikesToday(@Bind("userId") UUID userId, @Bind("startOfDay") Instant startOfDay);

        @SqlQuery("""
            SELECT COUNT(*) FROM likes
            WHERE who_likes = :userId
              AND direction = 'SUPER_LIKE'
              AND created_at >= :startOfDay
              AND deleted_at IS NULL
            """)
        int countSuperLikesToday(@Bind("userId") UUID userId, @Bind("startOfDay") Instant startOfDay);

        @SqlQuery("""
                SELECT COUNT(*) FROM likes
                WHERE who_likes = :userId
                  AND direction = 'PASS'
                  AND created_at >= :startOfDay
              AND deleted_at IS NULL
                """)
        int countPassesToday(@Bind("userId") UUID userId, @Bind("startOfDay") Instant startOfDay);

        @SqlUpdate("UPDATE likes SET deleted_at = CURRENT_TIMESTAMP WHERE id = :likeId AND deleted_at IS NULL")
        void delete(@Bind("likeId") UUID likeId);

        @SqlUpdate(SQL_SOFT_DELETE_OWNED_LIKE)
        int deleteLikeOwnedBy(@Bind("ownerUserId") UUID ownerUserId, @Bind("likeId") UUID likeId);
    }

    @RegisterRowMapper(MatchMapper.class)
    private interface MatchDao {
        String MATCH_COLUMNS =
                "id, user_a, user_b, created_at, updated_at, state, ended_at, ended_by, end_reason, deleted_at";

        @SqlUpdate("""
                MERGE INTO matches (
                    id, user_a, user_b, created_at, updated_at, state, ended_at, ended_by, end_reason, deleted_at
                ) KEY (id)
                VALUES (
                    :id, :userA, :userB, :createdAt, :updatedAt, :state, :endedAt, :endedBy, :endReason, :deletedAt
                )
                """)
        void save(@BindBean Match match);

        @SqlUpdate("""
                UPDATE matches
                SET state = :state,
                    updated_at = :updatedAt,
                    ended_at = :endedAt,
                    ended_by = :endedBy,
                    end_reason = :endReason,
                    deleted_at = :deletedAt
                WHERE id = :id
                """)
        void update(@BindBean Match match);

        @SqlQuery("SELECT " + MATCH_COLUMNS + " FROM matches WHERE id = :matchId AND deleted_at IS NULL")
        Optional<Match> get(@Bind("matchId") String matchId);

        @SqlQuery("SELECT EXISTS(SELECT 1 FROM matches WHERE id = :matchId AND deleted_at IS NULL)")
        boolean exists(@Bind("matchId") String matchId);

        @SqlQuery("SELECT " + MATCH_COLUMNS + " FROM matches WHERE (user_a = :userId OR user_b = :userId) "
                + "AND state = 'ACTIVE' AND deleted_at IS NULL")
        List<Match> getActiveMatchesFor(@Bind("userId") UUID userId);

        @SqlQuery("SELECT " + MATCH_COLUMNS + " FROM matches WHERE (user_a = :userId OR user_b = :userId) "
                + "AND deleted_at IS NULL")
        List<Match> getAllMatchesFor(@Bind("userId") UUID userId);

        @SqlQuery("SELECT CASE WHEN user_a = :userId THEN user_b ELSE user_a END AS counterpart_id "
                + "FROM matches WHERE (user_a = :userId OR user_b = :userId) AND deleted_at IS NULL")
        Set<UUID> getMatchedCounterpartIds(@Bind("userId") UUID userId);

        /**
         * Counts all non-deleted matches for the user (active + ended). Used as the
         * {@code totalCount} for {@code getPageOfMatchesFor}.
         */
        @SqlQuery("SELECT COUNT(*) FROM matches WHERE (user_a = :userId OR user_b = :userId) AND deleted_at IS NULL")
        int countMatchesFor(@Bind("userId") UUID userId);

        /**
         * Counts only active, non-deleted matches for the user. Used as the totalCount
         * for {@code getPageOfActiveMatchesFor}.
         */
        @SqlQuery("SELECT COUNT(*) FROM matches WHERE (user_a = :userId OR user_b = :userId) "
                + "AND state = 'ACTIVE' AND deleted_at IS NULL")
        int countActiveMatchesFor(@Bind("userId") UUID userId);

        /**
         * Returns a page of ALL matches (active and ended), newest first.
         * The DB drives the slice — no in-memory list accumulation.
         */
        @SqlQuery("SELECT " + MATCH_COLUMNS + " FROM matches WHERE (user_a = :userId OR user_b = :userId) "
                + "AND deleted_at IS NULL ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
        List<Match> getPageOfMatchesFor(
                @Bind("userId") UUID userId, @Bind("offset") int offset, @Bind("limit") int limit);

        /**
         * Returns a page of ACTIVE matches only, newest first.
         * The DB drives the slice — no in-memory list accumulation.
         */
        @SqlQuery("SELECT " + MATCH_COLUMNS + " FROM matches WHERE (user_a = :userId OR user_b = :userId) "
                + "AND state = 'ACTIVE' AND deleted_at IS NULL ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
        List<Match> getPageOfActiveMatchesFor(
                @Bind("userId") UUID userId, @Bind("offset") int offset, @Bind("limit") int limit);

        @SqlUpdate("UPDATE matches SET deleted_at = CURRENT_TIMESTAMP WHERE id = :matchId")
        void delete(@Bind("matchId") String matchId);

        @SqlUpdate("DELETE FROM matches WHERE deleted_at < :threshold")
        int purgeDeletedBefore(@Bind("threshold") Instant threshold);
    }

    @RegisterRowMapper(UndoStateMapper.class)
    private interface UndoDao {

        @SqlUpdate("""
                MERGE INTO undo_states (
                    user_id, like_id, who_likes, who_got_liked, direction, like_created_at, match_id, expires_at
                ) KEY (user_id)
                VALUES (:userId, :likeId, :whoLikes, :whoGotLiked, :direction, :likeCreatedAt, :matchId, :expiresAt)
                """)
        void upsert(@BindBean UndoStateBindings bindings);

        @SqlQuery("""
                SELECT user_id, like_id, who_likes, who_got_liked, direction, like_created_at, match_id, expires_at
                FROM undo_states
                WHERE user_id = :userId
                """)
        Undo findByUserId(@Bind("userId") UUID userId);

        @SqlUpdate("DELETE FROM undo_states WHERE user_id = :userId")
        int deleteByUserId(@Bind("userId") UUID userId);

        @SqlUpdate("DELETE FROM undo_states WHERE expires_at < :now")
        int deleteExpired(@Bind("now") Instant now);

        @SqlQuery("""
                SELECT user_id, like_id, who_likes, who_got_liked, direction, like_created_at, match_id, expires_at
                FROM undo_states
                """)
        List<Undo> findAll();
    }

    /** Like-time DTO. */
    private record LikeTimeEntry(UUID userId, Instant likedAt) {}

    public static class LikeTimeEntryMapper implements RowMapper<LikeTimeEntry> {
        @Override
        public LikeTimeEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
            UUID userId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, COLUMN_WHO_LIKES);
            Instant likedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, COLUMN_CREATED_AT);
            return new LikeTimeEntry(userId, likedAt);
        }
    }

    public static class LikeMapper implements RowMapper<Like> {
        @Override
        public Like map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, PARAM_ID);
            var whoLikes = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, COLUMN_WHO_LIKES);
            var whoGotLiked = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "who_got_liked");
            var direction = JdbiTypeCodecs.SqlRowReaders.readEnum(rs, PARAM_DIRECTION, Like.Direction.class);
            var createdAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, COLUMN_CREATED_AT);

            return new Like(id, whoLikes, whoGotLiked, direction, createdAt);
        }
    }

    public static class MatchMapper implements RowMapper<Match> {
        @Override
        public Match map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Match(
                    rs.getString("id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_a"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_b"),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, COLUMN_CREATED_AT),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "updated_at"),
                    JdbiTypeCodecs.SqlRowReaders.readEnum(rs, PARAM_STATE, MatchState.class),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "ended_at"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "ended_by"),
                    JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "end_reason", MatchArchiveReason.class),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "deleted_at"));
        }
    }

    public static class UndoStateMapper implements RowMapper<Undo> {
        @Override
        public Undo map(ResultSet rs, StatementContext ctx) throws SQLException {
            UUID userId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_id");
            UUID likeId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "like_id");
            UUID whoLikes = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, COLUMN_WHO_LIKES);
            UUID whoGotLiked = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "who_got_liked");
            Like.Direction direction = Like.Direction.valueOf(rs.getString(PARAM_DIRECTION));
            Instant likeCreatedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "like_created_at");
            String matchId = rs.getString("match_id");
            Instant expiresAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "expires_at");

            ConnectionModels.Like like =
                    new ConnectionModels.Like(likeId, whoLikes, whoGotLiked, direction, likeCreatedAt);
            return new Undo(userId, like, matchId, expiresAt);
        }
    }
}
