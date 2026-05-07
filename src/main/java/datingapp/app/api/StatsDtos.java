package datingapp.app.api;

import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.UserStats;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class StatsDtos {
    private StatsDtos() {}

    /** User stats DTO. */
    static record UserStatsDto(
            UUID userId,
            Instant computedAt,
            int totalSwipesGiven,
            int likesGiven,
            int passesGiven,
            String likeRatio,
            int totalSwipesReceived,
            int likesReceived,
            int passesReceived,
            String incomingLikeRatio,
            int totalMatches,
            int activeMatches,
            String matchRate,
            int blocksGiven,
            int blocksReceived,
            int reportsGiven,
            int reportsReceived,
            String reciprocityScore,
            double selectivenessScore,
            double attractivenessScore) {
        static UserStatsDto from(UserStats stats) {
            return new UserStatsDto(
                    stats.userId(),
                    stats.computedAt(),
                    stats.totalSwipesGiven(),
                    stats.likesGiven(),
                    stats.passesGiven(),
                    stats.getLikeRatioDisplay(),
                    stats.totalSwipesReceived(),
                    stats.likesReceived(),
                    stats.passesReceived(),
                    stats.getIncomingLikeRatioDisplay(),
                    stats.totalMatches(),
                    stats.activeMatches(),
                    stats.getMatchRateDisplay(),
                    stats.blocksGiven(),
                    stats.blocksReceived(),
                    stats.reportsGiven(),
                    stats.reportsReceived(),
                    stats.getReciprocityDisplay(),
                    stats.selectivenessScore(),
                    stats.attractivenessScore());
        }
    }

    /** Achievement unlocked DTO. */
    static record AchievementUnlockedDto(
            UUID id,
            String achievementName,
            String description,
            String icon,
            String iconLiteral,
            String category,
            int xp,
            Instant unlockedAt) {
        static AchievementUnlockedDto from(UserAchievement achievement) {
            return new AchievementUnlockedDto(
                    achievement.id(),
                    achievement.achievement().getDisplayName(),
                    achievement.achievement().getDescription(),
                    achievement.achievement().getIcon(),
                    achievement.achievement().getIconLiteral(),
                    achievement.achievement().getCategory().name(),
                    achievement.achievement().getXp(),
                    achievement.unlockedAt());
        }
    }

    /** Achievement snapshot DTO. */
    static record AchievementSnapshotDto(
            List<AchievementUnlockedDto> unlocked,
            List<AchievementUnlockedDto> newlyUnlocked,
            int unlockedCount,
            int newlyUnlockedCount) {
        AchievementSnapshotDto {
            unlocked = unlocked == null ? List.of() : List.copyOf(unlocked);
            newlyUnlocked = newlyUnlocked == null ? List.of() : List.copyOf(newlyUnlocked);
            unlockedCount = unlocked.size();
            newlyUnlockedCount = newlyUnlocked.size();
        }

        static AchievementSnapshotDto from(
                datingapp.app.usecase.profile.ProfileInsightsUseCases.AchievementSnapshot snapshot) {
            return new AchievementSnapshotDto(
                    snapshot.unlocked().stream()
                            .map(AchievementUnlockedDto::from)
                            .toList(),
                    snapshot.newlyUnlocked().stream()
                            .map(AchievementUnlockedDto::from)
                            .toList(),
                    snapshot.unlocked().size(),
                    snapshot.newlyUnlocked().size());
        }
    }
}
