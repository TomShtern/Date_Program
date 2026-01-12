package datingapp.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.Notification;
import datingapp.core.NotificationStorage;
import datingapp.core.NotificationType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** H2 implementation of NotificationStorage. */
public class H2NotificationStorage implements NotificationStorage {

    private final DatabaseManager dbManager;
    private final ObjectMapper objectMapper;

    public H2NotificationStorage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.objectMapper = new ObjectMapper();
        ensureSchema();
    }

    private void ensureSchema() {
        String sql =
                """
                CREATE TABLE IF NOT EXISTS notifications (
                    id UUID PRIMARY KEY,
                    user_id UUID NOT NULL,
                    type VARCHAR(30) NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    message TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    is_read BOOLEAN DEFAULT FALSE,
                    data_json TEXT,
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )
                """;
        String index = "CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, is_read)";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt1 = conn.prepareStatement(sql);
                PreparedStatement stmt2 = conn.prepareStatement(index)) {
            stmt1.execute();
            stmt2.execute();
        } catch (SQLException e) {
            throw new StorageException("Failed to ensure notifications schema", e);
        }
    }

    @Override
    public void save(Notification notification) {
        String sql =
                """
                INSERT INTO notifications (id, user_id, type, title, message, created_at, is_read, data_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, notification.id());
            stmt.setObject(2, notification.userId());
            stmt.setString(3, notification.type().name());
            stmt.setString(4, notification.title());
            stmt.setString(5, notification.message());
            stmt.setTimestamp(6, Timestamp.from(notification.createdAt()));
            stmt.setBoolean(7, notification.isRead());
            stmt.setString(8, toJson(notification.data()));

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save notification: " + notification.id(), e);
        }
    }

    @Override
    public void markAsRead(UUID id) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to mark notification as read: " + id, e);
        }
    }

    @Override
    public List<Notification> getForUser(UUID userId, boolean unreadOnly) {
        String sql =
                "SELECT id, user_id, type, title, message, created_at, is_read, data_json FROM notifications WHERE user_id = ?";
        if (unreadOnly) {
            sql += " AND is_read = FALSE";
        }
        sql += " ORDER BY created_at DESC";

        List<Notification> notifications = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                notifications.add(mapRow(rs));
            }
            return notifications;

        } catch (SQLException e) {
            throw new StorageException("Failed to get notifications for user: " + userId, e);
        }
    }

    @Override
    public Optional<Notification> get(UUID id) {
        String sql =
                "SELECT id, user_id, type, title, message, created_at, is_read, data_json FROM notifications WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new StorageException("Failed to get notification: " + id, e);
        }
    }

    @Override
    public void delete(UUID id) {
        String sql = "DELETE FROM notifications WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to delete notification: " + id, e);
        }
    }

    @Override
    public void deleteOldNotifications(Instant before) {
        String sql = "DELETE FROM notifications WHERE created_at < ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(before));
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to delete old notifications", e);
        }
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        return new Notification(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                NotificationType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("message"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getBoolean("is_read"),
                fromJson(rs.getString("data_json")));
    }

    private String toJson(Map<String, String> data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to serialize notification data", e);
        }
    }

    private Map<String, String> fromJson(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to deserialize notification data", e);
        }
    }
}
