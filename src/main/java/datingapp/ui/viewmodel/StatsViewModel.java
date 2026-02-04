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
import javafx.beans.property.IntegerProperty;
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

    private User currentUser;

    /** Track disposed state to prevent operations after cleanup. */
    private final AtomicBoolean disposed = new AtomicBoolean(false);

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

        logInfo("Refreshing stats for user: {}", currentUser.getName());

        // Load achievements
        List<UserAchievement> earned = achievementService.getUnlocked(currentUser.getId());
        achievements.clear();
        for (UserAchievement ua : earned) {
            achievements.add(ua.achievement());
        }

        // Load stats from storage if available
        if (likeStorage != null) {
            int likesGiven = likeStorage.countByDirection(currentUser.getId(), Like.Direction.LIKE);
            int likesReceived = likeStorage.countReceivedByDirection(currentUser.getId(), Like.Direction.LIKE);
            totalLikesGiven.set(likesGiven);
            totalLikesReceived.set(likesReceived);

            // Calculate response rate (matches / likes received)
            if (matchStorage != null) {
                List<Match> matches = matchStorage.getActiveMatchesFor(currentUser.getId());
                totalMatches.set(matches.size());

                if (likesReceived > 0) {
                    double rate = (double) matches.size() / likesReceived * 100;
                    responseRate.set(String.format("%.0f%%", Math.min(rate, 100)));
                } else {
                    responseRate.set("--");
                }
            }
        }
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    // --- Properties ---
    public ObservableList<Achievement> getAchievements() {
        return achievements;
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

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        achievements.clear();
    }
}
