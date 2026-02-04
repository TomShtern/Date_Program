package datingapp.app.cli;

import datingapp.core.Achievement.UserAchievement;
import datingapp.core.AchievementService;
import datingapp.core.AppSession;
import datingapp.core.Stats.UserStats;
import datingapp.core.StatsService;
import datingapp.core.User;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles display of user statistics and achievements in the CLI. */
public class StatsHandler {
    private static final Logger logger = LoggerFactory.getLogger(StatsHandler.class);

    private final StatsService statsService;
    private final AchievementService achievementService;
    private final AppSession session;
    private final InputReader inputReader;

    public StatsHandler(
            StatsService statsService,
            AchievementService achievementService,
            AppSession session,
            InputReader inputReader) {
        this.statsService = statsService;
        this.achievementService = achievementService;
        this.session = session;
        this.inputReader = inputReader;
    }

    /**
     * Displays comprehensive user statistics including activity, matches, and
     * scores.
     */
    public void viewStatistics() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            logger.info("\n" + CliConstants.SEPARATOR_LINE);
            logger.info(CliConstants.HEADER_YOUR_STATISTICS);
            logger.info(CliConstants.SEPARATOR_LINE + "\n");

            UserStats stats = statsService.getOrComputeStats(currentUser.getId());
            String computedAt = stats.computedAt().toString().substring(0, 16).replace("T", " ");
            logger.info("  Last updated: {}\n", computedAt);

            // Activity section
            logger.info(CliConstants.STATS_ACTIVITY);
            logger.info(CliConstants.SECTION_LINE);
            logger.info(
                    "  Swipes given:     {} ({} likes, {} passes)",
                    stats.totalSwipesGiven(),
                    stats.likesGiven(),
                    stats.passesGiven());

            String likeRatioDesc = "";
            if (stats.likeRatio() > 0.7) {
                likeRatioDesc = "(you like most people)";
            } else if (stats.likeRatio() < 0.3) {
                likeRatioDesc = "(you're selective)";
            }

            logger.info("  Like ratio:       {} {}", stats.getLikeRatioDisplay(), likeRatioDesc);
            logger.info(
                    "  Swipes received:  {} ({} likes, {} passes)\n",
                    stats.totalSwipesReceived(),
                    stats.likesReceived(),
                    stats.passesReceived());

            // Matches section
            logger.info(CliConstants.STATS_MATCHES);
            logger.info(CliConstants.SECTION_LINE);
            logger.info("  Total matches:    {}", stats.totalMatches());
            logger.info("  Active matches:   {}", stats.activeMatches());

            String matchRateDesc = "";
            if (stats.matchRate() > 0.2) {
                matchRateDesc = "(above average!)";
            } else if (stats.matchRate() < 0.1) {
                matchRateDesc = "(below average)";
            }

            logger.info("  Match rate:       {} {}\n", stats.getMatchRateDisplay(), matchRateDesc);

            // Scores section
            logger.info(CliConstants.STATS_SCORES);
            logger.info(CliConstants.SECTION_LINE);
            logger.info("  Reciprocity:      {} (of your likes, liked you back)", stats.getReciprocityDisplay());

            String selectivenessDesc = "Average";
            if (stats.selectivenessScore() > 0.6) {
                selectivenessDesc = "Above average (selective)";
            } else if (stats.selectivenessScore() < 0.4) {
                selectivenessDesc = "Below average (open-minded)";
            }

            logger.info("  Selectiveness:    {}", selectivenessDesc);

            String attractivenessDesc = "Average";
            if (stats.attractivenessScore() > 0.6) {
                attractivenessDesc = "Above average";
            } else if (stats.attractivenessScore() < 0.4) {
                attractivenessDesc = "Below average";
            }

            logger.info("  Attractiveness:   {}\n", attractivenessDesc);

            // Safety section (only show if there's activity)
            if (stats.blocksGiven() > 0
                    || stats.blocksReceived() > 0
                    || stats.reportsGiven() > 0
                    || stats.reportsReceived() > 0) {
                logger.info(CliConstants.STATS_SAFETY);
                logger.info(CliConstants.SECTION_LINE);
                logger.info("  Blocks: {} given | {} received", stats.blocksGiven(), stats.blocksReceived());
                logger.info("  Reports: {} given | {} received\n", stats.reportsGiven(), stats.reportsReceived());
            }

            inputReader.readLine("Press Enter to return...");
        });
    }

    /** Displays all unlocked achievements for the current user. */
    public void viewAchievements() {
        CliUtilities.requireLogin(() -> {
            // Check for any new achievements first
            checkAndDisplayNewAchievements(session.getCurrentUser());

            User currentUser = session.getCurrentUser();
            final List<UserAchievement> unlocked = achievementService.getUnlocked(currentUser.getId());

            logger.info("\n" + CliConstants.SEPARATOR_LINE);
            logger.info(CliConstants.HEADER_YOUR_ACHIEVEMENTS);
            logger.info(CliConstants.SEPARATOR_LINE + "\n");

            if (unlocked.isEmpty()) {
                logger.info("  No achievements yet. Keep swiping!\n");
            } else {
                logger.info("  Unlocked: {} / ???\n", unlocked.size());
                // I don't know total count easily without asking service for all definitions.

                for (UserAchievement ua : unlocked) {
                    logger.info(
                            "  ✅ {} - {}",
                            ua.achievement().getDisplayName(),
                            ua.achievement().getDescription());
                    // Date formatting?
                    String dateStr = ua.unlockedAt().toString().substring(0, 10);
                    logger.info("     (Unlocked: {})", dateStr);
                }
                logger.info("");
            }

            inputReader.readLine("Press Enter to return...");
        });
    }

    /** Checks for new achievements and displays them if any were unlocked. */
    private void checkAndDisplayNewAchievements(User currentUser) {
        List<UserAchievement> newAchievements = achievementService.checkAndUnlock(currentUser.getId());
        if (!newAchievements.isEmpty()) {
            logger.info("\n🏆 NEW ACHIEVEMENTS UNLOCKED! 🏆");
            for (UserAchievement ua : newAchievements) {
                logger.info(
                        "  ✨ {} - {}",
                        ua.achievement().getDisplayName(),
                        ua.achievement().getDescription());
            }
            logger.info("");
        }
    }
}
