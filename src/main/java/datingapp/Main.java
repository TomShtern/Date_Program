package datingapp;

import datingapp.app.cli.CliConstants;
import datingapp.app.cli.CliUtilities.InputReader;
import datingapp.app.cli.HandlerFactory;
import datingapp.core.*;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console-based dating app - Phase 0.5. Main entry point with interactive menu.
 * Refactored to
 * delegate logic to specialized handlers.
 */
public final class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Main() {
        /* Utility class with only static methods */
    }

    // Application context
    private static ServiceRegistry services;

    // CLI Components
    private static InputReader inputReader;
    private static HandlerFactory handlers;

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            initializeApp(scanner);

            logger.info("\n🌹 Welcome to Dating App 🌹\n");

            boolean running = true;
            while (running) {
                printMenu();
                String choice = inputReader.readLine("Choose an option: ");

                switch (choice) {
                    case "1" -> handlers.profile().createUser();
                    case "2" -> handlers.profile().selectUser();
                    case "3" -> handlers.profile().completeProfile();
                    case "4" -> handlers.matching().browseCandidates();
                    case "5" -> handlers.matching().viewMatches();
                    case "6" -> handlers.safety().blockUser();
                    case "7" -> handlers.safety().reportUser();
                    case "8" -> handlers.safety().manageBlockedUsers();
                    case "9" -> handlers.profile().setDealbreakers();
                    case "10" -> handlers.stats().viewStatistics();
                    case "11" -> handlers.profile().previewProfile();
                    case "12" -> handlers.stats().viewAchievements();
                    case "13" -> handlers.profileNotes().viewAllNotes();
                    case "14" -> handlers.profile().viewProfileScore();
                    case "15" -> handlers.safety().verifyProfile();
                    case "16" -> handlers.likerBrowser().browseWhoLikedMe();
                    case "17" -> handlers.messaging().showConversations();
                    case "18" -> handlers.relationship().viewNotifications();
                    case "19" -> handlers.relationship().viewPendingRequests();
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
        // Initialize application with default configuration
        services = AppBootstrap.initialize();

        // Initialize CLI Infrastructure
        inputReader = new InputReader(scanner);

        // Initialize Handlers via factory
        handlers = new HandlerFactory(services, AppSession.getInstance(), inputReader);
    }

    private static void printMenu() {
        logger.info(CliConstants.SEPARATOR_LINE);
        logger.info("         DATING APP - PHASE 0.5");
        logger.info(CliConstants.SEPARATOR_LINE);

        User currentUser = AppSession.getInstance().getCurrentUser();

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
        int unreadCount = handlers.messaging().getTotalUnreadCount();
        String unreadStr = unreadCount > 0 ? " (" + unreadCount + " new)" : "";
        logger.info("  17. 💬 Conversations{}", unreadStr);
        logger.info("  18. 🔔 Notifications");
        logger.info("  19. 🤝 Friend Requests");
        logger.info("  0. Exit");
        logger.info(CliConstants.SEPARATOR_LINE + "\n");
    }

    private static void shutdown() {
        AppBootstrap.shutdown();
    }
}
