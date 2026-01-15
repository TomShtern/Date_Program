package datingapp.ui.controller;

import datingapp.ui.NavigationService;
import datingapp.ui.UISession;
import datingapp.ui.ViewFactory;
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
public class DashboardController implements Initializable {
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Bind UI labels to ViewModel properties
        userNameLabel.textProperty().bind(viewModel.userNameProperty());
        statusLabel.textProperty().bind(viewModel.dailyLikesStatusProperty());
        dailyPickLabel.textProperty().bind(viewModel.dailyPickNameProperty());
        totalMatchesLabel.textProperty().bind(viewModel.totalMatchesProperty());
        completionLabel.textProperty().bind(viewModel.profileCompletionProperty());

        // Bind achievements list
        if (achievementsListView != null) {
            achievementsListView.setItems(viewModel.getRecentAchievements());
        }

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);

        // Load initial data
        viewModel.refresh();
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
}
