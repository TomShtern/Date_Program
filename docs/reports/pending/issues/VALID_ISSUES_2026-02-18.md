# Valid Open Issues — Dating App Codebase
> **Verified:** 2026-02-18
> **Methodology:** Every issue below was confirmed against the **current source code** as the truth source.
> All "fixed" items from prior audit reports have been excluded.
> **Total open issues:** 36 (7 Critical · 9 High · 11 Medium · 4 Low · 5 Code-Quality)

---

## Table of Contents
- [Critical Issues](#-critical-issues)
- [High Issues](#-high-issues)
- [Medium Issues](#-medium-issues)
- [Low Issues](#-low-issues)
- [Code Quality / Organizational](#-code-quality--organizational)
- [What Was Fixed](#-what-was-fixed)

---

## 🔴 Critical Issues

> Architecture or design flaws that create OOM risk, data loss, or violate the project's core invariants.

---

### CRIT-01 — CandidateFinder: Full Table Scan Loads Entire User Database Into RAM

**File:** `core/matching/CandidateFinder.java` · [findCandidatesForUser()](src/main/java/datingapp/core/matching/CandidateFinder.java)

```java
List<User> activeUsers = userStorage.findActive(); // SELECT * FROM users WHERE state = 'ACTIVE' — no filters
```

**Problem:** `findActive()` returns every active user as Java objects. All 7 candidate filters (gender, age, distance, dealbreakers, blocks, interactions) run in-memory in Java streams. At 100K+ users this is an OOM crash and perpetually thrashes the database.

**Fix Direction:** Push age/gender/distance bounding-box predicates into SQL `WHERE` clauses in `JdbiUserStorage`. Use `CandidateFinder` only for weighted scoring of the pre-filtered result set.

---

### CRIT-02 — ChatViewModel: Silent Message Data Loss

**Files:** `ui/viewmodel/screen/ChatViewModel.java` · `ui/screen/ChatController.java`

```java
// ChatViewModel — SendResult discarded completely
messagingService.sendMessage(currentUser.getId(), otherId, text.trim());

// ChatController — input cleared before knowing if send succeeded
viewModel.sendMessage(text);
messageArea.clear(); // ← unconditional
```

**Problem:** `ConnectionService.sendMessage()` returns a well-designed `SendResult` that carries success/failure/error codes. The ViewModel discards it. The Controller clears the input field unconditionally. Failed sends (validation errors, banned user, storage failure) silently disappear — the user believes the message was sent.

**Fix Direction:** Return `SendResult` from `ChatViewModel.sendMessage()`. In `ChatController`, only call `messageArea.clear()` when `result.success()`. Show failure feedback via `UiFeedbackService`.

---

### CRIT-03 — AppConfig.defaults() Bypass: Split-Brain Configuration

**Files:** `core/model/User.java:505` · `ui/screen/LoginController.java:77` · `core/profile/ValidationService.java` (no-arg constructor)

```java
// LoginController — static field, ignores loaded app-config.json
private static final AppConfig CONFIG = AppConfig.defaults();

// User.java setAgeRange() — creates a new defaults() per call
AppConfig config = AppConfig.defaults();
```

**Problem:** Changes to `config/app-config.json` at startup have no effect on these components — they silently use hardwired defaults regardless. This creates split-brain where the storage layer might enforce one set of rules while the UI and domain enforce another.

**Fix Direction:** Inject the startup-loaded `AppConfig` (from `ApplicationStartup.initialize()`) throughout. Remove all `AppConfig.defaults()` calls from production code paths.

---

### CRIT-04 — TrustSafetyService: Public Null-Dependency Constructors

**File:** `core/matching/TrustSafetyService.java`

```java
public TrustSafetyService() {
    this(DEFAULT_VERIFICATION_TTL, new Random());
}
public TrustSafetyService(Duration verificationTtl, Random random) {
    this(null, null, null, null, verificationTtl, random); // all 4 core deps = null
}
```

**Problem:** Violates the project's fail-fast principle (`Objects.requireNonNull` in constructors). These are `public` constructors — any code can instantiate a broken `TrustSafetyService`. Methods like `report()` NPE at runtime; `block()` throws `IllegalStateException`. Failure is deferred from construction to runtime with no actionable message.

**Fix Direction:** Remove these constructors from production code. Tests should use the full constructor with `TestStorages` implementations.

---

### CRIT-05 — ConnectionService: Friend-Zone Methods Throw Instead of Returning Results

**File:** `core/connection/ConnectionService.java`

```java
// requestFriendZone(), acceptFriendZone(), declineFriendZone(), gracefulExit()
throw new TransitionValidationException("An active match is required...");
throw new TransitionValidationException("Friend request not found.");
// ... 8+ more throws
```

**Problem:** Project policy: *"Services return result records, never throw business exceptions."* `ConnectionService` has `SendResult`, `BlockResult`, `ReportResult` following this pattern correctly — but all 4 friend-zone/transition methods are exception-based. Forces callers into try/catch for control flow.

**Fix Direction:** Introduce a `TransitionResult` record. Refactor `requestFriendZone`, `acceptFriendZone`, `declineFriendZone`, and `gracefulExit` to return it instead of throwing.

---

### CRIT-06 — RecommendationService: Hardcoded Activity Scoring Thresholds

**File:** `core/matching/RecommendationService.java` (line ~541)

```java
private double calculateActivityScore(User user) {
    long hours = ...; // hours since last active
    if (hours < 1)   return 1.0;  // cannot be changed without recompilation
    if (hours < 24)  return 0.9;
    if (hours < 72)  return 0.7;
    if (hours < 168) return 0.5;
    if (hours < 720) return 0.3;
    return 0.1;
}
```

**Problem:** 6 hardcoded magic numbers control the activity scoring algorithm. Cannot be tuned via config. Violates the centralized configuration principle enforced everywhere else.

**Fix Direction:** Move thresholds to `AppConfig` or named constants in a constants holder.

---

### CRIT-07 — MatchQualityService + MatchingService: Throw Exceptions Instead of Result Types

**Files:** `core/matching/MatchQualityService.java:266` · `core/matching/MatchingService.java:224`

```java
// MatchQualityService.computeQuality()
if (me == null) throw new IllegalArgumentException("User not found: " + perspectiveUserId);
if (them == null) throw new IllegalArgumentException("User not found: " + otherUserId);

// MatchingService.findPendingLikersWithTimes()
if (userStorage == null)
    throw new IllegalStateException("userStorage required for liker browsing. Use full constructor.");
```

**Problem:** Same violation as CRIT-05 but in two additional services. `computeQuality()` should return `Optional<MatchQuality>` or a result wrapper. The `IllegalStateException` in `MatchingService` is doubly wrong — it means the builder's `build()` method allows construction of a broken object (confirmed: `userStorage` has no `requireNonNull` in the constructor).

**Fix Direction:**
- `MatchQualityService.computeQuality()`: Return `Optional<MatchQuality>`.
- `MatchingService.Builder.build()`: Add `Objects.requireNonNull(userStorage, "userStorage required for liker browsing")` and remove the runtime throw.

---

## 🟠 High Issues

> Violates a stated project convention, creates consistency problems, or introduces security/data integrity risk.

---

### HIGH-01 — ConnectionService: Multi-Step Writes Without Transaction Boundaries

**File:** `core/connection/ConnectionService.java`

`acceptFriendZone` performs **3 sequential writes** across 2 different storage objects with no `@Transaction`:

```java
interactionStorage.update(match);               // Write 1
communicationStorage.updateFriendRequest(updated); // Write 2 — if this fails, Write 1 is committed
communicationStorage.saveNotification(...);      // Write 3 — same risk
```

Same pattern in `requestFriendZone` (2 writes) and `gracefulExit` (2 writes).

**Impact:** Process crash or storage exception between writes leaves the system in an inconsistent state (e.g., match transitioned to FRIENDS but FriendRequest still PENDING).

**Fix Direction:** Use JDBI's `inTransaction()` callbacks or `@Transaction` annotation for all multi-write service methods.

---

### HIGH-02 — REST API: Intentionally Unauthenticated Local IPC (IDOR if Network-Exposed)

**File:** `app/api/RestApiServer.java`

```java
// No app.before() middleware. All 10 endpoints fully open:
app.get("/api/users/{id}/conversations", this::getConversations);  // IDOR: any caller reads any user's chats
app.post("/api/conversations/{id}/messages", this::sendMessage);   // IDOR: impersonation
app.get("/api/users/{id}/candidates", this::getCandidates);
// ... 7 more unauthenticated routes
```

**Status:** The code now documents the localhost-only stance in `RestApiServer.registerRoutes()` and binds the server to localhost only. The remaining concern is exposure risk if the API is ever network-reachable.

**Impact:** If network-exposed, any caller can list user conversations, send messages as any user, and view all candidates.

**Fix Direction (minimal):** Keep the localhost-only stance explicit in code and docs; if the deployment model changes, add auth middleware before exposing the API beyond localhost.

---

### HIGH-03 — HashSet Used for Enum Types (6 Violations)

**Files:**
- `app/cli/ProfileHandler.java:277` — `Set<Gender> result = new HashSet<>()`
- `core/profile/MatchPreferences.java:490–494` — 5 `DealbreakersBuilder` fields (`Smoking`, `Drinking`, `WantsKids`, `LookingFor`, `Education`)

**Problem:** Project rule: *"Use `EnumSet` for enums, never `HashSet`."* `EnumSet` uses a bitmask internally (O(1) operations, minimal allocation). `HashSet` adds unnecessary boxing and hashing overhead for fixed enum domains.

**Fix Direction:** Replace with `EnumSet.noneOf(X.class)` in builder/accumulator contexts. Use `Collectors.toCollection(() -> EnumSet.noneOf(Gender.class))` in stream pipelines.

---

### HIGH-04 — MatchingService: userStorage Not Null-Checked in Constructor or Builder

**File:** `core/matching/MatchingService.java:46`

```java
this.interactionStorage = Objects.requireNonNull(interactionStorage, "...");
this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "...");
this.userStorage = userStorage; // ← no null check — silently accepts null
```

**Problem:** The builder's `build()` also passes `userStorage` with no `requireNonNull`. The service accepts a null `userStorage` at construction, then throws `IllegalStateException` at the first call that needs it (CRIT-07). Fail-fast principle violated.

**Fix Direction:** Add `Objects.requireNonNull(userStorage, "userStorage cannot be null")` in the constructor. Remove the runtime throw in `findPendingLikersWithTimes()`.

---

### HIGH-05 — DatabaseManager: shutdown() Not Synchronized

**File:** `storage/DatabaseManager.java`

```java
public synchronized Connection getConnection() { ... }  // synchronized ✅
public static synchronized DatabaseManager getInstance() { ... }  // synchronized ✅
public void shutdown() {                                // NOT synchronized ❌
    if (dataSource != null) {
        dataSource.close();
        dataSource = null;
    }
}
```

**Problem:** A concurrent `getConnection()` call can interleave with `shutdown()` — seeing `initialized = true` while `dataSource` is being nulled, causing an NPE or operating on a closed connection pool.

**Fix Direction:** Add `synchronized` to `shutdown()`.

---

### HIGH-06 — RecommendationService: Mixed Clock Usage (Injected Clock Bypassed)

**File:** `core/matching/RecommendationService.java`

The service injects a `Clock` field but bypasses it in 3 places:

| Line | Direct AppClock Call                    | Should Be              |
|------|-----------------------------------------|------------------------|
| ~364 | `AppClock.today(config.userTimeZone())` | `LocalDate.now(clock)` |
| ~377 | `AppClock.today(config.userTimeZone())` | `LocalDate.now(clock)` |
| ~544 | `AppClock.now()`                        | `Instant.now(clock)`   |

**Problem:** Tests injecting a fixed `Clock` instance won't control time in these code paths. Time-dependent logic becomes non-deterministic in tests.

**Fix Direction:** Replace all 3 `AppClock.*` calls with `LocalDate.now(clock)` / `Instant.now(clock)` using the injected field.

---

### HIGH-07 — AppConfig: 57-Parameter God Record

**File:** `core/AppConfig.java`

Single flat record with 57 parameters spanning limits, validation, algorithm weights, UI config, and safety thresholds. No sub-grouping.

**Problem:** Constructing `AppConfig` in tests requires 57 arguments (or `.builder()` with 57 setter calls). Violates Single Responsibility — a matching algorithm weight change and a UI window dimension share the same record.

**Fix Direction:** Group into sub-records: e.g., `MatchingConfig`, `ValidationConfig`, `UiConfig`, `SafetyConfig`. Referenced in user note: *"Make a config folder and place the config files there."*

---

### HIGH-08 — LifestyleMatcher: 5 Identical isAcceptable() Overloads (Code Duplication)

**File:** `core/matching/LifestyleMatcher.java:21–58`

```java
public static boolean isAcceptable(Lifestyle.Smoking value, Set<Lifestyle.Smoking> allowed) {
    if (allowed == null || allowed.isEmpty()) { return true; }
    return value != null && allowed.contains(value);
}
// ...repeated verbatim for Drinking, WantsKids, LookingFor, Education
```

All 5 overloads have identical method bodies. Adding a new lifestyle enum requires copy-pasting again.

**Fix Direction:** Single generic method:
```java
public static <E extends Enum<E>> boolean isAcceptable(E value, Set<E> allowed) {
    if (allowed == null || allowed.isEmpty()) { return true; }
    return value != null && allowed.contains(value);
}
```

---

### HIGH-09 — ProfileService: 793+ Lines, 6 Mixed Responsibilities

**File:** `core/profile/ProfileService.java`

Single class handling:
1. Profile completion scoring (basic/interests/lifestyle/preference scoring)
2. Improvement tips generation
3. Achievement tracking and unlocking
4. Profile preview formatting
5. Behavior analysis (selective/open-minded classification)
6. Match counting (loads ALL matches from storage just to count them — no `COUNT(*)` query)

**Problem:** High cognitive load. Changes to achievement logic risk breaking completion scoring. The match-counting code loads all match objects when a `COUNT(*)` SQL query would suffice.

**Fix Direction (non-breaking):**
- Add a table-of-contents comment at the top with section line ranges
- Add `// ══════ SECTION: Achievement Tracking ══════` section dividers
- Replace `getMatchCount()` with a `COUNT(*)` storage query to eliminate unnecessary object loading

---

## 🟡 Medium Issues

> Code smells, minor convention violations, or inconsistencies; not immediately dangerous.

---

### MED-01 — DatingApp.java: 6 Hardcoded Window Dimension Literals

**File:** `ui/DatingApp.java:49–55`

```java
primaryStage.setMinWidth(800);   primaryStage.setMinHeight(600);
primaryStage.setMaxWidth(1600);  primaryStage.setMaxHeight(1000);
primaryStage.setWidth(1000);     primaryStage.setHeight(760);
```

None reference `UiConstants`. Should use named constants for consistency with the rest of the UI layer.

---

### MED-02 — DatingApp.java: 6 Debug Logger Statements at INFO Level

**File:** `ui/DatingApp.java:init()`

```java
logger.info("[DEBUG] Initializing application services...");
logger.info("[DEBUG] ServiceRegistry initialized.");
// ... 4 more [DEBUG] prefixed info logs
```

**Fix Direction:** Demote to `logger.debug(...)` and remove the `[DEBUG]` prefix.

---

### MED-03 — Magic Numbers in UI Controllers

**Files:**
- `ui/screen/LoginController.java:formatFilter()` — truncation at `24` chars (hardcoded)
- `ui/screen/ChatController.java:ConversationListCell.updateItem()` — preview at `35` chars (hardcoded)

Both values should be named constants in `UiConstants`.

---

### MED-04 — ValidationService Not Registered in ServiceRegistry

**Files:** `core/profile/ValidationService.java` · `core/ServiceRegistry.java`

`ValidationService` is not a field in `ServiceRegistry`, so callers instantiate it manually instead of getting a shared instance. This makes it impossible to inject a custom `AppConfig` instance centrally.

**Fix Direction:** Add `ValidationService` to `ServiceRegistry` alongside the other 9 registered services.

---

### MED-05 — UndoService: Inconsistent Clock Mechanism

**File:** `core/matching/UndoService.java:49,73,96`

```java
Instant expiresAt = Instant.now(clock).plusSeconds(config.undoWindowSeconds());
```

`UndoService` injects a `Clock` and uses `Instant.now(clock)`. The rest of the project uses `AppClock.now()`. The no-arg constructor defaults to `Clock.systemUTC()` rather than `AppClock.clock()`.

**Impact:** Tests using `AppClock.setTestClock()` to freeze time do not affect `UndoService` timestamps. Two clock mechanisms coexist.

**Fix Direction:** Either migrate `UndoService` to use `AppClock.now()` (simpler, aligns with project convention) or document the deliberate divergence.

---

### MED-06 — MatchQualityService: 34 Hardcoded Threshold Constants (Not in AppConfig)

**File:** `core/matching/MatchQualityService.java:32–66`

34 `private static final` constants (star thresholds, distance thresholds, pace sync thresholds, response score values, pace dimension scores). A comment even labels them *"inlined from deleted ScoringConstants"* — they were never moved to `AppConfig`.

**Fix Direction:** Move scoring algorithm thresholds to a `ScoringConstants` holder or to `AppConfig`. At minimum, they should not be scattered throughout a 778-line class.

---

### MED-07 — Dealbreakers Compact Constructor Uses Set.copyOf Instead of EnumSet.copyOf

**File:** `core/profile/MatchPreferences.java:393–399`

```java
acceptableSmoking = acceptableSmoking == null ? Set.of() : Set.copyOf(acceptableSmoking);
// ... repeated for Drinking, WantsKids, LookingFor, Education
```

`Set.copyOf()` returns a `HashSet`-backed unmodifiable set — not an `EnumSet`. The empty-case `Set.of()` also loses type information. Inconsistent with how `User.java` handles enum sets.

**Fix Direction:** Use `EnumSetUtil.safeCopy(acceptableSmoking, Lifestyle.Smoking.class)` or the equivalent guard pattern used in `User.java`.

---

### MED-08 — Main.java: No Login Guard + 7 Mutable Static Fields

**File:** `app/Main.java`

**No login guard:** Menu options 3–20 dispatch directly without checking `AppSession.getInstance().getCurrentUser() != null`. Handlers internally detect null user but produce inconsistent error messages.

**Mutable static state:** 7 handler/service references (`services`, `inputReader`, `matchingHandler`, `profileHandler`, `safetyHandler`, `statsHandler`, `messagingHandler`) are `private static` non-final fields — untestable and not thread-safe.

**Fix Direction:**
- Add `if (session.getCurrentUser() == null) { log("Please select a user first (option 1 or 2)."); continue; }` before the option dispatch.
- Move handler creation into a local scope or a proper initialization block.

---

### MED-09 — MatchQualityService: 778+ Lines, Needs Internal Organization

**File:** `core/matching/MatchQualityService.java`

778 lines containing: 34 constants, 6-factor scoring, lifestyle delegation, pace compatibility scoring, highlight string generation, nested `InterestMatcher` utility class (115 lines), nested `MatchQuality` record (100 lines).

**Problem:** Not a split candidate (the nested types are correctly scoped), but navigating the file without section structure is difficult.

**Fix Direction:** Add section headers (`// ══════ PACE COMPATIBILITY ══════`) and a top-of-file table of contents comment with line ranges.

---

### MED-10 — MatchPreferences.java: 789+ Lines, 4-Level Nesting Depth

**File:** `core/profile/MatchPreferences.java`

789 lines containing `MatchPreferences` class + `Interest` enum (39 values + inner `Category`) + `Lifestyle` container with 5 enums + `PacePreferences` record with 4 inner enums + `Dealbreakers` record with `Builder` and `Evaluator` (200+ lines). Finding a specific inner class requires scrolling past 600+ lines.

**Fix Direction:** Add a table of contents comment at the top with line ranges for each major type. Add section dividers between types. (The consolidation is architecturally valid — the improvement is navigability.)

---

### MED-11 — RecommendationService.resolveUsers(): Unnecessary HashSet Wrapper

**File:** `core/matching/RecommendationService.java` (line ~386)

```java
new HashSet<>(ids) // wraps List<UUID> unnecessarily before use
```

Use `Set.copyOf(ids)` or collect directly to a `Set` to avoid the defensive wrapper.

---

## 🟢 Low Issues

> Style nits or minor quality improvements; no functional impact.

---

### LOW-01 — LifestyleMatcher: Missing isMatch() Overloads for WantsKids and Education

**File:** `core/matching/LifestyleMatcher.java:64–79`

`isMatch()` overloads exist for `Smoking`, `Drinking`, and `LookingFor`, but not for `WantsKids` or `Education`. The pattern is `a != null && b != null && a == b` — identical across all. `MatchQualityService` works around this gap by calling `areKidsStancesCompatible()` directly.

**Fix Direction:** Add the 2 missing overloads, or adopt the generic approach from HIGH-08.

---

### LOW-02 — Dealbreakers.Evaluator: Fully-Qualified java.util.ArrayList

**File:** `core/profile/MatchPreferences.java:627`

```java
List<String> failures = new java.util.ArrayList<>();
```

`ArrayList` is imported elsewhere in the project. Add the import and use shorthand.

---

### LOW-03 — Dealbreakers.Evaluator.passes(): AppConfig Parameter Never Used

**File:** `core/profile/MatchPreferences.java:604`

```java
public static boolean passes(User seeker, User candidate, AppConfig config) {
    Objects.requireNonNull(config, "config cannot be null"); // only usage
    // config is never passed to any sub-method
}
```

`config` is validated but never referenced in any computation inside `passes()` or `getFailedDealbreakers()`. Misleading API — callers think config affects evaluation.

**Fix Direction:** Remove the parameter, or implement config-dependent logic (e.g., configurable height bounds from `config.minHeightCm()`).

---

### LOW-04 — Inconsistent Builder Null-Check Completeness

| Builder                         | Null-Checks All Required Deps?                       |
|---------------------------------|------------------------------------------------------|
| `RecommendationService.Builder` | ✅ All 8 deps                                         |
| `MatchingService.Builder`       | ❌ Missing `userStorage`                              |
| `TrustSafetyService`            | ❌ No builder; public null-constructors (see CRIT-04) |

**Fix Direction:** `MatchingService.Builder` — add `requireNonNull` for `userStorage`.

---

## 📦 Code Quality / Organizational

> Structural issues — not bugs, but create maintenance friction.

---

### ORG-01 — ProfileController: 919-Line God Controller, No UiUtils

**File:** `ui/screen/ProfileController.java`

919 lines mixing event handling, complex styling logic, enum-to-string converter factories, photo upload logic, and dealbreaker editor logic. No `UiUtils` or `UiSupport` class exists to extract common patterns into.

**Fix Direction:** Extract enum converter factories and reusable cell factories to a shared `UiUtils` class. Consider decomposing photo upload and dealbreaker editing into sub-controllers.

---

### ORG-02 — Test Storage Stub Proliferation (7 Inline Stubs Alongside TestStorages)

**Affected test files:**

| File                           | Inline Stub                                          |
|--------------------------------|------------------------------------------------------|
| `ProfileCreateSelectTest.java` | `InMemoryUserStorage implements UserStorage`         |
| `TrustSafetyServiceTest.java`  | `InMemoryUserStorage implements UserStorage`         |
| `LikerBrowserServiceTest.java` | `InMemoryInteractionStorage` + `InMemoryUserStorage` |
| `AchievementServiceTest.java`  | `InMemoryUserStorage implements UserStorage`         |
| `MessagingServiceTest.java`    | `InMemoryUserStorage implements UserStorage`         |
| `DailyPickServiceTest.java`    | `InMemoryUserStorage implements UserStorage`         |

**Problem:** `TestStorages.java` exists as the centralized hub, but 7 additional inline implementations duplicate it, creating maintenance churn when storage interfaces change.

**Fix Direction:** Migrate inline stubs to `TestStorages.java` entries and update all test references.

---

### ORG-03 — REST API: Auth-Absence Comment Merged into HIGH-02

**File:** `app/api/RestApiServer.java`

The REST API has zero authentication (see HIGH-02). This note is now merged into that issue because `RestApiServer.registerRoutes()` already documents the localhost-only intent.

No separate open action remains for the comment itself.

---

### ORG-04 — PerformanceMonitor: AppClock Used for Elapsed-Time Measurement

**File:** `core/PerformanceMonitor.java`

Uses `AppClock.now()` for timing instead of `System.nanoTime()`. In tests with a frozen test clock, all timers report 0ms elapsed.

**Problem:** Design tradeoff worth documenting with a comment so future contributors understand why timing appears broken in tests.

---

### ORG-05 — 1 Remaining TODO in Production Source Code

**File:** `ui/screen/MatchesController.java:512`

```java
// TODO: Replace with real presence status when user presence tracking is implemented
```

One `TODO` remains in main source. Low priority but should be tracked.

---

## ✅ What Was Fixed

> Issues from the three audit reports that are **no longer present** in the current code.

| ID          | Issue                                                                                          | Evidence of Fix                                                       |
|-------------|------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| FI-CONS-007 | MatchingService `catch (Exception _)` → now `catch (RuntimeException ...)` with named variable | Verified in `MatchingService.java:145`                                |
| FI-CONS-001 | DatabaseManager God Object (was ~784 lines)                                                    | Now 153 lines; DDL delegated to `MigrationRunner`/`SchemaInitializer` |
| FI-CONS-003 | `ProfileNote` nested in `User.java`                                                            | Now standalone `core/model/ProfileNote.java`                          |
| FI-CONS-011 | JDBI SQL column list copy-pasted in every SELECT                                               | `ALL_COLUMNS` constant used in all 4 user SELECT queries              |
| M-3 (M2-2)  | `ScoringConstants` class referenced in comments                                                | Class deleted; only 2 harmless *explanatory* comments remain          |
| M2-5        | Main.java FFM console init block undocumented                                                  | Now has clear `// ═══ CONSOLE UTF-8 ═══` header + inline explanations |
| FI-CONS-004 | Duplicated validation logic between `User.java` and `ValidationService`                        | Both now use `AppConfig.defaults()` for thresholds                    |
| FI-CONS-006 | CSS hardcoded hex colors (170+)                                                                | Replaced with CSS variables                                           |
| FI-CONS-008 | ServiceRegistry DI boilerplate                                                                 | Organized with structured section comments                            |
| FI-CONS-010 | Fragmented config constants scattered across services                                          | Consolidated into `AppConfig.java` (6 new validation constants added) |
| FI-CONS-014 | CLAUDE.md documentation drift                                                                  | Updated to reflect actual implemented patterns                        |

---

## Recommended Fix Order

### Phase 1 — Quick Wins (< 2 hours each, high impact)
1. **HIGH-05** — Add `synchronized` to `DatabaseManager.shutdown()`
2. **CRIT-03** — In `User.java`, remove the `AppConfig.defaults()` local var; take config from constructor
3. **HIGH-04** — Add `requireNonNull(userStorage)` in `MatchingService` constructor
4. **HIGH-03** — Replace 6 `HashSet<EnumType>` with `EnumSet.noneOf()`
5. **MED-07** — Replace `Set.copyOf()` with `EnumSetUtil.safeCopy()` in Dealbreakers
6. **MED-02** — Change `logger.info("[DEBUG] ...")` to `logger.debug(...)` in `DatingApp.java`
7. **HIGH-02** — Keep the localhost-only auth stance documented in `RestApiServer.registerRoutes()`
8. **LOW-02** — Fix fully-qualified `java.util.ArrayList` import
9. **LOW-03** — Remove unused `AppConfig config` param from `Dealbreakers.Evaluator.passes()`

### Phase 2 — Design Fixes (significant scope)
10. **CRIT-04** — Remove null-dep constructors from TrustSafetyService
11. **CRIT-07** — `MatchQualityService.computeQuality()` → `Optional<MatchQuality>`; move userStorage null-check to builder
12. **CRIT-02** — Wire `SendResult` back to `ChatViewModel`/`ChatController`
13. **CRIT-05** — Introduce `TransitionResult` in `ConnectionService`
14. **HIGH-06** — Fix `RecommendationService` to use injected `clock` consistently (3 locations)
15. **MED-05** — Migrate `UndoService` to `AppClock.now()`
16. **HIGH-08** — Generic `LifestyleMatcher.isAcceptable()` + add missing `isMatch()` overloads
17. **MED-04** — Register `ValidationService` in `ServiceRegistry`
18. **MED-08** — Add login guard in `Main.java`; scope handler initialization properly

### Phase 3 — Organizational & Scalability
19. **CRIT-01** — Push SQL-side filtering into `JdbiUserStorage.findActive()` (age, gender, distance bounding box)
20. **HIGH-01** — Wrap multi-write `ConnectionService` methods in JDBI transactions
21. **HIGH-09** + **MED-09** + **MED-10** — Add section headers/TOC to `ProfileService`, `MatchQualityService`, `MatchPreferences`
22. **HIGH-07** — Decompose `AppConfig` into sub-records
23. **ORG-01** — Extract `ProfileController` helper logic to `UiUtils`
24. **ORG-02** — Consolidate inline test stubs into `TestStorages`
25. **MED-06** — Move `MatchQualityService` constants to config/constants holder
26. **CRIT-06** + **MED-11** — Move activity-scoring thresholds to config

---

*Source of truth: actual `.java` source files as of 2026-02-18. Issues verified by direct code inspection.*

