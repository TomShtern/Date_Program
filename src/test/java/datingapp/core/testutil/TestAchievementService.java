package datingapp.core.testutil;

import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reusable achievement-service fixture for tests. */
public final class TestAchievementService implements AchievementService {

    private final List<UserAchievement> unlocked;
    private final List<AchievementProgress> progress;

    private TestAchievementService(List<UserAchievement> unlocked, List<AchievementProgress> progress) {
        this.unlocked = List.copyOf(unlocked);
        this.progress = List.copyOf(progress);
    }

    public static TestAchievementService empty() {
        return new TestAchievementService(List.of(), List.of());
    }

    public static TestAchievementService unlocked(UserAchievement... achievements) {
        return new TestAchievementService(Arrays.asList(achievements), List.of());
    }

    @Override
    public List<UserAchievement> checkAndUnlock(UUID userId) {
        return unlocked;
    }

    @Override
    public List<UserAchievement> getUnlocked(UUID userId) {
        return unlocked;
    }

    @Override
    public List<AchievementProgress> getProgress(UUID userId) {
        return progress;
    }

    @Override
    public Map<Achievement.Category, List<AchievementProgress>> getProgressByCategory(UUID userId) {
        return progress.stream()
                .collect(Collectors.groupingBy(
                        progressEntry -> progressEntry.achievement().getCategory()));
    }

    @Override
    public int countUnlocked(UUID userId) {
        return unlocked.size();
    }
}
