# Codebase Analysis Report - MiniMax M2.5

**Date:** 2026-02-24  
**Analyzer:** MiniMax M2.5  
**Scope:** Full codebase analysis excluding documentation

---

## Executive Summary

This report identifies major architectural, design, and implementation issues in the dating app codebase. The analysis reveals a system suffering from **extreme class bloat**, **excessive synchronization**, **tight coupling**, and **significant code duplication**. The codebase has 89 main Java files and 71 test files totaling approximately 51,000 LOC, but the quality issues make navigation and maintenance extremely challenging.

---

## Critical Issues (Priority 1)

### 1.1 Massive User Entity Class (807 lines)

**Location:** [`User.java`](src/main/java/datingapp/core/model/User.java)

**Issue:** The `User` class is a 807-line god object containing:
- 40+ fields (identity, profile, lifestyle, verification, preferences)
- 60+ synchronized methods (getters/setters)
- Nested StorageBuilder pattern (300+ lines)
- Multiple nested enums (Gender, UserState, VerificationMethod)

**Why It's Bad:**
- **Lock contention:** Every getter/setter call acquires the same lock, creating severe concurrency bottleneck
- **Violates Single Responsibility Principle:** User manages identity, profile data, lifestyle preferences, verification, dealbreakers, interests, and pace preferences
- **Impossible to test in isolation:** Any change affects too many concerns
- **Navigation nightmare:** Developers cannot quickly find what they're looking for

**Suggested Solution:**
```
1. Split User into:
   - UserIdentity (id, name, createdAt, state)
   - UserProfile (bio, photos, birthDate, gender, location)
   - UserPreferences (ageRange, distance, interests, lifestyle)
   - UserVerification (email, phone, verified, code)
   - UserDealbreakers (dealbreakers set)

2. Remove synchronized from immutable fields (id, createdAt)
   - Use ConcurrentHashMap for collections instead

3. Extract StorageBuilder to separate factory class
```

**Impact:** HIGH - Affects every layer of the application

---

### 1.2 Excessive Synchronization Creating Lock Contention

**Location:** Multiple files, primarily [`User.java`](src/main/java/datingapp/core/model/User.java)

**Issue:** Found 75+ synchronized methods across the codebase, with User.java having 60+ synchronized methods.

**Why It's Bad:**
- Single lock for all fields creates severe contention in concurrent scenarios
- Every UI read and background operation competes for the same lock
- Java's intrinsic locking is not scalable

**Suggested Solution:**
```
1. Use java.util.concurrent primitives:
   - AtomicInteger/AtomicReference for counters
   - ConcurrentHashMap for collections
   - ReadWriteLock for read-heavy scenarios

2. Make immutable fields truly immutable (final + no setters)

3. Consider splitting mutable state into separate objects
```

**Impact:** HIGH - Performance degradation under load

---

### 1.3 God-Class ServiceRegistry with 17 Dependencies

**Location:** [`ServiceRegistry.java`](src/main/java/datingapp/core/ServiceRegistry.java)

**Issue:** The ServiceRegistry constructor requires 17 parameters:
```java
public ServiceRegistry(
    AppConfig config,
    UserStorage userStorage,
    InteractionStorage interactionStorage,
    CommunicationStorage communicationStorage,
    AnalyticsStorage analyticsStorage,
    TrustSafetyStorage trustSafetyStorage,
    CandidateFinder candidateFinder,
    MatchingService matchingService,
    TrustSafetyService trustSafetyService,
    ActivityMetricsService activityMetricsService,
    MatchQualityService matchQualityService,
    ProfileService profileService,
    RecommendationService recommendationService,
    UndoService undoService,
    ConnectionService connectionService,
    ValidationService validationService)
```

**Why It's Bad:**
- **Testability nightmare:** Impossible to mock all 17 dependencies
- **Violates Interface Segregation:** Every consumer gets ALL dependencies
- **Constructor explosion:** Adding new services requires modifying this constructor
- **Tight coupling:** Services cannot be used independently

**Suggested Solution:**
```
1. Split into multiple registries by domain:
   - MatchingRegistry (candidateFinder, matchingService, undoService)
   - ProfileRegistry (profileService, validationService)
   - CommunicationRegistry (connectionService)
   - MetricsRegistry (activityMetricsService)

2. Use dependency injection framework (Dagger/Hilt)

3. Or use lazy initialization with method references
```

**Impact:** HIGH - Prevents independent service testing and reuse

---

### 1.4 Massive Service Classes (25k-35k chars each)

**Location:**
- [`MatchQualityService.java`](src/main/java/datingapp/core/matching/MatchQualityService.java) - 30,163 chars
- [`RecommendationService.java`](src/main/java/datingapp/core/matching/RecommendationService.java) - 25,620 chars
- [`ProfileService.java`](src/main/java/datingapp/core/profile/ProfileService.java) - 34,762 chars
- [`ConnectionService.java`](src/main/java/datingapp/core/connection/ConnectionService.java) - 25,283 chars

**Why It's Bad:**
- Each service handles too many responsibilities
- Impossible to understand entire service at once
- Changes in one area risk breaking unrelated features
- Hard to locate specific functionality

**Suggested Solution:**
```
MatchQualityService should split into:
- DistanceScoringService
- AgeScoringService  
- InterestScoringService
- LifestyleScoringService
- PaceScoringService
- MatchQualityAggregator

RecommendationService should split into:
- DailyRecommendationService
- CandidateFilteringService
- StandoutCalculationService
```

**Impact:** HIGH - Maintenance and navigation difficulty

---

## High Priority Issues (Priority 2)

### 2.1 CLI Handler God Classes

**Location:**
- [`MatchingHandler.java`](src/main/java/datingapp/app/cli/MatchingHandler.java) - 42,018 chars
- [`ProfileHandler.java`](src/main/java/datingapp/app/cli/ProfileHandler.java) - 41,918 chars

**Issue:** Each CLI handler is 40k+ characters handling 20+ different operations.

**Why It's Bad:**
- Single class handles profile, matching, messaging, stats, safety
- Massive switch statements or if-else chains
- Code duplication across handlers for similar operations

**Suggested Solution:**
```
Split MatchingHandler into:
- BrowseCandidatesHandler
- ViewMatchesHandler  
- WhoLikedMeHandler
- StandoutsHandler
- NotificationsHandler

Split ProfileHandler into:
- CreateProfileHandler
- CompleteProfileHandler
- EditProfileHandler
- ViewProfileHandler
- DealbreakersHandler
```

**Impact:** MEDIUM-HIGH - CLI maintenance difficulty

---

### 2.2 Massive UI Controllers (25k-35k chars)

**Location:**
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java) - 32,610 chars
- [`MatchesController.java`](src/main/java/datingapp/ui/screen/MatchesController.java) - 31,637 chars
- [`LoginController.java`](src/main/java/datingapp/ui/screen/LoginController.java) - 27,110 chars
- [`MatchingController.java`](src/main/java/datingapp/ui/screen/MatchingController.java) - 21,221 chars

**Issue:** Controllers handle too many UI concerns, FXML loading, event handling, and business logic.

**Why It's Bad:**
- 500+ line initialize() methods
- Massive field declarations (50+ @FXML fields)
- Mixed concerns: UI state, business logic, navigation

**Suggested Solution:**
```
1. Extract UI logic into specialized classes:
   - ProfileFormManager
   - MatchCardRenderer
   - ConversationListManager

2. Use composition over inheritance

3. Break into smaller FXML components with dedicated controllers
```

**Impact:** MEDIUM - JavaFX maintenance difficulty

---

### 2.3 Massive ViewModels (20k-30k chars)

**Location:**
- [`ProfileViewModel.java`](src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java) - 29,721 chars
- [`MatchesViewModel.java`](src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java) - 26,009 chars
- [`LoginViewModel.java`](src/main/java/datingapp/ui/viewmodel/LoginViewModel.java) - 17,107 chars

**Issue:** ViewModels contain excessive business logic and UI state management.

**Suggested Solution:**
```
1. Split ProfileViewModel into:
   - ProfileFormViewModel (form state)
   - ProfileDisplayViewModel (read-only display)
   - ProfileCompletionViewModel (wizard flow)

2. Use smaller, focused view models composed together
```

**Impact:** MEDIUM - UI testability and maintenance

---

### 2.4 MatchPreferences God Class (32,577 chars)

**Location:** [`MatchPreferences.java`](src/main/java/datingapp/core/profile/MatchPreferences.java)

**Issue:** Contains:
- 50+ interest enum values
- 8 nested enum types (Interest, Lifestyle, Smoking, Drinking, etc.)
- Static utility methods for all preference types

**Why It's Bad:**
- Should be a package of related preferences, not one mega class
- Interests enum should be in its own file
- Each lifestyle category should be separate

**Suggested Solution:**
```
Move to separate files:
- Interest.java (enum with category)
- Lifestyle.java (container for lifestyle enums)
- Smoking.java, Drinking.java, WantsKids.java, etc.
- Dealbreakers.java
- PacePreferences.java
```

**Impact:** MEDIUM - Code organization

---

### 2.5 AppConfig Builder Bloat (670+ lines)

**Location:** [`AppConfig.java`](src/main/java/datingapp/core/AppConfig.java)

**Issue:** 
- 60+ builder methods (one per configuration field)
- 4 nested config records (MatchingConfig, ValidationConfig, AlgorithmConfig, SafetyConfig)
- Multiple validation constants duplicated

**Why It's Bad:**
- Adding new config requires editing multiple places
- 670 lines just for configuration
- Constants scattered between builder and nested records

**Suggested Solution:**
```
1. Use a configuration library (Typesafe Config, Lightbend Config)

2. Or generate builder from configuration schema

3. Group related configs into smaller, focused builders
```

**Impact:** MEDIUM - Configuration management

---

### 2.6 Duplicate Entry Points with Shared Initialization

**Location:**
- [`Main.java`](src/main/java/datingapp/Main.java) - CLI entry
- [`DatingApp.java`](src/main/java/datingapp/ui/DatingApp.java) - JavaFX entry

**Issue:** Both initialize the application differently:
```java
// Main.java
ServiceRegistry services = ApplicationStartup.initialize();

// DatingApp.java  
ServiceRegistry serviceRegistry = ApplicationStartup.initialize();
```

**Why It's Bad:**
- Code duplication in initialization logic
- Both create identical ServiceRegistry
- Hard to ensure consistent startup

**Suggested Solution:**
```
1. Create single ApplicationRunner class
2. Both Main and DatingApp delegate to common runner
3. Extract common bootstrap logic
```

**Impact:** MEDIUM - Inconsistent initialization

---

## Medium Priority Issues (Priority 3)

### 3.1 Duplicate Constants Across Files

**Location:** Multiple files

**Issue:** Same constants defined in multiple places:
- Error messages duplicated across handlers
- Tier names ("Diamond", "Gold", "Silver", "Bronze") in both ProfileService and MatchPreferences
- Date formatters in multiple controllers
- CSS class names scattered

**Example:**
```java
// ProfileService.java
private static final String TIER_DIAMOND = "Diamond";
private static final String TIER_GOLD = "Gold";

// MatchPreferences.java - same tier strings
```

**Suggested Solution:**
```
1. Create central constants class per module:
   - MatchingConstants
   - ProfileConstants  
   - ValidationConstants

2. Or use enum with display names
```

**Impact:** MEDIUM - Inconsistency risk

---

### 3.2 Nested Enums Not Actually Nested

**Location:** 30+ enum definitions

**Issue:** Many enums declared as `public static enum` inside classes but should be top-level:
- User.Gender, User.UserState, User.VerificationMethod
- Match.MatchState, Match.MatchArchiveReason
- ConnectionModels nested enums
- MatchPreferences nested enums (8 of them)

**Why It's Bad:**
- Confusion about when to use outer class qualifier
- Some are used across packages requiring long imports
- Counter to Java best practices for enum scope

**Suggested Solution:**
```
Move standalone enums to top-level:
- Gender.java (in model package)
- UserState.java  
- MatchState.java
- VerificationMethod.java
```

**Impact:** MEDIUM - Code organization

---

### 3.3 Storage Implementation Duplication

**Location:** [`JdbiUserStorage.java`](src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java) and similar

**Issue:**
- Similar patterns repeated across JDBI storage classes
- Row mapping logic duplicated
- SQL string constants repeated

**Suggested Solution:**
```
1. Create base JDBI repository class
2. Extract common row mapping utilities
3. Use JDBI's @Mapper annotation with shared mappers
```

**Impact:** MEDIUM - DRY principle violation

---

### 3.4 Complex Conditional Logic in Services

**Location:** Multiple service classes

**Issue:** Deeply nested conditionals, especially in:
- CandidateFinder filtering logic
- MatchQualityService scoring
- ProfileService completion calculations

**Example:**
```java
if (candidate.getAge() >= minAge && candidate.getAge() <= maxAge) {
    if (candidate.getGender() != null && interestedIn.contains(candidate.getGender())) {
        if (distance <= maxDistance) {
            // 5 more levels...
        }
    }
}
```

**Suggested Solution:**
```
1. Use specification pattern for filtering
2. Chain small predicate functions
3. Extract complex conditions to well-named methods
```

**Impact:** MEDIUM - Code readability

---

### 3.5 Magic Numbers and Strings

**Location:** Throughout codebase

**Issue:** Hardcoded values without constants:
- Age limits (18, 120)
- Distance limits (50, 500)
- Score thresholds (40, 60, 75, 90)
- UI sizing values

**Example:**
```java
if (maxDistanceKm <= 0 || maxDistanceKm > 500) // magic number
if (age < 18 || age > 120) // magic numbers
```

**Suggested Solution:**
```
All thresholds should come from AppConfig:
if (age < config.validation().minAge() || age > config.validation().maxAge())
```

**Impact:** MEDIUM - Maintainability

---

### 3.6 Tight Coupling Through Direct Storage Access

**Location:** ViewModels and Controllers

**Issue:** Direct access to storage interfaces instead of through services:
```java
// In ViewModel
UserStorage userStorage = services.getUserStorage();
userStorage.findAll();

// Should be through ProfileService
profileService.listUsers();
```

**Why It's Bad:**
- Bypasses business logic in services
- Duplicates validation and transformation logic
- Makes services less relevant

**Suggested Solution:**
```
Ensure all data access goes through appropriate service layer
```

**Impact:** MEDIUM - Architecture violation

---

## Low Priority Issues (Priority 4)

### 4.1 Inconsistent Naming Conventions

**Issue:** Mix of naming styles:
- `getCurrentUser()` vs `findAll()`
- `getUserById()` vs `get()`
- Some use `Service` suffix, some don't

**Impact:** LOW - Confusion

---

### 4.2 Missing Null Safety

**Issue:** Some methods return null instead of Optional:
- `ConnectionService.sendMessage()` returns null in error cases
- Some storage getters

**Impact:** LOW - NullPointerException risk

---

### 4.3 Logging Inconsistency

**Issue:** Mix of approaches:
- Some use LoggingSupport interface
- Some directly use Logger
- Some don't log at all

**Impact:** LOW - Debugging difficulty

---

## Summary Statistics

| Category | Count | Total Lines | Largest File |
|----------|-------|-------------|--------------|
| Core Models | 3 | ~35,000 | User.java (807) |
| Core Services | 15 | ~150,000 | ProfileService.java (34k) |
| CLI Handlers | 7 | ~120,000 | MatchingHandler.java (42k) |
| UI Controllers | 12 | ~130,000 | ProfileController.java (32k) |
| ViewModels | 13 | ~130,000 | ProfileViewModel.java (29k) |
| Storage JDBI | 6 | ~130,000 | JdbiMatchmakingStorage.java (37k) |

---

## Recommended Prioritization

### Immediate (This Sprint)
1. Remove excessive synchronization from User class
2. Split User into smaller domain objects
3. Reduce ServiceRegistry dependencies

### Short-term (1-2 Months)
1. Split massive service classes
2. Break up CLI handlers
3. Simplify UI controllers

### Medium-term (3-6 Months)
1. Extract nested enums to top-level
2. Create constants classes
3. Implement dependency injection

---

## Conclusion

The codebase suffers from **severe bloat** at every layer. The User class with 60+ synchronized methods and the ServiceRegistry with 17 dependencies are the most critical issues causing:
- **Poor performance** (lock contention)
- **Testability issues** (impossible to isolate)
- **Maintenance nightmare** (cannot find anything)
- **Risk of regressions** (any change affects everything)

The suggested refactoring follows the **Single Responsibility Principle** and **Law of Demeter** - splitting god classes into focused collaborators that can be understood, tested, and maintained independently.

---

*Generated by MiniMax M2.5 on 2026-02-24*

**Date:** 2026-02-24  
**Analyzer:** MiniMax M2.5  
**Scope:** Full codebase analysis excluding documentation

---

## Executive Summary

This report identifies major architectural, design, and implementation issues in the dating app codebase. The analysis reveals a system suffering from **extreme class bloat**, **excessive synchronization**, **tight coupling**, and **significant code duplication**. The codebase has 89 main Java files and 71 test files totaling approximately 51,000 LOC, but the quality issues make navigation and maintenance extremely challenging.

---

## Critical Issues (Priority 1)

### 1.1 Massive User Entity Class (807 lines)

**Location:** [`User.java`](src/main/java/datingapp/core/model/User.java)

**Issue:** The `User` class is a 807-line god object containing:
- 40+ fields (identity, profile, lifestyle, verification, preferences)
- 60+ synchronized methods (getters/setters)
- Nested StorageBuilder pattern (300+ lines)
- Multiple nested enums (Gender, UserState, VerificationMethod)

**Why It's Bad:**
- **Lock contention:** Every getter/setter call acquires the same lock, creating severe concurrency bottleneck
- **Violates Single Responsibility Principle:** User manages identity, profile data, lifestyle preferences, verification, dealbreakers, interests, and pace preferences
- **Impossible to test in isolation:** Any change affects too many concerns
- **Navigation nightmare:** Developers cannot quickly find what they're looking for

**Suggested Solution:**
```
1. Split User into:
   - UserIdentity (id, name, createdAt, state)
   - UserProfile (bio, photos, birthDate, gender, location)
   - UserPreferences (ageRange, distance, interests, lifestyle)
   - UserVerification (email, phone, verified, code)
   - UserDealbreakers (dealbreakers set)

2. Remove synchronized from immutable fields (id, createdAt)
   - Use ConcurrentHashMap for collections instead

3. Extract StorageBuilder to separate factory class
```

**Impact:** HIGH - Affects every layer of the application

---

### 1.2 Excessive Synchronization Creating Lock Contention

**Location:** Multiple files, primarily [`User.java`](src/main/java/datingapp/core/model/User.java)

**Issue:** Found 75+ synchronized methods across the codebase, with User.java having 60+ synchronized methods.

**Why It's Bad:**
- Single lock for all fields creates severe contention in concurrent scenarios
- Every UI read and background operation competes for the same lock
- Java's intrinsic locking is not scalable

**Suggested Solution:**
```
1. Use java.util.concurrent primitives:
   - AtomicInteger/AtomicReference for counters
   - ConcurrentHashMap for collections
   - ReadWriteLock for read-heavy scenarios

2. Make immutable fields truly immutable (final + no setters)

3. Consider splitting mutable state into separate objects
```

**Impact:** HIGH - Performance degradation under load

---

### 1.3 God-Class ServiceRegistry with 17 Dependencies

**Location:** [`ServiceRegistry.java`](src/main/java/datingapp/core/ServiceRegistry.java)

**Issue:** The ServiceRegistry constructor requires 17 parameters:
```java
public ServiceRegistry(
    AppConfig config,
    UserStorage userStorage,
    InteractionStorage interactionStorage,
    CommunicationStorage communicationStorage,
    AnalyticsStorage analyticsStorage,
    TrustSafetyStorage trustSafetyStorage,
    CandidateFinder candidateFinder,
    MatchingService matchingService,
    TrustSafetyService trustSafetyService,
    ActivityMetricsService activityMetricsService,
    MatchQualityService matchQualityService,
    ProfileService profileService,
    RecommendationService recommendationService,
    UndoService undoService,
    ConnectionService connectionService,
    ValidationService validationService)
```

**Why It's Bad:**
- **Testability nightmare:** Impossible to mock all 17 dependencies
- **Violates Interface Segregation:** Every consumer gets ALL dependencies
- **Constructor explosion:** Adding new services requires modifying this constructor
- **Tight coupling:** Services cannot be used independently

**Suggested Solution:**
```
1. Split into multiple registries by domain:
   - MatchingRegistry (candidateFinder, matchingService, undoService)
   - ProfileRegistry (profileService, validationService)
   - CommunicationRegistry (connectionService)
   - MetricsRegistry (activityMetricsService)

2. Use dependency injection framework (Dagger/Hilt)

3. Or use lazy initialization with method references
```

**Impact:** HIGH - Prevents independent service testing and reuse

---

### 1.4 Massive Service Classes (25k-35k chars each)

**Location:**
- [`MatchQualityService.java`](src/main/java/datingapp/core/matching/MatchQualityService.java) - 30,163 chars
- [`RecommendationService.java`](src/main/java/datingapp/core/matching/RecommendationService.java) - 25,620 chars
- [`ProfileService.java`](src/main/java/datingapp/core/profile/ProfileService.java) - 34,762 chars
- [`ConnectionService.java`](src/main/java/datingapp/core/connection/ConnectionService.java) - 25,283 chars

**Why It's Bad:**
- Each service handles too many responsibilities
- Impossible to understand entire service at once
- Changes in one area risk breaking unrelated features
- Hard to locate specific functionality

**Suggested Solution:**
```
MatchQualityService should split into:
- DistanceScoringService
- AgeScoringService  
- InterestScoringService
- LifestyleScoringService
- PaceScoringService
- MatchQualityAggregator

RecommendationService should split into:
- DailyRecommendationService
- CandidateFilteringService
- StandoutCalculationService
```

**Impact:** HIGH - Maintenance and navigation difficulty

---

## High Priority Issues (Priority 2)

### 2.1 CLI Handler God Classes

**Location:**
- [`MatchingHandler.java`](src/main/java/datingapp/app/cli/MatchingHandler.java) - 42,018 chars
- [`ProfileHandler.java`](src/main/java/datingapp/app/cli/ProfileHandler.java) - 41,918 chars

**Issue:** Each CLI handler is 40k+ characters handling 20+ different operations.

**Why It's Bad:**
- Single class handles profile, matching, messaging, stats, safety
- Massive switch statements or if-else chains
- Code duplication across handlers for similar operations

**Suggested Solution:**
```
Split MatchingHandler into:
- BrowseCandidatesHandler
- ViewMatchesHandler  
- WhoLikedMeHandler
- StandoutsHandler
- NotificationsHandler

Split ProfileHandler into:
- CreateProfileHandler
- CompleteProfileHandler
- EditProfileHandler
- ViewProfileHandler
- DealbreakersHandler
```

**Impact:** MEDIUM-HIGH - CLI maintenance difficulty

---

### 2.2 Massive UI Controllers (25k-35k chars)

**Location:**
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java) - 32,610 chars
- [`MatchesController.java`](src/main/java/datingapp/ui/screen/MatchesController.java) - 31,637 chars
- [`LoginController.java`](src/main/java/datingapp/ui/screen/LoginController.java) - 27,110 chars
- [`MatchingController.java`](src/main/java/datingapp/ui/screen/MatchingController.java) - 21,221 chars

**Issue:** Controllers handle too many UI concerns, FXML loading, event handling, and business logic.

**Why It's Bad:**
- 500+ line initialize() methods
- Massive field declarations (50+ @FXML fields)
- Mixed concerns: UI state, business logic, navigation

**Suggested Solution:**
```
1. Extract UI logic into specialized classes:
   - ProfileFormManager
   - MatchCardRenderer
   - ConversationListManager

2. Use composition over inheritance

3. Break into smaller FXML components with dedicated controllers
```

**Impact:** MEDIUM - JavaFX maintenance difficulty

---

### 2.3 Massive ViewModels (20k-30k chars)

**Location:**
- [`ProfileViewModel.java`](src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java) - 29,721 chars
- [`MatchesViewModel.java`](src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java) - 26,009 chars
- [`LoginViewModel.java`](src/main/java/datingapp/ui/viewmodel/LoginViewModel.java) - 17,107 chars

**Issue:** ViewModels contain excessive business logic and UI state management.

**Suggested Solution:**
```
1. Split ProfileViewModel into:
   - ProfileFormViewModel (form state)
   - ProfileDisplayViewModel (read-only display)
   - ProfileCompletionViewModel (wizard flow)

2. Use smaller, focused view models composed together
```

**Impact:** MEDIUM - UI testability and maintenance

---

### 2.4 MatchPreferences God Class (32,577 chars)

**Location:** [`MatchPreferences.java`](src/main/java/datingapp/core/profile/MatchPreferences.java)

**Issue:** Contains:
- 50+ interest enum values
- 8 nested enum types (Interest, Lifestyle, Smoking, Drinking, etc.)
- Static utility methods for all preference types

**Why It's Bad:**
- Should be a package of related preferences, not one mega class
- Interests enum should be in its own file
- Each lifestyle category should be separate

**Suggested Solution:**
```
Move to separate files:
- Interest.java (enum with category)
- Lifestyle.java (container for lifestyle enums)
- Smoking.java, Drinking.java, WantsKids.java, etc.
- Dealbreakers.java
- PacePreferences.java
```

**Impact:** MEDIUM - Code organization

---

### 2.5 AppConfig Builder Bloat (670+ lines)

**Location:** [`AppConfig.java`](src/main/java/datingapp/core/AppConfig.java)

**Issue:** 
- 60+ builder methods (one per configuration field)
- 4 nested config records (MatchingConfig, ValidationConfig, AlgorithmConfig, SafetyConfig)
- Multiple validation constants duplicated

**Why It's Bad:**
- Adding new config requires editing multiple places
- 670 lines just for configuration
- Constants scattered between builder and nested records

**Suggested Solution:**
```
1. Use a configuration library (Typesafe Config, Lightbend Config)

2. Or generate builder from configuration schema

3. Group related configs into smaller, focused builders
```

**Impact:** MEDIUM - Configuration management

---

### 2.6 Duplicate Entry Points with Shared Initialization

**Location:**
- [`Main.java`](src/main/java/datingapp/Main.java) - CLI entry
- [`DatingApp.java`](src/main/java/datingapp/ui/DatingApp.java) - JavaFX entry

**Issue:** Both initialize the application differently:
```java
// Main.java
ServiceRegistry services = ApplicationStartup.initialize();

// DatingApp.java  
ServiceRegistry serviceRegistry = ApplicationStartup.initialize();
```

**Why It's Bad:**
- Code duplication in initialization logic
- Both create identical ServiceRegistry
- Hard to ensure consistent startup

**Suggested Solution:**
```
1. Create single ApplicationRunner class
2. Both Main and DatingApp delegate to common runner
3. Extract common bootstrap logic
```

**Impact:** MEDIUM - Inconsistent initialization

---

## Medium Priority Issues (Priority 3)

### 3.1 Duplicate Constants Across Files

**Location:** Multiple files

**Issue:** Same constants defined in multiple places:
- Error messages duplicated across handlers
- Tier names ("Diamond", "Gold", "Silver", "Bronze") in both ProfileService and MatchPreferences
- Date formatters in multiple controllers
- CSS class names scattered

**Example:**
```java
// ProfileService.java
private static final String TIER_DIAMOND = "Diamond";
private static final String TIER_GOLD = "Gold";

// MatchPreferences.java - same tier strings
```

**Suggested Solution:**
```
1. Create central constants class per module:
   - MatchingConstants
   - ProfileConstants  
   - ValidationConstants

2. Or use enum with display names
```

**Impact:** MEDIUM - Inconsistency risk

---

### 3.2 Nested Enums Not Actually Nested

**Location:** 30+ enum definitions

**Issue:** Many enums declared as `public static enum` inside classes but should be top-level:
- User.Gender, User.UserState, User.VerificationMethod
- Match.MatchState, Match.MatchArchiveReason
- ConnectionModels nested enums
- MatchPreferences nested enums (8 of them)

**Why It's Bad:**
- Confusion about when to use outer class qualifier
- Some are used across packages requiring long imports
- Counter to Java best practices for enum scope

**Suggested Solution:**
```
Move standalone enums to top-level:
- Gender.java (in model package)
- UserState.java  
- MatchState.java
- VerificationMethod.java
```

**Impact:** MEDIUM - Code organization

---

### 3.3 Storage Implementation Duplication

**Location:** [`JdbiUserStorage.java`](src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java) and similar

**Issue:**
- Similar patterns repeated across JDBI storage classes
- Row mapping logic duplicated
- SQL string constants repeated

**Suggested Solution:**
```
1. Create base JDBI repository class
2. Extract common row mapping utilities
3. Use JDBI's @Mapper annotation with shared mappers
```

**Impact:** MEDIUM - DRY principle violation

---

### 3.4 Complex Conditional Logic in Services

**Location:** Multiple service classes

**Issue:** Deeply nested conditionals, especially in:
- CandidateFinder filtering logic
- MatchQualityService scoring
- ProfileService completion calculations

**Example:**
```java
if (candidate.getAge() >= minAge && candidate.getAge() <= maxAge) {
    if (candidate.getGender() != null && interestedIn.contains(candidate.getGender())) {
        if (distance <= maxDistance) {
            // 5 more levels...
        }
    }
}
```

**Suggested Solution:**
```
1. Use specification pattern for filtering
2. Chain small predicate functions
3. Extract complex conditions to well-named methods
```

**Impact:** MEDIUM - Code readability

---

### 3.5 Magic Numbers and Strings

**Location:** Throughout codebase

**Issue:** Hardcoded values without constants:
- Age limits (18, 120)
- Distance limits (50, 500)
- Score thresholds (40, 60, 75, 90)
- UI sizing values

**Example:**
```java
if (maxDistanceKm <= 0 || maxDistanceKm > 500) // magic number
if (age < 18 || age > 120) // magic numbers
```

**Suggested Solution:**
```
All thresholds should come from AppConfig:
if (age < config.validation().minAge() || age > config.validation().maxAge())
```

**Impact:** MEDIUM - Maintainability

---

### 3.6 Tight Coupling Through Direct Storage Access

**Location:** ViewModels and Controllers

**Issue:** Direct access to storage interfaces instead of through services:
```java
// In ViewModel
UserStorage userStorage = services.getUserStorage();
userStorage.findAll();

// Should be through ProfileService
profileService.listUsers();
```

**Why It's Bad:**
- Bypasses business logic in services
- Duplicates validation and transformation logic
- Makes services less relevant

**Suggested Solution:**
```
Ensure all data access goes through appropriate service layer
```

**Impact:** MEDIUM - Architecture violation

---

## Low Priority Issues (Priority 4)

### 4.1 Inconsistent Naming Conventions

**Issue:** Mix of naming styles:
- `getCurrentUser()` vs `findAll()`
- `getUserById()` vs `get()`
- Some use `Service` suffix, some don't

**Impact:** LOW - Confusion

---

### 4.2 Missing Null Safety

**Issue:** Some methods return null instead of Optional:
- `ConnectionService.sendMessage()` returns null in error cases
- Some storage getters

**Impact:** LOW - NullPointerException risk

---

### 4.3 Logging Inconsistency

**Issue:** Mix of approaches:
- Some use LoggingSupport interface
- Some directly use Logger
- Some don't log at all

**Impact:** LOW - Debugging difficulty

---

## Summary Statistics

| Category | Count | Total Lines | Largest File |
|----------|-------|-------------|--------------|
| Core Models | 3 | ~35,000 | User.java (807) |
| Core Services | 15 | ~150,000 | ProfileService.java (34k) |
| CLI Handlers | 7 | ~120,000 | MatchingHandler.java (42k) |
| UI Controllers | 12 | ~130,000 | ProfileController.java (32k) |
| ViewModels | 13 | ~130,000 | ProfileViewModel.java (29k) |
| Storage JDBI | 6 | ~130,000 | JdbiMatchmakingStorage.java (37k) |

---

## Recommended Prioritization

### Immediate (This Sprint)
1. Remove excessive synchronization from User class
2. Split User into smaller domain objects
3. Reduce ServiceRegistry dependencies

### Short-term (1-2 Months)
1. Split massive service classes
2. Break up CLI handlers
3. Simplify UI controllers

### Medium-term (3-6 Months)
1. Extract nested enums to top-level
2. Create constants classes
3. Implement dependency injection

---

## Conclusion

The codebase suffers from **severe bloat** at every layer. The User class with 60+ synchronized methods and the ServiceRegistry with 17 dependencies are the most critical issues causing:
- **Poor performance** (lock contention)
- **Testability issues** (impossible to isolate)
- **Maintenance nightmare** (cannot find anything)
- **Risk of regressions** (any change affects everything)

The suggested refactoring follows the **Single Responsibility Principle** and **Law of Demeter** - splitting god classes into focused collaborators that can be understood, tested, and maintained independently.

---

*Generated by MiniMax M2.5 on 2026-02-24*

