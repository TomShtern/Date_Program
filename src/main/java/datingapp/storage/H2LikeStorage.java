package datingapp.storage;

import datingapp.core.LikeStorage;
import datingapp.core.UserInteractions.Like;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** H2 implementation of LikeStorage. */
public class H2LikeStorage implements LikeStorage {

    private static final String COL_ID = "id";
    private static final String COL_WHO_LIKES = "who_likes";
    private static final String COL_WHO_GOT_LIKED = "who_got_liked";
    private static final String COL_DIRECTION = "direction";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_LIKED_AT = "liked_at";

    private final DatabaseManager dbManager;

    public H2LikeStorage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
        String sql = """
                SELECT id, who_likes, who_got_liked, direction, created_at
                FROM likes WHERE who_likes = ? AND who_got_liked = ?
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, fromUserId);
            stmt.setObject(2, toUserId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(new Like(
                        rs.getObject(COL_ID, UUID.class),
                        rs.getObject(COL_WHO_LIKES, UUID.class),
                        rs.getObject(COL_WHO_GOT_LIKED, UUID.class),
                        Like.Direction.valueOf(rs.getString(COL_DIRECTION)),
                        rs.getTimestamp(COL_CREATED_AT).toInstant()));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new StorageException("Failed to get like", e);
        }
    }

    @Override
    public void save(Like like) {
        String sql = """
                INSERT INTO likes (id, who_likes, who_got_liked, direction, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, like.id());
            stmt.setObject(2, like.whoLikes());
            stmt.setObject(3, like.whoGotLiked());
            stmt.setString(4, like.direction().name());
            stmt.setTimestamp(5, Timestamp.from(like.createdAt()));

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save like: " + like.id(), e);
        }
    }

    @Override
    public boolean exists(UUID from, UUID to) {
        String sql = "SELECT 1 FROM likes WHERE who_likes = ? AND who_got_liked = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, from);
            stmt.setObject(2, to);
            ResultSet rs = stmt.executeQuery();

            return rs.next();

        } catch (SQLException e) {
            throw new StorageException("Failed to check like exists", e);
        }
    }

    @Override
    public boolean mutualLikeExists(UUID a, UUID b) {
        // Check if BOTH users have LIKED (not passed) each other
        String sql = """
                SELECT COUNT(*) FROM likes l1
                JOIN likes l2 ON l1.who_likes = l2.who_got_liked
                             AND l1.who_got_liked = l2.who_likes
                WHERE l1.who_likes = ? AND l1.who_got_liked = ?
                  AND l1.direction = 'LIKE' AND l2.direction = 'LIKE'
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, a);
            stmt.setObject(2, b);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;

        } catch (SQLException e) {
            throw new StorageException("Failed to check mutual like", e);
        }
    }

    @Override
    public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
        String sql = "SELECT who_got_liked FROM likes WHERE who_likes = ?";
        Set<UUID> result = new HashSet<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(rs.getObject(COL_WHO_GOT_LIKED, UUID.class));
            }
            return result;

        } catch (SQLException e) {
            throw new StorageException("Failed to get liked user IDs", e);
        }
    }

    @Override
    public Set<UUID> getUserIdsWhoLiked(UUID userId) {
        String sql = "SELECT who_likes FROM likes WHERE who_got_liked = ? AND direction = 'LIKE'";
        Set<UUID> result = new HashSet<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(rs.getObject(COL_WHO_LIKES, UUID.class));
            }
            return result;

        } catch (SQLException e) {
            throw new StorageException("Failed to get users who liked", e);
        }
    }

    @Override
    public java.util.Map<UUID, Instant> getLikeTimesForUsersWhoLiked(UUID userId) {
        String sql = """
                SELECT who_likes, MAX(created_at) AS liked_at
                FROM likes
                WHERE who_got_liked = ? AND direction = 'LIKE'
                GROUP BY who_likes
                """;

        java.util.Map<UUID, Instant> result = new java.util.HashMap<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UUID whoLikes = rs.getObject(COL_WHO_LIKES, UUID.class);
                Timestamp likedAt = rs.getTimestamp(COL_LIKED_AT);
                if (whoLikes != null && likedAt != null) {
                    result.put(whoLikes, likedAt.toInstant());
                }
            }
            return result;

        } catch (SQLException e) {
            throw new StorageException("Failed to get liker timestamps", e);
        }
    }

    // === Statistics Methods (Phase 0.5b) ===

    @Override
    public int countByDirection(UUID userId, Like.Direction direction) {
        String sql = "SELECT COUNT(*) FROM likes WHERE who_likes = ? AND direction = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setString(2, direction.name());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count likes by direction", e);
        }
    }

    @Override
    public int countReceivedByDirection(UUID userId, Like.Direction direction) {
        String sql = "SELECT COUNT(*) FROM likes WHERE who_got_liked = ? AND direction = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setString(2, direction.name());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count received likes by direction", e);
        }
    }

    @Override
    public int countMutualLikes(UUID userId) {
        // Count users that userId LIKED who also LIKED userId back
        String sql = """
                SELECT COUNT(*) FROM likes l1
                JOIN likes l2 ON l1.who_likes = l2.who_got_liked
                             AND l1.who_got_liked = l2.who_likes
                WHERE l1.who_likes = ?
                  AND l1.direction = 'LIKE' AND l2.direction = 'LIKE'
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count mutual likes", e);
        }
    }

    // === Daily Limit Methods (Phase 1) ===

    @Override
    public int countLikesToday(UUID userId, Instant startOfDay) {
        String sql = """
                SELECT COUNT(*) FROM likes
                WHERE who_likes = ? AND direction = 'LIKE' AND created_at >= ?
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setTimestamp(2, Timestamp.from(startOfDay));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count likes today", e);
        }
    }

    @Override
    public int countPassesToday(UUID userId, Instant startOfDay) {
        String sql = """
                SELECT COUNT(*) FROM likes
                WHERE who_likes = ? AND direction = 'PASS' AND created_at >= ?
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setTimestamp(2, Timestamp.from(startOfDay));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count passes today", e);
        }
    }

    // === Undo Methods (Phase 1) ===

    @Override
    public void delete(UUID likeId) {
        String sql = "DELETE FROM likes WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, likeId);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new StorageException("Like not found for deletion: " + likeId);
            }

        } catch (SQLException e) {
            throw new StorageException("Failed to delete like: " + likeId, e);
        }
    }
}
