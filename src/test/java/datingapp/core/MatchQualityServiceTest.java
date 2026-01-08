package datingapp.core;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for MatchQualityService.
 */
@DisplayName("MatchQualityService Tests")
class MatchQualityServiceTest {

    private MatchQualityService service;
    private TestUserStorage userStorage;
    private TestLikeStorage likeStorage;

    @BeforeEach
    void setUp() {
        userStorage = new TestUserStorage();
        likeStorage = new TestLikeStorage();
        service = new MatchQualityService(userStorage, likeStorage);
    }

    @Nested
    @DisplayName("Score Calculations")
    class ScoreCalculationTests {

        @Test
        @DisplayName("Close distance gives high score")
        void closeDistanceGivesHighScore() {
            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.01, 34.01); // ~1.4km
            alice.setMaxDistanceKm(50);
            bob.setMaxDistanceKm(50);

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            assertTrue(quality.distanceScore() > 0.9);
            assertTrue(quality.distanceKm() < 5);
        }

        @Test
        @DisplayName("Same age gives perfect age score")
        void sameAgeGivesPerfectScore() {
            User alice = createUser("Alice", 28, 32.0, 34.0);
            User bob = createUser("Bob", 28, 32.0, 34.0);

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            assertEquals(1.0, quality.ageScore());
            assertEquals(0, quality.ageDifference());
        }

        @Test
        @DisplayName("Matching lifestyle gives high lifestyle score")
        void matchingLifestyleGivesHighScore() {
            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.0, 34.0);

            alice.setSmoking(Lifestyle.Smoking.NEVER);
            alice.setDrinking(Lifestyle.Drinking.SOCIALLY);
            alice.setWantsKids(Lifestyle.WantsKids.SOMEDAY);
            alice.setLookingFor(Lifestyle.LookingFor.LONG_TERM);

            bob.setSmoking(Lifestyle.Smoking.NEVER);
            bob.setDrinking(Lifestyle.Drinking.SOCIALLY);
            bob.setWantsKids(Lifestyle.WantsKids.SOMEDAY);
            bob.setLookingFor(Lifestyle.LookingFor.LONG_TERM);

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            assertEquals(1.0, quality.lifestyleScore());
        }

        @Test
        @DisplayName("Missing lifestyle data gives neutral score")
        void missingLifestyleGivesNeutralScore() {
            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.0, 34.0);
            // No lifestyle data set

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            assertEquals(0.5, quality.lifestyleScore());
        }
    }

    @Nested
    @DisplayName("Highlight Generation")
    class HighlightTests {

        @Test
        @DisplayName("Close distance generates highlight")
        void closeDistanceGeneratesHighlight() {
            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.01, 34.01); // Very close
            alice.setMaxDistanceKm(50);
            bob.setMaxDistanceKm(50);

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            assertTrue(quality.highlights().stream()
                    .anyMatch(h -> h.contains("km away") || h.contains("nearby")));
        }

        @Test
        @DisplayName("Same relationship goal generates highlight")
        void sameGoalGeneratesHighlight() {
            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.0, 34.0);
            alice.setLookingFor(Lifestyle.LookingFor.LONG_TERM);
            bob.setLookingFor(Lifestyle.LookingFor.LONG_TERM);

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            assertTrue(quality.lifestyleMatches().stream()
                    .anyMatch(h -> h.contains("long-term")));
        }

        @Test
        @DisplayName("Similar age generates highlight")
        void similarAgeGeneratesHighlight() {
            User alice = createUser("Alice", 28, 32.0, 34.0);
            User bob = createUser("Bob", 29, 32.0, 34.0); // 1 year diff

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            assertTrue(quality.highlights().stream()
                    .anyMatch(h -> h.contains("Similar age")));
        }
    }

    @Nested
    @DisplayName("Progress Bar Rendering")
    class ProgressBarTests {

        @Test
        @DisplayName("Full score renders all filled")
        void fullScoreRendersAllFilled() {
            String bar = MatchQualityService.renderProgressBar(1.0, 10);
            assertEquals("██████████", bar);
        }

        @Test
        @DisplayName("Empty score renders all empty")
        void emptyScoreRendersAllEmpty() {
            String bar = MatchQualityService.renderProgressBar(0.0, 10);
            assertEquals("░░░░░░░░░░", bar);
        }

        @Test
        @DisplayName("Half score renders half filled")
        void halfScoreRendersHalfFilled() {
            String bar = MatchQualityService.renderProgressBar(0.5, 10);
            assertEquals("█████░░░░░", bar);
        }
    }

    // === Helper Methods ===

    private User createUser(String name, int age, double lat, double lon) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(LocalDate.now().minusYears(age));
        user.setGender(User.Gender.OTHER);
        user.setInterestedIn(EnumSet.of(User.Gender.OTHER));
        user.setLocation(lat, lon);
        user.setMaxDistanceKm(50);
        user.setAgeRange(18, 60);
        user.addPhotoUrl("http://example.com/photo.jpg");
        user.setBio("Test bio");
        return user;
    }

    private void addMutualLikes(UUID userA, UUID userB) {
        Like likeA = Like.create(userA, userB, Like.Direction.LIKE);
        Like likeB = Like.create(userB, userA, Like.Direction.LIKE);
        likeStorage.save(likeA);
        likeStorage.save(likeB);
    }

    // === Test Doubles ===

    private static class TestUserStorage implements UserStorage {
        private final java.util.Map<UUID, User> users = new java.util.HashMap<>();

        @Override
        public void save(User user) {
            users.put(user.getId(), user);
        }

        @Override
        public User get(UUID id) {
            return users.get(id);
        }

        @Override
        public java.util.List<User> findAll() {
            return new java.util.ArrayList<>(users.values());
        }

        @Override
        public java.util.List<User> findActive() {
            return users.values().stream()
                    .filter(u -> u.getState() == User.State.ACTIVE)
                    .toList();
        }
    }

    private static class TestLikeStorage implements LikeStorage {
        private final java.util.Map<String, Like> likes = new java.util.HashMap<>();

        private static String key(UUID from, UUID to) {
            return from + "_" + to;
        }

        @Override
        public void save(Like like) {
            likes.put(key(like.whoLikes(), like.whoGotLiked()), like);
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return Optional.ofNullable(likes.get(key(fromUserId, toUserId)));
        }

        @Override
        public boolean exists(UUID from, UUID to) {
            return likes.containsKey(key(from, to));
        }

        @Override
        public boolean mutualLikeExists(UUID a, UUID b) {
            return likes.containsKey(key(a, b)) && likes.containsKey(key(b, a));
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            return Set.of();
        }

        @Override
        public int countByDirection(UUID userId, Like.Direction direction) {
            return 0;
        }

        @Override
        public int countReceivedByDirection(UUID userId, Like.Direction direction) {
            return 0;
        }

        @Override
        public int countMutualLikes(UUID userId) {
            return 0;
        }
    }
}
