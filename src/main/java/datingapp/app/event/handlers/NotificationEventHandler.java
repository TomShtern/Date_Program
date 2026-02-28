package datingapp.app.event.handlers;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.storage.CommunicationStorage;
import java.util.Map;
import java.util.Objects;

/**
 * Listens for relationship transition events and creates user-facing notifications.
 * Handles graceful-exit and friend-zone-acceptance notifications.
 */
public final class NotificationEventHandler {

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
    }

    void onRelationshipTransitioned(AppEvent.RelationshipTransitioned event) {
        switch (event.toState()) {
            case "GRACEFUL_EXIT" -> {
                Notification notification = Notification.create(
                        event.targetId(),
                        Notification.Type.GRACEFUL_EXIT,
                        "Relationship Ended",
                        "The other user has gracefully moved on from this relationship.",
                        Map.of("initiatorId", event.initiatorId().toString()));
                communicationStorage.saveNotification(notification);
            }
            case "FRIENDS" -> {
                Notification notification = Notification.create(
                        event.initiatorId(),
                        Notification.Type.FRIEND_REQUEST_ACCEPTED,
                        "Friend Request Accepted",
                        "Your match with the other user has successfully transitioned to the Friend Zone.",
                        Map.of("responderId", event.targetId().toString()));
                communicationStorage.saveNotification(notification);
            }
            default -> {
                // No notification needed for other transitions
            }
        }
    }
}
