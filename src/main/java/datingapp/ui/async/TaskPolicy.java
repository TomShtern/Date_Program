package datingapp.ui.async;

/**
 * Standard policies for asynchronous ViewModel tasks.
 */
public enum TaskPolicy {
    STANDARD(true),
    LATEST_WINS(true),
    FIRE_AND_FORGET(false);

    private final boolean trackLoading;

    TaskPolicy(boolean trackLoading) {
        this.trackLoading = trackLoading;
    }

    public boolean trackLoading() {
        return trackLoading;
    }
}
