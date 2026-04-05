package datingapp.app.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.storage.DatabaseManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(5)
@DisplayName("ApplicationStartup bootstrap configuration")
class ApplicationStartupBootstrapTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    @BeforeEach
    void setUp() {
        clearBootstrapState();
        System.setProperty(PROFILE_PROPERTY, "test");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:app-startup-bootstrap-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    @AfterEach
    void tearDown() {
        clearBootstrapState();
    }

    @Test
    @DisplayName("load should prefer an explicit system property override")
    void loadUsesSystemPropertyOverride(@TempDir Path tempDir) throws IOException {
        Path overrideConfig = tempDir.resolve("override-config.json");
        Files.writeString(overrideConfig, """
                {
                  "dailyLikeLimit": 17
                }
                """);

        String previous = System.getProperty("datingapp.config");
        try {
            System.setProperty("datingapp.config", overrideConfig.toString());

            AppConfig config = ApplicationStartup.load();

            assertEquals(17, config.matching().dailyLikeLimit());
        } finally {
            restoreSystemProperty("datingapp.config", previous);
        }
    }

    @Test
    @DisplayName("load should fall back to the bundled app config when override is missing")
    void loadFallsBackToBundledConfig(@TempDir Path tempDir) {
        String previous = System.getProperty("datingapp.config");
        try {
            System.setProperty(
                    "datingapp.config", tempDir.resolve("missing-config.json").toString());

            AppConfig config = ApplicationStartup.load();

            assertEquals(100, config.matching().dailyLikeLimit());
        } finally {
            restoreSystemProperty("datingapp.config", previous);
        }
    }

    @Test
    @DisplayName("applyEnvironmentOverrides should fail fast on invalid numeric values")
    void invalidNumericEnvValuesFailFast() {
        AppConfig.Builder builder = AppConfig.builder();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ApplicationStartup.applyEnvironmentOverrides(
                        builder, name -> "DATING_APP_DAILY_LIKE_LIMIT".equals(name) ? "not-a-number" : null));

        assertTrue(error.getMessage().contains("DATING_APP_DAILY_LIKE_LIMIT"));
    }

    @Test
    @DisplayName("applyEnvironmentOverrides should fail fast on invalid timezone values")
    void invalidTimezoneEnvValuesFailFast() {
        AppConfig.Builder builder = AppConfig.builder();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ApplicationStartup.applyEnvironmentOverrides(
                        builder, name -> "DATING_APP_USER_TIME_ZONE".equals(name) ? "Not/A_Zone" : null));

        assertTrue(error.getMessage().contains("DATING_APP_USER_TIME_ZONE"));
    }

    @Test
    @DisplayName("applyEnvironmentOverrides should apply storage database overrides")
    void storageDatabaseEnvOverridesAreApplied() {
        AppConfig.Builder builder = AppConfig.builder();

        ApplicationStartup.applyEnvironmentOverrides(builder, name -> switch (name) {
            case "DATING_APP_DB_DIALECT" -> "POSTGRESQL";
            case "DATING_APP_DB_URL" -> "jdbc:postgresql://localhost:5432/datingapp";
            case "DATING_APP_DB_USERNAME" -> "datingapp";
            default -> null;
        });

        AppConfig config = builder.build();

        assertEquals("POSTGRESQL", config.storage().databaseDialect());
        assertEquals(
                "jdbc:postgresql://localhost:5432/datingapp", config.storage().databaseUrl());
        assertEquals("datingapp", config.storage().databaseUsername());
        assertEquals(30, config.storage().queryTimeoutSeconds());
    }

    @Test
    @DisplayName("getServices should require initialize and shutdown should clear access")
    void getServicesRequiresInitializeAndShutdownClearsAccess() {
        IllegalStateException beforeInit = assertThrows(IllegalStateException.class, ApplicationStartup::getServices);
        assertTrue(beforeInit.getMessage().contains("initialize"));

        AppConfig suppliedConfig = AppConfig.builder().dailyLikeLimit(17).build();
        ServiceRegistry services = ApplicationStartup.initialize(suppliedConfig);

        assertSame(suppliedConfig, services.getConfig());
        assertSame(services, ApplicationStartup.getServices());

        ApplicationStartup.shutdown();

        IllegalStateException afterShutdown =
                assertThrows(IllegalStateException.class, ApplicationStartup::getServices);
        assertTrue(afterShutdown.getMessage().contains("initialize"));
    }

    @Test
    @DisplayName("reset should clear access after initialization")
    void resetClearsAccessAfterInitialization() {
        AppConfig suppliedConfig = AppConfig.builder().dailyLikeLimit(23).build();

        ServiceRegistry services = ApplicationStartup.initialize(suppliedConfig);

        assertSame(suppliedConfig, services.getConfig());
        assertSame(services, ApplicationStartup.getServices());

        ApplicationStartup.reset();

        IllegalStateException afterReset = assertThrows(IllegalStateException.class, ApplicationStartup::getServices);
        assertTrue(afterReset.getMessage().contains("initialize"));
    }

    private static void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private static void clearBootstrapState() {
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.clearProperty(PROFILE_PROPERTY);
        System.clearProperty("datingapp.config");
    }
}
