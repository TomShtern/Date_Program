# File Consolidation Plan

> **Generated:** 2026-01-21
> **Status:** ✅ COMPLETED
> **Files Deleted:** 6 files (131 → 125, ~5% reduction)

---

## ✅ Completion Summary (2026-01-21)

| File Deleted | Merged Into | Type |
|--------------|-------------|------|
| `FriendRequestStatus.java` | `FriendRequest.Status` | Nested enum |
| `NotificationType.java` | `Notification.Type` | Nested enum |
| `ArchiveReason.java` | `Match.ArchiveReason` | Nested enum |
| `ViewFactory.java` | `ViewModelFactory` | Nested enum |
| `SkeletonLoaderUtil.java` | `SkeletonLoader` | Static methods |
| `ServiceRegistryBuilder.java` | `ServiceRegistry.Builder` | Nested class |

**Skipped (Architecture Reasons):**
- `TransitionValidationException.java` - Stays in `core/` (domain layer)
- `StorageException.java` - Stays in `storage/` (data layer)
- `GeoUtils.java` - Pure utility used by 4+ classes across packages

---

## Executive Summary

After thorough analysis of the codebase (131 main Java files, 42 test files), the codebase is **already well-structured**. Previous consolidation work has been done (e.g., 4 pace preference enums merged into `PacePreferences.java`).

**Consolidation opportunities are limited but targeted**, focusing on small enum files that can be nested into their parent classes following existing patterns like `Like.Direction`.

---

## Current State Analysis

| Package | File Count | Consolidation Candidates |
|---------|------------|-------------------------|
| `core/` | ~45 files | 5 small enums/exceptions |
| `storage/` | ~20 files | None (clean architecture) |
| `cli/` | ~15 files | None |
| `ui/` | ~30 files | 1 optional (ViewFactory) |
| `test/` | 42 files | None (1 test per class) |

### Files Under 30 Lines (Primary Candidates)
- `FriendRequestStatus.java` - 9 lines
- `NotificationType.java` - 10 lines
- `TransitionValidationException.java` - 8 lines
- `ArchiveReason.java` - 9 lines
- `StorageException.java` - 19 lines
- `ViewFactory.java` - 28 lines (contains only ViewType enum)

---

## Implementation Phases

### Phase 1: Enum Consolidation (Low Risk) 🟢

**Estimated Time:** 30 minutes
**Files Saved:** 2

#### 1.1 Merge `FriendRequestStatus` into `FriendRequest`

**Before:**
```
core/FriendRequest.java (18 lines)
core/FriendRequestStatus.java (9 lines)
```

**After:**
```java
// FriendRequest.java
public record FriendRequest(...) {
    public enum Status { PENDING, ACCEPTED, DECLINED, EXPIRED }
    // ... existing code
}
```

**Rationale:** Follows existing `Like.Direction` nested enum pattern.

**Files to Update:**
- `FriendRequest.java` - Add nested enum
- Delete `FriendRequestStatus.java`
- Update all imports: `FriendRequestStatus` → `FriendRequest.Status`

---

#### 1.2 Merge `NotificationType` into `Notification`

**Before:**
```
core/Notification.java (22 lines)
core/NotificationType.java (10 lines)
```

**After:**
```java
// Notification.java
public record Notification(...) {
    public enum Type { NEW_MATCH, NEW_MESSAGE, PROFILE_VIEW, ... }
    // ... existing code
}
```

**Files to Update:**
- `Notification.java` - Add nested enum
- Delete `NotificationType.java`
- Update all imports: `NotificationType` → `Notification.Type`

---

### Phase 2: Exception Consolidation (Low Risk) 🟢

**Estimated Time:** 20 minutes
**Files Saved:** 2

#### 2.1 Create Unified `Exceptions.java`

**Before:**
```
core/TransitionValidationException.java (8 lines)
storage/StorageException.java (19 lines)
```

**After:**
```java
// core/Exceptions.java
public final class Exceptions {
    private Exceptions() {} // Utility class

    public static class TransitionValidationException extends RuntimeException {
        public TransitionValidationException(String message) { super(message); }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) { super(message, cause); }
    }
}
```

**Files to Update:**
- Create `core/Exceptions.java`
- Delete `TransitionValidationException.java`
- Delete `StorageException.java`
- Update all imports and usages

---

### Phase 3: Archive Reason Merge (Medium Risk) 🟡

**Estimated Time:** 15 minutes
**Files Saved:** 1

#### 3.1 Merge `ArchiveReason` into `Conversation` or `Match`

**Before:**
```
core/ArchiveReason.java (9 lines)
```

**After:**
```java
// In Conversation.java or Match.java (whichever uses it more)
public enum ArchiveReason { MUTUAL_UNMATCH, BLOCKED, REPORTED, ... }
```

**Risk:** Need to verify which class is the primary consumer.

---

### Phase 4: UI Consolidation (Optional) 🟡

**Estimated Time:** 10 minutes
**Files Saved:** 1

#### 4.1 Merge `ViewFactory` into `NavigationService`

`ViewFactory.java` only contains a `ViewType` enum. This can be moved into `NavigationService` which manages view navigation.

**Skip if:** ViewFactory has additional responsibilities planned.

---

## Files NOT Recommended for Consolidation

### Storage Interfaces (16 files) ❌
```
UserStorage, MatchStorage, LikeStorage, BlockStorage,
ReportStorage, ConversationStorage, MessageStorage, etc.
```
**Reason:** Clean architecture pattern - each interface defines a clear contract. Merging would violate Single Responsibility Principle.

### Test Files ❌
**Reason:** One test class per production class is best practice for maintainability.

### UI Session Classes ❌
```
UserSession.java (CLI - plain Java)
UserSessionFx.java (JavaFX - reactive)
```
**Reason:** Different frameworks require different implementations.

---

## Verification Checklist

After **each phase**:

```bash
# 1. Format code
mvn spotless:apply

# 2. Compile
mvn compile

# 3. Run all tests
mvn test

# 4. Full verification
mvn verify

# 5. If all pass, commit
git add -A && git commit -m "Phase X: [description]"
```

---

## Rollback Plan

Each phase is a single commit. To rollback:

```bash
# View recent commits
git log --oneline -5

# Revert specific phase
git revert <commit-hash>
```

---

## Summary

| Phase | Description | Files Saved | Risk |
|-------|-------------|-------------|------|
| 1 | Enum consolidation | 2 | 🟢 Low |
| 2 | Exception consolidation | 2 | 🟢 Low |
| 3 | ArchiveReason merge | 1 | 🟡 Medium |
| 4 | ViewFactory merge | 1 | 🟡 Optional |
| **Total** | | **5-6 files** | |

### Key Insight

The codebase is already well-organized. The consolidation opportunities are **surgical and targeted** rather than sweeping changes. This is a sign of good architecture.

---

## Ready to Implement?

Proceed with Phase 1 first. Each phase should be verified independently before moving to the next.
