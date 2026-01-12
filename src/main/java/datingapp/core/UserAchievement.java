package datingapp.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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
