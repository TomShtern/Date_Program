package datingapp.core.testutil;

import datingapp.core.BlockStorage;
import datingapp.core.UserInteractions.Block;
import java.util.*;

/**
 * In-memory BlockStorage for testing. Thread-safe and provides test helper methods.
 */
public class InMemoryBlockStorage implements BlockStorage {
    private final Set<Block> blocks = new HashSet<>();

    @Override
    public void save(Block block) {
        blocks.add(block);
    }

    @Override
    public boolean isBlocked(UUID userA, UUID userB) {
        return blocks.stream()
                .anyMatch(b -> (b.blockerId().equals(userA) && b.blockedId().equals(userB))
                        || (b.blockerId().equals(userB) && b.blockedId().equals(userA)));
    }

    @Override
    public Set<UUID> getBlockedUserIds(UUID userId) {
        Set<UUID> result = new HashSet<>();
        for (Block block : blocks) {
            if (block.blockerId().equals(userId)) {
                result.add(block.blockedId());
            } else if (block.blockedId().equals(userId)) {
                result.add(block.blockerId());
            }
        }
        return result;
    }

    @Override
    public int countBlocksGiven(UUID userId) {
        return (int) blocks.stream().filter(b -> b.blockerId().equals(userId)).count();
    }

    @Override
    public int countBlocksReceived(UUID userId) {
        return (int) blocks.stream().filter(b -> b.blockedId().equals(userId)).count();
    }

    // === Test Helpers ===

    /** Clears all blocks */
    public void clear() {
        blocks.clear();
    }

    /** Returns number of blocks stored */
    public int size() {
        return blocks.size();
    }
}
