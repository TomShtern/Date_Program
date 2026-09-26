# Plan 10: JDBI Storage Plumbing, Serialization, and Batching

> **Date:** 2026-04-10
> **Wave:** 2
> **Priority:** Medium
> **Parallel-safe with:** none recommended
> **Must run after:** P09
> **Status:** Planned

---

## Objective

Consolidate storage-adapter concerns that belong specifically to the JDBI layer: dialect propagation, shared JSON mapping policy, batching, and low-level row/codecs allocation behavior.

## Issues addressed

| Issue ID | Summary                                                           |
|----------|-------------------------------------------------------------------|
| 3.3      | Multiple `ObjectMapper` instances duplicate JSON policy ownership |
| 14.1     | Dialect detection is repeated across JDBI storage implementations |
| 18.2     | `saveStandouts()` uses repeated writes instead of batching        |
| 18.5     | `JdbiTypeCodecs` allocates a new UTC calendar per row read        |

## Primary source files and seams

- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiTypeCodecs.java`
- `src/main/java/datingapp/storage/jdbi/SqlDialectSupport.java`
- `src/main/java/datingapp/storage/StorageFactory.java`

## Boundary contract

### Primary edit owners

- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMetricsStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiTypeCodecs.java`
- `src/main/java/datingapp/storage/jdbi/SqlDialectSupport.java`

### Supporting edit owner (narrow wiring only)

- `src/main/java/datingapp/storage/StorageFactory.java` — only to thread resolved dialect or shared codec/wiring behavior through composition

### Escalate instead of expanding scope if

- the change becomes a broader runtime lifecycle/configuration change owned by P09
- the change becomes migration/bootstrap redesign owned by P09B

## Primary verification slice

- `src/test/java/datingapp/storage/jdbi/JdbiMetricsStorageTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiConnectionStorageAtomicityTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiCommunicationStorageSocialTest.java`
- `src/test/java/datingapp/storage/jdbi/SqlRowReadersTest.java`
- `src/test/java/datingapp/core/storage/StorageContractTest.java`

## Execution slices

### Slice A — move shared adapter policy to one owner

- unify notification/object mapping behind a shared codec/helper where that policy truly must stay aligned
- avoid pushing a global mapper through unrelated runtime layers

### Slice B — stop rediscovering the dialect per adapter

- pass the resolved runtime dialect from composition/wiring into the JDBI adapter cluster
- keep SQL divergence centralized in `SqlDialectSupport`

### Slice C — batch writes where storage hot paths justify it

- replace standout row-by-row persistence with a JDBI batch strategy
- keep semantics aligned with the existing storage contract

### Slice D — trim low-level allocation churn

- remove unnecessary per-row UTC calendar allocation if the JDBC contract allows safe reuse
- keep timestamp handling explicitly UTC-safe

## Dependencies and orchestration notes

- Execute this plan only after P09 and P09B define the runtime/bootstrap contract.
- Do not run this plan in parallel with any other plan that changes JDBI storage implementations.

## Out of scope

- schema/bootstrap redesign (P09, P09B)
- future storage-adapter scalability guardrails (P13)