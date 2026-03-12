package datingapp.app.usecase.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.messaging.MessagingUseCases.ArchiveConversationCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.DeleteConversationCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.DeleteMessageCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.ListConversationsQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.SendMessageCommand;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
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
    private TestStorages.Communications communicationStorage;
    private MessagingUseCases useCases;
    private User sender;
    private User recipient;

    @BeforeEach
    void setUp() {
        var config = AppConfig.defaults();
        userStorage = new TestStorages.Users();
        communicationStorage = new TestStorages.Communications();
        interactionStorage = new TestStorages.Interactions(communicationStorage);

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
    @DisplayName("sendMessage should sanitize HTML payloads before persisting")
    void sendMessageSanitizesHtmlPayloads() {
        var result = useCases.sendMessage(new SendMessageCommand(
                UserContext.cli(sender.getId()), recipient.getId(), "<script>alert('xss')</script>Hello"));

        assertTrue(result.success());
        assertEquals("Hello", result.data().message().content());
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

    @Test
    @DisplayName("deleteMessage removes sender-owned message")
    void deleteMessageRemovesSenderOwnedMessage() {
        var sendResult = useCases.sendMessage(
                new SendMessageCommand(UserContext.cli(sender.getId()), recipient.getId(), "Delete me"));
        assertTrue(sendResult.success());

        var message = sendResult.data().message();
        var deleteResult = useCases.deleteMessage(
                new DeleteMessageCommand(UserContext.cli(sender.getId()), message.conversationId(), message.id()));

        assertTrue(deleteResult.success());
        assertTrue(communicationStorage.getMessage(message.id()).isEmpty());
    }

    @Test
    @DisplayName("archiveConversation archives the conversation for the acting user")
    void archiveConversationArchivesConversationForActingUser() {
        var sendResult = useCases.sendMessage(
                new SendMessageCommand(UserContext.cli(sender.getId()), recipient.getId(), "Archive me"));
        assertTrue(sendResult.success());

        String conversationId = sendResult.data().message().conversationId();
        var archiveResult = useCases.archiveConversation(new ArchiveConversationCommand(
                UserContext.cli(sender.getId()), conversationId, MatchArchiveReason.UNMATCH));

        assertTrue(archiveResult.success());
        var conversation = communicationStorage.getConversation(conversationId).orElseThrow();
        MatchArchiveReason archiveReason = conversation.getUserA().equals(sender.getId())
                ? conversation.getUserAArchiveReason()
                : conversation.getUserBArchiveReason();
        assertEquals(MatchArchiveReason.UNMATCH, archiveReason);
    }

    @Test
    @DisplayName("deleteConversation removes conversation and messages")
    void deleteConversationRemovesConversationAndMessages() {
        var sendResult = useCases.sendMessage(
                new SendMessageCommand(UserContext.cli(sender.getId()), recipient.getId(), "Delete conversation"));
        assertTrue(sendResult.success());

        String conversationId = sendResult.data().message().conversationId();
        var deleteResult = useCases.deleteConversation(
                new DeleteConversationCommand(UserContext.cli(sender.getId()), conversationId));

        assertTrue(deleteResult.success());
        assertTrue(communicationStorage.getConversation(conversationId).isEmpty());
        assertTrue(communicationStorage.getMessages(conversationId, 50, 0).isEmpty());
    }
}
