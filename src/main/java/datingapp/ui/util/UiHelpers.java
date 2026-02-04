package datingapp.ui.util;

import java.util.regex.Pattern;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;

/**
 * Consolidated UI helper utilities for the JavaFX application.
 * Contains: ResponsiveController (interface), ValidationHelper
 */
public final class UiHelpers {

    private UiHelpers() {
        // Utility class
    }

    // ========== RESPONSIVE CONTROLLER INTERFACE ==========

    /**
     * Interface for controllers that support responsive layout changes.
     * Implement this in controllers that need to adapt their UI based on window
     * size.
     */
    public interface ResponsiveController {

        /**
         * Called when the window enters compact mode (width < 900px).
         * Controllers should hide non-essential UI elements and use single-column
         * layouts.
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
         * @param field      The text field to validate
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
         * @param field      The text field to validate
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
         * @param field      The text field to validate
         * @param errorLabel Optional label to show error message
         * @param min        Minimum required length
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
         * @param area       The text area to validate
         * @param errorLabel Optional label to show error message
         * @param min        Minimum required length
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
         * @param control    The text input control
         * @param errorLabel Optional error label to hide
         */
        public static void clearValidation(TextInputControl control, Label errorLabel) {
            control.getStyleClass().removeAll(ERROR_CLASS, SUCCESS_CLASS);
            hideErrorLabel(errorLabel);
        }
    }
}
