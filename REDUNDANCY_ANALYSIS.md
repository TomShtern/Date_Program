# Redundancy & Complexity Analysis

> **Generated:** 2026-01-31
> **Purpose:** Identify redundancies, duplications, and architectural issues preventing the codebase from being streamlined and simple.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Critical Issues: Parallel UI Systems](#critical-issues-parallel-ui-systems)
3. [High Priority: Storage Interface Fragmentation](#high-priority-storage-interface-fragmentation)
4. [Medium Priority: Service Inconsistencies](#medium-priority-service-inconsistencies)
5. [Minor Issues: Code Organization](#minor-issues-code-organization)
6. [Feature Parity Gap](#feature-parity-gap)
7. [Quantified Impact](#quantified-impact)
8. [Recommended Consolidation Strategy](#recommended-consolidation-strategy)

---

## Executive Summary

This codebase has **13 significant issues** stemming from a common anti-pattern: as features were added via different interfaces (CLI vs JavaFX GUI), code duplicated instead of sharing a common abstraction layer. The result is **two parallel systems doing the same things slightly differently**.

### Key Findings

| Category     | Issue                                                      | Impact                             |
|--------------|------------------------------------------------------------|------------------------------------|
| **Critical** | Duplicated business logic in CLI handlers vs UI ViewModels | ~2,000 lines of redundant code     |
| **Critical** | Two separate session management classes                    | Maintenance burden, potential bugs |
| **High**     | 13 storage interfaces (4+ are tiny and mergeable)          | ~20 unnecessary files              |
| **Medium**   | Inconsistent service registration                          | Confusing DI patterns              |
| **Minor**    | Giant ServiceRegistry (26 parameters)                      | Code smell, hard to extend         |

---

## Critical Issues: Parallel UI Systems

### Issue #1: Two Separate Session Management Classes

The codebase has **two independent classes** that track the currently logged-in user:

| File                                                              | Class         | Purpose                    |
|-------------------------------------------------------------------|---------------|----------------------------|
| `src/main/java/datingapp/app/cli/CliUtilities.java` (lines 39-74) | `UserSession` | Tracks current CLI user    |
| `src/main/java/datingapp/ui/ViewModelFactory.java` (lines 38-74)  | `UISession`   | Tracks current JavaFX user |

#### Side-by-Side Comparison

**CliUtilities.UserSession:**
```java
public static class UserSession {
    private User currentUser;

    public User getCurrentUser() { return currentUser; }
    public void setCurrentUser(User currentUser) { this.currentUser = currentUser; }
    public boolean isLoggedIn() { return currentUser != null; }
    public boolean isActive() {
        return currentUser != null && currentUser.getState() == User.State.ACTIVE;
    }
}
```

**ViewModelFactory.UISession:**
```java
public static final class UISession {
    private final ObjectProperty<User> currentUser = new SimpleObjectProperty<>();

    public User getCurrentUser() { return currentUser.get(); }
    public void setCurrentUser(User user) { this.currentUser.set(user); }
    public boolean isLoggedIn() { return currentUser.get() != null; }
    public boolean isActive() {
        User user = currentUser.get();
        return user != null && user.getState() == User.State.ACTIVE;
    }
    public void logout() { currentUser.set(null); }
}
```

**These are functionally identical.** The only difference is `UISession` uses JavaFX `ObjectProperty` for data binding.

#### Recommended Fix

Create a single `AppSession` class in `core/` package:

```java
// core/AppSession.java
public class AppSession {
    private static final AppSession INSTANCE = new AppSession();
    private User currentUser;
    private final List<Consumer<User>> listeners = new ArrayList<>();

    public static AppSession getInstance() { return INSTANCE; }

    public User getCurrentUser() { return currentUser; }
    public void setCurrentUser(User user) {
        this.currentUser = user;
        listeners.forEach(l -> l.accept(user));
    }
    public boolean isLoggedIn() { return currentUser != null; }
    public boolean isActive() {
        return currentUser != null && currentUser.getState() == User.State.ACTIVE;
    }
    public void addListener(Consumer<User> listener) { listeners.add(listener); }
}
```

The JavaFX layer can wrap this with an `ObjectProperty` adapter if needed.

---

### Issue #2: CLI Handlers Duplicate ViewModel Business Logic

This is the **most significant redundancy** in the codebase. Each CLI handler duplicates the logic that also exists in the corresponding UI ViewModel.

#### Example: MatchingHandler vs MatchingViewModel

| File                                                          | Lines | Purpose                    |
|---------------------------------------------------------------|-------|----------------------------|
| `src/main/java/datingapp/app/cli/MatchingHandler.java`        | 642   | CLI matching operations    |
| `src/main/java/datingapp/ui/viewmodel/MatchingViewModel.java` | 344   | JavaFX matching operations |

**Duplicated Operations:**

| Operation            | MatchingHandler Location                      | MatchingViewModel Location          |
|----------------------|-----------------------------------------------|-------------------------------------|
| Load candidates      | `browseCandidates()` lines 113-150            | `refreshCandidates()` lines 117-179 |
| Filter candidates    | lines 130-136                                 | lines 144-163                       |
| Process like/pass    | `processCandidateInteraction()` lines 152-222 | `processSwipe()` lines 206-230      |
| Record undo state    | line 218                                      | line 218                            |
| Check for match      | lines 205-213                                 | lines 223-227                       |
| Distance calculation | lines 156-158                                 | lines 304-311                       |

**Both call the exact same services:**
- `CandidateFinder.findCandidates()`
- `MatchingService.recordLike()`
- `UndoService.recordSwipe()`
- `LikeStorage.getLikedOrPassedUserIds()`
- `BlockStorage.getBlockedUserIds()`

#### Code Comparison: Candidate Loading

**MatchingHandler.browseCandidates() - lines 127-136:**
```java
List<User> activeUsers = userStorage.findActive();
Set<UUID> alreadyInteracted = likeStorage.getLikedOrPassedUserIds(currentUser.getId());
Set<UUID> blockedUsers = blockStorage.getBlockedUserIds(currentUser.getId());

Set<UUID> excluded = new HashSet<>(alreadyInteracted);
excluded.addAll(blockedUsers);

List<User> candidates = candidateFinderService.findCandidates(currentUser, activeUsers, excluded);
```

**MatchingViewModel.refreshCandidates() - lines 144-163:**
```java
List<User> activeUsers = userStorage.findActive();
// ... logging ...

Set<UUID> alreadyInteracted = likeStorage.getLikedOrPassedUserIds(currentUser.getId());
Set<UUID> blockedUsers = blockStorage.getBlockedUserIds(currentUser.getId());

Set<UUID> excluded = new HashSet<>(alreadyInteracted);
excluded.addAll(blockedUsers);

List<User> candidates = candidateFinder.findCandidates(currentUser, activeUsers, excluded);
```

**This is nearly identical code.**

#### Full Handler/ViewModel Duplication Matrix

| CLI Handler                   | UI ViewModel                    | Estimated Duplicated Lines |
|-------------------------------|---------------------------------|----------------------------|
| `MatchingHandler` (642 lines) | `MatchingViewModel` (344 lines) | ~250                       |
| `ProfileHandler`              | `ProfileViewModel`              | ~150                       |
| `MessagingHandler`            | `ChatViewModel`                 | ~100                       |
| `StatsHandler`                | `StatsViewModel`                | ~80                        |
| `SafetyHandler`               | (no equivalent)                 | N/A                        |
| `LikerBrowserHandler`         | `MatchesViewModel` (partial)    | ~100                       |
| `RelationshipHandler`         | (no equivalent)                 | N/A                        |
| `ProfileNotesHandler`         | (no equivalent)                 | N/A                        |

**Total estimated duplicated business logic: ~680-800 lines**

#### Recommended Fix

Create an **Application Services Layer** between handlers/viewmodels and core services:

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                    │
├────────────────────────┬────────────────────────────────┤
│   CLI Handlers         │   JavaFX ViewModels            │
│   (Console I/O only)   │   (UI binding only)            │
└────────────────────────┴────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│              Application Services Layer (NEW)            │
│  MatchingAppService, ProfileAppService, etc.            │
│  (Shared business logic, no UI dependencies)            │
└─────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    Core Services                         │
│  MatchingService, CandidateFinder, UndoService, etc.    │
└─────────────────────────────────────────────────────────┘
```

Example `MatchingAppService`:

```java
// core/app/MatchingAppService.java
public class MatchingAppService {
    private final CandidateFinder candidateFinder;
    private final MatchingService matchingService;
    private final UndoService undoService;
    // ... other dependencies

    public record SwipeResult(boolean matched, Match match, User candidate) {}

    public List<User> loadCandidates(User currentUser) {
        List<User> activeUsers = userStorage.findActive();
        Set<UUID> excluded = new HashSet<>(likeStorage.getLikedOrPassedUserIds(currentUser.getId()));
        excluded.addAll(blockStorage.getBlockedUserIds(currentUser.getId()));
        return candidateFinder.findCandidates(currentUser, activeUsers, excluded);
    }

    public SwipeResult processSwipe(User currentUser, User candidate, boolean liked) {
        Like.Direction direction = liked ? Like.Direction.LIKE : Like.Direction.PASS;
        Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
        Optional<Match> match = matchingService.recordLike(like);
        undoService.recordSwipe(currentUser.getId(), like, match.orElse(null));
        return new SwipeResult(match.isPresent(), match.orElse(null), candidate);
    }
}
```

Then handlers and viewmodels become thin wrappers:

```java
// CLI Handler becomes:
public void browseCandidates() {
    userSession.requireLogin(() -> {
        List<User> candidates = matchingAppService.loadCandidates(currentUser);
        // Just handle display and input
    });
}

// ViewModel becomes:
public void like() {
    SwipeResult result = matchingAppService.processSwipe(currentUser, candidate, true);
    if (result.matched()) {
        lastMatch.set(result.match());
    }
    nextCandidate();
}
```

---

### Issue #3: Two Entry Points with Identical Bootstrapping

Both `Main.java` and `DatingApp.java` perform the same initialization:

| Step           | Main.java                                                     | DatingApp.java                                               |
|----------------|---------------------------------------------------------------|--------------------------------------------------------------|
| Load config    | `AppConfig.defaults()` line 98                                | `AppConfig.defaults()` line 29                               |
| Get database   | `DatabaseManager.getInstance()` line 101                      | `DatabaseManager.getInstance()` line 33                      |
| Build registry | `ServiceRegistry.Builder.buildH2(dbManager, config)` line 104 | `ServiceRegistry.Builder.buildH2(dbManager, config)` line 38 |

#### Recommended Fix

Extract to a shared bootstrap class:

```java
// core/AppBootstrap.java
public final class AppBootstrap {
    private static ServiceRegistry services;
    private static DatabaseManager dbManager;

    public static ServiceRegistry initialize() {
        return initialize(AppConfig.defaults());
    }

    public static ServiceRegistry initialize(AppConfig config) {
        if (services == null) {
            dbManager = DatabaseManager.getInstance();
            services = ServiceRegistry.Builder.buildH2(dbManager, config);
        }
        return services;
    }

    public static void shutdown() {
        if (dbManager != null) {
            dbManager.shutdown();
        }
    }
}
```

Then entry points become:

```java
// Main.java
public static void main(String[] args) {
    ServiceRegistry services = AppBootstrap.initialize();
    // ... CLI setup
}

// DatingApp.java
@Override
public void init() {
    this.serviceRegistry = AppBootstrap.initialize();
    // ... JavaFX setup
}
```

---

## High Priority: Storage Interface Fragmentation

### Issue #4: Too Many Tiny Storage Interfaces

The codebase has **13 separate storage interfaces** in `core/storage/`. Several are very small and could be consolidated:

| Interface                | Methods | Lines | Could Merge Into                         |
|--------------------------|---------|-------|------------------------------------------|
| `DailyPickStorage`       | 3       | 37    | `UserStorage` or `DailyService` internal |
| `ProfileViewStorage`     | 5       | 54    | `StatsStorage` (analytics data)          |
| `ProfileNoteStorage`     | 4       | ~40   | `UserStorage` (user-related data)        |
| `UserAchievementStorage` | 4       | ~40   | `StatsStorage` (gamification data)       |

Each interface requires a **cascade of files**:

```
core/storage/DailyPickStorage.java          (interface)
storage/jdbi/JdbiDailyPickStorage.java      (implementation)
storage/mapper/DailyPickMapper.java         (row mapper, if needed)
ServiceRegistry field + getter              (2 lines)
ServiceRegistry.Builder instantiation       (1 line)
```

**4 tiny interfaces × ~5 files each = ~20 files that could be eliminated.**

#### Storage Interface Size Analysis

| Interface                | Method Count | Complexity   | Recommendation                         |
|--------------------------|--------------|--------------|----------------------------------------|
| `UserStorage`            | 6            | High         | Keep (core entity)                     |
| `LikeStorage`            | 10           | High         | Keep (complex queries)                 |
| `MatchStorage`           | 7            | Medium       | Keep (state management)                |
| `BlockStorage`           | 6            | Medium       | Keep (bidirectional queries)           |
| `MessagingStorage`       | 8            | High         | Keep (conversations + messages)        |
| `ReportStorage`          | 4            | Low          | Keep (safety-critical)                 |
| `SwipeSessionStorage`    | 7            | Medium       | Keep (session lifecycle)               |
| `StatsStorage`           | 5            | Medium       | Keep (time-series data)                |
| `SocialStorage`          | 6            | Medium       | Keep (friend requests + notifications) |
| `DailyPickStorage`       | 3            | **Very Low** | **Merge**                              |
| `ProfileViewStorage`     | 5            | **Low**      | **Merge**                              |
| `ProfileNoteStorage`     | 4            | **Low**      | **Merge**                              |
| `UserAchievementStorage` | 4            | **Low**      | **Merge**                              |

#### Recommended Consolidation

**Option A: Merge into existing interfaces**

```java
// Add to UserStorage
void markDailyPickViewed(UUID userId, LocalDate date);
boolean hasDailyPickViewed(UUID userId, LocalDate date);

// Add to StatsStorage
void recordProfileView(UUID viewerId, UUID viewedId);
int getProfileViewCount(UUID userId);
void saveUserAchievement(UserAchievement achievement);
List<UserAchievement> getUserAchievements(UUID userId);
```

**Option B: Create aggregate interfaces**

```java
// UserActivityStorage (combines tracking data)
interface UserActivityStorage {
    // Daily picks
    void markDailyPickViewed(UUID userId, LocalDate date);
    boolean hasDailyPickViewed(UUID userId, LocalDate date);

    // Profile views
    void recordProfileView(UUID viewerId, UUID viewedId);
    int getProfileViewCount(UUID userId);

    // Profile notes
    void saveNote(ProfileNote note);
    Optional<ProfileNote> getNote(UUID authorId, UUID subjectId);
}
```

---

## Medium Priority: Service Inconsistencies

### Issue #5: ValidationService Not in ServiceRegistry

`ValidationService` is created ad-hoc in `Main.java` but not registered:

```java
// Main.java line 111
ValidationService validationService = new ValidationService();

// ServiceRegistry has 12 services registered... but not this one
```

**Problems:**
1. Inconsistent with other services
2. Each handler that needs it must receive it manually
3. JavaFX UI has to create its own instance (or doesn't use validation)

#### Recommended Fix

Either:

**A) Add to ServiceRegistry:**
```java
// In ServiceRegistry.Builder.buildH2():
ValidationService validationService = new ValidationService();

// In ServiceRegistry:
private final ValidationService validationService;
public ValidationService getValidationService() { return validationService; }
```

**B) Make it a pure utility class with static methods:**
```java
public final class ValidationUtils {
    private ValidationUtils() {}

    public static ValidationResult validateName(String name) { ... }
    public static ValidationResult validateAge(int age, AppConfig config) { ... }
}
```

---

### Issue #6: ProfilePreviewService vs ProfileCompletionService Confusion

`ViewModelFactory` has this confusing comment:

```java
// ViewModelFactory.java line 171
profileViewModel = new ProfileViewModel(services.getUserStorage(), null);
// Comment: "ProfileCompletionService methods are static, so we pass null"
```

**Questions this raises:**
1. If `ProfileCompletionService` is all static, why is it a service?
2. Why does `ProfileViewModel` accept it as a constructor parameter if it's null?
3. `ProfilePreviewService` already has `calculateCompleteness()` — is there duplication?

#### Recommended Fix

1. Verify if `ProfileCompletionService` exists separately from `ProfilePreviewService`
2. If they overlap, merge them
3. If it's all static utilities, convert to utility class or merge into `ProfilePreviewService`

---

### Issue #7: MatchQualityService Only Used in CLI

`MatchingHandler` uses `MatchQualityService` for detailed compatibility scores:

```java
// MatchingHandler.java line 251
MatchQuality quality = matchQualityService.computeQuality(match, currentUser.getId());
```

But `MatchingViewModel` **reinvents a simplified version**:

```java
// MatchingViewModel.java lines 267-293
public String getCompatibilityDisplay(User candidate) {
    int score = 50; // Base score

    // Shared interests bonus (+5 per shared interest, max 25)
    if (currentUser.getInterests() != null && candidate.getInterests() != null) {
        long sharedCount = currentUser.getInterests().stream()
                .filter(i -> candidate.getInterests().contains(i))
                .count();
        score += Math.min((int) sharedCount * 5, 25);
    }

    // Age range match bonus (+15)
    // Distance (+10)

    return Math.min(score, 99) + "%";
}
```

**This is a duplicate, simplified implementation that:**
- Ignores lifestyle compatibility
- Ignores pace preferences
- Uses different weights than the real service
- Could produce inconsistent results between CLI and GUI

#### Recommended Fix

Inject `MatchQualityService` into `MatchingViewModel` and use it:

```java
// In MatchingViewModel
private final MatchQualityService matchQualityService;

public String getCompatibilityDisplay(User candidate) {
    // Use the real service
    MatchQuality quality = matchQualityService.computeQuality(currentUser, candidate);
    return quality.compatibilityScore() + "%";
}
```

---

## Minor Issues: Code Organization

### Issue #8: Container Classes Create Awkward Imports

The consolidation into container classes (done previously) means imports look like:

```java
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.Block;
import datingapp.core.Messaging.Message;
import datingapp.core.Messaging.Conversation;
import datingapp.core.Stats.UserStats;
```

Instead of the more natural:
```java
import datingapp.core.Like;
import datingapp.core.Message;
```

**Trade-off:** This is a minor readability issue vs. the benefit of fewer files. The current approach is acceptable but adds cognitive overhead.

---

### Issue #9: Giant ServiceRegistry Constructor (26 Parameters)

```java
// ServiceRegistry.java lines 96-122
@SuppressWarnings("java:S107")  // Suppressing "too many parameters" warning!
ServiceRegistry(
    AppConfig config,
    UserStorage userStorage,
    LikeStorage likeStorage,
    MatchStorage matchStorage,
    BlockStorage blockStorage,
    ReportStorage reportStorage,
    SwipeSessionStorage sessionStorage,
    StatsStorage statsStorage,
    DailyPickStorage dailyPickStorage,
    UserAchievementStorage userAchievementStorage,
    ProfileViewStorage profileViewStorage,
    ProfileNoteStorage profileNoteStorage,
    MessagingStorage messagingStorage,
    SocialStorage socialStorage,
    CandidateFinder candidateFinder,
    MatchingService matchingService,
    TrustSafetyService trustSafetyService,
    SessionService sessionService,
    StatsService statsService,
    MatchQualityService matchQualityService,
    ProfilePreviewService profilePreviewService,
    DailyService dailyService,
    UndoService undoService,
    AchievementService achievementService,
    MessagingService messagingService,
    RelationshipTransitionService relationshipTransitionService)
```

**This is a "God Object" code smell.** The `@SuppressWarnings` annotation acknowledges the problem!

#### Recommended Fix

Group into modules:

```java
public class ServiceRegistry {
    private final StorageModule storage;
    private final MatchingModule matching;
    private final MessagingModule messaging;
    private final SafetyModule safety;
    private final StatsModule stats;

    public record StorageModule(
        UserStorage users,
        LikeStorage likes,
        MatchStorage matches,
        BlockStorage blocks,
        // ... grouped logically
    ) {}

    // Access via: services.storage().users()
}
```

---

### Issue #10: Manual Handler Wiring in Main.java

Lines 114-162 of `Main.java` manually wire 8 handlers with 10+ dependencies each:

```java
matchingHandler = new MatchingHandler(new MatchingHandler.Dependencies(
    services.getCandidateFinder(),
    services.getMatchingService(),
    services.getLikeStorage(),
    services.getMatchStorage(),
    services.getBlockStorage(),
    services.getDailyService(),
    services.getUndoService(),
    services.getMatchQualityService(),
    services.getUserStorage(),
    services.getAchievementService(),
    services.getProfileViewStorage(),
    services.getRelationshipTransitionService(),
    userSession,
    inputReader
));
```

**The `Dependencies` record pattern adds boilerplate** — you're essentially declaring the same dependencies twice (in the record and in the handler fields).

#### Recommended Fix

Create a `HandlerFactory`:

```java
public class HandlerFactory {
    private final ServiceRegistry services;
    private final UserSession session;
    private final InputReader input;

    public MatchingHandler createMatchingHandler() {
        return new MatchingHandler(services, session, input);
    }

    public ProfileHandler createProfileHandler() {
        return new ProfileHandler(services, session, input);
    }
    // ...
}
```

And simplify handlers to take `ServiceRegistry` directly:

```java
public class MatchingHandler {
    public MatchingHandler(ServiceRegistry services, UserSession session, InputReader input) {
        this.candidateFinder = services.getCandidateFinder();
        this.matchingService = services.getMatchingService();
        // ...
    }
}
```

---

## Feature Parity Gap

### Issue #11: Features Present in CLI but Missing/Incomplete in JavaFX

| Feature                          | CLI Implementation                                                 | JavaFX Implementation              | Gap            |
|----------------------------------|--------------------------------------------------------------------|------------------------------------|----------------|
| **Daily Picks**                  | Full flow with Skip/Like/Pass in `MatchingHandler.showDailyPick()` | Not visible in `MatchingViewModel` | **Missing**    |
| **Match Quality Breakdown**      | Detailed scores, bars, highlights in `viewMatchDetails()`          | Simplified percentage only         | **Incomplete** |
| **Profile Verification**         | `SafetyHandler.verifyProfile()`                                    | No equivalent controller           | **Missing**    |
| **Liker Browser**                | `LikerBrowserHandler.browseWhoLikedMe()`                           | Partial in `MatchesController`?    | **Unclear**    |
| **Profile Notes**                | `ProfileNotesHandler` (full CRUD)                                  | No equivalent                      | **Missing**    |
| **Relationship Transitions**     | Friend Zone, Graceful Exit in `RelationshipHandler`                | No equivalent                      | **Missing**    |
| **Block/Report from Match View** | Available in `MatchingHandler.handleMatchDetailAction()`           | Unclear                            | **Unclear**    |

**This suggests the CLI is the "real" application and JavaFX is an incomplete port.**

---

## Quantified Impact

### Current State

| Metric                     | Count      | Notes                           |
|----------------------------|------------|---------------------------------|
| Total Java files           | ~126       | Production + test               |
| Storage interfaces         | 13         | 4+ are tiny/mergeable           |
| JDBI implementations       | 16         | Matches interfaces + adapters   |
| CLI handlers               | 8          | Full feature set                |
| UI ViewModels              | 8          | Incomplete feature set          |
| ServiceRegistry params     | 26         | Code smell                      |
| Estimated duplicated logic | ~800 lines | Between handlers and viewmodels |

### Potential After Consolidation

| Metric                  | Current      | After              | Reduction  |
|-------------------------|--------------|--------------------|------------|
| Storage interfaces      | 13           | 9                  | -4 files   |
| JDBI implementations    | 16           | 12                 | -4 files   |
| Session classes         | 2            | 1                  | -1 class   |
| Handler/ViewModel logic | ~2000 lines  | ~1200 lines        | -800 lines |
| ServiceRegistry params  | 26           | ~10 (with modules) | -16 params |
| Entry point duplication | 2 × 15 lines | 1 × 10 lines       | -20 lines  |

---

## Recommended Consolidation Strategy

### Phase 1: Quick Wins (Low Risk)

1. **Create `AppSession`** in `core/`
   - Single session class used by both CLI and JavaFX
   - Replace `CliUtilities.UserSession` and `ViewModelFactory.UISession`

2. **Create `AppBootstrap`** in `core/`
   - Single initialization point for both entry points
   - Extract duplicated code from `Main.java` and `DatingApp.java`

3. **Add `ValidationService` to `ServiceRegistry`**
   - Or convert to utility class with static methods

### Phase 2: Storage Consolidation (Medium Risk)

4. **Merge tiny storage interfaces:**
   - `DailyPickStorage` → into `DailyService` internal or `UserStorage`
   - `ProfileViewStorage` → into `StatsStorage`
   - `ProfileNoteStorage` → into `UserStorage`
   - `UserAchievementStorage` → into `StatsStorage`

5. **Group `ServiceRegistry` into modules:**
   - `StorageModule`, `MatchingModule`, `MessagingModule`, etc.

### Phase 3: Application Services Layer (Higher Effort)

6. **Create shared Application Services:**
   - `MatchingAppService` (shared by handler and viewmodel)
   - `ProfileAppService`
   - `MessagingAppService`
   - etc.

7. **Refactor handlers to be thin wrappers:**
   - CLI handlers: Console I/O + call AppService
   - ViewModels: UI binding + call AppService

### Phase 4: Feature Parity (If JavaFX is the future)

8. **Implement missing JavaFX features:**
   - Daily Picks
   - Profile Notes
   - Relationship Transitions
   - Full Match Quality display

9. **Or deprecate CLI:**
   - If JavaFX is the primary interface, consider deprecating CLI
   - Keep only for testing/debugging

---

## Appendix: File Impact Matrix

Files that would change with full consolidation:

| File                                           | Change Type          | Priority |
|------------------------------------------------|----------------------|----------|
| `core/AppSession.java`                         | **NEW**              | Phase 1  |
| `core/AppBootstrap.java`                       | **NEW**              | Phase 1  |
| `app/cli/CliUtilities.java`                    | Remove `UserSession` | Phase 1  |
| `ui/ViewModelFactory.java`                     | Remove `UISession`   | Phase 1  |
| `Main.java`                                    | Use `AppBootstrap`   | Phase 1  |
| `ui/DatingApp.java`                            | Use `AppBootstrap`   | Phase 1  |
| `core/storage/DailyPickStorage.java`           | **DELETE**           | Phase 2  |
| `core/storage/ProfileViewStorage.java`         | **DELETE**           | Phase 2  |
| `core/storage/ProfileNoteStorage.java`         | **DELETE**           | Phase 2  |
| `core/storage/UserAchievementStorage.java`     | **DELETE**           | Phase 2  |
| `storage/jdbi/JdbiDailyPickStorage.java`       | **DELETE**           | Phase 2  |
| `storage/jdbi/JdbiProfileViewStorage.java`     | **DELETE**           | Phase 2  |
| `storage/jdbi/JdbiProfileNoteStorage.java`     | **DELETE**           | Phase 2  |
| `storage/jdbi/JdbiUserAchievementStorage.java` | **DELETE**           | Phase 2  |
| `core/ServiceRegistry.java`                    | Modularize           | Phase 2  |
| `core/app/MatchingAppService.java`             | **NEW**              | Phase 3  |
| `core/app/ProfileAppService.java`              | **NEW**              | Phase 3  |
| `app/cli/MatchingHandler.java`                 | Thin wrapper         | Phase 3  |
| `ui/viewmodel/MatchingViewModel.java`          | Thin wrapper         | Phase 3  |

---

*Analysis generated 2026-01-31. Review with team before implementing changes.*
