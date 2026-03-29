package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.RecommendationService;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiMatchDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiMatchDataAccess;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("DashboardViewModel Logic and Thread-Safety Test")
class DashboardViewModelTest {

    private TestStorages.Users users;
    private TestStorages.Interactions interactions;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Analytics analyticsStorage;
    private TestStorages.Communications communications;
    private RecommendationService dailyService;
    private TestUiThreadDispatcher uiDispatcher;

    private DashboardViewModel viewModel;
    private User currentUser;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @BeforeEach
    void setUp() {
        users = new TestStorages.Users();
        interactions = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        analyticsStorage = new TestStorages.Analytics();
        communications = new TestStorages.Communications();

        TestClock.setFixed(FIXED_INSTANT);

        AppConfig config = AppConfig.defaults();
        CandidateFinder candidateFinder =
                new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
        TestStorages.Standouts standoutStorage = new TestStorages.Standouts();
        ProfileService profileService =
                new ProfileService(config, analyticsStorage, interactions, trustSafetyStorage, users);

        dailyService = RecommendationService.builder()
                .interactionStorage(interactions)
                .userStorage(users)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(standoutStorage)
                .profileService(profileService)
                .config(config)
                .build();

        UiMatchDataAccess matchData = new StorageUiMatchDataAccess(interactions, trustSafetyStorage);

        ConnectionService messagingService = new ConnectionService(config, communications, interactions, users);
        uiDispatcher = new TestUiThreadDispatcher();

        viewModel = new DashboardViewModel(
                new DashboardViewModel.Dependencies(
                        dailyService, matchData, profileService, messagingService, profileService, config),
                AppSession.getInstance(),
                uiDispatcher);

        currentUser = createActiveUser("DashboardUser");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        uiDispatcher.drainAll();
    }

    @AfterEach
    void tearDown() {
        if (viewModel != null) {
            viewModel.dispose();
        }
        TestClock.reset();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("refresh() fetches dashboard data and updates properties on FX thread")
    void shouldRefreshDashboardData() throws InterruptedException {
        performRefreshAndDrainUntilIdle();

        assertEquals(currentUser.getName(), viewModel.userNameProperty().get());
        assertTrue(viewModel.dailyLikesStatusProperty().get().contains("Likes:"));
        assertEquals("0", viewModel.totalMatchesProperty().get());
        assertFalse(viewModel.loadingProperty().get());
        assertEquals(0, viewModel.unreadMessagesProperty().get());
        assertFalse(viewModel.dailyPickAvailableProperty().get());
    }

    @Test
    @DisplayName("refresh exposes daily pick reason, availability, and seen state")
    void shouldExposeDailyPickReasonAvailabilityAndSeenState() throws InterruptedException {
        User candidate = createActiveUser("DailyPickCandidate");
        candidate.setGender(Gender.FEMALE);
        candidate.setInterestedIn(EnumSet.of(Gender.MALE));
        users.save(candidate);

        performRefreshAndDrainUntilIdle();

        assertTrue(viewModel.dailyPickAvailableProperty().get());
        assertEquals(candidate.getId(), viewModel.dailyPickUserIdProperty().get());
        assertTrue(viewModel.dailyPickReasonProperty().get() != null
                && !viewModel.dailyPickReasonProperty().get().isBlank());
        assertFalse(viewModel.dailyPickSeenProperty().get());
        assertEquals("", viewModel.dailyPickEmptyMessageProperty().get());

        dailyService.markDailyPickViewed(currentUser.getId());
        performRefreshAndDrainUntilIdle();

        assertTrue(viewModel.dailyPickSeenProperty().get());
    }

    @Test
    @DisplayName("performRefresh remains re-callable without getting stuck")
    void performRefreshRemainsRecallable() throws InterruptedException {
        performRefreshAndDrainUntilIdle();

        performRefreshAndDrainUntilIdle();
        assertEquals(currentUser.getName(), viewModel.userNameProperty().get());
    }

    @Test
    @DisplayName("concurrent refreshes are safe and last one wins")
    void shouldHandleConcurrentRefreshes() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(20);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int i = 0; i < 20; i++) {
            Thread.ofVirtual().start(() -> {
                ready.countDown();
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start dashboard refresh");
                    }
                    viewModel.refresh();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure.compareAndSet(
                            null, new IllegalStateException("Interrupted while coordinating dashboard refresh", e));
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "Timed out waiting for dashboard refresh workers");
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "Timed out waiting for dashboard refresh workers to complete");
        assertNull(failure.get(), "Concurrent dashboard refresh worker failed: " + failure.get());

        awaitAndDrainUntilLoadingClears();

        assertFalse(viewModel.loadingProperty().get());
        assertEquals(currentUser.getName(), viewModel.userNameProperty().get());
    }

    @Test
    @DisplayName("dispose cancels operations and prevents further updates")
    void shouldPreventUpdatesAfterDispose() throws InterruptedException {
        viewModel.refresh();
        viewModel.dispose();

        awaitAndDrainUntilLoadingClears();

        assertEquals("Not Logged In", viewModel.userNameProperty().get());
        assertFalse(viewModel.loadingProperty().get());
    }

    private void performRefreshAndDrainUntilIdle() throws InterruptedException {
        viewModel.performRefresh();
        awaitAndDrainUntilLoadingClears();
    }

    private void awaitAndDrainUntilLoadingClears() throws InterruptedException {
        for (int attempts = 0; attempts < 10; attempts++) {
            uiDispatcher.drainAll();
            if (!viewModel.loadingProperty().get()) {
                return;
            }
            int observedQueueCount = uiDispatcher.queuedActionCount();
            assertTrue(uiDispatcher.awaitQueuedActionCountAtLeast(observedQueueCount + 1, 5000));
        }
        uiDispatcher.drainAll();
    }

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(25));
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
        user.setAgeRange(18, 60, 18, 120);
        user.setMaxDistanceKm(50, 500);
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setBio("Dashboard bio");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        if (user.getState() != UserState.ACTIVE) {
            throw new IllegalStateException("User should be active for test");
        }
        return user;
    }

    private static final class TestUiThreadDispatcher implements UiThreadDispatcher {
        private final ConcurrentLinkedQueue<Runnable> queuedActions = new ConcurrentLinkedQueue<>();
        private final AtomicInteger queuedActionCount = new AtomicInteger();
        private final Object monitor = new Object();
        private final ThreadLocal<Boolean> uiThread = ThreadLocal.withInitial(() -> false);

        @Override
        public boolean isUiThread() {
            return uiThread.get();
        }

        @Override
        public void dispatch(Runnable action) {
            queuedActions.add(action);
            synchronized (monitor) {
                queuedActionCount.incrementAndGet();
                monitor.notifyAll();
            }
        }

        int queuedActionCount() {
            return queuedActionCount.get();
        }

        boolean awaitQueuedActionCountAtLeast(int expectedCount, long timeoutMillis) throws InterruptedException {
            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            synchronized (monitor) {
                while (queuedActionCount.get() < expectedCount) {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0) {
                        return false;
                    }
                    long waitMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
                    int waitNanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(waitMillis));
                    monitor.wait(waitMillis, waitNanos);
                }
                return true;
            }
        }

        void drainAll() {
            Runnable action;
            while ((action = queuedActions.poll()) != null) {
                boolean previous = uiThread.get();
                uiThread.set(true);
                try {
                    action.run();
                } finally {
                    uiThread.set(previous);
                }
            }
        }
    }
}
