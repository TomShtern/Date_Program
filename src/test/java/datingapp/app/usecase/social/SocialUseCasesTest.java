package datingapp.app.usecase.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestAction;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestsQuery;
import datingapp.app.usecase.social.SocialUseCases.MarkNotificationReadCommand;
import datingapp.app.usecase.social.SocialUseCases.NotificationsQuery;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.RespondFriendRequestCommand;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SocialUseCases")
class SocialUseCasesTest {

    private TestStorages.Interactions interactionStorage;
    private TestStorages.Communications communicationStorage;
    private SocialUseCases useCases;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        var config = AppConfig.defaults();
        var userStorage = new TestStorages.Users();
        communicationStorage = new TestStorages.Communications();
        interactionStorage = new TestStorages.Interactions(communicationStorage);
        var trustSafetyStorage = new TestStorages.TrustSafety();

        userA = TestUserFactory.createActiveUser(UUID.randomUUID(), "UserA");
        userB = TestUserFactory.createActiveUser(UUID.randomUUID(), "UserB");
        userB.setGender(User.Gender.FEMALE);
        userB.setInterestedIn(Set.of(User.Gender.MALE));

        userStorage.save(userA);
        userStorage.save(userB);
        interactionStorage.save(Match.create(userA.getId(), userB.getId()));

        var connectionService = new ConnectionService(config, communicationStorage, interactionStorage, userStorage);
        var trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactionStorage, userStorage, config, communicationStorage)
                .build();
        useCases = new SocialUseCases(connectionService, trustSafetyService, communicationStorage);
    }

    @Test
    @DisplayName("pending friend requests can be accepted")
    void pendingFriendRequestsCanBeAccepted() {
        var requestTransition =
                useCases.requestFriendZone(new RelationshipCommand(UserContext.cli(userA.getId()), userB.getId()));
        assertTrue(requestTransition.success());

        var pending = useCases.pendingFriendRequests(new FriendRequestsQuery(UserContext.cli(userB.getId())));
        assertTrue(pending.success());
        assertEquals(1, pending.data().size());

        UUID requestId = pending.data().getFirst().id();
        var accepted = useCases.respondToFriendRequest(
                new RespondFriendRequestCommand(UserContext.cli(userB.getId()), requestId, FriendRequestAction.ACCEPT));

        assertTrue(accepted.success());
        Match updated = interactionStorage
                .get(Match.generateId(userA.getId(), userB.getId()))
                .orElseThrow();
        assertEquals(Match.MatchState.FRIENDS, updated.getState());
    }

    @Test
    @DisplayName("notifications can be marked as read")
    void notificationsCanBeMarkedAsRead() {
        Notification notification = Notification.create(
                userA.getId(), Notification.Type.NEW_MESSAGE, "Message", "Hello", Map.of("conversationId", "conv-1"));
        communicationStorage.saveNotification(notification);

        var unread = useCases.notifications(new NotificationsQuery(UserContext.cli(userA.getId()), true));
        assertTrue(unread.success());
        assertEquals(1, unread.data().size());

        var markRead = useCases.markNotificationRead(
                new MarkNotificationReadCommand(UserContext.cli(userA.getId()), notification.id()));
        assertTrue(markRead.success());

        var unreadAfter = useCases.notifications(new NotificationsQuery(UserContext.cli(userA.getId()), true));
        assertTrue(unreadAfter.success());
        assertTrue(unreadAfter.data().isEmpty());
    }

    @Test
    @DisplayName("block user succeeds")
    void blockUserSucceeds() {
        var result = useCases.blockUser(new RelationshipCommand(UserContext.cli(userA.getId()), userB.getId()));

        assertTrue(result.success());
        assertTrue(result.data().errorMessage() == null
                || result.data().errorMessage().isBlank());
    }
}
