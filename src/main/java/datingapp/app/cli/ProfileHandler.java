package datingapp.app.cli;

import datingapp.app.cli.CliTextAndInput.EnumMenu;
import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.ProfileUseCases.SaveProfileCommand;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.EnumSetUtil;
import datingapp.core.LoggingSupport;
import datingapp.core.ServiceRegistry;
import datingapp.core.TextUtil;
import datingapp.core.i18n.I18n;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.LocationModels.City;
import datingapp.core.model.LocationModels.Country;
import datingapp.core.model.LocationModels.ResolvedLocation;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.UserStorage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    private static final String ERROR_MESSAGE_FORMAT = "❌ {}\n";
    private static final String ERROR_WITH_GAP_FORMAT = "\n❌ {}\n";
    private static final String WARNING_MESSAGE_FORMAT = "⚠️  {}";
    private static final String PRESS_ENTER_MENU_KEY = "cli.common.press_enter_menu";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final UserStorage userStorage;
    private final ValidationService validationService;
    private final LocationService locationService;
    private final AppConfig config;
    private final ProfileUseCases profileUseCases;
    private final AppSession session;
    private final InputReader inputReader;

    private static final String PROMPT_CHOICE = "Your choice: ";

    public ProfileHandler(
            UserStorage userStorage,
            ValidationService validationService,
            LocationService locationService,
            ProfileUseCases profileUseCases,
            AppConfig config,
            AppSession session,
            InputReader inputReader) {
        this.userStorage = Objects.requireNonNull(userStorage);
        this.validationService = Objects.requireNonNull(validationService, "validationService cannot be null");
        this.locationService = Objects.requireNonNull(locationService, "locationService cannot be null");
        this.profileUseCases = Objects.requireNonNull(profileUseCases, "profileUseCases cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.session = session;
        this.inputReader = inputReader;
    }

    public ProfileHandler(
            UserStorage userStorage,
            ValidationService validationService,
            ProfileUseCases profileUseCases,
            AppConfig config,
            AppSession session,
            InputReader inputReader) {
        this(
                userStorage,
                validationService,
                new LocationService(validationService),
                profileUseCases,
                config,
                session,
                inputReader);
    }

    public static ProfileHandler fromServices(ServiceRegistry services, AppSession session, InputReader inputReader) {
        Objects.requireNonNull(services, "services cannot be null");
        return new ProfileHandler(
                services.getUserStorage(),
                services.getValidationService(),
                services.getLocationService(),
                services.getProfileUseCases(),
                services.getConfig(),
                session,
                inputReader);
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
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            logInfo("\n" + I18n.text("cli.profile.complete.header", currentUser.getName()) + "\n");

            User draft = copyForProfileEditing(currentUser);

            promptBio(draft);
            promptBirthDate(draft);
            promptGender(draft);
            promptInterestedIn(draft);
            promptInterests(draft);
            promptLocation(draft);
            promptPreferences(draft);
            promptPhoto(draft);
            promptLifestyle(draft);
            promptPacePreferences(draft);

            var saveResult =
                    profileUseCases.saveProfile(new SaveProfileCommand(UserContext.cli(currentUser.getId()), draft));
            if (!saveResult.success()) {
                logInfo(ERROR_WITH_GAP_FORMAT, saveResult.error().message());
                return;
            }

            session.setCurrentUser(saveResult.data().user());

            if (saveResult.data().activated()) {
                logInfo("\n" + I18n.text("cli.profile.complete.activated"));
            } else if (!saveResult.data().user().isComplete()) {
                logInfo("\n" + I18n.text("cli.profile.complete.incomplete"));
            }

            logInfo(I18n.text("cli.profile.complete.saved") + "\n");
            displayNewAchievements(saveResult.data().newlyUnlocked());
        });
    }

    private static User copyForProfileEditing(User source) {
        User.StorageBuilder builder = User.StorageBuilder.create(
                        source.getId(), source.getName(), source.getCreatedAt())
                .bio(source.getBio())
                .birthDate(source.getBirthDate())
                .gender(source.getGender())
                .interestedIn(source.getInterestedIn())
                .location(source.getLat(), source.getLon())
                .hasLocationSet(source.hasLocationSet())
                .maxDistanceKm(source.getMaxDistanceKm())
                .ageRange(source.getMinAge(), source.getMaxAge())
                .state(source.getState())
                .verified(source.isVerified())
                .verificationMethod(source.getVerificationMethod())
                .verificationCode(source.getVerificationCode())
                .verificationSentAt(source.getVerificationSentAt())
                .verifiedAt(source.getVerifiedAt())
                .updatedAt(source.getUpdatedAt())
                .deletedAt(source.getDeletedAt())
                .photoUrls(source.getPhotoUrls())
                .interests(source.getInterests())
                .smoking(source.getSmoking())
                .drinking(source.getDrinking())
                .wantsKids(source.getWantsKids())
                .lookingFor(source.getLookingFor())
                .education(source.getEducation())
                .heightCm(source.getHeightCm())
                .email(source.getEmail())
                .phone(source.getPhone())
                .pacePreferences(source.getPacePreferences());
        User copy = builder.build();
        copy.setDealbreakers(source.getDealbreakers());
        return copy;
    }

    /**
     * Displays a preview of how the user's profile appears to other users,
     * including completeness
     * percentage and improvement tips.
     */
    public void previewProfile() {
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            var previewResult = profileUseCases.generatePreview(currentUser);
            if (!previewResult.success()) {
                logInfo(ERROR_WITH_GAP_FORMAT, previewResult.error().message());
                inputReader.readLine(I18n.text(PRESS_ENTER_MENU_KEY));
                return;
            }
            ProfileService.ProfilePreview preview = previewResult.data();

            logInfo("\n" + CliTextAndInput.SEPARATOR_LINE);
            logInfo(I18n.text("cli.profile.preview.title"));
            logInfo(CliTextAndInput.SEPARATOR_LINE);
            logInfo("");
            logInfo(I18n.text("cli.profile.preview.subtitle"));
            logInfo("");

            // Card display
            logInfo(CliTextAndInput.BOX_TOP);
            String verifiedBadge = currentUser.isVerified() ? " ✅ Verified" : "";
            int age = currentUser.getAge(java.time.ZoneId.systemDefault()).orElse(0);
            logInfo("│ 💝 {}, {} years old{}", currentUser.getName(), age, verifiedBadge);
            logInfo("│ 📍 Location: {}", locationService.formatForDisplay(currentUser.getLat(), currentUser.getLon()));
            String bio = preview.displayBio();
            if (bio.length() > 50) {
                bio = bio.substring(0, 47) + "...";
            }
            logInfo(CliTextAndInput.PROFILE_BIO_FORMAT, bio);
            if (preview.displayLookingFor() != null) {
                logInfo("│ 💭 {}", preview.displayLookingFor());
            }
            logInfo(CliTextAndInput.BOX_BOTTOM);

            // Completeness
            ProfileService.ProfileCompleteness comp = preview.completeness();
            logInfo("");
            logInfo(I18n.text("cli.profile.preview.completeness", comp.percentage()));

            // Render progress bar when profile has some completeness
            if (comp.percentage() > 0 && logger.isInfoEnabled()) {
                String progressBar = TextUtil.renderProgressBar(comp.percentage() / 100.0, 20);
                logInfo("  {}", progressBar);
            }

            if (!comp.missingFields().isEmpty()) {
                logInfo("");
                logInfo(I18n.text("cli.profile.preview.missing"));
                comp.missingFields().forEach(f -> logInfo("    • {}", f));
            }

            // Tips
            if (!preview.improvementTips().isEmpty()) {
                logInfo("");
                logInfo(I18n.text("cli.profile.preview.tips"));
                preview.improvementTips().forEach(tip -> logInfo(INDENTED_LINE, tip));
            }

            logInfo("");
            inputReader.readLine(I18n.text(PRESS_ENTER_MENU_KEY));
        });
    }

    /**
     * Allows the user to configure their dealbreakers - hard filters that exclude
     * potential matches
     * based on lifestyle MatchPreferences.
     */
    public void setDealbreakers() {
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            boolean editing = true;
            while (editing) {
                displayCurrentDealbreakers(currentUser);
                displayDealbreakerMenu();

                String choice = inputReader.readLine(PROMPT_CHOICE).trim();
                if (choice.isEmpty() && inputReader.wasInputExhausted()) {
                    choice = "0";
                }
                if ("0".equals(choice)) {
                    logInfo(CliTextAndInput.CANCELLED);
                    editing = false;
                } else {
                    handleDealbreakerChoice(choice, currentUser);
                    userStorage.save(currentUser);
                }
            }
        });
    }

    // --- Helper Methods ---

    /**
     * Checks for newly unlocked achievements and displays them to the user.
     *
     * @param currentUser The user to check achievements for
     */
    private void displayNewAchievements(List<UserAchievement> newAchievements) {
        if (!newAchievements.isEmpty()) {
            logInfo("\n" + I18n.text("cli.profile.achievements.title"));
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
        if (bio.isBlank()) {
            return;
        }

        ValidationService.ValidationResult bioResult = validationService.validateBio(bio);
        if (!bioResult.valid()) {
            logInfo("⚠️  Invalid bio:");
            bioResult.errors().forEach(error -> logInfo(INDENTED_BULLET, error));
            return;
        }

        currentUser.setBio(bio.trim());
    }

    private void promptBirthDate(User currentUser) {
        String birthStr = inputReader.readLine("Birth date (yyyy-MM-dd): ");
        if (birthStr == null || birthStr.isBlank()) {
            logInfo("⚠️  Invalid date, skipping.");
            return;
        }
        try {
            LocalDate birthDate = LocalDate.parse(birthStr, DATE_FORMAT);
            ValidationService.ValidationResult result = validationService.validateBirthDate(birthDate);
            if (!result.valid()) {
                String message = result.errors().isEmpty()
                        ? "Invalid date"
                        : result.errors().getFirst();
                logInfo(WARNING_MESSAGE_FORMAT, message);
                return;
            }
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
        logInfo("\n" + CliTextAndInput.GENDER_OPTIONS);
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
        logInfo("\n" + CliTextAndInput.INTERESTED_IN_PROMPT);
        String interestedStr = inputReader.readLine("Your preferences: ");
        Set<Gender> interestedIn = parseGenderSet(interestedStr);
        if (!interestedIn.isEmpty()) {
            currentUser.setInterestedIn(interestedIn);
        }
    }

    private Set<Gender> parseGenderSet(String input) {
        String normalized = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
        Set<Gender> result = EnumSet.noneOf(Gender.class);

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
            if (choice.isEmpty() && inputReader.wasInputExhausted()) {
                logDebug("Input exhausted while editing interests; returning to previous menu.");
                break;
            }
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
                case "0" -> editing = false; // NOPMD AssignmentInOperand
                default -> logInfo(CliTextAndInput.INVALID_SELECTION);
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
        Country selectedCountry = promptCountry();
        Optional<ResolvedLocation> resolved = promptCitySelection(selectedCountry.code());
        if (resolved.isEmpty()) {
            resolved = promptZipSelection(selectedCountry.code());
        }
        if (resolved.isPresent()) {
            ResolvedLocation location = resolved.get();
            currentUser.setLocation(location.latitude(), location.longitude());
            logInfo("✅ Location set to {}.", location.label());
            return;
        }
        logInfo("⚠️  Location skipped.");
    }

    private Country promptCountry() {
        Country defaultCountry = locationService.getDefaultCountry();
        List<Country> countries = locationService.getAvailableCountries();
        logInfo("\n--- LOCATION ---");
        for (int i = 0; i < countries.size(); i++) {
            Country country = countries.get(i);
            String defaultMarker = country.defaultSelection() ? " [default]" : "";
            logInfo("  {}. {}{}", i + 1, country.displayName(), defaultMarker);
        }
        String input = inputReader
                .readLine("Select country (press Enter for default): ")
                .trim();
        if (input.isBlank()) {
            return defaultCountry;
        }
        try {
            int selectedIndex = Integer.parseInt(input) - 1;
            if (selectedIndex < 0 || selectedIndex >= countries.size()) {
                logInfo("⚠️  Invalid selection. Using {}.", defaultCountry.displayName());
                return defaultCountry;
            }
            Country selectedCountry = countries.get(selectedIndex);
            if (!selectedCountry.available()) {
                logInfo("⚠️  {} is coming soon. Using {}.", selectedCountry.name(), defaultCountry.displayName());
                return defaultCountry;
            }
            return selectedCountry;
        } catch (NumberFormatException _) {
            logInfo("⚠️  Invalid selection. Using {}.", defaultCountry.displayName());
            return defaultCountry;
        }
    }

    private Optional<ResolvedLocation> promptCitySelection(String countryCode) {
        List<City> popularCities = locationService.getPopularCities(countryCode, 5);
        if (!popularCities.isEmpty()) {
            logInfo("Popular cities:");
            for (int i = 0; i < popularCities.size(); i++) {
                logInfo("  {}. {}", i + 1, popularCities.get(i).displayName());
            }
        }
        String query = inputReader
                .readLine("City name (press Enter to use ZIP instead): ")
                .trim();
        if (query.isBlank()) {
            return Optional.empty();
        }
        List<City> results = locationService.searchCities(countryCode, query, 10);
        if (results.isEmpty()) {
            logInfo("⚠️  No matching cities found. Try ZIP instead.");
            return Optional.empty();
        }
        logInfo("Matching cities:");
        for (int i = 0; i < results.size(); i++) {
            logInfo("  {}. {}", i + 1, results.get(i).displayName());
        }
        String selection =
                inputReader.readLine("Choose city number (or 0 to use ZIP): ").trim();
        try {
            int selectedIndex = Integer.parseInt(selection) - 1;
            if (selectedIndex == -1) {
                return Optional.empty();
            }
            if (selectedIndex < 0 || selectedIndex >= results.size()) {
                logInfo("⚠️  Invalid city selection. Try ZIP instead.");
                return Optional.empty();
            }
            return Optional.of(locationService.resolveCity(results.get(selectedIndex)));
        } catch (NumberFormatException _) {
            logInfo("⚠️  Invalid city selection. Try ZIP instead.");
            return Optional.empty();
        }
    }

    private Optional<ResolvedLocation> promptZipSelection(String countryCode) {
        String zipCode =
                inputReader.readLine("ZIP code (press Enter to skip): ").trim();
        if (zipCode.isBlank()) {
            return Optional.empty();
        }
        LocationService.ZipLookupResult lookupResult = locationService.lookupZip(countryCode, zipCode);
        if (!lookupResult.valid()) {
            logInfo(WARNING_MESSAGE_FORMAT, lookupResult.message());
            return Optional.empty();
        }
        if (lookupResult.resolvedLocation().isEmpty()) {
            logInfo(WARNING_MESSAGE_FORMAT, lookupResult.message());
            return Optional.empty();
        }
        return lookupResult.resolvedLocation();
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
                    currentUser.setMaxDistanceKm(dist, config.matching().maxDistanceKm());
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
                currentUser.setAgeRange(
                        minAge,
                        maxAge,
                        config.validation().minAge(),
                        config.validation().maxAge());
            }
        } catch (NumberFormatException _) {
            logInfo("⚠️  Invalid age range, using defaults.");
        }
    }

    private void promptPhoto(User currentUser) {
        String photoUrl = inputReader.readLine("Photo URL (or press Enter to skip): ");
        if (photoUrl.isBlank()) {
            return;
        }

        ValidationService.ValidationResult photoResult = validationService.validatePhotoUrl(photoUrl);
        if (!photoResult.valid()) {
            logInfo("⚠️  Invalid photo URL:");
            photoResult.errors().forEach(error -> logInfo(INDENTED_BULLET, error));
            return;
        }

        try {
            currentUser.addPhotoUrl(ValidationService.normalizePhotoUrl(photoUrl));
        } catch (IllegalArgumentException e) {
            logInfo(WARNING_MESSAGE_FORMAT, e.getMessage());
        }
    }

    /**
     * Prompts the user to enter their lifestyle preferences (smoking, drinking,
     * etc.).
     *
     * @param currentUser The user whose lifestyle preferences are being set
     */
    private void promptLifestyle(User currentUser) {
        logInfo("\n" + CliTextAndInput.HEADER_LIFESTYLE + "\n");

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
        logInfo("\n" + CliTextAndInput.SEPARATOR_LINE);
        logInfo("         SET YOUR DEALBREAKERS");
        logInfo(CliTextAndInput.SEPARATOR_LINE + "\n");
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
        logInfo(CliTextAndInput.MENU_DIVIDER);
        logInfo("  1. Set smoking dealbreaker");
        logInfo("  2. Set drinking dealbreaker");
        logInfo("  3. Set kids stance dealbreaker");
        logInfo("  4. Set relationship goal dealbreaker");
        logInfo("  5. Set height range");
        logInfo("  6. Set max age difference");
        logInfo("  7. Clear all dealbreakers");
        logInfo("  0. Cancel");
        logInfo(CliTextAndInput.MENU_DIVIDER + "\n");
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
            case "0" -> logInfo(CliTextAndInput.CANCELLED);
            default -> logInfo(CliTextAndInput.INVALID_SELECTION);
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
            logInfo(CliTextAndInput.INVALID_INPUT);
        } catch (IllegalArgumentException e) {
            logInfo(ERROR_MESSAGE_FORMAT, e.getMessage());
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
                logInfo(CliTextAndInput.INVALID_INPUT);
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
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();

            logInfo("\n" + CliTextAndInput.MENU_DIVIDER);
            logInfo("       📝 NOTES ABOUT {}", subjectName.toUpperCase(Locale.ROOT));
            logInfo(CliTextAndInput.MENU_DIVIDER);

            Optional<ProfileNote> existingNote = userStorage.getProfileNote(currentUser.getId(), subjectId);

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
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();

            logInfo("\n" + CliTextAndInput.MENU_DIVIDER);
            logInfo("         📝 MY PROFILE NOTES");
            logInfo(CliTextAndInput.MENU_DIVIDER + "\n");

            List<ProfileNote> notes = userStorage.getProfileNotesByAuthor(currentUser.getId());

            if (notes.isEmpty()) {
                logInfo("You haven't added any notes yet.");
                logInfo("Tip: Add notes when viewing matches to remember details!\n");
                return;
            }

            logInfo("You have {} note(s):\n", notes.size());

            for (int i = 0; i < notes.size(); i++) {
                ProfileNote note = notes.get(i);
                User subject = userStorage.get(note.subjectId()).orElse(null);
                String subjectName = subject != null ? subject.getName() : "(deleted user)";

                logInfo("  {}. {} - \"{}\"", i + 1, subjectName, note.getPreview());
            }

            logInfo("\nEnter number to view/edit, or 0 to go back:");
            String input = inputReader.readLine("Choice: ");

            try {
                int idx = Integer.parseInt(input) - 1;
                if (idx >= 0 && idx < notes.size()) {
                    ProfileNote note = notes.get(idx);
                    User subject = userStorage.get(note.subjectId()).orElse(null);
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
        logInfo("\nEnter your note about {} (max {} chars):", subjectName, ProfileNote.MAX_LENGTH);
        logInfo("Examples: \"Met at coffee shop\", \"Loves hiking\", \"Dinner Thursday 7pm\"");
        String content = inputReader.readLine("\nNote: ");

        if (content.isBlank()) {
            logInfo("⚠️  Note cannot be empty.");
            return;
        }

        if (content.length() > ProfileNote.MAX_LENGTH) {
            logInfo("⚠️  Note is too long ({} chars). Max is {} chars.", content.length(), ProfileNote.MAX_LENGTH);
            return;
        }

        try {
            ProfileNote note = ProfileNote.create(authorId, subjectId, content);
            userStorage.saveProfileNote(note);
            logInfo("✅ Note saved!\n");
        } catch (IllegalArgumentException e) {
            logInfo(ERROR_MESSAGE_FORMAT, e.getMessage());
        }
    }

    private void editNote(ProfileNote existing) {
        logInfo("\nCurrent note: \"{}\"\n", existing.content());
        logInfo("Enter new note (or press Enter to keep current):");
        String content = inputReader.readLine("Note: ");

        if (content.isBlank()) {
            logInfo("✓ Note unchanged.\n");
            return;
        }

        if (content.length() > ProfileNote.MAX_LENGTH) {
            logInfo("⚠️  Note is too long ({} chars). Max is {} chars.", content.length(), ProfileNote.MAX_LENGTH);
            return;
        }

        try {
            ProfileNote updated = existing.withContent(content);
            userStorage.saveProfileNote(updated);
            logInfo("✅ Note updated!\n");
        } catch (IllegalArgumentException e) {
            logInfo(ERROR_MESSAGE_FORMAT, e.getMessage());
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
        ValidationService.ValidationResult nameResult = validationService.validateName(name);
        if (!nameResult.valid()) {
            logInfo("❌ Invalid name:");
            nameResult.errors().forEach(error -> logInfo("   • {}", error));
            logInfo("");
            return;
        }

        User user = new User(UUID.randomUUID(), name.trim());
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
        CliTextAndInput.requireLogin(session, () -> {
            User currentUser = session.getCurrentUser();
            var completionResult = profileUseCases.calculateCompletion(currentUser);
            if (!completionResult.success()) {
                logInfo(ERROR_WITH_GAP_FORMAT, completionResult.error().message());
                inputReader.readLine(I18n.text(PRESS_ENTER_MENU_KEY));
                return;
            }
            ProfileService.CompletionResult result = completionResult.data();

            logInfo("\n───────────────────────────────────────");
            logInfo("      📊 PROFILE COMPLETION SCORE");
            logInfo("───────────────────────────────────────\n");

            logInfo("  {} {}% {}", result.getTierEmoji(), result.score(), result.tier());
            if (logger.isInfoEnabled()) {
                String overallBar = TextUtil.renderProgressBar(result.score() / 100.0, 25);
                logInfo("  {}", overallBar);
            }
            logInfo("");

            // Category breakdown
            for (ProfileService.CategoryBreakdown cat : result.breakdown()) {
                logInfo("  {} - {}%", cat.category(), cat.score());
                if (logger.isInfoEnabled()) {
                    String categoryBar = TextUtil.renderProgressBar(cat.score() / 100.0, 15);
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
            inputReader.readLine(I18n.text(PRESS_ENTER_MENU_KEY));
        });
    }
}
