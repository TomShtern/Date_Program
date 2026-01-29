package datingapp.storage.jdbi;

import datingapp.core.Messaging.Message;
import datingapp.core.storage.MessageStorage;
import datingapp.storage.mapper.MessageMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for Message entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(MessageMapper.class)
public interface JdbiMessageStorage extends MessageStorage {

    @SqlUpdate("""
            INSERT INTO messages (id, conversation_id, sender_id, content, created_at)
            VALUES (:id, :conversationId, :senderId, :content, :createdAt)
            """)
    @Override
    void save(@BindBean Message message);

    @SqlQuery("""
            SELECT id, conversation_id, sender_id, content, created_at
            FROM messages
            WHERE conversation_id = :conversationId
            ORDER BY created_at ASC
            LIMIT :limit OFFSET :offset
            """)
    @Override
    List<Message> getMessages(
            @Bind("conversationId") String conversationId, @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("""
            SELECT id, conversation_id, sender_id, content, created_at
            FROM messages
            WHERE conversation_id = :conversationId
            ORDER BY created_at DESC
            LIMIT 1
            """)
    @Override
    Optional<Message> getLatestMessage(@Bind("conversationId") String conversationId);

    @SqlQuery("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId")
    @Override
    int countMessages(@Bind("conversationId") String conversationId);

    @SqlQuery("""
            SELECT COUNT(*) FROM messages
            WHERE conversation_id = :conversationId
            AND created_at > :after
            """)
    @Override
    int countMessagesAfter(@Bind("conversationId") String conversationId, @Bind("after") Instant after);

    @SqlQuery("""
            SELECT COUNT(*) FROM messages
            WHERE conversation_id = :conversationId
            AND sender_id != :senderId
            """)
    @Override
    int countMessagesNotFromSender(@Bind("conversationId") String conversationId, @Bind("senderId") UUID senderId);

    @SqlQuery("""
            SELECT COUNT(*) FROM messages
            WHERE conversation_id = :conversationId
            AND created_at > :after
            AND sender_id != :excludeSenderId
            """)
    @Override
    int countMessagesAfterNotFrom(
            @Bind("conversationId") String conversationId,
            @Bind("after") Instant after,
            @Bind("excludeSenderId") UUID excludeSenderId);

    @SqlUpdate("DELETE FROM messages WHERE conversation_id = :conversationId")
    @Override
    void deleteByConversation(@Bind("conversationId") String conversationId);
}
