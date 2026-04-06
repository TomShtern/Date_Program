package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileInsightsUseCases.AchievementsQuery;
import datingapp.app.usecase.profile.ProfileInsightsUseCases.StatsQuery;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.AppSession;
import datingapp.core.connection.ConnectionService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.model.User;
import datingapp.ui.async.UiThreadDispatcher;
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

/**
 * ViewModel for the Stats and Achievements screen.
 * Displays user progress, match statistics, and earned achievements.
 */
public class StatsViewModel extends BaseViewModel {

    private final ActivityMetricsService activityMetricsService;
    private final ConnectionService connectionService;
    private final ProfileUseCases profileUseCases;
    private final AppSession session;
    private final Clock clock;

    private final ObservableList<Achievement> achievements = FXCollections.observableArrayList();

    // Stats properties
    private final IntegerProperty totalLikesGiven = new SimpleIntegerProperty(0);
    private final IntegerProperty totalLikesReceived = new SimpleIntegerProperty(0);
    private final IntegerProperty totalMatches = new SimpleIntegerProperty(0);
    private final IntegerProperty messagesExchanged = new SimpleIntegerProperty(0);
    private final IntegerProperty loginStreak = new SimpleIntegerProperty(0);
    private final StringProperty responseRate = new SimpleStringProperty("--");
    private final BooleanProperty loadFailed = new SimpleBooleanProperty(false);
    private final StringProperty loadFailureMessage = new SimpleStringProperty("");

    private static final String UNKNOWN = "unknown";

    private final AtomicReference<User> currentUser = new AtomicReference<>();

    /** Error handler for late-bound error communication (M-22). */
    public void setErrorHandler(ViewModelErrorSink handler) {
        this.setErrorSink(handler);
    }

    /** Canonical constructor. */
    public StatsViewModel(
            ActivityMetricsService activityMetricsService,
            ConnectionService connectionService,
            ProfileUseCases profileUseCases,
            AppSession session,
            Clock clock,
            UiThreadDispatcher uiDispatcher) {
        super("stats", uiDispatcher);
        this.activityMetricsService =
                Objects.requireNonNull(activityMetricsService, "activityMetricsService cannot be null");
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService cannot be null");
        this.profileUseCases = Objects.requireNonNull(profileUseCases, "profileUseCases cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
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
            loadFailureMessage.set(data.failureMessage());
            loadFailed.set(true);
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
        logInfo("Refreshed stats for user: {}", maskUserIdentifier(data.user()));
    }

    private List<Achievement> fetchAchievements(java.util.UUID userId) {
        var result = profileUseCases.getAchievements(new AchievementsQuery(UserContext.ui(userId), false));
        if (!result.success()) {
            throw new IllegalStateException(
                    result.error() != null ? result.error().message() : "Failed to load achievements");
        }
        return result.data().unlocked().stream()
                .map(UserAchievement::achievement)
                .toList();
    }

    private StatsData fetchStats(java.util.UUID userId) {
        UserStats stats = resolveUserStats(userId);

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

    private UserStats resolveUserStats(java.util.UUID userId) {
        var result = profileUseCases.getOrComputeStats(new StatsQuery(UserContext.ui(userId)));
        if (result.success()) {
            return result.data();
        }
        logWarn(
                "profileUseCases.getOrComputeStats failed: {}",
                result.error() != null ? result.error().message() : "unknown");
        return activityMetricsService.getOrComputeStats(userId);
    }

    private int fetchMessagesExchanged(java.util.UUID userId) {
        return connectionService.getTotalMessagesExchanged(userId);
    }

    private int computeLoginStreak(java.util.UUID userId) {
        List<Session> sessions = activityMetricsService.getSessionHistory(userId, 60);
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

    @Override
    public void dispose() {
        asyncScope.dispatchToUi(achievements::clear);
        super.dispose();
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
            return UNKNOWN;
        }
        return Integer.toHexString(user.getId().toString().hashCode());
    }
}
