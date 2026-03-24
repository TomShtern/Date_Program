package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
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
@DisplayName("NotesViewModel note browser")
class NotesViewModelTest {

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
    @DisplayName("initialize loads notes and enriches them with subject names")
    void initializeLoadsNotesWithNames() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);
        ProfileUseCases profileUseCases = new ProfileUseCases(
                users, profileService, null, null, null, config, new ProfileActivationPolicy(), null);

        User author = createUser("Morgan", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User subjectOne = createUser("Riley", Gender.MALE, EnumSet.of(Gender.FEMALE));
        User subjectTwo = createUser("Taylor", Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(author);
        users.save(subjectOne);
        users.save(subjectTwo);
        users.saveProfileNote(ProfileNote.create(author.getId(), subjectOne.getId(), "Thoughtful and funny"));
        users.saveProfileNote(ProfileNote.create(author.getId(), subjectTwo.getId(), "Great travel stories"));
        AppSession.getInstance().setCurrentUser(author);

        NotesViewModel viewModel = new NotesViewModel(
                profileUseCases, new StorageUiUserStore(users), AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(waitUntil(() -> viewModel.getNotes().size() == 2, 5000));
        assertTrue(
                viewModel.getNotes().stream().anyMatch(note -> note.userName().equals("Riley")));
        assertTrue(
                viewModel.getNotes().stream().anyMatch(note -> note.userName().equals("Taylor")));
        assertTrue(viewModel.getNotes().stream().anyMatch(note -> note.content().contains("Thoughtful")));

        viewModel.dispose();
    }

    @Test
    @DisplayName("initialize keeps notes empty when the user has no saved notes")
    void initializeLeavesEmptyStateForNoNotes() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);
        ProfileUseCases profileUseCases = new ProfileUseCases(
                users, profileService, null, null, null, config, new ProfileActivationPolicy(), null);

        User author = createUser("Jordan", Gender.FEMALE, EnumSet.of(Gender.MALE));
        users.save(author);
        AppSession.getInstance().setCurrentUser(author);

        NotesViewModel viewModel = new NotesViewModel(
                profileUseCases, new StorageUiUserStore(users), AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(waitUntil(viewModel.getNotes()::isEmpty, 5000));
        viewModel.dispose();
    }

    @Test
    @DisplayName("saveSelectedNote updates storage and the observable note list")
    void saveSelectedNoteUpdatesStorageAndList() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);
        ProfileUseCases profileUseCases = new ProfileUseCases(
                users, profileService, null, null, null, config, new ProfileActivationPolicy(), null);

        User author = createUser("Morgan", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User subject = createUser("Riley", Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(author);
        users.save(subject);
        users.saveProfileNote(ProfileNote.create(author.getId(), subject.getId(), "Thoughtful and funny"));
        AppSession.getInstance().setCurrentUser(author);

        NotesViewModel viewModel = new NotesViewModel(
                profileUseCases, new StorageUiUserStore(users), AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(waitUntil(() -> viewModel.getNotes().size() == 1, 5000));
        viewModel.selectNote(viewModel.getNotes().get(0));
        viewModel.selectedNoteContentProperty().set("Updated note content for Riley");
        viewModel.saveSelectedNote();

        assertTrue(waitUntil(
                () -> "Updated note content for Riley"
                        .equals(viewModel.getNotes().get(0).content()),
                5000));
        assertTrue(waitUntil(
                () -> fixtureNoteContent(users, author.getId(), subject.getId())
                        .filter("Updated note content for Riley"::equals)
                        .isPresent(),
                5000));

        viewModel.dispose();
    }

    @Test
    @DisplayName("deleteSelectedNote removes storage and the observable note list")
    void deleteSelectedNoteRemovesStorageAndList() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Analytics analytics = new TestStorages.Analytics();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);
        ProfileUseCases profileUseCases = new ProfileUseCases(
                users, profileService, null, null, null, config, new ProfileActivationPolicy(), null);

        User author = createUser("Jordan", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User subject = createUser("Taylor", Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(author);
        users.save(subject);
        users.saveProfileNote(ProfileNote.create(author.getId(), subject.getId(), "Great travel stories"));
        AppSession.getInstance().setCurrentUser(author);

        NotesViewModel viewModel = new NotesViewModel(
                profileUseCases, new StorageUiUserStore(users), AppSession.getInstance(), TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(waitUntil(() -> viewModel.getNotes().size() == 1, 5000));
        viewModel.selectNote(viewModel.getNotes().get(0));
        viewModel.deleteSelectedNote();

        assertTrue(waitUntil(viewModel.getNotes()::isEmpty, 5000));
        assertTrue(waitUntil(
                () -> fixtureNoteContent(users, author.getId(), subject.getId()).isEmpty(), 5000));

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
        user.setBio("Notes test user");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    private static java.util.Optional<String> fixtureNoteContent(
            TestStorages.Users users, UUID authorId, UUID subjectId) {
        return users.getProfileNote(authorId, subjectId).map(ProfileNote::content);
    }
}
