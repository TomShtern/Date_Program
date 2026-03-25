package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.matching.Standout;
import datingapp.core.model.User;
import datingapp.storage.DatabaseManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class JdbiMetricsStorageTest {

    private DatabaseManager dbManager;
    private JdbiMetricsStorage storage;
    private User viewer;
    private User viewed;

    @BeforeEach
    void setUp() {
        String dbName = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        dbManager = DatabaseManager.getInstance();

        Jdbi jdbi = Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException ex) {
                        throw new DatabaseManager.StorageException("Failed to get database connection", ex);
                    }
                })
                .installPlugin(new SqlObjectPlugin());

        jdbi.registerArgument(new JdbiTypeCodecs.EnumSetSqlCodec.EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new JdbiTypeCodecs.EnumSetSqlCodec.InterestColumnMapper());
        jdbi.registerColumnMapper(Instant.class, (rs, col, ctx) -> {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toInstant() : null;
        });

        storage = new JdbiMetricsStorage(jdbi);
        JdbiUserStorage userStorage = new JdbiUserStorage(jdbi);

        viewer = new User(UUID.randomUUID(), "Viewer");
        viewed = new User(UUID.randomUUID(), "Viewed");
        userStorage.save(viewer);
        userStorage.save(viewed);

        AppClock.setFixed(Instant.parse("2026-03-22T12:00:00Z"));
    }

    @AfterEach
    void tearDown() {
        AppClock.reset();
        if (dbManager != null) {
            dbManager.shutdown();
            DatabaseManager.resetInstance();
        }
    }

    @Test
    @DisplayName("should tolerate duplicate profile view inserts at the same timestamp")
    void recordProfileViewSameTimestampIsIdempotent() {
        assertDoesNotThrow(() -> storage.recordProfileView(viewer.getId(), viewed.getId()));
        assertDoesNotThrow(() -> storage.recordProfileView(viewer.getId(), viewed.getId()));

        assertEquals(1, storage.getProfileViewCount(viewed.getId()));
        assertEquals(1, storage.getUniqueViewerCount(viewed.getId()));
        assertTrue(storage.hasViewedProfile(viewer.getId(), viewed.getId()));
        assertEquals(List.of(viewer.getId()), storage.getRecentViewers(viewed.getId(), 10));
    }

    @Test
    @DisplayName("markStandoutInteracted should update standout interacted_at when null")
    void markStandoutInteractedSetsTimestamp() {
        Standout standout = Standout.create(viewer.getId(), viewed.getId(), LocalDate.now(), 1, 85, "Great match");
        storage.saveStandouts(viewer.getId(), List.of(standout), LocalDate.now());

        List<Standout> before = storage.getStandouts(viewer.getId(), LocalDate.now());
        assertEquals(1, before.size());
        assertNull(before.get(0).interactedAt());

        Instant interactionTime = AppClock.now().plusSeconds(60);
        boolean updated = storage.markStandoutInteracted(standout.id(), interactionTime);

        assertTrue(updated, "markStandoutInteracted should return true on successful update");
        List<Standout> after = storage.getStandouts(viewer.getId(), LocalDate.now());
        assertEquals(1, after.size());
        assertEquals(interactionTime, after.get(0).interactedAt());
    }

    @Test
    @DisplayName("markStandoutInteracted should be idempotent - second call returns false")
    void markStandoutInteractedIsIdempotent() {
        Standout standout = Standout.create(viewer.getId(), viewed.getId(), LocalDate.now(), 1, 85, "Great match");
        storage.saveStandouts(viewer.getId(), List.of(standout), LocalDate.now());

        Instant firstTime = AppClock.now().plusSeconds(10);
        boolean firstUpdate = storage.markStandoutInteracted(standout.id(), firstTime);
        assertTrue(firstUpdate, "First update should succeed");

        Instant secondTime = AppClock.now().plusSeconds(120);
        boolean secondUpdate = storage.markStandoutInteracted(standout.id(), secondTime);
        assertFalse(secondUpdate, "Second update should fail (idempotent)");

        List<Standout> standouts = storage.getStandouts(viewer.getId(), LocalDate.now());
        assertEquals(1, standouts.size());
        assertEquals(firstTime, standouts.get(0).interactedAt(), "Should preserve first interaction time");
    }

    @Test
    @DisplayName("markStandoutInteracted with non-existent standout returns false")
    void markStandoutInteractedNonExistentReturnsFalse() {
        UUID nonExistentId = UUID.randomUUID();
        boolean result = storage.markStandoutInteracted(nonExistentId, AppClock.now());
        assertFalse(result, "Should return false for non-existent standout");
    }
}
