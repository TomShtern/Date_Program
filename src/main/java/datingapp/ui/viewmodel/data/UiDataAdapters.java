package datingapp.ui.viewmodel.data;

import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.UserInteractions.Like;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * UI-layer adapter interfaces and implementations for data access.
 *
 * <p>Decouples ViewModels from {@code datingapp.core.storage.*} interfaces.
 * ViewModels depend on the inner interfaces ({@link UiUserStore}, {@link UiMatchDataAccess}),
 * while the inner implementations bridge to core storage.
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class UiDataAdapters {

    private UiDataAdapters() {}

    // ── Interfaces ──────────────────────────────────────────────────────────

    /** UI-layer adapter interface for user data access. */
    public interface UiUserStore {

        /** Returns all users regardless of state. */
        List<User> findAll();

        /** Persists the given user (insert or update). */
        void save(User user);

        /** Batch lookup of users by their IDs. */
        Map<UUID, User> findByIds(Set<UUID> ids);
    }

    /** UI-layer adapter interface for match-related data access. */
    public interface UiMatchDataAccess {

        /** Returns all ACTIVE matches for the given user. */
        List<Match> getActiveMatchesFor(UUID userId);

        /** Returns ALL matches (including ended) for the given user. */
        List<Match> getAllMatchesFor(UUID userId);

        /** Returns the IDs of all users blocked by (or blocking) this user. */
        Set<UUID> getBlockedUserIds(UUID userId);

        /** Returns the IDs of users this user has already liked or passed on. */
        Set<UUID> getLikedOrPassedUserIds(UUID userId);

        /** Returns the like between two users, if any. */
        Optional<Like> getLike(UUID fromUserId, UUID toUserId);

        /** Deletes a like by its ID (used for undo/withdraw). */
        void deleteLike(UUID likeId);
    }

    // ── Implementations ─────────────────────────────────────────────────────

    /** Bridges the UI layer to the core {@link UserStorage} interface. */
    public static final class StorageUiUserStore implements UiUserStore {

        private final UserStorage userStorage;

        public StorageUiUserStore(UserStorage userStorage) {
            this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        }

        @Override
        public List<User> findAll() {
            return userStorage.findAll();
        }

        @Override
        public void save(User user) {
            userStorage.save(user);
        }

        @Override
        public Map<UUID, User> findByIds(Set<UUID> ids) {
            return userStorage.findByIds(ids);
        }
    }

    /** Bridges the UI layer to the core match/like/block storage interfaces. */
    public static final class StorageUiMatchDataAccess implements UiMatchDataAccess {

        private final InteractionStorage interactionStorage;
        private final TrustSafetyStorage trustSafetyStorage;

        public StorageUiMatchDataAccess(InteractionStorage interactionStorage, TrustSafetyStorage trustSafetyStorage) {
            this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
            this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
        }

        @Override
        public List<Match> getActiveMatchesFor(UUID userId) {
            return interactionStorage.getActiveMatchesFor(userId);
        }

        @Override
        public List<Match> getAllMatchesFor(UUID userId) {
            return interactionStorage.getAllMatchesFor(userId);
        }

        @Override
        public Set<UUID> getBlockedUserIds(UUID userId) {
            return trustSafetyStorage.getBlockedUserIds(userId);
        }

        @Override
        public Set<UUID> getLikedOrPassedUserIds(UUID userId) {
            return interactionStorage.getLikedOrPassedUserIds(userId);
        }

        @Override
        public Optional<Like> getLike(UUID fromUserId, UUID toUserId) {
            return interactionStorage.getLike(fromUserId, toUserId);
        }

        @Override
        public void deleteLike(UUID likeId) {
            interactionStorage.delete(likeId);
        }
    }
}
