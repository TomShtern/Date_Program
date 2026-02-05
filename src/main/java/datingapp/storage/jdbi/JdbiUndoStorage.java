package datingapp.storage.jdbi;

import datingapp.core.UndoState;
import datingapp.core.UserInteractions;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI interface for undo state storage.
 * Stores one undo state per user (latest swipe only).
 */
@RegisterRowMapper(JdbiUndoStorage.UndoStateMapper.class)
public interface JdbiUndoStorage {

    @SqlUpdate("""
            MERGE INTO undo_states (user_id, like_id, who_likes, who_got_liked, direction, like_created_at, match_id, expires_at)
            KEY (user_id)
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
    UndoState findByUserId(@Bind("userId") UUID userId);

    @SqlUpdate("DELETE FROM undo_states WHERE user_id = :userId")
    int deleteByUserId(@Bind("userId") UUID userId);

    @SqlUpdate("DELETE FROM undo_states WHERE expires_at < :now")
    int deleteExpired(@Bind("now") Instant now);

    @SqlQuery(
            "SELECT user_id, like_id, who_likes, who_got_liked, direction, like_created_at, match_id, expires_at FROM undo_states")
    List<UndoState> findAll();

    /** Row mapper for UndoState. */
    class UndoStateMapper implements RowMapper<UndoState> {
        @Override
        public UndoState map(ResultSet rs, StatementContext ctx) throws SQLException {
            UUID userId = rs.getObject("user_id", UUID.class);
            UUID likeId = rs.getObject("like_id", UUID.class);
            UUID whoLikes = rs.getObject("who_likes", UUID.class);
            UUID whoGotLiked = rs.getObject("who_got_liked", UUID.class);
            String directionStr = rs.getString("direction");
            Instant likeCreatedAt = rs.getTimestamp("like_created_at").toInstant();
            String matchId = rs.getString("match_id");
            Instant expiresAt = rs.getTimestamp("expires_at").toInstant();

            UserInteractions.Like.Direction direction = UserInteractions.Like.Direction.valueOf(directionStr);
            UserInteractions.Like like =
                    new UserInteractions.Like(likeId, whoLikes, whoGotLiked, direction, likeCreatedAt);

            return new UndoState(userId, like, matchId, expiresAt);
        }
    }
}
