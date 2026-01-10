package datingapp.storage;

import datingapp.core.Achievement;
import datingapp.core.UserAchievement;
import datingapp.core.UserAchievementStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** H2 implementation of UserAchievementStorage. */
public class H2UserAchievementStorage implements UserAchievementStorage {

  private final DatabaseManager dbManager;

  public H2UserAchievementStorage(DatabaseManager dbManager) {
    this.dbManager = Objects.requireNonNull(dbManager);
  }

  @Override
  public void save(UserAchievement achievement) {
    String sql =
        """
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
    String sql =
        """
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
    String sql =
        """
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
