package datingapp.core;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Centralized, immutable application configuration. All configurable values should be defined here
 * for easy modification.
 *
 * <p>This enables future drop-in changes: - Different profiles (dev/prod) - External configuration
 * (properties files, env vars) - Testing with different thresholds
 */
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
        int achievementMatchTier1, // First match milestone (1)
        int achievementMatchTier2, // Second match milestone (5)
        int achievementMatchTier3, // Third match milestone (10)
        int achievementMatchTier4, // Fourth match milestone (25)
        int achievementMatchTier5, // Fifth match milestone (50)
        int minSwipesForBehaviorAchievement, // Min swipes to evaluate behavior (50)
        int maxDistanceKm, // Max allowed search distance (500km)
        int maxAge, // Max valid age (120)
        // Match quality weights (MED-01: consolidated from MatchQualityConfig)
        double distanceWeight, // Weight for distance score (0.15 default)
        double ageWeight, // Weight for age score (0.10 default)
        double interestWeight, // Weight for interest score (0.25 default)
        double lifestyleWeight, // Weight for lifestyle score (0.25 default)
        double paceWeight, // Weight for pace score (0.15 default)
        double responseWeight // Weight for response time score (0.10 default)
        ) {
    /** Default configuration values. */
    public static AppConfig defaults() {
        return new AppConfig(
                3, // autoBanThreshold
                100, // dailyLikeLimit
                1, // dailySuperLikeLimit
                -1, // dailyPassLimit (-1 = unlimited)
                ZoneId.systemDefault(), // userTimeZone
                5, // maxInterests
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
                1, // achievementMatchTier1
                5, // achievementMatchTier2
                10, // achievementMatchTier3
                25, // achievementMatchTier4
                50, // achievementMatchTier5
                50, // minSwipesForBehaviorAchievement
                500, // maxDistanceKm
                120, // maxAge
                // Match quality weights
                0.15, // distanceWeight
                0.10, // ageWeight
                0.25, // interestWeight
                0.25, // lifestyleWeight
                0.15, // paceWeight
                0.10 // responseWeight
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
        private int maxInterests = 5;
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
        private int achievementMatchTier1 = 1;
        private int achievementMatchTier2 = 5;
        private int achievementMatchTier3 = 10;
        private int achievementMatchTier4 = 25;
        private int achievementMatchTier5 = 50;
        private int minSwipesForBehaviorAchievement = 50;
        private int maxDistanceKm = 500;
        private int maxAge = 120;
        // Match quality weights
        private double distanceWeight = 0.15;
        private double ageWeight = 0.10;
        private double interestWeight = 0.25;
        private double lifestyleWeight = 0.25;
        private double paceWeight = 0.15;
        private double responseWeight = 0.10;

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
                    achievementMatchTier1,
                    achievementMatchTier2,
                    achievementMatchTier3,
                    achievementMatchTier4,
                    achievementMatchTier5,
                    minSwipesForBehaviorAchievement,
                    maxDistanceKm,
                    maxAge,
                    distanceWeight,
                    ageWeight,
                    interestWeight,
                    lifestyleWeight,
                    paceWeight,
                    responseWeight);
        }
    }
}
