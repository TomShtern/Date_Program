# PostgreSQL Next Steps

This file captures the recommended follow-up work after the PostgreSQL runtime move and the Windows PowerShell startup fix.

## Current Assumptions

These recommendations assume all of the following are true:

- the main development platform is Windows 11
- PowerShell is the primary script/runtime shell
- CircleCI is the CI system
- Docker is intentionally postponed for now
- local PostgreSQL is the preferred runtime path for the current stage of the project

Those assumptions matter. They change the order and shape of the next work.

## Current Status

The local PostgreSQL runtime path is working end to end on Windows based on recent manual verification:

- `.\start_local_postgres.ps1` starts cleanly
- `.\run_postgresql_smoke.ps1` passes
- `.\run_verify.ps1` passes
- the PowerShell popup/hang issue is gone

The main remaining work is hardening, coverage, onboarding, and maintenance discipline.

## What Changed Because Of CircleCI And No-Docker-For-Now

- The old “Linux CI + PostgreSQL service container” recommendation is no longer the default recommendation for this stage.
- The immediate CI focus should be Windows-aligned validation that does not require Docker.
- Real local PostgreSQL remains the primary truth for now.
- If CI needs live PostgreSQL before Docker is adopted, the best fit is a Windows-based machine image or a self-hosted runner with PostgreSQL installed, not a forced early Docker setup.
- Containerized PostgreSQL smoke is still a valid future option, but it is no longer the next recommendation.

## Recommendations

1. Add a CircleCI Windows job for the PowerShell PostgreSQL script tests.

Why: the bug you just hit was Windows-specific. That means the highest-value CI coverage right now is Windows process-launch and PowerShell behavior, not cross-platform container orchestration.

Suggested action: add a CircleCI Windows job that runs:
`src/test/powershell/StartLocalPostgresScriptTest.ps1`,
`src/test/powershell/RunPostgresqlSmokeScriptTest.ps1`,
`src/test/powershell/RunVerifyScriptTest.ps1`,
and `src/test/powershell/StopLocalPostgresScriptTest.ps1`.

2. Keep the first CI milestone Docker-free.

Why: you explicitly postponed Docker because it adds overhead right now. That is a reasonable choice at this stage, and the next-steps document should reflect that rather than trying to reintroduce Docker through CI by stealth.

Suggested action: treat CircleCI job setup, Windows script validation, and local database workflow hardening as the current milestone. Leave Docker-backed CI database orchestration for a later milestone.

3. If you want live PostgreSQL in CircleCI before Docker, use a Windows machine/self-hosted path.

Why: if the project reaches the point where CI must hit a real PostgreSQL instance before Docker is adopted, the cleanest match to your current workflow is a Windows environment with PostgreSQL installed, not an early Linux container job that pulls the team away from the real platform.

Suggested action: if needed later, evaluate one of:
- a CircleCI Windows machine image with PostgreSQL installed during the job
- a self-hosted Windows runner with local PostgreSQL available
- a dedicated internal CI machine that mirrors the local Windows setup

4. Add an explicit regression note or test for the `AppConfig.Builder` H2 default versus the runtime PostgreSQL config file.

Why: this split is intentional, but it is easy for someone to “clean it up” incorrectly. The builder still defaults to H2 for compatibility/test boundaries while runtime resolves to PostgreSQL through bootstrap config.

Suggested action: add a focused config-loading test or a short clarifying comment near bootstrap/config code so this distinction is preserved.

5. Add a PostgreSQL binary discovery or preflight helper.

Why: local failures can still come from `pg_ctl`, `pg_isready`, `psql`, or `createdb` not being reachable on PATH, especially on Windows.

Suggested action: either add a helper script or extend `start_local_postgres.ps1` to fail fast with a clear message naming the missing binaries.

6. Add a one-command local environment check script.

Why: local database first only stays simple if setup failures are obvious. A preflight script can confirm PowerShell, Java, Maven, PostgreSQL binaries, and the expected port before someone burns time debugging.

Suggested action: add a script such as `check_postgresql_runtime_env.ps1` that reports pass/fail for the required local dependencies.

7. Link the PostgreSQL/PowerShell guide from the main README and any onboarding entrypoints.

Why: the new guide only helps if people can find it without digging through the repo root manually.

Suggested action: add a short “PostgreSQL runtime notes” link from `README.md`, and optionally from `CLAUDE.md` or `AGENTS.md`.

8. Add a cleanup convention for PostgreSQL runtime logs.

Why: local-first debugging is great, but it creates real local artifacts. Without a convention, people will eventually read stale logs and chase the wrong issue.

Suggested action: decide whether `data/local-postgresql/*.log` files should be retained, rotated, truncated, or cleaned by a helper script.

9. Keep treating `.\run_verify.ps1` as the primary local proof path.

Why: `mvn spotless:apply verify` proves the Maven/code-quality side, not the PostgreSQL runtime side. If local PostgreSQL is the real runtime path, the local proof command must include that runtime path.

Suggested action: keep docs and habits aligned around `.\run_verify.ps1` as the canonical local “everything still works together” command.

10. Preserve H2 compatibility/test paths intentionally.

Why: runtime now defaults to PostgreSQL, but not every test or compatibility seam should be migrated immediately. H2 still has value for speed, isolation, and compatibility boundaries.

Suggested action: keep `buildH2(...)` and `buildInMemory(...)` in place until there is a deliberate testing-strategy change, not an accidental drift.

11. Focus the next round of work on local-first automation and onboarding, not more startup-script surgery.

Why: the startup path is now functioning. The next likely failures are setup drift, missing tools, and regression detection gaps.

Suggested action: prioritize preflight, docs, CI script coverage, and better diagnostics before reopening the launcher code.

12. Add a start-stop-start idempotence test for the local PostgreSQL helpers.

Why: scripts often work in a single happy path but fail when repeated or partially interrupted.

Suggested action: add a PowerShell test flow that exercises `start`, `stop`, and `start` again and confirms each step behaves predictably.

13. Add a missing-binaries test case for the helper scripts.

Why: PATH issues are common on Windows and should fail with useful diagnostics instead of raw command-not-found noise.

Suggested action: create a PowerShell sandbox where PostgreSQL binaries are intentionally unavailable and verify the script reports an actionable error.

14. Improve startup-failure diagnostics when readiness polling times out.

Why: the current errors are acceptable, but future debugging will be faster if the failure message points directly at the right files and evidence.

Suggested action: include the relevant log file path and possibly the tail of `postgres.log` when PostgreSQL never becomes ready.

15. Support an explicit PostgreSQL binary directory override.

Why: some Windows setups install PostgreSQL in custom locations or have multiple PostgreSQL versions side by side.

Suggested action: add a `-PostgresBinDir` parameter or environment override that prepends the desired bin directory during the helper-script run.

16. Support easy customization of port, base directory, and database name for isolated local runs.

Why: local-first workflows become much easier when multiple worktrees or branches can coexist without fighting over one cluster and one port.

Suggested action: keep the current parameterized script shape and slightly improve documentation around `-Port`, `-BaseDir`, and `-Database`.

17. Persist relevant artifacts in CircleCI even before Docker is adopted.

Why: CI failures are still much easier to diagnose if logs and test outputs are saved, even when the job is only running script tests.

Suggested action: archive PowerShell test output, startup stderr/stdout, and any generated PostgreSQL logs as CircleCI artifacts on failure.

18. Add a fresh-cluster migration smoke path for local/manual validation first.

Why: a reused cluster proves one kind of stability, but a fresh data directory catches initialization and migration regressions earlier.

Suggested action: create a targeted local validation path that initializes a clean cluster in a temp directory and proves bootstrap/schema setup still works. Treat it as a local/manual seam first, then consider CI later.

19. Document the safe “reset local PostgreSQL state” flow.

Why: eventually someone will need to recover from a broken local cluster, corrupted state, or confusing leftover data.

Suggested action: add a short section either to the guide or a helper script describing how to stop PostgreSQL, remove the local data directory, and reinitialize safely.

20. Add a regression test that proves smoke-script environment overrides are restored.

Why: `run_postgresql_smoke.ps1` temporarily sets PostgreSQL runtime environment variables, and environment leakage can poison later commands or tests.

Suggested action: extend the smoke-script PowerShell test to assert that preexisting env vars are restored after the script exits.

21. Add one more targeted PostgreSQL-backed storage test beyond the broad smoke path.

Why: the current smoke test gives valuable wide coverage, but one or two focused PostgreSQL-backed tests in critical seams would improve confidence without replacing H2 coverage.

Suggested action: choose one high-value JDBI seam such as matching, analytics, or migration-sensitive persistence and add a focused PostgreSQL-backed test for it.

22. Add a lightweight CircleCI “local workflow fidelity” checklist to the repo docs.

Why: since local Windows + PowerShell + local PostgreSQL is the real workflow, CI should be described as supporting that workflow, not redefining it.

Suggested action: document which validations are expected locally, which are expected in CircleCI, and which are intentionally deferred until Docker adoption.

## Suggested Priority Order

If you want a practical execution order for the current stage, this is the order I would recommend:

1. CircleCI Windows job for PowerShell script tests
2. Binary preflight / environment check script
3. Missing-binaries and env-restoration script tests
4. README link to the PostgreSQL/PowerShell guide
5. Log/artifact retention policy for local and CircleCI failures
6. Fresh-cluster local migration smoke
7. One extra targeted PostgreSQL-backed storage test
8. Revisit CircleCI live PostgreSQL strategy only when local-first flow is stable and you actually need it

## Suggested Non-Goals For Now

- Do not introduce Docker just to make CI look more “standard”.
- Do not replace the local Windows PostgreSQL workflow with a CI-first workflow.
- Do not rewrite the PostgreSQL helper scripts again without a failing reproduction.
- Do not remove H2 compatibility paths just because runtime now defaults to PostgreSQL.
- Do not broaden PostgreSQL work into unrelated persistence refactors unless a concrete bug or requirement justifies it.

## My Current Opinion

The local-database-first decision is reasonable for the current stage of this project.

It changes the recommendation in a few important ways:

- Windows fidelity matters more than generic cross-platform container convenience.
- CircleCI should support the local workflow first, not compete with it.
- Docker-backed database CI is still a valid future step, but it is no longer the default immediate next step.

The one thing I would emphasize is this:

If you postpone Docker, then you should be more deliberate about local environment checks, script test coverage, and doc clarity. Those become the substitute discipline that keeps the local-first approach from turning into “works on one machine.”
