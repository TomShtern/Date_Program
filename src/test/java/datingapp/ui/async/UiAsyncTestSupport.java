package datingapp.ui.async;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/** Shared async test helpers for UI/ViewModel tests. */
public final class UiAsyncTestSupport {

    private UiAsyncTestSupport() {}

    public static boolean waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
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

    public static void pauseMillis(long millis) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    public static void sleepQuietly(long millis) {
        pauseMillis(millis);
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
        }
    }

    public static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean await(CountDownLatch latch, Duration timeout) throws InterruptedException {
        return latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static final class TestUiThreadDispatcher implements UiThreadDispatcher {
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

    public static final class QueuedUiThreadDispatcher implements UiThreadDispatcher {
        private final ConcurrentLinkedQueue<Runnable> queuedActions = new ConcurrentLinkedQueue<>();
        private final ThreadLocal<Boolean> uiThread = ThreadLocal.withInitial(() -> false);
        private final CountDownLatch firstDispatchQueued = new CountDownLatch(1);

        @Override
        public boolean isUiThread() {
            return uiThread.get();
        }

        @Override
        public void dispatch(Runnable action) {
            queuedActions.add(action);
            firstDispatchQueued.countDown();
        }

        public boolean awaitFirstDispatchQueued() {
            try {
                return firstDispatchQueued.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        public void drainAll() {
            Runnable action;
            while ((action = queuedActions.poll()) != null) {
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
}
