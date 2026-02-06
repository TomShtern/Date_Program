package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.testutil.TestStorages;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for StandoutsService.
 */
@Timeout(5)
@SuppressWarnings("unused")
@DisplayName("StandoutsService")
class StandoutsServiceTest {

    private TestStorages.Users userStorage;
    private TestStandoutStorage standoutStorage;
    private CandidateFinder candidateFinder;
    private StandoutsService service;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        TestStorages.Likes likeStorage = new TestStorages.Likes();
        TestStorages.Blocks blockStorage = new TestStorages.Blocks();
        standoutStorage = new TestStandoutStorage();
        candidateFinder = new CandidateFinder(userStorage, likeStorage, blockStorage, AppConfig.defaults());
        service = new StandoutsService(userStorage, standoutStorage, candidateFinder, AppConfig.defaults());
    }

    /** Creates a fully complete user that can be activated. */
    private User createCompleteActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio for " + name);
        user.setBirthDate(LocalDate.now().minusYears(25)); // 25 years old
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.allOf(Gender.class));
        user.setLocation(32.0853, 34.7818); // Tel Aviv
        user.setMaxDistanceKm(50);
        user.setAgeRange(18, 99);
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.WILDCARD,
                PacePreferences.TimeToFirstDate.WILDCARD,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEPENDS_ON_VIBE));
        user.activate();
        return user;
    }

    /** Creates a user with specific gender preferences for matching tests. */
    private User createUserWithGender(String name, Gender myGender, Gender interestedIn) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio for " + name);
        user.setBirthDate(LocalDate.now().minusYears(25));
        user.setGender(myGender);
        user.setInterestedIn(EnumSet.of(interestedIn));
        user.setLocation(32.0853, 34.7818);
        user.setMaxDistanceKm(50);
        user.setAgeRange(18, 99);
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.WILDCARD,
                PacePreferences.TimeToFirstDate.WILDCARD,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEPENDS_ON_VIBE));
        user.activate();
        return user;
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should require non-null userStorage")
        void requiresUserStorage() {
            assertThrows(
                    NullPointerException.class,
                    () -> new StandoutsService(null, standoutStorage, candidateFinder, AppConfig.defaults()));
        }

        @Test
        @DisplayName("Should require non-null standoutStorage")
        void requiresStandoutStorage() {
            assertThrows(
                    NullPointerException.class,
                    () -> new StandoutsService(userStorage, null, candidateFinder, AppConfig.defaults()));
        }

        @Test
        @DisplayName("Should require non-null candidateFinder")
        void requiresCandidateFinder() {
            assertThrows(
                    NullPointerException.class,
                    () -> new StandoutsService(userStorage, standoutStorage, null, AppConfig.defaults()));
        }

        @Test
        @DisplayName("Should require non-null config")
        void requiresConfig() {
            assertThrows(
                    NullPointerException.class,
                    () -> new StandoutsService(userStorage, standoutStorage, candidateFinder, null));
        }
    }

    @Nested
    @DisplayName("getStandouts")
    class GetStandouts {

        @Test
        @DisplayName("Should return empty result when no candidates")
        void returnsEmptyWhenNoCandidates() {
            User seeker = createCompleteActiveUser("Seeker");
            userStorage.save(seeker);

            StandoutsService.Result result = service.getStandouts(seeker);

            assertTrue(result.isEmpty());
            assertEquals(0, result.count());
            assertNotNull(result.message());
        }

        @Test
        @DisplayName("Should return standouts from cache if available")
        void returnsCachedStandouts() {
            User seeker = createCompleteActiveUser("Seeker");
            userStorage.save(seeker);

            // Pre-cache some standouts
            LocalDate today = LocalDate.now();
            Standout cached = Standout.create(seeker.getId(), UUID.randomUUID(), today, 1, 90, "Test");
            standoutStorage.cachedStandouts.put(seeker.getId() + "_" + today, List.of(cached));

            StandoutsService.Result result = service.getStandouts(seeker);

            assertTrue(result.fromCache());
            assertEquals(1, result.count());
        }

        @Test
        @DisplayName("Should generate standouts when not cached")
        void generatesWhenNotCached() {
            User seeker = createUserWithGender("Seeker", Gender.FEMALE, Gender.MALE);
            User candidate1 = createUserWithGender("Bob", Gender.MALE, Gender.FEMALE);
            User candidate2 = createUserWithGender("Charlie", Gender.MALE, Gender.FEMALE);

            userStorage.save(seeker);
            userStorage.save(candidate1);
            userStorage.save(candidate2);

            StandoutsService.Result result = service.getStandouts(seeker);

            assertFalse(result.isEmpty());
            assertFalse(result.fromCache());
        }

        @Test
        @DisplayName("Should limit to 10 standouts")
        void limitsToTen() {
            User seeker = createUserWithGender("Seeker", Gender.FEMALE, Gender.MALE);
            userStorage.save(seeker);

            // Create 15 candidates
            for (int i = 0; i < 15; i++) {
                User candidate = createUserWithGender("Candidate" + i, Gender.MALE, Gender.FEMALE);
                userStorage.save(candidate);
            }

            StandoutsService.Result result = service.getStandouts(seeker);

            assertTrue(result.count() <= 10);
        }
    }

    @Nested
    @DisplayName("markInteracted")
    class MarkInteracted {

        @Test
        @DisplayName("Should mark standout as interacted")
        void marksAsInteracted() {
            UUID seekerId = UUID.randomUUID();
            UUID standoutId = UUID.randomUUID();

            service.markInteracted(seekerId, standoutId);

            assertTrue(standoutStorage.interactedPairs.contains(seekerId + "_" + standoutId));
        }
    }

    @Nested
    @DisplayName("resolveUsers")
    class ResolveUsers {

        @Test
        @DisplayName("Should resolve standout IDs to users")
        void resolvesStandouts() {
            User user1 = createCompleteActiveUser("User1");
            User user2 = createCompleteActiveUser("User2");
            userStorage.save(user1);
            userStorage.save(user2);

            List<Standout> standouts = List.of(
                    Standout.create(UUID.randomUUID(), user1.getId(), LocalDate.now(), 1, 90, "Test1"),
                    Standout.create(UUID.randomUUID(), user2.getId(), LocalDate.now(), 2, 85, "Test2"));

            var resolved = service.resolveUsers(standouts);

            assertEquals(2, resolved.size());
            assertEquals(user1, resolved.get(user1.getId()));
            assertEquals(user2, resolved.get(user2.getId()));
        }

        @Test
        @DisplayName("Should skip missing users")
        void skipsMissingUsers() {
            User user1 = createCompleteActiveUser("User1");
            userStorage.save(user1);

            UUID missingId = UUID.randomUUID();
            List<Standout> standouts = List.of(
                    Standout.create(UUID.randomUUID(), user1.getId(), LocalDate.now(), 1, 90, "Test1"),
                    Standout.create(UUID.randomUUID(), missingId, LocalDate.now(), 2, 85, "Test2"));

            var resolved = service.resolveUsers(standouts);

            assertEquals(1, resolved.size());
            assertNull(resolved.get(missingId));
        }
    }

    @Nested
    @DisplayName("Result record")
    class ResultTests {

        @Test
        @DisplayName("empty() should create empty result with message")
        void emptyCreatesWithMessage() {
            StandoutsService.Result result = StandoutsService.Result.empty("Test message");

            assertTrue(result.isEmpty());
            assertEquals(0, result.count());
            assertEquals("Test message", result.message());
            assertFalse(result.fromCache());
        }

        @Test
        @DisplayName("of() should create result with standouts")
        void ofCreatesWithStandouts() {
            List<Standout> standouts =
                    List.of(Standout.create(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), 1, 90, "Test"));

            StandoutsService.Result result = StandoutsService.Result.of(standouts, 5, true);

            assertFalse(result.isEmpty());
            assertEquals(1, result.count());
            assertEquals(5, result.totalCandidates());
            assertTrue(result.fromCache());
            assertNull(result.message());
        }
    }

    // === Test Doubles ===

    private static class TestStandoutStorage implements Standout.Storage {
        List<Standout> savedStandouts = new ArrayList<>();
        Map<String, List<Standout>> cachedStandouts = new HashMap<>();
        Set<String> interactedPairs = new HashSet<>();

        @Override
        public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
            String key = seekerId + "_" + date;
            return cachedStandouts.getOrDefault(key, List.of());
        }

        @Override
        public void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date) {
            String key = seekerId + "_" + date;
            savedStandouts.addAll(standouts);
            cachedStandouts.put(key, new ArrayList<>(standouts));
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date) {
            interactedPairs.add(seekerId + "_" + standoutUserId);
        }

        @Override
        public int cleanup(LocalDate before) {
            // Not used in tests
            return 0;
        }
    }
}
