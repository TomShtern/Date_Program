package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.AppConfig;
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
import datingapp.core.profile.LocationService;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.viewmodel.UiDataAdapters.UiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiProfileNoteDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("UiAdapterCache")
class UiAdapterCacheTest {

    @Test
    @DisplayName("userStore is cached and returns the same instance on repeated calls")
    void userStoreIsCachedUntilReset() {
        UiAdapterCache cache = new UiAdapterCache();
        ServiceRegistry services = buildTestServiceRegistry();

        UiUserStore first = cache.userStore(services);
        UiUserStore second = cache.userStore(services);

        assertSame(first, second, "userStore should be cached within cache lifetime");

        cache.reset();

        UiUserStore third = cache.userStore(services);
        assertNotSame(first, third, "After reset, new userStore instance should be created");
    }

    @Test
    @DisplayName("profileNotes and presence adapters are cached independently")
    void profileNotesAndPresenceAdaptersAreCachedIndependently() {
        UiAdapterCache cache = new UiAdapterCache();
        ServiceRegistry services = buildTestServiceRegistry();

        UiProfileNoteDataAccess profileNotes1 = cache.profileNotes(services);
        UiProfileNoteDataAccess profileNotes2 = cache.profileNotes(services);
        assertSame(profileNotes1, profileNotes2, "profileNotes should be cached");

        UiPresenceDataAccess presence1 = cache.presence(services);
        UiPresenceDataAccess presence2 = cache.presence(services);
        assertSame(presence1, presence2, "presence should be cached");
    }

    @Test
    @DisplayName("reset clears all cached adapters")
    void resetClearsAllCachedAdapters() {
        UiAdapterCache cache = new UiAdapterCache();
        ServiceRegistry services = buildTestServiceRegistry();

        UiUserStore userStore1 = cache.userStore(services);
        UiProfileNoteDataAccess profileNotes1 = cache.profileNotes(services);
        UiPresenceDataAccess presence1 = cache.presence(services);

        cache.reset();

        UiUserStore userStore2 = cache.userStore(services);
        UiProfileNoteDataAccess profileNotes2 = cache.profileNotes(services);
        UiPresenceDataAccess presence2 = cache.presence(services);

        assertNotSame(userStore1, userStore2, "userStore should be reset");
        assertNotSame(profileNotes1, profileNotes2, "profileNotes should be reset");
        assertNotSame(presence1, presence2, "presence should be reset");
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
}
