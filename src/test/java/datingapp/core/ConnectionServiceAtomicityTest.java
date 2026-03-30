package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.Match;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
@DisplayName("ConnectionService atomic authorization and write behavior")
class ConnectionServiceAtomicityTest {

    @Test
    @DisplayName("sendMessage leaves no partial conversation state when the last-message write fails")
    void sendMessageLeavesNoPartialConversationStateWhenAtomicWriteFails() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        TestStorages.Users users = new TestStorages.Users();
        users.save(TestUserFactory.createActiveUser(senderId, "Sender"));
        users.save(TestUserFactory.createActiveUser(recipientId, "Recipient"));

        TestStorages.Interactions interactions = new TestStorages.Interactions();
        interactions.save(Match.create(senderId, recipientId));

        FailingMessageCommunications communications = new FailingMessageCommunications();
        ConnectionService service = new ConnectionService(AppConfig.defaults(), communications, interactions, users);

        assertThrows(RuntimeException.class, () -> service.sendMessage(senderId, recipientId, "Hello there"));

        String conversationId = Conversation.generateId(senderId, recipientId);
        assertTrue(communications.getConversation(conversationId).isEmpty());
        assertEquals(0, communications.countMessages(conversationId));
    }

    @Test
    @DisplayName("getOrCreateConversation rejects pairs without messaging eligibility")
    void getOrCreateConversationRejectsPairsWithoutMessagingEligibility() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        TestStorages.Users users = new TestStorages.Users();
        users.save(TestUserFactory.createActiveUser(userA, "Alice"));
        users.save(TestUserFactory.createActiveUser(userB, "Bob"));

        ConnectionService service = new ConnectionService(
                AppConfig.defaults(), new TestStorages.Communications(), new TestStorages.Interactions(), users);

        assertThrows(IllegalArgumentException.class, () -> service.getOrCreateConversation(userA, userB));
    }

    @Test
    @DisplayName("deleteConversation leaves no partial state when message deletion fails")
    void deleteConversationLeavesNoPartialStateWhenAtomicDeleteFails() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        TestStorages.Users users = new TestStorages.Users();
        users.save(TestUserFactory.createActiveUser(userA, "Alice"));
        users.save(TestUserFactory.createActiveUser(userB, "Bob"));

        TestStorages.Interactions interactions = new TestStorages.Interactions();
        interactions.save(Match.create(userA, userB));

        FailingDeleteCommunications communications = new FailingDeleteCommunications();
        ConnectionService service = new ConnectionService(AppConfig.defaults(), communications, interactions, users);

        service.sendMessage(userA, userB, "Message to delete");
        String conversationId = Conversation.generateId(userA, userB);

        assertThrows(RuntimeException.class, () -> service.deleteConversation(userA, conversationId));

        assertTrue(communications.getConversation(conversationId).isPresent());
        assertEquals(1, communications.countMessages(conversationId));
    }

    @Test
    @DisplayName("match factory uses the canonical deterministic ID")
    void matchFactoryUsesCanonicalDeterministicId() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000001");

        Match match = Match.create(first, second);

        assertEquals(Match.generateId(first, second), match.getId());
        assertEquals(second, match.getUserA());
        assertEquals(first, match.getUserB());
    }

    @Test
    @DisplayName("conversation factory uses the canonical deterministic ID")
    void conversationFactoryUsesCanonicalDeterministicId() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000001");

        Conversation conversation = Conversation.create(first, second);

        assertEquals(Conversation.generateId(first, second), conversation.getId());
        assertEquals(second, conversation.getUserA());
        assertEquals(first, conversation.getUserB());
    }

    private static final class FailingMessageCommunications extends TestStorages.Communications {
        @Override
        public void saveMessageAndUpdateConversationLastMessageAt(
                datingapp.core.connection.ConnectionModels.Message message) {
            throw new RuntimeException("simulated last-message update failure");
        }
    }

    private static final class FailingDeleteCommunications extends TestStorages.Communications {
        @Override
        public void deleteConversationWithMessages(String conversationId) {
            throw new RuntimeException("simulated conversation delete failure");
        }
    }
}
