package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.OnboardingContext;
import datingapp.ui.viewmodel.LoginViewModel;
import datingapp.ui.viewmodel.ProfileViewModel;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("First-run onboarding journey regression")
class OnboardingFlowTest {

    private static final String PROFILE_FXML = "/fxml/profile.fxml";

    private static final datingapp.ui.async.UiThreadDispatcher TEST_DISPATCHER =
            JavaFxTestSupport.immediateUiDispatcher();

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @AfterEach
    void tearDown() {
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName(
            "new account enters onboarding, stays on profile while incomplete, then reaches dashboard once activated")
    void newAccountEntersOnboardingStaysOnProfileWhileIncompleteThenReachesDashboardOnceActivated() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);
        LoginViewModel loginViewModel = new LoginViewModel(
                new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                config,
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());
        LoginController loginController = new LoginController(
                loginViewModel, profileService, config.safety().userTimeZone());

        User createdUser = loginViewModel.createUser("Taylor", 29, Gender.OTHER, Gender.FEMALE);
        assertNotNull(createdUser);

        JavaFxTestSupport.runOnFxAndWait(() -> loginController.continueIntoOnboarding(createdUser));
        assertEquals(createdUser, AppSession.getInstance().getCurrentUser());

        ProfileViewModel profileViewModel = createProfileViewModel(users, config, profileService);
        profileViewModel.setOnboardingContext(OnboardingContext.newAccount(createdUser.getId()));
        TrackingProfileController profileController = new TrackingProfileController(profileViewModel);

        JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml(PROFILE_FXML, () -> profileController);
        Parent root = loaded.root();

        Label onboardingHeadlineLabel = JavaFxTestSupport.lookup(root, "#onboardingHeadlineLabel", Label.class);
        Button saveButton = JavaFxTestSupport.lookup(root, "#saveButton", Button.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(onboardingHeadlineLabel::isVisible);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));
        assertEquals("Finish onboarding", JavaFxTestSupport.callOnFxAndWait(saveButton::getText));

        JavaFxTestSupport.runOnFxAndWait(() -> profileViewModel.bioProperty().set("Journey bio"));
        JavaFxTestSupport.runOnFxAndWait(saveButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> "Journey bio"
                        .equals(AppSession.getInstance().getCurrentUser().getBio()),
                5000));
        assertEquals(
                User.UserState.INCOMPLETE,
                AppSession.getInstance().getCurrentUser().getState());
        assertTrue(profileViewModel.onboardingActiveProperty().get());
        assertFalse(profileController.navigatedToDashboard());

        JavaFxTestSupport.runOnFxAndWait(() -> {
            profileViewModel.setLocationCoordinates(32.0853, 34.7818);
            profileViewModel.messagingFrequencyProperty().set(PacePreferences.MessagingFrequency.OFTEN);
            profileViewModel.timeToFirstDateProperty().set(PacePreferences.TimeToFirstDate.FEW_DAYS);
            profileViewModel.communicationStyleProperty().set(PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING);
            profileViewModel.depthPreferenceProperty().set(PacePreferences.DepthPreference.DEEP_CHAT);
        });
        AppSession.getInstance().getCurrentUser().setPhotoUrls(List.of("https://example.com/taylor.jpg"));
        JavaFxTestSupport.runOnFxAndWait(saveButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(profileController::navigatedToDashboard, 5000));
        assertEquals(
                User.UserState.ACTIVE, AppSession.getInstance().getCurrentUser().getState());
        assertFalse(profileViewModel.onboardingActiveProperty().get());

        profileViewModel.dispose();
        loginViewModel.dispose();
    }

    private static ProfileViewModel createProfileViewModel(
            TestStorages.Users users, AppConfig config, ProfileService profileService) {
        ValidationService validationService = new ValidationService(config);
        return new ProfileViewModel(new ProfileViewModel.Dependencies(
                new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                profileService,
                new datingapp.app.usecase.profile.ProfileMutationUseCases(
                        users,
                        validationService,
                        datingapp.core.testutil.TestAchievementService.empty(),
                        config,
                        new datingapp.core.workflow.ProfileActivationPolicy(),
                        new datingapp.app.testutil.TestEventBus()),
                null,
                config,
                AppSession.getInstance(),
                validationService,
                new LocationService(validationService),
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy()));
    }

    private static final class TrackingProfileController extends ProfileController {
        private boolean navigatedToDashboard;

        TrackingProfileController(ProfileViewModel viewModel) {
            super(viewModel);
        }

        @Override
        protected void navigateToDashboard() {
            navigatedToDashboard = true;
        }

        boolean navigatedToDashboard() {
            return navigatedToDashboard;
        }
    }
}
