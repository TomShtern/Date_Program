package datingapp.storage;

import datingapp.core.ArchiveReason;
import datingapp.core.Match;
import datingapp.core.MatchStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** H2 implementation of MatchStorage. */
public class H2MatchStorage implements MatchStorage {

    private final DatabaseManager dbManager;

    public H2MatchStorage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        ensureSchema();
    }

    /** Ensures the schema has the new columns for state support. */
    private void ensureSchema() {
        // Add state column if not exists
        addColumnIfNotExists("state", "VARCHAR(20) DEFAULT 'ACTIVE'");
        addColumnIfNotExists("ended_at", "TIMESTAMP");
        addColumnIfNotExists("ended_by", "UUID");
        addColumnIfNotExists("end_reason", "VARCHAR(20)");
    }

    private void addColumnIfNotExists(String columnName, String columnDef) {
        String checkSql =
                """
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_NAME = 'MATCHES' AND COLUMN_NAME = ?
        """;
        String addSql = "ALTER TABLE matches ADD COLUMN " + columnName + " " + columnDef;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setString(1, columnName.toUpperCase());
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement addStmt = conn.prepareStatement(addSql)) {
                    addStmt.executeUpdate();
                }
            }

        } catch (SQLException e) {
            // Column might already exist, ignore
        }
    }

    @Override
    public void save(Match match) {
        String sql =
                """
        INSERT INTO matches (id, user_a, user_b, created_at, state, ended_at, ended_by, end_reason)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, match.getId());
            stmt.setObject(2, match.getUserA());
            stmt.setObject(3, match.getUserB());
            stmt.setTimestamp(4, Timestamp.from(match.getCreatedAt()));
            stmt.setString(5, match.getState().name());

            if (match.getEndedAt() != null) {
                stmt.setTimestamp(6, Timestamp.from(match.getEndedAt()));
            } else {
                stmt.setNull(6, Types.TIMESTAMP);
            }

            if (match.getEndedBy() != null) {
                stmt.setObject(7, match.getEndedBy());
            } else {
                stmt.setNull(7, Types.OTHER);
            }

            if (match.getEndReason() != null) {
                stmt.setString(8, match.getEndReason().name());
            } else {
                stmt.setNull(8, Types.VARCHAR);
            }

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save match: " + match.getId(), e);
        }
    }

    @Override
    public void update(Match match) {
        String sql =
                """
        UPDATE matches SET state = ?, ended_at = ?, ended_by = ?, end_reason = ?
        WHERE id = ?
        """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, match.getState().name());

            if (match.getEndedAt() != null) {
                stmt.setTimestamp(2, Timestamp.from(match.getEndedAt()));
            } else {
                stmt.setNull(2, Types.TIMESTAMP);
            }

            if (match.getEndedBy() != null) {
                stmt.setObject(3, match.getEndedBy());
            } else {
                stmt.setNull(3, Types.OTHER);
            }

            if (match.getEndReason() != null) {
                stmt.setString(4, match.getEndReason().name());
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }

            stmt.setString(5, match.getId());

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to update match: " + match.getId(), e);
        }
    }

    @Override
    public Optional<Match> get(String matchId) {
        String sql = "SELECT * FROM matches WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, matchId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new StorageException("Failed to get match: " + matchId, e);
        }
    }

    @Override
    public boolean exists(String matchId) {
        String sql = "SELECT 1 FROM matches WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, matchId);
            ResultSet rs = stmt.executeQuery();

            return rs.next();

        } catch (SQLException e) {
            throw new StorageException("Failed to check match exists", e);
        }
    }

    @Override
    public List<Match> getActiveMatchesFor(UUID userId) {
        String sql =
                """
        SELECT * FROM matches
        WHERE (user_a = ? OR user_b = ?)
        AND state = 'ACTIVE'
        """;
        return queryMatches(sql, userId);
    }

    @Override
    public List<Match> getAllMatchesFor(UUID userId) {
        String sql = "SELECT * FROM matches WHERE user_a = ? OR user_b = ?";
        return queryMatches(sql, userId);
    }

    private List<Match> queryMatches(String sql, UUID userId) {
        List<Match> matches = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setObject(2, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                matches.add(mapRow(rs));
            }
            return matches;

        } catch (SQLException e) {
            throw new StorageException("Failed to get matches for user: " + userId, e);
        }
    }

    private Match mapRow(ResultSet rs) throws SQLException {
        String stateStr = rs.getString("state");
        Match.State state = stateStr != null ? Match.State.valueOf(stateStr) : Match.State.ACTIVE;

        Timestamp endedAtTs = rs.getTimestamp("ended_at");
        UUID endedBy = rs.getObject("ended_by", UUID.class);

        return new Match(
                rs.getString("id"),
                rs.getObject("user_a", UUID.class),
                rs.getObject("user_b", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                state,
                endedAtTs != null ? endedAtTs.toInstant() : null,
                endedBy,
                rs.getString("end_reason") != null ? ArchiveReason.valueOf(rs.getString("end_reason")) : null);
    }

    // === Undo Methods (Phase 1) ===

    @Override
    public void delete(String matchId) {
        String sql = "DELETE FROM matches WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, matchId);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new StorageException("Match not found for deletion: " + matchId);
            }

        } catch (SQLException e) {
            throw new StorageException("Failed to delete match: " + matchId, e);
        }
    }
}
