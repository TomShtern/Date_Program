package datingapp.core;

import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.ConversationStorage;
import datingapp.core.storage.DailyPickStorage;
import datingapp.core.storage.FriendRequestStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.MessageStorage;
import datingapp.core.storage.NotificationStorage;
import datingapp.core.storage.PlatformStatsStorage;
import datingapp.core.storage.ProfileNoteStorage;
import datingapp.core.storage.ProfileViewStorage;
import datingapp.core.storage.ReportStorage;
import datingapp.core.storage.SwipeSessionStorage;
import datingapp.core.storage.UserAchievementStorage;
import datingapp.core.storage.UserStatsStorage;
import datingapp.core.storage.UserStorage;
import datingapp.storage.DatabaseManager;
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
    private final SwipeSessionStorage sessionStorage;
    private final UserStatsStorage userStatsStorage;
    private final PlatformStatsStorage platformStatsStorage;
    private final DailyPickStorage dailyPickStorage;
    private final UserAchievementStorage userAchievementStorage;
    private final ProfileViewStorage profileViewStorage;
    private final ProfileNoteStorage profileNoteStorage;
    private final ConversationStorage conversationStorage; // Messaging
    private final MessageStorage messageStorage; // Messaging
    private final FriendRequestStorage friendRequestStorage;
    private final NotificationStorage notificationStorage;

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
    public static final class Builder {

        private Builder() {
            // Utility class
        }

        /**
         * Builds a ServiceRegistry with H2 database storage using the new modular architecture.
         * Delegates to AppContext for storage/service wiring, then wraps in ServiceRegistry.
         *
         * @param dbManager The H2 database manager
         * @param config Application configuration
         * @return Fully wired ServiceRegistry
         */
        public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
            datingapp.module.AppContext app = datingapp.module.AppContext.create(dbManager, config);
            return fromAppContext(app);
        }

        /** Builds a ServiceRegistry with H2 database and default configuration. */
        public static ServiceRegistry buildH2(DatabaseManager dbManager) {
            return buildH2(dbManager, AppConfig.defaults());
        }

        /** Builds an in-memory ServiceRegistry for testing. Uses the same H2 in-memory mode. */
        public static ServiceRegistry buildInMemory(AppConfig config) {
            return buildH2(DatabaseManager.getInstance(), config);
        }

        /**
         * Creates a ServiceRegistry from an AppContext for backward compatibility.
         * This allows gradual migration from ServiceRegistry to AppContext.
         *
         * @param app The AppContext containing all modules
         * @return A ServiceRegistry wrapping the AppContext's components
         */
        public static ServiceRegistry fromAppContext(datingapp.module.AppContext app) {
            return new ServiceRegistry(
                    app.config(),
                    app.storage().users(),
                    app.storage().likes(),
                    app.storage().matches(),
                    app.storage().blocks(),
                    app.storage().reports(),
                    app.storage().swipeSessions(),
                    app.storage().userStats(),
                    app.storage().platformStats(),
                    app.storage().dailyPicks(),
                    app.storage().achievements(),
                    app.storage().profileViews(),
                    app.storage().profileNotes(),
                    app.storage().conversations(),
                    app.storage().messages(),
                    app.storage().friendRequests(),
                    app.storage().notifications(),
                    app.matching().finder(),
                    app.matching().matching(),
                    app.safety().trustSafety(),
                    app.matching().session(),
                    app.stats().stats(),
                    app.matching().quality(),
                    app.stats().profilePreview(),
                    app.matching().daily(),
                    app.matching().undo(),
                    app.stats().achievements(),
                    app.messaging().messaging(),
                    app.messaging().transitions());
        }
    }
}
