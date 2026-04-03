package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.async.UiThreadDispatcher;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("NotesViewModel note browser")
class NotesViewModelTest {

    private static final UiThreadDispatcher TEST_DISPATCHER = JavaFxTestSupport.immediateUiDispatcher();
    private static final String AUTHOR_NAME = "Morgan";
    private static final String SUBJECT_ONE_NAME = "Riley";
    private static final String SUBJECT_TWO_NAME = "Taylor";
    private static final String UPDATED_RILEY_NOTE = "Updated note content for Riley";
    private static final Instant UPDATED_NOTE_INSTANT = Instant.parse("2026-02-01T00:30:00Z");

    @BeforeAll
    static void initJfx() {
        try {
            JavaFxTestSupport.initJfx();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to initialize JavaFX toolkit", e);
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
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);
        ProfileUseCases profileUseCases = new ProfileUseCases(
                users,
                profileService,
                null,
                null,
                TestAchievementService.empty(),
                config,
                new ProfileActivationPolicy(),
                new InProcessAppEventBus());

        User author = createUser(AUTHOR_NAME, Gender.FEMALE, EnumSet.of(Gender.MALE));
        User subjectOne = createUser(SUBJECT_ONE_NAME, Gender.MALE, EnumSet.of(Gender.FEMALE));
        User subjectTwo = createUser(SUBJECT_TWO_NAME, Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(author);
        users.save(subjectOne);
        users.save(subjectTwo);
        users.saveProfileNote(ProfileNote.create(author.getId(), subjectOne.getId(), "Thoughtful and funny"));
        users.saveProfileNote(ProfileNote.create(author.getId(), subjectTwo.getId(), "Great travel stories"));
        AppSession.getInstance().setCurrentUser(author);

        NotesViewModel viewModel = new NotesViewModel(
                profileUseCases,
                new UiDataAdapters.StorageUiUserStore(users),
                AppSession.getInstance(),
                config.safety().userTimeZone(),
                TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(JavaFxTestSupport.waitUntil(() -> viewModel.getNotes().size() == 2, 5000));
        assertTrue(
                viewModel.getNotes().stream().anyMatch(note -> note.userName().equals(SUBJECT_ONE_NAME)));
        assertTrue(
                viewModel.getNotes().stream().anyMatch(note -> note.userName().equals(SUBJECT_TWO_NAME)));
        assertTrue(viewModel.getNotes().stream().anyMatch(note -> note.content().contains("Thoughtful")));

        viewModel.dispose();
    }

    @Test
    @DisplayName("initialize formats note timestamps using the configured user timezone")
    void initializeFormatsNoteTimestampsUsingConfiguredUserTimezone() throws InterruptedException {
        TimeZone originalDefault = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            TestStorages.Users users = new TestStorages.Users();
            AppConfig config = AppConfig.defaults();
            ZoneId configuredZone = ZoneId.of("Pacific/Honolulu");
            ProfileService profileService = new ProfileService(users);
            ProfileUseCases profileUseCases = new ProfileUseCases(
                    users,
                    profileService,
                    null,
                    null,
                    TestAchievementService.empty(),
                    config,
                    new ProfileActivationPolicy(),
                    new InProcessAppEventBus());

            User author = createUser(AUTHOR_NAME, Gender.FEMALE, EnumSet.of(Gender.MALE));
            User subject = createUser(SUBJECT_ONE_NAME, Gender.MALE, EnumSet.of(Gender.FEMALE));
            users.save(author);
            users.save(subject);
            users.saveProfileNote(new ProfileNote(
                    author.getId(),
                    subject.getId(),
                    "Timezone-sensitive note",
                    Instant.parse("2026-01-31T23:30:00Z"),
                    UPDATED_NOTE_INSTANT));
            AppSession.getInstance().setCurrentUser(author);

            NotesViewModel viewModel = new NotesViewModel(
                    profileUseCases,
                    new UiDataAdapters.StorageUiUserStore(users),
                    AppSession.getInstance(),
                    configuredZone,
                    TEST_DISPATCHER);
            viewModel.initialize();

            assertTrue(JavaFxTestSupport.waitUntil(() -> viewModel.getNotes().size() == 1, 5000));
            String expected = DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(configuredZone)
                    .format(UPDATED_NOTE_INSTANT);
            String utcRendered = DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("UTC"))
                    .format(UPDATED_NOTE_INSTANT);
            assertEquals(expected, viewModel.getNotes().getFirst().lastModified());
            assertNotEquals(expected, utcRendered);

            viewModel.dispose();
        } finally {
            TimeZone.setDefault(originalDefault);
        }
    }

    @Test
    @DisplayName("initialize keeps notes empty when the user has no saved notes")
    void initializeLeavesEmptyStateForNoNotes() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);
        ProfileUseCases profileUseCases = new ProfileUseCases(
                users,
                profileService,
                null,
                null,
                TestAchievementService.empty(),
                config,
                new ProfileActivationPolicy(),
                new InProcessAppEventBus());

        User author = createUser("Jordan", Gender.FEMALE, EnumSet.of(Gender.MALE));
        users.save(author);
        AppSession.getInstance().setCurrentUser(author);

        NotesViewModel viewModel = new NotesViewModel(
                profileUseCases,
                new UiDataAdapters.StorageUiUserStore(users),
                AppSession.getInstance(),
                config.safety().userTimeZone(),
                TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(JavaFxTestSupport.waitUntil(viewModel.getNotes()::isEmpty, 5000));
        viewModel.dispose();
    }

    @Test
    @DisplayName("saveSelectedNote updates storage and the observable note list")
    void saveSelectedNoteUpdatesStorageAndList() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);
        ProfileUseCases profileUseCases = new ProfileUseCases(
                users,
                profileService,
                null,
                null,
                TestAchievementService.empty(),
                config,
                new ProfileActivationPolicy(),
                new InProcessAppEventBus());

        User author = createUser(AUTHOR_NAME, Gender.FEMALE, EnumSet.of(Gender.MALE));
        User subject = createUser(SUBJECT_ONE_NAME, Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(author);
        users.save(subject);
        users.saveProfileNote(ProfileNote.create(author.getId(), subject.getId(), "Thoughtful and funny"));
        AppSession.getInstance().setCurrentUser(author);

        NotesViewModel viewModel = new NotesViewModel(
                profileUseCases,
                new UiDataAdapters.StorageUiUserStore(users),
                AppSession.getInstance(),
                config.safety().userTimeZone(),
                TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(JavaFxTestSupport.waitUntil(() -> viewModel.getNotes().size() == 1, 5000));
        viewModel.selectNote(viewModel.getNotes().get(0));
        viewModel.selectedNoteContentProperty().set(UPDATED_RILEY_NOTE);
        viewModel.saveSelectedNote();

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> UPDATED_RILEY_NOTE.equals(viewModel.getNotes().get(0).content()), 5000));
        assertTrue(JavaFxTestSupport.waitUntil(
                () -> fixtureNoteContent(users, author.getId(), subject.getId())
                        .filter(UPDATED_RILEY_NOTE::equals)
                        .isPresent(),
                5000));

        viewModel.dispose();
    }

    @Test
    @DisplayName("deleteSelectedNote removes storage and the observable note list")
    void deleteSelectedNoteRemovesStorageAndList() throws InterruptedException {
        TestStorages.Users users = new TestStorages.Users();
        AppConfig config = AppConfig.defaults();
        ProfileService profileService = new ProfileService(users);
        ProfileUseCases profileUseCases = new ProfileUseCases(
                users,
                profileService,
                null,
                null,
                TestAchievementService.empty(),
                config,
                new ProfileActivationPolicy(),
                new InProcessAppEventBus());

        User author = createUser("Jordan", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User subject = createUser(SUBJECT_TWO_NAME, Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(author);
        users.save(subject);
        users.saveProfileNote(ProfileNote.create(author.getId(), subject.getId(), "Great travel stories"));
        AppSession.getInstance().setCurrentUser(author);

        NotesViewModel viewModel = new NotesViewModel(
                profileUseCases,
                new UiDataAdapters.StorageUiUserStore(users),
                AppSession.getInstance(),
                config.safety().userTimeZone(),
                TEST_DISPATCHER);
        viewModel.initialize();

        assertTrue(JavaFxTestSupport.waitUntil(() -> viewModel.getNotes().size() == 1, 5000));
        viewModel.selectNote(viewModel.getNotes().get(0));
        viewModel.deleteSelectedNote();

        assertTrue(JavaFxTestSupport.waitUntil(viewModel.getNotes()::isEmpty, 5000));
        assertTrue(JavaFxTestSupport.waitUntil(
                () -> fixtureNoteContent(users, author.getId(), subject.getId()).isEmpty(), 5000));

        viewModel.dispose();
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
