package datingapp.core.service;

import datingapp.core.AppClock;
import datingapp.core.model.Match;
import datingapp.core.model.Stats.PlatformStats;
import datingapp.core.model.Stats.UserStats;
import datingapp.core.model.UserInteractions.Like;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.StatsStorage;
import datingapp.core.storage.TrustSafetyStorage;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class StatsService {

    private final LikeStorage likeStorage;
    private final MatchStorage matchStorage;
    private final TrustSafetyStorage trustSafetyStorage;
    private final StatsStorage statsStorage;

    public StatsService(
            LikeStorage likeStorage,
            MatchStorage matchStorage,
            TrustSafetyStorage trustSafetyStorage,
            StatsStorage statsStorage) {
        this.likeStorage = Objects.requireNonNull(likeStorage);
        this.matchStorage = Objects.requireNonNull(matchStorage);
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage);
        this.statsStorage = Objects.requireNonNull(statsStorage);
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
        builder.blocksGiven = trustSafetyStorage.countBlocksGiven(userId);
        builder.blocksReceived = trustSafetyStorage.countBlocksReceived(userId);
        builder.reportsGiven = trustSafetyStorage.countReportsBy(userId);
        builder.reportsReceived = trustSafetyStorage.countReportsAgainst(userId);

        // --- Reciprocity Score ---
        // Of all the people I liked, what % liked me back?
        int mutualLikes = likeStorage.countMutualLikes(userId);
        builder.reciprocityScore =
                builder.likesGiven > 0 ? Math.min(1.0, (double) mutualLikes / builder.likesGiven) : 0.0;

        // --- Derived Scores (require platform averages) ---
        Optional<PlatformStats> platformStats = statsStorage.getLatestPlatformStats();
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
        statsStorage.saveUserStats(stats);
        return stats;
    }

    /**
     * Get user stats, computing fresh if none exist or stale (older than 24 hours).
     *
     * @param userId the user ID
     * @return the latest or freshly computed statistics
     */
    public UserStats getOrComputeStats(UUID userId) {
        Optional<UserStats> existing = statsStorage.getLatestUserStats(userId);

        if (existing.isPresent()) {
            // Check if stale (older than 24 hours)
            Duration age = Duration.between(existing.get().computedAt(), AppClock.now());
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
        return statsStorage.getLatestUserStats(userId);
    }

    /**
     * Compute and save platform-wide statistics.
     *
     * @return the computed platform statistics
     */
    public PlatformStats computeAndSavePlatformStats() {
        List<UserStats> allStats = statsStorage.getAllLatestUserStats();

        if (allStats.isEmpty()) {
            PlatformStats stats = PlatformStats.empty();
            statsStorage.savePlatformStats(stats);
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

        statsStorage.savePlatformStats(stats);
        return stats;
    }

    /**
     * Get the latest platform statistics.
     *
     * @return the latest platform statistics if available
     */
    public Optional<PlatformStats> getPlatformStats() {
        return statsStorage.getLatestPlatformStats();
    }
}
