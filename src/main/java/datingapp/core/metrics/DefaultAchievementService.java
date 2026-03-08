package datingapp.core.metrics;

import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DefaultAchievementService implements AchievementService {

    private final AppConfig config;
    private final AnalyticsStorage analyticsStorage;
    private final InteractionStorage interactionStorage;
    private final TrustSafetyStorage trustSafetyStorage;
    private final UserStorage userStorage;
    private final ProfileService profileService;

    public DefaultAchievementService(
            AppConfig config,
            AnalyticsStorage analyticsStorage,
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            UserStorage userStorage,
            ProfileService profileService) {
        this.config = Objects.requireNonNull(config);
        this.analyticsStorage = Objects.requireNonNull(analyticsStorage);
        this.interactionStorage = Objects.requireNonNull(interactionStorage);
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage);
        this.userStorage = Objects.requireNonNull(userStorage);
        this.profileService = Objects.requireNonNull(profileService);
    }

    @Override
    public List<UserAchievement> checkAndUnlock(UUID userId) {
        List<UserAchievement> newlyUnlocked = new ArrayList<>();
        User user = userStorage.get(userId).orElse(null);
        if (user == null) {
            return newlyUnlocked;
        }

        for (Achievement achievement : Achievement.values()) {
            if (!analyticsStorage.hasAchievement(userId, achievement) && isEarned(userId, user, achievement)) {
                UserAchievement unlocked = UserAchievement.create(userId, achievement);
                analyticsStorage.saveUserAchievement(unlocked);
                newlyUnlocked.add(unlocked);
            }
        }

        return newlyUnlocked;
    }

    @Override
    public List<UserAchievement> getUnlocked(UUID userId) {
        return analyticsStorage.getUnlockedAchievements(userId);
    }

    @Override
    public List<AchievementProgress> getProgress(UUID userId) {
        List<AchievementProgress> progress = new ArrayList<>();
        User user = userStorage.get(userId).orElse(null);
        if (user == null) {
            return progress;
        }

        for (Achievement achievement : Achievement.values()) {
            boolean unlocked = analyticsStorage.hasAchievement(userId, achievement);
            int[] currentAndTarget = getProgressValues(userId, user, achievement);
            progress.add(new AchievementProgress(achievement, currentAndTarget[0], currentAndTarget[1], unlocked));
        }

        return progress;
    }

    @Override
    public Map<Achievement.Category, List<AchievementProgress>> getProgressByCategory(UUID userId) {
        List<AchievementProgress> allProgress = getProgress(userId);
        Map<Achievement.Category, List<AchievementProgress>> grouped = new EnumMap<>(Achievement.Category.class);

        for (Achievement.Category category : Achievement.Category.values()) {
            grouped.put(category, new ArrayList<>());
        }

        for (AchievementProgress progress : allProgress) {
            grouped.get(progress.achievement().getCategory()).add(progress);
        }

        return grouped;
    }

    @Override
    public int countUnlocked(UUID userId) {
        return analyticsStorage.countUnlockedAchievements(userId);
    }

    private boolean isEarned(UUID userId, User user, Achievement achievement) {
        return switch (achievement) {
            case FIRST_SPARK -> getMatchCount(userId) >= config.safety().achievementMatchTier1();
            case SOCIAL_BUTTERFLY -> getMatchCount(userId) >= config.safety().achievementMatchTier2();
            case POPULAR -> getMatchCount(userId) >= config.safety().achievementMatchTier3();
            case SUPERSTAR -> getMatchCount(userId) >= config.safety().achievementMatchTier4();
            case LEGEND -> getMatchCount(userId) >= config.safety().achievementMatchTier5();
            case SELECTIVE -> isSelective(userId);
            case OPEN_MINDED -> isOpenMinded(userId);
            case COMPLETE_PACKAGE -> profileService.calculateCompleteness(user).percentage() == 100;
            case STORYTELLER -> getBioLength(user) > config.safety().bioAchievementLength();
            case LIFESTYLE_GURU ->
                profileService.countLifestyleFields(user) >= config.safety().lifestyleFieldTarget();
            case GUARDIAN -> trustSafetyStorage.countReportsBy(userId) >= 1;
        };
    }

    private int[] getProgressValues(UUID userId, User user, Achievement achievement) {
        int matchCount = getMatchCount(userId);
        return switch (achievement) {
            case FIRST_SPARK -> new int[] {matchCount, config.safety().achievementMatchTier1()};
            case SOCIAL_BUTTERFLY -> new int[] {matchCount, config.safety().achievementMatchTier2()};
            case POPULAR -> new int[] {matchCount, config.safety().achievementMatchTier3()};
            case SUPERSTAR -> new int[] {matchCount, config.safety().achievementMatchTier4()};
            case LEGEND -> new int[] {matchCount, config.safety().achievementMatchTier5()};
            case SELECTIVE, OPEN_MINDED ->
                new int[] {getTotalSwipes(userId), config.safety().minSwipesForBehaviorAchievement()};
            case COMPLETE_PACKAGE ->
                new int[] {profileService.calculateCompleteness(user).percentage(), 100};
            case STORYTELLER -> new int[] {getBioLength(user), config.safety().bioAchievementLength()};
            case LIFESTYLE_GURU ->
                new int[] {
                    profileService.countLifestyleFields(user), config.safety().lifestyleFieldTarget()
                };
            case GUARDIAN -> new int[] {trustSafetyStorage.countReportsBy(userId), 1};
        };
    }

    private int getMatchCount(UUID userId) {
        return interactionStorage.getAllMatchesFor(userId).size();
    }

    private int getTotalSwipes(UUID userId) {
        return interactionStorage.countByDirection(userId, Like.Direction.LIKE)
                + interactionStorage.countByDirection(userId, Like.Direction.PASS);
    }

    private boolean isSelective(UUID userId) {
        int totalSwipes = getTotalSwipes(userId);
        if (totalSwipes < config.safety().minSwipesForBehaviorAchievement()) {
            return false;
        }
        int likes = interactionStorage.countByDirection(userId, Like.Direction.LIKE);
        double likeRatio = (double) likes / totalSwipes;
        return likeRatio < config.safety().selectiveThreshold();
    }

    private boolean isOpenMinded(UUID userId) {
        int totalSwipes = getTotalSwipes(userId);
        if (totalSwipes < config.safety().minSwipesForBehaviorAchievement()) {
            return false;
        }
        int likes = interactionStorage.countByDirection(userId, Like.Direction.LIKE);
        double likeRatio = (double) likes / totalSwipes;
        return likeRatio > config.safety().openMindedThreshold();
    }

    private int getBioLength(User user) {
        return user.getBio() != null ? user.getBio().length() : 0;
    }
}
