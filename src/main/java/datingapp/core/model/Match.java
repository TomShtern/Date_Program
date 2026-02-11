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

    /** Represents the current state of a match. */
    public static enum State {
        ACTIVE, // Both users are matched
        FRIENDS, // Mutual transition to platonic friendship
        UNMATCHED, // One user ended the match
        GRACEFUL_EXIT, // One user ended the match kindly
        BLOCKED // One user blocked the other
    }

    /**
     * Reasons why a relationship/match was archived or ended.
     *
     * <p>Note: Some reasons overlap with terminal {@link State} values. We keep both for
     * analytics/history without changing the state machine.
     */
    public static enum ArchiveReason {
        FRIEND_ZONE,
        GRACEFUL_EXIT,
        UNMATCH,
        BLOCK
    }

    private final String id;
    private final UUID userA;
    private final UUID userB;
    private final Instant createdAt;
    private State state;
    private Instant endedAt; // When match was ended (nullable)
    private UUID endedBy; // Who ended it (nullable)
    private ArchiveReason endReason; // Why it ended (nullable) - nested enum in Match
    private Instant deletedAt; // Soft-delete timestamp (nullable)

    /** Full constructor for reconstitution from storage. */
    public Match(
            String id,
            UUID userA,
            UUID userB,
            Instant createdAt,
            State state,
            Instant endedAt,
            UUID endedBy,
            ArchiveReason endReason) {
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
        return new Match(id, userA, userB, AppClock.now(), State.ACTIVE, null, null, null);
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
        if (isInvalidTransition(this.state, State.UNMATCHED)) {
            throw new IllegalStateException("Cannot unmatch from " + this.state + " state");
        }
        if (!involves(userId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        this.state = State.UNMATCHED;
        this.endedAt = AppClock.now();
        this.endedBy = userId;
        this.endReason = ArchiveReason.UNMATCH;
    }

    /** Block - ends the match due to blocking. */
    public void block(UUID userId) {
        if (!involves(userId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        // Can block even if not active (defensive)
        this.state = State.BLOCKED;
        this.endedAt = AppClock.now();
        this.endedBy = userId;
        this.endReason = ArchiveReason.BLOCK;
    }

    /** transitionToFriends - transitions the match to FRIENDS state. */
    public void transitionToFriends(UUID initiatorId) {
        if (isInvalidTransition(this.state, State.FRIENDS)) {
            throw new IllegalStateException("Cannot transition to FRIENDS from " + state);
        }
        if (!involves(initiatorId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        this.state = State.FRIENDS;
        // We don't set endedAt/endedBy because the relationship is still "active" in a
        // new way
    }

    /** gracefulExit - ends the match kindly. */
    public void gracefulExit(UUID initiatorId) {
        if (isInvalidTransition(this.state, State.GRACEFUL_EXIT)) {
            throw new IllegalStateException("Cannot transition to GRACEFUL_EXIT from " + state);
        }
        if (!involves(initiatorId)) {
            throw new IllegalArgumentException("User is not part of this match");
        }
        this.state = State.GRACEFUL_EXIT;
        this.endedAt = AppClock.now();
        this.endedBy = initiatorId;
        this.endReason = ArchiveReason.GRACEFUL_EXIT;
    }

    private boolean isInvalidTransition(State from, State to) {
        return switch (from) {
            case ACTIVE ->
                !Set.of(State.FRIENDS, State.UNMATCHED, State.GRACEFUL_EXIT, State.BLOCKED)
                        .contains(to);
            case FRIENDS ->
                !Set.of(State.UNMATCHED, State.GRACEFUL_EXIT, State.BLOCKED).contains(to);
            case UNMATCHED, GRACEFUL_EXIT, BLOCKED -> true;
        };
    }

    /** Checks if the match allows messaging. */
    public boolean canMessage() {
        return this.state == State.ACTIVE || this.state == State.FRIENDS;
    }

    /** Checks if the match is currently active. */
    public boolean isActive() {
        return this.state == State.ACTIVE;
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

    public State getState() {
        return state;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public UUID getEndedBy() {
        return endedBy;
    }

    public ArchiveReason getEndReason() {
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

    /** Sets the deleted-at timestamp; used when reconstructing from storage. */
    public void setDeletedAt(Instant deletedAt) {
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
