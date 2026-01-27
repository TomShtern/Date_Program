package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Achievement;
import datingapp.core.Stats.PlatformStats;
import datingapp.core.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("H2MetricsStorage")
class H2MetricsStorageTest {

    private H2MetricsStorage.Achievements achievements;
    private H2MetricsStorage.PlatformStatistics platformStats;
    private H2MetricsStorage.DailyPicks dailyPicks;
    private H2UserStorage userStorage;

    @BeforeEach
    void setUp() {
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:metrics-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        DatabaseManager.resetInstance();
        DatabaseManager dbManager = DatabaseManager.getInstance();

        achievements = new H2MetricsStorage.Achievements(dbManager);
        platformStats = new H2MetricsStorage.PlatformStatistics(dbManager);
        dailyPicks = new H2MetricsStorage.DailyPicks(dbManager);
        userStorage = new H2UserStorage(dbManager);
    }

    @Test
    @DisplayName("Saves and queries user achievements")
    void savesAndQueriesAchievements() {
        UUID userId = UUID.randomUUID();
        Achievement.UserAchievement achievement =
                new Achievement.UserAchievement(UUID.randomUUID(), userId, Achievement.FIRST_SPARK, Instant.now());

        saveUser(userId);
        achievements.save(achievement);

        assertTrue(achievements.hasAchievement(userId, Achievement.FIRST_SPARK));
        List<Achievement.UserAchievement> unlocked = achievements.getUnlocked(userId);
        assertEquals(1, unlocked.size());
        assertEquals(Achievement.FIRST_SPARK, unlocked.get(0).achievement());
        assertEquals(1, achievements.countUnlocked(userId));
    }

    @Test
    @DisplayName("Saves and retrieves platform stats")
    void savesAndRetrievesPlatformStats() {
        PlatformStats stats = new PlatformStats(UUID.randomUUID(), Instant.now(), 100, 10.0, 5.0, 0.25, 0.75);

        platformStats.save(stats);

        Optional<PlatformStats> loaded = platformStats.getLatest();
        assertTrue(loaded.isPresent());
        assertEquals(stats.totalActiveUsers(), loaded.get().totalActiveUsers());
        assertEquals(stats.avgLikesReceived(), loaded.get().avgLikesReceived());
    }

    @Test
    @DisplayName("Tracks daily pick views")
    void tracksDailyPickViews() {
        UUID userId = UUID.randomUUID();
        java.time.LocalDate today = java.time.LocalDate.now();

        saveUser(userId);
        dailyPicks.markViewed(userId, today);

        assertTrue(dailyPicks.hasViewed(userId, today));
        assertFalse(dailyPicks.hasViewed(userId, today.plusDays(1)));
    }

    private void saveUser(UUID userId) {
        userStorage.save(new User(userId, "User_" + userId));
    }
}
