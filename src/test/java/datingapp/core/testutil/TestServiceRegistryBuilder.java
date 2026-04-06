package datingapp.core.testutil;

import datingapp.app.event.AppEventBus;
import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.testutil.TestEventBus;
import datingapp.app.usecase.profile.ProfileInsightsUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.CompatibilityCalculator;
import datingapp.core.matching.DailyLimitService;
import datingapp.core.matching.DailyPickService;
import datingapp.core.matching.DefaultCompatibilityCalculator;
import datingapp.core.matching.DefaultDailyLimitService;
import datingapp.core.matching.DefaultDailyPickService;
import datingapp.core.matching.DefaultStandoutService;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.DefaultAchievementService;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.time.ZoneOffset;

/**
 * Shared test utility for building a fully-wired {@link ServiceRegistry}
 * with in-memory test storages.
 *
 * <p>Usage:
 * <pre>{@code
 * ServiceRegistry registry = TestServiceRegistryBuilder.build();
 * }</pre>
 *
 * <p>For callers that need individual pieces (e.g. the {@link UserStorage}
 * to seed test data), use {@link #buildWithStorages()} to get both the
 * registry and the backing storages.
 */
public final class TestServiceRegistryBuilder {

    private TestServiceRegistryBuilder() {
        // utility class
    }

    // ── Simple one-shot factory ──────────────────────────────────────────

    /** Builds a ready-to-use {@link ServiceRegistry} with default test storages. */
    public static ServiceRegistry build() {
        return buildWithStorages().registry();
    }

    // ── Full result with storage access ──────────────────────────────────

    /**
     * Builds a {@link ServiceRegistry} together with the underlying test storages
     * so tests can seed data or assert on storage state.
     */
    public static RegistryWithStorages buildWithStorages() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();

        AppConfig config = AppConfig.defaults();
        AppEventBus eventBus = new InProcessAppEventBus();
        ValidationService validationService = new ValidationService(config);

        CandidateFinder candidateFinder = new CandidateFinder(users, interactions, trustSafety, ZoneOffset.UTC);
        ActivityMetricsService activityMetricsService =
                new ActivityMetricsService(interactions, trustSafety, analytics, config);
        ProfileService profileService = new ProfileService(users);
        CompatibilityCalculator compatibilityCalculator = new DefaultCompatibilityCalculator(config);
        DailyLimitService dailyLimitService = new DefaultDailyLimitService(interactions, config);
        DailyPickService dailyPickService = new DefaultDailyPickService(analytics, candidateFinder, config);
        StandoutService standoutService = new DefaultStandoutService(
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
        AchievementService achievementService =
                new DefaultAchievementService(config, analytics, interactions, trustSafety, users, profileService);

        ServiceRegistry registry = ServiceRegistry.builder()
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

        return new RegistryWithStorages(registry, users, interactions, communications, trustSafety, analytics);
    }

    // ── ProfileUseCases helper (frequently needed in CLI tests) ─────────

    /**
     * Builds a {@link ProfileUseCases} wired with test-default storages and services.
     *
     * @param userStorage the user storage to use (typically a {@link TestStorages.Users} instance)
     * @return a fully-wired ProfileUseCases ready for CLI handler tests
     */
    public static ProfileUseCases buildProfileUseCases(UserStorage userStorage) {
        AppConfig config = AppConfig.defaults();
        ValidationService validationService = new ValidationService(config);
        ProfileService profileService = new ProfileService(userStorage);
        ProfileMutationUseCases mutationUseCases = new ProfileMutationUseCases(
                userStorage,
                validationService,
                TestAchievementService.empty(),
                config,
                new ProfileActivationPolicy(),
                new TestEventBus());
        ProfileNotesUseCases notesUseCases =
                new ProfileNotesUseCases(userStorage, validationService, config, new TestEventBus());
        ProfileInsightsUseCases insightsUseCases = new ProfileInsightsUseCases(TestAchievementService.empty(), null);
        return new ProfileUseCases(
                userStorage, profileService, validationService, mutationUseCases, notesUseCases, insightsUseCases);
    }

    /**
     * Holds a {@link ServiceRegistry} alongside the test storages that back it,
     * so tests can both use the registry and inspect/seed storage state.
     */
    public static final class RegistryWithStorages {
        private final ServiceRegistry registry;
        private final TestStorages.Users users;
        private final TestStorages.Interactions interactions;
        private final TestStorages.Communications communications;
        private final TestStorages.TrustSafety trustSafety;
        private final TestStorages.Analytics analytics;

        private RegistryWithStorages(
                ServiceRegistry registry,
                TestStorages.Users users,
                TestStorages.Interactions interactions,
                TestStorages.Communications communications,
                TestStorages.TrustSafety trustSafety,
                TestStorages.Analytics analytics) {
            this.registry = registry;
            this.users = users;
            this.interactions = interactions;
            this.communications = communications;
            this.trustSafety = trustSafety;
            this.analytics = analytics;
        }

        public ServiceRegistry registry() {
            return registry;
        }

        public TestStorages.Users users() {
            return users;
        }

        public TestStorages.Interactions interactions() {
            return interactions;
        }

        public TestStorages.Communications communications() {
            return communications;
        }

        public TestStorages.TrustSafety trustSafety() {
            return trustSafety;
        }

        public TestStorages.Analytics analytics() {
            return analytics;
        }
    }
}
