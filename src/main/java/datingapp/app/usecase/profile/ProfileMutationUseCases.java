package datingapp.app.usecase.profile;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.event.EventPublishing;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.Snapshots;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.SanitizerUtils;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.AccountCleanupStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Profile mutation and account-lifecycle application use-cases shared across adapters. */
@SuppressWarnings("java:S107")
public final class ProfileMutationUseCases {

    private static final Logger logger = LoggerFactory.getLogger(ProfileMutationUseCases.class);
    private static final String CONTEXT_REQUIRED = "Context is required";
    private static final String USER_STORAGE_REQUIRED = "UserStorage is required";
    private static final String USER_NOT_FOUND = "User not found";

    /** Stable conflict codes for the pause/resume routes. */
    public static final String REASON_PROFILE_NOT_ACTIVE = "PROFILE_NOT_ACTIVE";

    public static final String REASON_PROFILE_NOT_PAUSED = "PROFILE_NOT_PAUSED";
    public static final String REASON_PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE";

    private final UserStorage userStorage;
    private final ValidationService validationService;
    private final AchievementService achievementService;
    private final AccountCleanupStorage accountCleanupStorage;
    private final AppConfig config;
    private final ProfileActivationPolicy activationPolicy;
    private final AppEventBus eventBus;
    private volatile datingapp.core.media.PhotoStore photoStore;

    public ProfileMutationUseCases(
            UserStorage userStorage,
            ValidationService validationService,
            AchievementService achievementService,
            AppConfig config,
            ProfileActivationPolicy activationPolicy,
            AppEventBus eventBus) {
        this(userStorage, validationService, achievementService, config, activationPolicy, eventBus, null);
    }

    @SuppressWarnings("java:S107")
    public ProfileMutationUseCases(
            UserStorage userStorage,
            ValidationService validationService,
            AchievementService achievementService,
            AppConfig config,
            ProfileActivationPolicy activationPolicy,
            AppEventBus eventBus,
            AccountCleanupStorage accountCleanupStorage) {
        this.userStorage = userStorage;
        this.validationService = validationService;
        this.achievementService = achievementService;
        this.accountCleanupStorage = accountCleanupStorage;
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.activationPolicy = Objects.requireNonNull(activationPolicy, "activationPolicy cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
    }

    /** Wires photo file cleanup for GDPR deletion. Optional; no-op when unset (e.g. tests). */
    public void setPhotoStore(datingapp.core.media.PhotoStore photoStore) {
        this.photoStore = photoStore;
    }

    public UseCaseResult<CreateUserResult> createUser(CreateUserCommand command) {
        if (command == null) {
            return UseCaseResult.failure(UseCaseError.validation("Create user command is required"));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }

        String trimmedName = command.name() == null ? "" : command.name().trim();
        if (trimmedName.isEmpty()) {
            return UseCaseResult.failure(UseCaseError.validation("Name cannot be empty"));
        }
        try {
            User user = new User(UUID.randomUUID(), trimmedName);
            if (command.age() == null && command.gender() == null && command.interestedIn() == null) {
                userStorage.save(user);
                return UseCaseResult.success(new CreateUserResult(user));
            }

            if (command.age() == null || command.gender() == null || command.interestedIn() == null) {
                return UseCaseResult.failure(UseCaseError.validation(
                        "Age, gender, and InterestedIn must either all be provided or all be omitted"));
            }
            if (command.age() < config.validation().minAge()
                    || command.age() > config.validation().maxAge()) {
                return UseCaseResult.failure(UseCaseError.validation(
                        "Age must be between " + config.validation().minAge() + " and "
                                + config.validation().maxAge()));
            }

            ProfileNormalizationSupport.applyMinimalBootstrap(
                    user, config, command.age(), command.gender(), command.interestedIn());
            userStorage.save(user);
            return UseCaseResult.success(new CreateUserResult(user));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internalFailure("create user", e));
        }
    }

    public UseCaseResult<ProfileSaveResult> saveProfile(SaveProfileCommand command) {
        if (command == null || command.context() == null || command.user() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and user are required"));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }
        if (!command.context().userId().equals(command.user().getId())) {
            return UseCaseResult.failure(UseCaseError.forbidden("User context does not match profile user"));
        }

        return executeProfileSave(command.user(), "profile.save user={} activated={} missingFields={}");
    }

    public UseCaseResult<ProfileSaveResult> savePhotoUrls(SavePhotoUrlsCommand command) {
        if (command == null || command.user() == null || command.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and user are required"));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }
        if (!command.context().userId().equals(command.user().getId())) {
            return UseCaseResult.failure(UseCaseError.forbidden("User context does not match profile user"));
        }

        return executeProfileSave(command.user(), "profile.savePhotoUrls user={} activated={} missingFields={}");
    }

    public record SavePhotoUrlsCommand(UserContext context, User user) {}

    private UseCaseResult<ProfileSaveResult> executeProfileSave(User user, String logMessage) {
        sanitizeProfileText(user);
        // A paused profile stays paused across edits; only resumeProfile brings it back.
        boolean activated = user.getState() != User.UserState.PAUSED
                && activationPolicy.tryActivate(user).activated();
        try {
            userStorage.save(user);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internalFailure("save profile", e));
        }

        if (logger.isInfoEnabled()) {
            logger.info(logMessage, user.getId(), activated, user.getMissingProfileFields());
        }

        List<UserAchievement> newAchievements = List.of();
        try {
            newAchievements = unlockAchievements(user.getId());
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Post-save achievement update failed for user {}: {}", user.getId(), e.getMessage(), e);
            }
        }

        EventPublishing.publishOrWarn(
                eventBus,
                new AppEvent.ProfileSaved(user.getId(), activated, AppClock.now()),
                "Post-save event publication failed for user " + user.getId(),
                logger);
        if (activated) {
            EventPublishing.publishOrWarn(
                    eventBus,
                    new AppEvent.ProfileCompleted(user.getId(), AppClock.now()),
                    "Post-profile-completed event failed for user " + user.getId(),
                    logger);
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
            ProfileNormalizationSupport.DiscoveryPreferences discoveryPreferences =
                    ProfileNormalizationSupport.normalizeDiscoveryPreferences(
                            config, command.minAge(), command.maxAge(), command.maxDistanceKm());
            user.setAgeRange(
                    discoveryPreferences.minAge(),
                    discoveryPreferences.maxAge(),
                    config.validation().minAge(),
                    config.validation().maxAge());
            user.setMaxDistanceKm(
                    discoveryPreferences.maxDistanceKm(), config.matching().maxDistanceKm());

            if (command.interestedIn() != null) {
                user.setInterestedIn(command.interestedIn());
            }

            userStorage.save(user);
            return UseCaseResult.success(user);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internalFailure("update preferences", e));
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

        try {
            applyProfileTextAndIdentityFields(user, command);
            applyProfileLocationFields(user, command);
            applyProfilePreferenceFields(user, command);
            applyProfileLifestyleFields(user, command);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return UseCaseResult.failure(UseCaseError.validation(stableProfileUpdateMessage(e.getMessage())));
        }

        return saveProfile(new SaveProfileCommand(command.context(), user));
    }

    /** Hides the caller's own profile from discovery. Only an ACTIVE profile can be paused. */
    public UseCaseResult<User> pauseProfile(PauseProfileCommand command) {
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
        if (user.getState() != User.UserState.ACTIVE) {
            return UseCaseResult.failure(
                    UseCaseError.conflict(REASON_PROFILE_NOT_ACTIVE, "Only an active profile can be paused"));
        }
        try {
            user.pause();
            userStorage.save(user);
            return UseCaseResult.success(user);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internalFailure("pause profile", e));
        }
    }

    /** Puts the caller's own paused profile back into discovery. The profile must still be complete. */
    public UseCaseResult<User> resumeProfile(ResumeProfileCommand command) {
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
        if (user.getState() == User.UserState.INCOMPLETE) {
            return UseCaseResult.failure(incompleteProfile());
        }
        if (user.getState() != User.UserState.PAUSED) {
            return UseCaseResult.failure(
                    UseCaseError.conflict(REASON_PROFILE_NOT_PAUSED, "Only a paused profile can be resumed"));
        }
        try {
            if (!activationPolicy.tryActivate(user).activated()) {
                return UseCaseResult.failure(incompleteProfile());
            }
            userStorage.save(user);
            return UseCaseResult.success(user);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internalFailure("resume profile", e));
        }
    }

    private static UseCaseError incompleteProfile() {
        return UseCaseError.conflict(REASON_PROFILE_INCOMPLETE, "Complete the profile before resuming it");
    }

    /**
     * Deletes a user account by soft-deletion.
     *
     * <p>
     * Contract: This method creates a deleted copy of the user, persists it to storage, and then
     * mutates the caller-visible User instance to reflect the deleted state. This ensures both
     * storage durability and request-local consistency.
     *
     * @param command the delete account command containing context and reason
     * @return success if the account was deleted; failure otherwise
     */
    public UseCaseResult<Void> deleteAccount(DeleteAccountCommand command) {
        if (command == null || command.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (command.reason() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Deletion reason is required"));
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
            User deletedUser = user.copy();
            applyDeletionState(deletedUser, deletedAt);
            if (accountCleanupStorage != null) {
                accountCleanupStorage.softDeleteAccount(deletedUser, deletedAt);
            } else {
                userStorage.save(deletedUser);
            }
            if (photoStore != null) {
                try {
                    photoStore.deleteAllForUser(command.context().userId());
                } catch (Exception e) {
                    if (logger.isWarnEnabled()) {
                        logger.warn(
                                "Photo cleanup failed for deleted user {}",
                                command.context().userId(),
                                e);
                    }
                }
            }
            // Intentional: mutate the caller-visible original so any downstream
            // request-local logic sees the deleted state. Persistence already
            // occurred on deletedUser above.
            applyDeletionState(user, deletedAt);
            if (logger.isInfoEnabled()) {
                logger.info("Account soft-deleted for user {} (reasonCode={})", user.getId(), command.reason());
            }
            EventPublishing.publishOrWarn(
                    eventBus,
                    new AppEvent.AccountDeleted(user.getId(), command.reason(), deletedAt),
                    "Post-account-delete event publication failed for user " + user.getId(),
                    logger);
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internalFailure("delete account", e));
        }
    }

    private List<UserAchievement> unlockAchievements(UUID userId) {
        if (achievementService != null) {
            return achievementService.checkAndUnlock(userId);
        }
        return List.of();
    }

    private void applyProfileTextAndIdentityFields(User user, UpdateProfileCommand command) {
        if (command.name() != null) {
            String trimmed = command.name().trim();
            if (trimmed.isBlank()) {
                throw new IllegalArgumentException("Name cannot be blank");
            }
            user.setName(SanitizerUtils.sanitize(trimmed));
        }
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
        if (command.interestedIn() != null) {
            user.setInterestedIn(command.interestedIn());
        }
        if (command.pacePreferences() != null) {
            user.setPacePreferences(command.pacePreferences());
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
            throw new IllegalArgumentException("Min age cannot exceed max age");
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
        if (command.interests() != null) {
            user.setInterests(command.interests());
        }
        if (command.dealbreakers() != null) {
            user.setDealbreakers(command.dealbreakers());
        }
    }

    private static void applyDeletionState(User user, Instant deletedAt) {
        user.markDeleted(deletedAt);
        if (user.getState() == User.UserState.ACTIVE) {
            user.pause();
        }
        user.scrubForDeletion();
    }

    private static void sanitizeProfileText(User user) {
        if (user.getName() != null) {
            user.setName(SanitizerUtils.sanitize(user.getName()));
        }
        if (user.getBio() != null) {
            user.setBio(SanitizerUtils.sanitize(user.getBio()));
        }
    }

    private static void assertValid(ValidationService.ValidationResult validationResult) {
        if (!validationResult.valid()) {
            String message = validationResult.errors().isEmpty()
                    ? "Validation failed"
                    : validationResult.errors().getFirst();
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Maps storage/validation internals to a stable user-facing message. Raw exception text
     * stays server-side (logged via {@code internalFailure} paths); the API surface only
     * exposes one of a small set of stable profile-update failures.
     */
    public static String stableProfileUpdateMessage(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Invalid profile update.";
        }
        String normalized = detail.trim();
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("name")) {
            return "Invalid name.";
        }
        if (lower.contains("bio")) {
            return "Invalid bio.";
        }
        if (lower.contains("min age")
                || lower.contains("max age")
                || lower.contains("minage")
                || lower.contains("maxage")
                || lower.contains("age range")) {
            return "Invalid age range.";
        }
        if (lower.contains("birth") || lower.contains("age")) {
            return "Invalid birth date.";
        }
        if (lower.contains("gender") || lower.contains("interested")) {
            return "Invalid gender preference.";
        }
        if (lower.contains("location") || lower.contains("latitude") || lower.contains("longitude")) {
            return "Invalid location.";
        }
        if (lower.contains("distance")) {
            return "Invalid distance preference.";
        }
        if (lower.contains("photo")) {
            return "Invalid photo.";
        }
        if (lower.contains("interest")
                || lower.contains("lifestyle")
                || lower.contains("education")
                || lower.contains("height")
                || lower.contains("dealbreaker")
                || lower.contains("pace")) {
            return "Invalid profile preferences.";
        }
        return "Invalid profile update.";
    }

    public static record SaveProfileCommand(UserContext context, User user) {}

    public static record ProfileSaveResult(User user, boolean activated, List<UserAchievement> newlyUnlocked) {
        public ProfileSaveResult {
            newlyUnlocked = newlyUnlocked == null ? List.of() : List.copyOf(newlyUnlocked);
        }
    }

    public static record UpdateDiscoveryPreferencesCommand(
            UserContext context, int minAge, int maxAge, int maxDistanceKm, Set<Gender> interestedIn) {
        public UpdateDiscoveryPreferencesCommand {
            interestedIn = Snapshots.setOf(interestedIn);
        }
    }

    public static record UpdateProfileCommand(
            UserContext context,
            String name,
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
            Set<datingapp.core.profile.MatchPreferences.Interest> interests,
            Dealbreakers dealbreakers,
            PacePreferences pacePreferences) {
        public UpdateProfileCommand {
            interestedIn = Snapshots.setOf(interestedIn);
            interests = Snapshots.setOf(interests);
        }
    }

    public static record DeleteAccountCommand(UserContext context, AppEvent.DeletionReason reason) {}

    public static record PauseProfileCommand(UserContext context) {}

    public static record ResumeProfileCommand(UserContext context) {}

    public static record CreateUserCommand(String name, Integer age, Gender gender, Gender interestedIn) {}

    public static record CreateUserResult(User user) {}
}
