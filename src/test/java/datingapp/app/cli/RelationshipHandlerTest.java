package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.app.cli.matching.MatchingHandler;
import datingapp.app.cli.shared.CliSupport.InputReader;
import datingapp.core.*;
import datingapp.core.connection.*;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.matching.*;
import datingapp.core.metrics.*;
import datingapp.core.model.*;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.*;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.recommendation.*;
import datingapp.core.safety.*;
import datingapp.core.testutil.TestStorages;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Unit tests for relationship CLI commands: viewPendingRequests(), viewNotifications().
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class RelationshipHandlerTest {

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Analytics analyticsStorage;
    private TestStorages.Communications communicationStorage;
    private AppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        analyticsStorage = new TestStorages.Analytics();
        communicationStorage = new TestStorages.Communications();

        session = AppSession.getInstance();
        session.reset();

        testUser = createActiveUser("TestUser");
        userStorage.save(testUser);
        session.setCurrentUser(testUser);
    }

    private MatchingHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        AppConfig config = AppConfig.defaults();
        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .build();
        TrustSafetyService trustSafetyService =
                new TrustSafetyService(trustSafetyStorage, interactionStorage, userStorage, config);
        ConnectionService transitionService = new ConnectionService(interactionStorage, communicationStorage);
        CandidateFinder candidateFinder =
                new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, config);
        MatchQualityService matchQualityService = new MatchQualityService(userStorage, interactionStorage, config);
        ProfileService profileCompletionService = new ProfileService(config);
        MatchingHandler.Dependencies deps = new MatchingHandler.Dependencies(
                candidateFinder,
                matchingService,
                interactionStorage,
                new RecommendationService(interactionStorage, config),
                new UndoService(interactionStorage, new TestStorages.Undos(), config),
                matchQualityService,
                userStorage,
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage),
                analyticsStorage,
                trustSafetyService,
                transitionService,
                new RecommendationService(
                        userStorage, new TestStorages.Standouts(), candidateFinder, profileCompletionService, config),
                communicationStorage,
                session,
                inputReader);
        return new MatchingHandler(deps);
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("View Pending Requests")
    class ViewPendingRequests {

        @Test
        @DisplayName("Shows message when not logged in")
        void showsMessageWhenNotLoggedIn() {
            session.logout();
            MatchingHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }

        @Test
        @DisplayName("Shows message when no pending requests")
        void showsMessageWhenNoPendingRequests() {
            MatchingHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }

        @Test
        @DisplayName("Lists pending friend requests")
        void listsPendingFriendRequests() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            // Create friend request TO testUser
            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            communicationStorage.saveFriendRequest(request);

            MatchingHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }

        @Test
        @DisplayName("Accepts friend request")
        void acceptsFriendRequest() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            // Create match first (required for friend zone)
            Match match = Match.create(testUser.getId(), otherUser.getId());
            interactionStorage.save(match);

            // Create friend request
            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            communicationStorage.saveFriendRequest(request);

            // Select request 1, accept
            MatchingHandler handler = createHandler("1\na\n");
            handler.viewPendingRequests();

            // Request should be accepted
            Optional<FriendRequest> updated = communicationStorage.getFriendRequest(request.id());
            assertTrue(updated.isPresent());
            assertEquals(FriendRequest.Status.ACCEPTED, updated.get().status());
        }

        @Test
        @DisplayName("Declines friend request")
        void declinesFriendRequest() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            interactionStorage.save(match);

            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            communicationStorage.saveFriendRequest(request);

            // Select request 1, decline
            MatchingHandler handler = createHandler("1\nd\n");
            handler.viewPendingRequests();

            Optional<FriendRequest> updated = communicationStorage.getFriendRequest(request.id());
            assertTrue(updated.isPresent());
            assertEquals(FriendRequest.Status.DECLINED, updated.get().status());
        }

        @Test
        @DisplayName("Handles back selection")
        void handlesBackSelection() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            communicationStorage.saveFriendRequest(request);

            MatchingHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }

        @Test
        @DisplayName("Handles invalid selection")
        void handlesInvalidSelection() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            interactionStorage.save(match);

            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            communicationStorage.saveFriendRequest(request);

            // Invalid selection
            MatchingHandler handler = createHandler("99\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("View Notifications")
    class ViewNotifications {

        @Test
        @DisplayName("Shows message when not logged in")
        void showsMessageWhenNotLoggedIn() {
            session.logout();
            MatchingHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::viewNotifications);
        }

        @Test
        @DisplayName("Shows message when no notifications")
        void showsMessageWhenNoNotifications() {
            MatchingHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::viewNotifications);
        }

        @Test
        @DisplayName("Lists notifications")
        void listsNotifications() {
            Notification notification = Notification.create(
                    testUser.getId(), Notification.Type.MATCH_FOUND, "New Match!", "You have a new match!", Map.of());
            communicationStorage.saveNotification(notification);

            MatchingHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::viewNotifications);
        }

        @Test
        @DisplayName("Marks notifications as read")
        void marksNotificationsAsRead() {
            Notification notification = Notification.create(
                    testUser.getId(),
                    Notification.Type.NEW_MESSAGE,
                    "New Message",
                    "You received a new message!",
                    Map.of());
            communicationStorage.saveNotification(notification);

            assertFalse(notification.isRead());

            MatchingHandler handler = createHandler("\n");
            handler.viewNotifications();

            // Notification should be marked as read
            Optional<Notification> updated = communicationStorage.getNotification(notification.id());
            assertTrue(updated.isPresent());
            assertTrue(updated.get().isRead());
        }

        @Test
        @DisplayName("Shows multiple notifications")
        void showsMultipleNotifications() {
            Notification n1 = Notification.create(
                    testUser.getId(), Notification.Type.MATCH_FOUND, "Match 1", "You matched!", Map.of());
            Notification n2 = Notification.create(
                    testUser.getId(), Notification.Type.NEW_MESSAGE, "Message", "New message!", Map.of());
            Notification n3 = Notification.create(
                    testUser.getId(),
                    Notification.Type.FRIEND_REQUEST,
                    "Friend Request",
                    "Someone wants to be friends!",
                    Map.of());

            communicationStorage.saveNotification(n1);
            communicationStorage.saveNotification(n2);
            communicationStorage.saveNotification(n3);

            MatchingHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::viewNotifications);
        }
    }

    // === Helper Methods ===

    private User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio for " + name);
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.TEXT_ONLY,
                PacePreferences.DepthPreference.SMALL_TALK));
        user.activate();
        return user;
    }
}
