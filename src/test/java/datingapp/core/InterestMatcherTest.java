package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unused") // IDE false positives for @Nested classes
class InterestMatcherTest {

  @Nested
  class Compare {
    @Test
    void identicalSets_returnsOverlapRatio1() {
      Set<Interest> setA = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL);
      Set<Interest> setB = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL);

      InterestMatcher.MatchResult result = InterestMatcher.compare(setA, setB);

      assertEquals(3, result.sharedCount());
      assertEquals(1.0, result.overlapRatio(), 0.001);
      assertEquals(1.0, result.jaccardIndex(), 0.001);
    }

    @Test
    void noOverlap_returnsOverlapRatio0() {
      Set<Interest> setA = EnumSet.of(Interest.HIKING, Interest.COFFEE);
      Set<Interest> setB = EnumSet.of(Interest.MOVIES, Interest.MUSIC);

      InterestMatcher.MatchResult result = InterestMatcher.compare(setA, setB);

      assertEquals(0, result.sharedCount());
      assertEquals(0.0, result.overlapRatio(), 0.001);
      assertEquals(0.0, result.jaccardIndex(), 0.001);
    }

    @Test
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
    void asymmetricSizes_usesSmallestDenominator() {
      Set<Interest> setA = EnumSet.of(Interest.HIKING, Interest.COFFEE);
      Set<Interest> setB =
          EnumSet.of(
              Interest.HIKING, Interest.COFFEE, Interest.TRAVEL, Interest.MOVIES, Interest.COFFEE);

      InterestMatcher.MatchResult result = InterestMatcher.compare(setA, setB);

      assertEquals(2, result.sharedCount());
      // shared(2) / minSize(2) = 1.0
      assertEquals(1.0, result.overlapRatio(), 0.001);
      // shared(2) / union(4) = 0.5
      assertEquals(0.5, result.jaccardIndex(), 0.001);
    }

    @Test
    void emptySets_handlesGracefully() {
      InterestMatcher.MatchResult result =
          InterestMatcher.compare(EnumSet.noneOf(Interest.class), EnumSet.noneOf(Interest.class));
      assertEquals(0, result.sharedCount());
      assertEquals(0.0, result.overlapRatio());
    }

    @Test
    void nullInputs_handlesGracefully() {
      InterestMatcher.MatchResult result = InterestMatcher.compare(null, null);
      assertNotNull(result);
      assertEquals(0, result.sharedCount());
    }
  }

  @Nested
  class FormatSharedInterests {
    @Test
    void singleInterest_returnsName() {
      Set<Interest> shared = EnumSet.of(Interest.HIKING);
      assertEquals("Hiking", InterestMatcher.formatSharedInterests(shared));
    }

    @Test
    void twoInterests_joinsWithAnd() {
      Set<Interest> shared = EnumSet.of(Interest.HIKING, Interest.COFFEE);
      // Order depends on EnumSet, hiking is first
      String formatted = InterestMatcher.formatSharedInterests(shared);
      assertTrue(
          formatted.contains("Hiking")
              && formatted.contains("Coffee")
              && formatted.contains("and"));
    }

    @Test
    void threeInterests_joinsWithCommaAnd() {
      Set<Interest> shared = EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL);
      String formatted = InterestMatcher.formatSharedInterests(shared);
      assertEquals("Hiking, Coffee, and Travel", formatted);
    }

    @Test
    void fourPlusInterests_showsAndXMore() {
      Set<Interest> shared =
          EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL, Interest.MOVIES);
      String formatted = InterestMatcher.formatSharedInterests(shared);
      // EnumSet iterates by ordinal: HIKING(0), MOVIES(6), COFFEE(18), TRAVEL(31)
      assertEquals("Hiking, Movies, Coffee, and 1 more", formatted);
    }

    @Test
    void emptySet_returnsEmptyString() {
      assertEquals("", InterestMatcher.formatSharedInterests(EnumSet.noneOf(Interest.class)));
    }

    @Test
    void nullSet_returnsEmptyString() {
      assertEquals("", InterestMatcher.formatSharedInterests(null));
    }
  }
}
