# Codebase Maintenance Analysis Report
**Date:** 2026-02-23
**Scope:** Dating App Java/JavaFX Application
**LOC:** ~51,000 total / ~38,800 code

> ⚠️ **Alignment status (2026-03-01): Historical snapshot**
> The LOC and implementation snapshots below are time-bound to report generation.
> Current baseline: **56,482 total Java LOC / 43,327 code LOC**; **116 main + 88 test = 204 Java files**.

---

## Executive Summary

This analysis identifies **12 major maintainability issues** that significantly impact code readability, maintainability, and long-term evolution of the codebase. The findings focus on architectural decisions, code duplication, over-engineering, and structural issues that would yield the highest ROI if addressed.

---

## 1. AppConfig Builder Bloat

### Issue Description
The [`AppConfig.Builder`](src/main/java/datingapp/core/AppConfig.java:245) class contains **50+ individual setter methods** for configuration properties, resulting in a 670+ line nested class. Each property requires:
- A private field declaration
- A setter method
- Manual wiring in `build()`

### Why It's Problematic
- **High maintenance burden**: Adding a new config property requires changes in 4+ locations
- **Cognitive overload**: Developers must scroll through hundreds of lines to understand available options
- **Error-prone**: Easy to forget to wire a new property into `build()`
- **Violates DRY**: Field name repeated in field declaration, setter, and build method

### Suggested Solution
Use a configuration map or properties-based approach:

```java
// Option A: Properties file with auto-binding
public record AppConfig(Map<String, Object> properties) {
    public int dailyLikeLimit() { return (int) properties.getOrDefault("dailyLikeLimit", 100); }
}

// Option B: Generate Builder via annotation processor
@ConfigProperties
public record AppConfig(int dailyLikeLimit, int dailySuperLikeLimit, ...) {}
```

### Impact Assessment
| Metric                | Current     | After Fix  |
|-----------------------|-------------|------------|
| Lines of Code         | ~670        | ~100       |
| Maintenance Cost      | High        | Low        |
| New Property Addition | 4 locations | 1 location |

---

## 2. ServiceRegistry as God Object

### Issue Description
[`ServiceRegistry`](src/main/java/datingapp/core/ServiceRegistry.java:21) acts as a **service locator** with 16+ getter methods for various services and storages. It's injected everywhere and creates implicit dependencies.

### Why It's Problematic
- **Hidden dependencies**: Classes declare `ServiceRegistry` but actually use only 2-3 services
- **Testing difficulty**: Must mock entire registry even when testing a single service
- **Violation of ISP**: Interface Segregation Principle - clients depend on methods they don't use
- **Encourages coupling**: Easy to add "just one more" service reference

### Suggested Solution
Use **explicit dependency injection** via constructors:

```java
// Instead of:
public MatchingHandler(ServiceRegistry services) {
    this.matchingService = services.getMatchingService();
    this.userStorage = services.getUserStorage();
    // ... 10 more
}

// Prefer:
public MatchingHandler(MatchingService matchingService, UserStorage userStorage) {
    this.matchingService = matchingService;
    this.userStorage = userStorage;
}
```

Or use a DI framework (Guice, Dagger) that generates the wiring.

### Impact Assessment
| Metric                 | Current      | After Fix      |
|------------------------|--------------|----------------|
| Constructor Parameters | 1 (registry) | 3-5 (explicit) |
| Test Mock Setup        | Complex      | Simple         |
| Dependency Visibility  | Hidden       | Explicit       |

---

## 3. Monolithic CLI Handler Classes

### Issue Description
CLI handlers are massive classes with mixed responsibilities:
- [`MatchingHandler.java`](src/main/java/datingapp/app/cli/MatchingHandler.java) - **42,018 chars**
- [`ProfileHandler.java`](src/main/java/datingapp/app/cli/ProfileHandler.java) - **41,918 chars**

These classes handle:
- User input parsing
- Business logic orchestration
- Display formatting
- Achievement checking
- Navigation flow

### Why It's Problematic
- **Single Responsibility Principle violation**: One class doing 5+ different things
- **Difficult to test**: Must set up entire environment to test a single flow
- **Code navigation**: Finding specific logic requires scrolling through thousands of lines
- **Merge conflicts**: Multiple developers working on same large file

### Suggested Solution
Extract focused components:

```java
// Current: One massive class
public class MatchingHandler { /* 1000+ lines */ }

// Proposed: Focused components
public class CandidateBrowser { /* handles browsing flow */ }
public class MatchDisplayer { /* handles formatting */ }
public class SwipeProcessor { /* handles swipe logic */ }
public class MatchingHandler { /* orchestrates only */ }
```

### Impact Assessment
| Metric              | Current     | After Fix  |
|---------------------|-------------|------------|
| Avg Class Size      | ~1000 lines | ~200 lines |
| Test Complexity     | High        | Low        |
| Merge Conflict Risk | High        | Low        |

---

## 4. TestStorages Monolith

### Issue Description
[`TestStorages.java`](src/test/java/datingapp/core/testutil/TestStorages.java) is a **45,660 character** file containing 5+ in-memory storage implementations as nested classes.

### Why It's Problematic
- **Navigation nightmare**: Finding a specific storage implementation requires searching
- **Compilation coupling**: Any change forces recompilation of all test utilities
- **IDE performance**: Large files slow down code analysis
- **Unclear ownership**: Who "owns" each storage implementation?

### Suggested Solution
Split into dedicated files:

```
src/test/java/datingapp/core/testutil/
├── TestUserStorage.java
├── TestInteractionStorage.java
├── TestCommunicationStorage.java
├── TestAnalyticsStorage.java
└── TestTrustSafetyStorage.java
```

### Impact Assessment
| Metric             | Current   | After Fix      |
|--------------------|-----------|----------------|
| File Size          | 45K chars | ~8K chars each |
| Compilation Units  | 1         | 5              |
| IDE Responsiveness | Slower    | Faster         |

---

## 5. EnumSetUtil Redundant Methods

### Issue Description
[`EnumSetUtil`](src/main/java/datingapp/core/EnumSetUtil.java) contains two nearly identical `safeCopy` methods:

```java
public static <E> EnumSet<E> safeCopy(Collection<E> source, Class<E> enumClass) { ... }
public static <E> EnumSet<E> safeCopy(Set<E> source, Class<E> enumClass) { ... }
```

And a third method `defensiveCopy` that does the same thing:

```java
public static <E> EnumSet<E> defensiveCopy(EnumSet<E> source, Class<E> enumClass) { ... }
```

### Why It's Problematic
- **Confusion**: Which method should callers use?
- **Maintenance burden**: Bug fixes must be applied to multiple locations
- **Code bloat**: 64 lines where 15 would suffice

### Suggested Solution
Consolidate to a single method:

```java
public static <E extends Enum<E>> EnumSet<E> safeCopy(Collection<E> source, Class<E> enumClass) {
    Objects.requireNonNull(enumClass, "enumClass cannot be null");
    if (source == null || source.isEmpty()) {
        return EnumSet.noneOf(enumClass);
    }
    return source instanceof EnumSet ? EnumSet.copyOf((EnumSet<E>) source) : EnumSet.copyOf(source);
}
```

### Impact Assessment
| Metric        | Current   | After Fix |
|---------------|-----------|-----------|
| Methods       | 3         | 1         |
| Lines of Code | 64        | ~15       |
| API Surface   | Confusing | Clear     |

---

## 6. LoggingSupport Interface Anti-Pattern

### Issue Description
[`LoggingSupport`](src/main/java/datingapp/core/LoggingSupport.java) is an interface that:
1. Requires implementers to provide `logger()` method
2. Provides default logging methods that delegate to `logger()`
3. Also provides **static** logging methods that take a Logger parameter

### Why It's Problematic
- **Interface bloat**: Mixing instance and static concerns
- **Redundant API**: Two ways to do the same thing
- **Forced inheritance**: Classes must implement the interface to get logging
- **Not a true interface**: It's a trait/mixin pretending to be an interface

### Suggested Solution
Option A: Use a simple utility class:

```java
public final class Log {
    public static void info(Logger log, String msg, Object... args) {
        if (log.isInfoEnabled()) log.info(msg, args);
    }
    // ... other levels
}
```

Option B: Use Lombok's `@Slf4j` annotation:

```java
@Slf4j
public class MyClass {
    public void doSomething() {
        log.info("Doing something"); // Auto-generated logger field
    }
}
```

### Impact Assessment
| Metric                | Current                                  | After Fix     |
|-----------------------|------------------------------------------|---------------|
| Interface Methods     | 11                                       | 0 (eliminate) |
| Boilerplate per Class | `implements LoggingSupport` + `logger()` | None          |
| Flexibility           | Low                                      | High          |

---

## 7. UI Controller Complexity

### Issue Description
Several UI controllers are extremely large:
- [`MatchesController.java`](src/main/java/datingapp/ui/screen/MatchesController.java) - **31,637 chars**
- [`LoginController.java`](src/main/java/datingapp/ui/screen/LoginController.java) - **27,110 chars**
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java) - **32,610 chars**

These contain:
- Inline CSS styles as string constants
- Animation logic mixed with business logic
- Complex cell factory implementations
- Extensive FXML binding code

### Why It's Problematic
- **View logic in controllers**: CSS and animations should be in separate files
- **Testing difficulty**: Must instantiate JavaFX components to test logic
- **Code reuse**: Animation patterns duplicated across controllers
- **Readability**: Business logic buried in UI setup code

### Suggested Solution
1. **Extract CSS to stylesheets**:
```java
// Current: Inline styles
private static final String STYLE_TITLE = "-fx-font-size: 24px; -fx-font-weight: bold; ...";

// Proposed: External CSS
// styles.css
.title { -fx-font-size: 24px; -fx-font-weight: bold; }
```

2. **Extract animations to UiAnimations** (already partially done)

3. **Use cell factory utilities**:
```java
// Current: Inline cell factory in controller
userListView.setCellFactory(lv -> new ListCell<>() { /* 50 lines */ });

// Proposed: Dedicated factory
userListView.setCellFactory(UserListCell::new);
```

### Impact Assessment
| Metric              | Current      | After Fix  |
|---------------------|--------------|------------|
| Avg Controller Size | ~30K chars   | ~15K chars |
| CSS Location        | Java strings | CSS files  |
| Test Coverage       | Low          | High       |

---

## 8. RecommendationService Multiple Responsibilities

### Issue Description
[`RecommendationService`](src/main/java/datingapp/core/matching/RecommendationService.java) handles:
- Daily like/pass limits
- Daily pick selection
- Standout generation
- Activity scoring
- LRU cache management

### Why It's Problematic
- **Single Responsibility Principle violation**: 5+ distinct responsibilities
- **Testing complexity**: Must understand all features to test any one
- **Change impact**: Modifying limits affects standouts code
- **Builder bloat**: 10+ required dependencies

### Suggested Solution
Extract focused services:

```java
// Proposed structure
DailyLimitService     // canLike, canPass, getStatus
DailyPickService      // getDailyPick, markViewed
StandoutService       // getStandouts, scoreCandidate
ActivityScoreService  // calculateActivityScore
```

### Impact Assessment
| Metric          | Current   | After Fix       |
|-----------------|-----------|-----------------|
| Class Size      | 623 lines | ~150 lines each |
| Dependencies    | 10        | 3-4 each        |
| Test Complexity | High      | Low             |

---

## 9. User Model Synchronization Overhead

### Issue Description
The [`User`](src/main/java/datingapp/core/model/User.java) class has **every getter and setter synchronized**:

```java
public synchronized String getName() { return name; }
public synchronized void setName(String name) { ... }
public synchronized Gender getGender() { return gender; }
// ... 40+ synchronized methods
```

### Why It's Problematic
- **Performance overhead**: Synchronization on every field access
- **False sense of thread safety**: Individual field access is thread-safe, but compound operations are not
- **Lock contention**: All methods synchronize on `this`, creating a bottleneck
- **Over-engineering**: Most access is single-threaded (JavaFX Application Thread)

### Suggested Solution
1. **Use `volatile` for simple fields** if visibility is the only concern
2. **Use `ReadWriteLock`** for read-heavy access patterns
3. **Accept single-threaded assumption** for UI-bound entities:

```java
// If only accessed from JavaFX thread:
public String getName() { return name; }  // No synchronization

// For truly concurrent access:
private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
public String getName() {
    lock.readLock().lock();
    try { return name; }
    finally { lock.readLock().unlock(); }
}
```

### Impact Assessment
| Metric               | Current   | After Fix |
|----------------------|-----------|-----------|
| Synchronized Methods | 40+       | 0-5       |
| Lock Contention      | High      | None      |
| Code Clarity         | Cluttered | Clean     |

---

## 10. Inlined Constants from Deleted ScoringConstants

### Issue Description
Multiple services have inlined "magic numbers" that were previously in `ScoringConstants`:

```java
// MatchQualityService.java
private static final int STAR_EXCELLENT_THRESHOLD = 90;
private static final int STAR_GREAT_THRESHOLD = 75;
private static final double PACE_SYNC_PERFECT = 0.95;
// ... 30+ constants

// ProfileService.java
private static final int BIO_TIP_MIN_LENGTH = 50;
private static final int TIER_DIAMOND_THRESHOLD = 95;
// ... 20+ constants
```

### Why It's Problematic
- **Duplication**: Same thresholds appear in multiple files
- **Inconsistency risk**: Changing a threshold in one place but not another
- **No single source of truth**: What's the "official" star threshold?
- **Hard to tune**: Must search codebase to find all related values

### Suggested Solution
Create a focused constants class or use AppConfig:

```java
// Option A: Dedicated constants
public final class ScoringThresholds {
    public static final int STAR_EXCELLENT = 90;
    public static final int STAR_GREAT = 75;
    // ...
}

// Option B: Add to AppConfig (better for runtime tuning)
public record AlgorithmConfig(
    int starExcellentThreshold,
    int starGreatThreshold,
    // ...
) {}
```

### Impact Assessment
| Metric             | Current    | After Fix   |
|--------------------|------------|-------------|
| Constants Location | Scattered  | Centralized |
| Runtime Tuning     | Impossible | Possible    |
| Consistency        | Uncertain  | Guaranteed  |

---

## 11. ViewModelFactory Manual Controller Mapping

### Issue Description
[`ViewModelFactory`](src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java) maintains a manual map of controller factories:

```java
private Map<Class<?>, Supplier<Object>> buildControllerFactories() {
    Map<Class<?>, Supplier<Object>> map = new HashMap<>();
    map.put(LoginController.class, () -> new LoginController(getLoginViewModel(), ...));
    map.put(DashboardController.class, () -> new DashboardController(getDashboardViewModel()));
    // ... 10 more
    return map;
}
```

### Why It's Problematic
- **Boilerplate**: Every controller must be manually registered
- **Error-prone**: Easy to forget to register a new controller
- **Tight coupling**: Factory knows about all controller types
- **Scaling issue**: Adding controllers requires modifying this class

### Suggested Solution
Use reflection or a DI framework:

```java
// Option A: Reflection-based
public Object createController(Class<?> controllerClass) {
    try {
        Constructor<?> ctor = controllerClass.getConstructors()[0];
        Object[] args = resolveDependencies(ctor.getParameterTypes());
        return ctor.newInstance(args);
    } catch (Exception e) {
        throw new RuntimeException("Failed to create " + controllerClass, e);
    }
}

// Option B: Use Dagger/Guice for automatic wiring
@Inject LoginController(LoginViewModel vm, ProfileService ps) { ... }
```

### Impact Assessment
| Metric                  | Current             | After Fix |
|-------------------------|---------------------|-----------|
| Lines of Code           | ~50                 | ~10       |
| New Controller Addition | Manual registration | Automatic |
| Compile-time Safety     | High                | Medium    |

---

## 12. PerformanceMonitor Timer Uses AppClock

### Issue Description
[`PerformanceMonitor.Timer`](src/main/java/datingapp/core/PerformanceMonitor.java:97) uses `AppClock.now()` instead of `System.nanoTime()`:

```java
public static Timer startTimer(String operationName) {
    return new Timer(operationName, AppClock.now());
}
```

The code comments acknowledge this is a trade-off for testability.

### Why It's Problematic
- **Incorrect measurements**: `Instant.now()` has millisecond precision, `nanoTime()` has nanosecond
- **Test artifacts**: Frozen clock in tests produces 0ms timings
- **Misleading data**: Production metrics may be inaccurate
- **False sense of testability**: Tests don't actually test timing behavior

### Suggested Solution
Use `System.nanoTime()` for measurements and accept that timing tests use real time:

```java
public static Timer startTimer(String operationName) {
    return new Timer(operationName, System.nanoTime());
}

@Override
public void close() {
    long durationNanos = System.nanoTime() - startNanos;
    record(operationName, TimeUnit.NANOSECONDS.toMillis(durationNanos));
}
```

For tests, use time limits or mock the `record` method instead.

### Impact Assessment
| Metric             | Current      | After Fix   |
|--------------------|--------------|-------------|
| Timing Precision   | Milliseconds | Nanoseconds |
| Test Accuracy      | Misleading   | Honest      |
| Production Metrics | Approximate  | Accurate    |

---

## Summary Table

| #  | Issue                         | Severity | Effort | Impact |
|----|-------------------------------|----------|--------|--------|
| 1  | AppConfig Builder Bloat       | High     | Medium | High   |
| 2  | ServiceRegistry God Object    | High     | High   | High   |
| 3  | Monolithic CLI Handlers       | High     | High   | High   |
| 4  | TestStorages Monolith         | Medium   | Low    | Medium |
| 5  | EnumSetUtil Redundancy        | Low      | Low    | Low    |
| 6  | LoggingSupport Anti-Pattern   | Medium   | Medium | Medium |
| 7  | UI Controller Complexity      | High     | High   | High   |
| 8  | RecommendationService SRP     | High     | Medium | High   |
| 9  | User Synchronization Overhead | Medium   | Medium | Medium |
| 10 | Inlined Constants             | Medium   | Low    | Medium |
| 11 | ViewModelFactory Mapping      | Low      | Medium | Low    |
| 12 | PerformanceMonitor Timer      | Low      | Low    | Low    |

---

## Recommended Prioritization

### Phase 1: Quick Wins (Low Effort, Good Impact)
1. **EnumSetUtil consolidation** - 30 minutes
2. **Inlined constants centralization** - 2 hours
3. **PerformanceMonitor fix** - 1 hour
4. **TestStorages split** - 2 hours

### Phase 2: Medium Effort, High Impact
5. **LoggingSupport replacement** - 4 hours
6. **RecommendationService split** - 8 hours
7. **User synchronization review** - 4 hours

### Phase 3: Major Refactoring (Plan Carefully)
8. **AppConfig Builder redesign** - 16 hours
9. **ServiceRegistry elimination** - 24 hours
10. **CLI Handler decomposition** - 24 hours
11. **UI Controller cleanup** - 32 hours

---

## Conclusion

The codebase demonstrates solid architectural foundations with clean layer separation (core/storage/app/ui). However, several classes have grown beyond reasonable maintainability thresholds. Addressing these issues incrementally will significantly improve:

- **Developer productivity** (less time navigating large files)
- **Code quality** (fewer bugs from copy-paste and missed updates)
- **Test coverage** (easier to test smaller, focused classes)
- **Onboarding** (new developers can understand smaller components faster)

The estimated total effort for all fixes is approximately **120 hours**, which could be spread across multiple sprints or addressed opportunistically when touching related code.
**Date:** 2026-02-23
**Scope:** Dating App Java/JavaFX Application
**LOC:** ~51,000 total / ~38,800 code

---

## Executive Summary

This analysis identifies **12 major maintainability issues** that significantly impact code readability, maintainability, and long-term evolution of the codebase. The findings focus on architectural decisions, code duplication, over-engineering, and structural issues that would yield the highest ROI if addressed.

---

## 1. AppConfig Builder Bloat

### Issue Description
The [`AppConfig.Builder`](src/main/java/datingapp/core/AppConfig.java:245) class contains **50+ individual setter methods** for configuration properties, resulting in a 670+ line nested class. Each property requires:
- A private field declaration
- A setter method
- Manual wiring in `build()`

### Why It's Problematic
- **High maintenance burden**: Adding a new config property requires changes in 4+ locations
- **Cognitive overload**: Developers must scroll through hundreds of lines to understand available options
- **Error-prone**: Easy to forget to wire a new property into `build()`
- **Violates DRY**: Field name repeated in field declaration, setter, and build method

### Suggested Solution
Use a configuration map or properties-based approach:

```java
// Option A: Properties file with auto-binding
public record AppConfig(Map<String, Object> properties) {
    public int dailyLikeLimit() { return (int) properties.getOrDefault("dailyLikeLimit", 100); }
}

// Option B: Generate Builder via annotation processor
@ConfigProperties
public record AppConfig(int dailyLikeLimit, int dailySuperLikeLimit, ...) {}
```

### Impact Assessment
| Metric                | Current     | After Fix  |
|-----------------------|-------------|------------|
| Lines of Code         | ~670        | ~100       |
| Maintenance Cost      | High        | Low        |
| New Property Addition | 4 locations | 1 location |

---

## 2. ServiceRegistry as God Object

### Issue Description
[`ServiceRegistry`](src/main/java/datingapp/core/ServiceRegistry.java:21) acts as a **service locator** with 16+ getter methods for various services and storages. It's injected everywhere and creates implicit dependencies.

### Why It's Problematic
- **Hidden dependencies**: Classes declare `ServiceRegistry` but actually use only 2-3 services
- **Testing difficulty**: Must mock entire registry even when testing a single service
- **Violation of ISP**: Interface Segregation Principle - clients depend on methods they don't use
- **Encourages coupling**: Easy to add "just one more" service reference

### Suggested Solution
Use **explicit dependency injection** via constructors:

```java
// Instead of:
public MatchingHandler(ServiceRegistry services) {
    this.matchingService = services.getMatchingService();
    this.userStorage = services.getUserStorage();
    // ... 10 more
}

// Prefer:
public MatchingHandler(MatchingService matchingService, UserStorage userStorage) {
    this.matchingService = matchingService;
    this.userStorage = userStorage;
}
```

Or use a DI framework (Guice, Dagger) that generates the wiring.

### Impact Assessment
| Metric                 | Current      | After Fix      |
|------------------------|--------------|----------------|
| Constructor Parameters | 1 (registry) | 3-5 (explicit) |
| Test Mock Setup        | Complex      | Simple         |
| Dependency Visibility  | Hidden       | Explicit       |

---

## 3. Monolithic CLI Handler Classes

### Issue Description
CLI handlers are massive classes with mixed responsibilities:
- [`MatchingHandler.java`](src/main/java/datingapp/app/cli/MatchingHandler.java) - **42,018 chars**
- [`ProfileHandler.java`](src/main/java/datingapp/app/cli/ProfileHandler.java) - **41,918 chars**

These classes handle:
- User input parsing
- Business logic orchestration
- Display formatting
- Achievement checking
- Navigation flow

### Why It's Problematic
- **Single Responsibility Principle violation**: One class doing 5+ different things
- **Difficult to test**: Must set up entire environment to test a single flow
- **Code navigation**: Finding specific logic requires scrolling through thousands of lines
- **Merge conflicts**: Multiple developers working on same large file

### Suggested Solution
Extract focused components:

```java
// Current: One massive class
public class MatchingHandler { /* 1000+ lines */ }

// Proposed: Focused components
public class CandidateBrowser { /* handles browsing flow */ }
public class MatchDisplayer { /* handles formatting */ }
public class SwipeProcessor { /* handles swipe logic */ }
public class MatchingHandler { /* orchestrates only */ }
```

### Impact Assessment
| Metric              | Current     | After Fix  |
|---------------------|-------------|------------|
| Avg Class Size      | ~1000 lines | ~200 lines |
| Test Complexity     | High        | Low        |
| Merge Conflict Risk | High        | Low        |

---

## 4. TestStorages Monolith

### Issue Description
[`TestStorages.java`](src/test/java/datingapp/core/testutil/TestStorages.java) is a **45,660 character** file containing 5+ in-memory storage implementations as nested classes.

### Why It's Problematic
- **Navigation nightmare**: Finding a specific storage implementation requires searching
- **Compilation coupling**: Any change forces recompilation of all test utilities
- **IDE performance**: Large files slow down code analysis
- **Unclear ownership**: Who "owns" each storage implementation?

### Suggested Solution
Split into dedicated files:

```
src/test/java/datingapp/core/testutil/
├── TestUserStorage.java
├── TestInteractionStorage.java
├── TestCommunicationStorage.java
├── TestAnalyticsStorage.java
└── TestTrustSafetyStorage.java
```

### Impact Assessment
| Metric             | Current   | After Fix      |
|--------------------|-----------|----------------|
| File Size          | 45K chars | ~8K chars each |
| Compilation Units  | 1         | 5              |
| IDE Responsiveness | Slower    | Faster         |

---

## 5. EnumSetUtil Redundant Methods

### Issue Description
[`EnumSetUtil`](src/main/java/datingapp/core/EnumSetUtil.java) contains two nearly identical `safeCopy` methods:

```java
public static <E> EnumSet<E> safeCopy(Collection<E> source, Class<E> enumClass) { ... }
public static <E> EnumSet<E> safeCopy(Set<E> source, Class<E> enumClass) { ... }
```

And a third method `defensiveCopy` that does the same thing:

```java
public static <E> EnumSet<E> defensiveCopy(EnumSet<E> source, Class<E> enumClass) { ... }
```

### Why It's Problematic
- **Confusion**: Which method should callers use?
- **Maintenance burden**: Bug fixes must be applied to multiple locations
- **Code bloat**: 64 lines where 15 would suffice

### Suggested Solution
Consolidate to a single method:

```java
public static <E extends Enum<E>> EnumSet<E> safeCopy(Collection<E> source, Class<E> enumClass) {
    Objects.requireNonNull(enumClass, "enumClass cannot be null");
    if (source == null || source.isEmpty()) {
        return EnumSet.noneOf(enumClass);
    }
    return source instanceof EnumSet ? EnumSet.copyOf((EnumSet<E>) source) : EnumSet.copyOf(source);
}
```

### Impact Assessment
| Metric        | Current   | After Fix |
|---------------|-----------|-----------|
| Methods       | 3         | 1         |
| Lines of Code | 64        | ~15       |
| API Surface   | Confusing | Clear     |

---

## 6. LoggingSupport Interface Anti-Pattern

### Issue Description
[`LoggingSupport`](src/main/java/datingapp/core/LoggingSupport.java) is an interface that:
1. Requires implementers to provide `logger()` method
2. Provides default logging methods that delegate to `logger()`
3. Also provides **static** logging methods that take a Logger parameter

### Why It's Problematic
- **Interface bloat**: Mixing instance and static concerns
- **Redundant API**: Two ways to do the same thing
- **Forced inheritance**: Classes must implement the interface to get logging
- **Not a true interface**: It's a trait/mixin pretending to be an interface

### Suggested Solution
Option A: Use a simple utility class:

```java
public final class Log {
    public static void info(Logger log, String msg, Object... args) {
        if (log.isInfoEnabled()) log.info(msg, args);
    }
    // ... other levels
}
```

Option B: Use Lombok's `@Slf4j` annotation:

```java
@Slf4j
public class MyClass {
    public void doSomething() {
        log.info("Doing something"); // Auto-generated logger field
    }
}
```

### Impact Assessment
| Metric                | Current                                  | After Fix     |
|-----------------------|------------------------------------------|---------------|
| Interface Methods     | 11                                       | 0 (eliminate) |
| Boilerplate per Class | `implements LoggingSupport` + `logger()` | None          |
| Flexibility           | Low                                      | High          |

---

## 7. UI Controller Complexity

### Issue Description
Several UI controllers are extremely large:
- [`MatchesController.java`](src/main/java/datingapp/ui/screen/MatchesController.java) - **31,637 chars**
- [`LoginController.java`](src/main/java/datingapp/ui/screen/LoginController.java) - **27,110 chars**
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java) - **32,610 chars**

These contain:
- Inline CSS styles as string constants
- Animation logic mixed with business logic
- Complex cell factory implementations
- Extensive FXML binding code

### Why It's Problematic
- **View logic in controllers**: CSS and animations should be in separate files
- **Testing difficulty**: Must instantiate JavaFX components to test logic
- **Code reuse**: Animation patterns duplicated across controllers
- **Readability**: Business logic buried in UI setup code

### Suggested Solution
1. **Extract CSS to stylesheets**:
```java
// Current: Inline styles
private static final String STYLE_TITLE = "-fx-font-size: 24px; -fx-font-weight: bold; ...";

// Proposed: External CSS
// styles.css
.title { -fx-font-size: 24px; -fx-font-weight: bold; }
```

2. **Extract animations to UiAnimations** (already partially done)

3. **Use cell factory utilities**:
```java
// Current: Inline cell factory in controller
userListView.setCellFactory(lv -> new ListCell<>() { /* 50 lines */ });

// Proposed: Dedicated factory
userListView.setCellFactory(UserListCell::new);
```

### Impact Assessment
| Metric              | Current      | After Fix  |
|---------------------|--------------|------------|
| Avg Controller Size | ~30K chars   | ~15K chars |
| CSS Location        | Java strings | CSS files  |
| Test Coverage       | Low          | High       |

---

## 8. RecommendationService Multiple Responsibilities

### Issue Description
[`RecommendationService`](src/main/java/datingapp/core/matching/RecommendationService.java) handles:
- Daily like/pass limits
- Daily pick selection
- Standout generation
- Activity scoring
- LRU cache management

### Why It's Problematic
- **Single Responsibility Principle violation**: 5+ distinct responsibilities
- **Testing complexity**: Must understand all features to test any one
- **Change impact**: Modifying limits affects standouts code
- **Builder bloat**: 10+ required dependencies

### Suggested Solution
Extract focused services:

```java
// Proposed structure
DailyLimitService     // canLike, canPass, getStatus
DailyPickService      // getDailyPick, markViewed
StandoutService       // getStandouts, scoreCandidate
ActivityScoreService  // calculateActivityScore
```

### Impact Assessment
| Metric          | Current   | After Fix       |
|-----------------|-----------|-----------------|
| Class Size      | 623 lines | ~150 lines each |
| Dependencies    | 10        | 3-4 each        |
| Test Complexity | High      | Low             |

---

## 9. User Model Synchronization Overhead

### Issue Description
The [`User`](src/main/java/datingapp/core/model/User.java) class has **every getter and setter synchronized**:

```java
public synchronized String getName() { return name; }
public synchronized void setName(String name) { ... }
public synchronized Gender getGender() { return gender; }
// ... 40+ synchronized methods
```

### Why It's Problematic
- **Performance overhead**: Synchronization on every field access
- **False sense of thread safety**: Individual field access is thread-safe, but compound operations are not
- **Lock contention**: All methods synchronize on `this`, creating a bottleneck
- **Over-engineering**: Most access is single-threaded (JavaFX Application Thread)

### Suggested Solution
1. **Use `volatile` for simple fields** if visibility is the only concern
2. **Use `ReadWriteLock`** for read-heavy access patterns
3. **Accept single-threaded assumption** for UI-bound entities:

```java
// If only accessed from JavaFX thread:
public String getName() { return name; }  // No synchronization

// For truly concurrent access:
private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
public String getName() {
    lock.readLock().lock();
    try { return name; }
    finally { lock.readLock().unlock(); }
}
```

### Impact Assessment
| Metric               | Current   | After Fix |
|----------------------|-----------|-----------|
| Synchronized Methods | 40+       | 0-5       |
| Lock Contention      | High      | None      |
| Code Clarity         | Cluttered | Clean     |

---

## 10. Inlined Constants from Deleted ScoringConstants

### Issue Description
Multiple services have inlined "magic numbers" that were previously in `ScoringConstants`:

```java
// MatchQualityService.java
private static final int STAR_EXCELLENT_THRESHOLD = 90;
private static final int STAR_GREAT_THRESHOLD = 75;
private static final double PACE_SYNC_PERFECT = 0.95;
// ... 30+ constants

// ProfileService.java
private static final int BIO_TIP_MIN_LENGTH = 50;
private static final int TIER_DIAMOND_THRESHOLD = 95;
// ... 20+ constants
```

### Why It's Problematic
- **Duplication**: Same thresholds appear in multiple files
- **Inconsistency risk**: Changing a threshold in one place but not another
- **No single source of truth**: What's the "official" star threshold?
- **Hard to tune**: Must search codebase to find all related values

### Suggested Solution
Create a focused constants class or use AppConfig:

```java
// Option A: Dedicated constants
public final class ScoringThresholds {
    public static final int STAR_EXCELLENT = 90;
    public static final int STAR_GREAT = 75;
    // ...
}

// Option B: Add to AppConfig (better for runtime tuning)
public record AlgorithmConfig(
    int starExcellentThreshold,
    int starGreatThreshold,
    // ...
) {}
```

### Impact Assessment
| Metric             | Current    | After Fix   |
|--------------------|------------|-------------|
| Constants Location | Scattered  | Centralized |
| Runtime Tuning     | Impossible | Possible    |
| Consistency        | Uncertain  | Guaranteed  |

---

## 11. ViewModelFactory Manual Controller Mapping

### Issue Description
[`ViewModelFactory`](src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java) maintains a manual map of controller factories:

```java
private Map<Class<?>, Supplier<Object>> buildControllerFactories() {
    Map<Class<?>, Supplier<Object>> map = new HashMap<>();
    map.put(LoginController.class, () -> new LoginController(getLoginViewModel(), ...));
    map.put(DashboardController.class, () -> new DashboardController(getDashboardViewModel()));
    // ... 10 more
    return map;
}
```

### Why It's Problematic
- **Boilerplate**: Every controller must be manually registered
- **Error-prone**: Easy to forget to register a new controller
- **Tight coupling**: Factory knows about all controller types
- **Scaling issue**: Adding controllers requires modifying this class

### Suggested Solution
Use reflection or a DI framework:

```java
// Option A: Reflection-based
public Object createController(Class<?> controllerClass) {
    try {
        Constructor<?> ctor = controllerClass.getConstructors()[0];
        Object[] args = resolveDependencies(ctor.getParameterTypes());
        return ctor.newInstance(args);
    } catch (Exception e) {
        throw new RuntimeException("Failed to create " + controllerClass, e);
    }
}

// Option B: Use Dagger/Guice for automatic wiring
@Inject LoginController(LoginViewModel vm, ProfileService ps) { ... }
```

### Impact Assessment
| Metric                  | Current             | After Fix |
|-------------------------|---------------------|-----------|
| Lines of Code           | ~50                 | ~10       |
| New Controller Addition | Manual registration | Automatic |
| Compile-time Safety     | High                | Medium    |

---

## 12. PerformanceMonitor Timer Uses AppClock

### Issue Description
[`PerformanceMonitor.Timer`](src/main/java/datingapp/core/PerformanceMonitor.java:97) uses `AppClock.now()` instead of `System.nanoTime()`:

```java
public static Timer startTimer(String operationName) {
    return new Timer(operationName, AppClock.now());
}
```

The code comments acknowledge this is a trade-off for testability.

### Why It's Problematic
- **Incorrect measurements**: `Instant.now()` has millisecond precision, `nanoTime()` has nanosecond
- **Test artifacts**: Frozen clock in tests produces 0ms timings
- **Misleading data**: Production metrics may be inaccurate
- **False sense of testability**: Tests don't actually test timing behavior

### Suggested Solution
Use `System.nanoTime()` for measurements and accept that timing tests use real time:

```java
public static Timer startTimer(String operationName) {
    return new Timer(operationName, System.nanoTime());
}

@Override
public void close() {
    long durationNanos = System.nanoTime() - startNanos;
    record(operationName, TimeUnit.NANOSECONDS.toMillis(durationNanos));
}
```

For tests, use time limits or mock the `record` method instead.

### Impact Assessment
| Metric             | Current      | After Fix   |
|--------------------|--------------|-------------|
| Timing Precision   | Milliseconds | Nanoseconds |
| Test Accuracy      | Misleading   | Honest      |
| Production Metrics | Approximate  | Accurate    |

---

## Summary Table

| #  | Issue                         | Severity | Effort | Impact |
|----|-------------------------------|----------|--------|--------|
| 1  | AppConfig Builder Bloat       | High     | Medium | High   |
| 2  | ServiceRegistry God Object    | High     | High   | High   |
| 3  | Monolithic CLI Handlers       | High     | High   | High   |
| 4  | TestStorages Monolith         | Medium   | Low    | Medium |
| 5  | EnumSetUtil Redundancy        | Low      | Low    | Low    |
| 6  | LoggingSupport Anti-Pattern   | Medium   | Medium | Medium |
| 7  | UI Controller Complexity      | High     | High   | High   |
| 8  | RecommendationService SRP     | High     | Medium | High   |
| 9  | User Synchronization Overhead | Medium   | Medium | Medium |
| 10 | Inlined Constants             | Medium   | Low    | Medium |
| 11 | ViewModelFactory Mapping      | Low      | Medium | Low    |
| 12 | PerformanceMonitor Timer      | Low      | Low    | Low    |

---

## Recommended Prioritization

### Phase 1: Quick Wins (Low Effort, Good Impact)
1. **EnumSetUtil consolidation** - 30 minutes
2. **Inlined constants centralization** - 2 hours
3. **PerformanceMonitor fix** - 1 hour
4. **TestStorages split** - 2 hours

### Phase 2: Medium Effort, High Impact
5. **LoggingSupport replacement** - 4 hours
6. **RecommendationService split** - 8 hours
7. **User synchronization review** - 4 hours

### Phase 3: Major Refactoring (Plan Carefully)
8. **AppConfig Builder redesign** - 16 hours
9. **ServiceRegistry elimination** - 24 hours
10. **CLI Handler decomposition** - 24 hours
11. **UI Controller cleanup** - 32 hours

---

## Conclusion

The codebase demonstrates solid architectural foundations with clean layer separation (core/storage/app/ui). However, several classes have grown beyond reasonable maintainability thresholds. Addressing these issues incrementally will significantly improve:

- **Developer productivity** (less time navigating large files)
- **Code quality** (fewer bugs from copy-paste and missed updates)
- **Test coverage** (easier to test smaller, focused classes)
- **Onboarding** (new developers can understand smaller components faster)

The estimated total effort for all fixes is approximately **120 hours**, which could be spread across multiple sprints or addressed opportunistically when touching related code.
**Date:** 2026-02-23
**Scope:** Dating App Java/JavaFX Application
**LOC:** ~51,000 total / ~38,800 code

---

## Executive Summary

This analysis identifies **12 major maintainability issues** that significantly impact code readability, maintainability, and long-term evolution of the codebase. The findings focus on architectural decisions, code duplication, over-engineering, and structural issues that would yield the highest ROI if addressed.

---

## 1. AppConfig Builder Bloat

### Issue Description
The [`AppConfig.Builder`](src/main/java/datingapp/core/AppConfig.java:245) class contains **50+ individual setter methods** for configuration properties, resulting in a 670+ line nested class. Each property requires:
- A private field declaration
- A setter method
- Manual wiring in `build()`

### Why It's Problematic
- **High maintenance burden**: Adding a new config property requires changes in 4+ locations
- **Cognitive overload**: Developers must scroll through hundreds of lines to understand available options
- **Error-prone**: Easy to forget to wire a new property into `build()`
- **Violates DRY**: Field name repeated in field declaration, setter, and build method

### Suggested Solution
Use a configuration map or properties-based approach:

```java
// Option A: Properties file with auto-binding
public record AppConfig(Map<String, Object> properties) {
    public int dailyLikeLimit() { return (int) properties.getOrDefault("dailyLikeLimit", 100); }
}

// Option B: Generate Builder via annotation processor
@ConfigProperties
public record AppConfig(int dailyLikeLimit, int dailySuperLikeLimit, ...) {}
```

### Impact Assessment
| Metric                | Current     | After Fix  |
|-----------------------|-------------|------------|
| Lines of Code         | ~670        | ~100       |
| Maintenance Cost      | High        | Low        |
| New Property Addition | 4 locations | 1 location |

---

## 2. ServiceRegistry as God Object

### Issue Description
[`ServiceRegistry`](src/main/java/datingapp/core/ServiceRegistry.java:21) acts as a **service locator** with 16+ getter methods for various services and storages. It's injected everywhere and creates implicit dependencies.

### Why It's Problematic
- **Hidden dependencies**: Classes declare `ServiceRegistry` but actually use only 2-3 services
- **Testing difficulty**: Must mock entire registry even when testing a single service
- **Violation of ISP**: Interface Segregation Principle - clients depend on methods they don't use
- **Encourages coupling**: Easy to add "just one more" service reference

### Suggested Solution
Use **explicit dependency injection** via constructors:

```java
// Instead of:
public MatchingHandler(ServiceRegistry services) {
    this.matchingService = services.getMatchingService();
    this.userStorage = services.getUserStorage();
    // ... 10 more
}

// Prefer:
public MatchingHandler(MatchingService matchingService, UserStorage userStorage) {
    this.matchingService = matchingService;
    this.userStorage = userStorage;
}
```

Or use a DI framework (Guice, Dagger) that generates the wiring.

### Impact Assessment
| Metric                 | Current      | After Fix      |
|------------------------|--------------|----------------|
| Constructor Parameters | 1 (registry) | 3-5 (explicit) |
| Test Mock Setup        | Complex      | Simple         |
| Dependency Visibility  | Hidden       | Explicit       |

---

## 3. Monolithic CLI Handler Classes

### Issue Description
CLI handlers are massive classes with mixed responsibilities:
- [`MatchingHandler.java`](src/main/java/datingapp/app/cli/MatchingHandler.java) - **42,018 chars**
- [`ProfileHandler.java`](src/main/java/datingapp/app/cli/ProfileHandler.java) - **41,918 chars**

These classes handle:
- User input parsing
- Business logic orchestration
- Display formatting
- Achievement checking
- Navigation flow

### Why It's Problematic
- **Single Responsibility Principle violation**: One class doing 5+ different things
- **Difficult to test**: Must set up entire environment to test a single flow
- **Code navigation**: Finding specific logic requires scrolling through thousands of lines
- **Merge conflicts**: Multiple developers working on same large file

### Suggested Solution
Extract focused components:

```java
// Current: One massive class
public class MatchingHandler { /* 1000+ lines */ }

// Proposed: Focused components
public class CandidateBrowser { /* handles browsing flow */ }
public class MatchDisplayer { /* handles formatting */ }
public class SwipeProcessor { /* handles swipe logic */ }
public class MatchingHandler { /* orchestrates only */ }
```

### Impact Assessment
| Metric              | Current     | After Fix  |
|---------------------|-------------|------------|
| Avg Class Size      | ~1000 lines | ~200 lines |
| Test Complexity     | High        | Low        |
| Merge Conflict Risk | High        | Low        |

---

## 4. TestStorages Monolith

### Issue Description
[`TestStorages.java`](src/test/java/datingapp/core/testutil/TestStorages.java) is a **45,660 character** file containing 5+ in-memory storage implementations as nested classes.

### Why It's Problematic
- **Navigation nightmare**: Finding a specific storage implementation requires searching
- **Compilation coupling**: Any change forces recompilation of all test utilities
- **IDE performance**: Large files slow down code analysis
- **Unclear ownership**: Who "owns" each storage implementation?

### Suggested Solution
Split into dedicated files:

```
src/test/java/datingapp/core/testutil/
├── TestUserStorage.java
├── TestInteractionStorage.java
├── TestCommunicationStorage.java
├── TestAnalyticsStorage.java
└── TestTrustSafetyStorage.java
```

### Impact Assessment
| Metric             | Current   | After Fix      |
|--------------------|-----------|----------------|
| File Size          | 45K chars | ~8K chars each |
| Compilation Units  | 1         | 5              |
| IDE Responsiveness | Slower    | Faster         |

---

## 5. EnumSetUtil Redundant Methods

### Issue Description
[`EnumSetUtil`](src/main/java/datingapp/core/EnumSetUtil.java) contains two nearly identical `safeCopy` methods:

```java
public static <E> EnumSet<E> safeCopy(Collection<E> source, Class<E> enumClass) { ... }
public static <E> EnumSet<E> safeCopy(Set<E> source, Class<E> enumClass) { ... }
```

And a third method `defensiveCopy` that does the same thing:

```java
public static <E> EnumSet<E> defensiveCopy(EnumSet<E> source, Class<E> enumClass) { ... }
```

### Why It's Problematic
- **Confusion**: Which method should callers use?
- **Maintenance burden**: Bug fixes must be applied to multiple locations
- **Code bloat**: 64 lines where 15 would suffice

### Suggested Solution
Consolidate to a single method:

```java
public static <E extends Enum<E>> EnumSet<E> safeCopy(Collection<E> source, Class<E> enumClass) {
    Objects.requireNonNull(enumClass, "enumClass cannot be null");
    if (source == null || source.isEmpty()) {
        return EnumSet.noneOf(enumClass);
    }
    return source instanceof EnumSet ? EnumSet.copyOf((EnumSet<E>) source) : EnumSet.copyOf(source);
}
```

### Impact Assessment
| Metric        | Current   | After Fix |
|---------------|-----------|-----------|
| Methods       | 3         | 1         |
| Lines of Code | 64        | ~15       |
| API Surface   | Confusing | Clear     |

---

## 6. LoggingSupport Interface Anti-Pattern

### Issue Description
[`LoggingSupport`](src/main/java/datingapp/core/LoggingSupport.java) is an interface that:
1. Requires implementers to provide `logger()` method
2. Provides default logging methods that delegate to `logger()`
3. Also provides **static** logging methods that take a Logger parameter

### Why It's Problematic
- **Interface bloat**: Mixing instance and static concerns
- **Redundant API**: Two ways to do the same thing
- **Forced inheritance**: Classes must implement the interface to get logging
- **Not a true interface**: It's a trait/mixin pretending to be an interface

### Suggested Solution
Option A: Use a simple utility class:

```java
public final class Log {
    public static void info(Logger log, String msg, Object... args) {
        if (log.isInfoEnabled()) log.info(msg, args);
    }
    // ... other levels
}
```

Option B: Use Lombok's `@Slf4j` annotation:

```java
@Slf4j
public class MyClass {
    public void doSomething() {
        log.info("Doing something"); // Auto-generated logger field
    }
}
```

### Impact Assessment
| Metric                | Current                                  | After Fix     |
|-----------------------|------------------------------------------|---------------|
| Interface Methods     | 11                                       | 0 (eliminate) |
| Boilerplate per Class | `implements LoggingSupport` + `logger()` | None          |
| Flexibility           | Low                                      | High          |

---

## 7. UI Controller Complexity

### Issue Description
Several UI controllers are extremely large:
- [`MatchesController.java`](src/main/java/datingapp/ui/screen/MatchesController.java) - **31,637 chars**
- [`LoginController.java`](src/main/java/datingapp/ui/screen/LoginController.java) - **27,110 chars**
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java) - **32,610 chars**

These contain:
- Inline CSS styles as string constants
- Animation logic mixed with business logic
- Complex cell factory implementations
- Extensive FXML binding code

### Why It's Problematic
- **View logic in controllers**: CSS and animations should be in separate files
- **Testing difficulty**: Must instantiate JavaFX components to test logic
- **Code reuse**: Animation patterns duplicated across controllers
- **Readability**: Business logic buried in UI setup code

### Suggested Solution
1. **Extract CSS to stylesheets**:
```java
// Current: Inline styles
private static final String STYLE_TITLE = "-fx-font-size: 24px; -fx-font-weight: bold; ...";

// Proposed: External CSS
// styles.css
.title { -fx-font-size: 24px; -fx-font-weight: bold; }
```

2. **Extract animations to UiAnimations** (already partially done)

3. **Use cell factory utilities**:
```java
// Current: Inline cell factory in controller
userListView.setCellFactory(lv -> new ListCell<>() { /* 50 lines */ });

// Proposed: Dedicated factory
userListView.setCellFactory(UserListCell::new);
```

### Impact Assessment
| Metric              | Current      | After Fix  |
|---------------------|--------------|------------|
| Avg Controller Size | ~30K chars   | ~15K chars |
| CSS Location        | Java strings | CSS files  |
| Test Coverage       | Low          | High       |

---

## 8. RecommendationService Multiple Responsibilities

### Issue Description
[`RecommendationService`](src/main/java/datingapp/core/matching/RecommendationService.java) handles:
- Daily like/pass limits
- Daily pick selection
- Standout generation
- Activity scoring
- LRU cache management

### Why It's Problematic
- **Single Responsibility Principle violation**: 5+ distinct responsibilities
- **Testing complexity**: Must understand all features to test any one
- **Change impact**: Modifying limits affects standouts code
- **Builder bloat**: 10+ required dependencies

### Suggested Solution
Extract focused services:

```java
// Proposed structure
DailyLimitService     // canLike, canPass, getStatus
DailyPickService      // getDailyPick, markViewed
StandoutService       // getStandouts, scoreCandidate
ActivityScoreService  // calculateActivityScore
```

### Impact Assessment
| Metric          | Current   | After Fix       |
|-----------------|-----------|-----------------|
| Class Size      | 623 lines | ~150 lines each |
| Dependencies    | 10        | 3-4 each        |
| Test Complexity | High      | Low             |

---

## 9. User Model Synchronization Overhead

### Issue Description
The [`User`](src/main/java/datingapp/core/model/User.java) class has **every getter and setter synchronized**:

```java
public synchronized String getName() { return name; }
public synchronized void setName(String name) { ... }
public synchronized Gender getGender() { return gender; }
// ... 40+ synchronized methods
```

### Why It's Problematic
- **Performance overhead**: Synchronization on every field access
- **False sense of thread safety**: Individual field access is thread-safe, but compound operations are not
- **Lock contention**: All methods synchronize on `this`, creating a bottleneck
- **Over-engineering**: Most access is single-threaded (JavaFX Application Thread)

### Suggested Solution
1. **Use `volatile` for simple fields** if visibility is the only concern
2. **Use `ReadWriteLock`** for read-heavy access patterns
3. **Accept single-threaded assumption** for UI-bound entities:

```java
// If only accessed from JavaFX thread:
public String getName() { return name; }  // No synchronization

// For truly concurrent access:
private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
public String getName() {
    lock.readLock().lock();
    try { return name; }
    finally { lock.readLock().unlock(); }
}
```

### Impact Assessment
| Metric               | Current   | After Fix |
|----------------------|-----------|-----------|
| Synchronized Methods | 40+       | 0-5       |
| Lock Contention      | High      | None      |
| Code Clarity         | Cluttered | Clean     |

---

## 10. Inlined Constants from Deleted ScoringConstants

### Issue Description
Multiple services have inlined "magic numbers" that were previously in `ScoringConstants`:

```java
// MatchQualityService.java
private static final int STAR_EXCELLENT_THRESHOLD = 90;
private static final int STAR_GREAT_THRESHOLD = 75;
private static final double PACE_SYNC_PERFECT = 0.95;
// ... 30+ constants

// ProfileService.java
private static final int BIO_TIP_MIN_LENGTH = 50;
private static final int TIER_DIAMOND_THRESHOLD = 95;
// ... 20+ constants
```

### Why It's Problematic
- **Duplication**: Same thresholds appear in multiple files
- **Inconsistency risk**: Changing a threshold in one place but not another
- **No single source of truth**: What's the "official" star threshold?
- **Hard to tune**: Must search codebase to find all related values

### Suggested Solution
Create a focused constants class or use AppConfig:

```java
// Option A: Dedicated constants
public final class ScoringThresholds {
    public static final int STAR_EXCELLENT = 90;
    public static final int STAR_GREAT = 75;
    // ...
}

// Option B: Add to AppConfig (better for runtime tuning)
public record AlgorithmConfig(
    int starExcellentThreshold,
    int starGreatThreshold,
    // ...
) {}
```

### Impact Assessment
| Metric             | Current    | After Fix   |
|--------------------|------------|-------------|
| Constants Location | Scattered  | Centralized |
| Runtime Tuning     | Impossible | Possible    |
| Consistency        | Uncertain  | Guaranteed  |

---

## 11. ViewModelFactory Manual Controller Mapping

### Issue Description
[`ViewModelFactory`](src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java) maintains a manual map of controller factories:

```java
private Map<Class<?>, Supplier<Object>> buildControllerFactories() {
    Map<Class<?>, Supplier<Object>> map = new HashMap<>();
    map.put(LoginController.class, () -> new LoginController(getLoginViewModel(), ...));
    map.put(DashboardController.class, () -> new DashboardController(getDashboardViewModel()));
    // ... 10 more
    return map;
}
```

### Why It's Problematic
- **Boilerplate**: Every controller must be manually registered
- **Error-prone**: Easy to forget to register a new controller
- **Tight coupling**: Factory knows about all controller types
- **Scaling issue**: Adding controllers requires modifying this class

### Suggested Solution
Use reflection or a DI framework:

```java
// Option A: Reflection-based
public Object createController(Class<?> controllerClass) {
    try {
        Constructor<?> ctor = controllerClass.getConstructors()[0];
        Object[] args = resolveDependencies(ctor.getParameterTypes());
        return ctor.newInstance(args);
    } catch (Exception e) {
        throw new RuntimeException("Failed to create " + controllerClass, e);
    }
}

// Option B: Use Dagger/Guice for automatic wiring
@Inject LoginController(LoginViewModel vm, ProfileService ps) { ... }
```

### Impact Assessment
| Metric                  | Current             | After Fix |
|-------------------------|---------------------|-----------|
| Lines of Code           | ~50                 | ~10       |
| New Controller Addition | Manual registration | Automatic |
| Compile-time Safety     | High                | Medium    |

---

## 12. PerformanceMonitor Timer Uses AppClock

### Issue Description
[`PerformanceMonitor.Timer`](src/main/java/datingapp/core/PerformanceMonitor.java:97) uses `AppClock.now()` instead of `System.nanoTime()`:

```java
public static Timer startTimer(String operationName) {
    return new Timer(operationName, AppClock.now());
}
```

The code comments acknowledge this is a trade-off for testability.

### Why It's Problematic
- **Incorrect measurements**: `Instant.now()` has millisecond precision, `nanoTime()` has nanosecond
- **Test artifacts**: Frozen clock in tests produces 0ms timings
- **Misleading data**: Production metrics may be inaccurate
- **False sense of testability**: Tests don't actually test timing behavior

### Suggested Solution
Use `System.nanoTime()` for measurements and accept that timing tests use real time:

```java
public static Timer startTimer(String operationName) {
    return new Timer(operationName, System.nanoTime());
}

@Override
public void close() {
    long durationNanos = System.nanoTime() - startNanos;
    record(operationName, TimeUnit.NANOSECONDS.toMillis(durationNanos));
}
```

For tests, use time limits or mock the `record` method instead.

### Impact Assessment
| Metric             | Current      | After Fix   |
|--------------------|--------------|-------------|
| Timing Precision   | Milliseconds | Nanoseconds |
| Test Accuracy      | Misleading   | Honest      |
| Production Metrics | Approximate  | Accurate    |

---

## Summary Table

| #  | Issue                         | Severity | Effort | Impact |
|----|-------------------------------|----------|--------|--------|
| 1  | AppConfig Builder Bloat       | High     | Medium | High   |
| 2  | ServiceRegistry God Object    | High     | High   | High   |
| 3  | Monolithic CLI Handlers       | High     | High   | High   |
| 4  | TestStorages Monolith         | Medium   | Low    | Medium |
| 5  | EnumSetUtil Redundancy        | Low      | Low    | Low    |
| 6  | LoggingSupport Anti-Pattern   | Medium   | Medium | Medium |
| 7  | UI Controller Complexity      | High     | High   | High   |
| 8  | RecommendationService SRP     | High     | Medium | High   |
| 9  | User Synchronization Overhead | Medium   | Medium | Medium |
| 10 | Inlined Constants             | Medium   | Low    | Medium |
| 11 | ViewModelFactory Mapping      | Low      | Medium | Low    |
| 12 | PerformanceMonitor Timer      | Low      | Low    | Low    |

---

## Recommended Prioritization

### Phase 1: Quick Wins (Low Effort, Good Impact)
1. **EnumSetUtil consolidation** - 30 minutes
2. **Inlined constants centralization** - 2 hours
3. **PerformanceMonitor fix** - 1 hour
4. **TestStorages split** - 2 hours

### Phase 2: Medium Effort, High Impact
5. **LoggingSupport replacement** - 4 hours
6. **RecommendationService split** - 8 hours
7. **User synchronization review** - 4 hours

### Phase 3: Major Refactoring (Plan Carefully)
8. **AppConfig Builder redesign** - 16 hours
9. **ServiceRegistry elimination** - 24 hours
10. **CLI Handler decomposition** - 24 hours
11. **UI Controller cleanup** - 32 hours

---

## Conclusion

The codebase demonstrates solid architectural foundations with clean layer separation (core/storage/app/ui). However, several classes have grown beyond reasonable maintainability thresholds. Addressing these issues incrementally will significantly improve:

- **Developer productivity** (less time navigating large files)
- **Code quality** (fewer bugs from copy-paste and missed updates)
- **Test coverage** (easier to test smaller, focused classes)
- **Onboarding** (new developers can understand smaller components faster)

The estimated total effort for all fixes is approximately **120 hours**, which could be spread across multiple sprints or addressed opportunistically when touching related code.

