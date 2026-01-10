# Swipe Statistics (Stored Snapshots) - Design Document

**Date:** 2026-01-08
**Status:** Draft
**Priority:** P1
**Complexity:** Medium
**Dependencies:** None (uses existing data)

---

## 1. Overview

### Purpose
Track and store user engagement statistics as periodic snapshots. This enables:
- Users to see their own activity metrics
- Historical trend analysis (week-over-week, month-over-month)
- Platform health monitoring
- Future recommendation algorithm improvements

### Design Decision: Snapshots vs On-Demand
**Chosen:** Stored snapshots because:
- Faster reads (no complex aggregation queries at runtime)
- Enables historical trends ("Your match rate improved 15% this month")
- Reduces database load during peak usage
- Platform-wide averages can be pre-computed

---

## 2. Domain Model

### 2.1 UserStats Record

```java
// core/UserStats.java
public record UserStats(
    UUID id,
    UUID userId,
    Instant computedAt,

    // === Outgoing Activity ===
    int totalSwipesGiven,         // likes + passes given
    int likesGiven,
    int passesGiven,
    double likeRatio,             // likesGiven / totalSwipesGiven (0.0-1.0)

    // === Incoming Activity ===
    int totalSwipesReceived,      // likes + passes received
    int likesReceived,
    int passesReceived,
    double incomingLikeRatio,     // likesReceived / totalSwipesReceived

    // === Matches ===
    int totalMatches,             // all-time matches created
    int activeMatches,            // currently active
    int endedMatches,             // unmatched + blocked
    double matchRate,             // totalMatches / likesGiven (0.0-1.0)

    // === Match Outcomes ===
    int unmatchesInitiated,       // matches YOU ended
    int unmatchesReceived,        // matches THEY ended
    int blocksInMatches,          // matches that ended in block

    // === Safety ===
    int blocksGiven,              // users you blocked
    int blocksReceived,           // users who blocked you
    int reportsGiven,
    int reportsReceived,

    // === Derived Scores (0.0-1.0) ===
    double reciprocityScore,      // % of your likes that liked you back
    double selectivenessScore,    // how picky vs platform average
    double attractivenessScore,   // likes received vs platform average
    double engagementScore        // activity level vs platform average
) {
    public UserStats {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(computedAt);

        // Validate ratios are 0.0-1.0
        validateRatio(likeRatio, "likeRatio");
        validateRatio(incomingLikeRatio, "incomingLikeRatio");
        validateRatio(matchRate, "matchRate");
        validateRatio(reciprocityScore, "reciprocityScore");
        validateRatio(selectivenessScore, "selectivenessScore");
        validateRatio(attractivenessScore, "attractivenessScore");
        validateRatio(engagementScore, "engagementScore");
    }

    private static void validateRatio(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be 0.0-1.0, got: " + value);
        }
    }

    /**
     * Factory for creating a new snapshot.
     */
    public static UserStats create(UUID userId, StatsBuilder builder) {
        return new UserStats(
            UUID.randomUUID(),
            userId,
            Instant.now(),
            builder.totalSwipesGiven,
            builder.likesGiven,
            builder.passesGiven,
            builder.likeRatio,
            builder.totalSwipesReceived,
            builder.likesReceived,
            builder.passesReceived,
            builder.incomingLikeRatio,
            builder.totalMatches,
            builder.activeMatches,
            builder.endedMatches,
            builder.matchRate,
            builder.unmatchesInitiated,
            builder.unmatchesReceived,
            builder.blocksInMatches,
            builder.blocksGiven,
            builder.blocksReceived,
            builder.reportsGiven,
            builder.reportsReceived,
            builder.reciprocityScore,
            builder.selectivenessScore,
            builder.attractivenessScore,
            builder.engagementScore
        );
    }

    /**
     * Builder for constructing stats during computation.
     */
    public static class StatsBuilder {
        // All fields with defaults
        public int totalSwipesGiven = 0;
        public int likesGiven = 0;
        public int passesGiven = 0;
        public double likeRatio = 0.0;
        public int totalSwipesReceived = 0;
        public int likesReceived = 0;
        public int passesReceived = 0;
        public double incomingLikeRatio = 0.0;
        public int totalMatches = 0;
        public int activeMatches = 0;
        public int endedMatches = 0;
        public double matchRate = 0.0;
        public int unmatchesInitiated = 0;
        public int unmatchesReceived = 0;
        public int blocksInMatches = 0;
        public int blocksGiven = 0;
        public int blocksReceived = 0;
        public int reportsGiven = 0;
        public int reportsReceived = 0;
        public double reciprocityScore = 0.0;
        public double selectivenessScore = 0.5; // 0.5 = average
        public double attractivenessScore = 0.5;
        public double engagementScore = 0.5;
    }
}
```

### 2.2 PlatformStats Record (For Computing Averages)

```java
// core/PlatformStats.java
public record PlatformStats(
    UUID id,
    Instant computedAt,
    int totalActiveUsers,
    double avgLikesReceived,
    double avgLikesGiven,
    double avgMatchRate,
    double avgLikeRatio
) {
    public static PlatformStats create(
        int totalActiveUsers,
        double avgLikesReceived,
        double avgLikesGiven,
        double avgMatchRate,
        double avgLikeRatio
    ) {
        return new PlatformStats(
            UUID.randomUUID(),
            Instant.now(),
            totalActiveUsers,
            avgLikesReceived,
            avgLikesGiven,
            avgMatchRate,
            avgLikeRatio
        );
    }
}
```

---

## 3. Storage Interface

```java
// core/UserStatsStorage.java
public interface UserStatsStorage {

    /**
     * Save a new stats snapshot.
     */
    void save(UserStats stats);

    /**
     * Get the most recent stats for a user.
     */
    Optional<UserStats> getLatest(UUID userId);

    /**
     * Get historical snapshots for a user (most recent first).
     */
    List<UserStats> getHistory(UUID userId, int limit);

    /**
     * Get latest stats for all users (for computing platform averages).
     */
    List<UserStats> getAllLatestStats();

    /**
     * Delete snapshots older than a certain date (cleanup).
     */
    int deleteOlderThan(Instant cutoff);
}

// core/PlatformStatsStorage.java
public interface PlatformStatsStorage {
    void save(PlatformStats stats);
    Optional<PlatformStats> getLatest();
    List<PlatformStats> getHistory(int limit);
}
```

---

## 4. Service Logic

```java
// core/StatsService.java
public class StatsService {

    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final BlockStorage blockStorage;
    private final ReportStorage reportStorage;
    private final UserStatsStorage userStatsStorage;
    private final PlatformStatsStorage platformStatsStorage;

    public StatsService(
        UserStorage userStorage,
        LikeStorage likeStorage,
        MatchStorage matchStorage,
        BlockStorage blockStorage,
        ReportStorage reportStorage,
        UserStatsStorage userStatsStorage,
        PlatformStatsStorage platformStatsStorage
    ) {
        this.userStorage = userStorage;
        this.likeStorage = likeStorage;
        this.matchStorage = matchStorage;
        this.blockStorage = blockStorage;
        this.reportStorage = reportStorage;
        this.userStatsStorage = userStatsStorage;
        this.platformStatsStorage = platformStatsStorage;
    }

    /**
     * Compute and save stats snapshot for a single user.
     */
    public UserStats computeAndSaveStats(UUID userId) {
        UserStats.StatsBuilder builder = new UserStats.StatsBuilder();

        // --- Outgoing Activity ---
        builder.likesGiven = likeStorage.countByDirection(userId, Like.Direction.LIKE);
        builder.passesGiven = likeStorage.countByDirection(userId, Like.Direction.PASS);
        builder.totalSwipesGiven = builder.likesGiven + builder.passesGiven;
        builder.likeRatio = builder.totalSwipesGiven > 0
            ? (double) builder.likesGiven / builder.totalSwipesGiven
            : 0.0;

        // --- Incoming Activity ---
        builder.likesReceived = likeStorage.countReceivedByDirection(userId, Like.Direction.LIKE);
        builder.passesReceived = likeStorage.countReceivedByDirection(userId, Like.Direction.PASS);
        builder.totalSwipesReceived = builder.likesReceived + builder.passesReceived;
        builder.incomingLikeRatio = builder.totalSwipesReceived > 0
            ? (double) builder.likesReceived / builder.totalSwipesReceived
            : 0.0;

        // --- Matches ---
        List<Match> allMatches = matchStorage.getAllMatchesFor(userId);
        builder.totalMatches = allMatches.size();
        builder.activeMatches = (int) allMatches.stream()
            .filter(Match::isActive).count();
        builder.endedMatches = builder.totalMatches - builder.activeMatches;
        builder.matchRate = builder.likesGiven > 0
            ? Math.min(1.0, (double) builder.totalMatches / builder.likesGiven)
            : 0.0;

        // --- Match Outcomes ---
        for (Match match : allMatches) {
            if (match.getState() == Match.State.UNMATCHED) {
                if (userId.equals(match.getEndedBy())) {
                    builder.unmatchesInitiated++;
                } else {
                    builder.unmatchesReceived++;
                }
            } else if (match.getState() == Match.State.BLOCKED) {
                builder.blocksInMatches++;
            }
        }

        // --- Safety ---
        builder.blocksGiven = blockStorage.countBlocksGiven(userId);
        builder.blocksReceived = blockStorage.countBlocksReceived(userId);
        builder.reportsGiven = reportStorage.countReportsBy(userId);
        builder.reportsReceived = reportStorage.countReportsAgainst(userId);

        // --- Reciprocity Score ---
        // Of all the people I liked, what % liked me back?
        int mutualLikes = likeStorage.countMutualLikes(userId);
        builder.reciprocityScore = builder.likesGiven > 0
            ? Math.min(1.0, (double) mutualLikes / builder.likesGiven)
            : 0.0;

        // --- Derived Scores (require platform averages) ---
        Optional<PlatformStats> platformStats = platformStatsStorage.getLatest();
        if (platformStats.isPresent()) {
            PlatformStats ps = platformStats.get();

            // Selectiveness: lower like ratio = more selective
            // Score of 0.5 = average, >0.5 = more selective, <0.5 = less selective
            if (ps.avgLikeRatio() > 0) {
                double selectivenessRaw = 1.0 - (builder.likeRatio / ps.avgLikeRatio());
                builder.selectivenessScore = Math.max(0.0, Math.min(1.0, 0.5 + selectivenessRaw * 0.5));
            }

            // Attractiveness: more likes received = more attractive
            if (ps.avgLikesReceived() > 0) {
                double attractivenessRaw = builder.likesReceived / ps.avgLikesReceived();
                builder.attractivenessScore = Math.max(0.0, Math.min(1.0, attractivenessRaw / 2.0));
            }

            // Engagement: more swipes = more engaged
            if (ps.avgLikesGiven() > 0) {
                double engagementRaw = builder.totalSwipesGiven / (ps.avgLikesGiven() + ps.avgLikesGiven());
                builder.engagementScore = Math.max(0.0, Math.min(1.0, engagementRaw / 2.0));
            }
        }

        UserStats stats = UserStats.create(userId, builder);
        userStatsStorage.save(stats);
        return stats;
    }

    /**
     * Compute stats snapshots for ALL active users.
     * Call this periodically (e.g., daily at midnight).
     */
    public int snapshotAllUsers() {
        List<User> activeUsers = userStorage.findActive();
        int count = 0;
        for (User user : activeUsers) {
            computeAndSaveStats(user.getId());
            count++;
        }

        // Also update platform stats
        computeAndSavePlatformStats();

        return count;
    }

    /**
     * Compute and save platform-wide statistics.
     */
    public PlatformStats computeAndSavePlatformStats() {
        List<UserStats> allStats = userStatsStorage.getAllLatestStats();

        if (allStats.isEmpty()) {
            PlatformStats stats = PlatformStats.create(0, 0.0, 0.0, 0.0, 0.5);
            platformStatsStorage.save(stats);
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

        int n = allStats.size();
        PlatformStats stats = PlatformStats.create(
            n,
            totalLikesReceived / n,
            totalLikesGiven / n,
            totalMatchRate / n,
            totalLikeRatio / n
        );

        platformStatsStorage.save(stats);
        return stats;
    }

    /**
     * Get user stats, computing fresh if none exist or stale.
     */
    public UserStats getOrComputeStats(UUID userId) {
        Optional<UserStats> existing = userStatsStorage.getLatest(userId);

        if (existing.isPresent()) {
            // Check if stale (older than 24 hours)
            Duration age = Duration.between(existing.get().computedAt(), Instant.now());
            if (age.toHours() < 24) {
                return existing.get();
            }
        }

        return computeAndSaveStats(userId);
    }
}
```

---

## 5. Required LikeStorage Additions

```java
// Add to core/LikeStorage.java
public interface LikeStorage {
    // ... existing methods ...

    /**
     * Count likes/passes given BY a user with specific direction.
     */
    int countByDirection(UUID userId, Like.Direction direction);

    /**
     * Count likes/passes RECEIVED by a user with specific direction.
     */
    int countReceivedByDirection(UUID userId, Like.Direction direction);

    /**
     * Count mutual likes (users this person liked who also liked them back).
     */
    int countMutualLikes(UUID userId);
}
```

```java
// Add to core/BlockStorage.java
public interface BlockStorage {
    // ... existing methods ...

    int countBlocksGiven(UUID userId);
    int countBlocksReceived(UUID userId);
}
```

```java
// Add to core/ReportStorage.java
public interface ReportStorage {
    // ... existing methods ...

    int countReportsBy(UUID reporterId);
    // countReportsAgainst already exists
}
```

---

## 6. Database Schema

```sql
-- User stats snapshots
CREATE TABLE user_stats (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    computed_at TIMESTAMP NOT NULL,

    -- Outgoing
    total_swipes_given INT NOT NULL DEFAULT 0,
    likes_given INT NOT NULL DEFAULT 0,
    passes_given INT NOT NULL DEFAULT 0,
    like_ratio DOUBLE NOT NULL DEFAULT 0.0,

    -- Incoming
    total_swipes_received INT NOT NULL DEFAULT 0,
    likes_received INT NOT NULL DEFAULT 0,
    passes_received INT NOT NULL DEFAULT 0,
    incoming_like_ratio DOUBLE NOT NULL DEFAULT 0.0,

    -- Matches
    total_matches INT NOT NULL DEFAULT 0,
    active_matches INT NOT NULL DEFAULT 0,
    ended_matches INT NOT NULL DEFAULT 0,
    match_rate DOUBLE NOT NULL DEFAULT 0.0,

    -- Match outcomes
    unmatches_initiated INT NOT NULL DEFAULT 0,
    unmatches_received INT NOT NULL DEFAULT 0,
    blocks_in_matches INT NOT NULL DEFAULT 0,

    -- Safety
    blocks_given INT NOT NULL DEFAULT 0,
    blocks_received INT NOT NULL DEFAULT 0,
    reports_given INT NOT NULL DEFAULT 0,
    reports_received INT NOT NULL DEFAULT 0,

    -- Derived scores
    reciprocity_score DOUBLE NOT NULL DEFAULT 0.0,
    selectiveness_score DOUBLE NOT NULL DEFAULT 0.5,
    attractiveness_score DOUBLE NOT NULL DEFAULT 0.5,
    engagement_score DOUBLE NOT NULL DEFAULT 0.5
);

CREATE INDEX idx_user_stats_user_id ON user_stats(user_id);
CREATE INDEX idx_user_stats_computed_at ON user_stats(user_id, computed_at DESC);

-- Platform-wide stats
CREATE TABLE platform_stats (
    id UUID PRIMARY KEY,
    computed_at TIMESTAMP NOT NULL,
    total_active_users INT NOT NULL DEFAULT 0,
    avg_likes_received DOUBLE NOT NULL DEFAULT 0.0,
    avg_likes_given DOUBLE NOT NULL DEFAULT 0.0,
    avg_match_rate DOUBLE NOT NULL DEFAULT 0.0,
    avg_like_ratio DOUBLE NOT NULL DEFAULT 0.5
);

CREATE INDEX idx_platform_stats_computed_at ON platform_stats(computed_at DESC);
```

---

## 7. Integration Points

### 7.1 ServiceRegistry Updates
```java
// Add to ServiceRegistry
private final UserStatsStorage userStatsStorage;
private final PlatformStatsStorage platformStatsStorage;
private final StatsService statsService;

// Getters...
```

### 7.2 ServiceRegistryBuilder Updates
```java
public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
    // ... existing storage creation ...

    H2UserStatsStorage userStatsStorage = new H2UserStatsStorage(dbManager);
    H2PlatformStatsStorage platformStatsStorage = new H2PlatformStatsStorage(dbManager);

    StatsService statsService = new StatsService(
        userStorage,
        likeStorage,
        matchStorage,
        blockStorage,
        reportStorage,
        userStatsStorage,
        platformStatsStorage
    );

    // ... build registry ...
}
```

---

## 8. Console UI Changes

### 8.1 New Menu Option
```
═══════════════════════════════════════
         DATING APP - PHASE 0.5
═══════════════════════════════════════
  Current User: Alice (ACTIVE)
───────────────────────────────────────
  1. Create new user
  2. Select existing user
  3. Complete/edit my profile
  4. Browse candidates
  5. View my matches
  6. Block a user
  7. Report a user
  8. View my statistics      <-- NEW
  0. Exit
═══════════════════════════════════════
```

### 8.2 Statistics Display
```
═══════════════════════════════════════
         YOUR DATING STATISTICS
═══════════════════════════════════════
  Last updated: 2026-01-08 14:30

  📊 ACTIVITY
  ───────────────────────────────────
  Swipes given:     247 (189 likes, 58 passes)
  Like ratio:       76.5% (you like most people)
  Swipes received:  312 (201 likes, 111 passes)

  💕 MATCHES
  ───────────────────────────────────
  Total matches:    43
  Active matches:   12
  Match rate:       22.8% (above average!)

  🎯 YOUR SCORES
  ───────────────────────────────────
  Reciprocity:      34.2% (of your likes, liked you back)
  Selectiveness:    Below average (you're open-minded)
  Attractiveness:   Above average

  ⚠️  SAFETY
  ───────────────────────────────────
  Blocks given: 3 | Reports given: 1

  Press Enter to return...
═══════════════════════════════════════
```

---

## 9. Implementation Steps

### Step 1: Storage Interfaces & Models (1-2 hours)
1. Create `UserStats.java` record in `core/`
2. Create `PlatformStats.java` record in `core/`
3. Create `UserStatsStorage.java` interface in `core/`
4. Create `PlatformStatsStorage.java` interface in `core/`
5. Add new methods to `LikeStorage`, `BlockStorage`, `ReportStorage`

### Step 2: H2 Storage Implementations (2-3 hours)
1. Create `H2UserStatsStorage.java` in `storage/`
2. Create `H2PlatformStatsStorage.java` in `storage/`
3. Add new query methods to `H2LikeStorage`, `H2BlockStorage`, `H2ReportStorage`
4. Update `DatabaseManager` to create new tables

### Step 3: StatsService (2-3 hours)
1. Create `StatsService.java` in `core/`
2. Implement `computeAndSaveStats()`
3. Implement `computeAndSavePlatformStats()`
4. Implement `snapshotAllUsers()`

### Step 4: Integration (1-2 hours)
1. Update `ServiceRegistry` and `ServiceRegistryBuilder`
2. Add stats menu option to `Main.java`
3. Implement stats display formatting

### Step 5: Testing (2-3 hours)
1. Unit tests for `UserStats` validation
2. Unit tests for `StatsService` computation logic
3. Integration tests for H2 storage
4. Manual testing of console UI

---

## 10. Test Plan

### 10.1 Unit Tests

| Test | Description |
|------|-------------|
| `UserStatsTest.validatesRatios` | Ratios must be 0.0-1.0 |
| `UserStatsTest.builderDefaults` | Builder starts with sensible defaults |
| `StatsServiceTest.computesLikeRatio` | Correct calculation: likes / (likes + passes) |
| `StatsServiceTest.computesMatchRate` | Correct calculation: matches / likes |
| `StatsServiceTest.computesReciprocity` | Correct mutual like percentage |
| `StatsServiceTest.handlesZeroDivision` | No errors when user has no activity |
| `StatsServiceTest.capsRatiosAtOne` | Match rate can't exceed 1.0 |

### 10.2 Integration Tests

| Test | Description |
|------|-------------|
| `H2UserStatsStorageTest.savesAndRetrieves` | Full roundtrip |
| `H2UserStatsStorageTest.getsLatestOnly` | Returns most recent snapshot |
| `H2UserStatsStorageTest.getsHistory` | Returns ordered history |
| `StatsServiceIntegrationTest.fullSnapshot` | End-to-end computation |

---

## 11. Success Criteria

- [ ] `UserStats` record captures all metrics defined above
- [ ] `StatsService.computeAndSaveStats()` correctly computes all fields
- [ ] Historical snapshots are stored and retrievable
- [ ] Platform averages are computed and used for derived scores
- [ ] Console displays user statistics in readable format
- [ ] Division by zero is handled gracefully
- [ ] All new code is in `core/` with zero framework imports
- [ ] All tests pass

---

## 12. Future Enhancements (Not in Scope)

- Weekly/monthly trend comparisons
- Graphical charts (requires UI framework)
- Email digest of stats
- Competitive leaderboards
- Anomaly detection (unusual activity patterns)
