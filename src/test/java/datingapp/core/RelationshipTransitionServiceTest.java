package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Match.MatchStorage;
import datingapp.core.Messaging.Conversation;
import datingapp.core.Messaging.ConversationStorage;
import datingapp.core.RelationshipTransitionService.TransitionValidationException;
import datingapp.core.Social.FriendRequest;
import datingapp.core.Social.FriendRequestStorage;
import datingapp.core.Social.Notification;
import datingapp.core.Social.NotificationStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class RelationshipTransitionServiceTest {

    private InMemoryMatchStorage matchStorage;
    private InMemoryFriendRequestStorage friendRequestStorage;
    private InMemoryConversationStorage conversationStorage;
    private InMemoryNotificationStorage notificationStorage;
    private RelationshipTransitionService service;

    private final UUID aliceId = UUID.randomUUID();
    private final UUID bobId = UUID.randomUUID();

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        matchStorage = new InMemoryMatchStorage();
        friendRequestStorage = new InMemoryFriendRequestStorage();
        conversationStorage = new InMemoryConversationStorage();
        notificationStorage = new InMemoryNotificationStorage();
        service = new RelationshipTransitionService(
                matchStorage, friendRequestStorage, conversationStorage, notificationStorage);

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
        assertTrue(friendRequestStorage.get(request.id()).isPresent());
    }

    @Test
    @DisplayName("Cannot request friend zone if no active match")
    void requestFriendZoneNoMatch() {
        UUID charlieId = UUID.randomUUID();
        assertThrows(TransitionValidationException.class, () -> service.requestFriendZone(aliceId, charlieId));
    }

    @Test
    @DisplayName("Cannot request twice if one is pending")
    void requestFriendZoneDuplicate() {
        service.requestFriendZone(aliceId, bobId);
        assertThrows(TransitionValidationException.class, () -> service.requestFriendZone(aliceId, bobId));
    }

    @Test
    @DisplayName("Accept friend zone updates match state")
    void acceptFriendZoneSuccess() {
        FriendRequest request = service.requestFriendZone(aliceId, bobId);

        service.acceptFriendZone(request.id(), bobId);

        Match match = matchStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.State.FRIENDS, match.getState());

        FriendRequest updated = friendRequestStorage.get(request.id()).orElseThrow();
        assertEquals(FriendRequest.Status.ACCEPTED, updated.status());
        assertNotNull(updated.respondedAt());
    }

    @Test
    @DisplayName("Only target user can accept")
    void acceptFriendZoneWrongUser() {
        FriendRequest request = service.requestFriendZone(aliceId, bobId);
        UUID requestId = request.id();
        UUID responderId = aliceId;
        assertThrows(TransitionValidationException.class, () -> service.acceptFriendZone(requestId, responderId));
    }

    @Test
    @DisplayName("Decline friend zone updates request status")
    void declineFriendZoneSuccess() {
        FriendRequest request = service.requestFriendZone(aliceId, bobId);
        service.declineFriendZone(request.id(), bobId);

        FriendRequest updated = friendRequestStorage.get(request.id()).orElseThrow();
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
        conversationStorage.save(convo);

        service.gracefulExit(aliceId, bobId);

        Match match = matchStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.State.GRACEFUL_EXIT, match.getState());
        assertEquals(Match.ArchiveReason.GRACEFUL_EXIT, match.getEndReason());
        assertEquals(aliceId, match.getEndedBy());

        // Check conversation archive
        assertTrue(conversationStorage.isArchived(convo.getId()));

        // Check notification
        List<Notification> notifications = notificationStorage.getForUser(bobId, true);
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

    private static class InMemoryFriendRequestStorage implements FriendRequestStorage {
        private final Map<UUID, FriendRequest> requests = new HashMap<>();

        @Override
        public void save(FriendRequest req) {
            requests.put(req.id(), req);
        }

        @Override
        public void update(FriendRequest req) {
            requests.put(req.id(), req);
        }

        @Override
        public Optional<FriendRequest> get(UUID id) {
            return Optional.ofNullable(requests.get(id));
        }

        @Override
        public Optional<FriendRequest> getPendingBetween(UUID u1, UUID u2) {
            return requests.values().stream()
                    .filter(r -> r.isPending()
                            && ((r.fromUserId().equals(u1) && r.toUserId().equals(u2))
                                    || (r.fromUserId().equals(u2)
                                            && r.toUserId().equals(u1))))
                    .findFirst();
        }

        @Override
        public List<FriendRequest> getPendingForUser(UUID userId) {
            return requests.values().stream()
                    .filter(r -> r.isPending() && r.toUserId().equals(userId))
                    .toList();
        }

        @Override
        public void delete(UUID id) {
            requests.remove(id);
        }
    }

    private static class InMemoryConversationStorage implements ConversationStorage {
        private final Map<String, Conversation> conversations = new HashMap<>();

        @Override
        public void save(Conversation c) {
            conversations.put(c.getId(), c);
        }

        @Override
        public Optional<Conversation> get(String id) {
            return Optional.ofNullable(conversations.get(id));
        }

        @Override
        public Optional<Conversation> getByUsers(UUID u1, UUID u2) {
            return get(Conversation.generateId(u1, u2));
        }

        @Override
        public List<Conversation> getConversationsFor(UUID u) {
            return List.of();
        }

        @Override
        public void updateLastMessageAt(String id, java.time.Instant ts) {
            // No-op: mock stub for testing
        }

        @Override
        public void updateReadTimestamp(String id, UUID u, java.time.Instant ts) {
            // No-op: mock stub for testing
        }

        @Override
        public void archive(String id, Match.ArchiveReason r) {
            archived.add(id);
        }

        @Override
        public void setVisibility(String id, UUID u, boolean v) {
            // No-op: mock stub for testing
        }

        @Override
        public void delete(String id) {
            // No-op: mock stub for testing
        }

        private final java.util.Set<String> archived = new java.util.HashSet<>();

        public boolean isArchived(String id) {
            return archived.contains(id);
        }
    }

    private static class InMemoryNotificationStorage implements NotificationStorage {
        private final Map<UUID, List<Notification>> userNotifications = new HashMap<>();

        @Override
        public void save(Notification n) {
            userNotifications
                    .computeIfAbsent(n.userId(), k -> new ArrayList<>())
                    .add(n);
        }

        @Override
        public void markAsRead(UUID id) {
            // No-op: mock stub for testing
        }

        @Override
        public List<Notification> getForUser(UUID userId, boolean unreadOnly) {
            return userNotifications.getOrDefault(userId, List.of());
        }

        @Override
        public Optional<Notification> get(UUID id) {
            return Optional.empty();
        }

        @Override
        public void delete(UUID id) {
            // No-op: mock stub for testing
        }

        @Override
        public void deleteOldNotifications(java.time.Instant before) {
            // No-op: mock stub for testing
        }
    }
}
