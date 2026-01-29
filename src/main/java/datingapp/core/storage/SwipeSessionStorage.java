package datingapp.core.storage;

import datingapp.core.SwipeSession;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage interface for SwipeSession entities.
 * Provides CRUD operations and aggregate queries for session analytics.
 */
public interface SwipeSessionStorage {

    /** Save a new session or update existing. */
    void save(SwipeSession session);

    /** Get a session by ID. */
    Optional<SwipeSession> get(UUID sessionId);

    /** Get the currently active session for a user, if any. */
    Optional<SwipeSession> getActiveSession(UUID userId);

    /**
     * Get recent sessions for a user (most recent first).
     *
     * @param userId the user ID
     * @param limit maximum number of sessions to return
     */
    List<SwipeSession> getSessionsFor(UUID userId, int limit);

    /**
     * Get sessions for a user within a time range.
     *
     * @param userId the user ID
     * @param start start of time range (inclusive)
     * @param end end of time range (inclusive)
     */
    List<SwipeSession> getSessionsInRange(UUID userId, Instant start, Instant end);

    /** Get aggregate session stats for a user. */
    SessionAggregates getAggregates(UUID userId);

    /**
     * End all active sessions older than timeout (cleanup job).
     *
     * @param timeout the inactivity duration after which sessions should be ended
     * @return number of sessions ended
     */
    int endStaleSessions(Duration timeout);

    /** Aggregate statistics across all sessions for a user. */
    public record SessionAggregates(
            int totalSessions,
            int totalSwipes,
            int totalLikes,
            int totalPasses,
            int totalMatches,
            double avgSessionDurationSeconds,
            double avgSwipesPerSession,
            double avgSwipeVelocity) {
        /** Empty aggregates for users with no sessions. */
        public static SessionAggregates empty() {
            return new SessionAggregates(0, 0, 0, 0, 0, 0.0, 0.0, 0.0);
        }
    }
}
