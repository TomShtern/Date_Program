package datingapp.core;

import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/**
 * Application configuration grouped by concern into six sub-records.
 *
 * <p>Use {@link #defaults()} for the default configuration, or {@link #builder()} for a custom one.
 */
public record AppConfig(
        MatchingConfig matching,
        ValidationConfig validation,
        AlgorithmConfig algorithm,
        StorageConfig storage,
        SafetyConfig safety,
        MediaConfig media,
        AuthConfig auth) {

    public static final int DEFAULT_REMATCH_COOLDOWN_HOURS = 168;
    public static final String DEVELOPMENT_ONLY_JWT_SECRET_PLACEHOLDER = "development-only-jwt-secret-change-me-please";

    // ========================================================================
    // Sub-record: MatchingConfig
    // ========================================================================

    public static record MatchingConfig(
            int dailyLikeLimit,
            int dailySuperLikeLimit,
            int dailyPassLimit,
            int maxSwipesPerSession,
            int rematchCooldownHours,
            double suspiciousSwipeVelocity,
            boolean suspiciousSwipeVelocityBlockingEnabled,
            double distanceWeight,
            double ageWeight,
            double interestWeight,
            double lifestyleWeight,
            double paceWeight,
            double responseWeight,
            int minSharedInterests,
            int sharedInterestsPreviewCount,
            int maxDistanceKm) {
        public MatchingConfig {
            AppConfigValidator.validateMatchingLimits(
                    dailyLikeLimit, dailySuperLikeLimit, dailyPassLimit, maxSwipesPerSession, rematchCooldownHours);
            AppConfigValidator.validateMatchingWeights(
                    suspiciousSwipeVelocity,
                    distanceWeight,
                    ageWeight,
                    interestWeight,
                    lifestyleWeight,
                    paceWeight,
                    responseWeight);
            AppConfigValidator.validateMatchingInterestPolicy(minSharedInterests, sharedInterestsPreviewCount);
            AppConfigValidator.validateMatchingBehaviorFlags();
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
            AppConfigValidator.validateValidationAgeAndHeight(minAge, maxAge, minHeightCm, maxHeightCm);
            AppConfigValidator.validateValidationTextLimits(
                    maxBioLength, maxReportDescLength, maxNameLength, maxMessageLength, maxProfileNoteLength);
            AppConfigValidator.validateValidationDiscoveryRanges(minAgeRangeSpan, minDistanceKm, maxDistanceKm);
            AppConfigValidator.validateValidationMediaAndPaging(
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
            AppConfigValidator.validateAlgorithmDistance(
                    nearbyDistanceKm, closeDistanceKm, similarAgeDiff, compatibleAgeDiff, paceCompatibilityThreshold);
            AppConfigValidator.validateAlgorithmResponseTimes(
                    responseTimeExcellentHours,
                    responseTimeGreatHours,
                    responseTimeGoodHours,
                    responseTimeWeekHours,
                    responseTimeMonthHours);
            AppConfigValidator.validateAlgorithmStandoutPolicy(
                    standoutDiversityDays,
                    standoutMinScore,
                    starExcellentThreshold,
                    starGreatThreshold,
                    starGoodThreshold,
                    starFairThreshold);
            AppConfigValidator.validateAlgorithmWeights(
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

    public static record StorageConfig(
            String databaseDialect,
            String databaseUrl,
            String databaseUsername,
            int queryTimeoutSeconds,
            int maxPoolSize,
            int minIdle,
            int connectionTimeoutSeconds,
            int validationTimeoutSeconds,
            int idleTimeoutSeconds,
            int maxLifetimeSeconds,
            int keepaliveTimeSeconds) {
        public StorageConfig {
            AppConfigValidator.validateStorage(databaseDialect, databaseUrl, databaseUsername, queryTimeoutSeconds);
            AppConfigValidator.validatePoolSizing(maxPoolSize, minIdle);
            AppConfigValidator.validatePoolTimeouts(
                    connectionTimeoutSeconds,
                    validationTimeoutSeconds,
                    idleTimeoutSeconds,
                    maxLifetimeSeconds,
                    keepaliveTimeSeconds);
            databaseDialect = databaseDialect.trim().toUpperCase(Locale.ROOT);
            databaseUrl = databaseUrl.trim();
            databaseUsername = databaseUsername.trim();
        }
    }

    // ========================================================================
    // Sub-record: MediaConfig
    // ========================================================================

    public static record MediaConfig(String photoStorageRoot, String photoPublicBaseUrl, long maxPhotoUploadBytes) {
        public MediaConfig {
            AppConfigValidator.validateMedia(photoStorageRoot, photoPublicBaseUrl, maxPhotoUploadBytes);
            photoStorageRoot = Path.of(photoStorageRoot.trim()).normalize().toString();
            photoPublicBaseUrl = photoPublicBaseUrl == null ? "" : photoPublicBaseUrl.trim();
        }
    }

    // ========================================================================
    // Sub-record: AuthConfig
    // ========================================================================

    public static record AuthConfig(
            String tokenIssuer,
            String jwtSecret,
            int accessTokenTtlSeconds,
            int refreshTokenTtlDays,
            int minPasswordLength) {
        public AuthConfig {
            AppConfigValidator.validateAuth(
                    tokenIssuer, jwtSecret, accessTokenTtlSeconds, refreshTokenTtlDays, minPasswordLength);
            tokenIssuer = tokenIssuer.trim();
            jwtSecret = jwtSecret.trim();
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
            AppConfigValidator.validateSafetySession(
                    autoBanThreshold, userTimeZone, sessionTimeoutMinutes, undoWindowSeconds);
            AppConfigValidator.validateSafetyAchievementThresholds(
                    achievementMatchTier1,
                    achievementMatchTier2,
                    achievementMatchTier3,
                    achievementMatchTier4,
                    achievementMatchTier5,
                    minSwipesForBehaviorAchievement);
            AppConfigValidator.validateSafetyBehaviorThresholds(
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
        Objects.requireNonNull(media, "media cannot be null");
        Objects.requireNonNull(auth, "auth cannot be null");
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
        private int rematchCooldownHours = DEFAULT_REMATCH_COOLDOWN_HOURS;
        private double suspiciousSwipeVelocity = 30.0;
        private boolean suspiciousSwipeVelocityBlockingEnabled = true;
        private double distanceWeight = 0.15;
        private double ageWeight = 0.10;
        private double interestWeight = 0.25;
        private double lifestyleWeight = 0.25;
        private double paceWeight = 0.15;
        private double responseWeight = 0.10;
        private int minSharedInterests = 3;
        private int sharedInterestsPreviewCount = 3;
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
        private String databaseDialect = "H2";
        private String databaseUrl = "jdbc:h2:./data/dating";
        private String databaseUsername = "sa";
        private int queryTimeoutSeconds = 30;
        private int maxPoolSize = 10;
        private int minIdle = 2;
        private int connectionTimeoutSeconds = 5;
        private int validationTimeoutSeconds = 3;
        private int idleTimeoutSeconds = 600;
        private int maxLifetimeSeconds = 1800;
        private int keepaliveTimeSeconds = 0;
        // MediaConfig fields
        private String photoStorageRoot = Path.of("data", "photos").toString();
        private String photoPublicBaseUrl = "";
        private long maxPhotoUploadBytes = 5L * 1024 * 1024;
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
        // AuthConfig fields
        private String tokenIssuer = "dating-app-phone-alpha";
        private String jwtSecret = DEVELOPMENT_ONLY_JWT_SECRET_PLACEHOLDER;
        private int accessTokenTtlSeconds = 900;
        private int refreshTokenTtlDays = 30;
        private int minPasswordLength = 12;

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

        public Builder maxPoolSize(int v) {
            this.maxPoolSize = v;
            return this;
        }

        public Builder minIdle(int v) {
            this.minIdle = v;
            return this;
        }

        public Builder connectionTimeoutSeconds(int v) {
            this.connectionTimeoutSeconds = v;
            return this;
        }

        public Builder validationTimeoutSeconds(int v) {
            this.validationTimeoutSeconds = v;
            return this;
        }

        public Builder idleTimeoutSeconds(int v) {
            this.idleTimeoutSeconds = v;
            return this;
        }

        public Builder maxLifetimeSeconds(int v) {
            this.maxLifetimeSeconds = v;
            return this;
        }

        public Builder keepaliveTimeSeconds(int v) {
            this.keepaliveTimeSeconds = v;
            return this;
        }

        public Builder databaseDialect(String v) {
            this.databaseDialect = v;
            return this;
        }

        public Builder databaseUrl(String v) {
            this.databaseUrl = v;
            return this;
        }

        public Builder databaseUsername(String v) {
            this.databaseUsername = v;
            return this;
        }

        public Builder photoStorageRoot(String v) {
            this.photoStorageRoot = v;
            return this;
        }

        public Builder photoPublicBaseUrl(String v) {
            this.photoPublicBaseUrl = v;
            return this;
        }

        public Builder maxPhotoUploadBytes(long v) {
            this.maxPhotoUploadBytes = v;
            return this;
        }

        public Builder tokenIssuer(String v) {
            this.tokenIssuer = v;
            return this;
        }

        public Builder jwtSecret(String v) {
            this.jwtSecret = v;
            return this;
        }

        public Builder accessTokenTtlSeconds(int v) {
            this.accessTokenTtlSeconds = v;
            return this;
        }

        public Builder refreshTokenTtlDays(int v) {
            this.refreshTokenTtlDays = v;
            return this;
        }

        public Builder minPasswordLength(int v) {
            this.minPasswordLength = v;
            return this;
        }

        public Builder maxSwipesPerSession(int v) {
            this.maxSwipesPerSession = v;
            return this;
        }

        public Builder rematchCooldownHours(int v) {
            this.rematchCooldownHours = v;
            return this;
        }

        public Builder suspiciousSwipeVelocity(double v) {
            this.suspiciousSwipeVelocity = v;
            return this;
        }

        public Builder suspiciousSwipeVelocityBlockingEnabled(boolean v) {
            this.suspiciousSwipeVelocityBlockingEnabled = v;
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

        public Builder sharedInterestsPreviewCount(int v) {
            this.sharedInterestsPreviewCount = v;
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
                    buildSafetyConfig(),
                    buildMediaConfig(),
                    buildAuthConfig());
        }

        private MatchingConfig buildMatchingConfig() {
            return new MatchingConfig(
                    dailyLikeLimit,
                    dailySuperLikeLimit,
                    dailyPassLimit,
                    maxSwipesPerSession,
                    rematchCooldownHours,
                    suspiciousSwipeVelocity,
                    suspiciousSwipeVelocityBlockingEnabled,
                    distanceWeight,
                    ageWeight,
                    interestWeight,
                    lifestyleWeight,
                    paceWeight,
                    responseWeight,
                    minSharedInterests,
                    sharedInterestsPreviewCount,
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
            return new StorageConfig(
                    databaseDialect,
                    databaseUrl,
                    databaseUsername,
                    queryTimeoutSeconds,
                    maxPoolSize,
                    minIdle,
                    connectionTimeoutSeconds,
                    validationTimeoutSeconds,
                    idleTimeoutSeconds,
                    maxLifetimeSeconds,
                    keepaliveTimeSeconds);
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

        private MediaConfig buildMediaConfig() {
            return new MediaConfig(photoStorageRoot, photoPublicBaseUrl, maxPhotoUploadBytes);
        }

        private AuthConfig buildAuthConfig() {
            return new AuthConfig(
                    tokenIssuer, jwtSecret, accessTokenTtlSeconds, refreshTokenTtlDays, minPasswordLength);
        }
    }
}
