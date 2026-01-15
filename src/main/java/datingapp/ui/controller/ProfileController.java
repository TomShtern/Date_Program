package datingapp.ui.controller;

import datingapp.ui.NavigationService;
import datingapp.ui.ViewFactory;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.viewmodel.ProfileViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

/**
 * Controller for the Profile Editor screen (profile.fxml).
 */
public class ProfileController implements Initializable {

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

        // Listen for interests changes to update chips
        if (interestsField != null) {
            interestsField.textProperty().addListener((obs, oldVal, newVal) -> populateInterestChips());
        }

        // Setup character counter for bio
        setupCharacterCounter();

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);
    }

    /** Setup the character counter binding for the bio text area. */
    private void setupCharacterCounter() {
        if (charCountLabel == null || bioArea == null) {
            return;
        }

        // Initial update
        updateCharCounter(bioArea.getText());

        // Listen for text changes
        bioArea.textProperty().addListener((obs, oldVal, newVal) -> updateCharCounter(newVal));
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
        viewModel.save();
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.DASHBOARD);
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleCancel() {
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.DASHBOARD);
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleBack() {
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.DASHBOARD);
    }
}
