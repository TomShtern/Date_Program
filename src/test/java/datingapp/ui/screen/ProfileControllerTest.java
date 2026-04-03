package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
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
import datingapp.ui.viewmodel.ProfileViewModel;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("ProfileController wiring and binding tests")
class ProfileControllerTest {

    private static final String FILE_URLS_ENABLED_PROPERTY = "datingapp.allowFileUrls";
    private static final String FILE_URL_ROOT_PROPERTY = "datingapp.allowedFileUrlRoot";

    private static final datingapp.ui.async.UiThreadDispatcher TEST_DISPATCHER =
            JavaFxTestSupport.immediateUiDispatcher();

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @Test
    @DisplayName("FXML photo navigation and primary-photo action stay wired")
    void photoNavigationAndPrimaryPhotoActionStayWired() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("datingapp-profile-controller-home");
        try {
            System.setProperty("user.home", tempHome.toString());
            System.setProperty(FILE_URLS_ENABLED_PROPERTY, "true");
            System.setProperty(FILE_URL_ROOT_PROPERTY, tempHome.toString());

            TestStorages.Users users = new TestStorages.Users();
            AppConfig config = AppConfig.defaults();
            ProfileService profileService = new ProfileService(users);

            User currentUser = createActiveUser("Photo User");
            Path managedDir = tempHome.resolve(".datingapp").resolve("photos");
            Files.createDirectories(managedDir);
            Path photoOne = Files.createTempFile(managedDir, "photo-one", ".png");
            Path photoTwo = Files.createTempFile(managedDir, "photo-two", ".png");
            currentUser.setPhotoUrls(
                    List.of(photoOne.toUri().toString(), photoTwo.toUri().toString()));
            users.save(currentUser);
            AppSession.getInstance().setCurrentUser(currentUser);

            ProfileViewModel viewModel = new ProfileViewModel(new ProfileViewModel.Dependencies(
                    new StorageUiUserStore(users),
                    profileService,
                    null,
                    config,
                    AppSession.getInstance(),
                    new ValidationService(config),
                    new LocationService(new ValidationService(config)),
                    TEST_DISPATCHER,
                    new datingapp.core.workflow.ProfileActivationPolicy()));

            JavaFxTestSupport.LoadedFxml loaded =
                    JavaFxTestSupport.loadFxml("/fxml/profile.fxml", () -> new ProfileController(viewModel));
            Parent root = loaded.root();
            Button nextPhotoButton = JavaFxTestSupport.lookup(root, "#nextPhotoButton", Button.class);
            Button setPrimaryPhotoButton = JavaFxTestSupport.lookup(root, "#setPrimaryPhotoButton", Button.class);
            Label photoIndicatorLabel = JavaFxTestSupport.lookup(root, "#photoIndicatorLabel", Label.class);

            assertTrue(JavaFxTestSupport.waitUntil(
                    () -> {
                        try {
                            return JavaFxTestSupport.callOnFxAndWait(photoIndicatorLabel::isVisible)
                                    && "1/2".equals(JavaFxTestSupport.callOnFxAndWait(photoIndicatorLabel::getText));
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                    },
                    5000));

            JavaFxTestSupport.runOnFxAndWait(nextPhotoButton::fire);
            assertEquals("2/2", JavaFxTestSupport.callOnFxAndWait(photoIndicatorLabel::getText));

            JavaFxTestSupport.runOnFxAndWait(setPrimaryPhotoButton::fire);
            assertTrue(JavaFxTestSupport.waitUntil(
                    () -> AppSession.getInstance()
                            .getCurrentUser()
                            .getPhotoUrls()
                            .getFirst()
                            .equals(photoTwo.toUri().toString()),
                    5000));

            viewModel.dispose();
            NavigationService.getInstance().clearHistory();
            AppSession.getInstance().reset();
        } finally {
            System.clearProperty(FILE_URLS_ENABLED_PROPERTY);
            System.clearProperty(FILE_URL_ROOT_PROPERTY);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    @DisplayName("Profile preview and score buttons stay wired with valid handlers (Task 5 Regression)")
    void previewAndProfileScoreButtonsStayWired() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("datingapp-profile-buttons-home");
        try {
            System.setProperty("user.home", tempHome.toString());
            System.setProperty(FILE_URLS_ENABLED_PROPERTY, "true");
            System.setProperty(FILE_URL_ROOT_PROPERTY, tempHome.toString());

            TestStorages.Users users = new TestStorages.Users();
            AppConfig config = AppConfig.defaults();
            ProfileService profileService = new ProfileService(users);

            User currentUser = createActiveUser("Button Test User");
            users.save(currentUser);
            AppSession.getInstance().setCurrentUser(currentUser);

            ProfileViewModel viewModel = new ProfileViewModel(new ProfileViewModel.Dependencies(
                    new datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore(users),
                    profileService,
                    null,
                    config,
                    AppSession.getInstance(),
                    new ValidationService(config),
                    new LocationService(new ValidationService(config)),
                    TEST_DISPATCHER,
                    new datingapp.core.workflow.ProfileActivationPolicy()));

            JavaFxTestSupport.LoadedFxml loaded =
                    JavaFxTestSupport.loadFxml("/fxml/profile.fxml", () -> new ProfileController(viewModel));
            Parent root = loaded.root();

            Button previewButton = JavaFxTestSupport.lookup(root, "#previewButton", Button.class);
            Button profileScoreButton = JavaFxTestSupport.lookup(root, "#profileScoreButton", Button.class);

            // Assert buttons are found and non-null (wired in FXML)
            assertNotNull(previewButton, "previewButton should be wired in profile.fxml");
            assertNotNull(profileScoreButton, "profileScoreButton should be wired in profile.fxml");

            // Assert buttons are accessible (indicates wiring is successful without crashing)
            assertEquals(
                    "Preview",
                    JavaFxTestSupport.callOnFxAndWait(previewButton::getText),
                    "previewButton should have correct text");
            assertEquals(
                    "Profile Score",
                    JavaFxTestSupport.callOnFxAndWait(profileScoreButton::getText),
                    "profileScoreButton should have correct text");
            viewModel.dispose();
            NavigationService.getInstance().clearHistory();
            AppSession.getInstance().reset();
        } finally {
            System.clearProperty(FILE_URLS_ENABLED_PROPERTY);
            System.clearProperty(FILE_URL_ROOT_PROPERTY);
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    @DisplayName("successful incomplete save keeps user on profile and shows completion guidance")
    void successfulIncompleteSaveKeepsUserOnProfileAndShowsCompletionGuidance() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = User.StorageBuilder.create(UUID.randomUUID(), "Draft User", AppClock.now())
                .state(User.UserState.INCOMPLETE)
                .build();
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        ProfileViewModel viewModel = new ProfileViewModel(new ProfileViewModel.Dependencies(
                new StorageUiUserStore(users),
                profileService,
                null,
                config,
                AppSession.getInstance(),
                new ValidationService(config),
                new LocationService(new ValidationService(config)),
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy()));
        TrackingProfileController controller = new TrackingProfileController(viewModel);

        JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml("/fxml/profile.fxml", () -> controller);
        Parent root = loaded.root();
        Button saveButton = JavaFxTestSupport.lookup(root, "#saveButton", Button.class);

        viewModel.bioProperty().set("Draft bio updated");

        JavaFxTestSupport.runOnFxAndWait(saveButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> "Draft bio updated"
                        .equals(AppSession.getInstance().getCurrentUser().getBio()),
                5000));
        assertFalse(controller.navigatedToDashboard());
        assertTrue(viewModel.completionDetailsProperty().get().contains("Missing"));

        controller.cleanup();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("successful activated save navigates to dashboard")
    void successfulActivatedSaveNavigatesToDashboard() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActivatableIncompleteUser("Activated User");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        ProfileViewModel viewModel = new ProfileViewModel(new ProfileViewModel.Dependencies(
                new StorageUiUserStore(users),
                profileService,
                null,
                config,
                AppSession.getInstance(),
                new ValidationService(config),
                new LocationService(new ValidationService(config)),
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy()));
        TrackingProfileController controller = new TrackingProfileController(viewModel);

        JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml("/fxml/profile.fxml", () -> controller);
        Parent root = loaded.root();
        Button saveButton = JavaFxTestSupport.lookup(root, "#saveButton", Button.class);

        viewModel.bioProperty().set("Activated bio updated");

        JavaFxTestSupport.runOnFxAndWait(saveButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(controller::navigatedToDashboard, 5000));

        controller.cleanup();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("successful save for an already active profile still navigates to dashboard")
    void successfulSaveForAlreadyActiveProfileNavigatesToDashboard() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActiveUser("Active User");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        ProfileViewModel viewModel = new ProfileViewModel(new ProfileViewModel.Dependencies(
                new StorageUiUserStore(users),
                profileService,
                null,
                config,
                AppSession.getInstance(),
                new ValidationService(config),
                new LocationService(new ValidationService(config)),
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy()));
        TrackingProfileController controller = new TrackingProfileController(viewModel);

        JavaFxTestSupport.LoadedFxml loaded = JavaFxTestSupport.loadFxml("/fxml/profile.fxml", () -> controller);
        Parent root = loaded.root();
        Button saveButton = JavaFxTestSupport.lookup(root, "#saveButton", Button.class);

        viewModel.bioProperty().set("Active user bio updated");

        JavaFxTestSupport.runOnFxAndWait(saveButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(controller::navigatedToDashboard, 5000));

        controller.cleanup();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(25));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.setAgeRange(18, 60, 18, 120);
        user.setMaxDistanceKm(50, 500);
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("https://example.com/profile-active.jpg");
        user.setBio("Bio");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    private static User createActivatableIncompleteUser(String name) {
        return User.StorageBuilder.create(UUID.randomUUID(), name, AppClock.now())
                .state(User.UserState.INCOMPLETE)
                .bio("Draft bio")
                .birthDate(AppClock.today().minusYears(25))
                .gender(Gender.OTHER)
                .interestedIn(EnumSet.of(Gender.OTHER))
                .location(40.7128, -74.0060)
                .hasLocationSet(true)
                .ageRange(18, 60)
                .maxDistanceKm(50)
                .photoUrls(List.of("http://example.com/photo.jpg"))
                .pacePreferences(new PacePreferences(
                        PacePreferences.MessagingFrequency.OFTEN,
                        PacePreferences.TimeToFirstDate.FEW_DAYS,
                        PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                        PacePreferences.DepthPreference.DEEP_CHAT))
                .build();
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
