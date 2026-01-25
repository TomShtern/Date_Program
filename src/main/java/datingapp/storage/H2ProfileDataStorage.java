package datingapp.storage;

import datingapp.core.User;
import datingapp.core.User.ProfileNoteStorage;
import datingapp.core.User.ProfileViewStorage;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * H2 storage implementation for profile-related ancillary data (notes, views).
 * Groups related CRUD operations for profile metadata.
 *
 * <p>This consolidated class provides access to both ProfileNoteStorage and
 * ProfileViewStorage implementations via nested classes.
 */
public final class H2ProfileDataStorage {

    private final Notes notes;
    private final Views views;

    public H2ProfileDataStorage(DatabaseManager dbManager) {
        this.notes = new Notes(dbManager);
        this.views = new Views(dbManager);
    }

    /** Returns the ProfileNoteStorage implementation. */
    public ProfileNoteStorage notes() {
        return notes;
    }

    /** Returns the ProfileViewStorage implementation. */
    public ProfileViewStorage views() {
        return views;
    }

    // ========================================================================
    // NOTES - H2 implementation of ProfileNoteStorage
    // ========================================================================

    /**
     * H2 database implementation of ProfileNoteStorage.
     * Stores private notes users make about other profiles.
     */
    public static class Notes extends AbstractH2Storage implements ProfileNoteStorage {

        private static final Logger logger = LoggerFactory.getLogger(Notes.class);

        public Notes(DatabaseManager dbManager) {
            super(dbManager);
            ensureSchema();
        }

        @Override
        protected void ensureSchema() {
            String sql = """
                    CREATE TABLE IF NOT EXISTS profile_notes (
                        author_id UUID NOT NULL,
                        subject_id UUID NOT NULL,
                        content VARCHAR(500) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        PRIMARY KEY (author_id, subject_id)
                    );
                    CREATE INDEX IF NOT EXISTS idx_profile_notes_author ON profile_notes(author_id);
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.execute();
            } catch (SQLException e) {
                throw new StorageException("Failed to initialize profile_notes table", e);
            }
        }

        @Override
        public void save(User.ProfileNote note) {
            String sql = """
                    MERGE INTO profile_notes (author_id, subject_id, content, created_at, updated_at)
                    KEY (author_id, subject_id)
                    VALUES (?, ?, ?, ?, ?)
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, note.authorId());
                stmt.setObject(2, note.subjectId());
                stmt.setString(3, note.content());
                stmt.setTimestamp(4, Timestamp.from(note.createdAt()));
                stmt.setTimestamp(5, Timestamp.from(note.updatedAt()));
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException("Failed to save profile note", e);
            }
        }

        @Override
        public Optional<User.ProfileNote> get(UUID authorId, UUID subjectId) {
            String sql = """
                    SELECT content, created_at, updated_at
                    FROM profile_notes
                    WHERE author_id = ? AND subject_id = ?
                    """;

            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, authorId);
                stmt.setObject(2, subjectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new User.ProfileNote(
                                authorId,
                                subjectId,
                                rs.getString("content"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant()));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Failed to get profile note: {}", e.getMessage());
            }
            return Optional.empty();
        }

        @Override
        public List<User.ProfileNote> getAllByAuthor(UUID authorId) {
            String sql = """
                    SELECT subject_id, content, created_at, updated_at
                    FROM profile_notes
                    WHERE author_id = ?
                    ORDER BY updated_at DESC
                    """;

            List<User.ProfileNote> notesList = new ArrayList<>();
            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, authorId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        notesList.add(new User.ProfileNote(
                                authorId,
                                rs.getObject("subject_id", UUID.class),
                                rs.getString("content"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant()));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Failed to get notes by author: {}", e.getMessage());
            }
            return notesList;
        }

        @Override
        public boolean delete(UUID authorId, UUID subjectId) {
            String sql = "DELETE FROM profile_notes WHERE author_id = ? AND subject_id = ?";
            try (Connection conn = dbManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, authorId);
                stmt.setObject(2, subjectId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.warn("Failed to delete profile note: {}", e.getMessage());
                return false;
            }
        }
    }

    // ========================================================================
    // VIEWS - H2 implementation of ProfileViewStorage
    // ========================================================================

    /**
     * H2 database implementation of ProfileViewStorage.
     * Tracks who viewed which profile and when.
     */
    public static class Views extends AbstractH2Storage implements ProfileViewStorage {

        private static final Logger logger = LoggerFactory.getLogger(Views.class);

        public Views(DatabaseManager dbManager) {
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
}
