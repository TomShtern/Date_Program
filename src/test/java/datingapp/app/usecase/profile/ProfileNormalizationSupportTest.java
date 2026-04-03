package datingapp.app.usecase.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileNormalizationSupport")
class ProfileNormalizationSupportTest {

    @Test
    @DisplayName("normalize discovery preferences clamps and swaps values into configured bounds")
    void normalizeDiscoveryPreferencesClampsAndSwapsValuesIntoConfiguredBounds() {
        AppConfig config =
                AppConfig.builder().minAge(18).maxAge(60).maxDistanceKm(120).build();

        var normalized = ProfileNormalizationSupport.normalizeDiscoveryPreferences(config, 70, 16, 999);

        assertEquals(18, normalized.minAge());
        assertEquals(60, normalized.maxAge());
        assertEquals(120, normalized.maxDistanceKm());
    }

    @Test
    @DisplayName("normalize discovery preferences floors distance to one kilometer")
    void normalizeDiscoveryPreferencesFloorsDistanceToOneKilometer() {
        AppConfig config =
                AppConfig.builder().minAge(18).maxAge(60).maxDistanceKm(120).build();

        var normalized = ProfileNormalizationSupport.normalizeDiscoveryPreferences(config, 30, 35, 0);

        assertEquals(30, normalized.minAge());
        assertEquals(35, normalized.maxAge());
        assertEquals(1, normalized.maxDistanceKm());
    }

    @Test
    @DisplayName("apply minimal bootstrap sets the same honest incomplete defaults")
    void applyMinimalBootstrapSetsTheSameHonestIncompleteDefaults() {
        AppConfig config = AppConfig.defaults();
        User user = new User(java.util.UUID.randomUUID(), "Bootstrap User");

        ProfileNormalizationSupport.applyMinimalBootstrap(user, config, 25, Gender.OTHER, Gender.FEMALE);

        assertEquals(AppClock.today().minusYears(25), user.getBirthDate());
        assertEquals(Gender.OTHER, user.getGender());
        assertEquals(java.util.Set.of(Gender.FEMALE), user.getInterestedIn());
        assertEquals("", user.getBio());
        assertEquals(List.of(), user.getPhotoUrls());
        assertNull(user.getPacePreferences());
        assertEquals(Dealbreakers.none(), user.getDealbreakers());
        assertTrue(user.getMinAge() <= 25);
        assertTrue(user.getMaxAge() >= 25);
    }

    @Test
    @DisplayName("bootstrap age range is centered around the requested age within config bounds")
    void bootstrapAgeRangeIsCenteredAroundRequestedAgeWithinConfigBounds() {
        AppConfig config = AppConfig.builder().minAge(18).maxAge(30).build();
        User user = new User(java.util.UUID.randomUUID(), "Range User");

        ProfileNormalizationSupport.applyMinimalBootstrap(user, config, 29, Gender.MALE, Gender.OTHER);

        assertEquals(24, user.getMinAge());
        assertEquals(30, user.getMaxAge());
    }
}
