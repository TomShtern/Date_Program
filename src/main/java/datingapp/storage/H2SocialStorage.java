package datingapp.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.Social.FriendRequest;
import datingapp.core.Social.FriendRequestStorage;
import datingapp.core.Social.Notification;
import datingapp.core.Social.NotificationStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * H2 storage implementation for social features (friend requests, notifications).
 * Groups related CRUD operations for social interaction workflows.
 *
 * <p>This consolidated class provides access to both FriendRequestStorage and
 * NotificationStorage implementations via nested classes.
 */
public final class H2SocialStorage {

    private final FriendRequests friendRequests;
    private final Notifications notifications;

    public H2SocialStorage(DatabaseManager dbManager) {
        this.friendRequests = new FriendRequests(dbManager);
        this.notifications = new Notifications(dbManager);
    }

    /** Returns the FriendRequestStorage implementation. */
    public FriendRequestStorage friendRequests() {
        return friendRequests;
    }

    /** Returns the NotificationStorage implementation. */
    public NotificationStorage notifications() {
        return notifications;
    }

    // ========================================================================
    // FRIEND REQUESTS - H2 implementation of FriendRequestStorage
    // ========================================================================

    /** H2 implementation of FriendRequestStorage. */
    public static class FriendRequests extends AbstractH2Storage implements FriendRequestStorage {

        public FriendRequests(DatabaseManager dbManager) {
            super(dbManager);
            ensureSchema();
        }

        @Override
        protected void ensureSchema() {
            String tableSql = """
                    CREATE TABLE IF NOT EXISTS friend_requests (
                        id UUID PRIMARY KEY,
                        from_user_id UUID NOT NULL,
                        to_user_id UUID NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        responded_at TIMESTAMP,
                        FOREIGN KEY (from_user_id) REFERENCES users(id),
                        FOREIGN KEY (to_user_id) REFERENCES users(id)
                    )
                    """;
            // Regular index for lookup performance (H2 doesn't support partial indexes with
            // WHERE)
            // Uniqueness for pending requests is enforced in application logic via
            // getPendingBetween()
            String indexSql = """
                    CREATE INDEX IF NOT EXISTS idx_friend_req_users
                    ON friend_requests(from_user_id, to_user_id, status)
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt1 = conn.prepareStatement(tableSql);
                    PreparedStatement stmt2 = conn.prepareStatement(indexSql)) {
                stmt1.execute();
                stmt2.execute();
            } catch (SQLException e) {
                throw new StorageException("Failed to ensure friend_requests schema", e);
            }
        }

        @Override
        public void save(FriendRequest request) {
            String sql = """
                    INSERT INTO friend_requests (id, from_user_id, to_user_id, created_at, status, responded_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, request.id());
                stmt.setObject(2, request.fromUserId());
                stmt.setObject(3, request.toUserId());
                stmt.setTimestamp(4, Timestamp.from(request.createdAt()));
                stmt.setString(5, request.status().name());
                if (request.respondedAt() != null) {
                    stmt.setTimestamp(6, Timestamp.from(request.respondedAt()));
                } else {
                    stmt.setNull(6, Types.TIMESTAMP);
                }

                stmt.executeUpdate();

            } catch (SQLException e) {
                throw new StorageException("Failed to save friend request: " + request.id(), e);
            }
        }

        @Override
        public void update(FriendRequest request) {
            String sql = """
                    UPDATE friend_requests SET status = ?, responded_at = ?
                    WHERE id = ?
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, request.status().name());
                if (request.respondedAt() != null) {
                    stmt.setTimestamp(2, Timestamp.from(request.respondedAt()));
                } else {
                    stmt.setNull(2, Types.TIMESTAMP);
                }
                stmt.setObject(3, request.id());

                stmt.executeUpdate();

            } catch (SQLException e) {
                throw new StorageException("Failed to update friend request: " + request.id(), e);
            }
        }

        @Override
        public Optional<FriendRequest> get(UUID id) {
            String sql =
                    "SELECT id, from_user_id, to_user_id, created_at, status, responded_at FROM friend_requests WHERE id = ?";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, id);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();

            } catch (SQLException e) {
                throw new StorageException("Failed to get friend request: " + id, e);
            }
        }

        @Override
        public Optional<FriendRequest> getPendingBetween(UUID user1, UUID user2) {
            String sql = """
                SELECT id, from_user_id, to_user_id, created_at, status, responded_at FROM friend_requests
                WHERE ((from_user_id = ? AND to_user_id = ?) OR (from_user_id = ? AND to_user_id = ?))
                AND status = 'PENDING'
                """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, user1);
                stmt.setObject(2, user2);
                stmt.setObject(3, user2);
                stmt.setObject(4, user1);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();

            } catch (SQLException e) {
                throw new StorageException("Failed to get pending friend request", e);
            }
        }

        @Override
        public List<FriendRequest> getPendingForUser(UUID userId) {
            String sql = """
                SELECT id, from_user_id, to_user_id, created_at, status, responded_at
                FROM friend_requests
                WHERE to_user_id = ? AND status = 'PENDING'
                """;
            List<FriendRequest> requests = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, userId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    requests.add(mapRow(rs));
                }
                return requests;

            } catch (SQLException e) {
                throw new StorageException("Failed to get pending requests for user: " + userId, e);
            }
        }

        @Override
        public void delete(UUID id) {
            String sql = "DELETE FROM friend_requests WHERE id = ?";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, id);
                stmt.executeUpdate();

            } catch (SQLException e) {
                throw new StorageException("Failed to delete friend request: " + id, e);
            }
        }

        private FriendRequest mapRow(ResultSet rs) throws SQLException {
            Timestamp respondedAtTs = rs.getTimestamp("responded_at");
            return new FriendRequest(
                    rs.getObject("id", UUID.class),
                    rs.getObject("from_user_id", UUID.class),
                    rs.getObject("to_user_id", UUID.class),
                    rs.getTimestamp("created_at").toInstant(),
                    FriendRequest.Status.valueOf(rs.getString("status")),
                    respondedAtTs != null ? respondedAtTs.toInstant() : null);
        }
    }

    // ========================================================================
    // NOTIFICATIONS - H2 implementation of NotificationStorage
    // ========================================================================

    /** H2 implementation of NotificationStorage. */
    public static class Notifications extends AbstractH2Storage implements NotificationStorage {

        private final ObjectMapper objectMapper;

        public Notifications(DatabaseManager dbManager) {
            super(dbManager);
            this.objectMapper = new ObjectMapper();
            ensureSchema();
        }

        @Override
        protected void ensureSchema() {
            String sql = """
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
            String sql = """
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

            List<Notification> notificationList = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, userId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    notificationList.add(mapRow(rs));
                }
                return notificationList;

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
                    Notification.Type.valueOf(rs.getString("type")),
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
}
