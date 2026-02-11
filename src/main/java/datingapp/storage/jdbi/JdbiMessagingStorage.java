package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.model.Match;
import datingapp.core.model.Messaging.Conversation;
import datingapp.core.model.Messaging.Message;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Consolidated JDBI storage implementation for messaging.
 * Combines operations from former JdbiConversationStorage and
 * JdbiMessageStorage.
 */
@RegisterRowMapper(JdbiMessagingStorage.ConversationMapper.class)
@RegisterRowMapper(JdbiMessagingStorage.MessageMapper.class)
public interface JdbiMessagingStorage {

    // ═══════════════════════════════════════════════════════════════
    // Conversation Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
            INSERT INTO conversations (id, user_a, user_b, created_at, last_message_at,
                                       user_a_last_read_at, user_b_last_read_at,
                                       archived_at, archive_reason, visible_to_user_a, visible_to_user_b)
            VALUES (:id, :userA, :userB, :createdAt, :lastMessageAt,
                    :userAReadAt, :userBReadAt, :archivedAt, :archiveReason,
                    :visibleToUserA, :visibleToUserB)
            """)
    void saveConversation(@BindBean Conversation conversation);

    @SqlQuery("""
            SELECT id, user_a, user_b, created_at, last_message_at,
                user_a_last_read_at, user_b_last_read_at,
                archived_at, archive_reason, visible_to_user_a, visible_to_user_b
            FROM conversations
            WHERE id = :conversationId
            """)
    Optional<Conversation> getConversation(@Bind("conversationId") String conversationId);

    default Optional<Conversation> getConversationByUsers(UUID userA, UUID userB) {
        String conversationId = Conversation.generateId(userA, userB);
        return getConversation(conversationId);
    }

    @SqlQuery("""
            SELECT id, user_a, user_b, created_at, last_message_at,
                user_a_last_read_at, user_b_last_read_at,
                archived_at, archive_reason, visible_to_user_a, visible_to_user_b
            FROM conversations
            WHERE user_a = :userId OR user_b = :userId
            ORDER BY COALESCE(last_message_at, created_at) DESC
            """)
    @RegisterRowMapper(ConversationMapper.class)
    List<Conversation> getConversationsFor(@Bind("userId") UUID userId);

    @SqlUpdate("UPDATE conversations SET last_message_at = :timestamp WHERE id = :conversationId")
    void updateConversationLastMessageAt(
            @Bind("conversationId") String conversationId, @Bind("timestamp") Instant timestamp);

    @SqlUpdate("""
            UPDATE conversations
            SET user_a_last_read_at = CASE WHEN user_a = :userId THEN :timestamp ELSE user_a_last_read_at END,
                user_b_last_read_at = CASE WHEN user_b = :userId THEN :timestamp ELSE user_b_last_read_at END
            WHERE id = :conversationId AND (user_a = :userId OR user_b = :userId)
            """)
    void updateConversationReadTimestamp(
            @Bind("conversationId") String conversationId,
            @Bind("userId") UUID userId,
            @Bind("timestamp") Instant timestamp);

    default void archiveConversation(String conversationId, Match.ArchiveReason reason) {
        archiveConversationInternal(conversationId, AppClock.now(), reason);
    }

    @SqlUpdate(
            "UPDATE conversations SET archived_at = :archivedAt, archive_reason = :reason WHERE id = :conversationId")
    void archiveConversationInternal(
            @Bind("conversationId") String conversationId,
            @Bind("archivedAt") Instant archivedAt,
            @Bind("reason") Match.ArchiveReason reason);

    @SqlUpdate("""
            UPDATE conversations
            SET visible_to_user_a = CASE WHEN user_a = :userId THEN :visible ELSE visible_to_user_a END,
                visible_to_user_b = CASE WHEN user_b = :userId THEN :visible ELSE visible_to_user_b END
            WHERE id = :conversationId
            """)
    void setConversationVisibility(
            @Bind("conversationId") String conversationId,
            @Bind("userId") UUID userId,
            @Bind("visible") boolean visible);

    @SqlUpdate("DELETE FROM conversations WHERE id = :conversationId")
    void deleteConversation(@Bind("conversationId") String conversationId);

    // ═══════════════════════════════════════════════════════════════
    // Message Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
            INSERT INTO messages (id, conversation_id, sender_id, content, created_at)
            VALUES (:id, :conversationId, :senderId, :content, :createdAt)
            """)
    void saveMessage(@BindBean Message message);

    @SqlQuery("""
            SELECT id, conversation_id, sender_id, content, created_at
            FROM messages
            WHERE conversation_id = :conversationId
            ORDER BY created_at ASC
            LIMIT :limit OFFSET :offset
            """)
    @RegisterRowMapper(MessageMapper.class)
    List<Message> getMessages(
            @Bind("conversationId") String conversationId, @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("""
            SELECT id, conversation_id, sender_id, content, created_at
            FROM messages
            WHERE conversation_id = :conversationId
            ORDER BY created_at DESC
            LIMIT 1
            """)
    @RegisterRowMapper(MessageMapper.class)
    Optional<Message> getLatestMessage(@Bind("conversationId") String conversationId);

    @SqlQuery("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId")
    int countMessages(@Bind("conversationId") String conversationId);

    @SqlQuery("""
            SELECT COUNT(*) FROM messages
            WHERE conversation_id = :conversationId
            AND created_at > :after
            """)
    int countMessagesAfter(@Bind("conversationId") String conversationId, @Bind("after") Instant after);

    @SqlQuery("""
            SELECT COUNT(*) FROM messages
            WHERE conversation_id = :conversationId
            AND sender_id != :senderId
            """)
    int countMessagesNotFromSender(@Bind("conversationId") String conversationId, @Bind("senderId") UUID senderId);

    @SqlQuery("""
            SELECT COUNT(*) FROM messages
            WHERE conversation_id = :conversationId
            AND created_at > :after
            AND sender_id != :excludeSenderId
            """)
    int countMessagesAfterNotFrom(
            @Bind("conversationId") String conversationId,
            @Bind("after") Instant after,
            @Bind("excludeSenderId") UUID excludeSenderId);

    @SqlUpdate("DELETE FROM messages WHERE conversation_id = :conversationId")
    void deleteMessagesByConversation(@Bind("conversationId") String conversationId);

    // ═══════════════════════════════════════════════════════════════
    // Row Mappers
    // ═══════════════════════════════════════════════════════════════

    /** Row mapper for Conversation records. */
    class ConversationMapper implements RowMapper<Conversation> {
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

    /** Row mapper for Message records. */
    class MessageMapper implements RowMapper<Message> {
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
}
