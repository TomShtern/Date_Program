package datingapp.cli;

import datingapp.core.Achievement.UserAchievement;
import datingapp.core.AchievementService;
import datingapp.core.Dealbreakers;
import datingapp.core.MatchQualityService.InterestMatcher;
import datingapp.core.PacePreferences;
import datingapp.core.PacePreferences.CommunicationStyle;
import datingapp.core.PacePreferences.DepthPreference;
import datingapp.core.PacePreferences.MessagingFrequency;
import datingapp.core.PacePreferences.TimeToFirstDate;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.ProfileCompletionService;
import datingapp.core.ProfilePreviewService;
import datingapp.core.User;
import datingapp.core.UserStorage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for profile-related CLI operations. Manages profile completion, preview, and dealbreaker
 * settings.
 */
public class ProfileHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProfileHandler.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserStorage userStorage;
    private final ProfilePreviewService profilePreviewService;
    private final AchievementService achievementService;
    private final UserSession userSession;
    private final InputReader inputReader;

    private static final String PROMPT_CHOICE = "Your choice: ";
    private static final String PROMPT_CHOICES = "Choices: ";

    public ProfileHandler(
            UserStorage userStorage,
            ProfilePreviewService profilePreviewService,
            AchievementService achievementService,
            UserSession userSession,
            InputReader inputReader) {
        this.userStorage = userStorage;
        this.profilePreviewService = profilePreviewService;
        this.achievementService = achievementService;
        this.userSession = userSession;
        this.inputReader = inputReader;
    }

    /**
     * Guides the user through completing their profile with prompts for all required fields. Attempts
     * to activate the profile if complete.
     */
    public void completeProfile() {
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        User currentUser = userSession.getCurrentUser();
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
    }

    /**
     * Displays a preview of how the user's profile appears to other users, including completeness
     * percentage and improvement tips.
     */
    public void previewProfile() {
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        User currentUser = userSession.getCurrentUser();
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
    }

    /**
     * Allows the user to configure their dealbreakers - hard filters that exclude potential matches
     * based on lifestyle preferences.
     */
    public void setDealbreakers() {
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        User currentUser = userSession.getCurrentUser();
        displayCurrentDealbreakers(currentUser);
        displayDealbreakerMenu();

        String choice = inputReader.readLine(PROMPT_CHOICE);
        handleDealbreakerChoice(choice, currentUser);

        userStorage.save(currentUser);
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
                case "0" -> editing = false;
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
            currentUser.setLocation(lat, lon);
        } catch (NumberFormatException _) {
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
        try {
            int dist = Integer.parseInt(distStr);
            currentUser.setMaxDistanceKm(dist);
        } catch (NumberFormatException _) {
            // Keep default
        }

        String minAgeStr = inputReader.readLine("Min age preference (default 18): ");
        String maxAgeStr = inputReader.readLine("Max age preference (default 99): ");
        try {
            int minAge = minAgeStr.isBlank() ? 18 : Integer.parseInt(minAgeStr);
            int maxAge = maxAgeStr.isBlank() ? 99 : Integer.parseInt(maxAgeStr);
            currentUser.setAgeRange(minAge, maxAge);
        } catch (IllegalArgumentException _) {
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
     * Prompts the user to enter their lifestyle preferences (smoking, drinking, etc.).
     *
     * @param currentUser The user whose lifestyle preferences are being set
     */
    private void promptLifestyle(User currentUser) {
        logger.info("\n" + CliConstants.HEADER_LIFESTYLE + "\n");

        String heightStr = inputReader.readLine("Height in cm (e.g., 175, or Enter to skip): ");
        if (!heightStr.isBlank()) {
            try {
                currentUser.setHeightCm(Integer.valueOf(heightStr));
            } catch (NumberFormatException _) {
                logger.info("⚠️  Invalid height, skipping.");
            }
        }

        logger.info("Smoking: 1=Never, 2=Sometimes, 3=Regularly, 0=Skip");
        String smokingChoice = inputReader.readLine(PROMPT_CHOICE);
        Lifestyle.Smoking smoking =
                switch (smokingChoice) {
                    case "1" -> Lifestyle.Smoking.NEVER;
                    case "2" -> Lifestyle.Smoking.SOMETIMES;
                    case "3" -> Lifestyle.Smoking.REGULARLY;
                    default -> null;
                };
        if (smoking != null) {
            currentUser.setSmoking(smoking);
        }

        logger.info("Drinking: 1=Never, 2=Socially, 3=Regularly, 0=Skip");
        String drinkingChoice = inputReader.readLine(PROMPT_CHOICE);
        Lifestyle.Drinking drinking =
                switch (drinkingChoice) {
                    case "1" -> Lifestyle.Drinking.NEVER;
                    case "2" -> Lifestyle.Drinking.SOCIALLY;
                    case "3" -> Lifestyle.Drinking.REGULARLY;
                    default -> null;
                };
        if (drinking != null) {
            currentUser.setDrinking(drinking);
        }

        logger.info("Kids: 1=Don't want, 2=Open to it, 3=Want someday, 4=Have kids, 0=Skip");
        String kidsChoice = inputReader.readLine(PROMPT_CHOICE);
        Lifestyle.WantsKids wantsKids =
                switch (kidsChoice) {
                    case "1" -> Lifestyle.WantsKids.NO;
                    case "2" -> Lifestyle.WantsKids.OPEN;
                    case "3" -> Lifestyle.WantsKids.SOMEDAY;
                    case "4" -> Lifestyle.WantsKids.HAS_KIDS;
                    default -> null;
                };
        if (wantsKids != null) {
            currentUser.setWantsKids(wantsKids);
        }

        logger.info("Looking for: 1=Casual, 2=Short-term, 3=Long-term, 4=Marriage, 5=Unsure, 0=Skip");
        String lookingForChoice = inputReader.readLine(PROMPT_CHOICE);
        Lifestyle.LookingFor lookingFor =
                switch (lookingForChoice) {
                    case "1" -> Lifestyle.LookingFor.CASUAL;
                    case "2" -> Lifestyle.LookingFor.SHORT_TERM;
                    case "3" -> Lifestyle.LookingFor.LONG_TERM;
                    case "4" -> Lifestyle.LookingFor.MARRIAGE;
                    case "5" -> Lifestyle.LookingFor.UNSURE;
                    default -> null;
                };
        if (lookingFor != null) {
            currentUser.setLookingFor(lookingFor);
        }
    }

    /**
     * Prompts the user to set their pace preferences (messaging frequency, depth, etc.).
     *
     * @param currentUser The user whose pace preferences are being set
     */
    private void promptPacePreferences(User currentUser) {
        logger.info("\n--- PACE PREFERENCES ---\n");
        logger.info("These help us find people who share your communication style.\n");

        PacePreferences current = currentUser.getPacePreferences();

        logger.info("Messaging Frequency: 1=Rarely, 2=Often, 3=Constantly, 0=Wildcard");
        String freqStr = inputReader.readLine(PROMPT_CHOICE);
        MessagingFrequency freq =
                switch (freqStr) {
                    case "1" -> MessagingFrequency.RARELY;
                    case "2" -> MessagingFrequency.OFTEN;
                    case "3" -> MessagingFrequency.CONSTANTLY;
                    case "0" -> MessagingFrequency.WILDCARD;
                    default -> current.messagingFrequency();
                };

        logger.info("Time to First Date: 1=Quickly, 2=Few Days, 3=Weeks, 4=Months, 0=Wildcard");
        String timeStr = inputReader.readLine(PROMPT_CHOICE);
        TimeToFirstDate time =
                switch (timeStr) {
                    case "1" -> TimeToFirstDate.QUICKLY;
                    case "2" -> TimeToFirstDate.FEW_DAYS;
                    case "3" -> TimeToFirstDate.WEEKS;
                    case "4" -> TimeToFirstDate.MONTHS;
                    case "0" -> TimeToFirstDate.WILDCARD;
                    default -> current.timeToFirstDate();
                };

        logger.info("Communication Style: 1=Text, 2=Voice, 3=Video, 4=In Person, 0=Wildcard");
        String styleStr = inputReader.readLine(PROMPT_CHOICE);
        CommunicationStyle style =
                switch (styleStr) {
                    case "1" -> CommunicationStyle.TEXT_ONLY;
                    case "2" -> CommunicationStyle.VOICE_NOTES;
                    case "3" -> CommunicationStyle.VIDEO_CALLS;
                    case "4" -> CommunicationStyle.IN_PERSON_ONLY;
                    case "0" -> CommunicationStyle.MIX_OF_EVERYTHING;
                    default -> current.communicationStyle();
                };

        logger.info("Conversation Depth: 1=Small Talk, 2=Deep, 3=Existential, 0=Wildcard");
        String depthStr = inputReader.readLine(PROMPT_CHOICE);
        DepthPreference depth =
                switch (depthStr) {
                    case "1" -> DepthPreference.SMALL_TALK;
                    case "2" -> DepthPreference.DEEP_CHAT;
                    case "3" -> DepthPreference.EXISTENTIAL;
                    case "0" -> DepthPreference.DEPENDS_ON_VIBE;
                    default -> current.depthPreference();
                };

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
        logger.info("\nAcceptable smoking (comma-separated, e.g., 1,2):");
        logger.info("  1=Never, 2=Sometimes, 3=Regularly, 0=Clear");
        String input = inputReader.readLine(PROMPT_CHOICES);

        Dealbreakers.Builder builder = current.toBuilder().clearSmoking();
        if (!input.equals("0")) {
            for (String s : input.split(",")) {
                switch (s.trim()) {
                    case "1" -> builder.acceptSmoking(Lifestyle.Smoking.NEVER);
                    case "2" -> builder.acceptSmoking(Lifestyle.Smoking.SOMETIMES);
                    case "3" -> builder.acceptSmoking(Lifestyle.Smoking.REGULARLY);
                    default -> {
                        // Ignore
                    }
                }
            }
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Smoking dealbreaker updated.\n");
    }

    private void editDrinkingDealbreaker(User currentUser, Dealbreakers current) {
        logger.info("\nAcceptable drinking (comma-separated):");
        logger.info("  1=Never, 2=Socially, 3=Regularly, 0=Clear");
        String input = inputReader.readLine(PROMPT_CHOICES);
        Dealbreakers.Builder builder = current.toBuilder().clearDrinking();
        if (!input.equals("0")) {
            for (String s : input.split(",")) {
                switch (s.trim()) {
                    case "1" -> builder.acceptDrinking(Lifestyle.Drinking.NEVER);
                    case "2" -> builder.acceptDrinking(Lifestyle.Drinking.SOCIALLY);
                    case "3" -> builder.acceptDrinking(Lifestyle.Drinking.REGULARLY);
                    default -> {
                        // Ignore
                    }
                }
            }
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Drinking dealbreaker updated.\n");
    }

    private void editKidsDealbreaker(User currentUser, Dealbreakers current) {
        logger.info("\nAcceptable kids stance (comma-separated):");
        logger.info("  1=Don't want, 2=Open, 3=Want someday, 4=Has kids, 0=Clear");
        String input = inputReader.readLine(PROMPT_CHOICES);
        Dealbreakers.Builder builder = current.toBuilder().clearKids();
        if (!input.equals("0")) {
            for (String s : input.split(",")) {
                switch (s.trim()) {
                    case "1" -> builder.acceptKidsStance(Lifestyle.WantsKids.NO);
                    case "2" -> builder.acceptKidsStance(Lifestyle.WantsKids.OPEN);
                    case "3" -> builder.acceptKidsStance(Lifestyle.WantsKids.SOMEDAY);
                    case "4" -> builder.acceptKidsStance(Lifestyle.WantsKids.HAS_KIDS);
                    default -> {
                        // Ignore
                    }
                }
            }
        }
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Kids stance dealbreaker updated.\n");
    }

    private void editLookingForDealbreaker(User currentUser, Dealbreakers current) {
        logger.info("\nAcceptable relationship goals (comma-separated):");
        logger.info("  1=Casual, 2=Short-term, 3=Long-term, 4=Marriage, 5=Unsure, 0=Clear");
        String input = inputReader.readLine(PROMPT_CHOICES);
        Dealbreakers.Builder builder = current.toBuilder().clearLookingFor();
        if (!input.equals("0")) {
            for (String s : input.split(",")) {
                switch (s.trim()) {
                    case "1" -> builder.acceptLookingFor(Lifestyle.LookingFor.CASUAL);
                    case "2" -> builder.acceptLookingFor(Lifestyle.LookingFor.SHORT_TERM);
                    case "3" -> builder.acceptLookingFor(Lifestyle.LookingFor.LONG_TERM);
                    case "4" -> builder.acceptLookingFor(Lifestyle.LookingFor.MARRIAGE);
                    case "5" -> builder.acceptLookingFor(Lifestyle.LookingFor.UNSURE);
                    default -> {
                        // Ignore
                    }
                }
            }
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

    /**
     * Displays the profile completion score and breakdown for the current user.
     */
    public void viewProfileScore() {
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        User currentUser = userSession.getCurrentUser();
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
    }
}
