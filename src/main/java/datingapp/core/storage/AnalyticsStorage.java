package datingapp.core.storage;

import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.PlatformStats;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.metrics.SwipeState.Session;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consolidated storage for analytics: stats, achievements, profile views, daily
 * picks, and swipe
 * sessions. Merges the former {@code StatsStorage} and
 * {@code SwipeSessionStorage} interfaces.
 */
public interface AnalyticsStorage {

    // ═══ User Stats ═══

    void saveUserStats(UserStats stats);

    Optional<UserStats> getLatestUserStats(UUID userId);

    List<UserStats> getUserStatsHistory(UUID userId, int limit);

    List<UserStats> getAllLatestUserStats();

    int deleteUserStatsOlderThan(Instant cutoff);

    // ═══ Platform Stats ═══

    void savePlatformStats(PlatformStats stats);

    Optional<PlatformStats> getLatestPlatformStats();

    List<PlatformStats> getPlatformStatsHistory(int limit);

    // ═══ Profile Views ═══

    void recordProfileView(UUID viewerId, UUID viewedId);

    int getProfileViewCount(UUID userId);

    int getUniqueViewerCount(UUID userId);

    List<UUID> getRecentViewers(UUID userId, int limit);

    boolean hasViewedProfile(UUID viewerId, UUID viewedId);

    // ═══ Achievements ═══

    void saveUserAchievement(UserAchievement achievement);

    List<UserAchievement> getUnlockedAchievements(UUID userId);

    boolean hasAchievement(UUID userId, Achievement achievement);

    int countUnlockedAchievements(UUID userId);

    // ═══ Daily Picks ═══

    void markDailyPickAsViewed(UUID userId, LocalDate date);

    boolean isDailyPickViewed(UUID userId, LocalDate date);

    int deleteDailyPickViewsOlderThan(LocalDate before);

    int deleteExpiredDailyPickViews(Instant cutoff);

    // ═══ Standouts ═══

    int deleteExpiredStandouts(Instant cutoff);

    // ═══ Swipe Sessions ═══

    void saveSession(Session session);

    Optional<Session> getSession(UUID sessionId);

    Optional<Session> getActiveSession(UUID userId);

    List<Session> getSessionsFor(UUID userId, int limit);

    List<Session> getSessionsInRange(UUID userId, Instant start, Instant end);

    SessionAggregates getSessionAggregates(UUID userId);

    int endStaleSessions(Duration timeout);

    int deleteExpiredSessions(Instant cutoff);

    /** Aggregated statistics for a user's swipe sessions. */
    public static record SessionAggregates(
            int totalSessions,
            int totalSwipes,
            int totalLikes,
            int totalPasses,
            int totalMatches,
            double avgSessionDurationSeconds,
            double avgSwipesPerSession,
            double avgSwipeVelocity) {
        public SessionAggregates {
            if (totalSessions < 0 || totalSwipes < 0 || totalLikes < 0 || totalPasses < 0 || totalMatches < 0) {
                throw new IllegalArgumentException("Aggregate counts cannot be negative");
            }
            if (avgSessionDurationSeconds < 0 || avgSwipesPerSession < 0 || avgSwipeVelocity < 0) {
                throw new IllegalArgumentException("Aggregate averages cannot be negative");
            }
        }

        public static SessionAggregates empty() {
            return new SessionAggregates(0, 0, 0, 0, 0, 0.0, 0.0, 0.0);
        }
    }
}
