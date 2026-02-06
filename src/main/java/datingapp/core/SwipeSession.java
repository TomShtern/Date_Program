package datingapp.core;

import datingapp.core.UserInteractions.Like;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a user's swiping session. A session tracks continuous swiping activity with metrics
 * like swipe count, like ratio, and velocity (swipes per minute).
 *
 * <p>Sessions are mutable (unlike Like/Match) because they're updated frequently during active
 * swiping.
 */
public class SwipeSession {

    /** Session state. */
    public enum State {
        ACTIVE, // Currently in progress
        COMPLETED // Ended (timeout or explicit)
    }

    private final UUID id;
    private final UUID userId;
    private final Instant startedAt;
    private Instant lastActivityAt;
    private Instant endedAt; // null if active
    private State state;

    // Counters
    private int swipeCount;
    private int likeCount;
    private int passCount;
    private int matchCount;

    /** Create a new active session. */
    public SwipeSession(UUID id, UUID userId, Instant startedAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt cannot be null");
        this.lastActivityAt = startedAt;
        this.state = State.ACTIVE;
        this.swipeCount = 0;
        this.likeCount = 0;
        this.passCount = 0;
        this.matchCount = 0;
    }

    /** Full constructor for loading from database. */
    @SuppressWarnings("java:S107")
    public SwipeSession(
            UUID id,
            UUID userId,
            Instant startedAt,
            Instant lastActivityAt,
            Instant endedAt,
            State state,
            int swipeCount,
            int likeCount,
            int passCount,
            int matchCount) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.lastActivityAt = Objects.requireNonNull(lastActivityAt);
        this.endedAt = endedAt;
        this.state = Objects.requireNonNull(state);
        // Validate counts are non-negative
        if (swipeCount < 0 || likeCount < 0 || passCount < 0 || matchCount < 0) {
            throw new IllegalArgumentException("Counts cannot be negative");
        }
        // Validate logical constraints
        if (matchCount > likeCount) {
            throw new IllegalArgumentException("matchCount cannot exceed likeCount");
        }
        if (likeCount + passCount != swipeCount) {
            throw new IllegalArgumentException("likeCount + passCount must equal swipeCount");
        }
        this.swipeCount = swipeCount;
        this.likeCount = likeCount;
        this.passCount = passCount;
        this.matchCount = matchCount;
    }

    /** Factory for creating a new session. */
    public static SwipeSession create(UUID userId) {
        return new SwipeSession(UUID.randomUUID(), userId, Instant.now());
    }

    /**
     * Record a swipe in this session.
     *
     * @param direction the swipe direction (LIKE or PASS)
     * @param matched whether this swipe resulted in a match
     * @throws IllegalStateException if session is already completed
     */
    public void recordSwipe(Like.Direction direction, boolean matched) {
        if (state != State.ACTIVE) {
            throw new IllegalStateException("Cannot record swipe on completed session");
        }

        this.swipeCount++;
        this.lastActivityAt = Instant.now();

        if (direction == Like.Direction.LIKE) {
            this.likeCount++;
            if (matched) {
                this.matchCount++;
            }
        } else {
            this.passCount++;
        }
    }

    /** Increment match count (when we discover a match after recording the swipe). */
    public void incrementMatchCount() {
        if (state == State.ACTIVE) {
            if (matchCount >= likeCount) {
                throw new IllegalStateException("matchCount cannot exceed likeCount");
            }
            this.matchCount++;
        }
    }

    /** End this session. */
    public void end() {
        if (state == State.COMPLETED) {
            return; // Already ended
        }
        this.state = State.COMPLETED;
        this.endedAt = Instant.now();
    }

    /**
     * Check if session has timed out based on inactivity.
     *
     * @param timeout the maximum allowed inactivity duration
     * @return true if the session has been inactive longer than timeout
     */
    public boolean isTimedOut(Duration timeout) {
        if (state == State.COMPLETED) {
            return false; // Already ended
        }
        Duration inactivity = Duration.between(lastActivityAt, Instant.now());
        return inactivity.compareTo(timeout) >= 0;
    }

    // === Computed Properties ===

    /** Get session duration in seconds. */
    public long getDurationSeconds() {
        Instant end = endedAt != null ? endedAt : Instant.now();
        long seconds = Duration.between(startedAt, end).toSeconds();
        return Math.max(0, seconds);
    }

    /** Get formatted duration (MM:SS or HH:MM:SS for long sessions). */
    public String getFormattedDuration() {
        long seconds = getDurationSeconds();
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    /** Get swipes per minute velocity. */
    public double getSwipesPerMinute() {
        long seconds = getDurationSeconds();
        if (seconds == 0) {
            return swipeCount;
        }
        return swipeCount * 60.0 / seconds;
    }

    /** Get like ratio for this session. */
    public double getLikeRatio() {
        return swipeCount > 0 ? (double) likeCount / swipeCount : 0.0;
    }

    /** Get match rate for this session (matches per like). */
    public double getMatchRate() {
        return likeCount > 0 ? (double) matchCount / likeCount : 0.0;
    }

    // === Getters ===

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public State getState() {
        return state;
    }

    public int getSwipeCount() {
        return swipeCount;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getPassCount() {
        return passCount;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SwipeSession that = (SwipeSession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("SwipeSession{id=%s, user=%s, swipes=%d, state=%s}", id, userId, swipeCount, state);
    }
}
