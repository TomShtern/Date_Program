package datingapp.core;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record representing the quality/compatibility of a match. Computed from one user's
 * perspective (scores may differ slightly between perspectives).
 */
public record MatchQuality(
    String matchId,
    UUID perspectiveUserId, // Whose perspective (for directional metrics)
    UUID otherUserId,
    Instant computedAt,

    // === Individual Scores (0.0 - 1.0) ===
    double distanceScore,
    double ageScore,
    double interestScore,
    double lifestyleScore,
    double responseScore, // How quickly mutual like happened

    // === Raw Data ===
    double distanceKm,
    int ageDifference,
    List<String> sharedInterests,
    List<String> lifestyleMatches, // e.g., "Both want kids someday"
    Duration timeBetweenLikes,

    // === Aggregates ===
    int compatibilityScore, // 0-100
    List<String> highlights // Human-readable highlights
    ) {
  public MatchQuality {
    Objects.requireNonNull(matchId, "matchId cannot be null");
    Objects.requireNonNull(perspectiveUserId, "perspectiveUserId cannot be null");
    Objects.requireNonNull(otherUserId, "otherUserId cannot be null");
    Objects.requireNonNull(computedAt, "computedAt cannot be null");

    // Validate scores are 0.0-1.0
    validateScore(distanceScore, "distanceScore");
    validateScore(ageScore, "ageScore");
    validateScore(interestScore, "interestScore");
    validateScore(lifestyleScore, "lifestyleScore");
    validateScore(responseScore, "responseScore");

    // Validate compatibility is 0-100
    if (compatibilityScore < 0 || compatibilityScore > 100) {
      throw new IllegalArgumentException(
          "compatibilityScore must be 0-100, got: " + compatibilityScore);
    }

    // Defensive copies
    sharedInterests = sharedInterests == null ? List.of() : List.copyOf(sharedInterests);
    lifestyleMatches = lifestyleMatches == null ? List.of() : List.copyOf(lifestyleMatches);
    highlights = highlights == null ? List.of() : List.copyOf(highlights);
  }

  private static void validateScore(double score, String name) {
    if (score < 0.0 || score > 1.0) {
      throw new IllegalArgumentException(name + " must be 0.0-1.0, got: " + score);
    }
  }

  /** Get star rating (1-5 stars based on compatibility). */
  public int getStarRating() {
    if (compatibilityScore >= 90) return 5;
    if (compatibilityScore >= 75) return 4;
    if (compatibilityScore >= 60) return 3;
    if (compatibilityScore >= 40) return 2;
    return 1;
  }

  /** Get compatibility label. */
  public String getCompatibilityLabel() {
    if (compatibilityScore >= 90) return "Excellent Match";
    if (compatibilityScore >= 75) return "Great Match";
    if (compatibilityScore >= 60) return "Good Match";
    if (compatibilityScore >= 40) return "Fair Match";
    return "Low Compatibility";
  }

  /** Render star icons for display. */
  public String getStarDisplay() {
    return "⭐".repeat(getStarRating());
  }

  /** Get a formatted display of the compatibility score. */
  public String getCompatibilityDisplay() {
    return compatibilityScore + "%";
  }

  /** Get a short summary for list views. */
  public String getShortSummary() {
    if (!highlights.isEmpty()) {
      // Return first highlight, truncated if needed
      String first = highlights.getFirst();
      return first.length() > 40 ? first.substring(0, 37) + "..." : first;
    }
    return getCompatibilityLabel();
  }
}
