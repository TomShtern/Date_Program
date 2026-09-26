# AI Agent Implementation Plan — Full Issue Remediation
> **Source:** `VALID_ISSUES_2026-02-18.md` (31 verified open issues)
> **Target:** Complete implementation by an AI coding agent. No issue skipped.
> **Date:** 2026-02-18

---

## Agent Protocol — Read Before Starting

These rules must be followed at all times.

1. **Read before editing.** Always read the full target file (or the targeted section) before making any change. Never edit from memory alone.
2. **Use `rg` to locate.** Each task provides an `rg` locate command. Run it first to confirm the exact line numbers in the current file.
3. **One phase at a time.** Complete every task in Phase 1 before moving to Phase 2. Dependencies flow forward only.
4. **Compile after each task.** Run `mvn compile -q` after every single task. If it fails, fix it before proceeding.
5. **Run tests after each phase.** Run the phase verification command shown at the phase boundary.
6. **Never throw from services.** All error paths in services return `*Result` records. No exceptions for control flow.
7. **Always use `EnumSet`.** Whenever writing `new HashSet<SomeEnum>()`, replace with `EnumSet.noneOf(SomeEnum.class)`.
8. **Always use `AppClock.now()`.** Never use `Instant.now()` or `new Date()` directly in production code.
9. **`Objects.requireNonNull` in every constructor** for every non-optional dependency parameter.
10. **Call `touch()` after every setter** on mutable domain entities (`User`, `Match`).
11. **Run `mvn spotless:apply`** before running `mvn verify` at end; do not skip.
12. **If a test breaks**, fix it before continuing. Do not mark a task done while a test is red.
13. **Inline test stubs** naming pattern for the `ORG-02` task: search for `class InMemory` in test files.

---

## Pre-Flight Verification

Before any changes, establish a clean baseline.

```powershell
cd "C:\Users\tom7s\Desktop\Claude_Folder_2\Date_Program"

# Step 1 — verify compile is clean
mvn compile -q
# Expected: BUILD SUCCESS with zero errors

# Step 2 — verify tests pass
mvn test -q
# Expected: BUILD SUCCESS, all tests green

# Step 3 — capture baseline test count
mvn test 2>&1 | Select-String "Tests run:" | Select-Object -Last 1
# Record this number. It must not decrease after your changes.
```

If the baseline is not clean, stop and resolve existing failures before implementing any tasks.

---

## Reference: File Paths (All Absolute)

| Short Name                          | Absolute Path                                                         |
|-------------------------------------|-----------------------------------------------------------------------|
| `CandidateFinder`                   | `src/main/java/datingapp/core/matching/CandidateFinder.java`          |
| `UserStorage`                       | `src/main/java/datingapp/core/storage/UserStorage.java`               |
| `JdbiUserStorage`                   | `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`           |
| `ConnectionService`                 | `src/main/java/datingapp/core/connection/ConnectionService.java`      |
| `MatchingService`                   | `src/main/java/datingapp/core/matching/MatchingService.java`          |
| `MatchQualityService`               | `src/main/java/datingapp/core/matching/MatchQualityService.java`      |
| `RecommendationService`             | `src/main/java/datingapp/core/matching/RecommendationService.java`    |
| `TrustSafetyService`                | `src/main/java/datingapp/core/matching/TrustSafetyService.java`       |
| `LifestyleMatcher`                  | `src/main/java/datingapp/core/matching/LifestyleMatcher.java`         |
| `UndoService`                       | `src/main/java/datingapp/core/matching/UndoService.java`              |
| `ProfileService`                    | `src/main/java/datingapp/core/profile/ProfileService.java`            |
| `ValidationService`                 | `src/main/java/datingapp/core/profile/ValidationService.java`         |
| `MatchPreferences`                  | `src/main/java/datingapp/core/profile/MatchPreferences.java`          |
| `User`                              | `src/main/java/datingapp/core/model/User.java`                        |
| `AppConfig`                         | `src/main/java/datingapp/core/AppConfig.java`                         |
| `ServiceRegistry`                   | `src/main/java/datingapp/core/ServiceRegistry.java`                   |
| `DatabaseManager`                   | `src/main/java/datingapp/storage/DatabaseManager.java`                |
| `RestApiServer`                     | `src/main/java/datingapp/app/api/RestApiServer.java`                  |
| `ProfileHandler`                    | `src/main/java/datingapp/app/cli/ProfileHandler.java`                 |
| `Main`                              | `src/main/java/datingapp/app/Main.java`                               |
| `DatingApp`                         | `src/main/java/datingapp/ui/DatingApp.java`                           |
| `UiConstants`                       | `src/main/java/datingapp/ui/UiConstants.java`                         |
| `ChatViewModel`                     | `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`             |
| `ChatController`                    | `src/main/java/datingapp/ui/screen/ChatController.java`               |
| `LoginController`                   | `src/main/java/datingapp/ui/screen/LoginController.java`              |
| `ProfileController`                 | `src/main/java/datingapp/ui/screen/ProfileController.java`            |
| `MatchesController`                 | `src/main/java/datingapp/ui/screen/MatchesController.java`            |
| `TestStorages`                      | `src/test/java/datingapp/testutil/TestStorages.java`                  |
| `RelationshipTransitionServiceTest` | `src/test/java/datingapp/core/RelationshipTransitionServiceTest.java` |

---

## ═══════════════════════════════════════════
## PHASE 1 — Quick Wins ✅ COMPLETE (2026-02-18)
## ═══════════════════════════════════════════

> **Goal:** Isolated, low-risk changes. Each is a contained edit with no API surface changes.
> **Verify after phase:** `mvn test -q` — all tests must pass.
> **STATUS: ALL 10 TASKS COMPLETE — 795 tests passing**

---

### ✅ TASK-1.1: Synchronize `DatabaseManager.shutdown()` [Fixes: HIGH-05]

**File:** `DatabaseManager`
**Risk:** Low — single keyword addition.

**Locate:**
```powershell
rg "public void shutdown" src/main/java/datingapp/storage/DatabaseManager.java -n
```

**Change:**

*Before:*
```java
    public void shutdown() {
        if (dataSource != null) {
```

*After:*
```java
    public synchronized void shutdown() {
        if (dataSource != null) {
```

**Compile check:** `mvn compile -q`

---

### ✅ TASK-1.2: Add `requireNonNull` for `userStorage` in `MatchingService` Constructor [Fixes: HIGH-04, CRIT-07 partial]

**File:** `MatchingService`
**Risk:** Low — adds null guard. Removes the deferred runtime throw from `findPendingLikersWithTimes()`.

**Step A — Locate the constructor null-checks:**
```powershell
rg "requireNonNull|userStorage" src/main/java/datingapp/core/matching/MatchingService.java -n
```

**Step A — Change the constructor** (find the line where `this.userStorage = userStorage;` appears with no null check):

*Before:*
```java
        this.userStorage = userStorage; // no requireNonNull
```

*After:*
```java
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage is required");
```

**Step B — Remove the deferred runtime throw:**

*Locate:*
```powershell
rg "IllegalStateException.*userStorage" src/main/java/datingapp/core/matching/MatchingService.java -n
```

*Before (example — verify exact text first):*
```java
        if (userStorage == null)
            throw new IllegalStateException("userStorage required for liker browsing. Use full constructor.");
```

*After:* Delete those lines entirely. The `requireNonNull` in the constructor is now the guard.

**Compile check:** `mvn compile -q`

> ⚠️ If any test constructs `MatchingService` with a null `userStorage`, it will now fail at construction. Search: `rg "MatchingService" src/test -n -g "*.java"` and update any such test to use `TestStorages.Interactions` / `TestStorages.Users`.

---

### ✅ TASK-1.3: Replace 6 `HashSet<EnumType>` with `EnumSet` [Fixes: HIGH-03]

**Files:** `ProfileHandler` (1 location), `MatchPreferences` (5 locations in `DealbreakersBuilder`)
**Risk:** Low — behaviorally equivalent; `EnumSet` is a drop-in replacement.

**Step A — ProfileHandler:**
```powershell
rg "HashSet" src/main/java/datingapp/app/cli/ProfileHandler.java -n
```
Read the method context (line ~277). The pattern is `Set<Gender> result = new HashSet<>();`.

*Before:*
```java
        Set<Gender> result = new HashSet<>();
```

*After:*
```java
        Set<Gender> result = EnumSet.noneOf(Gender.class);
```

Add import if missing: `import java.util.EnumSet;`

**Step B — MatchPreferences `DealbreakersBuilder` fields (5 lines ~490–494):**
```powershell
rg "HashSet" src/main/java/datingapp/core/profile/MatchPreferences.java -n
```

*Before (all 5 fields):*
```java
        private final Set<Lifestyle.Smoking> smoking = new HashSet<>();
        private final Set<Lifestyle.Drinking> drinking = new HashSet<>();
        private final Set<Lifestyle.WantsKids> kids = new HashSet<>();
        private final Set<Lifestyle.LookingFor> lookingFor = new HashSet<>();
        private final Set<Lifestyle.Education> education = new HashSet<>();
```

*After:*
```java
        private final Set<Lifestyle.Smoking> smoking = EnumSet.noneOf(Lifestyle.Smoking.class);
        private final Set<Lifestyle.Drinking> drinking = EnumSet.noneOf(Lifestyle.Drinking.class);
        private final Set<Lifestyle.WantsKids> kids = EnumSet.noneOf(Lifestyle.WantsKids.class);
        private final Set<Lifestyle.LookingFor> lookingFor = EnumSet.noneOf(Lifestyle.LookingFor.class);
        private final Set<Lifestyle.Education> education = EnumSet.noneOf(Lifestyle.Education.class);
```

Remove `import java.util.HashSet;` from `MatchPreferences` only if no other `HashSet` usage remains.

**Compile check:** `mvn compile -q`

---

### ✅ TASK-1.4: Replace `Set.copyOf()` with `EnumSet` in `Dealbreakers` Compact Constructor [Fixes: MED-07]

**File:** `MatchPreferences` (the `Dealbreakers` record compact constructor)
**Dependency:** TASK-1.3 should be done first (same file, different area).

**Locate:**
```powershell
rg "Set\.copyOf|Set\.of\(\)" src/main/java/datingapp/core/profile/MatchPreferences.java -n
```

Read the `Dealbreakers` compact constructor. It will have a pattern like:
```java
acceptableSmoking = acceptableSmoking == null ? Set.of() : Set.copyOf(acceptableSmoking);
```

**Change — replace each of the 5 fields** using the `EnumSetUtil.safeCopy()` pattern (verify `EnumSetUtil` exists in `core/`):

*Before (for each field):*
```java
            acceptableSmoking = acceptableSmoking == null ? Set.of() : Set.copyOf(acceptableSmoking);
            acceptableDrinking = acceptableDrinking == null ? Set.of() : Set.copyOf(acceptableDrinking);
            acceptableKids = acceptableKids == null ? Set.of() : Set.copyOf(acceptableKids);
            acceptableLookingFor = acceptableLookingFor == null ? Set.of() : Set.copyOf(acceptableLookingFor);
            acceptableEducation = acceptableEducation == null ? Set.of() : Set.copyOf(acceptableEducation);
```

*After (verify field declarations use `Set<EnumType>` — they should after TASK-1.3):*
```java
            acceptableSmoking = acceptableSmoking != null && !acceptableSmoking.isEmpty()
                ? EnumSet.copyOf(acceptableSmoking) : EnumSet.noneOf(Lifestyle.Smoking.class);
            acceptableDrinking = acceptableDrinking != null && !acceptableDrinking.isEmpty()
                ? EnumSet.copyOf(acceptableDrinking) : EnumSet.noneOf(Lifestyle.Drinking.class);
            acceptableKids = acceptableKids != null && !acceptableKids.isEmpty()
                ? EnumSet.copyOf(acceptableKids) : EnumSet.noneOf(Lifestyle.WantsKids.class);
            acceptableLookingFor = acceptableLookingFor != null && !acceptableLookingFor.isEmpty()
                ? EnumSet.copyOf(acceptableLookingFor) : EnumSet.noneOf(Lifestyle.LookingFor.class);
            acceptableEducation = acceptableEducation != null && !acceptableEducation.isEmpty()
                ? EnumSet.copyOf(acceptableEducation) : EnumSet.noneOf(Lifestyle.Education.class);
```

> ⚠️ `EnumSet.copyOf(emptyCollection)` throws `IllegalArgumentException`. The null/empty guard prevents this. Verify the field **declarations** in the `Dealbreakers` record use `Set<Lifestyle.X>` (not `EnumSet<>`) so the record can be serialized freely.

**Compile check:** `mvn compile -q`

---

### ✅ TASK-1.5: Demote `[DEBUG]` Log Statements in `DatingApp.java` [Fixes: MED-02]

**File:** `DatingApp`
**Risk:** Zero — cosmetic logging change.

**Locate:**
```powershell
rg "\[DEBUG\]" src/main/java/datingapp/ui/DatingApp.java -n
```

For each line found (expect ~6), change `logger.info("[DEBUG] ...")` → `logger.debug("...")` (remove the `[DEBUG]` prefix from the string).

Example:
*Before:*
```java
        logger.info("[DEBUG] Initializing application services...");
```

*After:*
```java
        logger.debug("Initializing application services...");
```

**Compile check:** `mvn compile -q`

---

### ✅ TASK-1.6: Add Window Dimension Constants to `UiConstants` and Use Them in `DatingApp.java` [Fixes: MED-01]

**Files:** `UiConstants`, `DatingApp`
**Risk:** Low — pure refactor of literals.

**Step A — Locate current literals in `DatingApp`:**
```powershell
rg "setMinWidth|setMaxWidth|setWidth|setMinHeight|setMaxHeight|setHeight" src/main/java/datingapp/ui/DatingApp.java -n
```

**Step B — Read `UiConstants.java`** to see existing structure, then add a new section:

```java
// ══════ WINDOW DIMENSIONS ══════
public static final double WINDOW_MIN_WIDTH   = 800;
public static final double WINDOW_MIN_HEIGHT  = 600;
public static final double WINDOW_MAX_WIDTH   = 1600;
public static final double WINDOW_MAX_HEIGHT  = 1000;
public static final double WINDOW_PREF_WIDTH  = 1000;
public static final double WINDOW_PREF_HEIGHT = 760;
```

> ⚠️ Check whether `UiConstants` is a `class` or `interface`. Add constants in the same style as existing entries.

**Step C — Replace literals in `DatingApp.java`:**

*Before:*
```java
        primaryStage.setMinWidth(800);   primaryStage.setMinHeight(600);
        primaryStage.setMaxWidth(1600);  primaryStage.setMaxHeight(1000);
        primaryStage.setWidth(1000);     primaryStage.setHeight(760);
```

*After:*
```java
        primaryStage.setMinWidth(UiConstants.WINDOW_MIN_WIDTH);
        primaryStage.setMinHeight(UiConstants.WINDOW_MIN_HEIGHT);
        primaryStage.setMaxWidth(UiConstants.WINDOW_MAX_WIDTH);
        primaryStage.setMaxHeight(UiConstants.WINDOW_MAX_HEIGHT);
        primaryStage.setWidth(UiConstants.WINDOW_PREF_WIDTH);
        primaryStage.setHeight(UiConstants.WINDOW_PREF_HEIGHT);
```

**Compile check:** `mvn compile -q`

---

### ✅ TASK-1.7: Add Magic Number Constants for UI Controller Truncation Limits [Fixes: MED-03]

**Files:** `UiConstants`, `LoginController`, `ChatController`

**Step A — Locate magic numbers:**
```powershell
rg "24|35" src/main/java/datingapp/ui/screen/LoginController.java -n
rg "24|35" src/main/java/datingapp/ui/screen/ChatController.java -n
```

Confirm these are the truncation/preview values described in MED-03.

**Step B — Add to `UiConstants`:**
```java
// ══════ TEXT TRUNCATION LIMITS ══════
public static final int NAME_FORMAT_MAX_CHARS       = 24;
public static final int CONVERSATION_PREVIEW_CHARS  = 35;
```

**Step C — Replace in each controller:**

`LoginController` — find the `formatFilter()` or equivalent method using `24`:
```java
// Before
text.substring(0, 24)
// After
text.substring(0, UiConstants.NAME_FORMAT_MAX_CHARS)
```

`ChatController` — find the `ConversationListCell.updateItem()` using `35`:
```java
// Before
preview.substring(0, 35)
// After
preview.substring(0, UiConstants.CONVERSATION_PREVIEW_CHARS)
```

**Compile check:** `mvn compile -q`

---

### ✅ TASK-1.8: Add Auth-Absence Comment to `RestApiServer` [Fixes: HIGH-02 partial, ORG-03]

**File:** `RestApiServer`
**Risk:** Zero — comment only.

**Locate:**
```powershell
rg "registerRoutes|app\.get|app\.post" src/main/java/datingapp/app/api/RestApiServer.java -n | Select-Object -First 10
```

Find the `registerRoutes()` method opening. Add the comment block immediately before the first `app.get(` or `app.post(` call:

```java
    private void registerRoutes() {
        // ═══════════════════════════════════════════════════════════════════
        // AUTHENTICATION NOTE: This REST API is intentionally unauthenticated.
        // It is designed for local IPC use only (CLI tools, local admin scripts).
        // Do NOT expose these endpoints over a public network without adding
        // authentication middleware (e.g., app.before() with a shared secret).
        // All 10 routes below operate without any identity verification.
        // ═══════════════════════════════════════════════════════════════════
```

**Compile check:** `mvn compile -q`

---

### ✅ TASK-1.9: Fix `java.util.ArrayList` Fully-Qualified Usage [Fixes: LOW-02]

**File:** `MatchPreferences` (inside `Dealbreakers.Evaluator`)

**Locate:**
```powershell
rg "java\.util\.ArrayList" src/main/java/datingapp/core/profile/MatchPreferences.java -n
```

Expected: one match at line ~627.

*Before:*
```java
        List<String> failures = new java.util.ArrayList<>();
```

*After:*
```java
        List<String> failures = new ArrayList<>();
```

Add `import java.util.ArrayList;` at the top of `MatchPreferences.java` if not already present.

**Compile check:** `mvn compile -q`

---

### ✅ TASK-1.10: Remove Unused `AppConfig config` Parameter from `Dealbreakers.Evaluator.passes()` [Fixes: LOW-03]

**File:** `MatchPreferences`
**Risk:** Medium — API change. Callers must be updated.

**Step A — Locate all callers:**
```powershell
rg "Dealbreakers\.Evaluator\.passes|Evaluator\.passes" src -n -g "*.java"
```

> ⚠️ The tests (`DealbreakersEvaluatorTest`) pass `AppConfig.defaults()` as the third argument. You must update all call sites after changing the signature.

**Step B — Change the method signature in `MatchPreferences.java`:**

*Before:*
```java
        public static boolean passes(User seeker, User candidate, AppConfig config) {
            Objects.requireNonNull(config, "config cannot be null");
```

*After:*
```java
        public static boolean passes(User seeker, User candidate) {
```

Remove the `requireNonNull(config, ...)` line and any use of `config` in the body (there are none per the audit).

**Step C — Update all call sites.** For each caller found in Step A:
- Remove the `AppConfig` argument from the call.
- Example: `Evaluator.passes(seeker, candidate, AppConfig.defaults())` → `Evaluator.passes(seeker, candidate)`

**Step D — Remove the `AppConfig` import** from `MatchPreferences.java` if no other usage remains:
```powershell
rg "AppConfig" src/main/java/datingapp/core/profile/MatchPreferences.java -n
```

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=DealbreakersEvaluatorTest test
```

---

### ✅ Phase 1 Verification

```powershell
mvn test -q
# Expected: BUILD SUCCESS — same test count as baseline, no regressions.
```

Record: Fixes applied — HIGH-05, HIGH-04 (partial), HIGH-03, MED-07, MED-02, MED-01, MED-03, HIGH-02+ORG-03, LOW-02, LOW-03.

---

## ═══════════════════════════════════════════
## PHASE 2 — Design Changes
## ═══════════════════════════════════════════

> **Goal:** API and behavioral changes that affect multiple files or require new types.
> **Compile after each task. Test after each group.**

---

### ✅ TASK-2.1: Remove Null-Dependency Constructors from `TrustSafetyService` [Fixes: CRIT-04]

**File:** `TrustSafetyService`
**Impact:** Tests that used the no-arg or 2-arg constructors must switch to the full constructor with `TestStorages`.

**Step A — Locate all constructors:**
```powershell
rg "public TrustSafetyService" src/main/java/datingapp/core/matching/TrustSafetyService.java -n
```

You will see (approximately):
- Line 35: `public TrustSafetyService()` — calls `this(DEFAULT_VERIFICATION_TTL, new Random())`
- Line 40: `public TrustSafetyService(Duration verificationTtl, Random random)` — calls `this(null, null, null, null, ...)`
- Line 44+: `public TrustSafetyService(...)` — the one with all 4 storage deps (keep this one)
- Line 58+: possibly another with fewer params — inspect and decide

**Step B — Delete the null-passing constructors** (lines 35–43 approximately). Keep only the full constructor(s) that use `Objects.requireNonNull` for all 4 storage dependencies.

**Step C — Find and fix all test usages:**
```powershell
rg "new TrustSafetyService\(\)" src/test -n -g "*.java"
rg "new TrustSafetyService\(DEFAULT_" src/test -n -g "*.java"
rg "new TrustSafetyService\(Duration|new TrustSafetyService\(trust" src/test -n -g "*.java"
```

For each test that called the removed constructors, replace with the full constructor using `TestStorages`:
```java
// Pattern for test setup
var trustSafetyStorage = new TestStorages.TrustSafety();
var interactionStorage = new TestStorages.Interactions();
var userStorage = new TestStorages.Users();
var service = new TrustSafetyService(
    trustSafetyStorage,
    interactionStorage,
    userStorage,
    AppConfig.defaults()
);
```
> ⚠️ Verify the exact full constructor signature before writing the test replacement. Read the remaining constructor in `TrustSafetyService.java`.

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=TrustSafetyServiceTest test
```

---

### ✅ TASK-2.2: Make `MatchQualityService.computeQuality()` Return `Optional<MatchQuality>` [Fixes: CRIT-07 partial]

**File:** `MatchQualityService`
**Impact:** All callers of `computeQuality()` must be updated.

**Step A — Locate current method signature and throws:**
```powershell
rg "computeQuality|IllegalArgumentException" src/main/java/datingapp/core/matching/MatchQualityService.java -n
```

**Step B — Find all callers:**
```powershell
rg "computeQuality" src -n -g "*.java"
```

**Step C — Change `MatchQualityService.computeQuality()`:**

*Before (approximate):*
```java
    public MatchQuality computeQuality(UUID perspectiveUserId, UUID otherUserId) {
        User me = userStorage.get(perspectiveUserId);
        User them = userStorage.get(otherUserId);
        if (me == null) throw new IllegalArgumentException("User not found: " + perspectiveUserId);
        if (them == null) throw new IllegalArgumentException("User not found: " + otherUserId);
        // ... computation
        return new MatchQuality(...);
    }
```

*After:*
```java
    public Optional<MatchQuality> computeQuality(UUID perspectiveUserId, UUID otherUserId) {
        User me = userStorage.get(perspectiveUserId);
        User them = userStorage.get(otherUserId);
        if (me == null || them == null) {
            logger.warn("computeQuality: user not found — perspective={} other={}", perspectiveUserId, otherUserId);
            return Optional.empty();
        }
        // ... computation unchanged
        return Optional.of(new MatchQuality(...));
    }
```

Add `import java.util.Optional;` if missing.

**Step D — Update callers.** For each caller found in Step B, handle the `Optional`:
```java
// Typical caller pattern
Optional<MatchQuality> quality = matchQualityService.computeQuality(userId, otherId);
quality.ifPresent(q -> { /* use q */ });
// OR for callers that need a default:
MatchQuality quality = matchQualityService.computeQuality(userId, otherId).orElse(null);
if (quality == null) return; // skip candidate
```

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=MatchQualityServiceTest test
```

---

### ✅ TASK-2.3: Wire `SendResult` Through `ChatViewModel` and `ChatController` [Fixes: CRIT-02]

**Files:** `ChatViewModel`, `ChatController`

**Step A — Verify current discard in `ChatViewModel`:**
```powershell
rg "sendMessage|SendResult" src/main/java/datingapp/ui/viewmodel/ChatViewModel.java -n
```

Expected: `messagingService.sendMessage(...)` return value is ignored.

**Step B — Modify `ChatViewModel.sendMessage()` to return `boolean` (or a status):**

Read the method (line ~258) first. Then:

*Before (approximate):*
```java
    public void sendMessage(String text) {
        // ... validation ...
        messagingService.sendMessage(currentUser.getId(), otherId, text.trim());
    }
```

*After:*
```java
    public boolean sendMessage(String text) {
        // ... validation (return false on validation failure) ...
        var result = messagingService.sendMessage(currentUser.getId(), otherId, text.trim());
        if (!result.success()) {
            notifyError("Failed to send message: " + result.errorMessage());
            return false;
        }
        // refresh message list after successful send
        loadMessages();
        return true;
    }
```

> ⚠️ Verify `messagingService.sendMessage()` returns `ConnectionService.SendResult`. Check the import path. Verify `notifyError()` / `errorHandler` exists in the ViewModel (it should per the `ViewModelErrorSink` pattern).

**Step C — Modify `ChatController` to only clear input on success:**

*Before (line ~250–253):*
```java
                viewModel.sendMessage(text);
                messageArea.clear();
```

*After:*
```java
                boolean sent = viewModel.sendMessage(text);
                if (sent) {
                    messageArea.clear();
                }
```

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=ChatViewModelTest test
```
(If no `ChatViewModelTest` exists, this is a gap to note but not a blocker — verify compile only.)

---

### TASK-2.4: Introduce `TransitionResult` in `ConnectionService` [Fixes: CRIT-05] ✅ COMPLETE

**File:** `ConnectionService`
**Impact:** All callers of `requestFriendZone`, `acceptFriendZone`, `declineFriendZone`, `gracefulExit` must be updated. Tests in `RelationshipTransitionServiceTest` that use `assertThrows(TransitionValidationException.class, ...)` must change to `assertFalse(result.success())`.

**Step A — Read the file thoroughly:**
```powershell
bat src/main/java/datingapp/core/connection/ConnectionService.java
```

**Step B — Add `TransitionResult` as a public static record inside `ConnectionService`** (mirror the existing `SendResult` pattern at the bottom of the file):

```java
    public static record TransitionResult(
            boolean success,
            String errorMessage) {

        public TransitionResult {
            if (!success) Objects.requireNonNull(errorMessage, "errorMessage required on failure");
        }

        public static TransitionResult success() {
            return new TransitionResult(true, null);
        }

        public static TransitionResult failure(String error) {
            return new TransitionResult(false, error);
        }
    }
```

**Step C — Refactor `requestFriendZone()`:**

*Before (signature):*
```java
    public FriendRequest requestFriendZone(UUID fromUserId, UUID targetUserId) {
```

**Decision:** `requestFriendZone` currently returns `FriendRequest` on success. Keep the `FriendRequest` in the result by creating a richer result. Alternatively, return `TransitionResult` that carries the `FriendRequest` as an optional field.

Use the richer pattern:
```java
    public static record TransitionResult(
            boolean success,
            FriendRequest friendRequest,  // non-null only on requestFriendZone success
            String errorMessage) {

        public static TransitionResult success() {
            return new TransitionResult(true, null, null);
        }

        public static TransitionResult successWithRequest(FriendRequest req) {
            return new TransitionResult(true, req, null);
        }

        public static TransitionResult failure(String error) {
            return new TransitionResult(false, null, error);
        }
    }
```

*After `requestFriendZone` (approximate):*
```java
    public TransitionResult requestFriendZone(UUID fromUserId, UUID targetUserId) {
        Match match = findActiveMatch(fromUserId, targetUserId);
        if (match == null) {
            return TransitionResult.failure("An active match is required to request the Friend Zone.");
        }
        boolean duplicate = communicationStorage.findPendingFriendRequest(fromUserId, targetUserId).isPresent();
        if (duplicate) {
            return TransitionResult.failure("A friend zone request is already pending between these users.");
        }
        FriendRequest req = FriendRequest.create(fromUserId, targetUserId);
        communicationStorage.saveFriendRequest(req);
        return TransitionResult.successWithRequest(req);
    }
```

Repeat the same pattern for `acceptFriendZone`, `declineFriendZone`, and `gracefulExit` — each currently `void` or returning `void` while throwing. Change return type to `TransitionResult`, replace every `throw new TransitionValidationException(...)` with `return TransitionResult.failure(...)`.

**Step D — Update callers in `MatchingHandler.java`:**
```powershell
rg "requestFriendZone|acceptFriendZone|declineFriendZone|gracefulExit" src/main/java/datingapp/app/cli/MatchingHandler.java -n
```

For each call site, capture the result and handle failure:
```java
// Before
transitionService.requestFriendZone(currentUser.getId(), otherUserId);

// After
var result = transitionService.requestFriendZone(currentUser.getId(), otherUserId);
if (!result.success()) {
    output.println("Could not request friend zone: " + result.errorMessage());
    return;
}
```

**Step E — Update `RelationshipTransitionServiceTest`:**
```powershell
rg "assertThrows.*TransitionValidationException|requestFriendZone|acceptFriendZone|declineFriendZone|gracefulExit" src/test/java/datingapp/core/RelationshipTransitionServiceTest.java -n
```

For each test that was `assertThrows(TransitionValidationException.class, () -> service.X(...))`:
```java
// Before
assertThrows(TransitionValidationException.class, () -> service.requestFriendZone(aliceId, charlieId));

// After
var result = service.requestFriendZone(aliceId, charlieId);
assertFalse(result.success());
// Optionally: assertThat(result.errorMessage()).contains("active match");
```

For tests that returned `FriendRequest` and asserted on it:
```java
// Before
FriendRequest request = service.requestFriendZone(aliceId, bobId);
assertNotNull(request);

// After
var result = service.requestFriendZone(aliceId, bobId);
assertTrue(result.success());
assertNotNull(result.friendRequest());
```

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=RelationshipTransitionServiceTest test
```

---

### ✅ TASK-2.5: Fix `RecommendationService` — Use Injected `Clock` Everywhere [Fixes: HIGH-06]

**File:** `RecommendationService`
**Risk:** Medium. Be careful that `LocalDate.now(clock)` uses the field `this.clock`.

**Locate the 3 bypass calls:**
```powershell
rg "AppClock\.now\(\)|AppClock\.today" src/main/java/datingapp/core/matching/RecommendationService.java -n
```

Expected: ~3 lines (lines ~364, ~377, ~544).

**Change line ~364 and ~377 (both use `AppClock.today(config.userTimeZone())`):**

*Before:*
```java
        LocalDate today = AppClock.today(config.userTimeZone());
```

*After:*
```java
        LocalDate today = LocalDate.now(clock).atStartOfDay(config.userTimeZone()).toLocalDate();
```
> ⚠️ Read the exact context of these two lines first. They may differ slightly. The goal is to use `clock` (the injected field) instead of `AppClock`. Verify `clock` is the instance field declared in the class.

**Change line ~544 (`AppClock.now()` for duration calculation):**

*Before:*
```java
        Duration sinceUpdate = Duration.between(candidate.getUpdatedAt(), AppClock.now());
```

*After:*
```java
        Duration sinceUpdate = Duration.between(candidate.getUpdatedAt(), Instant.now(clock));
```

**Verify no more `AppClock` bypasses remain:**
```powershell
rg "AppClock" src/main/java/datingapp/core/matching/RecommendationService.java -n
```

Remove the `import datingapp.core.AppClock;` line if no usages remain.

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=RecommendationServiceTest test
```
(If test class name differs, find it: `rg "RecommendationService" src/test -n -g "*.java"`)

---

### ✅ TASK-2.6: Migrate `UndoService` to `AppClock.now()` [Fixes: MED-05]

**File:** `UndoService`
**Dependency:** None. Stand-alone change.

**Current state:** `UndoService` injects a `Clock` field and uses `Instant.now(clock)`. The no-arg constructor defaults to `Clock.systemUTC()` rather than `AppClock.clock()`.

**Approach:** Make no-arg constructor delegate to `AppClock.clock()` so test-frozen clocks propagate:

**Locate the no-arg constructor:**
```powershell
rg "Clock\.systemUTC|Clock\.system" src/main/java/datingapp/core/matching/UndoService.java -n
```

*Before (line ~30):*
```java
    public UndoService(InteractionStorage interactionStorage, Undo.Storage undoStorage, AppConfig config) {
        this(interactionStorage, undoStorage, config, Clock.systemUTC());
    }
```

*After:*
```java
    public UndoService(InteractionStorage interactionStorage, Undo.Storage undoStorage, AppConfig config) {
        this(interactionStorage, undoStorage, config, AppClock.clock());
    }
```

Add `import datingapp.core.AppClock;` if missing.

> ℹ️ This is the simpler fix. The alternative is to migrate all `Instant.now(clock)` calls to `AppClock.now()`, but using `AppClock.clock()` as the default achieves the same result for test controllability.

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=UndoServiceTest test
```

---

### ❌ TASK-2.7: [INCOMPLETE - missing isMatch(WantsKids) and isMatch(Education)] Make `LifestyleMatcher.isAcceptable()` Generic [Fixes: HIGH-08, LOW-01]

**File:** `LifestyleMatcher`
**Risk:** Low. The 5 typed overloads are replaced by 1 generic method. Java's type inference handles call sites transparently — callers do NOT need to change.

**Step A — Read current file:**
```powershell
bat src/main/java/datingapp/core/matching/LifestyleMatcher.java
```

**Step B — Replace the 5 `isAcceptable` overloads with one generic method:**

*Before (5 identical-body methods):*
```java
    public static boolean isAcceptable(Lifestyle.Smoking value, Set<Lifestyle.Smoking> allowed) {
        if (allowed == null || allowed.isEmpty()) { return true; }
        return value != null && allowed.contains(value);
    }
    public static boolean isAcceptable(Lifestyle.Drinking value, Set<Lifestyle.Drinking> allowed) {
        if (allowed == null || allowed.isEmpty()) { return true; }
        return value != null && allowed.contains(value);
    }
    // ... 3 more identical
```

*After (one generic method):*
```java
    /**
     * Returns true if {@code value} is contained in {@code allowed}, or if {@code allowed}
     * is null or empty (meaning no preference / all accepted).
     */
    public static <E extends Enum<E>> boolean isAcceptable(E value, Set<E> allowed) {
        if (allowed == null || allowed.isEmpty()) { return true; }
        return value != null && allowed.contains(value);
    }
```

**Step C — Add the 2 missing `isMatch` overloads for `WantsKids` and `Education` [Fixes: LOW-01]:**

The existing `isMatch` overloads all use the pattern `a != null && b != null && a == b`. Add:

```java
    public static boolean isMatch(Lifestyle.WantsKids a, Lifestyle.WantsKids b) {
        return a != null && b != null && a == b;
    }

    public static boolean isMatch(Lifestyle.Education a, Lifestyle.Education b) {
        return a != null && b != null && a == b;
    }
```

**Step D — Verify no callers broke:**
```powershell
rg "isAcceptable|LifestyleMatcher" src -n -g "*.java" | Select-Object -First 30
```

Java resolves the generic method at compile time — all previous call sites with concrete enum types still compile.

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=LifestyleMatcherTest test
```
(Find exact test class: `rg "LifestyleMatcher" src/test -n -g "*.java"`)

---

### ✅ TASK-2.8: Register `ValidationService` in `ServiceRegistry` [Fixes: MED-04]

**Files:** `ServiceRegistry`, `ValidationService`, callers

**Step A — Check current `ServiceRegistry` constructor:**
```powershell
bat src/main/java/datingapp/core/ServiceRegistry.java
```

**Step B — Add `validationService` field and getter:**

In `ServiceRegistry.java`, locate where other services are declared (e.g., `private final MatchingService matchingService;`). Add:

```java
    private final ValidationService validationService;
```

In the constructor's parameter list and body, add:
```java
    // Constructor param (add alongside other service params)
    ValidationService validationService,

    // Constructor body (add alongside other assignments)
    this.validationService = Objects.requireNonNull(validationService, "validationService");
```

Add getter:
```java
    public ValidationService getValidationService() { return validationService; }
```

**Step C — Update `ApplicationStartup.initialize()`:**
```powershell
rg "ValidationService|new ServiceRegistry" src/main/java/datingapp/app/bootstrap/ApplicationStartup.java -n
```

In `ApplicationStartup`, construct a `ValidationService` with the loaded `AppConfig` and pass it to `ServiceRegistry`:
```java
ValidationService validationService = new ValidationService(config);
// then pass to ServiceRegistry constructor
```

**Step D — Verify `ValidationService` has a constructor accepting `AppConfig`:**
```powershell
rg "public ValidationService" src/main/java/datingapp/core/profile/ValidationService.java -n
```

If it only has a no-arg constructor `this(AppConfig.defaults())`, the one-arg constructor may already exist. If not:

*Before:*
```java
    public ValidationService() {
        this(AppConfig.defaults());
    }
    public ValidationService(AppConfig config) {
        ...
    }
```

*After (no change needed if the 1-arg constructor already exists):* — just use it in `ApplicationStartup`.

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=ValidationServiceTest test
```

---

### ✅ TASK-2.9: Scope `AppConfig.defaults()` Bypasses in Domain Model [Fixes: CRIT-03 partial]

**Files:** `User.java`, `MatchPreferences.java`

These files create local `AppConfig.defaults()` instances inside methods, ignoring the externally loaded config.

**Step A — Locate and fix `User.java` line ~505:**
```powershell
rg "AppConfig config = AppConfig.defaults()" src/main/java/datingapp/core/model/User.java -n
```

Read the surrounding method. The method (likely `setAgeRange()` or similar) creates a local config just to read `minAge()`/`maxAge()`. Replace with the class-level static `CONFIG` field that `User.java` should already have:

*Before:*
```java
        AppConfig config = AppConfig.defaults();
        if (minAge < config.minAge() || ...) {
```

*After:*
```java
        // Use class-level CONFIG constant (defined at top of User.java)
        if (minAge < CONFIG.minAge() || ...) {
```

Verify the class-level field exists:
```powershell
rg "private static final AppConfig CONFIG" src/main/java/datingapp/core/model/User.java -n
```

If it doesn't exist, add it:
```java
private static final AppConfig CONFIG = AppConfig.defaults();
```

**Step B — Locate and fix `MatchPreferences.java` line ~400:**
```powershell
rg "AppConfig heightConfig = AppConfig.defaults()" src/main/java/datingapp/core/profile/MatchPreferences.java -n
```

Same approach — replace with a class-level `CONFIG` constant or inline reference.

**Step C — Fix ViewModels (3 files): `PreferencesViewModel`, `LoginViewModel`, `ProfileViewModel`**

These have `private static final AppConfig CONFIG = AppConfig.defaults();`. The cleanest fix is to pass `AppConfig` through the `ViewModelFactory` to each ViewModel constructor:

```powershell
rg "private static final AppConfig CONFIG" src/main/java/datingapp/ui/viewmodel -n -g "*.java"
```

For each ViewModel:
1. Change the field to an instance field: `private final AppConfig config;`
2. Accept `AppConfig config` in the constructor
3. Update `ViewModelFactory` to pass `services.getConfig()` when constructing each ViewModel

**Locate `ViewModelFactory`:**
```powershell
bat src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java
```

For each ViewModel constructor call in `ViewModelFactory`, add `services.getConfig()` as a parameter.

**Step D — Fix `ProfileController.java` lines ~476–477:**
```powershell
rg "AppConfig.defaults\(\)" src/main/java/datingapp/ui/screen/ProfileController.java -n
```

The controller should get config from its ViewModel, not from `AppConfig.defaults()`. Read the ViewModel used by `ProfileController`, expose config bounds as ViewModel properties, then read them in the Controller.

Alternatively (simpler): inject the config via the ViewModel's existing config field and add accessor methods:
```java
// In ProfileViewModel (after Step C above)
public int getMinHeightCm() { return config.minHeightCm(); }
public int getMaxHeightCm() { return config.maxHeightCm(); }
```

Then in `ProfileController`:
```java
// Before
int minHeight = datingapp.core.AppConfig.defaults().minHeightCm();
int maxHeight = datingapp.core.AppConfig.defaults().maxHeightCm();

// After
int minHeight = viewModel.getMinHeightCm();
int maxHeight = viewModel.getMaxHeightCm();
```

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn test -q
```

---

### ✅ TASK-2.10: Add Login Guard in `Main.java` and Scope Handler Initialization [Fixes: MED-08]

**File:** `Main.java`

**Step A — Read the main dispatch loop:**
```powershell
bat src/main/java/datingapp/app/Main.java
```

**Step B — Identify the dispatch switch/if-else** and locate where options 3–N are handled.

**Step C — Add the login guard.** Find the point after the user enters a menu choice but before the handler dispatch:

```java
// Add this guard before handling any option that requires a logged-in user
int option = readOption(...);
if (option >= 3 && session.getCurrentUser() == null) {
    output.println("Please select a user first (option 1 or 2).");
    continue;
}
// ... existing switch/dispatch ...
```

> ⚠️ Read the exact structure first. Do not blindly paste — the option numbering and dispatch mechanism must be preserved exactly.

**Step D — Scope the 7 mutable static handler fields.** If handlers are declared as `private static HandlerType handler;` at class level:

*Before (class body):*
```java
private static MatchingHandler matchingHandler;
private static ProfileHandler profileHandler;
// ...
```

*After (localized inside `main()` or the init method):*
```java
// Move into main() or a local initializeHandlers() method called once
MatchingHandler matchingHandler = new MatchingHandler(new MatchingHandler.Dependencies(...));
ProfileHandler profileHandler = new ProfileHandler(...);
// ...
```

> ⚠️ If these are accessed from a nested class or lambda, you may need `final` local vars or an enclosing record/object. Read the file to understand the scope before changing.

**Compile check:** `mvn compile -q`

---

### ✅ TASK-2.11: Move Activity-Score Thresholds out of `RecommendationService` [Fixes: CRIT-06]

**File:** `RecommendationService`, `AppConfig` (or a constants class)

**Step A — Locate the hardcoded thresholds:**
```powershell
rg "hours < 1|hours < 24|hours < 72|hours < 168|hours < 720" src/main/java/datingapp/core/matching/RecommendationService.java -n
```

**Step B — Create named constants.** Given project convention, if the thresholds are tunable add them to `AppConfig`. If they are fixed algorithm constants, add a private constants section inside `RecommendationService`:

```java
// ══════ ACTIVITY SCORE THRESHOLDS (hours since last active) ══════
private static final long ACTIVITY_VERY_RECENT_HOURS  = 1;
private static final long ACTIVITY_RECENT_HOURS       = 24;
private static final long ACTIVITY_MODERATE_HOURS     = 72;
private static final long ACTIVITY_WEEKLY_HOURS       = 168;
private static final long ACTIVITY_MONTHLY_HOURS      = 720;

// ══════ ACTIVITY SCORE VALUES ══════
private static final double ACTIVITY_SCORE_VERY_RECENT = 1.0;
private static final double ACTIVITY_SCORE_RECENT      = 0.9;
private static final double ACTIVITY_SCORE_MODERATE    = 0.7;
private static final double ACTIVITY_SCORE_WEEKLY      = 0.5;
private static final double ACTIVITY_SCORE_MONTHLY     = 0.3;
private static final double ACTIVITY_SCORE_INACTIVE    = 0.1;
```

**Step C — Replace the magic numbers in `calculateActivityScore()`:**

*Before:*
```java
        if (hours < 1)   return 1.0;
        if (hours < 24)  return 0.9;
        if (hours < 72)  return 0.7;
        if (hours < 168) return 0.5;
        if (hours < 720) return 0.3;
        return 0.1;
```

*After:*
```java
        if (hours < ACTIVITY_VERY_RECENT_HOURS)  return ACTIVITY_SCORE_VERY_RECENT;
        if (hours < ACTIVITY_RECENT_HOURS)        return ACTIVITY_SCORE_RECENT;
        if (hours < ACTIVITY_MODERATE_HOURS)      return ACTIVITY_SCORE_MODERATE;
        if (hours < ACTIVITY_WEEKLY_HOURS)        return ACTIVITY_SCORE_WEEKLY;
        if (hours < ACTIVITY_MONTHLY_HOURS)       return ACTIVITY_SCORE_MONTHLY;
        return ACTIVITY_SCORE_INACTIVE;
```

**Compile check:** `mvn compile -q`

---

### ✅ TASK-2.12: Fix `RecommendationService.resolveUsers()` Unnecessary `HashSet` Wrapper [Fixes: MED-11]

**File:** `RecommendationService`

**Locate:**
```powershell
rg "new HashSet" src/main/java/datingapp/core/matching/RecommendationService.java -n
```

*Before:*
```java
        new HashSet<>(ids)
```

*After:*
```java
        Set.copyOf(ids)
```

Or if ordering/mutability matters, read the context and use the appropriate alternative.

**Compile check:** `mvn compile -q`

---

### ✅ Phase 2 Verification

```powershell
mvn test -q
# Expected: BUILD SUCCESS — same or more tests than baseline, all green.
```

Record: Fixes applied — CRIT-04, CRIT-07, CRIT-02, CRIT-05, HIGH-06, MED-05, HIGH-08, LOW-01, MED-04, CRIT-03 (partial), MED-08, CRIT-06, MED-11.

---

## ═══════════════════════════════════════════
## PHASE 3 — Organizational & Scalability
## ═══════════════════════════════════════════

> **Goal:** Structural improvements. No behavioral regressions.
> **These are safe changes — no API surface changes except CRIT-01.**

---

### ✅ TASK-3.1: Push SQL Filters Into `JdbiUserStorage.findActive()` [Fixes: CRIT-01]

**Files:** `UserStorage` (interface), `JdbiUserStorage`, `CandidateFinder`, `TestStorages`
**Impact:** High. This changes the storage interface — all implementations and test stubs must be updated.

**Step A — Read the current interface:**
```powershell
bat src/main/java/datingapp/core/storage/UserStorage.java
```

**Step B — Add a new method to `UserStorage` interface:**

```java
/**
 * Pre-filtered candidate query for a given seeker's basic criteria.
 * All parameters use the seeker's preferences to push filtering to SQL level.
 * Age range uses birthdate arithmetic: candidate must be born between
 * (today - maxAge years) and (today - minAge years).
 *
 * @param excludeId     the seeker's own UUID (exclude from results)
 * @param genders       set of Gender values the seeker is interested in
 * @param minAge        minimum acceptable candidate age (years)
 * @param maxAge        maximum acceptable candidate age (years)
 * @param seekerLat     seeker's latitude (degrees)
 * @param seekerLon     seeker's longitude (degrees)
 * @param maxDistanceKm maximum distance filter (bounding-box approximation)
 * @return active users matching base criteria, unsorted
 */
List<User> findCandidates(UUID excludeId, Set<Gender> genders,
                          int minAge, int maxAge,
                          double seekerLat, double seekerLon, int maxDistanceKm);
```

> ⚠️ If `Gender` is a standalone enum in `core/model/`, import it. Verify the exact path with:
> `fd Gender.java src/main/java -t f`

**Step C — Implement in `JdbiUserStorage`:**

The H2 database does not have native haversine support. Use a latitude/longitude bounding box approximation:
- 1 degree latitude ≈ 111 km
- 1 degree longitude ≈ 111 km × cos(lat)

```java
@Override
public List<User> findCandidates(UUID excludeId, Set<Gender> genders,
                                 int minAge, int maxAge,
                                 double seekerLat, double seekerLon, int maxDistanceKm) {
    double latDelta = maxDistanceKm / 111.0;
    double lonDelta = maxDistanceKm / (111.0 * Math.cos(Math.toRadians(seekerLat)));
    double minLat = seekerLat - latDelta;
    double maxLat = seekerLat + latDelta;
    double minLon = seekerLon - lonDelta;
    double maxLon = seekerLon + lonDelta;

    // birthdate range for age filter
    LocalDate today = LocalDate.now();
    LocalDate minBirthDate = today.minusYears(maxAge);  // oldest acceptable
    LocalDate maxBirthDate = today.minusYears(minAge);  // youngest acceptable

    // Gender filter — build IN clause from the set
    String genderInClause = genders.stream()
            .map(g -> "'" + g.name() + "'")
            .collect(Collectors.joining(", ", "(", ")"));

    // Note: genderInClause is built from a trusted enum set, not user input — safe
    String sql = "SELECT " + ALL_COLUMNS + " FROM users "
            + "WHERE id <> :excludeId "
            + "  AND state = 'ACTIVE' "
            + "  AND gender IN " + genderInClause
            + "  AND birth_date BETWEEN :minBirthDate AND :maxBirthDate "
            + "  AND latitude  BETWEEN :minLat AND :maxLat "
            + "  AND longitude BETWEEN :minLon AND :maxLon";

    return jdbi.withHandle(handle -> handle.createQuery(sql)
            .bind("excludeId", excludeId)
            .bind("minBirthDate", minBirthDate)
            .bind("maxBirthDate", maxBirthDate)
            .bind("minLat", minLat)
            .bind("maxLat", maxLat)
            .bind("minLon", minLon)
            .bind("maxLon", maxLon)
            .map(new Mapper())
            .list());
}
```

> ⚠️ Check whether `latitude` and `longitude` columns exist in the `users` table schema (`SchemaInitializer.java`). If they do not exist, add them as a migration in `MigrationRunner` before implementing this. Verify:
> ```powershell
> rg "latitude|longitude" src/main/java/datingapp/storage/schema/SchemaInitializer.java -n
> ```

**Step D — Update `CandidateFinder.findCandidatesForUser()`:**

*Before:*
```java
            List<User> activeUsers = userStorage.findActive();
            Set<UUID> excluded = ...;
            List<User> candidates = findCandidates(currentUser, activeUsers, excluded);
```

*After:*
```java
            // Push primary filters to SQL; run secondary filters (interactions, dealbreakers) in-memory
            List<User> preFiltered = userStorage.findCandidates(
                currentUser.getId(),
                currentUser.getInterestedIn(),
                currentUser.getMinAge(),
                currentUser.getMaxAge(),
                currentUser.getLatitude(),
                currentUser.getLongitude(),
                currentUser.getMaxDistanceKm()
            );
            Set<UUID> excluded = ...; // interaction/block exclusions
            List<User> candidates = findCandidates(currentUser, preFiltered, excluded);
```

> ⚠️ Verify `User` has `getLatitude()`, `getLongitude()`, `getMinAge()`, `getMaxAge()`, `getMaxDistanceKm()` — they must exist for this to work. If any are missing, check the domain model and schema.

**Step E — Update `TestStorages` to implement the new interface method:**
```powershell
bat src/test/java/datingapp/testutil/TestStorages.java
```

Find the `Users` inner class that implements `UserStorage`. Add the new method:

```java
@Override
public List<User> findCandidates(UUID excludeId, Set<Gender> genders,
                                 int minAge, int maxAge,
                                 double seekerLat, double seekerLon, int maxDistanceKm) {
    // Delegate to findActive() and apply same filters in-memory for tests
    return findActive().stream()
            .filter(u -> !u.getId().equals(excludeId))
            .filter(u -> genders.contains(u.getGender()))
            .filter(u -> u.getBirthDate() != null &&
                    ChronoUnit.YEARS.between(u.getBirthDate(), LocalDate.now()) >= minAge &&
                    ChronoUnit.YEARS.between(u.getBirthDate(), LocalDate.now()) <= maxAge)
            .toList();
}
```

**Step F — Update `RecommendationService`** — it also calls `userStorage.findActive()` directly (line ~192). Evaluate if that specific call site can also use `findCandidates`. If it loads all active users for a different purpose (e.g., platform stats), leave `findActive()` for that site.

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=CandidateFinderTest test
```

---

### ✅ TASK-3.2: Wrap Multi-Write `ConnectionService` Methods in JDBI Transactions [Fixes: HIGH-01]

**File:** `ConnectionService`
**Dependency:** TASK-2.4 must be complete (method signatures changed to return `TransitionResult`).

**Step A — Understand how JDBI is used in `ConnectionService`:**
```powershell
rg "jdbi|Handle|inTransaction|@Transaction" src/main/java/datingapp/core/connection/ConnectionService.java -n
```

**Step B — Identify which storage operations need grouping.** Per the issue:
- `acceptFriendZone` — 3 writes across 2 storage objects
- `requestFriendZone` — 2 writes
- `gracefulExit` — 2 writes

**Approach A (if `ConnectionService` holds a `Jdbi` instance):** Use `jdbi.inTransaction()`:

```java
// Before (in acceptFriendZone):
interactionStorage.update(match);
communicationStorage.updateFriendRequest(updated);
communicationStorage.saveNotification(notification);

// After:
jdbi.inTransaction(handle -> {
    interactionStorage.updateWithHandle(handle, match);
    communicationStorage.updateFriendRequestWithHandle(handle, updated);
    communicationStorage.saveNotificationWithHandle(handle, notification);
    return null;
});
```

> ⚠️ This approach requires the storage implementations to accept a `Handle` parameter — a bigger change. If this is infeasible with the current storage abstraction (interface-based injection), use Approach B.

**Approach B (simpler — if using JDBI SqlObject with `@Transaction`):** Mark the JDBI DAO methods that compose multiple writes with `@Transaction`. This works if `ConnectionService` directly calls a single DAO interface that delegates all writes.

Read the actual storage injection pattern first:
```powershell
rg "communicationStorage|interactionStorage" src/main/java/datingapp/core/connection/ConnectionService.java -n | Select-Object -First 15
```

**Approach C (pragmatic — if cross-storage transactions are infeasible with H2/JDBI abstraction):** Document the limitation with a `// KNOWN LIMITATION: no cross-storage transaction` comment and a compensating reversal for each write sequence:

```java
// requestFriendZone — write 1
match.transitionToFriendPending();
interactionStorage.update(match);
try {
    // write 2
    communicationStorage.saveFriendRequest(req);
} catch (Exception e) {
    // compensate: revert match state
    match.revertToActive();
    interactionStorage.update(match);
    return TransitionResult.failure("Storage failure — state reverted: " + e.getMessage());
}
```

Choose the approach that fits the actual storage abstraction. Document the decision in a code comment.

CodeRabbit
Approach C (compensating transactions) provides weaker consistency guarantees.

Lines 1484-1498 suggest a compensating transaction pattern with manual state reversal. While pragmatic, this approach:

Does not provide atomicity (the first write is committed before the second is attempted)
Can fail to revert if the compensating write itself fails
May leave audit logs or cache state inconsistent
If the storage layer cannot support proper transactions, document this as a known data consistency risk rather than presenting it as equivalent to transactional approaches A/B.

Additionally, line 1501 instructs the agent to "choose the approach that fits" without providing criteria for the decision. An AI agent may lack the context to make this architectural choice reliably.

Provide a decision tree or explicit criteria for selecting among the three approaches (e.g., "If ConnectionService has a Jdbi field, use Approach A; if using SqlObject DAOs with @Transaction support, use Approach B; otherwise document as KNOWN LIMITATION").

**Compile check:** `mvn compile -q`

---

### ❌ TASK-3.3: [IN PROGRESS - section headers needed] Add Section Headers and TOC to Large Files [Fixes: HIGH-09, MED-09, MED-10]

**Files:** `ProfileService`, `MatchQualityService`, `MatchPreferences`
**Risk:** Zero — comment-only changes.

**For each file, add at the top of the class body, after the field declarations:**

```java
    // ══════════════════════════════════════════════════════════════════
    // TABLE OF CONTENTS
    // ══════════════════════════════════════════════════════════════════
    // LINE ~XX    SECTION: [Name]
    // LINE ~XX    SECTION: [Name]
    // LINE ~XX    SECTION: [Name]
    // ══════════════════════════════════════════════════════════════════
```

**Then add divider comments before each logical section:**
```java
    // ══════ PROFILE COMPLETION SCORING ══════════════════════════════
```

**ProfileService (793 lines, 6 sections):**
```powershell
bat src/main/java/datingapp/core/profile/ProfileService.java | Select-Object -First 100
```
Identify the 6 section boundaries (completion scoring, tips, achievements, preview, behavior analysis, match counting), note line numbers, add headers.

**MatchQualityService (778 lines):**
```powershell
bat src/main/java/datingapp/core/matching/MatchQualityService.java | Select-Object -First 100
```
Sections: constants, main scoring, distance/age factors, interest scoring, lifestyle scoring, pace scoring, response scoring, nested classes.

**MatchPreferences (789 lines, 4 major types):**
```powershell
bat src/main/java/datingapp/core/profile/MatchPreferences.java | Select-Object -First 50
```
Sections: `MatchPreferences` class, `Interest` enum + Category, `Lifestyle` group, `PacePreferences`, `Dealbreakers` + Builder + Evaluator.

**HIGH-09 additional fix — `ProfileService.getMatchCount()`:**

Locate the method that counts matches by loading all match objects:
```powershell
rg "getMatchCount\|findAll\|count" src/main/java/datingapp/core/profile/ProfileService.java -n
```

If `ProfileService` loads a full list just to call `.size()`, this should use a `COUNT(*)` storage query. Add `long countMatchesForUser(UUID userId)` to `InteractionStorage` interface and implement it in `JdbiMatchmakingStorage`:

```java
// InteractionStorage addition
long countMatchesForUser(UUID userId);

// JdbiMatchmakingStorage implementation
@SqlQuery("SELECT COUNT(*) FROM matches WHERE (user_a_id = :userId OR user_b_id = :userId) AND state = 'ACTIVE'")
long countMatchesForUser(@Bind("userId") UUID userId);
```

Then replace the in-memory count in `ProfileService` with the new query call.

**Compile check:** `mvn compile -q`

---

### ❌ TASK-3.4: [TODO - create ScoringConstants.java] Move `MatchQualityService` Constants to a Holder [Fixes: MED-06]

**File:** `MatchQualityService` — 34 `private static final` constants

**Approach:** Create `ScoringConstants.java` in `core/matching/` (the class that was previously deleted had public constants scattered — this one will be package-private internal constants):

```java
// src/main/java/datingapp/core/matching/ScoringConstants.java
package datingapp.core.matching;

/**
 * Algorithm constants for match quality scoring.
 * These are tuning parameters inlined from MatchQualityService.
 * They do NOT appear in AppConfig because they represent algorithm-specific
 * internal ratios, not user-configurable operational thresholds.
 */
final class ScoringConstants {
    private ScoringConstants() {}

    // ══════ STAR RATING THRESHOLDS ══════
    static final double FIVE_STAR_THRESHOLD   = 0.90;
    static final double FOUR_STAR_THRESHOLD   = 0.75;
    static final double THREE_STAR_THRESHOLD  = 0.60;
    // ... (move all 34 constants here)
}
```

Then in `MatchQualityService`, remove the field declarations and reference `ScoringConstants.X` wherever they were used.

> ⚠️ Read all 34 constants from `MatchQualityService` first before creating the file. Preserve exact names and values.

**Compile check:** `mvn compile -q`

---

### ✅ TASK-3.5: Consolidate Inline Test Stubs into `TestStorages` [Fixes: ORG-02]

**Files:** `TestStorages` + 6 test files with inline `InMemory*` stubs.

**Step A — Find all inline stubs:**
```powershell
rg "class InMemory" src/test -n -g "*.java"
```

For each found class:
1. Copy its implementation into `TestStorages.java` as a named inner class (or verify if it duplicates an existing `TestStorages.*` entry).
2. Delete the inline class from its test file.
3. Update the test's field declaration to use `TestStorages.Users()` (or whichever is appropriate).

**Pattern for TestStorages inner class:**
```java
// In TestStorages.java
public static class Users implements UserStorage {
    private final Map<UUID, User> store = new LinkedHashMap<>();

    @Override
    public void save(User user) { store.put(user.getId(), user); }

    @Override
    public User get(UUID id) { return store.get(id); }

    @Override
    public List<User> findActive() {
        return store.values().stream()
                .filter(u -> u.getState() == UserState.ACTIVE)
                .toList();
    }

    // ... all other interface methods
}
```

**Step B — For each inline stub, verify it doesn't have extra behavior** not present in the centralized `TestStorages` version (some might have overridden methods with specific test assertions). If so, keep that inline stub but note the exception.

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn test -q
```

---

### ❌ TASK-3.6: [TODO - create UiUtils.java] Extract `ProfileController` Helper Logic to `UiUtils` [Fixes: ORG-01]

**File:** `ProfileController` (919 lines)
**Target:** Create `src/main/java/datingapp/ui/util/UiUtils.java`

**Step A — Read and categorize the 919 lines:**
```powershell
bat src/main/java/datingapp/ui/screen/ProfileController.java | Select-Object -First 100
rg "private.*factory\|private.*converter\|private.*cell\|createCell\|makeConverter\|buildFactory" src/main/java/datingapp/ui/screen/ProfileController.java -n
```

**Step B — Create `UiUtils.java`** to hold:
1. Enum-to-display-string converter factories (if present)
2. Reusable `ListCell` factory methods
3. Any other generic UI helper methods not tied to a specific controller

```java
// src/main/java/datingapp/ui/util/UiUtils.java
package datingapp.ui.util;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import java.util.function.Function;

public final class UiUtils {
    private UiUtils() {}

    /** Generic enum display cell factory — maps enum values to their display strings. */
    public static <T extends Enum<T>> Callback<ListView<T>, ListCell<T>> enumCellFactory(
            Function<T, String> displayMapper) {
        return lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayMapper.apply(item));
            }
        };
    }
}
```

**Step C — Extract methods from `ProfileController`** that match the extracted helper pattern. Replace the inline implementations with calls to `UiUtils`.

> ⚠️ This is the most open-ended task. If the methods in `ProfileController` are tightly coupled to `@FXML` fields or specific lifecycle callbacks, only extract the truly reusable parts. Do not break controller functionality. Test the JavaFX UI manually after this task if possible.

**Compile check:** `mvn compile -q`

---

### ❌ TASK-3.7: [TODO - update MatchesController comment] Address the Remaining `TODO` in `MatchesController` [Fixes: ORG-05]

**File:** `MatchesController` (line ~512)

**Locate:**
```powershell
rg "TODO.*presence\|TODO.*status" src/main/java/datingapp/ui/screen/MatchesController.java -n
```

Since user presence is not yet implemented, convert the `TODO` into a tracked comment:

*Before:*
```java
// TODO: Replace with real presence status when user presence tracking is implemented
```

*After:*
```java
// FUTURE(presence-tracking): Replace with live status once UserPresenceService is implemented.
// Currently always shows "Offline". Tracked in: VALID_ISSUES_2026-02-18.md#ORG-05
String presenceStatus = "Offline";
```

Also add `TODO` suppression for any static analysis tool:
```java
// FUTURE(presence-tracking): See VALID_ISSUES_2026-02-18.md#ORG-05 // NOPMD.CommentRequired
```

**Compile check:** `mvn compile -q`

---

### ❌ TASK-3.8: [TODO - add PerformanceMonitor comment] Document `PerformanceMonitor` AppClock Timing Limitation [Fixes: ORG-04]

**File:** `PerformanceMonitor`

**Locate:**
```powershell
rg "AppClock\.now\(\)" src/main/java/datingapp/core/PerformanceMonitor.java -n
```

Add a comment above the `AppClock.now()` usage:

```java
// NOTE: Uses AppClock.now() instead of System.nanoTime() to maintain testability
// consistency across the codebase. Trade-off: in tests with a frozen TestClock,
// all timers will report 0ms elapsed. This is acceptable because PerformanceMonitor
// output is informational only and is not asserted in tests.
```

**Compile check:** `mvn compile -q`

---

### ✅ TASK-3.9: [DONE] Decompose `AppConfig` into Sub-Records [Fixes: HIGH-07]

> **Completed:** Decomposed 57-param flat record into 4 static nested sub-records: `MatchingConfig` (13), `ValidationConfig` (12), `AlgorithmConfig` (16), `SafetyConfig` (16). All 57 backward-compat delegate accessors added. Builder retains flat setters for zero call-site changes. Weight-sum validation moved to `MatchingConfig` compact constructor. `defaults()` delegates to `builder().build()`. 795/795 tests pass. `mvn compile` + `spotless:apply` clean.

**File:** `AppConfig`
**Risk:** High. This changes the API of a widely-used record. Plan carefully.

**Step A — Read the full file:**
```powershell
bat src/main/java/datingapp/core/AppConfig.java
```

**Step B — Count usages before changing:**
```powershell
rg "AppConfig\." src/main -n -g "*.java" | Measure-Object | Select-Object -ExpandProperty Count
```

**Step C — Design the sub-records.** Group the 57 parameters into logical sub-records:

```java
// New sub-records (as top-level classes or static nested records)
public static record MatchingConfig(
    int dailyLikeLimit,
    int maxSwipesPerSession,
    double distanceWeight,
    double interestWeight,
    double lifestyleWeight,
    double ageWeight,
    double paceWeight,
    double responseWeight,
    int minSharedInterests,
    int maxCandidatesPerPage,
    // ... all matching-related params
) {}

public static record ValidationConfig(
    int minAge,
    int maxAge,
    int minHeightCm,
    int maxHeightCm,
    int maxBioLength,
    // ... all validation params
) {}

public static record AlgorithmConfig(
    int nearbyDistanceKm,
    int similarAgeDiff,
    // ... algorithm tuning params
) {}

public static record SafetyConfig(
    int maxReportsBeforeReview,
    int maxBlocksPerDay,
    // ... safety params
) {}
```

**Step D — Update `AppConfig` record** to hold sub-records instead of flat params:
```java
public record AppConfig(
    MatchingConfig matching,
    ValidationConfig validation,
    AlgorithmConfig algorithm,
    SafetyConfig safety
) {
    // Backward compat accessors (delegate to sub-records)
    public int minAge() { return validation.minAge(); }
    public int dailyLikeLimit() { return matching.dailyLikeLimit(); }
    // ... one per param for backward compatibility during migration
}
```

> ⚠️ Adding backward-compat delegating accessors means all existing callers (`CONFIG.minAge()`, `CONFIG.dailyLikeLimit()`, etc.) continue to work without change. This makes the migration safe. Remove the compat accessors in a later task once all callers have been migrated to `CONFIG.validation().minAge()`. **Only remove compat accessors if explicitly asked to in a follow-up.**

**Step E — Update `AppConfig.defaults()` and the builder.**

**Compile check:** `mvn compile -q`

**Test verify:**
```powershell
mvn -Ptest-output-verbose -Dtest=AppConfigTest test
```

---

### ⚠️ Phase 3 Verification — PARTIAL

> **Verified 2026-02-18:** `mvn verify` → BUILD SUCCESS. 795/795 tests pass. 0 Checkstyle violations. 0 PMD violations. Spotless: 143 files clean. JaCoCo: all coverage checks met.
> **Remaining tracked work:** `TASK-3.3` (IN PROGRESS), `TASK-3.4` (TODO), `TASK-3.6` (TODO), `TASK-3.7` (TODO), `TASK-3.8` (TODO).

---

## ═══════════════════════════════════════════
## POST-IMPLEMENTATION — Final Verification
## ═══════════════════════════════════════════

### Step 1 — Format + Full Quality Gate

```powershell
cd "C:\Users\tom7s\Desktop\Claude_Folder_2\Date_Program"
mvn spotless:apply
mvn verify
```

Expected output contains:
- `BUILD SUCCESS`
- `Tests run: NNN, Failures: 0, Errors: 0, Skipped: 0`
- `Coverage ... (X%) is >= (60.00%)`
- No `PMD` violation errors
- No `Checkstyle` violations

### Step 2 — Coverage Check

```powershell
mvn verify 2>&1 | Select-String "Coverage|Tests run:|BUILD" | Select-Object -Last 10
```

### Step 3 — Spot-Check New Code Paths

```powershell
# Verify TrustSafetyService no longer has null constructors
rg "this\(null, null" src/main/java/datingapp/core/matching/TrustSafetyService.java
# Expected: no output (zero matches)

# Verify HashSet is gone from production enum contexts
rg "new HashSet" src/main/java -g "*.java"
# Expected: no output (zero matches)

# Verify AppClock bypasses are gone from RecommendationService
rg "AppClock" src/main/java/datingapp/core/matching/RecommendationService.java
# Expected: no output (zero matches — import removed)

# Verify shutdown() is synchronized
rg "synchronized.*shutdown\|shutdown.*synchronized" src/main/java/datingapp/storage/DatabaseManager.java
# Expected: one match

# Verify SendResult is propagated in ChatViewModel
rg "result\.success\(\)|result = .*sendMessage" src/main/java/datingapp/ui/viewmodel/ChatViewModel.java
# Expected: at least one match
```

### Step 4 — Run Individual Regression Tests for Key Areas

```powershell
# Phase 1 regressions
mvn -Ptest-output-verbose -Dtest="DealbreakersEvaluatorTest" test

# Phase 2 regressions
mvn -Ptest-output-verbose -Dtest="TrustSafetyServiceTest,RelationshipTransitionServiceTest,MatchQualityServiceTest,MatchingServiceTest,RecommendationServiceTest" test

# Phase 3 regressions
mvn -Ptest-output-verbose -Dtest="CandidateFinderTest,AppConfigTest" test
```

### Step 5 — Document Completion

After all verifications pass, update the issue tracker file:

```powershell
# Append completion stamp to VALID_ISSUES_2026-02-18.md
```

Add at the bottom of `new_Issues/VALID_ISSUES_2026-02-18.md`:
```markdown
---
## Implementation Status
**Implemented:** 2026-XX-XX by AI agent
**All 31 issues resolved:** ✅
**Final test count:** NNN
**Final coverage:** XX%
```

---

## Issue-to-Task Cross Reference

| Issue ID | Task(s)   | Phase |
|----------|-----------|-------|
| CRIT-01  | TASK-3.1  | 3     |
| CRIT-02  | TASK-2.3  | 2     |
| CRIT-03  | TASK-2.9  | 2     |
| CRIT-04  | TASK-2.1  | 2     |
| CRIT-05  | TASK-2.4  | 2     |
| CRIT-06  | TASK-2.11 | 2     |
| CRIT-07  | TASK-2.2  | 2     |
| HIGH-01  | TASK-3.2  | 3     |
| HIGH-02  | TASK-1.8  | 1     |
| HIGH-03  | TASK-1.3  | 1     |
| HIGH-04  | TASK-1.2  | 1     |
| HIGH-05  | TASK-1.1  | 1     |
| HIGH-06  | TASK-2.5  | 2     |
| HIGH-07  | TASK-3.9  | 3     |
| HIGH-08  | TASK-2.7  | 2     |
| HIGH-09  | TASK-3.3  | 3     |
| MED-01   | TASK-1.6  | 1     |
| MED-02   | TASK-1.5  | 1     |
| MED-03   | TASK-1.7  | 1     |
| MED-04   | TASK-2.8  | 2     |
| MED-05   | TASK-2.6  | 2     |
| MED-06   | TASK-3.4  | 3     |
| MED-07   | TASK-1.4  | 1     |
| MED-08   | TASK-2.10 | 2     |
| MED-09   | TASK-3.3  | 3     |
| MED-10   | TASK-3.3  | 3     |
| MED-11   | TASK-2.12 | 2     |
| LOW-01   | TASK-2.7  | 2     |
| LOW-02   | TASK-1.9  | 1     |
| LOW-03   | TASK-1.10 | 1     |
| LOW-04   | TASK-1.2  | 1     |
| ORG-01   | TASK-3.6  | 3     |
| ORG-02   | TASK-3.5  | 3     |
| ORG-03   | TASK-1.8  | 1     |
| ORG-04   | TASK-3.8  | 3     |
| ORG-05   | TASK-3.7  | 3     |

---

## Task Dependency Graph

```
TASK-1.1  (standalone)
TASK-1.2  (standalone)
TASK-1.3  ← must run before TASK-1.4 (same file, MatchPreferences)
TASK-1.4  ← depends on TASK-1.3
TASK-1.5  (standalone)
TASK-1.6  (standalone)
TASK-1.7  ← depends on TASK-1.6 (UiConstants must exist)
TASK-1.8  (standalone)
TASK-1.9  (standalone)
TASK-1.10 ← must update all callers (use rg to find them first)

TASK-2.1  (standalone)
TASK-2.2  ← must update callers after signature change
TASK-2.3  (standalone)
TASK-2.4  ← callers must be updated; tests must be updated
TASK-2.5  (standalone)
TASK-2.6  (standalone)
TASK-2.7  ← add generic isAcceptable + 2 missing isMatch overloads (do together)
TASK-2.8  ← depends on ApplicationStartup having config accessible
TASK-2.9  ← depends on TASK-2.8 (ValidationService in ServiceRegistry)
TASK-2.10 (standalone)
TASK-2.11 (standalone)
TASK-2.12 (standalone)

TASK-3.1  ← requires schema check for lat/lon columns; updates interface + impl + tests
TASK-3.2  ← depends on TASK-2.4 (TransitionResult return types)
TASK-3.3  ← adds countMatchesForUser to InteractionStorage — update TestStorages too
TASK-3.4  (standalone — create new file, update MatchQualityService imports)
TASK-3.5  ← read each inline stub before deleting; do NOT break test functionality
TASK-3.6  ← high risk of breaking JavaFX; compile+run UI to verify
TASK-3.7  (standalone)
TASK-3.8  (standalone)
TASK-3.9  ← high blast radius; add compat accessors before removing params
```

---

*Plan generated: 2026-02-18 | Source: `VALID_ISSUES_2026-02-18.md` | 31 issues | 28 tasks across 3 phases*

