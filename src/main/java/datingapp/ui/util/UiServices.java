package datingapp.ui.util;

import java.net.URL;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;

/**
 * Consolidated UI service utilities for cross-cutting concerns.
 * Delegates to top-level {@link Toast} and {@link ImageCache} utilities.
 *
 * <p>
 * Access via top-level classes:
 * <ul>
 * <li>{@link Toast} - Toast notification helpers</li>
 * <li>{@link ImageCache} - Static image caching with LRU eviction</li>
 * </ul>
 */
public final class UiServices {

    private UiServices() {
        // Utility class - no instantiation
    }

    /** Returns a cached avatar image or a default placeholder. */
    public static Image getAvatar(String path, double size) {
        return ImageCache.getAvatar(path, size);
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
        URL stylesheet = UiServices.class.getResource("/css/theme.css");
        if (stylesheet != null) {
            dialogPane.getStylesheets().add(stylesheet.toExternalForm());
        }
        dialogPane.getStyleClass().add("confirmation-dialog");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
