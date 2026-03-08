package datingapp.ui.viewmodel;

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
import datingapp.ui.async.UiThreadDispatcher;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.UUID;
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
        assertTrue(
                waitUntil(() -> secondManagedUri.equals(viewModel.getPhotoUrls().getFirst()), 5000));
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
    void saveThirdPhotoPreservesPrimaryAndReplacesSecondarySlot() throws Exception {
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
        Path thirdPhoto = createTempImageFile("photo-three");

        viewModel.savePhoto(firstPhoto.toFile());
        assertTrue(waitUntil(() -> viewModel.getPhotoUrls().size() == 1, 5000));
        String firstManagedUri = viewModel.getPhotoUrls().getFirst();

        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        viewModel.savePhoto(secondPhoto.toFile());
        assertTrue(waitUntil(() -> viewModel.getPhotoUrls().size() == 2, 5000));
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        viewModel.savePhoto(thirdPhoto.toFile());
        assertTrue(waitUntil(() -> viewModel.getPhotoUrls().size() == 2, 5000));
        assertTrue(waitUntil(() -> viewModel.getPhotoUrls().contains(firstManagedUri), 5000));

        assertTrue(Files.exists(Path.of(URI.create(firstManagedUri))));
        assertEquals(2, viewModel.getPhotoUrls().size());
        assertEquals(firstManagedUri, viewModel.getPhotoUrls().getFirst());

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
