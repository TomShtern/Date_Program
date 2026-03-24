package datingapp.ui.screen;

import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiConstants;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.StandoutsViewModel;
import datingapp.ui.viewmodel.StandoutsViewModel.StandoutEntry;
import java.net.URL;
import java.util.Comparator;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

/**
 * Controller for the Standouts screen (standouts.fxml).
 * Shows today's top recommended profiles and allows the user to navigate
 * to matching to interact with them.
 */
public class StandoutsController extends BaseController implements Initializable {

    private static final String SORT_RANK = "Rank (Top first)";
    private static final String SORT_SCORE = "Score (High to low)";
    private static final String SORT_NAME_AZ = "Name (A-Z)";

    @FXML
    private BorderPane rootPane;

    @FXML
    private ListView<StandoutEntry> standoutsListView;

    @FXML
    private Label statusLabel;

    @FXML
    private ComboBox<String> sortComboBox;

    @FXML
    private TextField filterTextField;

    private final StandoutsViewModel viewModel;
    private FilteredList<StandoutEntry> filteredStandouts;
    private SortedList<StandoutEntry> sortedStandouts;

    public StandoutsController(StandoutsViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);

        filteredStandouts = new FilteredList<>(viewModel.getStandouts(), entry -> true);
        sortedStandouts = new SortedList<>(filteredStandouts, comparatorFor(SORT_RANK));
        standoutsListView.setItems(sortedStandouts);
        standoutsListView.setCellFactory(lv -> new StandoutListCell(this::handleViewProfileClicked));
        configureSortAndFilterControls();

        // Show status message (e.g. "No standouts today") when list is empty
        addSubscription(viewModel.statusMessageProperty().subscribe(msg -> {
            boolean hasMessage = msg != null && !msg.isEmpty();
            statusLabel.setText(hasMessage ? msg : "");
            statusLabel.setVisible(hasMessage);
            statusLabel.setManaged(hasMessage);
        }));

        setupAccessibilityMetadata();

        viewModel.initialize();
        UiAnimations.fadeIn(rootPane, 800);
    }

    private void configureSortAndFilterControls() {
        sortComboBox.getItems().setAll(SORT_RANK, SORT_SCORE, SORT_NAME_AZ);
        sortComboBox.getSelectionModel().select(SORT_RANK);
        sortComboBox
                .valueProperty()
                .addListener((obs, previous, next) -> sortedStandouts.setComparator(comparatorFor(next)));

        filterTextField
                .textProperty()
                .addListener(
                        (obs, previous, query) -> filteredStandouts.setPredicate(entry -> matchesFilter(entry, query)));
    }

    private static Comparator<StandoutEntry> comparatorFor(String sortOption) {
        if (SORT_NAME_AZ.equals(sortOption)) {
            return Comparator.comparing(entry -> entry.displayName().toLowerCase(Locale.ROOT));
        }
        if (SORT_SCORE.equals(sortOption)) {
            return Comparator.comparingInt(StandoutEntry::score).reversed().thenComparingInt(StandoutEntry::rank);
        }
        return Comparator.comparingInt(StandoutEntry::rank)
                .thenComparing(Comparator.comparingInt(StandoutEntry::score).reversed())
                .thenComparing(entry -> entry.displayName().toLowerCase(Locale.ROOT));
    }

    private static boolean matchesFilter(StandoutEntry entry, String query) {
        if (entry == null) {
            return false;
        }
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        String name = entry.displayName() == null ? "" : entry.displayName().toLowerCase(Locale.ROOT);
        String reason = entry.reason() == null ? "" : entry.reason().toLowerCase(Locale.ROOT);
        return name.contains(normalizedQuery) || reason.contains(normalizedQuery);
    }

    private void handleViewProfileClicked(StandoutEntry entry) {
        if (entry == null) {
            return;
        }
        viewModel.markInteracted(entry);
        NavigationService.getInstance().setNavigationContext(NavigationService.ViewType.PROFILE_VIEW, entry.userId());
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.PROFILE_VIEW);
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleRefresh() {
        viewModel.loadStandouts();
    }

    private void setupAccessibilityMetadata() {
        if (standoutsListView != null) {
            standoutsListView.setAccessibleText(
                    "Today's standout profiles. Use the View Profile button on each row to open a profile.");
        }
        if (statusLabel != null) {
            statusLabel.setAccessibleText("Standouts status message");
        }
        if (sortComboBox != null) {
            sortComboBox.setAccessibleText("Sort standout profiles");
        }
        if (filterTextField != null) {
            filterTextField.setAccessibleText("Filter standout profiles by name or reason");
        }
    }

    /** Cell that displays a standout entry with rank, name, score, and reason. */
    private static class StandoutListCell extends ListCell<StandoutEntry> {

        @FunctionalInterface
        private interface OnViewProfileClicked {
            void handle(StandoutEntry entry);
        }

        private final HBox container = new HBox(16);
        private final Label rankLabel = new Label();
        private final VBox infoBox = new VBox(4);
        private final Label nameLabel = new Label();
        private final Label scoreLabel = new Label();
        private final Label reasonLabel = new Label();
        private final Region spacer = new Region();
        private final Button viewButton = new Button("View Profile");
        private final OnViewProfileClicked onViewProfileClicked;

        public StandoutListCell(OnViewProfileClicked callback) {
            this.onViewProfileClicked = callback;
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
            viewButton.setAccessibleText("View profile for standout candidate");

            HBox.setHgrow(spacer, Priority.ALWAYS);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(
                    UiConstants.PADDING_MEDIUM,
                    UiConstants.PADDING_LARGE,
                    UiConstants.PADDING_MEDIUM,
                    UiConstants.PADDING_LARGE));
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

                // Button click triggers explicit navigation via callback
                viewButton.setOnAction(event -> {
                    event.consume();
                    if (onViewProfileClicked != null) {
                        onViewProfileClicked.handle(entry);
                    }
                });

                setGraphic(container);
                setStyle("-fx-background-color: transparent;");
            }
        }
    }
}
