package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.NotesViewModel;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("NotesController bindings")
class NotesControllerTest {

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
    private NotesViewModel viewModel;

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @AfterEach
    void tearDown() {
        if (viewModel != null) {
            viewModel.dispose();
        }
        NavigationService.getInstance().clearHistory();
        session.reset();
    }

    @Test
    @DisplayName("FXML loads saved notes into the list and hides the empty state")
    void fxmlLoadsSavedNotesIntoListAndHidesEmptyState() throws Exception {
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
        session.setCurrentUser(author);

        viewModel = new NotesViewModel(profileUseCases, new StorageUiUserStore(users), session, TEST_DISPATCHER);

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/notes.fxml", () -> new NotesController(viewModel));
        Parent root = loaded.root();
        @SuppressWarnings("unchecked")
        ListView<NotesViewModel.NoteEntry> notesListView =
                JavaFxTestSupport.lookup(root, "#notesListView", ListView.class);
        VBox emptyStateBox = JavaFxTestSupport.lookup(root, "#emptyStateBox", VBox.class);
        Button openSelectedButton = JavaFxTestSupport.findButtonByText(root, "Open Selected");

        assertTrue(JavaFxTestSupport.waitUntil(() -> !viewModel.getNotes().isEmpty(), 5000));
        assertFalse(JavaFxTestSupport.callOnFxAndWait(notesListView.getItems()::isEmpty));
        assertFalse(JavaFxTestSupport.callOnFxAndWait(emptyStateBox::isVisible));
        assertFalse(JavaFxTestSupport.callOnFxAndWait(openSelectedButton::isDisabled));
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
}
