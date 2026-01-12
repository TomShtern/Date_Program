package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for UserAchievement domain model. */
class UserAchievementTest {

    @Test
    @DisplayName("create() factory generates ID and timestamp")
    void createFactoryGeneratesIdAndTimestamp() {
        UUID userId = UUID.randomUUID();
        Achievement achievement = Achievement.FIRST_SPARK;

        UserAchievement ua = UserAchievement.create(userId, achievement);

        assertNotNull(ua.id(), "ID should be generated");
        assertEquals(userId, ua.userId(), "User ID should match");
        assertEquals(achievement, ua.achievement(), "Achievement should match");
        assertNotNull(ua.unlockedAt(), "Unlock timestamp should be set");
    }

    @Test
    @DisplayName("of() factory loads from storage with all fields")
    void ofFactoryLoadsFromStorage() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Achievement achievement = Achievement.SOCIAL_BUTTERFLY;
        Instant timestamp = Instant.now().minusSeconds(3600);

        UserAchievement ua = UserAchievement.of(id, userId, achievement, timestamp);

        assertEquals(id, ua.id());
        assertEquals(userId, ua.userId());
        assertEquals(achievement, ua.achievement());
        assertEquals(timestamp, ua.unlockedAt());
    }

    @Test
    @DisplayName("IDs are unique for multiple unlocks")
    void idsAreUnique() {
        UUID userId = UUID.randomUUID();

        UserAchievement ua1 = UserAchievement.create(userId, Achievement.FIRST_SPARK);
        UserAchievement ua2 = UserAchievement.create(userId, Achievement.SOCIAL_BUTTERFLY);

        assertNotEquals(ua1.id(), ua2.id(), "Different unlocks should have unique IDs");
    }

    @Test
    @DisplayName("Null ID throws NullPointerException")
    void nullIdThrows() {
        assertThrows(
                NullPointerException.class,
                () -> UserAchievement.of(null, UUID.randomUUID(), Achievement.FIRST_SPARK, Instant.now()));
    }

    @Test
    @DisplayName("Null userId throws NullPointerException")
    void nullUserIdThrows() {
        assertThrows(NullPointerException.class, () -> UserAchievement.create(null, Achievement.FIRST_SPARK));
    }

    @Test
    @DisplayName("Null achievement throws NullPointerException")
    void nullAchievementThrows() {
        assertThrows(NullPointerException.class, () -> UserAchievement.create(UUID.randomUUID(), null));
    }

    @Test
    @DisplayName("Null unlockedAt throws NullPointerException")
    void nullUnlockedAtThrows() {
        assertThrows(
                NullPointerException.class,
                () -> UserAchievement.of(UUID.randomUUID(), UUID.randomUUID(), Achievement.FIRST_SPARK, null));
    }

    @Test
    @DisplayName("Record equality works correctly")
    void recordEquality() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Achievement achievement = Achievement.FIRST_SPARK;
        Instant timestamp = Instant.now();

        UserAchievement ua1 = UserAchievement.of(id, userId, achievement, timestamp);
        UserAchievement ua2 = UserAchievement.of(id, userId, achievement, timestamp);

        assertEquals(ua1, ua2, "Records with same values should be equal");
        assertEquals(ua1.hashCode(), ua2.hashCode(), "Hash codes should match");
    }
}
