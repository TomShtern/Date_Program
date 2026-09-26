# Implementation Plan: Core Logic & Consistency Fixes

**Source:** `AUDIT_PROBLEMS_CORE.md`
**Scope:** Core services, business rules, concurrency, and CLI logic
**Target:** Fix all findings (C-01 through L-09, H-01 through H-20, M-01 through M-27)

---

## Pre-Implementation Checklist

Before starting any fix:
1. Run `mvn test` to confirm all 736+ tests pass (green baseline)
2. Run `mvn spotless:apply` to ensure formatting is clean
3. Create a git branch: `git checkout -b fix/audit-core-logic`

After each fix group:
- Run `mvn test` to verify no regressions
- Run `mvn spotless:apply && mvn verify` before committing

## Status (2026-02-06)
- ✅ C-01 Done: Replaced unbounded per-user locks with fixed striped lock pool in `SessionService`.
- ✅ C-02 Done: Moved `AppSession.notifyListeners` outside synchronized block.
- ✅ C-03 Done: Recorded swipes after match determination and removed redundant `recordMatch`.
- ✅ C-04 Done: `MessagingService.canMessage()` now mirrors `match.canMessage()`; added FRIENDS test.
- ✅ C-05 Done: Verified MERGE + unique constraint; conflict handler already safe (no change needed).
- ✅ H-01 Done: `cleanupOldDailyPickViews` now removes entries older than 7 days; added test coverage.
- ✅ H-02 Done: Added `User.hasLocationSet`, storage mapping, and (0,0) location handling.
- ✅ H-03 Done: Guarded distance calculations in match quality, standouts, and daily pick reasons.
- ✅ H-04 Done: Cached daily picks per user/day; deterministic pick and reason across calls.
- ✅ H-05 Done: Match counts use `getAllMatchesFor`; progress now reflects total count.
- ✅ H-06 Done: `MatchingService` uses a single constructor + builder; call sites updated.
- ✅ H-07 Done: Daily pick generic reasons only used when no specific reasons exist.
- ✅ H-08 Done: `SwipeSession.getSwipesPerMinute()` uses proper rate formula.
- ✅ H-10 Done: Age calculation now uses UTC.
- ✅ H-14 Done: Removed reflection from achievements display; use `Achievement.UserAchievement`.
- ✅ H-15 Done: Enforced daily like limit in standout and like-back paths; added UI test.
- ✅ H-17 Done: Wrapped CLI handler calls with `safeExecute` in `Main`.
- ✅ H-19 Done: Daily pick cleanup and cache retention prevents unbounded growth.
- ✅ H-20 Done: `Match.canMessage()` already blocks messaging for BLOCKED; verified usage.
- ✅ M-01 Done: AppConfig weight sum validation added with tests.
- ✅ M-02 Done: Response score thresholds now configurable (`responseTimeWeekHours`, `responseTimeMonthHours`).
- ✅ M-03 Done: Dealbreaker height check fails on null candidate height; tests updated.
- ✅ M-04 Done: Undo success returns `null` message; constructor permits null on success.
- ✅ M-05 Done: Dealbreakers and ValidationService accept injected AppConfig; CandidateFinder passes config.
- ✅ M-06 Done: Match conflict handler filters to ACTIVE state when retrieving existing match.
- ✅ M-07 Done: Cached seeker `interestedIn` set in `CandidateFinder` to avoid extra copies.
- ✅ M-08 Done: `PerformanceMonitor.Timer` records success/error distinctly with `markSuccess()`.
- ✅ M-16 Done: Added constructor null checks in `HandlerFactory`.
- ✅ M-23 Done: `DailyService` uses injected `Clock` for time, enabling deterministic tests.
- ✅ M-27 Done: `processSwipe()` returns `configError` instead of throwing when misconfigured.
- ✅ L-01 Done: `AppBootstrap` uses volatile cached services for fast reads after init.
- ✅ L-03 Done: Documented overlap between `Match.State` and `Match.ArchiveReason`.
- ✅ L-04 Done: `User.isVerified()` now returns primitive boolean; call sites updated.
- ✅ L-06 Done: `LoginViewModel.createUser()` saves once with activation check.
- ✅ L-08 Done: `User.getPhotoUrls()` returns unmodifiable list (no defensive copies).

---

## Fix 1: C-01 — SessionService.userLocks.clear() Race Condition
**File:** `src/main/java/datingapp/core/SessionService.java`
**Lines:** 77-79
**Severity:** Critical

### Current Code (lines 77-79):
```java
if (userLocks.size() > MAX_USER_LOCKS) {
    userLocks.clear();
}
```

### Steps:
1. Open `SessionService.java`
2. Replace the naive `clear()` with LRU-style eviction. Replace the `ConcurrentMap<UUID, Object> userLocks` field and the clearing logic with a **striped lock pool** approach:
   - Replace the field declaration (line 24) from:
     ```java
     private final ConcurrentMap<UUID, Object> userLocks = new ConcurrentHashMap<>();
     ```
     To:
     ```java
     private static final int LOCK_STRIPE_COUNT = 256;
     private final Object[] lockStripes;
     ```
   - Initialize in the constructor after existing `this.config = ...`:
     ```java
     this.lockStripes = new Object[LOCK_STRIPE_COUNT];
     for (int i = 0; i < LOCK_STRIPE_COUNT; i++) {
         lockStripes[i] = new Object();
     }
     ```
   - Replace the lock acquisition in `recordSwipe()` (lines 77-80) from:
     ```java
     if (userLocks.size() > MAX_USER_LOCKS) {
         userLocks.clear();
     }
     Object lock = userLocks.computeIfAbsent(userId, id -> new Object());
     ```
     To:
     ```java
     Object lock = lockStripes[Math.floorMod(userId.hashCode(), LOCK_STRIPE_COUNT)];
     ```
3. Remove the `MAX_USER_LOCKS` constant (line 27) and the `ConcurrentMap` import since they're no longer needed
4. Remove the `java.util.concurrent.ConcurrentMap` import if no longer used

### Test:
- All existing `SessionServiceTest` tests must pass
- The striped lock pool is fixed-size (256 objects), so it never grows unbounded

---

## Fix 2: C-02 — AppSession.notifyListeners() Called Inside synchronized
**File:** `src/main/java/datingapp/core/AppSession.java`
**Lines:** 37-40

### Steps:
1. Open `AppSession.java`
2. Change `setCurrentUser()` to copy state, release lock, then notify:
   - Replace lines 37-40:
     ```java
     public synchronized void setCurrentUser(User user) {
         this.currentUser = user;
         notifyListeners(user);
     }
     ```
     With:
     ```java
     public void setCurrentUser(User user) {
         synchronized (this) {
             this.currentUser = user;
         }
         notifyListeners(user);
     }
     ```
3. Note: `notifyListeners` already iterates a `CopyOnWriteArrayList` (thread-safe for iteration), so this is safe even outside the synchronized block

### Test:
- Existing tests pass unchanged

---

## Fix 3: C-03 — MatchingService.recordLike() Records Swipe As matched=false Before Knowing
**File:** `src/main/java/datingapp/core/MatchingService.java`
**Lines:** 104-111

### Steps:
1. Open `MatchingService.java`
2. Move the session recording to AFTER match determination. Change lines 98-146 so that the `sessionService.recordSwipe()` call happens AFTER we know whether it's a match:
   - Remove the early session recording (lines 104-111):
     ```java
     if (sessionService != null) {
         sessionService.recordSwipe(
                 like.whoLikes(), like.direction(), false
                 );
     }
     ```
   - Instead, track `matched` as a local variable. After the match determination block (around line 145), add the session recording with the correct `matched` status:
     ```java
     // Record in session with correct match status
     if (sessionService != null) {
         sessionService.recordSwipe(like.whoLikes(), like.direction(), matchResult.isPresent());
     }
     ```
   - Full refactored flow:
     ```java
     public Optional<Match> recordLike(Like like) {
         if (likeStorage.exists(like.whoLikes(), like.whoGotLiked())) {
             return Optional.empty();
         }
         likeStorage.save(like);
         if (like.direction() == Like.Direction.PASS) {
             if (sessionService != null) {
                 sessionService.recordSwipe(like.whoLikes(), like.direction(), false);
             }
             return Optional.empty();
         }
         Optional<Match> matchResult = Optional.empty();
         if (likeStorage.mutualLikeExists(like.whoLikes(), like.whoGotLiked())) {
             Match match = Match.create(like.whoLikes(), like.whoGotLiked());
             try {
                 matchStorage.save(match);
                 matchResult = Optional.of(match);
             } catch (RuntimeException ex) {
                 if (logger.isWarnEnabled()) {
                     logger.warn("Match save conflict for {}: {}", match.getId(), ex.getMessage());
                 }
                 matchResult = matchStorage.get(match.getId());
             }
         }
         if (sessionService != null) {
             sessionService.recordSwipe(like.whoLikes(), like.direction(), matchResult.isPresent());
         }
         return matchResult;
     }
     ```
3. Remove the separate `sessionService.recordMatch()` call inside the match block since we're now passing `true` to `recordSwipe()` directly

### Test:
- All `MatchingServiceTest` tests must pass
- Verify `matchCount <= likeCount` invariant is preserved in `SwipeSession`

---

## Fix 4: C-04 — MessagingService.canMessage() vs sendMessage() Inconsistency
**File:** `src/main/java/datingapp/core/MessagingService.java`
**Line:** 213

### Steps:
1. Open `MessagingService.java`
2. Change line 213 from:
   ```java
   return matchOpt.isPresent() && matchOpt.get().isActive();
   ```
   To:
   ```java
   return matchOpt.isPresent() && matchOpt.get().canMessage();
   ```
   This aligns `canMessage()` with `sendMessage()` which already uses `match.canMessage()` (line 57)

### Test:
- Existing messaging tests pass
- Add a test case: verify `canMessage()` returns `true` for FRIENDS state match

---

## Fix 5: C-05 — Race Condition in Match Creation (Non-Atomic Check-Then-Act)
**File:** `src/main/java/datingapp/core/MatchingService.java`
**Lines:** 121-127

### Steps:
1. The existing code already uses `matchStorage.save(match)` with upsert semantics (MERGE) and has a catch block for conflicts (lines 135-142)
2. Verify that `JdbiMatchStorage.save()` uses `MERGE INTO ... KEY(id)` — it already does
3. The fix is already partially in place. However, to be fully safe, add a database-level unique constraint. Open `DatabaseManager.java` and confirm the `uk_matches` constraint exists (line 177): `CONSTRAINT uk_matches UNIQUE (user_a, user_b)` — it's already there
4. The conflict handler (lines 135-142) fetches the existing match on conflict. This is correct. No code change needed here — the idempotent save + unique constraint is sufficient for H2

---

## Fix 6: H-01 — DailyService.cleanupOldDailyPickViews() Never Cleans Anything
**File:** `src/main/java/datingapp/core/DailyService.java`
**Lines:** 158-159

### Steps:
1. Open `DailyService.java`
2. The issue is on line 158-159: `cleanupOldDailyPickViews(date)` is called with `date = today`, but entries are only added for today, so `date.isBefore(today)` is always false
3. Change line 159 from:
   ```java
   cleanupOldDailyPickViews(date);
   ```
   To:
   ```java
   cleanupOldDailyPickViews(date.minusDays(7));
   ```
   This removes entries older than 7 days, keeping a reasonable retention window

### Test:
- Existing `DailyServiceTest` passes
- Add test: insert entries for 10 days ago, call cleanup with today.minusDays(7), verify entries are removed

---

## Fix 7: H-02 — CandidateFinder.hasLocation() Fails for Users at (0, 0)
**File:** `src/main/java/datingapp/core/CandidateFinder.java`
**Line:** 367-369

### Steps:
1. This is a systemic issue. The proper fix requires adding a `hasLocationSet` boolean to `User`
2. Open `src/main/java/datingapp/core/User.java` (find the class)
3. Add a new field: `private boolean hasLocationSet = false;`
4. In `User.setLocation(double lat, double lon)`, set `this.hasLocationSet = true; touch();`
5. Add getter: `public boolean hasLocationSet() { return hasLocationSet; }`
6. In `User.StorageBuilder`, add `.hasLocationSet(boolean)` that sets the field
7. In `CandidateFinder.java`, change `hasLocation()` (line 367-369) from:
   ```java
   private boolean hasLocation(User user) {
       return user.getLat() != 0.0 || user.getLon() != 0.0;
   }
   ```
   To:
   ```java
   private boolean hasLocation(User user) {
       return user.hasLocationSet();
   }
   ```
8. In `JdbiUserStorage.java` Mapper, read from DB: set `hasLocationSet` to true when lat or lon is non-null/non-zero (backward compatible)
9. Add column: In `DatabaseManager.java`, add migration: `ALTER TABLE users ADD COLUMN IF NOT EXISTS has_location_set BOOLEAN DEFAULT FALSE`
10. Update `UserBindingHelper` to include `has_location_set`
11. Update `JdbiUserStorage` MERGE query and `ALL_COLUMNS` to include `has_location_set`

### Test:
- Add test in `CandidateFinderTest`: user at (0,0) with `hasLocationSet=true` is treated as having location
- User without location set should bypass distance filtering (existing behavior preserved)

---

## Fix 8: H-03 — GeoUtils.distanceTo() Called Without Location Check
**Files:** `core/MatchQualityService.java`, `core/StandoutsService.java` (find it), `core/DailyService.java`

### Steps:
1. In `MatchQualityService.java`, around line 318, before calculating distance:
   ```java
   double distanceKm;
   if (me.hasLocationSet() && them.hasLocationSet()) {
       distanceKm = GeoUtils.distanceKm(me.getLat(), me.getLon(), them.getLat(), them.getLon());
   } else {
       distanceKm = -1; // Unknown distance
   }
   double distanceScore = distanceKm >= 0 ? calculateDistanceScore(distanceKm, me.getMaxDistanceKm()) : 0.5;
   ```
2. In `DailyService.java`, `generateReason()` method (line 186), guard the distance calculation:
   ```java
   if (seeker.hasLocationSet() && picked.hasLocationSet()) {
       double distance = GeoUtils.distanceKm(seeker.getLat(), seeker.getLon(), picked.getLat(), picked.getLon());
       // ... existing distance-based reason logic
   }
   ```
3. Find and open `StandoutsService.java`, apply the same guard around any GeoUtils calls

### Test:
- Add tests with users that have no location set — scores should default to 0.5 (neutral)

---

## Fix 9: H-04 — DailyService.getDailyPick() Non-Deterministic
**File:** `src/main/java/datingapp/core/DailyService.java`
**Lines:** 128-131

### Steps:
1. The seed is deterministic but the candidate list size changes. Fix: cache the picked user ID per day
2. Add a new `ConcurrentHashMap<String, UUID>` field for cached picks:
   ```java
   private final Map<String, UUID> cachedDailyPicks = new ConcurrentHashMap<>();
   ```
3. In `getDailyPick()`, after candidates are found, change the pick logic:
   ```java
   String cacheKey = seeker.getId() + "_" + today;
   UUID cachedPickId = cachedDailyPicks.get(cacheKey);
   User picked = null;
   if (cachedPickId != null) {
       picked = candidates.stream().filter(c -> c.getId().equals(cachedPickId)).findFirst().orElse(null);
   }
   if (picked == null) {
       long seed = today.toEpochDay() + seeker.getId().hashCode();
       Random random = new Random(seed);
       picked = candidates.get(random.nextInt(candidates.size()));
       cachedDailyPicks.put(cacheKey, picked.getId());
   }
   ```
4. In `cleanupOldDailyPickViews()`, also clean old cache entries:
   ```java
   cachedDailyPicks.entrySet().removeIf(entry -> !entry.getKey().endsWith("_" + LocalDate.now(config.userTimeZone())));
   ```

### Test:
- Call `getDailyPick()` twice for same user+day with changing candidate list size — should return same user

---

## Fix 10: H-05 — AchievementService.getMatchCount() Only Counts Active Matches
**File:** `src/main/java/datingapp/core/AchievementService.java`
**Lines:** 223-225

### Steps:
1. Change `getMatchCount()` from:
   ```java
   private int getMatchCount(UUID userId) {
       return matchStorage.getActiveMatchesFor(userId).size();
   }
   ```
   To:
   ```java
   private int getMatchCount(UUID userId) {
       return matchStorage.getAllMatchesFor(userId).size();
   }
   ```
   This counts all-time matches, not just currently active ones. `getAllMatchesFor()` already exists on `MatchStorage`

### Test:
- Create user with 3 matches, unmatch 2, verify `getMatchCount()` returns 3

---

## Fix 11: H-06 — MatchingService Has 4 Constructors
**File:** `src/main/java/datingapp/core/MatchingService.java`
**Lines:** 40-88

### Steps:
1. Replace the 4 telescoping constructors with a single full constructor + Builder:
2. Keep only one constructor:
   ```java
   public MatchingService(
           LikeStorage likeStorage,
           MatchStorage matchStorage,
           UserStorage userStorage,
           BlockStorage blockStorage,
           SessionService sessionService,
           UndoService undoService,
           DailyService dailyService) {
       this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
       this.matchStorage = Objects.requireNonNull(matchStorage, "matchStorage cannot be null");
       this.userStorage = userStorage;
       this.blockStorage = blockStorage;
       this.sessionService = sessionService;
       this.undoService = undoService;
       this.dailyService = dailyService;
   }
   ```
3. Add a static Builder class:
   ```java
   public static class Builder {
       private LikeStorage likeStorage;
       private MatchStorage matchStorage;
       private UserStorage userStorage;
       private BlockStorage blockStorage;
       private SessionService sessionService;
       private UndoService undoService;
       private DailyService dailyService;

       public Builder likeStorage(LikeStorage s) { this.likeStorage = s; return this; }
       public Builder matchStorage(MatchStorage s) { this.matchStorage = s; return this; }
       public Builder userStorage(UserStorage s) { this.userStorage = s; return this; }
       public Builder blockStorage(BlockStorage s) { this.blockStorage = s; return this; }
       public Builder sessionService(SessionService s) { this.sessionService = s; return this; }
       public Builder undoService(UndoService s) { this.undoService = s; return this; }
       public Builder dailyService(DailyService s) { this.dailyService = s; return this; }

       public MatchingService build() {
           return new MatchingService(likeStorage, matchStorage, userStorage, blockStorage,
                   sessionService, undoService, dailyService);
       }
   }
   public static Builder builder() { return new Builder(); }
   ```
4. Update all call sites (find with grep `new MatchingService(`) in:
   - `ServiceRegistry` or `AppBootstrap`
   - `HandlerFactory`
   - Test files
5. In `processSwipe()`, change the null check to a more descriptive error

### Test:
- All existing tests pass after call-site updates

---

## Fix 12: H-07 — DailyService.generateReason() Dilutes Specific Reasons
**File:** `src/main/java/datingapp/core/DailyService.java`
**Lines:** 225-231

### Steps:
1. Move the generic fallback strings to only be added when `reasons.isEmpty()`:
   - Replace lines 225-231 (unconditional additions + final selection):
     ```java
     reasons.add("Our algorithm thinks you might click!");
     reasons.add("Something different today!");
     reasons.add("Expand your horizons!");
     reasons.add("Why not give them a chance?");
     reasons.add("Could be a pleasant surprise!");
     return reasons.get(random.nextInt(reasons.size()));
     ```
     With:
     ```java
     if (reasons.isEmpty()) {
         reasons.add("Our algorithm thinks you might click!");
         reasons.add("Something different today!");
         reasons.add("Expand your horizons!");
         reasons.add("Why not give them a chance?");
         reasons.add("Could be a pleasant surprise!");
     }
     return reasons.get(random.nextInt(reasons.size()));
     ```

### Test:
- Create two users with matching lifestyle fields — verify reason is specific, not generic

---

## Fix 13: H-08 — SwipeSession.getSwipesPerMinute() Deflates Velocity
**File:** `src/main/java/datingapp/core/SwipeSession.java`
**Lines:** 172-178

### Steps:
1. Change `getSwipesPerMinute()` from:
   ```java
   public double getSwipesPerMinute() {
       long seconds = getDurationSeconds();
       if (seconds < 60) {
           return swipeCount; // Less than a minute, return raw count
       }
       return swipeCount / (seconds / 60.0);
   }
   ```
   To:
   ```java
   public double getSwipesPerMinute() {
       long seconds = getDurationSeconds();
       if (seconds == 0) {
           return swipeCount;
       }
       return swipeCount * 60.0 / seconds;
   }
   ```

### Test:
- 5 swipes in 10 seconds should return 30.0, not 5.0
- 60 swipes in 60 seconds should return 60.0

---

## Fix 14: H-10 — Age Calculation Timezone Sensitivity
**File:** `src/main/java/datingapp/core/User.java`

### Steps:
1. Find the `getAge()` method in `User.java`
2. Ensure it uses UTC consistently. Change from `LocalDate.now()` to `LocalDate.now(ZoneOffset.UTC)`:
   ```java
   public int getAge() {
       if (birthDate == null) return 0;
       return Period.between(birthDate, LocalDate.now(java.time.ZoneOffset.UTC)).getYears();
   }
   ```

### Test:
- Existing age-related tests pass

---

## Fix 15: H-14 — MatchingHandler Uses Reflection for Achievement Access
**File:** `src/main/java/datingapp/app/cli/MatchingHandler.java`
**Lines:** 591-615

### Steps:
1. The issue is that `checkAndDisplayNewAchievements()` uses reflection to access `UserAchievement`
2. `Achievement.UserAchievement` should already be `public static` per project conventions. Verify: open `Achievement.java` and check
3. Replace the reflection-based code (lines 591-615):
   ```java
   private void checkAndDisplayNewAchievements(User currentUser) {
       List<Achievement.UserAchievement> newAchievements = achievementService.checkAndUnlock(currentUser.getId());
       if (newAchievements.isEmpty()) {
           return;
       }
       logInfo("\n🏆 NEW ACHIEVEMENTS UNLOCKED! 🏆");
       for (Achievement.UserAchievement ua : newAchievements) {
           logInfo("  ✨ {} - {}", ua.achievement().getDisplayName(), ua.achievement().getDescription());
       }
       logInfo("");
   }
   ```
4. Remove the `java.lang.reflect.Method` usage from the file

### Test:
- Compile check: no reflection needed

---

## Fix 16: H-15 — Standout and Like-Back Actions Bypass Daily Like Limit
**Files:** `app/cli/MatchingHandler.java` (line 718), `ui/viewmodel/MatchesViewModel.java` (line 228)

### Steps:
1. In `MatchingHandler.processStandoutInteraction()` (around line 715), add a daily limit check before the `recordLike()` call:
   ```java
   private void processStandoutInteraction(User currentUser, User candidate, boolean isLike) {
       if (isLike && !dailyService.canLike(currentUser.getId())) {
           showDailyLimitReached(currentUser);
           return;
       }
       // ... rest of existing code
   }
   ```
2. In `MatchesViewModel.likeBack()` (line 228), the ViewModel needs access to `DailyService`:
   - Add `DailyService` as a constructor parameter
   - Update `ViewModelFactory.getMatchesViewModel()` to pass `services.getDailyService()`
   - Add the check:
     ```java
     public void likeBack(LikeCardData like) {
         // ... existing null checks ...
         if (!dailyService.canLike(currentUser.getId())) {
             notifyError("Daily like limit reached", new IllegalStateException("Daily limit reached"));
             return;
         }
         matchingService.recordLike(Like.create(currentUser.getId(), like.userId(), Like.Direction.LIKE));
         refreshAll();
     }
     ```

### Test:
- Add test: after exhausting daily likes, `likeBack()` should not create a like

---

## Fix 17: H-17 — Main.java No Error Handling Around Handler Calls
**File:** `src/main/java/datingapp/Main.java`
**Lines:** 47-73

### Steps:
1. Wrap each handler call in the switch statement with try-catch:
   ```java
   switch (choice) {
       case "1" -> safeExecute(() -> handlers.profile().createUser());
       case "2" -> safeExecute(() -> handlers.profile().selectUser());
       // ... etc for all cases
       case "0" -> {
           running = false;
           logInfo("\n👋 Goodbye!\n");
       }
       default -> logInfo(CliConstants.INVALID_SELECTION);
   }
   ```
2. Add a helper method:
   ```java
   private static void safeExecute(Runnable action) {
       try {
           action.run();
       } catch (Exception e) {
           logInfo("\n❌ An error occurred: {}\n", e.getMessage());
           if (logger.isDebugEnabled()) {
               logger.debug("Handler error details", e);
           }
       }
   }
   ```

### Test:
- No direct test needed; this is a catch-all safety net

---

## Fix 18: H-19 — DailyService.dailyPickViews Unbounded In-Memory Storage
**File:** `src/main/java/datingapp/core/DailyService.java`

### Steps:
1. Already partially addressed in Fix 6 (cleanup retention from today → today-7)
2. Additionally, add a size cap. In `markDailyPickViewed()` (line 158), the existing size check triggers cleanup. This is already there. The fix from Fix 6 makes the cleanup actually work
3. No additional changes needed beyond Fix 6

---

## Fix 19: H-20 — Blocking Does Not Update Match/Messaging State
**File:** `src/main/java/datingapp/core/TrustSafetyService.java`

### Steps:
1. The `TrustSafetyService.block()` method already calls `updateMatchStateForBlock()` (line 205)
2. However, we need to also verify messaging is blocked. The messaging check goes through `match.canMessage()` which returns false for BLOCKED state
3. Verify `Match.canMessage()` returns false when state is BLOCKED — if it doesn't, fix it
4. Find `Match.java` and check the `canMessage()` method. It should return:
   ```java
   public boolean canMessage() {
       return state == State.ACTIVE || state == State.FRIENDS;
   }
   ```
   This already excludes BLOCKED. No code change needed if this is the case.

---

## Fix 20: M-01 — MatchQualityService Config Weights Not Validated
**File:** `src/main/java/datingapp/core/AppConfig.java`

### Steps:
1. Find `AppConfig` and locate the builder's `build()` method or the record constructor
2. Add a validation that the 6 weights sum to approximately 1.0:
   ```java
   double weightSum = distanceWeight + ageWeight + interestWeight + lifestyleWeight + paceWeight + responseWeight;
   if (Math.abs(weightSum - 1.0) > 0.01) {
       throw new IllegalArgumentException(
           "Config weights must sum to 1.0, got: " + weightSum);
   }
   ```

### Test:
- Add test: creating AppConfig with weights summing to 1.5 throws
- Add test: default AppConfig weights sum to 1.0

---

## Fix 21: M-02 — MatchQualityService.calculateResponseScore() Hardcoded Magic Numbers
**File:** `src/main/java/datingapp/core/MatchQualityService.java`
**Lines:** 590-599

### Steps:
1. Replace the hardcoded `168` (week) and `720` (month) with config values
2. Add to `AppConfig`: `responseTimeWeekHours()` defaulting to 168, and `responseTimeMonthHours()` defaulting to 720
3. In `calculateResponseScore()`, replace:
   ```java
   if (hours < 168) { return 0.5; }
   if (hours < 720) { return 0.3; }
   ```
   With:
   ```java
   if (hours < config.responseTimeWeekHours()) { return 0.5; }
   if (hours < config.responseTimeMonthHours()) { return 0.3; }
   ```

---

## Fix 22: M-03 — Dealbreakers.Evaluator.passesHeight() Returns true on null
**File:** Find `Dealbreakers.java` (search for `Dealbreakers.Evaluator.passesHeight`)

### Steps:
1. Locate the `passesHeight()` method
2. Change it to fail on null candidate height (consistent with other checks):
   ```java
   if (candidateHeight == null) {
       return false; // Missing candidate field = fail dealbreaker
   }
   ```

### Test:
- Add test: candidate with null height should fail the height dealbreaker

---

## Fix 23: M-04 — UndoResult.success() Uses Empty String
**File:** Find `UndoService.java`, locate `UndoResult`

### Steps:
1. Open `src/main/java/datingapp/core/UndoService.java`
2. Find `UndoResult.success()` and change the empty message to `null` or a meaningful message:
   ```java
   public static UndoResult success(Like undoneSwipe, boolean matchDeleted) {
       return new UndoResult(true, undoneSwipe, matchDeleted, null);
   }
   ```
3. Update the compact constructor to allow null message on success

---

## Fix 24: M-05 — Static AppConfig.defaults() Untestable in Dealbreakers/ValidationService
**File:** `core/Dealbreakers.java`, `core/ValidationService.java`

### Steps:
1. In `Dealbreakers.Evaluator`, change from static config to accept config as parameter:
   ```java
   public static boolean passes(User seeker, User candidate, AppConfig config) { ... }
   ```
   Add an overload that uses defaults for backward compat:
   ```java
   public static boolean passes(User seeker, User candidate) {
       return passes(seeker, candidate, AppConfig.defaults());
   }
   ```
2. Do the same for `ValidationService` if it has static AppConfig
3. Update `CandidateFinder.passesDealbreakers()` to pass the config

---

## Fix 25: M-06 — MatchingService.recordLike() Conflict Handler May Return Stale Match
**File:** `src/main/java/datingapp/core/MatchingService.java`
**Lines:** 135-142

### Steps:
1. In the catch block, verify the fetched match is still active:
   ```java
   } catch (RuntimeException ex) {
       if (logger.isWarnEnabled()) {
           logger.warn("Match save conflict for {}: {}", match.getId(), ex.getMessage());
       }
       Optional<Match> existing = matchStorage.get(match.getId());
       return existing.filter(m -> m.getState() == Match.State.ACTIVE);
   }
   ```

---

## Fix 26: M-07 — CandidateFinder Creates Unnecessary Defensive Copies
**File:** `src/main/java/datingapp/core/CandidateFinder.java`
**Line:** 319

### Steps:
1. In `findCandidates()`, cache the seeker's `interestedIn` set before the stream:
   ```java
   Set<Gender> seekerInterestedIn = seeker.getInterestedIn();
   ```
2. Use this cached set in the `matchesGenderPreferences` filter instead of calling `seeker.getInterestedIn()` each time (which creates a defensive copy)
3. Pass `seekerInterestedIn` to the filter method or restructure to use it

---

## Fix 27: M-08 — PerformanceMonitor.Timer Records Metrics on Exception
**File:** Find `PerformanceMonitor.java`

### Steps:
1. Add a `success` flag to the Timer that defaults to `false`
2. Add `markSuccess()` method
3. In `close()`, only record if success or add an `error` tag to the metric
4. Update callers to call `timer.markSuccess()` before `close()`/end of try-with-resources

---

## Fix 28: M-16 — HandlerFactory Missing Constructor Validation
**File:** `src/main/java/datingapp/app/cli/HandlerFactory.java`
**Lines:** 33-37

### Steps:
1. Add null checks to the constructor:
   ```java
   public HandlerFactory(ServiceRegistry services, AppSession session, InputReader inputReader) {
       this.services = Objects.requireNonNull(services, "services cannot be null");
       this.session = Objects.requireNonNull(session, "session cannot be null");
       this.inputReader = Objects.requireNonNull(inputReader, "inputReader cannot be null");
   }
   ```

---

## Fix 29: M-23 — Time Handling Inconsistent (Instant.now() vs Clock)
**Files:** Multiple services

### Steps:
1. This is a broader refactoring. For now, note which files use `Instant.now()` directly vs injected Clock
2. Add `Clock` parameter to key services that need testable time:
   - `DailyService`: already uses `config.userTimeZone()`, but `Instant.now()` calls should use a `Clock`
   - `SwipeSession`: `Instant.now()` in `recordSwipe()` and `end()` could use a Clock
3. For this sprint, add an optional `Clock` to `DailyService` constructor (default to `Clock.systemDefaultZone()`)
4. Replace `Instant.now()` with `Instant.now(clock)` and `LocalDate.now(zone)` with `LocalDate.now(clock)` in DailyService

---

## Fix 30: M-27 — MatchingService.processSwipe() Throws IllegalStateException
**File:** `src/main/java/datingapp/core/MatchingService.java`
**Lines:** 163-166

### Steps:
1. Change from throwing to returning a result:
   ```java
   public SwipeResult processSwipe(User currentUser, User candidate, boolean liked) {
       if (dailyService == null || undoService == null) {
           return SwipeResult.blocked(null, "dailyService and undoService required for processSwipe");
       }
       // ... rest unchanged
   }
   ```
2. Note: `SwipeResult.blocked()` takes a session parameter. Create a new factory method if needed:
   ```java
   public static SwipeResult configError(String reason) {
       return new SwipeResult(false, false, null, null, reason);
   }
   ```

---

## Fix 31: L-01 through L-09 — Low-Severity Items

### L-01: AppBootstrap.getServices() unnecessary synchronization
- After initialization, use volatile field pattern. Add `private volatile ServiceRegistry cachedServices;`

### L-02: DailyService uses ConcurrentHashMap for single-threaded access
- Leave as-is for now — premature optimization risk

### L-03: Match.ArchiveReason and Match.State overlap
- Document the overlap; refactoring would be breaking change

### L-04: User.isVerified() returns Boolean instead of boolean
- Change return type from `Boolean` to `boolean` in the getter

### L-06: LoginViewModel.createUser() double save
- Combine the save and activate into one operation:
  ```java
  if (newUser.isComplete()) {
      newUser.activate();
  }
  userStorage.save(newUser);
  ```
  Remove the first `userStorage.save(newUser)` before the activate check

### L-08: User.getPhotoUrls() defensive copy on every access
- Change to `return Collections.unmodifiableList(photoUrls);`

### L-09: DailyService random access on large list
- Leave as-is for H2; note for future database-level optimization

---

## Post-Implementation

1. Run full test suite: `mvn test`
2. Run quality checks: `mvn spotless:apply && mvn verify`
