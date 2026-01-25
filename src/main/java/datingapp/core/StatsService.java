package datingapp.core;

import datingapp.core.Stats.PlatformStats;
import datingapp.core.Stats.UserStats;
import datingapp.core.UserInteractions.BlockStorage;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserInteractions.LikeStorage;
import datingapp.core.UserInteractions.ReportStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for computing and managing user statistics. Computes statistics from various data sources
 * and stores as snapshots.
 */
public class StatsService {

    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final BlockStorage blockStorage;
    private final ReportStorage reportStorage;
    private final UserStatsStorage userStatsStorage;
    private final PlatformStatsStorage platformStatsStorage;

    public StatsService(
            LikeStorage likeStorage,
            MatchStorage matchStorage,
            BlockStorage blockStorage,
            ReportStorage reportStorage,
            UserStatsStorage userStatsStorage,
            PlatformStatsStorage platformStatsStorage) {
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.blockStorage = Objects.requireNonNull(blockStorage);
        this.reportStorage = Objects.requireNonNull(reportStorage);
        this.userStatsStorage = Objects.requireNonNull(userStatsStorage);
        this.platformStatsStorage = Objects.requireNonNull(platformStatsStorage);
    }

    /**
     * Compute and save stats snapshot for a single user.
     *
     * @param userId the user ID
     * @return the computed statistics
     */
    public UserStats computeAndSaveStats(UUID userId) {
        UserStats.StatsBuilder builder = new UserStats.StatsBuilder();

        // --- Outgoing Activity ---
        builder.likesGiven = likeStorage.countByDirection(userId, Like.Direction.LIKE);
        builder.passesGiven = likeStorage.countByDirection(userId, Like.Direction.PASS);
        builder.totalSwipesGiven = builder.likesGiven + builder.passesGiven;
        builder.likeRatio = builder.totalSwipesGiven > 0 ? (double) builder.likesGiven / builder.totalSwipesGiven : 0.0;

        // --- Incoming Activity ---
        builder.likesReceived = likeStorage.countReceivedByDirection(userId, Like.Direction.LIKE);
        builder.passesReceived = likeStorage.countReceivedByDirection(userId, Like.Direction.PASS);
        builder.totalSwipesReceived = builder.likesReceived + builder.passesReceived;
        builder.incomingLikeRatio =
                builder.totalSwipesReceived > 0 ? (double) builder.likesReceived / builder.totalSwipesReceived : 0.0;

        // --- Matches ---
        List<Match> allMatches = matchStorage.getAllMatchesFor(userId);
        builder.totalMatches = allMatches.size();
        builder.activeMatches =
                (int) allMatches.stream().filter(Match::isActive).count();
        builder.matchRate =
                builder.likesGiven > 0 ? Math.min(1.0, (double) builder.totalMatches / builder.likesGiven) : 0.0;

        // --- Safety ---
        builder.blocksGiven = blockStorage.countBlocksGiven(userId);
        builder.blocksReceived = blockStorage.countBlocksReceived(userId);
        builder.reportsGiven = reportStorage.countReportsBy(userId);
        builder.reportsReceived = reportStorage.countReportsAgainst(userId);

        // --- Reciprocity Score ---
        // Of all the people I liked, what % liked me back?
        int mutualLikes = likeStorage.countMutualLikes(userId);
        builder.reciprocityScore =
                builder.likesGiven > 0 ? Math.min(1.0, (double) mutualLikes / builder.likesGiven) : 0.0;

        // --- Derived Scores (require platform averages) ---
        Optional<PlatformStats> platformStats = platformStatsStorage.getLatest();
        if (platformStats.isPresent()) {
            PlatformStats ps = platformStats.get();

            // Selectiveness: lower like ratio = more selective
            // Score of 0.5 = average, >0.5 = more selective, <0.5 = less selective
            if (ps.avgLikeRatio() > 0) {
                double selectivenessRaw = 1.0 - (builder.likeRatio / ps.avgLikeRatio());
                builder.selectivenessScore = Math.clamp(0.5 + selectivenessRaw * 0.5, 0.0, 1.0);
            }

            // Attractiveness: more likes received = more attractive
            if (ps.avgLikesReceived() > 0) {
                double attractivenessRaw = builder.likesReceived / ps.avgLikesReceived();
                builder.attractivenessScore = Math.clamp(attractivenessRaw / 2.0, 0.0, 1.0);
            }
        }

        UserStats stats = UserStats.create(userId, builder);
        userStatsStorage.save(stats);
        return stats;
    }

    /**
     * Get user stats, computing fresh if none exist or stale (older than 24 hours).
     *
     * @param userId the user ID
     * @return the latest or freshly computed statistics
     */
    public UserStats getOrComputeStats(UUID userId) {
        Optional<UserStats> existing = userStatsStorage.getLatest(userId);

        if (existing.isPresent()) {
            // Check if stale (older than 24 hours)
            Duration age = Duration.between(existing.get().computedAt(), Instant.now());
            if (age.toHours() < 24) {
                return existing.get();
            }
        }

        return computeAndSaveStats(userId);
    }

    /**
     * Get the latest stats for a user without computing if missing.
     *
     * @param userId the user ID
     * @return the latest statistics if available
     */
    public Optional<UserStats> getStats(UUID userId) {
        return userStatsStorage.getLatest(userId);
    }

    /**
     * Compute and save platform-wide statistics.
     *
     * @return the computed platform statistics
     */
    public PlatformStats computeAndSavePlatformStats() {
        List<UserStats> allStats = userStatsStorage.getAllLatestStats();

        if (allStats.isEmpty()) {
            PlatformStats stats = PlatformStats.empty();
            platformStatsStorage.save(stats);
            return stats;
        }

        double totalLikesReceived = 0;
        double totalLikesGiven = 0;
        double totalMatchRate = 0;
        double totalLikeRatio = 0;

        for (UserStats s : allStats) {
            totalLikesReceived += s.likesReceived();
            totalLikesGiven += s.likesGiven();
            totalMatchRate += s.matchRate();
            totalLikeRatio += s.likeRatio();
        }

        int n = allStats.size();
        PlatformStats stats = PlatformStats.create(
                n, totalLikesReceived / n, totalLikesGiven / n, totalMatchRate / n, totalLikeRatio / n);

        platformStatsStorage.save(stats);
        return stats;
    }

    /**
     * Get the latest platform statistics.
     *
     * @return the latest platform statistics if available
     */
    public Optional<PlatformStats> getPlatformStats() {
        return platformStatsStorage.getLatest();
    }
}
