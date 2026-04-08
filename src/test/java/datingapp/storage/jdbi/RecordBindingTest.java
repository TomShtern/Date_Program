package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.storage.DatabaseManager;
import java.time.Instant;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for record binding in JDBI DAOs. Validates that records bound with
 * @BindMethods work correctly for persistence operations.
 *
 * Addresses findings F44 and F45 from SOURCE_CODE_ONLY_ISSUES_AUDIT_2026-03-28.md
 */
@Timeout(10)
class RecordBindingTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    private DatabaseManager dbManager;
    private Jdbi jdbi;
    private JdbiMatchmakingStorage matchmakingStorage;
    private JdbiTrustSafetyStorage trustSafetyStorage;

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
        String dbName = "record_binding_test_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        dbManager = DatabaseManager.getInstance();

        jdbi = Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException e) {
                        throw new DatabaseManager.StorageException("Failed to get database connection", e);
                    }
                })
                .installPlugin(new SqlObjectPlugin());

        jdbi.registerArgument(new JdbiTypeCodecs.EnumSetSqlCodec.EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new JdbiTypeCodecs.EnumSetSqlCodec.InterestColumnMapper());
        JdbiTypeCodecs.registerInstantCodec(jdbi);

        matchmakingStorage = new JdbiMatchmakingStorage(jdbi);
        trustSafetyStorage = jdbi.onDemand(JdbiTrustSafetyStorage.class);

        // Insert test users for foreign key constraints
        jdbi.withHandle(handle -> {
            handle.execute(
                    "INSERT INTO users (id, name, email, created_at, updated_at, state) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "User One",
                    "user1@test.com",
                    Instant.now(),
                    Instant.now(),
                    "ACTIVE");
            handle.execute(
                    "INSERT INTO users (id, name, email, created_at, updated_at, state) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "User Two",
                    "user2@test.com",
                    Instant.now(),
                    Instant.now(),
                    "ACTIVE");
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(PROFILE_PROPERTY);
        if (dbManager != null) {
            dbManager.shutdown();
            DatabaseManager.resetInstance();
        }
    }

    @Test
    @DisplayName("F44: Like record can be persisted with @BindMethods")
    void likeRecordBindingWorks() {
        UUID user1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID user2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Like like = Like.create(user1, user2, Like.Direction.LIKE);

        // Should not throw when saving
        matchmakingStorage.save(like);

        // Verify the like was persisted by checking existence
        boolean exists = matchmakingStorage.exists(user1, user2);
        assertTrue(exists, "Like should exist in database");
    }

    @Test
    @DisplayName("Like deletion enforces the owner when soft-deleting by ID")
    void likeDeletionEnforcesOwnerWhenDeletingById() {
        UUID user1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID user2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Like like = Like.create(user1, user2, Like.Direction.LIKE);
        matchmakingStorage.save(like);

        assertFalse(matchmakingStorage.deleteLikeOwnedBy(user2, like.id()));
        assertTrue(matchmakingStorage.exists(user1, user2), "Like should remain when owner does not match");

        assertTrue(matchmakingStorage.deleteLikeOwnedBy(user1, like.id()));
        assertFalse(matchmakingStorage.exists(user1, user2), "Like should be soft-deleted for the owning user");
    }

    @Test
    @DisplayName("F45: Block record can be persisted with @BindMethods")
    void blockRecordBindingWorks() {
        UUID user1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID user2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Block block = new Block(UUID.randomUUID(), user1, user2, Instant.now());

        // Should not throw when saving
        trustSafetyStorage.save(block);

        // Verify the block was persisted
        boolean isBlocked = trustSafetyStorage.isBlocked(user1, user2);
        assertTrue(isBlocked, "Block should be persisted");
    }

    @Test
    @DisplayName("Block can be recreated after delete without duplicating the pair")
    void deletedBlockCanBeRecreated() {
        UUID user1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID user2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        trustSafetyStorage.save(Block.create(user1, user2));
        assertTrue(trustSafetyStorage.deleteBlock(user1, user2));
        assertFalse(trustSafetyStorage.isBlocked(user1, user2), "Block should be hidden after delete");

        trustSafetyStorage.save(Block.create(user1, user2));

        assertTrue(trustSafetyStorage.isBlocked(user1, user2), "Block should be active after recreation");
        long pairRowCount = jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT COUNT(*) FROM blocks WHERE blocker_id = :blockerId AND blocked_id = :blockedId")
                .bind("blockerId", user1)
                .bind("blockedId", user2)
                .mapTo(Long.class)
                .one());
        assertEquals(
                1L,
                pairRowCount,
                "Recreating a block should revive the existing pair row rather than creating duplicates");
    }

    @Test
    @DisplayName("F45: Report record can be persisted with @BindMethods")
    void reportRecordBindingWorks() {
        UUID user1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID user2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Report report = new Report(UUID.randomUUID(), user1, user2, Report.Reason.SPAM, "Test report", Instant.now());

        // Should not throw when saving
        trustSafetyStorage.save(report);

        // Verify the report was persisted
        boolean hasReported = trustSafetyStorage.hasReported(user1, user2);
        assertTrue(hasReported, "Report should be persisted");
    }
}
