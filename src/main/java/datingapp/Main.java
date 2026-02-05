package datingapp;

import datingapp.app.cli.CliConstants;
import datingapp.app.cli.HandlerFactory;
import datingapp.app.cli.InputReader;
import datingapp.core.AppBootstrap;
import datingapp.core.AppSession;
import datingapp.core.DailyService;
import datingapp.core.ServiceRegistry;
import datingapp.core.SessionService;
import datingapp.core.User;
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

            logInfo("\n🌹 Welcome to Dating App 🌹\n");

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
                    case "20" -> handlers.matching().viewStandouts();
                    case "0" -> {
                        running = false;
                        logInfo("\n👋 Goodbye!\n");
                    }
                    default -> logInfo(CliConstants.INVALID_SELECTION);
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

    private static void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private static void printMenu() {
        logInfo(CliConstants.SEPARATOR_LINE);
        logInfo("         DATING APP - PHASE 0.5");
        logInfo(CliConstants.SEPARATOR_LINE);

        User currentUser = AppSession.getInstance().getCurrentUser();

        if (currentUser != null) {
            logInfo("  Current User: {} ({})", currentUser.getName(), currentUser.getState());

            // Show active session info
            SessionService sessionService = services.getSessionService();
            sessionService
                    .getCurrentSession(currentUser.getId())
                    .ifPresent(session -> logInfo(
                            "  Session: {} swipes ({} likes, {} passes) | {} elapsed",
                            session.getSwipeCount(),
                            session.getLikeCount(),
                            session.getPassCount(),
                            session.getFormattedDuration()));

            // Show daily likes
            DailyService dailyService = services.getDailyService();
            DailyService.DailyStatus dailyStatus = dailyService.getStatus(currentUser.getId());
            if (dailyStatus.hasUnlimitedLikes()) {
                logInfo("  💝 Daily Likes: unlimited");
            } else {
                logInfo(
                        "  💝 Daily Likes: {}/{} remaining",
                        dailyStatus.likesRemaining(),
                        services.getConfig().dailyLikeLimit());
            }

        } else {
            logInfo("  Current User: [None]");
        }
        logInfo("───────────────────────────────────────");
        logInfo("  1. Create new user");
        logInfo("  2. Select existing user");
        logInfo("  3. Complete my profile");
        logInfo("  4. Browse candidates");
        logInfo("  5. View my matches");
        logInfo("  6. 🚫 Block a user");
        logInfo("  7. ⚠️  Report a user");
        logInfo("  8. 🔓 Manage blocked users");
        logInfo("  9. 🎯 Set dealbreakers");
        logInfo("  10. 📊 View my statistics");
        logInfo("  11. 👤 Preview my profile");
        logInfo("  12. 🏆 View achievements");
        logInfo("  13. 📝 My profile notes");
        logInfo("  14. 📊 Profile completion score");
        logInfo("  15. ✅ Verify my profile");
        logInfo("  16. 💌 Who liked me");
        int unreadCount = handlers.messaging().getTotalUnreadCount();
        String unreadStr = unreadCount > 0 ? " (" + unreadCount + " new)" : "";
        logInfo("  17. 💬 Conversations{}", unreadStr);
        logInfo("  18. 🔔 Notifications");
        logInfo("  19. 🤝 Friend Requests");
        logInfo("  20. 🌟 View Standouts");
        logInfo("  0. Exit");
        logInfo(CliConstants.SEPARATOR_LINE + "\n");
    }

    private static void shutdown() {
        AppBootstrap.shutdown();
    }
}
