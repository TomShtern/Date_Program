package datingapp.storage;

import datingapp.core.User.ProfileViewStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** H2 database implementation of ProfileViewStorage. Tracks who viewed which profile and when. */
public class H2ProfileViewStorage extends AbstractH2Storage implements ProfileViewStorage {

    private static final Logger logger = LoggerFactory.getLogger(H2ProfileViewStorage.class);

    public H2ProfileViewStorage(DatabaseManager dbManager) {
        super(dbManager);
        ensureSchema();
    }

    @Override
    protected void ensureSchema() {
        String sql = """
                CREATE TABLE IF NOT EXISTS profile_views (
                    viewer_id UUID NOT NULL,
                    viewed_id UUID NOT NULL,
                    viewed_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (viewer_id, viewed_id, viewed_at)
                );
                CREATE INDEX IF NOT EXISTS idx_profile_views_viewed_id ON profile_views(viewed_id);
                CREATE INDEX IF NOT EXISTS idx_profile_views_viewed_at ON
                    profile_views(viewed_at DESC);
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            throw new StorageException("Failed to initialize profile_views table", e);
        }
    }

    @Override
    public void recordView(UUID viewerId, UUID viewedId) {
        if (viewerId.equals(viewedId)) {
            return; // Don't record self-views
        }

        String sql = "INSERT INTO profile_views (viewer_id, viewed_id, viewed_at) VALUES (?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, viewerId);
            stmt.setObject(2, viewedId);
            stmt.setTimestamp(3, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to record profile view: {}", e.getMessage());
        }
    }

    @Override
    public int getViewCount(UUID userId) {
        String sql = "SELECT COUNT(*) FROM profile_views WHERE viewed_id = ?";
        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to get view count: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public int getUniqueViewerCount(UUID userId) {
        String sql = "SELECT COUNT(DISTINCT viewer_id) FROM profile_views WHERE viewed_id = ?";
        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to get unique viewer count: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public List<UUID> getRecentViewers(UUID userId, int limit) {
        // Query that works with H2's GROUP BY semantics
        String simpleSql = """
                SELECT viewer_id, MAX(viewed_at) as last_view
                FROM profile_views
                WHERE viewed_id = ?
                GROUP BY viewer_id
                ORDER BY last_view DESC
                LIMIT ?
                """;

        List<UUID> viewers = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(simpleSql)) {
            stmt.setObject(1, userId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    viewers.add(rs.getObject("viewer_id", UUID.class));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to get recent viewers: {}", e.getMessage());
        }
        return viewers;
    }

    @Override
    public boolean hasViewed(UUID viewerId, UUID viewedId) {
        String sql = "SELECT 1 FROM profile_views WHERE viewer_id = ? AND viewed_id = ? LIMIT 1";
        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, viewerId);
            stmt.setObject(2, viewedId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warn("Failed to check view history: {}", e.getMessage());
        }
        return false;
    }
}
