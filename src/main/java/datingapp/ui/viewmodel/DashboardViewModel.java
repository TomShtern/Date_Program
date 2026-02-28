package datingapp.ui.viewmodel;

import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.matching.RecommendationService.DailyPick;
import datingapp.core.matching.RecommendationService.DailyStatus;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.ui.async.AsyncErrorRouter;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.async.ViewModelAsyncScope;
import datingapp.ui.viewmodel.UiDataAdapters.UiMatchDataAccess;
import java.util.List;
import java.util.Objects;
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
    private static final String UNKNOWN_VALUE = "--";
    private static final String DEFAULT_LIKES_TEXT = "Likes: --/50";
    private static final String DEFAULT_PICK_TEXT = "No pick available";

    private final RecommendationService dailyService;
    private final UiMatchDataAccess matchData;
    private final ProfileService achievementService;
    private final ConnectionService messagingService;
    private final ProfileService profileCompletionService;
    private final AppSession session;
    private final ViewModelAsyncScope asyncScope;

    // Observable properties for data binding
    private final StringProperty userName = new SimpleStringProperty("Not Logged In");
    private final StringProperty dailyLikesStatus = new SimpleStringProperty("Likes: 0/50");
    private final StringProperty dailyPickName = new SimpleStringProperty("No pick today");
    private final StringProperty totalMatches = new SimpleStringProperty("0");
    private final StringProperty profileCompletion = new SimpleStringProperty("0%");
    private final IntegerProperty notificationCount = new SimpleIntegerProperty(0);
    private final IntegerProperty unreadMessages = new SimpleIntegerProperty(0);
    private final IntegerProperty friendRequestsCount = new SimpleIntegerProperty(0);
    private final BooleanProperty loading = new SimpleBooleanProperty(true);

    private final ObservableList<String> recentAchievements = FXCollections.observableArrayList();

    private ViewModelErrorSink errorHandler;

    public DashboardViewModel(
            RecommendationService dailyService,
            UiMatchDataAccess matchData,
            ProfileService achievementService,
            ConnectionService messagingService,
            ProfileService profileCompletionService,
            AppSession session) {
        this(
                dailyService,
                matchData,
                achievementService,
                messagingService,
                profileCompletionService,
                session,
                new JavaFxUiThreadDispatcher());
    }

    public DashboardViewModel(
            RecommendationService dailyService,
            UiMatchDataAccess matchData,
            ProfileService achievementService,
            ConnectionService messagingService,
            ProfileService profileCompletionService,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        this.dailyService = Objects.requireNonNull(dailyService, "dailyService cannot be null");
        this.matchData = Objects.requireNonNull(matchData, "matchData cannot be null");
        this.achievementService = Objects.requireNonNull(achievementService, "achievementService cannot be null");
        this.messagingService = Objects.requireNonNull(messagingService, "messagingService cannot be null");
        this.profileCompletionService =
                Objects.requireNonNull(profileCompletionService, "profileCompletionService cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.asyncScope = createAsyncScope(uiDispatcher);
    }

    /**
     * Gets the current user from the UI session.
     */
    public User getCurrentUser() {
        return session.getCurrentUser();
    }

    /**
     * Refreshes all dashboard data from the services asynchronously.
     */
    public void refresh() {
        User user = getCurrentUser();
        if (user == null) {
            logWarn("Cannot refresh dashboard - no user logged in");
            setLoadingState(false);
            return;
        }

        asyncScope.runLatest("dashboard-refresh", "refresh dashboard", () -> loadDashboardData(user), this::applyData);
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        asyncScope.dispose();
        setLoadingState(false);
    }

    public void logout() {
        session.logout();
    }

    public void setErrorHandler(ViewModelErrorSink handler) {
        this.errorHandler = handler;
    }

    private DashboardData loadDashboardData(User user) {
        logInfo("Performing dashboard refresh for user: {}", user.getName());

        String name = user.getName();
        if (Thread.currentThread().isInterrupted()) {
            return DashboardData.empty(name);
        }
        ErrorCollector errors = new ErrorCollector();

        String completionText = loadCompletionText(user, errors);
        if (Thread.currentThread().isInterrupted()) {
            return DashboardData.empty(name);
        }
        String likesText = loadLikesText(user, errors);
        if (Thread.currentThread().isInterrupted()) {
            return DashboardData.empty(name);
        }
        String matchCount = loadMatchCount(user, errors);
        if (Thread.currentThread().isInterrupted()) {
            return DashboardData.empty(name);
        }
        String pickName = loadPickName(user, errors);
        if (Thread.currentThread().isInterrupted()) {
            return DashboardData.empty(name);
        }
        List<UserAchievement> achievements = loadAchievements(user, errors);
        if (Thread.currentThread().isInterrupted()) {
            return DashboardData.empty(name);
        }
        NotificationSnapshot notificationSnapshot = loadNotifications(user, errors);

        return new DashboardData(
                name,
                completionText,
                likesText,
                matchCount,
                pickName,
                achievements,
                notificationSnapshot.unreadCount(),
                notificationSnapshot.pendingRequests(),
                notificationSnapshot.unreadNotifications(),
                errors.firstError());
    }

    private String loadCompletionText(User user, ErrorCollector errors) {
        if (Thread.currentThread().isInterrupted()) {
            return UNKNOWN_VALUE;
        }
        try {
            return profileCompletionService.calculate(user).getDisplayString();
        } catch (Exception e) {
            logError("Completion count error", e);
            errors.capture(e);
            return UNKNOWN_VALUE;
        }
    }

    private String loadLikesText(User user, ErrorCollector errors) {
        if (Thread.currentThread().isInterrupted()) {
            return DEFAULT_LIKES_TEXT;
        }
        try {
            DailyStatus status = dailyService.getStatus(user.getId());
            return status.hasUnlimitedLikes()
                    ? "Likes: ∞"
                    : "Likes: " + status.likesUsed() + "/" + (status.likesUsed() + status.likesRemaining());
        } catch (Exception e) {
            logError("Likes reload error", e);
            errors.capture(e);
            return DEFAULT_LIKES_TEXT;
        }
    }

    private String loadMatchCount(User user, ErrorCollector errors) {
        if (Thread.currentThread().isInterrupted()) {
            return UNKNOWN_VALUE;
        }
        try {
            return String.valueOf(matchData.countActiveMatchesFor(user.getId()));
        } catch (Exception e) {
            logError("Match count error", e);
            errors.capture(e);
            return UNKNOWN_VALUE;
        }
    }

    private String loadPickName(User user, ErrorCollector errors) {
        if (Thread.currentThread().isInterrupted()) {
            return DEFAULT_PICK_TEXT;
        }
        try {
            Optional<DailyPick> pick = dailyService.getDailyPick(user);
            if (pick.isEmpty()) {
                return DEFAULT_PICK_TEXT;
            }

            User pickedUser = pick.get().user();
            java.time.ZoneId timezone = AppConfig.defaults().safety().userTimeZone();
            int ageVal = pickedUser.getAge(timezone);
            String age = ageVal > 0 ? String.valueOf(ageVal) : "?";
            return pickedUser.getName() + ", " + age;
        } catch (Exception e) {
            logError("Daily pick error", e);
            errors.capture(e);
            return DEFAULT_PICK_TEXT;
        }
    }

    private List<UserAchievement> loadAchievements(User user, ErrorCollector errors) {
        if (Thread.currentThread().isInterrupted()) {
            return List.of();
        }
        try {
            return achievementService.getUnlocked(user.getId());
        } catch (Exception e) {
            logError("Achievements error", e);
            errors.capture(e);
            return List.of();
        }
    }

    private NotificationSnapshot loadNotifications(User user, ErrorCollector errors) {
        if (Thread.currentThread().isInterrupted()) {
            return new NotificationSnapshot(0, 0, 0);
        }
        try {
            int unreadCount = messagingService.getTotalUnreadCount(user.getId());
            int pendingRequests = messagingService.countPendingRequestsFor(user.getId());
            int unreadNotifications = messagingService.getUnreadNotificationCount(user.getId());
            return new NotificationSnapshot(unreadCount, pendingRequests, unreadNotifications);
        } catch (Exception e) {
            logError("Notifications/Messages error", e);
            errors.capture(e);
            return new NotificationSnapshot(0, 0, 0);
        }
    }

    private void applyData(DashboardData data) {
        userName.set(data.name());
        profileCompletion.set(data.completionText());
        dailyLikesStatus.set(data.likesText());
        totalMatches.set(data.matchCount());
        dailyPickName.set(data.pickName());
        unreadMessages.set(data.unreadCount());
        friendRequestsCount.set(data.pendingRequests());
        notificationCount.set(data.unreadCount() + data.pendingRequests() + data.unreadNotifications());

        recentAchievements.clear();
        data.achievements().stream()
                .sorted((a, b) -> b.unlockedAt().compareTo(a.unlockedAt()))
                .limit(3)
                .forEach(ua -> recentAchievements.add(
                        ua.achievement().getIcon() + " " + ua.achievement().getDisplayName()));

        if (data.error() != null) {
            notifyError("Some dashboard data failed to load", data.error());
        }
    }

    private void notifyError(String userMessage, Exception e) {
        if (errorHandler == null) {
            return;
        }
        String detail = e.getMessage();
        String message = detail == null || detail.isBlank() ? userMessage : userMessage + ": " + detail;
        asyncScope.dispatchToUi(() -> errorHandler.onError(message));
    }

    private void setLoadingState(boolean isLoading) {
        if (loading.get() != isLoading) {
            loading.set(isLoading);
        }
    }

    private ViewModelAsyncScope createAsyncScope(UiThreadDispatcher uiDispatcher) {
        UiThreadDispatcher dispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher cannot be null");
        ViewModelAsyncScope scope = new ViewModelAsyncScope(
                "dashboard", dispatcher, new AsyncErrorRouter(logger, dispatcher, () -> errorHandler));
        scope.setLoadingStateConsumer(this::setLoadingState);
        return scope;
    }

    private record DashboardData(
            String name,
            String completionText,
            String likesText,
            String matchCount,
            String pickName,
            List<UserAchievement> achievements,
            int unreadCount,
            int pendingRequests,
            int unreadNotifications,
            Exception error) {
        private static DashboardData empty(String name) {
            return new DashboardData(
                    name,
                    UNKNOWN_VALUE,
                    DEFAULT_LIKES_TEXT,
                    UNKNOWN_VALUE,
                    DEFAULT_PICK_TEXT,
                    List.of(),
                    0,
                    0,
                    0,
                    null);
        }
    }

    private record NotificationSnapshot(int unreadCount, int pendingRequests, int unreadNotifications) {}

    private static final class ErrorCollector {
        private Exception firstError;

        private void capture(Exception error) {
            if (firstError == null) {
                firstError = error;
            }
        }

        private Exception firstError() {
            return firstError;
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

    public IntegerProperty friendRequestsCountProperty() {
        return friendRequestsCount;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public ObservableList<String> getRecentAchievements() {
        return recentAchievements;
    }
}
