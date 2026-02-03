package datingapp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service for calculating profile completion percentage.
 */
public final class ProfileCompletionService {

    private ProfileCompletionService() {}

    /**
     * Result of completion analysis.
     */
    public static record CompletionResult(
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

        /**
         * @return percentage of completion score.
         */
        public int getPercentage() {
            return score;
        }

        /**
         * @return human-readable tier label.
         */
        public String getTierLabel() {
            return tier;
        }

        public String getTierEmoji() {
            return ProfileCompletionService.tierEmojiForScore(score);
        }

        public String getDisplayString() {
            return score + "% " + tier;
        }
    }

    /**
     * Breakdown of completion by category.
     */
    public static record CategoryBreakdown(
            String category, int score, List<String> filledItems, List<String> missingItems) {

        public CategoryBreakdown {
            Objects.requireNonNull(category, "category cannot be null");
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("score must be 0-100, got: " + score);
            }
            filledItems = filledItems != null ? List.copyOf(filledItems) : List.of();
            missingItems = missingItems != null ? List.copyOf(missingItems) : List.of();
        }
    }

    private record CategoryResult(
            int earnedPoints,
            int totalPoints,
            int filledCount,
            int totalCount,
            CategoryBreakdown breakdown,
            List<String> nextSteps) {}

    /**
     * Calculate the completion result for a user.
     */
    public static CompletionResult calculate(User user) {
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

        List<CategoryBreakdown> breakdown = new ArrayList<>();
        breakdown.add(basic.breakdown());
        breakdown.add(interests.breakdown());
        breakdown.add(lifestyle.breakdown());
        breakdown.add(preferences.breakdown());

        List<String> nextSteps = new ArrayList<>();
        appendSteps(nextSteps, basic.nextSteps());
        appendSteps(nextSteps, interests.nextSteps());
        appendSteps(nextSteps, lifestyle.nextSteps());
        appendSteps(nextSteps, preferences.nextSteps());

        int finalScore = totalPoints == 0 ? 0 : earnedPoints * 100 / totalPoints;
        String tier = calculateTier(finalScore);

        return new CompletionResult(finalScore, tier, filledCount, totalCount, breakdown, nextSteps);
    }

    private static CategoryResult scoreBasicInfo(User user) {
        int totalPoints = 0;
        int earnedPoints = 0;
        List<String> basicFilled = new ArrayList<>();
        List<String> basicMissing = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();

        totalPoints += 5;
        if (user.getName() != null && !user.getName().isBlank()) {
            earnedPoints += 5;
            basicFilled.add("Name");
        } else {
            basicMissing.add("Name");
        }

        totalPoints += 10;
        if (user.getBio() != null && !user.getBio().isBlank()) {
            earnedPoints += 10;
            basicFilled.add("Bio");
        } else {
            basicMissing.add("Bio");
            nextSteps.add("📝 Add a bio to tell others about yourself");
        }

        totalPoints += 5;
        if (user.getBirthDate() != null) {
            earnedPoints += 5;
            basicFilled.add("Birth date");
        } else {
            basicMissing.add("Birth date");
            nextSteps.add("Add your birth date to complete your profile");
        }

        totalPoints += 5;
        if (user.getGender() != null) {
            earnedPoints += 5;
            basicFilled.add("Gender");
        } else {
            basicMissing.add("Gender");
        }

        totalPoints += 5;
        if (user.getInterestedIn() != null && !user.getInterestedIn().isEmpty()) {
            earnedPoints += 5;
            basicFilled.add("Interested in");
        } else {
            basicMissing.add("Interested in");
        }

        totalPoints += 10;
        if (user.getPhotoUrls() != null && !user.getPhotoUrls().isEmpty()) {
            earnedPoints += 10;
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

    private static CategoryResult scoreInterests(User user) {
        int totalPoints = 20;
        int earnedPoints = 0;
        List<String> interestsFilled = new ArrayList<>();
        List<String> interestsMissing = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();

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
        CategoryBreakdown breakdown =
                new CategoryBreakdown("Interests", interestsScore, interestsFilled, interestsMissing);

        int filledCount = interestCount > 0 ? 1 : 0;
        return new CategoryResult(earnedPoints, totalPoints, filledCount, 1, breakdown, nextSteps);
    }

    private static CategoryResult scoreLifestyle(User user) {
        int totalPoints = 0;
        int earnedPoints = 0;
        List<String> lifestyleFilled = new ArrayList<>();
        List<String> lifestyleMissing = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();

        totalPoints += 5;
        if (user.getHeightCm() != null) {
            earnedPoints += 5;
            lifestyleFilled.add("Height");
        } else {
            lifestyleMissing.add("Height");
        }

        totalPoints += 5;
        if (user.getSmoking() != null) {
            earnedPoints += 5;
            lifestyleFilled.add("Smoking");
        } else {
            lifestyleMissing.add("Smoking");
        }

        totalPoints += 5;
        if (user.getDrinking() != null) {
            earnedPoints += 5;
            lifestyleFilled.add("Drinking");
        } else {
            lifestyleMissing.add("Drinking");
        }

        totalPoints += 5;
        if (user.getWantsKids() != null) {
            earnedPoints += 5;
            lifestyleFilled.add("Kids preference");
        } else {
            lifestyleMissing.add("Kids preference");
        }

        totalPoints += 5;
        if (user.getLookingFor() != null) {
            earnedPoints += 5;
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

    private static CategoryResult scorePreferences(User user) {
        int totalPoints = 0;
        int earnedPoints = 0;
        List<String> prefsFilled = new ArrayList<>();
        List<String> prefsMissing = new ArrayList<>();

        totalPoints += 5;
        if (user.getLat() != 0.0 || user.getLon() != 0.0) {
            earnedPoints += 5;
            prefsFilled.add("Location");
        } else {
            prefsMissing.add("Location");
        }

        totalPoints += 5;
        if (user.getMinAge() >= 18 && user.getMaxAge() <= 120 && user.getMinAge() <= user.getMaxAge()) {
            earnedPoints += 5;
            prefsFilled.add("Age preferences");
        } else {
            prefsMissing.add("Age preferences (invalid range)");
        }

        totalPoints += 5;
        if (user.getDealbreakers() != null) {
            earnedPoints += 5;
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
            if (nextSteps.size() >= 3) {
                return;
            }
            nextSteps.add(step);
        }
    }

    private static String calculateTier(int score) {
        if (score >= 95) {
            return "Diamond";
        }
        if (score >= 85) {
            return "Gold";
        }
        if (score >= 70) {
            return "Silver";
        }
        if (score >= 50) {
            return "Bronze";
        }
        return "Starter";
    }

    private static String tierEmojiForScore(int score) {
        if (score >= 95) {
            return "💎";
        }
        if (score >= 85) {
            return "🥇";
        }
        if (score >= 70) {
            return "🥈";
        }
        if (score >= 50) {
            return "🥉";
        }
        return "🌱";
    }

    /**
     * Render a simple ASCII progress bar.
     */
    public static String renderProgressBar(int percentage, int width) {
        int filled = percentage * width / 100;
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "#" : "-");
        }
        bar.append("] ").append(percentage).append("%");
        return bar.toString();
    }
}
