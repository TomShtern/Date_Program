package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.*;
import datingapp.core.model.Messaging.Conversation;
import datingapp.core.model.UserInteractions.FriendRequest;
import datingapp.core.model.UserInteractions.Notification;
import datingapp.core.service.*;
import datingapp.core.service.RelationshipTransitionService.TransitionValidationException;
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
    private RelationshipTransitionService service;

    private final UUID aliceId = UUID.randomUUID();
    private final UUID bobId = UUID.randomUUID();

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        interactionStorage = new TestStorages.Interactions();
        communicationStorage = new TestStorages.Communications();
        service = new RelationshipTransitionService(interactionStorage, communicationStorage);

        // Create a match between Alice and Bob
        Match match = Match.create(aliceId, bobId);
        interactionStorage.save(match);
    }

    @Test
    @DisplayName("Request friend zone creates pending request")
    void requestFriendZoneSuccess() {
        FriendRequest request = service.requestFriendZone(aliceId, bobId);

        assertNotNull(request);
        assertEquals(aliceId, request.fromUserId());
        assertEquals(bobId, request.toUserId());
        assertEquals(FriendRequest.Status.PENDING, request.status());
        assertTrue(communicationStorage.getFriendRequest(request.id()).isPresent());
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

        Match match = interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.State.FRIENDS, match.getState());

        FriendRequest updated =
                communicationStorage.getFriendRequest(request.id()).orElseThrow();
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

        FriendRequest updated =
                communicationStorage.getFriendRequest(request.id()).orElseThrow();
        assertEquals(FriendRequest.Status.DECLINED, updated.status());

        // Match should still be ACTIVE
        Match match = interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.State.ACTIVE, match.getState());
    }

    @Test
    @DisplayName("Graceful exit updates match and archives conversation")
    void gracefulExitSuccess() {
        // Create a conversation
        Conversation convo = Conversation.create(aliceId, bobId);
        communicationStorage.saveConversation(convo);

        service.gracefulExit(aliceId, bobId);

        Match match = interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.State.GRACEFUL_EXIT, match.getState());
        assertEquals(Match.ArchiveReason.GRACEFUL_EXIT, match.getEndReason());
        assertEquals(aliceId, match.getEndedBy());

        // Check conversation archive
        Conversation archived =
                communicationStorage.getConversation(convo.getId()).orElseThrow();
        assertNotNull(archived.getArchivedAt());
        assertEquals(Match.ArchiveReason.GRACEFUL_EXIT, archived.getArchiveReason());

        // Check notification
        List<Notification> notifications = communicationStorage.getNotificationsForUser(bobId, true);
        assertEquals(1, notifications.size());
        assertEquals(Notification.Type.GRACEFUL_EXIT, notifications.get(0).type());
    }

    @Test
    @DisplayName("Graceful exit works from FRIENDS state")
    void gracefulExitFromFriends() {
        Match match = interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        match.transitionToFriends(aliceId);
        interactionStorage.update(match);

        service.gracefulExit(bobId, aliceId);

        Match updated = interactionStorage.get(Match.generateId(aliceId, bobId)).orElseThrow();
        assertEquals(Match.State.GRACEFUL_EXIT, updated.getState());
    }
}
