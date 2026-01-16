package datingapp.ui.util;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Singleton service for displaying toast notifications.
 *
 * <p>
 * Toasts are non-blocking, auto-dismissing notifications that appear at
 * the bottom of the screen. They support four levels: SUCCESS, ERROR, WARNING,
 * INFO.
 *
 * <p>
 * Usage:
 *
 * <pre>
 * // In controller's initialize():
 * ToastService.getInstance().setContainer((StackPane) rootPane);
 *
 * // Show notifications:
 * ToastService.getInstance().showSuccess("Profile saved!");
 * ToastService.getInstance().showError("Connection failed");
 * </pre>
 */
public final class ToastService {

    private static ToastService instance;
    private StackPane toastContainer;

    private ToastService() {
        // Private constructor for singleton
    }

    /**
     * Gets the singleton instance.
     */
    public static ToastService getInstance() {
        if (instance == null) {
            instance = new ToastService();
        }
        return instance;
    }

    /**
     * Sets the container pane where toasts will be displayed.
     * Must be called before showing any toasts.
     *
     * @param container the StackPane to use as toast container
     */
    public void setContainer(StackPane container) {
        this.toastContainer = container;
    }

    /**
     * Shows a success toast with green accent.
     */
    public void showSuccess(String message) {
        show(message, ToastLevel.SUCCESS, Duration.seconds(3));
    }

    /**
     * Shows an error toast with red accent.
     */
    public void showError(String message) {
        show(message, ToastLevel.ERROR, Duration.seconds(5));
    }

    /**
     * Shows a warning toast with amber accent.
     */
    public void showWarning(String message) {
        show(message, ToastLevel.WARNING, Duration.seconds(4));
    }

    /**
     * Shows an info toast with blue accent.
     */
    public void showInfo(String message) {
        show(message, ToastLevel.INFO, Duration.seconds(3));
    }

    private void show(String message, ToastLevel level, Duration duration) {
        if (toastContainer == null) {
            return;
        }

        HBox toast = createToast(message, level);
        toastContainer.getChildren().add(toast);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 30, 0));

        // Initial state for entrance animation
        toast.setOpacity(0);
        toast.setTranslateY(50);

        // Entrance animation: fade in + slide up
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), toast);
        fadeIn.setToValue(1);

        TranslateTransition slideUp = new TranslateTransition(Duration.millis(200), toast);
        slideUp.setToY(0);

        ParallelTransition entrance = new ParallelTransition(fadeIn, slideUp);
        entrance.play();

        // Auto-dismiss after duration
        PauseTransition pause = new PauseTransition(duration);
        pause.setOnFinished(e -> dismiss(toast));
        pause.play();
    }

    private void dismiss(HBox toast) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> toastContainer.getChildren().remove(toast));
        fadeOut.play();
    }

    private HBox createToast(String message, ToastLevel level) {
        HBox toast = new HBox(12);
        toast.getStyleClass().addAll("toast", "toast-" + level.name().toLowerCase());
        toast.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon(level.getIcon());
        icon.setIconSize(20);

        Label label = new Label(message);
        label.getStyleClass().add("toast-message");

        toast.getChildren().addAll(icon, label);
        toast.setMaxWidth(400);
        return toast;
    }

    /**
     * Toast notification levels with associated icons.
     */
    public enum ToastLevel {
        SUCCESS("mdi2c-check-circle"),
        ERROR("mdi2a-alert-circle"),
        WARNING("mdi2a-alert"),
        INFO("mdi2i-information");

        private final String icon;

        ToastLevel(String icon) {
            this.icon = icon;
        }

        public String getIcon() {
            return icon;
        }
    }
}
