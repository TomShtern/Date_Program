package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppSession;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * ViewModel for the moderation admin screen. Lists UNDER_REVIEW users with report counts and
 * exposes ban / dismiss / uphold actions that delegate to {@link TrustSafetyService}.
 */
public final class AdminViewModel extends BaseViewModel {

    private final TrustSafetyService trustSafetyService;
    private final ProfileUseCases profileUseCases;
    private final AppSession session;
    private final ObservableList<PendingReport> pendingReports = FXCollections.observableArrayList();
    private final StringProperty statusMessage = new SimpleStringProperty("");

    /** A user currently under moderation review, with the number of reports filed against them. */
    public record PendingReport(UUID userId, String name, int reportCount) {}

    public AdminViewModel(TrustSafetyService trustSafetyService, ProfileUseCases profileUseCases, AppSession session) {
        this(trustSafetyService, profileUseCases, session, new JavaFxUiThreadDispatcher());
    }

    public AdminViewModel(
            TrustSafetyService trustSafetyService,
            ProfileUseCases profileUseCases,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        super("admin", uiDispatcher);
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService, "trustSafetyService");
        this.profileUseCases = Objects.requireNonNull(profileUseCases, "profileUseCases");
        this.session = Objects.requireNonNull(session, "session");
    }

    public ObservableList<PendingReport> getPendingReports() {
        return pendingReports;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public void setErrorHandler(ViewModelErrorSink errorSink) {
        setErrorSink(errorSink);
    }

    public void refresh() {
        asyncScope.runFireAndForget("load pending reports", () -> {
            UseCaseResult<List<User>> result = profileUseCases.listUsers();
            if (!result.success()) {
                asyncScope.dispatchToUi(() -> statusMessage.set(result.error().message()));
                return;
            }
            List<PendingReport> reports = result.data().stream()
                    .filter(user -> user.getState() == User.UserState.UNDER_REVIEW)
                    .map(user -> new PendingReport(
                            user.getId(), user.getName(), trustSafetyService.countReportsAgainst(user.getId())))
                    .toList();
            asyncScope.dispatchToUi(() -> {
                pendingReports.setAll(reports);
                statusMessage.set(reports.isEmpty() ? "No pending reviews." : reports.size() + " pending review(s).");
            });
        });
    }

    public void ban(PendingReport report) {
        act(report, "ban", trustSafetyService::adminBan, "Banned");
    }

    public void dismiss(PendingReport report) {
        act(report, "dismiss", trustSafetyService::adminDismiss, "Dismissed");
    }

    public void uphold(PendingReport report) {
        act(report, "uphold", trustSafetyService::adminUphold, "Upheld");
    }

    private void act(PendingReport report, String action, BiConsumer<UUID, UUID> call, String successLabel) {
        if (report == null) {
            return;
        }
        User current = session.getCurrentUser();
        if (current == null) {
            statusMessage.set("Sign in as an admin first.");
            return;
        }
        UUID adminId = current.getId();
        asyncScope.runFireAndForget("admin " + action, () -> {
            try {
                call.accept(adminId, report.userId());
                asyncScope.dispatchToUi(() -> {
                    statusMessage.set(successLabel + " " + report.name() + ".");
                    refresh();
                });
            } catch (Exception e) {
                asyncScope.dispatchToUi(() -> statusMessage.set("Admin action failed."));
                notifyError("Admin action failed.", e);
            }
        });
    }
}
