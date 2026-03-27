# Codebase Consolidation Design

**Date:** 2026-01-30
**Status:** ✅ COMPLETED (Phase 1 ✅, Phase 2 ✅, Phase 3 ✅, Phase 4 ✅)
**Goal:** Reduce file count and eliminate duplication without changing functionality

> **Implementation Log:**
> - **Phase 1 (CLI Login Guards):** ✅ COMPLETED 2026-01-30
>   - Added `requireLogin()` method to `UserSession` class in `CliUtilities.java`
>   - Updated 15 occurrences across 6 handlers: `ProfileHandler`, `SafetyHandler`, `StatsHandler`, `MatchingHandler`, `LikerBrowserHandler`, `ProfileNotesHandler`
>   - All tests passing, no regressions
> - **Phase 2 (Mapper Consolidation):** ✅ ALREADY COMPLETED (verified 2026-01-30)
>   - All 15 mappers already inlined as inner `Mapper` classes in JDBI interfaces
>   - Only `MapperHelper.java` and `EnumSetColumnMapper.java` remain (as intended)
> - **Phase 3 (Module System Elimination):** ✅ COMPLETED 2026-01-30
>   - Inlined all module logic directly into `ServiceRegistry.Builder.buildH2()`
>   - Deleted 7 files: `AppContext.java`, `StorageModule.java`, `MatchingModule.java`, `MessagingModule.java`, `SafetyModule.java`, `StatsModule.java`, `Module.java`
>   - Lines saved: ~309 (29,978 → 29,669)
> - **Phase 4 (Storage Consolidation):** ✅ COMPLETED 2026-01-30
>   - **StatsStorage:** ✅ Created `StatsStorage` consolidating `UserStatsStorage` + `PlatformStatsStorage`
>     - Created `JdbiStatsStorage.java`, updated `StatsService`, `ServiceRegistry`
>     - Deleted: `UserStatsStorage.java`, `PlatformStatsStorage.java`, `JdbiUserStatsStorage.java`, `JdbiPlatformStatsStorage.java`
>   - **MessagingStorage:** ✅ Created `MessagingStorage` consolidating `ConversationStorage` + `MessageStorage`
>     - Created `JdbiMessagingStorage.java`, updated `MessagingService`, `RelationshipTransitionService`, `ServiceRegistry`
>     - Deleted: `ConversationStorage.java`, `MessageStorage.java`, `JdbiConversationStorage.java`, `JdbiMessageStorage.java`
>   - **SocialStorage:** ✅ Created `SocialStorage` consolidating `FriendRequestStorage` + `NotificationStorage`
>     - Created `JdbiSocialStorage.java`, updated `RelationshipTransitionService`, `RelationshipHandler`, `ServiceRegistry`
>     - Deleted: `FriendRequestStorage.java`, `NotificationStorage.java`, `JdbiFriendRequestStorage.java`, `JdbiNotificationStorage.java`
>   - **UserInteractionStorage:** ❌ INTENTIONALLY DEFERRED
>     - LikeStorage has 13+ consumers (high coupling risk)
>     - Like, Block, Report serve distinct functional purposes (matching, safety, moderation)
>     - Consolidation would reduce code clarity without significant file count benefit
>   - **Final Metrics:**
>     - Main Java Files: 97 (down from ~107)
>     - Total LOC: 25,759 (down from ~29,978 at start) - **~14% reduction**



---

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Codebase Context](#codebase-context)
3. [Prerequisites](#prerequisites)
4. [Phase 1: CLI Login Guard De-duplication](#phase-1-cli-login-guard-de-duplication) *(Start Here)*
5. [Phase 2: Mapper Consolidation](#phase-2-mapper-consolidation)
6. [Phase 3: Module System Elimination](#phase-3-module-system-elimination)
7. [Phase 4: Storage Consolidation](#phase-4-storage-consolidation)
8. [Verification & Completion](#verification--completion)

---

## Executive Summary

This plan consolidates the codebase from **~126 files to ~96 files** (24% reduction) through four phases:

| Phase     | What                                | Files Deleted | Lines Saved | Effort      |
|-----------|-------------------------------------|---------------|-------------|-------------|
| 1         | CLI login guard de-duplication      | 0             | ~54         | 30 min      |
| 2         | Inline mappers into JDBI interfaces | 13            | ~400        | 2 hrs       |
| 3         | Eliminate module system             | 7             | ~360        | 2 hrs       |
| 4         | Consolidate storage by domain       | 10            | ~320        | 4 hrs       |
| **Total** |                                     | **30**        | **~1,134**  | **8.5 hrs** |

**Key Principle:** We're removing actual duplication and unnecessary abstraction, NOT just reorganizing. Every change should result in less total code.

---

## Codebase Context

### Project Structure
```
src/main/java/datingapp/
├── core/                    # Business logic (ZERO framework imports)
│   ├── storage/             # Storage INTERFACES (contracts)
│   ├── User.java            # Primary entity (806 LOC)
│   ├── Match.java           # Primary entity
│   ├── ServiceRegistry.java # Composition root (323 LOC)
│   └── [services]           # Business services
├── storage/                 # Data layer
│   ├── jdbi/                # JDBI implementations of interfaces
│   ├── mapper/              # Row mappers (TARGET: consolidate)
│   └── DatabaseManager.java
├── module/                  # Module system (TARGET: eliminate)
│   ├── AppContext.java
│   ├── StorageModule.java
│   └── [*Module.java]
├── cli/                     # Console UI handlers
│   ├── CliUtilities.java    # Contains UserSession class
│   └── [*Handler.java]
└── ui/                      # JavaFX UI
```

### Key Files You'll Work With

| File                   | Path                                      | Purpose                                       |
|------------------------|-------------------------------------------|-----------------------------------------------|
| `CliUtilities.java`    | `src/main/java/datingapp/cli/`            | Contains `UserSession` class - add guard here |
| `ServiceRegistry.java` | `src/main/java/datingapp/core/`           | Composition root - module logic moves here    |
| `MapperHelper.java`    | `src/main/java/datingapp/storage/mapper/` | Utility class - KEEP, don't delete            |
| `UserMapper.java`      | `src/main/java/datingapp/storage/mapper/` | Complex mapper - KEEP, don't delete           |

### Technology Stack
- **Java 25** with preview features enabled
- **JDBI 3.51.0** for declarative SQL (`@SqlQuery`, `@SqlUpdate`, `@RegisterRowMapper`)
- **H2 Database** (embedded)
- **Maven** build system

### Important Patterns

**JDBI Interface Pattern:**
```java
@RegisterRowMapper(EntityMapper.class)
public interface JdbiEntityStorage extends EntityStorage {
    @SqlQuery("SELECT * FROM table WHERE id = :id")
    Entity get(@Bind("id") UUID id);

    @SqlUpdate("INSERT INTO table (...) VALUES (...)")
    void save(@BindBean Entity entity);
}
```

**Inner Class Mapper (what we're moving to):**
```java
public interface JdbiEntityStorage extends EntityStorage {
    @SqlQuery("SELECT * FROM table")
    @RegisterRowMapper(EntityRowMapper.class)  // Reference inner class
    List<Entity> getAll();

    // Inner class - no separate file needed
    class EntityRowMapper implements RowMapper<Entity> {
        @Override
        public Entity map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Entity(...);
        }
    }
}
```

---

## Prerequisites

Before starting implementation:

### 1. Verify Build is Green
```bash
mvn clean test
```
**Expected:** 581 tests pass, BUILD SUCCESS

### 2. Verify You're on a Clean Branch
```bash
git status
```
Create a feature branch if needed.

### 3. Have These Tools Ready
- IDE with Java support
- Terminal for running Maven
- This document open for reference

---

## Phase 1: CLI Login Guard De-duplication

**Goal:** Replace 16 duplicate login checks with a single utility method.
**Risk:** Very Low
**Time:** 30 minutes

### Step 1.1: Add the Guard Method to UserSession

**File:** `src/main/java/datingapp/cli/CliUtilities.java`

**Find this class** (around line 37):
```java
public static class UserSession {
    private User currentUser;
    // ... existing methods
}
```

**Add this method inside the UserSession class**, after the existing methods:

```java
    private static final Logger logger = LoggerFactory.getLogger(UserSession.class);

    /**
     * Executes the action only if a user is logged in.
     * Logs "Please select a user first" if not logged in.
     *
     * @param action The action to execute if logged in
     * @return true if action was executed, false if not logged in
     */
    public boolean requireLogin(Runnable action) {
        if (!isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return false;
        }
        action.run();
        return true;
    }
```

**Required import** (add at top of file if not present):
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

### Step 1.2: Update Each Handler

For each occurrence, transform from the OLD pattern to the NEW pattern.

**Search pattern to find all occurrences:**
```
if (!userSession.isLoggedIn()) {
    logger.info(CliConstants.PLEASE_SELECT_USER);
    return;
}
```

**Transformation:**

```java
// ═══════════════════════════════════════════════════════════════
// BEFORE - 16 occurrences of this pattern
// ═══════════════════════════════════════════════════════════════
public void someMethod() {
    if (!userSession.isLoggedIn()) {
        logger.info(CliConstants.PLEASE_SELECT_USER);
        return;
    }

    // ... rest of method body (could be 10-200 lines)
    doSomething();
    doSomethingElse();
}

// ═══════════════════════════════════════════════════════════════
// AFTER - wrap entire method body in lambda
// ═══════════════════════════════════════════════════════════════
public void someMethod() {
    userSession.requireLogin(() -> {
        // ... rest of method body (same code, now inside lambda)
        doSomething();
        doSomethingElse();
    });
}
```

### Step 1.3: Files and Locations to Update

| File                           | Line(s) | Method Name            |
|--------------------------------|---------|------------------------|
| `cli/ProfileHandler.java`      | ~66     | `completeProfile()`    |
| `cli/ProfileHandler.java`      | ~104    | `previewProfile()`     |
| `cli/ProfileHandler.java`      | ~167    | `setDealbreakers()`    |
| `cli/ProfileHandler.java`      | ~721    | `viewProfileScore()`   |
| `cli/SafetyHandler.java`       | ~46     | `blockUser()`          |
| `cli/SafetyHandler.java`       | ~109    | `reportUser()`         |
| `cli/SafetyHandler.java`       | ~214    | `manageBlockedUsers()` |
| `cli/SafetyHandler.java`       | ~271    | `verifyProfile()`      |
| `cli/StatsHandler.java`        | ~34     | `viewStatistics()`     |
| `cli/StatsHandler.java`        | ~125    | `viewAchievements()`   |
| `cli/MatchingHandler.java`     | ~114    | `browseCandidates()`   |
| `cli/MatchingHandler.java`     | ~233    | `viewMatches()`        |
| `cli/LikerBrowserHandler.java` | ~32     | `browseWhoLikedMe()`   |
| `cli/ProfileNotesHandler.java` | ~44     | `manageNoteFor()`      |
| `cli/ProfileNotesHandler.java` | ~167    | `viewAllNotes()`       |

### Step 1.4: Validation Checklist

After completing Phase 1:

- [✓] Added `requireLogin()` method to `UserSession` class
- [✓] Added Logger field and import to `CliUtilities.java`
- [✓] Updated all 15 occurrences (verified via grep):
  ```bash
  grep -r "if (!userSession.isLoggedIn())" src/main/java/datingapp/cli/
  ```
  **Result:** No occurrences remain (all replaced)
- [✓] Run tests:
  ```bash
  mvn test
  ```
  **Result:** All tests pass

### Phase 1 Gotchas

1. **Lambda scope:** Local variables used inside the lambda must be effectively final. If a method modifies a local variable after the guard check, you may need to restructure slightly.

2. **Return values:** If the original method returns a value after the guard, use a different pattern:
   ```java
   // If method returns something:
   public SomeResult methodWithReturn() {
       if (!userSession.isLoggedIn()) {
           logger.info(CliConstants.PLEASE_SELECT_USER);
           return null;  // or empty result
       }
       return computeResult();
   }

   // Keep as-is OR use Optional pattern - don't force into lambda
   ```

3. **Checked exceptions:** If code inside throws checked exceptions, the lambda won't compile. Wrap in try-catch or keep the original pattern for those methods.

---

## Phase 2: Mapper Consolidation

**Goal:** Move 13 trivial mapper classes inline into their JDBI interfaces.
**Risk:** Low
**Time:** 2 hours
**Dependency:** None (can run parallel with Phase 1)

### Step 2.1: Understand the Pattern

Each mapper follows this exact pattern:

```java
// CURRENT: Separate file (e.g., BlockMapper.java)
package datingapp.storage.mapper;

public class BlockMapper implements RowMapper<Block> {
    @Override
    public Block map(ResultSet rs, StatementContext ctx) throws SQLException {
        return new Block(
            MapperHelper.readUuid(rs, "id"),
            MapperHelper.readUuid(rs, "column_name"),
            // ... more fields
            MapperHelper.readInstant(rs, "created_at"));
    }
}
```

We're moving this INTO the JDBI interface that uses it:

```java
// TARGET: Inner class in JDBI interface
package datingapp.storage.jdbi;

public interface JdbiBlockStorage extends BlockStorage {

    @SqlQuery("SELECT * FROM blocks WHERE blocker_id = :userId")
    @RegisterRowMapper(BlockRowMapper.class)  // Reference the inner class
    List<Block> getBlocksByUser(@Bind("userId") UUID userId);

    // ... other methods ...

    // Inner class mapper
    class BlockRowMapper implements RowMapper<Block> {
        @Override
        public Block map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Block(
                MapperHelper.readUuid(rs, "id"),
                MapperHelper.readUuid(rs, "blocker_id"),
                MapperHelper.readUuid(rs, "blocked_id"),
                MapperHelper.readInstant(rs, "created_at"));
        }
    }
}
```

### Step 2.2: Process Each Mapper

For each mapper in the table below:

1. **Open** the mapper file from `storage/mapper/`
2. **Open** the corresponding JDBI interface from `storage/jdbi/`
3. **Copy** the mapper class body
4. **Paste** as an inner class at the bottom of the JDBI interface
5. **Rename** from `EntityMapper` to `EntityRowMapper` (convention)
6. **Update** all `@RegisterRowMapper` annotations to reference the inner class
7. **Add** any missing imports to the JDBI interface
8. **Delete** the original mapper file

### Step 2.3: Mapper-to-JDBI Mapping Table

| Mapper File (DELETE)                | JDBI Interface (ADD inner class)       | Inner Class Name           |
|-------------------------------------|----------------------------------------|----------------------------|
| `mapper/BlockMapper.java`           | `jdbi/JdbiBlockStorage.java`           | `BlockRowMapper`           |
| `mapper/LikeMapper.java`            | `jdbi/JdbiLikeStorage.java`            | `LikeRowMapper`            |
| `mapper/ReportMapper.java`          | `jdbi/JdbiReportStorage.java`          | `ReportRowMapper`          |
| `mapper/MessageMapper.java`         | `jdbi/JdbiMessageStorage.java`         | `MessageRowMapper`         |
| `mapper/MatchMapper.java`           | `jdbi/JdbiMatchStorage.java`           | `MatchRowMapper`           |
| `mapper/FriendRequestMapper.java`   | `jdbi/JdbiFriendRequestStorage.java`   | `FriendRequestRowMapper`   |
| `mapper/NotificationMapper.java`    | `jdbi/JdbiNotificationStorage.java`    | `NotificationRowMapper`    |
| `mapper/UserAchievementMapper.java` | `jdbi/JdbiUserAchievementStorage.java` | `UserAchievementRowMapper` |
| `mapper/ProfileNoteMapper.java`     | `jdbi/JdbiProfileNoteStorage.java`     | `ProfileNoteRowMapper`     |
| `mapper/SwipeSessionMapper.java`    | `jdbi/JdbiSwipeSessionStorage.java`    | `SwipeSessionRowMapper`    |
| `mapper/DailyPickMapper.java`       | `jdbi/JdbiDailyPickStorage.java`       | `DailyPickRowMapper`       |
| `mapper/ConversationMapper.java`    | `jdbi/JdbiConversationStorage.java`    | `ConversationRowMapper`    |
| `mapper/ProfileViewMapper.java`     | `jdbi/JdbiProfileViewStorage.java`     | `ProfileViewRowMapper`     |

### Step 2.4: Detailed Example - BlockMapper

**BEFORE:** Two separate files

`storage/mapper/BlockMapper.java`:
```java
package datingapp.storage.mapper;

import datingapp.core.UserInteractions.Block;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class BlockMapper implements RowMapper<Block> {
    @Override
    public Block map(ResultSet rs, StatementContext ctx) throws SQLException {
        return new Block(
                MapperHelper.readUuid(rs, "id"),
                MapperHelper.readUuid(rs, "blocker_id"),
                MapperHelper.readUuid(rs, "blocked_id"),
                MapperHelper.readInstant(rs, "created_at"));
    }
}
```

`storage/jdbi/JdbiBlockStorage.java`:
```java
package datingapp.storage.jdbi;

import datingapp.core.UserInteractions.Block;
import datingapp.core.storage.BlockStorage;
import datingapp.storage.mapper.BlockMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@RegisterRowMapper(BlockMapper.class)
public interface JdbiBlockStorage extends BlockStorage {

    @SqlUpdate("MERGE INTO blocks (id, blocker_id, blocked_id, created_at) KEY (id) VALUES (:id, :blockerId, :blockedId, :createdAt)")
    @Override
    void save(@BindBean Block block);

    @SqlQuery("SELECT * FROM blocks WHERE blocker_id = :userId")
    @Override
    List<Block> getBlocksByUser(@Bind("userId") UUID userId);

    // ... other methods
}
```

**AFTER:** Single file with inner class

`storage/jdbi/JdbiBlockStorage.java`:
```java
package datingapp.storage.jdbi;

import datingapp.core.UserInteractions.Block;
import datingapp.core.storage.BlockStorage;
import datingapp.storage.mapper.MapperHelper;  // Still needed for utilities
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@RegisterRowMapper(JdbiBlockStorage.BlockRowMapper.class)  // Updated reference
public interface JdbiBlockStorage extends BlockStorage {

    @SqlUpdate("MERGE INTO blocks (id, blocker_id, blocked_id, created_at) KEY (id) VALUES (:id, :blockerId, :blockedId, :createdAt)")
    @Override
    void save(@BindBean Block block);

    @SqlQuery("SELECT * FROM blocks WHERE blocker_id = :userId")
    @Override
    List<Block> getBlocksByUser(@Bind("userId") UUID userId);

    // ... other methods ...

    // ═══════════════════════════════════════════════════════════════
    // Inner class mapper - moved from BlockMapper.java
    // ═══════════════════════════════════════════════════════════════
    class BlockRowMapper implements RowMapper<Block> {
        @Override
        public Block map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Block(
                    MapperHelper.readUuid(rs, "id"),
                    MapperHelper.readUuid(rs, "blocker_id"),
                    MapperHelper.readUuid(rs, "blocked_id"),
                    MapperHelper.readInstant(rs, "created_at"));
        }
    }
}
```

**Then DELETE:** `storage/mapper/BlockMapper.java`

### Step 2.5: Files to KEEP (Do NOT Delete)

| File                              | Reason                                                |
|-----------------------------------|-------------------------------------------------------|
| `mapper/UserMapper.java`          | Complex mapper (172 LOC) using StorageBuilder pattern |
| `mapper/UserStatsMapper.java`     | Will be consolidated in Phase 4                       |
| `mapper/PlatformStatsMapper.java` | Will be consolidated in Phase 4                       |
| `mapper/MapperHelper.java`        | Utility class used by all mappers                     |

### Step 2.6: Validation Checklist

After completing Phase 2:

- [✓] All 13 mapper files deleted (already done before this session)
- [✓] All 13 JDBI interfaces have inner mapper classes
- [✓] All `@RegisterRowMapper` annotations reference inner classes
- [✓] No dangling imports (verified)
- [✓] Run tests:
  ```bash
  mvn test
  ```
  **Result:** All tests pass

### Phase 2 Gotchas

1. **Import cleanup:** After moving mapper inline, remove the old import (`import datingapp.storage.mapper.BlockMapper;`) and add any new imports needed (`import java.sql.ResultSet;`, etc.)

2. **Class-level vs method-level annotation:** If a JDBI interface has `@RegisterRowMapper` at class level, it applies to all methods. If only some methods use the mapper, use method-level annotation instead.

3. **MapperHelper imports:** The inner class still needs to use `MapperHelper.readUuid()`, etc. Make sure `import datingapp.storage.mapper.MapperHelper;` is in the JDBI interface.

---

## Phase 3: Module System Elimination

**Goal:** Inline the module abstraction layer into ServiceRegistry.
**Risk:** Medium (affects DI wiring)
**Time:** 2 hours
**Dependency:** Should complete BEFORE Phase 4 (storage consolidation)

### Step 3.1: Understand Current Architecture

```
Current call flow:
Main.java
  → ServiceRegistry.Builder.buildH2(dbManager, config)
    → AppContext.create(dbManager, config)
      → StorageModule.forH2(dbManager)         // Creates JDBI & all storage instances
      → MatchingModule.create(storage, config)  // Creates matching services
      → MessagingModule.create(storage)         // Creates messaging services
      → SafetyModule.create(storage, config)    // Creates safety services
      → StatsModule.create(storage, config)     // Creates stats services
    → fromAppContext(app)                       // Wraps into ServiceRegistry

Target call flow:
Main.java
  → ServiceRegistry.Builder.buildH2(dbManager, config)
    → [All logic inlined directly]             // No intermediate modules
    → new ServiceRegistry(...)
```

### Step 3.2: Read the Module Files First

Before making changes, read and understand what each module does:

1. **StorageModule.forH2()** - `module/StorageModule.java` lines 74-129
   - Creates JDBI instance
   - Registers custom type handlers
   - Instantiates all 16 storage interfaces via `jdbi.onDemand()`

2. **MatchingModule.create()** - `module/MatchingModule.java`
   - Creates: `CandidateFinder`, `MatchingService`, `UndoService`, `DailyService`, `SessionService`, `MatchQualityService`, `ProfilePreviewService`

3. **MessagingModule.create()** - `module/MessagingModule.java`
   - Creates: `MessagingService`, `RelationshipTransitionService`

4. **SafetyModule.create()** - `module/SafetyModule.java`
   - Creates: `TrustSafetyService`, `ValidationService`

5. **StatsModule.create()** - `module/StatsModule.java`
   - Creates: `StatsService`, `AchievementService`, `ProfileCompletionService`

### Step 3.3: Modify ServiceRegistry.Builder

**File:** `src/main/java/datingapp/core/ServiceRegistry.java`

**Find** the `Builder` class (around line 254) and the `buildH2` method.

**Replace** the current implementation that delegates to AppContext with direct instantiation:

```java
public static final class Builder {

    private Builder() {
        // Utility class
    }

    /**
     * Builds a ServiceRegistry with H2 database storage.
     * All storage and service instantiation happens directly here.
     */
    public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
        Objects.requireNonNull(dbManager, "dbManager cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        // ═══════════════════════════════════════════════════════════════
        // JDBI Setup (from StorageModule.forH2)
        // ═══════════════════════════════════════════════════════════════
        org.jdbi.v3.core.Jdbi jdbi = org.jdbi.v3.core.Jdbi.create(() -> {
            try {
                return dbManager.getConnection();
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("Failed to get database connection", e);
            }
        }).installPlugin(new org.jdbi.v3.sqlobject.SqlObjectPlugin());

        jdbi.registerArgument(new datingapp.storage.jdbi.EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new datingapp.storage.jdbi.EnumSetColumnMapper());

        // ═══════════════════════════════════════════════════════════════
        // Storage Instantiation (from StorageModule.forH2)
        // ═══════════════════════════════════════════════════════════════
        UserStorage userStorage = new datingapp.storage.jdbi.JdbiUserStorageAdapter(jdbi);
        LikeStorage likeStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiLikeStorage.class);
        MatchStorage matchStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiMatchStorage.class);
        BlockStorage blockStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiBlockStorage.class);
        ReportStorage reportStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiReportStorage.class);
        SwipeSessionStorage sessionStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiSwipeSessionStorage.class);
        UserStatsStorage userStatsStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiUserStatsStorage.class);
        PlatformStatsStorage platformStatsStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiPlatformStatsStorage.class);
        DailyPickStorage dailyPickStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiDailyPickStorage.class);
        UserAchievementStorage userAchievementStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiUserAchievementStorage.class);
        ProfileViewStorage profileViewStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiProfileViewStorage.class);
        ProfileNoteStorage profileNoteStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiProfileNoteStorage.class);
        ConversationStorage conversationStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiConversationStorage.class);
        MessageStorage messageStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiMessageStorage.class);
        FriendRequestStorage friendRequestStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiFriendRequestStorage.class);
        NotificationStorage notificationStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiNotificationStorage.class);

        // ═══════════════════════════════════════════════════════════════
        // Service Instantiation (from *Module.create methods)
        // ═══════════════════════════════════════════════════════════════

        // Matching services (from MatchingModule)
        CandidateFinder candidateFinder = new CandidateFinder(userStorage, matchStorage, blockStorage, likeStorage, config);
        MatchingService matchingService = new MatchingService(likeStorage, matchStorage, userStorage, blockStorage);
        UndoService undoService = new UndoService(likeStorage, matchStorage);
        SessionService sessionService = new SessionService(sessionStorage, config);
        DailyService dailyService = new DailyService(dailyPickStorage, likeStorage, userStorage, config);
        MatchQualityService matchQualityService = new MatchQualityService(config);
        ProfilePreviewService profilePreviewService = new ProfilePreviewService();

        // Messaging services (from MessagingModule)
        MessagingService messagingService = new MessagingService(
                conversationStorage, messageStorage, matchStorage, userStorage);
        RelationshipTransitionService relationshipTransitionService = new RelationshipTransitionService(
                matchStorage, friendRequestStorage, notificationStorage, userStorage);

        // Safety services (from SafetyModule)
        TrustSafetyService trustSafetyService = new TrustSafetyService(
                reportStorage, userStorage, blockStorage, config);
        ValidationService validationService = new ValidationService();

        // Stats services (from StatsModule)
        StatsService statsService = new StatsService(
                userStatsStorage, platformStatsStorage, profileViewStorage);
        AchievementService achievementService = new AchievementService(
                userAchievementStorage, userStatsStorage);
        ProfileCompletionService profileCompletionService = new ProfileCompletionService();

        // ═══════════════════════════════════════════════════════════════
        // Build ServiceRegistry
        // ═══════════════════════════════════════════════════════════════
        return new ServiceRegistry(
                config,
                userStorage,
                likeStorage,
                matchStorage,
                blockStorage,
                reportStorage,
                sessionStorage,
                userStatsStorage,
                platformStatsStorage,
                dailyPickStorage,
                userAchievementStorage,
                profileViewStorage,
                profileNoteStorage,
                conversationStorage,
                messageStorage,
                friendRequestStorage,
                notificationStorage,
                candidateFinder,
                matchingService,
                trustSafetyService,
                sessionService,
                statsService,
                matchQualityService,
                profilePreviewService,
                dailyService,
                undoService,
                achievementService,
                messagingService,
                relationshipTransitionService);
    }

    /** Builds a ServiceRegistry with H2 database and default configuration. */
    public static ServiceRegistry buildH2(DatabaseManager dbManager) {
        return buildH2(dbManager, AppConfig.defaults());
    }
}
```

### Step 3.4: Remove Old fromAppContext Method

Delete the `fromAppContext(AppContext app)` method from the Builder class - it's no longer needed.

### Step 3.5: Remove Module Imports

Remove these imports from ServiceRegistry.java:
```java
// DELETE these imports
import datingapp.module.AppContext;
import datingapp.module.StorageModule;
import datingapp.module.MatchingModule;
// etc.
```

### Step 3.6: Delete Module Files

Delete the entire `module/` directory:

```
src/main/java/datingapp/module/
├── Module.java           # DELETE
├── AppContext.java       # DELETE
├── StorageModule.java    # DELETE
├── MatchingModule.java   # DELETE
├── MessagingModule.java  # DELETE
├── SafetyModule.java     # DELETE
└── StatsModule.java      # DELETE
```

### Step 3.7: Validation Checklist

After completing Phase 3:

- [✓] `ServiceRegistry.Builder.buildH2()` contains all inline instantiation
- [✓] `fromAppContext()` method removed
- [✓] All 7 module files deleted
- [✓] No references to module package (verified)
- [✓] Run tests:
  ```bash
  mvn test
  ```
  **Result:** All tests pass

### Phase 3 Gotchas

1. **Service constructor parameters:** Double-check each service's constructor signature. The module files have the correct parameter order - copy exactly.

2. **Fully qualified names:** In the inlined code, you may need to use fully qualified class names (e.g., `datingapp.storage.jdbi.JdbiLikeStorage.class`) to avoid import conflicts.

3. **ValidationService has no dependencies:** It's constructed with `new ValidationService()` - no parameters.

4. **Order matters:** Some services depend on others. Instantiate storage first, then services that only need storage, then services that need other services.

---

## Phase 4: Storage Consolidation

**Goal:** Group related storage interfaces and implementations by domain.
**Risk:** Medium-High (most files affected)
**Time:** 4 hours
**Dependency:** Complete Phase 3 first (module elimination)

### Step 4.1: Consolidation Overview

We're merging related storage interfaces AND their JDBI implementations:

| Group                      | Interfaces Merged                          | Implementations Merged                                 |
|----------------------------|--------------------------------------------|--------------------------------------------------------|
| **UserInteractionStorage** | BlockStorage + LikeStorage + ReportStorage | JdbiBlockStorage + JdbiLikeStorage + JdbiReportStorage |
| **MessagingStorage**       | MessageStorage + ConversationStorage       | JdbiMessageStorage + JdbiConversationStorage           |
| **SocialStorage**          | FriendRequestStorage + NotificationStorage | JdbiFriendRequestStorage + JdbiNotificationStorage     |
| **StatsStorage**           | UserStatsStorage + PlatformStatsStorage    | JdbiUserStatsStorage + JdbiPlatformStatsStorage        |

### Step 4.2: Create UserInteractionStorage Interface

**Create new file:** `src/main/java/datingapp/core/storage/UserInteractionStorage.java`

```java
package datingapp.core.storage;

import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.Report;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Consolidated storage interface for user interactions: blocks, likes, and reports.
 * Groups related operations that were previously in separate interfaces.
 */
public interface UserInteractionStorage {

    // ═══════════════════════════════════════════════════════════════
    // Block Operations (from BlockStorage)
    // ═══════════════════════════════════════════════════════════════

    void saveBlock(Block block);

    Optional<Block> getBlock(UUID id);

    List<Block> getBlocksByUser(UUID userId);

    boolean isBlocked(UUID blockerId, UUID blockedId);

    Set<UUID> getBlockedUserIds(UUID userId);

    void deleteBlock(UUID blockerId, UUID blockedId);

    // ═══════════════════════════════════════════════════════════════
    // Like Operations (from LikeStorage)
    // ═══════════════════════════════════════════════════════════════

    void saveLike(Like like);

    Optional<Like> getLike(UUID id);

    List<Like> getLikesBy(UUID userId);

    List<Like> getLikesReceived(UUID userId);

    boolean likeExists(UUID whoLikes, UUID whoGotLiked);

    boolean mutualLikeExists(UUID userA, UUID userB);

    Set<UUID> getLikedOrPassedUserIds(UUID userId);

    void deleteLike(UUID id);

    int countLikesToday(UUID userId);

    // ═══════════════════════════════════════════════════════════════
    // Report Operations (from ReportStorage)
    // ═══════════════════════════════════════════════════════════════

    void saveReport(Report report);

    Optional<Report> getReport(UUID id);

    List<Report> getReportsAgainst(UUID userId);

    List<Report> getReportsByReporter(UUID reporterId);

    int countReportsAgainst(UUID userId);
}
```

### Step 4.3: Create JdbiUserInteractionStorage Implementation

**Create new file:** `src/main/java/datingapp/storage/jdbi/JdbiUserInteractionStorage.java`

```java
package datingapp.storage.jdbi;

import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.Report;
import datingapp.core.storage.UserInteractionStorage;
import datingapp.storage.mapper.MapperHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI implementation of UserInteractionStorage.
 * Consolidates block, like, and report storage operations.
 */
public interface JdbiUserInteractionStorage extends UserInteractionStorage {

    // ═══════════════════════════════════════════════════════════════
    // Block Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("MERGE INTO blocks (id, blocker_id, blocked_id, created_at) KEY (id) VALUES (:id, :blockerId, :blockedId, :createdAt)")
    @Override
    void saveBlock(@BindBean Block block);

    @SqlQuery("SELECT * FROM blocks WHERE id = :id")
    @RegisterRowMapper(BlockRowMapper.class)
    @Override
    Optional<Block> getBlock(@Bind("id") UUID id);

    @SqlQuery("SELECT * FROM blocks WHERE blocker_id = :userId")
    @RegisterRowMapper(BlockRowMapper.class)
    @Override
    List<Block> getBlocksByUser(@Bind("userId") UUID userId);

    @SqlQuery("SELECT EXISTS(SELECT 1 FROM blocks WHERE blocker_id = :blockerId AND blocked_id = :blockedId)")
    @Override
    boolean isBlocked(@Bind("blockerId") UUID blockerId, @Bind("blockedId") UUID blockedId);

    @SqlQuery("SELECT blocked_id FROM blocks WHERE blocker_id = :userId")
    @Override
    Set<UUID> getBlockedUserIds(@Bind("userId") UUID userId);

    @SqlUpdate("DELETE FROM blocks WHERE blocker_id = :blockerId AND blocked_id = :blockedId")
    @Override
    void deleteBlock(@Bind("blockerId") UUID blockerId, @Bind("blockedId") UUID blockedId);

    // ═══════════════════════════════════════════════════════════════
    // Like Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("MERGE INTO likes (id, who_likes, who_got_liked, direction, created_at) KEY (id) VALUES (:id, :whoLikes, :whoGotLiked, :direction, :createdAt)")
    @Override
    void saveLike(@BindBean Like like);

    @SqlQuery("SELECT * FROM likes WHERE id = :id")
    @RegisterRowMapper(LikeRowMapper.class)
    @Override
    Optional<Like> getLike(@Bind("id") UUID id);

    @SqlQuery("SELECT * FROM likes WHERE who_likes = :userId ORDER BY created_at DESC")
    @RegisterRowMapper(LikeRowMapper.class)
    @Override
    List<Like> getLikesBy(@Bind("userId") UUID userId);

    @SqlQuery("SELECT * FROM likes WHERE who_got_liked = :userId ORDER BY created_at DESC")
    @RegisterRowMapper(LikeRowMapper.class)
    @Override
    List<Like> getLikesReceived(@Bind("userId") UUID userId);

    @SqlQuery("SELECT EXISTS(SELECT 1 FROM likes WHERE who_likes = :whoLikes AND who_got_liked = :whoGotLiked)")
    @Override
    boolean likeExists(@Bind("whoLikes") UUID whoLikes, @Bind("whoGotLiked") UUID whoGotLiked);

    @SqlQuery("""
        SELECT EXISTS(
            SELECT 1 FROM likes l1
            JOIN likes l2 ON l1.who_likes = l2.who_got_liked AND l1.who_got_liked = l2.who_likes
            WHERE l1.who_likes = :userA AND l1.who_got_liked = :userB
        )
        """)
    @Override
    boolean mutualLikeExists(@Bind("userA") UUID userA, @Bind("userB") UUID userB);

    @SqlQuery("SELECT who_got_liked FROM likes WHERE who_likes = :userId")
    @Override
    Set<UUID> getLikedOrPassedUserIds(@Bind("userId") UUID userId);

    @SqlUpdate("DELETE FROM likes WHERE id = :id")
    @Override
    void deleteLike(@Bind("id") UUID id);

    @SqlQuery("SELECT COUNT(*) FROM likes WHERE who_likes = :userId AND created_at >= CURRENT_DATE")
    @Override
    int countLikesToday(@Bind("userId") UUID userId);

    // ═══════════════════════════════════════════════════════════════
    // Report Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("MERGE INTO reports (id, reporter_id, reported_user_id, reason, description, created_at) KEY (id) VALUES (:id, :reporterId, :reportedUserId, :reason, :description, :createdAt)")
    @Override
    void saveReport(@BindBean Report report);

    @SqlQuery("SELECT * FROM reports WHERE id = :id")
    @RegisterRowMapper(ReportRowMapper.class)
    @Override
    Optional<Report> getReport(@Bind("id") UUID id);

    @SqlQuery("SELECT * FROM reports WHERE reported_user_id = :userId ORDER BY created_at DESC")
    @RegisterRowMapper(ReportRowMapper.class)
    @Override
    List<Report> getReportsAgainst(@Bind("userId") UUID userId);

    @SqlQuery("SELECT * FROM reports WHERE reporter_id = :reporterId ORDER BY created_at DESC")
    @RegisterRowMapper(ReportRowMapper.class)
    @Override
    List<Report> getReportsByReporter(@Bind("reporterId") UUID reporterId);

    @SqlQuery("SELECT COUNT(*) FROM reports WHERE reported_user_id = :userId")
    @Override
    int countReportsAgainst(@Bind("userId") UUID userId);

    // ═══════════════════════════════════════════════════════════════
    // Row Mappers
    // ═══════════════════════════════════════════════════════════════

    class BlockRowMapper implements RowMapper<Block> {
        @Override
        public Block map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Block(
                    MapperHelper.readUuid(rs, "id"),
                    MapperHelper.readUuid(rs, "blocker_id"),
                    MapperHelper.readUuid(rs, "blocked_id"),
                    MapperHelper.readInstant(rs, "created_at"));
        }
    }

    class LikeRowMapper implements RowMapper<Like> {
        @Override
        public Like map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Like(
                    MapperHelper.readUuid(rs, "id"),
                    MapperHelper.readUuid(rs, "who_likes"),
                    MapperHelper.readUuid(rs, "who_got_liked"),
                    MapperHelper.readEnum(rs, "direction", Like.Direction.class),
                    MapperHelper.readInstant(rs, "created_at"));
        }
    }

    class ReportRowMapper implements RowMapper<Report> {
        @Override
        public Report map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Report(
                    MapperHelper.readUuid(rs, "id"),
                    MapperHelper.readUuid(rs, "reporter_id"),
                    MapperHelper.readUuid(rs, "reported_user_id"),
                    MapperHelper.readEnum(rs, "reason", Report.Reason.class),
                    rs.getString("description"),
                    MapperHelper.readInstant(rs, "created_at"));
        }
    }
}
```

### Step 4.4: Repeat for Other Groups

Create similar consolidated interfaces and implementations for:

1. **MessagingStorage** (MessageStorage + ConversationStorage)
2. **SocialStorage** (FriendRequestStorage + NotificationStorage)
3. **StatsStorage** (UserStatsStorage + PlatformStatsStorage)

Follow the same pattern as UserInteractionStorage.

### Step 4.5: Update ServiceRegistry

**File:** `src/main/java/datingapp/core/ServiceRegistry.java`

1. Replace separate storage fields with consolidated ones:
   ```java
   // BEFORE
   private final BlockStorage blockStorage;
   private final LikeStorage likeStorage;
   private final ReportStorage reportStorage;

   // AFTER
   private final UserInteractionStorage userInteractionStorage;
   ```

2. Update constructor parameters

3. Update getter methods (or add convenience methods that delegate)

4. Update `Builder.buildH2()` to instantiate consolidated storage

### Step 4.6: Update All Service Constructors

Services that previously took `BlockStorage`, `LikeStorage`, `ReportStorage` separately now need to take `UserInteractionStorage`.

**Services to update:**
- `CandidateFinder`
- `MatchingService`
- `TrustSafetyService`
- Any other service that uses these storage interfaces

### Step 4.7: Delete Old Files

After all updates are complete and tests pass:

**Interface files to delete:**
- `core/storage/BlockStorage.java`
- `core/storage/LikeStorage.java`
- `core/storage/ReportStorage.java`
- `core/storage/MessageStorage.java`
- `core/storage/ConversationStorage.java`
- `core/storage/FriendRequestStorage.java`
- `core/storage/NotificationStorage.java`
- `core/storage/UserStatsStorage.java`
- `core/storage/PlatformStatsStorage.java`

**Implementation files to delete:**
- `storage/jdbi/JdbiBlockStorage.java`
- `storage/jdbi/JdbiLikeStorage.java`
- `storage/jdbi/JdbiReportStorage.java`
- `storage/jdbi/JdbiMessageStorage.java`
- `storage/jdbi/JdbiConversationStorage.java`
- `storage/jdbi/JdbiFriendRequestStorage.java`
- `storage/jdbi/JdbiNotificationStorage.java`
- `storage/jdbi/JdbiUserStatsStorage.java`
- `storage/jdbi/JdbiPlatformStatsStorage.java`

### Step 4.8: Validation Checklist

After completing Phase 4:

- [✓] 3 new consolidated interface files created (StatsStorage, MessagingStorage, SocialStorage)
- [✓] 3 new consolidated JDBI implementation files created
- [✓] 12 old interface/implementation files deleted
- [~] UserInteractionStorage intentionally deferred (LikeStorage has 13+ consumers)
- [✓] ServiceRegistry updated to use consolidated storage
- [✓] All service constructors updated
- [✓] No references to old storage interfaces (verified)
- [✓] Run tests:
  ```bash
  mvn verify
  ```
  **Result:** All tests pass (exit code 0)

### Phase 4 Gotchas

1. **Method signature conflicts:** Ensure no two merged interfaces have methods with the same name but different signatures.

2. **Test updates:** Tests that mock `BlockStorage` etc. will need to mock `UserInteractionStorage` instead.

3. **Do Phase 4 incrementally:** Consolidate one group at a time, run tests after each group.

4. **Keep services working:** If a service only needs block operations, it still gets the full `UserInteractionStorage` - that's fine, it just uses the methods it needs.

---

## Verification & Completion

### Final Verification Steps

After all phases complete:

```bash
# 1. Clean build
mvn clean compile

# 2. Run all tests
mvn test

# 3. Verify file count reduction
find src/main/java -name "*.java" | wc -l
# Expected: ~96 files (down from ~126)

# 4. Verify no broken imports
mvn compile 2>&1 | grep -i "cannot find symbol"
# Expected: No output

# 5. Run the application
mvn exec:java
# Expected: Application starts normally
```

### Completion Checklist

- [✓] **Phase 1:** CLI guards - 15 occurrences replaced with `requireLogin()`
- [✓] **Phase 2:** Mappers - Already complete (verified), inner classes in JDBI interfaces
- [✓] **Phase 3:** Modules - 7 files deleted, logic inlined to ServiceRegistry
- [✓] **Phase 4:** Storage - 12 files deleted, 3 consolidated interfaces created (UserInteractionStorage intentionally deferred)
- [✓] All tests pass (`mvn verify` exit code 0)
- [✓] Application compiles correctly
- [✓] Total main files reduced from ~107 to 97, LOC from ~29,978 to 25,759 (~14% reduction)

### Rollback Instructions

If something goes wrong:

1. **Phase-level rollback:** Each phase is independent (except Phase 4 depends on Phase 3). Git revert the commits for the failing phase.

2. **Full rollback:**
   ```bash
   git checkout main -- src/
   ```

---

## Appendix: Quick Reference

### File Paths Summary

| What               | Path                                                       |
|--------------------|------------------------------------------------------------|
| CLI Utilities      | `src/main/java/datingapp/cli/CliUtilities.java`            |
| ServiceRegistry    | `src/main/java/datingapp/core/ServiceRegistry.java`        |
| Mapper Helper      | `src/main/java/datingapp/storage/mapper/MapperHelper.java` |
| JDBI Storage       | `src/main/java/datingapp/storage/jdbi/`                    |
| Storage Interfaces | `src/main/java/datingapp/core/storage/`                    |
| Module Files       | `src/main/java/datingapp/module/` (DELETE all)             |

### Commands Summary

```bash
# Build
mvn clean compile

# Test
mvn test

# Run app
mvn exec:java

# Search for patterns
grep -r "pattern" src/main/java/

# Count files
find src/main/java -name "*.java" | wc -l
```

---

*Document created: 2026-01-30*
*Enhanced for AI agent execution: 2026-01-30*
*Ready for implementation*
