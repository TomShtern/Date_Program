package datingapp.core.storage;

import datingapp.core.model.Gender;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Pre-filtered candidate query for a given seeker's basic criteria.
     * Filters applied in SQL: active state, gender, age range, and optional bounding-box distance.
     * Remaining filters (interaction exclusions, dealbreakers, mutual preferences) are applied
     * in-memory by {@code CandidateFinder}.
     *
     * <p>The default implementation falls back to {@link #findActive()} filtered in-memory,
     * which is correct but unoptimized. Storage implementations should override this to push
     * the filters to the database.
     *
     * @param excludeId     the seeker's own UUID (excluded from results)
     * @param genders       candidate genders the seeker is interested in
     * @param minAge        minimum acceptable candidate age (years)
     * @param maxAge        maximum acceptable candidate age (years)
     * @param seekerLat     seeker's latitude (degrees); use 0 when location not set
     * @param seekerLon     seeker's longitude (degrees); use 0 when location not set
     * @param maxDistanceKm bounding-box radius in km; use large value (e.g. 50000) to skip distance filter
     * @return active users matching base criteria, unsorted
     */
    default List<User> findCandidates(
            UUID excludeId,
            Set<Gender> genders,
            int minAge,
            int maxAge,
            double seekerLat,
            double seekerLon,
            int maxDistanceKm) {
        if (genders == null || genders.isEmpty()) {
            return List.of();
        }
        return findActive().stream()
                .filter(u -> !u.getId().equals(excludeId))
                .filter(u -> genders.contains(u.getGender()))
                .filter(u -> u.getAge() >= minAge && u.getAge() <= maxAge)
                .toList();
    }

    /** Finds all users regardless of state. */
    List<User> findAll();

    /**
     * Finds multiple users by their IDs in a single batch query.
     * Returns a map of user ID to User. Missing IDs are not included in the map.
     *
     * @param ids the user IDs to look up
     * @return map of found users keyed by their ID
     */
    default Map<UUID, User> findByIds(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, User> result = new java.util.HashMap<>();
        for (UUID id : ids) {
            User user = get(id);
            if (user != null) {
                result.put(id, user);
            }
        }
        return result;
    }

    /**
     * Deletes a user and all their associated data. When combined with CASCADE DELETE on
     * foreign keys, this will automatically remove likes, matches, sessions, and stats.
     *
     * @param id The user ID to delete
     */
    void delete(UUID id);

    /**
     * Permanently removes all soft-deleted users whose {@code deleted_at} is before the
     * given threshold. This is a hard delete for GDPR compliance and storage reclamation.
     *
     * @param threshold rows with {@code deleted_at < threshold} are purged
     * @return number of rows purged
     */
    default int purgeDeletedBefore(java.time.Instant threshold) {
        return 0;
    }

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
