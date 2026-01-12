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
        int undoWindowSeconds // Time window for undo in seconds (30)
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
                30 // undoWindowSeconds
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
                    undoWindowSeconds);
        }
    }
}
