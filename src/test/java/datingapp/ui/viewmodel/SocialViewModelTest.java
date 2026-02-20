package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiSocialDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import datingapp.ui.viewmodel.UiDataAdapters.UiSocialDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("SocialViewModel notifications, friend requests, and lifecycle tests")
class SocialViewModelTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    private TestStorages.Users users;
    private TestStorages.Interactions interactions;
    private TestStorages.TrustSafety trustSafety;
    private TestStorages.Communications communications;

    private SocialViewModel viewModel;
    private User currentUser;

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException _) {
            // Toolkit already initialized by another test class
        }
    }

    @BeforeEach
    void setUp() {
        users = new TestStorages.Users();
        interactions = new TestStorages.Interactions();
        trustSafety = new TestStorages.TrustSafety();
        communications = new TestStorages.Communications();

        TestClock.setFixed(FIXED_INSTANT);

        AppConfig config = AppConfig.defaults();
        ConnectionService connectionService = new ConnectionService(config, communications, interactions, users);
        UiSocialDataAccess socialDataAccess = new StorageUiSocialDataAccess(communications);
        UiUserStore userStore = new StorageUiUserStore(users);

        viewModel = new SocialViewModel(connectionService, socialDataAccess, userStore, AppSession.getInstance());

        currentUser = buildActiveUser("Mia");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);
    }

    @AfterEach
    void tearDown() {
        if (viewModel != null) {
            viewModel.dispose();
        }
        TestClock.reset();
        AppSession.getInstance().reset();
    }

    // --- Tests ---

    @Test
    @DisplayName("refresh() loads existing notifications for the current user")
    void shouldLoadNotificationsOnRefresh() throws InterruptedException {
        Notification notif = Notification.create(
                currentUser.getId(),
                Notification.Type.MATCH_FOUND,
                "New Match!",
                "You matched with someone exciting.",
                null);
        communications.saveNotification(notif);

        viewModel.initialize();

        Thread.sleep(500);
        waitForFxEvents();

        assertEquals(1, viewModel.getNotifications().size());
        assertEquals("New Match!", viewModel.getNotifications().get(0).title());
        assertFalse(viewModel.getNotifications().get(0).isRead());
        assertFalse(viewModel.loadingProperty().get());
    }

    @Test
    @DisplayName("refresh() resolves sender names for pending friend requests")
    void shouldLoadFriendRequestsWithResolvedSenderNames() throws InterruptedException {
        User sender = buildActiveUser("Leo");
        users.save(sender);

        FriendRequest request = FriendRequest.create(sender.getId(), currentUser.getId());
        communications.saveFriendRequest(request);

        viewModel.initialize();

        Thread.sleep(500);
        waitForFxEvents();

        assertEquals(1, viewModel.getPendingRequests().size());

        SocialViewModel.FriendRequestEntry entry =
                viewModel.getPendingRequests().get(0);
        assertEquals("Leo", entry.fromUserName());
        assertEquals(request.id(), entry.requestId());
    }

    @Test
    @DisplayName("friend request falls back to UUID string when sender user record is missing")
    void shouldFallBackToUuidForUnresolvableSender() throws InterruptedException {
        UUID unknownSenderId = UUID.randomUUID(); // deliberately NOT saved in users storage
        FriendRequest request = FriendRequest.create(unknownSenderId, currentUser.getId());
        communications.saveFriendRequest(request);

        viewModel.initialize();

        Thread.sleep(500);
        waitForFxEvents();

        assertEquals(1, viewModel.getPendingRequests().size());
        // Graceful degradation: falls back to the UUID string representation
        String displayName = viewModel.getPendingRequests().get(0).fromUserName();
        assertEquals(unknownSenderId.toString(), displayName);
    }

    @Test
    @DisplayName("markNotificationRead() marks the notification as read and refreshes the list")
    void shouldMarkNotificationAsRead() throws InterruptedException {
        Notification notif = Notification.create(
                currentUser.getId(), Notification.Type.NEW_MESSAGE, "New Message", "Someone sent you a message.", null);
        communications.saveNotification(notif);

        viewModel.initialize();

        Thread.sleep(500);
        waitForFxEvents();

        assertFalse(viewModel.getNotifications().get(0).isRead(), "Notification should start as unread");

        viewModel.markNotificationRead(notif);

        Thread.sleep(500);
        waitForFxEvents();

        assertTrue(viewModel.getNotifications().get(0).isRead(), "Notification should be marked as read");
    }

    @Test
    @DisplayName("markNotificationRead() is a no-op for an already-read notification")
    void shouldIgnoreAlreadyReadNotification() throws InterruptedException {
        // Construct a notification that is already read at creation time
        Notification alreadyRead = new Notification(
                UUID.randomUUID(),
                currentUser.getId(),
                Notification.Type.FRIEND_REQUEST,
                "Friend Request",
                "You have a pending friend request.",
                FIXED_INSTANT,
                true, // isRead = true
                null);
        communications.saveNotification(alreadyRead);

        viewModel.initialize();

        Thread.sleep(500);
        waitForFxEvents();

        // Calling markNotificationRead on an already-read notification should be a no-op
        int notifCount = viewModel.getNotifications().size();
        viewModel.markNotificationRead(alreadyRead);

        Thread.sleep(300);
        waitForFxEvents();

        // List count should be unchanged and ViewModel should remain stable
        assertEquals(notifCount, viewModel.getNotifications().size());
        assertFalse(viewModel.loadingProperty().get());
    }

    @Test
    @DisplayName("refresh() with no social data results in empty lists")
    void shouldReturnEmptyListsWhenNoData() throws InterruptedException {
        viewModel.initialize();

        Thread.sleep(500);
        waitForFxEvents();

        assertTrue(viewModel.getNotifications().isEmpty());
        assertTrue(viewModel.getPendingRequests().isEmpty());
        assertFalse(viewModel.loadingProperty().get());
    }

    @Test
    @DisplayName("dispose() clears both lists and prevents further updates")
    void shouldClearListsOnDispose() throws InterruptedException {
        Notification notif = Notification.create(
                currentUser.getId(), Notification.Type.MATCH_FOUND, "Congrats", "You have a new match.", null);
        communications.saveNotification(notif);

        viewModel.initialize();
        viewModel.dispose(); // Dispose before the virtual thread completes

        Thread.sleep(500);
        waitForFxEvents();

        assertTrue(viewModel.getNotifications().isEmpty());
        assertTrue(viewModel.getPendingRequests().isEmpty());
        assertFalse(viewModel.loadingProperty().get());
    }

    @Test
    @DisplayName("multiple rapid refresh() calls are safe and leave the ViewModel stable")
    void shouldHandleConcurrentRefreshes() throws InterruptedException {
        // Fire many concurrent refreshes to verify no race conditions in list updates
        for (int i = 0; i < 15; i++) {
            Thread.ofVirtual().start(() -> viewModel.refresh());
        }

        Thread.sleep(1000);
        waitForFxEvents();

        // Lists should be consistent (not stuck loading or partially updated)
        assertFalse(viewModel.loadingProperty().get());
    }

    // --- Helpers ---

    private void waitForFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout waiting for FX thread");
        }
    }

    private static User buildActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(25));
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
        user.setAgeRange(18, 60);
        user.setMaxDistanceKm(50);
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setBio("Social test bio");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        if (user.getState() != UserState.ACTIVE) {
            throw new IllegalStateException("User must be ACTIVE for test");
        }
        return user;
    }
}
