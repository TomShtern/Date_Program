package datingapp.app.cli;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.BrowseCandidatesCommand;
import datingapp.app.usecase.matching.MatchingUseCases.PendingLikersQuery;
import datingapp.app.usecase.matching.MatchingUseCases.ProcessSwipeCommand;
import datingapp.app.usecase.matching.MatchingUseCases.RecordLikeCommand;
import datingapp.app.usecase.matching.MatchingUseCases.StandoutInteractionCommand;
import datingapp.app.usecase.matching.MatchingUseCases.StandoutsQuery;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestAction;
import datingapp.app.usecase.social.SocialUseCases.FriendRequestsQuery;
import datingapp.app.usecase.social.SocialUseCases.MarkNotificationReadCommand;
import datingapp.app.usecase.social.SocialUseCases.NotificationsQuery;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.RespondFriendRequestCommand;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.LoggingSupport;
import datingapp.core.ServiceRegistry;
import datingapp.core.TextUtil;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder.GeoUtils;
import datingapp.core.matching.DailyPickService.DailyPick;
import datingapp.core.matching.InterestMatcher;
import datingapp.core.matching.MatchQualityService;
import datingapp.core.matching.MatchQualityService.MatchQuality;
import datingapp.core.matching.MatchingService;
import datingapp.core.matching.MatchingService.PendingLiker;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.matching.StandoutService;
import datingapp.core.matching.UndoService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.ProfileService;
import datingapp.core.storage.AnalyticsStorage;
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

    private final RecommendationService dailyService;
    private final UndoService undoService;
    private final UserStorage userStorage;
    private final AnalyticsStorage analyticsStorage;
    private final RecommendationService standoutsService;
    private final AppConfig config;
    private final AppSession session;
    private final InputReader inputReader;
    private final Runnable profileCompleteCallback; // nullable; invoked when location is missing
    private final MatchingUseCases matchingUseCases;
    private final SocialUseCases socialUseCases;

    public MatchingHandler(Dependencies dependencies) {
        this.dailyService = dependencies.dailyService();
        this.undoService = dependencies.undoService();
        this.userStorage = dependencies.userStorage();
        this.analyticsStorage = dependencies.analyticsStorage();
        this.standoutsService = dependencies.standoutsService();
        this.config = dependencies.config();
        this.session = dependencies.userSession();
        this.inputReader = dependencies.inputReader();
        this.profileCompleteCallback = dependencies.profileCompleteCallback();
        this.matchingUseCases = dependencies.matchingUseCases();
        this.socialUseCases = dependencies.socialUseCases();
    }

    @Override
    public Logger logger() {
        return logger;
    }

    public static record Dependencies(
            MatchingService matchingService,
            RecommendationService dailyService,
            UndoService undoService,
            MatchQualityService matchQualityService,
            UserStorage userStorage,
            ProfileService achievementService,
            AnalyticsStorage analyticsStorage,
            RecommendationService standoutsService,
            AppConfig config,
            AppSession userSession,
            InputReader inputReader,
            Runnable profileCompleteCallback,
            MatchingUseCases matchingUseCases,
            SocialUseCases socialUseCases) {

        public Dependencies {
            Objects.requireNonNull(matchingService);
            Objects.requireNonNull(dailyService);
            Objects.requireNonNull(undoService);
            Objects.requireNonNull(matchQualityService);
            Objects.requireNonNull(userStorage);
            Objects.requireNonNull(achievementService);
            Objects.requireNonNull(analyticsStorage);
            Objects.requireNonNull(standoutsService);
            Objects.requireNonNull(config);
            Objects.requireNonNull(userSession);
            Objects.requireNonNull(inputReader);
            Objects.requireNonNull(matchingUseCases);
            Objects.requireNonNull(socialUseCases);
        }

        public static Dependencies fromServices(
                ServiceRegistry services,
                AppSession session,
                InputReader inputReader,
                Runnable profileCompleteCallback) {
            Objects.requireNonNull(services);
            return new Dependencies(
                    services.getMatchingService(),
                    services.getRecommendationService(),
                    services.getUndoService(),
                    services.getMatchQualityService(),
                    services.getUserStorage(),
                    services.getProfileService(),
                    services.getAnalyticsStorage(),
                    services.getRecommendationService(),
                    services.getConfig(),
                    session,
                    inputReader,
                    profileCompleteCallback,
                    services.getMatchingUseCases(),
                    services.getSocialUseCases());
        }

        public static Dependencies fromServices(ServiceRegistry services, AppSession session, InputReader inputReader) {
            return fromServices(services, session, inputReader, null);
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
            if (!canBrowseCandidates(currentUser)) {
                return;
            }

            var browseResult = matchingUseCases.browseCandidates(
                    new BrowseCandidatesCommand(UserContext.cli(currentUser.getId()), currentUser));
            if (!browseResult.success()) {
                logInfo(ERR_FAILED, browseResult.error().message());
                return;
            }

            browseCandidateResults(currentUser, browseResult.data());
        });
    }

    private boolean canBrowseCandidates(User currentUser) {
        if (currentUser.getState() != UserState.ACTIVE) {
            logInfo("\n⚠️  You must be ACTIVE to browse candidates. Complete your profile first.\n");
            return false;
        }

        if (!currentUser.hasLocationSet()) {
            logInfo("\n⚠️  Your profile location is not set.");
            logInfo("   Distance-based candidate matching requires your location to find nearby people.\n");
            if (profileCompleteCallback != null) {
                String response = inputReader.readLine("   Would you like to complete your profile now? (y/N): ");
                if ("y".equalsIgnoreCase(response.trim())) {
                    profileCompleteCallback.run();
                }
            }
            logInfo("   → Go to 'Complete my profile' from the main menu to add your location.\n");
            return false;
        }

        return true;
    }

    private void browseCandidateResults(User currentUser, MatchingUseCases.BrowseCandidatesResult browseData) {
        Optional<DailyPick> dailyPick = browseData.dailyPick();
        if (!browseData.dailyPickViewed() && dailyPick.isPresent()) {
            showDailyPick(dailyPick.orElseThrow(), currentUser);
        }

        logInfo("\n" + CliTextAndInput.HEADER_BROWSE_CANDIDATES + "\n");
        List<User> candidates = browseData.candidates();
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

        var result = matchingUseCases.processSwipe(new ProcessSwipeCommand(
                UserContext.cli(currentUser.getId()), currentUser, candidate, "l".equals(action), false));
        if (!result.success()) {
            showDailyLimitReached(currentUser);
            return false;
        }
        displaySwipeResult(result.data(), candidate);
        promptUndo(candidate.getName(), currentUser);
        return true;
    }

    private void displayCandidateProfile(User candidate, User currentUser, double distance) {
        logInfo(CliTextAndInput.BOX_TOP);
        boolean verified = candidate.isVerified();
        int age = candidate.getAge(config.safety().userTimeZone()).orElse(0);
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

    private void displaySwipeResult(MatchingService.SwipeResult result, User candidate) {
        if (result.matched()) {
            logInfo("\n🎉🎉🎉 IT'S A MATCH! 🎉🎉🎉");
            logInfo("You and {} like each other!\n", candidate.getName());
            // Achievement unlock is handled by AchievementEventHandler via event bus
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

            var matchesPayload = loadActiveMatches(currentUser);
            if (matchesPayload.isEmpty()) {
                return;
            }

            List<Match> matches = matchesPayload.get().matches();

            if (matches.isEmpty()) {
                logInfo("😢 No matches yet. Keep swiping!\n");
                return;
            }

            logInfo("💕 You have {} active match(es):\n", matches.size());
            renderMatchesSummary(matches, matchesPayload.get().usersById(), currentUser);

            logInfo(CliTextAndInput.MENU_DIVIDER_WITH_NEWLINES);
            logInfo("  " + CliTextAndInput.PROMPT_VIEW_UNMATCH_BLOCK);
            String action = inputReader.readLine("\nYour choice: ").toLowerCase(Locale.ROOT);
            handleMatchesAction(action, matches, currentUser);
        });
    }

    private Optional<MatchingUseCases.ActiveMatchesResult> loadActiveMatches(User currentUser) {
        var matchesResult = matchingUseCases.listActiveMatches(
                new MatchingUseCases.ListActiveMatchesQuery(UserContext.cli(currentUser.getId())));
        if (!matchesResult.success()) {
            logInfo(ERR_FAILED, matchesResult.error().message());
            return Optional.empty();
        }
        return Optional.of(matchesResult.data());
    }

    private void renderMatchesSummary(List<Match> matches, Map<UUID, User> usersById, User currentUser) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = usersById.get(otherUserId);
            if (otherUser != null) {
                var qualityResult = matchingUseCases.matchQuality(
                        new MatchingUseCases.MatchQualityQuery(UserContext.cli(currentUser.getId()), match));
                if (qualityResult.success()) {
                    MatchQuality quality = qualityResult.data();
                    int displayIndex = i + 1;
                    String verifiedBadge = otherUser.isVerified() ? " ✅ Verified" : "";
                    int age = otherUser.getAge(config.safety().userTimeZone()).orElse(0);
                    logInfo(
                            "  {}. {} {}{}, {}         {} {}%",
                            displayIndex,
                            quality.getStarDisplay(),
                            otherUser.getName(),
                            verifiedBadge,
                            age,
                            " ".repeat(Math.max(0, 10 - otherUser.getName().length())),
                            quality.compatibilityScore());
                    logInfo("     \"{}\"", quality.getShortSummary());
                }
            }
        }
    }

    private void handleMatchesAction(String action, List<Match> matches, User currentUser) {
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
            var qualityResult = matchingUseCases.matchQuality(
                    new MatchingUseCases.MatchQualityQuery(UserContext.cli(currentUser.getId()), match));
            if (!qualityResult.success()) {
                logInfo("  Match quality unavailable — user may have been removed.");
                return;
            }
            MatchQuality quality = qualityResult.data();

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

        int age = otherUser.getAge(config.safety().userTimeZone()).orElse(0);
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
                            socialUseCases.unmatch(
                                    new RelationshipCommand(UserContext.cli(currentUser.getId()), otherUserId)),
                            "✅ Unmatched with " + otherUser.getName() + ".\n");
                }
            }
            case "b" -> {
                String confirm = inputReader.readLine(
                        CliTextAndInput.BLOCK_PREFIX + otherUser.getName() + CliTextAndInput.CONFIRM_SUFFIX);
                if ("y".equalsIgnoreCase(confirm)) {
                    var result = socialUseCases.blockUser(
                            new RelationshipCommand(UserContext.cli(currentUser.getId()), otherUserId));
                    if (result.success()) {
                        logInfo("🚫 Blocked {}. Match ended.\n", otherUser.getName());
                    } else {
                        logInfo("❌ {}\n", result.error().message());
                    }
                }
            }
            case "f" -> {
                logInfo("\nSending Friend Zone request to {}...", otherUser.getName());
                logTransitionResult(
                        socialUseCases.requestFriendZone(
                                new RelationshipCommand(UserContext.cli(currentUser.getId()), otherUserId)),
                        "✅ Friend request sent!\n");
            }
            case "g" -> {
                String confirm = inputReader.readLine("Are you sure you want to exit gracefully? (y/n): ");
                if ("y".equalsIgnoreCase(confirm)) {
                    logTransitionResult(
                            socialUseCases.gracefulExit(
                                    new RelationshipCommand(UserContext.cli(currentUser.getId()), otherUserId)),
                            "🕊️ Graceful exit successful. Match ended.\n");
                }
            }
            default -> {
                /* No action for unrecognized input */ }
        }
    }

    private void logTransitionResult(UseCaseResult<ConnectionService.TransitionResult> result, String successMessage) {
        if (result.success()) {
            logInfo(successMessage);
        } else {
            logInfo(ERR_FAILED, result.error().message());
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
                var result = socialUseCases.unmatch(
                        new RelationshipCommand(UserContext.cli(currentUser.getId()), otherUserId));
                if (result.success()) {
                    logInfo("✅ Unmatched with {}.\n", otherUser.getName());
                } else {
                    logInfo(ERR_FAILED, result.error().message());
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
                var result = socialUseCases.blockUser(
                        new RelationshipCommand(UserContext.cli(currentUser.getId()), otherUserId));
                if (result.success()) {
                    logInfo("🚫 Blocked {}. Match ended.\n", otherUser.getName());
                } else {
                    logInfo("❌ {}\n", result.error().message());
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
        int age = candidate.getAge(config.safety().userTimeZone()).orElse(0);
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
        User candidate = pick.user();
        var result = matchingUseCases.processSwipe(new ProcessSwipeCommand(
                UserContext.cli(currentUser.getId()), currentUser, candidate, "l".equals(action), true));
        if (!result.success()) {
            showDailyLimitReached(currentUser);
            return;
        }
        if (result.data().matched()) {
            logInfo("\n🎉🎉🎉 IT'S A MATCH WITH YOUR DAILY PICK! 🎉🎉🎉\n");
        } else if (result.data().like().direction() == Like.Direction.LIKE) {
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
            var standoutResult =
                    matchingUseCases.standouts(new StandoutsQuery(UserContext.cli(currentUser.getId()), currentUser));
            if (!standoutResult.success()) {
                logInfo("{}\n", standoutResult.error().message());
                return;
            }
            StandoutService.Result result = standoutResult.data().result();
            if (result.isEmpty()) {
                logInfo(result.message() != null ? result.message() : "No standouts available today.");
                logInfo("\n");
                return;
            }
            if (result.fromCache()) {
                logInfo("(Cached from earlier today - refreshes at midnight)\n");
            }
            List<Standout> standouts = result.standouts();
            Map<UUID, User> users = standoutResult.data().usersById();
            if (users.isEmpty()) {
                users = standoutsService.resolveUsers(standouts);
            }
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
            int age = candidate.getAge(config.safety().userTimeZone()).orElse(0);
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
        Like.Direction direction = isLike ? Like.Direction.LIKE : Like.Direction.PASS;
        var likeResult = matchingUseCases.recordLike(
                new RecordLikeCommand(UserContext.cli(currentUser.getId()), candidate.getId(), direction, true));
        if (!likeResult.success()) {
            showDailyLimitReached(currentUser);
            return;
        }

        matchingUseCases.markStandoutInteracted(
                new StandoutInteractionCommand(UserContext.cli(currentUser.getId()), candidate.getId()));

        if (likeResult.data().match().isPresent()) {
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

        var requestsResult =
                socialUseCases.pendingFriendRequests(new FriendRequestsQuery(UserContext.cli(currentUser.getId())));
        if (!requestsResult.success()) {
            logInfo("{}\n", requestsResult.error().message());
            return;
        }

        List<FriendRequest> requests = requestsResult.data();
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
                        socialUseCases.respondToFriendRequest(new RespondFriendRequestCommand(
                                UserContext.cli(currentUser.getId()), req.id(), FriendRequestAction.ACCEPT)),
                        "✅ You are now friends with " + fromName + "! You can find them in your matches.\n");
            } else if ("d".equals(action)) {
                logTransitionResult(
                        socialUseCases.respondToFriendRequest(new RespondFriendRequestCommand(
                                UserContext.cli(currentUser.getId()), req.id(), FriendRequestAction.DECLINE)),
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

        var notificationsResult =
                socialUseCases.notifications(new NotificationsQuery(UserContext.cli(currentUser.getId()), false));
        if (!notificationsResult.success()) {
            logInfo("{}\n", notificationsResult.error().message());
            return;
        }

        List<Notification> notifications = notificationsResult.data();
        if (notifications.isEmpty()) {
            logInfo("\nNo notifications.\n");
            return;
        }

        logInfo("\n--- YOUR NOTIFICATIONS ---");
        for (Notification n : notifications) {
            String status = n.isRead() ? "  " : "🆕";
            logInfo("{} [{}] {}: {}", status, n.createdAt(), n.title(), n.message());
            if (!n.isRead()) {
                socialUseCases.markNotificationRead(
                        new MarkNotificationReadCommand(UserContext.cli(currentUser.getId()), n.id()));
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
            var likersResult =
                    matchingUseCases.pendingLikers(new PendingLikersQuery(UserContext.cli(currentUser.getId())));
            if (!likersResult.success()) {
                logInfo(ERR_FAILED, likersResult.error().message());
                return;
            }

            List<PendingLiker> likers = likersResult.data();

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
        int age = user.getAge(config.safety().userTimeZone()).orElse(0);
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
        var result = matchingUseCases.recordLike(
                new RecordLikeCommand(UserContext.cli(currentUser.getId()), other.getId(), direction, false));
        if (result.success()) {
            logInfo("✅ Saved.\n");
        } else {
            logInfo(ERR_FAILED, result.error().message());
        }
    }
}
