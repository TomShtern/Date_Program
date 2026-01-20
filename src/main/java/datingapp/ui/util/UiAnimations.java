package datingapp.ui.util;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/** Utility class for common UI animations and effects. */
public final class UiAnimations {

    private UiAnimations() {
        // Utility class
    }

    /**
     * Fades in a node.
     */
    public static void fadeIn(Node node, double durationMs) {
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    /**
     * Slides a node up while fading in.
     */
    public static void slideUp(Node node, double durationMs, double distance) {
        node.setOpacity(0);
        node.setTranslateY(distance);

        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setToValue(1.0);

        TranslateTransition tt = new TranslateTransition(Duration.millis(durationMs), node);
        tt.setToY(0);

        ft.play();
        tt.play();
    }

    /**
     * Adds a subtle pulse effect on hover.
     */
    public static void addPulseOnHover(Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(200), node);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(1.05);
        st.setToY(1.05);

        node.setOnMouseEntered(e -> {
            st.setRate(1.0);
            st.play();
        });
        node.setOnMouseExited(e -> {
            st.setRate(-1.0);
            st.play();
        });
    }

    /**
     * Quick scale pulse for button press feedback.
     * Per JavaFX Skill Cheatsheet Section 14.
     */
    public static void pulseScale(Node node) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(100), node);
        scale.setToX(1.15);
        scale.setToY(1.15);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.play();
    }

    /**
     * Adds a continuous pulsing glow effect to a node.
     *
     * @param node The node to apply the glow effect to
     * @param glowColor The color of the glow
     * @return The Timeline so it can be stopped when navigating away
     */
    public static Timeline addPulsingGlow(Node node, Color glowColor) {
        DropShadow glow = new DropShadow();
        glow.setColor(glowColor);
        glow.setRadius(15);
        glow.setSpread(0.3);

        Timeline timeline = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(glow.radiusProperty(), 15),
                        new KeyValue(glow.spreadProperty(), 0.2)),
                new KeyFrame(
                        Duration.millis(1000),
                        new KeyValue(glow.radiusProperty(), 25),
                        new KeyValue(glow.spreadProperty(), 0.4)),
                new KeyFrame(
                        Duration.millis(2000),
                        new KeyValue(glow.radiusProperty(), 15),
                        new KeyValue(glow.spreadProperty(), 0.2)));
        timeline.setCycleCount(Animation.INDEFINITE);

        node.setEffect(glow);
        timeline.play();

        return timeline;
    }

    /**
     * Creates a fade-in animation.
     *
     * @param node The node to fade in
     * @param duration The duration of the fade
     * @return The FadeTransition (not yet played)
     */
    public static FadeTransition createFadeIn(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        return fade;
    }

    /**
     * Creates a fade-out animation.
     *
     * @param node The node to fade out
     * @param duration The duration of the fade
     * @return The FadeTransition (not yet played)
     */
    public static FadeTransition createFadeOut(Node node, Duration duration) {
        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(1);
        fade.setToValue(0);
        return fade;
    }

    /**
     * Creates a scale pulse animation (for buttons, icons, etc.).
     *
     * @param node The node to pulse
     * @return The ScaleTransition (not yet played)
     */
    public static ScaleTransition createPulse(Node node) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(150), node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.1);
        pulse.setToY(1.1);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        return pulse;
    }

    /**
     * Plays a shake animation for validation errors.
     *
     * @param node The node to shake
     */
    public static void playShake(Node node) {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), node);
        shake.setFromX(-10);
        shake.setToX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> node.setTranslateX(0));
        shake.play();
    }

    /**
     * Bounce animation for elements entering the screen.
     *
     * @param node The node to bounce in
     */
    public static void playBounceIn(Node node) {
        node.setScaleX(0);
        node.setScaleY(0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(400), node);
        scale.setFromX(0);
        scale.setFromY(0);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);

        // Overshoot effect
        ScaleTransition overshoot = new ScaleTransition(Duration.millis(100), node);
        overshoot.setFromX(1);
        overshoot.setFromY(1);
        overshoot.setToX(1.05);
        overshoot.setToY(1.05);
        overshoot.setAutoReverse(true);
        overshoot.setCycleCount(2);

        scale.setOnFinished(e -> overshoot.play());
        scale.play();
    }

    /**
     * Creates a slide transition for a node.
     *
     * @param node The node to slide
     * @param toX Target X translation
     * @param duration The duration of the slide
     * @return The TranslateTransition (not yet played)
     */
    public static TranslateTransition createSlide(Node node, double toX, Duration duration) {
        TranslateTransition slide = new TranslateTransition(duration, node);
        slide.setToX(toX);
        slide.setInterpolator(Interpolator.EASE_OUT);
        return slide;
    }

    /**
     * Adds a parallax effect that moves a node based on mouse position.
     *
     * @param node The node to apply parallax to
     * @param intensity The intensity of the parallax effect (5-20 recommended)
     */
    public static void addParallaxEffect(Node node, double intensity) {
        node.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }

            newScene.setOnMouseMoved(e -> {
                double centerX = newScene.getWidth() / 2;
                double centerY = newScene.getHeight() / 2;

                double offsetX = (e.getSceneX() - centerX) / centerX * intensity;
                double offsetY = (e.getSceneY() - centerY) / centerY * intensity;

                TranslateTransition move = new TranslateTransition(Duration.millis(100), node);
                move.setToX(offsetX);
                move.setToY(offsetY);
                move.play();
            });
        });
    }
}
