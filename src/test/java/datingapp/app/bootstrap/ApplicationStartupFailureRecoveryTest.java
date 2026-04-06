package datingapp.app.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.storage.DatabaseManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
@DisplayName("ApplicationStartup failure recovery")
class ApplicationStartupFailureRecoveryTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    @BeforeEach
    void setUp() {
        clearBootstrapState();
        System.setProperty(PROFILE_PROPERTY, "test");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:app-startup-recovery-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    @AfterEach
    void tearDown() {
        ApplicationStartup.setInitializationCompleteHookForTests(null);
        clearBootstrapState();
    }

    @Test
    @DisplayName("failed initialize leaves services unavailable and allows a clean retry")
    void failedInitializeLeavesServicesUnavailableAndAllowsCleanRetry() {
        ApplicationStartup.setInitializationCompleteHookForTests(() -> {
            throw new IllegalStateException("synthetic startup failure");
        });
        AppConfig defaults = AppConfig.defaults();

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> ApplicationStartup.initialize(defaults));

        assertTrue(error.getMessage().contains("synthetic startup failure"));
        assertThrows(IllegalStateException.class, ApplicationStartup::getServices);

        ApplicationStartup.setInitializationCompleteHookForTests(null);

        assertDoesNotThrow(ApplicationStartup::shutdown);
        assertDoesNotThrow(ApplicationStartup::reset);

        ServiceRegistry services = ApplicationStartup.initialize(defaults);

        assertNotNull(services);
        assertSame(services, ApplicationStartup.getServices());
    }

    private static void clearBootstrapState() {
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.clearProperty(PROFILE_PROPERTY);
    }
}
