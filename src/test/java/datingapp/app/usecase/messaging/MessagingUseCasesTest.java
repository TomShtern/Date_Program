package datingapp.app.usecase.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.testutil.TestEventBus;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.messaging.MessagingUseCases.ArchiveConversationCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.CountMessagesByConversationIdsQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.DeleteConversationCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.DeleteMessageCommand;
import datingapp.app.usecase.messaging.MessagingUseCases.ListConversationsQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.LoadConversationQuery;
import datingapp.app.usecase.messaging.MessagingUseCases.SendMessageCommand;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.User;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.util.ArrayList;
import java.util.List;
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
        useCases = new MessagingUseCases(connectionService, new TestEventBus());
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
    @DisplayName("openConversation resolves the target conversation even when it is outside the requested preview page")
    void openConversationResolvesTargetConversationOutsideRequestedPreviewPage() {
        var targetSend = useCases.sendMessage(
                new SendMessageCommand(UserContext.cli(sender.getId()), recipient.getId(), "Target conversation"));
        assertTrue(targetSend.success());

        User otherRecipient = TestUserFactory.createActiveUser(UUID.randomUUID(), "Other Recipient");
        otherRecipient.setGender(User.Gender.FEMALE);
        otherRecipient.setInterestedIn(java.util.Set.of(User.Gender.MALE));
        userStorage.save(otherRecipient);
        interactionStorage.save(Match.create(sender.getId(), otherRecipient.getId()));

        var otherSend = useCases.sendMessage(
                new SendMessageCommand(UserContext.cli(sender.getId()), otherRecipient.getId(), "More recent"));
        assertTrue(otherSend.success());

        var result = useCases.openConversation(
                new MessagingUseCases.OpenConversationCommand(UserContext.cli(sender.getId()), recipient.getId()));

        assertTrue(result.success());
        assertEquals(
                targetSend.data().message().conversationId(),
                result.data().conversation().getId());
        assertEquals(
                targetSend.data().message().conversationId(),
                result.data().preview().conversation().getId());
    }

    @Test
    @DisplayName("loadConversation maps a missing conversation to NOT_FOUND")
    void loadConversationMapsMissingConversationToNotFound() {
        var result = useCases.loadConversation(
                new LoadConversationQuery(UserContext.cli(sender.getId()), recipient.getId(), 50, 0, false));

        assertFalse(result.success());
        assertEquals(UseCaseError.Code.NOT_FOUND, result.error().code());
    }

    @Test
    @DisplayName("loadConversation maps oversized page requests to VALIDATION")
    void loadConversationMapsOversizedPageRequestsToValidation() {
        var result = useCases.loadConversation(new LoadConversationQuery(
                UserContext.cli(sender.getId()), recipient.getId(), Integer.MAX_VALUE, 0, false));

        assertFalse(result.success());
        assertEquals(UseCaseError.Code.VALIDATION, result.error().code());
    }

    @Test
    @DisplayName("countMessagesByConversationIds returns batch message counts")
    void countMessagesByConversationIdsReturnsBatchCounts() {
        var sendResult = useCases.sendMessage(
                new SendMessageCommand(UserContext.cli(sender.getId()), recipient.getId(), "Batch count"));
        assertTrue(sendResult.success());

        String conversationId = Conversation.generateId(sender.getId(), recipient.getId());
        var result = useCases.countMessagesByConversationIds(new CountMessagesByConversationIdsQuery(
                UserContext.cli(sender.getId()), java.util.Set.of(conversationId)));

        assertTrue(result.success());
        assertEquals(1, result.data().get(conversationId));
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
    @DisplayName("archiveConversation should publish ConversationArchived on success")
    void archiveConversationPublishesConversationArchivedEvent() {
        var sendResult = useCases.sendMessage(
                new SendMessageCommand(UserContext.cli(sender.getId()), recipient.getId(), "Archive me"));
        assertTrue(sendResult.success());

        String conversationId = sendResult.data().message().conversationId();
        List<AppEvent> publishedEvents = new ArrayList<>();
        MessagingUseCases eventUseCases = new MessagingUseCases(
                new ConnectionService(AppConfig.defaults(), communicationStorage, interactionStorage, userStorage),
                capturingEventBus(publishedEvents));

        var archiveResult = eventUseCases.archiveConversation(new ArchiveConversationCommand(
                UserContext.cli(sender.getId()), conversationId, MatchArchiveReason.UNMATCH));

        assertTrue(archiveResult.success());
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.getFirst() instanceof AppEvent.ConversationArchived);
        AppEvent.ConversationArchived event = (AppEvent.ConversationArchived) publishedEvents.getFirst();
        assertEquals(conversationId, event.conversationId());
        assertEquals(sender.getId(), event.archivedByUserId());
        assertNotNull(event.occurredAt());
    }

    @Test
    @DisplayName("constructor requires non-null event bus")
    void constructorRequiresNonNullEventBus() {
        ConnectionService connectionService =
                new ConnectionService(AppConfig.defaults(), communicationStorage, interactionStorage, userStorage);

        NullPointerException exception =
                assertThrows(NullPointerException.class, () -> new MessagingUseCases(connectionService, null));

        assertEquals("eventBus cannot be null", exception.getMessage());
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

    @Test
    @DisplayName("loadConversation should succeed even when markAsRead side effect fails")
    void loadConversationSucceedsWhenMarkAsReadFails() {
        var sendResult = useCases.sendMessage(
                new SendMessageCommand(UserContext.cli(sender.getId()), recipient.getId(), "Hello"));
        assertTrue(sendResult.success());

        ConnectionService flakyService =
                new ConnectionService(AppConfig.defaults(), communicationStorage, interactionStorage, userStorage) {
                    @Override
                    public void markAsRead(UUID userId, String conversationId) {
                        throw new IllegalStateException("simulated lock timeout");
                    }
                };
        MessagingUseCases flakyUseCases = new MessagingUseCases(flakyService, new TestEventBus());

        var loadResult = flakyUseCases.loadConversation(
                new LoadConversationQuery(UserContext.cli(recipient.getId()), sender.getId(), 50, 0, true));

        assertTrue(loadResult.success());
        assertEquals(1, loadResult.data().messages().size());
    }

    private static AppEventBus capturingEventBus(List<AppEvent> publishedEvents) {
        return new AppEventBus() {
            @Override
            public void publish(AppEvent event) {
                publishedEvents.add(event);
            }

            @Override
            public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler) {
                // Not needed for these tests.
            }

            @Override
            public <T extends AppEvent> void subscribe(
                    Class<T> eventType, AppEventHandler<T> handler, HandlerPolicy policy) {
                // Not needed for these tests.
            }
        };
    }
}
