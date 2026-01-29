package datingapp.storage.mapper;

import datingapp.core.Match;
import datingapp.core.Messaging.Conversation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for Conversation entities.
 * Maps database rows to mutable Conversation objects.
 */
public class ConversationMapper implements RowMapper<Conversation> {

    @Override
    public Conversation map(ResultSet rs, StatementContext ctx) throws SQLException {
        String id = rs.getString("id");
        UUID userA = MapperHelper.readUuid(rs, "user_a");
        UUID userB = MapperHelper.readUuid(rs, "user_b");
        Instant createdAt = MapperHelper.readInstant(rs, "created_at");
        Instant lastMessageAt = MapperHelper.readInstant(rs, "last_message_at");
        Instant userAReadAt = MapperHelper.readInstant(rs, "user_a_last_read_at");
        Instant userBReadAt = MapperHelper.readInstant(rs, "user_b_last_read_at");
        Instant archivedAt = MapperHelper.readInstant(rs, "archived_at");
        Match.ArchiveReason archiveReason = MapperHelper.readEnum(rs, "archive_reason", Match.ArchiveReason.class);
        boolean visibleToUserA = rs.getBoolean("visible_to_user_a");
        boolean visibleToUserB = rs.getBoolean("visible_to_user_b");

        return new Conversation(
                id,
                userA,
                userB,
                createdAt,
                lastMessageAt,
                userAReadAt,
                userBReadAt,
                archivedAt,
                archiveReason,
                visibleToUserA,
                visibleToUserB);
    }
}
