package datingapp.storage.mapper;

import datingapp.core.SwipeSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for SwipeSession entities.
 * Maps database rows to SwipeSession domain objects.
 */
public class SwipeSessionMapper implements RowMapper<SwipeSession> {

    @Override
    public SwipeSession map(ResultSet rs, StatementContext ctx) throws SQLException {
        UUID id = MapperHelper.readUuid(rs, "id");
        UUID userId = MapperHelper.readUuid(rs, "user_id");
        var startedAt = MapperHelper.readInstant(rs, "started_at");
        var lastActivityAt = MapperHelper.readInstant(rs, "last_activity_at");
        var endedAt = MapperHelper.readInstantOptional(rs, "ended_at");
        SwipeSession.State state = SwipeSession.State.valueOf(rs.getString("state"));
        int swipeCount = rs.getInt("swipe_count");
        int likeCount = rs.getInt("like_count");
        int passCount = rs.getInt("pass_count");
        int matchCount = rs.getInt("match_count");

        return new SwipeSession(
                id, userId, startedAt, lastActivityAt, endedAt, state, swipeCount, likeCount, passCount, matchCount);
    }
}
