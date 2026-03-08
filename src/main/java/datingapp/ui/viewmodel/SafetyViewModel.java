package datingapp.ui.viewmodel;

import datingapp.core.AppSession;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** ViewModel for the Safety & Privacy screen. */
public final class SafetyViewModel extends BaseViewModel {

    private final TrustSafetyService trustSafetyService;
    private final AppSession session;
    private final ObservableList<BlockedUserEntry> blockedUsers = FXCollections.observableArrayList();
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private ViewModelErrorSink errorHandler;

    public record BlockedUserEntry(UUID userId, String name, String blockedAtLabel) {}

    public SafetyViewModel(TrustSafetyService trustSafetyService, AppSession session) {
        this(trustSafetyService, session, new JavaFxUiThreadDispatcher());
    }

    public SafetyViewModel(TrustSafetyService trustSafetyService, AppSession session, UiThreadDispatcher uiDispatcher) {
        super("safety", uiDispatcher);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService, "trustSafetyService cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public void initialize() {
        loadBlockedUsers();
    }

    public void setErrorHandler(ViewModelErrorSink errorHandler) {
        this.errorHandler = errorHandler;
    }

    public ObservableList<BlockedUserEntry> getBlockedUsers() {
        return blockedUsers;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public void clearStatusMessage() {
        statusMessage.set("");
    }

    public void loadBlockedUsers() {
        if (isDisposed()) {
            return;
        }
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            blockedUsers.clear();
            return;
        }
        asyncScope.runLatest(
                "safety-load",
                "load blocked users",
                () -> {
                    try {
                        return mapBlockedUsers(trustSafetyService.getBlockedUsers(currentUser.getId()));
                    } catch (RuntimeException ex) {
                        reportError(ex.getMessage());
                        return List.of();
                    }
                },
                loadedEntries -> blockedUsers.setAll(loadedEntries.toArray(BlockedUserEntry[]::new)));
    }

    public void unblockUser(UUID blockedUserId) {
        if (isDisposed() || blockedUserId == null) {
            return;
        }
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            blockedUsers.clear();
            return;
        }

        asyncScope.runLatest(
                "safety-unblock",
                "unblock user",
                () -> {
                    try {
                        boolean success = trustSafetyService.unblock(currentUser.getId(), blockedUserId);
                        if (!success) {
                            throw new IllegalStateException("Unable to unblock that user right now.");
                        }
                        List<BlockedUserEntry> updatedEntries =
                                mapBlockedUsers(trustSafetyService.getBlockedUsers(currentUser.getId()));
                        String blockedUserName = blockedUsers.stream()
                                .filter(entry -> entry.userId().equals(blockedUserId))
                                .map(BlockedUserEntry::name)
                                .findFirst()
                                .orElse("User");
                        return new UnblockResult(updatedEntries, blockedUserName + " has been unblocked.");
                    } catch (RuntimeException ex) {
                        reportError(ex.getMessage());
                        return new UnblockResult(blockedUsers.stream().toList(), "");
                    }
                },
                result -> {
                    blockedUsers.setAll(result.entries().toArray(BlockedUserEntry[]::new));
                    if (!result.message().isBlank()) {
                        statusMessage.set(result.message());
                    }
                });
    }

    @Override
    protected void onDispose() {
        blockedUsers.clear();
    }

    private List<BlockedUserEntry> mapBlockedUsers(List<User> blocked) {
        return blocked.stream()
                .map(user -> new BlockedUserEntry(user.getId(), user.getName(), "Blocked profile"))
                .toList();
    }

    private void reportError(String message) {
        if (errorHandler != null && message != null && !message.isBlank()) {
            asyncScope.dispatchToUi(() -> errorHandler.onError(message));
        }
    }

    private record UnblockResult(List<BlockedUserEntry> entries, String message) {}
}
