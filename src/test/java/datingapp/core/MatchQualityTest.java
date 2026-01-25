package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.MatchQualityService.MatchQuality;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for MatchQuality record. */
@DisplayName("MatchQuality Tests")
@SuppressWarnings("unused") // IDE false positives for @Nested classes
class MatchQualityTest {

    private static final String MATCH_ID = "test-match-id";
    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Valid MatchQuality is created successfully")
        void validMatchQualityCreated() {
            MatchQuality quality = new MatchQuality(
                    MATCH_ID,
                    USER_A,
                    USER_B,
                    Instant.now(),
                    0.8,
                    0.9,
                    0.5,
                    0.7,
                    0.6,
                    0.5,
                    10.5,
                    5,
                    List.of("Hiking"),
                    List.of("Both non-smokers"),
                    Duration.ofHours(2),
                    "Perfect Sync",
                    75,
                    List.of("Great match"));

            assertEquals(MATCH_ID, quality.matchId());
            assertEquals(75, quality.compatibilityScore());
        }

        @Test
        @DisplayName("Throws on null matchId")
        void throwsOnNullMatchId() {
            assertThrows(
                    NullPointerException.class,
                    () -> new MatchQuality(
                            null,
                            USER_A,
                            USER_B,
                            Instant.now(),
                            0.8,
                            0.9,
                            0.5,
                            0.7,
                            0.6,
                            0.5,
                            10.5,
                            5,
                            List.of(),
                            List.of(),
                            Duration.ZERO,
                            "Good Sync",
                            75,
                            List.of()));
        }

        @Test
        @DisplayName("Throws if score above 1.0")
        void throwsIfScoreAboveOne() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new MatchQuality(
                            MATCH_ID,
                            USER_A,
                            USER_B,
                            Instant.now(),
                            1.5,
                            0.9,
                            0.5,
                            0.7,
                            0.6,
                            0.5,
                            10.5,
                            5,
                            List.of(),
                            List.of(),
                            Duration.ZERO,
                            "Good Sync",
                            75,
                            List.of()));
        }

        @Test
        @DisplayName("Throws if score below 0.0")
        void throwsIfScoreBelowZero() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new MatchQuality(
                            MATCH_ID,
                            USER_A,
                            USER_B,
                            Instant.now(),
                            -0.1,
                            0.9,
                            0.5,
                            0.7,
                            0.6,
                            0.5,
                            10.5,
                            5,
                            List.of(),
                            List.of(),
                            Duration.ZERO,
                            "Good Sync",
                            75,
                            List.of()));
        }

        @Test
        @DisplayName("Throws if compatibility above 100")
        void throwsIfCompatibilityAbove100() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new MatchQuality(
                            MATCH_ID,
                            USER_A,
                            USER_B,
                            Instant.now(),
                            0.8,
                            0.9,
                            0.5,
                            0.7,
                            0.6,
                            0.5,
                            10.5,
                            5,
                            List.of(),
                            List.of(),
                            Duration.ZERO,
                            "Good Sync",
                            101,
                            List.of() // > 100
                            ));
        }

        @Test
        @DisplayName("Null lists become empty lists")
        void nullListsBecomeEmpty() {
            MatchQuality quality = new MatchQuality(
                    MATCH_ID,
                    USER_A,
                    USER_B,
                    Instant.now(),
                    0.8,
                    0.9,
                    0.5,
                    0.7,
                    0.6,
                    0.5,
                    10.5,
                    5,
                    null,
                    null,
                    Duration.ZERO,
                    "Good Sync",
                    75,
                    null);

            assertNotNull(quality.sharedInterests());
            assertNotNull(quality.lifestyleMatches());
            assertNotNull(quality.highlights());
            assertTrue(quality.sharedInterests().isEmpty());
        }
    }

    @Nested
    @DisplayName("Star Rating")
    class StarRatingTests {

        @Test
        @DisplayName("90+ gets 5 stars")
        void ninetyPlusGetsFiveStars() {
            MatchQuality quality = createWithScore(95);
            assertEquals(5, quality.getStarRating());
            assertEquals("Excellent Match", quality.getCompatibilityLabel());
        }

        @Test
        @DisplayName("75-89 gets 4 stars")
        void seventyFivePlusGetsFourStars() {
            MatchQuality quality = createWithScore(80);
            assertEquals(4, quality.getStarRating());
            assertEquals("Great Match", quality.getCompatibilityLabel());
        }

        @Test
        @DisplayName("60-74 gets 3 stars")
        void sixtyPlusGetsThreeStars() {
            MatchQuality quality = createWithScore(65);
            assertEquals(3, quality.getStarRating());
            assertEquals("Good Match", quality.getCompatibilityLabel());
        }

        @Test
        @DisplayName("40-59 gets 2 stars")
        void fortyPlusGetsTwoStars() {
            MatchQuality quality = createWithScore(50);
            assertEquals(2, quality.getStarRating());
            assertEquals("Fair Match", quality.getCompatibilityLabel());
        }

        @Test
        @DisplayName("Below 40 gets 1 star")
        void belowFortyGetsOneStar() {
            MatchQuality quality = createWithScore(30);
            assertEquals(1, quality.getStarRating());
            assertEquals("Low Compatibility", quality.getCompatibilityLabel());
        }

        @Test
        @DisplayName("Star display shows correct emoji count")
        void starDisplayShowsCorrectCount() {
            MatchQuality quality = createWithScore(95);
            assertEquals("⭐⭐⭐⭐⭐", quality.getStarDisplay());
        }
    }

    @Nested
    @DisplayName("Display Methods")
    class DisplayTests {

        @Test
        @DisplayName("getCompatibilityDisplay returns formatted percentage")
        void getCompatibilityDisplayReturnsPercentage() {
            MatchQuality quality = createWithScore(87);
            assertEquals("87%", quality.getCompatibilityDisplay());
        }

        @Test
        @DisplayName("getShortSummary returns first highlight")
        void getShortSummaryReturnsFirstHighlight() {
            MatchQuality quality = new MatchQuality(
                    MATCH_ID,
                    USER_A,
                    USER_B,
                    Instant.now(),
                    0.8,
                    0.9,
                    0.5,
                    0.7,
                    0.6,
                    0.5,
                    10.5,
                    5,
                    List.of(),
                    List.of(),
                    Duration.ZERO,
                    "Good Sync",
                    75,
                    List.of("First highlight", "Second"));
            assertEquals("First highlight", quality.getShortSummary());
        }

        @Test
        @DisplayName("getShortSummary truncates long highlights")
        void getShortSummaryTruncatesLong() {
            String longHighlight =
                    "This is a very long highlight that exceeds forty characters and should be truncated";
            MatchQuality quality = new MatchQuality(
                    MATCH_ID,
                    USER_A,
                    USER_B,
                    Instant.now(),
                    0.8,
                    0.9,
                    0.5,
                    0.7,
                    0.6,
                    0.5,
                    10.5,
                    5,
                    List.of(),
                    List.of(),
                    Duration.ZERO,
                    "Good Sync",
                    75,
                    List.of(longHighlight));
            String summary = quality.getShortSummary();
            assertTrue(summary.endsWith("..."));
            assertTrue(summary.length() <= 40);
        }

        @Test
        @DisplayName("getShortSummary returns label when no highlights")
        void getShortSummaryReturnsLabelWhenNoHighlights() {
            MatchQuality quality = createWithScore(75);
            assertEquals("Great Match", quality.getShortSummary());
        }
    }

    private MatchQuality createWithScore(int score) {
        return new MatchQuality(
                MATCH_ID,
                USER_A,
                USER_B,
                Instant.now(),
                0.8,
                0.9,
                0.5,
                0.7,
                0.6,
                0.5,
                10.5,
                5,
                List.of(),
                List.of(),
                Duration.ZERO,
                "Good Sync",
                score,
                List.of());
    }
}
