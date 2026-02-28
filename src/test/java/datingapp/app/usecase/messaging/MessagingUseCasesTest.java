package datingapp.app.usecase.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.messaging.MessagingUseCases.ListConversationsQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.SendMessageCommand;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MessagingUseCases")
class MessagingUseCasesTest {

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private MessagingUseCases useCases;
    private User sender;
    private User recipient;

    @BeforeEach
    void setUp() {
        var config = AppConfig.defaults();
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        var communicationStorage = new TestStorages.Communications();

        sender = TestUserFactory.createActiveUser(UUID.randomUUID(), "Sender");
        recipient = TestUserFactory.createActiveUser(UUID.randomUUID(), "Recipient");
        recipient.setGender(User.Gender.FEMALE);
        recipient.setInterestedIn(java.util.Set.of(User.Gender.MALE));

        userStorage.save(sender);
        userStorage.save(recipient);
        interactionStorage.save(Match.create(sender.getId(), recipient.getId()));

        ConnectionService connectionService =
                new ConnectionService(config, communicationStorage, interactionStorage, userStorage);
        useCases = new MessagingUseCases(connectionService);
    }

    @Test
    @DisplayName("sendMessage should succeed for active match")
    void sendMessageSucceedsWhenActiveMatchExists() {
        var result = useCases.sendMessage(
                new SendMessageCommand(UserContext.cli(sender.getId()), recipient.getId(), "Hello there"));

        assertTrue(result.success());
        assertNotNull(result.data());
        assertTrue(result.data().success());
        assertNotNull(result.data().message());
    }

    @Test
    @DisplayName("sendMessage should fail for blank content")
    void sendMessageFailsForBlankContent() {
        var result =
                useCases.sendMessage(new SendMessageCommand(UserContext.cli(sender.getId()), recipient.getId(), "  "));

        assertFalse(result.success());
        assertNotNull(result.error());
        assertEquals("Message content cannot be empty", result.error().message());
    }

    @Test
    @DisplayName("listConversations returns unread totals")
    void listConversationsIncludesUnreadTotal() {
        useCases.sendMessage(
                new SendMessageCommand(UserContext.cli(sender.getId()), recipient.getId(), "Unread message"));

        var result = useCases.listConversations(new ListConversationsQuery(UserContext.cli(recipient.getId()), 50, 0));

        assertTrue(result.success());
        assertFalse(result.data().conversations().isEmpty());
        assertEquals(1, result.data().totalUnreadCount());
    }
}
