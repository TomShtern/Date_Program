package datingapp.app.api;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.event.handlers.AchievementEventHandler;
import datingapp.app.event.handlers.MetricsEventHandler;
import datingapp.app.event.handlers.NotificationEventHandler;
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
import datingapp.core.matching.Standout;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.DefaultAchievementService;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared REST/API test fixture builder for wiring ServiceRegistry graphs. */
final class RestApiTestFixture {

    private RestApiTestFixture() {}

    static Builder builder(
            UserStorage userStorage, InteractionStorage interactionStorage, CommunicationStorage communicationStorage) {
        return new Builder(userStorage, interactionStorage, communicationStorage);
    }

    static final class Builder {
        private final UserStorage userStorage;
        private final InteractionStorage interactionStorage;
        private final CommunicationStorage communicationStorage;
        private AppConfig config = AppConfig.defaults();
        private TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();
        private TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        private Standout.Storage standoutStorage = new InMemoryStandoutStorage();
        private Undo.Storage undoStorage = new InMemoryUndoStorage();

        private Builder(
                UserStorage userStorage,
                InteractionStorage interactionStorage,
                CommunicationStorage communicationStorage) {
            this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
            this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
            this.communicationStorage =
                    Objects.requireNonNull(communicationStorage, "communicationStorage cannot be null");
        }

        Builder config(AppConfig config) {
            this.config = Objects.requireNonNull(config, "config cannot be null");
            return this;
        }

        Builder analyticsStorage(TestStorages.Analytics analyticsStorage) {
            this.analyticsStorage = Objects.requireNonNull(analyticsStorage, "analyticsStorage cannot be null");
            return this;
        }

        Builder trustSafetyStorage(TestStorages.TrustSafety trustSafetyStorage) {
            this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
            return this;
        }

        Builder standoutStorage(Standout.Storage standoutStorage) {
            this.standoutStorage = Objects.requireNonNull(standoutStorage, "standoutStorage cannot be null");
            return this;
        }

        Builder undoStorage(Undo.Storage undoStorage) {
            this.undoStorage = Objects.requireNonNull(undoStorage, "undoStorage cannot be null");
            return this;
        }

        ServiceRegistry build() {
            InProcessAppEventBus eventBus = new InProcessAppEventBus();
            CandidateFinder candidateFinder = new CandidateFinder(
                    userStorage,
                    interactionStorage,
                    trustSafetyStorage,
                    config.safety().userTimeZone());
            ActivityMetricsService activityMetricsService =
                    new ActivityMetricsService(interactionStorage, trustSafetyStorage, analyticsStorage, config);
            ProfileService profileService = new ProfileService(userStorage);
            CompatibilityCalculator compatibilityCalculator = new DefaultCompatibilityCalculator(config);
            DailyLimitService dailyLimitService = new DefaultDailyLimitService(interactionStorage, config);
            DailyPickService dailyPickService = new DefaultDailyPickService(analyticsStorage, candidateFinder, config);
            StandoutService standoutService = new DefaultStandoutService(
                    compatibilityCalculator, userStorage, candidateFinder, standoutStorage, profileService, config);
            RecommendationService recommendationService =
                    new RecommendationService(dailyLimitService, dailyPickService, standoutService);
            UndoService undoService = new UndoService(interactionStorage, undoStorage, config);
            MatchingService matchingService = MatchingService.builder()
                    .interactionStorage(interactionStorage)
                    .trustSafetyStorage(trustSafetyStorage)
                    .userStorage(userStorage)
                    .activityMetricsService(activityMetricsService)
                    .dailyService(recommendationService)
                    .undoService(undoService)
                    .candidateFinder(candidateFinder)
                    .build();
            TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                            trustSafetyStorage, interactionStorage, userStorage, config, communicationStorage)
                    .build();
            ConnectionService connectionService =
                    new ConnectionService(config, communicationStorage, interactionStorage, userStorage);
            MatchQualityService matchQualityService =
                    new MatchQualityService(userStorage, interactionStorage, config, compatibilityCalculator);
            ValidationService validationService = new ValidationService(config);
            LocationService locationService = new LocationService(validationService);
            AchievementService achievementService = new DefaultAchievementService(
                    config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage, profileService);

            new AchievementEventHandler(achievementService).register(eventBus);
            new MetricsEventHandler(activityMetricsService).register(eventBus);
            new NotificationEventHandler(communicationStorage).register(eventBus);

            return ServiceRegistry.builder()
                    .config(config)
                    .userStorage(userStorage)
                    .interactionStorage(interactionStorage)
                    .communicationStorage(communicationStorage)
                    .analyticsStorage(analyticsStorage)
                    .trustSafetyStorage(trustSafetyStorage)
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
    }

    static final class InMemoryUndoStorage implements Undo.Storage {
        private final Map<UUID, Undo> byUserId = new HashMap<>();

        @Override
        public void save(Undo state) {
            byUserId.put(state.userId(), state);
        }

        @Override
        public Optional<Undo> findByUserId(UUID userId) {
            return Optional.ofNullable(byUserId.get(userId));
        }

        @Override
        public boolean delete(UUID userId) {
            return byUserId.remove(userId) != null;
        }

        @Override
        public int deleteExpired(Instant now) {
            List<UUID> toDelete = new ArrayList<>();
            for (Undo undo : byUserId.values()) {
                if (undo.isExpired(now)) {
                    toDelete.add(undo.userId());
                }
            }
            toDelete.forEach(byUserId::remove);
            return toDelete.size();
        }

        @Override
        public List<Undo> findAll() {
            return List.copyOf(byUserId.values());
        }
    }

    static final class InMemoryStandoutStorage implements Standout.Storage {
        @Override
        public void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date) {
            // no-op test storage
        }

        @Override
        public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
            return List.of();
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date) {
            // no-op test storage
        }

        @Override
        public int cleanup(LocalDate before) {
            return 0;
        }
    }
}
