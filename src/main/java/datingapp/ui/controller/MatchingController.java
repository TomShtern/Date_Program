package datingapp.ui.controller;

import datingapp.core.Match;
import datingapp.core.User;
import datingapp.ui.NavigationService;
import datingapp.ui.ViewFactory;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.viewmodel.MatchingViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Matching screen (matching.fxml).
 * Handles candidate display, swipe actions, and match popup.
 */
public class MatchingController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(MatchingController.class);

    @FXML
    private javafx.scene.layout.BorderPane rootPane;

    @FXML
    private VBox candidateCard;

    @FXML
    private Label nameLabel;

    @FXML
    private Label bioLabel;

    @FXML
    private Label distanceLabel;

    @FXML
    private Label matchScoreLabel;

    @FXML
    private VBox noCandidatesContainer;

    @FXML
    @SuppressWarnings("unused")
    private Button undoButton;

    @FXML
    private HBox actionButtonsContainer;

    private final MatchingViewModel viewModel;

    public MatchingController(MatchingViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize the ViewModel with current user from UISession
        viewModel.initialize();

        // Bind visibility to hasMoreCandidates
        candidateCard.visibleProperty().bind(viewModel.hasMoreCandidatesProperty());
        candidateCard.managedProperty().bind(viewModel.hasMoreCandidatesProperty());
        noCandidatesContainer
                .visibleProperty()
                .bind(viewModel.hasMoreCandidatesProperty().not());
        noCandidatesContainer
                .managedProperty()
                .bind(viewModel.hasMoreCandidatesProperty().not());

        // Hide action buttons when no candidates
        actionButtonsContainer.visibleProperty().bind(viewModel.hasMoreCandidatesProperty());
        actionButtonsContainer.managedProperty().bind(viewModel.hasMoreCandidatesProperty());

        // Update UI when current candidate changes
        viewModel.currentCandidateProperty().addListener((obs, oldVal, newVal) -> {
            updateCandidateUI(newVal);
        });

        // Listen for matches to show popup
        viewModel.matchedUserProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showMatchPopup(newVal, viewModel.lastMatchProperty().get());
            }
        });

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);

        updateCandidateUI(viewModel.currentCandidateProperty().get());

        // Suppress unused warning for logger
        if (logger.isTraceEnabled()) {
            logger.trace("MatchingController initialized");
        }
    }

    private void updateCandidateUI(User user) {
        if (user == null) {
            return;
        }

        nameLabel.setText(user.getName() + ", " + user.getAge());
        bioLabel.setText(user.getBio() != null ? user.getBio() : "No bio provided.");
        distanceLabel.setText("📍 " + viewModel.getDistanceDisplay(user));
        matchScoreLabel.setText("⭐ " + viewModel.getCompatibilityDisplay(user));
    }

    /**
     * Shows the "IT'S A MATCH!" popup dialog.
     */
    @SuppressWarnings("unused")
    private void showMatchPopup(User matchedUser, Match match) {
        logger.info("Showing match popup for: {}", matchedUser.getName());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("It's a Match!");

        // Apply theme
        dialog.getDialogPane()
                .getStylesheets()
                .add(getClass().getResource("/css/theme.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        // Create content
        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-padding: 40;");

        Label titleLabel = new Label("It's a Match!");
        titleLabel.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: -fx-accent-super;");

        org.kordamp.ikonli.javafx.FontIcon heartIcon = new org.kordamp.ikonli.javafx.FontIcon("mdi2h-heart-pulse");
        heartIcon.setIconSize(80);
        heartIcon.setIconColor(javafx.scene.paint.Color.web("#f43f5e")); // Rose 500

        Label messageLabel = new Label("You and " + matchedUser.getName() + " liked each other!");
        messageLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: -fx-text-secondary;");

        content.getChildren().addAll(heartIcon, titleLabel, messageLabel);
        dialog.getDialogPane().setContent(content);

        // Add entry animation to the dialog content
        UiAnimations.fadeIn(content, 600);

        // Add buttons
        ButtonType sendMessageBtn = new ButtonType("Send Message", ButtonBar.ButtonData.OK_DONE);
        ButtonType keepSwipingBtn = new ButtonType("Keep Swiping", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(sendMessageBtn, keepSwipingBtn);

        // Style buttons
        Button sendBtn = (Button) dialog.getDialogPane().lookupButton(sendMessageBtn);
        sendBtn.getStyleClass().add("button");
        sendBtn.setStyle("-fx-background-color: -fx-accent-super;");

        dialog.showAndWait().ifPresent(response -> {
            if (response == sendMessageBtn) {
                // Navigate to chat with this match
                NavigationService.getInstance().navigateTo(ViewFactory.ViewType.CHAT);
            }
            // Clear the match notification
            viewModel.clearMatchNotification();
        });
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleLike() {
        viewModel.like();
    }

    @FXML
    @SuppressWarnings("unused")
    private void handlePass() {
        viewModel.pass();
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleUndo() {
        viewModel.undo();
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleBack() {
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.DASHBOARD);
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleExpandPreferences() {
        logger.info("User clicked Expand Preferences - navigating to Profile settings");
        // TODO: Navigate to filter/preferences screen when implemented
        // For now, navigate to Profile where they can update preferences
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.PROFILE);
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleCheckLikes() {
        logger.info("User clicked Check Likes - navigating to Matches");
        // Navigate to Matches screen where they can see who liked them
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.MATCHES);
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleImproveProfile() {
        logger.info("User clicked Improve Profile - navigating to Profile");
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.PROFILE);
    }
}
