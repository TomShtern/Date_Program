package datingapp.app.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.storage.DatabaseManager;
import datingapp.storage.StorageFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consolidated application startup and configuration loader entry point. */
public final class ApplicationStartup {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartup.class);

    private static final String CONFIG_FILE = "./config/app-config.json";
    private static final String ENV_PREFIX = "DATING_APP_";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static volatile ServiceRegistry services;
    private static volatile DatabaseManager dbManager;
    private static volatile boolean initialized = false;

    private ApplicationStartup() {}

    public static synchronized ServiceRegistry initialize() {
        return initialize(load());
    }

    public static synchronized ServiceRegistry initialize(AppConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        if (!initialized) {
            dbManager = DatabaseManager.getInstance();
            services = StorageFactory.buildH2(dbManager, config);
            initialized = true;
        }
        return services;
    }

    public static ServiceRegistry getServices() {
        ServiceRegistry current = services;
        if (!initialized || current == null) {
            throw new IllegalStateException("ApplicationStartup.initialize() must be called first");
        }
        return current;
    }

    public static synchronized void shutdown() {
        if (dbManager != null) {
            dbManager.shutdown();
        }
        initialized = false;
        services = null;
        dbManager = null;
    }

    public static synchronized void reset() {
        shutdown();
        AppSession.getInstance().reset();
    }

    public static AppConfig load() {
        return load(Path.of(CONFIG_FILE));
    }

    public static AppConfig load(Path configPath) {
        AppConfig.Builder builder = AppConfig.builder();

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                applyJsonConfig(builder, json);
                logInfo("Loaded configuration from: {}", configPath);
            } catch (IOException ex) {
                // File exists but failed to read/parse — this is a configuration error, not
                // ignorable
                throw new IllegalStateException("Config file exists but failed to load: " + configPath, ex);
            }
        } else {
            logInfo("Config file not found at {}, using defaults", configPath);
        }

        applyEnvironmentOverrides(builder);
        return builder.build();
    }

    public static AppConfig fromJson(String json) {
        AppConfig.Builder builder = AppConfig.builder();
        applyJsonConfig(builder, json);
        applyEnvironmentOverrides(builder);
        return builder.build();
    }

    private static void applyJsonConfig(AppConfig.Builder builder, String json) {
        try {
            JsonNode root = MAPPER.readTree(json);

            applyInt(root, "autoBanThreshold", builder::autoBanThreshold);
            applyInt(root, "dailyLikeLimit", builder::dailyLikeLimit);
            applyInt(root, "dailySuperLikeLimit", builder::dailySuperLikeLimit);
            applyInt(root, "dailyPassLimit", builder::dailyPassLimit);
            applyInt(root, "maxInterests", builder::maxInterests);
            applyInt(root, "maxPhotos", builder::maxPhotos);
            applyInt(root, "maxBioLength", builder::maxBioLength);
            applyInt(root, "maxReportDescLength", builder::maxReportDescLength);

            applyInt(root, "sessionTimeoutMinutes", builder::sessionTimeoutMinutes);
            applyInt(root, "maxSwipesPerSession", builder::maxSwipesPerSession);
            applyDouble(root, "suspiciousSwipeVelocity", builder::suspiciousSwipeVelocity);
            applyInt(root, "undoWindowSeconds", builder::undoWindowSeconds);

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

            applyInt(root, "achievementMatchTier1", builder::achievementMatchTier1);
            applyInt(root, "achievementMatchTier2", builder::achievementMatchTier2);
            applyInt(root, "achievementMatchTier3", builder::achievementMatchTier3);
            applyInt(root, "achievementMatchTier4", builder::achievementMatchTier4);
            applyInt(root, "achievementMatchTier5", builder::achievementMatchTier5);
            applyInt(root, "minSwipesForBehaviorAchievement", builder::minSwipesForBehaviorAchievement);

            applyInt(root, "maxDistanceKm", builder::maxDistanceKm);
            applyInt(root, "maxAge", builder::maxAge);
            applyInt(root, "minAge", builder::minAge);

            applyInt(root, "minHeightCm", builder::minHeightCm);
            applyInt(root, "maxHeightCm", builder::maxHeightCm);
            applyInt(root, "minDistanceKm", builder::minDistanceKm);
            applyInt(root, "maxNameLength", builder::maxNameLength);
            applyInt(root, "minAgeRangeSpan", builder::minAgeRangeSpan);

            applyDouble(root, "distanceWeight", builder::distanceWeight);
            applyDouble(root, "ageWeight", builder::ageWeight);
            applyDouble(root, "interestWeight", builder::interestWeight);
            applyDouble(root, "lifestyleWeight", builder::lifestyleWeight);
            applyDouble(root, "paceWeight", builder::paceWeight);
            applyDouble(root, "responseWeight", builder::responseWeight);

            applyInt(root, "cleanupRetentionDays", builder::cleanupRetentionDays);

            if (root.has("userTimeZone")) {
                builder.userTimeZone(ZoneId.of(root.get("userTimeZone").asText()));
            }
        } catch (IOException ex) {
            logWarn("Failed to parse JSON config", ex);
        }
    }

    private static void applyEnvironmentOverrides(AppConfig.Builder builder) {
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

        String tz = System.getenv(ENV_PREFIX + "USER_TIME_ZONE");
        if (tz != null && !tz.isBlank()) {
            try {
                builder.userTimeZone(ZoneId.of(tz));
            } catch (Exception ex) {
                logWarn("Invalid timezone in env var {}{}: {}", ENV_PREFIX, "USER_TIME_ZONE", tz);
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
