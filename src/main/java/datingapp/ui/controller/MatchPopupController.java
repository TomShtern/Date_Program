package datingapp.ui.controller;

import datingapp.core.model.User;
import datingapp.ui.util.UiAnimations.ConfettiAnimation;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controller for the "It's a Match!" celebration popup.
 * Displays confetti, animated avatars, and action buttons.
 *
 * <p>FXML controller reference:
 * {@code fx:controller="datingapp.ui.controller.MatchPopupController"}
 */
@SuppressWarnings("unused") // FXML-injected members and handlers are referenced from FXML.
public class MatchPopupController implements Initializable {

    @FXML
    private StackPane rootPane;

    @FXML
    private Canvas confettiCanvas;

    @FXML
    private FontIcon leftAvatarIcon;

    @FXML
    private FontIcon rightAvatarIcon;

    @FXML
    private FontIcon heartIcon;

    @FXML
    private Label matchMessage;

    private ConfettiAnimation confetti;
    private Runnable onMessageCallback;
    private Runnable onContinueCallback;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Size canvas to parent
        confettiCanvas.widthProperty().bind(rootPane.widthProperty());
        confettiCanvas.heightProperty().bind(rootPane.heightProperty());
    }

    /**
     * Sets the matched user information and plays the entrance animation.
     *
     * @param currentUser The currently logged-in user
     * @param matchedUser The user they matched with
     */
    @SuppressWarnings("unused")
    public void setMatchedUser(User currentUser, User matchedUser) {
        matchMessage.setText("You and " + matchedUser.getName() + " liked each other!");
        playEntranceAnimation();
    }

    private void playEntranceAnimation() {
        // Fade in overlay
        rootPane.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), rootPane);
        fadeIn.setToValue(1);

        // Left avatar flies in from left
        StackPane leftWrapper = (StackPane) leftAvatarIcon.getParent();
        leftWrapper.setTranslateX(-200);

        TranslateTransition leftFly = new TranslateTransition(Duration.millis(400), leftWrapper);
        leftFly.setToX(0);
        leftFly.setInterpolator(Interpolator.EASE_OUT);

        // Right avatar flies in from right
        StackPane rightWrapper = (StackPane) rightAvatarIcon.getParent();
        rightWrapper.setTranslateX(200);

        TranslateTransition rightFly = new TranslateTransition(Duration.millis(400), rightWrapper);
        rightFly.setToX(0);
        rightFly.setInterpolator(Interpolator.EASE_OUT);

        // Heart pulse
        heartIcon.setScaleX(0);
        heartIcon.setScaleY(0);
        ScaleTransition heartPop = new ScaleTransition(Duration.millis(300), heartIcon);
        heartPop.setToX(1.2);
        heartPop.setToY(1.2);
        heartPop.setDelay(Duration.millis(300));
        heartPop.setOnFinished(e -> {
            ScaleTransition settle = new ScaleTransition(Duration.millis(150), heartIcon);
            settle.setToX(1);
            settle.setToY(1);
            settle.play();
        });

        // Start confetti
        confetti = new ConfettiAnimation();
        confetti.play(confettiCanvas);

        new ParallelTransition(fadeIn, leftFly, rightFly, heartPop).play();
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleMessage() {
        close();
        if (onMessageCallback != null) {
            onMessageCallback.run();
        }
    }

    @FXML
    @SuppressWarnings("unused")
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

        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootPane);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            if (rootPane.getParent() instanceof StackPane parent) {
                parent.getChildren().remove(rootPane);
            }
        });
        fadeOut.play();
    }

    /** Sets the callback to run when "Send Message" is clicked. */
    public void setOnMessage(Runnable callback) {
        this.onMessageCallback = callback;
    }

    /** Sets the callback to run when "Keep Browsing" is clicked. */
    public void setOnContinue(Runnable callback) {
        this.onContinueCallback = callback;
    }
}
