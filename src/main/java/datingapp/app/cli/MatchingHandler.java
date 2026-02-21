package datingapp.app.cli;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.core.AppSession;
import datingapp.core.LoggingSupport;
import datingapp.core.ServiceRegistry;
import datingapp.core.TextUtil;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.CandidateFinder.GeoUtils;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchQualityService.InterestMatcher;
import datingapp.core.matching.MatchQualityService.MatchQuality;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.MatchingService.PendingLiker;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.RecommendationService.DailyPick;
import datingapp.core.matching.Standout;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.matching.UndoService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.ProfileService;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public class MatchingHandler implements LoggingSupport {
    private static final Logger logger = LoggerFactory.getLogger(MatchingHandler.class);
    private static final String ERR_FAILED = "❌ Failed: {}\n";

    private final CandidateFinder candidateFinderService;
    private final MatchingService matchingService;
    private final InteractionStorage interactionStorage;
    private final RecommendationService dailyService;
    private final UndoService undoService;
    private final MatchQualityService matchQualityService;
    private final UserStorage userStorage;
    private final ProfileService achievementService;
    private final AnalyticsStorage analyticsStorage;
    private final TrustSafetyService trustSafetyService;
    private final ConnectionService transitionService;
    private final RecommendationService standoutsService;
    private final CommunicationStorage communicationStorage;
    private final AppSession session;
    private final InputReader inputReader;

    public MatchingHandler(Dependencies dependencies) {
        this.candidateFinderService = dependencies.candidateFinderService();
        this.matchingService = dependencies.matchingService();
        this.interactionStorage = dependencies.interactionStorage();
        this.dailyService = dependencies.dailyService();
        this.undoService = dependencies.undoService();
        this.matchQualityService = dependencies.matchQualityService();
        this.userStorage = dependencies.userStorage();
        this.achievementService = dependencies.achievementService();
        this.analyticsStorage = dependencies.analyticsStorage();
        this.trustSafetyService = dependencies.trustSafetyService();
        this.transitionService = dependencies.transitionService();
        this.standoutsService = dependencies.standoutsService();
        this.communicationStorage = dependencies.communicationStorage();
        this.session = dependencies.userSession();
        this.inputReader = dependencies.inputReader();
    }

    @Override
    public Logger logger() {
        return logger;
    }

    public static record Dependencies(
            CandidateFinder candidateFinderService,
            MatchingService matchingService,
            InteractionStorage interactionStorage,
            RecommendationService dailyService,
            UndoService undoService,
            MatchQualityService matchQualityService,
            UserStorage userStorage,
            ProfileService achievementService,
            AnalyticsStorage analyticsStorage,
            TrustSafetyService trustSafetyService,
            ConnectionService transitionService,
            RecommendationService standoutsService,
            CommunicationStorage communicationStorage,
            AppSession userSession,
            InputReader inputReader) {

        public Dependencies {
            Objects.requireNonNull(candidateFinderService);
            Objects.requireNonNull(matchingService);
            Objects.requireNonNull(interactionStorage);
            Objects.requireNonNull(dailyService);
            Objects.requireNonNull(undoService);
            Objects.requireNonNull(matchQualityService);
            Objects.requireNonNull(userStorage);
            Objects.requireNonNull(achievementService);
            Objects.requireNonNull(analyticsStorage);
            Objects.requireNonNull(trustSafetyService);
            Objects.requireNonNull(transitionService);
            Objects.requireNonNull(standoutsService);
            Objects.requireNonNull(communicationStorage);
            Objects.requireNonNull(userSession);
            Objects.requireNonNull(inputReader);
        }

        public static Dependencies fromServices(ServiceRegistry services, AppSession session, InputReader inputReader) {
            Objects.requireNonNull(services);
            return new Dependencies(
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
                    inputReader);
        }
    }

    /**
     * Displays the candidate browsing interface, showing potential matches to the
     * user. Handles daily
     * picks, candidate filtering, and user interactions (like/pass).
     */
    public void browseCandidates() {
        CliTextAndInput.requireLogin(session, () -> {
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

            logInfo("\n" + CliTextAndInput.HEADER_BROWSE_CANDIDATES + "\n");

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
        analyticsStorage.recordProfileView(currentUser.getId(), candidate.getId());
        double distance = GeoUtils.distanceKm(
                currentUser.getLat(), currentUser.getLon(),
                candidate.getLat(), candidate.getLon());
        displayCandidateProfile(candidate, currentUser, distance);

        String action = readValidatedChoice(
                CliTextAndInput.PROMPT_LIKE_PASS_QUIT,
                "❌ Invalid choice. Please enter L (like), P (pass), or Q (quit).",
                "l",
                "p",
                "q");
        if ("q".equals(action)) {
            logInfo(CliTextAndInput.MSG_STOPPING_BROWSE);
            return false;
        }

        MatchingService.SwipeResult result = matchingService.processSwipe(currentUser, candidate, "l".equals(action));
        if (!result.success()) {
            showDailyLimitReached(currentUser);
            return false;
        }
        displaySwipeResult(result, candidate, currentUser);
        promptUndo(candidate.getName(), currentUser);
        return true;
    }

    private void displayCandidateProfile(User candidate, User currentUser, double distance) {
        logInfo(CliTextAndInput.BOX_TOP);
        boolean verified = candidate.isVerified();
        @SuppressWarnings("deprecation") // CLI display - system timezone appropriate
        int age = candidate.getAge();
        logInfo(
                "│ 💝 {}{}{}, {} years old",
                candidate.getName(),
                verified ? " " : "",
                verified ? "✅ Verified" : "",
                age);
        if (logger.isInfoEnabled()) {
            logInfo("│ 📍 {} km away", String.format("%.1f", distance));
        }
        logInfo(CliTextAndInput.PROFILE_BIO_FORMAT, candidate.getBio() != null ? candidate.getBio() : "(no bio)");
        logSharedInterests(currentUser, candidate);
        logInfo(CliTextAndInput.BOX_BOTTOM);
    }

    private void displaySwipeResult(MatchingService.SwipeResult result, User candidate, User currentUser) {
        if (result.matched()) {
            logInfo("\n🎉🎉🎉 IT'S A MATCH! 🎉🎉🎉");
            logInfo("You and {} like each other!\n", candidate.getName());
            checkAndDisplayNewAchievements(currentUser);
        } else if (result.like().direction() == Like.Direction.LIKE) {
            logInfo("❤️  Liked!\n");
        } else {
            logInfo("👋 Passed.\n");
        }
    }

    private String readValidatedChoice(String prompt, String errorMsg, String... valid) {
        while (true) {
            String input = inputReader.readLine(prompt);
            if (input == null) {
                String message = "Input stream closed while waiting for choice: " + prompt;
                logInfo("❌ {}", message);
                throw new IllegalStateException(message);
            }
            Optional<String> validated = CliTextAndInput.validateChoice(input, valid);
            if (validated.isPresent()) {
                return validated.get();
            }
            logInfo(errorMsg);
        }
    }

    /**
     * Displays the user's active matches with compatibility scores and options to
     * view details,
     * unmatch, or block matches.
     */
    public void viewMatches() {
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            logInfo("\n" + CliTextAndInput.SEPARATOR_LINE);
            logInfo("         YOUR MATCHES");
            logInfo(CliTextAndInput.SEPARATOR_LINE + "\n");

            List<Match> matches = interactionStorage.getActiveMatchesFor(currentUser.getId());

            if (matches.isEmpty()) {
                logInfo("😢 No matches yet. Keep swiping!\n");
                return;
            }

            logInfo("💕 You have {} active match(es):\n", matches.size());

            for (int i = 0; i < matches.size(); i++) {
                Match match = matches.get(i);
                UUID otherUserId = match.getOtherUser(currentUser.getId());
                User otherUser = userStorage.get(otherUserId).orElse(null);
                final int displayIndex = i + 1;

                if (otherUser != null && logger.isInfoEnabled()) {
                    matchQualityService
                            .computeQuality(match, currentUser.getId())
                            .ifPresent(quality -> {
                                String verifiedBadge = otherUser.isVerified() ? " ✅ Verified" : "";
                                @SuppressWarnings("deprecation") // CLI display - system timezone appropriate
                                int age = otherUser.getAge();
                                logInfo(
                                        "  {}. {} {}{}, {}         {} {}%",
                                        displayIndex,
                                        quality.getStarDisplay(),
                                        otherUser.getName(),
                                        verifiedBadge,
                                        age,
                                        " "
                                                .repeat(Math.max(
                                                        0,
                                                        10 - otherUser.getName().length())),
                                        quality.compatibilityScore());
                                logInfo("     \"{}\"", quality.getShortSummary());
                            });
                }
            }

            logInfo(CliTextAndInput.MENU_DIVIDER_WITH_NEWLINES);
            logInfo("  " + CliTextAndInput.PROMPT_VIEW_UNMATCH_BLOCK);
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
                logInfo(CliTextAndInput.INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage.get(otherUserId).orElse(null);
            if (otherUser == null) {
                logInfo("  Other user not found — user may have been removed.");
                return;
            }
            MatchQuality quality = matchQualityService
                    .computeQuality(match, currentUser.getId())
                    .orElse(null);
            if (quality == null) {
                logInfo("  Match quality unavailable — user may have been removed.");
                return;
            }

            displayMatchQuality(otherUser, quality);

            logInfo(CliTextAndInput.MENU_DIVIDER_WITH_NEWLINES);
            logInfo("  (U)nmatch | (B)lock | (F)riend Zone | (G)raceful Exit | back");
            String action = inputReader.readLine("  Your choice: ").toLowerCase(Locale.ROOT);

            handleMatchDetailAction(action, otherUser, otherUserId, currentUser);

        } catch (NumberFormatException _) {
            logInfo(CliTextAndInput.INVALID_INPUT);
        }
    }

    private void displayMatchQuality(User otherUser, MatchQuality quality) {
        String nameUpper = otherUser.getName().toUpperCase(Locale.ROOT);
        logInfo("\n" + CliTextAndInput.SEPARATOR_LINE);
        logInfo("         MATCH WITH {}", nameUpper);
        logInfo(CliTextAndInput.SEPARATOR_LINE + "\n");

        @SuppressWarnings("deprecation") // CLI display - system timezone appropriate
        int age = otherUser.getAge();
        logInfo("  👤 {}, {}", otherUser.getName(), age);
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

        logInfo("\n" + CliTextAndInput.SECTION_LINE);
        logInfo("  COMPATIBILITY: {}%  {}", quality.compatibilityScore(), quality.getStarDisplay());
        logInfo("  {}", quality.getCompatibilityLabel());
        logInfo(CliTextAndInput.SECTION_LINE);

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

        // Use TextUtil for consistent progress bar rendering
        String distanceBar = TextUtil.renderProgressBar(quality.distanceScore(), 12);
        String ageBar = TextUtil.renderProgressBar(quality.ageScore(), 12);
        String interestBar = TextUtil.renderProgressBar(quality.interestScore(), 12);
        String lifestyleBar = TextUtil.renderProgressBar(quality.lifestyleScore(), 12);
        String paceBar = TextUtil.renderProgressBar(quality.paceScore(), 12);
        String responseBar = TextUtil.renderProgressBar(quality.responseScore(), 12);

        logInfo("\n  📊 SCORE BREAKDOWN");
        logInfo(CliTextAndInput.SECTION_LINE);
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

    private void handleMatchDetailAction(String action, User otherUser, UUID otherUserId, User currentUser) {
        switch (action) {
            case "u" -> {
                String confirm = inputReader.readLine("Unmatch with " + otherUser.getName() + "? (y/n): ");
                if ("y".equalsIgnoreCase(confirm)) {
                    logTransitionResult(
                            transitionService.unmatch(currentUser.getId(), otherUserId),
                            "✅ Unmatched with " + otherUser.getName() + ".\n");
                }
            }
            case "b" -> {
                String confirm = inputReader.readLine(
                        CliTextAndInput.BLOCK_PREFIX + otherUser.getName() + CliTextAndInput.CONFIRM_SUFFIX);
                if ("y".equalsIgnoreCase(confirm)) {
                    TrustSafetyService.BlockResult result = trustSafetyService.block(currentUser.getId(), otherUserId);
                    if (result.success()) {
                        logInfo("🚫 Blocked {}. Match ended.\n", otherUser.getName());
                    } else {
                        logInfo("❌ {}\n", result.errorMessage());
                    }
                }
            }
            case "f" -> {
                logInfo("\nSending Friend Zone request to {}...", otherUser.getName());
                logTransitionResult(
                        transitionService.requestFriendZone(currentUser.getId(), otherUserId),
                        "✅ Friend request sent!\n");
            }
            case "g" -> {
                String confirm = inputReader.readLine("Are you sure you want to exit gracefully? (y/n): ");
                if ("y".equalsIgnoreCase(confirm)) {
                    logTransitionResult(
                            transitionService.gracefulExit(currentUser.getId(), otherUserId),
                            "🕊️ Graceful exit successful. Match ended.\n");
                }
            }
            default -> {
                /* No action for unrecognized input */ }
        }
    }

    private void logTransitionResult(ConnectionService.TransitionResult result, String successMessage) {
        if (result.success()) {
            logInfo(successMessage);
        } else {
            logInfo(ERR_FAILED, result.errorMessage());
        }
    }

    private void unmatchFromList(List<Match> matches, User currentUser) {
        String input = inputReader.readLine("Enter match number to unmatch: ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= matches.size()) {
                logInfo(CliTextAndInput.INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage.get(otherUserId).orElse(null);
            if (otherUser == null) {
                logInfo("❌ User not found — may have already been deleted.\n");
                return;
            }

            String confirm = inputReader.readLine("Unmatch with " + otherUser.getName() + "? (y/n): ");
            if ("y".equalsIgnoreCase(confirm)) {
                ConnectionService.TransitionResult result = transitionService.unmatch(currentUser.getId(), otherUserId);
                if (result.success()) {
                    logInfo("✅ Unmatched with {}.\n", otherUser.getName());
                } else {
                    logInfo(ERR_FAILED, result.errorMessage());
                }
            } else {
                logInfo(CliTextAndInput.CANCELLED);
            }
        } catch (NumberFormatException _) {
            logInfo(CliTextAndInput.INVALID_INPUT);
        }
    }

    private void blockFromMatches(List<Match> matches, User currentUser) {
        String input = inputReader.readLine("Enter match number to block: ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= matches.size()) {
                logInfo(CliTextAndInput.INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage.get(otherUserId).orElse(null);
            if (otherUser == null) {
                logInfo("❌ User not found — may have already been deleted.\n");
                return;
            }

            String confirm = inputReader.readLine(
                    CliTextAndInput.BLOCK_PREFIX + otherUser.getName() + "? This will end your match. (y/n): ");
            if ("y".equalsIgnoreCase(confirm)) {
                TrustSafetyService.BlockResult result = trustSafetyService.block(currentUser.getId(), otherUserId);
                if (result.success()) {
                    logInfo("🚫 Blocked {}. Match ended.\n", otherUser.getName());
                } else {
                    logInfo("❌ {}\n", result.errorMessage());
                }
            } else {
                logInfo(CliTextAndInput.CANCELLED);
            }
        } catch (NumberFormatException _) {
            logInfo(CliTextAndInput.INVALID_INPUT);
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
        RecommendationService.DailyStatus status = dailyService.getStatus(currentUser.getId());
        String timeUntilReset = RecommendationService.formatDuration(dailyService.getTimeUntilReset());

        logInfo("\n" + CliTextAndInput.SEPARATOR_LINE);
        logInfo("         💔 DAILY LIMIT REACHED");
        logInfo(CliTextAndInput.SEPARATOR_LINE);
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
        logInfo(CliTextAndInput.SEPARATOR_LINE + "\n");
    }

    private void showDailyPick(DailyPick pick, User currentUser) {
        User candidate = pick.user();
        double distance = GeoUtils.distanceKm(
                currentUser.getLat(), currentUser.getLon(),
                candidate.getLat(), candidate.getLon());
        displayDailyPickProfile(pick, currentUser, distance);

        String action = readValidatedChoice(
                CliTextAndInput.PROMPT_LIKE_PASS_SKIP,
                "❌ Invalid choice. Please enter L (like), P (pass), or S (skip).",
                "l",
                "p",
                "s");
        if ("s".equals(action)) {
            logInfo("  👋 You can see this pick again later today.\n");
            return;
        }
        processDailyPickSwipe(pick, currentUser, action);
    }

    private void displayDailyPickProfile(DailyPick pick, User currentUser, double distance) {
        logInfo("\n" + CliTextAndInput.SEPARATOR_LINE);
        logInfo("       🎲 YOUR DAILY PICK 🎲");
        logInfo(CliTextAndInput.SEPARATOR_LINE);
        logInfo("");
        logInfo("  ✨ {}", pick.reason());
        logInfo("");
        User candidate = pick.user();
        logInfo(CliTextAndInput.BOX_TOP);
        @SuppressWarnings("deprecation") // CLI display - system timezone appropriate
        int age = candidate.getAge();
        logInfo("│ 🎁 {}, {} years old", candidate.getName(), age);
        if (logger.isInfoEnabled()) {
            logInfo("│ 📍 {} km away", String.format("%.1f", distance));
        }
        logInfo(CliTextAndInput.PROFILE_BIO_FORMAT, candidate.getBio() != null ? candidate.getBio() : "(no bio)");
        InterestMatcher.MatchResult matchResult =
                InterestMatcher.compare(currentUser.getInterests(), candidate.getInterests());
        if (!matchResult.shared().isEmpty() && logger.isInfoEnabled()) {
            logInfo("│ ✨ You both like: {}", InterestMatcher.formatSharedInterests(matchResult.shared()));
        }
        logInfo(CliTextAndInput.BOX_BOTTOM);
        logInfo("");
        logInfo("  This pick resets tomorrow at midnight!");
        logInfo("");
    }

    private void processDailyPickSwipe(DailyPick pick, User currentUser, String action) {
        dailyService.markDailyPickViewed(currentUser.getId());
        User candidate = pick.user();
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
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            if (currentUser.getState() != UserState.ACTIVE) {
                logInfo("\n⚠️  You must be ACTIVE to view standouts. Complete your profile first.\n");
                return;
            }
            logInfo("\n🌟 === TODAY'S STANDOUTS === 🌟\n");
            RecommendationService.Result result = standoutsService.getStandouts(currentUser);
            if (result.isEmpty()) {
                logInfo(result.message() != null ? result.message() : "No standouts available today.");
                logInfo("\n");
                return;
            }
            if (result.fromCache()) {
                logInfo("(Cached from earlier today - refreshes at midnight)\n");
            }
            List<Standout> standouts = result.standouts();
            Map<UUID, User> users = standoutsService.resolveUsers(standouts);
            displayStandoutCandidates(standouts, users, result.totalCandidates());
            logInfo("\n[L] Like a standout  [P] Pass  [B] Back to menu");
            String input = inputReader.readLine("\nYour choice: ").toUpperCase(Locale.ROOT);
            handleStandoutSelection(input, standouts, users, currentUser);
        });
    }

    private void displayStandoutCandidates(List<Standout> standouts, Map<UUID, User> users, int totalCandidates) {
        logInfo("Your top {} matches from {} candidates:\n", standouts.size(), totalCandidates);
        for (Standout s : standouts) {
            User candidate = users.get(s.standoutUserId());
            if (candidate == null) {
                continue;
            }
            String interacted = s.hasInteracted() ? " ✓" : "";
            logInfo(
                    "{}. {} {} (Score: {}%){}",
                    s.rank(), getStandoutEmoji(s.rank()), candidate.getName(), s.score(), interacted);
            @SuppressWarnings("deprecation") // CLI display - system timezone appropriate
            int age = candidate.getAge();
            logInfo("   {} - {}", s.reason(), age + "yo");
        }
    }

    private void handleStandoutSelection(
            String input, List<Standout> standouts, Map<UUID, User> users, User currentUser) {
        if (!"L".equals(input) && !"P".equals(input)) {
            return;
        }
        logInfo("Enter standout number (1-{}):", standouts.size());
        String numStr = inputReader.readLine("Selection: ");
        try {
            int num = Integer.parseInt(numStr);
            if (num >= 1 && num <= standouts.size()) {
                Standout selected = standouts.get(num - 1);
                User candidate = users.get(selected.standoutUserId());
                if (candidate != null) {
                    processStandoutInteraction(currentUser, candidate, "L".equals(input));
                }
            } else {
                logInfo("Invalid number.\n");
            }
        } catch (NumberFormatException _) {
            logInfo("Invalid input.\n");
        }
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
            logInfo("👋 Passed on {}.\n", candidate.getName());
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

    // --- Friend Requests & Notifications (from RelationshipHandler) ---

    /** Displays and manages pending friend requests. */
    public void viewPendingRequests() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            logInfo("\n⚠️ Please log in first.\n");
            return;
        }

        List<FriendRequest> requests = transitionService.getPendingRequestsFor(currentUser.getId());
        if (requests.isEmpty()) {
            logInfo("\nNo pending friend requests.\n");
            return;
        }

        logInfo("\n--- PENDING FRIEND REQUESTS ---");
        for (int i = 0; i < requests.size(); i++) {
            FriendRequest req = requests.get(i);
            User from = userStorage.get(req.fromUserId()).orElse(null);
            String fromName = from != null ? from.getName() : "Unknown User";
            logInfo("  {}. From: {} (Received: {})", i + 1, fromName, req.createdAt());
        }

        String choice = inputReader.readLine("\nEnter request number to respond (or 'b' to go back): ");
        if ("b".equalsIgnoreCase(choice)) {
            return;
        }

        handleFriendRequestResponse(requests, choice);
    }

    private void handleFriendRequestResponse(List<FriendRequest> requests, String choice) {
        try {
            int idx = Integer.parseInt(choice) - 1;
            if (idx < 0 || idx >= requests.size()) {
                logInfo("Invalid selection.");
                return;
            }

            FriendRequest req = requests.get(idx);
            User from = userStorage.get(req.fromUserId()).orElse(null);
            String fromName = from != null ? from.getName() : "Unknown User";
            logInfo("\nFriend Request from {}", fromName);

            String action = inputReader
                    .readLine("Do you want to (A)ccept or (D)ecline? ")
                    .toLowerCase(Locale.ROOT);
            User currentUser = session.getCurrentUser();

            if ("a".equals(action)) {
                logTransitionResult(
                        transitionService.acceptFriendZone(req.id(), currentUser.getId()),
                        "✅ You are now friends with " + fromName + "! You can find them in your matches.\n");
            } else if ("d".equals(action)) {
                logTransitionResult(
                        transitionService.declineFriendZone(req.id(), currentUser.getId()),
                        "Declined friend request from " + fromName + ".\n");
            }
        } catch (NumberFormatException e) {
            logInfo("Error processing request: {}\n", e.getMessage());
        }
    }

    /** Displays notifications for the current user. */
    public void viewNotifications() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            logInfo("\n⚠️ Please log in first.\n");
            return;
        }

        List<Notification> notifications = communicationStorage.getNotificationsForUser(currentUser.getId(), false);
        if (notifications.isEmpty()) {
            logInfo("\nNo notifications.\n");
            return;
        }

        logInfo("\n--- YOUR NOTIFICATIONS ---");
        for (Notification n : notifications) {
            String status = n.isRead() ? "  " : "🆕";
            logInfo("{} [{}] {}: {}", status, n.createdAt(), n.title(), n.message());
            if (!n.isRead()) {
                communicationStorage.markNotificationAsRead(n.id());
            }
        }

        logInfo("--------------------------\n");
        inputReader.readLine("Press Enter to continue...");
    }

    // --- Liker Browser (from LikerBrowserHandler) ---

    /** Browse pending likers who have liked the current user. */
    public void browseWhoLikedMe() {
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            List<PendingLiker> likers = matchingService.findPendingLikersWithTimes(currentUser.getId());

            if (likers.isEmpty()) {
                logInfo("\nNo new likes yet.\n");
                return;
            }

            logInfo("\n--- Who Liked Me ({} pending) ---\n", likers.size());
            for (PendingLiker liker : likers) {
                showLikerCard(liker);

                logInfo("1. Like back");
                logInfo("2. Pass");
                logInfo("0. Stop\n");

                String choice = inputReader.readLine("Choice: ");
                switch (choice) {
                    case "1" -> recordLikerSwipe(currentUser, liker.user(), Like.Direction.LIKE);
                    case "2" -> recordLikerSwipe(currentUser, liker.user(), Like.Direction.PASS);
                    case "0" -> {
                        return;
                    }
                    default -> logInfo(CliTextAndInput.INVALID_SELECTION);
                }
            }
            logInfo("\nEnd of list.\n");
        });
    }

    private void showLikerCard(PendingLiker pending) {
        User user = pending.user();
        String verifiedBadge = user.isVerified() ? " ✅ Verified" : "";
        String likedAgo = TextUtil.formatTimeAgo(pending.likedAt());

        logInfo(CliTextAndInput.BOX_TOP);
        @SuppressWarnings("deprecation") // CLI display - system timezone appropriate
        int age = user.getAge();
        logInfo("│ 💝 {}, {} years old{}", user.getName(), age, verifiedBadge);
        logInfo("│ 🕒 Liked you {}", likedAgo);
        logInfo("│ 📍 Location: {}, {}", user.getLat(), user.getLon());

        String bio = user.getBio() == null ? "" : user.getBio();
        if (bio.length() > 50) {
            bio = bio.substring(0, 47) + "...";
        }
        logInfo(CliTextAndInput.PROFILE_BIO_FORMAT, bio);
        logInfo(CliTextAndInput.BOX_BOTTOM);
        logInfo("");
    }

    private void recordLikerSwipe(User currentUser, User other, Like.Direction direction) {
        matchingService.recordLike(Like.create(currentUser.getId(), other.getId(), direction));
        logInfo("✅ Saved.\n");
    }
}
