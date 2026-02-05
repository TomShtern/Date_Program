package datingapp.ui.viewmodel;

/**
 * Functional interface for ViewModels to report errors to their controllers.
 * Controllers implement this to show toast notifications.
 */
@FunctionalInterface
public interface ErrorHandler {
    void onError(String message);
}
