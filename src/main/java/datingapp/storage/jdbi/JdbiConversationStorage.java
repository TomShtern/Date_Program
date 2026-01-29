package datingapp.storage.jdbi;

import datingapp.core.Match;
import datingapp.core.Messaging.Conversation;
import datingapp.core.storage.ConversationStorage;
import datingapp.storage.mapper.ConversationMapper;
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
 * JDBI storage implementation for Conversation entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(ConversationMapper.class)
public interface JdbiConversationStorage extends ConversationStorage {

    @SqlUpdate("""
            INSERT INTO conversations (id, user_a, user_b, created_at, last_message_at,
                                       user_a_last_read_at, user_b_last_read_at,
                                       archived_at, archive_reason, visible_to_user_a, visible_to_user_b)
            VALUES (:id, :userA, :userB, :createdAt, :lastMessageAt,
                    :userAReadAt, :userBReadAt, :archivedAt, :archiveReason,
                    :visibleToUserA, :visibleToUserB)
            """)
    @Override
    void save(@BindBean Conversation conversation);

    @SqlQuery("""
            SELECT id, user_a, user_b, created_at, last_message_at,
                user_a_last_read_at, user_b_last_read_at,
                archived_at, archive_reason, visible_to_user_a, visible_to_user_b
            FROM conversations
            WHERE id = :conversationId
            """)
    @Override
    Optional<Conversation> get(@Bind("conversationId") String conversationId);

    @Override
    default Optional<Conversation> getByUsers(UUID userA, UUID userB) {
        String conversationId = Conversation.generateId(userA, userB);
        return get(conversationId);
    }

    @SqlQuery("""
            SELECT id, user_a, user_b, created_at, last_message_at,
                user_a_last_read_at, user_b_last_read_at,
                archived_at, archive_reason, visible_to_user_a, visible_to_user_b
            FROM conversations
            WHERE user_a = :userId OR user_b = :userId
            ORDER BY COALESCE(last_message_at, created_at) DESC
            """)
    @Override
    List<Conversation> getConversationsFor(@Bind("userId") UUID userId);

    @SqlUpdate("UPDATE conversations SET last_message_at = :timestamp WHERE id = :conversationId")
    @Override
    void updateLastMessageAt(@Bind("conversationId") String conversationId, @Bind("timestamp") Instant timestamp);

    @SqlUpdate("""
            UPDATE conversations
            SET user_a_last_read_at = CASE WHEN user_a = :userId THEN :timestamp ELSE user_a_last_read_at END,
                user_b_last_read_at = CASE WHEN user_b = :userId THEN :timestamp ELSE user_b_last_read_at END
            WHERE id = :conversationId AND (user_a = :userId OR user_b = :userId)
            """)
    @Override
    void updateReadTimestamp(
            @Bind("conversationId") String conversationId,
            @Bind("userId") UUID userId,
            @Bind("timestamp") Instant timestamp);

    @SqlUpdate(
            "UPDATE conversations SET archived_at = :archivedAt, archive_reason = :reason WHERE id = :conversationId")
    @Override
    default void archive(@Bind("conversationId") String conversationId, Match.ArchiveReason reason) {
        archiveInternal(conversationId, Instant.now(), reason);
    }

    @SqlUpdate(
            "UPDATE conversations SET archived_at = :archivedAt, archive_reason = :reason WHERE id = :conversationId")
    void archiveInternal(
            @Bind("conversationId") String conversationId,
            @Bind("archivedAt") Instant archivedAt,
            @Bind("reason") Match.ArchiveReason reason);

    @SqlUpdate("""
            UPDATE conversations
            SET visible_to_user_a = CASE WHEN user_a = :userId THEN :visible ELSE visible_to_user_a END,
                visible_to_user_b = CASE WHEN user_b = :userId THEN :visible ELSE visible_to_user_b END
            WHERE id = :conversationId
            """)
    @Override
    void setVisibility(
            @Bind("conversationId") String conversationId,
            @Bind("userId") UUID userId,
            @Bind("visible") boolean visible);

    @SqlUpdate("DELETE FROM conversations WHERE id = :conversationId")
    @Override
    void delete(@Bind("conversationId") String conversationId);
}
