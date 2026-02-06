# Consolidated Codebase Audit — February 6, 2026

**Sources:** Kimmy 2.5, Grok, Opus 4.6 (core + storage/UI), GPT-5 Mini, Raptor Mini, GPT-4.1
**Scope:** Full codebase — ~119 production Java files (~19K LOC), 37 test files
**Total Unique Findings:** 75+

This audit has been split into problem-type files:

- [AUDIT_PROBLEMS_CORE.md](AUDIT_PROBLEMS_CORE.md) — Core services, business rules, concurrency, and CLI
- [AUDIT_PROBLEMS_UI.md](AUDIT_PROBLEMS_UI.md) — JavaFX controllers, ViewModels, navigation, and UX
- [AUDIT_PROBLEMS_STORAGE.md](AUDIT_PROBLEMS_STORAGE.md) — JDBI, database mapping, and query efficiency
- [AUDIT_PROBLEMS_SECURITY.md](AUDIT_PROBLEMS_SECURITY.md) — API security, auth, privacy, and crypto
<!-- ChangeStamp: 2|2026-02-06 17:29:51|agent:codex|scope:audit-group-index|Regroup audit index by problem type|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT.md -->

<!--ARCHIVE:2:agent:codex:scope:audit-group-index-->
This audit has been split into category files:

- [AUDIT_FINDINGS.md](AUDIT_FINDINGS.md) — Critical/High/Medium/Low findings
- [AUDIT_SUBSYSTEMS.md](AUDIT_SUBSYSTEMS.md) — REST API, UI/JavaFX, and Storage issues
- [AUDIT_SUMMARY.md](AUDIT_SUMMARY.md) — Strengths, priorities, and performance targets
<!-- ChangeStamp: 1|2026-02-06 17:21:41|agent:codex|scope:audit-split-index|Split audit into category files|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT.md -->
<!--/ARCHIVE-->

---

<!--ARCHIVE:1:agent:codex:scope:audit-split-index-->
# Consolidated Codebase Audit — February 6, 2026

**Sources:** Kimmy 2.5, Grok, Opus 4.6 (core + storage/UI), GPT-5 Mini, Raptor Mini, GPT-4.1
**Scope:** Full codebase — ~119 production Java files (~19K LOC), 37 test files
**Total Unique Findings:** 75+

---

## Table of Contents

1. [Critical Findings](#1-critical-findings)
2. [High-Severity Issues](#2-high-severity-issues)
3. [Medium-Severity Issues](#3-medium-severity-issues)
4. [Low-Severity Issues](#4-low-severity-issues)
5. [REST API Security & Design Gaps](#5-rest-api-security--design-gaps)
6. [UI/JavaFX Architecture Issues](#6-uijavafx-architecture-issues)
7. [Storage Layer Issues](#7-storage-layer-issues)
8. [Strengths & Positive Observations](#8-strengths--positive-observations)
9. [Recommended Fix Priority](#9-recommended-fix-priority)

---

## 1. Critical Findings

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

### C-06: `ProfileController.handleSave()` — `cleanup()` Called BEFORE `save()`

**File:** `ui/controller/ProfileController.java`
**Source:** Storage/UI Audit

```java
cleanup(); // destroys all subscriptions FIRST
viewModel.save(); // save may trigger property updates — nobody listening
NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
```

**Fix:** Call `viewModel.save()` first, then `cleanup()`, then navigate.

---

### C-07: `MatchesController.handleStartChat()` — Missing Navigation Context

**File:** `ui/controller/MatchesController.java`
**Source:** Storage/UI Audit

Navigates to CHAT view without calling `setNavigationContext()`. The `ChatController` calls `consumeNavigationContext()` expecting a `UUID`, but receives `null`. The "Message" button on every match card is effectively broken.

**Fix:** Set navigation context before navigating.

---

### C-08: `MatchesController` — 5 INDEFINITE Animations Never Stopped

**File:** `ui/controller/MatchesController.java`
**Source:** Storage/UI Audit

5 animations use `setCycleCount(Animation.INDEFINITE)` and `.play()` but have zero `.stop()` calls in the entire file. When navigating away, animations continue running, holding scene graph references and burning CPU.

**Fix:** Store animation references. Override `cleanup()` from `BaseController` to call `.stop()` on each.

---

## 2. High-Severity Issues

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

### H-09: Verification Code Uses `java.util.Random` — Not Cryptographically Secure

**File:** `core/TrustSafetyService.java`
**Source:** Grok

```java
private final Random random; // Not SecureRandom
```

`java.util.Random` is predictable and should not be used for security tokens.

**Fix:** Use `SecureRandom`.

---

### H-10: Age Calculation Timezone Sensitivity

**File:** `core/User.java`
**Source:** Grok

Age can differ by 1 day depending on timezone. A user could appear as 17 in one timezone and 18 in another, creating legal compliance issues.

**Fix:** Use consistent timezone (UTC) for age calculations.

---

### H-11: N+1 Query Patterns in Multiple Services

**Files:** `core/MatchingService.java`, `core/StandoutsService.java`, `core/MessagingService.java`, `ui/viewmodel/MatchesViewModel.java`
**Source:** Grok, Opus 4.6, Raptor Mini, Storage/UI Audit

Multiple services call `userStorage.get(id)` in loops. For 50 items → 50+ separate DB round-trips.

**Fix:** Add `UserStorage.findByIds(Set<UUID>)` batch method and use it everywhere.

---

### H-12: FX Thread Violations — `MatchesViewModel`, `LoginViewModel`, `StatsViewModel`

**Files:** Multiple ViewModels
**Source:** Storage/UI Audit

All three perform blocking storage calls directly on the FX Application Thread, causing UI freezes. Contrast with `DashboardViewModel` and `ChatViewModel` which correctly use `Thread.ofVirtual()` + `Platform.runLater()`.

**Fix:** Wrap all DB calls in virtual threads; update ObservableLists via `Platform.runLater()`.

---

### H-13: Memory Leaks — Untracked Listeners

**Files:** `ui/controller/ProfileController.java`, `ui/controller/DashboardController.java`, `ui/controller/MatchesController.java`
**Source:** Storage/UI Audit

Raw `addListener()` calls not wrapped in `addSubscription()`. BaseController.cleanup() cannot remove them. Lambda closures prevent garbage collection.

**Fix:** Wrap all listeners in `addSubscription()`.

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

### H-16: `JdbiUserStorage.readEnumSet()` — No Try-Catch on `Enum.valueOf`

**File:** `storage/jdbi/JdbiUserStorage.java`
**Source:** Storage/UI Audit

If the database contains a corrupted or legacy enum value, the entire user deserialization fails. Note: `readInterestSet()` in the same file correctly uses try-catch.

---

### H-17: `Main.java` — No Error Handling Around Handler Calls

**File:** `Main.java`
**Source:** Storage/UI Audit

Switch cases invoke handlers directly without try-catch. A `StorageException` will crash the CLI app with a stack trace.

---

### H-18: Hardcoded Password in Development

**File:** `storage/DatabaseManager.java`
**Source:** Grok

```java
private static final String DEFAULT_DEV_PASSWORD = "dev";
```

**Fix:** Require password from environment variables; fail fast if not set.

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

### M-10: `JdbiUserStorage.Mapper.readPhotoUrls()` Returns Fixed-Size List

`Arrays.asList(...)` creates a fixed-size list. Code calling `.add()` on it gets `UnsupportedOperationException`.

**Fix:** Wrap in `new ArrayList<>(...)`.

---

### M-11: `MapperHelper.readEnum()` Crashes on Invalid Enum Values

Uses `Enum.valueOf()` without try-catch. Will throw for unknown/legacy values.

---

### M-12: `ProfileController.validateHeightRange()` — Hardcoded Thresholds

Uses hardcoded `120`-`250` instead of `CONFIG.minHeightCm()` (50) and `CONFIG.maxHeightCm()` (300).

---

### M-13: `ProfileController.handleEditDealbreakers()` — Uses `HashSet` for Enums

Should use `EnumSet` per project conventions.

---

### M-14: `MatchesController` — Random Status Dots in Production

Uses random assignment for "online"/"away"/"offline" status. This is mock/demo logic showing fake presence data.

---

### M-15: `MatchesController` — Unbounded Particle Creation

Heart particles spawned every 800ms with no maximum cap. On slow devices, count can grow unboundedly.

---

### M-16: `HandlerFactory` — Missing Constructor Validation

No `Objects.requireNonNull()` checks on constructor parameters.

---

### M-17: `PreferencesViewModel.savePreferences()` — No Input Validation

No bounds checking on `minAge`, `maxAge`, `maxDistance`. Could set `minAge > maxAge` or negative distances.

---

### M-18: `PreferencesViewModel` — Incomplete `OTHER` Gender Handling

If user is interested only in `Gender.OTHER`, the code falls through to the `EVERYONE` default case, losing the preference.

---

### M-19: `ProfileViewModel.savePhoto()` — Single Background Thread Tracking

Only tracks one thread. Rapid double-clicks lose the first thread reference; `dispose()` can only interrupt the last.

---

### M-20: `DatabaseManager` — Static Mutable State Without Synchronization

`jdbcUrl` is a static mutable `String` set without synchronization. Race condition if set during connection.

---

### M-21: `NavigationService` — Singleton Context Not Thread-Safe

`navigationContext` is a plain `Object` with no synchronization. Race condition between ViewModel (background thread) and controller (FX thread).

**Fix:** Use `AtomicReference<Object>`.

---

### M-22: `StatsViewModel` — Silent Degradation with Null Storage

Single-arg constructor sets `likeStorage=null` and `matchStorage=null`. `refresh()` silently skips stat sections with no user indication.

---

### M-23: Time Handling Inconsistent

Some code uses injected `Clock` (e.g., `UndoService`), other places call `Instant.now()` directly. Reduces testability.

---

### M-24: CSV Parsing Doesn't Trim Entries

`MapperHelper.readCsvAsList()`: `"A, B, C".split(",")` produces `["A", " B", " C"]` with leading spaces.

**Fix:** Use `split("\\s*,\\s*")` or trim elements.

---

### M-25: Logs May Include PII

User names/IDs appear in log messages — privacy risk for production.

---

### M-26: `MatchQualityService` / `MessagingService` — Hard-Coded Limit Values

`MessagingService` uses literal `100` for message limits, `ProfileViewModel` hardcodes `500` for max distance.

---

### M-27: Services Throwing Instead of Returning Result Records

`MatchingService.processSwipe()` throws `IllegalStateException` — violates the Result pattern convention.

---

### M-28: Foreign Key Enforcement Unreliable

Schema and code use mismatched column names; error handling is too broad, risking silent data integrity loss.

---

### M-29: ResultSet Resource Leaks

Some storage classes do not close ResultSets properly, risking connection exhaustion.

---

## 4. Low-Severity Issues

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

### L-05: `JdbiMatchStorage.getActiveMatchesFor()` — OR-Based Query

`WHERE (user_a = :userId OR user_b = :userId)` may prevent index usage. A UNION would be faster at scale.

---

### L-06: `LoginViewModel.createUser()` — Double Save

Saves user, then activates, then saves again — two DB writes where one would suffice.

---

### L-07: `DashboardController` — Unnecessary `Objects.requireNonNull(obs)` in Listener

Scene property change callbacks in JavaFX never pass null for the observable parameter.

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

### API-01: No Authentication or Authorization 🔴

The REST API has zero authentication. Any caller can read any user's profile, like/pass as any user, read any conversation's messages, or send messages as any user.

**Impact:** Complete impersonation, data theft, manipulation.

---

### API-02: `MessagingRoutes.getMessages()` Bypasses Authorization

Calls `messagingStorage.getMessages()` directly instead of `MessagingService.getMessages()` which performs membership checks. Anyone who knows a conversation ID can read all messages.

---

### API-03: No Rate Limiting on Any Endpoint

An attacker could like every user in seconds, send thousands of messages per second, or scrape all profiles.

---

### API-04: `MatchRoutes` Bypasses Daily Limits

`likeUser()` creates a Like via `matchingService.recordLike()` without checking `dailyService.canLike()`. Only the CLI/UI processSwipe path checks daily limits.

---

### API-05: `MatchRoutes.from()` Sets `otherUserName` to "Unknown" Always

The static `from()` method hardcodes "Unknown". The instance `toSummary()` correctly looks up the name, but `likeUser()` uses the static method.

---

### API-06: No Input Sanitization

User-supplied content stored as-is with no HTML/script sanitization. XSS risk if rendered in a web frontend.

---

### API-07: No HTTPS Enforcement

Database credentials could be sent over plaintext if proxy not configured.

---

### API-08: Weak Session Management

Sessions never expire. No TTL on `AppSession`.

**Fix:** Implement JWT with expiration; refresh token rotation.

---

### API-09: No CSRF Protection

State-changing operations have no CSRF token protection.

---

### API-10: Insecure Direct Object Reference

Users may access others' conversations by guessing IDs.

---

### API-11: Sensitive Data Logging

User data may be logged in error messages without redaction.

---

## 6. UI/JavaFX Architecture Issues

### UI-01: `ImageCache` Has No Eviction Policy — Unbounded Memory Growth

The image cache grows indefinitely. High-resolution images accumulate and eventually cause `OutOfMemoryError`.

**Fix:** Use LRU cache with configurable max size, `WeakHashMap`, or `SoftReference`.

---

### UI-02: `BaseController` Animations May Never Be Stopped

Loading overlays with `INDEFINITE` animations may not be stopped on cleanup if references aren't tracked.

---

### UI-03: Navigation Context Can Be Lost

Quick navigation away and back may consume context before the target controller reads it.

---

### UI-04: `ViewModelFactory` Creates Singletons — Stale State After Logout

Cached ViewModels hold stale data from previous sessions. No `reset()` or `invalidateAll()` method.

---

### UI-05: `getFirst()` Calls — Non-Standard List API

**Source:** GPT-5 Mini, Raptor Mini

Multiple files use `list.getFirst()` which is a Java 21+ `SequencedCollection` method. While it compiles with Java 25, it's non-idiomatic and lacks empty-list guards.

**Files:** `ProfileViewModel`, `MatchQualityService`, `StandoutsService`, `UiComponents`

**Fix:** Replace with guarded `list.isEmpty() ? default : list.get(0)` or `list.stream().findFirst()`.

---

## 7. Storage Layer Issues

### ST-01: N+1 Query in `MessagingService.getConversations()`

For 50 conversations → 150-200 SQL queries (user lookup + last message + unread count per conversation).

**Fix:** Batch lookups with IN clauses → 3-4 queries total.

---

### ST-02: `DailyService.dailyPickViews` In-Memory — Lost on Restart

Users may see the same daily pick as "new" repeatedly after restart.

---

### ST-03: No Connection Pool Sizing in `DatabaseManager`

Uses `DriverManager.getConnection()` per request — no HikariCP. Acceptable for H2 embedded, problematic for remote DB.

---

### ST-04: `MapperHelper.readEnumSet()` Missing `Enum.valueOf()` Error Handling

Invalid enum values crash the entire query. Should catch and skip invalid values.

---

### ST-05: No Database Migration Tooling

Schema changes are manual Java code. No versioned migrations, no rollback capability.

**Fix:** Adopt Flyway or Liquibase.

---

## 8. Strengths & Positive Observations

### Architecture Excellence
- **Clean 3-Layer Design:** Proper separation between core business logic, storage, and UI
- **MVVM Pattern:** Well-implemented JavaFX architecture with proper viewmodel separation
- **Dependency Injection:** Clean service wiring through `ServiceRegistry`

### Code Quality
- **Excellent Test Coverage:** 84% core coverage with 736 passing tests
- **Type Safety:** Extensive use of enums, records, and compile-time validation
- **Error Handling:** Result pattern prevents exceptions from propagating to UI
- **Modern Java:** Proper use of Java 25 features and patterns

### Development Practices
- **Comprehensive Documentation:** Detailed AGENTS.md and CLAUDE.md guides
- **Code Formatting:** Automated formatting with Palantir Java Format
- **Quality Gates:** Checkstyle, PMD, and Spotless integration

### Storage Layer
- **SQL Injection Protection:** All JDBI storage uses `@Bind` / `@BindBean` parameterized queries; zero string concatenation in SQL
- **Bounded Navigation History:** Stack bounded at `MAX_HISTORY_SIZE = 20`
- **Deterministic IDs:** Match/Conversation IDs use canonical ordering consistently

### UI Layer
- **DashboardViewModel / ChatViewModel** correctly use `Thread.ofVirtual()` + `Platform.runLater()` for background DB work
- **ErrorHandler pattern** — Clean ViewModel→Controller error propagation via functional interface
- **BaseController pattern** — Good lifecycle management (when used)

---

## 9. Recommended Fix Priority

### Immediate (This Sprint)

| #  | Finding                                              | Effort |
|----|------------------------------------------------------|--------|
| 1  | C-01: SessionService lock clearing race condition    | 1 hr   |
| 2  | C-02: AppSession deadlock risk                       | 15 min |
| 3  | C-04: MessagingService canMessage inconsistency      | 5 min  |
| 4  | C-06: Reorder cleanup/save in ProfileController      | 2 min  |
| 5  | C-07: Fix handleStartChat missing context            | 5 min  |
| 6  | C-08: Stop all INDEFINITE animations in cleanup      | 30 min |
| 7  | H-01: DailyService memory leak (cleanup never fires) | 10 min |
| 8  | H-07: Daily pick reason dilution                     | 5 min  |
| 9  | H-08: SwipeSession velocity calculation              | 5 min  |
| 10 | H-17: Main.java try-catch around handlers            | 15 min |

### Short-Term (Next Sprint)

| #  | Finding                                             | Effort |
|----|-----------------------------------------------------|--------|
| 11 | C-03/C-05: MatchingService swipe/match atomicity    | 2 hr   |
| 12 | H-03: GeoUtils location checks in 3 services        | 30 min |
| 13 | H-04: Daily pick stability                          | 1 hr   |
| 14 | H-05: Achievement count — use all-time, not active  | 30 min |
| 15 | H-09: SecureRandom for verification codes           | 10 min |
| 16 | H-11: N+1 query fixes (add batch user loading)      | 2 hr   |
| 17 | H-12: Background threads for 3 ViewModels           | 1.5 hr |
| 18 | H-13: Track all listeners via addSubscription       | 30 min |
| 19 | H-14: Fix Achievement visibility, remove reflection | 20 min |
| 20 | H-15: Route all likes through daily limit check     | 15 min |
| 21 | H-16: Enum.valueOf try-catch in readEnumSet         | 10 min |

### Medium-Term (Next Month)

| #  | Finding                                                           | Effort |
|----|-------------------------------------------------------------------|--------|
| 22 | H-06: Consolidate MatchingService constructors                    | 2 hr   |
| 23 | M-01: Validate config weight sum                                  | 30 min |
| 24 | M-05: Make AppConfig injectable in Dealbreakers/ValidationService | 1 hr   |
| 25 | M-07: Cache seeker data in CandidateFinder                        | 30 min |
| 26 | UI-01: ImageCache LRU eviction                                    | 1 hr   |
| 27 | UI-04: ViewModelFactory reset on logout                           | 45 min |
| 28 | API-01-11: API security hardening (auth, rate limiting, etc.)     | 1 week |
| 29 | ST-05: Database migration tooling (Flyway)                        | 1 day  |
| 30 | M-24: CSV parsing trim fix                                        | 10 min |

### Performance Targets

| Metric               | Current     | Target                   |
|----------------------|-------------|--------------------------|
| Candidate Loading    | Unbounded   | <100ms for 50 candidates |
| Message Send         | Synchronous | <50ms, async processing  |
| Daily Pick           | O(n) memory | O(1) with caching        |
| Stats Calculation    | Full recalc | Incremental, <10ms       |
| Database Connections | Per-request | Pooled, max 50           |
| Cache Hit Rate       | 0%          | >80% for hot data        |

---

*Consolidated from 7 independent audit reports — February 6, 2026*
*Auditors: Kimmy 2.5, Grok, Opus 4.6, GPT-5 Mini, Raptor Mini, GPT-4.1*
<!--/ARCHIVE-->

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append "\:CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
1|2026-02-06 17:21:41|agent:codex|audit-split-index|Split audit into category files|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT.md
2|2026-02-06 17:29:51|agent:codex|audit-group-index|Regroup audit index by problem type|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT.md
---AGENT-LOG-END---
