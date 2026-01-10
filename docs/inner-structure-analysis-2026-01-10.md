# Inner Structure Analysis & Refactoring Plan

**Date**: 2026-01-10
**Phase**: 1.5
**Focus**: Internal code organization, method structure, class responsibilities
**Total Files Analyzed**: 62 source files
**Critical Issues Found**: 4 files requiring immediate refactoring

---

## Executive Summary

This analysis examines the **internal organization** of source code files, focusing on method structure, responsibilities, code duplication, and organizational patterns within classes. While the previous analysis (source-code-analysis-2026-01-10.md) confirmed correct architectural placement, this document identifies opportunities for improving **inner structure**.

**Key Findings**:
- ✅ **Methods are well-named** following conventions
- ✅ **Services are properly stateless** with injected dependencies
- ⚠️ **4 files exceed 400 lines** and need restructuring
- ❌ **75 lines of code duplication** in ProfileHandler
- ⚠️ **Display logic mixed with business logic** in handlers
- ⚠️ **Serialization helpers** should be extracted to utilities

**Impact**: Improvements will enhance maintainability, reduce duplication, and make code easier to test.

---

## File Size Distribution Analysis

### Size Categories

```
CRITICAL (400+ lines):
├── ProfileHandler.java         597 lines  ❗ Highest priority
├── MatchingHandler.java        436 lines  ❗ High priority
└── User.java                   401 lines  ⚠️  Acceptable (domain model)

LARGE (250-400 lines):
├── H2UserStorage.java          300 lines  ⚠️  Needs extraction
├── MatchQualityService.java    274 lines  ✅ Acceptable
└── DatabaseManager.java        268 lines  ✅ Fine (infrastructure)

MEDIUM (150-250 lines):
├── H2LikeStorage.java          230 lines  ✅ Consistent
├── H2SwipeSessionStorage.java  212 lines  ✅ Consistent
├── SwipeSession.java           201 lines  ✅ Good
├── AchievementService.java     197 lines  ✅ Well-organized
└── 10+ more files...

SMALL (<150 lines):
└── 40+ files                             ✅ Ideal size
```

**Observation**: CLI handlers vary wildly (56 to 597 lines), indicating inconsistent responsibility distribution.

---

## Detailed File Analysis

### 1. ProfileHandler.java (597 lines) ❗ CRITICAL

**Current Structure**:
```
Lines 1-48:    Imports, fields, constructor
Lines 50-82:   completeProfile() - orchestration
Lines 84-146:  previewProfile() - display
Lines 148-162: setDealbreakers() - orchestration
Lines 164-594: Private helpers (430 lines!)
```

**Critical Issues**:

1. **Code Duplication** (75 lines):
   ```java
   // Six nearly identical methods (lines 519-594)
   private void copyExceptSmoking(...)   // 12 lines
   private void copyExceptDrinking(...)  // 12 lines
   private void copyExceptKids(...)      // 12 lines
   private void copyExceptLookingFor(...) // 12 lines
   private void copyExceptHeight(...)    // 12 lines
   private void copyExceptAge(...)       // 12 lines
   ```
   **Problem**: Nearly identical code repeated 6 times
   **Impact**: 75 lines of duplication, hard to maintain

2. **Six Similar Edit Methods** (lines 441-517):
   ```java
   editSmokingDealbreaker()      // 21 lines
   editDrinkingDealbreaker()     // 21 lines
   editKidsDealbreaker()         // 23 lines
   editLookingForDealbreaker()   // 25 lines
   editHeightDealbreaker()       // 24 lines
   editAgeDealbreaker()          // 19 lines
   ```
   **Problem**: Repetitive structure, could be parameterized
   **Impact**: 133 lines of similar code

**Refactoring Recommendations**:

**Option A**: Extract DealbreakersEditor helper class
```java
// NEW: src/main/java/datingapp/cli/DealbreakersEditor.java
public class DealbreakersEditor {
  private final InputReader inputReader;

  public Dealbreakers editSmoking(Dealbreakers current) {
    // Single method handles all the logic
    return Dealbreakers.builder()
        .mergeFrom(current)  // NEW method on Dealbreakers.Builder
        .acceptSmoking(promptSmokingChoices())
        .build();
  }

  // Similar for other dealbreaker types
}
```

**Option B**: Add mergeFrom() to Dealbreakers.Builder
```java
// MODIFY: src/main/java/datingapp/core/Dealbreakers.java
public static class Builder {
  public Builder mergeFrom(Dealbreakers other) {
    // Copy all non-null fields from other
    this.smoking.addAll(other.acceptableSmoking());
    this.drinking.addAll(other.acceptableDrinking());
    // ... etc
    return this;
  }
}
```

**Estimated Impact**: Reduce ProfileHandler from 597 → **350 lines** (-247 lines, -41%)

---

### 2. MatchingHandler.java (436 lines) ❗ HIGH PRIORITY

**Current Structure**:
```
Lines 1-73:    Imports, fields, constructor
Lines 75-134:  browseCandidates() - main flow
Lines 136-184: processCandidateInteraction() - business logic
Lines 186-256: viewMatches() - display + interaction
Lines 258-303: viewMatchDetails() - nested interaction
Lines 305-371: displayMatchQuality() - pure display
Lines 373-392: displayScoreBreakdown() - pure display
Lines 394-428: handleMatchDetailAction() - business logic
Lines 430-488: Various helper methods
```

**Critical Issues**:

1. **Mixed Responsibilities**:
   - Display logic (rendering) in same class as business logic
   - Lines 305-392: Pure display methods (~87 lines)
   - Could be extracted to ProfileDisplayer utility

2. **Long Methods**:
   ```java
   viewMatches()              → 70 lines  // Orchestration + display
   viewMatchDetails()         → 46 lines  // Logic + display
   displayMatchQuality()      → 67 lines  // Pure display
   ```

3. **Deep Nesting**:
   ```java
   viewMatches() {
     if (...) {
       for (...) {
         if (...) {  // 3 levels deep
   ```

**Refactoring Recommendations**:

**Extract Display Logic**:
```java
// NEW: src/main/java/datingapp/cli/MatchDisplayFormatter.java
public class MatchDisplayFormatter {

  public static void displayQuality(User user, MatchQuality quality) {
    // All formatting logic from lines 305-392
    // Returns formatted string, no logger calls
  }

  public static String formatScoreBreakdown(MatchQuality quality) {
    // Extract lines 373-392
  }
}
```

**Simplify MatchingHandler**:
```java
// REFACTORED: MatchingHandler.java
private void viewMatchDetails(List<Match> matches, User currentUser) {
  // Simplified - uses MatchDisplayFormatter
  Match match = getSelectedMatch(matches);  // Extract selection logic
  String display = MatchDisplayFormatter.displayQuality(otherUser, quality);
  logger.info(display);
  handleUserAction();  // Simplified action handling
}
```

**Estimated Impact**: Reduce MatchingHandler from 436 → **320 lines** (-116 lines, -27%)

---

### 3. User.java (401 lines) ⚠️ ACCEPTABLE

**Current Structure**:
```
Lines 1-13:    Package, imports
Lines 14-63:   Enums, fields
Lines 65-85:   Constructors
Lines 87-138:  fromDatabase() factory
Lines 140-208: Getters (basic + calculated)
Lines 210-338: Setters (with touch())
Lines 340-370: State transitions
Lines 372-391: Helper methods
Lines 393-401: equals, hashCode, toString
```

**Assessment**: ✅ **Well-Organized**
- Clear sections with consistent patterns
- Long due to comprehensive domain model (24 fields)
- Setters are verbose but consistent (each calls touch())

**Minor Improvements**:

1. **Add Section Comments**:
   ```java
   // ========================================
   // SECTION: Constructors & Factory Methods
   // ========================================

   // ========================================
   // SECTION: Getters
   // ========================================

   // ========================================
   // SECTION: Setters (with timestamp update)
   // ========================================

   // ========================================
   // SECTION: State Machine Transitions
   // ========================================
   ```

2. **Group Related Getters/Setters**:
   - Core fields (name, bio, birthDate, gender)
   - Preferences (interestedIn, maxDistanceKm, minAge, maxAge)
   - Lifestyle (smoking, drinking, wantsKids, lookingFor, education, heightCm)
   - Dealbreakers
   - Interests

**Estimated Impact**: Minimal changes, add 20 lines of comments for clarity

---

### 4. H2UserStorage.java (300 lines) ⚠️ NEEDS EXTRACTION

**Current Structure**:
```
Lines 1-30:    Imports, class declaration
Lines 32-106:  save() - SQL + serialization (104 lines!)
Lines 108-127: get() - SQL + deserialization
Lines 129-148: findActive(), findAll()
Lines 150-158: findByQuery() - helper
Lines 160-248: mapUser() - complex deserialization (88 lines!)
Lines 250-266: mapDealbreakers() - helper
Lines 268-300: Serialization helpers (8 methods)
```

**Critical Issues**:

1. **save() Method Too Long** (104 lines):
   - 30 parameters to bind
   - Serialization logic inline
   - Should delegate to helper methods

2. **mapUser() Too Long** (88 lines):
   - Deserialization logic inline
   - Multiple conditional blocks
   - Should use mapper helper

3. **Serialization Helpers Should Be External**:
   ```java
   // Lines 268-300: These should be in utility class
   private <E extends Enum<E>> Set<E> parseEnumSet(...)
   private String serializeEnumSet(...)
   private String gendersToString(...)
   private Set<User.Gender> stringToGenders(...)
   private String urlsToString(...)
   private List<String> stringToUrls(...)
   private Set<Interest> parseInterests(...)
   private String serializeInterests(...)
   ```

**Refactoring Recommendations**:

**Extract Serialization Utility**:
```java
// NEW: src/main/java/datingapp/storage/SerializationUtils.java
public final class SerializationUtils {
  private SerializationUtils() {}  // Utility class

  public static <E extends Enum<E>> Set<E> parseEnumSet(String csv, Class<E> clazz) {
    // Implementation from H2UserStorage line 268
  }

  public static String serializeEnumSet(Set<? extends Enum<?>> values) {
    // Implementation from H2UserStorage line 285
  }

  public static Set<Interest> parseInterests(String csv) {
    // Implementation from H2UserStorage line 312
  }

  // ... other serialization methods
}
```

**Extract UserMapper**:
```java
// NEW: src/main/java/datingapp/storage/UserMapper.java
public class UserMapper {

  public static User fromResultSet(ResultSet rs) throws SQLException {
    // Extract mapUser() logic (lines 160-248)
    // Use SerializationUtils for parsing
  }

  public static void bindUserParameters(PreparedStatement stmt, User user)
      throws SQLException {
    // Extract parameter binding from save() (lines 44-92)
  }
}
```

**Simplified H2UserStorage**:
```java
public class H2UserStorage implements UserStorage {
  @Override
  public void save(User user) {
    String sql = "...";
    try (Connection conn = dbManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
      UserMapper.bindUserParameters(stmt, user);  // Delegated
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new StorageException("Failed to save user: " + user.getId(), e);
    }
  }

  @Override
  public User get(UUID id) {
    // ... SQL query
    return UserMapper.fromResultSet(rs);  // Delegated
  }
}
```

**Estimated Impact**:
- Reduce H2UserStorage from 300 → **120 lines** (-180 lines, -60%)
- Create SerializationUtils: ~80 lines
- Create UserMapper: ~150 lines
- **Net**: Same functionality, better organization

---

### 5. Main.java (152 lines) ✅ MOSTLY GOOD

**Current Structure**:
```
Lines 1-27:    Imports, class declaration, fields
Lines 29-67:   main() - entry point (38 lines)
Lines 69-122:  initializeApp() - dependency wiring (53 lines)
Lines 124-163: printMenu() - UI display (39 lines)
Lines 165-169: shutdown() - cleanup (4 lines)
```

**Minor Issues**:

1. **printMenu() is Long** (39 lines):
   - Includes session info logic
   - Could extract session display to helper

2. **initializeApp() is Acceptable**:
   - Dependency wiring naturally verbose
   - Could group related components

**Minor Refactoring**:

```java
// Extract session display logic
private static void displaySessionInfo(User currentUser) {
  SessionService sessionService = services.getSessionService();
  sessionService.getCurrentSession(currentUser.getId())
      .ifPresent(session -> logger.info(...));

  DailyLimitService dailyLimitService = services.getDailyLimitService();
  DailyLimitService.DailyStatus status = dailyLimitService.getStatus(currentUser.getId());
  // ... display logic
}

private static void printMenu() {
  logger.info(CliConstants.SEPARATOR_LINE);
  // ... menu header

  if (userSession.getCurrentUser() != null) {
    displaySessionInfo(userSession.getCurrentUser());  // Delegated
  }

  // ... menu options
}
```

**Estimated Impact**: Reduce Main.java from 152 → **130 lines** (-22 lines, -14%)

---

## Method Organization Patterns

### ✅ Good Patterns Found

1. **Constructor Injection** (all services):
   ```java
   public MatchingService(LikeStorage likeStorage, MatchStorage matchStorage) {
     this.likeStorage = Objects.requireNonNull(likeStorage);
     this.matchStorage = Objects.requireNonNull(matchStorage);
   }
   ```

2. **Factory Methods** (domain models):
   ```java
   public static User fromDatabase(...)  // User.java
   public static Match create(...)       // Match.java
   public static Like create(...)        // Like.java
   ```

3. **Touch Pattern** (timestamp updates):
   ```java
   public void setName(String name) {
     this.name = Objects.requireNonNull(name);
     touch();  // Updates timestamp
   }
   ```

4. **Switch Expressions** (routing):
   ```java
   return switch (achievement) {
     case FIRST_SPARK -> getMatchCount(userId) >= 1;
     case SOCIAL_BUTTERFLY -> getMatchCount(userId) >= 5;
     // ...
   };
   ```

5. **Validation in Constructors**:
   ```java
   public Block {  // Compact constructor
     Objects.requireNonNull(id, "id cannot be null");
     if (blockerId.equals(blockedId)) {
       throw new IllegalArgumentException("Cannot block yourself");
     }
   }
   ```

### ⚠️ Anti-Patterns Found

1. **God Methods** (>50 lines):
   - `save()` in H2UserStorage: 104 lines
   - `mapUser()` in H2UserStorage: 88 lines
   - `displayMatchQuality()` in MatchingHandler: 67 lines
   - `viewMatches()` in MatchingHandler: 70 lines

2. **Code Duplication**:
   - 6 `copyExcept*()` methods in ProfileHandler (75 lines total)
   - Similar edit methods in ProfileHandler (133 lines)

3. **Mixed Responsibilities**:
   - Display + business logic in handlers
   - Serialization + SQL in storage classes

4. **Deep Nesting** (>3 levels):
   - Found in `viewMatches()` - MatchingHandler
   - Found in `processCandidateInteraction()` - MatchingHandler

---

## Code Smell Identification

### Priority 1: Critical Smells (Fix First)

| File | Issue | Lines | Impact |
|------|-------|-------|--------|
| ProfileHandler.java | Code duplication (copyExcept* methods) | 75 | HIGH - hard to maintain |
| ProfileHandler.java | Similar edit methods | 133 | MEDIUM - repetitive |
| H2UserStorage.java | God method (save) | 104 | HIGH - hard to test |
| H2UserStorage.java | God method (mapUser) | 88 | HIGH - hard to understand |
| MatchingHandler.java | Mixed responsibilities | 87 | MEDIUM - tight coupling |

### Priority 2: Moderate Smells (Improve Soon)

| File | Issue | Lines | Impact |
|------|-------|-------|--------|
| MatchingHandler.java | Long methods (viewMatches) | 70 | MEDIUM |
| MatchingHandler.java | Deep nesting | N/A | LOW - reduces readability |
| Main.java | Long printMenu() | 39 | LOW - cosmetic |
| User.java | Missing section comments | N/A | LOW - harder to navigate |

### Priority 3: Minor Smells (Nice to Have)

| File | Issue | Lines | Impact |
|------|-------|-------|--------|
| Various | Magic numbers in display logic | N/A | LOW |
| Various | Inconsistent String formatting | N/A | LOW |

---

## Specific Refactoring Recommendations

### Recommendation 1: Extract DealbreakersEditor ⭐ HIGH PRIORITY

**Problem**: 208 lines of duplication in ProfileHandler
**Solution**: Extract helper class

**Implementation**:

```java
// NEW FILE: src/main/java/datingapp/cli/DealbreakersEditor.java
package datingapp.cli;

import datingapp.core.Dealbreakers;
import datingapp.core.Lifestyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for editing dealbreakers via CLI.
 * Extracted from ProfileHandler to reduce duplication.
 */
public class DealbreakersEditor {
  private static final Logger logger = LoggerFactory.getLogger(DealbreakersEditor.class);
  private final InputReader inputReader;

  public DealbreakersEditor(InputReader inputReader) {
    this.inputReader = inputReader;
  }

  public Dealbreakers editSmoking(Dealbreakers current) {
    logger.info("\nAcceptable smoking (comma-separated, e.g., 1,2):");
    logger.info("  1=Never, 2=Sometimes, 3=Regularly, 0=Clear");
    String input = inputReader.readLine("Choices: ");

    Dealbreakers.Builder builder = Dealbreakers.builder().mergeFrom(current);
    if (!input.equals("0")) {
      // Parse and add smoking preferences
      Set<Lifestyle.Smoking> choices = parseSmokingChoices(input);
      builder.acceptSmoking(choices.toArray(Lifestyle.Smoking[]::new));
    }
    return builder.build();
  }

  // Similar methods for drinking, kids, lookingFor, height, age
  // BUT each is focused and uses common parsing logic

  private Set<Lifestyle.Smoking> parseSmokingChoices(String input) {
    Set<Lifestyle.Smoking> result = EnumSet.noneOf(Lifestyle.Smoking.class);
    for (String s : input.split(",")) {
      switch (s.trim()) {
        case "1" -> result.add(Lifestyle.Smoking.NEVER);
        case "2" -> result.add(Lifestyle.Smoking.SOMETIMES);
        case "3" -> result.add(Lifestyle.Smoking.REGULARLY);
      }
    }
    return result;
  }
}
```

**Benefit**:
- Eliminates 75 lines of duplication
- Improves testability (can unit test editor separately)
- Reduces ProfileHandler complexity

---

### Recommendation 2: Add mergeFrom() to Dealbreakers.Builder ⭐ HIGH PRIORITY

**Problem**: No way to preserve existing dealbreakers when editing one field
**Solution**: Add mergeFrom() method to Builder

**Implementation**:

```java
// MODIFY: src/main/java/datingapp/core/Dealbreakers.java
public static class Builder {
  // ... existing fields

  /**
   * Merges all non-empty dealbreakers from another Dealbreakers object.
   * This allows incremental editing of dealbreakers.
   *
   * @param other the dealbreakers to merge from
   * @return this builder for chaining
   */
  public Builder mergeFrom(Dealbreakers other) {
    if (other == null) return this;

    if (other.hasSmokingDealbreaker()) {
      this.smoking.addAll(other.acceptableSmoking());
    }
    if (other.hasDrinkingDealbreaker()) {
      this.drinking.addAll(other.acceptableDrinking());
    }
    if (other.hasKidsDealbreaker()) {
      this.kids.addAll(other.acceptableKidsStance());
    }
    if (other.hasLookingForDealbreaker()) {
      this.lookingFor.addAll(other.acceptableLookingFor());
    }
    if (other.hasEducationDealbreaker()) {
      this.education.addAll(other.acceptableEducation());
    }
    if (other.hasHeightDealbreaker()) {
      this.minHeightCm = other.minHeightCm();
      this.maxHeightCm = other.maxHeightCm();
    }
    if (other.hasAgeDealbreaker()) {
      this.maxAgeDifference = other.maxAgeDifference();
    }

    return this;
  }
}
```

**Benefit**:
- Eliminates need for 6 copyExcept* methods
- Single point of merge logic
- Easier to maintain

---

### Recommendation 3: Extract SerializationUtils ⭐ MEDIUM PRIORITY

**Problem**: Serialization logic duplicated/inline in storage classes
**Solution**: Create shared utility class

**Implementation**: See section 4 (H2UserStorage analysis) above

**Benefits**:
- Reusable across all H2*Storage classes
- Single place to fix serialization bugs
- Easier to test serialization logic independently

---

### Recommendation 4: Extract MatchDisplayFormatter ⭐ MEDIUM PRIORITY

**Problem**: Display logic mixed with business logic in MatchingHandler
**Solution**: Extract formatting to utility class

**Implementation**: See section 2 (MatchingHandler analysis) above

**Benefits**:
- Separation of concerns
- Display logic can be tested independently
- Handler focuses on orchestration

---

### Recommendation 5: Add Section Comments to Large Classes ⭐ LOW PRIORITY

**Problem**: Hard to navigate 400+ line files
**Solution**: Add clear section markers

**Implementation**:

```java
// src/main/java/datingapp/core/User.java
public class User {

  // ============================================================
  // SECTION: Nested Enums
  // ============================================================

  public enum Gender { ... }
  public enum State { ... }

  // ============================================================
  // SECTION: Fields
  // ============================================================

  // Immutable
  private final UUID id;
  private final Instant createdAt;

  // Core Profile
  private String name;
  private String bio;

  // Preferences
  private Set<Gender> interestedIn;
  private int maxDistanceKm;

  // Lifestyle (Phase 0.5b)
  private Lifestyle.Smoking smoking;

  // Interests (Phase 1)
  private Set<Interest> interests;

  // ============================================================
  // SECTION: Constructors & Factory Methods
  // ============================================================

  public User(UUID id, String name) { ... }
  public static User fromDatabase(...) { ... }

  // ============================================================
  // SECTION: Getters
  // ============================================================

  public UUID getId() { ... }

  // ============================================================
  // SECTION: Setters (with timestamp update)
  // ============================================================

  public void setName(String name) { ... }

  // ============================================================
  // SECTION: State Machine Transitions
  // ============================================================

  public void activate() { ... }
  public void pause() { ... }
  public void ban() { ... }

  // ============================================================
  // SECTION: Helper Methods
  // ============================================================

  public boolean isComplete() { ... }
  private void touch() { ... }

  // ============================================================
  // SECTION: Object Methods
  // ============================================================

  @Override
  public boolean equals(Object o) { ... }
  @Override
  public int hashCode() { ... }
  @Override
  public String toString() { ... }
}
```

**Benefits**:
- Easier navigation in IDE
- Clear structure at a glance
- Helpful for new developers

---

## Implementation Roadmap

### Phase 1: Critical Fixes (Week 1)

**Priority 1A: ProfileHandler Refactoring**
- [ ] Create `DealbreakersEditor` class
- [ ] Add `mergeFrom()` to `Dealbreakers.Builder`
- [ ] Refactor ProfileHandler to use new classes
- [ ] Update tests
- [ ] **Estimated Time**: 3-4 hours
- [ ] **Files Changed**: 2 new, 2 modified
- [ ] **Lines Saved**: -247 lines in ProfileHandler

**Priority 1B: H2UserStorage Extraction**
- [ ] Create `SerializationUtils` class
- [ ] Create `UserMapper` class
- [ ] Refactor H2UserStorage to use utilities
- [ ] Update other H2*Storage classes to use SerializationUtils
- [ ] Update tests
- [ ] **Estimated Time**: 4-5 hours
- [ ] **Files Changed**: 2 new, 11 modified
- [ ] **Lines Reorganized**: ~250 lines into utilities

### Phase 2: High Priority Improvements (Week 2)

**Priority 2A: MatchingHandler Cleanup**
- [ ] Create `MatchDisplayFormatter` utility
- [ ] Extract display methods from MatchingHandler
- [ ] Simplify MatchingHandler methods
- [ ] Update tests
- [ ] **Estimated Time**: 2-3 hours
- [ ] **Files Changed**: 1 new, 1 modified
- [ ] **Lines Saved**: -116 lines in MatchingHandler

**Priority 2B: Add Section Comments**
- [ ] Add section comments to User.java
- [ ] Add section comments to MatchingHandler.java
- [ ] Add section comments to ProfileHandler.java
- [ ] **Estimated Time**: 1 hour
- [ ] **Files Changed**: 3 modified
- [ ] **Lines Added**: +60 lines (comments)

### Phase 3: Polish (Week 3)

**Priority 3: Minor Improvements**
- [ ] Refactor Main.java menu display
- [ ] Extract constants to CliConstants
- [ ] Add JavaDoc to new utility classes
- [ ] **Estimated Time**: 2 hours
- [ ] **Files Changed**: 3 modified

### Phase 4: Validation (Week 3)

**Verification Tasks**
- [ ] Run full test suite: `mvn test`
- [ ] Run quality checks: `mvn verify`
- [ ] Format code: `mvn spotless:apply`
- [ ] Build fat JAR: `mvn package`
- [ ] Smoke test application
- [ ] Update documentation (CLAUDE.md, AGENTS.md)
- [ ] **Estimated Time**: 1-2 hours

---

## Before/After Examples

### Example 1: ProfileHandler Dealbreaker Editing

**BEFORE** (ProfileHandler.java lines 441-517, 519-594):
```java
// 208 lines of repetitive code
private void editSmokingDealbreaker(User currentUser, Dealbreakers current) {
  logger.info("\nAcceptable smoking (comma-separated, e.g., 1,2):");
  logger.info("  1=Never, 2=Sometimes, 3=Regularly, 0=Clear");
  String input = inputReader.readLine("Choices: ");

  Dealbreakers.Builder builder = Dealbreakers.builder();
  copyExceptSmoking(builder, current);  // 12 lines of duplication
  if (!input.equals("0")) {
    for (String s : input.split(",")) {
      switch (s.trim()) {
        case "1" -> builder.acceptSmoking(Lifestyle.Smoking.NEVER);
        case "2" -> builder.acceptSmoking(Lifestyle.Smoking.SOMETIMES);
        case "3" -> builder.acceptSmoking(Lifestyle.Smoking.REGULARLY);
      }
    }
  }
  currentUser.setDealbreakers(builder.build());
  logger.info("✅ Smoking dealbreaker updated.\n");
}

// ... 5 more nearly identical methods

private void copyExceptSmoking(Dealbreakers.Builder b, Dealbreakers c) {
  if (c.hasDrinkingDealbreaker())
    b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
  if (c.hasKidsDealbreaker())
    b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
  // ... 4 more fields
}

// ... 5 more copyExcept* methods (75 lines total)
```

**AFTER** (ProfileHandler.java - simplified):
```java
// Reduced to ~40 lines
private final DealbreakersEditor dealbreakersEditor;

private void editSmokingDealbreaker(User currentUser, Dealbreakers current) {
  Dealbreakers updated = dealbreakersEditor.editSmoking(current);
  currentUser.setDealbreakers(updated);
  logger.info("✅ Smoking dealbreaker updated.\n");
}

// ... 5 more short wrapper methods (no duplication!)
```

**NEW: DealbreakersEditor.java**:
```java
public class DealbreakersEditor {
  public Dealbreakers editSmoking(Dealbreakers current) {
    // All logic here, properly encapsulated
    return Dealbreakers.builder()
        .mergeFrom(current)  // Single merge method
        .acceptSmoking(promptSmokingChoices())
        .build();
  }
}
```

**Improvement**: 208 lines → ~80 lines total (-128 lines, -62%)

---

### Example 2: H2UserStorage Serialization

**BEFORE** (H2UserStorage.java):
```java
// 300 lines, serialization mixed with SQL
public class H2UserStorage implements UserStorage {

  @Override
  public void save(User user) {
    String sql = "...";  // 30 parameters
    try (Connection conn = dbManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

      // 104 lines of parameter binding + serialization
      stmt.setObject(1, user.getId());
      stmt.setString(2, user.getName());
      // ... 28 more parameters with inline serialization
      stmt.setString(6, gendersToString(user.getInterestedIn()));  // Inline
      stmt.setString(30, serializeInterests(user.getInterests()));  // Inline

      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new StorageException("...", e);
    }
  }

  private String gendersToString(Set<User.Gender> genders) {
    // 10 lines of serialization logic
  }

  private String serializeInterests(Set<Interest> interests) {
    // 8 lines of serialization logic
  }

  // ... 6 more serialization methods (70 lines total)
}
```

**AFTER** (H2UserStorage.java - simplified):
```java
// 120 lines, focused on SQL only
public class H2UserStorage implements UserStorage {

  @Override
  public void save(User user) {
    String sql = "...";
    try (Connection conn = dbManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

      UserMapper.bindParameters(stmt, user);  // Delegated!
      stmt.executeUpdate();

    } catch (SQLException e) {
      throw new StorageException("...", e);
    }
  }

  @Override
  public User get(UUID id) {
    // ... SQL query
    return UserMapper.fromResultSet(rs);  // Delegated!
  }
}
```

**NEW: SerializationUtils.java**:
```java
// 80 lines - reusable serialization logic
public final class SerializationUtils {

  public static <E extends Enum<E>> Set<E> parseEnumSet(String csv, Class<E> clazz) {
    // Common parsing logic
  }

  public static String serializeEnumSet(Set<? extends Enum<?>> values) {
    // Common serialization logic
  }

  // ... 6 more methods
}
```

**NEW: UserMapper.java**:
```java
// 150 lines - User-specific mapping
public class UserMapper {

  public static void bindParameters(PreparedStatement stmt, User user)
      throws SQLException {
    // Uses SerializationUtils for complex fields
    stmt.setObject(1, user.getId());
    stmt.setString(6, SerializationUtils.serializeGenders(user.getInterestedIn()));
    stmt.setString(30, SerializationUtils.serializeInterests(user.getInterests()));
  }

  public static User fromResultSet(ResultSet rs) throws SQLException {
    // Uses SerializationUtils for parsing
    Set<User.Gender> genders = SerializationUtils.parseGenders(rs.getString("interested_in"));
    Set<Interest> interests = SerializationUtils.parseInterests(rs.getString("interests"));
    // ...
  }
}
```

**Improvement**: Better organization, reusable utilities, easier testing

---

## Metrics Summary

### Current State

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Largest File | 597 lines | <400 | ❌ |
| Files >400 lines | 3 | 0 | ⚠️ |
| Code Duplication | 208 lines | 0 | ❌ |
| God Methods (>80 lines) | 4 | 0 | ⚠️ |
| Average Handler Size | 239 lines | <200 | ⚠️ |
| Files with Section Comments | 0 | 10+ | ❌ |

### Target State (After Refactoring)

| Metric | Current | Target | Change |
|--------|---------|--------|--------|
| ProfileHandler size | 597 | 350 | -247 lines |
| MatchingHandler size | 436 | 320 | -116 lines |
| H2UserStorage size | 300 | 120 | -180 lines |
| Main.java size | 152 | 130 | -22 lines |
| Code duplication | 208 lines | 0 | -208 lines |
| New utility classes | 0 | 4 | +4 files |
| Files with sections | 0 | 15+ | +15 files |

**Net Impact**:
- **Lines Reduced**: ~565 lines removed from oversized files
- **Lines Added**: ~380 lines in new utility classes
- **Net Reduction**: ~185 lines (-3% of codebase)
- **Organization**: Significantly improved

---

## Testing Impact

### Files Requiring Test Updates

1. **ProfileHandlerTest** (if exists):
   - Update to test DealbreakersEditor integration
   - Add tests for DealbreakersEditor directly
   - Estimated: +50 lines of tests

2. **H2UserStorageTest**:
   - Verify UserMapper integration
   - No test logic changes needed (same functionality)

3. **New Test Files Needed**:
   - `DealbreakersEditorTest.java` - Test editor logic
   - `SerializationUtilsTest.java` - Test serialization
   - `UserMapperTest.java` - Test mapping logic
   - Estimated: +200 lines total

**Testing Strategy**:
- Unit test utilities independently
- Integration tests remain unchanged
- Refactored classes should pass existing tests

---

## Risk Assessment

### Low Risk Changes ✅

- Adding section comments
- Extracting pure utility classes (SerializationUtils)
- Adding mergeFrom() to Dealbreakers.Builder

**Why Low Risk**: No behavioral changes, additive only

### Medium Risk Changes ⚠️

- Refactoring ProfileHandler (dealbreaker editing)
- Extracting UserMapper
- Refactoring MatchingHandler (display logic)

**Why Medium Risk**: Logic movement, need careful testing

**Mitigation**:
- Comprehensive unit tests before refactoring
- Integration tests verify end-to-end behavior
- Code review focused on logic preservation
- Smoke testing after each change

### High Risk Changes ❌ (None Identified)

**Why No High Risk**: All refactorings preserve existing behavior

---

## Conclusion

The Dating App codebase demonstrates **good architectural organization** but has **inner structure issues** in a few key files. The problems are concentrated in:

1. ProfileHandler (code duplication)
2. MatchingHandler (mixed responsibilities)
3. H2UserStorage (serialization mixed with SQL)

**Key Takeaway**: These issues are **fixable** through straightforward refactorings that don't change behavior. The refactoring plan focuses on:
- Eliminating duplication
- Extracting utilities
- Separating concerns
- Improving navigability

**Recommended Approach**: Tackle in 3 phases over 2-3 weeks, with comprehensive testing between each phase.

**When to Revisit**:
- After Phase 1 completion → Assess if Phase 2 is needed
- After adding 3+ new CLI handlers → Review handler patterns
- Every 6 months → Check for new inner structure issues

---

**Analysis Performed By**: GitHub Copilot (Claude Sonnet 4.5)
**Analysis Date**: 2026-01-10
**Phase**: 1.5
**Next Review**: After Phase 1 implementation or 2026-07-10
