package datingapp.core;

import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Application configuration grouped by concern into five sub-records.
 *
 * <p>Use {@link #defaults()} for the default configuration, or {@link #builder()} for a custom one.
 */
public record AppConfig(
        MatchingConfig matching,
        ValidationConfig validation,
        AlgorithmConfig algorithm,
        StorageConfig storage,
        SafetyConfig safety) {

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
            AppConfigValidator.validateMatching(
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
                    minSharedInterests);
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
            int maxMessageLength,
            int maxProfileNoteLength,
            int minAgeRangeSpan,
            int minDistanceKm,
            int maxDistanceKm,
            int maxInterests,
            int maxPhotos,
            int messageMaxPageSize,
            int chatBackgroundPollSeconds,
            int chatActivePollSeconds,
            int maxStandouts) {
        public ValidationConfig {
            AppConfigValidator.validateValidation(
                    minAge,
                    maxAge,
                    minHeightCm,
                    maxHeightCm,
                    maxBioLength,
                    maxReportDescLength,
                    maxNameLength,
                    maxMessageLength,
                    maxProfileNoteLength,
                    minAgeRangeSpan,
                    minDistanceKm,
                    maxDistanceKm,
                    maxInterests,
                    maxPhotos,
                    messageMaxPageSize,
                    chatBackgroundPollSeconds,
                    chatActivePollSeconds,
                    maxStandouts);
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
            int standoutDiversityDays,
            int standoutMinScore,
            int starExcellentThreshold,
            int starGreatThreshold,
            int starGoodThreshold,
            int starFairThreshold,
            double standoutDistanceWeight,
            double standoutAgeWeight,
            double standoutInterestWeight,
            double standoutLifestyleWeight,
            double standoutCompletenessWeight,
            double standoutActivityWeight) {
        public AlgorithmConfig {
            AppConfigValidator.validateAlgorithm(
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
                    standoutDiversityDays,
                    standoutMinScore,
                    starExcellentThreshold,
                    starGreatThreshold,
                    starGoodThreshold,
                    starFairThreshold,
                    standoutDistanceWeight,
                    standoutAgeWeight,
                    standoutInterestWeight,
                    standoutLifestyleWeight,
                    standoutCompletenessWeight,
                    standoutActivityWeight);
        }
    }

    // ========================================================================
    // Sub-record: StorageConfig
    // ========================================================================

    public static record StorageConfig(int queryTimeoutSeconds) {
        public StorageConfig {
            AppConfigValidator.validateStorage(queryTimeoutSeconds);
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
            AppConfigValidator.validateSafety(
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
        }
    }

    // ========================================================================
    // Compact constructor
    // ========================================================================

    public AppConfig {
        Objects.requireNonNull(matching, "matching cannot be null");
        Objects.requireNonNull(validation, "validation cannot be null");
        Objects.requireNonNull(algorithm, "algorithm cannot be null");
        Objects.requireNonNull(storage, "storage cannot be null");
        Objects.requireNonNull(safety, "safety cannot be null");
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
    // Builder — flat setters for backward compatibility with existing call sites.
    // build() assembles the four sub-records through named section builders.
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
        private int maxMessageLength = Message.MAX_LENGTH;
        private int maxProfileNoteLength = ProfileNote.MAX_LENGTH;
        private int minAgeRangeSpan = 5;
        private int minDistanceKm = 1;
        private int maxInterests = 10;
        private int maxPhotos = User.MAX_PHOTOS;
        private int messageMaxPageSize = 100;
        private int chatBackgroundPollSeconds = 15;
        private int chatActivePollSeconds = 5;
        private int maxStandouts = 10;
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
        private int standoutDiversityDays = 3;
        private int standoutMinScore = 40;
        private int starExcellentThreshold = 90;
        private int starGreatThreshold = 75;
        private int starGoodThreshold = 60;
        private int starFairThreshold = 40;
        private double standoutDistanceWeight = 0.20;
        private double standoutAgeWeight = 0.15;
        private double standoutInterestWeight = 0.25;
        private double standoutLifestyleWeight = 0.20;
        private double standoutCompletenessWeight = 0.10;
        private double standoutActivityWeight = 0.10;
        // StorageConfig fields
        private int queryTimeoutSeconds = 30;
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

        public Builder queryTimeoutSeconds(int v) {
            this.queryTimeoutSeconds = v;
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

        public Builder standoutDiversityDays(int v) {
            this.standoutDiversityDays = v;
            return this;
        }

        public Builder standoutMinScore(int v) {
            this.standoutMinScore = v;
            return this;
        }

        public Builder starExcellentThreshold(int v) {
            this.starExcellentThreshold = v;
            return this;
        }

        public Builder starGreatThreshold(int v) {
            this.starGreatThreshold = v;
            return this;
        }

        public Builder starGoodThreshold(int v) {
            this.starGoodThreshold = v;
            return this;
        }

        public Builder starFairThreshold(int v) {
            this.starFairThreshold = v;
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

        public Builder maxMessageLength(int v) {
            this.maxMessageLength = v;
            return this;
        }

        public Builder maxProfileNoteLength(int v) {
            this.maxProfileNoteLength = v;
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

        public Builder chatBackgroundPollSeconds(int v) {
            this.chatBackgroundPollSeconds = v;
            return this;
        }

        public Builder chatActivePollSeconds(int v) {
            this.chatActivePollSeconds = v;
            return this;
        }

        public Builder maxStandouts(int v) {
            this.maxStandouts = v;
            return this;
        }

        public Builder softDeleteRetentionDays(int v) {
            this.softDeleteRetentionDays = v;
            return this;
        }

        public AppConfig build() {
            return new AppConfig(
                    buildMatchingConfig(),
                    buildValidationConfig(),
                    buildAlgorithmConfig(),
                    buildStorageConfig(),
                    buildSafetyConfig());
        }

        private MatchingConfig buildMatchingConfig() {
            return new MatchingConfig(
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
        }

        private ValidationConfig buildValidationConfig() {
            return new ValidationConfig(
                    minAge,
                    maxAge,
                    minHeightCm,
                    maxHeightCm,
                    maxBioLength,
                    maxReportDescLength,
                    maxNameLength,
                    maxMessageLength,
                    maxProfileNoteLength,
                    minAgeRangeSpan,
                    minDistanceKm,
                    maxDistanceKm,
                    maxInterests,
                    maxPhotos,
                    messageMaxPageSize,
                    chatBackgroundPollSeconds,
                    chatActivePollSeconds,
                    maxStandouts);
        }

        private AlgorithmConfig buildAlgorithmConfig() {
            return new AlgorithmConfig(
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
                    standoutDiversityDays,
                    standoutMinScore,
                    starExcellentThreshold,
                    starGreatThreshold,
                    starGoodThreshold,
                    starFairThreshold,
                    standoutDistanceWeight,
                    standoutAgeWeight,
                    standoutInterestWeight,
                    standoutLifestyleWeight,
                    standoutCompletenessWeight,
                    standoutActivityWeight);
        }

        private StorageConfig buildStorageConfig() {
            return new StorageConfig(queryTimeoutSeconds);
        }

        private SafetyConfig buildSafetyConfig() {
            return new SafetyConfig(
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
        }
    }
}
