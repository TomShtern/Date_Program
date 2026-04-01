package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestStorages;
import datingapp.ui.JavaFxTestSupport;
import datingapp.ui.NavigationService;
import datingapp.ui.viewmodel.ProfileReadOnlyViewModel;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("ProfileViewController read-only profile view")
class ProfileViewControllerTest {

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @Test
    @DisplayName("FXML loads and renders navigation-context user details")
    void fxmlLoadsAndRendersSelectedUser() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        User viewedUser = createViewedUser();
        users.save(viewedUser);

        NavigationService.getInstance()
                .setNavigationContext(NavigationService.ViewType.PROFILE_VIEW, viewedUser.getId());
        ProfileReadOnlyViewModel viewModel = new ProfileReadOnlyViewModel(
                new StorageUiUserStore(users), AppConfig.defaults(), JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/profile-view.fxml", () -> new ProfileViewController(viewModel));
        Parent root = loaded.root();
        Label nameLabel = JavaFxTestSupport.lookup(root, "#nameLabel", Label.class);
        Label bioLabel = JavaFxTestSupport.lookup(root, "#bioLabel", Label.class);

        assertTrue(JavaFxTestSupport.waitUntil(() -> !nameLabel.getText().isBlank(), 5000));
        assertTrue(nameLabel.getText().contains("Viewed User"));
        assertEquals("Read-only profile bio", bioLabel.getText());

        viewModel.dispose();
        NavigationService.getInstance().clearHistory();
    }

    @Test
    @DisplayName("blank photo URL falls back to default avatar instead of clearing the image")
    void blankPhotoUrlFallsBackToDefaultAvatar() throws Exception {
        TestStorages.Users users = new TestStorages.Users();
        User viewedUser = createViewedUserWithoutPhotos();
        users.save(viewedUser);

        NavigationService.getInstance()
                .setNavigationContext(NavigationService.ViewType.PROFILE_VIEW, viewedUser.getId());
        ProfileReadOnlyViewModel viewModel = new ProfileReadOnlyViewModel(
                new StorageUiUserStore(users), AppConfig.defaults(), JavaFxTestSupport.blockingUiDispatcher());

        JavaFxTestSupport.LoadedFxml loaded =
                JavaFxTestSupport.loadFxml("/fxml/profile-view.fxml", () -> new ProfileViewController(viewModel));
        Parent root = loaded.root();
        Label nameLabel = JavaFxTestSupport.lookup(root, "#nameLabel", Label.class);
        ImageView profileImageView = JavaFxTestSupport.lookup(root, "#profileImageView", ImageView.class);

        assertTrue(JavaFxTestSupport.waitUntil(() -> !nameLabel.getText().isBlank(), 5000));
        assertNotNull(JavaFxTestSupport.callOnFxAndWait(profileImageView::getImage));

        viewModel.dispose();
        NavigationService.getInstance().clearHistory();
    }

    private static User createViewedUser() {
        User user = new User(UUID.randomUUID(), "Viewed User");
        user.setBirthDate(AppClock.today().minusYears(29));
        user.setGender(Gender.FEMALE);
        user.setInterestedIn(EnumSet.of(Gender.MALE));
        user.setAgeRange(21, 40, 18, 120);
        user.setMaxDistanceKm(50, 500);
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/viewed.jpg");
        user.setBio("Read-only profile bio");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    private static User createViewedUserWithoutPhotos() {
        User user = new User(UUID.randomUUID(), "No Photo User");
        user.setBirthDate(AppClock.today().minusYears(29));
        user.setGender(Gender.FEMALE);
        user.setInterestedIn(EnumSet.of(Gender.MALE));
        user.setAgeRange(21, 40, 18, 120);
        user.setMaxDistanceKm(50, 500);
        user.setLocation(40.7128, -74.0060);
        user.setBio("Read-only profile bio without uploaded photos");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        return user;
    }
}
