package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.ProfileUseCases.DeleteAccountCommand;
import datingapp.app.usecase.profile.ProfileUseCases.SaveProfileCommand;
import datingapp.core.AppSession;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.core.model.User.VerificationMethod;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** ViewModel for the Safety & Privacy screen. */
public final class SafetyViewModel extends BaseViewModel {

    private final TrustSafetyService trustSafetyService;
    private final ProfileUseCases profileUseCases;
    private final AppSession session;
    private final ObservableList<BlockedUserEntry> blockedUsers = FXCollections.observableArrayList();
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ObjectProperty<VerificationMethod> verificationMethod =
            new SimpleObjectProperty<>(VerificationMethod.EMAIL);
    private final StringProperty verificationContact = new SimpleStringProperty("");
    private final StringProperty verificationCode = new SimpleStringProperty("");
    private final BooleanProperty accountDeleted = new SimpleBooleanProperty(false);
    private ViewModelErrorSink errorHandler;

    public record BlockedUserEntry(UUID userId, String name, String blockedAtLabel) {}

    public SafetyViewModel(TrustSafetyService trustSafetyService, AppSession session) {
        this(trustSafetyService, null, session, new JavaFxUiThreadDispatcher());
    }

    public SafetyViewModel(TrustSafetyService trustSafetyService, AppSession session, UiThreadDispatcher uiDispatcher) {
        this(trustSafetyService, null, session, uiDispatcher);
    }

    public SafetyViewModel(
            TrustSafetyService trustSafetyService,
            ProfileUseCases profileUseCases,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        super("safety", uiDispatcher);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService, "trustSafetyService cannot be null");
        this.profileUseCases = profileUseCases;
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public void initialize() {
        syncVerificationFieldsFromSession();
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

    public ObjectProperty<VerificationMethod> verificationMethodProperty() {
        return verificationMethod;
    }

    public StringProperty verificationContactProperty() {
        return verificationContact;
    }

    public StringProperty verificationCodeProperty() {
        return verificationCode;
    }

    public BooleanProperty accountDeletedProperty() {
        return accountDeleted;
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

    public void startVerification() {
        if (isDisposed()) {
            return;
        }
        User currentUser = session.getCurrentUser();
        if (currentUser == null || profileUseCases == null) {
            reportError("Profile verification is unavailable right now.");
            return;
        }

        try {
            applyVerificationContact(currentUser);
            String generatedCode = trustSafetyService.generateVerificationCode();
            currentUser.startVerification(verificationMethod.get(), generatedCode);
            var result = profileUseCases.saveProfile(
                    new SaveProfileCommand(UserContext.ui(currentUser.getId()), currentUser));
            if (!result.success()) {
                reportError(result.error().message());
                return;
            }
            session.setCurrentUser(result.data().user());
            syncVerificationFieldsFromSession();
            statusMessage.set("Verification code generated. Local/dev code: " + generatedCode);
        } catch (IllegalArgumentException e) {
            reportError(e.getMessage());
        }
    }

    public void confirmVerification() {
        if (isDisposed()) {
            return;
        }
        User currentUser = session.getCurrentUser();
        if (currentUser == null || profileUseCases == null) {
            reportError("Profile verification is unavailable right now.");
            return;
        }
        if (!trustSafetyService.verifyCode(currentUser, verificationCode.get())) {
            reportError("Verification code is invalid or expired.");
            return;
        }

        currentUser.markVerified();
        var result =
                profileUseCases.saveProfile(new SaveProfileCommand(UserContext.ui(currentUser.getId()), currentUser));
        if (!result.success()) {
            reportError(result.error().message());
            return;
        }
        session.setCurrentUser(result.data().user());
        verificationCode.set("");
        syncVerificationFieldsFromSession();
        statusMessage.set("Profile verified successfully.");
    }

    public void deleteCurrentAccount() {
        if (isDisposed()) {
            return;
        }
        User currentUser = session.getCurrentUser();
        if (currentUser == null || profileUseCases == null) {
            reportError("Account deletion is unavailable right now.");
            return;
        }

        var result = profileUseCases.deleteAccount(
                new DeleteAccountCommand(UserContext.ui(currentUser.getId()), "User-initiated safety screen deletion"));
        if (!result.success()) {
            reportError(result.error().message());
            return;
        }
        blockedUsers.clear();
        session.reset();
        accountDeleted.set(true);
        statusMessage.set("Account deleted. You have been signed out.");
    }

    @Override
    protected void onDispose() {
        blockedUsers.clear();
    }

    private void syncVerificationFieldsFromSession() {
        User currentUser = session.getCurrentUser();
        if (currentUser == null) {
            verificationContact.set("");
            verificationCode.set("");
            return;
        }
        VerificationMethod currentMethod = currentUser.getVerificationMethod();
        if (currentMethod != null) {
            verificationMethod.set(currentMethod);
        }
        verificationContact.set(resolveVerificationContact(currentUser, verificationMethod.get()));
    }

    private void applyVerificationContact(User currentUser) {
        VerificationMethod method =
                verificationMethod.get() == null ? VerificationMethod.EMAIL : verificationMethod.get();
        String contact = verificationContact.get() == null
                ? ""
                : verificationContact.get().trim();
        if (method == VerificationMethod.EMAIL) {
            currentUser.setEmail(contact);
            return;
        }
        currentUser.setPhone(contact);
    }

    private String resolveVerificationContact(User currentUser, VerificationMethod method) {
        return method == VerificationMethod.PHONE
                ? Objects.toString(currentUser.getPhone(), "")
                : Objects.toString(currentUser.getEmail(), "");
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
