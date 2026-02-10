package datingapp.ui.viewmodel;

import datingapp.core.AppSession;
import datingapp.core.Messaging.Conversation;
import datingapp.core.Messaging.Message;
import datingapp.core.MessagingService;
import datingapp.core.MessagingService.ConversationPreview;
import datingapp.core.User;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.Nullable;
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
    private final AtomicInteger activeLoads = new AtomicInteger(0);
    private final AtomicLong messageLoadToken = new AtomicLong(0);

    private User currentUser;

    /** Track disposed state to prevent operations after cleanup. */
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    /** Keep reference to listener for cleanup. */
    private final javafx.beans.value.ChangeListener<ConversationPreview> selectionListener;

    public ChatViewModel(MessagingService messagingService) {
        this.messagingService = messagingService;

        // Listen for selection changes to load messages
        selectionListener = (obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadMessages(newVal);
            } else {
                activeMessages.clear();
            }
        };
        selectedConversation.addListener(selectionListener);
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        selectedConversation.removeListener(selectionListener);
        conversations.clear();
        activeMessages.clear();
        activeLoads.set(0);
        setLoadingState(false);
    }

    /**
     * Gets the current user from UISession if not set.
     */
    @Nullable
    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = AppSession.getInstance().getCurrentUser();
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
        User user = ensureCurrentUser();
        if (disposed.get() || user == null) {
            activeLoads.set(0);
            setLoadingState(false);
            return;
        }

        beginLoading();
        Thread.ofVirtual().name("chat-refresh").start(() -> {
            List<ConversationPreview> previews = List.of();
            int unread = totalUnreadCount.get();
            try {
                logInfo("Refreshing conversations for user: {}", user.getName());
                previews = messagingService.getConversations(user.getId());
                unread = computeUnreadCount(previews);
            } catch (Exception e) {
                logError("Failed to refresh conversations", e);
            }

            List<ConversationPreview> finalPreviews = previews;
            int finalUnread = unread;
            runOnFx(() -> {
                if (!disposed.get()) {
                    updateConversations(finalPreviews, finalUnread);
                }
                endLoading();
            });
        });
    }

    public void openConversationWithUser(UUID otherUserId, Consumer<ConversationPreview> onReady) {
        User user = ensureCurrentUser();
        if (disposed.get() || user == null || otherUserId == null) {
            if (onReady != null) {
                Platform.runLater(() -> onReady.accept(null));
            }
            return;
        }

        beginLoading();
        Thread.ofVirtual().name("chat-open").start(() -> {
            List<ConversationPreview> previews = List.of();
            ConversationPreview preview = null;
            boolean loaded = false;
            try {
                Conversation conversation = messagingService.getOrCreateConversation(user.getId(), otherUserId);
                previews = messagingService.getConversations(user.getId());
                preview = previews.stream()
                        .filter(p -> p.conversation().getId().equals(conversation.getId()))
                        .findFirst()
                        .orElse(null);
                loaded = true;
            } catch (Exception e) {
                logError("Failed to open conversation", e);
            }

            ConversationPreview finalPreview = preview;
            List<ConversationPreview> finalPreviews = previews;
            boolean finalLoaded = loaded;
            runOnFx(() -> {
                if (!disposed.get() && finalLoaded) {
                    updateConversations(finalPreviews, computeUnreadCount(finalPreviews));
                }
                if (onReady != null) {
                    onReady.accept(finalPreview);
                }
                endLoading();
            });
        });
    }

    /**
     * Loads messages for the specified conversation.
     *
     * <p>Uses tokenized requests to prevent race conditions when rapidly switching conversations.
     * If a new request arrives before this one completes, the stale token is detected and the
     * background work is skipped early (before database queries).
     */
    private void loadMessages(ConversationPreview conversation) {
        User user = currentUser;
        if (disposed.get() || user == null || conversation == null) {
            return;
        }

        long token = messageLoadToken.incrementAndGet();
        String conversationId = conversation.conversation().getId();
        UUID otherUserId = conversation.otherUser().getId();

        beginLoading();
        Thread.ofVirtual()
                .name("chat-messages-load")
                .start(() -> loadMessagesInBackground(token, conversationId, otherUserId, user));
    }

    /**
     * Background task for loading messages. Supports early exit if conversation was switched.
     *
     * @param token the request token for race condition detection
     * @param conversationId the conversation to load messages for
     * @param otherUserId the other user in the conversation
     * @param user the current user
     */
    private void loadMessagesInBackground(long token, String conversationId, UUID otherUserId, User user) {
        // Early check: if token is stale, skip database work immediately
        if (messageLoadToken.get() != token) {
            runOnFx(this::endLoading);
            return;
        }

        List<Message> messages = null;
        Integer unread = null;
        try {
            logInfo("Loading messages for conversation: {}", otherUserId);
            messages = messagingService.getMessages(user.getId(), otherUserId, 100, 0);
            messagingService.markAsRead(user.getId(), conversationId);

            List<ConversationPreview> previews = messagingService.getConversations(user.getId());
            unread = computeUnreadCount(previews);
        } catch (Exception e) {
            logError("Failed to load messages", e);
        }

        List<Message> finalMessages = messages;
        Integer finalUnread = unread;
        runOnFx(() -> updateMessagesOnFx(token, conversationId, finalMessages, finalUnread));
    }

    /**
     * Updates message display on FX thread after background load completes.
     *
     * @param token the request token for final race condition check
     * @param conversationId the conversation loaded
     * @param messages the loaded messages (or null if load failed)
     * @param unread the new unread count (or null if computation failed)
     */
    private void updateMessagesOnFx(long token, String conversationId, List<Message> messages, Integer unread) {
        ConversationPreview selected = selectedConversation.get();
        // Final defensive check before updating UI
        if (!disposed.get()
                && selected != null
                && selected.conversation().getId().equals(conversationId)
                && token == messageLoadToken.get()) {
            if (messages != null) {
                activeMessages.setAll(messages);
            }
            if (unread != null) {
                totalUnreadCount.set(unread);
            }
        }
        endLoading();
    }

    public void sendMessage(String text) {
        if (currentUser == null || selectedConversation.get() == null || text == null || text.isBlank()) {
            return;
        }

        logInfo("Sending message to: {}", selectedConversation.get().otherUser().getName());
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
    @Nullable
    public UUID getCurrentUserId() {
        return currentUser != null ? currentUser.getId() : null;
    }

    private void updateConversations(List<ConversationPreview> previews, int unread) {
        if (disposed.get()) {
            return;
        }
        conversations.setAll(previews);
        totalUnreadCount.set(unread);
    }

    private static int computeUnreadCount(List<ConversationPreview> previews) {
        return previews.stream().mapToInt(ConversationPreview::unreadCount).sum();
    }

    private void beginLoading() {
        if (activeLoads.incrementAndGet() == 1) {
            setLoadingState(true);
        }
    }

    private void endLoading() {
        int remaining = activeLoads.decrementAndGet();
        if (remaining <= 0) {
            activeLoads.set(0);
            setLoadingState(false);
        }
    }

    private void setLoadingState(boolean isLoading) {
        runOnFx(() -> {
            if (loading.get() != isLoading) {
                loading.set(isLoading);
            }
        });
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logError(String message, Throwable error) {
        if (logger.isErrorEnabled()) {
            logger.error(message, error);
        }
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
