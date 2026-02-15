package datingapp.ui.feedback;

import datingapp.ui.animation.UiAnimations;
import datingapp.ui.constants.UiConstants;
import datingapp.ui.util.ImageCache;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Consolidated UI feedback service for JavaFX controllers and view models.
 *
 * <p>Provides confirmation dialogs, validation helpers, responsive controller
 * hooks, avatar access, and non-blocking toast notifications.
 */
public final class UiFeedbackService {
    private static StackPane toastContainer;

    private UiFeedbackService() {
        // Utility class
    }

    /** Returns a cached avatar image or a default placeholder. */
    public static Image getAvatar(String path, double size) {
        return ImageCache.getAvatar(path, size);
    }

    /** Clears validation styling for a text input control. */
    public static void clearValidation(TextInputControl control, Label errorLabel) {
        ValidationHelper.clearValidation(control, errorLabel);
    }

    /**
     * Shows a confirmation dialog and returns true if user confirms.
     *
     * @param title Dialog title
     * @param header Header text (can be null)
     * @param content Detailed message
     * @return true if user clicked OK, false otherwise
     */
    public static boolean showConfirmation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        DialogPane dialogPane = alert.getDialogPane();
        URL stylesheet = UiFeedbackService.class.getResource("/css/theme.css");
        if (stylesheet != null) {
            dialogPane.getStylesheets().add(stylesheet.toExternalForm());
        }
        dialogPane.getStyleClass().add("confirmation-dialog");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
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

    // ========== RESPONSIVE CONTROLLER INTERFACE ==========

    /**
     * Interface for controllers that support responsive layout changes.
     * Implement this in controllers that need to adapt their UI based on window size.
     */
    public interface ResponsiveController {

        /**
         * Called when the window enters compact mode (width < 900px).
         * Controllers should hide non-essential UI elements and use single-column layouts.
         *
         * @param compact true to enable compact mode, false for normal mode
         */
        void setCompactMode(boolean compact);

        /**
         * Called when the window enters expanded mode (width > 1100px).
         * Controllers can show additional UI elements and use wider layouts.
         *
         * @param expanded true to enable expanded mode, false for normal mode
         */
        default void setExpandedMode(boolean expanded) {
            // Default implementation does nothing
        }
    }

    // ========== VALIDATION HELPER ==========

    /**
     * Utility class for form field validation with visual feedback.
     * Integrates with {@link UiAnimations} for shake animations on errors.
     */
    public static final class ValidationHelper {

        private static final String ERROR_CLASS = "error";
        private static final String SUCCESS_CLASS = "success";
        private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]++@(?:[\\w-]++\\.)++[\\w-]{2,4}$");

        private ValidationHelper() {
            // Utility class
        }

        /**
         * Validates that a text field is not empty.
         *
         * @param field The text field to validate
         * @param errorLabel Optional label to show error message
         * @return true if valid, false otherwise
         */
        public static boolean validateRequired(TextField field, Label errorLabel) {
            if (field.getText() == null || field.getText().trim().isEmpty()) {
                markError(field, errorLabel, "This field is required");
                return false;
            }
            markSuccess(field, errorLabel);
            return true;
        }

        /**
         * Validates that a text field contains a valid email address.
         *
         * @param field The text field to validate
         * @param errorLabel Optional label to show error message
         * @return true if valid, false otherwise
         */
        public static boolean validateEmail(TextField field, Label errorLabel) {
            String email = field.getText();
            if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
                markError(field, errorLabel, "Please enter a valid email");
                return false;
            }
            markSuccess(field, errorLabel);
            return true;
        }

        /**
         * Validates that a text field meets a minimum length requirement.
         *
         * @param field The text field to validate
         * @param errorLabel Optional label to show error message
         * @param min Minimum required length
         * @return true if valid, false otherwise
         */
        public static boolean validateMinLength(TextField field, Label errorLabel, int min) {
            if (field.getText() == null || field.getText().length() < min) {
                markError(field, errorLabel, "Minimum " + min + " characters required");
                return false;
            }
            markSuccess(field, errorLabel);
            return true;
        }

        /**
         * Validates that a text area meets a minimum length requirement.
         *
         * @param area The text area to validate
         * @param errorLabel Optional label to show error message
         * @param min Minimum required length
         * @return true if valid, false otherwise
         */
        public static boolean validateTextAreaMinLength(TextArea area, Label errorLabel, int min) {
            if (area.getText() == null || area.getText().length() < min) {
                markError(area, errorLabel, "Minimum " + min + " characters required");
                return false;
            }
            markSuccess(area, errorLabel);
            return true;
        }

        /** Marks a text field as having an error with visual feedback. */
        private static void markError(TextInputControl control, Label errorLabel, String message) {
            control.getStyleClass().removeAll(ERROR_CLASS, SUCCESS_CLASS);
            control.getStyleClass().add(ERROR_CLASS);
            showErrorLabel(errorLabel, message);
            UiAnimations.playShake(control);
        }

        private static void showErrorLabel(Label errorLabel, String message) {
            if (errorLabel != null) {
                errorLabel.setText(message);
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            }
        }

        /** Marks a text field as valid with visual feedback. */
        private static void markSuccess(TextInputControl control, Label errorLabel) {
            control.getStyleClass().removeAll(ERROR_CLASS, SUCCESS_CLASS);
            control.getStyleClass().add(SUCCESS_CLASS);
            hideErrorLabel(errorLabel);
        }

        private static void hideErrorLabel(Label errorLabel) {
            if (errorLabel != null) {
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
            }
        }

        /**
         * Clears all validation styling from a text input control.
         *
         * @param control The text input control
         * @param errorLabel Optional error label to hide
         */
        public static void clearValidation(TextInputControl control, Label errorLabel) {
            control.getStyleClass().removeAll(ERROR_CLASS, SUCCESS_CLASS);
            hideErrorLabel(errorLabel);
        }
    }

    /** Toast notification levels with associated icons. */
    public static enum ToastLevel {
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
