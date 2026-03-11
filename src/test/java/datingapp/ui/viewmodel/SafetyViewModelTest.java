package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.async.UiThreadDispatcher;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
@DisplayName("SafetyViewModel blocked user management")
class SafetyViewModelTest {

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

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException _) {
            // already initialized
        }
    }

    @AfterEach
    void tearDown() {
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("initialize loads blocked users and unblock removes them")
    void initializeLoadsAndUnblockRemovesUsers() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User blocker = createUser("Avery", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User blocked = createUser("Blake", Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(blocker);
        users.save(blocked);
        AppSession.getInstance().setCurrentUser(blocker);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();
        trustSafetyService.block(blocker.getId(), blocked.getId());

        SafetyViewModel viewModel = new SafetyViewModel(trustSafetyService, AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(waitUntil(() -> viewModel.getBlockedUsers().size() == 1, 5000));
        assertEquals("Blake", viewModel.getBlockedUsers().getFirst().name());

        viewModel.unblockUser(blocked.getId());

        assertTrue(waitUntil(viewModel.getBlockedUsers()::isEmpty, 5000));
        assertTrue(viewModel.statusMessageProperty().get().contains("unblocked"));

        viewModel.dispose();
    }

    @Test
    @DisplayName("initialize leaves blocked list empty when nothing is blocked")
    void initializeLeavesEmptyStateWhenNothingBlocked() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User blocker = createUser("Casey", Gender.FEMALE, EnumSet.of(Gender.MALE));
        users.save(blocker);
        AppSession.getInstance().setCurrentUser(blocker);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();

        SafetyViewModel viewModel = new SafetyViewModel(trustSafetyService, AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(waitUntil(viewModel.getBlockedUsers()::isEmpty, 5000));
        viewModel.dispose();
    }

    private static boolean waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            waitForFxEvents();
            if (condition.getAsBoolean()) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        waitForFxEvents();
        return condition.getAsBoolean();
    }

    private static void waitForFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for FX events");
        }
    }

    private static User createUser(String name, Gender gender, EnumSet<Gender> interestedIn) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(28));
        user.setGender(gender);
        user.setInterestedIn(interestedIn);
        user.setAgeRange(21, 45, 18, 120);
        user.setMaxDistanceKm(50, AppConfig.defaults().matching().maxDistanceKm());
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setBio("Safety test user");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }
}
