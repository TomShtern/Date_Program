package datingapp.core;

import java.time.ZoneId;
import java.util.Objects;

/** Shared validation rules for {@link AppConfig} sections. */
final class AppConfigValidator {

    private AppConfigValidator() {}

    static void validateMatching(
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

        double weightSum = distanceWeight + ageWeight + interestWeight + lifestyleWeight + paceWeight + responseWeight;
        if (Math.abs(weightSum - 1.0) > 0.01) {
            throw new IllegalArgumentException("Match-quality weights must sum to 1.0, got: " + weightSum);
        }
    }

    static void validateValidation(
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
        requireNonNegative("minAge", minAge);
        requireNonNegative("maxAge", maxAge);
        if (minAge > maxAge) {
            throw new IllegalArgumentException("minAge must be <= maxAge");
        }
        requireNonNegative("minHeightCm", minHeightCm);
        requireNonNegative("maxHeightCm", maxHeightCm);
        if (minHeightCm > maxHeightCm) {
            throw new IllegalArgumentException("minHeightCm must be <= maxHeightCm");
        }
        requireNonNegative("maxBioLength", maxBioLength);
        requireNonNegative("maxReportDescLength", maxReportDescLength);
        requireNonNegative("maxNameLength", maxNameLength);
        requireNonNegative("minAgeRangeSpan", minAgeRangeSpan);
        requireNonNegative("minDistanceKm", minDistanceKm);
        requireNonNegative("maxInterests", maxInterests);
        requireNonNegative("maxPhotos", maxPhotos);
        requireNonNegative("messageMaxPageSize", messageMaxPageSize);
    }

    static void validateAlgorithm(
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

    static void validateSafety(
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
}
