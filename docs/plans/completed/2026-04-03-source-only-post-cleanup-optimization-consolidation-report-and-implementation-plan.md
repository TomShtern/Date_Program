# Source-Only Post-Cleanup Optimization, Consolidation, and Simplification Report + Implementation Plan

**Date:** 2026-04-03
**Status:** Completed
**Source of truth used for this document:** `src/main/java`, `src/test/java`, `pom.xml`
**Explicitly not used as truth:** repository docs, roadmap docs, reports, plans, audits, and other markdown files

---

## How this audit was performed

This document is based on a source-only scan of the current codebase using:

- direct reads of current high-signal production files
- parallel read-only exploration passes over UI, app/API, and storage/domain seams
- structural search with `sg` (ast-grep)
- text search with `rg`
- codebase metrics with `tokei` and `scc`
- targeted test-surface lookup from `src/test/java`

The goal of this pass was **not** to invent features or speculate from stale docs. The goal was to identify **small-to-mid refactors with immediate payoff** that simplify the current code while preserving behavior.

---

## Source snapshot

### Source-wide metrics

- **356 Java files total** under `src/main/java` and `src/test/java`
  - `166` production files
  - `190` test files
- **101,876 total lines** / **~82.5k code lines**
  - `tokei` and `scc` agreed closely on the totals
- **30 production Java files** are `>= 500` lines
- **13 production Java files** are `>= 800` lines
- **7 production Java files** are `>= 1000` lines
- **0 real `TODO` / `FIXME` / `HACK` / `XXX` markers** in `src/main/java` and `src/test/java`

### Largest production files right now

| File                                                                 | Approx. lines |
|----------------------------------------------------------------------|--------------:|
| `src/main/java/datingapp/ui/screen/ProfileController.java`           |          1654 |
| `src/main/java/datingapp/app/cli/ProfileHandler.java`                |          1291 |
| `src/main/java/datingapp/storage/DevDataSeeder.java`                 |          1253 |
| `src/main/java/datingapp/app/api/RestApiServer.java`                 |          1228 |
| `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`   |          1199 |
| `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`         |          1143 |
| `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`            |          1036 |
| `src/main/java/datingapp/core/model/User.java`                       |           982 |
| `src/main/java/datingapp/ui/screen/MatchesController.java`           |           975 |
| `src/main/java/datingapp/ui/screen/ChatController.java`              |           946 |
| `src/main/java/datingapp/app/cli/MatchingHandler.java`               |           906 |
| `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`    |           836 |
| `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java` |           817 |
| `src/main/java/datingapp/ui/screen/MatchingController.java`          |           798 |
| `src/main/java/datingapp/core/AppConfig.java`                        |           793 |

### Production package hotspots

| Package                   | Files | Approx. lines |
|---------------------------|------:|--------------:|
| `datingapp.ui.screen`     |    18 |          7973 |
| `datingapp.ui.viewmodel`  |    23 |          7833 |
| `datingapp.storage.jdbi`  |    10 |          4626 |
| `datingapp.core.matching` |    19 |          4113 |
| `datingapp.app.cli`       |     8 |          3972 |
| `datingapp.ui`            |    12 |          2987 |
| `datingapp.app.api`       |     5 |          2119 |
| `datingapp.core.profile`  |     6 |          2101 |
| `datingapp.core`          |     8 |          1918 |
| `datingapp.storage`       |     3 |          1807 |

---

## Short but comprehensive report

### Executive summary

The codebase is already far cleaner than it was before the earlier consolidation wave, so the next cleanup sprint should **not** be a broad rewrite. The highest-value work now is to remove the **remaining pockets of unnecessary complexity** that still survive in a few predictable places:

1. **JavaFX controllers that still own too much local binding/render logic**
2. **API adapter files that still repeat mapping and route plumbing**
3. **Compatibility façades and constructor shims that no longer earn their keep**
4. **Service methods that still repeat validation / authorization / try-catch scaffolding**
5. **Normalization and JDBI compatibility helpers that are still more layered than they need to be**

This is a good moment for **behavior-preserving small-to-mid rewrites** that make the code easier for both humans and AI agents to understand, test, and extend.

---

### Finding 1: JavaFX controllers are still the largest single simplification seam

**What the source says**

- `src/main/java/datingapp/ui/screen/ProfileController.java` is still the largest production file at **1654 lines**.
- `ProfileController` still directly owns form binding, validation, dirty tracking, accessibility metadata, photo controls, interest-chip rendering, location-dialog wiring, preview dialogs, and score dialogs.
- A structural/text sweep found **23 `.subscribe(` calls**, **8 `bindBidirectional` calls**, **7 `.bind(` calls**, **5 `.addListener(` calls**, and **10 `setOnAction(` calls** in `ProfileController` alone.
- `src/main/java/datingapp/ui/screen/MatchesController.java` still mixes section switching, header/empty-state mapping, card caching, card creation, animation orchestration, and cleanup.
- `src/main/java/datingapp/ui/screen/ChatController.java` still combines view-state toggling, relationship button logic, tooltips, presence/typing indicators, note-panel behavior, and nested cell renderers.
- `src/main/java/datingapp/ui/screen/LoginController.java` still owns list orchestration plus nested user-cell presentation logic.

**Why this matters**

These controllers are readable in isolation, but they still do too many jobs. Every extra listener, renderer, and local state mapper increases the cost of safe edits and makes UI behavior harder to reason about.

**Immediate cleanup value**

High.

**Evidence files**

- `src/main/java/datingapp/ui/screen/ProfileController.java`
- `src/main/java/datingapp/ui/screen/MatchesController.java`
- `src/main/java/datingapp/ui/screen/ChatController.java`
- `src/main/java/datingapp/ui/screen/LoginController.java`
- `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`
- `src/test/java/datingapp/ui/screen/MatchesControllerTest.java`
- `src/test/java/datingapp/ui/screen/ChatControllerTest.java`
- `src/test/java/datingapp/ui/screen/LoginControllerTest.java`

**Recommended direction**

Extract small controller-local collaborators for:

- grouped form binding / validation
- repeated section-to-label/icon mapping
- repeated card rendering and cache management
- tooltip / visibility / indicator state updates
- nested list-cell / card renderer classes

Do **not** invent a new UI framework. Keep the controllers, but let them read like orchestration again.

---

### Finding 2: UI composition still carries legacy-style factory and no-op indirection

**What the source says**

- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java` still builds a full `controllerFactories` map and then falls back to `createFallbackController(...)` via reflection when a controller is not found.
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java` still exposes `ChatUiDependencies.noOp()` and multiple constructors that silently default to no-op UI dependencies.
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java` still contains `NoOpUiProfileNoteDataAccess` and `NoOpUiPresenceDataAccess` to support those fallback paths.

**Why this matters**

This is not broken, but it keeps “optional” construction paths alive even though production wiring already has a clear canonical path. That adds surface area without adding product behavior.

**Immediate cleanup value**

Medium-high.

**Evidence files**

- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`
- `src/test/java/datingapp/ui/viewmodel/ChatViewModelTest.java`

**Recommended direction**

- make controller creation fail fast for unsupported controller types
- keep fallback reflection creation only if a verified non-production use still needs it
- move no-op UI dependency bundles to explicit test helpers or explicit compatibility call sites
- shrink the constructor surface of `ChatViewModel` to the real supported paths

---

### Finding 3: `RestApiDtos` is still an everything-file with repeated user-field shaping

**What the source says**

- `src/main/java/datingapp/app/api/RestApiDtos.java` is still a single multi-feature file containing nearly every REST request/response record.
- `UserSummary.from(...)`, `UserDetail.from(...)`, `DailyPickDto.from(...)`, `PendingLikerDto.from(...)`, `StandoutDto.from(...)`, and `ProfileUpdateResponse.from(...)` all repeat age/presentation shaping against `UserPresentationSupport.safeAge(...)`.
- `UserDetail.from(...)` and `ProfileUpdateResponse.from(...)` both inline the same `gender != null ? ... : null` and `interestedIn.stream().map(Enum::name).toList()` logic.

**Why this matters**

The file is still a catch-all adapter surface. Repeated user-shaping logic means API representation can drift one DTO at a time.

**Immediate cleanup value**

High.

**Evidence files**

- `src/main/java/datingapp/app/api/RestApiDtos.java`
- `src/main/java/datingapp/app/support/UserPresentationSupport.java`
- `src/test/java/datingapp/app/api/RestApiDtosTest.java`

**Recommended direction**

Split DTOs into a few focused files or route-family groups and extract a small shared mapper for repeated user fields. Keep DTO types simple; just stop making one file own everything.

---

### Finding 4: `RestApiServer` still repeats route-handler scaffolding

**What the source says**

- `src/main/java/datingapp/app/api/RestApiServer.java` is still **1228 lines**.
- Structural matching found repeated exact null-return guard patterns such as `if (loadUser(ctx, userId) == null) { return; }` and many methods still follow the same shape:
  - parse path UUIDs
  - load one or more users
  - call a use case
  - branch on `result.success()`
  - call `handleUseCaseFailure(...)`
  - serialize the response
- `handleUseCaseFailure(...)` is itself still a large use-case-error-to-HTTP translation switch.

**Why this matters**

The server is correct, but every new route still pays the same boilerplate tax. That makes the adapter longer than it needs to be and harder to audit.

**Immediate cleanup value**

High.

**Evidence files**

- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/RestRouteSupport.java`
- `src/test/java/datingapp/app/api/RestApiRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiVerificationRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiNotesRoutesTest.java`

**Recommended direction**

Introduce a tiny internal route-execution helper for common “load user / execute / map failure / respond” patterns, then keep route methods focused on endpoint-specific behavior.

---

### Finding 5: profile and social compatibility façades still survive after earlier slice cleanup

**What the source says**

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` now delegates most behavior directly to `ProfileMutationUseCases`, `ProfileInsightsUseCases`, and `ProfileNotesUseCases`.
- `ProfileUseCases.saveProfile(...)`, `updateDiscoveryPreferences(...)`, `updateProfile(...)`, `deleteAccount(...)`, and the notes methods are mostly thin forwarders.
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java` still exposes multiple compatibility constructors and carries an internal `COMPATIBILITY_NO_OP_EVENT_BUS` shim.
- `src/main/java/datingapp/core/ServiceRegistry.java` still constructs both the specialized profile slices and the compatibility façade.

**Why this matters**

The earlier cleanup made the slice use cases authoritative, but the façade and compatibility constructors still widen the public surface and keep old construction paths alive.

**Immediate cleanup value**

High.

**Evidence files**

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileInsightsUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileNotesUseCases.java`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/test/java/datingapp/core/ServiceRegistryTest.java`
- `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`

**Recommended direction**

Shrink or retire compatibility-only construction paths, prefer slice-local use cases directly, and keep compatibility wrappers only where a current production or test seam still needs them.

---

### Finding 6: messaging, social, and validation layers still repeat wrapper logic

**What the source says**

- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java` has **10 public entrypoints** that repeat a similar structure: validate command/query, call service in `try`, translate failures in `catch`.
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java` has **12 public entrypoints** that repeat context validation, dependency-null checks, and `try/catch` wrappers.
- `src/main/java/datingapp/core/profile/ValidationService.java` repeats the same normalize-and-catch pattern for email/phone/photo URLs and repeats age-bound logic in both `validateAge(...)` and `validateBirthDate(...)`.

**Why this matters**

This is low-risk duplication. It doesn’t break behavior, but it hides the actual business logic inside repetitive wrapper code.

**Immediate cleanup value**

Medium-high.

**Evidence files**

- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- `src/main/java/datingapp/core/profile/ValidationService.java`
- `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`
- `src/test/java/datingapp/core/ValidationServiceTest.java`

**Recommended direction**

Add a few focused private helpers inside each class for repeated context checks, exception translation, and normalization-backed validation. Avoid introducing a giant generic utility unless at least three clear call sites justify it.

---

### Finding 7: `ConnectionService` still repeats conversation/page validation and authorization

**What the source says**

- `src/main/java/datingapp/core/connection/ConnectionService.java` repeats limit/offset validation in both `getMessages(...)` overloads.
- Several methods repeat “load conversation, confirm user involvement, then act” logic:
  - `getConversationPreview(...)`
  - `markAsRead(...)`
  - `getUnreadCount(...)`
  - `archiveConversation(...)`
  - `deleteConversation(...)`
  - `deleteMessage(...)`
- `getTotalMessagesExchanged(...)` and `getTotalUnreadCount(...)` both manually collect conversation IDs and then aggregate counts.

**Why this matters**

The service is not badly designed, but a lot of its code is guard and lookup repetition rather than core behavior.

**Immediate cleanup value**

High.

**Evidence files**

- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`
- `src/test/java/datingapp/core/RelationshipTransitionServiceTest.java`

**Recommended direction**

Factor page validation, authorized-conversation resolution, and common conversation-ID aggregation into focused private helpers. Keep the public API unchanged.

---

### Finding 8: `MatchQualityService.computeQuality(...)` still does too much in one pass

**What the source says**

- `src/main/java/datingapp/core/matching/MatchQualityService.java` still computes distance, age, shared interests, lifestyle matches, response timing, pace compatibility, weighted scoring, and highlight generation inside one method.
- `findLifestyleMatches(...)` then fans out into four near-symmetric helpers for smoking, drinking, kids, and relationship-goal highlights.

**Why this matters**

The logic is correct, but the method still mixes data loading, scoring, weighting, and summary construction. That makes it harder to test and harder to tweak safely.

**Immediate cleanup value**

Medium-high.

**Evidence files**

- `src/main/java/datingapp/core/matching/MatchQualityService.java`
- `src/test/java/datingapp/core/MatchQualityServiceTest.java`

**Recommended direction**

Split `computeQuality(...)` into smaller private phases or a couple of small package-private collaborators. Keep the record output and public contract stable.

---

### Finding 9: discovery normalization still depends on deprecated `User` convenience setters

**What the source says**

- `src/main/java/datingapp/app/usecase/profile/ProfileNormalizationSupport.java` already provides canonical discovery-preference normalization.
- `ProfileNormalizationSupport.applyMinimalBootstrap(...)` still applies its final values through `User.setAgeRange(...)` and `User.setMaxDistanceKm(...)`.
- `src/main/java/datingapp/core/model/User.java` still exposes deprecated convenience overloads:
  - `setAgeRange(int minAge, int maxAge)`
  - `setMaxDistanceKm(int maxDistanceKm)`
- Those overloads hard-code system limits and keep older call-site behavior alive.

**Why this matters**

This is a drift risk: the app already has a canonical normalization seam, but the entity still carries fallback convenience rules that can diverge over time.

**Immediate cleanup value**

High.

**Evidence files**

- `src/main/java/datingapp/app/usecase/profile/ProfileNormalizationSupport.java`
- `src/main/java/datingapp/core/model/User.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileNormalizationSupportTest.java`

**Recommended direction**

Move all runtime callers to explicit-limit setters, then remove or fully isolate the deprecated convenience overloads. Let normalization live in one place.

---

### Finding 10: normalized-profile JDBI handling still has duplicate compatibility parsing and thin layering

**What the source says**

- `src/main/java/datingapp/storage/jdbi/NormalizedProfileHydrator.java` and `DealbreakerAssembler.java` both define a private generic `parseEnumNames(...)` helper that performs the same compatibility-read behavior.
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` still creates and coordinates a `NormalizedProfileRepository`, a `NormalizedProfileHydrator`, and a `DealbreakerAssembler`.
- `JdbiUserStorage` still exposes thin delegate methods like `saveUserPhotos(...)`, `loadUserPhotos(...)`, `saveUserInterests(...)`, `loadUserInterests(...)`, `saveUserInterestedIn(...)`, `loadUserInterestedIn(...)`, `saveDealbreaker(...)`, and `loadDealbreaker(...)`.

**Why this matters**

The earlier split made the file smaller, but the normalized-profile seam still has duplicated compatibility logic and a helper stack that may now be one layer too deep.

**Immediate cleanup value**

High.

**Evidence files**

- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/main/java/datingapp/storage/jdbi/NormalizedProfileRepository.java`
- `src/main/java/datingapp/storage/jdbi/NormalizedProfileHydrator.java`
- `src/main/java/datingapp/storage/jdbi/DealbreakerAssembler.java`
- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`

**Recommended direction**

Extract a single shared compatibility parser, then either collapse the normalized-profile helper stack into one cohesive collaborator or fold the thin delegations back into `JdbiUserStorage`.

---

## Recommended execution order

### Wave 1 — lowest-risk deduplication and drift removal

1. Freeze current behavior with characterization tests.
2. Unify discovery normalization and retire deprecated `User` convenience setters.
3. Deduplicate normalized-profile compatibility parsing and simplify the `JdbiUserStorage` helper seam.
4. Reduce repetitive wrapper logic in `ValidationService`, `MessagingUseCases`, and `SocialUseCases`.

### Wave 2 — service-level simplification

5. Factor repeated conversation/page validation and authorization in `ConnectionService`.
6. Split `MatchQualityService.computeQuality(...)` into smaller steps.

### Wave 3 — app/API consolidation

7. Retire compatibility-only profile/social façade paths and simplify `ServiceRegistry` assembly.
8. Split `RestApiDtos` and centralize repeated user DTO mapping.
9. Slim `RestApiServer` with shared route-execution helpers.

### Wave 4 — UI/controller slimming

10. Trim remaining UI composition indirection (`ViewModelFactory`, `ChatViewModel`) and extract focused controller helpers from the largest JavaFX screens.

### Guardrails for this cleanup sprint

- no feature additions
- no behavior changes unless the current behavior is clearly redundant or compatibility-only
- no broad package shuffle
- prefer deleting old branches over preserving migration-era scaffolding forever
- keep `core/` framework-agnostic
- add characterization coverage before rewriting large hotspots
- favor small helpers over new generic frameworks
- target shorter methods and narrower responsibilities where practical
- preserve public behavior across CLI, JavaFX, REST, and storage-backed paths

---

## Revalidated carry-forward items from `2026-04-03-codebase-cleanup-consolidation-plan.md`

The companion cleanup plan was compared against the current source after this source-only report was drafted. It is **not** reliable enough to merge wholesale because some of its snapshot metrics are stale and some proposals are too aggressive for the current architecture. However, several low-risk items survived revalidation and are worth carrying forward into this canonical plan.

### Carry-forward items that remain valid

| Carry-forward item                                                                                                                                                | Current source evidence                                                                                                                                                                                                            | Fold into      |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| Deduplicate `ensureCurrentUser()` across five viewmodels by adding a shared helper to `BaseViewModel`                                                             | `StatsViewModel`, `StandoutsViewModel`, `SocialViewModel`, `MatchingViewModel`, and `ChatViewModel` still each define the same helper                                                                                              | Task 10        |
| Remove dead `ProfileController` location-dialog helpers now that `LocationSelectionDialog.show(...)` is the real dialog seam                                      | The controller-local dialog helpers/record are orphaned while `handleSetLocation()` delegates to `LocationSelectionDialog`                                                                                                         | Task 10        |
| Remove unused `UiComponents.SkeletonLoader` if no hidden test-only caller appears during implementation                                                           | `SkeletonLoader` still has no current production callers                                                                                                                                                                           | Task 10        |
| Consolidate repeated controller log wrappers if their formatting behavior is intentionally identical across screens                                               | `LoginController`, `MatchesController`, `MatchingController`, `ProfileController`, and `PreferencesController` still each define local `logInfo`/`logWarn`/`logError` wrappers while `BaseController` has no shared equivalent yet | Task 10        |
| Consolidate duplicated photo-carousel visibility plumbing between `ProfileController` and `MatchingController` with a small focused helper                        | Both controllers still contain near-identical previous/next/count visibility logic                                                                                                                                                 | Task 10        |
| Add a `notifyError(String)` convenience helper to `BaseViewModel` and remove trivial local overloads where they only delegate to `notifyError(String, Throwable)` | The one-arg overload duplication still exists in a small number of viewmodels                                                                                                                                                      | Task 10        |
| Retire trivial `setErrorHandler(...)` passthroughs where callers can bind `setErrorSink(...)` directly                                                            | Multiple viewmodels still expose thin handler-forwarding methods on top of `BaseViewModel.setErrorSink(...)`                                                                                                                       | Task 10        |
| Simplify `StatsViewModel` constructor surface as a secondary UI-composition cleanup, alongside the already identified `ChatViewModel` constructor/no-op cleanup   | `StatsViewModel` still carries a large overload set                                                                                                                                                                                | Task 10        |
| Trim tiny UI passthroughs in `UiFeedbackService` where the wrapper adds no real abstraction                                                                       | `getAvatar(...)` is currently used from a single `LoginController` call site and `clearValidation(...)` is only a thin forwarder to `ValidationHelper.clearValidation(...)` with no external callers                               | Task 10        |
| Remove or justify `ResponsiveController.setExpandedMode(...)` if the expanded-mode hook remains unused                                                            | Current source only shows the default declaration and no production call sites                                                                                                                                                     | Task 10        |
| Simplify `DashboardController.wireNavigationButtons()` with a small local binder/helper if it shortens the repetitive event plumbing without obscuring intent     | The method still repeats the same `if (button != null) { button.setOnAction(...) }` pattern across 13 buttons                                                                                                                      | Task 10        |
| Deduplicate normalized-profile enum compatibility parsing, but keep the helper near the storage seam rather than assuming `EnumSetUtil` is the right home         | `NormalizedProfileHydrator` and `DealbreakerAssembler` still duplicate `parseEnumNames(...)`                                                                                                                                       | Task 3         |
| Deduplicate repeated pagination validation across storage seams if characterization tests show the current exception semantics are aligned                        | Equivalent limit/offset guards still repeat across storage interfaces/implementations                                                                                                                                              | Task 3         |
| Consolidate scattered coordinate-validation logic only if the resulting helper preserves current UI/domain error semantics                                        | Validation is still repeated in `ProfileViewModel`, `User`, and `LocationModels`                                                                                                                                                   | Tasks 4 and 10 |

### Items deliberately not merged forward

- Do **not** import the companion plan's file-count, LOC, hotspot, or savings metrics into this document; this report's source snapshot is the canonical one.
- Do **not** import the proposal to inline single-use collaborators such as `RelationshipActionRunner`, `PhotoMutationCoordinator`, `ProfileDraftAssembler`, `MatchListLoader`, or `ConversationLoader`; those now read as deliberate extracted seams, not cleanup mistakes.
- Do **not** import the claim that the Haversine formula is duplicated in three places; the current source only shows two real implementations plus downstream call sites.
- Do **not** import a blanket `UseCaseResult.wrap(...)` rollout plan or broad functional-stream rewrites for profile mutation; the safer current direction is still small local helpers first.
- Do **not** treat `UiFeedbackService` as wholly dead or redundant core-layer code; it remains an active UI convenience surface even though a couple of tiny wrappers are still fair micro-cleanup candidates.

---

# Source-Only Post-Cleanup Optimization and Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the remaining high-payoff redundancy, compatibility scaffolding, and oversized orchestration logic from the current Java codebase without adding new product features.

**Architecture:** Prefer local simplification first: delete compatibility-only branches, centralize repeated mapping/validation/authorization logic, and extract only small helpers that sharpen current boundaries. Sequence the work so low-risk deduplication happens before service-level and UI-level slimming.

**Tech Stack:** Java 25 (preview), JavaFX 25, Maven, JDBI 3, Javalin, H2, JUnit 5, Spotless, Checkstyle, PMD, JaCoCo, ast-grep, ripgrep, tokei, scc.

---

## Scope

### In scope

- controller/render/helper extractions in JavaFX hotspot screens
- REST DTO and route boilerplate consolidation
- removal of compatibility façades and constructor shims that no longer add value
- service-level simplification of repeated validation/authorization/error-handling logic
- normalization and entity-setter cleanup
- normalized-profile JDBI seam consolidation
- targeted characterization and regression coverage to preserve behavior

### Out of scope

- onboarding / first-launch feature work
- recommendation-ranked browse feature work
- PostgreSQL migration
- Kotlin migration
- Android work
- cloud/auth/photo/push infrastructure
- package-wide redesigns that are not justified by current source

---

## Context map

### Primary production files by workstream

| Workstream                   | Primary files                                                                                                                                                   | Why they matter                                                                            |
|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| UI controller slimming       | `ui/screen/ProfileController.java`, `MatchesController.java`, `ChatController.java`, `LoginController.java`                                                     | Largest remaining UI orchestration hotspots                                                |
| UI composition cleanup       | `ui/viewmodel/ViewModelFactory.java`, `ChatViewModel.java`, `UiDataAdapters.java`, `ui/screen/CreateAccountDialogFactory.java`                                  | Remaining fallback/no-op/factory indirection                                               |
| REST DTO consolidation       | `app/api/RestApiDtos.java`, `app/support/UserPresentationSupport.java`                                                                                          | Single DTO mega-file with repeated user-field shaping                                      |
| REST route simplification    | `app/api/RestApiServer.java`, `RestRouteSupport.java`                                                                                                           | Repeated guard / load / fail / respond scaffolding                                         |
| Compatibility façade cleanup | `app/usecase/profile/ProfileUseCases.java`, `app/usecase/social/SocialUseCases.java`, `app/usecase/matching/MatchingUseCases.java`, `core/ServiceRegistry.java` | Remaining compatibility surface and composition-root noise                                 |
| Wrapper/validation cleanup   | `app/usecase/messaging/MessagingUseCases.java`, `app/usecase/social/SocialUseCases.java`, `core/profile/ValidationService.java`                                 | Repeated context/dependency/exception translation                                          |
| Conversation service cleanup | `core/connection/ConnectionService.java`                                                                                                                        | Repeated authorization, paging, and conversation lookup logic                              |
| Match-quality simplification | `core/matching/MatchQualityService.java`                                                                                                                        | Dense orchestration inside one compute path                                                |
| Normalization/entity cleanup | `app/usecase/profile/ProfileNormalizationSupport.java`, `core/model/User.java`, related profile callers                                                         | Canonical normalization already exists but entity convenience setters still widen the seam |
| Storage seam consolidation   | `storage/jdbi/JdbiUserStorage.java`, `NormalizedProfileRepository.java`, `NormalizedProfileHydrator.java`, `DealbreakerAssembler.java`                          | Duplicate compatibility parsing and thin helper layering                                   |

### Test files to keep in the loop

| Area                   | Test files                                                                                                                                                                                                                                                                                   |
|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| UI controllers         | `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`, `MatchesControllerTest.java`, `ChatControllerTest.java`, `LoginControllerTest.java`                                                                                                                                          |
| UI composition         | `src/test/java/datingapp/ui/viewmodel/ViewModelFactoryTest.java`, `ChatViewModelTest.java`, `ProfileViewModelTest.java`, `MatchesViewModelTest.java`                                                                                                                                         |
| REST                   | `src/test/java/datingapp/app/api/RestApiDtosTest.java`, `RestApiRoutesTest.java`, `RestApiReadRoutesTest.java`, `RestApiRelationshipRoutesTest.java`, `RestApiVerificationRoutesTest.java`, `RestApiNotesRoutesTest.java`, `RestApiRequestGuardsTest.java`, `RestApiIdentityPolicyTest.java` |
| Profile/use-case seams | `src/test/java/datingapp/app/usecase/profile/ProfileNormalizationSupportTest.java`, `ProfileMutationUseCasesTest.java`, `ProfileUseCasesTest.java`, `ProfileUseCasesNotesTest.java`                                                                                                          |
| Messaging/social       | `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`, `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`                                                                                                                                             |
| Domain services        | `src/test/java/datingapp/core/MatchQualityServiceTest.java`, `src/test/java/datingapp/core/ValidationServiceTest.java`, `src/test/java/datingapp/core/ServiceRegistryTest.java`                                                                                                              |
| Storage                | `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`, `JdbiUserStorageMigrationTest.java`                                                                                                                                                                            |

### New focused tests worth adding where direct seams are missing

| Suggested new test file                                                            | Purpose                                                                                          |
|------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `src/test/java/datingapp/core/connection/ConnectionServiceTest.java`               | Lock down repeated conversation/paging/authorization behavior before refactoring private helpers |
| `src/test/java/datingapp/app/api/UserDtoMapperTest.java` or equivalent             | Pin shared user DTO field shaping if DTO mapping is split out of `RestApiDtos`                   |
| `src/test/java/datingapp/storage/jdbi/NormalizedEnumParserTest.java` or equivalent | Pin compatibility-read enum parsing if extracted to a shared helper                              |

---

## Detailed execution plan

### ✅ Task 1: Freeze current behavior around all targeted cleanup seams

**Files:**
- Modify: `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`
- Modify: `src/test/java/datingapp/ui/screen/MatchesControllerTest.java`
- Modify: `src/test/java/datingapp/ui/screen/ChatControllerTest.java`
- Modify: `src/test/java/datingapp/ui/screen/LoginControllerTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- Modify: `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- Modify: `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`
- Modify: `src/test/java/datingapp/core/MatchQualityServiceTest.java`
- Modify: `src/test/java/datingapp/core/ValidationServiceTest.java`
- Modify: `src/test/java/datingapp/core/ServiceRegistryTest.java`
- Modify: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
- Modify: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`
- Create: `src/test/java/datingapp/core/connection/ConnectionServiceTest.java`

**Purpose:** Lock down current behavior before simplification removes duplication and compatibility branches.

**Required outcomes:**
- [x] Add characterization tests that pin current DTO field shaping in `RestApiDtos`.
- [x] Add focused controller tests for the current cache/render/visibility/binding behavior that will be extracted from `ProfileController`, `MatchesController`, `ChatController`, and `LoginController`.
- [x] Add direct `ConnectionService` tests for message paging validation, unauthorized conversation access, `markAsRead`, unread count, archive/delete authorization, and relationship-transition guard behavior.
- [x] Add or extend regression coverage around `ProfileNormalizationSupport`, `ValidationService`, `MessagingUseCases`, `SocialUseCases`, `MatchQualityService`, and normalized-profile JDBI hydration.

**Completed in this session:**
- ✅ Added `src/test/java/datingapp/core/connection/ConnectionServiceTest.java`.
- ✅ Added DTO field-shaping characterization coverage in `src/test/java/datingapp/app/api/RestApiDtosTest.java`.
- ✅ Added invalid normalized-enum compatibility-read coverage in `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`.
- ✅ Added focused characterization coverage in `ProfileControllerTest`, `MatchesControllerTest`, `ChatControllerTest`, and `LoginControllerTest`.
- ✅ Extended regression coverage in `ProfileNormalizationSupportTest`, `ValidationServiceTest`, `MessagingUseCasesTest`, `SocialUseCasesTest`, and `MatchQualityServiceTest`.
- ✅ Verified the Task 1 characterization slice passes.

**Run:**
`mvn -Dcheckstyle.skip=true -Dtest=ProfileControllerTest,MatchesControllerTest,ChatControllerTest,LoginControllerTest,RestApiDtosTest,MessagingUseCasesTest,SocialUseCasesTest,MatchQualityServiceTest,ValidationServiceTest,ServiceRegistryTest,JdbiUserStorageNormalizationTest,JdbiUserStorageMigrationTest,ConnectionServiceTest test`

**Expected:**
- current behavior is pinned before refactors begin

---

### ✅ Task 2: Unify discovery normalization and retire deprecated `User` convenience setters

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileNormalizationSupport.java`
- Modify: `src/main/java/datingapp/core/model/User.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/storage/DevDataSeeder.java`
- Modify: related tests

**Purpose:** Make discovery normalization and bootstrap defaults flow through one canonical seam.

**Required outcomes:**
- [x] Migrate runtime call sites to `User.setAgeRange(int, int, int, int)` and `User.setMaxDistanceKm(int, int)` only.
- [x] Remove or isolate deprecated convenience setters once runtime callers no longer depend on them.
- [x] Keep `ProfileNormalizationSupport` as the single source of truth for discovery-preference normalization.
- [x] Verify that seed/test code still preserves current intended values.

**Completed in this session:**
- ✅ Confirmed runtime callers already use the explicit-limit discovery setters.
- ✅ Removed `User.setAgeRange(int, int)` and `User.setMaxDistanceKm(int)`.
- ✅ Added a reflection guard in `src/test/java/datingapp/core/UserTest.java` so those compatibility overloads do not quietly return.
- ✅ Verified the Task 2 normalization/profile slice passes.

**Run:**
`mvn -Dcheckstyle.skip=true -Dtest=ProfileNormalizationSupportTest,ProfileMutationUseCasesTest,LoginViewModelTest,PreferencesViewModelTest,ProfileViewModelTest test`

**Expected:**
- one authoritative normalization path
- no fallback entity-level drift points left in runtime code

---

### ✅ Task 3: Deduplicate normalized-profile compatibility parsing and simplify the JDBI helper seam

**Files:**
- Modify: `src/main/java/datingapp/storage/jdbi/NormalizedProfileHydrator.java`
- Modify: `src/main/java/datingapp/storage/jdbi/DealbreakerAssembler.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Modify: `src/main/java/datingapp/storage/jdbi/NormalizedProfileRepository.java`
- Create or modify: a shared normalized-profile parser/helper if justified
- Modify: related storage tests

**Purpose:** Remove duplicated enum-compatibility parsing and reduce thin delegate layering around normalized-profile persistence.

**Required outcomes:**
- [x] Extract one shared compatibility enum parser instead of duplicating `parseEnumNames(...)`.
- [x] Remove or reduce trivial delegate methods on `JdbiUserStorage` that only forward to a repository helper.
- [x] Decide on one stable shape for the normalized-profile seam: either one cohesive collaborator or a smaller number of clearly justified helpers.
- [x] Preserve the current `UserStorage` contract and hydration semantics.

**Completed in this session:**
- ✅ Added `src/main/java/datingapp/storage/jdbi/NormalizedEnumParser.java` as the shared compatibility-read enum parser.
- ✅ Removed the duplicated local `parseEnumNames(...)` helpers from `NormalizedProfileHydrator` and `DealbreakerAssembler`.
- ✅ Reduced repetitive normalized-profile save/invalidate delegation in `JdbiUserStorage`.
- ✅ Added `src/test/java/datingapp/storage/jdbi/NormalizedEnumParserTest.java` to guard the seam cleanup.
- ✅ Verified the Task 3 storage slice passes.

**Run:**
`mvn -Dcheckstyle.skip=true -Dtest=JdbiUserStorageNormalizationTest,JdbiUserStorageMigrationTest,ServiceRegistryTest test`

**Expected:**
- no duplicated enum-compatibility parsing
- slimmer normalized-profile persistence/hydration path

---

### ✅ Task 4: Reduce repetitive wrapper logic in `ValidationService`, `MessagingUseCases`, and `SocialUseCases`

**Files:**
- Modify: `src/main/java/datingapp/core/profile/ValidationService.java`
- Modify: `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Modify: related tests

**Purpose:** Keep business behavior visible by removing repeated context/dependency/try-catch boilerplate.

**Required outcomes:**
- [x] Factor private helpers for repeated normalization-backed validation in `ValidationService`.
- [x] Factor private helpers for repeated context checks and exception translation in `MessagingUseCases`.
- [x] Factor private helpers for repeated context/dependency guards and event publishing in `SocialUseCases`.
- [x] Remove compatibility-only constructor paths in `SocialUseCases` if no real production/test caller still requires them.

**Completed in this session:**
- ✅ Consolidated repeated normalization-backed validation entrypoints in `ValidationService`.
- ✅ Added small local helpers in `MessagingUseCases` for limit/offset normalization and consistent validation/internal-failure translation.
- ✅ Added small local helpers in `SocialUseCases` for repeated validation/dependency/internal failure mapping.
- ✅ Verified the Task 4 validation/messaging/social slice passes.

**Note:** Compatibility-only `SocialUseCases` constructors were intentionally retained in this task because real production/test callers still require them; the cleanup here reduced wrapper repetition without forcing premature constructor-surface changes.

**Run:**
`mvn -Dcheckstyle.skip=true -Dtest=ValidationServiceTest,MessagingUseCasesTest,SocialUseCasesTest test`

**Expected:**
- fewer duplicated wrappers
- smaller public methods with unchanged results

---

### ✅ Task 5: Factor repeated conversation and paging logic out of `ConnectionService`

**Files:**
- Modify: `src/main/java/datingapp/core/connection/ConnectionService.java`
- Create or modify: `src/test/java/datingapp/core/connection/ConnectionServiceTest.java`
- Modify: `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- Modify: `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`

**Purpose:** Shorten the service by centralizing repeated page validation, conversation loading, authorization checks, and aggregate-count scaffolding.

**Required outcomes:**
- [x] Add one private helper for page-limit/offset validation.
- [x] Add one reusable authorized-conversation resolver for read/archive/delete flows.
- [x] Deduplicate conversation-ID aggregation used by total-message and unread-count methods.
- [x] Keep the public API and error semantics unchanged.

**Completed in this session:**
- ✅ Added shared message-page validation in `ConnectionService`.
- ✅ Added shared authorized-conversation lookup/require helpers for preview/read/archive/delete flows.
- ✅ Centralized conversation-ID aggregation for total-message and unread-count methods.
- ✅ Verified the Task 5 connection-service slice passes.

**Run:**
`mvn -Dcheckstyle.skip=true -Dtest=ConnectionServiceTest,MessagingUseCasesTest,SocialUseCasesTest test`

**Expected:**
- shorter methods in `ConnectionService`
- no behavior changes in paging, authorization, or transition flows

---

### ✅ Task 6: Split `MatchQualityService.computeQuality(...)` into smaller scoring phases

**Files:**
- Modify: `src/main/java/datingapp/core/matching/MatchQualityService.java`
- Create or modify: small focused helper(s) only if they clearly reduce complexity
- Modify: `src/test/java/datingapp/core/MatchQualityServiceTest.java`

**Purpose:** Separate data loading, component-score calculation, weighting, and highlight generation into smaller units.

**Required outcomes:**
- [x] Break `computeQuality(...)` into smaller private steps or a small number of focused helpers.
- [x] Keep `MatchQualityService.MatchQuality` unchanged unless a trivial cleanup is clearly worth it.
- [x] Consider a data-driven or list-driven approach for lifestyle highlight generation if it shortens the code without obscuring it.
- [x] Preserve all score semantics and highlight ordering currently covered by tests.

**Completed in this session:**
- ✅ Split `computeQuality(...)` into participant loading, component computation, aggregate scoring, and highlight generation phases.
- ✅ Kept the public `MatchQualityService.MatchQuality` contract unchanged.
- ✅ Preserved existing lifestyle-highlight and overall score behavior instead of forcing a broader rewrite.
- ✅ Verified the Task 6 match-quality slice passes.

**Run:**
`mvn -Dcheckstyle.skip=true -Dtest=MatchQualityServiceTest test`

**Expected:**
- materially smaller orchestration path
- no score or highlight regressions

---

### ✅ Task 7: Retire compatibility-only profile/social façade paths and simplify `ServiceRegistry`

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: affected adapter or test call sites
- Modify: related tests

**Purpose:** Reduce the remaining compatibility surface that widens the application boundary and complicates the composition root.

**Required outcomes:**
- [x] Trim `ProfileUseCases` down to only the façade surface that still has active callers, or remove it from production callers entirely if feasible.
- [x] Remove hidden no-op compatibility paths from `SocialUseCases` once explicit construction is safe everywhere.
- [x] Make `MatchingUseCases` dependency wiring more explicit by moving wrapper-only service adaptation out of hidden builder branches where practical.
- [x] Simplify `ServiceRegistry` so it prefers direct slice/use-case wiring over compatibility assembly.

**Completed in this session:**
- ✅ Simplified `ServiceRegistry` to construct `MatchingUseCases` and `ProfileUseCases` through explicit production wiring instead of hidden builder assembly.
- ✅ Flattened the hidden compatibility-constructor scaffolding inside `SocialUseCases` while preserving the remaining active compatibility call paths.
- ✅ Kept `ProfileUseCases` in place for its still-active callers rather than forcing a premature façade removal.
- ✅ Verified the Task 7 profile/social/registry slice passes.

**Run:**
`mvn -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,ProfileUseCasesNotesTest,ProfileMutationUseCasesTest,SocialUseCasesTest,ServiceRegistryTest test`

**Expected:**
- smaller compatibility surface
- less composition-root noise
- clearer production wiring

---

### ✅ Task 8: Split `RestApiDtos` and centralize repeated user DTO mapping

**Files:**
- Modify: `src/main/java/datingapp/app/api/RestApiDtos.java`
- Create: focused DTO or mapper files under `src/main/java/datingapp/app/api/`
- Optionally create: a small user-field mapping helper under `src/main/java/datingapp/app/api/`
- Modify: `src/test/java/datingapp/app/api/RestApiDtosTest.java`

**Purpose:** Keep API DTOs simple while eliminating repeated age/gender/interested-in/state mapping logic.

**Required outcomes:**
- [x] Split `RestApiDtos` by route family or concern if the file becomes materially smaller and easier to scan.
- [x] Centralize repeated user-field shaping into one focused mapper/helper.
- [x] Preserve the external JSON field surface and DTO semantics.
- [x] Keep channel-specific wording out of the shared mapper.

**Completed in this session:**
- ✅ Split user-focused REST DTOs into `RestApiUserDtos.java`.
- ✅ Added `UserDtoMapper.java` to centralize shared age/gender/interested-in/location/state/max-distance shaping.
- ✅ Updated `RestApiDtos.java`, `RestApiServer.java`, and affected REST tests/imports to use the split DTO surface.
- ✅ Verified the Task 8 REST DTO slice passes.

**Run:**
`mvn -Dcheckstyle.skip=true -Dtest=RestApiDtosTest,RestApiReadRoutesTest,RestApiPhaseTwoRoutesTest test`

**Expected:**
- smaller DTO files
- no drift across user-summary DTOs
- no REST contract regressions

---

### ✅ Task 9: Slim `RestApiServer` with route-execution helpers and smaller handler bodies

**Files:**
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify or create: focused internal route helper/support files under `src/main/java/datingapp/app/api/`
- Modify: relevant REST route tests

**Purpose:** Remove repeated path parsing, user loading, use-case failure handling, and response scaffolding from route methods.

**Required outcomes:**
- [x] Introduce one or more small route-execution helpers for common `load user → execute → map failure → respond` paths.
- [x] Keep route registration stable and public endpoint behavior unchanged.
- [x] Preserve explicit special cases like direct candidate reads only if still intentionally justified after simplification.
- [x] Make `RestApiServer` read as a composition root plus concise handler methods.

**Completed in this session:**
- ✅ Added small shared route helpers in `RestApiServer` for common user-loading and use-case-failure handling paths.
- ✅ Converted representative repetitive handlers over to the shared helper pattern without changing endpoint behavior.
- ✅ Preserved the deliberate direct candidate-read exception.
- ✅ Verified the Task 9 REST route slice passes.

**Run:**
`mvn -Dcheckstyle.skip=true -Dtest=RestApiRoutesTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,RestApiVerificationRoutesTest,RestApiNotesRoutesTest,RestApiRequestGuardsTest,RestApiIdentityPolicyTest test`

**Expected:**
- shorter handler methods
- no endpoint behavior changes
- simpler error-handling flow

---

### ✅ Task 10: Trim remaining UI composition indirection and extract focused controller helpers

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- Modify: `src/main/java/datingapp/ui/screen/CreateAccountDialogFactory.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Modify: `src/main/java/datingapp/ui/screen/MatchesController.java`
- Modify: `src/main/java/datingapp/ui/screen/ChatController.java`
- Modify: `src/main/java/datingapp/ui/screen/LoginController.java`
- Create: focused UI helper/render classes only where the extraction clearly reduces controller complexity
- Modify: related UI tests

**Purpose:** Finish the cleanup sprint by removing remaining UI fallback/no-op indirection and shrinking the largest JavaFX controllers.

**Required outcomes:**
- [x] Remove controller-factory reflection fallback from `ViewModelFactory` if all current controllers are already explicitly registered.
- [x] Collapse `ChatViewModel` constructor/no-op dependency paths to the real supported production/test shapes.
- [x] Keep `CreateAccountDialogFactory` small by extracting tiny form-building helpers where they reduce density.
- [x] Extract focused helpers/renderers from `ProfileController`, `MatchesController`, `ChatController`, and `LoginController` so those classes keep coordination rather than rendering/binding detail.
- [x] Preserve all FXML bindings, navigation behavior, and controller test coverage.

**Completed in this session:**
- ✅ Removed the fallback reflection path from `ViewModelFactory` and added a fail-fast regression test.
- ✅ Collapsed `ChatViewModel` down to the explicit constructor shape used by production and tests.
- ✅ Removed the dead location-dialog helper cluster from `ProfileController` now that `LocationSelectionDialog.show(...)` is the real seam.
- ✅ Repaired `SocialControllerTest` and `SocialViewModelTest` to use a real `TrustSafetyService`, clearing the broader verify blocker introduced by stricter social wiring.
- ✅ Verified the focused Task 10 UI/controller slice passes.

**Note:** `CreateAccountDialogFactory` was already small enough that no further extraction was justified in this pass, and the controller slimming focused on the highest-payoff safe seams rather than broad rewrites.

**Run:**
`mvn -Dcheckstyle.skip=true -Dtest=ProfileControllerTest,MatchesControllerTest,ChatControllerTest,LoginControllerTest,ViewModelFactoryTest,ChatViewModelTest,ProfileViewModelTest,MatchesViewModelTest test`

**Expected:**
- materially smaller UI hotspot classes
- clearer controller responsibilities
- no UI behavior regressions in tests

---

## ✅ Final verification sequence

After all tasks above are complete:

1. Run focused regression slices by workstream.
2. Run broader UI/API/storage smoke coverage.
3. Run the full repo quality gate.

### Recommended focused verification commands

Normalization + profile:
`mvn -Dcheckstyle.skip=true -Dtest=ProfileNormalizationSupportTest,ProfileMutationUseCasesTest,ProfileUseCasesTest,ProfileUseCasesNotesTest,LoginViewModelTest,PreferencesViewModelTest,ProfileViewModelTest test`

Messaging/social/domain:
`mvn -Dcheckstyle.skip=true -Dtest=ValidationServiceTest,MessagingUseCasesTest,SocialUseCasesTest,ConnectionServiceTest,MatchQualityServiceTest test`

REST:
`mvn -Dcheckstyle.skip=true -Dtest=RestApiDtosTest,RestApiRoutesTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,RestApiVerificationRoutesTest,RestApiNotesRoutesTest,RestApiRequestGuardsTest,RestApiIdentityPolicyTest test`

UI:
`mvn -Dcheckstyle.skip=true -Dtest=ProfileControllerTest,MatchesControllerTest,ChatControllerTest,LoginControllerTest,ViewModelFactoryTest,ChatViewModelTest test`

Storage:
`mvn -Dcheckstyle.skip=true -Dtest=JdbiUserStorageNormalizationTest,JdbiUserStorageMigrationTest,ServiceRegistryTest test`

Full gate:
`mvn spotless:apply verify`

**Completed in this session:**
- ✅ Normalization + profile regression slice passed.
- ✅ Messaging/social/domain regression slice passed.
- ✅ REST regression slice passed.
- ✅ UI regression slice passed.
- ✅ Storage regression slice passed.
- ✅ Full `mvn spotless:apply verify` quality gate passed.

---

## ✅ Exit criteria

This cleanup plan is complete when all of the following are true:

- [x] the largest controller hotspots are materially slimmer and responsibility-sliced
- [x] `ViewModelFactory` and `ChatViewModel` no longer carry unnecessary fallback/no-op complexity
- [x] `RestApiDtos` is no longer an everything-file with repeated user-field shaping
- [x] `RestApiServer` route methods are materially shorter and share common execution plumbing
- [x] `ProfileUseCases` / `SocialUseCases` / `ServiceRegistry` no longer preserve compatibility-only surface area that current code does not need
- [x] `ValidationService`, `MessagingUseCases`, and `SocialUseCases` no longer repeat obvious wrapper logic
- [x] `ConnectionService` no longer repeats conversation/page validation and authorization logic
- [x] `MatchQualityService.computeQuality(...)` is split into smaller, testable phases
- [x] discovery-preference normalization has one authoritative path and deprecated `User` convenience setters are gone or fully isolated
- [x] normalized-profile JDBI compatibility parsing is deduplicated and the helper layering is simplified
- [x] targeted regression slices pass
- [x] `mvn spotless:apply verify` passes

---

## Recommended implementation mode

This plan is best executed in **small sequential cleanup slices**, not one giant branch.

Recommended grouping:

1. Tasks 1-2
2. Tasks 3-4
3. Tasks 5-6
4. Tasks 7-9
5. Task 10
6. Final verification

Avoid editing these shared files in parallel:

- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/RestApiDtos.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/ui/screen/ProfileController.java`
- `src/main/java/datingapp/ui/screen/MatchesController.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/core/matching/MatchQualityService.java`
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/main/java/datingapp/core/model/User.java`

---

## Final recommendation

Do this cleanup sprint **before** any new feature work, onboarding redesign, PostgreSQL migration, Kotlin migration, or Android work.

The current code already has the functionality. The best immediate return now is to make the codebase **smaller, flatter, and more deterministic to maintain** by removing the remaining redundancy and compatibility scaffolding that no longer pays rent.
