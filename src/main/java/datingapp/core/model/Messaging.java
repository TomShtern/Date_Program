package datingapp.core.model;

import datingapp.core.AppClock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Container for messaging domain models (message + conversation). */
public final class Messaging {

    private static final String CONVERSATION_ID_SEPARATOR = "_";

    private Messaging() {
        // Utility class
    }

    /** Formats a deterministic conversation ID for two users. */
    public static String formatConversationId(UUID a, UUID b) {
        return Conversation.generateId(a, b);
    }

    /**
     * Represents a single message within a conversation. Immutable after creation.
     *
     * <p>Messages are validated on construction: content cannot be empty or exceed 1000
     * characters.
     */
    public record Message(UUID id, String conversationId, UUID senderId, String content, Instant createdAt) {

        public static final int MAX_LENGTH = 1000;

        // Compact constructor - validates parameters
        public Message {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(conversationId, "conversationId cannot be null");
            Objects.requireNonNull(senderId, "senderId cannot be null");
            Objects.requireNonNull(createdAt, "createdAt cannot be null");

            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("Message cannot be empty");
            }
            content = content.trim();
            if (content.length() > MAX_LENGTH) {
                throw new IllegalArgumentException("Message too long (max " + MAX_LENGTH + " characters)");
            }
        }

        /**
         * Creates a new message with auto-generated ID and current timestamp.
         *
         * @param conversationId The conversation this message belongs to
         * @param senderId The user sending the message
         * @param content The message content
         * @return A new Message instance
         */
        public static Message create(String conversationId, UUID senderId, String content) {
            return new Message(UUID.randomUUID(), conversationId, senderId, content, AppClock.now());
        }
    }

    /**
     * Represents a conversation between two matched users. Mutable - timestamps can be updated.
     *
     * <p>The ID is deterministic: sorted concatenation of both user UUIDs. userA is always the
     * lexicographically smaller UUID. This mirrors the Match ID pattern.
     */
    public static class Conversation {

        private final String id;
        private final UUID userA; // Lexicographically smaller
        private final UUID userB; // Lexicographically larger
        private final Instant createdAt;
        private Instant lastMessageAt;
        private Instant userAReadAt;
        private Instant userBReadAt;
        private Instant archivedAt;
        private Match.ArchiveReason archiveReason;
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
                Instant archivedAt,
                Match.ArchiveReason archiveReason,
                boolean visibleToUserA,
                boolean visibleToUserB) {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(userA, "userA cannot be null");
            Objects.requireNonNull(userB, "userB cannot be null");
            Objects.requireNonNull(createdAt, "createdAt cannot be null");

            if (userA.equals(userB)) {
                throw new IllegalArgumentException("Cannot have conversation with yourself");
            }

            // Validate ordering
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
            this.archivedAt = archivedAt;
            this.archiveReason = archiveReason;
            this.visibleToUserA = visibleToUserA;
            this.visibleToUserB = visibleToUserB;
        }

        /**
         * Creates a new Conversation with deterministic ID based on sorted user UUIDs.
         *
         * @param a First user UUID
         * @param b Second user UUID
         * @return A new Conversation with proper ordering and deterministic ID
         */
        public static Conversation create(UUID a, UUID b) {
            Objects.requireNonNull(a, "a cannot be null");
            Objects.requireNonNull(b, "b cannot be null");

            if (a.equals(b)) {
                throw new IllegalArgumentException("Cannot have conversation with yourself");
            }

            String userAString = a.toString();
            String userBString = b.toString();

            UUID userA;
            UUID userB;
            if (userAString.compareTo(userBString) < 0) {
                userA = a;
                userB = b;
            } else {
                userA = b;
                userB = a;
            }

            String id = userA + CONVERSATION_ID_SEPARATOR + userB;
            Instant now = AppClock.now();
            return new Conversation(id, userA, userB, now, null, null, null, null, null, true, true);
        }

        /** Generates the deterministic conversation ID for two user UUIDs. */
        public static String generateId(UUID a, UUID b) {
            String userAString = a.toString();
            String userBString = b.toString();

            if (userAString.compareTo(userBString) < 0) {
                return userAString + CONVERSATION_ID_SEPARATOR + userBString;
            } else {
                return userBString + CONVERSATION_ID_SEPARATOR + userAString;
            }
        }

        /** Checks if this conversation involves the given user. */
        public boolean involves(UUID userId) {
            return userA.equals(userId) || userB.equals(userId);
        }

        /** Gets the other user in this conversation. */
        public UUID getOtherUser(UUID userId) {
            if (userA.equals(userId)) {
                return userB;
            } else if (userB.equals(userId)) {
                return userA;
            }
            throw new IllegalArgumentException("User is not part of this conversation");
        }

        /** Updates the last message timestamp. */
        public void updateLastMessageAt(Instant timestamp) {
            Objects.requireNonNull(timestamp, "timestamp cannot be null");
            this.lastMessageAt = timestamp;
        }

        /**
         * Updates the read timestamp for a specific user.
         *
         * @param userId The user who read the conversation
         * @param timestamp When they last read it
         */
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

        /**
         * Gets the last read timestamp for a specific user.
         *
         * @param userId The user to get the read timestamp for
         * @return The last read timestamp, or null if never read
         */
        public Instant getLastReadAt(UUID userId) {
            if (userA.equals(userId)) {
                return userAReadAt;
            } else if (userB.equals(userId)) {
                return userBReadAt;
            }
            throw new IllegalArgumentException("User is not part of this conversation");
        }

        // Getters
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

        public Instant getArchivedAt() {
            return archivedAt;
        }

        public Match.ArchiveReason getArchiveReason() {
            return archiveReason;
        }

        public boolean isVisibleToUserA() {
            return visibleToUserA;
        }

        public boolean isVisibleToUserB() {
            return visibleToUserB;
        }

        /**
         * Archives the conversation for both users.
         *
         * @param reason The reason for archiving
         */
        public void archive(Match.ArchiveReason reason) {
            this.archivedAt = AppClock.now();
            this.archiveReason = reason;
        }

        /**
         * Sets the visibility for a specific user.
         *
         * @param userId The user to set visibility for
         * @param visible Whether it should be visible
         */
        public void setVisibility(UUID userId, boolean visible) {
            if (userA.equals(userId)) {
                this.visibleToUserA = visible;
            } else if (userB.equals(userId)) {
                this.visibleToUserB = visible;
            } else {
                throw new IllegalArgumentException("User is not part of this conversation");
            }
        }

        /** Checks if the conversation is visible to the given user. */
        public boolean isVisibleTo(UUID userId) {
            if (userA.equals(userId)) {
                return visibleToUserA;
            } else if (userB.equals(userId)) {
                return visibleToUserB;
            }
            return false;
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
}
