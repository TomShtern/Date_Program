package datingapp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import datingapp.core.AppConfig;
import datingapp.core.Block;
import datingapp.core.BlockStorage;
import datingapp.core.CandidateFinderService;
import datingapp.core.DailyLimitService;
import datingapp.core.Dealbreakers;
import datingapp.core.GeoUtils;
import datingapp.core.Lifestyle;
import datingapp.core.Like;
import datingapp.core.LikeStorage;
import datingapp.core.Match;
import datingapp.core.MatchQuality;
import datingapp.core.MatchQualityService;
import datingapp.core.MatchStorage;
import datingapp.core.MatchingService;
import datingapp.core.ProfilePreviewService;
import datingapp.core.Report;
import datingapp.core.ReportService;
import datingapp.core.ServiceRegistry;
import datingapp.core.ServiceRegistryBuilder;
import datingapp.core.SessionService;
import datingapp.core.StatsService;
import datingapp.core.UndoService;
import datingapp.core.User;
import datingapp.core.UserStats;
import datingapp.core.UserStorage;
import datingapp.storage.DatabaseManager;

/**
 * Console-based dating app - Phase 0.5.
 * Main entry point with interactive menu.
 */
public class Main {

    private static Scanner scanner;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Constants for repeated messages
    private static final String INVALID_SELECTION = "\n❌ Invalid selection.\n";
    private static final String INVALID_INPUT = "❌ Invalid input.\n";
    private static final String PLEASE_SELECT_USER = "\n⚠️  Please select or create a user first.\n";
    private static final String CANCELLED = "Cancelled.\n";
    private static final String SEPARATOR_LINE = "═══════════════════════════════════════";
    private static final String SECTION_LINE = "  ───────────────────────────────────";
    private static final String PROMPT_CHOICE = "Your choice: ";
    private static final String PROMPT_CHOICES = "Choices: ";
    private static final String CONFIRM_SUFFIX = "? (y/n): ";
    private static final String BLOCK_PREFIX = "Block ";

    // Application context
    private static DatabaseManager dbManager;
    private static ServiceRegistry services;

    // Current session
    private static User currentUser = null;

    public static void main(String[] args) {
        try (Scanner s = new Scanner(System.in)) {
            scanner = s;
            initializeApp();

            logger.info("\n🌹 Welcome to Dating App 🌹\n");

            boolean running = true;
            while (running) {
                printMenu();
                String choice = readLine("Choose an option: ");

                switch (choice) {
                    case "1" -> createUser();
                    case "2" -> selectUser();
                    case "3" -> completeProfile();
                    case "4" -> browseCandidates();
                    case "5" -> viewMatches();
                    case "6" -> blockUser();
                    case "7" -> reportUser();
                    case "8" -> setDealbreakers();
                    case "9" -> viewStatistics();
                    case "10" -> previewProfile();
                    case "0" -> {
                        running = false;
                        logger.info("\n👋 Goodbye!\n");
                    }
                    default -> logger.info("\n❌ Invalid option. Try again.\n");
                }
            }

            shutdown();
        }
    }

    private static void initializeApp() {
        // Configuration - can be customized or loaded from file
        AppConfig config = AppConfig.defaults();

        // Database
        dbManager = DatabaseManager.getInstance();

        // Wire up all services through the registry
        services = ServiceRegistryBuilder.buildH2(dbManager, config);
    }

    // Convenience accessors for services
    private static UserStorage userStorage() {
        return services.getUserStorage();
    }

    private static LikeStorage likeStorage() {
        return services.getLikeStorage();
    }

    private static MatchStorage matchStorage() {
        return services.getMatchStorage();
    }

    private static BlockStorage blockStorage() {
        return services.getBlockStorage();
    }

    private static CandidateFinderService candidateFinder() {
        return services.getCandidateFinder();
    }

    private static MatchingService matchingService() {
        return services.getMatchingService();
    }

    private static ReportService reportService() {
        return services.getReportService();
    }

    private static SessionService sessionService() {
        return services.getSessionService();
    }

    private static StatsService statsService() {
        return services.getStatsService();
    }

    private static MatchQualityService matchQualityService() {
        return services.getMatchQualityService();
    }

    private static ProfilePreviewService profilePreviewService() {
        return services.getProfilePreviewService();
    }

    private static DailyLimitService dailyLimitService() {
        return services.getDailyLimitService();
    }

    private static UndoService undoService() {
        return services.getUndoService();
    }

    private static void printMenu() {
        logger.info(SEPARATOR_LINE);
        logger.info("         DATING APP - PHASE 0.5");
        logger.info(SEPARATOR_LINE);

        if (currentUser != null) {
            logger.info("  Current User: {} ({})", currentUser.getName(), currentUser.getState());
            // Show active session info (Phase 0.5b)
            sessionService().getCurrentSession(currentUser.getId())
                    .ifPresent(session -> logger.info("  Session: {} swipes ({} likes, {} passes) | {} elapsed",
                            session.getSwipeCount(),
                            session.getLikeCount(),
                            session.getPassCount(),
                            session.getFormattedDuration()));
            // Show daily likes (Phase 1)
            DailyLimitService.DailyStatus dailyStatus = dailyLimitService().getStatus(currentUser.getId());
            if (dailyStatus.hasUnlimitedLikes()) {
                logger.info("  💝 Daily Likes: unlimited");
            } else {
                logger.info("  💝 Daily Likes: {}/{} remaining",
                        dailyStatus.likesRemaining(), services.getConfig().dailyLikeLimit());
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
        logger.info("  8. 🎯 Set dealbreakers");
        logger.info("  9. 📊 View my statistics");
        logger.info("  10. 👤 Preview my profile");
        logger.info("  0. Exit");
        logger.info(SEPARATOR_LINE + "\n");
    }

    // === 1. CREATE USER ===
    private static void createUser() {
        logger.info("\n--- Create New User ---\n");

        String name = readLine("Enter your name: ");
        if (name.isBlank()) {
            logger.info("❌ Name cannot be empty.\n");
            return;
        }

        User user = new User(UUID.randomUUID(), name);
        userStorage().save(user);
        currentUser = user;

        logger.info("\n✅ User created! ID: {}", user.getId());
        logger.info("   Status: {} (Complete your profile to become ACTIVE)\n", user.getState());
    }

    // === 2. SELECT USER ===
    private static void selectUser() {
        logger.info("\n--- Select User ---\n");

        List<User> users = userStorage().findAll();
        if (users.isEmpty()) {
            logger.info("No users found. Create one first!\n");
            return;
        }

        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            logger.info("  {}. {} ({})", i + 1, u.getName(), u.getState());
        }

        String input = readLine("\nSelect user number (or 0 to cancel): ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= users.size()) {
                if (idx != -1)
                    logger.info(INVALID_SELECTION);
                return;
            }
            currentUser = users.get(idx);
            logger.info("\n✅ Selected: {}\n", currentUser.getName());
        } catch (NumberFormatException e) {
            logger.info(INVALID_INPUT);
        }
    }

    // === 3. COMPLETE PROFILE ===
    private static void completeProfile() {
        if (currentUser == null) {
            logger.info(PLEASE_SELECT_USER);
            return;
        }

        logger.info("\n--- Complete Profile for {} ---\n", currentUser.getName());

        promptBio();
        promptBirthDate();
        promptGender();
        promptInterests();
        promptLocation();
        promptPreferences();
        promptPhoto();
        promptLifestyle();

        // Try to activate if complete
        if (currentUser.isComplete() && currentUser.getState() == User.State.INCOMPLETE) {
            currentUser.activate();
            logger.info("\n🎉 Profile complete! Status changed to ACTIVE.");
        } else if (!currentUser.isComplete()) {
            logger.info("\n⚠️  Profile still incomplete. Missing required fields.");
        }

        userStorage().save(currentUser);
        logger.info("✅ Profile saved!\n");
    }

    private static void promptBio() {
        String bio = readLine("Bio (short description): ");
        if (!bio.isBlank())
            currentUser.setBio(bio);
    }

    private static void promptBirthDate() {
        String birthStr = readLine("Birth date (yyyy-MM-dd): ");
        try {
            LocalDate birthDate = LocalDate.parse(birthStr, DATE_FORMAT);
            currentUser.setBirthDate(birthDate);
        } catch (DateTimeParseException e) {
            logger.info("⚠️  Invalid date format, skipping.");
        }
    }

    private static void promptGender() {
        logger.info("\nGender options: 1=MALE, 2=FEMALE, 3=OTHER");
        String genderChoice = readLine("Your gender (1/2/3): ");
        User.Gender gender = switch (genderChoice) {
            case "1" -> User.Gender.MALE;
            case "2" -> User.Gender.FEMALE;
            case "3" -> User.Gender.OTHER;
            default -> null;
        };
        if (gender != null)
            currentUser.setGender(gender);
    }

    private static void promptInterests() {
        logger.info("\nInterested in (comma-separated, e.g., 1,2):");
        logger.info("  1=MALE, 2=FEMALE, 3=OTHER");
        String interestedStr = readLine("Your preferences: ");
        Set<User.Gender> interestedIn = parseGenderSet(interestedStr);
        if (!interestedIn.isEmpty())
            currentUser.setInterestedIn(interestedIn);
    }

    private static void promptLocation() {
        String latStr = readLine("\nLatitude (e.g., 32.0853): ");
        String lonStr = readLine("Longitude (e.g., 34.7818): ");
        try {
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            currentUser.setLocation(lat, lon);
        } catch (NumberFormatException e) {
            logger.info("⚠️  Invalid coordinates, skipping.");
        }
    }

    private static void promptPreferences() {
        // Distance
        String distStr = readLine("Max distance (km, default 50): ");
        try {
            int dist = Integer.parseInt(distStr);
            currentUser.setMaxDistanceKm(dist);
        } catch (NumberFormatException e) {
            // Keep default
        }

        // Age range
        String minAgeStr = readLine("Min age preference (default 18): ");
        String maxAgeStr = readLine("Max age preference (default 99): ");
        try {
            int minAge = minAgeStr.isBlank() ? 18 : Integer.parseInt(minAgeStr);
            int maxAge = maxAgeStr.isBlank() ? 99 : Integer.parseInt(maxAgeStr);
            currentUser.setAgeRange(minAge, maxAge);
        } catch (IllegalArgumentException e) {
            logger.info("⚠️  Invalid age range, using defaults.");
        }
    }

    private static void promptPhoto() {
        String photoUrl = readLine("Photo URL (or press Enter to skip): ");
        if (!photoUrl.isBlank()) {
            currentUser.addPhotoUrl(photoUrl);
        }
    }

    private static void promptLifestyle() {
        logger.info("\n--- Lifestyle (optional, helps with matching) ---\n");

        // Height
        String heightStr = readLine("Height in cm (e.g., 175, or Enter to skip): ");
        if (!heightStr.isBlank()) {
            try {
                currentUser.setHeightCm(Integer.valueOf(heightStr));
            } catch (NumberFormatException e) {
                logger.info("⚠️  Invalid height, skipping.");
            } catch (IllegalArgumentException e) {
                logger.info("⚠️  {}", e.getMessage());
            }
        }

        // Smoking
        logger.info("Smoking: 1=Never, 2=Sometimes, 3=Regularly, 0=Skip");
        String smokingChoice = readLine(PROMPT_CHOICE);
        Lifestyle.Smoking smoking = switch (smokingChoice) {
            case "1" -> Lifestyle.Smoking.NEVER;
            case "2" -> Lifestyle.Smoking.SOMETIMES;
            case "3" -> Lifestyle.Smoking.REGULARLY;
            default -> null;
        };
        if (smoking != null)
            currentUser.setSmoking(smoking);

        // Drinking
        logger.info("Drinking: 1=Never, 2=Socially, 3=Regularly, 0=Skip");
        String drinkingChoice = readLine(PROMPT_CHOICE);
        Lifestyle.Drinking drinking = switch (drinkingChoice) {
            case "1" -> Lifestyle.Drinking.NEVER;
            case "2" -> Lifestyle.Drinking.SOCIALLY;
            case "3" -> Lifestyle.Drinking.REGULARLY;
            default -> null;
        };
        if (drinking != null)
            currentUser.setDrinking(drinking);

        // Wants Kids
        logger.info("Kids: 1=Don't want, 2=Open to it, 3=Want someday, 4=Have kids, 0=Skip");
        String kidsChoice = readLine(PROMPT_CHOICE);
        Lifestyle.WantsKids wantsKids = switch (kidsChoice) {
            case "1" -> Lifestyle.WantsKids.NO;
            case "2" -> Lifestyle.WantsKids.OPEN;
            case "3" -> Lifestyle.WantsKids.SOMEDAY;
            case "4" -> Lifestyle.WantsKids.HAS_KIDS;
            default -> null;
        };
        if (wantsKids != null)
            currentUser.setWantsKids(wantsKids);

        // Looking For
        logger.info("Looking for: 1=Casual, 2=Short-term, 3=Long-term, 4=Marriage, 5=Unsure, 0=Skip");
        String lookingForChoice = readLine(PROMPT_CHOICE);
        Lifestyle.LookingFor lookingFor = switch (lookingForChoice) {
            case "1" -> Lifestyle.LookingFor.CASUAL;
            case "2" -> Lifestyle.LookingFor.SHORT_TERM;
            case "3" -> Lifestyle.LookingFor.LONG_TERM;
            case "4" -> Lifestyle.LookingFor.MARRIAGE;
            case "5" -> Lifestyle.LookingFor.UNSURE;
            default -> null;
        };
        if (lookingFor != null)
            currentUser.setLookingFor(lookingFor);
    }

    // === 4. BROWSE CANDIDATES ===
    private static void browseCandidates() {
        if (currentUser == null) {
            logger.info(PLEASE_SELECT_USER);
            return;
        }

        if (currentUser.getState() != User.State.ACTIVE) {
            logger.info("\n⚠️  You must be ACTIVE to browse candidates. Complete your profile first.\n");
            return;
        }

        logger.info("\n--- Browse Candidates ---\n");

        List<User> activeUsers = userStorage().findActive();
        Set<UUID> alreadyInteracted = likeStorage().getLikedOrPassedUserIds(currentUser.getId());
        Set<UUID> blockedUsers = blockStorage().getBlockedUserIds(currentUser.getId());

        // Combine exclusions
        Set<UUID> excluded = new HashSet<>(alreadyInteracted);
        excluded.addAll(blockedUsers);

        List<User> candidates = candidateFinder().findCandidates(currentUser, activeUsers, excluded);

        if (candidates.isEmpty()) {
            logger.info("😔 No candidates found. Try again later!\n");
            return;
        }

        for (User candidate : candidates) {
            boolean keepBrowsing = processCandidateInteraction(candidate);
            if (!keepBrowsing) {
                break;
            }
        }
    }

    private static boolean processCandidateInteraction(User candidate) {
        double distance = GeoUtils.distanceKm(
                currentUser.getLat(), currentUser.getLon(),
                candidate.getLat(), candidate.getLon());

        logger.info("┌─────────────────────────────────────────┐");
        logger.info("│ 💝 {}, {} years old", candidate.getName(), candidate.getAge());
        if (logger.isInfoEnabled()) {
            logger.info("│ 📍 {} km away", String.format("%.1f", distance));
        }
        logger.info("│ 📝 {}", candidate.getBio() != null ? candidate.getBio() : "(no bio)");
        logger.info("└─────────────────────────────────────────┘");

        String action = readLine("  [L]ike / [P]ass / [Q]uit browsing: ").toLowerCase();

        if (action.equals("q")) {
            logger.info("\nStopping browse.\n");
            return false;
        }

        Like.Direction direction = action.equals("l") ? Like.Direction.LIKE : Like.Direction.PASS;

        // Check daily limit before recording a like (Phase 1)
        if (direction == Like.Direction.LIKE && !dailyLimitService().canLike(currentUser.getId())) {
            showDailyLimitReached();
            return false;
        }

        Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
        Optional<Match> match = matchingService().recordLike(like);

        if (match.isPresent()) {
            logger.info("\n🎉🎉🎉 IT'S A MATCH! 🎉🎉🎉");
            logger.info("You and {} like each other!\n", candidate.getName());
        } else if (direction == Like.Direction.LIKE) {
            logger.info("❤️  Liked!\n");
        } else {
            logger.info("👋 Passed.\n");
        }

        // Record for undo (Phase 1)
        undoService().recordSwipe(currentUser.getId(), like, match);

        // Offer undo if available
        promptUndo(candidate.getName());

        return true;
    }

    /**
     * Show daily limit reached screen.
     */
    private static void showDailyLimitReached() {
        DailyLimitService.DailyStatus status = dailyLimitService().getStatus(currentUser.getId());
        String timeUntilReset = DailyLimitService.formatDuration(dailyLimitService().getTimeUntilReset());

        logger.info("\n" + SEPARATOR_LINE);
        logger.info("         💔 DAILY LIMIT REACHED");
        logger.info(SEPARATOR_LINE);
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
        readLine("   [Press Enter to return to menu]");
        logger.info(SEPARATOR_LINE + "\n");
    }

    /**
     * Prompt user to undo their last swipe if undo is available.
     * Shows real-time countdown and handles user response.
     * (Phase 1 - Undo Last Swipe feature)
     */
    private static void promptUndo(String candidateName) {
        if (!undoService().canUndo(currentUser.getId())) {
            return; // No undo available
        }

        // Show countdown prompt with real-time calculation
        int secondsLeft = undoService().getSecondsRemaining(currentUser.getId());
        String prompt = String.format("⏪ Undo last swipe? (%ds remaining) (Y/N): ", secondsLeft);
        String response = readLine(prompt).toLowerCase();

        if (response.equals("y")) {
            UndoService.UndoResult result = undoService().undo(currentUser.getId());

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

    // === 5. VIEW MATCHES ===
    private static void viewMatches() {
        if (currentUser == null) {
            logger.info(PLEASE_SELECT_USER);
            return;
        }

        logger.info("\n" + SEPARATOR_LINE);
        logger.info("         YOUR MATCHES");
        logger.info(SEPARATOR_LINE + "\n");

        List<Match> matches = matchStorage().getActiveMatchesFor(currentUser.getId());

        if (matches.isEmpty()) {
            logger.info("😢 No matches yet. Keep swiping!\n");
            return;
        }

        logger.info("💕 You have {} active match(es):\n", matches.size());

        // Display matches with quality scores
        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage().get(otherUserId);

            if (otherUser != null && logger.isInfoEnabled()) {
                MatchQuality quality = matchQualityService().computeQuality(match, currentUser.getId());
                logger.info("  {}. {} {}, {}         {} {}%",
                        i + 1,
                        quality.getStarDisplay(),
                        otherUser.getName(),
                        otherUser.getAge(),
                        " ".repeat(Math.max(0, 10 - otherUser.getName().length())),
                        quality.compatibilityScore());
                logger.info("     \"{}\"", quality.getShortSummary());
            }
        }

        logger.info("\n───────────────────────────────────────");
        logger.info("  [V]iew details / [U]nmatch / [B]lock / [Enter] to go back");
        String action = readLine("\nYour choice: ").toLowerCase();

        switch (action) {
            case "v" -> viewMatchDetails(matches);
            case "u" -> unmatchFromList(matches);
            case "b" -> blockFromMatches(matches);
            default -> {
                /* back to menu */ }
        }
    }

    private static void viewMatchDetails(List<Match> matches) {
        String input = readLine("Enter match number to view: ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= matches.size()) {
                logger.info(INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage().get(otherUserId);
            MatchQuality quality = matchQualityService().computeQuality(match, currentUser.getId());

            displayMatchQuality(otherUser, quality);

            logger.info("\n───────────────────────────────────────");
            logger.info("  (U)nmatch  (B)lock  (Enter to go back)");
            String action = readLine("  Your choice: ").toLowerCase();

            handleMatchDetailAction(action, match, otherUser, otherUserId);

        } catch (NumberFormatException e) {
            logger.info(INVALID_INPUT);
        }
    }

    private static void displayMatchQuality(User otherUser, MatchQuality quality) {
        String nameUpper = otherUser.getName().toUpperCase();
        logger.info("\n" + SEPARATOR_LINE);
        logger.info("         MATCH WITH {}", nameUpper);
        logger.info(SEPARATOR_LINE + "\n");

        // Basic info
        logger.info("  👤 {}, {}", otherUser.getName(), otherUser.getAge());
        if (otherUser.getBio() != null) {
            logger.info("  📝 {}", otherUser.getBio());
        }
        String distanceStr = String.format("%.1f", quality.distanceKm());
        logger.info("  📍 {} km away", distanceStr);

        // Compatibility header
        logger.info("\n" + SECTION_LINE);
        logger.info("  COMPATIBILITY: {}%  {}", quality.compatibilityScore(), quality.getStarDisplay());
        logger.info("  {}", quality.getCompatibilityLabel());
        logger.info(SECTION_LINE);

        // Highlights
        if (!quality.highlights().isEmpty()) {
            logger.info("\n  ✨ WHY YOU MATCHED");
            quality.highlights().forEach(h -> logger.info("  • {}", h));
        }

        // Score breakdown
        displayScoreBreakdown(quality);

        // Lifestyle matches detail
        if (!quality.lifestyleMatches().isEmpty()) {
            logger.info("\n  💫 LIFESTYLE ALIGNMENT");
            quality.lifestyleMatches().forEach(m -> logger.info("  • {}", m));
        }
    }

    private static void displayScoreBreakdown(MatchQuality quality) {
        logger.info("\n  📊 SCORE BREAKDOWN");
        logger.info(SECTION_LINE);
        logger.info("  Distance:      {} {}%",
                MatchQualityService.renderProgressBar(quality.distanceScore(), 12),
                (int) (quality.distanceScore() * 100));
        logger.info("  Age match:     {} {}%",
                MatchQualityService.renderProgressBar(quality.ageScore(), 12),
                (int) (quality.ageScore() * 100));
        logger.info("  Interests:     {} {}%",
                MatchQualityService.renderProgressBar(quality.interestScore(), 12),
                (int) (quality.interestScore() * 100));
        logger.info("  Lifestyle:     {} {}%",
                MatchQualityService.renderProgressBar(quality.lifestyleScore(), 12),
                (int) (quality.lifestyleScore() * 100));
        logger.info("  Response:      {} {}%",
                MatchQualityService.renderProgressBar(quality.responseScore(), 12),
                (int) (quality.responseScore() * 100));
    }

    private static void handleMatchDetailAction(String action, Match match, User otherUser, UUID otherUserId) {
        if (action.equals("u")) {
            String confirm = readLine("Unmatch with " + otherUser.getName() + CONFIRM_SUFFIX);
            if (confirm.equalsIgnoreCase("y")) {
                match.unmatch(currentUser.getId());
                matchStorage().update(match);
                logger.info("✅ Unmatched with {}.\n", otherUser.getName());
            }
        } else if (action.equals("b")) {
            String confirm = readLine(BLOCK_PREFIX + otherUser.getName() + CONFIRM_SUFFIX);
            if (confirm.equalsIgnoreCase("y")) {
                Block block = Block.create(currentUser.getId(), otherUserId);
                blockStorage().save(block);
                match.block(currentUser.getId());
                matchStorage().update(match);
                logger.info("🚫 Blocked {}. Match ended.\n", otherUser.getName());
            }
        }
    }

    private static void unmatchFromList(List<Match> matches) {
        String input = readLine("Enter match number to unmatch: ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= matches.size()) {
                logger.info(INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage().get(otherUserId);

            String confirm = readLine("Unmatch with " + otherUser.getName() + CONFIRM_SUFFIX);
            if (confirm.equalsIgnoreCase("y")) {
                match.unmatch(currentUser.getId());
                matchStorage().update(match);
                logger.info("✅ Unmatched with {}.\n", otherUser.getName());
            } else {
                logger.info(CANCELLED);
            }
        } catch (NumberFormatException e) {
            logger.info(INVALID_INPUT);
        }
    }

    private static void blockFromMatches(List<Match> matches) {
        String input = readLine("Enter match number to block: ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= matches.size()) {
                logger.info(INVALID_SELECTION);
                return;
            }

            Match match = matches.get(idx);
            UUID otherUserId = match.getOtherUser(currentUser.getId());
            User otherUser = userStorage().get(otherUserId);

            String confirm = readLine(BLOCK_PREFIX + otherUser.getName() + "? This will end your match. (y/n): ");
            if (confirm.equalsIgnoreCase("y")) {
                // Block the user
                Block block = Block.create(currentUser.getId(), otherUserId);
                blockStorage().save(block);

                // End the match
                match.block(currentUser.getId());
                matchStorage().update(match);

                logger.info("🚫 Blocked {}. Match ended.\n", otherUser.getName());
            } else {
                logger.info(CANCELLED);
            }
        } catch (NumberFormatException e) {
            logger.info(INVALID_INPUT);
        }
    }

    // === 6. BLOCK USER ===
    private static void blockUser() {
        if (currentUser == null) {
            logger.info(PLEASE_SELECT_USER);
            return;
        }

        logger.info("\n--- Block a User ---\n");

        // Show users (excluding self and already blocked)
        List<User> allUsers = userStorage().findAll();
        Set<UUID> alreadyBlocked = blockStorage().getBlockedUserIds(currentUser.getId());

        List<User> blockableUsers = allUsers.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .filter(u -> !alreadyBlocked.contains(u.getId()))
                .toList();

        if (blockableUsers.isEmpty()) {
            logger.info("No users to block.\n");
            return;
        }

        for (int i = 0; i < blockableUsers.size(); i++) {
            User u = blockableUsers.get(i);
            logger.info("  {}. {}", i + 1, u.getName());
        }

        String input = readLine("\nSelect user to block (or 0 to cancel): ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= blockableUsers.size()) {
                if (idx != -1)
                    logger.info(INVALID_SELECTION);
                return;
            }

            User toBlock = blockableUsers.get(idx);
            String confirm = readLine(BLOCK_PREFIX + toBlock.getName() + CONFIRM_SUFFIX);
            if (confirm.equalsIgnoreCase("y")) {
                Block block = Block.create(currentUser.getId(), toBlock.getId());
                blockStorage().save(block);

                // If matched, end the match
                String matchId = Match.generateId(currentUser.getId(), toBlock.getId());
                matchStorage().get(matchId).ifPresent(match -> {
                    if (match.isActive()) {
                        match.block(currentUser.getId());
                        matchStorage().update(match);
                    }
                });

                logger.info("🚫 Blocked {}.\n", toBlock.getName());
            } else {
                logger.info(CANCELLED);
            }
        } catch (NumberFormatException e) {
            logger.info(INVALID_INPUT);
        }
    }

    // === 7. REPORT USER ===
    private static void reportUser() {
        if (currentUser == null) {
            logger.info(PLEASE_SELECT_USER);
            return;
        }

        if (currentUser.getState() != User.State.ACTIVE) {
            logger.info("\n⚠️  You must be ACTIVE to report users.\n");
            return;
        }

        logger.info("\n--- Report a User ---\n");

        List<User> allUsers = userStorage().findAll();
        List<User> reportableUsers = allUsers.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .toList();

        if (reportableUsers.isEmpty()) {
            logger.info("No users to report.\n");
            return;
        }

        User toReport = selectReportCandidate(reportableUsers);
        if (toReport == null)
            return;

        Report.Reason reason = selectReportReason();
        if (reason == null)
            return;

        String description = readLine("Additional details (optional, max 500 chars): ");
        if (description.isBlank())
            description = null;

        ReportService.ReportResult result = reportService().report(
                currentUser.getId(),
                toReport.getId(),
                reason,
                description);

        if (result.success()) {
            logger.info("\n✅ Report submitted. {} has been blocked.", toReport.getName());
            if (result.userWasBanned()) {
                logger.info("⚠️  This user has been automatically BANNED due to multiple reports.");
            }
            logger.info("");
        } else {
            logger.info("\n❌ {}\n", result.errorMessage());
        }
    }

    private static User selectReportCandidate(List<User> reportableUsers) {
        for (int i = 0; i < reportableUsers.size(); i++) {
            User u = reportableUsers.get(i);
            logger.info("  {}. {} ({})", i + 1, u.getName(), u.getState());
        }

        String input = readLine("\nSelect user to report (or 0 to cancel): ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= reportableUsers.size()) {
                if (idx != -1)
                    logger.info(INVALID_SELECTION);
                return null;
            }
            return reportableUsers.get(idx);
        } catch (NumberFormatException e) {
            logger.info(INVALID_INPUT);
            return null;
        }
    }

    private static Report.Reason selectReportReason() {
        logger.info("\nReason for report:");
        Report.Reason[] reasons = Report.Reason.values();
        for (int i = 0; i < reasons.length; i++) {
            logger.info("  {}. {}", i + 1, reasons[i]);
        }

        String reasonInput = readLine("Select reason: ");
        try {
            int reasonIdx = Integer.parseInt(reasonInput) - 1;
            if (reasonIdx < 0 || reasonIdx >= reasons.length) {
                logger.info("❌ Invalid reason.\n");
                return null;
            }
            return reasons[reasonIdx];
        } catch (NumberFormatException e) {
            logger.info(INVALID_INPUT);
            return null;
        }
    }

    // === 8. SET DEALBREAKERS ===
    private static void setDealbreakers() {
        if (currentUser == null) {
            logger.info(PLEASE_SELECT_USER);
            return;
        }

        displayCurrentDealbreakers();
        displayDealbreakerMenu();

        String choice = readLine(PROMPT_CHOICE);
        handleDealbreakerChoice(choice);

        userStorage().save(currentUser);
    }

    private static void displayCurrentDealbreakers() {
        logger.info("\n" + SEPARATOR_LINE);
        logger.info("         SET YOUR DEALBREAKERS");
        logger.info(SEPARATOR_LINE + "\n");

        logger.info("Dealbreakers are HARD filters. People who");
        logger.info("don't match will NEVER appear in your feed.\n");

        Dealbreakers current = currentUser.getDealbreakers();
        if (current.hasAnyDealbreaker()) {
            logger.info("Current dealbreakers:");
            if (current.hasSmokingDealbreaker())
                logger.info("  - Smoking: {}", current.acceptableSmoking());
            if (current.hasDrinkingDealbreaker())
                logger.info("  - Drinking: {}", current.acceptableDrinking());
            if (current.hasKidsDealbreaker())
                logger.info("  - Kids stance: {}", current.acceptableKidsStance());
            if (current.hasLookingForDealbreaker())
                logger.info("  - Looking for: {}", current.acceptableLookingFor());
            if (current.hasHeightDealbreaker())
                logger.info("  - Height: {} - {} cm", current.minHeightCm(), current.maxHeightCm());
            if (current.hasAgeDealbreaker())
                logger.info("  - Max age diff: {} years", current.maxAgeDifference());
            logger.info("");
        } else {
            logger.info("No dealbreakers set (showing everyone).\n");
        }
    }

    private static void displayDealbreakerMenu() {
        logger.info("───────────────────────────────────────");
        logger.info("  1. Set smoking dealbreaker");
        logger.info("  2. Set drinking dealbreaker");
        logger.info("  3. Set kids stance dealbreaker");
        logger.info("  4. Set relationship goal dealbreaker");
        logger.info("  5. Set height range");
        logger.info("  6. Set max age difference");
        logger.info("  7. Clear all dealbreakers");
        logger.info("  0. Cancel");
        logger.info("───────────────────────────────────────\n");
    }

    private static void handleDealbreakerChoice(String choice) {
        Dealbreakers current = currentUser.getDealbreakers();
        switch (choice) {
            case "1" -> editSmokingDealbreaker(current);
            case "2" -> editDrinkingDealbreaker(current);
            case "3" -> editKidsDealbreaker(current);
            case "4" -> editLookingForDealbreaker(current);
            case "5" -> editHeightDealbreaker(current);
            case "6" -> editAgeDealbreaker(current);
            case "7" -> {
                currentUser.setDealbreakers(Dealbreakers.none());
                logger.info("✅ All dealbreakers cleared.\n");
            }
            case "0" -> logger.info(CANCELLED);
            default -> logger.info(INVALID_SELECTION);
        }
    }

    private static void editSmokingDealbreaker(Dealbreakers current) {
        logger.info("\nAcceptable smoking (comma-separated, e.g., 1,2):");
        logger.info("  1=Never, 2=Sometimes, 3=Regularly, 0=Clear");
        String input = readLine(PROMPT_CHOICES);

        Dealbreakers.Builder builder = Dealbreakers.builder();
        copyExceptSmoking(builder, current);
        if (!input.equals("0")) {
            for (String s : input.split(",")) {
                switch (s.trim()) {
                    case "1" -> builder.acceptSmoking(Lifestyle.Smoking.NEVER);
                    case "2" -> builder.acceptSmoking(Lifestyle.Smoking.SOMETIMES);
                    case "3" -> builder.acceptSmoking(Lifestyle.Smoking.REGULARLY);
                    default -> {
                        /* Ignore invalid input */ }
                }
            }
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Smoking dealbreaker updated.\n");
    }

    private static void editDrinkingDealbreaker(Dealbreakers current) {
        logger.info("\nAcceptable drinking (comma-separated):");
        logger.info("  1=Never, 2=Socially, 3=Regularly, 0=Clear");
        String input = readLine(PROMPT_CHOICES);

        Dealbreakers.Builder builder = Dealbreakers.builder();
        copyExceptDrinking(builder, current);
        if (!input.equals("0")) {
            for (String s : input.split(",")) {
                switch (s.trim()) {
                    case "1" -> builder.acceptDrinking(Lifestyle.Drinking.NEVER);
                    case "2" -> builder.acceptDrinking(Lifestyle.Drinking.SOCIALLY);
                    case "3" -> builder.acceptDrinking(Lifestyle.Drinking.REGULARLY);
                    default -> {
                        /* Ignore invalid input */ }
                }
            }
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Drinking dealbreaker updated.\n");
    }

    private static void editKidsDealbreaker(Dealbreakers current) {
        logger.info("\nAcceptable kids stance (comma-separated):");
        logger.info("  1=Don't want, 2=Open, 3=Want someday, 4=Has kids, 0=Clear");
        String input = readLine(PROMPT_CHOICES);

        Dealbreakers.Builder builder = Dealbreakers.builder();
        copyExceptKids(builder, current);
        if (!input.equals("0")) {
            for (String s : input.split(",")) {
                switch (s.trim()) {
                    case "1" -> builder.acceptKidsStance(Lifestyle.WantsKids.NO);
                    case "2" -> builder.acceptKidsStance(Lifestyle.WantsKids.OPEN);
                    case "3" -> builder.acceptKidsStance(Lifestyle.WantsKids.SOMEDAY);
                    case "4" -> builder.acceptKidsStance(Lifestyle.WantsKids.HAS_KIDS);
                    default -> {
                        /* Ignore invalid input */ }
                }
            }
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Kids stance dealbreaker updated.\n");
    }

    private static void editLookingForDealbreaker(Dealbreakers current) {
        logger.info("\nAcceptable relationship goals (comma-separated):");
        logger.info("  1=Casual, 2=Short-term, 3=Long-term, 4=Marriage, 5=Unsure, 0=Clear");
        String input = readLine(PROMPT_CHOICES);

        Dealbreakers.Builder builder = Dealbreakers.builder();
        copyExceptLookingFor(builder, current);
        if (!input.equals("0")) {
            for (String s : input.split(",")) {
                switch (s.trim()) {
                    case "1" -> builder.acceptLookingFor(Lifestyle.LookingFor.CASUAL);
                    case "2" -> builder.acceptLookingFor(Lifestyle.LookingFor.SHORT_TERM);
                    case "3" -> builder.acceptLookingFor(Lifestyle.LookingFor.LONG_TERM);
                    case "4" -> builder.acceptLookingFor(Lifestyle.LookingFor.MARRIAGE);
                    case "5" -> builder.acceptLookingFor(Lifestyle.LookingFor.UNSURE);
                    default -> {
                        /* Ignore invalid input */ }
                }
            }
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Looking for dealbreaker updated.\n");
    }

    private static void editHeightDealbreaker(Dealbreakers current) {
        logger.info("\nHeight range (in cm), or Enter to clear:");
        String minStr = readLine("Minimum height (e.g., 160): ");
        String maxStr = readLine("Maximum height (e.g., 190): ");

        Dealbreakers.Builder builder = Dealbreakers.builder();
        copyExceptHeight(builder, current);
        try {
            Integer min = minStr.isBlank() ? null : Integer.valueOf(minStr);
            Integer max = maxStr.isBlank() ? null : Integer.valueOf(maxStr);
            if (min != null || max != null) {
                builder.heightRange(min, max);
            }
            currentUser.setDealbreakers(builder.build());
            logger.info("✅ Height dealbreaker updated.\n");
        } catch (NumberFormatException e) {
            logger.info(INVALID_INPUT);
        } catch (IllegalArgumentException e) {
            logger.info("❌ {}\n", e.getMessage());
        }
    }

    private static void editAgeDealbreaker(Dealbreakers current) {
        logger.info("\nMax age difference (years), or Enter to clear:");
        String input = readLine("Max years: ");

        Dealbreakers.Builder builder = Dealbreakers.builder();
        copyExceptAge(builder, current);
        if (!input.isBlank()) {
            try {
                builder.maxAgeDifference(Integer.parseInt(input));
                currentUser.setDealbreakers(builder.build());
                logger.info("✅ Age dealbreaker updated.\n");
            } catch (NumberFormatException e) {
                logger.info(INVALID_INPUT);
            }
        } else {
            currentUser.setDealbreakers(builder.build());
            logger.info("✅ Age dealbreaker cleared.\n");
        }
    }

    // === 9. VIEW STATISTICS ===
    private static void viewStatistics() {
        if (currentUser == null) {
            logger.info(PLEASE_SELECT_USER);
            return;
        }

        logger.info("\n" + SEPARATOR_LINE);
        logger.info("         YOUR DATING STATISTICS");
        logger.info(SEPARATOR_LINE + "\n");

        // Compute or retrieve fresh stats
        UserStats stats = statsService().getOrComputeStats(currentUser.getId());

        // Format the computed time
        String computedAt = stats.computedAt().toString().substring(0, 16).replace("T", " ");
        logger.info("  Last updated: {}\n", computedAt);

        // Activity section
        logger.info("  📊 ACTIVITY");
        logger.info(SECTION_LINE);
        logger.info("  Swipes given:     {} ({} likes, {} passes)",
                stats.totalSwipesGiven(), stats.likesGiven(), stats.passesGiven());

        String likeRatioDesc = "";
        if (stats.likeRatio() > 0.7) {
            likeRatioDesc = "(you like most people)";
        } else if (stats.likeRatio() < 0.3) {
            likeRatioDesc = "(you're selective)";
        }

        logger.info("  Like ratio:       {} {}", stats.getLikeRatioDisplay(), likeRatioDesc);
        logger.info("  Swipes received:  {} ({} likes, {} passes)\n",
                stats.totalSwipesReceived(), stats.likesReceived(), stats.passesReceived());

        // Matches section
        logger.info("  💕 MATCHES");
        logger.info(SECTION_LINE);
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
        logger.info("  🎯 YOUR SCORES");
        logger.info(SECTION_LINE);
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
        if (stats.blocksGiven() > 0 || stats.blocksReceived() > 0 ||
                stats.reportsGiven() > 0 || stats.reportsReceived() > 0) {
            logger.info("  ⚠️  SAFETY");
            logger.info(SECTION_LINE);
            logger.info("  Blocks: {} given | {} received", stats.blocksGiven(), stats.blocksReceived());
            logger.info("  Reports: {} given | {} received\n", stats.reportsGiven(), stats.reportsReceived());
        }

        readLine("Press Enter to return...");
    }

    // Helper methods for dealbreaker editing (preserve other dealbreakers)

    private static void copyExceptSmoking(Dealbreakers.Builder b, Dealbreakers c) {
        if (c.hasDrinkingDealbreaker())
            b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
        if (c.hasKidsDealbreaker())
            b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
        if (c.hasLookingForDealbreaker())
            b.acceptLookingFor(c.acceptableLookingFor().toArray(Lifestyle.LookingFor[]::new));
        if (c.hasHeightDealbreaker())
            b.heightRange(c.minHeightCm(), c.maxHeightCm());
        if (c.hasAgeDealbreaker())
            b.maxAgeDifference(c.maxAgeDifference());
    }

    private static void copyExceptDrinking(Dealbreakers.Builder b, Dealbreakers c) {
        if (c.hasSmokingDealbreaker())
            b.acceptSmoking(c.acceptableSmoking().toArray(Lifestyle.Smoking[]::new));
        if (c.hasKidsDealbreaker())
            b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
        if (c.hasLookingForDealbreaker())
            b.acceptLookingFor(c.acceptableLookingFor().toArray(Lifestyle.LookingFor[]::new));
        if (c.hasHeightDealbreaker())
            b.heightRange(c.minHeightCm(), c.maxHeightCm());
        if (c.hasAgeDealbreaker())
            b.maxAgeDifference(c.maxAgeDifference());
    }

    private static void copyExceptKids(Dealbreakers.Builder b, Dealbreakers c) {
        if (c.hasSmokingDealbreaker())
            b.acceptSmoking(c.acceptableSmoking().toArray(Lifestyle.Smoking[]::new));
        if (c.hasDrinkingDealbreaker())
            b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
        if (c.hasLookingForDealbreaker())
            b.acceptLookingFor(c.acceptableLookingFor().toArray(Lifestyle.LookingFor[]::new));
        if (c.hasHeightDealbreaker())
            b.heightRange(c.minHeightCm(), c.maxHeightCm());
        if (c.hasAgeDealbreaker())
            b.maxAgeDifference(c.maxAgeDifference());
    }

    private static void copyExceptLookingFor(Dealbreakers.Builder b, Dealbreakers c) {
        if (c.hasSmokingDealbreaker())
            b.acceptSmoking(c.acceptableSmoking().toArray(Lifestyle.Smoking[]::new));
        if (c.hasDrinkingDealbreaker())
            b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
        if (c.hasKidsDealbreaker())
            b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
        if (c.hasHeightDealbreaker())
            b.heightRange(c.minHeightCm(), c.maxHeightCm());
        if (c.hasAgeDealbreaker())
            b.maxAgeDifference(c.maxAgeDifference());
    }

    private static void copyExceptHeight(Dealbreakers.Builder b, Dealbreakers c) {
        if (c.hasSmokingDealbreaker())
            b.acceptSmoking(c.acceptableSmoking().toArray(Lifestyle.Smoking[]::new));
        if (c.hasDrinkingDealbreaker())
            b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
        if (c.hasKidsDealbreaker())
            b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
        if (c.hasLookingForDealbreaker())
            b.acceptLookingFor(c.acceptableLookingFor().toArray(Lifestyle.LookingFor[]::new));
        if (c.hasAgeDealbreaker())
            b.maxAgeDifference(c.maxAgeDifference());
    }

    private static void copyExceptAge(Dealbreakers.Builder b, Dealbreakers c) {
        if (c.hasSmokingDealbreaker())
            b.acceptSmoking(c.acceptableSmoking().toArray(Lifestyle.Smoking[]::new));
        if (c.hasDrinkingDealbreaker())
            b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
        if (c.hasKidsDealbreaker())
            b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
        if (c.hasLookingForDealbreaker())
            b.acceptLookingFor(c.acceptableLookingFor().toArray(Lifestyle.LookingFor[]::new));
        if (c.hasHeightDealbreaker())
            b.heightRange(c.minHeightCm(), c.maxHeightCm());
    }

    // === HELPERS ===

    private static String readLine(String prompt) {
        logger.info(prompt);
        return scanner.nextLine().trim();
    }

    private static Set<User.Gender> parseGenderSet(String input) {
        Set<User.Gender> result = EnumSet.noneOf(User.Gender.class);
        if (input == null || input.isBlank())
            return result;

        for (String part : input.split(",")) {
            switch (part.trim()) {
                case "1" -> result.add(User.Gender.MALE);
                case "2" -> result.add(User.Gender.FEMALE);
                case "3" -> result.add(User.Gender.OTHER);
                default -> {
                    // Ignore invalid input
                }
            }
        }
        return result;
    }

    private static void previewProfile() {
        if (currentUser == null) {
            logger.info("\n❌ Please select a user first (Option 2).\n");
            return;
        }

        ProfilePreviewService.ProfilePreview preview = profilePreviewService().generatePreview(currentUser);

        logger.info("\n" + SEPARATOR_LINE);
        logger.info("      👤 YOUR PROFILE PREVIEW");
        logger.info(SEPARATOR_LINE);
        logger.info("");
        logger.info("  This is how others see you:");
        logger.info("");

        // Card display (same as candidate card)
        logger.info("┌─────────────────────────────────────────┐");
        logger.info("│ 💝 {}, {} years old", currentUser.getName(), currentUser.getAge());
        logger.info("│ 📍 Location: {}, {}", currentUser.getLat(), currentUser.getLon());
        String bio = preview.displayBio();
        if (bio.length() > 50) {
            bio = bio.substring(0, 47) + "...";
        }
        logger.info("│ 📝 {}", bio);
        if (preview.displayLookingFor() != null) {
            logger.info("│ 💭 {}", preview.displayLookingFor());
        }
        logger.info("└─────────────────────────────────────────┘");

        // Completeness section
        ProfilePreviewService.ProfileCompleteness comp = preview.completeness();
        logger.info("");
        logger.info("  📊 PROFILE COMPLETENESS: {}%", comp.percentage());
        logger.info("  " + ProfilePreviewService.renderProgressBar(comp.percentage() / 100.0, 20));

        if (!comp.missingFields().isEmpty()) {
            logger.info("");
            logger.info("  ⚠️  Missing fields:");
            comp.missingFields().forEach(f -> logger.info("    • {}", f));
        }

        // Tips section
        if (!preview.improvementTips().isEmpty()) {
            logger.info("");
            logger.info("  💡 IMPROVEMENT TIPS:");
            preview.improvementTips().forEach(tip -> logger.info("    {}", tip));
        }

        logger.info("");
        readLine("  [Press Enter to return to menu]");
    }

    private static void shutdown() {
        if (dbManager != null) {
            dbManager.shutdown();
        }
    }
}
