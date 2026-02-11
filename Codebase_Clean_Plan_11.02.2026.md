# Codebase Consolidation & Reorganization Plan

**Status: ✅ COMPLETE (2026-02-11)**

**Goal:** Reduce 131 main source files → ~102 files (-29).
**Result:** 131 → 102 main files (-29 exactly). 825 tests passing. BUILD SUCCESS.
**Strategy:** Nested static types, Record consolidation, dead code removal, and logical package layering.
**Safety:** Verified builds after every phase. All 820+ tests kept passing.

---

## Phase 1: Core Model Consolidation (-3 files)

*Focus: Merging tiny data carriers into their semantic parents using Java 25 Records.*

### 1A. PacePreferences → INTO `Preferences.java`

- **Source:** `core/PacePreferences.java` (105 lines — Record + 4 Enums)
- **Target:** `core/Preferences.java` (250 lines)
- **Result:** ~355 lines
- **Action:** Move as `public static record PacePreferences` inside `Preferences`. Move its 4 enums (`MessagingFrequency`, `TimeToFirstDate`, `CommunicationStyle`, `DepthPreference`) inside `Preferences` as well.
- **Import updates:** 14 files change `datingapp.core.PacePreferences` → `datingapp.core.Preferences.PacePreferences`.

### 1B. DailyPick → INTO `DailyService.java`

- **Source:** `core/DailyPick.java` (17 lines — tiny Record)
- **Target:** `core/DailyService.java` (320 lines)
- **Result:** ~337 lines
- **Action:** Move as `public static record DailyPick` inside the service that produces it.
- **Import updates:** 2 main files change to `DailyService.DailyPick`.

### 1C. Social → INTO `UserInteractions.java`

- **Source:** `core/Social.java` (96 lines — FriendRequest + Notification container)
- **Target:** `core/UserInteractions.java` (119 lines)
- **Result:** ~215 lines
- **Action:** Move `FriendRequest` and `Notification` records, plus `isTerminalStatus()` helper, inside `UserInteractions`.
- **Outcome:** `Social.java` is deleted.
- **Import updates:** 6 files change `datingapp.core.Social` → `datingapp.core.UserInteractions`.

**Verify:** `mvn spotless:apply && mvn verify`

---

## Phase 2: Storage Layer Consolidation (-8 files)

*Focus: Removing pass-through adapters, consolidating small storage interfaces, and deleting dead code.*

### 2A. DailyPickViewStorage → INTO `StatsStorage.java`

- **Source:** `core/storage/DailyPickViewStorage.java` (32 lines, 3 methods) + `storage/jdbi/JdbiDailyPickViewStorage.java` (31 lines)
- **Target:** `core/storage/StatsStorage.java` (132 lines) + `storage/jdbi/JdbiStatsStorage.java` (296 lines)
- **Action:** Add the 3 methods (`markAsViewed`, `isViewed`, `deleteOlderThan`) to `StatsStorage` interface. Merge JDBI implementation into `JdbiStatsStorage`.
- **Import updates:** 6 files (ServiceRegistry, StorageFactory, DailyService, tests, etc.)
- **Files eliminated:** 2

### 2B. TrustSafetyStorage (Merge Report + Block)

- **Sources:** `core/storage/ReportStorage.java` (27 lines, 6 methods) + `core/storage/BlockStorage.java` (40 lines, 8 methods)
- **Target:** `core/storage/TrustSafetyStorage.java` (NEW — ~67 lines, 14 methods)
- **JDBI:** Merge `storage/jdbi/JdbiBlockStorage.java` (89 lines) + `storage/jdbi/JdbiReportStorage.java` (71 lines) → `storage/jdbi/JdbiTrustSafetyStorage.java` (NEW)
- **Action:** Create one unified storage interface for all safety concerns (blocking + reporting). Delete the 4 old files, create 2 new files.
- **Import updates:** BlockStorage (18 files) + ReportStorage (8 files) — many overlap. All change to `TrustSafetyStorage`.
- **Files eliminated:** 2 (4 deleted - 2 created)

### 2C. Eliminate JDBI Pass-Through Adapters

- **Delete:** `storage/jdbi/JdbiStandoutStorageAdapter.java` (39 lines — zero custom logic)
- **Delete:** `storage/jdbi/JdbiUndoStorageAdapter.java` (58 lines — zero custom logic)
- **Action:** In `StorageFactory`, use the JDBI proxy directly (cast to `Standout.Storage` / `UndoState.Storage`).
- **Keep:** `JdbiUserStorageAdapter.java` (98 lines — has custom `findByIds()` logic, not a pass-through)
- **Files eliminated:** 2

### 2D. Consolidate EnumSet JDBI Helpers

- **Sources:** `storage/jdbi/EnumSetColumnMapper.java` (42 lines) + `storage/jdbi/EnumSetArgumentFactory.java` (36 lines)
- **Target:** `storage/jdbi/EnumSetJdbiSupport.java` (NEW — ~78 lines)
- **Action:** Both handle EnumSet↔JDBI serialization. Combine into single file with 2 public static inner classes.
- **Files eliminated:** 1 (2 → 1)

### 2E. Delete Dead Code: `TransactionTemplate.java`

- **Source:** `storage/TransactionTemplate.java` (152 lines)
- **Action:** **DELETE** — confirmed zero references outside its own file. No class imports, instantiates, or calls it. This is dead code.
- **Files eliminated:** 1

**Verify:** `mvn spotless:apply && mvn verify`

---

## Phase 3: CLI Layer Consolidation (-5 files)

*Focus: Merging tiny CLI utilities and related handlers.*

### 3A. InputReader + EnumMenu → INTO `CliSupport.java`

- **Sources:** `app/cli/InputReader.java` (32 lines) + `app/cli/EnumMenu.java` (122 lines)
- **Target:** `app/cli/CliSupport.java` (108 lines)
- **Result:** ~262 lines
- **Action:** Move InputReader as `public static class InputReader` inside CliSupport. Move EnumMenu similarly. These are purely CLI utilities.
- **Import updates:** InputReader: 2 files (Main.java, test). EnumMenu: 0 (same package).
- **Files eliminated:** 2

### 3B. ProfileNotesHandler → INTO `ProfileHandler.java`

- **Source:** `app/cli/ProfileNotesHandler.java` (224 lines)
- **Target:** `app/cli/ProfileHandler.java` (789 lines)
- **Result:** ~1013 lines
- **Action:** Move profile notes methods into ProfileHandler. Both deal with profile management.
- **HandlerFactory update:** Remove separate `profileNotes()` factory method, integrate into `profile()`.
- **Files eliminated:** 1

### 3C. RelationshipHandler + LikerBrowserHandler → INTO `MatchingHandler.java`

- **Sources:** `app/cli/RelationshipHandler.java` (128 lines) + `app/cli/LikerBrowserHandler.java` (122 lines)
- **Target:** `app/cli/MatchingHandler.java` (733 lines)
- **Result:** ~983 lines
- **Action:** Move relationship transition methods (unmatch, block, friend zone) and liker browsing into MatchingHandler. These are all part of the matching flow.
- **HandlerFactory update:** Remove separate factory methods for relationship/likerBrowser, integrate into `matching()`.
- **Files eliminated:** 2

**Verify:** `mvn spotless:apply && mvn verify`

---

## Phase 4: REST API Consolidation (-3 files)

### 4A. Inline Routes INTO `RestApiServer.java`

- **Sources:** `app/api/UserRoutes.java` (104 lines) + `app/api/MatchRoutes.java` (118 lines) + `app/api/MessagingRoutes.java` (134 lines)
- **Target:** `app/api/RestApiServer.java` (166 lines)
- **Result:** ~522 lines
- **Action:** Move route registration methods directly into RestApiServer as private methods. Confirmed: no external file imports these routes (0 external references).
- **Files eliminated:** 3

**Verify:** `mvn spotless:apply && mvn verify`

---

## Phase 5: UI Layer Consolidation (-8 files)

*Focus: Removing redundant adapters and merging helpers into their controllers.*

### 5A. Consolidate UI Data Adapters (4 → 1)

- **Sources:**
  - `ui/viewmodel/data/UiUserStore.java` (24 lines — interface)
  - `ui/viewmodel/data/UiMatchDataAccess.java` (34 lines — interface)
  - `ui/viewmodel/data/StorageUiUserStore.java` (39 lines — implementation)
  - `ui/viewmodel/data/StorageUiMatchDataAccess.java` (61 lines — implementation)
- **Target:** `ui/viewmodel/data/UiDataAdapters.java` (NEW — ~158 lines)
- **Action:** Create single file with both interfaces and both implementations as `public static` nested types. The interfaces remain public for ViewModel usage.
- **Files eliminated:** 3 (4 → 1)

### 5B. Constants & Animations

- **CacheConstants** (15 lines) → Merge into `ui/constants/UiConstants.java` (rename from `AnimationConstants.java`, 69 lines). Result: ~84 lines.
- **ConfettiAnimation** (137 lines) → Merge into `ui/util/UiAnimations.java` (260 lines) as a `public static class`. Result: ~397 lines.
- **Import updates:** All files importing `AnimationConstants` → `UiConstants`, plus 2 files importing `CacheConstants` → `UiConstants`. ConfettiAnimation: 2 main files + 1 test.
- **Files eliminated:** 2

### 5C. Controller Helpers

- **LikesTabRenderer** (114 lines) → Merge into `MatchesController.java` (693 lines) as private inner class. Result: ~807 lines. Same-package, 0 explicit imports.
- **DealbreakersChipHelper** (60 lines) → Merge into `PreferencesController.java` (254 lines) as private helper methods. Result: ~314 lines. Same-package, 0 explicit imports.
- **UserListCellFactory** (217 lines) → Merge into `LoginController.java` (450 lines) as private static inner class. Result: ~667 lines. Confirmed: only 1 call site (LoginController line 134).
- **Files eliminated:** 3

**Verify:** `mvn spotless:apply && mvn verify`

---

## Phase 6: Infrastructure Cleanup (-2 files)

### 6A. CleanupService → INTO `SessionService.java`

- **Source:** `core/CleanupService.java` (80 lines — calls 2 storage methods)
- **Target:** `core/SessionService.java` (199 lines)
- **Result:** ~279 lines
- **Action:** Add `runCleanup()` method and `CleanupResult` record to SessionService. SessionService already imports `SwipeSessionStorage`; add `StatsStorage` dependency for daily pick cleanup.
- **Import updates:** 1 file (StorageFactory)
- **Files eliminated:** 1

### 6B. StorageException → INTO `DatabaseManager.java`

- **Source:** `storage/StorageException.java` (20 lines — 3 constructors)
- **Target:** `storage/DatabaseManager.java` (139 lines)
- **Result:** ~159 lines
- **Action:** Move as `public static class StorageException extends RuntimeException` inside DatabaseManager.
- **Import updates:** 2 files (JdbiSocialStorage, JdbiTransactionExecutor)
- **Files eliminated:** 1

**Verify:** `mvn spotless:apply && mvn verify`

---

## Phase 7: The Great Reorganization (0 file count change, major navigation improvement)

*Focus: Fixing the "Flat Core" problem. This changes imports globally but doesn't change file counts.*

### 7A. Create `core/model/`

Move **Domain Models** from `core/` root to `core/model/`:

- `User.java`, `Match.java`, `Messaging.java`, `Preferences.java` (with PacePreferences), `Dealbreakers.java`, `UserInteractions.java` (with Social types), `Stats.java`, `SwipeSession.java`, `Achievement.java`, `UndoState.java`, `Standout.java`
- **11 files moved**

### 7B. Create `core/service/`

Move **Services** from `core/` root to `core/service/`:

- `CandidateFinder.java`, `MatchingService.java`, `MatchQualityService.java`, `AchievementService.java`, `DailyService.java` (with DailyPick), `ProfileCompletionService.java`, `RelationshipTransitionService.java`, `MessagingService.java`, `SessionService.java` (with CleanupService), `StatsService.java`, `StandoutsService.java`, `TrustSafetyService.java`, `UndoService.java`, `ValidationService.java`
- **14 files moved**

### 7C. Flatten Singleton Packages

- `core/constants/` → Move `ScoringConstants.java` to `core/` root (only file in package). Delete empty package.
- `storage/mapper/` → Move `MapperHelper.java` to `storage/jdbi/` (only file in package). Delete empty package.
- `ui/component/` → Move `UiComponents.java` to `ui/` root (only file in package). Delete empty package.

### 7D. `core/` root retains utility/infrastructure files:

After moves, `core/` root keeps only: `AppConfig.java`, `AppClock.java`, `AppSession.java`, `EnumSetUtil.java`, `LoggingSupport.java`, `PerformanceMonitor.java`, `ServiceRegistry.java`, `ScoringConstants.java` — **8 files**

### 7E. Import Update Strategy

Use `sd` (Stream Editor) or `ast-grep` for mechanical import updates:

```
import datingapp.core.User;             → import datingapp.core.model.User;
import datingapp.core.MatchingService;  → import datingapp.core.service.MatchingService;
import datingapp.core.PacePreferences;  → import datingapp.core.model.Preferences.PacePreferences;
```

After all moves: `mvn spotless:apply && mvn clean verify`

---

## Execution Summary

| Phase     | Description            | Files Removed | Risk             |
|-----------|------------------------|---------------|------------------|
| **1**     | Core Model Records     | -3            | Low              |
| **2**     | Storage Consolidation  | **-8**        | Medium           |
| **3**     | CLI Consolidation      | -5            | Low              |
| **4**     | REST API Inlining      | -3            | Low              |
| **5**     | UI Adapters & Helpers  | **-8**        | Medium           |
| **6**     | Infrastructure Cleanup | -2            | Low              |
| **7**     | Package Reorganization | 0             | Medium (Imports) |
| **Total** | **Reduction**          | **-29 Files** |                  |

**Final State:**

- **Files:** ~102 Main Source Files (down from 131).
- **Structure:** Clean, tiered packages (`core/model/`, `core/service/`, `core/`). No flat directories.
- **Logic:** Preserved 100%. No features removed. All 820+ tests passing.

---

## Execution Order & Dependencies

```
Phase 1 (models)  ──→  Phase 2 (storage)  ──→  Phase 3 (CLI)
                                                     │
Phase 4 (API) ←── can run after Phase 1 ─────────────┘
                                                     │
Phase 5 (UI) ←── can run after Phase 2 ──────────────┘
                                                     │
Phase 6 (infra) ←── after all above ─────────────────┘
                                                     │
Phase 7 (reorg) ←── MUST be last ────────────────────┘
```

Phases 3, 4, 5 are independent and can be done in any order after Phase 2.
Phase 7 MUST be last since it changes all import paths globally.

---

## Critical Files Modified Across Multiple Phases

- `core/ServiceRegistry.java` — references nearly every storage interface
- `storage/StorageFactory.java` — wires all JDBI implementations
- `app/cli/HandlerFactory.java` — creates all CLI handlers
- `ui/ViewModelFactory.java` — creates all ViewModels and adapters
- `core/testutil/TestStorages.java` — mock implementations of storage interfaces

---

## Test Impact

- **Tests needing merge** (matching handler merges):
  - `RelationshipHandlerTest.java` + `LikerBrowserHandlerTest.java` → merge into `MatchingHandlerTest.java`
  - `ProfileNotesHandlerTest.java` → merge into ProfileHandler tests or keep standalone
  - `DailyPickServiceTest.java` — update DailyPick import
  - `PaceCompatibilityTest.java` — update PacePreferences import

- **Tests needing import-only updates:** ~30+ test files (mechanical via `sd`/`ast-grep`)
- **No tests should be deleted** — all 820+ tests must continue passing

---

## Rejected Merges (from prior proposals)

These merges were evaluated and **rejected** for specific reasons:

| Proposed Merge                                        | Reason for Rejection                                                                                                                                                                                                                 |
|-------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SchemaInitializer + MigrationRunner → DatabaseManager | **Reverses intentional SRP extraction.** SchemaInitializer's own JavaDoc says it was extracted from DatabaseManager for single-responsibility. Merging creates an 868-line mega-file mixing DDL, migrations, and connection pooling. |
| ConfigLoader → AppConfig                              | **Violates architecture.** ConfigLoader (in `app/`) has Jackson imports (`com.fasterxml.jackson`). AppConfig (in `core/`) must have ZERO framework imports. Moving it would contaminate the core package.                            |
| ErrorHandler → BaseController                         | **Well-placed despite tiny size.** 10-line `@FunctionalInterface` used across 9+ ViewModels. Nesting it forces awkward imports.                                                                                                      |

---

## Verification Plan

After EACH phase:
1. `mvn spotless:apply` — fix formatting
2. `$out = mvn verify 2>&1 | Out-String` — single build run, capture output
3. Check: `$out | Select-String "BUILD (SUCCESS|FAILURE)" | Select-Object -Last 1`
4. Check: `$out | Select-String "Tests run:" | Select-Object -Last 1`
5. If failures: fix before proceeding to next phase

After ALL phases:
- `mvn compile && mvn exec:exec` — verify CLI runs
- `mvn javafx:run` — verify GUI launches

---

## Critical Instructions for Execution

1. **Do not skip Verification steps.** If `mvn verify` fails, stop and fix immediately.
2. **Use `ast-grep` or `sd`** for Phase 7 import updates to ensure accuracy.
3. **Prioritize Simplicity:** If a merge requires rewriting complex logic, stop and ask. These should be mostly structural moves (static nesting).
4. **Build Command Discipline:** Capture `mvn verify` output ONCE into `$out`, then query `$out` multiple times. NEVER run the build multiple times with different filters.
5. **Nested types must be `public static`** — forgetting `static` on nested records/classes causes compilation errors.
