package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.*;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.*;
import datingapp.core.metrics.*;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.model.*;
import datingapp.core.profile.*;
import datingapp.core.recommendation.*;
import datingapp.core.safety.*;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for ActivityMetricsService session logic. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class SessionServiceTest {

    private AnalyticsStorage analyticsStorage;
    private AppConfig config;
    private ActivityMetricsService service;
    private UUID userId;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        analyticsStorage = new TestStorages.Analytics();
        config = AppConfig.builder()
                .sessionTimeoutMinutes(5)
                .maxSwipesPerSession(100)
                .suspiciousSwipeVelocity(30.0)
                .build();
        service = new ActivityMetricsService(analyticsStorage, config);
        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("Session creation tests")
    class SessionCreationTests {

        @Test
        @DisplayName("Creates new session when none exists")
        void createsNewSession() {
            Session session = service.getOrCreateSession(userId);

            assertNotNull(session);
            assertEquals(userId, session.getUserId());
            assertTrue(session.isActive());
        }

        @Test
        @DisplayName("Returns existing active session")
        void returnsExistingSession() {
            Session first = service.getOrCreateSession(userId);
            Session second = service.getOrCreateSession(userId);

            assertEquals(first.getId(), second.getId());
        }

        @Test
        @DisplayName("Creates new session after timeout")
        void createsNewAfterTimeout() {
            // Create initial session
            Session initial = service.getOrCreateSession(userId);

            // Manually make it appear timed out by creating with old timestamp
            Session oldSession = new Session(
                    initial.getId(),
                    userId,
                    AppClock.now().minus(Duration.ofMinutes(10)),
                    AppClock.now().minus(Duration.ofMinutes(10)),
                    null,
                    Session.State.ACTIVE,
                    5,
                    3,
                    2,
                    1);
            analyticsStorage.saveSession(oldSession);

            // Now get session again - should create new one
            Session newSession = service.getOrCreateSession(userId);

            assertNotEquals(initial.getId(), newSession.getId());
            assertEquals(0, newSession.getSwipeCount());
        }
    }

    @Nested
    @DisplayName("Swipe recording tests")
    class SwipeRecordingTests {

        @Test
        @DisplayName("Recording swipe returns success result")
        void recordSwipeSuccess() {
            ActivityMetricsService.SwipeResult result = service.recordSwipe(userId, Like.Direction.LIKE, false);

            assertTrue(result.allowed());
            assertNull(result.blockedReason());
            assertEquals(1, result.session().getSwipeCount());
        }

        @Test
        @DisplayName("Blocks swipe when session limit reached")
        void blocksAtLimit() {
            // Create session near limit
            Session session = service.getOrCreateSession(userId);
            for (int i = 0; i < 100; i++) {
                session.recordSwipe(Like.Direction.LIKE, false);
            }
            analyticsStorage.saveSession(session);

            // Try one more
            ActivityMetricsService.SwipeResult result = service.recordSwipe(userId, Like.Direction.LIKE, false);

            assertFalse(result.allowed());
            assertEquals("Session swipe limit reached. Take a break!", result.blockedReason());
        }

        @Test
        @DisplayName("Warns when swipe velocity is suspicious")
        void warnsOnSuspiciousVelocity() {
            ActivityMetricsService.SwipeResult result = null;
            for (int i = 0; i < 31; i++) {
                result = service.recordSwipe(userId, Like.Direction.LIKE, false);
            }

            assertNotNull(result);
            assertTrue(result.allowed());
            assertTrue(result.hasWarning());
            assertNotNull(result.warning());
        }
    }

    @Nested
    @DisplayName("Session ending tests")
    class SessionEndingTests {

        @Test
        @DisplayName("Can explicitly end session")
        void endsSession() {
            service.getOrCreateSession(userId);

            service.endSession(userId);

            Optional<Session> active = service.getCurrentSession(userId);
            assertTrue(active.isEmpty());
        }
    }

    @Nested
    @DisplayName("History and aggregates tests")
    class HistoryTests {

        @Test
        @DisplayName("Returns empty list for user with no sessions")
        void emptyHistoryForNew() {
            List<Session> history = service.getSessionHistory(userId, 10);

            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("Returns aggregates for user")
        void returnsAggregates() {
            // Create a session with some swipes
            Session session = service.getOrCreateSession(userId);
            session.recordSwipe(Like.Direction.LIKE, true);
            session.recordSwipe(Like.Direction.PASS, false);
            analyticsStorage.saveSession(session);

            AnalyticsStorage.SessionAggregates agg = service.getAggregates(userId);

            assertEquals(1, agg.totalSessions());
            assertEquals(2, agg.totalSwipes());
            assertEquals(1, agg.totalLikes());
            assertEquals(1, agg.totalPasses());
        }
    }
}
