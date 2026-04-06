# PostgreSQL and PowerShell Guide

This file records the practical rules for PostgreSQL runtime work in this repository on Windows.

## What Is Canonical

- Runtime storage is PostgreSQL by default through [`config/app-config.json`](./config/app-config.json).
- The production/runtime composition path is `StorageFactory.buildSqlDatabase(...)`.
- `buildH2(...)` and `buildInMemory(...)` are still valid compatibility and test paths. They are not the main runtime path.
- The canonical full local verification command is `.\run_verify.ps1`.
- The canonical local PostgreSQL helper scripts are:
  - `.\start_local_postgres.ps1`
  - `.\run_postgresql_smoke.ps1`
  - `.\stop_local_postgres.ps1`

## Daily Commands

```powershell
# Start or reuse the local PostgreSQL instance
.\start_local_postgres.ps1

# Run only the PostgreSQL runtime smoke path
.\run_postgresql_smoke.ps1

# Run the full local gate: Maven quality gate + PostgreSQL smoke + cleanup
.\run_verify.ps1

# Stop the local PostgreSQL instance
.\stop_local_postgres.ps1
```

## PowerShell and Windows Rules

- Prefer PowerShell-friendly commands and script entrypoints in this repo.
- When Maven test selection includes commas, prefer `mvn --% ...` so PowerShell does not reinterpret arguments.
- On Windows, do not assume `pg_ctl -w start` is a safe success boundary when launched through PowerShell wrappers.
- Treat PostgreSQL readiness as the real success condition. In this repo, `start_local_postgres.ps1` polls `pg_isready`.
- Keep the `pg_ctl -o` server options payload as one quoted argument. Splitting it breaks startup on Windows because `pg_ctl` can misread `-h` as its own switch.
- If you need hidden child script execution during tests, prefer `Start-Process ... -WindowStyle Hidden` so no blank PowerShell window appears.

## Credentials and Config

- The local helper scripts default to:
  - username: `datingapp`
  - password: `datingapp`
  - database: `datingapp`
  - port: `55432`
- Runtime password can be supplied through:
  - `.env`
  - OS env var `DATING_APP_DB_PASSWORD`
  - JVM property `-Ddatingapp.db.password=...`
- Local PostgreSQL defaults are already represented in:
  - [`config/app-config.json`](./config/app-config.json)
  - [`config/app-config.postgresql.local.json`](./config/app-config.postgresql.local.json)
  - [`.env.example`](./.env.example)

## Important Nuance

- `AppConfig.Builder` still defaults to H2.
- That is intentional for compatibility and test boundaries.
- The normal runtime path still resolves to PostgreSQL because bootstrap loads the repo config file.
- Do not "fix" this by blindly changing every H2 default to PostgreSQL without checking the H2-backed compatibility tests.

## What To Verify

- For PowerShell script changes:
  - `.\src\test\powershell\StartLocalPostgresScriptTest.ps1`
  - `.\src\test\powershell\RunPostgresqlSmokeScriptTest.ps1`
  - `.\src\test\powershell\RunVerifyScriptTest.ps1`
  - `.\src\test\powershell\StopLocalPostgresScriptTest.ps1`
- For PostgreSQL runtime changes:
  - `.\run_postgresql_smoke.ps1`
  - `.\run_verify.ps1`
- For full repo verification after substantial changes:
  - `mvn spotless:apply verify`
  - `.\run_verify.ps1`

## Do

- Use the local PostgreSQL instance first.
- Reuse the repo helper scripts instead of ad-hoc `pg_ctl` commands.
- Keep logs and stderr/stdout redirection when launching `pg_ctl` from PowerShell.
- Restore environment variables after PostgreSQL smoke runs.
- Keep PowerShell script tests as the regression seam for Windows process-launch behavior.

## Avoid

- Do not reintroduce direct blocking waits on `pg_ctl` as the only success signal.
- Do not split the `-o "-p ... -h localhost"` payload into separate PowerShell arguments.
- Do not assume a passing Maven-only gate proves the PostgreSQL runtime path.
- Do not treat Docker as the first choice here; it is only a fallback when no local PostgreSQL instance is available.
- Do not remove H2 compatibility paths just because runtime now uses PostgreSQL.

## Troubleshooting

- If `start_local_postgres.ps1` hangs, inspect:
  - `data/local-postgresql/postgres.log`
  - `data/local-postgresql/pg_ctl-start.stderr.log`
  - `data/local-postgresql/pg_ctl-start.stdout.log`
- If smoke fails but direct startup works, check the Maven properties passed by `run_postgresql_smoke.ps1`.
- If the full verify path fails after Maven succeeds, the failure is usually in the PostgreSQL smoke path or cleanup path, not the quality gate itself.
- If a PowerShell test opens a blank extra window again, inspect any new `Start-Process` usage first.

## Recommended Mental Model

- `mvn spotless:apply verify` proves the Maven/code-quality side.
- `PostgresqlRuntimeSmokeTest` proves the storage/runtime side.
- `run_verify.ps1` is the combined local proof that both sides still work together on Windows.
