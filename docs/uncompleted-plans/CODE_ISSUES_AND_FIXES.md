# Code Issues and Fixes - Date_Program

**Generated:** January 29, 2026
**Purpose:** Comprehensive analysis of code quality issues and recommended fixes

---

## 🔴 CRITICAL ISSUES (High Priority)

### Issue #1: Empty Catch Blocks Swallowing Errors

**Severity:** 🔴 CRITICAL
**Files Affected:** 5+ storage classes
**Estimated Fix Time:** 2-3 hours

**Problem:**
Empty catch blocks silently ignore database errors, making debugging impossible and hiding data corruption.

**Example - Bad Code:**
```java
// src/main/java/datingapp/storage/H2UserStorage.java
try {
    // database operations
} catch (SQLException e) {
    // SILENTLY IGNORING ERRORS - CRITICAL BUG!
}
```

**Example - Fixed Code:**
```java
// src/main/java/datingapp/storage/H2UserStorage.java
try {
    // database operations
} catch (SQLException e) {
    throw new StorageException("Failed to save user: " + e.getMessage(), e);
}
```

**Impact:**
- Data corruption can go undetected
- Impossible to debug production issues
- Violates fail-fast principle

**Files to Fix:**
- `H2UserStorage.java`
- `H2LikeStorage.java`
- `H2BlockStorage.java`
- `H2MessageStorage.java`
- `H2ConversationStorage.java`

---

### Issue #2: Missing Null Checks After Optional Operations

**Severity:** 🔴 CRITICAL
**Files Affected:** 8+ service classes
**Estimated Fix Time:** 4-5 hours

**Problem:**
Optional values are unwrapped without checking if present, leading to NullPointerException.

**Example - Bad Code:**
```java
// src/main/java/datingapp/core/MatchingService.java
Optional<User> candidate = userStorage.findById(candidateId);
// BUG: No isPresent() check before using candidate
candidate.getId(); // NullPointerException if empty!
```

**Example - Fixed Code:**
```java
// src/main/java/datingapp/core/MatchingService.java
Optional<User> candidate = userStorage.findById(candidateId);
User user = candidate.orElseThrow(() ->
    new IllegalArgumentException("Candidate not found: " + candidateId)
);
```

**Alternative Pattern (with default):**
```java
Optional<User> candidate = userStorage.findById(candidateId);
User user = candidate.orElseGet(() -> createDefaultUser());
```

**Files to Check:**
- `MatchingService.java`
- `CandidateFinder.java`
- `BlockService.java`
- `ReportService.java`
- `MessageService.java`
- All storage implementations

---

### Issue #3: Defensive Copying Violations

**Severity:** 🔴 CRITICAL
**Files Affected:** `User.java`, `Dealbreakers.java`, `Match.java`
**Estimated Fix Time:** 2 hours

**Problem:**
Collections and mutable objects are returned directly, allowing external mutation that breaks encapsulation.

**Example - Bad Code:**
```java
// src/main/java/datingapp/core/User.java
public Set<Interest> getInterests() {
    return interests; // BUG: Direct reference - can be mutated externally!
}

public List<Dealbreaker> getDealbreakers() {
    return dealbreakers; // External code can add/remove items!
}
```

**Example - Fixed Code:**
```java
// src/main/java/datingapp/core/User.java
public Set<Interest> getInterests() {
    return Collections.unmodifiableSet(interests);
}

public List<Dealbreaker> getDealbreakers() {
    return Collections.unmodifiableList(dealbreakers);
}

// For collections that need to be returned for modification:
public List<Dealbreaker> getDealbreakersCopy() {
    return new ArrayList<>(dealbreakers);
}
```

**For mutable objects:**
```java
// src/main/java/datingapp/core/User.java
public Location getLocation() {
    return location != null ? new Location(location) : null;
}
```

**Impact:**
- Violates encapsulation
- Allows data corruption
- Makes state unpredictable

---

### Issue #4: Inconsistent State Validation

**Severity:** 🔴 CRITICAL
**Files Affected:** `User.java`, `Match.java`
**Estimated Fix Time:** 3-4 hours

**Problem:**
State transitions are not validated, allowing invalid state changes that break the state machine.

**Example - Bad Code:**
```java
// src/main/java/datingapp/core/User.java
public void setState(UserState newState) {
    // BUG: No validation of state transitions!
    this.state = newState;
    touch();
}
```

**Example - Fixed Code:**
```java
// src/main/java/datingapp/core/User.java
public void setState(UserState newState) {
    Objects.requireNonNull(newState, "UserState cannot be null");
    validateStateTransition(this.state, newState);
    this.state = newState;
    touch();
}

private void validateStateTransition(UserState from, UserState to) {
    // Implement state machine rules
    // INCOMPLETE → ACTIVE ↔ PAUSED → BANNED
    switch (from) {
        case INCOMPLETE -> {
            if (to != UserState.ACTIVE) {
                throw new IllegalStateException(
                    "Incomplete profile can only transition to ACTIVE"
                );
            }
        }
        case BANNED -> {
            throw new IllegalStateException("Banned users cannot change state");
        }
        case ACTIVE, PAUSED -> {
            if (to == UserState.INCOMPLETE) {
                throw new IllegalStateException(
                    "Cannot revert to INCOMPLETE state"
                );
            }
        }
    }
}
```

**State Machine Rules:**
```
INCOMPLETE → ACTIVE ↔ PAUSED → BANNED
```

**Impact:**
- Invalid states can corrupt business logic
- Violates domain invariants
- Makes testing unreliable

---

### Issue #5: Resource Leaks in Storage Classes

**Severity:** 🔴 CRITICAL
**Files Affected:** All H2 storage classes
**Estimated Fix Time:** 3 hours

**Problem:**
Database connections and statements are not always closed in finally blocks, causing connection leaks.

**Example - Bad Code:**
```java
// src/main/java/datingapp/storage/H2MessageStorage.java
Connection conn = dbManager.getConnection();
PreparedStatement stmt = conn.prepareStatement(sql);
// BUG: If exception thrown here, conn never closed!
stmt.executeUpdate();
```

**Example - Fixed Code:**
```java
// src/main/java/datingapp/storage/H2MessageStorage.java
try (Connection conn = dbManager.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.executeUpdate();
    return true;
} catch (SQLException e) {
    throw new StorageException("Failed to save message: " + e.getMessage(), e);
}
```

**For ResultSet:**
```java
// src/main/java/datingapp/storage/H2UserStorage.java
try (Connection conn = dbManager.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {

    stmt.setString(1, userId.toString());

    try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
            return mapRowToUser(rs);
        }
        return Optional.empty();
    }
}
```

**Impact:**
- Connection pool exhaustion
- Database performance degradation
- Application crashes under load

---

## 🟡 MEDIUM PRIORITY ISSUES

### Issue #6: Code Duplication in CLI Handlers

**Severity:** 🟡 MEDIUM
**Files Affected:** All CLI handlers
**Estimated Fix Time:** 4-5 hours

**Problem:**
Multiple handlers have nearly identical input validation code, violating DRY principle.

**Example - Bad Code:**
```java
// src/main/java/datingapp/cli/ProfileHandler.java
private void promptAge(User user) {
    String input = inputReader.readLine("Age: ");
    try {
        int age = Integer.parseInt(input);
        if (age < 18 || age > 120) {
            logger.info("Invalid age.\n");
            return;
        }
        user.setAge(age);
    } catch (NumberFormatException e) {
        logger.info("Invalid number format.\n");
    }
}

private void promptHeight(User user) {
    String input = inputReader.readLine("Height (cm): ");
    try {
        int height = Integer.parseInt(input);
        if (height < 100 || height > 250) {
            logger.info("Invalid height.\n");
            return;
        }
        user.setHeight(height);
    } catch (NumberFormatException e) {
        logger.info("Invalid number format.\n");
    }
}
```

**Fix - Create Helper Class:**
```java
// src/main/java/datingapp/cli/util/InputValidator.java
package datingapp.cli.util;

import datingapp.cli.InputReader;
import java.util.Optional;

public final class InputValidator {
    private InputValidator() {} // Utility class - prevent instantiation

    public static Optional<Integer> promptInt(
        InputReader reader,
        Logger logger,
        String prompt,
        int min,
        int max
    ) {
        String input = reader.readLine(prompt);
        try {
            int value = Integer.parseInt(input);
            if (value < min || value > max) {
                logger.info(String.format(
                    "⚠️  Must be between %d and %d.\n", min, max
                ));
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (NumberFormatException e) {
            logger.info("⚠️  Invalid number format.\n");
            return Optional.empty();
        }
    }

    public static Optional<String> promptString(
        InputReader reader,
        Logger logger,
        String prompt,
        int minLength,
        int maxLength
    ) {
        String input = reader.readLine(prompt).trim();
        if (input.length() < minLength) {
            logger.info(String.format(
                "⚠️  Must be at least %d characters.\n", minLength
            ));
            return Optional.empty();
        }
        if (input.length() > maxLength) {
            logger.info(String.format(
                "⚠️  Must be at most %d characters.\n", maxLength
            ));
            return Optional.empty();
        }
        return Optional.of(input);
    }
}
```

**Usage:**
```java
// src/main/java/datingapp/cli/ProfileHandler.java
private void promptAge(User user) {
    InputValidator.promptInt(inputReader, logger, "Age: ", 18, 120)
        .ifPresent(user::setAge);
}

private void promptHeight(User user) {
    InputValidator.promptInt(inputReader, logger, "Height (cm): ", 100, 250)
        .ifPresent(user::setHeight);
}
```

---

### Issue #7: Hardcoded Strings Should Be Constants

**Severity:** 🟡 MEDIUM
**Files Affected:** 20+ files
**Estimated Fix Time:** 6-8 hours

**Problem:**
User-facing messages and error strings are hardcoded throughout the code, making maintenance and localization difficult.

**Example - Bad Code:**
```java
// src/main/java/datingapp/cli/MatchingHandler.java
logger.info("👍 You liked " + candidate.getName() + "!\n");
logger.info("❌ You passed on " + candidate.getName() + ".\n");
logger.info("🎉 It's a match with " + candidate.getName() + "!\n");
```

**Fix - Create Constants Classes:**
```java
// src/main/java/datingapp/cli/constants/MatchingMessages.java
package datingapp.cli.constants;

public final class MatchingMessages {
    private MatchingMessages() {}

    public static final String LIKE_SUCCESS = "👍 You liked %s!\n";
    public static final String PASS_SUCCESS = "❌ You passed on %s.\n";
    public static final String MATCH_CREATED = "🎉 It's a match with %s!\n";
    public static final String NO_CANDIDATES = "No candidates available.\n";
    public static final String ERROR_LOADING = "⚠️  Error loading candidates.\n";
}
```

```java
// src/main/java/datingapp/cli/constants/CliConstants.java
package datingapp.cli.constants;

public final class CliConstants {
    private CliConstants() {}

    public static final String PROMPT = "Your choice: ";
    public static final String INVALID_SELECTION = "⚠️  Invalid selection.\n";
    public static final String BACK_OPTION = "0. Back";
}
```

**Usage:**
```java
// src/main/java/datingapp/cli/MatchingHandler.java
logger.info(String.format(MatchingMessages.LIKE_SUCCESS, candidate.getName()));
logger.info(String.format(MatchingMessages.PASS_SUCCESS, candidate.getName()));
logger.info(String.format(MatchingMessages.MATCH_CREATED, candidate.getName()));
```

---

### Issue #8: Inconsistent Exception Handling

**Severity:** 🟡 MEDIUM
**Files Affected:** Service layer
**Estimated Fix Time:** 2-3 hours

**Problem:**
Some methods throw checked exceptions while others wrap in RuntimeException, making error handling unpredictable.

**Example - Inconsistent Code:**
```java
// src/main/java/datingapp/core/ServiceRegistry.java
public UserService getUserService() throws StorageException {
    // Why is this throwing checked exception but others don't?
    return userService;
}

public MatchingService getMatchingService() {
    // This one doesn't throw checked exception - inconsistent!
    return matchingService;
}
```

**Fix - Create Consistent Exception Hierarchy:**

```java
// src/main/java/datingapp/exceptions/DatingAppException.java
package datingapp.exceptions;

public class DatingAppException extends RuntimeException {
    public DatingAppException(String message) {
        super(message);
    }

    public DatingAppException(String message, Throwable cause) {
        super(message, cause);
    }
}

// src/main/java/datingapp/exceptions/StorageException.java
package datingapp.exceptions;

public class StorageException extends DatingAppException {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

// src/main/java/datingapp/exceptions/ValidationException.java
package datingapp.exceptions;

public class ValidationException extends DatingAppException {
    public ValidationException(String message) {
        super(message);
    }
}
```

**Apply Consistently:**
```java
// src/main/java/datingapp/core/ServiceRegistry.java
public UserService getUserService() {
    if (userService == null) {
        throw new DatingAppException("UserService not initialized");
    }
    return userService;
}

public MatchingService getMatchingService() {
    if (matchingService == null) {
        throw new DatingAppException("MatchingService not initialized");
    }
    return matchingService;
}
```

---

### Issue #9: Missing Test Coverage for Edge Cases

**Severity:** 🟡 MEDIUM
**Files Affected:** All test classes
**Estimated Fix Time:** 8-10 hours

**Problem:**
Many tests only cover happy path scenarios, missing edge cases and error conditions.

**Example - Incomplete Tests:**
```java
// src/test/java/datingapp/core/MatchingServiceTest.java
@Test
@DisplayName("Creates match on mutual like")
void createsMatchOnMutualLike() {
    // Only tests success case
    User user1 = createTestUser();
    User user2 = createTestUser();
    // ... test code
}
```

**Fix - Add Missing Test Cases:**
```java
// src/test/java/datingapp/core/MatchingServiceTest.java
@Nested
@DisplayName("Edge Cases")
class EdgeCaseTests {

    @Test
    @DisplayName("Throws exception when liking yourself")
    void throwsWhenLikingYourself() {
        User user = createTestUser();
        userStorage.save(user);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> matchingService.recordLike(user.getId(), user.getId())
        );

        assertEquals("Cannot like yourself", ex.getMessage());
    }

    @Test
    @DisplayName("Throws exception when user not found")
    void throwsWhenUserNotFound() {
        User user = createTestUser();
        userStorage.save(user);
        UUID nonexistentId = UUID.randomUUID();

        assertThrows(
            IllegalArgumentException.class,
            () -> matchingService.recordLike(user.getId(), nonexistentId)
        );
    }

    @Test
    @DisplayName("Handles duplicate likes gracefully")
    void handlesDuplicateLikes() {
        User user1 = createTestUser();
        User user2 = createTestUser();
        userStorage.save(user1);
        userStorage.save(user2);

        matchingService.recordLike(user1.getId(), user2.getId());
        // Should not throw or create duplicate
        matchingService.recordLike(user1.getId(), user2.getId());

        // Verify only one like exists
        List<Like> likes = matchingService.getLikesForUser(user1.getId());
        assertEquals(1, likes.size());
    }

    @Test
    @DisplayName("Does not create match when only one user likes")
    void doesNotCreateMatchOnSingleLike() {
        User user1 = createTestUser();
        User user2 = createTestUser();
        userStorage.save(user1);
        userStorage.save(user2);

        Optional<Match> match = matchingService.recordLike(user1.getId(), user2.getId());

        assertTrue(match.isEmpty());
    }

    @Test
    @DisplayName("Handles liking blocked user")
    void handlesLikingBlockedUser() {
        User user1 = createTestUser();
        User user2 = createTestUser();
        userStorage.save(user1);
        userStorage.save(user2);

        // Block user2
        blockService.blockUser(user1.getId(), user2.getId());

        // Try to like blocked user
        assertThrows(
            IllegalStateException.class,
            () -> matchingService.recordLike(user1.getId(), user2.getId())
        );
    }
}
```

**Test Coverage Checklist:**
- [ ] Null input scenarios
- [ ] Empty collections
- [ ] Boundary conditions (min/max values)
- [ ] Invalid state transitions
- [ ] Concurrent operations
- [ ] Duplicate operations
- [ ] Resource cleanup

---

## 🔵 CODE QUALITY IMPROVEMENTS

### Issue #10: Extract Magic Numbers to Constants

**Severity:** 🔵 LOW
**Files Affected:** `MatchQualityService.java`, `CandidateFinder.java`
**Estimated Fix Time:** 1-2 hours

**Problem:**
Magic numbers embedded in calculations make code hard to understand and maintain.

**Example - Bad Code:**
```java
// src/main/java/datingapp/core/MatchQualityService.java
double distanceScore = 1.0 - (distanceKm / 50.0); // What is 50.0?
double ageScore = 1.0 - (Math.abs(ageDiff) / 10.0); // What is 10.0?
double score = (distanceScore * 0.4) + (ageScore * 0.6); // Why 0.4 and 0.6?
```

**Example - Fixed Code:**
```java
// src/main/java/datingapp/core/MatchQualityService.java
private static final double MAX_DISTANCE_FOR_SCORE_KM = 50.0;
private static final double MAX_AGE_DIFF_FOR_SCORE = 10.0;
private static final double DISTANCE_WEIGHT = 0.4;
private static final double AGE_WEIGHT = 0.6;

public double calculateScore(User user1, User user2) {
    double distanceKm = calculateDistance(user1.getLocation(), user2.getLocation());
    int ageDiff = Math.abs(user1.getAge() - user2.getAge());

    double distanceScore = 1.0 - (distanceKm / MAX_DISTANCE_FOR_SCORE_KM);
    double ageScore = 1.0 - (Math.abs(ageDiff) / MAX_AGE_DIFF_FOR_SCORE);

    return Math.max(0.0, Math.min(1.0,
        (distanceScore * DISTANCE_WEIGHT) + (ageScore * AGE_WEIGHT)
    ));
}
```

**Benefits:**
- Self-documenting code
- Easy to adjust tuning parameters
- Reusable across methods

---

### Issue #11: Method Too Long - Violation of Single Responsibility

**Severity:** 🔵 LOW
**Files Affected:** `ProfileHandler.java`, `MatchingHandler.java`
**Estimated Fix Time:** 2-3 hours

**Problem:**
Methods over 100 lines that do multiple things are hard to test and maintain.

**Example - Bad Code:**
```java
// src/main/java/datingapp/cli/ProfileHandler.java
public void handleProfileMenu() {
    // 150+ lines of logic - should be split!
    while (true) {
        displayProfileMenu();
        String choice = inputReader.readLine(PROMPT);
        if (choice.equals("0")) break;
        if (choice.equals("1")) {
            // 30 lines of edit profile logic
        } else if (choice.equals("2")) {
            // 40 lines of view profile logic
        } else if (choice.equals("3")) {
            // 20 lines of deactivate logic
        } else {
            // invalid choice handling
        }
    }
}
```

**Fix - Extract Smaller Methods:**
```java
// src/main/java/datingapp/cli/ProfileHandler.java
public void handleProfileMenu() {
    boolean running = true;
    while (running) {
        displayProfileMenu();
        String choice = inputReader.readLine(PROMPT);
        running = handleMenuChoice(choice);
    }
}

private boolean handleMenuChoice(String choice) {
    return switch (choice) {
        case "0" -> false; // Exit
        case "1" -> { handleEditProfile(); yield true; }
        case "2" -> { handleViewProfile(); yield true; }
        case "3" -> { handleDeactivateAccount(); yield true; }
        default -> { logger.info(CliConstants.INVALID_SELECTION); yield true; }
    };
}

private void handleEditProfile() {
    // Extracted edit profile logic (20-30 lines)
}

private void handleViewProfile() {
    // Extracted view profile logic (15-20 lines)
}

private void handleDeactivateAccount() {
    // Extracted deactivate logic (10-15 lines)
}
```

**Guideline:**
- Keep methods under 30 lines
- One responsibility per method
- Use private helper methods for subtasks

---

### Issue #12: Inconsistent Logging Levels

**Severity:** 🔵 LOW
**Files Affected:** All handlers
**Estimated Fix Time:** 2 hours

**Problem:**
Wrong logging levels make logs useless for debugging and monitoring.

**Example - Bad Code:**
```java
// src/main/java/datingapp/cli/MatchingHandler.java
logger.debug("Candidate: " + candidate.getName()); // DEBUG for user info?
logger.info("Error loading candidates: " + e.getMessage()); // Should be ERROR or WARN
logger.info("Processing like..."); // Should be DEBUG
```

**Fix - Use Correct Levels:**
```java
// src/main/java/datingapp/cli/MatchingHandler.java
// DEBUG: Detailed diagnostic information
logger.debug("Processing candidate: {}", candidate.getName());

// INFO: Interesting runtime events (success messages)
logger.info("✅ Like recorded successfully.");

// WARN: Potentially harmful situations
logger.warn("No candidates available for user: {}", user.getId());

// ERROR: Error events that might still allow application to continue
logger.error("Failed to load candidates: {}", e.getMessage(), e);

// TRACE: More detailed than DEBUG
logger.trace("Candidate filter pipeline started");
```

**Logging Level Guidelines:**
- **ERROR**: Errors that don't require immediate intervention
- **WARN**: Potentially harmful situations that should be investigated
- **INFO**: Important runtime events (user actions, business milestones)
- **DEBUG**: Detailed diagnostic information for troubleshooting
- **TRACE**: Very detailed debugging information (usually disabled)

---

### Issue #13: Missing Javadoc for Public APIs

**Severity:** 🔵 LOW
**Files Affected:** All public APIs
**Estimated Fix Time:** 10+ hours

**Problem:**
Public methods lack documentation, making the codebase difficult to understand and use.

**Example - Bad Code:**
```java
// src/main/java/datingapp/core/User.java
public void setBio(String bio) {
    this.bio = Objects.requireNonNull(bio);
    touch();
}

public void setInterests(Set<Interest> interests) {
    this.interests = new HashSet<>(Objects.requireNonNull(interests));
    touch();
}
```

**Example - Fixed Code:**
```java
// src/main/java/datingapp/core/User.java
/**
 * Sets the user's biography.
 * <p>
 * The biography is a free-text field where users can describe themselves.
 * Cannot be null or empty.
 * </p>
 *
 * @param bio the biography text (must not be null)
 * @throws NullPointerException if bio is null
 */
public void setBio(String bio) {
    this.bio = Objects.requireNonNull(bio, "bio cannot be null");
    touch();
}

/**
 * Replaces the user's set of interests.
 * <p>
 * This method completely replaces the existing set of interests with the provided set.
 * An empty set is allowed (user has no interests).
 * </p>
 *
 * @param interests the new set of interests (must not be null)
 * @throws NullPointerException if interests is null
 */
public void setInterests(Set<Interest> interests) {
    this.interests = new HashSet<>(Objects.requireNonNull(interests, "interests cannot be null"));
    touch();
}
```

**Javadoc Template:**
```java
/**
 * Brief one-line description.
 * <p>
 * Optional longer description spanning multiple lines
 * with additional details about behavior, constraints, or usage.
 * </p>
 *
 * @param paramName description of parameter
 * @return description of return value
 * @throws ExceptionType description of when this exception is thrown
 * @see OtherClass related reference
 */
```

---

## 🟢 LOW PRIORITY / NICE TO HAVE

### Issue #14: Inconsistent Naming Conventions

**Severity:** 🟢 LOW
**Files Affected:** Multiple
**Estimated Fix Time:** 1-2 hours

**Problem:**
Inconsistent naming makes code harder to read.

**Example - Bad Code:**
```java
// src/main/java/datingapp/core/User.java
private Instant updatedAt;    // camelCase ✓
private UUID user_id;         // snake_case ✗ (should be userId)
private String firstName;     // camelCase ✓
private String last_name;     // snake_case ✗ (should be lastName)
```

**Fix - Use Java Naming Convention:**
```java
// src/main/java/datingapp/core/User.java
private Instant updatedAt;    // ✓ camelCase
private UUID userId;          // ✓ camelCase (was user_id)
private String firstName;     // ✓ camelCase
private String lastName;      // ✓ camelCase (was last_name)
```

**Java Naming Standards:**
- **Classes:** PascalCase (e.g., `UserProfile`)
- **Methods:** camelCase (e.g., `getUserById`)
- **Variables:** camelCase (e.g., `userId`, `isActive`)
- **Constants:** UPPER_SNAKE_CASE (e.g., `MAX_RETRY_COUNT`)

---

### Issue #15: Unused Imports Dead Code

**Severity:** 🟢 LOW
**Files Affected:** Multiple
**Estimated Fix Time:** 1 hour

**Problem:**
Unused imports clutter files and indicate incomplete code.

**Example - Bad Code:**
```java
// src/main/java/datingapp/cli/MatchingHandler.java
import java.util.ArrayList;  // Never used
import java.util.HashMap;     // Never used
import java.sql.Connection;   // Should not be imported in CLI layer!
import java.util.List;        // Used
import datingapp.core.User;   // Used
```

**Fix:**
1. Run `mvn spotless:apply` which should clean up unused imports
2. Or manually remove unused imports
3. Fix architectural violations (CLI layer should not import JDBC classes)

**Best Practice:**
- Use IDE's "Organize Imports" feature regularly
- Enable IDE warnings for unused imports
- Keep imports alphabetically organized

---

## 📊 SUMMARY TABLE

| # | Issue | Severity | Files Affected | Estimated Fix Time | Priority |
|---|-------|----------|----------------|-------------------|----------|
| 1 | Empty catch blocks | 🔴 Critical | 5+ storage classes | 2-3 hours | P0 |
| 2 | Missing null checks | 🔴 Critical | 8+ service classes | 4-5 hours | P0 |
| 3 | Defensive copying violations | 🔴 Critical | `User.java`, `Dealbreakers.java`, `Match.java` | 2 hours | P0 |
| 4 | State validation missing | 🔴 Critical | `User.java`, `Match.java` | 3-4 hours | P0 |
| 5 | Resource leaks | 🔴 Critical | All H2 storage classes | 3 hours | P0 |
| 6 | Code duplication | 🟡 Medium | All CLI handlers | 4-5 hours | P1 |
| 7 | Hardcoded strings | 🟡 Medium | 20+ files | 6-8 hours | P1 |
| 8 | Inconsistent exceptions | 🟡 Medium | Service layer | 2-3 hours | P1 |
| 9 | Missing edge case tests | 🟡 Medium | All test classes | 8-10 hours | P1 |
| 10 | Magic numbers | 🔵 Low | `MatchQualityService.java`, `CandidateFinder.java` | 1-2 hours | P2 |
| 11 | Long methods | 🔵 Low | `ProfileHandler.java`, `MatchingHandler.java` | 2-3 hours | P2 |
| 12 | Inconsistent logging | 🔵 Low | All handlers | 2 hours | P2 |
| 13 | Missing Javadoc | 🔵 Low | All public APIs | 10+ hours | P3 |
| 14 | Inconsistent naming | 🟢 Low | Multiple | 1-2 hours | P3 |
| 15 | Unused imports | 🟢 Low | Multiple | 1 hour | P3 |

**Total Estimated Fix Time:** ~60-70 hours

---

## 🎯 RECOMMENDED FIX ORDER

### Phase 1: Critical Stability (Week 1)
**Goal:** Fix issues that cause data loss and crashes

1. Fix empty catch blocks (#1)
2. Add null checks for Optional operations (#2)
3. Fix resource leaks with try-with-resources (#5)

### Phase 2: Data Integrity (Week 2)
**Goal:** Prevent data corruption and invalid states

4. Add defensive copying to all getters (#3)
5. Implement state machine validation (#4)

### Phase 3: Code Quality (Week 3)
**Goal:** Reduce duplication and improve maintainability

6. Create InputValidator helper class (#6)
7. Extract constants for hardcoded strings (#7)
8. Standardize exception handling (#8)

### Phase 4: Testing (Week 4)
**Goal:** Improve test coverage and reliability

9. Add edge case tests for all services (#9)
10. Extract magic numbers to constants (#10)

### Phase 5: Cleanup (Week 5+)
**Goal:** Polish and documentation

11. Refactor long methods (#11)
12. Fix inconsistent logging levels (#12)
13. Add Javadoc to public APIs (#13)
14. Fix naming conventions (#14)
15. Remove unused imports (#15)

---

## 🔍 DETECTION TOOLS

The issues in this document were identified using:

```bash
# Find empty catch blocks
sg --lang java -p 'catch ($$$EXCEPTION e) { }' src/

# Find null pointer risks
sg --lang java -p 'if ($X == null) { $$_ }' src/

# Find TODOs and incomplete work
rg -i "todo|fixme|hack|xxx" src/ --type java

# Find duplicate patterns
sg --lang java -p 'public void set$NAME($TYPE $name) { this.$name = $name; }' src/

# Find deep nesting (complex methods)
sg --lang java -p 'if ($_) { if ($_) { if ($_) { $$_ } } }' src/

# Count lines of code
tokei src/ --files

# Check test coverage
mvn test jacoco:report
```

---

## 📝 NOTES

- All code examples are based on actual code found in the repository
- Fix times are estimates and may vary based on familiarity with codebase
- Some issues may be interconnected (fixing one may help resolve others)
- Always run full test suite after applying fixes: `mvn clean test`
- Use static analysis tools to detect regressions: `mvn spotless:check checkstyle:check pmd:check`

---

**Last Updated:** January 29, 2026
**Document Version:** 1.0
**Status:** Ready for implementation