package datingapp.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of user engagement statistics. Stored periodically for historical trends and
 * faster reads.
 */
public record UserStats(
    UUID id,
    UUID userId,
    Instant computedAt,

    // === Outgoing Activity ===
    int totalSwipesGiven, // likes + passes given
    int likesGiven,
    int passesGiven,
    double likeRatio, // likesGiven / totalSwipesGiven (0.0-1.0)

    // === Incoming Activity ===
    int totalSwipesReceived, // likes + passes received
    int likesReceived,
    int passesReceived,
    double incomingLikeRatio, // likesReceived / totalSwipesReceived

    // === Matches ===
    int totalMatches, // all-time matches created
    int activeMatches, // currently active
    double matchRate, // totalMatches / likesGiven (0.0-1.0)

    // === Safety ===
    int blocksGiven, // users you blocked
    int blocksReceived, // users who blocked you
    int reportsGiven,
    int reportsReceived,

    // === Derived Scores (0.0-1.0) ===
    double reciprocityScore, // % of your likes that liked you back
    double selectivenessScore, // how picky vs platform average
    double attractivenessScore // likes received vs platform average
    ) {
  public UserStats {
    Objects.requireNonNull(id);
    Objects.requireNonNull(userId);
    Objects.requireNonNull(computedAt);

    // Validate ratios are 0.0-1.0
    validateRatio(likeRatio, "likeRatio");
    validateRatio(incomingLikeRatio, "incomingLikeRatio");
    validateRatio(matchRate, "matchRate");
    validateRatio(reciprocityScore, "reciprocityScore");
    validateRatio(selectivenessScore, "selectivenessScore");
    validateRatio(attractivenessScore, "attractivenessScore");
  }

  private static void validateRatio(double value, String name) {
    if (value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be 0.0-1.0, got: " + value);
    }
  }

  /** Factory for creating a new snapshot. */
  public static UserStats create(UUID userId, StatsBuilder builder) {
    return new UserStats(
        UUID.randomUUID(),
        userId,
        Instant.now(),
        builder.totalSwipesGiven,
        builder.likesGiven,
        builder.passesGiven,
        builder.likeRatio,
        builder.totalSwipesReceived,
        builder.likesReceived,
        builder.passesReceived,
        builder.incomingLikeRatio,
        builder.totalMatches,
        builder.activeMatches,
        builder.matchRate,
        builder.blocksGiven,
        builder.blocksReceived,
        builder.reportsGiven,
        builder.reportsReceived,
        builder.reciprocityScore,
        builder.selectivenessScore,
        builder.attractivenessScore);
  }

  /**
   * Builder for constructing stats during computation. Fields are public for direct assignment
   * during computation.
   */
  @SuppressWarnings("java:S1104") // Public fields are intentional for builder pattern
  public static class StatsBuilder {
    public int totalSwipesGiven = 0;
    public int likesGiven = 0;
    public int passesGiven = 0;
    public double likeRatio = 0.0;
    public int totalSwipesReceived = 0;
    public int likesReceived = 0;
    public int passesReceived = 0;
    public double incomingLikeRatio = 0.0;
    public int totalMatches = 0;
    public int activeMatches = 0;
    public double matchRate = 0.0;
    public int blocksGiven = 0;
    public int blocksReceived = 0;
    public int reportsGiven = 0;
    public int reportsReceived = 0;
    public double reciprocityScore = 0.0;
    public double selectivenessScore = 0.5; // 0.5 = average
    public double attractivenessScore = 0.5;
  }

  private static final String PERCENT_FORMAT = "%.1f%%";

  /** Get a display-friendly like ratio string. */
  public String getLikeRatioDisplay() {
    return String.format(PERCENT_FORMAT, likeRatio * 100);
  }

  /** Get a display-friendly match rate string. */
  public String getMatchRateDisplay() {
    return String.format(PERCENT_FORMAT, matchRate * 100);
  }

  /** Get a display-friendly reciprocity string. */
  public String getReciprocityDisplay() {
    return String.format(PERCENT_FORMAT, reciprocityScore * 100);
  }
}
