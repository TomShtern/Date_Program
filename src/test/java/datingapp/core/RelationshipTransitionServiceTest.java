package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.*;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.Match.MatchState;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.testutil.TestStorages;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class RelationshipTransitionServiceTest {

    private InteractionStorage interactionStorage;
    private CommunicationStorage communicationStorage;
    private ConnectionService service;

    private final UUID aliceId = UUID.randomUUID();
    private final UUID bobId = UUID.randomUUID();

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        interactionStorage = new TestStorages.Interactions();
        communicationStorage = new TestStorages.Communications();
        service = new ConnectionService(
                AppConfig.defaults(), communicationStorage, interactionStorage, new TestStorages.Users());

        // Create a match between Alice and Bob
        Match match = Match.create(aliceId, bobId);
        interactionStorage.save(match);
    }

    @Test
    @DisplayName("Request friend zone creates pending request")
    void requestFriendZoneSuccess() {
        ConnectionService.TransitionResult result = service.requestFriendZone(aliceId, bobId);

        assertTrue(result.success());
        ConnectionModels.FriendRequest request = result.friendRequest();
        assertNotNull(request);
        assertEquals(aliceId, request.fromUserId());
        assertEquals(bobId, request.toUserId());
        assertEquals(ConnectionModels.FriendRequest.Status.PENDING, request.status());
        assertTrue(communicationStorage.getFriendRequest(request.id()).isPresent());
    }

    @Test
    @DisplayName("Cannot request friend zone if no active match")
    void requestFriendZoneNoMatch() {
        UUID charlieId = UUID.randomUUID();
        ConnectionService.TransitionResult result = service.requestFriendZone(aliceId, charlieId);
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("Cannot request twice if one is pending")
    void requestFriendZoneDuplicate() {
        service.requestFriendZone(aliceId, bobId);
        ConnectionService.TransitionResult result = service.requestFriendZone(aliceId, bobId);
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("Accept friend zone updates match state")
    void acceptFriendZoneSuccess() {
        ConnectionService.TransitionResult reqResult = service.requestFriendZone(aliceId, bobId);
        assertTrue(reqResult.success());
        ConnectionModels.FriendRequest request = reqResult.friendRequest();

        ConnectionService.TransitionResult acceptResult = service.acceptFriendZone(request.id(), bobId);
        assertTrue(acceptResult.success());

        Match match = interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(MatchState.FRIENDS, match.getState());

        ConnectionModels.FriendRequest updated =
                communicationStorage.getFriendRequest(request.id()).orElseThrow();
        assertEquals(ConnectionModels.FriendRequest.Status.ACCEPTED, updated.status());
        assertNotNull(updated.respondedAt());
    }

    @Test
    @DisplayName("Only target user can accept")
    void acceptFriendZoneWrongUser() {
        ConnectionService.TransitionResult reqResult = service.requestFriendZone(aliceId, bobId);
        assertTrue(reqResult.success());
        UUID requestId = reqResult.friendRequest().id();

        ConnectionService.TransitionResult result = service.acceptFriendZone(requestId, aliceId);
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("Decline friend zone updates request status")
    void declineFriendZoneSuccess() {
        ConnectionService.TransitionResult reqResult = service.requestFriendZone(aliceId, bobId);
        assertTrue(reqResult.success());
        UUID requestId = reqResult.friendRequest().id();

        ConnectionService.TransitionResult declineResult = service.declineFriendZone(requestId, bobId);
        assertTrue(declineResult.success());

        ConnectionModels.FriendRequest updated =
                communicationStorage.getFriendRequest(requestId).orElseThrow();
        assertEquals(ConnectionModels.FriendRequest.Status.DECLINED, updated.status());

        // Match should still be ACTIVE
        Match match = interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(MatchState.ACTIVE, match.getState());
    }

    @Test
    @DisplayName("Graceful exit updates match and archives conversation")
    void gracefulExitSuccess() {
        // Create a conversation
        ConnectionModels.Conversation convo = ConnectionModels.Conversation.create(aliceId, bobId);
        communicationStorage.saveConversation(convo);

        ConnectionService.TransitionResult result = service.gracefulExit(aliceId, bobId);
        assertTrue(result.success());

        Match match = interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(MatchState.GRACEFUL_EXIT, match.getState());
        assertEquals(MatchArchiveReason.GRACEFUL_EXIT, match.getEndReason());
        assertEquals(aliceId, match.getEndedBy());

        // Check conversation archive
        ConnectionModels.Conversation archived =
                communicationStorage.getConversation(convo.getId()).orElseThrow();
        assertNotNull(archived.getArchivedAt());
        assertEquals(MatchArchiveReason.GRACEFUL_EXIT, archived.getArchiveReason());

        // Check notification
        List<ConnectionModels.Notification> notifications = communicationStorage.getNotificationsForUser(bobId, true);
        assertEquals(1, notifications.size());
        assertEquals(
                ConnectionModels.Notification.Type.GRACEFUL_EXIT,
                notifications.get(0).type());
    }

    @Test
    @DisplayName("Graceful exit works from FRIENDS state")
    void gracefulExitFromFriends() {
        Match match = interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        match.transitionToFriends(aliceId);
        interactionStorage.update(match);

        ConnectionService.TransitionResult result = service.gracefulExit(bobId, aliceId);
        assertTrue(result.success());

        Match updated = interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(MatchState.GRACEFUL_EXIT, updated.getState());
    }
}
