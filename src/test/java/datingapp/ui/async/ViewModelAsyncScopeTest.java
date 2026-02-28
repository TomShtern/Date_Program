package datingapp.ui.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.ui.viewmodel.ViewModelErrorSink;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("ViewModelAsyncScope")
class ViewModelAsyncScopeTest {

    @Test
    @DisplayName("runLatest delivers only the newest callback for a key")
    void runLatestDeliversOnlyNewestCallbackForKey() throws InterruptedException {
        TestUiThreadDispatcher dispatcher = new TestUiThreadDispatcher();
        ViewModelAsyncScope scope = createScope(dispatcher, message -> {
            throw new AssertionError("Unexpected error: " + message);
        });

        AtomicReference<String> delivered = new AtomicReference<>();
        AtomicInteger callbackCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        scope.runLatest(
                "refresh",
                "first refresh",
                () -> {
                    sleepQuietly(150);
                    return "first";
                },
                value -> {
                    delivered.set(value);
                    callbackCount.incrementAndGet();
                    latch.countDown();
                });

        scope.runLatest("refresh", "second refresh", () -> "second", value -> {
            delivered.set(value);
            callbackCount.incrementAndGet();
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("second", delivered.get());
        assertEquals(1, callbackCount.get());

        scope.dispose();
    }

    @Test
    @DisplayName("dispose cancels in-flight tasks and suppresses callbacks")
    void disposeCancelsInflightTasksAndSuppressesCallbacks() throws InterruptedException {
        TestUiThreadDispatcher dispatcher = new TestUiThreadDispatcher();
        ViewModelAsyncScope scope = createScope(dispatcher, message -> {
            throw new AssertionError("Unexpected error: " + message);
        });

        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        TaskHandle handle = scope.run(
                "long task",
                () -> {
                    started.countDown();
                    sleepQuietly(800);
                    return 42;
                },
                value -> callbackInvoked.set(true));

        assertTrue(started.await(1, TimeUnit.SECONDS));
        scope.dispose();

        sleepQuietly(200);

        assertTrue(handle.isCancelled());
        assertFalse(callbackInvoked.get());
    }

    @Test
    @DisplayName("success callbacks execute on the UI dispatcher")
    void successCallbacksExecuteOnUiDispatcher() throws InterruptedException {
        TestUiThreadDispatcher dispatcher = new TestUiThreadDispatcher();
        ViewModelAsyncScope scope = createScope(dispatcher, message -> {
            throw new AssertionError("Unexpected error: " + message);
        });

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean callbackOnUiThread = new AtomicBoolean(false);

        scope.run("ui callback", () -> "ok", value -> {
            callbackOnUiThread.set(dispatcher.isUiThread());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(callbackOnUiThread.get());

        scope.dispose();
    }

    @Test
    @DisplayName("errors are routed exactly once through AsyncErrorRouter")
    void errorsAreRoutedExactlyOnceThroughAsyncErrorRouter() throws InterruptedException {
        TestUiThreadDispatcher dispatcher = new TestUiThreadDispatcher();
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        ViewModelAsyncScope scope = createScope(dispatcher, message -> {
            errorMessage.set(message);
            errorCount.incrementAndGet();
            latch.countDown();
        });

        scope.run(
                "explode",
                () -> {
                    throw new IllegalStateException("boom");
                },
                value -> {
                    throw new AssertionError("Callback should not run on failure");
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, errorCount.get());
        assertNotNull(errorMessage.get());
        assertTrue(errorMessage.get().contains("explode"));
        assertTrue(errorMessage.get().contains("boom"));

        scope.dispose();
    }

    @Test
    @DisplayName("loading state tracks overlapping tasks and returns to false")
    void loadingStateTracksOverlappingTasksAndReturnsToFalse() throws InterruptedException {
        TestUiThreadDispatcher dispatcher = new TestUiThreadDispatcher();
        ViewModelAsyncScope scope = createScope(dispatcher, message -> {
            throw new AssertionError("Unexpected error: " + message);
        });

        List<Boolean> loadingStates = new CopyOnWriteArrayList<>();
        scope.setLoadingStateConsumer(loadingStates::add);

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        scope.run(
                "task-a",
                () -> {
                    awaitQuietly(release);
                    return 1;
                },
                value -> done.countDown());

        scope.run(
                "task-b",
                () -> {
                    awaitQuietly(release);
                    return 2;
                },
                value -> done.countDown());

        assertTrue(waitUntil(() -> loadingStates.contains(Boolean.TRUE), Duration.ofSeconds(2)));

        release.countDown();
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue(waitUntil(
                () -> !loadingStates.isEmpty() && Boolean.FALSE.equals(loadingStates.get(loadingStates.size() - 1)),
                Duration.ofSeconds(2)));

        scope.dispose();
    }

    @Test
    @DisplayName("fire-and-forget tasks do not toggle loading state")
    void fireAndForgetTasksDoNotToggleLoadingState() throws InterruptedException {
        TestUiThreadDispatcher dispatcher = new TestUiThreadDispatcher();
        ViewModelAsyncScope scope = createScope(dispatcher, message -> {
            throw new AssertionError("Unexpected error: " + message);
        });

        List<Boolean> loadingStates = new CopyOnWriteArrayList<>();
        scope.setLoadingStateConsumer(loadingStates::add);

        CountDownLatch latch = new CountDownLatch(1);
        scope.runFireAndForget("fire", latch::countDown);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertFalse(loadingStates.contains(Boolean.TRUE));

        scope.dispose();
    }

    private static ViewModelAsyncScope createScope(TestUiThreadDispatcher dispatcher, ViewModelErrorSink sink) {
        AsyncErrorRouter router = new AsyncErrorRouter(dispatcher, () -> sink, (_, _) -> {});
        return new ViewModelAsyncScope("test", dispatcher, router);
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return true;
            }
            pauseMillis(20);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while waiting for async condition");
            }
        }
        return condition.getAsBoolean();
    }

    private static void sleepQuietly(long millis) {
        pauseMillis(millis);
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static void pauseMillis(long millis) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    private static final class TestUiThreadDispatcher implements UiThreadDispatcher {
        private final ThreadLocal<Boolean> uiThread = ThreadLocal.withInitial(() -> false);

        @Override
        public boolean isUiThread() {
            return uiThread.get();
        }

        @Override
        public void dispatch(Runnable action) {
            boolean previous = uiThread.get();
            uiThread.set(true);
            try {
                action.run();
            } finally {
                uiThread.set(previous);
            }
        }
    }
}
