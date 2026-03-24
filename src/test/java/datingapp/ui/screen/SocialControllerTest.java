package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.User;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.viewmodel.SocialViewModel;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiSocialDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("SocialController wiring and rendering")
class SocialControllerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @AfterEach
    void resetSession() {
        AppSession.getInstance().reset();
        TestClock.reset();
    }

    @Test
    @DisplayName("FXML loads and binds notifications and friend requests lists")
    void fxmlLoadsAndBindsSocialLists() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedData();

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/social.fxml", () -> new SocialController(fixture.viewModel));
        Parent root = loaded.root();

        ListView<?> notifications = JavaFxTestSupport.lookup(root, "#notificationsListView", ListView.class);
        ListView<?> requests = JavaFxTestSupport.lookup(root, "#requestsListView", ListView.class);

        assertTrue(JavaFxTestSupport.waitUntil(() -> !notifications.getItems().isEmpty(), 5000));
        assertTrue(JavaFxTestSupport.waitUntil(() -> !requests.getItems().isEmpty(), 5000));

        fixture.dispose();
    }

    @Test
    @DisplayName("Empty social data: lists remain empty with no notifications or friend requests")
    void emptySocialDataKeepsListsEmpty() throws Exception {
        Fixture fixture = new Fixture();
        User currentUser = TestUserFactory.createActiveUser("EmptySocialUser");
        fixture.users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/social.fxml", () -> new SocialController(fixture.viewModel));
        Parent root = loaded.root();

        ListView<?> notifications = JavaFxTestSupport.lookup(root, "#notificationsListView", ListView.class);
        ListView<?> requests = JavaFxTestSupport.lookup(root, "#requestsListView", ListView.class);

        assertTrue(notifications.getItems().isEmpty());
        assertTrue(requests.getItems().isEmpty());

        fixture.dispose();
    }

    @Test
    @DisplayName("Pressing Enter or Space on unread notification marks it as read")
    void keyboardEnterOnUnreadNotificationMarksAsRead() throws Exception {
        Fixture fixture = new Fixture();
        fixture.seedData();

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/social.fxml", () -> new SocialController(fixture.viewModel));
        Parent root = loaded.root();
        @SuppressWarnings("unchecked")
        ListView<Notification> notificationsListView =
                JavaFxTestSupport.lookup(root, "#notificationsListView", ListView.class);

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> !notificationsListView.getItems().isEmpty(), 5000));

        Notification selectedNotification = JavaFxTestSupport.callOnFxAndWait(
                () -> notificationsListView.getItems().get(0));
        assertTrue(JavaFxTestSupport.waitUntil(() -> !selectedNotification.isRead(), 5000));

        JavaFxTestSupport.runOnFxAndWait(() -> {
            notificationsListView.getSelectionModel().select(selectedNotification);
            KeyEvent enterEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
            notificationsListView.fireEvent(enterEvent);
        });

        // Verify notification is marked as read
        assertTrue(JavaFxTestSupport.waitUntil(
                () -> fixture.communications
                        .getNotification(selectedNotification.id())
                        .map(Notification::isRead)
                        .orElse(false),
                5000));

        fixture.dispose();
    }

    private static final class Fixture {
        private final TestStorages.Users users = new TestStorages.Users();
        private final TestStorages.Interactions interactions = new TestStorages.Interactions();
        private final TestStorages.Communications communications = new TestStorages.Communications();
        private final SocialViewModel viewModel;
        private final User currentUser = TestUserFactory.createActiveUser("SocialCurrent");
        private final User sender = TestUserFactory.createActiveUser("SocialSender");

        private Fixture() {
            TestClock.setFixed(FIXED_INSTANT);
            AppConfig config = AppConfig.defaults();
            ConnectionService connectionService = new ConnectionService(config, communications, interactions, users);
            var socialUseCases =
                    new datingapp.app.usecase.social.SocialUseCases(connectionService, null, communications);
            this.viewModel = new SocialViewModel(
                    connectionService,
                    new StorageUiSocialDataAccess(communications),
                    new StorageUiUserStore(users),
                    socialUseCases,
                    AppSession.getInstance(),
                    new datingapp.ui.async.JavaFxUiThreadDispatcher());
        }

        private void seedData() {
            users.save(currentUser);
            users.save(sender);
            AppSession.getInstance().setCurrentUser(currentUser);
            communications.saveNotification(Notification.create(
                    currentUser.getId(),
                    Notification.Type.MATCH_FOUND,
                    "New Match",
                    "You matched with someone.",
                    null));
            communications.saveFriendRequest(FriendRequest.create(sender.getId(), currentUser.getId()));
        }

        private void dispose() {
            viewModel.dispose();
        }
    }
}
