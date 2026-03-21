package datingapp.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies thread-safe initialization of DatabaseManager.
 * Multiple threads calling getConnection() concurrently must not
 * cause double pool initialization or other race conditions.
 */
@Timeout(10)
class DatabaseManagerThreadSafetyTest {

    @BeforeEach
    void setup() {
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:thread_safety_test_" + UUID.randomUUID());
        DatabaseManager.resetInstance();
    }

    @Test
    @DisplayName("concurrent getConnection() calls should not cause double initialization")
    void concurrentGetConnection() throws Exception {
        DatabaseManager dm = DatabaseManager.getInstance();
        int threadCount = 10;
        var latch = new CountDownLatch(1);
        var errors = new ConcurrentLinkedQueue<Throwable>();

        List<Thread> threads = IntStream.range(0, threadCount)
                .mapToObj(i -> Thread.ofVirtual().start(() -> {
                    try {
                        latch.await();
                        try (Connection conn = dm.getConnection()) {
                            assertNotNull(conn, "Connection should not be null");
                            assertFalse(conn.isClosed(), "Connection should be open");
                        }
                    } catch (Exception e) {
                        errors.add(e);
                    }
                }))
                .toList();

        latch.countDown(); // Release all threads simultaneously
        for (Thread t : threads) {
            t.join();
        }

        assertTrue(errors.isEmpty(), "Concurrent getConnection() failures: " + errors);
    }

    @Test
    @DisplayName("multiple sequential connections should all be valid")
    void sequentialConnections() throws Exception {
        DatabaseManager dm = DatabaseManager.getInstance();

        for (int i = 0; i < 5; i++) {
            try (Connection conn = dm.getConnection()) {
                assertNotNull(conn);
                assertFalse(conn.isClosed());
            }
        }
    }

    @Test
    @DisplayName("connection validation config and pool should serve valid connections")
    void connectionValidationConfigAndPoolValidity() throws Exception {
        DatabaseManager dm = DatabaseManager.getInstance();

        // Verify pool is initialized and settings are applied
        try (Connection conn = dm.getConnection()) {
            assertNotNull(conn, "Connection should not be null");
            assertFalse(conn.isClosed(), "Connection should be open");
        }

        // Verify Hikari configuration via reflection
        verifyHikariConnectionValidationSettings(dm);
    }

    private void verifyHikariConnectionValidationSettings(DatabaseManager dm) throws Exception {
        // Access the dataSource field via reflection
        Field dsField = DatabaseManager.class.getDeclaredField("dataSource");
        dsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var atomicRef = (java.util.concurrent.atomic.AtomicReference<HikariDataSource>) dsField.get(dm);
        HikariDataSource ds = atomicRef.get();

        assertNotNull(ds, "HikariDataSource should be initialized");

        String testQuery = ds.getConnectionTestQuery();
        long validationTimeout = ds.getValidationTimeout();

        assertEquals("SELECT 1", testQuery, "Connection test query should be 'SELECT 1'");
        assertEquals(3000, validationTimeout, "Validation timeout should be 3000 ms");
    }
}
