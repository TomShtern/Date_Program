package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.matching.MatchingUseCases.StandoutInteractionCommand;
import datingapp.app.usecase.matching.MatchingUseCases.StandoutsQuery;
import datingapp.core.AppSession;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.model.User;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * ViewModel for the Standouts screen.
 * Loads today's top standout candidates from {@link RecommendationService} and
 * exposes them for display.
 */
public class StandoutsViewModel extends BaseViewModel {

    private final RecommendationService recommendationService;
    private final MatchingUseCases matchingUseCases;
    private final AppSession session;

    private final ObservableList<StandoutEntry> standouts = FXCollections.observableArrayList();
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private User currentUser;

    /** Combines a {@link Standout} with its resolved {@link User} for display. */
    public record StandoutEntry(Standout standout, User user) {

        public String displayName() {
            return user.getName();
        }

        public int rank() {
            return standout.rank();
        }

        public int score() {
            return standout.score();
        }

        public String reason() {
            return standout.reason();
        }

        public UUID userId() {
            return user.getId();
        }
    }

    public StandoutsViewModel(RecommendationService recommendationService, AppSession session) {
        this(recommendationService, null, session, new JavaFxUiThreadDispatcher());
    }

    public StandoutsViewModel(
            RecommendationService recommendationService, MatchingUseCases matchingUseCases, AppSession session) {
        this(recommendationService, matchingUseCases, session, new JavaFxUiThreadDispatcher());
    }

    public StandoutsViewModel(
            RecommendationService recommendationService,
            MatchingUseCases matchingUseCases,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        super("standouts", uiDispatcher);
        this.recommendationService =
                Objects.requireNonNull(recommendationService, "recommendationService cannot be null");
        this.matchingUseCases = matchingUseCases;
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public final void setErrorHandler(ViewModelErrorSink handler) {
        setErrorSink(handler);
    }

    /** Initializes the ViewModel by loading the current user and fetching standouts. */
    public void initialize() {
        if (ensureCurrentUser() != null) {
            loadStandouts();
        }
    }

    /** Fetches today's standouts for the current user. Safe to call as a manual refresh. */
    public void loadStandouts() {
        User user = ensureCurrentUser();
        if (asyncScope.isDisposed() || user == null) {
            return;
        }

        asyncScope.runLatest("standouts-load", "load standouts", () -> loadStandoutsData(user), data -> {
            standouts.setAll(data.entries());
            statusMessage.set(data.statusMessage());
        });
    }

    private StandoutsData loadStandoutsData(User user) {
        List<StandoutEntry> entries = List.of();
        String message = "";
        try {
            List<Standout> standoutItems;
            Map<UUID, User> resolved;
            boolean empty;
            String resultMessage;
            if (matchingUseCases != null) {
                var useCaseResult = matchingUseCases.standouts(new StandoutsQuery(UserContext.ui(user.getId()), user));
                if (useCaseResult.success()) {
                    standoutItems = useCaseResult.data().result().standouts();
                    resolved = useCaseResult.data().usersById();
                    empty = useCaseResult.data().result().isEmpty();
                    resultMessage = useCaseResult.data().result().message();
                } else {
                    RecommendationService.Result result = recommendationService.getStandouts(user);
                    standoutItems = result.standouts();
                    resolved = recommendationService.resolveUsers(standoutItems);
                    empty = result.isEmpty();
                    resultMessage = result.message();
                }
            } else {
                RecommendationService.Result result = recommendationService.getStandouts(user);
                standoutItems = result.standouts();
                resolved = recommendationService.resolveUsers(standoutItems);
                empty = result.isEmpty();
                resultMessage = result.message();
            }

            if (!empty) {
                entries = standoutItems.stream()
                        .filter(s -> resolved.containsKey(s.standoutUserId()))
                        .map(s -> new StandoutEntry(s, resolved.get(s.standoutUserId())))
                        .toList();
            } else {
                message = resultMessage != null ? resultMessage : "No standouts today. Check back tomorrow!";
            }
        } catch (Exception _) {
            logger.warn("Failed to load standouts");
            message = "Could not load standouts. Please try again.";
            notifyError(message);
        }

        return new StandoutsData(entries, message);
    }

    /**
     * Marks a standout as interacted (e.g. after the user taps to view their profile).
     * Runs silently in the background — does not affect UI state.
     */
    public void markInteracted(StandoutEntry entry) {
        User user = ensureCurrentUser();
        if (user == null || entry == null) {
            return;
        }
        asyncScope.runFireAndForget("mark standout interacted", () -> {
            try {
                if (matchingUseCases != null) {
                    matchingUseCases.markStandoutInteracted(
                            new StandoutInteractionCommand(UserContext.ui(user.getId()), entry.userId()));
                } else {
                    recommendationService.markInteracted(user.getId(), entry.userId());
                }
            } catch (Exception _) {
                logger.warn("Failed to mark standout as interacted");
            }
        });
    }

    @Override
    protected void onDispose() {
        standouts.clear();
    }

    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = session.getCurrentUser();
        }
        return currentUser;
    }

    private void notifyError(String message) {
        notifyError(message, null);
    }

    // --- Properties ---

    public ObservableList<StandoutEntry> getStandouts() {
        return standouts;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    private record StandoutsData(List<StandoutEntry> entries, String statusMessage) {}
}
