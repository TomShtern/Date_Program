package datingapp.core.metrics;

import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Consolidated swipe domain state (session + undo). */
public final class SwipeState {

    private SwipeState() {
        // Utility class
    }

    public static Session createSession(UUID userId) {
        return Session.create(userId);
    }

    public static Undo createUndo(UUID userId, ConnectionModels.Like like, String matchId, Instant expiresAt) {
        return Undo.create(userId, like, matchId, expiresAt);
    }

    /** Represents a user's active or completed swipe session. */
    public static class Session {

        /** Session state. */
        public static enum State {
            ACTIVE,
            COMPLETED
        }

        private final UUID id;
        private final UUID userId;
        private final Instant startedAt;
        private Instant lastActivityAt;
        private Instant endedAt;
        private State state;

        private int swipeCount;
        private int likeCount;
        private int passCount;
        private int matchCount;

        public Session(UUID id, UUID userId, Instant startedAt) {
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

        @SuppressWarnings("java:S107")
        public Session(
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
            if (swipeCount < 0 || likeCount < 0 || passCount < 0 || matchCount < 0) {
                throw new IllegalArgumentException("Counts cannot be negative");
            }
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

        public static Session create(UUID userId) {
            return new Session(UUID.randomUUID(), userId, AppClock.now());
        }

        public void recordSwipe(ConnectionModels.Like.Direction direction, boolean matched) {
            if (state != State.ACTIVE) {
                throw new IllegalStateException("Cannot record swipe on completed session");
            }

            this.swipeCount++;
            this.lastActivityAt = AppClock.now();

            if (direction == ConnectionModels.Like.Direction.LIKE) {
                this.likeCount++;
                if (matched) {
                    this.matchCount++;
                }
            } else {
                this.passCount++;
            }
        }

        public void incrementMatchCount() {
            if (state == State.ACTIVE) {
                if (matchCount >= likeCount) {
                    throw new IllegalStateException("matchCount cannot exceed likeCount");
                }
                this.matchCount++;
            }
        }

        public void end() {
            if (state == State.COMPLETED) {
                return;
            }
            this.state = State.COMPLETED;
            this.endedAt = AppClock.now();
        }

        public boolean isTimedOut(Duration timeout) {
            if (state == State.COMPLETED) {
                return false;
            }
            Duration inactivity = Duration.between(lastActivityAt, AppClock.now());
            return inactivity.compareTo(timeout) >= 0;
        }

        public long getDurationSeconds() {
            Instant end = endedAt != null ? endedAt : AppClock.now();
            long seconds = Duration.between(startedAt, end).toSeconds();
            return Math.max(0, seconds);
        }

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

        public double getSwipesPerMinute() {
            long seconds = getDurationSeconds();
            if (seconds == 0) {
                return swipeCount;
            }
            return swipeCount * 60.0 / seconds;
        }

        public double getLikeRatio() {
            return swipeCount > 0 ? (double) likeCount / swipeCount : 0.0;
        }

        public double getMatchRate() {
            return likeCount > 0 ? (double) matchCount / likeCount : 0.0;
        }

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
            Session that = (Session) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return String.format(
                    "SwipeState.Session{id=%s, user=%s, swipes=%d, state=%s}", id, userId, swipeCount, state);
        }
    }

    /** Represents the undo state for a single swipe action. */
    public static record Undo(UUID userId, ConnectionModels.Like like, String matchId, Instant expiresAt) {

        public Undo {
            Objects.requireNonNull(userId, "userId cannot be null");
            Objects.requireNonNull(like, "like cannot be null");
            Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        }

        public static Undo create(UUID userId, ConnectionModels.Like like, String matchId, Instant expiresAt) {
            return new Undo(userId, like, matchId, expiresAt);
        }

        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }

        /** Storage interface for persisted undo state. */
        public static interface Storage {
            void save(Undo state);

            Optional<Undo> findByUserId(UUID userId);

            boolean delete(UUID userId);

            int deleteExpired(Instant now);

            List<Undo> findAll();
        }
    }
}
