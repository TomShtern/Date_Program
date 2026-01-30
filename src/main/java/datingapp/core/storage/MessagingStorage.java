package datingapp.core.storage;

import datingapp.core.Match;
import datingapp.core.Messaging.Conversation;
import datingapp.core.Messaging.Message;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consolidated storage interface for messaging: conversations and messages.
 * Groups related operations that were previously in separate interfaces.
 *
 * <p>
 * This interface combines:
 * <ul>
 * <li>{@code ConversationStorage} - Conversation entity management</li>
 * <li>{@code MessageStorage} - Message entity management within
 * conversations</li>
 * </ul>
 */
public interface MessagingStorage {

    // ═══════════════════════════════════════════════════════════════
    // Conversation Operations (from ConversationStorage)
    // ═══════════════════════════════════════════════════════════════

    /** Saves a new conversation. */
    void saveConversation(Conversation conversation);

    /** Gets a conversation by ID. */
    Optional<Conversation> getConversation(String conversationId);

    /** Gets a conversation by the two user UUIDs (order-independent). */
    Optional<Conversation> getConversationByUsers(UUID userA, UUID userB);

    /**
     * Gets all conversations for a given user, sorted by lastMessageAt descending.
     */
    List<Conversation> getConversationsFor(UUID userId);

    /** Updates the lastMessageAt timestamp for a conversation. */
    void updateConversationLastMessageAt(String conversationId, Instant timestamp);

    /** Updates the read timestamp for a specific user in a conversation. */
    void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp);

    /** Archives the conversation with a reason. */
    void archiveConversation(String conversationId, Match.ArchiveReason reason);

    /** Updates the visibility of the conversation for a specific user. */
    void setConversationVisibility(String conversationId, UUID userId, boolean visible);

    /** Deletes a conversation and all its messages (cascade). */
    void deleteConversation(String conversationId);

    // ═══════════════════════════════════════════════════════════════
    // Message Operations (from MessageStorage)
    // ═══════════════════════════════════════════════════════════════

    /** Saves a new message. */
    void saveMessage(Message message);

    /**
     * Gets messages for a conversation, ordered by createdAt ascending. Uses offset
     * pagination.
     *
     * @param conversationId The conversation to get messages for
     * @param limit          Maximum number of messages to return
     * @param offset         Number of messages to skip
     * @return List of messages, ordered oldest first
     */
    List<Message> getMessages(String conversationId, int limit, int offset);

    /**
     * Gets the latest message in a conversation (for preview).
     *
     * @param conversationId The conversation to get the latest message for
     * @return The latest message, or empty if no messages exist
     */
    Optional<Message> getLatestMessage(String conversationId);

    /**
     * Counts total messages in a conversation.
     *
     * @param conversationId The conversation to count messages for
     * @return The total message count
     */
    int countMessages(String conversationId);

    /**
     * Counts messages sent after a given timestamp. Used for unread count
     * calculation.
     *
     * @param conversationId The conversation to count messages for
     * @param after          Count messages created after this timestamp
     * @return The count of messages after the timestamp
     */
    int countMessagesAfter(String conversationId, Instant after);

    /**
     * Counts messages in a conversation not sent by the specified user.
     *
     * @param conversationId The conversation to count messages for
     * @param senderId       The user whose messages to exclude
     * @return Count of messages from other participants
     */
    int countMessagesNotFromSender(String conversationId, UUID senderId);

    /**
     * Counts messages sent after a timestamp, excluding messages from a specific
     * sender.
     * Used for accurate unread count calculation.
     *
     * @param conversationId  The conversation to count messages for
     * @param after           Count messages created after this timestamp
     * @param excludeSenderId The user whose messages to exclude from the count
     * @return The count of messages after the timestamp not from the excluded
     *         sender
     */
    int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId);

    /**
     * Deletes all messages for a conversation. Called when conversation is deleted.
     *
     * @param conversationId The conversation to delete messages for
     */
    void deleteMessagesByConversation(String conversationId);
}
