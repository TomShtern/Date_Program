package datingapp.storage;

import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.BlockStorage;
import datingapp.core.UserInteractions.Report;
import datingapp.core.UserInteractions.ReportStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * H2 storage implementation for moderation-related entities (blocks and reports).
 * Groups related CRUD operations for content moderation workflows.
 *
 * <p>This consolidated class provides access to both BlockStorage and ReportStorage
 * implementations via nested classes.
 */
public final class H2ModerationStorage {

    private final Blocks blocks;
    private final Reports reports;

    public H2ModerationStorage(DatabaseManager dbManager) {
        this.blocks = new Blocks(dbManager);
        this.reports = new Reports(dbManager);
    }

    /** Returns the BlockStorage implementation. */
    public BlockStorage blocks() {
        return blocks;
    }

    /** Returns the ReportStorage implementation. */
    public ReportStorage reports() {
        return reports;
    }

    // ========================================================================
    // BLOCKS - H2 implementation of BlockStorage
    // ========================================================================

    /** H2 implementation of BlockStorage. */
    public static class Blocks extends AbstractH2Storage implements BlockStorage {

        public Blocks(DatabaseManager dbManager) {
            super(dbManager);
            ensureSchema();
        }

        @Override
        protected void ensureSchema() {
            String sql = """
                    CREATE TABLE IF NOT EXISTS blocks (
                        id UUID PRIMARY KEY,
                        blocker_id UUID NOT NULL,
                        blocked_id UUID NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        UNIQUE (blocker_id, blocked_id)
                    )
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException("Failed to create blocks table", e);
            }

            // Create indexes for efficient lookups
            String indexSql1 = "CREATE INDEX IF NOT EXISTS idx_blocks_blocker ON blocks(blocker_id)";
            String indexSql2 = "CREATE INDEX IF NOT EXISTS idx_blocks_blocked ON blocks(blocked_id)";

            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement(indexSql1)) {
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(indexSql2)) {
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                throw new StorageException("Failed to create block indexes", e);
            }
        }

        @Override
        public void save(Block block) {
            String sql = """
                    INSERT INTO blocks (id, blocker_id, blocked_id, created_at)
                    VALUES (?, ?, ?, ?)
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, block.id());
                stmt.setObject(2, block.blockerId());
                stmt.setObject(3, block.blockedId());
                stmt.setTimestamp(4, Timestamp.from(block.createdAt()));

                stmt.executeUpdate();

            } catch (SQLException e) {
                throw new StorageException("Failed to save block", e);
            }
        }

        @Override
        public boolean isBlocked(UUID userA, UUID userB) {
            // Check if either user has blocked the other
            String sql = """
                    SELECT 1 FROM blocks
                    WHERE (blocker_id = ? AND blocked_id = ?)
                       OR (blocker_id = ? AND blocked_id = ?)
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, userA);
                stmt.setObject(2, userB);
                stmt.setObject(3, userB);
                stmt.setObject(4, userA);

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }

            } catch (SQLException e) {
                throw new StorageException("Failed to check block status", e);
            }
        }

        @Override
        public Set<UUID> getBlockedUserIds(UUID userId) {
            // Returns all users that this user blocked OR who blocked this user
            String sql = """
                    SELECT blocked_id FROM blocks WHERE blocker_id = ?
                    UNION
                    SELECT blocker_id FROM blocks WHERE blocked_id = ?
                    """;

            Set<UUID> blockedIds = new HashSet<>();

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, userId);
                stmt.setObject(2, userId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        blockedIds.add(rs.getObject(1, UUID.class));
                    }
                }

                return blockedIds;

            } catch (SQLException e) {
                throw new StorageException("Failed to get blocked user IDs", e);
            }
        }

        // === Statistics Methods (Phase 0.5b) ===

        @Override
        public int countBlocksGiven(UUID userId) {
            String sql = "SELECT COUNT(*) FROM blocks WHERE blocker_id = ?";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }

            } catch (SQLException e) {
                throw new StorageException("Failed to count blocks given", e);
            }
        }

        @Override
        public int countBlocksReceived(UUID userId) {
            String sql = "SELECT COUNT(*) FROM blocks WHERE blocked_id = ?";

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setObject(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }

            } catch (SQLException e) {
                throw new StorageException("Failed to count blocks received", e);
            }
        }
    }

    // ========================================================================
    // REPORTS - H2 implementation of ReportStorage
    // ========================================================================

    /** H2 implementation of ReportStorage. */
    public static class Reports extends AbstractH2Storage implements ReportStorage {

        public Reports(DatabaseManager dbManager) {
            super(dbManager);
            ensureSchema();
        }

        @Override
        protected void ensureSchema() {
            String sql = """
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
            String sql = """
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
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }

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

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }

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
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        reports.add(new Report(
                                rs.getObject("id", UUID.class),
                                rs.getObject("reporter_id", UUID.class),
                                rs.getObject("reported_user_id", UUID.class),
                                Report.Reason.valueOf(rs.getString("reason")),
                                rs.getString("description"),
                                rs.getTimestamp("created_at").toInstant()));
                    }
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
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }

            } catch (SQLException e) {
                throw new StorageException("Failed to count reports by user", e);
            }
        }
    }
}
