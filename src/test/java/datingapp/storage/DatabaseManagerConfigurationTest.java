package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariDataSource;
import datingapp.core.AppConfig;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
    @DisplayName("H2 URLs should ignore an unscoped environment password")
    void h2UrlsIgnoreUnscopedEnvironmentPassword() {
        String resolvedPassword = DatabaseManager.resolveExplicitPassword(
                "jdbc:h2:./target/dbmanager-config-" + UUID.randomUUID(),
                key -> null,
                key -> "DATING_APP_DB_PASSWORD".equals(key) ? "datingapp" : null,
                key -> "DATING_APP_DB_PASSWORD".equals(key) ? "datingapp" : null);

        assertNull(resolvedPassword);
    }

    @Test
    @DisplayName("PostgreSQL URLs should use environment password fallback")
    void postgresqlUrlsUseEnvironmentPasswordFallback() {
        String resolvedPassword = DatabaseManager.resolveExplicitPassword(
                "jdbc:postgresql://localhost:5432/datingapp",
                key -> null,
                key -> "DATING_APP_DB_PASSWORD".equals(key) ? "datingapp" : null,
                key -> "DATING_APP_DB_PASSWORD".equals(key) ? "datingapp" : null);

        assertEquals("datingapp", resolvedPassword);
    }

    @Test
    @DisplayName("PostgreSQL URLs should reject missing external passwords")
    void postgresqlUrlsRejectMissingExternalPasswords() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DatabaseManager.resolvePassword(null, null, "jdbc:postgresql://localhost:5432/datingapp"));

        assertEquals(
                "Database password must be provided via datingapp.db.password or DATING_APP_DB_PASSWORD",
                exception.getMessage());
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
    void devProfileRewritesImplicitDefaultFileDatabasePath() {
        assertEquals("jdbc:h2:./data/dating-dev", invokeEffectiveJdbcUrl("jdbc:h2:./data/dating", "dev", null));
    }

    @Test
    @DisplayName("test profile should rewrite the implicit default file database path")
    void testProfileRewritesImplicitDefaultFileDatabasePath() {
        assertEquals("jdbc:h2:./data/dating-test", invokeEffectiveJdbcUrl("jdbc:h2:./data/dating", "test", null));
    }

    @Test
    @DisplayName("explicit local database paths should not be rewritten for dev profile")
    void explicitLocalDatabasePathIsNotRewrittenForDevProfile() {
        String explicitUrl = "jdbc:h2:./target/dbmanager-config-explicit-" + UUID.randomUUID();

        assertEquals(explicitUrl, invokeEffectiveJdbcUrl(explicitUrl, "dev", null));
    }

    @Test
    @DisplayName("implicit default database path should stay unchanged when an explicit password is configured")
    void implicitDefaultDatabasePathStaysUnchangedWhenExplicitPasswordIsConfigured() {
        assertEquals(
                "jdbc:h2:./data/dating",
                invokeEffectiveJdbcUrl("jdbc:h2:./data/dating", "dev", "super-secret-password"));
    }

    @Test
    @DisplayName("postgresql JDBC URLs should not be rewritten for legacy H2 profiles")
    void postgresqlJdbcUrlsAreNotRewrittenForLegacyProfiles() {
        String postgresqlUrl = "jdbc:postgresql://localhost:5432/datingapp";

        assertEquals(postgresqlUrl, invokeEffectiveJdbcUrl(postgresqlUrl, "dev", null));
        assertEquals(postgresqlUrl, invokeEffectiveJdbcUrl(postgresqlUrl, "test", null));
    }

    @Test
    @DisplayName("PostgreSQL session setup should pin timezone to UTC before applying timeout")
    void postgresqlSessionSetupPinsTimezoneToUtc() throws Exception {
        DatabaseManager manager = DatabaseManager.getInstance();
        manager.configureStorage(AppConfig.builder()
                .databaseDialect("POSTGRESQL")
                .databaseUrl("jdbc:postgresql://localhost:55432/datingapp")
                .databaseUsername("datingapp")
                .queryTimeoutSeconds(42)
                .build()
                .storage());

        List<String> executedSql = new ArrayList<>();
        Statement statement = (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[] {Statement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "execute" -> {
                        executedSql.add((String) args[0]);
                        yield true;
                    }
                    case "close" -> null;
                    case "isClosed" -> false;
                    default -> defaultValue(method.getReturnType());
                });
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createStatement" -> statement;
                    case "close" -> null;
                    case "isClosed" -> false;
                    default -> defaultValue(method.getReturnType());
                });

        Method applySessionQueryTimeout =
                DatabaseManager.class.getDeclaredMethod("applySessionQueryTimeout", Connection.class);
        applySessionQueryTimeout.setAccessible(true);
        applySessionQueryTimeout.invoke(manager, connection);

        assertEquals(
                List.of("SET search_path TO public", "SET TIME ZONE 'UTC'", "SET statement_timeout TO 42000"),
                executedSql);
    }

    @Test
    @DisplayName("configurePoolSettings should externalize Hikari pool sizing and timeouts")
    void configurePoolSettingsExternalizesHikariSettings() throws Exception {
        System.setProperty(PROFILE_PROPERTY, "test");
        DatabaseManager manager = DatabaseManager.createIsolated(
                "jdbc:h2:mem:dbmanager-pool-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        AppConfig.StorageConfig storageConfig = AppConfig.builder()
                .databaseDialect("H2")
                .databaseUrl("jdbc:h2:mem:dbmanager-pool-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .databaseUsername("sa")
                .queryTimeoutSeconds(30)
                .maxPoolSize(14)
                .minIdle(4)
                .connectionTimeoutSeconds(7)
                .validationTimeoutSeconds(5)
                .idleTimeoutSeconds(900)
                .maxLifetimeSeconds(2400)
                .keepaliveTimeSeconds(120)
                .build()
                .storage();

        manager.configurePoolSettings(storageConfig);

        Connection connection = manager.getConnection();
        try {
            assertNotNull(connection);
            var dataSourceField = DatabaseManager.class.getDeclaredField("dataSource");
            dataSourceField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var dataSourceRef =
                    (java.util.concurrent.atomic.AtomicReference<HikariDataSource>) dataSourceField.get(manager);
            HikariDataSource dataSource = dataSourceRef.get();
            assertNotNull(dataSource);
            assertEquals(14, dataSource.getMaximumPoolSize());
            assertEquals(4, dataSource.getMinimumIdle());
            assertEquals(7000L, dataSource.getConnectionTimeout());
            assertEquals(5000L, dataSource.getValidationTimeout());
            assertEquals(900000L, dataSource.getIdleTimeout());
            assertEquals(2400000L, dataSource.getMaxLifetime());
            assertEquals(120000L, dataSource.getKeepaliveTime());
        } finally {
            connection.close();
            manager.shutdown();
            System.clearProperty(PROFILE_PROPERTY);
        }
    }

    @Test
    @DisplayName("resetInstance restores the default singleton JDBC URL")
    void resetInstanceRestoresDefaultSingletonJdbcUrl() throws Exception {
        DatabaseManager.setJdbcUrl("jdbc:h2:./target/dbmanager-config-reset-" + UUID.randomUUID());

        DatabaseManager.resetInstance();

        assertEquals("jdbc:h2:./data/dating", currentJdbcUrl());
    }

    private static String invokeEffectiveJdbcUrl(String jdbcUrl, String profile, String explicitPassword) {
        return DatabaseManager.resolveJdbcUrl(jdbcUrl, profile, explicitPassword);
    }

    private static String invokeResolvedPassword(String explicitPassword, String profile, String jdbcUrl) {
        return DatabaseManager.resolvePassword(explicitPassword, profile, jdbcUrl);
    }

    private static String currentJdbcUrl() throws Exception {
        var field = DatabaseManager.class.getDeclaredField("jdbcUrl");
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Double.TYPE) {
            return 0.0d;
        }
        if (returnType == Float.TYPE) {
            return 0.0f;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        return null;
    }

    private static void clearRuntimeConfig() {
        System.clearProperty(PROFILE_PROPERTY);
        System.clearProperty(PASSWORD_PROPERTY);
    }
}
