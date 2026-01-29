package datingapp.storage.mapper;

import datingapp.core.Stats.UserStats;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/** JDBI mapper for UserStats domain model. */
public class UserStatsMapper implements RowMapper<UserStats> {

    @Override
    public UserStats map(ResultSet rs, StatementContext ctx) throws SQLException {
        return new UserStats(
                MapperHelper.readUuid(rs, "id"),
                MapperHelper.readUuid(rs, "user_id"),
                MapperHelper.readInstant(rs, "computed_at"),
                rs.getInt("total_swipes_given"),
                rs.getInt("likes_given"),
                rs.getInt("passes_given"),
                rs.getDouble("like_ratio"),
                rs.getInt("total_swipes_received"),
                rs.getInt("likes_received"),
                rs.getInt("passes_received"),
                rs.getDouble("incoming_like_ratio"),
                rs.getInt("total_matches"),
                rs.getInt("active_matches"),
                rs.getDouble("match_rate"),
                rs.getInt("blocks_given"),
                rs.getInt("blocks_received"),
                rs.getInt("reports_given"),
                rs.getInt("reports_received"),
                rs.getDouble("reciprocity_score"),
                rs.getDouble("selectiveness_score"),
                rs.getDouble("attractiveness_score"));
    }
}
