# Plan 09B: Migration and Schema Safety

> **Date:** 2026-04-10
> **Wave:** 2
> **Priority:** High
> **Parallel-safe with:** none recommended
> **Must run after:** P09
> **Status:** Planned

---

## Objective

Reduce the chance of getting stranded in a half-migrated or hard-to-recover schema state by isolating migration-engine cleanup, irreversible schema handling, pair-ID invariants (the shared deterministic match/conversation ID length contract), and bootstrap transaction/timeout behavior into one dedicated plan.

## Issues addressed

| Issue ID | Summary                                                                     |
|----------|-----------------------------------------------------------------------------|
| 4.2      | `MigrationRunner` mixes registry, execution, introspection, and DDL helpers |
| 11.7     | V3 schema cleanup is irreversible                                           |
| 16.5     | Pair-ID length is encoded as a magic number                                 |
| 17.10    | Startup migration has weaker timeout and atomicity guarantees               |

## Primary source files and seams

- `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- `src/main/java/datingapp/storage/schema/SchemaInitializer.java`

## Boundary contract

### Primary edit owners

- `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- `src/main/java/datingapp/storage/schema/SchemaInitializer.java`

### Supporting read-only seams

- `src/main/java/datingapp/storage/DatabaseManager.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`

### Escalate instead of expanding scope if

- the change becomes a general `DatabaseManager` lifecycle/configuration redesign
- the change starts requiring broad JDBI adapter cleanup instead of migration-specific updates
- rollback or archival design needs a wider product/data-retention decision

## Primary verification slice

- `src/test/java/datingapp/storage/schema/MigrationRunnerMetadataTest.java`
- `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
- `src/test/java/datingapp/storage/PostgresqlSchemaBootstrapSmokeTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageMigrationTest.java`

## Execution slices

### Slice A — separate migration registry/engine/helper roles

- decompose `MigrationRunner` only as far as needed to make schema changes reviewable and execution logic auditable
- avoid a broad schema framework rewrite

### Slice B — make irreversible cleanup explicit

- decide whether V3 cleanup needs staging, archival, or stronger rollback documentation
- ensure the final plan spells out recovery expectations rather than hiding them in raw DDL

### Slice C — centralize schema invariants

- replace the bare pair-ID length literals with one shared invariant used by schema and dependent storage checks

### Slice D — align bootstrap guarantees with runtime expectations

- improve timeout and transaction/auto-commit clarity for startup migration execution
- keep the boundary between runtime lifecycle (P09) and migration semantics explicit

## Dependencies and orchestration notes

- Run only after P09 stabilizes `DatabaseManager` lifecycle/configuration semantics.
- Treat `MigrationRunner.java` and `SchemaInitializer.java` as single-owner files during execution.
- Use the PostgreSQL bootstrap smoke test as a required gate, not a nice-to-have.

## Out of scope

- general runtime lifecycle/configuration cleanup (P09)
- adapter-level batching/codec cleanup (P10)
- large-file structural cleanup beyond the migration seam itself
