# Implementation Plan: Data Consistency (Cross-Storage Transactions)

**Status:** ✅ **COMPLETED** (2026-03-08)

**Source Report:** `Generated_Report_Generated_By_StepFun-Step-3.5-Flash_21.02.2026.md` (Finding F-001)

## Completion Notes (2026-03-08)

- ✅ Cross-storage relationship transition writes are implemented atomically in `JdbiMatchmakingStorage` via `jdbi.inTransaction(...)`:
    - `acceptFriendZoneTransition(...)`
    - `gracefulExitTransition(...)`
    - `unmatchTransition(...)`
- ✅ `ConnectionService` now prefers atomic transition paths whenever `InteractionStorage.supportsAtomicRelationshipTransitions()` is true, avoiding partial direct writes in the JDBI path.
- ✅ Added JDBI integration rollback coverage in `JdbiMatchmakingStorageTransitionAtomicityTest` to prove transaction rollback when downstream writes fail mid-transition.
- ✅ Existing service-level atomic preference coverage remains in `ConnectionServiceTransitionTest`.

## 1. Goal Description
Operations that involve relationship transitions (such as `acceptFriendZone`, `gracefulExit`, and `unmatch` within `ConnectionService`) currently mutate state across two separate storage boundaries: `InteractionStorage` and `CommunicationStorage`. When an operation completes partially (e.g., updating a match succeeds but updating the associated conversation or notification fails), the application attempts to issue a non-atomic compensating write (`match.revertToActive()`).
If this compensating write fails, the database is left in a corrupted state (e.g., a match in the `FRIENDS` state but a friend request still `PENDING`).

**Objective:**
Establish proper transactional boundaries using JDBI's cross-DAO transaction capabilities to ensure that multi-table operations spanning interactions and communications are strictly atomic.

## 2. Proposed Changes

We will introduce a `TransactionCoordinator` (or implement `Jdbi` directly into a specialized Cross-Storage executor) that executes both DAO updates within a single DB transaction. Since both storages share the same underlying H2 database connection pool (via Jdbi), this is straightforward to implement natively.

### `datingapp.core.storage`

#### [NEW] `TransactionCoordinator.java`
- Define an interface `TransactionCoordinator` with a method `<T> T inTransaction(java.util.function.Supplier<T> operation);` or similar, allowing services to wrap multi-storage updates.
*(Alternative simplicity approach)*: Avoid an entirely new abstraction by passing a transaction context down, but that breaks interface separation. Best approach: Add `runInTransaction(Runnable r)` to a centralized manager, or have `InteractionStorage` and `CommunicationStorage` share a common transaction executor service injected at `StorageFactory`.

#### [MODIFY] `InteractionStorage.java` & `CommunicationStorage.java`
- We will leverage the existing implementations (`JdbiMatchmakingStorage` and `JdbiConnectionStorage`). Since they use the same `Jdbi` instance inside `StorageFactory.buildH2()`, we can inject a lightweight `TransactionManager` utilizing `jdbi.inTransaction()` to ensure `handle` reuse across DAO invocations if properly configured using the ThreadLocal scoping (JDBI handles this natively with correct configuration).
- Simpler approach for *this specific codebase*: Create a `RelationshipTransitionManager` component that orchestrates these cross-storage operations using a single `Jdbi` handle, taking it out of `ConnectionService` where it doesn't have DB-level access.

### `datingapp.storage.jdbi`

#### [NEW] `JdbiTransactionManager.java`
- Inject the system `Jdbi` component.
- Implements `TransactionManager`. Wraps execution of runnables in `jdbi.useTransaction(handle -> { ... })`.

### `datingapp.core.connection`

#### [MODIFY] `ConnectionService.java`
- Replace the fragile try-catch block containing compensating writes (e.g., lines 322-333 for friend zoning):
```java
// Current flawed logic
match.transitionToFriends();
interactionStorage.update(match);
try {
    communicationStorage.updateFriendRequest(updated);
} catch (Exception e) {
    match.revertToActive(); // NOT ATOMIC!
    interactionStorage.update(match);
}

// Proposed logic
transactionManager.runInTransaction(() -> {
    interactionStorage.update(match);
    communicationStorage.updateFriendRequest(updated);
    communicationStorage.saveNotification(acceptedNotification);
});
```
- Apply this pattern to `acceptFriendZone()`, `gracefulExit()`, and `unmatch()`.

## 3. Verification Plan

### Automated Tests
- Create an integration test `ConnectionServiceTransactionTest.java` targeting `acceptFriendZone`.
- Introduce a mock or simulated failure (e.g., wrapping `communicationStorage` with a proxy that throws a `RuntimeException` when `updateFriendRequest` is called).
- Execute the transaction block. Check the database to guarantee that the `Match` state was fully rolled back to `ACTIVE` automatically by the JDBI transaction fail-safe, demonstrating transaction integrity without manual compensating writes.

### Verification Executed

- ✅ Added and passed: `JdbiMatchmakingStorageTransitionAtomicityTest`
    - `acceptFriendZoneTransitionRollsBackMatchUpdateWhenRequestUpdateFails`
    - `gracefulExitTransitionRollsBackMatchUpdateWhenConversationArchiveFails`
    - `unmatchTransitionRollsBackMatchUpdateWhenConversationArchiveFails`
- ✅ Reconfirmed: `ConnectionServiceTransitionTest`
- ✅ Full gate passed: `mvn spotless:apply verify` (BUILD SUCCESS; tests/checkstyle/PMD/JaCoCo all green)

### Manual Verification
- No visual GUI test needed for the internal edge case rollbacks. Code review guarantees correctness because the DB engine strictly respects the transaction boundaries once JDBI correctly wraps the call.
