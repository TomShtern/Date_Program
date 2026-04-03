package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.CompatibilityCalculator;
import datingapp.core.matching.DefaultCompatibilityCalculator;
import datingapp.core.matching.DefaultDailyLimitService;
import datingapp.core.matching.DefaultDailyPickService;
import datingapp.core.matching.DefaultStandoutService;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.DefaultAchievementService;
import datingapp.core.model.User;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import datingapp.ui.screen.ChatController;
import datingapp.ui.screen.DashboardController;
import datingapp.ui.screen.LoginController;
import datingapp.ui.screen.MatchingController;
import datingapp.ui.screen.ProfileController;
import datingapp.ui.screen.StatsController;
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
@DisplayName("ViewModelFactory")
class ViewModelFactoryTest {

    private ServiceRegistry services;
    private AppSession session;
    private ViewModelFactory factory;

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
        session = AppSession.getInstance();
        session.reset();
        services = buildTestServiceRegistry();
        factory = new ViewModelFactory(services, session);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.dispose();
        }
        session.reset();
    }

    @Test
    @DisplayName("getChatViewModel returns the same cached instance on repeated calls")
    void getChatViewModelReturnsCachedInstance() {
        ChatViewModel first = factory.getChatViewModel();
        ChatViewModel second = factory.getChatViewModel();

        assertNotNull(first);
        assertSame(first, second, "ViewModel should be cached within factory lifetime");
    }

    @Test
    @DisplayName("getMatchingViewModel returns the same cached instance on repeated calls")
    void getMatchingViewModelReturnsCachedInstance() {
        MatchingViewModel first = factory.getMatchingViewModel();
        MatchingViewModel second = factory.getMatchingViewModel();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("getStatsViewModel returns the same cached instance on repeated calls")
    void getStatsViewModelReturnsCachedInstance() {
        StatsViewModel first = factory.getStatsViewModel();
        StatsViewModel second = factory.getStatsViewModel();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("getProfileViewModel returns the same cached instance on repeated calls")
    void getProfileViewModelReturnsCachedInstance() {
        ProfileViewModel first = factory.getProfileViewModel();
        ProfileViewModel second = factory.getProfileViewModel();

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    @DisplayName("reset clears cache and disposes ViewModels; new instances are distinct")
    void resetClearsCacheAndDisposesViewModels() throws InterruptedException {
        ChatViewModel original = factory.getChatViewModel();
        assertNotNull(original);

        factory.reset();
        drainFxEvents();

        assertTrue(original.isDisposed(), "Original ViewModel should be disposed after reset");

        ChatViewModel afterReset = factory.getChatViewModel();
        assertNotNull(afterReset);
        assertNotSame(original, afterReset, "New ViewModel should be a fresh instance after reset");
    }

    @Test
    @DisplayName("dispose clears cache and disposes ViewModels permanently")
    void disposeClearsCacheAndDisposesViewModelsPermanently() throws InterruptedException {
        ChatViewModel vm = factory.getChatViewModel();
        assertNotNull(vm);

        factory.dispose();
        drainFxEvents();

        assertTrue(vm.isDisposed(), "ViewModel should be disposed after factory dispose");
    }

    @Test
    @DisplayName("currentUserProperty synchronizes with AppSession")
    void currentUserPropertySynchronizesWithAppSession() throws InterruptedException {
        User testUser = createMinimalActiveUser("TestUser");

        assertNotNull(factory.currentUserProperty());
        assertNull(factory.currentUserProperty().get());

        session.setCurrentUser(testUser);
        drainFxEvents();

        assertEquals(testUser, factory.currentUserProperty().get());

        session.setCurrentUser(null);
        drainFxEvents();

        assertNull(factory.currentUserProperty().get());
    }

    @Test
    @DisplayName("createController returns the correct controller type for known classes")
    void createControllerReturnsCorrectTypeForKnownClasses() {
        Object chat = factory.createController(ChatController.class);
        Object dashboard = factory.createController(DashboardController.class);
        Object login = factory.createController(LoginController.class);
        Object matching = factory.createController(MatchingController.class);
        Object profile = factory.createController(ProfileController.class);
        Object stats = factory.createController(StatsController.class);

        assertInstanceOf(ChatController.class, chat);
        assertInstanceOf(DashboardController.class, dashboard);
        assertInstanceOf(LoginController.class, login);
        assertInstanceOf(MatchingController.class, matching);
        assertInstanceOf(ProfileController.class, profile);
        assertInstanceOf(StatsController.class, stats);
    }

    @Test
    @DisplayName("createController fails fast for unregistered controller types")
    void createControllerFailsFastForUnregisteredControllerTypes() {
        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> factory.createController(UnregisteredController.class));

        assertTrue(error.getMessage().contains(UnregisteredController.class.getName()));
    }

    @Test
    @DisplayName("getPreferencesStore returns a non-null store")
    void getPreferencesStoreReturnsNonNull() {
        assertNotNull(factory.getPreferencesStore());
    }

    private void drainFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timeout waiting for FX thread");
        }
    }

    private static ServiceRegistry buildTestServiceRegistry() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();

        AppConfig config = AppConfig.defaults();
        var eventBus = new InProcessAppEventBus();
        ValidationService validationService = new ValidationService(config);

        CandidateFinder candidateFinder =
                new CandidateFinder(users, interactions, trustSafety, java.time.ZoneId.of("UTC"));
        ActivityMetricsService activityMetricsService =
                new ActivityMetricsService(interactions, trustSafety, analytics, config);
        ProfileService profileService = new ProfileService(users);
        CompatibilityCalculator compatibilityCalculator = new DefaultCompatibilityCalculator(config);
        datingapp.core.matching.DailyLimitService dailyLimitService =
                new DefaultDailyLimitService(interactions, config);
        datingapp.core.matching.DailyPickService dailyPickService =
                new DefaultDailyPickService(analytics, candidateFinder, config);
        datingapp.core.matching.StandoutService standoutService = new DefaultStandoutService(
                compatibilityCalculator, users, candidateFinder, new TestStorages.Standouts(), profileService, config);
        RecommendationService recommendationService =
                new RecommendationService(dailyLimitService, dailyPickService, standoutService);
        UndoService undoService = new UndoService(interactions, new TestStorages.Undos(), config);
        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(interactions)
                .trustSafetyStorage(trustSafety)
                .userStorage(users)
                .activityMetricsService(activityMetricsService)
                .dailyService(recommendationService)
                .undoService(undoService)
                .candidateFinder(candidateFinder)
                .build();
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafety, interactions, users, config, communications)
                .build();
        ConnectionService connectionService = new ConnectionService(config, communications, interactions, users);
        MatchQualityService matchQualityService =
                new MatchQualityService(users, interactions, config, compatibilityCalculator);
        LocationService locationService = new LocationService(validationService);
        datingapp.core.metrics.AchievementService achievementService =
                new DefaultAchievementService(config, analytics, interactions, trustSafety, users, profileService);

        return ServiceRegistry.builder()
                .config(config)
                .userStorage(users)
                .interactionStorage(interactions)
                .communicationStorage(communications)
                .analyticsStorage(analytics)
                .trustSafetyStorage(trustSafety)
                .candidateFinder(candidateFinder)
                .matchingService(matchingService)
                .trustSafetyService(trustSafetyService)
                .activityMetricsService(activityMetricsService)
                .matchQualityService(matchQualityService)
                .profileService(profileService)
                .recommendationService(recommendationService)
                .dailyLimitService(dailyLimitService)
                .dailyPickService(dailyPickService)
                .standoutService(standoutService)
                .undoService(undoService)
                .achievementService(achievementService)
                .connectionService(connectionService)
                .validationService(validationService)
                .locationService(locationService)
                .eventBus(eventBus)
                .build();
    }

    private static User createMinimalActiveUser(String name) {
        return TestUserFactory.createActiveUser(name);
    }

    private static final class UnregisteredController {}
}
