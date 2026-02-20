package datingapp.ui.screen;

import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.SocialViewModel;
import datingapp.ui.viewmodel.SocialViewModel.FriendRequestEntry;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Controller for the Social screen (social.fxml).
 * Shows notifications and pending friend requests in a two-tab layout.
 */
public class SocialController extends BaseController implements Initializable {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM, HH:mm");

    @FXML
    private BorderPane rootPane;

    @SuppressWarnings("unused")
    @FXML
    private TabPane tabPane;

    @FXML
    private ListView<Notification> notificationsListView;

    @FXML
    private ListView<FriendRequestEntry> requestsListView;

    private final SocialViewModel viewModel;

    public SocialController(SocialViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);

        notificationsListView.setItems(viewModel.getNotifications());
        notificationsListView.setCellFactory(lv -> createNotificationCell());

        requestsListView.setItems(viewModel.getPendingRequests());
        requestsListView.setCellFactory(lv -> createRequestCell());

        viewModel.initialize();
        UiAnimations.fadeIn(rootPane, 800);
    }

    private ListCell<Notification> createNotificationCell() {
        return new NotificationListCell(viewModel);
    }

    private ListCell<FriendRequestEntry> createRequestCell() {
        return new FriendRequestListCell(viewModel);
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleRefresh() {
        viewModel.refresh();
    }

    /** Cell for displaying a notification entry. Tapping marks it as read. */
    private static class NotificationListCell extends ListCell<Notification> {

        private final HBox container = new HBox(12);
        private final VBox infoBox = new VBox(4);
        private final Label titleLabel = new Label();
        private final Label bodyLabel = new Label();
        private final Label timeLabel = new Label();
        private final Region unreadDot = new Region();

        public NotificationListCell(SocialViewModel viewModel) {
            unreadDot.setPrefSize(8, 8);
            unreadDot.setMinSize(8, 8);
            unreadDot.setStyle("-fx-background-color: #3b82f6; -fx-background-radius: 4;");

            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            bodyLabel.getStyleClass().add("text-secondary");
            bodyLabel.setStyle("-fx-font-size: 12px;");
            bodyLabel.setWrapText(true);
            timeLabel.getStyleClass().add("text-secondary");
            timeLabel.setStyle("-fx-font-size: 11px;");

            infoBox.getChildren().addAll(titleLabel, bodyLabel, timeLabel);
            HBox.setHgrow(infoBox, Priority.ALWAYS);

            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(12, 16, 12, 16));
            container.getChildren().addAll(unreadDot, infoBox);

            // Tap to mark as read
            setOnMouseClicked(event -> {
                Notification item = getItem();
                if (item != null && !item.isRead()) {
                    viewModel.markNotificationRead(item);
                }
            });
        }

        @Override
        protected void updateItem(Notification notification, boolean empty) {
            super.updateItem(notification, empty);
            if (empty || notification == null) {
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                titleLabel.setText(notification.title());
                bodyLabel.setText(notification.message());

                String time =
                        notification.createdAt().atZone(ZoneId.systemDefault()).format(TIME_FORMAT);
                timeLabel.setText(time);

                boolean unread = !notification.isRead();
                unreadDot.setVisible(unread);
                unreadDot.setManaged(unread);

                setGraphic(container);
                setStyle(
                        unread
                                ? "-fx-background-color: rgba(59, 130, 246, 0.08);"
                                : "-fx-background-color: transparent;");
            }
        }
    }

    /** Cell for displaying a pending friend request with Accept and Decline buttons. */
    private static class FriendRequestListCell extends ListCell<FriendRequestEntry> {

        private final HBox container = new HBox(12);
        private final Label fromLabel = new Label();
        private final Region spacer = new Region();
        private final Button acceptButton = new Button("Accept");
        private final Button declineButton = new Button("Decline");
        private final SocialViewModel viewModel;

        public FriendRequestListCell(SocialViewModel viewModel) {
            this.viewModel = viewModel;

            fromLabel.setStyle("-fx-font-size: 14px;");

            acceptButton.getStyleClass().add("button-primary");
            acceptButton.setStyle("-fx-font-size: 12px; -fx-padding: 6 14;");

            declineButton.getStyleClass().add("button-transparent");
            declineButton.setStyle("-fx-font-size: 12px; -fx-padding: 6 14;");

            HBox.setHgrow(spacer, Priority.ALWAYS);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(12, 16, 12, 16));
            container.getChildren().addAll(fromLabel, spacer, acceptButton, declineButton);
        }

        @Override
        protected void updateItem(FriendRequestEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                fromLabel.setText("Friend request from " + entry.fromUserName());

                acceptButton.setOnAction(event -> {
                    event.consume();
                    viewModel.acceptRequest(entry);
                });

                declineButton.setOnAction(event -> {
                    event.consume();
                    viewModel.declineRequest(entry);
                });

                setGraphic(container);
                setStyle("-fx-background-color: transparent;");
            }
        }
    }
}
