package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestStorages;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for ProfileHandler validation and input-gating behavior. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ProfileHandlerTest {

    private TestStorages.Users userStorage;
    private AppSession session;
    private ValidationService validationService;
    private ProfileUseCases profileUseCases;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        session = AppSession.getInstance();
        session.reset();
        validationService = new ValidationService(AppConfig.defaults());
        profileUseCases = new ProfileUseCases(
                userStorage,
                null,
                validationService,
                null,
                null,
                AppConfig.defaults(),
                new datingapp.core.workflow.ProfileActivationPolicy(),
                null);
    }

    @Test
    @DisplayName("createUser rejects overlong names")
    void createUserRejectsOverlongNames() {
        ProfileHandler handler = createHandler(repeat('a', 101) + "\n");

        handler.createUser();

        assertEquals(0, userStorage.findAll().size());
        assertNull(session.getCurrentUser());
    }

    @Test
    @DisplayName("promptBio rejects overlong bios")
    void promptBioRejectsOverlongBios() throws Exception {
        ProfileHandler handler = createHandler(repeat('b', 501) + "\n");
        User user = createEditableUser();

        invokePrompt(handler, "promptBio", user);

        assertNull(user.getBio());
    }

    @Test
    @DisplayName("promptPhoto rejects unsafe URLs")
    void promptPhotoRejectsUnsafeUrls() throws Exception {
        ProfileHandler handler = createHandler("javascript:alert(1)\n");
        User user = createEditableUser();

        invokePrompt(handler, "promptPhoto", user);

        assertTrue(user.getPhotoUrls().isEmpty());
    }

    @Test
    @DisplayName("completeProfile keeps session user unchanged when save fails")
    void completeProfileKeepsSessionUserUnchangedWhenSaveFails() {
        User original = createEditableUser();
        original.setBio("original-bio");
        session.setCurrentUser(original);

        ProfileUseCases failingUseCases =
                new ProfileUseCases(
                        userStorage,
                        null,
                        validationService,
                        null,
                        null,
                        AppConfig.defaults(),
                        new datingapp.core.workflow.ProfileActivationPolicy(),
                        null) {
                    @Override
                    public UseCaseResult<ProfileUseCases.ProfileSaveResult> saveProfile(
                            ProfileUseCases.SaveProfileCommand command) {
                        return UseCaseResult.failure(UseCaseError.internal("forced-save-failure"));
                    }
                };

        InputReader inputReader = new InputReader(new Scanner(new StringReader("new-bio\n")));
        ProfileHandler handler = new ProfileHandler(
                userStorage, validationService, failingUseCases, AppConfig.defaults(), session, inputReader);

        handler.completeProfile();

        assertEquals("original-bio", session.getCurrentUser().getBio());
        assertEquals(original.getId(), session.getCurrentUser().getId());
    }

    private ProfileHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        return new ProfileHandler(
                userStorage, validationService, profileUseCases, AppConfig.defaults(), session, inputReader);
    }

    private static void invokePrompt(ProfileHandler handler, String methodName, User user) throws Exception {
        Method method = ProfileHandler.class.getDeclaredMethod(methodName, User.class);
        method.setAccessible(true);
        method.invoke(handler, user);
    }

    private static User createEditableUser() {
        User user = new User(UUID.randomUUID(), "Test User");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.TEXT_ONLY,
                PacePreferences.DepthPreference.SMALL_TALK));
        return user;
    }

    private static String repeat(char value, int count) {
        return String.valueOf(value).repeat(count);
    }
}
