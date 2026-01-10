package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for UserStats validation and creation. */
class UserStatsTest {

  @Nested
  @DisplayName("Validation tests")
  class ValidationTests {

    @Test
    @DisplayName("Ratios must be between 0.0 and 1.0")
    void validatesRatios() {
      var ex = assertThrows(IllegalArgumentException.class, () -> createInvalidRatioStats());
      assertNotNull(ex);
    }

    @Test
    @DisplayName("Negative ratios are rejected")
    void rejectsNegativeRatios() {
      var ex = assertThrows(IllegalArgumentException.class, () -> createNegativeRatioStats());
      assertNotNull(ex);
    }

    private UserStats createInvalidRatioStats() {
      return new UserStats(
          UUID.randomUUID(),
          UUID.randomUUID(),
          Instant.now(),
          0,
          0,
          0,
          1.5, // likeRatio > 1.0
          0,
          0,
          0,
          0.5,
          0,
          0,
          0.5,
          0,
          0,
          0,
          0,
          0.5,
          0.5,
          0.5);
    }

    private UserStats createNegativeRatioStats() {
      return new UserStats(
          UUID.randomUUID(),
          UUID.randomUUID(),
          Instant.now(),
          0,
          0,
          0,
          -0.1, // negative likeRatio
          0,
          0,
          0,
          0.5,
          0,
          0,
          0.5,
          0,
          0,
          0,
          0,
          0.5,
          0.5,
          0.5);
    }

    @Test
    @DisplayName("Valid stats are accepted")
    void acceptsValidStats() {
      UserStats stats =
          new UserStats(
              UUID.randomUUID(),
              UUID.randomUUID(),
              Instant.now(),
              100,
              70,
              30,
              0.7, // outgoing
              80,
              50,
              30,
              0.625, // incoming
              15,
              10,
              0.214, // matches
              2,
              1,
              1,
              0, // safety
              0.35,
              0.6,
              0.55 // scores
              );

      assertEquals(100, stats.totalSwipesGiven());
      assertEquals(70, stats.likesGiven());
      assertEquals(0.7, stats.likeRatio(), 0.01);
      assertEquals(15, stats.totalMatches());
    }
  }

  @Nested
  @DisplayName("Builder tests")
  class BuilderTests {

    @Test
    @DisplayName("StatsBuilder creates valid stats through factory")
    void builderCreatesValidStats() {
      UserStats.StatsBuilder builder = new UserStats.StatsBuilder();
      builder.likesGiven = 50;
      builder.passesGiven = 50;
      builder.totalSwipesGiven = 100;
      builder.likeRatio = 0.5;
      builder.totalMatches = 10;
      builder.matchRate = 0.2;

      UUID userId = UUID.randomUUID();
      UserStats stats = UserStats.create(userId, builder);

      assertNotNull(stats.id());
      assertEquals(userId, stats.userId());
      assertEquals(100, stats.totalSwipesGiven());
      assertEquals(0.5, stats.likeRatio(), 0.01);
    }

    @Test
    @DisplayName("Builder defaults to sensible values")
    void builderDefaults() {
      UserStats.StatsBuilder builder = new UserStats.StatsBuilder();

      assertEquals(0, builder.likesGiven);
      assertEquals(0.0, builder.likeRatio);
      assertEquals(0.5, builder.selectivenessScore); // 0.5 = average
      assertEquals(0.5, builder.attractivenessScore);
    }
  }

  @Nested
  @DisplayName("Display formatting tests")
  class DisplayTests {

    @Test
    @DisplayName("Like ratio displays as percentage")
    void likeRatioDisplay() {
      UserStats stats = createTestStats(0.75);
      assertEquals("75.0%", stats.getLikeRatioDisplay());
    }

    @Test
    @DisplayName("Match rate displays as percentage")
    void matchRateDisplay() {
      UserStats.StatsBuilder builder = new UserStats.StatsBuilder();
      builder.matchRate = 0.214;
      UserStats stats = UserStats.create(UUID.randomUUID(), builder);
      assertEquals("21.4%", stats.getMatchRateDisplay());
    }

    private UserStats createTestStats(double likeRatio) {
      UserStats.StatsBuilder builder = new UserStats.StatsBuilder();
      builder.likeRatio = likeRatio;
      return UserStats.create(UUID.randomUUID(), builder);
    }
  }
}
