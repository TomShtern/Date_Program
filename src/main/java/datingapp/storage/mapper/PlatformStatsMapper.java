package datingapp.storage.mapper;

import datingapp.core.Stats.PlatformStats;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/** JDBI mapper for PlatformStats domain model. */
public class PlatformStatsMapper implements RowMapper<PlatformStats> {

    @Override
    public PlatformStats map(ResultSet rs, StatementContext ctx) throws SQLException {
        return new PlatformStats(
                MapperHelper.readUuid(rs, "id"),
                MapperHelper.readInstant(rs, "computed_at"),
                rs.getInt("total_active_users"),
                rs.getDouble("avg_likes_received"),
                rs.getDouble("avg_likes_given"),
                rs.getDouble("avg_match_rate"),
                rs.getDouble("avg_like_ratio"));
    }
}
