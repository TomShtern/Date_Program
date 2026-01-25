package datingapp.storage;

import datingapp.core.ProfileNoteStorage;
import datingapp.core.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * H2 database implementation of ProfileNoteStorage. Stores private notes users make about other
 * profiles.
 */
public class H2ProfileNoteStorage extends AbstractH2Storage implements ProfileNoteStorage {

    private static final Logger logger = LoggerFactory.getLogger(H2ProfileNoteStorage.class);

    public H2ProfileNoteStorage(DatabaseManager dbManager) {
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

        List<User.ProfileNote> notes = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, authorId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    notes.add(new User.ProfileNote(
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
        return notes;
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
