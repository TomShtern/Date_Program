package datingapp.core.model;

import datingapp.core.AppClock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a match between two users who mutually liked each other. Mutable -
 * state can change
 * (ACTIVE -> UNMATCHED/BLOCKED).
 *
 * <p>
 * The ID is deterministic: sorted concatenation of both user UUIDs. userA is
 * always the
 * lexicographically smaller UUID.
 */
public class Match {

    private final String id;
    private final UUID userA;
    private final UUID userB;
    private final Instant createdAt;
    private MatchState state;
    private Instant endedAt; // When match was ended (nullable)
    private UUID endedBy; // Who ended it (nullable)
    private MatchArchiveReason endReason; // Why it ended (nullable)
    private Instant deletedAt; // Soft-delete timestamp (nullable)

    /** Full constructor for reconstitution from storage. */
    public Match(
            String id,
            UUID userA,
            UUID userB,
            Instant createdAt,
            MatchState state,
            Instant endedAt,
            UUID endedBy,
            MatchArchiveReason endReason) {
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
        this.endReason = endReason;
    }

    /**
     * Creates a new Match with deterministic ID based on sorted user UUIDs. Starts
     * in ACTIVE state.
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

        String firstUserId = a.toString();
        String secondUserId = b.toString();

        UUID userA;
        UUID userB;
        if (firstUserId.compareTo(secondUserId) < 0) {
            userA = a;
            userB = b;
        } else {
            userA = b;
            userB = a;
        }

        String id = userA + "_" + userB;
        return new Match(id, userA, userB, AppClock.now(), MatchState.ACTIVE, null, null, null);
    }

    /** Generates the deterministic match ID for two user UUIDs. */
    public static String generateId(UUID a, UUID b) {
        Objects.requireNonNull(a, "a cannot be null");
        Objects.requireNonNull(b, "b cannot be null");

        if (a.equals(b)) {
            throw new IllegalArgumentException("Cannot generate match ID for the same user");
        }

        String firstUserId = a.toString();
        String secondUserId = b.toString();

        if (firstUserId.compareTo(secondUserId) < 0) {
            return firstUserId + "_" + secondUserId;
        } else {
            return secondUserId + "_" + firstUserId;
        }
    }

    /** Unmatch - ends the match. Can be done from ACTIVE or FRIENDS state. */
    public void unmatch(UUID userId) {
        if (isInvalidTransition(this.state, MatchState.UNMATCHED)) {
            throw new IllegalStateException("Cannot unmatch from " + this.state + " state");
        }
        if (!involves(userId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        this.state = MatchState.UNMATCHED;
        this.endedAt = AppClock.now();
        this.endedBy = userId;
        this.endReason = MatchArchiveReason.UNMATCH;
    }

    /** Block - ends the match due to blocking. */
    public void block(UUID userId) {
        if (!involves(userId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        // Can block even if not active (defensive)
        this.state = MatchState.BLOCKED;
        this.endedAt = AppClock.now();
        this.endedBy = userId;
        this.endReason = MatchArchiveReason.BLOCK;
    }

    /** transitionToFriends - transitions the match to FRIENDS state. */
    public void transitionToFriends(UUID initiatorId) {
        if (isInvalidTransition(this.state, MatchState.FRIENDS)) {
            throw new IllegalStateException("Cannot transition to FRIENDS from " + state);
        }
        if (!involves(initiatorId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        this.state = MatchState.FRIENDS;
        // We don't set endedAt/endedBy because the relationship is still "active" in a
        // new way
    }

    /** gracefulExit - ends the match kindly. */
    public void gracefulExit(UUID initiatorId) {
        if (isInvalidTransition(this.state, MatchState.GRACEFUL_EXIT)) {
            throw new IllegalStateException("Cannot transition to GRACEFUL_EXIT from " + state);
        }
        if (!involves(initiatorId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        this.state = MatchState.GRACEFUL_EXIT;
        this.endedAt = AppClock.now();
        this.endedBy = initiatorId;
        this.endReason = MatchArchiveReason.GRACEFUL_EXIT;
    }

    private boolean isInvalidTransition(MatchState from, MatchState to) {
        return switch (from) {
            case ACTIVE ->
                !Set.of(MatchState.FRIENDS, MatchState.UNMATCHED, MatchState.GRACEFUL_EXIT, MatchState.BLOCKED)
                        .contains(to);
            case FRIENDS ->
                !Set.of(MatchState.UNMATCHED, MatchState.GRACEFUL_EXIT, MatchState.BLOCKED)
                        .contains(to);
            case UNMATCHED, GRACEFUL_EXIT, BLOCKED -> true;
        };
    }

    /** Checks if the match allows messaging. */
    public boolean canMessage() {
        return this.state == MatchState.ACTIVE || this.state == MatchState.FRIENDS;
    }

    /** Checks if the match is currently active. */
    public boolean isActive() {
        return this.state == MatchState.ACTIVE;
    }

    /** Checks if this match involves the given user. */
    public boolean involves(UUID userId) {
        return userA.equals(userId) || userB.equals(userId);
    }

    /** Gets the other user in this match. */
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

    public MatchState getState() {
        return state;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public UUID getEndedBy() {
        return endedBy;
    }

    public MatchArchiveReason getEndReason() {
        return endReason;
    }

    // Soft-delete support

    /** Returns the deletion timestamp, or {@code null} if not deleted. */
    public Instant getDeletedAt() {
        return deletedAt;
    }

    /** Marks this entity as soft-deleted at the given instant. */
    public void markDeleted(Instant deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt cannot be null");
    }

    /** Returns {@code true} if this match has been soft-deleted. */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Restores the deleted-at timestamp from storage. This method is for storage
     * layer
     * reconstitution only — production code should use
     * {@link #markDeleted(Instant)}.
     */
    public void restoreDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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
