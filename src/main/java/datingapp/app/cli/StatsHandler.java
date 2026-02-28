package datingapp.app.cli;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.ProfileUseCases.AchievementsQuery;
import datingapp.app.usecase.profile.ProfileUseCases.StatsQuery;
import datingapp.core.AppSession;
import datingapp.core.LoggingSupport;
import datingapp.core.ServiceRegistry;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.model.User;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles display of user statistics and achievements in the CLI. */
public class StatsHandler implements LoggingSupport {
    private static final Logger logger = LoggerFactory.getLogger(StatsHandler.class);
    private static final String PRESS_ENTER_PROMPT = "Press Enter to return...";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final ProfileUseCases profileUseCases;
    private final AppSession session;
    private final InputReader inputReader;

    public StatsHandler(ProfileUseCases profileUseCases, AppSession session, InputReader inputReader) {
        this.profileUseCases = java.util.Objects.requireNonNull(profileUseCases, "profileUseCases cannot be null");
        this.session = java.util.Objects.requireNonNull(session, "session cannot be null");
        this.inputReader = java.util.Objects.requireNonNull(inputReader, "inputReader cannot be null");
    }

    public static StatsHandler fromServices(ServiceRegistry services, AppSession session, InputReader inputReader) {
        java.util.Objects.requireNonNull(services, "services cannot be null");
        return new StatsHandler(services.getProfileUseCases(), session, inputReader);
    }

    @Override
    public Logger logger() {
        return logger;
    }

    /**
     * Displays comprehensive user statistics including activity, matches, and
     * scores.
     */
    public void viewStatistics() {
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            logInfo("\n" + CliTextAndInput.SEPARATOR_LINE);
            logInfo(CliTextAndInput.HEADER_YOUR_STATISTICS);
            logInfo(CliTextAndInput.SEPARATOR_LINE + "\n");

            var statsResult = profileUseCases.getOrComputeStats(new StatsQuery(UserContext.cli(currentUser.getId())));
            if (!statsResult.success()) {
                logInfo("❌ {}\n", statsResult.error().message());
                inputReader.readLine(PRESS_ENTER_PROMPT);
                return;
            }

            UserStats stats = statsResult.data();
            String computedAt = stats.computedAt() == null ? "N/A" : DATE_TIME_FORMATTER.format(stats.computedAt());
            logInfo("  Last updated: {}\n", computedAt);

            printActivitySection(stats);
            printMatchesSection(stats);
            printScoresSection(stats);
            printSafetySection(stats);

            inputReader.readLine(PRESS_ENTER_PROMPT);
        });
    }

    /** Displays all unlocked achievements for the current user. */
    public void viewAchievements() {
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            var achievementsResult =
                    profileUseCases.getAchievements(new AchievementsQuery(UserContext.cli(currentUser.getId()), true));
            if (!achievementsResult.success()) {
                logInfo("\n❌ {}\n", achievementsResult.error().message());
                inputReader.readLine(PRESS_ENTER_PROMPT);
                return;
            }
            var snapshot = achievementsResult.data();
            final List<UserAchievement> unlocked = snapshot.unlocked();

            printNewAchievements(snapshot.newlyUnlocked());

            logInfo("\n" + CliTextAndInput.SEPARATOR_LINE);
            logInfo(CliTextAndInput.HEADER_YOUR_ACHIEVEMENTS);
            logInfo(CliTextAndInput.SEPARATOR_LINE + "\n");

            if (unlocked.isEmpty()) {
                logInfo("  No achievements yet. Keep swiping!\n");
            } else {
                logInfo("  Unlocked: {} / ???\n", unlocked.size());
                // I don't know total count easily without asking service for all definitions.

                for (UserAchievement ua : unlocked) {
                    logInfo(
                            "  ✅ {} - {}",
                            ua.achievement().getDisplayName(),
                            ua.achievement().getDescription());
                    // Date formatting?
                    String dateStr = ua.unlockedAt() == null ? "N/A" : DATE_FORMATTER.format(ua.unlockedAt());
                    logInfo("     (Unlocked: {})", dateStr);
                }
                logInfo("");
            }

            inputReader.readLine(PRESS_ENTER_PROMPT);
        });
    }

    private void printActivitySection(UserStats stats) {
        logInfo(CliTextAndInput.STATS_ACTIVITY);
        logInfo(CliTextAndInput.SECTION_LINE);
        logInfo(
                "  Swipes given:     {} ({} likes, {} passes)",
                stats.totalSwipesGiven(),
                stats.likesGiven(),
                stats.passesGiven());
        logInfo("  Like ratio:       {} {}", stats.getLikeRatioDisplay(), describeLikeRatio(stats.likeRatio()));
        logInfo(
                "  Swipes received:  {} ({} likes, {} passes)\n",
                stats.totalSwipesReceived(),
                stats.likesReceived(),
                stats.passesReceived());
    }

    private void printMatchesSection(UserStats stats) {
        logInfo(CliTextAndInput.STATS_MATCHES);
        logInfo(CliTextAndInput.SECTION_LINE);
        logInfo("  Total matches:    {}", stats.totalMatches());
        logInfo("  Active matches:   {}", stats.activeMatches());
        logInfo("  Match rate:       {} {}\n", stats.getMatchRateDisplay(), describeMatchRate(stats.matchRate()));
    }

    private void printScoresSection(UserStats stats) {
        logInfo(CliTextAndInput.STATS_SCORES);
        logInfo(CliTextAndInput.SECTION_LINE);
        logInfo("  Reciprocity:      {} (of your likes, liked you back)", stats.getReciprocityDisplay());
        logInfo("  Selectiveness:    {}", describeSelectiveness(stats.selectivenessScore()));
        logInfo("  Attractiveness:   {}\n", describeAttractiveness(stats.attractivenessScore()));
    }

    private void printSafetySection(UserStats stats) {
        boolean hasSafetyActivity = stats.blocksGiven() > 0
                || stats.blocksReceived() > 0
                || stats.reportsGiven() > 0
                || stats.reportsReceived() > 0;
        if (!hasSafetyActivity) {
            return;
        }
        logInfo(CliTextAndInput.STATS_SAFETY);
        logInfo(CliTextAndInput.SECTION_LINE);
        logInfo("  Blocks: {} given | {} received", stats.blocksGiven(), stats.blocksReceived());
        logInfo("  Reports: {} given | {} received\n", stats.reportsGiven(), stats.reportsReceived());
    }

    private void printNewAchievements(List<UserAchievement> newlyUnlocked) {
        if (newlyUnlocked.isEmpty()) {
            return;
        }
        logInfo("\n🏆 NEW ACHIEVEMENTS UNLOCKED! 🏆");
        for (UserAchievement ua : newlyUnlocked) {
            logInfo(
                    "  ✨ {} - {}",
                    ua.achievement().getDisplayName(),
                    ua.achievement().getDescription());
        }
        logInfo("");
    }

    private static String describeLikeRatio(double likeRatio) {
        if (likeRatio > 0.7) {
            return "(you like most people)";
        }
        if (likeRatio < 0.3) {
            return "(you're selective)";
        }
        return "";
    }

    private static String describeMatchRate(double matchRate) {
        if (matchRate > 0.2) {
            return "(above average!)";
        }
        if (matchRate < 0.1) {
            return "(below average)";
        }
        return "";
    }

    private static String describeSelectiveness(double score) {
        if (score > 0.6) {
            return "Above average (selective)";
        }
        if (score < 0.4) {
            return "Below average (open-minded)";
        }
        return "Average";
    }

    private static String describeAttractiveness(double score) {
        if (score > 0.6) {
            return "Above average";
        }
        if (score < 0.4) {
            return "Below average";
        }
        return "Average";
    }
}
