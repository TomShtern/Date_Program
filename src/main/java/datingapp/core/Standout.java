package datingapp.core;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a standout profile selection for a user on a specific date.
 * Standouts are the top 10 daily matches ranked by compatibility score.
 */
public record Standout(
        UUID id,
        UUID seekerId,
        UUID standoutUserId,
        LocalDate featuredDate,
        int rank,
        int score,
        String reason,
        Instant createdAt,
        Instant interactedAt) {

    public Standout {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(seekerId, "seekerId required");
        Objects.requireNonNull(standoutUserId, "standoutUserId required");
        Objects.requireNonNull(featuredDate, "featuredDate required");
        Objects.requireNonNull(reason, "reason required");
        Objects.requireNonNull(createdAt, "createdAt required");

        if (rank < 1 || rank > 10) {
            throw new IllegalArgumentException("rank must be 1-10, got: " + rank);
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be 0-100, got: " + score);
        }
    }

    /** Factory for creating new standout. */
    public static Standout create(
            UUID seekerId, UUID standoutUserId, LocalDate date, int rank, int score, String reason) {
        return new Standout(
                UUID.randomUUID(), seekerId, standoutUserId, date, rank, score, reason, Instant.now(), null);
    }

    /** Factory for loading from database. */
    public static Standout fromDatabase(
            UUID id,
            UUID seekerId,
            UUID standoutUserId,
            LocalDate featuredDate,
            int rank,
            int score,
            String reason,
            Instant createdAt,
            Instant interactedAt) {
        return new Standout(id, seekerId, standoutUserId, featuredDate, rank, score, reason, createdAt, interactedAt);
    }

    public boolean hasInteracted() {
        return interactedAt != null;
    }

    public Standout withInteraction(Instant timestamp) {
        return new Standout(id, seekerId, standoutUserId, featuredDate, rank, score, reason, createdAt, timestamp);
    }

    /** Storage interface for standouts. */
    public interface Storage {
        void saveStandouts(UUID seekerId, java.util.List<Standout> standouts, LocalDate date);

        java.util.List<Standout> getStandouts(UUID seekerId, LocalDate date);

        void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date);

        int cleanup(LocalDate before);
    }
}
