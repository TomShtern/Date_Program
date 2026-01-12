package datingapp.core;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Represents a system notification for a user. */
public record Notification(
        UUID id,
        UUID userId,
        NotificationType type,
        String title,
        String message,
        Instant createdAt,
        boolean isRead,
        Map<String, String> data) {

    public static Notification create(
            UUID userId, NotificationType type, String title, String message, Map<String, String> data) {
        return new Notification(UUID.randomUUID(), userId, type, title, message, Instant.now(), false, data);
    }
}
