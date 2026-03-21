package datingapp.storage;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
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
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.core.workflow.RelationshipWorkflowPolicy;
import datingapp.storage.jdbi.JdbiConnectionStorage;
import datingapp.storage.jdbi.JdbiMatchmakingStorage;
import datingapp.storage.jdbi.JdbiMetricsStorage;
import datingapp.storage.jdbi.JdbiTrustSafetyStorage;
import datingapp.storage.jdbi.JdbiTypeCodecs;
import datingapp.storage.jdbi.JdbiUserStorage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

/** Factory for creating fully wired {@link ServiceRegistry} instances. */
public final class StorageFactory {

    private StorageFactory() {}

    public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
        Objects.requireNonNull(dbManager, "dbManager cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        Jdbi jdbi = Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException e) {
                        throw new DatabaseManager.StorageException("Failed to get database connection", e);
                    }
                })
                .installPlugin(new SqlObjectPlugin());

        jdbi.registerArgument(new JdbiTypeCodecs.EnumSetSqlCodec.EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new JdbiTypeCodecs.EnumSetSqlCodec.InterestColumnMapper());
        jdbi.registerColumnMapper(Instant.class, (rs, col, ctx) -> {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toInstant() : null;
        });

        UserStorage userStorage = new JdbiUserStorage(jdbi);
        JdbiMatchmakingStorage matchmakingStorage = new JdbiMatchmakingStorage(jdbi);
        InteractionStorage interactionStorage = matchmakingStorage;
        CommunicationStorage communicationStorage = new JdbiConnectionStorage(jdbi);
        JdbiMetricsStorage metricsStorage = new JdbiMetricsStorage(jdbi);
        AnalyticsStorage analyticsStorage = metricsStorage;
        TrustSafetyStorage trustSafetyStorage = jdbi.onDemand(JdbiTrustSafetyStorage.class);

        Undo.Storage undoStorage = matchmakingStorage.undoStorage();
        Standout.Storage standoutStorage = metricsStorage;

        CompatibilityCalculator compatibilityCalculator = new DefaultCompatibilityCalculator(config);

        CandidateFinder candidateFinder = new CandidateFinder(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                config.safety().userTimeZone());
        ProfileService profileService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);

        AchievementService achievementService = new DefaultAchievementService(
                config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage, profileService);

        DailyLimitService dailyLimitService = new DefaultDailyLimitService(interactionStorage, config);
        DailyPickService dailyPickService =
                new DefaultDailyPickService(userStorage, interactionStorage, analyticsStorage, candidateFinder, config);
        StandoutService standoutService = new DefaultStandoutService(
                compatibilityCalculator, userStorage, candidateFinder, standoutStorage, profileService, config);

        RecommendationService recommendationService =
                new RecommendationService(dailyLimitService, dailyPickService, standoutService);
        UndoService undoService = new UndoService(interactionStorage, undoStorage, config);
        ActivityMetricsService activityMetricsService =
                new ActivityMetricsService(interactionStorage, trustSafetyStorage, analyticsStorage, config);

        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .activityMetricsService(activityMetricsService)
                .undoService(undoService)
                .dailyService(recommendationService)
                .candidateFinder(candidateFinder)
                .build();

        MatchQualityService matchQualityService =
                new MatchQualityService(userStorage, interactionStorage, config, compatibilityCalculator);

        ConnectionService connectionService =
                new ConnectionService(config, communicationStorage, interactionStorage, userStorage);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactionStorage, userStorage, config, communicationStorage)
                .build();
        trustSafetyService.setCandidateFinder(candidateFinder);

        ValidationService validationService = new ValidationService(config);

        AppEventBus eventBus = new InProcessAppEventBus();

        new AchievementEventHandler(achievementService).register(eventBus);
        new MetricsEventHandler(activityMetricsService).register(eventBus);
        new NotificationEventHandler(communicationStorage).register(eventBus);
        eventBus.subscribe(
                AppEvent.ProfileSaved.class,
                ignoredEvent -> {
                    // Intentionally no-op: keeps one REQUIRED policy subscription in production wiring.
                },
                AppEventBus.HandlerPolicy.REQUIRED);

        ProfileActivationPolicy activationPolicy = new ProfileActivationPolicy();
        RelationshipWorkflowPolicy workflowPolicy = new RelationshipWorkflowPolicy();

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
                .compatibilityCalculator(compatibilityCalculator)
                .achievementService(achievementService)
                .connectionService(connectionService)
                .validationService(validationService)
                .eventBus(eventBus)
                .activationPolicy(activationPolicy)
                .workflowPolicy(workflowPolicy)
                .build();
    }

    public static ServiceRegistry buildH2(DatabaseManager dbManager) {
        return buildH2(dbManager, AppConfig.defaults());
    }

    public static ServiceRegistry buildInMemory(AppConfig config) {
        return buildH2(DatabaseManager.getInstance(), config);
    }
}
