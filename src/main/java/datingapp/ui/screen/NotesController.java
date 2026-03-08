package datingapp.ui.screen;

import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.NotesViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Controller for the notes browser screen. */
public final class NotesController extends BaseController implements Initializable {

    @FXML
    private BorderPane rootPane;

    @FXML
    private ListView<NotesViewModel.NoteEntry> notesListView;

    @FXML
    private VBox emptyStateBox;

    private final NotesViewModel viewModel;

    public NotesController(NotesViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);
        notesListView.setItems(viewModel.getNotes());
        notesListView.setCellFactory(_ -> new NoteEntryCell());
        emptyStateBox.visibleProperty().bind(Bindings.isEmpty(viewModel.getNotes()));
        emptyStateBox.managedProperty().bind(emptyStateBox.visibleProperty());
        notesListView.visibleProperty().bind(emptyStateBox.visibleProperty().not());
        notesListView.managedProperty().bind(notesListView.visibleProperty());
        notesListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedNote();
            }
        });

        viewModel.initialize();
        UiAnimations.fadeIn(rootPane, 800);
    }

    @FXML
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private void handleOpenSelected() {
        openSelectedNote();
    }

    private void openSelectedNote() {
        NotesViewModel.NoteEntry entry = notesListView.getSelectionModel().getSelectedItem();
        if (entry == null) {
            UiFeedbackService.showInfo("Select a note first, then open it.");
            return;
        }
        NavigationService navigationService = NavigationService.getInstance();
        navigationService.setNavigationContext(NavigationService.ViewType.MATCHING, entry.userId());
        navigationService.navigateTo(NavigationService.ViewType.MATCHING);
    }

    private static final class NoteEntryCell extends ListCell<NotesViewModel.NoteEntry> {
        @Override
        protected void updateItem(NotesViewModel.NoteEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            Label nameLabel = new Label(item.userName());
            nameLabel.getStyleClass().add("stat-label-primary");

            Label contentLabel = new Label(item.content());
            contentLabel.getStyleClass().add("text-secondary");
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(360);

            Label updatedAtLabel = new Label(item.lastModified());
            updatedAtLabel.getStyleClass().add("stat-label-small");

            VBox textBox = new VBox(4, nameLabel, contentLabel);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(12, textBox, spacer, updatedAtLabel);
            row.getStyleClass().add("achievement-card");
            setGraphic(row);
        }
    }
}
