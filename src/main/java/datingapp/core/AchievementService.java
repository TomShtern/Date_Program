package datingapp.core;

import datingapp.core.Achievement.UserAchievement;
import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.ReportStorage;
import datingapp.core.storage.StatsStorage;
import datingapp.core.storage.UserStorage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Service for checking and unlocking achievements. Evaluates user activity
 * against achievement
 * criteria and awards new achievements.
 */
public class AchievementService {

    private final StatsStorage achievementStorage;
    private final MatchStorage matchStorage;
    private final LikeStorage likeStorage;
    private final UserStorage userStorage;
    private final ReportStorage reportStorage;
    private final ProfilePreviewService profilePreviewService;
    private final AppConfig config;

    public AchievementService(
            StatsStorage achievementStorage,
            MatchStorage matchStorage,
            LikeStorage likeStorage,
            UserStorage userStorage,
            ReportStorage reportStorage,
            ProfilePreviewService profilePreviewService,
            AppConfig config) {
        this.achievementStorage = Objects.requireNonNull(achievementStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.userStorage = Objects.requireNonNull(userStorage);
        this.reportStorage = Objects.requireNonNull(reportStorage);
        this.profilePreviewService = Objects.requireNonNull(profilePreviewService);
        this.config = Objects.requireNonNull(config);
    }

    /** Progress towards an achievement. */
    public record AchievementProgress(Achievement achievement, int current, int target, boolean unlocked) {

        public AchievementProgress {
            Objects.requireNonNull(achievement, "achievement cannot be null");
            if (current < 0 || target <= 0) {
                throw new IllegalArgumentException("current and target must be positive");
            }
            if (unlocked && current < target) {
                throw new IllegalArgumentException("unlocked achievements must meet target");
            }
        }

        /**
         * Gets the progress percentage (0-100).
         *
         * @return The progress percentage
         */
        public int getProgressPercent() {
            if (unlocked || target == 0) {
                return 100;
            }
            return Math.min(100, current * 100 / target);
        }

        /**
         * Gets a display string for the progress.
         *
         * @return The progress display string
         */
        public String getProgressDisplay() {
            if (unlocked) {
                return "✓ Unlocked";
            }
            int displayCurrent = Math.min(current, target);
            return displayCurrent + "/" + target;
        }
    }

    /**
     * Check all achievements for a user and unlock any newly earned ones.
     *
     * @param userId the user ID
     * @return list of newly unlocked achievements (empty if none)
     */
    public List<UserAchievement> checkAndUnlock(UUID userId) {
        List<UserAchievement> newlyUnlocked = new ArrayList<>();
        User user = userStorage.get(userId);
        if (user == null) {
            return newlyUnlocked;
        }

        // Check each achievement
        for (Achievement achievement : Achievement.values()) {
            if (!achievementStorage.hasAchievement(userId, achievement) && isEarned(userId, user, achievement)) {
                UserAchievement unlocked = UserAchievement.create(userId, achievement);
                achievementStorage.saveUserAchievement(unlocked);
                newlyUnlocked.add(unlocked);
            }
        }

        return newlyUnlocked;
    }

    /** Get all unlocked achievements for a user. */
    public List<UserAchievement> getUnlocked(UUID userId) {
        return achievementStorage.getUnlockedAchievements(userId);
    }

    /**
     * Get progress for all achievements (both locked and unlocked).
     *
     * @param userId The user ID
     * @return List of achievement progress
     */
    public List<AchievementProgress> getProgress(UUID userId) {
        List<AchievementProgress> progress = new ArrayList<>();
        User user = userStorage.get(userId);
        if (user == null) {
            return progress;
        }

        for (Achievement achievement : Achievement.values()) {
            boolean unlocked = achievementStorage.hasAchievement(userId, achievement);
            int[] currentAndTarget = getProgressValues(userId, user, achievement);
            progress.add(new AchievementProgress(achievement, currentAndTarget[0], currentAndTarget[1], unlocked));
        }

        return progress;
    }

    /** Get progress grouped by category. */
    public Map<Achievement.Category, List<AchievementProgress>> getProgressByCategory(UUID userId) {
        List<AchievementProgress> allProgress = getProgress(userId);
        Map<Achievement.Category, List<AchievementProgress>> grouped = new EnumMap<>(Achievement.Category.class);

        for (Achievement.Category category : Achievement.Category.values()) {
            grouped.put(category, new ArrayList<>());
        }

        for (AchievementProgress p : allProgress) {
            grouped.get(p.achievement().getCategory()).add(p);
        }

        return grouped;
    }

    /** Check if achievement criteria is met. */
    private boolean isEarned(UUID userId, User user, Achievement achievement) {
        return switch (achievement) {
            // Matching milestones
            case FIRST_SPARK -> getMatchCount(userId) >= config.achievementMatchTier1();
            case SOCIAL_BUTTERFLY -> getMatchCount(userId) >= config.achievementMatchTier2();
            case POPULAR -> getMatchCount(userId) >= config.achievementMatchTier3();
            case SUPERSTAR -> getMatchCount(userId) >= config.achievementMatchTier4();
            case LEGEND -> getMatchCount(userId) >= config.achievementMatchTier5();

            // Behavior achievements
            case SELECTIVE -> isSelective(userId);
            case OPEN_MINDED -> isOpenMinded(userId);

            // Profile achievements
            case COMPLETE_PACKAGE -> isProfileComplete(user);
            case STORYTELLER -> hasBioOver100Chars(user);
            case LIFESTYLE_GURU -> hasAllLifestyleFields(user);

            // Safety achievements
            case GUARDIAN -> hasReportedUser(userId);
        };
    }

    /**
     * Get current progress values for an achievement.
     *
     * @return int[2] where [0] = current, [1] = target
     */
    private int[] getProgressValues(UUID userId, User user, Achievement achievement) {
        int matchCount = getMatchCount(userId);
        return switch (achievement) {
            // Matching milestones
            case FIRST_SPARK -> new int[] {matchCount, config.achievementMatchTier1()};
            case SOCIAL_BUTTERFLY -> new int[] {matchCount, config.achievementMatchTier2()};
            case POPULAR -> new int[] {matchCount, config.achievementMatchTier3()};
            case SUPERSTAR -> new int[] {matchCount, config.achievementMatchTier4()};
            case LEGEND -> new int[] {matchCount, config.achievementMatchTier5()};

            // Behavior - needs minimum swipes
            case SELECTIVE, OPEN_MINDED -> new int[] {getTotalSwipes(userId), config.minSwipesForBehaviorAchievement()};

            // Profile achievements
            case COMPLETE_PACKAGE -> new int[] {getProfileCompleteness(user), 100};
            case STORYTELLER -> new int[] {getBioLength(user), config.bioAchievementLength()};
            case LIFESTYLE_GURU -> new int[] {getLifestyleFieldCount(user), config.lifestyleFieldTarget()};

            // Safety
            case GUARDIAN -> new int[] {getReportsGiven(userId), 1};
        };
    }

    // === Helper Methods ===

    private int getMatchCount(UUID userId) {
        return matchStorage.getAllMatchesFor(userId).size();
    }

    private int getTotalSwipes(UUID userId) {
        return likeStorage.countByDirection(userId, Like.Direction.LIKE)
                + likeStorage.countByDirection(userId, Like.Direction.PASS);
    }

    private boolean isSelective(UUID userId) {
        int totalSwipes = getTotalSwipes(userId);
        if (totalSwipes < config.minSwipesForBehaviorAchievement()) {
            return false;
        }
        int likes = likeStorage.countByDirection(userId, Like.Direction.LIKE);
        double likeRatio = (double) likes / totalSwipes;
        return likeRatio < config.selectiveThreshold();
    }

    private boolean isOpenMinded(UUID userId) {
        int totalSwipes = getTotalSwipes(userId);
        if (totalSwipes < config.minSwipesForBehaviorAchievement()) {
            return false;
        }
        int likes = likeStorage.countByDirection(userId, Like.Direction.LIKE);
        double likeRatio = (double) likes / totalSwipes;
        return likeRatio > config.openMindedThreshold();
    }

    private boolean isProfileComplete(User user) {
        return getProfileCompleteness(user) == 100;
    }

    private int getProfileCompleteness(User user) {
        ProfilePreviewService.ProfileCompleteness completeness = profilePreviewService.calculateCompleteness(user);
        return completeness.percentage();
    }

    private boolean hasBioOver100Chars(User user) {
        return getBioLength(user) > config.bioAchievementLength();
    }

    private int getBioLength(User user) {
        return user.getBio() != null ? user.getBio().length() : 0;
    }

    private boolean hasAllLifestyleFields(User user) {
        return getLifestyleFieldCount(user) >= config.lifestyleFieldTarget();
    }

    private int getLifestyleFieldCount(User user) {
        int count = 0;
        if (user.getSmoking() != null) {
            count++;
        }
        if (user.getDrinking() != null) {
            count++;
        }
        if (user.getWantsKids() != null) {
            count++;
        }
        if (user.getLookingFor() != null) {
            count++;
        }
        if (user.getHeightCm() != null) {
            count++;
        }
        return count;
    }

    private boolean hasReportedUser(UUID userId) {
        return getReportsGiven(userId) >= 1;
    }

    private int getReportsGiven(UUID userId) {
        return reportStorage.countReportsBy(userId);
    }

    /** Count total unlocked achievements. */
    public int countUnlocked(UUID userId) {
        return achievementStorage.countUnlockedAchievements(userId);
    }
}
