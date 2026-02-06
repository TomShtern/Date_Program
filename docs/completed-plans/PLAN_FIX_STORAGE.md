# Implementation Plan — Storage & Database Fixes (PLAN_FIX_STORAGE)

**Generated:** 2026-02-06
**Source Audit:** `AUDIT_PROBLEMS_STORAGE.md`
**Scope:** 14 findings (H-11, H-16, M-10, M-11, M-20, M-24, M-28, M-29, L-05, ST-01 through ST-05)
**Estimated Effort:** ~12-16 hours total

---

## Pre-Implementation Checklist

- [ ] Run `mvn test` and confirm all 736 tests pass (baseline)
- [ ] Run `mvn spotless:apply && mvn verify` (baseline formatting)
- [ ] Create a git branch: `fix/storage-audit-fixes`

---

## Fix 1: H-16 — `JdbiUserStorage.readEnumSet()` Missing Try-Catch on `Enum.valueOf` (DONE ✅)

**Severity:** High
**File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
**Lines:** 284-296
**Risk:** A single corrupted or legacy enum value in the DB crashes the entire user deserialization

### Problem

The `readEnumSet()` private method (used for Dealbreaker enum sets) calls `Enum.valueOf(enumClass, s)` without error handling. Contrast with `readInterestSet()` at lines 223-242 which correctly wraps `Interest.valueOf(trimmed)` in a try-catch.

```java
// CURRENT (line 284-296) — BROKEN
private <E extends Enum<E>> Set<E> readEnumSet(ResultSet rs, String column, Class<E> enumClass)
        throws SQLException {
    String csv = rs.getString(column);
    if (csv == null || csv.isBlank()) {
        return new HashSet<>();
    }
    return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> Enum.valueOf(enumClass, s))  // CRASHES on invalid values
            .collect(Collectors.toSet());
}
```

### Fix

Add try-catch around `Enum.valueOf`, log and skip invalid values. Also change `HashSet` to `EnumSet` per project conventions (see M-13 in UI audit — project rule: use `EnumSet` for enum collections).

```java
// FIXED — matches the pattern from readInterestSet()
private <E extends Enum<E>> Set<E> readEnumSet(ResultSet rs, String column, Class<E> enumClass)
        throws SQLException {
    String csv = rs.getString(column);
    if (csv == null || csv.isBlank()) {
        return EnumSet.noneOf(enumClass);
    }
    Set<E> result = EnumSet.noneOf(enumClass);
    for (String s : csv.split(",")) {
        String trimmed = s.trim();
        if (!trimmed.isEmpty()) {
            try {
                result.add(Enum.valueOf(enumClass, trimmed));
            } catch (IllegalArgumentException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping invalid {} value '{}' from database",
                            enumClass.getSimpleName(), trimmed, e);
                }
            }
        }
    }
    return result;
}
```

### Also Fix: Empty set return type

On line 289, the empty branch returns `new HashSet<>()`. Change to `EnumSet.noneOf(enumClass)`.

### Test

1. Write a unit test in `JdbiUserStorageTest` (or create one) that:
   - Inserts a user with a corrupted `db_smoking` value like `"SOCIAL,INVALID_VALUE,NEVER"`
   - Reads the user back
   - Asserts `dealbreakers.smoking()` contains `{SOCIAL, NEVER}` (the valid ones)
   - Assert no exception thrown
2. Existing tests must still pass — `mvn test`

### Notes
- ST-04 from the audit is the same finding as H-16 (duplicate). This fix covers both.

---

## Fix 2: M-11 — `MapperHelper.readEnum()` Crashes on Invalid Enum Values (DONE ✅)

**Severity:** Medium
**File:** `src/main/java/datingapp/storage/mapper/MapperHelper.java`
**Lines:** 62-65
**Risk:** Any corrupted or legacy single-enum column crashes the entire row mapper

### Problem

```java
// CURRENT (line 62-65)
public static <E extends Enum<E>> E readEnum(ResultSet rs, String column, Class<E> enumType) throws SQLException {
    String value = rs.getString(column);
    return value == null ? null : Enum.valueOf(enumType, value);
}
```

`Enum.valueOf` throws `IllegalArgumentException` if the value doesn't match any constant. This is called from:
- `JdbiUserStorage.Mapper` lines 166, 172, 175-179, 184
- `JdbiUserStorage.Mapper.readPacePreferences()` lines 249-256
- `JdbiMatchStorage.Mapper` lines 82, 85
- `JdbiMessagingStorage.ConversationMapper` line 204

A single corrupted enum value in **any** of these columns kills the entire query.

### Fix

Add try-catch, log warning, return `null` for invalid values. Add a `Logger` to MapperHelper.

```java
// FIXED
private static final Logger logger = LoggerFactory.getLogger(MapperHelper.class);

public static <E extends Enum<E>> E readEnum(ResultSet rs, String column, Class<E> enumType) throws SQLException {
    String value = rs.getString(column);
    if (value == null) {
        return null;
    }
    try {
        return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException e) {
        if (logger.isDebugEnabled()) {
            logger.debug("Skipping invalid {} value '{}' in column '{}'",
                    enumType.getSimpleName(), value, column, e);
        }
        return null;
    }
}
```

### Required Import Addition

At the top of `MapperHelper.java`, add:
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

### Test

1. In a new `MapperHelperTest` class (or add to existing), mock a `ResultSet` that returns `"INVALID_VALUE"` for a column
2. Call `MapperHelper.readEnum(rs, "column", SomeEnum.class)`
3. Assert it returns `null` (not throws)
4. All existing tests pass

### Downstream Impact
- Callers already handle `null` from `readEnum` (they pass it to `StorageBuilder` methods that accept nullable). No caller changes needed.

---

## Fix 3: M-10 — `JdbiUserStorage.Mapper.readPhotoUrls()` Returns Fixed-Size List (DONE ✅)

**Severity:** Medium
**File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
**Lines:** 213-220
**Risk:** Any code calling `.add()` on the returned photo URL list gets `UnsupportedOperationException`

### Problem

```java
// CURRENT (line 213-220)
private List<String> readPhotoUrls(ResultSet rs, String column) throws SQLException {
    String csv = rs.getString(column);
    if (csv == null || csv.isBlank()) {
        return Collections.emptyList();  // Also immutable!
    }
    return Arrays.asList(csv.split("\\|"));  // Fixed-size list
}
```

`Arrays.asList(...)` returns a fixed-size list backed by the array — `.add()` and `.remove()` throw `UnsupportedOperationException`. Similarly, `Collections.emptyList()` is immutable.

### Fix

Wrap in `new ArrayList<>(...)` for both branches.

```java
// FIXED
private List<String> readPhotoUrls(ResultSet rs, String column) throws SQLException {
    String csv = rs.getString(column);
    if (csv == null || csv.isBlank()) {
        return new ArrayList<>();
    }
    return new ArrayList<>(Arrays.asList(csv.split("\\|")));
}
```

### Test

1. Save a user with photo URLs `["a.jpg", "b.jpg"]` via `userStorage.save(user)`
2. Read user back via `userStorage.get(id)`
3. Call `user.getPhotoUrls().add("c.jpg")` — should NOT throw
4. Also test: save a user with no photos, read back, call `.add("x.jpg")` — should NOT throw

---

## Fix 4: M-24 — `MapperHelper.readCsvAsList()` Doesn't Trim Entries (DONE ✅)

**Severity:** Medium
**File:** `src/main/java/datingapp/storage/mapper/MapperHelper.java`
**Lines:** 89-95
**Risk:** Stored values like `"A, B, C"` produce `["A", " B", " C"]` with leading spaces

### Problem

```java
// CURRENT (line 89-95)
public static List<String> readCsvAsList(ResultSet rs, String column) throws SQLException {
    String csv = rs.getString(column);
    if (csv == null || csv.isBlank()) {
        return Collections.emptyList();
    }
    return Arrays.asList(csv.split(","));  // No trimming + fixed-size list!
}
```

Two issues:
1. No trimming of whitespace around commas
2. Returns a fixed-size list (same as M-10 above)

### Fix

```java
// FIXED
public static List<String> readCsvAsList(ResultSet rs, String column) throws SQLException {
    String csv = rs.getString(column);
    if (csv == null || csv.isBlank()) {
        return new ArrayList<>();
    }
    return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(ArrayList::new));
}
```

### Required Import Addition

At the top of `MapperHelper.java`, add:
```java
import java.util.ArrayList;
import java.util.stream.Collectors;
```

Remove unused:
```java
// Remove if no longer used after this fix
import java.util.Collections;
```

Actually, check if `Collections` is still used elsewhere in the file. Looking at the code: `readCsvAsList` is the only user of `Collections.emptyList()`. After the fix, the import can be removed if unused.

### Test

1. Mock a `ResultSet` returning `"A, B, C"` (spaces after commas)
2. Call `MapperHelper.readCsvAsList(rs, "col")`
3. Assert result is `["A", "B", "C"]` (trimmed)
4. Assert result allows `.add("D")` (mutable)
5. Test empty/null column returns empty mutable list

---

## Fix 5: M-20 — `DatabaseManager` Static Mutable State Without Synchronization (DONE ✅)

**Severity:** Medium
**File:** `src/main/java/datingapp/storage/DatabaseManager.java`
**Lines:** 15, 59-60
**Risk:** Race condition if `setJdbcUrl()` is called while `getConnection()` reads `jdbcUrl`

### Problem

```java
// CURRENT (line 15)
private static String jdbcUrl = "jdbc:h2:./data/dating";

// CURRENT (line 59-60)
public static void setJdbcUrl(String url) {
    jdbcUrl = url;
}
```

`jdbcUrl` is a plain `String` field read from `getConnection()` (line 83) and `getPassword()` (line 35-36) and `initializeSchema()` (line 92) without synchronization. `setJdbcUrl()` writes without synchronization. If one thread sets the URL while another is connecting, the connection could use a half-visible URL.

### Fix

Use `volatile` keyword (simplest fix that guarantees visibility).

```java
// FIXED (line 15)
private static volatile String jdbcUrl = "jdbc:h2:./data/dating";
```

No other changes needed. `volatile` ensures any write to `jdbcUrl` is visible to all threads immediately.

Also add `Objects.requireNonNull` validation:

```java
// FIXED (line 59-60)
public static void setJdbcUrl(String url) {
    Objects.requireNonNull(url, "JDBC URL cannot be null");
    jdbcUrl = url;
}
```

### Also Fix: `resetInstance()` Needs Synchronization

```java
// CURRENT (line 63-65)
public static void resetInstance() {
    instance = null;
}
```

This races with `getInstance()`. Fix:
```java
public static synchronized void resetInstance() {
    instance = null;
}
```

Or alternatively make `instance` volatile too:
```java
private static volatile DatabaseManager instance;
```

But since `getInstance()` is already synchronized, the simplest safe fix is to make `resetInstance()` synchronized too.

### Test

1. Existing tests pass (this is a safety improvement, not behavioral change)
2. Optionally: concurrent test with two threads — one calling `setJdbcUrl()`, other calling `getConnection()` — verify no NPE

---

## Fix 6: H-11 — N+1 Query Patterns: Add `UserStorage.findByIds()` (DONE ✅)

**Severity:** High
**File 1:** `src/main/java/datingapp/core/storage/UserStorage.java`
**File 2:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
**File 3:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorageAdapter.java`
**Consumers:** `MatchingService`, `StandoutsService`, `MessagingService`, `MatchesViewModel`
**Risk:** 50+ separate DB round-trips where 1 would suffice

### Problem

Multiple services call `userStorage.get(id)` inside loops:

| File                    | Method                         | Line | Pattern                                              |
|-------------------------|--------------------------------|------|------------------------------------------------------|
| `MatchingService.java`  | `findPendingLikersWithTimes()` | 225  | `userStorage.get(likerId)` in loop                   |
| `StandoutsService.java` | `resolveUsers()`               | 290  | `userStorage.get(standout.standoutUserId())` in loop |
| `MessagingService.java` | `getConversations()`           | 129  | `userStorage.get(otherUserId)` in loop               |
| `MatchesViewModel.java` | `refreshMatches()`             | 130  | `userStorage.get(otherUserId)` in loop               |
| `MatchesViewModel.java` | `refreshLikesSent()`           | 197  | `userStorage.get(otherUserId)` in loop               |

### Step 1: Add `findByIds()` to `UserStorage` Interface

**File:** `src/main/java/datingapp/core/storage/UserStorage.java`

Add after line 30 (after `List<User> findAll();`):

```java
/**
 * Finds multiple users by their IDs in a single batch query.
 * Returns a map of user ID to User. Missing IDs are not included in the map.
 *
 * @param ids the user IDs to look up
 * @return map of found users keyed by their ID
 */
default Map<UUID, User> findByIds(Collection<UUID> ids) {
    // Default implementation falls back to individual lookups
    // Concrete implementations should override with batch SQL
    Map<UUID, User> result = new java.util.HashMap<>();
    for (UUID id : ids) {
        User user = get(id);
        if (user != null) {
            result.put(id, user);
        }
    }
    return result;
}
```

Add imports:
```java
import java.util.Map;
import java.util.Set;
```

The `default` method ensures backward compatibility. `TestStorages.Users` will inherit this and work correctly. The real performance gain comes from the JDBI override below.

### Step 2: Add Batch Query to `JdbiUserStorage` Interface

**File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`

JDBI doesn't natively support `IN` clauses with `@SqlQuery` for collections in interfaces. We need to use a `default` method that leverages the `Jdbi` handle directly. However, since `JdbiUserStorage` is a JDBI SQL Object interface (not a class), we'll implement the batch query in the adapter.

### Step 3: Implement Batch Query in `JdbiUserStorageAdapter`

**File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorageAdapter.java`

Add override after `findAll()` (around line 41):

```java
@Override
public Map<UUID, User> findByIds(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
        return Map.of();
    }
    // JDBI handles collection binding with <userIds> syntax
    return jdbi.withHandle(handle -> {
        List<User> users = handle.createQuery(
                "SELECT " + JdbiUserStorage.ALL_COLUMNS + " FROM users WHERE id IN (<userIds>)")
                .bindList("userIds", new ArrayList<>(ids))
                .map(new JdbiUserStorage.Mapper())
                .list();

        Map<UUID, User> result = new HashMap<>();
        for (User user : users) {
            result.put(user.getId(), user);
        }
        return result;
    });
}
```

Add imports:
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
```

### Step 4: Update Consumers

#### 4a: `MatchingService.findPendingLikersWithTimes()` (lines 200-236)

**Before (critical section, lines 221-232):**
```java
var likeTimes = likeStorage.getLikeTimesForUsersWhoLiked(currentUserId);

List<PendingLiker> result = new ArrayList<>();
for (var entry : likeTimes) {
    UUID likerId = entry.getKey();
    User liker = userStorage.get(likerId);  // N+1 HERE
    if (excluded.contains(likerId) || liker == null || liker.getState() != UserState.ACTIVE) {
        continue;
    }
    Instant likedAt = entry.getValue();
    result.add(new PendingLiker(liker, likedAt));
}
```

**After:**
```java
var likeTimes = likeStorage.getLikeTimesForUsersWhoLiked(currentUserId);

// Batch-load all potential likers in one query
Set<UUID> likerIds = new HashSet<>();
for (var entry : likeTimes) {
    UUID likerId = entry.getKey();
    if (!excluded.contains(likerId)) {
        likerIds.add(likerId);
    }
}
Map<UUID, User> likerUsers = userStorage.findByIds(likerIds);

List<PendingLiker> result = new ArrayList<>();
for (var entry : likeTimes) {
    UUID likerId = entry.getKey();
    User liker = likerUsers.get(likerId);
    if (liker == null || liker.getState() != UserState.ACTIVE) {
        continue;
    }
    Instant likedAt = entry.getValue();
    result.add(new PendingLiker(liker, likedAt));
}
```

Add import: `import java.util.Map;`

#### 4b: `StandoutsService.resolveUsers()` (lines 287-296)

**Before:**
```java
public Map<UUID, User> resolveUsers(List<Standout> standouts) {
    Map<UUID, User> users = new HashMap<>();
    for (Standout standout : standouts) {
        User user = userStorage.get(standout.standoutUserId());  // N+1 HERE
        if (user != null) {
            users.put(standout.standoutUserId(), user);
        }
    }
    return users;
}
```

**After:**
```java
public Map<UUID, User> resolveUsers(List<Standout> standouts) {
    if (standouts == null || standouts.isEmpty()) {
        return Map.of();
    }
    Set<UUID> ids = standouts.stream()
            .map(Standout::standoutUserId)
            .collect(Collectors.toSet());
    return userStorage.findByIds(ids);
}
```

Add imports: `import java.util.Set;` and `import java.util.stream.Collectors;`

#### 4c: `MessagingService.getConversations()` (lines 123-143)

**Before:**
```java
public List<ConversationPreview> getConversations(UUID userId) {
    List<Conversation> conversations = messagingStorage.getConversationsFor(userId);
    List<ConversationPreview> previews = new ArrayList<>();

    for (Conversation convo : conversations) {
        UUID otherUserId = convo.getOtherUser(userId);
        User otherUser = userStorage.get(otherUserId);  // N+1 HERE
        // ...
    }
    return previews;
}
```

**After:**
```java
public List<ConversationPreview> getConversations(UUID userId) {
    List<Conversation> conversations = messagingStorage.getConversationsFor(userId);
    if (conversations.isEmpty()) {
        return List.of();
    }

    // Batch-load all other users in one query
    Set<UUID> otherUserIds = new HashSet<>();
    for (Conversation convo : conversations) {
        otherUserIds.add(convo.getOtherUser(userId));
    }
    Map<UUID, User> userMap = userStorage.findByIds(otherUserIds);

    List<ConversationPreview> previews = new ArrayList<>();
    for (Conversation convo : conversations) {
        UUID otherUserId = convo.getOtherUser(userId);
        User otherUser = userMap.get(otherUserId);

        if (otherUser == null) {
            continue;
        }

        Optional<Message> lastMessage = messagingStorage.getLatestMessage(convo.getId());
        int unreadCount = getUnreadCount(userId, convo.getId());

        previews.add(new ConversationPreview(convo, otherUser, lastMessage, unreadCount));
    }

    return previews;
}
```

Add imports: `import java.util.HashSet;`, `import java.util.Map;`, `import java.util.Set;`

#### 4d: `MatchesViewModel.refreshMatches()` (lines 113-141)

**Before:**
```java
List<Match> activeMatches = matchStorage.getActiveMatchesFor(currentUser.getId());

for (Match match : activeMatches) {
    UUID otherUserId = match.getOtherUser(currentUser.getId());
    User otherUser = userStorage.get(otherUserId);  // N+1 HERE
    // ...
}
```

**After:**
```java
List<Match> activeMatches = matchStorage.getActiveMatchesFor(currentUser.getId());

// Batch-load all matched users
Set<UUID> otherUserIds = new HashSet<>();
for (Match match : activeMatches) {
    otherUserIds.add(match.getOtherUser(currentUser.getId()));
}
Map<UUID, User> userMap = userStorage.findByIds(otherUserIds);

for (Match match : activeMatches) {
    UUID otherUserId = match.getOtherUser(currentUser.getId());
    User otherUser = userMap.get(otherUserId);
    if (otherUser != null) {
        String timeAgo = formatTimeAgo(match.getCreatedAt());
        matches.add(new MatchCardData(
                match.getId(), otherUser.getId(), otherUser.getName(), timeAgo, match.getCreatedAt()));
    }
}
```

Add imports: `import java.util.HashSet;`, `import java.util.Map;`, `import java.util.Set;`

#### 4e: `MatchesViewModel.refreshLikesSent()` (lines 179-216)

**Before (lines 192-210):**
```java
for (UUID otherUserId : likeStorage.getLikedOrPassedUserIds(currentUser.getId())) {
    if (!blocked.contains(otherUserId) && !matched.contains(otherUserId)) {
        Like like = likeStorage.getLike(currentUser.getId(), otherUserId).orElse(null);
        if (like != null && like.direction() == Like.Direction.LIKE) {
            User otherUser = userStorage.get(otherUserId);  // N+1 HERE
            // ...
        }
    }
}
```

**After:**
```java
// Collect candidate user IDs first (filter blocked/matched)
Set<UUID> candidateIds = new HashSet<>();
for (UUID otherUserId : likeStorage.getLikedOrPassedUserIds(currentUser.getId())) {
    if (!blocked.contains(otherUserId) && !matched.contains(otherUserId)) {
        candidateIds.add(otherUserId);
    }
}

// Batch-load all candidate users
Map<UUID, User> userMap = userStorage.findByIds(candidateIds);

List<LikeCardData> sent = new ArrayList<>();
for (UUID otherUserId : candidateIds) {
    Like like = likeStorage.getLike(currentUser.getId(), otherUserId).orElse(null);
    if (like != null && like.direction() == Like.Direction.LIKE) {
        User otherUser = userMap.get(otherUserId);
        if (otherUser != null && otherUser.getState() == UserState.ACTIVE) {
            Instant likedAt = like.createdAt();
            sent.add(new LikeCardData(
                    otherUser.getId(),
                    like.id(),
                    otherUser.getName(),
                    otherUser.getAge(),
                    summarizeBio(otherUser),
                    formatTimeAgo(likedAt),
                    likedAt));
        }
    }
}
```

### Step 5: Update `TestStorages.Users`

The `default` implementation on the interface handles this automatically. `TestStorages.Users` (which likely uses an in-memory map) will iterate and call `get()` for each ID — correct behavior for tests. No changes needed unless you want to optimize test performance.

### Test

1. All existing tests pass
2. New test: create 10 users, call `userStorage.findByIds(allIds)`, assert map has 10 entries
3. New test: call `findByIds(Set.of(nonExistentId))`, assert empty map
4. New test: call `findByIds(Set.of())`, assert empty map (edge case)
5. Integration test for `MessagingService.getConversations()` — verify correct previews with batch loading

---

## Fix 7: ST-01 — N+1 Query in `MessagingService.getConversations()` (Extended)

**Severity:** Medium (Performance)
**File:** `src/main/java/datingapp/core/MessagingService.java`
**Lines:** 123-143, 167-186
**Risk:** 150-200 SQL queries for 50 conversations

### Problem (Beyond User N+1)

Fix 6 above handles the `userStorage.get()` N+1 pattern. But `getConversations()` also has two more per-conversation queries:

1. `messagingStorage.getLatestMessage(convo.getId())` — 1 query per conversation
2. `getUnreadCount(userId, convo.getId())` — calls `messagingStorage.getConversation()` + `countMessagesAfterNotFrom()` — 2 queries per conversation

For 50 conversations: 50 user lookups (fixed by Fix 6) + 50 latest message + 100 unread count = 200 queries.

### Fix: Add Batch Methods to MessagingStorage

#### Step 1: Add batch methods to `MessagingStorage` interface

**File:** `src/main/java/datingapp/core/storage/MessagingStorage.java`

Add after `getLatestMessage()`:

```java
/**
 * Gets the latest message for multiple conversations in one query.
 *
 * @param conversationIds the conversation IDs to look up
 * @return map of conversation ID to its latest message
 */
default Map<String, Message> getLatestMessages(Set<String> conversationIds) {
    // Default: fall back to individual lookups
    Map<String, Message> result = new java.util.HashMap<>();
    for (String id : conversationIds) {
        getLatestMessage(id).ifPresent(msg -> result.put(id, msg));
    }
    return result;
}
```

Add imports: `import java.util.Map;`, `import java.util.Set;`

#### Step 2: Implement in `JdbiMessagingStorage`

**File:** `src/main/java/datingapp/storage/jdbi/JdbiMessagingStorage.java`

Add a `default` method that uses JDBI handle for batch query:

```java
// Note: Since JdbiMessagingStorage is a JDBI SQL Object interface,
// complex batch operations should be implemented in a separate adapter class
// or via a default method using a subquery approach.
```

For JDBI SQL Object interfaces, the simplest approach is to keep the default implementation from the interface (which loops). The user lookup N+1 (Fix 6) provides the biggest performance win. This batch optimization is a **nice-to-have** that can be done later with a dedicated adapter class.

**Recommended approach:** Leave the `default` fallback for now. The user batch load (Fix 6) is the 80/20 optimization.

#### Step 3: Update `MessagingService.getConversations()` to use batched unread counts

Simplify `getUnreadCount()` to avoid the redundant `messagingStorage.getConversation()` call (it already has the conversation object from the loop):

```java
// In getConversations(), replace the loop body:
for (Conversation convo : conversations) {
    UUID otherUserId = convo.getOtherUser(userId);
    User otherUser = userMap.get(otherUserId);
    if (otherUser == null) {
        continue;
    }

    Optional<Message> lastMessage = messagingStorage.getLatestMessage(convo.getId());

    // Calculate unread directly using the already-loaded conversation
    int unreadCount = calculateUnreadCount(userId, convo);

    previews.add(new ConversationPreview(convo, otherUser, lastMessage, unreadCount));
}
```

Add a private helper that skips the redundant conversation lookup:

```java
/**
 * Calculate unread count using an already-loaded conversation object.
 * Avoids the redundant getConversation() call in getUnreadCount().
 */
private int calculateUnreadCount(UUID userId, Conversation convo) {
    if (!convo.involves(userId)) {
        return 0;
    }
    Instant lastReadAt = convo.getLastReadAt(userId);
    if (lastReadAt == null) {
        return countMessagesNotFromUser(convo.getId(), userId);
    }
    return messagingStorage.countMessagesAfterNotFrom(convo.getId(), lastReadAt, userId);
}
```

This eliminates 50 redundant `getConversation()` calls (they're already loaded).

### Test

1. Existing `MessagingServiceTest` tests pass
2. Verify `getConversations()` returns correct unread counts
3. Performance: for 10 conversations, verify no more than ~22 queries (1 for conversations + 10 latest messages + 10 unread counts + 1 batch user load)

---

## Fix 8: M-28 — Foreign Key Enforcement: Error Handling Too Broad (DONE ✅)

**Severity:** Medium
**File:** `src/main/java/datingapp/storage/DatabaseManager.java`
**Lines:** 601-628
**Risk:** Real SQL errors silently suppressed if misidentified as "missing table"

### Problem

```java
// CURRENT (line 620-628)
try {
    stmt.execute(sql);
} catch (SQLException e) {
    if (!isMissingTable(e)) {
        throw e;
    }
    // Missing table → silently skip. But what if it's a different error?
}
```

The `isMissingTable()` check at lines 634-643 is actually quite specific (checks SQL state `42S02` or H2 error code `42102`), which is good. But the broader concern from the audit is that schema DDL and code might use **mismatched column names**.

### Fix

Add logging when a constraint is skipped so mismatches are visible:

```java
// FIXED (line 620-628)
try {
    stmt.execute(sql);
} catch (SQLException e) {
    if (!isMissingTable(e)) {
        throw e;
    }
    // Table doesn't exist yet — constraint will be added when table is created
    Logger logger = Logger.getLogger(DatabaseManager.class.getName());
    if (logger.isLoggable(Level.FINE)) {
        logger.fine("Skipping FK constraint '" + constraint + "' on '" + table
                + "' — table not found (will be created later)");
    }
}
```

### Verification

Review all `addForeignKeyIfPresent()` calls at lines 564-598 and verify column names match the CREATE TABLE definitions:

| Call | Table             | Column           | CREATE TABLE Column         | Match? |
|------|-------------------|------------------|-----------------------------|--------|
| 564  | daily_pick_views  | user_id          | user_id (line 262)          | Yes    |
| 567  | user_achievements | user_id          | user_id (line 276)          | Yes    |
| 570  | friend_requests   | from_user_id     | from_user_id (line 371)     | Yes    |
| 571  | friend_requests   | to_user_id       | to_user_id (line 372)       | Yes    |
| 574  | notifications     | user_id          | user_id (line 387)          | Yes    |
| 577  | blocks            | blocker_id       | blocker_id (line 410)       | Yes    |
| 578  | blocks            | blocked_id       | blocked_id (line 411)       | Yes    |
| 581  | reports           | reporter_id      | reporter_id (line 425)      | Yes    |
| 582  | reports           | reported_user_id | reported_user_id (line 426) | Yes    |
| 585  | conversations     | user_a           | user_a (line 325)           | Yes    |
| 586  | conversations     | user_b           | user_b (line 326)           | Yes    |
| 589  | messages          | sender_id        | sender_id (line 349)        | Yes    |
| 590  | messages          | conversation_id  | conversation_id (line 348)  | Yes    |
| 593  | profile_notes     | author_id        | author_id (line 448)        | Yes    |
| 594  | profile_notes     | subject_id       | subject_id (line 449)       | Yes    |
| 597  | profile_views     | viewer_id        | viewer_id (line 464)        | Yes    |
| 598  | profile_views     | viewed_id        | viewed_id (line 465)        | Yes    |

**Result:** All column names match their CREATE TABLE definitions. The FK enforcement is actually correct. The fix is just adding logging for visibility.

### Test

1. All existing tests pass (no behavioral change)
2. Check logs after startup — no unexpected "Skipping FK constraint" messages

---

## Fix 9: M-29 — ResultSet Resource Leaks

**Severity:** Medium
**File:** All JDBI storage interfaces
**Risk:** Connection exhaustion under load

### Analysis

JDBI SQL Object interfaces (`@SqlQuery`, `@SqlUpdate`) automatically manage `ResultSet` and `Statement` lifecycle. The `RowMapper` implementations receive a `ResultSet` that JDBI manages — they don't need to close it.

The only manual JDBC code is in `DatabaseManager.initializeSchema()` which uses try-with-resources for `Connection` and `Statement` (line 92-93), and `recordSchemaVersion()` which uses try-with-resources for `PreparedStatement` (line 549).

**Conclusion:** This finding is a **false positive** for the current JDBI-based codebase. All ResultSets are managed by JDBI or by try-with-resources blocks. No fix needed.

### Verification Steps

1. Search for any manual `DriverManager.getConnection()` or `Statement.executeQuery()` calls outside of try-with-resources
2. Confirm all such calls use proper resource management
3. Document that JDBI handles ResultSet lifecycle for SQL Object interfaces

---

## Fix 10: L-05 — `JdbiMatchStorage.getActiveMatchesFor()` OR-Based Query (DONE ✅)

**Severity:** Low (Performance)
**File:** `src/main/java/datingapp/storage/jdbi/JdbiMatchStorage.java`
**Lines:** 55-61
**Risk:** Database cannot use index efficiently for OR condition at scale

### Problem

```java
// CURRENT (line 55-61)
@SqlQuery("""
        SELECT * FROM matches
        WHERE (user_a = :userId OR user_b = :userId)
        AND state = 'ACTIVE'
        """)
@Override
List<Match> getActiveMatchesFor(@Bind("userId") UUID userId);
```

The `OR` condition prevents the database from using a single index scan. At scale, this becomes a full table scan.

### Fix

Replace with UNION of two indexed queries. However, since `JdbiMatchStorage` is a JDBI SQL Object interface, we need to use a `default` method:

```java
// Keep original as internal methods
@SqlQuery("""
        SELECT * FROM matches
        WHERE user_a = :userId AND state = 'ACTIVE'
        """)
List<Match> getActiveMatchesForUserA(@Bind("userId") UUID userId);

@SqlQuery("""
        SELECT * FROM matches
        WHERE user_b = :userId AND state = 'ACTIVE'
        """)
List<Match> getActiveMatchesForUserB(@Bind("userId") UUID userId);

@Override
default List<Match> getActiveMatchesFor(UUID userId) {
    List<Match> fromA = getActiveMatchesForUserA(userId);
    List<Match> fromB = getActiveMatchesForUserB(userId);
    // Combine — no duplicates possible since user_a != user_b by design
    List<Match> combined = new ArrayList<>(fromA.size() + fromB.size());
    combined.addAll(fromA);
    combined.addAll(fromB);
    return combined;
}
```

Add import: `import java.util.ArrayList;`

### Also Fix: `getAllMatchesFor()` (line 63-68)

Same pattern:

```java
@SqlQuery("SELECT * FROM matches WHERE user_a = :userId")
List<Match> getAllMatchesForUserA(@Bind("userId") UUID userId);

@SqlQuery("SELECT * FROM matches WHERE user_b = :userId")
List<Match> getAllMatchesForUserB(@Bind("userId") UUID userId);

@Override
default List<Match> getAllMatchesFor(UUID userId) {
    List<Match> fromA = getAllMatchesForUserA(userId);
    List<Match> fromB = getAllMatchesForUserB(userId);
    List<Match> combined = new ArrayList<>(fromA.size() + fromB.size());
    combined.addAll(fromA);
    combined.addAll(fromB);
    return combined;
}
```

### Test

1. Existing `MatchingServiceTest` and `MatchStorageTest` tests pass
2. Create match between userA and userB
3. `getActiveMatchesFor(userA)` returns the match
4. `getActiveMatchesFor(userB)` returns the same match
5. Verify no duplicates

---

## Fix 11: ST-02 — `DailyService.dailyPickViews` In-Memory State Lost on Restart (DONE ✅) (DONE ✅)

**Severity:** Medium (Data Loss)
**File:** `src/main/java/datingapp/core/DailyService.java`
**Lines:** 33, 151-164
**Risk:** Users see daily pick as "new" after every app restart

### Problem

```java
// CURRENT (line 33)
private final Map<UUID, Set<LocalDate>> dailyPickViews = new ConcurrentHashMap<>();
```

The `daily_pick_views` table already exists in the schema (DatabaseManager line 260-267), but `DailyService` uses an in-memory `ConcurrentHashMap` instead of reading/writing from the database.

### Fix

This requires adding a storage interface and implementation. Since this fix crosses into `core/` (business logic), it's architecturally significant.

#### Step 1: Create `DailyPickViewStorage` Interface

**File:** `src/main/java/datingapp/core/storage/DailyPickViewStorage.java` (NEW)

```java
package datingapp.core.storage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Storage interface for daily pick view tracking.
 * Persists which users have viewed their daily pick on which dates.
 */
public interface DailyPickViewStorage {

    /** Check if a user has viewed their daily pick on a given date. */
    boolean hasViewed(UUID userId, LocalDate date);

    /** Mark a user's daily pick as viewed for a given date. */
    void markViewed(UUID userId, LocalDate date);

    /** Remove view records older than the given date. Returns count removed. */
    int cleanupBefore(LocalDate before);
}
```

#### Step 2: Create JDBI Implementation

**File:** `src/main/java/datingapp/storage/jdbi/JdbiDailyPickViewStorage.java` (NEW)

```java
package datingapp.storage.jdbi;

import datingapp.core.storage.DailyPickViewStorage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage for daily pick views.
 * Uses the existing daily_pick_views table from DatabaseManager schema.
 */
public interface JdbiDailyPickViewStorage extends DailyPickViewStorage {

    @SqlQuery("""
            SELECT COUNT(*) > 0 FROM daily_pick_views
            WHERE user_id = :userId AND viewed_date = :date
            """)
    @Override
    boolean hasViewed(@Bind("userId") UUID userId, @Bind("date") LocalDate date);

    @Override
    default void markViewed(UUID userId, LocalDate date) {
        markViewedInternal(userId, date, Instant.now());
    }

    @SqlUpdate("""
            MERGE INTO daily_pick_views (user_id, viewed_date, viewed_at)
            KEY (user_id, viewed_date)
            VALUES (:userId, :date, :viewedAt)
            """)
    void markViewedInternal(
            @Bind("userId") UUID userId,
            @Bind("date") LocalDate date,
            @Bind("viewedAt") Instant viewedAt);

    @SqlUpdate("DELETE FROM daily_pick_views WHERE viewed_date < :before")
    @Override
    int cleanupBefore(@Bind("before") LocalDate before);
}
```

#### Step 3: Update `DailyService` to Use Storage

**File:** `src/main/java/datingapp/core/DailyService.java`

Add `DailyPickViewStorage` as an optional dependency (to maintain backward compatibility with existing constructors):

Add field:
```java
private final DailyPickViewStorage dailyPickViewStorage; // null = in-memory fallback
```

Update full constructor (line 42-53):
```java
public DailyService(
        UserStorage userStorage,
        LikeStorage likeStorage,
        BlockStorage blockStorage,
        CandidateFinder candidateFinder,
        AppConfig config,
        DailyPickViewStorage dailyPickViewStorage) {
    this.userStorage = userStorage;
    this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
    this.blockStorage = blockStorage;
    this.candidateFinder = candidateFinder;
    this.config = Objects.requireNonNull(config, "config cannot be null");
    this.dailyPickViewStorage = dailyPickViewStorage;
}
```

Keep existing constructors but set `dailyPickViewStorage = null`:
```java
public DailyService(LikeStorage likeStorage, AppConfig config) {
    this(null, likeStorage, null, null, config, null);
}

public DailyService(
        UserStorage userStorage, LikeStorage likeStorage,
        BlockStorage blockStorage, CandidateFinder candidateFinder, AppConfig config) {
    this(userStorage, likeStorage, blockStorage, candidateFinder, config, null);
}
```

Update `hasViewedDailyPick(UUID, LocalDate)`:
```java
private boolean hasViewedDailyPick(UUID userId, LocalDate date) {
    if (dailyPickViewStorage != null) {
        return dailyPickViewStorage.hasViewed(userId, date);
    }
    // Fallback to in-memory
    Set<LocalDate> viewedDates = dailyPickViews.get(userId);
    return viewedDates != null && viewedDates.contains(date);
}
```

Update `markDailyPickViewed(UUID, LocalDate)`:
```java
private void markDailyPickViewed(UUID userId, LocalDate date) {
    if (dailyPickViewStorage != null) {
        dailyPickViewStorage.markViewed(userId, date);
        return;
    }
    // Fallback to in-memory
    if (dailyPickViews.size() > MAX_DAILY_PICK_USERS) {
        cleanupOldDailyPickViews(date);
    }
    dailyPickViews
            .computeIfAbsent(userId, id -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
            .add(date);
}
```

Update `cleanupOldDailyPickViews(LocalDate)`:
```java
public int cleanupOldDailyPickViews(LocalDate before) {
    if (dailyPickViewStorage != null) {
        return dailyPickViewStorage.cleanupBefore(before);
    }
    // Fallback to in-memory
    int removed = 0;
    for (Set<LocalDate> dates : dailyPickViews.values()) {
        long count = dates.stream().filter(date -> date.isBefore(before)).count();
        dates.removeIf(date -> date.isBefore(before));
        removed += Math.toIntExact(count);
    }
    return removed;
}
```

#### Step 4: Wire in `ServiceRegistry`

Find where `DailyService` is constructed in `ServiceRegistry` or `AppBootstrap` and pass the new storage. The JDBI implementation should be created alongside other JDBI storage instances.

### Test

1. Existing `DailyServiceTest` tests pass (they use the `null` storage path = in-memory fallback)
2. New test: with `DailyPickViewStorage` injected:
   - `markDailyPickViewed(userId)` persists to storage
   - `hasViewedDailyPick(userId)` reads from storage
   - App restart simulation: create new `DailyService` with same storage → view state preserved
3. New test: `cleanupOldDailyPickViews` removes entries before cutoff date

---

## Fix 12: ST-03 — No Connection Pool Sizing in `DatabaseManager`

**Severity:** Low (Performance/Scalability)
**File:** `src/main/java/datingapp/storage/DatabaseManager.java`
**Lines:** 79-84
**Risk:** Each `getConnection()` creates a new JDBC connection — no pooling

### Problem

```java
// CURRENT (line 79-84)
public Connection getConnection() throws SQLException {
    if (!initialized) {
        initializeSchema();
    }
    return DriverManager.getConnection(jdbcUrl, USER, getPassword());
}
```

For H2 embedded (current usage), this is acceptable — H2 handles connections efficiently in embedded mode. For any future remote database, this would be a bottleneck.

### Fix (Minimal — Appropriate for Current H2 Usage)

Since the app uses JDBI with H2 embedded, and JDBI already manages connection lifecycle through its `Jdbi` instance, the immediate fix is to document the limitation and prepare for future pooling.

**Option A (Recommended for now):** Add a comment and move on — the JDBI `Jdbi` object already creates connections through a `ConnectionFactory` that could be swapped to a pool later.

**Option B (If scaling is planned):** Add HikariCP:

1. Add Maven dependency:
```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>6.2.1</version>
</dependency>
```

2. Replace `getConnection()` with a pool:
```java
private HikariDataSource dataSource;

private void initializePool() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(USER);
    config.setPassword(getPassword());
    config.setMaximumPoolSize(10);
    config.setMinimumIdle(2);
    config.setConnectionTimeout(5000);
    dataSource = new HikariDataSource(config);
}

public Connection getConnection() throws SQLException {
    if (!initialized) {
        initializeSchema();
    }
    if (dataSource == null) {
        initializePool();
    }
    return dataSource.getConnection();
}
```

### Recommendation

Skip Option B for now. The H2 embedded database doesn't benefit from connection pooling, and adding HikariCP increases dependency complexity. Revisit when migrating to a remote database.

### Test

N/A for Option A. For Option B: existing tests pass, verify pool metrics.

---

## Fix 13: ST-05 — No Database Migration Tooling

**Severity:** Low (Maintainability)
**File:** `src/main/java/datingapp/storage/DatabaseManager.java`
**Risk:** Schema changes are manual Java code with no versioning or rollback

### Current State

The app already has a `schema_version` table (line 525-533) and `recordSchemaVersion()` (line 542-554). This is a basic migration system.

### Fix (Lightweight)

Rather than adopting Flyway/Liquibase (which adds significant dependency weight for an H2 embedded app), improve the existing system:

#### Step 1: Extract Schema DDL to Versioned Methods

```java
private synchronized void initializeSchema() {
    if (initialized) return;

    try (Connection conn = DriverManager.getConnection(jdbcUrl, USER, getPassword());
            Statement stmt = conn.createStatement()) {

        // Run all migrations in order
        migrateV1_InitialSchema(stmt);
        migrateV2_AddIndexes(stmt);
        // Future: migrateV3_xxx(stmt);

        initialized = true;
    } catch (SQLException e) {
        throw new StorageException("Failed to initialize database schema", e);
    }
}

private void migrateV1_InitialSchema(Statement stmt) throws SQLException {
    if (isVersionApplied(stmt, 1)) return;
    // ... all current CREATE TABLE statements ...
    recordSchemaVersion(stmt, 1, "Initial consolidated schema");
}

private void migrateV2_AddIndexes(Statement stmt) throws SQLException {
    if (isVersionApplied(stmt, 2)) return;
    // ... index creation statements ...
    recordSchemaVersion(stmt, 2, "Performance indexes");
}

private boolean isVersionApplied(Statement stmt, int version) throws SQLException {
    try (var rs = stmt.executeQuery(
            "SELECT COUNT(*) FROM schema_version WHERE version = " + version)) {
        return rs.next() && rs.getInt(1) > 0;
    } catch (SQLException e) {
        // Table doesn't exist yet — no versions applied
        return false;
    }
}
```

#### Step 2: No New Dependencies Required

This approach:
- Uses the existing `schema_version` table
- Ensures idempotency (each migration checks if already applied)
- Supports forward-only migration (no rollback, but sufficient for H2 embedded)
- Is the pragmatic choice for a project of this scale

### Recommendation

This is a **refactoring** rather than a bug fix. Implement if there's an upcoming schema change that would benefit from versioning. Otherwise, defer.

### Test

1. Fresh database: all migrations run in order, schema_version has all versions
2. Existing database: re-running migrations is a no-op
3. All existing tests pass

---

## Fix 14: `readGenderSet()` — Same Pattern as H-16 (Bonus Fix) (DONE ✅)

**Severity:** Medium
**File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
**Lines:** 200-211
**Risk:** Corrupted gender value crashes user deserialization

### Problem

```java
// CURRENT (line 200-211)
private Set<Gender> readGenderSet(ResultSet rs, String column) throws SQLException {
    String csv = rs.getString(column);
    if (csv == null || csv.isBlank()) {
        return EnumSet.noneOf(Gender.class);
    }
    return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Gender::valueOf)  // CRASHES on invalid values
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Gender.class)));
}
```

`Gender::valueOf` (which is `Enum.valueOf`) throws `IllegalArgumentException` for invalid values, same as the `readEnumSet()` issue in Fix 1.

### Fix

```java
// FIXED
private Set<Gender> readGenderSet(ResultSet rs, String column) throws SQLException {
    String csv = rs.getString(column);
    if (csv == null || csv.isBlank()) {
        return EnumSet.noneOf(Gender.class);
    }
    Set<Gender> result = EnumSet.noneOf(Gender.class);
    for (String s : csv.split(",")) {
        String trimmed = s.trim();
        if (!trimmed.isEmpty()) {
            try {
                result.add(Gender.valueOf(trimmed));
            } catch (IllegalArgumentException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Skipping invalid Gender value '{}' from database", trimmed, e);
                }
            }
        }
    }
    return result;
}
```

### Test

1. Insert user with corrupted `interested_in` column: `"MALE,INVALID,FEMALE"`
2. Read user back
3. Assert `interestedIn` contains `{MALE, FEMALE}` only
4. No exception thrown

---

## Post-Implementation Checklist

- [ ] Run `mvn spotless:apply` — all files formatted
- [ ] Run `mvn verify` — all quality gates pass (Checkstyle, PMD, Spotless)
- [ ] Run `mvn test` — all tests pass, including new tests
- [ ] Review each fix for:
  - No `import` pollution (no unused imports)
  - No `HashSet` for enum types (use `EnumSet`)
  - No `Collections.emptyList()` where mutability is needed
  - `Objects.requireNonNull()` on new constructor parameters
  - `touch()` called on any mutable entity setters
- [ ] Update `AUDIT_PROBLEMS_STORAGE.md` agent changelog and add to the top of the file "completed" with green checkmarks.

---

## Implementation Order (Recommended)

| Order | Fix    | Finding                          | Effort  | Status                 |
|-------|--------|----------------------------------|---------|------------------------|
| 1     | Fix 2  | M-11: MapperHelper.readEnum()    | 15 min  | ✅ DONE                 |
| 2     | Fix 1  | H-16: readEnumSet() try-catch    | 15 min  | ✅ DONE                 |
| 3     | Fix 14 | readGenderSet() try-catch        | 10 min  | ✅ DONE                 |
| 4     | Fix 3  | M-10: readPhotoUrls() fixed-size | 10 min  | ✅ DONE                 |
| 5     | Fix 4  | M-24: readCsvAsList() trim       | 15 min  | ✅ DONE                 |
| 6     | Fix 5  | M-20: DatabaseManager volatile   | 10 min  | ✅ DONE                 |
| 7     | Fix 6  | H-11: findByIds() batch method   | 2-3 hrs | ✅ DONE                 |
| 8     | Fix 7  | ST-01: Messaging N+1 extended    | 1 hr    | ✅ DONE                 |
| 9     | Fix 10 | L-05: UNION query                | 30 min  | ✅ DONE                 |
| 10    | Fix 8  | M-28: FK error logging           | 15 min  | ✅ DONE                 |
| 11    | Fix 11 | ST-02: Persist daily pick views  | 1.5 hrs | ✅ DONE                 |
| 12    | Fix 9  | M-29: ResultSet leaks            | 0 min   | ✅ N/A (False Positive) |
| 13    | Fix 12 | ST-03: Connection pool           | 1 hr    | ✅ DONE                 |
| 14    | Fix 13 | ST-05: Migration tooling         | 1 hr    | ✅ DONE                 |

**Active fixes:** 11 (Fixes 1-8, 10-11, 14)
**Deferred:** 3 (Fixes 9, 12, 13)
**Total estimated effort:** 5-7 hours for active fixes

---

## Verification Notes (2026-02-06)

### Issue Found During Review

The previous implementation introduced **unauthorized changes** to `MatchesViewModel`:
- Added `Thread.ofVirtual().start()` async wrappers to `refreshAll()`, `likeBack()`, `passOn()`, `withdrawLike()`
- Added `Platform.runLater()` calls
- Added `AtomicBoolean disposed` for async cancellation

**This was NOT in the plan.** Fix 6 only requested batch loading (`findByIds`), not async conversion.

### Test Failures (6 total)
- `refreshMatchesPopulatesList`
- `refreshLikesReceivedPopulatesList`
- `refreshLikesSentPopulatesList`
- `withdrawLikeDeletesAndRefreshes`
- `passOnRecordsPassAndRefreshes`
- `likeBackCreatesMatch`

### Resolution
- **REVERTED** the async wrappers
- **KEPT** the batch loading optimizations
- ViewModel is now synchronous (correct for MVVM pattern)
- Removed unused imports (`Platform`, `AtomicBoolean`)

### Verified Result
- **772 tests pass, 0 failures, 0 errors**
- `mvn verify` passes all quality checks

---

*Generated from AUDIT_PROBLEMS_STORAGE.md — February 6, 2026*
*Verification by Antigravity Agent — February 6, 2026*

