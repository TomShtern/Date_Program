# Storage Interface Consolidation: 9 → 5 ✅ COMPLETE✅
✅ COMPLETE✅
## Goal

<!--ARCHIVE:1:codex:storage-5-finalization-->
Consolidate `core/storage/` from 9 interfaces to 5 by merging domain-related interfaces. This simplifies `ServiceRegistry`, reduces file count, and aligns interfaces with actual domain boundaries.
<!--/ARCHIVE-->
Consolidation is complete as of 2026-02-11. `core/storage/` now contains 5 interfaces (`UserStorage`, `InteractionStorage`, `CommunicationStorage`, `AnalyticsStorage`, `TrustSafetyStorage`), and implementation wiring/tests/docs were migrated to the consolidated model.
1|2026-02-11 22:45:00|agent:codex|scope:storage-5-finalization|Marked full plan complete, validated build/tests/quality gates, and synced docs to 5-interface architecture|docs/plans/storage-consolidation-9-to-5.md;CLAUDE.md;AGENTS.md;.github/copilot-instructions.md

## Merge Map

| New Interface            | Absorbs                                                             | Total Methods |
|--------------------------|---------------------------------------------------------------------|---------------|
| **UserStorage**          | *(unchanged)*                                                       | 12            |
| **InteractionStorage**   | `LikeStorage` (13) + `MatchStorage` (9) + `TransactionExecutor` (1) | 23+2 defaults |
| **CommunicationStorage** | `MessagingStorage` (18) + `SocialStorage` (12)                      | 30            |
| **AnalyticsStorage**     | `StatsStorage` (20) + `SwipeSessionStorage` (8, 3 renamed)          | 28            |
| **TrustSafetyStorage**   | *(unchanged)*                                                       | 12            |

**Files deleted:** 7 old interfaces + 1 JDBI impl = 8 files removed
**Files created:** 3 interfaces + 3 JDBI adapters = 6 files added
**Net:** -2 files

---

## Critical Rules

1. **No method renames in InteractionStorage.** Java overloading handles `save(Like)` vs `save(Match)`, `exists(UUID,UUID)` vs `exists(String)`, `delete(UUID)` vs `delete(String)`.
2. **3 renames in AnalyticsStorage** for consistency with existing prefixed names (`saveUserStats`, etc.):
   - `save(SwipeSession)` → `saveSession(SwipeSession)`
   - `get(UUID)` → `getSession(UUID)`
   - `getAggregates(UUID)` → `getSessionAggregates(UUID)`
3. **`SessionAggregates` record** moves from `SwipeSessionStorage.SessionAggregates` → `AnalyticsStorage.SessionAggregates`.
4. **JDBI adapter pattern:** Follow `JdbiUserStorageAdapter` — take `Jdbi` in constructor, delegate via `onDemand` proxies. Internal JDBI DAO interfaces survive as implementation details.
5. **Build discipline:** Run `mvn spotless:apply` then `mvn verify` only ONCE at the end. Capture output into `$out`, query with `Select-String`.

---

## Completion Tracking (2026-02-11)

| Step         | Status | What was completed                                                                                                                                                                                             |
|--------------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1            | DONE   | Created `InteractionStorage` and migrated like/match/atomic undo contract.                                                                                                                                     |
| 2            | DONE   | Created `CommunicationStorage` and migrated messaging/social contract.                                                                                                                                         |
| 3            | DONE   | Created `AnalyticsStorage`, moved `SessionAggregates`, and applied session method renames.                                                                                                                     |
| 4            | DONE   | Added `JdbiInteractionStorageAdapter`, `JdbiCommunicationStorageAdapter`, `JdbiAnalyticsStorageAdapter` with adapter delegation pattern.                                                                       |
| 5            | DONE   | Updated `ServiceRegistry` to expose consolidated storage getters and constructor wiring.                                                                                                                       |
| 6            | DONE   | Updated `StorageFactory` to instantiate/wire consolidated adapters and removed transaction executor wiring.                                                                                                    |
| 7            | DONE   | Updated service constructors/fields/usages across affected core services (`AchievementService`, `DailyService`, `MatchingService`, `MessagingService`, `SessionService`, `StatsService`, `UndoService`, etc.). |
| 8            | DONE   | Updated `UiDataAdapters.StorageUiMatchDataAccess` to use `InteractionStorage`.                                                                                                                                 |
| 9            | DONE   | Updated `ViewModelFactory` and app layer usages to consume consolidated storages.                                                                                                                              |
| 10           | DONE   | Rebuilt `TestStorages` around `Interactions`, `Communications`, `Analytics` and aligned in-memory behavior with new interfaces.                                                                                |
| 11           | DONE   | Migrated tests to consolidated storage types/constructors and shared instances where required.                                                                                                                 |
| 12           | DONE   | Deleted old storage interfaces and `JdbiTransactionExecutor`; removed obsolete `extends` from internal DAO interfaces.                                                                                         |
| 13           | DONE   | Updated `CLAUDE.md`, `AGENTS.md`, and `.github/copilot-instructions.md` to reflect 5 storage interfaces and new adapter landscape.                                                                             |
| Verification | DONE   | `mvn verify` passed on 2026-02-11: 802 tests, `BUILD SUCCESS`, Spotless/Checkstyle/PMD/JaCoCo checks all green; orphan-reference and file-count checks also passed.                                            |

## Step 1: Create `InteractionStorage.java`

**File:** `src/main/java/datingapp/core/storage/InteractionStorage.java`
**Package:** `datingapp.core.storage`

Combine all methods from `LikeStorage`, `MatchStorage`, and `TransactionExecutor`. Method signatures are copied verbatim (no renames). Imports needed: `Like`, `Match`, `java.time.Instant`, `java.util.*`.

### Methods to include (exact signatures):

```java
// ═══ Like Operations ═══

Optional<Like> getLike(UUID fromUserId, UUID toUserId);
void save(Like like);
boolean exists(UUID from, UUID to);
boolean mutualLikeExists(UUID a, UUID b);
Set<UUID> getLikedOrPassedUserIds(UUID userId);
Set<UUID> getUserIdsWhoLiked(UUID userId);
List<Map.Entry<UUID, Instant>> getLikeTimesForUsersWhoLiked(UUID userId);
int countByDirection(UUID userId, Like.Direction direction);
int countReceivedByDirection(UUID userId, Like.Direction direction);
int countMutualLikes(UUID userId);
int countLikesToday(UUID userId, Instant startOfDay);
int countPassesToday(UUID userId, Instant startOfDay);
void delete(UUID likeId);

// ═══ Match Operations ═══

void save(Match match);
void update(Match match);
Optional<Match> get(String matchId);
boolean exists(String matchId);
List<Match> getActiveMatchesFor(UUID userId);
List<Match> getAllMatchesFor(UUID userId);
default Optional<Match> getByUsers(UUID userA, UUID userB) {
    return get(Match.generateId(userA, userB));
}
void delete(String matchId);
default int purgeDeletedBefore(Instant threshold) { return 0; }

// ═══ Transaction Operations ═══

boolean atomicUndoDelete(UUID likeId, String matchId);
```

---

## Step 2: Create `CommunicationStorage.java`

**File:** `src/main/java/datingapp/core/storage/CommunicationStorage.java`
**Package:** `datingapp.core.storage`

Combine all methods from `MessagingStorage` and `SocialStorage` verbatim. No renames needed (no collisions). Imports needed: `Conversation`, `Message`, `Match.ArchiveReason`, `FriendRequest`, `Notification`, `java.time.Instant`, `java.util.*`.

### Methods to include (exact signatures):

```java
// ═══ Conversation Operations ═══

void saveConversation(Conversation conversation);
Optional<Conversation> getConversation(String conversationId);
Optional<Conversation> getConversationByUsers(UUID userA, UUID userB);
List<Conversation> getConversationsFor(UUID userId);
void updateConversationLastMessageAt(String conversationId, Instant timestamp);
void updateConversationReadTimestamp(String conversationId, UUID userId, Instant timestamp);
void archiveConversation(String conversationId, Match.ArchiveReason reason);
void setConversationVisibility(String conversationId, UUID userId, boolean visible);
void deleteConversation(String conversationId);

// ═══ Message Operations ═══

void saveMessage(Message message);
List<Message> getMessages(String conversationId, int limit, int offset);
Optional<Message> getLatestMessage(String conversationId);
int countMessages(String conversationId);
int countMessagesAfter(String conversationId, Instant after);
int countMessagesNotFromSender(String conversationId, UUID senderId);
int countMessagesAfterNotFrom(String conversationId, Instant after, UUID excludeSenderId);
void deleteMessagesByConversation(String conversationId);

// ═══ Friend Request Operations ═══

void saveFriendRequest(FriendRequest request);
void updateFriendRequest(FriendRequest request);
Optional<FriendRequest> getFriendRequest(UUID id);
Optional<FriendRequest> getPendingFriendRequestBetween(UUID user1, UUID user2);
List<FriendRequest> getPendingFriendRequestsForUser(UUID userId);
void deleteFriendRequest(UUID id);

// ═══ Notification Operations ═══

void saveNotification(Notification notification);
void markNotificationAsRead(UUID id);
List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly);
Optional<Notification> getNotification(UUID id);
void deleteNotification(UUID id);
void deleteOldNotifications(Instant before);
```

---

## Step 3: Create `AnalyticsStorage.java`

**File:** `src/main/java/datingapp/core/storage/AnalyticsStorage.java`
**Package:** `datingapp.core.storage`

Combine all methods from `StatsStorage` (verbatim) and `SwipeSessionStorage` (3 methods renamed). Move `SessionAggregates` record here. Imports needed: `UserStats`, `PlatformStats`, `UserAchievement`, `Achievement`, `SwipeSession`, `java.time.*`, `java.util.*`.

### Methods to include:

```java
// ═══ User Stats ═══

void saveUserStats(UserStats stats);
Optional<UserStats> getLatestUserStats(UUID userId);
List<UserStats> getUserStatsHistory(UUID userId, int limit);
List<UserStats> getAllLatestUserStats();
int deleteUserStatsOlderThan(Instant cutoff);

// ═══ Platform Stats ═══

void savePlatformStats(PlatformStats stats);
Optional<PlatformStats> getLatestPlatformStats();
List<PlatformStats> getPlatformStatsHistory(int limit);

// ═══ Profile Views ═══

void recordProfileView(UUID viewerId, UUID viewedId);
int getProfileViewCount(UUID userId);
int getUniqueViewerCount(UUID userId);
List<UUID> getRecentViewers(UUID userId, int limit);
boolean hasViewedProfile(UUID viewerId, UUID viewedId);

// ═══ Achievements ═══

void saveUserAchievement(UserAchievement achievement);
List<UserAchievement> getUnlockedAchievements(UUID userId);
boolean hasAchievement(UUID userId, Achievement achievement);
int countUnlockedAchievements(UUID userId);

// ═══ Daily Picks ═══

void markDailyPickAsViewed(UUID userId, LocalDate date);
boolean isDailyPickViewed(UUID userId, LocalDate date);
int deleteDailyPickViewsOlderThan(LocalDate before);
int deleteExpiredDailyPickViews(Instant cutoff);

// ═══ Swipe Sessions (renamed from SwipeSessionStorage) ═══

void saveSession(SwipeSession session);           // was: save(SwipeSession)
Optional<SwipeSession> getSession(UUID sessionId); // was: get(UUID)
Optional<SwipeSession> getActiveSession(UUID userId);
List<SwipeSession> getSessionsFor(UUID userId, int limit);
List<SwipeSession> getSessionsInRange(UUID userId, Instant start, Instant end);
SessionAggregates getSessionAggregates(UUID userId); // was: getAggregates(UUID)
int endStaleSessions(Duration timeout);
int deleteExpiredSessions(Instant cutoff);

// ═══ SessionAggregates record (moved from SwipeSessionStorage) ═══
// Keep exact same record definition with compact constructor validation
```

---

## Step 4: Create 3 JDBI Adapter Classes

Follow the adapter pattern from `src/main/java/datingapp/storage/jdbi/JdbiUserStorageAdapter.java`:
- Constructor takes `Jdbi`
- Creates `onDemand` proxies of internal JDBI DAO interfaces
- Each method delegates to the appropriate proxy

### 4a: `JdbiInteractionStorageAdapter.java`

**File:** `src/main/java/datingapp/storage/jdbi/JdbiInteractionStorageAdapter.java`

```java
public class JdbiInteractionStorageAdapter implements InteractionStorage {
    private final Jdbi jdbi;
    private final JdbiLikeStorage likeDao;
    private final JdbiMatchStorage matchDao;

    public JdbiInteractionStorageAdapter(Jdbi jdbi) {
        this.jdbi = jdbi;
        this.likeDao = jdbi.onDemand(JdbiLikeStorage.class);
        this.matchDao = jdbi.onDemand(JdbiMatchStorage.class);
    }

    // Like methods → delegate to likeDao
    // Match methods → delegate to matchDao
    // atomicUndoDelete → inline from JdbiTransactionExecutor:
    //   jdbi.inTransaction(handle -> { DELETE likes, DELETE matches })
}
```

**`atomicUndoDelete` inline logic** (copy from `JdbiTransactionExecutor.java`):
```java
@Override
public boolean atomicUndoDelete(UUID likeId, String matchId) {
    try {
        return jdbi.inTransaction(handle -> {
            int likesDeleted = handle.createUpdate("DELETE FROM likes WHERE id = :id")
                    .bind("id", likeId).execute();
            if (likesDeleted == 0) return false;
            if (matchId != null) {
                handle.createUpdate("DELETE FROM matches WHERE id = :id")
                        .bind("id", matchId).execute();
            }
            return true;
        });
    } catch (Exception e) {
        throw new StorageException("Atomic undo delete failed", e);
    }
}
```

### 4b: `JdbiCommunicationStorageAdapter.java`

**File:** `src/main/java/datingapp/storage/jdbi/JdbiCommunicationStorageAdapter.java`

```java
public class JdbiCommunicationStorageAdapter implements CommunicationStorage {
    private final JdbiMessagingStorage messagingDao;
    private final JdbiSocialStorage socialDao;

    public JdbiCommunicationStorageAdapter(Jdbi jdbi) {
        this.messagingDao = jdbi.onDemand(JdbiMessagingStorage.class);
        this.socialDao = jdbi.onDemand(JdbiSocialStorage.class);
    }
    // Conversation/Message methods → delegate to messagingDao
    // FriendRequest/Notification methods → delegate to socialDao
}
```

### 4c: `JdbiAnalyticsStorageAdapter.java`

**File:** `src/main/java/datingapp/storage/jdbi/JdbiAnalyticsStorageAdapter.java`

```java
public class JdbiAnalyticsStorageAdapter implements AnalyticsStorage {
    private final JdbiStatsStorage statsDao;
    private final JdbiSwipeSessionStorage sessionDao;

    public JdbiAnalyticsStorageAdapter(Jdbi jdbi) {
        this.statsDao = jdbi.onDemand(JdbiStatsStorage.class);
        this.sessionDao = jdbi.onDemand(JdbiSwipeSessionStorage.class);
    }
    // Stats/Profile/Achievement/DailyPick methods → delegate to statsDao
    // Session methods → delegate to sessionDao
    //   saveSession(s) → sessionDao.save(s)
    //   getSession(id) → sessionDao.get(id)
    //   getSessionAggregates(id) → sessionDao.getAggregates(id)
}
```

---

## Step 5: Update `ServiceRegistry.java`

**File:** `src/main/java/datingapp/core/ServiceRegistry.java`

### Remove fields + imports:
```
- LikeStorage likeStorage
- MatchStorage matchStorage
- SwipeSessionStorage sessionStorage
- StatsStorage statsStorage
- MessagingStorage messagingStorage
- SocialStorage socialStorage
```

### Add fields + imports:
```
+ InteractionStorage interactionStorage
+ CommunicationStorage communicationStorage
+ AnalyticsStorage analyticsStorage
```

### Constructor: 22 params → 19
Remove the 6 old storage params, add 3 new ones. Keep `Objects.requireNonNull()` for each.

### Getters: Replace 6 → 3
```
- getLikeStorage(), getMatchStorage(), getSessionStorage(),
  getStatsStorage(), getMessagingStorage(), getSocialStorage()
+ getInteractionStorage(), getCommunicationStorage(), getAnalyticsStorage()
```

Keep `getUserStorage()` and `getTrustSafetyStorage()` unchanged.

---

## Step 6: Update `StorageFactory.java`

**File:** `src/main/java/datingapp/storage/StorageFactory.java`

### Storage instantiation section — replace:
```java
// OLD
LikeStorage likeStorage = jdbi.onDemand(JdbiLikeStorage.class);
MatchStorage matchStorage = jdbi.onDemand(JdbiMatchStorage.class);
TrustSafetyStorage trustSafetyStorage = jdbi.onDemand(JdbiTrustSafetyStorage.class);
SwipeSessionStorage sessionStorage = jdbi.onDemand(JdbiSwipeSessionStorage.class);
StatsStorage statsStorage = jdbi.onDemand(JdbiStatsStorage.class);
MessagingStorage messagingStorage = jdbi.onDemand(JdbiMessagingStorage.class);
SocialStorage socialStorage = jdbi.onDemand(JdbiSocialStorage.class);
// ... later:
JdbiTransactionExecutor txExecutor = new JdbiTransactionExecutor(jdbi);
undoService.setTransactionExecutor(txExecutor);

// NEW
InteractionStorage interactionStorage = new JdbiInteractionStorageAdapter(jdbi);
CommunicationStorage communicationStorage = new JdbiCommunicationStorageAdapter(jdbi);
AnalyticsStorage analyticsStorage = new JdbiAnalyticsStorageAdapter(jdbi);
TrustSafetyStorage trustSafetyStorage = jdbi.onDemand(JdbiTrustSafetyStorage.class);
```

### Service constructor calls — update every service instantiation:

```java
// Use this mapping throughout:
// likeStorage    → interactionStorage
// matchStorage   → interactionStorage
// sessionStorage → analyticsStorage
// statsStorage   → analyticsStorage
// messagingStorage → communicationStorage
// socialStorage  → communicationStorage
// txExecutor     → (remove — absorbed into interactionStorage)
```

**Critical:** `UndoService.setTransactionExecutor(txExecutor)` line is deleted — `atomicUndoDelete` is now on `InteractionStorage` directly. Update `UndoService` to call `interactionStorage.atomicUndoDelete()` instead.

### ServiceRegistry constructor call — pass 5 storages instead of 8.

---

## Step 7: Update 12 Services

Each service: change field types, constructor params, and imports. Method calls stay the same EXCEPT for 3 renamed session methods.

| Service File                                      | Old Storage Fields                                         | New Storage Fields                           |
|---------------------------------------------------|------------------------------------------------------------|----------------------------------------------|
| `core/service/AchievementService.java`            | `statsStorage`, `matchStorage`, `likeStorage`              | `analyticsStorage`, `interactionStorage`     |
| `core/service/CandidateFinder.java`               | `likeStorage`                                              | `interactionStorage`                         |
| `core/service/DailyService.java`                  | `likeStorage`, `statsStorage`                              | `interactionStorage`, `analyticsStorage`     |
| `core/service/MatchingService.java`               | `likeStorage`, `matchStorage`                              | `interactionStorage`                         |
| `core/service/MatchQualityService.java`           | `likeStorage`                                              | `interactionStorage`                         |
| `core/service/MessagingService.java`              | `messagingStorage`, `matchStorage`                         | `communicationStorage`, `interactionStorage` |
| `core/service/RelationshipTransitionService.java` | `matchStorage`, `socialStorage`, `messagingStorage`        | `interactionStorage`, `communicationStorage` |
| `core/service/SessionService.java`                | `sessionStorage`, `statsStorage`                           | `analyticsStorage`                           |
| `core/service/StatsService.java`                  | `likeStorage`, `matchStorage`, `statsStorage`              | `interactionStorage`, `analyticsStorage`     |
| `core/service/TrustSafetyService.java`            | `matchStorage`                                             | `interactionStorage`                         |
| `core/service/UndoService.java`                   | `likeStorage`, `matchStorage` + `setTransactionExecutor()` | `interactionStorage` (remove setter)         |
| `core/service/StandoutsService.java`              | *(no storage changes)*                                     | *(no changes)*                               |

### Method call renames in `SessionService.java` ONLY:
```java
// OLD                                    → NEW
sessionStorage.save(session)              → analyticsStorage.saveSession(session)
sessionStorage.get(id)                    → analyticsStorage.getSession(id)
sessionStorage.getActiveSession(userId)   → analyticsStorage.getActiveSession(userId)  // unchanged
sessionStorage.getSessionsFor(id, limit)  → analyticsStorage.getSessionsFor(id, limit)  // unchanged
sessionStorage.getSessionsInRange(...)    → analyticsStorage.getSessionsInRange(...)  // unchanged
sessionStorage.getAggregates(userId)      → analyticsStorage.getSessionAggregates(userId)
sessionStorage.endStaleSessions(timeout)  → analyticsStorage.endStaleSessions(timeout)  // unchanged
sessionStorage.deleteExpiredSessions(...) → analyticsStorage.deleteExpiredSessions(...)  // unchanged
```

### UndoService special case:
- Remove `TransactionExecutor` field and `setTransactionExecutor()` setter
- Call `interactionStorage.atomicUndoDelete(likeId, matchId)` directly

---

## Step 8: Update `UiDataAdapters.java`

**File:** `src/main/java/datingapp/ui/viewmodel/data/UiDataAdapters.java`

### `StorageUiMatchDataAccess` constructor:
```java
// OLD
public StorageUiMatchDataAccess(MatchStorage matchStorage, LikeStorage likeStorage, TrustSafetyStorage trustSafetyStorage)

// NEW
public StorageUiMatchDataAccess(InteractionStorage interactionStorage, TrustSafetyStorage trustSafetyStorage)
```

### Internal field + delegation:
```java
// OLD: 2 fields (matchStorage + likeStorage) delegating separately
// NEW: 1 field (interactionStorage) delegating to same methods
//   matchStorage.getActiveMatchesFor() → interactionStorage.getActiveMatchesFor()
//   likeStorage.getLikedOrPassedUserIds() → interactionStorage.getLikedOrPassedUserIds()
//   likeStorage.getLike() → interactionStorage.getLike()
//   likeStorage.delete() → interactionStorage.delete()  (UUID overload)
```

### Remove imports:
```
- import datingapp.core.storage.LikeStorage;
- import datingapp.core.storage.MatchStorage;
+ import datingapp.core.storage.InteractionStorage;
```

---

## Step 9: Update `ViewModelFactory.java` and CLI/API layers

**File:** `src/main/java/datingapp/ui/ViewModelFactory.java`
- Replace `services.getLikeStorage()` / `services.getMatchStorage()` → `services.getInteractionStorage()`
- Replace `services.getMessagingStorage()` / `services.getSocialStorage()` → `services.getCommunicationStorage()`
- Replace `services.getStatsStorage()` / `services.getSessionStorage()` → `services.getAnalyticsStorage()`

**Scan** `src/main/java/datingapp/app/cli/` and `src/main/java/datingapp/app/api/` for any direct storage getter calls and update similarly.

---

## Step 10: Update `TestStorages.java`

**File:** `src/test/java/datingapp/core/testutil/TestStorages.java`

### Merge inner classes:

**`Interactions implements InteractionStorage`** — combine contents of `Likes` + `Matches`:
- Keep both internal maps: `Map<String, Like> likes` + `Map<String, Match> matches`
- Copy all method implementations from both classes
- Add `atomicUndoDelete`: delete from both maps, return success
- Keep test helpers: `clear()`, `size()` (return likes.size() + matches.size()), `likeSize()`, `matchSize()`

**`Communications implements CommunicationStorage`** — combine contents of `Messaging` + `Social`:
- Keep both internal maps: conversations + messages + friendRequests + notifications
- Copy all method implementations from both classes

**`Analytics implements AnalyticsStorage`** — extend existing `Stats` class:
- Add `Map<UUID, SwipeSession> sessions` for session tracking
- Implement 8 session methods (in-memory HashMap-backed, same pattern as existing)
- Map renamed methods: `saveSession` → put in map, `getSession` → get from map, `getSessionAggregates` → return `SessionAggregates.empty()`

### Delete old inner classes:
- `Likes`, `Matches`, `Messaging`, `Social`, `Stats`

### Keep unchanged:
- `Users`, `TrustSafety`, `Standouts`, `Undos`

---

## Step 11: Update All Test Files

Mechanical find-and-replace across `src/test/java/`:

### Type replacements:
```
LikeStorage      → InteractionStorage
MatchStorage     → InteractionStorage
TransactionExecutor → (remove, use InteractionStorage)
MessagingStorage → CommunicationStorage
SocialStorage    → CommunicationStorage
StatsStorage     → AnalyticsStorage
SwipeSessionStorage → AnalyticsStorage
```

### TestStorages instantiation replacements:
```
new TestStorages.Likes()     → new TestStorages.Interactions()
new TestStorages.Matches()   → (use same Interactions instance)
new TestStorages.Messaging() → new TestStorages.Communications()
new TestStorages.Social()    → (use same Communications instance)
new TestStorages.Stats()     → new TestStorages.Analytics()
```

**IMPORTANT:** When a test used BOTH `Likes()` AND `Matches()` as separate instances, they must now share ONE `Interactions` instance. Same for `Messaging()` + `Social()` → one `Communications` instance. Same for `Stats()` → `Analytics()`.

### SessionAggregates reference:
```
SwipeSessionStorage.SessionAggregates → AnalyticsStorage.SessionAggregates
```

---

## Step 12: Delete Old Files

### Delete 7 interfaces:
```
src/main/java/datingapp/core/storage/LikeStorage.java
src/main/java/datingapp/core/storage/MatchStorage.java
src/main/java/datingapp/core/storage/TransactionExecutor.java
src/main/java/datingapp/core/storage/MessagingStorage.java
src/main/java/datingapp/core/storage/SocialStorage.java
src/main/java/datingapp/core/storage/StatsStorage.java
src/main/java/datingapp/core/storage/SwipeSessionStorage.java
```

### Delete 1 JDBI impl:
```
src/main/java/datingapp/storage/jdbi/JdbiTransactionExecutor.java
```

### Remove `extends` from 6 internal JDBI DAOs:
These files keep their `@SqlQuery`/`@SqlUpdate` annotations but no longer extend the deleted interfaces:
```
JdbiLikeStorage         — remove "extends LikeStorage"
JdbiMatchStorage        — remove "extends MatchStorage"
JdbiMessagingStorage    — remove "extends MessagingStorage"
JdbiSocialStorage       — remove "extends SocialStorage"
JdbiStatsStorage        — remove "extends StatsStorage"
JdbiSwipeSessionStorage — remove "extends SwipeSessionStorage"
```

---

## Step 13: Update Documentation

### `CLAUDE.md`:
- Storage interface count: 9 → 5
- Package table: update `core/storage/` description to "5 storage interfaces"
- Update getter names in any code examples
- Update `ServiceRegistry` constructor example if shown
- Update Recent Updates section

### `AGENTS.md`:
- Update any storage interface references

### `.github/copilot-instructions.md`:
- Update if it references old storage interface names

---

## Verification

```powershell
# 1. Format code
mvn spotless:apply

# 2. Full build + test (ONE run, capture output)
$out = mvn verify 2>&1 | Out-String
$out | Select-String "BUILD (SUCCESS|FAILURE)" | Select-Object -Last 1
$out | Select-String "Tests run:" | Select-Object -Last 1
$out | Select-String "ERROR|FAILURE"

# 3. Verify core/storage/ has exactly 5 files
(Get-ChildItem src/main/java/datingapp/core/storage -Filter "*.java").Count
# Expected: 5 (UserStorage, InteractionStorage, CommunicationStorage, AnalyticsStorage, TrustSafetyStorage)

# 4. Verify no orphan references to deleted interfaces
rg "import datingapp.core.storage.LikeStorage" src/
rg "import datingapp.core.storage.MatchStorage" src/
rg "import datingapp.core.storage.TransactionExecutor" src/
rg "import datingapp.core.storage.MessagingStorage" src/
rg "import datingapp.core.storage.SocialStorage" src/
rg "import datingapp.core.storage.StatsStorage" src/
rg "import datingapp.core.storage.SwipeSessionStorage" src/
# All should return 0 matches

# 5. Verify no references to deleted TestStorages classes
rg "TestStorages\.(Likes|Matches|Messaging|Social|Stats)\b" src/test/
# Should return 0 matches

# 6. Verify JdbiTransactionExecutor is gone
Test-Path src/main/java/datingapp/storage/jdbi/JdbiTransactionExecutor.java
# Should return False
```

## Plan Changelog

1|2026-02-11 22:45:00|agent:codex|scope:storage-5-finalization|Marked full plan complete, validated build/tests/quality gates, and synced docs to 5-interface architecture|docs/plans/storage-consolidation-9-to-5.md;CLAUDE.md;AGENTS.md;.github/copilot-instructions.md
