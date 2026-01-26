package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.DailyService.DailyPickStorage;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration test for H2DailyPickViewStorage. Tests database operations for tracking daily pick
 * views.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class H2DailyPickViewStorageTest {

    private DatabaseManager dbManager;
    private DailyPickStorage storage;

    @BeforeEach
    void setUp() {
        // Use in-memory H2 database for testing
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:test_daily_pick_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        DatabaseManager.resetInstance();
        dbManager = DatabaseManager.getInstance();
        storage = new H2MetricsStorage(dbManager).dailyPicks();
    }

    @AfterEach
    void tearDown() {
        dbManager.shutdown();
        DatabaseManager.resetInstance();
    }

    @Test
    void markViewed_createsRecord() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.now();

        storage.markViewed(userId, date);

        assertTrue(storage.hasViewed(userId, date), "Should return true after marking as viewed");
    }

    @Test
    void hasViewed_returnsFalse_beforeMarking() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.now();

        assertFalse(storage.hasViewed(userId, date), "Should return false before marking as viewed");
    }

    // Note: hasViewed_returnsTrue_afterMarking was removed as it was identical to markViewed_createsRecord

    @Test
    void hasViewed_differentDates_independent() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        storage.markViewed(userId, today);

        assertTrue(storage.hasViewed(userId, today), "Should return true for marked date");
        assertFalse(storage.hasViewed(userId, yesterday), "Should return false for unmarked date");
    }

    @Test
    void hasViewed_differentUsers_independent() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        LocalDate date = LocalDate.now();

        storage.markViewed(user1, date);

        assertTrue(storage.hasViewed(user1, date), "User1 should have viewed");
        assertFalse(storage.hasViewed(user2, date), "User2 should not have viewed");
    }

    @Test
    void markViewed_multipleTimes_upserts() {
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.now();

        // Mark multiple times (should upsert, not create duplicates)
        storage.markViewed(userId, date);
        storage.markViewed(userId, date);
        storage.markViewed(userId, date);

        // Should still return true (no error from duplicates)
        assertTrue(storage.hasViewed(userId, date));
    }

    @Test
    void cleanup_removesOldRecords() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        LocalDate threeDaysAgo = today.minusDays(3);
        LocalDate fiveDaysAgo = today.minusDays(5);

        // Mark views for different dates
        storage.markViewed(userId, fiveDaysAgo);
        storage.markViewed(userId, threeDaysAgo);
        storage.markViewed(userId, today);

        // Cleanup records before 4 days ago
        int deleted = storage.cleanup(today.minusDays(4));

        // Should have deleted the 5-days-ago record
        assertEquals(1, deleted, "Should delete 1 old record");
        assertFalse(storage.hasViewed(userId, fiveDaysAgo), "Old record should be deleted");
        assertTrue(storage.hasViewed(userId, threeDaysAgo), "Recent record should remain");
        assertTrue(storage.hasViewed(userId, today), "Today's record should remain");
    }

    @Test
    void cleanup_keepsRecentRecords() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        storage.markViewed(userId, today);
        storage.markViewed(userId, yesterday);

        // Cleanup very old records (before 30 days ago)
        int deleted = storage.cleanup(today.minusDays(30));

        // Should not delete any recent records
        assertEquals(0, deleted, "Should not delete recent records");
        assertTrue(storage.hasViewed(userId, today));
        assertTrue(storage.hasViewed(userId, yesterday));
    }

    @Test
    void cleanup_noRecords_returnsZero() {
        LocalDate today = LocalDate.now();

        int deleted = storage.cleanup(today.minusDays(30));

        assertEquals(0, deleted, "Should return 0 when no records to delete");
    }

    @Test
    void cleanup_allOldRecords_deletesAll() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        LocalDate tenDaysAgo = LocalDate.now().minusDays(10);

        storage.markViewed(user1, tenDaysAgo);
        storage.markViewed(user2, tenDaysAgo);

        // Cleanup everything before today
        int deleted = storage.cleanup(LocalDate.now());

        assertEquals(2, deleted, "Should delete all old records");
        assertFalse(storage.hasViewed(user1, tenDaysAgo));
        assertFalse(storage.hasViewed(user2, tenDaysAgo));
    }
}
