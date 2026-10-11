package datingapp.app.usecase.messaging;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.event.EventPublishing;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.Snapshots;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionService;
import datingapp.core.connection.ConnectionService.ConversationPreview;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.profile.ValidationService;
import datingapp.core.storage.CommunicationStorage.ArchiveFilter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Messaging application-use-case bundle shared by CLI, JavaFX, and REST adapters. */
public class MessagingUseCases {

    private static final Logger logger = LoggerFactory.getLogger(MessagingUseCases.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final String CONTEXT_REQUIRED = "Context is required";
    private static final String PRESENCE_FORBIDDEN = "Presence is only available for a current match";

    private final ConnectionService connectionService;
    private final ValidationService validationService;
    private final AppEventBus eventBus;

    public MessagingUseCases(ConnectionService connectionService, AppEventBus eventBus) {
        this(connectionService, null, eventBus);
    }

    public MessagingUseCases(
            ConnectionService connectionService, ValidationService validationService, AppEventBus eventBus) {
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService cannot be null");
        this.validationService = validationService;
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
    }

    public UseCaseResult<ConversationListResult> listConversations(ListConversationsQuery query) {
        if (query == null || query.context() == null) {
            return validationFailure(CONTEXT_REQUIRED);
        }
        int limit = normalizeLimit(query.limit());
        int offset = normalizeOffset(query.offset());
        try {
            List<ConversationPreview> previews =
                    connectionService.getConversations(query.context().userId(), query.archiveFilter(), limit, offset);
            int totalUnread =
                    previews.stream().mapToInt(ConversationPreview::unreadCount).sum();
            return UseCaseResult.success(new ConversationListResult(previews, totalUnread));
        } catch (Exception e) {
            return internalFailure("list conversations", e);
        }
    }

    public UseCaseResult<OpenConversationResult> openConversation(OpenConversationCommand command) {
        if (command == null || command.context() == null || command.otherUserId() == null) {
            return validationFailure("Context and target user are required");
        }
        try {
            Conversation conversation =
                    connectionService.getOrCreateConversation(command.context().userId(), command.otherUserId());
            return connectionService
                    .getConversationPreview(command.context().userId(), conversation.getId())
                    .map(preview -> UseCaseResult.success(new OpenConversationResult(conversation, preview)))
                    .orElseGet(() -> UseCaseResult.failure(UseCaseError.notFound("Conversation preview not found")));
        } catch (Exception e) {
            return internalFailure("open conversation", e);
        }
    }

    public UseCaseResult<ConversationThread> loadConversation(LoadConversationQuery query) {
        if (query == null || query.context() == null || query.otherUserId() == null) {
            return validationFailure("Context and target user are required");
        }
        int limit = normalizeLimit(query.limit());
        int offset = normalizeOffset(query.offset());
        try {
            var result = connectionService.getMessages(query.context().userId(), query.otherUserId(), limit, offset);
            if (!result.success()) {
                return UseCaseResult.failure(mapConversationLoadFailure(result.errorMessage()));
            }

            String conversationId = Conversation.generateId(query.context().userId(), query.otherUserId());
            if (query.markAsRead()) {
                markConversationAsReadBestEffort(query.context().userId(), conversationId);
            }
            boolean canMessage = connectionService.canMessage(query.context().userId(), query.otherUserId());

            return UseCaseResult.success(new ConversationThread(result.messages(), canMessage, conversationId));
        } catch (Exception e) {
            return internalFailure("load conversation", e);
        }
    }

    private void markConversationAsReadBestEffort(UUID userId, String conversationId) {
        try {
            connectionService.markAsRead(userId, conversationId);
        } catch (Exception markReadError) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "markAsRead failed for conversation {} and user {}: {}",
                        conversationId,
                        userId,
                        markReadError.getMessage());
            }
        }
    }

    private UseCaseError mapConversationLoadFailure(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return UseCaseError.internal("Failed to load conversation");
        }

        return switch (errorMessage) {
            case "Invalid limit", "Invalid offset" -> UseCaseError.validation("Invalid conversation page.");
            case "Conversation not found or unauthorized" ->
                UseCaseError.notFound("Conversation not found or unauthorized.");
            default -> UseCaseError.conflict(errorMessage);
        };
    }

    public UseCaseResult<ConnectionService.SendResult> sendMessage(SendMessageCommand command) {
        if (command == null || command.context() == null || command.recipientId() == null) {
            return validationFailure("Context and recipient are required");
        }
        if (validationService != null) {
            var validationResult = validationService.validateMessageContent(command.content());
            if (!validationResult.valid()) {
                return validationFailure(validationResult.errors().getFirst());
            }
        } else if (command.content() == null || command.content().isBlank()) {
            return validationFailure("Message content cannot be empty");
        }
        ConnectionService.SendResult result;
        try {
            result =
                    connectionService.sendMessage(command.context().userId(), command.recipientId(), command.content());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(stableSendMessage(result.errorMessage())));
            }
        } catch (Exception e) {
            return internalFailure("send message", e);
        }

        publishMessageSentEvent(command, result);
        return UseCaseResult.success(result);
    }

    /**
     * Maps message-send internals to a stable user-facing message. Raw storage/match
     * details stay server-side; the API surface only exposes whether messaging is
     * unavailable, blocked, or the content was invalid.
     */
    public static String stableSendMessage(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Cannot send message.";
        }
        String lower = detail.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("block")) {
            return "Cannot message: user is blocked.";
        }
        if (lower.contains("no active match") || lower.contains("no longer messageable") || lower.contains("match")) {
            return "Cannot message: no active match.";
        }
        if (lower.contains("inactive") || lower.contains("not found")) {
            return "Message recipient is not available.";
        }
        if (lower.contains("empty") || lower.contains("too long") || lower.contains("content")) {
            return "Invalid message content.";
        }
        return "Cannot send message.";
    }

    /**
     * Publishes the message-sent event on a best-effort basis after persistence succeeds.
     * Persistence is the success boundary; event publication must not retroactively fail the send.
     */
    private void publishMessageSentEvent(SendMessageCommand command, ConnectionService.SendResult result) {
        EventPublishing.publishOrWarn(
                eventBus,
                new AppEvent.MessageSent(
                        command.context().userId(),
                        command.recipientId(),
                        result.message().id(),
                        AppClock.now()),
                "Event publish failed for message " + result.message().id(),
                logger);
    }

    public UseCaseResult<Void> markConversationRead(MarkConversationReadCommand command) {
        if (command == null || command.context() == null || command.conversationId() == null) {
            return validationFailure("Context and conversationId are required");
        }
        try {
            connectionService.markAsRead(command.context().userId(), command.conversationId());
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return internalFailure("mark conversation as read", e);
        }
    }

    public UseCaseResult<Integer> totalUnreadCount(UserContext context) {
        if (context == null) {
            return validationFailure(CONTEXT_REQUIRED);
        }
        try {
            return UseCaseResult.success(connectionService.getTotalUnreadCount(context.userId()));
        } catch (Exception e) {
            return internalFailure("compute unread count", e);
        }
    }

    public UseCaseResult<Map<String, Integer>> countMessagesByConversationIds(
            CountMessagesByConversationIdsQuery query) {
        if (query == null || query.context() == null) {
            return validationFailure(CONTEXT_REQUIRED);
        }
        if (query.conversationIds() == null) {
            return validationFailure("conversationIds are required");
        }
        try {
            return UseCaseResult.success(connectionService.countMessagesByConversationIds(query.conversationIds()));
        } catch (Exception e) {
            return internalFailure("count messages by conversation IDs", e);
        }
    }

    public UseCaseResult<Void> deleteConversation(DeleteConversationCommand command) {
        if (command == null || command.context() == null || command.conversationId() == null) {
            return validationFailure("Context and conversationId are required");
        }
        try {
            connectionService.deleteConversation(command.context().userId(), command.conversationId());
            return UseCaseResult.success(null);
        } catch (IllegalArgumentException e) {
            return validationFailure(stableConversationMessage(e.getMessage()));
        } catch (Exception e) {
            return internalFailure("delete conversation", e);
        }
    }

    public UseCaseResult<Void> archiveConversation(ArchiveConversationCommand command) {
        if (command == null
                || command.context() == null
                || command.conversationId() == null
                || command.reason() == null) {
            return validationFailure("Context, conversationId and reason are required");
        }
        try {
            connectionService.archiveConversation(
                    command.conversationId(), command.context().userId(), command.reason());
            EventPublishing.publishOrWarn(
                    eventBus,
                    new AppEvent.ConversationArchived(
                            command.conversationId(), command.context().userId(), AppClock.now()),
                    "Event publish failed for archived conversation " + command.conversationId(),
                    logger);
            return UseCaseResult.success(null);
        } catch (IllegalArgumentException e) {
            return validationFailure(stableConversationMessage(e.getMessage()));
        } catch (Exception e) {
            return internalFailure("archive conversation", e);
        }
    }

    public UseCaseResult<Void> unarchiveConversation(UnarchiveConversationCommand command) {
        if (command == null || command.context() == null || command.conversationId() == null) {
            return validationFailure("Context and conversationId are required");
        }
        try {
            connectionService.unarchiveConversation(
                    command.conversationId(), command.context().userId());
            return UseCaseResult.success(null);
        } catch (IllegalArgumentException e) {
            return validationFailure(stableConversationMessage(e.getMessage()));
        } catch (Exception e) {
            return internalFailure("unarchive conversation", e);
        }
    }

    /**
     * Decides whether the caller may read another user's presence: only while the pair can currently
     * message each other (live match, neither blocked the other). Reading your own presence is always
     * allowed. A supplied conversation id must be the conversation of exactly these two users.
     */
    public UseCaseResult<Void> authorizePresenceRead(PresenceReadQuery query) {
        if (query == null || query.context() == null || query.targetUserId() == null) {
            return validationFailure("Context and target user are required");
        }
        UUID viewerId = query.context().userId();
        UUID targetUserId = query.targetUserId();
        try {
            if (viewerId.equals(targetUserId)) {
                return UseCaseResult.success(null);
            }
            if (query.conversationId() != null
                    && !query.conversationId().equals(Conversation.generateId(viewerId, targetUserId))) {
                return UseCaseResult.failure(UseCaseError.forbidden(PRESENCE_FORBIDDEN));
            }
            if (!connectionService.canMessage(viewerId, targetUserId)) {
                return UseCaseResult.failure(UseCaseError.forbidden(PRESENCE_FORBIDDEN));
            }
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return internalFailure("authorize presence read", e);
        }
    }

    public UseCaseResult<Void> deleteMessage(DeleteMessageCommand command) {
        if (command == null
                || command.context() == null
                || command.conversationId() == null
                || command.messageId() == null) {
            return validationFailure("Context, conversationId and messageId are required");
        }
        try {
            connectionService.deleteMessage(command.context().userId(), command.conversationId(), command.messageId());
            return UseCaseResult.success(null);
        } catch (IllegalArgumentException e) {
            return validationFailure(stableConversationMessage(e.getMessage()));
        } catch (Exception e) {
            return internalFailure("delete message", e);
        }
    }

    /**
     * Maps conversation/message internals to a stable user-facing message. Raw storage
     * details stay server-side; the API surface only exposes whether the target was
     * missing/unauthorized or the request was malformed.
     */
    public static String stableConversationMessage(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Invalid conversation request.";
        }
        String lower = detail.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("not found") || lower.contains("unauthorized") || lower.contains("forbidden")) {
            return "Conversation not found or unauthorized.";
        }
        if (lower.contains("not belong")
                || lower.contains("not involv")
                || lower.contains("not participant")
                || lower.contains("not member")) {
            return "Conversation does not belong to current user.";
        }
        if (lower.contains("message") && (lower.contains("not found") || lower.contains("missing"))) {
            return "Message not found.";
        }
        return "Invalid conversation request.";
    }

    private static int normalizeLimit(int limit) {
        return limit > 0 ? limit : DEFAULT_LIMIT;
    }

    private static int normalizeOffset(int offset) {
        return Math.max(0, offset);
    }

    private static <T> UseCaseResult<T> validationFailure(String message) {
        return UseCaseResult.failure(UseCaseError.validation(message));
    }

    private static <T> UseCaseResult<T> internalFailure(String action, Exception error) {
        return UseCaseResult.failure(UseCaseError.internalFailure(action, error));
    }

    public static record ListConversationsQuery(
            UserContext context, int limit, int offset, ArchiveFilter archiveFilter) {
        public ListConversationsQuery {
            archiveFilter = archiveFilter == null ? ArchiveFilter.ALL : archiveFilter;
        }

        public ListConversationsQuery(UserContext context, int limit, int offset) {
            this(context, limit, offset, ArchiveFilter.ALL);
        }
    }

    public static record ConversationListResult(List<ConversationPreview> conversations, int totalUnreadCount) {
        public ConversationListResult {
            conversations = Snapshots.listOf(conversations);
        }
    }

    public static record OpenConversationCommand(UserContext context, UUID otherUserId) {}

    public static record OpenConversationResult(Conversation conversation, ConversationPreview preview) {}

    public static record LoadConversationQuery(
            UserContext context, UUID otherUserId, int limit, int offset, boolean markAsRead) {}

    public static record ConversationThread(List<Message> messages, boolean canMessage, String conversationId) {
        public ConversationThread {
            messages = Snapshots.listOf(messages);
        }
    }

    public static record SendMessageCommand(UserContext context, UUID recipientId, String content) {}

    public static record CountMessagesByConversationIdsQuery(UserContext context, Set<String> conversationIds) {
        public CountMessagesByConversationIdsQuery {
            conversationIds = Snapshots.setOf(conversationIds);
        }
    }

    public static record MarkConversationReadCommand(UserContext context, String conversationId) {}

    public static record DeleteConversationCommand(UserContext context, String conversationId) {}

    public static record ArchiveConversationCommand(
            UserContext context, String conversationId, MatchArchiveReason reason) {}

    public static record UnarchiveConversationCommand(UserContext context, String conversationId) {}

    /** {@code conversationId} is optional; when present it must be the pair's own conversation. */
    public static record PresenceReadQuery(UserContext context, UUID targetUserId, String conversationId) {}

    public static record DeleteMessageCommand(UserContext context, String conversationId, UUID messageId) {}
}
