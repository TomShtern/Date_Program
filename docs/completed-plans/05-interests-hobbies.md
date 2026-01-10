# Feature 5: Interests/Hobbies System

**Priority:** High
**Complexity:** Medium
**Dependencies:** None
**Estimated Effort:** ~450 LOC across 11 files
**Status:** ✅ COMPLETE

---

## Overview

Add a tagging system for user interests and hobbies that:
- Enhances profile richness and self-expression
- Factors into match quality scoring (shared interests = higher compatibility)
- Improves candidate sorting (users with more shared interests rank higher)

> [!IMPORTANT]
> This feature fills a critical gap: `MatchQualityService.computeInterestScore()` currently returns a placeholder value of `0.5`. This implementation will provide real interest-based scoring.

---

## User Stories

1. As a user, I want to add interests to my profile from a predefined list
2. As a user, I want to see shared interests highlighted on candidate cards
3. As a user, I want matches with more shared interests to score higher

---

## AI Agent Implementation Guardrails

> [!CAUTION]
> **Mandatory Rules for AI Agents Implementing This Feature:**

### Architecture Constraints
1. **Core package purity**: `Interest.java` and `InterestMatcher.java` must have ZERO framework/database imports. Only `java.*` imports allowed.
2. **Two-layer separation**: Storage serialization logic belongs in `H2UserStorage.java`, NOT in `User.java` or `Interest.java`.
3. **Immutability patterns**: Use `EnumSet.copyOf()` for defensive copies in getters/setters.

### Code Quality Requirements
1. **No magic numbers**: Use constants for limits (e.g., `MAX_INTERESTS = 10`).
2. **Null safety**: All public methods must handle null inputs gracefully.
3. **Test-first approach**: Write tests before or alongside implementation code.

### Integration Points
1. **Do NOT modify**: `Dealbreakers.java`, `DealbreakersEvaluator.java`, or `CandidateFinder` filter logic.
2. **MUST update**: `ServiceRegistry.java` if adding new services.
3. **MUST update**: `ProfilePreviewService.java` to include interests in completeness calculation.

### Verification Gates
- [x] All existing tests pass (`mvn test`)
- [x] New tests achieve 90%+ coverage for new classes
- [x] CLI prompt works with edge cases (0 interests, 10 interests, invalid input)
- [x] Match quality score changes when interests are added

---

## Implementation Order

> [!TIP]
> **Follow this exact sequence to minimize integration issues:**

```
Phase 1: Core Domain (no dependencies)
  1. Interest.java (enum + Category)
  2. InterestMatcher.java (utility)
  3. InterestTest.java + InterestMatcherTest.java

Phase 2: User Integration
  4. User.java (add field + accessors)
  5. UserTest.java (add interest tests)

Phase 3: Storage Layer
  6. H2UserStorage.java (serialize/deserialize)
  7. DatabaseManager.java (schema migration)
  8. H2StorageIntegrationTest.java (verify persistence)

Phase 4: Match Quality Integration
  9. MatchQualityService.java (replace placeholder)
  10. MatchQualityServiceTest.java (add interest tests)

Phase 5: CLI Integration
  11. Main.java (prompts + display)
  12. ProfilePreviewService.java (add to completeness)

Phase 6: Final Verification
  13. Run full test suite
  14. Manual CLI testing
```

---

## Proposed Changes

### Phase 1: Core Domain

#### [NEW] `src/main/java/datingapp/core/Interest.java`

**Purpose**: Enum containing predefined interests organized by category.

**File Location**: `src/main/java/datingapp/core/Interest.java`

**Package Declaration**: `package datingapp.core;`

**Imports Required**: ONLY these standard library imports:
```java
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
```

**Complete Implementation**:
```java
package datingapp.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Predefined interests for user profiles.
 * Organized by category for easier selection in CLI.
 *
 * <p>Usage example:
 * <pre>
 * Set<Interest> interests = EnumSet.of(Interest.HIKING, Interest.COFFEE);
 * Interest.Category cat = Interest.HIKING.getCategory(); // OUTDOORS
 * List<Interest> outdoorInterests = Interest.byCategory(Interest.Category.OUTDOORS);
 * </pre>
 */
public enum Interest {
    // ===== OUTDOORS =====
    HIKING("Hiking", Category.OUTDOORS),
    CAMPING("Camping", Category.OUTDOORS),
    FISHING("Fishing", Category.OUTDOORS),
    CYCLING("Cycling", Category.OUTDOORS),
    RUNNING("Running", Category.OUTDOORS),
    CLIMBING("Climbing", Category.OUTDOORS),

    // ===== ARTS & CULTURE =====
    MOVIES("Movies", Category.ARTS),
    MUSIC("Music", Category.ARTS),
    CONCERTS("Concerts", Category.ARTS),
    ART_GALLERIES("Art Galleries", Category.ARTS),
    THEATER("Theater", Category.ARTS),
    PHOTOGRAPHY("Photography", Category.ARTS),
    READING("Reading", Category.ARTS),
    WRITING("Writing", Category.ARTS),

    // ===== FOOD & DRINK =====
    COOKING("Cooking", Category.FOOD),
    BAKING("Baking", Category.FOOD),
    WINE("Wine", Category.FOOD),
    CRAFT_BEER("Craft Beer", Category.FOOD),
    COFFEE("Coffee", Category.FOOD),
    FOODIE("Foodie", Category.FOOD),

    // ===== SPORTS & FITNESS =====
    GYM("Gym", Category.SPORTS),
    YOGA("Yoga", Category.SPORTS),
    BASKETBALL("Basketball", Category.SPORTS),
    SOCCER("Soccer", Category.SPORTS),
    TENNIS("Tennis", Category.SPORTS),
    SWIMMING("Swimming", Category.SPORTS),
    GOLF("Golf", Category.SPORTS),

    // ===== GAMES & TECH =====
    VIDEO_GAMES("Video Games", Category.TECH),
    BOARD_GAMES("Board Games", Category.TECH),
    CODING("Coding", Category.TECH),
    TECH("Tech", Category.TECH),
    PODCASTS("Podcasts", Category.TECH),

    // ===== SOCIAL =====
    TRAVEL("Travel", Category.SOCIAL),
    DANCING("Dancing", Category.SOCIAL),
    VOLUNTEERING("Volunteering", Category.SOCIAL),
    PETS("Pets", Category.SOCIAL),
    DOGS("Dogs", Category.SOCIAL),
    CATS("Cats", Category.SOCIAL),
    NIGHTLIFE("Nightlife", Category.SOCIAL);

    /**
     * Maximum number of interests a user can select.
     * Enforced in User.setInterests() and CLI prompts.
     */
    public static final int MAX_PER_USER = 10;

    /**
     * Minimum interests for "interests complete" in ProfilePreviewService.
     */
    public static final int MIN_FOR_COMPLETE = 3;

    /**
     * Interest categories for organized display.
     */
    public enum Category {
        OUTDOORS("🏕️ Outdoors"),
        ARTS("🎨 Arts & Culture"),
        FOOD("🍳 Food & Drink"),
        SPORTS("🏃 Sports & Fitness"),
        TECH("🎮 Games & Tech"),
        SOCIAL("🎉 Social");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final String displayName;
    private final Category category;

    Interest(String displayName, Category category) {
        this.displayName = Objects.requireNonNull(displayName);
        this.category = Objects.requireNonNull(category);
    }

    public String getDisplayName() {
        return displayName;
    }

    public Category getCategory() {
        return category;
    }

    /**
     * Returns all interests in the given category.
     * @param category the category to filter by
     * @return list of interests (never null, may be empty)
     */
    public static List<Interest> byCategory(Category category) {
        if (category == null) {
            return List.of();
        }
        return Arrays.stream(values())
            .filter(i -> i.category == category)
            .toList();
    }

    /**
     * Returns total count of available interests.
     */
    public static int count() {
        return values().length;
    }
}
```

**Validation Checklist for AI Agent**:
- [ ] File is in `src/main/java/datingapp/core/`
- [ ] Package is `datingapp.core`
- [ ] Only `java.*` imports (no framework imports)
- [ ] All enum constants have displayName and category
- [ ] `byCategory()` handles null input
- [ ] Constants `MAX_PER_USER` and `MIN_FOR_COMPLETE` are defined
- [ ] Category enum has emoji display names

---

#### [NEW] `src/main/java/datingapp/core/InterestMatcher.java`

**Purpose**: Utility class to compute interest overlap between two users.

**File Location**: `src/main/java/datingapp/core/InterestMatcher.java`

**Complete Implementation**:
```java
package datingapp.core;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for comparing interest sets between users.
 * Used by MatchQualityService to compute interest-based compatibility.
 *
 * <p>This class computes two metrics:
 * <ul>
 *   <li><b>Overlap Ratio</b>: shared / min(a.size, b.size) - rewards having all interests match</li>
 *   <li><b>Jaccard Index</b>: shared / union - standard similarity metric</li>
 * </ul>
 *
 * <p>Thread-safe: This class is stateless and all methods are static.
 */
public final class InterestMatcher {

    private InterestMatcher() {
        // Utility class - prevent instantiation
    }

    /**
     * Result of comparing two interest sets.
     *
     * @param shared the interests both users have in common
     * @param sharedCount number of shared interests (convenience)
     * @param overlapRatio shared / min(a.size, b.size), range [0.0, 1.0]
     * @param jaccardIndex shared / union, range [0.0, 1.0]
     */
    public record MatchResult(
        Set<Interest> shared,
        int sharedCount,
        double overlapRatio,
        double jaccardIndex
    ) {
        /**
         * Returns true if there are any shared interests.
         */
        public boolean hasSharedInterests() {
            return sharedCount > 0;
        }
    }

    /**
     * Compares two sets of interests and returns detailed match metrics.
     *
     * @param a first interest set (may be null or empty)
     * @param b second interest set (may be null or empty)
     * @return MatchResult with overlap metrics
     */
    public static MatchResult compare(Set<Interest> a, Set<Interest> b) {
        // Handle null/empty cases
        Set<Interest> setA = (a == null || a.isEmpty())
            ? EnumSet.noneOf(Interest.class)
            : EnumSet.copyOf(a);
        Set<Interest> setB = (b == null || b.isEmpty())
            ? EnumSet.noneOf(Interest.class)
            : EnumSet.copyOf(b);

        // Empty set case
        if (setA.isEmpty() || setB.isEmpty()) {
            return new MatchResult(EnumSet.noneOf(Interest.class), 0, 0.0, 0.0);
        }

        // Compute intersection
        Set<Interest> shared = EnumSet.copyOf(setA);
        shared.retainAll(setB);
        int sharedCount = shared.size();

        // Compute union for Jaccard
        Set<Interest> union = EnumSet.copyOf(setA);
        union.addAll(setB);
        int unionSize = union.size();

        // Calculate ratios
        int minSize = Math.min(setA.size(), setB.size());
        double overlapRatio = (double) sharedCount / minSize;
        double jaccardIndex = (double) sharedCount / unionSize;

        return new MatchResult(shared, sharedCount, overlapRatio, jaccardIndex);
    }

    /**
     * Formats shared interests as a human-readable string.
     * Shows up to 3 interests, with "and X more" if exceeded.
     *
     * @param shared set of shared interests
     * @return formatted string like "Hiking, Coffee, and 2 more" or empty string if none
     */
    public static String formatSharedInterests(Set<Interest> shared) {
        if (shared == null || shared.isEmpty()) {
            return "";
        }

        List<String> names = shared.stream()
            .limit(3)
            .map(Interest::getDisplayName)
            .collect(Collectors.toList());

        int remaining = shared.size() - 3;

        if (remaining > 0) {
            return String.join(", ", names) + ", and " + remaining + " more";
        } else if (names.size() == 1) {
            return names.get(0);
        } else if (names.size() == 2) {
            return names.get(0) + " and " + names.get(1);
        } else {
            return names.get(0) + ", " + names.get(1) + ", and " + names.get(2);
        }
    }

    /**
     * Formats shared interests as a list for display.
     * Returns display names sorted alphabetically.
     *
     * @param shared set of shared interests
     * @return list of display names (never null)
     */
    public static List<String> formatAsList(Set<Interest> shared) {
        if (shared == null || shared.isEmpty()) {
            return List.of();
        }
        return shared.stream()
            .map(Interest::getDisplayName)
            .sorted()
            .toList();
    }
}
```

**Validation Checklist for AI Agent**:
- [ ] Class is `final` with private constructor
- [ ] Only `java.*` imports
- [ ] `compare()` handles null and empty sets gracefully
- [ ] `MatchResult` record has `hasSharedInterests()` convenience method
- [ ] `formatSharedInterests()` limits to 3 items with "and X more"

---

### Phase 2: User Integration

#### [MODIFY] `src/main/java/datingapp/core/User.java`

**Changes Required**:

1. Add import (after existing imports):
```java
import java.util.EnumSet;  // Add if not present
```

2. Add field (after `private Dealbreakers dealbreakers;` around line 59):
```java
// Interests (Phase 1 feature)
private Set<Interest> interests = EnumSet.noneOf(Interest.class);
```

3. Add getter (after `getDealbreakers()` around line 194):
```java
/**
 * Returns the user's interests as a defensive copy.
 * @return set of interests (never null, may be empty)
 */
public Set<Interest> getInterests() {
    return interests.isEmpty()
        ? EnumSet.noneOf(Interest.class)
        : EnumSet.copyOf(interests);
}
```

4. Add setter (after lifestyle setters around line 318):
```java
/**
 * Sets the user's interests.
 * Maximum of {@link Interest#MAX_PER_USER} interests allowed.
 *
 * @param interests set of interests (null treated as empty)
 * @throws IllegalArgumentException if more than MAX_PER_USER interests
 */
public void setInterests(Set<Interest> interests) {
    if (interests != null && interests.size() > Interest.MAX_PER_USER) {
        throw new IllegalArgumentException(
            "Maximum " + Interest.MAX_PER_USER + " interests allowed, got " + interests.size());
    }
    this.interests = (interests == null || interests.isEmpty())
        ? EnumSet.noneOf(Interest.class)
        : EnumSet.copyOf(interests);
    touch();
}

/**
 * Adds a single interest to the user's profile.
 * @param interest the interest to add
 * @throws IllegalArgumentException if adding would exceed MAX_PER_USER
 */
public void addInterest(Interest interest) {
    if (interest == null) {
        return;
    }
    if (interests.size() >= Interest.MAX_PER_USER && !interests.contains(interest)) {
        throw new IllegalArgumentException(
            "Maximum " + Interest.MAX_PER_USER + " interests allowed");
    }
    interests.add(interest);
    touch();
}

/**
 * Removes an interest from the user's profile.
 * @param interest the interest to remove
 */
public void removeInterest(Interest interest) {
    if (interest != null && interests.remove(interest)) {
        touch();
    }
}
```

5. Update full constructor (around line 80) to accept interests:
```java
// Add parameter after `Instant updatedAt`:
// Set<Interest> interests
```

> [!WARNING]
> **Do NOT modify the 2-argument constructor** (`User(UUID id, String name)`). That's for creating new incomplete users.

**Validation Checklist for AI Agent**:
- [ ] Import `Interest` added
- [ ] Field initialized to `EnumSet.noneOf(Interest.class)`
- [ ] Getter returns defensive copy
- [ ] Setter validates max limit and calls `touch()`
- [ ] `addInterest()` validates limit before adding
- [ ] `removeInterest()` only calls `touch()` if change occurred

---

### Phase 3: Storage Layer

#### [MODIFY] `src/main/java/datingapp/storage/H2UserStorage.java`

**Changes Required**:

1. Find the `save()` method and add interests serialization:
```java
// After setting other fields in the PreparedStatement:
String interestsStr = user.getInterests().stream()
    .map(Interest::name)
    .sorted()  // Consistent ordering for testing
    .collect(Collectors.joining(","));
stmt.setString(columnIndex, interestsStr);  // Adjust column index!
```

2. Add import at top:
```java
import datingapp.core.Interest;
```

3. Find the `mapRow()` or result set mapping method and add deserialization:
```java
// After extracting other fields:
String interestsStr = rs.getString("interests");
Set<Interest> interests;
if (interestsStr != null && !interestsStr.isBlank()) {
    interests = Arrays.stream(interestsStr.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(Interest::valueOf)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Interest.class)));
} else {
    interests = EnumSet.noneOf(Interest.class);
}
user.setInterests(interests);
```

> [!CAUTION]
> **Handle Invalid Enum Values**: If the database contains an interest name that doesn't exist in the enum (e.g., after removing an interest), `Interest.valueOf()` will throw. Wrap in try-catch:
> ```java
> try {
>     interests.add(Interest.valueOf(name));
> } catch (IllegalArgumentException e) {
>     // Log and skip invalid interest
> }
> ```

#### [MODIFY] `src/main/java/datingapp/storage/DatabaseManager.java`

**Find the schema creation SQL** and add the interests column:

```sql
-- In CREATE TABLE users statement, add:
interests VARCHAR(500),
```

**Or if table already exists**, add migration in `initializeSchema()`:
```java
// After other ALTER TABLE statements:
try {
    stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS interests VARCHAR(500)");
} catch (SQLException e) {
    // Column may already exist, ignore
}
```

---

### Phase 4: Match Quality Integration

#### [MODIFY] `src/main/java/datingapp/core/MatchQualityService.java`

**Purpose**: Replace the placeholder interest score with real calculation.

**Find**: The method that computes interest score (search for `0.5` or `interestScore`).

**Replace placeholder with**:
```java
/**
 * Computes interest compatibility score.
 * Uses overlap ratio (shared / smaller set) for scoring.
 *
 * @param user first user
 * @param other second user
 * @return score between 0.0 and 1.0
 */
private double computeInterestScore(User user, User other) {
    InterestMatcher.MatchResult result = InterestMatcher.compare(
        user.getInterests(),
        other.getInterests()
    );

    // If either user has no interests, return neutral score
    if (user.getInterests().isEmpty() || other.getInterests().isEmpty()) {
        return 0.5;
    }

    return result.overlapRatio();
}
```

**Also update highlight generation** (find `generateHighlights` or similar):
```java
// Add after other highlight checks:
InterestMatcher.MatchResult interestResult = InterestMatcher.compare(
    user.getInterests(),
    other.getInterests()
);
if (interestResult.sharedCount() >= 2) {
    String interestsText = InterestMatcher.formatSharedInterests(interestResult.shared());
    highlights.add("You both enjoy " + interestsText);
}
```

**Add import**:
```java
import datingapp.core.InterestMatcher;
```

---

### Phase 5: CLI Integration

#### [MODIFY] `src/main/java/datingapp/Main.java`

**Add interest selection to profile completion**:

Find `promptInterests()` method (around line 321) and replace with:

```java
private static void promptInterests() {
    logger.info("\n");
    logger.info("═══════════════════════════════════════");
    logger.info("     🎯 SELECT YOUR INTERESTS");
    logger.info("═══════════════════════════════════════");
    logger.info("  Choose up to {} interests that describe you.", Interest.MAX_PER_USER);
    logger.info("  Having at least {} interests improves your matches!\n", Interest.MIN_FOR_COMPLETE);

    // Display current selections
    Set<Interest> selected = currentUser.getInterests();
    logger.info("  Currently selected: {}/{}\n", selected.size(), Interest.MAX_PER_USER);

    // Display by category
    int globalIndex = 1;
    for (Interest.Category category : Interest.Category.values()) {
        logger.info("  {}", category.getDisplayName());
        List<Interest> catInterests = Interest.byCategory(category);
        for (Interest interest : catInterests) {
            String marker = selected.contains(interest) ? "✓" : " ";
            logger.info("    {:2}. [{}] {}", globalIndex, marker, interest.getDisplayName());
            globalIndex++;
        }
        logger.info("");
    }

    logger.info("  Enter numbers to toggle (e.g., '1,5,12'), or 'done' to finish:");
    String input = readLine("  > ").trim().toLowerCase();

    if (input.equals("done") || input.isEmpty()) {
        return;
    }

    // Parse and toggle interests
    try {
        Set<Interest> updated = EnumSet.copyOf(selected);
        Interest[] allInterests = Interest.values();

        for (String part : input.split(",")) {
            int index = Integer.parseInt(part.trim()) - 1;
            if (index >= 0 && index < allInterests.length) {
                Interest interest = allInterests[index];
                if (updated.contains(interest)) {
                    updated.remove(interest);
                    logger.info("  ➖ Removed: {}", interest.getDisplayName());
                } else if (updated.size() < Interest.MAX_PER_USER) {
                    updated.add(interest);
                    logger.info("  ➕ Added: {}", interest.getDisplayName());
                } else {
                    logger.info("  ⚠️ Maximum {} interests reached!", Interest.MAX_PER_USER);
                }
            }
        }

        currentUser.setInterests(updated);
        userStorage().save(currentUser);
        logger.info("\n  ✅ Interests updated! ({}/{})\n", updated.size(), Interest.MAX_PER_USER);

    } catch (NumberFormatException e) {
        logger.info("  ❌ Invalid input. Enter numbers separated by commas.\n");
    }
}
```

**Update candidate card display** (find where candidate info is shown):
```java
// Add after bio display:
Set<Interest> shared = InterestMatcher.compare(
    currentUser.getInterests(),
    candidate.getInterests()
).shared();
if (!shared.isEmpty()) {
    logger.info("│ 🎯 Shared: {}", InterestMatcher.formatSharedInterests(shared));
}
```

**Add import**:
```java
import datingapp.core.Interest;
import datingapp.core.InterestMatcher;
```

---

#### [MODIFY] `src/main/java/datingapp/core/ProfilePreviewService.java`

**Add interests to profile completeness calculation**:

```java
// In the list of tracked fields, add:
// "interests" - count as complete if >= MIN_FOR_COMPLETE

// In completeness calculation:
int interestCount = user.getInterests().size();
if (interestCount >= Interest.MIN_FOR_COMPLETE) {
    completedFields++;
}

// In tips generation:
if (interestCount == 0) {
    tips.add("Add at least " + Interest.MIN_FOR_COMPLETE +
        " interests - profiles with shared interests get 60% more matches");
} else if (interestCount < Interest.MIN_FOR_COMPLETE) {
    int needed = Interest.MIN_FOR_COMPLETE - interestCount;
    tips.add("Add " + needed + " more interest(s) to complete your profile");
}
```

---

## Data Model Summary

```
User.interests: Set<Interest>
  ↓ serialized in H2UserStorage.save()
users.interests: VARCHAR(500) = "HIKING,COFFEE,TRAVEL,MOVIES"
  ↓ deserialized in H2UserStorage.mapRow()
EnumSet<Interest>
```

---

## Scoring Algorithm Reference

```java
// Overlap Ratio (used for scoring):
overlapRatio = sharedCount / min(userInterests.size, otherInterests.size)

// Examples:
// User A: [Hiking, Coffee, Travel]  (3 interests)
// User B: [Hiking, Coffee, Movies]  (3 interests)
// Shared: [Hiking, Coffee]          (2 interests)
// Overlap Ratio = 2 / 3 = 0.667

// Edge case - asymmetric sizes:
// User A: [Hiking, Coffee]          (2 interests)
// User B: [Hiking, Coffee, Travel, Movies, Yoga]  (5 interests)
// Shared: [Hiking, Coffee]          (2 interests)
// Overlap Ratio = 2 / 2 = 1.0 (100% of smaller set matches)
```

---

## Edge Cases Reference

| Scenario | Expected Behavior |
|----------|-------------------|
| User has 0 interests | Score = 0.5 (neutral), no "Shared" display |
| Both users have identical interests | Score = 1.0, "You both enjoy X, Y, and Z" |
| 0 shared interests | Score = 0.0, no "Shared" display |
| One user: 2 interests, other: 10 | Use smaller set as denominator |
| User tries to add 11th interest | `IllegalArgumentException` thrown |
| Database has invalid interest name | Skip and log warning, don't crash |
| Null interests in comparison | Treated as empty set |

---

## Test Plan

### Unit Tests

#### [NEW] `src/test/java/datingapp/core/InterestTest.java`

```java
@Test void byCategory_outdoors_returns6Interests()
@Test void byCategory_null_returnsEmptyList()
@Test void allInterests_haveDisplayNames()
@Test void allInterests_haveCategories()
@Test void count_returnsCorrectTotal()
@Test void maxPerUser_is10()
```

#### [NEW] `src/test/java/datingapp/core/InterestMatcherTest.java`

```java
@Nested class Compare {
    @Test void identicalSets_returnsOverlapRatio1()
    @Test void noOverlap_returnsOverlapRatio0()
    @Test void partialOverlap_calculatesCorrectly()
    @Test void emptyFirstSet_returnsZeroRatios()
    @Test void emptySecondSet_returnsZeroRatios()
    @Test void nullFirstSet_handlesGracefully()
    @Test void nullSecondSet_handlesGracefully()
    @Test void asymmetricSizes_usesSmallestDenominator()
}

@Nested class FormatSharedInterests {
    @Test void singleInterest_returnsName()
    @Test void twoInterests_joinsWithAnd()
    @Test void threeInterests_joinsWithCommaAnd()
    @Test void fourPlusInterests_showsAndXMore()
    @Test void emptySet_returnsEmptyString()
    @Test void nullSet_returnsEmptyString()
}
```

#### [MODIFY] `src/test/java/datingapp/core/UserTest.java`

```java
@Nested class Interests {
    @Test void newUser_hasEmptyInterests()
    @Test void setInterests_storesValues()
    @Test void setInterests_null_treatedAsEmpty()
    @Test void setInterests_exceedsMax_throwsException()
    @Test void addInterest_addsToSet()
    @Test void addInterest_atMax_throwsException()
    @Test void removeInterest_removesFromSet()
    @Test void getInterests_returnsDefensiveCopy()
}
```

#### [MODIFY] `src/test/java/datingapp/core/MatchQualityServiceTest.java`

```java
@Nested class InterestScoring {
    @Test void noInterests_returns0point5()
    @Test void identicalInterests_increases overallScore()
    @Test void noSharedInterests_decreasesScore()
    @Test void sharedInterests_addHighlight()
}
```

### Integration Tests

#### [MODIFY] `src/test/java/datingapp/storage/H2StorageIntegrationTest.java`

```java
@Test void saveAndLoadUser_preservesInterests()
@Test void saveUser_emptyInterests_handlesGracefully()
@Test void loadUser_missingInterestsColumn_returnsEmptySet()
```

### Manual CLI Test Checklist

```
1. Start app: `mvn exec:java`
2. Create new user
3. Complete profile → Select "Edit interests"
4. Add 5 interests (Hiking, Coffee, Travel, Movies, Cooking)
5. Verify display shows "5/10 selected"
6. Try adding 6 more → Should stop at 10 with warning
7. Exit and restart app → Verify interests persisted
8. Create second user with some overlapping interests
9. Browse as first user → Should see "🎯 Shared: Hiking, Coffee" on candidate card
10. Like and match → View match details → Should show interest highlight
```

---

## File Change Summary

| File | Action | LOC | Priority |
|------|--------|-----|----------|
| `Interest.java` | NEW | ~120 | P1 |
| `InterestMatcher.java` | NEW | ~90 | P1 |
| `InterestTest.java` | NEW | ~40 | P1 |
| `InterestMatcherTest.java` | NEW | ~80 | P1 |
| `User.java` | MODIFY | +40 | P2 |
| `UserTest.java` | MODIFY | +30 | P2 |
| `H2UserStorage.java` | MODIFY | +25 | P3 |
| `DatabaseManager.java` | MODIFY | +5 | P3 |
| `H2StorageIntegrationTest.java` | MODIFY | +15 | P3 |
| `MatchQualityService.java` | MODIFY | +25 | P4 |
| `MatchQualityServiceTest.java` | MODIFY | +20 | P4 |
| `Main.java` | MODIFY | +70 | P5 |
| `ProfilePreviewService.java` | MODIFY | +15 | P5 |

**Total: ~575 LOC across 13 files**

---

## Rollback Plan

If issues are discovered post-implementation:

1. **Database**: Column is nullable, so old data unaffected
2. **User.java**: Field defaults to empty set, backward compatible
3. **MatchQualityService**: Can revert to `return 0.5` placeholder
4. **Main.java**: Can remove/comment out interest prompts

---

## Success Criteria

- [x] All 24+ existing tests pass
- [x] 12+ new tests added and passing
- [x] Interest selection works in CLI
- [x] Interests persist across app restarts
- [x] Match quality score changes with shared interests
- [x] Candidate cards show "🎯 Shared: X, Y" when applicable
- [x] Profile preview includes interest completeness
- [x] No framework imports in core package

---

**Last Updated:** 2026-01-09
**Estimated Implementation Time:** 2-3 hours for experienced developer
