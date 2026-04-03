package datingapp.core.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionService.MessageLoadResult;
import datingapp.core.connection.ConnectionService.SendResult;
import datingapp.core.connection.ConnectionService.TransitionResult;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.User;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConnectionService")
class ConnectionServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-03T00:00:00Z");

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.Communications communicationStorage;
    private ConnectionService service;
    private User sender;
    private User recipient;
    private User outsider;

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_NOW);

        userStorage = new TestStorages.Users();
        communicationStorage = new TestStorages.Communications();
        interactionStorage = new TestStorages.Interactions(communicationStorage);

        sender = TestUserFactory.createActiveUser(UUID.randomUUID(), "Sender");
        recipient = TestUserFactory.createActiveUser(UUID.randomUUID(), "Recipient");
        outsider = TestUserFactory.createActiveUser(UUID.randomUUID(), "Outsider");

        userStorage.save(sender);
        userStorage.save(recipient);
        userStorage.save(outsider);
        interactionStorage.save(Match.create(sender.getId(), recipient.getId()));

        service = new ConnectionService(AppConfig.defaults(), communicationStorage, interactionStorage, userStorage);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("getMessages(UUID, UUID, limit, offset) rejects invalid paging and unauthorized access")
    void getMessagesByUserPairRejectsInvalidPagingAndUnauthorizedAccess() {
        SendResult first = service.sendMessage(sender.getId(), recipient.getId(), "Hello");
        assertTrue(first.success());

        MessageLoadResult invalidLimit = service.getMessages(sender.getId(), recipient.getId(), 0, 0);
        MessageLoadResult invalidOffset = service.getMessages(sender.getId(), recipient.getId(), 1, -1);
        MessageLoadResult unauthorized = service.getMessages(outsider.getId(), recipient.getId(), 1, 0);

        assertFalse(invalidLimit.success());
        assertEquals("Invalid limit", invalidLimit.errorMessage());
        assertFalse(invalidOffset.success());
        assertEquals("Invalid offset", invalidOffset.errorMessage());
        assertFalse(unauthorized.success());
        assertEquals("Conversation not found or unauthorized", unauthorized.errorMessage());
    }

    @Test
    @DisplayName("getMessages(String, limit, offset) rejects invalid paging")
    void getMessagesByConversationIdRejectsInvalidPaging() {
        service.sendMessage(sender.getId(), recipient.getId(), "First message");
        String conversationId = Conversation.generateId(sender.getId(), recipient.getId());

        MessageLoadResult invalidLimit = service.getMessages(conversationId, 0, 0);
        MessageLoadResult invalidOffset = service.getMessages(conversationId, 1, -1);

        assertFalse(invalidLimit.success());
        assertEquals("Invalid limit", invalidLimit.errorMessage());
        assertFalse(invalidOffset.success());
        assertEquals("Invalid offset", invalidOffset.errorMessage());
    }

    @Test
    @DisplayName("markAsRead clears unread messages and only counts messages from the other user")
    void markAsReadClearsUnreadMessagesAndTracksUnreadSemantics() {
        service.sendMessage(sender.getId(), recipient.getId(), "One");
        service.sendMessage(recipient.getId(), sender.getId(), "Two");
        service.sendMessage(sender.getId(), recipient.getId(), "Three");
        String conversationId = Conversation.generateId(sender.getId(), recipient.getId());

        assertEquals(2, service.getUnreadCount(recipient.getId(), conversationId));

        service.markAsRead(recipient.getId(), conversationId);

        assertEquals(0, service.getUnreadCount(recipient.getId(), conversationId));

        TestClock.setFixed(FIXED_NOW.plusSeconds(1));
        service.sendMessage(sender.getId(), recipient.getId(), "Four");

        assertEquals(1, service.getUnreadCount(recipient.getId(), conversationId));
    }

    @Test
    @DisplayName("archiveConversation and deleteConversation reject unauthorized users")
    void archiveAndDeleteConversationRejectUnauthorizedUsers() {
        service.sendMessage(sender.getId(), recipient.getId(), "Hello");
        String conversationId = Conversation.generateId(sender.getId(), recipient.getId());

        IllegalArgumentException archiveError =
                assertThrows(IllegalArgumentException.class, () -> archiveConversationAsOutsider(conversationId));
        IllegalArgumentException deleteError =
                assertThrows(IllegalArgumentException.class, () -> deleteConversationAsOutsider(conversationId));

        assertEquals("Conversation not found or unauthorized", archiveError.getMessage());
        assertEquals("Conversation not found or unauthorized", deleteError.getMessage());
    }

    @Test
    @DisplayName("gracefulExit fails when atomic relationship transitions are unsupported")
    void gracefulExitFailsWhenAtomicRelationshipTransitionsAreUnsupported() {
        TestStorages.Interactions nonAtomicInteractions = new TestStorages.Interactions(communicationStorage) {
            @Override
            public boolean supportsAtomicRelationshipTransitions() {
                return false;
            }
        };
        nonAtomicInteractions.save(Match.create(sender.getId(), recipient.getId()));

        ConnectionService nonAtomicService =
                new ConnectionService(AppConfig.defaults(), communicationStorage, nonAtomicInteractions, userStorage);

        TransitionResult result = nonAtomicService.gracefulExit(sender.getId(), recipient.getId());

        assertFalse(result.success());
        assertEquals("Relationship transition requires atomic storage support", result.errorMessage());
        assertNull(result.friendRequest());
    }

    private void archiveConversationAsOutsider(String conversationId) {
        service.archiveConversation(conversationId, outsider.getId(), MatchArchiveReason.BLOCK);
    }

    private void deleteConversationAsOutsider(String conversationId) {
        service.deleteConversation(outsider.getId(), conversationId);
    }
}
