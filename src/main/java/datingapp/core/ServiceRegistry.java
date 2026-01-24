package datingapp.core;

import datingapp.storage.DatabaseManager;
import datingapp.storage.H2BlockStorage;
import datingapp.storage.H2ConversationStorage;
import datingapp.storage.H2DailyPickViewStorage;
import datingapp.storage.H2FriendRequestStorage;
import datingapp.storage.H2LikeStorage;
import datingapp.storage.H2MatchStorage;
import datingapp.storage.H2MessageStorage;
import datingapp.storage.H2NotificationStorage;
import datingapp.storage.H2PlatformStatsStorage;
import datingapp.storage.H2ProfileNoteStorage;
import datingapp.storage.H2ProfileViewStorage;
import datingapp.storage.H2ReportStorage;
import datingapp.storage.H2SwipeSessionStorage;
import datingapp.storage.H2UserAchievementStorage;
import datingapp.storage.H2UserStatsStorage;
import datingapp.storage.H2UserStorage;
import java.util.Objects;

/**
 * Central registry holding all storage and service instances. Provides a single point of access for
 * all application components.
 *
 * <p>This pattern enables: - Easy testing with mock implementations - Swapping storage backends (H2
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
    private final SwipeSessionStorage sessionStorage; // Phase 0.5b
    private final UserStatsStorage userStatsStorage; // Phase 0.5b
    private final PlatformStatsStorage platformStatsStorage; // Phase 0.5b
    private final DailyPickStorage dailyPickStorage; // Phase 1
    private final UserAchievementStorage userAchievementStorage; // Phase 1
    private final ProfileViewStorage profileViewStorage; // Phase 1.5 - view counter
    private final ProfileNoteStorage profileNoteStorage; // Phase 1.5 - private notes
    private final ConversationStorage conversationStorage; // Messaging
    private final MessageStorage messageStorage; // Messaging
    private final FriendRequestStorage friendRequestStorage; // Phase 2
    private final NotificationStorage notificationStorage; // Phase 3

    // Services
    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;
    private final ReportService reportService;
    private final SessionService sessionService; // Phase 0.5b
    private final StatsService statsService; // Phase 0.5b
    private final MatchQualityService matchQualityService; // Phase 0.5b
    private final ProfilePreviewService profilePreviewService; // Phase 1
    private final DailyLimitService dailyLimitService; // Phase 1
    private final UndoService undoService; // Phase 1
    private final DailyPickService dailyPickService; // Phase 1
    private final AchievementService achievementService; // Phase 1
    private final MessagingService messagingService; // Messaging
    private final RelationshipTransitionService relationshipTransitionService; // Phase 2/3
    private final PaceCompatibilityService paceCompatibilityService; // Phase 1

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
            UserStatsStorage userStatsStorage,
            PlatformStatsStorage platformStatsStorage,
            DailyPickStorage dailyPickStorage,
            UserAchievementStorage userAchievementStorage,
            ProfileViewStorage profileViewStorage,
            ProfileNoteStorage profileNoteStorage,
            ConversationStorage conversationStorage,
            MessageStorage messageStorage,
            FriendRequestStorage friendRequestStorage,
            NotificationStorage notificationStorage,
            CandidateFinder candidateFinder,
            MatchingService matchingService,
            ReportService reportService,
            SessionService sessionService,
            StatsService statsService,
            MatchQualityService matchQualityService,
            ProfilePreviewService profilePreviewService,
            DailyLimitService dailyLimitService,
            UndoService undoService,
            DailyPickService dailyPickService,
            AchievementService achievementService,
            MessagingService messagingService,
            RelationshipTransitionService relationshipTransitionService,
            PaceCompatibilityService paceCompatibilityService) {
        this.config = Objects.requireNonNull(config);
        this.userStorage = Objects.requireNonNull(userStorage);
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.blockStorage = Objects.requireNonNull(blockStorage);
        this.reportStorage = Objects.requireNonNull(reportStorage);
        this.sessionStorage = Objects.requireNonNull(sessionStorage);
        this.userStatsStorage = Objects.requireNonNull(userStatsStorage);
        this.platformStatsStorage = Objects.requireNonNull(platformStatsStorage);
        this.dailyPickStorage = Objects.requireNonNull(dailyPickStorage);
        this.userAchievementStorage = Objects.requireNonNull(userAchievementStorage);
        this.profileViewStorage = Objects.requireNonNull(profileViewStorage);
        this.profileNoteStorage = Objects.requireNonNull(profileNoteStorage);
        this.conversationStorage = Objects.requireNonNull(conversationStorage);
        this.messageStorage = Objects.requireNonNull(messageStorage);
        this.friendRequestStorage = Objects.requireNonNull(friendRequestStorage);
        this.notificationStorage = Objects.requireNonNull(notificationStorage);
        this.candidateFinder = Objects.requireNonNull(candidateFinder);
        this.matchingService = Objects.requireNonNull(matchingService);
        this.reportService = Objects.requireNonNull(reportService);
        this.sessionService = Objects.requireNonNull(sessionService);
        this.statsService = Objects.requireNonNull(statsService);
        this.matchQualityService = Objects.requireNonNull(matchQualityService);
        this.profilePreviewService = Objects.requireNonNull(profilePreviewService);
        this.dailyLimitService = Objects.requireNonNull(dailyLimitService);
        this.undoService = Objects.requireNonNull(undoService);
        this.dailyPickService = Objects.requireNonNull(dailyPickService);
        this.achievementService = Objects.requireNonNull(achievementService);
        this.messagingService = Objects.requireNonNull(messagingService);
        this.relationshipTransitionService = Objects.requireNonNull(relationshipTransitionService);
        this.paceCompatibilityService = Objects.requireNonNull(paceCompatibilityService);
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

    public CandidateFinder getCandidateFinder() {
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

    public UndoService getUndoService() {
        return undoService;
    }

    public DailyPickStorage getDailyPickStorage() {
        return dailyPickStorage;
    }

    public DailyPickService getDailyPickService() {
        return dailyPickService;
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

    public ConversationStorage getConversationStorage() {
        return conversationStorage;
    }

    public MessageStorage getMessageStorage() {
        return messageStorage;
    }

    public MessagingService getMessagingService() {
        return messagingService;
    }

    public FriendRequestStorage getFriendRequestStorage() {
        return friendRequestStorage;
    }

    public NotificationStorage getNotificationStorage() {
        return notificationStorage;
    }

    public RelationshipTransitionService getRelationshipTransitionService() {
        return relationshipTransitionService;
    }

    public PaceCompatibilityService getPaceCompatibilityService() {
        return paceCompatibilityService;
    }

    /**
     * Builder for creating ServiceRegistry instances with different storage backends.
     *
     * <p>Extension point: Add new build methods for different backends: - buildPostgres(config,
     * connectionPool) - buildInMemory(config) // for testing
     */
    public static class Builder {

        private Builder() {
            // Utility class
        }

        /**
         * Builds a ServiceRegistry with H2 database storage.
         *
         * @param dbManager The H2 database manager
         * @param config Application configuration
         * @return Fully wired ServiceRegistry
         */
        public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
            // Storage layer
            UserStorage userStorage = new H2UserStorage(dbManager);
            LikeStorage likeStorage = new H2LikeStorage(dbManager);
            MatchStorage matchStorage = new H2MatchStorage(dbManager);
            BlockStorage blockStorage = new H2BlockStorage(dbManager);
            ReportStorage reportStorage = new H2ReportStorage(dbManager);
            SwipeSessionStorage sessionStorage = new H2SwipeSessionStorage(dbManager);
            UserStatsStorage userStatsStorage = new H2UserStatsStorage(dbManager);
            PlatformStatsStorage platformStatsStorage = new H2PlatformStatsStorage(dbManager);
            DailyPickStorage dailyPickStorage = new H2DailyPickViewStorage(dbManager);

            // Services
            CandidateFinder candidateFinder = new CandidateFinder();
            SessionService sessionService = new SessionService(sessionStorage, config);
            MatchingService matchingService = new MatchingService(likeStorage, matchStorage, sessionService);
            ReportService reportService = new ReportService(reportStorage, userStorage, blockStorage, config);
            StatsService statsService = new StatsService(
                    likeStorage, matchStorage, blockStorage, reportStorage, userStatsStorage, platformStatsStorage);
            PaceCompatibilityService paceCompatibilityService = new PaceCompatibilityService();
            MatchQualityService matchQualityService =
                    new MatchQualityService(userStorage, likeStorage, paceCompatibilityService);
            ProfilePreviewService profilePreviewService = new ProfilePreviewService();
            DailyLimitService dailyLimitService = new DailyLimitService(likeStorage, config);
            UndoService undoService = new UndoService(likeStorage, matchStorage, config);
            DailyPickService dailyPickService =
                    new DailyPickService(userStorage, likeStorage, blockStorage, dailyPickStorage, config);

            // Achievement System (Phase 1)
            UserAchievementStorage userAchievementStorage = new H2UserAchievementStorage(dbManager);
            AchievementService achievementService = new AchievementService(
                    userAchievementStorage,
                    matchStorage,
                    likeStorage,
                    userStorage,
                    reportStorage,
                    profilePreviewService);

            // Profile Views & Notes (Phase 1.5)
            ProfileViewStorage profileViewStorage = new H2ProfileViewStorage(dbManager);
            ProfileNoteStorage profileNoteStorage = new H2ProfileNoteStorage(dbManager);

            // Messaging (Phase 2)
            ConversationStorage conversationStorage = new H2ConversationStorage(dbManager);
            MessageStorage messageStorage = new H2MessageStorage(dbManager);
            MessagingService messagingService =
                    new MessagingService(conversationStorage, messageStorage, matchStorage, userStorage);

            // Relationship Lifecycle (Phase 2 & 3)
            FriendRequestStorage friendRequestStorage = new H2FriendRequestStorage(dbManager);
            NotificationStorage notificationStorage = new H2NotificationStorage(dbManager);
            RelationshipTransitionService relationshipTransitionService = new RelationshipTransitionService(
                    matchStorage, friendRequestStorage, conversationStorage, notificationStorage);

            return new ServiceRegistry(
                    config,
                    userStorage,
                    likeStorage,
                    matchStorage,
                    blockStorage,
                    reportStorage,
                    sessionStorage,
                    userStatsStorage,
                    platformStatsStorage,
                    dailyPickStorage,
                    userAchievementStorage,
                    profileViewStorage,
                    profileNoteStorage,
                    conversationStorage,
                    messageStorage,
                    friendRequestStorage,
                    notificationStorage,
                    candidateFinder,
                    matchingService,
                    reportService,
                    sessionService,
                    statsService,
                    matchQualityService,
                    profilePreviewService,
                    dailyLimitService,
                    undoService,
                    dailyPickService,
                    achievementService,
                    messagingService,
                    relationshipTransitionService,
                    paceCompatibilityService);
        }

        /** Builds a ServiceRegistry with H2 database and default configuration. */
        public static ServiceRegistry buildH2(DatabaseManager dbManager) {
            return buildH2(dbManager, AppConfig.defaults());
        }

        /** Builds an in-memory ServiceRegistry for testing. Uses the same H2 in-memory mode. */
        public static ServiceRegistry buildInMemory(AppConfig config) {
            return buildH2(DatabaseManager.getInstance(), config);
        }
    }
}
