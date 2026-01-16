package datingapp.ui.component;

import javafx.animation.Animation;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Animated typing indicator showing bouncing dots.
 * Used in chat interfaces to indicate the other user is typing.
 */
public class TypingIndicator extends HBox {

    private static final int DOT_COUNT = 3;
    private static final double DOT_RADIUS = 4;
    private static final Duration BOUNCE_DURATION = Duration.millis(400);

    /**
     * Creates a new typing indicator with 3 bouncing dots.
     */
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
            bounce.play();
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
