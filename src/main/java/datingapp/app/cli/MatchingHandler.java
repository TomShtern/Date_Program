package datingapp.app.cli;

import datingapp.core.Achievement;
import datingapp.core.AchievementService;
import datingapp.core.AppSession;
import datingapp.core.CandidateFinder;
import datingapp.core.CandidateFinder.GeoUtils;
import datingapp.core.DailyPick;
import datingapp.core.DailyService;
import datingapp.core.Match;
import datingapp.core.MatchQualityService;
import datingapp.core.MatchQualityService.InterestMatcher;
import datingapp.core.MatchQualityService.MatchQuality;
import datingapp.core.MatchingService;
import datingapp.core.RelationshipTransitionService;
import datingapp.core.RelationshipTransitionService.TransitionValidationException;
import datingapp.core.Standout;
import datingapp.core.StandoutsService;
import datingapp.core.UndoService;
import datingapp.core.User;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserState;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.StatsStorage;
import datingapp.core.storage.UserStorage;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for matching-related CLI operations. Manages swiping, match
 * discovery, daily picks, and
 * match quality display.
 */
public class MatchingHandler {
    private static final Logger logger = LoggerFactory.getLogger(MatchingHandler.class);

    private final CandidateFinder candidateFinderService;
    private final MatchingService matchingService;
    private final MatchStorage matchStorage;
    private final BlockStorage blockStorage;
    private final DailyService dailyService;
    private final UndoService undoService;
    private final MatchQualityService matchQualityService;
    private final UserStorage userStorage;
    private final AchievementService achievementService;
    private final StatsStorage statsStorage;
    private final RelationshipTransitionService transitionService;
    private final StandoutsService standoutsService;
    private final AppSession session;
    private final InputReader inputReader;

    public MatchingHandler(Dependencies dependencies) {
        this.candidateFinderService = dependencies.candidateFinderService();
        this.matchingService = dependencies.matchingService();
        this.matchStorage = dependencies.matchStorage();
        this.blockStorage = dependencies.blockStorage();
        this.dailyService = dependencies.dailyService();
        this.undoService = dependencies.undoService();
        this.matchQualityService = dependencies.matchQualityService();
        this.userStorage = dependencies.userStorage();
        this.achievementService = dependencies.achievementService();
        this.statsStorage = dependencies.statsStorage();
        this.transitionService = dependencies.transitionService();
        this.standoutsService = dependencies.standoutsService();
        this.session = dependencies.userSession();
        this.inputReader = dependencies.inputReader();
    }

    public record Dependencies(
            CandidateFinder candidateFinderService,
            MatchingService matchingService,
            MatchStorage matchStorage,
            BlockStorage blockStorage,
            DailyService dailyService,
            UndoService undoService,
            MatchQualityService matchQualityService,
            UserStorage userStorage,
            AchievementService achievementService,
            StatsStorage statsStorage,
            RelationshipTransitionService transitionService,
            StandoutsService standoutsService,
            AppSession userSession,
            InputReader inputReader) {

        public Dependencies {
            Objects.requireNonNull(candidateFinderService);
            Objects.requireNonNull(matchingService);
            Objects.requireNonNull(matchStorage);
            Objects.requireNonNull(blockStorage);
            Objects.requireNonNull(dailyService);
            Objects.requireNonNull(undoService);
            Objects.requireNonNull(matchQualityService);
            Objects.requireNonNull(userStorage);
            Objects.requireNonNull(achievementService);
            Objects.requireNonNull(statsStorage);
            Objects.requireNonNull(transitionService);
            Objects.requireNonNull(standoutsService);
            Objects.requireNonNull(userSession);
            Objects.requireNonNull(inputReader);
        }
    }

    /**
     * Displays the candidate browsing interface, showing potential matches to the
     * user. Handles daily
     * picks, candidate filtering, and user interactions (like/pass).
     */
    public void browseCandidates() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            if (currentUser.getState() != UserState.ACTIVE) {
                logInfo("\n⚠️  You must be ACTIVE to browse candidates. Complete your profile first.\n");
                return;
            }

            // Check for daily pick first
            Optional<DailyPick> dailyPick = dailyService.getDailyPick(currentUser);
            if (dailyPick.isPresent() && !dailyService.hasViewedDailyPick(currentUser.getId())) {
                showDailyPick(dailyPick.get(), currentUser);
            }

            logInfo("\n" + CliConstants.HEADER_BROWSE_CANDIDATES + "\n");

            List<User> candidates = candidateFinderService.findCandidatesForUser(currentUser);

            if (candidates.isEmpty()) {
                logInfo("😔 No candidates found. Try again later!\n");
                return;
            }

            for (User candidate : candidates) {
                boolean keepBrowsing = processCandidateInteraction(candidate, currentUser);
                if (!keepBrowsing) {
                    break;
                }
            }
        });
    }

    private boolean processCandidateInteraction(User candidate, User currentUser) {
        // Record this profile view
        statsStorage.recordProfileView(currentUser.getId(), candidate.getId());

        double distance = GeoUtils.distanceKm(
                currentUser.getLat(), currentUser.getLon(),
                candidate.getLat(), candidate.getLon());

        logInfo(CliConstants.BOX_TOP);
        boolean verified = candidate.isVerified();
        logInfo(
                "│ 💝 {}{}{}, {} years old",
                candidate.getName(),
                verified ? " " : "",
                verified ? "✅ Verified" : "",
                candidate.getAge());
        if (logger.isInfoEnabled()) {
            logInfo("│ 📍 {} km away", String.format("%.1f", distance));
        }
        logInfo(CliConstants.PROFILE_BIO_FORMAT, candidate.getBio() != null ? candidate.getBio() : "(no bio)");

        logSharedInterests(currentUser, candidate);

        logInfo(CliConstants.BOX_BOTTOM);

        // Validate input and re-prompt until valid choice is entered
        String action = null;
        while (action == null) {
            String input = inputReader.readLine(CliConstants.PROMPT_LIKE_PASS_QUIT);
            Optional<String> validated = CliUtilities.validateChoice(input, "l", "p", "q");
            if (validated.isEmpty()) {
                logInfo("❌ Invalid choice. Please enter L (like), P (pass), or Q (quit).");
            } else {
                action = validated.get();
            }
        }

        if ("q".equals(action)) {
            logInfo(CliConstants.MSG_STOPPING_BROWSE);
            return false;
        }

        MatchingService.SwipeResult result = matchingService.processSwipe(currentUser, candidate, "l".equals(action));

        if (!result.success()) {
            showDailyLimitReached(currentUser);
            return false;
        }

        if (result.matched()) {
            logInfo("\n🎉🎉🎉 IT'S A MATCH! 🎉🎉🎉");
            logInfo("You and {} like each other!\n", candidate.getName());
            checkAndDisplayNewAchievements(currentUser);
        } else if (result.like().direction() == Like.Direction.LIKE) {
            logInfo("❤️  Liked!\n");
        } else {
            logInfo("👋 Passed.\n");
        }
        promptUndo(candidate.getName(), currentUser);

        return true;
    }

    /**
     * Displays the user's active matches with compatibility scores and options to
     * view details,
     * unmatch, or block matches.
     */
    public void viewMatches() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            logInfo("\n" + CliConstants.SEPARATOR_LINE);
            logInfo("         YOUR MATCHES");
            logInfo(CliConstants.SEPARATOR_LINE + "\n");

            List<Match> matches = matchStorage.getActiveMatchesFor(currentUser.getId());

            if (matches.isEmpty()) {
                logInfo("😢 No matches yet. Keep swiping!\n");
                return;
            }

            logInfo("💕 You have {} active match(es):\n", matches.size());

            for (int i = 0; i < matches.size(); i++) {
                Match match = matches.get(i);
                UUID otherUserId = match.getOtherUser(currentUser.getId());
                User otherUser = userStorage.get(otherUserId);

                if (otherUser != null && logger.isInfoEnabled()) {
                    MatchQuality quality = matchQualityService.computeQuality(match, currentUser.getId());
                    String verifiedBadge = otherUser.isVerified() ? " ✅ Verified" : "";
                    logInfo(
                            "  {}. {} {}{}, {}         {} {}%",
                            i + 1,
                            quality.getStarDisplay(),
                            otherUser.getName(),
                            verifiedBadge,
                            otherUser.getAge(),
                            " ".repeat(Math.max(0, 10 - otherUser.getName().length())),
                            quality.compatibilityScore());
                    logInfo("     \"{}\"", quality.getShortSummary());
                }
            }

            logInfo(CliConstants.MENU_DIVIDER_WITH_NEWLINES);
            logInfo("  " + CliConstants.PROMPT_VIEW_UNMATCH_BLOCK);
            String action = inputReader.readLine("\nYour choice: ").toLowerCase(Locale.ROOT);

            switch (action) {
                case "v" -> viewMatchDetails(matches, currentUser);
                case "u" -> unmatchFromList(matches, currentUser);
                case "b" -> blockFromMatches(matches, currentUser);
                default -> {
                    /* back */
                }
            }
        });
    }

    private void viewMatchDetails(List<Match> matches, User currentUser) {
        String input = inputReader.readLine("Enter match number to view: ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= matches.size()) {
                logInfo(CliConstants.INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage.get(otherUserId);
            MatchQuality quality = matchQualityService.computeQuality(match, currentUser.getId());

            displayMatchQuality(otherUser, quality);

            logInfo(CliConstants.MENU_DIVIDER_WITH_NEWLINES);
            logInfo("  (U)nmatch | (B)lock | (F)riend Zone | (G)raceful Exit | back");
            String action = inputReader.readLine("  Your choice: ").toLowerCase(Locale.ROOT);

            handleMatchDetailAction(action, match, otherUser, otherUserId, currentUser);

        } catch (NumberFormatException _) {
            logInfo(CliConstants.INVALID_INPUT);
        }
    }

    private void displayMatchQuality(User otherUser, MatchQuality quality) {
        String nameUpper = otherUser.getName().toUpperCase(Locale.ROOT);
        logInfo("\n" + CliConstants.SEPARATOR_LINE);
        logInfo("         MATCH WITH {}", nameUpper);
        logInfo(CliConstants.SEPARATOR_LINE + "\n");

        logInfo("  👤 {}, {}", otherUser.getName(), otherUser.getAge());
        if (otherUser.getBio() != null) {
            logInfo("  📝 {}", otherUser.getBio());
        }
        double distanceKm = quality.distanceKm();
        if (distanceKm < 0) {
            logInfo("  📍 Distance unknown");
        } else {
            String distanceStr = String.format("%.1f", distanceKm);
            logInfo("  📍 {} km away", distanceStr);
        }

        logInfo("\n" + CliConstants.SECTION_LINE);
        logInfo("  COMPATIBILITY: {}%  {}", quality.compatibilityScore(), quality.getStarDisplay());
        logInfo("  {}", quality.getCompatibilityLabel());
        logInfo(CliConstants.SECTION_LINE);

        if (!quality.highlights().isEmpty()) {
            logInfo("\n  ✨ WHY YOU MATCHED");
            quality.highlights().forEach(h -> logInfo("  • {}", h));
        }

        displayScoreBreakdown(quality);

        if (!quality.lifestyleMatches().isEmpty()) {
            logInfo("\n  💫 LIFESTYLE ALIGNMENT");
            quality.lifestyleMatches().forEach(m -> logInfo("  • {}", m));
        }

        logInfo("\n  ⏱️  PACE SYNC: {}", quality.paceSyncLevel());
    }

    private void displayScoreBreakdown(MatchQuality quality) {
        if (!logger.isInfoEnabled()) {
            return;
        }

        String distanceBar = MatchQualityService.renderProgressBar(quality.distanceScore(), 12);
        String ageBar = MatchQualityService.renderProgressBar(quality.ageScore(), 12);
        String interestBar = MatchQualityService.renderProgressBar(quality.interestScore(), 12);
        String lifestyleBar = MatchQualityService.renderProgressBar(quality.lifestyleScore(), 12);
        String paceBar = MatchQualityService.renderProgressBar(quality.paceScore(), 12);
        String responseBar = MatchQualityService.renderProgressBar(quality.responseScore(), 12);

        logInfo("\n  📊 SCORE BREAKDOWN");
        logInfo(CliConstants.SECTION_LINE);
        logInfo("  Distance:      {} {}%", distanceBar, (int) (quality.distanceScore() * 100));
        logInfo("  Age match:     {} {}%", ageBar, (int) (quality.ageScore() * 100));
        logInfo("  Interests:     {} {}%", interestBar, (int) (quality.interestScore() * 100));
        logInfo("  Lifestyle:     {} {}%", lifestyleBar, (int) (quality.lifestyleScore() * 100));
        logInfo("  Pace/Sync:      {} {}%", paceBar, (int) (quality.paceScore() * 100));
        logInfo("  Response:      {} {}%", responseBar, (int) (quality.responseScore() * 100));
    }

    private void logSharedInterests(User currentUser, User candidate) {
        InterestMatcher.MatchResult matchResult =
                InterestMatcher.compare(currentUser.getInterests(), candidate.getInterests());
        if (!matchResult.hasSharedInterests() || !logger.isInfoEnabled()) {
            return;
        }

        String badge = getMutualInterestsBadge(matchResult.sharedCount());
        String sharedInterests = InterestMatcher.formatSharedInterests(matchResult.shared());
        logInfo(
                "│ {} {} shared interest{}: {}",
                badge,
                matchResult.sharedCount(),
                matchResult.sharedCount() > 1 ? "s" : "",
                sharedInterests);
    }

    private void handleMatchDetailAction(
            String action, Match match, User otherUser, UUID otherUserId, User currentUser) {
        switch (action) {
            case "u" -> {
                String confirm = inputReader.readLine("Unmatch with " + otherUser.getName() + "? (y/n): ");
                if ("y".equalsIgnoreCase(confirm)) {
                    match.unmatch(currentUser.getId());
                    matchStorage.update(match);
                    logInfo("✅ Unmatched with {}.\n", otherUser.getName());
                }
            }
            case "b" -> {
                String confirm = inputReader.readLine(
                        CliConstants.BLOCK_PREFIX + otherUser.getName() + CliConstants.CONFIRM_SUFFIX);
                if ("y".equalsIgnoreCase(confirm)) {
                    Block block = Block.create(currentUser.getId(), otherUserId);
                    blockStorage.save(block);
                    match.block(currentUser.getId());
                    matchStorage.update(match);
                    logInfo("🚫 Blocked {}. Match ended.\n", otherUser.getName());
                }
            }
            case "f" -> {
                logInfo("\nSending Friend Zone request to {}...", otherUser.getName());
                try {
                    transitionService.requestFriendZone(currentUser.getId(), otherUserId);
                    logInfo("✅ Friend request sent!\n");
                } catch (TransitionValidationException e) {
                    logInfo("❌ Failed: {}\n", e.getMessage());
                }
            }
            case "g" -> {
                String confirm = inputReader.readLine("Are you sure you want to exit gracefully? (y/n): ");
                if ("y".equalsIgnoreCase(confirm)) {
                    try {
                        transitionService.gracefulExit(currentUser.getId(), otherUserId);
                        logInfo("🕊️ Graceful exit successful. Match ended.\n");
                    } catch (TransitionValidationException e) {
                        logInfo("❌ Failed: {}\n", e.getMessage());
                    }
                }
            }
            default -> {
                /* No action for unrecognized input */ }
        }
    }

    private void unmatchFromList(List<Match> matches, User currentUser) {
        String input = inputReader.readLine("Enter match number to unmatch: ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= matches.size()) {
                logInfo(CliConstants.INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage.get(otherUserId);

            String confirm = inputReader.readLine("Unmatch with " + otherUser.getName() + "? (y/n): ");
            if ("y".equalsIgnoreCase(confirm)) {
                match.unmatch(currentUser.getId());
                matchStorage.update(match);
                logInfo("✅ Unmatched with {}.\n", otherUser.getName());
            } else {
                logInfo(CliConstants.CANCELLED);
            }
        } catch (NumberFormatException _) {
            logInfo(CliConstants.INVALID_INPUT);
        }
    }

    private void blockFromMatches(List<Match> matches, User currentUser) {
        String input = inputReader.readLine("Enter match number to block: ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= matches.size()) {
                logInfo(CliConstants.INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage.get(otherUserId);

            String confirm = inputReader.readLine(
                    CliConstants.BLOCK_PREFIX + otherUser.getName() + "? This will end your match. (y/n): ");
            if ("y".equalsIgnoreCase(confirm)) {
                Block block = Block.create(currentUser.getId(), otherUserId);
                blockStorage.save(block);
                match.block(currentUser.getId());
                matchStorage.update(match);
                logInfo("🚫 Blocked {}. Match ended.\n", otherUser.getName());
            } else {
                logInfo(CliConstants.CANCELLED);
            }
        } catch (NumberFormatException _) {
            logInfo(CliConstants.INVALID_INPUT);
        }
    }

    /**
     * Displays a message when the user has reached their daily like limit, showing
     * remaining time
     * until reset and tips for better matching.
     *
     * @param currentUser The user who reached the limit
     */
    private void showDailyLimitReached(User currentUser) {
        DailyService.DailyStatus status = dailyService.getStatus(currentUser.getId());
        String timeUntilReset = DailyService.formatDuration(dailyService.getTimeUntilReset());

        logInfo("\n" + CliConstants.SEPARATOR_LINE);
        logInfo("         💔 DAILY LIMIT REACHED");
        logInfo(CliConstants.SEPARATOR_LINE);
        logInfo("");
        logInfo("   You've used all {} likes for today!", status.likesUsed());
        logInfo("");
        logInfo("   Resets in: {}", timeUntilReset);
        logInfo("");
        logInfo("   Tips for tomorrow:");
        logInfo("   • Take time to read profiles");
        logInfo("   • Quality over quantity");
        logInfo("   • Check your matches!");
        logInfo("");
        inputReader.readLine("   [Press Enter to return to menu]");
        logInfo(CliConstants.SEPARATOR_LINE + "\n");
    }

    private void showDailyPick(DailyPick pick, User currentUser) {
        logInfo("\n" + CliConstants.SEPARATOR_LINE);
        logInfo("       🎲 YOUR DAILY PICK 🎲");
        logInfo(CliConstants.SEPARATOR_LINE);
        logInfo("");
        logInfo("  ✨ {}", pick.reason());
        logInfo("");

        User candidate = pick.user();
        double distance = GeoUtils.distanceKm(
                currentUser.getLat(), currentUser.getLon(),
                candidate.getLat(), candidate.getLon());

        logInfo(CliConstants.BOX_TOP);
        logInfo("│ 🎁 {}, {} years old", candidate.getName(), candidate.getAge());
        if (logger.isInfoEnabled()) {
            logInfo("│ 📍 {} km away", String.format("%.1f", distance));
        }
        logInfo(CliConstants.PROFILE_BIO_FORMAT, candidate.getBio() != null ? candidate.getBio() : "(no bio)");

        InterestMatcher.MatchResult matchResult =
                InterestMatcher.compare(currentUser.getInterests(), candidate.getInterests());
        if (!matchResult.shared().isEmpty() && logger.isInfoEnabled()) {
            String sharedInterests = InterestMatcher.formatSharedInterests(matchResult.shared());
            logInfo("│ ✨ You both like: {}", sharedInterests);
        }

        logInfo(CliConstants.BOX_BOTTOM);
        logInfo("");
        logInfo("  This pick resets tomorrow at midnight!");
        logInfo("");

        // Validate input and re-prompt until valid choice is entered
        String action = null;
        while (action == null) {
            String input = inputReader.readLine(CliConstants.PROMPT_LIKE_PASS_SKIP);
            Optional<String> validated = CliUtilities.validateChoice(input, "l", "p", "s");
            if (validated.isEmpty()) {
                logInfo("❌ Invalid choice. Please enter L (like), P (pass), or S (skip).");
            } else {
                action = validated.get();
            }
        }

        if ("s".equals(action)) {
            logInfo("  👋 You can see this pick again later today.\n");
            return;
        }

        // Only mark as viewed after valid action (not on skip)
        dailyService.markDailyPickViewed(currentUser.getId());

        MatchingService.SwipeResult result = matchingService.processSwipe(currentUser, candidate, "l".equals(action));

        if (!result.success()) {
            showDailyLimitReached(currentUser);
            return;
        }

        if (result.matched()) {
            logInfo("\n🎉🎉🎉 IT'S A MATCH WITH YOUR DAILY PICK! 🎉🎉🎉\n");
        } else if (result.like().direction() == Like.Direction.LIKE) {
            logInfo("❤️  Liked your daily pick!\n");
        } else {
            logInfo("👋 Passed on daily pick.\n");
        }
        promptUndo(candidate.getName(), currentUser);
    }

    private void promptUndo(String candidateName, User currentUser) {
        if (!undoService.canUndo(currentUser.getId())) {
            return;
        }

        int secondsLeft = undoService.getSecondsRemaining(currentUser.getId());
        String prompt = String.format("⏪ Undo last swipe? (%ds remaining) (Y/N): ", secondsLeft);
        String response = inputReader.readLine(prompt).toLowerCase(Locale.ROOT);

        if ("y".equals(response)) {
            UndoService.UndoResult result = undoService.undo(currentUser.getId());

            if (result.success()) {
                String directionStr = result.undoneSwipe().direction() == Like.Direction.LIKE ? "like" : "pass";
                logInfo("\n✅ Undone! Your {} on {} has been reversed.", directionStr, candidateName);

                if (result.matchDeleted()) {
                    logInfo("   (The match was also removed)\n");
                } else {
                    logInfo("");
                }
            } else {
                logInfo("\n❌ {}\n", result.message());
            }
        }
    }

    private void checkAndDisplayNewAchievements(User currentUser) {
        List<Achievement.UserAchievement> newAchievements = achievementService.checkAndUnlock(currentUser.getId());
        if (newAchievements.isEmpty()) {
            return;
        }

        logInfo("\n🏆 NEW ACHIEVEMENTS UNLOCKED! 🏆");
        for (Achievement.UserAchievement ua : newAchievements) {
            logInfo(
                    "  ✨ {} - {}",
                    ua.achievement().getDisplayName(),
                    ua.achievement().getDescription());
        }
        logInfo("");
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    /**
     * Returns an emoji badge based on the number of shared interests. More matches
     * = more exciting
     * badge!
     */
    private String getMutualInterestsBadge(int sharedCount) {
        if (sharedCount >= 5) {
            return "🎯🔥"; // Perfect match
        } else if (sharedCount >= 3) {
            return "🎯✨"; // Great match
        } else if (sharedCount >= 2) {
            return "🎯"; // Good match
        } else {
            return "✨"; // Some shared interests
        }
    }

    /**
     * Displays today's standout profiles - the top 10 high-quality matches.
     */
    public void viewStandouts() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            if (currentUser.getState() != UserState.ACTIVE) {
                logInfo("\n⚠️  You must be ACTIVE to view standouts. Complete your profile first.\n");
                return;
            }

            logInfo("\n🌟 === TODAY'S STANDOUTS === 🌟\n");

            StandoutsService.Result result = standoutsService.getStandouts(currentUser);

            if (result.isEmpty()) {
                logInfo(result.message() != null ? result.message() : "No standouts available today.");
                logInfo("\n");
                return;
            }

            if (result.fromCache()) {
                logInfo("(Cached from earlier today - refreshes at midnight)\n");
            }

            List<Standout> standouts = result.standouts();
            java.util.Map<UUID, User> users = standoutsService.resolveUsers(standouts);

            logInfo("Your top {} matches from {} candidates:\n", standouts.size(), result.totalCandidates());

            for (Standout s : standouts) {
                User candidate = users.get(s.standoutUserId());

                if (candidate == null) {
                    continue;
                }

                String interacted = s.hasInteracted() ? " ✓" : "";
                logInfo(
                        "{}. {} {} (Score: {}%){}",
                        s.rank(), getStandoutEmoji(s.rank()), candidate.getName(), s.score(), interacted);
                logInfo("   {} - {}", s.reason(), candidate.getAge() + "yo");
            }

            logInfo("\n[L] Like a standout  [P] Pass  [B] Back to menu");
            String input = inputReader.readLine("\nYour choice: ").toUpperCase(Locale.ROOT);

            if ("L".equals(input) || "P".equals(input)) {
                logInfo("Enter standout number (1-{}):", standouts.size());
                String numStr = inputReader.readLine("Selection: ");
                try {
                    int num = Integer.parseInt(numStr);
                    if (num >= 1 && num <= standouts.size()) {
                        Standout selected = standouts.get(num - 1);
                        User candidate = users.get(selected.standoutUserId());
                        if (candidate != null) {
                            boolean isLike = "L".equals(input);
                            processStandoutInteraction(currentUser, candidate, isLike);
                        }
                    } else {
                        logInfo("Invalid number.\n");
                    }
                } catch (NumberFormatException _) {
                    logInfo("Invalid input.\n");
                }
            }
        });
    }

    private void processStandoutInteraction(User currentUser, User candidate, boolean isLike) {
        if (isLike && !dailyService.canLike(currentUser.getId())) {
            showDailyLimitReached(currentUser);
            return;
        }
        Like.Direction direction = isLike ? Like.Direction.LIKE : Like.Direction.PASS;
        Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
        Optional<Match> matchResult = matchingService.recordLike(like);

        standoutsService.markInteracted(currentUser.getId(), candidate.getId());

        if (matchResult.isPresent()) {
            logInfo("\n🎉 IT'S A MATCH with {}! 🎉\n", candidate.getName());
        } else if (isLike) {
            logInfo("✅ Liked {}!\n", candidate.getName());
        } else {
            logInfo("👋 Passed on {}.\\n", candidate.getName());
        }
    }

    private String getStandoutEmoji(int rank) {
        return switch (rank) {
            case 1 -> "👑";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "⭐";
        };
    }
}
