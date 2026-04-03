package datingapp.app.usecase.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.testutil.TestEventBus;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.workflow.ProfileActivationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileMutationUseCases")
class ProfileMutationUseCasesTest {

    private TestStorages.Users userStorage;
    private ProfileMutationUseCases useCases;

    @BeforeEach
    void setUp() {
        AppConfig config = AppConfig.defaults();
        userStorage = new TestStorages.Users();
        useCases = new ProfileMutationUseCases(
                userStorage,
                new ValidationService(config),
                TestAchievementService.empty(),
                config,
                new ProfileActivationPolicy(),
                new TestEventBus());
    }

    @Test
    @DisplayName("createUser creates and persists an honest incomplete account")
    void createUserCreatesAndPersistsHonestIncompleteAccount() {
        var result = useCases.createUser(
                new ProfileMutationUseCases.CreateUserCommand("  New User  ", 25, Gender.OTHER, Gender.FEMALE));

        assertTrue(result.success());
        User createdUser = result.data().user();
        assertNotNull(createdUser.getId());
        assertEquals("New User", createdUser.getName());
        assertEquals(UserState.INCOMPLETE, createdUser.getState());
        assertEquals(AppClock.today().minusYears(25), createdUser.getBirthDate());
        assertEquals(Gender.OTHER, createdUser.getGender());
        assertEquals(java.util.Set.of(Gender.FEMALE), createdUser.getInterestedIn());
        assertEquals("", createdUser.getBio());
        assertTrue(createdUser.getPhotoUrls().isEmpty());
        assertEquals(Dealbreakers.none(), createdUser.getDealbreakers());
        assertFalse(createdUser.isComplete());
        assertTrue(userStorage.get(createdUser.getId()).isPresent());
    }

    @Test
    @DisplayName("createUser rejects invalid input")
    void createUserRejectsInvalidInput() {
        var result = useCases.createUser(new ProfileMutationUseCases.CreateUserCommand(" ", 17, null, null));

        assertFalse(result.success());
        assertEquals("Name cannot be empty", result.error().message());
    }

    @Test
    @DisplayName("saveProfile accepts slice-local command and returns slice-local result")
    void saveProfileUsesSliceLocalContract() {
        User user = new User(java.util.UUID.randomUUID(), "Slice Save User");

        var result = useCases.saveProfile(
                new ProfileMutationUseCases.SaveProfileCommand(UserContext.cli(user.getId()), user));

        assertTrue(result.success());
        assertNotNull(result.data().user());
        assertEquals(user.getId(), result.data().user().getId());
    }

    @Test
    @DisplayName("updateDiscoveryPreferences accepts slice-local command")
    void updateDiscoveryPreferencesUsesSliceLocalContract() {
        User user = new User(java.util.UUID.randomUUID(), "Slice Preference User");
        userStorage.save(user);

        var result = useCases.updateDiscoveryPreferences(new ProfileMutationUseCases.UpdateDiscoveryPreferencesCommand(
                UserContext.cli(user.getId()), 18, 40, 25, java.util.Set.of(Gender.OTHER)));

        assertTrue(result.success());
        assertEquals(18, result.data().getMinAge());
        assertEquals(40, result.data().getMaxAge());
        assertEquals(25, result.data().getMaxDistanceKm());
        assertEquals(java.util.Set.of(Gender.OTHER), result.data().getInterestedIn());
    }
}
