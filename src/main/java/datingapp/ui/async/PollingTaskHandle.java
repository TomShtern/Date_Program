package datingapp.ui.async;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handle for long-lived polling tasks managed by {@link ViewModelAsyncScope}.
 */
public final class PollingTaskHandle implements TaskHandle {

    private final String taskName;
    private final Duration interval;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final AtomicReference<Thread> thread = new AtomicReference<>();

    PollingTaskHandle(String taskName, Duration interval) {
        this.taskName = Objects.requireNonNull(taskName, "taskName cannot be null");
        this.interval = Objects.requireNonNull(interval, "interval cannot be null");
    }

    static PollingTaskHandle cancelled(String taskName, Duration interval) {
        PollingTaskHandle handle = new PollingTaskHandle(taskName, interval);
        handle.cancelled.set(true);
        handle.done.set(true);
        return handle;
    }

    void bindThread(Thread pollingThread) {
        thread.set(pollingThread);
    }

    void markDone() {
        done.set(true);
    }

    public Duration interval() {
        return interval;
    }

    @Override
    public String taskName() {
        return taskName;
    }

    @Override
    public TaskPolicy policy() {
        return TaskPolicy.POLLING;
    }

    @Override
    public boolean cancel() {
        boolean changed = cancelled.compareAndSet(false, true);
        Thread runningThread = thread.get();
        if (changed && runningThread != null && runningThread.isAlive()) {
            runningThread.interrupt();
        }
        return changed;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public boolean isDone() {
        return done.get();
    }
}
