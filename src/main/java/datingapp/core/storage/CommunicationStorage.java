package datingapp.core.storage;

import datingapp.core.model.ConnectionModels.Conversation;
import datingapp.core.model.ConnectionModels.FriendRequest;
import datingapp.core.model.ConnectionModels.Message;
import datingapp.core.model.ConnectionModels.Notification;
import datingapp.core.model.Match;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consolidated storage for messaging and social operations. Merges the former {@code
 * MessagingStorage} and {@code SocialStorage} interfaces.
 */
public interface CommunicationStorage {

    // ═══ Conversation Operations ═══

    void saveConversation(Conversation conversation);

    Optional<Conversation> getConversation(String conversationId);

    Optional<Conversation> getConversationByUsers(UUID userA, UUID userB);

    List<Conversation> getConversationsFor(UUID userId);

    void updateConversationLastMessageAt(String conversationId, Instant timestamp);

    void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp);

    void archiveConversation(String conversationId, Match.ArchiveReason reason);

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
