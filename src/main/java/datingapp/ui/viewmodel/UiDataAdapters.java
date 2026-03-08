package datingapp.ui.viewmodel;

import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UserContext;
import datingapp.app.usecase.profile.ProfileUseCases;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.model.Match;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.PageData;
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
 * <p>
 * Decouples ViewModels from {@code datingapp.core.storage.*} interfaces.
 * ViewModels depend on the inner interfaces ({@link UiUserStore},
 * {@link UiMatchDataAccess}),
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

        /**
         * Returns a bounded page of ACTIVE matches, newest first.
         *
         * @param userId the user whose active matches to page through
         * @param offset zero-based start index
         * @param limit  maximum number of items per page
         */
        PageData<Match> getPageOfActiveMatchesFor(UUID userId, int offset, int limit);

        /**
         * Returns the total number of ACTIVE matches for the user.
         * Used to derive total page count in the UI.
         */
        int countActiveMatchesFor(UUID userId);

        /** Returns the IDs of all users blocked by (or blocking) this user. */
        Set<UUID> getBlockedUserIds(UUID userId);

        /** Returns the IDs of users this user has already liked or passed on. */
        Set<UUID> getLikedOrPassedUserIds(UUID userId);

        /** Returns the like between two users, if any. */
        Optional<Like> getLike(UUID fromUserId, UUID toUserId);

        /** Deletes a like by its ID (used for undo/withdraw). */
        void deleteLike(UUID likeId);
    }

    /** UI-layer adapter interface for social and notification data access. */
    public interface UiSocialDataAccess {

        /**
         * Returns notifications for the user.
         *
         * @param userId     the user to fetch notifications for
         * @param unreadOnly if {@code true}, only returns unread notifications
         */
        List<Notification> getNotifications(UUID userId, boolean unreadOnly);

        /** Marks the notification with the given ID as read. */
        void markNotificationRead(UUID notificationId);
    }

    /** UI-layer adapter interface for private profile-note access. */
    public interface UiProfileNoteDataAccess {

        /** Returns the private note authored by {@code authorId} about {@code subjectId}, if present. */
        Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId);

        /** Creates or updates the private note authored by {@code authorId} about {@code subjectId}. */
        ProfileNote upsertProfileNote(UUID authorId, UUID subjectId, String content);

        /** Deletes the private note authored by {@code authorId} about {@code subjectId}. */
        boolean deleteProfileNote(UUID authorId, UUID subjectId);
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

    /** Bridges the UI layer to the core {@link CommunicationStorage} interface. */
    public static final class StorageUiSocialDataAccess implements UiSocialDataAccess {

        private final CommunicationStorage communicationStorage;

        public StorageUiSocialDataAccess(CommunicationStorage communicationStorage) {
            this.communicationStorage =
                    Objects.requireNonNull(communicationStorage, "communicationStorage cannot be null");
        }

        @Override
        public List<Notification> getNotifications(UUID userId, boolean unreadOnly) {
            return communicationStorage.getNotificationsForUser(userId, unreadOnly);
        }

        @Override
        public void markNotificationRead(UUID notificationId) {
            communicationStorage.markNotificationAsRead(notificationId);
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
        public PageData<Match> getPageOfActiveMatchesFor(UUID userId, int offset, int limit) {
            return interactionStorage.getPageOfActiveMatchesFor(userId, offset, limit);
        }

        @Override
        public int countActiveMatchesFor(UUID userId) {
            return interactionStorage.countActiveMatchesFor(userId);
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

    /** Bridges the UI layer to profile-note use-cases. */
    public static final class UseCaseUiProfileNoteDataAccess implements UiProfileNoteDataAccess {

        private final ProfileUseCases profileUseCases;

        public UseCaseUiProfileNoteDataAccess(ProfileUseCases profileUseCases) {
            this.profileUseCases = Objects.requireNonNull(profileUseCases, "profileUseCases cannot be null");
        }

        @Override
        public Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
            var result = profileUseCases.getProfileNote(
                    new ProfileUseCases.ProfileNoteQuery(UserContext.ui(authorId), subjectId));
            if (result.success()) {
                return Optional.of(result.data());
            }
            if (result.error().code() == UseCaseError.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw new IllegalStateException(result.error().message());
        }

        @Override
        public ProfileNote upsertProfileNote(UUID authorId, UUID subjectId, String content) {
            var result = profileUseCases.upsertProfileNote(
                    new ProfileUseCases.UpsertProfileNoteCommand(UserContext.ui(authorId), subjectId, content));
            if (!result.success()) {
                throw new IllegalStateException(result.error().message());
            }
            return result.data();
        }

        @Override
        public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
            var result = profileUseCases.deleteProfileNote(
                    new ProfileUseCases.DeleteProfileNoteCommand(UserContext.ui(authorId), subjectId));
            if (result.success()) {
                return Boolean.TRUE.equals(result.data());
            }
            if (result.error().code() == UseCaseError.Code.NOT_FOUND) {
                return false;
            }
            throw new IllegalStateException(result.error().message());
        }
    }

    /** Safe default note adapter used when a caller does not wire note support. */
    public static final class NoOpUiProfileNoteDataAccess implements UiProfileNoteDataAccess {

        @Override
        public Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
            return Optional.empty();
        }

        @Override
        public ProfileNote upsertProfileNote(UUID authorId, UUID subjectId, String content) {
            throw new UnsupportedOperationException("Profile-note access is not configured");
        }

        @Override
        public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
            return false;
        }
    }
}
