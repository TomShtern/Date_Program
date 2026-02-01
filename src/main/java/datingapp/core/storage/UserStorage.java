package datingapp.core.storage;

import datingapp.core.User;
import datingapp.core.User.ProfileNote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage interface for User entities.
 * Defined in core, implemented in storage layer.
 */
public interface UserStorage {

    /** Saves a user (insert or update). */
    void save(User user);

    /**
     * Gets a user by ID.
     *
     * @param id The user ID
     * @return The user, or null if not found
     */
    User get(UUID id);

    /** Finds all active users. */
    List<User> findActive();

    /** Finds all users regardless of state. */
    List<User> findAll();

    /**
     * Deletes a user and all their associated data. When combined with CASCADE DELETE on
     * foreign keys, this will automatically remove likes, matches, sessions, and stats.
     *
     * @param id The user ID to delete
     */
    void delete(UUID id);

    // ═══════════════════════════════════════════════════════════════
    // Profile Notes (from ProfileNoteStorage)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Saves or updates a note about another user.
     *
     * @param note the profile note to save
     */
    void saveProfileNote(ProfileNote note);

    /**
     * Gets a user's note about another user.
     *
     * @param authorId ID of the note author
     * @param subjectId ID of the user the note is about
     * @return the note if it exists
     */
    Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId);

    /**
     * Gets all notes created by a user.
     *
     * @param authorId ID of the note author
     * @return list of all notes by this user
     */
    List<ProfileNote> getProfileNotesByAuthor(UUID authorId);

    /**
     * Deletes a note.
     *
     * @param authorId ID of the note author
     * @param subjectId ID of the user the note is about
     * @return true if a note was deleted
     */
    boolean deleteProfileNote(UUID authorId, UUID subjectId);
}
