package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.SafetyViewModel;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("SafetyController bindings")
class SafetyControllerTest {

    private static final UiThreadDispatcher TEST_DISPATCHER = JavaFxTestSupport.immediateUiDispatcher();

    private final AppSession session = AppSession.getInstance();
    private SafetyViewModel viewModel;

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
    @DisplayName("FXML loads blocked users into the list and hides the empty state")
    void fxmlLoadsBlockedUsersIntoListAndHidesEmptyState() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Interactions interactions = new TestStorages.Interactions();
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Communications communications = new TestStorages.Communications();
        AppConfig config = AppConfig.defaults();

        User blocker = createUser("Avery", Gender.FEMALE, EnumSet.of(Gender.MALE));
        User blocked = createUser("Blake", Gender.MALE, EnumSet.of(Gender.FEMALE));
        users.save(blocker);
        users.save(blocked);
        session.setCurrentUser(blocker);

        TrustSafetyService trustSafetyService = TrustSafetyService.builder(
                        trustSafetyStorage, interactions, users, config, communications)
                .build();
        trustSafetyService.block(blocker.getId(), blocked.getId());
        viewModel = new SafetyViewModel(trustSafetyService, session, TEST_DISPATCHER);

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/safety.fxml", () -> new SafetyController(viewModel));
        Parent root = loaded.root();
        @SuppressWarnings("unchecked")
        ListView<SafetyViewModel.BlockedUserEntry> blockedUsersListView =
                JavaFxTestSupport.lookup(root, "#blockedUsersListView", ListView.class);
        VBox emptyStateBox = JavaFxTestSupport.lookup(root, "#emptyStateBox", VBox.class);

        assertTrue(
                JavaFxTestSupport.waitUntil(() -> !viewModel.getBlockedUsers().isEmpty(), 5000));
        assertFalse(JavaFxTestSupport.callOnFxAndWait(blockedUsersListView.getItems()::isEmpty));
        assertFalse(JavaFxTestSupport.callOnFxAndWait(emptyStateBox::isVisible));
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
}
