package datingapp.core.storage;

import datingapp.core.UserInteractions.Block;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Storage interface for Block entities.
 * Defined in core, implemented in storage layer.
 */
public interface BlockStorage {

    /** Saves a block. */
    void save(Block block);

    /** Returns true if EITHER user has blocked the other. Block is bidirectional in effect. */
    boolean isBlocked(UUID userA, UUID userB);

    /** Returns all user IDs that the given user should not see. */
    Set<UUID> getBlockedUserIds(UUID userId);

    /** Returns all blocks created by the given user. */
    List<Block> findByBlocker(UUID blockerId);

    /**
     * Deletes a block between two users.
     *
     * @param blockerId the user who created the block
     * @param blockedId the user who was blocked
     * @return true if a block was deleted, false if no block existed
     */
    boolean delete(UUID blockerId, UUID blockedId);

    /** Count blocks GIVEN by a user (users they blocked). */
    int countBlocksGiven(UUID userId);

    /** Count blocks RECEIVED by a user (users who blocked them). */
    int countBlocksReceived(UUID userId);
}
