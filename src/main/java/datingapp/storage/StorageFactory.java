package datingapp.storage;

import datingapp.core.AchievementService;
import datingapp.core.AppConfig;
import datingapp.core.CandidateFinder;
import datingapp.core.CleanupService;
import datingapp.core.DailyService;
import datingapp.core.MatchQualityService;
import datingapp.core.MatchingService;
import datingapp.core.MessagingService;
import datingapp.core.ProfileCompletionService;
import datingapp.core.RelationshipTransitionService;
import datingapp.core.ServiceRegistry;
import datingapp.core.SessionService;
import datingapp.core.Standout;
import datingapp.core.StandoutsService;
import datingapp.core.StatsService;
import datingapp.core.TrustSafetyService;
import datingapp.core.UndoService;
import datingapp.core.UndoState;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.DailyPickViewStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.MessagingStorage;
import datingapp.core.storage.ReportStorage;
import datingapp.core.storage.SocialStorage;
import datingapp.core.storage.StatsStorage;
import datingapp.core.storage.SwipeSessionStorage;
import datingapp.core.storage.UserStorage;
import datingapp.storage.jdbi.EnumSetArgumentFactory;
import datingapp.storage.jdbi.EnumSetColumnMapper;
import datingapp.storage.jdbi.JdbiBlockStorage;
import datingapp.storage.jdbi.JdbiDailyPickViewStorage;
import datingapp.storage.jdbi.JdbiLikeStorage;
import datingapp.storage.jdbi.JdbiMatchStorage;
import datingapp.storage.jdbi.JdbiMessagingStorage;
import datingapp.storage.jdbi.JdbiReportStorage;
import datingapp.storage.jdbi.JdbiSocialStorage;
import datingapp.storage.jdbi.JdbiStandoutStorage;
import datingapp.storage.jdbi.JdbiStandoutStorageAdapter;
import datingapp.storage.jdbi.JdbiStatsStorage;
import datingapp.storage.jdbi.JdbiSwipeSessionStorage;
import datingapp.storage.jdbi.JdbiTransactionExecutor;
import datingapp.storage.jdbi.JdbiUndoStorageAdapter;
import datingapp.storage.jdbi.JdbiUserStorageAdapter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

/**
 * Factory for creating fully-wired {@link ServiceRegistry} instances backed by different storage
 * backends. Extracted from the former {@code ServiceRegistry.Builder} to keep the registry focused
 * on holding references while this factory handles wiring concerns.
 *
 * <p>Extension point: add {@code buildPostgres()} or similar for other backends.
 */
public final class StorageFactory {

    private StorageFactory() {
        // Utility class
    }

    /**
     * Builds a ServiceRegistry with H2 database storage. All storage and service instantiation
     * happens directly here.
     *
     * @param dbManager The H2 database manager
     * @param config Application configuration
     * @return Fully wired ServiceRegistry
     */
    public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
        Objects.requireNonNull(dbManager, "dbManager cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        // ═══════════════════════════════════════════════════════════════
        // JDBI Setup
        // ═══════════════════════════════════════════════════════════════
        Jdbi jdbi = Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException e) {
                        throw new StorageException("Failed to get database connection", e);
                    }
                })
                .installPlugin(new SqlObjectPlugin());

        jdbi.registerArgument(new EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new EnumSetColumnMapper());
        jdbi.registerColumnMapper(Instant.class, (rs, col, ctx) -> {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toInstant() : null;
        });

        // ═══════════════════════════════════════════════════════════════
        // Storage Instantiation
        // ═══════════════════════════════════════════════════════════════
        UserStorage userStorage = new JdbiUserStorageAdapter(jdbi);
        LikeStorage likeStorage = jdbi.onDemand(JdbiLikeStorage.class);
        MatchStorage matchStorage = jdbi.onDemand(JdbiMatchStorage.class);
        BlockStorage blockStorage = jdbi.onDemand(JdbiBlockStorage.class);
        DailyPickViewStorage dailyPickViewStorage = jdbi.onDemand(JdbiDailyPickViewStorage.class);
        ReportStorage reportStorage = jdbi.onDemand(JdbiReportStorage.class);
        SwipeSessionStorage sessionStorage = jdbi.onDemand(JdbiSwipeSessionStorage.class);
        StatsStorage statsStorage = jdbi.onDemand(JdbiStatsStorage.class);
        MessagingStorage messagingStorage = jdbi.onDemand(JdbiMessagingStorage.class);
        SocialStorage socialStorage = jdbi.onDemand(JdbiSocialStorage.class);

        UndoState.Storage undoStorage = new JdbiUndoStorageAdapter(jdbi);

        // ═══════════════════════════════════════════════════════════════
        // Matching Services
        // ═══════════════════════════════════════════════════════════════
        CandidateFinder candidateFinder = new CandidateFinder(userStorage, likeStorage, blockStorage, config);
        DailyService dailyService =
                new DailyService(userStorage, likeStorage, blockStorage, dailyPickViewStorage, candidateFinder, config);
        UndoService undoService = new UndoService(likeStorage, matchStorage, undoStorage, config);

        JdbiTransactionExecutor txExecutor = new JdbiTransactionExecutor(jdbi);
        undoService.setTransactionExecutor(txExecutor);

        SessionService sessionService = new SessionService(sessionStorage, config);
        MatchingService matchingService = MatchingService.builder()
                .likeStorage(likeStorage)
                .matchStorage(matchStorage)
                .userStorage(userStorage)
                .blockStorage(blockStorage)
                .sessionService(sessionService)
                .undoService(undoService)
                .dailyService(dailyService)
                .build();
        MatchQualityService matchQualityService = new MatchQualityService(userStorage, likeStorage, config);

        // ═══════════════════════════════════════════════════════════════
        // Messaging Services
        // ═══════════════════════════════════════════════════════════════
        MessagingService messagingService = new MessagingService(messagingStorage, matchStorage, userStorage);
        RelationshipTransitionService relationshipTransitionService =
                new RelationshipTransitionService(matchStorage, socialStorage, messagingStorage);

        // ═══════════════════════════════════════════════════════════════
        // Safety Services
        // ═══════════════════════════════════════════════════════════════
        TrustSafetyService trustSafetyService =
                new TrustSafetyService(reportStorage, userStorage, blockStorage, matchStorage, config);

        // ═══════════════════════════════════════════════════════════════
        // Stats Services
        // ═══════════════════════════════════════════════════════════════
        ProfileCompletionService profileCompletionService = new ProfileCompletionService(config);
        StatsService statsService =
                new StatsService(likeStorage, matchStorage, blockStorage, reportStorage, statsStorage);
        AchievementService achievementService = new AchievementService(
                statsStorage, matchStorage, likeStorage, userStorage, reportStorage, profileCompletionService, config);

        // ═══════════════════════════════════════════════════════════════
        // Maintenance Services
        // ═══════════════════════════════════════════════════════════════
        CleanupService cleanupService = new CleanupService(statsStorage, sessionStorage, config);

        // ═══════════════════════════════════════════════════════════════
        // Standouts Service
        // ═══════════════════════════════════════════════════════════════
        Standout.Storage standoutStorage = new JdbiStandoutStorageAdapter(jdbi.onDemand(JdbiStandoutStorage.class));
        StandoutsService standoutsService =
                new StandoutsService(userStorage, standoutStorage, candidateFinder, profileCompletionService, config);

        // ═══════════════════════════════════════════════════════════════
        // Build ServiceRegistry
        // ═══════════════════════════════════════════════════════════════
        return new ServiceRegistry(
                config,
                userStorage,
                likeStorage,
                matchStorage,
                blockStorage,
                dailyPickViewStorage,
                reportStorage,
                sessionStorage,
                statsStorage,
                messagingStorage,
                socialStorage,
                candidateFinder,
                matchingService,
                trustSafetyService,
                sessionService,
                statsService,
                matchQualityService,
                profileCompletionService,
                dailyService,
                undoService,
                achievementService,
                messagingService,
                relationshipTransitionService,
                cleanupService,
                standoutsService);
    }

    /** Builds a ServiceRegistry with H2 database and default configuration. */
    public static ServiceRegistry buildH2(DatabaseManager dbManager) {
        return buildH2(dbManager, AppConfig.defaults());
    }

    /** Builds an in-memory ServiceRegistry for testing. Uses H2 in-memory mode. */
    public static ServiceRegistry buildInMemory(AppConfig config) {
        return buildH2(DatabaseManager.getInstance(), config);
    }
}
