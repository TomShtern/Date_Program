# Consolidated Codebase Audit — Core Logic & Consistency (February 6, 2026)

**Category:** Core services, business rules, concurrency, and CLI logic
**Sources:** Kimmy 2.5, Grok, Opus 4.6 (core + storage/UI), GPT-5 Mini, Raptor Mini, GPT-4.1
**Scope:** Full codebase — ~119 production Java files (~19K LOC), 37 test files
**Total Unique Findings:** 75+
<!-- ChangeStamp: 1|2026-02-06 17:29:51|agent:codex|scope:audit-group-core|Regroup audit by problem type (core logic)|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT_PROBLEMS_CORE.md -->

---

## Issues

### C-01: Race Condition — `SessionService.userLocks.clear()` Destroys Active Locks

**File:** `core/SessionService.java`
**Source:** Opus 4.6

```java
if (userLocks.size() > MAX_USER_LOCKS) {
    userLocks.clear();  // Destroys locks other threads are synchronized on
}
Object lock = userLocks.computeIfAbsent(userId, id -> new Object());
synchronized (lock) { ... }
```

When the `ConcurrentHashMap` exceeds 10K entries, `clear()` removes lock objects that other threads may be actively `synchronized` on. Thread A may still hold the old evicted lock, while Thread B creates a new lock for the same userId — both enter the critical section simultaneously.

**Fix:** Use LRU eviction, `WeakReference` values, or a fixed-size striped lock pool.

---

### C-02: Potential Deadlock — `AppSession.notifyListeners()` Called Inside `synchronized`

**File:** `core/AppSession.java`
**Source:** Opus 4.6

```java
public synchronized void setCurrentUser(User user) {
    this.currentUser = user;
    notifyListeners(user);  // Calls user-supplied callbacks while holding monitor
}
```

If any listener dispatches work to another thread that calls `getCurrentUser()` or `isLoggedIn()`, that thread blocks forever. JavaFX listeners using `Platform.runLater()` are especially susceptible.

**Fix:** Copy state, release lock, then notify outside the synchronized block.

---

### C-03: `MatchingService.recordLike()` Records Swipe As `matched=false` Before Knowing Match Status

**File:** `core/MatchingService.java`
**Source:** Opus 4.6

The swipe is recorded with `matched=false` before the mutual-like check. If it turns out to be a match, `sessionService.recordMatch()` tries to increment `matchCount` separately, creating an inconsistent state window and potentially violating the `matchCount <= likeCount` invariant.

**Fix:** Defer session recording until after match determination.

---

### C-04: `MessagingService` — `canMessage()` vs `sendMessage()` Check Different Match States

**File:** `core/MessagingService.java`
**Source:** Opus 4.6

| Method          | Check                         | Allows FRIENDS? |
|-----------------|-------------------------------|-----------------|
| `sendMessage()` | `matchOpt.get().canMessage()` | ✅ Yes           |
| `canMessage()`  | `matchOpt.get().isActive()`   | ❌ No            |

UI may incorrectly disable the send button for friends who should be able to message.

**Fix:** Change `canMessage()` to use `match.canMessage()` not `match.isActive()`.

---

### C-05: Race Condition in Match Creation — Non-Atomic Check-Then-Act

**File:** `core/MatchingService.java`
**Source:** Grok

```java
if (likeStorage.mutualLikeExists(like.whoLikes(), like.whoGotLiked())) {
    Match match = Match.create(like.whoLikes(), like.whoGotLiked());
    matchStorage.save(match); // Race condition — two simultaneous mutual likes
}
```

Two simultaneous mutual likes can create duplicate matches.

**Fix:** Use database-level unique constraints or distributed locks.

---

### H-01: `DailyService.cleanupOldDailyPickViews()` Never Cleans Anything — Memory Leak

**File:** `core/DailyService.java`
**Source:** Opus 4.6

The cleanup removes entries with dates before the given date, but `date` is always today and entries are only added for today. So `date.isBefore(today)` is always `false` — the map grows without bound.

**Fix:** Pass `date.minusDays(7)` or use a configurable retention period.

---

### H-02: `CandidateFinder.hasLocation()` Fails for Users at (0, 0) Coordinates

**File:** `core/CandidateFinder.java`
**Source:** Opus 4.6, Grok, Kimmy

```java
private boolean hasLocation(User user) {
    return user.getLat() != 0.0 || user.getLon() != 0.0;
}
```

Any user at latitude 0, longitude 0 is treated as having "no location." Users without location data bypass all distance filtering entirely.

**Fix:** Add a `hasLocationSet` boolean field to `User`.

---

### H-03: `GeoUtils.distanceTo()` Called Without Location Check in Multiple Services

**Files:** `core/MatchQualityService.java`, `core/StandoutsService.java`, `core/DailyService.java`
**Source:** Opus 4.6

These services skip the `hasLocation()` check, producing garbage distance scores from (0,0) to actual locations, corrupting match quality calculations and daily pick reasons.

---

### H-04: `DailyService.getDailyPick()` Non-Deterministic When Candidate List Changes

**File:** `core/DailyService.java`
**Source:** Opus 4.6

The seed is deterministic but `candidates.size()` changes as users join/leave. A user refreshing the app could see their "daily pick" change multiple times per day.

**Fix:** Cache the picked user's ID in a persistent store at start-of-day.

---

### H-05: `AchievementService.getMatchCount()` Only Counts Active Matches — Unmatching Revokes Progress

**File:** `core/AchievementService.java`
**Source:** Opus 4.6, Kimmy

```java
return matchStorage.getActiveMatchesFor(userId).size();
```

Users who unmatch drop below achievement thresholds. Achievements should track peak or all-time counts.

---

### H-06: `MatchingService` Has 4 Constructors with Inconsistent Null Dependencies

**File:** `core/MatchingService.java`
**Source:** Opus 4.6, GPT-5 Mini

The telescoping constructor pattern is fragile — different constructors leave different dependencies null, causing `IllegalStateException` at runtime depending on which method is called.

**Fix:** Consolidate to a single constructor + Builder with dependency group validation.

---

### H-07: `DailyService.generateReason()` Always Dilutes Specific Reasons with Generic Fallbacks

**File:** `core/DailyService.java`
**Source:** Opus 4.6

5 generic strings are always appended to reasons. Even with 3 specific reasons, there's a 62.5% chance the displayed reason is generic.

**Fix:** Only add fallbacks when `reasons.isEmpty()`.

---

### H-08: `SwipeSession.getSwipesPerMinute()` Deflates Velocity for Sessions Under 60 Seconds

**File:** `core/SwipeSession.java`
**Source:** Opus 4.6

```java
if (seconds < 60) {
    return swipeCount; // 5 swipes in 10 seconds → returns 5.0, not 30.0
}
```

This makes rapid bot-like swiping undetectable by the anti-bot check.

**Fix:** Extrapolate to per-minute rate: `swipeCount * 60.0 / seconds`.

---

### H-10: Age Calculation Timezone Sensitivity

**File:** `core/User.java`
**Source:** Grok

Age can differ by 1 day depending on timezone. A user could appear as 17 in one timezone and 18 in another, creating legal compliance issues.

**Fix:** Use consistent timezone (UTC) for age calculations.

---

### H-14: `MatchingHandler` Uses Reflection for Achievement Access

**File:** `app/cli/MatchingHandler.java`
**Source:** Storage/UI Audit

Uses `java.lang.reflect.Method` to call `achievement()`, `getDisplayName()`, `getDescription()` — fragile and breaks silently if method names change.

**Fix:** Make `Achievement.UserAchievement` public static per project conventions.

---

### H-15: Standout and Like-Back Actions Bypass Daily Like Limit

**Files:** `app/cli/MatchingHandler.java`, `ui/viewmodel/MatchesViewModel.java`
**Source:** Storage/UI Audit

Both `processStandoutInteraction()` and `likeBack()` call `matchingService.recordLike()` directly without checking daily limits.

**Fix:** Route all likes through daily limit check.

---

### H-17: `Main.java` — No Error Handling Around Handler Calls

**File:** `Main.java`
**Source:** Storage/UI Audit

Switch cases invoke handlers directly without try-catch. A `StorageException` will crash the CLI app with a stack trace.

---

### H-19: `DailyService.dailyPickViews` — Unbounded In-Memory Storage

**File:** `core/DailyService.java`
**Source:** Grok

10,000 users × 365 days = ~3.6M entries. No TTL on entries.

**Fix:** Implement TTL, persist to database, or use bounded cache.

---

### H-20: Blocking Does Not Update Match/Messaging State

**Source:** GPT-4.1

Blocked users can still message; match state is not always transitioned on block.

---

## 3. Medium-Severity Issues

### M-01: `MatchQualityService` Config Weights Not Validated to Sum to 1.0

If misconfigured to sum > 1.0, the compatibility score exceeds 100. If < 1.0, scores are deflated. No startup validation catches this.

---

### M-02: `MatchQualityService.calculateResponseScore()` Hardcodes Magic Numbers

The week (168h) and month (720h) thresholds are hardcoded while other thresholds use config. Violates no-hardcoded-thresholds convention.

---

### M-03: `Dealbreakers.Evaluator.passesHeight()` Inconsistent with Contract

Javadoc states "missing candidate fields FAIL dealbreakers" but `passesHeight()` returns `true` when `candidateHeight == null`, while other lifestyle checks correctly fail on null.

---

### M-04: `UndoResult.success()` Uses Empty String for Message

On failure, compact constructor requires `message` to be non-null and non-blank. On success, it's empty `""`. Callers checking `result.message().isBlank()` get ambiguous results.

---

### M-05: `Dealbreakers` and `ValidationService` — Static `AppConfig.defaults()` Untestable

Both use `private static final AppConfig CONFIG = AppConfig.defaults()` making it impossible to inject custom config for testing boundary conditions.

---

### M-06: `MatchingService.recordLike()` Conflict Handler May Return Stale Match

On storage conflict, fetches existing match which may have been transitioned to UNMATCHED. Caller may show "It's a match!" for an ended match.

---

### M-07: `CandidateFinder` Creates Unnecessary Defensive Copies Every Filter Step

`seeker.getInterestedIn()` creates `EnumSet.copyOf()` for every candidate in the stream. For 10K active users → ~20K unnecessary copies.

**Fix:** Cache the seeker's `interestedIn` once before the stream.

---

### M-08: `PerformanceMonitor.Timer` Records Metrics Even When Exceptions Thrown

`close()` records timing data regardless of success/failure, polluting metrics with partial operations.

---

### M-09: `TrustSafetyService` Test Constructors Leave All Dependencies Null

If any test calls methods not guarded by `ensureReportDependencies()`, it throws NPE.

---

### M-16: `HandlerFactory` — Missing Constructor Validation

No `Objects.requireNonNull()` checks on constructor parameters.

---

### M-23: Time Handling Inconsistent

Some code uses injected `Clock` (e.g., `UndoService`), other places call `Instant.now()` directly. Reduces testability.

---

### M-26: `MatchQualityService` / `MessagingService` — Hard-Coded Limit Values

`MessagingService` uses literal `100` for message limits, `ProfileViewModel` hardcodes `500` for max distance.

---

### M-27: Services Throwing Instead of Returning Result Records

`MatchingService.processSwipe()` throws `IllegalStateException` — violates the Result pattern convention.

---

### L-01: `AppBootstrap.getServices()` — Unnecessary Synchronization After Init

`ServiceRegistry` is immutable after initialization; read path could use a volatile field.

---

### L-02: `DailyService` Uses `ConcurrentHashMap` for Single-Threaded Access

Overhead is wasted if concurrency isn't needed.

---

### L-03: `Match.ArchiveReason` and `Match.State` Overlap

`GRACEFUL_EXIT` appears in both enums. `UNMATCH` maps to both `ArchiveReason.UNMATCH` and `State.UNMATCHED`.

---

### L-04: `User.isVerified()` Returns `Boolean` (Boxed) Instead of `boolean`

Field is `boolean` (primitive) but getter returns `Boolean` (boxed) — unnecessary autoboxing.

---

### L-08: `User.getPhotoUrls()` — Defensive Copy on Every Access

```java
return new ArrayList<>(photoUrls); // Every call creates a copy
```

**Fix:** Return `Collections.unmodifiableList()` or use immutable list internally.

---

### L-09: `DailyService.getDailyPick()` Uses Random Access on Large List

`candidates.get(random.nextInt(candidates.size()))` requires loading all candidates first.

**Fix:** Use `ORDER BY RANDOM() LIMIT 1` at database level.

---

## 5. REST API Security & Design Gaps

---

*Consolidated from 7 independent audit reports — February 6, 2026*
*Auditors: Kimmy 2.5, Grok, Opus 4.6, GPT-5 Mini, Raptor Mini, GPT-4.1*

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
1|2026-02-06 17:29:51|agent:codex|audit-group-core|Regroup audit by problem type (core logic)|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT_PROBLEMS_CORE.md
---AGENT-LOG-END---
