package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Report;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("H2ModerationStorage")
class H2ModerationStorageTest {

    private H2ModerationStorage.Blocks blocks;
    private H2ModerationStorage.Reports reports;

    @BeforeEach
    void setUp() {
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:moderation-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        DatabaseManager.resetInstance();
        DatabaseManager dbManager = DatabaseManager.getInstance();

        blocks = new H2ModerationStorage.Blocks(dbManager);
        reports = new H2ModerationStorage.Reports(dbManager);
    }

    @Test
    @DisplayName("Saves and queries blocks")
    void savesAndQueriesBlocks() {
        UUID blocker = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();

        Block block = Block.create(blocker, blocked);
        blocks.save(block);

        assertTrue(blocks.isBlocked(blocker, blocked));
        assertEquals(Set.of(blocked), blocks.getBlockedUserIds(blocker));
        assertEquals(1, blocks.countBlocksGiven(blocker));
        assertEquals(1, blocks.countBlocksReceived(blocked));
    }

    @Test
    @DisplayName("Deletes blocks successfully")
    void deletesBlocksSuccessfully() {
        UUID blocker = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();

        Block block = Block.create(blocker, blocked);
        blocks.save(block);

        assertTrue(blocks.isBlocked(blocker, blocked), "Block should exist");

        boolean deleted = blocks.delete(blocker, blocked);

        assertTrue(deleted, "Delete should return true");
        assertFalse(blocks.isBlocked(blocker, blocked), "Block should no longer exist");
        assertEquals(0, blocks.countBlocksGiven(blocker), "Blocker should have 0 blocks");
        assertEquals(0, blocks.countBlocksReceived(blocked), "Blocked user should have 0 blocks received");
    }

    @Test
    @DisplayName("Returns false when deleting non-existent block")
    void returnsFalseWhenDeletingNonexistentBlock() {
        UUID blocker = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();

        boolean deleted = blocks.delete(blocker, blocked);

        assertFalse(deleted, "Delete should return false when block doesn't exist");
    }

    @Test
    @DisplayName("Finds blocks by blocker")
    void findsBlocksByBlocker() {
        UUID blocker = UUID.randomUUID();
        UUID blocked1 = UUID.randomUUID();
        UUID blocked2 = UUID.randomUUID();
        UUID blocked3 = UUID.randomUUID();

        Block block1 = Block.create(blocker, blocked1);
        Block block2 = Block.create(blocker, blocked2);
        Block block3 = Block.create(blocker, blocked3);

        blocks.save(block1);
        blocks.save(block2);
        blocks.save(block3);

        var foundBlocks = blocks.findByBlocker(blocker);

        assertEquals(3, foundBlocks.size(), "Should find 3 blocks");
        assertTrue(foundBlocks.stream().anyMatch(b -> b.blockedId().equals(blocked1)), "Should contain block1");
        assertTrue(foundBlocks.stream().anyMatch(b -> b.blockedId().equals(blocked2)), "Should contain block2");
        assertTrue(foundBlocks.stream().anyMatch(b -> b.blockedId().equals(blocked3)), "Should contain block3");
    }

    @Test
    @DisplayName("Saves and queries reports")
    void savesAndQueriesReports() {
        UUID reporter = UUID.randomUUID();
        UUID reported = UUID.randomUUID();

        Report report = Report.create(reporter, reported, Report.Reason.SPAM, "spam");
        reports.save(report);

        assertTrue(reports.hasReported(reporter, reported));
        assertEquals(1, reports.countReportsBy(reporter));
        assertEquals(1, reports.countReportsAgainst(reported));
        assertFalse(reports.hasReported(reporter, UUID.randomUUID()));
    }
}
