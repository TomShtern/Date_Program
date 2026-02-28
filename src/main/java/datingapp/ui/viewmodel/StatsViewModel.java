package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.app.usecase.profile.ProfileUseCases.AchievementsQuery;
import datingapp.app.usecase.profile.ProfileUseCases.StatsQuery;
import datingapp.core.AppSession;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.ui.async.AsyncErrorRouter;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.async.ViewModelAsyncScope;
import java.util.List;
import java.util.Objects;
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

    private final ProfileService achievementService;
    private final ActivityMetricsService statsService;
    private final ProfileUseCases profileUseCases;
    private final AppSession session;
    private final ViewModelAsyncScope asyncScope;

    private final ObservableList<Achievement> achievements = FXCollections.observableArrayList();

    // Stats properties
    private final IntegerProperty totalLikesGiven = new SimpleIntegerProperty(0);
    private final IntegerProperty totalLikesReceived = new SimpleIntegerProperty(0);
    private final IntegerProperty totalMatches = new SimpleIntegerProperty(0);
    private final StringProperty responseRate = new SimpleStringProperty("--");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    private final AtomicReference<User> currentUser = new AtomicReference<>();

    /** Error handler for ViewModel→Controller error communication (M-22). */
    private final AtomicReference<ViewModelErrorSink> errorHandler = new AtomicReference<>();

    public void setErrorHandler(ViewModelErrorSink handler) {
        this.errorHandler.set(handler);
    }

    public StatsViewModel(ProfileService achievementService, ActivityMetricsService statsService, AppSession session) {
        this(achievementService, statsService, null, session, new JavaFxUiThreadDispatcher());
    }

    public StatsViewModel(
            ProfileService achievementService,
            ActivityMetricsService statsService,
            ProfileUseCases profileUseCases,
            AppSession session) {
        this(achievementService, statsService, profileUseCases, session, new JavaFxUiThreadDispatcher());
    }

    public StatsViewModel(
            ProfileService achievementService,
            ActivityMetricsService statsService,
            ProfileUseCases profileUseCases,
            AppSession session,
            UiThreadDispatcher uiDispatcher) {
        this.achievementService = Objects.requireNonNull(achievementService, "achievementService cannot be null");
        this.statsService = Objects.requireNonNull(statsService, "statsService cannot be null");
        this.profileUseCases = profileUseCases;
        this.session = Objects.requireNonNull(session, "session cannot be null");
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

        java.util.UUID userId = user.getId();
        asyncScope.runLatest(
                "stats-refresh",
                "refresh stats",
                () -> new StatsRefreshData(fetchAchievements(userId), fetchStats(userId), user),
                data -> {
                    achievements.setAll(data.achievements());
                    totalLikesGiven.set(data.stats().likesGiven());
                    totalLikesReceived.set(data.stats().likesReceived());
                    totalMatches.set(data.stats().matchesCount());
                    responseRate.set(data.stats().rateText());
                    if (logger.isInfoEnabled()) {
                        logger.info("Refreshed stats for user: {}", maskUserIdentifier(data.user()));
                    }
                });
    }

    private List<Achievement> fetchAchievements(java.util.UUID userId) {
        List<UserAchievement> earned;
        if (profileUseCases != null) {
            var result = profileUseCases.getAchievements(new AchievementsQuery(UserContext.ui(userId), false));
            earned = result.success() ? result.data().unlocked() : List.of();
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

    private record StatsRefreshData(List<Achievement> achievements, StatsData stats, User user) {}

    private String maskUserIdentifier(User user) {
        if (user == null || user.getId() == null) {
            return "unknown";
        }
        return Integer.toHexString(user.getId().toString().hashCode());
    }
}
