# Standouts Feature - Implementation Plan

**Feature:** Curated grid of 10 exceptional profiles daily (separate from Daily Pick)
**Phase:** 1.5 → 2.0
**Estimated Complexity:** Medium-High
**Dependencies:** MatchQualityService, CandidateFinder, ProfilePreviewService

---

## 1. Feature Overview

### 1.1 What is Standouts?

Standouts is a daily curated collection of **10 exceptional profiles** that represent the highest-quality matches for a user. Unlike the random "Daily Pick" (serendipity-focused), Standouts uses **data-driven ranking** to surface profiles with:

- High predicted compatibility scores
- Complete, high-quality profiles
- Strong lifestyle/interest alignment
- Recent activity (engaged users)

### 1.2 Key Differentiators from Daily Pick

| Aspect              | Daily Pick                    | Standouts                       |
|---------------------|-------------------------------|---------------------------------|
| **Count**           | 1 profile                     | 10 profiles                     |
| **Selection**       | Random (serendipity)          | Ranked by compatibility         |
| **Filters Applied** | Minimal (blocks only)         | Full 7-stage pipeline           |
| **Refresh**         | Daily at midnight             | Daily at midnight               |
| **Purpose**         | Discovery outside preferences | Best matches within preferences |
| **UI**              | Single card, full display     | Grid view, compact cards        |

### 1.3 User Value Proposition

- **Save time**: See best matches first without endless swiping
- **Quality over quantity**: Curated excellence vs. random browsing
- **Daily refresh**: New opportunities every day
- **Engagement driver**: Reason to return daily

---

## 2. Functional Requirements

### 2.1 Core Requirements

| ID  | Requirement                                             | Priority |
|-----|---------------------------------------------------------|----------|
| F1  | Display 10 standout profiles daily per user             | Must     |
| F2  | Rank profiles by composite quality score                | Must     |
| F3  | Apply full candidate filtering (7-stage pipeline)       | Must     |
| F4  | Refresh standouts at midnight (timezone-aware)          | Must     |
| F5  | Track which standouts user has viewed/interacted with   | Must     |
| F6  | Allow like/pass on individual standouts                 | Must     |
| F7  | Show compatibility score and top highlight per standout | Must     |
| F8  | Prevent duplicate standouts across consecutive days     | Should   |
| F9  | Show "why standout" explanation for each profile        | Should   |
| F10 | Integrate with daily like limits                        | Must     |

### 2.2 Ranking Algorithm Requirements

The **Standout Score** combines multiple quality signals:

```
StandoutScore = (
    CompatibilityScore × 0.50 +      // From MatchQualityService (modified for pre-match)
    ProfileCompleteness × 0.20 +     // From ProfilePreviewService
    InterestOverlap × 0.15 +         // From InterestMatcher
    RecentActivity × 0.10 +          // Last login/swipe activity
    MutualFitBonus × 0.05            // Bonus if they'd likely match back
)
```

**Pre-Match Compatibility Score** (modified from MatchQualityService):
- Distance score (15%)
- Age score (10%)
- Interest score (30%)
- Lifestyle score (30%)
- ~~Response time~~ → Replaced with **Profile Quality** (15%)

### 2.3 Edge Cases

| Scenario                         | Behavior                                 |
|----------------------------------|------------------------------------------|
| < 10 candidates pass filters     | Show all available (may be 0-9)          |
| User has no interests set        | Use lifestyle-focused scoring weights    |
| All standouts already interacted | Show message "Check back tomorrow!"      |
| User at daily like limit         | Show standouts but disable like button   |
| New user (no swipe history)      | Use default scoring, no mutual fit bonus |

---

## 3. Architecture Design

### 3.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                           CLI Layer                              │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    MatchingHandler                           ││
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  ││
│  │  │ Daily Pick  │  │  Standouts  │  │  Browse Candidates  │  ││
│  │  │   Display   │  │    Grid     │  │      Display        │  ││
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘  ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Core Layer                               │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐ │
│  │ StandoutsService │  │MatchQualityService│  │CandidateFinder │ │
│  │   (NEW)          │  │   (existing)      │  │  (existing)    │ │
│  └────────┬─────────┘  └────────┬─────────┘  └───────┬────────┘ │
│           │                     │                    │          │
│  ┌────────▼─────────────────────▼────────────────────▼────────┐ │
│  │                    StandoutRanker (NEW)                     │ │
│  │  - calculateStandoutScore(seeker, candidate)                │ │
│  │  - rankCandidates(seeker, candidates) → top 10              │ │
│  │  - generateStandoutReason(seeker, candidate)                │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌───────────────────────────▼─────────────────────────────────┐ │
│  │               StandoutStorage (NEW interface)                │ │
│  │  - saveStandouts(userId, List<Standout>, date)              │ │
│  │  - getStandouts(userId, date) → List<Standout>              │ │
│  │  - markInteracted(userId, standoutUserId, date)             │ │
│  │  - hasViewed(userId, date) → boolean                        │ │
│  │  - cleanup(beforeDate) → int                                │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Storage Layer                              │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                 H2StandoutStorage (NEW)                      ││
│  │  - standouts table (user_id, standout_user_id, date, etc.)  ││
│  │  - standout_views table (user_id, date, viewed_at)          ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Data Flow

```
1. User opens Standouts menu
         │
         ▼
2. StandoutsService.getStandouts(userId)
         │
         ├─► Check cache: standoutStorage.getStandouts(userId, today)
         │         │
         │         ├─► If cached & valid → Return cached standouts
         │         │
         │         └─► If not cached or stale → Continue to step 3
         │
         ▼
3. Generate fresh standouts:
   a) CandidateFinder.findCandidates(seeker, allActive, excluded)
         │
         ▼
   b) StandoutRanker.rankCandidates(seeker, candidates)
      - Calculate StandoutScore for each candidate
      - Sort descending by score
      - Take top 10
         │
         ▼
   c) Build Standout records with reasons
         │
         ▼
   d) standoutStorage.saveStandouts(userId, standouts, today)
         │
         ▼
4. Return List<Standout> to CLI
         │
         ▼
5. MatchingHandler displays grid UI
         │
         ▼
6. User selects standout [1-10]
         │
         ├─► [L]ike → Check daily limit → Record like → Mark interacted
         │
         ├─► [P]ass → Record pass → Mark interacted
         │
         └─► [V]iew details → Show full profile + compatibility breakdown
```

---

## 4. Domain Models

### 4.1 Standout Record

**Location:** `src/main/java/datingapp/core/Standout.java`

```java
package datingapp.core;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a standout profile selection for a user on a specific date.
 * Immutable record containing the candidate, their ranking score, and interaction status.
 */
public record Standout(
        UUID id,
        UUID seekerId,           // The user viewing standouts
        UUID standoutUserId,     // The featured profile
        LocalDate featuredDate,  // Date this standout was generated
        int rank,                // Position 1-10 in the grid
        int standoutScore,       // Composite score 0-100
        String reason,           // "High compatibility" / "Shared interests" / etc.
        Instant createdAt,
        Instant interactedAt     // null if not yet interacted
) {
    public Standout {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(seekerId, "seekerId required");
        Objects.requireNonNull(standoutUserId, "standoutUserId required");
        Objects.requireNonNull(featuredDate, "featuredDate required");
        Objects.requireNonNull(reason, "reason required");
        Objects.requireNonNull(createdAt, "createdAt required");

        if (rank < 1 || rank > 10) {
            throw new IllegalArgumentException("rank must be 1-10, got: " + rank);
        }
        if (standoutScore < 0 || standoutScore > 100) {
            throw new IllegalArgumentException("standoutScore must be 0-100, got: " + standoutScore);
        }
    }

    /**
     * Factory method for creating a new standout.
     */
    public static Standout create(UUID seekerId, UUID standoutUserId, LocalDate date,
                                   int rank, int score, String reason) {
        return new Standout(
                UUID.randomUUID(),
                seekerId,
                standoutUserId,
                date,
                rank,
                score,
                reason,
                Instant.now(),
                null
        );
    }

    /**
     * Factory method for loading from database.
     */
    public static Standout fromDatabase(UUID id, UUID seekerId, UUID standoutUserId,
                                         LocalDate featuredDate, int rank, int standoutScore,
                                         String reason, Instant createdAt, Instant interactedAt) {
        return new Standout(id, seekerId, standoutUserId, featuredDate, rank,
                            standoutScore, reason, createdAt, interactedAt);
    }

    public boolean hasInteracted() {
        return interactedAt != null;
    }

    public Standout withInteraction(Instant timestamp) {
        return new Standout(id, seekerId, standoutUserId, featuredDate, rank,
                            standoutScore, reason, createdAt, timestamp);
    }
}
```

### 4.2 StandoutResult Record

**Location:** `src/main/java/datingapp/core/StandoutResult.java`

```java
package datingapp.core;

import java.util.List;

/**
 * Result object containing standouts and metadata for display.
 */
public record StandoutResult(
        List<Standout> standouts,
        int totalCandidates,      // How many passed filters before ranking
        boolean fromCache,        // Whether result was cached or freshly computed
        String message            // Optional message ("Check back tomorrow!", etc.)
) {
    public boolean isEmpty() {
        return standouts == null || standouts.isEmpty();
    }

    public int count() {
        return standouts != null ? standouts.size() : 0;
    }

    public static StandoutResult empty(String message) {
        return new StandoutResult(List.of(), 0, false, message);
    }

    public static StandoutResult of(List<Standout> standouts, int totalCandidates, boolean cached) {
        return new StandoutResult(standouts, totalCandidates, cached, null);
    }
}
```

### 4.3 StandoutScore Record (Internal)

**Location:** `src/main/java/datingapp/core/StandoutScore.java`

```java
package datingapp.core;

/**
 * Internal record for ranking calculation. Not persisted.
 */
record StandoutScore(
        User candidate,
        double compatibilityScore,    // 0.0-1.0
        double profileCompleteness,   // 0.0-1.0
        double interestOverlap,       // 0.0-1.0
        double recentActivity,        // 0.0-1.0
        double mutualFitBonus,        // 0.0-1.0
        int compositeScore            // 0-100 final score
) {
    // Weights for composite calculation
    private static final double W_COMPATIBILITY = 0.50;
    private static final double W_PROFILE = 0.20;
    private static final double W_INTERESTS = 0.15;
    private static final double W_ACTIVITY = 0.10;
    private static final double W_MUTUAL = 0.05;

    public static StandoutScore calculate(User seeker, User candidate,
                                           double compatibility, double completeness,
                                           double interests, double activity, double mutual) {
        double weighted =
            compatibility * W_COMPATIBILITY +
            completeness * W_PROFILE +
            interests * W_INTERESTS +
            activity * W_ACTIVITY +
            mutual * W_MUTUAL;

        int composite = (int) Math.round(weighted * 100);
        return new StandoutScore(candidate, compatibility, completeness,
                                  interests, activity, mutual, composite);
    }
}
```

---

## 5. Storage Layer

### 5.1 StandoutStorage Interface

**Location:** `src/main/java/datingapp/core/StandoutStorage.java`

```java
package datingapp.core;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Storage interface for standout profile selections.
 * Defined in core package (no database imports).
 */
public interface StandoutStorage {

    /**
     * Save or update standouts for a user on a specific date.
     * Replaces any existing standouts for that user/date combination.
     */
    void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date);

    /**
     * Get standouts for a user on a specific date.
     * Returns empty list if none exist.
     */
    List<Standout> getStandouts(UUID seekerId, LocalDate date);

    /**
     * Mark a specific standout as interacted (liked or passed).
     */
    void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date);

    /**
     * Check if user has viewed standouts today (opened the grid).
     */
    boolean hasViewedToday(UUID seekerId, LocalDate date);

    /**
     * Mark that user has viewed standouts grid.
     */
    void markViewed(UUID seekerId, LocalDate date);

    /**
     * Get count of uninteracted standouts for a user/date.
     */
    int countUninteracted(UUID seekerId, LocalDate date);

    /**
     * Check if a specific user was a standout recently (for diversity).
     * Returns true if standoutUserId appeared in seeker's standouts within last N days.
     */
    boolean wasRecentStandout(UUID seekerId, UUID standoutUserId, int withinDays);

    /**
     * Cleanup old standout records.
     * @return number of records deleted
     */
    int cleanup(LocalDate before);
}
```

### 5.2 Database Schema

**Location:** Add to `DatabaseManager.initializeSchema()`

```java
// ═══════════════════════════════════════════════════════════════
// STANDOUTS TABLES
// ═══════════════════════════════════════════════════════════════

// Main standouts table - stores daily selections
stmt.execute("""
    CREATE TABLE IF NOT EXISTS standouts (
        id UUID PRIMARY KEY,
        seeker_id UUID NOT NULL,
        standout_user_id UUID NOT NULL,
        featured_date DATE NOT NULL,
        rank INT NOT NULL,
        standout_score INT NOT NULL,
        reason VARCHAR(200) NOT NULL,
        created_at TIMESTAMP NOT NULL,
        interacted_at TIMESTAMP,
        CONSTRAINT fk_standouts_seeker FOREIGN KEY (seeker_id) REFERENCES users(id),
        CONSTRAINT fk_standouts_user FOREIGN KEY (standout_user_id) REFERENCES users(id),
        CONSTRAINT uk_standouts_daily UNIQUE (seeker_id, standout_user_id, featured_date)
    )
    """);

// Index for fetching user's daily standouts
stmt.execute("""
    CREATE INDEX IF NOT EXISTS idx_standouts_seeker_date
    ON standouts(seeker_id, featured_date DESC)
    """);

// Index for diversity check (was this person a recent standout?)
stmt.execute("""
    CREATE INDEX IF NOT EXISTS idx_standouts_diversity
    ON standouts(seeker_id, standout_user_id, featured_date DESC)
    """);

// View tracking table - tracks when user opened standouts grid
stmt.execute("""
    CREATE TABLE IF NOT EXISTS standout_views (
        seeker_id UUID NOT NULL,
        viewed_date DATE NOT NULL,
        viewed_at TIMESTAMP NOT NULL,
        PRIMARY KEY (seeker_id, viewed_date)
    )
    """);
```

### 5.3 H2StandoutStorage Implementation

**Location:** `src/main/java/datingapp/storage/H2StandoutStorage.java`

```java
package datingapp.storage;

import datingapp.core.Standout;
import datingapp.core.StandoutStorage;
import datingapp.core.StorageException;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class H2StandoutStorage implements StandoutStorage {

    private final DatabaseManager dbManager;

    public H2StandoutStorage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date) {
        String deleteSql = "DELETE FROM standouts WHERE seeker_id = ? AND featured_date = ?";
        String insertSql = """
            INSERT INTO standouts (id, seeker_id, standout_user_id, featured_date,
                                   rank, standout_score, reason, created_at, interacted_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection()) {
            // Delete existing standouts for this date
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setObject(1, seekerId);
                deleteStmt.setDate(2, Date.valueOf(date));
                deleteStmt.executeUpdate();
            }

            // Insert new standouts
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (Standout s : standouts) {
                    insertStmt.setObject(1, s.id());
                    insertStmt.setObject(2, s.seekerId());
                    insertStmt.setObject(3, s.standoutUserId());
                    insertStmt.setDate(4, Date.valueOf(s.featuredDate()));
                    insertStmt.setInt(5, s.rank());
                    insertStmt.setInt(6, s.standoutScore());
                    insertStmt.setString(7, s.reason());
                    insertStmt.setTimestamp(8, Timestamp.from(s.createdAt()));
                    insertStmt.setTimestamp(9, s.interactedAt() != null
                        ? Timestamp.from(s.interactedAt()) : null);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to save standouts", e);
        }
    }

    @Override
    public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
        String sql = """
            SELECT id, seeker_id, standout_user_id, featured_date, rank,
                   standout_score, reason, created_at, interacted_at
            FROM standouts
            WHERE seeker_id = ? AND featured_date = ?
            ORDER BY rank ASC
            """;

        List<Standout> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, seekerId);
            stmt.setDate(2, Date.valueOf(date));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapToStandout(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to get standouts", e);
        }
        return results;
    }

    @Override
    public void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date) {
        String sql = """
            UPDATE standouts
            SET interacted_at = ?
            WHERE seeker_id = ? AND standout_user_id = ? AND featured_date = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setObject(2, seekerId);
            stmt.setObject(3, standoutUserId);
            stmt.setDate(4, Date.valueOf(date));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to mark standout interacted", e);
        }
    }

    @Override
    public boolean hasViewedToday(UUID seekerId, LocalDate date) {
        String sql = "SELECT COUNT(*) FROM standout_views WHERE seeker_id = ? AND viewed_date = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, seekerId);
            stmt.setDate(2, Date.valueOf(date));

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to check standout view status", e);
        }
    }

    @Override
    public void markViewed(UUID seekerId, LocalDate date) {
        String sql = """
            MERGE INTO standout_views (seeker_id, viewed_date, viewed_at)
            KEY (seeker_id, viewed_date)
            VALUES (?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, seekerId);
            stmt.setDate(2, Date.valueOf(date));
            stmt.setTimestamp(3, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to mark standouts viewed", e);
        }
    }

    @Override
    public int countUninteracted(UUID seekerId, LocalDate date) {
        String sql = """
            SELECT COUNT(*) FROM standouts
            WHERE seeker_id = ? AND featured_date = ? AND interacted_at IS NULL
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, seekerId);
            stmt.setDate(2, Date.valueOf(date));

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to count uninteracted standouts", e);
        }
    }

    @Override
    public boolean wasRecentStandout(UUID seekerId, UUID standoutUserId, int withinDays) {
        String sql = """
            SELECT COUNT(*) FROM standouts
            WHERE seeker_id = ? AND standout_user_id = ?
            AND featured_date >= ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, seekerId);
            stmt.setObject(2, standoutUserId);
            stmt.setDate(3, Date.valueOf(LocalDate.now().minusDays(withinDays)));

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to check recent standout", e);
        }
    }

    @Override
    public int cleanup(LocalDate before) {
        String sql1 = "DELETE FROM standouts WHERE featured_date < ?";
        String sql2 = "DELETE FROM standout_views WHERE viewed_date < ?";

        int deleted = 0;
        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                stmt.setDate(1, Date.valueOf(before));
                deleted += stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(sql2)) {
                stmt.setDate(1, Date.valueOf(before));
                deleted += stmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to cleanup standouts", e);
        }
        return deleted;
    }

    private Standout mapToStandout(ResultSet rs) throws SQLException {
        Timestamp interactedAt = rs.getTimestamp("interacted_at");
        return Standout.fromDatabase(
            rs.getObject("id", UUID.class),
            rs.getObject("seeker_id", UUID.class),
            rs.getObject("standout_user_id", UUID.class),
            rs.getDate("featured_date").toLocalDate(),
            rs.getInt("rank"),
            rs.getInt("standout_score"),
            rs.getString("reason"),
            rs.getTimestamp("created_at").toInstant(),
            interactedAt != null ? interactedAt.toInstant() : null
        );
    }
}
```

---

## 6. Service Layer

### 6.1 StandoutRanker (Scoring Engine)

**Location:** `src/main/java/datingapp/core/StandoutRanker.java`

```java
package datingapp.core;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ranking engine that scores and selects standout profiles.
 * Stateless utility - all data passed as parameters.
 */
public class StandoutRanker {

    private static final int MAX_STANDOUTS = 10;
    private static final int DIVERSITY_DAYS = 3;  // Don't repeat standouts within 3 days

    private final ProfilePreviewService profilePreviewService;
    private final MatchQualityConfig qualityConfig;

    public StandoutRanker(ProfilePreviewService profilePreviewService,
                          MatchQualityConfig qualityConfig) {
        this.profilePreviewService = Objects.requireNonNull(profilePreviewService);
        this.qualityConfig = Objects.requireNonNull(qualityConfig);
    }

    /**
     * Rank candidates and return top 10 standouts.
     *
     * @param seeker The user requesting standouts
     * @param candidates Pre-filtered candidates (already passed 7-stage pipeline)
     * @param recentStandoutIds Set of user IDs that were standouts in last N days
     * @return List of StandoutScore objects, sorted descending, max 10
     */
    public List<StandoutScore> rankCandidates(User seeker, List<User> candidates,
                                               Set<UUID> recentStandoutIds) {
        return candidates.stream()
            .filter(c -> !recentStandoutIds.contains(c.getId()))  // Diversity filter
            .map(candidate -> calculateScore(seeker, candidate))
            .sorted(Comparator.comparingInt(StandoutScore::compositeScore).reversed())
            .limit(MAX_STANDOUTS)
            .collect(Collectors.toList());
    }

    /**
     * Calculate composite standout score for a candidate.
     */
    public StandoutScore calculateScore(User seeker, User candidate) {
        // 1. Compatibility Score (pre-match version - no response time)
        double compatibility = calculatePreMatchCompatibility(seeker, candidate);

        // 2. Profile Completeness
        double completeness = profilePreviewService.calculateCompleteness(candidate) / 100.0;

        // 3. Interest Overlap
        InterestMatcher.MatchResult interests = InterestMatcher.compare(
            seeker.getInterests(), candidate.getInterests());
        double interestScore = interests.overlapRatio();

        // 4. Recent Activity (placeholder - based on updatedAt)
        double activityScore = calculateActivityScore(candidate);

        // 5. Mutual Fit Bonus (would they like seeker back?)
        double mutualFit = calculateMutualFitBonus(seeker, candidate);

        return StandoutScore.calculate(seeker, candidate,
            compatibility, completeness, interestScore, activityScore, mutualFit);
    }

    /**
     * Pre-match compatibility (without response time factor).
     * Uses 4 of the 5 MatchQuality factors, re-weighted.
     */
    private double calculatePreMatchCompatibility(User seeker, User candidate) {
        // Distance score
        double distanceKm = GeoUtils.distanceKm(
            seeker.getLat(), seeker.getLon(),
            candidate.getLat(), candidate.getLon());
        double distanceScore = Math.max(0, 1.0 - (distanceKm / seeker.getMaxDistanceKm()));

        // Age score
        int ageDiff = Math.abs(seeker.getAge() - candidate.getAge());
        double avgAgeRange = ((seeker.getMaxAge() - seeker.getMinAge()) +
                              (candidate.getMaxAge() - candidate.getMinAge())) / 2.0;
        double ageScore = avgAgeRange > 0 ? Math.max(0, 1.0 - (ageDiff / avgAgeRange)) : 0.5;

        // Interest score
        InterestMatcher.MatchResult interests = InterestMatcher.compare(
            seeker.getInterests(), candidate.getInterests());
        double interestScore = calculateInterestScore(interests, seeker, candidate);

        // Lifestyle score
        double lifestyleScore = calculateLifestyleScore(seeker, candidate);

        // Re-weighted for pre-match (no response time)
        // Original: 15% distance, 10% age, 30% interests, 30% lifestyle, 15% response
        // Pre-match: 20% distance, 15% age, 35% interests, 30% lifestyle
        return distanceScore * 0.20 +
               ageScore * 0.15 +
               interestScore * 0.35 +
               lifestyleScore * 0.30;
    }

    private double calculateInterestScore(InterestMatcher.MatchResult match,
                                           User seeker, User candidate) {
        if (seeker.getInterests().isEmpty() && candidate.getInterests().isEmpty()) {
            return 0.5;  // Neutral
        }
        if (seeker.getInterests().isEmpty() || candidate.getInterests().isEmpty()) {
            return 0.3;  // Penalty for missing interests
        }
        return match.overlapRatio();
    }

    private double calculateLifestyleScore(User seeker, User candidate) {
        int matches = 0;
        int total = 0;

        // Smoking
        if (seeker.getSmoking() != null && candidate.getSmoking() != null) {
            total++;
            if (seeker.getSmoking() == candidate.getSmoking()) matches++;
        }

        // Drinking
        if (seeker.getDrinking() != null && candidate.getDrinking() != null) {
            total++;
            if (seeker.getDrinking() == candidate.getDrinking()) matches++;
        }

        // Wants Kids
        if (seeker.getWantsKids() != null && candidate.getWantsKids() != null) {
            total++;
            if (areKidsPreferencesCompatible(seeker.getWantsKids(), candidate.getWantsKids())) {
                matches++;
            }
        }

        // Looking For
        if (seeker.getLookingFor() != null && candidate.getLookingFor() != null) {
            total++;
            if (seeker.getLookingFor() == candidate.getLookingFor()) matches++;
        }

        return total > 0 ? (double) matches / total : 0.5;
    }

    private boolean areKidsPreferencesCompatible(Lifestyle.WantsKids a, Lifestyle.WantsKids b) {
        if (a == b) return true;
        if (a == Lifestyle.WantsKids.OPEN || b == Lifestyle.WantsKids.OPEN) return true;
        if ((a == Lifestyle.WantsKids.SOMEDAY && b == Lifestyle.WantsKids.HAS_KIDS) ||
            (b == Lifestyle.WantsKids.SOMEDAY && a == Lifestyle.WantsKids.HAS_KIDS)) return true;
        return false;
    }

    private double calculateActivityScore(User candidate) {
        if (candidate.getUpdatedAt() == null) return 0.5;

        Duration sinceUpdate = Duration.between(candidate.getUpdatedAt(), Instant.now());
        long hours = sinceUpdate.toHours();

        if (hours < 1) return 1.0;      // Active in last hour
        if (hours < 24) return 0.9;     // Active today
        if (hours < 72) return 0.7;     // Active in 3 days
        if (hours < 168) return 0.5;    // Active this week
        if (hours < 720) return 0.3;    // Active this month
        return 0.1;                      // Inactive
    }

    private double calculateMutualFitBonus(User seeker, User candidate) {
        // Check if candidate's preferences would accept seeker
        // Gender preference match
        if (candidate.getInterestedIn() != null &&
            !candidate.getInterestedIn().contains(seeker.getGender())) {
            return 0.0;  // They wouldn't match back
        }

        // Age preference match
        int seekerAge = seeker.getAge();
        if (seekerAge < candidate.getMinAge() || seekerAge > candidate.getMaxAge()) {
            return 0.0;  // Outside their age range
        }

        // Distance preference (would we be in their range?)
        double distanceKm = GeoUtils.distanceKm(
            seeker.getLat(), seeker.getLon(),
            candidate.getLat(), candidate.getLon());
        if (distanceKm > candidate.getMaxDistanceKm()) {
            return 0.2;  // Outside their distance, lower bonus
        }

        return 1.0;  // Good mutual fit
    }

    /**
     * Generate human-readable reason for why this profile is a standout.
     */
    public String generateReason(User seeker, User candidate, StandoutScore score) {
        List<String> reasons = new ArrayList<>();

        // High compatibility
        if (score.compatibilityScore() >= 0.8) {
            reasons.add("Highly compatible");
        } else if (score.compatibilityScore() >= 0.6) {
            reasons.add("Good compatibility");
        }

        // Strong interest overlap
        InterestMatcher.MatchResult interests = InterestMatcher.compare(
            seeker.getInterests(), candidate.getInterests());
        if (interests.sharedCount() >= 3) {
            reasons.add("Many shared interests");
        } else if (interests.sharedCount() >= 1) {
            reasons.add("Shared interests");
        }

        // Complete profile
        if (score.profileCompleteness() >= 0.9) {
            reasons.add("Complete profile");
        }

        // Nearby
        double distanceKm = GeoUtils.distanceKm(
            seeker.getLat(), seeker.getLon(),
            candidate.getLat(), candidate.getLon());
        if (distanceKm < 5) {
            reasons.add("Lives nearby");
        }

        // Same relationship goals
        if (seeker.getLookingFor() != null &&
            seeker.getLookingFor() == candidate.getLookingFor()) {
            reasons.add("Same relationship goals");
        }

        // Recently active
        if (score.recentActivity() >= 0.9) {
            reasons.add("Recently active");
        }

        // Fallback
        if (reasons.isEmpty()) {
            reasons.add("Top match for you");
        }

        // Return first reason (most relevant based on order)
        return reasons.get(0);
    }
}
```

### 6.2 StandoutsService (Main Service)

**Location:** `src/main/java/datingapp/core/StandoutsService.java`

```java
package datingapp.core;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main service for standouts feature.
 * Orchestrates candidate finding, ranking, and storage.
 */
public class StandoutsService {

    private static final int DIVERSITY_DAYS = 3;

    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final BlockStorage blockStorage;
    private final StandoutStorage standoutStorage;
    private final CandidateFinderService candidateFinder;
    private final StandoutRanker ranker;
    private final AppConfig config;

    public StandoutsService(UserStorage userStorage,
                            LikeStorage likeStorage,
                            BlockStorage blockStorage,
                            StandoutStorage standoutStorage,
                            CandidateFinderService candidateFinder,
                            StandoutRanker ranker,
                            AppConfig config) {
        this.userStorage = Objects.requireNonNull(userStorage);
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.blockStorage = Objects.requireNonNull(blockStorage);
        this.standoutStorage = Objects.requireNonNull(standoutStorage);
        this.candidateFinder = Objects.requireNonNull(candidateFinder);
        this.ranker = Objects.requireNonNull(ranker);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Get today's standouts for a user.
     * Returns cached standouts if available, otherwise generates fresh.
     */
    public StandoutResult getStandouts(User seeker) {
        LocalDate today = LocalDate.now(config.userTimeZone());

        // Check for cached standouts
        List<Standout> cached = standoutStorage.getStandouts(seeker.getId(), today);
        if (!cached.isEmpty()) {
            return StandoutResult.of(cached, cached.size(), true);
        }

        // Generate fresh standouts
        return generateStandouts(seeker, today);
    }

    /**
     * Generate fresh standouts for a user.
     */
    private StandoutResult generateStandouts(User seeker, LocalDate date) {
        // Get all active users
        List<User> activeUsers = userStorage.findActive();

        // Build exclusion set
        Set<UUID> alreadyInteracted = likeStorage.getLikedOrPassedUserIds(seeker.getId());
        Set<UUID> blockedUsers = blockStorage.getBlockedUserIds(seeker.getId());
        Set<UUID> excluded = new HashSet<>(alreadyInteracted);
        excluded.addAll(blockedUsers);

        // Apply 7-stage filter pipeline
        List<User> candidates = candidateFinder.findCandidates(seeker, activeUsers, excluded);

        if (candidates.isEmpty()) {
            return StandoutResult.empty("No standouts available. Try adjusting your preferences!");
        }

        // Get recent standouts for diversity filtering
        Set<UUID> recentStandoutIds = getRecentStandoutIds(seeker.getId());

        // Rank candidates
        List<StandoutScore> ranked = ranker.rankCandidates(seeker, candidates, recentStandoutIds);

        if (ranked.isEmpty()) {
            return StandoutResult.empty("Check back tomorrow for fresh standouts!");
        }

        // Convert to Standout records
        List<Standout> standouts = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            StandoutScore score = ranked.get(i);
            String reason = ranker.generateReason(seeker, score.candidate(), score);

            Standout standout = Standout.create(
                seeker.getId(),
                score.candidate().getId(),
                date,
                i + 1,  // rank 1-10
                score.compositeScore(),
                reason
            );
            standouts.add(standout);
        }

        // Cache standouts
        standoutStorage.saveStandouts(seeker.getId(), standouts, date);

        return StandoutResult.of(standouts, candidates.size(), false);
    }

    /**
     * Get standout user IDs from recent days for diversity.
     */
    private Set<UUID> getRecentStandoutIds(UUID seekerId) {
        Set<UUID> recent = new HashSet<>();
        LocalDate today = LocalDate.now(config.userTimeZone());

        for (int i = 1; i <= DIVERSITY_DAYS; i++) {
            LocalDate pastDate = today.minusDays(i);
            List<Standout> pastStandouts = standoutStorage.getStandouts(seekerId, pastDate);
            for (Standout s : pastStandouts) {
                recent.add(s.standoutUserId());
            }
        }

        return recent;
    }

    /**
     * Mark that user has viewed standouts grid today.
     */
    public void markViewed(UUID seekerId) {
        LocalDate today = LocalDate.now(config.userTimeZone());
        standoutStorage.markViewed(seekerId, today);
    }

    /**
     * Check if user has viewed standouts today.
     */
    public boolean hasViewedToday(UUID seekerId) {
        LocalDate today = LocalDate.now(config.userTimeZone());
        return standoutStorage.hasViewedToday(seekerId, today);
    }

    /**
     * Mark a standout as interacted (after like/pass).
     */
    public void markInteracted(UUID seekerId, UUID standoutUserId) {
        LocalDate today = LocalDate.now(config.userTimeZone());
        standoutStorage.markInteracted(seekerId, standoutUserId, today);
    }

    /**
     * Get count of uninteracted standouts for today.
     */
    public int countUninteracted(UUID seekerId) {
        LocalDate today = LocalDate.now(config.userTimeZone());
        return standoutStorage.countUninteracted(seekerId, today);
    }

    /**
     * Resolve standout user IDs to full User objects.
     */
    public Map<UUID, User> resolveUsers(List<Standout> standouts) {
        Map<UUID, User> users = new HashMap<>();
        for (Standout s : standouts) {
            User user = userStorage.get(s.standoutUserId());
            if (user != null) {
                users.put(s.standoutUserId(), user);
            }
        }
        return users;
    }
}
```

---

## 7. CLI Integration

### 7.1 Menu Integration

**Location:** Update `Main.java` menu

```java
// In printMenu() method, add new option:
logger.info("  [12] 🌟 View Standouts");

// In main loop switch statement:
case "12" -> matchingHandler.viewStandouts();
```

### 7.2 MatchingHandler.viewStandouts()

**Location:** Add to `MatchingHandler.java`

```java
/**
 * Display the daily standouts grid and handle interactions.
 */
public void viewStandouts() {
    if (!userSession.isActive()) {
        logger.info(CliConstants.PLEASE_SELECT_USER);
        return;
    }

    User currentUser = userSession.getCurrentUser();
    StandoutResult result = standoutsService.getStandouts(currentUser);

    // Mark as viewed
    standoutsService.markViewed(currentUser.getId());

    // Display header
    logger.info("\n");
    logger.info(CliConstants.SEPARATOR_LINE);
    logger.info("         🌟 YOUR STANDOUTS 🌟");
    logger.info(CliConstants.SEPARATOR_LINE);

    if (result.isEmpty()) {
        logger.info("\n  {}\n", result.message());
        return;
    }

    logger.info("\n  Today's top {} matches for you:\n", result.count());

    // Resolve user objects
    Map<UUID, User> users = standoutsService.resolveUsers(result.standouts());

    // Display grid (2 columns × 5 rows)
    displayStandoutsGrid(result.standouts(), users, currentUser);

    // Show interaction menu
    logger.info(CliConstants.SECTION_LINE);
    logger.info("  Select [1-{}] to view profile, or [Enter] to go back", result.count());

    String choice = inputReader.readLine("\n  Your choice: ").trim();

    if (choice.isEmpty()) {
        return;
    }

    try {
        int index = Integer.parseInt(choice);
        if (index >= 1 && index <= result.count()) {
            Standout selected = result.standouts().get(index - 1);
            User standoutUser = users.get(selected.standoutUserId());
            if (standoutUser != null) {
                handleStandoutInteraction(selected, standoutUser, currentUser);
            }
        } else {
            logger.info(CliConstants.INVALID_SELECTION);
        }
    } catch (NumberFormatException e) {
        logger.info(CliConstants.INVALID_INPUT);
    }
}

/**
 * Display standouts in a compact grid format.
 */
private void displayStandoutsGrid(List<Standout> standouts, Map<UUID, User> users, User seeker) {
    for (int i = 0; i < standouts.size(); i++) {
        Standout s = standouts.get(i);
        User u = users.get(s.standoutUserId());

        if (u == null) continue;

        // Calculate distance
        double distanceKm = GeoUtils.distanceKm(
            seeker.getLat(), seeker.getLon(),
            u.getLat(), u.getLon());

        // Status indicator
        String status = s.hasInteracted() ? "✓" : " ";

        // Format: [1] ⭐⭐⭐ Alice, 28 · 3.2km · "Shared interests"
        String stars = "⭐".repeat(getStarRating(s.standoutScore()));

        logger.info("  [{}]{} {} {}, {} · {:.1f}km",
            s.rank(),
            status,
            stars,
            u.getName(),
            u.getAge(),
            distanceKm);
        logger.info("       💬 \"{}\"", s.reason());
        logger.info("");
    }
}

private int getStarRating(int score) {
    if (score >= 90) return 5;
    if (score >= 75) return 4;
    if (score >= 60) return 3;
    if (score >= 40) return 2;
    return 1;
}

/**
 * Handle interaction with a selected standout.
 */
private void handleStandoutInteraction(Standout standout, User standoutUser, User currentUser) {
    // Display full profile
    displayStandoutProfile(standout, standoutUser, currentUser);

    // Show action options
    logger.info(CliConstants.SECTION_LINE);

    // Check if already interacted
    if (standout.hasInteracted()) {
        logger.info("  ✓ You've already interacted with this standout today.");
        logger.info("  Press [Enter] to go back");
        inputReader.readLine("");
        return;
    }

    logger.info("  [L]ike  [P]ass  [B]ack");
    String action = inputReader.readLine("\n  Your action: ").toLowerCase().trim();

    switch (action) {
        case "l" -> handleStandoutLike(standout, standoutUser, currentUser);
        case "p" -> handleStandoutPass(standout, standoutUser, currentUser);
        case "b", "" -> { /* Go back */ }
        default -> logger.info(CliConstants.INVALID_INPUT);
    }
}

private void displayStandoutProfile(Standout standout, User user, User seeker) {
    double distanceKm = GeoUtils.distanceKm(
        seeker.getLat(), seeker.getLon(),
        user.getLat(), user.getLon());

    logger.info("\n");
    logger.info(CliConstants.SEPARATOR_LINE);
    logger.info("         🌟 STANDOUT PROFILE");
    logger.info(CliConstants.SEPARATOR_LINE);

    logger.info("\n");
    logger.info(CliConstants.BOX_TOP);
    logger.info("│ 🌟 {}, {} years old", user.getName(), user.getAge());
    logger.info("│ 📍 {:.1f} km away", distanceKm);
    logger.info("│");
    logger.info(CliConstants.PROFILE_BIO_FORMAT,
        user.getBio() != null && !user.getBio().isBlank() ? user.getBio() : "(no bio)");

    // Show interests
    if (!user.getInterests().isEmpty()) {
        InterestMatcher.MatchResult match = InterestMatcher.compare(
            seeker.getInterests(), user.getInterests());
        if (!match.shared().isEmpty()) {
            logger.info("│ ✨ Shared: {}", InterestMatcher.formatSharedInterests(match.shared()));
        }
    }

    logger.info(CliConstants.BOX_BOTTOM);

    // Show standout reason and score
    logger.info("\n  🏆 Standout Score: {}%", standout.standoutScore());
    logger.info("  💡 Why standout: \"{}\"", standout.reason());
}

private void handleStandoutLike(Standout standout, User standoutUser, User currentUser) {
    // Check daily limit
    if (!dailyLimitService.canLike(currentUser.getId())) {
        showDailyLimitReached(currentUser);
        return;
    }

    // Record like
    Like like = Like.create(currentUser.getId(), standoutUser.getId(), Like.Direction.LIKE);
    Match match = matchingService.recordLike(like);

    // Mark standout as interacted
    standoutsService.markInteracted(currentUser.getId(), standoutUser.getId());

    // Record for undo
    undoService.recordSwipe(currentUser.getId(), like, match);

    // Show result
    if (match != null) {
        logger.info("\n  🎉🎉🎉 IT'S A MATCH! 🎉🎉🎉");
        logger.info("  You and {} both like each other!", standoutUser.getName());

        // Check achievements
        checkAndDisplayNewAchievements(currentUser.getId());
    } else {
        logger.info("\n  ❤️ Liked {}!", standoutUser.getName());
    }

    // Prompt undo
    promptUndo(standoutUser.getName(), currentUser);
}

private void handleStandoutPass(Standout standout, User standoutUser, User currentUser) {
    // Record pass
    Like pass = Like.create(currentUser.getId(), standoutUser.getId(), Like.Direction.PASS);
    matchingService.recordLike(pass);

    // Mark standout as interacted
    standoutsService.markInteracted(currentUser.getId(), standoutUser.getId());

    // Record for undo
    undoService.recordSwipe(currentUser.getId(), pass, null);

    logger.info("\n  👋 Passed on {}", standoutUser.getName());

    // Prompt undo
    promptUndo(standoutUser.getName(), currentUser);
}
```

---

## 8. ServiceRegistry Integration

### 8.1 Update ServiceRegistry

**Location:** `src/main/java/datingapp/core/ServiceRegistry.java`

Add fields and getters:

```java
// Add fields
private final StandoutStorage standoutStorage;
private final StandoutRanker standoutRanker;
private final StandoutsService standoutsService;

// Add to constructor parameters and assignments

// Add getters
public StandoutStorage getStandoutStorage() { return standoutStorage; }
public StandoutRanker getStandoutRanker() { return standoutRanker; }
public StandoutsService getStandoutsService() { return standoutsService; }
```

### 8.2 Update ServiceRegistryBuilder

**Location:** `src/main/java/datingapp/core/ServiceRegistryBuilder.java`

```java
public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
    // ... existing storage initialization ...

    // Standout storage
    StandoutStorage standoutStorage = new H2StandoutStorage(dbManager);

    // ... existing service initialization ...

    // Standout services
    StandoutRanker standoutRanker = new StandoutRanker(
        profilePreviewService,
        MatchQualityConfig.defaults());

    StandoutsService standoutsService = new StandoutsService(
        userStorage,
        likeStorage,
        blockStorage,
        standoutStorage,
        candidateFinder,
        standoutRanker,
        config);

    return new ServiceRegistry(
        // ... existing params ...
        standoutStorage,
        standoutRanker,
        standoutsService
    );
}
```

---

## 9. Testing Strategy

### 9.1 Unit Tests

**Location:** `src/test/java/datingapp/core/`

| Test Class                  | Coverage                                           |
|-----------------------------|----------------------------------------------------|
| `StandoutTest.java`         | Record validation, factory methods, immutability   |
| `StandoutRankerTest.java`   | Scoring algorithm, ranking order, diversity filter |
| `StandoutsServiceTest.java` | Cache behavior, generation flow, edge cases        |

### 9.2 Key Test Scenarios

```java
@Nested
class StandoutRankerTest {

    @Test
    void ranksHighCompatibilityFirst() {
        // Given candidates with different compatibility scores
        // When ranked
        // Then highest compatibility is rank 1
    }

    @Test
    void respectsDiversityFilter() {
        // Given recent standout IDs
        // When generating new standouts
        // Then recent standouts are excluded
    }

    @Test
    void limitsToTenStandouts() {
        // Given 50 candidates
        // When ranked
        // Then exactly 10 returned
    }

    @Test
    void generatesAppropriateReasons() {
        // Given candidate with high interest overlap
        // When generating reason
        // Then reason mentions shared interests
    }
}

@Nested
class StandoutsServiceTest {

    @Test
    void returnsCachedStandouts() {
        // Given standouts already generated today
        // When getStandouts called again
        // Then returns cached (fromCache = true)
    }

    @Test
    void generatesNewOnNewDay() {
        // Given standouts from yesterday
        // When getStandouts called today
        // Then generates fresh standouts
    }

    @Test
    void handlesNoCandidates() {
        // Given no candidates pass filters
        // When getStandouts called
        // Then returns empty result with message
    }
}
```

### 9.3 In-Memory Test Storage

```java
private static class InMemoryStandoutStorage implements StandoutStorage {
    private final Map<String, List<Standout>> standouts = new HashMap<>();
    private final Set<String> views = new HashSet<>();

    private String key(UUID seekerId, LocalDate date) {
        return seekerId + ":" + date;
    }

    @Override
    public void saveStandouts(UUID seekerId, List<Standout> list, LocalDate date) {
        standouts.put(key(seekerId, date), new ArrayList<>(list));
    }

    @Override
    public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
        return standouts.getOrDefault(key(seekerId, date), List.of());
    }

    // ... other methods ...
}
```

---

## 10. Implementation Phases

### Phase 1: Core Infrastructure (Day 1-2)

- [ ] Create `Standout.java` record
- [ ] Create `StandoutResult.java` record
- [ ] Create `StandoutScore.java` record
- [ ] Create `StandoutStorage.java` interface
- [ ] Add database schema to `DatabaseManager`
- [ ] Implement `H2StandoutStorage`
- [ ] Write storage tests

### Phase 2: Ranking Engine (Day 2-3)

- [ ] Create `StandoutRanker.java`
- [ ] Implement `calculateScore()` with 5 factors
- [ ] Implement `rankCandidates()` with diversity filter
- [ ] Implement `generateReason()`
- [ ] Write ranker unit tests

### Phase 3: Service Layer (Day 3-4)

- [ ] Create `StandoutsService.java`
- [ ] Implement cache-or-generate flow
- [ ] Implement view tracking
- [ ] Implement interaction tracking
- [ ] Update `ServiceRegistry` and `ServiceRegistryBuilder`
- [ ] Write service tests

### Phase 4: CLI Integration (Day 4-5)

- [ ] Add menu option to `Main.java`
- [ ] Implement `MatchingHandler.viewStandouts()`
- [ ] Implement grid display
- [ ] Implement profile detail view
- [ ] Implement like/pass handling
- [ ] Integrate with daily limits and undo

### Phase 5: Testing & Polish (Day 5-6)

- [ ] End-to-end manual testing
- [ ] Edge case handling
- [ ] Performance validation
- [ ] Documentation update (CLAUDE.md)

---

## 11. Future Enhancements

### 11.1 Potential Improvements

| Enhancement           | Description                          | Complexity |
|-----------------------|--------------------------------------|------------|
| **Smart Caching**     | Pre-compute standouts overnight      | Medium     |
| **A/B Testing**       | Test different ranking weights       | Medium     |
| **Premium Standouts** | Show 20 standouts for premium users  | Low        |
| **Standout Boost**    | Let users pay to appear in standouts | High       |
| **ML Ranking**        | Use machine learning for scoring     | High       |
| **Notification**      | "Your standouts are ready!"          | Medium     |

### 11.2 Metrics to Track

- Standouts view rate (% of daily active users viewing standouts)
- Standout interaction rate (likes + passes / views)
- Standout match rate (matches from standouts / total matches)
- Cache hit rate (cached / total requests)
- Average standout score distribution

---

## 12. Risks & Mitigations

| Risk                                                      | Impact | Mitigation                                          |
|-----------------------------------------------------------|--------|-----------------------------------------------------|
| **Performance** - Scoring all candidates is slow          | High   | Cache aggressively, consider async generation       |
| **Cold Start** - New users have no history for mutual fit | Medium | Use default scores, weight other factors            |
| **Stale Data** - Cached standouts show inactive users     | Low    | Include activity score, re-generate if user reports |
| **Gaming** - Users try to appear in standouts             | Low    | Don't expose ranking algorithm details              |

---

## Appendix A: Configuration Constants

```java
// In AppConfig or new StandoutConfig
public static final int MAX_STANDOUTS = 10;
public static final int DIVERSITY_DAYS = 3;
public static final int CACHE_DAYS = 1;

// Scoring weights
public static final double W_COMPATIBILITY = 0.50;
public static final double W_PROFILE = 0.20;
public static final double W_INTERESTS = 0.15;
public static final double W_ACTIVITY = 0.10;
public static final double W_MUTUAL = 0.05;
```

---

## Appendix B: CLI Display Mockup

```
═══════════════════════════════════════
         🌟 YOUR STANDOUTS 🌟
═══════════════════════════════════════

  Today's top 10 matches for you:

  [1]  ⭐⭐⭐⭐⭐ Alice, 28 · 2.3km
       💬 "Highly compatible"

  [2]  ⭐⭐⭐⭐⭐ Emma, 26 · 4.1km
       💬 "Many shared interests"

  [3]  ⭐⭐⭐⭐ Jessica, 30 · 1.8km
       💬 "Lives nearby"

  [4]  ⭐⭐⭐⭐ Sarah, 27 · 5.2km
       💬 "Same relationship goals"

  [5]✓ ⭐⭐⭐⭐ Michelle, 29 · 3.7km
       💬 "Complete profile"

  ... (6-10 similar format)

  ───────────────────────────────────
  Select [1-10] to view profile, or [Enter] to go back

  Your choice: _
```

---

*Document generated: 2026-01-10*
*Author: Claude Code*
*Status: Ready for implementation*
