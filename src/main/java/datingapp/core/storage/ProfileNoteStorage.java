package datingapp.core.storage;

import datingapp.core.User.ProfileNote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage interface for private profile notes.
 * Defined in core, implemented in storage layer.
 */
public interface ProfileNoteStorage {

    /**
     * Saves or updates a note about another user.
     *
     * @param note the profile note to save
     */
    void save(ProfileNote note);

    /**
     * Gets a user's note about another user.
     *
     * @param authorId ID of the note author
     * @param subjectId ID of the user the note is about
     * @return the note if it exists
     */
    Optional<ProfileNote> get(UUID authorId, UUID subjectId);

    /**
     * Gets all notes created by a user.
     *
     * @param authorId ID of the note author
     * @return list of all notes by this user
     */
    List<ProfileNote> getAllByAuthor(UUID authorId);

    /**
     * Deletes a note.
     *
     * @param authorId ID of the note author
     * @param subjectId ID of the user the note is about
     * @return true if a note was deleted
     */
    boolean delete(UUID authorId, UUID subjectId);
}
