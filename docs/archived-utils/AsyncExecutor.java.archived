package datingapp.ui.util;

import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized async execution utility for UI operations.
 *
 * <p>Uses JDK 25 Virtual Threads for I/O-bound operations. All UI updates
 * are automatically marshalled to the JavaFX Application Thread.
 *
 * <p>Usage:
 * <pre>
 * AsyncExecutor.execute(
 *     () -> userStorage.findById(id),    // Background task
 *     user -> updateUI(user),            // Success callback
 *     error -> showError(error)          // Error callback (optional)
 * );
 * </pre>
 */
public final class AsyncExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AsyncExecutor.class);
    private static final String ERROR_PREFIX = "Operation failed: ";

    private AsyncExecutor() {
        // Utility class - no instantiation
    }

    /**
     * Executes an I/O operation on a virtual thread with UI callback.
     *
     * @param <T>            Result type
     * @param backgroundTask Supplier that performs blocking I/O (runs on virtual thread)
     * @param onSuccess      Consumer called with result on FX thread
     * @param onError        Consumer called with exception on FX thread (optional, may be null)
     */
    public static <T> void execute(Supplier<T> backgroundTask, Consumer<T> onSuccess, Consumer<Exception> onError) {
        Objects.requireNonNull(backgroundTask, "backgroundTask cannot be null");
        Objects.requireNonNull(onSuccess, "onSuccess cannot be null");

        Thread.ofVirtual().name("async-task").start(() -> {
            try {
                T result = backgroundTask.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception e) {
                // Check if it's an interrupt and restore flag
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                }
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept(e);
                    } else {
                        logger.error("Async task failed", e);
                        ToastService.getInstance().showError(ERROR_PREFIX + e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * Executes an I/O operation on a virtual thread with success callback only.
     * Errors are logged and displayed via ToastService.
     *
     * @param <T>            Result type
     * @param backgroundTask Supplier that performs blocking I/O
     * @param onSuccess      Consumer called with result on FX thread
     */
    public static <T> void execute(Supplier<T> backgroundTask, Consumer<T> onSuccess) {
        execute(backgroundTask, onSuccess, null);
    }

    /**
     * Executes a void I/O operation on a virtual thread.
     *
     * @param backgroundTask Runnable that performs blocking I/O
     * @param onComplete     Runnable called on FX thread when complete (optional)
     * @param onError        Consumer called with exception on FX thread (optional)
     */
    public static void run(Runnable backgroundTask, Runnable onComplete, Consumer<Exception> onError) {
        Objects.requireNonNull(backgroundTask, "backgroundTask cannot be null");

        Thread.ofVirtual().name("async-run").start(() -> {
            try {
                backgroundTask.run();
                if (onComplete != null) {
                    Platform.runLater(onComplete);
                }
            } catch (Exception e) {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                }
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept(e);
                    } else {
                        logger.error("Async run failed", e);
                        ToastService.getInstance().showError(ERROR_PREFIX + e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * Executes multiple parallel tasks using Structured Concurrency (JDK 25).
     * All tasks must succeed for the result handler to be called.
     *
     * @param <A>      First result type
     * @param <B>      Second result type
     * @param taskA    First supplier
     * @param taskB    Second supplier
     * @param onResult BiConsumer called with both results on FX thread
     * @param onError  Consumer called if any task fails (optional)
     */
    public static <A, B> void executeParallel(
            Supplier<A> taskA,
            Supplier<B> taskB,
            java.util.function.BiConsumer<A, B> onResult,
            Consumer<Exception> onError) {
        Objects.requireNonNull(taskA, "taskA cannot be null");
        Objects.requireNonNull(taskB, "taskB cannot be null");
        Objects.requireNonNull(onResult, "onResult cannot be null");

        Thread.ofVirtual().name("async-parallel").start(() -> {
            try (var scope = StructuredTaskScope.open()) {
                var subtaskA = scope.fork(taskA::get);
                var subtaskB = scope.fork(taskB::get);
                scope.join();
                handleParallelResults(subtaskA, subtaskB, onResult, onError);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                notifyError(onError, e);
            } catch (Exception e) {
                handleParallelException(e, onError);
            }
        });
    }

    private static <A, B> void handleParallelResults(
            StructuredTaskScope.Subtask<A> subtaskA,
            StructuredTaskScope.Subtask<B> subtaskB,
            java.util.function.BiConsumer<A, B> onResult,
            Consumer<Exception> onError) {
        boolean bothSucceeded = subtaskA.state() == StructuredTaskScope.Subtask.State.SUCCESS
                && subtaskB.state() == StructuredTaskScope.Subtask.State.SUCCESS;

        if (bothSucceeded) {
            A resultA = subtaskA.get();
            B resultB = subtaskB.get();
            Platform.runLater(() -> onResult.accept(resultA, resultB));
        } else {
            Exception ex = new RuntimeException("One or more parallel tasks failed");
            notifyErrorOrDefault(onError, ex, "Parallel operation failed");
        }
    }

    private static void handleParallelException(Exception e, Consumer<Exception> onError) {
        Platform.runLater(() -> {
            if (onError != null) {
                onError.accept(e);
            } else {
                logger.error("Parallel execution failed", e);
                ToastService.getInstance().showError(ERROR_PREFIX + e.getMessage());
            }
        });
    }

    private static void notifyError(Consumer<Exception> onError, Exception e) {
        if (onError != null) {
            Platform.runLater(() -> onError.accept(e));
        }
    }

    private static void notifyErrorOrDefault(Consumer<Exception> onError, Exception e, String defaultMessage) {
        Platform.runLater(() -> {
            if (onError != null) {
                onError.accept(e);
            } else {
                ToastService.getInstance().showError(defaultMessage);
            }
        });
    }
}
