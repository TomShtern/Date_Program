package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.core.RuntimeEnvironment;
import datingapp.core.ServiceRegistry;
import datingapp.support.LivePostgresqlTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
@DisplayName("PostgreSQL schema bootstrap smoke")
class PostgresqlSchemaBootstrapSmokeTest {

    private static final String DB_URL_ENV = "DATING_APP_DB_URL";
    private static final String DB_USERNAME_ENV = "DATING_APP_DB_USERNAME";
    private static final String DB_PASSWORD_ENV = "DATING_APP_DB_PASSWORD";
    private static final String DB_PROFILE_PROPERTY = "datingapp.db.profile";

    @BeforeEach
    void setUp() {
        LivePostgresqlTestConfig.assumeConfigured();
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.clearProperty(DB_PROFILE_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.clearProperty(DB_PROFILE_PROPERTY);
    }

    @Test
    @DisplayName("ApplicationStartup.load should bootstrap the configured PostgreSQL target")
    void applicationStartupLoadBootstrapsConfiguredPostgresqlTarget() {
        ServiceRegistry services = ApplicationStartup.initialize(ApplicationStartup.load());

        assertNotNull(services.getUserStorage());
        assertEquals("POSTGRESQL", services.getConfig().storage().databaseDialect());
        assertEquals(
                RuntimeEnvironment.getEnv(DB_URL_ENV),
                services.getConfig().storage().databaseUrl());
        assertEquals(
                RuntimeEnvironment.getEnv(DB_USERNAME_ENV),
                services.getConfig().storage().databaseUsername());
    }
}
