package datingapp.ui.controller;

import datingapp.core.AppClock;
import datingapp.ui.NavigationService;
import datingapp.ui.util.Toast;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.viewmodel.MatchesViewModel;
import datingapp.ui.viewmodel.MatchesViewModel.LikeCardData;
import datingapp.ui.viewmodel.MatchesViewModel.MatchCardData;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.ResourceBundle;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Matches screen (matches.fxml).
 * Displays all active matches for the current user with premium animations.
 * Extends BaseController for automatic subscription cleanup.
 */
public class MatchesController extends BaseController implements Initializable {

    private static final String START_BROWSING_LABEL = "Start Browsing";
    private static final String ICON_HEART = "mdi2h-heart";
    private static final String COLOR_PINK = "#f43f5e";
    private static final Logger logger = LoggerFactory.getLogger(MatchesController.class);
    private static final Random RANDOM = new Random();

    private enum Section {
        MATCHES,
        LIKES_YOU,
        YOU_LIKED
    }

    @FXML
    private BorderPane rootPane;

    @FXML
    private FontIcon headerIcon;

    @FXML
    private Label headerTitleLabel;

    @FXML
    private Label matchCountLabel;

    @FXML
    private ToggleGroup sectionGroup;

    @FXML
    private ToggleButton matchesTabButton;

    @FXML
    private ToggleButton likesYouTabButton;

    @FXML
    private ToggleButton youLikedTabButton;

    @FXML
    private Label matchesTabCountLabel;

    @FXML
    private Label likesYouTabCountLabel;

    @FXML
    private Label youLikedTabCountLabel;

    @FXML
    private FlowPane matchesFlow;

    @FXML
    private VBox emptyStateContainer;

    @FXML
    private FontIcon emptyStateIcon;

    @FXML
    private Label emptyTitleLabel;

    @FXML
    private Label emptySubtitleLabel;

    @FXML
    private Label emptyHintLabel;

    @FXML
    private Button emptyActionButton;

    @FXML
    private Button backButton;

    private final MatchesViewModel viewModel;
    private final LikesTabRenderer likesTabRenderer;
    private Pane particleLayer;
    private boolean emptyStateAnimated;
    private Section currentSection = Section.MATCHES;

    public MatchesController(MatchesViewModel viewModel) {
        this.viewModel = viewModel;
        this.likesTabRenderer = new LikesTabRenderer(viewModel);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(Toast::showError);

        // Initialize viewmodel
        viewModel.initialize();

        setupSectionTabs();
        bindTabCounts();

        // Populate initial cards
        populateCards();

        // Listen for changes using Subscription API
        addSubscription(viewModel.getMatches().subscribe(this::onSectionDataChanged));
        addSubscription(viewModel.getLikesReceived().subscribe(this::onSectionDataChanged));
        addSubscription(viewModel.getLikesSent().subscribe(this::onSectionDataChanged));

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);

        // Start empty state animations if visible
        startEmptyStateAnimations();

        wireNavigationButtons();
    }

    private void onSectionDataChanged() {
        populateCards();
    }

    private void setupSectionTabs() {
        matchesTabButton.setUserData(Section.MATCHES);
        likesYouTabButton.setUserData(Section.LIKES_YOU);
        youLikedTabButton.setUserData(Section.YOU_LIKED);

        ToggleButton defaultTab = resolveDefaultTab();
        defaultTab.setSelected(true);
        currentSection = (Section) defaultTab.getUserData();
        updateHeader();

        addSubscription(sectionGroup.selectedToggleProperty().subscribe(newToggle -> {
            if (newToggle == null) {
                matchesTabButton.setSelected(true);
                return;
            }
            currentSection = (Section) newToggle.getUserData();
            updateHeader();
            populateCards();
        }));
    }

    private ToggleButton resolveDefaultTab() {
        if (!viewModel.getLikesReceived().isEmpty()) {
            return likesYouTabButton;
        }
        if (!viewModel.getMatches().isEmpty()) {
            return matchesTabButton;
        }
        if (!viewModel.getLikesSent().isEmpty()) {
            return youLikedTabButton;
        }
        return matchesTabButton;
    }

    private void bindTabCounts() {
        matchesTabCountLabel.textProperty().bind(viewModel.matchCountProperty().asString());
        likesYouTabCountLabel
                .textProperty()
                .bind(viewModel.likesReceivedCountProperty().asString());
        youLikedTabCountLabel
                .textProperty()
                .bind(viewModel.likesSentCountProperty().asString());
    }

    /** Start animations for the empty state. */
    private void startEmptyStateAnimations() {
        if (!emptyStateContainer.isVisible()) {
            return;
        }

        // Create particle layer for floating hearts
        createFloatingHeartsBackground();

        // Animate the main icon with pronounced effects
        if (!emptyStateAnimated) {
            animateMainIcon();
            animateEmptyStateContainer();
            emptyStateAnimated = true;
        }

        // Ensure icon stays updated for the active section
        updateEmptyStateContent();
    }

    /** Create floating heart particles in the background. */
    private void createFloatingHeartsBackground() {
        if (particleLayer != null) {
            return;
        }

        // Create a particle layer as overlay
        particleLayer = new Pane();
        particleLayer.setMouseTransparent(true);
        particleLayer.setPickOnBounds(false);

        // Add to root pane behind everything
        if (rootPane.getCenter() instanceof javafx.scene.control.ScrollPane scrollPane) {
            StackPane wrapper = new StackPane();
            wrapper.getChildren().addAll(particleLayer, scrollPane);
            rootPane.setCenter(wrapper);
        }

        // Spawn floating hearts periodically
        Timeline spawnTimeline = new Timeline(new KeyFrame(Duration.millis(800), e -> {
            e.consume();
            spawnFloatingHeart();
        }));
        spawnTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        spawnTimeline.play();
        trackAnimation(spawnTimeline);

        // Spawn initial batch
        for (int i = 0; i < 5; i++) {
            PauseTransition delay = new PauseTransition(Duration.millis(RANDOM.nextInt(500)));
            delay.setOnFinished(e -> {
                e.consume();
                spawnFloatingHeart();
            });
            delay.play();
        }
    }

    /** Maximum number of heart particles to prevent unbounded growth. */
    private static final int MAX_FLOATING_HEARTS = 20;

    /** Spawn a single floating heart particle. */
    private void spawnFloatingHeart() {
        if (particleLayer == null) {
            return;
        }
        // Cap particle count to prevent unbounded growth (M-15)
        if (particleLayer.getChildren().size() >= MAX_FLOATING_HEARTS) {
            return;
        }

        FontIcon heart = new FontIcon(ICON_HEART);
        heart.setIconSize(12 + RANDOM.nextInt(16)); // 12-28 size
        heart.setIconColor(Color.web(COLOR_PINK, 0.3 + RANDOM.nextDouble() * 0.4)); // 30-70% opacity
        heart.setOpacity(0);

        // Random horizontal position
        double startX = RANDOM.nextDouble() * 400 + 200;
        double startY = 600; // Start from bottom
        heart.setLayoutX(startX);
        heart.setLayoutY(startY);

        particleLayer.getChildren().add(heart);

        // Float up animation
        TranslateTransition floatUp =
                new TranslateTransition(Duration.millis(4000 + (double) RANDOM.nextInt(3000)), heart);
        floatUp.setFromY(0);
        floatUp.setToY(-500 - (double) RANDOM.nextInt(200));
        floatUp.setInterpolator(Interpolator.EASE_OUT);

        // Gentle horizontal sway
        TranslateTransition sway = new TranslateTransition(Duration.millis(1500), heart);
        sway.setFromX(0);
        sway.setToX(-30 + (double) RANDOM.nextInt(60));
        sway.setCycleCount(4);
        sway.setAutoReverse(true);

        // Fade in then out
        FadeTransition fadeIn = new FadeTransition(Duration.millis(500), heart);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(0.6);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(1000), heart);
        fadeOut.setFromValue(0.6);
        fadeOut.setToValue(0);
        fadeOut.setDelay(Duration.millis(3000 + (double) RANDOM.nextInt(2000)));

        // Gentle rotation
        RotateTransition rotate = new RotateTransition(Duration.millis(3000), heart);
        rotate.setFromAngle(-15);
        rotate.setToAngle(15);
        rotate.setCycleCount(2);
        rotate.setAutoReverse(true);

        ParallelTransition animation = new ParallelTransition(floatUp, sway, fadeIn, fadeOut, rotate);
        animation.setOnFinished(e -> {
            e.consume();
            particleLayer.getChildren().remove(heart);
        });
        animation.play();
    }

    /** Animate the main broken heart icon. */
    private void animateMainIcon() {
        // Apply pulsing glow
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web(COLOR_PINK));
        glow.setRadius(30);
        glow.setSpread(0.4);
        emptyStateIcon.setEffect(glow);

        // Pulse the glow
        Timeline glowPulse = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(glow.radiusProperty(), 20),
                        new KeyValue(glow.spreadProperty(), 0.2)),
                new KeyFrame(
                        Duration.millis(1200),
                        new KeyValue(glow.radiusProperty(), 45, Interpolator.EASE_BOTH),
                        new KeyValue(glow.spreadProperty(), 0.6, Interpolator.EASE_BOTH)));
        glowPulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
        glowPulse.setAutoReverse(true);
        glowPulse.play();
        trackAnimation(glowPulse);

        // Scale breathing
        ScaleTransition breathe = new ScaleTransition(Duration.millis(1800), emptyStateIcon);
        breathe.setFromX(1.0);
        breathe.setFromY(1.0);
        breathe.setToX(1.15);
        breathe.setToY(1.15);
        breathe.setCycleCount(javafx.animation.Animation.INDEFINITE);
        breathe.setAutoReverse(true);
        breathe.setInterpolator(Interpolator.EASE_BOTH);
        breathe.play();
        trackAnimation(breathe);
    }

    /** Animate the empty state container with gentle float. */
    private void animateEmptyStateContainer() {
        TranslateTransition floatAnim = new TranslateTransition(Duration.millis(2500), emptyStateContainer);
        floatAnim.setFromY(0);
        floatAnim.setToY(-12);
        floatAnim.setCycleCount(javafx.animation.Animation.INDEFINITE);
        floatAnim.setAutoReverse(true);
        floatAnim.setInterpolator(Interpolator.EASE_BOTH);
        floatAnim.play();
        trackAnimation(floatAnim);
    }

    /** Populate the FlowPane with the current section's cards. */
    private void populateCards() {
        matchesFlow.getChildren().clear();

        if (getActiveCardsCount() == 0) {
            matchesFlow.setVisible(false);
            matchesFlow.setManaged(false);
            emptyStateContainer.setVisible(true);
            emptyStateContainer.setManaged(true);
            startEmptyStateAnimations();
        } else {
            matchesFlow.setVisible(true);
            matchesFlow.setManaged(true);
            emptyStateContainer.setVisible(false);
            emptyStateContainer.setManaged(false);

            int index = 0;
            for (VBox card : buildCardsForSection()) {
                matchesFlow.getChildren().add(card);
                animateCardEntrance(card, index * 120);
                index++;
            }
        }

        updateHeader();
    }

    private int getActiveCardsCount() {
        return switch (currentSection) {
            case MATCHES -> viewModel.getMatches().size();
            case LIKES_YOU -> viewModel.getLikesReceived().size();
            case YOU_LIKED -> viewModel.getLikesSent().size();
            default -> viewModel.getMatches().size();
        };
    }

    private List<VBox> buildCardsForSection() {
        List<VBox> cards = new ArrayList<>();
        switch (currentSection) {
            case MATCHES -> {
                for (MatchCardData match : viewModel.getMatches()) {
                    cards.add(createMatchCard(match));
                }
            }
            case LIKES_YOU -> {
                for (LikeCardData like : viewModel.getLikesReceived()) {
                    cards.add(likesTabRenderer.createIncomingLikeCard(like));
                }
            }
            case YOU_LIKED -> {
                for (LikeCardData like : viewModel.getLikesSent()) {
                    cards.add(likesTabRenderer.createOutgoingLikeCard(like));
                }
            }
            default -> {
                logWarn("Unknown section {}, defaulting to matches list", currentSection);
                for (MatchCardData match : viewModel.getMatches()) {
                    cards.add(createMatchCard(match));
                }
            }
        }
        return cards;
    }

    /** Animate a card entering with fade + slide up. */
    private void animateCardEntrance(Node card, int delayMs) {
        card.setOpacity(0);
        card.setTranslateY(40);
        card.setScaleX(0.9);
        card.setScaleY(0.9);

        FadeTransition fade = new FadeTransition(Duration.millis(500), card);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setDelay(Duration.millis(delayMs));

        TranslateTransition slide = new TranslateTransition(Duration.millis(500), card);
        slide.setFromY(40);
        slide.setToY(0);
        slide.setDelay(Duration.millis(delayMs));
        slide.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(500), card);
        scale.setFromX(0.9);
        scale.setFromY(0.9);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setDelay(Duration.millis(delayMs));
        scale.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition entrance = new ParallelTransition(fade, slide, scale);
        entrance.play();
    }

    /** Create a styled match card for the given match data. */
    private VBox createMatchCard(MatchCardData match) {
        VBox card = new VBox(14);
        card.getStyleClass().add("match-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(220);
        card.setPadding(new Insets(28, 20, 28, 20));

        // Check if this is a new match (less than 24 hours old)
        boolean isNewMatch =
                java.time.Duration.between(match.matchedAt(), AppClock.now()).toHours() < 24;

        // "New" badge for recent matches
        if (isNewMatch) {
            Label newBadge = new Label("NEW");
            newBadge.getStyleClass().add("new-match-badge");
            card.getChildren().add(newBadge);

            // Animate the badge with pronounced pulse
            ScaleTransition badgePulse = new ScaleTransition(Duration.millis(600), newBadge);
            badgePulse.setFromX(1.0);
            badgePulse.setFromY(1.0);
            badgePulse.setToX(1.2);
            badgePulse.setToY(1.2);
            badgePulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
            badgePulse.setAutoReverse(true);
            badgePulse.play();
        }

        // Avatar container with glow effect
        StackPane avatarContainer = new StackPane();
        avatarContainer.getStyleClass().add("match-avatar-container");
        FontIcon avatarIcon = new FontIcon("mdi2a-account");
        avatarIcon.setIconSize(36);
        avatarIcon.setIconColor(Color.web("#10b981"));
        avatarIcon.getStyleClass().add("match-avatar-icon");

        // TODO: Replace with real presence status when user presence tracking is implemented
        Region statusDot = new Region();
        statusDot.getStyleClass().add("status-dot");
        statusDot.getStyleClass().add("status-offline");
        StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
        statusDot.setTranslateX(4);
        statusDot.setTranslateY(4);

        avatarContainer.getChildren().addAll(avatarIcon, statusDot);

        // User name with premium styling
        Label nameLabel = new Label(match.userName());
        nameLabel.getStyleClass().add("match-user-name");

        // Match time with secondary styling
        Label timeLabel = new Label("Matched " + match.matchedTimeAgo());
        timeLabel.getStyleClass().add("match-time-label");

        // Premium message button
        Button messageBtn = new Button("Message");
        messageBtn.getStyleClass().add("match-message-btn");
        FontIcon msgIcon = new FontIcon("mdi2m-message-text");
        msgIcon.setIconSize(16);
        msgIcon.setIconColor(Color.WHITE);
        messageBtn.setGraphic(msgIcon);
        messageBtn.setOnAction(e -> {
            e.consume();
            handleStartChat(match);
        });

        // Button hover animation
        messageBtn.setOnMouseEntered(e -> {
            e.consume();
            ScaleTransition bounce = new ScaleTransition(Duration.millis(100), messageBtn);
            bounce.setToX(1.08);
            bounce.setToY(1.08);
            bounce.play();
        });
        messageBtn.setOnMouseExited(e -> {
            e.consume();
            ScaleTransition unbounce = new ScaleTransition(Duration.millis(100), messageBtn);
            unbounce.setToX(1.0);
            unbounce.setToY(1.0);
            unbounce.play();
        });

        card.getChildren().addAll(avatarContainer, nameLabel, timeLabel, messageBtn);
        return card;
    }

    /** Navigate to chat with selected match. */
    private void handleStartChat(MatchCardData match) {
        logInfo("Starting chat with match: {}", match.userName());
        // Set navigation context so ChatController knows which user to chat with
        NavigationService nav = NavigationService.getInstance();
        nav.setNavigationContext(match.userId());
        nav.navigateTo(NavigationService.ViewType.CHAT);
    }

    @FXML
    private void handleBrowse() {
        logInfo("Navigating to browse/matching screen");
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHING);
    }

    private void wireNavigationButtons() {
        if (backButton != null) {
            backButton.setOnAction(event -> {
                event.consume();
                handleBack();
            });
        }
        if (emptyActionButton != null) {
            emptyActionButton.setOnAction(event -> {
                event.consume();
                handleBrowse();
            });
        }
    }

    private void updateHeader() {
        switch (currentSection) {
            case MATCHES -> {
                headerTitleLabel.setText("Your Matches");
                headerIcon.setIconLiteral("mdi2h-heart-multiple");
                headerIcon.setIconColor(Color.web(COLOR_PINK));
                matchCountLabel.setText(
                        String.valueOf(viewModel.matchCountProperty().get()));
            }
            case LIKES_YOU -> {
                headerTitleLabel.setText("Likes You");
                headerIcon.setIconLiteral("mdi2h-heart-flash");
                headerIcon.setIconColor(Color.web("#f59e0b"));
                matchCountLabel.setText(
                        String.valueOf(viewModel.likesReceivedCountProperty().get()));
            }
            case YOU_LIKED -> {
                headerTitleLabel.setText("You Liked");
                headerIcon.setIconLiteral(ICON_HEART);
                headerIcon.setIconColor(Color.web("#a855f7"));
                matchCountLabel.setText(
                        String.valueOf(viewModel.likesSentCountProperty().get()));
            }
            default -> {
                logWarn("Unknown section {}, defaulting to matches header", currentSection);
                headerTitleLabel.setText("Your Matches");
                headerIcon.setIconLiteral("mdi2h-heart-multiple");
                headerIcon.setIconColor(Color.web(COLOR_PINK));
                matchCountLabel.setText(
                        String.valueOf(viewModel.matchCountProperty().get()));
            }
        }

        updateEmptyStateContent();
    }

    private void updateEmptyStateContent() {
        switch (currentSection) {
            case MATCHES -> {
                emptyStateIcon.setIconLiteral("mdi2h-heart-broken");
                emptyStateIcon.setIconColor(Color.web("#64748b"));
                emptyTitleLabel.setText("No matches yet");
                emptySubtitleLabel.setText(
                        "Your perfect match is waiting! Start browsing to connect with amazing people.");
                emptyHintLabel.setText("Like someone and if they like you back, it's a match!");
                emptyActionButton.setText(START_BROWSING_LABEL);
            }
            case LIKES_YOU -> {
                emptyStateIcon.setIconLiteral("mdi2h-heart-flash");
                emptyStateIcon.setIconColor(Color.web("#f59e0b"));
                emptyTitleLabel.setText("No new likes yet");
                emptySubtitleLabel.setText("When someone likes you, they will appear here.");
                emptyHintLabel.setText("Keep browsing to boost your profile visibility.");
                emptyActionButton.setText("Browse More");
            }
            case YOU_LIKED -> {
                emptyStateIcon.setIconLiteral(ICON_HEART);
                emptyStateIcon.setIconColor(Color.web("#a855f7"));
                emptyTitleLabel.setText("No likes sent yet");
                emptySubtitleLabel.setText("Swipe right on people you like to start conversations.");
                emptyHintLabel.setText("Sent likes stay here until they respond.");
                emptyActionButton.setText(START_BROWSING_LABEL);
            }
            default -> {
                logWarn("Unknown section {}, defaulting to matches empty state", currentSection);
                emptyStateIcon.setIconLiteral("mdi2h-heart-broken");
                emptyStateIcon.setIconColor(Color.web("#64748b"));
                emptyTitleLabel.setText("No matches yet");
                emptySubtitleLabel.setText(
                        "Your perfect match is waiting! Start browsing to connect with amazing people.");
                emptyHintLabel.setText("Like someone and if they like you back, it's a match!");
                emptyActionButton.setText(START_BROWSING_LABEL);
            }
        }
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

    /**
     * Cleans up resources when navigating away from this controller.
     * Stops all INDEFINITE animations to prevent memory leaks and CPU waste.
     */
    @Override
    public void cleanup() {
        // Clear particle layer if it exists
        if (particleLayer != null) {
            particleLayer.getChildren().clear();
        }
        // super.cleanup() stops all tracked animations and subscriptions
        super.cleanup();
    }
}
