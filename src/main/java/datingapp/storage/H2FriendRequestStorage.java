package datingapp.storage;

import datingapp.core.FriendRequest;
import datingapp.core.FriendRequestStatus;
import datingapp.core.FriendRequestStorage;
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

/** H2 implementation of FriendRequestStorage. */
public class H2FriendRequestStorage implements FriendRequestStorage {

    private final DatabaseManager dbManager;

    public H2FriendRequestStorage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        ensureSchema();
    }

    private void ensureSchema() {
        String tableSql =
                """
                CREATE TABLE IF NOT EXISTS friend_requests (
                    id UUID PRIMARY KEY,
                    from_user_id UUID NOT NULL,
                    to_user_id UUID NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    responded_at TIMESTAMP,
                    FOREIGN KEY (from_user_id) REFERENCES users(id),
                    FOREIGN KEY (to_user_id) REFERENCES users(id)
                )
                """;
        // Unique partial index to prevent multiple pending requests between same users
        // H2 supports filtered indices: CREATE UNIQUE INDEX name ON table(cols) WHERE
        // condition
        String indexSql =
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_friend_req_pending
                ON friend_requests(from_user_id, to_user_id)
                WHERE status = 'PENDING'
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt1 = conn.prepareStatement(tableSql);
                PreparedStatement stmt2 = conn.prepareStatement(indexSql)) {
            stmt1.execute();
            stmt2.execute();
        } catch (SQLException e) {
            throw new StorageException("Failed to ensure friend_requests schema", e);
        }
    }

    @Override
    public void save(FriendRequest request) {
        String sql =
                """
                INSERT INTO friend_requests (id, from_user_id, to_user_id, created_at, status, responded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, request.id());
            stmt.setObject(2, request.fromUserId());
            stmt.setObject(3, request.toUserId());
            stmt.setTimestamp(4, Timestamp.from(request.createdAt()));
            stmt.setString(5, request.status().name());
            if (request.respondedAt() != null) {
                stmt.setTimestamp(6, Timestamp.from(request.respondedAt()));
            } else {
                stmt.setNull(6, Types.TIMESTAMP);
            }

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save friend request: " + request.id(), e);
        }
    }

    @Override
    public void update(FriendRequest request) {
        String sql =
                """
                UPDATE friend_requests SET status = ?, responded_at = ?
                WHERE id = ?
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, request.status().name());
            if (request.respondedAt() != null) {
                stmt.setTimestamp(2, Timestamp.from(request.respondedAt()));
            } else {
                stmt.setNull(2, Types.TIMESTAMP);
            }
            stmt.setObject(3, request.id());

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to update friend request: " + request.id(), e);
        }
    }

    @Override
    public Optional<FriendRequest> get(UUID id) {
        String sql = "SELECT * FROM friend_requests WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new StorageException("Failed to get friend request: " + id, e);
        }
    }

    @Override
    public Optional<FriendRequest> getPendingBetween(UUID user1, UUID user2) {
        String sql =
                """
                SELECT * FROM friend_requests
                WHERE ((from_user_id = ? AND to_user_id = ?) OR (from_user_id = ? AND to_user_id = ?))
                AND status = 'PENDING'
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, user1);
            stmt.setObject(2, user2);
            stmt.setObject(3, user2);
            stmt.setObject(4, user1);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new StorageException("Failed to get pending friend request", e);
        }
    }

    @Override
    public List<FriendRequest> getPendingForUser(UUID userId) {
        String sql = "SELECT * FROM friend_requests WHERE to_user_id = ? AND status = 'PENDING'";
        List<FriendRequest> requests = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                requests.add(mapRow(rs));
            }
            return requests;

        } catch (SQLException e) {
            throw new StorageException("Failed to get pending requests for user: " + userId, e);
        }
    }

    @Override
    public void delete(UUID id) {
        String sql = "DELETE FROM friend_requests WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to delete friend request: " + id, e);
        }
    }

    private FriendRequest mapRow(ResultSet rs) throws SQLException {
        Timestamp respondedAtTs = rs.getTimestamp("responded_at");
        return new FriendRequest(
                rs.getObject("id", UUID.class),
                rs.getObject("from_user_id", UUID.class),
                rs.getObject("to_user_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                FriendRequestStatus.valueOf(rs.getString("status")),
                respondedAtTs != null ? respondedAtTs.toInstant() : null);
    }
}
