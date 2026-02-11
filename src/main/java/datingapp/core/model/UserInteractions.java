package datingapp.core.model;

import datingapp.core.AppClock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Container for user interaction domain models (likes, blocks, reports). */
public final class UserInteractions {

    public static final String ID_REQUIRED = "id cannot be null";
    public static final String CREATED_AT_REQUIRED = "createdAt cannot be null";

    private UserInteractions() {
        // Utility class
    }

    /** Represents a like or pass action from one user to another. Immutable after creation. */
    public static record Like(UUID id, UUID whoLikes, UUID whoGotLiked, Direction direction, Instant createdAt) {

        /**
         * The direction of a like action. LIKE = interested in the user PASS = not interested in
         * the user
         */
        public enum Direction {
            LIKE,
            PASS
        }

        // Compact constructor - validates parameters
        public Like {
            Objects.requireNonNull(id, ID_REQUIRED);
            Objects.requireNonNull(whoLikes, "whoLikes cannot be null");
            Objects.requireNonNull(whoGotLiked, "whoGotLiked cannot be null");
            Objects.requireNonNull(direction, "direction cannot be null");
            Objects.requireNonNull(createdAt, CREATED_AT_REQUIRED);

            if (whoLikes.equals(whoGotLiked)) {
                throw new IllegalArgumentException("Cannot like yourself");
            }
        }

        /** Creates a new Like with auto-generated ID and current timestamp. */
        public static Like create(UUID whoLikes, UUID whoGotLiked, Direction direction) {
            return new Like(UUID.randomUUID(), whoLikes, whoGotLiked, direction, AppClock.now());
        }
    }

    /**
     * Represents a block between two users. When user A blocks user B, neither can see the other
     * (bidirectional effect). Immutable after creation.
     */
    public static record Block(
            UUID id,
            UUID blockerId, // User who initiated the block
            UUID blockedId, // User who got blocked
            Instant createdAt) {

        // Compact constructor - validates parameters
        public Block {
            Objects.requireNonNull(id, ID_REQUIRED);
            Objects.requireNonNull(blockerId, "blockerId cannot be null");
            Objects.requireNonNull(blockedId, "blockedId cannot be null");
            Objects.requireNonNull(createdAt, CREATED_AT_REQUIRED);

            if (blockerId.equals(blockedId)) {
                throw new IllegalArgumentException("Cannot block yourself");
            }
        }

        /** Creates a new Block with generated ID and current timestamp. */
        public static Block create(UUID blockerId, UUID blockedId) {
            Objects.requireNonNull(blockerId, "blockerId cannot be null");
            Objects.requireNonNull(blockedId, "blockedId cannot be null");

            return new Block(UUID.randomUUID(), blockerId, blockedId, AppClock.now());
        }
    }

    /** Represents a report filed against a user. Immutable after creation. */
    public static record Report(
            UUID id,
            UUID reporterId, // Who filed the report
            UUID reportedUserId, // Who is being reported
            Reason reason,
            String description, // Optional free text (max 500 chars)
            Instant createdAt) {

        /** Reasons why a user can be reported. */
        public enum Reason {
            SPAM,
            INAPPROPRIATE_CONTENT,
            HARASSMENT,
            FAKE_PROFILE,
            UNDERAGE,
            OTHER
        }

        // Compact constructor - validates parameters
        public Report {
            Objects.requireNonNull(id, ID_REQUIRED);
            Objects.requireNonNull(reporterId, "reporterId cannot be null");
            Objects.requireNonNull(reportedUserId, "reportedUserId cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
            Objects.requireNonNull(createdAt, CREATED_AT_REQUIRED);

            if (reporterId.equals(reportedUserId)) {
                throw new IllegalArgumentException("Cannot report yourself");
            }
            if (description != null && description.length() > 500) {
                throw new IllegalArgumentException("Description too long (max 500)");
            }
        }

        /** Creates a new Report with generated ID and current timestamp. */
        public static Report create(UUID reporterId, UUID reportedUserId, Reason reason, String description) {
            return new Report(UUID.randomUUID(), reporterId, reportedUserId, reason, description, AppClock.now());
        }
    }

    /** Returns true if a friend request status is terminal. */
    public static boolean isTerminalStatus(FriendRequest.Status status) {
        return status == FriendRequest.Status.DECLINED || status == FriendRequest.Status.EXPIRED;
    }

    /** Represents a request to transition a match to the "Friend Zone". */
    public static record FriendRequest(
            UUID id, UUID fromUserId, UUID toUserId, Instant createdAt, Status status, Instant respondedAt) {

        public FriendRequest {
            Objects.requireNonNull(id, ID_REQUIRED);
            Objects.requireNonNull(fromUserId, "fromUserId cannot be null");
            Objects.requireNonNull(toUserId, "toUserId cannot be null");
            Objects.requireNonNull(createdAt, CREATED_AT_REQUIRED);
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
    public static record Notification(
            UUID id,
            UUID userId,
            Type type,
            String title,
            String message,
            Instant createdAt,
            boolean isRead,
            Map<String, String> data) {

        public Notification {
            Objects.requireNonNull(id, ID_REQUIRED);
            Objects.requireNonNull(userId, "userId cannot be null");
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(title, "title cannot be null");
            Objects.requireNonNull(message, "message cannot be null");
            Objects.requireNonNull(createdAt, CREATED_AT_REQUIRED);
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
