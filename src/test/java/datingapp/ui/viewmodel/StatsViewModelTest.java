package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.testutil.TestEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.profile.ProfileInsightsUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestActivityMetricsService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.async.UiAsyncTestSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("StatsViewModel behavior")
class StatsViewModelTest {

    private TimeZone originalTimeZone;

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @BeforeEach
    void captureTimeZone() {
        originalTimeZone = TimeZone.getDefault();
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
        AppClock.reset();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("refresh loads achievements and zero-state metrics")
    void refreshLoadsAchievementsAndZeroStateMetrics() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createActiveUser("Stats User");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        ProfileUseCases profileUseCases =
                createProfileUseCases(users, config, new SingleAchievementService(currentUser.getId()));

        StatsViewModel viewModel = new StatsViewModel(
                new ActivityMetricsService(interactions, trustSafety, analytics, config),
                new ConnectionService(config, communications, interactions, users),
                profileUseCases,
                AppSession.getInstance(),
                AppClock.clock(),
                new UiAsyncTestSupport.TestUiThreadDispatcher());

        viewModel.initialize();

        assertTrue(JavaFxTestSupport.waitUntil(() -> viewModel.getAchievements().size() == 1, 5000));
        assertEquals(Achievement.FIRST_SPARK, viewModel.getAchievements().get(0));
        assertEquals(0, viewModel.totalLikesGivenProperty().get());
        assertEquals(0, viewModel.totalLikesReceivedProperty().get());
        assertEquals(0, viewModel.totalMatchesProperty().get());
        assertEquals("--", viewModel.responseRateProperty().get());
        assertFalse(viewModel.loadFailedProperty().get());

        viewModel.dispose();
    }

    @Test
    @DisplayName("achievement load failures are surfaced instead of collapsing to empty state")
    void achievementLoadFailuresAreSurfaced() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createActiveUser("Stats User");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        ValidationService validationSvc = new ValidationService(config);
        SingleAchievementService achievementSvc = new SingleAchievementService(currentUser.getId());
        ActivityMetricsService activityMetricsService =
                new ActivityMetricsService(interactions, trustSafety, analytics, config);

        ProfileUseCases failingProfileUseCases =
                new ProfileUseCases(
                        users,
                        new ProfileService(users),
                        validationSvc,
                        new ProfileMutationUseCases(
                                users,
                                validationSvc,
                                achievementSvc,
                                config,
                                new datingapp.core.workflow.ProfileActivationPolicy(),
                                new TestEventBus()),
                        new ProfileNotesUseCases(users, validationSvc, config, new TestEventBus()),
                        new ProfileInsightsUseCases(achievementSvc, activityMetricsService)) {
                    @Override
                    public UseCaseResult<ProfileInsightsUseCases.AchievementSnapshot> getAchievements(
                            ProfileInsightsUseCases.AchievementsQuery query) {
                        return UseCaseResult.failure(UseCaseError.internal("achievement load failed"));
                    }
                };

        StatsViewModel viewModel = new StatsViewModel(
                activityMetricsService,
                new ConnectionService(config, communications, interactions, users),
                failingProfileUseCases,
                AppSession.getInstance(),
                AppClock.clock(),
                new UiAsyncTestSupport.TestUiThreadDispatcher());

        viewModel.initialize();

        assertTrue(
                JavaFxTestSupport.waitUntil(() -> viewModel.loadFailedProperty().get(), 5000));
        assertFalse(viewModel.loadFailureMessageProperty().get().isBlank());
        assertEquals(0, viewModel.getAchievements().size());

        viewModel.dispose();
    }

    @Test
    @DisplayName("total achievement count matches enum length")
    void totalAchievementCountMatchesEnumLength() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createActiveUser("Stats User");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        ProfileUseCases profileUseCases = createProfileUseCases(users, config);

        StatsViewModel viewModel = new StatsViewModel(
                new ActivityMetricsService(interactions, trustSafety, analytics, config),
                new ConnectionService(config, communications, interactions, users),
                profileUseCases,
                AppSession.getInstance(),
                AppClock.clock(),
                new UiAsyncTestSupport.TestUiThreadDispatcher());
        try {
            assertEquals(Achievement.values().length, viewModel.getTotalAchievementCount());
        } finally {
            viewModel.dispose();
        }
    }

    @Test
    @DisplayName("login streak uses UTC clock semantics instead of host default timezone")
    void loginStreakUsesUtcClockSemantics() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Pacific/Kiritimati")));
        AppClock.setClock(Clock.fixed(Instant.parse("2026-03-25T00:30:00Z"), ZoneOffset.UTC));

        User currentUser = createActiveUser("Stats User");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        analytics.saveSession(new datingapp.core.metrics.SwipeState.Session(
                UUID.randomUUID(),
                currentUser.getId(),
                Instant.parse("2026-03-24T23:30:00Z"),
                Instant.parse("2026-03-24T23:30:00Z"),
                null,
                datingapp.core.metrics.SwipeState.Session.MatchState.ACTIVE,
                0,
                0,
                0,
                0));
        analytics.saveSession(new datingapp.core.metrics.SwipeState.Session(
                UUID.randomUUID(),
                currentUser.getId(),
                Instant.parse("2026-03-25T00:30:00Z"),
                Instant.parse("2026-03-25T00:30:00Z"),
                null,
                datingapp.core.metrics.SwipeState.Session.MatchState.ACTIVE,
                0,
                0,
                0,
                0));

        ProfileUseCases profileUseCases = createProfileUseCases(users, config);

        StatsViewModel viewModel = new StatsViewModel(
                new ActivityMetricsService(interactions, trustSafety, analytics, config),
                new ConnectionService(config, communications, interactions, users),
                profileUseCases,
                AppSession.getInstance(),
                AppClock.clock(),
                new UiAsyncTestSupport.TestUiThreadDispatcher());

        viewModel.initialize();

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> viewModel.loginStreakProperty().get() == 2, 5000));

        viewModel.dispose();
    }

    private static User createActiveUser(String name) {
        return TestUserFactory.createActiveUser(name);
    }

    private static ProfileUseCases createProfileUseCases(TestStorages.Users users, AppConfig config) {
        return createProfileUseCases(users, config, TestAchievementService.empty());
    }

    private static ProfileUseCases createProfileUseCases(
            TestStorages.Users users, AppConfig config, AchievementService achievementService) {
        ValidationService validationService = new ValidationService(config);
        return new ProfileUseCases(
                users,
                new ProfileService(users),
                validationService,
                new ProfileMutationUseCases(
                        users,
                        validationService,
                        achievementService,
                        config,
                        new datingapp.core.workflow.ProfileActivationPolicy(),
                        new TestEventBus()),
                new ProfileNotesUseCases(users, validationService, config, new TestEventBus()),
                new ProfileInsightsUseCases(
                        achievementService,
                        TestActivityMetricsService.withStorages(
                                new TestStorages.Interactions(),
                                new TestStorages.TrustSafety(),
                                new TestStorages.Analytics(),
                                config)));
    }

    private static final class SingleAchievementService implements AchievementService {
        private final UUID userId;

        private SingleAchievementService(UUID userId) {
            this.userId = userId;
        }

        @Override
        public List<UserAchievement> checkAndUnlock(UUID ignoredUserId) {
            return getUnlocked(ignoredUserId);
        }

        @Override
        public List<UserAchievement> getUnlocked(UUID ignoredUserId) {
            return List.of(UserAchievement.create(userId, Achievement.FIRST_SPARK));
        }

        @Override
        public List<AchievementProgress> getProgress(UUID ignoredUserId) {
            return List.of();
        }

        @Override
        public Map<Achievement.Category, List<AchievementProgress>> getProgressByCategory(UUID ignoredUserId) {
            return Map.of();
        }

        @Override
        public int countUnlocked(UUID ignoredUserId) {
            return 1;
        }
    }
}
