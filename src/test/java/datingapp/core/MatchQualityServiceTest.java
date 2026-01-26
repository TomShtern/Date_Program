package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.MatchQualityService.InterestMatcher;
import datingapp.core.MatchQualityService.MatchQuality;
import datingapp.core.MatchQualityService.MatchQualityConfig;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.Preferences.PacePreferences.CommunicationStyle;
import datingapp.core.Preferences.PacePreferences.DepthPreference;
import datingapp.core.Preferences.PacePreferences.MessagingFrequency;
import datingapp.core.Preferences.PacePreferences.TimeToFirstDate;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.LikeStorage;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests for MatchQualityService. */
@DisplayName("MatchQualityService Tests")
@SuppressWarnings("unused") // IDE false positives for @Nested classes and @BeforeEach
@Timeout(value = 5, unit = TimeUnit.SECONDS)
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

        @Test
        @DisplayName("Identical interests give perfect interest score")
        void identicalInterestsGivePerfectScore() {
            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.0, 34.0);

            alice.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE));
            bob.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE));

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            assertEquals(1.0, quality.interestScore());
            assertTrue(quality.highlights().stream().anyMatch(h -> h.contains("You share 2 interests")));
        }

        @Test
        @DisplayName("Partial interest overlap calculates correctly")
        void partialInterestOverlapCalculatesCorrectly() {
            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.0, 34.0);

            alice.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL));
            bob.setInterests(EnumSet.of(Interest.HIKING, Interest.MOVIES, Interest.TRAVEL));

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            // 2 shared / 3 min = 0.666
            assertEquals(0.666, quality.interestScore(), 0.001);
            assertTrue(quality.highlights().stream().anyMatch(h -> h.contains("You share 2 interests")));
        }

        @Test
        @DisplayName("No shared interests gives zero score")
        void noSharedInterestsGivesZeroScore() {
            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.0, 34.0);

            alice.setInterests(EnumSet.of(Interest.HIKING));
            bob.setInterests(EnumSet.of(Interest.COOKING));

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            assertEquals(0.0, quality.interestScore());
        }

        @Test
        @DisplayName("One user missing interests gives penalty score")
        void oneUserMissingInterestsGivesPenalty() {
            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.0, 34.0);

            alice.setInterests(EnumSet.of(Interest.HIKING));
            // Bob has none

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId());

            assertEquals(0.3, quality.interestScore());
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

            assertTrue(quality.highlights().stream().anyMatch(h -> h.contains("km away") || h.contains("nearby")));
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

            assertTrue(quality.lifestyleMatches().stream().anyMatch(h -> h.contains("long-term")));
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

            assertTrue(quality.highlights().stream().anyMatch(h -> h.contains("Similar age")));
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

    // ==================== MATCH QUALITY CONFIG TESTS ====================

    @Nested
    @DisplayName("MatchQualityConfig Validation")
    class ConfigValidationTests {

        @Test
        @DisplayName("Default config weights sum to 1.0")
        void defaultConfigSumsToOne() {
            MatchQualityConfig config = MatchQualityConfig.defaults();

            double total = config.distanceWeight()
                    + config.ageWeight()
                    + config.interestWeight()
                    + config.lifestyleWeight()
                    + config.paceWeight()
                    + config.responseWeight();

            assertEquals(1.0, total, 0.001);
        }

        @Test
        @DisplayName("Proximity focused config weights sum to 1.0")
        void proximityFocusedSumsToOne() {
            MatchQualityConfig config = MatchQualityConfig.proximityFocused();

            double total = config.distanceWeight()
                    + config.ageWeight()
                    + config.interestWeight()
                    + config.lifestyleWeight()
                    + config.paceWeight()
                    + config.responseWeight();

            assertEquals(1.0, total, 0.001);
        }

        @Test
        @DisplayName("Lifestyle focused config weights sum to 1.0")
        void lifestyleFocusedSumsToOne() {
            MatchQualityConfig config = MatchQualityConfig.lifestyleFocused();

            double total = config.distanceWeight()
                    + config.ageWeight()
                    + config.interestWeight()
                    + config.lifestyleWeight()
                    + config.paceWeight()
                    + config.responseWeight();

            assertEquals(1.0, total, 0.001);
        }

        @Test
        @DisplayName("Throws if weights don't sum to 1.0")
        void throwsIfWeightsDontSumToOne() {
            assertThrows(IllegalArgumentException.class, () -> new MatchQualityConfig(0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        }

        @Test
        @DisplayName("Throws if any weight is negative")
        void throwsIfNegativeWeight() {
            assertThrows(IllegalArgumentException.class, () -> new MatchQualityConfig(-0.1, 0.3, 0.2, 0.2, 0.2, 0.2));
        }

        @Test
        @DisplayName("Allows custom valid weights")
        void allowsCustomValidWeights() {
            MatchQualityConfig config = new MatchQualityConfig(0.1, 0.1, 0.2, 0.2, 0.2, 0.2);

            assertEquals(0.1, config.distanceWeight());
            assertEquals(0.1, config.ageWeight());
            assertEquals(0.2, config.interestWeight());
            assertEquals(0.2, config.lifestyleWeight());
            assertEquals(0.2, config.paceWeight());
            assertEquals(0.2, config.responseWeight());
        }

        @Test
        @DisplayName("Allows zero weights if sum is 1.0")
        void allowsZeroWeights() {
            MatchQualityConfig config = new MatchQualityConfig(0.0, 0.0, 0.5, 0.5, 0.0, 0.0);

            assertEquals(0.0, config.distanceWeight());
            assertEquals(0.0, config.ageWeight());
            assertEquals(0.5, config.interestWeight());
            assertEquals(0.5, config.lifestyleWeight());
        }

        @Test
        @DisplayName("Allows all weight in one category")
        void allowsAllWeightInOneCategory() {
            MatchQualityConfig config = new MatchQualityConfig(1.0, 0.0, 0.0, 0.0, 0.0, 0.0);

            assertEquals(1.0, config.distanceWeight());
            assertEquals(0.0, config.ageWeight());
        }

        @Test
        @DisplayName("Throws if weights sum slightly below 1.0")
        void throwsIfWeightsSumBelowOne() {
            assertThrows(
                    IllegalArgumentException.class, () -> new MatchQualityConfig(0.15, 0.15, 0.15, 0.15, 0.15, 0.15));
        }

        @Test
        @DisplayName("Throws if weights sum slightly above 1.0")
        void throwsIfWeightsSumAboveOne() {
            assertThrows(IllegalArgumentException.class, () -> new MatchQualityConfig(0.2, 0.2, 0.2, 0.2, 0.2, 0.2));
        }

        @Test
        @DisplayName("Throws if any weight exceeds 1.0")
        void throwsIfWeightExceedsOne() {
            assertThrows(IllegalArgumentException.class, () -> new MatchQualityConfig(1.5, 0.0, 0.0, 0.0, 0.0, -0.5));
        }
    }

    // ==================== INTEREST MATCHER TESTS ====================

    @Nested
    @DisplayName("InterestMatcher - Compare")
    class InterestMatcherCompareTests {

        @Test
        @DisplayName("Identical sets returns overlap ratio 1.0")
        void identicalSets_returnsOverlapRatio1() {
            Set<Interest> setA = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL);
            Set<Interest> setB = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL);

            InterestMatcher.MatchResult result = InterestMatcher.compare(setA, setB);

            assertEquals(3, result.sharedCount());
            assertEquals(1.0, result.overlapRatio(), 0.001);
            assertEquals(1.0, result.jaccardIndex(), 0.001);
        }

        @Test
        @DisplayName("No overlap returns overlap ratio 0.0")
        void noOverlap_returnsOverlapRatio0() {
            Set<Interest> setA = EnumSet.of(Interest.HIKING, Interest.COFFEE);
            Set<Interest> setB = EnumSet.of(Interest.MOVIES, Interest.MUSIC);

            InterestMatcher.MatchResult result = InterestMatcher.compare(setA, setB);

            assertEquals(0, result.sharedCount());
            assertEquals(0.0, result.overlapRatio(), 0.001);
            assertEquals(0.0, result.jaccardIndex(), 0.001);
        }

        @Test
        @DisplayName("Partial overlap calculates correctly")
        void partialOverlap_calculatesCorrectly() {
            Set<Interest> setA = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL);
            Set<Interest> setB = EnumSet.of(Interest.HIKING, Interest.MOVIES, Interest.TRAVEL);

            InterestMatcher.MatchResult result = InterestMatcher.compare(setA, setB);

            assertEquals(2, result.sharedCount());
            // shared(2) / minSize(3) = 0.666
            assertEquals(0.666, result.overlapRatio(), 0.001);
            // shared(2) / union(4) = 0.5
            assertEquals(0.5, result.jaccardIndex(), 0.001);
        }

        @Test
        @DisplayName("Asymmetric sizes uses smallest denominator")
        void asymmetricSizes_usesSmallestDenominator() {
            Set<Interest> setA = EnumSet.of(Interest.HIKING, Interest.COFFEE);
            Set<Interest> setB =
                    EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL, Interest.MOVIES, Interest.COFFEE);

            InterestMatcher.MatchResult result = InterestMatcher.compare(setA, setB);

            assertEquals(2, result.sharedCount());
            // shared(2) / minSize(2) = 1.0
            assertEquals(1.0, result.overlapRatio(), 0.001);
            // shared(2) / union(4) = 0.5
            assertEquals(0.5, result.jaccardIndex(), 0.001);
        }

        @Test
        @DisplayName("Empty sets handles gracefully")
        void emptySets_handlesGracefully() {
            InterestMatcher.MatchResult result =
                    InterestMatcher.compare(EnumSet.noneOf(Interest.class), EnumSet.noneOf(Interest.class));
            assertEquals(0, result.sharedCount());
            assertEquals(0.0, result.overlapRatio());
        }

        @Test
        @DisplayName("Null inputs handles gracefully")
        void nullInputs_handlesGracefully() {
            InterestMatcher.MatchResult result = InterestMatcher.compare(null, null);
            assertNotNull(result);
            assertEquals(0, result.sharedCount());
        }
    }

    @Nested
    @DisplayName("InterestMatcher - Format Shared Interests")
    class InterestMatcherFormatTests {

        @Test
        @DisplayName("Single interest returns name")
        void singleInterest_returnsName() {
            Set<Interest> shared = EnumSet.of(Interest.HIKING);
            assertEquals("Hiking", InterestMatcher.formatSharedInterests(shared));
        }

        @Test
        @DisplayName("Two interests joins with 'and'")
        void twoInterests_joinsWithAnd() {
            Set<Interest> shared = EnumSet.of(Interest.HIKING, Interest.COFFEE);
            // Order depends on EnumSet, hiking is first
            String formatted = InterestMatcher.formatSharedInterests(shared);
            assertTrue(formatted.contains("Hiking") && formatted.contains("Coffee") && formatted.contains("and"));
        }

        @Test
        @DisplayName("Three interests joins with comma and 'and'")
        void threeInterests_joinsWithCommaAnd() {
            Set<Interest> shared = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL);
            String formatted = InterestMatcher.formatSharedInterests(shared);
            assertEquals("Hiking, Coffee, and Travel", formatted);
        }

        @Test
        @DisplayName("Four+ interests shows 'and X more'")
        void fourPlusInterests_showsAndXMore() {
            Set<Interest> shared = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL, Interest.MOVIES);
            String formatted = InterestMatcher.formatSharedInterests(shared);
            // EnumSet iterates by ordinal: HIKING(0), MOVIES(6), COFFEE(18), TRAVEL(31)
            assertEquals("Hiking, Movies, Coffee, and 1 more", formatted);
        }

        @Test
        @DisplayName("Empty set returns empty string")
        void emptySet_returnsEmptyString() {
            assertEquals("", InterestMatcher.formatSharedInterests(EnumSet.noneOf(Interest.class)));
        }

        @Test
        @DisplayName("Null set returns empty string")
        void nullSet_returnsEmptyString() {
            assertEquals("", InterestMatcher.formatSharedInterests(null));
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
        user.setPacePreferences(new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        return user;
    }

    private void addMutualLikes(UUID userA, UUID userB) {
        Like likeA = Like.create(userA, userB, Like.Direction.LIKE);
        Like likeB = Like.create(userB, userA, Like.Direction.LIKE);
        likeStorage.save(likeA);
        likeStorage.save(likeB);
    }

    // === Test Doubles ===

    private static class TestUserStorage implements User.Storage {
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

        @Override
        public void delete(UUID id) {
            users.remove(id);
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
        public Set<UUID> getUserIdsWhoLiked(UUID userId) {
            return Set.of();
        }

        @Override
        public java.util.Map<UUID, java.time.Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
            java.util.Map<UUID, java.time.Instant> result = new java.util.HashMap<>();
            for (Like like : likes.values()) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.put(like.whoLikes(), like.createdAt());
                }
            }
            return result;
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

        @Override
        public int countLikesToday(UUID userId, java.time.Instant startOfDay) {
            return 0;
        }

        @Override
        public int countPassesToday(UUID userId, java.time.Instant startOfDay) {
            return 0;
        }

        @Override
        public void delete(UUID likeId) {
            likes.values().removeIf(like -> like.id().equals(likeId));
        }
    }
}
