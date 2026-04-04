package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestStorages;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultBrowseRankingService")
class DefaultBrowseRankingServiceTest {

    @Test
    @DisplayName("ranks stronger compatibility above simple proximity")
    void ranksStrongerCompatibilityAboveSimpleProximity() {
        AppConfig config = AppConfig.defaults();
        DefaultBrowseRankingService rankingService = new DefaultBrowseRankingService(
                new DefaultCompatibilityCalculator(config), new ProfileService(new TestStorages.Users()), config);

        User seeker = createBrowseUser(
                "Seeker",
                32.0853,
                34.7818,
                EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.MUSIC),
                Lifestyle.Smoking.NEVER,
                Lifestyle.Drinking.SOCIALLY,
                Lifestyle.WantsKids.SOMEDAY,
                Lifestyle.LookingFor.LONG_TERM,
                AppClock.now());

        User nearWeakCandidate = createSparseBrowseCandidate(
                "Near Weak", 32.0854, 34.7819, AppClock.now().minus(Duration.ofDays(45)));
        User farStrongCandidate = createBrowseUser(
                "Far Strong",
                32.4500,
                34.9500,
                EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.MUSIC),
                Lifestyle.Smoking.NEVER,
                Lifestyle.Drinking.SOCIALLY,
                Lifestyle.WantsKids.SOMEDAY,
                Lifestyle.LookingFor.LONG_TERM,
                AppClock.now());

        List<User> ranked = rankingService.rankCandidates(seeker, List.of(nearWeakCandidate, farStrongCandidate));

        assertEquals(List.of(farStrongCandidate, nearWeakCandidate), ranked);
    }

    private static User createBrowseUser(
            String name,
            double lat,
            double lon,
            Set<Interest> interests,
            Lifestyle.Smoking smoking,
            Lifestyle.Drinking drinking,
            Lifestyle.WantsKids wantsKids,
            Lifestyle.LookingFor lookingFor,
            java.time.Instant updatedAt) {
        return User.StorageBuilder.create(UUID.randomUUID(), name, AppClock.now())
                .state(User.UserState.ACTIVE)
                .bio("Bio for " + name)
                .birthDate(AppClock.today().minusYears(28))
                .gender(User.Gender.FEMALE)
                .interestedIn(Set.of(User.Gender.MALE))
                .location(lat, lon)
                .hasLocationSet(true)
                .maxDistanceKm(500)
                .ageRange(18, 99)
                .photoUrls(
                        List.of("http://example.com/" + name.replace(' ', '-').toLowerCase() + ".jpg"))
                .interests(interests)
                .smoking(smoking)
                .drinking(drinking)
                .wantsKids(wantsKids)
                .lookingFor(lookingFor)
                .pacePreferences(new PacePreferences(
                        PacePreferences.MessagingFrequency.OFTEN,
                        PacePreferences.TimeToFirstDate.FEW_DAYS,
                        PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                        PacePreferences.DepthPreference.DEEP_CHAT))
                .updatedAt(updatedAt)
                .build();
    }

    private static User createSparseBrowseCandidate(String name, double lat, double lon, java.time.Instant updatedAt) {
        return User.StorageBuilder.create(UUID.randomUUID(), name, AppClock.now())
                .state(User.UserState.ACTIVE)
                .bio("")
                .birthDate(AppClock.today().minusYears(28))
                .gender(User.Gender.FEMALE)
                .interestedIn(Set.of(User.Gender.MALE))
                .location(lat, lon)
                .hasLocationSet(true)
                .maxDistanceKm(500)
                .ageRange(18, 99)
                .photoUrls(List.of())
                .interests(Set.of())
                .updatedAt(updatedAt)
                .build();
    }
}
