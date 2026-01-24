package datingapp.core;

import datingapp.core.Messaging.Message;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage interface for Message entities. Defined in core, implemented in storage layer. */
public interface MessageStorage {

    /** Saves a new message. */
    void save(Message message);

    /**
     * Gets messages for a conversation, ordered by createdAt ascending. Uses offset pagination.
     *
     * @param conversationId The conversation to get messages for
     * @param limit Maximum number of messages to return
     * @param offset Number of messages to skip
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
     * Counts messages sent after a given timestamp. Used for unread count calculation.
     *
     * @param conversationId The conversation to count messages for
     * @param after Count messages created after this timestamp
     * @return The count of messages after the timestamp
     */
    int countMessagesAfter(String conversationId, Instant after);

    /**
     * Counts messages in a conversation not sent by the specified user. Used for initial unread count
     * when user has never read the conversation.
     *
     * @param conversationId The conversation to count messages for
     * @param senderId The user whose messages to exclude
     * @return Count of messages from other participants
     */
    int countMessagesNotFromSender(String conversationId, UUID senderId);

    /**
     * Deletes all messages for a conversation. Called when conversation is deleted.
     *
     * @param conversationId The conversation to delete messages for
     */
    void deleteByConversation(String conversationId);
}
