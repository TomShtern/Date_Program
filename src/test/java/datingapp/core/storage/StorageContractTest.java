package datingapp.core.storage;

import static org.junit.jupiter.api.Assertions.assertThrows;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Storage contract defaults")
class StorageContractTest {

    @Test
    @DisplayName("UserStorage purgeDeletedBefore fails fast when unsupported")
    void userStoragePurgeDeletedBeforeFailsFast() {
        UserStorage storage = new TestStorages.Users();

        assertThrows(UnsupportedOperationException.class, () -> storage.purgeDeletedBefore(Instant.EPOCH));
    }

    @Test
    @DisplayName("InteractionStorage matched counterpart lookup fails fast when unsupported")
    void interactionStorageMatchedCounterpartLookupFailsFast() {
        InteractionStorage storage = new UnsupportedInteractionStorage();
        UUID userId = UUID.randomUUID();

        assertThrows(UnsupportedOperationException.class, () -> storage.getMatchedCounterpartIds(userId));
    }

    @Test
    @DisplayName("InteractionStorage purgeDeletedBefore fails fast when unsupported")
    void interactionStoragePurgeDeletedBeforeFailsFast() {
        InteractionStorage storage = new UnsupportedInteractionStorage();

        assertThrows(UnsupportedOperationException.class, () -> storage.purgeDeletedBefore(Instant.EPOCH));
    }

    @Test
    @DisplayName("CommunicationStorage pending-request count fails fast when unsupported")
    void communicationStoragePendingRequestCountFailsFast() {
        CommunicationStorage storage = new UnsupportedCommunicationStorage();
        UUID userId = UUID.randomUUID();

        assertThrows(UnsupportedOperationException.class, () -> storage.countPendingFriendRequestsForUser(userId));
    }

    private static final class UnsupportedInteractionStorage implements InteractionStorage {
        @Override
        public Optional<datingapp.core.connection.ConnectionModels.Like> getLike(UUID fromUserId, UUID toUserId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void save(datingapp.core.connection.ConnectionModels.Like like) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countByDirection(UUID userId, datingapp.core.connection.ConnectionModels.Like.Direction direction) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countReceivedByDirection(
                UUID userId, datingapp.core.connection.ConnectionModels.Like.Direction direction) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countMutualLikes(UUID userId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countLikesToday(UUID userId, Instant startOfDay) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countSuperLikesToday(UUID userId, Instant startOfDay) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countPassesToday(UUID userId, Instant startOfDay) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void delete(UUID likeId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void save(Match match) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void update(Match match) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Match> get(String matchId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public boolean exists(String matchId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Set<UUID> getMatchedCounterpartIds(UUID userId) {
            return InteractionStorage.super.getMatchedCounterpartIds(userId);
        }

        @Override
        public Optional<Match> getByUsers(UUID userA, UUID userB) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void delete(String matchId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countMatchesFor(UUID userId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countActiveMatchesFor(UUID userId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public PageData<Match> getPageOfMatchesFor(UUID userId, int offset, int limit) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public PageData<Match> getPageOfActiveMatchesFor(UUID userId, int offset, int limit) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int purgeDeletedBefore(Instant threshold) {
            return InteractionStorage.super.purgeDeletedBefore(threshold);
        }

        @Override
        public boolean supportsAtomicRelationshipTransitions() {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public boolean acceptFriendZoneTransition(
                Match updatedMatch, FriendRequest acceptedRequest, Notification notification) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public boolean gracefulExitTransition(
                Match updatedMatch, Optional<Conversation> archivedConversation, Notification notification) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public boolean unmatchTransition(Match updatedMatch, Optional<Conversation> archivedConversation) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public boolean atomicUndoDelete(UUID likeId, String matchId) {
            throw new UnsupportedOperationException("stub");
        }
    }

    private static final class UnsupportedCommunicationStorage implements CommunicationStorage {
        @Override
        public void saveConversation(Conversation conversation) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Conversation> getConversation(String conversationId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Conversation> getConversationByUsers(UUID userA, UUID userB) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Conversation> getConversationsFor(UUID userId, int limit, int offset) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Conversation> getAllConversationsFor(UUID userId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void updateConversationLastMessageAt(String conversationId, Instant timestamp) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void archiveConversation(String conversationId, UUID userId, MatchArchiveReason reason) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void setConversationVisibility(String conversationId, UUID userId, boolean visible) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteConversation(String conversationId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void saveMessage(Message message) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Message> getMessages(String conversationId, int limit, int offset) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Message> getMessage(UUID messageId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Message> getLatestMessage(String conversationId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countMessages(String conversationId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Map<String, Integer> countMessagesByConversationIds(Set<String> conversationIds) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countMessagesAfter(String conversationId, Instant after) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countMessagesNotFromSender(String conversationId, UUID senderId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteMessage(UUID messageId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteMessagesByConversation(String conversationId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void saveFriendRequest(FriendRequest request) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void saveFriendRequestWithNotification(FriendRequest request, Notification notification) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void updateFriendRequest(FriendRequest request) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<FriendRequest> getFriendRequest(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<FriendRequest> getPendingFriendRequestBetween(UUID user1, UUID user2) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<FriendRequest> getPendingFriendRequestsForUser(UUID userId) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public int countPendingFriendRequestsForUser(UUID userId) {
            return CommunicationStorage.super.countPendingFriendRequestsForUser(userId);
        }

        @Override
        public void deleteFriendRequest(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void saveNotification(Notification notification) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void markNotificationAsRead(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public Optional<Notification> getNotification(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteNotification(UUID id) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public void deleteOldNotifications(Instant before) {
            throw new UnsupportedOperationException("stub");
        }
    }
}
