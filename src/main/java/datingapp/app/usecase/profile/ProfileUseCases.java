package datingapp.app.usecase.profile;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.SanitizerUtils;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.AccountCleanupStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Profile and user-progress application use-cases shared across adapters. */
@SuppressWarnings("java:S107")
public class ProfileUseCases {

    private static final Logger logger = LoggerFactory.getLogger(ProfileUseCases.class);
    private static final String CONTEXT_REQUIRED = "Context is required";
    private static final String PROFILE_SERVICE_REQUIRED = "ProfileService is required";
    private static final String USER_STORAGE_REQUIRED = "UserStorage is required";
    private static final String USER_NOT_FOUND = "User not found";
    private static final String AUTHOR_NOT_FOUND = "Author not found";
    private static final String CONTEXT_AND_SUBJECT_REQUIRED = "Context and subjectId are required";
    private static final String PROFILE_NOTE_TOO_LONG = "Note content exceeds maximum length of %d characters";

    private final UserStorage userStorage;
    private final ProfileService profileService;
    private final ValidationService validationService;
    private final ActivityMetricsService activityMetricsService;
    private final AchievementService achievementService;
    private final AccountCleanupStorage accountCleanupStorage;
    private final AppConfig config;
    private final ProfileActivationPolicy activationPolicy;
    private final AppEventBus eventBus;

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("java:S107")
    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ActivityMetricsService activityMetricsService,
            AchievementService achievementService,
            AppConfig config,
            ProfileActivationPolicy activationPolicy,
            AppEventBus eventBus) {
        this(
                userStorage,
                profileService,
                validationService,
                activityMetricsService,
                achievementService,
                config,
                activationPolicy,
                eventBus,
                null);
    }

    @SuppressWarnings("java:S107")
    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ActivityMetricsService activityMetricsService,
            AchievementService achievementService,
            AppConfig config,
            ProfileActivationPolicy activationPolicy,
            AppEventBus eventBus,
            AccountCleanupStorage accountCleanupStorage) {
        this.userStorage = userStorage;
        this.profileService = profileService;
        this.validationService = validationService;
        this.activityMetricsService = activityMetricsService;
        this.achievementService = achievementService;
        this.accountCleanupStorage = accountCleanupStorage;
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.activationPolicy = Objects.requireNonNull(activationPolicy, "activationPolicy cannot be null");
        this.eventBus = eventBus;
    }

    public static final class Builder {
        private UserStorage userStorage;
        private ProfileService profileService;
        private ValidationService validationService;
        private ActivityMetricsService activityMetricsService;
        private AchievementService achievementService;
        private AccountCleanupStorage accountCleanupStorage;
        private AppConfig config;
        private ProfileActivationPolicy activationPolicy = new ProfileActivationPolicy();
        private AppEventBus eventBus;

        private Builder() {}

        public Builder userStorage(UserStorage userStorage) {
            this.userStorage = userStorage;
            return this;
        }

        public Builder profileService(ProfileService profileService) {
            this.profileService = profileService;
            return this;
        }

        public Builder validationService(ValidationService validationService) {
            this.validationService = validationService;
            return this;
        }

        public Builder activityMetricsService(ActivityMetricsService activityMetricsService) {
            this.activityMetricsService = activityMetricsService;
            return this;
        }

        public Builder achievementService(AchievementService achievementService) {
            this.achievementService = achievementService;
            return this;
        }

        public Builder accountCleanupStorage(AccountCleanupStorage accountCleanupStorage) {
            this.accountCleanupStorage = accountCleanupStorage;
            return this;
        }

        public Builder config(AppConfig config) {
            this.config = config;
            return this;
        }

        public Builder activationPolicy(ProfileActivationPolicy activationPolicy) {
            this.activationPolicy = activationPolicy;
            return this;
        }

        public Builder eventBus(AppEventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public ProfileUseCases build() {
            return new ProfileUseCases(
                    userStorage,
                    profileService,
                    validationService,
                    activityMetricsService,
                    achievementService,
                    config,
                    activationPolicy,
                    eventBus,
                    accountCleanupStorage);
        }
    }

    public UseCaseResult<ProfileSaveResult> saveProfile(SaveProfileCommand command) {
        if (command == null || command.context() == null || command.user() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and user are required"));
        }
        if (userStorage == null || profileService == null) {
            return UseCaseResult.failure(UseCaseError.dependency("UserStorage and ProfileService are required"));
        }
        if (!command.context().userId().equals(command.user().getId())) {
            return UseCaseResult.failure(UseCaseError.forbidden("User context does not match profile user"));
        }

        User user = command.user();
        sanitizeProfileText(user);
        var activation = activationPolicy.tryActivate(user);
        boolean activated = activation.activated();
        try {
            userStorage.save(user);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to save profile: " + e.getMessage()));
        }

        List<UserAchievement> newAchievements = List.of();
        try {
            newAchievements = unlockAchievements(user.getId());
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Post-save achievement update failed for user {}: {}", user.getId(), e.getMessage(), e);
            }
        }

        publishEvent(
                new AppEvent.ProfileSaved(user.getId(), activated, AppClock.now()),
                "Post-save event publication failed for user " + user.getId());
        if (activated) {
            publishEvent(
                    new AppEvent.ProfileCompleted(user.getId(), AppClock.now()),
                    "Post-profile-completed event failed for user " + user.getId());
        }

        return UseCaseResult.success(new ProfileSaveResult(user, activated, newAchievements));
    }

    public UseCaseResult<User> updateDiscoveryPreferences(UpdateDiscoveryPreferencesCommand command) {
        if (command == null || command.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }

        User user = userStorage.get(command.context().userId()).orElse(null);
        if (user == null) {
            return UseCaseResult.failure(UseCaseError.notFound(USER_NOT_FOUND));
        }

        try {
            int minAge = Math.clamp(
                    command.minAge(),
                    config.validation().minAge(),
                    config.validation().maxAge());
            int maxAge = Math.clamp(
                    command.maxAge(),
                    config.validation().minAge(),
                    config.validation().maxAge());
            if (minAge > maxAge) {
                int swap = minAge;
                minAge = maxAge;
                maxAge = swap;
            }

            int maxDistance =
                    Math.clamp(command.maxDistanceKm(), 1, config.matching().maxDistanceKm());
            user.setAgeRange(
                    minAge,
                    maxAge,
                    config.validation().minAge(),
                    config.validation().maxAge());
            user.setMaxDistanceKm(maxDistance, config.matching().maxDistanceKm());

            if (command.interestedIn() != null && !command.interestedIn().isEmpty()) {
                user.setInterestedIn(EnumSet.copyOf(command.interestedIn()));
            }

            userStorage.save(user);
            return UseCaseResult.success(user);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to update preferences: " + e.getMessage()));
        }
    }

    public UseCaseResult<ProfileSaveResult> updateProfile(UpdateProfileCommand command) {
        if (command == null || command.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (userStorage == null || validationService == null) {
            return UseCaseResult.failure(UseCaseError.dependency("UserStorage and ValidationService are required"));
        }

        User user = userStorage.get(command.context().userId()).orElse(null);
        if (user == null) {
            return UseCaseResult.failure(UseCaseError.notFound(USER_NOT_FOUND));
        }

        boolean locationWasSet = user.hasLocationSet();
        double previousLatitude = user.getLat();
        double previousLongitude = user.getLon();

        try {
            applyProfileTextAndIdentityFields(user, command);
            applyProfileLocationFields(user, command);
            applyProfilePreferenceFields(user, command);
            applyProfileLifestyleFields(user, command);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return UseCaseResult.failure(UseCaseError.validation(e.getMessage()));
        }

        UseCaseResult<ProfileSaveResult> saveResult = saveProfile(new SaveProfileCommand(command.context(), user));
        if (saveResult.success()
                && command.latitude() != null
                && command.longitude() != null
                && (!locationWasSet
                        || Double.compare(previousLatitude, command.latitude()) != 0
                        || Double.compare(previousLongitude, command.longitude()) != 0)) {
            publishEvent(
                    new AppEvent.LocationUpdated(user.getId(), command.latitude(), command.longitude(), AppClock.now()),
                    "Post-location-updated event failed for user " + user.getId());
        }
        return saveResult;
    }

    public UseCaseResult<List<User>> listUsers() {
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }
        try {
            return UseCaseResult.success(userStorage.findAll());
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to list users: " + e.getMessage()));
        }
    }

    public UseCaseResult<User> getUserById(UUID userId) {
        if (userId == null) {
            return UseCaseResult.failure(UseCaseError.validation("User ID is required"));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }
        try {
            return userStorage
                    .get(userId)
                    .map(UseCaseResult::success)
                    .orElseGet(() -> UseCaseResult.failure(UseCaseError.notFound(USER_NOT_FOUND)));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load user: " + e.getMessage()));
        }
    }

    public UseCaseResult<AchievementSnapshot> getAchievements(AchievementsQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (profileService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(PROFILE_SERVICE_REQUIRED));
        }

        try {
            List<UserAchievement> newlyUnlocked =
                    query.checkForNew() ? unlockAchievements(query.context().userId()) : List.of();
            List<UserAchievement> unlocked =
                    getUnlockedAchievements(query.context().userId());
            return UseCaseResult.success(new AchievementSnapshot(unlocked, newlyUnlocked));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load achievements: " + e.getMessage()));
        }
    }

    public UseCaseResult<UserStats> getOrComputeStats(StatsQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (activityMetricsService == null) {
            return UseCaseResult.failure(UseCaseError.dependency("ActivityMetricsService is required"));
        }
        try {
            return UseCaseResult.success(
                    activityMetricsService.getOrComputeStats(query.context().userId()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load user statistics: " + e.getMessage()));
        }
    }

    public UseCaseResult<ProfileService.CompletionResult> calculateCompletion(User user) {
        if (user == null) {
            return UseCaseResult.failure(UseCaseError.validation("User is required"));
        }
        if (profileService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(PROFILE_SERVICE_REQUIRED));
        }
        try {
            return UseCaseResult.success(profileService.calculate(user));
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to calculate profile completion: " + e.getMessage()));
        }
    }

    public UseCaseResult<ProfileService.ProfilePreview> generatePreview(User user) {
        if (user == null) {
            return UseCaseResult.failure(UseCaseError.validation("User is required"));
        }
        if (profileService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(PROFILE_SERVICE_REQUIRED));
        }
        try {
            return UseCaseResult.success(profileService.generatePreview(user));
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to generate profile preview: " + e.getMessage()));
        }
    }

    public UseCaseResult<Boolean> deleteAccount(DeleteAccountCommand command) {
        if (command == null || command.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }

        User user = userStorage.get(command.context().userId()).orElse(null);
        if (user == null) {
            return UseCaseResult.failure(UseCaseError.notFound(USER_NOT_FOUND));
        }

        try {
            var deletedAt = AppClock.now();
            user.markDeleted(deletedAt);
            if (user.getState() == User.UserState.ACTIVE) {
                user.pause();
            }
            AppEvent.DeletionReason deletionReason = sanitizeDeletionReason(command.reason());
            if (accountCleanupStorage != null) {
                accountCleanupStorage.softDeleteAccount(user, deletedAt);
            } else {
                userStorage.save(user);
            }
            if (logger.isInfoEnabled()) {
                logger.info("Account soft-deleted for user {} (reasonCode={})", user.getId(), deletionReason);
            }
            publishEvent(
                    new AppEvent.AccountDeleted(user.getId(), deletionReason, deletedAt),
                    "Post-account-delete event publication failed for user " + user.getId());
            return UseCaseResult.success(true);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to delete account: " + e.getMessage()));
        }
    }

    public UseCaseResult<List<ProfileNote>> listProfileNotes(ProfileNotesQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }
        if (userStorage.get(query.context().userId()).isEmpty()) {
            return UseCaseResult.failure(UseCaseError.notFound(AUTHOR_NOT_FOUND));
        }
        try {
            return UseCaseResult.success(
                    userStorage.getProfileNotesByAuthor(query.context().userId()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load profile notes: " + e.getMessage()));
        }
    }

    public UseCaseResult<ProfileNote> getProfileNote(ProfileNoteQuery query) {
        if (query == null || query.context() == null || query.subjectId() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_AND_SUBJECT_REQUIRED));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }
        if (userStorage.get(query.context().userId()).isEmpty()) {
            return UseCaseResult.failure(UseCaseError.notFound(AUTHOR_NOT_FOUND));
        }
        try {
            return userStorage
                    .getProfileNote(query.context().userId(), query.subjectId())
                    .map(UseCaseResult::success)
                    .orElseGet(() -> UseCaseResult.failure(UseCaseError.notFound("Profile note not found")));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load profile note: " + e.getMessage()));
        }
    }

    public UseCaseResult<ProfileNote> upsertProfileNote(UpsertProfileNoteCommand command) {
        if (command == null || command.context() == null || command.subjectId() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_AND_SUBJECT_REQUIRED));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }
        if (userStorage.get(command.context().userId()).isEmpty()) {
            return UseCaseResult.failure(UseCaseError.notFound(AUTHOR_NOT_FOUND));
        }
        if (userStorage.get(command.subjectId()).isEmpty()) {
            return UseCaseResult.failure(UseCaseError.notFound("Subject user not found"));
        }

        try {
            String sanitizedContent = SanitizerUtils.sanitize(command.content());
            int maxProfileNoteLength = config.validation().maxProfileNoteLength();
            if (sanitizedContent != null && sanitizedContent.length() > maxProfileNoteLength) {
                return UseCaseResult.failure(
                        UseCaseError.validation(PROFILE_NOTE_TOO_LONG.formatted(maxProfileNoteLength)));
            }
            ProfileNote note = userStorage
                    .getProfileNote(command.context().userId(), command.subjectId())
                    .map(existing -> existing.withContent(sanitizedContent))
                    .orElseGet(() ->
                            ProfileNote.create(command.context().userId(), command.subjectId(), sanitizedContent));
            userStorage.saveProfileNote(note);
            int safeContentLength = sanitizedContent == null ? 0 : sanitizedContent.length();
            publishEvent(
                    new AppEvent.ProfileNoteSaved(
                            command.context().userId(), command.subjectId(), safeContentLength, AppClock.now()),
                    "Post-profile-note-save event publication failed for author "
                            + command.context().userId());
            return UseCaseResult.success(note);
        } catch (IllegalArgumentException | NullPointerException e) {
            return UseCaseResult.failure(UseCaseError.validation(e.getMessage()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to save profile note: " + e.getMessage()));
        }
    }

    public UseCaseResult<Boolean> deleteProfileNote(DeleteProfileNoteCommand command) {
        if (command == null || command.context() == null || command.subjectId() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_AND_SUBJECT_REQUIRED));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }
        if (userStorage.get(command.context().userId()).isEmpty()) {
            return UseCaseResult.failure(UseCaseError.notFound(AUTHOR_NOT_FOUND));
        }
        try {
            boolean deleted = userStorage.deleteProfileNote(command.context().userId(), command.subjectId());
            if (!deleted) {
                return UseCaseResult.failure(UseCaseError.notFound("Profile note not found"));
            }
            publishEvent(
                    new AppEvent.ProfileNoteDeleted(command.context().userId(), command.subjectId(), AppClock.now()),
                    "Post-profile-note-delete event publication failed for author "
                            + command.context().userId());
            return UseCaseResult.success(true);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to delete profile note: " + e.getMessage()));
        }
    }

    public ValidationService validationService() {
        return validationService;
    }

    public static record SaveProfileCommand(UserContext context, User user) {}

    public static record ProfileSaveResult(User user, boolean activated, List<UserAchievement> newlyUnlocked) {}

    public static record UpdateDiscoveryPreferencesCommand(
            UserContext context, int minAge, int maxAge, int maxDistanceKm, Set<Gender> interestedIn) {}

    public static record UpdateProfileCommand(
            UserContext context,
            String bio,
            java.time.LocalDate birthDate,
            Gender gender,
            Set<Gender> interestedIn,
            Double latitude,
            Double longitude,
            Integer maxDistanceKm,
            Integer minAge,
            Integer maxAge,
            Integer heightCm,
            Lifestyle.Smoking smoking,
            Lifestyle.Drinking drinking,
            Lifestyle.WantsKids wantsKids,
            Lifestyle.LookingFor lookingFor,
            Lifestyle.Education education,
            Set<Interest> interests,
            Dealbreakers dealbreakers) {}

    public static record AchievementsQuery(UserContext context, boolean checkForNew) {}

    public static record AchievementSnapshot(List<UserAchievement> unlocked, List<UserAchievement> newlyUnlocked) {}

    public static record StatsQuery(UserContext context) {}

    public static record DeleteAccountCommand(UserContext context, String reason) {}

    public static record ProfileNotesQuery(UserContext context) {}

    public static record ProfileNoteQuery(UserContext context, UUID subjectId) {}

    public static record UpsertProfileNoteCommand(UserContext context, UUID subjectId, String content) {}

    public static record DeleteProfileNoteCommand(UserContext context, UUID subjectId) {}

    private static void sanitizeProfileText(User user) {
        user.setName(SanitizerUtils.sanitize(user.getName()));
        user.setBio(SanitizerUtils.sanitize(user.getBio()));
    }

    private void publishEvent(AppEvent event, String failureMessage) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(event);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("{}: {}", failureMessage, e.getMessage(), e);
            }
        }
    }

    private static AppEvent.DeletionReason sanitizeDeletionReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return AppEvent.DeletionReason.ANONYMIZED_CODE;
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("privacy")
                || normalized.contains("erase")
                || normalized.contains("gdpr")
                || normalized.contains("right-to-erasure")) {
            return AppEvent.DeletionReason.PRIVACY_REQUEST;
        }
        if (normalized.contains("safety")
                || normalized.contains("abuse")
                || normalized.contains("policy")
                || normalized.contains("moderation")
                || normalized.contains("ban")) {
            return AppEvent.DeletionReason.SAFETY_ACTION;
        }
        if (normalized.contains("user") || normalized.contains("self")) {
            return AppEvent.DeletionReason.USER_REQUEST;
        }
        return AppEvent.DeletionReason.ANONYMIZED_CODE;
    }

    private List<UserAchievement> unlockAchievements(UUID userId) {
        if (achievementService != null) {
            return achievementService.checkAndUnlock(userId);
        }
        if (profileService != null) {
            return profileService.checkAndUnlock(userId);
        }
        return List.of();
    }

    private List<UserAchievement> getUnlockedAchievements(UUID userId) {
        if (achievementService != null) {
            return achievementService.getUnlocked(userId);
        }
        if (profileService != null) {
            return profileService.getUnlocked(userId);
        }
        return List.of();
    }

    private void applyProfileTextAndIdentityFields(User user, UpdateProfileCommand command) {
        if (command.bio() != null) {
            assertValid(validationService.validateBio(command.bio()));
            user.setBio(SanitizerUtils.sanitize(command.bio()));
        }
        if (command.birthDate() != null) {
            assertValid(validationService.validateBirthDate(command.birthDate()));
            user.setBirthDate(command.birthDate());
        }
        if (command.gender() != null) {
            user.setGender(command.gender());
        }
        if (command.interestedIn() != null && !command.interestedIn().isEmpty()) {
            user.setInterestedIn(command.interestedIn());
        }
    }

    private void applyProfileLocationFields(User user, UpdateProfileCommand command) {
        if (command.latitude() == null && command.longitude() == null) {
            return;
        }
        if (command.latitude() == null || command.longitude() == null) {
            throw new IllegalArgumentException("Both latitude and longitude are required");
        }
        assertValid(validationService.validateLocation(command.latitude(), command.longitude()));
        user.setLocation(command.latitude(), command.longitude());

        if (command.maxDistanceKm() != null) {
            assertValid(validationService.validateDistance(command.maxDistanceKm()));
            user.setMaxDistanceKm(command.maxDistanceKm(), config.matching().maxDistanceKm());
        }
    }

    private void applyProfilePreferenceFields(User user, UpdateProfileCommand command) {
        if (command.maxDistanceKm() != null && command.latitude() == null && command.longitude() == null) {
            assertValid(validationService.validateDistance(command.maxDistanceKm()));
            user.setMaxDistanceKm(command.maxDistanceKm(), config.matching().maxDistanceKm());
        }

        if (command.minAge() == null && command.maxAge() == null) {
            return;
        }

        int minAge = command.minAge() != null ? command.minAge() : user.getMinAge();
        int maxAge = command.maxAge() != null ? command.maxAge() : user.getMaxAge();
        if (minAge > maxAge) {
            int swap = minAge;
            minAge = maxAge;
            maxAge = swap;
        }
        assertValid(validationService.validateAgeRange(minAge, maxAge));
        user.setAgeRange(
                minAge,
                maxAge,
                config.validation().minAge(),
                config.validation().maxAge());
    }

    private void applyProfileLifestyleFields(User user, UpdateProfileCommand command) {
        if (command.heightCm() != null) {
            assertValid(validationService.validateHeight(command.heightCm()));
            user.setHeightCm(command.heightCm());
        }
        if (command.smoking() != null) {
            user.setSmoking(command.smoking());
        }
        if (command.drinking() != null) {
            user.setDrinking(command.drinking());
        }
        if (command.wantsKids() != null) {
            user.setWantsKids(command.wantsKids());
        }
        if (command.lookingFor() != null) {
            user.setLookingFor(command.lookingFor());
        }
        if (command.education() != null) {
            user.setEducation(command.education());
        }
        if (command.interests() != null && !command.interests().isEmpty()) {
            user.setInterests(command.interests());
        }
        if (command.dealbreakers() != null) {
            user.setDealbreakers(command.dealbreakers());
        }
    }

    private static void assertValid(ValidationService.ValidationResult validationResult) {
        if (!validationResult.valid()) {
            throw new IllegalArgumentException(validationResult.errors().getFirst());
        }
    }
}
