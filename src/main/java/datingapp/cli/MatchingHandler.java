package datingapp.cli;

import datingapp.core.Achievement.UserAchievement;
import datingapp.core.AchievementService;
import datingapp.core.CandidateFinder;
import datingapp.core.CandidateFinder.GeoUtils;
import datingapp.core.DailyService;
import datingapp.core.Match;
import datingapp.core.MatchQualityService;
import datingapp.core.MatchQualityService.InterestMatcher;
import datingapp.core.MatchQualityService.MatchQuality;
import datingapp.core.MatchingService;
import datingapp.core.RelationshipTransitionService;
import datingapp.core.RelationshipTransitionService.TransitionValidationException;
import datingapp.core.UndoService;
import datingapp.core.User;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.ProfileViewStorage;
import datingapp.core.storage.UserStorage;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final BlockStorage blockStorage;
    private final DailyService dailyService;
    private final UndoService undoService;
    private final MatchQualityService matchQualityService;
    private final UserStorage userStorage;
    private final AchievementService achievementService;
    private final ProfileViewStorage profileViewStorage;
    private final RelationshipTransitionService transitionService;
    private final CliUtilities.UserSession userSession;
    private final CliUtilities.InputReader inputReader;

    public MatchingHandler(Dependencies dependencies) {
        this.candidateFinderService = dependencies.candidateFinderService();
        this.matchingService = dependencies.matchingService();
        this.likeStorage = dependencies.likeStorage();
        this.matchStorage = dependencies.matchStorage();
        this.blockStorage = dependencies.blockStorage();
        this.dailyService = dependencies.dailyService();
        this.undoService = dependencies.undoService();
        this.matchQualityService = dependencies.matchQualityService();
        this.userStorage = dependencies.userStorage();
        this.achievementService = dependencies.achievementService();
        this.profileViewStorage = dependencies.profileViewStorage();
        this.transitionService = dependencies.transitionService();
        this.userSession = dependencies.userSession();
        this.inputReader = dependencies.inputReader();
    }

    public record Dependencies(
            CandidateFinder candidateFinderService,
            MatchingService matchingService,
            LikeStorage likeStorage,
            MatchStorage matchStorage,
            BlockStorage blockStorage,
            DailyService dailyService,
            UndoService undoService,
            MatchQualityService matchQualityService,
            UserStorage userStorage,
            AchievementService achievementService,
            ProfileViewStorage profileViewStorage,
            RelationshipTransitionService transitionService,
            CliUtilities.UserSession userSession,
            CliUtilities.InputReader inputReader) {

        public Dependencies {
            Objects.requireNonNull(candidateFinderService);
            Objects.requireNonNull(matchingService);
            Objects.requireNonNull(likeStorage);
            Objects.requireNonNull(matchStorage);
            Objects.requireNonNull(blockStorage);
            Objects.requireNonNull(dailyService);
            Objects.requireNonNull(undoService);
            Objects.requireNonNull(matchQualityService);
            Objects.requireNonNull(userStorage);
            Objects.requireNonNull(achievementService);
            Objects.requireNonNull(profileViewStorage);
            Objects.requireNonNull(transitionService);
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
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        User currentUser = userSession.getCurrentUser();
        if (currentUser.getState() != User.State.ACTIVE) {
            logger.info("\n⚠️  You must be ACTIVE to browse candidates. Complete your profile first.\n");
            return;
        }

        // Check for daily pick first
        Optional<DailyService.DailyPick> dailyPick = dailyService.getDailyPick(currentUser);
        if (dailyPick.isPresent() && !dailyService.hasViewedDailyPick(currentUser.getId())) {
            showDailyPick(dailyPick.get(), currentUser);
        }

        logger.info("\n" + CliConstants.HEADER_BROWSE_CANDIDATES + "\n");

        List<User> activeUsers = userStorage.findActive();
        Set<UUID> alreadyInteracted = likeStorage.getLikedOrPassedUserIds(currentUser.getId());
        Set<UUID> blockedUsers = blockStorage.getBlockedUserIds(currentUser.getId());

        Set<UUID> excluded = new HashSet<>(alreadyInteracted);
        excluded.addAll(blockedUsers);

        List<User> candidates = candidateFinderService.findCandidates(currentUser, activeUsers, excluded);

        if (candidates.isEmpty()) {
            logger.info("😔 No candidates found. Try again later!\n");
            return;
        }

        for (User candidate : candidates) {
            boolean keepBrowsing = processCandidateInteraction(candidate, currentUser);
            if (!keepBrowsing) {
                break;
            }
        }
    }

    private boolean processCandidateInteraction(User candidate, User currentUser) {
        // Record this profile view
        profileViewStorage.recordView(currentUser.getId(), candidate.getId());

        double distance = GeoUtils.distanceKm(
                currentUser.getLat(), currentUser.getLon(),
                candidate.getLat(), candidate.getLon());

        logger.info(CliConstants.BOX_TOP);
        boolean verified = Boolean.TRUE.equals(candidate.isVerified());
        logger.info(
                "│ 💝 {}{}{}, {} years old",
                candidate.getName(),
                verified ? " " : "",
                verified ? "✅ Verified" : "",
                candidate.getAge());
        if (logger.isInfoEnabled()) {
            logger.info("│ 📍 {} km away", String.format("%.1f", distance));
        }
        logger.info(CliConstants.PROFILE_BIO_FORMAT, candidate.getBio() != null ? candidate.getBio() : "(no bio)");

        logSharedInterests(currentUser, candidate);

        logger.info(CliConstants.BOX_BOTTOM);

        // Validate input and re-prompt until valid choice is entered
        String action = null;
        while (action == null) {
            String input = inputReader.readLine(CliConstants.PROMPT_LIKE_PASS_QUIT);
            Optional<String> validated = CliUtilities.validateChoice(input, "l", "p", "q");
            if (validated.isEmpty()) {
                logger.info("❌ Invalid choice. Please enter L (like), P (pass), or Q (quit).");
            } else {
                action = validated.get();
            }
        }

        if ("q".equals(action)) {
            logger.info(CliConstants.MSG_STOPPING_BROWSE);
            return false;
        }

        Like.Direction direction = "l".equals(action) ? Like.Direction.LIKE : Like.Direction.PASS;

        // Check daily limit before recording a like
        if (direction == Like.Direction.LIKE && !dailyService.canLike(currentUser.getId())) {
            showDailyLimitReached(currentUser);
            return false;
        }

        Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
        Optional<Match> match = matchingService.recordLike(like);

        if (match.isPresent()) {
            logger.info("\n🎉🎉🎉 IT'S A MATCH! 🎉🎉🎉");
            logger.info("You and {} like each other!\n", candidate.getName());
        } else if (direction == Like.Direction.LIKE) {
            logger.info("❤️  Liked!\n");
        } else {
            logger.info("👋 Passed.\n");
        }

        if (match.isPresent()) {
            checkAndDisplayNewAchievements(currentUser);
        }

        undoService.recordSwipe(currentUser.getId(), like, match.orElse(null));
        promptUndo(candidate.getName(), currentUser);

        return true;
    }

    /**
     * Displays the user's active matches with compatibility scores and options to
     * view details,
     * unmatch, or block matches.
     */
    public void viewMatches() {
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        User currentUser = userSession.getCurrentUser();
        logger.info("\n" + CliConstants.SEPARATOR_LINE);
        logger.info("         YOUR MATCHES");
        logger.info(CliConstants.SEPARATOR_LINE + "\n");

        List<Match> matches = matchStorage.getActiveMatchesFor(currentUser.getId());

        if (matches.isEmpty()) {
            logger.info("😢 No matches yet. Keep swiping!\n");
            return;
        }

        logger.info("💕 You have {} active match(es):\n", matches.size());

        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage.get(otherUserId);

            if (otherUser != null && logger.isInfoEnabled()) {
                MatchQuality quality = matchQualityService.computeQuality(match, currentUser.getId());
                String verifiedBadge = Boolean.TRUE.equals(otherUser.isVerified()) ? " ✅ Verified" : "";
                logger.info(
                        "  {}. {} {}{}, {}         {} {}%",
                        i + 1,
                        quality.getStarDisplay(),
                        otherUser.getName(),
                        verifiedBadge,
                        otherUser.getAge(),
                        " ".repeat(Math.max(0, 10 - otherUser.getName().length())),
                        quality.compatibilityScore());
                logger.info("     \"{}\"", quality.getShortSummary());
            }
        }

        logger.info(CliConstants.MENU_DIVIDER_WITH_NEWLINES);
        logger.info("  " + CliConstants.PROMPT_VIEW_UNMATCH_BLOCK);
        String action = inputReader.readLine("\nYour choice: ").toLowerCase(Locale.ROOT);

        switch (action) {
            case "v" -> viewMatchDetails(matches, currentUser);
            case "u" -> unmatchFromList(matches, currentUser);
            case "b" -> blockFromMatches(matches, currentUser);
            default -> {
                /* back */
            }
        }
    }

    private void viewMatchDetails(List<Match> matches, User currentUser) {
        String input = inputReader.readLine("Enter match number to view: ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= matches.size()) {
                logger.info(CliConstants.INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage.get(otherUserId);
            MatchQuality quality = matchQualityService.computeQuality(match, currentUser.getId());

            displayMatchQuality(otherUser, quality);

            logger.info(CliConstants.MENU_DIVIDER_WITH_NEWLINES);
            logger.info("  (U)nmatch | (B)lock | (F)riend Zone | (G)raceful Exit | back");
            String action = inputReader.readLine("  Your choice: ").toLowerCase(Locale.ROOT);

            handleMatchDetailAction(action, match, otherUser, otherUserId, currentUser);

        } catch (NumberFormatException _) {
            logger.info(CliConstants.INVALID_INPUT);
        }
    }

    private void displayMatchQuality(User otherUser, MatchQuality quality) {
        String nameUpper = otherUser.getName().toUpperCase(Locale.ROOT);
        logger.info("\n" + CliConstants.SEPARATOR_LINE);
        logger.info("         MATCH WITH {}", nameUpper);
        logger.info(CliConstants.SEPARATOR_LINE + "\n");

        logger.info("  👤 {}, {}", otherUser.getName(), otherUser.getAge());
        if (otherUser.getBio() != null) {
            logger.info("  📝 {}", otherUser.getBio());
        }
        String distanceStr = String.format("%.1f", quality.distanceKm());
        logger.info("  📍 {} km away", distanceStr);

        logger.info("\n" + CliConstants.SECTION_LINE);
        logger.info("  COMPATIBILITY: {}%  {}", quality.compatibilityScore(), quality.getStarDisplay());
        logger.info("  {}", quality.getCompatibilityLabel());
        logger.info(CliConstants.SECTION_LINE);

        if (!quality.highlights().isEmpty()) {
            logger.info("\n  ✨ WHY YOU MATCHED");
            quality.highlights().forEach(h -> logger.info("  • {}", h));
        }

        displayScoreBreakdown(quality);

        if (!quality.lifestyleMatches().isEmpty()) {
            logger.info("\n  💫 LIFESTYLE ALIGNMENT");
            quality.lifestyleMatches().forEach(m -> logger.info("  • {}", m));
        }

        logger.info("\n  ⏱️  PACE SYNC: {}", quality.paceSyncLevel());
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

        logger.info("\n  📊 SCORE BREAKDOWN");
        logger.info(CliConstants.SECTION_LINE);
        logger.info("  Distance:      {} {}%", distanceBar, (int) (quality.distanceScore() * 100));
        logger.info("  Age match:     {} {}%", ageBar, (int) (quality.ageScore() * 100));
        logger.info("  Interests:     {} {}%", interestBar, (int) (quality.interestScore() * 100));
        logger.info("  Lifestyle:     {} {}%", lifestyleBar, (int) (quality.lifestyleScore() * 100));
        logger.info("  Pace/Sync:      {} {}%", paceBar, (int) (quality.paceScore() * 100));
        logger.info("  Response:      {} {}%", responseBar, (int) (quality.responseScore() * 100));
    }

    private void logSharedInterests(User currentUser, User candidate) {
        InterestMatcher.MatchResult matchResult =
                InterestMatcher.compare(currentUser.getInterests(), candidate.getInterests());
        if (!matchResult.hasSharedInterests() || !logger.isInfoEnabled()) {
            return;
        }

        String badge = getMutualInterestsBadge(matchResult.sharedCount());
        String sharedInterests = InterestMatcher.formatSharedInterests(matchResult.shared());
        logger.info(
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
                    logger.info("✅ Unmatched with {}.\n", otherUser.getName());
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
                    logger.info("🚫 Blocked {}. Match ended.\n", otherUser.getName());
                }
            }
            case "f" -> {
                logger.info("\nSending Friend Zone request to {}...", otherUser.getName());
                try {
                    transitionService.requestFriendZone(currentUser.getId(), otherUserId);
                    logger.info("✅ Friend request sent!\n");
                } catch (TransitionValidationException e) {
                    logger.info("❌ Failed: {}\n", e.getMessage());
                }
            }
            case "g" -> {
                String confirm = inputReader.readLine("Are you sure you want to exit gracefully? (y/n): ");
                if ("y".equalsIgnoreCase(confirm)) {
                    try {
                        transitionService.gracefulExit(currentUser.getId(), otherUserId);
                        logger.info("🕊️ Graceful exit successful. Match ended.\n");
                    } catch (TransitionValidationException e) {
                        logger.info("❌ Failed: {}\n", e.getMessage());
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
                logger.info(CliConstants.INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage.get(otherUserId);

            String confirm = inputReader.readLine("Unmatch with " + otherUser.getName() + "? (y/n): ");
            if ("y".equalsIgnoreCase(confirm)) {
                match.unmatch(currentUser.getId());
                matchStorage.update(match);
                logger.info("✅ Unmatched with {}.\n", otherUser.getName());
            } else {
                logger.info(CliConstants.CANCELLED);
            }
        } catch (NumberFormatException _) {
            logger.info(CliConstants.INVALID_INPUT);
        }
    }

    private void blockFromMatches(List<Match> matches, User currentUser) {
        String input = inputReader.readLine("Enter match number to block: ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= matches.size()) {
                logger.info(CliConstants.INVALID_SELECTION);
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
                logger.info("🚫 Blocked {}. Match ended.\n", otherUser.getName());
            } else {
                logger.info(CliConstants.CANCELLED);
            }
        } catch (NumberFormatException _) {
            logger.info(CliConstants.INVALID_INPUT);
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

        logger.info("\n" + CliConstants.SEPARATOR_LINE);
        logger.info("         💔 DAILY LIMIT REACHED");
        logger.info(CliConstants.SEPARATOR_LINE);
        logger.info("");
        logger.info("   You've used all {} likes for today!", status.likesUsed());
        logger.info("");
        logger.info("   Resets in: {}", timeUntilReset);
        logger.info("");
        logger.info("   Tips for tomorrow:");
        logger.info("   • Take time to read profiles");
        logger.info("   • Quality over quantity");
        logger.info("   • Check your matches!");
        logger.info("");
        inputReader.readLine("   [Press Enter to return to menu]");
        logger.info(CliConstants.SEPARATOR_LINE + "\n");
    }

    private void showDailyPick(DailyService.DailyPick pick, User currentUser) {
        logger.info("\n" + CliConstants.SEPARATOR_LINE);
        logger.info("       🎲 YOUR DAILY PICK 🎲");
        logger.info(CliConstants.SEPARATOR_LINE);
        logger.info("");
        logger.info("  ✨ {}", pick.reason());
        logger.info("");

        User candidate = pick.user();
        double distance = GeoUtils.distanceKm(
                currentUser.getLat(), currentUser.getLon(),
                candidate.getLat(), candidate.getLon());

        logger.info(CliConstants.BOX_TOP);
        logger.info("│ 🎁 {}, {} years old", candidate.getName(), candidate.getAge());
        if (logger.isInfoEnabled()) {
            logger.info("│ 📍 {} km away", String.format("%.1f", distance));
        }
        logger.info(CliConstants.PROFILE_BIO_FORMAT, candidate.getBio() != null ? candidate.getBio() : "(no bio)");

        InterestMatcher.MatchResult matchResult =
                InterestMatcher.compare(currentUser.getInterests(), candidate.getInterests());
        if (!matchResult.shared().isEmpty() && logger.isInfoEnabled()) {
            String sharedInterests = InterestMatcher.formatSharedInterests(matchResult.shared());
            logger.info("│ ✨ You both like: {}", sharedInterests);
        }

        logger.info(CliConstants.BOX_BOTTOM);
        logger.info("");
        logger.info("  This pick resets tomorrow at midnight!");
        logger.info("");

        // Validate input and re-prompt until valid choice is entered
        String action = null;
        while (action == null) {
            String input = inputReader.readLine(CliConstants.PROMPT_LIKE_PASS_SKIP);
            Optional<String> validated = CliUtilities.validateChoice(input, "l", "p", "s");
            if (validated.isEmpty()) {
                logger.info("❌ Invalid choice. Please enter L (like), P (pass), or S (skip).");
            } else {
                action = validated.get();
            }
        }

        if ("s".equals(action)) {
            logger.info("  👋 You can see this pick again later today.\n");
            return;
        }

        // Only mark as viewed after valid action (not on skip)
        dailyService.markDailyPickViewed(currentUser.getId());

        Like.Direction direction = "l".equals(action) ? Like.Direction.LIKE : Like.Direction.PASS;

        if (direction == Like.Direction.LIKE && !dailyService.canLike(currentUser.getId())) {
            showDailyLimitReached(currentUser);
            return;
        }

        Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
        Optional<Match> match = matchingService.recordLike(like);

        if (match.isPresent()) {
            logger.info("\n🎉🎉🎉 IT'S A MATCH WITH YOUR DAILY PICK! 🎉🎉🎉\n");
        } else if (direction == Like.Direction.LIKE) {
            logger.info("❤️  Liked your daily pick!\n");
        } else {
            logger.info("👋 Passed on daily pick.\n");
        }

        undoService.recordSwipe(currentUser.getId(), like, match.orElse(null));
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
                logger.info("\n✅ Undone! Your {} on {} has been reversed.", directionStr, candidateName);

                if (result.matchDeleted()) {
                    logger.info("   (The match was also removed)\n");
                } else {
                    logger.info("");
                }
            } else {
                logger.info("\n❌ {}\n", result.message());
            }
        }
    }

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
}
