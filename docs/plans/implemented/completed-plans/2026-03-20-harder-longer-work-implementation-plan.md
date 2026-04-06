# Harder and Longer Work Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce long-term blast radius by fixing storage performance bottlenecks, tightening storage/service contracts, removing architectural boundary leaks, and consolidating duplicated domain/service logic.

## ✅ Execution status (2026-03-23)

- ✅ Chunk 1 completed (V010, V079, V065, V064)
- ✅ Chunk 2 completed (V011, V026, V071, V073, V078)
- ✅ Chunk 3 completed (V009, V024, V075, V083)
- ✅ Chunk 4 completed (V067)
- ✅ Full quality gate completed (`mvn spotless:apply verify`)

## ✅ Follow-up completions (2026-04-06)

- ✅ PostgreSQL verification routine completed: `./run_verify.ps1` now runs the Maven quality gate plus PostgreSQL smoke with cleanup, and CI now includes the PostgreSQL runtime smoke path.
- ✅ REST browse/candidates consolidation completed: `/api/users/{id}/browse` is the canonical browse contract, while `/api/users/{id}/candidates` remains as a deprecated compatibility alias that projects the canonical browse candidate list.

**Architecture:** This tranche is intentionally sequenced from the bottom up. First stabilize storage read paths and schema support, because every higher layer depends on them. Then tighten contracts so silent no-op defaults and duplicated rules cannot drift. Only after the substrate is stable should we refactor the REST/UI boundary and event publishing paths. This keeps the work testable, minimizes regression risk, and gives each step a clear rollback boundary.

**Tech Stack:** Java 25, Maven, JDBI/H2, JavaFX 25, JUnit 5, existing `ServiceRegistry`, `ProfileService`, `RestApiServer`, `ProfileController`, `ImageCache`, `ValidationService`, `AppEventBus`.

---

## Scope at a glance

### In scope
- `V010` Jdbi user storage exhibits N+1 profile loading
- `V011` profile service creates a legacy achievement service per call
- `V065` missing standalone index on `messages(conversation_id)`
- `V079` `UserStorage.findByIds()` default implementation is inefficient
- `V064` `ImageCache` preload strategy can exhaust threads under load
- `V067` performance observability is insufficient
- `V009` REST routes bypass the use-case layer via deliberate exceptions
- `V024` `ProfileController` is too large and mixes responsibilities
- `V026` storage defaults mask incomplete atomic/cleanup behavior
- `V071` `ProfileActivationPolicy` duplicates completeness logic
- `V073` `RecommendationService` is mostly pass-through
- `V078` storage interface defaults violate efficient contract expectations
- `V075` social event publishing uses magic state strings

### Explicitly out of scope for this plan
- Security hardening and authentication (`V002`, `V020`, `V025`, `V033`)
- Quick correctness/UX fixes handled in the separate pressing/quick-wins plan
- Historical/resolved items that the register already marked invalid or obsolete

---

## File map

| File                                                                         | Role in this plan        | Why it changes                                                             |
|------------------------------------------------------------------------------|--------------------------|----------------------------------------------------------------------------|
| `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`                  | storage optimization     | batch profile loading, override inefficient defaults                       |
| `src/main/java/datingapp/core/storage/UserStorage.java`                      | storage contract         | replace N+1-friendly defaults with explicit efficient contracts            |
| `src/main/java/datingapp/storage/schema/SchemaInitializer.java`              | schema support           | add the missing messaging index                                            |
| `src/main/java/datingapp/storage/schema/MigrationRunner.java`                | schema migration         | add persistent schema changes for the new index                            |
| `src/main/java/datingapp/core/profile/ProfileService.java`                   | service composition      | stop constructing achievement services per call                            |
| `src/main/java/datingapp/core/metrics/DefaultAchievementService.java`        | service lifetime         | confirm injected singleton behavior works cleanly                          |
| `src/main/java/datingapp/core/workflow/ProfileActivationPolicy.java`         | rule consolidation       | remove duplicate completeness logic                                        |
| `src/main/java/datingapp/core/model/User.java`                               | rule consolidation       | keep completeness / mutation logic in one place                            |
| `src/main/java/datingapp/core/matching/RecommendationService.java`           | abstraction cleanup      | decide whether the façade has meaningful value                             |
| `src/main/java/datingapp/core/storage/CommunicationStorage.java`             | contract cleanup         | remove silent no-op defaults where unsupported behavior should be explicit |
| `src/main/java/datingapp/core/storage/InteractionStorage.java`               | contract cleanup         | same as above                                                              |
| `src/main/java/datingapp/core/storage/AnalyticsStorage.java`                 | contract cleanup         | same as above                                                              |
| `src/main/java/datingapp/core/storage/TrustSafetyStorage.java`               | contract cleanup         | same as above                                                              |
| `src/main/java/datingapp/ui/ImageCache.java`                                 | performance tuning       | bound preload concurrency and remove thread pressure                       |
| `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`           | observability            | add structured timing / counters where useful                              |
| `src/main/java/datingapp/app/api/RestApiServer.java`                         | boundary cleanup         | move direct service/storage access behind use-case contracts               |
| `src/main/java/datingapp/ui/screen/ProfileController.java`                   | controller decomposition | split a god-controller into smaller units/helpers                          |
| `src/main/java/datingapp/ui/UiUtils.java`                                    | shared UI helpers        | host utility methods extracted from controllers                            |
| `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`             | event semantics          | centralize event types and remove magic strings                            |
| `src/main/java/datingapp/app/event/AppEventBus.java`                         | event publishing         | tighten publish points and keep them single-source                         |
| `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java` | regression coverage      | batch-loading and normalization behavior                                   |
| `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`          | regression coverage      | verify index creation                                                      |
| `src/test/java/datingapp/core/profile/ProfileServiceTest.java`               | regression coverage      | singleton reuse and achievement behavior                                   |
| `src/test/java/datingapp/core/matching/RecommendationServiceTest.java`       | regression coverage      | prove the abstraction is useful or intentionally collapse it               |
| `src/test/java/datingapp/ui/ImageCacheTest.java`                             | regression coverage      | concurrency and preload bounds                                             |
| `src/test/java/datingapp/app/api/RestApiServerTest.java`                     | regression coverage      | route behavior and boundary routing                                        |
| `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`               | regression coverage      | decomposition safety                                                       |
| `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`         | regression coverage      | event payload semantics                                                    |

## Implementation notes and end-to-end gotchas

These are the decisions and constraints that keep this plan executable from start to finish:

- `V010` and `V079` are linked. Update the `UserStorage` contract and the JDBI override together so callers do not see a mixed world where one path is batched and another still loops through `get()`.
- Keep the normalization logic in `JdbiUserStorage` correct while batching. This is a performance refactor, not a data-shape change.
- `V065` requires schema and migration support together. Fresh installs and already-migrated databases must end up with the same messaging index state.
- `V011` should stop constructing the legacy achievement service per call, but the external `ProfileService` API should stay stable. If constructor wiring changes, update `ServiceRegistry` and any tests that build the service manually.
- `V026` and `V078` are contract issues, not just cleanup. If an operation is unsupported, make that explicit; do not preserve silent `0`/`false` defaults that pretend the feature exists.
- `V071` should leave exactly one source of truth for profile completeness. Prefer `User` as the domain source and make `ProfileActivationPolicy` a thin delegator if both still need to exist.
- `V073` may be a legitimate abstraction or a needless wrapper. Do not refactor it until the tests make the responsibility clear; collapse it only if the service truly adds no coordination value.
- `V009` is a boundary cleanup plan, not a security plan. Preserve any deliberate special-case routes, but isolate them so the rest of the API depends on use cases instead of storage internals.
- `ProfileController` decomposition should not change the FXML contract or the ViewModel API. Move helper logic out first; split UI responsibilities only after the current behavior is pinned by tests.
- `V075` should converge on named event types or constants. Keep `AppEventBus` as the single publish mechanism and move naming discipline into `SocialUseCases`.
- `V067` is intentionally lightweight. Add only the observability needed to reason about long-running work; do not turn this into a new telemetry platform.

## Current code paths to preserve

- `JdbiUserStorage.applyNormalizedProfileData()` and `UserStorage.findByIds()` are the main storage hot paths.
- `SchemaInitializer.createMessagingSchema()` and `MigrationRunner` govern the messaging index state.
- `ProfileService.legacyAchievementService()` is the allocation smell to remove.
- `ProfileActivationPolicy.missingFields()` and `User.isComplete()` are the duplicated completeness rules.
- `RestApiServer` route methods are the boundary leak points.
- `ProfileController` owns the currently-too-large UI controller surface.
- `SocialUseCases` and `AppEventBus` own the event naming/publishing path.
- `ImageCache` and `ActivityMetricsService` are the performance/observability surfaces.

## Verification commands

- Start with focused storage and schema tests, because those are the substrate for the rest of the plan.
- Re-run the constructor/service and contract tests before touching the boundary cleanup chunk.
- Finish with `mvn spotless:apply verify` only after the storage, contract, and boundary slices are green.

---

## Chunk 1: storage performance and schema support

### Why this chunk comes first
Performance and schema shape underpin the rest of the application. If the storage layer keeps issuing N+1 queries or the schema lacks the right index, every higher-level refactor inherits the slowdown. Fix the data access layer first so later behavior changes are measured on a stable base.

### Issues covered
- `V010`
- `V079`
- `V065`
- `V064`

### Primary files
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Modify: `src/main/java/datingapp/core/storage/UserStorage.java`
- Modify: `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
- Modify: `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- Modify: `src/main/java/datingapp/ui/ImageCache.java`
- Modify: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
- Modify: `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
- Modify: `src/test/java/datingapp/ui/ImageCacheTest.java`

### Implementation shape
1. Add tests that reveal the current N+1 and missing-index behavior.
2. Replace default storage loops with batched, concrete overrides.
3. Add the missing messaging index in both schema creation and migration flow.
4. Bound image preload concurrency so the UI cannot starve threads under load.

### Step-by-step plan
- [x] ✅ **Step 1: Write the performance regression tests first**
  - Add a test that proves `UserStorage.findByIds()` no longer loops through single-record `get()` calls in the JDBI path.
  - Add a test that proves normalized profile loading can fetch multiple users without repeated per-user round trips.
  - Add a schema test that fails until `messages(conversation_id)` exists as its own index.
  - Add a cache/concurrency test for `ImageCache` preload pressure.

- [x] ✅ **Step 2: Run the targeted tests to confirm the existing bottlenecks**
  - Keep the slice narrow enough that failures map directly to each performance issue.

- [x] ✅ **Step 3: Implement the storage and schema fixes**
  - Override `findByIds()` with a batched query in `JdbiUserStorage`.
  - Rework profile normalization to collect related rows in bulk where practical.
  - Add the missing `conversation_id` index in `SchemaInitializer` and `MigrationRunner`.
  - Bound image-cache preload work with a clear concurrency cap and a predictable fallback path.

- [x] ✅ **Step 4: Re-run the targeted tests**
  - Confirm batch loading still preserves normalization correctness.
  - Confirm the new index test passes on fresh schema creation and migration paths.

- [x] ✅ **Step 5: Smoke test the downstream call sites**
  - Validate matching, messaging, and profile-fetch flows that depend on `UserStorage.findByIds()`.
  - Validate image-heavy screens still load correctly with the preload cap in place.

### Acceptance criteria
- Batch user loading no longer exhibits the obvious N+1 pattern.
- The schema contains a dedicated `messages(conversation_id)` index.
- Image preload work is bounded and no longer creates avoidable thread pressure.
- Normalized user profile assembly still produces complete, correct objects.

### Rollback notes
- If batching becomes too invasive for one step, keep the current contract and introduce a private batch helper first, then swap the public override once tests are green.
- For the index change, keep migration logic additive so fresh installs and existing databases both converge on the same schema.

---

## Chunk 2: storage contracts and service reuse

### Why this chunk comes second
This is where silent contract violations stop being tolerated. The goal is to remove hidden no-ops, consolidate duplicated rules, and stop allocating services repeatedly when they should be reused.

### Issues covered
- `V011`
- `V026`
- `V071`
- `V073`
- `V078`

### Primary files
- Modify: `src/main/java/datingapp/core/profile/ProfileService.java`
- Modify: `src/main/java/datingapp/core/metrics/DefaultAchievementService.java`
- Modify: `src/main/java/datingapp/core/workflow/ProfileActivationPolicy.java`
- Modify: `src/main/java/datingapp/core/model/User.java`
- Modify: `src/main/java/datingapp/core/matching/RecommendationService.java`
- Modify: `src/main/java/datingapp/core/storage/UserStorage.java`
- Modify: `src/main/java/datingapp/core/storage/CommunicationStorage.java`
- Modify: `src/main/java/datingapp/core/storage/InteractionStorage.java`
- Modify: `src/main/java/datingapp/core/storage/AnalyticsStorage.java`
- Modify: `src/main/java/datingapp/core/storage/TrustSafetyStorage.java`
- Modify: `src/test/java/datingapp/core/profile/ProfileServiceTest.java`
- Modify: `src/test/java/datingapp/core/matching/RecommendationServiceTest.java`
- Modify: `src/test/java/datingapp/core/workflow/ProfileActivationPolicyTest.java` or equivalent completeness coverage
- Modify: storage implementation tests for any interface contract changes

### Implementation shape
1. Prove via tests that achievement service reuse is stable.
2. Decide which storage defaults must fail fast instead of silently returning placeholder values.
3. Collapse duplicated completeness logic into a single source of truth.
4. Decide whether `RecommendationService` is a real abstraction or just a pass-through wrapper.
5. Make every storage contract say explicitly whether an operation is supported.

### Step-by-step plan
- [x] ✅ **Step 1: Write tests that expose the current contract problems**
  - Add `ProfileServiceTest` coverage that asserts achievement-service reuse rather than per-call allocation.
  - Add tests that show unsupported storage defaults are visible and not silently ignored.
  - Add a completeness-policy test proving `ProfileActivationPolicy` and `User` agree on profile completeness.
  - Add a `RecommendationServiceTest` that makes the abstraction’s intent explicit.

- [x] ✅ **Step 2: Run the contract tests and confirm current behavior is weak or duplicated**
  - Verify the tests fail in the expected places before changing behavior.

- [x] ✅ **Step 3: Implement the contract cleanups**
  - Inject and reuse `AchievementService` in `ProfileService` instead of constructing the legacy service per call.
  - Replace silent no-op storage defaults with explicit unsupported-capability behavior where the contract requires a real implementation.
  - Remove duplicated completeness logic by making one class the source of truth and delegating the other.
  - Decide on `RecommendationService`: keep it only if it owns meaningful coordination; otherwise collapse it and update callers.

- [x] ✅ **Step 4: Re-run the contract tests**
  - Confirm the service reuse test passes.
  - Confirm unsupported methods fail explicitly rather than returning fake success.
  - Confirm completeness logic still matches all current domain rules.

- [x] ✅ **Step 5: Run a focused integration sweep**
  - Validate the profile flow, recommendation flow, and storage behavior together.

### Acceptance criteria
- `ProfileService` does not recreate achievement services per invocation.
- Storage contracts do not hide unsupported behavior behind `0`/`false` defaults.
- Profile completeness is defined in one place and reused consistently.
- Any retained `RecommendationService` has a clearly documented purpose.

### Rollback notes
- If changing storage defaults risks breaking callers too broadly, introduce explicit `supportsX()` checks or throw clear unsupported-operation exceptions first, then tighten call sites in a second pass.
- If `RecommendationService` turns out to be intentionally useful, keep it and rename/document it around its real responsibility instead of collapsing it mechanically.

---

## Chunk 3: boundary cleanup for REST, UI controllers, and events

### Why this chunk comes third
These changes are broader and more visible in the architecture, so they should happen only after the lower-level contracts are stable. The aim is to reduce boundary leaks, shrink oversized controllers, and make event semantics explicit.

### Issues covered
- `V009`
- `V024`
- `V075`
- `V083`

### Primary files
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Modify: `src/main/java/datingapp/ui/UiUtils.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Modify: `src/main/java/datingapp/app/event/AppEventBus.java`
- Modify: `src/test/java/datingapp/app/api/RestApiServerTest.java`
- Modify: `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`
- Modify: `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`

### Implementation shape
1. Lock down the current route behavior in tests before moving boundaries.
2. Make REST routes depend on use-case contracts instead of reaching directly into storage/service internals.
3. Split `ProfileController` into smaller pieces so layout, event handling, and business interaction no longer live in one class.
4. Replace social event magic strings with named constants or enums.
5. Keep event publishing centralized enough that a single business operation has a single publish path.

### Step-by-step plan
- [x] ✅ **Step 1: Write route and controller tests that document current behavior**
  - Add `RestApiServerTest` coverage for representative routes that currently bypass use cases.
  - Add `ProfileControllerTest` coverage around the responsibilities you expect to split out.
  - Add `SocialUseCasesTest` coverage for event payload values.

- [x] ✅ **Step 2: Run the tests and verify where the boundary leaks are**
  - Confirm the tests highlight direct route coupling and controller bloat instead of hiding it.

- [x] ✅ **Step 3: Refactor the REST boundary first**
  - Move route logic onto use-case interfaces where possible.
  - Keep any deliberate exceptions explicit and isolated rather than spread across handlers.
  - Update `ServiceRegistry` wiring only as needed to support the cleaner dependency shape.

- [x] ✅ **Step 4: Decompose `ProfileController`**
  - Extract repeated UI utilities to `UiUtils`.
  - Split clearly separable behavior into helper classes or smaller focused methods.
  - Keep the ViewModel contract unchanged so the UI refactor does not spill into unrelated layers.

- [x] ✅ **Step 5: Replace magic event strings**
  - Introduce named event constants or enums in `SocialUseCases`.
  - Centralize event publishing so the code does not drift across separate methods.

- [x] ✅ **Step 6: Re-run the boundary tests**
  - Confirm the REST routes still work.
  - Confirm the controller still behaves the same from the user’s perspective.
  - Confirm event payload semantics remain stable.

### Acceptance criteria
- REST routes use use-case boundaries by default.
- `ProfileController` no longer tries to own every unrelated UI concern.
- Social events are named and stable instead of stringly typed.
- Event publishing has a single obvious source of truth.

### Rollback notes
- If a route must remain an exception, make that explicit in a dedicated adapter rather than leaving it as a scattered special case.
- If controller decomposition threatens behavior, keep the public FXML/controller contract stable and move only the internal helper logic first.

---

## Chunk 4: observability and final verification

### Why this chunk comes last
Observability is useful only after the main architecture is stable enough to measure. Keep this lightweight: the goal is to gain visibility into the long-running work, not to build a new metrics platform.

### Issues covered
- `V067`

### Primary files
- Modify: `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`
- Modify: `src/main/java/datingapp/ui/async/TaskPolicy.java` if timing hooks are useful there
- Modify: `src/main/java/datingapp/ui/async/ViewModelAsyncScope.java` if timing hooks are useful there
- Modify: related tests under `src/test/java/datingapp/core/metrics/` or the closest existing coverage location

### Implementation shape
1. Add the smallest useful metrics hooks to the most important long-running paths.
2. Prefer structured counters/timing over ad-hoc logging.
3. Keep the instrumentation small enough that it does not change control flow or app behavior.

### Step-by-step plan
- [x] ✅ **Step 1: Define the minimum useful observability tests**
  - Add tests that prove metrics hooks can be triggered without changing business outcomes.
  - Keep the assertions focused on counters/timing presence, not on exact performance numbers.

- [x] ✅ **Step 2: Implement lightweight instrumentation**
  - Add timing or counter collection in `ActivityMetricsService` where it can be reused.
  - If `TaskPolicy` or `ViewModelAsyncScope` are instrumented, keep it opt-in and local.

- [x] ✅ **Step 3: Re-run the tests and inspect the surface area**
  - Confirm instrumentation does not alter return values or control flow.

- [x] ✅ **Step 4: Finish with a full repository verification run**
  - Run `mvn spotless:apply verify` once all hard-work chunks are green.

### Acceptance criteria
- The app has enough telemetry to reason about the long-running paths in this plan.
- Instrumentation remains lightweight and non-invasive.
- The final verification run is green.

### Rollback notes
- If metrics wiring becomes too invasive, keep the hooks in one service class and defer framework-level instrumentation to a later tranche.

---

## Final verification order

### Suggested execution order for an agent
1. Storage performance and schema chunk
2. Storage contracts and service reuse chunk
3. Boundary cleanup chunk
4. Observability chunk
5. `mvn spotless:apply verify`

### Exit checklist
- [x] ✅ Every in-scope issue has a matching regression test or a documented reason not to add one yet.
- [x] ✅ Performance fixes are done before boundary refactors that might otherwise obscure the bottleneck.
- [x] ✅ Contract cleanup removes silent no-ops instead of papering over them.
- [x] ✅ REST/UI/event refactors preserve visible behavior.
- [x] ✅ Full Maven quality gate passes.
