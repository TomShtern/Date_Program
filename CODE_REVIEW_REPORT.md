# Code Architecture Review Report

**Date:** 2026-02-21  
**Scope:** Full codebase analysis (~139 Java files, ~45K LOC)  
**Goal:** Improve clarity, navigability, and maintainability for AI agents and humans

---

## Executive Summary

This project is a well-architected **dating application** built with Clean Architecture principles, featuring:
- **Three-layer architecture**: Core domain (pure Java), Storage (JDBI + H2), Presentation (CLI + JavaFX)
- **9 domain services** with clear dependency injection via `ServiceRegistry`
- **5 storage interfaces** with consolidated JDBI implementations
- **~800+ tests** with 60% minimum coverage on core layer

**Overall Assessment**: The architecture is sound and follows best practices. However, there are opportunities to improve **navigability for AI agents**, reduce **conceptual complexity**, and fix **known issues** that could confuse future developers.

---

## 1. Architecture Strengths

### 1.1 Clean Layer Separation
The project correctly enforces:
- ✅ **Zero framework imports in core/** - Pure domain logic
- ✅ **Storage interfaces in core/** - Dependency inversion
- ✅ **Concrete implementations in storage/** - JDBI adapters
- ✅ **Presentation layers depend on core** - No upward dependencies

### 1.2 Well-Designed Patterns
- **ServiceRegistry** as composition root
- **Builder pattern** for complex services (MatchingService, RecommendationService)
- **StorageBuilder** for database reconstruction
- **Result records** for error handling (never throw for business failures)
- **Touch pattern** for mutable entity timestamps
- **Nested types** for domain-owned enums (User.Gender, User.UserState)

### 1.3 Good Documentation
- `AGENTS.md` - Comprehensive coding standards and patterns
- `architecture.md` - System design with diagrams
- `PROJECT_STRUCTURE_GUIDE.md` - File-by-file explanations
- `CLAUDE.md` - Critical gotchas for AI agents

---

## 2. Issues Identified

### 2.1 CRITICAL: Thread Safety Issues

| Issue | Location | Impact |
|-------|----------|--------|
| Race condition in DatabaseManager | `storage/DatabaseManager.java:97-104` | Multiple schema initializations |
| Unsynchronized candidateQueue | `ui/viewmodel/MatchingViewModel.java:50` | ConcurrentModificationException |
| Non-volatile services field | `app/bootstrap/ApplicationStartup.java:26-27` | Stale ServiceRegistry returned |

**Recommendation**: Fix these immediately as documented in existing audits.

### 2.2 Complexity: Large Classes and Interfaces

#### Issue: Fat Interfaces in Storage Layer
The storage interfaces have grown too large over time:

| Interface | Method Count | Problem |
|-----------|-------------|---------|
| `InteractionStorage` | ~25 methods | Handles likes, matches, undo transactions |
| `CommunicationStorage` | ~30 methods | Messages, conversations, friend requests, notifications |
| `AnalyticsStorage` | ~25 methods | Stats, achievements, sessions, daily picks, profile views |

**Recommendation**: Consider splitting into smaller interfaces by **aggregate boundary**:
- `LikeStorage`, `MatchStorage` (from InteractionStorage)
- `MessageStorage`, `ConversationStorage`, `NotificationStorage` (from CommunicationStorage)
- `StatsStorage`, `AchievementStorage`, `SessionStorage` (from AnalyticsStorage)

This follows the original design intent noted in `architecture.md` lines 310-315.

#### Issue: ProfileService God Class
`core/profile/ProfileService.java` (~800 lines) handles:
- Profile completion scoring
- Achievement checking/unlocking
- Behavior analysis
- User listing

**Recommendation**: Split into:
- `ProfileCompletionService` - Completeness scoring, tips
- `AchievementService` - Achievement checking and unlocking
- Keep minimal `ProfileService` as facade

#### Issue: RecommendationService God Class
`core/matching/RecommendationService.java` (~600 lines) handles:
- Daily limits tracking
- Daily pick generation
- Standout computation
- Activity scoring

**Recommendation**: Split into:
- `DailyLimitService` - Daily like/pass limits
- `DailyPickService` - Seeded-random daily recommendations
- `StandoutService` - Standout candidate computation

### 2.3 Duplication: Logging Patterns

**Current State**: 41 static Logger declarations found across the codebase.

While `LoggingSupport` interface exists, many classes **implement it but still declare their own Logger**:

```java
// Problem: Redundant declaration
public class MatchingHandler implements LoggingSupport {
    private static final Logger logger = LoggerFactory.getLogger(MatchingHandler.class);
    // ...
}
```

**Recommendation**: 
1. Make `LoggingSupport` provide a default `logger()` method that derives the class name
2. Or use `@SuppressWarnings` and standard LoggerFactory pattern consistently

### 2.4 Inconsistent Naming Conventions

| Pattern | Location | Issue |
|---------|----------|-------|
| `*Handler` vs `*Service` | `app/cli/` | CLI uses Handler, core uses Service |
| `*Storage` vs `*Repository` | `core/storage/` | Some projects use Repository pattern |
| Duplicate services | Multiple | `TrustSafetyService` in `core/matching/`, also appears to exist elsewhere |

**Recommendation**: Standardize on:
- `*Service` for all business logic
- `*Storage` for data access interfaces
- `*Handler` should be UI-layer only (CLI controllers)

### 2.5 Duplicated Utility Code

#### GeoUtils Duplication
- `CandidateFinder.GeoUtils` (static nested class)
- `UserStorage.findCandidates()` has inline Haversine (lines 89-100)
- `CandidateFinder` has its own distance calculation

**Recommendation**: Create a single `GeoUtils` in `core/` and use everywhere.

#### Constants Scattered
- `ProfileService.java` lines 31-64: Profile scoring thresholds
- `MatchQualityService`: Match quality weights
- `AppConfig`: Algorithm weights

**Recommendation**: Move all threshold constants to `AppConfig` or a dedicated `ScoringConstants` class.

### 2.6 Complexity: Builder Pattern Overuse

Some services use builders but have too many optional dependencies:

```java
// MatchingService - 3 optional dependencies
MatchingService matchingService = MatchingService.builder()
    .interactionStorage(interactionStorage)
    .trustSafetyStorage(trustSafetyStorage)
    .userStorage(userStorage)
    .activityMetricsService(activityMetricsService)  // Optional - can be null
    .undoService(undoService)                        // Optional - can be null
    .dailyService(recommendationService)             // Optional - can be null
    .build();
```

**Recommendation**: 
- Use constructor injection with `@Nullable` annotations
- Or provide factory methods for common configurations

### 2.7 CLI Handler Dependency Injection

**Current**: Handlers receive all dependencies in constructor, but `MatchingHandler` has **12 dependencies**:

```java
public MatchingHandler(Dependencies dependencies) {
    this.candidateFinderService = dependencies.candidateFinderService();
    this.matchingService = dependencies.matchingService();
    // ... 10 more
}
```

**Recommendation**: Consider passing `ServiceRegistry` directly to handlers and letting them extract what they need. This reduces constructor bloat and makes adding new services easier (no handler changes needed).

### 2.8 Inconsistent Package Structure

```
core/
  ├── matching/
  │   ├── CandidateFinder.java
  │   ├── MatchingService.java
  │   ├── MatchQualityService.java
  │   ├── RecommendationService.java  ← Does daily limits + daily picks + standouts
  │   ├── Standout.java               ← Just a data class!
  │   ├── TrustSafetyService.java
  │   └── UndoService.java
  │
  ├── profile/
  │   ├── ProfileService.java        ← God class
  │   ├── MatchPreferences.java      ← Just enums and records
  │   └── ValidationService.java
  │
  └── metrics/
      ├── ActivityMetricsService.java
      ├── EngagementDomain.java      ← Enum + records (achievements, stats)
      └── SwipeState.java            ← Session + Undo records
```

**Recommendation**: 
1. Move `Standout.java` to a `recommendation/` package
2. Split `RecommendationService` into separate services
3. Consider renaming `EngagementDomain` to `AchievementDomain`

### 2.9 UI Layer: Controller/ViewModel Confusion

| Class | Problem |
|-------|---------|
| `ui/screen/MilestonePopupController.java` | Also exists as `ui/popup/MilestonePopupController.java` |
| Multiple screens | Some have popups, some don't - inconsistent structure |

**Recommendation**: 
- Remove duplicate `MilestonePopupController` 
- Standardize popup handling

---

## 3. Simplification Opportunities

### 3.1 Flatten Storage Interfaces

**Before** (current):
```java
public interface InteractionStorage {
    // 25 methods for likes + matches + undo
    Optional<Like> getLike(UUID from, UUID to);
    void save(Like like);
    boolean exists(UUID from, UUID to);
    boolean mutualLikeExists(UUID a, UUID b);
    // ... 20 more
}
```

**After** (simplified):
```java
// Keep as consolidated for now due to transactional requirements
// But split into inner interfaces for clarity:
public interface InteractionStorage {
    LikeOperations likes();
    MatchOperations matches();
    
    interface LikeOperations {
        Optional<Like> get(UUID from, UUID to);
        void save(Like like);
        // ...
    }
}
```

### 3.2 Remove Unnecessary Indirection

**Current**: CLI handlers use `Dependencies` inner class:
```java
public MatchingHandler(Dependencies dependencies) {
    this.candidateFinderService = dependencies.candidateFinderService();
}
// ...
interface Dependencies {
    CandidateFinder candidateFinderService();
    // 11 more methods
}
```

**Simplified**: Pass ServiceRegistry directly:
```java
public MatchingHandler(ServiceRegistry services) {
    this.candidateFinder = services.getCandidateFinder();
    // etc.
}
```

### 3.3 Consolidate Result Records

Many services define similar Result records:
- `MatchingService.LikeResult`
- `ConnectionService.SendResult`
- Various `*Result` records in services

**Recommendation**: Create common `Result<T>` type:
```java
public record Result<T>(boolean success, T data, String error) {
    public static <T> Result<T> ok(T data) { return new Result<>(true, data, null); }
    public static <T> Result<T> fail(String error) { return new Result<>(false, null, error); }
}
```

### 3.4 Reduce Test Boilerplate

**Current**: Tests manually create storage mocks:
```java
var userStorage = new TestStorages.Users();
var interactionStorage = new TestStorages.Interactions();
```

**Already improved** via `TestStorages` - this is good!

---

## 4. AI Agent Navigation Guide

### 4.1 Quick Decision Tree

```
Need to modify...                    → Look in...
─────────────────────────────────────────────────────────
User profile data/model              → core/model/User.java
Match logic                         → core/matching/MatchingService.java
Finding candidates                  → core/matching/CandidateFinder.java
Sending messages                    → core/connection/ConnectionService.java
User stats/achievements             → core/metrics/ActivityMetricsService.java
Database operations                → storage/jdbi/Jdbi*Storage.java
CLI menu flow                       → app/cli/*Handler.java
JavaFX UI screen                    → ui/screen/*Controller.java
JavaFX view logic                   → ui/viewmodel/*ViewModel.java
App configuration                   → core/AppConfig.java
Add new feature                     → Find related service, add method
```

### 4.2 Key Files for Understanding Flow

| Task | Entry Points |
|------|-------------|
| User registration | `ProfileHandler.createUser()` → `UserStorage.save()` |
| Swipe/Like | `MatchingHandler.handleSwipe()` → `MatchingService.recordLike()` |
| Match creation | `MatchingService` → `Match.create()` → `InteractionStorage.save()` |
| Send message | `MessagingHandler` → `ConnectionService.sendMessage()` |
| View matches | `MatchesHandler` → `InteractionStorage.getMatchesFor()` |

### 4.3 Common Patterns to Replicate

1. **Adding a new storage method**:
   - Add to interface in `core/storage/*Storage.java`
   - Implement in `storage/jdbi/Jdbi*Storage.java`

2. **Adding a new service**:
   - Create in appropriate `core/*/` package
   - Add to `ServiceRegistry`
   - Wire in `StorageFactory.buildH2()`

3. **Adding a new CLI command**:
   - Add method to existing `*Handler` or create new one
   - Register in `Main.java` menu

4. **Adding a new UI screen**:
   - Create FXML in `resources/fxml/`
   - Create Controller in `ui/screen/`
   - Create ViewModel in `ui/viewmodel/`
   - Add ViewType to `NavigationService.ViewType`
   - Register in `ViewModelFactory`

---

## 5. Prioritized Recommendations

### Priority 1: Fix Critical Bugs (Week 1)
| Action | Effort | Risk |
|--------|--------|------|
| Make DatabaseManager.initialized volatile | 15 min | Low |
| Add synchronization to MatchingViewModel.candidateQueue | 30 min | Low |
| Make ApplicationStartup fields volatile | 15 min | Low |

### Priority 2: Improve Navigability (Week 2)
| Action | Effort | Risk |
|--------|--------|------|
| Add comprehensive class-level Javadoc with "Quick reference" | 2h | Low |
| Create decision-tree documentation in AGENTS.md | 1h | Low |
| Consolidate LoggingSupport usage | 1h | Low |

### Priority 3: Reduce Complexity (Week 3-4)
| Action | Effort | Risk |
|--------|--------|------|
| Split ProfileService into ProfileCompletion + Achievement | 3h | Medium |
| Split RecommendationService into DailyLimit + DailyPick + Standout | 3h | Medium |
| Consolidate GeoUtils | 1h | Low |
| Remove duplicate MilestonePopupController | 30 min | Low |

### Priority 4: Long-term Improvements
| Action | Effort | Risk |
|--------|--------|------|
| Split storage interfaces by aggregate | 5h | Medium |
| Create common Result<T> type | 2h | Low |
| Standardize CLI handler DI pattern | 2h | Low |

---

## 6. Summary

This is a **well-designed** Clean Architecture project with clear separation of concerns. The main opportunities for improvement are:

1. **Fix critical thread safety issues** (already identified in audits)
2. **Reduce god classes** in services (ProfileService, RecommendationService)
3. **Improve AI agent navigation** with better documentation and decision trees
4. **Consolidate duplicated utilities** (GeoUtils, logging, constants)
5. **Standardize patterns** across CLI handlers

The codebase is in good shape for continued development. These recommendations will make it **more navigable for AI agents** and **easier to extend** for new features.

---

*Generated: 2026-02-21*
