package datingapp.ui.screen;

import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionService.ConversationPreview;
import datingapp.core.model.User;
import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiComponents;
import datingapp.ui.UiConstants;
import datingapp.ui.UiDialogs;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.ChatViewModel;
import datingapp.ui.viewmodel.UiDataAdapters.PresenceStatus;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.UUID;
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
    private Button friendZoneButton;

    @FXML
    private Button gracefulExitButton;

    @FXML
    private Button unmatchButton;

    @FXML
    private Label chatHeaderLabel;

    @FXML
    private Region chatPresenceDot;

    @FXML
    private TextArea profileNoteArea;

    @FXML
    private Label profileNoteStatusLabel;

    @FXML
    private Button saveProfileNoteButton;

    @FXML
    private Button deleteProfileNoteButton;

    @FXML
    private VBox emptyStateContainer;

    @FXML
    private VBox chatContainer;

    @FXML
    private HBox typingIndicatorHost;

    private final ChatViewModel viewModel;
    private final UiComponents.TypingIndicator typingIndicator = new UiComponents.TypingIndicator();

    public ChatController(ChatViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        conversationListView.setItems(viewModel.getConversations());
        messageListView.setItems(viewModel.getActiveMessages());

        conversationListView.setCellFactory(lv -> createConversationCell());
        messageListView.setCellFactory(lv -> createMessageCell());
        if (profileNoteArea != null) {
            profileNoteArea.textProperty().bindBidirectional(viewModel.profileNoteContentProperty());
        }
        if (profileNoteStatusLabel != null) {
            profileNoteStatusLabel.textProperty().bind(viewModel.profileNoteStatusMessageProperty());
        }
        if (saveProfileNoteButton != null) {
            saveProfileNoteButton
                    .disableProperty()
                    .bind(viewModel.selectedConversationProperty().isNull().or(viewModel.profileNoteBusyProperty()));
        }
        if (deleteProfileNoteButton != null) {
            deleteProfileNoteButton
                    .disableProperty()
                    .bind(viewModel.selectedConversationProperty().isNull().or(viewModel.profileNoteBusyProperty()));
        }
        if (friendZoneButton != null) {
            friendZoneButton
                    .disableProperty()
                    .bind(viewModel.selectedConversationProperty().isNull());
        }
        if (gracefulExitButton != null) {
            gracefulExitButton
                    .disableProperty()
                    .bind(viewModel.selectedConversationProperty().isNull());
        }
        if (unmatchButton != null) {
            unmatchButton
                    .disableProperty()
                    .bind(viewModel.selectedConversationProperty().isNull());
        }
        if (typingIndicatorHost != null) {
            typingIndicatorHost.getChildren().setAll(typingIndicator);
            typingIndicatorHost.setVisible(false);
            typingIndicatorHost.setManaged(false);
        }

        // Bind selection using Subscription API
        addSubscription(conversationListView
                .getSelectionModel()
                .selectedItemProperty()
                .subscribe(this::handleConversationSelection));
        addSubscription(viewModel.presenceStatusProperty().subscribe(this::updatePresenceIndicator));
        addSubscription(viewModel.remoteTypingProperty().subscribe(this::updateTypingIndicator));

        // Initial state
        chatContainer.setVisible(false);
        emptyStateContainer.setVisible(true);
        updatePresenceIndicator(viewModel.presenceStatusProperty().get());
        updateTypingIndicator(viewModel.remoteTypingProperty().get());

        // Initialize ViewModel with current user from UISession
        viewModel.initialize();

        NavigationService.getInstance()
                .consumeNavigationContext(NavigationService.ViewType.CHAT, UUID.class)
                .ifPresent(userId -> viewModel.openConversationWithUser(userId, preview -> {
                    if (preview != null) {
                        conversationListView.getSelectionModel().select(preview);
                        conversationListView.scrollTo(preview);
                    }
                }));

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
        updateTypingIndicator(viewModel.remoteTypingProperty().get());
    }

    private void updatePresenceIndicator(PresenceStatus status) {
        if (chatPresenceDot == null) {
            return;
        }
        chatPresenceDot.getStyleClass().removeAll("status-online", "status-away", "status-offline");
        PresenceStatus resolvedStatus = status != null ? status : PresenceStatus.UNKNOWN;
        boolean visible = resolvedStatus != PresenceStatus.UNKNOWN;
        if (visible) {
            chatPresenceDot
                    .getStyleClass()
                    .add(
                            switch (resolvedStatus) {
                                case ONLINE -> "status-online";
                                case AWAY -> "status-away";
                                case OFFLINE -> "status-offline";
                                case UNKNOWN -> "status-offline";
                            });
        }
        chatPresenceDot.setVisible(visible);
        chatPresenceDot.setManaged(visible);
    }

    private void updateTypingIndicator(Boolean typing) {
        if (typingIndicatorHost == null) {
            return;
        }
        boolean visible = Boolean.TRUE.equals(typing)
                && viewModel.selectedConversationProperty().get() != null;
        typingIndicatorHost.setVisible(visible);
        typingIndicatorHost.setManaged(visible);
        if (visible) {
            typingIndicator.show();
        } else {
            typingIndicator.hide();
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
            statusDot.getStyleClass().add("status-dot");
            statusDot.setVisible(false);
            statusDot.setManaged(false);
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
            container.setPadding(new Insets(
                    UiConstants.SPACING_STANDARD,
                    UiConstants.CHAT_LIST_HORIZONTAL_PADDING,
                    UiConstants.SPACING_STANDARD,
                    UiConstants.CHAT_LIST_HORIZONTAL_PADDING));
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
                        .map(s -> s.length() > UiConstants.CONVERSATION_PREVIEW_CHARS
                                ? s.substring(0, UiConstants.CONVERSATION_PREVIEW_CHARS) + "..."
                                : s)
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
        private final Region readReceiptIcon = new Region();
        private final HBox metaBox = new HBox(4);
        private final ChatViewModel viewModel;

        public MessageListCell(ChatViewModel viewModel) {
            this.viewModel = viewModel;
            bubble.setMaxWidth(300);
            bubble.setPadding(new Insets(
                    UiConstants.SPACING_STANDARD,
                    UiConstants.CHAT_BUBBLE_HORIZONTAL_PADDING,
                    UiConstants.SPACING_STANDARD,
                    UiConstants.CHAT_BUBBLE_HORIZONTAL_PADDING));
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(280);
            timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(255,255,255,0.6);");

            readReceiptIcon.getStyleClass().addAll("read-receipt-icon", "read-receipt-seen");
            readReceiptIcon.setPrefSize(12, 12);
            readReceiptIcon.setMinSize(12, 12);
            readReceiptIcon.setMaxSize(12, 12);
            readReceiptIcon.setVisible(false);
            readReceiptIcon.setManaged(false);

            metaBox.setAlignment(Pos.CENTER_RIGHT);
            metaBox.getChildren().addAll(timeLabel, readReceiptIcon);

            bubble.getChildren().addAll(contentLabel, metaBox);
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

                    ConversationPreview currentConv =
                            viewModel.selectedConversationProperty().get();
                    if (currentConv != null) {
                        boolean isRead = !msg.createdAt()
                                .isAfter(currentConv
                                        .conversation()
                                        .getLastReadAt(currentConv.otherUser().getId()));
                        readReceiptIcon.setVisible(isRead);
                        readReceiptIcon.setManaged(isRead);
                    } else {
                        readReceiptIcon.setVisible(false);
                        readReceiptIcon.setManaged(false);
                    }
                } else {
                    bubble.getStyleClass().clear();
                    bubble.setStyle("-fx-background-color: -fx-surface-dark; -fx-background-radius: 18 18 18 4;");
                    container.setAlignment(Pos.CENTER_LEFT);
                    container.getChildren().add(bubble);
                    readReceiptIcon.setVisible(false);
                    readReceiptIcon.setManaged(false);
                }

                container.setPadding(new Insets(
                        UiConstants.SPACING_XSMALL,
                        UiConstants.SPACING_STANDARD,
                        UiConstants.SPACING_XSMALL,
                        UiConstants.SPACING_STANDARD));
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
            boolean sent = viewModel.sendMessage(text);
            if (sent) {
                messageArea.clear();

                // Scroll to bottom
                if (!viewModel.getActiveMessages().isEmpty()) {
                    messageListView.scrollTo(viewModel.getActiveMessages().size() - 1);
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleBrowseMatches() {
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.MATCHING);
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleBlock() {
        ConversationPreview selected = viewModel.selectedConversationProperty().get();
        if (selected == null) {
            return;
        }
        User otherUser = selected.otherUser();
        UiDialogs.confirmAndExecute(
                "Block User",
                "Block " + otherUser.getName() + "?",
                "Your conversation will end and they cannot contact you.",
                () -> viewModel.blockUser(otherUser.getId()),
                otherUser.getName() + " has been blocked.");
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleReport() {
        ConversationPreview selected = viewModel.selectedConversationProperty().get();
        if (selected == null) {
            return;
        }
        User otherUser = selected.otherUser();
        UiDialogs.showReportDialog(otherUser.getName(), (reason, desc) -> {
            viewModel.reportUser(otherUser.getId(), reason, desc, true);
            UiFeedbackService.showSuccess(otherUser.getName() + " has been reported.");
        });
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleSaveProfileNote() {
        viewModel.saveSelectedProfileNote();
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleDeleteProfileNote() {
        viewModel.deleteSelectedProfileNote();
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleRequestFriendZone() {
        ConversationPreview selected = viewModel.selectedConversationProperty().get();
        if (selected == null) {
            return;
        }
        User otherUser = selected.otherUser();
        UiDialogs.confirmAndExecute(
                "Request Friend Zone",
                "Ask " + otherUser.getName() + " to continue as friends?",
                "They will receive a friend-zone request.",
                viewModel::requestFriendZoneForSelectedConversation,
                "Friend-zone request sent.");
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleGracefulExit() {
        ConversationPreview selected = viewModel.selectedConversationProperty().get();
        if (selected == null) {
            return;
        }
        User otherUser = selected.otherUser();
        UiDialogs.confirmAndExecute(
                "Graceful Exit",
                "End things kindly with " + otherUser.getName() + "?",
                "Your conversation will be archived for both of you.",
                viewModel::gracefulExitSelectedConversation,
                "Graceful exit completed.");
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleUnmatch() {
        ConversationPreview selected = viewModel.selectedConversationProperty().get();
        if (selected == null) {
            return;
        }
        User otherUser = selected.otherUser();
        UiDialogs.confirmAndExecute(
                "Unmatch",
                "Unmatch with " + otherUser.getName() + "?",
                "This removes the relationship and archives the conversation.",
                viewModel::unmatchSelectedConversation,
                otherUser.getName() + " has been unmatched.");
    }
}
