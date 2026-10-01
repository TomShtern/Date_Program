# AGENTS.md

> **Updated:** 2026-09-27
> **Role in the instruction stack:** lowest-level workflow guide for agents working in this repo.
> **Hierarchy:** `.github/copilot-instructions.md` → `CLAUDE.md` → `AGENTS.md`.

This file is intentionally not a second copy of the repo map in `CLAUDE.md` and `.claude/rules/`.
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
5. Prefer the simplest complete fix, not the smallest diff. Refactor when needed to fully correct the contract, behavior, and all affected code paths, including callers, dependencies, tests, and related design.
6. Do not dismiss compilation errors as “pre-existing” just because they appear outside your edited files. Treat every compile failure as relevant and fix it, even if it is unrelated to your current change.
 After fixing unrelated/pre-existing errors, clearly report them separately: what failed, where it was, what you changed, and that it was outside the original task scope.
7. Do NOT suppress issues, fix them properly from the root cause, and do not leave known issues unfixed in the codebase. If you find a problem that is outside the scope of the current task, fix it properly anyway.

## Search and tool discipline

- For `.java`, prefer symbol-aware/LSP navigation first, then `ast-grep`, then plain-text search when needed.
- Use read-only subagents for focused codebase exploration; if one helper path is unavailable, switch tools instead of retrying the same failure mode.
- Use execution-oriented helpers for Maven/test runs rather than manually chaining shell commands in an interactive terminal.
- Prefer symbol-aware rename/usages tools when changing names across files.
- On Windows PowerShell, use `mvn --% ...` when Maven arguments contain commas or special characters that PowerShell might parse.
- For local PostgreSQL setup/debugging, run `.\scripts/check_postgresql_runtime_env.ps1` before deeper app-level diagnosis; it validates CLI availability, effective env/`.env` settings, reachability, and login.
- Treat `.\scripts/start_local_postgres.ps1` as more than a bare startup helper: it is the repo-owned local bootstrap path for PostgreSQL observability (`pg_stat_statements`, `compute_query_id`) and the local `datingapp` role defaults in the target database.
- For PostgreSQL runtime work, prefer an already-running local PostgreSQL instance first; use Docker only as a disposable fallback when no local server is available.
- For phone-alpha backend LAN testing, prefer `.\scripts/start_phone_alpha_backend.ps1` over manual classpath/JVM startup; it runs PostgreSQL preflight (starting local PostgreSQL if needed), auto-compiles stale classes, builds the runtime classpath, verifies `/api/health` on localhost and LAN, and prints the Flutter `dart-define` values.

## Editing discipline

- Preserve layer boundaries:
  - `core/` stays framework-free
  - `app/usecase/*` stays the app boundary
  - `ui/viewmodel/*` should go through `BaseViewModel`, `ui/async/*`, and `UiDataAdapters`
- Do not reintroduce removed legacy names such as `AppBootstrap`, `HandlerFactory`, `Toast`, `UiSupport`, or `ui/controller` references.
- Prefer the canonical helpers already in the repo:
  - `AppClock.now()` / `AppClock.today()` / `AppClock.clock()` depending on whether the code consumes instants, dates, or an injected `Clock`
  - `Match.generateId(...)` (`core.model`) / `Conversation.generateId(...)` (`core.connection.ConnectionModels`)
  - `User.copy()`
  - `EnumSetUtil.safeCopy(...)`
- For ViewModel actions that can hit storage or network, keep the work inside `ViewModelAsyncScope`; do not invoke use cases synchronously on the FX thread.
- For user-visible controller image loads, prefer `ImageCache.getImageAsync(...)`; keep synchronous `getImage(...)` for preload or non-UI paths.
- For location UX changes, keep `LocationService`, `GeocodingService` implementations (`Local`/`Nominatim` behind `FallbackGeocodingService` in `ServiceRegistry`), `LocationSelectionDialog`, `ProfileViewModel`, and `ViewModelFactory` aligned.
- Treat direct `new XxxViewModel(...)` calls outside `ViewModelFactory` as compatibility/test shims, not the production composition path (`ViewModelFactory` is the JavaFX composition root; controllers take ViewModels from it).

## Verification discipline

Run verification in this order unless the task clearly needs a different sequence:

1. Check touched files for errors.
2. Run focused tests for the changed area.
3. Run a broader smoke suite if multiple subsystems changed.
4. If the work touches PowerShell helpers under `scripts/`, run the matching script test(s) under `src/test/powershell` before broader verification.
5. If the work touches PostgreSQL runtime support, use `.\scripts/check_postgresql_runtime_env.ps1` for environment/connectivity triage and run the local smoke path (`.\scripts/run_postgresql_smoke.ps1`, which drives property-driven `PostgresqlRuntimeSmokeTest`) during targeted validation.
6. Run the full Maven quality gate before claiming completion:

```powershell
mvn spotless:apply verify
```

7. Run the repo-level full local verification path when the change is substantial or affects runtime/verification seams:

```powershell
.\scripts/run_verify.ps1
```

Use targeted Maven test selection for faster iteration, e.g.:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,MatchingUseCasesTest test
```

For async/location/UI changes, the highest-signal targeted slices are the directly relevant classes among:

```powershell
mvn --% test -Dtest=ChatViewModelTest,MatchingViewModelTest,SafetyViewModelTest,ImageCacheTest,LocationServiceTest,LocalGeocodingServiceTest,NominatimGeocodingServiceTest,LocationSelectionDialogTest,ProfileControllerTest,ProfileViewModelTest,OnboardingFlowTest
```

For REST API / backend changes, the highest-signal targeted slices are:

```powershell
mvn --% test -Dtest=AuthUseCasesTest,RestApiAuthRoutesTest,RestApiPhotoRoutesTest,RestApiRequestGuardsTest,RestApiIdentityPolicyTest,RestApiRateLimitTest,RestApiDailyLimitTest,RestApiTestFixtureTest
```

For PowerShell helper changes, start with the nearest script test, e.g.:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\src\test\powershell\StartLocalPostgresScriptTest.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File .\src\test\powershell\RunPostgresqlSmokeScriptTest.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File .\src\test\powershell\RunVerifyScriptTest.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File .\src\test\powershell\CheckPostgresqlRuntimeEnvScriptTest.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File .\src\test\powershell\StopLocalPostgresScriptTest.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File .\src\test\powershell\ResetLocalPostgresScriptTest.ps1
```

## Documentation maintenance rules

- Keep the instruction hierarchy non-redundant:
  - `copilot-instructions.md` = highest-level always-on rules
  - `CLAUDE.md` = verified repo map and current gotchas
  - `AGENTS.md` = execution workflow and verification discipline
- Update counts, package snapshots, commands, and gotchas only from current source/build output.
- Prefer package-level snapshots over brittle exhaustive class lists when a file map changes quickly.
- If a doc stops matching the code, fix the doc or remove the stale claim.

## Practical defaults for this repo

- Prefer targeted tests during implementation.
- Prefer the full quality gate before final handoff.
- Treat `.\scripts/check_postgresql_runtime_env.ps1` as the first local PostgreSQL preflight when PATH, `.env`, or login state may be the problem.
- Treat `.\scripts/run_verify.ps1` as the canonical repo-level full local verification path; it runs the Maven quality gate and PostgreSQL smoke together.
- For the phone-alpha backend REST API, prefer `.\scripts/start_phone_alpha_backend.ps1` as the one-command LAN startup path; it runs PostgreSQL preflight, starts local PostgreSQL if needed, verifies `/api/health`, and prints the Flutter `dart-define` values.
- For PostgreSQL runtime changes, prefer the repo-local helpers `scripts/start_local_postgres.ps1`, `scripts/reset_local_postgres.ps1`, `scripts/run_postgresql_smoke.ps1`, and `scripts/stop_local_postgres.ps1` over ad-hoc Docker-first validation. (Docker appears only in `.circleci/config.yml` for CI Postgres; `.env.example` names local PostgreSQL the default and Docker an optional fallback.)
- For local PostgreSQL resets where you may need to inspect or reuse the prior state, `.\scripts/reset_local_postgres.ps1` preserves the newest `reset_backup_*` schema by default (`-RetainedAutoBackupSchemas 1`); use `-RetainedAutoBackupSchemas <n>` when you intentionally want to keep more than one auto backup.
- If you need a current PostgreSQL schema snapshot for review or backup before runtime work, run `.\scripts/export_local_postgresql_schema.ps1`; it exports the `public` schema via `pg_dump` against the resolved local PostgreSQL target.
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
