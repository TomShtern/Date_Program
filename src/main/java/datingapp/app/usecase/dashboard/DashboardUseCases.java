package datingapp.app.usecase.dashboard;

import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.RecommendationService;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** App-layer dashboard aggregation use-cases shared across adapters. */
public final class DashboardUseCases {

    private static final String CONTEXT_REQUIRED = "Context is required";
    private static final String DEFAULT_LIKES_TEXT = "Likes: --";
    private static final String DEFAULT_PICK_TEXT = "No pick today";
    private static final String DEFAULT_PICK_EMPTY_MESSAGE =
            "No daily pick is available right now. Check back tomorrow.";

    private final UserStorage userStorage;
    private final RecommendationService recommendationService;
    private final InteractionStorage interactionStorage;
    private final AchievementService achievementService;
    private final ConnectionService connectionService;
    private final ProfileService profileService;
    private final AppConfig config;

    public DashboardUseCases(
            UserStorage userStorage,
            RecommendationService recommendationService,
            InteractionStorage interactionStorage,
            AchievementService achievementService,
            ConnectionService connectionService,
            ProfileService profileService,
            AppConfig config) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.recommendationService =
                Objects.requireNonNull(recommendationService, "recommendationService cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.achievementService = Objects.requireNonNull(achievementService, "achievementService cannot be null");
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService cannot be null");
        this.profileService = Objects.requireNonNull(profileService, "profileService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public UseCaseResult<DashboardSummaryResult> getDashboardSummary(DashboardSummaryQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }

        try {
            User user = userStorage.get(query.context().userId()).orElse(null);
            if (user == null) {
                return UseCaseResult.failure(UseCaseError.notFound("User not found"));
            }

            String completionText = profileService.calculate(user).getDisplayString();
            RecommendationService.DailyStatus dailyStatus = recommendationService.getStatus(user.getId());
            RecommendationService.DailyPick dailyPick =
                    recommendationService.getDailyPick(user).orElse(null);
            List<UserAchievement> unlockedAchievements = achievementService.getUnlocked(user.getId());

            return UseCaseResult.success(new DashboardSummaryResult(
                    user.getName(),
                    completionText,
                    toDailyStatusSummary(dailyStatus),
                    interactionStorage.countActiveMatchesFor(user.getId()),
                    toDailyPickSummary(dailyPick),
                    toAchievementSummary(unlockedAchievements),
                    new UnreadSummary(
                            connectionService.getTotalUnreadCount(user.getId()),
                            connectionService.countPendingRequestsFor(user.getId()),
                            connectionService.getUnreadNotificationCount(user.getId())),
                    computeProfileNudge(user)));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load dashboard summary: " + e.getMessage()));
        }
    }

    public UseCaseResult<Void> markDailyPickViewed(MarkDailyPickViewedCommand command) {
        if (command == null || command.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }

        try {
            recommendationService.markDailyPickViewed(command.context().userId());
            return UseCaseResult.success(null);
        } catch (Exception e) {
            return UseCaseResult.failure(
                    UseCaseError.internal("Failed to update daily pick status: " + e.getMessage()));
        }
    }

    public record DashboardSummaryQuery(UserContext context) {}

    public record MarkDailyPickViewedCommand(UserContext context) {}

    public record DashboardSummaryResult(
            String userName,
            String completionText,
            DailyStatusSummary dailyStatus,
            int totalMatches,
            DailyPickSummary dailyPick,
            AchievementSummary achievementSummary,
            UnreadSummary unreadSummary,
            String profileNudgeMessage) {}

    public record DailyStatusSummary(String displayText, int likesUsed, int likesRemaining, boolean unlimitedLikes) {}

    public record DailyPickSummary(
            boolean available,
            UUID userId,
            String displayName,
            String reason,
            boolean alreadySeen,
            String emptyMessage) {}

    public record AchievementSummary(List<UnlockedAchievement> unlockedAchievements) {
        public AchievementSummary {
            unlockedAchievements = List.copyOf(unlockedAchievements);
        }
    }

    public record UnlockedAchievement(UUID id, Achievement achievement, Instant unlockedAt) {}

    public record UnreadSummary(int unreadMessages, int pendingRequests, int unreadNotifications) {
        public int notificationCount() {
            return unreadMessages + pendingRequests + unreadNotifications;
        }
    }

    private DailyStatusSummary toDailyStatusSummary(RecommendationService.DailyStatus status) {
        return new DailyStatusSummary(
                buildLikesText(status), status.likesUsed(), status.likesRemaining(), status.hasUnlimitedLikes());
    }

    private DailyPickSummary toDailyPickSummary(RecommendationService.DailyPick dailyPick) {
        if (dailyPick == null) {
            return new DailyPickSummary(false, null, DEFAULT_PICK_TEXT, "", false, DEFAULT_PICK_EMPTY_MESSAGE);
        }

        User pickUser = dailyPick.user();
        int age = pickUser.getAge(config.safety().userTimeZone()).orElse(0);
        return new DailyPickSummary(
                true,
                pickUser.getId(),
                pickUser.getName() + ", " + age,
                dailyPick.reason(),
                dailyPick.alreadySeen(),
                "");
    }

    private static AchievementSummary toAchievementSummary(List<UserAchievement> unlockedAchievements) {
        List<UnlockedAchievement> achievements = unlockedAchievements.stream()
                .map(achievement ->
                        new UnlockedAchievement(achievement.id(), achievement.achievement(), achievement.unlockedAt()))
                .toList();
        return new AchievementSummary(achievements);
    }

    private static String buildLikesText(RecommendationService.DailyStatus status) {
        if (status == null) {
            return DEFAULT_LIKES_TEXT;
        }
        return status.hasUnlimitedLikes()
                ? "Likes: ∞"
                : "Likes: " + status.likesUsed() + "/"
                        + (status.likesUsed() + status.likesRemaining()); // NOPMD UselessParentheses
    }

    private String computeProfileNudge(User user) {
        if (user.getBio() == null || user.getBio().isBlank()) {
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
        if (profileService.countLifestyleFields(user) < 3) {
            return "Add a few lifestyle details to improve match quality.";
        }
        return "";
    }
}
