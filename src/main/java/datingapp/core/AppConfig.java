package datingapp.core;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Application configuration. Decomposed into four logical sub-records for readability; all original
 * component-level accessors are preserved as delegating methods for backward compatibility.
 *
 * <p>Use {@link #defaults()} for the default configuration, or {@link #builder()} for a custom one.
 */
public record AppConfig(
        MatchingConfig matching, ValidationConfig validation, AlgorithmConfig algorithm, SafetyConfig safety) {

    // ========================================================================
    // Sub-record: MatchingConfig
    // ========================================================================

    public static record MatchingConfig(
            int dailyLikeLimit,
            int dailySuperLikeLimit,
            int dailyPassLimit,
            int maxSwipesPerSession,
            double suspiciousSwipeVelocity,
            double distanceWeight,
            double ageWeight,
            double interestWeight,
            double lifestyleWeight,
            double paceWeight,
            double responseWeight,
            int minSharedInterests,
            int maxDistanceKm) {
        public MatchingConfig {
            if (dailyLikeLimit < -1) {
                throw new IllegalArgumentException("dailyLikeLimit must be >= -1");
            }
            if (dailyPassLimit < -1) {
                throw new IllegalArgumentException("dailyPassLimit must be >= -1");
            }
            requireNonNegative("dailySuperLikeLimit", dailySuperLikeLimit);
            requireNonNegative("maxSwipesPerSession", maxSwipesPerSession);
            requireNonNegative("suspiciousSwipeVelocity", suspiciousSwipeVelocity);
            requireNonNegative("distanceWeight", distanceWeight);
            requireNonNegative("ageWeight", ageWeight);
            requireNonNegative("interestWeight", interestWeight);
            requireNonNegative("lifestyleWeight", lifestyleWeight);
            requireNonNegative("paceWeight", paceWeight);
            requireNonNegative("responseWeight", responseWeight);
            requireNonNegative("minSharedInterests", minSharedInterests);
            requireNonNegative("maxDistanceKm", maxDistanceKm);
            double weightSum =
                    distanceWeight + ageWeight + interestWeight + lifestyleWeight + paceWeight + responseWeight;
            if (Math.abs(weightSum - 1.0) > 0.01) {
                throw new IllegalArgumentException("Match-quality weights must sum to 1.0, got: " + weightSum);
            }
        }
    }

    // ========================================================================
    // Sub-record: ValidationConfig
    // ========================================================================

    public static record ValidationConfig(
            int minAge,
            int maxAge,
            int minHeightCm,
            int maxHeightCm,
            int maxBioLength,
            int maxReportDescLength,
            int maxNameLength,
            int minAgeRangeSpan,
            int minDistanceKm,
            int maxInterests,
            int maxPhotos,
            int messageMaxPageSize) {
        public ValidationConfig {
            requireNonNegative("minAge", minAge);
            requireNonNegative("maxAge", maxAge);
            requireNonNegative("minHeightCm", minHeightCm);
            requireNonNegative("maxHeightCm", maxHeightCm);
            requireNonNegative("maxBioLength", maxBioLength);
            requireNonNegative("maxReportDescLength", maxReportDescLength);
            requireNonNegative("maxNameLength", maxNameLength);
            requireNonNegative("minAgeRangeSpan", minAgeRangeSpan);
            requireNonNegative("minDistanceKm", minDistanceKm);
            requireNonNegative("maxInterests", maxInterests);
            requireNonNegative("maxPhotos", maxPhotos);
            requireNonNegative("messageMaxPageSize", messageMaxPageSize);
        }
    }

    // ========================================================================
    // Sub-record: AlgorithmConfig
    // ========================================================================

    public static record AlgorithmConfig(
            int nearbyDistanceKm,
            int closeDistanceKm,
            int similarAgeDiff,
            int compatibleAgeDiff,
            int paceCompatibilityThreshold,
            int responseTimeExcellentHours,
            int responseTimeGreatHours,
            int responseTimeGoodHours,
            int responseTimeWeekHours,
            int responseTimeMonthHours,
            double standoutDistanceWeight,
            double standoutAgeWeight,
            double standoutInterestWeight,
            double standoutLifestyleWeight,
            double standoutCompletenessWeight,
            double standoutActivityWeight) {
        public AlgorithmConfig {
            requireNonNegative("nearbyDistanceKm", nearbyDistanceKm);
            requireNonNegative("closeDistanceKm", closeDistanceKm);
            requireNonNegative("similarAgeDiff", similarAgeDiff);
            requireNonNegative("compatibleAgeDiff", compatibleAgeDiff);
            requireNonNegative("paceCompatibilityThreshold", paceCompatibilityThreshold);
            requireNonNegative("responseTimeExcellentHours", responseTimeExcellentHours);
            requireNonNegative("responseTimeGreatHours", responseTimeGreatHours);
            requireNonNegative("responseTimeGoodHours", responseTimeGoodHours);
            requireNonNegative("responseTimeWeekHours", responseTimeWeekHours);
            requireNonNegative("responseTimeMonthHours", responseTimeMonthHours);
            requireNonNegative("standoutDistanceWeight", standoutDistanceWeight);
            requireNonNegative("standoutAgeWeight", standoutAgeWeight);
            requireNonNegative("standoutInterestWeight", standoutInterestWeight);
            requireNonNegative("standoutLifestyleWeight", standoutLifestyleWeight);
            requireNonNegative("standoutCompletenessWeight", standoutCompletenessWeight);
            requireNonNegative("standoutActivityWeight", standoutActivityWeight);
        }
    }

    // ========================================================================
    // Sub-record: SafetyConfig
    // ========================================================================

    public static record SafetyConfig(
            int autoBanThreshold,
            ZoneId userTimeZone,
            int sessionTimeoutMinutes,
            int undoWindowSeconds,
            int achievementMatchTier1,
            int achievementMatchTier2,
            int achievementMatchTier3,
            int achievementMatchTier4,
            int achievementMatchTier5,
            int minSwipesForBehaviorAchievement,
            double selectiveThreshold,
            double openMindedThreshold,
            int bioAchievementLength,
            int lifestyleFieldTarget,
            int cleanupRetentionDays,
            int softDeleteRetentionDays) {
        public SafetyConfig {
            Objects.requireNonNull(userTimeZone, "userTimeZone cannot be null");
            requireNonNegative("autoBanThreshold", autoBanThreshold);
            requireNonNegative("sessionTimeoutMinutes", sessionTimeoutMinutes);
            requireNonNegative("undoWindowSeconds", undoWindowSeconds);
            requireNonNegative("achievementMatchTier1", achievementMatchTier1);
            requireNonNegative("achievementMatchTier2", achievementMatchTier2);
            requireNonNegative("achievementMatchTier3", achievementMatchTier3);
            requireNonNegative("achievementMatchTier4", achievementMatchTier4);
            requireNonNegative("achievementMatchTier5", achievementMatchTier5);
            requireNonNegative("minSwipesForBehaviorAchievement", minSwipesForBehaviorAchievement);
            requireNonNegative("selectiveThreshold", selectiveThreshold);
            requireNonNegative("openMindedThreshold", openMindedThreshold);
            requireNonNegative("bioAchievementLength", bioAchievementLength);
            requireNonNegative("lifestyleFieldTarget", lifestyleFieldTarget);
            requireNonNegative("cleanupRetentionDays", cleanupRetentionDays);
            requireNonNegative("softDeleteRetentionDays", softDeleteRetentionDays);
        }
    }

    // ========================================================================
    // Compact constructor
    // ========================================================================

    public AppConfig {
        Objects.requireNonNull(matching, "matching cannot be null");
        Objects.requireNonNull(validation, "validation cannot be null");
        Objects.requireNonNull(algorithm, "algorithm cannot be null");
        Objects.requireNonNull(safety, "safety cannot be null");
    }

    // ========================================================================
    // Backward-compatible delegate accessors — matching sub-record
    // ========================================================================

    public int dailyLikeLimit() {
        return matching.dailyLikeLimit();
    }

    public int dailySuperLikeLimit() {
        return matching.dailySuperLikeLimit();
    }

    public int dailyPassLimit() {
        return matching.dailyPassLimit();
    }

    public int maxSwipesPerSession() {
        return matching.maxSwipesPerSession();
    }

    public double suspiciousSwipeVelocity() {
        return matching.suspiciousSwipeVelocity();
    }

    public double distanceWeight() {
        return matching.distanceWeight();
    }

    public double ageWeight() {
        return matching.ageWeight();
    }

    public double interestWeight() {
        return matching.interestWeight();
    }

    public double lifestyleWeight() {
        return matching.lifestyleWeight();
    }

    public double paceWeight() {
        return matching.paceWeight();
    }

    public double responseWeight() {
        return matching.responseWeight();
    }

    public int minSharedInterests() {
        return matching.minSharedInterests();
    }

    public int maxDistanceKm() {
        return matching.maxDistanceKm();
    }

    // ========================================================================
    // Backward-compatible delegate accessors — validation sub-record
    // ========================================================================

    public int minAge() {
        return validation.minAge();
    }

    public int maxAge() {
        return validation.maxAge();
    }

    public int minHeightCm() {
        return validation.minHeightCm();
    }

    public int maxHeightCm() {
        return validation.maxHeightCm();
    }

    public int maxBioLength() {
        return validation.maxBioLength();
    }

    public int maxReportDescLength() {
        return validation.maxReportDescLength();
    }

    public int maxNameLength() {
        return validation.maxNameLength();
    }

    public int minAgeRangeSpan() {
        return validation.minAgeRangeSpan();
    }

    public int minDistanceKm() {
        return validation.minDistanceKm();
    }

    public int maxInterests() {
        return validation.maxInterests();
    }

    public int maxPhotos() {
        return validation.maxPhotos();
    }

    public int messageMaxPageSize() {
        return validation.messageMaxPageSize();
    }

    // ========================================================================
    // Backward-compatible delegate accessors — algorithm sub-record
    // ========================================================================

    public int nearbyDistanceKm() {
        return algorithm.nearbyDistanceKm();
    }

    public int closeDistanceKm() {
        return algorithm.closeDistanceKm();
    }

    public int similarAgeDiff() {
        return algorithm.similarAgeDiff();
    }

    public int compatibleAgeDiff() {
        return algorithm.compatibleAgeDiff();
    }

    public int paceCompatibilityThreshold() {
        return algorithm.paceCompatibilityThreshold();
    }

    public int responseTimeExcellentHours() {
        return algorithm.responseTimeExcellentHours();
    }

    public int responseTimeGreatHours() {
        return algorithm.responseTimeGreatHours();
    }

    public int responseTimeGoodHours() {
        return algorithm.responseTimeGoodHours();
    }

    public int responseTimeWeekHours() {
        return algorithm.responseTimeWeekHours();
    }

    public int responseTimeMonthHours() {
        return algorithm.responseTimeMonthHours();
    }

    public double standoutDistanceWeight() {
        return algorithm.standoutDistanceWeight();
    }

    public double standoutAgeWeight() {
        return algorithm.standoutAgeWeight();
    }

    public double standoutInterestWeight() {
        return algorithm.standoutInterestWeight();
    }

    public double standoutLifestyleWeight() {
        return algorithm.standoutLifestyleWeight();
    }

    public double standoutCompletenessWeight() {
        return algorithm.standoutCompletenessWeight();
    }

    public double standoutActivityWeight() {
        return algorithm.standoutActivityWeight();
    }

    // ========================================================================
    // Backward-compatible delegate accessors — safety sub-record
    // ========================================================================

    public int autoBanThreshold() {
        return safety.autoBanThreshold();
    }

    public ZoneId userTimeZone() {
        return safety.userTimeZone();
    }

    public int sessionTimeoutMinutes() {
        return safety.sessionTimeoutMinutes();
    }

    public int undoWindowSeconds() {
        return safety.undoWindowSeconds();
    }

    public int achievementMatchTier1() {
        return safety.achievementMatchTier1();
    }

    public int achievementMatchTier2() {
        return safety.achievementMatchTier2();
    }

    public int achievementMatchTier3() {
        return safety.achievementMatchTier3();
    }

    public int achievementMatchTier4() {
        return safety.achievementMatchTier4();
    }

    public int achievementMatchTier5() {
        return safety.achievementMatchTier5();
    }

    public int minSwipesForBehaviorAchievement() {
        return safety.minSwipesForBehaviorAchievement();
    }

    public double selectiveThreshold() {
        return safety.selectiveThreshold();
    }

    public double openMindedThreshold() {
        return safety.openMindedThreshold();
    }

    public int bioAchievementLength() {
        return safety.bioAchievementLength();
    }

    public int lifestyleFieldTarget() {
        return safety.lifestyleFieldTarget();
    }

    public int cleanupRetentionDays() {
        return safety.cleanupRetentionDays();
    }

    public int softDeleteRetentionDays() {
        return safety.softDeleteRetentionDays();
    }

    // ========================================================================
    // Utility methods
    // ========================================================================

    /** Returns true when passes are unlimited. */
    public boolean hasUnlimitedPasses() {
        return matching.dailyPassLimit() < 0;
    }

    /** Returns true when likes are unlimited. */
    public boolean hasUnlimitedLikes() {
        return matching.dailyLikeLimit() < 0;
    }

    /** Session timeout as a {@link Duration}. */
    public Duration getSessionTimeout() {
        return Duration.ofMinutes(safety.sessionTimeoutMinutes());
    }

    // ========================================================================
    // Factory methods
    // ========================================================================

    /** Default application configuration. */
    public static AppConfig defaults() {
        return builder().build();
    }

    /** Builder for creating custom {@link AppConfig} instances. */
    public static Builder builder() {
        return new Builder();
    }

    // ========================================================================
    // Shared validation helpers (accessible from sub-record compact constructors)
    // ========================================================================

    private static void requireNonNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    // ========================================================================
    // Builder — flat setters for backward compatibility with existing call sites.
    // build() assembles the four sub-records internally.
    // ========================================================================

    public static class Builder {
        // MatchingConfig fields
        private int dailyLikeLimit = 100;
        private int dailySuperLikeLimit = 1;
        private int dailyPassLimit = -1;
        private int maxSwipesPerSession = 500;
        private double suspiciousSwipeVelocity = 30.0;
        private double distanceWeight = 0.15;
        private double ageWeight = 0.10;
        private double interestWeight = 0.25;
        private double lifestyleWeight = 0.25;
        private double paceWeight = 0.15;
        private double responseWeight = 0.10;
        private int minSharedInterests = 3;
        private int maxDistanceKm = 500;
        // ValidationConfig fields
        private int minAge = 18;
        private int maxAge = 120;
        private int minHeightCm = 50;
        private int maxHeightCm = 300;
        private int maxBioLength = 500;
        private int maxReportDescLength = 500;
        private int maxNameLength = 100;
        private int minAgeRangeSpan = 5;
        private int minDistanceKm = 1;
        private int maxInterests = 10;
        private int maxPhotos = 2;
        private int messageMaxPageSize = 100;
        // AlgorithmConfig fields
        private int nearbyDistanceKm = 5;
        private int closeDistanceKm = 10;
        private int similarAgeDiff = 2;
        private int compatibleAgeDiff = 5;
        private int paceCompatibilityThreshold = 50;
        private int responseTimeExcellentHours = 1;
        private int responseTimeGreatHours = 24;
        private int responseTimeGoodHours = 72;
        private int responseTimeWeekHours = 168;
        private int responseTimeMonthHours = 720;
        private double standoutDistanceWeight = 0.20;
        private double standoutAgeWeight = 0.15;
        private double standoutInterestWeight = 0.25;
        private double standoutLifestyleWeight = 0.20;
        private double standoutCompletenessWeight = 0.10;
        private double standoutActivityWeight = 0.10;
        // SafetyConfig fields
        private int autoBanThreshold = 3;
        private ZoneId userTimeZone = ZoneId.systemDefault();
        private int sessionTimeoutMinutes = 5;
        private int undoWindowSeconds = 30;
        private int achievementMatchTier1 = 1;
        private int achievementMatchTier2 = 5;
        private int achievementMatchTier3 = 10;
        private int achievementMatchTier4 = 25;
        private int achievementMatchTier5 = 50;
        private int minSwipesForBehaviorAchievement = 50;
        private double selectiveThreshold = 0.20;
        private double openMindedThreshold = 0.60;
        private int bioAchievementLength = 100;
        private int lifestyleFieldTarget = 5;
        private int cleanupRetentionDays = 30;
        private int softDeleteRetentionDays = 90;

        public Builder autoBanThreshold(int v) {
            this.autoBanThreshold = v;
            return this;
        }

        public Builder dailyLikeLimit(int v) {
            this.dailyLikeLimit = v;
            return this;
        }

        public Builder dailySuperLikeLimit(int v) {
            this.dailySuperLikeLimit = v;
            return this;
        }

        public Builder dailyPassLimit(int v) {
            this.dailyPassLimit = v;
            return this;
        }

        public Builder userTimeZone(ZoneId v) {
            this.userTimeZone = v;
            return this;
        }

        public Builder maxInterests(int v) {
            this.maxInterests = v;
            return this;
        }

        public Builder maxPhotos(int v) {
            this.maxPhotos = v;
            return this;
        }

        public Builder maxBioLength(int v) {
            this.maxBioLength = v;
            return this;
        }

        public Builder maxReportDescLength(int v) {
            this.maxReportDescLength = v;
            return this;
        }

        public Builder sessionTimeoutMinutes(int v) {
            this.sessionTimeoutMinutes = v;
            return this;
        }

        public Builder maxSwipesPerSession(int v) {
            this.maxSwipesPerSession = v;
            return this;
        }

        public Builder suspiciousSwipeVelocity(double v) {
            this.suspiciousSwipeVelocity = v;
            return this;
        }

        public Builder undoWindowSeconds(int v) {
            this.undoWindowSeconds = v;
            return this;
        }

        public Builder nearbyDistanceKm(int v) {
            this.nearbyDistanceKm = v;
            return this;
        }

        public Builder closeDistanceKm(int v) {
            this.closeDistanceKm = v;
            return this;
        }

        public Builder similarAgeDiff(int v) {
            this.similarAgeDiff = v;
            return this;
        }

        public Builder compatibleAgeDiff(int v) {
            this.compatibleAgeDiff = v;
            return this;
        }

        public Builder minSharedInterests(int v) {
            this.minSharedInterests = v;
            return this;
        }

        public Builder paceCompatibilityThreshold(int v) {
            this.paceCompatibilityThreshold = v;
            return this;
        }

        public Builder responseTimeExcellentHours(int v) {
            this.responseTimeExcellentHours = v;
            return this;
        }

        public Builder responseTimeGreatHours(int v) {
            this.responseTimeGreatHours = v;
            return this;
        }

        public Builder responseTimeGoodHours(int v) {
            this.responseTimeGoodHours = v;
            return this;
        }

        public Builder responseTimeWeekHours(int v) {
            this.responseTimeWeekHours = v;
            return this;
        }

        public Builder responseTimeMonthHours(int v) {
            this.responseTimeMonthHours = v;
            return this;
        }

        public Builder achievementMatchTier1(int v) {
            this.achievementMatchTier1 = v;
            return this;
        }

        public Builder achievementMatchTier2(int v) {
            this.achievementMatchTier2 = v;
            return this;
        }

        public Builder achievementMatchTier3(int v) {
            this.achievementMatchTier3 = v;
            return this;
        }

        public Builder achievementMatchTier4(int v) {
            this.achievementMatchTier4 = v;
            return this;
        }

        public Builder achievementMatchTier5(int v) {
            this.achievementMatchTier5 = v;
            return this;
        }

        public Builder minSwipesForBehaviorAchievement(int v) {
            this.minSwipesForBehaviorAchievement = v;
            return this;
        }

        public Builder maxDistanceKm(int v) {
            this.maxDistanceKm = v;
            return this;
        }

        public Builder maxAge(int v) {
            this.maxAge = v;
            return this;
        }

        public Builder minAge(int v) {
            this.minAge = v;
            return this;
        }

        public Builder minHeightCm(int v) {
            this.minHeightCm = v;
            return this;
        }

        public Builder maxHeightCm(int v) {
            this.maxHeightCm = v;
            return this;
        }

        public Builder minDistanceKm(int v) {
            this.minDistanceKm = v;
            return this;
        }

        public Builder maxNameLength(int v) {
            this.maxNameLength = v;
            return this;
        }

        public Builder minAgeRangeSpan(int v) {
            this.minAgeRangeSpan = v;
            return this;
        }

        public Builder distanceWeight(double v) {
            this.distanceWeight = v;
            return this;
        }

        public Builder ageWeight(double v) {
            this.ageWeight = v;
            return this;
        }

        public Builder interestWeight(double v) {
            this.interestWeight = v;
            return this;
        }

        public Builder lifestyleWeight(double v) {
            this.lifestyleWeight = v;
            return this;
        }

        public Builder paceWeight(double v) {
            this.paceWeight = v;
            return this;
        }

        public Builder responseWeight(double v) {
            this.responseWeight = v;
            return this;
        }

        public Builder cleanupRetentionDays(int v) {
            this.cleanupRetentionDays = v;
            return this;
        }

        public Builder standoutDistanceWeight(double v) {
            this.standoutDistanceWeight = v;
            return this;
        }

        public Builder standoutAgeWeight(double v) {
            this.standoutAgeWeight = v;
            return this;
        }

        public Builder standoutInterestWeight(double v) {
            this.standoutInterestWeight = v;
            return this;
        }

        public Builder standoutLifestyleWeight(double v) {
            this.standoutLifestyleWeight = v;
            return this;
        }

        public Builder standoutCompletenessWeight(double v) {
            this.standoutCompletenessWeight = v;
            return this;
        }

        public Builder standoutActivityWeight(double v) {
            this.standoutActivityWeight = v;
            return this;
        }

        public Builder selectiveThreshold(double v) {
            this.selectiveThreshold = v;
            return this;
        }

        public Builder openMindedThreshold(double v) {
            this.openMindedThreshold = v;
            return this;
        }

        public Builder bioAchievementLength(int v) {
            this.bioAchievementLength = v;
            return this;
        }

        public Builder lifestyleFieldTarget(int v) {
            this.lifestyleFieldTarget = v;
            return this;
        }

        public Builder messageMaxPageSize(int v) {
            this.messageMaxPageSize = v;
            return this;
        }

        public Builder softDeleteRetentionDays(int v) {
            this.softDeleteRetentionDays = v;
            return this;
        }

        public AppConfig build() {
            MatchingConfig matchingConfig = new MatchingConfig(
                    dailyLikeLimit,
                    dailySuperLikeLimit,
                    dailyPassLimit,
                    maxSwipesPerSession,
                    suspiciousSwipeVelocity,
                    distanceWeight,
                    ageWeight,
                    interestWeight,
                    lifestyleWeight,
                    paceWeight,
                    responseWeight,
                    minSharedInterests,
                    maxDistanceKm);
            ValidationConfig validationConfig = new ValidationConfig(
                    minAge,
                    maxAge,
                    minHeightCm,
                    maxHeightCm,
                    maxBioLength,
                    maxReportDescLength,
                    maxNameLength,
                    minAgeRangeSpan,
                    minDistanceKm,
                    maxInterests,
                    maxPhotos,
                    messageMaxPageSize);
            AlgorithmConfig algorithmConfig = new AlgorithmConfig(
                    nearbyDistanceKm,
                    closeDistanceKm,
                    similarAgeDiff,
                    compatibleAgeDiff,
                    paceCompatibilityThreshold,
                    responseTimeExcellentHours,
                    responseTimeGreatHours,
                    responseTimeGoodHours,
                    responseTimeWeekHours,
                    responseTimeMonthHours,
                    standoutDistanceWeight,
                    standoutAgeWeight,
                    standoutInterestWeight,
                    standoutLifestyleWeight,
                    standoutCompletenessWeight,
                    standoutActivityWeight);
            SafetyConfig safetyConfig = new SafetyConfig(
                    autoBanThreshold,
                    userTimeZone,
                    sessionTimeoutMinutes,
                    undoWindowSeconds,
                    achievementMatchTier1,
                    achievementMatchTier2,
                    achievementMatchTier3,
                    achievementMatchTier4,
                    achievementMatchTier5,
                    minSwipesForBehaviorAchievement,
                    selectiveThreshold,
                    openMindedThreshold,
                    bioAchievementLength,
                    lifestyleFieldTarget,
                    cleanupRetentionDays,
                    softDeleteRetentionDays);
            return new AppConfig(matchingConfig, validationConfig, algorithmConfig, safetyConfig);
        }
    }
}
