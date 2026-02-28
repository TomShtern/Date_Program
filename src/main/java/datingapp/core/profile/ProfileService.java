package datingapp.core.profile;

import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.util.ArrayList;
import java.util.EnumMap;
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

    // ── Profile completion thresholds (inlined from deleted ScoringConstants) ──
    private static final int BIO_TIP_MIN_LENGTH = 50;
    private static final int BIO_TIP_BOOST_LENGTH = 100;
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
    private static final int INTERESTS_PARTIAL_HIGH_COUNT = 3;
    private static final int INTERESTS_PARTIAL_HIGH_POINTS = 15;
    private static final int INTERESTS_PARTIAL_LOW_POINTS = 10;
    private static final int LIFESTYLE_FIELD_POINTS = 5;
    private static final int PREFERENCES_FIELD_POINTS = 5;
    private static final int TIER_DIAMOND_THRESHOLD = 95;
    private static final int TIER_GOLD_THRESHOLD = 85;
    private static final int TIER_SILVER_THRESHOLD = 70;
    private static final int TIER_BRONZE_THRESHOLD = 50;
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

    /** Canonical constructor — all dependencies are required. */
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

    // ========================================================================
    // Records
    // ========================================================================

    /** Result of detailed completion analysis with category breakdowns. */
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

    /** Result of simple profile completeness calculation. */
    public static record ProfileCompleteness(int percentage, List<String> filledFields, List<String> missingFields) {

        public ProfileCompleteness {
            if (percentage < 0 || percentage > 100) {
                throw new IllegalArgumentException("percentage must be 0-100, got: " + percentage);
            }
            filledFields = filledFields != null ? List.copyOf(filledFields) : List.of();
            missingFields = missingFields != null ? List.copyOf(missingFields) : List.of();
        }
    }

    /** Full profile preview result. */
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
            displayBio = displayBio != null ? displayBio.trim() : null;
            displayLookingFor = displayLookingFor != null ? displayLookingFor.trim() : null;
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
                    "Birth date",
                    BASIC_BIRTHDATE_POINTS,
                    u -> u.getBirthDate() != null,
                    "Add your birth date to complete your profile"),
            new FieldCheck("Gender", BASIC_GENDER_POINTS, u -> u.getGender() != null),
            new FieldCheck(
                    "Interested in",
                    BASIC_INTERESTED_POINTS,
                    u -> u.getInterestedIn() != null && !u.getInterestedIn().isEmpty()),
            new FieldCheck(
                    "Photo",
                    BASIC_PHOTO_POINTS,
                    u -> u.getPhotoUrls() != null && !u.getPhotoUrls().isEmpty(),
                    "📸 Add a photo - profiles with photos get 10x more matches!"));

    private static final List<FieldCheck> LIFESTYLE_FIELDS = List.of(
            new FieldCheck("Height", LIFESTYLE_FIELD_POINTS, u -> u.getHeightCm() != null),
            new FieldCheck("Smoking", LIFESTYLE_FIELD_POINTS, u -> u.getSmoking() != null),
            new FieldCheck("Drinking", LIFESTYLE_FIELD_POINTS, u -> u.getDrinking() != null),
            new FieldCheck("Kids preference", LIFESTYLE_FIELD_POINTS, u -> u.getWantsKids() != null),
            new FieldCheck("Looking for", LIFESTYLE_FIELD_POINTS, u -> u.getLookingFor() != null));

    // ========================================================================
    // Detailed completion analysis (category-based scoring)
    // ========================================================================

    /**
     * Calculate the detailed completion result for a user with category breakdowns.
     */
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
        List<String> photoUrls = user.getPhotoUrls() != null ? user.getPhotoUrls() : List.of();
        Set<Interest> interests = user.getInterests() != null ? user.getInterests() : Set.of();

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
        checkField("Photo", !photoUrls.isEmpty(), filled, missing);

        // Lifestyle fields (optional but encouraged)
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

    /** Generate improvement tips based on profile state. */
    public List<String> generateTips(User user) {
        List<String> tips = new ArrayList<>();
        List<String> photoUrls = user.getPhotoUrls() != null ? user.getPhotoUrls() : List.of();
        Set<Interest> interests = user.getInterests() != null ? user.getInterests() : Set.of();

        // Bio tips
        if (user.getBio() == null || user.getBio().isBlank()) {
            tips.add("📝 Add a bio to tell others about yourself");
        } else if (user.getBio().length() < BIO_TIP_MIN_LENGTH) {
            tips.add("💡 Expand your bio - profiles with " + BIO_TIP_BOOST_LENGTH + "+ chars get 2x more likes");
        }

        // Photo tips
        if (photoUrls.isEmpty()) {
            tips.add("📸 Add a photo - it's required for browsing");
        } else if (photoUrls.size() < PHOTO_TIP_MIN_COUNT) {
            tips.add("📸 Add a second photo - users with 2 photos get 40% more matches");
        }

        // Lifestyle tips
        if (user.getLookingFor() == null) {
            tips.add("💝 Share what you're looking for - helps find compatible matches");
        }
        if (user.getHeightCm() == null) {
            tips.add("📏 Add your height - many users filter by height");
        }
        if (countLifestyleFields(user) < LIFESTYLE_FIELDS_MIN) {
            tips.add("🧘 Complete more lifestyle fields for better match quality");
        }

        // Distance tips
        if (user.getMaxDistanceKm() < DISTANCE_TIP_MAX_KM) {
            tips.add("📍 Consider expanding your distance for more options");
        }

        // Age range tips
        if (user.getMaxAge() - user.getMinAge() < AGE_RANGE_TIP_MIN_YEARS) {
            tips.add("🎂 A wider age range gives you more potential matches");
        }

        // Interest tips
        int interestCount = interests.size();
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

    // ========================================================================
    // Private helpers
    // ========================================================================

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
        CategoryBreakdown breakdown = new CategoryBreakdown(name, score, filled, missing);
        return new CategoryResult(
                earnedPoints, totalPoints, filled.size(), filled.size() + missing.size(), breakdown, nextSteps);
    }

    private CategoryResult scoreBasicInfo(User user) {
        return scoreCategory("Basic Info", BASIC_FIELDS, user);
    }

    private CategoryResult scoreInterests(User user) {
        int totalPoints = INTERESTS_TOTAL_POINTS;
        int earnedPoints = 0;
        List<String> interestsFilled = new ArrayList<>();
        List<String> interestsMissing = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();

        int interestCount = user.getInterests() != null ? user.getInterests().size() : 0;
        if (interestCount >= INTERESTS_FULL_COUNT) {
            earnedPoints += INTERESTS_TOTAL_POINTS;
            interestsFilled.add(INTERESTS_FULL_COUNT + "+ interests selected");
        } else if (interestCount >= INTERESTS_PARTIAL_HIGH_COUNT) {
            earnedPoints += INTERESTS_PARTIAL_HIGH_POINTS;
            interestsFilled.add(interestCount + " interests selected");
            interestsMissing.add("Add " + (INTERESTS_FULL_COUNT - interestCount) + " more interests for full score");
            nextSteps.add("🎯 Add more interests to improve match quality");
        } else if (interestCount >= 1) {
            earnedPoints += INTERESTS_PARTIAL_LOW_POINTS;
            interestsFilled.add(interestCount + " interest(s) selected");
            interestsMissing.add("Add " + (INTERESTS_FULL_COUNT - interestCount) + " more interests for full score");
            nextSteps.add("🎯 Add more interests to improve match quality");
        } else {
            interestsMissing.add("No interests selected");
            nextSteps.add("🎯 Add your interests to find compatible matches");
        }

        int interestsScore =
                (interestCount >= INTERESTS_FULL_COUNT) ? 100 : (interestCount * 100 / INTERESTS_FULL_COUNT);
        CategoryBreakdown breakdown =
                new CategoryBreakdown("Interests", interestsScore, interestsFilled, interestsMissing);

        int filledCount = interestCount > 0 ? 1 : 0;
        return new CategoryResult(earnedPoints, totalPoints, filledCount, 1, breakdown, nextSteps);
    }

    private CategoryResult scoreLifestyle(User user) {
        CategoryResult result = scoreCategory("Lifestyle", LIFESTYLE_FIELDS, user);
        // Lifestyle adds a single "complete the section" tip when any field is missing,
        // rather than per-field tips — append it if missing items exist.
        if (!result.breakdown().missingItems().isEmpty()) {
            List<String> steps = new ArrayList<>(result.nextSteps());
            steps.add("💫 Complete your lifestyle section for better matches");
            return new CategoryResult(
                    result.earnedPoints(),
                    result.totalPoints(),
                    result.filledCount(),
                    result.totalCount(),
                    result.breakdown(),
                    steps);
        }
        return result;
    }

    private CategoryResult scorePreferences(User user) {
        int totalPoints = 0;
        int earnedPoints = 0;
        List<String> prefsFilled = new ArrayList<>();
        List<String> prefsMissing = new ArrayList<>();

        totalPoints += PREFERENCES_FIELD_POINTS;
        if (user.getLat() != 0.0 || user.getLon() != 0.0) {
            earnedPoints += PREFERENCES_FIELD_POINTS;
            prefsFilled.add("Location");
        } else {
            prefsMissing.add("Location");
        }

        totalPoints += PREFERENCES_FIELD_POINTS;
        if (user.getMinAge() >= config.validation().minAge()
                && user.getMaxAge() <= config.validation().maxAge()
                && user.getMinAge() <= user.getMaxAge()) {
            earnedPoints += PREFERENCES_FIELD_POINTS;
            prefsFilled.add("Age preferences");
        } else {
            prefsMissing.add("Age preferences (invalid range)");
        }

        totalPoints += PREFERENCES_FIELD_POINTS;
        if (user.getDealbreakers() != null) {
            earnedPoints += PREFERENCES_FIELD_POINTS;
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

    // ========================================================================
    // Achievement tracking
    // ========================================================================

    /** Progress towards an achievement. */
    public static record AchievementProgress(Achievement achievement, int current, int target, boolean unlocked) {

        public AchievementProgress {
            Objects.requireNonNull(achievement, "achievement cannot be null");
            if (current < 0 || target <= 0) {
                throw new IllegalArgumentException("current and target must be positive");
            }
            if (unlocked && current < target) {
                throw new IllegalArgumentException("unlocked achievements must meet target");
            }
        }

        public int getProgressPercent() {
            if (unlocked || target == 0) {
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

    /** Check all achievements for a user and unlock any newly earned ones. */
    public List<UserAchievement> checkAndUnlock(UUID userId) {
        List<UserAchievement> newlyUnlocked = new ArrayList<>();
        User user = userStorage.get(userId).orElse(null);
        if (user == null) {
            return newlyUnlocked;
        }

        for (Achievement achievement : Achievement.values()) {
            if (!analyticsStorage.hasAchievement(userId, achievement) && isEarned(userId, user, achievement)) {
                UserAchievement unlocked = UserAchievement.create(userId, achievement);
                analyticsStorage.saveUserAchievement(unlocked);
                newlyUnlocked.add(unlocked);
            }
        }

        return newlyUnlocked;
    }

    /** Get all unlocked achievements for a user. */
    public List<UserAchievement> getUnlocked(UUID userId) {
        return analyticsStorage.getUnlockedAchievements(userId);
    }

    /** Get progress for all achievements (both locked and unlocked). */
    public List<AchievementProgress> getProgress(UUID userId) {
        List<AchievementProgress> progress = new ArrayList<>();
        User user = userStorage.get(userId).orElse(null);
        if (user == null) {
            return progress;
        }

        for (Achievement achievement : Achievement.values()) {
            boolean unlocked = analyticsStorage.hasAchievement(userId, achievement);
            int[] currentAndTarget = getProgressValues(userId, user, achievement);
            progress.add(new AchievementProgress(achievement, currentAndTarget[0], currentAndTarget[1], unlocked));
        }

        return progress;
    }

    /** Get progress grouped by category. */
    public Map<Achievement.Category, List<AchievementProgress>> getProgressByCategory(UUID userId) {
        List<AchievementProgress> allProgress = getProgress(userId);
        Map<Achievement.Category, List<AchievementProgress>> grouped = new EnumMap<>(Achievement.Category.class);

        for (Achievement.Category category : Achievement.Category.values()) {
            grouped.put(category, new ArrayList<>());
        }

        for (AchievementProgress progress : allProgress) {
            grouped.get(progress.achievement().getCategory()).add(progress);
        }

        return grouped;
    }

    /** Count total unlocked achievements. */
    public int countUnlocked(UUID userId) {
        return analyticsStorage.countUnlockedAchievements(userId);
    }

    private boolean isEarned(UUID userId, User user, Achievement achievement) {
        return switch (achievement) {
            case FIRST_SPARK -> getMatchCount(userId) >= config.safety().achievementMatchTier1();
            case SOCIAL_BUTTERFLY -> getMatchCount(userId) >= config.safety().achievementMatchTier2();
            case POPULAR -> getMatchCount(userId) >= config.safety().achievementMatchTier3();
            case SUPERSTAR -> getMatchCount(userId) >= config.safety().achievementMatchTier4();
            case LEGEND -> getMatchCount(userId) >= config.safety().achievementMatchTier5();
            case SELECTIVE -> isSelective(userId);
            case OPEN_MINDED -> isOpenMinded(userId);
            case COMPLETE_PACKAGE -> isProfileComplete(user);
            case STORYTELLER -> hasBioOver100Chars(user);
            case LIFESTYLE_GURU -> hasAllLifestyleFields(user);
            case GUARDIAN -> hasReportedUser(userId);
        };
    }

    private int[] getProgressValues(UUID userId, User user, Achievement achievement) {
        int matchCount = getMatchCount(userId);
        return switch (achievement) {
            case FIRST_SPARK -> new int[] {matchCount, config.safety().achievementMatchTier1()};
            case SOCIAL_BUTTERFLY -> new int[] {matchCount, config.safety().achievementMatchTier2()};
            case POPULAR -> new int[] {matchCount, config.safety().achievementMatchTier3()};
            case SUPERSTAR -> new int[] {matchCount, config.safety().achievementMatchTier4()};
            case LEGEND -> new int[] {matchCount, config.safety().achievementMatchTier5()};
            case SELECTIVE, OPEN_MINDED ->
                new int[] {getTotalSwipes(userId), config.safety().minSwipesForBehaviorAchievement()};
            case COMPLETE_PACKAGE -> new int[] {getProfileCompleteness(user), 100};
            case STORYTELLER -> new int[] {getBioLength(user), config.safety().bioAchievementLength()};
            case LIFESTYLE_GURU ->
                new int[] {countLifestyleFields(user), config.safety().lifestyleFieldTarget()};
            case GUARDIAN -> new int[] {getReportsGiven(userId), 1};
        };
    }

    private int getMatchCount(UUID userId) {
        return interactionStorage.getAllMatchesFor(userId).size();
    }

    private int getTotalSwipes(UUID userId) {
        return interactionStorage.countByDirection(userId, Like.Direction.LIKE)
                + interactionStorage.countByDirection(userId, Like.Direction.PASS);
    }

    private boolean isSelective(UUID userId) {
        int totalSwipes = getTotalSwipes(userId);
        if (totalSwipes < config.safety().minSwipesForBehaviorAchievement()) {
            return false;
        }
        int likes = interactionStorage.countByDirection(userId, Like.Direction.LIKE);
        double likeRatio = (double) likes / totalSwipes;
        return likeRatio < config.safety().selectiveThreshold();
    }

    private boolean isOpenMinded(UUID userId) {
        int totalSwipes = getTotalSwipes(userId);
        if (totalSwipes < config.safety().minSwipesForBehaviorAchievement()) {
            return false;
        }
        int likes = interactionStorage.countByDirection(userId, Like.Direction.LIKE);
        double likeRatio = (double) likes / totalSwipes;
        return likeRatio > config.safety().openMindedThreshold();
    }

    private boolean isProfileComplete(User user) {
        return getProfileCompleteness(user) == 100;
    }

    private int getProfileCompleteness(User user) {
        ProfileCompleteness completeness = calculateCompleteness(user);
        return completeness.percentage();
    }

    private boolean hasBioOver100Chars(User user) {
        return getBioLength(user) > config.safety().bioAchievementLength();
    }

    private int getBioLength(User user) {
        return user.getBio() != null ? user.getBio().length() : 0;
    }

    private boolean hasAllLifestyleFields(User user) {
        return countLifestyleFields(user) >= config.safety().lifestyleFieldTarget();
    }

    private boolean hasReportedUser(UUID userId) {
        return getReportsGiven(userId) >= 1;
    }

    private int getReportsGiven(UUID userId) {
        return trustSafetyStorage.countReportsBy(userId);
    }
}
