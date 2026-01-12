package datingapp.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing swipe sessions. Handles session lifecycle (create, update, end) and anti-bot
 * detection.
 */
public class SessionService {

    private final SwipeSessionStorage sessionStorage;
    private final AppConfig config;

    public SessionService(SwipeSessionStorage sessionStorage, AppConfig config) {
        this.sessionStorage = Objects.requireNonNull(sessionStorage, "sessionStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Get or create an active session for the user. If existing session is timed out, ends it and
     * creates a new one.
     *
     * @param userId the user ID
     * @return an active session for the user
     */
    public SwipeSession getOrCreateSession(UUID userId) {
        Optional<SwipeSession> existing = sessionStorage.getActiveSession(userId);

        if (existing.isPresent()) {
            SwipeSession session = existing.get();

            // Check if timed out
            if (session.isTimedOut(config.getSessionTimeout())) {
                session.end();
                sessionStorage.save(session);
                return createNewSession(userId);
            }

            return session;
        }

        return createNewSession(userId);
    }

    /** Create a new session for the user. */
    private SwipeSession createNewSession(UUID userId) {
        SwipeSession session = SwipeSession.create(userId);
        sessionStorage.save(session);
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
        SwipeSession session = getOrCreateSession(userId);

        // Check for anti-bot limits
        if (session.getSwipeCount() >= config.maxSwipesPerSession()) {
            return SwipeResult.blocked(session, "Session swipe limit reached. Take a break!");
        }

        // Record the swipe
        session.recordSwipe(direction, matched);
        sessionStorage.save(session);

        // Check for suspicious velocity
        String warning = null;
        if (session.getSwipeCount() >= 10 && session.getSwipesPerMinute() > config.suspiciousSwipeVelocity()) {
            warning = "Unusually fast swiping detected. Take a moment to review profiles!";
        }

        return SwipeResult.success(session, warning);
    }

    /**
     * Update session with a match (when we discover it after the swipe).
     *
     * @param userId the user ID
     */
    public void recordMatch(UUID userId) {
        Optional<SwipeSession> active = sessionStorage.getActiveSession(userId);
        if (active.isPresent()) {
            SwipeSession session = active.get();
            session.incrementMatchCount();
            sessionStorage.save(session);
        }
    }

    /**
     * Explicitly end the user's current session.
     *
     * @param userId the user ID
     */
    public void endSession(UUID userId) {
        Optional<SwipeSession> active = sessionStorage.getActiveSession(userId);
        if (active.isPresent()) {
            SwipeSession session = active.get();
            session.end();
            sessionStorage.save(session);
        }
    }

    /**
     * Get the current active session for display purposes.
     *
     * @param userId the user ID
     * @return the active session, or empty if none
     */
    public Optional<SwipeSession> getCurrentSession(UUID userId) {
        return sessionStorage.getActiveSession(userId);
    }

    /**
     * Get session history for display.
     *
     * @param userId the user ID
     * @param limit maximum number of sessions to return
     * @return list of sessions, most recent first
     */
    public List<SwipeSession> getSessionHistory(UUID userId, int limit) {
        return sessionStorage.getSessionsFor(userId, limit);
    }

    /**
     * Get today's sessions for a user.
     *
     * @param userId the user ID
     * @return list of sessions started today
     */
    public List<SwipeSession> getTodaysSessions(UUID userId) {
        Instant startOfDay =
                LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return sessionStorage.getSessionsInRange(userId, startOfDay, Instant.now());
    }

    /**
     * Get aggregate session statistics for a user.
     *
     * @param userId the user ID
     * @return aggregate statistics
     */
    public SwipeSessionStorage.SessionAggregates getAggregates(UUID userId) {
        return sessionStorage.getAggregates(userId);
    }

    /**
     * Cleanup job: end all stale sessions.
     *
     * @return number of sessions ended
     */
    public int cleanupStaleSessions() {
        return sessionStorage.endStaleSessions(config.getSessionTimeout());
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
