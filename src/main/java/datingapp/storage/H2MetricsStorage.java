package datingapp.storage;

import datingapp.core.Achievement;
import datingapp.core.Achievement.UserAchievement;
import datingapp.core.Achievement.UserAchievementStorage;
import datingapp.core.DailyService.DailyPickStorage;
import datingapp.core.Stats.PlatformStats;
import datingapp.core.Stats.PlatformStatsStorage;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * H2 storage implementation for platform metrics and tracking data.
 * Groups achievements, daily picks, and platform-wide statistics.
 *
 * <p>This consolidated class provides access to UserAchievementStorage,
 * DailyPickStorage, and PlatformStatsStorage implementations via nested classes.
 */
public final class H2MetricsStorage {

    private final Achievements achievements;
    private final DailyPicks dailyPicks;
    private final PlatformStatistics platformStats;

    public H2MetricsStorage(DatabaseManager dbManager) {
        this.achievements = new Achievements(dbManager);
        this.dailyPicks = new DailyPicks(dbManager);
        this.platformStats = new PlatformStatistics(dbManager);
    }

    /** Returns the UserAchievementStorage implementation. */
    public UserAchievementStorage achievements() {
        return achievements;
    }

    /** Returns the DailyPickStorage implementation. */
    public DailyPickStorage dailyPicks() {
        return dailyPicks;
    }

    /** Returns the PlatformStatsStorage implementation. */
    public PlatformStatsStorage platformStats() {
        return platformStats;
    }

    // ========================================================================
    // ACHIEVEMENTS - H2 implementation of UserAchievementStorage
    // ========================================================================

    /** H2 implementation of UserAchievementStorage. */
    public static class Achievements extends AbstractH2Storage implements UserAchievementStorage {

        public Achievements(DatabaseManager dbManager) {
            super(dbManager);
        }

        @Override
        protected void ensureSchema() {
            // Table created by DatabaseManager
        }

        @Override
        public void save(UserAchievement achievement) {
            String sql = """
                    MERGE INTO user_achievements (id, user_id, achievement, unlocked_at)
                    KEY (user_id, achievement)
                    VALUES (?, ?, ?, ?)
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, achievement.id());
                ps.setObject(2, achievement.userId());
                ps.setString(3, achievement.achievement().name());
                ps.setTimestamp(4, Timestamp.from(achievement.unlockedAt()));
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException("Failed to save achievement", e);
            }
        }

        @Override
        public List<UserAchievement> getUnlocked(UUID userId) {
            String sql = """
                    SELECT id, user_id, achievement, unlocked_at
                    FROM user_achievements
                    WHERE user_id = ?
                    ORDER BY unlocked_at DESC
                    """;

            List<UserAchievement> results = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, userId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to get unlocked achievements", e);
            }

            return results;
        }

        @Override
        public boolean hasAchievement(UUID userId, Achievement achievement) {
            String sql = """
                    SELECT COUNT(*) FROM user_achievements
                    WHERE user_id = ? AND achievement = ?
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, userId);
                ps.setString(2, achievement.name());

                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to check achievement", e);
            }
        }

        @Override
        public int countUnlocked(UUID userId) {
            String sql = "SELECT COUNT(*) FROM user_achievements WHERE user_id = ?";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, userId);

                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to count achievements", e);
            }
        }

        private UserAchievement mapRow(ResultSet rs) throws SQLException {
            return UserAchievement.of(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    Achievement.valueOf(rs.getString("achievement")),
                    rs.getTimestamp("unlocked_at").toInstant());
        }
    }

    // ========================================================================
    // DAILY PICKS - H2 implementation of DailyPickStorage
    // ========================================================================

    /**
     * H2 database implementation of DailyPickStorage.
     * Tracks when users view their daily picks to prevent duplicate displays.
     */
    public static class DailyPicks extends AbstractH2Storage implements DailyPickStorage {

        public DailyPicks(DatabaseManager dbManager) {
            super(dbManager);
        }

        @Override
        public void markViewed(UUID userId, LocalDate date) {
            String sql = """
                    MERGE INTO daily_pick_views (user_id, viewed_date, viewed_at)
                    VALUES (?, ?, ?)
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, userId);
                stmt.setDate(2, Date.valueOf(date));
                stmt.setTimestamp(3, Timestamp.from(Instant.now()));

                stmt.executeUpdate();

            } catch (SQLException e) {
                throw new StorageException("Failed to mark daily pick viewed: " + userId, e);
            }
        }

        @Override
        public boolean hasViewed(UUID userId, LocalDate date) {
            String sql = """
                    SELECT COUNT(*) FROM daily_pick_views
                    WHERE user_id = ? AND viewed_date = ?
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, userId);
                stmt.setDate(2, Date.valueOf(date));

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }

            } catch (SQLException e) {
                throw new StorageException("Failed to check daily pick view: " + userId, e);
            }
        }

        @Override
        public int cleanup(LocalDate before) {
            String sql = "DELETE FROM daily_pick_views WHERE viewed_date < ?";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setDate(1, Date.valueOf(before));
                return stmt.executeUpdate();

            } catch (SQLException e) {
                throw new StorageException("Failed to cleanup old daily pick views", e);
            }
        }
    }

    // ========================================================================
    // PLATFORM STATS - H2 implementation of PlatformStatsStorage
    // ========================================================================

    /** H2 implementation of PlatformStatsStorage. */
    public static class PlatformStatistics extends AbstractH2Storage implements PlatformStatsStorage {

        public PlatformStatistics(DatabaseManager dbManager) {
            super(dbManager);
        }

        @Override
        public void save(PlatformStats stats) {
            String sql = """
                    INSERT INTO platform_stats (id, computed_at, total_active_users,
                        avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, stats.id());
                stmt.setTimestamp(2, Timestamp.from(stats.computedAt()));
                stmt.setInt(3, stats.totalActiveUsers());
                stmt.setDouble(4, stats.avgLikesReceived());
                stmt.setDouble(5, stats.avgLikesGiven());
                stmt.setDouble(6, stats.avgMatchRate());
                stmt.setDouble(7, stats.avgLikeRatio());

                stmt.executeUpdate();

            } catch (SQLException e) {
                throw new StorageException("Failed to save platform stats", e);
            }
        }

        @Override
        public Optional<PlatformStats> getLatest() {
            String sql = """
                SELECT id, computed_at, total_active_users,
                    avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
                FROM platform_stats
                ORDER BY computed_at DESC
                LIMIT 1
                """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapStats(rs));
                    }
                    return Optional.empty();
                }

            } catch (SQLException e) {
                throw new StorageException("Failed to get latest platform stats", e);
            }
        }

        @Override
        public List<PlatformStats> getHistory(int limit) {
            String sql = """
                SELECT id, computed_at, total_active_users,
                    avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
                FROM platform_stats
                ORDER BY computed_at DESC
                LIMIT ?
                """;
            List<PlatformStats> history = new ArrayList<>();

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        history.add(mapStats(rs));
                    }
                }
                return history;

            } catch (SQLException e) {
                throw new StorageException("Failed to get platform stats history", e);
            }
        }

        private PlatformStats mapStats(ResultSet rs) throws SQLException {
            return new PlatformStats(
                    rs.getObject("id", UUID.class),
                    rs.getTimestamp("computed_at").toInstant(),
                    rs.getInt("total_active_users"),
                    rs.getDouble("avg_likes_received"),
                    rs.getDouble("avg_likes_given"),
                    rs.getDouble("avg_match_rate"),
                    rs.getDouble("avg_like_ratio"));
        }
    }
}
