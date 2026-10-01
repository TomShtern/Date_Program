# Storage Module Overview

> Verified against `src/main/java/datingapp/storage` (2026-09-27).
> Package-level snapshot only — list the subpackage directory for the current files.

## Package purpose

`datingapp.storage` implements the storage interfaces in
`datingapp.core.storage` over PostgreSQL (runtime) and H2 (compat/test),
using JDBI plus HikariCP pooling. Schema is code-owned and idempotent
(`IF NOT EXISTS`); fresh installs land on `SchemaInitializer`, older
databases upgrade via `MigrationRunner`.

## Layout

```text
storage/
  DatabaseDialect.java        # POSTGRESQL/H2 detection (fromJdbcUrl/fromConfig)
  DatabaseManager.java        # HikariCP pool + lifecycle; nested StorageException (RuntimeException);
                              # per-connection session setup (PG: search_path public, TIME ZONE UTC, statement_timeout;
                              # H2: TIME ZONE UTC + QUERY_TIMEOUT); schema via MigrationRunner
  DevDataSeeder.java          # env-gated (DATING_APP_SEED_DATA=true), idempotent seed data
  StorageFactory.java         # buildSqlDatabase(...) = runtime; buildH2(...)/buildInMemory(...) = compat/test
  jdbi/
    JdbiUserStorage.java            # OperationalUserStorage
    JdbiMatchmakingStorage.java     # OperationalInteractionStorage (atomic like→match, unmatch/block transitions)
    JdbiConnectionStorage.java      # OperationalCommunicationStorage (conversations/messages)
    JdbiMetricsStorage.java         # AnalyticsStorage + Standout.Storage (incl. SwipeSessionMapper → metrics Session)
    JdbiTrustSafetyStorage.java     # TrustSafetyStorage (blocks/reports)
    JdbiAuthStorage.java            # AuthStorage (clerk_identities: Clerk user id -> local user UUID)
    JdbiAccountCleanupStorage.java  # AccountCleanupStorage (transactional soft-delete graph)
    DealbreakerAssembler.java       # dealbreaker query assembly
    JdbiNotificationJson.java       # notification JSON codec
    JdbiTypeCodecs.java             # Instant codec + EnumSetSqlCodec (EnumSetArgumentFactory, InterestColumnMapper)
    NormalizedEnumParser.java / NormalizedProfileHydrator.java / NormalizedProfileRepository.java
    SqlDialectSupport.java          # dialect detection for StorageFactory
  schema/
    SchemaInitializer.java    # createAllTables: users → auth → likes/matches/swipe_sessions → stats →
                              # daily_picks/views → achievements → messaging/social/moderation/profile/standouts/undo/normalized
    MigrationRunner.java      # pending-migration runner for existing databases
```

## Patterns

- JDBI is used as concrete implementation classes over injected `Jdbi`
  handles (not one-interface-per-table `@SqlObject` declarations).
- Record-typed parameters bind with `@BindMethods`, not `@BindBean`
  (records have no bean accessors); entity classes such as `Match` /
  `Conversation` keep `@BindBean`.
- Errors surface as `DatabaseManager.StorageException` (unchecked), not a
  standalone checked `StorageException`.
- There is no `storage/mapper/MapperHelper`, `UserBindingHelper`,
  standalone `EnumSetArgumentFactory`/`EnumSetColumnMapper`, or
  `JdbiUserStorageAdapter` in current source — enum-set binding lives in
  `JdbiTypeCodecs.EnumSetSqlCodec`.

## Schema (from `SchemaInitializer.createAllTables`)

`CREATE TABLE IF NOT EXISTS` tables: `users`, `clerk_identities`, `likes`, `matches`, `swipe_sessions`, `user_stats`,
`platform_stats`, `daily_picks`, `daily_pick_views`, `user_achievements`,
`conversations`, `messages`, `friend_requests`, `notifications`, `blocks`,
`reports`, `profile_notes`, `profile_views`, `standouts`, `user_photos`,
`user_interests`, `user_interested_in`, `user_db_{smoking,drinking,wants_kids,looking_for,education}`,
`undo_states`.

Key constraints (verified): `likes` has FKs to `users` + `uk_likes`
pair uniqueness + direction/distinct-user checks; `matches` has FKs to
`users` + `uk_matches` pair uniqueness + state/end-reason/73-char pair-ID
checks; `users` carries gender/lifestyle/verification/pace value checks
plus unique email/phone.

## Wiring

```java
ServiceRegistry services = StorageFactory.buildSqlDatabase(dbManager, config); // runtime
ServiceRegistry compat = StorageFactory.buildH2(dbManager, config);            // compat/test
```

`StorageFactory` configures storage + pool + query timeout from
`AppConfig.storage()`, creates one shared `Jdbi` (with `SqlObjectPlugin`
and the type codecs), detects the dialect via `SqlDialectSupport`, builds
the persistence components, registers the event handlers, and assembles
the `ServiceRegistry`. Never construct the graph inside a controller —
`ServiceRegistry` (app-wide) and `ViewModelFactory` (JavaFX) are the
composition roots.
