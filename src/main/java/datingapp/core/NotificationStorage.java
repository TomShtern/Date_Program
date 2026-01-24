package datingapp.core;

import datingapp.core.Social.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage interface for Notification entities. */
public interface NotificationStorage {
    void save(Notification notification);

    void markAsRead(UUID id);

    List<Notification> getForUser(UUID userId, boolean unreadOnly);

    Optional<Notification> get(UUID id);

    void delete(UUID id);

    void deleteOldNotifications(java.time.Instant before);
}
