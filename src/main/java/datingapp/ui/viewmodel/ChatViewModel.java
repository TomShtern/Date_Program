package datingapp.ui.viewmodel;

import datingapp.core.Messaging.Message;
import datingapp.core.MessagingService;
import datingapp.core.MessagingService.ConversationPreview;
import datingapp.core.User;
import datingapp.ui.ViewModelFactory.UISession;
import java.util.List;
import java.util.UUID;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Messaging screen.
 * Handles the conversation list and the active message thread.
 */
public class ChatViewModel {
    private static final Logger logger = LoggerFactory.getLogger(ChatViewModel.class);

    private final MessagingService messagingService;
    private final ObservableList<ConversationPreview> conversations = FXCollections.observableArrayList();
    private final ObservableList<Message> activeMessages = FXCollections.observableArrayList();

    private final ObjectProperty<ConversationPreview> selectedConversation = new SimpleObjectProperty<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final IntegerProperty totalUnreadCount = new SimpleIntegerProperty(0);

    private User currentUser;

    public ChatViewModel(MessagingService messagingService) {
        this.messagingService = messagingService;

        // Listen for selection changes to load messages
        selectedConversation.addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadMessages(newVal);
            } else {
                activeMessages.clear();
            }
        });
    }

    /**
     * Gets the current user from UISession if not set.
     */
    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = UISession.getInstance().getCurrentUser();
        }
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        refreshConversations();
    }

    /**
     * Initialize from UISession.
     */
    public void initialize() {
        if (ensureCurrentUser() != null) {
            refreshConversations();
        }
    }

    public void refreshConversations() {
        if (ensureCurrentUser() == null) {
            return;
        }

        logger.info("Refreshing conversations for user: {}", currentUser.getName());
        loading.set(true);
        List<ConversationPreview> previews = messagingService.getConversations(currentUser.getId());
        conversations.setAll(previews);

        // Calculate total unread
        int unread =
                previews.stream().mapToInt(ConversationPreview::unreadCount).sum();
        totalUnreadCount.set(unread);

        loading.set(false);
    }

    private void loadMessages(ConversationPreview conversation) {
        if (currentUser == null) {
            return;
        }

        logger.info(
                "Loading messages for conversation: {}",
                conversation.otherUser().getName());
        List<Message> messages = messagingService.getMessages(
                currentUser.getId(), conversation.otherUser().getId(), 100, 0);
        activeMessages.setAll(messages);

        // Mark as read
        messagingService.markAsRead(
                currentUser.getId(), conversation.conversation().getId());

        // Refresh to update unread counts
        refreshUnreadCount();
    }

    private void refreshUnreadCount() {
        if (currentUser == null) {
            return;
        }
        List<ConversationPreview> previews = messagingService.getConversations(currentUser.getId());
        int unread =
                previews.stream().mapToInt(ConversationPreview::unreadCount).sum();
        totalUnreadCount.set(unread);
    }

    public void sendMessage(String text) {
        if (currentUser == null || selectedConversation.get() == null || text == null || text.isBlank()) {
            return;
        }

        logger.info(
                "Sending message to: {}", selectedConversation.get().otherUser().getName());
        messagingService.sendMessage(
                currentUser.getId(), selectedConversation.get().otherUser().getId(), text.trim());

        // Refresh local messages
        loadMessages(selectedConversation.get());
    }

    /**
     * Check if a message was sent by the current user.
     */
    public boolean isMessageFromCurrentUser(Message message) {
        return currentUser != null && message.senderId().equals(currentUser.getId());
    }

    /**
     * Get the current user's ID for message styling.
     */
    public UUID getCurrentUserId() {
        return currentUser != null ? currentUser.getId() : null;
    }

    // --- Properties ---
    public ObservableList<ConversationPreview> getConversations() {
        return conversations;
    }

    public ObservableList<Message> getActiveMessages() {
        return activeMessages;
    }

    public ObjectProperty<ConversationPreview> selectedConversationProperty() {
        return selectedConversation;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public IntegerProperty totalUnreadCountProperty() {
        return totalUnreadCount;
    }
}
