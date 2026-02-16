package datingapp.storage;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.profile.ProfileService;
import datingapp.core.recommendation.RecommendationService;
import datingapp.core.recommendation.Standout;
import datingapp.core.safety.TrustSafetyService;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import datingapp.storage.jdbi.connection.JdbiConnectionStorage;
import datingapp.storage.jdbi.matching.JdbiMatchmakingStorage;
import datingapp.storage.jdbi.metrics.JdbiMetricsStorage;
import datingapp.storage.jdbi.profile.JdbiUserStorage;
import datingapp.storage.jdbi.safety.JdbiTrustSafetyStorage;
import datingapp.storage.jdbi.shared.JdbiTypeCodecs;
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

        CandidateFinder candidateFinder =
                new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, config);
        ProfileService profileService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);
        RecommendationService recommendationService = RecommendationService.builder()
                .userStorage(userStorage)
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(standoutStorage)
                .profileService(profileService)
                .config(config)
                .clock(java.time.Clock.systemUTC())
                .build();
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
                .build();
        MatchQualityService matchQualityService = new MatchQualityService(userStorage, interactionStorage, config);

        ConnectionService connectionService =
                new ConnectionService(config, communicationStorage, interactionStorage, userStorage);

        TrustSafetyService trustSafetyService =
                new TrustSafetyService(trustSafetyStorage, interactionStorage, userStorage, config);

        return new ServiceRegistry(
                config,
                userStorage,
                interactionStorage,
                communicationStorage,
                analyticsStorage,
                trustSafetyStorage,
                candidateFinder,
                matchingService,
                trustSafetyService,
                activityMetricsService,
                matchQualityService,
                profileService,
                recommendationService,
                undoService,
                connectionService);
    }

    public static ServiceRegistry buildH2(DatabaseManager dbManager) {
        return buildH2(dbManager, AppConfig.defaults());
    }

    public static ServiceRegistry buildInMemory(AppConfig config) {
        return buildH2(DatabaseManager.getInstance(), config);
    }
}
