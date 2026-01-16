package datingapp.ui.component;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Animated skeleton loading placeholder with shimmer effect.
 * Displays a gradient animation while content is loading.
 */
public class SkeletonLoader extends Region {

    private final Rectangle skeleton;
    private final Timeline shimmerAnimation;
    private double animationProgress = 0;

    /**
     * Creates a skeleton loader with specified dimensions.
     *
     * @param width  Width of the skeleton placeholder
     * @param height Height of the skeleton placeholder
     */
    public SkeletonLoader(double width, double height) {
        skeleton = new Rectangle(width, height);
        skeleton.setArcWidth(8);
        skeleton.setArcHeight(8);

        // Initial gradient
        updateGradient(0);

        // Animate gradient position
        shimmerAnimation = new Timeline(new KeyFrame(Duration.millis(50), e -> {
            animationProgress += 0.033;
            if (animationProgress > 1) {
                animationProgress = 0;
            }
            updateGradient(animationProgress);
        }));
        shimmerAnimation.setCycleCount(Timeline.INDEFINITE);
        shimmerAnimation.play();

        getChildren().add(skeleton);
        setMinSize(width, height);
        setMaxSize(width, height);
    }

    private void updateGradient(double position) {
        double startX = -0.5 + position * 2;
        double endX = startX + 0.5;

        LinearGradient gradient = new LinearGradient(
                startX,
                0,
                endX,
                0,
                true,
                CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(30, 41, 59)),
                new Stop(0.5, Color.rgb(51, 65, 85)),
                new Stop(1, Color.rgb(30, 41, 59)));
        skeleton.setFill(gradient);
    }

    /** Stops the shimmer animation. */
    public void stop() {
        shimmerAnimation.stop();
    }
}
