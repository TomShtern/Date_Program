package datingapp.core;

import java.time.Instant;
import java.util.UUID;

/** Represents a request to transition a match to the "Friend Zone". */
public record FriendRequest(
        UUID id, UUID fromUserId, UUID toUserId, Instant createdAt, Status status, Instant respondedAt) {

    /** Status of a friend zone request. */
    public enum Status {
        PENDING,
        ACCEPTED,
        DECLINED,
        EXPIRED
    }

    public static FriendRequest create(UUID fromUserId, UUID toUserId) {
        return new FriendRequest(UUID.randomUUID(), fromUserId, toUserId, Instant.now(), Status.PENDING, null);
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }
}
