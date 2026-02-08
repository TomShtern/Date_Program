package datingapp.storage;

import static org.junit.jupiter.api.Assertions.*;

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
}
