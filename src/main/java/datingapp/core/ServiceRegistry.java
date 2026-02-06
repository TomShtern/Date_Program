package datingapp.core;

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
import datingapp.storage.DatabaseManager;
import datingapp.storage.TransactionTemplate;
import java.util.Objects;

/**
 * Central registry holding all storage and service instances. Provides a single
 * point of access for
 * all application components.
 *
 * <p>
 * This pattern enables: - Easy testing with mock implementations - Swapping
 * storage backends (H2
 * -> PostgreSQL) - Adding new services without modifying Main
 */
@SuppressWarnings("java:S6539")
public class ServiceRegistry {

    // ─────────────────────────────────────────────
    // Configuration
    // ─────────────────────────────────────────────
    private final AppConfig config;

    // ─────────────────────────────────────────────
    // Core Storage (Users, Likes, Matches)
    // ─────────────────────────────────────────────
    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final BlockStorage blockStorage;
    private final DailyPickViewStorage dailyPickViewStorage;
    private final SwipeSessionStorage sessionStorage;

    // ─────────────────────────────────────────────
    // Trust & Safety Storage
    // ─────────────────────────────────────────────
    private final ReportStorage reportStorage;

    // ─────────────────────────────────────────────
    // Profile & Stats Storage
    // ─────────────────────────────────────────────
    private final StatsStorage statsStorage; // Consolidated: user + platform stats

    // ─────────────────────────────────────────────
    // Messaging & Social Storage
    // ─────────────────────────────────────────────
    private final MessagingStorage messagingStorage; // Consolidated: conversation + message
    private final SocialStorage socialStorage; // Consolidated: friend request + notification

    // ─────────────────────────────────────────────
    // Core Services (Matching)
    // ─────────────────────────────────────────────
    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;
    private final SessionService sessionService;
    private final MatchQualityService matchQualityService;
    private final DailyService dailyService;
    private final UndoService undoService;

    // ─────────────────────────────────────────────
    // Trust & Safety Services
    // ─────────────────────────────────────────────
    private final TrustSafetyService trustSafetyService;

    // ─────────────────────────────────────────────
    // Stats & Achievement Services
    // ─────────────────────────────────────────────
    private final StatsService statsService;
    private final ProfilePreviewService profilePreviewService;
    private final AchievementService achievementService;

    // ─────────────────────────────────────────────
    // Messaging & Relationship Services
    // ─────────────────────────────────────────────
    private final MessagingService messagingService; // Messaging
    private final RelationshipTransitionService relationshipTransitionService;

    // ─────────────────────────────────────────────
    // Transaction Support
    // ─────────────────────────────────────────────
    private final TransactionTemplate transactionTemplate;

    // ─────────────────────────────────────────────
    // Maintenance Services
    // ─────────────────────────────────────────────
    private final CleanupService cleanupService;
    private final StandoutsService standoutsService;

    /** Package-private constructor - use ServiceRegistry.Builder to create. */
    @SuppressWarnings("java:S107")
    ServiceRegistry(
            AppConfig config,
            UserStorage userStorage,
            LikeStorage likeStorage,
            MatchStorage matchStorage,
            BlockStorage blockStorage,
            DailyPickViewStorage dailyPickViewStorage,
            ReportStorage reportStorage,
            SwipeSessionStorage sessionStorage,
            StatsStorage statsStorage,
            MessagingStorage messagingStorage,
            SocialStorage socialStorage,
            CandidateFinder candidateFinder,
            MatchingService matchingService,
            TrustSafetyService trustSafetyService,
            SessionService sessionService,
            StatsService statsService,
            MatchQualityService matchQualityService,
            ProfilePreviewService profilePreviewService,
            DailyService dailyService,
            UndoService undoService,
            AchievementService achievementService,
            MessagingService messagingService,
            RelationshipTransitionService relationshipTransitionService,
            TransactionTemplate transactionTemplate,
            CleanupService cleanupService,
            StandoutsService standoutsService) {
        this.config = Objects.requireNonNull(config);
        this.userStorage = Objects.requireNonNull(userStorage);
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.blockStorage = Objects.requireNonNull(blockStorage);
        this.dailyPickViewStorage = Objects.requireNonNull(dailyPickViewStorage);
        this.reportStorage = Objects.requireNonNull(reportStorage);
        this.sessionStorage = Objects.requireNonNull(sessionStorage);
        this.statsStorage = Objects.requireNonNull(statsStorage);
        this.messagingStorage = Objects.requireNonNull(messagingStorage);
        this.socialStorage = Objects.requireNonNull(socialStorage);
        this.candidateFinder = Objects.requireNonNull(candidateFinder);
        this.matchingService = Objects.requireNonNull(matchingService);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService);
        this.sessionService = Objects.requireNonNull(sessionService);
        this.statsService = Objects.requireNonNull(statsService);
        this.matchQualityService = Objects.requireNonNull(matchQualityService);
        this.profilePreviewService = Objects.requireNonNull(profilePreviewService);
        this.dailyService = Objects.requireNonNull(dailyService);
        this.undoService = Objects.requireNonNull(undoService);
        this.achievementService = Objects.requireNonNull(achievementService);
        this.messagingService = Objects.requireNonNull(messagingService);
        this.relationshipTransitionService = Objects.requireNonNull(relationshipTransitionService);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.cleanupService = Objects.requireNonNull(cleanupService);
        this.standoutsService = Objects.requireNonNull(standoutsService);
    }

    // === Configuration ===

    public AppConfig getConfig() {
        return config;
    }

    // === Core Storage ===

    public UserStorage getUserStorage() {
        return userStorage;
    }

    public LikeStorage getLikeStorage() {
        return likeStorage;
    }

    public MatchStorage getMatchStorage() {
        return matchStorage;
    }

    public BlockStorage getBlockStorage() {
        return blockStorage;
    }

    public SwipeSessionStorage getSessionStorage() {
        return sessionStorage;
    }

    public DailyPickViewStorage getDailyPickViewStorage() {
        return dailyPickViewStorage;
    }

    // === Trust & Safety Storage ===

    public ReportStorage getReportStorage() {
        return reportStorage;
    }

    // === Profile & Stats Storage ===

    public StatsStorage getStatsStorage() {
        return statsStorage;
    }

    // === Messaging & Social Storage ===

    public MessagingStorage getMessagingStorage() {
        return messagingStorage;
    }

    public SocialStorage getSocialStorage() {
        return socialStorage;
    }

    // === Matching Services ===

    public CandidateFinder getCandidateFinder() {
        return candidateFinder;
    }

    public MatchingService getMatchingService() {
        return matchingService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public MatchQualityService getMatchQualityService() {
        return matchQualityService;
    }

    public DailyService getDailyService() {
        return dailyService;
    }

    public UndoService getUndoService() {
        return undoService;
    }

    // === Trust & Safety Services ===

    public TrustSafetyService getTrustSafetyService() {
        return trustSafetyService;
    }

    // === Stats & Achievement Services ===

    public StatsService getStatsService() {
        return statsService;
    }

    public ProfilePreviewService getProfilePreviewService() {
        return profilePreviewService;
    }

    public AchievementService getAchievementService() {
        return achievementService;
    }

    // === Messaging & Relationship Services ===

    public MessagingService getMessagingService() {
        return messagingService;
    }

    public RelationshipTransitionService getRelationshipTransitionService() {
        return relationshipTransitionService;
    }

    // === Transaction Support ===

    /**
     * Gets the transaction template for atomic database operations.
     *
     * @return The transaction template for ACID operations
     */
    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    // === Maintenance Services ===

    /**
     * Gets the cleanup service for purging expired data.
     *
     * @return The cleanup service
     */
    public CleanupService getCleanupService() {
        return cleanupService;
    }

    public StandoutsService getStandoutsService() {
        return standoutsService;
    }

    /**
     * Builder for creating ServiceRegistry instances with different storage
     * backends.
     *
     * <p>
     * Extension point: Add new build methods for different backends: -
     * buildPostgres(config,
     * connectionPool) - buildInMemory(config) // for testing
     */
    @SuppressWarnings("java:S1192") // Allow repeated literal strings for clarity in this builder
    public static final class Builder {

        private Builder() {
            // Utility class
        }

        /**
         * Builds a ServiceRegistry with H2 database storage.
         * All storage and service instantiation happens directly here (inlined from
         * former module classes: StorageModule, MatchingModule, MessagingModule,
         * SafetyModule, StatsModule).
         *
         * @param dbManager The H2 database manager
         * @param config    Application configuration
         * @return Fully wired ServiceRegistry
         */
        public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
            Objects.requireNonNull(dbManager, "dbManager cannot be null");
            Objects.requireNonNull(config, "config cannot be null");

            // ═══════════════════════════════════════════════════════════════
            // JDBI Setup (inlined from StorageModule.forH2)
            // ═══════════════════════════════════════════════════════════════
            org.jdbi.v3.core.Jdbi jdbi = org.jdbi.v3.core.Jdbi.create(() -> {
                        try {
                            return dbManager.getConnection();
                        } catch (java.sql.SQLException e) {
                            throw new datingapp.storage.StorageException("Failed to get database connection", e);
                        }
                    })
                    .installPlugin(new org.jdbi.v3.sqlobject.SqlObjectPlugin());

            jdbi.registerArgument(new datingapp.storage.jdbi.EnumSetArgumentFactory());
            jdbi.registerColumnMapper(new datingapp.storage.jdbi.EnumSetColumnMapper());
            // Register mapper for Instant type to handle Map<UUID, Instant> mappings
            jdbi.registerColumnMapper(java.time.Instant.class, (rs, col, ctx) -> {
                java.sql.Timestamp ts = rs.getTimestamp(col);
                return ts != null ? ts.toInstant() : null;
            });

            // ═══════════════════════════════════════════════════════════════
            // Storage Instantiation (inlined from StorageModule.forH2)
            // ═══════════════════════════════════════════════════════════════
            UserStorage userStorage = new datingapp.storage.jdbi.JdbiUserStorageAdapter(jdbi);
            LikeStorage likeStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiLikeStorage.class);
            MatchStorage matchStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiMatchStorage.class);
            BlockStorage blockStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiBlockStorage.class);
            DailyPickViewStorage dailyPickViewStorage =
                    jdbi.onDemand(datingapp.storage.jdbi.JdbiDailyPickViewStorage.class);
            ReportStorage reportStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiReportStorage.class);
            SwipeSessionStorage sessionStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiSwipeSessionStorage.class);
            StatsStorage statsStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiStatsStorage.class);
            MessagingStorage messagingStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiMessagingStorage.class);
            SocialStorage socialStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiSocialStorage.class);

            // Undo storage for persistent undo state
            UndoState.Storage undoStorage = new datingapp.storage.jdbi.JdbiUndoStorageAdapter(jdbi);

            // ═══════════════════════════════════════════════════════════════
            // Matching Services (inlined from MatchingModule.create)
            // ═══════════════════════════════════════════════════════════════
            CandidateFinder candidateFinder = new CandidateFinder(userStorage, likeStorage, blockStorage, config);
            DailyService dailyService = new DailyService(
                    userStorage, likeStorage, blockStorage, dailyPickViewStorage, candidateFinder, config);
            UndoService undoService = new UndoService(likeStorage, matchStorage, undoStorage, config);

            // Wire transaction support for atomic undo operations
            datingapp.storage.jdbi.JdbiTransactionExecutor txExecutor =
                    new datingapp.storage.jdbi.JdbiTransactionExecutor(jdbi);
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
            // Messaging Services (inlined from MessagingModule.create)
            // ═══════════════════════════════════════════════════════════════
            MessagingService messagingService = new MessagingService(messagingStorage, matchStorage, userStorage);
            RelationshipTransitionService relationshipTransitionService =
                    new RelationshipTransitionService(matchStorage, socialStorage, messagingStorage);

            // ═══════════════════════════════════════════════════════════════
            // Safety Services (inlined from SafetyModule.create)
            // ═══════════════════════════════════════════════════════════════
            TrustSafetyService trustSafetyService =
                    new TrustSafetyService(reportStorage, userStorage, blockStorage, matchStorage, config);
            // ValidationService is now a utility class - instances not tracked in registry

            // ═══════════════════════════════════════════════════════════════
            // Stats Services (inlined from StatsModule.create)
            // ═══════════════════════════════════════════════════════════════
            ProfilePreviewService profilePreviewService = new ProfilePreviewService();
            StatsService statsService =
                    new StatsService(likeStorage, matchStorage, blockStorage, reportStorage, statsStorage);
            AchievementService achievementService = new AchievementService(
                    statsStorage, matchStorage, likeStorage, userStorage, reportStorage, profilePreviewService, config);

            // ═══════════════════════════════════════════════════════════════
            // Transaction Support
            // ═══════════════════════════════════════════════════════════════
            TransactionTemplate transactionTemplate = new TransactionTemplate(jdbi);

            // ═══════════════════════════════════════════════════════════════
            // Maintenance Services
            // ═══════════════════════════════════════════════════════════════
            CleanupService cleanupService = new CleanupService(statsStorage, sessionStorage, config);

            // ═══════════════════════════════════════════════════════════════
            // Standouts Service
            // ═══════════════════════════════════════════════════════════════
            Standout.Storage standoutStorage = new datingapp.storage.jdbi.JdbiStandoutStorageAdapter(
                    jdbi.onDemand(datingapp.storage.jdbi.JdbiStandoutStorage.class));
            StandoutsService standoutsService =
                    new StandoutsService(userStorage, standoutStorage, candidateFinder, config);

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
                    profilePreviewService,
                    dailyService,
                    undoService,
                    achievementService,
                    messagingService,
                    relationshipTransitionService,
                    transactionTemplate,
                    cleanupService,
                    standoutsService);
        }

        /** Builds a ServiceRegistry with H2 database and default configuration. */
        public static ServiceRegistry buildH2(DatabaseManager dbManager) {
            return buildH2(dbManager, AppConfig.defaults());
        }

        /**
         * Builds an in-memory ServiceRegistry for testing. Uses the same H2 in-memory
         * mode.
         */
        public static ServiceRegistry buildInMemory(AppConfig config) {
            return buildH2(DatabaseManager.getInstance(), config);
        }
    }
}
