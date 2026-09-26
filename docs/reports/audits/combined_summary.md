# Combined Architecture Audit — Summary (Unified)

**Date:** 2026-02-07
**Scope:** Full repository (reported as 126 main + 56 test files; ~185 code files in `src/`)
**Sources merged:** `summary.md`, `summaryByGPT.md`, `audit_summary_v3.md` (deduplicated)

---

## Snapshot Metrics (All Reported Values)

| Metric                        | Value                                   | Sources                                  |
|-------------------------------|-----------------------------------------|------------------------------------------|
| Main Java files               | 126                                     | `summary.md`, `summaryByGPT.md`          |
| Test Java files               | 56                                      | `summary.md`, `summaryByGPT.md`          |
| Total code files in `src/`    | 185 (Java 182, CSS 2, XML 1)            | `summaryByGPT.md`, `audit_summary_v3.md` |
| Code lines                    | 34,289                                  | `summary.md`                             |
| Avg LOC per main Java file    | 154.63                                  | `summaryByGPT.md`                        |
| Largest file                  | `ProfileController.java` (667 LOC)      | `summaryByGPT.md`                        |
| Most complex file (heuristic) | `ProfileHandler.java` (≈105)            | `summaryByGPT.md`                        |
| Test coverage (reported)      | 60%                                     | `summaryByGPT.md`                        |
| Duplicate blocks detected     | 6 (IDEA diagnostics)                    | `summaryByGPT.md`                        |
| NEW issues found (v3)         | 171+                                    | `audit_summary_v3.md`                    |
| v3 severity distribution      | 6 CRITICAL, 57 HIGH, 68 MEDIUM, 40+ LOW | `audit_summary_v3.md`                    |

---

## Top Findings (Merged)

### Critical / Highest Impact

1. **ViewModel → Storage layer violations** (6 files) + **FX-thread DB queries** (3 VMs): breaks MVVM and causes UI freezes. 🔜 P05
2. **Flat `core/` package** (44 files): obscures boundaries, drives coupling. 📋 P03
3. **ServiceRegistry god-class** (472 LOC, 26+ fields, 25‑param constructor) with storage wiring in `core/`. 📋 P03
4. **Logging helper duplication** (23 files, ~2,645 LOC) from PMD GuardLogStatement. 🔜 P04
5. **Dead/orphaned code** (`PurgeService` 📋 P02, `SoftDeletable` 📋 P03, `EnumSetUtil` 🔜 P06, `ErrorMessages` 📋 P02).
6. **Thread safety defects (v3)**: race conditions in `DatabaseManager` 📋 P01, `MatchingViewModel` 🔜 P05, `AppBootstrap` 📋 P02, `NavigationService` 🔜 P07, `UndoService` 📋 P02, `DailyService` 📋 P02.
7. **No transaction boundary in `MatchingService.recordLike()`** (v3): duplicate match risk. 📋 P02
8. **Date‑dependent tests** (7 files) using `LocalDate.now()` → flaky failures. 🔜 P08

### Additional Key Findings

- **Presentation logic duplication** between CLI handlers and JavaFX ViewModels (IDEA duplicate reports confirm).
- **Oversized UI controllers** (`ProfileController`, `MatchesController`, `LoginController`) and **oversized CLI handlers** (`ProfileHandler`, `MatchingHandler`, `MessagingHandler`).
- **Core wiring leaks into storage** via `AppBootstrap` and `ServiceRegistry`.
- **God-class risk** in `DatabaseManager`, `User`, `AppConfig`, `MatchQualityService`.
- **Exception handling gaps** (silent SQL exceptions, hidden navigation errors, inconsistent Result patterns).
- **SQL issues** (N+1 queries, missing indexes, CSV‑serialized enums, mapping precision loss).
- **Interface design issues** (fat `StatsStorage`, mixed abstraction in `MessagingStorage`, Optional vs null inconsistencies).
- **Null-safety gaps** (missing `@Nullable` on 18 methods; mutable list returns).
- **Magic numbers** scattered across UI timing, scoring thresholds, completion scoring, cache config.

---

## Prioritized Action List (Unified)

### Do First (Week 1–2) — Quick Wins & Critical Fixes

- Delete `PurgeService` (dead code). 📋 P02
- Merge `CliConstants + CliUtilities → CliSupport`. 🔜 P06
- Inline `SoftDeletable` into `User` + `Match`. 📋 P03
- Inline/generalize `EnumSetUtil`. 🔜 P06
- Merge `ErrorMessages → ValidationService`. 📋 P02
- Move `MODULE_OVERVIEW.md` to `docs/`. 📋 P02
- Add shared `handleBack()` to `BaseController`. 🔜 P07
- Create `LoggingSupport` mixin interface. 🔜 P04
- Genericize 6 dealbreaker edit methods. 🔜 P06
- Clean root-level artifacts. 📋 P02
- Merge/rename overlapping UI utilities (`UiHelpers`, `UiServices`, `UiAnimations`). 🔜 P07
- Extract repeated SQL fragments in `Jdbi*Storage` into shared constants/helpers. 📋 P01
- **Thread‑safety fixes:** add `volatile` fields, replace `LinkedList` with `ConcurrentLinkedQueue`, use `computeIfAbsent()`. 📋 P01+P02
- **Transaction boundary:** wrap `MatchingService.recordLike()`. 📋 P02

### Do Next (Week 3–4) — Architectural Fixes

- Fix FX‑thread DB queries (3 ViewModels). 🔜 P05
- Route ViewModels through services only (remove `core.storage.*` imports). 🔜 P05
- Move `AppBootstrap` + `ConfigLoader` out of `core/`. 📋 P03
- Extract `StorageFactory` from `ServiceRegistry`. 📋 P03
- Add ArchUnit layer‑violation tests. 📋 P03
- Fix swallowed exceptions (DatabaseManager, ConfigLoader, NavigationService, MatchingService). 📋 P01+P02 (partial), 🔜 P07 (NavigationService)
- Replace N+1 query in `JdbiMatchStorage` with UNION; add missing indexes. 📋 P01
- Split fat interfaces (`StatsStorage`, `MessagingStorage`). 🔜 P05

### Do Later (Week 5–6) — Structural Improvements

- Split `core/` into sub‑packages. 📋 P03
- Inject `AppConfig` via constructor (8 files). 📋 P03
- Extract large methods (>100 LOC). 🔜 P06/P07
- Nest standalone enums into `User.java`. 📋 P03
- Create constants classes (`AnimationConstants`, scoring thresholds in `AppConfig`, `CacheConstants`). 🔜 P08
- Add `@Nullable` annotations and enforce null‑safety rules. 🔜 P08
- Standardize `Optional` vs `null` usage across storage interfaces. 📝 Backlog

---

## Root Causes (Merged)

1. Organic growth without package planning → flat `core/` with 44 files.
2. PMD GuardLogStatement workaround copied instead of shared.
3. Expedient shortcuts in ViewModels (direct storage access).
4. Manual DI at scale → `ServiceRegistry` god‑class.
5. Lack of ArchUnit or layer tests → violations accumulated.
6. No concurrency testing → race conditions survived.
7. Date‑naive test fixtures using `LocalDate.now()`.
8. Catch‑and‑log exception handling copied without semantics.
9. Interface design by accretion (ISP violations).
10. Scattered constants (magic numbers) and inconsistent Result patterns.

---

## Prevention Recommendations (Merged)

1. Add ArchUnit tests for layer rules **and** thread‑safety constraints.
2. Require Clock injection (flag `LocalDate.now()` / `Instant.now()` in tests).
3. Standardize Result pattern; lint for exceptions in services.
4. Centralize logging helpers via `LoggingSupport`.
5. Document module boundaries (`package-info.java`).
6. Enforce constructor injection; avoid static config.
7. Cap interface size (review >10 methods).
8. Add constants checklist in PR reviews.

---

## Notes & Limitations

- Cyclomatic complexity values are heuristic estimates.
- No circular dependencies detected at module level.
- Dynamic UI behavior and performance require runtime checks.

---

For full details, file‑by‑file summaries, category breakdowns, and roadmaps, see `combined_report.md`.