package datingapp.storage;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.Standout;
import datingapp.core.model.SwipeState.Undo;
import datingapp.core.service.ActivityMetricsService;
import datingapp.core.service.CandidateFinder;
import datingapp.core.service.ConnectionService;
import datingapp.core.service.MatchQualityService;
import datingapp.core.service.MatchingService;
import datingapp.core.service.ProfileService;
import datingapp.core.service.RecommendationService;
import datingapp.core.service.TrustSafetyService;
import datingapp.core.service.UndoService;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
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

        CandidateFinder candidateFinder =
                new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, config);
        ProfileService profileService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);
        RecommendationService recommendationService = new RecommendationService(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                analyticsStorage,
                candidateFinder,
                standoutStorage,
                profileService,
                config,
                null);
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
                new ConnectionService(communicationStorage, interactionStorage, userStorage);

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
