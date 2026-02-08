package datingapp.core;

import java.time.Instant;

/**
 * Contract for entities that support soft-deletion.
 *
 * <p>Soft-deleted entities are not physically removed from the database;
 * instead, a {@code deleted_at} timestamp is set. Storage queries filter
 * out rows where {@code deleted_at IS NOT NULL} unless explicitly requested.
 *
 * <p>Purge operations permanently remove rows whose {@code deleted_at} is
 * older than {@link AppConfig#softDeleteRetentionDays()}.
 */
public interface SoftDeletable {

    /** Returns the deletion timestamp, or {@code null} if not deleted. */
    Instant getDeletedAt();

    /** Marks this entity as soft-deleted at the given instant. */
    void markDeleted(Instant deletedAt);

    /** Returns {@code true} if this entity has been soft-deleted. */
    default boolean isDeleted() {
        return getDeletedAt() != null;
    }
}
