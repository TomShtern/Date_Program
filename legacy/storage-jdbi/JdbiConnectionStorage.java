package datingapp.storage.jdbi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppClock;
import datingapp.core.model.ConnectionModels.Conversation;
import datingapp.core.model.ConnectionModels.FriendRequest;
import datingapp.core.model.ConnectionModels.FriendRequest.Status;
import datingapp.core.model.ConnectionModels.Message;
import datingapp.core.model.ConnectionModels.Notification;
import datingapp.core.model.ConnectionModels.Notification.Type;
import datingapp.core.model.Match;
import datingapp.core.storage.CommunicationStorage;
import datingapp.storage.DatabaseManager.StorageException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/** Consolidated JDBI storage for conversations, messages, friend requests, and notifications. */
public final class JdbiConnectionStorage implements CommunicationStorage {

    private final MessagingDao messagingDao;
    private final SocialDao socialDao;

    public JdbiConnectionStorage(Jdbi jdbi) {
        Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.messagingDao = jdbi.onDemand(MessagingDao.class);
        this.socialDao = jdbi.onDemand(SocialDao.class);
    }

    @Override
    public void saveConversation(Conversation conversation) {
        messagingDao.saveConversation(conversation);
    }

    @Override
    public Optional<Conversation> getConversation(String conversationId) {
        return messagingDao.getConversation(conversationId);
    }

    @Override
    public Optional<Conversation> getConversationByUsers(UUID userA, UUID userB) {
        return messagingDao.getConversationByUsers(userA, userB);
    }

    @Override
    public List<Conversation> getConversationsFor(UUID userId) {
        return messagingDao.getConversationsFor(userId);
    }

    @Override
    public void updateConversationLastMessageAt(String conversationId, Instant timestamp) {
        messagingDao.updateConversationLastMessageAt(conversationId, timestamp);
    }

    @Override
    public void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp) {
        messagingDao.updateConversationReadTimestamp(conversationId, userId, timestamp);
    }

    @Override
    public void archiveConversation(String conversationId, Match.ArchiveReason reason) {
        messagingDao.archiveConversation(conversationId, reason);
    }

    @Override
    public void setConversationVisibility(String conversationId, UUID userId, boolean visible) {
        messagingDao.setConversationVisibility(conversationId, userId, visible);
    }

    @Override
    public void deleteConversation(String conversationId) {
        messagingDao.deleteConversation(conversationId);
    }

    @Override
    public void saveMessage(Message message) {
        messagingDao.saveMessage(message);
    }

    @Override
    public List<Message> getMessages(String conversationId, int limit, int offset) {
        return messagingDao.getMessages(conversationId, limit, offset);
    }

    @Override
    public Optional<Message> getLatestMessage(String conversationId) {
        return messagingDao.getLatestMessage(conversationId);
    }

    @Override
    public int countMessages(String conversationId) {
        return messagingDao.countMessages(conversationId);
    }

    @Override
    public int countMessagesAfter(String conversationId, Instant after) {
        return messagingDao.countMessagesAfter(conversationId, after);
    }

    @Override
    public int countMessagesNotFromSender(String conversationId, UUID senderId) {
        return messagingDao.countMessagesNotFromSender(conversationId, senderId);
    }

    @Override
    public int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId) {
        return messagingDao.countMessagesAfterNotFrom(conversationId, after, excludeSenderId);
    }

    @Override
    public void deleteMessagesByConversation(String conversationId) {
        messagingDao.deleteMessagesByConversation(conversationId);
    }

    @Override
    public void saveFriendRequest(FriendRequest request) {
        socialDao.saveFriendRequest(request);
    }

    @Override
    public void updateFriendRequest(FriendRequest request) {
        socialDao.updateFriendRequest(request);
    }

    @Override
    public Optional<FriendRequest> getFriendRequest(UUID id) {
        return socialDao.getFriendRequest(id);
    }

    @Override
    public Optional<FriendRequest> getPendingFriendRequestBetween(UUID user1, UUID user2) {
        return socialDao.getPendingFriendRequestBetween(user1, user2);
    }

    @Override
    public List<FriendRequest> getPendingFriendRequestsForUser(UUID userId) {
        return socialDao.getPendingFriendRequestsForUser(userId);
    }

    @Override
    public void deleteFriendRequest(UUID id) {
        socialDao.deleteFriendRequest(id);
    }

    @Override
    public void saveNotification(Notification notification) {
        socialDao.saveNotification(notification);
    }

    @Override
    public void markNotificationAsRead(UUID id) {
        socialDao.markNotificationAsRead(id);
    }

    @Override
    public List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
        return socialDao.getNotificationsForUser(userId, unreadOnly);
    }

    @Override
    public Optional<Notification> getNotification(UUID id) {
        return socialDao.getNotification(id);
    }

    @Override
    public void deleteNotification(UUID id) {
        socialDao.deleteNotification(id);
    }

    @Override
    public void deleteOldNotifications(Instant before) {
        socialDao.deleteOldNotifications(before);
    }

    @RegisterRowMapper(ConversationMapper.class)
    @RegisterRowMapper(MessageMapper.class)
    private interface MessagingDao {

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
        List<Message> getMessages(
                @Bind("conversationId") String conversationId, @Bind("limit") int limit, @Bind("offset") int offset);

        @SqlQuery("""
            SELECT id, conversation_id, sender_id, content, created_at
            FROM messages
            WHERE conversation_id = :conversationId
            ORDER BY created_at DESC
            LIMIT 1
            """)
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
    }

    @RegisterRowMapper(FriendRequestMapper.class)
    @RegisterRowMapper(NotificationMapper.class)
    private interface SocialDao {

        ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        @SqlUpdate("""
            INSERT INTO friend_requests (id, from_user_id, to_user_id, created_at, status, responded_at)
            VALUES (:id, :fromUserId, :toUserId, :createdAt, :status, :respondedAt)
            """)
        void saveFriendRequest(@BindBean FriendRequest request);

        @SqlUpdate("""
            UPDATE friend_requests SET status = :status, responded_at = :respondedAt
            WHERE id = :id
            """)
        void updateFriendRequest(@BindBean FriendRequest request);

        @SqlQuery("""
            SELECT id, from_user_id, to_user_id, created_at, status, responded_at
            FROM friend_requests WHERE id = :id
            """)
        Optional<FriendRequest> getFriendRequest(@Bind("id") UUID id);

        @SqlQuery("""
            SELECT id, from_user_id, to_user_id, created_at, status, responded_at
            FROM friend_requests
            WHERE ((from_user_id = :user1 AND to_user_id = :user2) OR (from_user_id = :user2 AND to_user_id = :user1))
            AND status = 'PENDING'
            """)
        Optional<FriendRequest> getPendingFriendRequestBetween(@Bind("user1") UUID user1, @Bind("user2") UUID user2);

        @SqlQuery("""
            SELECT id, from_user_id, to_user_id, created_at, status, responded_at
            FROM friend_requests
            WHERE to_user_id = :userId AND status = 'PENDING'
            """)
        List<FriendRequest> getPendingFriendRequestsForUser(@Bind("userId") UUID userId);

        @SqlUpdate("DELETE FROM friend_requests WHERE id = :id")
        void deleteFriendRequest(@Bind("id") UUID id);

        @SqlUpdate("""
            INSERT INTO notifications (id, user_id, type, title, message, created_at, is_read, data_json)
            VALUES (:id, :userId, :type, :title, :message, :createdAt, :isRead, :dataJson)
            """)
        void saveNotificationInternal(@BindBean Notification notification, @Bind("dataJson") String dataJson);

        default void saveNotification(Notification notification) {
            String dataJson = toJson(notification.data());
            saveNotificationInternal(notification, dataJson);
        }

        @SqlUpdate("UPDATE notifications SET is_read = TRUE WHERE id = :id")
        void markNotificationAsRead(@Bind("id") UUID id);

        @SqlQuery("""
            SELECT id, user_id, type, title, message, created_at, is_read, data_json
            FROM notifications WHERE user_id = :userId
            ORDER BY created_at DESC
            """)
        List<Notification> getAllNotificationsForUser(@Bind("userId") UUID userId);

        @SqlQuery("""
            SELECT id, user_id, type, title, message, created_at, is_read, data_json
            FROM notifications WHERE user_id = :userId AND is_read = FALSE
            ORDER BY created_at DESC
            """)
        List<Notification> getUnreadNotificationsForUser(@Bind("userId") UUID userId);

        default List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
            return unreadOnly ? getUnreadNotificationsForUser(userId) : getAllNotificationsForUser(userId);
        }

        @SqlQuery("""
            SELECT id, user_id, type, title, message, created_at, is_read, data_json
            FROM notifications WHERE id = :id
            """)
        Optional<Notification> getNotification(@Bind("id") UUID id);

        @SqlUpdate("DELETE FROM notifications WHERE id = :id")
        void deleteNotification(@Bind("id") UUID id);

        @SqlUpdate("DELETE FROM notifications WHERE created_at < :before")
        void deleteOldNotifications(@Bind("before") Instant before);

        static String toJson(Map<String, String> data) {
            if (data == null) {
                return null;
            }
            try {
                return OBJECT_MAPPER.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                throw new StorageException("Failed to serialize notification data", e);
            }
        }
    }

    public static class ConversationMapper implements RowMapper<Conversation> {
        @Override
        public Conversation map(ResultSet rs, StatementContext ctx) throws SQLException {
            String id = rs.getString("id");
            UUID userA = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_a");
            UUID userB = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_b");
            Instant createdAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at");
            Instant lastMessageAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "last_message_at");
            Instant userAReadAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "user_a_last_read_at");
            Instant userBReadAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "user_b_last_read_at");
            Instant archivedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "archived_at");
            Match.ArchiveReason archiveReason =
                    JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "archive_reason", Match.ArchiveReason.class);
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

    public static class MessageMapper implements RowMapper<Message> {
        @Override
        public Message map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id");
            var conversationId = rs.getString("conversation_id");
            var senderId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "sender_id");
            var content = rs.getString("content");
            var createdAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at");
            return new Message(id, conversationId, senderId, content, createdAt);
        }
    }

    public static class FriendRequestMapper implements RowMapper<FriendRequest> {
        @Override
        public FriendRequest map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id");
            var fromUserId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "from_user_id");
            var toUserId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "to_user_id");
            var createdAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at");
            var status = JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "status", Status.class);
            var respondedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "responded_at");

            return new FriendRequest(id, fromUserId, toUserId, createdAt, status, respondedAt);
        }
    }

    public static class NotificationMapper implements RowMapper<Notification> {
        private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

        @Override
        public Notification map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id");
            var userId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_id");
            var type = JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "type", Type.class);
            var title = rs.getString("title");
            var message = rs.getString("message");
            var createdAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at");
            var isRead = rs.getBoolean("is_read");
            var data = fromJson(rs.getString("data_json"));

            return new Notification(id, userId, type, title, message, createdAt, isRead, data);
        }

        private Map<String, String> fromJson(String json) {
            if (json == null) {
                return Map.of();
            }
            try {
                return SocialDao.OBJECT_MAPPER.readValue(json, MAP_TYPE);
            } catch (JsonProcessingException e) {
                throw new StorageException("Failed to deserialize notification data", e);
            }
        }
    }
}
