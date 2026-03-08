package datingapp.core.profile;

import datingapp.core.AppConfig;
import datingapp.core.metrics.DefaultAchievementService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class ProfileService {

    private final AppConfig config;
    private final AnalyticsStorage analyticsStorage;
    private final InteractionStorage interactionStorage;
    private final TrustSafetyStorage trustSafetyStorage;
    private final UserStorage userStorage;

    // ── Profile completion thresholds ──
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

    public ProfileService(
            AppConfig config,
            AnalyticsStorage analyticsStorage,
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            UserStorage userStorage) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.analyticsStorage = Objects.requireNonNull(analyticsStorage, "analyticsStorage cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
    }

    public List<User> listUsers() {
        return userStorage.findAll();
    }

    public Optional<User> getUserById(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        return userStorage.get(userId);
    }

    public Map<UUID, User> getUsersByIds(Set<UUID> userIds) {
        Objects.requireNonNull(userIds, "userIds cannot be null");
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userStorage.findByIds(userIds);
    }

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
            Objects.requireNonNull(tier, "tier cannot be null");
            breakdown = breakdown != null ? List.copyOf(breakdown) : List.of();
            nextSteps = nextSteps != null ? List.copyOf(nextSteps) : List.of();
        }

        public int percentage() {
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

    public static record AchievementProgress(Achievement achievement, int current, int target, boolean unlocked) {
        public int getProgressPercent() {
            if (unlocked || target <= 0) {
                return 100;
            }
            return Math.min(100, current * 100 / target);
        }

        public String getProgressDisplay() {
            if (unlocked) {
                return "✓ Unlocked";
            }
            int displayCurrent = Math.min(current, target);
            return displayCurrent + "/" + target;
        }
    }

    public static record CategoryBreakdown(
            String category, int score, List<String> filledItems, List<String> missingItems) {
        public CategoryBreakdown {
            Objects.requireNonNull(category, "category cannot be null");
            filledItems = filledItems != null ? List.copyOf(filledItems) : List.of();
            missingItems = missingItems != null ? List.copyOf(missingItems) : List.of();
        }
    }

    public static record ProfileCompleteness(int percentage, List<String> filledFields, List<String> missingFields) {
        public ProfileCompleteness {
            filledFields = filledFields != null ? List.copyOf(filledFields) : List.of();
            missingFields = missingFields != null ? List.copyOf(missingFields) : List.of();
        }
    }

    public static record ProfilePreview(
            User user,
            ProfileCompleteness completeness,
            List<String> improvementTips,
            String displayBio,
            String displayLookingFor) {
        public ProfilePreview {
            Objects.requireNonNull(user);
            Objects.requireNonNull(completeness);
            improvementTips = improvementTips != null ? List.copyOf(improvementTips) : List.of();
        }
    }

    private static record CategoryResult(
            int earnedPoints,
            int totalPoints,
            int filledCount,
            int totalCount,
            CategoryBreakdown breakdown,
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
                    u -> u.getName() != null && !u.getName().isBlank()),
            new FieldCheck(
                    "Bio",
                    BASIC_BIO_POINTS,
                    u -> u.getBio() != null && !u.getBio().isBlank(),
                    "📝 Add a bio to tell others about yourself"),
            new FieldCheck(
                    "Birth date", BASIC_BIRTHDATE_POINTS, u -> u.getBirthDate() != null, "📅 Add your birth date"),
            new FieldCheck("Gender", BASIC_GENDER_POINTS, u -> u.getGender() != null),
            new FieldCheck(
                    "Interested in",
                    BASIC_INTERESTED_POINTS,
                    u -> u.getInterestedIn() != null && !u.getInterestedIn().isEmpty()),
            new FieldCheck(
                    "Photo",
                    BASIC_PHOTO_POINTS,
                    u -> u.getPhotoUrls() != null && !u.getPhotoUrls().isEmpty(),
                    "📸 Add a photo"));

    private static final List<FieldCheck> LIFESTYLE_FIELDS = List.of(
            new FieldCheck("Height", LIFESTYLE_FIELD_POINTS, u -> u.getHeightCm() != null),
            new FieldCheck("Smoking", LIFESTYLE_FIELD_POINTS, u -> u.getSmoking() != null),
            new FieldCheck("Drinking", LIFESTYLE_FIELD_POINTS, u -> u.getDrinking() != null),
            new FieldCheck("Kids preference", LIFESTYLE_FIELD_POINTS, u -> u.getWantsKids() != null),
            new FieldCheck("Looking for", LIFESTYLE_FIELD_POINTS, u -> u.getLookingFor() != null));

    public CompletionResult calculate(User user) {
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

    public ProfilePreview generatePreview(User user) {
        ProfileCompleteness completeness = calculateCompleteness(user);
        List<String> tips = generateTips(user);
        String displayBio = user.getBio() != null ? user.getBio() : "(no bio)";
        String displayLookingFor =
                user.getLookingFor() != null ? user.getLookingFor().getDisplayName() : null;

        return new ProfilePreview(user, completeness, tips, displayBio, displayLookingFor);
    }

    public ProfileCompleteness calculateCompleteness(User user) {
        List<String> filled = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> photoUrls = user.getPhotoUrls() != null ? user.getPhotoUrls() : List.of();
        Set<Interest> interests = user.getInterests() != null ? user.getInterests() : Set.of();

        checkField("Name", user.getName() != null && !user.getName().isBlank(), filled, missing);
        checkField("Bio", user.getBio() != null && !user.getBio().isBlank(), filled, missing);
        checkField("Birth Date", user.getBirthDate() != null, filled, missing);
        checkField("Gender", user.getGender() != null, filled, missing);
        checkField(
                "Interested In",
                user.getInterestedIn() != null && !user.getInterestedIn().isEmpty(),
                filled,
                missing);
        checkField(FIELD_LOCATION, user.getLat() != 0.0 || user.getLon() != 0.0, filled, missing);
        checkField("Photo", !photoUrls.isEmpty(), filled, missing);

        checkField("Height", user.getHeightCm() != null, filled, missing);
        checkField("Smoking", user.getSmoking() != null, filled, missing);
        checkField("Drinking", user.getDrinking() != null, filled, missing);
        checkField("Kids Stance", user.getWantsKids() != null, filled, missing);
        checkField("Looking For", user.getLookingFor() != null, filled, missing);
        checkField("Interests", interests.size() >= Interest.MIN_FOR_COMPLETE, filled, missing);

        int total = filled.size() + missing.size();
        int percentage = total > 0 ? filled.size() * 100 / total : 0;

        return new ProfileCompleteness(percentage, filled, missing);
    }

    public List<String> generateTips(User user) {
        List<String> tips = new ArrayList<>();
        List<String> photoUrls = user.getPhotoUrls() != null ? user.getPhotoUrls() : List.of();
        Set<Interest> interests = user.getInterests() != null ? user.getInterests() : Set.of();

        if (user.getBio() == null || user.getBio().isBlank()) {
            tips.add("📝 Add a bio to tell others about yourself");
        } else if (user.getBio().length() < BIO_TIP_MIN_LENGTH) {
            tips.add("💡 Expand your bio");
        }

        if (photoUrls.isEmpty()) {
            tips.add("📸 Add a photo");
        } else if (photoUrls.size() < PHOTO_TIP_MIN_COUNT) {
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

    private static void appendInterestTips(List<String> tips, Set<Interest> interests) {
        if (interests.isEmpty()) {
            tips.add("🎯 Add at least " + Interest.MIN_FOR_COMPLETE + " interests");
        } else if (interests.size() < Interest.MIN_FOR_COMPLETE) {
            int remaining = Interest.MIN_FOR_COMPLETE - interests.size();
            tips.add("🎯 Add " + remaining + " more interest" + (remaining == 1 ? "" : "s"));
        }
    }

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
                new CategoryBreakdown(name, score, filled, missing),
                nextSteps);
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
                new CategoryBreakdown("Interests", score, filled, missing),
                steps);
    }

    private CategoryResult scoreLifestyle(User user) {
        return scoreCategory("Lifestyle", LIFESTYLE_FIELDS, user);
    }

    private CategoryResult scorePreferences(User user) {
        int earnedPoints = 0;
        List<String> filled = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        if (user.getLat() != 0.0) {
            earnedPoints += PREFERENCES_FIELD_POINTS;
            filled.add(FIELD_LOCATION);
        } else {
            missing.add(FIELD_LOCATION);
        }

        if (user.getMinAge() > 0) {
            earnedPoints += PREFERENCES_FIELD_POINTS;
            filled.add("Age");
        } else {
            missing.add("Age");
        }

        int score = filled.isEmpty() ? 0 : filled.size() * 100 / (filled.size() + missing.size());
        return new CategoryResult(
                earnedPoints,
                PREFERENCES_FIELD_POINTS * 2,
                filled.size(),
                2,
                new CategoryBreakdown("Preferences", score, filled, missing),
                List.of());
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

    private static String tierEmojiForScore(int score) {
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

    private static void checkField(String fieldName, boolean isFilled, List<String> filled, List<String> missing) {
        if (isFilled) {
            filled.add(fieldName);
        } else {
            missing.add(fieldName);
        }
    }

    public List<UserAchievement> checkAndUnlock(UUID userId) {
        return legacyAchievementService().checkAndUnlock(userId);
    }

    public List<UserAchievement> getUnlocked(UUID userId) {
        return legacyAchievementService().getUnlocked(userId);
    }

    public List<AchievementProgress> getProgress(UUID userId) {
        return legacyAchievementService().getProgress(userId).stream()
                .map(progress -> new AchievementProgress(
                        progress.achievement(), progress.current(), progress.target(), progress.unlocked()))
                .toList();
    }

    public Map<Achievement.Category, List<AchievementProgress>> getProgressByCategory(UUID userId) {
        return legacyAchievementService().getProgressByCategory(userId).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                        .map(progress -> new AchievementProgress(
                                progress.achievement(), progress.current(), progress.target(), progress.unlocked()))
                        .toList()));
    }

    public int countUnlocked(UUID userId) {
        return legacyAchievementService().countUnlocked(userId);
    }

    private DefaultAchievementService legacyAchievementService() {
        return new DefaultAchievementService(
                config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage, this);
    }
}
