package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.*;
import datingapp.core.model.Messaging.Conversation;
import datingapp.core.model.Messaging.Message;
import datingapp.core.model.UserInteractions.FriendRequest;
import datingapp.core.model.UserInteractions.Notification;
import datingapp.core.service.*;
import datingapp.core.service.RelationshipTransitionService.TransitionValidationException;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.MessagingStorage;
import datingapp.core.storage.SocialStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class RelationshipTransitionServiceTest {

    private InMemoryMatchStorage matchStorage;
    private InMemorySocialStorage socialStorage;
    private InMemoryMessagingStorage messagingStorage;
    private RelationshipTransitionService service;

    private final UUID aliceId = UUID.randomUUID();
    private final UUID bobId = UUID.randomUUID();

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        matchStorage = new InMemoryMatchStorage();
        socialStorage = new InMemorySocialStorage();
        messagingStorage = new InMemoryMessagingStorage();
        service = new RelationshipTransitionService(matchStorage, socialStorage, messagingStorage);

        // Create a match between Alice and Bob
        Match match = Match.create(aliceId, bobId);
        matchStorage.save(match);
    }

    @Test
    @DisplayName("Request friend zone creates pending request")
    void requestFriendZoneSuccess() {
        FriendRequest request = service.requestFriendZone(aliceId, bobId);

        assertNotNull(request);
        assertEquals(aliceId, request.fromUserId());
        assertEquals(bobId, request.toUserId());
        assertEquals(FriendRequest.Status.PENDING, request.status());
        assertTrue(socialStorage.getFriendRequest(request.id()).isPresent());
    }

    @Test
    @DisplayName("Cannot request friend zone if no active match")
    void requestFriendZoneNoMatch() {
        UUID charlieId = UUID.randomUUID();
        TransitionValidationException ex =
                assertThrows(TransitionValidationException.class, () -> service.requestFriendZone(aliceId, charlieId));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("Cannot request twice if one is pending")
    void requestFriendZoneDuplicate() {
        service.requestFriendZone(aliceId, bobId);
        TransitionValidationException ex =
                assertThrows(TransitionValidationException.class, () -> service.requestFriendZone(aliceId, bobId));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("Accept friend zone updates match state")
    void acceptFriendZoneSuccess() {
        FriendRequest request = service.requestFriendZone(aliceId, bobId);

        service.acceptFriendZone(request.id(), bobId);

        Match match = matchStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.State.FRIENDS, match.getState());

        FriendRequest updated = socialStorage.getFriendRequest(request.id()).orElseThrow();
        assertEquals(FriendRequest.Status.ACCEPTED, updated.status());
        assertNotNull(updated.respondedAt());
    }

    @Test
    @DisplayName("Only target user can accept")
    void acceptFriendZoneWrongUser() {
        FriendRequest request = service.requestFriendZone(aliceId, bobId);
        UUID requestId = request.id();
        UUID responderId = aliceId;
        TransitionValidationException ex = assertThrows(
                TransitionValidationException.class, () -> service.acceptFriendZone(requestId, responderId));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("Decline friend zone updates request status")
    void declineFriendZoneSuccess() {
        FriendRequest request = service.requestFriendZone(aliceId, bobId);
        service.declineFriendZone(request.id(), bobId);

        FriendRequest updated = socialStorage.getFriendRequest(request.id()).orElseThrow();
        assertEquals(FriendRequest.Status.DECLINED, updated.status());

        // Match should still be ACTIVE
        Match match = matchStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.State.ACTIVE, match.getState());
    }

    @Test
    @DisplayName("Graceful exit updates match and archives conversation")
    void gracefulExitSuccess() {
        // Create a conversation
        Conversation convo = Conversation.create(aliceId, bobId);
        messagingStorage.saveConversation(convo);

        service.gracefulExit(aliceId, bobId);

        Match match = matchStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.State.GRACEFUL_EXIT, match.getState());
        assertEquals(Match.ArchiveReason.GRACEFUL_EXIT, match.getEndReason());
        assertEquals(aliceId, match.getEndedBy());

        // Check conversation archive
        assertTrue(messagingStorage.isArchived(convo.getId()));

        // Check notification
        List<Notification> notifications = socialStorage.getNotificationsForUser(bobId, true);
        assertEquals(1, notifications.size());
        assertEquals(Notification.Type.GRACEFUL_EXIT, notifications.get(0).type());
    }

    @Test
    @DisplayName("Graceful exit works from FRIENDS state")
    void gracefulExitFromFriends() {
        Match match = matchStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        match.transitionToFriends(aliceId);
        matchStorage.update(match);

        service.gracefulExit(bobId, aliceId);

        Match updated = matchStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.State.GRACEFUL_EXIT, updated.getState());
    }

    // --- Mock Storages ---

    private static class InMemoryMatchStorage implements MatchStorage {
        private final Map<String, Match> matches = new HashMap<>();

        @Override
        public void save(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public void update(Match match) {
            matches.put(match.getId(), match);
        }

        @Override
        public Optional<Match> get(String id) {
            return Optional.ofNullable(matches.get(id));
        }

        @Override
        public boolean exists(String id) {
            return matches.containsKey(id);
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            return List.of();
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return List.of();
        }

        @Override
        public void delete(String id) {
            matches.remove(id);
        }
    }

    private static class InMemorySocialStorage implements SocialStorage {
        private final Map<UUID, FriendRequest> requests = new HashMap<>();
        private final Map<UUID, List<Notification>> userNotifications = new HashMap<>();

        // Friend request operations
        @Override
        public void saveFriendRequest(FriendRequest req) {
            requests.put(req.id(), req);
        }

        @Override
        public void updateFriendRequest(FriendRequest req) {
            requests.put(req.id(), req);
        }

        @Override
        public Optional<FriendRequest> getFriendRequest(UUID id) {
            return Optional.ofNullable(requests.get(id));
        }

        @Override
        public Optional<FriendRequest> getPendingFriendRequestBetween(UUID u1, UUID u2) {
            return requests.values().stream()
                    .filter(r -> r.isPending()
                            && ((r.fromUserId().equals(u1) && r.toUserId().equals(u2))
                                    || (r.fromUserId().equals(u2)
                                            && r.toUserId().equals(u1))))
                    .findFirst();
        }

        @Override
        public List<FriendRequest> getPendingFriendRequestsForUser(UUID userId) {
            return requests.values().stream()
                    .filter(r -> r.isPending() && r.toUserId().equals(userId))
                    .toList();
        }

        @Override
        public void deleteFriendRequest(UUID id) {
            requests.remove(id);
        }

        // Notification operations
        @Override
        public void saveNotification(Notification n) {
            userNotifications
                    .computeIfAbsent(n.userId(), k -> new ArrayList<>())
                    .add(n);
        }

        @Override
        public void markNotificationAsRead(UUID id) {
            // No-op: mock stub for testing
        }

        @Override
        public List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly) {
            return userNotifications.getOrDefault(userId, List.of());
        }

        @Override
        public Optional<Notification> getNotification(UUID id) {
            return Optional.empty();
        }

        @Override
        public void deleteNotification(UUID id) {
            // No-op: mock stub for testing
        }

        @Override
        public void deleteOldNotifications(Instant before) {
            // No-op: mock stub for testing
        }
    }

    private static class InMemoryMessagingStorage implements MessagingStorage {
        private final Map<String, Conversation> conversations = new HashMap<>();
        private final Set<String> archived = new HashSet<>();

        // Conversation operations
        @Override
        public void saveConversation(Conversation c) {
            conversations.put(c.getId(), c);
        }

        @Override
        public Optional<Conversation> getConversation(String id) {
            return Optional.ofNullable(conversations.get(id));
        }

        @Override
        public Optional<Conversation> getConversationByUsers(UUID u1, UUID u2) {
            return getConversation(Conversation.generateId(u1, u2));
        }

        @Override
        public List<Conversation> getConversationsFor(UUID u) {
            return List.of();
        }

        @Override
        public void updateConversationLastMessageAt(String id, Instant ts) {
            // No-op: mock stub for testing
        }

        @Override
        public void updateConversationReadTimestamp(String id, UUID u, Instant ts) {
            // No-op: mock stub for testing
        }

        @Override
        public void archiveConversation(String id, Match.ArchiveReason r) {
            archived.add(id);
        }

        @Override
        public void setConversationVisibility(String id, UUID u, boolean v) {
            // No-op: mock stub for testing
        }

        @Override
        public void deleteConversation(String id) {
            conversations.remove(id);
        }

        // Message operations - minimal implementations for tests
        @Override
        public void saveMessage(Message message) {
            // No-op: mock stub for testing
        }

        @Override
        public List<Message> getMessages(String conversationId, int limit, int offset) {
            return List.of();
        }

        @Override
        public Optional<Message> getLatestMessage(String conversationId) {
            return Optional.empty();
        }

        @Override
        public int countMessages(String conversationId) {
            return 0;
        }

        @Override
        public int countMessagesAfter(String conversationId, Instant after) {
            return 0;
        }

        @Override
        public int countMessagesNotFromSender(String conversationId, UUID senderId) {
            return 0;
        }

        @Override
        public int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId) {
            return 0;
        }

        @Override
        public void deleteMessagesByConversation(String conversationId) {
            // No-op: mock stub for testing
        }

        public boolean isArchived(String id) {
            return archived.contains(id);
        }
    }
}
