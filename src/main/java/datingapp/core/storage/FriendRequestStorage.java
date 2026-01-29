package datingapp.core.storage;

import datingapp.core.Social.FriendRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage interface for FriendRequest entities.
 * Defined in core, implemented in storage layer.
 */
public interface FriendRequestStorage {

    /** Saves a new friend request. */
    void save(FriendRequest request);

    /** Updates an existing friend request (e.g., status change). */
    void update(FriendRequest request);

    /** Gets a friend request by ID. */
    Optional<FriendRequest> get(UUID id);

    /** Gets a pending friend request between two users (either direction). */
    Optional<FriendRequest> getPendingBetween(UUID user1, UUID user2);

    /** Gets all pending friend requests for a user (requests they received). */
    List<FriendRequest> getPendingForUser(UUID userId);

    /** Deletes a friend request by ID. */
    void delete(UUID id);
}
