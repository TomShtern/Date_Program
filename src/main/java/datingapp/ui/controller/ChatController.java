package datingapp.ui.controller;

import datingapp.core.Messaging.Message;
import datingapp.core.MessagingService.ConversationPreview;
import datingapp.ui.NavigationService;
import datingapp.ui.util.UiAnimations;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controller for the Chat screen (chat.fxml).
 * Handles conversation display, message styling, and sending messages.
 * Extends BaseController for automatic subscription cleanup.
 */
public class ChatController extends BaseController implements Initializable {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private javafx.scene.layout.BorderPane rootPane;

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

        // Bind selection using Subscription API
        addSubscription(conversationListView
                .getSelectionModel()
                .selectedItemProperty()
                .subscribe(this::handleConversationSelection));

        // Initial state
        chatContainer.setVisible(false);
        emptyStateContainer.setVisible(true);

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);
    }

    /** Handle conversation selection change. */
    private void handleConversationSelection(ConversationPreview selected) {
        viewModel.selectedConversationProperty().set(selected);
        if (selected != null) {
            chatHeaderLabel.setText(selected.otherUser().getName());
            chatContainer.setVisible(true);
            emptyStateContainer.setVisible(false);
        } else {
            chatContainer.setVisible(false);
            emptyStateContainer.setVisible(true);
        }
    }

    private ListCell<ConversationPreview> createConversationCell() {
        return new ConversationListCell();
    }

    private static class ConversationListCell extends ListCell<ConversationPreview> {
        private final HBox container = new HBox(15);
        private final StackPane avatarStack = new StackPane();
        private final org.kordamp.ikonli.javafx.FontIcon avatarIcon =
                new org.kordamp.ikonli.javafx.FontIcon("mdi2a-account");
        private final Region statusDot = new Region();
        private final VBox textBox = new VBox(4);
        private final HBox topRow = new HBox(10);
        private final Label nameLabel = new Label();
        private final Label unreadBadge = new Label();
        private final Label snippetLabel = new Label();

        public ConversationListCell() {
            avatarStack.setPrefSize(40, 40);
            avatarStack.getStyleClass().add("icon-primary");
            avatarStack.setStyle("-fx-background-color: -fx-surface-dark; -fx-background-radius: 20;");
            avatarIcon.setIconSize(24);

            // Configure status dot
            statusDot.getStyleClass().addAll("status-dot", "status-online");
            StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
            statusDot.setTranslateX(2);
            statusDot.setTranslateY(2);

            avatarStack.getChildren().addAll(avatarIcon, statusDot);

            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            snippetLabel.getStyleClass().add("text-secondary");
            snippetLabel.setStyle("-fx-font-size: 12px;");

            unreadBadge.getStyleClass().add("notification-badge");
            unreadBadge.setStyle("-fx-min-width: 18; -fx-min-height: 18; -fx-background-radius: 9;");

            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            topRow.getChildren().addAll(nameLabel, unreadBadge);
            textBox.getChildren().addAll(topRow, snippetLabel);

            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(10, 15, 10, 15));
            HBox.setHgrow(textBox, Priority.ALWAYS);
            container.getChildren().addAll(avatarStack, textBox);
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
                    unreadBadge.setManaged(true);
                } else {
                    unreadBadge.setVisible(false);
                    unreadBadge.setManaged(false);
                }

                String snippet = item.lastMessage()
                        .map(Message::content)
                        .map(s -> s.length() > 35 ? s.substring(0, 35) + "..." : s)
                        .orElse("No messages yet");
                snippetLabel.setText(snippet);

                setGraphic(container);
            }
        }
    }

    private ListCell<Message> createMessageCell() {
        return new MessageListCell(viewModel);
    }

    private static class MessageListCell extends ListCell<Message> {
        private final HBox container = new HBox();
        private final VBox bubble = new VBox(4);
        private final Label contentLabel = new Label();
        private final Label timeLabel = new Label();
        private final ChatViewModel viewModel;

        public MessageListCell(ChatViewModel viewModel) {
            this.viewModel = viewModel;
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
                    bubble.getStyleClass().setAll("message-bubble-gradient");
                    bubble.setStyle(""); // Clear any explicit style
                    container.setAlignment(Pos.CENTER_RIGHT);
                    container.getChildren().add(bubble);
                } else {
                    bubble.getStyleClass().clear();
                    bubble.setStyle("-fx-background-color: -fx-surface-dark; -fx-background-radius: 18 18 18 4;");
                    container.setAlignment(Pos.CENTER_LEFT);
                    container.getChildren().add(bubble);
                }

                container.setPadding(new Insets(4, 10, 4, 10));
                setGraphic(container);
                setStyle("-fx-background-color: transparent;");
            }
        }
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
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleBrowseMatches() {
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHING);
    }
}
