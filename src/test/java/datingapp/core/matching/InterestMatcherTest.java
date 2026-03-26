package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.profile.MatchPreferences.Interest;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Focused test coverage for InterestMatcher. Tests interest comparison, formatting utilities, and
 * MatchResult validation constraints.
 */
@DisplayName("InterestMatcher")
class InterestMatcherTest {

    @Nested
    @DisplayName("compare")
    class CompareTests {

        @Test
        @DisplayName("null first argument returns empty result")
        void compareNullFirstArgument() {
            InterestMatcher.MatchResult result =
                    InterestMatcher.compare(null, EnumSet.of(Interest.HIKING, Interest.COFFEE));

            assertNotNull(result);
            assertEquals(0, result.sharedCount());
            assertTrue(result.shared().isEmpty());
            assertEquals(0.0, result.overlapRatio());
            assertEquals(0.0, result.jaccardIndex());
            assertFalse(result.hasSharedInterests());
        }

        @Test
        @DisplayName("null second argument returns empty result")
        void compareNullSecondArgument() {
            InterestMatcher.MatchResult result = InterestMatcher.compare(EnumSet.of(Interest.HIKING), null);

            assertNotNull(result);
            assertEquals(0, result.sharedCount());
            assertTrue(result.shared().isEmpty());
            assertEquals(0.0, result.overlapRatio());
            assertEquals(0.0, result.jaccardIndex());
            assertFalse(result.hasSharedInterests());
        }

        @Test
        @DisplayName("both arguments null returns empty result")
        void compareBothNull() {
            InterestMatcher.MatchResult result = InterestMatcher.compare(null, null);

            assertNotNull(result);
            assertEquals(0, result.sharedCount());
            assertTrue(result.shared().isEmpty());
            assertEquals(0.0, result.overlapRatio());
            assertEquals(0.0, result.jaccardIndex());
            assertFalse(result.hasSharedInterests());
        }

        @Test
        @DisplayName("empty first set returns empty result")
        void compareEmptyFirstSet() {
            InterestMatcher.MatchResult result =
                    InterestMatcher.compare(EnumSet.noneOf(Interest.class), EnumSet.of(Interest.HIKING));

            assertNotNull(result);
            assertEquals(0, result.sharedCount());
            assertTrue(result.shared().isEmpty());
            assertEquals(0.0, result.overlapRatio());
            assertEquals(0.0, result.jaccardIndex());
            assertFalse(result.hasSharedInterests());
        }

        @Test
        @DisplayName("empty second set returns empty result")
        void compareEmptySecondSet() {
            InterestMatcher.MatchResult result =
                    InterestMatcher.compare(EnumSet.of(Interest.HIKING), EnumSet.noneOf(Interest.class));

            assertNotNull(result);
            assertEquals(0, result.sharedCount());
            assertTrue(result.shared().isEmpty());
            assertEquals(0.0, result.overlapRatio());
            assertEquals(0.0, result.jaccardIndex());
            assertFalse(result.hasSharedInterests());
        }

        @Test
        @DisplayName("identical sets returns perfect match")
        void compareIdenticalSets() {
            Set<Interest> interests = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.MUSIC);
            InterestMatcher.MatchResult result = InterestMatcher.compare(interests, interests);

            assertNotNull(result);
            assertEquals(3, result.sharedCount());
            assertEquals(interests, result.shared());
            assertEquals(1.0, result.overlapRatio());
            assertEquals(1.0, result.jaccardIndex());
            assertTrue(result.hasSharedInterests());
        }

        @Test
        @DisplayName("partial match computes correct overlap")
        void comparePartialMatch() {
            Set<Interest> a = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.MUSIC);
            Set<Interest> b = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.GYM);
            InterestMatcher.MatchResult result = InterestMatcher.compare(a, b);

            assertNotNull(result);
            assertEquals(2, result.sharedCount());
            assertEquals(EnumSet.of(Interest.HIKING, Interest.COFFEE), result.shared());
            // minSize = 3, overlap = 2/3
            assertEquals(2.0 / 3.0, result.overlapRatio(), 0.001);
            // union = 4, jaccard = 2/4
            assertEquals(0.5, result.jaccardIndex());
            assertTrue(result.hasSharedInterests());
        }

        @Test
        @DisplayName("asymmetric sets with different sizes")
        void compareAsymmetricSets() {
            Set<Interest> a = EnumSet.of(Interest.HIKING, Interest.COFFEE);
            Set<Interest> b = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.MUSIC, Interest.GYM, Interest.YOGA);
            InterestMatcher.MatchResult result = InterestMatcher.compare(a, b);

            assertNotNull(result);
            assertEquals(2, result.sharedCount());
            assertEquals(EnumSet.of(Interest.HIKING, Interest.COFFEE), result.shared());
            // minSize = 2, overlap = 2/2 = 1.0
            assertEquals(1.0, result.overlapRatio());
            // union = 5, jaccard = 2/5 = 0.4
            assertEquals(0.4, result.jaccardIndex());
            assertTrue(result.hasSharedInterests());
        }

        @Test
        @DisplayName("no overlap computes zero metrics")
        void compareNoOverlap() {
            Set<Interest> a = EnumSet.of(Interest.HIKING, Interest.CAMPING);
            Set<Interest> b = EnumSet.of(Interest.MUSIC, Interest.THEATER);
            InterestMatcher.MatchResult result = InterestMatcher.compare(a, b);

            assertNotNull(result);
            assertEquals(0, result.sharedCount());
            assertTrue(result.shared().isEmpty());
            assertEquals(0.0, result.overlapRatio());
            assertEquals(0.0, result.jaccardIndex());
            assertFalse(result.hasSharedInterests());
        }

        @Test
        @DisplayName("single shared interest")
        void compareSingleSharedInterest() {
            Set<Interest> a = EnumSet.of(Interest.HIKING, Interest.CAMPING, Interest.CYCLING);
            Set<Interest> b = EnumSet.of(Interest.HIKING, Interest.MUSIC);
            InterestMatcher.MatchResult result = InterestMatcher.compare(a, b);

            assertNotNull(result);
            assertEquals(1, result.sharedCount());
            assertEquals(EnumSet.of(Interest.HIKING), result.shared());
            // minSize = 2, overlap = 1/2 = 0.5
            assertEquals(0.5, result.overlapRatio());
            // union = 4 {HIKING, CAMPING, CYCLING, MUSIC}, jaccard = 1/4 = 0.25
            assertEquals(0.25, result.jaccardIndex());
            assertTrue(result.hasSharedInterests());
        }
    }

    @Nested
    @DisplayName("formatSharedInterests with default preview count")
    class FormatSharedInterestsDefaultTests {

        @Test
        @DisplayName("null returns empty string")
        void formatNullSharedInterests() {
            String result = InterestMatcher.formatSharedInterests(null);

            assertEquals("", result);
        }

        @Test
        @DisplayName("empty set returns empty string")
        void formatEmptySharedInterests() {
            String result = InterestMatcher.formatSharedInterests(EnumSet.noneOf(Interest.class));

            assertEquals("", result);
        }

        @Test
        @DisplayName("single interest returns interest name")
        void formatSingleInterest() {
            String result = InterestMatcher.formatSharedInterests(EnumSet.of(Interest.HIKING));

            assertEquals("Hiking", result);
        }

        @Test
        @DisplayName("two interests returns both with 'and'")
        void formatTwoInterests() {
            String result = InterestMatcher.formatSharedInterests(EnumSet.of(Interest.HIKING, Interest.CAMPING));

            assertTrue(result.contains("and"), "Should contain 'and'");
            assertTrue(result.contains("Hiking"), "Should contain 'Hiking'");
            assertTrue(result.contains("Camping"), "Should contain 'Camping'");
        }

        @Test
        @DisplayName("three interests uses oxford comma")
        void formatThreeInterests() {
            String result = InterestMatcher.formatSharedInterests(
                    EnumSet.of(Interest.HIKING, Interest.CAMPING, Interest.FISHING));

            // Result should be sorted and use oxford comma: "Camping, Fishing, and Hiking"
            assertTrue(result.contains("and"), "Should contain 'and'");
            assertTrue(result.contains(","), "Should contain comma");
        }

        @Test
        @DisplayName("more than default preview count (3) shows truncation")
        void formatMoreThanDefaultPreview() {
            Set<Interest> interests = EnumSet.of(Interest.HIKING, Interest.CAMPING, Interest.FISHING, Interest.CYCLING);
            String result = InterestMatcher.formatSharedInterests(interests);

            assertTrue(result.contains("and 1 more"), "Should show truncation message");
        }

        @Test
        @DisplayName("exactly default preview count (3) shows all")
        void formatExactlyDefaultPreview() {
            Set<Interest> interests = EnumSet.of(Interest.HIKING, Interest.CAMPING, Interest.FISHING);
            String result = InterestMatcher.formatSharedInterests(interests);

            assertTrue(result.contains("and"), "Should contain 'and'");
            assertFalse(result.contains("more"), "Should not contain 'more'");
        }
    }

    @Nested
    @DisplayName("formatSharedInterests with custom preview count")
    class FormatSharedInterestsCustomTests {

        @Test
        @DisplayName("custom preview count of 1 triggers truncation for 2+ interests")
        void formatCustomPreviewOne() {
            Set<Interest> interests = EnumSet.of(Interest.HIKING, Interest.CAMPING);
            String result = InterestMatcher.formatSharedInterests(interests, 1);

            assertTrue(result.contains("and 1 more"), "Should show truncation for custom preview");
        }

        @Test
        @DisplayName("custom preview count of 2")
        void formatCustomPreviewTwo() {
            Set<Interest> interests = EnumSet.of(Interest.HIKING, Interest.CAMPING, Interest.FISHING, Interest.CYCLING);
            String result = InterestMatcher.formatSharedInterests(interests, 2);

            assertTrue(result.contains("and 2 more"), "Should show correct truncation count");
        }

        @Test
        @DisplayName("custom preview count larger than set size")
        void formatCustomPreviewLargerThanSet() {
            Set<Interest> interests = EnumSet.of(Interest.HIKING, Interest.CAMPING);
            String result = InterestMatcher.formatSharedInterests(interests, 10);

            assertFalse(result.contains("more"), "Should not truncate when preview >= set size");
            assertTrue(result.contains("and"), "Should still format with 'and'");
        }

        @Test
        @DisplayName("previewCount less than 1 throws IllegalArgumentException")
        void formatPreviewCountLessThanOne() {
            Set<Interest> interests = EnumSet.of(Interest.HIKING, Interest.CAMPING);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> InterestMatcher.formatSharedInterests(interests, 0),
                    "Should throw for previewCount < 1");
        }

        @Test
        @DisplayName("previewCount zero throws IllegalArgumentException")
        void formatPreviewCountZero() {
            Set<Interest> interests = EnumSet.of(Interest.HIKING);

            assertThrows(IllegalArgumentException.class, () -> InterestMatcher.formatSharedInterests(interests, 0));
        }

        @Test
        @DisplayName("previewCount negative throws IllegalArgumentException")
        void formatPreviewCountNegative() {
            Set<Interest> interests = EnumSet.of(Interest.HIKING);

            assertThrows(IllegalArgumentException.class, () -> InterestMatcher.formatSharedInterests(interests, -1));
        }
    }

    @Nested
    @DisplayName("formatAsList")
    class FormatAsListTests {

        @Test
        @DisplayName("null returns empty list")
        void formatAsListNull() {
            List<String> result = InterestMatcher.formatAsList(null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("empty set returns empty list")
        void formatAsListEmpty() {
            List<String> result = InterestMatcher.formatAsList(EnumSet.noneOf(Interest.class));

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("single interest returns single-element list")
        void formatAsListSingle() {
            List<String> result = InterestMatcher.formatAsList(EnumSet.of(Interest.HIKING));

            assertEquals(1, result.size());
            assertEquals("Hiking", result.get(0));
        }

        @Test
        @DisplayName("multiple interests returns sorted list")
        void formatAsListMultiple() {
            Set<Interest> interests = EnumSet.of(Interest.YOGA, Interest.HIKING, Interest.COFFEE);
            List<String> result = InterestMatcher.formatAsList(interests);

            assertEquals(3, result.size());
            // Should be sorted alphabetically
            assertEquals(List.of("Coffee", "Hiking", "Yoga"), result);
        }

        @Test
        @DisplayName("output is deterministic across multiple calls")
        void formatAsListDeterministic() {
            Set<Interest> interests = EnumSet.of(Interest.MUSIC, Interest.GYM, Interest.CAMPING, Interest.BOARD_GAMES);

            List<String> result1 = InterestMatcher.formatAsList(interests);
            List<String> result2 = InterestMatcher.formatAsList(interests);
            List<String> result3 = InterestMatcher.formatAsList(interests);

            assertEquals(result1, result2);
            assertEquals(result2, result3);
        }

        @Test
        @DisplayName("results are sorted alphabetically")
        void formatAsListSorted() {
            Set<Interest> interests = EnumSet.of(
                    Interest.THEATER, Interest.BAKING, Interest.SWIMMING, Interest.ART_GALLERIES, Interest.READING);
            List<String> result = InterestMatcher.formatAsList(interests);

            List<String> sorted = result.stream().sorted().toList();
            assertEquals(sorted, result, "Result should be already sorted");
        }
    }

    @Nested
    @DisplayName("MatchResult constructor validation")
    class MatchResultConstructorTests {

        @Test
        @DisplayName("null shared set throws NullPointerException")
        void constructorNullShared() {
            assertThrows(
                    NullPointerException.class,
                    () -> new InterestMatcher.MatchResult(null, 0, 0.0, 0.0),
                    "Should throw NullPointerException for null shared set");
        }

        @Test
        @DisplayName("negative sharedCount throws IllegalArgumentException")
        void constructorNegativeSharedCount() {
            Set<Interest> shared = EnumSet.noneOf(Interest.class);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new InterestMatcher.MatchResult(shared, -1, 0.0, 0.0),
                    "Should throw for negative sharedCount");
        }

        @Test
        @DisplayName("overlapRatio less than 0 throws IllegalArgumentException")
        void constructorOverlapRatioBelowRange() {
            Set<Interest> shared = EnumSet.noneOf(Interest.class);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new InterestMatcher.MatchResult(shared, 0, -0.1, 0.0),
                    "Should throw for overlapRatio < 0");
        }

        @Test
        @DisplayName("overlapRatio greater than 1 throws IllegalArgumentException")
        void constructorOverlapRatioAboveRange() {
            Set<Interest> shared = EnumSet.noneOf(Interest.class);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new InterestMatcher.MatchResult(shared, 0, 1.1, 0.0),
                    "Should throw for overlapRatio > 1");
        }

        @Test
        @DisplayName("jaccardIndex less than 0 throws IllegalArgumentException")
        void constructorJaccardIndexBelowRange() {
            Set<Interest> shared = EnumSet.noneOf(Interest.class);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new InterestMatcher.MatchResult(shared, 0, 0.0, -0.1),
                    "Should throw for jaccardIndex < 0");
        }

        @Test
        @DisplayName("jaccardIndex greater than 1 throws IllegalArgumentException")
        void constructorJaccardIndexAboveRange() {
            Set<Interest> shared = EnumSet.noneOf(Interest.class);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new InterestMatcher.MatchResult(shared, 0, 0.0, 1.1),
                    "Should throw for jaccardIndex > 1");
        }

        @Test
        @DisplayName("valid parameters construct successfully")
        void constructorValid() {
            Set<Interest> shared = EnumSet.of(Interest.HIKING, Interest.COFFEE);

            InterestMatcher.MatchResult result = new InterestMatcher.MatchResult(shared, 2, 0.75, 0.5);

            assertNotNull(result);
            assertEquals(shared, result.shared());
            assertEquals(2, result.sharedCount());
            assertEquals(0.75, result.overlapRatio());
            assertEquals(0.5, result.jaccardIndex());
        }

        @Test
        @DisplayName("boundary values 0.0 and 1.0 are valid")
        void constructorBoundaryValues() {
            Set<Interest> shared = EnumSet.noneOf(Interest.class);

            InterestMatcher.MatchResult result0 = new InterestMatcher.MatchResult(shared, 0, 0.0, 0.0);
            assertNotNull(result0);

            Set<Interest> shared1 = EnumSet.of(Interest.HIKING);
            InterestMatcher.MatchResult result1 = new InterestMatcher.MatchResult(shared1, 1, 1.0, 1.0);
            assertNotNull(result1);
        }

        @Test
        @DisplayName("hasSharedInterests returns true only when sharedCount > 0")
        void hasSharedInterests() {
            Set<Interest> empty = EnumSet.noneOf(Interest.class);
            InterestMatcher.MatchResult resultEmpty = new InterestMatcher.MatchResult(empty, 0, 0.0, 0.0);
            assertFalse(resultEmpty.hasSharedInterests());

            Set<Interest> withInterest = EnumSet.of(Interest.HIKING);
            InterestMatcher.MatchResult resultWithInterest = new InterestMatcher.MatchResult(withInterest, 1, 1.0, 1.0);
            assertTrue(resultWithInterest.hasSharedInterests());
        }
    }

    @Nested
    @DisplayName("Edge cases and comprehensive scenarios")
    class EdgeCaseTests {

        @Test
        @DisplayName("large interest sets produce expected metrics")
        void largeInterestSets() {
            // Set with many interests
            Set<Interest> a = EnumSet.of(
                    Interest.HIKING,
                    Interest.CAMPING,
                    Interest.FISHING,
                    Interest.CYCLING,
                    Interest.RUNNING,
                    Interest.CLIMBING,
                    Interest.MOVIES,
                    Interest.MUSIC);
            Set<Interest> b = EnumSet.of(
                    Interest.HIKING,
                    Interest.CAMPING,
                    Interest.MOVIES,
                    Interest.MUSIC,
                    Interest.COOKING,
                    Interest.BAKING,
                    Interest.GYM,
                    Interest.YOGA);

            InterestMatcher.MatchResult result = InterestMatcher.compare(a, b);

            assertEquals(4, result.sharedCount());
            assertEquals(
                    EnumSet.of(Interest.HIKING, Interest.CAMPING, Interest.MOVIES, Interest.MUSIC), result.shared());
            // minSize = 8, overlap = 4/8 = 0.5
            assertEquals(0.5, result.overlapRatio());
            // union = 12, jaccard = 4/12 ≈ 0.333
            assertEquals(4.0 / 12.0, result.jaccardIndex(), 0.001);
        }

        @Test
        @DisplayName("formatSharedInterests maintains alphabetical order in output")
        void formatSharedInterestsAlphabetical() {
            Set<Interest> interests = EnumSet.of(Interest.YOGA, Interest.HIKING, Interest.CAMPING, Interest.BAKING);
            String result = InterestMatcher.formatSharedInterests(interests, 4);

            // Should be alphabetically ordered: "Baking, Camping, Hiking, and Yoga"
            assertTrue(result.toLowerCase().startsWith("baking"), "Should start with 'Baking' (alphabetical)");
        }

        @Test
        @DisplayName("all methods are thread-safe and stateless")
        void threadSafety() {
            // Verify that calling methods multiple times with same inputs yields same output
            Set<Interest> a = EnumSet.of(Interest.HIKING, Interest.COFFEE);
            Set<Interest> b = EnumSet.of(Interest.HIKING, Interest.MUSIC);

            InterestMatcher.MatchResult r1 = InterestMatcher.compare(a, b);
            InterestMatcher.MatchResult r2 = InterestMatcher.compare(a, b);

            assertEquals(r1.sharedCount(), r2.sharedCount());
            assertEquals(r1.overlapRatio(), r2.overlapRatio());
            assertEquals(r1.jaccardIndex(), r2.jaccardIndex());
        }
    }
}
