package datingapp.ui.async;

/**
 * Standard policies for asynchronous ViewModel tasks.
 */
public enum TaskPolicy {
    STANDARD(true, false),
    LATEST_WINS(true, true),
    LATEST_WINS_SILENT(false, true),
    FIRE_AND_FORGET(false, false),
    POLLING(false, true);

    private final boolean trackLoading;
    private final boolean keyed;

    TaskPolicy(boolean trackLoading, boolean keyed) {
        this.trackLoading = trackLoading;
        this.keyed = keyed;
    }

    public boolean trackLoading() {
        return trackLoading;
    }

    public boolean isKeyed() {
        return keyed;
    }
}
