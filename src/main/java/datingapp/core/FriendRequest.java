package datingapp.core;

import java.time.Instant;
import java.util.UUID;

/** Represents a request to transition a match to the "Friend Zone". */
public record FriendRequest(
        UUID id, UUID fromUserId, UUID toUserId, Instant createdAt, FriendRequestStatus status, Instant respondedAt) {

    public static FriendRequest create(UUID fromUserId, UUID toUserId) {
        return new FriendRequest(
                UUID.randomUUID(), fromUserId, toUserId, Instant.now(), FriendRequestStatus.PENDING, null);
    }

    public boolean isPending() {
        return status == FriendRequestStatus.PENDING;
    }
}
