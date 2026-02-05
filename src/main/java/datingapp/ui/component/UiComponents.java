package datingapp.ui.component;

import java.util.function.Supplier;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

/**
 * Consolidated UI component classes for visual elements.
 * Contains: TypingIndicator, ProgressRing, SkeletonLoader
 */
public final class UiComponents {

    private UiComponents() {
        // Utility class
    }

    /**
     * Creates a loading overlay with a spinner. The overlay is hidden by default.
     *
     * @return a StackPane containing the loading spinner
     */
    public static StackPane createLoadingOverlay() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(50, 50);
        spinner.getStyleClass().add("loading-spinner");

        StackPane overlay = new StackPane(spinner);
        overlay.getStyleClass().add("loading-overlay");
        overlay.setVisible(false);
        overlay.setManaged(false);
        return overlay;
    }

    // ========== TYPING INDICATOR ==========

    /**
     * Animated typing indicator showing bouncing dots.
     * Used in chat interfaces to indicate the other user is typing.
     */
    public static final class TypingIndicator extends HBox {

        private static final int DOT_COUNT = 3;
        private static final double DOT_RADIUS = 4;
        private static final Duration BOUNCE_DURATION = Duration.millis(400);

        /** Creates a new typing indicator with 3 bouncing dots. */
        public TypingIndicator() {
            setSpacing(4);
            setAlignment(Pos.CENTER);
            getStyleClass().add("typing-indicator");

            for (int i = 0; i < DOT_COUNT; i++) {
                Circle dot = new Circle(DOT_RADIUS);
                dot.setFill(Color.web("#94a3b8"));
                dot.getStyleClass().add("typing-dot");
                getChildren().add(dot);

                // Staggered bounce animation
                TranslateTransition bounce = new TranslateTransition(BOUNCE_DURATION, dot);
                bounce.setFromY(0);
                bounce.setToY(-6);
                bounce.setAutoReverse(true);
                bounce.setCycleCount(Animation.INDEFINITE);
                bounce.setDelay(Duration.millis(i * 150L));
                Platform.runLater(bounce::play);
            }

            // Start hidden
            setVisible(false);
            setManaged(false);
        }

        /** Shows the typing indicator. */
        public void show() {
            setVisible(true);
            setManaged(true);
        }

        /** Hides the typing indicator. */
        public void hide() {
            setVisible(false);
            setManaged(false);
        }
    }

    // ========== PROGRESS RING ==========

    /**
     * Animated circular progress ring with percentage display.
     * Shows a smooth arc animation as progress changes.
     */
    public static final class ProgressRing extends StackPane {

        private final Arc progressArc;
        private final Label percentLabel;
        private final DoubleProperty progress = new SimpleDoubleProperty(0);

        /**
         * Creates a progress ring with the specified radius.
         *
         * @param radius Outer radius of the ring
         */
        public ProgressRing(double radius) {
            double strokeWidth = 6;
            double innerRadius = radius - strokeWidth;

            // Background track
            Arc track = new Arc(radius, radius, innerRadius, innerRadius, 90, -360);
            track.setType(ArcType.OPEN);
            track.setStroke(Color.web("#1e293b"));
            track.setStrokeWidth(strokeWidth);
            track.setFill(Color.TRANSPARENT);
            track.setStrokeLineCap(StrokeLineCap.ROUND);

            // Progress arc
            progressArc = new Arc(radius, radius, innerRadius, innerRadius, 90, 0);
            progressArc.setType(ArcType.OPEN);
            progressArc.setStroke(Color.web("#667eea"));
            progressArc.setStrokeWidth(strokeWidth);
            progressArc.setFill(Color.TRANSPARENT);
            progressArc.setStrokeLineCap(StrokeLineCap.ROUND);

            // Center label
            percentLabel = new Label("0%");
            percentLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

            // Bind arc length to progress
            Arc progressArcRef = progressArc;
            Label percentLabelRef = percentLabel;
            progress.addListener((obs, oldVal, newVal) -> {
                if (obs == null) {
                    return;
                }
                if (Double.compare(oldVal.doubleValue(), newVal.doubleValue()) == 0) {
                    return;
                }
                progressArcRef.setLength(-360 * newVal.doubleValue());
                percentLabelRef.setText(String.format("%.0f%%", newVal.doubleValue() * 100));
            });

            getChildren().addAll(track, progressArc, percentLabel);
            setMinSize(radius * 2, radius * 2);
            setMaxSize(radius * 2, radius * 2);
        }

        /**
         * Animates the progress ring to a target value.
         *
         * @param value Target progress value (0.0 to 1.0)
         */
        public void animateTo(double value) {
            Timeline animation = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(progress, progress.get())),
                    new KeyFrame(Duration.millis(800), new KeyValue(progress, value, Interpolator.EASE_BOTH)));
            animation.play();
        }

        /**
         * Sets the progress value immediately without animation.
         *
         * @param value Progress value (0.0 to 1.0)
         */
        public void setProgress(double value) {
            progress.set(value);
        }

        /**
         * Gets the progress property for binding.
         *
         * @return The progress property
         */
        public DoubleProperty progressProperty() {
            return progress;
        }
    }

    // ========== SKELETON LOADER ==========

    /**
     * Animated skeleton loading placeholder with shimmer effect.
     * Displays a gradient animation while content is loading.
     */
    public static final class SkeletonLoader extends Region {

        private final Timeline shimmerAnimation;

        /**
         * Creates a skeleton loader with specified dimensions.
         *
         * @param width  Width of the skeleton placeholder
         * @param height Height of the skeleton placeholder
         */
        public SkeletonLoader(double width, double height) {
            Rectangle localSkeleton = new Rectangle(width, height);
            localSkeleton.setArcWidth(8);
            localSkeleton.setArcHeight(8);

            // Initial gradient
            updateGradient(localSkeleton, 0);

            // Animate gradient position
            double[] animationProgress = {0};
            shimmerAnimation = new Timeline(new KeyFrame(Duration.millis(50), e -> {
                e.consume();
                animationProgress[0] += 0.033;
                if (animationProgress[0] > 1) {
                    animationProgress[0] = 0;
                }
                updateGradient(localSkeleton, animationProgress[0]);
            }));
            shimmerAnimation.setCycleCount(Animation.INDEFINITE);
            shimmerAnimation.play();

            getChildren().add(localSkeleton);
            setMinSize(width, height);
            setMaxSize(width, height);
        }

        private static void updateGradient(Rectangle skeleton, double position) {
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
            SkeletonLoader skeletonLoader = new SkeletonLoader(width, height);
            container.getChildren().setAll(skeletonLoader);

            // 2. Load content on virtual thread
            Thread.ofVirtual().name("skeleton-load").start(() -> {
                try {
                    Node content = contentLoader.get();

                    Platform.runLater(() -> {
                        // 3. Stop skeleton animation
                        skeletonLoader.stop();

                        // 4. Crossfade to content
                        content.setOpacity(0);
                        container.getChildren().setAll(content);

                        FadeTransition fade = new FadeTransition(Duration.millis(300), content);
                        fade.setToValue(1.0);
                        fade.play();
                    });
                } catch (Exception _) {
                    Platform.runLater(() -> {
                        skeletonLoader.stop();
                        // Show error state or placeholder
                        container.getChildren().clear();
                    });
                }
            });
        }

        /**
         * Shows a skeleton loader with a Runnable callback when loading completes.
         * Useful when content is already in the container and just needs data
         * population.
         *
         * @param container  the pane to overlay with skeleton
         * @param width      skeleton width
         * @param height     skeleton height
         * @param dataLoader runnable that loads data (runs on virtual thread)
         * @param onComplete runnable called on FX thread when done
         */
        public static void loadDataWithSkeleton(
                Pane container, double width, double height, Runnable dataLoader, Runnable onComplete) {

            // 1. Create and show skeleton overlay
            SkeletonLoader skeletonLoader = new SkeletonLoader(width, height);
            Node originalContent = container.getChildren().isEmpty()
                    ? null
                    : container.getChildren().getFirst();

            // Stack skeleton on top
            if (originalContent != null) {
                originalContent.setOpacity(0.3);
            }
            container.getChildren().add(skeletonLoader);

            // 2. Load data on virtual thread
            Thread.ofVirtual().name("data-load").start(() -> {
                try {
                    dataLoader.run();
                } finally {
                    Platform.runLater(() -> {
                        // 3. Remove skeleton and restore content
                        skeletonLoader.stop();
                        container.getChildren().remove(skeletonLoader);

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
}
