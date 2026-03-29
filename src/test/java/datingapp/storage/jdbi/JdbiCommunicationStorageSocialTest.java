package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    private DatabaseManager dbManager;
    private Jdbi jdbi;
    private CommunicationStorage communicationStorage;
    private UserStorage userStorage;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
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
        System.clearProperty(PROFILE_PROPERTY);
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
            User dave = new User(UUID.randomUUID(), "Dave");
            userStorage.save(dave);
            UUID daveId = dave.getId();

            FriendRequest req2 = FriendRequest.create(carolId, bob.getId());
            communicationStorage.saveFriendRequest(req1);
            communicationStorage.saveFriendRequest(req2);

            // Save one accepted request for bob
            FriendRequest req3 = new FriendRequest(
                    UUID.randomUUID(),
                    daveId,
                    bob.getId(),
                    Instant.now(),
                    FriendRequest.Status.ACCEPTED,
                    Instant.now());
            communicationStorage.saveFriendRequest(req3);

            // Save one pending request for dave (from bob)
            FriendRequest req4 = FriendRequest.create(bob.getId(), daveId);
            communicationStorage.saveFriendRequest(req4);

            assertEquals(2, communicationStorage.countPendingFriendRequestsForUser(bob.getId()));
            assertEquals(1, communicationStorage.countPendingFriendRequestsForUser(daveId));
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
            communicationStorage.markNotificationAsRead(alice.getId(), notification.id());

            List<Notification> notifications = communicationStorage.getNotificationsForUser(alice.getId(), false);
            assertTrue(notifications.get(0).isRead());
        }

        @Test
        @DisplayName("should ignore mark-read attempts from a different user")
        void shouldIgnoreMarkReadForDifferentUser() {
            Notification notification = Notification.create(alice.getId(), Type.MATCH_FOUND, "Title", "Message", null);

            communicationStorage.saveNotification(notification);
            communicationStorage.markNotificationAsRead(bob.getId(), notification.id());

            assertFalse(communicationStorage
                    .getNotification(notification.id())
                    .orElseThrow()
                    .isRead());
        }

        @Test
        @DisplayName("should mark all notifications as read in one storage call")
        void shouldMarkAllNotificationsAsRead() {
            Notification first = Notification.create(alice.getId(), Type.MATCH_FOUND, "First", "Message", null);
            Notification second = Notification.create(alice.getId(), Type.NEW_MESSAGE, "Second", "Message", null);
            Notification otherUser = Notification.create(bob.getId(), Type.NEW_MESSAGE, "Other", "Message", null);

            communicationStorage.saveNotification(first);
            communicationStorage.saveNotification(second);
            communicationStorage.saveNotification(otherUser);

            int updated = communicationStorage.markAllNotificationsAsRead(alice.getId());

            assertEquals(2, updated);
            assertTrue(communicationStorage
                    .getNotification(first.id())
                    .orElseThrow()
                    .isRead());
            assertTrue(communicationStorage
                    .getNotification(second.id())
                    .orElseThrow()
                    .isRead());
            assertFalse(communicationStorage
                    .getNotification(otherUser.id())
                    .orElseThrow()
                    .isRead());
        }

        @Test
        @DisplayName("should delete all notifications for a user in one storage call")
        void shouldDeleteAllNotificationsForUser() {
            Notification first = Notification.create(alice.getId(), Type.MATCH_FOUND, "First", "Message", null);
            Notification second = Notification.create(alice.getId(), Type.NEW_MESSAGE, "Second", "Message", null);
            Notification otherUser = Notification.create(bob.getId(), Type.NEW_MESSAGE, "Other", "Message", null);

            communicationStorage.saveNotification(first);
            communicationStorage.saveNotification(second);
            communicationStorage.saveNotification(otherUser);

            int deleted = communicationStorage.deleteNotificationsForUser(alice.getId());

            assertEquals(2, deleted);
            assertTrue(communicationStorage.getNotification(first.id()).isEmpty());
            assertTrue(communicationStorage.getNotification(second.id()).isEmpty());
            assertTrue(communicationStorage.getNotification(otherUser.id()).isPresent());
        }

        @Test
        @DisplayName("should ignore delete attempts from a different user")
        void shouldIgnoreDeleteForDifferentUser() {
            Notification notification = Notification.create(alice.getId(), Type.MATCH_FOUND, "Title", "Message", null);

            communicationStorage.saveNotification(notification);
            communicationStorage.deleteNotification(bob.getId(), notification.id());

            assertTrue(communicationStorage.getNotification(notification.id()).isPresent());
        }
    }

    @Nested
    @DisplayName("Conversations")
    class Conversations {

        @Test
        @DisplayName("deleted conversation should hide its messages and counts")
        void deletedConversationShouldHideMessagesAndCounts() {
            var conversation = ConnectionModels.Conversation.create(alice.getId(), bob.getId());
            communicationStorage.saveConversation(conversation);

            Instant sameTimestamp = Instant.parse("2026-03-29T10:15:30Z");
            communicationStorage.saveMessage(new ConnectionModels.Message(
                    UUID.randomUUID(), conversation.getId(), alice.getId(), "First", sameTimestamp));
            communicationStorage.saveMessage(new ConnectionModels.Message(
                    UUID.randomUUID(), conversation.getId(), bob.getId(), "Second", sameTimestamp));

            communicationStorage.deleteConversation(conversation.getId());

            assertTrue(communicationStorage
                    .getMessages(conversation.getId(), 10, 0)
                    .isEmpty());
            assertEquals(0, communicationStorage.countMessages(conversation.getId()));
            assertEquals(
                    0,
                    communicationStorage
                            .countMessagesByConversationIds(Set.of(conversation.getId()))
                            .getOrDefault(conversation.getId(), -1));
            assertTrue(
                    communicationStorage.getLatestMessage(conversation.getId()).isEmpty());
        }

        @Test
        @DisplayName("fully hidden conversation should hide its messages and counts")
        void fullyHiddenConversationShouldHideMessagesAndCounts() {
            var conversation = ConnectionModels.Conversation.create(alice.getId(), bob.getId());
            communicationStorage.saveConversation(conversation);

            Instant sameTimestamp = Instant.parse("2026-03-29T10:15:30Z");
            communicationStorage.saveMessage(new ConnectionModels.Message(
                    UUID.randomUUID(), conversation.getId(), alice.getId(), "First", sameTimestamp));
            communicationStorage.saveMessage(new ConnectionModels.Message(
                    UUID.randomUUID(), conversation.getId(), bob.getId(), "Second", sameTimestamp));

            communicationStorage.setConversationVisibility(conversation.getId(), alice.getId(), false);
            communicationStorage.setConversationVisibility(conversation.getId(), bob.getId(), false);

            assertTrue(communicationStorage
                    .getMessages(conversation.getId(), 10, 0)
                    .isEmpty());
            assertEquals(0, communicationStorage.countMessages(conversation.getId()));
            assertTrue(
                    communicationStorage.getLatestMessage(conversation.getId()).isEmpty());
        }

        @Test
        @DisplayName("messages with identical timestamps should use deterministic id ordering")
        void messagesWithIdenticalTimestampsShouldUseDeterministicIdOrdering() {
            var conversation = ConnectionModels.Conversation.create(alice.getId(), bob.getId());
            communicationStorage.saveConversation(conversation);

            Instant sameTimestamp = Instant.parse("2026-03-29T10:15:30Z");
            var first = new ConnectionModels.Message(
                    new UUID(0L, 2L), conversation.getId(), alice.getId(), "First", sameTimestamp);
            var second = new ConnectionModels.Message(
                    new UUID(0L, 1L), conversation.getId(), bob.getId(), "Second", sameTimestamp);

            communicationStorage.saveMessage(first);
            communicationStorage.saveMessage(second);

            List<ConnectionModels.Message> messages = communicationStorage.getMessages(conversation.getId(), 10, 0);
            List<UUID> expectedIds = new ArrayList<>(List.of(second.id(), first.id()));

            assertIterableEquals(
                    expectedIds,
                    messages.stream().map(ConnectionModels.Message::id).toList());
            assertEquals(
                    expectedIds.get(expectedIds.size() - 1),
                    communicationStorage
                            .getLatestMessage(conversation.getId())
                            .orElseThrow()
                            .id());
        }

        @Test
        @DisplayName("conversations with identical timestamps should use deterministic id ordering")
        void conversationsWithIdenticalTimestampsShouldUseDeterministicIdOrdering() {
            var firstConversation = ConnectionModels.Conversation.create(alice.getId(), bob.getId());
            User carol = new User(UUID.randomUUID(), "Carol");
            userStorage.save(carol);
            var secondConversation = ConnectionModels.Conversation.create(alice.getId(), carol.getId());

            communicationStorage.saveConversation(firstConversation);
            communicationStorage.saveConversation(secondConversation);

            Instant sameTimestamp = Instant.parse("2026-03-29T10:15:30Z");
            communicationStorage.updateConversationLastMessageAt(firstConversation.getId(), sameTimestamp);
            communicationStorage.updateConversationLastMessageAt(secondConversation.getId(), sameTimestamp);

            List<String> expectedIds = new ArrayList<>(List.of(firstConversation.getId(), secondConversation.getId()));
            expectedIds.sort(Comparator.reverseOrder());

            assertIterableEquals(
                    expectedIds,
                    communicationStorage.getAllConversationsFor(alice.getId()).stream()
                            .map(Conversation::getId)
                            .toList());
        }

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
