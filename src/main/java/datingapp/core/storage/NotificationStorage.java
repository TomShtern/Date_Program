package datingapp.core.storage;

import datingapp.core.Social.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage interface for Notification entities.
 * Defined in core, implemented in storage layer.
 */
public interface NotificationStorage {

    /** Saves a new notification. */
    void save(Notification notification);

    /** Marks a notification as read. */
    void markAsRead(UUID id);

    /** Gets notifications for a user, optionally filtering to unread only. */
    List<Notification> getForUser(UUID userId, boolean unreadOnly);

    /** Gets a notification by ID. */
    Optional<Notification> get(UUID id);

    /** Deletes a notification by ID. */
    void delete(UUID id);

    /** Deletes old notifications before a given timestamp. */
    void deleteOldNotifications(Instant before);
}
