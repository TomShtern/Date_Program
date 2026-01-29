package datingapp.storage.mapper;

import datingapp.core.Messaging.Message;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for Message records.
 * Maps database rows to Message domain objects.
 */
public class MessageMapper implements RowMapper<Message> {

    @Override
    public Message map(ResultSet rs, StatementContext ctx) throws SQLException {
        var id = MapperHelper.readUuid(rs, "id");
        var conversationId = rs.getString("conversation_id");
        var senderId = MapperHelper.readUuid(rs, "sender_id");
        var content = rs.getString("content");
        var createdAt = MapperHelper.readInstant(rs, "created_at");

        return new Message(id, conversationId, senderId, content, createdAt);
    }
}
