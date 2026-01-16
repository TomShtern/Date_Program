package datingapp.ui.component;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

/**
 * Animated circular progress ring with percentage display.
 * Shows a smooth arc animation as progress changes.
 */
public class ProgressRing extends StackPane {

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

        // Center label (must be initialized before adding progress listener)
        percentLabel = new Label("0%");
        percentLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

        // Bind arc length to progress
        progress.addListener((obs, oldVal, newVal) -> {
            progressArc.setLength(-360 * newVal.doubleValue());
            percentLabel.setText(String.format("%.0f%%", newVal.doubleValue() * 100));
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
