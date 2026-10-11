package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionService;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.User;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.CommunicationStorage.ArchiveFilter;
import datingapp.core.testutil.TestUserFactory;
import datingapp.storage.DatabaseManager;
import datingapp.storage.StorageFactory;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Runs against real SQL because the in-memory test storage cannot reproduce the bug this guards: a
 * deleted conversation row still owns the pair's deterministic id, so a plain insert collides with it.
 */
@Timeout(20)
@DisplayName("Conversation delete, revival and archive against SQL storage")
class JdbiConversationVisibilityTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    private ServiceRegistry services;
    private ConnectionService connectionService;
    private CommunicationStorage communicationStorage;
    private User alice;
    private User bob;
    private String conversationId;

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
        DatabaseManager.resetInstance();
        services = StorageFactory.buildInMemory(AppConfig.defaults());
        connectionService = services.getConnectionService();
        communicationStorage = services.getCommunicationStorage();

        alice = TestUserFactory.createActiveUser(UUID.randomUUID(), "Alice");
        bob = TestUserFactory.createActiveUser(UUID.randomUUID(), "Bob");
        services.getUserStorage().save(alice);
        services.getUserStorage().save(bob);
        services.getInteractionStorage().save(Match.create(alice.getId(), bob.getId()));
        conversationId = Conversation.generateId(alice.getId(), bob.getId());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(PROFILE_PROPERTY);
        DatabaseManager.resetInstance();
    }

    @Test
    @DisplayName("delete hides the conversation for the caller only and keeps its messages")
    void deleteHidesForCallerOnly() {
        assertTrue(connectionService
                .sendMessage(alice.getId(), bob.getId(), "Hello Bob")
                .success());

        connectionService.deleteConversation(alice.getId(), conversationId);

        assertTrue(connectionService.getConversations(alice.getId(), 50, 0).isEmpty());
        assertEquals(1, connectionService.getConversations(bob.getId(), 50, 0).size());
        assertEquals(1, communicationStorage.countMessages(conversationId));
    }

    @Test
    @DisplayName("a message from the other side brings a deleted conversation back with its history")
    void messageFromOtherSideRevivesDeletedConversation() {
        assertTrue(connectionService
                .sendMessage(alice.getId(), bob.getId(), "Hello Bob")
                .success());
        connectionService.deleteConversation(alice.getId(), conversationId);

        assertTrue(connectionService
                .sendMessage(bob.getId(), alice.getId(), "Hi Alice")
                .success());

        assertEquals(1, connectionService.getConversations(alice.getId(), 50, 0).size());
        assertEquals(
                2,
                connectionService
                        .getMessages(alice.getId(), bob.getId(), 50, 0)
                        .messages()
                        .size());
    }

    @Test
    @DisplayName("the user who deleted can message again")
    void deleterCanMessageAgain() {
        assertTrue(connectionService
                .sendMessage(alice.getId(), bob.getId(), "Hello Bob")
                .success());
        connectionService.deleteConversation(alice.getId(), conversationId);

        assertTrue(connectionService
                .sendMessage(alice.getId(), bob.getId(), "Me again")
                .success());

        assertEquals(1, connectionService.getConversations(alice.getId(), 50, 0).size());
    }

    @Test
    @DisplayName("messaging works again when both users deleted the conversation")
    void messagingWorksAfterBothUsersDeleted() {
        assertTrue(connectionService
                .sendMessage(alice.getId(), bob.getId(), "Hello Bob")
                .success());
        connectionService.deleteConversation(alice.getId(), conversationId);
        connectionService.deleteConversation(bob.getId(), conversationId);

        assertTrue(connectionService
                .sendMessage(bob.getId(), alice.getId(), "Round two")
                .success());

        assertEquals(1, connectionService.getConversations(alice.getId(), 50, 0).size());
        assertEquals(1, connectionService.getConversations(bob.getId(), 50, 0).size());
    }

    @Test
    @DisplayName("a row soft-deleted by the old delete behavior no longer blocks messaging")
    void legacySoftDeletedRowNoLongerBlocksMessaging() {
        assertTrue(connectionService
                .sendMessage(alice.getId(), bob.getId(), "Hello Bob")
                .success());
        communicationStorage.deleteConversationWithMessages(conversationId);
        assertTrue(communicationStorage.getConversation(conversationId).isEmpty());

        assertTrue(connectionService
                .sendMessage(bob.getId(), alice.getId(), "Hi again")
                .success());

        assertEquals(1, connectionService.getConversations(alice.getId(), 50, 0).size());
        assertEquals(1, connectionService.getConversations(bob.getId(), 50, 0).size());
    }

    @Test
    @DisplayName("the archive filter is judged per user and unarchive clears only the caller's flag")
    void archiveFilterIsPerUser() {
        assertTrue(connectionService
                .sendMessage(alice.getId(), bob.getId(), "Hello Bob")
                .success());
        connectionService.archiveConversation(conversationId, alice.getId(), MatchArchiveReason.UNMATCH);
        connectionService.archiveConversation(conversationId, bob.getId(), MatchArchiveReason.UNMATCH);

        assertEquals(1, listSize(alice, ArchiveFilter.ARCHIVED));
        assertEquals(0, listSize(alice, ArchiveFilter.NOT_ARCHIVED));
        assertEquals(1, listSize(alice, ArchiveFilter.ALL));

        connectionService.unarchiveConversation(conversationId, alice.getId());

        assertEquals(0, listSize(alice, ArchiveFilter.ARCHIVED));
        assertEquals(1, listSize(alice, ArchiveFilter.NOT_ARCHIVED));
        assertEquals(1, listSize(bob, ArchiveFilter.ARCHIVED));
        Conversation reloaded =
                communicationStorage.getConversation(conversationId).orElseThrow();
        assertFalse(reloaded.isArchivedBy(alice.getId()));
        assertTrue(reloaded.isArchivedBy(bob.getId()));
    }

    @Test
    @DisplayName("super likers are a subset of the users who liked")
    void superLikersAreReportedSeparately() {
        User carol = TestUserFactory.createActiveUser(UUID.randomUUID(), "Carol");
        services.getUserStorage().save(carol);
        services.getInteractionStorage().save(Like.create(bob.getId(), alice.getId(), Like.Direction.LIKE));
        services.getInteractionStorage().save(Like.create(carol.getId(), alice.getId(), Like.Direction.SUPER_LIKE));

        assertEquals(Set.of(carol.getId()), services.getInteractionStorage().getUserIdsWhoSuperLiked(alice.getId()));
        assertEquals(
                Set.of(bob.getId(), carol.getId()),
                services.getInteractionStorage().getUserIdsWhoLiked(alice.getId()));
    }

    private int listSize(User user, ArchiveFilter filter) {
        return connectionService.getConversations(user.getId(), filter, 50, 0).size();
    }
}
