package datingapp.app.usecase.profile;

import datingapp.app.event.AppEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppConfig;
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
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.AccountCleanupStorage;
import datingapp.core.storage.UserStorage;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Profile and user-progress application use-cases shared across adapters. */
@SuppressWarnings("java:S107")
public class ProfileUseCases {
    private static final String USER_STORAGE_REQUIRED = "UserStorage is required";
    private static final String PROFILE_SERVICE_REQUIRED = "ProfileService is required";
    private static final String USER_NOT_FOUND = "User not found";

    private final UserStorage userStorage;
    private final ProfileService profileService;
    private final ValidationService validationService;
    private final ProfileMutationUseCases profileMutationUseCases;
    private final ProfileNotesUseCases profileNotesUseCases;
    private final ProfileInsightsUseCases profileInsightsUseCases;

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("java:S107")
    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ActivityMetricsService activityMetricsService,
            datingapp.core.metrics.AchievementService achievementService,
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
                null,
                null,
                null,
                null);
    }

    @SuppressWarnings("java:S107")
    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ActivityMetricsService activityMetricsService,
            datingapp.core.metrics.AchievementService achievementService,
            AppConfig config,
            ProfileActivationPolicy activationPolicy,
            AppEventBus eventBus,
            AccountCleanupStorage accountCleanupStorage) {
        this(
                userStorage,
                profileService,
                validationService,
                activityMetricsService,
                achievementService,
                config,
                activationPolicy,
                eventBus,
                null,
                accountCleanupStorage,
                null,
                null);
    }

    @SuppressWarnings("java:S107")
    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ActivityMetricsService activityMetricsService,
            datingapp.core.metrics.AchievementService achievementService,
            AppConfig config,
            ProfileActivationPolicy activationPolicy,
            AppEventBus eventBus,
            ProfileMutationUseCases profileMutationUseCases,
            AccountCleanupStorage accountCleanupStorage,
            ProfileNotesUseCases profileNotesUseCases,
            ProfileInsightsUseCases profileInsightsUseCases) {
        this.userStorage = userStorage;
        this.profileService = profileService;
        this.validationService = validationService;
        this.profileMutationUseCases = profileMutationUseCases != null
                ? profileMutationUseCases
                : new ProfileMutationUseCases(
                        userStorage,
                        validationService,
                        achievementService,
                        config,
                        activationPolicy,
                        eventBus,
                        accountCleanupStorage);
        this.profileNotesUseCases = profileNotesUseCases != null
                ? profileNotesUseCases
                : new ProfileNotesUseCases(userStorage, validationService, config, eventBus);
        this.profileInsightsUseCases = profileInsightsUseCases != null
                ? profileInsightsUseCases
                : new ProfileInsightsUseCases(achievementService, activityMetricsService);
    }

    public static final class Builder {
        private UserStorage userStorage;
        private ProfileService profileService;
        private ValidationService validationService;
        private ActivityMetricsService activityMetricsService;
        private datingapp.core.metrics.AchievementService achievementService;
        private AccountCleanupStorage accountCleanupStorage;
        private ProfileMutationUseCases profileMutationUseCases;
        private ProfileNotesUseCases profileNotesUseCases;
        private ProfileInsightsUseCases profileInsightsUseCases;
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

        public Builder achievementService(datingapp.core.metrics.AchievementService achievementService) {
            this.achievementService = achievementService;
            return this;
        }

        public Builder accountCleanupStorage(AccountCleanupStorage accountCleanupStorage) {
            this.accountCleanupStorage = accountCleanupStorage;
            return this;
        }

        public Builder profileMutationUseCases(ProfileMutationUseCases profileMutationUseCases) {
            this.profileMutationUseCases = profileMutationUseCases;
            return this;
        }

        public Builder profileNotesUseCases(ProfileNotesUseCases profileNotesUseCases) {
            this.profileNotesUseCases = profileNotesUseCases;
            return this;
        }

        public Builder profileInsightsUseCases(ProfileInsightsUseCases profileInsightsUseCases) {
            this.profileInsightsUseCases = profileInsightsUseCases;
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
                    profileMutationUseCases,
                    accountCleanupStorage,
                    profileNotesUseCases,
                    profileInsightsUseCases);
        }
    }

    public UseCaseResult<ProfileSaveResult> saveProfile(SaveProfileCommand command) {
        return profileMutationUseCases.saveProfile(command);
    }

    public UseCaseResult<User> updateDiscoveryPreferences(UpdateDiscoveryPreferencesCommand command) {
        return profileMutationUseCases.updateDiscoveryPreferences(command);
    }

    public UseCaseResult<ProfileSaveResult> updateProfile(UpdateProfileCommand command) {
        return profileMutationUseCases.updateProfile(command);
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

    public UseCaseResult<Map<UUID, User>> getUsersByIds(GetUsersByIdsQuery query) {
        if (query == null || query.userIds() == null) {
            return UseCaseResult.failure(UseCaseError.validation("User IDs are required"));
        }
        if (userStorage == null) {
            return UseCaseResult.failure(UseCaseError.dependency(USER_STORAGE_REQUIRED));
        }

        try {
            if (query.userIds().isEmpty()) {
                return UseCaseResult.success(Map.of());
            }

            List<UUID> requestedIds =
                    query.userIds().stream().filter(Objects::nonNull).toList();
            if (requestedIds.isEmpty()) {
                return UseCaseResult.success(Map.of());
            }

            Map<UUID, User> usersById = userStorage.findByIds(Set.copyOf(requestedIds));
            LinkedHashMap<UUID, User> stableUsers = new LinkedHashMap<>();
            for (UUID userId : requestedIds) {
                User user = usersById.get(userId);
                if (user != null) {
                    stableUsers.put(userId, user);
                }
            }
            return UseCaseResult.success(Collections.unmodifiableMap(stableUsers));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load users: " + e.getMessage()));
        }
    }

    public UseCaseResult<AchievementSnapshot> getAchievements(AchievementsQuery query) {
        if (query == null) {
            return UseCaseResult.failure(UseCaseError.validation("achievements query must not be null"));
        }
        var result = profileInsightsUseCases.getAchievements(toInsightsQuery(query));
        if (!result.success()) {
            return UseCaseResult.failure(result.error());
        }
        return UseCaseResult.success(
                new AchievementSnapshot(result.data().unlocked(), result.data().newlyUnlocked()));
    }

    public UseCaseResult<UserStats> getOrComputeStats(StatsQuery query) {
        var result = profileInsightsUseCases.getOrComputeStats(toInsightsQuery(query));
        if (!result.success()) {
            return UseCaseResult.failure(result.error());
        }
        return UseCaseResult.success(result.data());
    }

    public UseCaseResult<SessionSummaryResult> getSessionSummary(SessionSummaryQuery query) {
        var result = profileInsightsUseCases.getSessionSummary(toInsightsQuery(query));
        if (!result.success()) {
            return UseCaseResult.failure(result.error());
        }
        return UseCaseResult.success(new SessionSummaryResult(result.data()
                .currentSession()
                .map(session -> new CurrentSessionSnapshot(
                        session.swipeCount(), session.likeCount(), session.passCount(), session.formattedDuration()))));
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

    public UseCaseResult<Void> deleteAccount(DeleteAccountCommand command) {
        return profileMutationUseCases.deleteAccount(command);
    }

    public UseCaseResult<List<ProfileNote>> listProfileNotes(ProfileNotesQuery query) {
        return profileNotesUseCases.listProfileNotes(
                query == null ? null : new ProfileNotesUseCases.ProfileNotesQuery(query.context()));
    }

    public UseCaseResult<ProfileNote> getProfileNote(ProfileNoteQuery query) {
        return profileNotesUseCases.getProfileNote(
                query == null ? null : new ProfileNotesUseCases.ProfileNoteQuery(query.context(), query.subjectId()));
    }

    public UseCaseResult<ProfileNote> upsertProfileNote(UpsertProfileNoteCommand command) {
        return profileNotesUseCases.upsertProfileNote(
                command == null
                        ? null
                        : new ProfileNotesUseCases.UpsertProfileNoteCommand(
                                command.context(), command.subjectId(), command.content()));
    }

    public UseCaseResult<Void> deleteProfileNote(DeleteProfileNoteCommand command) {
        return profileNotesUseCases.deleteProfileNote(
                command == null
                        ? null
                        : new ProfileNotesUseCases.DeleteProfileNoteCommand(command.context(), command.subjectId()));
    }

    public ProfileNotesUseCases getProfileNotesUseCases() {
        return profileNotesUseCases;
    }

    public ProfileMutationUseCases getProfileMutationUseCases() {
        return profileMutationUseCases;
    }

    public ProfileInsightsUseCases getProfileInsightsUseCases() {
        return profileInsightsUseCases;
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

    public static record SessionSummaryQuery(UserContext context) {}

    public static record CurrentSessionSnapshot(
            int swipeCount, int likeCount, int passCount, String formattedDuration) {}

    public static record SessionSummaryResult(java.util.Optional<CurrentSessionSnapshot> currentSession) {}

    public static record DeleteAccountCommand(
            UserContext context, datingapp.app.event.AppEvent.DeletionReason reason) {}

    public static record ProfileNotesQuery(UserContext context) {}

    public static record ProfileNoteQuery(UserContext context, UUID subjectId) {}

    public static record UpsertProfileNoteCommand(UserContext context, UUID subjectId, String content) {}

    public static record DeleteProfileNoteCommand(UserContext context, UUID subjectId) {}

    public static record GetUsersByIdsQuery(List<UUID> userIds) {}

    private static ProfileInsightsUseCases.AchievementsQuery toInsightsQuery(AchievementsQuery query) {
        return new ProfileInsightsUseCases.AchievementsQuery(
                query == null ? null : query.context(), query != null && query.checkForNew());
    }

    private static ProfileInsightsUseCases.StatsQuery toInsightsQuery(StatsQuery query) {
        return new ProfileInsightsUseCases.StatsQuery(query == null ? null : query.context());
    }

    private static ProfileInsightsUseCases.SessionSummaryQuery toInsightsQuery(SessionSummaryQuery query) {
        return new ProfileInsightsUseCases.SessionSummaryQuery(query == null ? null : query.context());
    }
}
