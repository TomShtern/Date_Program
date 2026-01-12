package datingapp.core;

import java.util.Set;
import java.util.UUID;

/** Storage interface for Block entities. Defined in core, implemented in storage layer. */
public interface BlockStorage {

    /** Saves a block. */
    void save(Block block);

    /** Returns true if EITHER user has blocked the other. Block is bidirectional in effect. */
    boolean isBlocked(UUID userA, UUID userB);

    /**
     * Returns all user IDs that the given user should not see. Includes users they blocked AND users
     * who blocked them.
     */
    Set<UUID> getBlockedUserIds(UUID userId);

    // === Statistics Methods (Phase 0.5b) ===

    /** Count blocks GIVEN by a user (users they blocked). */
    int countBlocksGiven(UUID userId);

    /** Count blocks RECEIVED by a user (users who blocked them). */
    int countBlocksReceived(UUID userId);
}
