package datingapp.ui.viewmodel;

import datingapp.core.Achievement.UserAchievement;
import datingapp.core.AchievementService;
import datingapp.core.DailyLimitService;
import datingapp.core.DailyLimitService.DailyStatus;
import datingapp.core.DailyPickService;
import datingapp.core.DailyPickService.DailyPick;
import datingapp.core.MatchStorage;
import datingapp.core.ProfileCompletionService;
import datingapp.core.User;
import datingapp.ui.UISession;
import java.util.List;
import java.util.Optional;
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
    private final BooleanProperty loading = new SimpleBooleanProperty(true);

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
    /**
     * Refreshes all dashboard data from the services asynchronously.
     */
    public void refresh() {
        User user = getCurrentUser();
        if (user == null) {
            logger.warn("Cannot refresh dashboard - no user logged in");
            return;
        }

        // Set loading state
        javafx.application.Platform.runLater(() -> loading.set(true));

        // Run data fetching in background to prevent UI freeze
        Thread.ofVirtual().start(() -> performRefresh(user));
    }

    private void performRefresh(User user) {
        logger.info("Performing dashboard refresh for user: {}", user.getName());

        // 1. Data that doesn't need DB can be set immediately or just captured
        String name = user.getName();

        // 2. Fetch DB data
        String completionText = "--";
        try {
            completionText = ProfileCompletionService.calculate(user).getDisplayString();
        } catch (Exception e) {
            logger.error("Completion count error", e);
        }

        String likesText = "Likes: --/50";
        try {
            DailyStatus status = dailyLimitService.getStatus(user.getId());
            likesText = status.hasUnlimitedLikes()
                    ? "Likes: ∞"
                    : "Likes: " + status.likesUsed() + "/" + (status.likesUsed() + status.likesRemaining());
        } catch (Exception e) {
            logger.error("Likes reload error", e);
        }

        String matchCount = "--";
        try {
            matchCount = String.valueOf(
                    matchStorage.getActiveMatchesFor(user.getId()).size());
        } catch (Exception e) {
            logger.error("Match count error", e);
        }

        String pickName = "No pick available";
        try {
            Optional<DailyPick> pick = dailyPickService.getDailyPick(user);
            if (pick.isPresent()) {
                User pickedUser = pick.get().user();
                pickName = pickedUser.getName() + ", " + pickedUser.getAge();
            }
        } catch (Exception e) {
            logger.error("Daily pick error", e);
        }

        List<UserAchievement> achievements = List.of();
        try {
            achievements = achievementService.getUnlocked(user.getId());
        } catch (Exception e) {
            logger.error("Achievements error", e);
        }

        // 3. Update UI on FX Thread
        final String finalCompletion = completionText;
        final String finalLikes = likesText;
        final String finalMatches = matchCount;
        final String finalPick = pickName;
        final List<UserAchievement> finalAchievements = achievements;

        javafx.application.Platform.runLater(() -> {
            userName.set(name);
            profileCompletion.set(finalCompletion);
            dailyLikesStatus.set(finalLikes);
            totalMatches.set(finalMatches);
            dailyPickName.set(finalPick);

            recentAchievements.clear();
            finalAchievements.stream()
                    .sorted((a, b) -> b.unlockedAt().compareTo(a.unlockedAt()))
                    .limit(3)
                    .forEach(ua -> recentAchievements.add(
                            ua.achievement().getIcon() + " " + ua.achievement().getDisplayName()));

            // Loading complete
            loading.set(false);
        });
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

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public ObservableList<String> getRecentAchievements() {
        return recentAchievements;
    }
}
