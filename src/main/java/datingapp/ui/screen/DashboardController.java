package datingapp.ui.screen;

import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiDialogs;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.DashboardViewModel;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
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
    private Label dailyPickReasonLabel;

    @FXML
    private Label dailyPickSeenLabel;

    @FXML
    private Label dailyPickEmptyLabel;

    @FXML
    private Label totalMatchesLabel;

    @FXML
    private Label completionLabel;

    @FXML
    private Label unreadBadgeLabel;

    @FXML
    private ListView<String> achievementsListView;

    @FXML
    private Button logoutButton;

    @FXML
    private Button refreshButton;

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

    @FXML
    private Button standoutsButton;

    @FXML
    private Button socialButton;

    @FXML
    private Button safetyButton;

    @FXML
    private Button notesButton;

    @FXML
    private Label profileNudgeLabel;

    @FXML
    private HBox profileNudgeBanner;

    @FXML
    private Button editProfileNudgeButton;

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
        addSubscription(viewModel.dailyPickReasonProperty().subscribe(this::updateDailyPickReason));
        addSubscription(viewModel.dailyPickSeenProperty().subscribe(this::updateDailyPickSeen));
        addSubscription(viewModel.dailyPickAvailableProperty().subscribe(this::updateDailyPickAvailability));
        addSubscription(viewModel.dailyPickEmptyMessageProperty().subscribe(this::updateDailyPickEmptyMessage));
        addSubscription(viewModel.totalMatchesProperty().subscribe(totalMatchesLabel::setText));
        addSubscription(viewModel.profileCompletionProperty().subscribe(completionLabel::setText));
        addSubscription(viewModel.profileNudgeMessageProperty().subscribe(this::updateProfileNudge));
        addSubscription(viewModel.newAchievementsAvailableProperty().subscribe(this::handleAchievementCelebration));
        addSubscription(viewModel.notificationCountProperty().subscribe(count -> {
            if (count != null && count.intValue() > 0) {
                unreadBadgeLabel.setText(String.valueOf(count));
                unreadBadgeLabel.setVisible(true);
                unreadBadgeLabel.setManaged(true);
            } else {
                unreadBadgeLabel.setVisible(false);
                unreadBadgeLabel.setManaged(false);
            }
        }));

        // Set initial values
        userNameLabel.setText(viewModel.userNameProperty().get());
        statusLabel.setText(viewModel.dailyLikesStatusProperty().get());
        dailyPickLabel.setText(viewModel.dailyPickNameProperty().get());
        updateDailyPickReason(viewModel.dailyPickReasonProperty().get());
        updateDailyPickSeen(viewModel.dailyPickSeenProperty().get());
        updateDailyPickAvailability(viewModel.dailyPickAvailableProperty().get());
        updateDailyPickEmptyMessage(viewModel.dailyPickEmptyMessageProperty().get());
        totalMatchesLabel.setText(viewModel.totalMatchesProperty().get());
        completionLabel.setText(viewModel.profileCompletionProperty().get());
        updateProfileNudge(viewModel.profileNudgeMessageProperty().get());

        // Bind achievements list
        if (achievementsListView != null) {
            achievementsListView.setItems(viewModel.getRecentAchievements());
        }

        // Load initial data (will trigger loading skeleton)
        viewModel.performRefresh();

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
        if (standoutsButton != null) {
            standoutsButton.setOnAction(event -> {
                event.consume();
                handleStandouts();
            });
        }
        if (socialButton != null) {
            socialButton.setOnAction(event -> {
                event.consume();
                handleSocial();
            });
        }
        if (safetyButton != null) {
            safetyButton.setOnAction(event -> {
                event.consume();
                handleSafety();
            });
        }
        if (notesButton != null) {
            notesButton.setOnAction(event -> {
                event.consume();
                handleNotes();
            });
        }
        if (refreshButton != null) {
            refreshButton.setOnAction(event -> {
                event.consume();
                animateRefreshButton();
                viewModel.performRefresh();
            });
        }
        if (editProfileNudgeButton != null) {
            editProfileNudgeButton.setOnAction(event -> {
                event.consume();
                handleProfile();
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
        if (!viewModel.dailyPickAvailableProperty().get()) {
            UiFeedbackService.showInfo(viewModel.dailyPickEmptyMessageProperty().get());
            return;
        }
        logger.info("Viewing daily pick - navigating to Matching");
        viewModel.markDailyPickViewed();
        if (viewModel.dailyPickUserIdProperty().get() != null) {
            NavigationService.getInstance()
                    .setNavigationContext(
                            NavigationService.ViewType.MATCHING,
                            viewModel.dailyPickUserIdProperty().get());
        }
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHING);
    }

    @FXML
    private void handleStandouts() {
        logger.info("Navigating to Standouts screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.STANDOUTS);
    }

    @FXML
    private void handleSocial() {
        logger.info("Navigating to Social screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.SOCIAL);
    }

    @FXML
    private void handleSafety() {
        logger.info("Navigating to Safety screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.SAFETY);
    }

    @FXML
    private void handleNotes() {
        logger.info("Navigating to Notes screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.NOTES);
    }

    @FXML
    private void handleLogout() {
        UiDialogs.confirmAndExecute(
                "Confirm Logout",
                "Are you sure you want to log out?",
                "You will need to log in again to continue.",
                () -> {
                    logger.info("Logging out");
                    viewModel.logout();
                    NavigationService.getInstance().getViewModelFactory().reset();
                    NavigationService.getInstance().navigateTo(NavigationService.ViewType.LOGIN);
                },
                null);
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

    private void animateRefreshButton() {
        if (refreshButton == null) {
            return;
        }
        Node graphic = refreshButton.getGraphic();
        if (graphic == null) {
            return;
        }
        RotateTransition rotate = new RotateTransition(Duration.millis(600), graphic);
        rotate.setByAngle(360);
        rotate.play();
    }

    private void updateProfileNudge(String message) {
        if (profileNudgeLabel == null) {
            return;
        }
        String resolvedMessage = message == null ? "" : message.trim();
        profileNudgeLabel.setText(resolvedMessage);
        boolean visible = !resolvedMessage.isEmpty();
        if (profileNudgeBanner != null) {
            profileNudgeBanner.setVisible(visible);
            profileNudgeBanner.setManaged(visible);
        }
        profileNudgeLabel.setVisible(visible);
        profileNudgeLabel.setManaged(visible);
        if (editProfileNudgeButton != null) {
            editProfileNudgeButton.setVisible(visible);
            editProfileNudgeButton.setManaged(visible);
        }
    }

    private void updateDailyPickReason(String reason) {
        if (dailyPickReasonLabel == null) {
            return;
        }
        String resolvedReason = reason == null ? "" : reason.trim();
        boolean visible = !resolvedReason.isEmpty();
        dailyPickReasonLabel.setText(resolvedReason);
        dailyPickReasonLabel.setVisible(visible);
        dailyPickReasonLabel.setManaged(visible);
    }

    private void updateDailyPickSeen(Boolean seen) {
        if (dailyPickSeenLabel == null) {
            return;
        }
        boolean visible = Boolean.TRUE.equals(seen);
        dailyPickSeenLabel.setVisible(visible);
        dailyPickSeenLabel.setManaged(visible);
    }

    private void updateDailyPickAvailability(Boolean available) {
        if (dailyPickButton == null) {
            return;
        }
        dailyPickButton.setDisable(!Boolean.TRUE.equals(available));
    }

    private void updateDailyPickEmptyMessage(String message) {
        if (dailyPickEmptyLabel == null) {
            return;
        }
        boolean visible = !viewModel.dailyPickAvailableProperty().get();
        dailyPickEmptyLabel.setText(message == null ? "" : message);
        dailyPickEmptyLabel.setVisible(visible);
        dailyPickEmptyLabel.setManaged(visible);
    }

    private void handleAchievementCelebration(Boolean shouldCelebrate) {
        if (!Boolean.TRUE.equals(shouldCelebrate)) {
            return;
        }
        StackPane rootStack = NavigationService.getInstance().getRootStack();
        if (rootStack == null || rootPane == null) {
            return;
        }

        List<Achievement> toShow = viewModel.getNewlyUnlockedAchievements();
        viewModel.markAchievementsSeen();

        Canvas canvas = new Canvas();
        canvas.setMouseTransparent(true);
        canvas.widthProperty().bind(rootStack.widthProperty());
        canvas.heightProperty().bind(rootStack.heightProperty());
        registerOverlay(canvas);

        UiAnimations.ConfettiAnimation confettiAnimation = new UiAnimations.ConfettiAnimation();
        confettiAnimation.play(canvas);

        PauseTransition removalDelay = new PauseTransition(Duration.seconds(3));
        removalDelay.setOnFinished(event -> {
            confettiAnimation.stop();
            rootStack.getChildren().remove(canvas);
            showAchievementPopups(rootStack, toShow);
        });
        removalDelay.play();
    }

    private void showAchievementPopups(StackPane rootStack, List<Achievement> achievements) {
        for (int i = 0; i < achievements.size(); i++) {
            Achievement achievement = achievements.get(i);
            PauseTransition stagger = new PauseTransition(Duration.millis(i * 600.0));
            stagger.setOnFinished(e -> showSingleAchievementPopup(rootStack, achievement));
            stagger.play();
        }
    }

    private void showSingleAchievementPopup(StackPane rootStack, Achievement achievement) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/achievement_popup.fxml"));
            StackPane popupRoot = loader.load();
            MilestonePopupController popup = loader.getController();
            rootStack.getChildren().add(popupRoot);
            popup.showAchievement(achievement);
        } catch (IOException e) {
            logger.error( // NOPMD GuardLogStatement
                    "Failed to load achievement popup for {}", achievement.getDisplayName(), e);
            UiFeedbackService.showWarning("We couldn\'t show an achievement popup, but your progress was saved.");
        }
    }
}
