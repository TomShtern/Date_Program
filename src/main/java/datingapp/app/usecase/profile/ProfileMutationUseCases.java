package datingapp.app.usecase.profile;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.profile.SanitizerUtils;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.AccountCleanupStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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

    private final UserStorage userStorage;
    private final ValidationService validationService;
    private final AchievementService achievementService;
    private final AccountCleanupStorage accountCleanupStorage;
    private final AppConfig config;
    private final ProfileActivationPolicy activationPolicy;
    private final AppEventBus eventBus;

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

    public UseCaseResult<ProfileUseCases.ProfileSaveResult> saveProfile(ProfileUseCases.SaveProfileCommand command) {
        if (command == null || command.context() == null || command.user() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and user are required"));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
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

        return UseCaseResult.success(new ProfileUseCases.ProfileSaveResult(user, activated, newAchievements));
    }

    public UseCaseResult<User> updateDiscoveryPreferences(ProfileUseCases.UpdateDiscoveryPreferencesCommand command) {
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

            if (command.interestedIn() != null) {
                user.setInterestedIn(command.interestedIn());
            }

            userStorage.save(user);
            return UseCaseResult.success(user);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to update preferences: " + e.getMessage()));
        }
    }

    public UseCaseResult<ProfileUseCases.ProfileSaveResult> updateProfile(
            ProfileUseCases.UpdateProfileCommand command) {
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

        UseCaseResult<ProfileUseCases.ProfileSaveResult> saveResult =
                saveProfile(new ProfileUseCases.SaveProfileCommand(command.context(), user));
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

    public UseCaseResult<Void> deleteAccount(ProfileUseCases.DeleteAccountCommand command) {
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
            // Intentional: mutate the caller-visible original so any downstream
            // request-local logic sees the deleted state. Persistence already
            // occurred on deletedUser above.
            applyDeletionState(user, deletedAt);
            if (logger.isInfoEnabled()) {
                logger.info("Account soft-deleted for user {} (reasonCode={})", user.getId(), command.reason());
            }
            publishEvent(
                    new AppEvent.AccountDeleted(user.getId(), command.reason(), deletedAt),
                    "Post-account-delete event publication failed for user " + user.getId());
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to delete account: " + e.getMessage()));
        }
    }

    private List<UserAchievement> unlockAchievements(UUID userId) {
        if (achievementService != null) {
            return achievementService.checkAndUnlock(userId);
        }
        return List.of();
    }

    private void publishEvent(AppEvent event, String failureMessage) {
        try {
            eventBus.publish(event);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("{}: {}", failureMessage, e.getMessage(), e);
            }
        }
    }

    private void applyProfileTextAndIdentityFields(User user, ProfileUseCases.UpdateProfileCommand command) {
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
    }

    private void applyProfileLocationFields(User user, ProfileUseCases.UpdateProfileCommand command) {
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

    private void applyProfilePreferenceFields(User user, ProfileUseCases.UpdateProfileCommand command) {
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

    private void applyProfileLifestyleFields(User user, ProfileUseCases.UpdateProfileCommand command) {
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
}
