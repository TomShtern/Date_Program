package datingapp.core;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform-wide statistics for computing relative scores.
 * Used to determine if a user is "above average" or "below average".
 */
public record PlatformStats(
        UUID id,
        Instant computedAt,
        int totalActiveUsers,
        double avgLikesReceived,
        double avgLikesGiven,
        double avgMatchRate,
        double avgLikeRatio) {
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

    /**
     * Empty platform stats for new platforms with no users.
     */
    public static PlatformStats empty() {
        return new PlatformStats(
                UUID.randomUUID(),
                Instant.now(),
                0, 0.0, 0.0, 0.0, 0.5);
    }
}
