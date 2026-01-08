# Feature 20: Profile Preview

> [!NOTE]
> **Status:** ✅ IMPLEMENTED & VERIFIED
> **Date:** 2026-01-08
> **Verification:** `PropertiesPreviewServiceTest` passing (12 tests)

**Priority:** Medium
**Complexity:** Low
**Dependencies:** None

---

## Overview

Allow users to see their own profile exactly as other users see it, including how their match compatibility would appear. Helps users optimize their profiles for better matches.

---

## User Stories

1. As a user, I want to see how my profile looks to others
2. As a user, I want to see sample compatibility scores with hypothetical matches
3. As a user, I want to identify missing or weak parts of my profile

---

## Proposed Changes

### Core Layer

#### [NEW] `ProfilePreviewService.java`
```java
public class ProfilePreviewService {
    private final MatchQualityService matchQualityService;

    public record ProfilePreview(
        User user,
        ProfileCompleteness completeness,
        List<String> improvementTips,
        int estimatedCompatibility  // Average with active users
    ) {}

    public record ProfileCompleteness(
        int percentage,
        List<String> filledFields,
        List<String> missingFields
    ) {}

    public ProfilePreview generatePreview(User user);
    public List<String> generateTips(User user);
    private int estimateAverageCompatibility(User user);
}
```

#### Completeness Calculation

```java
public ProfileCompleteness calculateCompleteness(User user) {
    List<String> filled = new ArrayList<>();
    List<String> missing = new ArrayList<>();

    // Required fields
    checkField("Name", user.getName(), filled, missing);
    checkField("Bio", user.getBio(), filled, missing);
    checkField("Birth Date", user.getBirthDate(), filled, missing);
    checkField("Gender", user.getGender(), filled, missing);
    checkField("Interested In", user.getInterestedIn(), filled, missing);
    checkField("Photo", !user.getPhotoUrls().isEmpty(), filled, missing);

    // Lifestyle fields (optional but encouraged)
    checkField("Height", user.getHeightCm(), filled, missing);
    checkField("Smoking", user.getSmoking(), filled, missing);
    checkField("Drinking", user.getDrinking(), filled, missing);
    checkField("Kids Stance", user.getWantsKids(), filled, missing);
    checkField("Looking For", user.getLookingFor(), filled, missing);

    int total = filled.size() + missing.size();
    int percentage = (filled.size() * 100) / total;

    return new ProfileCompleteness(percentage, filled, missing);
}
```

#### Tips Generation

```java
public List<String> generateTips(User user) {
    List<String> tips = new ArrayList<>();

    // Bio tips
    if (user.getBio() == null || user.getBio().length() < 50) {
        tips.add("💡 Add more to your bio - profiles with 100+ char bios get 2x more likes");
    }

    // Photo tips
    if (user.getPhotoUrls().size() < 2) {
        tips.add("📸 Add a second photo - users with 2 photos get 40% more matches");
    }

    // Lifestyle tips
    if (user.getLookingFor() == null) {
        tips.add("💝 Share what you're looking for - it helps find compatible matches");
    }

    // Location tips
    if (user.getMaxDistanceKm() < 10) {
        tips.add("📍 Consider expanding your distance - more options nearby");
    }

    return tips;
}
```

---

### CLI Changes

#### [MODIFY] `Main.java`

**New Menu Option:**
```
  11. 👤 Preview my profile
```

**Profile Preview Screen:**
```java
private static void previewProfile() {
    if (currentUser == null) {
        logger.info(PLEASE_SELECT_USER);
        return;
    }

    ProfilePreview preview = profilePreviewService.generatePreview(currentUser);

    logger.info("\n" + SEPARATOR_LINE);
    logger.info("      👤 YOUR PROFILE PREVIEW");
    logger.info(SEPARATOR_LINE);
    logger.info("");
    logger.info("  This is how others see you:");
    logger.info("");

    // Card display (same as candidate card)
    logger.info("┌─────────────────────────────────────────┐");
    logger.info("│ 💝 {}, {} years old", currentUser.getName(), currentUser.getAge());
    logger.info("│ 📍 Location: {}, {}", currentUser.getLat(), currentUser.getLon());
    logger.info("│ 📝 {}", currentUser.getBio() != null ? currentUser.getBio() : "(no bio)");
    if (currentUser.getLookingFor() != null) {
        logger.info("│ 💭 {}", currentUser.getLookingFor().getDisplayName());
    }
    logger.info("└─────────────────────────────────────────┘");

    // Completeness section
    ProfileCompleteness comp = preview.completeness();
    logger.info("");
    logger.info("  📊 PROFILE COMPLETENESS: {}%", comp.percentage());
    logger.info("  " + renderProgressBar(comp.percentage() / 100.0, 20));

    if (!comp.missingFields().isEmpty()) {
        logger.info("");
        logger.info("  ⚠️  Missing fields:");
        comp.missingFields().forEach(f -> logger.info("    • {}", f));
    }

    // Tips section
    if (!preview.improvementTips().isEmpty()) {
        logger.info("");
        logger.info("  💡 IMPROVEMENT TIPS:");
        preview.improvementTips().forEach(tip -> logger.info("    {}", tip));
    }

    // Estimated compatibility
    logger.info("");
    logger.info("  🎯 Estimated avg. compatibility: {}%", preview.estimatedCompatibility());

    logger.info("");
    readLine("  [Press Enter to return to menu]");
}
```

---

## Profile Preview Display

```
═══════════════════════════════════════
      👤 YOUR PROFILE PREVIEW
═══════════════════════════════════════

  This is how others see you:

┌─────────────────────────────────────────┐
│ 💝 Alex, 28 years old
│ 📍 Location: Tel Aviv
│ 📝 Coffee lover and weekend hiker
│ 💭 Looking for a long-term relationship
└─────────────────────────────────────────┘

  📊 PROFILE COMPLETENESS: 75%
  ████████████████░░░░

  ⚠️  Missing fields:
    • Height
    • Education

  💡 IMPROVEMENT TIPS:
    📸 Add a second photo - 40% more matches
    💡 Share your smoking/drinking preferences

  🎯 Estimated avg. compatibility: 68%

  [Press Enter to return to menu]
```

---

## Verification Plan

### Unit Tests

Create `ProfilePreviewServiceTest.java`:
- `calculateCompleteness_fullProfile_returns100`
- `calculateCompleteness_missingBio_listsCorrectly`
- `generateTips_shortBio_suggestsLonger`
- `generateTips_onePhoto_suggestsSecond`

### Manual CLI Test

1. Create user with minimal profile
2. Select "Preview my profile"
3. Verify missing fields are listed
4. Complete profile fields
5. Preview again → Completeness should increase

---

## File Summary

| File | Action | Lines |
|------|--------|-------|
| `ProfilePreviewService.java` | NEW | ~100 |
| `ServiceRegistry.java` | MODIFY | +3 |
| `ServiceRegistryBuilder.java` | MODIFY | +3 |
| `Main.java` | MODIFY | +60 |
| `ProfilePreviewServiceTest.java` | NEW | ~60 |

**Total: ~230 lines**
