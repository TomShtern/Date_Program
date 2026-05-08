package datingapp.core.testutil;

import datingapp.app.event.AppEventBus;
import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.testutil.TestEventBus;
import datingapp.app.usecase.auth.AuthTokenService;
import datingapp.app.usecase.auth.AuthUseCases;
import datingapp.app.usecase.profile.ProfileInsightsUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.BrowseRankingService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.CompatibilityCalculator;
import datingapp.core.matching.DailyLimitService;
import datingapp.core.matching.DailyPickService;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.OperationalCommunicationStorage;
import datingapp.core.storage.OperationalInteractionStorage;
import datingapp.core.storage.OperationalUserStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.location.LocationService;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

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

    /** Starts building a shared test {@link ServiceRegistry} for the supplied storages. */
    public static Builder builder(
            OperationalUserStorage userStorage,
            OperationalInteractionStorage interactionStorage,
            OperationalCommunicationStorage communicationStorage) {
        return new Builder(userStorage, interactionStorage, communicationStorage);
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
        TestStorages.Standouts standouts = new TestStorages.Standouts();
        TestStorages.Undos undos = new TestStorages.Undos();

        ServiceRegistry registry = buildRegistry(
                users,
                interactions,
                communications,
                AppConfig.defaults(),
                analytics,
                trustSafety,
                standouts,
                undos,
                BrowseRankingService.identity(),
                ZoneOffset.UTC);

        return new RegistryWithStorages(registry, users, interactions, communications, trustSafety, analytics);
    }

    private static ServiceRegistry buildRegistry(
            OperationalUserStorage userStorage,
            OperationalInteractionStorage interactionStorage,
            OperationalCommunicationStorage communicationStorage,
            AppConfig config,
            AnalyticsStorage analyticsStorage,
            TrustSafetyStorage trustSafetyStorage,
            Standout.Storage standoutStorage,
            Undo.Storage undoStorage,
            BrowseRankingService browseRankingService,
            ZoneId candidateFinderZone) {
        AppConfig resolvedConfig = Objects.requireNonNull(config, "config cannot be null");
        OperationalUserStorage resolvedOperationalUserStorage =
                Objects.requireNonNull(userStorage, "userStorage cannot be null");
        UserStorage resolvedUserStorage = resolvedOperationalUserStorage;
        OperationalInteractionStorage resolvedOperationalInteractionStorage =
                Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        InteractionStorage resolvedInteractionStorage = resolvedOperationalInteractionStorage;
        OperationalCommunicationStorage resolvedOperationalCommunicationStorage =
                Objects.requireNonNull(communicationStorage, "communicationStorage cannot be null");
        CommunicationStorage resolvedCommunicationStorage = resolvedOperationalCommunicationStorage;
        AnalyticsStorage resolvedAnalyticsStorage =
                Objects.requireNonNull(analyticsStorage, "analyticsStorage cannot be null");
        TrustSafetyStorage resolvedTrustSafetyStorage =
                Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
        Standout.Storage resolvedStandoutStorage =
                Objects.requireNonNull(standoutStorage, "standoutStorage cannot be null");
        Undo.Storage resolvedUndoStorage = Objects.requireNonNull(undoStorage, "undoStorage cannot be null");
        ZoneId resolvedCandidateFinderZone =
                Objects.requireNonNull(candidateFinderZone, "candidateFinderZone cannot be null");

        AppEventBus eventBus = new InProcessAppEventBus();
        ValidationService validationService = new ValidationService(resolvedConfig);

        CandidateFinder candidateFinder = new CandidateFinder(
                resolvedOperationalUserStorage,
                resolvedOperationalInteractionStorage,
                resolvedTrustSafetyStorage,
                resolvedCandidateFinderZone);
        ActivityMetricsService activityMetricsService = new ActivityMetricsService(
                resolvedOperationalInteractionStorage,
                resolvedTrustSafetyStorage,
                resolvedAnalyticsStorage,
                resolvedConfig);
        ProfileService profileService = new ProfileService(resolvedUserStorage);
        CompatibilityCalculator compatibilityCalculator = new CompatibilityCalculator(resolvedConfig);
        DailyLimitService dailyLimitService =
                new DailyLimitService(resolvedOperationalInteractionStorage, resolvedConfig);
        DailyPickService dailyPickService =
                new DailyPickService(resolvedAnalyticsStorage, candidateFinder, resolvedConfig);
        StandoutService standoutService = new StandoutService(
                compatibilityCalculator,
                resolvedOperationalUserStorage,
                candidateFinder,
                resolvedStandoutStorage,
                profileService,
                resolvedConfig);
        BrowseRankingService resolvedBrowseRankingService = browseRankingService != null
                ? browseRankingService
                : new BrowseRankingService(compatibilityCalculator, profileService, resolvedConfig);
        RecommendationService recommendationService = new RecommendationService(
                dailyLimitService, dailyPickService, standoutService, resolvedBrowseRankingService);
        UndoService undoService =
                new UndoService(resolvedOperationalInteractionStorage, resolvedUndoStorage, resolvedConfig);
        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(resolvedOperationalInteractionStorage)
                .trustSafetyStorage(resolvedTrustSafetyStorage)
                .userStorage(resolvedOperationalUserStorage)
                .activityMetricsService(activityMetricsService)
                .dailyService(recommendationService)
                .undoService(undoService)
                .candidateFinder(candidateFinder)
                .build();
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        resolvedTrustSafetyStorage,
                        resolvedOperationalInteractionStorage,
                        resolvedOperationalUserStorage,
                        resolvedConfig,
                        resolvedOperationalCommunicationStorage)
                .build();
        ConnectionService connectionService = new ConnectionService(
                resolvedConfig,
                resolvedOperationalCommunicationStorage,
                resolvedOperationalInteractionStorage,
                resolvedOperationalUserStorage);
        MatchQualityService matchQualityService = new MatchQualityService(
                resolvedOperationalUserStorage,
                resolvedOperationalInteractionStorage,
                resolvedConfig,
                compatibilityCalculator);
        LocationService locationService = new LocationService(validationService);
        AchievementService achievementService = new AchievementService(
                resolvedConfig,
                resolvedAnalyticsStorage,
                resolvedOperationalInteractionStorage,
                resolvedTrustSafetyStorage,
                resolvedOperationalUserStorage,
                profileService);
        TestStorages.Auth authStorage = new TestStorages.Auth();
        AuthTokenService authTokenService = new AuthTokenService(resolvedConfig.auth());
        AuthUseCases authUseCases =
                new AuthUseCases(resolvedConfig, resolvedUserStorage, authStorage, authTokenService);

        return ServiceRegistry.builder()
                .config(resolvedConfig)
                .userStorage(resolvedUserStorage)
                .interactionStorage(resolvedInteractionStorage)
                .communicationStorage(resolvedCommunicationStorage)
                .analyticsStorage(resolvedAnalyticsStorage)
                .trustSafetyStorage(resolvedTrustSafetyStorage)
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
                .authUseCases(authUseCases)
                .build();
    }

    /** Builder for a shared test {@link ServiceRegistry} graph. */
    public static final class Builder {
        private final OperationalUserStorage userStorage;
        private final OperationalInteractionStorage interactionStorage;
        private final OperationalCommunicationStorage communicationStorage;
        private AppConfig config = AppConfig.defaults();
        private AnalyticsStorage analyticsStorage = new TestStorages.Analytics();
        private TrustSafetyStorage trustSafetyStorage = new TestStorages.TrustSafety();
        private Standout.Storage standoutStorage = new TestStorages.Standouts();
        private Undo.Storage undoStorage = new TestStorages.Undos();
        private BrowseRankingService browseRankingService;

        private Builder(
                OperationalUserStorage userStorage,
                OperationalInteractionStorage interactionStorage,
                OperationalCommunicationStorage communicationStorage) {
            this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
            this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
            this.communicationStorage =
                    Objects.requireNonNull(communicationStorage, "communicationStorage cannot be null");
        }

        public Builder config(AppConfig config) {
            this.config = Objects.requireNonNull(config, "config cannot be null");
            return this;
        }

        public Builder analyticsStorage(AnalyticsStorage analyticsStorage) {
            this.analyticsStorage = Objects.requireNonNull(analyticsStorage, "analyticsStorage cannot be null");
            return this;
        }

        public Builder trustSafetyStorage(TrustSafetyStorage trustSafetyStorage) {
            this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
            return this;
        }

        public Builder standoutStorage(Standout.Storage standoutStorage) {
            this.standoutStorage = Objects.requireNonNull(standoutStorage, "standoutStorage cannot be null");
            return this;
        }

        public Builder undoStorage(Undo.Storage undoStorage) {
            this.undoStorage = Objects.requireNonNull(undoStorage, "undoStorage cannot be null");
            return this;
        }

        public Builder browseRankingService(BrowseRankingService browseRankingService) {
            this.browseRankingService =
                    Objects.requireNonNull(browseRankingService, "browseRankingService cannot be null");
            return this;
        }

        public ServiceRegistry build() {
            return buildRegistry(
                    userStorage,
                    interactionStorage,
                    communicationStorage,
                    config,
                    analyticsStorage,
                    trustSafetyStorage,
                    standoutStorage,
                    undoStorage,
                    browseRankingService,
                    config.safety().userTimeZone());
        }
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
