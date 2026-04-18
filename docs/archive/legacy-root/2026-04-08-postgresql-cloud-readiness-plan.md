# PostgreSQL Cloud Readiness Plan (Planning Only)

> Date: 2026-04-08
> Scope: planning and analysis only — no implementation changes were made.
> Goal: prepare this repository for a later move from local PostgreSQL to a remote/cloud managed PostgreSQL service without breaking today’s local-first workflow.

## Executive summary

This repository is in a better place than many projects before a cloud move because PostgreSQL is already the real runtime path, the connection details are externalized, migrations are centralized, and there is already a live PostgreSQL smoke test.

The main cloud-readiness gaps are not foundational schema problems. They are mostly runtime-operational concerns:

1. no explicit **local vs cloud** profile contract yet
2. hard-coded Hikari pool behavior in `DatabaseManager`
3. no first-class SSL/TLS / CA-cert configuration surface
4. `statement_timeout` is applied with `SET` on every connection
5. migrations run automatically during normal application startup
6. verification and docs are heavily optimized for local Windows + PowerShell, not for remote managed PostgreSQL

That means the right next move later is **not** “switch the URL and hope.” The right move is to harden the configuration contract first, then separate local/direct, cloud/direct, and cloud/pooled connection modes deliberately.

## Changes worth planning now so cloud or Docker stays easy later

Yes — but only a **small set of future-friendly changes** is worth preparing now.

The key rule is:

> **Do not create a separate application storage model for Docker.**
> A Dockerized PostgreSQL instance should fit the same **direct PostgreSQL runtime contract** as a locally installed PostgreSQL server or a direct managed-cloud PostgreSQL endpoint.

So the future-ready distinction the app should care about is mainly:

- **direct PostgreSQL**
	- local installed PostgreSQL
	- Dockerized PostgreSQL
	- cloud direct/admin endpoint
- **pooled PostgreSQL**
	- provider or proxy pooler endpoint for normal app traffic when needed later

That leads to a few low-cost changes that are worth carrying into future planning now.

### 1. Stabilize the direct-vs-pooled contract early

Before cloud adoption becomes urgent, the repo should stop thinking in terms of only “local vs cloud.”

The more durable distinction is:

- **direct** connections can safely own session behavior like `SET TIME ZONE`, migration work, and admin tasks
- **pooled** connections may need reduced session assumptions and stricter startup discipline

**Why this is worth preparing now:**

- Docker fits cleanly into the direct model
- local installed PostgreSQL also fits the direct model
- future cloud direct/admin access fits the same model
- only provider poolers need materially different runtime handling

### 2. Add a second smoke/verification path for an already-running PostgreSQL target

Current verification is strong for the repo-local local-first path, but `run_postgresql_smoke.ps1` still assumes it should start a local PostgreSQL instance itself.

That is ideal for current local development, but later the repo should also support a second mode:

- **local-managed mode** — current behavior: start/prepare local PostgreSQL, then run the smoke
- **external-target mode** — assume PostgreSQL is already running somewhere and only run the smoke against the provided JDBC URL/credentials

This single change would make the same smoke seam reusable against:

- local installed PostgreSQL
- a Docker container you started separately
- a direct cloud PostgreSQL endpoint

**Why this is worth preparing now:** it avoids baking “start local PostgreSQL first” into every future verification story.

### 3. Expand the storage config contract only where it has immediate local value too

Not every cloud setting is worth adding now. A few are.

The best candidates are:

- optional **application name**
- optional **SSL mode**
- config-driven **pool settings** (`maxPoolSize`, `minIdle`, `connectionTimeout`, `validationTimeout`, and later `maxLifetime` / `keepalive`)
- optional **migration mode** or startup policy flag

These fields are worth planning because they are not purely “cloud-only”:

- `applicationName` is useful locally in PostgreSQL diagnostics too
- pool tuning can help both local and future deployments
- `sslMode` can remain optional locally but makes the config surface future-stable
- migration mode clarifies behavior even before multiple environments exist

### 4. Keep tracked config files local-first, but add future-shaped templates

Current tracked config and `.env.example` are intentionally local-first. That is still correct.

What should change later is not the local default, but the existence of clear templates such as:

- local direct PostgreSQL
- Docker direct PostgreSQL
- cloud direct PostgreSQL
- cloud pooled PostgreSQL

These can stay as examples/templates rather than production secrets.

**Why this is worth preparing now:** it prevents the later jump from “localhost only” straight into provider-specific ad-hoc configuration.

### 5. Decide the migration policy before cloud or Docker becomes critical

The repo should eventually make this explicit:

- direct local/dev targets may still auto-migrate on startup
- Dockerized shared/test targets may or may not auto-migrate, depending on workflow
- cloud app startup should usually not be the thing that performs migrations
- cloud direct/admin targets should own migration execution

**Why this is worth preparing now:** it is easier to standardize before multiple deployment shapes exist than after.

## Current repo state

### What is already cloud-friendly

- **Runtime storage already targets PostgreSQL by default** through `ApplicationStartup` + `StorageFactory.buildSqlDatabase(...)`.
- **`AppConfig.StorageConfig` already exists** and defines the main storage contract: dialect, JDBC URL, username, and query timeout.
- **Environment/property override flow already exists** through `ApplicationStartup` and `RuntimeEnvironment`.
- **Project `.env` fallback already exists** through `RuntimeEnvironment`, which is also a useful fit for future Docker/env-file workflows.
- **Password is already externalized** rather than stored in tracked config JSON.
- **Schema evolution is centralized** in append-only `MigrationRunner`.
- **There is already a live PostgreSQL proof path** through `PostgresqlRuntimeSmokeTest` and `run_postgresql_smoke.ps1`.

### What is still strongly local-only

- `config/app-config.json` and `config/app-config.postgresql.local.json` point at `localhost:55432`.
- `.env.example` is local-first and does not model cloud-specific concerns like TLS mode or CA trust.
- `DatabaseManager` hard-codes pool settings and assumes a straightforward direct JDBC connection.
- `DatabaseManager` applies `SET statement_timeout` after opening each connection.
- `DatabaseManager.initializeSchema()` runs migrations as part of normal startup.
- Operational scripts and docs assume a Windows machine with PostgreSQL CLI tools on `PATH`.

## Key future decision points

These decisions should be made *before* implementation work begins.

### 1. Runtime target strategy

The repo needs an explicit answer to this question:

> Will the runtime distinguish between **direct** PostgreSQL targets and **pooled** PostgreSQL targets, with local-installed, Dockerized, and cloud-direct instances all fitting the direct contract?

My recommendation is:

- `local-direct` — current direct local PostgreSQL path
- `docker-direct` — Dockerized PostgreSQL, but still the same direct runtime contract
- `cloud-direct` — direct managed PostgreSQL connection for migrations/admin/smoke work
- `cloud-pooled` — app traffic path when a provider pooler or external pool is used

The important design point is that `docker-direct` should not require a separate storage architecture — just a different endpoint/startup workflow.

That separation matters because some managed services treat direct and pooled connections very differently.

### 2. Provider shape

Before implementation, decide whether the expected first cloud target is more like:

- **managed PostgreSQL with direct TCP + TLS** (for example AWS RDS or Azure Flexible Server), or
- **managed PostgreSQL with provider pooler in front** (for example Neon or Supabase poolers).

This matters because transaction-mode poolers can change what is safe at the session level.

### 3. Migration ownership

The app currently runs migrations during startup. That is convenient locally, but in a cloud deployment with multiple instances it can become noisy or risky.

A future cloud plan should answer:

- should migrations run in a dedicated job/command before app startup?
- should one instance be allowed to migrate and others wait?
- should migrations always use the direct connection profile rather than the pooled one?

## Major readiness gaps and recommendations

### 1. Configuration contract is too small for cloud needs

Current storage config includes:

- `databaseDialect`
- `databaseUrl`
- `databaseUsername`
- `queryTimeoutSeconds`

That is a good start, but cloud PostgreSQL usually needs more than that.

#### Planned additions to think through later

- runtime target kind (`direct` vs `pooled`) or an equivalent profile model
- connection/profile name (`local`, `cloud-direct`, `cloud-pooled`)
- SSL/TLS mode
- optional application name
- optional root CA certificate path
- pool size settings
- connection timeout / validation timeout
- max lifetime / keepalive / idle timeout
- perhaps a separate migration/admin JDBC URL

Docker does **not** need special storage semantics here; it only needs to fit the same direct-target contract cleanly.

One important nuance: the existing `DATING_APP_DB_PROFILE` / `datingapp.db.profile` path already has legacy H2-oriented meaning inside `DatabaseManager` (`test` / `dev` file-database behavior and password fallback rules).

That means a future profile model should be introduced deliberately:

- either replace that legacy profile meaning cleanly
- or introduce a separate runtime-target concept without silently overloading the old profile flag

Do **not** assume the current `DB_PROFILE` name is already a safe drop-in abstraction for `local-direct`, `docker-direct`, or `cloud-pooled`.

### 2. Pooling is hard-coded today

`DatabaseManager` currently hard-codes values like:

- `maximumPoolSize = 10`
- `minimumIdle = 2`
- `connectionTimeout = 5000`
- `connectionTestQuery = "SELECT 1"`
- `validationTimeout = 3000`

That is fine locally, but cloud rollout needs deliberate tuning.

#### Planning recommendation

Later, make pool behavior config-driven rather than hard-coded. At minimum, externalize:

- max pool size
- min idle
- connection timeout
- validation timeout
- max lifetime
- keepalive time
- leak detection threshold for staging/debugging

Also consider whether the explicit `connectionTestQuery` should stay. PostgreSQL JDBC supports `Connection.isValid()`, so a fixed test query may be unnecessary overhead.

### 3. TLS/SSL is not yet modeled explicitly

Local development does not need much TLS ceremony. Managed PostgreSQL usually does.

#### Planning recommendation

Add a future config path for:

- SSL mode (`prefer`, `require`, `verify-full`)
- optional CA certificate / trust-store location
- optional provider-specific connection options

Important principle: keep secrets and trust material out of tracked JSON files. Continue using env vars / secrets injection.

### 4. `statement_timeout` via session `SET` needs review

`DatabaseManager.applySessionQueryTimeout(...)` currently runs `SET statement_timeout TO ...` on each PostgreSQL connection.

That is fine for direct local PostgreSQL. It becomes more subtle with some cloud/pooler combinations, especially transaction-mode pooling, where session state may not behave the way a normal direct connection does.

#### Planning recommendation

Later, evaluate one of these strategies:

- keep direct connections for the app and continue using per-connection `SET statement_timeout`
- move the timeout into connection options / JDBC URL where supported
- keep direct connections for migrations/admin and use a pooler-safe configuration for app traffic

### 5. Startup migrations should become an explicit deployment decision

The current startup path calls migrations during normal initialization. That is excellent for local developer convenience and should stay that way locally.

For cloud deployment, however, the repo should eventually separate these concerns:

- **local dev startup** may still auto-migrate
- **cloud app startup** should likely assume migrations are already done
- **cloud migration job** should use the direct/admin connection path, not the pooled app path

This is one of the most important readiness items.

### 6. Verification is local-strong, cloud-light

Right now the repo has strong local proof via:

- `run_postgresql_smoke.ps1`
- `run_verify.ps1`
- `PostgresqlRuntimeSmokeTest`

That is good. It should remain the primary local proof path.

#### Planning recommendation

Later add a **cloud-specific smoke seam** rather than replacing the local seam. For example:

- an env-driven smoke path for any already-running direct PostgreSQL target
- an env-driven managed-PostgreSQL smoke test profile
- a dedicated connectivity/SSL test
- a migration-on-direct-connection validation step

That same external-target seam should also be the future Docker validation path.

### 7. Secrets strategy is good locally, but incomplete for deployment

The repo already avoids putting the DB password in tracked JSON. Keep that.

#### Planning recommendation

Later decide and document:

- where cloud DB credentials come from
- how they are rotated
- whether the app consumes one full JDBC secret or several smaller env vars
- where CA/trust settings live

My recommendation is to prefer **component-style secrets** where practical:

- host
- port
- database
- username
- password
- ssl mode
- root cert path

That makes debugging and rotation easier than one giant opaque URL secret.

## Suggested phased plan (planning only)

## Phase 0 — Protect the current local contract

Before any cloud work, preserve these invariants:

- local PostgreSQL remains the default development path
- `run_verify.ps1` remains the canonical local proof command
- H2 compatibility paths stay intentional until a deliberate decision changes them
- no secrets move into tracked config files
- Docker remains optional and should plug into the same direct PostgreSQL contract, not a forked architecture

## Phase 1 — Define the future config/profile model

Later work should first define a stable configuration contract.

### Planned outcome

A small, explicit profile model such as:

- `local-direct`
- `docker-direct`
- `cloud-direct`
- `cloud-pooled`

If the names change later, keep the underlying distinction intact: direct targets vs pooled targets.

Also decide whether the existing H2-era `DB_PROFILE` variable is being deprecated, renamed, or repurposed. That decision should be explicit, because the current code already assigns it legacy behavior.

### Files likely involved later

- `src/main/java/datingapp/core/AppConfig.java`
- `src/main/java/datingapp/core/AppConfigValidator.java`
- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/main/java/datingapp/core/RuntimeEnvironment.java`
- `config/app-config.json`
- future cloud config files under `config/`
- `.env.example`

## Phase 2 — Make connection and pool behavior configurable

### Planned outcome

Move hard-coded pool behavior out of `DatabaseManager` and into config/env surfaces.

Also widen the storage contract just enough to support direct-vs-pooled evolution cleanly, without forcing provider-specific logic into the app.

### Files likely involved later

- `src/main/java/datingapp/storage/DatabaseManager.java`
- `src/main/java/datingapp/core/AppConfig.java`
- `src/main/java/datingapp/core/AppConfigValidator.java`
- tests covering DB config behavior

## Phase 3 — Split app startup from migration/admin startup

### Planned outcome

Keep local convenience, but create a separate cloud-safe operational story:

- app runtime path for direct targets
- app runtime path for pooled targets when needed
- migration/admin path
- reusable smoke path for any already-running direct PostgreSQL target

### Files likely involved later

- `src/main/java/datingapp/storage/DatabaseManager.java`
- `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- startup/verification scripts
- smoke tests

## Phase 4 — Add cloud verification and observability

### Planned outcome

Introduce cloud-aware proof points without weakening the local ones.

### Planned verification ideas

- direct cloud connectivity smoke test
- TLS/SSL validation test
- pool-behavior sanity test
- health endpoint / pool metrics / diagnostics

## Phase 5 — Pilot one provider deliberately

### Planned outcome

Test the chosen provider with a small staging or free-tier instance before productionizing anything.

### What to validate in that pilot

- direct vs pooled endpoint behavior
- SSL mode and cert handling
- statement timeout behavior
- connection lifetime / idle behavior
- migration behavior
- provider connection limits

## Provider-specific planning notes

Some managed PostgreSQL providers strongly encourage a pooler endpoint. That is not automatically wrong, but it matters because provider poolers can behave differently from direct PostgreSQL sessions.

Planning implication: the repo should be able to support **both** of these later:

- direct connection for migrations/admin/smoke verification
- pooled connection for normal application traffic when appropriate

## Recommended file map for the eventual work

| File                                                              | Why it matters later                                                                             |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`   | central runtime config loading and startup path                                                  |
| `src/main/java/datingapp/core/AppConfig.java`                     | storage config contract                                                                          |
| `src/main/java/datingapp/core/AppConfigValidator.java`            | storage config guardrails                                                                        |
| `src/main/java/datingapp/core/RuntimeEnvironment.java`            | env / `.env` resolution behavior                                                                 |
| `src/main/java/datingapp/storage/DatabaseManager.java`            | JDBC URL, password resolution, Hikari config, statement timeout, migration bootstrapping         |
| `src/main/java/datingapp/storage/StorageFactory.java`             | runtime service graph assembly                                                                   |
| `src/main/java/datingapp/storage/schema/MigrationRunner.java`     | migration policy and idempotence                                                                 |
| `config/app-config.json`                                          | current default runtime profile                                                                  |
| `config/app-config.postgresql.local.json`                         | local PostgreSQL profile template                                                                |
| `run_postgresql_smoke.ps1`                                        | current local-first smoke path; likely future split into local-managed and external-target modes |
| `.env.example`                                                    | env-contract starting point                                                                      |
| `POSTGRESQL_POWERSHELL_GUIDE.md`                                  | current local operational guide                                                                  |
| `POSTGRESQL_NEXT_STEPS.md`                                        | local-first PostgreSQL backlog and discipline                                                    |
| `src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java` | live PostgreSQL proof seam                                                                       |

## Suggested non-goals for now

- Do **not** replace the local workflow with a cloud-first workflow.
- Do **not** remove H2 compatibility just because PostgreSQL is the runtime path today.
- Do **not** wire provider-specific settings into random classes without first stabilizing the config contract.
- Do **not** treat “successful connection to a cloud database” as the same thing as “cloud-ready architecture.”

## External references consulted

- PostgreSQL SSL/TLS docs: https://www.postgresql.org/docs/current/ssl-tcp.html
- PostgreSQL connection-string docs: https://www.postgresql.org/docs/current/libpq-connect.html
- HikariCP configuration guidance: https://github.com/brettwooldridge/HikariCP/wiki
- Neon connection pooling guide: https://neon.com/docs/connect/connection-pooling
- Supabase PostgreSQL connection guide: https://supabase.com/docs/guides/database/connecting-to-postgres

## Bottom line

This repo is already **PostgreSQL-capable**; it is not yet **cloud-ready by contract**.

That is a good place to be.

The eventual move should be treated as a controlled configuration/pooling/migration exercise — not as a storage rewrite and not as a reason to abandon the solid local-first workflow that already exists.
