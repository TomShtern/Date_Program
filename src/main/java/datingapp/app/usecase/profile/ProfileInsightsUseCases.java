package datingapp.app.usecase.profile;

import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.metrics.SwipeState.Session;
import java.util.List;
import java.util.UUID;

/** Read-only profile insight use-cases for achievements, stats, and session summary. */
public class ProfileInsightsUseCases {

    private static final String CONTEXT_REQUIRED = "Context is required";
    private static final String ACTIVITY_METRICS_REQUIRED = "ActivityMetricsService is required";

    private final AchievementService achievementService;
    private final ActivityMetricsService activityMetricsService;

    public ProfileInsightsUseCases(
            AchievementService achievementService, ActivityMetricsService activityMetricsService) {
        this.achievementService = achievementService;
        this.activityMetricsService = activityMetricsService;
    }

    public UseCaseResult<AchievementSnapshot> getAchievements(AchievementsQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (achievementService == null) {
            return UseCaseResult.failure(UseCaseError.dependency("AchievementService is required"));
        }

        try {
            List<UserAchievement> newlyUnlocked = query.checkForNew()
                    ? achievementService.checkAndUnlock(query.context().userId())
                    : List.of();
            List<UserAchievement> unlocked =
                    achievementService.getUnlocked(query.context().userId());
            return UseCaseResult.success(new AchievementSnapshot(unlocked, newlyUnlocked));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load achievements: " + e.getMessage()));
        }
    }

    public UseCaseResult<UserStats> getOrComputeStats(StatsQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (activityMetricsService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(ACTIVITY_METRICS_REQUIRED));
        }
        try {
            return UseCaseResult.success(getOrComputeStats(query.context().userId()));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load user statistics: " + e.getMessage()));
        }
    }

    UserStats getOrComputeStats(UUID userId) {
        return activityMetricsService.getOrComputeStats(userId);
    }

    public UseCaseResult<SessionSummaryResult> getSessionSummary(SessionSummaryQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (activityMetricsService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(ACTIVITY_METRICS_REQUIRED));
        }
        try {
            return UseCaseResult.success(new SessionSummaryResult(activityMetricsService
                    .getCurrentSession(query.context().userId())
                    .map(ProfileInsightsUseCases::toCurrentSessionSnapshot)));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load session summary: " + e.getMessage()));
        }
    }

    public UseCaseResult<List<Session>> getSessionHistory(SessionHistoryQuery query) {
        if (query == null || query.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation(CONTEXT_REQUIRED));
        }
        if (activityMetricsService == null) {
            return UseCaseResult.failure(UseCaseError.dependency(ACTIVITY_METRICS_REQUIRED));
        }

        try {
            return UseCaseResult.success(List.copyOf(
                    activityMetricsService.getSessionHistory(query.context().userId(), query.limit())));
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internal("Failed to load session history: " + e.getMessage()));
        }
    }

    public static record AchievementsQuery(UserContext context, boolean checkForNew) {}

    public static record AchievementSnapshot(List<UserAchievement> unlocked, List<UserAchievement> newlyUnlocked) {
        public AchievementSnapshot {
            unlocked = List.copyOf(unlocked);
            newlyUnlocked = List.copyOf(newlyUnlocked);
        }
    }

    public static record StatsQuery(UserContext context) {}

    public static record SessionHistoryQuery(UserContext context, int limit) {
        public SessionHistoryQuery {
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive");
            }
        }
    }

    public static record SessionSummaryQuery(UserContext context) {}

    public static record CurrentSessionSnapshot(
            int swipeCount, int likeCount, int passCount, String formattedDuration) {}

    public static record SessionSummaryResult(java.util.Optional<CurrentSessionSnapshot> currentSession) {}

    private static CurrentSessionSnapshot toCurrentSessionSnapshot(datingapp.core.metrics.SwipeState.Session session) {
        return new CurrentSessionSnapshot(
                session.getSwipeCount(),
                session.getLikeCount(),
                session.getPassCount(),
                session.getFormattedDuration());
    }
}
