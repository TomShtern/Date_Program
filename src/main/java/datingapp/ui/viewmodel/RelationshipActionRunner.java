package datingapp.ui.viewmodel;

import datingapp.ui.async.ViewModelAsyncScope;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Executes match relationship actions with the existing async/sync plumbing. */
final class RelationshipActionRunner {

    @FunctionalInterface
    interface WarningLogger {
        void warn(String message, Throwable error);
    }

    record SyncCallbacks(
            Runnable startLoading,
            Runnable stopLoading,
            WarningLogger warningLogger,
            BiConsumer<String, Throwable> notifyError) {

        SyncCallbacks {
            Objects.requireNonNull(startLoading, "startLoading cannot be null");
            Objects.requireNonNull(stopLoading, "stopLoading cannot be null");
            Objects.requireNonNull(warningLogger, "warningLogger cannot be null");
            Objects.requireNonNull(notifyError, "notifyError cannot be null");
        }
    }

    private final ViewModelAsyncScope asyncScope;

    RelationshipActionRunner(ViewModelAsyncScope asyncScope) {
        this.asyncScope = Objects.requireNonNull(asyncScope, "asyncScope cannot be null");
    }

    void run(
            boolean runAsync,
            String taskName,
            String userMessage,
            Runnable action,
            Runnable onSuccess,
            SyncCallbacks syncCallbacks) {
        Objects.requireNonNull(taskName, "taskName cannot be null");
        Objects.requireNonNull(userMessage, "userMessage cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(onSuccess, "onSuccess cannot be null");

        if (runAsync) {
            asyncScope.run(
                    taskName,
                    () -> {
                        action.run();
                        return Boolean.TRUE;
                    },
                    _ -> onSuccess.run());
            return;
        }

        syncCallbacks.startLoading().run();
        try {
            action.run();
            onSuccess.run();
        } catch (Exception e) {
            syncCallbacks.warningLogger().warn(userMessage, e);
            syncCallbacks.notifyError().accept(userMessage, e);
        } finally {
            syncCallbacks.stopLoading().run();
        }
    }
}
