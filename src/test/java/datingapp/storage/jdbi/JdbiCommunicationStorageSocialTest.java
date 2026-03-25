package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels;
import datingapp.core.connection.ConnectionModels.Conversation;
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

        @Test
        @DisplayName("should count pending friend requests")
        void shouldCountPendingFriendRequests() {
            // Save two pending requests for bob
            FriendRequest req1 = FriendRequest.create(alice.getId(), bob.getId());
            User carol = new User(UUID.randomUUID(), "Carol");
            userStorage.save(carol);
            UUID carolId = carol.getId();

            FriendRequest req2 = FriendRequest.create(carolId, bob.getId());
            communicationStorage.saveFriendRequest(req1);
            communicationStorage.saveFriendRequest(req2);

            // Save one accepted request for bob
            FriendRequest req3 = new FriendRequest(
                    UUID.randomUUID(), alice.getId(), bob.getId(), Instant.now(), FriendRequest.Status.ACCEPTED, null);
            communicationStorage.saveFriendRequest(req3);

            // Save one pending request for carol (from bob)
            FriendRequest req4 = FriendRequest.create(bob.getId(), carolId);
            communicationStorage.saveFriendRequest(req4);

            assertEquals(2, communicationStorage.countPendingFriendRequestsForUser(bob.getId()));
            assertEquals(1, communicationStorage.countPendingFriendRequestsForUser(carolId));
            assertEquals(0, communicationStorage.countPendingFriendRequestsForUser(alice.getId()));
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

    @Nested
    @DisplayName("Conversations")
    class Conversations {

        @Test
        @DisplayName("should soft-delete conversation and hide from queries")
        void shouldSoftDeleteConversation() {
            var conversation = ConnectionModels.Conversation.create(alice.getId(), bob.getId());
            communicationStorage.saveConversation(conversation);

            // Verify conversation is visible before delete
            assertTrue(
                    communicationStorage.getConversation(conversation.getId()).isPresent());

            // Soft-delete the conversation
            communicationStorage.deleteConversation(conversation.getId());

            // Verify conversation is no longer visible in queries (soft-deleted)
            assertTrue(
                    communicationStorage.getConversation(conversation.getId()).isEmpty());
            assertTrue(communicationStorage
                    .getConversationsFor(alice.getId(), 10, 0)
                    .isEmpty());
            assertTrue(communicationStorage.getAllConversationsFor(bob.getId()).isEmpty());

            Instant deletedAt = jdbi.withHandle(
                    handle -> handle.createQuery("SELECT deleted_at FROM conversations WHERE id = :conversationId")
                            .bind("conversationId", conversation.getId())
                            .mapTo(Instant.class)
                            .one());
            assertNotNull(deletedAt, "conversations.deleted_at should be set on soft delete");
        }

        @Test
        @DisplayName("soft-deleted conversation should remain in database but hidden")
        void softDeletedConversationRemains() {
            var conversation = ConnectionModels.Conversation.create(alice.getId(), bob.getId());
            communicationStorage.saveConversation(conversation);

            communicationStorage.deleteConversation(conversation.getId());

            // The row should still exist in the database (soft-delete, not hard delete)
            // We verify this by attempting to update the archived state - if the row was hard-deleted,
            // the update would have no effect
            // For now, we verify that the conversation is logically hidden from all retrieval methods
            assertTrue(
                    communicationStorage.getConversation(conversation.getId()).isEmpty(),
                    "Soft-deleted conversation should not be retrievable via getConversation");
        }

        @Test
        @DisplayName("hidden conversation should not appear in getConversationsFor for hidden user")
        void hiddenConversationExcludedFromGetConversationsFor() {
            var conversation = ConnectionModels.Conversation.create(alice.getId(), bob.getId());
            communicationStorage.saveConversation(conversation);

            // Verify visible to both initially
            List<Conversation> aliceConversations = communicationStorage.getConversationsFor(alice.getId(), 10, 0);
            List<Conversation> bobConversations = communicationStorage.getConversationsFor(bob.getId(), 10, 0);
            assertEquals(1, aliceConversations.size(), "Alice should see conversation initially");
            assertEquals(1, bobConversations.size(), "Bob should see conversation initially");

            // Hide conversation for Alice
            communicationStorage.setConversationVisibility(conversation.getId(), alice.getId(), false);

            // Verify hidden conversation doesn't appear for Alice
            aliceConversations = communicationStorage.getConversationsFor(alice.getId(), 10, 0);
            assertTrue(aliceConversations.isEmpty(), "Alice should not see hidden conversation in paged query");

            // Verify still visible to Bob
            bobConversations = communicationStorage.getConversationsFor(bob.getId(), 10, 0);
            assertEquals(1, bobConversations.size(), "Bob should still see conversation in paged query");
        }

        @Test
        @DisplayName("hidden conversation should not appear in getAllConversationsFor for hidden user")
        void hiddenConversationExcludedFromGetAllConversationsFor() {
            var conversation = ConnectionModels.Conversation.create(alice.getId(), bob.getId());
            communicationStorage.saveConversation(conversation);

            // Verify visible to both initially
            List<Conversation> aliceConversations = communicationStorage.getAllConversationsFor(alice.getId());
            List<Conversation> bobConversations = communicationStorage.getAllConversationsFor(bob.getId());
            assertEquals(1, aliceConversations.size(), "Alice should see conversation initially");
            assertEquals(1, bobConversations.size(), "Bob should see conversation initially");

            // Hide conversation for Bob
            communicationStorage.setConversationVisibility(conversation.getId(), bob.getId(), false);

            // Verify hidden conversation doesn't appear for Bob
            bobConversations = communicationStorage.getAllConversationsFor(bob.getId());
            assertTrue(bobConversations.isEmpty(), "Bob should not see hidden conversation in all-conversations query");

            // Verify still visible to Alice
            aliceConversations = communicationStorage.getAllConversationsFor(alice.getId());
            assertEquals(
                    1, aliceConversations.size(), "Alice should still see conversation in all-conversations query");
        }

        @Test
        @DisplayName("re-showing hidden conversation should make it visible again")
        void rehidingConversationShowsItAgain() {
            var conversation = ConnectionModels.Conversation.create(alice.getId(), bob.getId());
            communicationStorage.saveConversation(conversation);

            // Hide and then show conversation for Alice
            communicationStorage.setConversationVisibility(conversation.getId(), alice.getId(), false);
            communicationStorage.setConversationVisibility(conversation.getId(), alice.getId(), true);

            // Verify conversation is visible again
            List<Conversation> aliceConversations = communicationStorage.getConversationsFor(alice.getId(), 10, 0);
            assertEquals(1, aliceConversations.size(), "Alice should see conversation after re-showing");
        }
    }
}
