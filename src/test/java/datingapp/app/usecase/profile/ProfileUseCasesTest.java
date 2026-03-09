package datingapp.app.usecase.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileUseCases.AchievementsQuery;
import datingapp.app.usecase.profile.ProfileUseCases.SaveProfileCommand;
import datingapp.app.usecase.profile.ProfileUseCases.StatsQuery;
import datingapp.app.usecase.profile.ProfileUseCases.UpdateDiscoveryPreferencesCommand;
import datingapp.app.usecase.profile.ProfileUseCases.UpsertProfileNoteCommand;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.profile.ProfileService;
import datingapp.core.profile.ValidationService;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileUseCases")
class ProfileUseCasesTest {

    private AppConfig config;
    private TestStorages.Users userStorage;
    private ProfileUseCases useCases;

    @BeforeEach
    void setUp() {
        config = AppConfig.defaults();
        userStorage = new TestStorages.Users();
        var interactionStorage = new TestStorages.Interactions();
        var analyticsStorage = new TestStorages.Analytics();
        var trustSafetyStorage = new TestStorages.TrustSafety();

        var profileService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);
        var validationService = new ValidationService(config);
        var metricsService =
                new ActivityMetricsService(interactionStorage, trustSafetyStorage, analyticsStorage, config);

        useCases = new ProfileUseCases(userStorage, profileService, validationService, metricsService, config);
    }

    @Test
    @DisplayName("saveProfile should activate complete INCOMPLETE profile")
    void saveProfileActivatesCompleteProfile() {
        User user = User.StorageBuilder.create(UUID.randomUUID(), "Ready User", AppClock.now())
                .state(User.UserState.INCOMPLETE)
                .bio("Complete profile bio")
                .birthDate(AppClock.today().minusYears(25))
                .gender(User.Gender.MALE)
                .interestedIn(Set.of(User.Gender.FEMALE))
                .photoUrls(List.of("http://example.com/photo.jpg"))
                .location(32.0853, 34.7818)
                .hasLocationSet(true)
                .maxDistanceKm(config.matching().maxDistanceKm())
                .ageRange(config.validation().minAge(), config.validation().maxAge())
                .pacePreferences(new PacePreferences(
                        MessagingFrequency.OFTEN,
                        TimeToFirstDate.FEW_DAYS,
                        CommunicationStyle.MIX_OF_EVERYTHING,
                        DepthPreference.DEEP_CHAT))
                .build();

        var result = useCases.saveProfile(new SaveProfileCommand(UserContext.cli(user.getId()), user));

        assertTrue(result.success());
        assertTrue(result.data().activated());
        assertEquals(User.UserState.ACTIVE, result.data().user().getState());
    }

    @Test
    @DisplayName("saveProfile should sanitize name and bio before persisting")
    void saveProfileSanitizesNameAndBio() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "<b>Alice</b>");
        user.setBio("<script>alert('xss')</script>Bio");

        var result = useCases.saveProfile(new SaveProfileCommand(UserContext.cli(user.getId()), user));

        assertTrue(result.success());
        assertEquals("Alice", result.data().user().getName());
        assertEquals("Bio", result.data().user().getBio());
    }

    @Test
    @DisplayName("upsertProfileNote should sanitize note content")
    void upsertProfileNoteSanitizesContent() {
        User author = TestUserFactory.createActiveUser(UUID.randomUUID(), "Author");
        User subject = TestUserFactory.createActiveUser(UUID.randomUUID(), "Subject");
        userStorage.save(author);
        userStorage.save(subject);

        var result = useCases.upsertProfileNote(new UpsertProfileNoteCommand(
                UserContext.cli(author.getId()), subject.getId(), "<img src=x onerror=alert('xss')>Keep"));

        assertTrue(result.success());
        assertEquals("Keep", result.data().content());
    }

    @Test
    @DisplayName("updateDiscoveryPreferences should clamp out-of-range values")
    void updateDiscoveryPreferencesClampsValues() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Preference User");
        userStorage.save(user);

        var result = useCases.updateDiscoveryPreferences(new UpdateDiscoveryPreferencesCommand(
                UserContext.cli(user.getId()), 1, 500, 9_999, Set.of(User.Gender.OTHER)));

        assertTrue(result.success());
        assertEquals(config.validation().minAge(), result.data().getMinAge());
        assertEquals(config.validation().maxAge(), result.data().getMaxAge());
        assertEquals(config.matching().maxDistanceKm(), result.data().getMaxDistanceKm());
        assertTrue(result.data().getInterestedIn().contains(User.Gender.OTHER));
    }

    @Test
    @DisplayName("getAchievements and getOrComputeStats should return successful snapshots")
    void achievementsAndStatsQueriesSucceed() {
        User user = TestUserFactory.createActiveUser(UUID.randomUUID(), "Stats User");
        userStorage.save(user);

        var achievements = useCases.getAchievements(new AchievementsQuery(UserContext.cli(user.getId()), true));
        var stats = useCases.getOrComputeStats(new StatsQuery(UserContext.cli(user.getId())));

        assertTrue(achievements.success());
        assertTrue(stats.success());
        assertNotNull(achievements.data());
        assertNotNull(stats.data());
    }
}
