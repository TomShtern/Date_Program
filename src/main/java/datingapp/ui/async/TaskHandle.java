package datingapp.ui.async;

/**
 * Handle for a task managed by {@link ViewModelAsyncScope}.
 */
public interface TaskHandle {

    String taskName();

    TaskPolicy policy();

    /**
     * Attempts to cancel the task.
     *
     * @return {@code true} when cancellation request was accepted
     */
    boolean cancel();

    boolean isCancelled();

    boolean isDone();
}
