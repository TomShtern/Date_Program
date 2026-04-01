package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatabaseManagerConfigurationTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";
    private static final String PASSWORD_PROPERTY = "datingapp.db.password";

    @BeforeEach
    @AfterEach
    void resetRuntimeConfigAndDatabaseManager() {
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
    void explicitPasswordPropertyIsReturnedVerbatim() {
        DatabaseManager.setJdbcUrl("jdbc:h2:./target/dbmanager-config-" + UUID.randomUUID());
        System.setProperty(PASSWORD_PROPERTY, "super-secret-password");

        assertEquals("super-secret-password", DatabaseManager.getConfiguredPassword());
    }

    @Test
    @DisplayName("test profile should allow empty password for local file databases")
    void testProfileAllowsEmptyPasswordForLocalFileDatabase() {
        DatabaseManager.setJdbcUrl("jdbc:h2:./target/dbmanager-config-" + UUID.randomUUID());
        System.setProperty(PROFILE_PROPERTY, "test");

        assertEquals("", DatabaseManager.getConfiguredPassword());
    }

    @Test
    @DisplayName("resolvePassword should use the provided JDBC URL snapshot")
    void resolvePasswordUsesProvidedJdbcUrlSnapshot() {
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:dbmanager-config-global-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        String localJdbcUrl = "jdbc:h2:./target/dbmanager-config-local-" + UUID.randomUUID();

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> invokeResolvedPassword(null, null, localJdbcUrl));

        assertEquals(
                "Local file databases require an explicit password or an explicit database profile (test/dev)",
                exception.getMessage());
    }

    @Test
    @DisplayName("dev profile should allow in-memory databases without an explicit password")
    void devProfileAllowsInMemoryDatabaseWithoutExplicitPassword() throws Exception {
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:dbmanager-config-dev-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        System.setProperty(PROFILE_PROPERTY, "dev");

        try (Connection connection = DatabaseManager.getInstance().getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
        }
    }

    @Test
    @DisplayName("dev profile should rewrite the implicit default file database path")
    void devProfileRewritesImplicitDefaultFileDatabasePath() throws Exception {
        assertEquals("jdbc:h2:./data/dating-dev", invokeEffectiveJdbcUrl("jdbc:h2:./data/dating", "dev", null));
    }

    @Test
    @DisplayName("test profile should rewrite the implicit default file database path")
    void testProfileRewritesImplicitDefaultFileDatabasePath() throws Exception {
        assertEquals("jdbc:h2:./data/dating-test", invokeEffectiveJdbcUrl("jdbc:h2:./data/dating", "test", null));
    }

    @Test
    @DisplayName("explicit local database paths should not be rewritten for dev profile")
    void explicitLocalDatabasePathIsNotRewrittenForDevProfile() throws Exception {
        String explicitUrl = "jdbc:h2:./target/dbmanager-config-explicit-" + UUID.randomUUID();

        assertEquals(explicitUrl, invokeEffectiveJdbcUrl(explicitUrl, "dev", null));
    }

    @Test
    @DisplayName("implicit default database path should stay unchanged when an explicit password is configured")
    void implicitDefaultDatabasePathStaysUnchangedWhenExplicitPasswordIsConfigured() throws Exception {
        assertEquals(
                "jdbc:h2:./data/dating",
                invokeEffectiveJdbcUrl("jdbc:h2:./data/dating", "dev", "super-secret-password"));
    }

    private static String invokeEffectiveJdbcUrl(String jdbcUrl, String profile, String explicitPassword)
            throws Exception {
        Method method =
                DatabaseManager.class.getDeclaredMethod("resolveJdbcUrl", String.class, String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, jdbcUrl, profile, explicitPassword);
    }

    private static String invokeResolvedPassword(String explicitPassword, String profile, String jdbcUrl) {
        try {
            Method method = DatabaseManager.class.getDeclaredMethod(
                    "resolvePassword", String.class, String.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, explicitPassword, profile, jdbcUrl);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(exception);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void clearRuntimeConfig() {
        System.clearProperty(PROFILE_PROPERTY);
        System.clearProperty(PASSWORD_PROPERTY);
    }
}
