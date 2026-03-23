package datingapp.app.usecase.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestAction;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestsQuery;
import datingapp.app.usecase.social.SocialUseCases.MarkNotificationReadCommand;
import datingapp.app.usecase.social.SocialUseCases.NotificationsQuery;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.ReportCommand;
import datingapp.app.usecase.social.SocialUseCases.RespondFriendRequestCommand;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SocialUseCases")
class SocialUseCasesTest {

    private TestStorages.Interactions interactionStorage;
    private TestStorages.Communications communicationStorage;
    private ConnectionService connectionService;
    private TrustSafetyService trustSafetyService;
    private SocialUseCases useCases;
    private InProcessAppEventBus eventBus;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        var config = AppConfig.defaults();
        var userStorage = new TestStorages.Users();
        communicationStorage = new TestStorages.Communications();
        interactionStorage = new TestStorages.Interactions(communicationStorage);
        var trustSafetyStorage = new TestStorages.TrustSafety();
        eventBus = new InProcessAppEventBus();

        userA = TestUserFactory.createActiveUser(UUID.randomUUID(), "UserA");
        userB = TestUserFactory.createActiveUser(UUID.randomUUID(), "UserB");
        userB.setGender(User.Gender.FEMALE);
        userB.setInterestedIn(Set.of(User.Gender.MALE));

        userStorage.save(userA);
        userStorage.save(userB);
        interactionStorage.save(Match.create(userA.getId(), userB.getId()));

        connectionService = new ConnectionService(config, communicationStorage, interactionStorage, userStorage);
        trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactionStorage, userStorage, config, communicationStorage)
                .build();
        useCases = createUseCases(eventBus);
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
    @DisplayName("request friend zone publishes the expected relationship transition event")
    void requestFriendZonePublishesExpectedRelationshipTransitionEvent() {
        AtomicReference<AppEvent.RelationshipTransitioned> published = new AtomicReference<>();
        eventBus.subscribe(AppEvent.RelationshipTransitioned.class, published::set);

        var result = useCases.requestFriendZone(new RelationshipCommand(UserContext.cli(userA.getId()), userB.getId()));

        assertTrue(result.success());
        assertRelationshipTransitionEvent(
                published.get(),
                Match.generateId(userA.getId(), userB.getId()),
                userA.getId(),
                userB.getId(),
                "MATCHED",
                "FRIEND_ZONE_REQUESTED");
    }

    @Test
    @DisplayName("unmatch publishes the expected relationship transition event")
    void unmatchPublishesExpectedRelationshipTransitionEvent() {
        AtomicReference<AppEvent.RelationshipTransitioned> published = new AtomicReference<>();
        eventBus.subscribe(AppEvent.RelationshipTransitioned.class, published::set);

        var result = useCases.unmatch(new RelationshipCommand(UserContext.cli(userA.getId()), userB.getId()));

        assertTrue(result.success());
        assertEquals(
                Match.MatchState.UNMATCHED,
                interactionStorage
                        .get(Match.generateId(userA.getId(), userB.getId()))
                        .orElseThrow()
                        .getState());
        assertRelationshipTransitionEvent(
                published.get(),
                Match.generateId(userA.getId(), userB.getId()),
                userA.getId(),
                userB.getId(),
                "MATCHED",
                "UNMATCHED");
    }

    @Test
    @DisplayName("graceful exit publishes the expected relationship transition event")
    void gracefulExitPublishesExpectedRelationshipTransitionEvent() {
        AtomicReference<AppEvent.RelationshipTransitioned> published = new AtomicReference<>();
        eventBus.subscribe(AppEvent.RelationshipTransitioned.class, published::set);

        var result = useCases.gracefulExit(new RelationshipCommand(UserContext.cli(userA.getId()), userB.getId()));

        assertTrue(result.success());
        assertEquals(
                Match.MatchState.GRACEFUL_EXIT,
                interactionStorage
                        .get(Match.generateId(userA.getId(), userB.getId()))
                        .orElseThrow()
                        .getState());
        assertRelationshipTransitionEvent(
                published.get(),
                Match.generateId(userA.getId(), userB.getId()),
                userA.getId(),
                userB.getId(),
                "ACTIVE",
                "GRACEFUL_EXIT");
    }

    @Test
    @DisplayName("publish failures do not break relationship transition success")
    void publishFailuresDoNotBreakRelationshipTransitionSuccess() {
        SocialUseCases failingUseCases = createUseCases(new ThrowingEventBus());

        var result =
                failingUseCases.gracefulExit(new RelationshipCommand(UserContext.cli(userA.getId()), userB.getId()));

        assertTrue(result.success());
        assertEquals(
                Match.MatchState.GRACEFUL_EXIT,
                interactionStorage
                        .get(Match.generateId(userA.getId(), userB.getId()))
                        .orElseThrow()
                        .getState());
    }

    @Test
    @DisplayName("publish failures do not break moderation success")
    void publishFailuresDoNotBreakModerationSuccess() {
        SocialUseCases failingUseCases = createUseCases(new ThrowingEventBus());

        var blockResult =
                failingUseCases.blockUser(new RelationshipCommand(UserContext.cli(userA.getId()), userB.getId()));
        var reportResult = failingUseCases.reportUser(new ReportCommand(
                UserContext.cli(userA.getId()), userB.getId(), Report.Reason.HARASSMENT, "Repeated harassment", true));

        assertTrue(blockResult.success());
        assertTrue(reportResult.success());
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

    @Test
    @DisplayName("block user publishes moderation event")
    void blockUserPublishesModerationEvent() {
        AtomicReference<AppEvent.UserBlocked> published = new AtomicReference<>();
        eventBus.subscribe(AppEvent.UserBlocked.class, published::set);

        var result = useCases.blockUser(new RelationshipCommand(UserContext.cli(userA.getId()), userB.getId()));

        assertTrue(result.success());
        assertEquals(userA.getId(), published.get().blockerId());
        assertEquals(userB.getId(), published.get().blockedUserId());
    }

    @Test
    @DisplayName("report user publishes moderation event")
    void reportUserPublishesModerationEvent() {
        AtomicReference<AppEvent.UserReported> published = new AtomicReference<>();
        eventBus.subscribe(AppEvent.UserReported.class, published::set);

        var result = useCases.reportUser(new ReportCommand(
                UserContext.cli(userA.getId()), userB.getId(), Report.Reason.HARASSMENT, "Repeated harassment", true));

        assertTrue(result.success());
        assertEquals(userA.getId(), published.get().reporterId());
        assertEquals(userB.getId(), published.get().reportedUserId());
        assertEquals(Report.Reason.HARASSMENT.name(), published.get().reason());
        assertTrue(published.get().blockedUser());
    }

    private SocialUseCases createUseCases(AppEventBus appEventBus) {
        return new SocialUseCases(connectionService, trustSafetyService, communicationStorage, appEventBus);
    }

    private static void assertRelationshipTransitionEvent(
            AppEvent.RelationshipTransitioned event,
            String matchId,
            UUID initiatorId,
            UUID targetId,
            String fromState,
            String toState) {
        assertNotNull(event);
        assertEquals(matchId, event.matchId());
        assertEquals(initiatorId, event.initiatorId());
        assertEquals(targetId, event.targetId());
        assertEquals(fromState, event.fromState());
        assertEquals(toState, event.toState());
        assertNotNull(event.occurredAt());
    }

    private static final class ThrowingEventBus implements AppEventBus {

        @Override
        public void publish(AppEvent event) {
            throw new RuntimeException("simulated event publication failure");
        }

        @Override
        public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public <T extends AppEvent> void subscribe(
                Class<T> eventType, AppEventHandler<T> handler, HandlerPolicy policy) {
            throw new UnsupportedOperationException("stub");
        }
    }
}
