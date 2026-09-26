# 2026-03-29 Source-Code-Only Review Report

> Scope: `src/main/java` and `src/test/java` only. I intentionally did **not** rely on repository documentation for findings.
>
> Coverage: all Java source areas were reviewed (`app`, `core`, `storage`, `ui`, `architecture` tests, and package-level tests). I used parallel read-only reviews per domain, then manually re-checked the highest-impact findings against the source before writing this report.

## Review method

- Read the codebase by source area, not by documentation.
- Used parallel read-only package reviews to cover the full tree without dropping context.
- Manually verified the most important findings in the source before including them here.
- Dropped several first-pass suspicions when the source did not fully support them. This report is intentionally stricter than a raw lint dump.

## Source inventory

Based on source-file inventory from this review session:

- `src/main/java`: **145** Java files
- `src/test/java`: **167** Java files
- Total Java files reviewed: **312**

Largest implementation hotspots by line count from the source tree (re-verified in the second pass):

- `src/main/java/datingapp/ui/screen/ProfileController.java` — **1588** lines
- `src/main/java/datingapp/app/api/RestApiServer.java` — **1467** lines
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java` — **1418** lines
- `src/main/java/datingapp/storage/DevDataSeeder.java` — **1288** lines
- `src/main/java/datingapp/app/cli/ProfileHandler.java` — **1246** lines

Those files are not automatically wrong, but they are the clearest complexity hotspots in the codebase.

## Executive summary

The codebase has a solid amount of explicit domain modeling, a meaningful test suite, and several good shared abstractions (`BaseController`, `BaseViewModel`, `ViewModelAsyncScope`, `UseCaseResult`, `ServiceRegistry`). The main issues are not “the app is chaotic”; they are more specific:

1. **Weak dependency contracts** in some use-case classes let misconfiguration survive construction and fail later at runtime.
2. **Silent degradation / silent no-op behavior** appears in a few important places, making failures harder to observe.
3. **Validation is inconsistent** across similar subsystems, especially in configuration and copy paths.
4. **Some modules are far too large**, concentrating unrelated responsibilities into classes that are harder to test and simplify.
5. **The UI layer has lifecycle-pattern drift**: some view models use the shared base abstractions, others manage async state manually.
6. **The test suite is broad but increasingly expensive to maintain**, because it duplicates service wiring, uses reflection heavily, and relies on timing-based stabilization in a few places.

## Highest-priority findings

### 1. Weak constructor contracts allow late runtime failure instead of early configuration failure

**Where**

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:64-94`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java:43-58`
- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java:568-645`

**What I found**

- `ProfileUseCases` exposes overloaded public constructors that accept nullable collaborators and only later checks for required dependencies inside methods like `saveProfile(...)`.
- `SocialUseCases` exposes several partially configured constructor shapes, including one that accepts only `TrustSafetyService`.
- `MatchingUseCases.wrapDailyLimitService(...)`, `wrapDailyPickService(...)`, and `wrapStandoutService(...)` explicitly convert a null `RecommendationService` into permissive or empty no-op wrappers.

**Why it matters**

This pushes configuration errors from construction time into runtime behavior:

- some methods fail late with dependency errors,
- some behaviors silently degrade,
- callers do not get a strong compile-time or startup-time guarantee that the use-case object is actually valid.

**Simpler direction**

- Prefer one validated construction path per use-case class.
- Keep builders if useful, but make `build()` fail fast on missing required dependencies.
- Remove or sharply limit permissive overloads and null-to-no-op wrappers.

### 2. `ActivityMetricsService.recordMatch()` silently discards work when no session exists

**Where**

- `src/main/java/datingapp/core/metrics/ActivityMetricsService.java:160-170`
- Diagnostic counter at `src/main/java/datingapp/core/metrics/ActivityMetricsService.java:42,185-190`

**What I found**

`recordMatch(UUID userId)` updates the current session if one exists. If not, it does not create a session, does not return a failure, does not log, and only increments `recordMatchNoOpCount`.

**Why it matters**

This is a classic observability trap: the method name says “record match”, but in one branch it quietly does not. If metrics drift shows up later, the failure is hidden behind a diagnostic snapshot that most call sites will never read.

**Simpler direction**

- Return a result/boolean.
- Or emit a warning/event when a match is dropped.
- Or explicitly create a session if that is the intended fallback.

### 3. Standout weight validation is weaker than match-quality validation

**Where**

- Match-quality weight sum check: `src/main/java/datingapp/core/AppConfigValidator.java:44-44`
- Standout weight validation: `src/main/java/datingapp/core/AppConfigValidator.java:175-181`

**What I found**

The validator enforces that match-quality weights sum to roughly `1.0`, but standout weights are only checked for being non-negative.

**Why it matters**

Even if the runtime logic can technically cope with arbitrary values, the validation rules are inconsistent for two conceptually similar weighting systems. That makes the configuration surface harder to reason about and easier to misconfigure.

**Simpler direction**

- Use one shared validation strategy for weighted scoring systems.
- Either enforce a meaningful total for standout weights too, or explicitly document/encode that they are intentionally unnormalized.

### 4. `User.copy()` manually rebuilds `Dealbreakers` instead of reusing the safer copy path already in the model

**Where**

- `src/main/java/datingapp/core/model/User.java:911-949`
- Existing safer copy helper: `src/main/java/datingapp/core/profile/MatchPreferences.java:493-504`

**What I found**

`User.copy()` manually reconstructs `Dealbreakers` field by field, even though `Dealbreakers` already exposes `toBuilder()`.

**Why it matters**

This is a field-drift hazard. If `Dealbreakers` gains another field later, `User.copy()` can silently stop copying it unless somebody remembers to update the manual reconstruction code.

**Simpler direction**

- Replace the manual reconstruction with `dealbreakers.toBuilder().build()`.

### 5. Candidate distance logic mixes permissive filtering with worst-distance ranking

**Where**

- `src/main/java/datingapp/core/matching/CandidateFinder.java:380-391`
- `src/main/java/datingapp/core/matching/CandidateFinder.java:394-399`

**What I found**

- `isWithinDistance(...)` returns `true` if either user has no location.
- `distanceTo(...)` returns `Double.MAX_VALUE` for the same case.
- The candidate list is then sorted by `distanceTo(...)`.

**Why it matters**

The behavior is internally inconsistent:

- missing candidate location is treated as acceptable during filtering,
- but as maximally far away during ranking.

That may be intentional, but the policy is implicit and hard to reason about. It is the kind of logic that tends to regress because different contributors read it differently.

**Simpler direction**

- Make “location unknown” a first-class ranking/filtering decision.
- Either reject unknown-location candidates, or keep them with an explicit ranking bucket, rather than mixing `true` and `Double.MAX_VALUE` semantics.

### 6. Hardcoded location data has no self-validation at initialization time

**Where**

- `src/main/java/datingapp/core/profile/LocationService.java:46-61`
- `src/main/java/datingapp/core/profile/LocationService.java:63-85`

**What I found**

`LocationService` embeds country, city, and ZIP data directly in static lists, but there is no startup-time validation block checking for duplicate entries, malformed coordinates, or overlapping/contradictory ZIP definitions.

**Why it matters**

With hardcoded datasets, typos become product behavior. This is especially risky when one class is the canonical location engine for multiple surfaces.

**Simpler direction**

- Add a static self-check for invariants.
- Keep the dataset in code if desired, but make invalid data fail fast.

### 7. Location-service wiring is duplicated instead of consistently injected

**Where**

- `src/main/java/datingapp/core/ServiceRegistry.java:107`
- `src/main/java/datingapp/app/cli/ProfileHandler.java:91-106`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:137-201`

**What I found**

The codebase has a shared `LocationService`, but it is still constructed in multiple places:

- once in `ServiceRegistry`,
- in a convenience constructor of `ProfileHandler`,
- repeatedly in `ProfileViewModel` overloads via `new LocationService(new ValidationService(config))`.

**Why it matters**

This does not currently break behavior because `LocationService` is largely stateless, but it weakens the architecture. It becomes easier for surfaces to diverge if service configuration or behavior changes later.

**Simpler direction**

- Standardize on injected `LocationService` everywhere.
- Keep convenience constructors only for tests if truly necessary.

### 8. `JdbiUserStorage` is correct-ish but stringly and chatty in ways that will age badly

**Where**

- Dynamic normalized-table access: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:504-519,697-704`
- Bulk profile hydration: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:521-598`

**What I found**

- Dynamic table names are concatenated into SQL after validation.
- `applyNormalizedProfileDataBatch(...)` loads normalized profile fields through a long sequence of separate queries.

**Why it matters**

`validateNormalizedTable(...)` makes the current code safe, so this is **not** an immediate injection bug. The problem is maintainability:

- dealbreaker dimensions are represented as strings and validated manually,
- hydration logic is repetitive,
- the batch loader grows query-by-query as more normalized fields are added.

**Simpler direction**

- Replace string table names with an enum or a dedicated abstraction.
- Consolidate normalized-field loading into a more structured mapper/loader.

### 9. The UI layer uses two different lifecycle styles for view models

**Where**

- Shared base pattern: `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java:14-87`
- Non-base view models: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java:57-214`, `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:59-224`
- Additional non-base examples: `PreferencesViewModel`, `MatchesViewModel`, `SocialViewModel`, `StatsViewModel`, `StandoutsViewModel`

**What I found**

Some view models extend `BaseViewModel`, others manually manage `ViewModelAsyncScope`, loading flags, disposal, and error-routing patterns.

**Why it matters**

The codebase already has a shared lifecycle abstraction, but only part of the UI layer uses it. That increases mental load and makes future cleanup changes harder to apply consistently.

**Simpler direction**

- Migrate the non-base view models to `BaseViewModel`, or
- formally define a second supported pattern and keep it minimal.

### 10. `TrustSafetyService` uses message-sniffing on exceptions to decide business meaning

**Where**

- `src/main/java/datingapp/core/matching/TrustSafetyService.java:407-418`

**What I found**

Duplicate-report detection walks the exception chain and checks whether the message contains `UNIQUE`, `DUPLICATE`, or `CONSTRAINT`.

**Why it matters**

This couples business behavior to storage-driver text. It is fragile, database-specific, and easy to regress if the underlying storage implementation changes its exception wording.

**Simpler direction**

- Return a typed duplicate-report signal from the storage boundary, or
- convert storage exceptions into domain-specific exceptions once, near the storage layer.

## Structural hotspots and over-complex areas

These are not single-line bugs. They are places where the code is doing too much in one class.

### Monolithic adapters and controllers

- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/cli/ProfileHandler.java`
- `src/main/java/datingapp/ui/screen/ProfileController.java`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`

**Observation**

These files mix multiple responsibilities: parsing, validation, orchestration, state mutation, UI binding, and formatting. They are readable only in slices, not as coherent units.

**Simplification direction**

- Split `RestApiServer` by route group and request/response mapping.
- Split `ProfileHandler` into focused profile-editing flows.
- Split `ProfileController`/`ProfileViewModel` by feature area: basics, photos, discovery preferences, pace preferences, location, notes.

### Seed / fixture code too large for one class

- `src/main/java/datingapp/storage/DevDataSeeder.java`

**Observation**

The file is big enough that it becomes part seed-data definition, part scenario authoring, part persistence orchestration.

**Simplification direction**

- Extract reusable builders or scenario packs.
- Keep the entrypoint small and the sample-data recipes composable.

## Package-by-package notes

### `app/`

**Good**

- `Main.runWithShutdown(...)` gives the CLI a clear shutdown guard.
- The app/use-case layer generally prefers result objects over flow-control exceptions.

**Concerns**

- Use-case construction contracts are too permissive.
- `RestApiServer` is doing too much in one place.
- CLI handlers are feature-rich but too large, especially around profile flows.

### `core/`

**Good**

- Domain types and enums are explicit.
- Validation and time handling are separated enough to stay testable.

**Concerns**

- Validation strictness is uneven across related subsystems.
- Some copy/clone logic duplicates model knowledge instead of reusing model-level copy paths.
- Matching logic contains a few policy seams that are implicit rather than explicit.

### `storage/`

**Good**

- The storage layer clearly separates interfaces from JDBI implementations.
- Transaction wrappers and DAO patterns are generally consistent.

**Concerns**

- Normalized profile loading is repetitive and query-heavy.
- Some string-based table selection remains brittle even with validation.

### `ui/`

**Good**

- `BaseController.cleanup()` is a good central lifecycle mechanism.
- `ViewModelAsyncScope` is a real abstraction, not fake architecture.

**Concerns**

- Adoption of shared lifecycle patterns is inconsistent.
- A few big view/controller classes are carrying too much responsibility.
- Location-service construction is duplicated in the UI layer instead of consistently injected.

### `src/test/java`

**Good**

- Coverage breadth is real; the suite clearly tries to exercise end-to-end flows, architecture rules, storage contracts, UI behavior, and async behavior.

**Concerns**

- Too much duplicated service wiring across tests.
- Too much reflection against private internals.
- A few tests are stabilized by repetition or timeouts instead of stronger synchronization.

## Test-suite findings

### 1. REST/API test wiring is heavily duplicated

**Representative examples**

- `src/test/java/datingapp/app/api/RestApiRateLimitTest.java:103-170`
- `src/test/java/datingapp/app/api/RestApiDailyLimitTest.java:232-308`
- Similar `createServices(...)` blocks also appear in `RestApiHealthRoutesTest`, `RestApiNotesRoutesTest`, `RestApiConversationBatchCountTest`, `RestApiRelationshipRoutesTest`, `RestApiReadRoutesTest`, `RestApiPhaseTwoRoutesTest`, and `MatchingFlowIntegrationTest`.

**Issue**

The same service graph is rebuilt in many files. Any constructor change or service-wiring adjustment becomes a cross-suite edit.

**Simpler direction**

- Extract a shared `TestServiceRegistryFactory` or `RestApiTestFixture`.

### 2. Tests depend on private implementation details via reflection

**Representative examples**

- `src/test/java/datingapp/app/cli/ProfileHandlerTest.java:122,264,281`
- `src/test/java/datingapp/app/cli/MatchingHandlerTest.java:181`
- `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java:850,860`

**Issue**

Reflection-based private access makes tests compile against structure instead of behavior.

**Simpler direction**

- Prefer public/package-private seams,
- or shift assertions toward observable behavior instead of internal method/field access.

### 3. Some async/UI tests are stabilized by repetition and long waits

**Representative examples**

- Known flaky test: `src/test/java/datingapp/ui/screen/ChatControllerTest.java:110-177`
- Repeated stability test: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java:215-252`
- Multiple `waitUntil(..., 5000/8000)` patterns in those same files

**Issue**

The suite is carrying timing debt. Repeated tests and large timeouts are sometimes necessary, but they are a smell when they become a stabilization strategy.

**Simpler direction**

- Prefer latches/signals/fakes over elapsed-time waiting.
- Reserve repetition for true stress tests, not for ordinary behavioral tests.

### 4. `AppClockTest` uses a fragile real-time tolerance assertion

**Where**

- `src/test/java/datingapp/core/AppClockTest.java:31-39`

**Issue**

The test asserts that `AppClock.now()` lands within a moving tolerance window around three separate live clock reads. It probably passes most of the time, but it is still more timing-sensitive than it needs to be.

**Simpler direction**

- Test the fixed-clock behavior rigorously.
- Keep “live clock” assertions minimal and tolerant, or drop them if they do not protect a meaningful contract.

## Simplification opportunities with low behavior risk

These changes should simplify the codebase without materially changing product behavior.

1. **Unify use-case construction**
   - Make builders the canonical path.
   - Fail fast on missing required dependencies.

2. **Make location wiring truly shared**
   - Inject `LocationService` everywhere instead of recreating it in UI/CLI convenience constructors.

3. **Reuse model-level copy helpers**
   - Replace manual `Dealbreakers` rebuilding in `User.copy()`.

4. **Split hotspot files by responsibility**
   - `RestApiServer`, `ProfileHandler`, `ProfileController`, `ProfileViewModel`, `DevDataSeeder`.

5. **Replace stringly normalized-table access with typed abstractions**
   - An enum or typed mapper would be clearer than raw table-name strings.

6. **Standardize view-model lifecycle management**
   - Prefer `BaseViewModel` unless there is a compelling reason not to.

7. **Consolidate test fixture construction**
   - A few focused factories would remove a large amount of duplicate test code.

## Suggested remediation order

1. **Tighten dependency contracts**
   - `ProfileUseCases`, `SocialUseCases`, `MatchingUseCases`.

2. **Fix silent / weakly observable behavior**
   - `ActivityMetricsService.recordMatch()`
   - `TrustSafetyService` duplicate detection boundary

3. **Tighten model/config consistency**
   - Standout weight validation
   - `User.copy()` / `Dealbreakers`
   - `LocationService` dataset self-validation

4. **Pay down architecture drift**
   - shared location-service injection
   - shared UI view-model lifecycle pattern

5. **Attack the big files**
   - split by responsibility, not by arbitrary layers

6. **Refactor the test harness**
   - shared service-registry factory
   - fewer reflection-based tests
   - fewer timeout/repetition-based async tests

## Bottom line

This is not a weak codebase. It is a codebase with **good building blocks** that has accumulated **contract looseness, duplicated wiring, and oversized files** as it grew.

If I had to summarize the core problem in one sentence, it would be this:

> The project often has the right abstraction already, but it does not use that abstraction consistently enough across all layers.

The biggest payoff will come from making construction stricter, lifecycle patterns more uniform, and the biggest files smaller and more single-purpose.

## Second-pass verification of the original report

I re-checked every major claim from the first report directly against the source. The result:

- No major claim had to be removed.
- The hotspot line counts were corrected from approximate values to exact second-pass counts.
- Several broad claims now have stricter evidence anchors below.

| Original claim                                                                        | Status                     | Evidence                                                                                                                                                                                                                       |
|---------------------------------------------------------------------------------------|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Weak constructor/dependency contracts in app use cases                                | Verified                   | `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java:64-94`, `src/main/java/datingapp/app/usecase/social/SocialUseCases.java:43-58`, `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java:568-645` |
| `ActivityMetricsService.recordMatch()` silently drops work when no session exists     | Verified                   | `src/main/java/datingapp/core/metrics/ActivityMetricsService.java:160-170`, `185-190`                                                                                                                                          |
| Standout weight validation is weaker than match-quality validation                    | Verified                   | `src/main/java/datingapp/core/AppConfigValidator.java:44`, `175-181`                                                                                                                                                           |
| `User.copy()` manually rebuilds `Dealbreakers` despite an existing copy path          | Verified                   | `src/main/java/datingapp/core/model/User.java:911-949`, `src/main/java/datingapp/core/profile/MatchPreferences.java:493-504`                                                                                                   |
| Candidate location handling mixes permissive filtering with worst-distance ranking    | Verified                   | `src/main/java/datingapp/core/matching/CandidateFinder.java:380-399`                                                                                                                                                           |
| `LocationService` hardcoded datasets have no self-validation block                    | Verified                   | `src/main/java/datingapp/core/profile/LocationService.java:46-85`                                                                                                                                                              |
| `LocationService` construction is duplicated instead of consistently injected         | Verified                   | `src/main/java/datingapp/core/ServiceRegistry.java:107`, `src/main/java/datingapp/app/cli/ProfileHandler.java:91-106`, `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:137-201`                                    |
| `JdbiUserStorage` uses stringly normalized-table SQL and repetitive hydration queries | Verified                   | `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:504-519`, `521-598`, `697-704`                                                                                                                                      |
| UI view models use inconsistent lifecycle patterns                                    | Verified                   | `src/main/java/datingapp/ui/viewmodel/BaseViewModel.java:14-87`; source scan: **16** view-model classes, **6** extend `BaseViewModel`, **10** do not                                                                           |
| `TrustSafetyService` uses exception-message sniffing to detect duplicate reports      | Verified                   | `src/main/java/datingapp/core/matching/TrustSafetyService.java:407-418`                                                                                                                                                        |
| Structural hotspot claim                                                              | Verified, counts corrected | `RestApiServer.java` 1467, `ProfileHandler.java` 1246, `ProfileController.java` 1588, `ProfileViewModel.java` 1418, `DevDataSeeder.java` 1288                                                                                  |
| REST/API tests duplicate large `createServices()` graphs                              | Verified                   | source scan: **9** test files contain `private static ServiceRegistry createServices(`; examples `RestApiHealthRoutesTest.java:119-177`, `RestApiReadRoutesTest.java:211-269`, `RestApiPhaseTwoRoutesTest.java:770-828`        |
| Tests use reflection against private internals                                        | Verified                   | `src/test/java/datingapp/app/cli/ProfileHandlerTest.java:122,264,281`, `src/test/java/datingapp/app/cli/MatchingHandlerTest.java:181-184`, `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java:850-863`            |
| Async/UI tests rely on repetition and long waits                                      | Verified                   | `src/test/java/datingapp/ui/screen/ChatControllerTest.java:110-177`, `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java:215-252`, plus widespread `waitUntil(..., 5000/8000)` usage across UI tests                  |
| `AppClockTest` uses fragile live-time tolerance                                       | Verified                   | `src/test/java/datingapp/core/AppClockTest.java:27-30`                                                                                                                                                                         |

## Additional findings from the second pass

These are **new concrete findings beyond the original report**, each verified against the source.

### 1. REST pagination limits are validated for positivity but not capped for size

**Severity:** Medium

**Evidence**

- `src/main/java/datingapp/app/api/RestApiServer.java:292-296`
- `src/main/java/datingapp/app/api/RestApiServer.java:437-444`
- `src/main/java/datingapp/app/api/RestApiServer.java:805-812`
- `src/main/java/datingapp/app/api/RestApiServer.java:881-888`

Each route parses `limit` and rejects only `<= 0`. None of these routes enforce a maximum bound before passing the value deeper into use cases/storage.

**Why it matters**

Large `limit` values can turn ordinary list endpoints into accidental resource-exhaustion paths.

**Simpler direction**

- Centralize `limit` parsing and clamp it to config-backed maxima.

### 2. `ActivityMetricsService.endSession()` mirrors the same silent no-op problem as `recordMatch()`

**Severity:** Low

**Evidence**

- `src/main/java/datingapp/core/metrics/ActivityMetricsService.java:175-181`

If no active session exists, `endSession(...)` only increments `endSessionNoOpCount` and returns nothing.

**Why it matters**

Callers cannot tell whether a session was actually ended or whether the request was dropped.

**Simpler direction**

- Return a result/boolean or log a warning on the no-op path.

### 3. `DefaultCompatibilityCalculator.calculateAgeScore()` hides malformed preference ranges behind a neutral score

**Severity:** Medium

**Evidence**

- `src/main/java/datingapp/core/matching/DefaultCompatibilityCalculator.java:67-78`

If the average age-range width is `<= 0`, the method returns `0.5` instead of failing or surfacing the invalid range state.

**Why it matters**

This makes malformed user preference ranges look like “neutral compatibility” instead of obviously invalid input.

**Simpler direction**

- Treat zero/negative preference ranges as invalid state and surface them explicitly.

### 4. Activity recency scoring in `DefaultCompatibilityCalculator` is hardcoded instead of configuration-driven

**Severity:** Low

**Evidence**

- Hardcoded thresholds: `src/main/java/datingapp/core/matching/DefaultCompatibilityCalculator.java:37-45`
- Usage: `src/main/java/datingapp/core/matching/DefaultCompatibilityCalculator.java:218-231`

The class accepts `AppConfig` and `Clock`, but the activity buckets (`1h`, `24h`, `72h`, etc.) remain internal constants.

**Why it matters**

The system treats some compatibility tuning as configuration and some as hardcoded policy, which makes the scoring surface inconsistent and harder to tune safely.

**Simpler direction**

- Either move these thresholds into config or clearly declare them as fixed policy.

### 5. Blocking users can leave conversation state only partially updated when `communicationStorage` is absent

**Severity:** Medium

**Evidence**

- `src/main/java/datingapp/core/matching/TrustSafetyService.java:456-479`
- `src/main/java/datingapp/core/matching/TrustSafetyService.java:492-548`

`updateMatchStateForBlock(...)` updates match state whenever `interactionStorage` exists, but only archives/hides the conversation if `communicationStorage != null`.

**Why it matters**

That creates a partial moderation outcome: relationship state says “blocked”, but conversation visibility/archive behavior may be skipped entirely.

**Simpler direction**

- Treat conversation-side effects as required for a fully configured block flow, or expose degraded-mode behavior explicitly.

### 6. Achievement unlocking is subscribed with `BEST_EFFORT`, so unlock failures are swallowed by the event bus

**Severity:** Medium

**Evidence**

- `src/main/java/datingapp/app/event/handlers/AchievementEventHandler.java:17-25`
- `src/main/java/datingapp/app/event/AppEventBus.java:5-22`
- `src/main/java/datingapp/app/event/InProcessAppEventBus.java:27-40`

The handler registers all achievement-related subscriptions with `HandlerPolicy.BEST_EFFORT`, and the event bus explicitly logs-and-continues on such failures.

**Why it matters**

An achievement failure becomes a silent user-facing correctness gap rather than a surfaced failure.

**Simpler direction**

- Use `REQUIRED` for critical user-state changes, or retry/surface failures explicitly.

### 7. `JavaFxTestSupport.waitUntil()` is a busy-wait harness that amplifies flakiness across the UI test suite

**Severity:** Medium

**Evidence**

- `src/test/java/datingapp/ui/JavaFxTestSupport.java:141-156`
- source scan found **110** wait/sleep/park occurrences tied to this style across `src/test/java`

`waitUntil(...)` repeatedly flushes FX events and then parks for `25ms` in a loop until timeout.

**Why it matters**

When the shared harness is polling-based, every UI test inherits timing sensitivity.

**Simpler direction**

- Prefer event-driven synchronization helpers over deadline polling in the shared harness.

### 8. REST API tests duplicate `InMemoryUndoStorage` / `InMemoryStandoutStorage` helper classes across multiple files

**Severity:** Medium

**Evidence**

- `src/test/java/datingapp/app/api/RestApiHealthRoutesTest.java:191-227`
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java:296-332`
- `src/test/java/datingapp/app/api/RestApiNotesRoutesTest.java:212-248`
- `src/test/java/datingapp/app/api/RestApiRateLimitTest.java:188-224`
- `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java:444-480`
- `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java:934-970`

These helper types are redefined in multiple REST test files instead of being centralized.

**Why it matters**

Contract drift becomes likely when helper implementations are copied rather than shared.

**Simpler direction**

- Move them into a shared REST test fixture utility or into `TestStorages`.

### 9. `ViewModelAsyncScopeTest` still hard-sleeps for synchronization in core async tests

**Severity:** Low

**Evidence**

- `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java:42`
- `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java:182`
- `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java:190`
- helper implementation: `src/test/java/datingapp/ui/async/ViewModelAsyncScopeTest.java:339-355`

The tests use `sleepQuietly(150)`, `sleepQuietly(800)`, and `sleepQuietly(200)` backed by `LockSupport.parkNanos(...)`.

**Why it matters**

These tests are verifying async mechanics, so sleep-based synchronization is especially brittle here.

**Simpler direction**

- Replace sleeps with latches, queued-dispatch checkpoints, or explicit task completion signals.

### 10. `MatchesViewModelTest` asserts private pagination state through reflection

**Severity:** Low

**Evidence**

- `src/test/java/datingapp/ui/viewmodel/MatchesViewModelTest.java:501-507`

The test reaches into private field `currentMatchOffset` via `getDeclaredField(...)` and asserts its value directly.

**Why it matters**

The test is verifying implementation structure, not just public behavior.

**Simpler direction**

- Expose a public observable/state query if offset correctness truly matters to behavior.

### 11. UI/controller tests repeatedly rebuild heavy fixture graphs instead of sharing setup

**Severity:** Medium

**Evidence**

- `src/test/java/datingapp/ui/screen/ChatControllerTest.java:348-419`
- `src/test/java/datingapp/ui/viewmodel/MatchingViewModelTest.java:321-410`
- source scan found **27** `new Fixture()` occurrences across UI screen/viewmodel tests

The fixture classes build full `TestStorages` graphs and multiple services repeatedly inside individual test methods.

**Why it matters**

This raises the maintenance cost and the runtime cost of the UI suite.

**Simpler direction**

- Share fixture builders or move common setup into `@BeforeEach` helpers/base utilities.

### 12. Test-local `createActiveUser` / `createEditableUser` helpers are still widely duplicated

**Severity:** Low

**Evidence**

- direct helper definitions appear in at least **25** test locations in a second-pass source scan
- examples: `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java:629`, `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java:489`, `src/test/java/datingapp/app/cli/MessagingHandlerTest.java:438`, `src/test/java/datingapp/app/cli/ProfileHandlerTest.java:286`, `src/test/java/datingapp/core/DailyPickServiceTest.java:339-343`

The codebase already has shared user-test helpers, but many tests still define local variants.

**Why it matters**

User factory drift makes the suite harder to standardize and refactor.

**Simpler direction**

- Consolidate on `TestUserFactory` or a small set of shared test builders.

### 13. `MessagingHandlerTest` shares one in-memory H2 database across the whole class

**Severity:** Medium

**Evidence**

- `src/test/java/datingapp/app/cli/MessagingHandlerTest.java:64-80`

The class uses `@BeforeAll`, a static `ServiceRegistry`, and `jdbc:h2:mem:...;DB_CLOSE_DELAY=-1`, meaning all test methods share one live database instance.

**Why it matters**

This increases cross-test state-coupling risk compared with the many tests that use isolated in-memory doubles.

**Simpler direction**

- Reset schema/state per test, or isolate each test method with its own DB instance.

### 14. `ProfileHandlerTest` asserts user-facing behavior by mutating global clock, timezone, and logger state

**Severity:** Medium

**Evidence**

- `src/test/java/datingapp/app/cli/ProfileHandlerTest.java:191-220`
- `src/test/java/datingapp/app/cli/ProfileHandlerTest.java:238-254`

The test swaps global `AppClock`, changes the JVM default timezone, changes logger level, and captures Logback output through a `ListAppender` just to assert rendered text.

**Why it matters**

This is a strong sign that the behavior under test is not exposed through a stable seam.

**Simpler direction**

- Extract renderable output into a presenter/formatter or make the formatting contract directly testable without global mutation.