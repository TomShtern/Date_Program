package datingapp.core.storage;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Maps Clerk user ids (the session token's {@code sub}) to local user ids. */
public interface AuthStorage {

    Optional<UUID> findUserIdByClerkId(String clerkUserId);

    /**
     * Links a Clerk user to a local user.
     *
     * @return {@code true} if the link was created, {@code false} if that Clerk id (or that local user) is already
     *     linked, in which case the caller should re-read with {@link #findUserIdByClerkId}
     */
    boolean linkClerkId(String clerkUserId, UUID userId, Instant createdAt);

    void deleteIdentityForUser(UUID userId);

    /**
     * Removes the "left while reported" flag for a Clerk user, if one exists.
     *
     * @return {@code true} if a flag existed, meaning this Clerk user's new profile must start under review
     */
    boolean consumeDepartureFlag(String clerkUserId);
}
