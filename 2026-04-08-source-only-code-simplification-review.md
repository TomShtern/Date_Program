# 2026-04-08 Source-Only Code Simplification Review

> **Scope:** `src/main/java` and `src/test/java` only.
>
> **Source of truth policy:** this report intentionally ignores repository docs, plans, audit markdown, and historical writeups. Every conclusion here is grounded in the current source code plus fresh source-derived tooling runs.
>
> **Revision status:** second-pass corrected revision. This supersedes the earlier April 8 draft and fixes overstatements, adds omissions, and updates the evidence with fresh counts and fresh analysis runs.
>
> **Status:** read-only analysis. No production or test code was changed.

## Why this document exists

This report is meant to be useful for two audiences at once:

- **Humans** who want a skim-friendly view of the most important cleanup opportunities.
- **AI coding agents** that may later execute a refactor campaign and need a precise source-grounded map of where complexity is concentrated, what is already improving, and what should be left alone.

The focus is not on tiny style issues. The focus is on **structural complexity** that leads to:

- high LOC,
- repetitive orchestration,
- unstable boundaries,
- expensive tests,
- broader blast radius for changes,
- and more architectural friction than the current codebase needs.

---

## Fresh evidence snapshot

### Inventory

| Tree            | Java files | Code LOC | Total lines | Reported complexity | Takeaway                                                                                                                  |
|-----------------|-----------:|---------:|------------:|--------------------:|---------------------------------------------------------------------------------------------------------------------------|
| `src/main/java` |        180 |   42,087 |      52,773 |               5,831 | Main complexity is concentrated in app adapters, storage, UI, and a few core interaction seams                            |
| `src/test/java` |        202 |   44,373 |      53,784 |               1,112 | Tests are slightly larger than main code, which is a strong signal that several seams are expensive to set up and protect |
| Combined        |        382 |   86,460 |     106,557 |               6,943 | This is a large codebase with complexity spread across multiple layers, not a single bad subsystem                        |

### Fresh static-analysis signal

| Tool       | Fresh result                | What it means                                                                            |
|------------|-----------------------------|------------------------------------------------------------------------------------------|
| Checkstyle | clean                       | Style rules are not the main problem                                                     |
| PMD        | clean                       | The dominant issues are not basic static-rule violations                                 |
| CPD        | 1 localized duplication     | There is some duplication, but the main debt is architectural shape, not mass copy-paste |
| SpotBugs   | not configured in `pom.xml` | No fresh SpotBugs signal was available                                                   |

### What that means immediately

This codebase’s main problem is **not linter debt**.

The real issue is **structural accumulation**:

- compatibility layers that survived too long,
- broad coordinators that kept absorbing responsibilities,
- transactional repositories spanning too many aggregates,
- UI orchestration seams that mirror unstable lower boundaries,
- tests that reconstruct too much of the app graph.

---

## What changed from the first pass

The first pass was directionally useful, but this second pass corrected several important points.

| Earlier framing                                                                                | Corrected source-backed view                                                                                                                                                                             |
|------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `RecommendationService` was treated like a major unfinished monolith                           | It is better described as a **coordination facade over already-split leaf services** (`DefaultDailyLimitService`, `DefaultDailyPickService`, `DefaultStandoutService`, `DefaultBrowseRankingService`)    |
| `CandidateFinder` was discussed as if cache invalidation were a real cleanup seam              | Its `invalidateCacheFor()` / `clearCache()` methods are deliberate no-ops; current behavior is explicitly **freshness-first**, not stale-cache-driven                                                    |
| `ProfileService` was framed like a partial extraction still needing completion                 | It is already a **thin facade** over `ProfileCompletionSupport` plus user lookup methods                                                                                                                 |
| `StorageFactory` was framed too harshly as a large blob                                        | It is still a central composition root, but it is **already segmented** into `createPersistenceComponents(...)`, `createDomainServices(...)`, `registerEventHandlers(...)`, and `assembleRegistry(...)`  |
| `SchemaInitializer` + `MigrationRunner` were described too strongly as competing schema truths | They are better understood as a **shared baseline plus append-only migration replay path**, with intentional overlap for compatibility                                                                   |
| `DatabaseManager` was discussed too much through an H2/legacy lens                             | It is a **runtime database manager for both H2 and PostgreSQL**, and the real problem is policy accretion, not missing PostgreSQL runtime support                                                        |
| UI controllers were ranked too aggressively as first-pass cleanup targets                      | The current source shows that **ViewModel/state seams matter more than controller glue**                                                                                                                 |
| `ViewModelFactory` / `NavigationService` were weighted too highly                              | Still important shared seams, but **not first-pass cleanup targets** now that `UiAdapterCache` and several ViewModel helpers already exist                                                               |
| `JdbiMetricsStorage` and `DevDataSeeder` were treated too much like top structural crises      | Both are large, but much of their size is **centralized analytics SQL** or **seed data bulk**, not the highest-leverage architectural debt                                                               |
| Several important omissions were missing                                                       | Added: `JdbiAccountCleanupStorage`, `DashboardUseCases`, `MatchesViewModel`, `UiAdapterCache`, `SafetyHandler`, `StatsHandler`, `MainMenuRegistry`, architecture tests, and adapter-presentation helpers |

---

## Main conclusions

### 1. The biggest problem is not “a few giant files”

The biggest problem is a set of **complexity multipliers** that make many files large at the same time.

### 2. The codebase already contains several good extractions

This matters because the right next move is often **finish the extraction and delete the old scaffolding**, not invent a whole new abstraction layer.

### 3. Tests are telling the same story as production code

The fact that test code is slightly larger than main code is not inherently bad. Here, it is an architectural signal: several runtime seams are broad enough that tests need large fixtures, graph builders, or helper subsystems.

### 4. Cleanup should target leverage, not file size alone

Some big files are primarily bulky because they are data-heavy or centrally responsible in a reasonable way. Other smaller files are more dangerous because they sit at high-fan-out coordination seams.

### 5. Best global strategy remains: inner seams first, outer adapters later

Do **not** start by beautifying controllers or splitting routes if the lower seams they mirror are still unstable.

---

## Corrected complexity multipliers

These are the structural patterns that recur across layers and create most of the maintainability drag.

### 1. Compatibility residue at application boundaries

**Where it shows up**

- `app/usecase/profile/ProfileUseCases.java`
- `app/usecase/social/SocialUseCases.java`
- `app/usecase/matching/MatchingUseCases.java`
- `ui/viewmodel/ProfileViewModel.java`
- `core/connection/ConnectionService.java`

**Why it matters**

The codebase sometimes keeps both the old umbrella API and the newer slice-specific path alive at the same time. That creates “two architectures at once.”

**What to do later**

- pick the real long-term boundary,
- migrate callers,
- delete the compatibility layer afterward.

### 2. Central composition roots with too much subsystem knowledge

**Where it shows up**

- `core/ServiceRegistry.java`
- `app/bootstrap/ApplicationStartup.java`
- `storage/StorageFactory.java`
- `storage/DatabaseManager.java`
- `ui/viewmodel/ViewModelFactory.java`

**Why it matters**

These files amplify blast radius. Changes in one subsystem often drag central wiring files into the diff.

### 3. Transactional multi-aggregate repositories

**Where it shows up**

- `storage/jdbi/JdbiAccountCleanupStorage.java`
- `storage/jdbi/JdbiMatchmakingStorage.java`
- `storage/jdbi/JdbiConnectionStorage.java`
- `storage/jdbi/JdbiUserStorage.java`

**Why it matters**

These classes combine too many business concerns inside one storage boundary, especially where transactions span multiple tables or graph transitions.

### 4. Stateful UI orchestration seams

**Where it shows up**

- `ui/viewmodel/ProfileViewModel.java`
- `ui/viewmodel/ChatViewModel.java`
- `ui/viewmodel/MatchesViewModel.java`
- `ui/viewmodel/MatchingViewModel.java`

**Why it matters**

These seams absorb form state, async flows, session sync, action routing, and presentation logic at the same time.

### 5. Broad test scaffolding that mirrors production architecture

**Where it shows up**

- `core/testutil/TestStorages.java`
- `app/api/RestApiTestFixture.java`
- large REST/UI/service tests
- architecture guard tests

**Why it matters**

The tests are not merely “big”; they preserve the same breadth as the production seams. That makes refactors more expensive than they need to be.

---

## Existing seams worth preserving

This section matters because not everything large is equally messy.

### App layer

- `app/api/RestRouteSupport.java` already splits route registration by family.
- `app/api/RestApiRequestGuards.java` already owns localhost-only, rate limiting, and mutating-route identity enforcement.
- `app/api/RestApiIdentityPolicy.java` already owns scoped identity parsing and conversation participant logic.
- The profile app layer is already split into:
  - `ProfileMutationUseCases`
  - `ProfileNotesUseCases`
  - `ProfileInsightsUseCases`
  - `VerificationUseCases`

### Core layer

- `core/profile/ProfileCompletionSupport.java` already owns most completion logic.
- `core/matching/RelationshipWorkflowPolicy.java` is the real rule seam for transitions.
- `core/matching/RecommendationService.java` is a stable facade over already-split leaf services.
- `core/AppConfigValidator.java` centralizes config validation cleanly.

### Storage layer

- `storage/DatabaseDialect.java` and `storage/jdbi/SqlDialectSupport.java` are stabilizers, not debt.
- `NormalizedProfileRepository`, `NormalizedProfileHydrator`, and `DealbreakerAssembler` already pulled real complexity out of `JdbiUserStorage`.

### UI layer

- `ui/viewmodel/UiAdapterCache.java` already extracted adapter lifecycle from `ViewModelFactory`.
- `ProfileDraftAssembler`, `PhotoMutationCoordinator`, `PhotoCarouselState`, `ConversationLoader`, `MatchListLoader`, and `RelationshipActionRunner` are meaningful extractions already in place.

### Test layer

- The architecture tests are useful guardrails, not noise:
  - `AdapterBoundaryArchitectureTest`
  - `TimePolicyArchitectureTest`
  - `EventCoverageArchitectureTest`

---

## Corrected hotspot ranking

This ranking is by **architectural leverage**, not line count alone.

| Rank | Hotspot seam                              | Primary files                                                                                                                                       | Why it ranks here                                                                                                              | Important caveat                                                                            |
|-----:|-------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
|    1 | Matching orchestration seam               | `MatchingUseCases`, `MatchingService`, `CandidateFinder`                                                                                            | Heavy command/query/policy overlap, many request/result shapes, localized wrapper residue, high fan-out into UI/REST/CLI/tests | `RecommendationService` itself is not the main problem; the wider seam around it is         |
|    2 | Relationship and messaging seam           | `SocialUseCases`, `ConnectionService`, `TrustSafetyService`, `RelationshipWorkflowPolicy`                                                           | Messaging, relationship transitions, moderation, friend requests, and notifications meet here                                  | `TrustSafetyService` is wide but still reasonably cohesive                                  |
|    3 | Central composition/runtime seam          | `ServiceRegistry`, `ApplicationStartup`, `StorageFactory`, `DatabaseManager`                                                                        | Central wiring, lifecycle, runtime policy, and use-case assembly create high blast radius                                      | `StorageFactory` is already segmented; it should be thinned, not radically reinvented first |
|    4 | Account deletion seam                     | `JdbiAccountCleanupStorage`                                                                                                                         | One transactional flow touches nearly the whole persistence graph                                                              | High-value because it crosses many bounded contexts at once                                 |
|    5 | Interaction persistence seam              | `JdbiMatchmakingStorage`                                                                                                                            | Likes, matches, undo, and transition persistence are bundled into one large storage boundary                                   | Strong candidate for separating state-machine writes from simpler persistence paths         |
|    6 | Conversation/social persistence seam      | `JdbiConnectionStorage`                                                                                                                             | Conversations, messages, friend requests, notifications, and batch query helpers live together                                 | Still cohesive enough to split carefully rather than explosively                            |
|    7 | User persistence seam                     | `JdbiUserStorage` + normalized-profile helpers                                                                                                      | User CRUD, cache/locks, notes, pagination, and hydration coordination are still broad                                          | Already partially decomposed; do not ignore the extracted helpers                           |
|    8 | REST orchestration seam                   | `RestApiServer`, `RestRouteSupport`, `RestApiRequestGuards`, `RestApiIdentityPolicy`, `RestApiUserDtos`, `UserDtoMapper`                            | Request parsing, compatibility aliasing, DTO mapping, and use-case orchestration still concentrate here                        | Route registration and guards are already partially extracted                               |
|    9 | Profile editor UI seam                    | `ProfileViewModel`, `ProfileDraftAssembler`, `PhotoMutationCoordinator`, `ProfileController`                                                        | Form state, completion/onboarding, save flows, and photo mutation all converge here                                            | The helper extraction is real; future cleanup should continue that direction                |
|   10 | Chat and matches UI seam                  | `ChatViewModel`, `MatchesViewModel`, `MatchingViewModel`, `ConversationLoader`, `MatchListLoader`, `RelationshipActionRunner`, `PhotoCarouselState` | Async state, paging, action routing, and UI timing/state concerns are concentrated here                                        | `MatchesViewModel` deserves more weight than it had in the first pass                       |
|   11 | CLI flow seam                             | `ProfileHandler`, `MatchingHandler`, `MessagingHandler`, `SafetyHandler`, `StatsHandler`, `MainMenuRegistry`                                        | Stateful user-interaction flows and repeated channel orchestration still live here                                             | `MainMenuRegistry` is more important than `Main` itself                                     |
|   12 | Schema and migration seam                 | `SchemaInitializer`, `MigrationRunner`                                                                                                              | Large correctness-critical boundary with deliberate baseline + migration replay logic                                          | This is more “important correctness surface” than “highest-priority architectural mess”     |
|   13 | Dashboard and profile aggregation seam    | `DashboardUseCases`, `ProfileUseCases`, profile slices                                                                                              | App-level aggregation and compatibility glue still create extra wrapping/mapping behavior                                      | `ProfileUseCases` is now mostly a facade, not a primary hotspot                             |
|   14 | Configuration and validation seam         | `AppConfig`, `AppConfigValidator`, `ValidationService`                                                                                              | Broad cross-cutting contract surface                                                                                           | Important, but lower-blast and cleaner than the top seams                                   |
|   15 | Large but lower-priority supporting files | `JdbiMetricsStorage`, `DevDataSeeder`, controllers, `UiFeedbackService`                                                                             | Large, but less strategically urgent than the seams above                                                                      | Size here overstates urgency                                                                |

---

## App layer findings (corrected)

### `src/main/java/datingapp/app/api/RestApiServer.java`

**Keep this finding:** still one of the most important adapter-layer seams.

**Corrected framing:**

- `RestApiServer` is no longer the right place to complain about route registration itself.
- The real issue is that it still centralizes **request orchestration, error/result mapping, compatibility alias behavior, and DTO shaping** across many route families.

**Important supporting seams**

- `RestRouteSupport` already splits registration by route family.
- `RestApiRequestGuards` already owns localhost-only, rate limiting, and scoped mutating-route checks.
- `RestApiIdentityPolicy` already owns acting-user identity and conversation participant parsing.
- `RestApiUserDtos` + `UserDtoMapper` carry user-display mapping logic that should be discussed alongside the server.

**Specific source points worth citing**

- `RestApiServer.getCandidates(...)`
- `RestApiServer.markCandidatesCompatibilityAlias(...)`
- `RestApiServer.handleUseCaseFailure(...)`
- `RestRouteSupport.registerRoutes(...)`
- `RestApiRequestGuards.registerRequestGuards(...)`
- `RestApiIdentityPolicy.parseConversationParticipants(...)`

### `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`

Still a major runtime-lifecycle seam.

It mixes:

- config loading,
- env override policy,
- config path resolution,
- startup/shutdown/reset,
- failure recovery,
- seeding,
- test hooks.

The bootstrap tests prove this is not just static-code suspicion:

- `ApplicationStartupBootstrapTest`
- `ApplicationStartupFailureRecoveryTest`
- `MainBootstrapLifecycleTest`

### App use-case bundle corrections

#### `MatchingUseCases`

Still a top cleanup target.

Why:

- 725 lines,
- 85 public methods by fresh aggregation,
- 28 nested types,
- 68 `UseCaseResult` returns,
- localized wrapper residue around daily-limit/daily-pick/standout compatibility.

#### `SocialUseCases`

Still important.

Why:

- moderation,
- notifications,
- relationship transitions,
- friend requests,
- compatibility residue.

#### `DashboardUseCases`

**Missed in the first pass.**

This is a real app aggregation seam across:

- completion,
- daily status,
- daily pick,
- achievements,
- unread counts,
- profile nudges.

#### `ProfileUseCases`

**Corrected framing:**

Treat this as a compatibility façade over the dedicated profile slices, not as a top complexity crisis on its own.

### CLI cluster (expanded)

The first pass under-weighted two files.

The CLI cleanup cluster should explicitly include:

- `ProfileHandler`
- `MatchingHandler`
- `MessagingHandler`
- `SafetyHandler`
- `StatsHandler`
- `MainMenuRegistry`

`MainMenuRegistry` is more meaningful than it first looked because it owns menu ordering, metadata, and dynamic rendering behavior.

---

## Core layer findings (corrected)

### `src/main/java/datingapp/core/ServiceRegistry.java`

This remains one of the most important fan-out seams in the codebase.

What matters is not just its size, but that it:

- stores many collaborators,
- assembles several use-case bundles inline,
- acts as both service catalog and composition root.

### `src/main/java/datingapp/core/model/User.java`

Still a real hotspot, but with an important caveat:

- yes, it absorbed many concerns,
- but it is also the real aggregate root for durable user state.

So future cleanup should be **targeted extraction**, not a flag-day redesign.

Reasonable extraction candidates later:

- photo normalization helpers,
- profile completeness policy,
- verification workflow helpers,
- storage-facing helper shapes.

### `src/main/java/datingapp/core/matching/MatchingService.java`

Still one of the strongest core cleanup candidates.

Why:

- swipe command handling,
- eligibility policy,
- in-flight dedupe,
- persistence integration,
- undo recording,
- pending-liker query behavior,
- metrics hooks.

This is the best example of a core service that wants a cleaner command/query split.

### `src/main/java/datingapp/core/matching/CandidateFinder.java`

This remains an important companion seam to `MatchingService`, but the first pass overstated the caching angle.

**Corrected interpretation:**

- the real complexity is the multi-rule eligibility pipeline,
- not stale-cache invalidation,
- because current source explicitly uses freshness-first behavior and no-op invalidation methods.

### `src/main/java/datingapp/core/connection/ConnectionService.java`

Still a strong hotspot.

It combines:

- message send/load/read,
- unread behavior,
- conversation previews,
- friend-zone flows,
- graceful exit,
- unmatch,
- transition result wrapping.

### `src/main/java/datingapp/core/matching/TrustSafetyService.java`

Important, but better described as **wide but cohesive**.

It is not merely a random grab bag; it is a real trust/safety boundary. The cleanup opportunity is to reduce breadth inside that boundary, not necessarily to shatter it into many tiny services.

### `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`

Still broad and still worth calling out.

It combines:

- session lifecycle,
- swipe gating,
- cleanup,
- user stats,
- platform stats.

### `RecommendationService` and the scoring cluster

This is where the biggest wording correction was needed.

**Corrected view:**

- `RecommendationService` is not the main mess.
- It is now a stable coordination facade over already-split leaf services.
- The real opportunity is to reduce duplicated result-shape/scoring/presentation logic **around** it.

Files to consider together:

- `RecommendationService`
- `DefaultBrowseRankingService`
- `DefaultDailyLimitService`
- `DefaultDailyPickService`
- `DefaultStandoutService`
- `DefaultCompatibilityCalculator`
- `MatchQualityService`

### `ProfileService`, `ProfileCompletionSupport`, `ValidationService`, `AppConfig`

All of these were a bit over-weighted in the first pass.

- `ProfileService` is already relatively thin.
- `ProfileCompletionSupport` is already the real extraction.
- `ValidationService` is broad, but mostly a low-blast-radius leaf.
- `AppConfig` is large, but structurally organized and backed by `AppConfigValidator`.

These are legitimate cleanup surfaces, but not the first architectural dominoes to push.

---

## Storage/runtime/schema findings (corrected)

### `src/main/java/datingapp/storage/DatabaseManager.java`

Still important, but with corrected emphasis.

**Corrected view:**

- This is not an H2-only manager.
- It is a runtime DB manager that supports H2 and PostgreSQL paths.
- The problem is not lack of runtime DB support; the problem is that too much runtime policy lives here.

Main responsibilities currently mixed here:

- pool lifecycle,
- runtime storage configuration,
- password/profile policy,
- JDBC URL resolution,
- schema bootstrap handoff,
- per-connection session setup.

### `src/main/java/datingapp/storage/StorageFactory.java`

Still a real central composition root, but the second pass softened the verdict.

**Corrected view:**

- It is already segmented into assembly stages.
- It should be kept thin and bounded.
- It is not the best “split immediately” target compared with more tangled seams.

### `SchemaInitializer` + `MigrationRunner`

Still important, but corrected from “two competing truths” to:

> a correctness-critical shared baseline + append-only legacy migration replay path with intentional overlap.

This is a big seam, but not the first one to refactor unless schema semantics are changing.

### `JdbiAccountCleanupStorage`

**Major omission from the first pass.**

This is now one of the most important storage cleanup targets because it touches almost the whole persistence graph in one transactional workflow.

It coordinates cleanup across:

- profile notes/views,
- normalized profile tables,
- stats/achievements,
- daily picks/views,
- swipe sessions,
- likes,
- matches,
- conversations/messages,
- standouts,
- friend requests,
- notifications,
- blocks,
- reports,
- undo state,
- user soft-delete.

### `JdbiMatchmakingStorage`

Still a major hotspot.

Why:

- likes,
- matches,
- undo state,
- transition writes,
- multiple DAO/mappers,
- multiple upsert builders,
- notification serialization.

This is one of the clearest transactional multi-aggregate storage seams in the repo.

### `JdbiConnectionStorage`

Still a major hotspot.

Why:

- messages,
- conversations,
- friend requests,
- notifications,
- transactional batch helpers,
- read and write helper SQL in one place.

### `JdbiUserStorage`

Still broad, but corrected from “pure monolith” to “partially decomposed but still broad.”

Already-extracted helpers that must be acknowledged:

- `NormalizedProfileRepository`
- `NormalizedProfileHydrator`
- `DealbreakerAssembler`

The remaining problem is the coordination seam around:

- CRUD,
- cache,
- locking,
- notes,
- paging,
- hydration orchestration.

### `JdbiMetricsStorage`

Still large, but lower priority than the transactional repository seams above.

Its size comes largely from being the central analytics store for:

- stats,
- achievements,
- sessions,
- views,
- daily picks,
- standouts.

That is still a cleanup candidate, but the code reads more like a centralized analytics contract than a highly tangled transition engine.

### Lower-priority storage/supporting files

- `DevDataSeeder` is large, but much of that size is seed data bulk and deterministic dataset wiring.
- `DatabaseDialect` and `SqlDialectSupport` are **good centralization seams**, not debt.
- `JdbiTrustSafetyStorage` is comparatively compact and not a top hotspot.

---

## UI findings (corrected)

### Strategic UI seams

These are the UI files that still matter most architecturally:

- `ProfileViewModel`
- `ChatViewModel`
- `MatchesViewModel`
- `MatchingViewModel`

The first pass under-weighted `MatchesViewModel`; the second pass corrects that.

### `ProfileViewModel`

Still broad, but now described more accurately.

**Corrected view:**

- it is still the main profile-editor orchestration seam,
- but helper extraction is already meaningfully underway.

Important existing seams to preserve and extend:

- `ProfileDraftAssembler`
- `PhotoMutationCoordinator`
- `PhotoCarouselState`

### `ChatViewModel`

Still a strong UI hotspot.

The second pass confirmed that the chat state machine is a real coordination seam and not just a long file.

Important extracted/helper seam to mention explicitly:

- `ConversationLoader`

### `MatchesViewModel`

Promoted in the second pass.

It is not just “another big list ViewModel.” It coordinates:

- paging,
- likes received/sent,
- relationship actions,
- refresh epochs,
- load-more behavior.

Important helpers already in place:

- `MatchListLoader`
- `RelationshipActionRunner`

### `MatchingViewModel`

Still important, but better discussed together with its existing `PhotoCarouselState` extraction.

### `ViewModelFactory` + `UiAdapterCache` + `NavigationService`

Second-pass correction:

- still important shared seams,
- but no longer ranked as first-pass cleanup targets.

Why:

- `UiAdapterCache` already removed a real concern from `ViewModelFactory`.
- `NavigationService` is still broad, but much of it is central hosting/navigation glue.

### Controller cluster

Second-pass correction:

- Controllers are large,
- but most of their complexity is downstream glue: binding, forwarding, animation, dialog wiring, and view state synchronization.

So:

- `ProfileController`, `ChatController`, `MatchingController`, `MatchesController`, and `DashboardController` matter,
- but they are **not** the best first structural cleanup targets.

### `UiFeedbackService`

Corrected view:

- still a utility bucket,
- but lower strategic urgency than the ViewModel state seams.

---

## Test architecture findings (expanded and corrected)

### Still-important broad fixtures

- `core/testutil/TestStorages.java`
- `app/api/RestApiTestFixture.java`

These remain strong architectural signals because they mirror broad production seams.

### Architecture guard tests were missing from the first pass

These deserve explicit mention because they protect repository-wide structural rules:

- `architecture/AdapterBoundaryArchitectureTest.java`
- `architecture/TimePolicyArchitectureTest.java`
- `architecture/EventCoverageArchitectureTest.java`

### UI helper tests were also under-weighted

These are valuable because they highlight the extracted seams that already exist:

- `UiAdapterCacheTest`
- `UiDataAdaptersTest`
- `UiDataAdaptersPresenceTest`
- `PhotoCarouselStateTest`
- `ViewModelArchitectureConsistencyTest`
- `BaseViewModelTest`
- `BaseControllerTest`

### Navigation tests matter

- `NavigationServiceTest`
- `NavigationServiceContextTest`
- `NavigationServiceRouteTest`

These show that navigation behavior is not just generic glue; it has real repository-specific rules.

### REST test coverage is broader than the first pass captured

In addition to the biggest route suites, these families should be explicitly acknowledged:

- `RestApiRelationshipRoutesTest`
- `RestApiVerificationRoutesTest`
- `RestApiNotesRoutesTest`
- `RestApiRequestGuardsTest`
- `RestApiRateLimitTest`

### Best interpretation of test size

The test suite should be read as:

- partly a mirror of broad production seams,
- partly a set of useful architectural guardrails,
- not merely as “bad because it is large.”

---

## Strategic hotspots vs. bulky-but-lower-priority files

This distinction is important.

### Strategic hotspots

These should drive real cleanup planning:

- `MatchingUseCases`
- `MatchingService`
- `CandidateFinder`
- `SocialUseCases`
- `ConnectionService`
- `TrustSafetyService`
- `ServiceRegistry`
- `ApplicationStartup`
- `DatabaseManager`
- `JdbiAccountCleanupStorage`
- `JdbiMatchmakingStorage`
- `JdbiConnectionStorage`
- `JdbiUserStorage`
- `RestApiServer`
- `ProfileViewModel`
- `ChatViewModel`
- `MatchesViewModel`
- `MatchingViewModel`

### Large but lower-priority files

These are real maintenance surfaces, but they should not outrank the list above:

- `RecommendationService`
- `ProfileService`
- `ValidationService`
- `AppConfig`
- `StorageFactory`
- `SchemaInitializer`
- `MigrationRunner`
- `JdbiMetricsStorage`
- `DevDataSeeder`
- `ViewModelFactory`
- `NavigationService`
- large controllers
- `UiFeedbackService`

---

## Corrected cleanup order

This is the revised global order optimized for **leverage and low thrash**.

### Phase 1 — Core interaction seams

**Primary targets**

- `MatchingService`
- `CandidateFinder`
- `ConnectionService`
- `TrustSafetyService`
- `RelationshipWorkflowPolicy`

**Why first**

These are the real behavior engines underneath multiple adapters and use-case bundles.

### Phase 2 — Application boundary bundles

**Primary targets**

- `MatchingUseCases`
- `SocialUseCases`
- `DashboardUseCases`

**Profile note:** keep `ProfileUseCases` as a façade until consumer migration is complete.

**Why second**

REST, CLI, UI, and tests all consume these seams directly.

### Phase 3 — Composition and runtime policy seam

**Primary targets**

- `ServiceRegistry`
- `ApplicationStartup`
- `DatabaseManager`
- `StorageFactory` (thin-and-clarify, not radical rewrite)

**Why third**

After the behavior seams stabilize, central wiring can shrink without repeated rework.

### Phase 4 — Transactional repository seams

**Primary targets**

- `JdbiAccountCleanupStorage`
- `JdbiMatchmakingStorage`
- `JdbiConnectionStorage`
- `JdbiUserStorage`

**Secondary target**

- `JdbiMetricsStorage`

**Why fourth**

These are the most important persistence boundaries once service-level behavior is clearer.

### Phase 5 — UI state seams

**Primary targets**

- `ProfileViewModel`
- `ChatViewModel`
- `MatchesViewModel`
- `MatchingViewModel`

**Keep and extend extracted helpers**

- `ProfileDraftAssembler`
- `PhotoMutationCoordinator`
- `PhotoCarouselState`
- `ConversationLoader`
- `MatchListLoader`
- `RelationshipActionRunner`

### Phase 6 — REST and CLI adapters

**Primary targets**

- `RestApiServer` and adjacent DTO/presentation seams
- CLI handler cluster
- `MainMenuRegistry`

**Why sixth**

These layers should mirror a cleaner interior, not define one.

### Phase 7 — Late/optional cleanup

**Targets**

- `User` aggregate (carefully, surgically)
- schema/migration organization
- `ValidationService`
- `AppConfig`
- `JdbiMetricsStorage`
- `DevDataSeeder`
- `ViewModelFactory`
- `NavigationService`
- controllers and utility buckets

**Why last**

These are important, but they either:

- already have cleaner structure than first appeared, or
- become easier after the earlier phases reduce the real coupling.

---

## High-priority “do not do first” list

1. **Do not start with a `RestApiServer` breakup.**
   The route helpers and guards are already partially extracted; the right prerequisite is cleaner application seams.

2. **Do not start with controller beautification.**
   Controller noise still mostly reflects broader ViewModel/state seams below.

3. **Do not start with `ViewModelFactory` or `NavigationService`.**
   Those become easier after ViewModel seams flatten further.

4. **Do not start with a flag-day `User` redesign.**
   It touches too many layers.

5. **Do not over-prioritize schema/migration restructuring.**
   It is important, but current source suggests this is more a correctness boundary than the highest-value cleanup target today.

6. **Do not mistake clean PMD/Checkstyle for structural health.**
   The main debt here is architectural shape, not linter findings.

---

## Fresh longest-file appendices

## Appendix A — Longest main-source Java files

| Lines | File                                                                 |
|------:|----------------------------------------------------------------------|
|  1344 | `src/main/java/datingapp/ui/screen/ProfileController.java`           |
|  1171 | `src/main/java/datingapp/storage/DevDataSeeder.java`                 |
|  1145 | `src/main/java/datingapp/app/cli/ProfileHandler.java`                |
|  1139 | `src/main/java/datingapp/app/api/RestApiServer.java`                 |
|  1074 | `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`         |
|  1072 | `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`   |
|   941 | `src/main/java/datingapp/storage/schema/MigrationRunner.java`        |
|   882 | `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`            |
|   840 | `src/main/java/datingapp/ui/screen/ChatController.java`              |
|   839 | `src/main/java/datingapp/ui/screen/MatchesController.java`           |
|   816 | `src/main/java/datingapp/app/cli/MatchingHandler.java`               |
|   810 | `src/main/java/datingapp/core/model/User.java`                       |
|   788 | `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`       |
|   725 | `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`    |
|   725 | `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` |
|   714 | `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`          |
|   711 | `src/main/java/datingapp/core/AppConfig.java`                        |
|   697 | `src/main/java/datingapp/storage/schema/SchemaInitializer.java`      |
|   678 | `src/main/java/datingapp/ui/screen/MatchingController.java`          |
|   670 | `src/main/java/datingapp/core/matching/TrustSafetyService.java`      |
|   641 | `src/main/java/datingapp/core/profile/MatchPreferences.java`         |
|   612 | `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`        |
|   586 | `src/main/java/datingapp/core/connection/ConnectionService.java`     |
|   577 | `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`         |
|   481 | `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`      |

### Interpretation

Size clustering still strongly supports the main narrative:

- app orchestration,
- storage transactions,
- UI state and controller glue,
- central runtime/config seams,
- a few broad core interaction boundaries.

## Appendix B — Longest test-source Java files

| Lines | File                                                                         |
|------:|------------------------------------------------------------------------------|
|  1273 | `src/test/java/datingapp/core/testutil/TestStorages.java`                    |
|   969 | `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`          |
|   921 | `src/test/java/datingapp/storage/jdbi/SqlRowReadersTest.java`                |
|   841 | `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`             |
|   808 | `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`       |
|   748 | `src/test/java/datingapp/core/MatchingServiceTest.java`                      |
|   682 | `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`                |
|   677 | `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java` |
|   670 | `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`                 |
|   668 | `src/test/java/datingapp/core/UserTest.java`                                 |
|   639 | `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`             |
|   612 | `src/test/java/datingapp/core/MessagingServiceTest.java`                     |
|   599 | `src/test/java/datingapp/core/SwipeSessionTest.java`                         |
|   596 | `src/test/java/datingapp/core/storage/StorageContractTest.java`              |
|   585 | `src/test/java/datingapp/core/MatchQualityServiceTest.java`                  |
|   562 | `src/test/java/datingapp/app/usecase/matching/MatchingUseCasesTest.java`     |
|   549 | `src/test/java/datingapp/core/TrustSafetyServiceTest.java`                   |
|   542 | `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`         |
|   539 | `src/test/java/datingapp/core/ValidationServiceTest.java`                    |
|   528 | `src/test/java/datingapp/app/cli/SafetyHandlerTest.java`                     |

### Interpretation

The test suite continues to confirm the same structural hotspots:

- broad route families,
- broad service seams,
- wide storage contracts,
- large UI coordination seams,
- large shared test infrastructure.

---

## AI-agent execution notes

If an AI coding agent uses this document as a work queue, these rules should guide execution:

1. **Optimize for leverage, not file size alone.**
2. **Preserve good extractions that already exist.** Extend them instead of inventing a parallel abstraction.
3. **Delete compatibility scaffolding after migration.** Do not let old and new boundaries coexist forever.
4. **Split command/query and transition seams before polishing outer adapters.**
5. **Refactor test fixtures alongside seam cleanup.** Otherwise the old shape survives in tests.
6. **Use composition roots as final simplification steps, not first moves.**
7. **Treat lint-clean output as neutral.** It neither proves structural health nor reduces the need for seam cleanup.

### Good first future implementation candidates

- split and clarify the `MatchingService` + `CandidateFinder` seam
- split and clarify `ConnectionService` + `TrustSafetyService` responsibilities
- simplify `MatchingUseCases` and `SocialUseCases` after the underlying core seams are cleaner
- isolate `JdbiAccountCleanupStorage` from the rest of the persistence graph
- continue the existing helper extraction direction inside `ProfileViewModel`, `ChatViewModel`, `MatchesViewModel`, and `MatchingViewModel`

### Bad first future implementation candidates

- route/controller beautification first
- `RestApiServer` breakup before app seams are cleaner
- `ViewModelFactory` rewrite first
- early flag-day `User` redesign
- treating `RecommendationService`, `StorageFactory`, or `DevDataSeeder` as the main crisis simply because they are large

---

## Final conclusion

After a second full pass, the codebase still does **not** look like “one terrible subsystem.”

It looks like a mature feature set that evolved through multiple cleanup waves, where the biggest remaining debt is:

> **unfinished simplification of the most central coordination seams**

The dominant lesson from the second pass is more precise than the first-pass wording:

> **keep the extractions that already happened, finish the ones that are half-done, and delete the compatibility scaffolding that no longer earns its keep.**

The biggest wins will come from simplifying:

- **behavior seams** (`MatchingService`, `ConnectionService`, `TrustSafetyService`),
- **application bundles** (`MatchingUseCases`, `SocialUseCases`, `DashboardUseCases`),
- **transactional repository seams** (`JdbiAccountCleanupStorage`, `JdbiMatchmakingStorage`, `JdbiConnectionStorage`, `JdbiUserStorage`),
- and **stateful UI seams** (`ProfileViewModel`, `ChatViewModel`, `MatchesViewModel`, `MatchingViewModel`).

That is where the current source says the next cleanup effort will pay off most.