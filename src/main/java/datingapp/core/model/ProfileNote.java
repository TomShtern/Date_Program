package datingapp.core.model;

import datingapp.core.AppClock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An internal note left by a moderator or admin on a user's profile. Admin-only
 * feature.
 */
public record ProfileNote(UUID authorId, UUID subjectId, String content, Instant createdAt, Instant updatedAt) {

    /** Maximum length for note content. */
    public static final int MAX_LENGTH = 500;

    public ProfileNote {
        Objects.requireNonNull(authorId, "authorId cannot be null");
        Objects.requireNonNull(subjectId, "subjectId cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");

        if (authorId.equals(subjectId)) {
            throw new IllegalArgumentException("Cannot create a note about yourself");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("Note content cannot be blank");
        }
        if (content.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Note content exceeds maximum length of " + MAX_LENGTH + " characters");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }

    /**
     * Creates a new profile note with current timestamp.
     *
     * @param authorId  ID of the user creating the note
     * @param subjectId ID of the user the note is about
     * @param content   the note content
     * @return a new ProfileNote
     * @throws IllegalArgumentException if content exceeds MAX_LENGTH or is blank
     */
    public static ProfileNote create(UUID authorId, UUID subjectId, String content) {
        Objects.requireNonNull(authorId, "authorId cannot be null");
        Objects.requireNonNull(subjectId, "subjectId cannot be null");
        Objects.requireNonNull(content, "content cannot be null");

        if (content.isBlank()) {
            throw new IllegalArgumentException("Note content cannot be blank");
        }
        if (content.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Note content exceeds maximum length of " + MAX_LENGTH + " characters");
        }
        if (authorId.equals(subjectId)) {
            throw new IllegalArgumentException("Cannot create a note about yourself");
        }

        Instant now = AppClock.now();
        return new ProfileNote(authorId, subjectId, content, now, now);
    }

    /**
     * Creates an updated version of this note with new content.
     *
     * @param newContent the new content
     * @return a new ProfileNote with updated content and timestamp
     */
    public ProfileNote withContent(String newContent) {
        if (newContent == null || newContent.isBlank()) {
            throw new IllegalArgumentException("Note content cannot be blank");
        }
        if (newContent.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Note content exceeds maximum length of " + MAX_LENGTH + " characters");
        }
        Instant now = AppClock.now();
        Instant updatedAt = now.isBefore(createdAt) ? createdAt : now;
        return new ProfileNote(authorId, subjectId, newContent, createdAt, updatedAt);
    }

    /**
     * Gets a preview of the note content (first 50 chars).
     *
     * @return truncated preview with ellipsis if content is longer
     */
    public String getPreview() {
        if (content.length() <= 50) {
            return content;
        }
        return content.substring(0, 47) + "...";
    }
}
