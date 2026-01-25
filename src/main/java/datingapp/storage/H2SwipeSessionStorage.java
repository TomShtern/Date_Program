package datingapp.storage;

import datingapp.core.SwipeSession;
import datingapp.core.SwipeSession.SwipeSessionStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** H2 implementation of SwipeSessionStorage. */
public class H2SwipeSessionStorage extends AbstractH2Storage implements SwipeSessionStorage {

    public H2SwipeSessionStorage(DatabaseManager dbManager) {
        super(dbManager);
    }

    @Override
    protected void ensureSchema() {
        // Table created by DatabaseManager
    }

    @Override
    public void save(SwipeSession session) {
        String sql = """
                MERGE INTO swipe_sessions (id, user_id, started_at, last_activity_at, ended_at,
                                           state, swipe_count, like_count, pass_count, match_count)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, session.getId());
            stmt.setObject(2, session.getUserId());
            stmt.setTimestamp(3, Timestamp.from(session.getStartedAt()));
            stmt.setTimestamp(4, Timestamp.from(session.getLastActivityAt()));
            if (session.getEndedAt() != null) {
                stmt.setTimestamp(5, Timestamp.from(session.getEndedAt()));
            } else {
                stmt.setNull(5, Types.TIMESTAMP);
            }
            stmt.setString(6, session.getState().name());
            stmt.setInt(7, session.getSwipeCount());
            stmt.setInt(8, session.getLikeCount());
            stmt.setInt(9, session.getPassCount());
            stmt.setInt(10, session.getMatchCount());

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save session: " + session.getId(), e);
        }
    }

    @Override
    public Optional<SwipeSession> get(UUID sessionId) {
        String sql = "SELECT id, user_id, started_at, last_activity_at, ended_at, "
                + "state, swipe_count, like_count, pass_count, match_count "
                + "FROM swipe_sessions WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, sessionId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapSession(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new StorageException("Failed to get session: " + sessionId, e);
        }
    }

    @Override
    public Optional<SwipeSession> getActiveSession(UUID userId) {
        String sql = "SELECT id, user_id, started_at, last_activity_at, ended_at, "
                + "state, swipe_count, like_count, pass_count, match_count "
                + "FROM swipe_sessions WHERE user_id = ? AND state = 'ACTIVE' LIMIT 1";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapSession(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new StorageException("Failed to get active session for user: " + userId, e);
        }
    }

    @Override
    public List<SwipeSession> getSessionsFor(UUID userId, int limit) {
        String sql = "SELECT id, user_id, started_at, last_activity_at, ended_at, "
                + "state, swipe_count, like_count, pass_count, match_count "
                + "FROM swipe_sessions WHERE user_id = ? ORDER BY started_at DESC LIMIT ?";
        List<SwipeSession> sessions = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                sessions.add(mapSession(rs));
            }
            return sessions;

        } catch (SQLException e) {
            throw new StorageException("Failed to get sessions for user: " + userId, e);
        }
    }

    @Override
    public List<SwipeSession> getSessionsInRange(UUID userId, Instant start, Instant end) {
        String sql = "SELECT * FROM swipe_sessions WHERE user_id = ? AND started_at >= ? AND "
                + "started_at <= ? ORDER BY started_at DESC";
        List<SwipeSession> sessions = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setTimestamp(2, Timestamp.from(start));
            stmt.setTimestamp(3, Timestamp.from(end));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                sessions.add(mapSession(rs));
            }
            return sessions;

        } catch (SQLException e) {
            throw new StorageException("Failed to get sessions in range for user: " + userId, e);
        }
    }

    @Override
    public SessionAggregates getAggregates(UUID userId) {
        String sql = """
                SELECT
                    COUNT(*) as total_sessions,
                    COALESCE(SUM(swipe_count), 0) as total_swipes,
                    COALESCE(SUM(like_count), 0) as total_likes,
                    COALESCE(SUM(pass_count), 0) as total_passes,
                    COALESCE(SUM(match_count), 0) as total_matches,
                    COALESCE(AVG(
                        CASE WHEN ended_at IS NOT NULL
                        THEN DATEDIFF('SECOND', started_at, ended_at)
                        ELSE 0 END
                    ), 0) as avg_duration_seconds
                FROM swipe_sessions
                WHERE user_id = ?
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int totalSessions = rs.getInt("total_sessions");
                int totalSwipes = rs.getInt("total_swipes");
                int totalLikes = rs.getInt("total_likes");
                int totalPasses = rs.getInt("total_passes");
                int totalMatches = rs.getInt("total_matches");
                double avgDurationSeconds = rs.getDouble("avg_duration_seconds");

                double avgSwipesPerSession = totalSessions > 0 ? (double) totalSwipes / totalSessions : 0.0;
                // Calculate avg velocity: if avg duration is at least 60s, use it; otherwise
                // assume 60s
                double avgSwipeVelocity = avgDurationSeconds >= 60
                        ? avgSwipesPerSession / (avgDurationSeconds / 60.0)
                        : avgSwipesPerSession;

                return new SessionAggregates(
                        totalSessions,
                        totalSwipes,
                        totalLikes,
                        totalPasses,
                        totalMatches,
                        avgDurationSeconds,
                        avgSwipesPerSession,
                        avgSwipeVelocity);
            }
            return SessionAggregates.empty();

        } catch (SQLException e) {
            throw new StorageException("Failed to get aggregates for user: " + userId, e);
        }
    }

    @Override
    public int endStaleSessions(Duration timeout) {
        Instant cutoff = Instant.now().minus(timeout);
        String sql = """
                UPDATE swipe_sessions
                SET state = 'COMPLETED', ended_at = ?
                WHERE state = 'ACTIVE' AND last_activity_at < ?
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            Timestamp now = Timestamp.from(Instant.now());
            stmt.setTimestamp(1, now);
            stmt.setTimestamp(2, Timestamp.from(cutoff));

            return stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to end stale sessions", e);
        }
    }

    private SwipeSession mapSession(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID userId = rs.getObject("user_id", UUID.class);
        Instant startedAt = rs.getTimestamp("started_at").toInstant();
        Instant lastActivityAt = rs.getTimestamp("last_activity_at").toInstant();
        Timestamp endedAtTs = rs.getTimestamp("ended_at");
        Instant endedAt = endedAtTs != null ? endedAtTs.toInstant() : null;
        SwipeSession.State state = SwipeSession.State.valueOf(rs.getString("state"));
        int swipeCount = rs.getInt("swipe_count");
        int likeCount = rs.getInt("like_count");
        int passCount = rs.getInt("pass_count");
        int matchCount = rs.getInt("match_count");

        return new SwipeSession(
                id, userId, startedAt, lastActivityAt, endedAt, state, swipeCount, likeCount, passCount, matchCount);
    }
}
