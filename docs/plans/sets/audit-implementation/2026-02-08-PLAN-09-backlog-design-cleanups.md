# Plan 09: Backlog - Interface Design and Data Model Cleanups

**Date:** 2026-02-08
**Priority:** LOW (Post-audit backlog)
**Estimated Effort:** 8-14 hours
**Risk Level:** Medium-High (API changes)
**Parallelizable:** NO - coordinate with any active feature work
**Status:** NOT STARTED

---

## Overview

This plan captures the remaining backlog items from the audit that were intentionally deferred. These changes are more invasive (API and data model shifts) and should be scheduled only after P01-P08 are complete.

### Backlog Items Addressed

| ID | Severity | Category | Summary |
|---|---|---|---|
| SQL-006 | MED | Storage Design | CSV serialization limits query capability |
| IF-005 | HIGH | API Design | Optional vs null inconsistency in storage |
| IF-006 to IF-016 | MED/LOW | API Design | Interface cohesion + naming + contracts |
| SQL-012 to SQL-014 | LOW | SQL | Minor inefficiencies |
| Dup-IDs | LOW | Duplication | PairId generation shared helper |
| User Split | LOW | Refactor | User god-class split (risky) |

---

## Files Owned by This Plan (Proposed)

> These are *proposed* targets and may expand once design is chosen.

- `src/main/java/datingapp/core/storage/UserStorage.java`
- `src/main/java/datingapp/core/storage/MatchStorage.java`
- `src/main/java/datingapp/core/storage/StatsStorage.java`
- `src/main/java/datingapp/core/storage/MessagingStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMatchStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiStatsStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiMessagingStorage.java`
- `src/main/java/datingapp/core/User.java`
- `src/main/java/datingapp/core/Match.java`
- `src/main/java/datingapp/core/Messaging.java`
- `src/main/java/datingapp/core/ServiceRegistry.java`
- `src/main/java/datingapp/storage/DatabaseManager.java`
- Various service and UI consumers depending on interface changes

---

## Detailed Tasks

### Task 1: Replace CSV-Serialized Enums With Join Tables (SQL-006)

**Goal:** Enable SQL-level filtering on interests and interested-in.

**Approach:**
1. Add new tables (e.g., `user_interests`, `user_interested_in`) with FK to `users` and enum value columns.
2. Migrate existing CSV data during schema migration.
3. Update JDBI mappers to read/write join tables.
4. Update CandidateFinder / storage queries to leverage SQL filtering.

**Notes:**
- Requires schema migrations and data backfill.
- Consider keeping CSV columns temporarily for backward compatibility.

---

### Task 2: Standardize Optional vs Null in Storage APIs (IF-005)

**Goal:** Make storage interfaces consistent and explicit.

**Steps:**
1. Decide standard: prefer `Optional<T>` for nullable returns.
2. Update `UserStorage.get(UUID)` to return `Optional<User>`.
3. Update all call sites and tests.
4. Consider adding `findById` for clarity and deprecate `get`.

**Risk:** Broad compile impact across services, UI, and tests.

---

### Task 3: Interface Cohesion Cleanup (IF-006 to IF-016)

**Scope:**
- Split mixed concerns in storage interfaces (beyond P05 scope).
- Rename ambiguous methods.
- Reduce default methods with silent no-op behavior.
- Clarify contracts via Javadoc.

**Examples:**
- UserStorage profile notes separation
- SocialStorage responsibilities
- AchievementService dependency count
- RelationshipTransitionService notification coupling

---

### Task 4: Minor SQL Inefficiencies (SQL-012 to SQL-014)

**Goal:** Clean up low-risk inefficiencies flagged in the audit.

**Steps:**
1. Audit the specific queries and document expected improvement.
2. Apply changes if safe; otherwise keep in backlog.

---

### Task 5: Shared PairId Helper (Dup-IDs)

**Goal:** Remove duplicate deterministic ID logic between Match and Messaging.

**Approach:**
- Introduce a small `PairId` helper in `core/util`.
- Update Match and Messaging to use it.

---

### Task 6: Optional User Split (God-class Risk)

**Goal:** Reduce `User` size by extracting profile/preferences/photos.

**Approach:**
- Introduce `UserProfile`, `UserPreferences`, `UserPhotos`.
- Update storage bindings and User API.
- Run only if maintenance cost outweighs refactor risk.

---

## Execution Order

1. Decide interface and storage API standards.
2. Design schema changes for CSV removal.
3. Implement storage interface changes + JDBI updates.
4. Update services, UI, and tests.
5. Apply minor SQL cleanups and shared helpers.

---

## Verification Checklist

- `mvn spotless:apply`
- `mvn verify`
- Data migration tested against a seeded database
- No remaining CSV enum parsing in storages

---

## Dependencies

- Must run after P01-P08.
- Coordinate with any new feature branches touching storage or User model.

---

## Rollback Strategy

1. Revert schema migrations and JDBI mapping changes.
2. Restore CSV columns and enum parsing.
3. Revert interface signature changes and restore Optional/null behavior.

This plan should be executed in one or two large, coordinated commits due to breadth.
