# Feature 2: Undo Last Swipe

**Priority:** High
**Complexity:** Low
**Dependencies:** None

---

## Overview

Allow users to undo their most recent swipe action within a configurable time window (default: 30 seconds) or before their next swipe. This prevents accidental passes on attractive profiles and reduces user frustration.

---

## User Stories

1. As a user, I want to undo an accidental pass so I don't miss a potential match
2. As a user, I want a clear indication when undo is available and when it expires
3. As a user, I should only be able to undo once per swipe (no undo-redo loops)

---

## Proposed Changes

### Core Layer

#### [NEW] `UndoService.java`
New service to manage undo state and execute undo operations.

```java
public class UndoService {
    private final LikeStorage likeStorage;
    private final AppConfig config;

    // In-memory state (per-user, not persisted)
    private final Map<UUID, UndoState> undoStates = new ConcurrentHashMap<>();

    public record UndoState(
        Like lastLike,
        Instant swipedAt,
        boolean undoUsed
    ) {}

    public void recordSwipe(Like like);        // Called after each swipe
    public boolean canUndo(UUID userId);       // Check if undo available
    public Instant getUndoExpiresAt(UUID userId);
    public boolean undo(UUID userId);          // Execute undo
    public void clearUndoState(UUID userId);   // Called on new swipe or expiry
}
```

#### [MODIFY] `LikeStorage.java`
Add method to delete a like.

```java
// Add to interface
void delete(UUID likeId);
boolean deleteByUsers(UUID whoLikes, UUID whoGotLiked);
```

#### [MODIFY] `AppConfig.java`
Add undo configuration.

```java
// Add fields
private Duration undoWindow = Duration.ofSeconds(30);
private boolean undoEnabled = true;

// Add getters
public Duration getUndoWindow();
public boolean isUndoEnabled();
```

---

### Storage Layer

#### [MODIFY] `H2LikeStorage.java`
Implement delete method.

```java
@Override
public boolean deleteByUsers(UUID whoLikes, UUID whoGotLiked) {
    String sql = "DELETE FROM likes WHERE who_likes = ? AND who_got_liked = ?";
    // Execute and return affected rows > 0
}
```

---

### CLI Changes

#### [MODIFY] `Main.java`

After each swipe in `processCandidateInteraction()`:
1. Show undo prompt: `"[U]ndo (25s) / [L]ike / [P]ass / [Q]uit: "`
2. Track countdown visually: `"[U]ndo (10s)..."`
3. Handle "u" input to execute undo

```java
// In browse loop, after recording a swipe:
if (undoService.canUndo(currentUser.getId())) {
    String undoPrompt = String.format("  [U]ndo (%ds left) ",
        undoService.getSecondsRemaining(currentUser.getId()));
    // Show in next candidate prompt
}
```

---

## Data Flow

```
User swipes PASS on Alice
    ↓
Like saved to DB
    ↓
UndoService.recordSwipe(like) → stores in undoStates
    ↓
User sees next candidate with "[U]ndo (25s)" option
    ↓
User presses 'u' within 30 seconds
    ↓
UndoService.undo(userId):
  1. Validates canUndo() == true
  2. Calls likeStorage.deleteByUsers(...)
  3. Clears undoState
  4. Returns true
    ↓
User sees Alice again (moves to front of queue)
```

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| User tries to undo after 30s | "Undo expired" message, proceed normally |
| User undoes, then swipes again | Cannot undo the undo (single undo only) |
| User closes app during undo window | Undo state lost (acceptable for console app) |
| User undoes a LIKE that created a match | Match is also deleted (cascading cleanup) |
| Two rapid swipes | First swipe's undo expires, only latest is undoable |

---

## Match Undo Handling

If the undone swipe was a LIKE that created a match:
1. Delete the Like
2. Also delete the Match via `matchStorage.delete(matchId)`
3. Notify user: "Match with Alice was also undone"

Add to `MatchStorage.java`:
```java
void delete(String matchId);
```

---

## Service Registration

#### [MODIFY] `ServiceRegistry.java`
```java
private final UndoService undoService;
public UndoService getUndoService();
```

#### [MODIFY] `ServiceRegistryBuilder.java`
```java
UndoService undoService = new UndoService(likeStorage, matchStorage, config);
```

---

## Verification Plan

### Unit Tests

Create `UndoServiceTest.java`:
- `canUndo_afterSwipe_returnsTrue`
- `canUndo_afterWindowExpires_returnsFalse`
- `canUndo_afterAlreadyUndone_returnsFalse`
- `undo_deletesLike_returnsTrue`
- `undo_whenMatchExists_deletesMatch`
- `undo_whenExpired_returnsFalse`

### Integration Test

Add to existing test suite:
```bash
mvn test -Dtest=UndoServiceTest
```

### Manual CLI Test

1. Start app: `mvn exec:java`
2. Select/create active user
3. Browse candidates
4. PASS on first candidate
5. Immediately press 'u' - should see "Undo successful, [Name] will appear again"
6. Wait 35 seconds, try 'u' - should see "Undo expired"

---

## File Summary

| File | Action | Lines |
|------|--------|-------|
| `UndoService.java` | NEW | ~80 |
| `LikeStorage.java` | MODIFY | +3 |
| `H2LikeStorage.java` | MODIFY | +15 |
| `MatchStorage.java` | MODIFY | +2 |
| `H2MatchStorage.java` | MODIFY | +10 |
| `AppConfig.java` | MODIFY | +10 |
| `ServiceRegistry.java` | MODIFY | +5 |
| `ServiceRegistryBuilder.java` | MODIFY | +3 |
| `Main.java` | MODIFY | +30 |
| `UndoServiceTest.java` | NEW | ~100 |

**Total estimated: ~260 lines**
