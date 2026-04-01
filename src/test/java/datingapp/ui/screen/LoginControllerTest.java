package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.viewmodel.LoginViewModel;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("LoginController wiring and filter behavior")
class LoginControllerTest {

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
    @DisplayName("FXML filter narrows the list and selection enables login")
    void fxmlFilterNarrowsListAndSelectionEnablesLogin() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        users.save(createActiveUser("Alex", Gender.MALE, EnumSet.of(Gender.FEMALE)));
        users.save(createActiveUser("Blair", Gender.FEMALE, EnumSet.of(Gender.MALE)));

        LoginViewModel viewModel = new LoginViewModel(
                new StorageUiUserStore(users),
                config,
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/login.fxml", () -> new LoginController(viewModel, profileService));
        Parent root = loaded.root();
        TextField filterField = JavaFxTestSupport.lookup(root, "#filterField", TextField.class);
        Button loginButton = JavaFxTestSupport.lookup(root, "#loginButton", Button.class);
        @SuppressWarnings("unchecked")
        ListView<User> userListView = JavaFxTestSupport.lookup(root, "#userListView", ListView.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(
                                        () -> userListView.getItems().size())
                                == 2;
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        JavaFxTestSupport.runOnFxAndWait(() -> filterField.setText("alex"));

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(
                                                () -> userListView.getItems().size())
                                        == 1
                                && "Alex".equals(JavaFxTestSupport.callOnFxAndWait(() -> userListView
                                        .getItems()
                                        .getFirst()
                                        .getName()));
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        assertEquals(false, JavaFxTestSupport.callOnFxAndWait(loginButton::isDisabled));

        viewModel.dispose();
    }

    @Test
    @DisplayName("Empty user repository keeps login button disabled")
    void emptyUserRepositoryKeepsLoginButtonDisabled() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        // No users saved - repository is empty

        LoginViewModel viewModel = new LoginViewModel(
                new StorageUiUserStore(users),
                config,
                AppSession.getInstance(),
                JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/login.fxml", () -> new LoginController(viewModel, profileService));
        Parent root = loaded.root();
        Button loginButton = JavaFxTestSupport.lookup(root, "#loginButton", Button.class);
        @SuppressWarnings("unchecked")
        ListView<User> userListView = JavaFxTestSupport.lookup(root, "#userListView", ListView.class);

        // Wait briefly to allow any async initialization
        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(
                                        () -> userListView.getItems().size())
                                == 0;
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        assertEquals(true, JavaFxTestSupport.callOnFxAndWait(loginButton::isDisabled));

        viewModel.dispose();
    }

    private static User createActiveUser(String name, Gender gender, EnumSet<Gender> interestedIn) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(25));
        user.setGender(gender);
        user.setInterestedIn(interestedIn);
        user.setAgeRange(18, 60, 18, 120);
        user.setMaxDistanceKm(50, AppConfig.defaults().matching().maxDistanceKm());
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setBio("Login test bio");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }
}
