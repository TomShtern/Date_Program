package datingapp.ui.screen;

import datingapp.app.support.UserPresentationSupport;
import datingapp.core.i18n.I18n;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.ui.ImageCache;
import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiDialogs;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.MatchingViewModel;
import java.net.URL;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.UUID;
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
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
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
    private TextArea noteTextArea;

    @FXML
    private Label noteStatusLabel;

    @FXML
    private Button saveNoteButton;

    @FXML
    private Button deleteNoteButton;

    @FXML
    private Label distanceLabel;

    @FXML
    private Label matchScoreLabel;

    @FXML
    private Button viewFullProfileButton;

    @FXML
    private VBox noCandidatesContainer;

    @FXML
    private Label noCandidatesHeading;

    @FXML
    private Label noCandidatesBody;

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
    private boolean cardTransitionInProgress;
    private boolean firstCandidateRendered;

    private static final double DRAG_THRESHOLD = 150;

    private final MatchingViewModel viewModel;
    private final ZoneId userTimeZone;

    public MatchingController(MatchingViewModel viewModel, ZoneId userTimeZone) {
        this.viewModel = viewModel;
        this.userTimeZone = Objects.requireNonNull(userTimeZone, "userTimeZone cannot be null");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        bindVisibility();
        bindViewModelState();
        UUID selectedCandidateId = consumeSelectedCandidateId();
        viewModel.initialize(selectedCandidateId);

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
        bindPhotoState();
        setupSwipeGestures();
    }

    private void bindVisibility() {
        candidateCard.visibleProperty().bind(viewModel.hasMoreCandidatesProperty());
        candidateCard.managedProperty().bind(viewModel.hasMoreCandidatesProperty());
        noCandidatesContainer
                .visibleProperty()
                .bind(viewModel.hasMoreCandidatesProperty().not());
        noCandidatesContainer
                .managedProperty()
                .bind(viewModel.hasMoreCandidatesProperty().not());
        actionButtonsContainer.visibleProperty().bind(viewModel.hasMoreCandidatesProperty());
        actionButtonsContainer.managedProperty().bind(viewModel.hasMoreCandidatesProperty());
    }

    private void bindViewModelState() {
        addSubscription(viewModel.currentCandidateProperty().subscribe(this::updateCandidateUI));
        addSubscription(viewModel.hasMoreCandidatesProperty().subscribe(hasMore -> {
            if (!Boolean.TRUE.equals(hasMore)) {
                finishCardTransition();
            }
        }));
        bindNoteState();
        addSubscription(viewModel.infoMessageProperty().subscribe(this::showInfoMessage));
        addSubscription(viewModel.matchedUserProperty().subscribe(this::showMatchPopupIfPresent));
        addSubscription(viewModel.locationMissingProperty().subscribe(this::updateEmptyStateCopy));
    }

    private void bindNoteState() {
        if (noteTextArea != null) {
            noteTextArea.textProperty().bindBidirectional(viewModel.noteContentProperty());
        }
        if (noteStatusLabel != null) {
            noteStatusLabel.textProperty().bind(viewModel.noteStatusMessageProperty());
        }
        if (saveNoteButton != null) {
            saveNoteButton
                    .disableProperty()
                    .bind(viewModel.currentCandidateProperty().isNull().or(viewModel.noteBusyProperty()));
        }
        if (deleteNoteButton != null) {
            deleteNoteButton
                    .disableProperty()
                    .bind(viewModel.currentCandidateProperty().isNull().or(viewModel.noteBusyProperty()));
        }
    }

    private void bindPhotoState() {
        addSubscription(viewModel.currentCandidatePhotoUrlProperty().subscribe(this::updateCandidatePhoto));
        addSubscription(
                viewModel.currentCandidatePhotoUrlsProperty().subscribe(urls -> updatePhotoControlsVisibility()));
        addSubscription(
                viewModel.currentCandidatePhotoIndexProperty().subscribe(idx -> updatePhotoControlsVisibility()));
    }

    private UUID consumeSelectedCandidateId() {
        return navigationService()
                .consumeNavigationContext(NavigationService.ViewType.MATCHING, UUID.class)
                .orElse(null);
    }

    private void showInfoMessage(String message) {
        if (message != null && !message.isBlank()) {
            UiFeedbackService.showInfo(message);
            viewModel.clearInfoMessage();
        }
    }

    private void showMatchPopupIfPresent(User user) {
        if (user != null) {
            showMatchPopup(user, viewModel.lastMatchProperty().get());
        }
    }

    private void updateEmptyStateCopy(boolean locationMissing) {
        if (locationMissing) {
            noCandidatesHeading.setText("Location not set");
            noCandidatesBody.setText("Add your location in your profile to discover people near you.");
            return;
        }
        noCandidatesHeading.setText("No more people around you!");
        noCandidatesBody.setText("You've seen everyone nearby. Try expanding your search or check back later!");
    }

    private void updateCandidatePhoto(String url) {
        if (url != null && !url.isBlank()) {
            candidatePhoto.setImage(ImageCache.getImage(url, 400, 350));
            preloadAdjacentCandidatePhotos();
            return;
        }
        candidatePhoto.setImage(null);
    }

    private void preloadAdjacentCandidatePhotos() {
        List<String> photoUrls = viewModel.currentCandidatePhotoUrlsProperty().get();
        if (photoUrls == null || photoUrls.size() < 2) {
            return;
        }
        int currentIndex = viewModel.currentCandidatePhotoIndexProperty().get();
        preloadPhotoAtIndex(photoUrls, currentIndex - 1, 400, 350);
        preloadPhotoAtIndex(photoUrls, currentIndex + 1, 400, 350);
    }

    private static void preloadPhotoAtIndex(List<String> photoUrls, int index, double width, double height) {
        if (index < 0 || index >= photoUrls.size()) {
            return;
        }
        String photoUrl = photoUrls.get(index);
        if (photoUrl != null && !photoUrl.isBlank()) {
            ImageCache.preload(photoUrl, width, height);
        }
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
        if (cardTransitionInProgress) {
            return;
        }
        cardTransitionInProgress = true;
        setCardInteractionEnabled(false);
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
        candidateCard.setScaleX(1);
        candidateCard.setScaleY(1);
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

            // Ignore swipe shortcuts when editing text (note editor, etc)
            Object target = e.getTarget();
            if (target instanceof TextInputControl) {
                return;
            }

            if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                handleUndo();
                e.consume();
                return;
            }
            switch (e.getCode()) {
                case LEFT -> {
                    handlePass();
                    e.consume();
                }
                case RIGHT -> {
                    handleLike();
                    e.consume();
                }
                case UP -> {
                    handleSuperLike();
                    e.consume();
                }
                case ESCAPE -> {
                    handleBack();
                    e.consume();
                }
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
            undoButton
                    .disableProperty()
                    .bind(viewModel
                            .loadingProperty()
                            .or(viewModel.undoAvailableProperty().not()));
            addSubscription(viewModel.undoCountdownSecondsProperty().subscribe(seconds -> {
                if (undoButton.getTooltip() == null) {
                    undoButton.setTooltip(new Tooltip());
                }
                int remainingSeconds = seconds.intValue();
                if (remainingSeconds > 0) {
                    undoButton.getTooltip().setText("Undo last action (Ctrl+Z) - " + remainingSeconds + "s remaining");
                } else {
                    undoButton.getTooltip().setText("Undo last action (Ctrl+Z)");
                }
            }));
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
        if (viewFullProfileButton != null) {
            viewFullProfileButton
                    .disableProperty()
                    .bind(viewModel.currentCandidateProperty().isNull());
        }
    }

    /** Super like action - triggered by button or UP key. */
    @FXML
    private void handleSuperLike() {
        logInfo("Super Like triggered");
        // Pulse the card for micro-interaction feedback
        UiAnimations.pulseScale(candidateCard);
        viewModel.superLike();
    }

    private void updateCandidateUI(User user) {
        if (user == null) {
            return;
        }

        int age = UserPresentationSupport.safeAge(user, userTimeZone);
        nameLabel.setText(user.getName() + ", " + age);
        bioLabel.setText(user.getBio() != null ? user.getBio() : "No bio provided.");
        distanceLabel.setText("📍 " + viewModel.getDistanceDisplay(user));
        matchScoreLabel.setText("⭐ " + viewModel.getCompatibilityDisplay(user));

        if (!firstCandidateRendered) {
            firstCandidateRendered = true;
            finishCardTransition();
            return;
        }

        if (cardTransitionInProgress) {
            playCardEntranceAnimation();
        }
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
        dialog.setTitle(I18n.text("ui.match.dialog.title"));

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

        Label titleLabel = new Label(I18n.text("ui.match.dialog.title"));
        titleLabel.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: -fx-accent-super;");

        org.kordamp.ikonli.javafx.FontIcon heartIcon = new org.kordamp.ikonli.javafx.FontIcon("mdi2h-heart-pulse");
        heartIcon.setIconSize(80);
        heartIcon.setIconColor(javafx.scene.paint.Color.web("#f43f5e")); // Rose 500

        Label messageLabel = new Label(I18n.text("ui.match.dialog.message", matchedUser.getName()));
        messageLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: -fx-text-secondary;");

        content.getChildren().addAll(heartIcon, titleLabel, messageLabel);
        dialog.getDialogPane().setContent(content);

        // Add entry animation to the dialog content
        UiAnimations.fadeIn(content, 600);

        // Add buttons
        ButtonType sendMessageBtn = new ButtonType(I18n.text("ui.match.dialog.send"), ButtonBar.ButtonData.OK_DONE);
        ButtonType keepSwipingBtn =
                new ButtonType(I18n.text("ui.match.dialog.keep_swiping"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(sendMessageBtn, keepSwipingBtn);

        // Style buttons
        Button sendBtn = (Button) dialog.getDialogPane().lookupButton(sendMessageBtn);
        sendBtn.getStyleClass().add("button");
        sendBtn.setStyle("-fx-background-color: -fx-accent-super;");

        dialog.showAndWait().ifPresent(response -> {
            if (Objects.equals(response, sendMessageBtn)) {
                // Navigate to chat with this match
                NavigationService navigationService = navigationService();
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
        animateCardExit(true, this::performLike);
    }

    @FXML
    private void handlePass() {
        animateCardExit(false, this::performPass);
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
        UiDialogs.confirmAndExecute(
                "Block User",
                "Block " + candidate.getName() + "?",
                "They will no longer appear in your feed.",
                () -> viewModel.blockCandidate(candidate.getId()),
                candidate.getName() + " has been blocked.");
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleReport() {
        User candidate = viewModel.currentCandidateProperty().get();
        if (candidate == null) {
            return;
        }
        UiDialogs.showReportDialog(candidate, (reason, desc) -> {
            viewModel.reportCandidate(candidate.getId(), reason, desc, true);
            UiFeedbackService.showSuccess(candidate.getName() + " has been reported.");
        });
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleSaveNote() {
        viewModel.saveCurrentCandidateNote();
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleDeleteNote() {
        viewModel.deleteCurrentCandidateNote();
    }

    @FXML
    private void handleExpandPreferences() {
        logInfo("User clicked Expand Preferences - navigating to Profile settings");
        // Navigate to filter/preferences screen
        navigationService().navigateTo(NavigationService.ViewType.PREFERENCES);
    }

    @FXML
    private void handleCheckLikes() {
        logInfo("User clicked Check Likes - navigating to Matches");
        // Navigate to Matches screen where they can see who liked them
        navigationService().navigateTo(NavigationService.ViewType.MATCHES);
    }

    @FXML
    private void handleImproveProfile() {
        logInfo("User clicked Improve Profile - navigating to Profile");
        navigationService().navigateTo(NavigationService.ViewType.PROFILE);
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleViewFullProfile() {
        User candidate = viewModel.currentCandidateProperty().get();
        if (candidate == null) {
            return;
        }
        NavigationService nav = navigationService();
        nav.setNavigationContext(NavigationService.ViewType.PROFILE_VIEW, candidate.getId());
        nav.navigateTo(NavigationService.ViewType.PROFILE_VIEW);
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

    private void playCardEntranceAnimation() {
        candidateCard.setOpacity(0);
        candidateCard.setScaleX(0.85);
        candidateCard.setScaleY(0.85);

        FadeTransition fade = new FadeTransition(Duration.millis(400), candidateCard);
        fade.setFromValue(0);
        fade.setToValue(1);

        javafx.animation.ScaleTransition scale =
                new javafx.animation.ScaleTransition(Duration.millis(400), candidateCard);
        scale.setFromX(0.85);
        scale.setFromY(0.85);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition entrance = new ParallelTransition(fade, scale);
        entrance.setOnFinished(event -> finishCardTransition());
        entrance.play();
    }

    private void finishCardTransition() {
        cardTransitionInProgress = false;
        setCardInteractionEnabled(true);
        resetCardPosition();
    }

    private void setCardInteractionEnabled(boolean enabled) {
        candidateCard.setDisable(!enabled);
        actionButtonsContainer.setDisable(!enabled);
    }
}
