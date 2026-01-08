# Dating App Core Features PRD

## Phase 0.5: Strengthening the Core

**Version:** 1.0
**Date:** 2026-01-07
**Status:** Draft
**Prerequisite:** Phase 0 Complete (Console App with User, Like, Match)

---

## Executive Summary

Before adding external dependencies (Spring, JPA, REST), we strengthen the domain model with features essential to any production dating app. These features are **pure business logic** — no frameworks, no HTTP, no external services.

**Goal:** Build a robust, well-tested core that handles real-world dating app scenarios before framework complexity obscures bugs.

---

## Architectural Rules (Unchanged)

| Rule | Description |
|------|-------------|
| **Core Stays Pure** | Zero framework imports in `core/`. Only `java.*` packages. |
| **One Job Per Layer** | `core/` = business logic. `storage/` = persistence. |
| **Start Simple** | No premature abstractions. Add complexity when tests demand it. |

---

## Feature Overview

| Category | Feature | Priority | Complexity |
|----------|---------|----------|------------|
| Safety | Block | P0 | Low |
| Safety | Unmatch | P0 | Low |
| Safety | Report | P0 | Medium |
| Matching | Interests/Tags | P1 | Low |
| Matching | Match Scoring Strategies | P1 | Medium |
| Matching | Activity Recency | P1 | Low |
| Business Rules | Daily Swipe Limit | P1 | Low |
| Business Rules | Super Like | P2 | Low |
| Business Rules | "Who Liked Me" | P2 | Medium |
| Messaging | Conversations & Messages | P2 | High |

**Priority Key:**
- P0 = Safety-critical, must have
- P1 = Core experience improvement
- P2 = Nice to have, can defer

---

# Part 1: Safety & Control

## 1.1 Block

### Purpose
Allow users to permanently hide another user from their experience. Blocking is **bidirectional** — neither user sees the other, regardless of who initiated the block.

### Domain Model

```java
// core/Block.java
public record Block(
    UUID id,
    UUID blockerId,      // User who initiated the block
    UUID blockedId,      // User who got blocked
    Instant createdAt
) {
    public Block {
        Objects.requireNonNull(id);
        Objects.requireNonNull(blockerId);
        Objects.requireNonNull(blockedId);
        Objects.requireNonNull(createdAt);

        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }
    }

    public static Block create(UUID blockerId, UUID blockedId) {
        return new Block(
            UUID.randomUUID(),
            blockerId,
            blockedId,
            Instant.now()
        );
    }
}
```

### Storage Interface

```java
// core/BlockStorage.java
public interface BlockStorage {
    void save(Block block);

    /**
     * Returns true if EITHER user has blocked the other.
     * Block is bidirectional in effect.
     */
    boolean isBlocked(UUID userA, UUID userB);

    /**
     * Returns all user IDs that the given user should not see.
     * Includes users they blocked AND users who blocked them.
     */
    Set<UUID> getBlockedUserIds(UUID userId);
}
```

### Impact on Existing Code

**CandidateFinder.findCandidates()** must exclude blocked users:

```java
public List<User> findCandidates(User seeker) {
    Set<UUID> alreadyInteracted = likeStorage.getLikedOrPassedUserIds(seeker.getId());
    Set<UUID> blockedUsers = blockStorage.getBlockedUserIds(seeker.getId());  // NEW

    return userStorage.findActive().stream()
        .filter(candidate -> !candidate.getId().equals(seeker.getId()))
        .filter(candidate -> !alreadyInteracted.contains(candidate.getId()))
        .filter(candidate -> !blockedUsers.contains(candidate.getId()))  // NEW
        // ... rest of filters
        .toList();
}
```

### Business Rules

| Rule | Description |
|------|-------------|
| Block is permanent | No unblock feature (simplicity) |
| Block is bidirectional | If A blocks B, neither sees the other |
| Block removes existing match | If A and B were matched, blocking ends the match |
| Block prevents future interaction | Cannot like, message, or see blocked user |

### Console Menu Addition

```
───────────────────────────────────────
  6. Block a user
───────────────────────────────────────
```

Flow:
1. Show list of users (perhaps from matches or recent interactions)
2. Select user to block
3. Confirm action
4. Create block record
5. If match exists, mark it as ended (see Unmatch)

### Success Criteria

- [ ] Can block a user from console
- [ ] Blocked user does not appear in candidates
- [ ] User who blocked you does not appear in candidates
- [ ] Cannot block yourself (validation)
- [ ] Block persists between restarts

---

## 1.2 Unmatch

### Purpose
Allow either party in a match to end it. Once unmatched, users return to the candidate pool (unless blocked).

### Domain Model Changes

**Match gains state:**

```java
// core/Match.java (modified)
public class Match {

    public enum State {
        ACTIVE,      // Both users are matched
        UNMATCHED,   // One user ended the match
        BLOCKED      // One user blocked the other
    }

    private final String id;
    private final UUID userA;
    private final UUID userB;
    private final Instant createdAt;
    private State state;              // NEW
    private Instant endedAt;          // NEW (nullable)
    private UUID endedBy;             // NEW (nullable) - who ended it

    // Constructor creates with ACTIVE state
    public static Match create(UUID a, UUID b) {
        return new Match(
            generateId(a, b),
            smaller(a, b),
            larger(a, b),
            Instant.now(),
            State.ACTIVE,
            null,
            null
        );
    }

    public void unmatch(UUID userId) {
        if (this.state != State.ACTIVE) {
            throw new IllegalStateException("Match is not active");
        }
        if (!involves(userId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        this.state = State.UNMATCHED;
        this.endedAt = Instant.now();
        this.endedBy = userId;
    }

    public void block(UUID userId) {
        if (!involves(userId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        this.state = State.BLOCKED;
        this.endedAt = Instant.now();
        this.endedBy = userId;
    }

    public boolean isActive() {
        return this.state == State.ACTIVE;
    }
}
```

### Storage Interface Changes

```java
// core/MatchStorage.java (modified)
public interface MatchStorage {
    void save(Match match);
    void update(Match match);  // NEW - for state changes
    Optional<Match> get(String matchId);
    List<Match> getActiveMatchesFor(UUID userId);  // RENAMED - only active
    List<Match> getAllMatchesFor(UUID userId);     // NEW - includes ended
}
```

### Impact on Existing Code

**After unmatch, users can re-enter candidate pool:**

The Like record still exists (so they won't see each other immediately), but if we want to allow re-matching:

Option A: Delete the Like records when unmatching (allows re-discovery)
Option B: Keep Like records, add "reset interaction" feature later

**Recommendation:** Option B (keep it simple, Like history is valuable data)

### Business Rules

| Rule | Description |
|------|-------------|
| Either party can unmatch | No mutual consent required |
| Unmatch is one-way | Cannot "re-match" without new likes |
| Unmatch preserves history | Like records remain for analytics |
| Unmatched users don't see each other | Like records still exclude from candidates |

### Console Menu Addition

```
───────────────────────────────────────
  5. View my matches
     [Within match view]
     - (U)nmatch this person
     - (B)lock this person
───────────────────────────────────────
```

### Success Criteria

- [ ] Can unmatch from an active match
- [ ] Match state changes to UNMATCHED
- [ ] Unmatched user no longer appears in "View Matches"
- [ ] Cannot unmatch an already inactive match
- [ ] endedAt and endedBy are recorded

---

## 1.3 Report

### Purpose
Allow users to flag problematic behavior. Reports accumulate and can trigger automatic banning at a threshold.

### Domain Model

```java
// core/Report.java
public record Report(
    UUID id,
    UUID reporterId,       // Who filed the report
    UUID reportedUserId,   // Who is being reported
    Reason reason,
    String description,    // Optional free text (max 500 chars)
    Instant createdAt
) {
    public enum Reason {
        SPAM,
        INAPPROPRIATE_CONTENT,
        HARASSMENT,
        FAKE_PROFILE,
        UNDERAGE,
        OTHER
    }

    public Report {
        Objects.requireNonNull(id);
        Objects.requireNonNull(reporterId);
        Objects.requireNonNull(reportedUserId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(createdAt);

        if (reporterId.equals(reportedUserId)) {
            throw new IllegalArgumentException("Cannot report yourself");
        }
        if (description != null && description.length() > 500) {
            throw new IllegalArgumentException("Description too long (max 500)");
        }
    }

    public static Report create(UUID reporterId, UUID reportedUserId,
                                 Reason reason, String description) {
        return new Report(
            UUID.randomUUID(),
            reporterId,
            reportedUserId,
            reason,
            description,
            Instant.now()
        );
    }
}
```

### Storage Interface

```java
// core/ReportStorage.java
public interface ReportStorage {
    void save(Report report);

    /**
     * Count reports against a user.
     */
    int countReportsAgainst(UUID userId);

    /**
     * Check if reporter has already reported this user.
     */
    boolean hasReported(UUID reporterId, UUID reportedUserId);

    /**
     * Get all reports against a user (for admin review later).
     */
    List<Report> getReportsAgainst(UUID userId);
}
```

### Report Service (Business Logic)

```java
// core/ReportService.java
public class ReportService {

    private static final int AUTO_BAN_THRESHOLD = 3;

    private final ReportStorage reportStorage;
    private final UserStorage userStorage;
    private final BlockStorage blockStorage;

    public ReportService(ReportStorage reportStorage,
                         UserStorage userStorage,
                         BlockStorage blockStorage) {
        this.reportStorage = reportStorage;
        this.userStorage = userStorage;
        this.blockStorage = blockStorage;
    }

    /**
     * File a report against a user.
     * Returns true if the reported user was auto-banned.
     */
    public ReportResult report(UUID reporterId, UUID reportedUserId,
                               Report.Reason reason, String description) {

        // Validate reporter exists and is active
        User reporter = userStorage.get(reporterId);
        if (reporter == null || reporter.getState() != User.State.ACTIVE) {
            throw new IllegalStateException("Reporter must be active user");
        }

        // Validate reported user exists
        User reportedUser = userStorage.get(reportedUserId);
        if (reportedUser == null) {
            throw new IllegalArgumentException("Reported user not found");
        }

        // Check for duplicate report
        if (reportStorage.hasReported(reporterId, reportedUserId)) {
            return new ReportResult(false, false, "Already reported this user");
        }

        // Save report
        Report report = Report.create(reporterId, reportedUserId, reason, description);
        reportStorage.save(report);

        // Auto-block: reporter automatically blocks reported user
        if (!blockStorage.isBlocked(reporterId, reportedUserId)) {
            Block block = Block.create(reporterId, reportedUserId);
            blockStorage.save(block);
        }

        // Check for auto-ban threshold
        int reportCount = reportStorage.countReportsAgainst(reportedUserId);
        boolean autoBanned = false;

        if (reportCount >= AUTO_BAN_THRESHOLD &&
            reportedUser.getState() != User.State.BANNED) {
            reportedUser.ban();
            userStorage.save(reportedUser);
            autoBanned = true;
        }

        return new ReportResult(true, autoBanned, null);
    }

    public record ReportResult(
        boolean success,
        boolean userWasBanned,
        String errorMessage
    ) {}
}
```

### Business Rules

| Rule | Description |
|------|-------------|
| One report per pair | User A can only report User B once |
| Report triggers block | Reporting auto-blocks the reported user |
| Auto-ban at threshold | 3 unique reports → automatic ban |
| Banned users stay banned | No auto-unban (manual admin action later) |
| Anyone can be reported | Even if not matched |

### Console Menu Addition

```
───────────────────────────────────────
  7. Report a user
───────────────────────────────────────
```

Flow:
1. Enter user name or select from list
2. Select reason from enum
3. Optional: enter description
4. Confirm
5. Show result (including if user was auto-banned)

### Success Criteria

- [ ] Can report a user with reason
- [ ] Cannot report yourself
- [ ] Cannot report same user twice
- [ ] Reporting auto-blocks the reported user
- [ ] 3 reports from different users triggers auto-ban
- [ ] Banned user state is BANNED
- [ ] Reports persist between restarts

---

# Part 2: Matching Quality

## 2.1 Interests/Tags

### Purpose
Allow users to specify interests. Candidates can be filtered or scored by interest overlap.

### Domain Model Changes

```java
// core/Interest.java
public enum Interest {
    // Outdoor & Sports
    HIKING,
    CYCLING,
    RUNNING,
    SWIMMING,
    YOGA,
    GYM,
    CLIMBING,
    CAMPING,

    // Arts & Culture
    MUSIC,
    MOVIES,
    READING,
    PHOTOGRAPHY,
    ART,
    THEATER,
    COOKING,
    DANCING,

    // Social
    TRAVEL,
    FOODIE,
    WINE,
    COFFEE,
    NIGHTLIFE,
    BOARD_GAMES,
    VIDEO_GAMES,

    // Lifestyle
    PETS,
    VOLUNTEERING,
    SPIRITUALITY,
    POLITICS,
    SCIENCE,
    TECH
}
```

```java
// core/User.java (additions)
public class User {
    // ... existing fields ...

    private Set<Interest> interests;  // NEW - max 5

    public void setInterests(Set<Interest> interests) {
        if (interests != null && interests.size() > 5) {
            throw new IllegalArgumentException("Maximum 5 interests allowed");
        }
        this.interests = interests == null ? Set.of() : Set.copyOf(interests);
        this.updatedAt = Instant.now();
    }

    public Set<Interest> getInterests() {
        return interests;
    }
}
```

### Storage Changes

**Database schema addition:**

```sql
-- Add column to users table
ALTER TABLE users ADD COLUMN interests VARCHAR(200);  -- Comma-separated
```

**H2UserStorage changes:**
- Serialize: `interests.stream().map(Enum::name).collect(joining(","))`
- Deserialize: `Arrays.stream(str.split(",")).map(Interest::valueOf).collect(toSet())`

### Interest Overlap Calculation

```java
// core/InterestUtils.java
public final class InterestUtils {

    private InterestUtils() {}

    /**
     * Calculate Jaccard similarity between two interest sets.
     * Returns 0.0 if no interests, 1.0 if identical.
     */
    public static double overlapScore(Set<Interest> a, Set<Interest> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        Set<Interest> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<Interest> union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / union.size();
    }

    /**
     * Get shared interests between two users.
     */
    public static Set<Interest> sharedInterests(Set<Interest> a, Set<Interest> b) {
        if (a == null || b == null) {
            return Set.of();
        }
        Set<Interest> shared = new HashSet<>(a);
        shared.retainAll(b);
        return Set.copyOf(shared);
    }
}
```

### Console Menu Addition

Profile completion now includes interests:

```
Select your interests (max 5, comma-separated numbers):
1. HIKING        2. CYCLING       3. RUNNING
4. SWIMMING      5. YOGA          6. GYM
... [show all]

Enter choices (e.g., 1,5,12):
```

### Success Criteria

- [ ] Can set up to 5 interests on profile
- [ ] Cannot set more than 5 interests
- [ ] Interests persist between restarts
- [ ] InterestUtils correctly calculates overlap score
- [ ] Shared interests can be displayed when viewing a candidate

---

## 2.2 Match Scoring Strategies

### Purpose
Replace simple filtering with a **scoring system**. Candidates are ranked by composite score from multiple strategies.

### Design: Strategy Pattern

```java
// core/scoring/MatchStrategy.java
public interface MatchStrategy {

    /**
     * Calculate a score for how well the candidate matches the seeker.
     *
     * @return score between 0.0 (no match) and 1.0 (perfect match)
     *         Return -1.0 to indicate "hard filter" (exclude candidate)
     */
    double score(User candidate, User seeker);

    /**
     * Human-readable name for logging/debugging.
     */
    String name();
}
```

### Concrete Strategies

```java
// core/scoring/DistanceStrategy.java
public class DistanceStrategy implements MatchStrategy {

    @Override
    public double score(User candidate, User seeker) {
        double distance = GeoUtils.distanceKm(
            seeker.getLat(), seeker.getLon(),
            candidate.getLat(), candidate.getLon()
        );

        // Hard filter: outside seeker's max distance
        if (distance > seeker.getMaxDistanceKm()) {
            return -1.0;
        }

        // Score: closer = higher (linear decay)
        return 1.0 - (distance / seeker.getMaxDistanceKm());
    }

    @Override
    public String name() {
        return "distance";
    }
}
```

```java
// core/scoring/GenderPreferenceStrategy.java
public class GenderPreferenceStrategy implements MatchStrategy {

    @Override
    public double score(User candidate, User seeker) {
        // Hard filter: must have mutual gender preferences
        boolean seekerInterestedInCandidate =
            seeker.getInterestedIn().contains(candidate.getGender());
        boolean candidateInterestedInSeeker =
            candidate.getInterestedIn().contains(seeker.getGender());

        if (!seekerInterestedInCandidate || !candidateInterestedInSeeker) {
            return -1.0;
        }

        return 1.0;  // Pass = full score (no gradation for gender)
    }

    @Override
    public String name() {
        return "gender_preference";
    }
}
```

```java
// core/scoring/AgePreferenceStrategy.java
public class AgePreferenceStrategy implements MatchStrategy {

    @Override
    public double score(User candidate, User seeker) {
        int candidateAge = candidate.getAge();
        int seekerAge = seeker.getAge();

        // Hard filter: mutual age preferences
        boolean candidateInSeekerRange =
            candidateAge >= seeker.getMinAge() && candidateAge <= seeker.getMaxAge();
        boolean seekerInCandidateRange =
            seekerAge >= candidate.getMinAge() && seekerAge <= candidate.getMaxAge();

        if (!candidateInSeekerRange || !seekerInCandidateRange) {
            return -1.0;
        }

        return 1.0;  // Pass = full score
    }

    @Override
    public String name() {
        return "age_preference";
    }
}
```

```java
// core/scoring/InterestOverlapStrategy.java
public class InterestOverlapStrategy implements MatchStrategy {

    @Override
    public double score(User candidate, User seeker) {
        // No hard filter - just scoring
        return InterestUtils.overlapScore(
            seeker.getInterests(),
            candidate.getInterests()
        );
    }

    @Override
    public String name() {
        return "interest_overlap";
    }
}
```

```java
// core/scoring/ActivityRecencyStrategy.java
public class ActivityRecencyStrategy implements MatchStrategy {

    private static final int ACTIVE_DAYS_THRESHOLD = 7;

    @Override
    public double score(User candidate, User seeker) {
        Instant lastActive = candidate.getLastActiveAt();
        if (lastActive == null) {
            return 0.5;  // Unknown = neutral score
        }

        long daysSinceActive = ChronoUnit.DAYS.between(
            lastActive, Instant.now()
        );

        if (daysSinceActive <= 1) {
            return 1.0;   // Active today/yesterday
        } else if (daysSinceActive <= ACTIVE_DAYS_THRESHOLD) {
            // Linear decay over the week
            return 1.0 - ((double) daysSinceActive / ACTIVE_DAYS_THRESHOLD);
        } else {
            return 0.1;   // Stale but not excluded
        }
    }

    @Override
    public String name() {
        return "activity_recency";
    }
}
```

### Composite Scorer

```java
// core/scoring/MatchScorer.java
public class MatchScorer {

    private final List<WeightedStrategy> strategies;

    public MatchScorer(List<WeightedStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public record WeightedStrategy(MatchStrategy strategy, double weight) {
        public WeightedStrategy {
            if (weight < 0 || weight > 1) {
                throw new IllegalArgumentException("Weight must be 0-1");
            }
        }
    }

    public record ScoredCandidate(
        User user,
        double totalScore,
        Map<String, Double> breakdown  // strategy name -> score
    ) implements Comparable<ScoredCandidate> {

        @Override
        public int compareTo(ScoredCandidate other) {
            return Double.compare(other.totalScore, this.totalScore);  // Descending
        }
    }

    /**
     * Score a candidate against a seeker.
     * Returns null if any strategy returns -1.0 (hard filter).
     */
    public ScoredCandidate score(User candidate, User seeker) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        double weightedSum = 0;
        double totalWeight = 0;

        for (WeightedStrategy ws : strategies) {
            double score = ws.strategy().score(candidate, seeker);

            // Hard filter: exclude candidate
            if (score < 0) {
                return null;
            }

            breakdown.put(ws.strategy().name(), score);
            weightedSum += score * ws.weight();
            totalWeight += ws.weight();
        }

        double totalScore = totalWeight > 0 ? weightedSum / totalWeight : 0;

        return new ScoredCandidate(candidate, totalScore, breakdown);
    }
}
```

### Updated CandidateFinder

```java
// core/CandidateFinder.java (refactored)
public class CandidateFinder {

    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final BlockStorage blockStorage;
    private final MatchScorer scorer;

    public CandidateFinder(UserStorage userStorage,
                           LikeStorage likeStorage,
                           BlockStorage blockStorage,
                           MatchScorer scorer) {
        this.userStorage = userStorage;
        this.likeStorage = likeStorage;
        this.blockStorage = blockStorage;
        this.scorer = scorer;
    }

    /**
     * Find and rank candidates for the seeker.
     */
    public List<ScoredCandidate> findCandidates(User seeker, int limit) {
        Set<UUID> excluded = new HashSet<>();
        excluded.add(seeker.getId());
        excluded.addAll(likeStorage.getLikedOrPassedUserIds(seeker.getId()));
        excluded.addAll(blockStorage.getBlockedUserIds(seeker.getId()));

        return userStorage.findActive().stream()
            .filter(candidate -> !excluded.contains(candidate.getId()))
            .map(candidate -> scorer.score(candidate, seeker))
            .filter(Objects::nonNull)  // Remove hard-filtered
            .sorted()  // By score descending
            .limit(limit)
            .toList();
    }
}
```

### Default Configuration

```java
// core/scoring/DefaultScorerFactory.java
public final class DefaultScorerFactory {

    private DefaultScorerFactory() {}

    public static MatchScorer create() {
        return new MatchScorer(List.of(
            // Hard filters (weight doesn't matter, they return -1 or 1)
            new WeightedStrategy(new GenderPreferenceStrategy(), 0.0),
            new WeightedStrategy(new AgePreferenceStrategy(), 0.0),

            // Soft scoring
            new WeightedStrategy(new DistanceStrategy(), 0.4),
            new WeightedStrategy(new InterestOverlapStrategy(), 0.35),
            new WeightedStrategy(new ActivityRecencyStrategy(), 0.25)
        ));
    }
}
```

### Success Criteria

- [ ] Strategies correctly return -1.0 for hard filters
- [ ] MatchScorer excludes candidates that fail hard filters
- [ ] Candidates are sorted by composite score (highest first)
- [ ] Score breakdown is available for debugging
- [ ] Adding a new strategy requires only implementing interface

---

## 2.3 Activity Recency

### Purpose
Track when users were last active. Prioritize active users in candidate ranking.

### Domain Model Changes

```java
// core/User.java (additions)
public class User {
    // ... existing fields ...

    private Instant lastActiveAt;  // NEW

    public void markActive() {
        this.lastActiveAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }
}
```

### When to Update

Activity is marked when user performs meaningful actions:
- Opens the app (selects user in console)
- Completes a swipe (like/pass)
- Sends a message (Phase 0.5+)

```java
// In Main.java or relevant service
user.markActive();
userStorage.save(user);
```

### Storage Changes

```sql
ALTER TABLE users ADD COLUMN last_active_at TIMESTAMP;
```

### Success Criteria

- [ ] lastActiveAt is set when user performs actions
- [ ] lastActiveAt persists between restarts
- [ ] ActivityRecencyStrategy scores recent users higher
- [ ] Users inactive for 7+ days get low scores (but not excluded)

---

# Part 3: Business Rules

## 3.1 Daily Swipe Limit

### Purpose
Limit the number of likes per day to prevent spam behavior and encourage thoughtful swiping.

### Configuration

```java
// core/SwipeLimitConfig.java
public record SwipeLimitConfig(
    int dailyLikeLimit,      // Max LIKE swipes per day (not passes)
    int dailySuperLikeLimit  // Max SUPER_LIKE per day
) {
    public static SwipeLimitConfig defaults() {
        return new SwipeLimitConfig(100, 1);
    }
}
```

### Storage Interface Changes

```java
// core/LikeStorage.java (additions)
public interface LikeStorage {
    // ... existing methods ...

    /**
     * Count likes (not passes) made by user today.
     */
    int countLikesToday(UUID userId);

    /**
     * Count super likes made by user today.
     */
    int countSuperLikesToday(UUID userId);
}
```

### Business Logic

```java
// core/MatchingService.java (updated)
public class MatchingService {

    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final SwipeLimitConfig limitConfig;

    public LikeResult recordLike(UUID swiperId, UUID targetId, Like.Direction direction) {

        // Check daily limits for LIKE and SUPER_LIKE
        if (direction == Like.Direction.LIKE) {
            int todayCount = likeStorage.countLikesToday(swiperId);
            if (todayCount >= limitConfig.dailyLikeLimit()) {
                return LikeResult.limitReached("Daily like limit reached");
            }
        } else if (direction == Like.Direction.SUPER_LIKE) {
            int todayCount = likeStorage.countSuperLikesToday(swiperId);
            if (todayCount >= limitConfig.dailySuperLikeLimit()) {
                return LikeResult.limitReached("Daily super like limit reached");
            }
        }

        // ... rest of existing logic ...
    }

    public record LikeResult(
        boolean success,
        boolean matched,
        Match match,
        String errorMessage
    ) {
        public static LikeResult limitReached(String message) {
            return new LikeResult(false, false, null, message);
        }

        public static LikeResult success(boolean matched, Match match) {
            return new LikeResult(true, matched, match, null);
        }
    }
}
```

### SQL for Today Count

```sql
SELECT COUNT(*) FROM likes
WHERE who_likes = ?
  AND direction = 'LIKE'
  AND DATE(created_at) = CURRENT_DATE;
```

### Console Feedback

```
You have used 47/100 likes today.
[L]ike  [P]ass  [S]uper Like (0/1 remaining)  [Q]uit
```

### Success Criteria

- [ ] Cannot exceed daily like limit
- [ ] Cannot exceed daily super like limit
- [ ] Limits reset at midnight
- [ ] PASS does not count against limit
- [ ] User sees remaining swipes
- [ ] Clear error message when limit reached

---

## 3.2 Super Like

### Purpose
Special like type that signals stronger interest. Limited to 1 per day.

### Domain Model Changes

```java
// core/Like.java (updated)
public record Like(
    UUID id,
    UUID whoLikes,
    UUID whoGotLiked,
    Direction direction,
    Instant createdAt
) {
    public enum Direction {
        LIKE,
        PASS,
        SUPER_LIKE  // NEW
    }

    public boolean isPositive() {
        return direction == Direction.LIKE || direction == Direction.SUPER_LIKE;
    }

    public boolean isSuperLike() {
        return direction == Direction.SUPER_LIKE;
    }
}
```

### Match Detection Update

Super like counts as a positive interaction for matching:

```java
// In MatchingService
public boolean mutualLikeExists(UUID a, UUID b) {
    // A liked/super-liked B AND B liked/super-liked A
    return likeStorage.hasPositiveLike(a, b) && likeStorage.hasPositiveLike(b, a);
}
```

```java
// core/LikeStorage.java (additions)
public interface LikeStorage {
    /**
     * Returns true if 'from' has LIKED or SUPER_LIKED 'to'.
     */
    boolean hasPositiveLike(UUID from, UUID to);
}
```

### Console Changes

```
───────────────────────────────────────
👤 Alice, 25  📍 3km away
   Interests: HIKING, YOGA, TRAVEL
───────────────────────────────────────
[L]ike  [P]ass  [S]uper Like (1 remaining)  [Q]uit
> s

⭐ Super Liked Alice!
```

### Success Criteria

- [ ] Super Like creates Like with SUPER_LIKE direction
- [ ] Super Like limited to 1 per day
- [ ] Super Like counts toward mutual matching
- [ ] Super Like is distinguishable in storage

---

## 3.3 "Who Liked Me"

### Purpose
Show users who have liked them but they haven't seen yet. High-value prospects since match is guaranteed if they like back.

### Storage Interface

```java
// core/LikeStorage.java (additions)
public interface LikeStorage {
    /**
     * Find users who liked/super-liked this user,
     * but this user hasn't swiped on them yet.
     */
    List<UUID> getPendingLikers(UUID userId);
}
```

### SQL Query

```sql
SELECT l.who_likes
FROM likes l
WHERE l.who_got_liked = ?
  AND l.direction IN ('LIKE', 'SUPER_LIKE')
  AND NOT EXISTS (
      SELECT 1 FROM likes l2
      WHERE l2.who_likes = ?
        AND l2.who_got_liked = l.who_likes
  );
```

### Service Method

```java
// core/WhoLikedMeService.java
public class WhoLikedMeService {

    private final LikeStorage likeStorage;
    private final UserStorage userStorage;
    private final BlockStorage blockStorage;

    public List<User> getPendingLikers(UUID userId) {
        Set<UUID> blockedUsers = blockStorage.getBlockedUserIds(userId);

        return likeStorage.getPendingLikers(userId).stream()
            .filter(likerId -> !blockedUsers.contains(likerId))
            .map(userStorage::get)
            .filter(Objects::nonNull)
            .filter(user -> user.getState() == User.State.ACTIVE)
            .toList();
    }
}
```

### Console Menu Addition

```
───────────────────────────────────────
  8. See who liked me (3 pending)
───────────────────────────────────────
```

Flow:
1. Show count of pending likers
2. User selects to view
3. Show list with option to like back (instant match) or pass

### Success Criteria

- [ ] Shows users who liked me that I haven't responded to
- [ ] Excludes blocked users
- [ ] Excludes inactive/banned users
- [ ] Liking back creates instant match
- [ ] Count shown in menu

---

# Part 4: Messaging

## 4.1 Conversation & Messages

### Purpose
Allow matched users to communicate. Messages are immutable after creation.

### Domain Models

```java
// core/Conversation.java
public class Conversation {

    private final String id;         // Deterministic like MatchId
    private final UUID userA;        // Lexicographically smaller
    private final UUID userB;        // Lexicographically larger
    private final Instant createdAt;
    private Instant lastMessageAt;   // Updated when message added

    public static Conversation create(UUID a, UUID b) {
        UUID smaller = a.compareTo(b) < 0 ? a : b;
        UUID larger = a.compareTo(b) < 0 ? b : a;
        String id = smaller.toString() + "_" + larger.toString();

        return new Conversation(id, smaller, larger, Instant.now(), null);
    }

    public static String generateId(UUID a, UUID b) {
        UUID smaller = a.compareTo(b) < 0 ? a : b;
        UUID larger = a.compareTo(b) < 0 ? b : a;
        return smaller.toString() + "_" + larger.toString();
    }

    public void updateLastMessageAt(Instant timestamp) {
        this.lastMessageAt = timestamp;
    }

    public boolean involves(UUID userId) {
        return userA.equals(userId) || userB.equals(userId);
    }

    public UUID getOtherUser(UUID userId) {
        if (userA.equals(userId)) return userB;
        if (userB.equals(userId)) return userA;
        throw new IllegalArgumentException("User not in conversation");
    }
}
```

```java
// core/Message.java
public record Message(
    UUID id,
    String conversationId,
    UUID senderId,
    String content,
    Instant createdAt
) {
    private static final int MAX_LENGTH = 1000;

    public Message {
        Objects.requireNonNull(id);
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(senderId);
        Objects.requireNonNull(content);
        Objects.requireNonNull(createdAt);

        if (content.isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        if (content.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Message too long (max " + MAX_LENGTH + ")");
        }
    }

    public static Message create(String conversationId, UUID senderId, String content) {
        return new Message(
            UUID.randomUUID(),
            conversationId,
            senderId,
            content.trim(),
            Instant.now()
        );
    }
}
```

### Storage Interfaces

```java
// core/ConversationStorage.java
public interface ConversationStorage {
    void save(Conversation conversation);
    Optional<Conversation> get(String conversationId);
    Optional<Conversation> getByUsers(UUID userA, UUID userB);
    List<Conversation> getConversationsFor(UUID userId);
    void updateLastMessageAt(String conversationId, Instant timestamp);
}
```

```java
// core/MessageStorage.java
public interface MessageStorage {
    void save(Message message);

    /**
     * Get messages for a conversation, ordered by createdAt ascending.
     */
    List<Message> getMessages(String conversationId, int limit, int offset);

    /**
     * Get latest message in a conversation (for preview).
     */
    Optional<Message> getLatestMessage(String conversationId);

    /**
     * Count unread messages (messages after user's last read timestamp).
     * For Phase 0.5, we can skip read tracking and count all.
     */
    int countMessages(String conversationId);
}
```

### Messaging Service

```java
// core/MessagingService.java
public class MessagingService {

    private final ConversationStorage conversationStorage;
    private final MessageStorage messageStorage;
    private final MatchStorage matchStorage;

    /**
     * Send a message between matched users.
     * Creates conversation if it doesn't exist.
     */
    public SendResult sendMessage(UUID senderId, UUID recipientId, String content) {

        // Verify match exists and is active
        String matchId = Match.generateId(senderId, recipientId);
        Optional<Match> match = matchStorage.get(matchId);

        if (match.isEmpty() || !match.get().isActive()) {
            return SendResult.failure("Cannot message: no active match");
        }

        // Get or create conversation
        String conversationId = Conversation.generateId(senderId, recipientId);
        Conversation conversation = conversationStorage.get(conversationId)
            .orElseGet(() -> {
                Conversation newConvo = Conversation.create(senderId, recipientId);
                conversationStorage.save(newConvo);
                return newConvo;
            });

        // Create and save message
        Message message = Message.create(conversationId, senderId, content);
        messageStorage.save(message);

        // Update conversation's last message timestamp
        conversationStorage.updateLastMessageAt(conversationId, message.createdAt());

        return SendResult.success(message);
    }

    public List<Message> getMessages(UUID userId, UUID otherUserId, int limit) {
        String conversationId = Conversation.generateId(userId, otherUserId);
        return messageStorage.getMessages(conversationId, limit, 0);
    }

    public record SendResult(boolean success, Message message, String error) {
        public static SendResult success(Message message) {
            return new SendResult(true, message, null);
        }
        public static SendResult failure(String error) {
            return new SendResult(false, null, error);
        }
    }
}
```

### Database Schema

```sql
CREATE TABLE conversations (
    id VARCHAR(100) PRIMARY KEY,
    user_a UUID NOT NULL REFERENCES users(id),
    user_b UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    last_message_at TIMESTAMP,
    UNIQUE (user_a, user_b)
);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL REFERENCES conversations(id),
    sender_id UUID NOT NULL REFERENCES users(id),
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at);
```

### Console Menu Addition

```
───────────────────────────────────────
  9. My conversations
───────────────────────────────────────
```

Flow:
1. List conversations with last message preview
2. Select conversation to view messages
3. Option to send new message
4. Messages displayed chronologically

```
═══════════════════════════════════════
  Conversation with Bob
═══════════════════════════════════════
[2026-01-07 10:30] Bob: Hey! How's it going?
[2026-01-07 10:32] You: Pretty good! I saw you like hiking too
[2026-01-07 10:35] Bob: Yeah! Have you been to Mt. Rainier?
───────────────────────────────────────
Type message (or /back to return):
```

### Business Rules

| Rule | Description |
|------|-------------|
| Only matched users can message | Check match exists and is ACTIVE |
| Messages are immutable | No edit, no delete |
| Max 1000 characters | Enforced in Message constructor |
| Empty messages rejected | Whitespace-only rejected |
| Conversation auto-created | First message creates conversation |

### Success Criteria

- [ ] Can send message to matched user
- [ ] Cannot send message to unmatched user
- [ ] Cannot send message if match is UNMATCHED/BLOCKED
- [ ] Messages persist between restarts
- [ ] Messages displayed in chronological order
- [ ] Conversation list shows last message preview
- [ ] Empty/too-long messages rejected

---

# Part 5: Implementation Order

## Recommended Sequence

```
Phase 0.5a: Safety (1-2 days)
├── 1. Block
├── 2. Unmatch (requires Match state change)
└── 3. Report

Phase 0.5b: Matching Quality (2-3 days)
├── 4. Interests/Tags
├── 5. Activity Recency (simple field)
└── 6. Match Scoring Strategies (refactor CandidateFinder)

Phase 0.5c: Business Rules (1-2 days)
├── 7. Super Like (enum addition)
├── 8. Daily Swipe Limits
└── 9. "Who Liked Me"

Phase 0.5d: Messaging (2-3 days)
├── 10. Conversation model + storage
├── 11. Message model + storage
└── 12. MessagingService + console UI
```

**Total estimate: 6-10 days**

## Dependencies

```
Block ──────────────┬──> CandidateFinder (excludes blocked)
                    └──> Report (auto-blocks)

Unmatch ────────────┬──> Match.State
                    └──> Block (can block from match)

Interests ──────────┬──> User
                    └──> InterestOverlapStrategy

Activity Recency ───┬──> User.lastActiveAt
                    └──> ActivityRecencyStrategy

Strategies ─────────┬──> CandidateFinder (refactored)
                    ├──> DistanceStrategy
                    ├──> GenderPreferenceStrategy
                    ├──> AgePreferenceStrategy
                    ├──> InterestOverlapStrategy
                    └──> ActivityRecencyStrategy

Super Like ─────────┬──> Like.Direction
                    └──> LikeStorage (counts)

Daily Limits ───────┬──> LikeStorage (counts)
                    └──> MatchingService (enforcement)

Who Liked Me ───────┬──> LikeStorage (pending likers)
                    └──> WhoLikedMeService

Messaging ──────────┬──> Match (must be active)
                    ├──> Conversation
                    ├──> Message
                    └──> MessagingService
```

---

# Part 6: Updated Console Menu

```
═══════════════════════════════════════
         DATING APP - PHASE 0.5
═══════════════════════════════════════
  Current User: Alice (ACTIVE)
  📍 San Francisco | 47/100 likes today
───────────────────────────────────────
  1. Create new user
  2. Select existing user
  3. Complete/edit my profile
  4. Browse candidates
  5. View my matches
  6. 💬 My conversations
  7. ❤️  Who liked me (3)
  8. 🚫 Block a user
  9. ⚠️  Report a user
  0. Exit
═══════════════════════════════════════
```

---

# Part 7: Test Plan

## Unit Tests Required

### Safety
| Test | Description |
|------|-------------|
| `BlockTest.cannotBlockSelf` | Throws on self-block |
| `BlockTest.blockIsBidirectional` | isBlocked(A,B) == isBlocked(B,A) |
| `CandidateFinderTest.excludesBlockedUsers` | Blocked users not in results |
| `UnmatchTest.stateTransition` | ACTIVE → UNMATCHED |
| `ReportTest.cannotReportSelf` | Throws on self-report |
| `ReportServiceTest.autoBanAtThreshold` | 3 reports → BANNED |

### Matching Quality
| Test | Description |
|------|-------------|
| `InterestUtilsTest.overlapScore` | Jaccard calculation correct |
| `DistanceStrategyTest.hardFilter` | Returns -1 when too far |
| `MatchScorerTest.excludesOnHardFilter` | Null result when any strategy returns -1 |
| `MatchScorerTest.sortsDescending` | Higher scores first |

### Business Rules
| Test | Description |
|------|-------------|
| `MatchingServiceTest.respectsDailyLimit` | Rejects when limit reached |
| `MatchingServiceTest.superLikeLimit` | Max 1 per day |
| `WhoLikedMeTest.findsPendingLikers` | Correct pending list |

### Messaging
| Test | Description |
|------|-------------|
| `MessageTest.rejectsEmpty` | Blank content throws |
| `MessageTest.rejectsTooLong` | >1000 chars throws |
| `MessagingServiceTest.requiresActiveMatch` | No match → failure |
| `ConversationTest.deterministicId` | Same ID regardless of user order |

---

# Part 8: Success Criteria Summary

## Must Have (P0)
- [ ] Block feature complete and tested
- [ ] Unmatch feature complete and tested
- [ ] Report feature with auto-ban complete and tested
- [ ] All safety features exclude users from candidates

## Should Have (P1)
- [ ] Interests on profile (max 5)
- [ ] Strategy-based scoring replaces simple filtering
- [ ] Activity recency tracking and scoring
- [ ] Daily swipe limits enforced

## Nice to Have (P2)
- [ ] Super Like with 1/day limit
- [ ] "Who Liked Me" feature
- [ ] Messaging between matched users

## Definition of Done
- [ ] All unit tests pass
- [ ] Console UI exercises all features
- [ ] Data persists between restarts
- [ ] Zero framework imports in `core/`
- [ ] Code follows existing patterns

---

# Appendix A: Database Schema (Complete)

```sql
-- Users (extended)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    bio VARCHAR(500),
    birth_date DATE,
    gender VARCHAR(20),
    interested_in VARCHAR(100),
    lat DOUBLE,
    lon DOUBLE,
    max_distance_km INT DEFAULT 50,
    min_age INT DEFAULT 18,
    max_age INT DEFAULT 99,
    photo_urls VARCHAR(1000),
    interests VARCHAR(200),              -- NEW
    state VARCHAR(20) NOT NULL DEFAULT 'INCOMPLETE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_active_at TIMESTAMP             -- NEW
);

-- Likes (extended)
CREATE TABLE likes (
    id UUID PRIMARY KEY,
    who_likes UUID NOT NULL REFERENCES users(id),
    who_got_liked UUID NOT NULL REFERENCES users(id),
    direction VARCHAR(20) NOT NULL,      -- LIKE, PASS, SUPER_LIKE
    created_at TIMESTAMP NOT NULL,
    UNIQUE (who_likes, who_got_liked)
);

-- Matches (extended)
CREATE TABLE matches (
    id VARCHAR(100) PRIMARY KEY,
    user_a UUID NOT NULL REFERENCES users(id),
    user_b UUID NOT NULL REFERENCES users(id),
    state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- NEW
    created_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,                           -- NEW
    ended_by UUID REFERENCES users(id),           -- NEW
    UNIQUE (user_a, user_b)
);

-- Blocks (NEW)
CREATE TABLE blocks (
    id UUID PRIMARY KEY,
    blocker_id UUID NOT NULL REFERENCES users(id),
    blocked_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    UNIQUE (blocker_id, blocked_id)
);

-- Reports (NEW)
CREATE TABLE reports (
    id UUID PRIMARY KEY,
    reporter_id UUID NOT NULL REFERENCES users(id),
    reported_user_id UUID NOT NULL REFERENCES users(id),
    reason VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    UNIQUE (reporter_id, reported_user_id)
);

-- Conversations (NEW)
CREATE TABLE conversations (
    id VARCHAR(100) PRIMARY KEY,
    user_a UUID NOT NULL REFERENCES users(id),
    user_b UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    last_message_at TIMESTAMP,
    UNIQUE (user_a, user_b)
);

-- Messages (NEW)
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL REFERENCES conversations(id),
    sender_id UUID NOT NULL REFERENCES users(id),
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Indexes
CREATE INDEX idx_likes_who_likes ON likes(who_likes);
CREATE INDEX idx_likes_created_date ON likes(who_likes, DATE(created_at));
CREATE INDEX idx_matches_user_a ON matches(user_a);
CREATE INDEX idx_matches_user_b ON matches(user_b);
CREATE INDEX idx_blocks_blocker ON blocks(blocker_id);
CREATE INDEX idx_blocks_blocked ON blocks(blocked_id);
CREATE INDEX idx_reports_reported ON reports(reported_user_id);
CREATE INDEX idx_conversations_user_a ON conversations(user_a);
CREATE INDEX idx_conversations_user_b ON conversations(user_b);
CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at);
```

---

# Appendix B: Package Structure (After Phase 0.5)

```
datingapp/
├── core/
│   ├── User.java
│   ├── Like.java
│   ├── Match.java
│   ├── Block.java                    // NEW
│   ├── Report.java                   // NEW
│   ├── Conversation.java             // NEW
│   ├── Message.java                  // NEW
│   ├── Interest.java                 // NEW (enum)
│   ├── UserStorage.java
│   ├── LikeStorage.java
│   ├── MatchStorage.java
│   ├── BlockStorage.java             // NEW
│   ├── ReportStorage.java            // NEW
│   ├── ConversationStorage.java      // NEW
│   ├── MessageStorage.java           // NEW
│   ├── CandidateFinder.java
│   ├── MatchingService.java
│   ├── ReportService.java            // NEW
│   ├── WhoLikedMeService.java        // NEW
│   ├── MessagingService.java         // NEW
│   ├── GeoUtils.java
│   ├── InterestUtils.java            // NEW
│   ├── SwipeLimitConfig.java         // NEW
│   └── scoring/                      // NEW package
│       ├── MatchStrategy.java
│       ├── MatchScorer.java
│       ├── DistanceStrategy.java
│       ├── GenderPreferenceStrategy.java
│       ├── AgePreferenceStrategy.java
│       ├── InterestOverlapStrategy.java
│       ├── ActivityRecencyStrategy.java
│       └── DefaultScorerFactory.java
├── storage/
│   ├── DatabaseManager.java
│   ├── StorageException.java
│   ├── H2UserStorage.java
│   ├── H2LikeStorage.java
│   ├── H2MatchStorage.java
│   ├── H2BlockStorage.java           // NEW
│   ├── H2ReportStorage.java          // NEW
│   ├── H2ConversationStorage.java    // NEW
│   └── H2MessageStorage.java         // NEW
└── Main.java
```

---

**End of PRD**