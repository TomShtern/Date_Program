package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.async.UiThreadDispatcher;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
@DisplayName("ProfileViewModel photo lifecycle")
class ProfileViewModelTest {

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

    private static final String SAMPLE_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4////fwAJ+wP9KobjigAAAABJRU5ErkJggg==";

    private final AppSession session = AppSession.getInstance();
    private String originalUserHome;

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException _) {
            // JavaFX already initialized
        }
    }

    @AfterEach
    void tearDown() {
        session.reset();
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    @DisplayName("save, set-primary, and delete photo update the managed gallery")
    void saveSetPrimaryAndDeletePhotoUpdateManagedGallery() throws Exception {
        originalUserHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("datingapp-profile-home");
        System.setProperty("user.home", tempHome.toString());

        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);

        User currentUser = createActiveUser("PhotoUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = new ProfileViewModel(
                new UiDataAdapters.StorageUiUserStore(users),
                profileService,
                (datingapp.app.usecase.profile.ProfileUseCases) null,
                config,
                session,
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy());
        viewModel.loadCurrentUser();

        Path firstPhoto = createTempImageFile("photo-one");
        Path secondPhoto = createTempImageFile("photo-two");

        viewModel.savePhoto(firstPhoto.toFile());
        assertTrue(waitUntil(() -> viewModel.getPhotoUrls().size() == 1, 5000));
        String firstManagedUri = viewModel.getPhotoUrls().getFirst();
        assertTrue(Files.exists(Path.of(URI.create(firstManagedUri))));

        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        viewModel.savePhoto(secondPhoto.toFile());
        assertTrue(waitUntil(() -> viewModel.getPhotoUrls().size() == 2, 5000));
        String secondManagedUri = viewModel.getPhotoUrls().get(1);
        assertTrue(Files.exists(Path.of(URI.create(secondManagedUri))));

        viewModel.setPrimaryPhoto(1);
        assertTrue(waitUntil(
                () -> !viewModel.getPhotoUrls().isEmpty()
                        && secondManagedUri.equals(viewModel.getPhotoUrls().getFirst()),
                5000));
        assertEquals(secondManagedUri, viewModel.getPhotoUrls().getFirst());

        viewModel.deletePhoto(0);
        assertTrue(waitUntil(() -> viewModel.getPhotoUrls().size() == 1, 5000));
        assertTrue(waitUntil(
                () -> !viewModel.getPhotoUrls().isEmpty()
                        && firstManagedUri.equals(viewModel.getPhotoUrls().getFirst()),
                5000));
        assertEquals(firstManagedUri, viewModel.getPhotoUrls().getFirst());
        assertTrue(Files.exists(Path.of(URI.create(firstManagedUri))));
        assertTrue(Files.notExists(Path.of(URI.create(secondManagedUri))));

        viewModel.dispose();
    }

    @Test
    @DisplayName("saving a third photo keeps the primary photo and replaces the secondary slot")
    void replacePhotoMaintainsGallerySizeAtSix() throws Exception {
        originalUserHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("datingapp-profile-home");
        System.setProperty("user.home", tempHome.toString());

        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);

        User currentUser = createActiveUser("PhotoUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = new ProfileViewModel(
                new UiDataAdapters.StorageUiUserStore(users),
                profileService,
                (datingapp.app.usecase.profile.ProfileUseCases) null,
                config,
                session,
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy());
        viewModel.loadCurrentUser();

        List<Path> sourcePhotos = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sourcePhotos.add(createTempImageFile("photo-" + i));
        }
        for (Path sourcePhoto : sourcePhotos) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            viewModel.savePhoto(sourcePhoto.toFile());
        }

        assertTrue(waitUntil(() -> viewModel.getPhotoUrls().size() == 6, 5000));
        assertEquals(6, viewModel.photoCountProperty().get());

        Path replacementPhoto = createTempImageFile("replacement-photo");
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        viewModel.replacePhoto(2, replacementPhoto.toFile());

        assertTrue(waitUntil(
                () -> viewModel.getPhotoUrls().size() == 6
                        && viewModel.getPhotoUrls().size() > 2
                        && Files.exists(
                                Path.of(URI.create(viewModel.getPhotoUrls().get(2)))),
                5000));

        String replacementUri = viewModel.getPhotoUrls().get(2);
        assertEquals(6, viewModel.photoCountProperty().get());
        assertTrue(Files.exists(Path.of(URI.create(replacementUri))));

        viewModel.dispose();
    }

    @Test
    @DisplayName("saveAsync persists edits only after a successful background save")
    void saveAsyncPersistsEditsOnlyAfterSuccess() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);

        User currentUser = createActiveUser("SaveUser");
        currentUser.setBio("Original bio");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = new ProfileViewModel(
                new UiDataAdapters.StorageUiUserStore(users),
                profileService,
                (datingapp.app.usecase.profile.ProfileUseCases) null,
                config,
                session,
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy());
        viewModel.loadCurrentUser();
        viewModel.bioProperty().set("Updated bio");
        viewModel.setLocationCoordinates(12.3456, -45.6789);

        AtomicBoolean saveResult = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        viewModel.saveAsync(success -> {
            saveResult.set(success);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(saveResult.get());
        assertFalse(viewModel.savingProperty().get());
        assertEquals("Updated bio", session.getCurrentUser().getBio());
        assertEquals("12.3456, -45.6789", viewModel.locationDisplayProperty().get());

        viewModel.dispose();
    }

    @Test
    @DisplayName("saveAsync failure keeps the original session user unchanged")
    void saveAsyncFailureKeepsOriginalSessionUserUnchanged() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);

        User currentUser = createActiveUser("SaveFailureUser");
        currentUser.setBio("Original bio");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        UiDataAdapters.UiUserStore failingStore = new UiDataAdapters.UiUserStore() {
            @Override
            public java.util.List<User> findAll() {
                return users.findAll();
            }

            @Override
            public void save(User user) {
                throw new IllegalStateException("Simulated save failure");
            }

            @Override
            public java.util.Map<UUID, User> findByIds(java.util.Set<UUID> ids) {
                return users.findByIds(ids);
            }
        };

        ProfileViewModel viewModel = new ProfileViewModel(
                failingStore,
                profileService,
                (datingapp.app.usecase.profile.ProfileUseCases) null,
                config,
                session,
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy());
        AtomicReference<String> errorMessage = new AtomicReference<>();
        viewModel.setErrorHandler(errorMessage::set);
        viewModel.loadCurrentUser();
        viewModel.bioProperty().set("Should not persist");

        AtomicBoolean saveResult = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(1);
        viewModel.saveAsync(success -> {
            saveResult.set(success);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertFalse(saveResult.get());
        assertFalse(viewModel.savingProperty().get());
        assertEquals("Original bio", session.getCurrentUser().getBio());
        assertTrue(errorMessage.get().contains("Failed to save profile"));

        viewModel.dispose();
    }

    @Test
    @DisplayName("supported saved coordinates are shown with a human friendly label")
    void supportedSavedCoordinatesUseHumanFriendlyLabel() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);

        User currentUser = createActiveUser("LocationUser");
        currentUser.setLocation(32.0853, 34.7818);
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = new ProfileViewModel(
                new UiDataAdapters.StorageUiUserStore(users),
                profileService,
                (datingapp.app.usecase.profile.ProfileUseCases) null,
                config,
                session,
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy());

        viewModel.loadCurrentUser();

        assertEquals(
                "Tel Aviv, Tel Aviv District",
                viewModel.locationDisplayProperty().get());

        viewModel.dispose();
    }

    @Test
    @DisplayName("oversized photos are rejected before any upload work starts")
    void oversizedPhotosAreRejectedBeforeUpload() throws Exception {
        originalUserHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("datingapp-profile-home");
        System.setProperty("user.home", tempHome.toString());

        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);

        User currentUser = createActiveUser("LargePhotoUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = new ProfileViewModel(
                new UiDataAdapters.StorageUiUserStore(users),
                profileService,
                (datingapp.app.usecase.profile.ProfileUseCases) null,
                config,
                session,
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy());

        Path oversizedPhoto = Files.createTempFile("oversized-photo", ".jpg");
        Files.write(oversizedPhoto, new byte[(5 * 1024 * 1024) + 1]);

        viewModel.savePhoto(oversizedPhoto.toFile());

        assertTrue(viewModel.getPhotoUrls().isEmpty());
        assertEquals(0, viewModel.photoCountProperty().get());

        viewModel.dispose();
    }

    @Test
    @DisplayName("toggleInterest honors the configured maximum interests limit")
    void toggleInterestHonorsConfiguredMaximumInterests() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        AppConfig config = AppConfig.builder().maxInterests(1).build();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);

        User currentUser = createActiveUser("InterestLimitUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = new ProfileViewModel(
                new UiDataAdapters.StorageUiUserStore(users),
                profileService,
                (datingapp.app.usecase.profile.ProfileUseCases) null,
                config,
                session,
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy());

        assertTrue(viewModel.toggleInterest(datingapp.core.profile.MatchPreferences.Interest.values()[0]));
        assertFalse(viewModel.toggleInterest(datingapp.core.profile.MatchPreferences.Interest.values()[1]));
        assertEquals(1, viewModel.getSelectedInterests().size());

        viewModel.dispose();
    }

    @Test
    @DisplayName("getMaxBioLength returns the configured bio length limit")
    void getMaxBioLengthReturnsConfiguredLimit() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        AppConfig config = AppConfig.builder().maxBioLength(320).build();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);

        User currentUser = createActiveUser("BioLimitUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = new ProfileViewModel(
                new UiDataAdapters.StorageUiUserStore(users),
                profileService,
                (datingapp.app.usecase.profile.ProfileUseCases) null,
                config,
                session,
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy());

        assertEquals(320, viewModel.getMaxBioLength());

        viewModel.dispose();
    }

    private static Path createTempImageFile(String prefix) throws Exception {
        Path file = Files.createTempFile(prefix, ".png");
        Files.write(file, java.util.Base64.getDecoder().decode(SAMPLE_PNG_BASE64));
        return file;
    }

    private static boolean waitUntil(BooleanSupplier condition, long timeoutMillis) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        return condition.getAsBoolean();
    }

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(25));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.setAgeRange(18, 60, 18, 120);
        user.setMaxDistanceKm(50, 500);
        user.setLocation(40.7128, -74.0060);
        user.setBio("Photo test user");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        return user;
    }
}
