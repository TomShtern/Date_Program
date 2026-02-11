package datingapp.core;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

public record AppConfig(
        int autoBanThreshold, // Number of reports before auto-ban
        int dailyLikeLimit, // Max likes per day (-1 = unlimited)
        int dailySuperLikeLimit, // Max super likes per day
        int dailyPassLimit, // Max passes per day (-1 = unlimited)
        ZoneId userTimeZone, // Timezone for daily limit reset at midnight
        int maxInterests, // Max interests per user
        int maxPhotos, // Max photos per user
        int maxBioLength, // Max bio length
        int maxReportDescLength, // Max report description length
        // Session tracking (Phase 0.5b)
        int sessionTimeoutMinutes, // Minutes of inactivity before session ends
        int maxSwipesPerSession, // Anti-bot: max swipes in single session
        double suspiciousSwipeVelocity, // Anti-bot: swipes/min threshold for warning
        // Undo feature (Phase 1)
        int undoWindowSeconds, // Time window for undo in seconds (30)
        // Algorithm thresholds (MED-01: centralized from hardcoded values)
        int nearbyDistanceKm, // Distance considered "nearby" (5km default)
        int closeDistanceKm, // Distance considered "close" (10km default)
        int similarAgeDiff, // Age difference considered "similar" (2 years)
        int compatibleAgeDiff, // Age difference considered "compatible" (5 years)
        int minSharedInterests, // Min shared interests for "many" (3)
        int paceCompatibilityThreshold, // Min pace score for compatibility (50)
        int responseTimeExcellentHours, // Response time for "excellent" (1)
        int responseTimeGreatHours, // Response time for "great" (24)
        int responseTimeGoodHours, // Response time for "good" (72)
        int responseTimeWeekHours, // Response time threshold for "okay" (168)
        int responseTimeMonthHours, // Response time threshold for "low" (720)
        int achievementMatchTier1, // First match milestone (1)
        int achievementMatchTier2, // Second match milestone (5)
        int achievementMatchTier3, // Third match milestone (10)
        int achievementMatchTier4, // Fourth match milestone (25)
        int achievementMatchTier5, // Fifth match milestone (50)
        int minSwipesForBehaviorAchievement, // Min swipes to evaluate behavior (50)
        int maxDistanceKm, // Max allowed search distance (500km)
        int maxAge, // Max valid age (120)
        // Validation bounds (FI-CONS-010: consolidated from User.java and
        // ValidationService)
        int minAge, // Min legal age (18)
        int minHeightCm, // Min valid height (50cm)
        int maxHeightCm, // Max valid height (300cm)
        int minDistanceKm, // Min search distance (1km)
        int maxNameLength, // Max name length (100 chars)
        int minAgeRangeSpan, // Min age range span (5 years)
        // Match quality weights (MED-01: consolidated from MatchQualityConfig)
        double distanceWeight, // Weight for distance score (0.15 default)
        double ageWeight, // Weight for age score (0.10 default)
        double interestWeight, // Weight for interest score (0.25 default)
        double lifestyleWeight, // Weight for lifestyle score (0.25 default)
        double paceWeight, // Weight for pace score (0.15 default)
        double responseWeight, // Weight for response time score (0.10 default)
        // Cleanup configuration
        int cleanupRetentionDays, // Days to retain expired data before cleanup (30 default)
        // Standout scoring weights (separate from match quality for different use case)
        double standoutDistanceWeight, // Weight for distance in standouts (0.20 default)
        double standoutAgeWeight, // Weight for age in standouts (0.15 default)
        double standoutInterestWeight, // Weight for interests in standouts (0.25 default)
        double standoutLifestyleWeight, // Weight for lifestyle in standouts (0.20 default)
        double standoutCompletenessWeight, // Weight for profile completeness (0.10 default)
        double standoutActivityWeight, // Weight for activity recency (0.10 default)
        // Achievement thresholds (centralized from AchievementService)
        double selectiveThreshold, // Like ratio below which behavior is "selective" (0.20)
        double openMindedThreshold, // Like ratio above which behavior is "open-minded" (0.60)
        int bioAchievementLength, // Min bio length for detailed writer achievement (100)
        int lifestyleFieldTarget, // Lifestyle fields needed for guru achievement (5)
        // Pagination & data retention
        int messageMaxPageSize, // Max messages per page query (100)
        int softDeleteRetentionDays // Days before purging soft-deleted rows (90)
        ) {
    public AppConfig {
        Objects.requireNonNull(userTimeZone, "userTimeZone cannot be null");

        requireNonNegative("autoBanThreshold", autoBanThreshold);
        // dailyLikeLimit allows -1 for unlimited
        if (dailyLikeLimit < -1) {
            throw new IllegalArgumentException("dailyLikeLimit must be >= -1 (use -1 for unlimited)");
        }
        requireNonNegative("dailySuperLikeLimit", dailySuperLikeLimit);
        // dailyPassLimit allows -1 for unlimited - validate separately
        if (dailyPassLimit < -1) {
            throw new IllegalArgumentException("dailyPassLimit must be >= -1 (use -1 for unlimited)");
        }
        requireNonNegative("maxInterests", maxInterests);
        requireNonNegative("maxPhotos", maxPhotos);
        requireNonNegative("maxBioLength", maxBioLength);
        requireNonNegative("maxReportDescLength", maxReportDescLength);
        requireNonNegative("sessionTimeoutMinutes", sessionTimeoutMinutes);
        requireNonNegative("maxSwipesPerSession", maxSwipesPerSession);
        requireNonNegative("suspiciousSwipeVelocity", suspiciousSwipeVelocity);
        requireNonNegative("undoWindowSeconds", undoWindowSeconds);
        requireNonNegative("nearbyDistanceKm", nearbyDistanceKm);
        requireNonNegative("closeDistanceKm", closeDistanceKm);
        requireNonNegative("similarAgeDiff", similarAgeDiff);
        requireNonNegative("compatibleAgeDiff", compatibleAgeDiff);
        requireNonNegative("minSharedInterests", minSharedInterests);
        requireNonNegative("paceCompatibilityThreshold", paceCompatibilityThreshold);
        requireNonNegative("responseTimeExcellentHours", responseTimeExcellentHours);
        requireNonNegative("responseTimeGreatHours", responseTimeGreatHours);
        requireNonNegative("responseTimeGoodHours", responseTimeGoodHours);
        requireNonNegative("responseTimeWeekHours", responseTimeWeekHours);
        requireNonNegative("responseTimeMonthHours", responseTimeMonthHours);
        requireNonNegative("achievementMatchTier1", achievementMatchTier1);
        requireNonNegative("achievementMatchTier2", achievementMatchTier2);
        requireNonNegative("achievementMatchTier3", achievementMatchTier3);
        requireNonNegative("achievementMatchTier4", achievementMatchTier4);
        requireNonNegative("achievementMatchTier5", achievementMatchTier5);
        requireNonNegative("minSwipesForBehaviorAchievement", minSwipesForBehaviorAchievement);
        requireNonNegative("maxDistanceKm", maxDistanceKm);
        requireNonNegative("maxAge", maxAge);
        requireNonNegative("minAge", minAge);
        requireNonNegative("minHeightCm", minHeightCm);
        requireNonNegative("maxHeightCm", maxHeightCm);
        requireNonNegative("minDistanceKm", minDistanceKm);
        requireNonNegative("maxNameLength", maxNameLength);
        requireNonNegative("minAgeRangeSpan", minAgeRangeSpan);
        requireNonNegative("distanceWeight", distanceWeight);
        requireNonNegative("ageWeight", ageWeight);
        requireNonNegative("interestWeight", interestWeight);
        requireNonNegative("lifestyleWeight", lifestyleWeight);
        requireNonNegative("paceWeight", paceWeight);
        requireNonNegative("responseWeight", responseWeight);
        requireNonNegative("cleanupRetentionDays", cleanupRetentionDays);
        requireNonNegative("standoutDistanceWeight", standoutDistanceWeight);
        requireNonNegative("standoutAgeWeight", standoutAgeWeight);
        requireNonNegative("standoutInterestWeight", standoutInterestWeight);
        requireNonNegative("standoutLifestyleWeight", standoutLifestyleWeight);
        requireNonNegative("standoutCompletenessWeight", standoutCompletenessWeight);
        requireNonNegative("standoutActivityWeight", standoutActivityWeight);
        requireNonNegative("selectiveThreshold", selectiveThreshold);
        requireNonNegative("openMindedThreshold", openMindedThreshold);
        requireNonNegative("bioAchievementLength", bioAchievementLength);
        requireNonNegative("lifestyleFieldTarget", lifestyleFieldTarget);
        requireNonNegative("messageMaxPageSize", messageMaxPageSize);
        requireNonNegative("softDeleteRetentionDays", softDeleteRetentionDays);

        double weightSum = distanceWeight + ageWeight + interestWeight + lifestyleWeight + paceWeight + responseWeight;
        if (Math.abs(weightSum - 1.0) > 0.01) {
            throw new IllegalArgumentException("Config weights must sum to 1.0, got: " + weightSum);
        }
    }

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

    /** Default configuration values. */
    public static AppConfig defaults() {
        return new AppConfig(
                3, // autoBanThreshold
                100, // dailyLikeLimit
                1, // dailySuperLikeLimit
                -1, // dailyPassLimit (-1 = unlimited)
                ZoneId.systemDefault(), // userTimeZone
                10, // maxInterests
                2, // maxPhotos
                500, // maxBioLength
                500, // maxReportDescLength
                5, // sessionTimeoutMinutes
                500, // maxSwipesPerSession
                30.0, // suspiciousSwipeVelocity
                30, // undoWindowSeconds
                // Algorithm thresholds
                5, // nearbyDistanceKm
                10, // closeDistanceKm
                2, // similarAgeDiff
                5, // compatibleAgeDiff
                3, // minSharedInterests
                50, // paceCompatibilityThreshold
                1, // responseTimeExcellentHours
                24, // responseTimeGreatHours
                72, // responseTimeGoodHours
                168, // responseTimeWeekHours
                720, // responseTimeMonthHours
                1, // achievementMatchTier1
                5, // achievementMatchTier2
                10, // achievementMatchTier3
                25, // achievementMatchTier4
                50, // achievementMatchTier5
                50, // minSwipesForBehaviorAchievement
                500, // maxDistanceKm
                120, // maxAge
                // Validation bounds
                18, // minAge
                50, // minHeightCm
                300, // maxHeightCm
                1, // minDistanceKm
                100, // maxNameLength
                5, // minAgeRangeSpan
                // Match quality weights
                0.15, // distanceWeight
                0.10, // ageWeight
                0.25, // interestWeight
                0.25, // lifestyleWeight
                0.15, // paceWeight
                0.10, // responseWeight
                // Cleanup configuration
                30, // cleanupRetentionDays
                // Standout scoring weights
                0.20, // standoutDistanceWeight
                0.15, // standoutAgeWeight
                0.25, // standoutInterestWeight
                0.20, // standoutLifestyleWeight
                0.10, // standoutCompletenessWeight
                0.10, // standoutActivityWeight
                // Achievement thresholds
                0.20, // selectiveThreshold
                0.60, // openMindedThreshold
                100, // bioAchievementLength
                5, // lifestyleFieldTarget
                // Pagination & data retention
                100, // messageMaxPageSize
                90 // softDeleteRetentionDays
                );
    }

    /** Check if passes are unlimited. */
    public boolean hasUnlimitedPasses() {
        return dailyPassLimit < 0;
    }

    /** Check if likes are unlimited. */
    public boolean hasUnlimitedLikes() {
        return dailyLikeLimit < 0;
    }

    /** Get session timeout as Duration. */
    public Duration getSessionTimeout() {
        return Duration.ofMinutes(sessionTimeoutMinutes);
    }

    /** Builder for creating custom configurations. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for creating AppConfig instances with custom values. */
    public static class Builder {
        private int autoBanThreshold = 3;
        private int dailyLikeLimit = 100;
        private int dailySuperLikeLimit = 1;
        private int dailyPassLimit = -1;
        private ZoneId userTimeZone = ZoneId.systemDefault();
        private int maxInterests = 10;
        private int maxPhotos = 2;
        private int maxBioLength = 500;
        private int maxReportDescLength = 500;
        private int sessionTimeoutMinutes = 5;
        private int maxSwipesPerSession = 500;
        private double suspiciousSwipeVelocity = 30.0;
        private int undoWindowSeconds = 30;
        // Algorithm thresholds
        private int nearbyDistanceKm = 5;
        private int closeDistanceKm = 10;
        private int similarAgeDiff = 2;
        private int compatibleAgeDiff = 5;
        private int minSharedInterests = 3;
        private int paceCompatibilityThreshold = 50;
        private int responseTimeExcellentHours = 1;
        private int responseTimeGreatHours = 24;
        private int responseTimeGoodHours = 72;
        private int responseTimeWeekHours = 168;
        private int responseTimeMonthHours = 720;
        private int achievementMatchTier1 = 1;
        private int achievementMatchTier2 = 5;
        private int achievementMatchTier3 = 10;
        private int achievementMatchTier4 = 25;
        private int achievementMatchTier5 = 50;
        private int minSwipesForBehaviorAchievement = 50;
        private int maxDistanceKm = 500;
        private int maxAge = 120;
        // Validation bounds
        private int minAge = 18;
        private int minHeightCm = 50;
        private int maxHeightCm = 300;
        private int minDistanceKm = 1;
        private int maxNameLength = 100;
        private int minAgeRangeSpan = 5;
        // Match quality weights
        private double distanceWeight = 0.15;
        private double ageWeight = 0.10;
        private double interestWeight = 0.25;
        private double lifestyleWeight = 0.25;
        private double paceWeight = 0.15;
        private double responseWeight = 0.10;
        // Cleanup configuration
        private int cleanupRetentionDays = 30;
        // Standout scoring weights
        private double standoutDistanceWeight = 0.20;
        private double standoutAgeWeight = 0.15;
        private double standoutInterestWeight = 0.25;
        private double standoutLifestyleWeight = 0.20;
        private double standoutCompletenessWeight = 0.10;
        private double standoutActivityWeight = 0.10;
        // Achievement thresholds
        private double selectiveThreshold = 0.20;
        private double openMindedThreshold = 0.60;
        private int bioAchievementLength = 100;
        private int lifestyleFieldTarget = 5;
        // Pagination & data retention
        private int messageMaxPageSize = 100;
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
            return new AppConfig(
                    autoBanThreshold,
                    dailyLikeLimit,
                    dailySuperLikeLimit,
                    dailyPassLimit,
                    userTimeZone,
                    maxInterests,
                    maxPhotos,
                    maxBioLength,
                    maxReportDescLength,
                    sessionTimeoutMinutes,
                    maxSwipesPerSession,
                    suspiciousSwipeVelocity,
                    undoWindowSeconds,
                    nearbyDistanceKm,
                    closeDistanceKm,
                    similarAgeDiff,
                    compatibleAgeDiff,
                    minSharedInterests,
                    paceCompatibilityThreshold,
                    responseTimeExcellentHours,
                    responseTimeGreatHours,
                    responseTimeGoodHours,
                    responseTimeWeekHours,
                    responseTimeMonthHours,
                    achievementMatchTier1,
                    achievementMatchTier2,
                    achievementMatchTier3,
                    achievementMatchTier4,
                    achievementMatchTier5,
                    minSwipesForBehaviorAchievement,
                    maxDistanceKm,
                    maxAge,
                    minAge,
                    minHeightCm,
                    maxHeightCm,
                    minDistanceKm,
                    maxNameLength,
                    minAgeRangeSpan,
                    distanceWeight,
                    ageWeight,
                    interestWeight,
                    lifestyleWeight,
                    paceWeight,
                    responseWeight,
                    cleanupRetentionDays,
                    standoutDistanceWeight,
                    standoutAgeWeight,
                    standoutInterestWeight,
                    standoutLifestyleWeight,
                    standoutCompletenessWeight,
                    standoutActivityWeight,
                    selectiveThreshold,
                    openMindedThreshold,
                    bioAchievementLength,
                    lifestyleFieldTarget,
                    messageMaxPageSize,
                    softDeleteRetentionDays);
        }
    }
}
