package datingapp.app.cli;

import datingapp.app.cli.CliSupport.EnumMenu;
import datingapp.app.cli.CliSupport.InputReader;
import datingapp.core.AppSession;
import datingapp.core.EnumSetUtil;
import datingapp.core.LoggingSupport;
import datingapp.core.model.Achievement.UserAchievement;
import datingapp.core.model.Dealbreakers;
import datingapp.core.model.Preferences.Interest;
import datingapp.core.model.Preferences.Lifestyle;
import datingapp.core.model.Preferences.PacePreferences;
import datingapp.core.model.Preferences.PacePreferences.CommunicationStyle;
import datingapp.core.model.Preferences.PacePreferences.DepthPreference;
import datingapp.core.model.Preferences.PacePreferences.MessagingFrequency;
import datingapp.core.model.Preferences.PacePreferences.TimeToFirstDate;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.service.AchievementService;
import datingapp.core.service.ProfileCompletionService;
import datingapp.core.service.ValidationService;
import datingapp.core.storage.UserStorage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for profile-related CLI operations. Manages profile completion,
 * preview, and dealbreaker
 * settings.
 */
public class ProfileHandler implements LoggingSupport {
    private static final Logger logger = LoggerFactory.getLogger(ProfileHandler.class);
    private static final String INDENTED_LINE = "    {}";
    private static final String INDENTED_BULLET = "    - {}";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserStorage userStorage;
    private final ProfileCompletionService profileCompletionService;
    private final AchievementService achievementService;
    private final ValidationService validationService;
    private final AppSession session;
    private final InputReader inputReader;

    private static final String PROMPT_CHOICE = "Your choice: ";

    public ProfileHandler(
            UserStorage userStorage,
            ProfileCompletionService profileCompletionService,
            AchievementService achievementService,
            ValidationService validationService,
            AppSession session,
            InputReader inputReader) {
        this.userStorage = userStorage;
        this.profileCompletionService = profileCompletionService;
        this.achievementService = achievementService;
        this.validationService =
                java.util.Objects.requireNonNull(validationService, "validationService cannot be null");
        this.session = session;
        this.inputReader = inputReader;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    /**
     * Guides the user through completing their profile with prompts for all
     * required fields. Attempts
     * to activate the profile if complete.
     */
    public void completeProfile() {
        CliSupport.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            logInfo("\n--- Complete Profile for {} ---\n", currentUser.getName());

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
            if (currentUser.isComplete() && currentUser.getState() == UserState.INCOMPLETE) {
                currentUser.activate();
                logInfo("\n🎉 Profile complete! Status changed to ACTIVE.");
            } else if (!currentUser.isComplete()) {
                logInfo("\n⚠️  Profile still incomplete. Missing required fields.");
            }

            userStorage.save(currentUser);
            logInfo("✅ Profile saved!\n");

            checkAndDisplayNewAchievements(currentUser);
        });
    }

    /**
     * Displays a preview of how the user's profile appears to other users,
     * including completeness
     * percentage and improvement tips.
     */
    public void previewProfile() {
        CliSupport.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            ProfileCompletionService.ProfilePreview preview = profileCompletionService.generatePreview(currentUser);

            logInfo("\n" + CliSupport.SEPARATOR_LINE);
            logInfo("      👤 YOUR PROFILE PREVIEW");
            logInfo(CliSupport.SEPARATOR_LINE);
            logInfo("");
            logInfo("  This is how others see you:");
            logInfo("");

            // Card display
            logInfo(CliSupport.BOX_TOP);
            String verifiedBadge = currentUser.isVerified() ? " ✅ Verified" : "";
            logInfo("│ 💝 {}, {} years old{}", currentUser.getName(), currentUser.getAge(), verifiedBadge);
            logInfo("│ 📍 Location: {}, {}", currentUser.getLat(), currentUser.getLon());
            String bio = preview.displayBio();
            if (bio.length() > 50) {
                bio = bio.substring(0, 47) + "...";
            }
            logInfo(CliSupport.PROFILE_BIO_FORMAT, bio);
            if (preview.displayLookingFor() != null) {
                logInfo("│ 💭 {}", preview.displayLookingFor());
            }
            logInfo(CliSupport.BOX_BOTTOM);

            // Completeness
            ProfileCompletionService.ProfileCompleteness comp = preview.completeness();
            logInfo("");
            logInfo("  📊 PROFILE COMPLETENESS: {}%", comp.percentage());

            // Render progress bar when profile has some completeness
            if (comp.percentage() > 0 && logger.isInfoEnabled()) {
                String progressBar = ProfileCompletionService.renderProgressBar(comp.percentage() / 100.0, 20);
                logInfo("  {}", progressBar);
            }

            if (!comp.missingFields().isEmpty()) {
                logInfo("");
                logInfo("  ⚠️  Missing fields:");
                comp.missingFields().forEach(f -> logInfo("    • {}", f));
            }

            // Tips
            if (!preview.improvementTips().isEmpty()) {
                logInfo("");
                logInfo("  💡 IMPROVEMENT TIPS:");
                preview.improvementTips().forEach(tip -> logInfo(INDENTED_LINE, tip));
            }

            logInfo("");
            inputReader.readLine("  [Press Enter to return to menu]");
        });
    }

    /**
     * Allows the user to configure their dealbreakers - hard filters that exclude
     * potential matches
     * based on lifestyle preferences.
     */
    public void setDealbreakers() {
        CliSupport.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            boolean editing = true;
            while (editing) {
                displayCurrentDealbreakers(currentUser);
                displayDealbreakerMenu();

                String choice = inputReader.readLine(PROMPT_CHOICE).trim();
                if ("0".equals(choice)) {
                    logInfo(CliSupport.CANCELLED);
                    editing = false;
                    continue;
                }

                handleDealbreakerChoice(choice, currentUser);
                userStorage.save(currentUser);
            }
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
            logInfo("\n🏆 NEW ACHIEVEMENTS UNLOCKED! 🏆");
            for (UserAchievement ua : newAchievements) {
                logInfo(
                        "  ✨ {} - {}",
                        ua.achievement().getDisplayName(),
                        ua.achievement().getDescription());
            }
            logInfo("");
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
            logInfo("⚠️  Invalid date format, skipping.");
        }
    }

    /**
     * Prompts the user to select their gender from available options.
     *
     * @param currentUser The user whose gender is being set
     */
    private void promptGender(User currentUser) {
        logInfo("\n" + CliSupport.GENDER_OPTIONS);
        String genderChoice = inputReader.readLine("Your gender (1/2/3): ");
        Gender gender =
                switch (genderChoice) {
                    case "1" -> Gender.MALE;
                    case "2" -> Gender.FEMALE;
                    case "3" -> Gender.OTHER;
                    default -> null;
                };
        if (gender != null) {
            currentUser.setGender(gender);
        }
    }

    private void promptInterestedIn(User currentUser) {
        logInfo("\n" + CliSupport.INTERESTED_IN_PROMPT);
        String interestedStr = inputReader.readLine("Your preferences: ");
        Set<Gender> interestedIn = parseGenderSet(interestedStr);
        if (!interestedIn.isEmpty()) {
            currentUser.setInterestedIn(interestedIn);
        }
    }

    private Set<Gender> parseGenderSet(String input) {
        String normalized = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
        Set<Gender> result = new HashSet<>();

        for (String token : normalized.split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            try {
                result.add(Gender.valueOf(token));
            } catch (IllegalArgumentException ex) {
                logDebug("Invalid gender selection: {}", ex.getMessage());
                logInfo("⚠️  Invalid gender: {}", token);
            }
        }

        return EnumSetUtil.safeCopy(result, Gender.class);
    }

    private void promptInterests(User currentUser) {
        Set<Interest> interestSet = EnumSetUtil.safeCopy(currentUser.getInterests(), Interest.class);
        boolean editing = true;

        while (editing) {
            displayInterestMenu(interestSet);
            String choice = inputReader.readLine("Select: ").trim();
            switch (choice) {
                case "1" -> addInterestByCategory(currentUser, Interest.Category.OUTDOORS, interestSet);
                case "2" -> addInterestByCategory(currentUser, Interest.Category.ARTS, interestSet);
                case "3" -> addInterestByCategory(currentUser, Interest.Category.FOOD, interestSet);
                case "4" -> addInterestByCategory(currentUser, Interest.Category.SPORTS, interestSet);
                case "5" -> addInterestByCategory(currentUser, Interest.Category.TECH, interestSet);
                case "6" -> addInterestByCategory(currentUser, Interest.Category.SOCIAL, interestSet);
                case "7" -> {
                    interestSet.clear();
                    currentUser.setInterests(interestSet);
                    logInfo("✅ Interests cleared.\n");
                }
                case "0" -> {
                    editing = false;
                } // NOPMD AssignmentInOperand
                default -> logInfo(CliSupport.INVALID_SELECTION);
            }
        }

        currentUser.setInterests(interestSet);
    }

    private void displayInterestMenu(Set<Interest> interestSet) {
        logInfo("\n--- Interests ({} / {}) ---", interestSet.size(), Interest.MAX_PER_USER);
        if (interestSet.isEmpty()) {
            logInfo("None selected.");
        } else {
            interestSet.forEach(interest -> logInfo(" - {}", interest.getDisplayName()));
        }
        logInfo("\nChoose a category to edit:");
        logInfo("  1. {}", Interest.Category.OUTDOORS.getDisplayName());
        logInfo("  2. {}", Interest.Category.ARTS.getDisplayName());
        logInfo("  3. {}", Interest.Category.FOOD.getDisplayName());
        logInfo("  4. {}", Interest.Category.SPORTS.getDisplayName());
        logInfo("  5. {}", Interest.Category.TECH.getDisplayName());
        logInfo("  6. {}", Interest.Category.SOCIAL.getDisplayName());
        logInfo("  7. Clear all interests");
        logInfo("  0. Back\n");
    }

    private void addInterestByCategory(User currentUser, Interest.Category category, Set<Interest> interestSet) {
        List<Interest> options = Interest.byCategory(category);
        if (options.isEmpty()) {
            logInfo("\nNo interests available in this category.\n");
            return;
        }
        displayInterestOptions(category, options, interestSet);

        String input = inputReader
                .readLine("Select numbers (comma-separated) or 0 to cancel: ")
                .trim();
        if (!"0".equals(input)) {
            applyInterestSelections(interestSet, options, input);
            currentUser.setInterests(interestSet);
        }
    }

    private void displayInterestOptions(Interest.Category category, List<Interest> options, Set<Interest> interestSet) {
        logInfo("\n{}:", category.getDisplayName());
        for (int i = 0; i < options.size(); i++) {
            Interest interest = options.get(i);
            String marker = interestSet.contains(interest) ? "✅" : "⬜";
            logInfo("  {}. {} {}", i + 1, marker, interest.getDisplayName());
        }
    }

    private void applyInterestSelections(Set<Interest> interestSet, List<Interest> options, String input) {
        for (String rawToken : input.split("[,\\s]+")) {
            String token = rawToken.trim();
            if (!token.isEmpty()) {
                Integer idx = parseInterestIndex(token, options.size());
                if (idx != null) {
                    toggleInterestSelection(interestSet, options.get(idx));
                }
            }
        }
    }

    @Nullable
    private Integer parseInterestIndex(String token, int size) {
        try {
            int idx = Integer.parseInt(token) - 1;
            if (idx < 0 || idx >= size) {
                logInfo("⚠️  Invalid selection: {}", token);
                return null;
            }
            return idx;
        } catch (NumberFormatException ex) {
            logDebug("Invalid interest selection: {}", ex.getMessage());
            logInfo("⚠️  Invalid selection: {}", token);
            return null;
        }
    }

    private void toggleInterestSelection(Set<Interest> interestSet, Interest interest) {
        if (interestSet.contains(interest)) {
            interestSet.remove(interest);
        } else if (interestSet.size() < Interest.MAX_PER_USER) {
            interestSet.add(interest);
        } else {
            logInfo("⚠️  You can select up to {} interests.", Interest.MAX_PER_USER);
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
                logInfo("⚠️  Invalid coordinates:");
                result.errors().forEach(e -> logInfo(INDENTED_BULLET, e));
            } else {
                currentUser.setLocation(lat, lon);
            }
        } catch (NumberFormatException e) {
            logDebug("Invalid coordinates: {}", e.getMessage());
            logInfo("⚠️  Invalid coordinates, skipping.");
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
                    logInfo("⚠️  Invalid distance:");
                    result.errors().forEach(e -> logInfo(INDENTED_BULLET, e));
                    logInfo("    Using default (50km)");
                } else {
                    currentUser.setMaxDistanceKm(dist);
                }
            } catch (NumberFormatException e) {
                logTrace("Using default distance, input was: {}", e.getMessage());
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
                logInfo("⚠️  Invalid age range:");
                result.errors().forEach(e -> logInfo(INDENTED_BULLET, e));
                logInfo("    Using defaults (18-99)");
            } else {
                currentUser.setAgeRange(minAge, maxAge);
            }
        } catch (NumberFormatException _) {
            logInfo("⚠️  Invalid age range, using defaults.");
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
        logInfo("\n" + CliSupport.HEADER_LIFESTYLE + "\n");

        String heightStr = inputReader.readLine("Height in cm (e.g., 175, or Enter to skip): ");
        if (!heightStr.isBlank()) {
            try {
                int height = Integer.parseInt(heightStr);
                ValidationService.ValidationResult result = validationService.validateHeight(height);
                if (!result.valid()) {
                    logInfo("⚠️  Invalid height:");
                    result.errors().forEach(e -> logInfo(INDENTED_BULLET, e));
                } else {
                    currentUser.setHeightCm(height);
                }
            } catch (NumberFormatException _) {
                logInfo("⚠️  Invalid height, skipping.");
            }
        }

        var smoking = EnumMenu.prompt(inputReader, Lifestyle.Smoking.class, "Select smoking:", true);
        smoking.ifPresent(currentUser::setSmoking);

        var drinking = EnumMenu.prompt(inputReader, Lifestyle.Drinking.class, "Select drinking:", true);
        drinking.ifPresent(currentUser::setDrinking);

        var wantsKids = EnumMenu.prompt(inputReader, Lifestyle.WantsKids.class, "Select kids preference:", true);
        wantsKids.ifPresent(currentUser::setWantsKids);

        var lookingFor = EnumMenu.prompt(inputReader, Lifestyle.LookingFor.class, "Select relationship goal:", true);
        lookingFor.ifPresent(currentUser::setLookingFor);
    }

    /**
     * Prompts the user to set their pace preferences (messaging frequency, depth,
     * etc.).
     *
     * @param currentUser The user whose pace preferences are being set
     */
    private void promptPacePreferences(User currentUser) {
        logInfo("\n--- PACE PREFERENCES ---\n");
        logInfo("These help us find people who share your communication style.\n");

        PacePreferences current = currentUser.getPacePreferences();

        var freq = EnumMenu.prompt(inputReader, MessagingFrequency.class, "Messaging frequency:", false)
                .orElse(current.messagingFrequency());

        var time = EnumMenu.prompt(inputReader, TimeToFirstDate.class, "Time to first date:", false)
                .orElse(current.timeToFirstDate());

        var style = EnumMenu.prompt(inputReader, CommunicationStyle.class, "Communication style:", false)
                .orElse(current.communicationStyle());

        var depth = EnumMenu.prompt(inputReader, DepthPreference.class, "Conversation depth:", false)
                .orElse(current.depthPreference());

        currentUser.setPacePreferences(new PacePreferences(freq, time, style, depth));
    }

    // --- Dealbreaker Helpers ---

    private void displayCurrentDealbreakers(User currentUser) {
        logInfo("\n" + CliSupport.SEPARATOR_LINE);
        logInfo("         SET YOUR DEALBREAKERS");
        logInfo(CliSupport.SEPARATOR_LINE + "\n");
        logInfo("Dealbreakers are HARD filters.\n");

        Dealbreakers current = currentUser.getDealbreakers();
        if (current.hasAnyDealbreaker()) {
            logInfo("Current dealbreakers:");
            if (current.hasSmokingDealbreaker()) {
                logInfo("  - Smoking: {}", current.acceptableSmoking());
            }
            if (current.hasDrinkingDealbreaker()) {
                logInfo("  - Drinking: {}", current.acceptableDrinking());
            }
            if (current.hasKidsDealbreaker()) {
                logInfo("  - Kids stance: {}", current.acceptableKidsStance());
            }
            if (current.hasLookingForDealbreaker()) {
                logInfo("  - Looking for: {}", current.acceptableLookingFor());
            }
            if (current.hasHeightDealbreaker()) {
                logInfo("  - Height: {} - {} cm", current.minHeightCm(), current.maxHeightCm());
            }
            if (current.hasAgeDealbreaker()) {
                logInfo("  - Max age diff: {} years", current.maxAgeDifference());
            }
            logInfo("");
        } else {
            logInfo("No dealbreakers set (showing everyone).\n");
        }
    }

    private void displayDealbreakerMenu() {
        logInfo(CliSupport.MENU_DIVIDER);
        logInfo("  1. Set smoking dealbreaker");
        logInfo("  2. Set drinking dealbreaker");
        logInfo("  3. Set kids stance dealbreaker");
        logInfo("  4. Set relationship goal dealbreaker");
        logInfo("  5. Set height range");
        logInfo("  6. Set max age difference");
        logInfo("  7. Clear all dealbreakers");
        logInfo("  0. Cancel");
        logInfo(CliSupport.MENU_DIVIDER + "\n");
    }

    // Note: I am copying the logic from Main.java for dealbreakers
    // Including the "copyExceptX" helpers directly here or refactoring.
    // Refactoring them to a Builder merge would be nicer on Dealbreakers class, but
    // for this task I will implement them as private helpers here to avoid changing
    // core classes too much.

    private void handleDealbreakerChoice(String choice, User currentUser) {
        Dealbreakers current = currentUser.getDealbreakers();
        switch (choice) {
            case "1" ->
                editEnumDealbreaker(
                        currentUser,
                        current,
                        Lifestyle.Smoking.class,
                        "Accept these smoking values:",
                        Dealbreakers.Builder::clearSmoking,
                        Dealbreakers.Builder::acceptSmoking,
                        "Smoking");
            case "2" ->
                editEnumDealbreaker(
                        currentUser,
                        current,
                        Lifestyle.Drinking.class,
                        "Accept these drinking values:",
                        Dealbreakers.Builder::clearDrinking,
                        Dealbreakers.Builder::acceptDrinking,
                        "Drinking");
            case "3" ->
                editEnumDealbreaker(
                        currentUser,
                        current,
                        Lifestyle.WantsKids.class,
                        "Accept these kids preferences:",
                        Dealbreakers.Builder::clearKids,
                        Dealbreakers.Builder::acceptKidsStance,
                        "Kids stance");
            case "4" ->
                editEnumDealbreaker(
                        currentUser,
                        current,
                        Lifestyle.LookingFor.class,
                        "Accept these relationship goals:",
                        Dealbreakers.Builder::clearLookingFor,
                        Dealbreakers.Builder::acceptLookingFor,
                        "Looking for");
            case "5" -> editHeightDealbreaker(currentUser, current);
            case "6" -> editAgeDealbreaker(currentUser, current);
            case "7" -> {
                currentUser.setDealbreakers(Dealbreakers.none());
                logInfo("✅ All dealbreakers cleared.\n");
            }
            case "0" -> logInfo(CliSupport.CANCELLED);
            default -> logInfo(CliSupport.INVALID_SELECTION);
        }
    }

    private <E extends Enum<E>> void editEnumDealbreaker(
            User currentUser,
            Dealbreakers current,
            Class<E> enumClass,
            String promptHeader,
            java.util.function.UnaryOperator<Dealbreakers.Builder> clearFn,
            java.util.function.BiFunction<Dealbreakers.Builder, E, Dealbreakers.Builder> acceptFn,
            String label) {
        var selected = EnumMenu.promptMultiple(inputReader, enumClass, promptHeader);
        Dealbreakers.Builder builder = clearFn.apply(current.toBuilder());
        for (var value : selected) {
            acceptFn.apply(builder, value);
        }
        currentUser.setDealbreakers(builder.build());
        logInfo("✅ {} dealbreaker updated.\n", label);
    }

    private void editHeightDealbreaker(User currentUser, Dealbreakers current) {
        logInfo("\nHeight range (in cm), or Enter to clear:");
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
            logInfo("✅ Height dealbreaker updated.\n");
        } catch (NumberFormatException _) {
            logInfo(CliSupport.INVALID_INPUT);
        } catch (IllegalArgumentException e) {
            logInfo("❌ {}\n", e.getMessage());
        }
    }

    private void editAgeDealbreaker(User currentUser, Dealbreakers current) {
        logInfo("\nMax age difference (years), or Enter to clear:");
        String input = inputReader.readLine("Max years: ");
        Dealbreakers.Builder builder = current.toBuilder().clearAge();
        if (!input.isBlank()) {
            try {
                builder.maxAgeDifference(Integer.parseInt(input));
                currentUser.setDealbreakers(builder.build());
                logInfo("✅ Age dealbreaker updated.\n");
            } catch (NumberFormatException _) {
                logInfo(CliSupport.INVALID_INPUT);
            }
        } else {
            currentUser.setDealbreakers(builder.build());
            logInfo("✅ Age dealbreaker cleared.\n");
        }
    }

    // --- Profile Notes Methods ---

    /**
     * Shows the note management menu for a specific user.
     *
     * @param subjectId   the ID of the user the note is about
     * @param subjectName the name of the user (for display)
     */
    public void manageNoteFor(UUID subjectId, String subjectName) {
        CliSupport.requireLogin(() -> {
            User currentUser = session.getCurrentUser();

            logInfo("\n" + CliSupport.MENU_DIVIDER);
            logInfo("       📝 NOTES ABOUT {}", subjectName.toUpperCase(Locale.ROOT));
            logInfo(CliSupport.MENU_DIVIDER);

            Optional<User.ProfileNote> existingNote = userStorage.getProfileNote(currentUser.getId(), subjectId);

            if (existingNote.isPresent()) {
                logInfo("\nCurrent note:");
                logInfo("\"{}\"", existingNote.get().content());
                logInfo("\n  1. Edit note");
                logInfo("  2. Delete note");
                logInfo("  0. Back");

                String choice = inputReader.readLine("\nChoice: ");
                switch (choice) {
                    case "1" -> editNote(existingNote.get());
                    case "2" -> deleteNote(currentUser.getId(), subjectId, subjectName);
                    default -> {
                        /* back */ }
                }
            } else {
                logInfo("\nNo notes yet.");
                logInfo("  1. Add a note");
                logInfo("  0. Back");

                String choice = inputReader.readLine("\nChoice: ");
                if ("1".equals(choice)) {
                    addNote(currentUser.getId(), subjectId, subjectName);
                }
            }
        });
    }

    /** Views all notes the current user has created. */
    public void viewAllNotes() {
        CliSupport.requireLogin(() -> {
            User currentUser = session.getCurrentUser();

            logInfo("\n" + CliSupport.MENU_DIVIDER);
            logInfo("         📝 MY PROFILE NOTES");
            logInfo(CliSupport.MENU_DIVIDER + "\n");

            List<User.ProfileNote> notes = userStorage.getProfileNotesByAuthor(currentUser.getId());

            if (notes.isEmpty()) {
                logInfo("You haven't added any notes yet.");
                logInfo("Tip: Add notes when viewing matches to remember details!\n");
                return;
            }

            logInfo("You have {} note(s):\n", notes.size());

            for (int i = 0; i < notes.size(); i++) {
                User.ProfileNote note = notes.get(i);
                User subject = userStorage.get(note.subjectId());
                String subjectName = subject != null ? subject.getName() : "(deleted user)";

                logInfo("  {}. {} - \"{}\"", i + 1, subjectName, note.getPreview());
            }

            logInfo("\nEnter number to view/edit, or 0 to go back:");
            String input = inputReader.readLine("Choice: ");

            try {
                int idx = Integer.parseInt(input) - 1;
                if (idx >= 0 && idx < notes.size()) {
                    User.ProfileNote note = notes.get(idx);
                    User subject = userStorage.get(note.subjectId());
                    String subjectName = subject != null ? subject.getName() : "this user";
                    manageNoteFor(note.subjectId(), subjectName);
                }
            } catch (NumberFormatException e) {
                logTrace("Non-numeric input for note selection: {}", e.getMessage());
                // Back to menu - user entered non-numeric input
            }
        });
    }

    /**
     * Gets the preview of a note for display in match lists.
     *
     * @param authorId  the note author
     * @param subjectId the subject of the note
     * @return the note preview, or empty string if no note exists
     */
    public String getNotePreview(UUID authorId, UUID subjectId) {
        return userStorage
                .getProfileNote(authorId, subjectId)
                .map(n -> "📝 " + n.getPreview())
                .orElse("");
    }

    /** Checks if a note exists for the given subject. */
    public boolean hasNote(UUID authorId, UUID subjectId) {
        return userStorage.getProfileNote(authorId, subjectId).isPresent();
    }

    private void addNote(UUID authorId, UUID subjectId, String subjectName) {
        logInfo("\nEnter your note about {} (max {} chars):", subjectName, User.ProfileNote.MAX_LENGTH);
        logInfo("Examples: \"Met at coffee shop\", \"Loves hiking\", \"Dinner Thursday 7pm\"");
        String content = inputReader.readLine("\nNote: ");

        if (content.isBlank()) {
            logInfo("⚠️  Note cannot be empty.");
            return;
        }

        if (content.length() > User.ProfileNote.MAX_LENGTH) {
            logInfo("⚠️  Note is too long ({} chars). Max is {} chars.", content.length(), User.ProfileNote.MAX_LENGTH);
            return;
        }

        try {
            User.ProfileNote note = User.ProfileNote.create(authorId, subjectId, content);
            userStorage.saveProfileNote(note);
            logInfo("✅ Note saved!\n");
        } catch (IllegalArgumentException e) {
            logInfo("❌ {}\n", e.getMessage());
        }
    }

    private void editNote(User.ProfileNote existing) {
        logInfo("\nCurrent note: \"{}\"\n", existing.content());
        logInfo("Enter new note (or press Enter to keep current):");
        String content = inputReader.readLine("Note: ");

        if (content.isBlank()) {
            logInfo("✓ Note unchanged.\n");
            return;
        }

        if (content.length() > User.ProfileNote.MAX_LENGTH) {
            logInfo("⚠️  Note is too long ({} chars). Max is {} chars.", content.length(), User.ProfileNote.MAX_LENGTH);
            return;
        }

        try {
            User.ProfileNote updated = existing.withContent(content);
            userStorage.saveProfileNote(updated);
            logInfo("✅ Note updated!\n");
        } catch (IllegalArgumentException e) {
            logInfo("❌ {}\n", e.getMessage());
        }
    }

    private void deleteNote(UUID authorId, UUID subjectId, String subjectName) {
        String confirm = inputReader.readLine("Delete note about " + subjectName + "? (y/n): ");
        if ("y".equalsIgnoreCase(confirm)) {
            if (userStorage.deleteProfileNote(authorId, subjectId)) {
                logInfo("✅ Note deleted.\n");
            } else {
                logInfo("⚠️  Note not found.\n");
            }
        } else {
            logInfo("Cancelled.\n");
        }
    }

    // --- User Creation and Selection Methods ---

    /** Creates a new user and sets them as the current user. */
    public void createUser() {
        logInfo("\n--- Create New User ---\n");

        String name = inputReader.readLine("Enter your name: ");
        if (name.isBlank()) {
            logInfo("❌ Name cannot be empty.\n");
            return;
        }

        User user = new User(UUID.randomUUID(), name);
        userStorage.save(user);
        session.setCurrentUser(user);

        logInfo("\n✅ User created! ID: {}", user.getId());
        logInfo("   Status: {} (Complete your profile to become ACTIVE)\n", user.getState());
    }

    /** Displays all users and allows selection of one as the current user. */
    public void selectUser() {
        logInfo("\n--- Select User ---\n");

        List<User> users = userStorage.findAll();
        if (users.isEmpty()) {
            logInfo("No users found. Create one first!\n");
            return;
        }

        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            logInfo("  {}. {} ({})", i + 1, u.getName(), u.getState());
        }

        String input = inputReader.readLine("\nSelect user number (or 0 to cancel): ");
        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx < 0 || idx >= users.size()) {
                if (idx != -1) {
                    logInfo("\n❌ Invalid selection.\n");
                }
                return;
            }
            session.setCurrentUser(users.get(idx));
            logInfo("\n✅ Selected: {}\n", session.getCurrentUser().getName());
        } catch (NumberFormatException _) {
            logInfo("❌ Invalid input.\n");
        }
    }

    /**
     * Displays the profile completion score and breakdown for the current user.
     */
    public void viewProfileScore() {
        CliSupport.requireLogin(() -> {
            User currentUser = session.getCurrentUser();
            ProfileCompletionService.CompletionResult result = profileCompletionService.calculate(currentUser);

            logInfo("\n───────────────────────────────────────");
            logInfo("      📊 PROFILE COMPLETION SCORE");
            logInfo("───────────────────────────────────────\n");

            logInfo("  {} {}% {}", result.getTierEmoji(), result.score(), result.tier());
            if (logger.isInfoEnabled()) {
                String overallBar = ProfileCompletionService.renderProgressBar(result.score(), 25);
                logInfo("  {}", overallBar);
            }
            logInfo("");

            // Category breakdown
            for (ProfileCompletionService.CategoryBreakdown cat : result.breakdown()) {
                logInfo("  {} - {}%", cat.category(), cat.score());
                if (logger.isInfoEnabled()) {
                    String categoryBar = ProfileCompletionService.renderProgressBar(cat.score(), 15);
                    logInfo(INDENTED_LINE, categoryBar);
                }
                if (!cat.missingItems().isEmpty()) {
                    cat.missingItems().forEach(m -> logInfo("    ⚪ {}", m));
                }
            }

            // Next steps
            if (!result.nextSteps().isEmpty()) {
                logInfo("\n  💡 NEXT STEPS:");
                result.nextSteps().forEach(s -> logInfo(INDENTED_LINE, s));
            }

            logInfo("");
            inputReader.readLine("  [Press Enter to return to menu]");
        });
    }
}
