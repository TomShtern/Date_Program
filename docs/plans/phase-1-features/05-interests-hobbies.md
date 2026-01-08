# Feature 5: Interests/Hobbies System

**Priority:** High
**Complexity:** Medium
**Dependencies:** None

---

## Overview

Add a tagging system for user interests and hobbies that:
- Enhances profile richness and self-expression
- Factors into match quality scoring (shared interests = higher compatibility)
- Improves candidate sorting (users with more shared interests rank higher)

---

## User Stories

1. As a user, I want to add interests to my profile from a predefined list
2. As a user, I want to see shared interests highlighted on candidate cards
3. As a user, I want matches with more shared interests to score higher

---

## Proposed Changes

### Core Layer

#### [NEW] `Interest.java`
Enum containing predefined interests organized by category.

```java
public enum Interest {
    // Outdoors
    HIKING("Hiking", Category.OUTDOORS),
    CAMPING("Camping", Category.OUTDOORS),
    FISHING("Fishing", Category.OUTDOORS),
    CYCLING("Cycling", Category.OUTDOORS),
    RUNNING("Running", Category.OUTDOORS),

    // Arts & Culture
    MOVIES("Movies", Category.ARTS),
    MUSIC("Music", Category.ARTS),
    CONCERTS("Concerts", Category.ARTS),
    ART_GALLERIES("Art Galleries", Category.ARTS),
    THEATER("Theater", Category.ARTS),
    PHOTOGRAPHY("Photography", Category.ARTS),
    READING("Reading", Category.ARTS),
    WRITING("Writing", Category.ARTS),

    // Food & Drink
    COOKING("Cooking", Category.FOOD),
    BAKING("Baking", Category.FOOD),
    WINE("Wine", Category.FOOD),
    CRAFT_BEER("Craft Beer", Category.FOOD),
    COFFEE("Coffee", Category.FOOD),
    FOODIE("Foodie", Category.FOOD),

    // Sports
    GYM("Gym", Category.SPORTS),
    YOGA("Yoga", Category.SPORTS),
    BASKETBALL("Basketball", Category.SPORTS),
    SOCCER("Soccer", Category.SPORTS),
    TENNIS("Tennis", Category.SPORTS),
    SWIMMING("Swimming", Category.SPORTS),

    // Games & Tech
    VIDEO_GAMES("Video Games", Category.TECH),
    BOARD_GAMES("Board Games", Category.TECH),
    CODING("Coding", Category.TECH),
    TECH("Tech", Category.TECH),

    // Social
    TRAVEL("Travel", Category.SOCIAL),
    DANCING("Dancing", Category.SOCIAL),
    VOLUNTEERING("Volunteering", Category.SOCIAL),
    PETS("Pets", Category.SOCIAL),
    DOGS("Dogs", Category.SOCIAL),
    CATS("Cats", Category.SOCIAL);

    public enum Category {
        OUTDOORS, ARTS, FOOD, SPORTS, TECH, SOCIAL
    }

    private final String displayName;
    private final Category category;

    public String getDisplayName();
    public Category getCategory();
    public static List<Interest> byCategory(Category category);
}
```

#### [MODIFY] `User.java`
Add interests field.

```java
// Add field
private Set<Interest> interests = EnumSet.noneOf(Interest.class);

// Add getter (defensive copy)
public Set<Interest> getInterests() {
    return EnumSet.copyOf(interests);
}

// Add setter
public void setInterests(Set<Interest> interests) {
    this.interests = interests != null
        ? EnumSet.copyOf(interests)
        : EnumSet.noneOf(Interest.class);
    touch();
}

// Add convenience method
public void addInterest(Interest interest) {
    this.interests.add(interest);
    touch();
}
```

#### [NEW] `InterestMatcher.java`
Utility to compute interest overlap.

```java
public class InterestMatcher {

    public record MatchResult(
        Set<Interest> shared,
        int sharedCount,
        double overlapRatio,    // shared / min(a.size, b.size)
        double jaccardIndex     // shared / union
    ) {}

    public static MatchResult compare(Set<Interest> a, Set<Interest> b);
    public static List<String> formatSharedInterests(Set<Interest> shared);
}
```

#### [MODIFY] `MatchQualityService.java`
Incorporate interests into scoring.

```java
// In computeQuality() method:
InterestMatcher.MatchResult interestMatch =
    InterestMatcher.compare(user.getInterests(), other.getInterests());

// Weight: 15% of total score
double interestScore = interestMatch.overlapRatio();

// Add to highlights if shared
if (interestMatch.sharedCount() >= 2) {
    highlights.add("You both enjoy " + formatTop3(interestMatch.shared()));
}
```

#### [MODIFY] `CandidateFinder.java`
Optionally sort by shared interests.

```java
// Add secondary sort after distance:
.sorted(
    Comparator.comparingDouble((User c) -> distanceTo(seeker, c))
        .thenComparingInt(c -> -countSharedInterests(seeker, c))
)
```

---

### Storage Layer

#### [MODIFY] `H2UserStorage.java`
Store interests as comma-separated string (like `interestedIn`).

```java
// In save():
String interestsStr = user.getInterests().stream()
    .map(Interest::name)
    .collect(Collectors.joining(","));

// In mapRow():
String interestsStr = rs.getString("interests");
Set<Interest> interests = interestsStr != null && !interestsStr.isEmpty()
    ? Arrays.stream(interestsStr.split(","))
        .map(Interest::valueOf)
        .collect(Collectors.toSet())
    : EnumSet.noneOf(Interest.class);
```

#### Database Migration
Add column to users table:
```sql
ALTER TABLE users ADD COLUMN interests VARCHAR(500);
```

---

### CLI Changes

#### [MODIFY] `Main.java`

**New Menu Option (in `completeProfile()`):**
```java
private static void promptInterests() {
    logger.info("\n--- Select Your Interests (up to 10) ---\n");

    // Display by category
    for (Interest.Category category : Interest.Category.values()) {
        logger.info("\n  {}", category.name());
        List<Interest> catInterests = Interest.byCategory(category);
        for (int i = 0; i < catInterests.size(); i++) {
            Interest interest = catInterests.get(i);
            String marker = currentUser.getInterests().contains(interest) ? "✓" : " ";
            logger.info("    {}. [{}] {}", i + 1, marker, interest.getDisplayName());
        }
    }

    logger.info("\n  Enter numbers to toggle (e.g., '1,5,12'), or 'done' to finish:");
    // Parse input and toggle interests
}
```

**Enhanced Candidate Card:**
```
┌─────────────────────────────────────────┐
│ 💝 Sarah, 28 years old
│ 📍 3.2 km away
│ 📝 Love hiking and coffee!
│ 🎯 Shared: Hiking, Coffee, Travel      ← NEW
└─────────────────────────────────────────┘
```

**Match Details Enhancement:**
```
  ✨ WHY YOU MATCHED
  • You both enjoy Hiking, Coffee, and Travel
  • 5 shared interests out of 8
```

---

## Data Model

```
User.interests: Set<Interest>
  ↓ stored as
users.interests: VARCHAR(500) = "HIKING,COFFEE,TRAVEL,MOVIES"
  ↓ loaded as
EnumSet<Interest>
```

---

## Scoring Algorithm

```java
// In MatchQualityService
private double computeInterestScore(User user, User other) {
    Set<Interest> userInterests = user.getInterests();
    Set<Interest> otherInterests = other.getInterests();

    if (userInterests.isEmpty() || otherInterests.isEmpty()) {
        return 0.5; // Neutral if no interests set
    }

    Set<Interest> shared = EnumSet.copyOf(userInterests);
    shared.retainAll(otherInterests);

    int minSize = Math.min(userInterests.size(), otherInterests.size());
    return (double) shared.size() / minSize;
}
```

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| User has no interests set | Score defaults to 0.5 (neutral) |
| Both users have same 5 interests | Score = 1.0 (perfect overlap) |
| 0 shared interests | Score = 0.0 |
| One user: 2 interests, other: 10 | Use smaller set as denominator |

---

## Verification Plan

### Unit Tests

Create `InterestMatcherTest.java`:
- `compare_identicalSets_returns100Percent`
- `compare_noOverlap_returns0Percent`
- `compare_partialOverlap_calculatesCorrectly`
- `compare_emptySets_handlesGracefully`

Create `InterestTest.java`:
- `byCategory_returnsCorrectInterests`
- `allInterests_haveDisplayNames`

### Integration Tests

Update `MatchQualityServiceTest.java`:
- Test that shared interests boost compatibility score

### Manual CLI Test

1. Create two users with overlapping interests
2. Complete profiles with: User A (Hiking, Coffee, Travel), User B (Hiking, Movies, Travel)
3. Browse as User A, like User B
4. Browse as User B, like User A → Match
5. View match details → Should show "Shared: Hiking, Travel"

---

## File Summary

| File | Action | Lines |
|------|--------|-------|
| `Interest.java` | NEW | ~120 |
| `InterestMatcher.java` | NEW | ~50 |
| `User.java` | MODIFY | +25 |
| `MatchQualityService.java` | MODIFY | +30 |
| `CandidateFinder.java` | MODIFY | +10 |
| `H2UserStorage.java` | MODIFY | +20 |
| `Main.java` | MODIFY | +60 |
| `InterestMatcherTest.java` | NEW | ~60 |
| `InterestTest.java` | NEW | ~30 |

**Total estimated: ~405 lines**
