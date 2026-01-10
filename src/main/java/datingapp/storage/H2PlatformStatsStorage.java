package datingapp.storage;

import datingapp.core.PlatformStats;
import datingapp.core.PlatformStatsStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** H2 implementation of PlatformStatsStorage. */
public class H2PlatformStatsStorage implements PlatformStatsStorage {

  private final DatabaseManager dbManager;

  public H2PlatformStatsStorage(DatabaseManager dbManager) {
    this.dbManager = dbManager;
  }

  @Override
  public void save(PlatformStats stats) {
    String sql =
        """
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
    String sql = "SELECT * FROM platform_stats ORDER BY computed_at DESC LIMIT 1";

    try (Connection conn = dbManager.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      ResultSet rs = stmt.executeQuery();

      if (rs.next()) {
        return Optional.of(mapStats(rs));
      }
      return Optional.empty();

    } catch (SQLException e) {
      throw new StorageException("Failed to get latest platform stats", e);
    }
  }

  @Override
  public List<PlatformStats> getHistory(int limit) {
    String sql = "SELECT * FROM platform_stats ORDER BY computed_at DESC LIMIT ?";
    List<PlatformStats> history = new ArrayList<>();

    try (Connection conn = dbManager.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setInt(1, limit);
      ResultSet rs = stmt.executeQuery();

      while (rs.next()) {
        history.add(mapStats(rs));
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
