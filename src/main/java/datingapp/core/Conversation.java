package datingapp.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a conversation between two matched users. Mutable - timestamps can be updated.
 *
 * <p>The ID is deterministic: sorted concatenation of both user UUIDs. userA is always the
 * lexicographically smaller UUID. This mirrors the Match ID pattern.
 */
public class Conversation {

  private final String id;
  private final UUID userA; // Lexicographically smaller
  private final UUID userB; // Lexicographically larger
  private final Instant createdAt;
  private Instant lastMessageAt;
  private Instant userALastReadAt;
  private Instant userBLastReadAt;

  /** Full constructor for reconstitution from storage. */
  public Conversation(
      String id,
      UUID userA,
      UUID userB,
      Instant createdAt,
      Instant lastMessageAt,
      Instant userALastReadAt,
      Instant userBLastReadAt) {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(userA, "userA cannot be null");
    Objects.requireNonNull(userB, "userB cannot be null");
    Objects.requireNonNull(createdAt, "createdAt cannot be null");

    if (userA.equals(userB)) {
      throw new IllegalArgumentException("Cannot have conversation with yourself");
    }

    // Validate ordering
    if (userA.toString().compareTo(userB.toString()) > 0) {
      throw new IllegalArgumentException("userA must be lexicographically smaller than userB");
    }

    this.id = id;
    this.userA = userA;
    this.userB = userB;
    this.createdAt = createdAt;
    this.lastMessageAt = lastMessageAt;
    this.userALastReadAt = userALastReadAt;
    this.userBLastReadAt = userBLastReadAt;
  }

  /**
   * Creates a new Conversation with deterministic ID based on sorted user UUIDs.
   *
   * @param a First user UUID
   * @param b Second user UUID
   * @return A new Conversation with proper ordering and deterministic ID
   */
  public static Conversation create(UUID a, UUID b) {
    Objects.requireNonNull(a, "a cannot be null");
    Objects.requireNonNull(b, "b cannot be null");

    if (a.equals(b)) {
      throw new IllegalArgumentException("Cannot have conversation with yourself");
    }

    String aStr = a.toString();
    String bStr = b.toString();

    UUID userA;
    UUID userB;
    if (aStr.compareTo(bStr) < 0) {
      userA = a;
      userB = b;
    } else {
      userA = b;
      userB = a;
    }

    String id = userA.toString() + "_" + userB.toString();
    Instant now = Instant.now();
    return new Conversation(id, userA, userB, now, null, null, null);
  }

  /** Generates the deterministic conversation ID for two user UUIDs. */
  public static String generateId(UUID a, UUID b) {
    String aStr = a.toString();
    String bStr = b.toString();

    if (aStr.compareTo(bStr) < 0) {
      return aStr + "_" + bStr;
    } else {
      return bStr + "_" + aStr;
    }
  }

  /** Checks if this conversation involves the given user. */
  public boolean involves(UUID userId) {
    return userA.equals(userId) || userB.equals(userId);
  }

  /** Gets the other user in this conversation. */
  public UUID getOtherUser(UUID userId) {
    if (userA.equals(userId)) {
      return userB;
    } else if (userB.equals(userId)) {
      return userA;
    }
    throw new IllegalArgumentException("User is not part of this conversation");
  }

  /** Updates the last message timestamp. */
  public void updateLastMessageAt(Instant timestamp) {
    Objects.requireNonNull(timestamp, "timestamp cannot be null");
    this.lastMessageAt = timestamp;
  }

  /**
   * Updates the read timestamp for a specific user.
   *
   * @param userId The user who read the conversation
   * @param timestamp When they last read it
   */
  public void updateReadTimestamp(UUID userId, Instant timestamp) {
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(timestamp, "timestamp cannot be null");

    if (userA.equals(userId)) {
      this.userALastReadAt = timestamp;
    } else if (userB.equals(userId)) {
      this.userBLastReadAt = timestamp;
    } else {
      throw new IllegalArgumentException("User is not part of this conversation");
    }
  }

  /**
   * Gets the last read timestamp for a specific user.
   *
   * @param userId The user to get the read timestamp for
   * @return The last read timestamp, or null if never read
   */
  public Instant getLastReadAt(UUID userId) {
    if (userA.equals(userId)) {
      return userALastReadAt;
    } else if (userB.equals(userId)) {
      return userBLastReadAt;
    }
    throw new IllegalArgumentException("User is not part of this conversation");
  }

  // Getters
  public String getId() {
    return id;
  }

  public UUID getUserA() {
    return userA;
  }

  public UUID getUserB() {
    return userB;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastMessageAt() {
    return lastMessageAt;
  }

  public Instant getUserALastReadAt() {
    return userALastReadAt;
  }

  public Instant getUserBLastReadAt() {
    return userBLastReadAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Conversation that = (Conversation) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Conversation{id='" + id + "', lastMessageAt=" + lastMessageAt + "}";
  }
}
