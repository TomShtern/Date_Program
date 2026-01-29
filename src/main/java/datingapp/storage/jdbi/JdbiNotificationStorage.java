package datingapp.storage.jdbi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.Social.Notification;
import datingapp.core.storage.NotificationStorage;
import datingapp.storage.StorageException;
import datingapp.storage.mapper.NotificationMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for Notification entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(NotificationMapper.class)
public interface JdbiNotificationStorage extends NotificationStorage {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @SqlUpdate("""
            INSERT INTO notifications (id, user_id, type, title, message, created_at, is_read, data_json)
            VALUES (:id, :userId, :type, :title, :message, :createdAt, :isRead, :dataJson)
            """)
    void saveInternal(
            @Bind("id") UUID id,
            @Bind("userId") UUID userId,
            @Bind("type") String type,
            @Bind("title") String title,
            @Bind("message") String message,
            @Bind("createdAt") Instant createdAt,
            @Bind("isRead") boolean isRead,
            @Bind("dataJson") String dataJson);

    @Override
    default void save(Notification notification) {
        String dataJson = toJson(notification.data());
        saveInternal(
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
    void markAsRead(@Bind("id") UUID id);

    @SqlQuery("""
            SELECT id, user_id, type, title, message, created_at, is_read, data_json
            FROM notifications WHERE user_id = :userId
            ORDER BY created_at DESC
            """)
    List<Notification> getAllForUser(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT id, user_id, type, title, message, created_at, is_read, data_json
            FROM notifications WHERE user_id = :userId AND is_read = FALSE
            ORDER BY created_at DESC
            """)
    List<Notification> getUnreadForUser(@Bind("userId") UUID userId);

    @Override
    default List<Notification> getForUser(UUID userId, boolean unreadOnly) {
        return unreadOnly ? getUnreadForUser(userId) : getAllForUser(userId);
    }

    @SqlQuery("""
            SELECT id, user_id, type, title, message, created_at, is_read, data_json
            FROM notifications WHERE id = :id
            """)
    @Override
    Optional<Notification> get(@Bind("id") UUID id);

    @SqlUpdate("DELETE FROM notifications WHERE id = :id")
    @Override
    void delete(@Bind("id") UUID id);

    @SqlUpdate("DELETE FROM notifications WHERE created_at < :before")
    @Override
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
