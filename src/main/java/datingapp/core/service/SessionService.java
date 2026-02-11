package datingapp.core.service;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.SwipeSession;
import datingapp.core.model.UserInteractions.Like;
import datingapp.core.storage.AnalyticsStorage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class SessionService {

    private final AnalyticsStorage analyticsStorage;
    private final AppConfig config;

    /** Fixed lock stripes to prevent race conditions in swipe recording. */
    private static final int LOCK_STRIPE_COUNT = 256;

    private final Object[] lockStripes;

    public SessionService(AnalyticsStorage analyticsStorage, AppConfig config) {
        this.analyticsStorage = Objects.requireNonNull(analyticsStorage, "analyticsStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.lockStripes = new Object[LOCK_STRIPE_COUNT];
        for (int i = 0; i < LOCK_STRIPE_COUNT; i++) {
            lockStripes[i] = new Object();
        }
    }

    /**
     * Get or create an active session for the user. If existing session is timed out, ends it and
     * creates a new one.
     *
     * @param userId the user ID
     * @return an active session for the user
     */
    public SwipeSession getOrCreateSession(UUID userId) {
        Optional<SwipeSession> existing = analyticsStorage.getActiveSession(userId);

        if (existing.isPresent()) {
            SwipeSession session = existing.get();

            // Check if timed out
            if (session.isTimedOut(config.getSessionTimeout())) {
                session.end();
                analyticsStorage.saveSession(session);
                return createNewSession(userId);
            }

            return session;
        }

        return createNewSession(userId);
    }

    /** Create a new session for the user. */
    private SwipeSession createNewSession(UUID userId) {
        SwipeSession session = SwipeSession.create(userId);
        analyticsStorage.saveSession(session);
        return session;
    }

    /**
     * Record a swipe in the user's current session.
     *
     * @param userId the user ID
     * @param direction the swipe direction (LIKE or PASS)
     * @param matched whether the swipe resulted in a match
     * @return the result of the swipe operation (may be blocked or have warnings)
     */
    public SwipeResult recordSwipe(UUID userId, Like.Direction direction, boolean matched) {
        // Per-user lock to prevent race condition in read-modify-write
        Object lock = lockStripes[Math.floorMod(userId.hashCode(), LOCK_STRIPE_COUNT)];
        synchronized (lock) {
            SwipeSession session = getOrCreateSession(userId);

            // Check for anti-bot limits
            if (session.getSwipeCount() >= config.maxSwipesPerSession()) {
                return SwipeResult.blocked(session, "Session swipe limit reached. Take a break!");
            }

            // Record the swipe
            session.recordSwipe(direction, matched);
            analyticsStorage.saveSession(session);

            // Check for suspicious velocity
            String warning = null;
            if (session.getSwipeCount() >= 10 && session.getSwipesPerMinute() > config.suspiciousSwipeVelocity()) {
                warning = "Unusually fast swiping detected. Take a moment to review profiles!";
            }

            return SwipeResult.success(session, warning);
        }
    }

    /**
     * Update session with a match (when we discover it after the swipe).
     *
     * @param userId the user ID
     */
    public void recordMatch(UUID userId) {
        Optional<SwipeSession> active = analyticsStorage.getActiveSession(userId);
        if (active.isPresent()) {
            SwipeSession session = active.get();
            session.incrementMatchCount();
            analyticsStorage.saveSession(session);
        }
    }

    /**
     * Explicitly end the user's current session.
     *
     * @param userId the user ID
     */
    public void endSession(UUID userId) {
        Optional<SwipeSession> active = analyticsStorage.getActiveSession(userId);
        if (active.isPresent()) {
            SwipeSession session = active.get();
            session.end();
            analyticsStorage.saveSession(session);
        }
    }

    /**
     * Get the current active session for display purposes.
     *
     * @param userId the user ID
     * @return the active session, or empty if none
     */
    public Optional<SwipeSession> getCurrentSession(UUID userId) {
        return analyticsStorage.getActiveSession(userId);
    }

    /**
     * Get session history for display.
     *
     * @param userId the user ID
     * @param limit maximum number of sessions to return
     * @return list of sessions, most recent first
     */
    public List<SwipeSession> getSessionHistory(UUID userId, int limit) {
        return analyticsStorage.getSessionsFor(userId, limit);
    }

    /**
     * Get today's sessions for a user.
     *
     * @param userId the user ID
     * @return list of sessions started today
     */
    public List<SwipeSession> getTodaysSessions(UUID userId) {
        Instant startOfDay = AppClock.today(config.userTimeZone())
                .atStartOfDay(config.userTimeZone())
                .toInstant();
        return analyticsStorage.getSessionsInRange(userId, startOfDay, AppClock.now());
    }

    /**
     * Get aggregate session statistics for a user.
     *
     * @param userId the user ID
     * @return aggregate statistics
     */
    public AnalyticsStorage.SessionAggregates getAggregates(UUID userId) {
        return analyticsStorage.getSessionAggregates(userId);
    }

    /**
     * Cleanup job: end all stale sessions.
     *
     * @return number of sessions ended
     */
    public int cleanupStaleSessions() {
        return analyticsStorage.endStaleSessions(config.getSessionTimeout());
    }

    /**
     * Runs all cleanup operations (expired daily picks, expired sessions) and returns a summary.
     * Idempotent - calling multiple times has no negative effects.
     *
     * @return CleanupResult with counts of deleted records
     */
    public CleanupResult runCleanup() {
        Instant cutoffDate = AppClock.now().minus(config.cleanupRetentionDays(), ChronoUnit.DAYS);
        int dailyPicksDeleted = analyticsStorage.deleteExpiredDailyPickViews(cutoffDate);
        int sessionsDeleted = analyticsStorage.deleteExpiredSessions(cutoffDate);
        return new CleanupResult(dailyPicksDeleted, sessionsDeleted);
    }

    /**
     * Result of a cleanup operation.
     *
     * @param dailyPicksDeleted Number of expired daily pick view records deleted
     * @param sessionsDeleted   Number of expired session records deleted
     */
    public static record CleanupResult(int dailyPicksDeleted, int sessionsDeleted) {

        /** Total number of records deleted across all categories. */
        public int totalDeleted() {
            return dailyPicksDeleted + sessionsDeleted;
        }

        /** Returns true if any records were deleted. */
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

    /** Result of a swipe operation in the context of session tracking. */
    public record SwipeResult(boolean allowed, SwipeSession session, String warning, String blockedReason) {
        public static SwipeResult success(SwipeSession session, String warning) {
            return new SwipeResult(true, session, warning, null);
        }

        public static SwipeResult blocked(SwipeSession session, String reason) {
            return new SwipeResult(false, session, null, reason);
        }

        public boolean hasWarning() {
            return warning != null;
        }
    }
}
