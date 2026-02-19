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
    private static final String PARAM_STATE = "state";
    private static final String PARAM_ENDED_AT = "endedAt";
    private static final String PARAM_ENDED_BY = "endedBy";
    private static final String PARAM_END_REASON = "endReason";
    private static final String PARAM_DELETED_AT = "deletedAt";
    private static final String COLUMN_WHO_LIKES = "who_likes";
    private static final String COLUMN_CREATED_AT = "created_at";

    private static final String SQL_ACTIVE_LIKE_EXISTS = """
        SELECT EXISTS (
        SELECT 1
        FROM likes
        WHERE who_likes = :whoLikes
          AND who_got_liked = :whoGotLiked
          AND deleted_at IS NULL
        )
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
          AND l1.direction = 'LIKE'
          AND l2.direction = 'LIKE'
          AND l1.deleted_at IS NULL
          AND l2.deleted_at IS NULL
        )
        """;

    private static final String SQL_ACTIVE_MATCH_EXISTS =
            "SELECT EXISTS(SELECT 1 FROM matches WHERE id = :id AND deleted_at IS NULL)";

    private static final String SQL_UPSERT_MATCH = """
        MERGE INTO matches (
        id, user_a, user_b, created_at, state, ended_at, ended_by, end_reason, deleted_at
        ) KEY (id)
        VALUES (
        :id, :userA, :userB, :createdAt, :state, :endedAt, :endedBy, :endReason, :deletedAt
        )
        """;

    private static final String SQL_UPDATE_MATCH_TRANSITION = """
        UPDATE matches
        SET state = :state,
        ended_at = :endedAt,
        ended_by = :endedBy,
        end_reason = :endReason,
        deleted_at = :deletedAt
        WHERE id = :id AND deleted_at IS NULL
        """;

    private static final String SQL_ACCEPT_FRIEND_REQUEST = """
        UPDATE friend_requests
        SET status = :status,
        responded_at = :respondedAt
        WHERE id = :id AND status = 'PENDING'
        """;

    private static final String SQL_ARCHIVE_CONVERSATION = """
        UPDATE conversations
        SET archived_at = :archivedAt,
        archive_reason = :archiveReason
        WHERE id = :conversationId
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
    public void save(Like like) {
        likeDao.save(like);
    }

    @Override
    public LikeMatchWriteResult saveLikeAndMaybeCreateMatch(Like like) {
        Objects.requireNonNull(like, "like cannot be null");

        try {
            return jdbi.inTransaction(handle -> {
                boolean likeAlreadyExists = handle.createQuery(SQL_ACTIVE_LIKE_EXISTS)
                        .bind(PARAM_WHO_LIKES, like.whoLikes())
                        .bind(PARAM_WHO_GOT_LIKED, like.whoGotLiked())
                        .mapTo(Boolean.class)
                        .one();
                if (likeAlreadyExists) {
                    return LikeMatchWriteResult.duplicateLike();
                }

                handle.createUpdate(SQL_UPSERT_LIKE)
                        .bind(PARAM_ID, like.id())
                        .bind(PARAM_WHO_LIKES, like.whoLikes())
                        .bind(PARAM_WHO_GOT_LIKED, like.whoGotLiked())
                        .bind(PARAM_DIRECTION, like.direction().name())
                        .bind(PARAM_CREATED_AT, like.createdAt())
                        .execute();

                if (like.direction() != Like.Direction.LIKE) {
                    return LikeMatchWriteResult.likeOnly();
                }

                boolean mutualLikeExists = handle.createQuery(SQL_MUTUAL_LIKE_EXISTS)
                        .bind(PARAM_WHO_LIKES, like.whoLikes())
                        .bind(PARAM_WHO_GOT_LIKED, like.whoGotLiked())
                        .mapTo(Boolean.class)
                        .one();
                if (!mutualLikeExists) {
                    return LikeMatchWriteResult.likeOnly();
                }

                String matchId = Match.generateId(like.whoLikes(), like.whoGotLiked());
                boolean activeMatchExists = handle.createQuery(SQL_ACTIVE_MATCH_EXISTS)
                        .bind(PARAM_ID, matchId)
                        .mapTo(Boolean.class)
                        .one();
                if (activeMatchExists) {
                    return LikeMatchWriteResult.likeOnly();
                }

                Match match = Match.create(like.whoLikes(), like.whoGotLiked());
                handle.createUpdate(SQL_UPSERT_MATCH)
                        .bind(PARAM_ID, match.getId())
                        .bind("userA", match.getUserA())
                        .bind("userB", match.getUserB())
                        .bind(PARAM_CREATED_AT, match.getCreatedAt())
                        .bind(PARAM_STATE, match.getState().name())
                        .bind(PARAM_ENDED_AT, match.getEndedAt())
                        .bind(PARAM_ENDED_BY, match.getEndedBy())
                        .bind(
                                PARAM_END_REASON,
                                match.getEndReason() != null
                                        ? match.getEndReason().name()
                                        : null)
                        .bind(PARAM_DELETED_AT, match.getDeletedAt())
                        .execute();

                return LikeMatchWriteResult.likeAndMatch(match);
            });
        } catch (Exception e) {
            throw new StorageException("Atomic like->match persistence failed", e);
        }
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
    public int countPassesToday(UUID userId, Instant startOfDay) {
        return likeDao.countPassesToday(userId, startOfDay);
    }

    @Override
    public void delete(UUID likeId) {
        likeDao.delete(likeId);
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
    public boolean supportsAtomicRelationshipTransitions() {
        return true;
    }

    @Override
    public boolean acceptFriendZoneTransition(
            Match updatedMatch, FriendRequest acceptedRequest, Notification notification) {
        Objects.requireNonNull(updatedMatch, "updatedMatch cannot be null");
        Objects.requireNonNull(acceptedRequest, "acceptedRequest cannot be null");
        Objects.requireNonNull(notification, "notification cannot be null");

        try {
            return jdbi.inTransaction(handle -> {
                int matchRows = handle.createUpdate(SQL_UPDATE_MATCH_TRANSITION)
                        .bind(PARAM_ID, updatedMatch.getId())
                        .bind(PARAM_STATE, updatedMatch.getState().name())
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

                saveNotification(handle, notification);
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
        Objects.requireNonNull(updatedMatch, "updatedMatch cannot be null");
        Objects.requireNonNull(archivedConversation, "archivedConversation cannot be null");
        Objects.requireNonNull(notification, "notification cannot be null");

        try {
            return jdbi.inTransaction(handle -> {
                int matchRows = handle.createUpdate(SQL_UPDATE_MATCH_TRANSITION)
                        .bind(PARAM_ID, updatedMatch.getId())
                        .bind(PARAM_STATE, updatedMatch.getState().name())
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
                            .bind("conversationId", conversation.getId())
                            .bind("archivedAt", conversation.getArchivedAt())
                            .bind("archiveReason", conversation.getArchiveReason())
                            .execute();
                    if (conversationRows != 1) {
                        throw new StorageException("Failed to archive conversation atomically");
                    }
                }

                saveNotification(handle, notification);
                return true;
            });
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Atomic graceful-exit transition failed", e);
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

        @SqlUpdate("""
            MERGE INTO likes (id, who_likes, who_got_liked, direction, created_at, deleted_at)
            KEY (who_likes, who_got_liked)
            VALUES (:id, :whoLikes, :whoGotLiked, :direction, :createdAt, NULL)
            """)
        void save(@BindBean Like like);

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
                                            AND l1.direction = 'LIKE' AND l2.direction = 'LIKE'
                                            AND l1.deleted_at IS NULL AND l2.deleted_at IS NULL
                )
                """)
        boolean mutualLikeExists(@Bind("a") UUID a, @Bind("b") UUID b);

        @SqlQuery("SELECT who_got_liked FROM likes WHERE who_likes = :userId AND deleted_at IS NULL")
        Set<UUID> getLikedOrPassedUserIds(@Bind("userId") UUID userId);

        @SqlQuery("""
                SELECT who_likes FROM likes
                WHERE who_got_liked = :userId AND direction = 'LIKE' AND deleted_at IS NULL
                """)
        Set<UUID> getUserIdsWhoLiked(@Bind("userId") UUID userId);

        @SqlQuery("""
                SELECT who_likes, created_at FROM likes
                WHERE who_got_liked = :userId AND direction = 'LIKE' AND deleted_at IS NULL
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
                  AND l1.direction = 'LIKE' AND l2.direction = 'LIKE'
                                    AND l1.deleted_at IS NULL AND l2.deleted_at IS NULL
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
                  AND direction = 'PASS'
                  AND created_at >= :startOfDay
                                    AND deleted_at IS NULL
                """)
        int countPassesToday(@Bind("userId") UUID userId, @Bind("startOfDay") Instant startOfDay);

        @SqlUpdate("UPDATE likes SET deleted_at = CURRENT_TIMESTAMP WHERE id = :likeId AND deleted_at IS NULL")
        void delete(@Bind("likeId") UUID likeId);
    }

    @RegisterRowMapper(MatchMapper.class)
    private interface MatchDao {
        String MATCH_COLUMNS = "id, user_a, user_b, created_at, state, ended_at, ended_by, end_reason, deleted_at";

        @SqlUpdate("""
                MERGE INTO matches (
                    id, user_a, user_b, created_at, state, ended_at, ended_by, end_reason, deleted_at
                ) KEY (id)
                VALUES (
                    :id, :userA, :userB, :createdAt, :state, :endedAt, :endedBy, :endReason, :deletedAt
                )
                """)
        void save(@BindBean Match match);

        @SqlUpdate("""
                UPDATE matches
                SET state = :state,
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
            Match match = new Match(
                    rs.getString("id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_a"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_b"),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, COLUMN_CREATED_AT),
                    JdbiTypeCodecs.SqlRowReaders.readEnum(rs, PARAM_STATE, MatchState.class),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "ended_at"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "ended_by"),
                    JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "end_reason", MatchArchiveReason.class));
            match.restoreDeletedAt(JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "deleted_at"));
            return match;
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
