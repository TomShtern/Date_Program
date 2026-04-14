package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.storage.DatabaseManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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

    @Test
    @DisplayName("Report can be recreated after delete without duplicating the pair")
    void deletedReportCanBeRecreated() {
        UUID user1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID user2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Report original =
                new Report(UUID.randomUUID(), user1, user2, Report.Reason.SPAM, "First report", Instant.now());
        trustSafetyStorage.save(original);

        jdbi.useHandle(handle -> handle.createUpdate("""
                        UPDATE reports
                        SET deleted_at = CURRENT_TIMESTAMP
                        WHERE reporter_id = :reporterId AND reported_user_id = :reportedUserId
                        """)
                .bind("reporterId", user1)
                .bind("reportedUserId", user2)
                .execute());

        assertFalse(trustSafetyStorage.hasReported(user1, user2), "Report should be hidden after soft delete");

        Report recreated = new Report(
                UUID.randomUUID(), user1, user2, Report.Reason.HARASSMENT, "Replacement report", Instant.now());
        trustSafetyStorage.save(recreated);

        assertTrue(trustSafetyStorage.hasReported(user1, user2), "Report should be active after recreation");
        long pairRowCount = jdbi.withHandle(handle -> handle.createQuery(
                        "SELECT COUNT(*) FROM reports WHERE reporter_id = :reporterId AND reported_user_id = :reportedUserId")
                .bind("reporterId", user1)
                .bind("reportedUserId", user2)
                .mapTo(Long.class)
                .one());
        assertEquals(
                1L,
                pairRowCount,
                "Recreating a report should revive the existing pair row rather than creating duplicates");
    }

    @Test
    @DisplayName("Undo state can be persisted and retrieved via undoStorage()")
    void undoStatePersistenceWorks() {
        UUID user1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID user2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Like like = Like.create(user1, user2, Like.Direction.LIKE);
        Instant expiresAt = Instant.now().plusSeconds(3600);
        Undo state = new Undo(user1, like, null, expiresAt);

        matchmakingStorage.undoStorage().save(state);

        Optional<Undo> found = matchmakingStorage.undoStorage().findByUserId(user1);
        assertTrue(found.isPresent(), "Undo state should be retrievable by user ID");
        Undo retrieved = found.get();
        assertEquals(user1, retrieved.userId());
        assertEquals(like.id(), retrieved.like().id());
        assertEquals(like.whoLikes(), retrieved.like().whoLikes());
        assertEquals(like.whoGotLiked(), retrieved.like().whoGotLiked());
        assertEquals(like.direction(), retrieved.like().direction());
        // H2 truncates Instant nanoseconds; compare with millisecond tolerance
        assertTrue(
                Duration.between(like.createdAt(), retrieved.like().createdAt())
                                .abs()
                                .toMillis()
                        < 100,
                "like.createdAt should round-trip within 100ms");
        assertNull(retrieved.matchId());
        assertTrue(
                Duration.between(expiresAt, retrieved.expiresAt()).abs().toMillis() < 100,
                "expiresAt should round-trip within 100ms");
    }

    @Test
    @DisplayName("Undo state with matchId round-trips through persistence")
    void undoStateWithMatchIdRoundTrips() {
        UUID user1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID user2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Like like = Like.create(user1, user2, Like.Direction.SUPER_LIKE);
        String matchId = "00000000-0000-0000-0000-000000000003~00000000-0000-0000-0000-000000000004";
        Undo state = new Undo(user1, like, matchId, Instant.now().plusSeconds(7200));

        matchmakingStorage.undoStorage().save(state);

        Optional<Undo> found = matchmakingStorage.undoStorage().findByUserId(user1);
        assertTrue(found.isPresent());
        assertEquals(matchId, found.get().matchId());
    }
}
