package datingapp.app.usecase.profile;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import java.util.EnumSet;
import java.util.Objects;

/** Shared normalization and bootstrap defaults for profile-related flows. */
public final class ProfileNormalizationSupport {

    private static final int MIN_DISTANCE_KM = 1;
    private static final int MIN_BOOTSTRAP_DISTANCE_KM = 50;
    private static final int BOOTSTRAP_AGE_RANGE_PADDING = 5;

    private ProfileNormalizationSupport() {}

    public static DiscoveryPreferences normalizeDiscoveryPreferences(
            AppConfig config, int minAge, int maxAge, int maxDistanceKm) {
        Objects.requireNonNull(config, "config cannot be null");

        int normalizedMinAge = Math.clamp(
                minAge, config.validation().minAge(), config.validation().maxAge());
        int normalizedMaxAge = Math.clamp(
                maxAge, config.validation().minAge(), config.validation().maxAge());
        if (normalizedMinAge > normalizedMaxAge) {
            int swap = normalizedMinAge;
            normalizedMinAge = normalizedMaxAge;
            normalizedMaxAge = swap;
        }

        int normalizedMaxDistance =
                Math.clamp(maxDistanceKm, MIN_DISTANCE_KM, config.matching().maxDistanceKm());
        return new DiscoveryPreferences(normalizedMinAge, normalizedMaxAge, normalizedMaxDistance);
    }

    public static void applyMinimalBootstrap(User user, AppConfig config, int age, Gender gender, Gender interestedIn) {
        Objects.requireNonNull(user, "user cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(gender, "gender cannot be null");
        Objects.requireNonNull(interestedIn, "interestedIn cannot be null");

        user.setBirthDate(AppClock.today().minusYears(age));
        user.setGender(gender);
        user.setInterestedIn(EnumSet.of(interestedIn));
        user.setBio("");

        DiscoveryPreferences discoveryPreferences = normalizeDiscoveryPreferences(
                config,
                age - BOOTSTRAP_AGE_RANGE_PADDING,
                age + BOOTSTRAP_AGE_RANGE_PADDING,
                MIN_BOOTSTRAP_DISTANCE_KM);
        user.setAgeRange(
                discoveryPreferences.minAge(),
                discoveryPreferences.maxAge(),
                config.validation().minAge(),
                config.validation().maxAge());
        user.setMaxDistanceKm(
                discoveryPreferences.maxDistanceKm(), config.matching().maxDistanceKm());
        user.setPhotoUrls(java.util.List.of());
        user.setPacePreferences(null);
        user.setDealbreakers(Dealbreakers.none());
    }

    public record DiscoveryPreferences(int minAge, int maxAge, int maxDistanceKm) {}
}
