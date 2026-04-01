package datingapp.app.event.handlers;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.storage.CommunicationStorage;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for relationship transition events and creates user-facing notifications.
 * Handles graceful-exit and friend-zone-acceptance notifications.
 */
public final class NotificationEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(NotificationEventHandler.class);

    private static final String DATA_MATCH_ID = "matchId";
    private static final String DATA_OTHER_USER_ID = "otherUserId";
    private static final String DATA_SENDER_ID = "senderId";
    private static final String DATA_MESSAGE_ID = "messageId";
    private static final String DATA_REQUEST_ID = "requestId";
    private static final String DATA_ACCEPTER_USER_ID = "accepterUserId";

    private final CommunicationStorage communicationStorage;

    public NotificationEventHandler(CommunicationStorage communicationStorage) {
        this.communicationStorage = Objects.requireNonNull(communicationStorage, "communicationStorage");
    }

    /** Subscribes this handler to the given event bus with BEST_EFFORT policy. */
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
        switch (event.toState()) {
            case GRACEFUL_EXIT -> {
                Notification notification = Notification.create(
                        event.targetId(),
                        Notification.Type.GRACEFUL_EXIT,
                        "Relationship Ended",
                        "The other user has gracefully moved on from this relationship.",
                        Map.of("initiatorId", event.initiatorId().toString()));
                communicationStorage.saveNotification(notification);
            }
            case FRIEND_ZONE_REQUESTED -> {
                Notification notification = Notification.create(
                        event.initiatorId(),
                        Notification.Type.FRIEND_REQUEST_ACCEPTED,
                        "Friend Request Accepted",
                        "Your match with the other user has successfully transitioned to the Friend Zone.",
                        Map.of("responderId", event.targetId().toString()));
                communicationStorage.saveNotification(notification);
            }
            case MATCHED, UNMATCHED, ACTIVE -> {
                // No notification needed for other transitions
            }
            default -> {
                // Defensive default for future enum additions.
            }
        }
    }

    void onMatchCreated(AppEvent.MatchCreated event) {
        saveNotification(Notification.create(
                event.userA(),
                Notification.Type.MATCH_FOUND,
                "New Match!",
                "You have a new match!",
                Map.of(
                        DATA_MATCH_ID,
                        event.matchId(),
                        DATA_OTHER_USER_ID,
                        event.userB().toString())));
        saveNotification(Notification.create(
                event.userB(),
                Notification.Type.MATCH_FOUND,
                "New Match!",
                "You have a new match!",
                Map.of(
                        DATA_MATCH_ID,
                        event.matchId(),
                        DATA_OTHER_USER_ID,
                        event.userA().toString())));
    }

    void onMessageSent(AppEvent.MessageSent event) {
        saveNotification(Notification.create(
                event.recipientId(),
                Notification.Type.NEW_MESSAGE,
                "New Message",
                "Someone sent you a new message.",
                Map.of(
                        DATA_SENDER_ID, event.senderId().toString(),
                        DATA_MESSAGE_ID, event.messageId().toString())));
    }

    void onFriendRequestAccepted(AppEvent.FriendRequestAccepted event) {
        saveNotification(Notification.create(
                event.fromUserId(),
                Notification.Type.FRIEND_REQUEST_ACCEPTED,
                "Friend Request Accepted",
                "Your friend request was accepted.",
                Map.of(
                        DATA_REQUEST_ID, event.requestId().toString(),
                        DATA_ACCEPTER_USER_ID, event.toUserId().toString(),
                        DATA_MATCH_ID, event.matchId())));
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
}
