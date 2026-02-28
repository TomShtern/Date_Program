package datingapp.ui.async;

import datingapp.ui.viewmodel.ViewModelErrorSink;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes asynchronous errors consistently for ViewModels.
 *
 * <p>
 * Priority:
 * </p>
 * <ol>
 * <li>{@link ViewModelErrorSink} (user-facing)</li>
 * <li>Logger fallback</li>
 * </ol>
 */
public final class AsyncErrorRouter {

    private final UiThreadDispatcher dispatcher;
    private final Supplier<ViewModelErrorSink> sinkSupplier;
    private final BiConsumer<String, Throwable> fallbackLogger;

    public AsyncErrorRouter(
            Class<?> ownerClass, UiThreadDispatcher dispatcher, Supplier<ViewModelErrorSink> sinkSupplier) {
        this(
                dispatcher,
                sinkSupplier,
                createDefaultLogger(Objects.requireNonNull(ownerClass, "ownerClass cannot be null")));
    }

    public AsyncErrorRouter(Logger logger, UiThreadDispatcher dispatcher, Supplier<ViewModelErrorSink> sinkSupplier) {
        this(dispatcher, sinkSupplier, createDefaultLogger(Objects.requireNonNull(logger, "logger cannot be null")));
    }

    AsyncErrorRouter(
            UiThreadDispatcher dispatcher,
            Supplier<ViewModelErrorSink> sinkSupplier,
            BiConsumer<String, Throwable> fallbackLogger) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        this.sinkSupplier = Objects.requireNonNull(sinkSupplier, "sinkSupplier cannot be null");
        this.fallbackLogger = Objects.requireNonNull(fallbackLogger, "fallbackLogger cannot be null");
    }

    public void onError(String taskName, Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable cannot be null");

        String message = buildMessage(taskName, throwable);
        ViewModelErrorSink sink = sinkSupplier.get();
        if (sink != null) {
            dispatcher.dispatchIfNeeded(() -> sink.onError(message));
            return;
        }

        fallbackLogger.accept(message, throwable);
    }

    private static String buildMessage(String taskName, Throwable throwable) {
        String normalizedTask = taskName == null || taskName.isBlank() ? "background task" : taskName;
        String detail = throwable.getMessage();
        if (detail == null || detail.isBlank()) {
            return "Failed to " + normalizedTask;
        }
        return "Failed to " + normalizedTask + ": " + detail;
    }

    private static BiConsumer<String, Throwable> createDefaultLogger(Class<?> ownerClass) {
        Logger logger = LoggerFactory.getLogger(ownerClass);
        return createDefaultLogger(logger);
    }

    private static BiConsumer<String, Throwable> createDefaultLogger(Logger logger) {
        return (message, throwable) -> {
            if (logger.isWarnEnabled()) {
                logger.warn(message, throwable);
            }
        };
    }
}
