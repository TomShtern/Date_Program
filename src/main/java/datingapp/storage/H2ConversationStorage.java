package datingapp.storage;

import datingapp.core.Conversation;
import datingapp.core.ConversationStorage;
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
public class H2ConversationStorage implements ConversationStorage {

  private final DatabaseManager dbManager;

  public H2ConversationStorage(DatabaseManager dbManager) {
    this.dbManager = dbManager;
    ensureSchema();
  }

  /** Creates the conversations table if it doesn't exist. */
  private void ensureSchema() {
    String createTableSql =
        """
                CREATE TABLE IF NOT EXISTS conversations (
                    id VARCHAR(100) PRIMARY KEY,
                    user_a UUID NOT NULL,
                    user_b UUID NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    last_message_at TIMESTAMP,
                    user_a_last_read_at TIMESTAMP,
                    user_b_last_read_at TIMESTAMP,
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
    String sql =
        """
                INSERT INTO conversations (id, user_a, user_b, created_at, last_message_at,
                                           user_a_last_read_at, user_b_last_read_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

    try (Connection conn = dbManager.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, conversation.getId());
      stmt.setObject(2, conversation.getUserA());
      stmt.setObject(3, conversation.getUserB());
      stmt.setTimestamp(4, Timestamp.from(conversation.getCreatedAt()));
      setNullableTimestamp(stmt, 5, conversation.getLastMessageAt());
      setNullableTimestamp(stmt, 6, conversation.getUserALastReadAt());
      setNullableTimestamp(stmt, 7, conversation.getUserBLastReadAt());

      stmt.executeUpdate();

    } catch (SQLException e) {
      throw new StorageException("Failed to save conversation: " + conversation.getId(), e);
    }
  }

  @Override
  public Optional<Conversation> get(String conversationId) {
    String sql = "SELECT * FROM conversations WHERE id = ?";

    try (Connection conn = dbManager.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, conversationId);
      ResultSet rs = stmt.executeQuery();

      if (rs.next()) {
        return Optional.of(mapRow(rs));
      }
      return Optional.empty();

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
    String sql =
        """
                SELECT * FROM conversations
                WHERE user_a = ? OR user_b = ?
                ORDER BY COALESCE(last_message_at, created_at) DESC
                """;

    List<Conversation> conversations = new ArrayList<>();

    try (Connection conn = dbManager.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setObject(1, userId);
      stmt.setObject(2, userId);
      ResultSet rs = stmt.executeQuery();

      while (rs.next()) {
        conversations.add(mapRow(rs));
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
    // First, determine if userId is userA or userB
    Optional<Conversation> convoOpt = get(conversationId);
    if (convoOpt.isEmpty()) {
      throw new StorageException("Conversation not found: " + conversationId);
    }

    Conversation convo = convoOpt.get();
    String column;
    if (convo.getUserA().equals(userId)) {
      column = "user_a_last_read_at";
    } else if (convo.getUserB().equals(userId)) {
      column = "user_b_last_read_at";
    } else {
      throw new IllegalArgumentException("User is not part of this conversation");
    }

    String sql = "UPDATE conversations SET " + column + " = ? WHERE id = ?";

    try (Connection conn = dbManager.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setTimestamp(1, Timestamp.from(timestamp));
      stmt.setString(2, conversationId);
      stmt.executeUpdate();

    } catch (SQLException e) {
      throw new StorageException("Failed to update read timestamp: " + conversationId, e);
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
    Timestamp userAReadAt = rs.getTimestamp("user_a_last_read_at");
    Timestamp userBReadAt = rs.getTimestamp("user_b_last_read_at");

    return new Conversation(
        rs.getString("id"),
        rs.getObject("user_a", UUID.class),
        rs.getObject("user_b", UUID.class),
        rs.getTimestamp("created_at").toInstant(),
        lastMsgAt != null ? lastMsgAt.toInstant() : null,
        userAReadAt != null ? userAReadAt.toInstant() : null,
        userBReadAt != null ? userBReadAt.toInstant() : null);
  }

  private void setNullableTimestamp(PreparedStatement stmt, int index, Instant instant)
      throws SQLException {
    if (instant != null) {
      stmt.setTimestamp(index, Timestamp.from(instant));
    } else {
      stmt.setNull(index, Types.TIMESTAMP);
    }
  }
}
