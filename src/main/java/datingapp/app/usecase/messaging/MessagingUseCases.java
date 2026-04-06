package datingapp.app.usecase.messaging;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionService;
import datingapp.core.connection.ConnectionService.ConversationPreview;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.profile.ValidationService;
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
                    connectionService.getConversations(query.context().userId(), limit, offset);
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
            case "Invalid limit", "Invalid offset" -> UseCaseError.validation(errorMessage);
            case "Conversation not found or unauthorized" -> UseCaseError.notFound(errorMessage);
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
                return UseCaseResult.failure(UseCaseError.conflict(result.errorMessage()));
            }
        } catch (Exception e) {
            return internalFailure("send message", e);
        }

        publishMessageSentEvent(command, result);
        return UseCaseResult.success(result);
    }

    private void publishMessageSentEvent(SendMessageCommand command, ConnectionService.SendResult result) {
        publishEvent(
                new AppEvent.MessageSent(
                        command.context().userId(),
                        command.recipientId(),
                        result.message().id(),
                        AppClock.now()),
                "Event publish failed for message " + result.message().id());
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
            return validationFailure(e.getMessage());
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
            publishEvent(
                    new AppEvent.ConversationArchived(
                            command.conversationId(), command.context().userId(), AppClock.now()),
                    "Event publish failed for archived conversation " + command.conversationId());
            return UseCaseResult.success(null);
        } catch (IllegalArgumentException e) {
            return validationFailure(e.getMessage());
        } catch (Exception e) {
            return internalFailure("archive conversation", e);
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
            return validationFailure(e.getMessage());
        } catch (Exception e) {
            return internalFailure("delete message", e);
        }
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
        return UseCaseResult.failure(UseCaseError.internal("Failed to " + action + ": " + error.getMessage()));
    }

    public static record ListConversationsQuery(UserContext context, int limit, int offset) {}

    public static record ConversationListResult(List<ConversationPreview> conversations, int totalUnreadCount) {}

    public static record OpenConversationCommand(UserContext context, UUID otherUserId) {}

    public static record OpenConversationResult(Conversation conversation, ConversationPreview preview) {}

    public static record LoadConversationQuery(
            UserContext context, UUID otherUserId, int limit, int offset, boolean markAsRead) {}

    public static record ConversationThread(List<Message> messages, boolean canMessage, String conversationId) {}

    public static record SendMessageCommand(UserContext context, UUID recipientId, String content) {}

    public static record CountMessagesByConversationIdsQuery(UserContext context, Set<String> conversationIds) {}

    public static record MarkConversationReadCommand(UserContext context, String conversationId) {}

    public static record DeleteConversationCommand(UserContext context, String conversationId) {}

    public static record ArchiveConversationCommand(
            UserContext context, String conversationId, MatchArchiveReason reason) {}

    public static record DeleteMessageCommand(UserContext context, String conversationId, UUID messageId) {}

    private void publishEvent(AppEvent event, String failureMessage) {
        try {
            eventBus.publish(event);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("{}: {}", failureMessage, e.getMessage(), e);
            }
        }
    }
}
