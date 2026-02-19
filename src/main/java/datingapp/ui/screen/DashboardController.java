package datingapp.ui.screen;

import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.DashboardViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Dashboard screen (dashboard.fxml).
 * Main hub for navigation to all app features.
 * Extends BaseController for automatic subscription cleanup.
 */
public class DashboardController extends BaseController
        implements Initializable, UiFeedbackService.ResponsiveController {
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

    @FXML
    private Button logoutButton;

    @FXML
    private Button browseButton;

    @FXML
    private Button matchesButton;

    @FXML
    private Button chatButton;

    @FXML
    private Button profileButton;

    @FXML
    private Button dailyPickButton;

    @FXML
    private Button statsButton;

    private final DashboardViewModel viewModel;

    public DashboardController(DashboardViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);

        // Use Subscription API for memory-safe listener management
        addSubscription(viewModel.userNameProperty().subscribe(userNameLabel::setText));
        addSubscription(viewModel.dailyLikesStatusProperty().subscribe(statusLabel::setText));
        addSubscription(viewModel.dailyPickNameProperty().subscribe(dailyPickLabel::setText));
        addSubscription(viewModel.totalMatchesProperty().subscribe(totalMatchesLabel::setText));
        addSubscription(viewModel.profileCompletionProperty().subscribe(completionLabel::setText));

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

        // Load initial data (will trigger loading skeleton)
        viewModel.refresh();

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);

        // Setup responsive window size listener
        setupResponsiveListener();

        wireNavigationButtons();
    }

    /** Adds a window width listener to trigger compact mode at 900px threshold. */
    private void setupResponsiveListener() {
        // Wait for scene to be available
        addSubscription(rootPane.sceneProperty().subscribe(newScene -> {
            if (newScene != null && newScene.getWindow() != null) {
                javafx.stage.Stage stage = (javafx.stage.Stage) newScene.getWindow();
                // Apply initial state
                setCompactMode(stage.getWidth() < 900);
                // Listen for resize
                addSubscription(stage.widthProperty().subscribe(newW -> {
                    if (newW != null) {
                        setCompactMode(newW.doubleValue() < 900);
                    }
                }));
            }
        }));
    }

    private void wireNavigationButtons() {
        if (browseButton != null) {
            browseButton.setOnAction(event -> {
                event.consume();
                handleBrowse();
            });
        }
        if (matchesButton != null) {
            matchesButton.setOnAction(event -> {
                event.consume();
                handleMatches();
            });
        }
        if (chatButton != null) {
            chatButton.setOnAction(event -> {
                event.consume();
                handleChat();
            });
        }
        if (profileButton != null) {
            profileButton.setOnAction(event -> {
                event.consume();
                handleProfile();
            });
        }
        if (dailyPickButton != null) {
            dailyPickButton.setOnAction(event -> {
                event.consume();
                handleViewDailyPick();
            });
        }
        if (statsButton != null) {
            statsButton.setOnAction(event -> {
                event.consume();
                handleStats();
            });
        }
        if (logoutButton != null) {
            logoutButton.setOnAction(event -> {
                event.consume();
                handleLogout();
            });
        }
    }

    @FXML
    private void handleBrowse() {
        logger.info("Navigating to Matching screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHING);
    }

    @FXML
    private void handleMatches() {
        logger.info("Navigating to Matches screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHES);
    }

    @FXML
    private void handleStats() {
        logger.info("Navigating to Stats screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.STATS);
    }

    @FXML
    private void handleChat() {
        logger.info("Navigating to Chat screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.CHAT);
    }

    @FXML
    private void handleProfile() {
        logger.info("Navigating to Profile screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.PROFILE);
    }

    @FXML
    private void handleViewDailyPick() {
        logger.info("Viewing daily pick - navigating to Matching");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHING);
    }

    @FXML
    private void handleLogout() {
        boolean confirmed = UiFeedbackService.showConfirmation(
                "Confirm Logout", "Are you sure you want to log out?", "You will need to log in again to continue.");
        if (confirmed) {
            logger.info("Logging out");
            viewModel.logout();
            NavigationService.getInstance().getViewModelFactory().reset();
            NavigationService.getInstance().navigateTo(NavigationService.ViewType.LOGIN);
        }
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
