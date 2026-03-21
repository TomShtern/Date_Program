package datingapp.ui.screen;

import datingapp.core.AppClock;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * Controller for the Chat screen (chat.fxml).
 * Handles conversation display, message styling, and sending messages.
 * Extends BaseController for automatic subscription cleanup.
 */
public class ChatController extends BaseController implements Initializable {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CONVERSATION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter THREAD_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final int MESSAGE_WARNING_THRESHOLD = Message.MAX_LENGTH - 100;
    private static final String MESSAGE_LENGTH_STYLE_NORMAL = "-fx-font-size: 11px; -fx-text-fill: -fx-text-secondary;";
    private static final String MESSAGE_LENGTH_STYLE_WARNING = "-fx-font-size: 11px; -fx-text-fill: #f59e0b;";
    private static final String MESSAGE_LENGTH_STYLE_LIMIT = "-fx-font-size: 11px; -fx-text-fill: #ef4444;";
    private static final String SNIPPET_STYLE_NORMAL = "-fx-font-size: 12px;";
    private static final String SNIPPET_STYLE_EMPTY = "-fx-font-size: 12px; -fx-font-style: italic;";

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
    private Button refreshButton;

    @FXML
    private Button unmatchButton;

    @FXML
    private Label chatHeaderLabel;

    @FXML
    private Region chatPresenceDot;

    @FXML
    private VBox notePanelContainer;

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

    @FXML
    private Label messageLengthLabel;

    @FXML
    private ProgressIndicator chatLoadingIndicator;

    private final ChatViewModel viewModel;
    private final UiComponents.TypingIndicator typingIndicator = new UiComponents.TypingIndicator();
    private final Tooltip presenceTooltip = new Tooltip();
    private final Tooltip sendButtonTooltip = new Tooltip();
    private ListChangeListener<Message> activeMessagesListener;
    private boolean shouldAutoScrollToBottom = true;

    public ChatController(ChatViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);
        conversationListView.setItems(viewModel.getConversations());
        messageListView.setItems(viewModel.getActiveMessages());
        Label noConversationsLabel = new Label("No conversations yet.\nMatch with someone to start.");
        noConversationsLabel.getStyleClass().add("text-secondary");
        noConversationsLabel.setWrapText(true);
        noConversationsLabel.setTextAlignment(TextAlignment.CENTER);
        noConversationsLabel.setMaxWidth(220);
        conversationListView.setPlaceholder(noConversationsLabel);

        conversationListView.setCellFactory(lv -> createConversationCell());
        messageListView.setCellFactory(lv -> createMessageCell());
        messageListView.addEventFilter(ScrollEvent.SCROLL, _ -> updateAutoScrollState());
        activeMessagesListener = change -> {
            if (viewModel.selectedConversationProperty().get() == null) {
                return;
            }
            boolean hasAddedMessages = false;
            while (change.next()) {
                if (change.wasAdded() && !change.getAddedSubList().isEmpty()) {
                    hasAddedMessages = true;
                }
            }
            if (!hasAddedMessages) {
                return;
            }
            updateAutoScrollState();
            if (!shouldAutoScrollToBottom) {
                return;
            }
            scrollToLatestMessage();
        };
        viewModel.getActiveMessages().addListener(activeMessagesListener);
        if (profileNoteArea != null) {
            profileNoteArea.textProperty().bindBidirectional(viewModel.profileNoteContentProperty());
        }
        if (notePanelContainer != null) {
            notePanelContainer
                    .visibleProperty()
                    .bind(viewModel.selectedConversationProperty().isNotNull());
            notePanelContainer
                    .managedProperty()
                    .bind(viewModel.selectedConversationProperty().isNotNull());
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
        if (messageArea != null) {
            messageArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleMessageComposerKeyPressed);
            updateMessageLengthIndicator(messageArea.getText());
            addSubscription(messageArea.textProperty().subscribe(this::updateMessageLengthIndicator));
        }
        configureSendButtonState();
        if (messageLengthLabel != null) {
            messageLengthLabel.setText("0/" + Message.MAX_LENGTH);
            messageLengthLabel.setStyle(MESSAGE_LENGTH_STYLE_NORMAL);
        }
        if (chatLoadingIndicator != null) {
            chatLoadingIndicator.visibleProperty().bind(viewModel.loadingProperty());
        }
        if (refreshButton != null) {
            refreshButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }
        setupAccessibilityMetadata();

        // Bind selection using Subscription API
        addSubscription(conversationListView
                .getSelectionModel()
                .selectedItemProperty()
                .subscribe(this::handleConversationSelection));
        addSubscription(viewModel.presenceStatusProperty().subscribe(this::updatePresenceIndicator));
        addSubscription(viewModel.remoteTypingProperty().subscribe(this::updateTypingIndicator));
        addSubscription(viewModel.presenceSupportedProperty().subscribe(_ -> refreshPresenceTooltip()));
        addSubscription(viewModel.presenceUnavailableMessageProperty().subscribe(_ -> refreshPresenceTooltip()));

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
            shouldAutoScrollToBottom = true;
            scrollToLatestMessage();
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
        refreshPresenceTooltip();
    }

    private void configureSendButtonState() {
        if (sendButton == null || messageArea == null) {
            return;
        }
        sendButton
                .disableProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> viewModel.selectedConversationProperty().get() == null
                                || viewModel.sendingProperty().get()
                                || messageArea.getText() == null
                                || messageArea.getText().isBlank(),
                        viewModel.selectedConversationProperty(),
                        viewModel.sendingProperty(),
                        messageArea.textProperty()));
        addSubscription(sendButton.disableProperty().subscribe(_ -> refreshSendButtonTooltip()));
        addSubscription(viewModel.selectedConversationProperty().subscribe(_ -> refreshSendButtonTooltip()));
        addSubscription(viewModel.sendingProperty().subscribe(_ -> refreshSendButtonTooltip()));
        addSubscription(messageArea.textProperty().subscribe(_ -> refreshSendButtonTooltip()));
        refreshSendButtonTooltip();
    }

    private void refreshSendButtonTooltip() {
        if (sendButton == null || messageArea == null) {
            return;
        }
        if (!sendButton.isDisabled()) {
            Tooltip.uninstall(sendButton, sendButtonTooltip);
            return;
        }
        String tooltipText;
        if (viewModel.selectedConversationProperty().get() == null) {
            tooltipText = "Select a conversation first";
        } else if (viewModel.sendingProperty().get()) {
            tooltipText = "Sending...";
        } else {
            tooltipText = "Type a message first";
        }
        sendButtonTooltip.setText(tooltipText);
        Tooltip.install(sendButton, sendButtonTooltip);
    }

    private void refreshPresenceTooltip() {
        if (chatPresenceDot == null) {
            return;
        }
        String text = null;
        if (!viewModel.presenceSupportedProperty().get()) {
            text = viewModel.presenceUnavailableMessageProperty().get();
        } else {
            text = switch (viewModel.presenceStatusProperty().get()) {
                case ONLINE -> "Online";
                case AWAY -> "Away";
                case OFFLINE -> "Offline";
                case UNKNOWN -> null;
            };
        }
        if (text == null || text.isBlank()) {
            Tooltip.uninstall(chatPresenceDot, presenceTooltip);
            return;
        }
        presenceTooltip.setText(text);
        Tooltip.install(chatPresenceDot, presenceTooltip);
    }

    private void setupAccessibilityMetadata() {
        if (conversationListView != null) {
            conversationListView.setAccessibleText("Conversation list");
        }
        if (messageListView != null) {
            messageListView.setAccessibleText("Message thread");
        }
        if (messageArea != null) {
            messageArea.setAccessibleText("Message composer");
        }
        if (sendButton != null) {
            sendButton.setAccessibleText("Send message");
        }
        if (refreshButton != null) {
            refreshButton.setAccessibleText("Refresh conversations");
        }
        if (profileNoteArea != null) {
            profileNoteArea.setAccessibleText("Private profile note");
        }
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
        return new ConversationListCell(viewModel::getCurrentUserId);
    }

    private void updateAutoScrollState() {
        ScrollBar verticalScrollBar = findVerticalScrollBar(messageListView);
        if (verticalScrollBar == null) {
            shouldAutoScrollToBottom = true;
            return;
        }
        shouldAutoScrollToBottom =
                verticalScrollBar.getMax() <= 0 || verticalScrollBar.getValue() >= verticalScrollBar.getMax() - 0.05;
    }

    private static ScrollBar findVerticalScrollBar(ListView<?> listView) {
        if (listView == null) {
            return null;
        }
        for (Node node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar scrollBar && scrollBar.getOrientation() == Orientation.VERTICAL) {
                return scrollBar;
            }
        }
        return null;
    }

    private static class ConversationListCell extends ListCell<ConversationPreview> {
        private static final String[] AVATAR_COLORS =
                new String[] {"#6366f1", "#ec4899", "#f59e0b", "#10b981", "#3b82f6", "#ef4444"};
        private final HBox container = new HBox(15);
        private final StackPane avatarStack = new StackPane();
        private final Label avatarInitials = new Label();
        private final Region statusDot = new Region();
        private final VBox textBox = new VBox(4);
        private final HBox topRow = new HBox(10);
        private final Label nameLabel = new Label();
        private final Label timeLabel = new Label();
        private final Label unreadBadge = new Label();
        private final Label snippetLabel = new Label();
        private final Supplier<UUID> currentUserIdSupplier;

        public ConversationListCell(Supplier<UUID> currentUserIdSupplier) {
            this.currentUserIdSupplier = currentUserIdSupplier;
            avatarStack.setPrefSize(40, 40);
            avatarStack.getStyleClass().add("icon-primary");
            avatarStack.setStyle("-fx-background-color: -fx-surface-dark; -fx-background-radius: 20;");
            avatarInitials.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");

            // Configure status dot
            statusDot.getStyleClass().add("status-dot");
            statusDot.setVisible(false);
            statusDot.setManaged(false);
            StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
            statusDot.setTranslateX(2);
            statusDot.setTranslateY(2);

            avatarStack.getChildren().addAll(avatarInitials, statusDot);

            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            snippetLabel.getStyleClass().add("text-secondary");
            snippetLabel.setStyle(SNIPPET_STYLE_NORMAL);

            timeLabel.getStyleClass().add("text-secondary");
            timeLabel.setStyle("-fx-font-size: 11px;");

            unreadBadge.getStyleClass().add("notification-badge");
            unreadBadge.setStyle("-fx-min-width: 18; -fx-min-height: 18; -fx-background-radius: 9;");

            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            topRow.getChildren().addAll(nameLabel, timeLabel, unreadBadge);
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
                String userName = item.otherUser().getName();
                nameLabel.setText(userName);
                avatarInitials.setText(computeInitials(userName));
                avatarStack.setStyle(
                        "-fx-background-color: " + colorForName(userName) + "; -fx-background-radius: 20;");
                timeLabel.setText(item.lastMessage()
                        .map(Message::createdAt)
                        .map(createdAt -> {
                            var zonedDateTime = createdAt.atZone(ZoneId.systemDefault());
                            LocalDate messageDate = zonedDateTime.toLocalDate();
                            return messageDate.equals(AppClock.today())
                                    ? zonedDateTime.format(TIME_FORMAT)
                                    : zonedDateTime.format(CONVERSATION_DATE_FORMAT);
                        })
                        .orElse(""));

                if (item.unreadCount() > 0) {
                    unreadBadge.setText(String.valueOf(item.unreadCount()));
                    unreadBadge.setVisible(true);
                    unreadBadge.setManaged(true);
                } else {
                    unreadBadge.setVisible(false);
                    unreadBadge.setManaged(false);
                }

                Message lastMessage = item.lastMessage().orElse(null);
                if (lastMessage == null) {
                    snippetLabel.setText("No messages yet");
                    snippetLabel.setStyle(SNIPPET_STYLE_EMPTY);
                } else {
                    UUID currentUserId = currentUserIdSupplier != null ? currentUserIdSupplier.get() : null;
                    String senderPrefix =
                            currentUserId != null && currentUserId.equals(lastMessage.senderId()) ? "You: " : "";
                    String preview = senderPrefix + lastMessage.content();
                    if (preview.length() > UiConstants.CONVERSATION_PREVIEW_CHARS) {
                        preview = preview.substring(0, UiConstants.CONVERSATION_PREVIEW_CHARS) + "...";
                    }
                    snippetLabel.setText(preview);
                    snippetLabel.setStyle(SNIPPET_STYLE_NORMAL);
                }

                setGraphic(container);
            }
        }

        private static String computeInitials(String name) {
            if (name == null || name.isBlank()) {
                return "?";
            }
            String trimmed = name.trim();
            int separator = trimmed.indexOf(' ');
            if (separator > 0 && separator + 1 < trimmed.length()) {
                char first = Character.toUpperCase(trimmed.charAt(0));
                char second = Character.toUpperCase(trimmed.charAt(separator + 1));
                return "" + first + second;
            }
            return String.valueOf(Character.toUpperCase(trimmed.charAt(0)));
        }

        private static String colorForName(String name) {
            int safeHash = name == null ? 0 : name.hashCode();
            int index = Math.floorMod(safeHash, AVATAR_COLORS.length);
            return AVATAR_COLORS[index];
        }
    }

    private ListCell<Message> createMessageCell() {
        return new MessageListCell(viewModel, messageListView);
    }

    private static class MessageListCell extends ListCell<Message> {
        private final VBox cellRoot = new VBox(6);
        private final Label dateSeparatorLabel = new Label();
        private final HBox container = new HBox();
        private final VBox bubble = new VBox(4);
        private final Label contentLabel = new Label();
        private final Label timeLabel = new Label();
        private final Region readReceiptIcon = new Region();
        private final HBox metaBox = new HBox(4);
        private final ChatViewModel viewModel;

        public MessageListCell(ChatViewModel viewModel, ListView<Message> messageListView) {
            this.viewModel = viewModel;
            bubble.maxWidthProperty().bind(messageListView.widthProperty().multiply(0.65));
            bubble.setPadding(new Insets(
                    UiConstants.SPACING_STANDARD,
                    UiConstants.CHAT_BUBBLE_HORIZONTAL_PADDING,
                    UiConstants.SPACING_STANDARD,
                    UiConstants.CHAT_BUBBLE_HORIZONTAL_PADDING));
            contentLabel.setWrapText(true);
            contentLabel.maxWidthProperty().bind(bubble.maxWidthProperty().subtract(24));
            timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(255,255,255,0.6);");

            dateSeparatorLabel.getStyleClass().add("text-secondary");
            dateSeparatorLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            dateSeparatorLabel.setAlignment(Pos.CENTER);
            dateSeparatorLabel.setMaxWidth(Double.MAX_VALUE);

            readReceiptIcon.getStyleClass().addAll("read-receipt-icon", "read-receipt-seen");
            readReceiptIcon.setPrefSize(12, 12);
            readReceiptIcon.setMinSize(12, 12);
            readReceiptIcon.setMaxSize(12, 12);
            readReceiptIcon.setVisible(false);
            readReceiptIcon.setManaged(false);

            metaBox.setAlignment(Pos.CENTER_RIGHT);
            metaBox.getChildren().addAll(timeLabel, readReceiptIcon);

            bubble.getChildren().addAll(contentLabel, metaBox);
            cellRoot.getChildren().addAll(dateSeparatorLabel, container);
        }

        @Override
        protected void updateItem(Message msg, boolean empty) {
            super.updateItem(msg, empty);
            if (empty || msg == null) {
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                updateDateSeparator(msg);
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
                        var lastReadAt = currentConv
                                .conversation()
                                .getLastReadAt(currentConv.otherUser().getId());
                        boolean isRead = lastReadAt != null && !msg.createdAt().isAfter(lastReadAt);
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
                setGraphic(cellRoot);
                setStyle("-fx-background-color: transparent;");
            }
        }

        private void updateDateSeparator(Message message) {
            Message previousMessage = null;
            ListView<Message> listView = getListView();
            int index = getIndex();
            if (listView != null && index > 0 && index - 1 < listView.getItems().size()) {
                previousMessage = listView.getItems().get(index - 1);
            }

            LocalDate currentDate =
                    message.createdAt().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate previousDate = previousMessage == null
                    ? null
                    : previousMessage.createdAt().atZone(ZoneId.systemDefault()).toLocalDate();

            boolean showDateSeparator = previousDate == null || !currentDate.equals(previousDate);
            dateSeparatorLabel.setVisible(showDateSeparator);
            dateSeparatorLabel.setManaged(showDateSeparator);
            if (!showDateSeparator) {
                return;
            }

            LocalDate today = AppClock.today();
            LocalDate yesterday = today.minusDays(1);
            if (currentDate.equals(today)) {
                dateSeparatorLabel.setText("Today");
                return;
            }
            if (currentDate.equals(yesterday)) {
                dateSeparatorLabel.setText("Yesterday");
                return;
            }
            dateSeparatorLabel.setText(currentDate.format(THREAD_DATE_FORMAT));
        }
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleSendMessage() {
        String text = messageArea.getText();
        if (text != null && !text.isBlank()) {
            boolean sent = viewModel.sendMessage(text, this::handleSendSuccess);
            if (sent) {
                updateMessageLengthIndicator(messageArea.getText());
            }
        }
    }

    @SuppressWarnings("unused")
    @FXML
    private void handleRefreshConversations() {
        viewModel.refreshConversations();
    }

    private void handleSendSuccess() {
        if (messageArea != null) {
            messageArea.clear();
            updateMessageLengthIndicator(messageArea.getText());
        }
        shouldAutoScrollToBottom = true;
        scrollToLatestMessage();
    }

    private void handleMessageComposerKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
            event.consume();
            handleSendMessage();
        }
    }

    private void updateMessageLengthIndicator(String text) {
        if (messageLengthLabel == null) {
            return;
        }
        int length = text == null ? 0 : text.length();
        messageLengthLabel.setText(length + "/" + Message.MAX_LENGTH);
        if (length >= Message.MAX_LENGTH) {
            messageLengthLabel.setStyle(MESSAGE_LENGTH_STYLE_LIMIT);
            return;
        }
        if (length >= MESSAGE_WARNING_THRESHOLD) {
            messageLengthLabel.setStyle(MESSAGE_LENGTH_STYLE_WARNING);
            return;
        }
        messageLengthLabel.setStyle(MESSAGE_LENGTH_STYLE_NORMAL);
    }

    private void scrollToLatestMessage() {
        if (messageListView == null || viewModel.getActiveMessages().isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
            if (!viewModel.getActiveMessages().isEmpty()) {
                messageListView.scrollTo(viewModel.getActiveMessages().size() - 1);
            }
        });
    }

    @Override
    public void cleanup() {
        if (activeMessagesListener != null) {
            viewModel.getActiveMessages().removeListener(activeMessagesListener);
            activeMessagesListener = null;
        }
        super.cleanup();
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
