package datingapp.ui.popup;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controller for Match Popups.
 */
@SuppressWarnings("PMD.UnusedPrivateMethod")
public class MatchPopupController {

    @FXML
    private StackPane rootPane;

    @FXML
    private Canvas confettiCanvas;

    @FXML
    private FontIcon leftAvatarIcon;

    @FXML
    private FontIcon rightAvatarIcon;

    @FXML
    private FontIcon heartIcon;

    @FXML
    private Label matchMessage;

    @FXML
    public void initialize() {
        // Init logic if needed
    }

    @FXML
    private void handleClose() {
        if (rootPane != null
                && rootPane.getScene() != null
                && rootPane.getScene().getWindow() != null) {
            rootPane.getScene().getWindow().hide();
        }
    }

    @FXML
    private void handleMessage() {
        handleClose();
    }

    @FXML
    private void handleContinue() {
        handleClose();
    }
}
