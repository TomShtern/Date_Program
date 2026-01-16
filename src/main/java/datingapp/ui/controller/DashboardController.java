package datingapp.ui.controller;

import datingapp.ui.NavigationService;
import datingapp.ui.UISession;
import datingapp.ui.ViewFactory;
import datingapp.ui.util.ResponsiveController;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.viewmodel.DashboardViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Dashboard screen (dashboard.fxml).
 * Main hub for navigation to all app features.
 */
public class DashboardController implements Initializable, ResponsiveController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @FXML
    private javafx.scene.layout.BorderPane rootPane;

    @FXML
    private Label userNameLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label dailyPickLabel;

    @FXML
    private Label totalMatchesLabel;

    @FXML
    private Label completionLabel;

    @FXML
    private ListView<String> achievementsListView;

    private final DashboardViewModel viewModel;

    public DashboardController(DashboardViewModel viewModel) {
        this.viewModel = viewModel;
    }

    // Weak listeners to prevent memory leaks with cached ViewModels
    private final javafx.beans.value.ChangeListener<String> nameListener =
            (obs, old, val) -> userNameLabel.setText(val);
    private final javafx.beans.value.ChangeListener<String> statusListener =
            (obs, old, val) -> statusLabel.setText(val);
    private final javafx.beans.value.ChangeListener<String> pickListener =
            (obs, old, val) -> dailyPickLabel.setText(val);
    private final javafx.beans.value.ChangeListener<String> matchListener =
            (obs, old, val) -> totalMatchesLabel.setText(val);
    private final javafx.beans.value.ChangeListener<String> completionListener =
            (obs, old, val) -> completionLabel.setText(val);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Use weak listeners for long-lived ViewModel properties
        viewModel.userNameProperty().addListener(new javafx.beans.value.WeakChangeListener<>(nameListener));
        viewModel.dailyLikesStatusProperty().addListener(new javafx.beans.value.WeakChangeListener<>(statusListener));
        viewModel.dailyPickNameProperty().addListener(new javafx.beans.value.WeakChangeListener<>(pickListener));
        viewModel.totalMatchesProperty().addListener(new javafx.beans.value.WeakChangeListener<>(matchListener));
        viewModel
                .profileCompletionProperty()
                .addListener(new javafx.beans.value.WeakChangeListener<>(completionListener));

        // Set initial values
        userNameLabel.setText(viewModel.userNameProperty().get());
        statusLabel.setText(viewModel.dailyLikesStatusProperty().get());
        dailyPickLabel.setText(viewModel.dailyPickNameProperty().get());
        totalMatchesLabel.setText(viewModel.totalMatchesProperty().get());
        completionLabel.setText(viewModel.profileCompletionProperty().get());

        // Bind achievements list
        if (achievementsListView != null) {
            achievementsListView.setItems(viewModel.getRecentAchievements());
        }

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);

        // Setup responsive window size listener
        setupResponsiveListener();

        // Load initial data
        viewModel.refresh();
    }

    /** Adds a window width listener to trigger compact mode at 900px threshold. */
    private void setupResponsiveListener() {
        // Wait for scene to be available
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && newScene.getWindow() != null) {
                javafx.stage.Stage stage = (javafx.stage.Stage) newScene.getWindow();
                // Apply initial state
                setCompactMode(stage.getWidth() < 900);
                // Listen for resize
                stage.widthProperty().addListener((o, oldW, newW) -> {
                    setCompactMode(newW.doubleValue() < 900);
                });
            }
        });
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleBrowse() {
        logger.info("Navigating to Matching screen");
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.MATCHING);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleMatches() {
        logger.info("Navigating to Matches screen");
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.MATCHES);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleStats() {
        logger.info("Navigating to Stats screen");
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.STATS);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleChat() {
        logger.info("Navigating to Chat screen");
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.CHAT);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleProfile() {
        logger.info("Navigating to Profile screen");
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.PROFILE);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleViewDailyPick() {
        logger.info("Viewing daily pick - navigating to Matching");
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.MATCHING);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleLogout() {
        logger.info("Logging out");
        UISession.getInstance().logout();
        NavigationService.getInstance().getViewModelFactory().reset();
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.LOGIN);
    }

    @Override
    public void setCompactMode(boolean compact) {
        if (rootPane == null) {
            return;
        }

        if (compact) {
            if (!rootPane.getStyleClass().contains("viewport-compact")) {
                rootPane.getStyleClass().add("viewport-compact");
            }
            // Hide secondary stats on very small screens
            if (totalMatchesLabel != null) {
                totalMatchesLabel.getParent().setVisible(false);
                totalMatchesLabel.getParent().setManaged(false);
            }
        } else {
            rootPane.getStyleClass().remove("viewport-compact");
            if (totalMatchesLabel != null) {
                totalMatchesLabel.getParent().setVisible(true);
                totalMatchesLabel.getParent().setManaged(true);
            }
        }
    }
}
