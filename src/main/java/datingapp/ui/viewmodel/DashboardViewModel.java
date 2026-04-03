package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.dashboard.DashboardUseCases;
import datingapp.core.AppSession;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.model.User;
import datingapp.ui.UiPreferencesStore;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * ViewModel for the Dashboard.
 * Handles loading stats, current user info, and the daily "best match" pick.
 */
public class DashboardViewModel extends BaseViewModel {

    private static final String UNKNOWN_VALUE = "--";
    private static final String DEFAULT_LIKES_TEXT = "Likes: --";
    private static final String DEFAULT_PICK_TEXT = "No pick today";
    private static final String DEFAULT_PICK_EMPTY_MESSAGE =
            "No daily pick is available right now. Check back tomorrow.";

    private final DashboardUseCases dashboardUseCases;
    private final AppSession session;
    private final UiPreferencesStore uiPreferencesStore;

    private final StringProperty userName = new SimpleStringProperty("Not Logged In");
    private final StringProperty dailyLikesStatus = new SimpleStringProperty(DEFAULT_LIKES_TEXT);
    private final StringProperty dailyPickName = new SimpleStringProperty(DEFAULT_PICK_TEXT);
    private final StringProperty dailyPickReason = new SimpleStringProperty("");
    private final StringProperty dailyPickEmptyMessage = new SimpleStringProperty(DEFAULT_PICK_EMPTY_MESSAGE);
    private final ObjectProperty<UUID> dailyPickUserId = new SimpleObjectProperty<>();
    private final BooleanProperty dailyPickSeen = new SimpleBooleanProperty(false);
    private final BooleanProperty dailyPickAvailable = new SimpleBooleanProperty(false);
    private final StringProperty totalMatches = new SimpleStringProperty("0");
    private final StringProperty profileCompletion = new SimpleStringProperty("0% Starter");
    private final IntegerProperty notificationCount = new SimpleIntegerProperty(0);
    private final IntegerProperty unreadMessages = new SimpleIntegerProperty(0);
    private final ObservableList<String> recentAchievements = FXCollections.observableArrayList();
    private final BooleanProperty newAchievementsAvailable = new SimpleBooleanProperty(false);
    private final StringProperty profileNudgeMessage = new SimpleStringProperty("");

    private Set<String> latestAchievementIds = Set.of();
    private List<Achievement> newlyUnlockedAchievements = List.of();

    public record Dependencies(DashboardUseCases dashboardUseCases) {}

    public DashboardViewModel(Dependencies dependencies, AppSession session) {
        this(dependencies, session, new JavaFxUiThreadDispatcher(), new UiPreferencesStore());
    }

    public DashboardViewModel(Dependencies dependencies, AppSession session, UiThreadDispatcher uiDispatcher) {
        this(dependencies, session, uiDispatcher, new UiPreferencesStore());
    }

    public DashboardViewModel(
            Dependencies dependencies,
            AppSession session,
            UiThreadDispatcher uiDispatcher,
            UiPreferencesStore uiPreferencesStore) {
        super("dashboard", uiDispatcher);
        Dependencies resolvedDependencies = Objects.requireNonNull(dependencies, "dependencies cannot be null");
        this.dashboardUseCases =
                Objects.requireNonNull(resolvedDependencies.dashboardUseCases(), "dashboardUseCases cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.uiPreferencesStore = Objects.requireNonNull(uiPreferencesStore, "uiPreferencesStore cannot be null");
    }

    public void refresh() {
        if (isDisposed()) {
            return;
        }
        User user = session.getCurrentUser();
        if (user == null) {
            userName.set("Not Logged In");
            setLoadingState(false);
            return;
        }

        asyncScope.runLatest(
                "dashboard-refresh", "refresh dashboard", () -> loadDashboardData(user), this::applyDashboardData);
    }

    public void performRefresh() {
        refresh();
    }

    public void setErrorHandler(ViewModelErrorSink handler) {
        setErrorSink(handler);
    }

    public void logout() {
        session.logout();
    }

    public void markDailyPickViewed() {
        if (isDisposed() || !dailyPickAvailable.get() || dailyPickSeen.get()) {
            return;
        }

        User user = session.getCurrentUser();
        if (user == null || dailyPickUserId.get() == null) {
            return;
        }

        asyncScope.runFireAndForget("mark daily pick viewed", () -> {
            var result = dashboardUseCases.markDailyPickViewed(
                    new DashboardUseCases.MarkDailyPickViewedCommand(UserContext.ui(user.getId())));
            if (result.success()) {
                asyncScope.dispatchToUi(() -> dailyPickSeen.set(true));
            } else {
                Exception failure = new IllegalStateException(result.error().message());
                logError("Failed to mark daily pick viewed", failure);
                notifyError("Failed to update daily pick status", failure);
            }
        });
    }

    public StringProperty userNameProperty() {
        return userName;
    }

    public StringProperty dailyLikesStatusProperty() {
        return dailyLikesStatus;
    }

    public StringProperty dailyPickNameProperty() {
        return dailyPickName;
    }

    public ObjectProperty<UUID> dailyPickUserIdProperty() {
        return dailyPickUserId;
    }

    public StringProperty dailyPickReasonProperty() {
        return dailyPickReason;
    }

    public StringProperty dailyPickEmptyMessageProperty() {
        return dailyPickEmptyMessage;
    }

    public BooleanProperty dailyPickSeenProperty() {
        return dailyPickSeen;
    }

    public BooleanProperty dailyPickAvailableProperty() {
        return dailyPickAvailable;
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

    public BooleanProperty newAchievementsAvailableProperty() {
        return newAchievementsAvailable;
    }

    public StringProperty profileNudgeMessageProperty() {
        return profileNudgeMessage;
    }

    public void markAchievementsSeen() {
        newAchievementsAvailable.set(false);
        uiPreferencesStore.saveSeenAchievementIds(latestAchievementIds);
    }

    public ObservableList<String> getRecentAchievements() {
        return recentAchievements;
    }

    public List<Achievement> getNewlyUnlockedAchievements() {
        return newlyUnlockedAchievements;
    }

    private DashboardData loadDashboardData(User user) {
        try {
            var summaryResult = dashboardUseCases.getDashboardSummary(
                    new DashboardUseCases.DashboardSummaryQuery(UserContext.ui(user.getId())));
            if (!summaryResult.success()) {
                return DashboardData.empty(
                        user.getName(),
                        new IllegalStateException(summaryResult.error().message()));
            }

            DashboardUseCases.DashboardSummaryResult summary = summaryResult.data();
            return new DashboardData(
                    summary.userName(),
                    summary.completionText(),
                    summary.dailyStatus().displayText(),
                    String.valueOf(summary.totalMatches()),
                    summary.dailyPick().displayName(),
                    summary.dailyPick().reason(),
                    summary.dailyPick().userId(),
                    summary.dailyPick().alreadySeen(),
                    summary.dailyPick().available(),
                    summary.dailyPick().emptyMessage(),
                    summary.achievementSummary().unlockedAchievements(),
                    summary.unreadSummary().unreadMessages(),
                    summary.unreadSummary().pendingRequests(),
                    summary.unreadSummary().unreadNotifications(),
                    summary.profileNudgeMessage(),
                    null);
        } catch (Exception e) {
            return DashboardData.empty(user.getName(), e);
        }
    }

    private void applyDashboardData(DashboardData data) {
        if (isDisposed()) {
            return;
        }
        userName.set(data.name());
        dailyLikesStatus.set(data.likesText());
        dailyPickName.set(data.pickName());
        dailyPickReason.set(data.pickReason());
        dailyPickUserId.set(data.pickUserId());
        dailyPickSeen.set(data.pickSeen());
        dailyPickAvailable.set(data.pickAvailable());
        dailyPickEmptyMessage.set(data.pickEmptyMessage());
        totalMatches.set(data.matchCount());
        profileCompletion.set(data.completionText());
        unreadMessages.set(data.unreadCount());
        notificationCount.set(data.unreadCount() + data.pendingRequests() + data.unreadNotifications());
        profileNudgeMessage.set(data.profileNudgeMessage());

        recentAchievements.setAll(data.achievements().stream()
                .sorted((left, right) -> right.unlockedAt().compareTo(left.unlockedAt()))
                .limit(3)
                .map(achievement -> achievement.achievement().getIcon() + " "
                        + achievement.achievement().getDisplayName())
                .toList());

        latestAchievementIds = data.achievements().stream()
                .map(achievement -> achievement.id().toString())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> seenAchievementIds = uiPreferencesStore.loadSeenAchievementIds();
        newlyUnlockedAchievements = data.achievements().stream()
                .filter(ua -> !seenAchievementIds.contains(ua.id().toString()))
                .map(DashboardUseCases.UnlockedAchievement::achievement)
                .toList();
        newAchievementsAvailable.set(!newlyUnlockedAchievements.isEmpty());

        if (data.error() != null) {
            String detail = data.error().getMessage();
            notifyError(
                    detail == null || detail.isBlank() ? "Some dashboard data failed to load" : detail, data.error());
        }
    }

    private record DashboardData(
            String name,
            String completionText,
            String likesText,
            String matchCount,
            String pickName,
            String pickReason,
            UUID pickUserId,
            boolean pickSeen,
            boolean pickAvailable,
            String pickEmptyMessage,
            List<DashboardUseCases.UnlockedAchievement> achievements,
            int unreadCount,
            int pendingRequests,
            int unreadNotifications,
            String profileNudgeMessage,
            Exception error) {
        private static DashboardData empty(String name, Exception error) {
            return new DashboardData(
                    name,
                    UNKNOWN_VALUE,
                    DEFAULT_LIKES_TEXT,
                    "0",
                    DEFAULT_PICK_TEXT,
                    "",
                    null,
                    false,
                    false,
                    DEFAULT_PICK_EMPTY_MESSAGE,
                    List.of(),
                    0,
                    0,
                    0,
                    "",
                    error);
        }
    }
}
