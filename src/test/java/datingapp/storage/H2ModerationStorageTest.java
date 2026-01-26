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
