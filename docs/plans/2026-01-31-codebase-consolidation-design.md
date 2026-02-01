# Codebase Consolidation Design

> **Created:** 2026-01-31
> **Status:** Approved
> **Goal:** Reduce complexity by eliminating duplication between CLI handlers and JavaFX ViewModels

---

## Context

The CLI runs **in parallel** with the JavaFX UI as a debug console for testing. Both interfaces need full feature access, but business logic should not be duplicated. The current codebase has ~800 lines of duplicated orchestration code.

**Key decision:** Enhance existing services rather than add a new Application Services Layer. This keeps file count low while eliminating duplication.

---

## Architecture Changes

### New Files (3)

| File                          | Purpose                                                           | Lines |
|-------------------------------|-------------------------------------------------------------------|-------|
| `core/AppSession.java`        | Unified session management for CLI + JavaFX with listener support | ~50   |
| `core/AppBootstrap.java`      | Centralized application initialization for both entry points      | ~40   |
| `app/cli/HandlerFactory.java` | Factory class for clean handler wiring in Main.java               | ~80   |

### Deleted Files (8)

| File                                           | Merged Into               |
|------------------------------------------------|---------------------------|
| `core/storage/DailyPickStorage.java`           | `DailyService` (internal) |
| `core/storage/ProfileViewStorage.java`         | `StatsStorage`            |
| `core/storage/ProfileNoteStorage.java`         | `UserStorage`             |
| `core/storage/UserAchievementStorage.java`     | `StatsStorage`            |
| `storage/jdbi/JdbiDailyPickStorage.java`       | `JdbiDailyService`        |
| `storage/jdbi/JdbiProfileViewStorage.java`     | `JdbiStatsStorage`        |
| `storage/jdbi/JdbiProfileNoteStorage.java`     | `JdbiUserStorage`         |
| `storage/jdbi/JdbiUserAchievementStorage.java` | `JdbiStatsStorage`        |

---

## Detailed Design

### 1. AppSession — Unified Session Management

**Location:** `src/main/java/datingapp/core/AppSession.java`

Replaces both `CliUtilities.UserSession` and `ViewModelFactory.UISession` with a single class that supports both plain access (CLI) and listener-based binding (JavaFX).

```java
package datingapp.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class AppSession {
    private static final AppSession INSTANCE = new AppSession();

    private User currentUser;
    private final List<Consumer<User>> listeners = new CopyOnWriteArrayList<>();

    private AppSession() {}

    public static AppSession getInstance() { return INSTANCE; }

    public User getCurrentUser() { return currentUser; }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        notifyListeners(user);
    }

    public boolean isLoggedIn() { return currentUser != null; }

    public boolean isActive() {
        return currentUser != null && currentUser.getState() == User.State.ACTIVE;
    }

    public void logout() { setCurrentUser(null); }

    public void addListener(Consumer<User> listener) { listeners.add(listener); }
    public void removeListener(Consumer<User> listener) { listeners.remove(listener); }

    private void notifyListeners(User user) {
        for (Consumer<User> listener : listeners) {
            listener.accept(user);
        }
    }

    public void reset() {
        currentUser = null;
        listeners.clear();
    }
}
```

**JavaFX Integration:** ViewModelFactory creates an adapter that wraps AppSession with ObjectProperty:

```java
AppSession.getInstance().addListener(user ->
    Platform.runLater(() -> currentUserProperty.set(user))
);
```

---

### 2. AppBootstrap — Centralized Initialization

**Location:** `src/main/java/datingapp/core/AppBootstrap.java`

Eliminates duplicated initialization code between `Main.java` and `DatingApp.java`.

```java
package datingapp.core;

import datingapp.storage.DatabaseManager;

public final class AppBootstrap {
    private static ServiceRegistry services;
    private static DatabaseManager dbManager;
    private static boolean initialized = false;

    private AppBootstrap() {}

    public static synchronized ServiceRegistry initialize() {
        return initialize(AppConfig.defaults());
    }

    public static synchronized ServiceRegistry initialize(AppConfig config) {
        if (!initialized) {
            dbManager = DatabaseManager.getInstance();
            services = ServiceRegistry.Builder.buildH2(dbManager, config);
            initialized = true;
        }
        return services;
    }

    public static ServiceRegistry getServices() {
        if (!initialized) {
            throw new IllegalStateException("AppBootstrap.initialize() must be called first");
        }
        return services;
    }

    public static synchronized void shutdown() {
        if (dbManager != null) {
            dbManager.shutdown();
        }
        initialized = false;
        services = null;
        dbManager = null;
    }

    public static synchronized void reset() {
        shutdown();
        AppSession.getInstance().reset();
    }
}
```

**Usage in Main.java:**
```java
public static void main(String[] args) {
    ServiceRegistry services = AppBootstrap.initialize();
    // ... CLI setup ...
    AppBootstrap.shutdown();
}
```

**Usage in DatingApp.java:**
```java
@Override
public void init() {
    this.serviceRegistry = AppBootstrap.initialize();
}

@Override
public void stop() {
    AppBootstrap.shutdown();
}
```

---

### 3. Service Enhancements

#### CandidateFinder — Add `findCandidatesForUser()`

**New dependencies:** `UserStorage`, `LikeStorage`, `BlockStorage`

```java
public List<User> findCandidatesForUser(User currentUser) {
    List<User> activeUsers = userStorage.findActive();
    Set<UUID> excluded = new HashSet<>(likeStorage.getLikedOrPassedUserIds(currentUser.getId()));
    excluded.addAll(blockStorage.getBlockedUserIds(currentUser.getId()));
    return findCandidates(currentUser, activeUsers, excluded);
}
```

**Impact:** Eliminates 5 duplicated lines in both MatchingHandler and MatchingViewModel.

#### MatchingService — Add `processSwipe()` with `SwipeResult`

**New dependencies:** `UndoService`, `DailyService`

```java
public record SwipeResult(
    boolean success,
    boolean matched,
    Match match,
    Like like,
    String message
) {
    public static SwipeResult matched(Match m, Like l) {
        return new SwipeResult(true, true, m, l, "It's a match!");
    }
    public static SwipeResult liked(Like l) {
        return new SwipeResult(true, false, null, l, "Liked!");
    }
    public static SwipeResult passed(Like l) {
        return new SwipeResult(true, false, null, l, "Passed.");
    }
    public static SwipeResult dailyLimitReached() {
        return new SwipeResult(false, false, null, null, "Daily like limit reached.");
    }
}

public SwipeResult processSwipe(User currentUser, User candidate, boolean liked) {
    if (liked && !dailyService.canLike(currentUser.getId())) {
        return SwipeResult.dailyLimitReached();
    }

    Like.Direction direction = liked ? Like.Direction.LIKE : Like.Direction.PASS;
    Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
    Optional<Match> match = recordLike(like);
    undoService.recordSwipe(currentUser.getId(), like, match.orElse(null));

    if (match.isPresent()) return SwipeResult.matched(match.get(), like);
    return liked ? SwipeResult.liked(like) : SwipeResult.passed(like);
}
```

**Impact:** Eliminates 6 duplicated lines in both MatchingHandler and MatchingViewModel.

---

### 4. Storage Merges

#### StatsStorage — Add ProfileView + Achievement methods

```java
// Profile view tracking (from ProfileViewStorage)
void recordProfileView(UUID viewerId, UUID viewedId);
int getProfileViewCount(UUID userId);
List<UUID> getRecentViewers(UUID userId, int limit);
Instant getLastViewedAt(UUID viewerId, UUID viewedId);
boolean hasViewed(UUID viewerId, UUID viewedId);

// Achievement tracking (from UserAchievementStorage)
void saveUserAchievement(UUID userId, Achievement achievement, Instant earnedAt);
List<Achievement> getUserAchievements(UUID userId);
boolean hasAchievement(UUID userId, Achievement achievement);
int getAchievementCount(UUID userId);
```

#### UserStorage — Add ProfileNote methods

```java
// Profile notes (from ProfileNoteStorage)
void saveProfileNote(ProfileNote note);
Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId);
List<ProfileNote> getNotesBy(UUID authorId);
void deleteProfileNote(UUID authorId, UUID subjectId);
```

#### DailyService — Internalize DailyPick storage

Move DailyPickStorage methods directly into JdbiDailyService or DailyService implementation. The interface was only used by DailyService anyway.

---

### 5. HandlerFactory

**Location:** `src/main/java/datingapp/app/cli/HandlerFactory.java`

```java
public final class HandlerFactory {
    private final ServiceRegistry services;
    private final AppSession session;
    private final CliUtilities.InputReader inputReader;

    // Lazily-created handlers
    private MatchingHandler matchingHandler;
    private ProfileHandler profileHandler;
    // ... other handlers ...

    public HandlerFactory(ServiceRegistry services, AppSession session) {
        this.services = services;
        this.session = session;
        this.inputReader = new CliUtilities.InputReader();
    }

    public MatchingHandler matching() {
        if (matchingHandler == null) {
            matchingHandler = new MatchingHandler(
                services.getCandidateFinder(),
                services.getMatchingService(),
                services.getUserStorage(),
                services.getMatchQualityService(),
                services.getAchievementService(),
                session,
                inputReader
            );
        }
        return matchingHandler;
    }

    // ... similar methods for other handlers ...
}
```

---

### 6. Other Changes

- **ServiceRegistry:** Add `ValidationService`, remove getters for merged storage interfaces
- **MatchingViewModel:** Inject `MatchQualityService` instead of duplicating compatibility calculation
- **CliUtilities:** Remove `UserSession` class
- **ViewModelFactory:** Remove `UISession` class, add AppSession adapter

---

## Impact Summary

| Metric                 | Before | After | Change |
|------------------------|--------|-------|--------|
| Storage interfaces     | 13     | 9     | -4     |
| Total Java files       | ~136   | ~131  | -5     |
| Duplicated LOC         | ~800   | ~200  | -600   |
| ServiceRegistry params | 26     | 22    | -4     |

---

## Decisions Made

| Decision              | Choice                               | Rationale                           |
|-----------------------|--------------------------------------|-------------------------------------|
| Architecture approach | Enhance existing services (Option C) | Fewer files, simpler than new layer |
| AppBootstrap          | Yes, create AppBootstrap             | Single init point, clean shutdown   |
| Session unification   | Yes, create AppSession               | Eliminates identical code           |
| Storage merges        | All 4 merged                         | Clear targets, -8 files             |
| ServiceRegistry       | Keep flat                            | 22 params acceptable after merges   |
| ValidationService     | Add to ServiceRegistry               | Consistent with other services      |
| MatchQualityService   | Inject into ViewModels               | Eliminate duplicate calculations    |
| Handler wiring        | HandlerFactory                       | Cleaner Main.java                   |

---

# AI Agent Implementation Instructions

> **For AI Coding Agents:** Follow these steps sequentially. Each phase must pass `mvn test` before proceeding to the next. Run `mvn spotless:apply` before any commit.

---

## Phase 1: Foundation (AppSession + AppBootstrap) ✅ COMPLETE

### ✅ Step 1.1: Create AppSession

**Task:** Create `src/main/java/datingapp/core/AppSession.java`

**Instructions:**
1. Create the file with the code from Section 1 above
2. Package: `datingapp.core`
3. Imports needed: `java.util.List`, `java.util.concurrent.CopyOnWriteArrayList`, `java.util.function.Consumer`
4. Run `mvn compile` to verify no syntax errors

**Verification:** `mvn compile` succeeds

### ✅ Step 1.2: Create AppBootstrap

**Task:** Create `src/main/java/datingapp/core/AppBootstrap.java`

**Instructions:**
1. Create the file with the code from Section 2 above
2. Package: `datingapp.core`
3. Imports needed: `datingapp.storage.DatabaseManager`
4. Run `mvn compile` to verify no syntax errors

**Verification:** `mvn compile` succeeds

### ✅ Step 1.3: Update Main.java to use AppBootstrap

**Task:** Refactor `src/main/java/datingapp/app/Main.java`

**Instructions:**
1. Find the existing initialization code (around lines 98-104):
   ```java
   AppConfig config = AppConfig.defaults();
   DatabaseManager dbManager = DatabaseManager.getInstance();
   ServiceRegistry services = ServiceRegistry.Builder.buildH2(dbManager, config);
   ```
2. Replace with:
   ```java
   ServiceRegistry services = AppBootstrap.initialize();
   ```
3. Find the shutdown code (in finally block or at end) and replace with:
   ```java
   AppBootstrap.shutdown();
   ```
4. Add import: `import datingapp.core.AppBootstrap;`
5. Remove unused imports for `DatabaseManager` if no longer used directly

**Verification:** `mvn compile` succeeds, `mvn exec:java` starts the CLI

### ✅ Step 1.4: Update DatingApp.java to use AppBootstrap

**Task:** Refactor `src/main/java/datingapp/ui/DatingApp.java`

**Instructions:**
1. In the `init()` method, replace initialization code with:
   ```java
   this.serviceRegistry = AppBootstrap.initialize();
   ```
2. In the `stop()` method (or add one if missing), add:
   ```java
   AppBootstrap.shutdown();
   ```
3. Add import: `import datingapp.core.AppBootstrap;`
4. Remove unused imports

**Verification:** `mvn compile` succeeds, `mvn javafx:run` launches the app

### ✅ Step 1.5: Update CliUtilities to use AppSession

**Task:** Refactor `src/main/java/datingapp/app/cli/CliUtilities.java`

**Instructions:**
1. Find the `UserSession` inner class (around lines 39-74)
2. Delete the entire `UserSession` class
3. Update any code in CliUtilities that references `UserSession` to use `AppSession.getInstance()` instead
4. Add import: `import datingapp.core.AppSession;`

**Verification:** `mvn compile` succeeds

### ✅ Step 1.6: Update CLI handlers to use AppSession

**Task:** Update all CLI handlers that use `UserSession`

**Files to check:**
- `MatchingHandler.java`
- `ProfileHandler.java`
- `MessagingHandler.java`
- `StatsHandler.java`
- `SafetyHandler.java`
- `LikerBrowserHandler.java`
- `RelationshipHandler.java`
- `ProfileNotesHandler.java`

**Instructions for each file:**
1. Find constructor parameters or fields of type `CliUtilities.UserSession` or `UserSession`
2. Replace with `AppSession`
3. Update field assignments to use `AppSession.getInstance()` or accept it as parameter
4. Update imports

**Verification:** `mvn compile` succeeds

### ✅ Step 1.7: Update ViewModelFactory to use AppSession

**Task:** Refactor `src/main/java/datingapp/ui/ViewModelFactory.java`

**Instructions:**
1. Find the `UISession` inner class (around lines 38-74)
2. Delete the entire `UISession` class
3. Replace with AppSession adapter pattern:
   ```java
   private final ObjectProperty<User> currentUserProperty = new SimpleObjectProperty<>();

   private void initializeSessionBinding() {
       AppSession.getInstance().addListener(user ->
           Platform.runLater(() -> currentUserProperty.set(user))
       );
       // Sync initial state
       currentUserProperty.set(AppSession.getInstance().getCurrentUser());
   }
   ```
4. Call `initializeSessionBinding()` in constructor
5. Update any code that used `UISession` to use `AppSession.getInstance()` or the property
6. Add imports: `import datingapp.core.AppSession;`

**Verification:** `mvn compile` succeeds

### ✅ Step 1.8: Run full test suite

**Task:** Verify Phase 1 complete

**Instructions:**
1. Run `mvn spotless:apply`
2. Run `mvn test`
3. Fix any failures before proceeding

**Verification:** All tests pass

---

## Phase 2: Storage Merges ✅ COMPLETE

### ✅ Step 2.1: Analyze current storage interfaces

**Task:** Read and understand the 4 interfaces to be merged

**Files to read:**
- `src/main/java/datingapp/core/storage/DailyPickStorage.java`
- `src/main/java/datingapp/core/storage/ProfileViewStorage.java`
- `src/main/java/datingapp/core/storage/ProfileNoteStorage.java`
- `src/main/java/datingapp/core/storage/UserAchievementStorage.java`

**Instructions:**
1. List all methods from each interface
2. Identify the target interface for each merge
3. Note any special types (e.g., `ProfileNote`, return types)

**No code changes in this step.**

### ✅ Step 2.2: Merge ProfileViewStorage into StatsStorage

**Task:** Add ProfileViewStorage methods to StatsStorage interface

**Instructions:**
1. Open `src/main/java/datingapp/core/storage/StatsStorage.java`
2. Add the following methods (copy from ProfileViewStorage):
   ```java
   // Profile view tracking
   void recordProfileView(UUID viewerId, UUID viewedId);
   int getProfileViewCount(UUID userId);
   List<UUID> getRecentViewers(UUID userId, int limit);
   Instant getLastViewedAt(UUID viewerId, UUID viewedId);
   boolean hasViewed(UUID viewerId, UUID viewedId);
   ```
3. Add necessary imports (`java.util.UUID`, `java.time.Instant`, `java.util.List`)
4. Open `src/main/java/datingapp/storage/jdbi/JdbiStatsStorage.java`
5. Implement the new methods by copying SQL from `JdbiProfileViewStorage`
6. Verify compile: `mvn compile`

### ✅ Step 2.3: Merge UserAchievementStorage into StatsStorage

**Task:** Add UserAchievementStorage methods to StatsStorage interface

**Instructions:**
1. Open `src/main/java/datingapp/core/storage/StatsStorage.java`
2. Add the following methods (copy from UserAchievementStorage):
   ```java
   // Achievement tracking
   void saveUserAchievement(UUID userId, Achievement achievement, Instant earnedAt);
   List<Achievement> getUserAchievements(UUID userId);
   boolean hasAchievement(UUID userId, Achievement achievement);
   int getAchievementCount(UUID userId);
   ```
3. Add import for `Achievement`
4. Open `src/main/java/datingapp/storage/jdbi/JdbiStatsStorage.java`
5. Implement the new methods by copying SQL from `JdbiUserAchievementStorage`
6. Verify compile: `mvn compile`

### ✅ Step 2.4: Merge ProfileNoteStorage into UserStorage

**Task:** Add ProfileNoteStorage methods to UserStorage interface

**Instructions:**
1. Open `src/main/java/datingapp/core/storage/UserStorage.java`
2. Add the following methods (copy from ProfileNoteStorage):
   ```java
   // Profile notes
   void saveProfileNote(ProfileNote note);
   Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId);
   List<ProfileNote> getNotesBy(UUID authorId);
   void deleteProfileNote(UUID authorId, UUID subjectId);
   ```
3. Add imports for `ProfileNote`, `Optional`
4. Open `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
5. Implement the new methods by copying SQL from `JdbiProfileNoteStorage`
6. Verify compile: `mvn compile`

### ✅ Step 2.5: Internalize DailyPickStorage into DailyService

**Task:** Move DailyPickStorage methods into DailyService

**Instructions:**
1. Open `src/main/java/datingapp/core/DailyService.java`
2. The service likely already has a `DailyPickStorage` field
3. Add public methods that delegate to or replace the storage calls:
   ```java
   public void markDailyPickViewed(UUID userId, LocalDate date) { ... }
   public boolean hasDailyPickViewed(UUID userId, LocalDate date) { ... }
   ```
4. If using JDBI, move the SQL annotations into the service's JDBI implementation
5. Verify compile: `mvn compile`

### ✅ Step 2.6: Update all callers of merged interfaces

**Task:** Find and update all code that uses the old interfaces

**Instructions:**
1. Use grep to find all usages:
   ```bash
   rg "ProfileViewStorage" --type java
   rg "UserAchievementStorage" --type java
   rg "ProfileNoteStorage" --type java
   rg "DailyPickStorage" --type java
   ```
2. For each usage:
   - If injected via constructor, change to the new target interface
   - Update method calls if method names changed
3. Update `ServiceRegistry`:
   - Remove getters for deleted interfaces
   - Update constructor to not require deleted interfaces
4. Update `ServiceRegistry.Builder.buildH2()` to not create deleted implementations
5. Verify compile: `mvn compile`

### ✅ Step 2.7: Delete old storage files

**Task:** Remove the merged storage interfaces and implementations

**Files to delete:**
- `src/main/java/datingapp/core/storage/DailyPickStorage.java`
- `src/main/java/datingapp/core/storage/ProfileViewStorage.java`
- `src/main/java/datingapp/core/storage/ProfileNoteStorage.java`
- `src/main/java/datingapp/core/storage/UserAchievementStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiDailyPickStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiProfileViewStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiProfileNoteStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiUserAchievementStorage.java`

**Instructions:**
1. Delete each file
2. Run `mvn compile` to verify no broken imports
3. Fix any remaining references

**Verification:** `mvn compile` succeeds with 8 fewer files

### ✅ Step 2.8: Run full test suite

**Task:** Verify Phase 2 complete

**Instructions:**
1. Run `mvn spotless:apply`
2. Run `mvn test`
3. Fix any failures before proceeding

**Verification:** All tests pass

---

---

### Prior phases (1–2): COMPLETE ✅✅

Phases 1 and 2 have been completed and verified. Proceeding to Phase 3.

---

## Phase 3: Service Enhancements

### Step 3.1: Enhance CandidateFinder with new dependencies

**Task:** Add UserStorage, LikeStorage, BlockStorage to CandidateFinder

**Instructions:**
1. Open `src/main/java/datingapp/core/CandidateFinder.java`
2. Add new fields:
   ```java
   private final UserStorage userStorage;
   private final LikeStorage likeStorage;
   private final BlockStorage blockStorage;
   ```
3. Update constructor to accept these parameters
4. Add the convenience method:
   ```java
   public List<User> findCandidatesForUser(User currentUser) {
       List<User> activeUsers = userStorage.findActive();
       Set<UUID> excluded = new HashSet<>(likeStorage.getLikedOrPassedUserIds(currentUser.getId()));
       excluded.addAll(blockStorage.getBlockedUserIds(currentUser.getId()));
       return findCandidates(currentUser, activeUsers, excluded);
   }
   ```
5. Add necessary imports
6. Update `ServiceRegistry.Builder` to pass new dependencies to CandidateFinder

**Verification:** `mvn compile` succeeds

### Step 3.2: Enhance MatchingService with SwipeResult

**Task:** Add SwipeResult record and processSwipe() method

**Instructions:**
1. Open `src/main/java/datingapp/core/MatchingService.java`
2. Add new fields:
   ```java
   private final UndoService undoService;
   private final DailyService dailyService;
   ```
3. Update constructor to accept these parameters
4. Add the `SwipeResult` record (can be nested class or separate file):
   ```java
   public record SwipeResult(boolean success, boolean matched, Match match, Like like, String message) {
       public static SwipeResult matched(Match m, Like l) { return new SwipeResult(true, true, m, l, "It's a match!"); }
       public static SwipeResult liked(Like l) { return new SwipeResult(true, false, null, l, "Liked!"); }
       public static SwipeResult passed(Like l) { return new SwipeResult(true, false, null, l, "Passed."); }
       public static SwipeResult dailyLimitReached() { return new SwipeResult(false, false, null, null, "Daily like limit reached."); }
   }
   ```
5. Add the `processSwipe()` method as shown in Section 3 above
6. Update `ServiceRegistry.Builder` to pass new dependencies to MatchingService

**Verification:** `mvn compile` succeeds

### Step 3.3: Refactor MatchingHandler to use new methods

**Task:** Simplify MatchingHandler using findCandidatesForUser() and processSwipe()

**Instructions:**
1. Open `src/main/java/datingapp/app/cli/MatchingHandler.java`
2. Find `browseCandidates()` method
3. Replace the candidate loading code (5 lines) with:
   ```java
   List<User> candidates = candidateFinderService.findCandidatesForUser(currentUser);
   ```
4. Find `processCandidateInteraction()` method
5. Replace the swipe processing code with:
   ```java
   SwipeResult result = matchingService.processSwipe(currentUser, candidate, "l".equals(action));
   if (!result.success()) {
       logger.info(result.message());
       return false;
   }
   logger.info(result.message());
   if (result.matched()) {
       checkAndDisplayNewAchievements(currentUser);
   }
   ```
6. Remove now-unused fields/dependencies if any
7. Update imports

**Verification:** `mvn compile` succeeds

### Step 3.4: Refactor MatchingViewModel to use new methods

**Task:** Simplify MatchingViewModel using findCandidatesForUser() and processSwipe()

**Instructions:**
1. Open `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
2. Find `refreshCandidates()` method
3. Replace the candidate loading code with:
   ```java
   List<User> candidates = candidateFinder.findCandidatesForUser(currentUser);
   ```
4. Find `processSwipe()` method
5. Replace the swipe processing code with:
   ```java
   SwipeResult result = matchingService.processSwipe(currentUser, candidate, liked);
   if (result.matched()) {
       lastMatch.set(result.match());
       matchedUser.set(candidate);
   }
   ```
6. Remove now-unused fields (likeStorage, blockStorage if only used for this)
7. Update constructor if dependencies changed

**Verification:** `mvn compile` succeeds

### Step 3.5: Inject MatchQualityService into MatchingViewModel

**Task:** Remove duplicate compatibility calculation

**Instructions:**
1. Open `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java`
2. Add field: `private final MatchQualityService matchQualityService;`
3. Update constructor to accept MatchQualityService
4. Find `getCompatibilityDisplay()` method (around lines 267-293)
5. Replace the entire method body with:
   ```java
   public String getCompatibilityDisplay(User candidate) {
       MatchQuality quality = matchQualityService.computeQuality(currentUser, candidate);
       return quality.compatibilityScore() + "%";
   }
   ```
6. Update ViewModelFactory to pass MatchQualityService to MatchingViewModel

**Verification:** `mvn compile` succeeds

### Step 3.6: Add ValidationService to ServiceRegistry

**Task:** Register ValidationService consistently

**Instructions:**
1. Open `src/main/java/datingapp/core/ServiceRegistry.java`
2. Add field: `private final ValidationService validationService;`
3. Add getter: `public ValidationService getValidationService() { return validationService; }`
4. Update constructor to accept ValidationService
5. Update `ServiceRegistry.Builder.buildH2()` to create and pass ValidationService
6. Update Main.java to get ValidationService from registry instead of creating directly

**Verification:** `mvn compile` succeeds

### Step 3.7: Run full test suite

**Task:** Verify Phase 3 complete

**Instructions:**
1. Run `mvn spotless:apply`
2. Run `mvn test`
3. Fix any failures before proceeding

**Verification:** All tests pass

---

## Phase 4: Cleanup (HandlerFactory)

### Step 4.1: Create HandlerFactory

**Task:** Create `src/main/java/datingapp/app/cli/HandlerFactory.java`

**Instructions:**
1. Create the file with the code from Section 5 above
2. Add lazy creation methods for all 8 handlers:
   - `matching()` → MatchingHandler
   - `profile()` → ProfileHandler
   - `messaging()` → MessagingHandler
   - `stats()` → StatsHandler
   - `safety()` → SafetyHandler
   - `likerBrowser()` → LikerBrowserHandler
   - `relationship()` → RelationshipHandler
   - `profileNotes()` → ProfileNotesHandler
3. Each method should create the handler with correct dependencies from ServiceRegistry

**Verification:** `mvn compile` succeeds

### Step 4.2: Refactor Main.java to use HandlerFactory

**Task:** Replace manual handler wiring with HandlerFactory

**Instructions:**
1. Open `src/main/java/datingapp/app/Main.java`
2. Find the handler instantiation code (around lines 114-162)
3. Replace with:
   ```java
   HandlerFactory handlers = new HandlerFactory(services, AppSession.getInstance());
   ```
4. Update the menu loop to use `handlers.matching()`, `handlers.profile()`, etc.
5. Remove the individual handler field declarations if no longer needed
6. Remove unused imports

**Verification:** `mvn compile` succeeds, `mvn exec:java` works

### Step 4.3: Final cleanup

**Task:** Remove any remaining dead code

**Instructions:**
1. Run `mvn spotless:apply`
2. Check for unused imports in all modified files
3. Check for unused fields in ServiceRegistry
4. Remove any TODO comments that are now resolved

### Step 4.4: Run full verification

**Task:** Verify entire consolidation complete

**Instructions:**
1. Run `mvn clean compile` (clean build)
2. Run `mvn test` (all tests)
3. Run `mvn spotless:check` (formatting)
4. Run `mvn exec:java` (CLI works)
5. Run `mvn javafx:run` (JavaFX works)
6. Count files: should be ~131 Java files (was ~136)

**Verification:** All checks pass

---

## Completion Checklist

- [x] **Phase 1: AppSession and AppBootstrap created and integrated** ✅ (2026-01-31 22:17)
  - Created [AppSession.java](../../src/main/java/datingapp/core/AppSession.java) - Unified session singleton for CLI + JavaFX
  - Created [AppBootstrap.java](../../src/main/java/datingapp/core/AppBootstrap.java) - Centralized initialization
  - Updated Main.java and DatingApp.java to use AppBootstrap
  - Removed CliUtilities.UserSession and ViewModelFactory.UISession
  - Updated all 8 CLI handlers to use AppSession
  - Updated all 8 JavaFX ViewModels and 4 Controllers to use AppSession
  - Fixed test singleton state issues (added reset() calls in setUp())
  - **All 588 tests passing** ✅
- [x] **Phase 2: 4 storage interfaces merged, 8 files deleted** ✅ (2026-02-01 14:30)
  - **Phase 2.1:** Deleted ProfileViewStorage.java (moved to StatsStorage)
  - **Phase 2.2:** Deleted JdbiProfileViewStorage.java (merged into JdbiStatsStorage)
  - **Phase 2.3:** Deleted UserAchievementStorage.java (moved to StatsStorage)
  - **Phase 2.4:** Deleted JdbiUserAchievementStorage.java (merged into JdbiStatsStorage)
  - **Phase 2.5:** Deleted ProfileNoteStorage.java (moved to UserStorage)
  - **Phase 2.6:** Deleted JdbiProfileNoteStorage.java (merged into JdbiUserStorage)
  - **Phase 2.7:** Deleted DailyPickStorage.java (internalized into DailyService)
  - **Phase 2.8:** Deleted JdbiDailyPickStorage.java (internalized into DailyService via JdbiStatsStorage)
  - Updated StatsStorage and JdbiStatsStorage with 9 new methods (profile views + achievements)
  - Updated UserStorage and JdbiUserStorage with 4 new methods (profile notes)
  - Updated ServiceRegistry, AchievementService, MatchingHandler, ProfileNotesHandler, Main.java callers
  - Fixed all compilation errors in adapters and tests
  - **All 581 tests passing** ✅
  - **Completion Summary:** 8 files successfully deleted, 4 storage interfaces consolidated into 2, ~120 LOC removed
- [ ] Phase 3: CandidateFinder and MatchingService enhanced
- [ ] Phase 4: HandlerFactory created, Main.java cleaned up
- [ ] All tests pass (`mvn test`)
- [ ] Formatting correct (`mvn spotless:check`)
- [ ] CLI works (`mvn exec:java`)
- [ ] JavaFX works (`mvn javafx:run`)

---

## Phase 2 Completion Summary (2026-02-01)

**Objective:** Consolidate 4 redundant storage interfaces and their JDBI implementations into 2 core interfaces.

**Files Deleted (8 total):**
1. `src/main/java/datingapp/core/storage/ProfileViewStorage.java` → Methods merged to StatsStorage
2. `src/main/java/datingapp/core/storage/UserAchievementStorage.java` → Methods merged to StatsStorage
3. `src/main/java/datingapp/core/storage/ProfileNoteStorage.java` → Methods merged to UserStorage
4. `src/main/java/datingapp/core/storage/DailyPickStorage.java` → Internalized in DailyService
5. `src/main/java/datingapp/storage/jdbi/JdbiProfileViewStorage.java` → Merged to JdbiStatsStorage
6. `src/main/java/datingapp/storage/jdbi/JdbiUserAchievementStorage.java` → Merged to JdbiStatsStorage
7. `src/main/java/datingapp/storage/jdbi/JdbiProfileNoteStorage.java` → Merged to JdbiUserStorage
8. `src/main/java/datingapp/storage/jdbi/JdbiDailyPickStorage.java` → Internalized in DailyService

**Methods Added:**
- StatsStorage: `recordProfileView()`, `getProfileViewCount()`, `getRecentViewers()`, `getLastViewedAt()`, `hasViewed()`, `saveUserAchievement()`, `getUserAchievements()`, `hasAchievement()`, `getAchievementCount()` (9 methods)
- UserStorage: `saveProfileNote()`, `getProfileNote()`, `getNotesBy()`, `deleteProfileNote()` (4 methods)

**Files Updated:**
- ServiceRegistry.java - Removed 4 getter methods, updated constructor
- AchievementService.java - Changed dependency from UserAchievementStorage to StatsStorage
- MatchingHandler.java - Updated achievement tracking calls
- ProfileNotesHandler.java - Updated profile note calls
- Main.java - Removed 4 storage instantiations
- All affected test helper classes

**Test Status:** 581 tests passing (no regressions)

**Code Reduction:** ~120 lines of duplicated/boilerplate code eliminated

**Storage Interfaces:** Reduced from 13 to 9 interfaces

---

*Design approved 2026-01-31. Phase 2 completed 2026-02-01. Implementation instructions for AI agents included.*
