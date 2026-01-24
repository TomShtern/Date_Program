package datingapp.storage;

import datingapp.core.BlockStorage;
import datingapp.core.UserInteractions.Block;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** H2 implementation of BlockStorage. */
public class H2BlockStorage extends AbstractH2Storage implements BlockStorage {

    public H2BlockStorage(DatabaseManager dbManager) {
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

            ResultSet rs = stmt.executeQuery();
            return rs.next();

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

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                blockedIds.add(rs.getObject(1, UUID.class));
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
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

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
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count blocks received", e);
        }
    }
}
