package datingapp.core;

import datingapp.core.Social.FriendRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage interface for FriendRequest entities. */
public interface FriendRequestStorage {
    void save(FriendRequest request);

    void update(FriendRequest request);

    Optional<FriendRequest> get(UUID id);

    Optional<FriendRequest> getPendingBetween(UUID user1, UUID user2);

    List<FriendRequest> getPendingForUser(UUID userId);

    void delete(UUID id);
}
