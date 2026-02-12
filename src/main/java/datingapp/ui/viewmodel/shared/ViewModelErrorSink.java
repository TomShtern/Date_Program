package datingapp.ui.viewmodel.shared;

/**
 * Functional contract for ViewModels to report user-facing errors to controllers.
 */
@FunctionalInterface
public interface ViewModelErrorSink {
    void onError(String message);
}
