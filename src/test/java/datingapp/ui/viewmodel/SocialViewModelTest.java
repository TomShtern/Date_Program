package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.social.SocialUseCases;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import datingapp.ui.async.UiAsyncTestSupport;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
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
    private TestStorages.Communications communications;
    private TestStorages.TrustSafety trustSafety;

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
        communications = new TestStorages.Communications();
        trustSafety = new TestStorages.TrustSafety();

        TestClock.setFixed(FIXED_INSTANT);

        AppConfig config = AppConfig.defaults();
        ConnectionService connectionService = new ConnectionService(config, communications, interactions, users);
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafety, interactions, users, config, communications)
                .build();
        var socialUseCases = SocialUseCases.forWorkflowAccess(connectionService, trustSafetyService, communications);

        viewModel = new SocialViewModel(
                connectionService,
                new UiDataAdapters.StorageUiSocialDataAccess(communications),
                new UiDataAdapters.StorageUiUserStore(users),
                socialUseCases,
                AppSession.getInstance(),
                new UiAsyncTestSupport.TestUiThreadDispatcher());

        currentUser = TestUserFactory.createActiveUser("Mia");
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
        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

        assertEquals(1, viewModel.getNotifications().size());
        assertEquals("New Match!", viewModel.getNotifications().get(0).title());
        assertFalse(viewModel.getNotifications().get(0).isRead());
        assertFalse(viewModel.loadingProperty().get());
    }

    @Test
    @DisplayName("refresh() resolves sender names for pending friend requests")
    void shouldLoadFriendRequestsWithResolvedSenderNames() throws InterruptedException {
        User sender = TestUserFactory.createActiveUser("Leo");
        users.save(sender);

        FriendRequest request = FriendRequest.create(sender.getId(), currentUser.getId());
        communications.saveFriendRequest(request);

        viewModel.initialize();
        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

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
        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

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
        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

        assertFalse(viewModel.getNotifications().get(0).isRead(), "Notification should start as unread");

        viewModel.markNotificationRead(notif);
        assertTrue(waitUntil(
                () -> !viewModel.loadingProperty().get()
                        && !viewModel.getNotifications().isEmpty()
                        && viewModel.getNotifications().get(0).isRead(),
                5000));

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
        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

        // Calling markNotificationRead on an already-read notification should be a
        // no-op
        int notifCount = viewModel.getNotifications().size();
        viewModel.markNotificationRead(alreadyRead);
        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

        // List count should be unchanged and ViewModel should remain stable
        assertEquals(notifCount, viewModel.getNotifications().size());
        assertFalse(viewModel.loadingProperty().get());
    }

    @Test
    @DisplayName("refresh() with no social data results in empty lists")
    void shouldReturnEmptyListsWhenNoData() throws InterruptedException {
        viewModel.initialize();
        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

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
        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

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
        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

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

    private boolean waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            waitForFxEvents();
            if (condition.getAsBoolean()) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        waitForFxEvents();
        return condition.getAsBoolean();
    }
}
