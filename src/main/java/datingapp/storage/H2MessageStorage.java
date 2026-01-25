package datingapp.storage;

import datingapp.core.Messaging.Message;
import datingapp.core.Messaging.MessageStorage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** H2 implementation of MessageStorage. */
public class H2MessageStorage extends AbstractH2Storage implements MessageStorage {

    public H2MessageStorage(DatabaseManager dbManager) {
        super(dbManager);
        ensureSchema();
    }

    /** Creates the messages table if it doesn't exist. */
    @Override
    protected void ensureSchema() {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS messages (
                    id UUID PRIMARY KEY,
                    conversation_id VARCHAR(100) NOT NULL,
                    sender_id UUID NOT NULL,
                    content VARCHAR(1000) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """;
        String index = "CREATE INDEX IF NOT EXISTS idx_messages_conversation_created "
                + "ON messages(conversation_id, created_at)";

        // FK constraint added separately - will fail gracefully if conversations table doesn't exist
        // or if constraint already exists. This enables cascade delete when conversations are deleted.
        String addFkConstraint = """
                ALTER TABLE messages ADD CONSTRAINT IF NOT EXISTS fk_messages_conversation
                FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                """;

        try (Connection conn = dbManager.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            stmt.execute(index);
            tryAddConversationForeignKey(stmt, addFkConstraint);
        } catch (SQLException e) {
            throw new StorageException("Failed to create messages schema", e);
        }
    }

    private void tryAddConversationForeignKey(Statement stmt, String addFkConstraint) throws SQLException {
        // Try to add FK constraint - ignore errors if conversations table doesn't exist yet
        // or if constraint already exists. The application handles cleanup via deleteByConversation.
        try {
            stmt.execute(addFkConstraint);
        } catch (SQLException _) {
            // FK constraint failed (conversations table may not exist yet) - this is OK
            // as the application handles cascade delete manually via deleteByConversation()
        }
    }

    @Override
    public void save(Message message) {
        String sql = """
                INSERT INTO messages (id, conversation_id, sender_id, content, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, message.id());
            stmt.setString(2, message.conversationId());
            stmt.setObject(3, message.senderId());
            stmt.setString(4, message.content());
            stmt.setTimestamp(5, Timestamp.from(message.createdAt()));

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save message: " + message.id(), e);
        }
    }

    @Override
    public List<Message> getMessages(String conversationId, int limit, int offset) {
        String sql = """
            SELECT id, conversation_id, sender_id, content, created_at
            FROM messages
            WHERE conversation_id = ?
            ORDER BY created_at ASC
            LIMIT ? OFFSET ?
            """;

        List<Message> messages = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conversationId);
            stmt.setInt(2, limit);
            stmt.setInt(3, Math.max(0, offset));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                messages.add(mapRow(rs));
            }
            return messages;

        } catch (SQLException e) {
            throw new StorageException("Failed to get messages for conversation: " + conversationId, e);
        }
    }

    @Override
    public Optional<Message> getLatestMessage(String conversationId) {
        String sql = """
            SELECT id, conversation_id, sender_id, content, created_at
            FROM messages
            WHERE conversation_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conversationId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new StorageException("Failed to get latest message for conversation: " + conversationId, e);
        }
    }

    @Override
    public int countMessages(String conversationId) {
        String sql = "SELECT COUNT(*) FROM messages WHERE conversation_id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conversationId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count messages for conversation: " + conversationId, e);
        }
    }

    @Override
    public int countMessagesAfter(String conversationId, Instant after) {
        String sql = """
                SELECT COUNT(*) FROM messages
                WHERE conversation_id = ?
                AND created_at > ?
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conversationId);
            stmt.setTimestamp(2, Timestamp.from(after));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count unread messages: " + conversationId, e);
        }
    }

    @Override
    public int countMessagesNotFromSender(String conversationId, UUID senderId) {
        String sql = """
                SELECT COUNT(*) FROM messages
                WHERE conversation_id = ?
                AND sender_id != ?
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conversationId);
            stmt.setObject(2, senderId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;

        } catch (SQLException e) {
            throw new StorageException("Failed to count messages not from sender: " + conversationId, e);
        }
    }

    @Override
    public void deleteByConversation(String conversationId) {
        String sql = "DELETE FROM messages WHERE conversation_id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conversationId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to delete messages for conversation: " + conversationId, e);
        }
    }

    private Message mapRow(ResultSet rs) throws SQLException {
        return new Message(
                rs.getObject("id", UUID.class),
                rs.getString("conversation_id"),
                rs.getObject("sender_id", UUID.class),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant());
    }
}
