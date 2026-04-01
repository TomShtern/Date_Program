package datingapp.core.profile;

import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Interest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

final class ProfileCompletionSupport {

    private static final int BIO_TIP_MIN_LENGTH = 50;
    private static final int PHOTO_TIP_MIN_COUNT = 2;
    private static final int LIFESTYLE_FIELDS_MIN = 3;
    private static final int DISTANCE_TIP_MAX_KM = 10;
    private static final int AGE_RANGE_TIP_MIN_YEARS = 5;
    private static final int NEXT_STEPS_MAX = 3;
    private static final int BASIC_NAME_POINTS = 5;
    private static final int BASIC_BIO_POINTS = 10;
    private static final int BASIC_BIRTHDATE_POINTS = 5;
    private static final int BASIC_GENDER_POINTS = 5;
    private static final int BASIC_INTERESTED_POINTS = 5;
    private static final int BASIC_PHOTO_POINTS = 10;
    private static final int INTERESTS_TOTAL_POINTS = 20;
    private static final int INTERESTS_FULL_COUNT = 5;
    private static final int LIFESTYLE_FIELD_POINTS = 5;
    private static final int PREFERENCES_FIELD_POINTS = 5;
    private static final int TIER_DIAMOND_THRESHOLD = 95;
    private static final int TIER_GOLD_THRESHOLD = 85;
    private static final int TIER_SILVER_THRESHOLD = 70;
    private static final int TIER_BRONZE_THRESHOLD = 40;
    private static final String TIER_DIAMOND = "Diamond";
    private static final String TIER_GOLD = "Gold";
    private static final String TIER_SILVER = "Silver";
    private static final String TIER_BRONZE = "Bronze";
    private static final String TIER_STARTER = "Starter";
    private static final String TIER_DIAMOND_EMOJI = "💎";
    private static final String TIER_GOLD_EMOJI = "🥇";
    private static final String TIER_SILVER_EMOJI = "🥈";
    private static final String TIER_BRONZE_EMOJI = "🥉";
    private static final String TIER_STARTER_EMOJI = "🌱";
    private static final String FIELD_LOCATION = "Location";

    private record CategoryResult(
            int earnedPoints,
            int totalPoints,
            int filledCount,
            int totalCount,
            ProfileService.CategoryBreakdown breakdown,
            List<String> nextSteps) {}

    private record FieldCheck(String name, int points, Predicate<User> isComplete, String nextStep) {
        FieldCheck(String name, int points, Predicate<User> isComplete) {
            this(name, points, isComplete, null);
        }
    }

    private static final List<FieldCheck> BASIC_FIELDS = List.of(
            new FieldCheck(
                    "Name",
                    BASIC_NAME_POINTS,
                    user -> user.getName() != null && !user.getName().isBlank()),
            new FieldCheck(
                    "Bio",
                    BASIC_BIO_POINTS,
                    user -> user.getBio() != null && !user.getBio().isBlank(),
                    "📝 Add a bio to tell others about yourself"),
            new FieldCheck(
                    "Birth date",
                    BASIC_BIRTHDATE_POINTS,
                    user -> user.getBirthDate() != null,
                    "📅 Add your birth date"),
            new FieldCheck("Gender", BASIC_GENDER_POINTS, user -> user.getGender() != null),
            new FieldCheck(
                    "Interested in",
                    BASIC_INTERESTED_POINTS,
                    user -> user.getInterestedIn() != null
                            && !user.getInterestedIn().isEmpty()),
            new FieldCheck("Photo", BASIC_PHOTO_POINTS, User::hasRealPhoto, "📸 Add a photo"));

    private static final List<FieldCheck> LIFESTYLE_FIELDS = List.of(
            new FieldCheck("Height", LIFESTYLE_FIELD_POINTS, user -> user.getHeightCm() != null),
            new FieldCheck("Smoking", LIFESTYLE_FIELD_POINTS, user -> user.getSmoking() != null),
            new FieldCheck("Drinking", LIFESTYLE_FIELD_POINTS, user -> user.getDrinking() != null),
            new FieldCheck("Kids preference", LIFESTYLE_FIELD_POINTS, user -> user.getWantsKids() != null),
            new FieldCheck("Looking for", LIFESTYLE_FIELD_POINTS, user -> user.getLookingFor() != null));

    private static final List<FieldCheck> PREFERENCES_FIELDS = List.of(
            new FieldCheck(FIELD_LOCATION, PREFERENCES_FIELD_POINTS, User::hasLocation),
            new FieldCheck("Age", PREFERENCES_FIELD_POINTS, user -> user.getMinAge() > 0));

    ProfileService.CompletionResult calculate(User user) {
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

        List<ProfileService.CategoryBreakdown> breakdown =
                List.of(basic.breakdown(), interests.breakdown(), lifestyle.breakdown(), preferences.breakdown());

        List<String> nextSteps = new ArrayList<>();
        appendSteps(nextSteps, basic.nextSteps());
        appendSteps(nextSteps, interests.nextSteps());
        appendSteps(nextSteps, lifestyle.nextSteps());
        appendSteps(nextSteps, preferences.nextSteps());

        int finalScore = totalPoints == 0 ? 0 : earnedPoints * 100 / totalPoints;
        return new ProfileService.CompletionResult(
                finalScore, calculateTier(finalScore), filledCount, totalCount, breakdown, nextSteps);
    }

    ProfileService.ProfilePreview generatePreview(User user) {
        ProfileService.ProfileCompleteness completeness = calculateCompleteness(user);
        List<String> tips = generateTips(user);
        String displayBio = user.getBio() != null ? user.getBio() : "(no bio)";
        String displayLookingFor =
                user.getLookingFor() != null ? user.getLookingFor().getDisplayName() : null;

        return new ProfileService.ProfilePreview(user, completeness, tips, displayBio, displayLookingFor);
    }

    ProfileService.ProfileCompleteness calculateCompleteness(User user) {
        List<String> filled = user.getFilledProfileFieldDisplayNames();
        List<String> missing = user.getMissingProfileFieldDisplayNames();
        int total = user.getRequiredProfileFieldCount();
        int percentage = total > 0 ? filled.size() * 100 / total : 0;

        return new ProfileService.ProfileCompleteness(percentage, filled, missing);
    }

    List<String> generateTips(User user) {
        List<String> tips = new ArrayList<>();
        Set<Interest> interests = user.getInterests() != null ? user.getInterests() : Set.of();
        int realPhotoCount = user.getRealPhotoCount();

        if (user.getBio() == null || user.getBio().isBlank()) {
            tips.add("📝 Add a bio to tell others about yourself");
        } else if (user.getBio().length() < BIO_TIP_MIN_LENGTH) {
            tips.add("💡 Expand your bio");
        }

        if (realPhotoCount == 0) {
            tips.add("📸 Add a photo");
        } else if (realPhotoCount < PHOTO_TIP_MIN_COUNT) {
            tips.add("📸 Add a second photo");
        }

        if (user.getLookingFor() == null) {
            tips.add("💝 Share what you're looking for");
        }
        if (user.getHeightCm() == null) {
            tips.add("📏 Add your height");
        }
        if (countLifestyleFields(user) < LIFESTYLE_FIELDS_MIN) {
            tips.add("🧘 Complete more lifestyle fields");
        }

        if (user.getMaxDistanceKm() < DISTANCE_TIP_MAX_KM) {
            tips.add("📍 Expand your distance");
        }

        if (user.getMaxAge() - user.getMinAge() < AGE_RANGE_TIP_MIN_YEARS) {
            tips.add("🎂 Use a wider age range");
        }

        appendInterestTips(tips, interests);
        return tips;
    }

    int countLifestyleFields(User user) {
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

    static String tierEmojiForScore(int score) {
        if (score >= TIER_DIAMOND_THRESHOLD) {
            return TIER_DIAMOND_EMOJI;
        }
        if (score >= TIER_GOLD_THRESHOLD) {
            return TIER_GOLD_EMOJI;
        }
        if (score >= TIER_SILVER_THRESHOLD) {
            return TIER_SILVER_EMOJI;
        }
        if (score >= TIER_BRONZE_THRESHOLD) {
            return TIER_BRONZE_EMOJI;
        }
        return TIER_STARTER_EMOJI;
    }

    private CategoryResult scoreBasicInfo(User user) {
        return scoreCategory("Basic Info", BASIC_FIELDS, user);
    }

    private CategoryResult scoreInterests(User user) {
        int interestCount = user.getInterests() != null ? user.getInterests().size() : 0;
        int earnedPoints =
                Math.min(INTERESTS_TOTAL_POINTS, interestCount * (INTERESTS_TOTAL_POINTS / INTERESTS_FULL_COUNT));
        int score = Math.min(100, interestCount * 100 / INTERESTS_FULL_COUNT);

        List<String> filled = interestCount > 0 ? List.of(interestCount + " interests") : List.of();
        List<String> missing = interestCount < INTERESTS_FULL_COUNT ? List.of("More interests") : List.of();
        List<String> steps = interestCount < INTERESTS_FULL_COUNT ? List.of("🎯 Add more interests") : List.of();

        return new CategoryResult(
                earnedPoints,
                INTERESTS_TOTAL_POINTS,
                interestCount > 0 ? 1 : 0,
                1,
                new ProfileService.CategoryBreakdown("Interests", score, filled, missing),
                steps);
    }

    private CategoryResult scoreLifestyle(User user) {
        return scoreCategory("Lifestyle", LIFESTYLE_FIELDS, user);
    }

    private CategoryResult scorePreferences(User user) {
        return scoreCategory("Preferences", PREFERENCES_FIELDS, user);
    }

    private CategoryResult scoreCategory(String name, List<FieldCheck> fields, User user) {
        int totalPoints = 0;
        int earnedPoints = 0;
        List<String> filled = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();

        for (FieldCheck field : fields) {
            totalPoints += field.points();
            if (field.isComplete().test(user)) {
                earnedPoints += field.points();
                filled.add(field.name());
            } else {
                missing.add(field.name());
                if (field.nextStep() != null) {
                    nextSteps.add(field.nextStep());
                }
            }
        }

        int score = filled.isEmpty() ? 0 : filled.size() * 100 / (filled.size() + missing.size());
        return new CategoryResult(
                earnedPoints,
                totalPoints,
                filled.size(),
                filled.size() + missing.size(),
                new ProfileService.CategoryBreakdown(name, score, filled, missing),
                nextSteps);
    }

    private static void appendInterestTips(List<String> tips, Set<Interest> interests) {
        if (interests.isEmpty()) {
            tips.add("🎯 Add at least " + Interest.MIN_FOR_COMPLETE + " interests");
        } else if (interests.size() < Interest.MIN_FOR_COMPLETE) {
            int remaining = Interest.MIN_FOR_COMPLETE - interests.size();
            tips.add("🎯 Add " + remaining + " more interest" + (remaining == 1 ? "" : "s"));
        }
    }

    private static void appendSteps(List<String> nextSteps, List<String> additions) {
        for (String step : additions) {
            if (nextSteps.size() >= NEXT_STEPS_MAX) {
                return;
            }
            nextSteps.add(step);
        }
    }

    private static String calculateTier(int score) {
        if (score >= TIER_DIAMOND_THRESHOLD) {
            return TIER_DIAMOND;
        }
        if (score >= TIER_GOLD_THRESHOLD) {
            return TIER_GOLD;
        }
        if (score >= TIER_SILVER_THRESHOLD) {
            return TIER_SILVER;
        }
        if (score >= TIER_BRONZE_THRESHOLD) {
            return TIER_BRONZE;
        }
        return TIER_STARTER;
    }
}
