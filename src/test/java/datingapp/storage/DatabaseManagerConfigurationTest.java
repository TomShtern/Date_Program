package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatabaseManagerConfigurationTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";
    private static final String PASSWORD_PROPERTY = "datingapp.db.password";

    @BeforeEach
    void setUp() {
        clearRuntimeConfig();
        DatabaseManager.resetInstance();
    }

    @AfterEach
    void tearDown() {
        clearRuntimeConfig();
        DatabaseManager.resetInstance();
    }

    @Test
    @DisplayName("local file databases must reject implicit password fallback")
    void localFileDatabaseRejectsImplicitPasswordFallback() {
        DatabaseManager.setJdbcUrl("jdbc:h2:./target/dbmanager-config-" + UUID.randomUUID());
        System.setProperty(PROFILE_PROPERTY, "prod");
        DatabaseManager manager = DatabaseManager.getInstance();

        assertThrows(IllegalStateException.class, manager::getConnection);
    }

    @Test
    @DisplayName("explicit password property should win over profile defaults")
    void explicitPasswordPropertyIsReturnedVerbatim() throws Exception {
        DatabaseManager.setJdbcUrl("jdbc:h2:./target/dbmanager-config-" + UUID.randomUUID());
        System.setProperty(PASSWORD_PROPERTY, "super-secret-password");

        assertEquals("super-secret-password", invokeConfiguredPassword());
    }

    @Test
    @DisplayName("test profile should allow empty password for local file databases")
    void testProfileAllowsEmptyPasswordForLocalFileDatabase() throws Exception {
        DatabaseManager.setJdbcUrl("jdbc:h2:./target/dbmanager-config-" + UUID.randomUUID());
        System.setProperty(PROFILE_PROPERTY, "test");

        assertEquals("", invokeConfiguredPassword());
    }

    private static String invokeConfiguredPassword() throws Exception {
        Method method = DatabaseManager.class.getDeclaredMethod("getConfiguredPassword");
        method.setAccessible(true);
        return (String) method.invoke(null);
    }

    private static void clearRuntimeConfig() {
        System.clearProperty(PROFILE_PROPERTY);
        System.clearProperty(PASSWORD_PROPERTY);
    }
}
