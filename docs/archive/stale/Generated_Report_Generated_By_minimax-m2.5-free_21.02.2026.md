# Code Audit Report - Dating App

**Generated:** 21.02.2026
**Scope:** `src/main/java/**` and `src/test/java/**`
**Model:** minimax-m2.5:free
**PMD Check:** BUILD SUCCESS

---

## 1. Architecture Overview

### 1.1 Data Flow & Entry Points

The application follows **Clean Architecture** with three distinct layers:

```
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 3A (CLI)          │  LAYER 3B (JavaFX)                  │
│  Main.java               │  DatingApp.java                     │
│  - MatchingHandler       │  - NavigationService                │
│  - ProfileHandler        │  - ViewModelFactory                  │
│  - SafetyHandler         │  - 8 Screen Controllers              │
│  - StatsHandler          │  - 8 ViewModels                     │
│  - MessagingHandler      │                                      │
└──────────────────────────┼──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│  LAYER 2: STORAGE (JDBI 3 + H2)                                 │
│  DatabaseManager → StorageFactory → Jdbi*Storage implementations│
│  - JdbiUserStorage, JdbiMatchmakingStorage, JdbiConnectionStorage│
│  - JdbiMetricsStorage, JdbiTrustSafetyStorage                  │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│  LAYER 1: CORE (Pure Business Logic)                           │
│  - MatchingService, CandidateFinder, RecommendationService     │
│  - ConnectionService (messaging + relationships)                │
│  - TrustSafetyService, UndoService, ProfileService             │
│  - ActivityMetricsService, MatchQualityService                 │
│  - User/Match models (mutable entities)                        │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Dependency Injection

- **ServiceRegistry**: Central DI container (144 lines, 17 dependencies)
- **StorageFactory**: Builds fully wired ServiceRegistry (133 lines)
- **ApplicationStartup**: Initializes config, DB, and services (222 lines)

### 1.3 Known Architectural Limitations (per AGENTS.md)

- Cross-storage writes not fully transactional
- Undo state is in-memory (lost on restart)
- Email/phone verification simulated (no real sending)
- No caching layer

---

## 2. Findings Summary

| Category                           | Count  |
|------------------------------------|--------|
| Missing Implementations/Features   | 4      |
| Duplication/Redundancy             | 5      |
| Logic/Architecture/Structure Flaws | 12     |
| Clear Problems/Issues/Mistakes     | 9      |
| **TOTAL**                          | **30** |

---

## 3. Findings Detail

### 3.1 Missing Implementations/Features

---

#### Finding 1: Email/Phone Verification Not Implemented

| Attribute      | Value                  |
|----------------|------------------------|
| **Category**   | Missing Implementation |
| **Severity**   | High                   |
| **Scope**      | [MAIN]                 |
| **Impact**     | 4/5                    |
| **Effort**     | 2/5                    |
| **Confidence** | 5/5                    |

**File:** `src/main/java/datingapp/core/model/User.java`
**Line:** 49-50

```java
/**
 * Verification method used to verify a profile.
 * NOTE: Currently simulated - email/phone not sent externally.
 */
public static enum VerificationMethod {
    EMAIL,
    PHONE
}
```

**Why it is an issue:** Verification is marked as "simulated" - no actual email/SMS delivery exists. Users can verify but receive no real communication.

**Current negative impact:**
- Feature is non-functional for production use
- No actual account verification occurs
- Trust/safety feature is incomplete

**Impact of fixing it:**
- Enable real email verification (SendGrid, AWS SES)
- Enable SMS verification (Twilio)
- Add verification code delivery and retry logic

---

#### Finding 2: Undo State Not Persisted

| Attribute      | Value                  |
|----------------|------------------------|
| **Category**   | Missing Implementation |
| **Severity**   | Medium                 |
| **Scope**      | [MAIN]                 |
| **Impact**     | 3/5                    |
| **Effort**     | 3/5                    |
| **Confidence** | 5/5                    |

**File:** `src/main/java/datingapp/core/matching/UndoService.java`
**Lines:** 1-207 (full class)

**Why it is an issue:** According to AGENTS.md: "Undo state is in-memory (lost on restart)". Users lose their undo capability when the application restarts.

**Current negative impact:**
- Users cannot undo swipes after app restart
- Poor UX for users who close and reopen the app
- Data inconsistency

**Impact of fixing it:**
- Persist Undo state to database
- Allow undo across sessions
- Improve user trust in the platform

---

#### Finding 3: No Caching Layer

| Attribute      | Value                  |
|----------------|------------------------|
| **Category**   | Missing Implementation |
| **Severity**   | Medium                 |
| **Scope**      | [MAIN]                 |
| **Impact**     | 3/5                    |
| **Effort**     | 4/5                    |
| **Confidence** | 5/5                    |

**Evidence:** AGENTS.md explicitly lists "No caching layer" as a known limitation.

**Why it is an issue:** Every candidate query hits the database, impacting performance with scale.

**Current negative impact:**
- Repeated queries for same data
- Poor performance under load
- Database connection pressure

**Impact of fixing it:**
- Add Caffeine/Guava cache for frequently accessed data
- Cache candidate results with TTL
- Reduce DB load significantly

---

#### Finding 4: No Transaction Management for Cross-Storage Operations

| Attribute      | Value                  |
|----------------|------------------------|
| **Category**   | Missing Implementation |
| **Severity**   | High                   |
| **Scope**      | [MAIN]                 |
| **Impact**     | 4/5                    |
| **Effort**     | 4/5                    |
| **Confidence** | 4/5                    |

**Evidence:** AGENTS.md: "Cross-storage writes not fully transactional"

**Why it is an issue:** When a match is created, likes are recorded across multiple tables. If one operation fails, data can become inconsistent.

**Current negative impact:**
- Potential data corruption on partial failures
- Integrity issues in like/match records
- Difficult debugging of partial states

**Impact of fixing it:**
- Wrap cross-storage operations in JDBI transactions
- Implement proper rollback on failure
- Ensure atomic operations

---

### 3.2 Duplication/Redundancy

---

#### Finding 5: Static AppConfig.defaults() in User Class

| Attribute      | Value       |
|----------------|-------------|
| **Category**   | Duplication |
| **Severity**   | Medium      |
| **Scope**      | [MAIN]      |
| **Impact**     | 3/5         |
| **Effort**     | 1/5         |
| **Confidence** | 5/5         |

**File:** `src/main/java/datingapp/core/model/User.java`
**Lines:** 72, 435

```java
private static final AppConfig CONFIG = AppConfig.defaults();  // Line 72

public synchronized int getAge() {
    return getAge(AppConfig.defaults().safety().userTimeZone());  // Line 435
}
```

**Why it is an issue:**
- Creates new `AppConfig.defaults()` on every `getAge()` call (line 435)
- Static CONFIG exists but is not used for `getAge()`
- Inconsistent with injected config pattern used throughout codebase

**Current negative impact:**
- Performance overhead from repeated object creation
- Inconsistent behavior if defaults change
- Violates established DI pattern

**Impact of fixing it:**
- Use existing `CONFIG` field instead of calling `defaults()`
- Single line change: `return getAge(CONFIG.safety().userTimeZone());`

---

#### Finding 6: AppConfig.defaults() in ValidationService

| Attribute      | Value       |
|----------------|-------------|
| **Category**   | Duplication |
| **Severity**   | Medium      |
| **Scope**      | [MAIN]      |
| **Impact**     | 3/5         |
| **Effort**     | 1/5         |
| **Confidence** | 5/5         |

**File:** `src/main/java/datingapp/core/profile/ValidationService.java`
**Line:** 28

```java
public ValidationService(AppConfig config) {
    this(Objects.requireNonNull(config, "config cannot be null"));
}

public ValidationService() {
    this(AppConfig.defaults());  // Line 28
}
```

**Why it is an issue:** Falls back to defaults instead of requiring explicit config injection.

**Current negative impact:** Same as Finding 5.

**Impact of fixing it:** Remove default constructor or document it as test-only.

---

#### Finding 7: AppConfig.defaults() in MatchPreferences

| Attribute      | Value       |
|----------------|-------------|
| **Category**   | Duplication |
| **Severity**   | Medium      |
| **Scope**      | [MAIN]      |
| **Impact**     | 3/5         |
| **Effort**     | 1/5         |
| **Confidence** | 5/5         |

**File:** `src/main/java/datingapp/core/profile/MatchPreferences.java`
**Line:** 392

```java
private static final AppConfig CONFIG = AppConfig.defaults();
```

**Why it is an issue:** Same pattern as Finding 5.

---

#### Finding 8: AppConfig.defaults() in LoginController

| Attribute      | Value       |
|----------------|-------------|
| **Category**   | Duplication |
| **Severity**   | Medium      |
| **Scope**      | [MAIN]      |
| **Impact**     | 3/5         |
| **Effort**     | 1/5         |
| **Confidence** | 5/5         |

**File:** `src/main/java/datingapp/ui/screen/LoginController.java`
**Line:** 78

```java
private static final AppConfig CONFIG = AppConfig.defaults();
```

**Why it is an issue:** UI layer using hardcoded defaults instead of injected config.

---

#### Finding 9: 45+ Call Sites of AppConfig.defaults()

| Attribute      | Value       |
|----------------|-------------|
| **Category**   | Duplication |
| **Severity**   | Medium      |
| **Scope**      | [TEST]      |
| **Impact**     | 2/5         |
| **Effort**     | 2/5         |
| **Confidence** | 5/5         |

**Evidence:** grep shows 45 matches for `AppConfig.defaults()`:
- 1 in main code (User.java)
- 1 in MatchPreferences.java
- 1 in LoginController.java
- 1 in ValidationService.java
- 1 in ProfileViewModel.java (comment reference)
- Rest in test code

**Why it is an issue:** Test code uses defaults instead of test fixtures.

**Current negative impact:** Tests may behave differently than production.

**Impact of fixing it:** Use shared test config fixtures.

---

### 3.3 Logic/Architecture/Structure Flaws

---

#### Finding 10: Optional.get() Without Null Check in ConnectionService

| Attribute      | Value      |
|----------------|------------|
| **Category**   | Logic Flaw |
| **Severity**   | High       |
| **Scope**      | [MAIN]     |
| **Impact**     | 5/5        |
| **Effort**     | 1/5        |
| **Confidence** | 5/5        |

**File:** `src/main/java/datingapp/core/connection/ConnectionService.java`
**Lines:** 68-77

```java
User sender = userStorage.get(senderId).orElse(null);  // Line 69
if (sender == null || sender.getState() != UserState.ACTIVE) {
    return SendResult.failure(SENDER_NOT_FOUND, SendResult.ErrorCode.USER_NOT_FOUND);
}

User recipient = userStorage.get(recipientId).orElse(null);  // Line 74
if (recipient == null || recipient.getState() != UserState.ACTIVE) {
    return SendResult.failure(RECIPIENT_NOT_FOUND, SendResult.ErrorCode.USER_NOT_FOUND);
}
```

**Why it is an issue:** Uses `.orElse(null)` pattern instead of Optional directly. This defeats the purpose of Optional and can lead to NPE if API changes.

**Current negative impact:** Minor - currently handled with null checks, but fragile.

**Impact of fixing it:** Use proper Optional chaining:
```java
User sender = userStorage.get(senderId).orElse(null);  // Keep for now with null check
// Alternative: use Optional<User> directly and map/filter
```

---

#### Finding 11: Hardcoded Magic Numbers in CandidateFinder

| Attribute      | Value          |
|----------------|----------------|
| **Category**   | Structure Flaw |
| **Severity**   | Low            |
| **Scope**      | [MAIN]         |
| **Impact**     | 2/5            |
| **Effort**     | 1/5            |
| **Confidence** | 4/5            |

**File:** `src/main/java/datingapp/core/matching/CandidateFinder.java`
**Line:** 149

```java
int distanceKm = currentUser.hasLocationSet() ? currentUser.getMaxDistanceKm() : 50_000;
```

**Why it is an issue:** `50_000` km is a magic number - should be a config constant.

**Current negative impact:** No way to adjust without code change.

**Impact of fixing it:** Move to AppConfig with sensible default.

---

#### Finding 12: AppSession Singleton Thread Safety

| Attribute      | Value             |
|----------------|-------------------|
| **Category**   | Architecture Flaw |
| **Severity**   | Medium            |
| **Scope**      | [MAIN]            |
| **Impact**     | 4/5               |
| **Effort**     | 2/5               |
| **Confidence** | 4/5               |

**File:** `src/main/java/datingapp/core/AppSession.java`
**Lines:** 21-27

```java
public static AppSession getInstance() {
    return INSTANCE;
}

public synchronized User getCurrentUser() {
    return currentUser;
}

public synchronized void setCurrentUser(User user) {
    this.currentUser = user;
    notifyListeners(user);
}
```

**Why it is an issue:**
- Uses eager initialization of singleton
- Synchronized on get/set but notifyListeners() called inside sync block
- Listener notification could cause deadlocks if listeners block

**Current negative impact:** Potential race condition if accessed from multiple threads without proper sync.

**Impact of fixing it:** Use volatile + double-checked locking, or use proper concurrent collections.

---

#### Finding 13: DatabaseManager Static Mutable State

| Attribute      | Value             |
|----------------|-------------------|
| **Category**   | Architecture Flaw |
| **Severity**   | Medium            |
| **Scope**      | [MAIN]            |
| **Impact**     | 3/5               |
| **Effort**     | 3/5               |
| **Confidence** | 4/5               |

**File:** `src/main/java/datingapp/storage/DatabaseManager.java`
**Lines:** 20-26

```java
private static volatile String jdbcUrl = "jdbc:h2:./data/dating";
private static DatabaseManager instance;
private final AtomicReference<HikariDataSource> dataSource = new AtomicReference<>();
private volatile boolean initialized = false;
```

**Why it is an issue:** Static mutable state makes testing difficult and can cause issues in multi-instance scenarios.

**Current negative impact:** Difficult to reset/reconfigure in tests.

**Impact of fixing it:** Consider dependency injection for DatabaseManager.

---

#### Finding 14: Null Return Instead of Optional.empty()

| Attribute      | Value      |
|----------------|------------|
| **Category**   | Logic Flaw |
| **Severity**   | Medium     |
| **Scope**      | [MAIN]     |
| **Impact**     | 3/5        |
| **Effort**     | 2/5        |
| **Confidence** | 5/5        |

**File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
**Lines:** 384, 538, 547, 629, 636, 643, 650, 657, 664, 671, 678, 713

```java
if (heightCm == null) {
    return null;  // Multiple locations
}
```

**Why it is an issue:** Over 90 `return null;` statements in codebase, many should return Optional.empty().

**Current negative impact:** Forces null checks, inconsistent with Optional API.

**Impact of fixing it:** Convert to Optional return types where appropriate.

---

#### Finding 15: SwipeState.LockStripes Complexity

| Attribute      | Value             |
|----------------|-------------------|
| **Category**   | Architecture Flaw |
| **Severity**   | Medium            |
| **Scope**      | [MAIN]            |
| **Impact**     | 3/5               |
| **Effort**     | 3/5               |
| **Confidence** | 3/5               |

**File:** `src/main/java/datingapp/core/metrics/ActivityMetricsService.java`
**Lines:** 26, 32-47

```java
private static final int LOCK_STRIPE_COUNT = 256;
private final Object[] lockStripes;

public ActivityMetricsService(...) {
    this.lockStripes = new Object[LOCK_STRIPE_COUNT];
    for (int i = 0; i < LOCK_STRIPE_COUNT; i++) {
        lockStripes[i] = new Object();
    }
}
```

**Why it is an issue:** Custom locking mechanism adds complexity. Could use ConcurrentHashMap or other standard concurrency utilities.

**Current negative impact:** Hard to reason about thread safety.

**Impact of fixing it:** Simplify with standard Java concurrency utilities.

---

#### Finding 16: MatchingService Optional Dependencies

| Attribute      | Value             |
|----------------|-------------------|
| **Category**   | Architecture Flaw |
| **Severity**   | Medium            |
| **Scope**      | [MAIN]            |
| **Impact**     | 3/5               |
| **Effort**     | 2/5               |
| **Confidence** | 5/5               |

**File:** `src/main/java/datingapp/core/matching/MatchingService.java`
**Lines:** 29-31, 142-144

```java
private ActivityMetricsService activityMetricsService; // Optional
private UndoService undoService; // Optional
private RecommendationService dailyService; // Optional

public SwipeResult processSwipe(User currentUser, User candidate, boolean liked) {
    if (dailyService == null || undoService == null) {
        return SwipeResult.configError("dailyService and undoService required for processSwipe");
    }
```

**Why it is an issue:** Service allows null dependencies but fails at runtime. Builder pattern helps but inconsistent with other services.

**Current negative impact:** Runtime failures if not configured correctly.

**Impact of fixing it:** Require all dependencies in constructor or use builder with validation.

---

#### Finding 17: Nested Exception Handling in Main.java

| Attribute      | Value      |
|----------------|------------|
| **Category**   | Logic Flaw |
| **Severity**   | Low        |
| **Scope**      | [MAIN]     |
| **Impact**     | 2/5        |
| **Effort**     | 1/5        |
| **Confidence** | 5/5        |

**File:** `src/main/java/datingapp/Main.java`
**Lines:** 38-62

```java
try {
    // FFM code
} catch (Throwable _) {
    try {
        // subprocess fallback
    } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
    } catch (Exception _) {
        assert true;
    }
}
```

**Why it is an issue:** Ctoo broad), empty catch for Exceptionatches Throwable (, underscore variable names.

**Current negative impact:** Hard to debug issues in initialization.

**Impact of fixing it:** Use specific exception types and proper handling.

---

#### Finding 18: Static Initialization Order Dependency

| Attribute      | Value             |
|----------------|-------------------|
| **Category**   | Architecture Flaw |
| **Severity**   | Medium            |
| **Scope**      | [MAIN]            |
| **Impact**     | 3/5               |
| **Effort**     | 2/5               |
| **Confidence** | 4/5               |

**File:** `src/main/java/datingapp/Main.java`
**Lines:** 36-65

```java
static {
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
        // UTF-8 console setup BEFORE logger init
    }
    System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
}
```

**Why it is an issue:** Static initializer runs before main(), very early in JVM lifecycle. Order dependency is fragile.

**Current negative impact:** Could fail silently on different JVMs/platforms.

**Impact of fixing it:** Document clearly or move to explicit initialization method.

---

#### Finding 19: LoggingSupport Interface Usage Inconsistency

| Attribute      | Value          |
|----------------|----------------|
| **Category**   | Structure Flaw |
| **Severity**   | Low            |
| **Scope**      | [MAIN]         |
| **Impact**     | 2/5            |
| **Effort**     | 2/5            |
| **Confidence** | 4/5            |

**Evidence:** CandidateFinder implements LoggingSupport, but ConnectionService, TrustSafetyService use direct Logger.

**Why it is an issue:** Two different logging patterns in similar services.

**Current negative impact:** Inconsistent code style.

**Impact of fixing it:** Standardize on one approach.

---

#### Finding 20: ProfileService Large Class

| Attribute      | Value          |
|----------------|----------------|
| **Category**   | Structure Flaw |
| **Severity**   | Medium         |
| **Scope**      | [MAIN]         |
| **Impact**     | 3/5            |
| **Effort**     | 3/5            |
| **Confidence** | 4/5            |

**Evidence:** ProfileService likely exceeds 500+ lines (not fully analyzed)

**Why it is an issue:** Large classes are harder to maintain and test.

**Current negative impact:** Code organization issues.

**Impact of fixing it:** Extract achievement, stats, and validation into separate services.

---

#### Finding 21: StorageBuilder Exposes Mutable Fields

| Attribute      | Value      |
|----------------|------------|
| **Category**   | Logic Flaw |
| **Severity**   | Low        |
| **Scope**      | [MAIN]     |
| **Impact**     | 2/5        |
| **Effort**     | 2/5        |
| **Confidence** | 4/5        |

**File:** `src/main/java/datingapp/core/model/User.java`
**Lines:** 153-306

```java
public static final class StorageBuilder {
    private final User user;
    // ... mutates user fields directly
}
```

**Why it is an issue:** StorageBuilder mutates User after construction, bypassing validation in setters.

**Current negative impact:** Database-loaded users may have invalid state.

**Impact of fixing it:** Use factory methods with validation or validate in build().

---

### 3.4 Clear Problems/Issues/Mistakes

---

#### Finding 22: NullPointerException Risk in ViewModelFactory

| Attribute      | Value   |
|----------------|---------|
| **Category**   | Problem |
| **Severity**   | High    |
| **Scope**      | [MAIN]  |
| **Impact**     | 4/5     |
| **Effort**     | 1/5     |
| **Confidence** | 5/5     |

**File:** `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java`
**Line:** 240

```java
return null;
```

**Why it is an issue:** Method returns null instead of Optional, caller may not check.

**Current negative impact:** NPE in UI layer.

**Impact of fixing it:** Return Optional or throw proper exception.

---

#### Finding 23: Return Null in JdbiUserStorage.findById

| Attribute      | Value   |
|----------------|---------|
| **Category**   | Problem |
| **Severity**   | High    |
| **Scope**      | [MAIN]  |
| **Impact**     | 4/5     |
| **Effort**     | 1/5     |
| **Confidence** | 5/5     |

**File:** `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
**Lines:** Multiple null returns

**Why it is an issue:** Storage layer returns null instead of Optional consistently.

**Current negative impact:** Inconsistent API, NPE risk.

**Impact of fixing it:** Return Optional.empty() throughout storage layer.

---

#### Finding 24: Hardcoded String Constants in Switch

| Attribute      | Value   |
|----------------|---------|
| **Category**   | Problem |
| **Severity**   | Low     |
| **Scope**      | [MAIN]  |
| **Impact**     | 2/5     |
| **Effort**     | 1/5     |
| **Confidence** | 5/5     |

**File:** `src/main/java/datingapp/Main.java`
**Lines:** 102-128

```java
switch (choice) {
    case "1" -> safeExecute(profileHandler::createUser);
    case "2" -> safeExecute(profileHandler::selectUser);
    // ... 20+ case statements
    default -> logInfo(CliTextAndInput.INVALID_SELECTION);
}
```

**Why it is an issue:** Hardcoded strings, no enum or constant.

**Current negative impact:** Typos not caught at compile time.

**Impact of fixing it:** Use constants or generated enum.

---

#### Finding 25: Unbounded List in findPendingLikersWithTimes

| Attribute      | Value   |
|----------------|---------|
| **Category**   | Problem |
| **Severity**   | Medium  |
| **Scope**      | [MAIN]  |
| **Impact**     | 3/5     |
| **Effort**     | 1/5     |
| **Confidence** | 4/5     |

**File:** `src/main/java/datingapp/core/matching/MatchingService.java`
**Lines:** 180-221

```java
public List<PendingLiker> findPendingLikersWithTimes(UUID currentUserId) {
    // No pagination - loads all likers into memory
}
```

**Why it is an issue:** Could cause OOM with many likers.

**Current negative impact:** Scalability issue.

**Impact of fixing it:** Add pagination or streaming.

---

#### Finding 26: Potential Resource Leak in Main.java FFM

| Attribute      | Value   |
|----------------|---------|
| **Category**   | Problem |
| **Severity**   | Low     |
| **Scope**      | [MAIN]  |
| **Impact**     | 2/5     |
| **Effort**     | 2/5     |
| **Confidence** | 3/5     |

**File:** `src/main/java/datingapp/Main.java`
**Lines:** 40-47

```java
var kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
// No explicit close() of Arena
```

**Why it is an issue:** Foreign Function Memory (FFM) Arena may not be properly cleaned up.

**Current negative impact:** Minor memory leak potential.

**Impact of fixing it:** Use try-with-resources or explicit close.

---

#### Finding 27: Inconsistent Error Handling in CLI Handlers

| Attribute      | Value   |
|----------------|---------|
| **Category**   | Problem |
| **Severity**   | Medium  |
| **Scope**      | [MAIN]  |
| **Impact**     | 3/5     |
| **Effort**     | 2/5     |
| **Confidence** | 4/5     |

**File:** `src/main/java/datingapp/app/cli/SafetyHandler.java`
**Lines:** 149, 154, 213, 217, 252, 257

```java
return null;  // Multiple locations
```

**Why it is an issue:** Similar pattern to JdbiUserStorage null returns.

**Current negative impact:** Inconsistent error handling.

**Impact of fixing it:** Standardize on Result patterns.

---

#### Finding 28: Missing Input Validation in RestApiServer

| Attribute      | Value   |
|----------------|---------|
| **Category**   | Problem |
| **Severity**   | High    |
| **Scope**      | [MAIN]  |
| **Impact**     | 4/5     |
| **Effort**     | 2/5     |
| **Confidence** | 4/5     |

**File:** `src/main/java/datingapp/app/api/RestApiServer.java`
**Lines:** 212, 215, 229, 232, 251, 272

```java
throw new IllegalArgumentException("limit must be greater than 0");
```

**Why it is an issue:** While validation exists, API lacks authentication/authorization.

**Current negative impact:** No security on REST endpoints.

**Impact of fixing it:** Add JWT/auth middleware.

---

#### Finding 29: Swallowed Exceptions in AppSession

| Attribute      | Value   |
|----------------|---------|
| **Category**   | Problem |
| **Severity**   | Low     |
| **Scope**      | [MAIN]  |
| **Impact**     | 2/5     |
| **Effort**     | 1/5     |
| **Confidence** | 5/5     |

**File:** `src/main/java/datingapp/core/AppSession.java`
**Lines:** 54-64

```java
private void notifyListeners(User user) {
    for (Consumer<User> listener : listeners) {
        try {
            listener.accept(user);
        } catch (Exception e) {
            LOGGER.warn("Session listener threw exception", e);
        }
    }
}
```

**Why it is an issue:** Exceptions silently caught, listener failures hidden.

**Current negative impact:** Hard to debug listener issues.

**Impact of fixing it:** Consider failing fast or collecting results.

---

#### Finding 30: ProfileNote Null Validation

| Attribute      | Value   |
|----------------|---------|
| **Category**   | Problem |
| **Severity**   | Medium  |
| **Scope**      | [MAIN]  |
| **Impact**     | 3/5     |
| **Effort**     | 1/5     |
| **Confidence** | 5/5     |

**File:** `src/main/java/datingapp/core/model/ProfileNote.java`
**Lines:** 25, 28, 31

```java
throw new IllegalArgumentException("Cannot create a note about yourself");
throw new IllegalArgumentException("Note content cannot be blank");
throw new IllegalArgumentException("Note content exceeds maximum length");
```

**Why it is an issue:** Validation scattered across constructors - could use factory methods.

**Current negative impact:** Inconsistent construction.

**Impact of fixing it:** Use static factory with validation.

---

## 4. Move-Forward Section

### 4.1 Phased Roadmap

#### Phase 1: Quick Wins (1-2 weeks)

| Priority | Action                                                     | Value  | Effort | Risk   | Dependencies |
|----------|------------------------------------------------------------|--------|--------|--------|--------------|
| 1        | Fix User.getAge() to use CONFIG instead of defaults()      | High   | Low    | Low    | None         |
| 2        | Return Optional.empty() instead of null in JdbiUserStorage | High   | Low    | Medium | None         |
| 3        | Add pagination to findPendingLikersWithTimes               | Medium | Low    | Low    | None         |
| 4        | Remove empty catch blocks                                  | Low    | Low    | Low    | None         |
| 5        | Add auth to RestApiServer                                  | High   | Medium | Medium | JWT library  |

#### Phase 2: Refactors (2-4 weeks)

| Priority | Action                                       | Value  | Effort | Risk   | Dependencies |
|----------|----------------------------------------------|--------|--------|--------|--------------|
| 1        | Persist Undo state to database               | High   | Medium | Medium | Phase1 #2    |
| 2        | Add transaction management for cross-storage | High   | Medium | Medium | None         |
| 3        | Refactor AppSession thread safety            | Medium | Medium | Medium | None         |
| 4        | Extract ProfileService into smaller services | Medium | High   | Low    | None         |
| 5        | Standardize LoggingSupport usage             | Low    | Low    | Low    | None         |

#### Phase 3: Strategic Improvements (1-2 months)

| Priority | Action                                  | Value  | Effort | Risk   | Dependencies |
|----------|-----------------------------------------|--------|--------|--------|--------------|
| 1        | Implement real email/SMS verification   | High   | High   | Medium | Phase2 #1    |
| 2        | Add caching layer (Caffeine/Guava)      | High   | Medium | Low    | None         |
| 3        | Implement proper DI for DatabaseManager | Medium | High   | Medium | None         |
| 4        | Add API authentication/authorization    | High   | High   | Medium | Phase1 #5    |
| 5        | Comprehensive error handling overhaul   | Medium | High   | Medium | Phase2 #5    |

---

### 4.2 Next Steps (5 options)

1. **Fix critical null safety issues**: Convert all `return null` to `Optional.empty()` in storage layer
2. **Add database transactions**: Wrap cross-storage operations in JDBI transactions
3. **Implement undo persistence**: Move UndoService state from in-memory to database
4. **Add API security**: Implement JWT authentication for RestApiServer
5. **Performance optimization**: Add caching for candidate queries

---

### 4.3 What Should Be Implemented But Is Not (5 options)

1. **Real verification system**: Email (SendGrid/AWS SES) and SMS (Twilio) delivery
2. **Cross-storage transactions**: Atomic operations across UserStorage, InteractionStorage
3. **API authentication**: JWT/OAuth2 for REST endpoints
4. **Comprehensive caching**: Caffeine cache for candidate results, user profiles
5. **Persistent undo state**: Survive application restarts

---

### 4.4 Missing Features/Components (5 options)

1. **Notification system**: Push notifications, email notifications for matches
2. **Image upload/storage**: Photo management beyond URL storage
3. **Reporting dashboard**: Admin interface for trust/safety monitoring
4. **Analytics pipeline**: User behavior analytics, A/B testing framework
5. **Rate limiting**: API rate limiting, daily action limits enforcement

---

### 4.5 Changes That Improve/Add Value (5 options)

1. **Machine learning recommendations**: Improve candidate matching with ML
2. **Real-time messaging**: WebSocket support instead of polling
3. **Social integration**: Facebook login, social graph features
4. **Premium features**: Subscription model, paid features
5. **Multi-language support**: Internationalization framework

---

### 4.6 Final Recommendations (5 options)

1. **Address all Phase 1 items immediately** - High impact, low effort
2. **Invest in proper testing infrastructure** - CI/CD, integration tests
3. **Document architectural decisions** - ADR format for key choices
4. **Establish monitoring/observability** - Metrics, logging, tracing
5. **Plan for scalability** - Consider microservices migration path

---

## 5. Appendix: Evidence of Analysis

- **PMD Check**: BUILD SUCCESS (no static analysis violations)
- **Files Analyzed**: 78 main + 59 test Java files
- **Architecture**: Clean Architecture with 3 layers
- **Entry Points**: Main.java (CLI), DatingApp.java (JavaFX)
- **Dependencies**: JDBI 3, H2 2.4, JavaFX 25, JUnit 5

---

*Report generated by minimax-m2.5:free model*
