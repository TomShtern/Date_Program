package datingapp.storage.jdbi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.Social.FriendRequest;
import datingapp.core.Social.FriendRequest.Status;
import datingapp.core.Social.Notification;
import datingapp.core.Social.Notification.Type;
import datingapp.core.storage.SocialStorage;
import datingapp.storage.StorageException;
import datingapp.storage.mapper.MapperHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * Consolidated JDBI storage implementation for social features.
 * Combines operations from former JdbiFriendRequestStorage and
 * JdbiNotificationStorage.
 */
@RegisterRowMapper(JdbiSocialStorage.FriendRequestMapper.class)
@RegisterRowMapper(JdbiSocialStorage.NotificationMapper.class)
public interface JdbiSocialStorage extends SocialStorage {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ═══════════════════════════════════════════════════════════════
    // Friend Request Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
            INSERT INTO friend_requests (id, from_user_id, to_user_id, created_at, status, responded_at)
            VALUES (:id, :fromUserId, :toUserId, :createdAt, :status, :respondedAt)
            """)
    @Override
    void saveFriendRequest(@BindBean FriendRequest request);

    @SqlUpdate("""
            UPDATE friend_requests SET status = :status, responded_at = :respondedAt
            WHERE id = :id
            """)
    @Override
    void updateFriendRequest(@BindBean FriendRequest request);

    @SqlQuery("""
            SELECT id, from_user_id, to_user_id, created_at, status, responded_at
            FROM friend_requests WHERE id = :id
            """)
    @Override
    Optional<FriendRequest> getFriendRequest(@Bind("id") UUID id);

    @SqlQuery("""
            SELECT id, from_user_id, to_user_id, created_at, status, responded_at
            FROM friend_requests
            WHERE ((from_user_id = :user1 AND to_user_id = :user2) OR (from_user_id = :user2 AND to_user_id = :user1))
            AND status = 'PENDING'
            """)
    @Override
    Optional<FriendRequest> getPendingFriendRequestBetween(@Bind("user1") UUID user1, @Bind("user2") UUID user2);

    @SqlQuery("""
            SELECT id, from_user_id, to_user_id, created_at, status, responded_at
            FROM friend_requests
            WHERE to_user_id = :userId AND status = 'PENDING'
            """)
    @Override
    List<FriendRequest> getPendingFriendRequestsForUser(@Bind("userId") UUID userId);

    @SqlUpdate("DELETE FROM friend_requests WHERE id = :id")
    @Override
    void deleteFriendRequest(@Bind("id") UUID id);

    // ═══════════════════════════════════════════════════════════════
    // Notification Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
            INSERT INTO notifications (id, user_id, type, title, message, created_at, is_read, data_json)
            VALUES (:id, :userId, :type, :title, :message, :createdAt, :isRead, :dataJson)
            """)
    void saveNotificationInternal(
            @Bind("id") UUID id,
            @Bind("userId") UUID userId,
            @Bind("type") String type,
            @Bind("title") String title,
            @Bind("message") String message,
            @Bind("createdAt") Instant createdAt,
            @Bind("isRead") boolean isRead,
            @Bind("dataJson") String dataJson);

    @Override
    default void saveNotification(Notification notification) {
        String dataJson = toJson(notification.data());
        saveNotificationInternal(
                notification.id(),
                notification.userId(),
                notification.type().name(),
                notification.title(),
                notification.message(),
                notification.createdAt(),
                notification.isRead(),
                dataJson);
    }

    @SqlUpdate("UPDATE notifications SET is_read = TRUE WHERE id = :id")
    @Override
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

    @Override
    default List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
        return unreadOnly ? getUnreadNotificationsForUser(userId) : getAllNotificationsForUser(userId);
    }

    @SqlQuery("""
            SELECT id, user_id, type, title, message, created_at, is_read, data_json
            FROM notifications WHERE id = :id
            """)
    @Override
    Optional<Notification> getNotification(@Bind("id") UUID id);

    @SqlUpdate("DELETE FROM notifications WHERE id = :id")
    @Override
    void deleteNotification(@Bind("id") UUID id);

    @SqlUpdate("DELETE FROM notifications WHERE created_at < :before")
    @Override
    void deleteOldNotifications(@Bind("before") Instant before);

    // ═══════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════
    // Row Mappers
    // ═══════════════════════════════════════════════════════════════

    /** Row mapper for FriendRequest records. */
    class FriendRequestMapper implements RowMapper<FriendRequest> {
        @Override
        public FriendRequest map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = MapperHelper.readUuid(rs, "id");
            var fromUserId = MapperHelper.readUuid(rs, "from_user_id");
            var toUserId = MapperHelper.readUuid(rs, "to_user_id");
            var createdAt = MapperHelper.readInstant(rs, "created_at");
            var status = MapperHelper.readEnum(rs, "status", Status.class);
            var respondedAt = MapperHelper.readInstant(rs, "responded_at");

            return new FriendRequest(id, fromUserId, toUserId, createdAt, status, respondedAt);
        }
    }

    /** Row mapper for Notification records. */
    class NotificationMapper implements RowMapper<Notification> {
        private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

        @Override
        public Notification map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = MapperHelper.readUuid(rs, "id");
            var userId = MapperHelper.readUuid(rs, "user_id");
            var type = MapperHelper.readEnum(rs, "type", Type.class);
            var title = rs.getString("title");
            var message = rs.getString("message");
            var createdAt = MapperHelper.readInstant(rs, "created_at");
            var isRead = rs.getBoolean("is_read");
            var data = fromJson(rs.getString("data_json"));

            return new Notification(id, userId, type, title, message, createdAt, isRead, data);
        }

        private Map<String, String> fromJson(String json) {
            if (json == null) {
                return Map.of();
            }
            try {
                return OBJECT_MAPPER.readValue(json, MAP_TYPE);
            } catch (JsonProcessingException e) {
                throw new StorageException("Failed to deserialize notification data", e);
            }
        }
    }
}
