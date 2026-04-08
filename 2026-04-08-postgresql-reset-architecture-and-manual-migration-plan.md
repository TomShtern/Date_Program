# 2026-04-08 PostgreSQL Reset Architecture and Manual Migration Plan

> **Status:** ✅ core reset architecture implemented; current code now targets `schema_version=14` after the 2026-04-08 post-audit hardening pass.
>
> **Validation note:** the real local reset plus full verification were executed on 2026-04-08 against schema version 13. The current code adds version-14 constraint hardening and broader regression coverage; rerun the external-command verification path in a shell that can spawn Maven/PostgreSQL binaries before treating the new version-14 state as revalidated.
>
> **Implemented scope:** fresh-baseline schema cleanup, fresh-install fast-path migration recording, local manual reset tooling, targeted regression coverage, real local reset execution, and full local verification.
>
> **Primary recommendation:** because the database is local, non-production, and contains only a small amount of fake data, the best next move is a **manual synthetic reset** rather than preserving the current repair-heavy migration history.
>
> **Source of truth:** actual code and tests only. This document intentionally ignores stale markdown unless it points back to live code.

## How to use this document

This document is optimized for a future AI coding agent or human implementer.

- Treat `src/main/java` and `src/test/java` as authoritative.
- Prefer the **recommended immediate target** in this document over a broad schema redesign.
- Preserve current storage contracts first; defer larger model refactors unless explicitly approved.
- Use the evidence map to verify every claim before changing code.

## Implementation progress

- ✅ Replaced the stale fresh-install baseline in `SchemaInitializer` with the current post-V14 schema shape used by the runtime.
- ✅ Added a fresh-database fast path in `MigrationRunner` so new databases record historical versions as covered by the fresh baseline instead of replaying the whole repair ladder.
- ✅ Expanded `SchemaInitializerTest` to lock the final fresh-schema table/index/constraint shape.
- ✅ Added `PostgresqlSchemaBootstrapSmokeTest` so the reset workflow can bootstrap any configured local PostgreSQL target safely.
- ✅ Added `reset_local_postgres.ps1` to perform the manual synthetic reset with a preserved backup schema and post-reset smoke validation.
- ✅ Added `ResetLocalPostgresScriptTest.ps1` to verify both the reset-script orchestration path and the generated import SQL normalization/filtering rules on Windows.
- ✅ Executed the real local reset successfully; preserved backup schema: `reset_backup_20260408_104757`.
- ✅ Verified the rebuilt local database preserved the expected fake-data counts (`users=16`, `user_photos=16`, `user_interested_in=16`, `likes=16`, `matches=8`).
- ✅ Verified the rebuilt schema metadata was recreated cleanly (`schema_version=13` rows recorded, backup schema preserved).
- ✅ Ran targeted regressions, PostgreSQL smoke, and the full repo verification gate successfully.
- ✅ Added V14 so legacy databases also receive the remaining fresh-baseline structural checks plus nonblank user-facing text constraints.
- ✅ Made `PostgresqlRuntimeSmokeTest` honor the configured PostgreSQL target instead of hard-coding `localhost:55432/datingapp`.
- ✅ Made `JdbiTrustSafetyStorage.save(Report)` revive soft-deleted report pair rows instead of leaving uniqueness-blocking ghost tombstones.

### Intentionally deferred

- ⏸️ Additional normalized child-table DB checks beyond the current runtime-safe scope remain deferred.
- ⏸️ Larger domain/storage redesign items listed in the non-goals section remain deferred.

## Executive recommendation

### Recommended now

1. **Reset to a clean PostgreSQL baseline that matches the current runtime contract.**
   - The current runtime schema is the result of `SchemaInitializer` **plus** `MigrationRunner` V2–V14 for legacy upgrades, while fresh installs land on the same contract directly.
   - Fresh installs should stop replaying a long chain of repair migrations just to reach the real schema.

2. **Do a manual synthetic data move instead of a real historical migration.**
   - Preserve the small amount of fake user data you care about.
   - Recreate the schema cleanly.
   - Reinsert the preserved rows with a small amount of transformation.

3. **Keep table names and storage contracts mostly stable in the reset.**
   - This gives the biggest quality improvement for the least code churn.
   - It avoids breaking the JDBI storages, use cases, REST layer, and tests all at once.
   - The only runtime-contract adjustments worth making now are the ones with immediate, code-verified benefit: **explicit UTC timestamp semantics** and **explicit row-revival semantics for certain soft-deleted unique pairs**.

### Do **not** combine with this reset unless separately approved

- full domain/storage refactor
- PostgreSQL-only redesign that drops H2 compatibility immediately
- native PostgreSQL enum types everywhere
- `conversation_participants` redesign
- deep analytics redesign / partitioning / event sourcing

## Verified context map

### Runtime/bootstrap seam

| File                                                            | Why it matters                                                                                             |
|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` | Production runtime path calls `StorageFactory.buildSqlDatabase(...)`.                                      |
| `src/main/java/datingapp/storage/StorageFactory.java`           | Composes the actual SQL-backed runtime graph.                                                              |
| `src/main/java/datingapp/storage/DatabaseManager.java`          | Owns connection setup, schema initialization, timeout/session settings, and runtime storage config.        |
| `config/app-config.postgresql.local.json`                       | Verified local PostgreSQL runtime target: `jdbc:postgresql://localhost:55432/datingapp`, user `datingapp`. |

### Schema/migration seam

| File                                                                | Why it matters                                                                                 |
|---------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `src/main/java/datingapp/storage/schema/SchemaInitializer.java`     | Current fresh-install baseline; creates the modern runtime schema directly.                    |
| `src/main/java/datingapp/storage/schema/MigrationRunner.java`       | Legacy runtime schema converges here through V14.                                              |
| `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java` | Proves baseline vs migrated behavior and several legacy repair cases.                          |

### Persistence contract seam

| File                                                                    | Why it matters                                                                                           |
|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `src/main/java/datingapp/core/model/User.java`                          | Defines the current profile aggregate and value semantics.                                               |
| `src/main/java/datingapp/core/profile/MatchPreferences.java`            | Source of truth for lifestyle, dealbreaker, interest, and pace enums/limits.                             |
| `src/main/java/datingapp/core/model/Match.java`                         | Deterministic unordered pair ID for matches.                                                             |
| `src/main/java/datingapp/core/connection/ConnectionModels.java`         | Deterministic unordered pair ID for conversations; source of friend request/report/block/message shapes. |
| `src/main/java/datingapp/storage/jdbi/NormalizedProfileRepository.java` | Real normalized storage shape for photos, interests, gender preferences, and dealbreaker tables.         |
| `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`       | Real SQL behavior for conversations, messages, friend requests, notifications.                           |
| `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`          | Real SQL behavior for profile views, achievements, daily picks, sessions, standouts.                     |
| `src/main/java/datingapp/storage/jdbi/JdbiAccountCleanupStorage.java`   | Strong evidence for which tables are treated as durable vs disposable during account cleanup.            |

### Contract tests worth preserving during implementation

| File                                                                         | What it proves                                                          |
|------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`     | Fresh schema should no longer expose legacy serialized profile columns. |
| `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java` | Normalized profile tables are real contract, not optional helpers.      |
| `src/test/java/datingapp/storage/jdbi/FriendRequestUniquenessTest.java`      | `pair_key` + `pending_marker` uniqueness is intentional behavior.       |
| `src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java`            | Live PostgreSQL runtime seam.                                           |
| `run_postgresql_smoke.ps1`                                                   | Existing repo-local PostgreSQL validation path.                         |

## Current verified state

### 1) Fresh installs now land directly on the runtime schema contract

- `ApplicationStartup.initialize(config)` calls `StorageFactory.buildSqlDatabase(...)`.
- `StorageFactory.buildSqlDatabase(...)` configures runtime storage on `DatabaseManager`.
- `DatabaseManager.initializeSchema()` calls `MigrationRunner.runAllPending(...)`.
- On a fresh database, `MigrationRunner` creates the baseline through `SchemaInitializer.createAllTables(...)` as V1, then records V2–V14 as covered by the fresh baseline instead of replaying them.
- On an existing database, `MigrationRunner` applies only the pending V2–V14 upgrades.
- In practice, the runtime shape is **28 tables** including `daily_picks` and `schema_version`.

**Implication:** the real schema is the **post-V14 final state**, and the fresh baseline now matches it for new databases.

### 2) Historical baggage is now isolated to legacy-upgrade support

Verified from `SchemaInitializer.java` and `MigrationRunner.java`:

- the fresh baseline no longer recreates legacy serialized profile columns such as `photo_urls`, `interests`, and `interested_in`
- V3 remains only as an upgrade path for older databases that still have those columns
- `daily_picks` exists directly in the fresh baseline, while V2 remains as compatibility for older installs
- V5/V6/V9/V11 remain repair-oriented legacy migrations
- V14 now backfills the remaining fresh-baseline structural checks and nonblank text checks onto upgraded databases

**Implication:** fresh database creation now matches the present project state; the remaining complexity exists for compatibility with older local databases only.

### 3) The current durable relational model is already visible in code

The code consistently treats the following as the real model:

- `users` as the primary profile row
- normalized child tables for multi-valued profile data:
  - `user_photos`
  - `user_interests`
  - `user_interested_in`
  - `user_db_smoking`
  - `user_db_drinking`
  - `user_db_wants_kids`
  - `user_db_looking_for`
  - `user_db_education`
- canonical unordered pair rows for:
  - `matches`
  - `conversations`
  - pending friend-request uniqueness via `pair_key`

### 4) Soft delete vs hard delete is mixed on purpose

Verified especially from `JdbiAccountCleanupStorage.java`:

- **soft-deleted user-facing graph rows:** `users`, `likes`, `matches`, `conversations`, `messages`, `profile_notes`, `blocks`, `reports`
- **hard-deleted or disposable rows:** `profile_views`, normalized profile child tables, `user_stats`, `user_achievements`, `daily_pick_views`, `daily_picks`, `swipe_sessions`, `standouts`, `friend_requests`, `notifications`, `undo_states`

**Implication:** the code already distinguishes between durable business history and disposable/derived/cache data. The reset should do the same.

### 5) Canonical pair IDs are a first-class contract

Verified from `Match.generateId(...)`, `Conversation.generateId(...)`, and usage scans:

- match ID format is `smaller_uuid + "_" + larger_uuid`
- conversation ID format is the same pattern
- friend-request lookup canonicalizes pair identity separately via `pair_key`
- these IDs are referenced broadly across runtime code and tests

**Implication:** a schema reset must preserve these pair-key semantics exactly.

## Recommended immediate target architecture

### Goal

Create a **clean fresh baseline** that already reflects the current post-V14 runtime schema and contract tests, then perform a manual data move into that schema.

### Design rule

**Keep the current storage contract stable.**

That means:

- keep current table names
- keep current core foreign key structure
- keep current canonical ID semantics
- keep normalized profile child tables
- keep current JDBI storage expectations

This is the best balance of correctness, speed, and risk.

### Slight runtime contract adjustments worth making

These are the only runtime-contract changes that look clearly worth baking into the reset plan.

#### 1. Make UTC timestamp semantics an explicit runtime contract

Current code already points this way:

- `DatabaseManager.applySessionQueryTimeout(...)` sets `TIME ZONE 'UTC'` before applying the query timeout
- `StorageFactory.registerTypeCodecs(...)` registers the shared `Instant` codec
- `JdbiTypeCodecs.registerInstantCodec(...)` and `SqlRowReaders.readInstant(...)` bind/read `Instant` values with an explicit UTC calendar
- `DatabaseManagerConfigurationTest` and `SqlRowReadersTest` now verify these UTC behaviors directly

**Recommendation:** treat this as a first-class runtime contract, not an incidental implementation detail.

That means the reset/implementation plan should preserve these rules:

- every SQL runtime connection is pinned to UTC session time
- all JDBI `Instant` bindings and reads go through the shared codec path, not ad-hoc timestamp mappings
- manual export/import scripts for the synthetic migration should preserve UTC `Instant` semantics end to end

**Benefit:** deterministic timestamp round-trips across PostgreSQL and H2-backed compatibility paths, fewer timezone surprises during manual import, and a cleaner future path to `TIMESTAMPTZ` if that ever becomes necessary.

#### 2. Make soft-delete revival semantics explicit where the code already depends on them

The current runtime is not using one universal "soft delete means tombstone forever" rule.

Verified examples:

- `JdbiMatchmakingStorage.saveLikeAndMaybeCreateMatch(...)` saves likes through an upsert keyed by `(who_likes, who_got_liked)` and writes `deleted_at = NULL`, which effectively revives the pair row on reuse
- `JdbiTrustSafetyStorage.save(Block)` explicitly revives a soft-deleted block row before attempting a fresh insert
- `JdbiTrustSafetyStorage.save(Report)` now explicitly revives a soft-deleted report row for the same pair instead of leaving an invisible uniqueness blocker
- `RecordBindingTest.deletedBlockCanBeRecreated()` proves that re-blocking the same pair reuses the existing row rather than creating duplicates
- `RecordBindingTest.deletedReportCanBeRecreated()` now proves the same revival behavior for reports
- `JdbiMatchmakingStorage` also has explicit unmatched-pair reactivation logic for matches

**Recommendation:** the reset plan should preserve and document this as a table-specific contract.

Practical rule for the reset:

- preserve active rows by default
- preserve soft-deleted rows only when their historical presence matters
- do not import soft-deleted unique-pair rows blindly and then assume later runtime behavior will treat them as inert tombstones

**Benefit:** avoids unique-key surprises after the reset and keeps the rebuilt data aligned with the actual storage behavior the app uses today.

## What is worth fixing now

### Fix now: baseline/final-schema mismatch

The new fresh baseline should directly contain:

- `daily_picks`
- all normalized profile tables
- all V10 foreign keys
- all V12 value checks
- V13 `matches.ended_by` integrity
- the final `profile_views` composite primary key shape

It should **not** recreate legacy serialized profile columns and then delete them later.

### Fix now: missing database-enforced invariants

The code enforces several rules that the database should also enforce.

#### Strongly recommended

- `likes`: `who_likes <> who_got_liked`
- `matches`: `user_a <> user_b`
- `conversations`: `user_a <> user_b`
- `friend_requests`: `from_user_id <> to_user_id`
- `blocks`: `blocker_id <> blocked_id`
- `reports`: `reporter_id <> reported_user_id`
- `profile_notes`: `author_id <> subject_id`
- `profile_views`: `viewer_id <> viewed_id`
- `users`: `min_age <= max_age`
- `users`: `db_min_height_cm <= db_max_height_cm` when both are non-null
- `users`: `db_max_age_diff >= 0` when non-null
- `matches`: terminal metadata should be internally consistent

#### Recommended if dialect portability stays acceptable

- enforce canonical pair ordering on `matches(user_a, user_b)` and `conversations(user_a, user_b)`
- enforce canonical ID content for `matches.id` and `conversations.id`
- add non-blank text checks for `messages.content`, `profile_notes.content`, `notifications.title`, `notifications.message`

### Fix now: normalized dealbreaker tables are still looser than the domain contract

This was underemphasized in the first version of the plan.

The normalized profile child tables are part of the real storage contract, but their value hygiene is still weaker than the domain model expects:

- `DealbreakerAssembler` parses normalized `user_db_*` values through `NormalizedEnumParser`
- `NormalizedEnumParser` silently ignores invalid enum names during compatibility reads
- current normalization tests still save arbitrary raw strings into `user_db_*` tables, which proves those tables are not yet constrained to the canonical enum value set

**Recommendation:** during the reset, treat `user_db_smoking`, `user_db_drinking`, `user_db_wants_kids`, `user_db_looking_for`, and `user_db_education` as cleanup targets, not just copy-as-is tables.

That means:

- normalize preserved `user_db_*` values to the current enum names before reinsertion
- explicitly map or reject legacy variants during the manual migration
- after cleanup, consider adding DB-level checks for these child-table `value` columns too, not just the scalar `users.*` lifestyle columns

**Benefit:** prevents silent loss of dealbreaker intent during hydration and makes the normalized child tables match the real domain contract instead of being a compatibility junk drawer.

### Fix now: naming/index drift

At least one verified index name is misleading:

- `idx_daily_picks_user` is created on `daily_pick_views(user_id)`, not on `daily_picks`

This should be corrected in the new clean schema.

### Fix now: define durable vs disposable data explicitly

For the manual reset, the project should stop treating all tables as equally valuable.

- user-owned domain data should be preserved when useful
- cache, snapshot, TTL, and analytics tables should usually be dropped and regenerated

## Future evolution guardrails (cloud / Docker)

This reset plan is still intentionally **local-first**.

It should **not** try to implement full cloud readiness now, but it also should not make the later move to Docker or managed PostgreSQL harder than necessary.

### Guardrails worth preserving during the reset

#### 1. Treat Dockerized PostgreSQL as the same **direct PostgreSQL** contract

If the app later runs against:

- local installed PostgreSQL
- Dockerized PostgreSQL
- a direct managed PostgreSQL endpoint

the runtime/storage model should stay the same.

The important distinction for future evolution is **direct vs pooled PostgreSQL**, not **local vs Docker vs cloud**.

Practical implication for this reset plan:

- do not invent a Docker-specific storage path
- do not make schema/runtime assumptions that only fit `localhost`
- keep the reset aligned with the current direct PostgreSQL runtime path

#### 2. Keep future room for a direct-vs-pooled split

If later work touches runtime config or connection management, prefer an additive model that can eventually distinguish:

- `local-direct`
- `docker-direct`
- `cloud-direct`
- `cloud-pooled`

without forcing that full abstraction into the reset immediately.

#### 3. Do not silently overload `DATING_APP_DB_PROFILE`

Current code already gives `DATING_APP_DB_PROFILE` / `datingapp.db.profile` legacy H2-oriented meaning in `DatabaseManager` (`dev` / `test` behavior and password fallback rules).

So if future-friendly config work happens during or after the reset:

- either deprecate/rename that flag explicitly
- or introduce a separate runtime-target concept

Do **not** treat the current profile flag as if it is already a safe general-purpose selector for Docker/cloud target kinds.

#### 4. Prefer a future verification model with two modes

The current smoke flow is correct for today:

- repo-local script starts/prepares local PostgreSQL
- smoke test runs against that local target

For future evolution, the reset should avoid making this harder to generalize into:

- **local-managed mode** — current behavior
- **external-target mode** — run the same smoke against any already-running direct PostgreSQL target

That would later cover:

- local installed PostgreSQL
- Dockerized PostgreSQL started separately
- direct cloud PostgreSQL

#### 5. Preserve the new UTC timestamp contract across any future direct target

The reset already treats UTC SQL session handling and shared `Instant` mapping as part of the runtime contract.

That should remain true whether the future direct target is:

- local PostgreSQL
- Dockerized PostgreSQL
- direct cloud PostgreSQL

### What this means for the next implementation task

The next reset implementation does **not** need to solve cloud readiness.

But if the work already touches runtime/config/verification seams, make only the smallest future-friendly moves:

- keep Docker on the same direct PostgreSQL contract
- avoid hard-wiring new localhost-only assumptions into runtime code
- leave room for a future external-target smoke mode
- avoid reusing `DB_PROFILE` as a new target abstraction without an explicit decision

## What is **not** worth fixing in this reset

These may be valid later, but they are not the best next move now:

- extracting verification into a separate `user_verification` table
- converting `notifications.data_json` from `TEXT` to `JSONB`
- introducing PostgreSQL-native enums across the schema
- redesigning conversations into `conversation_participants`
- replacing snapshot tables with event-sourced analytics
- removing H2 support unless the whole repo is intentionally moving to PostgreSQL-only runtime/tests

## Table-by-table reset policy

### Durable identity/profile data

| Table                 | Keep table | Preserve existing rows | Reset notes                                                                                             |
|-----------------------|-----------:|-----------------------:|---------------------------------------------------------------------------------------------------------|
| `users`               |        Yes |                    Yes | New baseline should already be the final column set; do not recreate dropped legacy serialized columns. |
| `user_photos`         |        Yes |                    Yes | Preserve order by `position`.                                                                           |
| `user_interests`      |        Yes |                    Yes | Preserve rows as-is.                                                                                    |
| `user_interested_in`  |        Yes |                    Yes | Preserve rows as-is.                                                                                    |
| `user_db_smoking`     |        Yes |                    Yes | Preserve the intent, but normalize imported `value` strings to the current enum names.                  |
| `user_db_drinking`    |        Yes |                    Yes | Preserve the intent, but normalize imported `value` strings to the current enum names.                  |
| `user_db_wants_kids`  |        Yes |                    Yes | Preserve the intent, but normalize imported `value` strings to the current enum names.                  |
| `user_db_looking_for` |        Yes |                    Yes | Preserve the intent, but normalize imported `value` strings to the current enum names.                  |
| `user_db_education`   |        Yes |                    Yes | Preserve the intent, but normalize imported `value` strings to the current enum names.                  |
| `profile_notes`       |        Yes |                    Yes | Preserve if the existing fake notes matter; otherwise optional.                                         |

### Relationship and messaging graph

| Table             | Keep table | Preserve existing rows | Reset notes                                                                                                                                                                                                |
|-------------------|-----------:|-----------------------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `likes`           |        Yes |               Optional | Preserve only non-deleted rows unless you intentionally want historical soft-deleted fake data. Current runtime upsert behavior can revive pair rows, so deleted likes are not just inert history.         |
| `matches`         |        Yes |               Optional | Preserve active/non-deleted rows by default; preserve terminal history only if it matters. Recompute/check canonical ID on import. Current runtime also has explicit unmatched-pair reactivation behavior. |
| `conversations`   |        Yes |               Optional | Preserve if you want the current fake chat graph. Recompute/check canonical ID on import.                                                                                                                  |
| `messages`        |        Yes |               Optional | Preserve only for preserved conversations.                                                                                                                                                                 |
| `friend_requests` |        Yes |               Optional | Recompute `pair_key` and `pending_marker` on import; do not trust copied helper values blindly.                                                                                                            |
| `blocks`          |        Yes |               Optional | Preserve only if the current fake moderation state matters. Current runtime explicitly revives a deleted pair row on re-block rather than inserting duplicates.                                            |
| `reports`         |        Yes |               Optional | Preserve only if the current fake moderation state matters.                                                                                                                                                |
| `notifications`   |        Yes |             Usually no | Safe to drop in a local reset unless the fake notification history matters.                                                                                                                                |

### Analytics, caches, and disposable state

| Table               | Keep table | Preserve existing rows | Reset notes                                   |
|---------------------|-----------:|-----------------------:|-----------------------------------------------|
| `profile_views`     |        Yes |                     No | Log/analytics table; do not backfill.         |
| `swipe_sessions`    |        Yes |                     No | Disposable session state.                     |
| `user_stats`        |        Yes |                     No | Snapshot/derived table.                       |
| `platform_stats`    |        Yes |                     No | Snapshot/derived table.                       |
| `daily_pick_views`  |        Yes |                     No | Cache/history; safe to drop.                  |
| `daily_picks`       |        Yes |                     No | Cache table; safe to drop.                    |
| `user_achievements` |        Yes |          No by default | Can be regenerated or re-earned in fake data. |
| `standouts`         |        Yes |                     No | Cache/derived discovery table.                |
| `undo_states`       |        Yes |                     No | Ephemeral transactional state.                |

### Metadata

| Table            |                          Keep table | Preserve existing rows | Reset notes                             |
|------------------|------------------------------------:|-----------------------:|-----------------------------------------|
| `schema_version` | Yes, if migration framework remains |                     No | Recreate clean; do not import old rows. |

## Canonical value sets the fresh schema should reflect

All of the following are verified from the domain model and/or V12 checks.

| Field                                                   | Allowed values                                                                             |
|---------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `users.state`                                           | `INCOMPLETE`, `ACTIVE`, `PAUSED`, `BANNED`                                                 |
| `users.gender`                                          | `MALE`, `FEMALE`, `OTHER`                                                                  |
| `users.smoking`                                         | `NEVER`, `SOMETIMES`, `REGULARLY`                                                          |
| `users.drinking`                                        | `NEVER`, `SOCIALLY`, `REGULARLY`                                                           |
| `users.wants_kids`                                      | `NO`, `OPEN`, `SOMEDAY`, `HAS_KIDS`                                                        |
| `users.looking_for`                                     | `CASUAL`, `SHORT_TERM`, `LONG_TERM`, `MARRIAGE`, `UNSURE`                                  |
| `users.education`                                       | `HIGH_SCHOOL`, `SOME_COLLEGE`, `BACHELORS`, `MASTERS`, `PHD`, `TRADE_SCHOOL`, `OTHER`      |
| `users.verification_method`                             | `EMAIL`, `PHONE`                                                                           |
| `users.pace_messaging_frequency`                        | `RARELY`, `OFTEN`, `CONSTANTLY`, `WILDCARD`                                                |
| `users.pace_time_to_first_date`                         | `QUICKLY`, `FEW_DAYS`, `WEEKS`, `MONTHS`, `WILDCARD`                                       |
| `users.pace_communication_style`                        | `TEXT_ONLY`, `VOICE_NOTES`, `VIDEO_CALLS`, `IN_PERSON_ONLY`, `MIX_OF_EVERYTHING`           |
| `users.pace_depth_preference`                           | `SMALL_TALK`, `DEEP_CHAT`, `EXISTENTIAL`, `DEPENDS_ON_VIBE`                                |
| `user_db_smoking.value`                                 | same canonical set as `users.smoking` after cleanup                                        |
| `user_db_drinking.value`                                | same canonical set as `users.drinking` after cleanup                                       |
| `user_db_wants_kids.value`                              | same canonical set as `users.wants_kids` after cleanup                                     |
| `user_db_looking_for.value`                             | same canonical set as `users.looking_for` after cleanup                                    |
| `user_db_education.value`                               | same canonical set as `users.education` after cleanup                                      |
| `likes.direction`                                       | `LIKE`, `SUPER_LIKE`, `PASS`                                                               |
| `matches.state`                                         | `ACTIVE`, `FRIENDS`, `UNMATCHED`, `GRACEFUL_EXIT`, `BLOCKED`                               |
| `matches.end_reason` / `conversations.archive_reason_*` | `FRIEND_ZONE`, `GRACEFUL_EXIT`, `UNMATCH`, `BLOCK`                                         |
| `friend_requests.status`                                | `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED`                                               |
| `notifications.type`                                    | `MATCH_FOUND`, `NEW_MESSAGE`, `FRIEND_REQUEST`, `FRIEND_REQUEST_ACCEPTED`, `GRACEFUL_EXIT` |
| `reports.reason`                                        | `SPAM`, `INAPPROPRIATE_CONTENT`, `HARASSMENT`, `FAKE_PROFILE`, `UNDERAGE`, `OTHER`         |

## Recommended reset strategy

### Core idea

Because the database is tiny and not live, use a **manual synthetic migration** rather than trying to preserve the current migration chain.

### Recommended preserve set

#### Always preserve

- `users`
- all normalized profile child tables
- `profile_notes` if you care about them

#### Preserve only if you want to keep the current fake relationship graph

- `likes`
- `matches`
- `conversations`
- `messages`
- `friend_requests`
- `blocks`
- `reports`

#### Default to dropping/rebuilding

- `profile_views`
- `swipe_sessions`
- `user_stats`
- `platform_stats`
- `daily_pick_views`
- `daily_picks`
- `user_achievements`
- `standouts`
- `undo_states`
- `notifications`
- `schema_version`

## Manual synthetic migration playbook

### Phase 0 — preconditions ✅ completed

- ✅ stop the app / runtime before the real reset
- ✅ confirm the target database is local-only
- ✅ confirm the preserve-set tables above
- ✅ execute the full preserve-set reset path (not profile-data-only)

### Phase 1 — create a safety snapshot ✅ completed

Use one of these approaches:

1. **Preferred:** create backup/staging copies inside PostgreSQL
2. **Alternative:** export the preserve-set tables to CSV/JSON

Because the current dataset is tiny, either is fine.

✅ Implemented with an in-database backup schema snapshot. The completed reset run preserved `reset_backup_20260408_104757`.

### Phase 2 — reset the schema ✅ completed

- drop the current schema contents
- recreate the schema from the new clean baseline
- ensure the fresh schema already matches the intended final post-reset contract

✅ Implemented by dropping/recreating `public`, then bootstrapping the fresh schema through the runtime startup path.

### Phase 3 — reinsert preserved data in dependency order ✅ completed

Recommended order:

1. `users`
2. normalized profile child tables
3. `profile_notes`
4. `likes`
5. `matches`
6. `friend_requests`
7. `conversations`
8. `messages`
9. optional `blocks`, `reports`, `notifications`

✅ Implemented with explicit-column imports in dependency order. The real reset preserved profile data plus the current fake likes/matches graph.

### Phase 4 — recompute helper/canonical values on import ✅ completed

Do **not** blindly trust helper fields copied from the old schema when they can be recomputed.

- recompute/verify `matches.id`
- recompute/verify `conversations.id`
- recompute `friend_requests.pair_key`
- recompute `friend_requests.pending_marker`

Also consider clearing obviously ephemeral verification-attempt state during import:

- `verification_code`
- `verification_sent_at`

while preserving durable verification status if it matters:

- `is_verified`
- `verification_method`
- `verified_at`

Also normalize the preserved child-table dealbreaker values before reinsertion:

- `user_db_smoking.value`
- `user_db_drinking.value`
- `user_db_wants_kids.value`
- `user_db_looking_for.value`
- `user_db_education.value`

Do not rely on compatibility reads silently dropping bad values after the import.

✅ Implemented canonical recomputation for `matches.id`, `conversations.id`, `friend_requests.pair_key`, and `friend_requests.pending_marker`, plus dealbreaker-value normalization during import.

### Phase 5 — deliberately skip disposable tables ✅ completed

Do **not** waste time backfilling:

- session caches
- daily pick caches
- standouts
- snapshot stats
- old schema version rows
- undo state

✅ Implemented by preserving only the defined durable/profile tables plus optional relationship tables; disposable/cache/analytics tables are rebuilt from the fresh schema only.

### Phase 6 — validate the rebuilt database ✅ completed

Minimum validation:

1. schema bootstraps cleanly
2. preserved users load through `UserStorage`
3. normalized profile data round-trips correctly
4. friend-request uniqueness still works
5. optional preserved relationship graph still resolves correctly
6. PostgreSQL smoke test passes

✅ Completed with targeted schema/storage regressions, the reset-script PowerShell regression, a real reset execution, post-reset row-count checks, and the repo PostgreSQL smoke path.

## Recommended implementation boundary

### Boundary for the next task (recommended)

Make the reset a **schema-contract cleanup**, not a domain redesign.

That means the next implementation should mainly touch:

| File                                                                         | Why                                                                                                                |
|------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `src/main/java/datingapp/storage/schema/SchemaInitializer.java`              | Replace the stale frozen baseline with the true current fresh-schema shape.                                        |
| `src/main/java/datingapp/storage/schema/MigrationRunner.java`                | Decide whether to collapse/archive historical repairs, keep only future migrations, or retain compatibility stubs. |
| `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`          | Update the expected fresh-schema contract.                                                                         |
| `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`     | Keep proving legacy serialized columns are gone from the live schema.                                              |
| `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java` | Keep the normalized profile contract green.                                                                        |
| `src/test/java/datingapp/storage/jdbi/FriendRequestUniquenessTest.java`      | Protect the `pair_key` / `pending_marker` behavior.                                                                |
| `src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java`            | Final PostgreSQL verification seam.                                                                                |

Optional additional seams **only if** the reset work already needs to touch runtime/config/verification behavior:

| File                                                   | Why                                                                                                   |
|--------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `src/main/java/datingapp/core/AppConfig.java`          | If the reset opportunistically prepares for future direct-vs-pooled config growth.                    |
| `src/main/java/datingapp/core/AppConfigValidator.java` | Keep any additive storage-config growth validated consistently.                                       |
| `src/main/java/datingapp/core/RuntimeEnvironment.java` | Preserve `.env`-based runtime lookup behavior that also fits future Docker/env-file workflows.        |
| `src/main/java/datingapp/storage/DatabaseManager.java` | Only if connection/profile semantics or verification behavior need small future-proofing adjustments. |
| `.env.example`                                         | Keep local-first defaults while leaving space for future Docker/cloud examples.                       |
| `run_postgresql_smoke.ps1`                             | Only if you intentionally split today’s local-managed smoke from a future external-target mode.       |

### Boundary **not** recommended for the next task

Do not simultaneously redesign these unless the user explicitly wants a larger refactor:

- `User` aggregate decomposition
- notification storage format redesign
- conversation participant model redesign
- analytics table redesign

## Verification path

Use the existing repo verification seams:

1. targeted schema/storage tests
2. PostgreSQL runtime smoke
3. final repo quality gate if the change becomes substantial

Recommended validation commands for the implementation phase:

- `mvn --% -Dcheckstyle.skip=true -Dtest=SchemaInitializerTest,JdbiUserStorageMigrationTest,JdbiUserStorageNormalizationTest,FriendRequestUniquenessTest test`
- `.\run_postgresql_smoke.ps1`
- `mvn spotless:apply verify`

### Completed verification on 2026-04-08

- ✅ `pwsh -NoProfile -ExecutionPolicy Bypass -File src/test/powershell/ResetLocalPostgresScriptTest.ps1`
- ✅ `mvn -Dcheckstyle.skip=false -Dtest=SchemaInitializerTest,MigrationRunnerMetadataTest,JdbiUserStorageMigrationTest,JdbiUserStorageNormalizationTest,FriendRequestUniquenessTest,DatabaseManagerConfigurationTest,PostgresqlSchemaBootstrapSmokeTest test`
- ✅ `pwsh -NoProfile -ExecutionPolicy Bypass -File .\reset_local_postgres.ps1 -ThrowOnFailure`
- ✅ `pwsh -NoProfile -ExecutionPolicy Bypass -File .\run_verify.ps1`

## Practical notes from this investigation

- local PostgreSQL runtime config is verified in `config/app-config.postgresql.local.json`
- the local runtime target is `localhost:55432`, database `datingapp`, username `datingapp`
- `PostgresqlRuntimeSmokeTest` and `run_postgresql_smoke.ps1` already define the main PostgreSQL validation seam
- current runtime code already treats UTC SQL timestamp handling and shared `Instant` mapping as real behavior, so the implementation phase should preserve that explicitly rather than leaving it implicit
- current runtime code does **not** use one universal soft-delete rule; some unique pair rows are explicitly revivable and the reset plan should keep that behavior in mind
- a live row-by-row inspection of the running database was **not** completed in this planning task because the PostgreSQL MCP bridge was unavailable and a local `psql` client was not available in the shell session

This does **not** block the planning recommendation, because the code already makes the final schema contract clear and the user has explicitly confirmed the live data is small, fake, and manually recoverable.

## Bottom line

The best next step is **not** “add another clever migration to the old chain.”

The best next step is:

1. define the clean current PostgreSQL schema that the app actually wants today
2. reset the local database manually
3. preserve only the rows worth keeping
4. keep the current storage/use-case contract stable
5. validate through the existing PostgreSQL smoke path and storage tests

That gives you a cleaner base now without wasting effort on production-grade migration complexity for disposable fake data.

## Evidence index

- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`
- `src/main/java/datingapp/storage/StorageFactory.java`
- `src/main/java/datingapp/storage/DatabaseManager.java`
- `src/main/java/datingapp/storage/schema/SchemaInitializer.java`
- `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- `src/main/java/datingapp/core/AppConfig.java`
- `src/main/java/datingapp/core/RuntimeEnvironment.java`
- `src/main/java/datingapp/core/model/User.java`
- `src/main/java/datingapp/core/model/Match.java`
- `src/main/java/datingapp/core/model/ProfileNote.java`
- `src/main/java/datingapp/core/connection/ConnectionModels.java`
- `src/main/java/datingapp/core/profile/MatchPreferences.java`
- `src/main/java/datingapp/storage/jdbi/NormalizedProfileRepository.java`
- `src/main/java/datingapp/storage/jdbi/NormalizedEnumParser.java`
- `src/main/java/datingapp/storage/jdbi/DealbreakerAssembler.java`
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiTrustSafetyStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiTypeCodecs.java`
- `src/main/java/datingapp/storage/jdbi/JdbiAccountCleanupStorage.java`
- `src/test/java/datingapp/storage/DatabaseManagerConfigurationTest.java`
- `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`
- `src/test/java/datingapp/storage/jdbi/FriendRequestUniquenessTest.java`
- `src/test/java/datingapp/storage/jdbi/RecordBindingTest.java`
- `src/test/java/datingapp/storage/jdbi/SqlRowReadersTest.java`
- `src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java`
- `.env.example`
- `run_postgresql_smoke.ps1`
- `start_local_postgres.ps1`
