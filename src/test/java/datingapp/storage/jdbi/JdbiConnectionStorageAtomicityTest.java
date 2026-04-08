package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.model.User;
import datingapp.core.testutil.TestUserFactory;
import datingapp.storage.DatabaseManager;
import datingapp.storage.schema.MigrationRunner;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.h2.api.Trigger;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
@DisplayName("JdbiConnectionStorage atomic write behavior")
class JdbiConnectionStorageAtomicityTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    private DatabaseManager dbManager;
    private Jdbi jdbi;
    private JdbiConnectionStorage communicationStorage;
    private JdbiUserStorage userStorage;

    private User sender;
    private User recipient;

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
        String dbName = "connection_atomicity_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        dbManager = DatabaseManager.getInstance();

        jdbi = Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException e) {
                        throw new DatabaseManager.StorageException("Failed to get database connection", e);
                    }
                })
                .installPlugin(new SqlObjectPlugin());

        jdbi.registerArgument(new JdbiTypeCodecs.EnumSetSqlCodec.EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new JdbiTypeCodecs.EnumSetSqlCodec.InterestColumnMapper());
        JdbiTypeCodecs.registerInstantCodec(jdbi);

        jdbi.useHandle(handle -> {
            try (Statement stmt = handle.getConnection().createStatement()) {
                MigrationRunner.runAllPending(stmt);
                stmt.execute("CREATE TRIGGER messages_delete_guard BEFORE UPDATE ON messages FOR EACH ROW CALL \""
                        + ConversationDeleteGuardTrigger.class.getName() + "\"");
            } catch (SQLException e) {
                throw new DatabaseManager.StorageException("Failed to initialize schema", e);
            }
        });

        communicationStorage = new JdbiConnectionStorage(jdbi);
        userStorage = new JdbiUserStorage(jdbi);

        sender = TestUserFactory.createActiveUser(UUID.randomUUID(), "Sender");
        recipient = TestUserFactory.createActiveUser(UUID.randomUUID(), "Recipient");
        userStorage.save(sender);
        userStorage.save(recipient);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(PROFILE_PROPERTY);
        if (dbManager != null) {
            dbManager.shutdown();
            DatabaseManager.resetInstance();
        }
    }

    @Test
    @DisplayName("saveMessageAndUpdateConversationLastMessageAt rolls back when the insert fails")
    void saveMessageAndUpdateConversationLastMessageAtRollsBackWhenInsertFails() {
        Conversation conversation = Conversation.create(sender.getId(), recipient.getId());
        communicationStorage.saveConversation(conversation);

        Message existing = Message.create(conversation.getId(), sender.getId(), "Existing");
        communicationStorage.saveMessage(existing);

        Message duplicate = new Message(
                existing.id(),
                conversation.getId(),
                sender.getId(),
                "Duplicate",
                existing.createdAt().plusSeconds(60));

        boolean messageWriteFailed = false;
        try {
            attemptAtomicMessageWrite(duplicate);
        } catch (UnableToExecuteStatementException _) {
            messageWriteFailed = true;
        }
        assertTrue(messageWriteFailed, "Expected message write to fail");

        Conversation reloaded =
                communicationStorage.getConversation(conversation.getId()).orElseThrow();
        assertNull(reloaded.getLastMessageAt(), "last_message_at should roll back on write failure");
        assertEquals(1, communicationStorage.countMessages(conversation.getId()));
    }

    @Test
    @DisplayName("deleteConversationWithMessages rolls back when the message cleanup step fails")
    void deleteConversationWithMessagesRollsBackWhenMessageCleanupFails() {
        Conversation conversation = Conversation.create(sender.getId(), recipient.getId());
        communicationStorage.saveConversation(conversation);
        communicationStorage.saveMessage(Message.create(conversation.getId(), sender.getId(), "Message"));

        String conversationId = conversation.getId();
        boolean deleteFailed = false;
        try {
            attemptAtomicDelete(conversationId);
        } catch (UnableToExecuteStatementException _) {
            deleteFailed = true;
        }
        assertTrue(deleteFailed, "Expected atomic delete to fail");

        Conversation reloaded =
                communicationStorage.getConversation(conversation.getId()).orElseThrow();
        assertNull(reloaded.getUserAArchivedAt());
        assertEquals(1, communicationStorage.countMessages(conversation.getId()));
    }

    private void attemptAtomicMessageWrite(Message duplicate) {
        communicationStorage.saveMessageAndUpdateConversationLastMessageAt(duplicate);
    }

    private void attemptAtomicDelete(String conversationId) {
        communicationStorage.deleteConversationWithMessages(conversationId);
    }

    public static final class ConversationDeleteGuardTrigger implements Trigger {
        @Override
        public void init(
                Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type) {
            // No initialization needed for this test-only trigger.
        }

        @Override
        public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
            throw new SQLException("simulated message cleanup failure after conversation deletion");
        }

        @Override
        public void close() {
            // Nothing to close.
        }

        @Override
        public void remove() {
            // Nothing to remove.
        }
    }
}
