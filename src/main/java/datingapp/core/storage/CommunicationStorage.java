package datingapp.core.storage;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.model.Match.MatchArchiveReason;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Soft-deletes a conversation by setting its {@code deleted_at} timestamp.
     *
     * <p>
     * The conversation record remains in the database but is logically hidden from
     * queries. After soft deletion, the conversation will not appear in
     * {@link #getConversation}, {@link #getConversationsFor}, or
     * {@link #getAllConversationsFor} results. Update and visibility operations on
     * soft-deleted conversations are prevented (WHERE clauses include
     * {@code deleted_at IS NULL}).
     *
     * <p>
     * Calling this method on an already soft-deleted conversation is a no-op
     * (protected by {@code AND deleted_at IS NULL} in the WHERE clause).
     *
     * @param conversationId the conversation to soft-delete
     */
    void deleteConversation(String conversationId);

    // ═══ Message Operations ═══

    void saveMessage(Message message);

    List<Message> getMessages(String conversationId, int limit, int offset);

    Optional<Message> getMessage(UUID messageId);

    Optional<Message> getLatestMessage(String conversationId);

    int countMessages(String conversationId);

    /**
     * Returns message counts for each requested conversation ID.
     *
     * <p>
     * Default implementation loops over {@link #countMessages(String)} for
     * backward compatibility. Database-backed implementations should override this
     * with a grouped query.
     */
    default Map<String, Integer> countMessagesByConversationIds(Set<String> conversationIds) {
        Objects.requireNonNull(conversationIds, "conversationIds cannot be null");
        if (conversationIds.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> counts = new HashMap<>();
        for (String conversationId : conversationIds) {
            if (conversationId == null || conversationId.isBlank()) {
                continue;
            }
            counts.put(conversationId, countMessages(conversationId));
        }
        return Map.copyOf(counts);
    }

    int countMessagesAfter(String conversationId, Instant after);

    int countMessagesNotFromSender(String conversationId, UUID senderId);

    int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId);

    void deleteMessage(UUID messageId);

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

    /**
     * Returns the number of pending friend requests directed at {@code userId}.
     *
     * <p>
     * Default implementation counts from the full list — override with a
     * {@code SELECT COUNT(*)}
     * for better performance.
     */
    default int countPendingFriendRequestsForUser(UUID userId) {
        return getPendingFriendRequestsForUser(userId).size();
    }

    void deleteFriendRequest(UUID id);

    // ═══ Notification Operations ═══

    void saveNotification(Notification notification);

    void markNotificationAsRead(UUID id);

    List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly);

    Optional<Notification> getNotification(UUID id);

    void deleteNotification(UUID id);

    void deleteOldNotifications(Instant before);
}
