package datingapp.core.metrics;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.EngagementDomain.PlatformStats;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.model.Match;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.OperationalInteractionStorage;
import datingapp.core.storage.OperationalUserStorage;
import datingapp.core.storage.TrustSafetyStorage;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

/**
 * Consolidated service for session tracking and metrics/statistics computation.
 *
 * <p>The five-argument constructor is the canonical runtime path. The four-argument constructor exists for
 * compatibility and test wiring that does not need user-storage-backed metric aggregation.
 */
public class ActivityMetricsService {

    private static final int LOCK_STRIPE_COUNT = 256;
    private static final String SUSPICIOUS_VELOCITY_WARNING =
            "Unusually fast swiping detected. Take a moment to review profiles!";
    private static final String SUSPICIOUS_VELOCITY_BLOCKED = "Unusually fast swiping detected. Swipe blocked for now.";

    private final OperationalUserStorage userStorage;
    private final OperationalInteractionStorage interactionStorage;
    private final TrustSafetyStorage trustSafetyStorage;
    private final AnalyticsStorage analyticsStorage;
    private final AppConfig config;
    private final Object[] lockStripes;
    private final LongAdder swipeLimitBlockedCount = new LongAdder();
    private final LongAdder velocityWarningCount = new LongAdder();
    private final LongAdder velocityBlockedCount = new LongAdder();
    private final LongAdder recordMatchNoOpCount = new LongAdder();
    private final LongAdder endSessionNoOpCount = new LongAdder();

    /** Compatibility/test constructor when user-storage-backed aggregation is not needed. */
    public ActivityMetricsService(
            OperationalInteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            AnalyticsStorage analyticsStorage,
            AppConfig config) {
        this(null, interactionStorage, trustSafetyStorage, analyticsStorage, config, false);
    }

    /** Canonical runtime constructor — all dependencies are required. */
    public ActivityMetricsService(
            OperationalUserStorage userStorage,
            OperationalInteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            AnalyticsStorage analyticsStorage,
            AppConfig config) {
        this(userStorage, interactionStorage, trustSafetyStorage, analyticsStorage, config, true);
    }

    private ActivityMetricsService(
            OperationalUserStorage userStorage,
            OperationalInteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            AnalyticsStorage analyticsStorage,
            AppConfig config,
            boolean requireUserStorage) {
        this.userStorage =
                requireUserStorage ? Objects.requireNonNull(userStorage, "userStorage cannot be null") : userStorage;
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
        this.analyticsStorage = Objects.requireNonNull(analyticsStorage, "analyticsStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.lockStripes = new Object[LOCK_STRIPE_COUNT];
        for (int i = 0; i < LOCK_STRIPE_COUNT; i++) {
            lockStripes[i] = new Object();
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

    public SwipeGateResult recordSwipe(UUID userId, Like.Direction direction, boolean matched) {
        Object lock = lockStripes[Math.floorMod(userId.hashCode(), LOCK_STRIPE_COUNT)];
        synchronized (lock) {
            Session session = getOrCreateSession(userId);

            if (session.getSwipeCount() >= config.matching().maxSwipesPerSession()) {
                swipeLimitBlockedCount.increment();
                return SwipeGateResult.blocked(session, "Session swipe limit reached. Take a break!");
            }

            int projectedSwipeCount = session.getSwipeCount() + 1;
            if (isSuspiciousSwipeVelocity(session, projectedSwipeCount)) {
                if (config.matching().suspiciousSwipeVelocityBlockingEnabled()) {
                    velocityBlockedCount.increment();
                    return SwipeGateResult.blocked(session, SUSPICIOUS_VELOCITY_BLOCKED);
                }

                persistSwipe(session, direction, matched);
                velocityWarningCount.increment();
                return SwipeGateResult.success(session, SUSPICIOUS_VELOCITY_WARNING);
            }

            persistSwipe(session, direction, matched);
            return SwipeGateResult.success(session, null);
        }
    }

    private void persistSwipe(Session session, Like.Direction direction, boolean matched) {
        session.recordSwipe(direction, matched);
        analyticsStorage.saveSession(session);
    }

    private boolean isSuspiciousSwipeVelocity(Session session, int projectedSwipeCount) {
        return projectedSwipeCount >= 10
                && getProjectedSwipesPerMinute(session, projectedSwipeCount)
                        > config.matching().suspiciousSwipeVelocity();
    }

    private static double getProjectedSwipesPerMinute(Session session, int projectedSwipeCount) {
        long durationSeconds = session.getDurationSeconds();
        if (durationSeconds == 0) {
            return projectedSwipeCount;
        }
        return projectedSwipeCount * 60.0 / durationSeconds;
    }

    public void recordActivity(UUID userId) {
        Object lock = lockStripes[Math.floorMod(userId.hashCode(), LOCK_STRIPE_COUNT)];
        synchronized (lock) {
            Session session = getOrCreateSession(userId);
            session.recordActivity();
            analyticsStorage.saveSession(session);
        }
    }

    public void recordMatch(UUID userId) {
        Object lock = lockStripes[Math.floorMod(userId.hashCode(), LOCK_STRIPE_COUNT)];
        synchronized (lock) {
            Optional<Session> active = analyticsStorage.getActiveSession(userId);
            if (active.isPresent()) {
                Session session = active.get();
                session.incrementMatchCount();
                analyticsStorage.saveSession(session);
            } else {
                recordMatchNoOpCount.increment();
            }
        }
    }

    public void endSession(UUID userId) {
        Object lock = lockStripes[Math.floorMod(userId.hashCode(), LOCK_STRIPE_COUNT)];
        synchronized (lock) {
            Optional<Session> active = analyticsStorage.getActiveSession(userId);
            if (active.isPresent()) {
                Session session = active.get();
                session.end();
                analyticsStorage.saveSession(session);
            } else {
                endSessionNoOpCount.increment();
            }
        }
    }

    public DiagnosticsSnapshot getDiagnosticsSnapshot() {
        return new DiagnosticsSnapshot(
                swipeLimitBlockedCount.sum(),
                velocityWarningCount.sum(),
                velocityBlockedCount.sum(),
                recordMatchNoOpCount.sum(),
                endSessionNoOpCount.sum());
    }

    public Optional<Session> getCurrentSession(UUID userId) {
        return analyticsStorage.getActiveSession(userId);
    }

    public List<Session> getSessionHistory(UUID userId, int limit) {
        return analyticsStorage.getSessionsFor(userId, limit);
    }

    public List<Session> getTodaysSessions(UUID userId) {
        Instant startOfDay = AppClock.today(config.safety().userTimeZone())
                .atStartOfDay(config.safety().userTimeZone())
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
        Instant cutoffDate = AppClock.now().minus(config.safety().cleanupRetentionDays(), ChronoUnit.DAYS);
        int dailyPicksDeleted = analyticsStorage.deleteExpiredDailyPickViews(cutoffDate);
        int sessionsDeleted = analyticsStorage.deleteExpiredSessions(cutoffDate);
        int standoutsDeleted = analyticsStorage.deleteExpiredStandouts(cutoffDate);
        int usersDeleted = userStorage != null ? userStorage.purgeDeletedBefore(cutoffDate) : 0;
        int interactionsDeleted = interactionStorage.purgeDeletedBefore(cutoffDate);
        return new CleanupResult(
                dailyPicksDeleted, sessionsDeleted, standoutsDeleted, usersDeleted, interactionsDeleted);
    }

    public UserStats computeAndSaveStats(UUID userId) {
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

    public static record CleanupResult(
            int dailyPicksDeleted,
            int sessionsDeleted,
            int standoutsDeleted,
            int usersDeleted,
            int interactionsDeleted) {
        public int totalDeleted() {
            return dailyPicksDeleted + sessionsDeleted + standoutsDeleted + usersDeleted + interactionsDeleted;
        }

        public boolean hadWork() {
            return totalDeleted() > 0;
        }

        @Override
        public String toString() {
            return "CleanupResult[dailyPicksDeleted=" + dailyPicksDeleted
                    + ", sessionsDeleted=" + sessionsDeleted
                    + ", standoutsDeleted=" + standoutsDeleted
                    + ", usersDeleted=" + usersDeleted
                    + ", interactionsDeleted=" + interactionsDeleted
                    + ", total=" + totalDeleted() + "]";
        }
    }

    public static record SwipeGateResult(boolean allowed, Session session, String warning, String blockedReason) {
        public static SwipeGateResult success(Session session, String warning) {
            return new SwipeGateResult(true, session, warning, null);
        }

        public static SwipeGateResult blocked(Session session, String reason) {
            return new SwipeGateResult(false, session, null, reason);
        }

        public boolean hasWarning() {
            return warning != null;
        }
    }

    public static record DiagnosticsSnapshot(
            long swipeLimitBlockedCount,
            long velocityWarningCount,
            long velocityBlockedCount,
            long recordMatchNoOpCount,
            long endSessionNoOpCount) {
        public long totalNotableEvents() {
            return swipeLimitBlockedCount
                    + velocityWarningCount
                    + velocityBlockedCount
                    + recordMatchNoOpCount
                    + endSessionNoOpCount;
        }

        public boolean hasAnyEvents() {
            return totalNotableEvents() > 0;
        }
    }
}
