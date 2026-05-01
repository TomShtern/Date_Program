package datingapp.core.storage;

import datingapp.core.matching.CandidateFinder.GeoUtils;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Storage interface for User entities.
 * Defined in core, implemented in storage layer.
 */
public interface UserStorage {

    interface LockedUserAccess {
        Optional<User> get(UUID userId);

        void save(User user);
    }

    ZoneId CANONICAL_AGE_ZONE = ZoneOffset.UTC;

    /** Saves a user (insert or update). */
    void save(User user);

    /**
     * Gets a user by ID.
     *
     * @param id The user ID
     * @return An Optional containing the user if found, empty otherwise
     */
    Optional<User> get(UUID id);

    /** Finds all active users. */
    List<User> findActive();

    /**
     * Returns a single page of active users using the implementation's natural
     * {@link #findActive()} ordering.
     *
     * <p>
     * Default implementation loads all active users and slices in memory.
     * Storage adapters should override this with a count + ordered page query for
     * production-scale datasets.
     *
     * @param offset zero-based index of the first item (must be &ge; 0)
     * @param limit maximum items to return (must be &gt; 0)
     * @return a page of active users and pagination metadata
     */
    default PageData<User> getPageOfActiveUsers(int offset, int limit) {
        return pageUsers(findActive(), offset, limit);
    }

    /**
     * Pre-filtered candidate query for a given seeker's basic criteria.
     * Filters applied in SQL: active state, gender, age range, and optional
     * bounding-box distance.
     * Remaining filters (interaction exclusions, dealbreakers, mutual preferences)
     * are applied
     * in-memory by {@code CandidateFinder}.
     *
     * <p>
     * The default implementation falls back to {@link #findActive()} filtered
     * in-memory,
     * which is correct but unoptimized. Storage implementations should override
     * this to push
     * the filters to the database.
     *
     * @param excludeId     the seeker's own UUID (excluded from results)
     * @param genders       candidate genders the seeker is interested in
     * @param minAge        minimum acceptable candidate age (years)
     * @param maxAge        maximum acceptable candidate age (years)
     * @param seekerLat     seeker's latitude (degrees); use 0 when location not set
     * @param seekerLon     seeker's longitude (degrees); use 0 when location not
     *                      set
     * @param maxDistanceKm bounding-box radius in km; use large value (e.g. 50000)
     *                      to skip distance filter
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

        boolean applyDistanceFilter = maxDistanceKm > 0 && Double.isFinite(seekerLat) && Double.isFinite(seekerLon);

        return findActive().stream()
                .filter(u -> !u.getId().equals(excludeId))
                .filter(u -> genders.contains(u.getGender()))
                .filter(u -> u.getAge(CANONICAL_AGE_ZONE)
                        .map(age -> age >= minAge && age <= maxAge)
                        .orElse(false))
                .filter(u -> !applyDistanceFilter || isWithinDistance(seekerLat, seekerLon, u, maxDistanceKm))
                .toList();
    }

    private static boolean isWithinDistance(double seekerLat, double seekerLon, User candidate, int maxDistanceKm) {
        if (candidate == null || !candidate.hasLocation()) {
            return false;
        }

        double distanceKm = GeoUtils.distanceKm(seekerLat, seekerLon, candidate.getLat(), candidate.getLon());
        return distanceKm <= maxDistanceKm;
    }

    /** Finds all users regardless of state. */
    List<User> findAll();

    /**
     * Returns a single page of all users using the implementation's natural
     * {@link #findAll()} ordering.
     *
     * <p>
     * Default implementation loads all users and slices in memory. Storage
     * adapters should override this with a count + ordered page query for
     * production-scale datasets.
     *
     * @param offset zero-based index of the first item (must be &ge; 0)
     * @param limit maximum items to return (must be &gt; 0)
     * @return a page of all users and pagination metadata
     */
    default PageData<User> getPageOfAllUsers(int offset, int limit) {
        return pageUsers(findAll(), offset, limit);
    }

    /**
     * Finds multiple users by their IDs in a single batch query.
     * Returns a map of user ID to User. Missing IDs are not included in the map.
     *
     * <p>
     * The default implementation performs a single in-memory scan over
     * {@link #findAll()} and filters the requested IDs. Storage adapters should
     * override this method with a real indexed batch query when possible.
     *
     * @param ids the user IDs to look up
     * @return map of found users keyed by their ID
     */
    default Map<UUID, User> findByIds(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> requestedIds = Set.copyOf(ids);
        Map<UUID, User> result = new HashMap<>();
        for (User user : findAll()) {
            if (requestedIds.contains(user.getId())) {
                result.put(user.getId(), user);
            }
        }
        return result;
    }

    default Optional<User> findByEmail(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(user -> normalizedEmail.equals(user.getEmail()))
                .findFirst();
    }

    private static PageData<User> pageUsers(List<User> users, int offset, int limit) {
        Objects.requireNonNull(users, "users cannot be null");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        int total = users.size();
        if (offset >= total) {
            return new PageData<>(List.of(), total, total, limit);
        }

        int end = (int) Math.min((long) offset + limit, total);
        return new PageData<>(users.subList(offset, end), total, offset, limit);
    }

    /**
     * Deletes a user and all their associated data. When combined with CASCADE
     * DELETE on
     * foreign keys, this will automatically remove likes, matches, sessions, and
     * stats.
     *
     * @param id The user ID to delete
     */
    void delete(UUID id);

    /**
     * Permanently removes all soft-deleted users whose {@code deleted_at} is before
     * the
     * given threshold. This is a hard delete for GDPR compliance and storage
     * reclamation.
     *
     * @param threshold rows with {@code deleted_at < threshold} are purged
     * @return number of rows purged
     */
    default int purgeDeletedBefore(java.time.Instant threshold) {
        Objects.requireNonNull(threshold, "threshold cannot be null");
        throw new UnsupportedOperationException(
                "UserStorage implementation must override purgeDeletedBefore(Instant) to support cleanup");
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
     * @param authorId  ID of the note author
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
     * @param authorId  ID of the note author
     * @param subjectId ID of the user the note is about
     * @return true if a note was deleted
     */
    boolean deleteProfileNote(UUID authorId, UUID subjectId);

    /**
     * Executes an operation under a database-level row lock for the given user.
     * Implementations should acquire a distributed lock (e.g., FOR UPDATE clause)
     * to ensure atomicity across multiple instances. The operation runs within the
     * lock context, and any get/save calls within it should reuse the same
     * transaction handle.
     *
     * @param userId    the user ID to lock
     * @param operation the operation to execute within the lock
     * @throws NullPointerException          if userId or operation is null
     * @throws UnsupportedOperationException if the implementation does not provide
     *                                       a lock-aware implementation
     */
    default void executeWithUserLock(UUID userId, Runnable operation) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        throw new UnsupportedOperationException(
                "executeWithUserLock must be implemented with database-level locking by the storage implementation");
    }

    default <T> T withUserLock(UUID userId, Function<LockedUserAccess, T> operation) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");

        AtomicReference<T> result = new AtomicReference<>();
        LockedUserAccess access = new LockedUserAccess() {
            @Override
            public Optional<User> get(UUID lockedUserId) {
                return UserStorage.this.get(lockedUserId);
            }

            @Override
            public void save(User user) {
                UserStorage.this.save(user);
            }
        };
        executeWithUserLock(userId, () -> result.set(operation.apply(access)));
        return result.get();
    }
}
