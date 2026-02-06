package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.CandidateFinder.GeoUtils;
import datingapp.core.PacePreferences.CommunicationStyle;
import datingapp.core.PacePreferences.DepthPreference;
import datingapp.core.PacePreferences.MessagingFrequency;
import datingapp.core.PacePreferences.TimeToFirstDate;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.UserStorage;
import java.time.LocalDate;
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

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        // CandidateFinder now requires storage dependencies, but tests only use findCandidates()
        // which doesn't touch the storage fields, so we provide minimal stubs
        UserStorage userStorage = new UserStorage() {
            @Override
            public void save(User user) {
                // no-op for test
            }

            @Override
            public User get(UUID id) {
                return null;
            }

            @Override
            public List<User> findAll() {
                return List.of();
            }

            @Override
            public List<User> findActive() {
                return List.of();
            }

            @Override
            public void delete(UUID id) {
                // no-op for test
            }

            @Override
            public void saveProfileNote(User.ProfileNote note) {
                // no-op for test
            }

            @Override
            public java.util.Optional<User.ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
                return java.util.Optional.empty();
            }

            @Override
            public List<User.ProfileNote> getProfileNotesByAuthor(UUID authorId) {
                return List.of();
            }

            @Override
            public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
                return false;
            }
        };
        LikeStorage likeStorage = new LikeStorage() {
            @Override
            public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
                return Set.of();
            }

            @Override
            public void save(UserInteractions.Like like) {
                // no-op for test
            }

            @Override
            public boolean exists(UUID from, UUID to) {
                return false;
            }

            @Override
            public boolean mutualLikeExists(UUID a, UUID b) {
                return false;
            }

            @Override
            public Set<UUID> getUserIdsWhoLiked(UUID userId) {
                return Set.of();
            }

            @Override
            public List<java.util.Map.Entry<UUID, java.time.Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
                return List.of();
            }

            @Override
            public int countByDirection(UUID userId, UserInteractions.Like.Direction direction) {
                return 0;
            }

            @Override
            public int countReceivedByDirection(UUID userId, UserInteractions.Like.Direction direction) {
                return 0;
            }

            @Override
            public java.util.Optional<UserInteractions.Like> getLike(UUID fromUserId, UUID toUserId) {
                return java.util.Optional.empty();
            }

            @Override
            public int countLikesToday(UUID userId, java.time.Instant startOfDay) {
                return 0;
            }

            @Override
            public int countPassesToday(UUID userId, java.time.Instant startOfDay) {
                return 0;
            }

            @Override
            public int countMutualLikes(UUID userId) {
                return 0;
            }

            @Override
            public void delete(UUID likeId) {
                // no-op for test
            }
        };
        BlockStorage blockStorage = new BlockStorage() {
            @Override
            public Set<UUID> getBlockedUserIds(UUID userId) {
                return Set.of();
            }

            @Override
            public void save(UserInteractions.Block block) {
                // no-op for test
            }

            @Override
            public boolean isBlocked(UUID userA, UUID userB) {
                return false;
            }

            @Override
            public List<UserInteractions.Block> findByBlocker(UUID blockerId) {
                return List.of();
            }

            @Override
            public boolean delete(UUID blockerId, UUID blockedId) {
                return false;
            }

            @Override
            public int countBlocksGiven(UUID userId) {
                return 0;
            }

            @Override
            public int countBlocksReceived(UUID userId) {
                return 0;
            }
        };

        finder = new CandidateFinder(userStorage, likeStorage, blockStorage, AppConfig.defaults());
        seeker = createUser("Seeker", Gender.MALE, EnumSet.of(Gender.FEMALE), 30, 32.0853, 34.7818);
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
        compatible.setAgeRange(25, 35);

        // TooOld is 28, but only looking for 18-25 (seeker doesn't fit)
        User tooOld = createUser("TooOld", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 32.0, 34.0);
        tooOld.setAgeRange(18, 25);

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

        seeker.setMaxDistanceKm(30); // Only 30km radius

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
        User zeroSeeker = createUser("ZeroSeeker", Gender.MALE, EnumSet.of(Gender.FEMALE), 30, 0.0, 0.0);
        zeroSeeker.setMaxDistanceKm(1);

        User farCandidate = createUser("FarAway", Gender.FEMALE, EnumSet.of(Gender.MALE), 28, 10.0, 10.0);

        List<User> result = finder.findCandidates(zeroSeeker, List.of(farCandidate), Set.of());

        assertTrue(result.isEmpty(), "Distance filtering should apply when (0,0) is explicitly set");
    }

    private User createUser(String name, Gender gender, Set<Gender> interestedIn, int age, double lat, double lon) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Bio");
        user.setBirthDate(LocalDate.now().minusYears(age));
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
