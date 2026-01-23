package datingapp.ui.controller;

import datingapp.ui.NavigationService;
import datingapp.ui.UISession;
import datingapp.ui.util.ResponsiveController;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.viewmodel.DashboardViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Dashboard screen (dashboard.fxml).
 * Main hub for navigation to all app features.
 * Extends BaseController for automatic subscription cleanup.
 */
public class DashboardController extends BaseController implements Initializable, ResponsiveController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    private static final String STYLE_VIEWPORT_COMPACT = "viewport-compact";

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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Use Subscription API for memory-safe listener management
        addSubscription(viewModel.userNameProperty().subscribe(val -> userNameLabel.setText(val)));
        addSubscription(viewModel.dailyLikesStatusProperty().subscribe(val -> statusLabel.setText(val)));
        addSubscription(viewModel.dailyPickNameProperty().subscribe(val -> dailyPickLabel.setText(val)));
        addSubscription(viewModel.totalMatchesProperty().subscribe(val -> totalMatchesLabel.setText(val)));
        addSubscription(viewModel.profileCompletionProperty().subscribe(val -> completionLabel.setText(val)));

        // Handle loading state changes for skeleton display
        addSubscription(viewModel.loadingProperty().subscribe(this::handleLoadingChange));

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

        // Load initial data (will trigger loading skeleton)
        viewModel.refresh();
    }

    /**
     * Handles loading state changes by showing/hiding skeleton loaders.
     */
    private void handleLoadingChange(boolean isLoading) {
        if (isLoading) {
            // Show skeleton for daily pick label while loading
            if (dailyPickLabel != null) {
                dailyPickLabel.setText("Loading...");
                dailyPickLabel.setOpacity(0.5);
            }
            if (achievementsListView != null) {
                achievementsListView.setOpacity(0.5);
            }
        } else {
            // Data loaded - restore full opacity with fade
            if (dailyPickLabel != null) {
                FadeTransition fade = new FadeTransition(Duration.millis(300), dailyPickLabel);
                fade.setToValue(1.0);
                fade.play();
            }
            if (achievementsListView != null) {
                FadeTransition fade = new FadeTransition(Duration.millis(300), achievementsListView);
                fade.setToValue(1.0);
                fade.play();
            }
        }
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
                stage.widthProperty().addListener((o, oldW, newW) -> setCompactMode(newW.doubleValue() < 900));
            }
        });
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleBrowse() {
        logger.info("Navigating to Matching screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHING);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleMatches() {
        logger.info("Navigating to Matches screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHES);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleStats() {
        logger.info("Navigating to Stats screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.STATS);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleChat() {
        logger.info("Navigating to Chat screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.CHAT);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleProfile() {
        logger.info("Navigating to Profile screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.PROFILE);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleViewDailyPick() {
        logger.info("Viewing daily pick - navigating to Matching");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHING);
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleLogout() {
        logger.info("Logging out");
        UISession.getInstance().logout();
        NavigationService.getInstance().getViewModelFactory().reset();
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.LOGIN);
    }

    @Override
    public void setCompactMode(boolean compact) {
        if (rootPane == null) {
            return;
        }

        if (compact) {
            if (!rootPane.getStyleClass().contains(STYLE_VIEWPORT_COMPACT)) {
                rootPane.getStyleClass().add(STYLE_VIEWPORT_COMPACT);
            }
            // Hide secondary stats on very small screens
            if (totalMatchesLabel != null) {
                totalMatchesLabel.getParent().setVisible(false);
                totalMatchesLabel.getParent().setManaged(false);
            }
        } else {
            rootPane.getStyleClass().remove(STYLE_VIEWPORT_COMPACT);
            if (totalMatchesLabel != null) {
                totalMatchesLabel.getParent().setVisible(true);
                totalMatchesLabel.getParent().setManaged(true);
            }
        }
    }
}
