package datingapp;

import datingapp.cli.CliConstants;
import datingapp.cli.InputReader;
import datingapp.cli.MatchingHandler;
import datingapp.cli.ProfileHandler;
import datingapp.cli.SafetyHandler;
import datingapp.cli.StatsHandler;
import datingapp.cli.UserManagementHandler;
import datingapp.cli.UserSession;
import datingapp.core.AppConfig;
import datingapp.core.DailyLimitService;
import datingapp.core.ServiceRegistry;
import datingapp.core.ServiceRegistryBuilder;
import datingapp.core.SessionService;
import datingapp.core.User;
import datingapp.storage.DatabaseManager;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console-based dating app - Phase 0.5. Main entry point with interactive menu. Refactored to
 * delegate logic to specialized handlers.
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  // Application context
  private static DatabaseManager dbManager;
  private static ServiceRegistry services;

  // CLI Components
  private static InputReader inputReader;
  private static UserSession userSession;
  private static UserManagementHandler userHandler;
  private static ProfileHandler profileHandler;
  private static MatchingHandler matchingHandler;
  private static SafetyHandler safetyHandler;
  private static StatsHandler statsHandler;

  public static void main(String[] args) {
    try (Scanner scanner = new Scanner(System.in)) {
      initializeApp(scanner);

      logger.info("\n🌹 Welcome to Dating App 🌹\n");

      boolean running = true;
      while (running) {
        printMenu();
        String choice = inputReader.readLine("Choose an option: ");

        switch (choice) {
          case "1" -> userHandler.createUser();
          case "2" -> userHandler.selectUser();
          case "3" -> profileHandler.completeProfile();
          case "4" -> matchingHandler.browseCandidates();
          case "5" -> matchingHandler.viewMatches();
          case "6" -> safetyHandler.blockUser();
          case "7" -> safetyHandler.reportUser();
          case "8" -> profileHandler.setDealbreakers();
          case "9" -> statsHandler.viewStatistics();
          case "10" -> profileHandler.previewProfile();
          case "11" -> statsHandler.viewAchievements();
          case "0" -> {
            running = false;
            logger.info("\n👋 Goodbye!\n");
          }
          default -> logger.info(CliConstants.INVALID_SELECTION);
        }
      }

      shutdown();
    }
  }

  private static void initializeApp(Scanner scanner) {
    // Configuration
    AppConfig config = AppConfig.defaults();

    // Database
    dbManager = DatabaseManager.getInstance();

    // Wire up all services through the registry
    services = ServiceRegistryBuilder.buildH2(dbManager, config);

    // Initialize CLI Infrastructure
    inputReader = new InputReader(scanner);
    userSession = new UserSession();

    // Initialize Handlers
    userHandler = new UserManagementHandler(services.getUserStorage(), userSession, inputReader);

    profileHandler =
        new ProfileHandler(
            services.getUserStorage(),
            services.getProfilePreviewService(),
            services.getAchievementService(),
            userSession,
            inputReader);

    matchingHandler = new MatchingHandler(services, userSession, inputReader);

    safetyHandler =
        new SafetyHandler(
            services.getUserStorage(),
            services.getBlockStorage(),
            services.getMatchStorage(),
            services.getReportService(),
            userSession,
            inputReader);

    statsHandler =
        new StatsHandler(
            services.getStatsService(), services.getAchievementService(), userSession, inputReader);
  }

  private static void printMenu() {
    logger.info(CliConstants.SEPARATOR_LINE);
    logger.info("         DATING APP - PHASE 0.5");
    logger.info(CliConstants.SEPARATOR_LINE);

    User currentUser = userSession.getCurrentUser();

    if (currentUser != null) {
      logger.info("  Current User: {} ({})", currentUser.getName(), currentUser.getState());

      // Show active session info
      SessionService sessionService = services.getSessionService();
      sessionService
          .getCurrentSession(currentUser.getId())
          .ifPresent(
              session ->
                  logger.info(
                      "  Session: {} swipes ({} likes, {} passes) | {} elapsed",
                      session.getSwipeCount(),
                      session.getLikeCount(),
                      session.getPassCount(),
                      session.getFormattedDuration()));

      // Show daily likes
      DailyLimitService dailyLimitService = services.getDailyLimitService();
      DailyLimitService.DailyStatus dailyStatus = dailyLimitService.getStatus(currentUser.getId());
      if (dailyStatus.hasUnlimitedLikes()) {
        logger.info("  💝 Daily Likes: unlimited");
      } else {
        logger.info(
            "  💝 Daily Likes: {}/{} remaining",
            dailyStatus.likesRemaining(),
            services.getConfig().dailyLikeLimit());
      }

    } else {
      logger.info("  Current User: [None]");
    }
    logger.info("───────────────────────────────────────");
    logger.info("  1. Create new user");
    logger.info("  2. Select existing user");
    logger.info("  3. Complete my profile");
    logger.info("  4. Browse candidates");
    logger.info("  5. View my matches");
    logger.info("  6. 🚫 Block a user");
    logger.info("  7. ⚠️  Report a user");
    logger.info("  8. 🎯 Set dealbreakers");
    logger.info("  9. 📊 View my statistics");
    logger.info("  10. 👤 Preview my profile");
    logger.info("  11. 🏆 View achievements");
    logger.info("  0. Exit");
    logger.info(CliConstants.SEPARATOR_LINE + "\n");
  }

  private static void shutdown() {
    if (dbManager != null) {
      // dbManager.close(); // If dbManager has close logic
    }
  }
}
