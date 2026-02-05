package datingapp.core;

import java.time.Instant;
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
    public record Like(UUID id, UUID whoLikes, UUID whoGotLiked, Direction direction, Instant createdAt) {

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
            return new Like(UUID.randomUUID(), whoLikes, whoGotLiked, direction, Instant.now());
        }
    }

    /**
     * Represents a block between two users. When user A blocks user B, neither can see the other
     * (bidirectional effect). Immutable after creation.
     */
    public record Block(
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

            return new Block(UUID.randomUUID(), blockerId, blockedId, Instant.now());
        }
    }

    /** Represents a report filed against a user. Immutable after creation. */
    public record Report(
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
            return new Report(UUID.randomUUID(), reporterId, reportedUserId, reason, description, Instant.now());
        }
    }
}
