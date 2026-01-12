package datingapp.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for calculating detailed profile completion scores. Provides granular breakdown of
 * profile completeness with tips for improvement.
 *
 * <p>Profile fields are weighted by importance:
 *
 * <ul>
 *   <li>Essential (required for matching): bio, photo, gender, interested in
 *   <li>Important (improve match quality): interests, lifestyle, dealbreakers
 *   <li>Optional (nice to have): height, education details
 * </ul>
 */
public final class ProfileCompletionService {

    private ProfileCompletionService() {
        // Utility class
    }

    /**
     * The result of a profile completion analysis.
     *
     * @param score overall completion percentage (0-100)
     * @param tier completion tier label (Starter, Bronze, Silver, Gold, Diamond)
     * @param filledFields number of fields that are filled
     * @param totalFields total number of scored fields
     * @param breakdown category-by-category breakdown
     * @param nextSteps prioritized suggestions for improvement
     */
    public record CompletionResult(
            int score,
            String tier,
            int filledFields,
            int totalFields,
            List<CategoryBreakdown> breakdown,
            List<String> nextSteps) {

        /** Returns a formatted display string like "78% Silver". */
        public String getDisplayString() {
            return score + "% " + tier;
        }

        /** Returns an emoji for the tier. */
        public String getTierEmoji() {
            return switch (tier) {
                case "Diamond" -> "💎";
                case "Gold" -> "🥇";
                case "Silver" -> "🥈";
                case "Bronze" -> "🥉";
                default -> "🌱";
            };
        }
    }

    /**
     * Breakdown of a single category (e.g., "Basic Info", "Lifestyle").
     *
     * @param category category name
     * @param score percentage complete (0-100)
     * @param filledItems items that are filled
     * @param missingItems items that are missing
     */
    public record CategoryBreakdown(String category, int score, List<String> filledItems, List<String> missingItems) {}

    /**
     * Calculates the detailed completion score for a user.
     *
     * @param user the user to analyze
     * @return the completion result with breakdown
     */
    public static CompletionResult calculate(User user) {
        int totalPoints = 0;
        int earnedPoints = 0;

        // === BASIC INFO (40 points max) ===
        List<String> basicFilled = new ArrayList<>();
        List<String> basicMissing = new ArrayList<>();

        // Name (always filled, 5 pts)
        totalPoints += 5;
        if (user.getName() != null && !user.getName().isBlank()) {
            earnedPoints += 5;
            basicFilled.add("Name");
        } else {
            basicMissing.add("Name");
        }

        // Bio (10 pts)
        totalPoints += 10;
        List<String> nextSteps = new ArrayList<>();
        if (user.getBio() != null && !user.getBio().isBlank()) {
            earnedPoints += 10;
            basicFilled.add("Bio");
        } else {
            basicMissing.add("Bio");
            nextSteps.add("📝 Add a bio to tell others about yourself");
        }

        // Birth date (5 pts)
        totalPoints += 5;
        if (user.getBirthDate() != null) {
            earnedPoints += 5;
            basicFilled.add("Birth date");
        } else {
            basicMissing.add("Birth date");
        }

        // Gender (5 pts)
        totalPoints += 5;
        if (user.getGender() != null) {
            earnedPoints += 5;
            basicFilled.add("Gender");
        } else {
            basicMissing.add("Gender");
        }

        // Interested in (5 pts)
        totalPoints += 5;
        if (user.getInterestedIn() != null && !user.getInterestedIn().isEmpty()) {
            earnedPoints += 5;
            basicFilled.add("Interested in");
        } else {
            basicMissing.add("Interested in");
        }

        // Photo (10 pts)
        totalPoints += 10;
        if (user.getPhotoUrls() != null && !user.getPhotoUrls().isEmpty()) {
            earnedPoints += 10;
            basicFilled.add("Photo");
        } else {
            basicMissing.add("Photo");
            nextSteps.add("📸 Add a photo - profiles with photos get 10x more matches!");
        }

        int basicScore =
                basicFilled.isEmpty() ? 0 : (basicFilled.size() * 100) / (basicFilled.size() + basicMissing.size());
        List<CategoryBreakdown> breakdown = new ArrayList<>();
        breakdown.add(new CategoryBreakdown("Basic Info", basicScore, basicFilled, basicMissing));

        // === INTERESTS (20 points max) ===
        List<String> interestsFilled = new ArrayList<>();
        List<String> interestsMissing = new ArrayList<>();

        totalPoints += 20;
        int interestCount = user.getInterests() != null ? user.getInterests().size() : 0;
        if (interestCount >= 5) {
            earnedPoints += 20;
            interestsFilled.add("5+ interests selected");
        } else if (interestCount >= 3) {
            earnedPoints += 15;
            interestsFilled.add(interestCount + " interests selected");
            interestsMissing.add("Add " + (5 - interestCount) + " more interests for full score");
            nextSteps.add("🎯 Add more interests to improve match quality");
        } else if (interestCount >= 1) {
            earnedPoints += 10;
            interestsFilled.add(interestCount + " interest(s) selected");
            interestsMissing.add("Add " + (5 - interestCount) + " more interests for full score");
            nextSteps.add("🎯 Add more interests to improve match quality");
        } else {
            interestsMissing.add("No interests selected");
            nextSteps.add("🎯 Add your interests to find compatible matches");
        }

        int interestsScore = (interestCount >= 5) ? 100 : (interestCount * 100 / 5);
        breakdown.add(new CategoryBreakdown("Interests", interestsScore, interestsFilled, interestsMissing));

        // === LIFESTYLE (25 points max) ===
        List<String> lifestyleFilled = new ArrayList<>();
        List<String> lifestyleMissing = new ArrayList<>();

        // Height (5 pts)
        totalPoints += 5;
        if (user.getHeightCm() != null) {
            earnedPoints += 5;
            lifestyleFilled.add("Height");
        } else {
            lifestyleMissing.add("Height");
        }

        // Smoking (5 pts)
        totalPoints += 5;
        if (user.getSmoking() != null) {
            earnedPoints += 5;
            lifestyleFilled.add("Smoking");
        } else {
            lifestyleMissing.add("Smoking");
        }

        // Drinking (5 pts)
        totalPoints += 5;
        if (user.getDrinking() != null) {
            earnedPoints += 5;
            lifestyleFilled.add("Drinking");
        } else {
            lifestyleMissing.add("Drinking");
        }

        // Kids (5 pts)
        totalPoints += 5;
        if (user.getWantsKids() != null) {
            earnedPoints += 5;
            lifestyleFilled.add("Kids preference");
        } else {
            lifestyleMissing.add("Kids preference");
        }

        // Looking for (5 pts)
        totalPoints += 5;
        if (user.getLookingFor() != null) {
            earnedPoints += 5;
            lifestyleFilled.add("Looking for");
        } else {
            lifestyleMissing.add("Looking for");
        }

        if (!lifestyleMissing.isEmpty() && nextSteps.size() < 3) {
            nextSteps.add("💫 Complete your lifestyle section for better matches");
        }

        int lifestyleScore = lifestyleFilled.isEmpty()
                ? 0
                : (lifestyleFilled.size() * 100) / (lifestyleFilled.size() + lifestyleMissing.size());
        breakdown.add(new CategoryBreakdown("Lifestyle", lifestyleScore, lifestyleFilled, lifestyleMissing));

        // === PREFERENCES (15 points max) ===
        List<String> prefsFilled = new ArrayList<>();
        List<String> prefsMissing = new ArrayList<>();

        // Location (5 pts)
        totalPoints += 5;
        if (user.getLat() != 0.0 || user.getLon() != 0.0) {
            earnedPoints += 5;
            prefsFilled.add("Location");
        } else {
            prefsMissing.add("Location");
        }

        // Age range (5 pts)
        totalPoints += 5;
        if (user.getMinAge() != 18 || user.getMaxAge() != 99) {
            earnedPoints += 5;
            prefsFilled.add("Age preferences");
        } else {
            prefsMissing.add("Age preferences (using defaults)");
        }

        // Dealbreakers (5 pts)
        totalPoints += 5;
        if (user.getDealbreakers() != null && user.getDealbreakers().hasAnyDealbreaker()) {
            earnedPoints += 5;
            prefsFilled.add("Dealbreakers set");
        } else {
            prefsMissing.add("No dealbreakers configured");
        }

        int prefsScore =
                prefsFilled.isEmpty() ? 0 : (prefsFilled.size() * 100) / (prefsFilled.size() + prefsMissing.size());
        breakdown.add(new CategoryBreakdown("Preferences", prefsScore, prefsFilled, prefsMissing));

        // === CALCULATE FINAL SCORE ===
        int finalScore = (earnedPoints * 100) / totalPoints;
        String tier = calculateTier(finalScore);

        int filledCount =
                basicFilled.size() + (interestCount > 0 ? 1 : 0) + lifestyleFilled.size() + prefsFilled.size();
        int totalCount = 6 + 1 + 5 + 3; // basic fields + interests + lifestyle + prefs

        return new CompletionResult(finalScore, tier, filledCount, totalCount, breakdown, nextSteps);
    }

    private static String calculateTier(int score) {
        if (score >= 90) {
            return "Diamond";
        }
        if (score >= 75) {
            return "Gold";
        }
        if (score >= 50) {
            return "Silver";
        }
        if (score >= 25) {
            return "Bronze";
        }
        return "Starter";
    }

    /**
     * Renders a progress bar for the given completion percentage.
     *
     * @param percentage 0-100 percentage
     * @param width bar width in characters
     * @return ASCII progress bar string
     */
    public static String renderProgressBar(int percentage, int width) {
        int filled = (percentage * width) / 100;
        int empty = width - filled;
        return "[" + "█".repeat(filled) + "░".repeat(empty) + "]";
    }
}
