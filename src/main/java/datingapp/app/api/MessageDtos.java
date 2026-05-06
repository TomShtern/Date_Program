package datingapp.app.api;

import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.model.Match;
import java.time.Instant;
import java.util.UUID;

final class MessageDtos {
    private MessageDtos() {}

    /** Conversation summary for API responses. */
    static record ConversationSummary(
            String id, UUID otherUserId, String otherUserName, int messageCount, Instant lastMessageAt) {}

    /** Message DTO for API responses. */
    static record MessageDto(UUID id, String conversationId, UUID senderId, String content, Instant sentAt) {
        static MessageDto from(Message message) {
            return new MessageDto(
                    message.id(), message.conversationId(), message.senderId(), message.content(), message.createdAt());
        }
    }

    /** Request body for sending a message. */
    static record SendMessageRequest(UUID senderId, String content) {}

    /** Request body for archiving a conversation. */
    static record ArchiveConversationRequest(Match.MatchArchiveReason reason) {}
}
