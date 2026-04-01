# 2026-03-31 CAST Structural Quality Advisor Report

> **Workspace:** `c:\Users\tom7s\Desktop\Claude_Folder_2\Date_Program`
>
> **Perspective:** CAST-style structural quality advisory pass derived from the local checked-out source
>
> **Source code access level:** Full local source available. This enables high-confidence code-level analysis of architecture, coupling, drift from intent, test seams, and persistence paths.
>
> **Live CAST Imaging access:** Not available in this session. This is therefore **not** a server-exported CAST inventory; it is a source-grounded structural quality review.
>
> **Source of truth used:** `pom.xml`, `src/main/java`, `src/test/java`, and the code comments/Javadocs embedded in those files. Stale repository documentation was intentionally not used as authority.

## Executive verdict

This repository is structurally stronger than a quick glance might suggest. The **core architectural intent is mostly intact**:

- one application,
- three runtime surfaces,
- shared bootstrap,
- shared domain/service graph,
- explicit application boundary via `app/usecase/*`,
- framework code mostly pushed outward.

The most important quality problem is **not domain chaos**. It is **boundary growth**.

The main structural drift is concentrated in classes that were meant to be adapters, factories, or composition helpers but have grown into broader orchestration hubs:

- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/storage/StorageFactory.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`

That matters because these files now sit on the highest-fan-in seams in the system. When they drift, the blast radius is large: CLI, JavaFX, REST, storage, and tests all feel it.

### Top-level conclusions

1. **The architecture still has a coherent center.** The layering is real, not just decorative.
2. **Implementation drift is real at the boundaries.** “Thin adapter” and “bridge/factory” classes now own policy, lifecycle, identity checks, and more than one responsibility.
3. **The highest-value fixes are structural clarifications, not deep domain rewrites.**
4. **Security posture is better than it first appears**, but there is a genuine local trust-boundary issue around `X-User-Id` on the local REST API.
5. **Green IT / efficiency risks exist mostly as query-shape and polling costs**, not as catastrophic waste.
6. **ISO-5055-style concerns are mostly maintainability/coupling concerns**, especially in large orchestrators and dense storage implementations.

## Architectural baseline from source

This repository is **one application** with **three intentional runtime surfaces**:

- CLI via `src/main/java/datingapp/Main.java`
- JavaFX desktop UI via `src/main/java/datingapp/ui/DatingApp.java`
- REST API via `src/main/java/datingapp/app/api/RestApiServer.java`

All three converge on the same runtime spine:

1. `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
2. `src/main/java/datingapp/storage/StorageFactory.java`
3. `src/main/java/datingapp/core/ServiceRegistry.java`

The intended layer shape embodied by package structure and constructors is:

- `app/` — bootstrap, transport adapters, events, use cases
- `core/` — domain services, models, policies, time/config/session abstractions
- `storage/` — persistence infrastructure, JDBI implementations, schema/migrations
- `ui/` — JavaFX shell, controllers, async helpers, ViewModels

That intent is visible directly in source:

- `ApplicationStartup` describes itself as the consolidated startup/config loader.
- `StorageFactory` describes itself as a factory for fully wired `ServiceRegistry` instances.
- `ViewModelFactory` describes itself as the bridge between `ServiceRegistry` and the UI layer.
- `RestApiServer` describes itself as a thin transport adapter.

The architectural problem is that the last three claims are now only **partly true**.

## Where implementation drifts from intent

Intent here is derived only from code-level evidence: class Javadocs, constructor shapes, package boundaries, and explicit comments in source.

| Area               | Encoded intent in source                                    | Actual implementation drift                                                                                                                                      | Why it matters technically                                                                        | Priority       |
|--------------------|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|----------------|
| `RestApiServer`    | Thin HTTP transport adapter                                 | Also owns localhost enforcement, rate limiting, acting-user identity checks, route-scoped policy, exception mapping, and one deliberate direct-read bypass       | Policy drift at the HTTP edge makes route changes, auth changes, and regression risk more coupled | **High**       |
| `ViewModelFactory` | Bridge/factory between `ServiceRegistry` and UI             | Also owns controller creation, ViewModel caching, session binding, disposal/lifecycle management, UI adapters, and fallback reflection                           | Too many reasons to change; UI bugs and memory/lifecycle bugs converge here                       | **High**       |
| `StorageFactory`   | “Factory” for `ServiceRegistry`                             | Actually a broad composition root: creates JDBI, storages, domain services, event bus, and handlers                                                              | Naming understates responsibility; composition mistakes have application-wide blast radius        | **High**       |
| `ProfileService`   | Service signature suggests multi-collaborator profile logic | Constructor requires `AppConfig`, analytics, interaction, and trust-safety storage, but implementation only retains `UserStorage` and `ProfileCompletionSupport` | Dependency surface is stale or misleading, which obscures intent and complicates testing          | **Medium**     |
| `ServiceRegistry`  | Registry of injected collaborators                          | Builds `LocationService` internally instead of accepting it from the builder                                                                                     | Small but real leak in composition purity; makes object graph less explicit                       | **Medium-Low** |

## Structural hotspot inventory

The list below ranks hotspots by **risk × centrality × business impact**, not by file size alone.

| Rank | Hotspot                                    | Layer                      | Why it is central                                                             | Most affected flows                              | Signal level                         |
|-----:|--------------------------------------------|----------------------------|-------------------------------------------------------------------------------|--------------------------------------------------|--------------------------------------|
|    1 | `ServiceRegistry`                          | composition/core boundary  | Shared service graph for all surfaces                                         | all runtime surfaces                             | High-value                           |
|    2 | `ApplicationStartup`                       | bootstrap                  | controls initialization, config, shutdown, scheduling                         | every launch path                                | High-value                           |
|    3 | `StorageFactory`                           | composition/persistence    | wires storages, services, event handlers                                      | startup, persistence, events                     | High-value                           |
|    4 | `RestApiServer`                            | adapter                    | widest transport boundary and policy concentration                            | REST read/write flows                            | High-value                           |
|    5 | `DatabaseManager`                          | persistence lifecycle      | schema init, pool, password/profile gate, timeout policy                      | all DB-backed flows                              | High-value                           |
|    6 | `ViewModelFactory`                         | UI boundary                | controller/viewmodel composition and lifecycle                                | all JavaFX flows                                 | High-value                           |
|    7 | `MatchingService` + `CandidateFinder`      | domain orchestration       | candidate filtering, swipe/match/undo, cache invalidation                     | browse, like/pass, undo, standouts               | High-value                           |
|    8 | `ConnectionService` + `TrustSafetyService` | domain orchestration       | messaging, relationship transitions, moderation                               | conversations, friend-zone, block/report/unmatch | High-value                           |
|    9 | `ProfileUseCases`                          | application boundary       | broad orchestration surface for profile, notes, stats, deletion, achievements | profile update, notes, account lifecycle         | High-value                           |
|   10 | `JdbiUserStorage`                          | persistence implementation | dense normalized-profile read/write behavior                                  | profile reads/writes, candidate inputs           | High-value                           |
|   11 | `ViewModelAsyncScope`                      | UI async infra             | shared async/polling/cancellation abstraction                                 | chat refresh and UI background work              | Medium                               |
|   12 | `MigrationRunner` + `SchemaInitializer`    | schema seam                | governs fresh/upgraded DB convergence                                         | all persisted data                               | Important seam, lower immediate risk |

## Detailed hotspot analysis

### 1) Runtime composition spine: `ApplicationStartup`, `StorageFactory`, `ServiceRegistry`

**Where these sit in the architecture**

- `ApplicationStartup` is the runtime entry spine.
- `StorageFactory` turns DB infrastructure into a fully wired graph.
- `ServiceRegistry` exposes that graph to adapters and UI.

**What depends on them**

- CLI startup in `Main.java`
- JavaFX startup in `ui/DatingApp.java`
- REST startup in `app/api/RestApiServer.java`
- controller/viewmodel creation in `ui/viewmodel/ViewModelFactory.java`
- effectively every use-case bundle

**Transactions / data paths affected**

- application boot
- config loading
- database initialization and migrations
- event handler registration
- service graph creation for matching, profile, messaging, safety, achievements

**Why this matters technically**

This spine is the **structural center of gravity**. A mistake here is not a local bug; it becomes a graph bug. Typical failure modes include:

- partial or inconsistent wiring,
- hidden dependency cycles,
- startup-order defects,
- mismatched prod/test graphs,
- silent drift between builder intent and actual construction.

**Assessment**

- `ApplicationStartup` is appropriately central, but it owns config loading, DB init, optional dev seeding, shutdown hook installation, and cleanup scheduler startup in one place.
- `StorageFactory` is no longer “just storage wiring”; it is an application composition root.
- `ServiceRegistry` is more than a registry; it also creates some boundary objects (`LocationService`) and assembles all use-case bundles.

**Remediation guidance**

1. Make the composition-root roles explicit in naming and structure.
	- Either rename `StorageFactory` to reflect full graph composition, or split it into:
	  - DB/JDBI assembly,
	  - domain-service assembly,
	  - event-handler registration,
	  - final registry assembly.
2. Move any remaining object creation out of `ServiceRegistry` itself where practical, especially internally-created boundary services like `LocationService`.
3. Keep `ApplicationStartup` as the lifecycle shell, but avoid letting it grow new business-adjacent responsibilities.

**Testing after change**

- `src/test/java/datingapp/core/ServiceRegistryTest.java`
- `src/test/java/datingapp/app/api/RestApiTestFixture.java`-backed API tests
- targeted startup/bootstrap tests already covering `ApplicationStartup`
- if composition changes are substantial: full `mvn spotless:apply verify`

### 2) REST boundary hotspot: `RestApiServer`

**Where it sits**

This is the adapter between HTTP and the application boundary.

**What depends on it**

- all REST routes,
- DTO conversion,
- request guards,
- route-level error mapping,
- REST regression tests.

**Transactions / data paths affected**

- browse candidates,
- like/pass/undo,
- match-quality,
- notifications,
- friend requests,
- graceful exit/unmatch/block/report,
- conversations/messages,
- profile update,
- profile notes.

**Why it matters technically**

This file is structurally risky because it combines several concerns:

- route registration,
- local-only access enforcement,
- local rate limiting,
- acting-user identity enforcement,
- input validation / path consistency checks,
- exception-to-HTTP mapping,
- one deliberate direct-read bypass for candidate summaries.

That is no longer “thin transport.” It is a policy-bearing boundary.

**Specific drift from intent**

- The class Javadoc says business logic remains in the core and that the class is a thin adapter.
- In reality, the adapter makes non-trivial policy decisions before the use-case layer is even reached.

**Security-specific structural concern**

The server is correctly loopback-only, but it still trusts a caller-provided `X-User-Id` header for mutating routes. That is a real local trust boundary.

- This is **not** internet-exposed by default.
- It **is** vulnerable to local caller impersonation if another local process can reach the port and knows a UUID.

**Remediation guidance**

1. Extract request-guard policy into a dedicated REST boundary policy component.
2. Extract acting-user resolution and scoped-identity validation into a separate helper/service.
3. Make the “direct-read adapter exception” explicit in naming and tests so it does not silently multiply.
4. If the local API is expected to remain unauthenticated, document it as a **local IPC boundary** and consider a lightweight shared-secret or local-token guard if any real local privilege boundary exists.

**Testing after change**

- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiDailyLimitTest.java`
- `src/test/java/datingapp/app/api/RestApiRateLimitTest.java`
- add/retain focused tests for:
  - loopback-only enforcement,
  - identity/header mismatch,
  - conversation participant validation,
  - the direct-read `/candidates` exception.

### 3) UI boundary hotspot: `ViewModelFactory`

**Where it sits**

This is the JavaFX composition hub between `ServiceRegistry` and UI controllers/ViewModels.

**What depends on it**

- `ui/DatingApp.java`
- `ui/NavigationService.java`
- all controller creation paths
- ViewModel caching and disposal behavior

**Transactions / data paths affected**

- login/logout session transitions,
- controller initialization,
- screen navigation,
- chat/profile/matching/social/stats screen state.

**Why it matters technically**

This class now combines:

- controller factory duties,
- ViewModel construction,
- AppSession binding,
- ViewModel cache ownership,
- lifecycle disposal/reset,
- UI data-adapter creation,
- reflective fallback controller creation.

That is a lot of responsibility for a single UI boundary type. It becomes a likely source of:

- stale ViewModel state,
- logout/reset defects,
- memory leaks,
- controller injection mismatches,
- future accidental UI coupling.

**Remediation guidance**

1. Decide what this class really is:
	- a pure factory,
	- or a UI composition root.
2. If it remains the composition root, split out:
	- session binding,
	- controller registry,
	- ViewModel cache/lifecycle,
	- UI adapter providers.
3. Keep the cache behavior explicit and defensive, but reduce the number of concerns per class.

**Testing after change**

- relevant `src/test/java/datingapp/ui/viewmodel/*` tests
- `src/test/java/datingapp/ui/JavaFxTestSupport.java`-based controller/UI tests
- `src/test/java/datingapp/ui/async/UiAsyncTestSupport.java`-based async UI tests
- specifically retest reset/dispose/logout behavior

### 4) DB lifecycle hotspot: `DatabaseManager`

**Where it sits**

This is the H2/Hikari lifecycle gate and migration trigger.

**What depends on it**

- `ApplicationStartup`
- `StorageFactory`
- all DB-backed storages
- all JDBI-backed tests

**Transactions / data paths affected**

- pool creation,
- schema initialization,
- password/profile resolution,
- per-session query timeout application.

**Why it matters technically**

This is one of the few places where infrastructure policy becomes mandatory runtime behavior. Failure modes here include:

- startup failure,
- test-order dependence,
- wrong DB profile/password semantics,
- query timeout misconfiguration,
- first-connection initialization surprises.

**Positive finding**

The password/profile policy is relatively strong. The code fails closed instead of silently using weak defaults for local file DBs.

**Remediation guidance**

1. Keep `DatabaseManager` narrow: pool lifecycle + migration trigger + connection session policy only.
2. Preserve the explicit profile/password rules; do **not** weaken them for convenience.
3. Keep test setup deterministic by continuing to force the `test` DB profile in relevant JDBI tests.

**Testing after change**

- `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
- DB/profile configuration tests around `DatabaseManager`
- representative JDBI atomicity tests

### 5) Matching behavior hotspot: `CandidateFinder` and `MatchingService`

**Where they sit**

- `CandidateFinder` is the candidate-selection engine.
- `MatchingService` is the swipe/match/undo orchestrator.

**What depends on them**

- REST browse/candidates/like/pass/undo/match-quality paths
- matching UI flows
- CLI matching flows
- recommendation and metrics behavior downstream

**Transactions / data paths affected**

- read path: `users`, `likes`, `matches`, `blocks`
- write path: `likes`, `matches`, `undo_states`
- cache path: in-memory candidate cache invalidation

**Why they matter technically**

These are central to the app’s core business value. Bugs here hit:

- who users see,
- who can swipe,
- whether matches form correctly,
- whether undo works,
- whether blocked/recently-unmatched users are excluded.

`CandidateFinder` also carries a cache and a rich filter chain, which makes it both useful and delicate.

**Remediation guidance**

1. Keep candidate selection logic centralized, but avoid growing unrelated concerns into it.
2. Treat cache invalidation as a first-class invariant when changing swipe/block/unmatch behavior.
3. If browse logic expands further, consider splitting prefiltering, policy filtering, and caching into clearer internal seams.

**Testing after change**

- `src/test/java/datingapp/core/CandidateFinderTest.java`
- `src/test/java/datingapp/core/MatchingServiceTest.java`
- `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`
- `src/test/java/datingapp/core/MatchQualityServiceTest.java`
- REST browse/matching regression tests

### 6) Relationship and messaging hotspot: `ConnectionService` and `TrustSafetyService`

**Where they sit**

They are shared domain orchestrators for:

- conversations/messages,
- unread state,
- friend-zone transitions,
- graceful exit/unmatch,
- block/report/auto-ban.

**What depends on them**

- messaging use cases,
- social use cases,
- REST conversation and relationship routes,
- UI chat/social flows.

**Transactions / data paths affected**

- `conversations`
- `messages`
- `friend_requests`
- `notifications`
- `matches`
- `blocks`
- `reports`

**Why they matter technically**

These classes sit on **multi-entity invariants**. They are not just CRUD wrappers. They coordinate relationship state across multiple tables and behaviors. That means bugs here tend to be cross-table consistency bugs rather than simple null checks.

**Positive finding**

- message content is sanitized before persistence,
- moderation audit logging is intentional and PII-aware,
- block/report flows include explicit workflow checks and audit context.

**Remediation guidance**

1. Keep atomic relationship transitions explicit and tested.
2. Preserve the separation between workflow-policy decisions and storage-side persistence.
3. If these classes grow further, split messaging concerns from relationship-transition concerns before they become unreviewable.

**Testing after change**

- `src/test/java/datingapp/core/ConnectionServiceAtomicityTest.java`
- `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`
- `src/test/java/datingapp/core/TrustSafetyServiceTest.java`
- `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`

### 7) Profile application-boundary hotspot: `ProfileUseCases` and `ProfileService`

**Where they sit**

- `ProfileUseCases` is the app boundary for profile-facing orchestration.
- `ProfileService` is the lower-level domain helper for profile reads/completion/preview.

**What depends on them**

- REST profile and note routes,
- profile UI flows,
- notes flows,
- achievements/stats/profile deletion paths.

**Transactions / data paths affected**

- `users`
- normalized profile tables (`user_photos`, `user_interests`, `user_interested_in`, `user_db_*` tables)
- `profile_notes`
- achievement/stats side effects through metrics services and event publication

**Why they matter technically**

`ProfileUseCases` is a textbook high-coupling seam: save/update, stats, achievements, deletion, notes, sanitization, activation, and event publication all meet here.

`ProfileService` has the opposite smell: its constructor suggests rich collaboration, but the actual implementation only uses `UserStorage` plus `ProfileCompletionSupport`.

That combination produces both kinds of structural drag:

- **too broad** at the application boundary,
- **too stale/misleading** at the lower-level service boundary.

**Remediation guidance**

1. Split `ProfileUseCases` by responsibility before it grows further.
	- profile persistence/update
	- notes
	- user progress/achievements/stats
	- account deletion lifecycle
2. Collapse `ProfileService` dependencies to what it actually needs, or restore the missing behavior if the constructor reflects unfinished intent.
3. Keep sanitization and activation policies explicit; those are valuable controls, not accidental complexity.

**Testing after change**

- `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesNotesTest.java`
- `src/test/java/datingapp/app/api/RestApiNotesRoutesTest.java`
- profile-related JavaFX ViewModel tests

### 8) Persistence-density hotspot: `JdbiUserStorage`

**Where it sits**

This is the dense JDBI implementation for user/profile storage.

**What depends on it**

- profile reads and writes,
- candidate prefiltering,
- any UI/API flow that needs users, profile details, notes, or page results.

**Transactions / data paths affected**

- `users`
- `profile_notes`
- normalized profile tables
- candidate prefilter read path
- page reads for active/all users.

**Why it matters technically**

This class mixes:

- DAO definitions,
- mappers,
- caching,
- lock handling,
- normalized write fan-out,
- normalized read fan-out,
- pagination.

That density is not automatically wrong, but it is a **true maintenance hotspot**.

**Efficiency-specific concern**

`applyNormalizedProfileDataBatch(...)` performs multiple separate table reads per batch. The design is clear and normalized, but it increases round-trips and makes large-page loads more expensive than they need to be.

**Remediation guidance**

1. Keep the schema normalized, but consider reducing query fan-out for common page-read paths.
2. Re-evaluate `OFFSET`-based pagination if user counts are expected to grow materially.
3. If refactoring, split internal helper responsibilities before splitting the public storage contract.

**Testing after change**

- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
- migration/storage regression tests
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- profile-related use-case tests

## Security / CVE-style structural assessment

### High-signal security findings

#### 1) Local trust-boundary weakness on REST identity

**Finding**

`RestApiServer` is loopback-only, but mutating routes accept `X-User-Id` as the acting identity without authenticating a principal.

**Why it matters technically**

This is not a remote network bug by default, but it is still a security boundary. Any local process able to reach the port can attempt to act as any user whose UUID it knows.

**Affected occurrences**

- mutating REST routes
- conversation participant checks
- user-scoped routes (`/api/users/{id}/...`)

**Priority**

**High** if the local machine/process boundary matters. **Medium** if the API is strictly developer IPC on a trusted single-user machine.

**Recommended fix**

- Add lightweight local authentication or signed local IPC credentials, or
- constrain the API to an even tighter local-only embedding boundary and treat it as non-user-facing tooling.

**Testing**

- add or keep tests for missing `X-User-Id`, mismatched route/header user IDs, and conversation-participant authorization.

### Important positive controls

These are real strengths and should not be “fixed away” by an overzealous audit:

- **Stored content sanitization exists on write paths** via `SanitizerUtils` and `ConnectionService.sendMessage(...)` / `ProfileUseCases.upsertProfileNote(...)`.
- **Moderation audit logging is deliberately PII-aware and restrictive** in `ModerationAuditEvent` / `ModerationAuditLogger`.
- **DB password/profile handling fails closed** in `DatabaseManager` rather than silently accepting weak defaults.

### Lower-signal or noisy security findings

- The local REST API may look like an unauthenticated API vulnerability in isolation; in this codebase it is explicitly loopback-only. The real issue is **local impersonation**, not public exposure.
- Large moderation/service classes may look like audit risk by size, but the source shows explicit validation and audit behavior rather than silent bypasses.

## Green IT / efficiency assessment

### High-signal efficiency findings

#### 1) Query fan-out in normalized profile batch reads

`JdbiUserStorage.applyNormalizedProfileDataBatch(...)` reads multiple normalized tables separately for each batch of users.

**Why it matters technically**

- more DB round-trips,
- higher latency on larger pages,
- more CPU/IO work for list-style reads,
- more energy cost under scale.

**Priority**

**Medium-High** if user pages or browse surfaces grow. **Medium** otherwise.

#### 2) Sequential conversation-preview scanning

`MessagingUseCases.findConversationPreview(...)` walks conversation pages until it finds a target conversation.

**Why it matters technically**

This is a bounded loop, not a correctness bug, but it degrades toward O(n) page scanning for larger inboxes.

**Priority**

**Medium**.

#### 3) Polling-based UI refresh in `ViewModelAsyncScope`

The polling loop is well-controlled and uses virtual threads, but it is still polling.

**Why it matters technically**

- periodic wakeups,
- unnecessary work if event-driven refresh is available,
- central async abstraction means small inefficiencies multiply.

**Priority**

**Medium** for long-running UI sessions.

### Lower-signal / acceptable tradeoffs

- `CleanupScheduler` is a periodic wakeup, but it is a single daemon scheduler with sane cadence; this is not a meaningful Green IT concern.
- Normalization itself is not wasteful. The cost comes from read shape, not from schema normalization as a concept.

## ISO-5055-style structural quality assessment

### Most relevant structural concerns

#### 1) High coupling / responsibility spread

- `ProfileUseCases`
- `RestApiServer`
- `ViewModelFactory`

These classes each have too many reasons to change.

#### 2) Dense infrastructure implementations

- `JdbiUserStorage`
- other JDBI storage classes by pattern

These are maintainable today, but only because the code is still disciplined. Their structural risk is **density**, not necessarily current incorrectness.

#### 3) Composition ambiguity

- `StorageFactory` is really a composition root.
- `ServiceRegistry` is mostly registry, partly assembler.

This kind of ambiguity is exactly the kind of thing ISO-style structural guidance tends to penalize because it increases change ripple.

### Structural strengths worth preserving

- clear runtime surfaces,
- explicit use-case boundary,
- append-only migration model in `MigrationRunner`,
- frozen baseline schema concept in `SchemaInitializer`,
- framework code mostly kept outside `core/`.

These are not trivial. They are the main reason this repository still feels coherent despite some broad classes.

## High-value fixes vs. noisy findings

### Highest-value fixes

1. **Refactor the REST boundary, not the REST business logic.**
	- Extract guard/identity/rate-limit concerns out of `RestApiServer`.
2. **Make composition-root roles explicit.**
	- Clarify `ApplicationStartup` / `StorageFactory` / `ServiceRegistry` boundaries.
3. **Split `ProfileUseCases` before it grows further.**
4. **Reduce misleading dependency surfaces.**
	- Start with `ProfileService`.
5. **Improve high-traffic read-path efficiency in `JdbiUserStorage`.**
6. **Decide whether `ViewModelFactory` is a pure factory or a UI composition root and align the code accordingly.**

### Real findings, but lower urgency

1. `ViewModelAsyncScope` polling
2. conversation-preview scanning in `MessagingUseCases`
3. `ServiceRegistry` internally constructing `LocationService`

### Mostly noise if treated as top-priority defects

1. “The app has too many big classes, therefore it is broken.”
	- Not true. Several large classes are dense but disciplined.
2. “The local REST API is a public API vulnerability.”
	- Misframed. It is a **local trust-boundary** issue, not a default internet exposure.
3. “The schema code being in Java is a defect.”
	- Not here. The Java-defined schema and append-only migration pattern are coherent and testable.

## Prioritized remediation plan

### Priority 1 — boundary correction and intent alignment

#### A. Split `RestApiServer` into transport vs boundary policy helpers

**Goal**

Restore alignment between “thin adapter” intent and actual implementation.

**Suggested shape**

- `RestApiServer` retains route/module registration and DTO mapping
- extract:
  - localhost/rate-limit guard
  - acting-user resolution/authorization helper
  - shared route validation helper

**Expected benefit**

- lower route regression risk,
- clearer auth/trust-boundary reasoning,
- easier targeted tests.

#### B. Clarify the composition spine

**Goal**

Make startup, composition, and registry roles legible and explicit.

**Suggested shape**

- `ApplicationStartup` = lifecycle/config/start/stop shell
- composition root = service graph assembly
- `ServiceRegistry` = registry, not partial factory

**Expected benefit**

- smaller blast radius for wiring changes,
- easier testing and onboarding,
- less hidden object creation.

#### C. Split `ProfileUseCases`

**Goal**

Reduce coupling at the profile application boundary.

**Suggested slices**

- profile mutation/update
- notes
- achievements/stats
- deletion/account lifecycle

**Expected benefit**

- smaller tests,
- clearer change ownership,
- better ISO-style maintainability profile.

### Priority 2 — persistence and UI quality improvements

#### D. Remove stale dependency surface from `ProfileService`

**Goal**

Make constructor intent truthful.

**Expected benefit**

- clearer unit boundaries,
- fewer misleading dependencies,
- lower reviewer confusion.

#### E. Optimize `JdbiUserStorage` batch reads

**Goal**

Reduce round-trips and page cost without undoing normalization.

**Expected benefit**

- faster list/profile/browse surfaces,
- better scale behavior,
- lower Green IT cost.

#### F. Simplify `ViewModelFactory`

**Goal**

Separate UI composition from session/cache/lifecycle responsibilities.

**Expected benefit**

- easier UI reasoning,
- lower lifecycle bug risk,
- cleaner controller creation semantics.

### Priority 3 — targeted performance and maintainability cleanup

#### G. Replace sequential conversation-preview search with a direct lookup path

#### H. Reduce polling dependence where event-driven refresh is feasible

#### I. Keep migration/schema seam disciplined and heavily tested, but do not “rewrite it for style”

## Testing strategy by remediation

| Change area                         | Existing tests to run first                                                                                                            | What new/strengthened tests matter most                                                    |
|-------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| REST boundary refactor              | `RestApiReadRoutesTest`, `RestApiRelationshipRoutesTest`, `RestApiPhaseTwoRoutesTest`, `RestApiDailyLimitTest`, `RestApiRateLimitTest` | explicit loopback-only, acting-user mismatch, conversation participant authorization tests |
| Composition spine cleanup           | `ServiceRegistryTest`, startup/bootstrap tests, `RestApiTestFixture`-backed API slices                                                 | tests proving identical service graph behavior before/after refactor                       |
| `ProfileUseCases` split             | `ProfileUseCasesTest`, `ProfileUseCasesNotesTest`, `RestApiNotesRoutesTest`                                                            | tests preserving event publication, activation, notes lifecycle, deletion flow             |
| `ProfileService` dependency cleanup | profile-related use-case tests plus any direct profile-service tests                                                                   | tests documenting the reduced constructor contract                                         |
| `JdbiUserStorage` read-path work    | `JdbiUserStorageNormalizationTest`, schema/migration tests, `RestApiReadRoutesTest`                                                    | tests for normalized-table completeness and page consistency                               |
| matching/candidate changes          | `CandidateFinderTest`, `MatchingServiceTest`, `MatchingUseCasesTest`, `MatchQualityServiceTest`                                        | cache invalidation and recent-unmatch/block filtering tests                                |
| messaging/relationship changes      | `MessagingUseCasesTest`, `ConnectionServiceAtomicityTest`, `SocialUseCasesTest`, `RestApiRelationshipRoutesTest`                       | cross-table atomicity and archive/delete/unmatch edge cases                                |
| UI composition / async changes      | relevant `ui/viewmodel/*` tests, `JavaFxTestSupport`, `UiAsyncTestSupport`-based tests                                                 | reset/dispose/logout, polling cancellation, latest-wins behavior                           |
| schema/migration changes            | `SchemaInitializerTest` and related migration/storage regression tests                                                                 | fresh-vs-upgraded DB convergence checks                                                    |

## Final judgment

The repository is **not structurally unsound**. It is structurally **uneven**.

The domain center is reasonably coherent. The drift is happening at the edges and at the composition seams:

- transport boundaries that acquired policy,
- factories that became composition roots,
- application bundles that took on too many duties,
- dense storage adapters whose read/write shapes now matter strategically.

That is good news, because it means the most valuable work is **clarifying and narrowing boundaries**, not rewriting the core model.

If I were sequencing this for maximum payoff, I would do it in this order:

1. `RestApiServer` boundary cleanup
2. composition spine clarification (`ApplicationStartup` / `StorageFactory` / `ServiceRegistry`)
3. `ProfileUseCases` split
4. `ProfileService` contract correction
5. `JdbiUserStorage` efficiency pass
6. `ViewModelFactory` simplification
7. smaller follow-up performance and polling refinements

That order gives the best combination of:

- reduced structural risk,
- improved intent alignment,
- smaller future blast radius,
- better security reasoning,
- better maintainability under continued feature growth.
