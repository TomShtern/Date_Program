package datingapp.core.model;

import datingapp.core.AppClock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A private note that a user can attach to another user's profile. Notes are
 * only visible to the
 * author - the subject never sees them.
 *
 * <p>
 * Use cases:
 *
 * <ul>
 * <li>Remember where you met someone ("Coffee shop downtown")
 * <li>Note conversation topics ("Loves hiking, has a dog named Max")
 * <li>Track date plans ("Dinner Thursday @ Olive Garden")
 * </ul>
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
        return new ProfileNote(authorId, subjectId, newContent, createdAt, AppClock.now());
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
