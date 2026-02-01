package datingapp.app.cli;

import datingapp.core.Achievement.UserAchievement;
import datingapp.core.AchievementService;
import datingapp.core.AppSession;
import datingapp.core.Dealbreakers;
import datingapp.core.MatchQualityService.InterestMatcher;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.Preferences.PacePreferences;
import datingapp.core.Preferences.PacePreferences.CommunicationStyle;
import datingapp.core.Preferences.PacePreferences.DepthPreference;
import datingapp.core.Preferences.PacePreferences.MessagingFrequency;
import datingapp.core.Preferences.PacePreferences.TimeToFirstDate;
import datingapp.core.ProfileCompletionService;
import datingapp.core.ProfilePreviewService;
import datingapp.core.User;
import datingapp.core.ValidationService;
import datingapp.core.storage.UserStorage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for profile-related CLI operations. Manages profile completion,
 * preview, and dealbreaker
 * settings.
 */
public class ProfileHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProfileHandler.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserStorage userStorage;
    private final ProfilePreviewService profilePreviewService;
    private final AchievementService achievementService;
    private final ValidationService validationService;
    private final AppSession session;
    private final CliUtilities.InputReader inputReader;

    private static final String PROMPT_CHOICE = "Your choice: ";

    public ProfileHandler(
            UserStorage userStorage,
            ProfilePreviewService profilePreviewService,
            AchievementService achievementService,
            ValidationService validationService,
            AppSession session,
            CliUtilities.InputReader inputReader) {
        this.userStorage = userStorage;
        this.profilePreviewService = profilePreviewService;
        this.achievementService = achievementService;
        this.validationService =
                java.util.Objects.requireNonNull(validationService, "validationService cannot be null");
        this.session = session;
        this.inputReader = inputReader;
    }

    /**
     * Guides the user through completing their profile with prompts for all
     * required fields. Attempts
     * to activate the profile if complete.
     */
    public void completeProfile() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            logger.info("\n--- Complete Profile for {} ---\n", currentUser.getName());

            promptBio(currentUser);
            promptBirthDate(currentUser);
            promptGender(currentUser);
            promptInterestedIn(currentUser);
            promptInterests(currentUser);
            promptLocation(currentUser);
            promptPreferences(currentUser);
            promptPhoto(currentUser);
            promptLifestyle(currentUser);
            promptPacePreferences(currentUser);

            // Try to activate if complete
            if (currentUser.isComplete() && currentUser.getState() == User.State.INCOMPLETE) {
                currentUser.activate();
                logger.info("\n🎉 Profile complete! Status changed to ACTIVE.");
            } else if (!currentUser.isComplete()) {
                logger.info("\n⚠️  Profile still incomplete. Missing required fields.");
            }

            userStorage.save(currentUser);
            logger.info("✅ Profile saved!\n");

            checkAndDisplayNewAchievements(currentUser);
        });
    }

    /**
     * Displays a preview of how the user's profile appears to other users,
     * including completeness
     * percentage and improvement tips.
     */
    public void previewProfile() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            ProfilePreviewService.ProfilePreview preview = profilePreviewService.generatePreview(currentUser);

            logger.info("\n" + CliConstants.SEPARATOR_LINE);
            logger.info("      👤 YOUR PROFILE PREVIEW");
            logger.info(CliConstants.SEPARATOR_LINE);
            logger.info("");
            logger.info("  This is how others see you:");
            logger.info("");

            // Card display
            logger.info(CliConstants.BOX_TOP);
            String verifiedBadge = Boolean.TRUE.equals(currentUser.isVerified()) ? " ✅ Verified" : "";
            logger.info("│ 💝 {}, {} years old{}", currentUser.getName(), currentUser.getAge(), verifiedBadge);
            logger.info("│ 📍 Location: {}, {}", currentUser.getLat(), currentUser.getLon());
            String bio = preview.displayBio();
            if (bio.length() > 50) {
                bio = bio.substring(0, 47) + "...";
            }
            logger.info(CliConstants.PROFILE_BIO_FORMAT, bio);
            if (preview.displayLookingFor() != null) {
                logger.info("│ 💭 {}", preview.displayLookingFor());
            }
            logger.info(CliConstants.BOX_BOTTOM);

            // Completeness
            ProfilePreviewService.ProfileCompleteness comp = preview.completeness();
            logger.info("");
            logger.info("  📊 PROFILE COMPLETENESS: {}%", comp.percentage());

            // Render progress bar when profile has some completeness
            if (comp.percentage() > 0 && logger.isInfoEnabled()) {
                String progressBar = ProfilePreviewService.renderProgressBar(comp.percentage() / 100.0, 20);
                logger.info("  {}", progressBar);
            }

            if (!comp.missingFields().isEmpty()) {
                logger.info("");
                logger.info("  ⚠️  Missing fields:");
                comp.missingFields().forEach(f -> logger.info("    • {}", f));
            }

            // Tips
            if (!preview.improvementTips().isEmpty()) {
                logger.info("");
                logger.info("  💡 IMPROVEMENT TIPS:");
                preview.improvementTips().forEach(tip -> logger.info("    {}", tip));
            }

            logger.info("");
            inputReader.readLine("  [Press Enter to return to menu]");
        });
    }

    /**
     * Allows the user to configure their dealbreakers - hard filters that exclude
     * potential matches
     * based on lifestyle preferences.
     */
    public void setDealbreakers() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            displayCurrentDealbreakers(currentUser);
            displayDealbreakerMenu();

            String choice = inputReader.readLine(PROMPT_CHOICE);
            handleDealbreakerChoice(choice, currentUser);

            userStorage.save(currentUser);
        });
    }

    // --- Helper Methods ---

    /**
     * Checks for newly unlocked achievements and displays them to the user.
     *
     * @param currentUser The user to check achievements for
     */
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
     * Prompts the user to enter their bio/description.
     *
     * @param currentUser The user whose bio is being set
     */
    private void promptBio(User currentUser) {
        String bio = inputReader.readLine("Bio (short description): ");
        if (!bio.isBlank()) {
            currentUser.setBio(bio);
        }
    }

    private void promptBirthDate(User currentUser) {
        String birthStr = inputReader.readLine("Birth date (yyyy-MM-dd): ");
        try {
            LocalDate birthDate = LocalDate.parse(birthStr, DATE_FORMAT);
            currentUser.setBirthDate(birthDate);
        } catch (DateTimeParseException _) {
            logger.info("⚠️  Invalid date format, skipping.");
        }
    }

    /**
     * Prompts the user to select their gender from available options.
     *
     * @param currentUser The user whose gender is being set
     */
    private void promptGender(User currentUser) {
        logger.info("\n" + CliConstants.GENDER_OPTIONS);
        String genderChoice = inputReader.readLine("Your gender (1/2/3): ");
        User.Gender gender =
                switch (genderChoice) {
                    case "1" -> User.Gender.MALE;
                    case "2" -> User.Gender.FEMALE;
                    case "3" -> User.Gender.OTHER;
                    default -> null;
                };
        if (gender != null) {
            currentUser.setGender(gender);
        }
    }

    private void promptInterestedIn(User currentUser) {
        logger.info("\n" + CliConstants.INTERESTED_IN_PROMPT);
        String interestedStr = inputReader.readLine("Your preferences: ");
        Set<User.Gender> interestedIn = parseGenderSet(interestedStr);
        if (!interestedIn.isEmpty()) {
            currentUser.setInterestedIn(interestedIn);
        }
    }

    // Quick fix: implementing parseGenderSet helper here as MainUtils is not
    // created yet
    private Set<User.Gender> parseGenderSet(String input) {
        Set<User.Gender> result = java.util.EnumSet.noneOf(User.Gender.class);
        if (input == null || input.isBlank()) {
            return result;
        }

        for (String part : input.split(",")) {
            switch (part.trim()) {
                case "1" -> result.add(User.Gender.MALE);
                case "2" -> result.add(User.Gender.FEMALE);
                case "3" -> result.add(User.Gender.OTHER);
                default -> {
                    // Ignore unrecognized input
                }
            }
        }
        return result;
    }

    /**
     * Prompts the user to select their interests from categorized options.
     *
     * @param currentUser The user whose interests are being set
     */
    private void promptInterests(User currentUser) {
        logger.info("\n" + CliConstants.HEADER_INTERESTS_HOBBIES);
        Set<Interest> selected = currentUser.getInterests();

        boolean editing = true;
        while (editing) {
            logger.info(
                    "\nCurrent interests: {}",
                    selected.isEmpty() ? "(none)" : InterestMatcher.formatSharedInterests(selected));
            logger.info("  1. Add by category");
            logger.info("  2. Clear all");
            logger.info("  0. Done");

            String choice = inputReader.readLine("\nChoice: ");
            switch (choice) {
                case "1" -> {
                    if (selected.size() >= Interest.MAX_PER_USER) {
                        logger.info("❌ Limit of {} reached.\n", Interest.MAX_PER_USER);
                        continue;
                    }
                    addInterestByCategory(selected);
                }
                case "2" -> {
                    selected.clear();
                    logger.info("✅ Interests cleared.\n");
                }
                case "0" -> {
                    editing = false;
                }
                default -> logger.info(CliConstants.INVALID_SELECTION);
            }
        }
        currentUser.setInterests(selected);
    }

    /**
     * Prompts the user to add interests from a specific category.
     *
     * @param selected The current set of selected interests to modify
     */
    private void addInterestByCategory(Set<Interest> selected) {
        Interest.Category[] categories = Interest.Category.values();
        for (int i = 0; i < categories.length; i++) {
            logger.info("  {}. {}", i + 1, categories[i].getDisplayName());
        }

        String input = inputReader.readLine("\nSelect category (or 0 to cancel): ");
        try {
            int catIdx = Integer.parseInt(input) - 1;
            if (catIdx < 0 || catIdx >= categories.length) {
                return;
            }

            Interest.Category cat = categories[catIdx];
            List<Interest> options = Interest.byCategory(cat);

            logger.info("\n--- {} ---", cat.getDisplayName());
            for (int i = 0; i < options.size(); i++) {
                Interest interest = options.get(i);
                String marker = selected.contains(interest) ? " [x]" : "";
                logger.info("  {}. {}{}", i + 1, interest.getDisplayName(), marker);
            }

            String intInput = inputReader.readLine("\nSelect interest (or 0 to cancel): ");
            int intIdx = Integer.parseInt(intInput) - 1;
            if (intIdx >= 0 && intIdx < options.size()) {
                Interest chosen = options.get(intIdx);
                if (selected.contains(chosen)) {
                    selected.remove(chosen);
                    logger.info("✅ Removed {}.\n", chosen.getDisplayName());
                } else if (selected.size() < Interest.MAX_PER_USER) {
                    selected.add(chosen);
                    logger.info("✅ Added {}.\n", chosen.getDisplayName());
                } else {
                    logger.info("❌ Limit reached.\n");
                }
            }
        } catch (NumberFormatException _) {
            logger.info(CliConstants.INVALID_INPUT);
        }
    }

    /**
     * Prompts the user to enter their location coordinates.
     *
     * @param currentUser The user whose location is being set
     */
    private void promptLocation(User currentUser) {
        String latStr = inputReader.readLine("\nLatitude (e.g., 32.0853): ");
        String lonStr = inputReader.readLine("Longitude (e.g., 34.7818): ");
        try {
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            ValidationService.ValidationResult result = validationService.validateLocation(lat, lon);
            if (!result.valid()) {
                logger.info("⚠️  Invalid coordinates:");
                result.errors().forEach(e -> logger.info("    - {}", e));
            } else {
                currentUser.setLocation(lat, lon);
            }
        } catch (NumberFormatException e) {
            logger.debug("Invalid coordinates: {}", e.getMessage());
            logger.info("⚠️  Invalid coordinates, skipping.");
        }
    }

    /**
     * Prompts the user to set their matching preferences (distance and age range).
     *
     * @param currentUser The user whose preferences are being set
     */
    private void promptPreferences(User currentUser) {
        String distStr = inputReader.readLine("Max distance (km, default 50): ");
        if (!distStr.isBlank()) {
            try {
                int dist = Integer.parseInt(distStr);
                ValidationService.ValidationResult result = validationService.validateDistance(dist);
                if (!result.valid()) {
                    logger.info("⚠️  Invalid distance:");
                    result.errors().forEach(e -> logger.info("    - {}", e));
                    logger.info("    Using default (50km)");
                } else {
                    currentUser.setMaxDistanceKm(dist);
                }
            } catch (NumberFormatException e) {
                logger.trace("Using default distance, input was: {}", e.getMessage());
                // Keep default - user entered non-numeric or empty input
            }
        }

        String minAgeStr = inputReader.readLine("Min age preference (default 18): ");
        String maxAgeStr = inputReader.readLine("Max age preference (default 99): ");
        try {
            int minAge = minAgeStr.isBlank() ? 18 : Integer.parseInt(minAgeStr);
            int maxAge = maxAgeStr.isBlank() ? 99 : Integer.parseInt(maxAgeStr);
            ValidationService.ValidationResult result = validationService.validateAgeRange(minAge, maxAge);
            if (!result.valid()) {
                logger.info("⚠️  Invalid age range:");
                result.errors().forEach(e -> logger.info("    - {}", e));
                logger.info("    Using defaults (18-99)");
            } else {
                currentUser.setAgeRange(minAge, maxAge);
            }
        } catch (NumberFormatException _) {
            logger.info("⚠️  Invalid age range, using defaults.");
        }
    }

    private void promptPhoto(User currentUser) {
        String photoUrl = inputReader.readLine("Photo URL (or press Enter to skip): ");
        if (!photoUrl.isBlank()) {
            currentUser.addPhotoUrl(photoUrl);
        }
    }

    /**
     * Prompts the user to enter their lifestyle preferences (smoking, drinking,
     * etc.).
     *
     * @param currentUser The user whose lifestyle preferences are being set
     */
    private void promptLifestyle(User currentUser) {
        logger.info("\n" + CliConstants.HEADER_LIFESTYLE + "\n");

        String heightStr = inputReader.readLine("Height in cm (e.g., 175, or Enter to skip): ");
        if (!heightStr.isBlank()) {
            try {
                int height = Integer.parseInt(heightStr);
                ValidationService.ValidationResult result = validationService.validateHeight(height);
                if (!result.valid()) {
                    logger.info("⚠️  Invalid height:");
                    result.errors().forEach(e -> logger.info("    - {}", e));
                } else {
                    currentUser.setHeightCm(height);
                }
            } catch (NumberFormatException _) {
                logger.info("⚠️  Invalid height, skipping.");
            }
        }

        var smoking = EnumMenu.prompt(inputReader, Lifestyle.Smoking.class, "Select smoking:", true);
        if (smoking != null) {
            currentUser.setSmoking(smoking);
        }

        var drinking = EnumMenu.prompt(inputReader, Lifestyle.Drinking.class, "Select drinking:", true);
        if (drinking != null) {
            currentUser.setDrinking(drinking);
        }

        var wantsKids = EnumMenu.prompt(inputReader, Lifestyle.WantsKids.class, "Select kids preference:", true);
        if (wantsKids != null) {
            currentUser.setWantsKids(wantsKids);
        }

        var lookingFor = EnumMenu.prompt(inputReader, Lifestyle.LookingFor.class, "Select relationship goal:", true);
        if (lookingFor != null) {
            currentUser.setLookingFor(lookingFor);
        }
    }

    /**
     * Prompts the user to set their pace preferences (messaging frequency, depth,
     * etc.).
     *
     * @param currentUser The user whose pace preferences are being set
     */
    private void promptPacePreferences(User currentUser) {
        logger.info("\n--- PACE PREFERENCES ---\n");
        logger.info("These help us find people who share your communication style.\n");

        PacePreferences current = currentUser.getPacePreferences();

        var freq = EnumMenu.prompt(inputReader, MessagingFrequency.class, "Messaging frequency:", false);
        if (freq == null) {
            freq = current.messagingFrequency();
        }

        var time = EnumMenu.prompt(inputReader, TimeToFirstDate.class, "Time to first date:", false);
        if (time == null) {
            time = current.timeToFirstDate();
        }

        var style = EnumMenu.prompt(inputReader, CommunicationStyle.class, "Communication style:", false);
        if (style == null) {
            style = current.communicationStyle();
        }

        var depth = EnumMenu.prompt(inputReader, DepthPreference.class, "Conversation depth:", false);
        if (depth == null) {
            depth = current.depthPreference();
        }

        currentUser.setPacePreferences(new PacePreferences(freq, time, style, depth));
    }

    // --- Dealbreaker Helpers ---

    private void displayCurrentDealbreakers(User currentUser) {
        logger.info("\n" + CliConstants.SEPARATOR_LINE);
        logger.info("         SET YOUR DEALBREAKERS");
        logger.info(CliConstants.SEPARATOR_LINE + "\n");
        logger.info("Dealbreakers are HARD filters.\n");

        Dealbreakers current = currentUser.getDealbreakers();
        if (current.hasAnyDealbreaker()) {
            logger.info("Current dealbreakers:");
            if (current.hasSmokingDealbreaker()) {
                logger.info("  - Smoking: {}", current.acceptableSmoking());
            }
            if (current.hasDrinkingDealbreaker()) {
                logger.info("  - Drinking: {}", current.acceptableDrinking());
            }
            if (current.hasKidsDealbreaker()) {
                logger.info("  - Kids stance: {}", current.acceptableKidsStance());
            }
            if (current.hasLookingForDealbreaker()) {
                logger.info("  - Looking for: {}", current.acceptableLookingFor());
            }
            if (current.hasHeightDealbreaker()) {
                logger.info("  - Height: {} - {} cm", current.minHeightCm(), current.maxHeightCm());
            }
            if (current.hasAgeDealbreaker()) {
                logger.info("  - Max age diff: {} years", current.maxAgeDifference());
            }
            logger.info("");
        } else {
            logger.info("No dealbreakers set (showing everyone).\n");
        }
    }

    private void displayDealbreakerMenu() {
        logger.info(CliConstants.MENU_DIVIDER);
        logger.info("  1. Set smoking dealbreaker");
        logger.info("  2. Set drinking dealbreaker");
        logger.info("  3. Set kids stance dealbreaker");
        logger.info("  4. Set relationship goal dealbreaker");
        logger.info("  5. Set height range");
        logger.info("  6. Set max age difference");
        logger.info("  7. Clear all dealbreakers");
        logger.info("  0. Cancel");
        logger.info(CliConstants.MENU_DIVIDER + "\n");
    }

    // Note: I am copying the logic from Main.java for dealbreakers
    // Including the "copyExceptX" helpers directly here or refactoring.
    // Refactoring them to a Builder merge would be nicer on Dealbreakers class, but
    // for this task I will implement them as private helpers here to avoid changing
    // core classes too much.

    private void handleDealbreakerChoice(String choice, User currentUser) {
        Dealbreakers current = currentUser.getDealbreakers();
        switch (choice) {
            case "1" -> editSmokingDealbreaker(currentUser, current);
            case "2" -> editDrinkingDealbreaker(currentUser, current);
            case "3" -> editKidsDealbreaker(currentUser, current);
            case "4" -> editLookingForDealbreaker(currentUser, current);
            case "5" -> editHeightDealbreaker(currentUser, current);
            case "6" -> editAgeDealbreaker(currentUser, current);
            case "7" -> {
                currentUser.setDealbreakers(Dealbreakers.none());
                logger.info("✅ All dealbreakers cleared.\n");
            }
            case "0" -> logger.info(CliConstants.CANCELLED);
            default -> logger.info(CliConstants.INVALID_SELECTION);
        }
    }

    private void editSmokingDealbreaker(User currentUser, Dealbreakers current) {
        var selected = EnumMenu.promptMultiple(inputReader, Lifestyle.Smoking.class, "Accept these smoking values:");
        Dealbreakers.Builder builder = current.toBuilder().clearSmoking();
        for (var value : selected) {
            builder.acceptSmoking(value);
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Smoking dealbreaker updated.\n");
    }

    private void editDrinkingDealbreaker(User currentUser, Dealbreakers current) {
        var selected = EnumMenu.promptMultiple(inputReader, Lifestyle.Drinking.class, "Accept these drinking values:");
        Dealbreakers.Builder builder = current.toBuilder().clearDrinking();
        for (var value : selected) {
            builder.acceptDrinking(value);
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Drinking dealbreaker updated.\n");
    }

    private void editKidsDealbreaker(User currentUser, Dealbreakers current) {
        var selected =
                EnumMenu.promptMultiple(inputReader, Lifestyle.WantsKids.class, "Accept these kids preferences:");
        Dealbreakers.Builder builder = current.toBuilder().clearKids();
        for (var value : selected) {
            builder.acceptKidsStance(value);
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Kids stance dealbreaker updated.\n");
    }

    private void editLookingForDealbreaker(User currentUser, Dealbreakers current) {
        var selected =
                EnumMenu.promptMultiple(inputReader, Lifestyle.LookingFor.class, "Accept these relationship goals:");
        Dealbreakers.Builder builder = current.toBuilder().clearLookingFor();
        for (var value : selected) {
            builder.acceptLookingFor(value);
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Looking for dealbreaker updated.\n");
    }

    private void editHeightDealbreaker(User currentUser, Dealbreakers current) {
        logger.info("\nHeight range (in cm), or Enter to clear:");
        String minStr = inputReader.readLine("Minimum height (e.g., 160): ");
        String maxStr = inputReader.readLine("Maximum height (e.g., 190): ");
        Dealbreakers.Builder builder = current.toBuilder().clearHeight();
        try {
            Integer min = minStr.isBlank() ? null : Integer.valueOf(minStr);
            Integer max = maxStr.isBlank() ? null : Integer.valueOf(maxStr);
            if (min != null || max != null) {
                builder.heightRange(min, max);
            }
            currentUser.setDealbreakers(builder.build());
            logger.info("✅ Height dealbreaker updated.\n");
        } catch (NumberFormatException _) {
            logger.info(CliConstants.INVALID_INPUT);
        } catch (IllegalArgumentException e) {
            logger.info("❌ {}\n", e.getMessage());
        }
    }

    private void editAgeDealbreaker(User currentUser, Dealbreakers current) {
        logger.info("\nMax age difference (years), or Enter to clear:");
        String input = inputReader.readLine("Max years: ");
        Dealbreakers.Builder builder = current.toBuilder().clearAge();
        if (!input.isBlank()) {
            try {
                builder.maxAgeDifference(Integer.parseInt(input));
                currentUser.setDealbreakers(builder.build());
                logger.info("✅ Age dealbreaker updated.\n");
            } catch (NumberFormatException _) {
                logger.info(CliConstants.INVALID_INPUT);
            }
        } else {
            currentUser.setDealbreakers(builder.build());
            logger.info("✅ Age dealbreaker cleared.\n");
        }
    }

    // --- User Creation and Selection Methods ---

    /** Creates a new user and sets them as the current user. */
    public void createUser() {
        logger.info("\n--- Create New User ---\n");

        String name = inputReader.readLine("Enter your name: ");
        if (name.isBlank()) {
            logger.info("❌ Name cannot be empty.\n");
            return;
        }

        User user = new User(UUID.randomUUID(), name);
        userStorage.save(user);
        session.setCurrentUser(user);

        logger.info("\n✅ User created! ID: {}", user.getId());
        logger.info("   Status: {} (Complete your profile to become ACTIVE)\n", user.getState());
    }

    /** Displays all users and allows selection of one as the current user. */
    public void selectUser() {
        logger.info("\n--- Select User ---\n");

        List<User> users = userStorage.findAll();
        if (users.isEmpty()) {
            logger.info("No users found. Create one first!\n");
            return;
        }

        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            logger.info("  {}. {} ({})", i + 1, u.getName(), u.getState());
        }

        String input = inputReader.readLine("\nSelect user number (or 0 to cancel): ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= users.size()) {
                if (idx != -1) {
                    logger.info("\n❌ Invalid selection.\n");
                }
                return;
            }
            session.setCurrentUser(users.get(idx));
            logger.info("\n✅ Selected: {}\n", session.getCurrentUser().getName());
        } catch (NumberFormatException _) {
            logger.info("❌ Invalid input.\n");
        }
    }

    /**
     * Displays the profile completion score and breakdown for the current user.
     */
    public void viewProfileScore() {
        CliUtilities.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            ProfileCompletionService.CompletionResult result = ProfileCompletionService.calculate(currentUser);

            logger.info("\n───────────────────────────────────────");
            logger.info("      📊 PROFILE COMPLETION SCORE");
            logger.info("───────────────────────────────────────\n");

            logger.info("  {} {}% {}", result.getTierEmoji(), result.score(), result.tier());
            if (logger.isInfoEnabled()) {
                String overallBar = ProfileCompletionService.renderProgressBar(result.score(), 25);
                logger.info("  {}", overallBar);
            }
            logger.info("");

            // Category breakdown
            for (ProfileCompletionService.CategoryBreakdown cat : result.breakdown()) {
                logger.info("  {} - {}%", cat.category(), cat.score());
                if (logger.isInfoEnabled()) {
                    String categoryBar = ProfileCompletionService.renderProgressBar(cat.score(), 15);
                    logger.info("    {}", categoryBar);
                }
                if (!cat.missingItems().isEmpty()) {
                    cat.missingItems().forEach(m -> logger.info("    ⚪ {}", m));
                }
            }

            // Next steps
            if (!result.nextSteps().isEmpty()) {
                logger.info("\n  💡 NEXT STEPS:");
                result.nextSteps().forEach(s -> logger.info("    {}", s));
            }

            logger.info("");
            inputReader.readLine("  [Press Enter to return to menu]");
        });
    }
}
