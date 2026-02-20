package datingapp.core.connection;

import datingapp.core.AppClock;
import datingapp.core.model.Match.MatchArchiveReason;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Consolidated connection domain models (messaging + interactions). */
public final class ConnectionModels {

    private static final String CONVERSATION_ID_SEPARATOR = "_";
    public static final String ID_REQUIRED = "id cannot be null";
    public static final String CREATED_AT_REQUIRED = "createdAt cannot be null";

    private ConnectionModels() {
        // Utility class
    }

    /** Formats a deterministic conversation ID for two users. */
    public static String formatConversationId(UUID a, UUID b) {
        return Conversation.generateId(a, b);
    }

    /** Returns true if a friend request status is terminal. */
    public static boolean isTerminalStatus(FriendRequest.Status status) {
        return status == FriendRequest.Status.DECLINED || status == FriendRequest.Status.EXPIRED;
    }

    /**
     * Represents a single message within a conversation. Immutable after creation.
     */
    public static record Message(UUID id, String conversationId, UUID senderId, String content, Instant createdAt) {

        public static final int MAX_LENGTH = 1000;

        public Message {
            Objects.requireNonNull(id, ID_REQUIRED);
            Objects.requireNonNull(conversationId, "conversationId cannot be null");
            Objects.requireNonNull(senderId, "senderId cannot be null");
            Objects.requireNonNull(createdAt, CREATED_AT_REQUIRED);

            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("Message cannot be empty");
            }
            content = content.trim();
            if (content.length() > MAX_LENGTH) {
                throw new IllegalArgumentException("Message too long (max " + MAX_LENGTH + " characters)");
            }
        }

        public static Message create(String conversationId, UUID senderId, String content) {
            return new Message(UUID.randomUUID(), conversationId, senderId, content, AppClock.now());
        }
    }

    /** Represents a conversation between two matched users. */
    public static class Conversation {

        private final String id;
        private final UUID userA;
        private final UUID userB;
        private final Instant createdAt;
        private Instant lastMessageAt;
        private Instant userAReadAt;
        private Instant userBReadAt;
        private Instant userAArchivedAt;
        private MatchArchiveReason userAArchiveReason;
        private Instant userBArchivedAt;
        private MatchArchiveReason userBArchiveReason;
        private boolean visibleToUserA;
        private boolean visibleToUserB;

        @SuppressWarnings("java:S107")
        public Conversation(
                String id,
                UUID userA,
                UUID userB,
                Instant createdAt,
                Instant lastMessageAt,
                Instant userAReadAt,
                Instant userBReadAt,
                Instant userAArchivedAt,
                MatchArchiveReason userAArchiveReason,
                Instant userBArchivedAt,
                MatchArchiveReason userBArchiveReason,
                boolean visibleToUserA,
                boolean visibleToUserB) {
            Objects.requireNonNull(id, ID_REQUIRED);
            Objects.requireNonNull(userA, "userA cannot be null");
            Objects.requireNonNull(userB, "userB cannot be null");
            Objects.requireNonNull(createdAt, CREATED_AT_REQUIRED);

            if (userA.equals(userB)) {
                throw new IllegalArgumentException("Cannot have conversation with yourself");
            }

            if (userA.toString().compareTo(userB.toString()) > 0) {
                throw new IllegalArgumentException("userA must be lexicographically smaller than userB");
            }

            this.id = id;
            this.userA = userA;
            this.userB = userB;
            this.createdAt = createdAt;
            this.lastMessageAt = lastMessageAt;
            this.userAReadAt = userAReadAt;
            this.userBReadAt = userBReadAt;
            this.userAArchivedAt = userAArchivedAt;
            this.userAArchiveReason = userAArchiveReason;
            this.userBArchivedAt = userBArchivedAt;
            this.userBArchiveReason = userBArchiveReason;
            this.visibleToUserA = visibleToUserA;
            this.visibleToUserB = visibleToUserB;
        }

        public static Conversation create(UUID a, UUID b) {
            Objects.requireNonNull(a, "a cannot be null");
            Objects.requireNonNull(b, "b cannot be null");

            if (a.equals(b)) {
                throw new IllegalArgumentException("Cannot have conversation with yourself");
            }

            String userAString = a.toString();
            String userBString = b.toString();

            UUID normalizedA;
            UUID normalizedB;
            if (userAString.compareTo(userBString) < 0) {
                normalizedA = a;
                normalizedB = b;
            } else {
                normalizedA = b;
                normalizedB = a;
            }

            String id = normalizedA + CONVERSATION_ID_SEPARATOR + normalizedB;
            Instant now = AppClock.now();
            return new Conversation(
                    id, normalizedA, normalizedB, now, null, null, null, null, null, null, null, true, true);
        }

        public static String generateId(UUID a, UUID b) {
            String userAString = a.toString();
            String userBString = b.toString();

            if (userAString.compareTo(userBString) < 0) {
                return userAString + CONVERSATION_ID_SEPARATOR + userBString;
            }
            return userBString + CONVERSATION_ID_SEPARATOR + userAString;
        }

        public boolean involves(UUID userId) {
            return userA.equals(userId) || userB.equals(userId);
        }

        public UUID getOtherUser(UUID userId) {
            if (userA.equals(userId)) {
                return userB;
            }
            if (userB.equals(userId)) {
                return userA;
            }
            throw new IllegalArgumentException("User is not part of this conversation");
        }

        public void updateLastMessageAt(Instant timestamp) {
            Objects.requireNonNull(timestamp, "timestamp cannot be null");
            this.lastMessageAt = timestamp;
        }

        public void updateReadTimestamp(UUID userId, Instant timestamp) {
            Objects.requireNonNull(userId, "userId cannot be null");
            Objects.requireNonNull(timestamp, "timestamp cannot be null");

            if (userA.equals(userId)) {
                this.userAReadAt = timestamp;
            } else if (userB.equals(userId)) {
                this.userBReadAt = timestamp;
            } else {
                throw new IllegalArgumentException("User is not part of this conversation");
            }
        }

        public Instant getLastReadAt(UUID userId) {
            if (userA.equals(userId)) {
                return userAReadAt;
            }
            if (userB.equals(userId)) {
                return userBReadAt;
            }
            throw new IllegalArgumentException("User is not part of this conversation");
        }

        public String getId() {
            return id;
        }

        public UUID getUserA() {
            return userA;
        }

        public UUID getUserB() {
            return userB;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getLastMessageAt() {
            return lastMessageAt;
        }

        public Instant getUserAReadAt() {
            return userAReadAt;
        }

        public Instant getUserBReadAt() {
            return userBReadAt;
        }

        public Instant getUserAArchivedAt() {
            return userAArchivedAt;
        }

        public MatchArchiveReason getUserAArchiveReason() {
            return userAArchiveReason;
        }

        public Instant getUserBArchivedAt() {
            return userBArchivedAt;
        }

        public MatchArchiveReason getUserBArchiveReason() {
            return userBArchiveReason;
        }

        public boolean isVisibleToUserA() {
            return visibleToUserA;
        }

        public boolean isVisibleToUserB() {
            return visibleToUserB;
        }

        public void archive(UUID userId, MatchArchiveReason reason) {
            if (userA.equals(userId)) {
                this.userAArchivedAt = AppClock.now();
                this.userAArchiveReason = reason;
            } else if (userB.equals(userId)) {
                this.userBArchivedAt = AppClock.now();
                this.userBArchiveReason = reason;
            } else {
                throw new IllegalArgumentException("User is not part of this conversation");
            }
        }

        public void setVisibility(UUID userId, boolean visible) {
            if (userA.equals(userId)) {
                this.visibleToUserA = visible;
            } else if (userB.equals(userId)) {
                this.visibleToUserB = visible;
            } else {
                throw new IllegalArgumentException("User is not part of this conversation");
            }
        }

        public boolean isVisibleTo(UUID userId) {
            if (userA.equals(userId)) {
                return visibleToUserA;
            }
            return userB.equals(userId) && visibleToUserB;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Conversation that = (Conversation) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "Conversation{id='" + id + "', lastMessageAt=" + lastMessageAt + "}";
        }
    }

    /** Represents a like or pass action from one user to another. */
    public static record Like(UUID id, UUID whoLikes, UUID whoGotLiked, Direction direction, Instant createdAt) {

        public static enum Direction {
            LIKE,
            PASS
        }

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

        public static Like create(UUID whoLikes, UUID whoGotLiked, Direction direction) {
            return new Like(UUID.randomUUID(), whoLikes, whoGotLiked, direction, AppClock.now());
        }
    }

    /** Represents a block between two users. */
    public static record Block(UUID id, UUID blockerId, UUID blockedId, Instant createdAt) {

        public Block {
            Objects.requireNonNull(id, ID_REQUIRED);
            Objects.requireNonNull(blockerId, "blockerId cannot be null");
            Objects.requireNonNull(blockedId, "blockedId cannot be null");
            Objects.requireNonNull(createdAt, CREATED_AT_REQUIRED);

            if (blockerId.equals(blockedId)) {
                throw new IllegalArgumentException("Cannot block yourself");
            }
        }

        public static Block create(UUID blockerId, UUID blockedId) {
            Objects.requireNonNull(blockerId, "blockerId cannot be null");
            Objects.requireNonNull(blockedId, "blockedId cannot be null");
            return new Block(UUID.randomUUID(), blockerId, blockedId, AppClock.now());
        }
    }

    /** Represents a report filed against a user. */
    public static record Report(
            UUID id, UUID reporterId, UUID reportedUserId, Reason reason, String description, Instant createdAt) {

        public static enum Reason {
            SPAM,
            INAPPROPRIATE_CONTENT,
            HARASSMENT,
            FAKE_PROFILE,
            UNDERAGE,
            OTHER
        }

        public Report {
            Objects.requireNonNull(id, ID_REQUIRED);
            Objects.requireNonNull(reporterId, "reporterId cannot be null");
            Objects.requireNonNull(reportedUserId, "reportedUserId cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
            Objects.requireNonNull(createdAt, CREATED_AT_REQUIRED);

            if (reporterId.equals(reportedUserId)) {
                throw new IllegalArgumentException("Cannot report yourself");
            }
            // Description length validation is handled by TrustSafetyService using
            // AppConfig
        }

        public static Report create(UUID reporterId, UUID reportedUserId, Reason reason, String description) {
            return new Report(UUID.randomUUID(), reporterId, reportedUserId, reason, description, AppClock.now());
        }
    }

    /** Represents a request to transition a match to friendship. */
    public static record FriendRequest(
            UUID id, UUID fromUserId, UUID toUserId, Instant createdAt, Status status, Instant respondedAt) {

        public static enum Status {
            PENDING,
            ACCEPTED,
            DECLINED,
            EXPIRED
        }

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

        public static enum Type {
            MATCH_FOUND,
            NEW_MESSAGE,
            FRIEND_REQUEST,
            FRIEND_REQUEST_ACCEPTED,
            GRACEFUL_EXIT
        }

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

        public static Notification create(
                UUID userId, Type type, String title, String message, Map<String, String> data) {
            return new Notification(UUID.randomUUID(), userId, type, title, message, AppClock.now(), false, data);
        }
    }
}
