package datingapp.ui.util;

import datingapp.ui.constants.AnimationConstants;
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
        ScaleTransition st = new ScaleTransition(AnimationConstants.HOVER_PULSE_DURATION, node);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(AnimationConstants.HOVER_PULSE_SCALE);
        st.setToY(AnimationConstants.HOVER_PULSE_SCALE);

        node.setOnMouseEntered(e -> {
            e.consume();
            st.setRate(1.0);
            st.play();
        });
        node.setOnMouseExited(e -> {
            e.consume();
            st.setRate(-1.0);
            st.play();
        });
    }

    /**
     * Quick scale pulse for button press feedback.
     * Per JavaFX Skill Cheatsheet Section 14.
     */
    public static void pulseScale(Node node) {
        ScaleTransition scale = new ScaleTransition(AnimationConstants.BUTTON_PULSE_DURATION, node);
        scale.setToX(AnimationConstants.BUTTON_PULSE_SCALE);
        scale.setToY(AnimationConstants.BUTTON_PULSE_SCALE);
        scale.setAutoReverse(true);
        scale.setCycleCount(AnimationConstants.BUTTON_PULSE_CYCLES);
        scale.play();
    }

    /**
     * Adds a continuous pulsing glow effect to a node.
     *
     * @param node      The node to apply the glow effect to
     * @param glowColor The color of the glow
     */
    public static void addPulsingGlow(Node node, Color glowColor) {
        DropShadow glow = new DropShadow();
        glow.setColor(glowColor);
        glow.setRadius(AnimationConstants.GLOW_RADIUS_MIN);
        glow.setSpread(AnimationConstants.GLOW_SPREAD_INITIAL);

        Timeline timeline = new Timeline(
                new KeyFrame(
                        AnimationConstants.GLOW_START_TIME,
                        new KeyValue(glow.radiusProperty(), AnimationConstants.GLOW_RADIUS_MIN),
                        new KeyValue(glow.spreadProperty(), AnimationConstants.GLOW_SPREAD_MIN)),
                new KeyFrame(
                        AnimationConstants.GLOW_MID_TIME,
                        new KeyValue(glow.radiusProperty(), AnimationConstants.GLOW_RADIUS_MAX),
                        new KeyValue(glow.spreadProperty(), AnimationConstants.GLOW_SPREAD_MAX)),
                new KeyFrame(
                        AnimationConstants.GLOW_END_TIME,
                        new KeyValue(glow.radiusProperty(), AnimationConstants.GLOW_RADIUS_MIN),
                        new KeyValue(glow.spreadProperty(), AnimationConstants.GLOW_SPREAD_MIN)));
        timeline.setCycleCount(Animation.INDEFINITE);

        node.setEffect(glow);
        timeline.play();
    }

    /**
     * Creates a fade-in animation.
     *
     * @param node     The node to fade in
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
     * @param node     The node to fade out
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
        ScaleTransition pulse = new ScaleTransition(AnimationConstants.ICON_PULSE_DURATION, node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(AnimationConstants.ICON_PULSE_SCALE);
        pulse.setToY(AnimationConstants.ICON_PULSE_SCALE);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(AnimationConstants.ICON_PULSE_CYCLES);
        return pulse;
    }

    /**
     * Plays a shake animation for validation errors.
     *
     * @param node The node to shake
     */
    public static void playShake(Node node) {
        TranslateTransition shake = new TranslateTransition(AnimationConstants.SHAKE_DURATION, node);
        shake.setFromX(-AnimationConstants.SHAKE_DISTANCE);
        shake.setToX(AnimationConstants.SHAKE_DISTANCE);
        shake.setCycleCount(AnimationConstants.SHAKE_CYCLES);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> {
            e.consume();
            node.setTranslateX(0);
        });
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

        ScaleTransition scale = new ScaleTransition(AnimationConstants.BOUNCE_IN_DURATION, node);
        scale.setFromX(0);
        scale.setFromY(0);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);

        // Overshoot effect
        ScaleTransition overshoot = new ScaleTransition(AnimationConstants.BOUNCE_OVERSHOOT_DURATION, node);
        overshoot.setFromX(1);
        overshoot.setFromY(1);
        overshoot.setToX(AnimationConstants.BOUNCE_OVERSHOOT_SCALE);
        overshoot.setToY(AnimationConstants.BOUNCE_OVERSHOOT_SCALE);
        overshoot.setAutoReverse(true);
        overshoot.setCycleCount(AnimationConstants.BOUNCE_OVERSHOOT_CYCLES);

        scale.setOnFinished(e -> {
            e.consume();
            overshoot.play();
        });
        scale.play();
    }

    /**
     * Creates a slide transition for a node.
     *
     * @param node     The node to slide
     * @param toX      Target X translation
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
     * @param node      The node to apply parallax to
     * @param intensity The intensity of the parallax effect (5-20 recommended)
     */
    public static void addParallaxEffect(Node node, double intensity) {
        node.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (obs == null) {
                return;
            }
            if (newScene == null) {
                return;
            }

            if (oldScene != null) {
                oldScene.setOnMouseMoved(null);
            }

            newScene.setOnMouseMoved(e -> {
                double centerX = newScene.getWidth() / 2;
                double centerY = newScene.getHeight() / 2;

                double offsetX = (e.getSceneX() - centerX) / centerX * intensity;
                double offsetY = (e.getSceneY() - centerY) / centerY * intensity;

                TranslateTransition move = new TranslateTransition(AnimationConstants.PARALLAX_MOVE_DURATION, node);
                move.setToX(offsetX);
                move.setToY(offsetY);
                move.play();
            });
        });
    }
}
