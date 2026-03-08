package datingapp.ui;

import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.model.User;
import java.net.URL;
import java.util.Objects;
import java.util.function.BiConsumer;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Utility class for common JavaFX dialogs.
 * Helps reduce controller bloat and ensures consistent styling and behavior.
 */
public final class UiDialogs {

    private UiDialogs() {
        // Prevent instantiation
    }

    /**
     * Shows a report user dialog and calls the provider consumer with the result.
     *
     * @param targetName The name of the user being reported
     * @param onReport Consumer called with (Reason, Description) if reported
     */
    public static void showReportDialog(String targetName, BiConsumer<Report.Reason, String> onReport) {
        Dialog<Report.Reason> dialog = new Dialog<>();
        dialog.setTitle("Report User");
        dialog.setHeaderText("Report " + targetName);

        applyTheme(dialog);

        ChoiceBox<Report.Reason> reasonBox = new ChoiceBox<>();
        reasonBox.getItems().addAll(Report.Reason.values());
        reasonBox.setConverter(UiUtils.createEnumStringConverter(r -> r.name().replace('_', ' ')));
        reasonBox.setValue(Report.Reason.INAPPROPRIATE_CONTENT);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20;");
        content.getChildren().addAll(new Label("Select a reason:"), reasonBox);
        dialog.getDialogPane().setContent(content);

        ButtonType reportBtn = new ButtonType("Report", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(reportBtn, cancelBtn);

        dialog.setResultConverter(bt -> reportBtn.equals(bt) ? reasonBox.getValue() : null);
        dialog.showAndWait().ifPresent(reason -> onReport.accept(reason, null));
    }

    public static void showReportDialog(User targetUser, BiConsumer<Report.Reason, String> onReport) {
        Objects.requireNonNull(targetUser, "targetUser cannot be null");
        showReportDialog(targetUser.getName(), onReport);
    }

    /**
     * Shows a confirmation dialog and, if confirmed, executes the action and shows a success message.
     *
     * @param title Dialog title
     * @param header Header text
     * @param body Body text
     * @param action Action to execute on confirmation
     * @param successMessage Success message to show after action
     */
    public static void confirmAndExecute(
            String title, String header, String body, Runnable action, String successMessage) {
        if (UiFeedbackService.showConfirmation(title, header, body)) {
            action.run();
            if (successMessage != null && !successMessage.isBlank()) {
                UiFeedbackService.showSuccess(successMessage);
            }
        }
    }

    private static void applyTheme(Dialog<?> dialog) {
        URL stylesheet = UiDialogs.class.getResource("/css/theme.css");
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
    }
}
