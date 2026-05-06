# Backend Codebase Audit Report

**Date:** 2026-05-06
**Scope:** `src/main/java/datingapp/` excluding `ui/` (JavaFX frontend), excluding documentation
**Layers audited:** `core/` (domain, `~15,400` lines), `app/` (usecases/API/CLI, `~8,600` lines), `storage/` (JDBI/schema, `~6,000` lines)
**Files analyzed:** `~130` Java source files

---

## CRITICAL — Correctness & Security Bugs

### C1 — `JdbiAuthStorage` hardcodes PostgreSQL `ON CONFLICT`, breaks H2

`storage/jdbi/JdbiAuthStorage.java:18-24`

Every other storage class (`JdbiUserStorage`, `JdbiMatchmakingStorage`, `JdbiMetricsStorage`) uses `SqlDialectSupport.upsertSql()` to generate dialect-appropriate upserts (H2 `MERGE INTO` vs PostgreSQL `ON CONFLICT`). `JdbiAuthStorage` alone hardcodes PostgreSQL syntax and does not accept a `DatabaseDialect` parameter:

```java
// HARDCODED PostgreSQL — H2 requires MERGE INTO
private static final String UPSERT_PASSWORD_HASH_SQL = """
    INSERT INTO user_credentials (...)
    ON CONFLICT (user_id) DO UPDATE SET ... = EXCLUDED....
    """;
```

**Fix:** Make `JdbiAuthStorage` dialect-aware like every other storage class.

---

### C2 — Race condition in `JdbiTrustSafetyStorage.save(Block)` / `save(Report)`

`storage/jdbi/JdbiTrustSafetyStorage.java:59-68, 141-150`

The class uses `jdbi.onDemand()` interface proxy with `default` methods that make **multiple DAO calls on separate connections** from the pool. Two threads can both pass the "does block exist?" check before either inserts, causing duplicate blocks (or `UNIQUE` constraint violation):

```java
default void save(Block block) {
    if (reviveDeletedBlock(block) > 0) { return; }     // Call 1 — connection A
    if (activeBlockExists(block.blockerId(), ...)) { return; } // Call 2 — connection B
    insertBlockRow(block);                              // Call 3 — connection C
    // NO TRANSACTION spans these three calls
}
```

**Fix:** Convert to concrete class with `jdbi.inTransaction()` wrapping, or use `Handle`-based interface.

---

### C3 — `AppClock` static mutable state leaks between tests

`core/AppClock.java:11`

```java
private static volatile Clock clock = Clock.systemUTC();
```

29 test files call `AppClock.setFixed()` / `setClock()`. Not all reset in `@AfterEach`. Tests that forget to call `AppClock.reset()` corrupt subsequent tests with wrong timestamps.

**Fix:** Add `AppClock.assertReset()` check in a base test class, or convert `AppClock` to an injectable, non-static service.

---

### C4 — `UserStorage.findByEmail()` scans all users in memory on every login/signup

`core/storage/UserStorage.java:169-176`

```java
default Optional<User> findByEmail(String normalizedEmail) {
    return findAll().stream()
            .filter(user -> normalizedEmail.equals(user.getEmail()))
            .findFirst();
}
```

This default method loads **every user** from the database into memory and filters in Java. Called on every login (`AuthUseCases.java:65`) and every signup (`AuthUseCases.java:47`). With thousands of users, this becomes catastrophically slow and uses excessive memory — no database index is leveraged.

**Fix:** Override in `JdbiUserStorage` with a direct SQL query: `SELECT * FROM users WHERE email = :email AND deleted_at IS NULL`.

---

## HIGH — Functional & Structural Problems

### H1 — `RestApiServer.java` is 1,792 lines (largest file in the project)

`app/api/RestApiServer.java`

Contains `~45` route handler methods, transport security, CORS, rate-limiting, photo storage, and `main()`. Must be split into per-domain controller classes (e.g., `AuthController`, `UserController`, `MatchController`, `MessageController`) with shared infrastructure extracted to middleware components.

---

### H2 — Duplicate Haversine formula

`core/profile/LocationService.java:338-349` vs `core/matching/CandidateFinder.java:136-149`

Same Haversine formula (Earth radius `6371.0` km), identical math, implemented in two places. `LocationService` should delegate to `CandidateFinder.GeoUtils.distanceKm()`.

---

### H3 — Duplicate `copyMatch()` in two services

`core/connection/ConnectionService.java:603-615` vs `core/matching/TrustSafetyService.java:417-429`

Both construct a new `Match` from all 10 parameters of an existing one — identical code. Add a `Match.copy()` factory method and use it in both services.

---

### H4 — Match state machine duplicated in two places

`core/model/Match.java:224-234` vs `core/workflow/RelationshipWorkflowPolicy.java:53-56`

`Match.isInvalidTransition()` and `RelationshipWorkflowPolicy.ALLOWED_TRANSITIONS` encode the same transition rules independently. If one is updated without the other, behavior diverges. `Match.isInvalidTransition()` should delegate to `RelationshipWorkflowPolicy`.

---

### H5 — Layering violation: `User.java` (model) depends on `ValidationService` (profile)

`core/model/User.java:584-591, 896-897`

```java
this.email = ValidationService.normalizeEmail(email);   // model -> profile dependency
this.phone = ValidationService.normalizePhone(phone);
return ValidationService.normalizePhotoUrl(trimmed);
```

Domain model directly imports and invokes a service class. Move the static normalizers (`normalizeEmail`, `normalizePhone`, `normalizePhotoUrl`) to a shared utility in `core/model/` or `core/`.

---

### H6 — `saveProfile()` and `savePhotoUrls()` are 50-line near-duplicates

`app/usecase/profile/ProfileMutationUseCases.java:113-161` and `:163-207`

Both methods follow the identical post-save flow: activate user → save to storage → log → unlock achievements → publish `ProfileSaved` + `ProfileCompleted` events. They differ only in the starting user state and one log message. Extract a shared `executeProfileSave(User, String logMessage)` method.

---

### H7 — No spatial index on `(lat, lon)` for candidate queries

`storage/schema/SchemaInitializer.java` (users table DDL), `storage/jdbi/JdbiUserStorage.java:150-176`

Candidate browsing filters by lat/lon bounding box, but both columns are unindexed `DOUBLE PRECISION`. At scale, this causes full table scans.

**Fix:** Add: `CREATE INDEX idx_users_location_state ON users(lat, lon, state) WHERE state = 'ACTIVE' AND deleted_at IS NULL`

---

### H8 — `save(Match)` and `save(Like)` are non-transactional

`storage/jdbi/JdbiMatchmakingStorage.java:221-224, 415-417`

```java
public void save(Match match) {
    jdbi.useHandle(handle -> saveMatch(handle, match));  // NOT useTransaction!
}
```

### H9 — Auth signup email normalization diverges from profile email normalization

`app/usecase/auth/AuthUseCases.java:176-181` vs `core/profile/ValidationService.java:305-336`

`AuthUseCases` normalizes email with only: `email.trim().toLowerCase(Locale.ROOT)`. `ValidationService` performs Unicode NFKC normalization, length validation, `@`-sign validation, IDN domain encoding, and domain format validation. An email accepted at signup (e.g., one with Unicode domain characters or exceeding 254 chars after NFKC normalization) will later fail when `User.setEmail()` calls `ValidationService.normalizeEmail()` via profile flows. This creates a **data consistency gap**.

**Fix:** Have `AuthUseCases` delegate to `ValidationService.normalizeEmail()`.

---

### H10 — `User.copy()` redundantly re-normalizes already-normalized photo URLs

`core/model/User.java:938-968`

`copy()` uses `StorageBuilder.photoUrls(photoUrls)` which calls `normalizePhotoUrls()`. Since the photo URLs are already normalized (they came from a valid `User`), this performs redundant normalization on every copy. More importantly, if normalization is lossy (it strips some URL components), repeated copies could progressively degrade valid URLs.

**Fix:** Add a `rawPhotoUrls()` setter to `StorageBuilder` that skips normalization for already-validated data.

---

### H11 — `TrustSafetyService.block()` save-and-transition not in single transaction, rollback can fail

`core/matching/TrustSafetyService.java:478-558`

The `block()` method:
1. Saves the `Block` record via `trustSafetyStorage.save(block)` — separate connection
2. Applies match/conversation transition via `interactionStorage.blockTransition(...)` — separate connection
3. If step 2 fails, rolls back via `trustSafetyStorage.deleteBlock(...)` — separate connection
4. If the deleteBlock rollback ALSO fails (logged as warning at line 469), the system is left with a persisted block but no match/conversation transition — inconsistent state.

There is no distributed transaction spanning both operations.

**Fix:** Wrap both operations in a single storage-layer transaction if supported, or use a saga pattern with compensation logging.

## MEDIUM — Design Debt & Inconsistency

### M1 — `LocationUpdated` event published but never subscribed

`app/usecase/profile/ProfileMutationUseCases.java:279`

`ProfileMutationUseCases.updateProfile()` publishes `AppEvent.LocationUpdated` — but `AchievementEventHandler`, `MetricsEventHandler`, and `NotificationEventHandler` have zero subscribers for this event type.

**Fix:** Wire a subscriber or remove the event from the sealed hierarchy.

---

### M2 — `DailyLimitReset` event: defined, never published, never subscribed

`app/event/AppEvent.java:64`

Completely dead event in the sealed hierarchy. Remove it.

---

### M3 — `SocialUseCases` COMPATIBILITY_NO_OP event bus silently drops all events

`app/usecase/social/SocialUseCases.java:35-51`

The `forTrustSafetyOnly()` and `forWorkflowAccess()` factory methods inject a `COMPATIBILITY_NO_OP_EVENT_BUS` that silently discards all events. Blocking/reporting/unmatching through the safety path produces no metrics, achievements, or notifications.

**Fix:** Accept a real `AppEventBus` parameter or at least log dropped events.

---

### M4 — `AuthUseCases` throws raw exceptions; all other use cases return `UseCaseResult<T>`

`app/usecase/auth/AuthUseCases.java:43-117`

`signup()`, `login()`, `refresh()`, `logout()`, `requireAuthenticatedUser()` throw `DuplicateAccountException`, `UnauthorizedException`, or `IllegalArgumentException` directly. Every other use case class returns the typed `UseCaseResult<T>` envelope. This forces the REST server to use exception handlers for auth failures while using `handleUseCaseFailure()` for everything else.

**Fix:** Convert `AuthUseCases` to return `UseCaseResult<T>`.

---

### M5 — Lat/lon validation duplicated across 4 locations

| Location                                      | Method                                       |
|-----------------------------------------------|----------------------------------------------|
| `core/model/LocationModels.java:85-94`        | `validateLatitude()` / `validateLongitude()` |
| `core/profile/GeocodingService.java:18-22`    | `GeocodingResult` compact constructor        |
| `core/model/User.java:629-652`                | `User.setLocation()`                         |
| `core/profile/ValidationService.java:272-287` | `validateLocation()`                         |

Same bounds check (`[-90,90]` / `[-180,180]`) and finiteness check in four places. Consolidate to `GeoUtils` or a new `GeoValidation` utility.

---

### M6 — `MatchingUseCases` NO_OP wrappers are unreachable through Builder path

`app/usecase/matching/MatchingUseCases.java:41-105`

`NO_OP_DAILY_LIMIT_SERVICE` and `NO_OP_DAILY_PICK_SERVICE` exist as default fallbacks returned when `wrapDailyLimitService(null)` / `wrapDailyPickService(null)` is called. The `Builder.build()` path always auto-seeds real implementations, making NO_OPs unreachable through that path. However, the public static `wrap*` methods remain accessible to external callers. Consider whether the null-safety contract of these wrap methods is still needed, or remove them if not.

---

### M7 — `listConversationsWithMessageCounts()` has zero callers

`app/usecase/messaging/MessagingUseCases.java:63-79, 299`

Only `listConversations()` is used by REST API and CLI. The message-count variant is dead code. Remove or add a route.

---

### M8 — `UserStorage` (storage interface) imports from matching package

`core/storage/UserStorage.java:114, 119`

```java
import datingapp.core.matching.CandidateFinder.GeoUtils;
```

A core storage interface has a compile-time dependency on the matching package for distance calculation. Move `GeoUtils` to `core/model/` or `core/geo/`.

---

### M9 — `AppConfigValidator` (core) imports from storage layer

`core/AppConfigValidator.java:7`

```java
import datingapp.storage.DatabaseDialect;
```

Core config validation depends on a storage-layer enum. The dependency arrow is backwards. Move `DatabaseDialect` to `core/` or inline the validation.

---

### M10 — `CURRENT_TIMESTAMP` hardcoded in 5 SQL queries

`storage/jdbi/JdbiMatchmakingStorage.java:92, 734, 744, 969, 1044`

Five places hardcode `CURRENT_TIMESTAMP` in SQL instead of using bind parameters with `AppClock.now()`. This loses application control over the exact timestamp value and makes time-related testing harder.

---

### M11 — Notification save logic duplicated between two storage classes

`storage/jdbi/JdbiConnectionStorage.java:690` vs `storage/jdbi/JdbiMatchmakingStorage.java:707-718`

Both `SocialDao.saveNotification()` and `JdbiMatchmakingStorage.saveNotification()` implement the same insert with JSON serialization. The matchmaking version also uses raw `handle.execute()` with positional parameters — the only place in the codebase doing raw JDBC-style parameter binding. Extract shared notification save logic.

---

### M12 — `PacePreferencesDto` defined in two different DTO files

`app/api/RestApiUserDtos.java:24` vs `app/api/RestApiDtos.java:295`

Two records with the same name but different semantics: the `RestApiUserDtos` version is read-only (no `toPacePreferences()` method), the `RestApiDtos` version converts to domain model. Either make them one type with both methods or give them distinct names (`ReadPacePreferencesDto` / `WritePacePreferencesDto`).

---

### M13 — `RestApiDtos.java` is 755 lines

`app/api/RestApiDtos.java`

Contains 37 distinct record types covering auth, location, matches, messages, notifications, verification, photos, stats, achievements, profile notes, reports, and dealbreakers in one file. Split into domain-specific DTO files.

---

### M14 — `ProfileHandler.java` is 1,237 lines

`app/cli/ProfileHandler.java`

Handles profile completion, preview, dealbreakers, notes CRUD, user creation, user selection, and profile scoring — five distinct responsibilities in one CLI class. Split by concern.

---

### M15 — `MatchingUseCases.Builder` has 11 required dependencies

`app/usecase/matching/MatchingUseCases.java:148-263`

The `recommendationService()` compatibility hook auto-seeds `dailyLimitService`, `dailyPickService`, and `standoutService` if unset, creating non-obvious wiring that hides configuration problems. Simplify or add explicit validation.

---

### M16 — `ApplicationStartup` uses static mutable state

`app/bootstrap/ApplicationStartup.java:86-93`

### M17 — `RestApiServer.handleUseCaseFailure()` throws for NOT_FOUND instead of setting context

`app/api/RestApiServer.java:1647-1671`

```java
case NOT_FOUND -> throw new NotFoundResponse(error.message());  // THROWS
case VALIDATION -> { ctx.status(400); ctx.json(...); }          // SETS directly
```

`NOT_FOUND` is the only case that throws an exception instead of setting status/json inline. This relies on Javalin's global exception handler catching `NotFoundResponse`, which is inconsistent with every other case that sets ctx directly.

**Fix:** Replace with `ctx.status(404).json(new ErrorResponse("NOT_FOUND", error.message()))`.

---

### M18 — All `IllegalStateException` mapped to HTTP 409 Conflict indiscriminately

`app/api/RestApiServer.java:1717-1720`

```java
app.exception(IllegalStateException.class, (e, ctx) -> {
    ctx.status(409);  // All IllegalStateException -> 409 Conflict
});
```

Many `IllegalStateException`s in the codebase represent genuine 500-level errors (e.g., `"Cannot activate a banned user"`, `"Cannot unmatch from BLOCKED state"`), not client-conflict errors. Every `IllegalStateException` is mapped to 409 regardless of its actual meaning.

**Fix:** Use more specific exception types, or inspect the exception to choose the correct status code.

---

### M19 — Conversations with deleted users silently dropped from conversation list

`core/connection/ConnectionService.java:215-221`

```java
User otherUser = otherUsers.get(otherUserId);
if (otherUser == null) {
    continue;  // SILENTLY SKIPS — no log, no indication
}
```

When a conversation partner has been soft-deleted, the conversation silently disappears from the user's list with no explanation.

**Fix:** Log at debug level at minimum. Optionally show deleted-user conversations with a placeholder name.

---

### M20 — Min/max age silently swapped in profile preference application

`app/usecase/profile/ProfileMutationUseCases.java:409-413`

```java
if (minAge > maxAge) {
    int swap = minAge;
    minAge = maxAge;
    maxAge = swap;  // Silently swaps — caller never knows
}
```

If a caller passes `minAge=40, maxAge=25`, the method silently swaps them to 25/40. This masks bugs in callers and could produce unexpected behavior.

**Fix:** Return a validation error instead of silently swapping.

---

### M21 — `MatchingService` inflight-dedup `ConcurrentHashMap`: add defensive time-based eviction

`core/matching/MatchingService.java:35, 252-263`

```java
swipeInFlight.putIfAbsent(inFlightKey, IN_FLIGHT_SENTINEL);
try {
    // ... process swipe ...
} finally {
    swipeInFlight.remove(inFlightKey);  // correctly cleans up on normal exceptions
}
```

The `try/finally` correctly handles all normal exception paths. However, JVM-fatal errors that bypass `finally` (e.g., `OutOfMemoryError`) could leave stale entries. In edge cases like thread interruption during the `putIfAbsent`-to-`try` window, a slow leak is theoretically possible over very long uptimes.

**Fix:** Add time-based eviction (e.g., entries older than 5 minutes auto-expire) as defense-in-depth, or expose a `cleanupStaleInflightEntries()` method for occasional housekeeping.

---

## LOW — Nice-to-Have Improvements

| #   | Issue                                                                                                                                                                          | Location                                                                           | Fix                                                                     |
|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| L1  | 6 interface+single-impl over-abstractions: `CompatibilityCalculator`, `BrowseRankingService`, `DailyLimitService`, `DailyPickService`, `StandoutService`, `AchievementService` | `core/matching/`                                                                   | Convert to concrete classes unless multiple implementations are planned |
| L2  | `AppConfig.Builder` has 80+ flat setter methods on 980 lines                                                                                                                   | `core/AppConfig.java`                                                              | Consider `with*()` pattern or config-file loading                       |
| L3  | `Achievement` enum leaks UI strings: `iconLiteral` = "mdi2h-heart-multiple"                                                                                                    | `core/metrics/EngagementDomain.java:21-35`                                         | Map achievement → icon in the UI layer, not the domain layer            |
| L4  | `SwipeState.Session.MatchState` collides with `Match.MatchState`                                                                                                               | `core/metrics/SwipeState.java:31`                                                  | Rename to `SessionState`                                                |
| L5  | `ProfileNote.create()` duplicates compact constructor validation                                                                                                               | `core/model/ProfileNote.java:52-59`                                                | Let the record constructor validate; remove `create()` pre-checks       |
| L6  | `generateId()` pattern: `Match` hardcodes `"_"`, `Conversation` uses `CONVERSATION_ID_SEPARATOR`                                                                               | `core/model/Match.java:124-140`, `core/connection/ConnectionModels.java:180-192`   | Extract shared `generatePairId(UUID, UUID, String)`                     |
| L7  | Empty no-op methods: `validateMatchingBehaviorFlags()` (empty) and `invalidateCacheFor()`/`clearCache()` (no-ops called by 3 files)                                            | `core/AppConfigValidator.java:62-64`, `core/matching/CandidateFinder.java:263-269` | Remove or implement                                                     |
| L8  | 8 public normalized-profile methods on `JdbiUserStorage` are dead code (production uses batch path)                                                                            | `storage/jdbi/JdbiUserStorage.java:304-335`                                        | Remove or mark `@VisibleForTesting`                                     |
| L9  | 19-parameter `build()` shim in `DevDataSeeder` — never called                                                                                                                  | `storage/DevDataSeeder.java:1188-1224`                                             | Remove                                                                  |
| L10 | `UserDtoMapper` is a 34-line thin wrapper                                                                                                                                      | `app/api/UserDtoMapper.java`                                                       | Merge into `UserPresentationSupport` or `RestApiUserDtos`               |
| L11 | `AuthTokenService` interface with single `JwtAuthTokenService` impl                                                                                                            | `app/usecase/auth/AuthTokenService.java`                                           | Remove interface if no alternative planned                              |
| L12 | `ProfileUseCases` is a 200-line facade with single-line delegations                                                                                                            | `app/usecase/profile/ProfileUseCases.java`                                         | Inline or simplify                                                      |
| L13 | `TestClock` underused — only 1 test file, rest call `AppClock` directly                                                                                                        | `src/test/java/datingapp/core/testutil/TestClock.java`                             | Deprecate or promote                                                    |
| L14 | `profile_views` PK includes `viewed_at` — ambiguous intent                                                                                                                     | `storage/schema/SchemaInitializer.java:601`                                        | Clarify if "track every view" (OK) or "latest view only" (PK wrong)     |
| L15 | `RestApiServer.getCandidates()` is a deprecated alias for `browseCandidates()`                                                                                                 | `app/api/RestApiServer.java:751-764`                                               | Remove deprecated alias                                                 |
| L16 | `sentLikes()` use case has no REST API route (UI-only)                                                                                                                         | `app/usecase/matching/MatchingUseCases.java:417-437`                               | Add route or document as UI-only                                        |
| L17 | Signup creates users with empty name via `StorageBuilder` bypass                                                                                                               | `app/usecase/auth/AuthUseCases.java:52-53`                                         | Require name on signup or set default placeholder                       |
| L18 | `ProfileNote.withContent()` duplicates constructor validation + silent clock-skew                                                                                              | `core/model/ProfileNote.java:72-82`                                                | Remove duplicate validation; log warning on clock-skew                  |
| L19 | `TrustSafetyService.contextOf()` silently drops null audit values                                                                                                              | `core/matching/TrustSafetyService.java:638-639`                                    | Log warning when null context values encountered                        |
| L20 | `Like` timestamp captured before persistence transaction boundary                                                                                                              | `core/matching/MatchingService.java:299`                                           | Move `Like.create()` inside transactional boundary                      |
| L21 | Stale shutdown hook survives re-initialization cycles                                                                                                                          | `app/bootstrap/ApplicationStartup.java:420-437`                                    | Remove old hook on shutdown or update on re-init                        |

---

## Summary

| Priority     | Count  | Categories                                                                                                                                                                                                                                     |
|--------------|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Critical** | 4      | 1 H2 incompatibility, 1 race condition, 1 test-leak, 1 full-table-scan on login                                                                                                                                                                |
| **High**     | 11     | 3 duplications, 1 state machine duplication, 2 layering violations, 1 missing index, 1 giant class, 1 DB transaction issue, 1 near-duplicate, 1 email normalization divergence, 1 redundant photo normalization, 1 transactional inconsistency |
| **Medium**   | 21     | 5 dead code, 6 inconsistency, 2 layering violations, 2 large files, 2 SQL quality, 1 silent event dropping, 1 complexity, 1 thrown-vs-set, 1 status code misuse, 1 silent conversation drop, 1 silent age swap, 1 zombie map entries           |
| **Low**      | 21     | 6 over-abstraction, 7 dead code, 2 naming, 3 misc, 1 empty-name bug, 1 redundant validation, 1 silent null drop, 1 timestamp misplacement, 1 stale hook                                                                                        |
| **Total**    | **57** |                                                                                                                                                                                                                                                |

> Review note: the original severity buckets are preserved above for provenance, but the bucket math and the fix-order labels do not fully reconcile. The calibration below is the actioning view after re-checking the codebase. I did not remove any original finding.

### Review Calibration

Suggested review-severity counts: **High 6**, **Medium 6**, **Low 44**, **None 1**. In this calibrated view, no item remains Critical.

| IDs                                                          | Original labels         | Review verdict                              | Suggested severity | Observation                                                                                                                                                                                                |
|--------------------------------------------------------------|-------------------------|---------------------------------------------|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| C1, C2, C4, H7, H9, H11                                      | Critical / High         | confirmed issue                             | High               | Real defects with current impact. C1 is H2 SQL compatibility, C2 and H11 are transaction-integrity races, C4 is auth-path scanning, H7 is location-query performance, and H9 is email-normalization drift. |
| C3, M1, M2, M7, M17, L21                                     | Critical / Medium / Low | real but narrow                             | Medium             | Valid issues, but they are mostly test hygiene, dead code, or lifecycle cleanup rather than user-facing defects.                                                                                           |
| H1-H6, H8, H10, M3-M6, M8-M16, M18-M20, M21, L1-L14, L16-L20 | High / Medium / Low     | design, cleanup, intentional, or overstated | Low                | Mostly maintainability, compatibility shims, deliberate normalization, or duplicated helpers. Treat these as backlog items, not defects.                                                                   |
| L15                                                          | Low                     | incorrect / false positive                  | None               | `getCandidates()` is a compatibility route, not a deprecated alias.                                                                                                                                        |

### Recommended Fix Order

1. **C4** — `findByEmail()` full table scan (performance/correctness under load)
2. **C1** — H2/PG auth SQL (correctness)
3. **C2** — block/report race condition (data integrity)
4. **C3** — AppClock test leaking (test reliability)
5. **H2, H3, H4** — duplicated code (maintenance hazard)
6. **H5, H8, M9** — layering violations (architectural hygiene)
7. **H9** — email normalization divergence (data consistency)
8. **H8** — non-transactional saves (data consistency)
9. **H11** — block() transactional inconsistency (data integrity)
10. **H7** — missing spatial index (performance)
11. **H1** — split RestApiServer (scalability)
12. **H10** — redundant photo URL normalization (correctness on copy)
13. **M1, M2, M6, M7** — dead code removal (hygiene)
14. **M3, M4** — consistency (event dropping, error handling)
15. **M5, M10-M12** — remaining duplication/quality
16. **M17-M21** — medium-level bugs and inconsistencies
17. **H6** — saveProfile/savePhotoUrls dedup
18. **L1-L21** — nice-to-haves
