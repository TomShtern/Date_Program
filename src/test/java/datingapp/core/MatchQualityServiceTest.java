package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.*;
import datingapp.core.matching.MatchQualityService.InterestMatcher;
import datingapp.core.matching.MatchQualityService.MatchQuality;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/** Tests for MatchQualityService. */
@DisplayName("MatchQualityService Tests")
@SuppressWarnings("unused") // IDE false positives for @Nested classes and @BeforeEach
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class MatchQualityServiceTest {

    private MatchQualityService service;
    private TestStorages.Users userStorage;
    private TestStorages.Interactions likeStorage;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        userStorage = new TestStorages.Users();
        likeStorage = new TestStorages.Interactions();
        service = new MatchQualityService(userStorage, likeStorage, AppConfig.defaults());
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

            assertEquals(0.3, quality.interestScore()); // INTEREST_MISSING_SCORE
        }

        @Test
        @DisplayName("Missing location yields neutral distance score")
        void missingLocationGivesNeutralDistanceScore() {
            User alice = new User(UUID.randomUUID(), "Alice");
            alice.setBirthDate(AppClock.today().minusYears(25));
            alice.setGender(Gender.OTHER);
            alice.setInterestedIn(EnumSet.of(Gender.OTHER));

            User bob = new User(UUID.randomUUID(), "Bob");
            bob.setBirthDate(AppClock.today().minusYears(26));
            bob.setGender(Gender.OTHER);
            bob.setInterestedIn(EnumSet.of(Gender.OTHER));

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());
            addMutualLikes(alice.getId(), bob.getId());

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

            assertEquals(0.5, quality.distanceScore()); // NEUTRAL_SCORE
            assertEquals(-1, quality.distanceKm());
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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

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

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

            assertTrue(quality.highlights().stream().anyMatch(h -> h.contains("Similar age")));
        }

        @Test
        @DisplayName("Highlights are capped at max count")
        void highlightsAreCapped() {
            User alice = createUser("Alice", 25, 32.0, 34.0);
            User bob = createUser("Bob", 26, 32.0, 34.0);
            alice.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL));
            bob.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL));
            alice.setSmoking(Lifestyle.Smoking.NEVER);
            bob.setSmoking(Lifestyle.Smoking.NEVER);
            alice.setDrinking(Lifestyle.Drinking.SOCIALLY);
            bob.setDrinking(Lifestyle.Drinking.SOCIALLY);

            userStorage.save(alice);
            userStorage.save(bob);

            Match match = Match.create(alice.getId(), bob.getId());

            Instant firstLikeAt = AppClock.now().minus(Duration.ofHours(2));
            Instant secondLikeAt = AppClock.now().minus(Duration.ofHours(1));
            likeStorage.save(new Like(UUID.randomUUID(), alice.getId(), bob.getId(), Like.Direction.LIKE, firstLikeAt));
            likeStorage.save(
                    new Like(UUID.randomUUID(), bob.getId(), alice.getId(), Like.Direction.LIKE, secondLikeAt));

            MatchQuality quality = service.computeQuality(match, alice.getId()).orElseThrow();

            assertEquals(
                    5, // HIGHLIGHT_MAX_COUNT
                    quality.highlights().size());
        }
    }

    @Nested
    @DisplayName("Progress Bar Rendering")
    class ProgressBarTests {

        @Test
        @DisplayName("should render progress bars correctly")
        void rendersProgressBars() {
            assertEquals("░░░░░░░░░░", TextUtil.renderProgressBar(0.0, 10));
            assertEquals("█████░░░░░", TextUtil.renderProgressBar(0.5, 10));
            assertEquals("██████████", TextUtil.renderProgressBar(1.0, 10));
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

            MatchQuality quality =
                    customService.computeQuality(match, alice.getId()).orElseThrow();

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
        user.setBirthDate(AppClock.today().minusYears(age));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.setLocation(lat, lon);
        user.setMaxDistanceKm(50);
        user.setAgeRange(18, 60);
        user.addPhotoUrl("http://example.com/photo.jpg");
        user.setBio("Test bio");
        user.setPacePreferences(new PacePreferences(
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
}
