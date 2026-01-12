package datingapp.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a block between two users. When user A blocks user B, neither can see the other
 * (bidirectional effect). Immutable after creation.
 */
public record Block(
    UUID id,
    UUID blockerId, // User who initiated the block
    UUID blockedId, // User who got blocked
    Instant createdAt) {

  /**
   * Creates a Block record with validation.
   *
   * @param id the unique identifier for this block
   * @param blockerId the user who initiated the block
   * @param blockedId the user who got blocked
   * @param createdAt when the block was created
   */
  public Block {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(blockerId, "blockerId cannot be null");
    Objects.requireNonNull(blockedId, "blockedId cannot be null");
    Objects.requireNonNull(createdAt, "createdAt cannot be null");

    if (blockerId.equals(blockedId)) {
      throw new IllegalArgumentException("Cannot block yourself");
    }
  }

  /** Creates a new Block with generated ID and current timestamp. */
  public static Block create(UUID blockerId, UUID blockedId) {
    Objects.requireNonNull(blockerId, "blockerId cannot be null");
    Objects.requireNonNull(blockedId, "blockedId cannot be null");

    return new Block(UUID.randomUUID(), blockerId, blockedId, Instant.now());
  }
}
