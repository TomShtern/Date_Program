# Combined Architecture Audit Report (Unified)

**Date:** 2026-02-07 (all source reports)
**Auditors:** GitHub Copilot (Claude Opus 4.6), Claude Opus 4.6 (parallel subagents)
**Scope:** Full repository — `src/main/java` + `src/test/java` (as reported)
**Sources merged:** `report.md`, `summary.md`, `reportByGPT.md`, `summaryByGPT.md`, `audit_report_v3.md`, `audit_summary_v3.md`
**Dedup policy:** Duplicate findings were merged into single entries; conflicting metrics are preserved with source attribution.

---

## 1. Repository Snapshot & Metrics (All Reported Values)

### 1.1 Metrics (Unique Values with Sources)

| Metric                              | Value                                       | Sources / Notes                                                                          |
|-------------------------------------|---------------------------------------------|------------------------------------------------------------------------------------------|
| Total Java source files             | **182** (126 main + 56 test)                | `report.md`                                                                              |
| Total code files in `src/`          | **185** (Java 182, CSS 2, XML 1)            | `reportByGPT.md`, `summaryByGPT.md`, `audit_report_v3.md`, `audit_summary_v3.md`         |
| Primary language                    | **Java 25**                                 | `report.md`, `reportByGPT.md`, `audit_report_v3.md`                                      |
| UI framework                        | **JavaFX 25** (AtlantaFX 2.1.0 theme)       | `report.md`                                                                              |
| Total lines (all)                   | **46,489**                                  | `report.md`                                                                              |
| Total code lines                    | **34,289**                                  | `report.md`                                                                              |
| Main source LOC                     | **27,634** (126 files, avg 219/file)        | `report.md`                                                                              |
| Test source LOC                     | **18,855** (56 files, avg 337/file)         | `report.md`                                                                              |
| Avg LOC per main Java file          | **154.63**                                  | `reportByGPT.md`, `summaryByGPT.md`, `audit_report_v3.md`                                |
| Test methods                        | **~808+** across 56 test files              | `report.md`                                                                              |
| CSS files                           | **2** (2,688 lines total)                   | `report.md`                                                                              |
| FXML files                          | **~11**                                     | `report.md`                                                                              |
| Unique import statements            | **287**                                     | `report.md`                                                                              |
| Largest file (main)                 | `ProfileController.java` **925 LOC**        | `report.md` (also tagged “God controller”)                                               |
| Largest file (main)                 | `ProfileController.java` **667 LOC**        | `reportByGPT.md`, `audit_report_v3.md`, `summaryByGPT.md` (metric discrepancy preserved) |
| Most imports (main)                 | `LoginController.java` **47**               | `report.md`                                                                              |
| Most complex file (heuristic)       | `ProfileHandler.java` **≈105**              | `reportByGPT.md`, `audit_report_v3.md`, `summaryByGPT.md`                                |
| Sources of mess & confusion (count) | **14** items                                | `report.md`                                                                              |
| Sources of mess & confusion (count) | **6** items                                 | `reportByGPT.md`                                                                         |
| Files confirmed valid               | **97 production files** (not flagged)       | `reportByGPT.md`                                                                         |
| Files confirmed invalid             | **29 production files** (flagged)           | `reportByGPT.md`                                                                         |
| Ideal target file count             | **~110–115** main Java (from current 126)   | `reportByGPT.md`                                                                         |
| Test coverage (reported)            | **60%** (from `analysis-summary.json`)      | `summaryByGPT.md`                                                                        |
| Duplicate blocks detected           | **6** (IDEA diagnostics)                    | `summaryByGPT.md`                                                                        |
| NEW issues found (v3)               | **171+**                                    | `audit_report_v3.md`                                                                     |
| Issue severity (v3)                 | **6 CRITICAL, 57 HIGH, 68 MEDIUM, 40+ LOW** | `audit_report_v3.md`                                                                     |
| Estimated fix effort (v3)           | **~45 hours over 6 weeks**                  | `audit_report_v3.md`                                                                     |
| v3 scope phrasing                   | **~150 source files**                       | `audit_summary_v3.md`                                                                    |

### 1.2 File Size Distribution (Main Source)

| Category | Threshold    | Count | %   |
|----------|--------------|-------|-----|
| x.large  | >1500 LOC    | 0     | 0%  |
| large    | 600–1500 LOC | 11    | 9%  |
| medium   | 300–600 LOC  | 26    | 21% |
| small    | 100–300 LOC  | 47    | 37% |
| x.small  | <100 LOC     | 42    | 33% |

### 1.3 Top 13 Largest Files (Main Source)

| File                                   | LOC | Imports | Category                     |
|----------------------------------------|-----|---------|------------------------------|
| `ui/controller/ProfileController.java` | 925 | 38      | God controller               |
| `core/User.java`                       | 828 | 12      | God model                    |
| `app/cli/ProfileHandler.java`          | 807 | 29      | God handler                  |
| `ui/controller/MatchesController.java` | 787 | 44      | Large controller             |
| `storage/DatabaseManager.java`         | 775 | ~15     | Mixed responsibilities       |
| `core/MatchQualityService.java`        | 752 | 16      | Large service                |
| `app/cli/MatchingHandler.java`         | 739 | 32      | Large handler                |
| `ui/viewmodel/ProfileViewModel.java`   | 699 | 33      | Layer violation              |
| `core/AppConfig.java`                  | 673 | 3       | Justified (40+ param record) |
| `ui/controller/LoginController.java`   | 645 | 47      | Over-coupled                 |
| `core/ServiceRegistry.java`            | 472 | 13      | God class                    |
| `ui/viewmodel/MatchesViewModel.java`   | 457 | ~20     | Layer violation              |
| `core/Dealbreakers.java`               | 442 | 6       | Moderate (domain model)      |

### 1.4 Files Confirmed Valid / Invalid (ReportByGPT)

**Valid (97 production files):** All remaining `src/main/java` files **not listed** in the flagged summaries below.

**Invalid / Flagged (29 production files):**
- `app/cli/MatchingHandler.java`
- `app/cli/MessagingHandler.java`
- `app/cli/ProfileHandler.java`
- `core/AchievementService.java`
- `core/AppConfig.java`
- `core/DailyService.java`
- `core/Dealbreakers.java`
- `core/MatchQualityService.java`
- `core/ProfileCompletionService.java`
- `core/User.java`
- `storage/DatabaseManager.java`
- `storage/jdbi/JdbiSocialStorage.java`
- `storage/jdbi/JdbiStatsStorage.java`
- `storage/jdbi/JdbiUserStorage.java`
- `ui/ViewModelFactory.java`
- `ui/component/UiComponents.java`
- `ui/controller/AchievementPopupController.java`
- `ui/controller/ChatController.java`
- `ui/controller/LoginController.java`
- `ui/controller/MatchesController.java`
- `ui/controller/MatchingController.java`
- `ui/controller/ProfileController.java`
- `ui/viewmodel/ChatViewModel.java`
- `ui/viewmodel/DashboardViewModel.java`
- `ui/viewmodel/LoginViewModel.java`
- `ui/viewmodel/MatchesViewModel.java`
- `ui/viewmodel/MatchingViewModel.java`
- `ui/viewmodel/ProfileViewModel.java`
- `ui/viewmodel/StatsViewModel.java`

---

## 1.5 Implementation Plan Tracker

> **Legend:** 📋 = Task written in plan | 🔜 = Assigned to future plan | 📝 = Backlog (unassigned)
>
> **Plans:** P01 = Storage Hardening | P02 = Core Thread Safety + Dead Code | P03 = Core Restructuring | P04 = Logging | P05 = ViewModel + FX-Thread | P06 = CLI + App Services | P07 = UI Controllers | P08 = Tests + Constants + Null Safety

| ID | Summary | Sev | Plan | Note |
|----|---------|-----|------|------|
| **Thread Safety (§9.1)** | | | | |
| TS-001 | DatabaseManager.getConnection() race | CRIT | 📋 P01 | Task 1 |
| TS-002 | MatchingViewModel candidateQueue | CRIT | 🔜 P05 | |
| TS-003 | AppBootstrap fields non-volatile | CRIT | 📋 P02 | Task 1 |
| TS-004 | NavigationService history deque | CRIT | 🔜 P07 | |
| TS-005 | UndoService transactionExecutor | CRIT | 📋 P02 | Task 3 |
| TS-006 | DailyService check-then-act | CRIT | 📋 P02 | Task 4 |
| TS-007 | ImageCache.preload() TOCTOU | HIGH | 🔜 P05 | |
| TS-008 | PerformanceMonitor mixed sync | HIGH | 📋 P02 | Task 5 |
| TS-009 | ChatViewModel observable lists | HIGH | 🔜 P05 | |
| TS-010 | AppSession listener logging | HIGH | 📋 P02 | Task 2 |
| TS-011 | ViewModelFactory sync | MED | 🔜 P05 | |
| TS-012 | SessionService init | MED | 📋 P03 | |
| TS-013 | NavigationService.getInstance() | MED | 🔜 P07 | |
| **Exception Handling (§9.3)** | | | | |
| EH-001 | DatabaseManager isVersionApplied | HIGH | 📋 P01 | Task 2 |
| EH-002 | AppSession swallows stack trace | HIGH | 📋 P02 | Task 2 |
| EH-003 | NavigationService FXML errors | HIGH | 🔜 P07 | |
| EH-004 | MatchingService fallback unguarded | HIGH | 📋 P02 | Task 6 |
| EH-005–010 | ConfigLoader silent catches | MED | 📋 P02 | Task 7 |
| EH-011 | HTTP routes throw 500 | MED | 🔜 P06 | |
| EH-012 | Result vs Exception inconsistency | MED | 🔜 P06 | |
| EH-013–014 | Log level inconsistencies | LOW | 🔜 P04 | |
| **SQL / Storage (§9.4)** | | | | |
| SQL-001 | N+1 getActiveMatchesFor | HIGH | 📋 P01 | Task 3 |
| SQL-002 | No transaction recordLike | HIGH | 📋 P02 | Task 6 |
| SQL-003 | Orphan records on match end | HIGH | 📋 P01 | Task 9 (**added**) |
| SQL-004 | SELECT * in 8 locations | MED | 📋 P01 | Task 4 |
| SQL-005 | Missing indexes | MED | 📋 P01 | Task 5 |
| SQL-006 | CSV serialization limits | MED | 📝 BL | Design change |
| SQL-007 | getAllLatestUserStats subquery | MED | 📋 P01 | Task 6 |
| SQL-008 | Null handling aliases | MED | 📋 P01 | Task 7c |
| SQL-009 | Timestamp→Instant precision | MED | 📋 P01 | Task 9 (**added**) |
| SQL-010 | CSV enum silent skip | MED | 📋 P01 | Task 9 (**added**) |
| SQL-011 | Timezone-naive Instant | MED | 📋 P01 | Task 9 (**added**) |
| SQL-012–014 | Minor inefficiencies | LOW | 📝 BL | |
| **Test Quality (§9.2)** | | | | |
| TQ-001 | Date-dependent tests (7 files) | HIGH | 🔜 P08 | |
| TQ-002 | Complex test setup | HIGH | 🔜 P08 | |
| TQ-003–005 | Test behavior issues | HIGH | 🔜 P08 | |
| TQ-006–015 | Missing edge cases | MED | 🔜 P08 | |
| TQ-016–028 | Vague assertions | LOW | 🔜 P08 | |
| **Interface Design (§9.5)** | | | | |
| IF-001 | StatsStorage fat interface | HIGH | 🔜 P05 | |
| IF-002 | MessagingStorage mixed | HIGH | 🔜 P05 | |
| IF-003 | CLI handlers no interface | HIGH | 🔜 P06 | |
| IF-004 | Inconsistent Result types | HIGH | 🔜 P06 | |
| IF-005 | Optional vs null | HIGH | 📝 BL | |
| IF-006–012 | Various interface issues | MED | 📝 BL | |
| IF-013–016 | Contract clarity | LOW | 📝 BL | |
| **Null Safety (§9.6)** | | | | |
| NS-001 | Missing @Nullable (18 methods) | HIGH | 🔜 P08 | |
| NS-002 | MapperHelper mutable list | MED | 📋 P01 | Task 7a |
| NS-003 | EnumMenu inconsistency | MED | 🔜 P06 | |
| NS-004 | MapperHelper readEnum | MED | 📋 P01 | Task 7b |
| NS-005–026 | Various null safety | LOW | 🔜 P08 | |
| **Magic Numbers (§9.7)** | 60+ items | — | 🔜 P08 | |
| **Refactors (§12)** | | | | |
| R-001 | ViewModel→Storage violations | — | 🔜 P05 | |
| R-002 | FX-thread DB queries | — | 🔜 P05 | |
| R-003 | core/ layer violations | — | 📋 P03 | |
| R-004 | Split core/ sub-packages | — | 📋 P03 | |
| R-005 | LoggingSupport interface | — | 🔜 P04 | |
| R-006 | Extract StorageFactory | — | 📋 P03 | |
| R-007 | AppConfig constructor inject | — | 📋 P03 | |
| R-008 | Extract large methods | — | 🔜 P06/P07 | |
| R-009 | Genericize dealbreaker methods | — | 🔜 P06 | |
| R-010 | Merge CliConstants+CliUtilities | — | 🔜 P06 | |
| R-011 | Delete PurgeService | — | 📋 P02 | Task 8 |
| R-012 | Inline SoftDeletable | — | 📋 P03 | |
| R-013 | Inline EnumSetUtil | — | 🔜 P06 | |
| R-014 | Merge ErrorMessages | — | 📋 P02 | Task 9 |
| R-015 | Move MODULE_OVERVIEW.md | — | 📋 P02 | Task 10 |
| R-016 | Clean root artifacts | — | 📋 P02 | Task 11 |
| R-017 | Shared handleBack() | — | 🔜 P07 | |
| R-018 | Nest standalone enums | — | 📋 P03 | |
| **Dead Code (§7)** | | | | |
| — | PurgeService | LOW | 📋 P02 | Task 8 |
| — | SoftDeletable | LOW | 📋 P03 | |
| — | EnumSetUtil | LOW | 🔜 P06 | |
| — | ErrorMessages | LOW | 📋 P02 | Task 9 |
| — | MODULE_OVERVIEW.md (×2) | LOW | 📋 P02 | Task 10 |
| **Duplication (§6)** | | | | |
| — | Logging helpers (23 files) | — | 🔜 P04 | |
| — | Dealbreaker methods (×6) | — | 🔜 P06 | |
| — | handleBack() (×6 controllers) | — | 🔜 P07 | |
| — | AppConfig.defaults() (×8 files) | — | 📋 P03 | |
| — | PairId generation (Match+Msg) | — | 📝 BL | |
| **File-Level (§5, §10)** | | | | |
| — | ProfileCompletionSvc+PreviewSvc merge | — | 📋 P03 | |
| — | UiHelpers+UiServices merge | — | 🔜 P07 | |
| — | User.java split | — | 📝 BL | Risky |
| — | DatabaseManager split | — | 📋 P03 | SchemaInit+Migration |
| — | ServiceRegistry StorageFactory | — | 📋 P03 | |

---

## 2. Consolidated Sources of Mess & Confusion

### 2.1 Combined List (All Reported Items)

**Structural / layering:**
- **Flat `core/` package**: 44 files with mixed models/services/utils/enums/config.
- **core → storage imports**: `AppBootstrap`, `ServiceRegistry` depend on infrastructure.
- **core → framework imports**: `ConfigLoader` depends on Jackson.
- **ViewModel → storage imports**: 6 of 8 ViewModels bypass services.

**Duplication / bloat:**
- **Duplicated logging helpers** in 23 files (GuardLogStatement workaround).
- **CLI/UI orchestration duplication** between handlers and ViewModels: `app/cli/MatchingHandler.java`, `ui/viewmodel/MatchingViewModel.java`, `app/cli/ProfileHandler.java`, `ui/viewmodel/ProfileViewModel.java`, `app/cli/MessagingHandler.java`, `ui/viewmodel/ChatViewModel.java`.
- **Copy‑paste dealbreaker methods** (6 identical methods in `ProfileHandler`).
- **Duplicate fragments (IDEA)**: `MatchingHandler`↔`MatchingViewModel`, `AchievementService`↔`ProfilePreviewService`, `Match`↔`Messaging`, and internal duplication in `LoginController`.

**Oversized classes:**
- **Oversized UI controllers**: `ProfileController`, `MatchesController`, `LoginController`.
- **Oversized CLI handlers**: `ProfileHandler`, `MatchingHandler`, `MessagingHandler`.
- **God-class infrastructure**: `DatabaseManager`, `ServiceRegistry`.
- **Large domain/config classes**: `User`, `AppConfig`, `MatchQualityService`.

**Concurrency / runtime risk:**
- **FX-thread DB queries** in 3 ViewModels (UI freeze risk).
- **Thread-safety violations** (11 files) and **N+1 queries** (3 files) in v3 audit.

**Configuration / API patterns:**
- **`AppConfig.defaults()` scattered** across 8 files.
- **Singleton overuse** (5 singletons) complicating testing.

**Code health:**
- **Large methods**: 5+ methods exceeding 120 LOC.
- **Orphaned/dead code**: `PurgeService`, `SoftDeletable`, `EnumSetUtil`.
- **Non-source files in source tree**: `core/MODULE_OVERVIEW.md`, `storage/MODULE_OVERVIEW.md`.
- **Root-level artifact clutter**: 37 doc/artifact files in repository root.

**NEW (v3) audit categories:**
- Thread safety violations (13 issues).
- Test date-dependence (7 test files).
- Swallowed exceptions (6 files).
- Fat storage interfaces (4 interfaces).
- Magic numbers (25+ files).
- Missing `@Nullable` annotations (10 files).

### 2.2 Why This Happened, How It Manifests, and Options

**Why it happened:** CLI and JavaFX were developed in parallel; both orchestrate flows directly against core services. Infrastructure bootstrapping remained in `core/` for convenience, pulling storage into the domain layer.

**How it manifests:** Oversized UI controllers and CLI handlers, duplication confirmed by IDE diagnostics, import counts 20–47, and large infrastructure god objects (`DatabaseManager`, `ServiceRegistry`).

**Prevention (behavioral):** Enforce clean layer boundaries, add a shared application service layer, and isolate infrastructure initialization in a dedicated module. UI/CLI should never orchestrate multi-step workflows.

**Options:**
1. **Keep both CLI + JavaFX** and extract shared application services (**recommended**).
2. **Deprecate one interface** (CLI or JavaFX) and keep only one orchestration surface.

**Suggested happy path:** Introduce `core/app/*` services, modularize service registry, split oversized controllers, and preserve public APIs via adapters while slimming handlers/viewmodels.

---

## 3. Layering Violations (Detailed)

### 3.1 core/ → storage/ (Must Not Happen)

| Core File                   | Imports From                                             | Violation                              |
|-----------------------------|----------------------------------------------------------|----------------------------------------|
| `core/AppBootstrap.java`    | `storage.DatabaseManager`                                | Core depends on storage implementation |
| `core/ServiceRegistry.java` | `storage.DatabaseManager`, `storage.TransactionTemplate` | Core wires storage implementations     |
| `core/ConfigLoader.java`    | `com.fasterxml.jackson.databind.*`                       | Core depends on framework library      |

### 3.2 ui/viewmodel/ → core/storage/ (Should Not Happen)

| ViewModel                   | Storage Interfaces Imported                                  |
|-----------------------------|--------------------------------------------------------------|
| `MatchesViewModel.java`     | `BlockStorage`, `LikeStorage`, `MatchStorage`, `UserStorage` |
| `LoginViewModel.java`       | `UserStorage`                                                |
| `StatsViewModel.java`       | `LikeStorage`, `MatchStorage`                                |
| `DashboardViewModel.java`   | `MatchStorage`                                               |
| `PreferencesViewModel.java` | `UserStorage`                                                |
| `ProfileViewModel.java`     | `UserStorage`                                                |

**Total violations:** 9 (3 core→storage/framework + 6 viewmodel→storage)

---

## 4. Architectural Clusters & Module Candidates

### 4.1 Clusters with LOC (Report.md)

**Cluster A: `matching` (7 files, ~2,917 LOC)**
- `CandidateFinder.java` (362) — candidate discovery pipeline
- `MatchingService.java` (269) — like/match orchestration
- `MatchQualityService.java` (752) — quality scoring
- `SessionService.java` (169) — swipe session management
- `UndoService.java` (224) — undo support
- `UndoState.java` (84) — undo state tracking
- `SwipeSession.java` (221) — session model

**Cluster B: `messaging` (3 files, ~698 LOC)**
- `Messaging.java` (277) — domain model
- `MessagingService.java` (256) — send/receive orchestration
- `RelationshipTransitionService.java` (165) — match state transitions

**Cluster C: `profile` (7 files, ~3,282 LOC)**
- `User.java` (828)
- `Dealbreakers.java` (442)
- `Preferences.java` (206)
- `ProfileCompletionService.java` (344)
- `PacePreferences.java` (85)
- `ProfilePreviewService.java` (152)
- `ValidationService.java` (162)

**Cluster D: `safety` (2 files, ~341 LOC)**
- `TrustSafetyService.java` (241)
- `UserInteractions.java` (100)

**Cluster E: `stats` (4 files, ~696 LOC)**
- `StatsService.java` (156)
- `AchievementService.java` (251)
- `Stats.java` (197)
- `Achievement.java` (92)

**Cluster F: `daily` (4 files, ~610 LOC)**
- `DailyService.java` (262)
- `DailyPick.java` (14)
- `StandoutsService.java` (268)
- `Standout.java` (66)

**Cluster G: `config / bootstrap` (5 files, ~1,077 LOC)**
- `AppConfig.java` (673)
- `ConfigLoader.java` (202)
- `AppBootstrap.java` (70)
- `AppSession.java` (69)
- `AppClock.java` (63)

**Remaining in `core/` (12 files, ~1,449 LOC)**
- `Match.java` (257)
- `Social.java` (83)
- `ServiceRegistry.java` (472) — DI container
- `CleanupService.java` (69)
- `PurgeService.java` (60) — dead code
- `PerformanceMonitor.java` (167)
- `EnumSetUtil.java` (70)
- `ErrorMessages.java` (49)
- `SoftDeletable.java` (22)
- `Gender.java` (7)
- `UserState.java` (11)
- `VerificationMethod.java` (11)

### 4.2 Module Candidates (ReportByGPT)

**Matching & Discovery** → `core/app/matching` + `ui/matching` + `cli/matching`
- Files: `CandidateFinder`, `MatchingService`, `DailyService`, `UndoService`, `MatchQualityService`, `MatchingHandler`, `MatchingViewModel`, `MatchingController`, `MatchRoutes`
- Recommended: add `MatchingAppService`, extract `SwipeFlow`/`CandidateLoader`

**Profile & Identity** → `core/app/profile` + `ui/profile`
- Files: `User`, `Preferences`, `Dealbreakers`, `ProfileCompletionService`, `ProfilePreviewService`, `ProfileHandler`, `ProfileViewModel`, `ProfileController`
- Recommended: add `ProfileAppService`, `ProfilePhotoManager`, `ProfileCompletenessEvaluator`

**Messaging** → `core/app/messaging` + `ui/messaging` + `cli/messaging`
- Files: `Messaging`, `MessagingService`, `MessagingHandler`, `ChatViewModel`, `ChatController`, `MessagingRoutes`
- Recommended: add `MessagingAppService`, extract `ConversationSelector`, `MessageFormatter`

**Stats & Achievements** → `core/app/stats` + `ui/stats`
- Files: `Stats`, `StatsService`, `AchievementService`, `StatsHandler`, `StatsViewModel`, `StatsController`
- Recommended: add `StatsAppService`, `StatsFormatter`, `AchievementBadgeMapper`

**Infrastructure & Storage** → `storage/schema` + `storage/jdbi`
- Files: `DatabaseManager`, `storage/jdbi/*`
- Recommended: split schema/migration concerns

---

## 5. File-by-File Flagged Summaries (Merged from ReportByGPT)

> One entry per flagged file; values and actions are preserved from `reportByGPT.md`.

### `app/cli/MatchingHandler.java`
- **LOC:** 608 | **Cyclomatic:** 81 (heuristic) | **Imports:** 32
- **Issues:** High complexity; mixes orchestration, CLI I/O, domain decisions; duplicate fragments with `MatchingViewModel` and with `ProfileHandler`/`StatsHandler`.
- **Suggested Action:** Extract `core/app/MatchingAppService`; slim CLI wrapper.

### `app/cli/MessagingHandler.java`
- **LOC:** 303 | **Cyclomatic:** 41 (heuristic) | **Imports:** 22
- **Issues:** CLI handler orchestrates messaging lifecycle, similar to `ChatViewModel` flow; high orchestration complexity.
- **Suggested Action:** Extract `core/app/MessagingAppService`.

### `app/cli/ProfileHandler.java`
- **LOC:** 642 | **Cyclomatic:** 105 (heuristic) | **Imports:** 29
- **Issues:** Largest CLI file; prompts, validation, persistence, domain logic; duplicate fragments with `MatchingHandler` and `StatsHandler`.
- **Suggested Action:** Extract `core/app/ProfileAppService`; split CLI flows.

### `core/AchievementService.java`
- **LOC:** 204 | **Cyclomatic:** 42 (heuristic) | **Imports:** 13
- **Issues:** Duplicate fragment with `ProfilePreviewService`; handles data formatting.
- **Suggested Action:** Extract shared helper for profile metrics/achievement computation.

### `core/AppConfig.java`
- **LOC:** 561 | **Cyclomatic:** 8 (heuristic) | **Imports:** 3
- **Issues:** Large configuration surface; hard to navigate.
- **Suggested Action:** Split into nested grouped configs (`Limits`, `Validation`, `Weights`).

### `core/DailyService.java`
- **LOC:** 244 | **Cyclomatic:** 24 (heuristic) | **Imports:** 20
- **Issues:** Mixes daily pick, limit tracking, eligibility logic.
- **Suggested Action:** Extract `DailyPickService` + `DailyLimitService` modules.

### `core/Dealbreakers.java`
- **LOC:** 343 | **Cyclomatic:** 38 (heuristic) | **Imports:** 5
- **Issues:** Large domain class with evaluator logic intertwined.
- **Suggested Action:** Split into `Dealbreakers` + `DealbreakersEvaluator`.

### `core/MatchQualityService.java`
- **LOC:** 513 | **Cyclomatic:** 80 (heuristic) | **Imports:** 16
- **Issues:** High complexity scoring logic; UI uses simplified scoring → risk of inconsistency.
- **Suggested Action:** Extract `MatchQualityScorer`, `MatchQualityBreakdown`.

### `core/ProfileCompletionService.java`
- **LOC:** 323 | **Cyclomatic:** 39 (heuristic) | **Imports:** 3
- **Issues:** Overlaps with profile preview logic.
- **Suggested Action:** Merge/rename as `ProfileQualityService`.

### `core/User.java`
- **LOC:** 571 | **Cyclomatic:** 30 (heuristic) | **Imports:** 12
- **Issues:** Large mutable domain object mixing profile, preferences, photos, state transitions.
- **Suggested Action:** Extract `UserProfile`, `UserPreferences`, `UserPhotos`.

### `storage/DatabaseManager.java`
- **LOC:** 605 | **Cyclomatic:** 19 (heuristic) | **Imports:** 10
- **Issues:** Monolithic infra class (pool creation + schema creation + migrations).
- **Suggested Action:** Split into `SchemaInitializer`, `MigrationRunner`, `ConnectionProvider`.

### `storage/jdbi/JdbiSocialStorage.java`
- **LOC:** 170 | **Cyclomatic:** 5 (heuristic) | **Imports:** 24
- **Issues:** High import count; SQL fragments and mappers scattered.
- **Suggested Action:** Extract shared `SqlFragments` / mapper helpers.

### `storage/jdbi/JdbiStatsStorage.java`
- **LOC:** 235 | **Cyclomatic:** 2 (heuristic) | **Imports:** 20
- **Issues:** Low complexity but many dependencies; SQL/mapping overgrown.
- **Suggested Action:** Group query methods into helpers.

### `storage/jdbi/JdbiUserStorage.java`
- **LOC:** 267 | **Cyclomatic:** 19 (heuristic) | **Imports:** 28
- **Issues:** High import count suggests mapper/SQL diffusion.
- **Suggested Action:** Extract `UserSql`, `UserMapper` helpers.

### `ui/ViewModelFactory.java`
- **LOC:** 195 | **Cyclomatic:** 28 (heuristic) | **Imports:** 25
- **Issues:** Wiring logic and viewmodel lifecycle conflated; many dependencies.
- **Suggested Action:** Split into `ViewModelProvider` + `ViewModelRegistry`.

### `ui/component/UiComponents.java`
- **LOC:** 224 | **Cyclomatic:** 9 (heuristic) | **Imports:** 29
- **Issues:** Utility dumping ground for UI component creation.
- **Suggested Action:** Split into `LoadingOverlays`, `Dialogs`, `Cells`.

### `ui/controller/AchievementPopupController.java`
- **LOC:** 159 | **Cyclomatic:** 6 (heuristic) | **Imports:** 22
- **Issues:** High dependency count for a popup controller.
- **Suggested Action:** Extract shared popup base or reusable component.

### `ui/controller/ChatController.java`
- **LOC:** 214 | **Cyclomatic:** 10 (heuristic) | **Imports:** 24
- **Issues:** Controller performs messaging orchestration; overlaps with ViewModel.
- **Suggested Action:** Extract `MessagingAppService` + slimmer controller.

### `ui/controller/LoginController.java`
- **LOC:** 525 | **Cyclomatic:** 39 (heuristic) | **Imports:** 47
- **Issues:** Combines list cell logic, filtering, navigation, login flow; internal duplication.
- **Suggested Action:** Split into `LoginListCell`, `LoginSearchController`, `LoginFlowService`.

### `ui/controller/MatchesController.java`
- **LOC:** 623 | **Cyclomatic:** 42 (heuristic) | **Imports:** 44
- **Issues:** Large controller; mixes UI rendering with business decisions.
- **Suggested Action:** Extract `MatchesSectionController`, `LikesSectionController`.

### `ui/controller/MatchingController.java`
- **LOC:** 367 | **Cyclomatic:** 29 (heuristic) | **Imports:** 31
- **Issues:** Coordinates matching workflow; logic should live in shared app service.
- **Suggested Action:** Extract `MatchingAppService` + slimmer controller.

### `ui/controller/ProfileController.java`
- **LOC:** 667 | **Cyclomatic:** 76 (heuristic) | **Imports:** 38
- **Issues:** Largest UI controller; handles photos, validation, navigation, persistence.
- **Suggested Action:** Split into `ProfilePhotoController`, `ProfileBasicsController`, `ProfilePreferencesController`.

### `ui/viewmodel/ChatViewModel.java`
- **LOC:** 262 | **Cyclomatic:** 28 (heuristic) | **Imports:** 23
- **Issues:** Orchestration overlaps with CLI.
- **Suggested Action:** Extract `MessagingAppService`.

### `ui/viewmodel/DashboardViewModel.java`
- **LOC:** 262 | **Cyclomatic:** 29 (heuristic) | **Imports:** 26
- **Issues:** High dependency count and orchestration logic.
- **Suggested Action:** Extract `DashboardAppService`.

### `ui/viewmodel/LoginViewModel.java`
- **LOC:** 301 | **Cyclomatic:** 36 (heuristic) | **Imports:** 23
- **Issues:** Large viewmodel with logic that could be delegated to services.
- **Suggested Action:** Extract `LoginAppService`.

### `ui/viewmodel/MatchesViewModel.java`
- **LOC:** 378 | **Cyclomatic:** 41 (heuristic) | **Imports:** 31
- **Issues:** Possible overlap with `LikerBrowserHandler`.
- **Suggested Action:** Extract `MatchesAppService`.

### `ui/viewmodel/MatchingViewModel.java`
- **LOC:** 267 | **Cyclomatic:** 31 (heuristic) | **Imports:** 22
- **Issues:** Duplicate fragment with `MatchingHandler`.
- **Suggested Action:** Extract `MatchingAppService`.

### `ui/viewmodel/ProfileViewModel.java`
- **LOC:** 522 | **Cyclomatic:** 61 (heuristic) | **Imports:** 33
- **Issues:** Large viewmodel overlapping with `ProfileHandler` + `ProfileController`.
- **Suggested Action:** Extract `ProfileAppService` + smaller sections.

### `ui/viewmodel/StatsViewModel.java`
- **LOC:** 165 | **Cyclomatic:** 16 (heuristic) | **Imports:** 21
- **Issues:** High dependency count for a stats ViewModel.
- **Suggested Action:** Extract `StatsAppService`.

---

## 6. Duplication Catalog (Report.md)

### 6.1 Logging Helpers (23 files)

**Pattern:** Each file defines private `logInfo()`, `logWarn()`, `logError()`, `logDebug()`, `logTrace()` with SLF4J level guards.

**Affected files:**
- CLI: `MatchingHandler`, `ProfileHandler`, `MessagingHandler`, `SafetyHandler`, `StatsHandler`, `ProfileNotesHandler`, `LikerBrowserHandler`, `RelationshipHandler`
- ViewModels: `LoginViewModel`, `DashboardViewModel`, `MatchesViewModel`, `PreferencesViewModel`, `StatsViewModel`, `ProfileViewModel`, `MatchingViewModel`, `ChatViewModel`
- Controllers: `ProfileController`, `LoginController`, `MatchesController`, `MatchingController`
- Other: `CandidateFinder`, `NavigationService`, `ViewModelFactory`

**Estimated duplication:** ~2,645 LOC.

**Fix:** Create shared `LoggingSupport` interface with default methods.

### 6.2 Dealbreaker Edit Methods (ProfileHandler)

**Pattern:** 6 methods at L635–L697 follow identical structure — prompt enum, set dealbreaker field, save.

**Fix:** Create generic `editEnumDealbreaker(User, Dealbreakers, String, Class<E>, BiConsumer<Dealbreakers, E>)`.

### 6.3 handleBack() Navigation (6 controllers)

**Pattern:** `handleBack()` calls `navigationService.goBack()` in 6 controllers.

**Fix:** Move to `BaseController` as shared method.

### 6.4 AppConfig.defaults() Static Pattern (8 files)

**Pattern:** `private static final AppConfig CONFIG = AppConfig.defaults()` duplicated across 8 files.

**Files:** `User.java`, `MessagingService.java`, `ProfileCompletionService.java`, `PurgeService.java`, `LoginController.java`, `ProfileViewModel.java`, `LoginViewModel.java`, `PreferencesViewModel.java`

**Fix:** Inject `AppConfig` via constructor.

---

## 7. Dead / Orphaned Code (Report.md)

| File                         | LOC | Evidence                             | Action                                | Plan |
|------------------------------|-----|--------------------------------------|---------------------------------------|------|
| `core/PurgeService.java`     | 60  | Zero inbound imports                 | Delete or merge into `CleanupService` | 📋 P02 |
| `core/SoftDeletable.java`    | 22  | 2 implementors, no polymorphic usage | Inline into `User` and `Match`        | 📋 P03 |
| `core/EnumSetUtil.java`      | 70  | Only 1 consumer (`ProfileHandler`)   | Inline or generalize                  | 🔜 P06 |
| `core/ErrorMessages.java`    | 49  | Only 3 consumers                     | Merge into `ValidationService`        | 📋 P02 |
| `core/MODULE_OVERVIEW.md`    | N/A | Non-source file in source package    | Move to `docs/`                       | 📋 P02 |
| `storage/MODULE_OVERVIEW.md` | N/A | Non-source file in source package    | Move to `docs/`                       | 📋 P02 |

---

## 8. High‑Risk Architecture Warnings (Combined)

### 8.1 Layer Direction Violations
- **core → storage:** `AppBootstrap`, `ServiceRegistry`
- **core → framework:** `ConfigLoader` (Jackson)
- **viewmodel → storage:** 6 ViewModels bypass service layer

### 8.2 God-Class Detail (Report.md)

| File                | LOC | Fields | Max Method LOC   | Verdict                  |
|---------------------|-----|--------|------------------|--------------------------|
| `ServiceRegistry`   | 472 | 26+    | ~100 (`buildH2`) | Extract `StorageFactory` |
| `ProfileController` | 925 | ~20    | 166              | Extract helpers          |
| `ProfileHandler`    | 807 | ~15    | 6× duplicate     | Genericize               |
| `MatchesController` | 787 | ~15    | ~200             | Extract tabs helper      |

**Additional detail:** `ServiceRegistry` includes a **25‑parameter constructor** (reported in `report.md`).

### 8.3 Hidden Shared State (Singletons)

| Singleton                         | Pattern               | Risk                       |
|-----------------------------------|-----------------------|----------------------------|
| `AppSession.getInstance()`        | Thread-safe singleton | Test isolation difficult   |
| `AppBootstrap.initialize()`       | Once-only gate        | `reset()` exists for tests |
| `DatabaseManager.getInstance()`   | Connection pool       | Single DB instance         |
| `NavigationService.getInstance()` | UI navigation         | UI-only, acceptable        |
| `AppClock`                        | Testable clock        | `setFixed()` for tests     |

### 8.4 Duplicate ID Generation
- `Match` and `Messaging` contain similar deterministic ID logic → extract `PairId` helper.

---

## 9. Detailed v3 Issue Categories (NEW Findings)

### 9.0 v3 Category Summary (from audit_summary_v3)

| Category                  | Issues Found | Severity Distribution          |
|---------------------------|--------------|--------------------------------|
| Thread Safety             | 13           | 6 CRITICAL, 4 HIGH, 3 MEDIUM   |
| Test Quality              | 28           | 12 HIGH, 10 MEDIUM, 6 LOW      |
| Exception Handling        | 14           | 4 HIGH, 7 MEDIUM, 3 LOW        |
| SQL/Storage               | 14           | 3 HIGH, 8 MEDIUM, 3 LOW        |
| Interface Design          | 16           | 5 HIGH, 8 MEDIUM, 3 LOW        |
| Null Safety/API Contracts | 26           | 8 HIGH, 12 MEDIUM, 6 LOW       |
| Magic Numbers/Constants   | 60+          | 0 CRITICAL, 20 MEDIUM, 40+ LOW |

### 9.1 Thread Safety Issues (13 total)

**CRITICAL: TS-001 — DatabaseManager.getConnection() race** (`storage/DatabaseManager.java:97-104`) 📋 P01

```java
public Connection getConnection() throws SQLException {
    if (!initialized) {  // CHECK (not synchronized)
        initializeSchema();
    }
    if (dataSource == null) {  // CHECK-THEN-ACT (race)
        initializePool();
    }
    return dataSource.getConnection();
}
```

- **Problem:** `initialized` is not volatile. Concurrent calls can trigger duplicate schema initialization.
- **Impact:** Schema corruption, pool races, startup failures.
- **Fix:** `private static volatile boolean initialized;` and volatile `dataSource`.

**CRITICAL: TS-002 — MatchingViewModel candidateQueue unsynchronized** (`ui/viewmodel/MatchingViewModel.java:39,149-150,164`) 🔜 P05

```java
private final Queue<User> candidateQueue = new LinkedList<>();  // NOT thread-safe
```

- **Problem:** Virtual thread writes + FX thread reads without sync.
- **Impact:** `ConcurrentModificationException`, wrong candidates.
- **Fix:** `ConcurrentLinkedQueue<User>` or explicit locking.

**CRITICAL: TS-003 — AppBootstrap services/dbManager non-volatile** (`core/AppBootstrap.java:14-16,51`) 📋 P02

```java
private static ServiceRegistry services;      // NOT volatile!
private static DatabaseManager dbManager;     // NOT volatile!
```

- **Problem:** Unsynchronized read in `getServices()`.
- **Impact:** Stale/null `ServiceRegistry`.
- **Fix:** Make both fields `volatile`.

**CRITICAL: TS-004 — NavigationService navigationHistory not thread-safe** (`ui/NavigationService.java:40-41`) 🔜 P07

```java
private final Deque<ViewType> navigationHistory = new ArrayDeque<>();
```

- **Problem:** Accessed from FX thread + background FXML loads without sync.
- **Impact:** ConcurrentModificationException, lost history.

**CRITICAL: TS-005 — UndoService transactionExecutor non-volatile** (`core/UndoService.java:38,73-74`) 📋 P02

```java
private TransactionExecutor transactionExecutor;  // NOT volatile
```

- **Problem:** Unsynchronized read/write.
- **Impact:** Thread may not see updated executor; non-atomic deletes.

**CRITICAL: TS-006 — DailyService check-then-act on ConcurrentHashMap** (`core/DailyService.java:35,142-157`) 📋 P02

- **Problem:** `cachedDailyPicks` is concurrent but check-then-act not atomic.
- **Fix:** `computeIfAbsent()` with `selectRandom()`.

**HIGH: TS-007 — ImageCache.preload() TOCTOU race** (`ui/util/ImageCache.java:172-188`). 🔜 P05

**HIGH: TS-008 — PerformanceMonitor min/max non-atomic** (`core/PerformanceMonitor.java:154-166`). 📋 P02

**HIGH: TS-009 — ChatViewModel observable lists race** (`ui/viewmodel/ChatViewModel.java:35-36`). 🔜 P05

**HIGH: TS-010 — AppSession listener callback outside sync** (`core/AppSession.java:37-42`). 📋 P02

**MEDIUM: TS-011–TS-013** — synchronization improvements in `ViewModelFactory` 🔜 P05, `SessionService` Stripe init 📋 P03, and `NavigationService.getInstance()` 🔜 P07.

---

### 9.2 Test Quality Issues (28 total)

**HIGH: TQ-001 — Temporal boundary tests (7 files)** 🔜 P08
- `DailyPickServiceTest.java:280`
- `StandoutsServiceTest.java:48,68`
- `AchievementServiceTest.java:120`
- `ProfilePreviewServiceTest.java:33`
- `DealbreakersEvaluatorTest.java:31,37`
- `DailyServiceTest.java:49,191`

**Problem:** `LocalDate.now().minusYears(age)` makes tests flaky around birthdays.
**Fix:** Inject fixed `Clock` into services/test helpers.

**HIGH: TQ-002 — Overly complex test setup** 🔜 P08
- `DailyPickServiceTest.java:295-535` (inline storages)
- `AchievementServiceTest.java:~500+` lines

**Fix:** Consolidate to `TestStorages.*`.

**HIGH: TQ-003 — Tests verify mock state, not behavior** 🔜 P08
- `DailyServiceTest.java:210,214-220`

**HIGH: TQ-004 — Cleanup test doesn't verify cleanup** 🔜 P08
- `DailyServiceTest.java:214-220` only checks return value.

**HIGH: TQ-005 — Regression tests document bugs** 🔜 P08
- `EdgeCaseRegressionTest.java:90-94,124-143` comments note “known limitation”.

**MEDIUM: TQ-006–TQ-015** — missing edge cases (null/empty boundaries, exact age limits, zero distance, empty collections, concurrent modification, setup validation, incomplete mocks). 🔜 P08

**LOW: TQ-016–TQ-028** — vague assertions and parameter handling issues. 🔜 P08

---

### 9.3 Exception Handling Issues (14 total)

**HIGH: EH-001 — DatabaseManager.isVersionApplied() swallows SQLException** (`storage/DatabaseManager.java:363-369`). 📋 P01

**HIGH: EH-002 — AppSession.notifyListeners() swallows exceptions** (logs message only). 📋 P02

**HIGH: EH-003 — NavigationService.navigateWithTransition() hides FXML load errors** (no user notification). 🔜 P07

**HIGH: EH-004 — MatchingService.recordLike() fallback can throw** (secondary query not caught). 📋 P02

**MEDIUM: EH-005–EH-010** — ConfigLoader silent catches (IOException/NumberFormatException) logged only. 📋 P02

**MEDIUM: EH-011 — HTTP routes throw business exceptions** (`MatchRoutes.java`, `MessagingRoutes.java`) → should return 400 not 500. 🔜 P06

**MEDIUM: EH-012 — Inconsistent Result vs Exception patterns** (Results + Exceptions + Optional used together). 🔜 P06

**LOW: EH-013–EH-014** — log level inconsistencies for similar errors. 🔜 P04

---

### 9.4 SQL / Storage Issues (14 total)

**HIGH: SQL-001 — N+1 in getActiveMatchesFor()** (`storage/jdbi/JdbiMatchStorage.java:70-77`) → use UNION. 📋 P01

**HIGH: SQL-002 — No transaction in MatchingService.recordLike()** (`core/MatchingService.java:120-166`). 📋 P02

**HIGH: SQL-003 — Orphan records possible** (`DatabaseManager` schema). Conversations/profile views not deleted/updated on match end. 📋 P01

**MEDIUM: SQL-004 — `SELECT *` in 8 locations** (`JdbiBlockStorage`, `JdbiLikeStorage`, `JdbiMatchStorage` (4), `JdbiReportStorage`). 📋 P01

**MEDIUM: SQL-005 — Missing indexes**: `conversations(user_a,user_b)`, `friend_requests(to_user_id,status)`, `messages(sender_id)`. 📋 P01

**MEDIUM: SQL-006 — CSV serialization limits query capability** for `interested_in`, `interests`. 📝 Backlog

**MEDIUM: SQL-007 — getAllLatestUserStats() subquery inefficiency** (`JdbiStatsStorage.java:84-97`) → prefer window function or NOT EXISTS. 📋 P01

**MEDIUM: SQL-008–SQL-011 — Mapping issues**
- inconsistent null handling (`readInstant` vs `readInstantOptional`) 📋 P01 (Task 7c)
- precision loss (Timestamp → Instant) 📋 P01 (Task 9a)
- CSV parsing silently skips invalid enum values 📋 P01 (Task 9b)
- timezone-naive Instant handling 📋 P01 (Task 9c)

**LOW: SQL-012–SQL-014** — minor inefficiencies (unused column selection, soft delete inconsistency). 📝 Backlog

---

### 9.5 Interface Design Issues (16 total)

**HIGH: IF-001 — StatsStorage fat interface** (5 unrelated concerns). Split into 5 focused interfaces. 🔜 P05

**HIGH: IF-002 — MessagingStorage mixed abstraction levels** (conversation lifecycle + message querying + state updates). 🔜 P05

**HIGH: IF-003 — CLI handlers lack common interface** (identical patterns but no shared `Handler`). 🔜 P06

**HIGH: IF-004 — Service result types inconsistent** (`SendResult`, `UndoResult`, `CleanupResult`, `TransitionValidationException`). 🔜 P06

**HIGH: IF-005 — Optional vs null inconsistency** (`UserStorage.get()` returns null, `MatchStorage.get()` returns Optional). 📝 Backlog

**MEDIUM: IF-006–IF-012** — SocialStorage mixed concerns, UserStorage profile notes unrelated, AchievementService 7 dependencies, RelationshipTransitionService notification coupling, SessionAggregates exposed, default methods hide no-op behavior, ambiguous method names. 📝 Backlog

**LOW: IF-013–IF-016** — contract clarity + documentation issues. 📝 Backlog

---

### 9.6 Null Safety / API Contract Issues (26 total)

**HIGH: NS-001 — Missing `@Nullable` on 18 methods** 🔜 P08
- `MapperHelper.java:30,49,58,67,87,96` (6 methods)
- `EnumMenu.java:48,58` (prompt returns null)
- `SafetyHandler.java:144,149,205,239,244` (5 methods)
- `ProfileHandler.java:390,395` (`parseInterestIndex()`)
- `LoginViewModel.java:147,252,257` (3 methods)
- `ChatViewModel.java:280` (`getCurrentUserId()`)

**MEDIUM: NS-002 — MapperHelper.readCsvAsList() returns mutable `ArrayList`**. 📋 P01

**MEDIUM: NS-003 — EnumMenu.prompt() vs promptMultiple() inconsistency** (null vs empty EnumSet). 🔜 P06

**MEDIUM: NS-004 — MapperHelper.readEnum() missing `enumType` validation**. 📋 P01

**LOW: NS-005–NS-026** — defensive copying gaps and other null-handling inconsistencies. 🔜 P08

---

### 9.7 Magic Numbers / Constants (60+ issues)

**Animation & UI timing (25 literals)**
- `Toast.java`: `Duration.seconds(3,4,5)`, `Duration.millis(200,300)`, `Insets(30)`, `translateY(50)`, `HBox(12)`, `iconSize(20)`, `maxWidth(400)`
- `UiAnimations.java`: `Duration.millis(50,100,150,200,400)`, scale factors (1.05, 1.1, 1.15), glow radius (15,25), spread (0.2–0.4), shake distance (-10,10), cycle count (6)
- `ConfettiAnimation.java`: `PARTICLE_COUNT(100)`, gravity(0.03), damping(0.99), rotation(360)

**Scoring thresholds (15 literals)**
- `MatchQualityService.java`: star rating boundaries (90, 75, 60, 40), display limits (3), distance thresholds (5, 15 km), age range (2 years), neutral score (0.5)

**Profile completion points (10 literals)**
- `ProfileCompletionService.java`: Name(5), Bio(10), BirthDate(5), Gender(5), InterestedIn(5), Photo(10), Interests(20)

**Cache configuration (5 literals)**
- `ImageCache.java`: `MAX_CACHE_SIZE(100)`, LinkedHashMap capacity (16), load factor (0.75f)

**Performance thresholds (2 literals)**
- `PerformanceMonitor.java`: `MAX_METRICS_SIZE(1000)`, slow threshold (100ms)

**Delimiter strings (5 literals)**
- `","` CSV delimiter (3 files), `"@"` cache key separator, `"x"` size separator

### 9.8 v3 High‑Risk Warnings Index (from audit_report_v3)

The v3 report explicitly flagged the following as high‑risk warnings; see the corresponding detailed items above:

- Race condition: `DatabaseManager` lazy init (TS‑001)
- Race condition: `MatchingViewModel` candidate queue (TS‑002)
- Memory visibility: `AppBootstrap` fields (TS‑003)
- No transaction: like/match creation (`MatchingService.recordLike()`, SQL‑002)
- Silent exceptions: schema version check (EH‑001)
- N+1 queries: match loading (SQL‑001)
- Fat interface: `StatsStorage` (IF‑001)
- Date‑dependent tests (TQ‑001)

---

## 10. Merge / Extraction Candidates (Combined)

### 10.1 Merge Candidates (Report.md)

| Files                                                | Destination           | Reason                            | File Savings |
|------------------------------------------------------|-----------------------|-----------------------------------|--------------|
| `CliConstants` + `CliUtilities`                      | `CliSupport.java`     | Same purpose, tiny size           | -1           |
| `PurgeService` + `CleanupService`                    | `CleanupService.java` | Overlapping, PurgeService dead    | -1           |
| `SoftDeletable` → User + Match                       | Inline                | 2 implementors, no polymorphism   | -1           |
| `EnumSetUtil` → ProfileHandler                       | Inline                | 1 consumer                        | -1           |
| `ErrorMessages` → ValidationService                  | Merge                 | 3 consumers, validation constants | -1           |
| `Gender` + `UserState` + `VerificationMethod` → User | Nested enums          | Tiny user-related enums           | -3           |

**Total file reduction (report.md): -8 files** (126 → ~118 main files)

### 10.2 Merge Candidates (ReportByGPT)

| Files                                                | Destination                    | Reason                        | Impact |
|------------------------------------------------------|--------------------------------|-------------------------------|--------|
| `MatchingHandler` + `MatchingViewModel`              | `core/app/MatchingAppService`  | Shared orchestration          | High   |
| `ProfileHandler` + `ProfileViewModel`                | `core/app/ProfileAppService`   | Shared validation/persistence | High   |
| `MessagingHandler` + `ChatViewModel`                 | `core/app/MessagingAppService` | Shared messaging flow         | High   |
| `ProfileCompletionService` + `ProfilePreviewService` | `ProfileQualityService`        | Overlapping metrics           | Medium |
| `UiHelpers` + `UiServices`                           | `UiToolkit`                    | Utility overlap               | Low    |
| `Match` + `Messaging` ID logic                       | `PairId` helper                | Duplicate ID generation       | Low    |

### 10.3 Extraction Candidates (Report.md)

| Source File                | Component                 | Target                   | LOC Moved |
|----------------------------|---------------------------|--------------------------|-----------|
| `ProfileController.java`   | Dealbreaker chip creation | `DealbreakersChipHelper` | ~166      |
| `MatchesController.java`   | Likes tab rendering       | `LikesTabRenderer`       | ~200      |
| `MatchQualityService.java` | Lifestyle scoring         | `LifestyleScorer`        | ~131      |
| `ServiceRegistry.java`     | `Builder.buildH2()`       | `StorageFactory`         | ~120      |
| `DatabaseManager.java`     | Schema DDL                | `SchemaMigrator`         | ~200      |
| `LoginController.java`     | User list cell            | `UserListCellFactory`    | ~80       |

### 10.4 Extraction Candidates (ReportByGPT)

| File                       | Suggested Extraction                                                                | Reason                    |
|----------------------------|-------------------------------------------------------------------------------------|---------------------------|
| `ProfileController.java`   | `ProfilePhotoController`, `ProfileBasicsController`, `ProfilePreferencesController` | Large controller          |
| `ProfileHandler.java`      | `ProfileAppService`, CLI prompt helper                                              | Very high complexity      |
| `DatabaseManager.java`     | `SchemaInitializer`, `MigrationRunner`                                              | Monolithic infrastructure |
| `MatchQualityService.java` | `MatchQualityScorer`, `MatchQualityBreakdown`                                       | High complexity algorithm |
| `User.java`                | `UserProfile`, `UserPreferences`, `UserPhotos`                                      | God-class risk            |

### 10.5 Additional Method Extraction Candidates (>100 LOC)

These candidates were explicitly listed in `report.md` in addition to the extraction summary:

- `ProfileController.wireAuxiliaryActions()` (~116 LOC) → split into named wiring groups.
- `User.java` validation logic (~101 LOC) → move into `ValidationService`.
- `MatchQualityService` secondary scoring (~93 LOC) → extract `DistanceScorer` helper.

---

## 11. Suggested Target Architecture & Dependency Graph

### 11.1 Target Architecture (Report.md)

```
src/main/java/datingapp/
├── Main.java
│
├── core/                              # Pure domain (ZERO framework imports)
│   ├── AppConfig.java
│   ├── AppClock.java
│   ├── LoggingSupport.java            # NEW: shared logging mixin
│   ├── Match.java
│   ├── Social.java
│   ├── ServiceRegistry.java           # Slimmed (no Builder)
│   ├── CleanupService.java
│   ├── PerformanceMonitor.java
│   │
│   ├── matching/                      # 7 files
│   ├── messaging/                     # 3 files
│   ├── profile/                       # 7 files
│   ├── safety/                        # 2 files
│   ├── stats/                         # 4 files
│   ├── daily/                         # 4 files
│   │
│   └── storage/                       # 11 interfaces (unchanged)
│
├── app/
│   ├── AppBootstrap.java              # MOVED from core/
│   ├── AppSession.java                # MOVED from core/
│   ├── ConfigLoader.java              # MOVED from core/
│   ├── cli/                           # 11 files (merged CliConstants+Utilities)
│   └── api/                           # 4 files (unchanged)
│
├── storage/
│   ├── StorageFactory.java            # NEW: extracted from ServiceRegistry
│   ├── SchemaMigrator.java            # NEW: extracted from DatabaseManager
│   ├── DatabaseManager.java           # Slimmed
│   ├── StorageException.java
│   ├── TransactionTemplate.java
│   ├── jdbi/                          # 19 files (unchanged)
│   └── mapper/                        # 1 file (unchanged)
│
└── ui/
    ├── DatingApp.java
    ├── NavigationService.java
    ├── ViewModelFactory.java
    ├── controller/                    # 11 files + extracted helpers
    ├── viewmodel/                     # 9 files (NO storage imports)
    ├── component/                     # 1 file
    └── util/                          # 6 files
```

**After refactors (report.md projection):**
- Main files: ~120 (—6 merges, +6 extractions, -3 moves)
- Violations: 0 (from 9)
- God classes: 0 (from 4)
- Duplicate LOC eliminated: ~3,000
- New architectural tests: 3 ArchUnit rules

### 11.2 Dependency Graph (Report.md)

```
datingapp.core (PURIFIED)
  → java.* only
  → NO datingapp.storage.*
  → NO com.fasterxml.*

datingapp.core.storage
  → java.* only (pure interfaces)

datingapp.app
  → datingapp.core.*
  → datingapp.core.storage.*
  → datingapp.storage.* (composition root only)

datingapp.storage
  → datingapp.core.*
  → datingapp.core.storage.*
  → org.jdbi.*
  → java.sql.*

datingapp.ui.viewmodel (FIXED)
  → datingapp.core.* (services ONLY)
  → javafx.beans.*
  → java.*
  → NO datingapp.core.storage.*

datingapp.ui.controller
  → datingapp.core.* (domain models + enums)
  → datingapp.ui.viewmodel.*
  → javafx.*
```

### 11.3 Suggested Module Structure (ReportByGPT)

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

### 11.4 v3 Incremental Module Additions

```
core/
  storage/
    UserStatsStorage.java         # NEW: split from StatsStorage
    PlatformStatsStorage.java     # NEW: split from StatsStorage
    ProfileViewStorage.java       # NEW: split from StatsStorage
    UserAchievementStorage.java   # NEW: split from StatsStorage
    StatsCleanupStorage.java      # NEW: split from StatsStorage
  constants/
    AnimationConstants.java       # NEW: UI timing values
    ScoringConstants.java         # NEW: if not in AppConfig

app/
  Handler.java                    # NEW: common interface
```

---

## 12. Refactor Plans & Roadmaps (All Sources)

### 12.1 Report.md — Refactor Plan (R-001 → R-018)

**R-001: Fix ViewModel→Storage Layer Violations** 🔜 P05
- **Problem:** 6 of 8 ViewModels import `core.storage.*`.
- **Files:** `MatchesViewModel`, `LoginViewModel`, `StatsViewModel`, `DashboardViewModel`, `PreferencesViewModel`, `ProfileViewModel`.
- **Steps:** Audit storage calls, create missing service methods, inject services only, remove storage imports, add ArchUnit test.
- **Risk:** Medium; **Rollback:** revert each VM independently.
- **Tests:** VM tests + new service tests + ArchUnit guard.

**R-002: Fix FX-Thread Database Queries** 🔜 P05
- **Problem:** `MatchesViewModel`, `LoginViewModel`, `StatsViewModel` query on FX thread.
- **Steps:** `Thread.ofVirtual()` + `Platform.runLater`, add `loading` property, bind overlays, follow `DashboardViewModel` pattern.
- **Risk:** Medium; **Rollback:** revert to synchronous calls.
- **Tests:** runtime verification (no UI freeze).

**R-003: Fix core/ Layer Violations** 📋 P03
- **Files:** `AppBootstrap`, `ServiceRegistry`, `ConfigLoader`.
- **Steps:** Move `AppBootstrap` + `ConfigLoader` to `app/`, extract `ServiceRegistry.Builder.buildH2()` to `storage/StorageFactory`, add ArchUnit test.
- **Risk:** Medium; **Rollback:** move files back.
- **Tests:** all existing tests + ArchUnit guard.

**R-004: Split core/ into Domain Sub-Packages** 📋 P03
- **Target:** `matching/`, `messaging/`, `profile/`, `safety/`, `stats/`, `daily/`.
- **Steps:** move files, update 182+ imports, run `spotless` + `verify`.
- **Risk:** High (mass import changes). **Rollback:** git revert.

**R-005: Extract LoggingSupport Interface** 🔜 P04
- **Problem:** 23 files duplicate logging helpers (~2,645 LOC).
- **Steps:** Create `LoggingSupport`, implement in 23 classes, delete private helper methods.
- **Risk:** Low; **Rollback:** restore methods.

**R-006: Extract ServiceRegistry.Builder → StorageFactory** 📋 P03
- **Problem:** 100+ LOC JDBI setup in `core/` god class.
- **Steps:** Create `storage/StorageFactory`, move setup, remove builder, update `AppBootstrap`.
- **Risk:** Medium.

**R-007: Centralize AppConfig via Constructor Injection** 📋 P03
- **Files:** `User`, `MessagingService`, `ProfileCompletionService`, `PurgeService`, `LoginController`, `ProfileViewModel`, `LoginViewModel`, `PreferencesViewModel`.
- **Steps:** Move validation to `ValidationService`, inject `AppConfig` to services and viewmodels.

**R-008: Extract Large Methods (>100 LOC)** 🔜 P06/P07
- `MatchesController` → `LikesTabRenderer`
- `ProfileController.handleEditDealbreakers()` → `DealbreakersChipHelper`
- `MatchQualityService` lifestyle scoring → `LifestyleScorer`
- `ProfileController.wireAuxiliaryActions()` → split methods

**R-009: Genericize ProfileHandler Dealbreaker Methods** 🔜 P06
- Replace 6 near-identical methods with generic `editEnumDealbreaker()`.

**R-010: Merge CliConstants + CliUtilities → CliSupport** (low risk) 🔜 P06

**R-011: Delete/Merge PurgeService** (dead code) 📋 P02

**R-012: Inline SoftDeletable** 📋 P03

**R-013: Merge EnumSetUtil or Inline** 🔜 P06

**R-014: Merge ErrorMessages → ValidationService** 📋 P02

**R-015: Move MODULE_OVERVIEW.md to docs/** 📋 P02

**R-016: Clean Root-Level Workspace Artifacts** (move 20+ files to `docs/audits/`, update `.gitignore`) 📋 P02

**R-017: Shared handleBack() in BaseController** (remove 6 duplicates) 🔜 P07

**R-018: Move Standalone Enums into User.java** (`Gender`, `UserState`, `VerificationMethod`) 📋 P03

### 12.2 ReportByGPT — Refactor Plan (R-001 → R-008)

**R-001: Introduce Matching Application Service**
- **Files:** `MatchingHandler`, `MatchingViewModel`, `CandidateFinder`, `MatchingService`, `UndoService`.
- **Steps:** Add `core/app/MatchingAppService`, move orchestration, update CLI/VM, keep wrappers.
- **Risk:** High; **Tests:** `MatchingServiceTest`, `CandidateFinderTest`, `MatchingHandlerTest`, `MatchesViewModelTest` + CLI smoke.

**R-002: Create Profile Application Service + Split Profile Controllers**
- **Files:** `ProfileHandler`, `ProfileController`, `ProfileViewModel`, `ProfileCompletionService`.
- **Steps:** Add `ProfileAppService`, extract `ProfilePhotoManager`, split controller, route UI/CLI to app service.
- **Risk:** High; **Tests:** `ProfileCompletionServiceTest`, `UserTest`, `ProfileCreateSelectTest` + JavaFX smoke.

**R-003: Create Messaging Application Service**
- **Files:** `MessagingHandler`, `ChatViewModel`, `MessagingService`.
- **Steps:** Add `MessagingAppService`, update CLI/VM, slim controller.
- **Risk:** Medium; **Tests:** `MessagingServiceTest`, `MessagingHandlerTest`, `ChatViewModelTest` + UI smoke.

**R-004: Unify Match Quality Scoring in UI**
- **Files:** `MatchQualityService`, `MatchingViewModel`.
- **Steps:** Add UI summary method to service, replace UI scoring.
- **Risk:** Low.

**R-005: Modularize ServiceRegistry**
- **Steps:** Introduce `StorageModule`, `MatchingModule`, `MessagingModule`, `SafetyModule`, `StatsModule`; keep getters during transition.
- **Risk:** Medium; **Tests:** `ServiceRegistryTest` + startup smoke.

**R-006: Split DatabaseManager into Schema + Migration Components**
- **Steps:** Create `SchemaInitializer`, `MigrationRunner`, keep `DatabaseManager` coordinator.
- **Risk:** High; **Tests:** `H2StorageIntegrationTest` + startup smoke.

**R-007: Extract User Profile Sub-Objects**
- **Steps:** Introduce `UserProfile`, `UserPreferences`, `UserPhotos`; update storage binding/mapping.
- **Risk:** High; **Tests:** `UserTest`, `ProfileCompletionServiceTest`, JDBI integration tests.

**R-008: Decompose Large UI Controllers**
- **Files:** `ProfileController`, `MatchesController`, `LoginController`.
- **Steps:** Extract list cell factories and helpers; create sub-controllers; delegate to services.
- **Risk:** Medium; **Tests:** `JavaFxCssValidationTest` + manual UI flows.

### 12.3 v3 Refactor Plan (R‑V3‑001 → R‑V3‑008)

**R‑V3‑001: Fix Critical Thread Safety (Week 1)**
- Files: `DatabaseManager`, `MatchingViewModel`, `AppBootstrap`, `NavigationService`, `UndoService`, `DailyService`.
- Steps: add `volatile` to 6 fields, replace `LinkedList` with `ConcurrentLinkedQueue`, `computeIfAbsent()`.
- Effort: 4h | Risk: Medium.

**R‑V3‑002: Fix Transaction Boundaries**
- File: `MatchingService.java`.
- Steps: wrap `recordLike()` in `TransactionTemplate`, add concurrent likes test.
- Effort: 2h | Risk: Medium.

**R‑V3‑003: Fix Test Date‑Dependence**
- Files: 7 test files.
- Steps: create `TestClock`, replace `LocalDate.now()`, add setup validation.
- Effort: 3h | Risk: Low.

**R‑V3‑004: Fix Swallowed Exceptions**
- Files: `DatabaseManager`, `ConfigLoader`, `NavigationService`, `MatchingService`.
- Steps: specific SQLException handling, Toast for navigation failures, wrap fallback queries.
- Effort: 3h | Risk: Low.

**R‑V3‑005: Fix N+1 Queries**
- File: `JdbiMatchStorage.java`.
- Steps: replace dual query with UNION, add missing indexes.
- Effort: 2h | Risk: Low.

**R‑V3‑006: Split Fat Interfaces**
- Files: `StatsStorage`, `MessagingStorage`.
- Steps: create 5 focused stats interfaces, update consumers.
- Effort: 4h | Risk: Medium.

**R‑V3‑007: Create Constants Classes**
- Steps: `AnimationConstants`, move scoring thresholds into `AppConfig`, `CacheConstants`.
- Effort: 4h | Risk: Low.

**R‑V3‑008: Add @Nullable Annotations**
- Steps: add `javax.annotation` dependency, annotate 18 methods, run static analysis.
- Effort: 2h | Risk: Low.

---

## 13. Implementation Roadmaps (All Sources)

### 13.1 Report.md Roadmap (6 Weeks, ~27h)

| Phase  | Refactors                                           | Effort | Risk    | Impact                       |
|--------|-----------------------------------------------------|--------|---------|------------------------------|
| Week 1 | R‑010 to R‑017 (quick wins)                         | 2h     | Low     | -7 files, cleaner workspace  |
| Week 2 | R‑005 (LoggingSupport), R‑009 (Dealbreakers)        | 3h     | Low     | -2,645 LOC duplication       |
| Week 3 | R‑002 (FX-thread fix), R‑001 (ViewModel layer fix)  | 6h     | Medium  | UI freezes + proper layering |
| Week 4 | R‑003 (core violations), R‑006 (StorageFactory)     | 4h     | Medium  | Clean architecture           |
| Week 5 | R‑004 (sub-packages), R‑007 (AppConfig injection)   | 8h     | High    | Module organization          |
| Week 6 | R‑008 (extract large methods), R‑018 (enum nesting) | 4h     | Low‑Med | Reduced complexity           |

### 13.2 Summary.md Quick Wins (Week 1–2)

- Delete `PurgeService` (dead code)
- Merge `CliConstants` + `CliUtilities` → `CliSupport`
- Inline `SoftDeletable`
- Inline/generalize `EnumSetUtil`
- Merge `ErrorMessages` → `ValidationService`
- Move `MODULE_OVERVIEW.md` to `docs/`
- Add shared `handleBack()` to `BaseController`
- Create `LoggingSupport` mixin
- Genericize 6 dealbreaker edit methods
- Clean root-level workspace artifacts

### 13.3 Summary.md Architecture Fixes (Week 3–4)

- Fix FX-thread DB queries
- Route ViewModels through services only
- Move `AppBootstrap` + `ConfigLoader` out of core
- Extract `StorageFactory` from `ServiceRegistry`
- Add 3 ArchUnit layer-violation tests

### 13.4 Summary.md Structural Improvements (Week 5–6)

- Split `core/` into sub‑packages
- Inject `AppConfig` via constructor (8 files)
- Extract large methods (>100 LOC)
- Nest standalone enums into `User.java`

### 13.5 v3 Roadmap (4 Weeks, 24h)

| Phase  | Focus                                 | Effort | Impact                    |
|--------|---------------------------------------|--------|---------------------------|
| Week 1 | Thread safety fixes (6 critical)      | 6h     | Prevent race conditions   |
| Week 2 | Test reliability + exception handling | 6h     | Stable CI, visible errors |
| Week 3 | SQL optimization + interface splits   | 6h     | Performance, cleaner deps |
| Week 4 | Constants + null safety               | 6h     | Maintainability           |

### 13.6 Additional v3 Backlog Item

- Standardize `Optional` vs `null` usage across storage interfaces (backlog from v3 summary).

---

## 14. Quick Wins (Combined)

- Use `MatchQualityService` in UI (replace duplicate compatibility logic).
- Extract deterministic ID generation into shared helper.
- Centralize CLI prompt/input validation into `CliPrompt` helper.
- Add typed `NavigationContext` instead of raw `Object`.
- Split `UiComponents` into smaller factories.
- Merge/rename overlapping UI utilities (`UiHelpers`, `UiServices`, `UiAnimations`).
- Extract repeated SQL fragments in `Jdbi*Storage` into shared constants/helpers.
- Make 6 fields `volatile` for thread safety.
- Use `computeIfAbsent()` in `DailyService` cache.
- Add Toast for FXML load errors in `NavigationService`.
- Replace dual match query with UNION.
- Add setup validation assertions in tests.

---

## 15. Projected Metrics After Refactoring (Summary.md)

| Metric                             | Before | After | Change       |
|------------------------------------|--------|-------|--------------|
| Main files                         | 126    | ~120  | -6           |
| Layer violations                   | 9      | 0     | -9           |
| God classes (>400 LOC, 15+ fields) | 4      | 0     | -4           |
| Duplicated LOC                     | ~3,000 | ~200  | -93%         |
| Flat core/ files                   | 44     | ~12   | -73%         |
| FX-thread DB queries               | 3 VMs  | 0     | -3           |
| Dead code files                    | 3      | 0     | -3           |
| ArchUnit guard tests               | 0      | 3     | +3           |
| Total estimated effort             | —      | ~27h  | Over 6 weeks |

---

## 16. Root Causes & Prevention (Combined)

### 16.1 Root Causes (Summary.md + v3)

1. Organic growth without package planning (flat `core/`).
2. PMD GuardLogStatement workaround copied instead of shared.
3. Expedient shortcuts in ViewModels (direct storage access).
4. Manual DI at scale (ServiceRegistry god-class).
5. No automated layer tests (violations accumulated).
6. No concurrency testing (thread safety bugs survived).
7. Date‑naive test fixtures (`LocalDate.now()` in tests).
8. Catch‑and‑log exception patterns copied without semantics.
9. Interface design by accretion (ISP violations).
10. Scattered constants (magic literals).
11. Inconsistent Result pattern usage.

### 16.2 Prevention Recommendations (Summary.md + v3)

1. Add ArchUnit tests to enforce layering and thread‑safety rules.
2. Require Clock injection (ban `LocalDate.now()`/`Instant.now()` in tests).
3. Standardize Result pattern in services (lint for exceptions).
4. Create `LoggingSupport` interface for consistent logging helpers.
5. Use `package-info.java` to document module boundaries.
6. Constructor injection only; avoid static config patterns.
7. Interface size limits (flag interfaces >10 methods).
8. Constants review checklist in PRs.

---

## 17. Notes & Limitations (Combined)

- Cyclomatic complexity values are heuristic estimates, not full parser results.
- No circular dependencies detected at module level.
- Dynamic UI behavior and performance issues require runtime checks.

---

*End of Combined Architecture Audit Report.*