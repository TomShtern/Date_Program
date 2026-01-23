package datingapp.ui.component;

import java.util.function.Supplier;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
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

    /**
     * Shows a skeleton loader, then replaces it with content from the supplier.
     * The content supplier runs on a virtual thread.
     *
     * @param container     the pane to show skeleton/content in
     * @param width         skeleton width
     * @param height        skeleton height
     * @param contentLoader supplier that returns the loaded content node
     */
    public static void loadWithSkeleton(Pane container, double width, double height, Supplier<Node> contentLoader) {

        // 1. Create and display skeleton immediately
        SkeletonLoader skeleton = new SkeletonLoader(width, height);
        container.getChildren().setAll(skeleton);

        // 2. Load content on virtual thread
        Thread.ofVirtual().name("skeleton-load").start(() -> {
            try {
                Node content = contentLoader.get();

                Platform.runLater(() -> {
                    // 3. Stop skeleton animation
                    skeleton.stop();

                    // 4. Crossfade to content
                    content.setOpacity(0);
                    container.getChildren().setAll(content);

                    FadeTransition fade = new FadeTransition(Duration.millis(300), content);
                    fade.setToValue(1.0);
                    fade.play();
                });
            } catch (Exception ignored) {
                Platform.runLater(() -> {
                    skeleton.stop();
                    // Show error state or placeholder
                    container.getChildren().clear();
                });
            }
        });
    }

    /**
     * Shows a skeleton loader with a Runnable callback when loading completes.
     * Useful when content is already in the container and just needs data population.
     *
     * @param container the pane to overlay with skeleton
     * @param width     skeleton width
     * @param height    skeleton height
     * @param dataLoader runnable that loads data (runs on virtual thread)
     * @param onComplete runnable called on FX thread when done
     */
    public static void loadDataWithSkeleton(
            Pane container, double width, double height, Runnable dataLoader, Runnable onComplete) {

        // 1. Create and show skeleton overlay
        SkeletonLoader skeleton = new SkeletonLoader(width, height);
        Node originalContent = container.getChildren().isEmpty()
                ? null
                : container.getChildren().getFirst();

        // Stack skeleton on top
        if (originalContent != null) {
            originalContent.setOpacity(0.3);
        }
        container.getChildren().add(skeleton);

        // 2. Load data on virtual thread
        Thread.ofVirtual().name("data-load").start(() -> {
            try {
                dataLoader.run();
            } finally {
                Platform.runLater(() -> {
                    // 3. Remove skeleton and restore content
                    skeleton.stop();
                    container.getChildren().remove(skeleton);

                    if (originalContent != null) {
                        FadeTransition fade = new FadeTransition(Duration.millis(200), originalContent);
                        fade.setToValue(1.0);
                        fade.play();
                    }

                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        });
    }

    /**
     * Creates a skeleton loader that fits the given node's dimensions.
     *
     * @param node the node to match dimensions of
     * @return a new SkeletonLoader
     */
    public static SkeletonLoader forNode(Node node) {
        double width = node.getBoundsInLocal().getWidth();
        double height = node.getBoundsInLocal().getHeight();
        return new SkeletonLoader(Math.max(width, 100), Math.max(height, 50));
    }
}
