package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.Match;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.viewmodel.ChatViewModel;
import datingapp.ui.viewmodel.UiDataAdapters.NoOpUiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UseCaseUiProfileNoteDataAccess;
import java.time.Duration;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("ChatController wiring and binding tests")
class ChatControllerTest {

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @Test
    @DisplayName("FXML selection toggles chat state and note buttons remain wired")
    void selectionTogglesChatStateAndNoteButtonsRemainWired() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedConversationWithNote("Known chat note");

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/chat.fxml", () -> new ChatController(fixture.viewModel));
        Parent root = loaded.root();
        @SuppressWarnings("unchecked")
        ListView<ConnectionService.ConversationPreview> conversationListView =
                JavaFxTestSupport.lookup(root, "#conversationListView", ListView.class);
        TextArea profileNoteArea = JavaFxTestSupport.lookup(root, "#profileNoteArea", TextArea.class);
        Button saveNoteButton = JavaFxTestSupport.lookup(root, "#saveProfileNoteButton", Button.class);
        Button deleteNoteButton = JavaFxTestSupport.lookup(root, "#deleteProfileNoteButton", Button.class);
        Button friendZoneButton = JavaFxTestSupport.lookup(root, "#friendZoneButton", Button.class);
        VBox chatContainer = JavaFxTestSupport.lookup(root, "#chatContainer", VBox.class);
        VBox emptyStateContainer = JavaFxTestSupport.lookup(root, "#emptyStateContainer", VBox.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return !JavaFxTestSupport.callOnFxAndWait(
                                () -> conversationListView.getItems().isEmpty());
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        JavaFxTestSupport.runOnFxAndWait(
                () -> conversationListView.getSelectionModel().selectFirst());

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(chatContainer::isVisible)
                                && !JavaFxTestSupport.callOnFxAndWait(emptyStateContainer::isVisible);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return "Known chat note".equals(JavaFxTestSupport.callOnFxAndWait(profileNoteArea::getText));
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        JavaFxTestSupport.runOnFxAndWait(() -> {
            profileNoteArea.setText("Updated chat note");
            saveNoteButton.fire();
        });

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> fixture.lookupNote()
                        .map(ProfileNote::content)
                        .filter("Updated chat note"::equals)
                        .isPresent(),
                5000));

        JavaFxTestSupport.runOnFxAndWait(deleteNoteButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(() -> fixture.lookupNote().isEmpty(), 5000));

        fixture.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    private static final class Fixture {
        private final TestStorages.Users users = new TestStorages.Users();
        private final TestStorages.Interactions interactions = new TestStorages.Interactions();
        private final TestStorages.Communications communications = new TestStorages.Communications();
        private final TestStorages.TrustSafety trustSafety = new TestStorages.TrustSafety();
        private final TestStorages.Analytics analytics = new TestStorages.Analytics();
        private final AppConfig config = AppConfig.defaults();
        private final ConnectionService connectionService =
                new ConnectionService(config, communications, interactions, users);
        private final TrustSafetyService trustSafetyService =
                new TrustSafetyService(trustSafety, interactions, users, config);
        private final User currentUser = createActiveUser("CurrentUser", Gender.MALE, EnumSet.of(Gender.FEMALE));
        private final User otherUser = createActiveUser("OtherUser", Gender.FEMALE, EnumSet.of(Gender.MALE));
        private final ChatViewModel viewModel;

        private Fixture() {
            ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);
            var noteUseCases =
                    new datingapp.app.usecase.profile.ProfileUseCases(users, profileService, null, null, config);
            var messagingUseCases = new datingapp.app.usecase.messaging.MessagingUseCases(connectionService);
            var socialUseCases = new datingapp.app.usecase.social.SocialUseCases(connectionService, trustSafetyService);
            this.viewModel = new ChatViewModel(
                    messagingUseCases,
                    socialUseCases,
                    AppSession.getInstance(),
                    new datingapp.ui.async.JavaFxUiThreadDispatcher(),
                    Duration.ofMillis(75),
                    Duration.ofMillis(75),
                    new ChatViewModel.ChatUiDependencies(
                            new UseCaseUiProfileNoteDataAccess(noteUseCases), new NoOpUiPresenceDataAccess()));
        }

        private void seedConversationWithNote(String noteContent) {
            users.save(currentUser);
            users.save(otherUser);
            interactions.save(Match.create(currentUser.getId(), otherUser.getId()));
            users.saveProfileNote(ProfileNote.create(currentUser.getId(), otherUser.getId(), noteContent));
            AppSession.getInstance().setCurrentUser(currentUser);
            viewModel.setCurrentUser(currentUser);
            connectionService.sendMessage(otherUser.getId(), currentUser.getId(), "Hello from chat test");
        }

        private java.util.Optional<ProfileNote> lookupNote() {
            return users.getProfileNote(currentUser.getId(), otherUser.getId());
        }

        private void dispose() {
            viewModel.dispose();
        }

        private static User createActiveUser(String name, Gender gender, EnumSet<Gender> interestedIn) {
            User user = new User(UUID.randomUUID(), name);
            user.setBirthDate(AppClock.today().minusYears(25));
            user.setGender(gender);
            user.setInterestedIn(interestedIn);
            user.setAgeRange(
                    18,
                    60,
                    AppConfig.defaults().validation().minAge(),
                    AppConfig.defaults().validation().maxAge());
            user.setMaxDistanceKm(50, AppConfig.defaults().matching().maxDistanceKm());
            user.setLocation(40.7128, -74.0060);
            user.addPhotoUrl("http://example.com/" + name + ".jpg");
            user.setBio("Bio");
            user.setPacePreferences(new PacePreferences(
                    PacePreferences.MessagingFrequency.OFTEN,
                    PacePreferences.TimeToFirstDate.FEW_DAYS,
                    PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                    PacePreferences.DepthPreference.DEEP_CHAT));
            user.activate();
            return user;
        }
    }
}
