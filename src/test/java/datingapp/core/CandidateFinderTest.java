package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for CandidateFinder. */
class CandidateFinderTest {

    private CandidateFinder finder;
    private User seeker;

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        finder = new CandidateFinder();
        seeker = createUser("Seeker", User.Gender.MALE, EnumSet.of(User.Gender.FEMALE), 30, 32.0853, 34.7818);
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

    private User createUser(
            String name, User.Gender gender, Set<User.Gender> interestedIn, int age, double lat, double lon) {
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
}
