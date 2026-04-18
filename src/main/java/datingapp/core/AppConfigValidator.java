package datingapp.core;

import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.storage.DatabaseDialect;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/** Shared validation rules for {@link AppConfig} sections. */
final class AppConfigValidator {

    private AppConfigValidator() {}

    static void validateMatchingLimits(
            int dailyLikeLimit,
            int dailySuperLikeLimit,
            int dailyPassLimit,
            int maxSwipesPerSession,
            int rematchCooldownHours) {
        if (dailyLikeLimit < -1) {
            throw new IllegalArgumentException("dailyLikeLimit must be >= -1");
        }
        if (dailyPassLimit < -1) {
            throw new IllegalArgumentException("dailyPassLimit must be >= -1");
        }
        requireNonNegative("dailySuperLikeLimit", dailySuperLikeLimit);
        requireNonNegative("maxSwipesPerSession", maxSwipesPerSession);
        requireNonNegative("rematchCooldownHours", rematchCooldownHours);
    }

    static void validateMatchingWeights(
            double suspiciousSwipeVelocity,
            double distanceWeight,
            double ageWeight,
            double interestWeight,
            double lifestyleWeight,
            double paceWeight,
            double responseWeight) {
        requireNonNegative("suspiciousSwipeVelocity", suspiciousSwipeVelocity);
        requireNonNegative("distanceWeight", distanceWeight);
        requireNonNegative("ageWeight", ageWeight);
        requireNonNegative("interestWeight", interestWeight);
        requireNonNegative("lifestyleWeight", lifestyleWeight);
        requireNonNegative("paceWeight", paceWeight);
        requireNonNegative("responseWeight", responseWeight);

        double weightSum = distanceWeight + ageWeight + interestWeight + lifestyleWeight + paceWeight + responseWeight;
        if (Math.abs(weightSum - 1.0) > 0.01) {
            throw new IllegalArgumentException(
                    "Match-quality weights must sum to 1.0 within +/- 0.01 tolerance, got: " + weightSum);
        }
    }

    static void validateMatchingInterestPolicy(int minSharedInterests, int sharedInterestsPreviewCount) {
        requireNonNegative("minSharedInterests", minSharedInterests);
        requireInRange(sharedInterestsPreviewCount, 1, Interest.count(), "sharedInterestsPreviewCount");
    }

    static void validateMatchingBehaviorFlags() {
        // Boolean flag is intrinsically valid; this hook keeps matching-related validation centralized.
    }

    static void validateValidationAgeAndHeight(int minAge, int maxAge, int minHeightCm, int maxHeightCm) {
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
    }

    static void validateValidationTextLimits(
            int maxBioLength,
            int maxReportDescLength,
            int maxNameLength,
            int maxMessageLength,
            int maxProfileNoteLength) {
        requireNonNegative("maxBioLength", maxBioLength);
        requireNonNegative("maxReportDescLength", maxReportDescLength);
        requireNonNegative("maxNameLength", maxNameLength);
        requireInRange(maxMessageLength, 1, Message.MAX_LENGTH, "maxMessageLength");
        requireInRange(maxProfileNoteLength, 1, ProfileNote.MAX_LENGTH, "maxProfileNoteLength");
    }

    static void validateValidationDiscoveryRanges(int minAgeRangeSpan, int minDistanceKm, int maxDistanceKm) {
        requireNonNegative("minAgeRangeSpan", minAgeRangeSpan);
        requireNonNegative("minDistanceKm", minDistanceKm);
        requireNonNegative("maxDistanceKm", maxDistanceKm);
        if (minDistanceKm > maxDistanceKm) {
            throw new IllegalArgumentException(
                    "minDistanceKm (" + minDistanceKm + ") must not exceed maxDistanceKm (" + maxDistanceKm + ")");
        }
    }

    static void validateValidationMediaAndPaging(
            int maxInterests,
            int maxPhotos,
            int messageMaxPageSize,
            int chatBackgroundPollSeconds,
            int chatActivePollSeconds,
            int maxStandouts) {
        requireNonNegative("maxInterests", maxInterests);
        requireNonNegative("maxPhotos", maxPhotos);
        if (maxPhotos != User.MAX_PHOTOS) {
            throw new IllegalArgumentException("maxPhotos must equal User.MAX_PHOTOS (" + User.MAX_PHOTOS + ")");
        }
        requireNonNegative("messageMaxPageSize", messageMaxPageSize);
        requireInRange(chatBackgroundPollSeconds, 1, 300, "chatBackgroundPollSeconds");
        requireInRange(chatActivePollSeconds, 1, 300, "chatActivePollSeconds");
        requireInRange(maxStandouts, 1, 100, "maxStandouts");
    }

    static void validateAlgorithmDistance(
            int nearbyDistanceKm,
            int closeDistanceKm,
            int similarAgeDiff,
            int compatibleAgeDiff,
            int paceCompatibilityThreshold) {
        requireNonNegative("nearbyDistanceKm", nearbyDistanceKm);
        requireNonNegative("closeDistanceKm", closeDistanceKm);
        if (nearbyDistanceKm > closeDistanceKm) {
            throw new IllegalArgumentException("nearbyDistanceKm (" + nearbyDistanceKm
                    + ") must not exceed closeDistanceKm (" + closeDistanceKm + ")");
        }
        requireNonNegative("similarAgeDiff", similarAgeDiff);
        requireNonNegative("compatibleAgeDiff", compatibleAgeDiff);
        requireNonNegative("paceCompatibilityThreshold", paceCompatibilityThreshold);
    }

    static void validateAlgorithmResponseTimes(
            int responseTimeExcellentHours,
            int responseTimeGreatHours,
            int responseTimeGoodHours,
            int responseTimeWeekHours,
            int responseTimeMonthHours) {
        requireNonNegative("responseTimeExcellentHours", responseTimeExcellentHours);
        requireNonNegative("responseTimeGreatHours", responseTimeGreatHours);
        requireNonNegative("responseTimeGoodHours", responseTimeGoodHours);
        requireNonNegative("responseTimeWeekHours", responseTimeWeekHours);
        requireNonNegative("responseTimeMonthHours", responseTimeMonthHours);
        if (responseTimeExcellentHours > responseTimeGreatHours) {
            throw new IllegalArgumentException("responseTimeExcellentHours must be <= responseTimeGreatHours");
        }
        if (responseTimeGreatHours > responseTimeGoodHours) {
            throw new IllegalArgumentException("responseTimeGreatHours must be <= responseTimeGoodHours");
        }
        if (responseTimeGoodHours > responseTimeWeekHours) {
            throw new IllegalArgumentException("responseTimeGoodHours must be <= responseTimeWeekHours");
        }
        if (responseTimeWeekHours > responseTimeMonthHours) {
            throw new IllegalArgumentException("responseTimeWeekHours must be <= responseTimeMonthHours");
        }
    }

    static void validateAlgorithmStandoutPolicy(
            int standoutDiversityDays,
            int standoutMinScore,
            int starExcellentThreshold,
            int starGreatThreshold,
            int starGoodThreshold,
            int starFairThreshold) {
        requireInRange(standoutDiversityDays, 1, 30, "standoutDiversityDays");
        requireInRange(standoutMinScore, 0, 100, "standoutMinScore");
        requireInRange(starExcellentThreshold, 0, 100, "starExcellentThreshold");
        requireInRange(starGreatThreshold, 0, 100, "starGreatThreshold");
        requireInRange(starGoodThreshold, 0, 100, "starGoodThreshold");
        requireInRange(starFairThreshold, 0, 100, "starFairThreshold");
        if (starExcellentThreshold < starGreatThreshold
                || starGreatThreshold < starGoodThreshold
                || starGoodThreshold < starFairThreshold) {
            throw new IllegalArgumentException(
                    "Star thresholds must be descending: excellent >= great >= good >= fair");
        }
    }

    static void validateAlgorithmWeights(
            double standoutDistanceWeight,
            double standoutAgeWeight,
            double standoutInterestWeight,
            double standoutLifestyleWeight,
            double standoutCompletenessWeight,
            double standoutActivityWeight) {
        requireNonNegative("standoutDistanceWeight", standoutDistanceWeight);
        requireNonNegative("standoutAgeWeight", standoutAgeWeight);
        requireNonNegative("standoutInterestWeight", standoutInterestWeight);
        requireNonNegative("standoutLifestyleWeight", standoutLifestyleWeight);
        requireNonNegative("standoutCompletenessWeight", standoutCompletenessWeight);
        requireNonNegative("standoutActivityWeight", standoutActivityWeight);
    }

    static void validateStorage(
            String databaseDialect, String databaseUrl, String databaseUsername, int queryTimeoutSeconds) {
        requireSupportedDatabaseDialect(databaseDialect);
        requireJdbcUrl(databaseUrl);
        DatabaseDialect configuredDialect = DatabaseDialect.fromConfig(databaseDialect, databaseUrl);
        DatabaseDialect jdbcDialect = DatabaseDialect.fromJdbcUrl(databaseUrl);
        if (configuredDialect != jdbcDialect) {
            throw new IllegalArgumentException(
                    "databaseDialect " + configuredDialect + " must match JDBC URL dialect " + jdbcDialect);
        }
        requireNonBlank("databaseUsername", databaseUsername);
        requireInRange(queryTimeoutSeconds, 1, 600, "queryTimeoutSeconds");
    }

    static void validatePoolSizing(int maxPoolSize, int minIdle) {
        requireInRange(maxPoolSize, 1, 200, "maxPoolSize");
        requireInRange(minIdle, 0, 200, "minIdle");
        if (minIdle > maxPoolSize) {
            throw new IllegalArgumentException("minIdle must be <= maxPoolSize");
        }
    }

    static void validatePoolTimeouts(
            int connectionTimeoutSeconds,
            int validationTimeoutSeconds,
            int idleTimeoutSeconds,
            int maxLifetimeSeconds,
            int keepaliveTimeSeconds) {
        requireInRange(connectionTimeoutSeconds, 1, 300, "connectionTimeoutSeconds");
        requireInRange(validationTimeoutSeconds, 1, 300, "validationTimeoutSeconds");
        requireInRange(idleTimeoutSeconds, 0, 86400, "idleTimeoutSeconds");
        requireInRange(maxLifetimeSeconds, 0, 86400, "maxLifetimeSeconds");
        requireInRange(keepaliveTimeSeconds, 0, 86400, "keepaliveTimeSeconds");

        if (keepaliveTimeSeconds > 0 && maxLifetimeSeconds > 0 && keepaliveTimeSeconds >= maxLifetimeSeconds) {
            throw new IllegalArgumentException(
                    "keepaliveTimeSeconds must be less than maxLifetimeSeconds when both are enabled");
        }
        if (idleTimeoutSeconds > 0 && maxLifetimeSeconds > 0 && idleTimeoutSeconds > maxLifetimeSeconds) {
            throw new IllegalArgumentException(
                    "idleTimeoutSeconds must not exceed maxLifetimeSeconds when both are enabled");
        }
    }

    static void validateSafetySession(
            int autoBanThreshold, ZoneId userTimeZone, int sessionTimeoutMinutes, int undoWindowSeconds) {
        Objects.requireNonNull(userTimeZone, "userTimeZone cannot be null");
        requireNonNegative("autoBanThreshold", autoBanThreshold);
        requireNonNegative("sessionTimeoutMinutes", sessionTimeoutMinutes);
        requireNonNegative("undoWindowSeconds", undoWindowSeconds);
    }

    static void validateSafetyAchievementThresholds(
            int achievementMatchTier1,
            int achievementMatchTier2,
            int achievementMatchTier3,
            int achievementMatchTier4,
            int achievementMatchTier5,
            int minSwipesForBehaviorAchievement) {
        requireNonNegative("achievementMatchTier1", achievementMatchTier1);
        requireNonNegative("achievementMatchTier2", achievementMatchTier2);
        requireNonNegative("achievementMatchTier3", achievementMatchTier3);
        requireNonNegative("achievementMatchTier4", achievementMatchTier4);
        requireNonNegative("achievementMatchTier5", achievementMatchTier5);
        requireNonNegative("minSwipesForBehaviorAchievement", minSwipesForBehaviorAchievement);
        if (achievementMatchTier1 > achievementMatchTier2) {
            throw new IllegalArgumentException("achievementMatchTier1 must be <= achievementMatchTier2");
        }
        if (achievementMatchTier2 > achievementMatchTier3) {
            throw new IllegalArgumentException("achievementMatchTier2 must be <= achievementMatchTier3");
        }
        if (achievementMatchTier3 > achievementMatchTier4) {
            throw new IllegalArgumentException("achievementMatchTier3 must be <= achievementMatchTier4");
        }
        if (achievementMatchTier4 > achievementMatchTier5) {
            throw new IllegalArgumentException("achievementMatchTier4 must be <= achievementMatchTier5");
        }
    }

    static void validateSafetyBehaviorThresholds(
            double selectiveThreshold,
            double openMindedThreshold,
            int bioAchievementLength,
            int lifestyleFieldTarget,
            int cleanupRetentionDays,
            int softDeleteRetentionDays) {
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

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireSupportedDatabaseDialect(String databaseDialect) {
        requireNonBlank("databaseDialect", databaseDialect);
        String normalizedDialect = databaseDialect.trim().toUpperCase(Locale.ROOT);
        if (!"H2".equals(normalizedDialect) && !"POSTGRESQL".equals(normalizedDialect)) {
            throw new IllegalArgumentException(
                    "databaseDialect must be one of [H2, POSTGRESQL], got: " + databaseDialect);
        }
    }

    private static void requireJdbcUrl(String databaseUrl) {
        requireNonBlank("databaseUrl", databaseUrl);
        if (!databaseUrl.trim().startsWith("jdbc:")) {
            throw new IllegalArgumentException("databaseUrl must start with jdbc:");
        }
    }

    private static void requireInRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
    }
}
