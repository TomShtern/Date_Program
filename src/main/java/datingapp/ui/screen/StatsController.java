package datingapp.ui.screen;

import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiConstants;
import datingapp.ui.viewmodel.StatsViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Controller for the Stats screen (stats.fxml).
 * Extends BaseController for automatic subscription cleanup.
 */
public class StatsController extends BaseController implements Initializable {

    @FXML
    private javafx.scene.layout.BorderPane rootPane;

    @FXML
    private ListView<Achievement> achievementListView;

    @FXML
    private Label totalLikesLabel;

    @FXML
    private Label totalMatchesLabel;

    @FXML
    private Label responseRateLabel;

    @FXML
    private Label totalLikesReceivedLabel;

    @FXML
    private Label messagesExchangedLabel;

    @FXML
    private Label loginStreakLabel;

    @FXML
    private Label achievementCountLabel;

    @FXML
    private GridPane statsGrid;

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
        achievementListView.setCellFactory(lv -> new AchievementListCell());

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
        if (totalLikesReceivedLabel != null) {
            totalLikesReceivedLabel
                    .textProperty()
                    .bind(viewModel.totalLikesReceivedProperty().asString());
        }
        if (messagesExchangedLabel != null) {
            messagesExchangedLabel
                    .textProperty()
                    .bind(viewModel.messagesExchangedProperty().asString());
        }
        if (loginStreakLabel != null) {
            loginStreakLabel.textProperty().bind(viewModel.loginStreakProperty().asString());
        }
        if (achievementCountLabel != null) {
            achievementCountLabel
                    .textProperty()
                    .bind(Bindings.createStringBinding(
                            () -> viewModel.getAchievements().size() + " / " + viewModel.getTotalAchievementCount(),
                            viewModel.getAchievements()));
        }

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);
        animateStatCards();
    }

    private void animateStatCards() {
        if (statsGrid == null) {
            return;
        }
        for (int index = 0; index < statsGrid.getChildren().size(); index++) {
            javafx.scene.Node card = statsGrid.getChildren().get(index);
            card.setOpacity(0);
            card.setTranslateY(20);
            javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(Duration.millis(index * 80L));
            delay.setOnFinished(event -> UiAnimations.slideUp(card, 320, 20));
            delay.play();
        }
    }

    private static class AchievementListCell extends ListCell<Achievement> {
        private final HBox container = new HBox(15);
        private final StackPane iconStack = new StackPane();
        private final org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon();
        private final VBox textBox = new VBox(4);
        private final Label nameLabel = new Label();
        private final Label descLabel = new Label();

        public AchievementListCell() {
            iconStack.setPrefSize(48, 48);
            iconStack.setStyle("-fx-background-color: -fx-surface-dark; "
                    + "-fx-background-radius: 24; -fx-border-color: -fx-accent-super; "
                    + "-fx-border-radius: 24; -fx-border-width: 1;");
            icon.setIconSize(24);
            icon.setIconColor(javafx.scene.paint.Color.web("#f59e0b")); // mdi2-star color
            iconStack.getChildren().add(icon);

            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            descLabel.getStyleClass().add("text-secondary");
            descLabel.setStyle("-fx-font-size: 12px;");
            textBox.getChildren().addAll(nameLabel, descLabel);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(UiConstants.PADDING_MEDIUM));
            HBox.setHgrow(textBox, Priority.ALWAYS);
            container.getChildren().addAll(iconStack, textBox);

            // Entrance animation
            UiAnimations.addPulseOnHover(container);
        }

        @Override
        protected void updateItem(Achievement item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                icon.setIconLiteral(getIconCode(item.getIcon()));
                nameLabel.setText(item.getDisplayName());
                descLabel.setText(item.getDescription());
                setGraphic(container);
            }
        }

        private String getIconCode(String emoji) {
            if (emoji == null) {
                return "mdi2t-trophy-variant";
            }
            if (emoji.contains("🔥")) {
                return "mdi2f-fire";
            }
            if (emoji.contains("❤️")) {
                return "mdi2h-heart";
            }
            if (emoji.contains("⭐")) {
                return "mdi2s-star";
            }
            if (emoji.contains("💬")) {
                return "mdi2c-chat";
            }
            return "mdi2t-trophy-variant";
        }
    }
}
