# Swipe Session Tracking - Design Document

**Date:** 2026-01-08
**Status:** Draft
**Priority:** P1
**Complexity:** Medium
**Dependencies:** None (enhances existing like flow)

---

## 1. Overview

### Purpose
Track user swiping behavior at the **session** level, not just individual swipes. This enables:
- Understanding engagement patterns (50 swipes in 1 sitting vs 5 swipes over 10 days)
- Detecting abnormal behavior (spam swiping, bots)
- Richer analytics for Swipe Statistics feature
- Foundation for future rate limiting

### What is a Session?
A session represents a continuous period of user activity:
- **Starts** when user begins browsing candidates
- **Continues** as long as user keeps swiping
- **Ends** after N minutes of inactivity (configurable, default 5 minutes)

### Key Metrics Per Session
- Duration (start to end)
- Total swipes (likes + passes)
- Like count / Pass count
- Matches made during session
- Swipes per minute (velocity)

---

## 2. Domain Model

### 2.1 SwipeSession Record

```java
// core/SwipeSession.java
public class SwipeSession {

    public enum State {
        ACTIVE,     // Currently in progress
        COMPLETED   // Ended (timeout or explicit)
    }

    private final UUID id;
    private final UUID userId;
    private final Instant startedAt;
    private Instant lastActivityAt;
    private Instant endedAt;        // null if active
    private State state;

    // Counters
    private int swipeCount;
    private int likeCount;
    private int passCount;
    private int matchCount;

    /**
     * Create a new active session.
     */
    public SwipeSession(UUID id, UUID userId, Instant startedAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.lastActivityAt = startedAt;
        this.state = State.ACTIVE;
        this.swipeCount = 0;
        this.likeCount = 0;
        this.passCount = 0;
        this.matchCount = 0;
    }

    /**
     * Full constructor for loading from database.
     */
    public SwipeSession(UUID id, UUID userId, Instant startedAt,
                        Instant lastActivityAt, Instant endedAt, State state,
                        int swipeCount, int likeCount, int passCount, int matchCount) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.lastActivityAt = Objects.requireNonNull(lastActivityAt);
        this.endedAt = endedAt;
        this.state = Objects.requireNonNull(state);
        this.swipeCount = swipeCount;
        this.likeCount = likeCount;
        this.passCount = passCount;
        this.matchCount = matchCount;
    }

    /**
     * Factory for creating a new session.
     */
    public static SwipeSession create(UUID userId) {
        return new SwipeSession(UUID.randomUUID(), userId, Instant.now());
    }

    /**
     * Record a swipe in this session.
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

    /**
     * End this session.
     */
    public void end() {
        if (state == State.COMPLETED) {
            return; // Already ended
        }
        this.state = State.COMPLETED;
        this.endedAt = Instant.now();
    }

    /**
     * Check if session has timed out based on inactivity.
     */
    public boolean isTimedOut(Duration timeout) {
        if (state == State.COMPLETED) {
            return false; // Already ended
        }
        Duration inactivity = Duration.between(lastActivityAt, Instant.now());
        return inactivity.compareTo(timeout) > 0;
    }

    // === Computed Properties ===

    /**
     * Get session duration in seconds.
     */
    public long getDurationSeconds() {
        Instant end = endedAt != null ? endedAt : Instant.now();
        return Duration.between(startedAt, end).toSeconds();
    }

    /**
     * Get swipes per minute velocity.
     */
    public double getSwipesPerMinute() {
        long seconds = getDurationSeconds();
        if (seconds < 60) {
            return swipeCount; // Less than a minute, return raw count
        }
        return (double) swipeCount / (seconds / 60.0);
    }

    /**
     * Get like ratio for this session.
     */
    public double getLikeRatio() {
        return swipeCount > 0 ? (double) likeCount / swipeCount : 0.0;
    }

    /**
     * Get match rate for this session.
     */
    public double getMatchRate() {
        return likeCount > 0 ? (double) matchCount / likeCount : 0.0;
    }

    // === Getters ===

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public Instant getEndedAt() { return endedAt; }
    public State getState() { return state; }
    public int getSwipeCount() { return swipeCount; }
    public int getLikeCount() { return likeCount; }
    public int getPassCount() { return passCount; }
    public int getMatchCount() { return matchCount; }

    public boolean isActive() { return state == State.ACTIVE; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SwipeSession that = (SwipeSession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("SwipeSession{id=%s, user=%s, swipes=%d, state=%s}",
            id, userId, swipeCount, state);
    }
}
```

### 2.2 Session Configuration

```java
// Add to AppConfig.java
public record AppConfig(
    // ... existing fields ...
    int sessionTimeoutMinutes,       // Default: 5
    int maxSwipesPerSession,         // Default: 500 (anti-bot)
    double suspiciousSwipeVelocity   // Default: 30 swipes/min
) {
    public static AppConfig defaults() {
        return new AppConfig(
            3,      // autoBanThreshold
            100,    // dailyLikeLimit
            1,      // dailySuperLikeLimit
            5,      // maxInterests
            2,      // maxPhotos
            500,    // maxBioLength
            500,    // maxReportDescLength
            5,      // sessionTimeoutMinutes
            500,    // maxSwipesPerSession
            30.0    // suspiciousSwipeVelocity
        );
    }

    public Duration getSessionTimeout() {
        return Duration.ofMinutes(sessionTimeoutMinutes);
    }
}
```

---

## 3. Storage Interface

```java
// core/SwipeSessionStorage.java
public interface SwipeSessionStorage {

    /**
     * Save a new session or update existing.
     */
    void save(SwipeSession session);

    /**
     * Get a session by ID.
     */
    Optional<SwipeSession> get(UUID sessionId);

    /**
     * Get the currently active session for a user, if any.
     */
    Optional<SwipeSession> getActiveSession(UUID userId);

    /**
     * Get recent sessions for a user (most recent first).
     */
    List<SwipeSession> getSessionsFor(UUID userId, int limit);

    /**
     * Get sessions for a user within a time range.
     */
    List<SwipeSession> getSessionsInRange(UUID userId, Instant start, Instant end);

    /**
     * Get aggregate session stats for a user.
     */
    SessionAggregates getAggregates(UUID userId);

    /**
     * End all active sessions older than timeout (cleanup job).
     */
    int endStaleSessions(Duration timeout);
}

/**
 * Aggregate statistics across all sessions.
 */
record SessionAggregates(
    int totalSessions,
    int totalSwipes,
    int totalLikes,
    int totalPasses,
    int totalMatches,
    double avgSessionDurationSeconds,
    double avgSwipesPerSession,
    double avgSwipeVelocity
) {}
```

---

## 4. Session Service

```java
// core/SessionService.java
public class SessionService {

    private final SwipeSessionStorage sessionStorage;
    private final AppConfig config;

    public SessionService(SwipeSessionStorage sessionStorage, AppConfig config) {
        this.sessionStorage = Objects.requireNonNull(sessionStorage);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Get or create an active session for the user.
     * If existing session is timed out, ends it and creates new one.
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

    /**
     * Create a new session for the user.
     */
    private SwipeSession createNewSession(UUID userId) {
        SwipeSession session = SwipeSession.create(userId);
        sessionStorage.save(session);
        return session;
    }

    /**
     * Record a swipe in the user's current session.
     * Returns the updated session and any warnings.
     */
    public SwipeResult recordSwipe(UUID userId, Like.Direction direction, boolean matched) {
        SwipeSession session = getOrCreateSession(userId);

        // Check for anti-bot limits
        if (session.getSwipeCount() >= config.maxSwipesPerSession()) {
            return SwipeResult.blocked(session, "Session swipe limit reached");
        }

        // Record the swipe
        session.recordSwipe(direction, matched);
        sessionStorage.save(session);

        // Check for suspicious velocity
        String warning = null;
        if (session.getSwipeCount() >= 10 &&
            session.getSwipesPerMinute() > config.suspiciousSwipeVelocity()) {
            warning = "Unusually fast swiping detected";
        }

        return SwipeResult.success(session, warning);
    }

    /**
     * Explicitly end the user's current session.
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
     * Get session history for display.
     */
    public List<SwipeSession> getSessionHistory(UUID userId, int limit) {
        return sessionStorage.getSessionsFor(userId, limit);
    }

    /**
     * Get today's sessions for a user.
     */
    public List<SwipeSession> getTodaysSessions(UUID userId) {
        Instant startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant();
        return sessionStorage.getSessionsInRange(userId, startOfDay, Instant.now());
    }

    /**
     * Cleanup job: end all stale sessions.
     */
    public int cleanupStaleSessions() {
        return sessionStorage.endStaleSessions(config.getSessionTimeout());
    }

    /**
     * Result of a swipe operation.
     */
    public record SwipeResult(
        boolean allowed,
        SwipeSession session,
        String warning,
        String blockedReason
    ) {
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
```

---

## 5. MatchingService Integration

```java
// Update MatchingService.java

public class MatchingService {

    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final SessionService sessionService;  // NEW

    public MatchingService(LikeStorage likeStorage, MatchStorage matchStorage,
                           SessionService sessionService) {
        this.likeStorage = likeStorage;
        this.matchStorage = matchStorage;
        this.sessionService = sessionService;
    }

    /**
     * Records a like action and checks for mutual match.
     * Also tracks the swipe in the session.
     */
    public LikeResult recordLike(Like like) {
        // Check if already exists
        if (likeStorage.exists(like.whoLikes(), like.whoGotLiked())) {
            return LikeResult.alreadyExists();
        }

        // Record in session first (may block if limits reached)
        SessionService.SwipeResult swipeResult = sessionService.recordSwipe(
            like.whoLikes(),
            like.direction(),
            false  // We don't know if it's a match yet
        );

        if (!swipeResult.allowed()) {
            return LikeResult.sessionLimitReached(swipeResult.blockedReason());
        }

        // Save the like
        likeStorage.save(like);

        // If it's a PASS, no match possible
        if (like.direction() == Like.Direction.PASS) {
            return LikeResult.passed(swipeResult.warning());
        }

        // Check for mutual LIKE
        if (likeStorage.mutualLikeExists(like.whoLikes(), like.whoGotLiked())) {
            Match match = Match.create(like.whoLikes(), like.whoGotLiked());

            if (!matchStorage.exists(match.getId())) {
                try {
                    matchStorage.save(match);

                    // Update session with match count
                    // (Session already has this swipe, just need to record match)
                    SwipeSession session = swipeResult.session();
                    // Note: The recordSwipe already happened, but we passed matched=false
                    // We should update the session's match count
                    // This requires a small refactor - see note below

                    return LikeResult.matched(match, swipeResult.warning());
                } catch (Exception e) {
                    return matchStorage.get(match.getId())
                        .map(m -> LikeResult.matched(m, swipeResult.warning()))
                        .orElse(LikeResult.liked(swipeResult.warning()));
                }
            }
        }

        return LikeResult.liked(swipeResult.warning());
    }

    /**
     * Result of a like operation.
     */
    public record LikeResult(
        Status status,
        Match match,
        String warning,
        String error
    ) {
        public enum Status {
            ALREADY_EXISTS,
            SESSION_LIMIT_REACHED,
            PASSED,
            LIKED,
            MATCHED
        }

        public static LikeResult alreadyExists() {
            return new LikeResult(Status.ALREADY_EXISTS, null, null, null);
        }

        public static LikeResult sessionLimitReached(String reason) {
            return new LikeResult(Status.SESSION_LIMIT_REACHED, null, null, reason);
        }

        public static LikeResult passed(String warning) {
            return new LikeResult(Status.PASSED, null, warning, null);
        }

        public static LikeResult liked(String warning) {
            return new LikeResult(Status.LIKED, null, warning, null);
        }

        public static LikeResult matched(Match match, String warning) {
            return new LikeResult(Status.MATCHED, match, warning, null);
        }

        public boolean isMatch() {
            return status == Status.MATCHED;
        }

        public boolean hasWarning() {
            return warning != null;
        }

        public boolean isBlocked() {
            return status == Status.SESSION_LIMIT_REACHED;
        }
    }
}
```

---

## 6. Database Schema

```sql
-- Swipe sessions table
CREATE TABLE swipe_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    started_at TIMESTAMP NOT NULL,
    last_activity_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    swipe_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    pass_count INT NOT NULL DEFAULT 0,
    match_count INT NOT NULL DEFAULT 0
);

-- Indexes for common queries
CREATE INDEX idx_sessions_user_id ON swipe_sessions(user_id);
CREATE INDEX idx_sessions_user_active ON swipe_sessions(user_id, state)
    WHERE state = 'ACTIVE';
CREATE INDEX idx_sessions_started_at ON swipe_sessions(user_id, started_at DESC);
```

---

## 7. Console UI Changes

### 7.1 Session Status in Header
```
═══════════════════════════════════════
         DATING APP - PHASE 0.5
═══════════════════════════════════════
  Current User: Alice (ACTIVE)
  Session: 12 swipes (8 likes, 4 passes) | 3:24 elapsed
───────────────────────────────────────
```

### 7.2 Session Stats in Statistics Menu
```
═══════════════════════════════════════
         YOUR DATING STATISTICS
═══════════════════════════════════════

  📊 CURRENT SESSION
  ───────────────────────────────────
  Duration:         12 minutes
  Swipes:           34 (28 likes, 6 passes)
  Matches:          2
  Swipe velocity:   2.8 per minute

  📈 SESSION HISTORY (Last 7 days)
  ───────────────────────────────────
  Total sessions:   14
  Avg duration:     8.5 minutes
  Avg swipes/session: 24
  Most active day:  Tuesday (5 sessions)

  ...
```

### 7.3 Warning Display
```
  ⚠️  Unusually fast swiping detected.
      Take a moment to review profiles carefully!
```

---

## 8. Implementation Steps

### Step 1: Create SwipeSession Class (1 hour)
1. Create `core/SwipeSession.java`
2. Implement state machine (ACTIVE → COMPLETED)
3. Add computed properties (duration, velocity, ratios)

### Step 2: Update AppConfig (30 min)
1. Add session timeout configuration
2. Add anti-bot thresholds
3. Update builder and defaults

### Step 3: Create Storage Interface (30 min)
1. Create `core/SwipeSessionStorage.java`
2. Define `SessionAggregates` record

### Step 4: Create H2 Implementation (2 hours)
1. Create `storage/H2SwipeSessionStorage.java`
2. Add table creation to `DatabaseManager`
3. Implement all interface methods

### Step 5: Create SessionService (1-2 hours)
1. Create `core/SessionService.java`
2. Implement session lifecycle management
3. Implement anti-bot checks

### Step 6: Update MatchingService (1-2 hours)
1. Add SessionService dependency
2. Update `recordLike()` to track sessions
3. Handle session limit responses
4. Update LikeResult to include warnings

### Step 7: Update ServiceRegistry (30 min)
1. Add SwipeSessionStorage
2. Add SessionService
3. Wire dependencies

### Step 8: Console UI Updates (1-2 hours)
1. Add session status to header
2. Add session history to statistics menu
3. Display warnings when appropriate

### Step 9: Testing (2 hours)
1. Unit tests for SwipeSession state machine
2. Unit tests for SessionService logic
3. Integration tests for H2 storage
4. Test anti-bot detection

---

## 9. Test Plan

### 9.1 Unit Tests

| Test | Description |
|------|-------------|
| `SwipeSessionTest.recordsSwipes` | Counters increment correctly |
| `SwipeSessionTest.calculatesVelocity` | Swipes per minute is accurate |
| `SwipeSessionTest.detectsTimeout` | isTimedOut works correctly |
| `SwipeSessionTest.cannotRecordOnCompleted` | Throws on ended session |
| `SessionServiceTest.createsNewSession` | Creates when none exists |
| `SessionServiceTest.reusesActiveSession` | Returns existing if valid |
| `SessionServiceTest.endsTimedOutSession` | Creates new after timeout |
| `SessionServiceTest.blocksAtLimit` | Returns blocked at max swipes |
| `SessionServiceTest.warnsOnHighVelocity` | Warning at suspicious speed |

### 9.2 Integration Tests

| Test | Description |
|------|-------------|
| `H2SwipeSessionStorageTest.savesAndRetrieves` | Full roundtrip |
| `H2SwipeSessionStorageTest.getsActiveSession` | Finds active only |
| `H2SwipeSessionStorageTest.endsStaleSession` | Cleanup works |
| `MatchingServiceIntegrationTest.tracksSession` | Like updates session |

---

## 10. Success Criteria

- [ ] Sessions automatically created when user starts swiping
- [ ] Sessions end after inactivity timeout
- [ ] Swipe counts tracked per session
- [ ] Match counts tracked per session
- [ ] Session velocity (swipes/min) calculated
- [ ] Anti-bot: blocks at max swipes per session
- [ ] Anti-bot: warns on suspicious velocity
- [ ] Session history retrievable
- [ ] Sessions persist between app restarts
- [ ] All new code in `core/` with zero framework imports
- [ ] All tests pass

---

## 11. Design Decisions

### Decision 1: Mutable Session Class
Unlike Like/Block/Report (immutable records), SwipeSession is mutable because it's updated frequently during a session. This avoids creating many objects.

### Decision 2: Soft Limits with Warnings
Rather than hard-blocking fast swipers immediately, we warn first. Only session-level limits (max swipes) are hard blocks.

### Decision 3: Session Timeout vs Explicit End
Sessions end automatically on timeout, but we also support explicit end (user exits browse mode). This handles both cases gracefully.

### Decision 4: No Session-to-Like Foreign Key
We don't link individual likes to sessions in the database (would require altering likes table). Session tracking is independent - we just count.

---

## 12. Future Enhancements (Not in Scope)

- Session-based rate limiting for API (Phase 1)
- Machine learning bot detection
- Session replay/audit for abuse investigation
- Time-of-day activity patterns
- Session quality score (thoughtful vs spam swiping)
