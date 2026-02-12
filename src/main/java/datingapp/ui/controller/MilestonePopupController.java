package datingapp.ui.controller;

import datingapp.core.model.User;
import datingapp.ui.util.UiAnimations.ConfettiAnimation;
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
import javafx.animation.TranslateTransition;
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

/** Consolidated popup controller for achievement and match milestone popups. */
@SuppressWarnings("unused")
public class MilestonePopupController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MilestonePopupController.class);
    private static final int AUTO_DISMISS_SECONDS = 5;

    @FXML
    private StackPane rootPane;

    @FXML
    private Canvas confettiCanvas;

    // Achievement popup fields
    @FXML
    private FontIcon achievementIcon;

    @FXML
    private Label nameLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label xpLabel;

    // Match popup fields
    @FXML
    private FontIcon leftAvatarIcon;

    @FXML
    private FontIcon rightAvatarIcon;

    @FXML
    private FontIcon heartIcon;

    @FXML
    private Label matchMessage;

    private ConfettiAnimation confetti;
    private Runnable onCloseCallback;
    private Runnable onMessageCallback;
    private Runnable onContinueCallback;
    private boolean autoDismiss = true;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (confettiCanvas != null && rootPane != null) {
            confettiCanvas.widthProperty().bind(rootPane.widthProperty());
            confettiCanvas.heightProperty().bind(rootPane.heightProperty());
        }
    }

    public void showAchievement(String iconLiteral, String name, String description, int xpAmount) {
        if (achievementIcon == null || nameLabel == null || descriptionLabel == null || xpLabel == null) {
            throw new IllegalStateException("Achievement popup fields are not configured");
        }

        achievementIcon.setIconLiteral(iconLiteral);
        nameLabel.setText(name);
        descriptionLabel.setText(description);
        xpLabel.setText("+" + xpAmount + " XP");

        logger.info("Showing achievement: {} (+{} XP)", name, xpAmount);
        playAchievementEntranceAnimation();

        if (autoDismiss) {
            PauseTransition delay = new PauseTransition(Duration.seconds(AUTO_DISMISS_SECONDS));
            delay.setOnFinished(event -> close());
            delay.play();
        }
    }

    public void showAchievement(AchievementType type) {
        showAchievement(type.iconLiteral, type.name, type.description, type.xp);
    }

    public void setMatchedUser(User currentUser, User matchedUser) {
        if (matchMessage == null) {
            throw new IllegalStateException("Match popup fields are not configured");
        }
        matchMessage.setText("You and " + matchedUser.getName() + " liked each other!");
        playMatchEntranceAnimation();
    }

    private void playAchievementEntranceAnimation() {
        if (rootPane == null || achievementIcon == null) {
            return;
        }

        rootPane.setOpacity(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), rootPane);
        fadeIn.setToValue(1);

        StackPane iconContainer = (StackPane) achievementIcon.getParent();
        iconContainer.setScaleX(0);
        iconContainer.setScaleY(0);

        ScaleTransition iconPop = new ScaleTransition(Duration.millis(400), iconContainer);
        iconPop.setToX(1.15);
        iconPop.setToY(1.15);
        iconPop.setDelay(Duration.millis(200));
        iconPop.setInterpolator(Interpolator.EASE_OUT);
        iconPop.setOnFinished(event -> {
            ScaleTransition settle = new ScaleTransition(Duration.millis(200), iconContainer);
            settle.setToX(1);
            settle.setToY(1);
            settle.setInterpolator(Interpolator.EASE_BOTH);
            settle.play();
        });

        addIconGlow();
        startConfetti();

        new ParallelTransition(fadeIn, iconPop).play();
    }

    private void playMatchEntranceAnimation() {
        if (rootPane == null || leftAvatarIcon == null || rightAvatarIcon == null || heartIcon == null) {
            return;
        }

        rootPane.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), rootPane);
        fadeIn.setToValue(1);

        StackPane leftWrapper = (StackPane) leftAvatarIcon.getParent();
        leftWrapper.setTranslateX(-200);
        TranslateTransition leftFly = new TranslateTransition(Duration.millis(400), leftWrapper);
        leftFly.setToX(0);
        leftFly.setInterpolator(Interpolator.EASE_OUT);

        StackPane rightWrapper = (StackPane) rightAvatarIcon.getParent();
        rightWrapper.setTranslateX(200);
        TranslateTransition rightFly = new TranslateTransition(Duration.millis(400), rightWrapper);
        rightFly.setToX(0);
        rightFly.setInterpolator(Interpolator.EASE_OUT);

        heartIcon.setScaleX(0);
        heartIcon.setScaleY(0);
        ScaleTransition heartPop = new ScaleTransition(Duration.millis(300), heartIcon);
        heartPop.setToX(1.2);
        heartPop.setToY(1.2);
        heartPop.setDelay(Duration.millis(300));
        heartPop.setOnFinished(event -> {
            ScaleTransition settle = new ScaleTransition(Duration.millis(150), heartIcon);
            settle.setToX(1);
            settle.setToY(1);
            settle.play();
        });

        startConfetti();
        new ParallelTransition(fadeIn, leftFly, rightFly, heartPop).play();
    }

    private void startConfetti() {
        if (confettiCanvas == null) {
            return;
        }
        confetti = new ConfettiAnimation();
        confetti.play(confettiCanvas);
    }

    private void addIconGlow() {
        if (achievementIcon == null) {
            return;
        }

        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#fbbf24"));
        glow.setRadius(30);
        glow.setSpread(0.3);
        achievementIcon.setEffect(glow);

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
    private void handleClose() {
        close();
    }

    @FXML
    private void handleMessage() {
        close();
        if (onMessageCallback != null) {
            onMessageCallback.run();
        }
    }

    @FXML
    private void handleContinue() {
        close();
        if (onContinueCallback != null) {
            onContinueCallback.run();
        }
    }

    private void close() {
        if (confetti != null) {
            confetti.stop();
        }

        if (rootPane == null) {
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootPane);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> {
            if (rootPane.getParent() instanceof StackPane parent) {
                parent.getChildren().remove(rootPane);
            }
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        });
        fadeOut.play();
    }

    public void setOnClose(Runnable callback) {
        this.onCloseCallback = callback;
    }

    public void setOnMessage(Runnable callback) {
        this.onMessageCallback = callback;
    }

    public void setOnContinue(Runnable callback) {
        this.onContinueCallback = callback;
    }

    public void setAutoDismiss(boolean autoDismiss) {
        this.autoDismiss = autoDismiss;
    }

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
