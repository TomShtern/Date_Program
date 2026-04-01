package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.Match;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.workflow.ProfileActivationPolicy;
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
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("ChatController wiring and binding tests")
class ChatControllerTest {

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @Test
    @DisplayName("initial state uses empty counter and reveals note panel only after selection")
    void initialStateUsesEmptyCounterAndRevealsNotePanelOnlyAfterSelection() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedConversationWithNote("Known chat note");

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/chat.fxml", () -> new ChatController(fixture.viewModel));
        Parent root = loaded.root();
        @SuppressWarnings("unchecked")
        ListView<ConnectionService.ConversationPreview> conversationListView =
                JavaFxTestSupport.lookup(root, "#conversationListView", ListView.class);
        Label messageLengthLabel = JavaFxTestSupport.lookup(root, "#messageLengthLabel", Label.class);
        VBox notePanelContainer = JavaFxTestSupport.lookup(root, "#notePanelContainer", VBox.class);
        Button friendZoneButton = JavaFxTestSupport.lookup(root, "#friendZoneButton", Button.class);
        Button gracefulExitButton = JavaFxTestSupport.lookup(root, "#gracefulExitButton", Button.class);
        Button unmatchButton = JavaFxTestSupport.lookup(root, "#unmatchButton", Button.class);

        assertEquals("0/1000", JavaFxTestSupport.callOnFxAndWait(messageLengthLabel::getText));
        assertTrue(!JavaFxTestSupport.callOnFxAndWait(notePanelContainer::isVisible));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(() ->
                friendZoneButton.getText() == null || friendZoneButton.getText().isBlank()));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(() -> gracefulExitButton.getText() == null
                || gracefulExitButton.getText().isBlank()));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(
                () -> unmatchButton.getText() == null || unmatchButton.getText().isBlank()));

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return !JavaFxTestSupport.callOnFxAndWait(
                                () -> conversationListView.getItems().isEmpty());
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                8000));

        JavaFxTestSupport.runOnFxAndWait(
                () -> conversationListView.getSelectionModel().selectFirst());

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(notePanelContainer::isVisible);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                8000));

        fixture.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
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

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return !JavaFxTestSupport.callOnFxAndWait(
                                () -> conversationListView.getItems().isEmpty());
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                8000));

        JavaFxTestSupport.runOnFxAndWait(() -> {
            conversationListView.getSelectionModel().selectFirst();
            fixture.viewModel
                    .selectedConversationProperty()
                    .set(conversationListView.getItems().get(0));
        });

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return !JavaFxTestSupport.callOnFxAndWait(saveNoteButton::isDisabled);
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
                8000));

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return !JavaFxTestSupport.callOnFxAndWait(deleteNoteButton::isDisabled);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                8000));

        JavaFxTestSupport.runOnFxAndWait(deleteNoteButton::fire);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return fixture.lookupNote().isEmpty()
                                && JavaFxTestSupport.callOnFxAndWait(profileNoteArea::getText)
                                        .isEmpty();
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        fixture.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("send clears input only after confirmed success and preserves failed text")
    void sendClearsInputOnlyAfterSuccessAndPreservesFailedText() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedConversationWithNote("Known chat note");

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/chat.fxml", () -> new ChatController(fixture.viewModel));
        Parent root = loaded.root();
        @SuppressWarnings("unchecked")
        ListView<ConnectionService.ConversationPreview> conversationListView =
                JavaFxTestSupport.lookup(root, "#conversationListView", ListView.class);
        TextArea messageArea = JavaFxTestSupport.lookup(root, "#messageArea", TextArea.class);
        Button sendButton = JavaFxTestSupport.lookup(root, "#sendButton", Button.class);
        Label messageLengthLabel = JavaFxTestSupport.lookup(root, "#messageLengthLabel", Label.class);
        String initialMessageLengthStyle = JavaFxTestSupport.callOnFxAndWait(messageLengthLabel::getStyle);

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

        JavaFxTestSupport.runOnFxAndWait(() -> {
            messageArea.setText("Hello success path");
            sendButton.fire();
        });

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(messageArea::getText)
                                .isEmpty();
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));
        assertEquals("Hello success path", fixture.latestMessageContent().orElseThrow());
        assertEquals("0/1000", JavaFxTestSupport.callOnFxAndWait(messageLengthLabel::getText));
        assertEquals(initialMessageLengthStyle, JavaFxTestSupport.callOnFxAndWait(messageLengthLabel::getStyle));

        String oversized = "x".repeat(Message.MAX_LENGTH + 1);
        JavaFxTestSupport.runOnFxAndWait(() -> {
            messageArea.setText(oversized);
            sendButton.fire();
        });

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return oversized.equals(JavaFxTestSupport.callOnFxAndWait(messageArea::getText));
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));
        assertEquals(
                Message.MAX_LENGTH + 1 + "/" + Message.MAX_LENGTH,
                JavaFxTestSupport.callOnFxAndWait(messageLengthLabel::getText));

        fixture.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("enter sends while shift enter keeps draft text intact")
    void enterSendsWhileShiftEnterKeepsDraftIntact() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedConversationWithNote("Known chat note");

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/chat.fxml", () -> new ChatController(fixture.viewModel));
        Parent root = loaded.root();
        @SuppressWarnings("unchecked")
        ListView<ConnectionService.ConversationPreview> conversationListView =
                JavaFxTestSupport.lookup(root, "#conversationListView", ListView.class);
        TextArea messageArea = JavaFxTestSupport.lookup(root, "#messageArea", TextArea.class);

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

        JavaFxTestSupport.runOnFxAndWait(() -> {
            messageArea.setText("Send via Enter");
            messageArea.fireEvent(
                    new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
        });

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return JavaFxTestSupport.callOnFxAndWait(messageArea::getText)
                                .isEmpty();
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));
        assertEquals("Send via Enter", fixture.latestMessageContent().orElseThrow());

        int messageCountAfterEnter = fixture.messageCount();
        JavaFxTestSupport.runOnFxAndWait(() -> {
            messageArea.setText("Keep this draft");
            messageArea.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, true, false, false, false));
        });

        assertEquals("Keep this draft", JavaFxTestSupport.callOnFxAndWait(messageArea::getText));
        assertEquals(messageCountAfterEnter, fixture.messageCount());
        assertNotEquals("Keep this draft", fixture.latestMessageContent().orElse(""));

        fixture.dispose();
        NavigationService.getInstance().clearHistory();
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("cleanup is safe and idempotent")
    void cleanupIsSafeAndIdempotent() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedConversationWithNote("Known chat note");

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/chat.fxml", () -> new ChatController(fixture.viewModel));
        ChatController controller = (ChatController) loaded.controller();

        JavaFxTestSupport.runOnFxAndWait(() -> {
            controller.cleanup();
            controller.cleanup();
        });

        assertTrue(JavaFxTestSupport.callOnFxAndWait(() -> loaded.root().getScene() != null));

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
        private final TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafety, interactions, users, config)
                .build();
        private final User currentUser = createActiveUser("CurrentUser", Gender.MALE, EnumSet.of(Gender.FEMALE));
        private final User otherUser = createActiveUser("OtherUser", Gender.FEMALE, EnumSet.of(Gender.MALE));
        private final ChatViewModel viewModel;

        private Fixture() {
            ProfileService profileService = new ProfileService(config, analytics, interactions, trustSafety, users);
            var noteUseCases = new datingapp.app.usecase.profile.ProfileUseCases(
                    users,
                    profileService,
                    null,
                    null,
                    TestAchievementService.empty(),
                    config,
                    new ProfileActivationPolicy(),
                    new InProcessAppEventBus());
            var messagingUseCases = new datingapp.app.usecase.messaging.MessagingUseCases(
                    connectionService, new InProcessAppEventBus());
            var socialUseCases = new datingapp.app.usecase.social.SocialUseCases(connectionService, trustSafetyService);
            this.viewModel = new ChatViewModel(
                    messagingUseCases,
                    socialUseCases,
                    AppSession.getInstance(),
                    new datingapp.ui.async.JavaFxUiThreadDispatcher(),
                    Duration.ofMillis(75),
                    Duration.ofMillis(75),
                    new ChatViewModel.ChatUiDependencies(
                            new UseCaseUiProfileNoteDataAccess(noteUseCases.getProfileNotesUseCases()),
                            new NoOpUiPresenceDataAccess()));
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

        private java.util.Optional<String> latestMessageContent() {
            String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(
                    currentUser.getId(), otherUser.getId());
            var result = connectionService.getMessages(conversationId, 50, 0);
            if (!result.success() || result.messages().isEmpty()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(
                    result.messages().get(result.messages().size() - 1).content());
        }

        private int messageCount() {
            String conversationId = datingapp.core.connection.ConnectionModels.Conversation.generateId(
                    currentUser.getId(), otherUser.getId());
            var result = connectionService.getMessages(conversationId, 50, 0);
            return result.success() ? result.messages().size() : 0;
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
