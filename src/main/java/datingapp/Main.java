package datingapp;

import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.app.cli.CliTextAndInput;
import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.cli.MainMenuRegistry;
import datingapp.app.cli.MatchingHandler;
import datingapp.app.cli.MessagingHandler;
import datingapp.app.cli.ProfileHandler;
import datingapp.app.cli.SafetyHandler;
import datingapp.app.cli.StatsHandler;
import datingapp.core.AppSession;
import datingapp.core.LoggingSupport;
import datingapp.core.ServiceRegistry;
import datingapp.core.matching.RecommendationService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.model.User;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
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
            } catch (Throwable _) { // NOPMD AvoidCatchingThrowable - MethodHandle.invoke() declares throws
                // Throwable
                // FFM unavailable — try subprocess fallback
                try {
                    new ProcessBuilder("cmd", "/c", "chcp 65001 >nul")
                            .inheritIO()
                            .start()
                            .waitFor();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt(); // Restore interrupt flag
                } catch (Exception _) {
                    // Best-effort: console codepage not critical for app functionality
                    assert true; // PMD: non-empty catch block
                }
            }
        }
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Main() {
        /* Utility class with only static methods */
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            runWithShutdown(
                    () -> {
                        ServiceRegistry services = ApplicationStartup.initialize();
                        InputReader inputReader = new InputReader(scanner);
                        AppSession session = AppSession.getInstance();

                        ProfileHandler profileHandler = ProfileHandler.fromServices(services, session, inputReader);
                        MatchingHandler matchingHandler = new MatchingHandler(MatchingHandler.Dependencies.fromServices(
                                services, session, inputReader, profileHandler::completeProfile));
                        SafetyHandler safetyHandler = SafetyHandler.fromServices(services, session, inputReader);
                        StatsHandler statsHandler = StatsHandler.fromServices(services, session, inputReader);
                        MessagingHandler messagingHandler =
                                MessagingHandler.fromServices(services, session, inputReader);
                        MainMenuRegistry menuRegistry = createMainMenuRegistry(
                                matchingHandler, profileHandler, safetyHandler, statsHandler, messagingHandler);

                        logInfo("\n🌹 Welcome to Dating App 🌹\n");

                        boolean running = true;
                        while (running) {
                            MainMenuRegistry.MenuRenderContext menuRenderContext =
                                    new MainMenuRegistry.MenuRenderContext(messagingHandler.getTotalUnreadCount());
                            printMenu(services, session, menuRegistry, menuRenderContext);
                            String choice = inputReader.readLine("Choose an option: ");

                            if (shouldExitMainMenu(inputReader)) {
                                logInfo("\n👋 Goodbye!\n");
                                break;
                            }

                            var option = menuRegistry.findOption(choice);
                            if (option.isEmpty()) {
                                logInfo(CliTextAndInput.INVALID_SELECTION);
                            } else if (option.orElseThrow().requiresLogin() && !session.isLoggedIn()) {
                                logInfo(CliTextAndInput.PLEASE_SELECT_USER);
                            } else {
                                MainMenuRegistry.DispatchResult dispatchResult =
                                        option.orElseThrow().action().execute();
                                running = dispatchResult != MainMenuRegistry.DispatchResult.EXIT;
                            }
                        }
                    },
                    ApplicationStartup::shutdown);
        }
    }

    static void runWithShutdown(Runnable action, Runnable shutdownAction) {
        try {
            action.run();
        } finally {
            shutdownAction.run();
        }
    }

    static boolean shouldExitMainMenu(InputReader inputReader) {
        return inputReader.wasInputExhausted();
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

    private static MainMenuRegistry.DispatchResult safeDispatch(Runnable action) {
        safeExecute(action);
        return MainMenuRegistry.DispatchResult.CONTINUE;
    }

    private static MainMenuRegistry createMainMenuRegistry(
            MatchingHandler matchingHandler,
            ProfileHandler profileHandler,
            SafetyHandler safetyHandler,
            StatsHandler statsHandler,
            MessagingHandler messagingHandler) {
        return MainMenuRegistry.createDefault(Map.ofEntries(
                Map.entry("1", () -> safeDispatch(profileHandler::createUser)),
                Map.entry("2", () -> safeDispatch(profileHandler::selectUser)),
                Map.entry("3", () -> safeDispatch(profileHandler::completeProfile)),
                Map.entry("4", () -> safeDispatch(matchingHandler::browseCandidates)),
                Map.entry("5", () -> safeDispatch(matchingHandler::viewMatches)),
                Map.entry("6", () -> safeDispatch(safetyHandler::blockUser)),
                Map.entry("7", () -> safeDispatch(safetyHandler::reportUser)),
                Map.entry("8", () -> safeDispatch(safetyHandler::manageBlockedUsers)),
                Map.entry("9", () -> safeDispatch(profileHandler::setDealbreakers)),
                Map.entry("10", () -> safeDispatch(statsHandler::viewStatistics)),
                Map.entry("11", () -> safeDispatch(profileHandler::previewProfile)),
                Map.entry("12", () -> safeDispatch(statsHandler::viewAchievements)),
                Map.entry("13", () -> safeDispatch(profileHandler::viewAllNotes)),
                Map.entry("14", () -> safeDispatch(profileHandler::viewProfileScore)),
                Map.entry("15", () -> safeDispatch(safetyHandler::verifyProfile)),
                Map.entry("16", () -> safeDispatch(matchingHandler::browseWhoLikedMe)),
                Map.entry("17", () -> safeDispatch(messagingHandler::showConversations)),
                Map.entry("18", () -> safeDispatch(matchingHandler::viewNotifications)),
                Map.entry("19", () -> safeDispatch(matchingHandler::viewPendingRequests)),
                Map.entry("20", () -> safeDispatch(matchingHandler::viewStandouts)),
                Map.entry("0", () -> {
                    logInfo("\n👋 Goodbye!\n");
                    return MainMenuRegistry.DispatchResult.EXIT;
                })));
    }

    private static void printMenu(
            ServiceRegistry services,
            AppSession session,
            MainMenuRegistry menuRegistry,
            MainMenuRegistry.MenuRenderContext menuRenderContext) {
        logInfo(CliTextAndInput.SEPARATOR_LINE);
        logInfo("         DATING APP - PHASE 0.5");
        logInfo(CliTextAndInput.SEPARATOR_LINE);

        User currentUser = session.getCurrentUser();

        if (currentUser != null) {
            logInfo("  Current User: {} ({})", currentUser.getName(), currentUser.getState());

            // Show active session info
            ActivityMetricsService sessionService = services.getActivityMetricsService();
            sessionService
                    .getCurrentSession(currentUser.getId())
                    .ifPresent(sessionInfo -> logInfo(
                            "  Session: {} swipes ({} likes, {} passes) | {} elapsed",
                            sessionInfo.getSwipeCount(),
                            sessionInfo.getLikeCount(),
                            sessionInfo.getPassCount(),
                            sessionInfo.getFormattedDuration()));

            // Show daily likes
            RecommendationService dailyService = services.getRecommendationService();
            RecommendationService.DailyStatus dailyStatus = dailyService.getStatus(currentUser.getId());
            if (dailyStatus.hasUnlimitedLikes()) {
                logInfo("  💝 Daily Likes: unlimited");
            } else {
                logInfo(
                        "  💝 Daily Likes: {}/{} remaining",
                        dailyStatus.likesRemaining(),
                        services.getConfig().matching().dailyLikeLimit());
            }

        } else {
            logInfo("  Current User: [None]");
        }
        logInfo("───────────────────────────────────────");
        for (String line : menuRegistry.renderOptionLines(menuRenderContext)) {
            logInfo(line);
        }
        logInfo(CliTextAndInput.SEPARATOR_LINE + "\n");
    }
}
