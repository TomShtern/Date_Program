package datingapp.storage.mapper;

import datingapp.core.UserInteractions.Report;
import datingapp.core.UserInteractions.Report.Reason;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for Report records.
 * Maps database rows to Report domain objects.
 */
public class ReportMapper implements RowMapper<Report> {

    @Override
    public Report map(ResultSet rs, StatementContext ctx) throws SQLException {
        var id = MapperHelper.readUuid(rs, "id");
        var reporterId = MapperHelper.readUuid(rs, "reporter_id");
        var reportedUserId = MapperHelper.readUuid(rs, "reported_user_id");
        var reason = MapperHelper.readEnum(rs, "reason", Reason.class);
        var description = rs.getString("description");
        var createdAt = MapperHelper.readInstant(rs, "created_at");

        return new Report(id, reporterId, reportedUserId, reason, description, createdAt);
    }
}
