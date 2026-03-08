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
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Profile and user-progress application use-cases shared across adapters. */
public class ProfileUseCases {

    private static final Logger logger = LoggerFactory.getLogger(ProfileUseCases.class);
    private static final String CONTEXT_REQUIRED = "Context is required";
    private static final String PROFILE_SERVICE_REQUIRED = "ProfileService is required";
    private static final String USER_STORAGE_REQUIRED = "UserStorage is required";
    private static final String AUTHOR_NOT_FOUND = "Author not found";
    private static final String CONTEXT_AND_SUBJECT_REQUIRED = "Context and subjectId are required";

    private final UserStorage userStorage;
    private final ProfileService profileService;
    private final ValidationService validationService;
    private final ActivityMetricsService activityMetricsService;
    private final AchievementService achievementService;
    private final AppConfig config;
    private final ProfileActivationPolicy activationPolicy;
    private final AppEventBus eventBus;

    /** Backward-compatible constructor — uses default activation policy, no event bus. */
    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ActivityMetricsService activityMetricsService,
            AppConfig config) {
        this(
                userStorage,
                profileService,
                validationService,
                activityMetricsService,
                null,
                config,
                new ProfileActivationPolicy(),
                null);
    }

    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ActivityMetricsService activityMetricsService,
            AchievementService achievementService,
            AppConfig config) {
        this(
                userStorage,
                profileService,
                validationService,
                activityMetricsService,
                achievementService,
                config,
                new ProfileActivationPolicy(),
                null);
    }

    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ActivityMetricsService activityMetricsService,
            AppConfig config,
            ProfileActivationPolicy activationPolicy) {
        this(
                userStorage,
                profileService,
                validationService,
                activityMetricsService,
                null,
                config,
                activationPolicy,
                null);
    }

    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ActivityMetricsService activityMetricsService,
            AchievementService achievementService,
            AppConfig config,
            ProfileActivationPolicy activationPolicy) {
        this(
                userStorage,
                profileService,
                validationService,
                activityMetricsService,
                achievementService,
                config,
                activationPolicy,
                null);
    }

    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ActivityMetricsService activityMetricsService,
            AchievementService achievementService,
            AppConfig config,
            ProfileActivationPolicy activationPolicy,
            AppEventBus eventBus) {
        this.userStorage = userStorage;
        this.profileService = profileService;
        this.validationService = validationService;
        this.activityMetricsService = activityMetricsService;
        this.achievementService = achievementService;
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.activationPolicy = Objects.requireNonNull(activationPolicy, "activationPolicy cannot be null");
        this.eventBus = eventBus;
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
            if (eventBus != null) {
                eventBus.publish(new AppEvent.ProfileSaved(user.getId(), activated, AppClock.now()));
            }
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Post-save action failed for user {}: {}", user.getId(), e.getMessage(), e);
            }
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
            return UseCaseResult.failure(UseCaseError.notFound("User not found"));
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
            ProfileNote note = userStorage
                    .getProfileNote(command.context().userId(), command.subjectId())
                    .map(existing -> existing.withContent(command.content()))
                    .orElseGet(() ->
                            ProfileNote.create(command.context().userId(), command.subjectId(), command.content()));
            userStorage.saveProfileNote(note);
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

    public static record AchievementsQuery(UserContext context, boolean checkForNew) {}

    public static record AchievementSnapshot(List<UserAchievement> unlocked, List<UserAchievement> newlyUnlocked) {}

    public static record StatsQuery(UserContext context) {}

    public static record ProfileNotesQuery(UserContext context) {}

    public static record ProfileNoteQuery(UserContext context, UUID subjectId) {}

    public static record UpsertProfileNoteCommand(UserContext context, UUID subjectId, String content) {}

    public static record DeleteProfileNoteCommand(UserContext context, UUID subjectId) {}

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
}
