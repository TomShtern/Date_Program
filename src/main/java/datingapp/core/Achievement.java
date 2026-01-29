package datingapp.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Enum defining all achievements in the gamification system. Achievements encourage engagement and
 * add personality to profiles.
 */
public enum Achievement {
    // === Matching Milestones ===
    FIRST_SPARK("First Spark", "Get your first match", "💫", Category.MATCHING, 1),
    SOCIAL_BUTTERFLY("Social Butterfly", "Get 5 matches", "🦋", Category.MATCHING, 5),
    POPULAR("Popular", "Get 10 matches", "⭐", Category.MATCHING, 10),
    SUPERSTAR("Superstar", "Get 25 matches", "🌟", Category.MATCHING, 25),
    LEGEND("Legend", "Get 50 matches", "👑", Category.MATCHING, 50),

    // === Swiping Behavior ===
    SELECTIVE("Selective", "Like ratio < 20% (50+ swipes)", "🎯", Category.BEHAVIOR, 50),
    OPEN_MINDED("Open-Minded", "Like ratio > 60% (50+ swipes)", "💝", Category.BEHAVIOR, 50),

    // === Profile Excellence ===
    COMPLETE_PACKAGE("Complete Package", "100% profile completion", "✅", Category.PROFILE, 100),
    STORYTELLER("Storyteller", "Bio over 100 characters", "📖", Category.PROFILE, 100),
    LIFESTYLE_GURU("Lifestyle Guru", "All lifestyle fields filled", "🧘", Category.PROFILE, 5),

    // === Safety ===
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

    /** Get formatted display string: "💫 First Spark". */
    public String getFormattedDisplay() {
        return icon + " " + displayName;
    }

    /** Record representing a user's unlocked achievement. Immutable and stored in the database. */
    public record UserAchievement(UUID id, UUID userId, Achievement achievement, Instant unlockedAt) {

        /**
         * Creates a UserAchievement record with validation.
         *
         * @param id the unique identifier for this achievement unlock
         * @param userId the user who unlocked the achievement
         * @param achievement the achievement that was unlocked
         * @param unlockedAt when the achievement was unlocked
         */
        public UserAchievement {
            Objects.requireNonNull(id, "id cannot be null");
            Objects.requireNonNull(userId, "userId cannot be null");
            Objects.requireNonNull(achievement, "achievement cannot be null");
            Objects.requireNonNull(unlockedAt, "unlockedAt cannot be null");
        }

        /** Factory method to create a new achievement unlock. */
        public static UserAchievement create(UUID userId, Achievement achievement) {
            return new UserAchievement(UUID.randomUUID(), userId, achievement, Instant.now());
        }

        /** Factory method for loading from storage. */
        public static UserAchievement of(UUID id, UUID userId, Achievement achievement, Instant unlockedAt) {
            return new UserAchievement(id, userId, achievement, unlockedAt);
        }
    }
}
