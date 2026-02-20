package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionModels.Notification.Type;
import datingapp.core.model.User;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.UserStorage;
import datingapp.storage.DatabaseManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class JdbiCommunicationStorageSocialTest {

    private DatabaseManager dbManager;
    private Jdbi jdbi;
    private CommunicationStorage communicationStorage;
    private UserStorage userStorage;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        String dbName = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        dbManager = DatabaseManager.getInstance();

        jdbi = Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException e) {
                        throw new DatabaseManager.StorageException("Failed to get database connection", e);
                    }
                })
                .installPlugin(new SqlObjectPlugin());

        jdbi.registerArgument(new JdbiTypeCodecs.EnumSetSqlCodec.EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new JdbiTypeCodecs.EnumSetSqlCodec.InterestColumnMapper());
        jdbi.registerColumnMapper(Instant.class, (rs, col, ctx) -> {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toInstant() : null;
        });
        communicationStorage = new JdbiConnectionStorage(jdbi);
        userStorage = new JdbiUserStorage(jdbi);

        // Setup test users
        alice = new User(UUID.randomUUID(), "Alice");
        bob = new User(UUID.randomUUID(), "Bob");

        userStorage.save(alice);
        userStorage.save(bob);
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.shutdown();
            DatabaseManager.resetInstance();
        }
    }

    @Nested
    @DisplayName("FriendRequests")
    class FriendRequests {

        @Test
        @DisplayName("should save and retrieve friend requests")
        void shouldSaveAndRetrieveFriendRequests() {
            FriendRequest request = FriendRequest.create(alice.getId(), bob.getId());
            communicationStorage.saveFriendRequest(request);

            List<FriendRequest> requests = communicationStorage.getPendingFriendRequestsForUser(bob.getId());
            assertEquals(1, requests.size());

            FriendRequest retrieved = requests.get(0);
            assertEquals(alice.getId(), retrieved.fromUserId());
            assertEquals(bob.getId(), retrieved.toUserId());
            assertEquals(FriendRequest.Status.PENDING, retrieved.status());
        }

        @Test
        @DisplayName("should get friend request between users")
        void shouldGetFriendRequest() {
            FriendRequest request = FriendRequest.create(alice.getId(), bob.getId());
            communicationStorage.saveFriendRequest(request);

            assertTrue(communicationStorage
                    .getPendingFriendRequestBetween(alice.getId(), bob.getId())
                    .isPresent());
            assertTrue(communicationStorage
                    .getPendingFriendRequestBetween(bob.getId(), alice.getId())
                    .isPresent()); // Should be bidirectional for pending
        }
    }

    @Nested
    @DisplayName("Notifications")
    class Notifications {

        @Test
        @DisplayName("should save and retrieve notifications")
        void shouldSaveAndRetrieveNotifications() {
            Notification notification = Notification.create(alice.getId(), Type.MATCH_FOUND, "Title", "Message", null);

            communicationStorage.saveNotification(notification);

            List<Notification> notifications = communicationStorage.getNotificationsForUser(alice.getId(), false);
            assertEquals(1, notifications.size());

            Notification retrieved = notifications.get(0);
            assertEquals(notification.id(), retrieved.id());
            assertEquals(alice.getId(), retrieved.userId());
            assertEquals("Title", retrieved.title());
            assertEquals("Message", retrieved.message());
            assertFalse(retrieved.isRead());
        }

        @Test
        @DisplayName("should mark notification as read")
        void shouldMarkNotificationAsRead() {
            Notification notification = Notification.create(alice.getId(), Type.MATCH_FOUND, "Title", "Message", null);

            communicationStorage.saveNotification(notification);
            communicationStorage.markNotificationAsRead(notification.id());

            List<Notification> notifications = communicationStorage.getNotificationsForUser(alice.getId(), false);
            assertTrue(notifications.get(0).isRead());
        }
    }
}
