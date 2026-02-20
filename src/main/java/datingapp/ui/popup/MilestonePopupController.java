package datingapp.ui.popup;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controller for Milestone and Match Popups.
 * Required to resolve FXML missing controller warnings.
 */
@SuppressWarnings("PMD.UnusedPrivateMethod")
public class MilestonePopupController {

    @FXML
    private StackPane rootPane;

    @FXML
    private Canvas confettiCanvas;

    @FXML
    private FontIcon achievementIcon;

    @FXML
    private Label titleLabel;

    @FXML
    private Label nameLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label xpLabel;

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
