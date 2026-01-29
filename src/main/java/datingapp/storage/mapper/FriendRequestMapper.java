package datingapp.storage.mapper;

import datingapp.core.Social.FriendRequest;
import datingapp.core.Social.FriendRequest.Status;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for FriendRequest records.
 * Maps database rows to FriendRequest domain objects.
 */
public class FriendRequestMapper implements RowMapper<FriendRequest> {

    @Override
    public FriendRequest map(ResultSet rs, StatementContext ctx) throws SQLException {
        var id = MapperHelper.readUuid(rs, "id");
        var fromUserId = MapperHelper.readUuid(rs, "from_user_id");
        var toUserId = MapperHelper.readUuid(rs, "to_user_id");
        var createdAt = MapperHelper.readInstant(rs, "created_at");
        var status = MapperHelper.readEnum(rs, "status", Status.class);
        var respondedAt = MapperHelper.readInstantNullable(rs, "responded_at");

        return new FriendRequest(id, fromUserId, toUserId, createdAt, status, respondedAt);
    }
}
