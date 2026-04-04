package datingapp.storage;

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
import datingapp.core.matching.DefaultBrowseRankingService;
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
import datingapp.core.storage.AccountCleanupStorage;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.core.workflow.RelationshipWorkflowPolicy;
import datingapp.storage.jdbi.JdbiAccountCleanupStorage;
import datingapp.storage.jdbi.JdbiConnectionStorage;
import datingapp.storage.jdbi.JdbiMatchmakingStorage;
import datingapp.storage.jdbi.JdbiMetricsStorage;
import datingapp.storage.jdbi.JdbiTrustSafetyStorage;
import datingapp.storage.jdbi.JdbiTypeCodecs;
import datingapp.storage.jdbi.JdbiUserStorage;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

/** Factory for creating fully wired {@link ServiceRegistry} instances. */
public final class StorageFactory {

    private StorageFactory() {}

    public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
        Objects.requireNonNull(dbManager, "dbManager cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        dbManager.configureQueryTimeoutSeconds(config.storage().queryTimeoutSeconds());

        Jdbi jdbi = createJdbi(dbManager);
        registerTypeCodecs(jdbi);

        PersistenceComponents persistence = createPersistenceComponents(jdbi);
        DomainServices domain = createDomainServices(config, persistence);

        registerEventHandlers(persistence, domain);

        return assembleRegistry(config, persistence, domain);
    }

    private static Jdbi createJdbi(DatabaseManager dbManager) {
        return Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException e) {
                        throw new DatabaseManager.StorageException("Failed to get database connection", e);
                    }
                })
                .installPlugin(new SqlObjectPlugin());
    }

    private static void registerTypeCodecs(Jdbi jdbi) {
        jdbi.registerArgument(new JdbiTypeCodecs.EnumSetSqlCodec.EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new JdbiTypeCodecs.EnumSetSqlCodec.InterestColumnMapper());
        jdbi.registerColumnMapper(Instant.class, (rs, col, ctx) -> {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toInstant() : null;
        });
    }

    private static PersistenceComponents createPersistenceComponents(Jdbi jdbi) {
        UserStorage userStorage = new JdbiUserStorage(jdbi);
        JdbiMatchmakingStorage matchmakingStorage = new JdbiMatchmakingStorage(jdbi);
        CommunicationStorage communicationStorage = new JdbiConnectionStorage(jdbi);
        JdbiMetricsStorage metricsStorage = new JdbiMetricsStorage(jdbi);
        TrustSafetyStorage trustSafetyStorage = jdbi.onDemand(JdbiTrustSafetyStorage.class);
        AccountCleanupStorage accountCleanupStorage = new JdbiAccountCleanupStorage(jdbi);

        return new PersistenceComponents(
                userStorage,
                matchmakingStorage,
                communicationStorage,
                metricsStorage,
                trustSafetyStorage,
                accountCleanupStorage,
                matchmakingStorage.undoStorage(),
                metricsStorage);
    }

    private static DomainServices createDomainServices(AppConfig config, PersistenceComponents persistence) {
        CompatibilityCalculator compatibilityCalculator = new DefaultCompatibilityCalculator(config);
        CandidateFinder candidateFinder = new CandidateFinder(
                persistence.userStorage(),
                persistence.interactionStorage(),
                persistence.trustSafetyStorage(),
                config.safety().userTimeZone(),
                Duration.ofHours(config.matching().rematchCooldownHours()));

        ProfileService profileService = new ProfileService(persistence.userStorage());
        AchievementService achievementService = new DefaultAchievementService(
                config,
                persistence.analyticsStorage(),
                persistence.interactionStorage(),
                persistence.trustSafetyStorage(),
                persistence.userStorage(),
                profileService);
        DailyLimitService dailyLimitService = new DefaultDailyLimitService(persistence.interactionStorage(), config);
        DailyPickService dailyPickService =
                new DefaultDailyPickService(persistence.analyticsStorage(), candidateFinder, config);
        StandoutService standoutService = new DefaultStandoutService(
                compatibilityCalculator,
                persistence.userStorage(),
                candidateFinder,
                persistence.standoutStorage(),
                profileService,
                config);
        RecommendationService recommendationService = new RecommendationService(
                dailyLimitService,
                dailyPickService,
                standoutService,
                new DefaultBrowseRankingService(compatibilityCalculator, profileService, config));
        UndoService undoService = new UndoService(persistence.interactionStorage(), persistence.undoStorage(), config);
        ActivityMetricsService activityMetricsService = new ActivityMetricsService(
                persistence.userStorage(),
                persistence.interactionStorage(),
                persistence.trustSafetyStorage(),
                persistence.analyticsStorage(),
                config);
        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(persistence.interactionStorage())
                .trustSafetyStorage(persistence.trustSafetyStorage())
                .userStorage(persistence.userStorage())
                .activityMetricsService(activityMetricsService)
                .undoService(undoService)
                .dailyService(recommendationService)
                .candidateFinder(candidateFinder)
                .build();
        MatchQualityService matchQualityService = new MatchQualityService(
                persistence.userStorage(), persistence.interactionStorage(), config, compatibilityCalculator);
        ConnectionService connectionService = new ConnectionService(
                config,
                persistence.communicationStorage(),
                persistence.interactionStorage(),
                persistence.userStorage());
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        persistence.trustSafetyStorage(),
                        persistence.interactionStorage(),
                        persistence.userStorage(),
                        config,
                        persistence.communicationStorage())
                .build();
        trustSafetyService.setCandidateFinder(candidateFinder);

        ValidationService validationService = new ValidationService(config);
        LocationService locationService = new LocationService(validationService);
        AppEventBus eventBus = new InProcessAppEventBus();

        return new DomainServices(
                candidateFinder,
                compatibilityCalculator,
                profileService,
                achievementService,
                dailyLimitService,
                dailyPickService,
                standoutService,
                recommendationService,
                undoService,
                activityMetricsService,
                matchingService,
                matchQualityService,
                connectionService,
                trustSafetyService,
                validationService,
                locationService,
                eventBus);
    }

    private static void registerEventHandlers(PersistenceComponents persistence, DomainServices domain) {
        new AchievementEventHandler(domain.achievementService()).register(domain.eventBus());
        new MetricsEventHandler(domain.activityMetricsService()).register(domain.eventBus());
        new NotificationEventHandler(persistence.communicationStorage()).register(domain.eventBus());
    }

    private static ServiceRegistry assembleRegistry(
            AppConfig config, PersistenceComponents persistence, DomainServices domain) {
        return ServiceRegistry.builder()
                .config(config)
                .userStorage(persistence.userStorage())
                .interactionStorage(persistence.interactionStorage())
                .communicationStorage(persistence.communicationStorage())
                .analyticsStorage(persistence.analyticsStorage())
                .trustSafetyStorage(persistence.trustSafetyStorage())
                .accountCleanupStorage(persistence.accountCleanupStorage())
                .candidateFinder(domain.candidateFinder())
                .matchingService(domain.matchingService())
                .trustSafetyService(domain.trustSafetyService())
                .activityMetricsService(domain.activityMetricsService())
                .matchQualityService(domain.matchQualityService())
                .profileService(domain.profileService())
                .recommendationService(domain.recommendationService())
                .dailyLimitService(domain.dailyLimitService())
                .dailyPickService(domain.dailyPickService())
                .standoutService(domain.standoutService())
                .undoService(domain.undoService())
                .achievementService(domain.achievementService())
                .connectionService(domain.connectionService())
                .validationService(domain.validationService())
                .locationService(domain.locationService())
                .eventBus(domain.eventBus())
                .activationPolicy(new ProfileActivationPolicy())
                .workflowPolicy(new RelationshipWorkflowPolicy())
                .build();
    }

    private record PersistenceComponents(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            CommunicationStorage communicationStorage,
            AnalyticsStorage analyticsStorage,
            TrustSafetyStorage trustSafetyStorage,
            AccountCleanupStorage accountCleanupStorage,
            Undo.Storage undoStorage,
            Standout.Storage standoutStorage) {}

    private record DomainServices(
            CandidateFinder candidateFinder,
            CompatibilityCalculator compatibilityCalculator,
            ProfileService profileService,
            AchievementService achievementService,
            DailyLimitService dailyLimitService,
            DailyPickService dailyPickService,
            StandoutService standoutService,
            RecommendationService recommendationService,
            UndoService undoService,
            ActivityMetricsService activityMetricsService,
            MatchingService matchingService,
            MatchQualityService matchQualityService,
            ConnectionService connectionService,
            TrustSafetyService trustSafetyService,
            ValidationService validationService,
            LocationService locationService,
            AppEventBus eventBus) {}

    /**
     * Builds a {@link ServiceRegistry} using an H2 in-memory database.
     * Despite the name, this uses a full SQL database engine (H2) in
     * in-memory mode, not a pure in-memory data structure.
     */
    public static ServiceRegistry buildInMemory(AppConfig config) {
        DatabaseManager isolatedManager = DatabaseManager.createIsolated(
                "jdbc:h2:mem:datingapp-inmemory-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return buildH2(isolatedManager, config);
    }
}
