package datingapp.ui.viewmodel.data;

import datingapp.core.Match;
import datingapp.core.UserInteractions.Like;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * UI-layer adapter interface for match-related data access.
 * Combines match, like, and block queries that ViewModels need without exposing
 * individual storage interfaces from {@code datingapp.core.storage}.
 */
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
