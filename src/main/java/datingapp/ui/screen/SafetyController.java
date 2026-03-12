package datingapp.ui.screen;

import datingapp.ui.UiAnimations;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.UiUtils;
import datingapp.ui.viewmodel.SafetyViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Controller for the Safety & Privacy screen. */
@SuppressWarnings("unused") // FXML-injected members and handlers are referenced from FXML.
public final class SafetyController extends BaseController implements Initializable {

    @FXML
    private BorderPane rootPane;

    @FXML
    private ListView<SafetyViewModel.BlockedUserEntry> blockedUsersListView;

    @FXML
    private VBox emptyStateBox;

    @FXML
    private ComboBox<datingapp.core.model.User.VerificationMethod> verificationMethodCombo;

    @FXML
    private TextField verificationContactField;

    @FXML
    private TextField verificationCodeField;

    @FXML
    private Button startVerificationButton;

    @FXML
    private Button confirmVerificationButton;

    @FXML
    private Button deleteAccountButton;

    private final SafetyViewModel viewModel;

    public SafetyController(SafetyViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);
        configureVerificationControls();
        blockedUsersListView.setItems(viewModel.getBlockedUsers());
        blockedUsersListView.setCellFactory(_ -> new BlockedUserCell());
        emptyStateBox.visibleProperty().bind(Bindings.isEmpty(viewModel.getBlockedUsers()));
        emptyStateBox.managedProperty().bind(emptyStateBox.visibleProperty());
        blockedUsersListView
                .visibleProperty()
                .bind(emptyStateBox.visibleProperty().not());
        blockedUsersListView.managedProperty().bind(blockedUsersListView.visibleProperty());

        addSubscription(viewModel.statusMessageProperty().subscribe(this::showStatusMessage));
        addSubscription(viewModel.accountDeletedProperty().subscribe(deleted -> {
            if (Boolean.TRUE.equals(deleted)) {
                cleanup();
                datingapp.ui.NavigationService.getInstance().navigateTo(datingapp.ui.NavigationService.ViewType.LOGIN);
            }
        }));
        viewModel.initialize();
        UiAnimations.fadeIn(rootPane, 800);
    }

    private void configureVerificationControls() {
        if (verificationMethodCombo != null) {
            verificationMethodCombo.getItems().setAll(datingapp.core.model.User.VerificationMethod.values());
            verificationMethodCombo.setConverter(
                    UiUtils.createEnumStringConverter(datingapp.core.model.User.VerificationMethod::name));
            verificationMethodCombo.valueProperty().bindBidirectional(viewModel.verificationMethodProperty());
        }
        if (verificationContactField != null) {
            verificationContactField.textProperty().bindBidirectional(viewModel.verificationContactProperty());
        }
        if (verificationCodeField != null) {
            verificationCodeField.textProperty().bindBidirectional(viewModel.verificationCodeProperty());
        }
    }

    private void showStatusMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        UiFeedbackService.showSuccess(message);
        viewModel.clearStatusMessage();
    }

    @FXML
    private void handleStartVerification() {
        viewModel.startVerification();
    }

    @FXML
    private void handleConfirmVerification() {
        viewModel.confirmVerification();
    }

    @FXML
    private void handleDeleteAccount() {
        boolean confirmed = UiFeedbackService.showConfirmation(
                "Delete account",
                "Delete your account?",
                "Your profile will be soft-deleted, hidden from discovery, and you will be signed out.");
        if (confirmed) {
            viewModel.deleteCurrentAccount();
        }
    }

    private final class BlockedUserCell extends ListCell<SafetyViewModel.BlockedUserEntry> {
        @Override
        protected void updateItem(SafetyViewModel.BlockedUserEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            Label nameLabel = new Label(item.name());
            nameLabel.getStyleClass().add("stat-label-primary");

            Label subtitleLabel = new Label(item.blockedAtLabel());
            subtitleLabel.getStyleClass().add("text-secondary");

            VBox textBox = new VBox(4, nameLabel, subtitleLabel);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button unblockButton = new Button("Unblock");
            unblockButton.getStyleClass().add("button-secondary");
            unblockButton.setOnAction(event -> {
                event.consume();
                boolean confirmed = UiFeedbackService.showConfirmation(
                        "Unblock user",
                        "Unblock " + item.name() + "?",
                        "They can appear in your recommendations and matches again.");
                if (confirmed) {
                    viewModel.unblockUser(item.userId());
                }
            });

            HBox row = new HBox(12, textBox, spacer, unblockButton);
            row.setFillHeight(true);
            row.getStyleClass().add("achievement-card");
            setGraphic(row);
        }
    }
}
