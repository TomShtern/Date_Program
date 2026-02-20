package datingapp.ui.screen;

import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.StandoutsViewModel;
import datingapp.ui.viewmodel.StandoutsViewModel.StandoutEntry;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Controller for the Standouts screen (standouts.fxml).
 * Shows today's top recommended profiles and allows the user to navigate
 * to matching to interact with them.
 */
public class StandoutsController extends BaseController implements Initializable {

    @FXML
    private BorderPane rootPane;

    @FXML
    private ListView<StandoutEntry> standoutsListView;

    @FXML
    private Label statusLabel;

    private final StandoutsViewModel viewModel;

    public StandoutsController(StandoutsViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);

        standoutsListView.setItems(viewModel.getStandouts());
        standoutsListView.setCellFactory(lv -> new StandoutListCell());

        // Show status message (e.g. "No standouts today") when list is empty
        addSubscription(viewModel.statusMessageProperty().subscribe(msg -> {
            boolean hasMessage = msg != null && !msg.isEmpty();
            statusLabel.setText(hasMessage ? msg : "");
            statusLabel.setVisible(hasMessage);
            statusLabel.setManaged(hasMessage);
        }));

        // On item selection: mark interacted and navigate to matching
        addSubscription(
                standoutsListView.getSelectionModel().selectedItemProperty().subscribe(this::handleStandoutSelected));

        viewModel.initialize();
        UiAnimations.fadeIn(rootPane, 800);
    }

    private void handleStandoutSelected(StandoutEntry entry) {
        if (entry == null) {
            return;
        }
        viewModel.markInteracted(entry);
        // Navigate to matching so the user can swipe on this candidate
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHING);
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleRefresh() {
        viewModel.loadStandouts();
    }

    /** Cell that displays a standout entry with rank, name, score, and reason. */
    private static class StandoutListCell extends ListCell<StandoutEntry> {

        private final HBox container = new HBox(16);
        private final Label rankLabel = new Label();
        private final VBox infoBox = new VBox(4);
        private final Label nameLabel = new Label();
        private final Label scoreLabel = new Label();
        private final Label reasonLabel = new Label();
        private final Region spacer = new Region();
        private final Button viewButton = new Button("View Profile");

        public StandoutListCell() {
            rankLabel.setStyle(
                    "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #fbbf24; -fx-min-width: 40;");

            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
            scoreLabel.getStyleClass().add("text-secondary");
            scoreLabel.setStyle("-fx-font-size: 12px;");
            reasonLabel.getStyleClass().add("text-secondary");
            reasonLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #a855f7;");

            infoBox.getChildren().addAll(nameLabel, scoreLabel, reasonLabel);

            viewButton.getStyleClass().add("button-primary");
            viewButton.setStyle("-fx-font-size: 12px; -fx-padding: 6 14;");

            HBox.setHgrow(spacer, Priority.ALWAYS);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(12, 16, 12, 16));
            container.getChildren().addAll(rankLabel, infoBox, spacer, viewButton);
        }

        @Override
        protected void updateItem(StandoutEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
            } else {
                rankLabel.setText("#" + entry.rank());
                nameLabel.setText(entry.displayName());
                scoreLabel.setText(entry.score() + "% compatibility");

                String reason = entry.reason();
                if (reason != null && !reason.isEmpty()) {
                    reasonLabel.setText(reason);
                    reasonLabel.setVisible(true);
                    reasonLabel.setManaged(true);
                } else {
                    reasonLabel.setVisible(false);
                    reasonLabel.setManaged(false);
                }

                // Button click triggers selection, which the controller handles
                viewButton.setOnAction(event -> {
                    event.consume();
                    getListView().getSelectionModel().select(entry);
                });

                setGraphic(container);
                setStyle("-fx-background-color: transparent;");
            }
        }
    }
}
