package datingapp.core.metrics;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.EngagementDomain.PlatformStats;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.model.Match;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Consolidated service for session tracking and metrics/statistics computation. */
public class ActivityMetricsService {

    private static final int LOCK_STRIPE_COUNT = 256;

    private final InteractionStorage interactionStorage;
    private final TrustSafetyStorage trustSafetyStorage;
    private final AnalyticsStorage analyticsStorage;
    private final AppConfig config;
    private final Object[] lockStripes;

    public ActivityMetricsService(AnalyticsStorage analyticsStorage, AppConfig config) {
        this(null, null, analyticsStorage, config);
    }

    public ActivityMetricsService(
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            AnalyticsStorage analyticsStorage) {
        this(interactionStorage, trustSafetyStorage, analyticsStorage, AppConfig.defaults());
    }

    public ActivityMetricsService(
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            AnalyticsStorage analyticsStorage,
            AppConfig config) {
        this.interactionStorage = interactionStorage;
        this.trustSafetyStorage = trustSafetyStorage;
        this.analyticsStorage = Objects.requireNonNull(analyticsStorage, "analyticsStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.lockStripes = new Object[LOCK_STRIPE_COUNT];
        for (int i = 0; i < LOCK_STRIPE_COUNT; i++) {
            lockStripes[i] = new Object();
        }
    }

    private void ensureStatsDependencies() {
        if (interactionStorage == null || trustSafetyStorage == null) {
            throw new IllegalStateException("Stats dependencies are not configured");
        }
    }

    public Session getOrCreateSession(UUID userId) {
        Optional<Session> existing = analyticsStorage.getActiveSession(userId);

        if (existing.isPresent()) {
            Session session = existing.get();
            if (session.isTimedOut(config.getSessionTimeout())) {
                session.end();
                analyticsStorage.saveSession(session);
                return createNewSession(userId);
            }
            return session;
        }

        return createNewSession(userId);
    }

    private Session createNewSession(UUID userId) {
        Session session = Session.create(userId);
        analyticsStorage.saveSession(session);
        return session;
    }

    public SwipeResult recordSwipe(UUID userId, Like.Direction direction, boolean matched) {
        Object lock = lockStripes[Math.floorMod(userId.hashCode(), LOCK_STRIPE_COUNT)];
        synchronized (lock) {
            Session session = getOrCreateSession(userId);

            if (session.getSwipeCount() >= config.maxSwipesPerSession()) {
                return SwipeResult.blocked(session, "Session swipe limit reached. Take a break!");
            }

            session.recordSwipe(direction, matched);
            analyticsStorage.saveSession(session);

            String warning = null;
            if (session.getSwipeCount() >= 10 && session.getSwipesPerMinute() > config.suspiciousSwipeVelocity()) {
                warning = "Unusually fast swiping detected. Take a moment to review profiles!";
            }

            return SwipeResult.success(session, warning);
        }
    }

    public void recordMatch(UUID userId) {
        Optional<Session> active = analyticsStorage.getActiveSession(userId);
        if (active.isPresent()) {
            Session session = active.get();
            session.incrementMatchCount();
            analyticsStorage.saveSession(session);
        }
    }

    public void endSession(UUID userId) {
        Optional<Session> active = analyticsStorage.getActiveSession(userId);
        if (active.isPresent()) {
            Session session = active.get();
            session.end();
            analyticsStorage.saveSession(session);
        }
    }

    public Optional<Session> getCurrentSession(UUID userId) {
        return analyticsStorage.getActiveSession(userId);
    }

    public List<Session> getSessionHistory(UUID userId, int limit) {
        return analyticsStorage.getSessionsFor(userId, limit);
    }

    public List<Session> getTodaysSessions(UUID userId) {
        Instant startOfDay = AppClock.today(config.userTimeZone())
                .atStartOfDay(config.userTimeZone())
                .toInstant();
        return analyticsStorage.getSessionsInRange(userId, startOfDay, AppClock.now());
    }

    public AnalyticsStorage.SessionAggregates getAggregates(UUID userId) {
        return analyticsStorage.getSessionAggregates(userId);
    }

    public int cleanupStaleSessions() {
        return analyticsStorage.endStaleSessions(config.getSessionTimeout());
    }

    public CleanupResult runCleanup() {
        Instant cutoffDate = AppClock.now().minus(config.cleanupRetentionDays(), ChronoUnit.DAYS);
        int dailyPicksDeleted = analyticsStorage.deleteExpiredDailyPickViews(cutoffDate);
        int sessionsDeleted = analyticsStorage.deleteExpiredSessions(cutoffDate);
        return new CleanupResult(dailyPicksDeleted, sessionsDeleted);
    }

    public UserStats computeAndSaveStats(UUID userId) {
        ensureStatsDependencies();
        UserStats.StatsBuilder builder = new UserStats.StatsBuilder();

        builder.likesGiven = interactionStorage.countByDirection(userId, Like.Direction.LIKE);
        builder.passesGiven = interactionStorage.countByDirection(userId, Like.Direction.PASS);
        builder.totalSwipesGiven = builder.likesGiven + builder.passesGiven;
        builder.likeRatio = builder.totalSwipesGiven > 0 ? (double) builder.likesGiven / builder.totalSwipesGiven : 0.0;

        builder.likesReceived = interactionStorage.countReceivedByDirection(userId, Like.Direction.LIKE);
        builder.passesReceived = interactionStorage.countReceivedByDirection(userId, Like.Direction.PASS);
        builder.totalSwipesReceived = builder.likesReceived + builder.passesReceived;
        builder.incomingLikeRatio =
                builder.totalSwipesReceived > 0 ? (double) builder.likesReceived / builder.totalSwipesReceived : 0.0;

        List<Match> allMatches = interactionStorage.getAllMatchesFor(userId);
        builder.totalMatches = allMatches.size();
        builder.activeMatches =
                (int) allMatches.stream().filter(Match::isActive).count();
        builder.matchRate =
                builder.likesGiven > 0 ? Math.min(1.0, (double) builder.totalMatches / builder.likesGiven) : 0.0;

        builder.blocksGiven = trustSafetyStorage.countBlocksGiven(userId);
        builder.blocksReceived = trustSafetyStorage.countBlocksReceived(userId);
        builder.reportsGiven = trustSafetyStorage.countReportsBy(userId);
        builder.reportsReceived = trustSafetyStorage.countReportsAgainst(userId);

        int mutualLikes = interactionStorage.countMutualLikes(userId);
        builder.reciprocityScore =
                builder.likesGiven > 0 ? Math.min(1.0, (double) mutualLikes / builder.likesGiven) : 0.0;

        Optional<PlatformStats> platformStats = analyticsStorage.getLatestPlatformStats();
        if (platformStats.isPresent()) {
            PlatformStats ps = platformStats.get();
            if (ps.avgLikeRatio() > 0) {
                double selectivenessRaw = 1.0 - (builder.likeRatio / ps.avgLikeRatio());
                builder.selectivenessScore = Math.clamp(0.5 + selectivenessRaw * 0.5, 0.0, 1.0);
            }
            if (ps.avgLikesReceived() > 0) {
                double attractivenessRaw = builder.likesReceived / ps.avgLikesReceived();
                builder.attractivenessScore = Math.clamp(attractivenessRaw / 2.0, 0.0, 1.0);
            }
        }

        UserStats stats = UserStats.create(userId, builder);
        analyticsStorage.saveUserStats(stats);
        return stats;
    }

    public UserStats getOrComputeStats(UUID userId) {
        ensureStatsDependencies();
        Optional<UserStats> existing = analyticsStorage.getLatestUserStats(userId);
        if (existing.isPresent()) {
            Duration age = Duration.between(existing.get().computedAt(), AppClock.now());
            if (age.toHours() < 24) {
                return existing.get();
            }
        }
        return computeAndSaveStats(userId);
    }

    public Optional<UserStats> getStats(UUID userId) {
        return analyticsStorage.getLatestUserStats(userId);
    }

    public PlatformStats computeAndSavePlatformStats() {
        ensureStatsDependencies();
        List<UserStats> allStats = analyticsStorage.getAllLatestUserStats();

        if (allStats.isEmpty()) {
            PlatformStats stats = PlatformStats.empty();
            analyticsStorage.savePlatformStats(stats);
            return stats;
        }

        double totalLikesReceived = 0;
        double totalLikesGiven = 0;
        double totalMatchRate = 0;
        double totalLikeRatio = 0;

        for (UserStats s : allStats) {
            totalLikesReceived += s.likesReceived();
            totalLikesGiven += s.likesGiven();
            totalMatchRate += s.matchRate();
            totalLikeRatio += s.likeRatio();
        }

        int count = allStats.size();
        PlatformStats stats = PlatformStats.create(
                count,
                totalLikesReceived / count,
                totalLikesGiven / count,
                totalMatchRate / count,
                totalLikeRatio / count);
        analyticsStorage.savePlatformStats(stats);
        return stats;
    }

    public Optional<PlatformStats> getPlatformStats() {
        return analyticsStorage.getLatestPlatformStats();
    }

    public static record CleanupResult(int dailyPicksDeleted, int sessionsDeleted) {
        public int totalDeleted() {
            return dailyPicksDeleted + sessionsDeleted;
        }

        public boolean hadWork() {
            return totalDeleted() > 0;
        }

        @Override
        public String toString() {
            return "CleanupResult[dailyPicks=" + dailyPicksDeleted
                    + ", sessions=" + sessionsDeleted
                    + ", total=" + totalDeleted() + "]";
        }
    }

    public record SwipeResult(boolean allowed, Session session, String warning, String blockedReason) {
        public static SwipeResult success(Session session, String warning) {
            return new SwipeResult(true, session, warning, null);
        }

        public static SwipeResult blocked(Session session, String reason) {
            return new SwipeResult(false, session, null, reason);
        }

        public boolean hasWarning() {
            return warning != null;
        }
    }
}
