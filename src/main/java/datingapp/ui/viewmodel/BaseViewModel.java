package datingapp.ui.viewmodel;

import datingapp.ui.async.AsyncErrorRouter;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.async.ViewModelAsyncScope;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all ViewModels in the application.
 * Provides common functionality for async operations, logging, and lifecycle management.
 */
public abstract class BaseViewModel {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ViewModelAsyncScope asyncScope;
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    /**
     * Initializes the BaseViewModel with a default error router.
     *
     * @param scopeName name for the async scope (used in logging and thread naming)
     * @param uiDispatcher the dispatcher for UI thread operations
     */
    protected BaseViewModel(String scopeName, UiThreadDispatcher uiDispatcher) {
        this(scopeName, uiDispatcher, null);
    }

    /**
     * Initializes the BaseViewModel with a specific error sink.
     *
     * @param scopeName name for the async scope
     * @param uiDispatcher the dispatcher for UI thread operations
     * @param errorSink optional sink for async errors
     */
    protected BaseViewModel(String scopeName, UiThreadDispatcher uiDispatcher, ViewModelErrorSink errorSink) {
        Objects.requireNonNull(scopeName, "scopeName cannot be null");
        UiThreadDispatcher dispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher cannot be null");

        this.asyncScope = new ViewModelAsyncScope(
                scopeName, dispatcher, new AsyncErrorRouter(logger, dispatcher, () -> errorSink));
        this.asyncScope.setLoadingStateConsumer(this.loading::set);
    }

    /**
     * Returns the loading state property for UI binding.
     */
    public final BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Updates the loading state.
     */
    protected final void setLoadingState(boolean isLoading) {
        if (loading.get() != isLoading) {
            loading.set(isLoading);
        }
    }

    /**
     * Disposes of resources held by the ViewModel.
     * Cancels any pending async operations.
     */
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            asyncScope.dispose();
            onDispose();
        }
    }

    /**
     * Hook for subclasses to perform additional cleanup during disposal.
     */
    protected void onDispose() {
        // Optional hook for subclasses
    }

    /**
     * Returns true if the ViewModel has been disposed.
     */
    protected final boolean isDisposed() {
        return disposed.get();
    }

    /**
     * Helper for debug level logging.
     */
    protected final void logDebug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, args);
        }
    }

    /**
     * Helper for info level logging.
     */
    protected final void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    /**
     * Helper for warning level logging.
     */
    protected final void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    /**
     * Helper for error level logging.
     */
    protected final void logError(String message, Throwable error) {
        if (logger.isErrorEnabled()) {
            logger.error(message, error);
        }
    }
}
