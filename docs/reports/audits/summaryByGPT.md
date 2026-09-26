# Architecture Audit Summary (2026-02-07)

## Snapshot

| Metric                        | Value                                                                    |
|-------------------------------|--------------------------------------------------------------------------|
| Source files (src/)           | 185 code files (Java 182, CSS 2, XML 1)                                  |
| Main Java files               | 126                                                                      |
| Test Java files               | 56                                                                       |
| Avg LOC per main Java file    | 154.63                                                                   |
| Largest file                  | `src/main/java/datingapp/ui/controller/ProfileController.java` (667 LOC) |
| Most complex file (heuristic) | `src/main/java/datingapp/app/cli/ProfileHandler.java` (≈105)             |
| Test coverage (reported)      | 60% (from `analysis-summary.json`)                                       |
| Duplicate blocks detected     | 6 (see `IDEA_Diagnostics/DuplicatedCode_aggregate.xml`)                  |

## Key findings

- **Presentation logic duplication** between CLI handlers and JavaFX ViewModels (e.g., `MatchingHandler` vs `MatchingViewModel`) is confirmed by IDE duplicate reports and large file sizes.
- **UI controllers are doing too much**: `ProfileController`, `MatchesController`, and `LoginController` are large, import-heavy, and combine UI wiring with business orchestration.
- **CLI handlers are complex orchestration hubs** (not just I/O), driving up complexity and maintenance cost.
- **Core wiring leaks into storage**: `AppBootstrap` and `ServiceRegistry` live in `core/` but import `storage` infrastructure (layering boundary blur).
- **God-class risk** in `DatabaseManager`, `User`, `AppConfig`, and `MatchQualityService` (size/complexity), with limited internal modularity.

## Prioritized action list

### High impact (next 2–4 sprints)

1. **Introduce Application Services** (`core/app/*`) to centralize matching/profile/messaging orchestration for both CLI + JavaFX.
2. **Modularize `ServiceRegistry`** into submodules (storage, matching, messaging, safety, stats) to reduce constructor size and wiring noise.
3. **Split `DatabaseManager`** into schema initialization/migrations/connection providers.
4. **Unify match quality scoring** by using `MatchQualityService` in UI instead of duplicate heuristics.
5. **Break down large UI controllers** into sub-controllers/components and move non-UI logic into services.
6. **Extract profile domain sub-objects** from `User` (profile + preferences + photos) to reduce domain class size.

### Medium/low impact (quick wins)

- Consolidate shared CLI prompt flows into a reusable helper.
- Merge or rename overlapping utilities (`UiHelpers`, `UiServices`, `UiAnimations`).
- Extract repeated SQL fragments in `Jdbi*Storage` into shared constants.
- Replace duplicate ID-generation logic (Match/Messaging) with a shared helper.
- Add a typed `NavigationContext` instead of raw `Object`.

## Notes & limitations

- Cyclomatic complexity is a **heuristic** estimate based on keyword counts, not a full parser.
- No circular dependencies detected at the module level.
- Dynamic UI behavior and performance issues are marked **needs runtime check** in the full report.

For the detailed audit with file-by-file summaries, cluster analysis, and a stepwise refactor plan, see `reportByGPT.md` (also mirrored in `report.md`).
