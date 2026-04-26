package datingapp.app.usecase.profile;

import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.UserStorage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Profile and user-progress application use-cases shared across adapters. */
public class ProfileUseCases {
    private static final String USER_NOT_FOUND = "User not found";

    private final UserStorage userStorage;
    private final ProfileService profileService;
    private final ValidationService validationService;
    private final ProfileMutationUseCases profileMutationUseCases;
    private final ProfileNotesUseCases profileNotesUseCases;
    private final ProfileInsightsUseCases profileInsightsUseCases;

    public ProfileUseCases(
            UserStorage userStorage,
            ProfileService profileService,
            ValidationService validationService,
            ProfileMutationUseCases profileMutationUseCases,
            ProfileNotesUseCases profileNotesUseCases,
            ProfileInsightsUseCases profileInsightsUseCases) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.profileService = Objects.requireNonNull(profileService, "profileService cannot be null");
        this.validationService = Objects.requireNonNull(validationService, "validationService cannot be null");
        this.profileMutationUseCases =
                Objects.requireNonNull(profileMutationUseCases, "profileMutationUseCases cannot be null");
        this.profileNotesUseCases = Objects.requireNonNull(profileNotesUseCases, "profileNotesUseCases cannot be null");
        this.profileInsightsUseCases =
                Objects.requireNonNull(profileInsightsUseCases, "profileInsightsUseCases cannot be null");
    }

    public UseCaseResult<ProfileMutationUseCases.ProfileSaveResult> saveProfile(
            ProfileMutationUseCases.SaveProfileCommand command) {
        return profileMutationUseCases.saveProfile(command);
    }

    public UseCaseResult<User> updateDiscoveryPreferences(
            ProfileMutationUseCases.UpdateDiscoveryPreferencesCommand command) {
        return profileMutationUseCases.updateDiscoveryPreferences(command);
    }

    public UseCaseResult<ProfileMutationUseCases.ProfileSaveResult> updateProfile(
            ProfileMutationUseCases.UpdateProfileCommand command) {
        return profileMutationUseCases.updateProfile(command);
    }

    public UseCaseResult<List<User>> listUsers() {
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

        try {
            if (query.userIds().isEmpty()) {
                return UseCaseResult.success(Map.of());
            }

            Set<UUID> deduplicatedIds = new java.util.HashSet<>(query.userIds().size());
            for (UUID id : query.userIds()) {
                if (id != null) {
                    deduplicatedIds.add(id);
                }
            }
            if (deduplicatedIds.isEmpty()) {
                return UseCaseResult.success(Map.of());
            }

            Map<UUID, User> usersById = userStorage.findByIds(deduplicatedIds);
            LinkedHashMap<UUID, User> stableUsers = new LinkedHashMap<>();
            for (UUID userId : query.userIds()) {
                if (userId != null) {
                    User user = usersById.get(userId);
                    if (user != null) {
                        stableUsers.put(userId, user);
                    }
                }
            }
            return UseCaseResult.success(Collections.unmodifiableMap(stableUsers));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load users: " + e.getMessage()));
        }
    }

    public UseCaseResult<ProfileInsightsUseCases.AchievementSnapshot> getAchievements(
            ProfileInsightsUseCases.AchievementsQuery query) {
        return profileInsightsUseCases.getAchievements(query);
    }

    public UseCaseResult<datingapp.core.metrics.EngagementDomain.UserStats> getOrComputeStats(
            ProfileInsightsUseCases.StatsQuery query) {
        return profileInsightsUseCases.getOrComputeStats(query);
    }

    public UseCaseResult<ProfileInsightsUseCases.SessionSummaryResult> getSessionSummary(
            ProfileInsightsUseCases.SessionSummaryQuery query) {
        return profileInsightsUseCases.getSessionSummary(query);
    }

    public UseCaseResult<List<Session>> getSessionHistory(ProfileInsightsUseCases.SessionHistoryQuery query) {
        return profileInsightsUseCases.getSessionHistory(query);
    }

    public UseCaseResult<ProfileService.CompletionResult> calculateCompletion(User user) {
        if (user == null) {
            return UseCaseResult.failure(UseCaseError.validation("User is required"));
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
        try {
            return UseCaseResult.success(profileService.generatePreview(user));
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to generate profile preview: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> deleteAccount(ProfileMutationUseCases.DeleteAccountCommand command) {
        return profileMutationUseCases.deleteAccount(command);
    }

    public UseCaseResult<List<ProfileNote>> listProfileNotes(ProfileNotesUseCases.ProfileNotesQuery query) {
        return profileNotesUseCases.listProfileNotes(query);
    }

    public UseCaseResult<ProfileNote> getProfileNote(ProfileNotesUseCases.ProfileNoteQuery query) {
        return profileNotesUseCases.getProfileNote(query);
    }

    public UseCaseResult<ProfileNote> upsertProfileNote(ProfileNotesUseCases.UpsertProfileNoteCommand command) {
        return profileNotesUseCases.upsertProfileNote(command);
    }

    public UseCaseResult<Void> deleteProfileNote(ProfileNotesUseCases.DeleteProfileNoteCommand command) {
        return profileNotesUseCases.deleteProfileNote(command);
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

    public static record GetUsersByIdsQuery(List<UUID> userIds) {}
}
