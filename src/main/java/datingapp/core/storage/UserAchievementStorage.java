package datingapp.core.storage;

import datingapp.core.Achievement;
import datingapp.core.Achievement.UserAchievement;
import java.util.List;
import java.util.UUID;

/**
 * Storage interface for UserAchievement entities.
 * Defined in core, implemented in storage layer.
 */
public interface UserAchievementStorage {

    /** Saves a new achievement unlock. */
    void save(UserAchievement achievement);

    /** Gets all achievements unlocked by a user. */
    List<UserAchievement> getUnlocked(UUID userId);

    /** Checks if a user has a specific achievement. */
    boolean hasAchievement(UUID userId, Achievement achievement);

    /** Counts total achievements unlocked by a user. */
    int countUnlocked(UUID userId);
}
