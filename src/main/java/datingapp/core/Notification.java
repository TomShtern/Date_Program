package datingapp.core;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Represents a system notification for a user. */
public record Notification(
        UUID id,
        UUID userId,
        Type type,
        String title,
        String message,
        Instant createdAt,
        boolean isRead,
        Map<String, String> data) {

    /** Types of notifications in the system. */
    public enum Type {
        MATCH_FOUND,
        NEW_MESSAGE,
        FRIEND_REQUEST,
        FRIEND_REQUEST_ACCEPTED,
        GRACEFUL_EXIT
    }

    public static Notification create(UUID userId, Type type, String title, String message, Map<String, String> data) {
        return new Notification(UUID.randomUUID(), userId, type, title, message, Instant.now(), false, data);
    }
}
