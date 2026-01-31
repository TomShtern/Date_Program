package datingapp.ui.controller;

import datingapp.ui.util.UiHelpers;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Achievement Popup overlay.
 * Displays achievement unlocked notifications with animations and confetti.
 *
 * <p>
 * FXML controller reference:
 * {@code fx:controller="datingapp.ui.controller.AchievementPopupController"}
 */
public class AchievementPopupController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AchievementPopupController.class);
    private static final int AUTO_DISMISS_SECONDS = 5;

    @FXML
    private StackPane rootPane;

    @FXML
    private Canvas confettiCanvas;

    @FXML
    private FontIcon achievementIcon;

    @FXML
    @SuppressWarnings("unused")
    private Label titleLabel; // Injected from FXML but not used in code

    @FXML
    private Label nameLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label xpLabel;

    private UiHelpers.ConfettiAnimation confetti;
    private Runnable onCloseCallback;
    private boolean autoDismiss = true;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Size canvas to parent
        confettiCanvas.widthProperty().bind(rootPane.widthProperty());
        confettiCanvas.heightProperty().bind(rootPane.heightProperty());
    }

    /**
     * Shows the achievement with specified details and plays entrance animation.
     *
     * @param iconLiteral The Material Design icon literal (e.g., "mdi2t-trophy")
     * @param name        The achievement name
     * @param description The achievement description
     * @param xpAmount    The XP/points earned
     */
    public void showAchievement(String iconLiteral, String name, String description, int xpAmount) {
        // Set content
        achievementIcon.setIconLiteral(iconLiteral);
        nameLabel.setText(name);
        descriptionLabel.setText(description);
        xpLabel.setText("+" + xpAmount + " XP");

        logger.info("Showing achievement: {} (+{} XP)", name, xpAmount);

        // Play entrance animation
        playEntranceAnimation();

        // Auto-dismiss after delay
        if (autoDismiss) {
            PauseTransition delay = new PauseTransition(Duration.seconds(AUTO_DISMISS_SECONDS));
            delay.setOnFinished(e -> close());
            delay.play();
        }
    }

    /** Convenience method for common achievement types. */
    public void showAchievement(AchievementType type) {
        showAchievement(type.iconLiteral, type.name, type.description, type.xp);
    }

    private void playEntranceAnimation() {
        // Initial state
        rootPane.setOpacity(0);

        // Fade in overlay
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), rootPane);
        fadeIn.setToValue(1);

        // Icon container scale animation
        StackPane iconContainer = (StackPane) achievementIcon.getParent();
        iconContainer.setScaleX(0);
        iconContainer.setScaleY(0);

        ScaleTransition iconPop = new ScaleTransition(Duration.millis(400), iconContainer);
        iconPop.setToX(1.15);
        iconPop.setToY(1.15);
        iconPop.setDelay(Duration.millis(200));
        iconPop.setInterpolator(Interpolator.EASE_OUT);
        iconPop.setOnFinished(e -> {
            ScaleTransition settle = new ScaleTransition(Duration.millis(200), iconContainer);
            settle.setToX(1);
            settle.setToY(1);
            settle.setInterpolator(Interpolator.EASE_BOTH);
            settle.play();
        });

        // Add pulsing glow to icon
        addIconGlow();

        // Start confetti
        confetti = new UiHelpers.ConfettiAnimation();
        confetti.play(confettiCanvas);

        new ParallelTransition(fadeIn, iconPop).play();
    }

    private void addIconGlow() {
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#fbbf24"));
        glow.setRadius(30);
        glow.setSpread(0.3);
        achievementIcon.setEffect(glow);

        // Animate glow pulse
        Timeline glowPulse = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(glow.radiusProperty(), 20),
                        new KeyValue(glow.spreadProperty(), 0.2)),
                new KeyFrame(
                        Duration.millis(800),
                        new KeyValue(glow.radiusProperty(), 40, Interpolator.EASE_BOTH),
                        new KeyValue(glow.spreadProperty(), 0.5, Interpolator.EASE_BOTH)));
        glowPulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
        glowPulse.setAutoReverse(true);
        glowPulse.play();
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleClose() {
        close();
    }

    private void close() {
        if (confetti != null) {
            confetti.stop();
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootPane);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            if (rootPane.getParent() instanceof StackPane parent) {
                parent.getChildren().remove(rootPane);
            }
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        });
        fadeOut.play();
    }

    /** Sets the callback to run when the popup is closed. */
    public void setOnClose(Runnable callback) {
        this.onCloseCallback = callback;
    }

    /** Sets whether the popup should auto-dismiss. Default is true. */
    public void setAutoDismiss(boolean autoDismiss) {
        this.autoDismiss = autoDismiss;
    }

    /** Predefined achievement types for common scenarios. */
    public enum AchievementType {
        FIRST_MATCH("mdi2h-heart-multiple", "First Match!", "You've made your first connection!", 50),
        PROFILE_COMPLETE("mdi2a-account-check", "Profile Complete", "Your profile is 100% complete!", 100),
        FIRST_MESSAGE("mdi2m-message-text", "First Message", "You sent your first message!", 25),
        TEN_MATCHES("mdi2s-star", "Popular!", "You've reached 10 matches!", 200),
        TWENTY_FIVE_MATCHES("mdi2t-trophy", "Rising Star", "25 matches and counting!", 500),
        FIFTY_MATCHES("mdi2c-crown", "Match Master", "50 matches! You're on fire!", 1000),
        FIRST_DATE("mdi2c-calendar-heart", "First Date", "You've scheduled your first date!", 150),
        ACTIVE_WEEK("mdi2f-fire", "Active Week", "You've been active for 7 days straight!", 75),
        SUPER_LIKER("mdi2l-lightning-bolt", "Super Liker", "You've used your first Super Like!", 30),
        PHOTO_PERFECT("mdi2c-camera", "Photo Perfect", "Added 5 photos to your profile!", 50);

        final String iconLiteral;
        final String name;
        final String description;
        final int xp;

        AchievementType(String iconLiteral, String name, String description, int xp) {
            this.iconLiteral = iconLiteral;
            this.name = name;
            this.description = description;
            this.xp = xp;
        }
    }
}
