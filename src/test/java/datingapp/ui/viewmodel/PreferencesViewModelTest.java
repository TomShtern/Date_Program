package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.NavigationService;
import datingapp.ui.UiPreferencesStore;
import datingapp.ui.UiPreferencesStore.ThemeMode;
import datingapp.ui.UiThemeService;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("PreferencesViewModel theme and discovery preferences")
class PreferencesViewModelTest {

    private static final UiThreadDispatcher TEST_DISPATCHER = new UiThreadDispatcher() {
        @Override
        public boolean isUiThread() {
            return true;
        }

        @Override
        public void dispatch(Runnable action) {
            action.run();
        }
    };

    private final AppSession session = AppSession.getInstance();

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException _) {
            // Toolkit already initialized
        }
    }

    @AfterEach
    void tearDown() {
        session.reset();
    }

    @Test
    @DisplayName("initialize loads persisted theme mode")
    void initializeLoadsPersistedThemeMode() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        User currentUser = createUser("Taylor");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        UiPreferencesStore store = new UiPreferencesStore("/datingapp/tests/preferences-" + UUID.randomUUID());
        store.saveThemeMode(ThemeMode.LIGHT);
        UiThemeService themeService = new UiThemeService(store, NavigationService.getInstance());

        PreferencesViewModel viewModel = new PreferencesViewModel(
                new StorageUiUserStore(users), null, themeService, AppConfig.defaults(), session, TEST_DISPATCHER);

        viewModel.initialize();
        waitForAsyncWork();

        assertEquals(ThemeMode.LIGHT, viewModel.themeModeProperty().get());
        assertEquals(currentUser.getMinAge(), viewModel.minAgeProperty().get());

        viewModel.dispose();
    }

    @Test
    @DisplayName("savePreferences persists discovery settings and theme")
    void savePreferencesPersistsDiscoverySettingsAndTheme() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        User currentUser = createUser("Jordan");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        UiPreferencesStore store = new UiPreferencesStore("/datingapp/tests/preferences-" + UUID.randomUUID());
        UiThemeService themeService = new UiThemeService(store, NavigationService.getInstance());
        PreferencesViewModel viewModel = new PreferencesViewModel(
                new StorageUiUserStore(users), null, themeService, AppConfig.defaults(), session, TEST_DISPATCHER);

        viewModel.initialize();
        waitForAsyncWork();

        viewModel.minAgeProperty().set(24);
        viewModel.maxAgeProperty().set(38);
        viewModel.maxDistanceProperty().set(77);
        viewModel.interestedInProperty().set(PreferencesViewModel.GenderPreference.MEN);
        viewModel.updateThemeMode(ThemeMode.LIGHT);
        waitForAsyncWork();

        viewModel.savePreferences();
        waitForAsyncWork();

        User savedUser = users.get(currentUser.getId()).orElseThrow();
        assertEquals(24, savedUser.getMinAge());
        assertEquals(38, savedUser.getMaxAge());
        assertEquals(77, savedUser.getMaxDistanceKm());
        assertTrue(savedUser.getInterestedIn().contains(Gender.MALE));
        assertEquals(ThemeMode.LIGHT, store.loadThemeMode());

        viewModel.dispose();
    }

    @Test
    @DisplayName("updateThemeMode routes theme changes through an injected theme service")
    void updateThemeModeRoutesThroughInjectedThemeService() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        User currentUser = createUser("Morgan");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        RecordingThemeService themeService = new RecordingThemeService(ThemeMode.DARK);
        PreferencesViewModel viewModel = new PreferencesViewModel(
                new StorageUiUserStore(users), null, themeService, AppConfig.defaults(), session, TEST_DISPATCHER);

        viewModel.initialize();
        waitForAsyncWork();
        themeService.resetCalls();

        viewModel.updateThemeMode(ThemeMode.LIGHT);
        waitForAsyncWork();

        assertEquals(ThemeMode.LIGHT, viewModel.themeModeProperty().get());
        assertEquals(ThemeMode.LIGHT, themeService.lastAppliedTheme());
        assertEquals(1, themeService.setCalls());

        viewModel.dispose();
    }

    private static void waitForAsyncWork() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Thread.ofVirtual().start(() -> {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(150));
            latch.countDown();
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for async work");
        }
    }

    private static User createUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(28));
        user.setGender(Gender.FEMALE);
        user.setInterestedIn(EnumSet.of(Gender.MALE, Gender.FEMALE));
        user.setAgeRange(21, 45, 18, 120);
        user.setMaxDistanceKm(50, AppConfig.defaults().matching().maxDistanceKm());
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setBio("Preference test user");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    private static final class RecordingThemeService extends UiThemeService {
        private ThemeMode currentThemeMode;
        private ThemeMode lastAppliedTheme;
        private int setCalls;

        private RecordingThemeService(ThemeMode initialThemeMode) {
            this.currentThemeMode = initialThemeMode;
        }

        @Override
        public ThemeMode loadThemeMode() {
            return currentThemeMode;
        }

        @Override
        public void setThemeMode(ThemeMode themeMode) {
            this.currentThemeMode = themeMode;
            this.lastAppliedTheme = themeMode;
            this.setCalls++;
        }

        private ThemeMode lastAppliedTheme() {
            return lastAppliedTheme;
        }

        private int setCalls() {
            return setCalls;
        }

        private void resetCalls() {
            this.lastAppliedTheme = null;
            this.setCalls = 0;
        }
    }
}
