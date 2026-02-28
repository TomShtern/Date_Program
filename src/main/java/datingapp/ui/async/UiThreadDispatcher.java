package datingapp.ui.async;

/**
 * Abstraction for dispatching work to the UI thread.
 */
public interface UiThreadDispatcher {

    /**
     * Returns {@code true} if the current thread is the UI thread.
     */
    boolean isUiThread();

    /**
     * Dispatches the action to the UI thread.
     */
    void dispatch(Runnable action);

    /**
     * Runs action immediately on the UI thread, otherwise dispatches it.
     */
    default void dispatchIfNeeded(Runnable action) {
        if (isUiThread()) {
            action.run();
            return;
        }
        dispatch(action);
    }
}
