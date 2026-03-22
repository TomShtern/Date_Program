package datingapp.ui.util;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Utility class for applying consistent tactile feedback effects to buttons.
 * Provides hover lift, press sink, and release spring-back effects.
 *
 * <p>Usage:
 * <pre>
 * ButtonFeedback.apply(myButton);
 * // Or with custom accent color:
 * ButtonFeedback.apply(myButton, Color.web("#f43f5e"));
 * </pre>
 */
public final class ButtonFeedback {
    private static final Color DEFAULT_GLOW_COLOR = Color.web("#667eea");
    private static final double GLOW_RADIUS = 10;
    private static final double PRESS_SCALE = 0.97;
    private static final double LIFT_OFFSET = -2;
    private static final Duration ANIMATION_DURATION = Duration.millis(100);

    private ButtonFeedback() {
        // Utility class - no instantiation
    }

    /**
     * Applies hover/press/release feedback effects to a button.
     * Uses the default accent color for glow effect.
     *
     * @param button the button to enhance
     */
    public static void apply(Button button) {
        apply(button, DEFAULT_GLOW_COLOR);
    }

    /**
     * Applies hover/press/release feedback effects to a button.
     *
     * @param button    the button to enhance
     * @param glowColor the color for the glow effect on hover
     */
    public static void apply(Button button, Color glowColor) {
        // Create glow effect for hover
        DropShadow glow = new DropShadow();
        glow.setColor(glowColor);
        glow.setRadius(GLOW_RADIUS);
        glow.setSpread(0.2);

        // Hover: Lift + glow
        button.setOnMouseEntered(e -> {
            // Lift animation
            Timeline lift = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(button.translateYProperty(), 0)),
                    new KeyFrame(
                            ANIMATION_DURATION,
                            new KeyValue(button.translateYProperty(), LIFT_OFFSET, Interpolator.EASE_OUT)));
            lift.play();

            // Add glow effect
            button.setEffect(glow);
        });

        // Mouse exit: Reset
        button.setOnMouseExited(e -> {
            // Reset lift
            Timeline resetLift = new Timeline(new KeyFrame(
                    ANIMATION_DURATION, new KeyValue(button.translateYProperty(), 0, Interpolator.EASE_OUT)));
            resetLift.play();

            // Remove glow
            button.setEffect(null);
        });

        // Press: Sink (scale down)
        button.setOnMousePressed(e -> {
            button.setScaleX(PRESS_SCALE);
            button.setScaleY(PRESS_SCALE);
        });

        // Release: Spring back
        button.setOnMouseReleased(e -> springBack(button));
    }

    /**
     * Spring back animation that returns button to normal scale with overshoot.
     */
    private static void springBack(Button button) {
        // Quick spring back with slight overshoot for bouncy feel
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(80), button);
        scaleUp.setToX(1.03);
        scaleUp.setToY(1.03);

        ScaleTransition scaleNormal = new ScaleTransition(Duration.millis(100), button);
        scaleNormal.setToX(1.0);
        scaleNormal.setToY(1.0);

        scaleUp.setOnFinished(e -> scaleNormal.play());
        scaleUp.play();
    }

    /**
     * Applies only press feedback (for buttons that already have custom hover).
     *
     * @param button the button to enhance
     */
    public static void applyPressOnly(Button button) {
        button.setOnMousePressed(e -> {
            button.setScaleX(PRESS_SCALE);
            button.setScaleY(PRESS_SCALE);
        });

        button.setOnMouseReleased(e -> springBack(button));
    }

    /**
     * Applies a subtle pulse animation to indicate activity.
     *
     * @param button the button to animate
     */
    public static void pulse(Button button) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(150), button);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.08);
        pulse.setToY(1.08);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.play();
    }
}
