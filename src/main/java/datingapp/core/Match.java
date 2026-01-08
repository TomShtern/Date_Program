package datingapp.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a match between two users who mutually liked each other.
 * Mutable - state can change (ACTIVE -> UNMATCHED/BLOCKED).
 *
 * The ID is deterministic: sorted concatenation of both user UUIDs.
 * userA is always the lexicographically smaller UUID.
 */
public class Match {

    public enum State {
        ACTIVE, // Both users are matched
        UNMATCHED, // One user ended the match
        BLOCKED // One user blocked the other
    }

    private final String id;
    private final UUID userA;
    private final UUID userB;
    private final Instant createdAt;
    private State state;
    private Instant endedAt; // When match was ended (nullable)
    private UUID endedBy; // Who ended it (nullable)

    /**
     * Full constructor for reconstitution from storage.
     */
    public Match(String id, UUID userA, UUID userB, Instant createdAt,
            State state, Instant endedAt, UUID endedBy) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(userA, "userA cannot be null");
        Objects.requireNonNull(userB, "userB cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(state, "state cannot be null");

        if (userA.equals(userB)) {
            throw new IllegalArgumentException("Cannot match with yourself");
        }

        // Validate ordering
        if (userA.toString().compareTo(userB.toString()) > 0) {
            throw new IllegalArgumentException("userA must be lexicographically smaller than userB");
        }

        this.id = id;
        this.userA = userA;
        this.userB = userB;
        this.createdAt = createdAt;
        this.state = state;
        this.endedAt = endedAt;
        this.endedBy = endedBy;
    }

    /**
     * Creates a new Match with deterministic ID based on sorted user UUIDs.
     * Starts in ACTIVE state.
     *
     * @param a First user UUID
     * @param b Second user UUID
     * @return A new Match with proper ordering and deterministic ID
     */
    public static Match create(UUID a, UUID b) {
        Objects.requireNonNull(a, "a cannot be null");
        Objects.requireNonNull(b, "b cannot be null");

        if (a.equals(b)) {
            throw new IllegalArgumentException("Cannot match with yourself");
        }

        String aStr = a.toString();
        String bStr = b.toString();

        UUID userA, userB;
        if (aStr.compareTo(bStr) < 0) {
            userA = a;
            userB = b;
        } else {
            userA = b;
            userB = a;
        }

        String id = userA.toString() + "_" + userB.toString();
        return new Match(id, userA, userB, Instant.now(), State.ACTIVE, null, null);
    }

    /**
     * Generates the deterministic match ID for two user UUIDs.
     */
    public static String generateId(UUID a, UUID b) {
        String aStr = a.toString();
        String bStr = b.toString();

        if (aStr.compareTo(bStr) < 0) {
            return aStr + "_" + bStr;
        } else {
            return bStr + "_" + aStr;
        }
    }

    /**
     * Unmatch - ends the match. Can only be done from ACTIVE state.
     */
    public void unmatch(UUID userId) {
        if (this.state != State.ACTIVE) {
            throw new IllegalStateException("Match is not active");
        }
        if (!involves(userId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        this.state = State.UNMATCHED;
        this.endedAt = Instant.now();
        this.endedBy = userId;
    }

    /**
     * Block - ends the match due to blocking.
     */
    public void block(UUID userId) {
        if (!involves(userId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        // Can block even if not active (defensive)
        this.state = State.BLOCKED;
        this.endedAt = Instant.now();
        this.endedBy = userId;
    }

    /**
     * Checks if the match is currently active.
     */
    public boolean isActive() {
        return this.state == State.ACTIVE;
    }

    /**
     * Checks if this match involves the given user.
     */
    public boolean involves(UUID userId) {
        return userA.equals(userId) || userB.equals(userId);
    }

    /**
     * Gets the other user in this match.
     */
    public UUID getOtherUser(UUID userId) {
        if (userA.equals(userId)) {
            return userB;
        } else if (userB.equals(userId)) {
            return userA;
        }
        throw new IllegalArgumentException("User is not part of this match");
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

    public State getState() {
        return state;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public UUID getEndedBy() {
        return endedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Match match = (Match) o;
        return Objects.equals(id, match.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Match{id='" + id + "', state=" + state + "}";
    }
}
