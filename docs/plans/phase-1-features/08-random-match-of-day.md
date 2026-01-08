# Feature 8: Random Match of the Day

**Priority:** Medium
**Complexity:** Low
**Dependencies:** None

---

## Overview

Present users with a daily "wild card" profile that bypasses normal filtering criteria, introducing serendipity into the matching process. This could help users discover matches outside their usual preferences.

---

## User Stories

1. As a user, I want a daily surprise profile that might be outside my normal criteria
2. As a user, I want to know why this profile was selected as my "pick of the day"
3. As a user, I want to be able to skip the daily pick without penalty

---

## Proposed Changes

### Core Layer

#### [NEW] `DailyPickService.java`
Service to select and manage daily pick.

```java
public class DailyPickService {
    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final BlockStorage blockStorage;
    private final AppConfig config;

    public record DailyPick(
        User user,
        LocalDate date,
        String reason,          // Why they were picked
        boolean alreadySeen     // Was this shown before today?
    ) {}

    /**
     * Get the daily pick for a user. Deterministic based on date.
     * Returns empty if no valid candidates or pick was already swiped.
     */
    public Optional<DailyPick> getDailyPick(User seeker);

    /**
     * Check if user has seen their daily pick today.
     */
    public boolean hasViewedDailyPick(UUID userId);

    /**
     * Mark the daily pick as viewed.
     */
    public void markDailyPickViewed(UUID userId);
}
```

#### Selection Algorithm

```java
public Optional<DailyPick> getDailyPick(User seeker) {
    // 1. Get all active users except self, blocked, and already interacted
    List<User> candidates = userStorage.findActive().stream()
        .filter(u -> !u.getId().equals(seeker.getId()))
        .filter(u -> !blockStorage.isBlocked(seeker.getId(), u.getId()))
        .filter(u -> !likeStorage.exists(seeker.getId(), u.getId()))
        .toList();

    if (candidates.isEmpty()) {
        return Optional.empty();
    }

    // 2. Use date + seeker ID as deterministic seed
    long seed = LocalDate.now().toEpochDay() + seeker.getId().hashCode();
    Random random = new Random(seed);

    // 3. Pick one candidate
    User picked = candidates.get(random.nextInt(candidates.size()));

    // 4. Generate a reason
    String reason = generateReason(seeker, picked);

    return Optional.of(new DailyPick(picked, LocalDate.now(), reason, false));
}

private String generateReason(User seeker, User picked) {
    List<String> reasons = new ArrayList<>();

    // Distance-based
    double distance = GeoUtils.distanceKm(seeker, picked);
    if (distance < 5) {
        reasons.add("Lives nearby!");
    }

    // Age-based
    if (Math.abs(seeker.getAge() - picked.getAge()) <= 2) {
        reasons.add("Similar age");
    }

    // Lifestyle matches (if implemented)
    if (seeker.getLookingFor() == picked.getLookingFor()) {
        reasons.add("Looking for the same thing");
    }

    // Random fun reasons
    reasons.add("Our algorithm thinks you might click!");
    reasons.add("Something different today!");
    reasons.add("Expand your horizons!");

    return reasons.get(new Random(seeker.getId().hashCode()).nextInt(reasons.size()));
}
```

#### [NEW] `DailyPickViewStorage.java`
Track when users viewed their daily pick.

```java
public interface DailyPickViewStorage {
    void markViewed(UUID userId, LocalDate date);
    boolean hasViewed(UUID userId, LocalDate date);
    void cleanup(LocalDate before);  // Remove old records
}
```

---

### Storage Layer

#### [NEW] `H2DailyPickViewStorage.java`
Simple table to track views.

```sql
CREATE TABLE daily_pick_views (
    user_id UUID NOT NULL,
    viewed_date DATE NOT NULL,
    viewed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, viewed_date)
);
```

---

### CLI Changes

#### [MODIFY] `Main.java`

**New Menu Flow:**

When user selects "Browse candidates" (option 4):

```java
private static void browseCandidates() {
    // ... existing validation ...

    // Check for daily pick first
    Optional<DailyPick> dailyPick = dailyPickService.getDailyPick(currentUser);
    if (dailyPick.isPresent() && !dailyPickService.hasViewedDailyPick(currentUser.getId())) {
        showDailyPick(dailyPick.get());
    }

    // Continue with normal browsing...
}

private static void showDailyPick(DailyPick pick) {
    logger.info("\n" + SEPARATOR_LINE);
    logger.info("       🎲 YOUR DAILY PICK 🎲");
    logger.info(SEPARATOR_LINE);
    logger.info("");
    logger.info("  ✨ {}", pick.reason());
    logger.info("");

    User candidate = pick.user();
    double distance = GeoUtils.distanceKm(
        currentUser.getLat(), currentUser.getLon(),
        candidate.getLat(), candidate.getLon());

    logger.info("┌─────────────────────────────────────────┐");
    logger.info("│ 🎁 {}, {} years old", candidate.getName(), candidate.getAge());
    logger.info("│ 📍 {} km away", String.format("%.1f", distance));
    logger.info("│ 📝 {}", candidate.getBio() != null ? candidate.getBio() : "(no bio)");
    logger.info("└─────────────────────────────────────────┘");
    logger.info("");
    logger.info("  This pick resets tomorrow at midnight!");
    logger.info("");

    String action = readLine("  [L]ike / [P]ass / [S]kip for now: ").toLowerCase();

    dailyPickService.markDailyPickViewed(currentUser.getId());

    if (action.equals("s")) {
        logger.info("  👋 You can see this pick again later today.\n");
        return;
    }

    Like.Direction direction = action.equals("l") ? Like.Direction.LIKE : Like.Direction.PASS;
    Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
    Optional<Match> match = matchingService().recordLike(like);

    if (match.isPresent()) {
        logger.info("\n🎉🎉🎉 IT'S A MATCH WITH YOUR DAILY PICK! 🎉🎉🎉\n");
    } else if (direction == Like.Direction.LIKE) {
        logger.info("❤️  Liked your daily pick!\n");
    } else {
        logger.info("👋 Passed on daily pick.\n");
    }
}
```

---

## Daily Pick Display

```
═══════════════════════════════════════
       🎲 YOUR DAILY PICK 🎲
═══════════════════════════════════════

  ✨ Our algorithm thinks you might click!

┌─────────────────────────────────────────┐
│ 🎁 Marcus, 29 years old
│ 📍 12.3 km away
│ 📝 Coffee enthusiast and weekend hiker
└─────────────────────────────────────────┘

  This pick resets tomorrow at midnight!

  [L]ike / [P]ass / [S]kip for now:
```

---

## Selection Properties

| Property | Value |
|----------|-------|
| Deterministic | Yes (same pick all day for same user) |
| Excludes blocked users | Yes |
| Excludes already-swiped | Yes |
| Respects dealbreakers | No (that's the point!) |
| Respects preferences | Partially (gender yes, age/distance no) |

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| No valid candidates | Daily pick not shown |
| User swipes on pick | Pick disappears for today |
| User skips pick | Pick reappears until swiped or midnight |
| All candidates already swiped | No pick for this day |
| New user joins mid-day | May become someone's pick next day |

---

## Verification Plan

### Unit Tests

Create `DailyPickServiceTest.java`:
- `getDailyPick_sameDateSameUser_returnsSamePick`
- `getDailyPick_differentDates_returnsDifferentPicks`
- `getDailyPick_respectsBlockList`
- `getDailyPick_excludesAlreadySwiped`
- `getDailyPick_noCandidates_returnsEmpty`

### Integration Test
```bash
mvn test -Dtest=DailyPickServiceTest
```

### Manual CLI Test

1. Start app with 3+ users
2. Select User A
3. Browse candidates → Should see "Daily Pick" first
4. Press 'S' to skip
5. Browse again → Daily pick appears again
6. Like the daily pick
7. Browse again → No daily pick (already swiped)
8. Check next day → New daily pick

---

## File Summary

| File | Action | Lines |
|------|--------|-------|
| `DailyPickService.java` | NEW | ~100 |
| `DailyPickViewStorage.java` | NEW | ~15 |
| `H2DailyPickViewStorage.java` | NEW | ~50 |
| `ServiceRegistry.java` | MODIFY | +5 |
| `ServiceRegistryBuilder.java` | MODIFY | +5 |
| `Main.java` | MODIFY | +60 |
| `DailyPickServiceTest.java` | NEW | ~70 |

**Total estimated: ~305 lines**
