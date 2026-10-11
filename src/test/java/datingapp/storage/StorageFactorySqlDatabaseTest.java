package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.core.presence.PresenceService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StorageFactory.buildSqlDatabase")
class StorageFactorySqlDatabaseTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(PROFILE_PROPERTY);
    }

    @Test
    @DisplayName("wraps dialect detection failures in StorageException")
    void buildSqlDatabaseWrapsDialectDetectionFailuresInStorageException() {
        DatabaseManager dbManager = DatabaseManager.createIsolated("jdbc:invalid:dialect-detection");

        try {
            DatabaseManager.StorageException exception = assertThrows(
                    DatabaseManager.StorageException.class,
                    () -> StorageFactory.buildSqlDatabase(dbManager, AppConfig.defaults()));

            String message = exception.getMessage();
            assertAll(
                    () -> assertTrue(message.contains("detect"), "Expected message to mention 'detect'"),
                    () -> assertTrue(message.contains("dialect"), "Expected message to mention 'dialect'"));
        } finally {
            dbManager.shutdown();
        }
    }

    @Test
    @DisplayName("presence is stored in the database, so a second registry on the same database sees it")
    void presenceIsPersistedInTheDatabase() throws Exception {
        String url = "jdbc:h2:mem:presence_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        DatabaseManager dbManager = DatabaseManager.createIsolated(url);

        try {
            ServiceRegistry first = StorageFactory.buildSqlDatabase(dbManager, AppConfig.defaults());
            User user = new User(UUID.randomUUID(), "Presence User");
            first.getUserStorage().save(user);

            first.getPresenceService().heartbeat(user.getId());
            first.getPresenceService().setTyping(user.getId(), "conversation-1");

            try (Connection conn = dbManager.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM presence_heartbeat WHERE user_id = '"
                            + user.getId() + "' AND is_typing_in = 'conversation-1'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }

            ServiceRegistry second = StorageFactory.buildSqlDatabase(dbManager, AppConfig.defaults());
            assertEquals(
                    PresenceService.Status.ONLINE, second.getPresenceService().getStatus(user.getId()));
            assertTrue(second.getPresenceService().isTyping(user.getId(), "conversation-1"));
            assertFalse(second.getPresenceService().isTyping(user.getId(), "conversation-2"));

            second.getPresenceService().purgeStale();
            assertEquals(
                    PresenceService.Status.ONLINE,
                    first.getPresenceService().getStatus(user.getId()),
                    "a fresh heartbeat must survive the stale purge");
        } finally {
            dbManager.shutdown();
        }
    }
}
