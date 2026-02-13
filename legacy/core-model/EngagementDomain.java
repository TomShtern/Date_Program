package datingapp.core.model;

import datingapp.core.AppClock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Consolidated engagement domain models (achievements + stats). */
public final class EngagementDomain {

    private EngagementDomain() {
        // Utility class
    }

    /** Creates a builder for assembling user stats snapshots. */
    public static UserStats.StatsBuilder builder() {
        return new UserStats.StatsBuilder();
    }

    /** Achievement catalog. */
    public enum Achievement {
        FIRST_SPARK("First Spark", "Get your first match", "💫", Category.MATCHING, 1),
        SOCIAL_BUTTERFLY("Social Butterfly", "Get 5 matches", "🦋", Category.MATCHING, 5),
        POPULAR("Popular", "Get 10 matches", "⭐", Category.MATCHING, 10),
        SUPERSTAR("Superstar", "Get 25 matches", "🌟", Category.MATCHING, 25),
        LEGEND("Legend", "Get 50 matches", "👑", Category.MATCHING, 50),
        SELECTIVE("Selective", "Like ratio < 20% (50+ swipes)", "🎯", Category.BEHAVIOR, 50),
        OPEN_MINDED("Open-Minded", "Like ratio > 60% (50+ swipes)", "💝", Category.BEHAVIOR, 50),
        COMPLETE_PACKAGE("Complete Package", "100% profile completion", "✅", Category.PROFILE, 100),
        STORYTELLER("Storyteller", "Bio over 100 characters", "📖", Category.PROFILE, 100),
        LIFESTYLE_GURU("Lifestyle Guru", "All lifestyle fields filled", "🧘", Category.PROFILE, 5),
        GUARDIAN("Guardian", "Report a fake profile", "🛡️", Category.SAFETY, 1);

        /** Achievement categories for grouping in UI. */
        public enum Category {
            MATCHING("Matching Milestones"),
            BEHAVIOR("Swiping Behavior"),
            PROFILE("Profile Excellence"),
            SAFETY("Safety & Community");

            private final String displayName;

            Category(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        private final String displayName;
        private final String description;
        private final String icon;
        private final Category category;
        private final int threshold;

        Achievement(String displayName, String description, String icon, Category category, int threshold) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.category = category;
            this.threshold = threshold;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }

        public Category getCategory() {
            return category;
        }

        public int getThreshold() {
            return threshold;
        }

        public String getFormattedDisplay() {
            return icon + " " + displayName;
        }

        /** Persisted unlocked achievement for a user. */
        public record UserAchievement(UUID id, UUID userId, Achievement achievement, Instant unlockedAt) {

            public UserAchievement {
                Objects.requireNonNull(id, "id cannot be null");
                Objects.requireNonNull(userId, "userId cannot be null");
                Objects.requireNonNull(achievement, "achievement cannot be null");
                Objects.requireNonNull(unlockedAt, "unlockedAt cannot be null");
            }

            public static UserAchievement create(UUID userId, Achievement achievement) {
                return new UserAchievement(UUID.randomUUID(), userId, achievement, AppClock.now());
            }

            public static UserAchievement of(UUID id, UUID userId, Achievement achievement, Instant unlockedAt) {
                return new UserAchievement(id, userId, achievement, unlockedAt);
            }
        }
    }

    /** Immutable snapshot of user engagement statistics. */
    public record UserStats(
            UUID id,
            UUID userId,
            Instant computedAt,
            int totalSwipesGiven,
            int likesGiven,
            int passesGiven,
            double likeRatio,
            int totalSwipesReceived,
            int likesReceived,
            int passesReceived,
            double incomingLikeRatio,
            int totalMatches,
            int activeMatches,
            double matchRate,
            int blocksGiven,
            int blocksReceived,
            int reportsGiven,
            int reportsReceived,
            double reciprocityScore,
            double selectivenessScore,
            double attractivenessScore) {

        public UserStats {
            Objects.requireNonNull(id);
            Objects.requireNonNull(userId);
            Objects.requireNonNull(computedAt);

            validateNonNegative(totalSwipesGiven, "totalSwipesGiven");
            validateNonNegative(likesGiven, "likesGiven");
            validateNonNegative(passesGiven, "passesGiven");
            validateNonNegative(totalSwipesReceived, "totalSwipesReceived");
            validateNonNegative(likesReceived, "likesReceived");
            validateNonNegative(passesReceived, "passesReceived");
            validateNonNegative(totalMatches, "totalMatches");
            validateNonNegative(activeMatches, "activeMatches");
            validateNonNegative(blocksGiven, "blocksGiven");
            validateNonNegative(blocksReceived, "blocksReceived");
            validateNonNegative(reportsGiven, "reportsGiven");
            validateNonNegative(reportsReceived, "reportsReceived");

            validateRatio(likeRatio, "likeRatio");
            validateRatio(incomingLikeRatio, "incomingLikeRatio");
            validateRatio(matchRate, "matchRate");
            validateRatio(reciprocityScore, "reciprocityScore");
            validateRatio(selectivenessScore, "selectivenessScore");
            validateRatio(attractivenessScore, "attractivenessScore");
        }

        private static void validateNonNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(name + " cannot be negative, got: " + value);
            }
        }

        private static void validateRatio(double value, String name) {
            if (value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException(name + " must be 0.0-1.0, got: " + value);
            }
        }

        public static UserStats create(UUID userId, StatsBuilder builder) {
            return new UserStats(
                    UUID.randomUUID(),
                    userId,
                    AppClock.now(),
                    builder.totalSwipesGiven,
                    builder.likesGiven,
                    builder.passesGiven,
                    builder.likeRatio,
                    builder.totalSwipesReceived,
                    builder.likesReceived,
                    builder.passesReceived,
                    builder.incomingLikeRatio,
                    builder.totalMatches,
                    builder.activeMatches,
                    builder.matchRate,
                    builder.blocksGiven,
                    builder.blocksReceived,
                    builder.reportsGiven,
                    builder.reportsReceived,
                    builder.reciprocityScore,
                    builder.selectivenessScore,
                    builder.attractivenessScore);
        }

        @SuppressWarnings("java:S1104")
        public static class StatsBuilder {
            public int totalSwipesGiven = 0;
            public int likesGiven = 0;
            public int passesGiven = 0;
            public double likeRatio = 0.0;
            public int totalSwipesReceived = 0;
            public int likesReceived = 0;
            public int passesReceived = 0;
            public double incomingLikeRatio = 0.0;
            public int totalMatches = 0;
            public int activeMatches = 0;
            public double matchRate = 0.0;
            public int blocksGiven = 0;
            public int blocksReceived = 0;
            public int reportsGiven = 0;
            public int reportsReceived = 0;
            public double reciprocityScore = 0.0;
            public double selectivenessScore = 0.5;
            public double attractivenessScore = 0.5;
        }

        private static final String PERCENT_FORMAT = "%.1f%%";

        public String getLikeRatioDisplay() {
            return String.format(PERCENT_FORMAT, likeRatio * 100);
        }

        public String getMatchRateDisplay() {
            return String.format(PERCENT_FORMAT, matchRate * 100);
        }

        public String getReciprocityDisplay() {
            return String.format(PERCENT_FORMAT, reciprocityScore * 100);
        }
    }

    /** Platform-wide statistics for computing relative scores. */
    public record PlatformStats(
            UUID id,
            Instant computedAt,
            int totalActiveUsers,
            double avgLikesReceived,
            double avgLikesGiven,
            double avgMatchRate,
            double avgLikeRatio) {

        public PlatformStats {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(computedAt, "computedAt cannot be null");
            if (totalActiveUsers < 0) {
                throw new IllegalArgumentException("totalActiveUsers cannot be negative");
            }
            if (avgLikesReceived < 0 || avgLikesGiven < 0) {
                throw new IllegalArgumentException("average like counts cannot be negative");
            }
            if (avgMatchRate < 0 || avgMatchRate > 1) {
                throw new IllegalArgumentException("avgMatchRate must be 0-1");
            }
            if (avgLikeRatio < 0 || avgLikeRatio > 1) {
                throw new IllegalArgumentException("avgLikeRatio must be 0-1");
            }
        }

        public static PlatformStats create(
                int totalActiveUsers,
                double avgLikesReceived,
                double avgLikesGiven,
                double avgMatchRate,
                double avgLikeRatio) {
            return new PlatformStats(
                    UUID.randomUUID(),
                    AppClock.now(),
                    totalActiveUsers,
                    avgLikesReceived,
                    avgLikesGiven,
                    avgMatchRate,
                    avgLikeRatio);
        }

        public static PlatformStats empty() {
            return new PlatformStats(UUID.randomUUID(), AppClock.now(), 0, 0.0, 0.0, 0.0, 0.5);
        }
    }
}
