package datingapp.storage;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import datingapp.core.DailyPickStorage;

/**
 * H2 database implementation of DailyPickStorage.
 * Tracks when users view their daily picks to prevent duplicate displays.
 */
public class H2DailyPickViewStorage implements DailyPickStorage {

    private final DatabaseManager dbManager;

    public H2DailyPickViewStorage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
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

            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

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
