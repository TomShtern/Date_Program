package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.app.cli.CliSupport.InputReader;
import datingapp.core.*;
import datingapp.core.model.*;
import datingapp.core.model.Preferences.PacePreferences;
import datingapp.core.model.User.Gender;
import datingapp.core.model.UserInteractions.FriendRequest;
import datingapp.core.model.UserInteractions.Notification;
import datingapp.core.service.*;
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
    private TestStorages.Likes likeStorage;
    private TestStorages.Matches matchStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Stats statsStorage;
    private TestStorages.Social socialStorage;
    private TestStorages.Messaging messagingStorage;
    private AppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        likeStorage = new TestStorages.Likes();
        matchStorage = new TestStorages.Matches();
        trustSafetyStorage = new TestStorages.TrustSafety();
        statsStorage = new TestStorages.Stats();
        socialStorage = new TestStorages.Social();
        messagingStorage = new TestStorages.Messaging();

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
                .likeStorage(likeStorage)
                .matchStorage(matchStorage)
                .userStorage(userStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .build();
        RelationshipTransitionService transitionService =
                new RelationshipTransitionService(matchStorage, socialStorage, messagingStorage);
        CandidateFinder candidateFinder = new CandidateFinder(userStorage, likeStorage, trustSafetyStorage, config);
        MatchQualityService matchQualityService = new MatchQualityService(userStorage, likeStorage, config);
        ProfileCompletionService profileCompletionService = new ProfileCompletionService(config);
        MatchingHandler.Dependencies deps = new MatchingHandler.Dependencies(
                candidateFinder,
                matchingService,
                matchStorage,
                trustSafetyStorage,
                new DailyService(likeStorage, config),
                new UndoService(likeStorage, matchStorage, new TestStorages.Undos(), config),
                matchQualityService,
                userStorage,
                new AchievementService(
                        statsStorage,
                        matchStorage,
                        likeStorage,
                        userStorage,
                        trustSafetyStorage,
                        profileCompletionService,
                        config),
                statsStorage,
                transitionService,
                new StandoutsService(
                        userStorage, new TestStorages.Standouts(), candidateFinder, profileCompletionService, config),
                socialStorage,
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
            socialStorage.saveFriendRequest(request);

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
            matchStorage.save(match);

            // Create friend request
            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            socialStorage.saveFriendRequest(request);

            // Select request 1, accept
            MatchingHandler handler = createHandler("1\na\n");
            handler.viewPendingRequests();

            // Request should be accepted
            Optional<FriendRequest> updated = socialStorage.getFriendRequest(request.id());
            assertTrue(updated.isPresent());
            assertEquals(FriendRequest.Status.ACCEPTED, updated.get().status());
        }

        @Test
        @DisplayName("Declines friend request")
        void declinesFriendRequest() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            matchStorage.save(match);

            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            socialStorage.saveFriendRequest(request);

            // Select request 1, decline
            MatchingHandler handler = createHandler("1\nd\n");
            handler.viewPendingRequests();

            Optional<FriendRequest> updated = socialStorage.getFriendRequest(request.id());
            assertTrue(updated.isPresent());
            assertEquals(FriendRequest.Status.DECLINED, updated.get().status());
        }

        @Test
        @DisplayName("Handles back selection")
        void handlesBackSelection() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            socialStorage.saveFriendRequest(request);

            MatchingHandler handler = createHandler("b\n");

            assertDoesNotThrow(handler::viewPendingRequests);
        }

        @Test
        @DisplayName("Handles invalid selection")
        void handlesInvalidSelection() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            Match match = Match.create(testUser.getId(), otherUser.getId());
            matchStorage.save(match);

            FriendRequest request = FriendRequest.create(otherUser.getId(), testUser.getId());
            socialStorage.saveFriendRequest(request);

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
            socialStorage.saveNotification(notification);

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
            socialStorage.saveNotification(notification);

            assertFalse(notification.isRead());

            MatchingHandler handler = createHandler("\n");
            handler.viewNotifications();

            // Notification should be marked as read
            Optional<Notification> updated = socialStorage.getNotification(notification.id());
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

            socialStorage.saveNotification(n1);
            socialStorage.saveNotification(n2);
            socialStorage.saveNotification(n3);

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
