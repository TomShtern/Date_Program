package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.connection.*;
import datingapp.core.matching.*;
import datingapp.core.metrics.*;
import datingapp.core.model.*;
import datingapp.core.profile.*;
import datingapp.core.recommendation.*;
import datingapp.core.safety.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for PerformanceMonitor utility.
 */
@Timeout(5)
@SuppressWarnings("unused")
@DisplayName("PerformanceMonitor")
class PerformanceMonitorTest {

    @BeforeEach
    void setUp() {
        PerformanceMonitor.reset();
    }

    @AfterEach
    void tearDown() {
        PerformanceMonitor.reset();
    }

    @Nested
    @DisplayName("Basic recording")
    class BasicRecording {

        @Test
        @DisplayName("Should record a single operation")
        void recordsSingleOperation() {
            PerformanceMonitor.record("test.operation", 50);

            PerformanceMonitor.OperationMetrics metrics = PerformanceMonitor.getMetrics("test.operation");

            assertNotNull(metrics);
            assertEquals(1, metrics.getCount());
            assertEquals(50, metrics.getTotalMs());
            assertEquals(50, metrics.getAverageMs());
            assertEquals(50, metrics.getMinMs());
            assertEquals(50, metrics.getMaxMs());
        }

        @Test
        @DisplayName("Should record multiple operations")
        void recordsMultipleOperations() {
            PerformanceMonitor.record("test.operation", 10);
            PerformanceMonitor.record("test.operation", 20);
            PerformanceMonitor.record("test.operation", 30);

            PerformanceMonitor.OperationMetrics metrics = PerformanceMonitor.getMetrics("test.operation");

            assertEquals(3, metrics.getCount());
            assertEquals(60, metrics.getTotalMs());
            assertEquals(20, metrics.getAverageMs());
            assertEquals(10, metrics.getMinMs());
            assertEquals(30, metrics.getMaxMs());
        }

        @Test
        @DisplayName("Should track separate operations independently")
        void tracksSeparateOperations() {
            PerformanceMonitor.record("op1", 100);
            PerformanceMonitor.record("op2", 200);

            assertEquals(100, PerformanceMonitor.getMetrics("op1").getAverageMs());
            assertEquals(200, PerformanceMonitor.getMetrics("op2").getAverageMs());
        }

        @Test
        @DisplayName("Should return null for unknown operation")
        void returnsNullForUnknown() {
            assertNull(PerformanceMonitor.getMetrics("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Timer")
    class TimerTests {

        @Test
        @DisplayName("Should auto-record when timer closes")
        void autoRecordsOnClose() {
            try (PerformanceMonitor.Timer timer = PerformanceMonitor.startTimer("auto.test")) {
                assertTrue(timer.elapsedMs() >= 0);
                timer.markSuccess();
            }

            PerformanceMonitor.OperationMetrics metrics = PerformanceMonitor.getMetrics("auto.test");
            assertNotNull(metrics);
            assertEquals(1, metrics.getCount());
            assertTrue(metrics.getAverageMs() >= 0); // At least 0ms (can be 0 on fast systems)
        }

        @Test
        @DisplayName("Should report elapsed time without closing")
        void reportsElapsedTime() {
            PerformanceMonitor.Timer timer = PerformanceMonitor.startTimer("elapsed.test");
            long elapsed = timer.elapsedMs();

            assertTrue(elapsed >= 0);
            timer.markSuccess();
            timer.close();
        }
    }

    @Nested
    @DisplayName("Reset")
    class ResetTests {

        @Test
        @DisplayName("Should clear all metrics on reset")
        void clearsMetricsOnReset() {
            PerformanceMonitor.record("clear.test", 100);
            assertNotNull(PerformanceMonitor.getMetrics("clear.test"));

            PerformanceMonitor.reset();

            assertNull(PerformanceMonitor.getMetrics("clear.test"));
        }
    }

    @Nested
    @DisplayName("OperationMetrics")
    class OperationMetricsTests {

        @Test
        @DisplayName("Should return name correctly")
        void returnsName() {
            PerformanceMonitor.record("named.op", 50);
            assertEquals("named.op", PerformanceMonitor.getMetrics("named.op").getName());
        }

        @Test
        @DisplayName("Should handle zero count edge case")
        void handlesZeroCount() {
            // Internal metrics object with zero count
            PerformanceMonitor.record("zero.edge", 0);
            PerformanceMonitor.OperationMetrics m = PerformanceMonitor.getMetrics("zero.edge");

            assertEquals(1, m.getCount());
            assertEquals(0, m.getAverageMs());
            assertEquals(0, m.getMinMs());
            assertEquals(0, m.getMaxMs());
        }
    }

    @Nested
    @DisplayName("Logging")
    class LoggingTests {

        @Test
        @DisplayName("logMetrics should not throw when empty")
        void logMetricsEmptyNoThrow() {
            assertDoesNotThrow(PerformanceMonitor::logMetrics);
        }

        @Test
        @DisplayName("logMetrics should not throw with data")
        void logMetricsWithDataNoThrow() {
            PerformanceMonitor.record("log.test", 100);
            assertDoesNotThrow(PerformanceMonitor::logMetrics);
        }

        @Test
        @DisplayName("logMetricsDebug should not throw")
        void logMetricsDebugNoThrow() {
            PerformanceMonitor.record("debug.test", 100);
            assertDoesNotThrow(PerformanceMonitor::logMetricsDebug);
        }
    }

    @Nested
    @DisplayName("Bounded Metrics Map")
    class BoundedMetricsTests {

        @Test
        @DisplayName("should not grow beyond MAX_METRICS_SIZE")
        void metricsMapIsBounded() {
            // Record 1100 unique operations (MAX is 1000)
            for (int i = 0; i < 1100; i++) {
                PerformanceMonitor.record("op-" + i, 10);
            }
            // Early operations should be recorded
            PerformanceMonitor.OperationMetrics early = PerformanceMonitor.getMetrics("op-0");
            assertNotNull(early, "Early operations should be recorded");
            // Operations beyond the 1000 limit should be silently dropped
            PerformanceMonitor.OperationMetrics late = PerformanceMonitor.getMetrics("op-1099");
            assertNull(late, "Operations beyond MAX_METRICS_SIZE should not be recorded");
        }
    }
}
