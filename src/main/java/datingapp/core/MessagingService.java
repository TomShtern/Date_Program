package datingapp.core;

import datingapp.core.Match.MatchStorage;
import datingapp.core.Messaging.Conversation;
import datingapp.core.Messaging.ConversationStorage;
import datingapp.core.Messaging.Message;
import datingapp.core.Messaging.MessageStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for messaging between matched users. Handles authorization, message creation, and
 * conversation management.
 */
public class MessagingService {

    private final ConversationStorage conversationStorage;
    private final MessageStorage messageStorage;
    private final MatchStorage matchStorage;
    private final UserStorage userStorage;

    public MessagingService(
            ConversationStorage conversationStorage,
            MessageStorage messageStorage,
            MatchStorage matchStorage,
            UserStorage userStorage) {
        this.conversationStorage = Objects.requireNonNull(conversationStorage, "conversationStorage cannot be null");
        this.messageStorage = Objects.requireNonNull(messageStorage, "messageStorage cannot be null");
        this.matchStorage = Objects.requireNonNull(matchStorage, "matchStorage cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
    }

    /**
     * Sends a message from one user to another.
     *
     * @param senderId The user sending the message
     * @param recipientId The user receiving the message
     * @param content The message content
     * @return SendResult indicating success or failure with details
     */
    public SendResult sendMessage(UUID senderId, UUID recipientId, String content) {
        // Validate sender exists and is active
        User sender = userStorage.get(senderId);
        if (sender == null || sender.getState() != User.State.ACTIVE) {
            return SendResult.failure("Sender not found or inactive", SendResult.ErrorCode.USER_NOT_FOUND);
        }

        // Validate recipient exists and is active
        User recipient = userStorage.get(recipientId);
        if (recipient == null || recipient.getState() != User.State.ACTIVE) {
            return SendResult.failure("Recipient not found or inactive", SendResult.ErrorCode.USER_NOT_FOUND);
        }

        // Verify active match exists
        String matchId = Match.generateId(senderId, recipientId);
        Optional<Match> matchOpt = matchStorage.get(matchId);

        if (matchOpt.isEmpty() || !matchOpt.get().canMessage()) {
            return SendResult.failure("Cannot message: no active match", SendResult.ErrorCode.NO_ACTIVE_MATCH);
        }

        // Validate content
        if (content == null || content.isBlank()) {
            return SendResult.failure("Message cannot be empty", SendResult.ErrorCode.EMPTY_MESSAGE);
        }
        content = content.trim();
        if (content.length() > Message.MAX_LENGTH) {
            return SendResult.failure(
                    "Message too long (max " + Message.MAX_LENGTH + " characters)",
                    SendResult.ErrorCode.MESSAGE_TOO_LONG);
        }

        // Get or create conversation
        String conversationId = Conversation.generateId(senderId, recipientId);
        if (conversationStorage.get(conversationId).isEmpty()) {
            Conversation newConvo = Conversation.create(senderId, recipientId);
            conversationStorage.save(newConvo);
        }

        // Create and save message
        Message message = Message.create(conversationId, senderId, content);
        messageStorage.save(message);

        // Update conversation's last message timestamp
        conversationStorage.updateLastMessageAt(conversationId, message.createdAt());

        // Update sender's lastActiveAt (Phase 3.1 feature)
        userStorage.save(sender);

        return SendResult.success(message);
    }

    /**
     * Gets messages for a conversation with pagination.
     *
     * @param userId The requesting user (must be part of conversation)
     * @param otherUserId The other user in the conversation
     * @param limit Maximum messages to return
     * @param offset Number of messages to skip
     * @return List of messages, ordered oldest first
     */
    public List<Message> getMessages(UUID userId, UUID otherUserId, int limit, int offset) {
        String conversationId = Conversation.generateId(userId, otherUserId);

        // Verify user is part of conversation
        Optional<Conversation> convoOpt = conversationStorage.get(conversationId);
        if (convoOpt.isEmpty() || !convoOpt.get().involves(userId)) {
            return List.of();
        }

        return messageStorage.getMessages(conversationId, limit, offset);
    }

    /**
     * Gets all conversations for a user with preview information.
     *
     * @param userId The user to get conversations for
     * @return List of conversation previews, sorted by most recent activity
     */
    public List<ConversationPreview> getConversations(UUID userId) {
        List<Conversation> conversations = conversationStorage.getConversationsFor(userId);
        List<ConversationPreview> previews = new ArrayList<>();

        for (Conversation convo : conversations) {
            UUID otherUserId = convo.getOtherUser(userId);
            User otherUser = userStorage.get(otherUserId);

            // Skip if other user doesn't exist
            if (otherUser == null) {
                continue;
            }

            Optional<Message> lastMessage = messageStorage.getLatestMessage(convo.getId());
            int unreadCount = getUnreadCount(userId, convo.getId());

            previews.add(new ConversationPreview(convo, otherUser, lastMessage, unreadCount));
        }

        return previews;
    }

    /**
     * Marks a conversation as read for a user.
     *
     * @param userId The user marking as read
     * @param conversationId The conversation to mark read
     */
    public void markAsRead(UUID userId, String conversationId) {
        Optional<Conversation> convoOpt = conversationStorage.get(conversationId);
        if (convoOpt.isEmpty() || !convoOpt.get().involves(userId)) {
            return;
        }

        conversationStorage.updateReadTimestamp(conversationId, userId, Instant.now());
    }

    /**
     * Gets unread message count for a user in a conversation.
     *
     * @param userId The user to count unread for
     * @param conversationId The conversation to count
     * @return Number of unread messages
     */
    public int getUnreadCount(UUID userId, String conversationId) {
        Optional<Conversation> convoOpt = conversationStorage.get(conversationId);
        if (convoOpt.isEmpty()) {
            return 0;
        }

        Conversation convo = convoOpt.get();
        if (!convo.involves(userId)) {
            return 0;
        }

        Instant lastReadAt = convo.getLastReadAt(userId);
        if (lastReadAt == null) {
            // Never read - count all messages not from this user
            return countMessagesNotFromUser(conversationId, userId);
        }

        return messageStorage.countMessagesAfter(conversationId, lastReadAt);
    }

    /**
     * Gets total unread message count across all conversations.
     *
     * @param userId The user to count unread for
     * @return Total unread messages
     */
    public int getTotalUnreadCount(UUID userId) {
        List<Conversation> conversations = conversationStorage.getConversationsFor(userId);
        int total = 0;
        for (Conversation convo : conversations) {
            total += getUnreadCount(userId, convo.getId());
        }
        return total;
    }

    /**
     * Checks if messaging is allowed between two users.
     *
     * @param userA First user
     * @param userB Second user
     * @return true if there's an active match between them
     */
    public boolean canMessage(UUID userA, UUID userB) {
        String matchId = Match.generateId(userA, userB);
        Optional<Match> matchOpt = matchStorage.get(matchId);
        return matchOpt.isPresent() && matchOpt.get().isActive();
    }

    /**
     * Gets or creates a conversation between two users. Does not check for match - caller should
     * verify.
     */
    public Conversation getOrCreateConversation(UUID userA, UUID userB) {
        String conversationId = Conversation.generateId(userA, userB);
        return conversationStorage.get(conversationId).orElseGet(() -> {
            Conversation newConvo = Conversation.create(userA, userB);
            conversationStorage.save(newConvo);
            return newConvo;
        });
    }

    /** Counts messages not sent by the given user (for initial unread count). */
    private int countMessagesNotFromUser(String conversationId, UUID userId) {
        return messageStorage.countMessagesNotFromSender(conversationId, userId);
    }

    // === Data Transfer Objects ===

    /** Result of sending a message. */
    public record SendResult(boolean success, Message message, String errorMessage, ErrorCode errorCode) {

        /** Error codes for message sending failures. */
        public enum ErrorCode {
            NO_ACTIVE_MATCH,
            USER_NOT_FOUND,
            EMPTY_MESSAGE,
            MESSAGE_TOO_LONG
        }

        public static SendResult success(Message message) {
            return new SendResult(true, message, null, null);
        }

        public static SendResult failure(String error, ErrorCode code) {
            return new SendResult(false, null, error, code);
        }
    }

    /** Preview of a conversation for list display. */
    public record ConversationPreview(
            Conversation conversation, User otherUser, Optional<Message> lastMessage, int unreadCount) {}
}
