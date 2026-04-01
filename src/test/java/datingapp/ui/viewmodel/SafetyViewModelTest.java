package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.workflow.ProfileActivationPolicy;
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

    private static final UiThreadDispatcher TEST_DISPATCHER = datingapp.ui.JavaFxTestSupport.immediateUiDispatcher();

    private SafetyViewModel viewModel;

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

        viewModel = new SafetyViewModel(trustSafetyService, AppSession.getInstance(), TEST_DISPATCHER);
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

        viewModel = new SafetyViewModel(trustSafetyService, AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(waitUntil(viewModel.getBlockedUsers()::isEmpty, 5000));
        viewModel.dispose();
    }

    @Test
    @DisplayName("verification flow persists generated code and marks the user verified")
    void verificationFlowPersistsAndVerifiesUser() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Communications communications = new TestStorages.Communications();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        AppConfig config = AppConfig.defaults();

        User currentUser = createUser("Veronica", Gender.FEMALE, EnumSet.of(Gender.MALE));
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();
        ProfileUseCases profileUseCases = new ProfileUseCases(
                users,
                new ProfileService(users),
                new ValidationService(config),
                new ActivityMetricsService(interactions, trustSafetyStorage, analytics, config),
                TestAchievementService.empty(),
                config,
                new ProfileActivationPolicy(),
                new InProcessAppEventBus());

        viewModel = new SafetyViewModel(trustSafetyService, profileUseCases, AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.initialize();
        viewModel.verificationMethodProperty().set(VerificationMethod.EMAIL);
        viewModel.verificationContactProperty().set("verified@example.com");

        viewModel.startVerification();

        User afterStart = AppSession.getInstance().getCurrentUser();
        String generatedCode = afterStart.getVerificationCode();
        assertTrue(generatedCode != null && generatedCode.length() == 6);
        assertTrue(viewModel.statusMessageProperty().get().contains(generatedCode));

        viewModel.verificationCodeProperty().set(generatedCode);
        viewModel.confirmVerification();

        assertTrue(AppSession.getInstance().getCurrentUser().isVerified());
        assertTrue(viewModel.statusMessageProperty().get().contains("verified"));
    }

    @Test
    @DisplayName("delete account soft-deletes the current user and resets the session")
    void deleteAccountSoftDeletesAndSignsOut() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        AppConfig config = AppConfig.defaults();

        User currentUser = createUser("DeleteMe", Gender.OTHER, EnumSet.of(Gender.OTHER));
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        ProfileUseCases profileUseCases = new ProfileUseCases(
                users,
                new ProfileService(users),
                new ValidationService(config),
                new ActivityMetricsService(interactions, trustSafetyStorage, analytics, config),
                TestAchievementService.empty(),
                config,
                new ProfileActivationPolicy(),
                new InProcessAppEventBus());
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, new TestStorages.Communications())
                .build();

        viewModel = new SafetyViewModel(trustSafetyService, profileUseCases, AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.deleteCurrentAccount();

        assertTrue(viewModel.accountDeletedProperty().get());
        assertTrue(users.get(currentUser.getId()).orElseThrow().isDeleted());
        assertNull(AppSession.getInstance().getCurrentUser());
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
