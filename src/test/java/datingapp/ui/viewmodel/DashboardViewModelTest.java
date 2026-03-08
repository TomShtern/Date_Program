package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiMatchDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiMatchDataAccess;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
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

    private DashboardViewModel viewModel;
    private User currentUser;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException _) {
            // Toolkit already initialized
        }
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

        RecommendationService dailyService = RecommendationService.builder()
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

        viewModel = new DashboardViewModel(
                new DashboardViewModel.Dependencies(
                        dailyService, matchData, profileService, messagingService, profileService, config),
                AppSession.getInstance());

        currentUser = createActiveUser("DashboardUser");
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

    private void waitForFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout waiting for FX thread");
        }
    }

    private boolean waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadlineNanos) {
            waitForFxEvents();
            if (condition.getAsBoolean()) {
                return true;
            }
        }
        waitForFxEvents();
        return condition.getAsBoolean();
    }

    @Test
    @DisplayName("refresh() fetches dashboard data and updates properties on FX thread")
    void shouldRefreshDashboardData() throws InterruptedException {
        viewModel.refresh();

        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

        assertEquals(currentUser.getName(), viewModel.userNameProperty().get());
        assertTrue(viewModel.dailyLikesStatusProperty().get().contains("Likes:"));
        assertEquals("0", viewModel.totalMatchesProperty().get());
        assertFalse(viewModel.loadingProperty().get());
        assertEquals(0, viewModel.unreadMessagesProperty().get());
    }

    @Test
    @DisplayName("concurrent refreshes are safe and last one wins")
    void shouldHandleConcurrentRefreshes() throws InterruptedException {
        // Trigger multiple refreshes simultaneously
        for (int i = 0; i < 20; i++) {
            Thread.ofVirtual().start(() -> viewModel.refresh());
        }

        assertTrue(
                waitUntil(
                        () -> !viewModel.loadingProperty().get()
                                && currentUser
                                        .getName()
                                        .equals(viewModel.userNameProperty().get()),
                        5000),
                "Dashboard refresh did not converge to expected latest state in time");

        // Even with multiple refreshes, the UI state shouldn't be broken or stuck
        // loading forever
        assertFalse(viewModel.loadingProperty().get());
        assertEquals(currentUser.getName(), viewModel.userNameProperty().get());
    }

    @Test
    @DisplayName("dispose cancels operations and prevents further updates")
    void shouldPreventUpdatesAfterDispose() throws InterruptedException {
        viewModel.refresh();
        viewModel.dispose();

        assertTrue(waitUntil(() -> !viewModel.loadingProperty().get(), 5000));

        // If disposed properly, the user name should be what it initially was,
        // because the FX update is aborted if 'disposed' is true.
        assertEquals("Not Logged In", viewModel.userNameProperty().get());
        assertFalse(viewModel.loadingProperty().get());
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
}
