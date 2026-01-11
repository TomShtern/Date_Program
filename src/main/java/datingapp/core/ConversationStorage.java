package datingapp.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage interface for Conversation entities. Defined in core, implemented in storage layer. */
public interface ConversationStorage {

  /** Saves a new conversation. */
  void save(Conversation conversation);

  /** Gets a conversation by ID. */
  Optional<Conversation> get(String conversationId);

  /** Gets a conversation by the two user UUIDs (order-independent). */
  Optional<Conversation> getByUsers(UUID userA, UUID userB);

  /** Gets all conversations for a given user, sorted by lastMessageAt descending. */
  List<Conversation> getConversationsFor(UUID userId);

  /** Updates the lastMessageAt timestamp for a conversation. */
  void updateLastMessageAt(String conversationId, Instant timestamp);

  /** Updates the read timestamp for a specific user in a conversation. */
  void updateReadTimestamp(String conversationId, UUID userId, Instant timestamp);

  /** Deletes a conversation and all its messages (cascade). */
  void delete(String conversationId);
}
