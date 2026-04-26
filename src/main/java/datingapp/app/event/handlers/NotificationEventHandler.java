package datingapp.app.event.handlers;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.storage.CommunicationStorage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NotificationEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationEventHandler.class);

    private static final String DATA_MATCH_ID = "matchId";
    private static final String DATA_CONVERSATION_ID = "conversationId";
    private static final String DATA_OTHER_USER_ID = "otherUserId";
    private static final String DATA_SENDER_ID = "senderId";
    private static final String DATA_MESSAGE_ID = "messageId";
    private static final String DATA_REQUEST_ID = "requestId";
    private static final String DATA_ACCEPTER_USER_ID = "accepterUserId";

    private final CommunicationStorage communicationStorage;

    public NotificationEventHandler(CommunicationStorage communicationStorage) {
        this.communicationStorage = Objects.requireNonNull(communicationStorage, "communicationStorage");
    }

    public void register(AppEventBus eventBus) {
        eventBus.subscribe(
                AppEvent.RelationshipTransitioned.class,
                this::onRelationshipTransitioned,
                AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(AppEvent.MatchCreated.class, this::onMatchCreated, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(AppEvent.MessageSent.class, this::onMessageSent, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(
                AppEvent.FriendRequestAccepted.class,
                this::onFriendRequestAccepted,
                AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(
                AppEvent.AccountDeleted.class, this::onAccountDeleted, AppEventBus.HandlerPolicy.BEST_EFFORT);
    }

    void onRelationshipTransitioned(AppEvent.RelationshipTransitioned event) {
        String pairId = Conversation.generateId(event.initiatorId(), event.targetId());
        Notification notification =
                switch (event.toState()) {
                    case GRACEFUL_EXIT ->
                        Notification.create(
                                event.targetId(),
                                Notification.Type.GRACEFUL_EXIT,
                                "Relationship Ended",
                                "The other user has gracefully moved on from this relationship.",
                                contextWith(
                                        pairId,
                                        "initiatorId",
                                        event.initiatorId().toString()));
                    case FRIEND_ZONE_REQUESTED ->
                        Notification.create(
                                event.initiatorId(),
                                Notification.Type.FRIEND_REQUEST_ACCEPTED,
                                "Friend Request Accepted",
                                "Your match with the other user has successfully transitioned to the Friend Zone.",
                                contextWith(
                                        pairId,
                                        DATA_REQUEST_ID,
                                        pairId,
                                        "responderId",
                                        event.targetId().toString(),
                                        DATA_ACCEPTER_USER_ID,
                                        event.targetId().toString()));
                    default -> null;
                };
        if (notification != null) {
            communicationStorage.saveNotification(notification);
        }
    }

    void onMatchCreated(AppEvent.MatchCreated event) {
        String pairId = Conversation.generateId(event.userA(), event.userB());
        saveNotification(Notification.create(
                event.userA(),
                Notification.Type.MATCH_FOUND,
                "New Match!",
                "You have a new match!",
                contextWith(pairId, DATA_OTHER_USER_ID, event.userB().toString())));
        saveNotification(Notification.create(
                event.userB(),
                Notification.Type.MATCH_FOUND,
                "New Match!",
                "You have a new match!",
                contextWith(pairId, DATA_OTHER_USER_ID, event.userA().toString())));
    }

    void onMessageSent(AppEvent.MessageSent event) {
        saveNotification(Notification.create(
                event.recipientId(),
                Notification.Type.NEW_MESSAGE,
                "New Message",
                "Someone sent you a new message.",
                contextWith(
                        Conversation.generateId(event.senderId(), event.recipientId()),
                        DATA_SENDER_ID,
                        event.senderId().toString(),
                        DATA_MESSAGE_ID,
                        event.messageId().toString())));
    }

    void onFriendRequestAccepted(AppEvent.FriendRequestAccepted event) {
        String pairId = Conversation.generateId(event.fromUserId(), event.toUserId());
        saveNotification(Notification.create(
                event.fromUserId(),
                Notification.Type.FRIEND_REQUEST_ACCEPTED,
                "Friend Request Accepted",
                "Your friend request was accepted.",
                contextWith(
                        pairId,
                        DATA_REQUEST_ID,
                        event.requestId().toString(),
                        DATA_ACCEPTER_USER_ID,
                        event.toUserId().toString())));
    }

    void onAccountDeleted(AppEvent.AccountDeleted event) {
        if (communicationStorage != null) {
            communicationStorage.deleteNotificationsForUser(event.userId());
        }
        if (logger.isInfoEnabled()) {
            logger.info("Account deleted for userId={}, cleaning up notifications", event.userId());
        }
    }

    private void saveNotification(Notification notification) {
        communicationStorage.saveNotification(notification);
    }

    private static Map<String, String> contextWith(String pairId, String... extraKeyValues) {
        if (extraKeyValues.length % 2 != 0) {
            throw new IllegalArgumentException("extraKeyValues must contain key/value pairs");
        }
        LinkedHashMap<String, String> data = new LinkedHashMap<>();
        data.put(DATA_MATCH_ID, pairId);
        data.put(DATA_CONVERSATION_ID, pairId);
        for (int i = 0; i < extraKeyValues.length; i += 2) {
            data.put(extraKeyValues[i], extraKeyValues[i + 1]);
        }
        return Map.copyOf(data);
    }
}
