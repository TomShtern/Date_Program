package datingapp.ui.viewmodel;

import datingapp.core.AchievementService;
import datingapp.core.DailyLimitService;
import datingapp.core.DailyLimitService.DailyStatus;
import datingapp.core.DailyPickService;
import datingapp.core.DailyPickService.DailyPick;
import datingapp.core.Match;
import datingapp.core.MatchStorage;
import datingapp.core.ProfileCompletionService;
import datingapp.core.ProfileCompletionService.CompletionResult;
import datingapp.core.User;
import datingapp.core.UserAchievement;
import datingapp.ui.UISession;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Dashboard screen.
 * Aggregates information from various services to display on the main hub.
 */
public class DashboardViewModel {
    private static final Logger logger = LoggerFactory.getLogger(DashboardViewModel.class);

    private final DailyPickService dailyPickService;
    private final MatchStorage matchStorage;
    private final DailyLimitService dailyLimitService;
    private final AchievementService achievementService;

    // Observable properties for data binding
    private final StringProperty userName = new SimpleStringProperty("Not Logged In");
    private final StringProperty dailyLikesStatus = new SimpleStringProperty("Likes: 0/50");
    private final StringProperty dailyPickName = new SimpleStringProperty("No pick today");
    private final StringProperty totalMatches = new SimpleStringProperty("0");
    private final StringProperty profileCompletion = new SimpleStringProperty("0%");
    private final IntegerProperty notificationCount = new SimpleIntegerProperty(0);
    private final IntegerProperty unreadMessages = new SimpleIntegerProperty(0);

    private final ObservableList<String> recentAchievements = FXCollections.observableArrayList();

    public DashboardViewModel(
            DailyPickService dailyPickService,
            MatchStorage matchStorage,
            DailyLimitService dailyLimitService,
            AchievementService achievementService) {
        this.dailyPickService = dailyPickService;
        this.matchStorage = matchStorage;
        this.dailyLimitService = dailyLimitService;
        this.achievementService = achievementService;
    }

    /**
     * Gets the current user from the UI session.
     */
    public User getCurrentUser() {
        return UISession.getInstance().getCurrentUser();
    }

    /**
     * Refreshes all dashboard data from the services.
     */
    public void refresh() {
        User user = getCurrentUser();
        if (user == null) {
            logger.warn("Cannot refresh dashboard - no user logged in");
            return;
        }

        logger.info("Refreshing dashboard data for user: {}", user.getName());

        // Update username
        userName.set(user.getName());

        // Profile completion (static method)
        try {
            CompletionResult completion = ProfileCompletionService.calculate(user);
            profileCompletion.set(completion.getDisplayString());
        } catch (Exception e) {
            logger.error("Failed to calculate profile completion", e);
            profileCompletion.set("--");
        }

        // Daily likes (use getStatus)
        try {
            DailyStatus status = dailyLimitService.getStatus(user.getId());
            if (status.hasUnlimitedLikes()) {
                dailyLikesStatus.set("Likes: ∞");
            } else {
                dailyLikesStatus.set(
                        "Likes: " + status.likesUsed() + "/" + (status.likesUsed() + status.likesRemaining()));
            }
        } catch (Exception e) {
            logger.error("Failed to get daily likes", e);
            dailyLikesStatus.set("Likes: --/50");
        }

        // Total matches (use getActiveMatchesFor)
        try {
            List<Match> matches = matchStorage.getActiveMatchesFor(user.getId());
            totalMatches.set(String.valueOf(matches.size()));
        } catch (Exception e) {
            logger.error("Failed to get matches", e);
            totalMatches.set("--");
        }

        // Daily pick (returns DailyPick, not User)
        try {
            Optional<DailyPick> pick = dailyPickService.getDailyPick(user);
            if (pick.isPresent()) {
                DailyPick dailyPick = pick.get();
                User pickedUser = dailyPick.user();
                dailyPickName.set(pickedUser.getName() + ", " + pickedUser.getAge());
            } else {
                dailyPickName.set("No pick available");
            }
        } catch (Exception e) {
            logger.error("Failed to get daily pick", e);
            dailyPickName.set("Error loading");
        }

        // Recent achievements (use getIcon() not getEmoji())
        try {
            recentAchievements.clear();
            List<UserAchievement> achievements = achievementService.getUnlocked(user.getId());
            // Show the 3 most recent
            achievements.stream()
                    .sorted((a, b) -> b.unlockedAt().compareTo(a.unlockedAt()))
                    .limit(3)
                    .forEach(ua -> recentAchievements.add(
                            ua.achievement().getIcon() + " " + ua.achievement().getDisplayName()));
        } catch (Exception e) {
            logger.error("Failed to get achievements", e);
        }
    }

    // --- Properties for data binding ---

    public StringProperty userNameProperty() {
        return userName;
    }

    public StringProperty dailyLikesStatusProperty() {
        return dailyLikesStatus;
    }

    public StringProperty dailyPickNameProperty() {
        return dailyPickName;
    }

    public StringProperty totalMatchesProperty() {
        return totalMatches;
    }

    public StringProperty profileCompletionProperty() {
        return profileCompletion;
    }

    public IntegerProperty notificationCountProperty() {
        return notificationCount;
    }

    public IntegerProperty unreadMessagesProperty() {
        return unreadMessages;
    }

    public ObservableList<String> getRecentAchievements() {
        return recentAchievements;
    }
}
