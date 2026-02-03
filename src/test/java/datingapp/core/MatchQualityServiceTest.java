package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.MatchQualityService.InterestMatcher;
import datingapp.core.MatchQualityService.MatchQuality;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.Preferences.PacePreferences.CommunicationStyle;
import datingapp.core.Preferences.PacePreferences.DepthPreference;
import datingapp.core.Preferences.PacePreferences.MessagingFrequency;
import datingapp.core.Preferences.PacePreferences.TimeToFirstDate;
import datingapp.core.User.ProfileNote;
import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.UserStorage;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

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
        service = new MatchQualityService(userStorage, likeStorage, AppConfig.defaults());
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
    @DisplayName("AppConfig Match Quality Weights")
    class ConfigWeightTests {

        @Test
        @DisplayName("Default config weights sum to 1.0")
        void defaultConfigSumsToOne() {
            AppConfig config = AppConfig.defaults();

            double total = config.distanceWeight()
                    + config.ageWeight()
                    + config.interestWeight()
                    + config.lifestyleWeight()
                    + config.paceWeight()
                    + config.responseWeight();

            assertEquals(1.0, total, 0.001);
        }

        @Test
        @DisplayName("Custom weights can be configured")
        void customWeightsCanBeConfigured() {
            AppConfig config = AppConfig.builder()
                    .distanceWeight(0.3)
                    .ageWeight(0.1)
                    .interestWeight(0.2)
                    .lifestyleWeight(0.2)
                    .paceWeight(0.1)
                    .responseWeight(0.1)
                    .build();

            assertEquals(0.3, config.distanceWeight());
            assertEquals(0.1, config.ageWeight());
            assertEquals(0.2, config.interestWeight());
            assertEquals(0.2, config.lifestyleWeight());
            assertEquals(0.1, config.paceWeight());
            assertEquals(0.1, config.responseWeight());
        }

        @Test
        @DisplayName("Service uses config weights for scoring")
        void serviceUsesConfigWeights() {
            // Create config with distance weight = 1.0, all others = 0.0
            AppConfig config = AppConfig.builder()
                    .distanceWeight(1.0)
                    .ageWeight(0.0)
                    .interestWeight(0.0)
                    .lifestyleWeight(0.0)
                    .paceWeight(0.0)
                    .responseWeight(0.0)
                    .build();

            MatchQualityService customService = new MatchQualityService(userStorage, likeStorage, config);

            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.0, 34.0); // Very close
            alice.setMaxDistanceKm(50);
            bob.setMaxDistanceKm(50);

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = customService.computeQuality(match, alice.getId());

            // Score should be very high since distance is the only factor
            assertTrue(quality.compatibilityScore() > 90);
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
            Set<Interest> setB = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL, Interest.MOVIES);

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

    private static class TestUserStorage implements UserStorage {
        private final java.util.Map<UUID, User> users = new java.util.HashMap<>();
        private final java.util.Map<String, ProfileNote> profileNotes = new java.util.concurrent.ConcurrentHashMap<>();

        private static String noteKey(UUID authorId, UUID subjectId) {
            return authorId + "_" + subjectId;
        }

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

        @Override
        public void saveProfileNote(ProfileNote note) {
            profileNotes.put(noteKey(note.authorId(), note.subjectId()), note);
        }

        @Override
        public Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
            return Optional.ofNullable(profileNotes.get(noteKey(authorId, subjectId)));
        }

        @Override
        public List<ProfileNote> getProfileNotesByAuthor(UUID authorId) {
            return profileNotes.values().stream()
                    .filter(note -> note.authorId().equals(authorId))
                    .sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt()))
                    .toList();
        }

        @Override
        public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
            return profileNotes.remove(noteKey(authorId, subjectId)) != null;
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
        public java.util.List<java.util.Map.Entry<UUID, java.time.Instant>> getLikeTimesForUsersWhoLiked(UUID userId) {
            java.util.List<java.util.Map.Entry<UUID, java.time.Instant>> result = new java.util.ArrayList<>();
            for (Like like : likes.values()) {
                if (like.whoGotLiked().equals(userId) && like.direction() == Like.Direction.LIKE) {
                    result.add(java.util.Map.entry(like.whoLikes(), like.createdAt()));
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
