package datingapp.core;

import datingapp.core.Achievement.UserAchievementStorage;
import datingapp.core.DailyService.DailyPickStorage;
import datingapp.core.Match.MatchStorage;
import datingapp.core.Messaging.ConversationStorage;
import datingapp.core.Messaging.MessageStorage;
import datingapp.core.Social.FriendRequestStorage;
import datingapp.core.Social.NotificationStorage;
import datingapp.core.Stats.PlatformStatsStorage;
import datingapp.core.Stats.UserStatsStorage;
import datingapp.core.SwipeSession.SwipeSessionStorage;
import datingapp.core.User.ProfileNoteStorage;
import datingapp.core.User.ProfileViewStorage;
import datingapp.core.UserInteractions.BlockStorage;
import datingapp.core.UserInteractions.LikeStorage;
import datingapp.core.UserInteractions.ReportStorage;
import datingapp.storage.DatabaseManager;
import datingapp.storage.H2ConversationStorage;
import datingapp.storage.H2LikeStorage;
import datingapp.storage.H2MatchStorage;
import datingapp.storage.H2MessageStorage;
import datingapp.storage.H2MetricsStorage;
import datingapp.storage.H2ModerationStorage;
import datingapp.storage.H2ProfileDataStorage;
import datingapp.storage.H2SocialStorage;
import datingapp.storage.H2SwipeSessionStorage;
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
    private final User.Storage userStorage;
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
    private final TrustSafetyService trustSafetyService;
    private final SessionService sessionService; // Phase 0.5b
    private final StatsService statsService; // Phase 0.5b
    private final MatchQualityService matchQualityService; // Phase 0.5b
    private final ProfilePreviewService profilePreviewService; // Phase 1
    private final DailyService dailyService; // Phase 1
    private final UndoService undoService; // Phase 1
    private final AchievementService achievementService; // Phase 1
    private final MessagingService messagingService; // Messaging
    private final RelationshipTransitionService relationshipTransitionService; // Phase 2/3

    /** Package-private constructor - use ServiceRegistry.Builder to create. */
    @SuppressWarnings("java:S107")
    ServiceRegistry(
            AppConfig config,
            User.Storage userStorage,
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

    public User.Storage getUserStorage() {
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
            // Core storage layer
            User.Storage userStorage = new H2UserStorage(dbManager);
            LikeStorage likeStorage = new H2LikeStorage(dbManager);
            MatchStorage matchStorage = new H2MatchStorage(dbManager);
            SwipeSessionStorage sessionStorage = new H2SwipeSessionStorage(dbManager);
            UserStatsStorage userStatsStorage = new H2UserStatsStorage(dbManager);

            // Consolidated moderation storage (blocks + reports)
            H2ModerationStorage moderationStorage = new H2ModerationStorage(dbManager);
            BlockStorage blockStorage = moderationStorage.blocks();
            ReportStorage reportStorage = moderationStorage.reports();

            // Consolidated metrics storage (platform stats, daily picks, achievements)
            H2MetricsStorage metricsStorage = new H2MetricsStorage(dbManager);
            PlatformStatsStorage platformStatsStorage = metricsStorage.platformStats();
            DailyPickStorage dailyPickStorage = metricsStorage.dailyPicks();
            UserAchievementStorage userAchievementStorage = metricsStorage.achievements();

            // Consolidated profile data storage (views + notes)
            H2ProfileDataStorage profileDataStorage = new H2ProfileDataStorage(dbManager);
            ProfileViewStorage profileViewStorage = profileDataStorage.views();
            ProfileNoteStorage profileNoteStorage = profileDataStorage.notes();

            // Messaging storage (Phase 2)
            ConversationStorage conversationStorage = new H2ConversationStorage(dbManager);
            MessageStorage messageStorage = new H2MessageStorage(dbManager);

            // Consolidated social storage (friend requests + notifications)
            H2SocialStorage socialStorage = new H2SocialStorage(dbManager);
            FriendRequestStorage friendRequestStorage = socialStorage.friendRequests();
            NotificationStorage notificationStorage = socialStorage.notifications();

            // Services
            CandidateFinder candidateFinder = new CandidateFinder();
            SessionService sessionService = new SessionService(sessionStorage, config);
            MatchingService matchingService =
                    new MatchingService(likeStorage, matchStorage, userStorage, blockStorage, sessionService);
            TrustSafetyService trustSafetyService =
                    new TrustSafetyService(reportStorage, userStorage, blockStorage, config);
            StatsService statsService = new StatsService(
                    likeStorage, matchStorage, blockStorage, reportStorage, userStatsStorage, platformStatsStorage);
            MatchQualityService matchQualityService = new MatchQualityService(userStorage, likeStorage, config);
            ProfilePreviewService profilePreviewService = new ProfilePreviewService();
            DailyService dailyService =
                    new DailyService(userStorage, likeStorage, blockStorage, dailyPickStorage, candidateFinder, config);
            UndoService undoService = new UndoService(likeStorage, matchStorage, config);

            // Achievement Service (Phase 1)
            AchievementService achievementService = new AchievementService(
                    userAchievementStorage,
                    matchStorage,
                    likeStorage,
                    userStorage,
                    reportStorage,
                    profilePreviewService,
                    config);

            // Messaging Service (Phase 2)
            MessagingService messagingService =
                    new MessagingService(conversationStorage, messageStorage, matchStorage, userStorage);

            // Relationship Lifecycle Service (Phase 2 & 3)
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

        /** Builds an in-memory ServiceRegistry for testing. Uses the same H2 in-memory mode. */
        public static ServiceRegistry buildInMemory(AppConfig config) {
            return buildH2(DatabaseManager.getInstance(), config);
        }
    }
}
