# Architecture Audit Report

## Project Metrics

| Metric                                   | Value                                                                    |
|------------------------------------------|--------------------------------------------------------------------------|
| Total Files                              | 185 code files in `src/` (Java 182, CSS 2, XML 1)                        |
| Primary Languages                        | Java 25, JavaFX 25, CSS, XML                                             |
| Average LOC per File                     | 154.63 (main Java only)                                                  |
| Largest File                             | `src/main/java/datingapp/ui/controller/ProfileController.java` (667 LOC) |
| Most Complex File                        | `src/main/java/datingapp/app/cli/ProfileHandler.java` (≈105, heuristic)  |
| sources of mess and confusion            | 6                                                                        |
| Files confirmed as valid                 | 97 production files (not flagged by size/complexity/imports/duplication) |
| Files confirmed as invalid               | 29 production files (flagged)                                            |
| estimated/expected/ideal number of files | Current main Java: 126 → Ideal: ~110–115 after consolidation             |

---

Intro:

| sources of mess and confusion          | (the files)                                                                                                                                                                                                    |
|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CLI/UI orchestration duplication       | `app/cli/MatchingHandler.java`, `ui/viewmodel/MatchingViewModel.java`, `app/cli/ProfileHandler.java`, `ui/viewmodel/ProfileViewModel.java`, `app/cli/MessagingHandler.java`, `ui/viewmodel/ChatViewModel.java` |
| Oversized UI controllers               | `ui/controller/ProfileController.java`, `ui/controller/MatchesController.java`, `ui/controller/LoginController.java`                                                                                           |
| Oversized CLI handlers                 | `app/cli/ProfileHandler.java`, `app/cli/MatchingHandler.java`, `app/cli/MessagingHandler.java`                                                                                                                 |
| God-class infrastructure               | `storage/DatabaseManager.java`, `core/ServiceRegistry.java`                                                                                                                                                    |
| Large domain/config classes            | `core/User.java`, `core/AppConfig.java`, `core/MatchQualityService.java`                                                                                                                                       |
| Duplicate fragments (IDEA diagnostics) | `MatchingHandler`↔`MatchingViewModel`, `AchievementService`↔`ProfilePreviewService`, `Match`↔`Messaging`, `LoginController` internal duplication                                                               |

| Files confirmed as valid                          | (the files)                                     |
|---------------------------------------------------|-------------------------------------------------|
| Production files not listed in **File Summaries** | All remaining `src/main/java` files (97 total). |

| Files confirmed as invalid                     | (the files)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
|------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Flagged by size/complexity/imports/duplication | `app/cli/MatchingHandler.java`, `app/cli/MessagingHandler.java`, `app/cli/ProfileHandler.java`, `core/AchievementService.java`, `core/AppConfig.java`, `core/DailyService.java`, `core/Dealbreakers.java`, `core/MatchQualityService.java`, `core/ProfileCompletionService.java`, `core/User.java`, `storage/DatabaseManager.java`, `storage/jdbi/JdbiSocialStorage.java`, `storage/jdbi/JdbiStatsStorage.java`, `storage/jdbi/JdbiUserStorage.java`, `ui/ViewModelFactory.java`, `ui/component/UiComponents.java`, `ui/controller/AchievementPopupController.java`, `ui/controller/ChatController.java`, `ui/controller/LoginController.java`, `ui/controller/MatchesController.java`, `ui/controller/MatchingController.java`, `ui/controller/ProfileController.java`, `ui/viewmodel/ChatViewModel.java`, `ui/viewmodel/DashboardViewModel.java`, `ui/viewmodel/LoginViewModel.java`, `ui/viewmodel/MatchesViewModel.java`, `ui/viewmodel/MatchingViewModel.java`, `ui/viewmodel/ProfileViewModel.java`, `ui/viewmodel/StatsViewModel.java` |

main issues, why the happened, how, how to prevent, what our options? what can be done, how the pros are doing it? what do you suggest based on the current situation? what is the happy path?

- **Why this happened:** CLI and JavaFX were developed in parallel, each orchestrating flows directly against core services. Without a shared application layer, UI and CLI repeated the same orchestration logic (candidate loading, swipe handling, messaging flows). At the same time, wiring/bootstrapping was kept in `core/` for convenience, pulling infrastructure into the domain layer.
- **How it manifests:** Large UI controllers and CLI handlers, duplicate fragments confirmed by IDE diagnostics, inflated import counts (over 20–47), and high complexity in the same hotspots. `DatabaseManager` and `ServiceRegistry` are large infrastructural god objects.
- **How to prevent:** Enforce a clean layering boundary with a thin presentation layer, add a shared application service layer, and separate infrastructure initialization into a dedicated module. Establish a policy that UI/CLI never orchestrate multi-step workflows; they only call application services.
- **Options:**
  1. **Keep both CLI + JavaFX** and extract application services shared by both (recommended).
  2. **Deprecate one interface** (CLI or JavaFX) and keep only one orchestration surface.
- **Suggested happy path:** Introduce `core/app/*` application services, modularize the service registry, and split oversized UI controllers. Preserve existing public APIs through adapters, then gradually slim the handlers/viewmodels.

---

## File Summaries

> One entry per file flagged for architectural concern.

### `src/main/java/datingapp/app/cli/MatchingHandler.java`

**Lines of Code:** 608
**Cyclomatic Complexity:** 81 (heuristic)
**Import/Dependency Count:** 32

**Detected Issues:**

- High complexity; mixes orchestration, CLI I/O, and domain decisions.
- Duplicate fragments with `MatchingViewModel` (IDEA duplicate: line ~130 in handler vs ~156 in viewmodel).
- Shared duplicate fragment with `ProfileHandler`/`StatsHandler` (IDEA duplicate: line ~618 vs ~190/163).

**Suggested Action:**

- **Type:** extract_class / modularize
- **Target Location:** `core/app/MatchingAppService` + slim CLI wrapper
- **Impact:** high

---

### `src/main/java/datingapp/app/cli/MessagingHandler.java`

**Lines of Code:** 303
**Cyclomatic Complexity:** 41 (heuristic)
**Import/Dependency Count:** 22

**Detected Issues:**

- CLI handler orchestrates messaging lifecycle, similar to `ChatViewModel` flow.
- High orchestration complexity in a UI-facing class.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `core/app/MessagingAppService`
- **Impact:** high

---

### `src/main/java/datingapp/app/cli/ProfileHandler.java`

**Lines of Code:** 642
**Cyclomatic Complexity:** 105 (heuristic)
**Import/Dependency Count:** 29

**Detected Issues:**

- Largest CLI file with highest complexity; handles prompts, validation, persistence, and domain logic.
- Duplicate fragments with `MatchingHandler` and `StatsHandler` (IDEA duplicate: line ~190 with matching/ stats fragments).

**Suggested Action:**

- **Type:** extract_class / split
- **Target Location:** `core/app/ProfileAppService`, plus smaller CLI flows
- **Impact:** high

---

### `src/main/java/datingapp/core/AchievementService.java`

**Lines of Code:** 204
**Cyclomatic Complexity:** 42 (heuristic)
**Import/Dependency Count:** 13

**Detected Issues:**

- Duplicate fragment with `ProfilePreviewService` (IDEA duplicate: line ~247 vs ~148).
- Medium complexity in a service that also handles data formatting.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** shared helper for profile metrics/achievement computation
- **Impact:** medium

---

### `src/main/java/datingapp/core/AppConfig.java`

**Lines of Code:** 561
**Cyclomatic Complexity:** 8 (heuristic)
**Import/Dependency Count:** 3

**Detected Issues:**

- Large configuration surface in one file; low complexity but hard to navigate.
- High change risk due to many parameters.

**Suggested Action:**

- **Type:** split / modularize
- **Target Location:** nested records or grouped config classes (`Limits`, `Validation`, `Weights`)
- **Impact:** medium

---

### `src/main/java/datingapp/core/DailyService.java`

**Lines of Code:** 244
**Cyclomatic Complexity:** 24 (heuristic)
**Import/Dependency Count:** 20

**Detected Issues:**

- High dependency count; mixes daily pick, limit tracking, and eligibility logic.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `DailyPickService` + `DailyLimitService` (internal modules)
- **Impact:** medium

---

### `src/main/java/datingapp/core/Dealbreakers.java`

**Lines of Code:** 343
**Cyclomatic Complexity:** 38 (heuristic)
**Import/Dependency Count:** 5

**Detected Issues:**

- Large domain class with evaluator logic intertwined.

**Suggested Action:**

- **Type:** split
- **Target Location:** `Dealbreakers` + `DealbreakersEvaluator`
- **Impact:** medium

---

### `src/main/java/datingapp/core/MatchQualityService.java`

**Lines of Code:** 513
**Cyclomatic Complexity:** 80 (heuristic)
**Import/Dependency Count:** 16

**Detected Issues:**

- High complexity scoring logic in a single service.
- UI uses simplified scoring; risk of inconsistent results.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `MatchQualityScorer`, `MatchQualityBreakdown`
- **Impact:** high

---

### `src/main/java/datingapp/core/ProfileCompletionService.java`

**Lines of Code:** 323
**Cyclomatic Complexity:** 39 (heuristic)
**Import/Dependency Count:** 3

**Detected Issues:**

- Overlaps with profile preview logic (per duplication in AchievementService/Preview flow).

**Suggested Action:**

- **Type:** merge / rename
- **Target Location:** `ProfileQualityService`
- **Impact:** medium

---

### `src/main/java/datingapp/core/User.java`

**Lines of Code:** 571
**Cyclomatic Complexity:** 30 (heuristic)
**Import/Dependency Count:** 12

**Detected Issues:**

- Large mutable domain object mixing profile, preferences, photos, and state transitions.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `UserProfile`, `UserPreferences`, `UserPhotos`
- **Impact:** high

---

### `src/main/java/datingapp/storage/DatabaseManager.java`

**Lines of Code:** 605
**Cyclomatic Complexity:** 19 (heuristic)
**Import/Dependency Count:** 10

**Detected Issues:**

- Monolithic infra class combining pool creation, schema creation, and migration logic.

**Suggested Action:**

- **Type:** split
- **Target Location:** `SchemaInitializer`, `MigrationRunner`, `ConnectionProvider`
- **Impact:** high

---

### `src/main/java/datingapp/storage/jdbi/JdbiSocialStorage.java`

**Lines of Code:** 170
**Cyclomatic Complexity:** 5 (heuristic)
**Import/Dependency Count:** 24

**Detected Issues:**

- High import count and multiple SQL fragments; suggests query/mapper scatter.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** shared `SqlFragments` / mapper helpers
- **Impact:** low

---

### `src/main/java/datingapp/storage/jdbi/JdbiStatsStorage.java`

**Lines of Code:** 235
**Cyclomatic Complexity:** 2 (heuristic)
**Import/Dependency Count:** 20

**Detected Issues:**

- Low complexity but many dependencies; SQL and mapping may be overgrown.

**Suggested Action:**

- **Type:** modularize
- **Target Location:** split query methods into grouped sections or helpers
- **Impact:** low

---

### `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`

**Lines of Code:** 267
**Cyclomatic Complexity:** 19 (heuristic)
**Import/Dependency Count:** 28

**Detected Issues:**

- High import count suggests mapper/SQL diffusion.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `UserSql`, `UserMapper` helpers
- **Impact:** medium

---

### `src/main/java/datingapp/ui/ViewModelFactory.java`

**Lines of Code:** 195
**Cyclomatic Complexity:** 28 (heuristic)
**Import/Dependency Count:** 25

**Detected Issues:**

- Wiring logic and viewmodel lifecycle are conflated; many dependencies.

**Suggested Action:**

- **Type:** split
- **Target Location:** `ViewModelProvider` + `ViewModelRegistry`
- **Impact:** medium

---

### `src/main/java/datingapp/ui/component/UiComponents.java`

**Lines of Code:** 224
**Cyclomatic Complexity:** 9 (heuristic)
**Import/Dependency Count:** 29

**Detected Issues:**

- Utility dumping ground for UI component creation.

**Suggested Action:**

- **Type:** split
- **Target Location:** `LoadingOverlays`, `Dialogs`, `Cells`
- **Impact:** low

---

### `src/main/java/datingapp/ui/controller/AchievementPopupController.java`

**Lines of Code:** 159
**Cyclomatic Complexity:** 6 (heuristic)
**Import/Dependency Count:** 22

**Detected Issues:**

- High dependency count for a popup controller.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** shared popup base or reusable UI component
- **Impact:** low

---

### `src/main/java/datingapp/ui/controller/ChatController.java`

**Lines of Code:** 214
**Cyclomatic Complexity:** 10 (heuristic)
**Import/Dependency Count:** 24

**Detected Issues:**

- Controller performs messaging orchestration; overlaps with viewmodel responsibilities.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `MessagingAppService` + slimmer controller
- **Impact:** medium

---

### `src/main/java/datingapp/ui/controller/LoginController.java`

**Lines of Code:** 525
**Cyclomatic Complexity:** 39 (heuristic)
**Import/Dependency Count:** 47

**Detected Issues:**

- Largest import count in UI; combines list cell logic, filtering, navigation, and login orchestration.
- Duplicate fragments within the file (IDEA duplicate: line ~189 vs ~202).

**Suggested Action:**

- **Type:** split
- **Target Location:** `LoginListCell`, `LoginSearchController`, `LoginFlowService`
- **Impact:** medium

---

### `src/main/java/datingapp/ui/controller/MatchesController.java`

**Lines of Code:** 623
**Cyclomatic Complexity:** 42 (heuristic)
**Import/Dependency Count:** 44

**Detected Issues:**

- Large controller and high dependency count; mixes UI rendering with business decisions.

**Suggested Action:**

- **Type:** split / extract_class
- **Target Location:** `MatchesSectionController`, `LikesSectionController`
- **Impact:** high

---

### `src/main/java/datingapp/ui/controller/MatchingController.java`

**Lines of Code:** 367
**Cyclomatic Complexity:** 29 (heuristic)
**Import/Dependency Count:** 31

**Detected Issues:**

- Controller coordinates matching workflow; logic could live in a shared app service.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `MatchingAppService` + slimmer controller
- **Impact:** medium

---

### `src/main/java/datingapp/ui/controller/ProfileController.java`

**Lines of Code:** 667
**Cyclomatic Complexity:** 76 (heuristic)
**Import/Dependency Count:** 38

**Detected Issues:**

- Largest UI controller; handles photo management, validation, navigation, persistence.

**Suggested Action:**

- **Type:** split / extract_class
- **Target Location:** `ProfilePhotoController`, `ProfileBasicsController`, `ProfilePreferencesController`
- **Impact:** high

---

### `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java`

**Lines of Code:** 262
**Cyclomatic Complexity:** 28 (heuristic)
**Import/Dependency Count:** 23

**Detected Issues:**

- ViewModel handles orchestration that could be shared with CLI.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `MessagingAppService`
- **Impact:** medium

---

### `src/main/java/datingapp/ui/viewmodel/DashboardViewModel.java`

**Lines of Code:** 262
**Cyclomatic Complexity:** 29 (heuristic)
**Import/Dependency Count:** 26

**Detected Issues:**

- High dependency count and orchestration logic within viewmodel.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `DashboardAppService`
- **Impact:** medium

---

### `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`

**Lines of Code:** 301
**Cyclomatic Complexity:** 36 (heuristic)
**Import/Dependency Count:** 23

**Detected Issues:**

- Large viewmodel with logic that could be delegated to services.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `LoginAppService`
- **Impact:** medium

---

### `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`

**Lines of Code:** 378
**Cyclomatic Complexity:** 41 (heuristic)
**Import/Dependency Count:** 31

**Detected Issues:**

- High complexity and imports; possible overlap with `LikerBrowserHandler`.

**Suggested Action:**

- **Type:** extract_class / modularize
- **Target Location:** `MatchesAppService`
- **Impact:** medium

---

### `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`

**Lines of Code:** 267
**Cyclomatic Complexity:** 31 (heuristic)
**Import/Dependency Count:** 22

**Detected Issues:**

- Duplicate fragment with `MatchingHandler` (IDEA duplicate: line ~156 vs ~130).

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `MatchingAppService`
- **Impact:** high

---

### `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`

**Lines of Code:** 522
**Cyclomatic Complexity:** 61 (heuristic)
**Import/Dependency Count:** 33

**Detected Issues:**

- Large viewmodel; overlapping responsibilities with `ProfileHandler` and `ProfileController`.

**Suggested Action:**

- **Type:** extract_class / split
- **Target Location:** `ProfileAppService` + smaller viewmodel sections
- **Impact:** high

---

### `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`

**Lines of Code:** 165
**Cyclomatic Complexity:** 16 (heuristic)
**Import/Dependency Count:** 21

**Detected Issues:**

- High dependency count for a stats viewmodel.

**Suggested Action:**

- **Type:** extract_class
- **Target Location:** `StatsAppService`
- **Impact:** low

---

## Architectural Clusters

> Group files that should logically belong together but currently do not.

### Cluster: Matching & Discovery

**Suggested Module:** `core/app/matching` + `ui/matching` + `cli/matching`
**Files:**

- `core/CandidateFinder.java`
- `core/MatchingService.java`
- `core/DailyService.java`
- `core/UndoService.java`
- `core/MatchQualityService.java`
- `app/cli/MatchingHandler.java`
- `ui/viewmodel/MatchingViewModel.java`
- `ui/controller/MatchingController.java`
- `app/api/MatchRoutes.java`

**Rationale:** Shared responsibility for candidate discovery and match orchestration; duplicated logic currently present in CLI + UI.

**Recommended Change:**

- Create module: `core/app/matching`
- Move files: add `MatchingAppService` and use from CLI/VM
- Extract shared interfaces/services: `SwipeFlow`, `CandidateLoader`

---

### Cluster: Profile & Identity

**Suggested Module:** `core/app/profile` + `ui/profile`
**Files:**

- `core/User.java`
- `core/Preferences.java`
- `core/Dealbreakers.java`
- `core/ProfileCompletionService.java`
- `core/ProfilePreviewService.java`
- `app/cli/ProfileHandler.java`
- `ui/viewmodel/ProfileViewModel.java`
- `ui/controller/ProfileController.java`

**Rationale:** Profile lifecycle and completion logic are split across CLI/UI; many responsibilities in one model and controller.

**Recommended Change:**

- Create module: `core/app/profile`
- Move files: add `ProfileAppService`
- Extract shared interfaces/services: `ProfilePhotoManager`, `ProfileCompletenessEvaluator`

---

### Cluster: Messaging

**Suggested Module:** `core/app/messaging` + `ui/messaging` + `cli/messaging`
**Files:**

- `core/Messaging.java`
- `core/MessagingService.java`
- `app/cli/MessagingHandler.java`
- `ui/viewmodel/ChatViewModel.java`
- `ui/controller/ChatController.java`
- `app/api/MessagingRoutes.java`

**Rationale:** Message send/load logic appears in both UI and CLI without a shared orchestrator.

**Recommended Change:**

- Create module: `core/app/messaging`
- Move files: add `MessagingAppService`
- Extract shared interfaces/services: `ConversationSelector`, `MessageFormatter`

---

### Cluster: Stats & Achievements

**Suggested Module:** `core/app/stats` + `ui/stats`
**Files:**

- `core/Stats.java`
- `core/StatsService.java`
- `core/AchievementService.java`
- `app/cli/StatsHandler.java`
- `ui/viewmodel/StatsViewModel.java`
- `ui/controller/StatsController.java`

**Rationale:** Achievements and stats are computed in core but displayed in multiple UI surfaces.

**Recommended Change:**

- Create module: `core/app/stats`
- Move files: add `StatsAppService`
- Extract shared interfaces/services: `StatsFormatter`, `AchievementBadgeMapper`

---

### Cluster: Infrastructure & Storage

**Suggested Module:** `storage/schema` + `storage/jdbi`
**Files:**

- `storage/DatabaseManager.java`
- `storage/jdbi/*`

**Rationale:** Schema creation, migrations, and query mapping are tightly coupled and centralized.

**Recommended Change:**

- Create module: `storage/schema`
- Move files: split `DatabaseManager`
- Extract shared interfaces/services: `SchemaInitializer`, `MigrationRunner`

---

## Refactor Plan

> Prioritized list. High impact first.

### Refactor ID: `R-001`

**Title:** Introduce Matching Application Service

**Problem:** Matching orchestration is duplicated between CLI and JavaFX (`MatchingHandler` ↔ `MatchingViewModel`), increasing defect risk and maintenance cost.

**Files Involved:**

- `app/cli/MatchingHandler.java`
- `ui/viewmodel/MatchingViewModel.java`
- `core/CandidateFinder.java`
- `core/MatchingService.java`
- `core/UndoService.java`

**Refactor Steps:**

1. Add `core/app/MatchingAppService` to encapsulate candidate loading + swipe processing.
2. Move duplicate orchestration logic into the app service.
3. Update CLI and ViewModel to call app service only.
4. Keep existing public methods for compatibility (deprecated wrappers).

**Risk Level:** high

**Rollback Strategy:**

- Keep old logic in CLI/VM behind feature flag or temporary methods; revert call sites to old logic if needed.

**Minimal Tests Required:**

- Unit: `MatchingServiceTest`, `CandidateFinderTest`
- Integration: `MatchingHandlerTest`, `MatchesViewModelTest`
- Smoke: Manual CLI match flow (needs runtime check)

---

### Refactor ID: `R-002`

**Title:** Create Profile Application Service + Split Profile Controllers

**Problem:** Profile logic is spread across `ProfileHandler`, `ProfileController`, and `ProfileViewModel`, creating heavy, duplicated workflows.

**Files Involved:**

- `app/cli/ProfileHandler.java`
- `ui/controller/ProfileController.java`
- `ui/viewmodel/ProfileViewModel.java`
- `core/ProfileCompletionService.java`

**Refactor Steps:**

1. Add `core/app/ProfileAppService` for profile save/validate/completion.
2. Extract photo handling into `ProfilePhotoManager`.
3. Split `ProfileController` into subcontrollers or components (photo, basics, preferences).
4. Replace direct storage/service calls in UI and CLI with app service.

**Risk Level:** high

**Rollback Strategy:**

- Maintain existing UI/CLI methods as wrappers while app service stabilizes.

**Minimal Tests Required:**

- Unit: `ProfileCompletionServiceTest`, `UserTest`
- Integration: `ProfileCreateSelectTest`
- Smoke: JavaFX profile edit flow (needs runtime check)

---

### Refactor ID: `R-003`

**Title:** Create Messaging Application Service

**Problem:** Message send/load logic is duplicated between CLI and JavaFX.

**Files Involved:**

- `app/cli/MessagingHandler.java`
- `ui/viewmodel/ChatViewModel.java`
- `core/MessagingService.java`

**Refactor Steps:**

1. Add `core/app/MessagingAppService` (load conversations, send messages, formatting).
2. Update CLI/VM to call app service.
3. Reduce controller responsibilities to UI wiring only.

**Risk Level:** medium

**Rollback Strategy:**

- Keep existing viewmodel/handler methods and delegate back if issues appear.

**Minimal Tests Required:**

- Unit: `MessagingServiceTest`
- Integration: `MessagingHandlerTest`, `ChatViewModelTest`
- Smoke: Messaging UI flow (needs runtime check)

---

### Refactor ID: `R-004`

**Title:** Unify Match Quality Scoring in UI

**Problem:** UI uses a simplified compatibility calculation while core has `MatchQualityService`.

**Files Involved:**

- `core/MatchQualityService.java`
- `ui/viewmodel/MatchingViewModel.java`

**Refactor Steps:**

1. Add a UI-friendly summary method to `MatchQualityService`.
2. Replace UI scoring code with service call.
3. Remove duplicate scoring logic in UI.

**Risk Level:** low

**Rollback Strategy:**

- Restore local UI calculation method if needed.

**Minimal Tests Required:**

- Unit: `MatchQualityServiceTest`
- Integration: `MatchingViewModelTest`

---

### Refactor ID: `R-005`

**Title:** Modularize ServiceRegistry

**Problem:** `ServiceRegistry` is a god object with many dependencies and broad responsibilities.

**Files Involved:**

- `core/ServiceRegistry.java`
- `core/AppBootstrap.java`

**Refactor Steps:**

1. Introduce nested modules: `StorageModule`, `MatchingModule`, `MessagingModule`, `SafetyModule`, `StatsModule`.
2. Make `ServiceRegistry` a thin aggregator over modules.
3. Update call sites gradually to use modules.

**Risk Level:** medium

**Rollback Strategy:**

- Keep existing getters while adding module accessors; revert to old access if needed.

**Minimal Tests Required:**

- Unit: `ServiceRegistryTest`
- Smoke: CLI startup + JavaFX startup (needs runtime check)

---

### Refactor ID: `R-006`

**Title:** Split DatabaseManager into Schema + Migration Components

**Problem:** `DatabaseManager` mixes connection, DDL, and migrations in one large file.

**Files Involved:**

- `storage/DatabaseManager.java`
- `storage/jdbi/*`

**Refactor Steps:**

1. Create `storage/schema/SchemaInitializer` and `storage/schema/MigrationRunner`.
2. Move DDL and migration logic out of `DatabaseManager`.
3. Keep `DatabaseManager` as a lightweight coordinator.

**Risk Level:** high

**Rollback Strategy:**

- Keep old DDL methods and delegate to them if migration failures occur.

**Minimal Tests Required:**

- Integration: `H2StorageIntegrationTest`
- Smoke: App start with empty DB (needs runtime check)

---

### Refactor ID: `R-007`

**Title:** Extract User Profile Sub-Objects

**Problem:** `User` is a large mutable entity handling multiple responsibilities (profile, photos, preferences, state).

**Files Involved:**

- `core/User.java`
- `storage/jdbi/UserBindingHelper.java` (and related mappers)

**Refactor Steps:**

1. Introduce `UserProfile`, `UserPreferences`, `UserPhotos` as nested or standalone records.
2. Update storage binding/mapping to serialize sub-objects.
3. Migrate call sites to new accessors.

**Risk Level:** high

**Rollback Strategy:**

- Keep old fields in parallel and map to new structures until stable.

**Minimal Tests Required:**

- Unit: `UserTest`, `ProfileCompletionServiceTest`
- Integration: JDBI storage tests (needs runtime check)

---

### Refactor ID: `R-008`

**Title:** Decompose Large UI Controllers

**Problem:** `ProfileController`, `MatchesController`, `LoginController` are oversized with heavy imports and logic.

**Files Involved:**

- `ui/controller/ProfileController.java`
- `ui/controller/MatchesController.java`
- `ui/controller/LoginController.java`

**Refactor Steps:**

1. Extract list cell factories and validation helpers to `ui/component`.
2. Create sub-controllers for profile sections and matches tabs.
3. Reduce controllers to UI wiring and delegate to viewmodels/app services.

**Risk Level:** medium

**Rollback Strategy:**

- Keep original FXML/controller wiring until subcomponents are stable.

**Minimal Tests Required:**

- UI: `JavaFxCssValidationTest`
- Smoke: manual UI flows (needs runtime check)

---

## High-Risk Architecture Warnings

- **Layering violation:** `core/AppBootstrap` and `core/ServiceRegistry` import `storage` infrastructure. Consider moving bootstrapping to an application/infrastructure layer.
- **God classes:** `DatabaseManager`, `ProfileController`, `ProfileHandler`, `MatchesController`, `User`, `AppConfig`, `MatchQualityService`.
- **Duplicate utilities:** CLI/UI orchestration duplicated between handlers and viewmodels (confirmed by IDE duplicate report).
- **Hidden shared state:** `AppSession` is global; ensure thread safety for concurrent UI/CLI contexts (needs runtime check).
- **Duplicate ID generation:** `Match` and `Messaging` contain similar deterministic ID logic (duplicate report indicates overlap).

---

## Merge Candidates

| Files                                                                    | Suggested Destination               | Reason                                | Expected Impact |
|--------------------------------------------------------------------------|-------------------------------------|---------------------------------------|-----------------|
| `app/cli/MatchingHandler.java` + `ui/viewmodel/MatchingViewModel.java`   | `core/app/MatchingAppService.java`  | Shared orchestration logic            | High            |
| `app/cli/ProfileHandler.java` + `ui/viewmodel/ProfileViewModel.java`     | `core/app/ProfileAppService.java`   | Shared validation/persistence         | High            |
| `app/cli/MessagingHandler.java` + `ui/viewmodel/ChatViewModel.java`      | `core/app/MessagingAppService.java` | Shared messaging flow                 | High            |
| `core/ProfileCompletionService.java` + `core/ProfilePreviewService.java` | `core/ProfileQualityService.java`   | Overlapping profile metrics           | Medium          |
| `ui/util/UiHelpers.java` + `ui/util/UiServices.java`                     | `ui/util/UiToolkit.java`            | Utility dumping / overlap             | Low             |
| `core/Match.java` + `core/Messaging.java` (ID logic)                     | `core/PairId.java` helper           | Duplicate deterministic ID generation | Low             |

---

## Extraction Candidates

| File                                   | Suggested Extraction                                                                | Reason                            |
|----------------------------------------|-------------------------------------------------------------------------------------|-----------------------------------|
| `ui/controller/ProfileController.java` | `ProfilePhotoController`, `ProfileBasicsController`, `ProfilePreferencesController` | Large controller, high complexity |
| `app/cli/ProfileHandler.java`          | `ProfileAppService`, CLI prompt helper                                              | Very high complexity              |
| `storage/DatabaseManager.java`         | `SchemaInitializer`, `MigrationRunner`                                              | Monolithic infrastructure         |
| `core/MatchQualityService.java`        | `MatchQualityScorer`, `MatchQualityBreakdown`                                       | High complexity algorithm         |
| `core/User.java`                       | `UserProfile`, `UserPreferences`, `UserPhotos`                                      | God-class risk                    |

---

## Quick Wins (Low Risk, High Value)

- Replace UI compatibility logic with `MatchQualityService` to ensure consistent scoring.
- Extract duplicate `Match.generateId` / `Messaging.Conversation.generateId` into a shared helper.
- Centralize CLI prompt and input validation patterns into a `CliPrompt` helper.
- Add a typed `NavigationContext` instead of `Object` in `NavigationService`.
- Split `UiComponents` into smaller UI factories to reduce imports.

---

## Suggested Module Structure

```
core/
  app/
    matching/
    profile/
    messaging/
    stats/
  domain/
    User, Match, Messaging, Preferences, Dealbreakers, Stats, Social
  services/
    MatchingService, MessagingService, DailyService, AchievementService, ...
  config/
    AppConfig, ConfigLoader
storage/
  schema/
    SchemaInitializer, MigrationRunner
  jdbi/
ui/
  controller/
  viewmodel/
  component/
  util/
app/
  cli/
  api/
```

*Complexity estimates are heuristic; dynamic UI behavior requires runtime checks.*
