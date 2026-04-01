package datingapp.core.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultAchievementService")
class DefaultAchievementServiceTest {

    private TestStorages.Users users;
    private TestStorages.Interactions interactions;
    private TestStorages.TrustSafety trustSafety;
    private TestStorages.Analytics analytics;
    private AppConfig config;
    private DefaultAchievementService achievementService;

    @BeforeEach
    void setUp() {
        users = new TestStorages.Users();
        interactions = new TestStorages.Interactions();
        trustSafety = new TestStorages.TrustSafety();
        analytics = new TestStorages.Analytics();
        config = AppConfig.defaults();

        ProfileService profileService = new ProfileService(users);
        achievementService =
                new DefaultAchievementService(config, analytics, interactions, trustSafety, users, profileService);
    }

    @Test
    @DisplayName("checkAndUnlock returns empty for unknown user")
    void checkAndUnlockUnknownUser() {
        assertTrue(achievementService.checkAndUnlock(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("checkAndUnlock awards FIRST_SPARK once match tier is met")
    void checkAndUnlockAwardsFirstSpark() {
        User user = createActiveUser("Achiever");
        users.save(user);

        for (int i = 0; i < config.safety().achievementMatchTier1(); i++) {
            interactions.save(Match.create(user.getId(), UUID.randomUUID()));
        }

        var unlocked = achievementService.checkAndUnlock(user.getId());

        assertTrue(unlocked.stream().anyMatch(a -> a.achievement() == EngagementDomain.Achievement.FIRST_SPARK));
        assertTrue(unlocked.stream().anyMatch(a -> a.achievement() == EngagementDomain.Achievement.COMPLETE_PACKAGE));
        assertEquals(2, achievementService.countUnlocked(user.getId()));
    }

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(AppClock.today().minusYears(26));
        user.setGender(Gender.FEMALE);
        user.setInterestedIn(EnumSet.of(Gender.MALE));
        user.setAgeRange(21, 40, 18, 120);
        user.setMaxDistanceKm(60, 500);
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setBio("Achievement test profile");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }
}
