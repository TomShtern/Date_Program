# PostgreSQL Setup, Schema, Config, Performance, and Stability Audit

> **Date:** 2026-04-09
> **Scope:** repository PostgreSQL setup, runtime config, Java bootstrap/storage path, local scripts, live local database state, and official PostgreSQL best-practice guidance
> **Status:** the immediate VS Code connection failure was diagnosed and fixed during this review

## Executive summary

The PostgreSQL setup is broadly solid and more disciplined than average for a local-first Java application:

- runtime config is explicit and consistent across `config/`, `.env`, and helper scripts
- the app boots PostgreSQL by default through `ApplicationStartup` + `StorageFactory.buildSqlDatabase(...)`
- the local PowerShell workflow is coherent and Windows-aware
- the schema already has meaningful foreign keys, check constraints, soft-delete-aware indexes, and append-only migrations
- application sessions explicitly set `TIME ZONE 'UTC'` and a per-session `statement_timeout`

The immediate connection problem was **not** caused by invalid SQL, broken credentials, or a bad VS Code profile. The real cause was simpler:

- **nothing was listening on `127.0.0.1:55432` when the VS Code PostgreSQL extension tried to connect**

After starting the repo-local PostgreSQL instance, the connection path worked:

- the profile `Local DatingApp` connected successfully
- direct `psql` access succeeded
- `run_postgresql_smoke.ps1` passed with `BUILD SUCCESS`

The highest-value follow-up work is:

1. make the local PostgreSQL startup path harder to miss for humans and tools
2. improve observability with `pg_stat_statements`
3. plan a future-safe timestamp strategy (`TIMESTAMPTZ` vs current `timestamp without time zone`)
4. review indexes using real query shapes and `EXPLAIN`, not just tiny local metrics
5. add a few remaining database-level constraints for enum-like values

---

## Immediate connection diagnosis and fix

### What failed

The VS Code PostgreSQL extension showed:

- `Connection error: connection timeout expired`
- `Connection failed, try again?`

### What I verified

#### Before startup

- local PostgreSQL binaries were present on `PATH`:
  - `pg_ctl`
  - `pg_isready`
  - `psql`
  - `createdb`
  - `initdb`
- **no listener existed on `localhost:55432`**

#### Repo connection contract

The repo’s expected local PostgreSQL contract is:

- **host:** `localhost` / `127.0.0.1`
- **port:** `55432`
- **database:** `datingapp`
- **username:** `datingapp`
- **password:** `datingapp`

This is consistent across:

- `config/app-config.json`
- `config/app-config.postgresql.local.json`
- `.env`
- `.env.example`
- `start_local_postgres.ps1`
- `POSTGRESQL_POWERSHELL_GUIDE.md`

#### After startup

Running `start_local_postgres.ps1` produced a live listener and readiness:

- `localhost:55432 - accepting connections`
- `Local PostgreSQL ready at localhost:55432`

Direct SQL access then succeeded:

- `SELECT version(), current_database(), current_user;`
- result: PostgreSQL `18.3`, database `datingapp`, user `datingapp`

The repo’s runtime smoke path also passed:

- `run_postgresql_smoke.ps1`
- `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

### Root cause

**Root cause:** the VS Code extension tried to connect before the local PostgreSQL server was running.

This was a runtime-state problem, not a schema/config corruption problem.

### Current status

- the local PostgreSQL runtime path is working
- the saved VS Code profile is valid
- the app’s PostgreSQL bootstrap/smoke path is green

### Practical note for the current IDE session

If the PostgreSQL side panel still shows the old failure state, retry or refresh the `Local DatingApp` connection now that the server is up.

---

## Verified repository contract

## Runtime defaults

| Surface                | Verified value                                |
|------------------------|-----------------------------------------------|
| Database dialect       | `POSTGRESQL`                                  |
| JDBC URL               | `jdbc:postgresql://localhost:55432/datingapp` |
| Username               | `datingapp`                                   |
| Password source        | `.env`, OS env var, or JVM property           |
| Local default password | `datingapp`                                   |
| Start script           | `start_local_postgres.ps1`                    |
| Smoke script           | `run_postgresql_smoke.ps1`                    |
| Full local verify      | `run_verify.ps1`                              |

## Java runtime path

The active production/runtime path is:

- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/main/java/datingapp/storage/StorageFactory.java`
- `src/main/java/datingapp/storage/DatabaseManager.java`

Important verified behaviors:

- `ApplicationStartup.initialize()` defaults to `StorageFactory.buildSqlDatabase(...)`
- `ApplicationStartup.load()` reads `config/app-config.json` by default and then applies env overrides
- `RuntimeEnvironment` supports project `.env` fallback
- `DatabaseManager.applySessionQueryTimeout(...)` executes:
  - `SET TIME ZONE 'UTC'`
  - `SET statement_timeout TO ...` for PostgreSQL sessions

That is a good foundation.

---

## Live database snapshot

## Server and session state

Verified against the live local database:

- **PostgreSQL version:** `18.3`
- **database:** `datingapp`
- **user:** `datingapp`
- **current schema:** `public`
- **server TimeZone:** `Asia/Jerusalem`
- **search_path:** `"$user", public`
- **default transaction isolation:** `read committed`
- **global statement_timeout:** `0`
- **global lock_timeout:** `0`
- **global idle_in_transaction_session_timeout:** `0`
- **global idle_session_timeout:** `0`
- **shared_preload_libraries:** empty
- **compute_query_id:** `auto`
- **installed extensions:** `plpgsql` only

## Schemas present

Non-system schemas currently present:

- `public`
- `reset_backup_20260408_104757`

That backup schema matters because it shows up in monitoring output and can distort index-usage summaries.

## Size and load snapshot

The local dataset is currently tiny.

Largest objects by total size:

- `likes` — `104 kB`
- `matches` — `88 kB`
- `swipe_sessions` — `80 kB`
- `users` — `64 kB`
- `conversations` — `56 kB`
- `messages` — `48 kB`

Live health snapshot:

- active connections: `1`
- blocked sessions: `0`
- blocking sessions: `0`
- lock waits: `0`
- cache hit ratio: `98.54%`
- index cache hit ratio: `91.69%`

These numbers are healthy, but the database is so small that they should be treated as **local-dev sanity signals**, not production-grade performance evidence.

---

## What is already good

### 1. The local-first PostgreSQL workflow is coherent

The repo has a clear, reusable workflow:

- start local PostgreSQL
- run smoke or verify
- stop only when needed

The PowerShell scripts are clearly the intended source of truth for Windows development.

### 2. The app-level runtime configuration is consistent

`config/app-config.json`, `config/app-config.postgresql.local.json`, `.env`, and the helper scripts all point at the same local PostgreSQL target.

That reduces drift and makes failures easier to reason about.

### 3. Schema bootstrap/migration structure is disciplined

The schema is Java-defined rather than SQL-file-first, but the design is internally consistent:

- `SchemaInitializer` defines the fresh-install baseline
- `MigrationRunner` is append-only and handles upgrades
- the migration list is explicit and versioned

### 4. The schema already has useful integrity protections

The live schema contains:

- foreign keys on core relationship edges
- many enum-like `CHECK` constraints
- deterministic pair-ID length checks
- distinct-user checks like `user_a <> user_b`
- partial indexes on soft-delete and active-row paths

### 5. Application sessions already do two smart things

`DatabaseManager` sets:

- `SET TIME ZONE 'UTC'`
- session `statement_timeout`

That is a good baseline for predictable application behavior.

---

## Findings, evidence, and recommendations

## High priority

### ✅ H1 — Connection startup is operationally correct but easy to miss

**Severity:** High for developer experience, Low for schema correctness

**Finding**

The VS Code connection timeout happened because the local PostgreSQL server was not running when the extension tried to connect.

**Evidence**

- no listener was present on `localhost:55432` before startup
- the saved profile `Local DatingApp` exists and connects successfully once the server is up
- `start_local_postgres.ps1` made the server reachable
- `psql` and `run_postgresql_smoke.ps1` both succeeded

**Why it matters**

This will keep recurring unless the startup requirement is obvious in the day-to-day workflow.

**Recommendation**

Keep the current scripts, but make the startup step harder to forget:

1. add a very obvious “Start PostgreSQL first” note near the main PostgreSQL connection instructions
2. optionally add a VS Code task such as `Start local PostgreSQL`
3. optionally add a small preflight script that checks:
   - server running
   - port reachable
   - `pg_isready`
   - `psql` login

**Expected impact**

Much faster recovery from the exact problem that happened here.

**Implemented on 2026-04-09**

- added `check_postgresql_runtime_env.ps1` to validate tools, listener state, and login before IDE/runtime use
- added repo-local VS Code tasks in `.vscode/tasks.json` for preflight/start/stop
- updated `README.md` and `POSTGRESQL_POWERSHELL_GUIDE.md` so the startup requirement is visible in the main local workflow
- added `src/test/powershell/CheckPostgresqlRuntimeEnvScriptTest.ps1`

---

### ✅ H2 — All event/audit timestamps use `timestamp without time zone`

**Severity:** High

**Finding**

The live schema stores application timestamps as `timestamp without time zone` across core tables such as:

- `users.created_at`, `users.updated_at`, `users.deleted_at`
- `likes.created_at`
- `matches.created_at`, `matches.updated_at`, `matches.ended_at`, `matches.deleted_at`
- `conversations.created_at`, `last_message_at`, archive/read/deleted timestamps
- `messages.created_at`, `deleted_at`
- analytics and moderation timestamps as well

**Evidence**

Observed directly in the live table DDL for `public`.

**Why it matters**

Official PostgreSQL docs note that:

- plain `timestamp` means `timestamp without time zone`
- timezone information in input literals is ignored for `timestamp without time zone`
- PostgreSQL recommends using date/time types that contain both date and time when using time zones

Repo-specific context makes this more important:

- the **server** is using `TimeZone = Asia/Jerusalem`
- the **application** forces `SET TIME ZONE 'UTC'` per session
- the schema still stores naive timestamps

That works locally if every writer is disciplined, but it becomes fragile across:

- daylight saving transitions
- external tools/manual SQL sessions
- cloud/multi-region migration
- future services not sharing the exact same session-init behavior

**Recommendation**

Plan a phased migration to `TIMESTAMPTZ` for true instants:

1. keep `DATE` columns as `DATE`
2. migrate event/audit timestamps to `TIMESTAMPTZ`
3. update JDBI mappings and migration tests together
4. preserve a strict UTC convention during migration

If this is postponed, at minimum:

- document the current UTC-writing contract clearly
- avoid ambiguous string literals
- ensure every runtime path sets the session timezone deliberately

**Expected impact**

Much better temporal correctness and far less future migration pain.

**Implemented on 2026-04-09**

- converted the fresh baseline schema in `SchemaInitializer.java` from naive `TIMESTAMP` columns to `TIMESTAMP WITH TIME ZONE` for event/audit instants
- added migration `V17` in `MigrationRunner.java` to convert legacy timestamp columns using UTC semantics (`... AT TIME ZONE 'UTC'`) on upgrade paths
- updated schema tests to the new latest migration version and added focused V17 coverage
- verified:
  - `SchemaInitializerTest` passes on the H2 compatibility path
  - `run_postgresql_smoke.ps1` still passes on the live PostgreSQL runtime path
  - live PostgreSQL columns now report `timestamp with time zone` for representative fields including:
    - `users.created_at`
    - `users.updated_at`
    - `matches.created_at`
    - `conversations.last_message_at`
    - `messages.created_at`
    - `notifications.created_at`
    - `schema_version.applied_at`
    - `undo_states.expires_at`

---

### ✅ H3 — `pg_stat_statements` is not enabled

**Severity:** High

**Finding**

The database currently has only `plpgsql` installed. `pg_stat_statements` is not active.

**Evidence**

- `pg_extension` returned only `plpgsql`
- `shared_preload_libraries` is empty

**Why it matters**

Without `pg_stat_statements`, you do not have first-class visibility into:

- slowest normalized queries
- cumulative execution cost
- planning vs execution time
- block hit/read behavior by statement
- which SQL actually matters under load

Official PostgreSQL docs explicitly require:

- `shared_preload_libraries = 'pg_stat_statements'`
- `compute_query_id = on|auto`
- `CREATE EXTENSION pg_stat_statements`

**Recommendation**

Enable `pg_stat_statements` for local performance work and any future staging/production-like environment.

Suggested minimum:

- set `shared_preload_libraries = 'pg_stat_statements'`
- keep `compute_query_id = auto` or set it explicitly to `on`
- create the extension in the target database
- use it before making serious index or query changes

**Expected impact**

A major observability upgrade with low conceptual cost.

**Implemented on 2026-04-09**

- updated `start_local_postgres.ps1` to write local cluster settings for `shared_preload_libraries = 'pg_stat_statements'` and `compute_query_id = on`
- the startup script now installs `pg_stat_statements` in the target database automatically after the database exists
- extended `src/test/powershell/StartLocalPostgresScriptTest.ps1` to cover the new observability bootstrap behavior
- verified live local state after startup:
  - `shared_preload_libraries = pg_stat_statements`
  - `compute_query_id = on`
  - extension `pg_stat_statements` is installed

---

## Medium priority

### ✅ M1 — Default `search_path` is still `"$user", public`

**Severity:** Medium

**Finding**

The live server uses the default-style search path:

- `"$user", public`

**Evidence**

Verified with `current_setting('search_path')`.

**Why it matters**

Official PostgreSQL docs note that the default search path is suitable only when the database has:

- a single user, or
- a few mutually trusting users

That is acceptable for the current local setup, but it is not the best long-term posture for a hardened runtime.

**Recommendation**

For any environment beyond single-user local dev:

1. decide whether the app should stay in `public` or move to a dedicated schema
2. set `search_path` explicitly for the app role/session
3. avoid relying on ambient defaults

**Expected impact**

Safer schema resolution and fewer “works locally, weird elsewhere” surprises.

**Implemented on 2026-04-09**

- `DatabaseManager.applySessionQueryTimeout(...)` now pins PostgreSQL application sessions to `SET search_path TO public` before applying the session timeout
- `start_local_postgres.ps1` now applies `ALTER ROLE ... IN DATABASE ... SET search_path = public` for the local `datingapp` role
- verified live local state: `SHOW search_path` now returns `public` for the `datingapp` role

---

### ✅ M2 — Several indexes need an `EXPLAIN`-driven review

**Severity:** Medium

**Finding**

The live metrics show **25 indexes with zero scans** and about **253,952 bytes** of apparently unused index storage.

However, the local dataset is tiny, so “unused” does **not** automatically mean “bad”.

**Evidence**

Live monitoring reported unused indexes including:

- `idx_messages_conversation_id`
- `idx_messages_conversation_created`
- `idx_conversations_last_msg`
- `idx_conversations_user_a_last_msg`
- `idx_conversations_user_b_last_msg`
- `idx_sessions_started_at_desc`
- several others

At the same time, the actual query shapes reveal something important:

- `JdbiConnectionStorage.getConversationsFor(...)` orders by:
  - `COALESCE(last_message_at, created_at) DESC, id DESC`
- but the schema indexes are on:
  - `last_message_at` only
  - or `(user_a, last_message_at DESC)` / `(user_b, last_message_at DESC)`

So at least part of the mismatch is not just “tiny data”; some indexes do not line up perfectly with real ordering expressions.

There is also a likely redundancy candidate:

- `messages(conversation_id)`
- `messages(conversation_id, created_at)`

Because the second index can serve left-prefix lookups on `conversation_id`, the standalone index may be unnecessary.

**Recommendation**

Do **not** drop indexes based only on local unused-index stats.

Instead:

1. seed representative data
2. run `EXPLAIN (ANALYZE, BUFFERS)` on real hot queries
3. review these likely candidates:
   - replace or supplement conversation-list indexes with expression indexes matching `COALESCE(last_message_at, created_at)` if that view is hot
   - verify whether `idx_messages_conversation_id` is redundant next to `idx_messages_conversation_created`
   - verify whether `idx_conversations_last_msg` is ever beneficial

**Expected impact**

Better read-path performance with less write/index maintenance overhead.

**Implemented on 2026-04-09**

- rewrote the visible-conversation list SQL in `JdbiConnectionStorage` from a broad `OR` predicate to a `UNION ALL` shape that matches the per-side conversation indexes more directly
- updated the converged schema/index path so the conversation activity indexes now match the real query filters and ordering more closely:
  - `idx_conversations_user_a_last_msg`
  - `idx_conversations_user_b_last_msg`
- removed the redundant converged-schema standalone `messages(conversation_id)` index while preserving `idx_messages_conversation_created`
- added migration `V16` so upgraded databases converge on the same index shape
- verified targeted tests for:
  - `SchemaInitializerTest`
  - `JdbiCommunicationStorageSocialTest`
- verified live local PostgreSQL index state after smoke/upgrade:
  - both conversation activity indexes exist with partial predicates on visible, non-deleted rows
  - `public.messages.idx_messages_conversation_id` is absent in the converged schema

---

### ✅ M3 — Manual/non-app sessions have no timeout safety net

**Severity:** Medium

**Finding**

The global server setting is:

- `statement_timeout = 0`

But the application sets a session timeout after connecting.

**Evidence**

- live server setting: `statement_timeout = 0`
- `DatabaseManager.applySessionQueryTimeout(...)` sets `statement_timeout` per app connection

**Why it matters**

This is actually a fairly reasonable design. Official PostgreSQL docs explicitly say setting `statement_timeout` in `postgresql.conf` is **not recommended** because it affects all sessions.

The subtle issue is that:

- app sessions are protected
- ad-hoc/manual sessions are not

That includes some extension/CLI troubleshooting sessions.

**Recommendation**

Keep the app-session behavior.

Optionally add role-level safeguards for the app role or admin workflows, such as:

- `ALTER ROLE datingapp SET statement_timeout = '30s'`
- possibly a small `lock_timeout`
- possibly `idle_in_transaction_session_timeout` for interactive/admin use

Be careful with pooled connections and interactive tools.

**Expected impact**

Lower risk of runaway manual queries without misconfiguring the whole server globally.

**Implemented on 2026-04-09**

- `start_local_postgres.ps1` now applies local role defaults for the `datingapp` role in the `datingapp` database:
  - `statement_timeout = 30s`
  - `lock_timeout = 5s`
  - `idle_in_transaction_session_timeout = 5min`
- kept the application’s per-session timeout behavior in `DatabaseManager` intact
- verified live local state with `SHOW` commands as the `datingapp` role:
  - `statement_timeout = 30s`
  - `lock_timeout = 5s`
  - `idle_in_transaction_session_timeout = 5min`

---

### ✅ M4 — Backup schema retention currently pollutes monitoring output

**Severity:** Medium

**Finding**

A backup schema exists in the live database:

- `reset_backup_20260408_104757`

Metrics output includes objects from that schema.

**Evidence**

- schema inventory showed both `public` and `reset_backup_20260408_104757`
- index-usage output included rows from the backup schema

**Why it matters**

This does not break the app, but it does:

- add noise to index-usage reports
- increase the chance of reading the wrong schema during debugging
- complicate future size/maintenance reviews

**Recommendation**

Define a clear policy for reset backup schemas:

- retain only the newest one
- move them out of the active local database if not needed
- or exclude them from monitoring queries/documentation snapshots

**Expected impact**

Cleaner operational signals and less debugging confusion.

**Implemented on 2026-04-09**

- `reset_local_postgres.ps1` now keeps only the newest auto-generated `reset_backup_*` schema by default after a successful reset and best-effort removes older ones
- added `-RetainedAutoBackupSchemas` to allow intentionally keeping more than one auto backup
- extended `ResetLocalPostgresScriptTest.ps1` to cover the new backup-schema retention cleanup query path

---

### ✅ M5 — A few enum-like columns are still application-only validated

**Severity:** Medium

**Finding**

Several stable-looking categorical columns still lack database constraints in the live schema, for example:

- `swipe_sessions.state`
- `user_interested_in.gender`
- `user_db_smoking.value`
- `user_db_drinking.value`
- `user_db_wants_kids.value`
- `user_db_looking_for.value`
- `user_db_education.value`

**Evidence**

Observed directly in the live DDL.

**Why it matters**

This means integrity still depends primarily on application behavior for those fields.

That may be fine for evolving vocabularies, but some of these look operationally stable enough to constrain safely.

**Recommendation**

Prioritize by stability:

1. add a `CHECK` constraint for `swipe_sessions.state`
2. decide whether normalized profile/preference tables should use checks or remain app-validated
3. if values may evolve frequently, document that explicitly and keep the DB flexible on purpose

**Expected impact**

Stronger data integrity with low runtime cost.

**Implemented on 2026-04-09**

- added baseline schema checks in `SchemaInitializer.java` for:
  - `swipe_sessions.state`
  - `user_interested_in.gender`
  - `user_db_smoking.value`
  - `user_db_drinking.value`
  - `user_db_wants_kids.value`
  - `user_db_looking_for.value`
  - `user_db_education.value`
- added migration `V15` in `MigrationRunner.java` so upgraded databases converge on the same constraints
- extended `SchemaInitializerTest` for fresh-schema and legacy-upgrade coverage
- verified live local PostgreSQL now contains all 7 expected constraint names in schema `public`

---

### ✅ M6 — Hikari pool config is fine for local dev but thin for longer-lived environments

**Severity:** Medium

**Finding**

`DatabaseManager` configures Hikari with a minimal set of properties:

- `maximumPoolSize = 10`
- `minimumIdle = 2`
- `connectionTimeout = 5000`
- `validationTimeout = 3000`
- `connectionTestQuery = 'SELECT 1'`

But it does not explicitly configure things like:

- `maxLifetime`
- `idleTimeout`
- `keepaliveTime`
- `initializationFailTimeout`
- metrics/monitoring integration

**Evidence**

Observed in `src/main/java/datingapp/storage/DatabaseManager.java`.

**Why it matters**

Hikari’s own guidance is that sensible defaults often work well, but pool sizing and connection lifecycle settings should be reviewed intentionally for production-like workloads.

For this repository today, the current config is acceptable for local use.

For a longer-lived deployment, you would want explicit decisions on:

- pool sizing
- stale connection handling
- startup failure behavior
- monitoring visibility

**Recommendation**

Keep the current minimal config for local dev.

Before any cloud/always-on rollout:

1. revisit `maximumPoolSize` based on real workload
2. set explicit connection lifecycle settings
3. expose pool metrics or MXBean monitoring
4. verify behavior across network interruptions and server restarts

**Expected impact**

Better resilience and easier runtime diagnostics.

**Implemented on 2026-04-09**

- extended `AppConfig.StorageConfig` with explicit Hikari-related settings:
  - `maxPoolSize`
  - `minIdle`
  - `connectionTimeoutSeconds`
  - `validationTimeoutSeconds`
  - `idleTimeoutSeconds`
  - `maxLifetimeSeconds`
  - `keepaliveTimeSeconds`
- updated `AppConfigValidator`, `ApplicationStartup` env overrides, and `DatabaseManager` to use the config surface instead of hard-coded pool values
- updated tracked config surfaces:
  - `config/app-config.json`
  - `config/app-config.postgresql.local.json`
  - `.env.example`
- updated the local `.env` defaults in this workspace to match the new pool settings surface
- verified targeted tests for:
  - `AppConfigTest`
  - `AppConfigValidatorTest`
  - `DatabaseManagerConfigurationTest`

---

## Lower priority / future-facing

### ✅ L1 — Email and phone are not yet database-hardened as identity fields

**Severity:** Low now, Medium later if used for auth/unique identity

**Finding**

`users.email` and `users.phone` are not unique in the live schema.

**Why it matters**

If those fields later become authoritative user identity fields, duplicates and case-sensitivity behavior will matter.

Official PostgreSQL docs note that:

- `citext` can provide case-insensitive comparison and uniqueness behavior
- but current docs also suggest considering **nondeterministic collations** instead for more correct Unicode handling

**Recommendation**

If email becomes a login/identity field:

1. choose a case-insensitive strategy (`citext` or nondeterministic collation)
2. clean/merge duplicates first
3. then add uniqueness enforcement

**Expected impact**

Cleaner identity semantics and fewer future migration headaches.

**Implemented on 2026-04-09**

- applied a conservative database-level hardening that fits the current codebase semantics:
  - `uk_users_email`
  - `uk_users_phone`
  - `ck_users_email_trimmed`
  - `ck_users_phone_trimmed`
- added migration `V18` to trim existing contact values, fail fast on duplicates, and then enforce the new constraints on upgraded databases
- kept the implementation H2-compatible instead of introducing PostgreSQL-only `citext` into the shared baseline path
- verified live local PostgreSQL now contains all four contact-hardening constraints on `public.users`

---

### ✅ L2 — Java-coded schema is workable, but DBA/agent review would benefit from a generated SQL snapshot

**Severity:** Low

**Finding**

This repo keeps schema definition in Java string DDL rather than migration SQL files.

**Why it matters**

It is internally consistent, but it makes quick schema review harder for:

- DBAs
- new contributors
- AI agents trying to compare pure SQL structure

**Recommendation**

Consider adding a generated schema snapshot or documented export path for review purposes.

This is an ergonomics improvement, not a correctness fix.

**Expected impact**

Faster review and easier diffing of schema changes.

**Implemented on 2026-04-09**

- added `export_local_postgresql_schema.ps1` to export a fresh `public`-schema snapshot from the local PostgreSQL instance via `pg_dump`
- added a checked-in SQL-first reference file at `postgresql-public-schema-snapshot.sql`
- verified the exporter runs successfully against the local database and writes a reproducible schema-only snapshot

---

## What I would **not** change right now

### 1. I would not delete indexes purely from local unused-index metrics

The local dataset is too small to make that trustworthy.

### 2. I would not set a blanket global `statement_timeout` in `postgresql.conf`

Official PostgreSQL docs specifically caution against that for all sessions.

### 3. I would not over-tune server memory settings yet

Current settings like:

- `shared_buffers = 128MB`
- `work_mem = 4MB`
- `maintenance_work_mem = 64MB`

are fine for a local development database of this size.

### 4. I would not switch `notifications.data_json` to `JSONB` unless payload querying becomes real

Right now it behaves like opaque serialized metadata, which is acceptable.

---

## Suggested implementation order for future agent work

## 1. Operational quality-of-life

**Goal:** prevent the exact connection issue that happened here

**Likely files:**

- `start_local_postgres.ps1`
- `POSTGRESQL_POWERSHELL_GUIDE.md`
- `README.md`
- optional `.vscode/tasks.json`

**Suggested changes:**

- add a more obvious startup note/task
- optionally add a preflight connectivity check script

---

## 2. Observability

**Goal:** make real performance work possible

**Likely surfaces:**

- local PostgreSQL config outside the repo
- docs in this repo
- optional bootstrap verification docs/scripts

**Suggested changes:**

- enable `pg_stat_statements`
- document how to inspect top queries and reset stats

---

## 3. Timestamp strategy

**Goal:** future-proof temporal correctness

**Likely files:**

- `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
- `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- JDBI storage classes and row mappers
- migration tests under `src/test/java/datingapp/storage/`

**Suggested changes:**

- plan a phased `TIMESTAMPTZ` migration
- update tests and app mappings together

---

## 4. Query/index alignment review

**Goal:** align indexes with real query shapes

**Likely files:**

- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`
- `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
- `src/main/java/datingapp/storage/schema/MigrationRunner.java`

**Suggested changes:**

- run `EXPLAIN (ANALYZE, BUFFERS)` on representative data
- validate or replace conversation/message indexes based on evidence

---

## 5. Remaining integrity constraints

**Goal:** move stable enum-like values from app-only validation to DB enforcement where appropriate

**Likely files:**

- `SchemaInitializer.java`
- `MigrationRunner.java`
- storage tests and migration tests

---

## Validation commands for future work

```powershell
.\start_local_postgres.ps1
.\run_postgresql_smoke.ps1
.\run_verify.ps1
mvn spotless:apply verify
```

For query/index review, use PostgreSQL-native inspection rather than guessing:

```sql
EXPLAIN (ANALYZE, BUFFERS)
...
```

---

## Official guidance used in this review

- PostgreSQL Date/Time Types
  `https://www.postgresql.org/docs/current/datatype-datetime.html`
  - plain `timestamp` means `timestamp without time zone`
  - timezone information is ignored for `timestamp without time zone`
  - PostgreSQL recommends timezone-aware date/time types when time zones matter

- PostgreSQL Client Connection Defaults
  `https://www.postgresql.org/docs/current/runtime-config-client.html`
  - `statement_timeout` is measured on the server side
  - setting `statement_timeout` in `postgresql.conf` is not recommended for all sessions
  - default search-path behavior is not ideal for multi-user hardening

- PostgreSQL Partial Indexes
  `https://www.postgresql.org/docs/current/indexes-partial.html`
  - partial indexes are specialized
  - predicates must match the query’s `WHERE` condition closely
  - they should not be treated as magic or as a substitute for better schema/query design

- PostgreSQL Indexes Overview
  `https://www.postgresql.org/docs/current/indexes.html`
  - indexes speed reads but add write/maintenance overhead
  - they should be used sensibly

- PostgreSQL `pg_stat_statements`
  `https://www.postgresql.org/docs/current/pgstatstatements.html`
  - requires preload + extension creation
  - provides planning/execution stats and normalized query visibility

- PostgreSQL `citext`
  `https://www.postgresql.org/docs/current/citext.html`
  - useful for case-insensitive comparisons and uniqueness
  - current docs also suggest considering nondeterministic collations for better Unicode behavior

- HikariCP guidance (via current documentation context)
  - sensible defaults are often fine
  - lifecycle settings and pool sizing should still be reviewed intentionally
  - metrics/monitoring matter for longer-lived environments

---

## Bottom line

The PostgreSQL foundation is **good**.

The immediate connection issue was operational, not architectural: **the local server was not running**.

After startup, the actual runtime path proved healthy:

- local PostgreSQL reachable
- VS Code profile valid
- direct SQL access valid
- application smoke path green

The most important improvements from here are not a full redesign. They are:

1. make startup/connection friction harder to hit
2. improve observability
3. adopt a more future-safe timestamp model
4. tune or simplify indexes based on real plans
5. keep tightening invariants where the domain is already stable

---

## 2026-04-10 second-pass optional polish

After the main PostgreSQL fixes were in place, a second pass focused only on cleanup/polish work:

- refactored large assertion-heavy test methods in `AppConfigTest` and `SchemaInitializerTest` into helper-based checks
- refreshed `postgresql-public-schema-snapshot.sql` from the live post-migration local PostgreSQL state
- kept the schema export path reproducible through `export_local_postgresql_schema.ps1`
- aligned `RunVerifyScriptTest.ps1` and `ResetLocalPostgresScriptTest.ps1` with the current local PostgreSQL workflow
- re-ran the full local verification path after the cleanup pass

Result: the cleanup pass did not reopen the core PostgreSQL fixes, and the full local verification path remained green.
