package datingapp.storage;

import datingapp.core.Report;
import datingapp.core.ReportStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** H2 implementation of ReportStorage. */
public class H2ReportStorage implements ReportStorage {

    private final DatabaseManager dbManager;

    public H2ReportStorage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        createTable();
    }

    private void createTable() {
        String sql =
                """
                CREATE TABLE IF NOT EXISTS reports (
                    id UUID PRIMARY KEY,
                    reporter_id UUID NOT NULL,
                    reported_user_id UUID NOT NULL,
                    reason VARCHAR(50) NOT NULL,
                    description VARCHAR(500),
                    created_at TIMESTAMP NOT NULL,
                    UNIQUE (reporter_id, reported_user_id)
                )
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to create reports table", e);
        }

        // Create index for efficient lookups
        String indexSql = "CREATE INDEX IF NOT EXISTS idx_reports_reported ON reports(reported_user_id)";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(indexSql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to create reports index", e);
        }
    }

    @Override
    public void save(Report report) {
        String sql =
                """
                INSERT INTO reports (id, reporter_id, reported_user_id, reason, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, report.id());
            stmt.setObject(2, report.reporterId());
            stmt.setObject(3, report.reportedUserId());
            stmt.setString(4, report.reason().name());
            stmt.setString(5, report.description());
            stmt.setTimestamp(6, Timestamp.from(report.createdAt()));

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save report", e);
        }
    }

    @Override
    public int countReportsAgainst(UUID userId) {
        String sql = "SELECT COUNT(*) FROM reports WHERE reported_user_id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count reports", e);
        }
    }

    @Override
    public boolean hasReported(UUID reporterId, UUID reportedUserId) {
        String sql = "SELECT 1 FROM reports WHERE reporter_id = ? AND reported_user_id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, reporterId);
            stmt.setObject(2, reportedUserId);

            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            throw new StorageException("Failed to check report status", e);
        }
    }

    @Override
    public List<Report> getReportsAgainst(UUID userId) {
        String sql = "SELECT id, reporter_id, reported_user_id, reason, description, created_at "
                + "FROM reports WHERE reported_user_id = ? ORDER BY created_at DESC";
        List<Report> reports = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                reports.add(new Report(
                        rs.getObject("id", UUID.class),
                        rs.getObject("reporter_id", UUID.class),
                        rs.getObject("reported_user_id", UUID.class),
                        Report.Reason.valueOf(rs.getString("reason")),
                        rs.getString("description"),
                        rs.getTimestamp("created_at").toInstant()));
            }

            return reports;

        } catch (SQLException e) {
            throw new StorageException("Failed to get reports", e);
        }
    }

    // === Statistics Methods (Phase 0.5b) ===

    @Override
    public int countReportsBy(UUID userId) {
        String sql = "SELECT COUNT(*) FROM reports WHERE reporter_id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count reports by user", e);
        }
    }
}
