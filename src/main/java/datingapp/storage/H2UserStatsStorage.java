package datingapp.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import datingapp.core.UserStats;
import datingapp.core.UserStatsStorage;

/**
 * H2 implementation of UserStatsStorage.
 */
public class H2UserStatsStorage implements UserStatsStorage {

    private final DatabaseManager dbManager;

    public H2UserStatsStorage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void save(UserStats stats) {
        String sql = """
                INSERT INTO user_stats (id, user_id, computed_at,
                    total_swipes_given, likes_given, passes_given, like_ratio,
                    total_swipes_received, likes_received, passes_received, incoming_like_ratio,
                    total_matches, active_matches, match_rate,
                    blocks_given, blocks_received, reports_given, reports_received,
                    reciprocity_score, selectiveness_score, attractiveness_score)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, stats.id());
            stmt.setObject(2, stats.userId());
            stmt.setTimestamp(3, Timestamp.from(stats.computedAt()));
            stmt.setInt(4, stats.totalSwipesGiven());
            stmt.setInt(5, stats.likesGiven());
            stmt.setInt(6, stats.passesGiven());
            stmt.setDouble(7, stats.likeRatio());
            stmt.setInt(8, stats.totalSwipesReceived());
            stmt.setInt(9, stats.likesReceived());
            stmt.setInt(10, stats.passesReceived());
            stmt.setDouble(11, stats.incomingLikeRatio());
            stmt.setInt(12, stats.totalMatches());
            stmt.setInt(13, stats.activeMatches());
            stmt.setDouble(14, stats.matchRate());
            stmt.setInt(15, stats.blocksGiven());
            stmt.setInt(16, stats.blocksReceived());
            stmt.setInt(17, stats.reportsGiven());
            stmt.setInt(18, stats.reportsReceived());
            stmt.setDouble(19, stats.reciprocityScore());
            stmt.setDouble(20, stats.selectivenessScore());
            stmt.setDouble(21, stats.attractivenessScore());

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save user stats: " + stats.id(), e);
        }
    }

    @Override
    public Optional<UserStats> getLatest(UUID userId) {
        String sql = """
                SELECT * FROM user_stats
                WHERE user_id = ?
                ORDER BY computed_at DESC
                LIMIT 1
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapStats(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new StorageException("Failed to get latest stats for user: " + userId, e);
        }
    }

    @Override
    public List<UserStats> getHistory(UUID userId, int limit) {
        String sql = """
                SELECT * FROM user_stats
                WHERE user_id = ?
                ORDER BY computed_at DESC
                LIMIT ?
                """;
        List<UserStats> history = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                history.add(mapStats(rs));
            }
            return history;

        } catch (SQLException e) {
            throw new StorageException("Failed to get stats history for user: " + userId, e);
        }
    }

    @Override
    public List<UserStats> getAllLatestStats() {
        // Get the most recent stats snapshot for each user
        String sql = """
                SELECT s.* FROM user_stats s
                INNER JOIN (
                    SELECT user_id, MAX(computed_at) as max_date
                    FROM user_stats
                    GROUP BY user_id
                ) latest ON s.user_id = latest.user_id AND s.computed_at = latest.max_date
                """;
        List<UserStats> allStats = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                allStats.add(mapStats(rs));
            }
            return allStats;

        } catch (SQLException e) {
            throw new StorageException("Failed to get all latest stats", e);
        }
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        String sql = "DELETE FROM user_stats WHERE computed_at < ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(cutoff));
            return stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to delete old stats", e);
        }
    }

    private UserStats mapStats(ResultSet rs) throws SQLException {
        return new UserStats(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getTimestamp("computed_at").toInstant(),
                rs.getInt("total_swipes_given"),
                rs.getInt("likes_given"),
                rs.getInt("passes_given"),
                rs.getDouble("like_ratio"),
                rs.getInt("total_swipes_received"),
                rs.getInt("likes_received"),
                rs.getInt("passes_received"),
                rs.getDouble("incoming_like_ratio"),
                rs.getInt("total_matches"),
                rs.getInt("active_matches"),
                rs.getDouble("match_rate"),
                rs.getInt("blocks_given"),
                rs.getInt("blocks_received"),
                rs.getInt("reports_given"),
                rs.getInt("reports_received"),
                rs.getDouble("reciprocity_score"),
                rs.getDouble("selectiveness_score"),
                rs.getDouble("attractiveness_score"));
    }
}
