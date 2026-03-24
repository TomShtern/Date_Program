package datingapp.ui.screen;

import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.NotesViewModel;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

    @FXML
    private TextArea noteEditorArea;

    @FXML
    private Button saveSelectedNoteButton;

    @FXML
    private Button deleteSelectedNoteButton;

    @FXML
    private Button openSelectedButton;

    private final NotesViewModel viewModel;

    public NotesController(NotesViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);
        notesListView.setItems(viewModel.getNotes());
        notesListView.setCellFactory(_ -> new NoteEntryCell());
        bindSelectionSync();
        bindListVisibility();
        bindEditorControls();
        wireListActions();

        viewModel.initialize();
        UiAnimations.fadeIn(rootPane, 800);
    }

    @FXML
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private void handleSaveSelectedNote() {
        viewModel.saveSelectedNote();
    }

    @FXML
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private void handleDeleteSelectedNote() {
        viewModel.deleteSelectedNote();
        notesListView.getSelectionModel().clearSelection();
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

    private void bindSelectionSync() {
        addSubscription(notesListView.getSelectionModel().selectedItemProperty().subscribe(viewModel::selectNote));
        addSubscription(viewModel.selectedNoteProperty().subscribe(this::syncListSelection));
    }

    private void syncListSelection(NotesViewModel.NoteEntry note) {
        if (note == null) {
            if (!notesListView.getSelectionModel().isEmpty()) {
                notesListView.getSelectionModel().clearSelection();
            }
            return;
        }

        Platform.runLater(() -> selectMatchingListItem(note));
    }

    private void selectMatchingListItem(NotesViewModel.NoteEntry note) {
        NotesViewModel.NoteEntry selectedItem =
                notesListView.getSelectionModel().getSelectedItem();
        if (Objects.equals(selectedItem, note)) {
            return;
        }

        for (int i = 0; i < notesListView.getItems().size(); i++) {
            if (Objects.equals(notesListView.getItems().get(i), note)) {
                notesListView.getSelectionModel().select(i);
                return;
            }
        }
    }

    private void bindListVisibility() {
        emptyStateBox.visibleProperty().bind(Bindings.isEmpty(viewModel.getNotes()));
        emptyStateBox.managedProperty().bind(emptyStateBox.visibleProperty());
        notesListView.visibleProperty().bind(emptyStateBox.visibleProperty().not());
        notesListView.managedProperty().bind(notesListView.visibleProperty());
    }

    private void bindEditorControls() {
        if (openSelectedButton != null) {
            openSelectedButton.disableProperty().bind(Bindings.isEmpty(viewModel.getNotes()));
        }
        if (noteEditorArea != null) {
            noteEditorArea.textProperty().bindBidirectional(viewModel.selectedNoteContentProperty());
            noteEditorArea
                    .disableProperty()
                    .bind(viewModel.selectedNoteProperty().isNull().or(viewModel.selectedNoteBusyProperty()));
        }
        if (saveSelectedNoteButton != null) {
            saveSelectedNoteButton
                    .disableProperty()
                    .bind(viewModel
                            .selectedNoteProperty()
                            .isNull()
                            .or(viewModel.selectedNoteBusyProperty())
                            .or(Bindings.createBooleanBinding(
                                    () -> {
                                        String noteBody = viewModel
                                                .selectedNoteContentProperty()
                                                .get();
                                        return noteBody == null || noteBody.isBlank();
                                    },
                                    viewModel.selectedNoteContentProperty())));
        }
        if (deleteSelectedNoteButton != null) {
            deleteSelectedNoteButton
                    .disableProperty()
                    .bind(viewModel.selectedNoteProperty().isNull().or(viewModel.selectedNoteBusyProperty()));
        }
    }

    private void wireListActions() {
        notesListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedNote();
            }
        });
        notesListView.setOnKeyPressed(this::handleNotesListKeyPress);
    }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private void handleNotesListKeyPress(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
            openSelectedNote();
            e.consume();
        }
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

            Label contentLabel = new Label(previewText(item.content()));
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

        private static String previewText(String content) {
            if (content == null || content.isBlank()) {
                return "";
            }
            int maxLength = 120;
            if (content.length() <= maxLength) {
                return content;
            }
            return content.substring(0, maxLength - 3) + "...";
        }
    }
}
