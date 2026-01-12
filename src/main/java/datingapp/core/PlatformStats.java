package datingapp.core;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform-wide statistics for computing relative scores. Used to determine if a user is "above
 * average" or "below average".
 */
public record PlatformStats(
    UUID id,
    Instant computedAt,
    int totalActiveUsers,
    double avgLikesReceived,
    double avgLikesGiven,
    double avgMatchRate,
    double avgLikeRatio) {

  /**
   * Creates a PlatformStats record.
   *
   * @param id the unique identifier for this stats snapshot
   * @param computedAt when these stats were computed
   * @param totalActiveUsers number of active users on the platform
   * @param avgLikesReceived average likes received per user
   * @param avgLikesGiven average likes given per user
   * @param avgMatchRate average match rate across all users
   * @param avgLikeRatio average like ratio across all users
   */
  public PlatformStats {}

  public static PlatformStats create(
      int totalActiveUsers,
      double avgLikesReceived,
      double avgLikesGiven,
      double avgMatchRate,
      double avgLikeRatio) {
    return new PlatformStats(
        UUID.randomUUID(),
        Instant.now(),
        totalActiveUsers,
        avgLikesReceived,
        avgLikesGiven,
        avgMatchRate,
        avgLikeRatio);
  }

  /** Empty platform stats for new platforms with no users. */
  public static PlatformStats empty() {
    return new PlatformStats(UUID.randomUUID(), Instant.now(), 0, 0.0, 0.0, 0.0, 0.5);
  }
}
