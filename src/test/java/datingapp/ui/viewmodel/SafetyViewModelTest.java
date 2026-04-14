package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.profile.ProfileInsightsUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.VerificationUseCases;
import datingapp.app.usecase.social.SocialUseCases;
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
import java.util.concurrent.atomic.AtomicReference;
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
    private static final String SHOW_VERIFICATION_CODE_PROPERTY = "datingapp.showVerificationCode";

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
        System.clearProperty(SHOW_VERIFICATION_CODE_PROPERTY);
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
        assertFalse(viewModel.statusMessageProperty().get().contains("[SIMULATED]"));

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
    void verificationFlowPersistsAndVerifiesUser() throws InterruptedException {
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
                new ProfileMutationUseCases(
                        users,
                        new ValidationService(config),
                        TestAchievementService.empty(),
                        config,
                        new ProfileActivationPolicy(),
                        new InProcessAppEventBus()),
                new ProfileNotesUseCases(users, new ValidationService(config), config, new InProcessAppEventBus()),
                new ProfileInsightsUseCases(
                        TestAchievementService.empty(),
                        new ActivityMetricsService(interactions, trustSafetyStorage, analytics, config)));
        SocialUseCases socialUseCases = new SocialUseCases(
                new datingapp.core.connection.ConnectionService(config, communications, interactions, users),
                trustSafetyService,
                communications,
                new InProcessAppEventBus());
        VerificationUseCases verificationUseCases = new VerificationUseCases(users, trustSafetyService);

        viewModel = new SafetyViewModel(
                socialUseCases,
                verificationUseCases,
                profileUseCases.getProfileMutationUseCases(),
                AppSession.getInstance(),
                TEST_DISPATCHER);
        viewModel.initialize();
        viewModel.verificationMethodProperty().set(VerificationMethod.EMAIL);
        viewModel.verificationContactProperty().set("verified@example.com");

        viewModel.startVerification();

        assertTrue(waitUntil(
                () -> {
                    User startedUser = AppSession.getInstance().getCurrentUser();
                    String status = viewModel.statusMessageProperty().get();
                    String code = startedUser != null ? startedUser.getVerificationCode() : null;
                    return code != null
                            && code.length() == 6
                            && status != null
                            && status.contains("Verification code generated")
                            && status.contains("configured verification channel");
                },
                5000));

        User afterStart = AppSession.getInstance().getCurrentUser();
        String generatedCode = afterStart.getVerificationCode();
        assertTrue(generatedCode != null && generatedCode.length() == 6);
        assertFalse(viewModel.statusMessageProperty().get().contains(generatedCode));
        assertFalse(viewModel.statusMessageProperty().get().contains("[SIMULATED]"));

        viewModel.verificationCodeProperty().set(generatedCode);
        viewModel.confirmVerification();

        assertTrue(waitUntil(
                () -> {
                    User verifiedUser = AppSession.getInstance().getCurrentUser();
                    String status = viewModel.statusMessageProperty().get();
                    return verifiedUser != null
                            && verifiedUser.isVerified()
                            && status != null
                            && status.contains("verified");
                },
                5000));

        assertTrue(AppSession.getInstance().getCurrentUser().isVerified());
        assertTrue(viewModel.statusMessageProperty().get().contains("verified"));
        assertFalse(viewModel.statusMessageProperty().get().contains("[SIMULATED]"));
    }

    @Test
    @DisplayName("start verification includes the generated code only when explicitly enabled")
    void startVerificationIncludesGeneratedCodeWhenExplicitlyEnabled() throws InterruptedException {
        System.setProperty(SHOW_VERIFICATION_CODE_PROPERTY, "true");

        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createUser("DevVerify", Gender.FEMALE, EnumSet.of(Gender.MALE));
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();
        VerificationUseCases verificationUseCases = new VerificationUseCases(users, trustSafetyService);

        viewModel = new SafetyViewModel(
                SocialUseCases.forTrustSafetyOnly(trustSafetyService),
                verificationUseCases,
                null,
                AppSession.getInstance(),
                TEST_DISPATCHER);
        viewModel.initialize();
        viewModel.verificationMethodProperty().set(VerificationMethod.EMAIL);
        viewModel.verificationContactProperty().set("dev-verify@example.com");

        viewModel.startVerification();

        assertTrue(waitUntil(
                () -> {
                    User startedUser = AppSession.getInstance().getCurrentUser();
                    String status = viewModel.statusMessageProperty().get();
                    String code = startedUser != null ? startedUser.getVerificationCode() : null;
                    return code != null && code.length() == 6 && status != null && status.contains(code);
                },
                5000));
        assertTrue(viewModel.statusMessageProperty().get().contains("Local/dev code"));
    }

    @Test
    @DisplayName("start verification does not block caller while verification work is running")
    void startVerificationDoesNotBlockCallerWhileVerificationWorkIsRunning() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createUser("AsyncStart", Gender.FEMALE, EnumSet.of(Gender.MALE));
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();
        CountDownLatch startStarted = new CountDownLatch(1);
        CountDownLatch releaseStart = new CountDownLatch(1);
        VerificationUseCases verificationUseCases =
                new BlockingVerificationUseCases(users, trustSafetyService, startStarted, releaseStart, null, null);

        viewModel = new SafetyViewModel(
                SocialUseCases.forTrustSafetyOnly(trustSafetyService),
                verificationUseCases,
                null,
                AppSession.getInstance(),
                TEST_DISPATCHER);
        viewModel.initialize();
        viewModel.verificationMethodProperty().set(VerificationMethod.EMAIL);
        viewModel.verificationContactProperty().set("async-start@example.com");

        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                viewModel.startVerification();
            } catch (Throwable throwable) {
                callerFailure.set(throwable);
            }
        });

        assertTrue(startStarted.await(5, TimeUnit.SECONDS));
        caller.join(250);

        assertNull(callerFailure.get());
        assertFalse(caller.isAlive(), "startVerification() should return promptly and leave work to asyncScope");

        releaseStart.countDown();
        assertTrue(
                waitUntil(() -> viewModel.statusMessageProperty().get().contains("Verification code generated"), 5000));
    }

    @Test
    @DisplayName("confirm verification does not block caller while confirmation work is running")
    void confirmVerificationDoesNotBlockCallerWhileConfirmationWorkIsRunning() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createUser("AsyncConfirm", Gender.FEMALE, EnumSet.of(Gender.MALE));
        currentUser.setEmail("async-confirm@example.com");
        currentUser.startVerification(VerificationMethod.EMAIL, "123456");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();
        CountDownLatch confirmStarted = new CountDownLatch(1);
        CountDownLatch releaseConfirm = new CountDownLatch(1);
        VerificationUseCases verificationUseCases =
                new BlockingVerificationUseCases(users, trustSafetyService, null, null, confirmStarted, releaseConfirm);

        viewModel = new SafetyViewModel(
                SocialUseCases.forTrustSafetyOnly(trustSafetyService),
                verificationUseCases,
                null,
                AppSession.getInstance(),
                TEST_DISPATCHER);
        viewModel.initialize();
        viewModel.verificationMethodProperty().set(VerificationMethod.EMAIL);
        viewModel.verificationContactProperty().set("async-confirm@example.com");
        viewModel.verificationCodeProperty().set("123456");

        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                viewModel.confirmVerification();
            } catch (Throwable throwable) {
                callerFailure.set(throwable);
            }
        });

        assertTrue(confirmStarted.await(5, TimeUnit.SECONDS));
        caller.join(250);

        assertNull(callerFailure.get());
        assertFalse(caller.isAlive(), "confirmVerification() should return promptly and leave work to asyncScope");

        releaseConfirm.countDown();
        assertTrue(waitUntil(
                () -> {
                    User verifiedUser = AppSession.getInstance().getCurrentUser();
                    String status = viewModel.statusMessageProperty().get();
                    return verifiedUser != null
                            && verifiedUser.isVerified()
                            && status != null
                            && status.contains("verified");
                },
                5000));
        assertTrue(viewModel.statusMessageProperty().get().contains("verified"));
    }

    @Test
    @DisplayName("delete account soft-deletes the current user and resets the session")
    void deleteAccountSoftDeletesAndSignsOut() throws InterruptedException {
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
                new ProfileMutationUseCases(
                        users,
                        new ValidationService(config),
                        TestAchievementService.empty(),
                        config,
                        new ProfileActivationPolicy(),
                        new InProcessAppEventBus()),
                new ProfileNotesUseCases(users, new ValidationService(config), config, new InProcessAppEventBus()),
                new ProfileInsightsUseCases(
                        TestAchievementService.empty(),
                        new ActivityMetricsService(interactions, trustSafetyStorage, analytics, config)));
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, new TestStorages.Communications())
                .build();

        viewModel = new SafetyViewModel(trustSafetyService, profileUseCases, AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.deleteCurrentAccount();

        assertTrue(waitUntil(
                () -> viewModel.accountDeletedProperty().get()
                        && AppSession.getInstance().getCurrentUser() == null,
                5000));

        assertTrue(viewModel.accountDeletedProperty().get());
        assertTrue(users.get(currentUser.getId()).orElseThrow().isDeleted());
        assertNull(AppSession.getInstance().getCurrentUser());
        assertFalse(viewModel.statusMessageProperty().get().contains("[SIMULATED]"));
    }

    @Test
    @DisplayName("delete account does not block caller while deletion work is running")
    void deleteAccountDoesNotBlockCallerWhileDeletionWorkIsRunning() throws Exception {
        BlockingUsers users = new BlockingUsers();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        AppConfig config = AppConfig.defaults();

        User currentUser = createUser("AsyncDelete", Gender.OTHER, EnumSet.of(Gender.OTHER));
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        users.armDeleteGate(deleteStarted, releaseDelete);

        ProfileMutationUseCases profileMutationUseCases = new ProfileMutationUseCases(
                users,
                new ValidationService(config),
                TestAchievementService.empty(),
                config,
                new ProfileActivationPolicy(),
                new InProcessAppEventBus());
        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, new TestStorages.Communications())
                .build();

        viewModel = new SafetyViewModel(
                SocialUseCases.forTrustSafetyOnly(trustSafetyService),
                null,
                profileMutationUseCases,
                AppSession.getInstance(),
                TEST_DISPATCHER);

        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                viewModel.deleteCurrentAccount();
            } catch (Throwable throwable) {
                callerFailure.set(throwable);
            }
        });

        assertTrue(deleteStarted.await(5, TimeUnit.SECONDS));
        caller.join(250);

        assertNull(callerFailure.get());
        assertFalse(caller.isAlive(), "deleteCurrentAccount() should return promptly and leave work to asyncScope");

        releaseDelete.countDown();
        assertTrue(waitUntil(viewModel.accountDeletedProperty()::get, 5000));
        assertTrue(users.get(currentUser.getId()).orElseThrow().isDeleted());
        assertNull(AppSession.getInstance().getCurrentUser());
    }

    @Test
    @DisplayName("legacy safety constructor does not bypass VerificationUseCases")
    void legacySafetyConstructorDoesNotBypassVerificationUseCases() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createUser("LegacyVerifier", Gender.FEMALE, EnumSet.of(Gender.MALE));
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();
        AtomicReference<String> errorMessage = new AtomicReference<>();

        viewModel = new SafetyViewModel(trustSafetyService, AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.setErrorHandler(errorMessage::set);
        viewModel.verificationMethodProperty().set(VerificationMethod.EMAIL);
        viewModel.verificationContactProperty().set("legacy@example.com");

        viewModel.startVerification();

        assertEquals("Profile verification is unavailable right now.", errorMessage.get());
        User stored = users.get(currentUser.getId()).orElseThrow();
        assertNull(stored.getVerificationCode());
        assertNull(stored.getEmail());
    }

    @Test
    @DisplayName("start verification surfaces thrown use-case failures through the error handler")
    void startVerificationSurfacesThrownUseCaseFailures() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createUser("ThrownStart", Gender.FEMALE, EnumSet.of(Gender.MALE));
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();
        AtomicReference<String> errorMessage = new AtomicReference<>();

        viewModel = new SafetyViewModel(
                SocialUseCases.forTrustSafetyOnly(trustSafetyService),
                new ThrowingVerificationUseCases(users, trustSafetyService, true, false),
                null,
                AppSession.getInstance(),
                TEST_DISPATCHER);
        viewModel.setErrorHandler(errorMessage::set);
        viewModel.verificationMethodProperty().set(VerificationMethod.EMAIL);
        viewModel.verificationContactProperty().set("thrown-start@example.com");

        viewModel.startVerification();

        assertTrue(waitUntil(() -> errorMessage.get() != null, 5000));
        assertTrue(errorMessage.get().contains("Could not start verification"));
        assertTrue(errorMessage.get().contains("synthetic start failure"));
    }

    @Test
    @DisplayName("confirm verification surfaces thrown use-case failures through the error handler")
    void confirmVerificationSurfacesThrownUseCaseFailures() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User currentUser = createUser("ThrownConfirm", Gender.FEMALE, EnumSet.of(Gender.MALE));
        currentUser.startVerification(VerificationMethod.EMAIL, "123456");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();
        AtomicReference<String> errorMessage = new AtomicReference<>();

        viewModel = new SafetyViewModel(
                SocialUseCases.forTrustSafetyOnly(trustSafetyService),
                new ThrowingVerificationUseCases(users, trustSafetyService, false, true),
                null,
                AppSession.getInstance(),
                TEST_DISPATCHER);
        viewModel.setErrorHandler(errorMessage::set);
        viewModel.verificationCodeProperty().set("123456");

        viewModel.confirmVerification();

        assertTrue(waitUntil(() -> errorMessage.get() != null, 5000));
        assertTrue(errorMessage.get().contains("Could not confirm verification"));
        assertTrue(errorMessage.get().contains("synthetic confirm failure"));
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

    private static void awaitGate(CountDownLatch started, CountDownLatch release, String operationName) {
        if (started == null || release == null) {
            return;
        }
        started.countDown();
        try {
            assertTrue(release.await(5, TimeUnit.SECONDS), operationName + " gate was never released");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting to release " + operationName, exception);
        }
    }

    private static final class BlockingVerificationUseCases extends VerificationUseCases {
        private final CountDownLatch startStarted;
        private final CountDownLatch releaseStart;
        private final CountDownLatch confirmStarted;
        private final CountDownLatch releaseConfirm;

        private BlockingVerificationUseCases(
                TestStorages.Users users,
                TrustSafetyService trustSafetyService,
                CountDownLatch startStarted,
                CountDownLatch releaseStart,
                CountDownLatch confirmStarted,
                CountDownLatch releaseConfirm) {
            super(users, trustSafetyService);
            this.startStarted = startStarted;
            this.releaseStart = releaseStart;
            this.confirmStarted = confirmStarted;
            this.releaseConfirm = releaseConfirm;
        }

        @Override
        public UseCaseResult<StartVerificationResult> startVerification(StartVerificationCommand command) {
            awaitGate(startStarted, releaseStart, "start verification");
            return super.startVerification(command);
        }

        @Override
        public UseCaseResult<ConfirmVerificationResult> confirmVerification(ConfirmVerificationCommand command) {
            awaitGate(confirmStarted, releaseConfirm, "confirm verification");
            return super.confirmVerification(command);
        }
    }

    private static final class ThrowingVerificationUseCases extends VerificationUseCases {
        private final boolean throwOnStart;
        private final boolean throwOnConfirm;

        private ThrowingVerificationUseCases(
                TestStorages.Users users,
                TrustSafetyService trustSafetyService,
                boolean throwOnStart,
                boolean throwOnConfirm) {
            super(users, trustSafetyService);
            this.throwOnStart = throwOnStart;
            this.throwOnConfirm = throwOnConfirm;
        }

        @Override
        public UseCaseResult<StartVerificationResult> startVerification(StartVerificationCommand command) {
            if (throwOnStart) {
                throw new IllegalStateException("synthetic start failure");
            }
            return super.startVerification(command);
        }

        @Override
        public UseCaseResult<ConfirmVerificationResult> confirmVerification(ConfirmVerificationCommand command) {
            if (throwOnConfirm) {
                throw new IllegalStateException("synthetic confirm failure");
            }
            return super.confirmVerification(command);
        }
    }

    private static final class BlockingUsers extends TestStorages.Users {
        private CountDownLatch deleteStarted;
        private CountDownLatch releaseDelete;

        private void armDeleteGate(CountDownLatch deleteStarted, CountDownLatch releaseDelete) {
            this.deleteStarted = deleteStarted;
            this.releaseDelete = releaseDelete;
        }

        @Override
        public void save(User user) {
            if (user != null && user.isDeleted() && deleteStarted != null && releaseDelete != null) {
                awaitGate(deleteStarted, releaseDelete, "delete account");
            }
            super.save(user);
        }
    }
}
