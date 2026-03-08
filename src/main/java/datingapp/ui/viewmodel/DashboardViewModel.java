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
import datingapp.ui.UiPreferencesStore;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.UiMatchDataAccess;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    private final RecommendationService dailyService;
    private final UiMatchDataAccess matchData;
    private final ProfileService achievementService;
    private final ConnectionService messagingService;
    private final ProfileService profileCompletionService;
    private final AppConfig config;
    private final AppSession session;
    private final UiPreferencesStore uiPreferencesStore;

    private final StringProperty userName = new SimpleStringProperty("Not Logged In");
    private final StringProperty dailyLikesStatus = new SimpleStringProperty(DEFAULT_LIKES_TEXT);
    private final StringProperty dailyPickName = new SimpleStringProperty(DEFAULT_PICK_TEXT);
    private final ObjectProperty<UUID> dailyPickUserId = new SimpleObjectProperty<>();
    private final StringProperty totalMatches = new SimpleStringProperty("0");
    private final StringProperty profileCompletion = new SimpleStringProperty("0% Starter");
    private final IntegerProperty notificationCount = new SimpleIntegerProperty(0);
    private final IntegerProperty unreadMessages = new SimpleIntegerProperty(0);
    private final IntegerProperty friendRequestsCount = new SimpleIntegerProperty(0);
    private final ObservableList<String> recentAchievements = FXCollections.observableArrayList();
    private final BooleanProperty newAchievementsAvailable = new SimpleBooleanProperty(false);
    private final StringProperty profileNudgeMessage = new SimpleStringProperty("");

    private Set<String> latestAchievementIds = Set.of();

    private ViewModelErrorSink errorHandler;

    public record Dependencies(
            RecommendationService dailyService,
            UiMatchDataAccess matchData,
            ProfileService achievementService,
            ConnectionService messagingService,
            ProfileService profileCompletionService,
            AppConfig config) {
        public static Dependencies fromServices(datingapp.core.ServiceRegistry services) {
            return new Dependencies(
                    services.getRecommendationService(),
                    new UiDataAdapters.StorageUiMatchDataAccess(
                            services.getInteractionStorage(), services.getTrustSafetyStorage()),
                    services.getProfileService(),
                    services.getConnectionService(),
                    services.getProfileService(),
                    services.getConfig());
        }
    }

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
        this.dailyService = Objects.requireNonNull(resolvedDependencies.dailyService(), "dailyService cannot be null");
        this.matchData = Objects.requireNonNull(resolvedDependencies.matchData(), "matchData cannot be null");
        this.achievementService =
                Objects.requireNonNull(resolvedDependencies.achievementService(), "achievementService cannot be null");
        this.messagingService =
                Objects.requireNonNull(resolvedDependencies.messagingService(), "messagingService cannot be null");
        this.profileCompletionService = Objects.requireNonNull(
                resolvedDependencies.profileCompletionService(), "profileCompletionService cannot be null");
        this.config = Objects.requireNonNull(resolvedDependencies.config(), "config cannot be null");
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
        this.errorHandler = handler;
    }

    public void logout() {
        session.logout();
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

    private DashboardData loadDashboardData(User user) {
        try {
            String completionText = profileCompletionService.calculate(user).getDisplayString();
            DailyStatus status = dailyService.getStatus(user.getId());
            String likesText = status.hasUnlimitedLikes()
                    ? "Likes: ∞"
                    : "Likes: " + status.likesUsed() + "/" + (status.likesUsed() + status.likesRemaining());

            int activeMatchCount = matchData.countActiveMatchesFor(user.getId());
            Optional<DailyPick> dailyPick = dailyService.getDailyPick(user);
            String pickName = DEFAULT_PICK_TEXT;
            UUID pickUserId = null;
            if (dailyPick.isPresent()) {
                User pickUser = dailyPick.get().user();
                int age = pickUser.getAge(config.safety().userTimeZone());
                pickName = pickUser.getName() + ", " + age;
                pickUserId = pickUser.getId();
            }

            List<UserAchievement> achievements = achievementService.getUnlocked(user.getId());
            int unreadCount = messagingService.getTotalUnreadCount(user.getId());
            int pendingRequests = messagingService.countPendingRequestsFor(user.getId());
            int unreadNotifications = messagingService.getUnreadNotificationCount(user.getId());
            String nudgeMessage = computeProfileNudge(user);

            return new DashboardData(
                    user.getName(),
                    completionText,
                    likesText,
                    String.valueOf(activeMatchCount),
                    pickName,
                    pickUserId,
                    achievements,
                    unreadCount,
                    pendingRequests,
                    unreadNotifications,
                    nudgeMessage,
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
        dailyPickUserId.set(data.pickUserId());
        totalMatches.set(data.matchCount());
        profileCompletion.set(data.completionText());
        unreadMessages.set(data.unreadCount());
        friendRequestsCount.set(data.pendingRequests());
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
        newAchievementsAvailable.set(latestAchievementIds.stream().anyMatch(id -> !seenAchievementIds.contains(id)));

        if (data.error() != null && errorHandler != null) {
            String detail = data.error().getMessage();
            errorHandler.onError(detail == null || detail.isBlank() ? "Some dashboard data failed to load" : detail);
        }
    }

    private String computeProfileNudge(User user) {
        if (user == null) {
            return "";
        }
        String bioText = user.getBio();
        if (bioText == null || bioText.isBlank()) {
            return "Add a bio to boost your profile!";
        }
        if (user.getPhotoUrls() == null || user.getPhotoUrls().isEmpty()) {
            return "Upload a photo to get more matches!";
        }
        if (!user.hasLocation()) {
            return "Set your location to discover people nearby.";
        }
        if (user.getInterests() == null || user.getInterests().size() < 3) {
            return "Pick at least 3 interests so your personality shines.";
        }
        if (profileCompletionService.countLifestyleFields(user) < 3) {
            return "Add a few lifestyle details to improve match quality.";
        }
        return "";
    }

    private record DashboardData(
            String name,
            String completionText,
            String likesText,
            String matchCount,
            String pickName,
            UUID pickUserId,
            List<UserAchievement> achievements,
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
                    null,
                    List.of(),
                    0,
                    0,
                    0,
                    "",
                    error);
        }
    }
}
