# Codebase Deep-Analysis Report

**Generated:** 2026-02-23
**Scope:** Source code only (documentation excluded)
**Total Files Analyzed:** 89 main Java files + 71 test files
**Lines of Code:** 51,081 total / 38,830 code

---

## Executive Summary

This analysis identifies high-impact architectural flaws, design anti-patterns, and structural issues that impede maintainability. The codebase demonstrates a well-intentioned three-layer clean architecture but suffers from several systemic issues that increase cognitive load and reduce development velocity.

**Critical Findings Count:**
- 🔴 **High Severity:** 6 issues
- 🟠 **Medium Severity:** 8 issues
- 🟡 **Low Severity:** 4 issues

---

## 🔴 HIGH SEVERITY ISSUES

### 1. ServiceRegistry as God Object Anti-Pattern

**Location:** [`ServiceRegistry.java`](src/main/java/datingapp/core/ServiceRegistry.java:45-78)

**Issue:** The `ServiceRegistry` class has a 16-parameter constructor and holds references to every service and storage in the application. This creates a central "God Object" that violates the Single Responsibility Principle and creates tight coupling across the entire codebase.

```java
@SuppressWarnings("java:S107")
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
        ValidationService validationService) {
    // 16 parameters - each addition ripples through the entire codebase
}
```

**Impact:**
- Any new service requires modifying this central class
- Tests must mock or construct all 16 dependencies
- Violates Interface Segregation - clients depend on all services
- Creates hidden dependencies - classes request the registry but only need 1-2 services

**Recommendation:** Replace with focused dependency injection:
1. Use constructor injection for specific dependencies each class needs
2. Consider a DI framework (Guice, Dagger) or manual composition root
3. Group related services into cohesive "feature modules" with their own registries

---

### 2. AppConfig.Builder with 50+ Individual Setters

**Location:** [`AppConfig.java`](src/main/java/datingapp/core/AppConfig.java:245-591)

**Issue:** The `AppConfig.Builder` class contains 50+ individual setter methods for configuration properties, all stored as flat fields. This creates a maintenance nightmare where adding a single property requires changes in multiple places.

```java
public static class Builder {
    // 50+ individual fields
    private int dailyLikeLimit = 100;
    private int dailySuperLikeLimit = 1;
    private int dailyPassLimit = -1;
    // ... 47 more fields ...

    // 50+ individual setters
    public Builder dailyLikeLimit(int v) { this.dailyLikeLimit = v; return this; }
    public Builder dailySuperLikeLimit(int v) { this.dailySuperLikeLimit = v; return this; }
    // ... 47 more setters ...

    // build() manually assembles 4 sub-records
    public AppConfig build() {
        return new AppConfig(
                buildMatchingConfig(), buildValidationConfig(),
                buildAlgorithmConfig(), buildSafetyConfig());
    }
}
```

**Impact:**
- Adding a property requires: field declaration + setter + correct placement in build method
- High risk of copy-paste errors
- Difficult to understand which properties belong to which sub-record
- The 4 sub-records (MatchingConfig, ValidationConfig, AlgorithmConfig, SafetyConfig) are good, but the Builder doesn't leverage them

**Recommendation:**
1. Create nested builders for each sub-record (e.g., `Builder.matching().dailyLikeLimit(100)`)
2. Or use the sub-records directly and compose them in the main builder
3. Consider auto-generating the builder from a schema

---

### 3. MatchingHandler with 14 Dependencies

**Location:** [`MatchingHandler.java`](src/main/java/datingapp/app/cli/MatchingHandler.java:90-143)

**Issue:** The `MatchingHandler.Dependencies` record contains 14 dependencies, making it one of the most coupled classes in the codebase. This indicates a violation of Single Responsibility Principle.

```java
public static record Dependencies(
        CandidateFinder candidateFinderService,
        MatchingService matchingService,
        InteractionStorage interactionStorage,
        RecommendationService dailyService,
        UndoService undoService,
        MatchQualityService matchQualityService,
        UserStorage userStorage,
        ProfileService achievementService,
        AnalyticsStorage analyticsStorage,
        TrustSafetyService trustSafetyService,
        ConnectionService transitionService,
        RecommendationService standoutsService,  // Note: same type as dailyService
        CommunicationStorage communicationStorage,
        AppSession userSession,
        InputReader inputReader) {
    // 14 dependencies for a single handler
}
```

**Impact:**
- Testing requires mocking 14 dependencies
- Handler likely does too many things (swiping, matches, standouts, likers, notifications, friend requests)
- Changes to any of these services may affect this handler
- Confusing naming: `dailyService` and `standoutsService` are both `RecommendationService`

**Recommendation:**
1. Split into focused handlers: `SwipeHandler`, `MatchesHandler`, `StandoutsHandler`, `LikerBrowserHandler`
2. Each handler should have 3-5 dependencies maximum
3. Use facade services to group related operations

---

### 4. User Entity with 30+ Fields and Synchronized Methods

**Location:** [`User.java`](src/main/java/datingapp/core/model/User.java:24-807)

**Issue:** The `User` entity contains 30+ fields with synchronized getters/setters, making it a "God Entity" that represents too many concerns simultaneously.

```java
public class User {
    // Core identity
    private final UUID id;
    private String name;

    // Profile fields
    private String bio;
    private LocalDate birthDate;
    private Gender gender;
    private Set<Gender> interestedIn;

    // Location
    private double lat;
    private double lon;
    private boolean hasLocationSet;
    private int maxDistanceKm;

    // Preferences
    private int minAge;
    private int maxAge;

    // Media
    private List<String> photoUrls;

    // State
    private UserState state;
    private final Instant createdAt;
    private Instant updatedAt;

    // Lifestyle (Phase 0.5b)
    private Lifestyle.Smoking smoking;
    private Lifestyle.Drinking drinking;
    private Lifestyle.WantsKids wantsKids;
    private Lifestyle.LookingFor lookingFor;
    private Lifestyle.Education education;
    private Integer heightCm;

    // Dealbreakers
    private MatchPreferences.Dealbreakers dealbreakers;

    // Interests
    private Set<Interest> interests;

    // Verification
    private String email;
    private String phone;
    private boolean isVerified;
    private VerificationMethod verificationMethod;
    private String verificationCode;
    private Instant verificationSentAt;
    private Instant verifiedAt;

    // Pace
    private PacePreferences pacePreferences;

    // Soft-delete
    private Instant deletedAt;

    // Every getter/setter is synchronized
    public synchronized String getBio() { return bio; }
    public synchronized void setBio(String bio) { this.bio = bio; touch(); }
    // ... 60+ more synchronized methods
}
```

**Impact:**
- 807 lines for a single entity
- Synchronized methods create lock contention under load
- Mixes concerns: identity, profile, preferences, verification, soft-delete
- StorageBuilder pattern adds another 150 lines
- Difficult to understand which fields are required vs optional

**Recommendation:**
1. Extract value objects: `Profile`, `Preferences`, `VerificationStatus`, `Location`
2. Use `ReadWriteLock` instead of `synchronized` for better concurrency
3. Consider making the entity immutable with a builder pattern for updates
4. Separate soft-delete into a wrapper/decorator

---

### 5. JdbiUserStorage with 250-Line UserSqlBindings Helper

**Location:** [`JdbiUserStorage.java`](src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java:476-723)

**Issue:** The `UserSqlBindings` inner class is a 250-line helper that manually maps every User field to SQL parameters. This is essentially a manual ORM that duplicates the entity structure.

```java
public static final class UserSqlBindings {
    private final User user;
    private final Dealbreakers dealbreakers;
    private final PacePreferences pace;

    public UUID getId() { return user.getId(); }
    public String getName() { return user.getName(); }
    public String getBio() { return user.getBio(); }
    public LocalDate getBirthDate() { return user.getBirthDate(); }
    public String getGender() { return user.getGender() != null ? user.getGender().name() : null; }
    public String getInterestedInCsv() { return serializeEnumSet(user.getInterestedIn()); }
    // ... 40+ more getter methods that just delegate to user

    // Plus serialization logic
    private String serializeEnumSet(Set<? extends Enum<?>> values) { ... }
}
```

**Impact:**
- Adding a field to `User` requires changes in: User.java, UserSqlBindings, Mapper, SQL schema
- High risk of field mapping drift
- 724 lines for a single storage implementation
- JSON serialization inline for complex types

**Recommendation:**
1. Use JDBI's built-in argument factories for custom types
2. Consider a code generator to create bindings from the entity
3. Use a proper ORM (Hibernate, JOOQ) if this pattern is widespread
4. At minimum, use reflection-based binding for simple cases

---

### 6. Multiple Singleton Patterns with Inconsistent Initialization

**Location:**
- [`AppSession.java`](src/main/java/datingapp/core/AppSession.java) - Singleton
- [`DatabaseManager.java`](src/main/java/datingapp/storage/DatabaseManager.java) - Singleton
- [`NavigationService.java`](src/main/java/datingapp/ui/NavigationService.java) - Singleton
- [`ApplicationStartup.java`](src/main/java/datingapp/app/bootstrap/ApplicationStartup.java:72-74) - Static state

**Issue:** Multiple classes use the singleton pattern with inconsistent initialization strategies, creating hidden dependencies and making testing difficult.

```java
// AppSession - classic singleton
public class AppSession {
    private static final AppSession INSTANCE = new AppSession();
    public static AppSession getInstance() { return INSTANCE; }
}

// DatabaseManager - lazy singleton
public class DatabaseManager {
    private static volatile DatabaseManager instance;
    public static DatabaseManager getInstance() { ... }
}

// ApplicationStartup - static mutable state
private static volatile ServiceRegistry services;
private static volatile DatabaseManager dbManager;
private static volatile boolean initialized = false;
```

**Impact:**
- Tests must manually reset singleton state
- Hidden dependencies - classes call `getInstance()` without explicit injection
- Thread-safety concerns with lazy initialization
- Difficult to run parallel tests

**Recommendation:**
1. Convert all singletons to dependency-injected services
2. Use `ApplicationStartup` as the composition root that creates and injects dependencies
3. For tests, use test-specific instances instead of resetting singletons

---

## 🟠 MEDIUM SEVERITY ISSUES

### 7. Duplicated Logging Patterns Across ViewModels

**Location:**
- [`ProfileViewModel.java`](src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:471-487)
- [`MatchesViewModel.java`](src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java:554-558)
- Similar patterns in other ViewModels

**Issue:** Every ViewModel implements its own inline logging methods with identical patterns.

```java
// ProfileViewModel
private void logInfo(String message, Object... args) {
    if (logger.isInfoEnabled()) {
        logger.info(message, args);
    }
}
private void logWarn(String message, Object... args) {
    if (logger.isWarnEnabled()) {
        logger.warn(message, args);
    }
}
private void logError(String message, Object... args) {
    if (logger.isErrorEnabled()) {
        logger.error(message, args);
    }
}

// MatchesViewModel - same pattern repeated
private void logWarn(String message, Object... args) {
    if (logger.isWarnEnabled()) {
        logger.warn(message, args);
    }
}
```

**Impact:**
- Code duplication across 10+ ViewModels
- Inconsistent logging patterns (some have logInfo, some don't)
- Modern SLF4J already handles level checks efficiently

**Recommendation:**
1. Remove inline logging methods - call `logger.info()` directly
2. Or create a `LoggingSupport` interface with default methods
3. SLF4J 2.0+ fluent API: `logger.atInfo().log(message, args)`

---

### 8. Duplicated Async Handling Pattern in ViewModels

**Location:** [`MatchesViewModel.java`](src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java:206-219)

**Issue:** The `isFxToolkitAvailable()` check and async execution pattern is duplicated across multiple ViewModels.

```java
// MatchesViewModel
private boolean isFxToolkitAvailable() {
    try {
        if (javafx.application.Platform.isFxApplicationThread()) {
            return true;
        }
        javafx.application.Platform.runLater(() -> { });
        return true;
    } catch (IllegalStateException _) {
        return false;
    }
}

// Then used in every method:
if (isFxToolkitAvailable() && javafx.application.Platform.isFxApplicationThread()) {
    Thread.ofVirtual().name("matches-refresh").start(() -> {
        // ... actual logic
    });
} else {
    // synchronous fallback
}
```

**Impact:**
- Duplicated across multiple ViewModels
- Complex conditional logic in every async method
- Easy to get wrong when copy-pasting

**Recommendation:**
1. Create a `UiExecutor` utility class that handles async execution
2. Use a single method: `uiExecutor.executeAsync(runnable)`
3. Consider a reactive approach with `Task` or `CompletableFuture`

---

### 9. Inline Constants in Services

**Location:**
- [`MatchQualityService.java`](src/main/java/datingapp/core/matching/MatchQualityService.java:36-71)
- [`RecommendationService.java`](src/main/java/datingapp/core/matching/RecommendationService.java:33-51)

**Issue:** Services contain 35+ inline constants for scoring thresholds that should be in configuration.

```java
// MatchQualityService - 35+ inline constants
private static final int STAR_EXCELLENT_THRESHOLD = 90;
private static final int STAR_GREAT_THRESHOLD = 75;
private static final int STAR_GOOD_THRESHOLD = 60;
private static final int STAR_FAIR_THRESHOLD = 40;
private static final String LABEL_EXCELLENT = "Excellent Match";
private static final String LABEL_GREAT = "Great Match";
private static final String LABEL_GOOD = "Good Match";
private static final String LABEL_FAIR = "Fair Match";
private static final String LABEL_LOW = "Low Compatibility";
private static final int SUMMARY_MAX_LENGTH = 40;
private static final int SUMMARY_TRUNCATE_LENGTH = 37;
private static final int HIGHLIGHT_MAX_COUNT = 5;
// ... 20+ more constants

// RecommendationService - similar pattern
private static final long ACTIVITY_VERY_RECENT_HOURS = 1;
private static final long ACTIVITY_RECENT_HOURS = 24;
private static final long ACTIVITY_MODERATE_HOURS = 72;
private static final double ACTIVITY_SCORE_VERY_RECENT = 1.0;
private static final double ACTIVITY_SCORE_RECENT = 0.9;
// ... 10+ more constants
```

**Impact:**
- Cannot tune algorithm without recompiling
- Constants that should be related (thresholds and labels) are separate
- Difficult to understand the complete scoring model

**Recommendation:**
1. Move scoring thresholds to `AppConfig.AlgorithmConfig`
2. Create a `ScoringConfig` record with all scoring-related constants
3. Consider externalizing to a configuration file for A/B testing

---

### 10. Deprecated Methods in User for Backward Compatibility

**Location:** [`User.java`](src/main/java/datingapp/core/model/User.java:432-600)

**Issue:** The `User` class contains deprecated methods that exist solely for backward compatibility, adding cognitive overhead.

```java
/**
 * @deprecated Use {@link #getAge(java.time.ZoneId)} to explicitly specify timezone.
 */
@Deprecated
public synchronized int getAge() {
    return getAge(java.time.ZoneId.systemDefault());
}

/**
 * @deprecated Use {@link #setMaxDistanceKm(int, int)} with explicit system limit
 */
@Deprecated
public synchronized void setMaxDistanceKm(int maxDistanceKm) {
    setMaxDistanceKm(maxDistanceKm, 500);
}

/**
 * @deprecated Use {@link #setAgeRange(int, int, int, int)} with explicit system limits
 */
@Deprecated
public synchronized void setAgeRange(int minAge, int maxAge) {
    setAgeRange(minAge, maxAge, 18, 120);
}
```

**Impact:**
- 4 deprecated methods in a 800-line class
- IDE warnings clutter the codebase
- Unclear migration path for callers

**Recommendation:**
1. Set a removal timeline for deprecated methods
2. Add `@Deprecated(forRemoval = true, since = "X.Y")` with version info
3. Or remove if no external callers exist

---

### 11. ViewModels with 25+ JavaFX Properties

**Location:** [`ProfileViewModel.java`](src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java:54-91)

**Issue:** ViewModels expose 25+ JavaFX properties, creating a "Property Explosion" that makes the class difficult to understand.

```java
// ProfileViewModel - 25+ properties
private final StringProperty name = new SimpleStringProperty("");
private final StringProperty bio = new SimpleStringProperty("");
private final StringProperty location = new SimpleStringProperty("");
private final StringProperty interests = new SimpleStringProperty("");
private final StringProperty completionStatus = new SimpleStringProperty("0%");
private final StringProperty completionDetails = new SimpleStringProperty("");
private final StringProperty primaryPhotoUrl = new SimpleStringProperty("");
private final javafx.collections.ObservableList<String> photoUrls = ...;
private final javafx.beans.property.IntegerProperty currentPhotoIndex = ...;
private final ObjectProperty<Gender> gender = new SimpleObjectProperty<>(null);
private final ObservableSet<Gender> interestedInGenders = ...;
private final ObjectProperty<LocalDate> birthDate = new SimpleObjectProperty<>(null);
private final ObjectProperty<Integer> height = new SimpleObjectProperty<>(null);
private final ObjectProperty<Lifestyle.Smoking> smoking = ...;
private final ObjectProperty<Lifestyle.Drinking> drinking = ...;
private final ObjectProperty<Lifestyle.WantsKids> wantsKids = ...;
private final ObjectProperty<Lifestyle.LookingFor> lookingFor = ...;
private final StringProperty minAge = new SimpleStringProperty("18");
private final StringProperty maxAge = new SimpleStringProperty("99");
private final StringProperty maxDistance = new SimpleStringProperty("50");
private final ObjectProperty<Dealbreakers> dealbreakers = ...;
private final StringProperty dealbreakersStatus = ...;
private final ObservableSet<Interest> selectedInterests = ...;
// Plus: disposed, backgroundThreads, errorHandler
```

**Impact:**
- 862 lines for a single ViewModel
- Each property needs a getter method (25+ getter methods)
- Difficult to understand the data model
- Testing requires setting up many properties

**Recommendation:**
1. Group related properties into sub-models (e.g., `ProfileData`, `PreferencesData`)
2. Use a form model pattern with validation
3. Consider using a reactive form library

---

### 12. StorageBuilder Pattern Complexity

**Location:** [`User.java`](src/main/java/datingapp/core/model/User.java:151-304)

**Issue:** The `StorageBuilder` pattern adds 150+ lines to the entity and duplicates all field setters.

```java
public static final class StorageBuilder {
    private final User user;

    private StorageBuilder(UUID id, String name, Instant createdAt) {
        this.user = new User(id, name, createdAt);
    }

    public static StorageBuilder create(UUID id, String name, Instant createdAt) {
        return new StorageBuilder(id, name, createdAt);
    }

    public StorageBuilder bio(String bio) { user.bio = bio; return this; }
    public StorageBuilder birthDate(LocalDate birthDate) { user.birthDate = birthDate; return this; }
    public StorageBuilder gender(Gender gender) { user.gender = gender; return this; }
    public StorageBuilder interestedIn(Set<Gender> interestedIn) { ... }
    // ... 25+ more setter methods

    public User build() { return user; }
}
```

**Impact:**
- Duplicates every field setter
- Bypasses validation (sets fields directly)
- Easy to forget required fields
- Increases entity size by 50%

**Recommendation:**
1. Use a separate `UserDto` record for storage mapping
2. Or use a reflection-based builder
3. Add validation in `build()` to ensure required fields are set

---

### 13. Inconsistent Error Handling Patterns

**Location:** Various service classes

**Issue:** Error handling is inconsistent across the codebase. Some services return result records, others throw exceptions.

```java
// ConnectionService - uses result records
public static record SendResult(boolean success, Message message, String errorMessage, ErrorCode errorCode) {
    public static SendResult success(Message m) { return new SendResult(true, m, null, null); }
    public static SendResult failure(String err, ErrorCode code) { return new SendResult(false, null, err, code); }
}

// MatchingService - uses result records
public static record SwipeResult(boolean success, Like like, boolean matched, Match match, String errorMessage) { ... }

// But User entity - throws exceptions
public synchronized void activate() {
    if (state == UserState.BANNED) {
        throw new IllegalStateException("Cannot activate a banned user");
    }
    if (!isComplete()) {
        throw new IllegalStateException("Cannot activate an incomplete profile");
    }
    this.state = UserState.ACTIVE;
}
```

**Impact:**
- Inconsistent API contracts
- Some callers must catch exceptions, others check result records
- Difficult to understand error handling strategy

**Recommendation:**
1. Standardize on result records for business failures
2. Reserve exceptions for truly exceptional conditions (programming errors, infrastructure failures)
3. Document the error handling strategy in the architecture guide

---

### 14. Large Test Utility Class

**Location:** [`TestStorages.java`](src/test/java/datingapp/core/testutil/TestStorages.java)

**Issue:** The `TestStorages` class is a 45,000+ character file containing multiple mock storage implementations.

**Impact:**
- Single file with 5+ mock implementations
- Difficult to navigate
- Changes to one mock affect all tests

**Recommendation:**
1. Split into separate files per storage type
2. Consider using a mock framework for complex scenarios

---

## 🟡 LOW SEVERITY ISSUES

### 15. Magic Numbers in UI Constants

**Location:** [`UiConstants.java`](src/main/java/datingapp/ui/UiConstants.java)

**Issue:** UI constants contain magic numbers without clear semantic meaning.

**Recommendation:** Add comments explaining the rationale for each value.

---

### 16. Inconsistent Naming: Service vs Service

**Location:** [`MatchingHandler.java`](src/main/java/datingapp/app/cli/MatchingHandler.java:51-65)

**Issue:** Field names inconsistently use "Service" suffix.

```java
private final CandidateFinder candidateFinderService;  // Has Service suffix
private final MatchingService matchingService;          // Has Service suffix
private final InteractionStorage interactionStorage;    // No Service suffix
private final RecommendationService dailyService;       // Named by feature, not type
private final RecommendationService standoutsService;   // Same type, different name
```

**Recommendation:** Standardize naming: either always include type suffix or never include it.

---

### 17. Hidden Dependencies via AppSession

**Location:** Various handlers and ViewModels

**Issue:** Classes access `AppSession.getInstance().getCurrentUser()` instead of receiving the user as a parameter.

**Impact:**
- Hidden dependency on AppSession
- Difficult to test with different users
- Thread-safety concerns

**Recommendation:** Pass the current user as a method parameter or constructor dependency.

---

### 18. Complex Nested Record Types

**Location:** [`ConnectionModels.java`](src/main/java/datingapp/core/connection/ConnectionModels.java)

**Issue:** Multiple nested records with similar names create confusion.

**Recommendation:** Extract to top-level records or use more descriptive names.

---

## Summary of Recommendations

### Immediate Actions (High Impact, Low Effort)

1. **Remove inline logging methods** - Call `logger.info()` directly
2. **Create UiExecutor utility** - Consolidate async execution pattern
3. **Document error handling strategy** - Clarify when to use result records vs exceptions

### Short-Term Actions (High Impact, Medium Effort)

4. **Split MatchingHandler** - Reduce from 14 dependencies to 3-5 per handler
5. **Refactor AppConfig.Builder** - Use nested builders for sub-records
6. **Move scoring constants to configuration** - Enable tuning without recompilation

### Long-Term Actions (High Impact, High Effort)

7. **Replace ServiceRegistry with proper DI** - Eliminate God Object
8. **Decompose User entity** - Extract value objects for profile, preferences, verification
9. **Simplify JDBI mapping** - Use argument factories or code generation

---

## Metrics Summary

| Metric             | Value                           | Concern Level |
|--------------------|---------------------------------|---------------|
| Largest Class      | User.java (807 lines)           | 🔴 High       |
| Most Dependencies  | MatchingHandler (14)            | 🔴 High       |
| Largest Builder    | AppConfig.Builder (50+ setters) | 🔴 High       |
| Singleton Count    | 4                               | 🟠 Medium     |
| Deprecated Methods | 4 in User                       | 🟡 Low        |
| Avg ViewModel Size | 600+ lines                      | 🟠 Medium     |

---

## Conclusion

The codebase demonstrates solid architectural intentions with its three-layer clean architecture. However, several systemic issues have accumulated over time:

1. **God Objects** (ServiceRegistry, User) that violate Single Responsibility
2. **Excessive Coupling** (MatchingHandler's 14 dependencies)
3. **Boilerplate Proliferation** (50+ setters, 250-line mapping classes)
4. **Inconsistent Patterns** (error handling, naming, singletons)

Addressing these issues systematically will significantly improve maintainability and development velocity. The recommendations are prioritized by impact-to-effort ratio, with immediate actions providing quick wins while longer-term refactoring addresses root causes.

---

*This report was generated from source code analysis only, excluding all documentation and AI agent files.*
