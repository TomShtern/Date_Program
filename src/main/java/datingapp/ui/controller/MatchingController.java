package datingapp.ui.controller;

import datingapp.core.Match;
import datingapp.core.User;
import datingapp.ui.NavigationService;
import datingapp.ui.ViewFactory;
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
        noCandidatesContainer
                .visibleProperty()
                .bind(viewModel.hasMoreCandidatesProperty().not());

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

        Label heartEmoji = new Label("💕");
        heartEmoji.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label("It's a Match!");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: -fx-accent-super;");

        Label messageLabel = new Label("You and " + matchedUser.getName() + " liked each other!");
        messageLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: -fx-text-secondary;");

        content.getChildren().addAll(heartEmoji, titleLabel, messageLabel);
        dialog.getDialogPane().setContent(content);

        // Add buttons
        ButtonType sendMessageBtn = new ButtonType("Send Message", ButtonBar.ButtonData.OK_DONE);
        ButtonType keepSwipingBtn = new ButtonType("Keep Swiping", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(sendMessageBtn, keepSwipingBtn);

        // Style buttons
        Button sendBtn = (Button) dialog.getDialogPane().lookupButton(sendMessageBtn);
        sendBtn.setStyle("-fx-background-color: -fx-accent-super; -fx-text-fill: white;");

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
}
