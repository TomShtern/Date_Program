package datingapp.ui.controller;

import datingapp.core.Message;
import datingapp.core.MessagingService.ConversationPreview;
import datingapp.ui.NavigationService;
import datingapp.ui.ViewFactory;
import datingapp.ui.viewmodel.ChatViewModel;
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
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Controller for the Chat screen (chat.fxml).
 * Handles conversation display, message styling, and sending messages.
 */
public class ChatController implements Initializable {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private ListView<ConversationPreview> conversationListView;

    @FXML
    private ListView<Message> messageListView;

    @FXML
    private TextArea messageArea;

    @SuppressWarnings("unused")
    @FXML
    private Button sendButton;

    @FXML
    private Label chatHeaderLabel;

    @FXML
    private VBox emptyStateContainer;

    @FXML
    private VBox chatContainer;

    private final ChatViewModel viewModel;

    public ChatController(ChatViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize ViewModel with current user from UISession
        viewModel.initialize();

        conversationListView.setItems(viewModel.getConversations());
        messageListView.setItems(viewModel.getActiveMessages());

        conversationListView.setCellFactory(lv -> createConversationCell());
        messageListView.setCellFactory(lv -> createMessageCell());

        // Bind selection
        conversationListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.selectedConversationProperty().set(newVal);
            if (newVal != null) {
                chatHeaderLabel.setText(newVal.otherUser().getName());
                chatContainer.setVisible(true);
                emptyStateContainer.setVisible(false);
            } else {
                chatContainer.setVisible(false);
                emptyStateContainer.setVisible(true);
            }
        });

        // Initial state
        chatContainer.setVisible(false);
        emptyStateContainer.setVisible(true);
    }

    private ListCell<ConversationPreview> createConversationCell() {
        return new ListCell<>() {
            private final VBox container = new VBox(4);
            private final HBox topRow = new HBox(8);
            private final Label nameLabel = new Label();
            private final Label unreadBadge = new Label();
            private final Label snippetLabel = new Label();

            {
                // Setup styling
                container.setStyle("-fx-padding: 10;");
                nameLabel.setStyle("-fx-font-weight: bold;");
                snippetLabel.setStyle("-fx-text-fill: -fx-text-secondary; -fx-font-size: 12px;");
                unreadBadge.setStyle("-fx-background-color: -fx-accent-super; -fx-background-radius: 10; "
                        + "-fx-padding: 2 6; -fx-font-size: 11px; -fx-font-weight: bold;");

                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                topRow.getChildren().addAll(nameLabel, unreadBadge);
                container.getChildren().addAll(topRow, snippetLabel);
            }

            @Override
            protected void updateItem(ConversationPreview item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.otherUser().getName());

                    if (item.unreadCount() > 0) {
                        unreadBadge.setText(String.valueOf(item.unreadCount()));
                        unreadBadge.setVisible(true);
                    } else {
                        unreadBadge.setVisible(false);
                    }

                    String snippet = item.lastMessage()
                            .map(Message::content)
                            .map(s -> s.length() > 40 ? s.substring(0, 40) + "..." : s)
                            .orElse("No messages yet");
                    snippetLabel.setText(snippet);

                    setGraphic(container);
                }
            }
        };
    }

    private ListCell<Message> createMessageCell() {
        return new ListCell<>() {
            private final HBox container = new HBox();
            private final VBox bubble = new VBox(4);
            private final Label contentLabel = new Label();
            private final Label timeLabel = new Label();

            {
                bubble.setMaxWidth(300);
                bubble.setPadding(new Insets(10, 14, 10, 14));
                contentLabel.setWrapText(true);
                contentLabel.setMaxWidth(280);
                timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(255,255,255,0.6);");
                bubble.getChildren().addAll(contentLabel, timeLabel);
            }

            @Override
            protected void updateItem(Message msg, boolean empty) {
                super.updateItem(msg, empty);
                if (empty || msg == null) {
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    contentLabel.setText(msg.content());

                    String time = msg.createdAt().atZone(ZoneId.systemDefault()).format(TIME_FORMAT);
                    timeLabel.setText(time);

                    boolean isMine = viewModel.isMessageFromCurrentUser(msg);

                    container.getChildren().clear();

                    if (isMine) {
                        // Sent message - right aligned, colored bubble
                        bubble.setStyle(
                                "-fx-background-color: -fx-accent-super; " + "-fx-background-radius: 18 18 4 18;");
                        container.setAlignment(Pos.CENTER_RIGHT);
                        container.getChildren().add(bubble);
                    } else {
                        // Received message - left aligned, dark bubble
                        bubble.setStyle("-fx-background-color: #3a3a50; " + "-fx-background-radius: 18 18 18 4;");
                        container.setAlignment(Pos.CENTER_LEFT);
                        container.getChildren().add(bubble);
                    }

                    container.setPadding(new Insets(4, 10, 4, 10));
                    setGraphic(container);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        };
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleSendMessage() {
        String text = messageArea.getText();
        if (text != null && !text.isBlank()) {
            viewModel.sendMessage(text);
            messageArea.clear();

            // Scroll to bottom
            if (!viewModel.getActiveMessages().isEmpty()) {
                messageListView.scrollTo(viewModel.getActiveMessages().size() - 1);
            }
        }
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleBack() {
        NavigationService.getInstance().navigateTo(ViewFactory.ViewType.DASHBOARD);
    }
}
