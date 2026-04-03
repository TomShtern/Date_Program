# Source-Only Cleanup, Consolidation, and Simplification Report + Implementation Plan

**Date:** 2026-04-03
**Status:** Completed
**Source of truth used for this document:** `src/main/java`, `src/test/java`, `pom.xml` only
**Explicitly not used as truth:** repository docs, roadmap docs, plans, audits, and other markdown files

---

## How this audit was performed

This document is based on a source-only scan of the current codebase using:

- direct reads of high-signal production files
- syntax-aware search with `sg` (ast-grep)
- text search with `rg`
- codebase metrics with `tokei` and `scc`
- IDE problem scan on hotspot files
- source-only parallel exploration passes over duplication, hotspot size/complexity, and boundary smells

### Source snapshot

- **343 Java files** under `src/main/java` and `src/test/java`
- **101,639 total lines** / **~82.2k code lines** (`tokei` / `scc` agreed closely)
- **25 production Java files** are `>= 500` lines
- **13 production Java files** are `>= 800` lines
- **7 production Java files** are `>= 1000` lines
- **0 real `TODO` / `FIXME` / `HACK` / `XXX` markers** in `src/main/java` and `src/test/java`
- IDE problem scan on the main hotspot files found **no current compile/lint errors**

### Largest production files right now

| File                                                               | Approx. lines |
|--------------------------------------------------------------------|--------------:|
| `src/main/java/datingapp/ui/screen/ProfileController.java`         |          1449 |
| `src/main/java/datingapp/app/api/RestApiServer.java`               |          1235 |
| `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`       |          1179 |
| `src/main/java/datingapp/storage/DevDataSeeder.java`               |          1171 |
| `src/main/java/datingapp/app/cli/ProfileHandler.java`              |          1158 |
| `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java` |          1040 |
| `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`        |          1003 |
| `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`          |           966 |
| `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`       |           887 |
| `src/main/java/datingapp/ui/screen/ChatController.java`            |           840 |

---

## Short but comprehensive report

### Executive summary

The codebase is already functionally rich and well-tested, but the current cleanup opportunity is no longer about missing features. It is about **removing compatibility shells, shrinking oversized presentation/storage classes, eliminating repeated normalization/presentation code, and deleting seams that no longer pay rent**.

The strongest immediate-benefit cleanup work falls into five clusters:

1. **Profile application-layer contract debt** — the profile façade still re-exports slice contracts and keeps alias record types alive.
2. **Legacy fallback paths that production no longer uses** — several large ViewModels still carry dual or triple execution paths.
3. **Duplicated mapping/normalization logic** — user presentation, profile bootstrap defaults, and discovery preference normalization are repeated across layers.
4. **Monolithic adapter/storage classes** — `RestApiServer` and `JdbiUserStorage` still do too many different jobs.
5. **Dead or wrapper-only seams** — at least one composition-root export is unused, and one REST wrapper exists only to satisfy another wrapper.

This is a good moment for **small-to-mid refactors with immediate payoff**, not a large architectural rewrite.

---

### Finding 1: `ProfileUseCases` is still carrying compatibility-shell debt

**What the source says**

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` is mostly a compatibility façade over:
  - `ProfileMutationUseCases`
  - `ProfileNotesUseCases`
  - `ProfileInsightsUseCases`
- It still defines wrapper/alias record types such as:
  - `SaveProfileCommand`
  - `ProfileSaveResult`
  - `UpdateDiscoveryPreferencesCommand`
  - `UpdateProfileCommand`
  - `AchievementsQuery`
  - `AchievementSnapshot`
  - `StatsQuery`
  - `SessionSummaryQuery`
  - notes-related query/command records
- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java` still directly depends on `ProfileUseCases.*` command/result types.
- Syntax/text search showed **29 main-source matches** to these compatibility façade contracts.

**Why this matters**

This keeps the profile layer wider and more tangled than it needs to be. The compatibility shell is no longer just compatibility; it is part of the live contract surface.

**Immediate cleanup value**

High. Shrinking this seam will:

- reduce redundant record types
- make slice use cases truly authoritative
- remove indirect coupling between `ProfileMutationUseCases` and `ProfileUseCases`
- simplify adapters like REST/UI/CLI that currently import façade contracts just to reach slice behavior

**Evidence files**

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- `src/main/java/datingapp/app/cli/ProfileHandler.java`
- `src/main/java/datingapp/app/cli/StatsHandler.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`

---

### Finding 2: production paths are cleaner than the code because legacy fallbacks still remain

**What the source says**

- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java` still contains dual branches for:
  - storage-backed loading
  - use-case-backed loading
- `ViewModelFactory` now builds the production `MatchesViewModel` with use-case slices and `null` for old raw dependencies.
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java` still supports three save paths:
  - mutation use case
  - façade use case
  - direct `userStore` legacy path
- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java` still supports both use-case persistence and raw `userStore` fallback.

**Why this matters**

The production path is already opinionated, but the code still pays the branching cost of older migration states. That inflates method size, multiplies test states, and makes the classes harder to scan.

**Immediate cleanup value**

High. Removing or isolating these dead/legacy branches will shorten the most complex ViewModels without changing functionality.

**Evidence files**

- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`

---

### Finding 3: `UiDataAdapters` mixes valuable adapters with pure pass-through wrappers

**What the source says**

- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java` contains:
  - useful adapters that really change semantics or shape:
    - `UseCaseUiProfileNoteDataAccess`
    - `MetricsUiPresenceDataAccess`
    - `UiPage<T>`
  - pure proxy adapters that are almost 1:1 pass-throughs:
    - `StorageUiUserStore`
    - `StorageUiSocialDataAccess`
- `ViewModelFactory` caches and wires these wrappers.
- `UiUserStore` is still widely injected into ViewModels, even where higher-level use cases already exist.

**Why this matters**

The adapter layer is doing two different jobs:

- real adaptation
- simple renaming/proxying

That makes the layer larger than necessary and keeps `ViewModelFactory` busier than it needs to be.

**Immediate cleanup value**

Medium-high. The pure proxy wrappers are not the biggest code smell in the repo, but they are a very clean consolidation target once the profile/match ViewModels are simplified.

**Evidence files**

- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/NotesViewModel.java`

---

### Finding 4: user presentation logic is duplicated across REST, CLI, and JavaFX

**What the source says**

Syntax-aware search found **22 structural matches** for `user.getAge(zone).orElse(0)` in production code.

This pattern and its surrounding display logic show up repeatedly in:

- `src/main/java/datingapp/app/api/RestApiDtos.java`
- `src/main/java/datingapp/app/cli/MatchingCliPresenter.java`
- `src/main/java/datingapp/app/cli/MatchingHandler.java`
- `src/main/java/datingapp/ui/screen/LoginController.java`
- `src/main/java/datingapp/ui/screen/MatchingController.java`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- `src/main/java/datingapp/app/usecase/dashboard/DashboardUseCases.java`

Repeated concerns include:

- safe age access with timezone
- verified badge or verified state display
- bio fallback text
- time-ago rendering
- user summary shaping

**Why this matters**

This is exactly the kind of duplication that drifts quietly: different channels start showing slightly different age fallback, bio fallback, verified semantics, or wording.

**Immediate cleanup value**

Medium-high. This should be consolidated into **small shared helpers**, not one giant formatter.

**Evidence files**

- `src/main/java/datingapp/app/api/RestApiDtos.java`
- `src/main/java/datingapp/app/cli/MatchingCliPresenter.java`
- `src/main/java/datingapp/ui/screen/LoginController.java`
- `src/main/java/datingapp/ui/screen/MatchingController.java`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`

---

### Finding 5: profile bootstrap defaults and discovery-preference normalization are still scattered

**What the source says**

- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java` has `applyMinimalCreateProfile(...)`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java` has `applyDefaultCreateProfile(...)`
- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java` clamps and rewrites age/distance preferences again
- `src/main/java/datingapp/ui/screen/CreateAccountDialogFactory.java` clamps age in dialog code
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java` separately validates/applies birth date, age range, and max distance
- Syntax-aware search found:
  - **8** `setAgeRange(...)` sites
  - **9** `setMaxDistanceKm(...)` sites

**Why this matters**

The business rules are simple, but they are not expressed in one place. That means onboarding, preferences, and profile editing can drift semantically even if all tests are green.

**Immediate cleanup value**

High. Centralizing normalization/defaulting would reduce repeated config-bound arithmetic and remove several subtle drift points.

**Evidence files**

- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
- `src/main/java/datingapp/ui/screen/CreateAccountDialogFactory.java`
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- `src/main/java/datingapp/storage/DevDataSeeder.java`
- `src/main/java/datingapp/app/cli/ProfileHandler.java`

---

### Finding 6: `JdbiUserStorage` is overloaded

**What the source says**

`src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` currently combines:

- cache management
- core CRUD
- normalized table writes
- normalized table hydration
- pagination
- profile-note storage
- row mappers
- SQL binding helpers
- user-lock behavior

Particularly dense areas:

- `loadNormalizedProfileData(...)` — large `UNION ALL` query + row fan-out switch
- duplicated paging methods:
  - `getPageOfActiveUsers(...)`
  - `getPageOfAllUsers(...)`
- large `UserSqlBindings` helper

**Why this matters**

The class is doing too many jobs at once. It is hard to test in slices and easy to accidentally grow further.

**Immediate cleanup value**

High. Splitting hydration, normalized persistence, and paging will make the storage layer easier to reason about without changing public behavior.

**Evidence files**

- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`

---

### Finding 7: `RestApiServer` is still too monolithic and repetitive

**What the source says**

- `src/main/java/datingapp/app/api/RestApiServer.java` is **1235 lines**
- A targeted search found **164** route-plumbing matches around repeated:
  - `loadUser(...)`
  - `handleUseCaseFailure(...)`
  - `ctx.status(...)`
  - `ctx.json(...)`
- The file still contains a single-use compatibility hop for achievements:
  - `new ProfileUseCases.AchievementSnapshot(...)`
- `AchievementSnapshotDto.from(...)` currently accepts the façade snapshot type instead of the slice snapshot type.

**Why this matters**

The REST adapter is correct but expensive to read and audit. Each new change reopens the same parse/load/dispatch/respond boilerplate.

**Immediate cleanup value**

High. This is a strong small-to-mid rewrite target: split by route group and centralize the repeated execution plumbing.

**Evidence files**

- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/RestApiDtos.java`
- `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- `src/test/java/datingapp/app/api/RestApiRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiVerificationRoutesTest.java`

---

### Finding 8: oversized UI classes still mix too many responsibilities

**What the source says**

The source scan and direct reads point to the same hotspot cluster:

- `ProfileViewModel.java` — form state, validation, diffing, persistence mode selection, photo lifecycle, preview building
- `MatchesViewModel.java` — pagination, loading, mapping, relationship actions, async orchestration, legacy fallback paths
- `ProfileController.java` — very large JavaFX binding/controller class
- `ChatViewModel.java` — conversation loading, polling, selection handling, send flow, action flow
- `ChatController.java` — controller wiring + UI state + rendering responsibilities

**Why this matters**

These classes are hard to scan because orchestration, mapping, validation, and side effects live together. The code works, but the unit of behavior is too large.

**Immediate cleanup value**

High, but sequence matters. These classes should be cleaned **after** contract/fallback cleanup so they only need to support the final production path.

**Evidence files**

- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- `src/main/java/datingapp/ui/screen/ProfileController.java`
- `src/main/java/datingapp/ui/screen/ChatController.java`

---

### Finding 9: a few seams can simply be deleted now

**What the source says**

- `ServiceRegistry.getCompatibilityCalculator()` appears to have **no usages**; code-usage lookup found only the definition.
- `new ProfileUseCases.AchievementSnapshot(...)` appears at **one** production call site in `RestApiServer`.

**Why this matters**

These are low-risk wins. They remove dead or wrapper-only surface area with immediate clarity benefit.

**Immediate cleanup value**

Medium, but very safe once the surrounding callers are updated.

**Evidence files**

- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/RestApiDtos.java`

---

## Recommended execution order

### Wave 1 — safest, highest leverage

1. Make profile slice contracts authoritative.
2. Remove production-dead fallback branches from the biggest ViewModels.
3. Delete dead/wrapper-only seams (`getCompatibilityCalculator`, REST achievement wrapper).

### Wave 2 — repeated logic cleanup

4. Revisit `UiDataAdapters` / `ViewModelFactory` after the contract simplification settles.
5. Consolidate profile bootstrap defaults and discovery-preference normalization.
6. Consolidate user presentation helpers.

### Wave 3 — structural slimming

7. Split `RestApiServer` into smaller route groups and shared executors.
8. Split `JdbiUserStorage` into smaller collaborators.
9. Extract focused collaborators from `ProfileViewModel`, `MatchesViewModel`, `ProfileController`, `ChatViewModel`, and `ChatController`.

### Guardrails for every refactor in this cleanup sprint

- no feature additions
- no behavior changes unless the current behavior is clearly redundant, inconsistent, or provably dead
- no broad package shuffle
- prefer a single production path over keeping migration-era fallback branches alive
- target shorter methods and narrower responsibilities
- use existing tests first; add characterization tests before cutting branches
- keep `core/` framework-agnostic

---

# Source-Only Cleanup and Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove compatibility debt, duplicate logic, dead seams, and oversized multi-responsibility code paths from the current Java codebase without adding features or changing user-visible product scope.

**Architecture:** Prefer making existing slice use cases authoritative, then collapse transitional adapter/fallback paths around them. For repeated logic, extract small shared helpers rather than inventing new large frameworks. For oversized UI/storage classes, split by responsibility only after the contracts and primary execution paths are simplified.

**Tech Stack:** Java 25 (preview), JavaFX 25, Maven, JDBI 3, Javalin, H2, JUnit 5, Spotless, Checkstyle, PMD, JaCoCo, ast-grep, ripgrep.

---

## Scope

### In scope

- profile slice contract cleanup
- removal of compatibility and wrapper-only types where they no longer add value
- deletion of production-dead fallback branches in ViewModels
- consolidation of repeated user presentation and profile-normalization logic
- targeted decomposition of `RestApiServer`, `JdbiUserStorage`, and oversized UI classes
- deletion of dead composition-root exports
- strengthening test coverage around the refactored seams

### Out of scope

- PostgreSQL migration
- Kotlin migration
- Android work
- new REST features
- new UI flows
- large domain-model redesign
- changing the current product feature set

---

## Context map

### Primary production files by workstream

| Workstream                          | Primary files                                                                                                                                                                                                                                     | Why they matter                                                  |
|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| Profile contract cleanup            | `app/usecase/profile/ProfileUseCases.java`, `ProfileMutationUseCases.java`, `ProfileInsightsUseCases.java`, `ProfileNotesUseCases.java`                                                                                                           | Compatibility façade and alias record debt start here            |
| ViewModel fallback removal          | `ui/viewmodel/ProfileViewModel.java`, `MatchesViewModel.java`, `PreferencesViewModel.java`, `ViewModelFactory.java`                                                                                                                               | Production path is already narrower than the code                |
| UI adapter cleanup                  | `ui/viewmodel/UiDataAdapters.java`, `ui/viewmodel/ViewModelFactory.java`, selected ViewModels                                                                                                                                                     | Some adapters adapt; some only proxy                             |
| Presentation helper consolidation   | `app/api/RestApiDtos.java`, `app/cli/MatchingCliPresenter.java`, `ui/screen/LoginController.java`, `ui/screen/MatchingController.java`, `ui/viewmodel/LoginViewModel.java`, `ui/viewmodel/MatchesViewModel.java`                                  | Same age/bio/verified/timeago logic is repeated                  |
| Normalization/default consolidation | `app/usecase/profile/ProfileMutationUseCases.java`, `ui/viewmodel/LoginViewModel.java`, `ui/viewmodel/PreferencesViewModel.java`, `ui/viewmodel/ProfileViewModel.java`, `ui/screen/CreateAccountDialogFactory.java`, `storage/DevDataSeeder.java` | Age/distance/defaulting logic is scattered                       |
| REST slimming                       | `app/api/RestApiServer.java`, `app/api/RestApiDtos.java`                                                                                                                                                                                          | Repeated route boilerplate and isolated wrapper seam             |
| Storage slimming                    | `storage/jdbi/JdbiUserStorage.java`                                                                                                                                                                                                               | Cache + CRUD + hydration + notes + pagination are mixed together |
| UI hotspot extraction               | `ui/viewmodel/ProfileViewModel.java`, `MatchesViewModel.java`, `ChatViewModel.java`, `ui/screen/ProfileController.java`, `ChatController.java`                                                                                                    | Oversized orchestration-heavy classes                            |
| Dead seam removal                   | `core/ServiceRegistry.java`, `app/api/RestApiServer.java`, `app/api/RestApiDtos.java`                                                                                                                                                             | Dead getter and wrapper-only compatibility hop                   |

### Test files to keep in the loop

| Area                       | Test files                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Profile use cases          | `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`, `ProfileUseCasesNotesTest.java`, `ProfileMutationUseCasesTest.java`                                                                                                                                                                                                                                                                                                                          |
| CLI profile/stats/matching | `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`, `ProfileCreateSelectTest.java`, `StatsHandlerTest.java`, `MatchingCliPresenterTest.java`, `MatchingHandlerTest.java`                                                                                                                                                                                                                                                                                      |
| REST                       | `src/test/java/datingapp/app/api/RestApiDtosTest.java`, `RestApiRoutesTest.java`, `RestApiReadRoutesTest.java`, `RestApiRelationshipRoutesTest.java`, `RestApiVerificationRoutesTest.java`, `RestApiRequestGuardsTest.java`, `RestApiIdentityPolicyTest.java`                                                                                                                                                                                                        |
| ViewModels/controllers     | `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`, `MatchesViewModelTest.java`, `PreferencesViewModelTest.java`, `LoginViewModelTest.java`, `ChatViewModelTest.java`, `DashboardViewModelTest.java`, `ViewModelFactoryTest.java`, `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`, `ChatControllerTest.java`, `LoginControllerTest.java`, `MatchingControllerTest.java`, `MatchesControllerTest.java`, `ProfileFormValidatorTest.java` |
| Storage                    | `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`, `JdbiUserStorageMigrationTest.java`, `JdbiMatchmakingStorageTransitionAtomicityTest.java`                                                                                                                                                                                                                                                                                              |
| Registry                   | `src/test/java/datingapp/core/ServiceRegistryTest.java`                                                                                                                                                                                                                                                                                                                                                                                                              |

### Refactor guardrails

- prefer deleting old branches over preserving them forever
- prefer slice-local records over façade alias records
- keep all new helpers small and responsibility-specific
- do not invent generic abstractions unless there are at least 3 clear call sites
- target methods under ~85 lines where practical
- keep nesting depth shallow
- verify after each cleanup slice

---

## Execution order

Implement in this order:

1. characterization tests for the seams being simplified
2. profile contract cleanup
3. fallback-path removal in ViewModels
4. adapter/factory cleanup
5. presentation helper consolidation
6. normalization/defaulting consolidation
7. REST slimming
8. storage slimming
9. hotspot UI extractions
10. dead seam removal and full verification

---

### Task 1: Freeze current behavior with characterization coverage

> **Status:** ✅ Completed on 2026-04-03. Added characterization coverage for the new slice-local mutation contract, direct insights DTO mapping, and explicit mutation-backed `ProfileViewModel` save wiring. Verified together with the Task 2 slice.

**Files:**
- Modify: `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- Modify: `src/test/java/datingapp/app/usecase/profile/ProfileMutationUseCasesTest.java`
- Modify: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- Modify: `src/test/java/datingapp/ui/viewmodel/MatchesViewModelTest.java`
- Modify: `src/test/java/datingapp/ui/viewmodel/PreferencesViewModelTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- Modify: `src/test/java/datingapp/core/ServiceRegistryTest.java`

**Purpose:** Lock down current behavior before removing compatibility layers and fallback paths.

- [x] Add tests that pin the current public behavior of `ProfileMutationUseCases`, `ProfileInsightsUseCases`, and `ProfileNotesUseCases` separately from `ProfileUseCases`.
- [x] Add tests that prove the production `MatchesViewModel`, `ProfileViewModel`, and `PreferencesViewModel` constructor paths do not require legacy fallback dependencies.
- [x] Add tests that pin REST achievement DTO behavior before changing the wrapper type.
- [x] Confirm via source search / code-usage inspection that `ServiceRegistry.getCompatibilityCalculator()` has no production callers before deleting it, then keep `ServiceRegistryTest` green after removal.
- [x] Run targeted tests.

Observed:
- Focused red/green verification first failed on missing slice-local profile mutation contracts and direct insights DTO mapping, then passed after implementation.
- Combined Task 1 + Task 2 regression slice via `runTests` passed: **147 passed, 0 failed** across the affected profile/use-case/API/UI registry tests.

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,ProfileMutationUseCasesTest,ProfileViewModelTest,MatchesViewModelTest,PreferencesViewModelTest,RestApiDtosTest,ServiceRegistryTest test`

Expected:
- current behavior is pinned before refactors begin

---

### Task 2: Make profile slice contracts authoritative

> **Status:** ✅ Completed on 2026-04-03. `ProfileMutationUseCases`, `ProfileInsightsUseCases`, and `ProfileNotesUseCases` now own the live contract types; `ProfileUseCases` delegates using slice-local records instead of façade-owned aliases.

**Files:**
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileInsightsUseCases.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileNotesUseCases.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Modify: `src/main/java/datingapp/app/cli/StatsHandler.java`
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`

**Purpose:** Stop using `ProfileUseCases` as the source of command/result types when the underlying slice use cases already own the behavior.

**Target shape:**

```java
// Examples of the direction, not mandatory final names
ProfileMutationUseCases.SaveProfileCommand
ProfileMutationUseCases.ProfileSaveResult
ProfileMutationUseCases.UpdateDiscoveryPreferencesCommand
ProfileMutationUseCases.UpdateProfileCommand
ProfileMutationUseCases.DeleteAccountCommand

ProfileInsightsUseCases.AchievementsQuery
ProfileInsightsUseCases.AchievementSnapshot
ProfileInsightsUseCases.StatsQuery
ProfileInsightsUseCases.SessionSummaryQuery

ProfileNotesUseCases.ProfileNotesQuery
ProfileNotesUseCases.ProfileNoteQuery
ProfileNotesUseCases.UpsertProfileNoteCommand
ProfileNotesUseCases.DeleteProfileNoteCommand
```

- [x] Introduce authoritative slice-local command/result records in the slice classes.
- [x] Update `ProfileMutationUseCases` to stop depending on `ProfileUseCases.*` record types.
- [x] Reduce `ProfileUseCases` to either:
  - a thin optional compatibility wrapper, or
  - a smaller read/helper façade that no longer owns mutation/insight/note contract types.
- [x] Migrate main-source callers off `ProfileUseCases.*` imports where the slice already exists.
- [x] Delete now-unused alias records and conversion helpers.
- [x] Run targeted tests.

Observed:
- `ProfileMutationUseCasesTest`, `ProfileUseCasesTest`, `ProfileUseCasesNotesTest`, `RestApiDtosTest`, `StatsViewModelTest`, and `MainLifecycleTest` all passed after the migration.
- Combined Task 1 + Task 2 regression slice via `runTests` passed: **147 passed, 0 failed**.

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,ProfileMutationUseCasesTest,ProfileUseCasesNotesTest,ProfileHandlerTest,StatsHandlerTest,ProfileViewModelTest,PreferencesViewModelTest,SafetyViewModelTest,ViewModelFactoryTest,ServiceRegistryTest test`

Expected:
- slice contracts are authoritative
- `ProfileUseCases` surface area is materially smaller
- no public behavior changes in tests

---

### Task 3: Remove production-dead fallback branches from the key ViewModels

> **Status:** ✅ Completed on 2026-04-03. `ProfileViewModel` now saves through one explicit mutation-use-case path, `PreferencesViewModel` persists only through `ProfileMutationUseCases`, and `MatchesViewModel` uses one use-case-backed production path with updated tests and factory wiring.

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: related tests under `src/test/java/datingapp/ui/viewmodel/`

**Purpose:** Align the code with the actual production wiring.

- [x] Remove the storage-backed branch from `MatchesViewModel` production logic and keep one use-case-backed loading/action path.
- [x] Remove the façade save path and direct-storage save path from `ProfileViewModel` production logic; keep one mutation-use-case path.
- [x] Remove raw `userStore` fallback persistence from `PreferencesViewModel` production logic.
- [x] If tests still need alternate seams, keep them isolated in test helpers or explicit testing constructors rather than inline production branching.
- [x] Simplify duplicated match/like mapping helpers once only one path remains.
- [x] Run targeted tests.

Observed:
- Focused `ProfileViewModelTest` run passed: **15 passed, 0 failed**.
- Focused `MatchesViewModelTest` + `MatchesControllerTest` run passed: **20 passed, 0 failed**.
- Full Task 3 regression slice via `runTests` passed: **58 passed, 0 failed** across `MatchesViewModelTest`, `ProfileViewModelTest`, `PreferencesViewModelTest`, `ViewModelFactoryTest`, `MatchesControllerTest`, `ProfileControllerTest`, and `PreferencesControllerTest`.

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=MatchesViewModelTest,ProfileViewModelTest,PreferencesViewModelTest,ViewModelFactoryTest,MatchesControllerTest,ProfileControllerTest,PreferencesControllerTest test`

Expected:
- fewer branches in the large ViewModels
- same observable behavior in UI tests

---

### Task 4: Separate real adapters from proxy-only adapters

> **Status:** ✅ Completed on 2026-04-03. Removed the pure proxy `UiUserStore` / `StorageUiUserStore` and `UiSocialDataAccess` / `StorageUiSocialDataAccess` seams, switched the affected viewmodels to direct storage types, and simplified `ViewModelFactory` accordingly while keeping the real shaping adapters in place.

**Files:**
- Modify: `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileReadOnlyViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/SocialViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/NotesViewModel.java`
- Modify: relevant tests

**Purpose:** Keep only adapters that actually adapt behavior or shape.

- [x] Audit `UiDataAdapters` and classify each adapter as either:
  - behavior/shape-changing, or
  - pure proxy.
- [x] Delete or inline proxy-only adapters that do not add semantics.
- [x] Keep `UiPage`, note-adapter, and presence-adapter style helpers that genuinely hide or translate behavior.
- [x] Simplify `ViewModelFactory` caching/wiring after the proxy-only adapters are removed.
- [x] Run targeted tests.

Observed:
- Focused Task 4 regression slice via `runTests` passed: **75 passed, 0 failed** across `LoginViewModelTest`, `NotesViewModelTest`, `SocialViewModelTest`, `ProfileViewModelTest`, `ViewModelFactoryTest`, `LoginControllerTest`, `NotesControllerTest`, `ProfileControllerTest`, `ProfileViewControllerTest`, and `SocialControllerTest`.

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=ViewModelFactoryTest,LoginViewModelTest,ProfileViewModelTest,ProfileViewControllerTest,SocialViewModelTest,NotesViewModelTest test`

Expected:
- smaller `UiDataAdapters`
- less adapter-specific plumbing in `ViewModelFactory`

---

### Task 5: Consolidate profile bootstrap defaults and discovery normalization

> **Status:** ✅ Completed on 2026-04-03. Added `ProfileNormalizationSupport` and routed the use-case, login, preferences, profile, and CLI normalization/default paths through it while preserving existing validation-first behavior.

**Files:**
- Create: `src/main/java/datingapp/app/usecase/profile/ProfileNormalizationSupport.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/CreateAccountDialogFactory.java`
- Modify: `src/main/java/datingapp/storage/DevDataSeeder.java`
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`
- Test: create/modify focused tests around normalization behavior

**Purpose:** Put age-range, distance, and minimal-profile-default logic in one place.

**Target shape:**

```java
final class ProfileNormalizationSupport {
    static int clampDiscoveryMinAge(AppConfig config, int age) { ... }
    static int clampDiscoveryMaxAge(AppConfig config, int age) { ... }
    static int clampMaxDistanceKm(AppConfig config, int distanceKm) { ... }
    static void applyMinimalBootstrap(User user, AppConfig config, int age, Gender gender, Gender interestedIn) { ... }
}
```

- [x] Extract shared normalization/defaulting helpers from `ProfileMutationUseCases`.
- [x] Make `LoginViewModel` delegate to the same normalization/bootstrap rules instead of re-implementing them.
- [x] Make `PreferencesViewModel` and `ProfileViewModel` call the same normalization helpers instead of repeating clamp/swap logic.
- [x] Reduce UI-only code in `CreateAccountDialogFactory` to editor/UX concerns; keep business defaults out of the dialog.
- [x] Update `DevDataSeeder` if it duplicates the same normalization assumptions.
- [x] Run targeted tests.

Observed:
- Red test first failed on missing `ProfileNormalizationSupport`, then passed after implementation.
- Focused Task 5 regression slice via `runTests` passed: **48 passed, 0 failed** across `ProfileNormalizationSupportTest`, `ProfileMutationUseCasesTest`, `LoginViewModelTest`, `PreferencesViewModelTest`, `ProfileViewModelTest`, and `ProfileHandlerTest`.

Notes:
- `CreateAccountDialogFactory` remained UI-only; no business-default logic was kept there.
- `DevDataSeeder` was left unchanged because its fixed seed-specific values did not need to adopt the runtime config-normalization seam to preserve current behavior.

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=ProfileMutationUseCasesTest,LoginViewModelTest,PreferencesViewModelTest,ProfileViewModelTest,ProfileHandlerTest,ProfileCreateSelectTest test`

Expected:
- one canonical implementation for minimal profile bootstrap and discovery normalization

---

### Task 6: Consolidate user presentation helpers

> **Status:** ✅ Completed on 2026-04-03. Added `UserPresentationSupport` and replaced the repeated age/bio presentation transformations in REST, CLI, and JavaFX while preserving channel-specific wording and layout.

**Files:**
- Create: `src/main/java/datingapp/app/support/UserPresentationSupport.java`
- Modify: `src/main/java/datingapp/app/api/RestApiDtos.java`
- Modify: `src/main/java/datingapp/app/cli/MatchingCliPresenter.java`
- Modify: `src/main/java/datingapp/ui/screen/LoginController.java`
- Modify: `src/main/java/datingapp/ui/screen/MatchingController.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- Test: create/modify helper tests and channel-specific tests

**Purpose:** Remove repeated age/bio/verified/timeago/user-summary shaping logic while preserving channel-specific output.

**Target shape:**

```java
final class UserPresentationSupport {
    static int safeAge(User user, ZoneId zone) { ... }
    static String fallbackBio(User user, String emptyText, int maxLength) { ... }
    static boolean isVerified(User user) { ... }
    static String timeAgo(Instant instant) { ... }
}
```

- [x] Extract small shared helpers for repeated user summary concerns.
- [x] Keep channel-specific wording in CLI/REST/UI; only consolidate the repeated source transformations.
- [x] Replace repeated `user.getAge(zone).orElse(0)` call sites and repeated bio fallback logic.
- [x] Avoid a giant formatter class; keep the helper minimal.
- [x] Run targeted tests.

Observed:
- Red helper test first failed on missing `UserPresentationSupport`, then passed after implementation.
- Focused Task 6 regression slice via `runTests` passed: **59 passed, 0 failed** across `UserPresentationSupportTest`, `RestApiDtosTest`, `MatchingCliPresenterTest`, `LoginViewModelTest`, `MatchesViewModelTest`, `LoginControllerTest`, and `MatchingControllerTest`.

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=RestApiDtosTest,MatchingCliPresenterTest,LoginControllerTest,MatchingControllerTest,LoginViewModelTest,MatchesViewModelTest test`

Expected:
- fewer repeated summary helpers
- same user-facing outputs

---

### Task 7: Slim `RestApiServer` and delete the isolated REST achievement wrapper

> **Status:** ✅ Completed on 2026-04-03. Removed the façade-only achievement snapshot hop, extracted route registration into `RestRouteSupport`, and kept `RestApiServer` as the REST composition root while preserving endpoint behavior across the focused route test surface.

**Files:**
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/app/api/RestApiDtos.java`
- Create: `src/main/java/datingapp/app/api/RestRouteSupport.java`
- Create: `src/main/java/datingapp/app/api/RestMatchingRoutes.java`
- Create: `src/main/java/datingapp/app/api/RestProfileRoutes.java`
- Create: `src/main/java/datingapp/app/api/RestMessagingRoutes.java`
- Test: relevant `RestApi*Test.java` files

**Purpose:** Reduce route boilerplate and remove wrapper-only DTO seams.

**Required outcomes:**

- `AchievementSnapshotDto.from(...)` accepts `ProfileInsightsUseCases.AchievementSnapshot`
- `RestApiServer` no longer constructs `new ProfileUseCases.AchievementSnapshot(...)`
- repeated `load user → call use case → map failure → respond` code is centralized in a support helper or small route-group classes

- [x] First remove the single-use achievement wrapper hop.
- [x] Introduce small route-support helpers for common response mapping.
- [x] Split the large route methods into grouped collaborators without changing registered endpoints.
- [x] Keep `RestApiServer` as the composition root for route registration only.
- [x] Run targeted REST tests.

Observed:
- Clean main compile after the Task 7 route extraction passed: `mvn --% clean -DskipTests compile` ✅
- Focused Task 7 REST regression slice passed: **57 passed, 0 failed** across `RestApiDtosTest`, `RestApiRoutesTest`, `RestApiReadRoutesTest`, `RestApiRelationshipRoutesTest`, `RestApiVerificationRoutesTest`, `RestApiRequestGuardsTest`, and `RestApiIdentityPolicyTest`.

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=RestApiDtosTest,RestApiRoutesTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,RestApiVerificationRoutesTest,RestApiRequestGuardsTest,RestApiIdentityPolicyTest test`

Expected:
- smaller route methods
- no change to endpoint behavior
- no façade-only achievement wrapper left

---

### Task 8: Split `JdbiUserStorage` by responsibility

> **Status:** ✅ Completed on 2026-04-03. Extracted normalized profile persistence/hydration/dealbreaker assembly into focused collaborators and deduped the user paging logic while preserving the existing `UserStorage` contract.

**Files:**
- Create: `src/main/java/datingapp/storage/jdbi/NormalizedProfileRepository.java`
- Create: `src/main/java/datingapp/storage/jdbi/NormalizedProfileHydrator.java`
- Create: `src/main/java/datingapp/storage/jdbi/DealbreakerAssembler.java`
- Modify: `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- Optionally create: `src/main/java/datingapp/storage/jdbi/UserPageQueries.java`
- Test: `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`, `JdbiUserStorageMigrationTest.java`

**Purpose:** Keep `JdbiUserStorage` as the façade, but move the heavy normalized-profile work out of it.

**Required outcomes:**

- normalized table load/save logic extracted
- duplicate page methods share one helper
- dealbreaker reconstruction moved out of the storage façade
- row-binding helper reduced or isolated

- [x] Extract the `UNION ALL` normalized-profile query/hydration into a dedicated collaborator.
- [x] Extract dealbreaker reconstruction into a dedicated assembler.
- [x] Dedupe `getPageOfActiveUsers(...)` and `getPageOfAllUsers(...)` through a private paging helper.
- [x] Keep the public `UserStorage` behavior identical.
- [x] Run targeted storage tests.

Observed:
- Created `NormalizedProfileRepository.java`, `NormalizedProfileHydrator.java`, and `DealbreakerAssembler.java`.
- Focused Task 8 regression slice passed: **62 passed, 0 failed** across `JdbiUserStorageNormalizationTest`, `JdbiUserStorageMigrationTest`, and `ServiceRegistryTest`.

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=JdbiUserStorageNormalizationTest,JdbiUserStorageMigrationTest,ServiceRegistryTest test`

Expected:
- much smaller `JdbiUserStorage`
- same persistence semantics

---

### Task 9: Extract focused collaborators from the largest UI hotspots

> **Status:** ✅ Completed on 2026-04-03. Extracted focused collaborators for draft assembly, photo mutation, match-list loading, relationship action execution, and conversation loading while preserving the existing screen APIs and behavior.

**Files:**
- Create: `src/main/java/datingapp/ui/viewmodel/ProfileDraftAssembler.java`
- Create: `src/main/java/datingapp/ui/viewmodel/PhotoMutationCoordinator.java`
- Create: `src/main/java/datingapp/ui/viewmodel/MatchListLoader.java`
- Create: `src/main/java/datingapp/ui/viewmodel/RelationshipActionRunner.java`
- Create: `src/main/java/datingapp/ui/viewmodel/ConversationLoader.java`
- Create: `src/main/java/datingapp/ui/screen/ProfileFormBindings.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Modify: `src/main/java/datingapp/ui/screen/ChatController.java`
- Tests: corresponding ViewModel/controller tests

**Purpose:** Split orchestration-heavy UI classes into smaller, easier-to-read helpers.

**Required outcomes:**

- `ProfileViewModel` keeps form state and coordination, but not draft assembly + photo mutation implementation details
- `MatchesViewModel` keeps screen state and actions, but not page-loading and relationship-action plumbing
- `ChatViewModel` keeps screen state, but not message/conversation loader internals
- `ProfileController` and `ChatController` become lighter binding/wiring classes

- [x] Extract `ProfileDraftAssembler` from `createDraftUser(...)`, `copyCurrentUserToDraft(...)`, and related field-application methods.
- [x] Extract `PhotoMutationCoordinator` from the photo import/replace/delete/set-primary flow.
- [x] Extract `MatchListLoader` and `RelationshipActionRunner` from `MatchesViewModel`.
- [x] Extract `ConversationLoader` or equivalent from `ChatViewModel` polling/loading logic.
- [x] Evaluate `ProfileFormBindings` and controller-side helper extraction; no additional controller helper was introduced because the smallest safe extraction point was not strong enough to justify a new abstraction in this pass.
- [x] Run targeted UI tests.

Observed:
- Created `ProfileDraftAssembler.java`, `PhotoMutationCoordinator.java`, `MatchListLoader.java`, `RelationshipActionRunner.java`, and `ConversationLoader.java`.
- Focused profile extraction tests passed: **21 passed, 0 failed** across `ProfileViewModelTest` and `ProfileControllerTest`.
- Focused matches extraction tests passed: **20 passed, 0 failed** across `MatchesViewModelTest` and `MatchesControllerTest`.
- Focused chat extraction tests passed: **29 passed, 0 failed** across `ChatViewModelTest` and `ChatControllerTest`.
- Integrated Task 9 UI regression slice passed: **70 passed, 0 failed** across `ProfileViewModelTest`, `ProfileControllerTest`, `MatchesViewModelTest`, `MatchesControllerTest`, `ChatViewModelTest`, and `ChatControllerTest`.

Run:
`mvn --% -Dcheckstyle.skip=true -Dtest=ProfileViewModelTest,MatchesViewModelTest,ChatViewModelTest,ProfileControllerTest,ChatControllerTest,MatchingControllerTest,MatchesControllerTest test`

Expected:
- materially smaller ViewModel/controller classes
- lower branch count and clearer responsibilities

---

### Task 10: Remove dead seams and complete the final cleanup pass

> **Status:** ✅ Completed on 2026-04-03. Removed the dead `ServiceRegistry` compatibility-calculator export, restored the `UiDataAdapters` boundary after the final refactor wave, re-ran all focused regression slices, and closed the sprint with a green full quality gate.

**Files:**
- Modify: `src/main/java/datingapp/core/ServiceRegistry.java`
- Modify: any affected tests
- Modify only what is required after Tasks 1-9

**Purpose:** Finish the consolidation by deleting leftovers and proving the system still works end to end.

**Required outcomes:**

- delete `getCompatibilityCalculator()` and matching builder/storage if still unused
- delete any remaining façade-only wrapper types no longer referenced
- delete helper code kept temporarily for migration if it no longer has callers

- [x] Remove the unused `CompatibilityCalculator` export from `ServiceRegistry` and its builder field/setter.
- [x] Remove any now-unused compatibility records/helpers left behind by earlier tasks.
- [x] Run focused regression suites by workstream.
- [x] Run the full repo quality gate.

Observed:
- Profile/contracts focused slice passed: **73 passed, 0 failed**.
- UI/adapter cleanup focused slice passed: **74 passed, 0 failed**.
- REST focused slice passed: **57 passed, 0 failed**.
- Storage focused slice passed: **40 passed, 0 failed**.
- Additional architecture/UI boundary rerun passed: **74 passed, 0 failed**.
- Final full gate passed: `mvn spotless:apply verify` ✅ with **BUILD SUCCESS**, **1712 tests run, 0 failures, 0 errors, 0 skipped**, Checkstyle clean, PMD clean, and JaCoCo checks met.

Focused runs:

Profile/contracts:
`mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,ProfileMutationUseCasesTest,ProfileHandlerTest,StatsHandlerTest,PreferencesViewModelTest,ProfileViewModelTest test`

UI/adapter cleanup:
`mvn --% -Dcheckstyle.skip=true -Dtest=ViewModelFactoryTest,LoginViewModelTest,NotesViewModelTest,SocialViewModelTest,MatchesViewModelTest,ChatViewModelTest test`

REST:
`mvn --% -Dcheckstyle.skip=true -Dtest=RestApiDtosTest,RestApiRoutesTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,RestApiVerificationRoutesTest,RestApiRequestGuardsTest,RestApiIdentityPolicyTest test`

Storage:
`mvn --% -Dcheckstyle.skip=true -Dtest=JdbiUserStorageNormalizationTest,JdbiUserStorageMigrationTest,JdbiMatchmakingStorageTransitionAtomicityTest test`

Full gate:
`mvn spotless:apply verify`

Expected:
- no dead seams left from this cleanup plan
- all targeted suites pass
- full quality gate passes

---

## Exit criteria

This cleanup plan is complete when all of the following are true:

- [x] `ProfileMutationUseCases`, `ProfileInsightsUseCases`, and `ProfileNotesUseCases` own their own contract types
- [x] `ProfileUseCases` is no longer the primary contract source for slice behavior
- [x] `MatchesViewModel`, `ProfileViewModel`, and `PreferencesViewModel` each have one clear production path
- [x] proxy-only UI adapters have been removed or significantly reduced
- [x] repeated user presentation helpers are consolidated
- [x] repeated profile bootstrap/defaulting/normalization logic is consolidated
- [x] `RestApiServer` no longer contains the isolated achievement compatibility wrapper and is materially slimmer
- [x] `JdbiUserStorage` is materially smaller and split by responsibility
- [x] the main oversized UI classes are slimmer and responsibility-sliced
- [x] `ServiceRegistry.getCompatibilityCalculator()` is removed if still unused
- [x] `mvn spotless:apply verify` passes

---

## Recommended implementation mode

This plan is best executed in **small sequential refactor slices**, not one giant branch.

Recommended grouping:

1. Tasks 1-2 together
2. Task 3
3. Tasks 4-6 together
4. Task 7
5. Task 8
6. Task 9
7. Task 10

Avoid editing these shared files in parallel:

- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
- `src/main/java/datingapp/ui/viewmodel/UiDataAdapters.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`

---

## Final recommendation

Do this cleanup sprint **before** any PostgreSQL migration, Kotlin migration, or Android work.

The codebase is at the point where removing redundancy and compatibility debt will make every later phase easier, cheaper, and less error-prone.