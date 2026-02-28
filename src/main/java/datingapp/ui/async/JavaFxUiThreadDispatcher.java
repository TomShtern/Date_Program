package datingapp.ui.async;

import java.util.Objects;
import javafx.application.Platform;

/**
 * JavaFX implementation of {@link UiThreadDispatcher}.
 */
public final class JavaFxUiThreadDispatcher implements UiThreadDispatcher {

    @Override
    public boolean isUiThread() {
        try {
            return Platform.isFxApplicationThread();
        } catch (IllegalStateException _) {
            // JavaFX toolkit is not initialized (typically unit tests).
            return true;
        }
    }

    @Override
    public void dispatch(Runnable action) {
        Objects.requireNonNull(action, "action cannot be null");
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
                return;
            }
            Platform.runLater(action);
        } catch (IllegalStateException _) {
            // JavaFX toolkit not initialized. Fall back to direct execution.
            action.run();
        }
    }
}
