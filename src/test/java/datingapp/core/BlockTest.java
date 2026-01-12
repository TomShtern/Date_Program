package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for Block domain model. */
class BlockTest {

    @Test
    @DisplayName("Cannot block yourself")
    void cannotBlockSelf() {
        UUID userId = UUID.randomUUID();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Block.create(userId, userId),
                "Should throw when trying to block yourself");
        assertNotNull(ex);
    }

    @Test
    @DisplayName("Block creation succeeds with different users")
    void blockCreationSucceeds() {
        UUID blockerId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();

        Block block = Block.create(blockerId, blockedId);

        assertNotNull(block.id(), "Block should have an ID");
        assertEquals(blockerId, block.blockerId(), "Blocker ID should match");
        assertEquals(blockedId, block.blockedId(), "Blocked ID should match");
        assertNotNull(block.createdAt(), "Created timestamp should be set");
    }

    @Test
    @DisplayName("Block IDs are unique")
    void blockIdsAreUnique() {
        UUID blockerId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();

        Block block1 = Block.create(blockerId, blockedId);
        Block block2 = Block.create(blockerId, blockedId);

        assertNotEquals(block1.id(), block2.id(), "Different blocks should have different IDs");
    }

    @Test
    @DisplayName("Block with null blocker ID throws NullPointerException")
    void nullBlockerIdThrows() {
        UUID blockedId = UUID.randomUUID();

        NullPointerException ex = assertThrows(NullPointerException.class, () -> Block.create(null, blockedId));
        assertNotNull(ex);
    }

    @Test
    @DisplayName("Block with null blocked ID throws NullPointerException")
    void nullBlockedIdThrows() {
        UUID blockerId = UUID.randomUUID();

        NullPointerException ex = assertThrows(NullPointerException.class, () -> Block.create(blockerId, null));
        assertNotNull(ex);
    }
}
