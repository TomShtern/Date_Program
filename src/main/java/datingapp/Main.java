package datingapp;

import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.app.cli.connection.MessagingHandler;
import datingapp.app.cli.matching.MatchingHandler;
import datingapp.app.cli.metrics.StatsHandler;
import datingapp.app.cli.profile.ProfileHandler;
import datingapp.app.cli.safety.SafetyHandler;
import datingapp.app.cli.shared.CliTextAndInput;
import datingapp.app.cli.shared.CliTextAndInput.InputReader;
import datingapp.core.AppSession;
import datingapp.core.LoggingSupport;
import datingapp.core.ServiceRegistry;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.model.User;
import datingapp.core.profile.ValidationService;
import datingapp.core.recommendation.RecommendationService;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console-based dating app - Phase 0.5. Main entry point with interactive menu.
 * Refactored to
 * delegate logic to specialized handlers.
 */
public final class Main {

    // Must run BEFORE logger init so Logback captures the UTF-8 streams
    static {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            try {
                // Direct Win32 API call via FFM — sets code page in THIS process's console
                var kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
                var sig = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
                var linker = Linker.nativeLinker();
                linker.downcallHandle(kernel32.find("SetConsoleOutputCP").orElseThrow(), sig)
                        .invoke(65001);
                linker.downcallHandle(kernel32.find("SetConsoleCP").orElseThrow(), sig)
                        .invoke(65001);
            } catch (Throwable throwable) { // NOPMD AvoidCatchingThrowable - MethodHandle.invoke() declares throws
                // Throwable
                // FFM unavailable — try subprocess fallback
                try {
                    new ProcessBuilder("cmd", "/c", "chcp 65001 >nul")
                            .inheritIO()
                            .start()
                            .waitFor();
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt(); // Restore interrupt flag
                } catch (Exception exception) {
                    // Best-effort: console codepage not critical for app functionality
                    assert true; // PMD: non-empty catch block
                }
            }
        }
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Application context
    private static ServiceRegistry services;

    // CLI Components
    private static InputReader inputReader;
    private static MatchingHandler matchingHandler;
    private static ProfileHandler profileHandler;
    private static SafetyHandler safetyHandler;
    private static StatsHandler statsHandler;
    private static MessagingHandler messagingHandler;

    private Main() {
        /* Utility class with only static methods */
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            initializeApp(scanner);

            logInfo("\n🌹 Welcome to Dating App 🌹\n");

            boolean running = true;
            while (running) {
                printMenu();
                String choice = inputReader.readLine("Choose an option: ");

                switch (choice) {
                    case "1" -> safeExecute(profileHandler::createUser);
                    case "2" -> safeExecute(profileHandler::selectUser);
                    case "3" -> safeExecute(profileHandler::completeProfile);
                    case "4" -> safeExecute(matchingHandler::browseCandidates);
                    case "5" -> safeExecute(matchingHandler::viewMatches);
                    case "6" -> safeExecute(safetyHandler::blockUser);
                    case "7" -> safeExecute(safetyHandler::reportUser);
                    case "8" -> safeExecute(safetyHandler::manageBlockedUsers);
                    case "9" -> safeExecute(profileHandler::setDealbreakers);
                    case "10" -> safeExecute(statsHandler::viewStatistics);
                    case "11" -> safeExecute(profileHandler::previewProfile);
                    case "12" -> safeExecute(statsHandler::viewAchievements);
                    case "13" -> safeExecute(profileHandler::viewAllNotes);
                    case "14" -> safeExecute(profileHandler::viewProfileScore);
                    case "15" -> safeExecute(safetyHandler::verifyProfile);
                    case "16" -> safeExecute(matchingHandler::browseWhoLikedMe);
                    case "17" -> safeExecute(messagingHandler::showConversations);
                    case "18" -> safeExecute(matchingHandler::viewNotifications);
                    case "19" -> safeExecute(matchingHandler::viewPendingRequests);
                    case "20" -> safeExecute(matchingHandler::viewStandouts);
                    case "0" -> {
                        running = false;
                        logInfo("\n👋 Goodbye!\n");
                    }
                    default -> logInfo(CliTextAndInput.INVALID_SELECTION);
                }
            }

            shutdown();
        }
    }

    private static void initializeApp(Scanner scanner) {
        // Initialize application with default configuration
        services = ApplicationStartup.initialize();

        // Initialize CLI Infrastructure
        inputReader = new InputReader(scanner);

        AppSession session = AppSession.getInstance();
        matchingHandler = new MatchingHandler(new MatchingHandler.Dependencies(
                services.getCandidateFinder(),
                services.getMatchingService(),
                services.getInteractionStorage(),
                services.getRecommendationService(),
                services.getUndoService(),
                services.getMatchQualityService(),
                services.getUserStorage(),
                services.getProfileService(),
                services.getAnalyticsStorage(),
                services.getTrustSafetyService(),
                services.getConnectionService(),
                services.getRecommendationService(),
                services.getCommunicationStorage(),
                session,
                inputReader));
        profileHandler = new ProfileHandler(
                services.getUserStorage(),
                services.getProfileService(),
                services.getProfileService(),
                new ValidationService(services.getConfig()),
                session,
                inputReader);
        safetyHandler =
                new SafetyHandler(services.getUserStorage(), services.getTrustSafetyService(), session, inputReader);
        statsHandler = new StatsHandler(
                services.getActivityMetricsService(), services.getProfileService(), session, inputReader);
        messagingHandler = new MessagingHandler(
                services.getConnectionService(),
                services.getInteractionStorage(),
                services.getTrustSafetyService(),
                inputReader,
                session);
    }

    private static void logInfo(String message, Object... args) {
        LoggingSupport.logInfo(logger, message, args);
    }

    private static void safeExecute(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LoggingSupport.logWarn(logger, "\n❌ An error occurred: {}\n", e.getMessage());
            LoggingSupport.logDebug(logger, "Handler error details", e);
        }
    }

    private static void printMenu() {
        logInfo(CliTextAndInput.SEPARATOR_LINE);
        logInfo("         DATING APP - PHASE 0.5");
        logInfo(CliTextAndInput.SEPARATOR_LINE);

        User currentUser = AppSession.getInstance().getCurrentUser();

        if (currentUser != null) {
            logInfo("  Current User: {} ({})", currentUser.getName(), currentUser.getState());

            // Show active session info
            ActivityMetricsService sessionService = services.getActivityMetricsService();
            sessionService
                    .getCurrentSession(currentUser.getId())
                    .ifPresent(session -> logInfo(
                            "  Session: {} swipes ({} likes, {} passes) | {} elapsed",
                            session.getSwipeCount(),
                            session.getLikeCount(),
                            session.getPassCount(),
                            session.getFormattedDuration()));

            // Show daily likes
            RecommendationService dailyService = services.getRecommendationService();
            RecommendationService.DailyStatus dailyStatus = dailyService.getStatus(currentUser.getId());
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
        int unreadCount = messagingHandler.getTotalUnreadCount();
        String unreadStr = unreadCount > 0 ? " (" + unreadCount + " new)" : "";
        logInfo("  17. 💬 Conversations{}", unreadStr);
        logInfo("  18. 🔔 Notifications");
        logInfo("  19. 🤝 Friend Requests");
        logInfo("  20. 🌟 View Standouts");
        logInfo("  0. Exit");
        logInfo(CliTextAndInput.SEPARATOR_LINE + "\n");
    }

    private static void shutdown() {
        ApplicationStartup.shutdown();
    }
}
