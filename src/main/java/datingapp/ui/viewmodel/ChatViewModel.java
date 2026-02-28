package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.app.usecase.messaging.MessagingUseCases.ListConversationsQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.LoadConversationQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.OpenConversationCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.SendMessageCommand;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.ReportCommand;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.connection.ConnectionService;
import datingapp.core.connection.ConnectionService.ConversationPreview;
import datingapp.core.connection.ConnectionService.SendResult;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.ui.async.AsyncErrorRouter;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.async.ViewModelAsyncScope;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
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

    private final ConnectionService messagingService;
    private final TrustSafetyService trustSafetyService;
    private final MessagingUseCases messagingUseCases;
    private final SocialUseCases socialUseCases;
    private final AppSession session;
    private final ViewModelAsyncScope asyncScope;
    private final ObservableList<ConversationPreview> conversations = FXCollections.observableArrayList();
    private final ObservableList<Message> activeMessages = FXCollections.observableArrayList();

    private final ObjectProperty<ConversationPreview> selectedConversation = new SimpleObjectProperty<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final IntegerProperty totalUnreadCount = new SimpleIntegerProperty(0);

    private User currentUser;

    private ViewModelErrorSink errorHandler;

    /** Set the error handler for send failures. */
    public void setErrorHandler(ViewModelErrorSink handler) {
        this.errorHandler = handler;
    }

    /** Keep reference to listener for cleanup. */
    private final javafx.beans.value.ChangeListener<ConversationPreview> selectionListener;

    public ChatViewModel(
            ConnectionService messagingService, TrustSafetyService trustSafetyService, AppSession session) {
        this(messagingService, trustSafetyService, session, new JavaFxUiThreadDispatcher());
    }

    public ChatViewModel(
            ConnectionService messagingService,
            TrustSafetyService trustSafetyService,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        this.messagingService = Objects.requireNonNull(messagingService, "messagingService cannot be null");
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService, "trustSafetyService cannot be null");
        this.messagingUseCases = new MessagingUseCases(this.messagingService);
        this.socialUseCases = new SocialUseCases(this.messagingService, this.trustSafetyService);
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.asyncScope = createAsyncScope(uiDispatcher);

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
        asyncScope.dispose();
        selectedConversation.removeListener(selectionListener);
        conversations.clear();
        activeMessages.clear();
        setLoadingState(false);
    }

    /**
     * Gets the current user from UISession if not set.
     */
    @Nullable
    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = session.getCurrentUser();
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
        if (asyncScope.isDisposed() || user == null) {
            setLoadingState(false);
            return;
        }

        asyncScope.runLatest(
                "chat-refresh",
                "refresh conversations",
                () -> refreshConversationData(user),
                data -> updateConversations(data.previews(), data.unreadCount()));
    }

    private ConversationRefreshData refreshConversationData(User user) {
        List<ConversationPreview> previews = List.of();
        int unread = totalUnreadCount.get();
        try {
            logInfo("Refreshing conversations for user: {}", user.getName());
            var result = messagingUseCases.listConversations(
                    new ListConversationsQuery(UserContext.ui(user.getId()), 50, 0));
            if (result.success()) {
                previews = result.data().conversations();
                unread = result.data().totalUnreadCount();
            }
        } catch (Exception e) {
            logError("Failed to refresh conversations", e);
        }
        return new ConversationRefreshData(previews, unread);
    }

    public void openConversationWithUser(UUID otherUserId, Consumer<ConversationPreview> onReady) {
        User user = ensureCurrentUser();
        if (asyncScope.isDisposed() || user == null || otherUserId == null) {
            if (onReady != null) {
                asyncScope.dispatchToUi(() -> onReady.accept(null));
            }
            return;
        }

        asyncScope.runLatest("chat-open", "open conversation", () -> loadOpenConversation(user, otherUserId), data -> {
            if (data.loaded()) {
                updateConversations(data.previews(), computeUnreadCount(data.previews()));
            }
            if (onReady != null) {
                onReady.accept(data.preview());
            }
        });
    }

    private OpenConversationData loadOpenConversation(User user, UUID otherUserId) {
        List<ConversationPreview> previews = List.of();
        ConversationPreview preview = null;
        boolean loaded = false;
        try {
            var result = messagingUseCases.openConversation(
                    new OpenConversationCommand(UserContext.ui(user.getId()), otherUserId, 50, 0));
            if (result.success()) {
                previews = messagingUseCases
                        .listConversations(new ListConversationsQuery(UserContext.ui(user.getId()), 50, 0))
                        .data()
                        .conversations();
                preview = result.data().preview();
                loaded = true;
            }
        } catch (Exception e) {
            logError("Failed to open conversation", e);
            asyncScope.onError("open conversation", e);
        }
        return new OpenConversationData(loaded, preview, previews);
    }

    /**
     * Loads messages for the specified conversation.
     *
     * <p>
     * Uses tokenized requests to prevent race conditions when rapidly switching
     * conversations.
     * If a new request arrives before this one completes, the stale token is
     * detected and the
     * background work is skipped early (before database queries).
     */
    private void loadMessages(ConversationPreview conversation) {
        User user = currentUser;
        if (asyncScope.isDisposed() || user == null || conversation == null) {
            return;
        }

        String conversationId = conversation.conversation().getId();
        UUID otherUserId = conversation.otherUser().getId();

        asyncScope.runLatest(
                "chat-messages",
                "load messages",
                () -> loadMessagesInBackground(conversationId, otherUserId, user),
                this::updateMessagesOnFx);
    }

    /**
     * Background task for loading messages. Supports early exit if conversation was
     * switched.
     *
     * @param token          the request token for race condition detection
     * @param conversationId the conversation to load messages for
     * @param otherUserId    the other user in the conversation
     * @param user           the current user
     */
    private MessageLoadData loadMessagesInBackground(String conversationId, UUID otherUserId, User user) {
        List<Message> messages = null;
        Integer unread = null;
        try {
            logInfo("Loading messages for conversation: {}", otherUserId);
            var result = messagingUseCases.loadConversation(
                    new LoadConversationQuery(UserContext.ui(user.getId()), otherUserId, 100, 0, true));
            if (result.success()) {
                messages = result.data().messages();
                var conversationsResult = messagingUseCases.listConversations(
                        new ListConversationsQuery(UserContext.ui(user.getId()), 50, 0));
                if (conversationsResult.success()) {
                    unread = conversationsResult.data().totalUnreadCount();
                }
            } else {
                logError("Failed to load messages: " + result.error().message(), null);
            }
        } catch (Exception e) {
            logError("Failed to load messages", e);
            asyncScope.onError("load messages", e);
        }

        return new MessageLoadData(conversationId, messages, unread);
    }

    /**
     * Updates message display on FX thread after background load completes.
     *
     * @param token          the request token for final race condition check
     * @param conversationId the conversation loaded
     * @param messages       the loaded messages (or null if load failed)
     * @param unread         the new unread count (or null if computation failed)
     */
    private void updateMessagesOnFx(MessageLoadData messageData) {
        ConversationPreview selected = selectedConversation.get();
        // Final defensive check before updating UI
        if (!asyncScope.isDisposed()
                && selected != null
                && selected.conversation().getId().equals(messageData.conversationId())) {
            if (messageData.messages() != null) {
                activeMessages.setAll(messageData.messages());
            }
            if (messageData.unreadCount() != null) {
                totalUnreadCount.set(messageData.unreadCount());
            }
        }
    }

    public boolean sendMessage(String text) {
        ConversationPreview conversation = selectedConversation.get();
        User sender = currentUser;
        if (sender == null || conversation == null || text == null || text.isBlank()) {
            return false;
        }

        String trimmedText = text.trim();
        logInfo("Sending message to: {}", conversation.otherUser().getName());
        dispatchSendMessage(
                conversation, sender.getId(), conversation.otherUser().getId(), trimmedText);

        return true;
    }

    private void dispatchSendMessage(
            ConversationPreview conversation, UUID senderId, UUID otherUserId, String trimmedText) {
        asyncScope.runFireAndForget("send message", () -> {
            try {
                var result = messagingUseCases.sendMessage(
                        new SendMessageCommand(UserContext.ui(senderId), otherUserId, trimmedText));
                asyncScope.dispatchToUi(() -> handleSendResult(
                        conversation,
                        result.success() ? result.data() : null,
                        result.success() ? null : result.error().message()));
            } catch (Exception e) {
                asyncScope.dispatchToUi(() -> reportSendFailure("Failed to send message: " + e.getMessage(), e));
            }
        });
    }

    private void handleSendResult(
            ConversationPreview conversation, @Nullable SendResult result, @Nullable String errorMessage) {
        if (asyncScope.isDisposed()) {
            return;
        }
        if (result == null || !result.success()) {
            reportSendFailure(
                    "Failed to send message: " + (errorMessage == null ? "Unknown error" : errorMessage), null);
            return;
        }

        ConversationPreview selected = selectedConversation.get();
        if (selected != null
                && selected.conversation()
                        .getId()
                        .equals(conversation.conversation().getId())) {
            loadMessages(selected);
        }
    }

    private void reportSendFailure(String message, Exception error) {
        if (errorHandler != null) {
            errorHandler.onError(message);
            return;
        }
        if (error != null) {
            logger.warn(message, error);
        } else {
            logger.warn(message);
        }
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
        if (asyncScope.isDisposed()) {
            return;
        }
        conversations.setAll(previews);
        totalUnreadCount.set(unread);
    }

    private static int computeUnreadCount(List<ConversationPreview> previews) {
        return previews.stream().mapToInt(ConversationPreview::unreadCount).sum();
    }

    private void setLoadingState(boolean isLoading) {
        if (loading.get() != isLoading) {
            loading.set(isLoading);
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

    public void blockUser(UUID targetId) {
        User user = ensureCurrentUser();
        if (user == null || targetId == null) {
            return;
        }
        asyncScope.runFireAndForget("block user", () -> {
            try {
                socialUseCases.blockUser(new RelationshipCommand(UserContext.ui(user.getId()), targetId));
                asyncScope.dispatchToUi(this::refreshConversations);
            } catch (Exception e) {
                logError("Failed to block user", e);
            }
        });
    }

    public void reportUser(UUID targetId, Report.Reason reason, String description, boolean blockUser) {
        User user = ensureCurrentUser();
        if (user == null || targetId == null || reason == null) {
            return;
        }
        asyncScope.runFireAndForget("report user", () -> {
            try {
                socialUseCases.reportUser(
                        new ReportCommand(UserContext.ui(user.getId()), targetId, reason, description, blockUser));
                if (blockUser) {
                    asyncScope.dispatchToUi(this::refreshConversations);
                }
            } catch (Exception e) {
                logError("Failed to report user", e);
            }
        });
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

    private ViewModelAsyncScope createAsyncScope(UiThreadDispatcher uiDispatcher) {
        UiThreadDispatcher dispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher cannot be null");
        ViewModelAsyncScope scope = new ViewModelAsyncScope(
                "chat", dispatcher, new AsyncErrorRouter(logger, dispatcher, () -> errorHandler));
        scope.setLoadingStateConsumer(this::setLoadingState);
        return scope;
    }

    private record ConversationRefreshData(List<ConversationPreview> previews, int unreadCount) {}

    private record OpenConversationData(
            boolean loaded, ConversationPreview preview, List<ConversationPreview> previews) {}

    private record MessageLoadData(String conversationId, List<Message> messages, Integer unreadCount) {}
}
