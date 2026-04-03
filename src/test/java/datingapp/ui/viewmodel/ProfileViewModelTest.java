package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.ProfileViewModel.SaveOutcome;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.List;
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
@DisplayName("ProfileViewModel photo lifecycle")
class ProfileViewModelTest {

    private static final String FILE_URLS_ENABLED_PROPERTY = "datingapp.allowFileUrls";
    private static final String FILE_URL_ROOT_PROPERTY = "datingapp.allowedFileUrlRoot";

    private static final UiThreadDispatcher TEST_DISPATCHER = datingapp.ui.JavaFxTestSupport.immediateUiDispatcher();

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
        System.clearProperty(FILE_URLS_ENABLED_PROPERTY);
        System.clearProperty(FILE_URL_ROOT_PROPERTY);
    }

    @Test
    @DisplayName("save, set-primary, and delete photo update the managed gallery")
    void saveSetPrimaryAndDeletePhotoUpdateManagedGallery() throws Exception {
        originalUserHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("datingapp-profile-home");
        System.setProperty("user.home", tempHome.toString());
        System.setProperty(FILE_URLS_ENABLED_PROPERTY, "true");
        System.setProperty(FILE_URL_ROOT_PROPERTY, tempHome.toString());

        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActiveUser("PhotoUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);
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
                () -> {
                    List<String> urls = snapshotPhotoUrls(viewModel);
                    return !urls.isEmpty() && secondManagedUri.equals(urls.getFirst());
                },
                5000));
        assertEquals(secondManagedUri, viewModel.getPhotoUrls().getFirst());

        viewModel.deletePhoto(0);
        assertTrue(waitUntil(() -> viewModel.getPhotoUrls().size() == 1, 5000));
        assertTrue(waitUntil(
                () -> {
                    List<String> urls = snapshotPhotoUrls(viewModel);
                    return !urls.isEmpty() && firstManagedUri.equals(urls.getFirst());
                },
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
        System.setProperty(FILE_URLS_ENABLED_PROPERTY, "true");
        System.setProperty(FILE_URL_ROOT_PROPERTY, tempHome.toString());

        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActiveUser("PhotoUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);
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
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = User.StorageBuilder.create(UUID.randomUUID(), "SaveUser", AppClock.now())
                .state(User.UserState.INCOMPLETE)
                .build();
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);
        viewModel.loadCurrentUser();
        viewModel.bioProperty().set("Updated bio");
        viewModel.setLocationCoordinates(12.3456, -45.6789);

        AtomicReference<SaveOutcome> saveResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        viewModel.saveAsync(outcome -> {
            saveResult.set(outcome);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(SaveOutcome.SAVED_DRAFT, saveResult.get());
        assertFalse(viewModel.savingProperty().get());
        assertEquals("Updated bio", session.getCurrentUser().getBio());
        assertEquals("12.3456, -45.6789", viewModel.locationDisplayProperty().get());

        viewModel.dispose();
    }

    @Test
    @DisplayName("saveAsync reports activation when a complete incomplete profile is saved")
    void saveAsyncReportsActivationWhenProfileCompletes() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActivatableIncompleteUser("ActivateUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);
        viewModel.loadCurrentUser();
        viewModel.bioProperty().set("Updated bio for activation");

        AtomicReference<SaveOutcome> saveResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        viewModel.saveAsync(outcome -> {
            saveResult.set(outcome);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(SaveOutcome.ACTIVATED, saveResult.get());
        assertEquals(User.UserState.ACTIVE, session.getCurrentUser().getState());

        viewModel.dispose();
    }

    @Test
    @DisplayName("saveAsync keeps already active profiles on the activated outcome path")
    void saveAsyncKeepsAlreadyActiveProfilesOnActivatedOutcomePath() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActiveUser("AlreadyActiveUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);
        viewModel.loadCurrentUser();
        viewModel.bioProperty().set("Updated active bio");

        AtomicReference<SaveOutcome> saveResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        viewModel.saveAsync(outcome -> {
            saveResult.set(outcome);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(SaveOutcome.ACTIVATED, saveResult.get());
        assertEquals(User.UserState.ACTIVE, session.getCurrentUser().getState());
        assertEquals("Updated active bio", session.getCurrentUser().getBio());

        viewModel.dispose();
    }

    @Test
    @DisplayName("saveAsync works with explicit mutation use cases and no profile facade")
    void saveAsyncWorksWithExplicitMutationUseCasesAndNoProfileFacade() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);
        ValidationService validationService = new ValidationService(config);

        User currentUser = createActiveUser("ExplicitMutationUser");
        currentUser.setBio("Original bio");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        var mutationUseCases = new datingapp.app.usecase.profile.ProfileMutationUseCases(
                users,
                validationService,
                datingapp.core.testutil.TestAchievementService.empty(),
                config,
                new datingapp.core.workflow.ProfileActivationPolicy(),
                new datingapp.app.testutil.TestEventBus());

        ProfileViewModel viewModel = new ProfileViewModel(new ProfileViewModel.Dependencies(
                new UiDataAdapters.StorageUiUserStore(users),
                profileService,
                mutationUseCases,
                null,
                config,
                session,
                validationService,
                new datingapp.core.profile.LocationService(validationService),
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy()));
        viewModel.loadCurrentUser();
        viewModel.bioProperty().set("Saved via explicit mutation use case");

        AtomicReference<SaveOutcome> saveResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        viewModel.saveAsync(outcome -> {
            saveResult.set(outcome);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(SaveOutcome.ACTIVATED, saveResult.get());
        assertEquals(
                "Saved via explicit mutation use case", session.getCurrentUser().getBio());

        viewModel.dispose();
    }

    @Test
    @DisplayName("saveAsync failure keeps the original session user unchanged")
    void saveAsyncFailureKeepsOriginalSessionUserUnchanged() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActiveUser("SaveFailureUser");
        currentUser.setBio("Original bio");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        datingapp.core.storage.UserStorage failingUserStorage = new datingapp.core.storage.UserStorage() {
            @Override
            public void save(User user) {
                throw new IllegalStateException("Simulated save failure");
            }

            @Override
            public java.util.Optional<User> get(UUID id) {
                return users.get(id);
            }

            @Override
            public java.util.List<User> findAll() {
                return users.findAll();
            }

            @Override
            public java.util.List<User> findActive() {
                return users.findActive();
            }

            @Override
            public java.util.List<User> findCandidates(
                    UUID excludeId,
                    java.util.Set<Gender> genders,
                    int minAge,
                    int maxAge,
                    double seekerLat,
                    double seekerLon,
                    int maxDistanceKm) {
                return users.findCandidates(excludeId, genders, minAge, maxAge, seekerLat, seekerLon, maxDistanceKm);
            }

            @Override
            public datingapp.core.storage.PageData<User> getPageOfActiveUsers(int offset, int limit) {
                return users.getPageOfActiveUsers(offset, limit);
            }

            @Override
            public datingapp.core.storage.PageData<User> getPageOfAllUsers(int offset, int limit) {
                return users.getPageOfAllUsers(offset, limit);
            }

            @Override
            public java.util.Map<UUID, User> findByIds(java.util.Set<UUID> ids) {
                return users.findByIds(ids);
            }

            @Override
            public void delete(UUID id) {
                users.delete(id);
            }

            @Override
            public int purgeDeletedBefore(Instant threshold) {
                return users.purgeDeletedBefore(threshold);
            }

            @Override
            public void saveProfileNote(datingapp.core.model.ProfileNote note) {
                users.saveProfileNote(note);
            }

            @Override
            public java.util.Optional<datingapp.core.model.ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
                return users.getProfileNote(authorId, subjectId);
            }

            @Override
            public java.util.List<datingapp.core.model.ProfileNote> getProfileNotesByAuthor(UUID authorId) {
                return users.getProfileNotesByAuthor(authorId);
            }

            @Override
            public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
                return users.deleteProfileNote(authorId, subjectId);
            }
        };

        ProfileViewModel viewModel = new ProfileViewModel(
                new UiDataAdapters.StorageUiUserStore(users),
                profileService,
                new datingapp.app.usecase.profile.ProfileMutationUseCases(
                        failingUserStorage,
                        new ValidationService(config),
                        datingapp.core.testutil.TestAchievementService.empty(),
                        config,
                        new datingapp.core.workflow.ProfileActivationPolicy(),
                        new datingapp.app.testutil.TestEventBus()),
                null,
                config,
                session,
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy());
        AtomicReference<String> errorMessage = new AtomicReference<>();
        viewModel.setErrorHandler(errorMessage::set);
        viewModel.loadCurrentUser();
        viewModel.bioProperty().set("Should not persist");

        AtomicReference<SaveOutcome> saveResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        viewModel.saveAsync(outcome -> {
            saveResult.set(outcome);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(SaveOutcome.FAILED, saveResult.get());
        assertFalse(viewModel.savingProperty().get());
        assertEquals("Original bio", session.getCurrentUser().getBio());
        assertTrue(errorMessage.get().contains("Failed to save profile"));

        viewModel.dispose();
    }

    @Test
    @DisplayName("supported saved coordinates are shown with a human friendly label")
    void supportedSavedCoordinatesUseHumanFriendlyLabel() {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActiveUser("LocationUser");
        currentUser.setLocation(32.0853, 34.7818);
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);

        viewModel.loadCurrentUser();

        assertEquals(
                "Tel Aviv, Tel Aviv District",
                viewModel.locationDisplayProperty().get());

        viewModel.dispose();
    }

    @Test
    @DisplayName("profile preview snapshot uses human friendly labels for supported locations")
    void profilePreviewSnapshotUsesHumanFriendlyLabelsForSupportedLocations() {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActiveUser("Preview Location User");
        currentUser.setLocation(32.0853, 34.7818);
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);

        viewModel.loadCurrentUser();

        ProfileViewModel.ProfilePreviewSnapshot snapshot = viewModel.buildPreviewSnapshot();

        assertEquals("Tel Aviv, Tel Aviv District", snapshot.location());

        viewModel.dispose();
    }

    @Test
    @DisplayName("oversized photos are rejected before any upload work starts")
    void oversizedPhotosAreRejectedBeforeUpload() throws Exception {
        originalUserHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("datingapp-profile-home");
        System.setProperty("user.home", tempHome.toString());

        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActiveUser("LargePhotoUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);

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
        AppConfig config = AppConfig.builder().maxInterests(1).build();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActiveUser("InterestLimitUser");
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);

        viewModel.loadCurrentUser();

        assertTrue(viewModel.toggleInterest(datingapp.core.profile.MatchPreferences.Interest.values()[0]));
        assertFalse(viewModel.toggleInterest(datingapp.core.profile.MatchPreferences.Interest.values()[1]));
        assertEquals(1, viewModel.getSelectedInterests().size());

        viewModel.dispose();
    }

    @Test
    @DisplayName("getMaxBioLength returns the configured bio length limit")
    void getMaxBioLengthReturnsConfiguredLimit() {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.builder().maxBioLength(320).build();
        ProfileService profileService = new ProfileService(users);

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

    @Test
    @DisplayName("saveAsync clears interestedIn and interests when selections are emptied")
    void saveAsyncClearsInterestedInAndInterestsWhenSelectionsAreEmptied() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);

        User currentUser = createActiveUser("ClearSelectionsUser");
        currentUser.setInterestedIn(EnumSet.of(Gender.MALE, Gender.FEMALE));
        currentUser.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE));
        users.save(currentUser);
        session.setCurrentUser(currentUser);

        ProfileViewModel viewModel = newMutationBackedProfileViewModel(users, config, profileService);
        viewModel.loadCurrentUser();
        viewModel.getInterestedInGenders().clear();
        viewModel.getSelectedInterests().clear();
        AtomicReference<String> errorMessage = new AtomicReference<>();
        viewModel.setErrorHandler(errorMessage::set);

        AtomicReference<SaveOutcome> saveResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        viewModel.saveAsync(outcome -> {
            saveResult.set(outcome);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(SaveOutcome.ACTIVATED, saveResult.get(), errorMessage.get());
        assertTrue(session.getCurrentUser().getInterestedIn().isEmpty());
        assertTrue(session.getCurrentUser().getInterests().isEmpty());

        viewModel.dispose();
    }

    @Test
    @DisplayName("self composing convenience constructors are not public production API")
    void selfComposingConvenienceConstructorsAreNotPublicProductionApi() {
        long publicConvenienceConstructorCount = java.util.Arrays.stream(
                        ProfileViewModel.class.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .filter(constructor -> {
                    Class<?>[] parameterTypes = constructor.getParameterTypes();
                    return parameterTypes.length != 1 || parameterTypes[0] != ProfileViewModel.Dependencies.class;
                })
                .count();

        assertEquals(0, publicConvenienceConstructorCount);
    }

    private static Path createTempImageFile(String prefix) throws Exception {
        Path file = Files.createTempFile(prefix, ".png");
        Files.write(file, java.util.Base64.getDecoder().decode(SAMPLE_PNG_BASE64));
        return file;
    }

    private ProfileViewModel newMutationBackedProfileViewModel(
            TestStorages.Users users, AppConfig config, ProfileService profileService) {
        return new ProfileViewModel(
                new UiDataAdapters.StorageUiUserStore(users),
                profileService,
                new datingapp.app.usecase.profile.ProfileMutationUseCases(
                        users,
                        new ValidationService(config),
                        datingapp.core.testutil.TestAchievementService.empty(),
                        config,
                        new datingapp.core.workflow.ProfileActivationPolicy(),
                        new datingapp.app.testutil.TestEventBus()),
                null,
                config,
                session,
                TEST_DISPATCHER,
                new datingapp.core.workflow.ProfileActivationPolicy());
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

    private static List<String> snapshotPhotoUrls(ProfileViewModel viewModel) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return List.copyOf(viewModel.getPhotoUrls());
            } catch (ConcurrentModificationException _) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        }
        try {
            return List.copyOf(viewModel.getPhotoUrls());
        } catch (ConcurrentModificationException _) {
            return javafxSnapshotPhotoUrls(viewModel);
        }
    }

    private static List<String> javafxSnapshotPhotoUrls(ProfileViewModel viewModel) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<String>> snapshot = new AtomicReference<>(List.of());
        Platform.runLater(() -> {
            snapshot.set(List.copyOf(viewModel.getPhotoUrls()));
            latch.countDown();
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for FX photo snapshot");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for FX photo snapshot", e);
        }
        return snapshot.get();
    }

    private static User createActiveUser(String name) {
        User user = TestUserFactory.createActiveUser(name);
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.setPhotoUrls(List.of());
        user.setBio("Photo test user");
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
                .pacePreferences(new datingapp.core.profile.MatchPreferences.PacePreferences(
                        datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency.OFTEN,
                        datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate.FEW_DAYS,
                        datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                        datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference.DEEP_CHAT))
                .build();
    }
}
