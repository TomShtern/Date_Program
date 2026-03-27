# Implementation Plan: Domain Logic Inconsistencies

**Status:** ✅ **COMPLETED** (2026-03-08)

**Source Report:** `Generated_Report_Generated_By_GLM5_21.02.2026.md` (Findings F-009, F-013, F-017)

## Completion Notes (2026-03-08)

- ✅ `User.getAge()` and `User.getAge(ZoneId)` now return `Optional<Integer>` rather than implicit `0` for missing birth dates.
- ✅ Downstream age-dependent call sites were migrated to explicit handling across core/UI/API layers (matching filters/scoring, projections, and displays).
- ✅ `Match.transitionToFriends(UUID)` now writes audit metadata (`endedAt`, `endedBy`, `endReason=FRIEND_ZONE`).
- ✅ `Match.revertToActive()` now clears stale termination metadata for invariant consistency.
- ✅ Removed `Match.restoreDeletedAt(...)` backdoor; storage reconstitution now uses constructor-provided `deletedAt`.
- ✅ Updated `JdbiMatchmakingStorage` mapper and constructor call sites to rehydrate `deletedAt` directly.

## Verification Executed

- ✅ Updated tests passed: `MatchStateTest`, `SoftDeletableTest`, `UserTest`, `MatchesViewModelTest`
- ✅ Regression tests passed: `DashboardViewModelTest`, `ConnectionServiceTransitionTest`, `JdbiMatchmakingStorageTransitionAtomicityTest`
- ✅ Full quality gate passed: `mvn spotless:apply verify` (BUILD SUCCESS; Checkstyle, Spotless, PMD, JaCoCo all green)

## 1. Goal Description
The core domain models (`User` and `Match`) are designed to be impenetrable state machines. However, several inconsistencies allow invalid states or silent failures:
1. `User.getAge()` returns `0` when `birthDate` is null, silently corrupting matching algorithms downstream instead of failing fast.
2. `Match.transitionToFriends()` moves the match to a terminal-like state but completely fails to set the standard audit tracking fields (`endedAt`, `endedBy`, `endReason`).
3. `Match.restoreDeletedAt()` acts as a public backdoor to mutate the soft-delete timestamp, bypassing the intentional restrictions of `markDeleted()`.

**Objective:**
Enforce strict domain invariants across `User` and `Match` by formalizing age access, mandating audit trails on all relationship terminations, and securing the soft-delete API.

## 2. Proposed Changes

### `datingapp.core.model`

#### [MODIFY] `User.java`
- Change the return type of `public synchronized int getAge()` to `public synchronized Optional<Integer> getAge()`.
  ```java
  public synchronized Optional<Integer> getAge() {
      return getAge(AppConfig.defaults().safety().userTimeZone());
  }
  public synchronized Optional<Integer> getAge(java.time.ZoneId timezone) {
      if (birthDate == null) {
          return Optional.empty();
      }
      return Optional.of(Period.between(birthDate, AppClock.today(timezone)).getYears());
  }
  ```
- Change all downstream call sites (in `CandidateFinder`, `MatchQualityService`, `RecommendationService`, `ProfileService`) to handle this `Optional` properly (e.g., filtering out candidates with empty ages, or defaulting to `0` explicitly if the specific algorithm permits it).

#### [MODIFY] `Match.java`
- Update `transitionToFriends(UUID initiatorId)` to record the audit fields, identical to how `unmatch`, `block`, and `gracefulExit` operate.
  ```java
  public void transitionToFriends(UUID initiatorId) {
      if (isInvalidTransition(this.state, MatchState.FRIENDS)) {
          throw new IllegalStateException("Cannot transition to FRIENDS from " + state);
      }
      if (!involves(initiatorId)) {
          throw new IllegalArgumentException("User is not part of this match");
      }
      this.state = MatchState.FRIENDS;
      this.endedAt = AppClock.now();
      this.endedBy = initiatorId;
      this.endReason = MatchArchiveReason.FRIEND_ZONE;
  }
  ```
- Update `revertToActive()` to clear the audit fields (set them back to `null`) to maintain state consistency during compensating transactions.
- Delete `restoreDeletedAt(Instant deletedAt)`. JDBI mappers (or `StorageBuilder` equivalents) should use reflection, a nested StorageBuilder, or the standard `markDeleted` method to reconstruct entities from the database, rather than exposing a domain backdoor. (Note: `Match` constructor doesn't take `deletedAt`, so we can add it to the full constructor `Match(...)` that `JdbiMatchmakingStorage.Mapper` currently calls).

#### [MODIFY] `JdbiMatchmakingStorage.java` (or wherever `Match` Mapper lives)
- Update the `Mapper` to pass `deletedAt` through the newly expanded `Match` constructor, satisfying the removal of `restoreDeletedAt`.

## 3. Verification Plan

### Automated Tests
1. **Compilation Check:** Run `mvn validate compile` to ensure all classes dependent on `User.getAge()` have correctly migrated to handling `Optional<Integer>`.
2. **Domain Tests:** Add or update tests in `MatchTest.java`:
   - `transitionToFriendsSetsAuditFields()`: Verify `endedAt`, `endedBy`, and `endReason` are correctly populated.
   - `revertToActiveClearsAuditFields()`: Verify the fields are set back to `null`.
3. **Unit Tests:** Add a test in `UserTest.java`:
   - `getAgeReturnsEmptyWhenBirthDateNull()`: Assert Optional.empty() is returned.

### Manual Verification
1. Launch the app via `mvn javafx:run`.
2. Login as a user, go to a Match in the Chat tab, and select "Transition to Friends".
3. Connect to the H2 database (`jdbc:h2:mem:datingapp`) or check the application logs to ensure the `end_reason` and `ended_at` columns for that match ID are populated in the storage layer.
