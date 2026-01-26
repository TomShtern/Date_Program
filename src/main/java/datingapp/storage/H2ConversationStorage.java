package datingapp.storage;

import datingapp.core.Match;
import datingapp.core.Messaging.Conversation;
import datingapp.core.Messaging.ConversationStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** H2 implementation of ConversationStorage. */
public class H2ConversationStorage extends AbstractH2Storage implements ConversationStorage {

    public H2ConversationStorage(DatabaseManager dbManager) {
        super(dbManager);
        ensureSchema();
    }

    /** Creates the conversations table if it doesn't exist. */
    @Override
    protected void ensureSchema() {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS conversations (
                    id VARCHAR(100) PRIMARY KEY,
                    user_a UUID NOT NULL,
                    user_b UUID NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    last_message_at TIMESTAMP,
                    user_a_last_read_at TIMESTAMP,
                    user_b_last_read_at TIMESTAMP,
                    archived_at TIMESTAMP,
                    archive_reason VARCHAR(20),
                    visible_to_user_a BOOLEAN DEFAULT TRUE,
                    visible_to_user_b BOOLEAN DEFAULT TRUE,
                    CONSTRAINT unq_conversation_users UNIQUE (user_a, user_b)
                )
                """;
        String indexA = "CREATE INDEX IF NOT EXISTS idx_conversations_user_a ON conversations(user_a)";
        String indexB = "CREATE INDEX IF NOT EXISTS idx_conversations_user_b ON conversations(user_b)";

        try (Connection conn = dbManager.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            stmt.execute(indexA);
            stmt.execute(indexB);
        } catch (SQLException e) {
            throw new StorageException("Failed to create conversations schema", e);
        }
    }

    @Override
    public void save(Conversation conversation) {
        String sql = """
                INSERT INTO conversations (id, user_a, user_b, created_at, last_message_at,
                                           user_a_last_read_at, user_b_last_read_at,
                                           archived_at, archive_reason, visible_to_user_a, visible_to_user_b)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conversation.getId());
            stmt.setObject(2, conversation.getUserA());
            stmt.setObject(3, conversation.getUserB());
            stmt.setTimestamp(4, Timestamp.from(conversation.getCreatedAt()));
            setNullableTimestamp(stmt, 5, conversation.getLastMessageAt());
            setNullableTimestamp(stmt, 6, conversation.getUserAReadAt());
            setNullableTimestamp(stmt, 7, conversation.getUserBReadAt());
            setNullableTimestamp(stmt, 8, conversation.getArchivedAt());
            if (conversation.getArchiveReason() != null) {
                stmt.setString(9, conversation.getArchiveReason().name());
            } else {
                stmt.setNull(9, Types.VARCHAR);
            }
            stmt.setBoolean(10, conversation.isVisibleToUserA());
            stmt.setBoolean(11, conversation.isVisibleToUserB());

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save conversation: " + conversation.getId(), e);
        }
    }

    @Override
    public Optional<Conversation> get(String conversationId) {
        String sql = """
            SELECT id, user_a, user_b, created_at, last_message_at,
                user_a_last_read_at, user_b_last_read_at,
                archived_at, archive_reason, visible_to_user_a, visible_to_user_b
            FROM conversations
            WHERE id = ?
            """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conversationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new StorageException("Failed to get conversation: " + conversationId, e);
        }
    }

    @Override
    public Optional<Conversation> getByUsers(UUID userA, UUID userB) {
        String conversationId = Conversation.generateId(userA, userB);
        return get(conversationId);
    }

    @Override
    public List<Conversation> getConversationsFor(UUID userId) {
        String sql = """
            SELECT id, user_a, user_b, created_at, last_message_at,
                user_a_last_read_at, user_b_last_read_at,
                archived_at, archive_reason, visible_to_user_a, visible_to_user_b
            FROM conversations
            WHERE user_a = ? OR user_b = ?
            ORDER BY COALESCE(last_message_at, created_at) DESC
            """;

        List<Conversation> conversations = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setObject(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    conversations.add(mapRow(rs));
                }
            }
            return conversations;

        } catch (SQLException e) {
            throw new StorageException("Failed to get conversations for user: " + userId, e);
        }
    }

    @Override
    public void updateLastMessageAt(String conversationId, Instant timestamp) {
        String sql = "UPDATE conversations SET last_message_at = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(timestamp));
            stmt.setString(2, conversationId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to update last message at: " + conversationId, e);
        }
    }

    @Override
    public void updateReadTimestamp(String conversationId, UUID userId, Instant timestamp) {
        // Single query with CASE/WHEN to update the correct column based on userId
        String sql = """
                UPDATE conversations
                SET user_a_last_read_at = CASE WHEN user_a = ? THEN ? ELSE user_a_last_read_at END,
                    user_b_last_read_at = CASE WHEN user_b = ? THEN ? ELSE user_b_last_read_at END
                WHERE id = ? AND (user_a = ? OR user_b = ?)
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            Timestamp ts = Timestamp.from(timestamp);
            stmt.setObject(1, userId);
            stmt.setTimestamp(2, ts);
            stmt.setObject(3, userId);
            stmt.setTimestamp(4, ts);
            stmt.setString(5, conversationId);
            stmt.setObject(6, userId);
            stmt.setObject(7, userId);

            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated == 0) {
                throw new StorageException("Conversation not found or user is not a participant: " + conversationId);
            }

        } catch (SQLException e) {
            throw new StorageException("Failed to update read timestamp: " + conversationId, e);
        }
    }

    @Override
    public void archive(String conversationId, Match.ArchiveReason reason) {
        String sql = "UPDATE conversations SET archived_at = ?, archive_reason = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setString(2, reason.name());
            stmt.setString(3, conversationId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to archive conversation: " + conversationId, e);
        }
    }

    @Override
    public void setVisibility(String conversationId, UUID userId, boolean visible) {
        String sql = """
                UPDATE conversations
                SET visible_to_user_a = CASE WHEN user_a = ? THEN ? ELSE visible_to_user_a END,
                    visible_to_user_b = CASE WHEN user_b = ? THEN ? ELSE visible_to_user_b END
                WHERE id = ?
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setBoolean(2, visible);
            stmt.setObject(3, userId);
            stmt.setBoolean(4, visible);
            stmt.setString(5, conversationId);

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to update visibility for conversation: " + conversationId, e);
        }
    }

    @Override
    public void delete(String conversationId) {
        // Messages will be deleted via cascade or separately by MessageStorage
        String sql = "DELETE FROM conversations WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conversationId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to delete conversation: " + conversationId, e);
        }
    }

    private Conversation mapRow(ResultSet rs) throws SQLException {
        Timestamp lastMsgAt = rs.getTimestamp("last_message_at");
        Timestamp userAReadTime = rs.getTimestamp("user_a_last_read_at");
        Timestamp userBReadTime = rs.getTimestamp("user_b_last_read_at");

        return new Conversation(
                rs.getString("id"),
                rs.getObject("user_a", UUID.class),
                rs.getObject("user_b", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                lastMsgAt != null ? lastMsgAt.toInstant() : null,
                userAReadTime != null ? userAReadTime.toInstant() : null,
                userBReadTime != null ? userBReadTime.toInstant() : null,
                rs.getTimestamp("archived_at") != null
                        ? rs.getTimestamp("archived_at").toInstant()
                        : null,
                rs.getString("archive_reason") != null
                        ? Match.ArchiveReason.valueOf(rs.getString("archive_reason"))
                        : null,
                rs.getBoolean("visible_to_user_a"),
                rs.getBoolean("visible_to_user_b"));
    }
}
