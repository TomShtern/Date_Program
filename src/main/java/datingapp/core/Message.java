package datingapp.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single message within a conversation. Immutable after creation.
 *
 * <p>
 * Messages are validated on construction: content cannot be empty or exceed
 * 1000 characters.
 */
public record Message(UUID id, String conversationId, UUID senderId, String content, Instant createdAt) {

    public static final int MAX_LENGTH = 1000;

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
     * @param senderId       The user sending the message
     * @param content        The message content
     * @return A new Message instance
     */
    public static Message create(String conversationId, UUID senderId, String content) {
        return new Message(UUID.randomUUID(), conversationId, senderId, content, Instant.now());
    }
}
