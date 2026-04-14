# AGENTS.md

> **Updated:** 2026-04-14
> **Role in the instruction stack:** lowest-level workflow guide for agents working in this repo.
> **Hierarchy:** `.github/copilot-instructions.md` → `CLAUDE.md` → `AGENTS.md`.

This file is intentionally not a second copy of the architecture snapshot in `CLAUDE.md`.
Use it for execution discipline, tool choice, validation order, and doc-maintenance rules.

## Source of truth

If any markdown guidance and the code disagree, trust:

- `src/main/java`
- `src/test/java`
- `pom.xml`

## Working mode for agents

1. Start from current code and current build config, not historical docs.
2. For multi-step work, keep an explicit todo list with exactly one step in progress.
3. Read the owning file and the nearest deciding seam fully before editing.
4. Prefer one coordinator for shared files and only parallelize independent work.
5. Make the smallest coherent change that actually fixes the contract or behavior.

## Search and tool discipline

- For `.java`, prefer symbol-aware/LSP navigation first, then `ast-grep`, then plain-text search when needed.
- Use read-only subagents for focused codebase exploration; if one helper path is unavailable, switch tools instead of retrying the same failure mode.
- Use execution-oriented helpers for Maven/test runs rather than manually chaining shell commands in an interactive terminal.
- Prefer symbol-aware rename/usages tools when changing names across files.
- On Windows PowerShell, use `mvn --% ...` when Maven arguments contain commas or special characters that PowerShell might parse.
- For PostgreSQL runtime work, prefer an already-running local PostgreSQL instance first; use Docker only as a disposable fallback when no local server is available.

## Editing discipline

- Preserve layer boundaries:
  - `core/` stays framework-free
  - `app/usecase/*` stays the app boundary
  - `ui/viewmodel/*` should go through `BaseViewModel`, `ui/async/*`, and `UiDataAdapters`
- Do not reintroduce removed legacy names such as `AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, or `ui/controller` references.
- Prefer the canonical helpers already in the repo:
  - `AppClock.now()` / `AppClock.today()` / `AppClock.clock()` depending on whether the code consumes instants, dates, or an injected `Clock`
  - `Match.generateId(...)` / `Conversation.generateId(...)`
  - `User.copy()`
  - `EnumSetUtil.safeCopy(...)`
- For ViewModel actions that can hit storage or network, keep the work inside `ViewModelAsyncScope`; do not invoke use cases synchronously on the FX thread.
- For user-visible controller image loads, prefer `ImageCache.getImageAsync(...)`; keep synchronous `getImage(...)` for preload or non-UI paths.
- For location UX changes, keep `LocationService`, `GeocodingService` implementations, `LocationSelectionDialog`, `ProfileViewModel`, and `ViewModelFactory` aligned.
- Treat any remaining convenience constructors outside `ViewModelFactory` as compatibility/test shims, not the production composition path.

## Verification discipline

Run verification in this order unless the task clearly needs a different sequence:

1. Check touched files for errors.
2. Run focused tests for the changed area.
3. Run a broader smoke suite if multiple subsystems changed.
4. If the work touches PostgreSQL runtime support, run the local smoke path (`.\run_postgresql_smoke.ps1` or equivalent property-driven `PostgresqlRuntimeSmokeTest`) during targeted validation.
5. Run the full Maven quality gate before claiming completion:

```powershell
mvn spotless:apply verify
```

6. Run the repo-level full local verification path when the change is substantial or affects runtime/verification seams:

```powershell
.\run_verify.ps1
```

Use targeted Maven test selection for faster iteration, e.g.:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,MatchingUseCasesTest test
```

For async/location/UI changes, the highest-signal targeted slices are the directly relevant classes among:

```powershell
mvn --% test -Dtest=ChatViewModelTest,MatchingViewModelTest,SafetyViewModelTest,ImageCacheTest,LocationServiceTest,LocalGeocodingServiceTest,NominatimGeocodingServiceTest,LocationSelectionDialogTest,ProfileControllerTest,ProfileViewModelTest,OnboardingFlowTest
```

## Documentation maintenance rules

- Keep the instruction hierarchy non-redundant:
  - `copilot-instructions.md` = highest-level always-on rules
  - `CLAUDE.md` = verified repo map and current gotchas
  - `AGENTS.md` = execution workflow and verification discipline
- `GEMINI.md` and `QWEN.md` should roughly mirror each other and serve as merged model-specific guides.
- Update counts, package snapshots, commands, and gotchas only from current source/build output.
- Prefer package-level snapshots over brittle exhaustive class lists when a file map changes quickly.
- If a doc stops matching the code, fix the doc or remove the stale claim.

## Practical defaults for this repo

- Prefer targeted tests during implementation.
- Prefer the full quality gate before final handoff.
- Current verified baseline: `mvn spotless:apply verify` passed on 2026-04-14.
- Treat `.\run_verify.ps1` as the canonical repo-level full local verification path; it runs the Maven quality gate and PostgreSQL smoke together.
- For PostgreSQL runtime changes, prefer the repo-local helpers `start_local_postgres.ps1`, `run_postgresql_smoke.ps1`, and `stop_local_postgres.ps1` over ad-hoc Docker-first validation.
- Use shared test helpers when available:
  - `JavaFxTestSupport`
  - `UiAsyncTestSupport`
  - `RestApiTestFixture`
  - `TestUserFactory`
  - `TestEventBus`
  - `TestAchievementService`

## When in doubt

- Follow `.github/copilot-instructions.md` first.
- Use `CLAUDE.md` for verified repo details.
- Use this file to decide how to execute the work cleanly.
