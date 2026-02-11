package datingapp.storage.jdbi;

import datingapp.core.model.Match;
import datingapp.core.model.Messaging.Conversation;
import datingapp.core.model.Messaging.Message;
import datingapp.core.model.UserInteractions.FriendRequest;
import datingapp.core.model.UserInteractions.Notification;
import datingapp.core.storage.CommunicationStorage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;

/**
 * JDBI adapter that implements {@link CommunicationStorage} by delegating to the internal
 * {@link JdbiMessagingStorage} and {@link JdbiSocialStorage} SQL Object DAOs.
 */
public final class JdbiCommunicationStorageAdapter implements CommunicationStorage {

    private final JdbiMessagingStorage messagingDao;
    private final JdbiSocialStorage socialDao;

    public JdbiCommunicationStorageAdapter(Jdbi jdbi) {
        Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.messagingDao = jdbi.onDemand(JdbiMessagingStorage.class);
        this.socialDao = jdbi.onDemand(JdbiSocialStorage.class);
    }

    // ═══ Conversation Operations ═══

    @Override
    public void saveConversation(Conversation conversation) {
        messagingDao.saveConversation(conversation);
    }

    @Override
    public Optional<Conversation> getConversation(String conversationId) {
        return messagingDao.getConversation(conversationId);
    }

    @Override
    public Optional<Conversation> getConversationByUsers(UUID userA, UUID userB) {
        return messagingDao.getConversationByUsers(userA, userB);
    }

    @Override
    public List<Conversation> getConversationsFor(UUID userId) {
        return messagingDao.getConversationsFor(userId);
    }

    @Override
    public void updateConversationLastMessageAt(String conversationId, Instant timestamp) {
        messagingDao.updateConversationLastMessageAt(conversationId, timestamp);
    }

    @Override
    public void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp) {
        messagingDao.updateConversationReadTimestamp(conversationId, userId, timestamp);
    }

    @Override
    public void archiveConversation(String conversationId, Match.ArchiveReason reason) {
        messagingDao.archiveConversation(conversationId, reason);
    }

    @Override
    public void setConversationVisibility(String conversationId, UUID userId, boolean visible) {
        messagingDao.setConversationVisibility(conversationId, userId, visible);
    }

    @Override
    public void deleteConversation(String conversationId) {
        messagingDao.deleteConversation(conversationId);
    }

    // ═══ Message Operations ═══

    @Override
    public void saveMessage(Message message) {
        messagingDao.saveMessage(message);
    }

    @Override
    public List<Message> getMessages(String conversationId, int limit, int offset) {
        return messagingDao.getMessages(conversationId, limit, offset);
    }

    @Override
    public Optional<Message> getLatestMessage(String conversationId) {
        return messagingDao.getLatestMessage(conversationId);
    }

    @Override
    public int countMessages(String conversationId) {
        return messagingDao.countMessages(conversationId);
    }

    @Override
    public int countMessagesAfter(String conversationId, Instant after) {
        return messagingDao.countMessagesAfter(conversationId, after);
    }

    @Override
    public int countMessagesNotFromSender(String conversationId, UUID senderId) {
        return messagingDao.countMessagesNotFromSender(conversationId, senderId);
    }

    @Override
    public int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId) {
        return messagingDao.countMessagesAfterNotFrom(conversationId, after, excludeSenderId);
    }

    @Override
    public void deleteMessagesByConversation(String conversationId) {
        messagingDao.deleteMessagesByConversation(conversationId);
    }

    // ═══ Friend Request Operations ═══

    @Override
    public void saveFriendRequest(FriendRequest request) {
        socialDao.saveFriendRequest(request);
    }

    @Override
    public void updateFriendRequest(FriendRequest request) {
        socialDao.updateFriendRequest(request);
    }

    @Override
    public Optional<FriendRequest> getFriendRequest(UUID id) {
        return socialDao.getFriendRequest(id);
    }

    @Override
    public Optional<FriendRequest> getPendingFriendRequestBetween(UUID user1, UUID user2) {
        return socialDao.getPendingFriendRequestBetween(user1, user2);
    }

    @Override
    public List<FriendRequest> getPendingFriendRequestsForUser(UUID userId) {
        return socialDao.getPendingFriendRequestsForUser(userId);
    }

    @Override
    public void deleteFriendRequest(UUID id) {
        socialDao.deleteFriendRequest(id);
    }

    // ═══ Notification Operations ═══

    @Override
    public void saveNotification(Notification notification) {
        socialDao.saveNotification(notification);
    }

    @Override
    public void markNotificationAsRead(UUID id) {
        socialDao.markNotificationAsRead(id);
    }

    @Override
    public List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
        return socialDao.getNotificationsForUser(userId, unreadOnly);
    }

    @Override
    public Optional<Notification> getNotification(UUID id) {
        return socialDao.getNotification(id);
    }

    @Override
    public void deleteNotification(UUID id) {
        socialDao.deleteNotification(id);
    }

    @Override
    public void deleteOldNotifications(Instant before) {
        socialDao.deleteOldNotifications(before);
    }
}
