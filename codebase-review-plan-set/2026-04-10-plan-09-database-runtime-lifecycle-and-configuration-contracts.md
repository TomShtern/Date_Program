# Plan 09: Database Runtime Lifecycle and Configuration Contracts

> **Date:** 2026-04-10
> **Wave:** 2
> **Priority:** High
> **Parallel-safe with:** none recommended
> **Must run before:** P09B, P10
> **Status:** Planned

---

## Objective

Make the runtime database lifecycle, configuration precedence, and singleton/pool semantics explicit and predictable before migration or JDBI-plumbing work begins.

## Issues addressed

| Issue ID | Summary                                                              |
|----------|----------------------------------------------------------------------|
| 2.3      | Password resolution is too indirect                                  |
| 11.6     | `DatabaseManager.resetInstance()` resets more than its name promises |
| 13.6     | `configurePoolSettings()` looks dynamic but is startup-only          |
| 14.2     | `DatabaseManager` mixes static-global and instance responsibilities  |
| 16.4     | `DatabaseManager` Javadoc still reads as H2-only                     |

## Primary source files and seams

- `src/main/java/datingapp/storage/DatabaseManager.java`
- `src/main/java/datingapp/storage/DatabaseDialect.java`

## Boundary contract

### Primary edit owners

- `src/main/java/datingapp/storage/DatabaseManager.java`
- `src/main/java/datingapp/storage/DatabaseDialect.java`

### Supporting read-only seams

- `src/main/java/datingapp/storage/StorageFactory.java`
- `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- `src/main/java/datingapp/storage/schema/SchemaInitializer.java`

### Escalate instead of expanding scope if

- the change requires restructuring `MigrationRunner.java` or `SchemaInitializer.java`
- the change requires changing JDBI adapter contracts instead of the runtime manager itself
- the change requires broad `ServiceRegistry` or `ApplicationStartup` restructuring

## Primary verification slice

- `src/test/java/datingapp/storage/DatabaseManagerConfigurationTest.java`
- `src/test/java/datingapp/storage/DatabaseManagerThreadSafetyTest.java`

## Execution slices

### Slice A — define runtime configuration precedence

- collapse password resolution into one obvious precedence chain
- make startup-only versus live-runtime configuration behavior explicit

### Slice B — clarify singleton and pool lifecycle

- separate instance reset from URL-reset semantics if both must exist
- document or enforce when pool settings are allowed to change

### Slice C — update runtime docs to match reality

- document the supported-dialect role of `DatabaseManager`
- make startup-only versus live-runtime behavior explicit in the public contract

## Dependencies and orchestration notes

- Run this plan before P09B and P10 so the runtime contract settles before migration or JDBI cleanup starts.
- Treat `DatabaseManager.java` as the single hot-file owner here.
- For any meaningful runtime/storage change here, use the local PostgreSQL verification helpers and the repo-level verify path.

## Out of scope

- migration/bootstrap safety and irreversible schema cleanup (P09B)
- JDBI batching/codec cleanup (P10)
- REST adapter cleanup (P08)
- future production-adapter guardrail work (P13)