package datingapp.storage.jdbi;

import datingapp.core.model.Achievement;
import datingapp.core.model.Achievement.UserAchievement;
import datingapp.core.model.Stats.PlatformStats;
import datingapp.core.model.Stats.UserStats;
import datingapp.core.model.SwipeSession;
import datingapp.core.storage.AnalyticsStorage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;

/**
 * JDBI adapter that implements {@link AnalyticsStorage} by delegating to the internal
 * {@link JdbiStatsStorage} and {@link JdbiSwipeSessionStorage} SQL Object DAOs.
 */
public final class JdbiAnalyticsStorageAdapter implements AnalyticsStorage {

    private final JdbiStatsStorage statsDao;
    private final JdbiSwipeSessionStorage sessionDao;

    public JdbiAnalyticsStorageAdapter(Jdbi jdbi) {
        Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.statsDao = jdbi.onDemand(JdbiStatsStorage.class);
        this.sessionDao = jdbi.onDemand(JdbiSwipeSessionStorage.class);
    }

    // ═══ User Stats ═══

    @Override
    public void saveUserStats(UserStats stats) {
        statsDao.saveUserStats(stats);
    }

    @Override
    public Optional<UserStats> getLatestUserStats(UUID userId) {
        return statsDao.getLatestUserStats(userId);
    }

    @Override
    public List<UserStats> getUserStatsHistory(UUID userId, int limit) {
        return statsDao.getUserStatsHistory(userId, limit);
    }

    @Override
    public List<UserStats> getAllLatestUserStats() {
        return statsDao.getAllLatestUserStats();
    }

    @Override
    public int deleteUserStatsOlderThan(Instant cutoff) {
        return statsDao.deleteUserStatsOlderThan(cutoff);
    }

    // ═══ Platform Stats ═══

    @Override
    public void savePlatformStats(PlatformStats stats) {
        statsDao.savePlatformStats(stats);
    }

    @Override
    public Optional<PlatformStats> getLatestPlatformStats() {
        return statsDao.getLatestPlatformStats();
    }

    @Override
    public List<PlatformStats> getPlatformStatsHistory(int limit) {
        return statsDao.getPlatformStatsHistory(limit);
    }

    // ═══ Profile Views ═══

    @Override
    public void recordProfileView(UUID viewerId, UUID viewedId) {
        statsDao.recordProfileView(viewerId, viewedId);
    }

    @Override
    public int getProfileViewCount(UUID userId) {
        return statsDao.getProfileViewCount(userId);
    }

    @Override
    public int getUniqueViewerCount(UUID userId) {
        return statsDao.getUniqueViewerCount(userId);
    }

    @Override
    public List<UUID> getRecentViewers(UUID userId, int limit) {
        return statsDao.getRecentViewers(userId, limit);
    }

    @Override
    public boolean hasViewedProfile(UUID viewerId, UUID viewedId) {
        return statsDao.hasViewedProfile(viewerId, viewedId);
    }

    // ═══ Achievements ═══

    @Override
    public void saveUserAchievement(UserAchievement achievement) {
        statsDao.saveUserAchievement(achievement);
    }

    @Override
    public List<UserAchievement> getUnlockedAchievements(UUID userId) {
        return statsDao.getUnlockedAchievements(userId);
    }

    @Override
    public boolean hasAchievement(UUID userId, Achievement achievement) {
        return statsDao.hasAchievement(userId, achievement);
    }

    @Override
    public int countUnlockedAchievements(UUID userId) {
        return statsDao.countUnlockedAchievements(userId);
    }

    // ═══ Daily Picks ═══

    @Override
    public void markDailyPickAsViewed(UUID userId, LocalDate date) {
        statsDao.markDailyPickAsViewed(userId, date);
    }

    @Override
    public boolean isDailyPickViewed(UUID userId, LocalDate date) {
        return statsDao.isDailyPickViewed(userId, date);
    }

    @Override
    public int deleteDailyPickViewsOlderThan(LocalDate before) {
        return statsDao.deleteDailyPickViewsOlderThan(before);
    }

    @Override
    public int deleteExpiredDailyPickViews(Instant cutoff) {
        return statsDao.deleteExpiredDailyPickViews(cutoff);
    }

    // ═══ Swipe Sessions ═══

    @Override
    public void saveSession(SwipeSession session) {
        sessionDao.save(session);
    }

    @Override
    public Optional<SwipeSession> getSession(UUID sessionId) {
        return sessionDao.get(sessionId);
    }

    @Override
    public Optional<SwipeSession> getActiveSession(UUID userId) {
        return sessionDao.getActiveSession(userId);
    }

    @Override
    public List<SwipeSession> getSessionsFor(UUID userId, int limit) {
        return sessionDao.getSessionsFor(userId, limit);
    }

    @Override
    public List<SwipeSession> getSessionsInRange(UUID userId, Instant start, Instant end) {
        return sessionDao.getSessionsInRange(userId, start, end);
    }

    @Override
    public SessionAggregates getSessionAggregates(UUID userId) {
        return sessionDao.getAggregates(userId);
    }

    @Override
    public int endStaleSessions(Duration timeout) {
        return sessionDao.endStaleSessions(timeout);
    }

    @Override
    public int deleteExpiredSessions(Instant cutoff) {
        return sessionDao.deleteExpiredSessions(cutoff);
    }
}
