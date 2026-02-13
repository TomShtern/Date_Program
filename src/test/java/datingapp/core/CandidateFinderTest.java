package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.*;
import datingapp.core.matching.*;
import datingapp.core.matching.CandidateFinder.GeoUtils;
import datingapp.core.metrics.*;
import datingapp.core.model.*;
import datingapp.core.profile.*;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.recommendation.*;
import datingapp.core.safety.*;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/** Unit tests for CandidateFinder. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class CandidateFinderTest {

    private CandidateFinder finder;
    private User seeker;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        var userStorage = new TestStorages.Users();
        var interactionStorage = new TestStorages.Interactions();
        var trustSafetyStorage = new TestStorages.TrustSafety();

        finder = new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, AppConfig.defaults());
        seeker = createUser("Seeker", User.Gender.MALE, EnumSet.of(User.Gender.FEMALE), 30, 32.0853, 34.7818);
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
        User candidate = createUser("Candidate", User.Gender.FEMALE, EnumSet.of(User.Gender.MALE), 28, 32.0, 34.0);

        List<User> result = finder.findCandidates(seeker, List.of(candidate), Set.of(candidate.getId()));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Filters by mutual gender preferences")
    void filtersByMutualGenderPreferences() {
        // Interested in seeker's gender
        User compatible = createUser("Compatible", User.Gender.FEMALE, EnumSet.of(User.Gender.MALE), 28, 32.0, 34.0);
        // NOT interested in seeker's gender
        User incompatible =
                createUser("Incompatible", User.Gender.FEMALE, EnumSet.of(User.Gender.FEMALE), 28, 32.0, 34.0);

        List<User> result = finder.findCandidates(seeker, List.of(compatible, incompatible), Set.of());

        assertEquals(1, result.size());
        assertEquals("Compatible", result.get(0).getName());
    }

    @Test
    @DisplayName("Filters by mutual age preferences")
    void filtersByMutualAgePreferences() {
        // Seeker is 30, looking for 25-35
        // Compatible is 28, looking for 25-35 (seeker fits)
        User compatible = createUser("Compatible", User.Gender.FEMALE, EnumSet.of(User.Gender.MALE), 28, 32.0, 34.0);
        compatible.setAgeRange(25, 35);

        // TooOld is 28, but only looking for 18-25 (seeker doesn't fit)
        User tooOld = createUser("TooOld", User.Gender.FEMALE, EnumSet.of(User.Gender.MALE), 28, 32.0, 34.0);
        tooOld.setAgeRange(18, 25);

        List<User> result = finder.findCandidates(seeker, List.of(compatible, tooOld), Set.of());

        assertEquals(1, result.size());
        assertEquals("Compatible", result.get(0).getName());
    }

    @Test
    @DisplayName("Filters by distance preference")
    void filtersByDistance() {
        // Close candidate - same city
        User close = createUser("Close", User.Gender.FEMALE, EnumSet.of(User.Gender.MALE), 28, 32.1, 34.8);
        // Far candidate - different city (~60km away)
        User far = createUser("Far", User.Gender.FEMALE, EnumSet.of(User.Gender.MALE), 28, 31.7683, 35.2137);

        seeker.setMaxDistanceKm(30); // Only 30km radius

        List<User> result = finder.findCandidates(seeker, List.of(close, far), Set.of());

        assertEquals(1, result.size());
        assertEquals("Close", result.get(0).getName());
    }

    @Test
    @DisplayName("Sorts candidates by distance ascending")
    void sortsByDistanceAscending() {
        User far = createUser("Far", User.Gender.FEMALE, EnumSet.of(User.Gender.MALE), 28, 32.5, 35.0);
        User close = createUser("Close", User.Gender.FEMALE, EnumSet.of(User.Gender.MALE), 28, 32.1, 34.8);
        User medium = createUser("Medium", User.Gender.FEMALE, EnumSet.of(User.Gender.MALE), 28, 32.3, 34.9);

        seeker.setMaxDistanceKm(200);

        List<User> result = finder.findCandidates(seeker, List.of(far, close, medium), Set.of());

        assertEquals(3, result.size());
        assertEquals("Close", result.get(0).getName());
        assertEquals("Medium", result.get(1).getName());
        assertEquals("Far", result.get(2).getName());
    }

    @Test
    @DisplayName("Treats (0,0) as a valid location when explicitly set")
    void treatsZeroZeroAsValidLocationWhenSet() {
        User zeroSeeker = createUser("ZeroSeeker", User.Gender.MALE, EnumSet.of(User.Gender.FEMALE), 30, 0.0, 0.0);
        zeroSeeker.setMaxDistanceKm(1);

        User farCandidate = createUser("FarAway", User.Gender.FEMALE, EnumSet.of(User.Gender.MALE), 28, 10.0, 10.0);

        List<User> result = finder.findCandidates(zeroSeeker, List.of(farCandidate), Set.of());

        assertTrue(result.isEmpty(), "Distance filtering should apply when (0,0) is explicitly set");
    }

    private User createUser(
            String name, User.Gender gender, Set<User.Gender> interestedIn, int age, double lat, double lon) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Bio");
        user.setBirthDate(AppClock.today().minusYears(age));
        user.setGender(gender);
        user.setInterestedIn(interestedIn);
        user.setLocation(lat, lon);
        user.setMaxDistanceKm(100);
        user.setAgeRange(18, 60);
        user.addPhotoUrl("photo.jpg");
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
}
