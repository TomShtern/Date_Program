package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.model.User;
import datingapp.storage.DatabaseManager;
import java.sql.Timestamp;
import java.time.Instant;
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
    private JdbiUserStorage userStorage;
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
                    } catch (java.sql.SQLException e) {
                        throw new DatabaseManager.StorageException("Failed to get database connection", e);
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
        userStorage = new JdbiUserStorage(jdbi);

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
}
