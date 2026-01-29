package datingapp.storage.mapper;

import datingapp.core.Match;
import datingapp.core.Match.ArchiveReason;
import datingapp.core.Match.State;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for Match objects.
 * Maps database rows to Match domain objects.
 */
public class MatchMapper implements RowMapper<Match> {

    @Override
    public Match map(ResultSet rs, StatementContext ctx) throws SQLException {
        String id = rs.getString("id");
        var userA = MapperHelper.readUuid(rs, "user_a");
        var userB = MapperHelper.readUuid(rs, "user_b");
        var createdAt = MapperHelper.readInstant(rs, "created_at");
        var state = MapperHelper.readEnum(rs, "state", State.class);
        var endedAt = MapperHelper.readInstant(rs, "ended_at");
        var endedBy = MapperHelper.readUuid(rs, "ended_by");
        var endReason = MapperHelper.readEnum(rs, "end_reason", ArchiveReason.class);

        return new Match(id, userA, userB, createdAt, state, endedAt, endedBy, endReason);
    }
}
