package datingapp.ui.viewmodel;

import datingapp.core.AppSession;
import datingapp.core.model.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.service.AchievementService;
import datingapp.core.service.DailyService;
import datingapp.core.service.DailyService.DailyPick;
import datingapp.core.service.DailyService.DailyStatus;
import datingapp.core.service.MessagingService;
import datingapp.core.service.ProfileCompletionService;
import datingapp.ui.viewmodel.data.UiDataAdapters.UiMatchDataAccess;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
 * ViewModel for the Dashboard screen.
 * Aggregates information from various services to display on the main hub.
 */
public class DashboardViewModel {
    private static final Logger logger = LoggerFactory.getLogger(DashboardViewModel.class);

    private final DailyService dailyService;
    private final UiMatchDataAccess matchData;
    private final AchievementService achievementService;
    private final MessagingService messagingService;
    private final ProfileCompletionService profileCompletionService;

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

    private ErrorHandler errorHandler;
    private final AtomicInteger activeLoads = new AtomicInteger(0);

    /** Track background thread for cleanup on dispose. */
    private final AtomicReference<Thread> backgroundThread = new AtomicReference<>();

    /**
     * Track disposed state to prevent operations after cleanup.
     *
     * <p>Uses {@link AtomicBoolean} instead of a volatile flag to make the threading
     * semantics explicit and consistent with other atomics (e.g. {@link #activeLoads}).
     * This allows safe, lock-free checks and updates from background threads and
     * keeps the option open for compound atomic operations if the lifecycle logic
     * becomes more complex.
     */
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public DashboardViewModel(
            DailyService dailyService,
            UiMatchDataAccess matchData,
            AchievementService achievementService,
            MessagingService messagingService,
            ProfileCompletionService profileCompletionService) {
        this.dailyService = dailyService;
        this.matchData = matchData;
        this.achievementService = achievementService;
        this.messagingService = messagingService;
        this.profileCompletionService = profileCompletionService;
    }

    /**
     * Gets the current user from the UI session.
     */
    public User getCurrentUser() {
        return AppSession.getInstance().getCurrentUser();
    }

    /**
     * Refreshes all dashboard data from the services asynchronously.
     */
    public void refresh() {
        User user = getCurrentUser();
        if (user == null) {
            logWarn("Cannot refresh dashboard - no user logged in");
            activeLoads.set(0);
            setLoadingState(false);
            return;
        }

        beginLoading();

        // Run data fetching in background to prevent UI freeze
        Thread thread = Thread.ofVirtual().start(() -> performRefresh(user));
        backgroundThread.set(thread);
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        Thread thread = backgroundThread.getAndSet(null);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        activeLoads.set(0);
        setLoadingState(false);
    }

    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    private void performRefresh(User user) {
        if (disposed.get()) {
            endLoading();
            return;
        }
        logInfo("Performing dashboard refresh for user: {}", user.getName());

        // 1. Data that doesn't need DB can be set immediately or just captured
        String name = user.getName();

        // 2. Fetch DB data
        String completionText = "--";
        Exception firstError = null;
        try {
            completionText = profileCompletionService.calculate(user).getDisplayString();
        } catch (Exception e) {
            logError("Completion count error", e);
            if (firstError == null) {
                firstError = e;
            }
        }

        String likesText = "Likes: --/50";
        try {
            DailyStatus status = dailyService.getStatus(user.getId());
            likesText = status.hasUnlimitedLikes()
                    ? "Likes: ∞"
                    : "Likes: " + status.likesUsed() + "/" + (status.likesUsed() + status.likesRemaining());
        } catch (Exception e) {
            logError("Likes reload error", e);
            if (firstError == null) {
                firstError = e;
            }
        }

        String matchCount = "--";
        try {
            matchCount =
                    String.valueOf(matchData.getActiveMatchesFor(user.getId()).size());
        } catch (Exception e) {
            logError("Match count error", e);
            if (firstError == null) {
                firstError = e;
            }
        }

        String pickName = "No pick available";
        try {
            Optional<DailyPick> pick = dailyService.getDailyPick(user);
            if (pick.isPresent()) {
                User pickedUser = pick.get().user();
                pickName = pickedUser.getName() + ", " + pickedUser.getAge();
            }
        } catch (Exception e) {
            logError("Daily pick error", e);
            if (firstError == null) {
                firstError = e;
            }
        }

        List<UserAchievement> achievements = List.of();
        try {
            achievements = achievementService.getUnlocked(user.getId());
        } catch (Exception e) {
            logError("Achievements error", e);
            if (firstError == null) {
                firstError = e;
            }
        }

        int unreadCount = 0;
        try {
            if (messagingService != null) {
                unreadCount = messagingService.getTotalUnreadCount(user.getId());
            }
        } catch (Exception e) {
            logError("Unread messages error", e);
            if (firstError == null) {
                firstError = e;
            }
        }

        // 3. Update UI on FX Thread
        final String finalCompletion = completionText;
        final String finalLikes = likesText;
        final String finalMatches = matchCount;
        final String finalPick = pickName;
        final List<UserAchievement> finalAchievements = achievements;
        final int finalUnreadCount = unreadCount;

        final Exception finalError = firstError;
        Platform.runLater(() -> {
            userName.set(name);
            profileCompletion.set(finalCompletion);
            dailyLikesStatus.set(finalLikes);
            totalMatches.set(finalMatches);
            dailyPickName.set(finalPick);
            unreadMessages.set(finalUnreadCount);
            // notificationCount could aggregate unread + other alerts in future

            recentAchievements.clear();
            finalAchievements.stream()
                    .sorted((a, b) -> b.unlockedAt().compareTo(a.unlockedAt()))
                    .limit(3)
                    .forEach(ua -> recentAchievements.add(
                            ua.achievement().getIcon() + " " + ua.achievement().getDisplayName()));

            // Loading complete
            endLoading();

            if (finalError != null) {
                notifyError("Some dashboard data failed to load", finalError);
            }
        });
    }

    private void notifyError(String userMessage, Exception e) {
        if (errorHandler == null) {
            return;
        }
        String detail = e.getMessage();
        String message = detail == null || detail.isBlank() ? userMessage : userMessage + ": " + detail;
        if (Platform.isFxApplicationThread()) {
            errorHandler.onError(message);
        } else {
            Platform.runLater(() -> errorHandler.onError(message));
        }
    }

    private void beginLoading() {
        if (activeLoads.incrementAndGet() == 1) {
            setLoadingState(true);
        }
    }

    private void endLoading() {
        int remaining = activeLoads.decrementAndGet();
        if (remaining <= 0) {
            activeLoads.set(0);
            setLoadingState(false);
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

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    private void logError(String message, Throwable error) {
        if (logger.isErrorEnabled()) {
            logger.error(message, error);
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

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public ObservableList<String> getRecentAchievements() {
        return recentAchievements;
    }
}
