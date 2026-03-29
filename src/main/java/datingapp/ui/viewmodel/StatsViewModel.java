package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.ProfileUseCases.AchievementsQuery;
import datingapp.app.usecase.profile.ProfileUseCases.StatsQuery;
import datingapp.core.AppClock;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.model.User;
import datingapp.ui.async.AsyncErrorRouter;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.async.ViewModelAsyncScope;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
    private final ActivityMetricsService statsService;
    private final ConnectionService connectionService;
    private final ProfileUseCases profileUseCases;
    private final AppSession session;
    private final Clock clock;
    private final ViewModelAsyncScope asyncScope;

    private final ObservableList<Achievement> achievements = FXCollections.observableArrayList();

    // Stats properties
    private final IntegerProperty totalLikesGiven = new SimpleIntegerProperty(0);
    private final IntegerProperty totalLikesReceived = new SimpleIntegerProperty(0);
    private final IntegerProperty totalMatches = new SimpleIntegerProperty(0);
    private final IntegerProperty messagesExchanged = new SimpleIntegerProperty(0);
    private final IntegerProperty loginStreak = new SimpleIntegerProperty(0);
    private final StringProperty responseRate = new SimpleStringProperty("--");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty loadFailed = new SimpleBooleanProperty(false);
    private final StringProperty loadFailureMessage = new SimpleStringProperty("");

    private final AtomicReference<User> currentUser = new AtomicReference<>();

    /** Error handler for ViewModel→Controller error communication (M-22). */
    private final AtomicReference<ViewModelErrorSink> errorHandler = new AtomicReference<>();

    public void setErrorHandler(ViewModelErrorSink handler) {
        this.errorHandler.set(handler);
    }

    public StatsViewModel(
            AchievementService achievementService,
            ActivityMetricsService statsService,
            ConnectionService connectionService,
            AppSession session) {
        this(
                achievementService,
                statsService,
                connectionService,
                null,
                session,
                AppClock.clock(),
                new JavaFxUiThreadDispatcher());
    }

    public StatsViewModel(
            AchievementService achievementService,
            ActivityMetricsService statsService,
            ConnectionService connectionService,
            ProfileUseCases profileUseCases,
            AppSession session) {
        this(
                achievementService,
                statsService,
                connectionService,
                profileUseCases,
                session,
                AppClock.clock(),
                new JavaFxUiThreadDispatcher());
    }

    public StatsViewModel(
            AchievementService achievementService,
            ActivityMetricsService statsService,
            ConnectionService connectionService,
            ProfileUseCases profileUseCases,
            AppSession session,
            Clock clock) {
        this(
                achievementService,
                statsService,
                connectionService,
                profileUseCases,
                session,
                clock,
                new JavaFxUiThreadDispatcher());
    }

    public StatsViewModel(
            AchievementService achievementService,
            ActivityMetricsService statsService,
            ConnectionService connectionService,
            ProfileUseCases profileUseCases,
            AppSession session,
            Clock clock,
            UiThreadDispatcher uiDispatcher) {
        this.achievementService = Objects.requireNonNull(achievementService, "achievementService cannot be null");
        this.statsService = Objects.requireNonNull(statsService, "statsService cannot be null");
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService cannot be null");
        this.profileUseCases = profileUseCases;
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.asyncScope = createAsyncScope(uiDispatcher);
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
        if (asyncScope.isDisposed() || user == null) {
            return;
        }

        asyncScope.runLatest("stats-refresh", "refresh stats", () -> loadStats(user), this::applyStatsData);
    }

    private StatsRefreshData loadStats(User user) {
        try {
            return StatsRefreshData.success(fetchAchievements(user.getId()), fetchStats(user.getId()), user);
        } catch (Exception e) {
            logWarn("Failed to load stats for {}: {}", maskUserIdentifier(user), e.getMessage(), e);
            return StatsRefreshData.failure(user, e);
        }
    }

    private void applyStatsData(StatsRefreshData data) {
        if (!data.success()) {
            loadFailed.set(true);
            loadFailureMessage.set(data.failureMessage());
            notifyError(data.failureMessage());
            return;
        }

        loadFailed.set(false);
        loadFailureMessage.set("");
        achievements.setAll(data.achievements());
        totalLikesGiven.set(data.stats().likesGiven());
        totalLikesReceived.set(data.stats().likesReceived());
        totalMatches.set(data.stats().matchesCount());
        messagesExchanged.set(data.stats().messagesExchanged());
        loginStreak.set(data.stats().loginStreak());
        responseRate.set(data.stats().rateText());
        if (logger.isInfoEnabled()) {
            logger.info("Refreshed stats for user: {}", maskUserIdentifier(data.user()));
        }
    }

    private void notifyError(String message) {
        ViewModelErrorSink handler = errorHandler.get();
        if (handler != null) {
            asyncScope.dispatchToUi(() -> handler.onError(message));
        }
    }

    private List<Achievement> fetchAchievements(java.util.UUID userId) {
        List<UserAchievement> earned;
        if (profileUseCases != null) {
            var result = profileUseCases.getAchievements(new AchievementsQuery(UserContext.ui(userId), false));
            if (!result.success()) {
                throw new IllegalStateException(
                        result.error() != null ? result.error().message() : "Failed to load achievements");
            }
            earned = result.data().unlocked();
        } else {
            earned = achievementService.getUnlocked(userId);
        }
        return earned.stream().map(UserAchievement::achievement).toList();
    }

    private StatsData fetchStats(java.util.UUID userId) {
        UserStats stats;
        if (profileUseCases != null) {
            var result = profileUseCases.getOrComputeStats(new StatsQuery(UserContext.ui(userId)));
            stats = result.success() ? result.data() : statsService.getOrComputeStats(userId);
        } else {
            stats = statsService.getOrComputeStats(userId);
        }

        int likesGiven = stats.likesGiven();
        int likesReceived = stats.likesReceived();
        int matchesCount = stats.activeMatches();
        int messageCount = fetchMessagesExchanged(userId);
        int streakDays = computeLoginStreak(userId);

        String rateText = "--";
        if (likesReceived > 0) {
            double rate = (double) matchesCount / likesReceived * 100;
            rateText = String.format("%.0f%%", Math.min(rate, 100));
        }

        return new StatsData(likesGiven, likesReceived, matchesCount, messageCount, streakDays, rateText);
    }

    private int fetchMessagesExchanged(java.util.UUID userId) {
        return connectionService.getTotalMessagesExchanged(userId);
    }

    private int computeLoginStreak(java.util.UUID userId) {
        List<Session> sessions = statsService.getSessionHistory(userId, 60);
        if (sessions.isEmpty()) {
            return 0;
        }

        Clock utcClock = clock.withZone(ZoneOffset.UTC);

        Set<LocalDate> activeDays = sessions.stream()
                .map(swipeSession ->
                        swipeSession.getStartedAt().atZone(utcClock.getZone()).toLocalDate())
                .collect(java.util.stream.Collectors.toSet());

        LocalDate cursor = LocalDate.now(utcClock);
        int streak = 0;
        while (activeDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private record StatsData(
            int likesGiven,
            int likesReceived,
            int matchesCount,
            int messagesExchanged,
            int loginStreak,
            String rateText) {}

    // --- Properties ---
    public ObservableList<Achievement> getAchievements() {
        return achievements;
    }

    /** Returns the total number of achievements defined in the system. */
    public int getTotalAchievementCount() {
        return Achievement.values().length;
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

    public IntegerProperty messagesExchangedProperty() {
        return messagesExchanged;
    }

    public IntegerProperty loginStreakProperty() {
        return loginStreak;
    }

    public StringProperty responseRateProperty() {
        return responseRate;
    }

    public BooleanProperty loadFailedProperty() {
        return loadFailed;
    }

    public StringProperty loadFailureMessageProperty() {
        return loadFailureMessage;
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        asyncScope.dispose();
        asyncScope.dispatchToUi(achievements::clear);
    }

    private void setLoadingState(boolean isLoading) {
        if (loading.get() != isLoading) {
            loading.set(isLoading);
        }
    }

    private ViewModelAsyncScope createAsyncScope(UiThreadDispatcher uiDispatcher) {
        UiThreadDispatcher dispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher cannot be null");
        ViewModelAsyncScope scope = new ViewModelAsyncScope(
                "stats", dispatcher, new AsyncErrorRouter(logger, dispatcher, errorHandler::get));
        scope.setLoadingStateConsumer(this::setLoadingState);
        return scope;
    }

    private record StatsRefreshData(
            boolean success, List<Achievement> achievements, StatsData stats, User user, String failureMessage) {

        private static StatsRefreshData success(List<Achievement> achievements, StatsData stats, User user) {
            return new StatsRefreshData(true, List.copyOf(achievements), stats, user, "");
        }

        private static StatsRefreshData failure(User user, Exception error) {
            return new StatsRefreshData(false, List.of(), null, user, error != null ? error.getMessage() : "");
        }
    }

    private String maskUserIdentifier(User user) {
        if (user == null || user.getId() == null) {
            return "unknown";
        }
        return Integer.toHexString(user.getId().toString().hashCode());
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }
}
