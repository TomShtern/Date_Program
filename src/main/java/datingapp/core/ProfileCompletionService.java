package datingapp.core;

import datingapp.core.Preferences.Interest;
import datingapp.core.constants.ScoringConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service for calculating profile completion percentage and generating profile previews. Shows users
 * how their profile appears to others, with completeness scoring and improvement tips.
 *
 * <p>Combines detailed category-based scoring ({@link #calculate(User)}) with simple field-based
 * completeness ({@link #calculateCompleteness(User)}) and preview generation
 * ({@link #generatePreview(User)}).
 */
public final class ProfileCompletionService {

    private final AppConfig config;

    public ProfileCompletionService(AppConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    // ========================================================================
    // Records
    // ========================================================================

    /** Result of detailed completion analysis with category breakdowns. */
    public record CompletionResult(
            int score,
            String tier,
            int filledFields,
            int totalFields,
            List<CategoryBreakdown> breakdown,
            List<String> nextSteps) {

        public CompletionResult {
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("score must be 0-100, got: " + score);
            }
            if (filledFields < 0 || totalFields < 0) {
                throw new IllegalArgumentException("field counts must be non-negative");
            }
            Objects.requireNonNull(tier, "tier cannot be null");
            breakdown = breakdown != null ? List.copyOf(breakdown) : List.of();
            nextSteps = nextSteps != null ? List.copyOf(nextSteps) : List.of();
        }

        public int getPercentage() {
            return score;
        }

        public String getTierLabel() {
            return tier;
        }

        public String getTierEmoji() {
            return tierEmojiForScore(score);
        }

        public String getDisplayString() {
            return score + "% " + tier;
        }
    }

    /** Breakdown of completion by category. */
    public record CategoryBreakdown(String category, int score, List<String> filledItems, List<String> missingItems) {

        public CategoryBreakdown {
            Objects.requireNonNull(category, "category cannot be null");
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("score must be 0-100, got: " + score);
            }
            filledItems = filledItems != null ? List.copyOf(filledItems) : List.of();
            missingItems = missingItems != null ? List.copyOf(missingItems) : List.of();
        }
    }

    /** Result of simple profile completeness calculation. */
    public record ProfileCompleteness(int percentage, List<String> filledFields, List<String> missingFields) {

        public ProfileCompleteness {
            if (percentage < 0 || percentage > 100) {
                throw new IllegalArgumentException("percentage must be 0-100, got: " + percentage);
            }
            filledFields = filledFields != null ? List.copyOf(filledFields) : List.of();
            missingFields = missingFields != null ? List.copyOf(missingFields) : List.of();
        }
    }

    /** Full profile preview result. */
    public record ProfilePreview(
            User user,
            ProfileCompleteness completeness,
            List<String> improvementTips,
            String displayBio,
            String displayLookingFor) {

        public ProfilePreview {
            Objects.requireNonNull(user);
            Objects.requireNonNull(completeness);
            improvementTips = improvementTips != null ? List.copyOf(improvementTips) : List.of();
            displayBio = displayBio != null ? displayBio.trim() : null;
            displayLookingFor = displayLookingFor != null ? displayLookingFor.trim() : null;
        }
    }

    private record CategoryResult(
            int earnedPoints,
            int totalPoints,
            int filledCount,
            int totalCount,
            CategoryBreakdown breakdown,
            List<String> nextSteps) {}

    // ========================================================================
    // Detailed completion analysis (category-based scoring)
    // ========================================================================

    /** Calculate the detailed completion result for a user with category breakdowns. */
    public CompletionResult calculate(User user) {
        Objects.requireNonNull(user, "user cannot be null");

        CategoryResult basic = scoreBasicInfo(user);
        CategoryResult interests = scoreInterests(user);
        CategoryResult lifestyle = scoreLifestyle(user);
        CategoryResult preferences = scorePreferences(user);

        int earnedPoints =
                basic.earnedPoints() + interests.earnedPoints() + lifestyle.earnedPoints() + preferences.earnedPoints();
        int totalPoints =
                basic.totalPoints() + interests.totalPoints() + lifestyle.totalPoints() + preferences.totalPoints();

        int filledCount =
                basic.filledCount() + interests.filledCount() + lifestyle.filledCount() + preferences.filledCount();
        int totalCount =
                basic.totalCount() + interests.totalCount() + lifestyle.totalCount() + preferences.totalCount();

        List<CategoryBreakdown> breakdown =
                List.of(basic.breakdown(), interests.breakdown(), lifestyle.breakdown(), preferences.breakdown());

        List<String> nextSteps = new ArrayList<>();
        appendSteps(nextSteps, basic.nextSteps());
        appendSteps(nextSteps, interests.nextSteps());
        appendSteps(nextSteps, lifestyle.nextSteps());
        appendSteps(nextSteps, preferences.nextSteps());

        int finalScore = totalPoints == 0 ? 0 : earnedPoints * 100 / totalPoints;
        String tier = calculateTier(finalScore);

        return new CompletionResult(finalScore, tier, filledCount, totalCount, breakdown, nextSteps);
    }

    // ========================================================================
    // Simple completeness + preview
    // ========================================================================

    /** Generate a complete profile preview for a user. */
    public ProfilePreview generatePreview(User user) {
        Objects.requireNonNull(user, "user cannot be null");

        ProfileCompleteness completeness = calculateCompleteness(user);
        List<String> tips = generateTips(user);
        String displayBio = user.getBio() != null ? user.getBio() : "(no bio)";
        String displayLookingFor =
                user.getLookingFor() != null ? user.getLookingFor().getDisplayName() : null;

        return new ProfilePreview(user, completeness, tips, displayBio, displayLookingFor);
    }

    /** Calculate how complete a user's profile is (simple field-based check). */
    public ProfileCompleteness calculateCompleteness(User user) {
        List<String> filled = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // Required fields (core profile)
        checkField("Name", user.getName() != null && !user.getName().isBlank(), filled, missing);
        checkField("Bio", user.getBio() != null && !user.getBio().isBlank(), filled, missing);
        checkField("Birth Date", user.getBirthDate() != null, filled, missing);
        checkField("Gender", user.getGender() != null, filled, missing);
        checkField(
                "Interested In",
                user.getInterestedIn() != null && !user.getInterestedIn().isEmpty(),
                filled,
                missing);
        checkField("Location", user.getLat() != 0.0 || user.getLon() != 0.0, filled, missing);
        checkField("Photo", !user.getPhotoUrls().isEmpty(), filled, missing);

        // Lifestyle fields (optional but encouraged)
        checkField("Height", user.getHeightCm() != null, filled, missing);
        checkField("Smoking", user.getSmoking() != null, filled, missing);
        checkField("Drinking", user.getDrinking() != null, filled, missing);
        checkField("Kids Stance", user.getWantsKids() != null, filled, missing);
        checkField("Looking For", user.getLookingFor() != null, filled, missing);
        checkField("Interests", user.getInterests().size() >= Interest.MIN_FOR_COMPLETE, filled, missing);

        int total = filled.size() + missing.size();
        int percentage = total > 0 ? filled.size() * 100 / total : 0;

        return new ProfileCompleteness(percentage, filled, missing);
    }

    /** Generate improvement tips based on profile state. */
    public List<String> generateTips(User user) {
        List<String> tips = new ArrayList<>();

        // Bio tips
        if (user.getBio() == null || user.getBio().isBlank()) {
            tips.add("📝 Add a bio to tell others about yourself");
        } else if (user.getBio().length() < ScoringConstants.ProfileCompletion.BIO_TIP_MIN_LENGTH) {
            tips.add("💡 Expand your bio - profiles with "
                    + ScoringConstants.ProfileCompletion.BIO_TIP_BOOST_LENGTH
                    + "+ chars get 2x more likes");
        }

        // Photo tips
        if (user.getPhotoUrls().isEmpty()) {
            tips.add("📸 Add a photo - it's required for browsing");
        } else if (user.getPhotoUrls().size() < ScoringConstants.ProfileCompletion.PHOTO_TIP_MIN_COUNT) {
            tips.add("📸 Add a second photo - users with 2 photos get 40% more matches");
        }

        // Lifestyle tips
        if (user.getLookingFor() == null) {
            tips.add("💝 Share what you're looking for - helps find compatible matches");
        }
        if (user.getHeightCm() == null) {
            tips.add("📏 Add your height - many users filter by height");
        }
        if (countLifestyleFields(user) < ScoringConstants.ProfileCompletion.LIFESTYLE_FIELDS_MIN) {
            tips.add("🧘 Complete more lifestyle fields for better match quality");
        }

        // Distance tips
        if (user.getMaxDistanceKm() < ScoringConstants.ProfileCompletion.DISTANCE_TIP_MAX_KM) {
            tips.add("📍 Consider expanding your distance for more options");
        }

        // Age range tips
        if (user.getMaxAge() - user.getMinAge() < ScoringConstants.ProfileCompletion.AGE_RANGE_TIP_MIN_YEARS) {
            tips.add("🎂 A wider age range gives you more potential matches");
        }

        // Interest tips
        int interestCount = user.getInterests().size();
        if (interestCount == 0) {
            tips.add("🎯 Add at least "
                    + Interest.MIN_FOR_COMPLETE
                    + " interests - profiles with shared interests get more matches");
        } else if (interestCount < Interest.MIN_FOR_COMPLETE) {
            int needed = Interest.MIN_FOR_COMPLETE - interestCount;
            tips.add("🎯 Add " + needed + " more interest(s) to complete your profile");
        }

        return tips;
    }

    /** Count how many lifestyle fields the user has set. */
    public int countLifestyleFields(User user) {
        int count = 0;
        if (user.getSmoking() != null) {
            count++;
        }
        if (user.getDrinking() != null) {
            count++;
        }
        if (user.getWantsKids() != null) {
            count++;
        }
        if (user.getLookingFor() != null) {
            count++;
        }
        if (user.getHeightCm() != null) {
            count++;
        }
        return count;
    }

    // ========================================================================
    // Progress bars
    // ========================================================================

    /** Render a simple ASCII progress bar with percentage (e.g. {@code [####------] 40%}). */
    public static String renderProgressBar(int percentage, int width) {
        int filled = percentage * width / 100;
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "#" : "-");
        }
        bar.append("] ").append(percentage).append("%");
        return bar.toString();
    }

    /** Render a Unicode block progress bar (e.g. {@code ████░░░░}). */
    public static String renderProgressBar(double fraction, int width) {
        int filled = (int) (fraction * width);
        int empty = width - filled;
        return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    private CategoryResult scoreBasicInfo(User user) {
        int totalPoints = 0;
        int earnedPoints = 0;
        List<String> basicFilled = new ArrayList<>();
        List<String> basicMissing = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();

        totalPoints += ScoringConstants.ProfileCompletion.BASIC_NAME_POINTS;
        if (user.getName() != null && !user.getName().isBlank()) {
            earnedPoints += ScoringConstants.ProfileCompletion.BASIC_NAME_POINTS;
            basicFilled.add("Name");
        } else {
            basicMissing.add("Name");
        }

        totalPoints += ScoringConstants.ProfileCompletion.BASIC_BIO_POINTS;
        if (user.getBio() != null && !user.getBio().isBlank()) {
            earnedPoints += ScoringConstants.ProfileCompletion.BASIC_BIO_POINTS;
            basicFilled.add("Bio");
        } else {
            basicMissing.add("Bio");
            nextSteps.add("📝 Add a bio to tell others about yourself");
        }

        totalPoints += ScoringConstants.ProfileCompletion.BASIC_BIRTHDATE_POINTS;
        if (user.getBirthDate() != null) {
            earnedPoints += ScoringConstants.ProfileCompletion.BASIC_BIRTHDATE_POINTS;
            basicFilled.add("Birth date");
        } else {
            basicMissing.add("Birth date");
            nextSteps.add("Add your birth date to complete your profile");
        }

        totalPoints += ScoringConstants.ProfileCompletion.BASIC_GENDER_POINTS;
        if (user.getGender() != null) {
            earnedPoints += ScoringConstants.ProfileCompletion.BASIC_GENDER_POINTS;
            basicFilled.add("Gender");
        } else {
            basicMissing.add("Gender");
        }

        totalPoints += ScoringConstants.ProfileCompletion.BASIC_INTERESTED_POINTS;
        if (user.getInterestedIn() != null && !user.getInterestedIn().isEmpty()) {
            earnedPoints += ScoringConstants.ProfileCompletion.BASIC_INTERESTED_POINTS;
            basicFilled.add("Interested in");
        } else {
            basicMissing.add("Interested in");
        }

        totalPoints += ScoringConstants.ProfileCompletion.BASIC_PHOTO_POINTS;
        if (user.getPhotoUrls() != null && !user.getPhotoUrls().isEmpty()) {
            earnedPoints += ScoringConstants.ProfileCompletion.BASIC_PHOTO_POINTS;
            basicFilled.add("Photo");
        } else {
            basicMissing.add("Photo");
            nextSteps.add("📸 Add a photo - profiles with photos get 10x more matches!");
        }

        int basicScore =
                basicFilled.isEmpty() ? 0 : basicFilled.size() * 100 / (basicFilled.size() + basicMissing.size());
        CategoryBreakdown breakdown = new CategoryBreakdown("Basic Info", basicScore, basicFilled, basicMissing);

        return new CategoryResult(
                earnedPoints,
                totalPoints,
                basicFilled.size(),
                basicFilled.size() + basicMissing.size(),
                breakdown,
                nextSteps);
    }

    private CategoryResult scoreInterests(User user) {
        int totalPoints = ScoringConstants.ProfileCompletion.INTERESTS_TOTAL_POINTS;
        int earnedPoints = 0;
        List<String> interestsFilled = new ArrayList<>();
        List<String> interestsMissing = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();

        int interestCount = user.getInterests() != null ? user.getInterests().size() : 0;
        if (interestCount >= ScoringConstants.ProfileCompletion.INTERESTS_FULL_COUNT) {
            earnedPoints += ScoringConstants.ProfileCompletion.INTERESTS_TOTAL_POINTS;
            interestsFilled.add(ScoringConstants.ProfileCompletion.INTERESTS_FULL_COUNT + "+ interests selected");
        } else if (interestCount >= ScoringConstants.ProfileCompletion.INTERESTS_PARTIAL_HIGH_COUNT) {
            earnedPoints += ScoringConstants.ProfileCompletion.INTERESTS_PARTIAL_HIGH_POINTS;
            interestsFilled.add(interestCount + " interests selected");
            interestsMissing.add("Add "
                    + (ScoringConstants.ProfileCompletion.INTERESTS_FULL_COUNT - interestCount)
                    + " more interests for full score");
            nextSteps.add("🎯 Add more interests to improve match quality");
        } else if (interestCount >= 1) {
            earnedPoints += ScoringConstants.ProfileCompletion.INTERESTS_PARTIAL_LOW_POINTS;
            interestsFilled.add(interestCount + " interest(s) selected");
            interestsMissing.add("Add "
                    + (ScoringConstants.ProfileCompletion.INTERESTS_FULL_COUNT - interestCount)
                    + " more interests for full score");
            nextSteps.add("🎯 Add more interests to improve match quality");
        } else {
            interestsMissing.add("No interests selected");
            nextSteps.add("🎯 Add your interests to find compatible matches");
        }

        int interestsScore = (interestCount >= ScoringConstants.ProfileCompletion.INTERESTS_FULL_COUNT)
                ? 100
                : (interestCount * 100 / ScoringConstants.ProfileCompletion.INTERESTS_FULL_COUNT);
        CategoryBreakdown breakdown =
                new CategoryBreakdown("Interests", interestsScore, interestsFilled, interestsMissing);

        int filledCount = interestCount > 0 ? 1 : 0;
        return new CategoryResult(earnedPoints, totalPoints, filledCount, 1, breakdown, nextSteps);
    }

    private CategoryResult scoreLifestyle(User user) {
        int totalPoints = 0;
        int earnedPoints = 0;
        List<String> lifestyleFilled = new ArrayList<>();
        List<String> lifestyleMissing = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();

        totalPoints += ScoringConstants.ProfileCompletion.LIFESTYLE_FIELD_POINTS;
        if (user.getHeightCm() != null) {
            earnedPoints += ScoringConstants.ProfileCompletion.LIFESTYLE_FIELD_POINTS;
            lifestyleFilled.add("Height");
        } else {
            lifestyleMissing.add("Height");
        }

        totalPoints += ScoringConstants.ProfileCompletion.LIFESTYLE_FIELD_POINTS;
        if (user.getSmoking() != null) {
            earnedPoints += ScoringConstants.ProfileCompletion.LIFESTYLE_FIELD_POINTS;
            lifestyleFilled.add("Smoking");
        } else {
            lifestyleMissing.add("Smoking");
        }

        totalPoints += ScoringConstants.ProfileCompletion.LIFESTYLE_FIELD_POINTS;
        if (user.getDrinking() != null) {
            earnedPoints += ScoringConstants.ProfileCompletion.LIFESTYLE_FIELD_POINTS;
            lifestyleFilled.add("Drinking");
        } else {
            lifestyleMissing.add("Drinking");
        }

        totalPoints += ScoringConstants.ProfileCompletion.LIFESTYLE_FIELD_POINTS;
        if (user.getWantsKids() != null) {
            earnedPoints += ScoringConstants.ProfileCompletion.LIFESTYLE_FIELD_POINTS;
            lifestyleFilled.add("Kids preference");
        } else {
            lifestyleMissing.add("Kids preference");
        }

        totalPoints += ScoringConstants.ProfileCompletion.LIFESTYLE_FIELD_POINTS;
        if (user.getLookingFor() != null) {
            earnedPoints += ScoringConstants.ProfileCompletion.LIFESTYLE_FIELD_POINTS;
            lifestyleFilled.add("Looking for");
        } else {
            lifestyleMissing.add("Looking for");
        }

        if (!lifestyleMissing.isEmpty()) {
            nextSteps.add("💫 Complete your lifestyle section for better matches");
        }

        int lifestyleScore = lifestyleFilled.isEmpty()
                ? 0
                : lifestyleFilled.size() * 100 / (lifestyleFilled.size() + lifestyleMissing.size());
        CategoryBreakdown breakdown =
                new CategoryBreakdown("Lifestyle", lifestyleScore, lifestyleFilled, lifestyleMissing);

        return new CategoryResult(
                earnedPoints,
                totalPoints,
                lifestyleFilled.size(),
                lifestyleFilled.size() + lifestyleMissing.size(),
                breakdown,
                nextSteps);
    }

    private CategoryResult scorePreferences(User user) {
        int totalPoints = 0;
        int earnedPoints = 0;
        List<String> prefsFilled = new ArrayList<>();
        List<String> prefsMissing = new ArrayList<>();

        totalPoints += ScoringConstants.ProfileCompletion.PREFERENCES_FIELD_POINTS;
        if (user.getLat() != 0.0 || user.getLon() != 0.0) {
            earnedPoints += ScoringConstants.ProfileCompletion.PREFERENCES_FIELD_POINTS;
            prefsFilled.add("Location");
        } else {
            prefsMissing.add("Location");
        }

        totalPoints += ScoringConstants.ProfileCompletion.PREFERENCES_FIELD_POINTS;
        if (user.getMinAge() >= config.minAge()
                && user.getMaxAge() <= config.maxAge()
                && user.getMinAge() <= user.getMaxAge()) {
            earnedPoints += ScoringConstants.ProfileCompletion.PREFERENCES_FIELD_POINTS;
            prefsFilled.add("Age preferences");
        } else {
            prefsMissing.add("Age preferences (invalid range)");
        }

        totalPoints += ScoringConstants.ProfileCompletion.PREFERENCES_FIELD_POINTS;
        if (user.getDealbreakers() != null) {
            earnedPoints += ScoringConstants.ProfileCompletion.PREFERENCES_FIELD_POINTS;
            if (user.getDealbreakers().hasAnyDealbreaker()) {
                prefsFilled.add("Dealbreakers configured");
            } else {
                prefsFilled.add("Dealbreakers reviewed (none set)");
            }
        } else {
            prefsMissing.add("Review dealbreakers");
        }

        int prefsScore =
                prefsFilled.isEmpty() ? 0 : prefsFilled.size() * 100 / (prefsFilled.size() + prefsMissing.size());
        CategoryBreakdown breakdown = new CategoryBreakdown("Preferences", prefsScore, prefsFilled, prefsMissing);

        return new CategoryResult(
                earnedPoints,
                totalPoints,
                prefsFilled.size(),
                prefsFilled.size() + prefsMissing.size(),
                breakdown,
                List.of());
    }

    private static void appendSteps(List<String> nextSteps, List<String> additions) {
        for (String step : additions) {
            if (nextSteps.size() >= ScoringConstants.ProfileCompletion.NEXT_STEPS_MAX) {
                return;
            }
            nextSteps.add(step);
        }
    }

    private static String calculateTier(int score) {
        if (score >= ScoringConstants.ProfileCompletion.TIER_DIAMOND_THRESHOLD) {
            return ScoringConstants.ProfileCompletion.TIER_DIAMOND;
        }
        if (score >= ScoringConstants.ProfileCompletion.TIER_GOLD_THRESHOLD) {
            return ScoringConstants.ProfileCompletion.TIER_GOLD;
        }
        if (score >= ScoringConstants.ProfileCompletion.TIER_SILVER_THRESHOLD) {
            return ScoringConstants.ProfileCompletion.TIER_SILVER;
        }
        if (score >= ScoringConstants.ProfileCompletion.TIER_BRONZE_THRESHOLD) {
            return ScoringConstants.ProfileCompletion.TIER_BRONZE;
        }
        return ScoringConstants.ProfileCompletion.TIER_STARTER;
    }

    private static String tierEmojiForScore(int score) {
        if (score >= ScoringConstants.ProfileCompletion.TIER_DIAMOND_THRESHOLD) {
            return ScoringConstants.ProfileCompletion.TIER_DIAMOND_EMOJI;
        }
        if (score >= ScoringConstants.ProfileCompletion.TIER_GOLD_THRESHOLD) {
            return ScoringConstants.ProfileCompletion.TIER_GOLD_EMOJI;
        }
        if (score >= ScoringConstants.ProfileCompletion.TIER_SILVER_THRESHOLD) {
            return ScoringConstants.ProfileCompletion.TIER_SILVER_EMOJI;
        }
        if (score >= ScoringConstants.ProfileCompletion.TIER_BRONZE_THRESHOLD) {
            return ScoringConstants.ProfileCompletion.TIER_BRONZE_EMOJI;
        }
        return ScoringConstants.ProfileCompletion.TIER_STARTER_EMOJI;
    }

    private static void checkField(String fieldName, boolean isFilled, List<String> filled, List<String> missing) {
        if (isFilled) {
            filled.add(fieldName);
        } else {
            missing.add(fieldName);
        }
    }
}
