package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.app.usecase.messaging.MessagingUseCases.SendMessageCommand;
import datingapp.app.usecase.social.SocialUseCases;
import datingapp.app.usecase.social.SocialUseCases.RelationshipCommand;
import datingapp.app.usecase.social.SocialUseCases.RelationshipTransitionOutcome;
import datingapp.app.usecase.social.SocialUseCases.ReportCommand;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.connection.ConnectionService.ConversationPreview;
import datingapp.core.connection.ConnectionService.SendResult;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.TaskHandle;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.ConversationLoader.ConversationRefreshData;
import datingapp.ui.viewmodel.ConversationLoader.MessageLoadData;
import datingapp.ui.viewmodel.ConversationLoader.OpenConversationData;
import datingapp.ui.viewmodel.UiDataAdapters.NoOpUiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.NoOpUiProfileNoteDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.PresenceStatus;
import datingapp.ui.viewmodel.UiDataAdapters.UiPresenceDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiProfileNoteDataAccess;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.Nullable;

/**
 * ViewModel for the Messaging screen.
 * Handles the conversation list and the active message thread.
 */
public class ChatViewModel extends BaseViewModel {
    private static final Duration DEFAULT_CONVERSATION_POLL_INTERVAL = Duration.ofSeconds(15);
    private static final Duration DEFAULT_ACTIVE_CONVERSATION_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final String TASK_LOAD_MESSAGES = "load messages";
    private static final String TASK_REFRESH_CONVERSATIONS = "refresh conversations";

    public record ChatUiDependencies(UiProfileNoteDataAccess noteDataAccess, UiPresenceDataAccess presenceDataAccess) {
        public ChatUiDependencies {
            Objects.requireNonNull(noteDataAccess, "noteDataAccess cannot be null");
            Objects.requireNonNull(presenceDataAccess, "presenceDataAccess cannot be null");
        }

        public static ChatUiDependencies noOp() {
            return new ChatUiDependencies(new NoOpUiProfileNoteDataAccess(), new NoOpUiPresenceDataAccess());
        }
    }

    private final MessagingUseCases messagingUseCases;
    private final ConversationLoader conversationLoader;
    private final SocialUseCases socialUseCases;
    private final UiProfileNoteDataAccess noteDataAccess;
    private final UiPresenceDataAccess presenceDataAccess;
    private final AppSession session;
    private final Duration conversationPollInterval;
    private final Duration activeConversationPollInterval;
    private final ObservableList<ConversationPreview> conversations = FXCollections.observableArrayList();
    private final ObservableList<Message> activeMessages = FXCollections.observableArrayList();

    private final ObjectProperty<ConversationPreview> selectedConversation = new SimpleObjectProperty<>();
    private final BooleanProperty sending = new SimpleBooleanProperty(false);
    private final IntegerProperty totalUnreadCount = new SimpleIntegerProperty(0);
    private final StringProperty profileNoteContent = new SimpleStringProperty("");
    private final StringProperty profileNoteStatusMessage = new SimpleStringProperty();
    private final BooleanProperty profileNoteBusy = new SimpleBooleanProperty(false);
    private final ObjectProperty<PresenceStatus> presenceStatus = new SimpleObjectProperty<>(PresenceStatus.UNKNOWN);
    private final BooleanProperty remoteTyping = new SimpleBooleanProperty(false);
    private final BooleanProperty presenceSupported = new SimpleBooleanProperty(false);
    private final StringProperty presenceUnavailableMessage = new SimpleStringProperty("");

    private User currentUser;
    private TaskHandle conversationsPollingHandle;
    private TaskHandle messagesPollingHandle;
    private final AtomicInteger noteLoadToken = new AtomicInteger();
    private PauseTransition profileNoteStatusDismissTransition;

    /** Set the error handler for send failures. */
    public void setErrorHandler(ViewModelErrorSink handler) {
        setErrorSink(handler);
    }

    /** Keep reference to listener for cleanup. */
    private final javafx.beans.value.ChangeListener<ConversationPreview> selectionListener;

    public ChatViewModel(MessagingUseCases messagingUseCases, SocialUseCases socialUseCases, AppSession session) {
        this(messagingUseCases, socialUseCases, session, new JavaFxUiThreadDispatcher());
    }

    public ChatViewModel(
            MessagingUseCases messagingUseCases,
            SocialUseCases socialUseCases,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        this(
                messagingUseCases,
                socialUseCases,
                session,
                uiDispatcher,
                DEFAULT_CONVERSATION_POLL_INTERVAL,
                DEFAULT_ACTIVE_CONVERSATION_POLL_INTERVAL,
                ChatUiDependencies.noOp());
    }

    public ChatViewModel(
            MessagingUseCases messagingUseCases,
            SocialUseCases socialUseCases,
            AppSession session,
            UiThreadDispatcher uiDispatcher,
            Duration conversationPollInterval,
            Duration activeConversationPollInterval) {
        this(
                messagingUseCases,
                socialUseCases,
                session,
                uiDispatcher,
                conversationPollInterval,
                activeConversationPollInterval,
                ChatUiDependencies.noOp());
    }

    public ChatViewModel(
            MessagingUseCases messagingUseCases,
            SocialUseCases socialUseCases,
            AppSession session,
            UiThreadDispatcher uiDispatcher,
            Duration conversationPollInterval,
            Duration activeConversationPollInterval,
            ChatUiDependencies uiDependencies) {
        super("chat", uiDispatcher);
        this.messagingUseCases = Objects.requireNonNull(messagingUseCases, "messagingUseCases cannot be null");
        this.conversationLoader = new ConversationLoader(this.messagingUseCases);
        this.socialUseCases = Objects.requireNonNull(socialUseCases, "socialUseCases cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.conversationPollInterval =
                Objects.requireNonNull(conversationPollInterval, "conversationPollInterval cannot be null");
        this.activeConversationPollInterval =
                Objects.requireNonNull(activeConversationPollInterval, "activeConversationPollInterval cannot be null");
        ChatUiDependencies resolvedUiDependencies =
                Objects.requireNonNull(uiDependencies, "uiDependencies cannot be null");
        this.noteDataAccess = resolvedUiDependencies.noteDataAccess();
        this.presenceDataAccess = resolvedUiDependencies.presenceDataAccess();
        this.presenceSupported.set(this.presenceDataAccess.isSupported());
        this.presenceUnavailableMessage.set(this.presenceDataAccess.unsupportedReason());

        // Listen for selection changes to load messages
        selectionListener = (obs, oldVal, newVal) -> {
            if (oldVal != null
                    && newVal != null
                    && oldVal.conversation()
                            .getId()
                            .equals(newVal.conversation().getId())) {
                return;
            }
            if (newVal != null) {
                loadMessages(newVal);
                startMessagesPolling();
                clearProfileNoteState();
                loadProfileNoteFor(newVal.otherUser());
                refreshPresenceState(newVal.otherUser());
            } else {
                activeMessages.clear();
                stopMessagesPolling();
                clearProfileNoteState();
                clearPresenceState();
            }
        };
        selectedConversation.addListener(selectionListener);
    }

    public ChatViewModel(
            MessagingUseCases messagingUseCases, SocialUseCases socialUseCases, AppSession session, AppConfig config) {
        this(
                messagingUseCases,
                socialUseCases,
                session,
                new JavaFxUiThreadDispatcher(),
                Duration.ofSeconds(config.validation().chatBackgroundPollSeconds()),
                Duration.ofSeconds(config.validation().chatActivePollSeconds()),
                ChatUiDependencies.noOp());
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    @Override
    protected void onDispose() {
        stopConversationsPolling();
        stopMessagesPolling();
        clearProfileNoteState();
        selectedConversation.removeListener(selectionListener);
        conversations.clear();
        activeMessages.clear();
        clearPresenceState();
        setLoadingState(false);
        sending.set(false);
    }

    private void refreshPresenceState(User otherUser) {
        User user = ensureCurrentUser();
        if (user == null || otherUser == null) {
            clearPresenceState();
            return;
        }

        UUID otherUserId = otherUser.getId();
        asyncScope.runFireAndForget("load presence", () -> {
            try {
                PresenceStatus status = presenceDataAccess.getPresence(otherUserId);
                boolean typing = presenceDataAccess.isTyping(otherUserId);
                asyncScope.dispatchToUi(() -> applyPresenceState(otherUserId, status, typing));
            } catch (Exception e) {
                logError("Failed to load presence state", e);
                asyncScope.dispatchToUi(() -> applyPresenceState(otherUserId, PresenceStatus.UNKNOWN, false));
            }
        });
    }

    private void applyPresenceState(UUID otherUserId, PresenceStatus status, boolean typing) {
        if (asyncScope.isDisposed()) {
            return;
        }
        ConversationPreview selected = selectedConversation.get();
        if (selected == null || !selected.otherUser().getId().equals(otherUserId)) {
            return;
        }
        presenceStatus.set(status != null ? status : PresenceStatus.UNKNOWN);
        remoteTyping.set(typing);
    }

    private void clearPresenceState() {
        presenceStatus.set(PresenceStatus.UNKNOWN);
        remoteTyping.set(false);
    }

    private void loadProfileNoteFor(User otherUser) {
        User user = ensureCurrentUser();
        if (user == null || otherUser == null) {
            clearProfileNoteState();
            return;
        }

        int token = noteLoadToken.incrementAndGet();
        setProfileNoteStatus(null, token, false);
        profileNoteBusy.set(true);
        asyncScope.runFireAndForget("load profile note", () -> {
            try {
                ProfileNote note = noteDataAccess
                        .getProfileNote(user.getId(), otherUser.getId())
                        .orElse(null);
                asyncScope.dispatchToUi(() -> applyLoadedProfileNote(otherUser.getId(), note, token));
            } catch (Exception e) {
                asyncScope.dispatchToUi(
                        () -> applyProfileNoteFailure(otherUser.getId(), token, "Failed to load note", e));
            }
        });
    }

    public void saveSelectedProfileNote() {
        User user = ensureCurrentUser();
        ConversationPreview selected = selectedConversation.get();
        if (user == null || selected == null) {
            return;
        }

        UUID otherUserId = selected.otherUser().getId();
        profileNoteBusy.set(true);
        profileNoteStatusMessage.set(null);
        String content = profileNoteContent.get();
        asyncScope.runFireAndForget("save profile note", () -> {
            try {
                ProfileNote savedNote = noteDataAccess.upsertProfileNote(user.getId(), otherUserId, content);
                int token = noteLoadToken.incrementAndGet();
                asyncScope.dispatchToUi(() -> {
                    if (!isSelectedConversation(otherUserId, token)) {
                        return;
                    }
                    profileNoteContent.set(savedNote.content());
                    setProfileNoteStatus("Private note saved.", token, true);
                    profileNoteBusy.set(false);
                });
            } catch (Exception e) {
                asyncScope.dispatchToUi(
                        () -> applyProfileNoteFailure(otherUserId, noteLoadToken.get(), "Failed to save note", e));
            }
        });
    }

    public void deleteSelectedProfileNote() {
        User user = ensureCurrentUser();
        ConversationPreview selected = selectedConversation.get();
        if (user == null || selected == null) {
            return;
        }

        UUID otherUserId = selected.otherUser().getId();
        profileNoteBusy.set(true);
        profileNoteStatusMessage.set(null);
        asyncScope.runFireAndForget("delete profile note", () -> {
            try {
                noteDataAccess.deleteProfileNote(user.getId(), otherUserId);
                int token = noteLoadToken.incrementAndGet();
                asyncScope.dispatchToUi(() -> {
                    if (!isSelectedConversation(otherUserId, token)) {
                        return;
                    }
                    profileNoteContent.set("");
                    setProfileNoteStatus("Private note deleted.", token, true);
                    profileNoteBusy.set(false);
                });
            } catch (Exception e) {
                asyncScope.dispatchToUi(
                        () -> applyProfileNoteFailure(otherUserId, noteLoadToken.get(), "Failed to delete note", e));
            }
        });
    }

    private void applyLoadedProfileNote(UUID otherUserId, ProfileNote note, int token) {
        if (!isSelectedConversation(otherUserId, token)) {
            return;
        }
        profileNoteContent.set(note != null ? note.content() : "");
        setProfileNoteStatus(null, token, false);
        profileNoteBusy.set(false);
    }

    private void applyProfileNoteFailure(UUID otherUserId, int token, String message, Exception error) {
        if (!isSelectedConversation(otherUserId, token)) {
            return;
        }
        if (error != null) {
            logError(message, error);
        }
        setProfileNoteStatus(message, token, true);
        profileNoteBusy.set(false);
    }

    private void setProfileNoteStatus(@Nullable String message, int token, boolean autoDismiss) {
        cancelProfileNoteStatusDismiss();
        profileNoteStatusMessage.set(message);
        if (!autoDismiss || message == null || message.isBlank()) {
            return;
        }
        PauseTransition pause = new PauseTransition(javafx.util.Duration.seconds(3));
        profileNoteStatusDismissTransition = pause;
        pause.setOnFinished(_ -> {
            if (!Objects.equals(pause, profileNoteStatusDismissTransition)) {
                return;
            }
            profileNoteStatusDismissTransition = null;
            if (token == noteLoadToken.get()) {
                profileNoteStatusMessage.set(null);
            }
        });
        pause.play();
    }

    private void cancelProfileNoteStatusDismiss() {
        if (profileNoteStatusDismissTransition != null) {
            profileNoteStatusDismissTransition.stop();
            profileNoteStatusDismissTransition = null;
        }
    }

    private boolean isSelectedConversation(UUID otherUserId, int token) {
        ConversationPreview selected = selectedConversation.get();
        return token == noteLoadToken.get()
                && selected != null
                && selected.otherUser().getId().equals(otherUserId);
    }

    private void clearProfileNoteState() {
        noteLoadToken.incrementAndGet();
        cancelProfileNoteStatusDismiss();
        profileNoteContent.set("");
        profileNoteStatusMessage.set(null);
        profileNoteBusy.set(false);
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
        if (user != null) {
            startConversationsPolling();
        } else {
            stopConversationsPolling();
            stopMessagesPolling();
        }
    }

    /**
     * Initialize from UISession.
     */
    public void initialize() {
        if (ensureCurrentUser() != null) {
            refreshConversations();
            startConversationsPolling();
        }
    }

    public void refreshConversations() {
        refreshConversations(false);
    }

    private void refreshConversations(boolean silent) {
        User user = ensureCurrentUser();
        if (asyncScope.isDisposed() || user == null) {
            setLoadingState(false);
            return;
        }

        if (silent) {
            asyncScope.runLatestSilently(
                    "chat-refresh",
                    TASK_REFRESH_CONVERSATIONS,
                    () -> refreshConversationData(user),
                    this::applyConversationRefreshData);
            return;
        }

        asyncScope.runLatest(
                "chat-refresh",
                TASK_REFRESH_CONVERSATIONS,
                () -> refreshConversationData(user),
                this::applyConversationRefreshData);
    }

    private ConversationRefreshData refreshConversationData(User user) {
        try {
            logInfo("Refreshing conversations for user: {}", user.getName());
            return conversationLoader.refreshConversations(user, totalUnreadCount.get());
        } catch (Exception e) {
            logError("Failed to refresh conversations", e);
        }
        return new ConversationRefreshData(null, totalUnreadCount.get());
    }

    private void applyConversationRefreshData(ConversationRefreshData data) {
        if (data.previews() != null) {
            updateConversations(data.previews(), data.unreadCount());
            return;
        }
        totalUnreadCount.set(data.unreadCount());
    }

    private void pollConversationsOnce() {
        User user = ensureCurrentUser();
        if (asyncScope.isDisposed()) {
            return;
        }
        if (user == null) {
            asyncScope.dispatchToUi(this::stopConversationsPolling);
            return;
        }

        ConversationRefreshData data = refreshConversationData(user);
        asyncScope.dispatchToUi(() -> {
            if (asyncScope.isDisposed()) {
                return;
            }
            applyConversationRefreshData(data);
        });
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
            if (data.loaded() && data.preview() != null) {
                upsertConversationPreview(data.preview());
            }
            if (onReady != null) {
                onReady.accept(data.preview());
            }
        });
    }

    private OpenConversationData loadOpenConversation(User user, UUID otherUserId) {
        try {
            return conversationLoader.openConversation(user, otherUserId);
        } catch (Exception e) {
            logError("Failed to open conversation", e);
            asyncScope.onError("open conversation", e);
        }
        return new OpenConversationData(false, null);
    }

    private void upsertConversationPreview(ConversationPreview preview) {
        if (asyncScope.isDisposed() || preview == null) {
            return;
        }

        List<ConversationPreview> updatedPreviews = new ArrayList<>(conversations);
        int existingIndex = -1;
        for (int index = 0; index < updatedPreviews.size(); index++) {
            if (updatedPreviews
                    .get(index)
                    .conversation()
                    .getId()
                    .equals(preview.conversation().getId())) {
                existingIndex = index;
                break;
            }
        }

        if (existingIndex >= 0) {
            updatedPreviews.set(existingIndex, preview);
        } else {
            updatedPreviews.addFirst(preview);
        }

        updateConversations(updatedPreviews, computeUnreadCount(updatedPreviews));
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
        loadMessages(conversation, false);
    }

    private void loadMessages(ConversationPreview conversation, boolean silent) {
        User user = currentUser;
        if (asyncScope.isDisposed() || user == null || conversation == null) {
            return;
        }

        String conversationId = conversation.conversation().getId();
        UUID otherUserId = conversation.otherUser().getId();

        if (silent) {
            asyncScope.runLatestSilently(
                    "chat-messages",
                    TASK_LOAD_MESSAGES,
                    () -> loadMessagesInBackground(conversationId, otherUserId, user),
                    this::updateMessagesOnFx);
            return;
        }

        asyncScope.runLatest(
                "chat-messages",
                TASK_LOAD_MESSAGES,
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
        try {
            logInfo("Loading messages for conversation: {}", otherUserId);
            return conversationLoader.loadMessages(user, otherUserId, conversationId);
        } catch (Exception e) {
            logError("Failed to load messages", e);
            asyncScope.onError(TASK_LOAD_MESSAGES, e);
        }
        return new MessageLoadData(conversationId, null, null, null);
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
            if (messageData.messages() != null && !sameMessages(activeMessages, messageData.messages())) {
                activeMessages.setAll(messageData.messages());
            }
            if (messageData.previews() != null) {
                int unread = messageData.unreadCount() != null ? messageData.unreadCount() : totalUnreadCount.get();
                updateConversations(messageData.previews(), unread);
                return;
            }
            if (messageData.unreadCount() != null) {
                totalUnreadCount.set(messageData.unreadCount());
            }
        }
    }

    private void pollMessagesOnce() {
        if (asyncScope.isDisposed()) {
            return;
        }

        ConversationPreview conversation = selectedConversation.get();
        User user = currentUser;
        if (conversation == null || user == null) {
            asyncScope.dispatchToUi(this::stopMessagesPolling);
            return;
        }

        UUID otherUserId = conversation.otherUser().getId();
        MessageLoadData messageData =
                loadMessagesInBackground(conversation.conversation().getId(), otherUserId, user);
        asyncScope.dispatchToUi(() -> updateMessagesOnFx(messageData));
        pollPresenceState(otherUserId);
    }

    private void pollPresenceState(UUID otherUserId) {
        try {
            PresenceStatus status = presenceDataAccess.getPresence(otherUserId);
            boolean typing = presenceDataAccess.isTyping(otherUserId);
            asyncScope.dispatchToUi(() -> applyPresenceState(otherUserId, status, typing));
        } catch (Exception e) {
            logError("Failed to load presence state", e);
            asyncScope.dispatchToUi(() -> applyPresenceState(otherUserId, PresenceStatus.UNKNOWN, false));
        }
    }

    public boolean sendMessage(String text) {
        return sendMessage(text, null);
    }

    public boolean sendMessage(String text, @Nullable Runnable onSuccess) {
        ConversationPreview conversation = selectedConversation.get();
        User sender = currentUser;
        if (sender == null || conversation == null || text == null || text.isBlank()) {
            return false;
        }

        String trimmedText = text.trim();
        logInfo("Sending message to: {}", conversation.otherUser().getName());
        sending.set(true);
        dispatchSendMessage(
                conversation, sender.getId(), conversation.otherUser().getId(), trimmedText, onSuccess);

        return true;
    }

    private void dispatchSendMessage(
            ConversationPreview conversation,
            UUID senderId,
            UUID otherUserId,
            String trimmedText,
            @Nullable Runnable onSuccess) {
        asyncScope.runFireAndForget("send message", () -> {
            try {
                var result = messagingUseCases.sendMessage(
                        new SendMessageCommand(UserContext.ui(senderId), otherUserId, trimmedText));
                asyncScope.dispatchToUi(() -> handleSendResult(
                        conversation,
                        result.success() ? result.data() : null,
                        result.success() ? null : result.error().message(),
                        onSuccess));
            } catch (Exception e) {
                asyncScope.dispatchToUi(() -> reportSendFailure("Failed to send message: " + e.getMessage(), e));
            }
        });
    }

    private void handleSendResult(
            ConversationPreview conversation,
            @Nullable SendResult result,
            @Nullable String errorMessage,
            @Nullable Runnable onSuccess) {
        if (asyncScope.isDisposed()) {
            return;
        }
        sending.set(false);
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
            if (onSuccess != null) {
                onSuccess.run();
            }
            loadMessages(selected);
        }
    }

    private void reportSendFailure(String message, Exception error) {
        sending.set(false);
        if (hasErrorSink()) {
            notifyError(message, error);
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
        String selectedConversationId = selectedConversation.get() != null
                ? selectedConversation.get().conversation().getId()
                : null;

        if (!sameConversationPreviews(conversations, previews)) {
            conversations.setAll(previews);
        }
        totalUnreadCount.set(unread);
        restoreSelection(previews, selectedConversationId);
    }

    private void restoreSelection(List<ConversationPreview> previews, String selectedConversationId) {
        if (selectedConversationId == null) {
            return;
        }
        ConversationPreview restored = previews.stream()
                .filter(preview -> preview.conversation().getId().equals(selectedConversationId))
                .findFirst()
                .orElse(null);
        if (restored == null) {
            selectedConversation.set(null);
            activeMessages.clear();
            return;
        }
        ConversationPreview currentlySelected = selectedConversation.get();
        if (currentlySelected == null || !sameConversationPreview(currentlySelected, restored)) {
            selectedConversation.set(restored);
        }
    }

    private static boolean sameConversationPreview(ConversationPreview current, ConversationPreview candidate) {
        if (current == null || candidate == null) {
            return false;
        }
        if (!current.conversation().getId().equals(candidate.conversation().getId())) {
            return false;
        }
        if (current.unreadCount() != candidate.unreadCount()) {
            return false;
        }
        UUID currentMessageId = current.lastMessage().map(Message::id).orElse(null);
        UUID candidateMessageId = candidate.lastMessage().map(Message::id).orElse(null);
        return Objects.equals(current.otherUser().getId(), candidate.otherUser().getId())
                && Objects.equals(currentMessageId, candidateMessageId);
    }

    private void startConversationsPolling() {
        stopConversationsPolling();
        conversationsPollingHandle = asyncScope.runPolling(
                "chat-conversations-polling",
                "poll conversations",
                conversationPollInterval,
                this::pollConversationsOnce);
    }

    private void stopConversationsPolling() {
        if (conversationsPollingHandle != null) {
            conversationsPollingHandle.cancel();
            conversationsPollingHandle = null;
        }
    }

    private void startMessagesPolling() {
        stopMessagesPolling();
        if (selectedConversation.get() == null) {
            return;
        }
        messagesPollingHandle = asyncScope.runPolling(
                "chat-messages-polling",
                "poll active conversation",
                activeConversationPollInterval,
                this::pollMessagesOnce);
    }

    private void stopMessagesPolling() {
        if (messagesPollingHandle != null) {
            messagesPollingHandle.cancel();
            messagesPollingHandle = null;
        }
    }

    private static boolean sameConversationPreviews(
            List<ConversationPreview> current, List<ConversationPreview> incoming) {
        if (current.size() != incoming.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            ConversationPreview existing = current.get(i);
            ConversationPreview candidate = incoming.get(i);
            if (!existing.conversation().getId().equals(candidate.conversation().getId())) {
                return false;
            }
            if (existing.unreadCount() != candidate.unreadCount()) {
                return false;
            }
            UUID existingMessageId = existing.lastMessage().map(Message::id).orElse(null);
            UUID candidateMessageId = candidate.lastMessage().map(Message::id).orElse(null);
            if (!Objects.equals(existingMessageId, candidateMessageId)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameMessages(List<Message> current, List<Message> incoming) {
        if (current.size() != incoming.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            Message existing = current.get(i);
            Message candidate = incoming.get(i);
            if (!existing.id().equals(candidate.id())
                    || !existing.content().equals(candidate.content())
                    || !existing.createdAt().equals(candidate.createdAt())) {
                return false;
            }
        }
        return true;
    }

    private static int computeUnreadCount(List<ConversationPreview> previews) {
        return previews.stream().mapToInt(ConversationPreview::unreadCount).sum();
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

    public void requestFriendZoneForSelectedConversation() {
        runRelationshipActionForSelectedConversation(
                "request friend zone",
                targetUserId -> socialUseCases.requestFriendZone(new RelationshipCommand(
                        UserContext.ui(ensureCurrentUser().getId()), targetUserId)));
    }

    public void gracefulExitSelectedConversation() {
        runRelationshipActionForSelectedConversation(
                "graceful exit",
                targetUserId -> socialUseCases.gracefulExit(new RelationshipCommand(
                        UserContext.ui(ensureCurrentUser().getId()), targetUserId)));
    }

    public void unmatchSelectedConversation() {
        runRelationshipActionForSelectedConversation(
                "unmatch",
                targetUserId -> socialUseCases.unmatch(new RelationshipCommand(
                        UserContext.ui(ensureCurrentUser().getId()), targetUserId)));
    }

    private void runRelationshipActionForSelectedConversation(
            String actionName, java.util.function.Function<UUID, UseCaseResult<RelationshipTransitionOutcome>> action) {
        User user = ensureCurrentUser();
        ConversationPreview selected = selectedConversation.get();
        if (user == null || selected == null) {
            return;
        }
        UUID otherUserId = selected.otherUser().getId();
        asyncScope.runFireAndForget(actionName, () -> {
            try {
                var result = action.apply(otherUserId);
                if (!result.success()) {
                    reportActionError(actionName + " failed: " + result.error().message(), null);
                    return;
                }
                asyncScope.dispatchToUi(() -> {
                    selectedConversation.set(null);
                    refreshConversations();
                });
            } catch (Exception e) {
                logError("Failed to " + actionName, e);
                reportActionError("Failed to " + actionName, e);
            }
        });
    }

    private void reportActionError(String message, Exception error) {
        if (hasErrorSink()) {
            notifyError(message, error);
            return;
        }
        if (error != null) {
            logger.warn(message, error);
        } else {
            logger.warn(message);
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

    public ReadOnlyBooleanProperty sendingProperty() {
        return sending;
    }

    public IntegerProperty totalUnreadCountProperty() {
        return totalUnreadCount;
    }

    public StringProperty profileNoteContentProperty() {
        return profileNoteContent;
    }

    public StringProperty profileNoteStatusMessageProperty() {
        return profileNoteStatusMessage;
    }

    public BooleanProperty profileNoteBusyProperty() {
        return profileNoteBusy;
    }

    public ObjectProperty<PresenceStatus> presenceStatusProperty() {
        return presenceStatus;
    }

    public BooleanProperty remoteTypingProperty() {
        return remoteTyping;
    }

    public ReadOnlyBooleanProperty presenceSupportedProperty() {
        return presenceSupported;
    }

    public StringProperty presenceUnavailableMessageProperty() {
        return presenceUnavailableMessage;
    }
}
