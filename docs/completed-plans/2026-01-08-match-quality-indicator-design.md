# Match Quality Indicator - Design Document

**Date:** 2026-01-08
**Status:** ✅ COMPLETE
**Completed:** 2026-01-10
**Priority:** P2
**Complexity:** Medium
**Dependencies:** Dealbreakers (for lifestyle alignment), Interests (PRD 0.5 Part 2.1, optional)

---

## 1. Overview

### Purpose
When viewing a match, show the user **why** they matched and how compatible they are. This:
- Provides conversation starters ("I see we both love hiking!")
- Helps users prioritize which matches to pursue
- Increases engagement with meaningful matches
- Reduces time wasted on low-compatibility matches

### What We Show
1. **Compatibility Score** (0-100) - Overall compatibility
2. **Highlights** - Human-readable reasons why you matched
3. **Breakdown** - Individual factors contributing to score

### Example Output
```
═══════════════════════════════════════
  MATCH: Alice & Bob
  Compatibility: 87%  ⭐⭐⭐⭐
═══════════════════════════════════════

  ✨ WHY YOU MATCHED
  ─────────────────────────────────────
  • You're both looking for a long-term relationship
  • You share 3 interests: Hiking, Travel, Photography
  • Lives just 2km away
  • Both liked each other within 24 hours

  📊 COMPATIBILITY BREAKDOWN
  ─────────────────────────────────────
  Distance:           ████████████ 95%
  Shared interests:   ████████░░░░ 60%
  Lifestyle match:    ████████████ 100%
  Response speed:     ██████░░░░░░ 50%
```

---

## 2. Domain Model

### 2.1 MatchQuality Record

```java
// core/MatchQuality.java
public record MatchQuality(
    String matchId,
    UUID perspectiveUserId,    // Whose perspective (for directional metrics)
    UUID otherUserId,
    Instant computedAt,

    // === Individual Scores (0.0 - 1.0) ===
    double distanceScore,
    double ageScore,
    double interestScore,
    double lifestyleScore,
    double responseScore,      // How quickly mutual like happened

    // === Raw Data ===
    double distanceKm,
    int ageDifference,
    List<String> sharedInterests,
    List<String> lifestyleMatches,    // e.g., "Both want kids someday"
    Duration timeBetweenLikes,

    // === Aggregates ===
    int compatibilityScore,           // 0-100
    List<String> highlights           // Human-readable highlights
) {
    public MatchQuality {
        Objects.requireNonNull(matchId);
        Objects.requireNonNull(perspectiveUserId);
        Objects.requireNonNull(otherUserId);
        Objects.requireNonNull(computedAt);

        // Validate scores are 0.0-1.0
        validateScore(distanceScore, "distanceScore");
        validateScore(ageScore, "ageScore");
        validateScore(interestScore, "interestScore");
        validateScore(lifestyleScore, "lifestyleScore");
        validateScore(responseScore, "responseScore");

        // Validate compatibility is 0-100
        if (compatibilityScore < 0 || compatibilityScore > 100) {
            throw new IllegalArgumentException("compatibilityScore must be 0-100");
        }

        // Defensive copies
        sharedInterests = sharedInterests == null ? List.of() : List.copyOf(sharedInterests);
        lifestyleMatches = lifestyleMatches == null ? List.of() : List.copyOf(lifestyleMatches);
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }

    private static void validateScore(double score, String name) {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException(name + " must be 0.0-1.0");
        }
    }

    /**
     * Get star rating (1-5 stars based on compatibility).
     */
    public int getStarRating() {
        if (compatibilityScore >= 90) return 5;
        if (compatibilityScore >= 75) return 4;
        if (compatibilityScore >= 60) return 3;
        if (compatibilityScore >= 40) return 2;
        return 1;
    }

    /**
     * Get compatibility label.
     */
    public String getCompatibilityLabel() {
        if (compatibilityScore >= 90) return "Excellent Match";
        if (compatibilityScore >= 75) return "Great Match";
        if (compatibilityScore >= 60) return "Good Match";
        if (compatibilityScore >= 40) return "Fair Match";
        return "Low Compatibility";
    }
}
```

### 2.2 Score Weights Configuration

```java
// core/MatchQualityConfig.java
public record MatchQualityConfig(
    double distanceWeight,      // How much distance matters
    double ageWeight,           // How much age alignment matters
    double interestWeight,      // How much shared interests matter
    double lifestyleWeight,     // How much lifestyle alignment matters
    double responseWeight       // How much response speed matters
) {
    public MatchQualityConfig {
        // Weights should sum to 1.0 (normalized)
        double total = distanceWeight + ageWeight + interestWeight +
                       lifestyleWeight + responseWeight;
        if (Math.abs(total - 1.0) > 0.001) {
            throw new IllegalArgumentException("Weights must sum to 1.0, got: " + total);
        }
    }

    /**
     * Default weights emphasizing interests and lifestyle.
     */
    public static MatchQualityConfig defaults() {
        return new MatchQualityConfig(
            0.15,   // distance - nice but not critical
            0.10,   // age - already filtered, less important
            0.30,   // interests - very important for conversation
            0.30,   // lifestyle - very important for long-term
            0.15    // response speed - indicates mutual enthusiasm
        );
    }

    /**
     * Weights for users who prioritize proximity.
     */
    public static MatchQualityConfig proximityFocused() {
        return new MatchQualityConfig(
            0.35,   // distance
            0.10,   // age
            0.20,   // interests
            0.25,   // lifestyle
            0.10    // response
        );
    }
}
```

---

## 3. Match Quality Service

```java
// core/MatchQualityService.java
public class MatchQualityService {

    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final MatchQualityConfig config;

    public MatchQualityService(UserStorage userStorage, LikeStorage likeStorage) {
        this(userStorage, likeStorage, MatchQualityConfig.defaults());
    }

    public MatchQualityService(UserStorage userStorage, LikeStorage likeStorage,
                               MatchQualityConfig config) {
        this.userStorage = Objects.requireNonNull(userStorage);
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Compute match quality from one user's perspective.
     */
    public MatchQuality computeQuality(Match match, UUID perspectiveUserId) {
        UUID otherUserId = match.getOtherUser(perspectiveUserId);

        User me = userStorage.get(perspectiveUserId);
        User them = userStorage.get(otherUserId);

        if (me == null || them == null) {
            throw new IllegalArgumentException("User not found");
        }

        // === Calculate Individual Scores ===

        // Distance Score
        double distanceKm = GeoUtils.distanceKm(
            me.getLat(), me.getLon(),
            them.getLat(), them.getLon()
        );
        double distanceScore = calculateDistanceScore(distanceKm, me.getMaxDistanceKm());

        // Age Score
        int ageDiff = Math.abs(me.getAge() - them.getAge());
        double ageScore = calculateAgeScore(ageDiff, me, them);

        // Interest Score
        List<String> sharedInterests = findSharedInterests(me, them);
        double interestScore = calculateInterestScore(sharedInterests, me, them);

        // Lifestyle Score
        List<String> lifestyleMatches = findLifestyleMatches(me, them);
        double lifestyleScore = calculateLifestyleScore(me, them);

        // Response Score
        Duration timeBetweenLikes = calculateTimeBetweenLikes(
            perspectiveUserId, otherUserId, match
        );
        double responseScore = calculateResponseScore(timeBetweenLikes);

        // === Calculate Overall Score ===
        double weightedScore =
            distanceScore * config.distanceWeight() +
            ageScore * config.ageWeight() +
            interestScore * config.interestWeight() +
            lifestyleScore * config.lifestyleWeight() +
            responseScore * config.responseWeight();

        int compatibilityScore = (int) Math.round(weightedScore * 100);

        // === Generate Highlights ===
        List<String> highlights = generateHighlights(
            me, them, distanceKm, sharedInterests, lifestyleMatches, timeBetweenLikes
        );

        return new MatchQuality(
            match.getId(),
            perspectiveUserId,
            otherUserId,
            Instant.now(),
            distanceScore,
            ageScore,
            interestScore,
            lifestyleScore,
            responseScore,
            distanceKm,
            ageDiff,
            sharedInterests,
            lifestyleMatches,
            timeBetweenLikes,
            compatibilityScore,
            highlights
        );
    }

    // === Score Calculation Methods ===

    private double calculateDistanceScore(double distanceKm, int maxDistanceKm) {
        if (distanceKm <= 1) return 1.0;  // Very close
        if (distanceKm >= maxDistanceKm) return 0.0;  // At limit

        // Linear decay from 1.0 to 0.0
        return 1.0 - (distanceKm / maxDistanceKm);
    }

    private double calculateAgeScore(int ageDiff, User me, User them) {
        // Perfect score if within 2 years
        if (ageDiff <= 2) return 1.0;

        // Calculate how well they fit in each other's ranges
        int myRange = me.getMaxAge() - me.getMinAge();
        int theirRange = them.getMaxAge() - them.getMinAge();
        int avgRange = (myRange + theirRange) / 2;

        if (avgRange == 0) return 1.0;  // No range = no penalty

        // Score based on how close to ideal vs range
        double normalizedDiff = (double) ageDiff / avgRange;
        return Math.max(0.0, 1.0 - normalizedDiff);
    }

    private double calculateInterestScore(List<String> shared, User me, User them) {
        // If neither has interests, neutral score
        Set<String> myInterests = getInterestNames(me);
        Set<String> theirInterests = getInterestNames(them);

        if (myInterests.isEmpty() && theirInterests.isEmpty()) {
            return 0.5;  // Unknown = neutral
        }

        if (myInterests.isEmpty() || theirInterests.isEmpty()) {
            return 0.3;  // One has interests, other doesn't
        }

        // Jaccard similarity: intersection / union
        Set<String> union = new HashSet<>(myInterests);
        union.addAll(theirInterests);

        return (double) shared.size() / union.size();
    }

    private Set<String> getInterestNames(User user) {
        // TODO: When Interests feature is implemented, use actual interests
        // For now, return empty set
        return Set.of();
    }

    private List<String> findSharedInterests(User me, User them) {
        Set<String> myInterests = getInterestNames(me);
        Set<String> theirInterests = getInterestNames(them);

        Set<String> shared = new HashSet<>(myInterests);
        shared.retainAll(theirInterests);

        return new ArrayList<>(shared);
    }

    private double calculateLifestyleScore(User me, User them) {
        int matches = 0;
        int total = 0;

        // Smoking
        if (me.getSmoking() != null && them.getSmoking() != null) {
            total++;
            if (me.getSmoking() == them.getSmoking()) matches++;
        }

        // Drinking
        if (me.getDrinking() != null && them.getDrinking() != null) {
            total++;
            if (me.getDrinking() == them.getDrinking()) matches++;
        }

        // Wants Kids
        if (me.getWantsKids() != null && them.getWantsKids() != null) {
            total++;
            if (areKidsStancesCompatible(me.getWantsKids(), them.getWantsKids())) {
                matches++;
            }
        }

        // Looking For
        if (me.getLookingFor() != null && them.getLookingFor() != null) {
            total++;
            if (me.getLookingFor() == them.getLookingFor()) matches++;
        }

        // If no lifestyle data, return neutral
        if (total == 0) return 0.5;

        return (double) matches / total;
    }

    private boolean areKidsStancesCompatible(Lifestyle.WantsKids a, Lifestyle.WantsKids b) {
        // Compatible if same, or if one is OPEN
        if (a == b) return true;
        if (a == Lifestyle.WantsKids.OPEN || b == Lifestyle.WantsKids.OPEN) return true;
        // SOMEDAY and HAS_KIDS are compatible
        if ((a == Lifestyle.WantsKids.SOMEDAY && b == Lifestyle.WantsKids.HAS_KIDS) ||
            (a == Lifestyle.WantsKids.HAS_KIDS && b == Lifestyle.WantsKids.SOMEDAY)) {
            return true;
        }
        return false;
    }

    private List<String> findLifestyleMatches(User me, User them) {
        List<String> matches = new ArrayList<>();

        if (me.getSmoking() != null && me.getSmoking() == them.getSmoking()) {
            if (me.getSmoking() == Lifestyle.Smoking.NEVER) {
                matches.add("Both non-smokers");
            }
        }

        if (me.getDrinking() != null && me.getDrinking() == them.getDrinking()) {
            matches.add("Same drinking habits");
        }

        if (me.getWantsKids() != null && them.getWantsKids() != null) {
            if (areKidsStancesCompatible(me.getWantsKids(), them.getWantsKids())) {
                matches.add("Compatible on kids");
            }
        }

        if (me.getLookingFor() != null && me.getLookingFor() == them.getLookingFor()) {
            matches.add("Both looking for " + me.getLookingFor().getDisplayName().toLowerCase());
        }

        return matches;
    }

    private Duration calculateTimeBetweenLikes(UUID userId, UUID otherId, Match match) {
        // Get the two likes that created this match
        Optional<Like> myLike = likeStorage.getLike(userId, otherId);
        Optional<Like> theirLike = likeStorage.getLike(otherId, userId);

        if (myLike.isEmpty() || theirLike.isEmpty()) {
            return Duration.ZERO;  // Shouldn't happen, but handle gracefully
        }

        Instant first = myLike.get().createdAt().isBefore(theirLike.get().createdAt())
            ? myLike.get().createdAt() : theirLike.get().createdAt();
        Instant second = myLike.get().createdAt().isAfter(theirLike.get().createdAt())
            ? myLike.get().createdAt() : theirLike.get().createdAt();

        return Duration.between(first, second);
    }

    private double calculateResponseScore(Duration timeBetween) {
        if (timeBetween == null || timeBetween.isZero()) {
            return 0.5;  // Unknown
        }

        long hours = timeBetween.toHours();

        // Within 1 hour = excellent
        if (hours < 1) return 1.0;
        // Within 24 hours = great
        if (hours < 24) return 0.9;
        // Within 3 days = good
        if (hours < 72) return 0.7;
        // Within a week = okay
        if (hours < 168) return 0.5;
        // Within a month = low
        if (hours < 720) return 0.3;
        // Longer = very low
        return 0.1;
    }

    // === Highlight Generation ===

    private List<String> generateHighlights(User me, User them, double distanceKm,
                                            List<String> sharedInterests,
                                            List<String> lifestyleMatches,
                                            Duration timeBetween) {
        List<String> highlights = new ArrayList<>();

        // Distance highlight
        if (distanceKm < 5) {
            highlights.add(String.format("Lives nearby (%.1f km away)", distanceKm));
        } else if (distanceKm < 15) {
            highlights.add(String.format("%.0f km away", distanceKm));
        }

        // Interest highlights
        if (!sharedInterests.isEmpty()) {
            if (sharedInterests.size() == 1) {
                highlights.add("You both enjoy " + sharedInterests.get(0));
            } else {
                String interestList = String.join(", ", sharedInterests);
                highlights.add("You share " + sharedInterests.size() + " interests: " + interestList);
            }
        }

        // Lifestyle highlights
        highlights.addAll(lifestyleMatches);

        // Response time highlight
        if (timeBetween != null && timeBetween.toHours() < 24) {
            highlights.add("Quick mutual interest - you both liked each other within a day!");
        }

        // Age highlight
        int ageDiff = Math.abs(me.getAge() - them.getAge());
        if (ageDiff <= 2) {
            highlights.add("Similar age");
        }

        // Limit to top 5 highlights
        if (highlights.size() > 5) {
            highlights = highlights.subList(0, 5);
        }

        return highlights;
    }
}
```

---

## 4. Storage Interface (Optional Caching)

```java
// core/MatchQualityStorage.java
public interface MatchQualityStorage {

    /**
     * Save computed match quality (cache).
     */
    void save(MatchQuality quality);

    /**
     * Get cached quality for a match from a user's perspective.
     */
    Optional<MatchQuality> get(String matchId, UUID perspectiveUserId);

    /**
     * Delete cached quality (when match ends or data changes).
     */
    void delete(String matchId);

    /**
     * Delete all cached qualities for a user (when profile changes).
     */
    void deleteForUser(UUID userId);
}
```

**Note:** Caching is optional for Phase 0.5. Can compute on-demand initially.

---

## 5. Required LikeStorage Addition

```java
// Add to LikeStorage.java
public interface LikeStorage {
    // ... existing methods ...

    /**
     * Get a specific like record.
     */
    Optional<Like> getLike(UUID fromUserId, UUID toUserId);
}
```

---

## 6. Database Schema (Optional Cache)

```sql
-- Optional: Cache computed match quality
CREATE TABLE match_quality_cache (
    id UUID PRIMARY KEY,
    match_id VARCHAR(100) NOT NULL,
    perspective_user_id UUID NOT NULL REFERENCES users(id),
    other_user_id UUID NOT NULL REFERENCES users(id),
    computed_at TIMESTAMP NOT NULL,

    -- Scores
    distance_score DOUBLE NOT NULL,
    age_score DOUBLE NOT NULL,
    interest_score DOUBLE NOT NULL,
    lifestyle_score DOUBLE NOT NULL,
    response_score DOUBLE NOT NULL,

    -- Raw data
    distance_km DOUBLE NOT NULL,
    age_difference INT NOT NULL,
    shared_interests VARCHAR(500),     -- JSON array or comma-separated
    lifestyle_matches VARCHAR(500),    -- JSON array or comma-separated
    time_between_likes_seconds BIGINT,

    -- Aggregates
    compatibility_score INT NOT NULL,
    highlights VARCHAR(1000),          -- JSON array or comma-separated

    UNIQUE (match_id, perspective_user_id)
);

CREATE INDEX idx_mqc_match ON match_quality_cache(match_id);
CREATE INDEX idx_mqc_user ON match_quality_cache(perspective_user_id);
```

---

## 7. Console UI Changes

### 7.1 Match List with Quality Preview
```
═══════════════════════════════════════
         YOUR MATCHES (12)
═══════════════════════════════════════

  1. Alice, 28          ⭐⭐⭐⭐⭐ 94%
     "3 shared interests • 2km away"

  2. Bob, 31            ⭐⭐⭐⭐  78%
     "Both looking for long-term"

  3. Carol, 26          ⭐⭐⭐   62%
     "5km away"

  4. Dave, 29           ⭐⭐     45%
     "Quick mutual like"

───────────────────────────────────────
  Enter number to view details, 0 to back: _
```

### 7.2 Match Detail View
```
═══════════════════════════════════════
        MATCH WITH ALICE
═══════════════════════════════════════

  👤 Alice, 28
  📍 San Francisco (2.3 km away)

  ─────────────────────────────────────
  COMPATIBILITY: 94%  ⭐⭐⭐⭐⭐
  ─────────────────────────────────────

  ✨ WHY YOU MATCHED
  • You share 3 interests: Hiking, Photography, Travel
  • Both looking for a long-term relationship
  • Lives nearby (2.3 km away)
  • Both non-smokers
  • You both liked each other within a day!

  📊 SCORE BREAKDOWN
  ─────────────────────────────────────
  Distance:      ████████████ 95%
  Age match:     ██████████░░ 85%
  Interests:     █████████░░░ 75%
  Lifestyle:     ████████████ 100%
  Response:      ██████████░░ 90%

───────────────────────────────────────
  (U)nmatch  (B)lock  (M)essage  (Back)
  Your choice: _
```

### 7.3 Progress Bar Helper

```java
// In Main.java or a UI helper class
private String renderProgressBar(double score, int width) {
    int filled = (int) Math.round(score * width);
    int empty = width - filled;
    return "█".repeat(filled) + "░".repeat(empty);
}

private String renderStars(int rating) {
    return "⭐".repeat(rating);
}
```

---

## 8. Implementation Steps

### Step 1: Create Data Models (1 hour)
1. Create `core/MatchQuality.java` record
2. Create `core/MatchQualityConfig.java` record
3. Add validation and helper methods

### Step 2: Add LikeStorage Method (30 min)
1. Add `getLike()` to interface
2. Implement in `H2LikeStorage`

### Step 3: Create MatchQualityService (3-4 hours)
1. Create `core/MatchQualityService.java`
2. Implement all score calculation methods
3. Implement highlight generation
4. Handle edge cases (missing data, etc.)

### Step 4: Integration (1 hour)
1. Add to ServiceRegistry
2. Wire dependencies

### Step 5: Console UI (2-3 hours)
1. Update match list to show quality preview
2. Create detailed match quality view
3. Implement progress bar rendering

### Step 6: Testing (2 hours)
1. Unit tests for score calculations
2. Unit tests for highlight generation
3. Integration test for full computation

### Step 7: Optional - Caching (1-2 hours)
1. Create storage interface
2. Implement H2 cache storage
3. Add cache invalidation logic

---

## 9. Test Plan

### 9.1 Unit Tests

| Test | Description |
|------|-------------|
| `MatchQualityTest.validatesScores` | Scores must be 0.0-1.0 |
| `MatchQualityTest.calculatesStarRating` | Correct stars for score ranges |
| `MatchQualityServiceTest.distanceScore` | Close = high, far = low |
| `MatchQualityServiceTest.ageScore` | Similar age = high score |
| `MatchQualityServiceTest.interestScore` | More overlap = higher |
| `MatchQualityServiceTest.lifestyleScore` | Matching lifestyles score higher |
| `MatchQualityServiceTest.responseScore` | Quick response = high score |
| `MatchQualityServiceTest.kidsCompatibility` | OPEN compatible with all |
| `MatchQualityServiceTest.generatesHighlights` | Creates meaningful highlights |
| `MatchQualityServiceTest.handlesNullLifestyle` | Neutral score for missing data |

### 9.2 Integration Tests

| Test | Description |
|------|-------------|
| `MatchQualityIntegrationTest.fullComputation` | End-to-end with real users |
| `H2MatchQualityCacheTest.roundtrip` | Save and retrieve cache |

---

## 10. Success Criteria

- [ ] MatchQuality computed for any match
- [ ] Compatibility score 0-100 based on weighted factors
- [ ] Star rating (1-5) displayed
- [ ] Human-readable highlights generated
- [ ] Score breakdown visible in UI
- [ ] Handles missing lifestyle data gracefully (neutral scores)
- [ ] Match list sorted by compatibility (optional)
- [ ] All new code in `core/` with zero framework imports
- [ ] All tests pass

---

## 11. Design Decisions

### Decision 1: Perspective-Based Quality
Quality is computed from one user's perspective because:
- Distance scoring might differ based on each user's max distance preference
- Highlights can be personalized ("You both..." vs "They also...")

### Decision 2: Configurable Weights
Different users might value different factors. Default weights prioritize interests and lifestyle, but proximity-focused weights are available.

### Decision 3: Graceful Degradation
If users haven't filled in lifestyle/interests, we return neutral scores (0.5) rather than failing. This ensures all matches get a quality score.

### Decision 4: Highlight Limits
Maximum 5 highlights to avoid information overload. Prioritize most meaningful/unique matches.

---

## 12. Dependencies on Other Features

### Required: Dealbreakers Feature
The lifestyle fields (smoking, drinking, wantsKids, lookingFor) come from the Dealbreakers feature. Without it, lifestyle score will always be 0.5.

### Optional: Interests Feature (PRD 0.5 Part 2.1)
If Interests are implemented, the interest score becomes meaningful. Without it, interest score will always be 0.5.

### Dependency Matrix

| Scenario | Distance | Age | Interest | Lifestyle | Response |
|----------|----------|-----|----------|-----------|----------|
| Only base features | ✅ | ✅ | 0.5 | 0.5 | ✅ |
| + Dealbreakers | ✅ | ✅ | 0.5 | ✅ | ✅ |
| + Interests | ✅ | ✅ | ✅ | 0.5 | ✅ |
| + Both | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## 13. Future Enhancements (Not in Scope)

- Machine learning-based compatibility prediction
- "Compatibility tips" - suggestions to improve profile
- Match quality trends over time
- A/B testing different weight configurations
- User-customizable weights
- "Why you might not work" (anti-highlights for transparency)
