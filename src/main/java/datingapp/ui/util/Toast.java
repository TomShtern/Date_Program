package datingapp.ui.util;

import datingapp.ui.constants.UiConstants;
import java.util.Locale;
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
 * Service for displaying toast notifications.
 *
 * <p>Toasts are non-blocking, auto-dismissing notifications that appear at the bottom
 * of the screen. They support four levels: SUCCESS, ERROR, WARNING, INFO.
 */
public final class Toast {
    private static StackPane toastContainer;

    private Toast() {
        // Utility class
    }

    /**
     * Sets the container pane where toasts will be displayed.
     * Must be called before showing any toasts.
     *
     * @param container the StackPane to use as toast container
     */
    public static void setContainer(StackPane container) {
        toastContainer = container;
    }

    /** Shows a success toast with green accent. */
    public static void showSuccess(String message) {
        show(message, ToastLevel.SUCCESS, UiConstants.TOAST_SUCCESS_DURATION);
    }

    /** Shows an error toast with red accent. */
    public static void showError(String message) {
        show(message, ToastLevel.ERROR, UiConstants.TOAST_ERROR_DURATION);
    }

    /** Shows a warning toast with amber accent. */
    public static void showWarning(String message) {
        show(message, ToastLevel.WARNING, UiConstants.TOAST_WARNING_DURATION);
    }

    /** Shows an info toast with blue accent. */
    public static void showInfo(String message) {
        show(message, ToastLevel.INFO, UiConstants.TOAST_INFO_DURATION);
    }

    private static void show(String message, ToastLevel level, Duration duration) {
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
        FadeTransition fadeIn = new FadeTransition(UiConstants.TOAST_ENTRANCE_DURATION, toast);
        fadeIn.setToValue(1);

        TranslateTransition slideUp = new TranslateTransition(UiConstants.TOAST_ENTRANCE_DURATION, toast);
        slideUp.setToY(0);

        ParallelTransition entrance = new ParallelTransition(fadeIn, slideUp);
        entrance.play();

        // Auto-dismiss after duration
        PauseTransition pause = new PauseTransition(duration);
        pause.setOnFinished(e -> {
            e.consume();
            dismiss(toast);
        });
        pause.play();
    }

    private static void dismiss(HBox toast) {
        FadeTransition fadeOut = new FadeTransition(UiConstants.TOAST_EXIT_DURATION, toast);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            e.consume();
            toastContainer.getChildren().remove(toast);
        });
        fadeOut.play();
    }

    private static HBox createToast(String message, ToastLevel level) {
        HBox toastBox = new HBox(12);
        toastBox.getStyleClass().addAll("toast", "toast-" + level.name().toLowerCase(Locale.ROOT));
        toastBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon(level.getIcon());
        icon.setIconSize(20);

        Label label = new Label(message);
        label.getStyleClass().add("toast-message");

        toastBox.getChildren().addAll(icon, label);
        toastBox.setMaxWidth(400);
        return toastBox;
    }

    /** Toast notification levels with associated icons. */
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
