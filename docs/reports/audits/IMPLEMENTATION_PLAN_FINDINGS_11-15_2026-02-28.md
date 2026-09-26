# Implementation Plan: Findings 11-15 — Agent-Ready Edition

Date: 2026-02-28 (enhanced)
Source: `docs/audits/RETROSPECTIVE_ARCHITECTURE_DECISIONS_2026-02-27.md` (Decisions 11-15)
Status: Ready for implementation
Owner: AI coding agents (any type)

## 0. How to Use This Plan

This plan contains **14 independent work units (WUs)**. Each WU is self-contained and can be assigned to a separate AI agent.

**Rules for agents:**
1. Read YOUR work unit section completely before writing any code.
2. Check the **Dependencies** field — do not start until listed WUs are merged.
3. Follow the **File Manifest** exactly — do not create files not listed.
4. Run the **Verification Gate** commands after completing work.
5. Check ALL items in **Acceptance Criteria** before declaring done.
6. Consult **Edge Cases & Gotchas** to avoid known pitfalls.
7. Do NOT modify files outside your manifest unless fixing a compilation error caused by your changes.

**Build commands (all agents):**
```bash
mvn spotless:apply          # Format code (ALWAYS run first)
mvn test                    # Run all tests
mvn verify                  # Full build + quality gates
mvn -Ptest-output-verbose test  # Verbose test output for debugging
```

**Package conventions:**
- New core contracts: `datingapp.core.*` (no framework imports)
- New app-layer classes: `datingapp.app.*`
- New event classes: `datingapp.app.event.*`
- Tests mirror source: `src/test/java/datingapp/<same-package>/`
- Use `AppClock.now()` not `Instant.now()`
- Use `LoggingSupport` interface for logging with PMD-safe guards

## 1. Goal

Implement and close Findings 11-15 by introducing:
1. Central workflow/state policy (Finding 11).
2. Normalized persistence for multi-value profile fields (Finding 12).
3. In-process event pipeline for cross-cutting side effects (Finding 13).
4. Canonical typed failure semantics across layers (Finding 14).
5. Unified time/timezone policy with no feature-level default leakage (Finding 15).

## 2. Work Unit Dependency Graph

```
Independent (no deps):        WU-01  WU-02  WU-03  WU-04  WU-11
                                 \      |      |      /       |
                                  \     |      |     /        |
Wiring:                           WU-05 ◄──────┘    WU-12
                                  / | \ \
                                 /  |  \ \
Policy:                    WU-06  WU-07  |  \
                              \    /     |   \
Integration:               WU-08  WU-09  |    \
                                   |    WU-13  WU-14
                                WU-10
```

**Parallel groups (can run simultaneously):**
- Group A (no deps): WU-01, WU-02, WU-03, WU-04, WU-11
- Group B (after WU-02,03,04): WU-05
- Group C (after WU-02): WU-06, WU-07
- Group D (after WU-05): WU-09, WU-13, WU-14
- Group E (after WU-06,07,05): WU-08
- Group F (after WU-09): WU-10
- Group G (after WU-11): WU-12

## 3. Work Unit Index

| WU | Title | Finding(s) | Dependencies | New Files | Modified Files |
|----|-------|-----------|-------------|-----------|---------------|
| 01 | Architecture guardrail tests | 11, 15 | none | 2 | 0 |
| 02 | AppError sealed hierarchy + AppResult | 14 | none | 2 | 0 |
| 03 | TimePolicy interface + DefaultTimePolicy | 15 | none | 3 | 0 |
| 04 | Event bus interfaces + InProcessAppEventBus | 13 | none | 3 | 0 |
| 05 | Wire foundations into ServiceRegistry | 13, 14, 15 | 02, 03, 04 | 0 | 4 |
| 06 | RelationshipWorkflowPolicy | 11 | 02 | 2 | 0 |
| 07 | ProfileActivationPolicy | 11 | 02 | 2 | 0 |
| 08 | Integrate workflow policies into services/usecases/adapters | 11 | 05, 06, 07 | 0 | 7 |
| 09 | Define domain events + implement event handlers | 13 | 04, 05 | ~8 | 0 |
| 10 | Convert imperative side-effects to event emission | 13 | 09 | 0 | 6 |
| 11 | Schema V3 migration + normalized table DAOs | 12 | none | 1-2 | 3 |
| 12 | Dual-write/read migration + legacy removal | 12 | 11 | 0 | 3 |
| 13 | End-to-end typed failure conversion + error mappers | 14 | 02, 05 | 3 | ~8 |
| 14 | Time policy rollout + deprecated path removal | 15 | 03, 05 | 0 | ~10 |

## 4. Global Conventions

### 4.1 ServiceRegistry Constructor (current — 16 parameters)
```java
// File: src/main/java/datingapp/core/ServiceRegistry.java
public ServiceRegistry(
    AppConfig config,                              // 1
    UserStorage userStorage,                       // 2
    InteractionStorage interactionStorage,         // 3
    CommunicationStorage communicationStorage,     // 4
    AnalyticsStorage analyticsStorage,             // 5
    TrustSafetyStorage trustSafetyStorage,         // 6
    CandidateFinder candidateFinder,               // 7
    MatchingService matchingService,               // 8
    TrustSafetyService trustSafetyService,         // 9
    ActivityMetricsService activityMetricsService,  // 10
    MatchQualityService matchQualityService,        // 11
    ProfileService profileService,                  // 12
    RecommendationService recommendationService,    // 13
    UndoService undoService,                        // 14
    ConnectionService connectionService,            // 15
    ValidationService validationService)            // 16
```
WU-05 will add new parameters. Other WUs must be aware of the final signature.

### 4.2 Test Infrastructure (reuse these)
```java
// In-memory storages: src/test/java/datingapp/core/testutil/TestStorages.java
TestStorages.Users        // implements UserStorage
TestStorages.Interactions // implements InteractionStorage
TestStorages.Communications // implements CommunicationStorage
TestStorages.Analytics    // implements AnalyticsStorage
TestStorages.TrustSafety  // implements TrustSafetyStorage
TestStorages.Undos        // implements Undo.Storage

// Test user factory:
TestUserFactory.createActiveUser(UUID id, String name)
TestUserFactory.createActiveUser(String name)

// Fixed clock for time tests:
TestClock.setFixed(Instant fixedInstant)
TestClock.reset()

// Default config:
AppConfig.defaults()
```

### 4.3 Existing Error Model (being extended, not replaced immediately)
```java
// Current: src/main/java/datingapp/app/usecase/common/UseCaseResult.java
public record UseCaseResult<T>(boolean success, T data, UseCaseError error)

// Current: src/main/java/datingapp/app/usecase/common/UseCaseError.java
public record UseCaseError(Code code, String message) {
    public enum Code { VALIDATION, NOT_FOUND, CONFLICT, FORBIDDEN, DEPENDENCY, INTERNAL }
}
```

---

## WU-01: Architecture Guardrail Tests

**Finding:** 11, 15
**Dependencies:** none
**Parallel group:** A (can run with WU-02, WU-03, WU-04, WU-11)

### Current State

No architecture tests exist. The following violations are present in the codebase and should be DETECTED (not fixed) by these tests:
- `ZoneId.systemDefault()` in feature code (StatsHandler:28, StatsHandler:30, MessagingHandler:38, ChatController:244, SocialController:131, User:437, MatchPreferences:630,665)
- `AppConfig.defaults()` in feature code (DashboardViewModel:234, MatchesViewModel:311,345, MatchingHandler:267,351,424,676,821,1035)

### Target State

Two test classes that detect policy violations using string-based source scanning (no ArchUnit dependency needed).

**File:** `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java`
```java
package datingapp.architecture;

// Scans Java source files for forbidden patterns.
// Uses java.nio.file to walk src/main/java.

class TimePolicyArchitectureTest {

    // Allowed files that MAY use ZoneId.systemDefault():
    static final Set<String> ZONE_DEFAULT_ALLOWLIST = Set.of(
        "AppConfig.java",      // Builder default (composition root)
        "AppClock.java"        // Clock infrastructure
    );

    // Allowed files that MAY use AppConfig.defaults():
    static final Set<String> CONFIG_DEFAULTS_ALLOWLIST = Set.of(
        "ApplicationStartup.java",  // Bootstrap
        "StorageFactory.java"       // Factory (composition root)
    );

    @Test void noFeatureCodeUsesZoneIdSystemDefault()
    // Walk src/main/java/**/*.java
    // For each file NOT in ZONE_DEFAULT_ALLOWLIST:
    //   Assert file does NOT contain "ZoneId.systemDefault()"
    //   Collect violations, fail with list of file:line violations

    @Test void noFeatureCodeUsesAppConfigDefaults()
    // Walk src/main/java/**/*.java
    // For each file NOT in CONFIG_DEFAULTS_ALLOWLIST:
    //   Assert file does NOT contain "AppConfig.defaults()"
    //   Collect violations, fail with list of file:line violations
}
```

**File:** `src/test/java/datingapp/architecture/AdapterBoundaryArchitectureTest.java`
```java
package datingapp.architecture;

class AdapterBoundaryArchitectureTest {

    // UI viewmodels must NOT import core.storage.* directly
    // (they should use UiDataAdapters interfaces)

    @Test void viewModelsDoNotImportCoreStorage()
    // Walk src/main/java/datingapp/ui/viewmodel/**/*.java
    // Assert no file contains "import datingapp.core.storage."
    // Allowlist: UiDataAdapters.java (defines the bridge)

    @Test void corePackageDoesNotImportFrameworks()
    // Walk src/main/java/datingapp/core/**/*.java
    // Assert no file contains:
    //   "import javafx.", "import org.jdbi.", "import io.javalin.",
    //   "import com.fasterxml.jackson."
}
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| CREATE | `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java` | New test class |
| CREATE | `src/test/java/datingapp/architecture/AdapterBoundaryArchitectureTest.java` | New test class |

### Wiring Instructions

None — these are standalone test classes with no production dependencies.

### Test Specification

- `TimePolicyArchitectureTest.noFeatureCodeUsesZoneIdSystemDefault()` — **EXPECTED TO FAIL** initially (violations exist). This is intentional: the test documents violations that WU-14 will fix. Mark with `@Disabled("Enable after WU-14 completes timezone rollout")`.
- `TimePolicyArchitectureTest.noFeatureCodeUsesAppConfigDefaults()` — **EXPECTED TO FAIL** initially. Mark with `@Disabled("Enable after WU-14 completes config rollout")`.
- `AdapterBoundaryArchitectureTest.viewModelsDoNotImportCoreStorage()` — Should PASS now (ViewModels already use UiDataAdapters).
- `AdapterBoundaryArchitectureTest.corePackageDoesNotImportFrameworks()` — Should PASS now.

### Edge Cases & Gotchas

1. **String matching false positives**: A comment mentioning `ZoneId.systemDefault()` should not trigger a violation. Filter to lines that are NOT comments (not starting with `//` or inside `/* */`). However, a simple approach is acceptable — the allowlist handles legitimate uses.
2. **Test source files**: Only scan `src/main/java`, NOT `src/test/java`. Test files legitimately use these patterns.
3. **Package path separator**: On Windows, `Path.toString()` uses backslashes. Use `path.toString().replace('\\', '/')` for pattern matching or use `Path.getFileName()`.

### Acceptance Criteria

- [ ] Both test files compile with `mvn compile -pl . -am`
- [ ] `AdapterBoundaryArchitectureTest` tests pass (2 tests green)
- [ ] `TimePolicyArchitectureTest` tests are `@Disabled` with clear message
- [ ] `mvn spotless:apply` produces no changes (code is formatted)
- [ ] No production code was modified

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.architecture.*"
# Expected: 2 tests pass, 2 tests skipped (@Disabled)
```

### Rollback Notes

Delete both test files. No production impact.

---

## WU-02: AppError Sealed Hierarchy + AppResult

**Finding:** 14
**Dependencies:** none
**Parallel group:** A

### Current State

The existing error model lives in `app/usecase/common/`:
- `UseCaseError` record with `Code` enum (6 values: VALIDATION, NOT_FOUND, CONFLICT, FORBIDDEN, DEPENDENCY, INTERNAL)
- `UseCaseResult<T>` record with boolean success + data + error fields

No sealed hierarchy exists. Core services use ad-hoc result records (TransitionResult, SendResult, SwipeResult) with string error messages.

### Target State

New `app/error/` package with a sealed interface hierarchy that EXTENDS (not replaces) the existing model. Existing `UseCaseError` codes map 1:1 to the new hierarchy.

**File:** `src/main/java/datingapp/app/error/AppError.java`
```java
package datingapp.app.error;

// Sealed hierarchy for typed application errors.
// Each variant carries a human-readable message and optional cause.

public sealed interface AppError {
    String message();

    record Validation(String message, String field) implements AppError {}
    // field: optional, identifies which input field failed

    record NotFound(String message, String resourceType, String resourceId) implements AppError {}
    // resourceType: "User", "Match", "Like", etc.
    // resourceId: the ID that was not found (nullable for queries)

    record Conflict(String message) implements AppError {}
    // Business rule violations: duplicate, state mismatch, daily limits

    record Forbidden(String message) implements AppError {}
    // Authorization: user cannot perform this action

    record Dependency(String message, String serviceName) implements AppError {}
    // serviceName: which optional service is missing

    record Infrastructure(String message, Throwable cause) implements AppError {}
    // DB errors, I/O failures — wraps infrastructure exceptions
    // cause: the original exception (nullable)

    record Internal(String message) implements AppError {}
    // Unexpected/unknown errors

    // Bridge FROM existing UseCaseError:
    static AppError fromUseCaseError(UseCaseError error) {
        return switch (error.code()) {
            case VALIDATION -> new Validation(error.message(), null);
            case NOT_FOUND -> new NotFound(error.message(), null, null);
            case CONFLICT -> new Conflict(error.message());
            case FORBIDDEN -> new Forbidden(error.message());
            case DEPENDENCY -> new Dependency(error.message(), null);
            case INTERNAL -> new Internal(error.message());
        };
    }

    // Bridge TO existing UseCaseError (for backward compat):
    default UseCaseError toUseCaseError() {
        return switch (this) {
            case Validation v -> UseCaseError.validation(v.message());
            case NotFound n -> UseCaseError.notFound(n.message());
            case Conflict c -> UseCaseError.conflict(c.message());
            case Forbidden f -> UseCaseError.forbidden(f.message());
            case Dependency d -> UseCaseError.dependency(d.message());
            case Infrastructure i -> UseCaseError.internal(i.message());
            case Internal i -> UseCaseError.internal(i.message());
        };
    }
}
```

**File:** `src/main/java/datingapp/app/error/AppResult.java`
```java
package datingapp.app.error;

// Generic result type. Interoperable with existing UseCaseResult.

public record AppResult<T>(T data, AppError error) {

    // Compact constructor: mutual exclusivity
    public AppResult {
        if (data != null && error != null)
            throw new IllegalArgumentException("Cannot have both data and error");
        // Note: both null is allowed (success with void/no-data)
    }

    public boolean success() { return error == null; }

    public static <T> AppResult<T> ok(T data) { return new AppResult<>(data, null); }
    public static <T> AppResult<T> ok() { return new AppResult<>(null, null); }
    public static <T> AppResult<T> fail(AppError error) {
        Objects.requireNonNull(error);
        return new AppResult<>(null, error);
    }

    // Bridge FROM UseCaseResult:
    public static <T> AppResult<T> fromUseCaseResult(UseCaseResult<T> r) {
        if (r.success()) return ok(r.data());
        return fail(AppError.fromUseCaseError(r.error()));
    }

    // Bridge TO UseCaseResult:
    public UseCaseResult<T> toUseCaseResult() {
        if (success()) return UseCaseResult.success(data);
        return UseCaseResult.failure(error.toUseCaseError());
    }
}
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| CREATE | `src/main/java/datingapp/app/error/AppError.java` | Sealed interface hierarchy |
| CREATE | `src/main/java/datingapp/app/error/AppResult.java` | Generic result record |

### Wiring Instructions

None yet — these are standalone types. WU-05 adds them to ServiceRegistry awareness. WU-13 converts services to use them.

### Test Specification

**File:** `src/test/java/datingapp/app/error/AppErrorTest.java`
```java
@Test void validationCarriesFieldName()
@Test void notFoundCarriesResourceInfo()
@Test void infrastructureCarriesCause()
@Test void fromUseCaseErrorRoundTrips()
// For each UseCaseError.Code: create UseCaseError, convert to AppError,
// convert back, assert code and message preserved

@Test void appResultMutualExclusivity()
// new AppResult<>(data, error) throws
// ok(data) has no error
// fail(error) has no data

@Test void appResultBridgeFromUseCaseResult()
// UseCaseResult.success(x) -> AppResult.fromUseCaseResult() -> success with x
// UseCaseResult.failure(err) -> AppResult.fromUseCaseResult() -> fail with mapped error

@Test void appResultBridgeToUseCaseResult()
// AppResult.ok(x) -> toUseCaseResult() -> success with x
// AppResult.fail(Conflict("msg")) -> toUseCaseResult() -> failure with CONFLICT code
```

### Edge Cases & Gotchas

1. **Sealed interface requires same package or explicit permits**: All record implementations are nested inside `AppError`, so they're automatically permitted. Do NOT put implementations in separate files.
2. **Infrastructure cause may be null**: The `Throwable cause` parameter should be nullable for cases where no underlying exception exists.
3. **Import conflict**: `AppError` is a new name. Ensure no existing class in the project uses this name (verified: none does).
4. **Preview features**: The project uses `--enable-preview` (Java 25). Sealed interfaces and pattern matching in switch are stable in Java 25, so no preview flag issues.
5. **AppResult allows both-null**: This represents a "success with no data" (void operations). This is intentional for operations like `unmatch()` that succeed but return no payload.

### Acceptance Criteria

- [ ] `AppError.java` compiles as a sealed interface with 7 record variants
- [ ] `AppResult.java` compiles with mutual exclusivity enforcement
- [ ] Bridge methods `fromUseCaseError`/`toUseCaseError` compile and handle all 6 existing codes
- [ ] Bridge methods `fromUseCaseResult`/`toUseCaseResult` compile
- [ ] All `AppErrorTest` tests pass
- [ ] `mvn spotless:apply` produces no changes
- [ ] No existing code modified

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.app.error.*"
# Expected: all tests green
```

### Rollback Notes

Delete `app/error/` package (2 files + test). No production impact.

---

## WU-03: TimePolicy Interface + DefaultTimePolicy

**Finding:** 15
**Dependencies:** none
**Parallel group:** A

### Current State

Time is managed by:
- `AppClock` (core/) — static `Clock` holder, provides `now()`, `today()`, `today(ZoneId)`, `clock()`
- `AppConfig.safety().userTimeZone()` — configured timezone, defaults to `ZoneId.systemDefault()` in builder
- Feature code directly calls `ZoneId.systemDefault()` in 8+ locations (see WU-01 inventory)
- Feature code directly calls `AppConfig.defaults().safety().userTimeZone()` in 6+ locations

### Target State

A `TimePolicy` contract in core that encapsulates "what time is it" and "what timezone for this user" without exposing `ZoneId.systemDefault()` or `AppConfig.defaults()`.

**File:** `src/main/java/datingapp/core/time/TimePolicy.java`
```java
package datingapp.core.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// Single source of truth for time operations.
// Feature code must use this instead of ZoneId.systemDefault() or AppConfig.defaults().

public interface TimePolicy {

    Instant now();
    // Returns current instant (delegates to AppClock)

    LocalDate today();
    // Returns today in the user's configured timezone

    ZoneId userZone();
    // Returns the configured user timezone
    // Feature code calls this instead of ZoneId.systemDefault()

    DateTimeFormatter withUserZone(DateTimeFormatter formatter);
    // Returns formatter.withZone(userZone())
    // Convenience for adapter formatting code
}
```

**File:** `src/main/java/datingapp/core/time/DefaultTimePolicy.java`
```java
package datingapp.core.time;

import datingapp.core.AppClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class DefaultTimePolicy implements TimePolicy {

    private final ZoneId userZone;

    public DefaultTimePolicy(ZoneId userZone) {
        this.userZone = Objects.requireNonNull(userZone);
    }

    @Override public Instant now() {
        return AppClock.now();
    }

    @Override public LocalDate today() {
        return AppClock.today(userZone);
    }

    @Override public ZoneId userZone() {
        return userZone;
    }

    @Override public DateTimeFormatter withUserZone(DateTimeFormatter formatter) {
        return formatter.withZone(userZone);
    }
}
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| CREATE | `src/main/java/datingapp/core/time/TimePolicy.java` | Interface |
| CREATE | `src/main/java/datingapp/core/time/DefaultTimePolicy.java` | Default impl |

### Wiring Instructions

None yet — WU-05 wires into ServiceRegistry. WU-14 rolls out to all feature code.

### Test Specification

**File:** `src/test/java/datingapp/core/time/TimePolicyTest.java`
```java
@BeforeEach void setUp()
// TestClock.setFixed(Instant.parse("2026-06-15T10:30:00Z"))
// policy = new DefaultTimePolicy(ZoneId.of("Asia/Jerusalem"))

@AfterEach void tearDown()
// TestClock.reset()

@Test void nowDelegatesToAppClock()
// policy.now() == TestClock fixed instant

@Test void todayUsesUserZone()
// At 2026-06-15T10:30:00Z with Asia/Jerusalem (+3):
// policy.today() == LocalDate.of(2026, 6, 15)  (13:30 local = same day)
// Change to 2026-06-14T22:00:00Z → policy.today() should be 2026-06-15 (01:00 local)

@Test void userZoneReturnsConfiguredZone()
// policy.userZone() == ZoneId.of("Asia/Jerusalem")

@Test void withUserZoneAppliesZone()
// formatter = DateTimeFormatter.ofPattern("HH:mm")
// formatted = policy.withUserZone(formatter)
// formatted.format(Instant.parse("2026-06-15T10:30:00Z")) == "13:30"

@Test void constructorRejectsNull()
// assertThrows(NullPointerException.class, () -> new DefaultTimePolicy(null))
```

### Edge Cases & Gotchas

1. **AppClock is static**: `DefaultTimePolicy.now()` delegates to `AppClock.now()`, which uses a static volatile `Clock`. This is correct — `TestClock.setFixed()` will affect all calls through `TimePolicy.now()`.
2. **DST transitions**: `today()` must use `userZone` not UTC. At midnight UTC on a DST boundary, `LocalDate.now(utcClock)` and `LocalDate.now(clock.withZone(userZone))` can differ by a day. The test should cover this.
3. **No `AppConfig` import in core/time/**: `TimePolicy` and `DefaultTimePolicy` are in `core/` and must NOT import `AppConfig`. The zone value is injected via constructor. `AppConfig` is read only at composition root (StorageFactory/ApplicationStartup).
4. **Thread safety**: `DefaultTimePolicy` is immutable (final field). Safe for concurrent access.

### Acceptance Criteria

- [ ] `TimePolicy.java` is an interface in `core.time` package with 4 methods
- [ ] `DefaultTimePolicy.java` is a final class implementing `TimePolicy`
- [ ] Constructor takes `ZoneId`, delegates time to `AppClock`
- [ ] No `AppConfig` import in either file
- [ ] No `ZoneId.systemDefault()` in either file
- [ ] All `TimePolicyTest` tests pass including DST edge case
- [ ] `mvn spotless:apply` produces no changes

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.core.time.*"
# Expected: all tests green
```

### Rollback Notes

Delete `core/time/` package (2 files + test). No production impact.

---

## WU-04: Event Bus Interfaces + InProcessAppEventBus

**Finding:** 13
**Dependencies:** none
**Parallel group:** A

### Current State

No event infrastructure exists. Side effects (achievements, metrics, notifications) are triggered imperatively at 13+ locations across CLI handlers, ViewModels, and core services (see Finding 13 inventory in Section 6).

### Target State

Synchronous in-process event bus in the app layer. Events are fired after primary action succeeds. Handlers execute in the same thread (no async complexity initially).

**File:** `src/main/java/datingapp/app/event/AppEvent.java`
```java
package datingapp.app.event;

import java.time.Instant;
import java.util.UUID;

// Marker interface for all application events.
// Events are immutable records carrying the minimum context needed.

public sealed interface AppEvent permits
    AppEvent.SwipeRecorded,
    AppEvent.MatchCreated,
    AppEvent.ProfileSaved,
    AppEvent.FriendRequestAccepted,
    AppEvent.RelationshipTransitioned,
    AppEvent.MessageSent {

    Instant occurredAt();

    record SwipeRecorded(UUID swiperId, UUID targetId,
                         String direction, boolean resulted_in_match,
                         Instant occurredAt) implements AppEvent {}
    // direction: "LIKE" or "PASS"

    record MatchCreated(UUID matchId, UUID userA, UUID userB,
                        Instant occurredAt) implements AppEvent {}

    record ProfileSaved(UUID userId, boolean activated,
                        Instant occurredAt) implements AppEvent {}
    // activated: true if this save caused profile activation

    record FriendRequestAccepted(UUID requestId, UUID fromUserId, UUID toUserId,
                                  UUID matchId, Instant occurredAt) implements AppEvent {}

    record RelationshipTransitioned(UUID matchId, UUID initiatorId, UUID targetId,
                                     String fromState, String toState,
                                     Instant occurredAt) implements AppEvent {}
    // fromState/toState: MatchState enum names as strings

    record MessageSent(UUID senderId, UUID recipientId, UUID messageId,
                       Instant occurredAt) implements AppEvent {}
}
```

**File:** `src/main/java/datingapp/app/event/AppEventBus.java`
```java
package datingapp.app.event;

// Contract for publishing and subscribing to application events.

public interface AppEventBus {

    void publish(AppEvent event);
    // Dispatches event to all registered handlers for that event type.
    // Handlers execute synchronously in publication order.
    // Handler exceptions are logged but do NOT propagate to publisher
    // (unless handler is REQUIRED — see HandlerPolicy).

    <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler);
    // Registers a handler for a specific event type.

    <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler,
                                         HandlerPolicy policy);
    // Registers with explicit policy.

    enum HandlerPolicy {
        REQUIRED,     // Exception propagates to publisher (transaction fails)
        BEST_EFFORT   // Exception logged, publisher continues
    }

    @FunctionalInterface
    interface AppEventHandler<T extends AppEvent> {
        void handle(T event);
    }
}
```

**File:** `src/main/java/datingapp/app/event/InProcessAppEventBus.java`
```java
package datingapp.app.event;

import datingapp.core.LoggingSupport;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InProcessAppEventBus implements AppEventBus, LoggingSupport {

    // Map<eventType, List<HandlerEntry>>
    private final Map<Class<? extends AppEvent>, List<HandlerEntry<?>>> handlers =
        new ConcurrentHashMap<>();

    private record HandlerEntry<T extends AppEvent>(
        AppEventHandler<T> handler, HandlerPolicy policy) {}

    @Override
    public void publish(AppEvent event) {
        // 1. Get handler list for event.getClass()
        // 2. For each handler:
        //    a. If BEST_EFFORT: try/catch, log warning on failure
        //    b. If REQUIRED: let exception propagate (wrapped in RuntimeException if checked)
        // 3. Log event publication at DEBUG level
    }

    @Override
    public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler) {
        subscribe(eventType, handler, HandlerPolicy.BEST_EFFORT);  // default
    }

    @Override
    public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler,
                                                HandlerPolicy policy) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(new HandlerEntry<>(handler, policy));
    }
}
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| CREATE | `src/main/java/datingapp/app/event/AppEvent.java` | Sealed event interface + 6 records |
| CREATE | `src/main/java/datingapp/app/event/AppEventBus.java` | Bus interface + handler policy |
| CREATE | `src/main/java/datingapp/app/event/InProcessAppEventBus.java` | Synchronous implementation |

### Wiring Instructions

None yet — WU-05 wires into ServiceRegistry/StorageFactory.

### Test Specification

**File:** `src/test/java/datingapp/app/event/InProcessAppEventBusTest.java`
```java
@Test void publishDispatchesToSubscribedHandler()
// Subscribe handler for SwipeRecorded, publish SwipeRecorded, assert handler called

@Test void publishDoesNotDispatchToUnrelatedHandler()
// Subscribe for SwipeRecorded, publish ProfileSaved, assert handler NOT called

@Test void bestEffortHandlerExceptionDoesNotPropagate()
// Subscribe BEST_EFFORT handler that throws, publish event
// Assert no exception reaches publisher

@Test void requiredHandlerExceptionPropagates()
// Subscribe REQUIRED handler that throws, publish event
// Assert exception reaches publisher (RuntimeException wrapping)

@Test void multipleHandlersCalledInOrder()
// Subscribe 3 handlers for same event type
// Publish event, assert all called in registration order

@Test void concurrentSubscribeAndPublish()
// Subscribe from multiple threads while publishing
// Assert no ConcurrentModificationException
```

### Edge Cases & Gotchas

1. **Type erasure with generics**: The `subscribe` method uses `Class<T>` as key. When looking up handlers in `publish`, use `event.getClass()` which returns the concrete record class, NOT `AppEvent.class`. This should match the subscription key.
2. **CopyOnWriteArrayList for thread safety**: Handler list must be thread-safe since subscriptions may happen during startup while events are published from multiple threads. `CopyOnWriteArrayList` is appropriate for many-reads/few-writes.
3. **Handler ordering**: Handlers fire in subscription (registration) order. This is important for REQUIRED handlers — if handler A is REQUIRED and fails, handlers B and C should NOT execute.
4. **No `core/` imports of event classes**: Events live in `app/event/`, NOT in `core/`. Core services do NOT publish events directly — use-cases do (after calling core services).
5. **Event immutability**: All events are records (immutable). Do not add mutable fields.
6. **`LoggingSupport` usage**: Implement `LoggingSupport` for PMD-safe logging. Use `logWarn(...)` for handler exceptions.

### Acceptance Criteria

- [ ] `AppEvent.java` compiles as sealed interface with 6 record variants
- [ ] `AppEventBus.java` compiles as interface with `publish`, `subscribe`, `HandlerPolicy`
- [ ] `InProcessAppEventBus.java` compiles with thread-safe handler dispatch
- [ ] BEST_EFFORT handlers swallow exceptions with logging
- [ ] REQUIRED handlers propagate exceptions to publisher
- [ ] All `InProcessAppEventBusTest` tests pass
- [ ] `mvn spotless:apply` produces no changes

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.app.event.*"
# Expected: all tests green
```

### Rollback Notes

Delete `app/event/` package (3 files + test). No production impact.

---

## WU-05: Wire Foundations into ServiceRegistry

**Finding:** 13, 14, 15
**Dependencies:** WU-02 (AppError), WU-03 (TimePolicy), WU-04 (EventBus)
**Parallel group:** B

### Current State

`ServiceRegistry` has 16 constructor parameters (see Section 4.1). It does not hold references to `TimePolicy`, `AppEventBus`, or know about `AppError`.

`StorageFactory.buildH2()` constructs all services and returns a `ServiceRegistry`. It does not create `TimePolicy` or `AppEventBus`.

`ApplicationStartup.initialize()` calls `StorageFactory.buildH2(dbManager, config)`.

### Target State

Add `TimePolicy` and `AppEventBus` to `ServiceRegistry` as new fields with getters. Create them in `StorageFactory.buildH2()`. No behavioral changes — just wiring.

**Changes to `ServiceRegistry.java`:**
```java
// ADD two new constructor parameters (positions 17 and 18):
//   TimePolicy timePolicy,
//   AppEventBus eventBus

// ADD two new private final fields:
//   private final TimePolicy timePolicy;
//   private final AppEventBus eventBus;

// ADD two new getters:
//   public TimePolicy getTimePolicy() { return timePolicy; }
//   public AppEventBus getEventBus() { return eventBus; }
```

**Changes to `StorageFactory.buildH2()`:**
```java
// After building config, before building services:
// 1. Create TimePolicy:
TimePolicy timePolicy = new DefaultTimePolicy(config.safety().userTimeZone());

// 2. Create EventBus:
AppEventBus eventBus = new InProcessAppEventBus();

// 3. Pass both to ServiceRegistry constructor (positions 17, 18)
```

**Changes to `ApplicationStartup`:**
```java
// No changes needed — it calls StorageFactory.buildH2() which handles everything
```

**Changes to `ViewModelFactory`:**
```java
// No changes yet — ViewModels will access TimePolicy via services.getTimePolicy()
// when WU-14 rolls out timezone changes
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| MODIFY | `src/main/java/datingapp/core/ServiceRegistry.java` | Add 2 fields, 2 constructor params, 2 getters |
| MODIFY | `src/main/java/datingapp/storage/StorageFactory.java` | Create TimePolicy + EventBus, pass to registry |
| MODIFY | `src/main/java/datingapp/core/time/TimePolicy.java` | No change (already created in WU-03) |
| MODIFY | `src/main/java/datingapp/app/event/AppEventBus.java` | No change (already created in WU-04) |

### Wiring Instructions

1. Add imports to `ServiceRegistry.java`:
   - `import datingapp.core.time.TimePolicy;`
   - `import datingapp.app.event.AppEventBus;`

2. Add imports to `StorageFactory.java`:
   - `import datingapp.core.time.DefaultTimePolicy;`
   - `import datingapp.core.time.TimePolicy;`
   - `import datingapp.app.event.AppEventBus;`
   - `import datingapp.app.event.InProcessAppEventBus;`

3. Update ALL existing `new ServiceRegistry(...)` call sites to pass the two new parameters. Search for `new ServiceRegistry(` — it should only appear in `StorageFactory.buildH2()`.

4. Update test helpers that construct `ServiceRegistry` directly (if any). Search test code for `new ServiceRegistry(`.

### Test Specification

**Update existing test:** `src/test/java/datingapp/core/ServiceRegistryTest.java`
```java
// Add assertions:
@Test void registryExposesTimePolicy()
// registry.getTimePolicy() is not null

@Test void registryExposesEventBus()
// registry.getEventBus() is not null
```

### Edge Cases & Gotchas

1. **Constructor parameter ordering matters**: Add `TimePolicy` and `AppEventBus` as the LAST two parameters to minimize diff. The existing 16 parameters stay in their current positions.
2. **Test ServiceRegistry construction**: If `ServiceRegistryTest` uses `StorageFactory.buildH2()`, it will automatically get the new parameters. If it constructs `ServiceRegistry` directly, you must add the two new params.
3. **Null check**: Add `Objects.requireNonNull(timePolicy)` and `Objects.requireNonNull(eventBus)` in the constructor body, matching the existing pattern for other parameters.
4. **ViewModelFactory is NOT changed yet**: Do not propagate TimePolicy to ViewModels in this WU. That happens in WU-14.

### Acceptance Criteria

- [ ] `ServiceRegistry` constructor accepts 18 parameters (was 16)
- [ ] `getTimePolicy()` and `getEventBus()` return non-null
- [ ] `StorageFactory.buildH2()` creates `DefaultTimePolicy` from config zone
- [ ] `StorageFactory.buildH2()` creates `InProcessAppEventBus`
- [ ] ALL existing tests still pass (no regressions)
- [ ] `mvn spotless:apply && mvn verify` succeeds

### Verification Gate

```bash
mvn spotless:apply
mvn test
# Expected: ALL tests pass (no regressions)
mvn verify
# Expected: full build succeeds including quality gates
```

### Rollback Notes

Revert ServiceRegistry and StorageFactory to 16-param constructor. Remove TimePolicy/EventBus references. Dependent WUs (06-14) would need to be reverted first.

---

## WU-06: RelationshipWorkflowPolicy

**Finding:** 11
**Dependencies:** WU-02 (AppError — for typed deny reasons)
**Parallel group:** C (can run with WU-07)

### Current State

Match state transition rules are split across:

1. **`Match.java` (lines 206-216)** — `isInvalidTransition()` private method with switch expression:
   - ACTIVE → {FRIENDS, UNMATCHED, GRACEFUL_EXIT, BLOCKED}
   - FRIENDS → {UNMATCHED, GRACEFUL_EXIT, BLOCKED}
   - UNMATCHED, GRACEFUL_EXIT, BLOCKED → terminal (all rejected)

2. **`ConnectionService.java`** — Additional business guards before calling Match methods:
   - `requestFriendZone` (line 255): checks match exists + is ACTIVE
   - `acceptFriendZone` (line 286-292): checks request recipient + pending status
   - `gracefulExit` (line 382): checks match exists + is ACTIVE or FRIENDS
   - `unmatch` (line 445): checks match exists + is ACTIVE or FRIENDS
   - `sendMessage` (lines 71-84): checks both users ACTIVE + match canMessage()

3. **`TrustSafetyService.java`** — Block transitions:
   - `updateMatchStateForBlock` (line 209): checks match state != BLOCKED before calling `match.block()`
   - `applyAutoBanIfThreshold` (line 192): checks user state != BANNED before calling `user.ban()`

### Target State

A single policy class that owns the COMPLETE transition matrix. Services consult the policy before mutating. The `Match.isInvalidTransition()` private method's logic is extracted and centralized.

**File:** `src/main/java/datingapp/core/workflow/WorkflowDecision.java`
```java
package datingapp.core.workflow;

// Typed allow/deny result from policy evaluation.

public sealed interface WorkflowDecision {

    record Allowed() implements WorkflowDecision {}

    record Denied(String reasonCode, String message) implements WorkflowDecision {}
    // reasonCode: machine-readable (e.g., "TERMINAL_STATE", "INVALID_TRANSITION", "MATCH_NOT_FOUND")
    // message: human-readable explanation

    default boolean isAllowed() { return this instanceof Allowed; }
    default boolean isDenied() { return this instanceof Denied; }

    static WorkflowDecision allow() { return new Allowed(); }
    static WorkflowDecision deny(String reasonCode, String message) {
        return new Denied(reasonCode, message);
    }
}
```

**File:** `src/main/java/datingapp/core/workflow/RelationshipWorkflowPolicy.java`
```java
package datingapp.core.workflow;

import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchState;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import java.util.Map;
import java.util.Set;

// Central authority for relationship state transitions.
// All transition checks MUST go through this policy.

public final class RelationshipWorkflowPolicy {

    // The complete transition matrix (source of truth):
    private static final Map<MatchState, Set<MatchState>> ALLOWED_TRANSITIONS = Map.of(
        MatchState.ACTIVE, Set.of(MatchState.FRIENDS, MatchState.UNMATCHED,
                                   MatchState.GRACEFUL_EXIT, MatchState.BLOCKED),
        MatchState.FRIENDS, Set.of(MatchState.UNMATCHED, MatchState.GRACEFUL_EXIT,
                                    MatchState.BLOCKED)
        // UNMATCHED, GRACEFUL_EXIT, BLOCKED are terminal — not in map (empty set = no transitions)
    );

    public WorkflowDecision canTransition(Match match, MatchState targetState) {
        // 1. Null checks for match and targetState
        // 2. If match.getState() == targetState → deny("SAME_STATE", "Already in state X")
        // 3. Lookup ALLOWED_TRANSITIONS.getOrDefault(match.getState(), Set.of())
        // 4. If target not in allowed set → deny("INVALID_TRANSITION", "Cannot transition from X to Y")
        // 5. Return allow()
    }

    public WorkflowDecision canRequestFriendZone(Match match) {
        // match must be ACTIVE (not FRIENDS — you're already friends)
        // Pseudocode:
        // if match.getState() != ACTIVE → deny("NOT_ACTIVE", "Friend zone requires active match")
        // return allow()
    }

    public WorkflowDecision canGracefulExit(Match match) {
        // match must be ACTIVE or FRIENDS
        return canTransition(match, MatchState.GRACEFUL_EXIT);
    }

    public WorkflowDecision canUnmatch(Match match) {
        // match must be ACTIVE or FRIENDS
        return canTransition(match, MatchState.UNMATCHED);
    }

    public WorkflowDecision canBlock(Match match) {
        // Block is allowed from ANY non-BLOCKED state (defensive)
        // if match.getState() == BLOCKED → deny("ALREADY_BLOCKED", ...)
        // return allow()
    }

    public WorkflowDecision canSendMessage(Match match, User sender, User recipient) {
        // 1. sender.getState() must be ACTIVE → deny("SENDER_NOT_ACTIVE", ...)
        // 2. recipient.getState() must be ACTIVE → deny("RECIPIENT_NOT_ACTIVE", ...)
        // 3. match.canMessage() must be true (ACTIVE or FRIENDS) → deny("MATCH_NOT_MESSAGEABLE", ...)
        // 4. return allow()
    }

    // Query method for UI/display:
    public Set<MatchState> allowedTransitionsFrom(MatchState state) {
        return ALLOWED_TRANSITIONS.getOrDefault(state, Set.of());
    }

    // Check if a state is terminal:
    public boolean isTerminal(MatchState state) {
        return !ALLOWED_TRANSITIONS.containsKey(state);
    }
}
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| CREATE | `src/main/java/datingapp/core/workflow/WorkflowDecision.java` | Sealed allow/deny result |
| CREATE | `src/main/java/datingapp/core/workflow/RelationshipWorkflowPolicy.java` | Central transition matrix + guard methods |

### Wiring Instructions

None yet — WU-08 integrates the policy into services. The policy is a stateless utility class (no constructor dependencies) that can be instantiated directly: `new RelationshipWorkflowPolicy()`.

### Test Specification

**File:** `src/test/java/datingapp/core/workflow/RelationshipWorkflowPolicyTest.java`
```java
// Use Match test instances with specific states.
// Test EVERY cell in the transition matrix:

@Test void activeCanTransitionToFriends()
@Test void activeCanTransitionToUnmatched()
@Test void activeCanTransitionToGracefulExit()
@Test void activeCanTransitionToBlocked()
@Test void friendsCanTransitionToUnmatched()
@Test void friendsCanTransitionToGracefulExit()
@Test void friendsCanTransitionToBlocked()
@Test void friendsCannotTransitionToActive()  // no revert via policy
@Test void unmatchedIsTerminal()  // all transitions denied
@Test void gracefulExitIsTerminal()
@Test void blockedIsTerminal()

@Test void canRequestFriendZoneOnlyFromActive()
@Test void cannotRequestFriendZoneFromFriends()

@Test void canBlockFromAnyNonBlockedState()
@Test void cannotBlockAlreadyBlocked()

@Test void canSendMessageRequiresActiveSender()
@Test void canSendMessageRequiresActiveRecipient()
@Test void canSendMessageRequiresMessageableMatch()

@Test void allowedTransitionsFromActiveReturns4()
@Test void allowedTransitionsFromTerminalReturnsEmpty()
@Test void isTerminalForBlockedUnmatchedGracefulExit()
```

### Edge Cases & Gotchas

1. **`Match.revertToActive()` exists but is NOT in the policy**: The `revertToActive()` method (Match.java line 185) is a compensating transaction used by ConnectionService when a non-atomic friend-zone acceptance fails. It is NOT a user-facing transition. The policy should NOT expose it. ConnectionService may still call `match.revertToActive()` directly for compensation.
2. **Block bypasses normal transition rules**: In TrustSafetyService, `match.block()` is called even from states that are normally terminal. The policy's `canBlock()` method should only deny if already BLOCKED, allowing block from ANY other state.
3. **Policy does not mutate**: The policy only answers "can this happen?" — it does NOT call `match.unmatch()` etc. The service still calls the entity mutation methods after consulting the policy.
4. **No database dependency**: The policy is pure logic. No storage imports.
5. **`canSendMessage` checks users AND match**: This is a composite check that the existing `ConnectionService.sendMessage()` does across lines 71-84. Centralizing it here.

### Acceptance Criteria

- [ ] `WorkflowDecision` is a sealed interface with `Allowed` and `Denied` variants
- [ ] `RelationshipWorkflowPolicy` contains the COMPLETE transition matrix matching Match.java's `isInvalidTransition()`
- [ ] `canTransition()` covers all MatchState x MatchState combinations
- [ ] Convenience methods (`canRequestFriendZone`, `canGracefulExit`, `canUnmatch`, `canBlock`, `canSendMessage`) delegate to matrix
- [ ] `canBlock()` allows block from any non-BLOCKED state
- [ ] All ~20 test cases pass
- [ ] `mvn spotless:apply` produces no changes

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.core.workflow.*"
# Expected: ~20 tests green
```

### Rollback Notes

Delete `core/workflow/` package (2 files + test). No production impact until WU-08 integrates.

---

## WU-07: ProfileActivationPolicy

**Finding:** 11
**Dependencies:** WU-02 (AppError)
**Parallel group:** C (can run with WU-06)

### Current State

Profile activation logic is duplicated in 3 locations:

1. **`ProfileUseCases.saveProfile()` (line 60)**:
   ```java
   if (user.isComplete() && user.getState() == UserState.INCOMPLETE) {
       user.activate();
       activated = true;
   }
   ```

2. **`LoginViewModel.login()` (lines 188-200)**: Auto-completes profile fields, then:
   ```java
   if (selectedUser.isComplete() && selectedUser.getState() == UserState.INCOMPLETE) {
       selectedUser.activate();  // line 194
   }
   ```

3. **`ProfileViewModel.attemptActivation()` (lines 497-508)**:
   ```java
   if (user.isComplete() && user.getState() == UserState.INCOMPLETE) {
       user.activate();  // line 500
       userStore.save(user);  // line 501
   }
   ```

Additionally, `User.activate()` (line 708) has its own guards:
```java
if (state == UserState.BANNED) throw new IllegalStateException("Cannot activate a banned user");
if (!isComplete()) throw new IllegalStateException("Cannot activate an incomplete profile");
```

### Target State

One policy class that centralizes the "can this user be activated?" check and the activation action.

**File:** `src/main/java/datingapp/core/workflow/ProfileActivationPolicy.java`
```java
package datingapp.core.workflow;

import datingapp.core.model.User;
import datingapp.core.model.User.UserState;

// Central authority for profile activation eligibility.

public final class ProfileActivationPolicy {

    public WorkflowDecision canActivate(User user) {
        // 1. if user == null → deny("NULL_USER", "User cannot be null")
        // 2. if user.getState() == BANNED → deny("BANNED", "Banned users cannot activate")
        // 3. if user.getState() == ACTIVE → deny("ALREADY_ACTIVE", "User is already active")
        // 4. if user.getState() == PAUSED → deny("PAUSED", "Paused users reactivate differently")
        // 5. if user.getState() != INCOMPLETE → deny("WRONG_STATE", "Unexpected state: X")
        // 6. if !user.isComplete() → deny("INCOMPLETE_PROFILE", "Profile is not complete: ...")
        //    Pseudocode: build missing-fields list for better error messages
        // 7. return allow()
    }

    public ActivationResult tryActivate(User user) {
        // 1. WorkflowDecision decision = canActivate(user)
        // 2. if denied → return ActivationResult.notActivated(decision)
        // 3. user.activate()  // calls the entity method
        // 4. return ActivationResult.activated(user)
    }

    public record ActivationResult(boolean activated, User user, WorkflowDecision decision) {
        public static ActivationResult activated(User user) {
            return new ActivationResult(true, user, WorkflowDecision.allow());
        }
        public static ActivationResult notActivated(WorkflowDecision decision) {
            return new ActivationResult(false, null, decision);
        }
    }

    // Helper: list missing fields for diagnostic messages
    // Pseudocode:
    // List<String> missingFields(User user) {
    //   List<String> missing = new ArrayList<>();
    //   if (user.getName() == null || user.getName().isBlank()) missing.add("name");
    //   if (user.getBio() == null || user.getBio().isBlank()) missing.add("bio");
    //   if (user.getBirthDate() == null) missing.add("birthDate");
    //   if (user.getGender() == null) missing.add("gender");
    //   if (user.getInterestedIn() == null || user.getInterestedIn().isEmpty()) missing.add("interestedIn");
    //   if (user.getMaxDistanceKm() <= 0) missing.add("maxDistanceKm");
    //   if (user.getMinAge() <= 0) missing.add("minAge");
    //   if (user.getMaxAge() < user.getMinAge()) missing.add("maxAge");
    //   if (user.getPhotoUrls() == null || user.getPhotoUrls().isEmpty()) missing.add("photoUrls");
    //   if (!user.hasCompletePace()) missing.add("pacePreferences");
    //   return missing;
    // }
}
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| CREATE | `src/main/java/datingapp/core/workflow/ProfileActivationPolicy.java` | Activation policy with canActivate + tryActivate |

### Wiring Instructions

None yet — WU-08 integrates into ProfileUseCases, LoginViewModel, ProfileViewModel. The policy is stateless: `new ProfileActivationPolicy()`.

### Test Specification

**File:** `src/test/java/datingapp/core/workflow/ProfileActivationPolicyTest.java`
```java
// Use TestUserFactory to create users in various states.

@Test void canActivateCompleteIncompleteUser()
// Create user with all fields filled, state INCOMPLETE → allowed

@Test void cannotActivateBannedUser()
// Create user with state BANNED → denied("BANNED", ...)

@Test void cannotActivateAlreadyActiveUser()
// Create user with state ACTIVE → denied("ALREADY_ACTIVE", ...)

@Test void cannotActivatePausedUser()
// Paused user → denied("PAUSED", ...)

@Test void cannotActivateWithMissingName()
// User with blank name → denied("INCOMPLETE_PROFILE", ...)

@Test void cannotActivateWithMissingPhotos()
// User with empty photoUrls → denied("INCOMPLETE_PROFILE", ...)

@Test void cannotActivateWithIncompletePace()
// User with incomplete pace preferences → denied("INCOMPLETE_PROFILE", ...)

@Test void tryActivateSucceedsForEligibleUser()
// Complete INCOMPLETE user → ActivationResult.activated == true, user state == ACTIVE

@Test void tryActivateReturnsDecisionForIneligibleUser()
// Banned user → ActivationResult.activated == false, decision.isDenied()

@Test void missingFieldsListsAllGaps()
// User missing name + photos → missing fields contains both
```

### Edge Cases & Gotchas

1. **`User.activate()` also validates**: The entity method throws `IllegalStateException` if preconditions fail. The policy's `tryActivate()` should call `canActivate()` first to avoid exceptions. If `canActivate()` returns `Allowed`, calling `user.activate()` should not throw (they check the same conditions).
2. **`User.isComplete()` is synchronized**: The policy calls `user.isComplete()` which acquires the user's monitor. This is fine for single-threaded use-case execution.
3. **`tryActivate` does NOT save**: It mutates the entity in memory but does NOT call `userStorage.save()`. The calling use-case is responsible for persistence.
4. **Paused → Active is NOT handled here**: Paused users reactivate via a different mechanism (not profile activation). The policy correctly rejects them.
5. **Missing fields diagnostic**: The `missingFields()` helper mirrors `User.isComplete()` logic (line 741-756). If `isComplete()` is ever updated, this helper must be updated too. Consider adding a comment referencing `User.isComplete()`.

### Acceptance Criteria

- [ ] `ProfileActivationPolicy` lives in `core.workflow` package
- [ ] `canActivate()` checks state + completeness, returns typed `WorkflowDecision`
- [ ] `tryActivate()` calls `canActivate()` then `user.activate()` only if allowed
- [ ] `ActivationResult` record carries activated flag + user + decision
- [ ] All ~10 test cases pass
- [ ] `mvn spotless:apply` produces no changes

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.core.workflow.*"
# Expected: all workflow tests green (WU-06 + WU-07 combined ~30 tests)
```

### Rollback Notes

Delete `ProfileActivationPolicy.java` + test. No production impact until WU-08.

---

## WU-08: Integrate Workflow Policies into Services/UseCases/Adapters

**Finding:** 11
**Dependencies:** WU-05 (wiring), WU-06 (RelationshipWorkflowPolicy), WU-07 (ProfileActivationPolicy)
**Parallel group:** E

### Current State

Transition guards are scattered across ConnectionService, TrustSafetyService, ProfileUseCases, LoginViewModel, and ProfileViewModel (see WU-06 and WU-07 baselines for exact locations).

### Target State

Services and use-cases consult the policy objects before mutating state. Adapter-level activation code in LoginViewModel and ProfileViewModel is replaced with use-case calls that internally use the policy.

**Changes to `ConnectionService.java`:**
```java
// ADD constructor parameter: RelationshipWorkflowPolicy workflowPolicy
// (Currently receives: config, communicationStorage, interactionStorage, userStorage,
//  activityMetricsService — add workflowPolicy as new parameter)

// In requestFriendZone (line ~255):
//   REPLACE: if (match.getState() != MatchState.ACTIVE) return failure(...)
//   WITH:    WorkflowDecision d = workflowPolicy.canRequestFriendZone(match);
//            if (d.isDenied()) return TransitionResult.failure(((Denied) d).message());

// In gracefulExit (line ~382):
//   REPLACE: if (match.getState() != ACTIVE && match.getState() != FRIENDS) return failure(...)
//   WITH:    WorkflowDecision d = workflowPolicy.canGracefulExit(match);
//            if (d.isDenied()) return TransitionResult.failure(((Denied) d).message());

// In unmatch (line ~445):
//   REPLACE: state check
//   WITH:    WorkflowDecision d = workflowPolicy.canUnmatch(match);
//            if (d.isDenied()) return TransitionResult.failure(((Denied) d).message());

// In sendMessage (lines 71-84):
//   REPLACE: individual sender/recipient/match checks
//   WITH:    WorkflowDecision d = workflowPolicy.canSendMessage(match, sender, recipient);
//            if (d.isDenied()) return SendResult.failure(((Denied) d).message(), errorCode);
//   NOTE: preserve specific ErrorCode mapping (USER_NOT_FOUND vs NO_ACTIVE_MATCH)
```

**Changes to `TrustSafetyService.java`:**
```java
// ADD constructor parameter: RelationshipWorkflowPolicy workflowPolicy

// In updateMatchStateForBlock (line ~209):
//   REPLACE: if (match.getState() != MatchState.BLOCKED)
//   WITH:    WorkflowDecision d = workflowPolicy.canBlock(match);
//            if (d.isAllowed()) { match.block(blockerId); ... }
```

**Changes to `ProfileUseCases.java`:**
```java
// ADD constructor parameter: ProfileActivationPolicy activationPolicy

// In saveProfile (line ~60):
//   REPLACE: if (user.isComplete() && user.getState() == UserState.INCOMPLETE) { user.activate(); ...}
//   WITH:    var activation = activationPolicy.tryActivate(user);
//            activated = activation.activated();
```

**Changes to `LoginViewModel.java`:**
```java
// ADD constructor parameter: ProfileActivationPolicy activationPolicy
// (or receive it through ViewModelFactory)

// In login() (lines 188-200):
//   REPLACE: direct isComplete() + activate() + try/catch
//   WITH:    var activation = activationPolicy.tryActivate(selectedUser);
//            // activation handles all guards, no try/catch needed

// In createUser() (lines 335-336):
//   REPLACE: if (newUser.isComplete()) { newUser.activate(); }
//   WITH:    activationPolicy.tryActivate(newUser);
//            // No need to check result — new user activation is best-effort
```

**Changes to `ProfileViewModel.java`:**
```java
// The persistProfileViaUseCase path (line 360) already delegates to ProfileUseCases
// which now uses the policy internally. No direct changes needed.

// In attemptActivation() (lines 497-508):
//   REPLACE: direct isComplete() + activate() + save()
//   WITH:    var activation = activationPolicy.tryActivate(user);
//            if (activation.activated()) { userStore.save(user); showSuccess(...); }
```

**Changes to `StorageFactory.java`:**
```java
// Create policy instances and pass to services:
RelationshipWorkflowPolicy workflowPolicy = new RelationshipWorkflowPolicy();
ProfileActivationPolicy activationPolicy = new ProfileActivationPolicy();

// Pass workflowPolicy to ConnectionService and TrustSafetyService constructors
// Pass activationPolicy to ProfileUseCases constructor
```

**Changes to `ViewModelFactory.java`:**
```java
// Pass activationPolicy to LoginViewModel constructor
// ProfileActivationPolicy can be obtained from ServiceRegistry or created directly
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| MODIFY | `src/main/java/datingapp/core/connection/ConnectionService.java` | Add workflowPolicy param, replace inline guards |
| MODIFY | `src/main/java/datingapp/core/matching/TrustSafetyService.java` | Add workflowPolicy param, replace block guard |
| MODIFY | `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` | Add activationPolicy param, replace activation logic |
| MODIFY | `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java` | Replace direct activation with policy call |
| MODIFY | `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java` | Replace attemptActivation with policy call |
| MODIFY | `src/main/java/datingapp/storage/StorageFactory.java` | Create policy instances, pass to constructors |
| MODIFY | `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java` | Pass activationPolicy to LoginViewModel |

### Wiring Instructions

1. **ServiceRegistry**: Optionally add `RelationshipWorkflowPolicy` and `ProfileActivationPolicy` as fields/getters. Since both are stateless, they can also be created locally in `StorageFactory` without registry exposure.
2. **ConnectionService constructor**: Existing params + new `RelationshipWorkflowPolicy`. Update ALL call sites (StorageFactory + tests).
3. **TrustSafetyService constructor**: Existing params + new `RelationshipWorkflowPolicy`. Update ALL call sites.
4. **ProfileUseCases**: Created inside `ServiceRegistry` constructor (lines 88-104). Add `activationPolicy` parameter.
5. **LoginViewModel**: Created in `ViewModelFactory.getLoginViewModel()`. Add `activationPolicy` parameter.

### Test Specification

**Update existing tests:**
- `RelationshipTransitionServiceTest` → pass `new RelationshipWorkflowPolicy()` to ConnectionService constructor
- `MatchingUseCasesTest` → may need workflowPolicy for services it constructs
- `ProfileUseCasesTest` → pass `new ProfileActivationPolicy()` to ProfileUseCases constructor
- `SocialViewModelTest` → update ConnectionService construction

**New integration tests:**
```java
@Test void connectionServiceUsesWorkflowPolicyForFriendZone()
// Transition UNMATCHED match to friend zone → failure from policy

@Test void profileUseCasesActivatesViaPolicy()
// Save complete profile → activated flag true

@Test void loginViewModelActivatesViaPolicy()
// Login with complete user → user state becomes ACTIVE
```

### Edge Cases & Gotchas

1. **ConnectionService.sendMessage() has specific ErrorCodes**: The current code maps failures to `SendResult.ErrorCode` (USER_NOT_FOUND, NO_ACTIVE_MATCH, etc.). When using the policy, you still need to produce the correct ErrorCode. Solution: check `WorkflowDecision.Denied.reasonCode()` to map back: "SENDER_NOT_ACTIVE" → USER_NOT_FOUND, "MATCH_NOT_MESSAGEABLE" → NO_ACTIVE_MATCH.
2. **Constructor signature changes cascade**: Adding parameters to `ConnectionService` affects `StorageFactory`, all tests that construct it, and any mock/spy of it. Use IDE-assisted refactoring or search for `new ConnectionService(`.
3. **LoginViewModel auto-complete still needed**: The `autoCompleteUserProfile()` method (lines 213-259) that fills default values should STAY. The policy is called AFTER auto-completion. Don't remove auto-complete logic.
4. **`Match.isInvalidTransition()` stays private**: Do NOT delete or change `Match.isInvalidTransition()`. The entity still validates internally when `match.unmatch()` etc. are called. The policy provides an ADDITIONAL check layer. Over time, the entity validation can be relaxed once all callers use the policy.
5. **Compensating revert in `acceptFriendZone()` stays**: The `match.revertToActive()` compensating transaction (ConnectionService line 339) is NOT affected by the policy. It's a recovery mechanism, not a user-facing transition.

### Acceptance Criteria

- [ ] ConnectionService consults `RelationshipWorkflowPolicy` for ALL transition operations
- [ ] TrustSafetyService consults `RelationshipWorkflowPolicy` for block operations
- [ ] ProfileUseCases uses `ProfileActivationPolicy.tryActivate()` instead of inline checks
- [ ] LoginViewModel uses `ProfileActivationPolicy` instead of direct `activate()` calls
- [ ] ProfileViewModel's `attemptActivation()` uses policy
- [ ] ALL existing tests pass (no behavior change for valid transitions)
- [ ] Invalid transition error messages are preserved (test regression)
- [ ] `mvn spotless:apply && mvn verify` succeeds

### Verification Gate

```bash
mvn spotless:apply
mvn test
# Expected: ALL tests pass
mvn verify
# Expected: full quality gates pass
```

### Rollback Notes

Revert constructor changes to ConnectionService, TrustSafetyService, ProfileUseCases. Restore inline guard checks. High-touch rollback (7 files) — consider reverting via git.

---

## WU-09: Define Domain Events + Implement Event Handlers

**Finding:** 13
**Dependencies:** WU-04 (event bus), WU-05 (wiring)
**Parallel group:** D (can run with WU-13, WU-14)

### Current State

Side effects are triggered imperatively at these locations (from Finding 13 analysis):

| Side Effect | Location | Type |
|-------------|----------|------|
| Achievement unlock | MatchingHandler:739 | REQUIRED |
| Profile view analytics | MatchingHandler:236 | BEST_EFFORT |
| Swipe metrics | MatchingService:117 | BEST_EFFORT |
| Activity metrics (message) | ConnectionService:106 | BEST_EFFORT |
| Friend request notification | ConnectionService:271 | REQUIRED |
| Acceptance notification | ConnectionService:344 | BEST_EFFORT |
| Graceful exit notification | ConnectionService:425 | BEST_EFFORT |

### Target State

Event handler classes that subscribe to events and execute side effects. These are standalone handlers — WU-10 will convert the emission sites.

**File:** `src/main/java/datingapp/app/event/handlers/AchievementEventHandler.java`
```java
package datingapp.app.event.handlers;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus.AppEventHandler;
import datingapp.core.LoggingSupport;
import datingapp.core.profile.ProfileService;

// Handles SwipeRecorded + MatchCreated → checks achievement unlock.

public final class AchievementEventHandler implements
        AppEventHandler<AppEvent.SwipeRecorded>, LoggingSupport {

    private final ProfileService profileService;

    public AchievementEventHandler(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public void handle(AppEvent.SwipeRecorded event) {
        // If event.resulted_in_match():
        //   profileService.checkAndUnlock(event.swiperId())
        // Pseudocode: mirrors MatchingHandler line 739 logic
    }

    // Register method (called during wiring):
    public void registerOn(AppEventBus bus) {
        bus.subscribe(AppEvent.SwipeRecorded.class, this, HandlerPolicy.BEST_EFFORT);
        // Achievements are BEST_EFFORT: failure should not block the swipe
    }
}
```

**File:** `src/main/java/datingapp/app/event/handlers/MetricsEventHandler.java`
```java
package datingapp.app.event.handlers;

// Handles SwipeRecorded → records swipe metrics.
// Handles MessageSent → records activity metrics.

public final class MetricsEventHandler implements LoggingSupport {

    private final ActivityMetricsService activityMetricsService;

    // Constructor receives nullable activityMetricsService (matches current pattern)

    public void handleSwipe(AppEvent.SwipeRecorded event) {
        // if activityMetricsService == null return;
        // activityMetricsService.recordSwipe(swiperId, direction, resulted_in_match)
        // Mirrors MatchingService:117
    }

    public void handleMessage(AppEvent.MessageSent event) {
        // if activityMetricsService == null return;
        // activityMetricsService.recordActivity(senderId)
        // Mirrors ConnectionService:106
    }

    public void registerOn(AppEventBus bus) {
        bus.subscribe(AppEvent.SwipeRecorded.class, this::handleSwipe, HandlerPolicy.BEST_EFFORT);
        bus.subscribe(AppEvent.MessageSent.class, this::handleMessage, HandlerPolicy.BEST_EFFORT);
    }
}
```

**File:** `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`
```java
package datingapp.app.event.handlers;

// Handles RelationshipTransitioned → creates notifications for graceful exit, friend zone acceptance.

public final class NotificationEventHandler implements LoggingSupport {

    private final CommunicationStorage communicationStorage;

    public void handleTransition(AppEvent.RelationshipTransitioned event) {
        // Switch on event.toState():
        //   "GRACEFUL_EXIT" → create + save notification for targetId
        //   "FRIENDS" (from acceptance) → create + save notification for initiatorId
        //   Others → no notification
        // Mirrors ConnectionService:394-399 (graceful exit) and :312-317 (acceptance)
    }

    public void registerOn(AppEventBus bus) {
        bus.subscribe(AppEvent.RelationshipTransitioned.class, this::handleTransition,
                      HandlerPolicy.BEST_EFFORT);
    }
}
```

**Handler registration wiring (in StorageFactory.buildH2 or a new method):**
```java
// After creating eventBus and services:
new AchievementEventHandler(profileService).registerOn(eventBus);
new MetricsEventHandler(activityMetricsService).registerOn(eventBus);
new NotificationEventHandler(communicationStorage).registerOn(eventBus);
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| CREATE | `src/main/java/datingapp/app/event/handlers/AchievementEventHandler.java` | Achievement unlock handler |
| CREATE | `src/main/java/datingapp/app/event/handlers/MetricsEventHandler.java` | Swipe + activity metrics handler |
| CREATE | `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java` | Transition notification handler |
| MODIFY | `src/main/java/datingapp/storage/StorageFactory.java` | Register handlers on event bus |

### Wiring Instructions

In `StorageFactory.buildH2()`, after creating `InProcessAppEventBus` and all services, register handlers:
```java
// Handler registration (add before returning ServiceRegistry):
new AchievementEventHandler(profileService).registerOn(eventBus);
new MetricsEventHandler(activityMetricsService).registerOn(eventBus);
new NotificationEventHandler(communicationStorage).registerOn(eventBus);
```

### Test Specification

**File:** `src/test/java/datingapp/app/event/handlers/AchievementEventHandlerTest.java`
```java
@Test void swipeWithMatchTriggersAchievementCheck()
@Test void swipeWithoutMatchDoesNotTriggerAchievementCheck()
@Test void handlerFailureDoesNotPropagate()  // BEST_EFFORT
```

**File:** `src/test/java/datingapp/app/event/handlers/MetricsEventHandlerTest.java`
```java
@Test void swipeRecordedTriggersSwipeMetric()
@Test void messageSentTriggersActivityMetric()
@Test void nullMetricsServiceHandledGracefully()
```

**File:** `src/test/java/datingapp/app/event/handlers/NotificationEventHandlerTest.java`
```java
@Test void gracefulExitCreatesNotification()
@Test void friendsTransitionCreatesNotification()
@Test void unmatchDoesNotCreateNotification()
@Test void blockDoesNotCreateNotification()
```

### Edge Cases & Gotchas

1. **Handler receives nullable services**: `MetricsEventHandler` must handle `activityMetricsService == null` (matches existing null-check pattern in MatchingService and ConnectionService).
2. **Notification text must match current behavior**: The notification messages created by `NotificationEventHandler` must match the current strings in ConnectionService (e.g., "The other user has gracefully moved on from this relationship."). Copy the exact text.
3. **Event handlers are NOT yet called**: This WU only creates the handlers and registers them. The actual event emission (replacing imperative calls) happens in WU-10. During this intermediate state, side effects fire BOTH from the old imperative path AND (if events happen to be published by tests) from handlers.
4. **Friend request notification stays in ConnectionService for now**: The friend request creation notification (ConnectionService:262-271) is tightly coupled to the `saveFriendRequestWithNotification` atomic call. It's NOT moved to an event handler yet — it stays imperative until WU-10 evaluates if it can be decoupled.

### Acceptance Criteria

- [ ] Three handler classes created in `app/event/handlers/`
- [ ] Each handler has a `registerOn(AppEventBus)` method
- [ ] Handlers are registered in `StorageFactory.buildH2()`
- [ ] Achievement handler mirrors MatchingHandler:739 logic
- [ ] Metrics handler mirrors MatchingService:117 + ConnectionService:106
- [ ] Notification handler mirrors ConnectionService notification creation
- [ ] All handler tests pass
- [ ] `mvn spotless:apply && mvn verify` succeeds

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.app.event.handlers.*"
mvn test  # full suite — no regressions
```

### Rollback Notes

Delete `app/event/handlers/` directory. Remove registration lines from StorageFactory. No behavior change since events aren't published yet.

---

## WU-10: Convert Imperative Side-Effects to Event Emission

**Finding:** 13
**Dependencies:** WU-09 (handlers exist and are registered)
**Parallel group:** F

### Current State

Side effects are triggered directly in:
1. `MatchingHandler.displaySwipeResult()` (line 286) → `checkAndDisplayNewAchievements()`
2. `MatchingHandler.processCandidateInteraction()` (line 236) → `analyticsStorage.recordProfileView()`
3. `MatchingService.recordLike()` (line 117) → `activityMetricsService.recordSwipe()`
4. `ConnectionService.sendMessage()` (line 106) → `activityMetricsService.recordActivity()`
5. `ConnectionService.gracefulExit()` (line 425) → notification creation
6. `ConnectionService.acceptFriendZone()` (line 344) → acceptance notification
7. `SocialViewModel.markNotificationRead()` (line 206) → direct storage fallback
8. `MatchesViewModel.performLikeBack()` (line 561) → direct service fallback

### Target State

Use-cases publish events after primary action succeeds. Event handlers (from WU-09) execute the side effects. Imperative side-effect calls are REMOVED from the call sites listed above.

**Key principle**: Events are published in USE-CASES, not in core services or adapters. Core services remain pure business logic. Adapters call use-cases.

**Changes to `MatchingUseCases.java` (use-case layer):**
```java
// ADD field: private final AppEventBus eventBus;
// ADD constructor parameter

// In processSwipe() after successful swipe:
//   eventBus.publish(new AppEvent.SwipeRecorded(
//       userId, candidateId, direction.name(), result.matched(), AppClock.now()));

// In recordLike() after successful like:
//   if (match.isPresent()) {
//       eventBus.publish(new AppEvent.MatchCreated(
//           match.get().getId(), like.whoLikes(), like.whoIsLiked(), AppClock.now()));
//   }
```

**Changes to `MessagingUseCases.java`:**
```java
// In sendMessage() after successful send:
//   eventBus.publish(new AppEvent.MessageSent(
//       senderId, recipientId, message.id(), AppClock.now()));
```

**Changes to `SocialUseCases.java`:**
```java
// In acceptFriendZone (or relevant method) after successful acceptance:
//   eventBus.publish(new AppEvent.FriendRequestAccepted(
//       requestId, fromUserId, toUserId, matchId, AppClock.now()));
//   eventBus.publish(new AppEvent.RelationshipTransitioned(
//       matchId, responderId, requesterId, "ACTIVE", "FRIENDS", AppClock.now()));
```

**Changes to `ProfileUseCases.java`:**
```java
// In saveProfile() after successful save + activation:
//   eventBus.publish(new AppEvent.ProfileSaved(
//       userId, activated, AppClock.now()));
```

**REMOVE from `MatchingService.java`:**
```java
// Line 115-118: REMOVE activityMetricsService.recordSwipe() call
// (Now handled by MetricsEventHandler via SwipeRecorded event)
```

**REMOVE from `ConnectionService.java`:**
```java
// Line 105-107: REMOVE activityMetricsService.recordActivity() call
// Line 394-399 + 425: REMOVE graceful exit notification creation
//   (Now handled by NotificationEventHandler via RelationshipTransitioned event)
// Line 312-317 + 344: REMOVE acceptance notification creation
//   (Now handled by NotificationEventHandler via FriendRequestAccepted/RelationshipTransitioned event)
// NOTE: Keep friend request creation notification (line 262-271) — it's part of the atomic save
```

**REMOVE from `MatchingHandler.java`:**
```java
// Line 286: REMOVE checkAndDisplayNewAchievements() call after match
// (Now handled by AchievementEventHandler via SwipeRecorded event)
// NOTE: Keep the display of "It's a Match!" — that's UI, not a side effect
```

**REMOVE fallback paths from ViewModels:**
```java
// SocialViewModel.markNotificationRead (line 206):
//   Remove direct socialDataAccess fallback — UseCase path should be the only path

// MatchesViewModel.performLikeBack (line 561):
//   Remove direct matchingService.recordLike fallback — UseCase path should be the only path

// MatchesViewModel.performPassOn (line 570):
//   Remove direct matchingService.recordLike fallback

// MatchesViewModel.performWithdrawLike (line 579):
//   Remove direct matchData.deleteLike fallback
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| MODIFY | `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` | Add eventBus, publish SwipeRecorded + MatchCreated |
| MODIFY | `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java` | Add eventBus, publish MessageSent |
| MODIFY | `src/main/java/datingapp/app/usecase/social/SocialUseCases.java` | Add eventBus, publish FriendRequestAccepted + RelationshipTransitioned |
| MODIFY | `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` | Add eventBus, publish ProfileSaved |
| MODIFY | `src/main/java/datingapp/core/matching/MatchingService.java` | Remove direct metrics call |
| MODIFY | `src/main/java/datingapp/core/connection/ConnectionService.java` | Remove direct metrics + notification calls |

### Wiring Instructions

1. All 4 UseCase classes receive `AppEventBus` as new constructor parameter.
2. UseCases are constructed inside `ServiceRegistry` constructor (lines 88-104). Pass `eventBus` to each.
3. Update `ServiceRegistry` constructor to pass eventBus to UseCase constructors.

### Test Specification

**Update existing tests:**
- `MatchingUseCasesTest` → verify event published after swipe (use a test spy on eventBus)
- `MessagingUseCasesTest` → verify MessageSent event published
- `SocialUseCasesTest` → verify transition events published
- `ProfileUseCasesTest` → verify ProfileSaved event published

**New integration test:**
```java
@Test void swipeTriggersAchievementViaEventPipeline()
// Wire real event bus + achievement handler + use case
// Perform swipe that creates match
// Assert achievement check was called (via test double)

@Test void messageTriggersActivityMetricViaEventPipeline()
// Wire real event bus + metrics handler + use case
// Send message
// Assert activity metric was recorded
```

### Edge Cases & Gotchas

1. **Friend request notification atomic save**: The `saveFriendRequestWithNotification` call in ConnectionService (line 271) saves the friend request AND notification atomically. Do NOT move this notification to an event handler — the notification is part of the friend request creation transaction. Only the ACCEPTANCE notification (informational) moves to events.
2. **ViewModel fallback removal order**: Remove fallback paths ONLY after confirming the UseCase paths work. If UseCase paths have bugs, the fallback removal would break functionality. Test the UseCase paths first.
3. **Achievement display in CLI**: `MatchingHandler.displaySwipeResult()` currently both checks achievements AND displays them. The event handler only CHECKS (unlocks). The DISPLAY of achievements in CLI still needs to happen. Solution: the event handler unlocks, then MatchingHandler can query for newly unlocked achievements to display. Or, the event can carry a result that MatchingHandler reads.
4. **Metrics service null checks**: When removing `activityMetricsService` calls from MatchingService and ConnectionService, also remove the null-check guards (lines 115-118 and 105-107). The event handler already handles null metrics service.
5. **Event ordering**: Events are published AFTER the primary action succeeds. If the primary action fails, no event is published. This preserves current behavior where metrics/notifications only fire on success.

### Acceptance Criteria

- [ ] All 4 UseCase classes publish appropriate events after successful actions
- [ ] Direct metrics calls removed from MatchingService and ConnectionService
- [ ] Direct notification creation removed from ConnectionService (except friend request atomic save)
- [ ] ViewModel fallback direct-service calls removed
- [ ] Side effects fire consistently across CLI/UI paths (both go through same UseCases)
- [ ] ALL existing tests pass with updated assertions
- [ ] `mvn spotless:apply && mvn verify` succeeds

### Verification Gate

```bash
mvn spotless:apply
mvn test
# Expected: ALL tests pass
# Verify side-effect parity: check that achievement, metrics, and notification
# tests exercise the event pipeline path, not the removed imperative path
mvn verify
```

### Rollback Notes

Restore imperative side-effect calls in MatchingService, ConnectionService, MatchingHandler. Restore ViewModel fallback paths. Remove event emission from UseCases. High-touch rollback — consider git revert.

---

## WU-11: Schema V3 Migration + Normalized Table DAOs

**Finding:** 12
**Dependencies:** none
**Parallel group:** A (can run with WU-01 through WU-04)

### Current State

Multi-value fields are stored as VARCHAR columns with JSON serialization (and CSV fallback on read):

| Column | Table | Type | Serialization |
|--------|-------|------|--------------|
| `interested_in` | users | VARCHAR(100) | JSON array of Gender enum names |
| `photo_urls` | users | VARCHAR(1000) | JSON array of URL strings |
| `interests` | users | VARCHAR(500) | JSON array of Interest enum names |
| `db_smoking` | users | VARCHAR(100) | JSON array of Smoking enum names |
| `db_drinking` | users | VARCHAR(100) | JSON array of Drinking enum names |
| `db_wants_kids` | users | VARCHAR(100) | JSON array of KidsStance enum names |
| `db_looking_for` | users | VARCHAR(100) | JSON array of LookingFor enum names |
| `db_education` | users | VARCHAR(200) | JSON array of Education enum names |

Schema management:
- `SchemaInitializer.java` — DDL for all tables
- `MigrationRunner.java` — 2 existing versions (V1 baseline, V2 backfill)
- Read path: `JdbiUserStorage.Mapper` (lines 274-459) with `parseMultiValueTokens()` JSON→CSV fallback
- Write path: `JdbiUserStorage.UserSqlBindings` (lines 476-723) with `serializeEnumSet()` to JSON

### Target State

Normalized junction tables for multi-value fields. Migration V3 creates the tables. New DAO methods for reading/writing normalized data.

**Migration V3 DDL (to add in `MigrationRunner.applyV3()`):**
```sql
-- Photo URLs: ordered list
CREATE TABLE IF NOT EXISTS user_photos (
    user_id UUID NOT NULL,
    position INT NOT NULL,
    url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, position),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Interests: set of enum values
CREATE TABLE IF NOT EXISTS user_interests (
    user_id UUID NOT NULL,
    interest VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, interest),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Gender preferences: set of enum values
CREATE TABLE IF NOT EXISTS user_interested_in (
    user_id UUID NOT NULL,
    gender VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, gender),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Dealbreaker multi-value tables (one per dealbreaker dimension):
CREATE TABLE IF NOT EXISTS user_db_smoking (
    user_id UUID NOT NULL,
    value VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, value),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS user_db_drinking (
    user_id UUID NOT NULL,
    value VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, value),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS user_db_wants_kids (
    user_id UUID NOT NULL,
    value VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, value),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS user_db_looking_for (
    user_id UUID NOT NULL,
    value VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, value),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS user_db_education (
    user_id UUID NOT NULL,
    value VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, value),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes for reverse lookups (find users by interest/gender):
CREATE INDEX IF NOT EXISTS idx_user_interests_interest ON user_interests(interest);
CREATE INDEX IF NOT EXISTS idx_user_interested_in_gender ON user_interested_in(gender);
```

**Changes to `MigrationRunner.java`:**
```java
// Add to MIGRATIONS list:
new VersionedMigration(3, "Normalize multi-value profile fields into junction tables",
    MigrationRunner::applyV3)

// New method:
private static void applyV3(Statement stmt) {
    // Execute all CREATE TABLE IF NOT EXISTS statements above
    // Execute all CREATE INDEX IF NOT EXISTS statements
    // No data migration yet — that's WU-12
}
```

**Changes to `SchemaInitializer.java`:**
```java
// Add DDL for normalized tables in the initialization flow
// These tables should be created alongside the users table
// Use IF NOT EXISTS for idempotency
```

**New DAO methods in `JdbiUserStorage.java` (or new support class):**
```java
// Write methods (for dual-write in WU-12):
void saveUserPhotos(UUID userId, List<String> urls)
// DELETE FROM user_photos WHERE user_id = :userId
// INSERT INTO user_photos (user_id, position, url) VALUES (:userId, :pos, :url)

void saveUserInterests(UUID userId, Set<Interest> interests)
// DELETE FROM user_interests WHERE user_id = :userId
// INSERT INTO user_interests (user_id, interest) VALUES (:userId, :name)

void saveUserInterestedIn(UUID userId, Set<Gender> genders)
// DELETE + INSERT pattern

void saveUserDealbreakers(UUID userId, Dealbreakers dealbreakers)
// DELETE + INSERT for each dealbreaker dimension

// Read methods (for dual-read in WU-12):
List<String> loadUserPhotos(UUID userId)
// SELECT url FROM user_photos WHERE user_id = :userId ORDER BY position

Set<String> loadUserInterests(UUID userId)
// SELECT interest FROM user_interests WHERE user_id = :userId

Set<String> loadUserInterestedIn(UUID userId)
// SELECT gender FROM user_interested_in WHERE user_id = :userId

// Similar for each dealbreaker table...
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| MODIFY | `src/main/java/datingapp/storage/schema/MigrationRunner.java` | Add V3 migration + applyV3 method |
| MODIFY | `src/main/java/datingapp/storage/schema/SchemaInitializer.java` | Add normalized table DDL |
| MODIFY | `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` | Add DAO methods for normalized tables |

### Wiring Instructions

None — schema changes are applied automatically by `MigrationRunner.runAllPending()` during startup. DAO methods are instance methods on the existing `JdbiUserStorage`.

### Test Specification

**Update:** `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
```java
@Test void normalizedTablesExistAfterInit()
// After schema init, verify all 8 normalized tables exist
// Use DatabaseMetaData.getTables() to check

@Test void normalizedTablesHaveForeignKeys()
// Verify FK constraints reference users(id)
```

**Update:** `src/test/java/datingapp/storage/schema/SchemaParityTest.java`
```java
// Ensure fresh DB and migrated DB have identical schema
// Including the new normalized tables
```

**New:** `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
```java
@Test void saveAndLoadPhotosRoundTrip()
// Save 3 photos, load them, verify order preserved

@Test void saveAndLoadInterestsRoundTrip()
// Save interests, load them, verify all present

@Test void saveAndLoadInterestedInRoundTrip()

@Test void saveAndLoadDealbreakerDimensionsRoundTrip()
// Test each dealbreaker dimension

@Test void savePhotosReplacesExisting()
// Save 3 photos, then save 2 different photos → only 2 returned

@Test void emptySetWritesNoRows()
// Save empty interests → loadUserInterests returns empty set
```

### Edge Cases & Gotchas

1. **H2 specific syntax**: All DDL uses H2-compatible SQL. `TIMESTAMP WITH TIME ZONE` is supported in H2. `UUID` type is native. `IF NOT EXISTS` is supported for both tables and indexes.
2. **Photo ordering**: `user_photos` uses `position` column (0-based) to maintain order. The DAO must preserve insertion order.
3. **Delete-then-insert pattern**: For save operations, always DELETE all rows for the user first, then INSERT the new set. This is simpler than diffing and avoids orphaned rows. Wrap in a transaction if JDBI supports it (it does via `useTransaction`).
4. **Enum name storage**: Store enum names as strings (e.g., "MALE", "FEMALE"). Do NOT store ordinal values. Matches existing JSON serialization pattern.
5. **Legacy columns stay**: Do NOT remove or alter the existing VARCHAR columns in this WU. They remain for backward compatibility. WU-12 handles dual-write/read and eventual removal.
6. **SchemaParityTest**: This test verifies fresh DB schema matches migrated DB schema. After adding V3, both paths must produce identical tables. Ensure `SchemaInitializer` and `MigrationRunner.applyV3` create the same structures.

### Acceptance Criteria

- [ ] V3 migration creates 8 normalized tables with correct schemas
- [ ] All tables have composite primary keys (user_id + value/position)
- [ ] All tables have FK to users(id)
- [ ] DAO save methods replace all rows for a user (delete + insert)
- [ ] DAO load methods return correct data after save
- [ ] Schema parity test passes (fresh == migrated)
- [ ] Existing tests pass (legacy columns untouched)
- [ ] `mvn spotless:apply && mvn verify` succeeds

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.storage.schema.*,datingapp.storage.jdbi.JdbiUserStorageNormalizationTest"
mvn test  # full suite
mvn verify
```

### Rollback Notes

Remove V3 from MIGRATIONS list. Remove normalized table DDL from SchemaInitializer. Remove DAO methods. Tables will remain in existing databases but be unused (harmless). For clean rollback, add a V4 migration that drops the tables.

---

## WU-12: Dual-Write/Read Migration + Legacy Removal

**Finding:** 12
**Dependencies:** WU-11 (normalized tables + DAOs exist)
**Parallel group:** G

### Current State (after WU-11)

Both old VARCHAR columns AND new normalized tables exist. Data is only in the VARCHAR columns. New DAO methods exist but are not called during normal save/load.

### Target State

Three-phase migration within this single WU:
1. **Dual-write**: Save to both legacy columns AND normalized tables
2. **Backfill**: Migrate existing legacy data to normalized tables
3. **Cutover**: Read from normalized tables, stop writing legacy columns, remove fallback parsing

**Phase 1 — Dual-write changes to `JdbiUserStorage.save()`:**
```java
// In the save() method (after the existing MERGE statement):
// ADD: call the new DAO methods to write normalized data:
//   saveUserPhotos(user.getId(), user.getPhotoUrls());
//   saveUserInterests(user.getId(), user.getInterests());
//   saveUserInterestedIn(user.getId(), user.getInterestedIn());
//   saveUserDealbreakers(user.getId(), user.getDealbreakers());
// The MERGE still writes to legacy columns too (dual-write)
```

**Phase 2 — Backfill method:**
```java
// New method in JdbiUserStorage:
public void backfillNormalizedData() {
    // 1. SELECT id FROM users
    // 2. For each user:
    //    a. Read multi-value fields from legacy columns (existing parsing)
    //    b. Write to normalized tables (using new DAO methods)
    //    c. Idempotent: delete + insert pattern means re-running is safe
    // 3. Log progress: "Backfilled N users"
}
```

**Phase 3 — Cutover changes to `JdbiUserStorage.Mapper`:**
```java
// In map() / readPhotoUrls() / readEnumSet():
// REPLACE: reading from legacy VARCHAR columns with JSON/CSV parsing
// WITH: reading from normalized tables via DAO load methods

// Specifically:
//   readPhotoUrls() → loadUserPhotos(userId)
//   readGenderSet("interested_in") → loadUserInterestedIn(userId)
//   readInterestSet("interests") → loadUserInterests(userId)
//   readDealbreakers() → loadUserDealbreakers(userId)

// REMOVE: parseMultiValueTokens(), parsePhotoUrlsJson() methods
// REMOVE: CSV fallback parsing logic
// REMOVE: serializeEnumSet() from UserSqlBindings (or keep for legacy column write if still dual-writing)
```

**Phase 3 — Stop writing legacy columns:**
```java
// In UserSqlBindings:
// Set legacy VARCHAR columns to NULL in the MERGE statement
// (or simply stop binding them — use NULL literals)
// Eventually remove the columns in a future migration (V4)
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| MODIFY | `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` | Dual-write in save(), backfill method, cutover reads, remove parsers |
| MODIFY | `src/main/java/datingapp/storage/schema/MigrationRunner.java` | Optional: add backfill call to V3 or create V4 |
| MODIFY | `src/main/java/datingapp/storage/schema/SchemaInitializer.java` | No changes unless removing legacy columns |

### Wiring Instructions

**Backfill trigger options:**
- Option A: Add backfill call at end of `MigrationRunner.applyV3()` — runs once during migration
- Option B: Add a separate V4 migration that calls `backfillNormalizedData()`
- Option C: Run backfill manually via a one-time startup hook
- **Recommended: Option A** — simplest, runs automatically, idempotent

### Test Specification

**Update:** `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
```java
@Test void saveWritesBothLegacyAndNormalized()
// Save user, verify data in both legacy columns AND normalized tables

@Test void backfillMigratesExistingLegacyData()
// Insert user with legacy data only (direct SQL INSERT)
// Run backfillNormalizedData()
// Verify normalized tables contain the migrated data

@Test void backfillIsIdempotent()
// Run backfill twice → same result, no duplicates

@Test void cutoverReadsFromNormalizedTables()
// After cutover: save user, load user, verify data comes from normalized tables
// Verify legacy column parsing methods are not called

@Test void legacyColumnRemovalDoesNotBreakLoad()
// After cutover: set legacy columns to NULL
// Load user → data comes from normalized tables
```

### Edge Cases & Gotchas

1. **Backfill with NULL legacy columns**: Some users may have NULL in legacy multi-value columns (new users who never set preferences). Backfill must handle NULL gracefully — write zero rows to normalized tables.
2. **Backfill with empty JSON arrays**: `"[]"` in legacy columns should produce zero rows in normalized tables, not one row with empty string.
3. **Photo URL ordering**: When backfilling photos, preserve the order from the JSON array. The `position` column should be set to the array index.
4. **Invalid enum values in legacy data**: Legacy CSV data may contain enum values that were renamed or removed. The backfill should skip invalid values and log a warning (matching existing `readEnumSet` behavior at line 415-425).
5. **Transaction scope**: The dual-write (MERGE + normalized inserts) should ideally be in the same transaction. Check if the JDBI `useHandle` call in `save()` supports this.
6. **Binding parameter naming**: The existing bindings use `*Csv` names (e.g., `getInterestedInCsv()`). During cutover, when you stop writing legacy columns, you can either keep binding NULL values or remove the columns from the MERGE statement. Removing columns from the MERGE is cleaner but changes the SQL string.

### Acceptance Criteria

- [ ] Dual-write: saving a user populates both legacy columns and normalized tables
- [ ] Backfill: existing legacy data is migrated to normalized tables idempotently
- [ ] Cutover: reads come from normalized tables, not legacy column parsing
- [ ] Legacy JSON/CSV parsing code is removed from Mapper class
- [ ] No data loss: all multi-value data round-trips correctly
- [ ] Schema parity test passes
- [ ] ALL existing tests pass (user save/load behavior unchanged)
- [ ] `mvn spotless:apply && mvn verify` succeeds

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.storage.**"
mvn test  # full suite
mvn verify
```

### Rollback Notes

Revert JdbiUserStorage to read from legacy columns (restore parsing code). Stop dual-writing to normalized tables. Normalized tables can remain (harmless empty data). Medium-complexity rollback.

---

## WU-13: End-to-End Typed Failure Conversion + Error Mappers

**Finding:** 14
**Dependencies:** WU-02 (AppError hierarchy), WU-05 (wiring)
**Parallel group:** D (can run with WU-09, WU-14)

### Current State

Error handling fragmentation:

1. **Use-case layer**: `UseCaseResult<T>` with `UseCaseError.Code` (6 codes)
2. **Core service results**: Ad-hoc records with string error messages:
   - `TransitionResult(boolean success, FriendRequest, String errorMessage)`
   - `SendResult(boolean success, Message, String errorMessage, ErrorCode)`
   - `SwipeResult(boolean success, boolean matched, Match, Like, String message)`
   - `UndoResult(boolean success, String message, Like, boolean matchDeleted)`
3. **Storage layer**: Boolean returns for operations that can fail:
   - `InteractionStorage.acceptFriendZoneTransition()` → returns `boolean`
   - `InteractionStorage.gracefulExitTransition()` → returns `boolean`
4. **REST API**: All failures mapped to 409 CONFLICT regardless of error type
5. **CLI**: `"❌ Failed: {}"` with error message string

### Target State

Three error mapper classes that translate `AppError` to channel-specific responses. Gradual conversion of core service results to use `AppError` at use-case boundaries.

**File:** `src/main/java/datingapp/app/error/ApiErrorMapper.java`
```java
package datingapp.app.error;

// Maps AppError to HTTP status + JSON response body.

public final class ApiErrorMapper {

    public record ApiErrorResponse(int status, String code, String message) {}

    public static ApiErrorResponse toHttp(AppError error) {
        return switch (error) {
            case AppError.Validation v -> new ApiErrorResponse(400, "VALIDATION", v.message());
            case AppError.NotFound n -> new ApiErrorResponse(404, "NOT_FOUND", n.message());
            case AppError.Conflict c -> new ApiErrorResponse(409, "CONFLICT", c.message());
            case AppError.Forbidden f -> new ApiErrorResponse(403, "FORBIDDEN", f.message());
            case AppError.Dependency d -> new ApiErrorResponse(503, "DEPENDENCY", d.message());
            case AppError.Infrastructure i -> new ApiErrorResponse(500, "INFRASTRUCTURE", i.message());
            case AppError.Internal i -> new ApiErrorResponse(500, "INTERNAL", i.message());
        };
    }
}
```

**File:** `src/main/java/datingapp/app/error/CliErrorMapper.java`
```java
package datingapp.app.error;

// Maps AppError to CLI-friendly formatted strings.

public final class CliErrorMapper {

    public static String toCliMessage(AppError error) {
        return switch (error) {
            case AppError.Validation v -> "Invalid input: " + v.message()
                + (v.field() != null ? " (field: " + v.field() + ")" : "");
            case AppError.NotFound n -> n.resourceType() != null
                ? n.resourceType() + " not found: " + n.message()
                : "Not found: " + n.message();
            case AppError.Conflict c -> "Cannot proceed: " + c.message();
            case AppError.Forbidden f -> "Not allowed: " + f.message();
            case AppError.Dependency d -> "Service unavailable: " + d.message();
            case AppError.Infrastructure i -> "System error: " + i.message();
            case AppError.Internal i -> "Unexpected error: " + i.message();
        };
    }
}
```

**File:** `src/main/java/datingapp/app/error/ViewModelErrorMapper.java`
```java
package datingapp.app.error;

// Maps AppError to UI-friendly messages (used by ViewModels for UiFeedbackService).

public final class ViewModelErrorMapper {

    public static String toUserMessage(AppError error) {
        return switch (error) {
            case AppError.Validation v -> v.message();
            case AppError.NotFound n -> "Could not find what you're looking for.";
            case AppError.Conflict c -> c.message();
            case AppError.Forbidden f -> "You don't have permission to do that.";
            case AppError.Dependency d -> "A required service is unavailable. Please try again.";
            case AppError.Infrastructure i -> "Something went wrong. Please try again.";
            case AppError.Internal i -> "An unexpected error occurred.";
        };
    }
}
```

**Changes to `RestApiServer.java`:**
```java
// Replace ad-hoc 409 mappings with deterministic mapper:
// BEFORE (lines 226-228):
//   ctx.status(409); ctx.json(new ErrorResponse(CONFLICT, error.message()));
// AFTER:
//   var apiError = ApiErrorMapper.toHttp(AppError.fromUseCaseError(result.error()));
//   ctx.status(apiError.status());
//   ctx.json(new ErrorResponse(apiError.code(), apiError.message()));
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| CREATE | `src/main/java/datingapp/app/error/ApiErrorMapper.java` | HTTP status mapper |
| CREATE | `src/main/java/datingapp/app/error/CliErrorMapper.java` | CLI text mapper |
| CREATE | `src/main/java/datingapp/app/error/ViewModelErrorMapper.java` | UI message mapper |
| MODIFY | `src/main/java/datingapp/app/api/RestApiServer.java` | Use ApiErrorMapper instead of hardcoded 409 |
| MODIFY | Various use-case files | Convert service results to AppError at boundaries |

### Wiring Instructions

Error mappers are stateless utility classes with static methods. No wiring needed. They are called directly at adapter boundaries.

### Test Specification

**File:** `src/test/java/datingapp/app/error/ApiErrorMapperTest.java`
```java
@Test void validationMapsTo400()
@Test void notFoundMapsTo404()
@Test void conflictMapsTo409()
@Test void forbiddenMapsTo403()
@Test void dependencyMapsTo503()
@Test void infrastructureMapsTo500()
@Test void internalMapsTo500()
```

**File:** `src/test/java/datingapp/app/error/CliErrorMapperTest.java`
```java
@Test void validationWithFieldIncludesFieldName()
@Test void notFoundWithResourceTypeIncludesType()
@Test void conflictPrefixesWithCannotProceed()
```

**File:** `src/test/java/datingapp/app/error/ViewModelErrorMapperTest.java`
```java
@Test void infrastructureGivesGenericMessage()
@Test void validationPassesThroughMessage()
```

### Edge Cases & Gotchas

1. **Gradual conversion**: Do NOT convert ALL service results in one pass. Start with `RestApiServer` (highest impact — fixes the 409-for-everything bug). Then CLI handlers, then ViewModels. Each can be a separate commit.
2. **SendResult.ErrorCode preservation**: `SendResult` has specific `ErrorCode` values (NO_ACTIVE_MATCH, USER_NOT_FOUND, etc.) that map to different `AppError` variants. When converting at the use-case boundary, map: `USER_NOT_FOUND` → `NotFound`, `NO_ACTIVE_MATCH` → `Conflict`, `EMPTY_MESSAGE` → `Validation`, `MESSAGE_TOO_LONG` → `Validation`.
3. **Backward compatibility**: Existing `UseCaseResult<T>` consumers should continue to work. Use the bridge methods (`AppError.fromUseCaseError`, `toUseCaseError`) during the transition period. Do NOT break existing UseCaseResult callers.
4. **Exception handlers in RestApiServer**: The existing exception handlers (lines 405-432) for `IllegalArgumentException` → 400, etc. should remain as a safety net. The `ApiErrorMapper` handles the normal path; exception handlers catch bugs.
5. **Test the 409 → correct status change**: After modifying RestApiServer, write integration tests that verify a NOT_FOUND error returns 404 (not 409).

### Acceptance Criteria

- [ ] Three mapper classes created with exhaustive `switch` on all `AppError` variants
- [ ] `ApiErrorMapper` maps to correct HTTP status codes (400/403/404/409/500/503)
- [ ] `RestApiServer` uses `ApiErrorMapper` instead of hardcoded 409
- [ ] NOT_FOUND errors now return 404 (not 409)
- [ ] VALIDATION errors now return 400 (not 409)
- [ ] All mapper tests pass
- [ ] ALL existing tests pass (backward compatible via bridges)
- [ ] `mvn spotless:apply && mvn verify` succeeds

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.app.error.*"
mvn test  # full suite
mvn verify
```

### Rollback Notes

Delete mapper classes. Revert RestApiServer to hardcoded 409 mappings. Low-risk rollback.

---

## WU-14: Time Policy Rollout + Deprecated Path Removal

**Finding:** 15
**Dependencies:** WU-03 (TimePolicy), WU-05 (wiring — TimePolicy in ServiceRegistry)
**Parallel group:** D (can run with WU-09, WU-13)

### Current State

`TimePolicy` exists (WU-03) and is available via `services.getTimePolicy()` (WU-05). But feature code still uses:

**`ZoneId.systemDefault()` violations (to fix):**

| File | Line | Current Code |
|------|------|-------------|
| StatsHandler.java | 28 | `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())` |
| StatsHandler.java | 30 | `DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())` |
| MessagingHandler.java | 38 | `DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())` |
| ChatController.java | 244 | `msg.createdAt().atZone(ZoneId.systemDefault())` |
| SocialController.java | 131 | `notification.createdAt().atZone(ZoneId.systemDefault())` |
| User.java | 437 | `@Deprecated getAge()` calls `getAge(ZoneId.systemDefault())` |
| MatchPreferences.java | 630 | `@Deprecated passes(User, User)` calls with `ZoneId.systemDefault()` |
| MatchPreferences.java | 665 | `@Deprecated getFailedDealbreakers(...)` calls with `ZoneId.systemDefault()` |
| ProfileHandler.java | 54 | `DateTimeFormatter.ofPattern("yyyy-MM-dd")` (implicit system default) |

**`AppConfig.defaults()` timezone violations (to fix):**

| File | Line | Current Code |
|------|------|-------------|
| DashboardViewModel.java | 234 | `AppConfig.defaults().safety().userTimeZone()` |
| MatchesViewModel.java | 311 | `AppConfig.defaults().safety().userTimeZone()` |
| MatchesViewModel.java | 345 | `AppConfig.defaults().safety().userTimeZone()` |
| MatchingHandler.java | 267,351,424,676,821,1035 | `AppConfig.defaults().safety().userTimeZone()` |

**RecommendationService bug:**

| File | Line | Issue |
|------|------|-------|
| RecommendationService.java | 380-381 | `getStartOfToday()` uses `clock.getZone()` (UTC) instead of user timezone |
| RecommendationService.java | 384-386 | `getResetTime()` uses `clock.getZone()` (UTC) instead of user timezone |

### Target State

All feature code uses `TimePolicy` instead of direct `ZoneId.systemDefault()` or `AppConfig.defaults()`.

**Changes to CLI handlers (StatsHandler, MessagingHandler, ProfileHandler, MatchingHandler):**
```java
// These handlers are constructed via Dependencies.fromServices(services, session, inputReader)
// or similar factory methods.

// ADD TimePolicy as a field received from services:
//   private final TimePolicy timePolicy;
// Obtained from services.getTimePolicy()

// REPLACE static formatters with instance formatters:
// BEFORE: static final DateTimeFormatter DATE_TIME_FORMATTER = ...withZone(ZoneId.systemDefault())
// AFTER:  private final DateTimeFormatter dateTimeFormatter;
//         In constructor: this.dateTimeFormatter = timePolicy.withUserZone(
//             DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

// REPLACE AppConfig.defaults().safety().userTimeZone():
// BEFORE: AppConfig.defaults().safety().userTimeZone()
// AFTER:  timePolicy.userZone()
```

**Changes to UI controllers (ChatController, SocialController):**
```java
// Controllers receive TimePolicy via constructor injection
// (passed from ViewModelFactory → controller factory lambda)

// REPLACE: msg.createdAt().atZone(ZoneId.systemDefault())
// WITH:    msg.createdAt().atZone(timePolicy.userZone())
```

**Changes to ViewModels (DashboardViewModel, MatchesViewModel):**
```java
// ViewModels receive TimePolicy via constructor
// (passed from ViewModelFactory which gets it from ServiceRegistry)

// REPLACE: AppConfig.defaults().safety().userTimeZone()
// WITH:    timePolicy.userZone()
```

**Changes to RecommendationService (bug fix):**
```java
// In getStartOfToday() (line 380):
// REPLACE: LocalDate.now(clock)
// WITH:    LocalDate.now(clock.withZone(config.safety().userTimeZone()))
// OR inject TimePolicy and use: timePolicy.today()

// In getResetTime() (line 384):
// REPLACE: clock.getZone()
// WITH:    config.safety().userTimeZone()
```

**Remove deprecated methods (final cleanup):**
```java
// User.java line 436-437: Remove @Deprecated getAge()
// MatchPreferences.java lines 629-630, 664-665: Remove @Deprecated passes() and getFailedDealbreakers()
// Search for callers first — ensure no remaining callers of deprecated overloads
```

### File Manifest

| Action | File Path | What Changes |
|--------|-----------|-------------|
| MODIFY | `src/main/java/datingapp/app/cli/StatsHandler.java` | Replace static formatters with TimePolicy |
| MODIFY | `src/main/java/datingapp/app/cli/MessagingHandler.java` | Replace static formatter with TimePolicy |
| MODIFY | `src/main/java/datingapp/app/cli/ProfileHandler.java` | Add zone to date formatter via TimePolicy |
| MODIFY | `src/main/java/datingapp/app/cli/MatchingHandler.java` | Replace AppConfig.defaults() with TimePolicy |
| MODIFY | `src/main/java/datingapp/ui/screen/ChatController.java` | Replace ZoneId.systemDefault() with TimePolicy |
| MODIFY | `src/main/java/datingapp/ui/screen/SocialController.java` | Replace ZoneId.systemDefault() with TimePolicy |
| MODIFY | `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java` | Replace AppConfig.defaults() with TimePolicy |
| MODIFY | `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java` | Replace AppConfig.defaults() with TimePolicy |
| MODIFY | `src/main/java/datingapp/core/matching/RecommendationService.java` | Fix UTC bug in daily reset |
| MODIFY | `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java` | Pass TimePolicy to ViewModels and controllers |
| MODIFY | `src/main/java/datingapp/core/model/User.java` | Remove deprecated getAge() |
| MODIFY | `src/main/java/datingapp/core/profile/MatchPreferences.java` | Remove deprecated methods |

### Wiring Instructions

1. **CLI handlers**: Each handler's `Dependencies` record or `fromServices()` factory must include `TimePolicy`. Add `services.getTimePolicy()` in `Main.java` wiring and `fromServices()` methods.
2. **UI controllers**: `ViewModelFactory.buildControllerFactories()` passes TimePolicy when constructing controllers. Get from `services.getTimePolicy()`.
3. **ViewModels**: Add `TimePolicy` parameter to ViewModel constructors. `ViewModelFactory.getXxxViewModel()` passes `services.getTimePolicy()`.
4. **RecommendationService**: Already receives `config` in its builder. Use `config.safety().userTimeZone()` for zone. Or add TimePolicy as builder parameter.

### Test Specification

**Enable previously disabled tests:**
- `TimePolicyArchitectureTest.noFeatureCodeUsesZoneIdSystemDefault()` → REMOVE `@Disabled`, should now PASS
- `TimePolicyArchitectureTest.noFeatureCodeUsesAppConfigDefaults()` → REMOVE `@Disabled`, should now PASS

**Update existing tests:**
- `StatsHandlerTest` → pass TimePolicy in constructor
- `MessagingHandlerTest` → pass TimePolicy in constructor
- `SocialViewModelTest` → pass TimePolicy in constructor
- `MatchesViewModelTest` → pass TimePolicy in constructor
- `RecommendationServiceTest` → verify daily reset uses user timezone

**New test:**
```java
@Test void recommendationServiceResetsAtUserMidnight()
// Set user timezone to Asia/Jerusalem (+3)
// Set clock to 23:00 UTC (02:00 local = next day)
// Verify getStartOfToday() returns the LOCAL date, not UTC date
```

### Edge Cases & Gotchas

1. **Static formatters → instance formatters**: CLI handlers currently use `static final DateTimeFormatter`. Changing to instance fields means they're created per-handler-instance. This is correct — handlers are created once at startup.
2. **CLI handler construction chain**: Handlers are built in `Main.java` via `Dependencies.fromServices(...)`. You must update the `Dependencies` record to include `TimePolicy` and update `fromServices()` to extract it from `ServiceRegistry`.
3. **Controller injection path**: Controllers are created by `ViewModelFactory.buildControllerFactories()` lambdas. You must thread `TimePolicy` through these lambdas. The simplest approach: store `timePolicy` as a field in `ViewModelFactory` (extracted from `services.getTimePolicy()` in constructor).
4. **Deprecated method callers**: Before removing `User.getAge()` (no-arg), search for ALL callers. Any caller must be migrated to `getAge(ZoneId)` first. Same for `MatchPreferences.passes()` and `getFailedDealbreakers()`.
5. **RecommendationService UTC bug**: This is a real behavior change. Daily limits will reset at user-local midnight instead of UTC midnight. This may affect users near UTC midnight boundaries. Verify with timezone edge case tests.
6. **Multiple passes may be needed**: With ~12 files to modify, compile frequently. Fix compilation errors before running tests.

### Acceptance Criteria

- [ ] Zero `ZoneId.systemDefault()` calls in feature code (only in allowed files)
- [ ] Zero `AppConfig.defaults()` calls in feature code (only in allowed files)
- [ ] Architecture guardrail tests (WU-01) pass when `@Disabled` is removed
- [ ] RecommendationService daily reset uses user timezone (bug fixed)
- [ ] Deprecated `getAge()`, `passes()`, `getFailedDealbreakers()` removed
- [ ] ALL existing tests pass with TimePolicy injection
- [ ] `mvn spotless:apply && mvn verify` succeeds

### Verification Gate

```bash
mvn spotless:apply
mvn test -pl . -Dtest="datingapp.architecture.*"
# Expected: ALL architecture tests pass (including previously disabled ones)
mvn test
# Expected: ALL tests pass
mvn verify
# Expected: full quality gates pass
```

### Rollback Notes

Revert all 12 files to use `ZoneId.systemDefault()` and `AppConfig.defaults()`. Restore deprecated methods. Re-disable architecture tests. High-touch rollback — use git revert.

---

## 5. Cross-Cutting Concerns

### 5.1 Compilation Order

When implementing multiple WUs, compilation may temporarily break. Follow this safe order:

1. **Create new classes first** (WU-02, WU-03, WU-04, WU-06, WU-07) — these add files with no dependencies on each other.
2. **Wire foundations** (WU-05) — this modifies ServiceRegistry/StorageFactory and must compile with the new classes.
3. **Integrate** (WU-08, WU-09, WU-10, WU-13, WU-14) — these modify existing code and depend on the wiring.

### 5.2 Import Rules Reminder

```
core/           → NO framework imports (no javafx, jdbi, javalin, jackson)
app/usecase/    → imports core/, app/error/, app/event/
app/cli/        → imports app/usecase/, core/ (thin adapter)
app/api/        → imports app/usecase/, core/, app/error/ (thin adapter)
app/event/      → imports core/ (events reference core model types by ID, not by direct reference)
ui/viewmodel/   → imports app/usecase/, ui/async/, core/model/ (NOT core/storage/)
ui/screen/      → imports ui/viewmodel/, javafx (NOT core/ directly)
storage/        → imports core/storage/ interfaces, jdbi, H2
```

### 5.3 Spotless + PMD Compliance

- **Always run `mvn spotless:apply` before committing.** Palantir format is strict.
- **PMD GuardLogStatement**: Use `LoggingSupport` interface methods (`logInfo`, `logWarn`, `logDebug`).
- **PMD EmptyCatchBlock**: Use `assert true;` in intentionally empty catch blocks.
- **PMD suppression**: `// NOPMD RuleName` inline comments survive Spotless reformatting.

### 5.4 Test Isolation Checklist

Every test class should:
- [ ] Use `@BeforeEach`/`@AfterEach` for setup/teardown
- [ ] Call `TestClock.reset()` in `@AfterEach` if it set a fixed clock
- [ ] Call `AppSession.getInstance().reset()` in `@AfterEach` if it set a user
- [ ] Dispose ViewModels in `@AfterEach`
- [ ] Use fresh `TestStorages.*` instances (not shared across tests)

## 6. Risks and Mitigations

| # | Risk | Severity | Mitigation |
|---|------|----------|-----------|
| 1 | Behavior drift during centralization of transitions | High | Characterization tests before each refactor. Explicit transition matrix tests including invalid-state attempts. |
| 2 | Data migration regressions for multi-value normalization | High | Dual-read/dual-write period. Idempotent backfill with parity tests. |
| 3 | Event ordering/duplication issues in side-effect pipeline | Medium | Synchronous in-process dispatch first. Idempotent handlers. Explicit handler criticality policy. |
| 4 | Error model migration causes broad signature churn | Medium | Transitional bridge methods (fromUseCaseError/toUseCaseError). Incremental conversion by boundary. |
| 5 | Timezone changes introduce subtle UX regressions | Medium | Fixed-clock tests covering zone boundaries and date rollover. Architecture guardrail tests. |
| 6 | Constructor parameter explosion in ServiceRegistry | Low | Accept 18 params for now. Future: replace with feature facades (Decision 2 in retrospective). |
| 7 | Parallel agent work causes merge conflicts | Medium | Each WU creates new files (minimal overlap). WU-05 and WU-08 touch shared files — schedule sequentially after dependencies. |

## 7. Definition of Done (All Findings 11-15)

Done ONLY when ALL are true:

1. [ ] Workflow/activation rules centralized in `RelationshipWorkflowPolicy` + `ProfileActivationPolicy`, reused across all channels (WU-06, WU-07, WU-08).
2. [ ] Multi-value profile data persisted and queried via normalized tables, legacy parser fallback removed (WU-11, WU-12).
3. [ ] Cross-cutting side effects run through `AppEventBus` pipeline, imperative calls removed (WU-04, WU-09, WU-10).
4. [ ] `AppError` sealed hierarchy used at use-case boundaries with `ApiErrorMapper`, `CliErrorMapper`, `ViewModelErrorMapper` (WU-02, WU-13).
5. [ ] Timezone/time behavior unified via `TimePolicy`, no feature-level `ZoneId.systemDefault()` or `AppConfig.defaults()` leakage (WU-03, WU-14).
6. [ ] `mvn spotless:apply && mvn verify` succeeds with all quality gates.
7. [ ] Architecture guardrail tests (WU-01) all enabled and passing.
8. [ ] Retrospective doc status for Decisions 11-15 updated to implemented.

## 8. Suggested Agent Assignment Order

For maximum parallelism with minimum risk:

**Wave 1** (5 agents, no dependencies):
- Agent A → WU-01 (guardrail tests)
- Agent B → WU-02 (AppError)
- Agent C → WU-03 (TimePolicy)
- Agent D → WU-04 (EventBus)
- Agent E → WU-11 (Schema V3)

**Wave 2** (1 agent, after Wave 1):
- Agent F → WU-05 (wire foundations)

**Wave 3** (4 agents, after WU-05):
- Agent G → WU-06 + WU-07 (both policies, one agent since they share the package)
- Agent H → WU-09 (event handlers)
- Agent I → WU-13 (error mappers)
- Agent J → WU-14 (time rollout)

**Wave 4** (2 agents, after Wave 3):
- Agent K → WU-08 (policy integration, after WU-06/07)
- Agent L → WU-10 (event emission, after WU-09)

**Wave 5** (1 agent, after WU-11):
- Agent M → WU-12 (data migration)

**Total: 5 waves, up to 5 parallel agents per wave.**
