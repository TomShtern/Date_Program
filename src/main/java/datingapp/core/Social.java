package datingapp.core;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Container for social domain models: friend requests and notifications. */
public final class Social {

    private Social() {
        // Utility class - prevent instantiation
    }

    /** Represents a request to transition a match to the "Friend Zone". */
    public record FriendRequest(
            UUID id, UUID fromUserId, UUID toUserId, Instant createdAt, Status status, Instant respondedAt) {

        /** Status of a friend zone request. */
        public enum Status {
            PENDING,
            ACCEPTED,
            DECLINED,
            EXPIRED
        }

        public static FriendRequest create(UUID fromUserId, UUID toUserId) {
            return new FriendRequest(UUID.randomUUID(), fromUserId, toUserId, Instant.now(), Status.PENDING, null);
        }

        public boolean isPending() {
            return status == Status.PENDING;
        }
    }

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

        public static Notification create(
                UUID userId, Type type, String title, String message, Map<String, String> data) {
            return new Notification(UUID.randomUUID(), userId, type, title, message, Instant.now(), false, data);
        }
    }
}
