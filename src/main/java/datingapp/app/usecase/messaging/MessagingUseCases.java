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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Messaging application-use-case bundle shared by CLI, JavaFX, and REST adapters. */
public class MessagingUseCases {

    private static final Logger logger = LoggerFactory.getLogger(MessagingUseCases.class);
    private static final int DEFAULT_LIMIT = 50;

    private final ConnectionService connectionService;
    private final AppEventBus eventBus;

    public MessagingUseCases(ConnectionService connectionService) {
        this(connectionService, null);
    }

    public MessagingUseCases(ConnectionService connectionService, AppEventBus eventBus) {
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService cannot be null");
        this.eventBus = eventBus;
    }

    public UseCaseResult<ConversationListResult> listConversations(ListConversationsQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context is required"));
        }
        int limit = query.limit() > 0 ? query.limit() : DEFAULT_LIMIT;
        int offset = Math.max(0, query.offset());
        try {
            List<ConversationPreview> previews =
                    connectionService.getConversations(query.context().userId(), limit, offset);
            int totalUnread =
                    previews.stream().mapToInt(ConversationPreview::unreadCount).sum();
            return UseCaseResult.success(new ConversationListResult(previews, totalUnread));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to list conversations: " + e.getMessage()));
        }
    }

    public UseCaseResult<OpenConversationResult> openConversation(OpenConversationCommand command) {
        if (command == null || command.context() == null || command.otherUserId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and target user are required"));
        }
        int limit = command.listLimit() > 0 ? command.listLimit() : DEFAULT_LIMIT;
        int offset = Math.max(0, command.listOffset());
        try {
            Conversation conversation =
                    connectionService.getOrCreateConversation(command.context().userId(), command.otherUserId());
            List<ConversationPreview> previews =
                    connectionService.getConversations(command.context().userId(), limit, offset);
            ConversationPreview preview = previews.stream()
                    .filter(item -> item.conversation().getId().equals(conversation.getId()))
                    .findFirst()
                    .orElse(null);
            if (preview == null) {
                return UseCaseResult.failure(UseCaseError.notFound("Conversation preview not found"));
            }
            return UseCaseResult.success(new OpenConversationResult(conversation, preview));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to open conversation: " + e.getMessage()));
        }
    }

    public UseCaseResult<ConversationThread> loadConversation(LoadConversationQuery query) {
        if (query == null || query.context() == null || query.otherUserId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and target user are required"));
        }
        int limit = query.limit() > 0 ? query.limit() : DEFAULT_LIMIT;
        int offset = Math.max(0, query.offset());
        try {
            var result = connectionService.getMessages(query.context().userId(), query.otherUserId(), limit, offset);
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(result.errorMessage()));
            }

            String conversationId = Conversation.generateId(query.context().userId(), query.otherUserId());
            if (query.markAsRead()) {
                connectionService.markAsRead(query.context().userId(), conversationId);
            }
            boolean canMessage = connectionService.canMessage(query.context().userId(), query.otherUserId());

            return UseCaseResult.success(new ConversationThread(result.messages(), canMessage, conversationId));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load conversation: " + e.getMessage()));
        }
    }

    public UseCaseResult<ConnectionService.SendResult> sendMessage(SendMessageCommand command) {
        if (command == null || command.context() == null || command.recipientId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and recipient are required"));
        }
        if (command.content() == null || command.content().isBlank()) {
            return UseCaseResult.failure(UseCaseError.validation("Message content cannot be empty"));
        }
        try {
            ConnectionService.SendResult result =
                    connectionService.sendMessage(command.context().userId(), command.recipientId(), command.content());
            if (!result.success()) {
                return UseCaseResult.failure(UseCaseError.conflict(result.errorMessage()));
            }
            publishMessageSentEvent(command, result);
            return UseCaseResult.success(result);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to send message: " + e.getMessage()));
        }
    }

    private void publishMessageSentEvent(SendMessageCommand command, ConnectionService.SendResult result) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(new AppEvent.MessageSent(
                    command.context().userId(),
                    command.recipientId(),
                    result.message().id(),
                    AppClock.now()));
        } catch (Exception publishEx) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Event publish failed for message {} from {} to {}: {}",
                        result.message().id(),
                        command.context().userId(),
                        command.recipientId(),
                        publishEx.getMessage());
            }
        }
    }

    public UseCaseResult<Void> markConversationRead(MarkConversationReadCommand command) {
        if (command == null || command.context() == null || command.conversationId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and conversationId are required"));
        }
        try {
            connectionService.markAsRead(command.context().userId(), command.conversationId());
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to mark conversation as read: " + e.getMessage()));
        }
    }

    public UseCaseResult<Integer> totalUnreadCount(UserContext context) {
        if (context == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context is required"));
        }
        try {
            return UseCaseResult.success(connectionService.getTotalUnreadCount(context.userId()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to compute unread count: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> deleteConversation(DeleteConversationCommand command) {
        if (command == null || command.context() == null || command.conversationId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and conversationId are required"));
        }
        try {
            connectionService.deleteConversation(command.context().userId(), command.conversationId());
            return UseCaseResult.success(null);
        } catch (IllegalArgumentException e) {
            return UseCaseResult.failure(UseCaseError.validation(e.getMessage()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to delete conversation: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> archiveConversation(ArchiveConversationCommand command) {
        if (command == null
                || command.context() == null
                || command.conversationId() == null
                || command.reason() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context, conversationId and reason are required"));
        }
        try {
            connectionService.archiveConversation(
                    command.conversationId(), command.context().userId(), command.reason());
            return UseCaseResult.success(null);
        } catch (IllegalArgumentException e) {
            return UseCaseResult.failure(UseCaseError.validation(e.getMessage()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to archive conversation: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> deleteMessage(DeleteMessageCommand command) {
        if (command == null
                || command.context() == null
                || command.conversationId() == null
                || command.messageId() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context, conversationId and messageId are required"));
        }
        try {
            connectionService.deleteMessage(command.context().userId(), command.conversationId(), command.messageId());
            return UseCaseResult.success(null);
        } catch (IllegalArgumentException e) {
            return UseCaseResult.failure(UseCaseError.validation(e.getMessage()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to delete message: " + e.getMessage()));
        }
    }

    public static record ListConversationsQuery(UserContext context, int limit, int offset) {}

    public static record ConversationListResult(List<ConversationPreview> conversations, int totalUnreadCount) {}

    public static record OpenConversationCommand(
            UserContext context, UUID otherUserId, int listLimit, int listOffset) {}

    public static record OpenConversationResult(Conversation conversation, ConversationPreview preview) {}

    public static record LoadConversationQuery(
            UserContext context, UUID otherUserId, int limit, int offset, boolean markAsRead) {}

    public static record ConversationThread(List<Message> messages, boolean canMessage, String conversationId) {}

    public static record SendMessageCommand(UserContext context, UUID recipientId, String content) {}

    public static record MarkConversationReadCommand(UserContext context, String conversationId) {}

    public static record DeleteConversationCommand(UserContext context, String conversationId) {}

    public static record ArchiveConversationCommand(
            UserContext context, String conversationId, MatchArchiveReason reason) {}

    public static record DeleteMessageCommand(UserContext context, String conversationId, UUID messageId) {}
}
