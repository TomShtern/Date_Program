package datingapp.storage.jdbi;

import datingapp.core.connection.ConnectionModels;
import datingapp.core.connection.ConnectionModels.Like;
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
    public void delete(String matchId) {
        matchDao.delete(matchId);
    }

    @Override
    public int purgeDeletedBefore(Instant threshold) {
        return matchDao.purgeDeletedBefore(threshold);
    }

    @Override
    public boolean atomicUndoDelete(UUID likeId, String matchId) {
        try {
            return jdbi.inTransaction(handle -> {
                int likesDeleted = handle.createUpdate("DELETE FROM likes WHERE id = :id")
                        .bind("id", likeId)
                        .execute();

                if (likesDeleted == 0) {
                    return false;
                }

                if (matchId != null) {
                    handle.createUpdate("DELETE FROM matches WHERE id = :id")
                            .bind("id", matchId)
                            .execute();
                }

                return true;
            });
        } catch (Exception e) {
            throw new StorageException("Atomic undo delete failed", e);
        }
    }

    private final class UndoStorageAdapter implements Undo.Storage {

        @Override
        public void save(Undo state) {
            var like = state.like();
            undoDao.upsert(
                    state.userId(),
                    like.id(),
                    like.whoLikes(),
                    like.whoGotLiked(),
                    like.direction().name(),
                    like.createdAt(),
                    state.matchId(),
                    state.expiresAt());
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
                WHERE who_likes = :fromUserId AND who_got_liked = :toUserId
                """)
        Optional<Like> getLike(@Bind("fromUserId") UUID fromUserId, @Bind("toUserId") UUID toUserId);

        @SqlUpdate("""
                INSERT INTO likes (id, who_likes, who_got_liked, direction, created_at)
                VALUES (:id, :whoLikes, :whoGotLiked, :direction, :createdAt)
                """)
        void save(@BindBean Like like);

        @SqlQuery("""
                SELECT EXISTS (
                    SELECT 1 FROM likes
                    WHERE who_likes = :from AND who_got_liked = :to
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
                )
                """)
        boolean mutualLikeExists(@Bind("a") UUID a, @Bind("b") UUID b);

        @SqlQuery("SELECT who_got_liked FROM likes WHERE who_likes = :userId")
        Set<UUID> getLikedOrPassedUserIds(@Bind("userId") UUID userId);

        @SqlQuery("""
                SELECT who_likes FROM likes
                WHERE who_got_liked = :userId AND direction = 'LIKE'
                """)
        Set<UUID> getUserIdsWhoLiked(@Bind("userId") UUID userId);

        @SqlQuery("""
                SELECT who_likes, created_at FROM likes
                WHERE who_got_liked = :userId AND direction = 'LIKE'
                """)
        List<LikeTimeEntry> getLikeTimesInternal(@Bind("userId") UUID userId);

        default List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
            return getLikeTimesInternal(userId).stream()
                    .map(e -> Map.entry(e.userId(), e.likedAt()))
                    .toList();
        }

        @SqlQuery("""
                SELECT COUNT(*) FROM likes
                WHERE who_likes = :userId AND direction = :direction
                """)
        int countByDirection(@Bind("userId") UUID userId, @Bind("direction") Like.Direction direction);

        @SqlQuery("""
                SELECT COUNT(*) FROM likes
                WHERE who_got_liked = :userId AND direction = :direction
                """)
        int countReceivedByDirection(@Bind("userId") UUID userId, @Bind("direction") Like.Direction direction);

        @SqlQuery("""
                SELECT COUNT(*) FROM likes l1
                JOIN likes l2 ON l1.who_likes = l2.who_got_liked
                             AND l1.who_got_liked = l2.who_likes
                WHERE l1.who_likes = :userId
                  AND l1.direction = 'LIKE' AND l2.direction = 'LIKE'
                """)
        int countMutualLikes(@Bind("userId") UUID userId);

        @SqlQuery("""
                SELECT COUNT(*) FROM likes
                WHERE who_likes = :userId
                  AND direction = 'LIKE'
                  AND created_at >= :startOfDay
                """)
        int countLikesToday(@Bind("userId") UUID userId, @Bind("startOfDay") Instant startOfDay);

        @SqlQuery("""
                SELECT COUNT(*) FROM likes
                WHERE who_likes = :userId
                  AND direction = 'PASS'
                  AND created_at >= :startOfDay
                """)
        int countPassesToday(@Bind("userId") UUID userId, @Bind("startOfDay") Instant startOfDay);

        @SqlUpdate("DELETE FROM likes WHERE id = :likeId")
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
        void upsert(
                @Bind("userId") UUID userId,
                @Bind("likeId") UUID likeId,
                @Bind("whoLikes") UUID whoLikes,
                @Bind("whoGotLiked") UUID whoGotLiked,
                @Bind("direction") String direction,
                @Bind("likeCreatedAt") Instant likeCreatedAt,
                @Bind("matchId") String matchId,
                @Bind("expiresAt") Instant expiresAt);

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
            UUID userId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "who_likes");
            Instant likedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at");
            return new LikeTimeEntry(userId, likedAt);
        }
    }

    public static class LikeMapper implements RowMapper<Like> {
        @Override
        public Like map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id");
            var whoLikes = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "who_likes");
            var whoGotLiked = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "who_got_liked");
            var direction = JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "direction", Like.Direction.class);
            var createdAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at");

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
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at"),
                    JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "state", MatchState.class),
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
            UUID whoLikes = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "who_likes");
            UUID whoGotLiked = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "who_got_liked");
            Like.Direction direction = Like.Direction.valueOf(rs.getString("direction"));
            Instant likeCreatedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "like_created_at");
            String matchId = rs.getString("match_id");
            Instant expiresAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "expires_at");

            ConnectionModels.Like like =
                    new ConnectionModels.Like(likeId, whoLikes, whoGotLiked, direction, likeCreatedAt);
            return new Undo(userId, like, matchId, expiresAt);
        }
    }
}
