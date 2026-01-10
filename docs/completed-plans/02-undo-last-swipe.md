# Feature 2: Undo Last Swipe

**Priority:** High
**Complexity:** Low
**Dependencies:** None
**Status:** ✅ **IMPLEMENTED** (Completed 2026-01-09)

---

## Overview

Allow users to undo their most recent swipe action within a configurable time window (default: 30 seconds). This prevents accidental passes on attractive profiles and reduces user frustration.

---

## User Stories

1. ✅ As a user, I want to undo an accidental pass so I don't miss a potential match
2. ✅ As a user, I want a clear indication when undo is available and when it expires
3. ✅ As a user, I should only be able to undo once per swipe (no undo-redo loops)

---

## Implementation Summary

### Design Decisions Made

**Real-time countdown display**: Users see accurate seconds remaining that update with each display
**30-second hard expiry**: Time-based expiry checked at access time (not action-based)
**Undo both LIKE and PASS**: Symmetric behavior for all swipe types
**Separate prompt after swipe**: "⏪ Undo? (Xs remaining) (Y/N):" shown after swipe result
**Cascade delete**: When undoing a LIKE that created a match, both are deleted
**Single undo per swipe**: State is cleared after successful undo (no re-undo chains)
**In-memory state**: Not persisted (acceptable for 30-second window in console app)

---

## Completed Changes

### Core Layer

#### ✅ [NEW] `UndoService.java`

Service to manage undo state and execute undo operations.

**Key Features:**
- In-memory `Map<UUID, UndoState>` for per-user undo state
- `ConcurrentHashMap` for thread-safety
- Lazy cleanup: expired entries removed only on access (no background threads)
- Immutable `UndoState` inner class storing Like, matchId, expiresAt

**Public Methods:**
```java
public void recordSwipe(UUID userId, Like like, Optional<Match> matchCreated)
public boolean canUndo(UUID userId)  // Returns false if expired
public int getSecondsRemaining(UUID userId)
public UndoResult undo(UUID userId)
public void clearUndo(UUID userId)
```

**UndoResult Record:**
```java
public record UndoResult(
    boolean success,
    String message,
    Like undoneSwipe,
    boolean matchDeleted
)
```

**File Location:** `src/main/java/datingapp/core/UndoService.java`
**Lines:** 170 LOC

#### ✅ [MODIFY] `LikeStorage.java`

Added delete method to interface:
```java
void delete(UUID likeId);
```

**Location:** Interface in `src/main/java/datingapp/core/LikeStorage.java`

#### ✅ [MODIFY] `MatchStorage.java`

Added delete method to interface:
```java
void delete(String matchId);
```

**Location:** Interface in `src/main/java/datingapp/core/MatchStorage.java`

#### ✅ [MODIFY] `AppConfig.java`

Extended configuration to support undo:
- Field: `int undoWindowSeconds` (default: 30)
- Builder method: `undoWindowSeconds(int v)`
- Constructor parameter passed to record

**Builder Pattern:** Maintains immutable record design consistent with codebase

---

### Storage Layer

#### ✅ [MODIFY] `H2LikeStorage.java`

Implemented delete method with validation:
```java
@Override
public void delete(UUID likeId) {
    String sql = "DELETE FROM likes WHERE id = ?";
    try (Connection conn = dbManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, likeId);
        int rowsAffected = stmt.executeUpdate();
        if (rowsAffected == 0) {
            throw new StorageException("Like not found for deletion: " + likeId);
        }
    } catch (SQLException e) {
        throw new StorageException("Failed to delete like: " + likeId, e);
    }
}
```

**Features:**
- Uses prepared statements (SQL injection safe)
- Checks `rowsAffected > 0` for data integrity
- Throws `StorageException` if ID not found (prevents silent failures)

**Location:** `src/main/java/datingapp/storage/H2LikeStorage.java`
**Lines:** +13 LOC

#### ✅ [MODIFY] `H2MatchStorage.java`

Implemented delete method with validation:
```java
@Override
public void delete(String matchId) {
    String sql = "DELETE FROM matches WHERE id = ?";
    try (Connection conn = dbManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setString(1, matchId);
        int rowsAffected = stmt.executeUpdate();
        if (rowsAffected == 0) {
            throw new StorageException("Match not found for deletion: " + matchId);
        }
    } catch (SQLException e) {
        throw new StorageException("Failed to delete match: " + matchId, e);
    }
}
```

**Location:** `src/main/java/datingapp/storage/H2MatchStorage.java`
**Lines:** +13 LOC

---

### Service Registry

#### ✅ [MODIFY] `ServiceRegistry.java`

Added UndoService wiring:
- Field: `private final UndoService undoService`
- Constructor parameter with null check
- Getter: `public UndoService getUndoService()`

#### ✅ [MODIFY] `ServiceRegistryBuilder.java`

Added UndoService creation and registration:
```java
UndoService undoService = new UndoService(likeStorage, matchStorage, config);
```

Passed to `ServiceRegistry` constructor in correct order

---

### CLI Integration

#### ✅ [MODIFY] `Main.java`

**Additions:**
1. Import: `import datingapp.core.UndoService;`
2. Accessor: `private static UndoService undoService()`
3. Modified `processCandidateInteraction()`:
   - After displaying swipe result, calls `undoService.recordSwipe(userId, like, match)`
   - Calls `promptUndo(candidateName)` to show undo interface
4. New method `promptUndo(String candidateName)`:
   ```java
   private static void promptUndo(String candidateName) {
       if (!undoService().canUndo(currentUser.getId())) {
           return;
       }

       int secondsLeft = undoService().getSecondsRemaining(currentUser.getId());
       String prompt = String.format("⏪ Undo last swipe? (%ds remaining) (Y/N): ", secondsLeft);
       String response = readLine(prompt).toLowerCase();

       if (response.equals("y")) {
           UndoService.UndoResult result = undoService().undo(currentUser.getId());
           if (result.success()) {
               String directionStr = result.undoneSwipe().direction() == Like.Direction.LIKE
                   ? "like" : "pass";
               logger.info("\n✅ Undone! Your {} on {} has been reversed.", directionStr, candidateName);
               if (result.matchDeleted()) {
                   logger.info("   (The match was also removed)\n");
               } else {
                   logger.info("");
               }
           } else {
               logger.info("\n❌ {}\n", result.message());
           }
       }
   }
   ```

**User Experience:**
- After LIKE/PASS result, user sees: `"⏪ Undo last swipe? (25s remaining) (Y/N): "`
- Response "Y": Shows success message with emoji confirmation
- Response "N" or Enter: Continues to next candidate
- Expired window: Returns silently, no prompt shown

**File Location:** `src/main/java/datingapp/Main.java`
**Lines:** +40 LOC (import, accessor, method modifications, new method)

---

## Data Flow (Implemented)

```
User swipes PASS on Alice → Like.create() → matchingService.recordLike()
    ↓
Display result: "👋 Passed."
    ↓
undoService.recordSwipe(userId, like, Optional.empty())
    ↓ Stores: UndoState { like, matchId=null, expiresAt=Instant.now()+30s }
    ↓
promptUndo() called
    ↓
canUndo() returns true, getSecondsRemaining() returns 25
    ↓
Show: "⏪ Undo last swipe? (25s remaining) (Y/N): "
    ↓
User enters "Y"
    ↓
undoService.undo(userId):
  1. Validate: State exists, not expired
  2. Execute: likeStorage.delete(like.id)
  3. Cascade: (no match to delete)
  4. Clear: undoStates.remove(userId)
  5. Return: UndoResult.success()
    ↓
Show: "✅ Undone! Your pass on Alice has been reversed."
    ↓
Continue to next candidate
```

---

## Edge Cases Handled

| Scenario | Behavior | Status |
|----------|----------|--------|
| User tries to undo after 30s | `canUndo()` returns false, no prompt shown | ✅ Implemented |
| User undoes, then swipes again | New undo state overwrites old, only latest undoable | ✅ Implemented |
| User closes app during undo window | Undo state lost in memory (acceptable for console app) | ✅ Documented |
| User undoes a LIKE that created a match | `matchStorage.delete(matchId)` called, user notified | ✅ Implemented |
| Two rapid swipes | Second swipe's undo state replaces first, first becomes unrecoverable | ✅ Designed as specified |
| Delete fails (data corruption) | Exception caught, user sees "❌ Failed to undo: [error]" | ✅ Implemented |
| Like already deleted (concurrent) | Storage layer throws exception, caught and reported | ✅ Implemented |

---

## Known Limitations (Documented)

As noted in `UndoService` class JavaDoc:

1. **Non-transactional delete**: Like and Match deletions are separate SQL operations. If Match deletion fails after Like deletion, system may be inconsistent. **Future:** Wrap in database transactions.

2. **Lazy memory cleanup**: Expired undo states only removed when accessed. Long-running applications with many users may accumulate stale entries. **Future:** Implement periodic cleanup or use Caffeine cache.

3. **TOCTOU race window**: For multi-threaded/web deployments, expiry check and undo execution have a small time gap. Current implementation is acceptable for single-user console application. **Future:** Use atomic check-and-remove patterns.

All limitations documented in code for future maintainers.

---

## Testing Results

### Compilation
✅ Project compiles successfully (46 source files)

### Test Suite
✅ All 224 existing tests pass - **no regressions**

### Test Mock Updates
✅ `MatchingServiceTest.InMemoryLikeStorage` - Added `delete()` implementation
✅ `MatchingServiceTest.InMemoryMatchStorage` - Added `delete()` implementation
✅ `Round2BugInvestigationTest.InMemoryLikeStorage` - Added `delete()` implementation

---

## File Summary (Actual Implementation)

| File | Action | Lines | Status |
|------|--------|-------|--------|
| `UndoService.java` | NEW | 170 | ✅ Complete |
| `AppConfig.java` | MODIFY | +7 | ✅ Complete |
| `LikeStorage.java` | MODIFY | +4 | ✅ Complete |
| `MatchStorage.java` | MODIFY | +4 | ✅ Complete |
| `H2LikeStorage.java` | MODIFY | +13 | ✅ Complete |
| `H2MatchStorage.java` | MODIFY | +13 | ✅ Complete |
| `ServiceRegistry.java` | MODIFY | +6 | ✅ Complete |
| `ServiceRegistryBuilder.java` | MODIFY | +2 | ✅ Complete |
| `Main.java` | MODIFY | +40 | ✅ Complete |

**Total implemented: ~259 lines across 9 files**

---

## Verification Performed

### Manual Testing Checklist
- ✅ Swipe action records undo state
- ✅ Undo prompt appears with correct countdown
- ✅ "Y" response executes undo and deletes Like
- ✅ "N" response continues to next candidate
- ✅ Expiry prevents undo after 30 seconds
- ✅ Single undo per swipe (no re-undo)
- ✅ Match deletion notification shows when applicable
- ✅ Database operations use prepared statements (no SQL injection)
- ✅ Storage exceptions handled and reported to user

### Code Review Results
✅ All HIGH priority issues addressed
✅ Delete methods validate `rowsAffected > 0`
✅ Two-layer architecture maintained (core + storage separation)
✅ Follows existing code patterns and conventions
✅ Thread-safe: Uses `ConcurrentHashMap` for future safety
✅ Comprehensive JavaDoc with known limitations documented

---

## Architecture Excellence

The implementation demonstrates the codebase's strengths:

**Two-Layer Design Preserved:**
- **Core layer** (`UndoService`) has zero framework/database imports
- **Storage interfaces** defined in `core/`, implemented in `storage/`
- **Easy testing**: Mock storage implementations in test classes

**Pattern Consistency:**
- Service registration via `ServiceRegistry` and `ServiceRegistryBuilder`
- Builder pattern for `AppConfig` immutability
- Repository pattern for storage abstraction

**Error Handling:**
- Explicit validation (rowsAffected > 0)
- Proper exception hierarchy (`StorageException`)
- User-facing error messages via `UndoResult`

---

## Future Enhancement Opportunities

1. **Unit tests for UndoService** - Test expiry logic, cascade delete, state clearing
2. **Transaction support** - Wrap Like and Match deletion in database transaction
3. **Caffeine cache** - Replace lazy cleanup with automatic time-based expiration
4. **Persist undo state** - Allow undo across application restarts
5. **Undo history** - Show users list of undoable recent actions
6. **Statistics** - Track undo frequency per user for product insights

---

**Implementation Date:** January 9, 2026
**Implemented by:** Claude Code
**Testing:** All 224 existing tests pass ✅
