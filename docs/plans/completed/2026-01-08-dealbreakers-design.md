# Dealbreakers (Hard Filters) - Design Document

**Date:** 2026-01-08
**Status:** ✅ COMPLETE
**Completed:** 2026-01-10
**Priority:** P1
**Complexity:** Medium
**Dependencies:** None

---

## 1. Overview

### Purpose
Allow users to set **hard filters** (dealbreakers) that absolutely exclude candidates. Unlike symmetric preferences (age range, distance), dealbreakers are **one-way**: if I have a dealbreaker against smokers, I won't see smokers, but smokers can still see me (unless they have their own dealbreakers).

### Key Distinction: Preferences vs Dealbreakers

| Type | Behavior | Example |
|------|----------|---------|
| **Preference** | Symmetric, both must match | Age range, gender interest |
| **Dealbreaker** | One-way, only I filter | "Must not smoke" |

### Why This Matters
- Users waste time swiping on fundamentally incompatible people
- Reduces frustration from matching with incompatible partners
- More meaningful matches = better user experience

---

## 2. Domain Model

### 2.1 Lifestyle Enums (New Profile Fields)

```java
// core/Lifestyle.java - Container for lifestyle enums
public final class Lifestyle {
    private Lifestyle() {} // Utility class

    /**
     * Smoking habits.
     */
    public enum Smoking {
        NEVER("Never"),
        SOMETIMES("Sometimes"),
        REGULARLY("Regularly");

        private final String displayName;
        Smoking(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * Drinking habits.
     */
    public enum Drinking {
        NEVER("Never"),
        SOCIALLY("Socially"),
        REGULARLY("Regularly");

        private final String displayName;
        Drinking(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * Stance on having children.
     */
    public enum WantsKids {
        NO("Don't want"),
        OPEN("Open to it"),
        SOMEDAY("Want someday"),
        HAS_KIDS("Have kids");

        private final String displayName;
        WantsKids(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * Relationship goals.
     */
    public enum LookingFor {
        CASUAL("Something casual"),
        SHORT_TERM("Short-term dating"),
        LONG_TERM("Long-term relationship"),
        MARRIAGE("Marriage"),
        UNSURE("Not sure yet");

        private final String displayName;
        LookingFor(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * Education level.
     */
    public enum Education {
        HIGH_SCHOOL("High school"),
        SOME_COLLEGE("Some college"),
        BACHELORS("Bachelor's degree"),
        MASTERS("Master's degree"),
        PHD("PhD/Doctorate"),
        TRADE_SCHOOL("Trade school"),
        OTHER("Other");

        private final String displayName;
        Education(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
}
```

### 2.2 Dealbreakers Record

```java
// core/Dealbreakers.java
public record Dealbreakers(
    // Lifestyle dealbreakers - null or empty Set means "no preference"
    Set<Lifestyle.Smoking> acceptableSmoking,
    Set<Lifestyle.Drinking> acceptableDrinking,
    Set<Lifestyle.WantsKids> acceptableKidsStance,
    Set<Lifestyle.LookingFor> acceptableLookingFor,
    Set<Lifestyle.Education> minimumEducation,

    // Physical dealbreakers
    Integer minHeightCm,    // null = no minimum
    Integer maxHeightCm,    // null = no maximum

    // Age dealbreaker (stricter than preference)
    Integer maxAgeDifference  // null = use standard age preference
) {
    public Dealbreakers {
        // Defensive copies
        acceptableSmoking = acceptableSmoking == null ? Set.of()
            : Set.copyOf(acceptableSmoking);
        acceptableDrinking = acceptableDrinking == null ? Set.of()
            : Set.copyOf(acceptableDrinking);
        acceptableKidsStance = acceptableKidsStance == null ? Set.of()
            : Set.copyOf(acceptableKidsStance);
        acceptableLookingFor = acceptableLookingFor == null ? Set.of()
            : Set.copyOf(acceptableLookingFor);
        minimumEducation = minimumEducation == null ? Set.of()
            : Set.copyOf(minimumEducation);

        // Validate height range
        if (minHeightCm != null && minHeightCm < 100) {
            throw new IllegalArgumentException("minHeightCm too low: " + minHeightCm);
        }
        if (maxHeightCm != null && maxHeightCm > 250) {
            throw new IllegalArgumentException("maxHeightCm too high: " + maxHeightCm);
        }
        if (minHeightCm != null && maxHeightCm != null && minHeightCm > maxHeightCm) {
            throw new IllegalArgumentException("minHeightCm > maxHeightCm");
        }

        // Validate age difference
        if (maxAgeDifference != null && maxAgeDifference < 0) {
            throw new IllegalArgumentException("maxAgeDifference cannot be negative");
        }
    }

    /**
     * Factory for no dealbreakers (accepts everyone).
     */
    public static Dealbreakers none() {
        return new Dealbreakers(
            Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
            null, null, null
        );
    }

    /**
     * Builder for constructing dealbreakers.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Set<Lifestyle.Smoking> smoking = new HashSet<>();
        private Set<Lifestyle.Drinking> drinking = new HashSet<>();
        private Set<Lifestyle.WantsKids> kids = new HashSet<>();
        private Set<Lifestyle.LookingFor> lookingFor = new HashSet<>();
        private Set<Lifestyle.Education> education = new HashSet<>();
        private Integer minHeight = null;
        private Integer maxHeight = null;
        private Integer maxAgeDiff = null;

        public Builder acceptSmoking(Lifestyle.Smoking... values) {
            smoking.addAll(Arrays.asList(values));
            return this;
        }

        public Builder acceptDrinking(Lifestyle.Drinking... values) {
            drinking.addAll(Arrays.asList(values));
            return this;
        }

        public Builder acceptKidsStance(Lifestyle.WantsKids... values) {
            kids.addAll(Arrays.asList(values));
            return this;
        }

        public Builder acceptLookingFor(Lifestyle.LookingFor... values) {
            lookingFor.addAll(Arrays.asList(values));
            return this;
        }

        public Builder requireEducation(Lifestyle.Education... values) {
            education.addAll(Arrays.asList(values));
            return this;
        }

        public Builder heightRange(Integer min, Integer max) {
            this.minHeight = min;
            this.maxHeight = max;
            return this;
        }

        public Builder maxAgeDifference(int years) {
            this.maxAgeDiff = years;
            return this;
        }

        public Dealbreakers build() {
            return new Dealbreakers(
                smoking, drinking, kids, lookingFor, education,
                minHeight, maxHeight, maxAgeDiff
            );
        }
    }

    /**
     * Check if a set-based dealbreaker is active.
     */
    public boolean hasSmokingDealbreaker() {
        return !acceptableSmoking.isEmpty();
    }

    public boolean hasDrinkingDealbreaker() {
        return !acceptableDrinking.isEmpty();
    }

    public boolean hasKidsDealbreaker() {
        return !acceptableKidsStance.isEmpty();
    }

    public boolean hasLookingForDealbreaker() {
        return !acceptableLookingFor.isEmpty();
    }

    public boolean hasEducationDealbreaker() {
        return !minimumEducation.isEmpty();
    }

    public boolean hasHeightDealbreaker() {
        return minHeightCm != null || maxHeightCm != null;
    }

    public boolean hasAgeDealbreaker() {
        return maxAgeDifference != null;
    }
}
```

### 2.3 User Class Extensions

```java
// Add to User.java

// === New Lifestyle Fields ===
private Lifestyle.Smoking smoking;
private Lifestyle.Drinking drinking;
private Lifestyle.WantsKids wantsKids;
private Lifestyle.LookingFor lookingFor;
private Lifestyle.Education education;
private Integer heightCm;  // in centimeters

// === Dealbreakers ===
private Dealbreakers dealbreakers;

// === Getters ===
public Lifestyle.Smoking getSmoking() { return smoking; }
public Lifestyle.Drinking getDrinking() { return drinking; }
public Lifestyle.WantsKids getWantsKids() { return wantsKids; }
public Lifestyle.LookingFor getLookingFor() { return lookingFor; }
public Lifestyle.Education getEducation() { return education; }
public Integer getHeightCm() { return heightCm; }
public Dealbreakers getDealbreakers() {
    return dealbreakers != null ? dealbreakers : Dealbreakers.none();
}

// === Setters ===
public void setSmoking(Lifestyle.Smoking smoking) {
    this.smoking = smoking;
    touch();
}

public void setDrinking(Lifestyle.Drinking drinking) {
    this.drinking = drinking;
    touch();
}

public void setWantsKids(Lifestyle.WantsKids wantsKids) {
    this.wantsKids = wantsKids;
    touch();
}

public void setLookingFor(Lifestyle.LookingFor lookingFor) {
    this.lookingFor = lookingFor;
    touch();
}

public void setEducation(Lifestyle.Education education) {
    this.education = education;
    touch();
}

public void setHeightCm(Integer heightCm) {
    if (heightCm != null && (heightCm < 100 || heightCm > 250)) {
        throw new IllegalArgumentException("Height must be 100-250 cm");
    }
    this.heightCm = heightCm;
    touch();
}

public void setDealbreakers(Dealbreakers dealbreakers) {
    this.dealbreakers = dealbreakers;
    touch();
}

// === Update full constructor to include new fields ===
// (See implementation steps)
```

---

## 3. Dealbreaker Evaluation Service

```java
// core/DealbreakersEvaluator.java
public class DealbreakersEvaluator {

    /**
     * Check if candidate passes all of seeker's dealbreakers.
     *
     * @param seeker The user looking for matches
     * @param candidate The potential match
     * @return true if candidate passes all dealbreakers, false if any fails
     */
    public boolean passes(User seeker, User candidate) {
        Dealbreakers db = seeker.getDealbreakers();

        // Check smoking dealbreaker
        if (db.hasSmokingDealbreaker()) {
            if (candidate.getSmoking() == null ||
                !db.acceptableSmoking().contains(candidate.getSmoking())) {
                return false;
            }
        }

        // Check drinking dealbreaker
        if (db.hasDrinkingDealbreaker()) {
            if (candidate.getDrinking() == null ||
                !db.acceptableDrinking().contains(candidate.getDrinking())) {
                return false;
            }
        }

        // Check kids stance dealbreaker
        if (db.hasKidsDealbreaker()) {
            if (candidate.getWantsKids() == null ||
                !db.acceptableKidsStance().contains(candidate.getWantsKids())) {
                return false;
            }
        }

        // Check looking for dealbreaker
        if (db.hasLookingForDealbreaker()) {
            if (candidate.getLookingFor() == null ||
                !db.acceptableLookingFor().contains(candidate.getLookingFor())) {
                return false;
            }
        }

        // Check education dealbreaker
        if (db.hasEducationDealbreaker()) {
            if (candidate.getEducation() == null ||
                !db.minimumEducation().contains(candidate.getEducation())) {
                return false;
            }
        }

        // Check height dealbreaker
        if (db.hasHeightDealbreaker() && candidate.getHeightCm() != null) {
            Integer candidateHeight = candidate.getHeightCm();
            if (db.minHeightCm() != null && candidateHeight < db.minHeightCm()) {
                return false;
            }
            if (db.maxHeightCm() != null && candidateHeight > db.maxHeightCm()) {
                return false;
            }
        }

        // Check age difference dealbreaker
        if (db.hasAgeDealbreaker()) {
            int seekerAge = seeker.getAge();
            int candidateAge = candidate.getAge();
            int ageDiff = Math.abs(seekerAge - candidateAge);
            if (ageDiff > db.maxAgeDifference()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get a list of which dealbreakers a candidate fails (for debugging/display).
     */
    public List<String> getFailedDealbreakers(User seeker, User candidate) {
        List<String> failures = new ArrayList<>();
        Dealbreakers db = seeker.getDealbreakers();

        if (db.hasSmokingDealbreaker()) {
            if (candidate.getSmoking() == null) {
                failures.add("Smoking status not specified");
            } else if (!db.acceptableSmoking().contains(candidate.getSmoking())) {
                failures.add("Smoking: " + candidate.getSmoking().getDisplayName());
            }
        }

        if (db.hasDrinkingDealbreaker()) {
            if (candidate.getDrinking() == null) {
                failures.add("Drinking status not specified");
            } else if (!db.acceptableDrinking().contains(candidate.getDrinking())) {
                failures.add("Drinking: " + candidate.getDrinking().getDisplayName());
            }
        }

        // ... similar for other dealbreakers ...

        return failures;
    }
}
```

---

## 4. CandidateFinder Integration

```java
// Update CandidateFinder.java

public class CandidateFinder implements CandidateFinderService {

    private final DealbreakersEvaluator dealbreakersEvaluator;

    public CandidateFinder() {
        this.dealbreakersEvaluator = new DealbreakersEvaluator();
    }

    @Override
    public List<User> findCandidates(User seeker, List<User> allActive, Set<UUID> alreadyInteracted) {
        return allActive.stream()
                .filter(candidate -> !candidate.getId().equals(seeker.getId()))
                .filter(candidate -> candidate.getState() == User.State.ACTIVE)
                .filter(candidate -> !alreadyInteracted.contains(candidate.getId()))
                .filter(candidate -> hasMatchingGenderPreferences(seeker, candidate))
                .filter(candidate -> hasMatchingAgePreferences(seeker, candidate))
                .filter(candidate -> isWithinDistance(seeker, candidate))
                .filter(candidate -> dealbreakersEvaluator.passes(seeker, candidate))  // NEW
                .sorted(Comparator.comparingDouble(c -> distanceTo(seeker, c)))
                .collect(Collectors.toList());
    }

    // ... rest unchanged ...
}
```

---

## 5. Database Schema Changes

```sql
-- Add lifestyle columns to users table
ALTER TABLE users ADD COLUMN smoking VARCHAR(20);
ALTER TABLE users ADD COLUMN drinking VARCHAR(20);
ALTER TABLE users ADD COLUMN wants_kids VARCHAR(20);
ALTER TABLE users ADD COLUMN looking_for VARCHAR(20);
ALTER TABLE users ADD COLUMN education VARCHAR(20);
ALTER TABLE users ADD COLUMN height_cm INT;

-- Dealbreakers stored as comma-separated values (simple approach)
-- Alternative: separate dealbreakers table
ALTER TABLE users ADD COLUMN db_smoking VARCHAR(100);      -- e.g., "NEVER,SOMETIMES"
ALTER TABLE users ADD COLUMN db_drinking VARCHAR(100);
ALTER TABLE users ADD COLUMN db_wants_kids VARCHAR(100);
ALTER TABLE users ADD COLUMN db_looking_for VARCHAR(100);
ALTER TABLE users ADD COLUMN db_education VARCHAR(200);
ALTER TABLE users ADD COLUMN db_min_height_cm INT;
ALTER TABLE users ADD COLUMN db_max_height_cm INT;
ALTER TABLE users ADD COLUMN db_max_age_diff INT;

-- Index for common queries (if needed later)
CREATE INDEX idx_users_smoking ON users(smoking);
CREATE INDEX idx_users_looking_for ON users(looking_for);
```

---

## 6. H2UserStorage Updates

```java
// Update H2UserStorage.java

// In mapRowToUser():
private User mapRowToUser(ResultSet rs) throws SQLException {
    // ... existing field mappings ...

    // Lifestyle fields
    String smokingStr = rs.getString("smoking");
    Lifestyle.Smoking smoking = smokingStr != null
        ? Lifestyle.Smoking.valueOf(smokingStr) : null;

    String drinkingStr = rs.getString("drinking");
    Lifestyle.Drinking drinking = drinkingStr != null
        ? Lifestyle.Drinking.valueOf(drinkingStr) : null;

    String wantsKidsStr = rs.getString("wants_kids");
    Lifestyle.WantsKids wantsKids = wantsKidsStr != null
        ? Lifestyle.WantsKids.valueOf(wantsKidsStr) : null;

    String lookingForStr = rs.getString("looking_for");
    Lifestyle.LookingFor lookingFor = lookingForStr != null
        ? Lifestyle.LookingFor.valueOf(lookingForStr) : null;

    String educationStr = rs.getString("education");
    Lifestyle.Education education = educationStr != null
        ? Lifestyle.Education.valueOf(educationStr) : null;

    Integer heightCm = rs.getObject("height_cm", Integer.class);

    // Dealbreakers
    Dealbreakers dealbreakers = mapDealbreakers(rs);

    // Create user with all fields...
}

private Dealbreakers mapDealbreakers(ResultSet rs) throws SQLException {
    Set<Lifestyle.Smoking> dbSmoking = parseEnumSet(
        rs.getString("db_smoking"), Lifestyle.Smoking.class);
    Set<Lifestyle.Drinking> dbDrinking = parseEnumSet(
        rs.getString("db_drinking"), Lifestyle.Drinking.class);
    Set<Lifestyle.WantsKids> dbKids = parseEnumSet(
        rs.getString("db_wants_kids"), Lifestyle.WantsKids.class);
    Set<Lifestyle.LookingFor> dbLookingFor = parseEnumSet(
        rs.getString("db_looking_for"), Lifestyle.LookingFor.class);
    Set<Lifestyle.Education> dbEducation = parseEnumSet(
        rs.getString("db_education"), Lifestyle.Education.class);

    Integer dbMinHeight = rs.getObject("db_min_height_cm", Integer.class);
    Integer dbMaxHeight = rs.getObject("db_max_height_cm", Integer.class);
    Integer dbMaxAgeDiff = rs.getObject("db_max_age_diff", Integer.class);

    return new Dealbreakers(
        dbSmoking, dbDrinking, dbKids, dbLookingFor, dbEducation,
        dbMinHeight, dbMaxHeight, dbMaxAgeDiff
    );
}

private <E extends Enum<E>> Set<E> parseEnumSet(String csv, Class<E> enumClass) {
    if (csv == null || csv.isBlank()) {
        return Set.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> Enum.valueOf(enumClass, s))
        .collect(Collectors.toSet());
}

private String serializeEnumSet(Set<? extends Enum<?>> values) {
    if (values == null || values.isEmpty()) {
        return null;
    }
    return values.stream()
        .map(Enum::name)
        .collect(Collectors.joining(","));
}

// In save() - add parameters for new fields
// ... update MERGE statement to include all new columns ...
```

---

## 7. Console UI Changes

### 7.1 Profile Completion - Lifestyle Fields
```
═══════════════════════════════════════
         COMPLETE YOUR PROFILE
═══════════════════════════════════════

  LIFESTYLE (optional, helps with matching)
  ─────────────────────────────────────

  Height (in cm, e.g., 175): _____

  Smoking habits:
    1. Never
    2. Sometimes
    3. Regularly
    0. Skip
  Your choice: _

  Drinking habits:
    1. Never
    2. Socially
    3. Regularly
    0. Skip
  Your choice: _

  Do you want kids?
    1. Don't want
    2. Open to it
    3. Want someday
    4. Already have kids
    0. Skip
  Your choice: _

  What are you looking for?
    1. Something casual
    2. Short-term dating
    3. Long-term relationship
    4. Marriage
    5. Not sure yet
    0. Skip
  Your choice: _
```

### 7.2 Dealbreakers Menu
```
═══════════════════════════════════════
         SET YOUR DEALBREAKERS
═══════════════════════════════════════

  Dealbreakers are HARD filters. People who
  don't match will NEVER appear in your feed.

  Current dealbreakers:
    - Smoking: Must be Never or Sometimes
    - Max age difference: 5 years
  ─────────────────────────────────────

  1. Set smoking dealbreaker
  2. Set drinking dealbreaker
  3. Set kids stance dealbreaker
  4. Set relationship goal dealbreaker
  5. Set height range
  6. Set max age difference
  7. Clear all dealbreakers
  0. Done

  Your choice: _
```

### 7.3 Setting a Specific Dealbreaker
```
═══════════════════════════════════════
    SMOKING DEALBREAKER
═══════════════════════════════════════

  Which smoking habits do you accept?
  (Select all that apply, comma-separated)

    1. Never
    2. Sometimes
    3. Regularly

  Enter choices (e.g., 1,2) or 0 to clear: 1,2

  ✓ Dealbreaker set: Accept Never, Sometimes
```

---

## 8. Implementation Steps

### Step 1: Create Lifestyle Enums (30 min)
1. Create `core/Lifestyle.java` with all enums
2. Add display names for UI

### Step 2: Create Dealbreakers Record (1 hour)
1. Create `core/Dealbreakers.java` with builder
2. Add validation logic
3. Add convenience methods

### Step 3: Update User Class (1-2 hours)
1. Add lifestyle fields with getters/setters
2. Add dealbreakers field
3. Update full constructor
4. Update `isComplete()` if lifestyle is required (probably not)

### Step 4: Create DealbreakersEvaluator (1 hour)
1. Create `core/DealbreakersEvaluator.java`
2. Implement `passes()` method
3. Implement `getFailedDealbreakers()` for debugging

### Step 5: Update CandidateFinder (30 min)
1. Add dealbreakers evaluation to filter chain
2. Test that filter is applied correctly

### Step 6: Update Database Schema (30 min)
1. Add ALTER TABLE statements to `DatabaseManager`
2. Test schema migration

### Step 7: Update H2UserStorage (2 hours)
1. Update MERGE statement with new columns
2. Update mapRowToUser() with new fields
3. Add serialization helpers for enum sets

### Step 8: Console UI (2-3 hours)
1. Add lifestyle questions to profile completion
2. Create dealbreakers menu
3. Implement dealbreaker setting flow

### Step 9: Testing (2 hours)
1. Unit tests for Dealbreakers validation
2. Unit tests for DealbreakersEvaluator
3. Integration tests for storage
4. Manual testing of full flow

---

## 9. Test Plan

### 9.1 Unit Tests

| Test | Description |
|------|-------------|
| `DealbreakersTest.validatesHeightRange` | minHeight <= maxHeight |
| `DealbreakersTest.builderCreatesValid` | Builder produces valid object |
| `DealbreakersTest.noneAcceptsAll` | `Dealbreakers.none()` has no filters |
| `DealbreakersEvaluatorTest.passesSmoking` | Accepts matching smoking status |
| `DealbreakersEvaluatorTest.failsSmoking` | Rejects non-matching smoking |
| `DealbreakersEvaluatorTest.nullFieldFails` | Missing field fails dealbreaker |
| `DealbreakersEvaluatorTest.noDealbreakersPassesAll` | No dealbreakers = accept all |
| `DealbreakersEvaluatorTest.heightRange` | Height within/outside range |
| `DealbreakersEvaluatorTest.ageDifference` | Age diff within/outside limit |

### 9.2 Integration Tests

| Test | Description |
|------|-------------|
| `CandidateFinderTest.appliesDealbreakers` | Filtered candidates respect dealbreakers |
| `H2UserStorageTest.savesLifestyleFields` | Lifestyle roundtrip |
| `H2UserStorageTest.savesDealbreakers` | Dealbreakers roundtrip |

---

## 10. Success Criteria

- [ ] All 5 lifestyle enums created with display names
- [ ] User can set lifestyle fields on profile
- [ ] User can set dealbreakers via menu
- [ ] CandidateFinder respects dealbreakers (one-way filtering)
- [ ] Dealbreakers persist between restarts
- [ ] Empty dealbreakers = no filtering
- [ ] Missing candidate fields fail dealbreakers (safe default)
- [ ] All new code in `core/` with zero framework imports
- [ ] All tests pass

---

## 11. Design Decisions & Trade-offs

### Decision 1: Missing Fields Fail Dealbreakers
If a user hasn't set their smoking status and someone has a smoking dealbreaker, they **won't** see that person. This encourages profile completion.

**Alternative considered:** Treat missing as "passes." Rejected because it defeats the purpose of dealbreakers.

### Decision 2: Dealbreakers are One-Way
My dealbreaker against smokers doesn't affect whether smokers see me. This maximizes pool size while respecting individual preferences.

**Alternative considered:** Symmetric dealbreakers. Rejected because it's too restrictive.

### Decision 3: CSV Storage for Enum Sets
Store dealbreaker enum sets as comma-separated strings (e.g., "NEVER,SOMETIMES").

**Alternative considered:** Separate `user_dealbreakers` junction table. More normalized but overkill for Phase 0.5.

---

## 12. Future Enhancements (Not in Scope)

- Dealbreaker suggestions based on unmatch patterns
- "Soft" dealbreakers that reduce score but don't eliminate
- Custom dealbreakers beyond predefined fields
- A/B testing dealbreaker effectiveness
