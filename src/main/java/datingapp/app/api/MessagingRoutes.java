package datingapp.app.api;

import datingapp.core.Messaging.Conversation;
import datingapp.core.Messaging.Message;
import datingapp.core.MessagingService;
import datingapp.core.ServiceRegistry;
import datingapp.core.User;
import datingapp.core.storage.MessagingStorage;
import datingapp.core.storage.UserStorage;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST API routes for messaging operations.
 *
 * <p>Handles listing conversations, getting messages, and sending messages.
 */
public class MessagingRoutes {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;

    private final UserStorage userStorage;
    private final MessagingStorage messagingStorage;
    private final MessagingService messagingService;

    public MessagingRoutes(ServiceRegistry services) {
        Objects.requireNonNull(services, "services cannot be null");
        this.userStorage = services.getUserStorage();
        this.messagingStorage = services.getMessagingStorage();
        this.messagingService = services.getMessagingService();
    }

    /** GET /api/users/{id}/conversations - Get all conversations for a user. */
    public void getConversations(Context ctx) {
        UUID userId = parseUuid(ctx.pathParam("id"));
        validateUserExists(userId);

        List<ConversationSummary> conversations = messagingStorage.getConversationsFor(userId).stream()
                .map(c -> toSummary(c, userId))
                .toList();
        ctx.json(conversations);
    }

    /** GET /api/conversations/{conversationId}/messages - Get all messages in a conversation. */
    public void getMessages(Context ctx) {
        String conversationId = ctx.pathParam("conversationId");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(DEFAULT_MESSAGE_LIMIT);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);

        List<MessageDto> messages = messagingStorage.getMessages(conversationId, limit, offset).stream()
                .map(MessageDto::from)
                .toList();
        ctx.json(messages);
    }

    /** POST /api/conversations/{conversationId}/messages - Send a message. */
    public void sendMessage(Context ctx) {
        String conversationId = ctx.pathParam("conversationId");
        SendMessageRequest request = ctx.bodyAsClass(SendMessageRequest.class);

        if (request.senderId() == null || request.content() == null) {
            throw new IllegalArgumentException("senderId and content are required");
        }

        UUID recipientId = extractRecipientFromConversation(conversationId, request.senderId());

        var result = messagingService.sendMessage(request.senderId(), recipientId, request.content());

        if (result.success()) {
            ctx.status(201);
            ctx.json(MessageDto.from(result.message()));
        } else {
            ctx.status(400);
            ctx.json(new RestApiServer.ErrorResponse(result.errorCode().name(), result.errorMessage()));
        }
    }

    private void validateUserExists(UUID userId) {
        if (userStorage.get(userId) == null) {
            throw new NotFoundResponse("User not found: " + userId);
        }
    }

    private UUID extractRecipientFromConversation(String conversationId, UUID senderId) {
        // Conversation ID format is "uuid1_uuid2" where uuid1 < uuid2 lexicographically
        String[] parts = conversationId.split("_");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid conversation ID format");
        }
        try {
            UUID id1 = UUID.fromString(parts[0]);
            UUID id2 = UUID.fromString(parts[1]);
            return id1.equals(senderId) ? id2 : id1;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid conversation ID format", ex);
        }
    }

    private ConversationSummary toSummary(Conversation conversation, UUID currentUserId) {
        UUID otherUserId = extractRecipientFromConversation(conversation.getId(), currentUserId);
        User otherUser = userStorage.get(otherUserId);
        String otherUserName = otherUser != null ? otherUser.getName() : "Unknown";
        int messageCount = messagingStorage.countMessages(conversation.getId());
        return new ConversationSummary(
                conversation.getId(), otherUserId, otherUserName, messageCount, conversation.getLastMessageAt());
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid UUID format: " + value, ex);
        }
    }

    /** Conversation summary for API responses. */
    public record ConversationSummary(
            String id, UUID otherUserId, String otherUserName, int messageCount, Instant lastMessageAt) {}

    /** Message DTO for API responses. */
    public record MessageDto(UUID id, String conversationId, UUID senderId, String content, Instant sentAt) {
        public static MessageDto from(Message message) {
            return new MessageDto(
                    message.id(), message.conversationId(), message.senderId(), message.content(), message.createdAt());
        }
    }

    /** Request body for sending a message. */
    public record SendMessageRequest(UUID senderId, String content) {}
}
