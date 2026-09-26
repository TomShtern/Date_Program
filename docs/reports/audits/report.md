# Architecture & Code-Smell Audit Report

**Date:** 2026-02-07
**Auditor:** GitHub Copilot (Claude Opus 4.6)
**Scope:** Full repository — `src/main/java` (126 files) + `src/test/java` (56 files)

---

## 1. Project Metrics

| Metric | Value |
| --- | --- |
| Total Source Files (Java) | 182 (126 main + 56 test) |
| Primary Language | Java 25 |
| UI Framework | JavaFX 25 (AtlantaFX 2.1.0 theme) |
| Total Lines (all) | 46,489 |
| Total Code Lines | 34,289 |
| Main Source LOC | 27,634 (126 files, avg 219/file) |
| Test Source LOC | 18,855 (56 files, avg 337/file) |
| Test Methods | ~808+ across 56 test files |
| CSS Files | 2 (2,688 lines total) |
| FXML Files | ~11 |
| Unique Import Statements | 287 |
| Largest File (main) | `ProfileController.java` (925 LOC) |
| Most Imports (main) | `LoginController.java` (47 imports) |

### File Size Distribution (Main Source)

| Category | Threshold | Count | % |
| --- | --- | --- | --- |
| x.large | >1500 LOC | 0 | 0% |
| large | 600–1500 LOC | 11 | 9% |
| medium | 300–600 LOC | 26 | 21% |
| small | 100–300 LOC | 47 | 37% |
| x.small | <100 LOC | 42 | 33% |

### Top 13 Largest Files

| File | LOC | Imports | Category |
| --- | --- | --- | --- |
| `ui/controller/ProfileController.java` | 925 | 38 | God controller |
| `core/User.java` | 828 | 12 | God model |
| `app/cli/ProfileHandler.java` | 807 | 29 | God handler |
| `ui/controller/MatchesController.java` | 787 | 44 | Large controller |
| `storage/DatabaseManager.java` | 775 | ~15 | Mixed responsibilities |
| `core/MatchQualityService.java` | 752 | 16 | Large service |
| `app/cli/MatchingHandler.java` | 739 | 32 | Large handler |
| `ui/viewmodel/ProfileViewModel.java` | 699 | 33 | Layer violation |
| `core/AppConfig.java` | 673 | 3 | Justified (40+ param record) |
| `ui/controller/LoginController.java` | 645 | 47 | Over-coupled |
| `core/ServiceRegistry.java` | 472 | 13 | God class |
| `ui/viewmodel/MatchesViewModel.java` | 457 | ~20 | Layer violation |
| `core/Dealbreakers.java` | 442 | 6 | Moderate (domain model) |

---

## 2. Sources of Mess and Confusion

| # | Source | Severity | Files Affected |
| --- | --- | --- | --- |
| 1 | **core/ flat package** — 44 files in one package (models, services, utils, enums, config) | High | 44 files |
| 2 | **Duplicated logging helpers** — 23 files containing identical private `logInfo()`, `logWarn()`, `logError()` methods | Medium | 23 files |
| 3 | **ViewModel→Storage layer violations** — 6 of 8 ViewModels directly import `core.storage.*` | High | 6 files |
| 4 | **ServiceRegistry god-class** — 472 LOC, 26+ fields, 25-param constructor, imports storage | High | 1 file (+all consumers) |
| 5 | **core/ imports storage/** — layer violation in 2 core files | High | 2 files |
| 6 | **core/ imports Jackson** — framework dependency violation | Medium | 1 file |
| 7 | **FX-thread DB queries** in 3 ViewModels (UI freeze risk) | High | 3 files |
| 8 | **AppConfig.defaults() scattered** — 8 files create independent static config | Medium | 8 files |
| 9 | **Copy-paste dealbreaker methods** — 6 identical methods in ProfileHandler | Medium | 1 file |
| 10 | **Large methods** — 5+ methods exceeding 120 LOC | Medium | 4 files |
| 11 | **Orphaned/dead code** — PurgeService (0 imports), SoftDeletable (2 users), EnumSetUtil (1 user) | Low | 3 files |
| 12 | **37 root-level doc/artifact files** cluttering workspace | Low | N/A |
| 13 | **Singleton overuse** — 5 singletons making testing difficult | Medium | 5 files |
| 14 | **MODULE_OVERVIEW.md in source tree** — non-source files in Java packages | Low | 2 files |

---

## 3. Layering Violations Detail

### core/ → storage/ (MUST NOT happen)

| Core File | Imports From | Violation |
| --- | --- | --- |
| `core/AppBootstrap.java` | `storage.DatabaseManager` | Core depends on storage implementation |
| `core/ServiceRegistry.java` | `storage.DatabaseManager`, `storage.TransactionTemplate` | Core wires storage implementations |
| `core/ConfigLoader.java` | `com.fasterxml.jackson.databind.*` | Core depends on framework library |

### ui/viewmodel/ → core/storage/ (SHOULD NOT happen — should go through services)

| ViewModel | Storage Interfaces Imported |
| --- | --- |
| `MatchesViewModel.java` | `BlockStorage`, `LikeStorage`, `MatchStorage`, `UserStorage` (4!) |
| `LoginViewModel.java` | `UserStorage` |
| `StatsViewModel.java` | `LikeStorage`, `MatchStorage` |
| `DashboardViewModel.java` | `MatchStorage` |
| `PreferencesViewModel.java` | `UserStorage` |
| `ProfileViewModel.java` | `UserStorage` |

**Total violations: 9** (3 core→storage + 6 viewmodel→storage)

---

## 4. Architectural Clusters (Module Candidates)

### Cluster A: `matching` (7 files, ~2,917 LOC)

| File | LOC | Role |
| --- | --- | --- |
| `CandidateFinder.java` | 362 | Candidate discovery pipeline |
| `MatchingService.java` | 269 | Like/match orchestration |
| `MatchQualityService.java` | 752 | Quality scoring |
| `SessionService.java` | 169 | Swipe session management |
| `UndoService.java` | 224 | Undo support |
| `UndoState.java` | 84 | Undo state tracking |
| `SwipeSession.java` | 221 | Session model |

**Cohesion**: All share `LikeStorage`, `MatchStorage`, `BlockStorage` dependencies. Cross-reference each other's types.

### Cluster B: `messaging` (3 files, ~698 LOC)

| File | LOC | Role |
| --- | --- | --- |
| `Messaging.java` | 277 | Domain model (Message, Conversation) |
| `MessagingService.java` | 256 | Send/receive orchestration |
| `RelationshipTransitionService.java` | 165 | Match state transitions |

### Cluster C: `profile` (7 files, ~3,282 LOC)

| File | LOC | Role |
| --- | --- | --- |
| `User.java` | 828 | User domain model |
| `Dealbreakers.java` | 442 | Dealbreaker records |
| `Preferences.java` | 206 | Interest/Lifestyle records |
| `ProfileCompletionService.java` | 344 | Completion % calculation |
| `PacePreferences.java` | 85 | Communication pace |
| `ProfilePreviewService.java` | 152 | Profile preview generation |
| `ValidationService.java` | 162 | Input validation |

### Cluster D: `safety` (2 files, ~341 LOC)

| File | LOC | Role |
| --- | --- | --- |
| `TrustSafetyService.java` | 241 | Reporting, banning, verification |
| `UserInteractions.java` | 100 | Like/Block/Report records |

### Cluster E: `stats` (4 files, ~696 LOC)

| File | LOC | Role |
| --- | --- | --- |
| `StatsService.java` | 156 | Statistics aggregation |
| `AchievementService.java` | 251 | Achievement tracking |
| `Stats.java` | 197 | Stats domain model |
| `Achievement.java` | 92 | Achievement enum |

### Cluster F: `daily` (4 files, ~610 LOC)

| File | LOC | Role |
| --- | --- | --- |
| `DailyService.java` | 262 | Daily limits & picks |
| `DailyPick.java` | 14 | Pick record |
| `StandoutsService.java` | 268 | Standout profiles |
| `Standout.java` | 66 | Standout model |

### Cluster G: `config / bootstrap` (5 files, ~1,077 LOC)

| File | LOC | Role |
| --- | --- | --- |
| `AppConfig.java` | 673 | Configuration record |
| `ConfigLoader.java` | 202 | JSON config loading (has Jackson import!) |
| `AppBootstrap.java` | 70 | Singleton init (has storage import!) |
| `AppSession.java` | 69 | User session singleton |
| `AppClock.java` | 63 | Testable clock |

### Remaining in `core/` (12 files, ~1,449 LOC)

| File | LOC | Notes |
| --- | --- | --- |
| `Match.java` | 257 | Domain model |
| `Social.java` | 83 | Domain model |
| `ServiceRegistry.java` | 472 | DI container — needs separate refactor |
| `CleanupService.java` | 69 | Merge with PurgeService |
| `PurgeService.java` | 60 | Dead code (0 imports) |
| `PerformanceMonitor.java` | 167 | Instrumentation |
| `EnumSetUtil.java` | 70 | Only 1 consumer |
| `ErrorMessages.java` | 49 | Only 3 consumers |
| `SoftDeletable.java` | 22 | Only 2 implementors |
| `Gender.java` | 7 | Standalone enum |
| `UserState.java` | 11 | Standalone enum |
| `VerificationMethod.java` | 11 | Standalone enum |

---

## 5. Method Extraction Candidates

Methods exceeding 100 LOC that should be extracted:

| File | Method (approx.) | LOC | Suggested Extraction |
| --- | --- | --- | --- |
| `MatchesController.java` | Tab rendering / likes section (~L450+) | ~200+ | `LikesTabRenderer` helper |
| `ProfileController.java` | `handleEditDealbreakers()` (L732) | ~166 | `DealbreakersChipHelper` |
| `MatchQualityService.java` | Lifestyle scoring (~L253) | ~131 | `LifestyleScorer` helper |
| `ProfileController.java` | `wireAuxiliaryActions()` (L56) | ~116 | Split into named wiring groups |
| `User.java` | Validation logic (~L720) | ~101 | `ValidationService` |
| `MatchQualityService.java` | Secondary scoring (~L573) | ~93 | `DistanceScorer` helper |

---

## 6. Duplication Catalog

### 6.1 Logging Helpers (23 files)

**Pattern**: Each file defines private `logInfo()`, `logWarn()`, `logError()`, `logDebug()`, `logTrace()` methods wrapping SLF4J calls with level guards (required by PMD GuardLogStatement rule).

**Affected Files**:
- CLI: `MatchingHandler`, `ProfileHandler`, `MessagingHandler`, `SafetyHandler`, `StatsHandler`, `ProfileNotesHandler`, `LikerBrowserHandler`, `RelationshipHandler`
- ViewModels: `LoginViewModel`, `DashboardViewModel`, `MatchesViewModel`, `PreferencesViewModel`, `StatsViewModel`, `ProfileViewModel`, `MatchingViewModel`, `ChatViewModel`
- Controllers: `ProfileController`, `LoginController`, `MatchesController`, `MatchingController`
- Other: `CandidateFinder`, `NavigationService`, `ViewModelFactory`

**Estimated Duplication**: ~115 LOC per file × concept = ~2,645 lines of identical boilerplate

**Fix**: Create shared `LoggingSupport` interface with default methods

### 6.2 Dealbreaker Edit Methods (ProfileHandler)

**Pattern**: 6 methods at L635–L697 follow identical structure — prompt enum, set dealbreaker field, save.

**Fix**: Create generic `editEnumDealbreaker(User, Dealbreakers, String, Class<E>, BiConsumer<Dealbreakers, E>)`

### 6.3 handleBack() Navigation (6 controllers)

**Pattern**: `handleBack()` calls `navigationService.goBack()` in 6 controllers.

**Fix**: Move to `BaseController` as shared method

### 6.4 AppConfig.defaults() Static Pattern (8 files)

**Pattern**: `private static final AppConfig CONFIG = AppConfig.defaults()` — identical line in 8 files.

**Fix**: Inject `AppConfig` via constructor

---

## 7. Dead / Orphaned Code

| File | LOC | Evidence | Action |
| --- | --- | --- | --- |
| `core/PurgeService.java` | 60 | Zero inbound imports (only self-references) | **Delete** or merge into CleanupService |
| `core/SoftDeletable.java` | 22 | Only 2 implementors (User, Match), no polymorphic usage | **Inline** into User and Match |
| `core/EnumSetUtil.java` | 70 | Only 1 consumer (ProfileHandler) | **Inline** or generalize |
| `core/ErrorMessages.java` | 49 | Only 3 consumers | **Merge** into ValidationService |
| `core/MODULE_OVERVIEW.md` | N/A | Non-source file in source package | **Move** to docs/ |
| `storage/MODULE_OVERVIEW.md` | N/A | Non-source file in source package | **Move** to docs/ |

---

## 8. Refactor Plan

### Priority: Critical (Fix ASAP)

#### R-001: Fix ViewModel→Storage Layer Violations

**Problem**: 6 of 8 ViewModels directly import `core.storage.*`, bypassing the service layer. This violates MVVM, makes testing harder, and creates tight coupling.

**Files**:
- `MatchesViewModel.java` (4 storage imports)
- `LoginViewModel.java` (1)
- `StatsViewModel.java` (2)
- `DashboardViewModel.java` (1)
- `PreferencesViewModel.java` (1)
- `ProfileViewModel.java` (1)

**Steps**:
1. Audit each ViewModel's storage calls — identify which service should own each
2. Create missing service methods for simple lookups
3. Update `ViewModelFactory` to inject services only
4. Remove all `core.storage.*` imports from ViewModels
5. Add ArchUnit test to prevent regression

**Risk**: Medium — threading model must be preserved
**Rollback**: Revert each ViewModel independently
**Tests**: Existing VM tests + new service method tests + ArchUnit guard

---

#### R-002: Fix FX-Thread Database Queries (3 ViewModels)

**Problem**: `MatchesViewModel`, `LoginViewModel`, and `StatsViewModel` run DB queries on the FX Application Thread, causing UI freezes. `DashboardViewModel` and `ChatViewModel` already correctly use `Thread.ofVirtual() + Platform.runLater()`.

**Files**: `MatchesViewModel.java`, `LoginViewModel.java`, `StatsViewModel.java`

**Steps**:
1. Wrap all storage/service calls in `Thread.ofVirtual().start(() -> { ... Platform.runLater(() -> ...) })`
2. Add `loading` BooleanProperty; toggle before/after
3. Bind loading overlays in matching controllers
4. Follow the pattern from `DashboardViewModel`

**Risk**: Medium (threading bugs)
**Rollback**: Revert to synchronous calls
**Tests**: Runtime verification — no `IllegalStateException`, no UI freezes

---

#### R-003: Fix core/ Layer Violations

**Problem**: 3 files in `core/` import from `storage/` or framework libraries.

**Files**: `AppBootstrap.java`, `ServiceRegistry.java`, `ConfigLoader.java`

**Steps**:
1. Move `AppBootstrap.java` → `app/AppBootstrap.java`
2. Move `ConfigLoader.java` → `app/ConfigLoader.java`
3. Extract `ServiceRegistry.Builder.buildH2()` → `storage/StorageFactory.java`
4. Add ArchUnit test: `noClasses().that().resideIn("..core..").should().dependOn("..storage..")`

**Risk**: Medium (import path changes)
**Rollback**: Move files back
**Tests**: All existing tests pass + ArchUnit guard

---

### Priority: High (Next Sprint)

#### R-004: Split core/ Into Domain Sub-Packages

**Problem**: 44 files in flat `core/` package — models, services, utilities, enums, config all mixed.

**Target Structure**:
- `core/matching/` (7 files)
- `core/messaging/` (3 files)
- `core/profile/` (7 files)
- `core/safety/` (2 files)
- `core/stats/` (4 files)
- `core/daily/` (4 files)
- `core/` (remaining ~17 files)

**Steps**:
1. Create sub-packages
2. Move files
3. Update all 182+ import statements
4. Run `mvn spotless:apply` then `mvn verify`

**Risk**: High (massive import changes, but zero logic changes)
**Rollback**: Single git revert
**Tests**: All 808 existing tests must pass unchanged

---

#### R-005: Extract LoggingSupport Interface

**Problem**: 23 files contain identical private `logInfo()`, `logWarn()`, `logError()` methods (~2,645 LOC duplicated).

**Steps**:
1. Create `core/LoggingSupport.java` interface with default methods
2. Each of 23 classes implements `LoggingSupport`
3. Add `@Override public Logger getLogger() { return logger; }`
4. Delete all private log helper methods

**Risk**: Low — behavior identical
**Rollback**: Revert interface, re-add private methods
**Tests**: All existing tests pass

---

#### R-006: Extract ServiceRegistry.Builder → StorageFactory

**Problem**: `ServiceRegistry.Builder.buildH2()` is 100+ LOC of JDBI setup in `core/` — a clear layer violation. 472 LOC god-class with 26+ fields.

**Steps**:
1. Create `storage/StorageFactory.java`
2. Move JDBI setup code there
3. `StorageFactory` returns `ServiceRegistry`
4. Remove `Builder` inner class from `ServiceRegistry`
5. Update `AppBootstrap` to use `StorageFactory`

**Risk**: Medium
**Rollback**: Restore `Builder` inner class
**Tests**: App startup tests + `ServiceRegistryTest`

---

### Priority: Medium (Backlog)

#### R-007: Centralize AppConfig via Constructor Injection

**Problem**: 8 files create `static final AppConfig CONFIG = AppConfig.defaults()` independently. Domain models shouldn't hold config.

**Files**: `User.java`, `MessagingService.java`, `ProfileCompletionService.java`, `PurgeService.java`, `LoginController.java`, `ProfileViewModel.java`, `LoginViewModel.java`, `PreferencesViewModel.java`

**Steps**:
1. Move `User.java` validation logic referencing CONFIG → `ValidationService`
2. Services: accept `AppConfig` via constructor
3. ViewModels: receive config through `ViewModelFactory`

**Risk**: Medium
**Tests**: All affected file tests

---

#### R-008: Extract Large Methods (>100 LOC)

**Problem**: 5+ methods exceed 100 LOC — too complex for single methods.

**Extractions**:
1. `MatchesController` ~200 LOC → `LikesTabRenderer`
2. `ProfileController.handleEditDealbreakers()` 166 LOC → `DealbreakersChipHelper`
3. `MatchQualityService` 131 LOC → `LifestyleScorer`
4. `ProfileController.wireAuxiliaryActions()` 116 LOC → split into named methods

**Risk**: Low (method extraction preserves behavior)
**Tests**: All existing tests for these 4 files

---

#### R-009: Genericize ProfileHandler Dealbreaker Methods

**Problem**: 6 near-identical methods L635–L697 in ProfileHandler.

**Steps**: Create generic `editEnumDealbreaker()` method replacing all 6.

**Risk**: Low
**Tests**: Dealbreaker test coverage

---

### Priority: Low (Quick Wins)

#### R-010: Merge CliConstants + CliUtilities → CliSupport

48 + 49 = 97 LOC. Same purpose. **-1 file**. Risk: Low.

#### R-011: Delete/Merge PurgeService

60 LOC, zero imports. Dead code. **-1 file**. Risk: Low.

#### R-012: Inline SoftDeletable

22 LOC interface, 2 implementors, no polymorphism. **-1 file**. Risk: Low.

#### R-013: Merge EnumSetUtil or Inline

70 LOC, 1 consumer. **-1 file**. Risk: Low.

#### R-014: Merge ErrorMessages → ValidationService

49 LOC, 3 consumers. **-1 file**. Risk: Low.

#### R-015: Move MODULE_OVERVIEW.md to docs/

Non-source files in Java packages. Risk: Zero.

#### R-016: Clean Root-Level Workspace Artifacts

Move 20+ stale audit/analysis files to `docs/audits/`. Add to `.gitignore`. Risk: Zero.

#### R-017: Shared handleBack() in BaseController

6 controllers have identical method. **-6 duplicate methods**. Risk: Low.

#### R-018: Move Standalone Enums into User.java

3 files (Gender 7 LOC, UserState 11 LOC, VerificationMethod 11 LOC). **-3 files** but widespread import changes. Risk: Medium.

---

## 9. Merge Candidates Summary

| Files | Destination | Reason | File Savings |
| --- | --- | --- | --- |
| `CliConstants` + `CliUtilities` | `CliSupport.java` | Same purpose, tiny size | -1 |
| `PurgeService` + `CleanupService` | `CleanupService.java` | Overlapping, PurgeService dead | -1 |
| `SoftDeletable` → User + Match | Inline | 2 implementors, no polymorphism | -1 |
| `EnumSetUtil` → ProfileHandler | Inline | 1 consumer | -1 |
| `ErrorMessages` → ValidationService | Merge | 3 consumers, validation constants | -1 |
| `Gender` + `UserState` + `VerificationMethod` → User | Nested enums | Tiny User-related enums | -3 |

**Total: -8 files** (126 → ~118 main files)

---

## 10. Extraction Candidates Summary

| Source File | Component | Target | LOC Moved |
| --- | --- | --- | --- |
| `ProfileController.java` | Dealbreaker chip creation | `DealbreakersChipHelper` | ~166 |
| `MatchesController.java` | Likes tab rendering | `LikesTabRenderer` | ~200 |
| `MatchQualityService.java` | Lifestyle scoring | `LifestyleScorer` | ~131 |
| `ServiceRegistry.java` | Builder.buildH2() | `StorageFactory` | ~120 |
| `DatabaseManager.java` | Schema DDL | `SchemaMigrator` | ~200 |
| `LoginController.java` | User list cell | `UserListCellFactory` | ~80 |

**Creates +6 new files, but significantly reduces complexity in 6 god-classes.**

---

## 11. High-Risk Architecture Warnings

### Layer Direction Violations
- **core → storage**: `AppBootstrap`, `ServiceRegistry` (2 files)
- **core → framework**: `ConfigLoader` imports Jackson (1 file)
- **viewmodel → storage**: 6 ViewModels bypass service layer
- **Verdict**: 9 total violations — must fix before adding more features

### God Classes
| File | LOC | Fields | Max Method LOC | Verdict |
| --- | --- | --- | --- | --- |
| `ServiceRegistry` | 472 | 26+ | ~100 (buildH2) | Extract StorageFactory |
| `ProfileController` | 925 | ~20 | 166 | Extract 2 helpers |
| `ProfileHandler` | 807 | ~15 | 6×duplicate | Genericize |
| `MatchesController` | 787 | ~15 | ~200 | Extract tabs helper |

### Hidden Shared State (Singletons)
| Singleton | Pattern | Risk |
| --- | --- | --- |
| `AppSession.getInstance()` | Thread-safe singleton | Parallel test isolation impossible |
| `AppBootstrap.initialize()` | Once-only gate | `reset()` method exists for tests |
| `DatabaseManager.getInstance()` | Connection pool | Single DB instance |
| `NavigationService.getInstance()` | UI navigation | UI-only, acceptable |
| `AppClock` | Testable clock | Has `setFixed()` for tests |

### Duplicate Utilities
- **23 files**: Logging helpers (~2,645 LOC)
- **6 controllers**: handleBack() duplication
- **8 files**: Static AppConfig.defaults() instantiation
- **6 methods**: Copy-paste dealbreaker editing (ProfileHandler)

---

## 12. Suggested Target Architecture

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

**After all refactors**:
- Main files: ~120 (—6 from merges, +6 from extractions, -3 moves)
- Violations: 0 (from 9)
- God classes: 0 (from 4)
- Duplicate LOC eliminated: ~3,000
- New architectural tests: 3 ArchUnit rules

---

## 13. Package Dependency Graph

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

---

## 14. Implementation Roadmap

| Phase | Refactors | Effort | Risk | Impact |
| --- | --- | --- | --- | --- |
| **Week 1** | R-010, R-011, R-012, R-013, R-014, R-015, R-016, R-017 (Quick Wins) | 2h | Low | -7 files, cleaner workspace |
| **Week 2** | R-005 (LoggingSupport), R-009 (Genericize dealbreakers) | 3h | Low | -2,645 LOC duplication |
| **Week 3** | R-002 (FX-thread fix), R-001 (ViewModel layer fix) | 6h | Medium | Fix UI freezes + proper layering |
| **Week 4** | R-003 (core layer violations), R-006 (StorageFactory extraction) | 4h | Medium | Clean architecture |
| **Week 5** | R-004 (Sub-packages), R-007 (AppConfig injection) | 8h | High | Full module organization |
| **Week 6** | R-008 (Extract large methods), R-018 (Enum nesting) | 4h | Low-Med | Reduced complexity |

**Total estimated effort: ~27 hours over 6 weeks**

---

*End of Architecture Audit Report*
