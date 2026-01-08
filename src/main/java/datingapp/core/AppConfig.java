package datingapp.core;

import java.time.Duration;

/**
 * Centralized, immutable application configuration.
 * All configurable values should be defined here for easy modification.
 *
 * This enables future drop-in changes:
 * - Different profiles (dev/prod)
 * - External configuration (properties files, env vars)
 * - Testing with different thresholds
 */
public record AppConfig(
        int autoBanThreshold, // Number of reports before auto-ban
        int dailyLikeLimit, // Max likes per day (Phase 3)
        int dailySuperLikeLimit, // Max super likes per day (Phase 3)
        int maxInterests, // Max interests per user (Phase 2)
        int maxPhotos, // Max photos per user
        int maxBioLength, // Max bio length
        int maxReportDescLength, // Max report description length
        // Session tracking (Phase 0.5b)
        int sessionTimeoutMinutes, // Minutes of inactivity before session ends
        int maxSwipesPerSession, // Anti-bot: max swipes in single session
        double suspiciousSwipeVelocity // Anti-bot: swipes/min threshold for warning
) {
    /**
     * Default configuration values.
     */
    public static AppConfig defaults() {
        return new AppConfig(
                3, // autoBanThreshold
                100, // dailyLikeLimit
                1, // dailySuperLikeLimit
                5, // maxInterests
                2, // maxPhotos
                500, // maxBioLength
                500, // maxReportDescLength
                5, // sessionTimeoutMinutes
                500, // maxSwipesPerSession
                30.0 // suspiciousSwipeVelocity
        );
    }

    /**
     * Get session timeout as Duration.
     */
    public Duration getSessionTimeout() {
        return Duration.ofMinutes(sessionTimeoutMinutes);
    }

    /**
     * Builder for creating custom configurations.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int autoBanThreshold = 3;
        private int dailyLikeLimit = 100;
        private int dailySuperLikeLimit = 1;
        private int maxInterests = 5;
        private int maxPhotos = 2;
        private int maxBioLength = 500;
        private int maxReportDescLength = 500;
        private int sessionTimeoutMinutes = 5;
        private int maxSwipesPerSession = 500;
        private double suspiciousSwipeVelocity = 30.0;

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

        public AppConfig build() {
            return new AppConfig(
                    autoBanThreshold,
                    dailyLikeLimit,
                    dailySuperLikeLimit,
                    maxInterests,
                    maxPhotos,
                    maxBioLength,
                    maxReportDescLength,
                    sessionTimeoutMinutes,
                    maxSwipesPerSession,
                    suspiciousSwipeVelocity);
        }
    }
}
