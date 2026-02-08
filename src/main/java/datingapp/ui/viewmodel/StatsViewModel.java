package datingapp.ui.viewmodel;

import datingapp.core.Achievement;
import datingapp.core.Achievement.UserAchievement;
import datingapp.core.AchievementService;
import datingapp.core.AppSession;
import datingapp.core.Match;
import datingapp.core.User;
import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final AchievementService achievementService;
    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;

    private final ObservableList<Achievement> achievements = FXCollections.observableArrayList();

    // Stats properties
    private final IntegerProperty totalLikesGiven = new SimpleIntegerProperty(0);
    private final IntegerProperty totalLikesReceived = new SimpleIntegerProperty(0);
    private final IntegerProperty totalMatches = new SimpleIntegerProperty(0);
    private final StringProperty responseRate = new SimpleStringProperty("--");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    private User currentUser;

    /** Track disposed state to prevent operations after cleanup. */
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    /** Error handler for ViewModel→Controller error communication (M-22). */
    private ErrorHandler errorHandler;

    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    public StatsViewModel(AchievementService achievementService) {
        // Backward compatible constructor for when ViewModelFactory doesn't have all
        // services
        this.achievementService = achievementService;
        this.likeStorage = null;
        this.matchStorage = null;
    }

    public StatsViewModel(AchievementService achievementService, LikeStorage likeStorage, MatchStorage matchStorage) {
        this.achievementService = achievementService;
        this.likeStorage = likeStorage;
        this.matchStorage = matchStorage;
    }

    /**
     * Gets the current user from UISession if not set.
     */
    private User ensureCurrentUser() {
        if (currentUser == null) {
            currentUser = AppSession.getInstance().getCurrentUser();
        }
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
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
        if (disposed.get() || ensureCurrentUser() == null) {
            return;
        }

        loading.set(true);
        Thread.ofVirtual().start(() -> {
            try {
                List<Achievement> achievementList = fetchAchievements(currentUser.getId());
                StatsData stats = fetchStats(currentUser.getId());

                javafx.application.Platform.runLater(() -> {
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
                        logger.info("Refreshed stats for user: {}", currentUser.getName());
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
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
        int likesGiven = 0;
        int likesReceived = 0;
        int matchesCount = 0;
        String rateText = "--";

        // M-22: Log when storage is unavailable instead of silent degradation
        if (likeStorage == null) {
            if (logger.isWarnEnabled()) {
                logger.warn("LikeStorage is null - stats will show default values");
            }
            return new StatsData(likesGiven, likesReceived, matchesCount, rateText);
        }

        likesGiven = likeStorage.countByDirection(userId, Like.Direction.LIKE);
        likesReceived = likeStorage.countReceivedByDirection(userId, Like.Direction.LIKE);

        if (matchStorage == null) {
            if (logger.isWarnEnabled()) {
                logger.warn("MatchStorage is null - match count and rate will show default values");
            }
            return new StatsData(likesGiven, likesReceived, matchesCount, rateText);
        }

        List<Match> matches = matchStorage.getActiveMatchesFor(userId);
        matchesCount = matches.size();

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
        if (errorHandler == null) {
            return;
        }
        String detail = e.getMessage();
        String message = detail == null || detail.isBlank() ? userMessage : userMessage + ": " + detail;
        if (javafx.application.Platform.isFxApplicationThread()) {
            errorHandler.onError(message);
        } else {
            javafx.application.Platform.runLater(() -> errorHandler.onError(message));
        }
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        achievements.clear();
    }
}
