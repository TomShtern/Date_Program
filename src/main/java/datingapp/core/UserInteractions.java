package datingapp.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Container for user interaction domain models (likes, blocks, reports). */
public final class UserInteractions {

    private static final String ID_REQUIRED = "id cannot be null";
    private static final String CREATED_AT_REQUIRED = "createdAt cannot be null";

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

        /**
         * Creates a Like record with validation.
         *
         * @param id the unique identifier for this like
         * @param whoLikes the user who performed the like/pass action
         * @param whoGotLiked the user who received the like/pass action
         * @param direction whether this is a LIKE or PASS
         * @param createdAt when the action was performed
         */
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

        /**
         * Creates a Block record with validation.
         *
         * @param id the unique identifier for this block
         * @param blockerId the user who initiated the block
         * @param blockedId the user who got blocked
         * @param createdAt when the block was created
         */
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

        /**
         * Creates a Report record with validation.
         *
         * @param id the unique identifier for this report
         * @param reporterId the user who filed the report
         * @param reportedUserId the user being reported
         * @param reason the reason for the report
         * @param description optional description (max 500 characters)
         * @param createdAt when the report was filed
         */
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

    // ========== STORAGE INTERFACES ==========

    /** Storage interface for Like entities. Defined in core, implemented in storage layer. */
    public interface LikeStorage {

        /** Get a specific like record (for Match Quality computation). */
        Optional<Like> getLike(UUID fromUserId, UUID toUserId);

        /** Saves a like/pass action. */
        void save(Like like);

        /** Checks if a like/pass already exists from one user to another. */
        boolean exists(UUID from, UUID to);

        /** Checks if both users have liked each other (mutual LIKE, not PASS). */
        boolean mutualLikeExists(UUID a, UUID b);

        /** Gets all user IDs that the given user has liked or passed. */
        Set<UUID> getLikedOrPassedUserIds(UUID userId);

        /** Gets all user IDs that liked the given user (direction=LIKE). */
        Set<UUID> getUserIdsWhoLiked(UUID userId);

        /** Returns like timestamps for each user who liked the given user (direction=LIKE). */
        Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId);

        /** Count likes/passes GIVEN BY a user with specific direction. */
        int countByDirection(UUID userId, Like.Direction direction);

        /** Count likes/passes RECEIVED BY a user with specific direction. */
        int countReceivedByDirection(UUID userId, Like.Direction direction);

        /** Count mutual likes (users this person liked who also liked them back). */
        int countMutualLikes(UUID userId);

        /** Count likes given by user since the specified start of day. */
        int countLikesToday(UUID userId, Instant startOfDay);

        /** Count passes given by user since the specified start of day. */
        int countPassesToday(UUID userId, Instant startOfDay);

        /** Delete a like by ID. Used for undo functionality. */
        void delete(UUID likeId);
    }

    /** Storage interface for Block entities. Defined in core, implemented in storage layer. */
    public interface BlockStorage {

        /** Saves a block. */
        void save(Block block);

        /** Returns true if EITHER user has blocked the other. Block is bidirectional in effect. */
        boolean isBlocked(UUID userA, UUID userB);

        /** Returns all user IDs that the given user should not see. */
        Set<UUID> getBlockedUserIds(UUID userId);

        /** Count blocks GIVEN by a user (users they blocked). */
        int countBlocksGiven(UUID userId);

        /** Count blocks RECEIVED by a user (users who blocked them). */
        int countBlocksReceived(UUID userId);
    }

    /** Storage interface for Report entities. Defined in core, implemented in storage layer. */
    public interface ReportStorage {

        /** Saves a report. */
        void save(Report report);

        /** Count reports against a user. */
        int countReportsAgainst(UUID userId);

        /** Check if reporter has already reported this user. */
        boolean hasReported(UUID reporterId, UUID reportedUserId);

        /** Get all reports against a user (for admin review later). */
        List<Report> getReportsAgainst(UUID userId);

        /** Count reports MADE BY a user (reports they filed). */
        int countReportsBy(UUID userId);
    }
}
