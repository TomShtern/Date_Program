package datingapp.ui.screen;

import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.AdminViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Controller for the moderation admin screen. */
@SuppressWarnings("unused") // FXML-injected members and handlers are referenced from FXML.
public final class AdminController extends BaseController implements Initializable {

    @FXML
    private BorderPane rootPane;

    @FXML
    private ListView<AdminViewModel.PendingReport> pendingReportsListView;

    @FXML
    private VBox emptyStateBox;

    @FXML
    private Label statusLabel;

    @FXML
    private Button refreshButton;

    @FXML
    private Button banButton;

    @FXML
    private Button dismissButton;

    @FXML
    private Button upholdButton;

    private final AdminViewModel viewModel;

    public AdminController(AdminViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);
        pendingReportsListView.setItems(viewModel.getPendingReports());
        pendingReportsListView.setCellFactory(_ -> new PendingReportCell());
        emptyStateBox.visibleProperty().bind(Bindings.isEmpty(viewModel.getPendingReports()));
        emptyStateBox.managedProperty().bind(emptyStateBox.visibleProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        viewModel.refresh();
    }

    @FXML
    private void handleRefresh() {
        viewModel.refresh();
    }

    @FXML
    private void handleBan() {
        viewModel.ban(pendingReportsListView.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void handleDismiss() {
        viewModel.dismiss(pendingReportsListView.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void handleUphold() {
        viewModel.uphold(pendingReportsListView.getSelectionModel().getSelectedItem());
    }

    private static final class PendingReportCell extends ListCell<AdminViewModel.PendingReport> {

        @Override
        protected void updateItem(AdminViewModel.PendingReport report, boolean empty) {
            super.updateItem(report, empty);
            if (empty || report == null) {
                setText(null);
                return;
            }
            setText(report.name() + " · " + report.reportCount() + " report(s)");
        }
    }
}
