package datingapp.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Container for social domain models: friend requests and notifications. */
public final class Social {

    private Social() {
        // Utility class - prevent instantiation
    }

    /** Returns true if a friend request status is terminal. */
    public static boolean isTerminalStatus(FriendRequest.Status status) {
        return status == FriendRequest.Status.DECLINED || status == FriendRequest.Status.EXPIRED;
    }

    /** Represents a request to transition a match to the "Friend Zone". */
    public record FriendRequest(
            UUID id, UUID fromUserId, UUID toUserId, Instant createdAt, Status status, Instant respondedAt) {

        public FriendRequest {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(fromUserId, "fromUserId cannot be null");
            Objects.requireNonNull(toUserId, "toUserId cannot be null");
            Objects.requireNonNull(createdAt, "createdAt cannot be null");
            Objects.requireNonNull(status, "status cannot be null");
            if (fromUserId.equals(toUserId)) {
                throw new IllegalArgumentException("fromUserId cannot equal toUserId");
            }
            if (status == Status.PENDING && respondedAt != null) {
                throw new IllegalArgumentException("respondedAt must be null for pending requests");
            }
        }

        /** Status of a friend zone request. */
        public enum Status {
            PENDING,
            ACCEPTED,
            DECLINED,
            EXPIRED
        }

        public static FriendRequest create(UUID fromUserId, UUID toUserId) {
            return new FriendRequest(UUID.randomUUID(), fromUserId, toUserId, AppClock.now(), Status.PENDING, null);
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

        public Notification {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(userId, "userId cannot be null");
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(title, "title cannot be null");
            Objects.requireNonNull(message, "message cannot be null");
            Objects.requireNonNull(createdAt, "createdAt cannot be null");
            if (title.isBlank()) {
                throw new IllegalArgumentException("title cannot be blank");
            }
            if (message.isBlank()) {
                throw new IllegalArgumentException("message cannot be blank");
            }
            data = data != null ? Map.copyOf(data) : Map.of();
        }

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
            return new Notification(UUID.randomUUID(), userId, type, title, message, AppClock.now(), false, data);
        }
    }
}
