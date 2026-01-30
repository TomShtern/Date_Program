package datingapp.core;

import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.DailyPickStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.MessagingStorage;
import datingapp.core.storage.ProfileNoteStorage;
import datingapp.core.storage.ProfileViewStorage;
import datingapp.core.storage.ReportStorage;
import datingapp.core.storage.SocialStorage;
import datingapp.core.storage.StatsStorage;
import datingapp.core.storage.SwipeSessionStorage;
import datingapp.core.storage.UserAchievementStorage;
import datingapp.core.storage.UserStorage;
import datingapp.storage.DatabaseManager;
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

    // Configuration
    private final AppConfig config;

    // Storage layer
    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final BlockStorage blockStorage;
    private final ReportStorage reportStorage;
    private final SwipeSessionStorage sessionStorage;
    private final StatsStorage statsStorage; // Consolidated: user + platform stats
    private final DailyPickStorage dailyPickStorage;
    private final UserAchievementStorage userAchievementStorage;
    private final ProfileViewStorage profileViewStorage;
    private final ProfileNoteStorage profileNoteStorage;
    private final MessagingStorage messagingStorage; // Consolidated: conversation + message
    private final SocialStorage socialStorage; // Consolidated: friend request + notification

    // Services
    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;
    private final TrustSafetyService trustSafetyService;
    private final SessionService sessionService;
    private final StatsService statsService;
    private final MatchQualityService matchQualityService;
    private final ProfilePreviewService profilePreviewService;
    private final DailyService dailyService;
    private final UndoService undoService;
    private final AchievementService achievementService;
    private final MessagingService messagingService; // Messaging
    private final RelationshipTransitionService relationshipTransitionService;

    /** Package-private constructor - use ServiceRegistry.Builder to create. */
    @SuppressWarnings("java:S107")
    ServiceRegistry(
            AppConfig config,
            UserStorage userStorage,
            LikeStorage likeStorage,
            MatchStorage matchStorage,
            BlockStorage blockStorage,
            ReportStorage reportStorage,
            SwipeSessionStorage sessionStorage,
            StatsStorage statsStorage,
            DailyPickStorage dailyPickStorage,
            UserAchievementStorage userAchievementStorage,
            ProfileViewStorage profileViewStorage,
            ProfileNoteStorage profileNoteStorage,
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
            RelationshipTransitionService relationshipTransitionService) {
        this.config = Objects.requireNonNull(config);
        this.userStorage = Objects.requireNonNull(userStorage);
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.blockStorage = Objects.requireNonNull(blockStorage);
        this.reportStorage = Objects.requireNonNull(reportStorage);
        this.sessionStorage = Objects.requireNonNull(sessionStorage);
        this.statsStorage = Objects.requireNonNull(statsStorage);
        this.dailyPickStorage = Objects.requireNonNull(dailyPickStorage);
        this.userAchievementStorage = Objects.requireNonNull(userAchievementStorage);
        this.profileViewStorage = Objects.requireNonNull(profileViewStorage);
        this.profileNoteStorage = Objects.requireNonNull(profileNoteStorage);
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
    }

    // === Getters ===

    public AppConfig getConfig() {
        return config;
    }

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

    public ReportStorage getReportStorage() {
        return reportStorage;
    }

    public SwipeSessionStorage getSessionStorage() {
        return sessionStorage;
    }

    public StatsStorage getStatsStorage() {
        return statsStorage;
    }

    public CandidateFinder getCandidateFinder() {
        return candidateFinder;
    }

    public MatchingService getMatchingService() {
        return matchingService;
    }

    public TrustSafetyService getTrustSafetyService() {
        return trustSafetyService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public StatsService getStatsService() {
        return statsService;
    }

    public MatchQualityService getMatchQualityService() {
        return matchQualityService;
    }

    public ProfilePreviewService getProfilePreviewService() {
        return profilePreviewService;
    }

    public DailyService getDailyService() {
        return dailyService;
    }

    public UndoService getUndoService() {
        return undoService;
    }

    public DailyPickStorage getDailyPickStorage() {
        return dailyPickStorage;
    }

    public UserAchievementStorage getUserAchievementStorage() {
        return userAchievementStorage;
    }

    public AchievementService getAchievementService() {
        return achievementService;
    }

    public ProfileViewStorage getProfileViewStorage() {
        return profileViewStorage;
    }

    public ProfileNoteStorage getProfileNoteStorage() {
        return profileNoteStorage;
    }

    public MessagingStorage getMessagingStorage() {
        return messagingStorage;
    }

    public MessagingService getMessagingService() {
        return messagingService;
    }

    public SocialStorage getSocialStorage() {
        return socialStorage;
    }

    public RelationshipTransitionService getRelationshipTransitionService() {
        return relationshipTransitionService;
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

            // ═══════════════════════════════════════════════════════════════
            // Storage Instantiation (inlined from StorageModule.forH2)
            // ═══════════════════════════════════════════════════════════════
            UserStorage userStorage = new datingapp.storage.jdbi.JdbiUserStorageAdapter(jdbi);
            LikeStorage likeStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiLikeStorage.class);
            MatchStorage matchStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiMatchStorage.class);
            BlockStorage blockStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiBlockStorage.class);
            ReportStorage reportStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiReportStorage.class);
            SwipeSessionStorage sessionStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiSwipeSessionStorage.class);
            StatsStorage statsStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiStatsStorage.class);
            DailyPickStorage dailyPickStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiDailyPickStorage.class);
            UserAchievementStorage userAchievementStorage =
                    jdbi.onDemand(datingapp.storage.jdbi.JdbiUserAchievementStorage.class);
            ProfileViewStorage profileViewStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiProfileViewStorage.class);
            ProfileNoteStorage profileNoteStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiProfileNoteStorage.class);
            MessagingStorage messagingStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiMessagingStorage.class);
            SocialStorage socialStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiSocialStorage.class);

            // ═══════════════════════════════════════════════════════════════
            // Matching Services (inlined from MatchingModule.create)
            // ═══════════════════════════════════════════════════════════════
            CandidateFinder candidateFinder = new CandidateFinder();
            SessionService sessionService = new SessionService(sessionStorage, config);
            MatchingService matchingService =
                    new MatchingService(likeStorage, matchStorage, userStorage, blockStorage, sessionService);
            MatchQualityService matchQualityService = new MatchQualityService(userStorage, likeStorage, config);
            DailyService dailyService =
                    new DailyService(userStorage, likeStorage, blockStorage, dailyPickStorage, candidateFinder, config);
            UndoService undoService = new UndoService(likeStorage, matchStorage, config);

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
                    new TrustSafetyService(reportStorage, userStorage, blockStorage, config);
            // ValidationService is now a utility class - instances not tracked in registry

            // ═══════════════════════════════════════════════════════════════
            // Stats Services (inlined from StatsModule.create)
            // ═══════════════════════════════════════════════════════════════
            ProfilePreviewService profilePreviewService = new ProfilePreviewService();
            StatsService statsService =
                    new StatsService(likeStorage, matchStorage, blockStorage, reportStorage, statsStorage);
            AchievementService achievementService = new AchievementService(
                    userAchievementStorage,
                    matchStorage,
                    likeStorage,
                    userStorage,
                    reportStorage,
                    profilePreviewService,
                    config);

            // ═══════════════════════════════════════════════════════════════
            // Build ServiceRegistry
            // ═══════════════════════════════════════════════════════════════
            return new ServiceRegistry(
                    config,
                    userStorage,
                    likeStorage,
                    matchStorage,
                    blockStorage,
                    reportStorage,
                    sessionStorage,
                    statsStorage,
                    dailyPickStorage,
                    userAchievementStorage,
                    profileViewStorage,
                    profileNoteStorage,
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
                    relationshipTransitionService);
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
