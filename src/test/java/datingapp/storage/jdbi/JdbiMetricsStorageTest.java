package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.matching.Standout;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.model.User;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.storage.DatabaseManager;
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
    private JdbiUserStorage userStorage;
    private User viewer;
    private User viewed;
    private static final String DB_PROFILE_PROPERTY = "datingapp.db.profile";

    @BeforeEach
    void setUp() {
        System.setProperty(DB_PROFILE_PROPERTY, "test");
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
        JdbiTypeCodecs.registerInstantCodec(jdbi);

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
        System.clearProperty(DB_PROFILE_PROPERTY);
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

    @Test
    @DisplayName("deleteExpiredSessions preserves active sessions even when they are older than the cutoff")
    void deleteExpiredSessionsPreservesActiveSessions() {
        AppClock.setFixed(Instant.parse("2026-03-25T12:00:00Z"));

        UUID activeUserId = UUID.randomUUID();
        UUID completedUserId = UUID.randomUUID();

        userStorage.save(new User(activeUserId, "Active Session User"));
        userStorage.save(new User(completedUserId, "Completed Session User"));

        Session activeSession = new Session(
                UUID.randomUUID(),
                activeUserId,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                Session.MatchState.ACTIVE,
                0,
                0,
                0,
                0);
        Session completedSession = new Session(
                UUID.randomUUID(),
                completedUserId,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T01:00:00Z"),
                Session.MatchState.COMPLETED,
                0,
                0,
                0,
                0);

        storage.saveSession(activeSession);
        storage.saveSession(completedSession);

        int deleted = storage.deleteExpiredSessions(Instant.parse("2026-03-01T00:00:00Z"));

        assertEquals(1, deleted);
        assertTrue(storage.getActiveSession(activeUserId).isPresent());
        assertTrue(storage.getSession(completedSession.getId()).isEmpty());
    }

    @Test
    @DisplayName("getAllLatestUserStats returns a single latest row per user when timestamps tie")
    void getAllLatestUserStatsReturnsOneLatestRowPerUserOnTimestampTie() {
        AppClock.setFixed(Instant.parse("2026-03-25T12:00:00Z"));

        UUID userId = UUID.randomUUID();
        userStorage.save(new User(userId, "Stats User"));
        UserStats first = new UserStats(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                userId,
                Instant.parse("2026-03-25T12:00:00Z"),
                0,
                0,
                0,
                0.0,
                0,
                0,
                0,
                0.0,
                0,
                0,
                0.0,
                0,
                0,
                0,
                0,
                0.5,
                0.5,
                0.5);
        UserStats second = new UserStats(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                userId,
                Instant.parse("2026-03-25T12:00:00Z"),
                0,
                0,
                0,
                0.0,
                0,
                0,
                0,
                0.0,
                0,
                0,
                0.0,
                0,
                0,
                0,
                0,
                0.5,
                0.5,
                0.5);

        storage.saveUserStats(first);
        storage.saveUserStats(second);

        List<UserStats> latest = storage.getAllLatestUserStats();

        assertEquals(1, latest.size());
        assertEquals(userId, latest.get(0).userId());
        assertEquals(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                latest.get(0).id());
    }

    @Test
    @DisplayName("getSessionAggregates preserves completed-session duration semantics")
    void getSessionAggregatesPreservesCompletedSessionDurationSemantics() {
        Session first = new Session(
                UUID.randomUUID(),
                viewer.getId(),
                Instant.parse("2026-03-25T12:00:00Z"),
                Instant.parse("2026-03-25T12:00:10Z"),
                Instant.parse("2026-03-25T12:00:10Z"),
                Session.MatchState.COMPLETED,
                4,
                3,
                1,
                1);
        Session second = new Session(
                UUID.randomUUID(),
                viewer.getId(),
                Instant.parse("2026-03-25T12:01:00Z"),
                Instant.parse("2026-03-25T12:01:20Z"),
                Instant.parse("2026-03-25T12:01:20Z"),
                Session.MatchState.COMPLETED,
                6,
                2,
                4,
                0);

        storage.saveSession(first);
        storage.saveSession(second);

        AnalyticsStorage.SessionAggregates aggregates = storage.getSessionAggregates(viewer.getId());

        assertEquals(2, aggregates.totalSessions());
        assertEquals(10, aggregates.totalSwipes());
        assertEquals(5, aggregates.totalLikes());
        assertEquals(5, aggregates.totalPasses());
        assertEquals(1, aggregates.totalMatches());
        assertEquals(15.0, aggregates.avgSessionDurationSeconds(), 0.0001);
        assertEquals(5.0, aggregates.avgSwipesPerSession(), 0.0001);
        assertEquals(10.0 / 30.0, aggregates.avgSwipeVelocity(), 0.0001);
    }
}
