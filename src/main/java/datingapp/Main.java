package datingapp;

import datingapp.cli.CliConstants;
import datingapp.cli.CliUtilities;
import datingapp.cli.LikerBrowserHandler;
import datingapp.cli.MatchingHandler;
import datingapp.cli.MessagingHandler;
import datingapp.cli.ProfileHandler;
import datingapp.cli.ProfileNotesHandler;
import datingapp.cli.RelationshipHandler;
import datingapp.cli.SafetyHandler;
import datingapp.cli.StatsHandler;
import datingapp.core.AppConfig;
import datingapp.core.DailyService;
import datingapp.core.ServiceRegistry;
import datingapp.core.SessionService;
import datingapp.core.User;
import datingapp.core.ValidationService;
import datingapp.storage.DatabaseManager;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console-based dating app - Phase 0.5. Main entry point with interactive menu. Refactored to
 * delegate logic to specialized handlers.
 */
public final class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Main() {
        /* Utility class with only static methods */
    }

    // Application context
    private static DatabaseManager dbManager;
    private static ServiceRegistry services;

    // CLI Components
    private static CliUtilities.InputReader inputReader;
    private static CliUtilities.UserSession userSession;
    private static ProfileHandler profileHandler;
    private static MatchingHandler matchingHandler;
    private static SafetyHandler safetyHandler;
    private static StatsHandler statsHandler;
    private static ProfileNotesHandler profileNotesHandler;
    private static LikerBrowserHandler likerBrowserHandler;
    private static MessagingHandler messagingHandler;
    private static RelationshipHandler relationshipHandler;

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            initializeApp(scanner);

            logger.info("\n🌹 Welcome to Dating App 🌹\n");

            boolean running = true;
            while (running) {
                printMenu();
                String choice = inputReader.readLine("Choose an option: ");

                switch (choice) {
                    case "1" -> profileHandler.createUser();
                    case "2" -> profileHandler.selectUser();
                    case "3" -> profileHandler.completeProfile();
                    case "4" -> matchingHandler.browseCandidates();
                    case "5" -> matchingHandler.viewMatches();
                    case "6" -> safetyHandler.blockUser();
                    case "7" -> safetyHandler.reportUser();
                    case "8" -> safetyHandler.manageBlockedUsers();
                    case "9" -> profileHandler.setDealbreakers();
                    case "10" -> statsHandler.viewStatistics();
                    case "11" -> profileHandler.previewProfile();
                    case "12" -> statsHandler.viewAchievements();
                    case "13" -> profileNotesHandler.viewAllNotes();
                    case "14" -> profileHandler.viewProfileScore();
                    case "15" -> safetyHandler.verifyProfile();
                    case "16" -> likerBrowserHandler.browseWhoLikedMe();
                    case "17" -> messagingHandler.showConversations();
                    case "18" -> relationshipHandler.viewNotifications();
                    case "19" -> relationshipHandler.viewPendingRequests();
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
        services = ServiceRegistry.Builder.buildH2(dbManager, config);

        // Initialize CLI Infrastructure
        inputReader = new CliUtilities.InputReader(scanner);
        userSession = new CliUtilities.UserSession();

        // Initialize validation service (stateless, no dependencies)
        ValidationService validationService = new ValidationService();

        // Initialize Handlers
        profileHandler = new ProfileHandler(
                services.getUserStorage(),
                services.getProfilePreviewService(),
                services.getAchievementService(),
                validationService,
                userSession,
                inputReader);

        MatchingHandler.Dependencies matchingDependencies = new MatchingHandler.Dependencies(
                services.getCandidateFinder(),
                services.getMatchingService(),
                services.getLikeStorage(),
                services.getMatchStorage(),
                services.getBlockStorage(),
                services.getDailyService(),
                services.getUndoService(),
                services.getMatchQualityService(),
                services.getUserStorage(),
                services.getAchievementService(),
                services.getProfileViewStorage(),
                services.getRelationshipTransitionService(),
                userSession,
                inputReader);
        matchingHandler = new MatchingHandler(matchingDependencies);

        safetyHandler = new SafetyHandler(
                services.getUserStorage(),
                services.getBlockStorage(),
                services.getMatchStorage(),
                services.getTrustSafetyService(),
                userSession,
                inputReader);

        statsHandler = new StatsHandler(
                services.getStatsService(), services.getAchievementService(), userSession, inputReader);

        profileNotesHandler = new ProfileNotesHandler(
                services.getProfileNoteStorage(), services.getUserStorage(), userSession, inputReader);

        likerBrowserHandler = new LikerBrowserHandler(services.getMatchingService(), userSession, inputReader);

        messagingHandler = new MessagingHandler(services, inputReader, userSession);

        relationshipHandler = new RelationshipHandler(
                services.getRelationshipTransitionService(),
                services.getNotificationStorage(),
                services.getUserStorage(),
                userSession,
                inputReader);
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
                    .ifPresent(session -> logger.info(
                            "  Session: {} swipes ({} likes, {} passes) | {} elapsed",
                            session.getSwipeCount(),
                            session.getLikeCount(),
                            session.getPassCount(),
                            session.getFormattedDuration()));

            // Show daily likes
            DailyService dailyService = services.getDailyService();
            DailyService.DailyStatus dailyStatus = dailyService.getStatus(currentUser.getId());
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
        logger.info("  8. 🔓 Manage blocked users");
        logger.info("  9. 🎯 Set dealbreakers");
        logger.info("  10. 📊 View my statistics");
        logger.info("  11. 👤 Preview my profile");
        logger.info("  12. 🏆 View achievements");
        logger.info("  13. 📝 My profile notes");
        logger.info("  14. 📊 Profile completion score");
        logger.info("  15. ✅ Verify my profile");
        logger.info("  16. 💌 Who liked me");
        int unreadCount = messagingHandler.getTotalUnreadCount();
        String unreadStr = unreadCount > 0 ? " (" + unreadCount + " new)" : "";
        logger.info("  17. 💬 Conversations{}", unreadStr);
        logger.info("  18. 🔔 Notifications");
        logger.info("  19. 🤝 Friend Requests");
        logger.info("  0. Exit");
        logger.info(CliConstants.SEPARATOR_LINE + "\n");
    }

    private static void shutdown() {
        if (dbManager != null) {
            dbManager.shutdown();
        }
    }
}
