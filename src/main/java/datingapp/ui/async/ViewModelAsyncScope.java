package datingapp.ui.async;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Shared async scope for ViewModels.
 *
 * <p>
 * Provides:
 * </p>
 * <ul>
 * <li>Virtual-thread background execution</li>
 * <li>UI-thread callback dispatching</li>
 * <li>Keyed latest-wins behavior</li>
 * <li>Task cancellation and disposal</li>
 * <li>Standard loading-state tracking</li>
 * </ul>
 */
public final class ViewModelAsyncScope {

    private static final String ERR_BACKGROUND_WORK_NULL = "backgroundWork cannot be null";
    private static final String ERR_TASK_KEY_NULL = "taskKey cannot be null";

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get();
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run();
    }

    private final String scopeName;
    private final UiThreadDispatcher dispatcher;
    private final AsyncErrorRouter errorRouter;

    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicInteger loadingCount = new AtomicInteger(0);

    private final Set<TaskHandle> activeHandles = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, AtomicLong> latestVersions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TaskHandle> latestHandles = new ConcurrentHashMap<>();
    private final Object keyedTaskRegistrationLock = new Object();

    private final AtomicReference<Consumer<Boolean>> loadingStateConsumer = new AtomicReference<>();
    private final DiagnosticsTracker diagnostics = new DiagnosticsTracker();

    public ViewModelAsyncScope(String scopeName, UiThreadDispatcher dispatcher, AsyncErrorRouter errorRouter) {
        this.scopeName = Objects.requireNonNull(scopeName, "scopeName cannot be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        this.errorRouter = Objects.requireNonNull(errorRouter, "errorRouter cannot be null");
    }

    public void setLoadingStateConsumer(Consumer<Boolean> loadingStateConsumer) {
        this.loadingStateConsumer.set(loadingStateConsumer);
        if (loadingStateConsumer != null) {
            dispatchToUi(() -> loadingStateConsumer.accept(loadingCount.get() > 0));
        }
    }

    public boolean isDisposed() {
        return disposed.get();
    }

    public DiagnosticsSnapshot diagnosticsSnapshot() {
        return diagnostics.snapshot();
    }

    public void onError(String taskName, Throwable throwable) {
        if (isDisposed()) {
            return;
        }
        diagnostics.recordRoutedError();
        errorRouter.onError(taskName, throwable);
    }

    public void dispatchToUi(Runnable action) {
        dispatcher.dispatchIfNeeded(action);
    }

    public <T> TaskHandle run(String taskName, ThrowingSupplier<T> backgroundWork, Consumer<T> onSuccess) {
        return launch(taskName, null, TaskPolicy.STANDARD, backgroundWork, onSuccess);
    }

    public <T> TaskHandle runLatest(
            String taskKey, String taskName, ThrowingSupplier<T> backgroundWork, Consumer<T> onSuccess) {
        Objects.requireNonNull(taskKey, ERR_TASK_KEY_NULL);
        return launch(taskName, taskKey, TaskPolicy.LATEST_WINS, backgroundWork, onSuccess);
    }

    public <T> TaskHandle runLatestSilently(
            String taskKey, String taskName, ThrowingSupplier<T> backgroundWork, Consumer<T> onSuccess) {
        Objects.requireNonNull(taskKey, ERR_TASK_KEY_NULL);
        return launch(taskName, taskKey, TaskPolicy.LATEST_WINS_SILENT, backgroundWork, onSuccess);
    }

    public TaskHandle runFireAndForget(String taskName, ThrowingRunnable backgroundWork) {
        Objects.requireNonNull(backgroundWork, ERR_BACKGROUND_WORK_NULL);
        return launch(
                taskName,
                null,
                TaskPolicy.FIRE_AND_FORGET,
                () -> {
                    backgroundWork.run();
                    return null;
                },
                _ -> {
                    // Intentionally ignored.
                });
    }

    public TaskHandle runPolling(String taskKey, String taskName, Duration interval, ThrowingRunnable backgroundWork) {
        Objects.requireNonNull(taskKey, ERR_TASK_KEY_NULL);
        Objects.requireNonNull(interval, "interval cannot be null");
        Objects.requireNonNull(backgroundWork, ERR_BACKGROUND_WORK_NULL);
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }

        String safeTaskName = normalizeTaskName(taskName);
        if (isDisposed()) {
            diagnostics.recordCancelledBeforeDelivery();
            return PollingTaskHandle.cancelled(safeTaskName, interval);
        }

        PollingTaskHandle handle = new PollingTaskHandle(safeTaskName, interval);
        activeHandles.add(handle);

        long version = registerLatestTask(taskKey, TaskPolicy.POLLING, handle);

        Runnable execution = () -> executePollingTask(handle, taskKey, version, safeTaskName, backgroundWork);
        Thread thread = Thread.ofVirtual().name(threadName(safeTaskName)).unstarted(execution);
        handle.bindThread(thread);
        thread.start();
        return handle;
    }

    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }

        latestVersions.values().forEach(AtomicLong::incrementAndGet);
        activeHandles.forEach(TaskHandle::cancel);
        activeHandles.clear();
        latestHandles.clear();
        loadingCount.set(0);
        pushLoadingState(false);
    }

    private <T> TaskHandle launch(
            String taskName,
            String taskKey,
            TaskPolicy policy,
            ThrowingSupplier<T> backgroundWork,
            Consumer<T> onSuccess) {
        Objects.requireNonNull(backgroundWork, ERR_BACKGROUND_WORK_NULL);
        Objects.requireNonNull(onSuccess, "onSuccess cannot be null");
        String safeTaskName = normalizeTaskName(taskName);

        if (isDisposed()) {
            diagnostics.recordCancelledBeforeDelivery();
            return ScopeTaskHandle.cancelled(safeTaskName, policy);
        }

        ScopeTaskHandle handle = new ScopeTaskHandle(safeTaskName, policy);
        activeHandles.add(handle);

        if (policy.trackLoading()) {
            handle.markLoadingStarted();
            beginLoading();
        }

        long version = registerLatestTask(taskKey, policy, handle);

        Runnable execution =
                () -> executeTask(handle, taskKey, version, safeTaskName, policy, backgroundWork, onSuccess);
        Thread thread = Thread.ofVirtual().name(threadName(safeTaskName)).unstarted(execution);
        handle.bindThread(thread);
        thread.start();
        return handle;
    }

    private long registerLatestTask(String taskKey, TaskPolicy policy, TaskHandle handle) {
        if (taskKey == null || !policy.isKeyed()) {
            return -1L;
        }
        synchronized (keyedTaskRegistrationLock) {
            long version = nextVersion(taskKey);
            TaskHandle previous = latestHandles.put(taskKey, handle);
            if (previous != null) {
                diagnostics.recordSupersededTask();
                previous.cancel();
                if (previous instanceof ScopeTaskHandle previousHandle && previousHandle.releaseLoading()) {
                    endLoading();
                }
            }
            return version;
        }
    }

    private <T> void executeTask(
            ScopeTaskHandle handle,
            String taskKey,
            long version,
            String taskName,
            TaskPolicy policy,
            ThrowingSupplier<T> backgroundWork,
            Consumer<T> onSuccess) {
        try {
            if (!canDeliverOrRecordCancelledBeforeDelivery(handle, taskKey, version)) {
                return;
            }

            T result = backgroundWork.get();

            if (!canDeliverOrRecordCancelledBeforeDelivery(handle, taskKey, version)) {
                return;
            }

            dispatchToUi(() -> {
                if (!canDeliverOrRecordCallbackSuppressed(handle, taskKey, version)) {
                    return;
                }
                try {
                    onSuccess.accept(result);
                } catch (Exception callbackError) {
                    onError(taskName, callbackError);
                }
            });
        } catch (RuntimeException error) {
            if (canDeliver(handle, taskKey, version)) {
                onError(taskName, error);
            } else {
                diagnostics.recordCancelledBeforeDelivery();
            }
        } finally {
            handle.markDone();
            activeHandles.remove(handle);
            if (taskKey != null && latestHandles.remove(taskKey, handle)) {
                latestVersions.remove(taskKey);
            }
            if (policy.trackLoading() && handle.releaseLoading()) {
                endLoading();
            }
        }
    }

    private void executePollingTask(
            PollingTaskHandle handle, String taskKey, long version, String taskName, ThrowingRunnable backgroundWork) {
        try {
            while (canDeliver(handle, taskKey, version)) {
                try {
                    backgroundWork.run();
                } catch (RuntimeException error) {
                    if (canDeliver(handle, taskKey, version)) {
                        onError(taskName, error);
                    }
                }

                if (canDeliver(handle, taskKey, version)) {
                    try {
                        Thread.sleep(handle.interval());
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        handle.cancel();
                    }
                }
            }
        } finally {
            if (!canDeliver(handle, taskKey, version)) {
                diagnostics.recordCancelledBeforeDelivery();
            }
            handle.markDone();
            activeHandles.remove(handle);
            if (latestHandles.remove(taskKey, handle)) {
                latestVersions.remove(taskKey);
            }
        }
    }

    private long nextVersion(String taskKey) {
        if (taskKey == null) {
            return -1L;
        }
        AtomicLong version = latestVersions.computeIfAbsent(taskKey, _ -> new AtomicLong(0));
        return version.incrementAndGet();
    }

    private boolean canDeliver(TaskHandle handle, String taskKey, long expectedVersion) {
        if (isDisposed() || handle.isCancelled()) {
            return false;
        }

        if (taskKey == null) {
            return true;
        }

        AtomicLong current = latestVersions.get(taskKey);
        return current != null && current.get() == expectedVersion;
    }

    private boolean canDeliverOrRecordCancelledBeforeDelivery(TaskHandle handle, String taskKey, long expectedVersion) {
        if (canDeliver(handle, taskKey, expectedVersion)) {
            return true;
        }
        diagnostics.recordCancelledBeforeDelivery();
        return false;
    }

    private boolean canDeliverOrRecordCallbackSuppressed(TaskHandle handle, String taskKey, long expectedVersion) {
        if (canDeliver(handle, taskKey, expectedVersion)) {
            return true;
        }
        diagnostics.recordCallbackSuppressed();
        return false;
    }

    private void beginLoading() {
        if (loadingCount.incrementAndGet() == 1) {
            pushLoadingState(true);
        }
    }

    private void endLoading() {
        if (disposed.get()) {
            return;
        }
        int remaining = loadingCount.decrementAndGet();
        if (remaining <= 0) {
            loadingCount.set(0);
            pushLoadingState(false);
        }
    }

    private void pushLoadingState(boolean isLoading) {
        Consumer<Boolean> consumer = loadingStateConsumer.get();
        if (consumer == null) {
            return;
        }
        dispatchToUi(() -> consumer.accept(isLoading));
    }

    private String threadName(String taskName) {
        return scopeName + "-" + taskName;
    }

    private static String normalizeTaskName(String taskName) {
        if (taskName == null || taskName.isBlank()) {
            return "task";
        }
        return taskName.trim();
    }

    public record DiagnosticsSnapshot(
            long supersededTaskCount,
            long cancelledBeforeDeliveryCount,
            long callbackSuppressedCount,
            long routedErrorCount) {}

    private static final class DiagnosticsTracker {
        private final AtomicLong supersededTaskCount = new AtomicLong();
        private final AtomicLong cancelledBeforeDeliveryCount = new AtomicLong();
        private final AtomicLong callbackSuppressedCount = new AtomicLong();
        private final AtomicLong routedErrorCount = new AtomicLong();

        private void recordSupersededTask() {
            supersededTaskCount.incrementAndGet();
        }

        private void recordCancelledBeforeDelivery() {
            cancelledBeforeDeliveryCount.incrementAndGet();
        }

        private void recordCallbackSuppressed() {
            callbackSuppressedCount.incrementAndGet();
        }

        private void recordRoutedError() {
            routedErrorCount.incrementAndGet();
        }

        private DiagnosticsSnapshot snapshot() {
            return new DiagnosticsSnapshot(
                    supersededTaskCount.get(),
                    cancelledBeforeDeliveryCount.get(),
                    callbackSuppressedCount.get(),
                    routedErrorCount.get());
        }
    }

    private static final class ScopeTaskHandle implements TaskHandle {
        private final String taskName;
        private final TaskPolicy policy;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final AtomicBoolean loading = new AtomicBoolean(false);

        private final AtomicReference<Thread> thread = new AtomicReference<>();

        private ScopeTaskHandle(String taskName, TaskPolicy policy) {
            this.taskName = taskName;
            this.policy = policy;
        }

        private static ScopeTaskHandle cancelled(String taskName, TaskPolicy policy) {
            ScopeTaskHandle handle = new ScopeTaskHandle(taskName, policy);
            handle.cancelled.set(true);
            handle.done.set(true);
            return handle;
        }

        private void bindThread(Thread thread) {
            this.thread.set(thread);
        }

        private void markDone() {
            done.set(true);
        }

        private void markLoadingStarted() {
            loading.set(true);
        }

        private boolean releaseLoading() {
            return loading.compareAndSet(true, false);
        }

        @Override
        public String taskName() {
            return taskName;
        }

        @Override
        public TaskPolicy policy() {
            return policy;
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
}
