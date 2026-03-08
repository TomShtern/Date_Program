package datingapp.core.metrics;

import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Service for tracking, unlocking, and querying user achievements. */
public interface AchievementService {

    /** Progress towards an achievement. */
    record AchievementProgress(Achievement achievement, int current, int target, boolean unlocked) {
        public int getProgressPercent() {
            if (unlocked || target <= 0) {
                return 100;
            }
            return Math.min(100, current * 100 / target);
        }

        public String getProgressDisplay() {
            if (unlocked) {
                return "✓ Unlocked";
            }
            int displayCurrent = Math.min(current, target);
            return displayCurrent + "/" + target;
        }
    }

    /** Check all achievements for a user and unlock any newly earned ones. */
    List<UserAchievement> checkAndUnlock(UUID userId);

    /** Get all unlocked achievements for a user. */
    List<UserAchievement> getUnlocked(UUID userId);

    /** Get progress for all achievements (both locked and unlocked). */
    List<AchievementProgress> getProgress(UUID userId);

    /** Get progress grouped by category. */
    Map<Achievement.Category, List<AchievementProgress>> getProgressByCategory(UUID userId);

    /** Count total unlocked achievements. */
    int countUnlocked(UUID userId);
}
