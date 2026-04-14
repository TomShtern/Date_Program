package datingapp.ui.screen;

import datingapp.ui.ImageCache;
import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.viewmodel.ProfileReadOnlyViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.UUID;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;

/** Read-only profile screen for viewing another user's profile. */
@SuppressWarnings("unused") // FXML-injected members and handlers are referenced from FXML.
public final class ProfileViewController extends BaseController implements Initializable {

    private long profilePhotoRequestId;

    @FXML
    private BorderPane rootPane;

    @FXML
    private ImageView profileImageView;

    @FXML
    private Label nameLabel;

    @FXML
    private Label bioLabel;

    @FXML
    private Label locationLabel;

    @FXML
    private Label lookingForLabel;

    @FXML
    private Label interestsLabel;

    @FXML
    private Label photoIndicatorLabel;

    private final ProfileReadOnlyViewModel viewModel;

    public ProfileViewController(ProfileReadOnlyViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        nameLabel.textProperty().bind(viewModel.nameProperty());
        bioLabel.textProperty().bind(viewModel.bioProperty());
        locationLabel.textProperty().bind(viewModel.locationProperty());
        lookingForLabel.textProperty().bind(viewModel.lookingForProperty());
        interestsLabel.textProperty().bind(viewModel.interestsProperty());
        addSubscription(viewModel.currentPhotoUrlProperty().subscribe(this::updatePhoto));
        addSubscription(viewModel.currentPhotoIndexProperty().subscribe(_ -> updatePhotoIndicator()));

        UUID targetUserId = navigationService()
                .consumeNavigationContext(NavigationService.ViewType.PROFILE_VIEW, UUID.class)
                .orElse(null);
        viewModel.loadUser(targetUserId);
        updatePhotoIndicator();
        UiAnimations.fadeIn(rootPane, 500);
    }

    private void updatePhoto(String photoUrl) {
        profilePhotoRequestId++;
        long requestId = profilePhotoRequestId;
        profileImageView.setImage(null);
        if (photoUrl == null || photoUrl.isEmpty()) {
            profileImageView.setImage(ImageCache.getImage(photoUrl, 360, 240));
            return;
        }
        ImageCache.getImageAsync(photoUrl, 360, 240, image -> {
            if (requestId != profilePhotoRequestId) {
                return;
            }
            profileImageView.setImage(image);
        });
    }

    @Override
    public void cleanup() {
        profilePhotoRequestId++;
        super.cleanup();
    }

    private void updatePhotoIndicator() {
        if (viewModel.getPhotoUrls().isEmpty()) {
            photoIndicatorLabel.setText("0/0");
            return;
        }
        photoIndicatorLabel.setText((viewModel.currentPhotoIndexProperty().get() + 1) + "/"
                + viewModel.getPhotoUrls().size());
    }

    @FXML
    private void handleNextPhoto() {
        viewModel.showNextPhoto();
    }

    @FXML
    private void handlePreviousPhoto() {
        viewModel.showPreviousPhoto();
    }
}
