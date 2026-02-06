package datingapp.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads AppConfig from external JSON file with environment variable overrides.
 *
 * <p>Configuration precedence (highest to lowest):
 * <ol>
 *   <li>Environment variables (DATING_APP_* prefix)</li>
 *   <li>JSON config file (./config/app-config.json)</li>
 *   <li>Built-in defaults (AppConfig.defaults())</li>
 * </ol>
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "dailyLikeLimit": 50,
 *   "autoBanThreshold": 5,
 *   "maxDistanceKm": 100
 * }
 * }</pre>
 *
 * <p>Example environment override:
 * <pre>{@code
 * DATING_APP_DAILY_LIKE_LIMIT=50
 * }</pre>
 */
public final class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String CONFIG_FILE = "./config/app-config.json";
    private static final String ENV_PREFIX = "DATING_APP_";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigLoader() {} // Utility class

    /**
     * Loads configuration from file with environment overrides.
     *
     * @return Loaded AppConfig or defaults if file not found
     */
    public static AppConfig load() {
        return load(Path.of(CONFIG_FILE));
    }

    /**
     * Loads configuration from specified path with environment overrides.
     *
     * @param configPath Path to JSON config file
     * @return Loaded AppConfig or defaults if file not found
     */
    public static AppConfig load(Path configPath) {
        AppConfig.Builder builder = AppConfig.builder();

        // Try loading from JSON file
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                applyJsonConfig(builder, json);
                logInfo("Loaded configuration from: {}", configPath);
            } catch (IOException ex) {
                logWarn("Failed to load config file {}: {}", configPath, ex.getMessage());
            }
        } else {
            logInfo("Config file not found at {}, using defaults", configPath);
        }

        // Apply environment variable overrides
        applyEnvironmentOverrides(builder);

        return builder.build();
    }

    /**
     * Loads configuration from JSON string.
     *
     * @param json JSON configuration string
     * @return Loaded AppConfig
     */
    public static AppConfig fromJson(String json) {
        AppConfig.Builder builder = AppConfig.builder();
        applyJsonConfig(builder, json);
        applyEnvironmentOverrides(builder);
        return builder.build();
    }

    private static void applyJsonConfig(AppConfig.Builder builder, String json) {
        try {
            JsonNode root = MAPPER.readTree(json);

            // Limits
            applyInt(root, "autoBanThreshold", builder::autoBanThreshold);
            applyInt(root, "dailyLikeLimit", builder::dailyLikeLimit);
            applyInt(root, "dailySuperLikeLimit", builder::dailySuperLikeLimit);
            applyInt(root, "dailyPassLimit", builder::dailyPassLimit);
            applyInt(root, "maxInterests", builder::maxInterests);
            applyInt(root, "maxPhotos", builder::maxPhotos);
            applyInt(root, "maxBioLength", builder::maxBioLength);
            applyInt(root, "maxReportDescLength", builder::maxReportDescLength);

            // Session
            applyInt(root, "sessionTimeoutMinutes", builder::sessionTimeoutMinutes);
            applyInt(root, "maxSwipesPerSession", builder::maxSwipesPerSession);
            applyDouble(root, "suspiciousSwipeVelocity", builder::suspiciousSwipeVelocity);

            // Undo
            applyInt(root, "undoWindowSeconds", builder::undoWindowSeconds);

            // Algorithm thresholds
            applyInt(root, "nearbyDistanceKm", builder::nearbyDistanceKm);
            applyInt(root, "closeDistanceKm", builder::closeDistanceKm);
            applyInt(root, "similarAgeDiff", builder::similarAgeDiff);
            applyInt(root, "compatibleAgeDiff", builder::compatibleAgeDiff);
            applyInt(root, "minSharedInterests", builder::minSharedInterests);
            applyInt(root, "paceCompatibilityThreshold", builder::paceCompatibilityThreshold);
            applyInt(root, "responseTimeExcellentHours", builder::responseTimeExcellentHours);
            applyInt(root, "responseTimeGreatHours", builder::responseTimeGreatHours);
            applyInt(root, "responseTimeGoodHours", builder::responseTimeGoodHours);
            applyInt(root, "responseTimeWeekHours", builder::responseTimeWeekHours);
            applyInt(root, "responseTimeMonthHours", builder::responseTimeMonthHours);

            // Achievement tiers
            applyInt(root, "achievementMatchTier1", builder::achievementMatchTier1);
            applyInt(root, "achievementMatchTier2", builder::achievementMatchTier2);
            applyInt(root, "achievementMatchTier3", builder::achievementMatchTier3);
            applyInt(root, "achievementMatchTier4", builder::achievementMatchTier4);
            applyInt(root, "achievementMatchTier5", builder::achievementMatchTier5);
            applyInt(root, "minSwipesForBehaviorAchievement", builder::minSwipesForBehaviorAchievement);

            // Distance and age
            applyInt(root, "maxDistanceKm", builder::maxDistanceKm);
            applyInt(root, "maxAge", builder::maxAge);
            applyInt(root, "minAge", builder::minAge);

            // Validation
            applyInt(root, "minHeightCm", builder::minHeightCm);
            applyInt(root, "maxHeightCm", builder::maxHeightCm);
            applyInt(root, "minDistanceKm", builder::minDistanceKm);
            applyInt(root, "maxNameLength", builder::maxNameLength);
            applyInt(root, "minAgeRangeSpan", builder::minAgeRangeSpan);

            // Weights
            applyDouble(root, "distanceWeight", builder::distanceWeight);
            applyDouble(root, "ageWeight", builder::ageWeight);
            applyDouble(root, "interestWeight", builder::interestWeight);
            applyDouble(root, "lifestyleWeight", builder::lifestyleWeight);
            applyDouble(root, "paceWeight", builder::paceWeight);
            applyDouble(root, "responseWeight", builder::responseWeight);

            // Cleanup
            applyInt(root, "cleanupRetentionDays", builder::cleanupRetentionDays);

            // Timezone
            if (root.has("userTimeZone")) {
                String tz = root.get("userTimeZone").asText();
                builder.userTimeZone(ZoneId.of(tz));
            }
        } catch (IOException ex) {
            logWarn("Failed to parse JSON config: {}", ex.getMessage());
        }
    }

    private static void applyEnvironmentOverrides(AppConfig.Builder builder) {
        // Convert camelCase to UPPER_SNAKE_CASE for env vars
        applyEnvInt("DAILY_LIKE_LIMIT", builder::dailyLikeLimit);
        applyEnvInt("DAILY_SUPER_LIKE_LIMIT", builder::dailySuperLikeLimit);
        applyEnvInt("DAILY_PASS_LIMIT", builder::dailyPassLimit);
        applyEnvInt("AUTO_BAN_THRESHOLD", builder::autoBanThreshold);
        applyEnvInt("MAX_DISTANCE_KM", builder::maxDistanceKm);
        applyEnvInt("MAX_SWIPES_PER_SESSION", builder::maxSwipesPerSession);
        applyEnvInt("UNDO_WINDOW_SECONDS", builder::undoWindowSeconds);
        applyEnvInt("SESSION_TIMEOUT_MINUTES", builder::sessionTimeoutMinutes);
        applyEnvInt("CLEANUP_RETENTION_DAYS", builder::cleanupRetentionDays);
        applyEnvInt("MIN_AGE", builder::minAge);
        applyEnvInt("MAX_AGE", builder::maxAge);

        // Timezone
        String tz = System.getenv(ENV_PREFIX + "USER_TIME_ZONE");
        if (tz != null && !tz.isBlank()) {
            try {
                builder.userTimeZone(ZoneId.of(tz));
            } catch (Exception ex) {
                logWarn("Invalid timezone in env var: {}", tz);
            }
        }
    }

    private static void applyInt(JsonNode root, String key, java.util.function.IntConsumer setter) {
        if (root.has(key)) {
            setter.accept(root.get(key).asInt());
        }
    }

    private static void applyDouble(JsonNode root, String key, java.util.function.DoubleConsumer setter) {
        if (root.has(key)) {
            setter.accept(root.get(key).asDouble());
        }
    }

    private static void applyEnvInt(String suffix, java.util.function.IntConsumer setter) {
        String value = System.getenv(ENV_PREFIX + suffix);
        if (value != null && !value.isBlank()) {
            try {
                setter.accept(Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                logWarn("Invalid integer in env var {}: {}", ENV_PREFIX + suffix, value);
            }
        }
    }

    private static void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private static void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }
}
