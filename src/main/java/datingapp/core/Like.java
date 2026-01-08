package datingapp.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a like or pass action from one user to another.
 * Immutable after creation.
 */
public record Like(
        UUID id,
        UUID whoLikes,
        UUID whoGotLiked,
        Direction direction,
        Instant createdAt) {

    /**
     * The direction of a like action.
     * LIKE = interested in the user
     * PASS = not interested in the user
     */
    public enum Direction {
        LIKE, PASS
    }

    public Like {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(whoLikes, "whoLikes cannot be null");
        Objects.requireNonNull(whoGotLiked, "whoGotLiked cannot be null");
        Objects.requireNonNull(direction, "direction cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");

        if (whoLikes.equals(whoGotLiked)) {
            throw new IllegalArgumentException("Cannot like yourself");
        }
    }

    /**
     * Creates a new Like with auto-generated ID and current timestamp.
     */
    public static Like create(UUID whoLikes, UUID whoGotLiked, Direction direction) {
        return new Like(UUID.randomUUID(), whoLikes, whoGotLiked, direction, Instant.now());
    }
}
