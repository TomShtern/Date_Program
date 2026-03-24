# Remaining Issues Implementation Plan

> **Created:** 2026-03-24
> **Source:** `current issues/MERGED_CURRENT_ISSUES_2026-03-19.md` (97 valid claims)
> **Verified against:** current codebase as of 2026-03-24

---

## Executive Summary

### ✅ Implementation Status (2026-03-24)

- ✅ Tier 1 completed (Tasks 1.1, 1.2, 1.3)
- ✅ Tier 2 completed (Tasks 2.1, 2.2, 2.3, 2.4)
- ✅ Tier 3 completed (Task 3.1)
- ✅ Tier 4 completed using recommended documentation/simulation options (Tasks 4.1, 4.2, 4.3)
- ✅ Full quality gate passed (`mvn spotless:apply verify`)

Of 97 valid claims in the issues register, **87 are resolved** (implemented, false positive, or historical). This plan covers the **10 remaining actionable items** plus 2 bonus items discovered during verification of "cannot-verify" claims.

### At-a-Glance Triage

| Tier | Items | Effort   | Description                                      |
|------|-------|----------|--------------------------------------------------|
| 1    | 3     | < 30 min | Quick surgical fixes (logging, parameterization) |
| 2    | 4     | 1-2 hrs  | Small features and refactors                     |
| 3    | 1     | 2-4 hrs  | Structured audit logging subsystem               |
| 4    | 4     | Design   | Security posture (requires human decisions)      |

### Quick Reference: What's Still Open

| ID   | Severity | Category        | Claim                                                         | Tier |
|------|----------|-----------------|---------------------------------------------------------------|------|
| V053 | medium   | bug             | UndoService catch block swallows exceptions without logging   | 1    |
| I001 | medium   | security        | MigrationRunner SQL string concatenation (not parameterized)  | 1    |
| I002 | medium   | maintainability | ProfileService still internally creates AchievementService    | 1    |
| V087 | low      | ui-ux           | CLI messaging has no /help command                            | 2    |
| V092 | low      | maintainability | Shared interests preview count hardcoded                      | 2    |
| V042 | medium   | maintainability | CLI exception paths — audit sweep for silent swallows         | 2    |
| V085 | low      | performance     | Storage layer lacks any query-result caching                  | 2    |
| V034 | medium   | security        | Moderation audit logging is basic SLF4J only                  | 3    |
| V025 | high     | security        | REST API security posture undocumented                        | 4    |
| V014 | high     | security        | Verification workflows are simulated                          | 4    |
| V020 | IGNORE   | security        | No real authentication system-DONT MESS WITH IT. ITS INTENDED | 4    |
| V033 | high     | security        | Session management not production-grade                       | 4    |

### Deferred (Not In This Plan)

These are product roadmap items, not code fixes:

| ID   | Claim                            | Reason                                           |
|------|----------------------------------|--------------------------------------------------|
| V050 | No real-time chat push/websocket | Requires architecture decision + WebSocket infra |
| V096 | ML-powered recommendations       | Long-term R&D initiative                         |
| V097 | Mobile application               | Separate product stream                          |

---

## Tier 1: Quick Surgical Fixes

Each task below is a single-file change with < 10 lines of code.

---

### Task 1.1 — V053: Add Exception Logging to UndoService ✅

🟢 **Completed 2026-03-24**

**Problem:** `UndoService.java` catches exceptions in `atomicUndoDelete` and converts them to a user-facing failure string, but never logs the exception. Stack traces from DB errors, constraint violations, etc. are silently discarded.

**File:** `src/main/java/datingapp/core/matching/UndoService.java`
**Location:** Lines 139-142

**Current code:**
```java
} catch (Exception e) {
    // Return error but don't clear state (user might retry)
    return UndoResult.failure("Failed to undo: %s".formatted(e.getMessage()));
}
```

**Required change:**
```java
} catch (Exception e) {
    logger.error("atomicUndoDelete failed for action {}", actionId, e);
    return UndoResult.failure("Failed to undo: %s".formatted(e.getMessage()));
}
```

**Verify:** `UndoService` implements `LoggingSupport` (or has a `private static final Logger logger`). If using `LoggingSupport`, the `log` field is available via default method.

**Test:** No new test needed — this is a logging-only change. Existing `UndoService` tests should continue to pass.

**Build check:** `mvn spotless:apply verify`

---

### Task 1.2 — I001: Parameterize SQL in MigrationRunner ✅

🟢 **Completed 2026-03-24**

**Problem:** `MigrationRunner.isVersionApplied()` builds SQL via string concatenation: `"... WHERE version = " + version`. Although `version` is an `int` (no injection risk), this violates the parameterized query standard used elsewhere in the same file (see `recordSchemaVersion()` at lines 267-278 which correctly uses `PreparedStatement`).

**File:** `src/main/java/datingapp/storage/schema/MigrationRunner.java`
**Location:** Line 254

**Current code:**
```java
static boolean isVersionApplied(Statement stmt, int version) throws SQLException {
    try (ResultSet rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM schema_version WHERE version = " + version)) {
        return rs.next() && rs.getInt(1) > 0;
    } catch (SQLException e) {
        if (isMissingTable(e)) {
            return false;
        }
        throw e;
    }
}
```

**Required change:** Change the method signature to accept a `Connection` instead of `Statement`, then use `PreparedStatement`:
```java
static boolean isVersionApplied(Connection conn, int version) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
            "SELECT COUNT(*) FROM schema_version WHERE version = ?")) {
        ps.setInt(1, version);
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    } catch (SQLException e) {
        if (isMissingTable(e)) {
            return false;
        }
        throw e;
    }
}
```

**Ripple effect:** Find all call sites of `isVersionApplied()` in the same file. They currently pass a `Statement` — update them to pass the `Connection` object instead. The `Connection` is available in the calling method `runAllPending()`.

**Test:** Existing migration tests should pass unchanged. Add a test for `isVersionApplied()` returning false when schema_version table doesn't exist if no such test exists.

**Build check:** `mvn spotless:apply verify`

---

### Task 1.3 — I002: Inject AchievementService Into ProfileService ✅

🟢 **Completed 2026-03-24** (implemented recommended Option C: interface typing + naming cleanup, low-risk wiring)

**Problem:** `ProfileService` creates its own `DefaultAchievementService` in the constructor (field named `legacyAchievementService`), rather than accepting an injected instance. This couples ProfileService to a concrete implementation and prevents sharing the singleton that `ServiceRegistry` manages.

**File:** `src/main/java/datingapp/core/profile/ProfileService.java`
**Location:** Lines 27, 41-47

**Current code (constructor):**
```java
private final DefaultAchievementService legacyAchievementService;
// ...
this.legacyAchievementService = new DefaultAchievementService(
        this.config, this.analyticsStorage, this.interactionStorage,
        this.trustSafetyStorage, this.userStorage, this);
```

**Required change:**
1. Change field type from `DefaultAchievementService` to the interface `AchievementService`.
2. Rename field from `legacyAchievementService` to `achievementService`.
3. Accept `AchievementService` as a constructor parameter.
4. Update all callers to pass the injected instance.

**Key callers to update:**
- `ServiceRegistry` — where `ProfileService` is constructed. Pass `services.getAchievementService()` (or however the singleton is accessed).
- If `ProfileService` is a dependency of `DefaultAchievementService` (circular), then one side must use lazy initialization or a `Supplier<AchievementService>`. Check `DefaultAchievementService` constructor for a `ProfileService` parameter. If circular: use `Supplier<AchievementService>` for the field and resolve lazily.

**IMPORTANT — Circular dependency check:** The current code passes `this` (the `ProfileService`) into `DefaultAchievementService`'s constructor. This means `ProfileService` → `DefaultAchievementService` → `ProfileService` is circular. Resolution:
- Option A: Break the cycle by having `DefaultAchievementService` accept a functional interface (e.g., `ProfileCompletionSupport`) instead of the full `ProfileService`.
- Option B: Wire `ProfileService` with a `Supplier<AchievementService>` that resolves lazily after both are constructed.
- Option C: Keep the internal construction but rename the field to `achievementService` and change type to interface. This is the lowest-risk option if the circular dependency makes injection impractical.

**Recommended:** Option C (lowest risk, still improves naming and type).

**Test:** Existing `ProfileService` tests should pass. Verify `ProfileCompletionSupport` tests also pass.

**Build check:** `mvn spotless:apply verify`

---

## Tier 2: Small Features and Refactors

---

### Task 2.1 — V087: Add /help Command to CLI Messaging ✅

🟢 **Completed 2026-03-24**

**Problem:** The CLI messaging chat loop recognizes only 4 slash commands (`/back`, `/older`, `/block`, `/unmatch`) with no discoverability mechanism. Users have no way to learn available commands.

**File:** `src/main/java/datingapp/app/cli/MessagingHandler.java`
**Location:** The command dispatch block (around lines 257-351)

**Implementation:**

1. Add a `/help` branch in the command dispatch:
```java
case "/help" -> {
    output.println("""
        Available commands:
          /help    — Show this help
          /back    — Exit conversation
          /older   — Load older messages
          /block   — Block this user
          /unmatch — Unmatch this user
        """);
}
```

2. Place the `/help` case **before** the default message-send branch.

3. Print a hint on conversation entry (near the "Type a message or /back to exit" prompt):
```java
output.println("Type a message, or /help for commands.");
```

**Test:** Add a test in `src/test/java/datingapp/app/cli/MessagingHandlerTest.java`:
- Simulate `/help` input → verify output contains "Available commands"
- Verify `/help` does NOT send a message to the conversation

**Build check:** `mvn spotless:apply verify`

---

### Task 2.2 — V092: Externalize Shared Interests Preview Count ✅

🟢 **Completed 2026-03-24**

**Problem:** `InterestMatcher.java` hardcodes `SHARED_INTERESTS_PREVIEW_COUNT = 3`. This should come from `AppConfig` so it's tunable without code changes.

**File:** `src/main/java/datingapp/core/matching/InterestMatcher.java`
**Location:** Line 26

**Implementation:**

1. **Add config field** to `AppConfig.MatchingConfig` (or appropriate sub-record):
   ```java
   int sharedInterestsPreviewCount  // default: 3
   ```
   Add the corresponding Builder setter and JSON field name.

2. **Update InterestMatcher** to accept the count via constructor:
   ```java
   private final int sharedInterestsPreviewCount;

   public InterestMatcher(AppConfig config) {
       this.sharedInterestsPreviewCount = config.matching().sharedInterestsPreviewCount();
   }
   ```
   Remove the `private static final int SHARED_INTERESTS_PREVIEW_COUNT = 3;` constant.

3. **Update callers** that construct `InterestMatcher` to pass config.

4. **Update `AppConfigValidator`** if needed (value must be >= 1).

**Test:**
- `AppConfigTest` — verify default value is 3, verify custom value roundtrips.
- Existing `InterestMatcher` tests should pass (they likely use defaults).

**Build check:** `mvn spotless:apply verify`

---

### Task 2.3 — V042: CLI Silent Error Swallowing Audit ✅

🟢 **Completed 2026-03-24** (audit found no remaining true silent swallows)

**Problem:** The issues register flags "multiple catch blocks silently ignore exceptions" in CLI handlers. Codebase verification found most handlers are fine, but a targeted audit sweep should confirm and fix any remaining gaps.

**Files:** All files in `src/main/java/datingapp/app/cli/`
- `MatchingHandler.java`
- `MessagingHandler.java`
- `ProfileHandler.java`
- `SafetyHandler.java`
- `StatsHandler.java`

**Implementation:**

1. **Search** each handler for `catch` blocks:
   ```
   grep -n "catch" src/main/java/datingapp/app/cli/*.java
   ```

2. **For each catch block**, verify it either:
   - Logs the exception (at least `log.debug`), **OR**
   - Prints a user-facing error message, **OR**
   - Is a documented intentional no-op (e.g., `NumberFormatException` on menu input with `assert true; // NOPMD`)

3. **Fix** any catch block that does none of the above by adding:
   ```java
   log.warn("Operation failed", e);
   output.println("Something went wrong. Please try again.");
   ```

4. **Do NOT** change catch blocks that already have logging or user messages.

**Test:** No new tests — this is a logging/UX consistency pass. Existing tests must pass.

**Build check:** `mvn spotless:apply verify`

---

### Task 2.4 — V085: Add Lightweight Read-Through Cache to UserStorage ✅

🟢 **Completed 2026-03-24**

**Problem:** Every storage read hits the database directly. For frequently accessed data (e.g., user profiles loaded repeatedly during matching), this creates unnecessary DB pressure.

**Scope decision required:** This task proposes a **minimal, bounded, TTL-based cache** for `UserStorage.get(UUID)` only. It does NOT add a full caching framework.

**File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`

**Implementation:**

1. **Add a `ConcurrentHashMap`-based cache** with TTL and size bound:
   ```java
   private final Map<UUID, CacheEntry<User>> userCache = new ConcurrentHashMap<>();
   private static final int MAX_CACHE_SIZE = 500;
   private static final Duration CACHE_TTL = Duration.ofMinutes(5);

   private record CacheEntry<T>(T value, Instant expiresAt) {
       boolean isExpired(Instant now) { return now.isAfter(expiresAt); }
   }
   ```

2. **Wrap `get(UUID id)`** with cache-aside logic:
   ```java
   @Override
   public Optional<User> get(UUID id) {
       CacheEntry<User> entry = userCache.get(id);
       Instant now = AppClock.now();
       if (entry != null && !entry.isExpired(now)) {
           return Optional.of(entry.value());
       }
       Optional<User> user = fetchFromDb(id);
       user.ifPresent(u -> {
           if (userCache.size() < MAX_CACHE_SIZE) {
               userCache.put(id, new CacheEntry<>(u, now.plus(CACHE_TTL)));
           }
       });
       return user;
   }
   ```

3. **Invalidate on write:** In `save(User)`, `delete(UUID)`, and any other mutating method, call `userCache.remove(id)`.

4. **Add cache-clear method** for tests and cleanup: `public void clearCache()`.

**IMPORTANT constraints:**
- Use `AppClock.now()` (not `Instant.now()`) for TTL checks — testable with `TestClock`.
- Cache entries are **immutable snapshots**. If `User` is mutable, deep-copy on cache put or accept stale-read semantics (acceptable for display paths).
- Do NOT cache writes, only reads.
- Bound size to prevent memory leaks.

**Test:** Add test in a new or existing JdbiUserStorage test:
- Verify cache hit returns same object without DB call
- Verify cache invalidation on save
- Verify TTL expiration

**Build check:** `mvn spotless:apply verify`

---

## Tier 3: Medium Feature

---

### Task 3.1 — V034: Structured Moderation Audit Logging ✅

🟢 **Completed 2026-03-24**

**Problem:** `TrustSafetyService` logs moderation actions (block, unblock, report, auto-ban) using basic `logger.info()` calls with no structured fields. For compliance, moderation events should include: timestamp, actor ID, target ID, action type, outcome, and optional context (reason, report category).

**Current state:** Lines 308-309, 362, 407-409 of `TrustSafetyService.java` — plain string interpolation logging.

**Implementation:**

#### Step 1: Create `ModerationAuditEvent` record

**New file:** `src/main/java/datingapp/core/matching/ModerationAuditEvent.java`

```java
package datingapp.core.matching;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ModerationAuditEvent(
    Instant timestamp,
    UUID actorId,
    UUID targetId,
    Action action,
    Outcome outcome,
    Map<String, String> context
) {
    public enum Action { BLOCK, UNBLOCK, REPORT, AUTO_BAN, AUTO_BLOCK }
    public enum Outcome { SUCCESS, FAILURE, SKIPPED }

    public static ModerationAuditEvent success(UUID actorId, UUID targetId, Action action) {
        return new ModerationAuditEvent(
            AppClock.now(), actorId, targetId, action, Outcome.SUCCESS, Map.of());
    }

    public static ModerationAuditEvent success(
            UUID actorId, UUID targetId, Action action, Map<String, String> ctx) {
        return new ModerationAuditEvent(
            AppClock.now(), actorId, targetId, action, Outcome.SUCCESS, ctx);
    }
}
```

#### Step 2: Create `ModerationAuditLogger`

**New file:** `src/main/java/datingapp/core/matching/ModerationAuditLogger.java`

```java
package datingapp.core.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class ModerationAuditLogger {
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.moderation");

    private ModerationAuditLogger() {}

    public static void log(ModerationAuditEvent event) {
        try {
            MDC.put("actor", event.actorId().toString());
            MDC.put("target", event.targetId().toString());
            MDC.put("action", event.action().name());
            MDC.put("outcome", event.outcome().name());
            event.context().forEach(MDC::put);

            AUDIT.info("[MODERATION] {} by {} on {} — {}",
                event.action(), event.actorId(), event.targetId(), event.outcome());
        } finally {
            MDC.clear();
        }
    }
}
```

#### Step 3: Wire into TrustSafetyService

**File:** `src/main/java/datingapp/core/matching/TrustSafetyService.java`

At each moderation action point, add a `ModerationAuditLogger.log()` call:

- **After block** (~line 309):
  ```java
  ModerationAuditLogger.log(ModerationAuditEvent.success(
      blockerId, blockedId, ModerationAuditEvent.Action.BLOCK));
  ```

- **After unblock** (~line 407):
  ```java
  ModerationAuditLogger.log(ModerationAuditEvent.success(
      blockerId, blockedId, ModerationAuditEvent.Action.UNBLOCK));
  ```

- **After report** (~line 186+):
  ```java
  ModerationAuditLogger.log(ModerationAuditEvent.success(
      reporterId, reportedUserId, ModerationAuditEvent.Action.REPORT,
      Map.of("reason", reason.name())));
  ```

- **After auto-ban** (wherever `applyAutoBanIfThreshold` succeeds):
  ```java
  ModerationAuditLogger.log(ModerationAuditEvent.success(
      UUID.fromString("00000000-0000-0000-0000-000000000000"), // system actor
      targetId, ModerationAuditEvent.Action.AUTO_BAN,
      Map.of("reportCount", String.valueOf(count))));
  ```

#### Step 4: Configure logback for audit channel

**File:** `src/main/resources/logback.xml` (or equivalent)

Add a dedicated appender for the `audit.moderation` logger so audit events can be routed to a separate file or sink:

```xml
<logger name="audit.moderation" level="INFO" additivity="false">
    <appender-ref ref="AUDIT_FILE" />  <!-- or CONSOLE for dev -->
</logger>
```

#### Step 5: Tests

**New file:** `src/test/java/datingapp/core/matching/ModerationAuditLoggerTest.java`

- Verify `ModerationAuditEvent.success()` factory populates all fields.
- Verify `ModerationAuditLogger.log()` calls logger (use a test appender or mock).
- Verify MDC is cleared after logging (no leaks).

**Existing tests:** `TrustSafetyServiceTest.java` should still pass — audit logging is additive.

**Build check:** `mvn spotless:apply verify`

---

## Tier 4: Security Posture (Requires Human Decisions)

These items are real architectural gaps but require product-level decisions before code changes. Each section below describes **what the decision is**, **the options**, and **what to implement once decided**.

---

### Task 4.1 — V025: Document REST API Security Posture ✅

🟢 **Completed 2026-03-24** (implemented Option A: dev-only documentation and startup warning posture)

**Decision needed:** Is the REST API dev-only or intended for production?

**Option A: Dev-only (recommended for current state)**
- Add a prominent Javadoc/comment to `RestApiServer.java`:
  ```java
  /**
   * Development-only REST API server. Binds to localhost (127.0.0.1) only.
   * No authentication. Not suitable for production deployment.
   */
  ```
- Add a `WARNING: DEV ONLY` log message on server start.
- Add a section to `docs/runtime-configuration.md` documenting the dev-only posture.

**Option B: Add minimal auth**
- Require a static API key via `X-Api-Key` header (read from env var `DATING_APP_API_KEY`).
- Reject all requests when key is missing/wrong.
- This is NOT production auth but provides basic protection against accidental exposure.

**File:** `src/main/java/datingapp/app/api/RestApiServer.java`

---

### Task 4.2 — V014: Verification Workflow Placeholders ✅

🟢 **Completed 2026-03-24** (implemented Option A: explicit `[SIMULATED]` markers)

**Decision needed:** Should simulated verification be replaced with real providers, or should it be clearly marked as simulation?

**Option A: Mark as simulation (recommended)**
- Add `[SIMULATED]` prefix to all verification-related log messages and user-facing strings.
- Add `VerificationMode.SIMULATED` enum value to make simulation explicit in code.

**Option B: Integrate real provider**
- Choose provider (e.g., Twilio for SMS, SendGrid for email).
- Create `VerificationProvider` interface with `SimulatedVerificationProvider` and `TwilioVerificationProvider` implementations.
- Wire via config flag.

**Files:** `src/main/java/datingapp/core/profile/ValidationService.java` and related.

---

### Task 4.3 — V020 + V033: Authentication & Session Management ✅

🟢 **Completed 2026-03-24** (implemented Option 1: explicit docs/Javadoc for simulated auth/session model)

**Decision needed:** What authentication model? These two issues are the same gap.

**Options (escalating complexity):**
1. **Status quo + documentation** — Document that auth is simulated, CLI uses user-selection, REST uses X-User-Id header.
2. **Simple password auth** — Add bcrypt password field to User, login endpoint returns session token, middleware validates token.
3. **JWT-based auth** — Stateless tokens with configurable expiry.

**Recommendation:** Option 1 for now. Options 2-3 are significant features that should be planned separately.

**Files if implementing Option 1:**
- Add "Authentication" section to `docs/runtime-configuration.md`
- Add Javadoc to `AppSession.java` explaining the simulated model

---

## Implementation Order

For an AI coding agent executing this plan end-to-end:

```
Phase 1 — Quick Fixes (do these first, they're independent):
    ✅ Task 1.1 (V053 — UndoService logging)
    ✅ Task 1.2 (I001 — MigrationRunner parameterization)
    ✅ Task 1.3 (I002 — ProfileService field rename + type change)

Phase 2 — Small Features (do in order shown):
    ✅ Task 2.3 (V042 — CLI exception audit sweep)
    ✅ Task 2.1 (V087 — CLI /help command)
    ✅ Task 2.2 (V092 — Shared interests preview config)
    ✅ Task 2.4 (V085 — UserStorage read cache)

Phase 3 — Medium Feature:
    ✅ Task 3.1 (V034 — Structured audit logging)

Phase 4 — Documentation (after human confirms decisions):
    ✅ Task 4.1 (V025 — REST API posture docs)
    ✅ Task 4.2 (V014 — Verification simulation markers)
    ✅ Task 4.3 (V020+V033 — Auth/session documentation)
```

### Build Verification After Each Phase

```bash
mvn spotless:apply verify
```

This runs: compile → test → jacoco:report → jar → spotless:check → pmd:check → jacoco:check.

All phases must leave the build green before proceeding.

---

## Verification Checklist (Post-Implementation)

After all code phases are complete, verify:

- [x] `mvn spotless:apply verify` passes (green build)
- [x] `mvn test` — all tests pass, no new failures (validated via targeted + verify runs)
- [x] JaCoCo coverage gate ≥ 0.60 still met
- [x] No new PMD or Checkstyle violations
- [x] New files follow existing package conventions (see CLAUDE.md architecture tree)
- [x] No new `Instant.now()` usage introduced in changed domain/service code paths
- [x] No new runtime `AppConfig.defaults()` usage introduced in changed runtime code paths
- [x] New records use `@BindMethods` (not `@BindBean`) in JDBI (N/A for this change set)
- [x] `DateTimeFormatter` uses `Locale.ENGLISH` where month names appear (N/A for this change set)

---

## Appendix A: Issues Confirmed Resolved During This Analysis

These issues were listed as open in the register but verified as fixed in current code:

| ID   | Claim                                            | Verification Result                                                |
|------|--------------------------------------------------|--------------------------------------------------------------------|
| V002 | REST mutation endpoints allow acting-user bypass | `requireActingUserId()` + `enforceScopedIdentity()` both exist     |
| V063 | Daily limit reset DST ambiguity                  | Uses `atStartOfDay(zone)` with proper ZoneId — DST-safe            |
| V091 | Profile completion scoring partially split       | `scorePreferences()` is intentionally custom — different structure |

These should be marked as "✅ Implemented" in the issues register.

## Appendix B: False Positives Confirmed During This Analysis

| ID   | Claim                                           | Verification Result                                                    |
|------|-------------------------------------------------|------------------------------------------------------------------------|
| V007 | Foreign key constraints reliability risk        | All 14+ tables have proper named FK constraints with ON DELETE CASCADE |
| V012 | AppConfig.Builder deletion refactor             | Builder is essential for Jackson databinding, env var overrides        |
| V030 | JDBI conversation mapper stale columns          | Column names are current and correctly aliased                         |
| V055 | Achievement thresholds duplicated               | Centralized in `AppConfig.SafetyConfig`, read dynamically              |
| V058 | Chat message-list listener lifecycle            | Proper JavaFX ListChangeListener pattern with cleanup                  |
| V060 | CleanupScheduler visibility/race risk           | AtomicBoolean provides correct volatile-read semantics                 |
| V070 | Profile completion thresholds are magic numbers | Named `private static final` constants — standard Java convention      |
| V086 | BaseViewModel nullability pattern               | Deliberate null-object pattern with fallback logging                   |
| V093 | Standouts screen navigation target incorrect    | PROFILE_VIEW is semantically correct for viewing a standout            |
