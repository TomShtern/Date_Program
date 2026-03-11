package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.testutil.TestStorages;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("LoginViewModel behavior")
class LoginViewModelTest {

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
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("createUser builds an active profile and refreshes the user list")
    void createUserBuildsActiveProfileAndRefreshesUserList() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        LoginViewModel viewModel = new LoginViewModel(
                new UiDataAdapters.StorageUiUserStore(users), AppConfig.defaults(), AppSession.getInstance());

        assertTrue(waitUntil(() -> viewModel.getUsers().isEmpty(), 5000));

        User created = viewModel.createUser("Taylor", 29, Gender.OTHER, Gender.FEMALE);

        assertNotNull(created);
        assertTrue(waitUntil(() -> viewModel.getUsers().size() == 1, 5000));
        assertEquals(User.UserState.ACTIVE, created.getState());
        assertEquals("Taylor", viewModel.getUsers().getFirst().getName());

        viewModel.dispose();
    }

    @Test
    @DisplayName("filter text narrows visible users by name")
    void filterTextNarrowsVisibleUsersByName() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        users.save(createUser("Alex", Gender.MALE));
        users.save(createUser("Blair", Gender.FEMALE));

        LoginViewModel viewModel = new LoginViewModel(
                new UiDataAdapters.StorageUiUserStore(users), AppConfig.defaults(), AppSession.getInstance());

        assertTrue(waitUntil(() -> viewModel.getUsers().size() == 2, 5000));
        viewModel.filterTextProperty().set("blair");

        assertTrue(waitUntil(() -> viewModel.getFilteredUsers().size() == 1, 5000));
        assertEquals("Blair", viewModel.getFilteredUsers().getFirst().getName());

        viewModel.dispose();
    }

    private static User createUser(String name, Gender gender) {
        User user = new User(java.util.UUID.randomUUID(), name);
        user.setBirthDate(datingapp.core.AppClock.today().minusYears(25));
        user.setGender(gender);
        user.setInterestedIn(java.util.EnumSet.of(Gender.OTHER));
        user.setAgeRange(18, 60, 18, 120);
        user.setMaxDistanceKm(50, AppConfig.defaults().matching().maxDistanceKm());
        user.setLocation(40.7128, -74.0060);
        user.setBio("Existing user");
        user.setPhotoUrls(java.util.List.of("placeholder://default-avatar"));
        user.setPacePreferences(new datingapp.core.profile.MatchPreferences.PacePreferences(
                datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency.OFTEN,
                datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate.FEW_DAYS,
                datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference.DEPENDS_ON_VIBE));
        user.setDealbreakers(datingapp.core.profile.MatchPreferences.Dealbreakers.none());
        user.activate();
        return user;
    }

    private static boolean waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadlineNanos) {
            CountDownLatchHelper.waitForFxEvents();
            if (condition.getAsBoolean()) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        CountDownLatchHelper.waitForFxEvents();
        return condition.getAsBoolean();
    }

    private static final class CountDownLatchHelper {
        private CountDownLatchHelper() {}

        private static void waitForFxEvents() throws InterruptedException {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            Platform.runLater(latch::countDown);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for FX thread");
            }
        }
    }
}
