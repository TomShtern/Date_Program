# 2026-04-01 Sequential Java Code Quality Report

> **Scope:** full Java audit across `src/main/java` and `src/test/java`
>
> **Scan order:** `Main.java` → `app/**` → `core/**` → `storage/**` → `ui/**` → `src/test/java/**`
>
> **Methods used:** targeted source reads, parallel read-only subagents, `tokei`, `rg`, and `sg`
>
> **Goal:** identify the most valuable ways to simplify, shorten, harden, and improve the codebase without wasting time on cosmetic nits

## Executive summary

The project already has a solid layered architecture and a serious test suite. That is the good news.

The bad news is that several layers are carrying **too much responsibility per class**, and a few important rules are duplicated in multiple places:

- time and timezone behavior is not enforced tightly enough,
- route identity/authorization logic is split across multiple REST classes,
- several CLI/UI/ViewModel classes are genuine “god objects”,
- storage code has some obvious batching and migration-hardening opportunities,
- architecture tests are valuable but some are disabled, text-based, or too narrow.

The biggest near-term value is **not** adding more features first. It is tightening the foundation so future features land on simpler, safer seams.

## How this audit was performed

This review was intentionally evidence-driven.

- I scanned the Java codebase in package order.
- I used **multiple parallel subagents** to audit the app, core, storage, UI, and test/architecture surfaces independently.
- I then spot-checked the highest-risk source files directly, including:
  - `src/main/java/datingapp/Main.java`
  - `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
  - `src/main/java/datingapp/app/api/RestApiServer.java`
  - `src/main/java/datingapp/core/AppClock.java`
  - `src/main/java/datingapp/core/matching/CandidateFinder.java`
  - `src/main/java/datingapp/core/matching/DefaultDailyLimitService.java`
  - `src/main/java/datingapp/ui/ImageCache.java`
  - `src/main/java/datingapp/ui/UiComponents.java`
  - `src/main/java/datingapp/storage/DatabaseManager.java`
  - `src/main/java/datingapp/storage/schema/MigrationRunner.java`
  - `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
  - `src/test/java/datingapp/architecture/ArchitectureTestSupport.java`
  - `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java`
- I used `tokei` for size/volume metrics, `rg` for direct-pattern searches, and `sg` for structural searches.

## Codebase snapshot

### Scale

| Metric                |  Value |
|-----------------------|-------:|
| Production Java files |    152 |
| Test Java files       |    174 |
| Total Java files      |    326 |
| Total Java lines      | 96,745 |
| Code lines            | 77,984 |
| Comment lines         |  5,014 |
| Blank lines           | 13,747 |

### Largest production files by line count

| File                                                               | Lines |
|--------------------------------------------------------------------|------:|
| `src/main/java/datingapp/ui/screen/ProfileController.java`         |  1389 |
| `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`       |  1189 |
| `src/main/java/datingapp/storage/DevDataSeeder.java`               |  1175 |
| `src/main/java/datingapp/app/api/RestApiServer.java`               |  1164 |
| `src/main/java/datingapp/app/cli/ProfileHandler.java`              |  1121 |
| `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`          |   966 |
| `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`        |   952 |
| `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java` |   951 |
| `src/main/java/datingapp/ui/screen/MatchesController.java`         |   814 |
| `src/main/java/datingapp/app/cli/MatchingHandler.java`             |   813 |
| `src/main/java/datingapp/core/model/User.java`                     |   812 |
| `src/main/java/datingapp/ui/screen/ChatController.java`            |   795 |

### Repository-wide structural signals

| Signal                                               | Count | Notes                                                    |
|------------------------------------------------------|------:|----------------------------------------------------------|
| `catch (Exception ...)` in `src/main/java`           |   113 | Heavy concentration in use cases and viewmodels          |
| `catch (Throwable ...)` in `src/main/java`           |     1 | In `Main.java` static startup path                       |
| `synchronized (...)` in `src/main/java`              |    21 | Cache/storage/metrics/UI state hotspots                  |
| `Optional.ofNullable(...).orElse(...)` exact pattern |     1 | `RestApiServer`                                          |
| `Instant.now(` in production Java                    |     6 | Time-policy drift signal                                 |
| `LocalDate.now(` in production Java                  |    11 | Time-policy drift signal                                 |
| `ZoneId.systemDefault(` in production Java           |    14 | Most worrying from a determinism/time-policy perspective |

### Overall interpretation

The codebase is **architecturally better than average**, but it is now large enough that a few overgrown seams are starting to dominate maintenance cost:

- composition roots,
- large adapter/controller/viewmodel classes,
- repeated policy logic,
- repeated storage mapping/batching patterns,
- architecture tests that need to become more structural and less textual.

## What should happen next

If I were sequencing the next major cleanup pass, I would do it in this order:

1. **Correctness and policy hardening first**
   - time/timezone policy,
   - REST identity/authorization centralization,
   - candidate cache invalidation,
   - migration safety.
2. **Modularize oversized app-layer adapters**
   - `RestApiServer`, `Main`, large CLI handlers.
3. **Shrink UI god objects**
   - `ProfileViewModel`, `ChatViewModel`, `MatchingViewModel`, `ProfileController`.
4. **Reduce storage complexity and needless work**
   - normalized data hydration, duplicate SQL fragments, per-row batch loops.
5. **Strengthen guardrail tests**
   - re-enable time-policy guard, replace line scans with structural checks, widen contract coverage.

## Intent-based classification

> **How to use this section:** this is now the main decision layer for the report.
>
> The grouping below reflects the roadmap in `2026-03-29-dating-app-roadmap-design.md` **plus** the clarifying answers gathered in this review session. The raw numbered findings remain below as a reference appendix, but the categories here are the working truth for what we should do, defer, rewrite, or ignore.

### Valid

These are valid, appropriate for the project direction, and should stay in the active backlog.

#### Core / REST / semantics

- **13** — Candidate cache invalidation (`active matching correctness`)
- **14** — Daily-limit timezone correctness (`core behavior consistency`)
- **15** — Centralize profile-completeness rules (`supports active ProfileCompletion work`)
- **17** — Unify swipe eligibility (`protects active semantic bug fixes`)
- **23** — Align `LocationService` advertised support (`matches current unsupported-location problem`)
- **53** — Require acting user for conversation message reads (`API semantics, not generic hardening`)
- **57** — Improve event-handler failure visibility (`event plumbing stays active`)
- **58** — Type event payloads (`reduces string-contract drift in active event flows`)
- **60** — Use configured timezone for daily picks (`same correctness family as daily limits`)
- **65** — Remove `systemDefault()` fallback overloads (`explicitly in scope now`)
- **66** — Tighten `User.StorageBuilder` location invariant (`latent correctness hole worth closing`)

#### Storage / data / test fidelity

- **27** — Extract generic normalized-collection writer (`high-value internal cleanup in active storage seam`)
- **48** — Shrink `TestStorages` / improve fake-vs-real parity (`important for reliable Phase 1 testing`)
- **49** — Reduce shadow composition in `RestApiTestFixture` (`REST consistency matters now`)
- **51** — Reduce sleep/poll-heavy UI tests (`test determinism matters now`)
- **52** — Expand storage contract coverage (`protects parity and hidden regressions`)
- **67** — `DatabaseManager` global mutable state cleanup (`shared-state cleanup stays active broadly`)
- **68** — True isolation for `buildInMemory(...)` (`important for tests and future migration work`)
- **69** — `DevDataSeeder` full-seed completion robustness (`development reliability still matters`)
- **70** — Type/stricten `JdbiUserStorage` normalized hydration (`active storage correctness seam`)
- **71** — Remove `ThreadLocal` ambient handle state (`active internal cleanup in a sensitive storage path`)
- **76** — Rename/scope `RestApiRoutesTest` correctly (`misleading test scope is worth fixing`)
- **77** — Add/clarify real `Main` lifecycle coverage (`lifecycle correctness stays active`)

#### JavaFX / UX / lifecycle

- **33** — Remove image loading from inside the `ImageCache` lock (`can cause visible UI stalls`)
- **35** — Split `ProfileViewModel` (`profile journey is active and this class is overloaded`)
- **36** — Split `ChatViewModel` (`messaging state/lifecycle tangle is real`)
- **37** — Split `MatchingViewModel` (`matching flow is core UX, not vanity cleanup`)
- **41** — Extract subcontrollers from `ProfileController` (`profile editing is active roadmap work`)
- **42** — Extract create-account flow from `LoginController` (`signup/onboarding matters now`)
- **44** — Extract shared photo-carousel state (`small, reusable, flow-relevant cleanup`)
- **72** — Track delayed achievement transitions in `DashboardController` (`navigation/lifecycle correctness`)
- **73** — Cancel `MilestonePopupController` auto-dismiss timer (`real UI lifecycle bug`)
- **74** — Track `NEW` badge animation cleanup (`untracked infinite animation is real cleanup debt`)
- **75** — Clear `NavigationService` history on logout (`session-boundary correctness`)

### Valid but Not appropriate

These claims are technically sound, but they do **not** fit the current phase, current priorities, or current product direction well enough to keep active now.

#### App / core / refactor-first items

- **2** — Fail-fast malformed config (`good idea, but narrower and lower-priority than current semantic work`)
- **3** — Thin `Main.java` launcher (`worthwhile, but not a roadmap unlock right now`)
- **5** — Reduce broad `catch (Exception)` usage (`too repo-wide for the current phase`)
- **6** — Split `MatchingUseCases` (`valid maintainability work, but not current-phase critical`)
- **7** — Split `ProfileHandler` (`CLI matters, but broad handler surgery is not automatically valuable now`)
- **8** — Split `MatchingHandler` (`same story: only if it helps active work directly`)
- **9** — Split `MessagingHandler` (`valid cleanup, but not the current messaging bottleneck`)
- **10** — Isolate `SocialUseCases` compatibility path (`good cleanup, wrong time`)
- **12** — Validate conversation ID inputs consistently (`nice contract improvement, not a roadmap driver`)
- **16** — Snapshot achievement metrics once (`achievements are not a strong active target right now`)
- **18** — Unify dealbreaker pass/fail explanation logic (`valid consistency work, but secondary`)
- **19** — Align validation contracts (`correct but too broad for current focus`)
- **20** — Optimize standout generation (`not a current roadmap priority`)
- **22** — Refactor `ServiceRegistry` (`architectural debt, not an immediate unlock`)
- **54** — Treat localhost as process boundary, not auth (`valid, but intentionally out of scope for now`)
- **55** — Health/readiness endpoint polish (`ops semantics are not a current focus`)
- **56** — Bootstrap rollback on partial init failure (`good engineering, but not current-phase priority`)
- **59** — Candidate cache size cap / eviction sweep (`secondary to real cache correctness concerns`)
- **61** — Split `ConnectionService` / normalize contracts (`generic restructuring is not the first move`)
- **62** — Batch total unread count (`valid performance cleanup, but not current-phase critical`)
- **63** — Batch stats snapshot computation (`surface ActivityMetrics first; optimize internals later`)
- **64** — Publish achievement unlock events (`valid seam, but not a current product target`)

#### Storage / schema / tests

- **25** — Remove duplicated/misnamed indexes (`real schema hygiene, but not active now`)
- **28** — Batch standout persistence (`write-path optimization without current pain evidence`)
- **29** — Consolidate storage JSON/mapping helpers (`useful cleanup, but not urgent`)
- **31** — Reduce repeated SQL fragments in `JdbiConnectionStorage` (`maintainability cleanup, not now`)
- **32** — Avoid per-borrow query timeout setup (`micro-optimization territory`)
- **45** — Re-enable time-policy architecture guard (`good later; not a current active item`)
- **50** — Split `SchemaInitializerTest` (`high-friction test cleanup, but not a current blocker`)

#### JavaFX / optional cleanup

- **38** — Remove FX toolkit probing from `MatchesViewModel` (`small smell, weak product payoff right now`)
- **43** — Unify UI validation surfaces (`current drift evidence is too weak for an active item`)

### Valid claim but Bad approach

These are **real issues**, but the current wording or proposed implementation direction is semantically wrong, too broad, or mismatched to the project.

#### App / core

- **1** — REST scoping/identity centralization (`real issue, but generic auth-metadata refactor is too broad`)
- **4** — `RestApiServer` cleanup (`real transport-root problem, but route modules already exist`)
- **11** — Replace global `AppClock` (`underlying time issue is real, but timezone semantics matter before a full clock migration`)
- **21** — Split `MatchQualityService` (`surface the feature first; deeper refactor can come later`)

#### Storage / tests

- **24** — Migration/bootstrap hardening (`real issue, but should be framed as failed-init recovery, not generic transaction-safe DDL`)
- **26** — Replace `JdbiUserStorage` `UNION ALL` hydration (`real complexity issue, but the fix should be evidence-driven, not assumed`)
- **30** — Validate atomic storage branches (`real invariant concern, but “exact row counts for every branch” is too blunt`)
- **46** — Replace line-based architecture scans (`real only for the remaining raw-line checks; import checks are already structural`)
- **47** — Expand spot-check architecture tests (`real weakness, but scope should stay roadmap-protective, not maximalist`)

#### JavaFX

- **34** — Replace raw virtual-thread UI helpers (`real lifecycle issue, but the fix should be a cancellable lifecycle-aware loader API, not necessarily “route through BaseViewModel”`)
- **39** — Shrink `NavigationService` (`real overloaded service, but needs concrete fixes rather than a vague “god object” ticket`)
- **40** — Break up `ViewModelFactory` wiring (`real noise concern, but “decentralize the factory” is the wrong framing`)

### Invalid

At this point, after the clarification pass, **no numbered findings are fully invalid**.

The issues were not “made up”; the main problem was usually one of **fit**, **priority**, or **approach** rather than factual accuracy.

---

## Raw audit findings (reference appendix)

Below are **77 numbered findings** in their original audit form. Keep them as the evidence-backed raw source, but use the classification section above as the actual working filter.

## `Main.java` and `app/**`

### 1. Centralize REST identity and authorization metadata
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/app/api/RestApiRequestGuards.java`, `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`
- **Evidence:** request requirements are split between path-string checks and separate identity/path validation logic.
- **Why it matters:** new endpoints can easily be exempted or validated incorrectly.
- **Fix direction:** attach identity/auth requirements to route definitions or a single route metadata object and enforce them from one place.

### 2. Fail fast on malformed configuration instead of silently continuing with defaults
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- **Evidence:** `applyJsonConfig(...)` logs JSON parse failure and leaves unread fields on defaults.
- **Why it matters:** a broken config can boot the app with the wrong runtime behavior while hiding the real problem.
- **Fix direction:** make malformed config fatal by default; keep lenient fallback only behind explicit dev/test behavior.

### 3. Strip `Main.java` down to a thin launcher
- **Severity:** Important
- **Files:** `src/main/java/datingapp/Main.java`
- **Evidence:** the file owns console encoding, startup wiring, menu loop, rendering, and shutdown behavior.
- **Why it matters:** entrypoints should be boring; this one is doing composition and control flow work that belongs elsewhere.
- **Fix direction:** move CLI-loop orchestration into a dedicated runner and isolate Windows console setup into a helper.

### 4. Break `RestApiServer` into route-specific modules with thinner handlers
- **Severity:** Important
- **Files:** `src/main/java/datingapp/app/api/RestApiServer.java`
- **Evidence:** the class contains route registration, request parsing, DTO mapping, use-case calls, and exception translation across many domains.
- **Why it matters:** this is already a transport-layer god class and will keep getting worse if left alone.
- **Fix direction:** extract per-domain route controllers plus shared response helpers; keep `RestApiServer` as boot/wiring only.

### 5. Reduce broad `catch (Exception)` usage in use-case and adapter code
- **Severity:** Important
- **Files:** notable hotspots include `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`, `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`, `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- **Evidence:** `sg` found 113 `catch (Exception)` blocks in production code, with heavy concentration in app/usecase and UI layers.
- **Why it matters:** broad catches hide failure contracts and make troubleshooting harder.
- **Fix direction:** catch narrower exceptions, map them once, and let unexpected failures bubble to centralized handlers.

### 6. Split `MatchingUseCases` into smaller workflow bundles
- **Severity:** Important
- **Files:** `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- **Evidence:** one class owns browse/swipe/undo/matches/pending-likers/standouts/match-quality/daily-status/like-removal flows.
- **Why it matters:** too many dependencies and too many unrelated responsibilities in one use-case façade.
- **Fix direction:** split into browse+swipe, match queries, standout/recommendation, and moderation/archive oriented slices.

### 7. Split `ProfileHandler` by user journey
- **Severity:** Important
- **Files:** `src/main/java/datingapp/app/cli/ProfileHandler.java`
- **Evidence:** account creation, selection, completion, notes, dealbreakers, preview, and score display are all mixed into one large CLI handler.
- **Why it matters:** it is hard to test and easy to break unrelated flows.
- **Fix direction:** separate account lifecycle, profile editing, notes, and profile insights into smaller handlers/presenters.

### 8. Split `MatchingHandler` into feature-focused CLI handlers
- **Severity:** Important
- **Files:** `src/main/java/datingapp/app/cli/MatchingHandler.java`
- **Evidence:** the class mixes candidate browsing, requests, standouts, notifications, “who liked me”, and match views; the dependency record is huge.
- **Why it matters:** it is a CLI mega-handler and a merge-conflict magnet.
- **Fix direction:** split by feature area and move rendering concerns into presenter helpers.

### 9. Separate conversation-list logic from conversation-session logic in `MessagingHandler`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/app/cli/MessagingHandler.java`
- **Evidence:** list pagination, interactive chat commands, and relationship side-effects live in the same stateful flow.
- **Why it matters:** inbox behavior and per-thread command handling have different state models.
- **Fix direction:** create a small inbox controller plus a thread-session controller with explicit command parsing.

### 10. Remove or isolate compatibility/no-op wiring paths from `SocialUseCases`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- **Evidence:** compatibility constructors, nullable dependencies, and a compatibility no-op event bus leave partially-wired behavior possible.
- **Why it matters:** hidden fallback paths produce late surprises.
- **Fix direction:** keep one real production constructor and move legacy compatibility behavior into a separate adapter layer.

## `core/**`

### 11. Replace the global mutable `AppClock` with explicit time injection in core services
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/core/AppClock.java` plus services reading it directly
- **Evidence:** `AppClock` stores a JVM-wide mutable static `Clock` and exposes `setFixed`, `setClock`, and `reset`.
- **Why it matters:** test cross-talk and hidden global state are a long-term maintenance tax.
- **Fix direction:** inject `Clock`/time providers into services and keep `AppClock` as a minimal bridge at bootstrap/test boundaries only.

### 12. Validate conversation ID inputs consistently
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/connection/ConnectionModels.java`, `src/main/java/datingapp/core/connection/ConnectionService.java`
- **Evidence:** `Conversation.generateId(UUID a, UUID b)` assumes non-null inputs; service paths can reach it before null validation.
- **Why it matters:** callers get low-signal `NullPointerException` behavior instead of explicit contract failures.
- **Fix direction:** validate IDs early and make the contract explicit through result types or defensive checks.

### 13. Strengthen candidate-cache invalidation strategy
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/core/matching/CandidateFinder.java`
- **Evidence:** cache entries live for 5 minutes and are keyed mostly by seeker state plus exclusion set; candidate-side state changes are not part of invalidation.
- **Why it matters:** stale recommendations can survive block/unblock, profile changes, or moderation state changes.
- **Fix direction:** invalidate via relevant domain events or shorten/remove the cache until stronger invalidation exists.

### 14. Use configured timezone for daily-limit boundaries
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/core/matching/DefaultDailyLimitService.java`
- **Evidence:** `getStartOfToday()` and `getResetTime()` use `clock.getZone()` instead of the configured user/app timezone.
- **Why it matters:** quotas can reset at the wrong local day boundary.
- **Fix direction:** use explicit configured timezone input for all daily-limit boundary calculations.

### 15. Centralize profile-completeness rules
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/model/User.java`, `src/main/java/datingapp/core/workflow/ProfileActivationPolicy.java`, `src/main/java/datingapp/core/profile/ProfileCompletionSupport.java`, `src/main/java/datingapp/core/metrics/DefaultAchievementService.java`
- **Evidence:** completion-like logic exists in multiple places.
- **Why it matters:** activation, completion UI, and achievements can drift.
- **Fix direction:** define one profile-completeness policy/schema and reuse it everywhere.

### 16. Snapshot metrics once per request in `DefaultAchievementService`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/metrics/DefaultAchievementService.java`
- **Evidence:** achievement checks and progress calculations repeatedly re-read counts.
- **Why it matters:** unnecessary storage work and noisier logic.
- **Fix direction:** compute a single per-user stats snapshot and feed it into achievement evaluators.

### 17. Unify swipe eligibility into one evaluator
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/matching/MatchingService.java`
- **Evidence:** eligibility checks are repeated across pre-checks, locked paths, and direct-like helpers.
- **Why it matters:** duplicated rules drift.
- **Fix direction:** return a structured eligibility result with a reason code and reuse it in all swipe flows.

### 18. Collapse duplicated dealbreaker pass/fail logic into one evaluation result
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/profile/MatchPreferences.java`
- **Evidence:** filtering logic and explanation logic for height/age are implemented separately.
- **Why it matters:** explanations can diverge from actual filter behavior.
- **Fix direction:** evaluate once and return both verdict and reason details from the same computation.

### 19. Make validation contracts consistent across service and model layers
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/profile/ValidationService.java`, `src/main/java/datingapp/core/model/User.java`, `src/main/java/datingapp/core/model/ProfileNote.java`
- **Evidence:** some validation helpers treat `null` as acceptable while model constructors/setters reject it.
- **Why it matters:** “validated” data can still fail later at the model boundary.
- **Fix direction:** align validation helpers with domain invariants or move validation responsibility more clearly to one boundary.

### 20. Reduce repeated work in standout generation
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/matching/DefaultStandoutService.java`
- **Evidence:** per-candidate profile completeness calculations and multi-day recent standout scans are done repeatedly.
- **Why it matters:** it scales poorly and obscures the real standout-selection logic.
- **Fix direction:** batch/cache the expensive parts and add a storage method that returns recent standout IDs in one query.

### 21. Split `MatchQualityService` into loading, scoring, and presentation stages
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/matching/MatchQualityService.java`
- **Evidence:** the service loads users, computes measures, assigns labels, and builds display text in one path, with sentinel values for missing data.
- **Why it matters:** mixed responsibilities and magic-value contracts are error-prone.
- **Fix direction:** model missing measurements explicitly and separate raw scoring from presentation formatting.

### 22. Refactor `ServiceRegistry` into smaller assembly stages
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/ServiceRegistry.java`
- **Evidence:** the composition root wires a very large matrix of services, storages, use cases, and compatibility paths.
- **Why it matters:** wiring bugs become harder to see and harder to test.
- **Fix direction:** split assembly into per-domain builders/factories while keeping one top-level registry facade.

### 23. Align `LocationService`’s advertised capabilities with actual data support
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/profile/LocationService.java`
- **Evidence:** country lists are broader than the real lookup/ZIP data support.
- **Why it matters:** the UI can present options that are not truly supported.
- **Fix direction:** either expose only supported countries or implement country-specific providers incrementally.

## `storage/**`

### 24. Make schema bootstrap and migration execution transaction-safe or recovery-safe
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/storage/DatabaseManager.java`, `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- **Evidence:** `initializeSchema()` runs all pending migrations on a normal pooled connection without an explicit overall recovery strategy.
- **Why it matters:** partial migrations are among the most painful bugs to unwind.
- **Fix direction:** use explicit transaction/recovery handling for upgrade runs and reset/close the pool on initialization failure.

### 25. Remove duplicated and confusing index definitions
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/schema/SchemaInitializer.java`, `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- **Evidence:** overlapping message/conversation indexes and confusing naming exist between fresh-install DDL and migration steps.
- **Why it matters:** duplicate indexes increase write cost and maintenance complexity.
- **Fix direction:** define ownership of each index in one place and normalize names to match their real table.

### 26. Replace the normalized-profile `UNION ALL` mega-query in `JdbiUserStorage`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- **Evidence:** `loadNormalizedProfileData(...)` merges photos/interests/interested-in/dealbreakers in one large `UNION ALL` query and reconstructs everything in Java.
- **Why it matters:** hot read paths pay too much complexity and query cost.
- **Fix direction:** load each normalized table with batched `IN (...)` queries and merge in memory via reusable helpers.

### 27. Extract a generic normalized-collection writer in `JdbiUserStorage`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- **Evidence:** photo/interests/interested-in/dealbreaker writes all follow the same delete-then-batch-insert pattern.
- **Why it matters:** duplicated persistence plumbing is easy to drift.
- **Fix direction:** introduce a reusable normalized-collection writer helper.

### 28. Batch standout persistence instead of upserting one row at a time
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`
- **Evidence:** standout saving loops row by row inside a transaction.
- **Why it matters:** obvious write-path inefficiency.
- **Fix direction:** use prepared batches or bulk upsert patterns.

### 29. Consolidate shared JSON codec and row-mapping helpers
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`, `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`, related mappers
- **Evidence:** notification JSON serialization and row-reading patterns are duplicated.
- **Why it matters:** serialization drift is subtle and expensive.
- **Fix direction:** move JSON encode/decode and small row-reader helpers into shared storage codec utilities.

### 30. Validate every branch of “atomic” storage operations
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- **Evidence:** some atomic transition/undo paths check one updated row but not every dependent row.
- **Why it matters:** a method can report success while only half the intended mutation actually applied.
- **Fix direction:** require exact affected-row counts for every branch that participates in an atomic operation.

### 31. Reduce repeated SQL fragments in `JdbiConnectionStorage`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- **Evidence:** similar visibility/deletion joins and batch-map logic appear in multiple message/conversation queries.
- **Why it matters:** future rule changes can easily hit one query but miss another.
- **Fix direction:** centralize shared predicate fragments and batch-result helpers.

### 32. Avoid per-borrow `SET QUERY_TIMEOUT` if the pool or driver can do it once
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/DatabaseManager.java`
- **Evidence:** `getConnection()` calls `applySessionQueryTimeout(connection)` for each borrowed connection.
- **Why it matters:** small overhead on every storage call path accumulates.
- **Fix direction:** prefer datasource/connection-init configuration if supported; otherwise document why per-borrow is unavoidable.

## `ui/**`

### 33. Remove image loading from inside the `ImageCache` global lock
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/ui/ImageCache.java`
- **Evidence:** `getImage(...)` enters `synchronized (CACHE)` and calls `loadImage(...)` before returning.
- **Why it matters:** slow IO/decode work can block unrelated cache readers and preloaders.
- **Fix direction:** load outside the lock and only synchronize the final cache mutation, ideally with per-key in-flight coalescing.

### 34. Replace raw virtual-thread UI helpers with the shared async model
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/ui/UiComponents.java`
- **Evidence:** `SkeletonLoader.loadWithSkeleton(...)` and `loadDataWithSkeleton(...)` use `Thread.ofVirtual().start(...)` and `Platform.runLater(...)` directly.
- **Why it matters:** work can outlive the screen lifecycle and update detached UI.
- **Fix direction:** route this through `BaseViewModel`/`ViewModelAsyncScope`/dispatcher abstractions and return a cancellable handle.

### 35. Split `ProfileViewModel` into form, photo, and persistence responsibilities
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- **Evidence:** the viewmodel manages draft state, validation, save orchestration, photo flows, preview generation, dirty tracking, and normalization.
- **Why it matters:** the class is too large to evolve safely.
- **Fix direction:** extract photo/gallery coordination and form-state/persistence helpers.

### 36. Split `ChatViewModel` by conversation list, thread state, and private notes
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- **Evidence:** one viewmodel owns polling, send flow, presence, typing state, selected conversation behavior, and note CRUD.
- **Why it matters:** the async lifecycle is harder to reason about when unrelated concerns are mixed.
- **Fix direction:** create smaller state objects or sub-viewmodels for conversation list, active thread, and notes.

### 37. Separate swipe flow from note/photo helpers in `MatchingViewModel`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
- **Evidence:** browsing, swipe processing, undo handling, match notifications, note loading/saving, and photo navigation all live together.
- **Why it matters:** this mixes different lifecycles and failure modes.
- **Fix direction:** extract candidate browsing/swipe state and candidate-note/photo state into separate components.

### 38. Remove environment probing from `MatchesViewModel`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- **Evidence:** the viewmodel combines refresh/pagination logic with FX toolkit availability probing.
- **Why it matters:** data-loading viewmodels should not own runtime-environment detection.
- **Fix direction:** move toolkit/runtime checks into UI infrastructure and keep the viewmodel data-focused.

### 39. Shrink `NavigationService`, which behaves like a UI god object
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/NavigationService.java`
- **Evidence:** it owns stage setup, theme application, history, transitions, controller cleanup, and notification/toast style concerns.
- **Why it matters:** too much hidden global UI behavior collects in one place.
- **Fix direction:** split navigation history/context, stage shell/theme handling, and cleanup coordination.

### 40. Break up the central wiring matrix in `ViewModelFactory`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- **Evidence:** controller factories and screen/viewmodel wiring are highly centralized.
- **Why it matters:** adding screens or adjusting dependencies creates unnecessary coupling and conflicts.
- **Fix direction:** organize wiring by feature or screen module instead of one giant registry-style class.

### 41. Extract reusable subcontrollers from `ProfileController`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/screen/ProfileController.java`
- **Evidence:** form setup, validation binding, photo navigation, accessibility setup, and dirty tracking live in one very large controller.
- **Why it matters:** this is one of the largest production files in the repo.
- **Fix direction:** extract photo carousel, preference binding, and validation setup into reusable components/helpers.

### 42. Move account-creation dialog construction out of `LoginController`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/screen/LoginController.java`
- **Evidence:** `showCreateAccountDialog()` builds a full embedded form inline.
- **Why it matters:** the login controller is carrying an entire second UI flow.
- **Fix direction:** convert the dialog into a separate builder/controller/form component.

### 43. Unify UI validation rules and feedback plumbing
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/UiFeedbackService.java`, `src/main/java/datingapp/ui/screen/ProfileFormValidator.java`
- **Evidence:** one layer handles generic validation helpers and another handles profile-specific validation with a separate approach.
- **Why it matters:** rules and messages can drift.
- **Fix direction:** define one validation result model and let feedback rendering be purely presentational.

### 44. Extract shared photo-carousel state from multiple viewmodels
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`, `ProfileReadOnlyViewModel.java`, `MatchingViewModel.java`
- **Evidence:** multiple viewmodels independently manage photo lists, current index, and next/previous logic.
- **Why it matters:** duplicated carousel behavior will diverge over time.
- **Fix direction:** move index management and URL normalization into one reusable photo-gallery state helper.

## `src/test/java/**`

### 45. Re-enable a real time-policy architecture guard
- **Severity:** Critical
- **Files:** `src/test/java/datingapp/architecture/TimePolicyArchitectureTest.java`
- **Evidence:** `noFeatureCodeUsesZoneIdSystemDefault()` is explicitly `@Disabled` while production still contains runtime timezone lookups.
- **Why it matters:** this is a known architectural policy with no active enforcement.
- **Fix direction:** introduce the missing time-policy abstraction or re-enable a narrower active guard with a small exception list.

### 46. Replace line-based architecture scans with structural parsing/matching
- **Severity:** Important
- **Files:** `src/test/java/datingapp/architecture/ArchitectureTestSupport.java`
- **Evidence:** `collectViolations(...)` scans raw lines and relies on text matching.
- **Why it matters:** formatting and comment structure can produce brittle false positives/negatives.
- **Fix direction:** use syntax-aware checks end-to-end, ideally with structural parsing or `sg`-style patterns.

### 47. Expand architecture checks from spot-checks to package-wide contracts
- **Severity:** Important
- **Files:** `src/test/java/datingapp/ui/viewmodel/ViewModelArchitectureConsistencyTest.java`, related architecture tests
- **Evidence:** some tests assert architecture only for a few concrete classes rather than the entire package surface.
- **Why it matters:** narrow guardrails create false confidence.
- **Fix direction:** reflectively scan concrete viewmodels/controllers/handlers and assert package-wide invariants.

### 48. Shrink `TestStorages`, which is becoming a second storage implementation
- **Severity:** Important
- **Files:** `src/test/java/datingapp/core/testutil/TestStorages.java`
- **Evidence:** it reimplements significant storage behavior, including pagination and transition semantics.
- **Why it matters:** complex fakes drift from production behavior.
- **Fix direction:** keep fakes small and dumb, and shift real behavior guarantees into contract tests against production-backed implementations.

### 49. Make REST test wiring mirror production composition more closely
- **Severity:** Important
- **Files:** `src/test/java/datingapp/app/api/RestApiTestFixture.java`
- **Evidence:** the fixture manually wires a large dependency graph and in-memory replacements.
- **Why it matters:** it is effectively a shadow composition root.
- **Fix direction:** reuse `ServiceRegistry.Builder` more directly and keep only explicit test overrides separate.

### 50. Split `SchemaInitializerTest` into smaller migration-focused files
- **Severity:** Important
- **Files:** `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
- **Evidence:** it is long, deeply coupled to migration text, and expensive to update.
- **Why it matters:** high-value tests should not also be high-friction tests.
- **Fix direction:** separate fresh-schema assertions from specific migration-behavior assertions and extract repeated setup helpers.

### 51. Replace sleep/poll-heavy UI tests with more deterministic hooks
- **Severity:** Important
- **Files:** `src/test/java/datingapp/ui/async/UiAsyncTestSupport.java`, `ViewModelAsyncScopeTest`, `ChatViewModelTest`, `ViewModelFactoryTest`
- **Evidence:** several tests rely on polling, sleep helpers, and wall-clock timeouts.
- **Why it matters:** that invites flakes and slows local feedback.
- **Fix direction:** prefer synchronous/deterministic dispatchers and explicit latches/state hooks over repeated waiting.

### 52. Expand storage contract coverage beyond today’s partial surface
- **Severity:** Important
- **Files:** `src/test/java/datingapp/core/storage/StorageContractTest.java`
- **Evidence:** some defaults and boundary behaviors are covered, but the contract surface is much wider.
- **Why it matters:** default-interface regressions can slip through quietly.
- **Fix direction:** add table-driven contract suites for paging, null/empty handling, unread counts, and default-method behavior relied on by production code.

## Second-pass corrections and additional observations

### Corrections and refinements to earlier findings

- The structural-signal counts near the top of the report are **triage indicators, not direct defect counts**. Some matches are genuine problems, while others are expected defaults or presentation-layer formatting paths.
- Findings **1** and **4** need narrower wording: `RestApiServer` already has inner route modules, so the real problem is that **acting-user scoping rules and handler logic remain centralized in the outer transport root**.
- Finding **2** should be read narrowly: **unknown config keys already fail fast**; the remaining leniency is JSON parse/deserialization fallback and config-path fallback behavior.
- Finding **24** is strongest as a **failed-bootstrap recovery and datasource-cleanup problem**, not merely “wrap every migration in one transaction,” because DDL transaction semantics are engine-specific.
- Finding **46** should be narrowed: import-boundary checks are already Javac/AST-based; the remaining issue is the subset of architecture rules that still rely on raw line scanning.

### Additional observations from the second pass

### 53. Require an acting user for conversation message reads
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/app/api/RestApiServer.java`, `src/main/java/datingapp/app/api/RestApiRequestGuards.java`, `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`
- **Evidence:** `GET /api/conversations/{conversationId}/messages` only verifies participation when `X-User-Id` is present. Without that header, `RestApiServer.getMessages(...)` infers the participants directly from `conversationId` and proceeds.
- **Why it matters:** any local process that can derive or guess a valid conversation ID can read that thread history.
- **Fix direction:** require an acting user for conversation message reads and validate membership server-side instead of trusting the path alone.

### 54. Treat localhost binding as a process boundary, not as user authentication
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/app/api/RestApiRequestGuards.java`, `src/main/java/datingapp/app/api/RestApiServer.java`
- **Evidence:** `requiresActingUserIdentity(...)` only enforces identity for `POST`, `PUT`, and `DELETE`. Sensitive user-scoped `GET` routes such as matches, notifications, stats, achievements, conversations, and profile notes remain anonymously readable to any local caller.
- **Why it matters:** “localhost only” still exposes data to other local processes, browser extensions, scripts, or malware on the same machine.
- **Fix direction:** explicitly classify public vs privileged reads and require `X-User-Id` on all sensitive user-scoped `GET` routes.

### 55. `/api/health` is liveness-only and bypasses the project time policy
- **Severity:** Important
- **Files:** `src/main/java/datingapp/app/api/RestApiServer.java`
- **Evidence:** `HealthRoutes.register()` returns `new HealthResponse("ok", System.currentTimeMillis())` and checks nothing about the database, service graph, or scheduler.
- **Why it matters:** a partially broken app can still report healthy, and the endpoint introduces a direct wall-clock call outside the normal time abstractions.
- **Fix direction:** separate liveness and readiness, and either use `AppClock` for timestamps or omit the timestamp if it is not semantically important.

### 56. Startup failure can leave partial global state behind
- **Severity:** Important
- **Files:** `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- **Evidence:** `initialize(AppConfig)` assigns `dbManager` and `services` before seeding/scheduler startup finishes, while `initialized` is only flipped at the end.
- **Why it matters:** a mid-startup failure can leave allocated resources and partially assigned singletons hanging around for the rest of the JVM lifetime.
- **Fix direction:** wrap bootstrap in a rollback-aware failure path that shuts down partial state and clears static fields before rethrowing.

### 57. Event-handler failures are easy to lose in production logs
- **Severity:** Important
- **Files:** `src/main/java/datingapp/app/event/InProcessAppEventBus.java`
- **Evidence:** BEST_EFFORT handlers only log `e.getMessage()` and suppress the full throwable unless the handler is `REQUIRED`.
- **Why it matters:** notifications, metrics, and achievement side-effects can silently stop working while the main business action still succeeds.
- **Fix direction:** log the full exception for BEST_EFFORT failures and consider metrics/alerts or retries for high-value handlers.

### 58. Event payloads are still stringly typed across handlers
- **Severity:** Important
- **Files:** `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`, `src/main/java/datingapp/app/event/handlers/MetricsEventHandler.java`
- **Evidence:** `NotificationEventHandler.onRelationshipTransitioned(...)` switches on raw state strings, and `MetricsEventHandler.onSwipeRecorded(...)` reconstructs `Like.Direction` via `valueOf(event.direction())`.
- **Why it matters:** payload spelling drift becomes a runtime-only failure instead of a compile-time one.
- **Fix direction:** publish typed enums in event records or centralize string-to-enum conversion before fan-out.

### 59. `CandidateFinder` has TTL-based freshness but no size cap or eviction sweep
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/matching/CandidateFinder.java`
- **Evidence:** the cache is a plain `ConcurrentHashMap` with TTL checks only on lookup; there is no maximum size and no background cleanup.
- **Why it matters:** stale entries for one-off seekers can linger indefinitely until those users search again.
- **Fix direction:** add an upper bound plus active eviction, or replace the bespoke cache with a bounded cache abstraction.

### 60. `DefaultDailyPickService` uses clock-local dates instead of the configured timezone
- **Severity:** Critical
- **Files:** `src/main/java/datingapp/core/matching/DefaultDailyPickService.java`
- **Evidence:** `getDailyPick(...)`, `hasViewedDailyPick(...)`, and `markDailyPickViewed(...)` use `LocalDate.now(clock)` instead of `config.safety().userTimeZone()`.
- **Why it matters:** “today’s pick” can shift at a different midnight boundary than daily likes, standouts, and user-facing dates.
- **Fix direction:** compute daily-pick dates from the configured application/user timezone, not from the clock’s implicit zone.

### 61. `ConnectionService` is a mega-service with mixed error contracts
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/connection/ConnectionService.java`
- **Evidence:** the class combines messaging, unread counts, conversation lifecycle, friend requests, graceful exits, and unmatching. Its methods mix `null`, `Optional`, booleans, result records, and thrown exceptions.
- **Why it matters:** the API is harder to use correctly and harder to evolve without surprising callers.
- **Fix direction:** split messaging from relationship-transition concerns and normalize the public result/error contract.

### 62. `ConnectionService.getTotalUnreadCount(...)` has an avoidable N+1 query pattern
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/connection/ConnectionService.java`
- **Evidence:** it loads all conversations and then calls `calculateUnreadCount(...)` per conversation instead of using `countUnreadMessagesByConversationIds(...)` in one batch.
- **Why it matters:** this is a common aggregate read and will scale badly as conversation counts rise.
- **Fix direction:** batch unread counting by conversation ID and only fall back to per-conversation logic when strictly necessary.

### 63. `ActivityMetricsService.computeAndSaveStats(...)` builds one snapshot from many separate reads
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`
- **Evidence:** likes, passes, received swipes, matches, blocks, reports, reciprocity, and platform averages are gathered via multiple independent storage calls.
- **Why it matters:** the method is slower than necessary and can assemble a slightly inconsistent “now” view if data changes mid-computation.
- **Fix direction:** introduce a batched analytics snapshot query or storage-side aggregate object for stats computation.

### 64. `DefaultAchievementService` has no publication seam for newly unlocked achievements
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/metrics/DefaultAchievementService.java`
- **Evidence:** `checkAndUnlock(...)` persists achievements and returns a list, but nothing publishes an event or callback when an unlock happens.
- **Why it matters:** notifications, analytics, or live UI feedback either have to poll or duplicate unlock logic.
- **Fix direction:** publish a dedicated achievement-unlocked event or otherwise expose a first-class unlock hook.

### 65. Deprecated `MatchPreferences` overloads still default to `ZoneId.systemDefault()`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/profile/MatchPreferences.java`
- **Evidence:** deprecated `passes(...)` and `getFailedDealbreakers(...)` overloads still use `ZoneId.systemDefault()` internally.
- **Why it matters:** the old API preserves an implicit timezone contract that can quietly disagree with the configured application timezone.
- **Fix direction:** remove or aggressively quarantine the deprecated overloads after migrating the remaining callers.

### 66. `User.StorageBuilder` can reconstitute inconsistent location state
- **Severity:** Important
- **Files:** `src/main/java/datingapp/core/model/User.java`
- **Evidence:** `StorageBuilder.location(lat, lon)` and `StorageBuilder.hasLocationSet(boolean)` are separate setters, so callers can build a user with coordinates present but `hasLocationSet == false`.
- **Why it matters:** reconstitution correctness depends on every loader remembering to set two coupled fields coherently.
- **Fix direction:** collapse those setters into one coherent storage-facing location contract.

### 67. `DatabaseManager` is process-global mutable state
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/DatabaseManager.java`
- **Evidence:** `jdbcUrl` is static and mutable, `getInstance()` is a singleton, and `resetInstance()` does not restore the JDBC URL to its default.
- **Why it matters:** test and runtime behavior becomes order-dependent across a shared JVM.
- **Fix direction:** eliminate static mutable configuration where possible, or reset every global field as part of instance teardown.

### 68. `StorageFactory.buildInMemory(...)` is not actually isolated
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/StorageFactory.java`
- **Evidence:** `buildInMemory(...)` delegates straight to `DatabaseManager.getInstance()` rather than constructing an isolated in-memory database manager.
- **Why it matters:** “in-memory” service graphs still depend on shared singleton database state and prior configuration order.
- **Fix direction:** make `buildInMemory(...)` create an isolated H2 in-memory instance with no shared singleton state.

### 69. `DevDataSeeder` can freeze a partial dataset forever
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/DevDataSeeder.java`
- **Evidence:** seeding is considered complete once the sentinel user exists, and that sentinel is inserted before the rest of the user and match data.
- **Why it matters:** a crash or failure after the sentinel insert can leave the database permanently half-seeded because later runs will skip the remainder.
- **Fix direction:** use a stronger completion marker or verify the full seed set before skipping.

### 70. `JdbiUserStorage.loadNormalizedProfileData(...)` is stringly typed and silently permissive
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- **Evidence:** the hydration path routes rows by string values like `"photos"`, `"interests"`, and `"dealbreaker"`, and unknown groups are ignored.
- **Why it matters:** typos or partial schema extensions can fail quietly instead of loudly.
- **Fix direction:** replace string group names with typed descriptors or table-specific loaders that fail fast on unexpected shapes.

### 71. `JdbiUserStorage` uses ambient transaction state via `ThreadLocal`
- **Severity:** Important
- **Files:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- **Evidence:** `executeWithUserLock(...)` stores a `Handle` in `lockedHandle`, and `save()`/`get()` change behavior when that ambient state is present.
- **Why it matters:** the class becomes harder to reason about, especially if nested or asynchronous access is introduced later.
- **Fix direction:** pass explicit handles or transactional contexts instead of relying on thread-local ambient state.

### 72. `DashboardController` leaves delayed achievement work untracked
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/screen/DashboardController.java`
- **Evidence:** `handleAchievementCelebration(...)` creates `PauseTransition` instances for confetti removal and popup staggering but does not track them through `BaseController` cleanup.
- **Why it matters:** delayed callbacks can still fire after navigation and mutate detached UI.
- **Fix direction:** track those transitions like other controller-owned animations and cancel them during cleanup.

### 73. `MilestonePopupController` does not cancel its auto-dismiss timer on manual close
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/screen/MilestonePopupController.java`
- **Evidence:** `showAchievement(...)` creates a local `PauseTransition` for auto-dismiss, but `close()` only stops confetti and glow animations.
- **Why it matters:** manual close can race with the delayed close path and trigger duplicate cleanup/fade-out work.
- **Fix direction:** store and cancel the auto-dismiss transition as part of the popup lifecycle.

### 74. `MatchesController` starts an untracked infinite animation for the “NEW” badge
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/screen/MatchesController.java`
- **Evidence:** `createMatchCard(...)` creates a repeating `ScaleTransition badgePulse` and starts it immediately without routing it through `trackAnimation(...)`.
- **Why it matters:** controller cleanup cannot stop animations it does not own.
- **Fix direction:** register badge animations with the controller’s tracked animation lifecycle.

### 75. Navigation history survives logout unless callers clear it manually
- **Severity:** Important
- **Files:** `src/main/java/datingapp/ui/NavigationService.java`, `src/main/java/datingapp/ui/screen/DashboardController.java`
- **Evidence:** `NavigationService` exposes `clearHistory()`, but `DashboardController.handleLogout()` resets the `ViewModelFactory` and navigates to login without clearing navigation history.
- **Why it matters:** back-navigation state can outlive the authenticated session boundary.
- **Fix direction:** clear navigation history as part of logout/reset flows or make login navigation implicitly reset history.

### 76. `RestApiRoutesTest` is DTO coverage, not route coverage
- **Severity:** Important
- **Files:** `src/test/java/datingapp/app/api/RestApiRoutesTest.java`
- **Evidence:** the file only tests DTO records such as `MessageDto`, `ErrorResponse`, `HealthResponse`, `UserSummary`, and `UserDetail`.
- **Why it matters:** the filename overstates real route coverage and can create false confidence during audit or maintenance work.
- **Fix direction:** rename the file to reflect DTO coverage or replace it with actual route-level tests.

### 77. `MainLifecycleTest` covers helper seams, not the full application lifecycle
- **Severity:** Important
- **Files:** `src/test/java/datingapp/MainLifecycleTest.java`
- **Evidence:** it tests `runWithShutdown(...)`, EOF handling, and status-line formatting, but does not exercise the real `Main.main(...)` startup/shutdown flow.
- **Why it matters:** the highest-risk lifecycle seam still lacks direct test pressure.
- **Fix direction:** add a focused lifecycle/integration test for the real main-entry bootstrap and shutdown path.

## Pre-classification audit prioritization (reference only)

> **Note:** this was the original raw-audit prioritization. The intent-based classification above now takes precedence when deciding what to keep active vs ignore for this project phase.

### Phase 1 — correctness hardening
1. Findings 1, 2, 11, 13, 14, 24, 45, 53, 54, 60
2. These reduce the biggest hidden-correctness risks.

### Phase 2 — shrink oversized orchestration points
1. Findings 3, 4, 6, 7, 8, 9, 22
2. This makes the rest of the cleanup much easier.

### Phase 3 — UI simplification
1. Findings 33, 34, 35, 36, 37, 41, 43, 44
2. Biggest developer-experience improvement for the desktop app.

### Phase 4 — storage simplification and performance
1. Findings 25, 26, 27, 28, 29, 30, 31, 32
2. Lower risk once the correctness work is underway.

### Phase 5 — guardrail strengthening
1. Findings 46, 47, 48, 49, 50, 51, 52
2. Prevents the same categories of drift from returning.

## Bottom line

The repo is **worth investing in**, because the architecture is already disciplined enough that cleanup work will compound. The next best move is not “add more stuff”; it is:

1. fix the policy seams,
2. split the god objects,
3. simplify storage hot paths,
4. make the guardrails real.

If I were picking the **first five tickets** from this report, I would start with:

1. require acting-user identity on conversation reads and other sensitive user-scoped `GET` routes,
2. route metadata-based REST identity enforcement,
3. fail-fast config loading plus bootstrap rollback on startup failure,
4. explicit timezone handling in daily limits and daily picks,
5. candidate-cache invalidation redesign.
