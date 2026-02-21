package datingapp.ui.screen;

import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.ui.ImageCache;
import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.UiUtils;
import datingapp.ui.viewmodel.MatchingViewModel;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Matching screen (matching.fxml).
 * Handles candidate display, swipe actions, and match popup.
 * Extends BaseController for automatic subscription cleanup.
 */
public class MatchingController extends BaseController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(MatchingController.class);

    @FXML
    private BorderPane rootPane;

    @FXML
    private VBox candidateCard;

    @FXML
    private ImageView candidatePhoto;

    @FXML
    private Button prevPhotoButton;

    @FXML
    private Button nextPhotoButton;

    @FXML
    private Label photoIndicatorLabel;

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
    private Button undoButton;

    @FXML
    private HBox actionButtonsContainer;

    @FXML
    private StackPane cardStackContainer;

    @FXML
    private Label likeOverlay;

    @FXML
    private Label passOverlay;

    @FXML
    private VBox expandPreferencesCard;

    @FXML
    private VBox checkLikesCard;

    @FXML
    private VBox improveProfileCard;

    // Swipe gesture state
    private double dragStartX;

    private static final double DRAG_THRESHOLD = 150;

    private final MatchingViewModel viewModel;

    public MatchingController(MatchingViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
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

        // Update UI when current candidate changes - using Subscription API
        addSubscription(viewModel.currentCandidateProperty().subscribe(this::updateCandidateUI));

        // Listen for matches to show popup - using Subscription API
        addSubscription(viewModel.matchedUserProperty().subscribe(user -> {
            if (user != null) {
                showMatchPopup(user, viewModel.lastMatchProperty().get());
            }
        }));

        // Initialize the ViewModel with current user from UISession
        viewModel.initialize();

        updateCandidateUI(viewModel.currentCandidateProperty().get());

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);

        // Suppress unused warning for logger
        if (logger.isTraceEnabled()) {
            logger.trace("MatchingController initialized");
        }

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        wireActionHandlers();

        // Bind Photo URL
        addSubscription(viewModel.currentCandidatePhotoUrlProperty().subscribe(url -> {
            if (url != null && !url.isBlank()) {
                candidatePhoto.setImage(ImageCache.getImage(url, 400, 350));
            } else {
                candidatePhoto.setImage(null);
            }
        }));

        addSubscription(
                viewModel.currentCandidatePhotoUrlsProperty().subscribe(urls -> updatePhotoControlsVisibility()));
        addSubscription(
                viewModel.currentCandidatePhotoIndexProperty().subscribe(idx -> updatePhotoControlsVisibility()));

        // Setup swipe gestures
        setupSwipeGestures();
    }

    private void updatePhotoControlsVisibility() {
        if (viewModel.currentCandidatePhotoUrlsProperty().get() == null) {
            return;
        }
        int size = viewModel.currentCandidatePhotoUrlsProperty().get().size();
        if (size > 1) {
            prevPhotoButton.setVisible(true);
            prevPhotoButton.setManaged(true);
            nextPhotoButton.setVisible(true);
            nextPhotoButton.setManaged(true);
            photoIndicatorLabel.setVisible(true);
            photoIndicatorLabel.setManaged(true);
            photoIndicatorLabel.setText(
                    (viewModel.currentCandidatePhotoIndexProperty().get() + 1) + "/" + size);
        } else {
            prevPhotoButton.setVisible(false);
            prevPhotoButton.setManaged(false);
            nextPhotoButton.setVisible(false);
            nextPhotoButton.setManaged(false);
            photoIndicatorLabel.setVisible(false);
            photoIndicatorLabel.setManaged(false);
        }
    }

    /** Setup drag-to-swipe gestures on the candidate card. */
    private void setupSwipeGestures() {
        candidateCard.setOnMousePressed(e -> {
            dragStartX = e.getSceneX();

            candidateCard.setCursor(Cursor.CLOSED_HAND);
        });

        candidateCard.setOnMouseDragged(e -> {
            double deltaX = e.getSceneX() - dragStartX;
            double rotation = deltaX * 0.04;

            candidateCard.setTranslateX(deltaX);
            candidateCard.setRotate(rotation);

            // Update overlay based on drag distance
            double progress = Math.min(Math.abs(deltaX) / DRAG_THRESHOLD, 1.0);

            if (deltaX > 30) {
                showLikeOverlay(progress);
                hidePassOverlay();
            } else if (deltaX < -30) {
                showPassOverlay(progress);
                hideLikeOverlay();
            } else {
                hideAllOverlays();
            }
        });

        candidateCard.setOnMouseReleased(e -> {
            candidateCard.setCursor(Cursor.HAND);
            double deltaX = e.getSceneX() - dragStartX;

            if (Math.abs(deltaX) > DRAG_THRESHOLD) {
                if (deltaX > 0) {
                    animateCardExit(true, this::performLike);
                } else {
                    animateCardExit(false, this::performPass);
                }
            } else {
                animateSnapBack();
            }
        });

        candidateCard.setCursor(Cursor.HAND);
    }

    private void showLikeOverlay(double progress) {
        likeOverlay.setVisible(true);
        likeOverlay.setManaged(true);
        likeOverlay.setOpacity(progress);
    }

    private void hideLikeOverlay() {
        likeOverlay.setVisible(false);
        likeOverlay.setManaged(false);
    }

    private void showPassOverlay(double progress) {
        passOverlay.setVisible(true);
        passOverlay.setManaged(true);
        passOverlay.setOpacity(progress);
    }

    private void hidePassOverlay() {
        passOverlay.setVisible(false);
        passOverlay.setManaged(false);
    }

    private void hideAllOverlays() {
        hideLikeOverlay();
        hidePassOverlay();
    }

    private void animateCardExit(boolean toRight, Runnable onComplete) {
        double targetX = toRight ? 800 : -800;
        double targetRotation = toRight ? 30 : -30;

        TranslateTransition translate = new TranslateTransition(Duration.millis(300), candidateCard);
        translate.setToX(targetX);
        translate.setInterpolator(Interpolator.EASE_IN);

        RotateTransition rotate = new RotateTransition(Duration.millis(300), candidateCard);
        rotate.setToAngle(targetRotation);

        FadeTransition fade = new FadeTransition(Duration.millis(300), candidateCard);
        fade.setToValue(0);

        ParallelTransition exit = new ParallelTransition(translate, rotate, fade);
        exit.setOnFinished(e -> {
            e.consume();
            resetCardPosition();
            hideAllOverlays();
            onComplete.run();
        });
        exit.play();
    }

    private void animateSnapBack() {
        TranslateTransition translate = new TranslateTransition(Duration.millis(200), candidateCard);
        translate.setToX(0);
        translate.setInterpolator(Interpolator.EASE_OUT);

        RotateTransition rotate = new RotateTransition(Duration.millis(200), candidateCard);
        rotate.setToAngle(0);

        ParallelTransition snapBack = new ParallelTransition(translate, rotate);
        snapBack.setOnFinished(e -> {
            e.consume();
            hideAllOverlays();
        });
        snapBack.play();
    }

    private void resetCardPosition() {
        candidateCard.setTranslateX(0);
        candidateCard.setRotate(0);
        candidateCard.setOpacity(1);
    }

    private void performLike() {
        viewModel.like();
    }

    private void performPass() {
        viewModel.pass();
    }

    /** Setup keyboard shortcuts for quick actions. */
    private void setupKeyboardShortcuts() {
        rootPane.setOnKeyPressed(e -> {
            // Only handle if candidate is visible
            if (!candidateCard.isVisible()) {
                return;
            }

            if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                handleUndo();
                return;
            }
            switch (e.getCode()) {
                case LEFT -> handlePass();
                case RIGHT -> handleLike();
                case UP -> handleSuperLike();
                case ESCAPE -> handleBack();
                default -> {
                    /* no action */
                }
            }
        });

        // Ensure root pane can receive keyboard events
        rootPane.setFocusTraversable(true);
        Platform.runLater(rootPane::requestFocus);
    }

    private void wireActionHandlers() {
        if (undoButton != null) {
            undoButton.setOnAction(event -> {
                event.consume();
                handleUndo();
            });
            undoButton.disableProperty().bind(viewModel.loadingProperty());
        }
        if (cardStackContainer != null) {
            cardStackContainer.setOnMouseClicked(event -> {
                event.consume();
                rootPane.requestFocus();
            });
        }
        if (expandPreferencesCard != null) {
            expandPreferencesCard.setOnMouseClicked(event -> {
                event.consume();
                handleExpandPreferences();
            });
        }
        if (checkLikesCard != null) {
            checkLikesCard.setOnMouseClicked(event -> {
                event.consume();
                handleCheckLikes();
            });
        }
        if (improveProfileCard != null) {
            improveProfileCard.setOnMouseClicked(event -> {
                event.consume();
                handleImproveProfile();
            });
        }
    }

    /** Super like action - triggered by button or UP key. */
    @FXML
    private void handleSuperLike() {
        logInfo("Super Like triggered");
        // Pulse the card for micro-interaction feedback
        UiAnimations.pulseScale(candidateCard);
        // For now, acts like a regular like (super like logic to be added later)
        viewModel.like();
    }

    private void updateCandidateUI(User user) {
        if (user == null) {
            return;
        }

        @SuppressWarnings("deprecation") // UI display - system timezone appropriate
        int age = user.getAge();
        nameLabel.setText(user.getName() + ", " + age);
        bioLabel.setText(user.getBio() != null ? user.getBio() : "No bio provided.");
        distanceLabel.setText("📍 " + viewModel.getDistanceDisplay(user));
        matchScoreLabel.setText("⭐ " + viewModel.getCompatibilityDisplay(user));
    }

    /**
     * Shows the "IT'S A MATCH!" popup dialog.
     */
    private void showMatchPopup(User matchedUser, Match match) {
        logInfo("Showing match popup for: {}", matchedUser.getName());
        if (match != null) {
            logDebug("Match id: {}", match.getId());
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("It's a Match!");

        // Apply theme
        String themeStylesheet = resolveStylesheet("/css/theme.css");
        if (themeStylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(themeStylesheet);
        }
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
            if (Objects.equals(response, sendMessageBtn)) {
                // Navigate to chat with this match
                NavigationService navigationService = NavigationService.getInstance();
                if (matchedUser != null) {
                    navigationService.setNavigationContext(NavigationService.ViewType.CHAT, matchedUser.getId());
                }
                navigationService.navigateTo(NavigationService.ViewType.CHAT);
            }
            // Clear the match notification
            viewModel.clearMatchNotification();
        });
    }

    @FXML
    private void handleLike() {
        // Pulse the card for micro-interaction feedback
        UiAnimations.pulseScale(candidateCard);
        viewModel.like();
    }

    @FXML
    private void handlePass() {
        // Pulse the card for micro-interaction feedback
        UiAnimations.pulseScale(candidateCard);
        viewModel.pass();
    }

    @SuppressWarnings("unused")
    @FXML
    private void handlePrevPhoto() {
        viewModel.showPreviousPhoto();
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleNextPhoto() {
        viewModel.showNextPhoto();
    }

    @FXML
    private void handleUndo() {
        viewModel.undo();
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleBlock() {
        User candidate = viewModel.currentCandidateProperty().get();
        if (candidate == null) {
            return;
        }
        boolean confirmed = UiFeedbackService.showConfirmation(
                "Block User", "Block " + candidate.getName() + "?", "They will no longer appear in your feed.");
        if (confirmed) {
            viewModel.blockCandidate(candidate.getId());
            UiFeedbackService.showSuccess(candidate.getName() + " has been blocked.");
        }
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleReport() {
        User candidate = viewModel.currentCandidateProperty().get();
        if (candidate == null) {
            return;
        }
        showReportDialog(candidate);
    }

    private void showReportDialog(User candidate) {
        Dialog<Report.Reason> dialog = new Dialog<>();
        dialog.setTitle("Report User");
        dialog.setHeaderText("Report " + candidate.getName());

        String themeStylesheet = resolveStylesheet("/css/theme.css");
        if (themeStylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(themeStylesheet);
        }
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        ChoiceBox<Report.Reason> reasonBox = new ChoiceBox<>();
        reasonBox.getItems().addAll(Report.Reason.values());
        reasonBox.setConverter(UiUtils.createEnumStringConverter(r -> r.name().replace('_', ' ')));
        reasonBox.setValue(Report.Reason.INAPPROPRIATE_CONTENT);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20;");
        content.getChildren().addAll(new Label("Select a reason:"), reasonBox);
        dialog.getDialogPane().setContent(content);

        ButtonType reportBtn = new ButtonType("Report", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(reportBtn, cancelBtn);

        dialog.setResultConverter(
                bt -> bt == reportBtn ? reasonBox.getValue() : null); // NOPMD CompareObjectsWithEquals
        dialog.showAndWait().ifPresent(reason -> {
            viewModel.reportCandidate(candidate.getId(), reason, null, true);
            UiFeedbackService.showSuccess(candidate.getName() + " has been reported.");
        });
    }

    @FXML
    private void handleExpandPreferences() {
        logInfo("User clicked Expand Preferences - navigating to Profile settings");
        // Navigate to filter/preferences screen
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.PREFERENCES);
    }

    @FXML
    private void handleCheckLikes() {
        logInfo("User clicked Check Likes - navigating to Matches");
        // Navigate to Matches screen where they can see who liked them
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHES);
    }

    @FXML
    private void handleImproveProfile() {
        logInfo("User clicked Improve Profile - navigating to Profile");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.PROFILE);
    }

    private String resolveStylesheet(String path) {
        URL resource = getClass().getResource(path);
        if (resource == null) {
            logWarn("Stylesheet not found: {}", path);
            return null;
        }
        return resource.toExternalForm();
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    private void logDebug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, args);
        }
    }
}
