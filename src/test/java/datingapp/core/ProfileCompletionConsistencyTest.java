package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.metrics.DefaultAchievementService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Profile completion consistency")
class ProfileCompletionConsistencyTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    private TestStorages.Users users;
    private TestStorages.Interactions interactions;
    private TestStorages.TrustSafety trustSafety;
    private TestStorages.Analytics analytics;
    private ProfileService profileService;
    private ProfileActivationPolicy activationPolicy;
    private DefaultAchievementService achievementService;

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        users = new TestStorages.Users();
        interactions = new TestStorages.Interactions();
        trustSafety = new TestStorages.TrustSafety();
        analytics = new TestStorages.Analytics();
        profileService = new ProfileService(users);
        activationPolicy = new ProfileActivationPolicy();
        achievementService = new DefaultAchievementService(
                AppConfig.defaults(), analytics, interactions, trustSafety, users, profileService);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("required profile truth is consistent across completeness, activation, and achievements")
    void requiredProfileTruthIsConsistentAcrossSurfaces() {
        User user = minimallyCompleteUser();
        users.save(user);

        assertTrue(user.isComplete());
        assertTrue(activationPolicy.canActivate(user).isAllowed());
        assertEquals(100, profileService.calculateCompleteness(user).percentage());
        assertTrue(achievementService.checkAndUnlock(user.getId()).stream()
                .anyMatch(achievement -> achievement.achievement() == Achievement.COMPLETE_PACKAGE));
    }

    private static User minimallyCompleteUser() {
        User user = new User(UUID.randomUUID(), "Consistent Casey");
        user.setBio("A profile with the required fields filled and no optional extras.");
        user.setBirthDate(AppClock.today().minusYears(27));
        user.setGender(Gender.FEMALE);
        user.setInterestedIn(Set.of(Gender.MALE));
        user.setLocation(32.0853, 34.7818);
        user.addPhotoUrl("https://example.com/casey.jpg");
        user.setPacePreferences(new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        return user;
    }
}
