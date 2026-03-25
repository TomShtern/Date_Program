package datingapp.app.event.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.storage.CommunicationStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationEventHandlerTest {

    private InProcessAppEventBus bus;
    private final List<Notification> savedNotifications = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bus = new InProcessAppEventBus();
        savedNotifications.clear();

        NotificationEventHandler handler = new NotificationEventHandler(new CapturingStorage());
        handler.register(bus);
    }

    @Test
    void gracefulExitCreatesNotification() {
        UUID initiatorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        bus.publish(new AppEvent.RelationshipTransitioned(
                "match-" + UUID.randomUUID(), initiatorId, targetId, "ACTIVE", "GRACEFUL_EXIT", Instant.now()));

        assertEquals(1, savedNotifications.size());
        Notification n = savedNotifications.getFirst();
        assertEquals(targetId, n.userId());
        assertEquals(Notification.Type.GRACEFUL_EXIT, n.type());
        assertEquals("Relationship Ended", n.title());
        assertEquals(initiatorId.toString(), n.data().get("initiatorId"));
    }

    @Test
    void friendZoneAcceptanceCreatesNotification() {
        UUID initiatorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        bus.publish(new AppEvent.RelationshipTransitioned(
                "match-" + UUID.randomUUID(), initiatorId, targetId, "ACTIVE", "FRIENDS", Instant.now()));

        assertEquals(1, savedNotifications.size());
        Notification n = savedNotifications.getFirst();
        assertEquals(initiatorId, n.userId());
        assertEquals(Notification.Type.FRIEND_REQUEST_ACCEPTED, n.type());
        assertEquals("Friend Request Accepted", n.title());
        assertEquals(targetId.toString(), n.data().get("responderId"));
    }

    @Test
    void otherTransitionsCreateNoNotification() {
        bus.publish(new AppEvent.RelationshipTransitioned(
                "match-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PENDING",
                "ACTIVE",
                Instant.now()));

        assertEquals(0, savedNotifications.size());
    }

    @Test
    void matchCreatedCreatesNotificationsForBothUsers() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String matchId = "match-" + UUID.randomUUID();

        bus.publish(new AppEvent.MatchCreated(matchId, userA, userB, Instant.now()));

        assertEquals(2, savedNotifications.size());
        Notification first = savedNotifications.get(0);
        Notification second = savedNotifications.get(1);
        assertEquals(Notification.Type.MATCH_FOUND, first.type());
        assertEquals(Notification.Type.MATCH_FOUND, second.type());
        assertEquals(matchId, first.data().get("matchId"));
        assertEquals(matchId, second.data().get("matchId"));
    }

    @Test
    void messageSentCreatesNotificationForRecipient() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        bus.publish(new AppEvent.MessageSent(senderId, recipientId, messageId, Instant.now()));

        assertEquals(1, savedNotifications.size());
        Notification notification = savedNotifications.getFirst();
        assertEquals(recipientId, notification.userId());
        assertEquals(Notification.Type.NEW_MESSAGE, notification.type());
        assertEquals(senderId.toString(), notification.data().get("senderId"));
        assertEquals(messageId.toString(), notification.data().get("messageId"));
    }

    @Test
    void friendRequestAcceptedCreatesNotificationForRequester() {
        UUID requesterId = UUID.randomUUID();
        UUID accepterId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String matchId = "match-" + UUID.randomUUID();

        bus.publish(new AppEvent.FriendRequestAccepted(requestId, requesterId, accepterId, matchId, Instant.now()));

        assertEquals(1, savedNotifications.size());
        Notification notification = savedNotifications.getFirst();
        assertEquals(requesterId, notification.userId());
        assertEquals(Notification.Type.FRIEND_REQUEST_ACCEPTED, notification.type());
        assertEquals("Friend Request Accepted", notification.title());
        assertEquals("Your friend request was accepted.", notification.message());
        assertEquals(requestId.toString(), notification.data().get("requestId"));
        assertEquals(accepterId.toString(), notification.data().get("accepterUserId"));
        assertEquals(matchId, notification.data().get("matchId"));
    }

    @Test
    void handlerExceptionsDoNotPropagate() {
        bus.subscribe(
                AppEvent.RelationshipTransitioned.class,
                event -> {
                    throw new RuntimeException("simulated notification failure");
                },
                AppEventBus.HandlerPolicy.BEST_EFFORT);

        bus.publish(new AppEvent.RelationshipTransitioned(
                "match-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACTIVE",
                "GRACEFUL_EXIT",
                Instant.now()));

        // Original handler still fired despite the throwing handler
        assertEquals(1, savedNotifications.size());
    }

    @Test
    void accountDeletedLogsAndContinues() {
        UUID userId = UUID.randomUUID();

        bus.publish(new AppEvent.AccountDeleted(userId, AppEvent.DeletionReason.USER_REQUEST, Instant.now()));

        // No exception should be thrown in BEST_EFFORT mode
        // The handler logs but doesn't fail if no cleanup method exists
        assertEquals(0, savedNotifications.size());
    }

    /** Minimal stub that only captures saveNotification calls. */
    private class CapturingStorage implements CommunicationStorage {

        @Override
        public void saveNotification(Notification notification) {
            savedNotifications.add(notification);
        }

        // --- Unused stubs below ---

        @Override
        public void saveConversation(Conversation c) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Conversation> getConversation(String id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Conversation> getConversationByUsers(UUID a, UUID b) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Conversation> getConversationsFor(UUID u, int l, int o) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Conversation> getAllConversationsFor(UUID u) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void updateConversationLastMessageAt(String id, Instant ts) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void updateConversationReadTimestamp(String id, UUID u, Instant ts) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void archiveConversation(String id, UUID u, MatchArchiveReason r) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void setConversationVisibility(String id, UUID u, boolean v) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteConversation(String id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void saveMessage(Message m) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Message> getMessages(String id, int l, int o) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Message> getMessage(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Message> getLatestMessage(String id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countMessages(String id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countMessagesAfter(String id, Instant a) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countMessagesNotFromSender(String id, UUID s) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countMessagesAfterNotFrom(String id, Instant a, UUID s) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteMessage(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteMessagesByConversation(String id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void saveFriendRequest(FriendRequest r) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void updateFriendRequest(FriendRequest r) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<FriendRequest> getFriendRequest(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<FriendRequest> getPendingFriendRequestBetween(UUID a, UUID b) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<FriendRequest> getPendingFriendRequestsForUser(UUID u) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteFriendRequest(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void markNotificationAsRead(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Notification> getNotificationsForUser(UUID u, boolean unread) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Notification> getNotification(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteNotification(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteOldNotifications(Instant before) {
            throw new UnsupportedOperationException("stub");
        }
    }
}
