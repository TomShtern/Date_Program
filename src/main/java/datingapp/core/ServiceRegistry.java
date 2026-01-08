package datingapp.core;

import java.util.Objects;

/**
 * Central registry holding all storage and service instances.
 * Provides a single point of access for all application components.
 *
 * This pattern enables:
 * - Easy testing with mock implementations
 * - Swapping storage backends (H2 -> PostgreSQL)
 * - Adding new services without modifying Main
 */
public class ServiceRegistry {

    // Configuration
    private final AppConfig config;

    // Storage layer
    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final BlockStorage blockStorage;
    private final ReportStorage reportStorage;
    private final SwipeSessionStorage sessionStorage; // Phase 0.5b
    private final UserStatsStorage userStatsStorage; // Phase 0.5b
    private final PlatformStatsStorage platformStatsStorage; // Phase 0.5b

    // Services
    private final CandidateFinderService candidateFinder;
    private final MatchingService matchingService;
    private final ReportService reportService;
    private final SessionService sessionService; // Phase 0.5b
    private final StatsService statsService; // Phase 0.5b
    private final MatchQualityService matchQualityService; // Phase 0.5b
    private final ProfilePreviewService profilePreviewService; // Phase 1
    private final DailyLimitService dailyLimitService; // Phase 1

    /**
     * Package-private constructor - use ServiceRegistryBuilder to create.
     */
    ServiceRegistry(AppConfig config,
            UserStorage userStorage,
            LikeStorage likeStorage,
            MatchStorage matchStorage,
            BlockStorage blockStorage,
            ReportStorage reportStorage,
            SwipeSessionStorage sessionStorage,
            UserStatsStorage userStatsStorage,
            PlatformStatsStorage platformStatsStorage,
            CandidateFinderService candidateFinder,
            MatchingService matchingService,
            ReportService reportService,
            SessionService sessionService,
            StatsService statsService,
            MatchQualityService matchQualityService,
            ProfilePreviewService profilePreviewService,
            DailyLimitService dailyLimitService) {
        this.config = Objects.requireNonNull(config);
        this.userStorage = Objects.requireNonNull(userStorage);
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.blockStorage = Objects.requireNonNull(blockStorage);
        this.reportStorage = Objects.requireNonNull(reportStorage);
        this.sessionStorage = Objects.requireNonNull(sessionStorage);
        this.userStatsStorage = Objects.requireNonNull(userStatsStorage);
        this.platformStatsStorage = Objects.requireNonNull(platformStatsStorage);
        this.candidateFinder = Objects.requireNonNull(candidateFinder);
        this.matchingService = Objects.requireNonNull(matchingService);
        this.reportService = Objects.requireNonNull(reportService);
        this.sessionService = Objects.requireNonNull(sessionService);
        this.statsService = Objects.requireNonNull(statsService);
        this.matchQualityService = Objects.requireNonNull(matchQualityService);
        this.profilePreviewService = Objects.requireNonNull(profilePreviewService);
        this.dailyLimitService = Objects.requireNonNull(dailyLimitService);
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

    public UserStatsStorage getUserStatsStorage() {
        return userStatsStorage;
    }

    public PlatformStatsStorage getPlatformStatsStorage() {
        return platformStatsStorage;
    }

    public CandidateFinderService getCandidateFinder() {
        return candidateFinder;
    }

    public MatchingService getMatchingService() {
        return matchingService;
    }

    public ReportService getReportService() {
        return reportService;
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

    public DailyLimitService getDailyLimitService() {
        return dailyLimitService;
    }
}
