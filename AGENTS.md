# AGENTS.md

> **Updated:** 2026-04-05
> **Role in the instruction stack:** lowest-level workflow guide for agents working in this repo.
> **Hierarchy:** `.github/copilot-instructions.md` → `CLAUDE.md` → `AGENTS.md`.

This file is intentionally **not** a second copy of the architecture snapshot in `CLAUDE.md`.
Use it for execution discipline, tool choice, validation order, and doc-maintenance rules.

## Source of truth

If any markdown guidance and the code disagree, trust:

- `src/main/java`
- `src/test/java`
- `pom.xml`

## Working mode for agents

1. Start from current code and current build config, not historical docs.
2. For multi-step work, keep an explicit todo list with exactly one step in progress.
3. Read the relevant files fully before editing them.
4. Prefer one coordinator for shared files and only parallelize independent work.
5. Keep diffs small, contract-driven, and easy to verify.

## Search and tool discipline

- Prefer syntax-aware search (`ast-grep`) when code structure matters.
- Use read-only subagents for focused codebase exploration.
- Use execution-oriented helpers for long Maven/test runs rather than manually chaining shell commands.
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
- Treat any remaining convenience constructors outside `ViewModelFactory` as compatibility/test shims, not the production composition path.

## Verification discipline

Run verification in this order unless the task clearly needs a different sequence:

1. Check touched files for errors.
2. Run focused tests for the changed area.
3. Run a broader smoke suite if multiple subsystems changed.
4. If the work touches PostgreSQL runtime support, run the local smoke path (`.\run_postgresql_smoke.ps1` or equivalent property-driven `PostgresqlRuntimeSmokeTest`) before final completion claims.
5. Run the full quality gate before claiming completion:

```powershell
mvn spotless:apply verify
```

Use targeted Maven test selection for faster iteration, e.g.:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ProfileUseCasesTest,MatchingUseCasesTest test
```

## Documentation maintenance rules

- Keep the instruction hierarchy non-redundant:
  - `copilot-instructions.md` = highest-level always-on rules
  - `CLAUDE.md` = verified repo map and current gotchas
  - `AGENTS.md` = execution workflow and verification discipline
- `GEMINI.md` and `QWEN.md` should roughly mirror each other and serve as merged model-specific guides.
- Update counts, package snapshots, commands, and gotchas only from current source/build output.
- If a doc stops matching the code, fix the doc or remove the stale claim.

## Practical defaults for this repo

- Prefer targeted tests during implementation.
- Prefer the full quality gate before final handoff.
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
- Use this file to decide **how** to execute the work cleanly.
