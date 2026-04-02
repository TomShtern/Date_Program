package datingapp.app.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.storage.DatabaseManager;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
        clearInitializationCompleteHook();
        clearBootstrapState();
    }

    @Test
    @DisplayName("initialize should roll back partial state after a startup failure")
    void initializeRollsBackPartialStateAfterFailure() {
        setInitializationCompleteHook(() -> {
            throw new IllegalStateException("synthetic startup failure");
        });

        IllegalStateException error = assertThrows(IllegalStateException.class, this::initializeWithDefaults);

        assertTrue(error.getMessage().contains("synthetic startup failure"));
        StartupState stateAfterFailure = snapshotState();
        assertFalse(stateAfterFailure.initialized());
        assertFalse(stateAfterFailure.hasServices());
        assertFalse(stateAfterFailure.hasDbManager());
        assertFalse(stateAfterFailure.hasCleanupScheduler());

        clearInitializationCompleteHook();

        ServiceRegistry services = ApplicationStartup.initialize(AppConfig.defaults());

        assertNotNull(services);
        assertTrue(snapshotState().initialized());
    }

    private static void setInitializationCompleteHook(Runnable hook) {
        initializationCompleteHook().set(hook);
    }

    private ServiceRegistry initializeWithDefaults() {
        return ApplicationStartup.initialize(AppConfig.defaults());
    }

    private static void clearInitializationCompleteHook() {
        initializationCompleteHook().set(null);
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<Runnable> initializationCompleteHook() {
        try {
            Field field = ApplicationStartup.class.getDeclaredField("INITIALIZATION_COMPLETE_HOOK");
            field.setAccessible(true);
            return (AtomicReference<Runnable>) field.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(
                    "Expected ApplicationStartup to expose INITIALIZATION_COMPLETE_HOOK for tests", ex);
        }
    }

    private static StartupState snapshotState() {
        try {
            Field initialized = ApplicationStartup.class.getDeclaredField("initialized");
            initialized.setAccessible(true);
            Field services = ApplicationStartup.class.getDeclaredField("services");
            services.setAccessible(true);
            Field dbManager = ApplicationStartup.class.getDeclaredField("dbManager");
            dbManager.setAccessible(true);
            Field cleanupScheduler = ApplicationStartup.class.getDeclaredField("CLEANUP_SCHEDULER_REF");
            cleanupScheduler.setAccessible(true);

            AtomicReference<?> schedulerRef = (AtomicReference<?>) cleanupScheduler.get(null);
            return new StartupState(
                    initialized.getBoolean(null),
                    services.get(null) != null,
                    dbManager.get(null) != null,
                    schedulerRef.get() != null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to inspect ApplicationStartup state", ex);
        }
    }

    private static void clearBootstrapState() {
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.clearProperty(PROFILE_PROPERTY);
    }

    private record StartupState(
            boolean initialized, boolean hasServices, boolean hasDbManager, boolean hasCleanupScheduler) {}
}
