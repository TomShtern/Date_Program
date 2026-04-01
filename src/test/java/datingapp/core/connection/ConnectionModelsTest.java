package datingapp.core.connection;

import static org.junit.jupiter.api.Assertions.assertThrows;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.FriendRequest.Status;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConnectionModels.FriendRequest")
class ConnectionModelsTest {

    @Test
    @DisplayName("conversation generateId rejects null participants explicitly")
    void conversationGenerateIdRejectsNullParticipantsExplicitly() {
        UUID userId = UUID.randomUUID();

        assertThrows(NullPointerException.class, () -> generateConversationId(null, userId));
        assertThrows(NullPointerException.class, () -> generateConversationId(userId, null));
    }

    @Test
    @DisplayName("conversation generateId rejects self conversations explicitly")
    void conversationGenerateIdRejectsSelfConversationsExplicitly() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> generateConversationId(userId, userId));
    }

    @Test
    @DisplayName("rejects ACCEPTED requests without respondedAt")
    void rejectsAcceptedWithoutRespondedAt() {
        assertThrows(IllegalArgumentException.class, () -> createFriendRequest(Status.ACCEPTED, null));
    }

    @Test
    @DisplayName("rejects DECLINED requests without respondedAt")
    void rejectsDeclinedWithoutRespondedAt() {
        assertThrows(IllegalArgumentException.class, () -> createFriendRequest(Status.DECLINED, null));
    }

    @Test
    @DisplayName("rejects EXPIRED requests without respondedAt")
    void rejectsExpiredWithoutRespondedAt() {
        assertThrows(IllegalArgumentException.class, () -> createFriendRequest(Status.EXPIRED, null));
    }

    private static String generateConversationId(UUID firstUserId, UUID secondUserId) {
        return Conversation.generateId(firstUserId, secondUserId);
    }

    private static FriendRequest createFriendRequest(Status status, Instant respondedAt) {
        return new FriendRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), status, respondedAt);
    }
}
