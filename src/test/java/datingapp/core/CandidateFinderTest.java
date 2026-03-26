package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.CandidateFinder.GeoUtils;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for CandidateFinder. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class CandidateFinderTest {

    private CandidateFinder finder;
    private CountingUsers userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private User seeker;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");
    private static final AppConfig CONFIG = AppConfig.defaults();
    private static final ZoneId ZONE = ZoneId.of("UTC");

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        userStorage = new CountingUsers();
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();

        finder = new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, ZONE);
        seeker = createUser("Seeker", Gender.MALE, EnumSet.of(Gender.FEMALE), 30, 32.0853, 34.7818);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("Excludes self from candidates")
    void excludesSelf() {
        List<User> candidates = finder.findCandidates(seeker, List.of(seeker), Set.of());
        assertTrue(candidates.isEmpty());
    }

    @Test
    @DisplayName("Excludes already interacted users")
    void excludesAlreadyInteracted() {
        User candidate = createUser("Candidate", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.0, 34.0);

        List<User> result = finder.findCandidates(seeker, List.of(candidate), Set.of(candidate.getId()));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Filters by mutual gender preferences")
    void filtersByMutualGenderPreferences() {
        // Interested in seeker's gender
        User compatible = createUser("Compatible", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.0, 34.0);
        // NOT interested in seeker's gender
        User incompatible = createUser("Incompatible", Gender.FEMALE, EnumSet.of(Gender.FEMALE), 28, 32.0, 34.0);

        List<User> result = finder.findCandidates(seeker, List.of(compatible, incompatible), Set.of());

        assertEquals(1, result.size());
        assertEquals("Compatible", result.get(0).getName());
    }

    @Test
    @DisplayName("Filters by mutual age preferences")
    void filtersByMutualAgePreferences() {
        // Seeker is 30, looking for 25-35
        // Compatible is 28, looking for 25-35 (seeker fits)
        User compatible = createUser("Compatible", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.0, 34.0);
        compatible.setAgeRange(
                25, 35, CONFIG.validation().minAge(), CONFIG.validation().maxAge());

        // TooOld is 28, but only looking for 18-25 (seeker doesn't fit)
        User tooOld = createUser("TooOld", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.0, 34.0);
        tooOld.setAgeRange(
                18, 25, CONFIG.validation().minAge(), CONFIG.validation().maxAge());

        List<User> result = finder.findCandidates(seeker, List.of(compatible, tooOld), Set.of());

        assertEquals(1, result.size());
        assertEquals("Compatible", result.get(0).getName());
    }

    @Test
    @DisplayName("Filters by distance preference")
    void filtersByDistance() {
        // Close candidate - same city
        User close = createUser("Close", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.1, 34.8);
        // Far candidate - different city (~60km away)
        User far = createUser("Far", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 31.7683, 35.2137);

        seeker.setMaxDistanceKm(30, CONFIG.matching().maxDistanceKm()); // Only 30km radius

        List<User> result = finder.findCandidates(seeker, List.of(close, far), Set.of());

        assertEquals(1, result.size());
        assertEquals("Close", result.get(0).getName());
    }

    @Test
    @DisplayName("Sorts candidates by distance ascending")
    void sortsByDistanceAscending() {
        User far = createUser("Far", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.5, 35.0);
        User close = createUser("Close", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.1, 34.8);
        User medium = createUser("Medium", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.3, 34.9);

        seeker.setMaxDistanceKm(200, CONFIG.matching().maxDistanceKm());

        List<User> result = finder.findCandidates(seeker, List.of(far, close, medium), Set.of());

        assertEquals(3, result.size());
        assertEquals("Close", result.get(0).getName());
        assertEquals("Medium", result.get(1).getName());
        assertEquals("Far", result.get(2).getName());
    }

    @Test
    @DisplayName("Treats (0,0) as a valid location when explicitly set")
    void treatsZeroZeroAsValidLocationWhenSet() {
        User zeroSeeker = createUser("ZeroSeeker", Gender.MALE, EnumSet.of(Gender.FEMALE), 30, 0.0, 0.0);
        zeroSeeker.setMaxDistanceKm(1, CONFIG.matching().maxDistanceKm());

        User farCandidate = createUser("FarAway", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 10.0, 10.0);

        List<User> result = finder.findCandidates(zeroSeeker, List.of(farCandidate), Set.of());

        assertTrue(result.isEmpty(), "Distance filtering should apply when (0,0) is explicitly set");
    }

    @Test
    @DisplayName("findCandidatesForUser returns empty when seeker location is missing")
    void findCandidatesForUserReturnsEmptyWhenSeekerLocationMissing() {
        User noLocationSeeker = createUserWithoutLocation("NoLocation", Gender.MALE, EnumSet.of(Gender.FEMALE), 30);

        List<User> result = finder.findCandidatesForUser(noLocationSeeker);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findCandidatesForUser caches by seeker and invalidates on demand")
    void findCandidatesForUserCachesAndInvalidates() {
        User cachedSeeker = createUser("CachedSeeker", Gender.MALE, EnumSet.of(Gender.FEMALE), 30, 32.0853, 34.7818);
        User candidate = createUser("Candidate", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.1, 34.8);
        userStorage.save(cachedSeeker);
        userStorage.save(candidate);

        finder.findCandidatesForUser(cachedSeeker);
        finder.findCandidatesForUser(cachedSeeker);
        assertEquals(1, userStorage.findCandidatesCallCount());

        finder.invalidateCacheFor(cachedSeeker.getId());
        finder.findCandidatesForUser(cachedSeeker);
        assertEquals(2, userStorage.findCandidatesCallCount());
    }

    @Test
    @DisplayName("findCandidatesForUser cache fingerprint includes pace/lifestyle/dealbreakers/interests")
    void findCandidatesForUserCacheFingerprintIncludesPreferenceState() {
        User cachedSeeker = createUser("CachedSeeker", Gender.MALE, EnumSet.of(Gender.FEMALE), 30, 32.0853, 34.7818);
        User candidate = createUser("Candidate", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.1, 34.8);
        userStorage.save(cachedSeeker);
        userStorage.save(candidate);

        finder.findCandidatesForUser(cachedSeeker);
        finder.findCandidatesForUser(cachedSeeker);
        assertEquals(
                1, userStorage.findCandidatesCallCount(), "second call should use cache before preference changes");

        cachedSeeker.setDealbreakers(
                Dealbreakers.builder().acceptSmoking(Lifestyle.Smoking.NEVER).build());
        cachedSeeker.setPacePreferences(new PacePreferences(
                MessagingFrequency.RARELY,
                TimeToFirstDate.WEEKS,
                CommunicationStyle.MIX_OF_EVERYTHING,
                DepthPreference.SMALL_TALK));
        cachedSeeker.setInterests(EnumSet.of(datingapp.core.profile.MatchPreferences.Interest.TRAVEL));

        finder.findCandidatesForUser(cachedSeeker);
        assertEquals(
                2,
                userStorage.findCandidatesCallCount(),
                "preference changes must invalidate fingerprint-equivalent cache entries");
    }

    // =========================================================================
    // Tests for "interested in everyone" (all genders selected) matching
    // =========================================================================

    @Test
    @DisplayName("Seeker open to everyone matches MALE, FEMALE, and OTHER candidates")
    void interestedInEveryone_seekerMatchesCandidatesOfAnyGender() {
        // A seeker who has selected all three genders is "open to everyone".
        User everyoneSeeker = createUser(
                "EveryoneSeeker",
                Gender.FEMALE,
                EnumSet.allOf(Gender.class), // MALE + FEMALE + OTHER
                30,
                32.09,
                34.79);

        // Three candidates of different genders — each interested in FEMALE,
        // so the reverse-direction check (candidate → seeker) also passes.
        User maleCand = createUser("MaleCandidate", Gender.MALE, EnumSet.of(Gender.FEMALE), 30, 32.09, 34.79);
        User femaleCand = createUser("FemaleCandidate", Gender.FEMALE, EnumSet.of(Gender.FEMALE), 30, 32.09, 34.79);
        User otherCand = createUser("OtherCandidate", Gender.OTHER, EnumSet.of(Gender.FEMALE), 30, 32.09, 34.79);

        List<User> result = finder.findCandidates(everyoneSeeker, List.of(maleCand, femaleCand, otherCand), Set.of());

        // All three should be returned — gender preference is fully open on the seeker
        // side.
        assertEquals(3, result.size(), "Seeker open to everyone should match all three genders");
    }

    @Test
    @DisplayName("Candidate open to everyone matches any seeker gender")
    void interestedInEveryone_candidateMatchesAnySeeker() {
        // MALE seeker who only likes FEMALE. // The candidate (FEMALE, open to
        // everyone) should still appear because
        // the candidate's side of the bidirectional check passes for MALE seekers.
        User maleSeeker = createUser("MaleSeeker", Gender.MALE, EnumSet.of(Gender.FEMALE), 30, 32.09, 34.79);

        // FEMALE candidate who is open to everyone (all genders).
        User everyoneCand =
                createUser("EveryoneCandidate", Gender.FEMALE, EnumSet.allOf(Gender.class), 28, 32.09, 34.79);

        List<User> result = finder.findCandidates(maleSeeker, List.of(everyoneCand), Set.of());

        assertEquals(1, result.size(), "Candidate open to everyone should match a MALE seeker");
    }

    @Test
    @DisplayName("Both seeker and candidate open to everyone match each other")
    void interestedInEveryone_mutualMatchWhenBothAreOpen() {
        User seekerAll = createUser("SeekerAll", Gender.OTHER, EnumSet.allOf(Gender.class), 30, 32.09, 34.79);

        User candidateAll = createUser("CandidateAll", Gender.MALE, EnumSet.allOf(Gender.class), 28, 32.09, 34.79);

        List<User> result = finder.findCandidates(seekerAll, List.of(candidateAll), Set.of());

        assertEquals(1, result.size(), "When both users are open to everyone, they should mutually match");
    }

    private User createUser(String name, Gender gender, Set<Gender> interestedIn, int age, double lat, double lon) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Bio");
        user.setBirthDate(AppClock.today().minusYears(age));
        user.setGender(gender);
        user.setInterestedIn(interestedIn);
        user.setLocation(lat, lon);
        user.setMaxDistanceKm(100, CONFIG.matching().maxDistanceKm());
        user.setAgeRange(
                18, 60, CONFIG.validation().minAge(), CONFIG.validation().maxAge());
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    private User createUserWithoutLocation(String name, Gender gender, Set<Gender> interestedIn, int age) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Bio");
        user.setBirthDate(AppClock.today().minusYears(age));
        user.setGender(gender);
        user.setInterestedIn(interestedIn);
        user.setMaxDistanceKm(100, CONFIG.matching().maxDistanceKm());
        user.setAgeRange(
                18, 60, CONFIG.validation().minAge(), CONFIG.validation().maxAge());
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    /** Tests for the GeoUtils Haversine distance calculation. */
    @Nested
    @DisplayName("GeoUtils.distanceKm")
    @SuppressWarnings("unused")
    class GeoUtilsDistanceTests {

        @Test
        @DisplayName("Same point returns zero distance")
        void samePointReturnsZero() {
            double distance = GeoUtils.distanceKm(32.0853, 34.7818, 32.0853, 34.7818);
            assertEquals(0.0, distance, 0.001);
        }

        @Test
        @DisplayName("Known distance - Tel Aviv to Jerusalem (~54km)")
        void telAvivToJerusalem() {
            // Tel Aviv: 32.0853, 34.7818
            // Jerusalem: 31.7683, 35.2137
            double distance = GeoUtils.distanceKm(32.0853, 34.7818, 31.7683, 35.2137);
            // Expected ~54km (actual distance varies based on exact coordinates)
            assertEquals(54.0, distance, 3.0); // Allow 3km tolerance
        }

        @Test
        @DisplayName("Known distance - New York to Los Angeles (~3940km)")
        void newYorkToLosAngeles() {
            // New York: 40.7128, -74.0060
            // Los Angeles: 34.0522, -118.2437
            double distance = GeoUtils.distanceKm(40.7128, -74.0060, 34.0522, -118.2437);
            assertEquals(3940, distance, 50); // Allow 50km tolerance
        }

        @Test
        @DisplayName("Distance is symmetric (A to B equals B to A)")
        void distanceIsSymmetric() {
            double ab = GeoUtils.distanceKm(32.0853, 34.7818, 31.7683, 35.2137);
            double ba = GeoUtils.distanceKm(31.7683, 35.2137, 32.0853, 34.7818);
            assertEquals(ab, ba, 0.001);
        }

        @Test
        @DisplayName("Antipodal points near maximum distance (~20015km)")
        void antipodalPoints() {
            // North pole to south pole (along same longitude)
            double distance = GeoUtils.distanceKm(90.0, 0.0, -90.0, 0.0);
            // Half Earth's circumference ≈ 20015 km
            assertEquals(20015, distance, 50);
        }

        @Test
        @DisplayName("Cross equator distance")
        void crossEquatorDistance() {
            // Singapore (just north of equator) to Jakarta (just south of equator)
            // Singapore: 1.3521, 103.8198
            // Jakarta: -6.2088, 106.8456
            double distance = GeoUtils.distanceKm(1.3521, 103.8198, -6.2088, 106.8456);
            // Expected ~890km
            assertEquals(890, distance, 30);
        }

        @Test
        @DisplayName("Cross dateline distance")
        void crossDatelineDistance() {
            // Tokyo, Japan: 35.6762, 139.6503
            // Los Angeles: 34.0522, -118.2437
            double distance = GeoUtils.distanceKm(35.6762, 139.6503, 34.0522, -118.2437);
            // Expected ~8815km (great circle distance)
            assertEquals(8815, distance, 100);
        }

        @Test
        @DisplayName("Very short distance (100 meters)")
        void veryShortDistance() {
            // Two points ~100m apart in Tel Aviv
            // 1 degree latitude ≈ 111km, so 0.001 degrees ≈ 111m
            double distance = GeoUtils.distanceKm(32.0853, 34.7818, 32.0862, 34.7818);
            assertEquals(0.1, distance, 0.02); // 100m ± 20m
        }
    }

    private static final class CountingUsers extends TestStorages.Users {
        private int findCandidatesCallCount;

        @Override
        public List<User> findCandidates(
                UUID excludeId,
                Set<Gender> genders,
                int minAge,
                int maxAge,
                double seekerLat,
                double seekerLon,
                int maxDistanceKm) {
            findCandidatesCallCount++;
            return super.findCandidates(excludeId, genders, minAge, maxAge, seekerLat, seekerLon, maxDistanceKm);
        }

        int findCandidatesCallCount() {
            return findCandidatesCallCount;
        }
    }
}
