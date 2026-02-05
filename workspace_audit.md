# Workspace Audit Report

**Date:** 2026-02-06
**Auditor:** GitHub Copilot (Claude Opus 4.6)
**Scope:** Full codebase read-only audit — functional, correctness, and architectural analysis
**Repository:** Date_Program (Java 25 + JavaFX 25 dating app)

---

## Executive Summary

| Metric                | Value                              |
|-----------------------|------------------------------------|
| Java Files            | 167 (130 main + 37 test)           |
| Total Lines           | 42,852                             |
| Code Lines            | 31,561                             |
| Comments              | 4,747                              |
| Blanks                | 6,544                              |
| Tests                 | 729 passing, 0 failures, 0 skipped |
| Compile Errors        | 0                                  |
| Critical Issues Found | 9                                  |
| High Issues Found     | 16                                 |
| Medium Issues Found   | 30                                 |
| Low Issues Found      | 18                                 |
| **Total Issues**      | **73**                             |

The codebase compiles cleanly and all 729 tests pass. However, the audit uncovered 9 critical correctness bugs, 16 high-severity issues, and 48 medium/low findings spanning thread safety, configuration inconsistencies, memory leaks, Result pattern violations, and architectural drift.

---

## Top 25 Highest-Priority Issues

| #  | Severity     | Category             | File                                                    | Description                                                                                                                                              |
|----|--------------|----------------------|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | **CRITICAL** | Thread Safety        | `core/AppSession.java:20`                               | `currentUser` field is not `volatile` — stale reads across threads                                                                                       |
| 2  | **CRITICAL** | Thread Safety        | `core/AppSession.java:~58`                              | `isActive()` reads `currentUser` twice (TOCTOU): null-check then `.getState()` — NPE if `logout()` races between reads                                   |
| 3  | **CRITICAL** | Thread Safety        | `core/User.java`                                        | `User` is mutable + shared via `AppSession.getCurrentUser()` — concurrent setter calls from UI and background threads with no synchronization            |
| 4  | **CRITICAL** | Hardcoded Value      | `core/User.java:496-508`                                | Photo limit hardcoded to `2` instead of `CONFIG.maxPhotos()`                                                                                             |
| 5  | **CRITICAL** | Config Inconsistency | `core/AppConfig.java:156` vs `core/Preferences.java:88` | `maxInterests` defaults to `5` but `Interest.MAX_PER_USER = 10`                                                                                          |
| 6  | **CRITICAL** | Hardcoded Value      | `core/User.java:~460`                                   | `isComplete()` checks `minAge >= 18` hardcoded instead of `CONFIG.minAge()`                                                                              |
| 7  | **CRITICAL** | Memory Leak          | `core/SessionService.java:~25`                          | `userLocks` ConcurrentHashMap grows unbounded — never cleaned up                                                                                         |
| 8  | **CRITICAL** | Memory Leak          | `core/DailyService.java:~30`                            | `dailyPickViews` ConcurrentHashMap grows unbounded in production                                                                                         |
| 9  | **CRITICAL** | Error Propagation    | `core/AppSession.java:~55`                              | `notifyListeners()` doesn't catch exceptions — one bad listener kills all                                                                                |
| 10 | **HIGH**     | Memory Leak          | `core/AppSession.java`                                  | `listeners` (CopyOnWriteArrayList) grows unboundedly — no `removeListener()` method exists; every navigation adds listeners that are never removed       |
| 11 | **HIGH**     | EnumSet Crash        | `core/User.java:~287`                                   | `getInterestedIn()` calls `EnumSet.copyOf(interestedIn)` without `isEmpty()` check — crashes on empty EnumSet (unlike `getInterests()` which does check) |
| 12 | **HIGH**     | Null Safety          | `core/MatchQualityService.java`                         | `computeQuality()` dereferences `userStorage.get(id)` which can return null if user was deleted — NPE on `.getLatitude()`                                |
| 13 | **HIGH**     | Storage API          | `core/storage/UserStorage.java`                         | `get()` returns nullable `User` while every other storage interface returns `Optional<T>` — forces 14+ null-checks, persistent NPE risk                  |
| 14 | **HIGH**     | Architecture         | `core/RelationshipTransitionService.java`               | Throws `TransitionValidationException` instead of returning Result records                                                                               |
| 15 | **HIGH**     | Architecture         | `core/MessagingService.java:96-101`                     | `getMessages()` throws exceptions instead of returning Result                                                                                            |
| 16 | **HIGH**     | Spurious Side Effect | `core/MessagingService.java:86`                         | `userStorage.save(sender)` called for no reason after sending message                                                                                    |
| 17 | **HIGH**     | Unsafe Constructors  | `core/TrustSafetyService.java:22-26`                    | No-arg and 2-arg constructors allow null core dependencies                                                                                               |
| 18 | **HIGH**     | Inconsistent API     | `core/MatchingService.java`                             | 4 constructors with inconsistent null-checking (some `requireNonNull`, some not)                                                                         |
| 19 | **HIGH**     | Thread Safety        | `core/AppSession.java:32-33`                            | `setCurrentUser` is not synchronized — race with `getCurrentUser`                                                                                        |
| 20 | **HIGH**     | Validation Gap       | `core/AppConfig.java`                                   | Match quality weights not validated to sum to 1.0                                                                                                        |
| 21 | **HIGH**     | Missing Cleanup      | `core/PerformanceMonitor.java`                          | Metrics map grows unbounded if timer names are dynamic                                                                                                   |
| 22 | **HIGH**     | Error Handling       | `core/MatchingService.java:128`                         | Catches `RuntimeException _` silently in `recordLike()` — swallows errors                                                                                |
| 23 | **HIGH**     | Config Mismatch      | `core/User.java` vs `core/AppConfig.java`               | `User` validates `maxDistanceKm` against CONFIG but `setPhotoUrls`/`addPhotoUrl` don't                                                                   |
| 24 | **HIGH**     | Architecture         | `core/TrustSafetyService.java`                          | `report()` auto-blocks + auto-bans in same call — violates single-responsibility                                                                         |
| 25 | **MEDIUM**   | Unbounded Query      | `core/MessagingService.java:119`                        | `getConversations()` loads ALL conversations with no pagination                                                                                          |

---

## Per-Package Analysis

### Package: `datingapp.core` (Domain Models)

#### `AppSession.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                                                                                       |
|--------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| TSA-01 | CRITICAL | 20      | `private User currentUser` — not `volatile`. When JavaFX UI thread reads and CLI/background thread writes, stale values are possible. CopyOnWriteArrayList for listeners does not solve this.                                                                                     |
| TSA-02 | CRITICAL | ~58     | `isActive()` reads `currentUser` twice without synchronization: `return currentUser != null && currentUser.getState() == UserState.ACTIVE`. Between the null-check and `.getState()`, another thread can call `logout()` setting `currentUser = null`, causing NPE (TOCTOU race). |
| TSA-03 | CRITICAL | 55-58   | `notifyListeners()` iterates listeners without try-catch. If any listener throws, remaining listeners are skipped and the calling thread gets an unexpected exception.                                                                                                            |
| TSA-04 | HIGH     | 32-34   | `setCurrentUser()` performs read-then-write (set field + notify) without synchronization. Concurrent `setCurrentUser` calls can interleave: field set to user A, but listeners notified with user B.                                                                              |
| TSA-05 | HIGH     | N/A     | `listeners` (CopyOnWriteArrayList) grows unboundedly — no `removeListener()` method exists. Controllers register listeners on every navigation but can never remove them, causing a memory leak.                                                                                  |
| TSA-06 | MEDIUM   | 61      | `reset()` clears user and listeners non-atomically. Between `currentUser = null` and `listeners.clear()`, a listener could fire on null.                                                                                                                                          |

**Suggested fix:** Make `currentUser` volatile, cache in local var for multi-read methods (`User user = this.currentUser`), wrap `setCurrentUser` in synchronized block, add try-catch in `notifyListeners`, and add `removeListener()`.

```java
// Fix for isActive() TOCTOU:
public boolean isActive() {
    User user = this.currentUser; // single read
    return user != null && user.getState() == UserState.ACTIVE;
}
```

#### `User.java`

| Issue  | Severity | Line(s)  | Description                                                                                                                                                                                                                                             |
|--------|----------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| USR-01 | CRITICAL | N/A      | `User` is a mutable object shared via `AppSession.getInstance().getCurrentUser()`. Multiple threads (UI thread, background service threads) can call setters concurrently with no synchronization and no defensive copying on `getCurrentUser()`.       |
| USR-02 | CRITICAL | 496-508  | `setPhotoUrls()` and `addPhotoUrl()` hardcode `Maximum 2 photos allowed` instead of using `CONFIG.maxPhotos()`. If AppConfig is changed to allow 6 photos (as documented), User rejects them.                                                           |
| USR-03 | CRITICAL | ~455-465 | `isComplete()` hardcodes `minAge >= 18`. If `CONFIG.minAge()` is changed to 21 (e.g., for alcohol-related app variant), profile completion check is wrong.                                                                                              |
| USR-04 | HIGH     | ~287     | `getInterestedIn()` calls `EnumSet.copyOf(interestedIn)` without checking `isEmpty()` first. `EnumSet.copyOf()` throws `IllegalArgumentException` on empty collections. `getInterests()` in the same file correctly checks `isEmpty()` — inconsistency. |
| USR-05 | MEDIUM   | ~460     | `isComplete()` hardcodes `getPhotoUrls().size() >= 2` instead of using `CONFIG.minPhotos()` or `CONFIG.maxPhotos()`. Separate from the addPhotoUrl limit — this is the completion check.                                                                |
| USR-06 | LOW      | N/A      | `isVerified()` returns `Boolean` (boxed) instead of `boolean` (primitive), risking NPE on unbox at call sites.                                                                                                                                          |
| USR-07 | LOW      | 244-249  | `StorageBuilder.build()` doesn't validate any fields. A storage record could have `null` name or `null` id if the builder was misused.                                                                                                                  |
| USR-08 | LOW      | 530      | `ProfileNote` validates `updatedAt.isBefore(createdAt)` but `withContent()` always creates a new `Instant.now()` for updatedAt which could theoretically be before createdAt on clock skew.                                                             |

#### `Match.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                                            |
|--------|----------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MAT-01 | MEDIUM   | 184     | `gracefulExit()` doesn't check `involves(initiatorId)` — unlike `unmatch()` and `block()` which do. A non-participant could graceful-exit a match.                                                                                     |
| MAT-02 | LOW      | 167     | `transitionToFriends()` doesn't set `endedAt`/`endedBy` which is documented as intentional, but `endReason` is also not set, creating inconsistent state. FRIENDS matches have null `endReason` but other terminal states have it set. |

#### `Messaging.java`

| Issue  | Severity | Line(s) | Description                                                                                                                   |
|--------|----------|---------|-------------------------------------------------------------------------------------------------------------------------------|
| MSG-01 | MEDIUM   | N/A     | `Conversation.archive()` takes an `ArchiveReason` from `Match` — tight coupling between Conversation and Match domain models. |

#### `Preferences.java` / `AppConfig.java`

| Issue  | Severity | Line(s)                         | Description                                                                                                                                                                                                                                                                                    |
|--------|----------|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CFG-01 | CRITICAL | AppConfig:156 vs Preferences:88 | `AppConfig.defaults()` sets `maxInterests=5` but `Interest.MAX_PER_USER=10`. Code in `User.setInterests()` uses `Interest.MAX_PER_USER`, ignoring AppConfig entirely. Users can add 10 interests even if config says 5.                                                                        |
| CFG-02 | HIGH     | AppConfig compact constructor   | Match quality weights (`distanceWeight` + `ageWeight` + `interestWeight` + `lifestyleWeight` + `paceWeight` + `responseWeight`) are validated individually as non-negative but never validated to sum to 1.0. Custom configs could have weights summing to 0.5 or 2.0, producing wrong scores. |

#### `DailyPick.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                      |
|--------|----------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DPK-01 | LOW      | N/A     | Record with `alreadySeen` flag — this is a mutable concept encoded in an immutable record. If `markDailyPickViewed()` is called after the pick is returned, the returned record still shows `alreadySeen=false`. |

---

### Package: `datingapp.core` (Services)

#### `MatchingService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                                                                                                   |
|--------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MTS-01 | HIGH     | 41-97   | Four constructors with inconsistent null-checking: basic constructor accepts `likeStorage`/`matchStorage` via `requireNonNull` but sets `userStorage=null`, `blockStorage=null` without warning. Other constructors vary.                                                                     |
| MTS-02 | HIGH     | 128     | `catch (RuntimeException _)` silently swallows storage errors during match save. If `matchStorage.save()` fails for reasons other than duplicate key (e.g., DB down), the match is lost silently.                                                                                             |
| MTS-03 | MEDIUM   | 139-148 | `processSwipe()` calls `dailyService.canLike()` and `undoService.recordSwipe()` but doesn't check if these services are null. The 7-arg constructor requires them, but the other constructors don't set them. Calling `processSwipe()` with a 2-arg or 4-arg constructed instance throws NPE. |
| MTS-04 | LOW      | 110-111 | `recordLike()` calls `sessionService.recordSwipe()` with `matched=false` (hardcoded), then later detects a match. The session record has wrong `matched` flag.                                                                                                                                |

#### `MessagingService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                                   |
|--------|----------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MGS-01 | HIGH     | 86      | `userStorage.save(sender)` called at end of `sendMessage()` — sender is never modified, so this is a spurious database write on every message send. Performance waste and potential for subtle bugs if save has side effects. |
| MGS-02 | HIGH     | 96-101  | `getMessages()` throws `IllegalArgumentException` for invalid limit/offset instead of returning empty list or Result record. Violates the project's "services never throw" convention.                                        |
| MGS-03 | MEDIUM   | 119-132 | `getConversations()` loads ALL conversations for a user with no pagination. For users with many matches, this could be very slow.                                                                                             |

#### `RelationshipTransitionService.java`

| Issue  | Severity | Line(s)     | Description                                                                                                                                                                   |
|--------|----------|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RTS-01 | HIGH     | All methods | Throws `TransitionValidationException` instead of returning Result records, violating the codebase convention. Every caller needs try-catch instead of checking `.success()`. |
| RTS-02 | MEDIUM   | Constructor | No `Objects.requireNonNull()` on constructor parameters (`matchStorage`, `socialStorage`, `messagingStorage`). NPE at runtime if null passed.                                 |

#### `TrustSafetyService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                                                                                               |
|--------|----------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| TSS-01 | HIGH     | 22-26   | No-arg constructor `TrustSafetyService()` and 2-arg constructor leave `reportStorage`, `userStorage`, `blockStorage`, `config` as null. Any method that calls `ensureReportDependencies()` throws, but `block()`, `unblock()`, `getBlockedUsers()` check individually and inconsistently. |
| TSS-02 | MEDIUM   | 93-100  | `report()` auto-blocks the reported user AND checks for auto-ban in the same call. This means a report always blocks regardless of whether the report is valid — no moderation review step.                                                                                               |
| TSS-03 | LOW      | 62      | `generateVerificationCode()` uses `java.util.Random` (not `SecureRandom`). For a verification code, this is predictable if the seed is known.                                                                                                                                             |

#### `SessionService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                      |
|--------|----------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SES-01 | CRITICAL | ~25     | `userLocks` ConcurrentHashMap never removes entries. Every user who has ever swiped adds an entry. Over time this is an unbounded memory leak in a long-running server.          |
| SES-02 | MEDIUM   | N/A     | `getTodaysSessions()` uses `ZoneId.systemDefault()` to compute start-of-day instead of a configurable timezone. Different server timezones produce different "today" boundaries. |
| SES-03 | MEDIUM   | 76      | Lambda `_ -> new Object()` — uses underscore identifier. While valid in Java 25 with preview, this is flagged by some linters and is unusual.                                    |

#### `DailyService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                                                                       |
|--------|----------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DLY-01 | CRITICAL | ~30     | `dailyPickViews` ConcurrentHashMap grows unbounded. `cleanupOldDailyPickViews()` exists but is never called automatically — requires external scheduled invocation. In a long-running app, this leaks memory for every user who views a daily pick.               |
| DLY-02 | MEDIUM   | 85-105  | `getDailyPick()` deterministic selection uses `today.toEpochDay() + seeker.getId().hashCode()` as seed. The same user gets the same pick all day, which is intentional, but `hashCode()` can collide — two different users could get the same seed and same pick. |
| DLY-03 | LOW      | 130-155 | `generateReason()` always adds 5 generic reasons to the list regardless of matches, then picks randomly. Result: specific reasons like "Lives nearby!" can be overridden by generic "Why not give them a chance!" — diluting meaningful reasons.                  |

#### `MatchQualityService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                                             |
|--------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MQS-01 | HIGH     | N/A     | `computeQuality()` calls `userStorage.get(userAId)` which returns nullable `User`. If the user was deleted between match creation and quality computation, `userA` or `userB` will be `null`, causing NPE on `userA.getLatitude()` etc. |
| MQS-02 | MEDIUM   | N/A     | `calculateResponseScore()` hardcodes time thresholds (1h, 24h, 72h, 168h, 720h) instead of using `config.responseTimeExcellentHours()` / `config.responseTimeGreatHours()` / `config.responseTimeGoodHours()`.                          |
| MQS-03 | LOW      | N/A     | `LOW_PACE_COMPATIBILITY_THRESHOLD` constant (`30`) should use `config.paceCompatibilityThreshold()` instead of a hardcoded value.                                                                                                       |

#### `AchievementService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                                 |
|--------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ACH-01 | MEDIUM   | N/A     | `isSelective()` hardcodes `likeRatio < 0.20` threshold. Should use a config parameter like `config.selectiveRatioThreshold()`.                                                                                              |
| ACH-02 | MEDIUM   | N/A     | `isOpenMinded()` hardcodes `likeRatio > 0.60` threshold. Should use a config parameter.                                                                                                                                     |
| ACH-03 | MEDIUM   | N/A     | Both `isSelective()` and `isOpenMinded()` hardcode `totalSwipes < 50` minimum threshold instead of `config.minSwipesForBehaviorAchievement()`.                                                                              |
| ACH-04 | MEDIUM   | N/A     | Achievement milestone thresholds (1, 5, 10, 25, 50 matches) are hardcoded instead of using `config.achievementMatchTier1()` through `config.achievementMatchTier5()`.                                                       |
| ACH-05 | LOW      | N/A     | Constructor accepts `AppConfig config` parameter and calls `Objects.requireNonNull(config)`, but **never assigns it to a field**. The config is validated then discarded — all hardcoded thresholds could use it but don't. |

#### `StandoutsService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                       |
|--------|----------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| STO-01 | MEDIUM   | N/A     | `calculateActivityScore()` hardcodes time thresholds (1h, 24h, 72h, 168h, 720h) — identical to `MatchQualityService`. Should share config or deduplicate logic.   |
| STO-02 | MEDIUM   | N/A     | `generateReason()` hardcodes `interests.sharedCount() >= 3` instead of `config.minSharedInterests()` and `distanceKm < 5` instead of `config.nearbyDistanceKm()`. |

#### `StatsService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                 |
|--------|----------|---------|---------------------------------------------------------------------------------------------------------------------------------------------|
| STS-01 | MEDIUM   | N/A     | `getOrComputeStats()` hardcodes 24-hour staleness check for cached stats. Should use a config parameter like `config.statsCacheTtlHours()`. |

#### `UndoService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                      |
|--------|----------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| UND-01 | MEDIUM   | 110-115 | When `transactionExecutor` is null, `likeStorage.delete()` and `matchStorage.delete()` are non-atomic. If the app crashes between the two deletes, data is inconsistent (like deleted but orphan match remains). |
| UND-02 | LOW      | 85      | `recordSwipe()` uses `Instant.now(clock)` but `undo()` also uses `Instant.now(clock)` for expiry check. If clock is injected as a fixed clock for testing, the expiry window is always 0 or negative.            |

#### `CleanupService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                     |
|--------|----------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| CLN-01 | MEDIUM   | N/A     | (Based on search) `CleanupService` exists but is not wired into `ServiceRegistry` or called on any schedule. Dead code unless manually invoked. |

#### `ProfileCompletionService.java`

| Issue  | Severity | Line(s) | Description                                                                                                                              |
|--------|----------|---------|------------------------------------------------------------------------------------------------------------------------------------------|
| PCS-01 | MEDIUM   | N/A     | Throws `IllegalArgumentException` for null user instead of returning 0% or a Result — inconsistent with services-never-throw convention. |
| PCS-02 | MEDIUM   | N/A     | `scorePreferences()` hardcodes `user.getMinAge() >= 18` and `user.getMaxAge() <= 120` instead of `CONFIG.minAge()` / `CONFIG.maxAge()`.  |

#### `PerformanceMonitor.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                         |
|--------|----------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| PMN-01 | HIGH     | N/A     | Static `ConcurrentHashMap` of metrics — grows unbounded if timer names include dynamic data (e.g., user IDs in timer names).                                                        |
| PMN-02 | MEDIUM   | N/A     | `OperationMetrics.record()` is `synchronized`, but `getMinMs()` / `getMaxMs()` / `getAvgMs()` are NOT synchronized. Readers can see partially-updated state (stale min/max values). |

---

#### `Dealbreakers.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                |
|--------|----------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DLB-01 | MEDIUM   | N/A     | Height validation in builder hardcodes bounds (`minHeightCm < 100`, `maxHeightCm > 250`) instead of using `CONFIG.minHeightCm()` / `CONFIG.maxHeightCm()`. |

#### `AppBootstrap.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                                                                                                                           |
|--------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ABT-01 | MEDIUM   | N/A     | `getServices()` reads `services` field without synchronization. Although `initialize()` is `synchronized`, a thread calling `getServices()` without calling `initialize()` first could see a partially-constructed object due to JMM visibility rules. Field should be `volatile` or method should be `synchronized`. |

#### `ConfigLoader.java`

| Issue  | Severity | Line(s) | Description                                                                                                                                                                                                                               |
|--------|----------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CFL-01 | MEDIUM   | N/A     | Imports `com.fasterxml.jackson.databind.JsonNode` and `ObjectMapper` in `core/` package. This is a framework dependency that violates the "zero framework imports in core/" rule. Should be moved to a utility or infrastructure package. |

---

### Package: `datingapp.core.storage` (Storage Interfaces)

| Issue  | Severity | File                       | Description                                                                                                                                                                                                                                                                      |
|--------|----------|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| STI-01 | MEDIUM   | `SwipeSessionStorage.java` | Contains `throw new` statements — storage interfaces shouldn't define default methods that throw.                                                                                                                                                                                |
| STI-02 | HIGH     | `UserStorage.java`         | `get(UUID)` returns nullable `User` instead of `Optional<User>`. Every other storage interface (`MatchStorage.get()`, `MessagingStorage.getConversation()`, etc.) returns `Optional<T>`. This inconsistency forces 14+ null-checks across services and is a persistent NPE risk. |

---

### Package: `datingapp.storage` (JDBI Implementations)

#### `DatabaseManager.java`

| Issue  | Severity | Description                                                                                                                                                                                   |
|--------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DBM-01 | MEDIUM   | Schema creation uses `CREATE TABLE IF NOT EXISTS` but column additions use `ALTER TABLE ADD COLUMN IF NOT EXISTS` — column type changes are never applied, so schema evolution is incomplete. |
| DBM-02 | LOW      | Database password hardcoded as `"dev"` in source. Environment variable `DATING_APP_DB_PASSWORD` is documented but not actually read anywhere in code.                                         |

#### `JdbiUserStorage.java`

| Issue  | Severity | Description                                                                                                                                                                                                      |
|--------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| JUS-01 | MEDIUM   | `ALL_COLUMNS` constant may drift from actual schema. If a column is added to the schema but not to `ALL_COLUMNS`, queries silently return null for that field.                                                   |
| JUS-02 | LOW      | Enum deserialization uses `Enum.valueOf()` which throws `IllegalArgumentException` if DB contains an unknown enum value (e.g., after enum refactoring). Should use `MapperHelper.readEnum()` which handles this. |

#### `MapperHelper.java`

| Issue  | Severity | Description                                                                                                                                        |
|--------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| MPH-01 | LOW      | `readEnum()` logs a warning and returns null for unknown values. This is graceful but callers may not handle the null, leading to NPEs downstream. |

#### `UserBindingHelper.java`

| Issue  | Severity | Description                                                                                                                               |
|--------|----------|-------------------------------------------------------------------------------------------------------------------------------------------|
| UBH-01 | LOW      | Redundant null checks: `user.getInterestedIn()` can never be null (getter always returns defensive copy) but the helper checks `== null`. |

---

### Package: `datingapp.ui` (JavaFX UI Layer)

#### `DatingApp.java`

| Issue  | Severity | Description                                                                                                                                                         |
|--------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| UIA-01 | MEDIUM   | `start()` method calls `AppBootstrap.initialize()` but doesn't handle initialization failure gracefully — exception would crash the app with no user-visible error. |

#### `NavigationService.java`

| Issue  | Severity | Description                                                                                                                                                   |
|--------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NAV-01 | MEDIUM   | `navigationContext` field is set-and-consume pattern without synchronization. If navigation happens from a background thread callback, context could be lost. |
| NAV-02 | LOW      | `consumeNavigationContext()` returns `Object` — no type safety. Callers must cast with `instanceof`, which is fragile when view expectations change.          |

#### `ViewModelFactory.java`

| Issue  | Severity | Description                                                                                                                                                                       |
|--------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| VMF-01 | MEDIUM   | ViewModels are singletons — shared across view instances. If a view is navigated to, away from, and back, the same ViewModel retains stale state (observable lists, error flags). |

#### Controllers (`ui/controller/`)

| Issue  | Severity | File                      | Description                                                                                                                              |
|--------|----------|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| CTL-01 | MEDIUM   | `ChatController.java`     | Messages loaded on background thread but list updates may not always be wrapped in `Platform.runLater`.                                  |
| CTL-02 | MEDIUM   | `MatchingController.java` | Swipe animations may fire after the controller is cleaned up if the animation callback runs after navigation.                            |
| CTL-03 | LOW      | `ProfileController.java`  | Birth date picker and photo URL inputs exist in FXML but may not sync properly with ViewModel if user navigates away before save.        |
| CTL-04 | LOW      | `BaseController.java`     | `cleanup()` clears subscriptions list but doesn't null out references to services — controllers may still hold references after cleanup. |

#### ViewModels (`ui/viewmodel/`)

| Issue | Severity | File                      | Description                                                                                                                           |
|-------|----------|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| VM-01 | MEDIUM   | `DashboardViewModel.java` | Calls `Platform.runLater` 3 times — background work may update UI properties outside FX thread *before* the runLater executes.        |
| VM-02 | MEDIUM   | `MatchingViewModel.java`  | Calls `Platform.runLater` 3 times — same risk as above. Loading state toggled on FX thread but data may be read on background thread. |
| VM-03 | MEDIUM   | `ChatViewModel.java`      | Calls `Platform.runLater` 2 times — messages list modifications must ALL be on FX thread or wrapped in runLater.                      |
| VM-04 | LOW      | `LoginViewModel.java`     | Loads all users eagerly on initialization — no lazy loading for large user databases.                                                 |

#### UI Utilities (`ui/util/`)

| Issue  | Severity | File                     | Description                                                                                                                                          |
|--------|----------|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| UIU-01 | MEDIUM   | `ConfettiAnimation.java` | `AnimationTimer.start()` called but `stop()` only called on explicit cleanup. If the popup is closed without calling stop, the timer leaks until GC. |
| UIU-02 | LOW      | `ImageCache.java`        | Cache size is unbounded — loading many profile photos could consume excessive memory.                                                                |
| UIU-03 | LOW      | `Toast.java`             | Toast notifications stack vertically — if many errors fire rapidly, toasts could overflow the screen.                                                |

---

### Package: `datingapp.app.cli` (CLI Layer)

| Issue  | Severity | File                   | Description                                                                                                                                                        |
|--------|----------|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CLI-01 | MEDIUM   | `HandlerFactory.java`  | Lazy handler creation is not thread-safe — if called concurrently (unlikely in CLI but possible), handlers could be created twice.                                 |
| CLI-06 | MEDIUM   | `CandidateFinder.java` | `hasLocation()` checks `lat != 0 && lon != 0`. Coordinates (0°, 0°) is a valid location in the Gulf of Guinea — users at null island are incorrectly filtered out. |
| CLI-02 | MEDIUM   | `MatchingHandler.java` | Calls `processSwipe()` which requires 7-arg `MatchingService` constructor. If wired with simpler constructor, NPE occurs.                                          |
| CLI-03 | LOW      | `InputReader.java`     | No input sanitization — user input passed directly to domain methods. While domain validates, CLI could provide better error messages.                             |
| CLI-04 | LOW      | `ProfileHandler.java`  | Calls `EnumSet.copyOf()` on user selections — if selection is empty, this throws. Should use `EnumSet.noneOf()` for empty case.                                    |
| CLI-05 | LOW      | Various                | `NumberFormatException` handling varies — some handlers catch it, others don't, leading to inconsistent UX.                                                        |

---

### Package: `datingapp.app.api` (REST API Layer)

| Issue  | Severity | File                   | Description                                                                                                                       |
|--------|----------|------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| API-01 | HIGH     | `RestApiServer.java`   | No authentication middleware — all endpoints are publicly accessible. Any user can act as any other user by providing their UUID. |
| API-02 | HIGH     | `UserRoutes.java`      | User creation accepts arbitrary UUIDs — no validation that the caller has authority to create users.                              |
| API-03 | MEDIUM   | `MatchRoutes.java`     | Match endpoints don't verify the requesting user is part of the match — information disclosure risk.                              |
| API-04 | MEDIUM   | `MessagingRoutes.java` | Message sending trusts `senderId` from request body — should come from authenticated session.                                     |
| API-05 | LOW      | All routes             | No rate limiting — susceptible to abuse.                                                                                          |
| API-06 | LOW      | All routes             | No input size limits on request bodies — large payloads could cause OOM.                                                          |

---

### Test Utilities (`testutil/`)

| Issue  | Severity | File                   | Description                                                                                                                                                                                  |
|--------|----------|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| TST-01 | MEDIUM   | `TestStorages.java`    | In-memory test storages may drift from actual storage interface changes. When a new method is added to a storage interface, the test storage must be updated manually.                       |
| TST-02 | LOW      | `TestUserFactory.java` | Factory creates users with default location (0,0) — which `CandidateFinder.hasLocation()` treats as "no location". Tests using factory users may get unexpected distance filtering behavior. |
| TST-03 | LOW      | Various tests          | No tests for REST API routes — entire API layer is untested.                                                                                                                                 |

---

## Cross-Cutting Concerns

### 1. Thread Safety
- `AppSession` is thread-unsafe: used from JavaFX thread, background threads, and CLI thread
- `SessionService.userLocks` never cleaned — memory leak
- `DailyService.dailyPickViews` never auto-cleaned — memory leak
- `PerformanceMonitor` static metrics map — unbounded

### 2. Result Pattern Adherence
Services that correctly use Result pattern:
- `MessagingService.SendResult` ✅
- `MatchingService.SwipeResult` ✅
- `UndoService.UndoResult` ✅
- `TrustSafetyService.ReportResult`, `BlockResult` ✅
- `SessionService.SwipeResult` ✅

Services that violate Result pattern:
- `RelationshipTransitionService` — throws `TransitionValidationException` ❌
- `MessagingService.getMessages()` — throws `IllegalArgumentException` ❌
- `ProfileCompletionService` — throws `IllegalArgumentException` ❌
- `TrustSafetyService.block()/unblock()/getBlockedUsers()` — throws `IllegalStateException` ❌

### 3. Configuration Consistency
| Config Field    | AppConfig Default | Actual Code Behavior             | Match?                       |
|-----------------|-------------------|----------------------------------|------------------------------|
| `maxPhotos`     | 2                 | User.java hardcodes 2            | ⚠ Fragile — not using CONFIG |
| `maxInterests`  | 5                 | `Interest.MAX_PER_USER = 10`     | ❌ Inconsistent               |
| `minAge`        | 18                | `User.isComplete()` hardcodes 18 | ⚠ Fragile — not using CONFIG |
| `maxBioLength`  | 500               | `ProfileNote.MAX_LENGTH = 500`   | ✅ (but separate constants)   |
| `maxDistanceKm` | 500               | User validates against CONFIG    | ✅                            |
| `minHeightCm`   | 50                | User validates against CONFIG    | ✅                            |
| `maxHeightCm`   | 300               | User validates against CONFIG    | ✅                            |

### 4. Constructor Injection Compliance

| File                            | Status                                                                                       |
|---------------------------------|----------------------------------------------------------------------------------------------|
| `RelationshipTransitionService` | **MISSING** all `Objects.requireNonNull()` — 3 params unvalidated                            |
| `TrustSafetyService`            | **INCONSISTENT** — 6-arg constructor validates 2 of 7 params; 2 constructors leave deps null |
| `MatchingService`               | **PARTIAL** — 7-arg constructor validates all, but 2-arg and 4-arg leave services null       |
| All other services              | ✅ Correct                                                                                    |

### 5. Architecture — Framework Imports in `core/`

| File                      | Import                             | Severity                            |
|---------------------------|------------------------------------|-------------------------------------|
| `ConfigLoader.java`       | `com.fasterxml.jackson.databind.*` | MEDIUM — Jackson is a framework dep |
| `CandidateFinder.java`    | `org.slf4j.*`                      | LOW — logging, pragmatic exception  |
| `PerformanceMonitor.java` | `org.slf4j.*`                      | LOW — logging, pragmatic exception  |
| `TrustSafetyService.java` | `org.slf4j.*`                      | LOW — logging, pragmatic exception  |

### 6. Config Misuse Cluster (11 instances)

Hardcoded thresholds scattered across services that should use `AppConfig`:

| Service                    | What's Hardcoded                                    | Config Field Available                       |
|----------------------------|-----------------------------------------------------|----------------------------------------------|
| `AchievementService`       | Selective ratio `0.20`, open-minded `0.60`          | (missing — needs new config field)           |
| `AchievementService`       | `totalSwipes < 50` minimum                          | `minSwipesForBehaviorAchievement()`          |
| `AchievementService`       | Milestone thresholds (1, 5, 10, 25, 50)             | `achievementMatchTier1()` through `5()`      |
| `MatchQualityService`      | Response time thresholds (1h, 24h, 72h, 168h, 720h) | `responseTimeExcellentHours()` etc.          |
| `MatchQualityService`      | Pace compatibility threshold `30`                   | `paceCompatibilityThreshold()`               |
| `StandoutsService`         | Same time thresholds + distance/interest config     | `nearbyDistanceKm()`, `minSharedInterests()` |
| `StatsService`             | Cache staleness `24h`                               | (missing — needs new config field)           |
| `ProfileCompletionService` | Age bounds `18`, `120`                              | `minAge()`, `maxAge()`                       |
| `Dealbreakers`             | Height bounds `100cm`, `250cm`                      | `minHeightCm()`, `maxHeightCm()`             |
| `User`                     | Photo limit `2`, min age `18`                       | `maxPhotos()`, `minAge()`                    |

### 7. Incomplete Feature Wiring
- `CleanupService` exists but is not registered in `ServiceRegistry` or scheduled
- `ConfigLoader` exists but `AppBootstrap.initialize()` doesn't use it
- `PerformanceMonitor` records metrics but has no reporting mechanism beyond log output
- `StandoutsService` and `Standout` exist but may not be fully integrated

---

## Prioritized Action List

### Priority 1 — Fix Now (Correctness Bugs)

1. **Make `AppSession.currentUser` volatile** and synchronize `setCurrentUser()` — prevents stale reads across threads
2. **Fix `AppSession.isActive()` TOCTOU race** — cache `currentUser` in local variable before double-read
3. **Fix `User.getInterestedIn()` EnumSet crash** — add `isEmpty()` check before `EnumSet.copyOf()`, matching `getInterests()` pattern
4. **Use `CONFIG.maxPhotos()`** in `User.setPhotoUrls()` and `addPhotoUrl()` instead of hardcoded `2`
5. **Reconcile `maxInterests` vs `MAX_PER_USER`**: Either change `AppConfig.maxInterests` default to 10, or make `User.setInterests()` use `CONFIG.maxInterests()`
6. **Use `CONFIG.minAge()`** in `User.isComplete()` instead of hardcoded `18`
7. **Add try-catch** in `AppSession.notifyListeners()` to prevent one bad listener from killing all notifications
8. **Add `removeListener()`** to `AppSession` to prevent listener memory leak
9. **Remove `userStorage.save(sender)`** from `MessagingService.sendMessage()` — spurious write
10. **Add null-check** in `MatchQualityService.computeQuality()` for deleted users

### Priority 2 — Fix Soon (Memory & Architecture)

11. **Add eviction** to `SessionService.userLocks` — use `WeakHashMap` or periodic cleanup
12. **Auto-schedule** `DailyService.cleanupOldDailyPickViews()` or use a bounded cache (e.g., `Caffeine`)
13. **Refactor `RelationshipTransitionService`** to return Result records instead of throwing
14. **Refactor `MessagingService.getMessages()`** to return Result instead of throwing
15. **Fix `MatchingService` constructors**: Reduce to 1-2 constructors using builder pattern; consistently validate all dependencies
16. **Remove null-accepting constructors** from `TrustSafetyService` or make them package-private for testing only
17. **Add `involves()` check** to `Match.gracefulExit()` — currently missing unlike `unmatch()` and `block()`
18. **Change `UserStorage.get()` to return `Optional<User>`** — align with all other storage interfaces
19. **Store `AppConfig` field in `AchievementService`** — currently validated but discarded
20. **Make `AppBootstrap.services` field volatile** or synchronize `getServices()`

### Priority 3 — Improve (Quality & Consistency)

21. **Replace 11 hardcoded thresholds with AppConfig** across AchievementService, MatchQualityService, StandoutsService, StatsService, ProfileCompletionService, Dealbreakers (see Config Misuse Cluster table)
22. **Move `ConfigLoader.java` out of `core/`** — it imports Jackson, violating the zero-framework-imports rule
23. **Validate match quality weights sum to 1.0** in `AppConfig` compact constructor
24. **Add REST API authentication** middleware — currently all endpoints are open
25. **Wire `CleanupService`** into ServiceRegistry and add a scheduler
26. **Wire `ConfigLoader`** into AppBootstrap for external configuration
27. **Bound `PerformanceMonitor` metrics map** — add max size or periodic reset
28. **Synchronize `PerformanceMonitor.OperationMetrics` getters** — readers see partially-updated state
29. **Bound `ImageCache`** with max entries or LRU eviction
30. **Add pagination** to `MessagingService.getConversations()`

---

## Tests to Add

| #  | Test Target                     | Test Description                                                                               | Priority |
|----|---------------------------------|------------------------------------------------------------------------------------------------|----------|
| 1  | `AppSession`                    | Thread safety: concurrent `setCurrentUser` + `getCurrentUser` from multiple threads            | HIGH     |
| 2  | `AppSession`                    | TOCTOU in `isActive()`: call from 2 threads, race `logout()` between null-check and getState() | HIGH     |
| 3  | `AppSession`                    | Listener exception isolation: one bad listener shouldn't prevent others from firing            | HIGH     |
| 4  | `AppSession`                    | Listener leak: add 100 listeners without removal, verify list doesn't grow unboundedly         | HIGH     |
| 5  | `User`                          | `getInterestedIn()` with empty `interestedIn` EnumSet — should NOT throw                       | HIGH     |
| 6  | `User`                          | `setPhotoUrls` with 3+ photos when `CONFIG.maxPhotos()` is set to 6                            | HIGH     |
| 7  | `User`                          | `isComplete()` with `CONFIG.minAge()` set to 21 and user age 19                                | HIGH     |
| 8  | `MatchingService`               | `processSwipe()` called on 2-arg constructed instance (should fail gracefully, not NPE)        | HIGH     |
| 9  | `MatchingService`               | `recordLike()` when `matchStorage.save()` throws non-duplicate-key exception                   | HIGH     |
| 10 | `MatchQualityService`           | `computeQuality()` when one of the matched users has been deleted — should not NPE             | HIGH     |
| 11 | `MessagingService`              | `sendMessage()` verify sender is NOT saved to storage                                          | MEDIUM   |
| 12 | `Match`                         | `gracefulExit()` with non-participant UUID (should throw, currently doesn't check)             | MEDIUM   |
| 13 | `DailyService`                  | Memory growth: call `getDailyPick()` for 10,000 users, verify cleanup works                    | MEDIUM   |
| 14 | `SessionService`                | Memory growth: call `recordSwipe()` for 10,000 users, verify locks are bounded                 | MEDIUM   |
| 15 | `AchievementService`            | Verify config thresholds are actually used (currently config is validated but discarded)       | MEDIUM   |
| 16 | `RelationshipTransitionService` | All methods with null parameters                                                               | MEDIUM   |
| 17 | `TrustSafetyService`            | `block()` with null storage (no-arg constructor)                                               | MEDIUM   |
| 18 | `AppConfig`                     | Builder with weights summing to != 1.0                                                         | LOW      |
| 19 | REST API routes                 | Full route test coverage (currently 0%)                                                        | LOW      |
| 20 | `CandidateFinder`               | Users at location (0,0) — verify treated as "no location"                                      | LOW      |

---

## Assumptions & Unknowns

### Assumptions Made During Audit
1. **Single-JVM deployment**: The app runs as a single process. Thread safety issues matter for JavaFX + background threads sharing `AppSession`, not for multi-node clusters.
2. **H2 embedded database**: No external database server. Schema evolution is done via `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ADD COLUMN IF NOT EXISTS`.
3. **`CleanupService` and `ConfigLoader`** are intentionally unwired for Phase 2 — not dead code per se, but prep for future phases.
4. **REST API is development-only**: No production deployment planned yet, so authentication is deprioritized (but still documented).
5. **`Interest.MAX_PER_USER = 10`** is the intended limit, and `AppConfig.maxInterests = 5` is a stale default that should be updated.

### Unknowns
1. **Is `StandoutsService` fully integrated?** It has storage and service classes but its wiring into the app flow is unclear.
2. **Is `PacePreferences.isComplete()` correctly defined?** `User.isComplete()` depends on it, but the logic wasn't fully audited.
3. **Does `TransactionExecutor` / `JdbiTransactionExecutor` work correctly?** The undo flow falls back to non-atomic deletes — unclear if the transaction path is tested.
4. **Are FXML files in sync with controllers?** FXML `fx:id` bindings were not cross-referenced against controller `@FXML` fields.
5. **Are CSS selectors in `theme.css` all valid?** `JavaFxCssValidationTest` exists but its coverage of dynamic styles is unknown.

---

## Machine-Readable Summary

```json
{
  "audit_date": "2026-02-06",
  "auditor": "GitHub Copilot (Claude Opus 4.6)",
  "repository": "Date_Program",
  "language": "Java 25",
  "framework": "JavaFX 25",
  "build_tool": "Maven 3.9.12",
  "database": "H2 2.4.240 + JDBI 3.51.0",
  "codebase_stats": {
    "total_java_files": 167,
    "main_files": 130,
    "test_files": 37,
    "total_lines": 42852,
    "code_lines": 31561,
    "comment_lines": 4747,
    "blank_lines": 6544
  },
  "build_status": {
    "compiles": true,
    "compile_errors": 0,
    "tests_run": 729,
    "tests_passed": 729,
    "tests_failed": 0,
    "tests_skipped": 0
  },
  "issues": {
    "total": 73,
    "critical": 9,
    "high": 16,
    "medium": 30,
    "low": 18,
    "categories": {
      "thread_safety": 8,
      "hardcoded_values": 3,
      "config_misuse": 13,
      "memory_leaks": 5,
      "result_pattern_violations": 4,
      "architecture": 5,
      "missing_validation": 5,
      "null_safety": 4,
      "enumset_bug": 1,
      "api_security": 4,
      "spurious_side_effects": 1,
      "code_quality": 8,
      "incomplete_features": 4,
      "test_gaps": 8
    }
  },
  "top_issues": [
    {"id": "TSA-01", "severity": "CRITICAL", "file": "core/AppSession.java", "summary": "currentUser not volatile"},
    {"id": "TSA-02", "severity": "CRITICAL", "file": "core/AppSession.java", "summary": "isActive() TOCTOU race — NPE on concurrent logout"},
    {"id": "USR-01", "severity": "CRITICAL", "file": "core/User.java", "summary": "User shared mutable object — concurrent setter calls"},
    {"id": "USR-02", "severity": "CRITICAL", "file": "core/User.java", "summary": "Photo limit hardcoded to 2"},
    {"id": "CFG-01", "severity": "CRITICAL", "file": "core/AppConfig.java", "summary": "maxInterests=5 vs MAX_PER_USER=10"},
    {"id": "USR-03", "severity": "CRITICAL", "file": "core/User.java", "summary": "isComplete() hardcodes minAge 18"},
    {"id": "SES-01", "severity": "CRITICAL", "file": "core/SessionService.java", "summary": "userLocks unbounded memory leak"},
    {"id": "DLY-01", "severity": "CRITICAL", "file": "core/DailyService.java", "summary": "dailyPickViews unbounded memory leak"},
    {"id": "TSA-03", "severity": "CRITICAL", "file": "core/AppSession.java", "summary": "Listener exception kills all notifications"}
  ],
  "action_items": {
    "priority_1_fix_now": 10,
    "priority_2_fix_soon": 10,
    "priority_3_improve": 10
  },
  "tests_to_add": 20,
  "unknowns": 5
}
```

---

*End of audit report. No code changes were made during this audit.*
