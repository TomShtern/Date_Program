## Overview

This document describes the current verified CI and local PostgreSQL setup for this repository.

The repository intentionally uses a split CI model:

- **GitHub Actions** provides the fast feedback lane for formatting, compile, and test feedback.
- **CircleCI** provides the full PostgreSQL-backed integration lane.
- **Local PostgreSQL helper scripts** provide a Windows/PowerShell-first developer workflow that mirrors the runtime storage path.

This file is the operational companion to `README.md`, `.circleci/config.yml`, `.github/workflows/verify.yml`, and the PostgreSQL helper scripts in the repo root.

## CircleCI

CircleCI is the authoritative **full integration** CI system for this repository.

### Source of truth

- `.circleci/config.yml`

### Workflow and jobs

Current workflow name:

- `postgresql-integration`

Current job sequence:

1. `postgres-verify`
2. `postgres-runtime-smoke`

`postgres-runtime-smoke` depends on `postgres-verify` succeeding.

### What `postgres-verify` does

The verify job is intentionally split into two phases instead of hiding everything behind one giant `mvn verify` step:

1. **Maven test suite (headless JavaFX)**
   - runs the test suite under `xvfb-run`
   - uses `-Djava.awt.headless=true`
   - uses a bounded `timeout` so the job fails fast if the test JVM wedges
   - avoids the verbose test profile that previously made CI very noisy and slower to diagnose

2. **Maven verify plugins (skip tests)**
   - runs the heavier verification plugins after tests are already green
   - uses `-DskipTests verify`

This split makes failures easier to localize and prevents test failures from being buried inside a large, slower `verify` phase.

### Why the package install step exists

The Linux CI environment needs additional UI/AWT libraries for JavaFX- and image-related tests.

The install step includes:

- `xvfb`, `xauth`
- GTK/X11/AWT dependencies like `libxi6`, `libxrender1`, `libxtst6`, `libxext6`, and related packages
- a portable fallback for the audio package name:
  - first tries `libasound2`
  - falls back to `libasound2t64`

The install step also:

- uses `--no-install-recommends`
- automatically retries on failure

These changes were added because Linux CI failures were traced to missing native UI/AWT libraries and package-name variation on newer Ubuntu images.

### Performance and stability notes

CircleCI currently favors **stability and diagnosability** over maximum parallelism.

Important current choices:

- a plan-compatible Docker resource class is used
- Maven output is kept relatively quiet
- the test phase has a heartbeat and a hard timeout
- surefire reports are stored as artifacts/test results

This keeps the CI easier to debug and avoids the previous failure mode where the job appeared to run forever while the JavaFX test JVM was already in a bad state.

### How to troubleshoot CircleCI

If CircleCI fails again, check in this order:

1. **Package install step**
   - look for Ubuntu package-name drift or apt failures
2. **Maven test suite (headless JavaFX)**
   - JavaFX/AWT native library problems
   - UI-thread timeouts
   - image/desktop-related failures
3. **Maven verify plugins (skip tests)**
   - Spotless, Checkstyle, PMD, JaCoCo, SpotBugs-related issues
4. **PostgreSQL smoke test**
   - DB startup, connection, or runtime storage-path regressions

If the CircleCI job looks slow, the first thing to determine is **which step** is active. The current split is designed specifically to make that obvious.

## GitHub Actions

GitHub Actions is the repository’s **fast feedback** CI system.

### Source of truth

- `.github/workflows/verify.yml`

### Workflow role

Current workflow name:

- `github-fast-feedback`

This workflow is intentionally lighter than CircleCI.

It is meant to answer:

- does the code format correctly?
- does it compile?
- do the tests pass in a GitHub-hosted Linux runner?

It is **not** the full PostgreSQL-backed integration authority. CircleCI owns that heavier lane.

### Current GitHub workflow behavior

The workflow currently:

- runs on `ubuntu-latest`
- uses:
  - `actions/checkout@v5`
  - `actions/setup-java@v5`
- opts into Node 24 now with:
  - `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true`
- sets `permissions: contents: read`
- uses workflow concurrency to cancel superseded in-progress runs on the same ref
- installs the same Linux UI packages/fallbacks needed for JavaFX/AWT tests
- runs the fast Maven lane under `xvfb-run`

### Why the action versions matter

The workflow was updated to the Node 24-compatible major versions because GitHub announced Node 20 deprecation on hosted runners.

Current intent:

- avoid future breakage from Node 20 removal
- keep the workflow aligned with the supported GitHub-hosted runner runtime

### Performance and stability notes

GitHub’s lane is optimized for speed more than exhaustive integration coverage.

Key stabilizers:

- `timeout-minutes: 20`
- concurrency cancellation for superseded runs
- same `xvfb-run` display geometry as CircleCI for consistency
- same Linux UI dependency strategy as CircleCI

### How to troubleshoot GitHub Actions

If the GitHub workflow fails, check:

1. package install step
2. headless JavaFX/AWT/image-related tests
3. Maven test failures in the fast lane
4. action-version/runtime warnings from GitHub-hosted runners

If a GitHub Actions warning mentions Node runtime deprecation, check whether the workflow still uses current action major versions and whether Node 24 opt-in is still present or no longer needed.

## PostgreSQL

The repository is local-first for PostgreSQL development and verification.

### Core local PostgreSQL files

- `config/app-config.postgresql.local.json`
- `.env.example`
- `start_local_postgres.ps1`
- `stop_local_postgres.ps1`
- `run_postgresql_smoke.ps1`
- `run_verify.ps1`

### Current local defaults

Default local PostgreSQL settings are:

- port: `55432`
- database: `datingapp`
- username: `datingapp`
- password: `datingapp`

The helper scripts and `.env.example` are aligned to these defaults.

### `start_local_postgres.ps1`

This script:

- initializes the local PostgreSQL data directory if needed
- starts PostgreSQL with a localhost-only listener
- waits for readiness with `pg_isready`
- creates the target database if it does not yet exist

It accepts parameters for:

- `Port`
- `BaseDir`
- `Superuser`
- `Credential`
- `Database`
- `StartupTimeoutSeconds`

### `stop_local_postgres.ps1`

This script:

- checks whether the repo-local PostgreSQL instance exists and is running
- stops it with `pg_ctl -m fast`
- exits cleanly if nothing is running

### `run_postgresql_smoke.ps1`

This script is the targeted PostgreSQL smoke runner.

It now:

- starts local PostgreSQL first
- supports `StartupTimeoutSeconds`
- sets the PostgreSQL runtime environment variables for the Maven smoke process
- restores or clears those environment variables safely afterward
- runs Maven in batch mode with color disabled for cleaner logs

This script is the right choice when you want a focused PostgreSQL runtime verification without running the entire local verification flow.

### `run_verify.ps1`

This is the repo-level local verification wrapper.

It now runs in this order:

1. start local PostgreSQL
2. run Maven quality gate (`spotless:apply verify`)
3. run PostgreSQL smoke verification
4. stop local PostgreSQL in a `finally` block

This ordering matters because PostgreSQL-backed bootstrap tests can run during the Maven quality gate if local config or environment points the app at PostgreSQL.

The script also prints simple elapsed-time summaries for:

- the Maven quality gate
- PostgreSQL smoke verification

### Environment/config notes

Important files/variables:

- `config/app-config.postgresql.local.json` provides the local tracked PostgreSQL config surface
- `.env.example` documents the local env override contract
- runtime password stays out of tracked JSON

Use the following environment variables when needed:

- `DATING_APP_DB_DIALECT`
- `DATING_APP_DB_URL`
- `DATING_APP_DB_USERNAME`
- `DATING_APP_DB_PASSWORD`

### PostgreSQL troubleshooting

If local PostgreSQL verification fails, check:

1. whether `pg_ctl`, `pg_isready`, `psql`, and `createdb` are available on `PATH`
2. whether port `55432` is already in use
3. whether the repo-local data directory exists and is writable
4. whether `.env` or OS env vars override the expected local PostgreSQL settings

### Practical division of responsibility

Current intended responsibility split is:

- **CircleCI**: full PostgreSQL-backed CI integration path
- **GitHub Actions**: fast feedback path
- **Local PowerShell scripts**: canonical developer verification path on Windows

That means future CI or runtime work should preserve this separation unless there is a strong reason to change it.

## Recommended usage summary

Use these paths depending on the goal:

- quick hosted feedback: **GitHub Actions**
- full hosted PostgreSQL integration: **CircleCI**
- full local proof: **`run_verify.ps1`**
- focused local PostgreSQL runtime proof: **`run_postgresql_smoke.ps1`**

## Notes for future maintenance

- Keep GitHub Actions lightweight.
- Keep CircleCI as the PostgreSQL-heavy lane unless there is a deliberate architectural reason to merge responsibilities.
- Avoid reintroducing verbose test logging profiles into CI unless actively debugging a CI-only failure.
- If Linux image packages change again, update the portable package install logic rather than hardcoding one Ubuntu-specific package name.