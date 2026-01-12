package datingapp.ui.controller;

import datingapp.core.Achievement;
import datingapp.ui.NavigationService;
import datingapp.ui.ViewFactory;
import datingapp.ui.viewmodel.StatsViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Controller for the Stats screen (stats.fxml).
 */
public class StatsController implements Initializable {

    @FXML
    private ListView<Achievement> achievementListView;

    @FXML
    private Label totalLikesLabel;

    @FXML
    private Label totalMatchesLabel;

    @FXML
    private Label responseRateLabel;

    private final StatsViewModel viewModel;

    public StatsController(StatsViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize from UISession
        viewModel.initialize();

        achievementListView.setItems(viewModel.getAchievements());

        // Styled achievement cells
        achievementListView.setCellFactory(lv -> new ListCell<>() {
            private final HBox container = new HBox(15);
            private final Label iconLabel = new Label();
            private final VBox textBox = new VBox(4);
            private final Label nameLabel = new Label();
            private final Label descLabel = new Label();

            {
                iconLabel.setStyle("-fx-font-size: 24px;");
                nameLabel.setStyle("-fx-font-weight: bold;");
                descLabel.setStyle("-fx-text-fill: -fx-text-secondary; -fx-font-size: 12px;");
                textBox.getChildren().addAll(nameLabel, descLabel);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPadding(new Insets(10));
                HBox.setHgrow(textBox, Priority.ALWAYS);
                container.getChildren().addAll(iconLabel, textBox);
            }

            @Override
            protected void updateItem(Achievement item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    iconLabel.setText(item.getIcon());
                    nameLabel.setText(item.getDisplayName());
                    descLabel.setText(item.getDescription());
                    setGraphic(container);
                }
            }
        });

        // Bind stats labels if they exist in FXML
        if (totalLikesLabel != null) {
            totalLikesLabel
                    .textProperty()
                    .bind(viewModel.totalLikesGivenProperty().asString());
        }
        if (totalMatchesLabel != null) {
            totalMatchesLabel
                    .textProperty()
                    .bind(viewModel.totalMatchesProperty().asString());
        }
        if (responseRateLabel != null) {
            responseRateLabel.textProperty().bind(viewModel.responseRateProperty());
        }
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleBack() {
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.DASHBOARD);
    }
}
