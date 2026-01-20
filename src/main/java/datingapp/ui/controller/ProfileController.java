package datingapp.ui.controller;

import datingapp.ui.NavigationService;
import datingapp.ui.ViewFactory;
import datingapp.ui.util.ToastService;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.viewmodel.ProfileViewModel;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Profile Editor screen (profile.fxml).
 * Extends BaseController for automatic subscription cleanup.
 */
public class ProfileController extends BaseController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    @FXML
    private javafx.scene.layout.BorderPane rootPane;

    @FXML
    private Label nameLabel;

    @FXML
    private Label completionLabel;

    @FXML
    private TextArea bioArea;

    @FXML
    private TextField locationField;

    @FXML
    private TextField interestsField;

    @FXML
    private FlowPane interestsFlow;

    @FXML
    private Label charCountLabel;

    @FXML
    private ImageView profileImageView;

    @FXML
    private FontIcon avatarPlaceholderIcon;

    @SuppressWarnings("unused")
    @FXML
    private Button cameraButton;

    @SuppressWarnings("unused")
    @FXML
    private StackPane avatarInner;

    private static final int BIO_MAX_LENGTH = 500;
    private static final int BIO_WARNING_THRESHOLD = 400;

    private final ProfileViewModel viewModel;

    public ProfileController(ProfileViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Bind form fields to ViewModel properties
        if (nameLabel != null) {
            nameLabel.textProperty().bind(viewModel.nameProperty());
        }
        if (completionLabel != null) {
            completionLabel.textProperty().bind(viewModel.completionStatusProperty());
        }
        if (bioArea != null) {
            bioArea.textProperty().bindBidirectional(viewModel.bioProperty());
        }
        if (locationField != null) {
            locationField.textProperty().bindBidirectional(viewModel.locationProperty());
        }
        if (interestsField != null) {
            interestsField.textProperty().bindBidirectional(viewModel.interestsProperty());
        }

        // Load current user data
        viewModel.loadCurrentUser();

        // Populate interest chips from the interests string
        populateInterestChips();

        // Listen for interests changes to update chips using Subscription API (memory-safe)
        if (interestsField != null) {
            addSubscription(interestsField.textProperty().subscribe(text -> populateInterestChips()));
        }

        // Setup character counter for bio using Subscription API (memory-safe)
        setupCharacterCounter();

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);

        // Add pulsing glow to avatar container
        StackPane avatarContainer = (StackPane) rootPane.lookup(".profile-avatar-container");
        if (avatarContainer != null) {
            UiAnimations.addPulsingGlow(avatarContainer, Color.web("#667eea"));
        }
    }

    /** Setup the character counter binding for the bio text area using Subscription API. */
    private void setupCharacterCounter() {
        if (charCountLabel == null || bioArea == null) {
            return;
        }

        // Initial update
        updateCharCounter(bioArea.getText());

        // Use Subscription API for memory-safe listener
        addSubscription(bioArea.textProperty().subscribe(this::updateCharCounter));
    }

    /** Updates the character counter label with appropriate styling. */
    private void updateCharCounter(String text) {
        int length = text == null ? 0 : text.length();
        charCountLabel.setText(length + "/" + BIO_MAX_LENGTH);

        // Remove existing style classes
        charCountLabel.getStyleClass().removeAll("char-counter-warning", "char-counter-limit");

        // Add appropriate warning class
        if (length >= BIO_MAX_LENGTH) {
            charCountLabel.getStyleClass().add("char-counter-limit");
        } else if (length >= BIO_WARNING_THRESHOLD) {
            charCountLabel.getStyleClass().add("char-counter-warning");
        }
    }

    /**
     * Populates the FlowPane with interest chips parsed from the interests string.
     */
    private void populateInterestChips() {
        if (interestsFlow == null) {
            return;
        }

        interestsFlow.getChildren().clear();

        String interests = viewModel.interestsProperty().get();
        if (interests == null || interests.isBlank()) {
            Label placeholder = new Label("No interests set");
            placeholder.getStyleClass().add("interest-chip");
            placeholder.setStyle("-fx-opacity: 0.6;");
            interestsFlow.getChildren().add(placeholder);
            return;
        }

        // Split by comma and create chips
        String[] interestArray = interests.split(",");
        for (String interest : interestArray) {
            String trimmed = interest.trim();
            if (!trimmed.isEmpty()) {
                Label chip = new Label(trimmed);
                chip.getStyleClass().add("interest-chip");
                interestsFlow.getChildren().add(chip);
            }
        }
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleSave() {
        cleanup(); // Clean up subscriptions before navigating away
        viewModel.save();
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.DASHBOARD);
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleCancel() {
        cleanup(); // Clean up subscriptions before navigating away
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.DASHBOARD);
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleBack() {
        handleCancel(); // Delegate to avoid duplicate code
    }

    /**
     * Handles profile photo upload via FileChooser.
     * Saves the photo via ViewModel for persistence.
     */
    @FXML
    @SuppressWarnings("unused")
    private void handleUploadPhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Photo");
        fileChooser
                .getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                        new FileChooser.ExtensionFilter("All Files", "*.*"));

        // Show file chooser dialog
        File selectedFile = fileChooser.showOpenDialog(rootPane.getScene().getWindow());

        if (selectedFile != null) {
            try {
                // Load image preview from file
                Image image = new Image(selectedFile.toURI().toString(), 200, 200, true, true);

                if (!image.isError()) {
                    // Set image to ImageView (immediate preview)
                    profileImageView.setImage(image);
                    profileImageView.setVisible(true);

                    // Hide placeholder icon
                    if (avatarPlaceholderIcon != null) {
                        avatarPlaceholderIcon.setVisible(false);
                    }

                    logger.info("Profile photo loaded: {}", selectedFile.getName());

                    // Save photo via ViewModel (persists to storage)
                    viewModel.savePhoto(selectedFile);
                } else {
                    logger.warn("Failed to load image: {}", selectedFile.getAbsolutePath());
                    ToastService.getInstance().showError("Failed to load image");
                }
            } catch (Exception e) {
                logger.error("Error loading profile photo", e);
                ToastService.getInstance().showError("Error loading photo: " + e.getMessage());
            }
        }
    }
}
