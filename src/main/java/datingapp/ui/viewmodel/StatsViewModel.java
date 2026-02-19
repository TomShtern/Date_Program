package datingapp.ui.viewmodel;

import datingapp.core.AppSession;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Stats and Achievements screen.
 * Displays user progress, match statistics, and earned achievements.
 */
public class StatsViewModel {
    private static final Logger logger = LoggerFactory.getLogger(StatsViewModel.class);

    private final ProfileService achievementService;
    private final ActivityMetricsService statsService;
    private final AppSession session;

    private final ObservableList<Achievement> achievements = FXCollections.observableArrayList();

    // Stats properties
    private final IntegerProperty totalLikesGiven = new SimpleIntegerProperty(0);
    private final IntegerProperty totalLikesReceived = new SimpleIntegerProperty(0);
    private final IntegerProperty totalMatches = new SimpleIntegerProperty(0);
    private final StringProperty responseRate = new SimpleStringProperty("--");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    private final AtomicReference<User> currentUser = new AtomicReference<>();

    /** Track disposed state to prevent operations after cleanup. */
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    /** Error handler for ViewModel→Controller error communication (M-22). */
    private final AtomicReference<ViewModelErrorSink> errorHandler = new AtomicReference<>();

    public void setErrorHandler(ViewModelErrorSink handler) {
        this.errorHandler.set(handler);
    }

    public StatsViewModel(ProfileService achievementService, ActivityMetricsService statsService, AppSession session) {
        this.achievementService = Objects.requireNonNull(achievementService, "achievementService cannot be null");
        this.statsService = Objects.requireNonNull(statsService, "statsService cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
    }

    /**
     * Gets the current user from UISession if not set.
     */
    private User ensureCurrentUser() {
        User user = currentUser.get();
        if (user == null) {
            user = session.getCurrentUser();
            if (user != null) {
                currentUser.set(user);
            }
        }
        return user;
    }

    public void setCurrentUser(User user) {
        this.currentUser.set(user);
        refresh();
    }

    /**
     * Initialize from UISession.
     */
    public void initialize() {
        if (ensureCurrentUser() != null) {
            refresh();
        }
    }

    public void refresh() {
        User user = ensureCurrentUser();
        if (disposed.get() || user == null) {
            return;
        }

        java.util.UUID userId = user.getId();
        loading.set(true);
        Thread.ofVirtual().start(() -> {
            try {
                List<Achievement> achievementList = fetchAchievements(userId);
                StatsData stats = fetchStats(userId);

                Platform.runLater(() -> {
                    if (disposed.get()) {
                        return;
                    }
                    achievements.setAll(achievementList);
                    totalLikesGiven.set(stats.likesGiven());
                    totalLikesReceived.set(stats.likesReceived());
                    totalMatches.set(stats.matchesCount());
                    responseRate.set(stats.rateText());
                    loading.set(false);
                    if (logger.isInfoEnabled()) {
                        logger.info("Refreshed stats for user: {}", maskUserIdentifier(user));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loading.set(false);
                    notifyError("Failed to refresh stats", e);
                });
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to refresh stats: {}", e.getMessage(), e);
                }
            }
        });
    }

    private List<Achievement> fetchAchievements(java.util.UUID userId) {
        List<UserAchievement> earned = achievementService.getUnlocked(userId);
        return earned.stream().map(UserAchievement::achievement).toList();
    }

    private StatsData fetchStats(java.util.UUID userId) {
        UserStats stats = statsService.getOrComputeStats(userId);

        int likesGiven = stats.likesGiven();
        int likesReceived = stats.likesReceived();
        int matchesCount = stats.activeMatches();

        String rateText = "--";
        if (likesReceived > 0) {
            double rate = (double) matchesCount / likesReceived * 100;
            rateText = String.format("%.0f%%", Math.min(rate, 100));
        }

        return new StatsData(likesGiven, likesReceived, matchesCount, rateText);
    }

    private record StatsData(int likesGiven, int likesReceived, int matchesCount, String rateText) {}

    // --- Properties ---
    public ObservableList<Achievement> getAchievements() {
        return achievements;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public IntegerProperty totalLikesGivenProperty() {
        return totalLikesGiven;
    }

    public IntegerProperty totalLikesReceivedProperty() {
        return totalLikesReceived;
    }

    public IntegerProperty totalMatchesProperty() {
        return totalMatches;
    }

    public StringProperty responseRateProperty() {
        return responseRate;
    }

    private void notifyError(String userMessage, Exception e) {
        ViewModelErrorSink handler = errorHandler.get();
        if (handler == null) {
            return;
        }
        String detail = e.getMessage();
        String message = detail == null || detail.isBlank() ? userMessage : userMessage + ": " + detail;
        if (Platform.isFxApplicationThread()) {
            handler.onError(message);
        } else {
            Platform.runLater(() -> handler.onError(message));
        }
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        if (Platform.isFxApplicationThread()) {
            achievements.clear();
        } else {
            Platform.runLater(achievements::clear);
        }
    }

    private String maskUserIdentifier(User user) {
        if (user == null || user.getId() == null) {
            return "unknown";
        }
        return Integer.toHexString(user.getId().toString().hashCode());
    }
}
