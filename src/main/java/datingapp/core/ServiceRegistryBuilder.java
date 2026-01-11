package datingapp.core;

import datingapp.storage.DatabaseManager;
import datingapp.storage.H2BlockStorage;
import datingapp.storage.H2ConversationStorage;
import datingapp.storage.H2DailyPickViewStorage;
import datingapp.storage.H2LikeStorage;
import datingapp.storage.H2MatchStorage;
import datingapp.storage.H2MessageStorage;
import datingapp.storage.H2PlatformStatsStorage;
import datingapp.storage.H2ProfileNoteStorage;
import datingapp.storage.H2ProfileViewStorage;
import datingapp.storage.H2ReportStorage;
import datingapp.storage.H2SwipeSessionStorage;
import datingapp.storage.H2UserAchievementStorage;
import datingapp.storage.H2UserStatsStorage;
import datingapp.storage.H2UserStorage;

/**
 * Builder for creating ServiceRegistry instances with different storage
 * backends.
 *
 * <p>
 * Extension point: Add new build methods for different backends: -
 * buildPostgres(config,
 * connectionPool) - buildInMemory(config) // for testing
 */
public final class ServiceRegistryBuilder {

  private ServiceRegistryBuilder() {
    // Utility class
  }

  /**
   * Builds a ServiceRegistry with H2 database storage.
   *
   * @param dbManager The H2 database manager
   * @param config    Application configuration
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
    CandidateFinderService candidateFinder = new CandidateFinder();
    SessionService sessionService = new SessionService(sessionStorage, config);
    MatchingService matchingService = new MatchingService(likeStorage, matchStorage, sessionService);
    ReportService reportService = new ReportService(reportStorage, userStorage, blockStorage, config);
    StatsService statsService = new StatsService(
        likeStorage,
        matchStorage,
        blockStorage,
        reportStorage,
        userStatsStorage,
        platformStatsStorage);
    MatchQualityService matchQualityService = new MatchQualityService(userStorage, likeStorage);
    ProfilePreviewService profilePreviewService = new ProfilePreviewService();
    DailyLimitService dailyLimitService = new DailyLimitService(likeStorage, config);
    UndoService undoService = new UndoService(likeStorage, matchStorage, config);
    DailyPickService dailyPickService = new DailyPickService(userStorage, likeStorage, blockStorage, dailyPickStorage,
        config);

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
    MessagingService messagingService = new MessagingService(conversationStorage, messageStorage, matchStorage,
        userStorage);

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
        messagingService);
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
