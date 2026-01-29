package datingapp.core.storage;

import datingapp.core.User;
import java.util.List;
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
}
