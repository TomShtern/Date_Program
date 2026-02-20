package datingapp.core.storage;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.model.Match.MatchArchiveReason;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consolidated storage for messaging and social operations. Merges the former
 * {@code
 * MessagingStorage} and {@code SocialStorage} interfaces.
 */
public interface CommunicationStorage {

    // ═══ Conversation Operations ═══

    void saveConversation(Conversation conversation);

    Optional<Conversation> getConversation(String conversationId);

    Optional<Conversation> getConversationByUsers(UUID userA, UUID userB);

    List<Conversation> getConversationsFor(UUID userId, int limit, int offset);

    List<Conversation> getAllConversationsFor(UUID userId);

    void updateConversationLastMessageAt(String conversationId, Instant timestamp);

    void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp);

    void archiveConversation(String conversationId, UUID userId, MatchArchiveReason reason);

    void setConversationVisibility(String conversationId, UUID userId, boolean visible);

    void deleteConversation(String conversationId);

    // ═══ Message Operations ═══

    void saveMessage(Message message);

    List<Message> getMessages(String conversationId, int limit, int offset);

    Optional<Message> getLatestMessage(String conversationId);

    int countMessages(String conversationId);

    int countMessagesAfter(String conversationId, Instant after);

    int countMessagesNotFromSender(String conversationId, UUID senderId);

    int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId);

    void deleteMessagesByConversation(String conversationId);

    // ═══ Friend Request Operations ═══

    void saveFriendRequest(FriendRequest request);

    /**
     * Persists a friend request and its paired notification as one logical write.
     *
     * <p>
     * Default implementation is sequential for backward compatibility.
     * Implementations with
     * transactional capabilities should override to guarantee atomic behavior.
     */
    default void saveFriendRequestWithNotification(FriendRequest request, Notification notification) {
        saveFriendRequest(request);
        saveNotification(notification);
    }

    void updateFriendRequest(FriendRequest request);

    Optional<FriendRequest> getFriendRequest(UUID id);

    Optional<FriendRequest> getPendingFriendRequestBetween(UUID user1, UUID user2);

    List<FriendRequest> getPendingFriendRequestsForUser(UUID userId);

    void deleteFriendRequest(UUID id);

    // ═══ Notification Operations ═══

    void saveNotification(Notification notification);

    void markNotificationAsRead(UUID id);

    List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly);

    Optional<Notification> getNotification(UUID id);

    void deleteNotification(UUID id);

    void deleteOldNotifications(Instant before);
}
