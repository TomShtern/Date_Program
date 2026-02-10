package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.*;
import datingapp.core.Social.FriendRequest;
import datingapp.core.Social.Notification;
import datingapp.core.User.Gender;
import datingapp.core.storage.MessagingStorage;
import datingapp.core.storage.SocialStorage;
import datingapp.core.testutil.TestStorages;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Unit tests for RelationshipHandler CLI commands: viewPendingRequests(), viewNotifications().
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class RelationshipHandlerTest {

    private TestStorages.Users userStorage;
    private TestStorages.Matches matchStorage;
    private InMemorySocialStorage socialStorage;
    private InMemoryMessagingStorage messagingStorage;
    private AppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        matchStorage = new TestStorages.Matches();
        socialStorage = new InMemorySocialStorage();
        messagingStorage = new InMemoryMessagingStorage();

        session = AppSession.getInstance();
        session.reset();

        testUser = createActiveUser("TestUser");
        userStorage.save(testUser);
        session.setCurrentUser(testUser);
    }

    private RelationshipHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        RelationshipTransitionService transitionService =
                new RelationshipTransitionService(matchStorage, socialStorage, messagingStorage);
        return new RelationshipHandler(transitionService, socialStorage, userStorage, session, inputReader);
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("View Pending Requests")
    class ViewPendingRequests {

        @Test
        @DisplayName("Shows message when not logged in")
        void showsMessageWhenNotLoggedIn() {
            session.logout();
            RelationshipHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }

        @Test
        @DisplayName("Shows message when no pending requests")
        void showsMessageWhenNoPendingRequests() {
            RelationshipHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }

        @Test
        @DisplayName("Lists pending friend requests")
        void listsPendingFriendRequests() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            // Create friend request TO testUser
            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            socialStorage.saveFriendRequest(request);

            RelationshipHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }

        @Test
        @DisplayName("Accepts friend request")
        void acceptsFriendRequest() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            // Create match first (required for friend zone)
            Match match = Match.create(testUser.getId(), otherUser.getId());
            matchStorage.save(match);

            // Create friend request
            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            socialStorage.saveFriendRequest(request);

            // Select request 1, accept
            RelationshipHandler handler = createHandler("1\na\n");
            handler.viewPendingRequests();

            // Request should be accepted
            Optional<FriendRequest> updated = socialStorage.getFriendRequest(request.id());
            assertTrue(updated.isPresent());
            assertEquals(FriendRequest.Status.ACCEPTED, updated.get().status());
        }

        @Test
        @DisplayName("Declines friend request")
        void declinesFriendRequest() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            matchStorage.save(match);

            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            socialStorage.saveFriendRequest(request);

            // Select request 1, decline
            RelationshipHandler handler = createHandler("1\nd\n");
            handler.viewPendingRequests();

            Optional<FriendRequest> updated = socialStorage.getFriendRequest(request.id());
            assertTrue(updated.isPresent());
            assertEquals(FriendRequest.Status.DECLINED, updated.get().status());
        }

        @Test
        @DisplayName("Handles back selection")
        void handlesBackSelection() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            socialStorage.saveFriendRequest(request);

            RelationshipHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }

        @Test
        @DisplayName("Handles invalid selection")
        void handlesInvalidSelection() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            matchStorage.save(match);

            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            socialStorage.saveFriendRequest(request);

            // Invalid selection
            RelationshipHandler handler = createHandler("99\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("View Notifications")
    class ViewNotifications {

        @Test
        @DisplayName("Shows message when not logged in")
        void showsMessageWhenNotLoggedIn() {
            session.logout();
            RelationshipHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::viewNotifications);
        }

        @Test
        @DisplayName("Shows message when no notifications")
        void showsMessageWhenNoNotifications() {
            RelationshipHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::viewNotifications);
        }

        @Test
        @DisplayName("Lists notifications")
        void listsNotifications() {
            Notification notification = Notification.create(
                    testUser.getId(), Notification.Type.MATCH_FOUND, "New Match!", "You have a new match!", Map.of());
            socialStorage.saveNotification(notification);

            RelationshipHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::viewNotifications);
        }

        @Test
        @DisplayName("Marks notifications as read")
        void marksNotificationsAsRead() {
            Notification notification = Notification.create(
                    testUser.getId(),
                    Notification.Type.NEW_MESSAGE,
                    "New Message",
                    "You received a new message!",
                    Map.of());
            socialStorage.saveNotification(notification);

            assertFalse(notification.isRead());

            RelationshipHandler handler = createHandler("\n");
            handler.viewNotifications();

            // Notification should be marked as read
            Optional<Notification> updated = socialStorage.getNotification(notification.id());
            assertTrue(updated.isPresent());
            assertTrue(updated.get().isRead());
        }

        @Test
        @DisplayName("Shows multiple notifications")
        void showsMultipleNotifications() {
            Notification n1 = Notification.create(
                    testUser.getId(), Notification.Type.MATCH_FOUND, "Match 1", "You matched!", Map.of());
            Notification n2 = Notification.create(
                    testUser.getId(), Notification.Type.NEW_MESSAGE, "Message", "New message!", Map.of());
            Notification n3 = Notification.create(
                    testUser.getId(),
                    Notification.Type.FRIEND_REQUEST,
                    "Friend Request",
                    "Someone wants to be friends!",
                    Map.of());

            socialStorage.saveNotification(n1);
            socialStorage.saveNotification(n2);
            socialStorage.saveNotification(n3);

            RelationshipHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::viewNotifications);
        }
    }

    // === Helper Methods ===

    private User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio for " + name);
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.TEXT_ONLY,
                PacePreferences.DepthPreference.SMALL_TALK));
        user.activate();
        return user;
    }

    // === In-Memory Mock Storages ===

    private static class InMemorySocialStorage implements SocialStorage {
        private final Map<UUID, FriendRequest> friendRequests = new HashMap<>();
        private final Map<UUID, Notification> notifications = new HashMap<>();

        @Override
        public void saveFriendRequest(FriendRequest request) {
            friendRequests.put(request.id(), request);
        }

        @Override
        public void updateFriendRequest(FriendRequest request) {
            friendRequests.put(request.id(), request);
        }

        @Override
        public Optional<FriendRequest> getFriendRequest(UUID id) {
            return Optional.ofNullable(friendRequests.get(id));
        }

        @Override
        public Optional<FriendRequest> getPendingFriendRequestBetween(UUID user1, UUID user2) {
            return friendRequests.values().stream()
                    .filter(r -> r.isPending())
                    .filter(r -> (r.fromUserId().equals(user1) && r.toUserId().equals(user2))
                            || (r.fromUserId().equals(user2) && r.toUserId().equals(user1)))
                    .findFirst();
        }

        @Override
        public List<FriendRequest> getPendingFriendRequestsForUser(UUID userId) {
            return friendRequests.values().stream()
                    .filter(r -> r.toUserId().equals(userId) && r.isPending())
                    .toList();
        }

        @Override
        public void deleteFriendRequest(UUID id) {
            friendRequests.remove(id);
        }

        @Override
        public void saveNotification(Notification notification) {
            notifications.put(notification.id(), notification);
        }

        @Override
        public void markNotificationAsRead(UUID id) {
            Notification n = notifications.get(id);
            if (n != null && !n.isRead()) {
                notifications.put(
                        id,
                        new Notification(
                                n.id(), n.userId(), n.type(), n.title(), n.message(), n.createdAt(), true, n.data()));
            }
        }

        @Override
        public List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
            return notifications.values().stream()
                    .filter(n -> n.userId().equals(userId))
                    .filter(n -> !unreadOnly || !n.isRead())
                    .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                    .toList();
        }

        @Override
        public Optional<Notification> getNotification(UUID id) {
            return Optional.ofNullable(notifications.get(id));
        }

        @Override
        public void deleteNotification(UUID id) {
            notifications.remove(id);
        }

        @Override
        public void deleteOldNotifications(Instant before) {
            notifications.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(before));
        }
    }

    private static class InMemoryMessagingStorage implements MessagingStorage {
        private final Map<String, datingapp.core.Messaging.Conversation> conversations = new HashMap<>();
        private final Map<String, List<datingapp.core.Messaging.Message>> messages = new HashMap<>();

        @Override
        public void saveConversation(datingapp.core.Messaging.Conversation conversation) {
            conversations.put(conversation.getId(), conversation);
        }

        @Override
        public Optional<datingapp.core.Messaging.Conversation> getConversation(String conversationId) {
            return Optional.ofNullable(conversations.get(conversationId));
        }

        @Override
        public Optional<datingapp.core.Messaging.Conversation> getConversationByUsers(UUID userA, UUID userB) {
            String id = datingapp.core.Messaging.Conversation.generateId(userA, userB);
            return getConversation(id);
        }

        @Override
        public List<datingapp.core.Messaging.Conversation> getConversationsFor(UUID userId) {
            return conversations.values().stream()
                    .filter(c -> c.involves(userId))
                    .toList();
        }

        @Override
        public void updateConversationLastMessageAt(String conversationId, Instant timestamp) {}

        @Override
        public void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp) {}

        @Override
        public void archiveConversation(String conversationId, Match.ArchiveReason reason) {}

        @Override
        public void setConversationVisibility(String conversationId, UUID userId, boolean visible) {}

        @Override
        public void deleteConversation(String conversationId) {
            conversations.remove(conversationId);
        }

        @Override
        public void saveMessage(datingapp.core.Messaging.Message message) {
            messages.computeIfAbsent(message.conversationId(), k -> new ArrayList<>())
                    .add(message);
        }

        @Override
        public List<datingapp.core.Messaging.Message> getMessages(String conversationId, int limit, int offset) {
            return messages.getOrDefault(conversationId, List.of());
        }

        @Override
        public Optional<datingapp.core.Messaging.Message> getLatestMessage(String conversationId) {
            List<datingapp.core.Messaging.Message> list = messages.get(conversationId);
            return list == null || list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1));
        }

        @Override
        public int countMessages(String conversationId) {
            return messages.getOrDefault(conversationId, List.of()).size();
        }

        @Override
        public int countMessagesAfter(String conversationId, Instant after) {
            return 0;
        }

        @Override
        public int countMessagesNotFromSender(String conversationId, UUID senderId) {
            return 0;
        }

        @Override
        public int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId) {
            return 0;
        }

        @Override
        public void deleteMessagesByConversation(String conversationId) {
            messages.remove(conversationId);
        }
    }
}
