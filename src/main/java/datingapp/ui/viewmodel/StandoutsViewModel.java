package datingapp.ui.viewmodel;

import datingapp.core.AppSession;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.Standout;
import datingapp.core.model.User;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Standouts screen.
 * Loads today's top standout candidates from {@link RecommendationService} and
 * exposes them for display.
 */
public class StandoutsViewModel {

    private static final Logger logger = LoggerFactory.getLogger(StandoutsViewModel.class);

    private final RecommendationService recommendationService;
    private final AppSession session;

    private final ObservableList<StandoutEntry> standouts = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private User currentUser;
    private ViewModelErrorSink errorHandler;

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
        this.recommendationService =
                Objects.requireNonNull(recommendationService, "recommendationService cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    public void setErrorHandler(ViewModelErrorSink handler) {
        this.errorHandler = handler;
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
        if (disposed.get() || user == null) {
            return;
        }

        setLoadingState(true);
        Thread.ofVirtual().name("standouts-load").start(() -> {
            List<StandoutEntry> entries = List.of();
            String message = "";
            try {
                RecommendationService.Result result = recommendationService.getStandouts(user);
                if (!result.isEmpty()) {
                    Map<UUID, User> resolved = recommendationService.resolveUsers(result.standouts());
                    entries = result.standouts().stream()
                            .filter(s -> resolved.containsKey(s.standoutUserId()))
                            .map(s -> new StandoutEntry(s, resolved.get(s.standoutUserId())))
                            .toList();
                } else {
                    message = result.message() != null ? result.message() : "No standouts today. Check back tomorrow!";
                }
            } catch (Exception e) {
                logWarn("Failed to load standouts", e);
                message = "Could not load standouts. Please try again.";
                notifyError(message);
            }

            List<StandoutEntry> finalEntries = entries;
            String finalMessage = message;
            runOnFx(() -> {
                if (!disposed.get()) {
                    standouts.setAll(finalEntries);
                    statusMessage.set(finalMessage);
                }
                setLoadingState(false);
            });
        });
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
        Thread.ofVirtual().name("standouts-interact").start(() -> {
            try {
                recommendationService.markInteracted(user.getId(), entry.userId());
            } catch (Exception e) {
                logWarn("Failed to mark standout as interacted", e);
            }
        });
    }

    /** Disposes resources. Should be called when the ViewModel is no longer needed. */
    public void dispose() {
        disposed.set(true);
        standouts.clear();
        setLoadingState(false);
    }

    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = session.getCurrentUser();
        }
        return currentUser;
    }

    private void notifyError(String message) {
        if (errorHandler != null) {
            runOnFx(() -> errorHandler.onError(message));
        }
    }

    private void setLoadingState(boolean isLoading) {
        runOnFx(() -> {
            if (loading.get() != isLoading) {
                loading.set(isLoading);
            }
        });
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    // --- Properties ---

    public ObservableList<StandoutEntry> getStandouts() {
        return standouts;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }
}
