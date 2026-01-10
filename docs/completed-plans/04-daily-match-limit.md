# Feature 4: Daily Match Limit

**Status:** ✅ COMPLETE (Implemented 2026-01-08)
**Priority:** Medium
**Complexity:** Low
**Dependencies:** None

---

## Overview

Implement configurable daily limits on likes and matches to:
- Encourage thoughtful, quality swiping over rapid-fire behavior
- Prevent spam/bot-like behavior
- Create scarcity that increases perceived value of likes

---

## User Stories

1. As a user, I want to see how many likes I have remaining today
2. As a user, I should be notified when I've hit my daily limit
3. As a user, I want my limits to reset at midnight

---

## Proposed Changes

### Core Layer

#### [NEW] `DailyLimitService.java`
Service to track and enforce daily limits.

```java
public class DailyLimitService {
    private final LikeStorage likeStorage;
    private final AppConfig config;

    public record DailyStatus(
        int likesUsed,
        int likesRemaining,
        int passesUsed,      // passes may be unlimited
        LocalDate date,
        Instant resetsAt
    ) {}

    public DailyStatus getStatus(UUID userId);
    public boolean canLike(UUID userId);
    public boolean canPass(UUID userId);  // Usually unlimited
    public Duration getTimeUntilReset();
}
```

#### [MODIFY] `AppConfig.java`
Add daily limit configuration.

```java
// Add fields with defaults
private int dailyLikeLimit = 100;          // Likes per day
private int dailyPassLimit = -1;           // -1 = unlimited
private ZoneId userTimeZone = ZoneId.systemDefault();

// Getters
public int getDailyLikeLimit();
public int getDailyPassLimit();
public boolean hasUnlimitedPasses();
public ZoneId getUserTimeZone();
```

#### [MODIFY] `LikeStorage.java`
Add method to count today's likes.

```java
// Add to interface
int countLikesToday(UUID userId, Instant startOfDay);
int countPassesToday(UUID userId, Instant startOfDay);
```

---

### Storage Layer

#### [MODIFY] `H2LikeStorage.java`
Implement daily count queries.

```java
@Override
public int countLikesToday(UUID userId, Instant startOfDay) {
    String sql = """
        SELECT COUNT(*) FROM likes
        WHERE who_likes = ?
          AND direction = 'LIKE'
          AND created_at >= ?
        """;
    // Execute and return count
}
```

---

### CLI Changes

#### [MODIFY] `Main.java`

**Menu Display Enhancement:**
```
═══════════════════════════════════════
         DATING APP - PHASE 1
═══════════════════════════════════════
  Current User: Alice (ACTIVE)
  Session: 5 swipes (3 likes, 2 passes) | 2:30 elapsed
  💝 Daily Likes: 47/100 remaining     ← NEW
───────────────────────────────────────
```

**Browse Flow Modification:**
```java
// Before recording a like, check limit
if (direction == Like.Direction.LIKE) {
    if (!dailyLimitService.canLike(currentUser.getId())) {
        DailyStatus status = dailyLimitService.getStatus(currentUser.getId());
        logger.info("❌ Daily like limit reached! Resets in {}",
            formatDuration(status.getTimeUntilReset()));
        // Offer to pass instead or quit
        return;
    }
}
```

**Limit Reached Screen:**
```
═══════════════════════════════════════
         💔 DAILY LIMIT REACHED
═══════════════════════════════════════

  You've used all 100 likes for today!

  Resets in: 4h 32m

  Tips for tomorrow:
  • Take time to read profiles
  • Quality over quantity
  • Check your matches!

  [Press Enter to return to menu]
═══════════════════════════════════════
```

---

## Data Flow

```
User attempts to LIKE
    ↓
DailyLimitService.canLike(userId):
  1. Calculate startOfDay (midnight in user's timezone)
  2. Query likeStorage.countLikesToday(userId, startOfDay)
  3. Compare to config.getDailyLikeLimit()
  4. Return count < limit
    ↓
If true → proceed with like
If false → show limit reached message
```

---

## Time Zone Handling

```java
public Instant getStartOfToday() {
    return LocalDate.now(config.getUserTimeZone())
        .atStartOfDay(config.getUserTimeZone())
        .toInstant();
}

public Instant getResetTime() {
    return LocalDate.now(config.getUserTimeZone())
        .plusDays(1)
        .atStartOfDay(config.getUserTimeZone())
        .toInstant();
}
```

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| User at 99/100 likes | Can make 1 more like |
| User at 100/100 likes | Cannot like, can still pass |
| Midnight crosses during session | Next like triggers fresh count |
| Config limit = -1 | No limit enforced (unlimited) |
| Config limit = 0 | All likes blocked (maintenance mode) |

---

## Service Registration

#### [MODIFY] `ServiceRegistry.java`
```java
private final DailyLimitService dailyLimitService;
public DailyLimitService getDailyLimitService();
```

#### [MODIFY] `ServiceRegistryBuilder.java`
```java
DailyLimitService dailyLimitService = new DailyLimitService(likeStorage, config);
```

---

## Verification Plan

### Unit Tests

Create `DailyLimitServiceTest.java`:
- `canLike_underLimit_returnsTrue`
- `canLike_atLimit_returnsFalse`
- `canLike_afterMidnight_resetsCount`
- `canPass_unlimited_alwaysTrue`
- `getStatus_returnsCorrectCounts`
- `getTimeUntilReset_calculatesCorrectly`

### Integration Test
```bash
mvn test -Dtest=DailyLimitServiceTest
```

### Manual CLI Test

1. Set `dailyLikeLimit = 3` in AppConfig for testing
2. Start app, select user
3. Browse and like 3 candidates
4. Attempt 4th like - should see "Daily limit reached"
5. Verify passes still work

---

## Configuration Example

For production, add to `AppConfig.defaults()`:
```java
public static AppConfig defaults() {
    return new AppConfig()
        .withDailyLikeLimit(100)
        .withDailyPassLimit(-1)  // unlimited
        .withUserTimeZone(ZoneId.systemDefault());
}
```

---

## File Summary

| File | Action | Lines |
|------|--------|-------|
| `DailyLimitService.java` | NEW | ~70 |
| `AppConfig.java` | MODIFY | +15 |
| `LikeStorage.java` | MODIFY | +4 |
| `H2LikeStorage.java` | MODIFY | +25 |
| `ServiceRegistry.java` | MODIFY | +5 |
| `ServiceRegistryBuilder.java` | MODIFY | +3 |
| `Main.java` | MODIFY | +35 |
| `DailyLimitServiceTest.java` | NEW | ~80 |

**Total estimated: ~240 lines**
