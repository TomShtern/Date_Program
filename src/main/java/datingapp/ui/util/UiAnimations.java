package datingapp.ui.util;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Utility class for common UI animations.
 */
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
}
