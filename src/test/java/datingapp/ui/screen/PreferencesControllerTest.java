package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.UiPreferencesStore;
import datingapp.ui.UiPreferencesStore.ThemeMode;
import datingapp.ui.UiThemeService;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.PreferencesViewModel;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("PreferencesController bindings")
class PreferencesControllerTest {

    private static final UiThreadDispatcher TEST_DISPATCHER = JavaFxTestSupport.immediateUiDispatcher();

    private final AppSession session = AppSession.getInstance();
    private PreferencesViewModel viewModel;

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @AfterEach
    void tearDown() {
        if (viewModel != null) {
            viewModel.dispose();
        }
        NavigationService.getInstance().clearHistory();
        session.reset();
    }

    @Test
    @DisplayName("FXML initialize reflects persisted theme and discovery values")
    void initializeReflectsPersistedThemeAndDiscoveryValues() throws Exception {
        User user = new User(UUID.randomUUID(), "Test User");
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
        user.setBio("Test");
        user.setLocation(40.7128, -74.0060);
        user.setAgeRange(20, 40, 18, 65);
        user.setMaxDistanceKm(50, 100);
        session.setCurrentUser(user);

        TestStorages.Users userStorage = new TestStorages.Users();
        userStorage.save(user);

        UiPreferencesStore store =
                new UiPreferencesStore("/datingapp/tests/preferences-controller-" + UUID.randomUUID());
        store.saveThemeMode(ThemeMode.LIGHT);
        UiThemeService themeService = new UiThemeService(store, NavigationService.getInstance());

        viewModel = new PreferencesViewModel(
                new StorageUiUserStore(userStorage),
                null,
                themeService,
                AppConfig.defaults(),
                session,
                TEST_DISPATCHER);

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/preferences.fxml", () -> new PreferencesController(viewModel));
        Parent root = loaded.root();
        ToggleButton themeToggle = JavaFxTestSupport.lookup(root, "#themeToggle", ToggleButton.class);
        ToggleButton womenToggle = JavaFxTestSupport.lookup(root, "#womenToggle", ToggleButton.class);
        Slider distanceSlider = JavaFxTestSupport.lookup(root, "#distanceSlider", Slider.class);

        assertTrue(
                JavaFxTestSupport.waitUntil(() -> viewModel.themeModeProperty().get() == ThemeMode.LIGHT, 5000));
        assertFalse(JavaFxTestSupport.callOnFxAndWait(themeToggle::isSelected));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(womenToggle::isSelected));
        assertEquals(50, JavaFxTestSupport.callOnFxAndWait(() -> (int) distanceSlider.getValue()));
    }

    @Test
    @DisplayName("theme toggle and gender buttons update the bound view model")
    void themeToggleAndGenderButtonsUpdateViewModel() throws Exception {
        User user = new User(UUID.randomUUID(), "Theme Tester");
        user.setGender(Gender.FEMALE);
        user.setInterestedIn(EnumSet.of(Gender.MALE, Gender.FEMALE, Gender.OTHER));
        user.setBio("Test");
        user.setLocation(40.7128, -74.0060);
        user.setAgeRange(20, 40, 18, 65);
        user.setMaxDistanceKm(50, 100);
        session.setCurrentUser(user);

        TestStorages.Users userStorage = new TestStorages.Users();
        userStorage.save(user);

        UiPreferencesStore store =
                new UiPreferencesStore("/datingapp/tests/preferences-controller-" + UUID.randomUUID());
        store.saveThemeMode(ThemeMode.DARK);
        UiThemeService themeService = new UiThemeService(store, NavigationService.getInstance());

        viewModel = new PreferencesViewModel(
                new StorageUiUserStore(userStorage),
                null,
                themeService,
                AppConfig.defaults(),
                session,
                TEST_DISPATCHER);

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/preferences.fxml", () -> new PreferencesController(viewModel));
        Parent root = loaded.root();
        ToggleButton themeToggle = JavaFxTestSupport.lookup(root, "#themeToggle", ToggleButton.class);
        ToggleButton menToggle = JavaFxTestSupport.lookup(root, "#menToggle", ToggleButton.class);

        JavaFxTestSupport.runOnFxAndWait(() -> {
            themeToggle.fire();
            menToggle.setSelected(true);
        });

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> viewModel.themeModeProperty().get() == ThemeMode.LIGHT
                        && viewModel.interestedInProperty().get() == PreferencesViewModel.GenderPreference.MEN,
                5000));
        assertEquals(ThemeMode.LIGHT, store.loadThemeMode());
    }
}
