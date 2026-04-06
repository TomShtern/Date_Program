package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.Match.MatchState;
import datingapp.core.testutil.TestStorages;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
@DisplayName("ConnectionService atomic transition behavior")
class ConnectionServiceTransitionTest {

    @Test
    @DisplayName("accept friend-zone prefers atomic transition support over direct partial writes")
    void acceptFriendZonePrefersAtomicTransitionSupportOverDirectPartialWrites() {
        AtomicInteractionStorage interactions = new AtomicInteractionStorage();
        TrackingCommunications communications = new TrackingCommunications();
        TestStorages.Users users = new TestStorages.Users();

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Match match = Match.create(userA, userB);
        interactions.save(match);

        ConnectionModels.FriendRequest request = ConnectionModels.FriendRequest.create(userA, userB);
        communications.saveFriendRequest(request);

        ConnectionService service = new ConnectionService(AppConfig.defaults(), communications, interactions, users);
        ConnectionService.TransitionResult result = service.acceptFriendZone(request.id(), userB);

        assertTrue(result.success());
        assertTrue(interactions.acceptFriendZoneTransitionCalled);
        assertEquals(0, interactions.directUpdateCalls);
        assertEquals(0, communications.updateFriendRequestCalls);

        Match updated = interactions.get(Match.generateId(userA, userB)).orElseThrow();
        assertEquals(MatchState.FRIENDS, updated.getState());
        assertEquals(
                ConnectionModels.FriendRequest.Status.ACCEPTED,
                result.friendRequest().status());
    }

    @Test
    @DisplayName("graceful exit prefers atomic transition support over direct partial writes")
    void gracefulExitPrefersAtomicTransitionSupportOverDirectPartialWrites() {
        AtomicInteractionStorage interactions = new AtomicInteractionStorage();
        TrackingCommunications communications = new TrackingCommunications();
        TestStorages.Users users = new TestStorages.Users();

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Match match = Match.create(userA, userB);
        match.transitionToFriends(userA);
        interactions.save(match);

        ConnectionModels.Conversation conversation = ConnectionModels.Conversation.create(userA, userB);
        communications.saveConversation(conversation);

        ConnectionService service = new ConnectionService(AppConfig.defaults(), communications, interactions, users);
        ConnectionService.TransitionResult result = service.gracefulExit(userA, userB);

        assertTrue(result.success());
        assertTrue(interactions.gracefulExitTransitionCalled);
        assertEquals(0, interactions.directUpdateCalls);
        assertEquals(0, communications.archiveConversationCalls);

        Match updated = interactions.get(Match.generateId(userA, userB)).orElseThrow();
        assertEquals(MatchState.GRACEFUL_EXIT, updated.getState());
        assertEquals(MatchArchiveReason.GRACEFUL_EXIT, updated.getEndReason());
    }

    @Test
    @DisplayName("unmatch prefers atomic transition support over direct partial writes")
    void unmatchPrefersAtomicTransitionSupportOverDirectPartialWrites() {
        AtomicInteractionStorage interactions = new AtomicInteractionStorage();
        TrackingCommunications communications = new TrackingCommunications();
        TestStorages.Users users = new TestStorages.Users();

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Match match = Match.create(userA, userB);
        interactions.save(match);
        interactions.save(Like.create(userA, userB, Like.Direction.LIKE));
        interactions.save(Like.create(userB, userA, Like.Direction.LIKE));

        ConnectionModels.Conversation conversation = ConnectionModels.Conversation.create(userA, userB);
        communications.saveConversation(conversation);

        ConnectionService service = new ConnectionService(AppConfig.defaults(), communications, interactions, users);
        ConnectionService.TransitionResult result = service.unmatch(userA, userB);

        assertTrue(result.success());
        assertTrue(interactions.unmatchTransitionCalled);
        assertEquals(0, interactions.directUpdateCalls);
        assertEquals(0, communications.archiveConversationCalls);

        Match updated = interactions.get(Match.generateId(userA, userB)).orElseThrow();
        assertEquals(MatchState.UNMATCHED, updated.getState());
        assertEquals(MatchArchiveReason.UNMATCH, updated.getEndReason());
        assertEquals(0, interactions.countByDirection(userA, Like.Direction.LIKE));
        assertEquals(0, interactions.countByDirection(userB, Like.Direction.LIKE));
        assertTrue(interactions.getLike(userA, userB).isEmpty());
        assertTrue(interactions.getLike(userB, userA).isEmpty());

        assertTrue(interactions.archivedConversation.isPresent());
        ConnectionModels.Conversation archived = interactions.archivedConversation.orElseThrow();
        assertNotNull(archived.getUserAArchivedAt());
        assertNotNull(archived.getUserBArchivedAt());
    }

    @Test
    @DisplayName("accept friend-zone failure does not mutate the stored match or request")
    void acceptFriendZoneFailureDoesNotMutateStoredMatchOrRequest() {
        TrackingCommunications communications = new TrackingCommunications();
        FailingAtomicInteractionStorage interactions = new FailingAtomicInteractionStorage(communications);
        TestStorages.Users users = new TestStorages.Users();

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Match match = Match.create(userA, userB);
        interactions.save(match);

        ConnectionModels.FriendRequest request = ConnectionModels.FriendRequest.create(userA, userB);
        communications.saveFriendRequest(request);

        ConnectionService service = new ConnectionService(AppConfig.defaults(), communications, interactions, users);
        ConnectionService.TransitionResult result = service.acceptFriendZone(request.id(), userB);

        assertFalse(result.success());
        Match stored = interactions.get(Match.generateId(userA, userB)).orElseThrow();
        assertEquals(MatchState.ACTIVE, stored.getState());
        assertNull(stored.getEndedAt());
        assertEquals(
                ConnectionModels.FriendRequest.Status.PENDING,
                communications.getFriendRequest(request.id()).orElseThrow().status());
    }

    @Test
    @DisplayName("graceful exit failure does not mutate stored match or conversation")
    void gracefulExitFailureDoesNotMutateStoredMatchOrConversation() {
        TrackingCommunications communications = new TrackingCommunications();
        FailingAtomicInteractionStorage interactions = new FailingAtomicInteractionStorage(communications);
        TestStorages.Users users = new TestStorages.Users();

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Match match = Match.create(userA, userB);
        match.transitionToFriends(userA);
        interactions.save(match);

        ConnectionModels.Conversation conversation = ConnectionModels.Conversation.create(userA, userB);
        communications.saveConversation(conversation);

        ConnectionService service = new ConnectionService(AppConfig.defaults(), communications, interactions, users);
        ConnectionService.TransitionResult result = service.gracefulExit(userA, userB);

        assertFalse(result.success());
        Match stored = interactions.get(Match.generateId(userA, userB)).orElseThrow();
        assertEquals(MatchState.FRIENDS, stored.getState());
        ConnectionModels.Conversation storedConversation =
                communications.getConversation(conversation.getId()).orElseThrow();
        assertNull(storedConversation.getUserAArchivedAt());
        assertNull(storedConversation.getUserBArchivedAt());
    }

    @Test
    @DisplayName("unmatch failure does not mutate stored match, likes, or conversation")
    void unmatchFailureDoesNotMutateStoredMatchLikesOrConversation() {
        TrackingCommunications communications = new TrackingCommunications();
        FailingAtomicInteractionStorage interactions = new FailingAtomicInteractionStorage(communications);
        TestStorages.Users users = new TestStorages.Users();

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Match match = Match.create(userA, userB);
        interactions.save(match);
        interactions.save(Like.create(userA, userB, Like.Direction.LIKE));
        interactions.save(Like.create(userB, userA, Like.Direction.LIKE));

        ConnectionModels.Conversation conversation = ConnectionModels.Conversation.create(userA, userB);
        communications.saveConversation(conversation);

        ConnectionService service = new ConnectionService(AppConfig.defaults(), communications, interactions, users);
        ConnectionService.TransitionResult result = service.unmatch(userA, userB);

        assertFalse(result.success());
        Match stored = interactions.get(Match.generateId(userA, userB)).orElseThrow();
        assertEquals(MatchState.ACTIVE, stored.getState());
        assertTrue(interactions.getLike(userA, userB).isPresent());
        assertTrue(interactions.getLike(userB, userA).isPresent());
        ConnectionModels.Conversation storedConversation =
                communications.getConversation(conversation.getId()).orElseThrow();
        assertNull(storedConversation.getUserAArchivedAt());
        assertNull(storedConversation.getUserBArchivedAt());
    }

    private static final class AtomicInteractionStorage extends TestStorages.Interactions {
        private boolean acceptFriendZoneTransitionCalled;
        private boolean gracefulExitTransitionCalled;
        private boolean unmatchTransitionCalled;
        private int directUpdateCalls;
        private Optional<ConnectionModels.Conversation> archivedConversation = Optional.empty();

        @Override
        public void update(Match match) {
            directUpdateCalls++;
            super.update(match);
        }

        @Override
        public boolean acceptFriendZoneTransition(
                Match updatedMatch,
                ConnectionModels.FriendRequest acceptedRequest,
                ConnectionModels.Notification notification) {
            acceptFriendZoneTransitionCalled = true;
            super.update(updatedMatch);
            return true;
        }

        @Override
        public boolean gracefulExitTransition(
                Match updatedMatch,
                Optional<ConnectionModels.Conversation> archivedConversation,
                ConnectionModels.Notification notification) {
            gracefulExitTransitionCalled = true;
            this.archivedConversation = archivedConversation;
            super.update(updatedMatch);
            return true;
        }

        @Override
        public boolean unmatchTransition(
                Match updatedMatch, Optional<ConnectionModels.Conversation> archivedConversation) {
            this.unmatchTransitionCalled = true;
            this.archivedConversation = archivedConversation;
            super.deletePairLikes(updatedMatch.getUserA(), updatedMatch.getUserB());
            super.update(updatedMatch);
            return true;
        }
    }

    private static final class FailingAtomicInteractionStorage extends TestStorages.Interactions {

        private FailingAtomicInteractionStorage(TestStorages.Communications communications) {
            super(communications);
        }

        @Override
        public boolean acceptFriendZoneTransition(
                Match updatedMatch,
                ConnectionModels.FriendRequest acceptedRequest,
                ConnectionModels.Notification notification) {
            return false;
        }

        @Override
        public boolean gracefulExitTransition(
                Match updatedMatch,
                Optional<ConnectionModels.Conversation> archivedConversation,
                ConnectionModels.Notification notification) {
            return false;
        }

        @Override
        public boolean unmatchTransition(
                Match updatedMatch, Optional<ConnectionModels.Conversation> archivedConversation) {
            return false;
        }
    }

    private static final class TrackingCommunications extends TestStorages.Communications {
        private int archiveConversationCalls;
        private int updateFriendRequestCalls;

        @Override
        public void archiveConversation(String conversationId, UUID userId, MatchArchiveReason reason) {
            archiveConversationCalls++;
            super.archiveConversation(conversationId, userId, reason);
        }

        @Override
        public void updateFriendRequest(ConnectionModels.FriendRequest request) {
            updateFriendRequestCalls++;
            super.updateFriendRequest(request);
        }
    }
}
