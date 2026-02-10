package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.UserInteractions.Like;
import datingapp.core.storage.SwipeSessionStorage;
import datingapp.core.testutil.TestClock;
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

/** Unit tests for SessionService logic. Uses a simple in-memory session storage for testing. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class SessionServiceTest {

    private InMemorySwipeSessionStorage storage;
    private AppConfig config;
    private SessionService service;
    private UUID userId;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        storage = new InMemorySwipeSessionStorage();
        config = AppConfig.builder()
                .sessionTimeoutMinutes(5)
                .maxSwipesPerSession(100)
                .suspiciousSwipeVelocity(30.0)
                .build();
        service = new SessionService(storage, config);
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
            SwipeSession session = service.getOrCreateSession(userId);

            assertNotNull(session);
            assertEquals(userId, session.getUserId());
            assertTrue(session.isActive());
        }

        @Test
        @DisplayName("Returns existing active session")
        void returnsExistingSession() {
            SwipeSession first = service.getOrCreateSession(userId);
            SwipeSession second = service.getOrCreateSession(userId);

            assertEquals(first.getId(), second.getId());
        }

        @Test
        @DisplayName("Creates new session after timeout")
        void createsNewAfterTimeout() {
            // Create initial session
            SwipeSession initial = service.getOrCreateSession(userId);

            // Manually make it appear timed out by creating with old timestamp
            SwipeSession oldSession = new SwipeSession(
                    initial.getId(),
                    userId,
                    AppClock.now().minus(Duration.ofMinutes(10)),
                    AppClock.now().minus(Duration.ofMinutes(10)),
                    null,
                    SwipeSession.State.ACTIVE,
                    5,
                    3,
                    2,
                    1);
            storage.save(oldSession);

            // Now get session again - should create new one
            SwipeSession newSession = service.getOrCreateSession(userId);

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
            SessionService.SwipeResult result = service.recordSwipe(userId, Like.Direction.LIKE, false);

            assertTrue(result.allowed());
            assertNull(result.blockedReason());
            assertEquals(1, result.session().getSwipeCount());
        }

        @Test
        @DisplayName("Blocks swipe when session limit reached")
        void blocksAtLimit() {
            // Create session near limit
            SwipeSession session = service.getOrCreateSession(userId);
            for (int i = 0; i < 100; i++) {
                session.recordSwipe(Like.Direction.LIKE, false);
            }
            storage.save(session);

            // Try one more
            SessionService.SwipeResult result = service.recordSwipe(userId, Like.Direction.LIKE, false);

            assertFalse(result.allowed());
            assertEquals("Session swipe limit reached. Take a break!", result.blockedReason());
        }

        @Test
        @DisplayName("Warns when swipe velocity is suspicious")
        void warnsOnSuspiciousVelocity() {
            SessionService.SwipeResult result = null;
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

            Optional<SwipeSession> active = service.getCurrentSession(userId);
            assertTrue(active.isEmpty());
        }
    }

    @Nested
    @DisplayName("History and aggregates tests")
    class HistoryTests {

        @Test
        @DisplayName("Returns empty list for user with no sessions")
        void emptyHistoryForNew() {
            List<SwipeSession> history = service.getSessionHistory(userId, 10);

            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("Returns aggregates for user")
        void returnsAggregates() {
            // Create a session with some swipes
            SwipeSession session = service.getOrCreateSession(userId);
            session.recordSwipe(Like.Direction.LIKE, true);
            session.recordSwipe(Like.Direction.PASS, false);
            storage.save(session);

            SwipeSessionStorage.SessionAggregates agg = service.getAggregates(userId);

            assertEquals(1, agg.totalSessions());
            assertEquals(2, agg.totalSwipes());
            assertEquals(1, agg.totalLikes());
            assertEquals(1, agg.totalPasses());
        }
    }

    /** Simple in-memory implementation for testing. */
    private static class InMemorySwipeSessionStorage implements SwipeSessionStorage {
        private final java.util.Map<UUID, SwipeSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void save(SwipeSession session) {
            sessions.put(session.getId(), session);
        }

        @Override
        public Optional<SwipeSession> get(UUID sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public Optional<SwipeSession> getActiveSession(UUID userId) {
            return sessions.values().stream()
                    .filter(s -> s.getUserId().equals(userId) && s.isActive())
                    .findFirst();
        }

        @Override
        public List<SwipeSession> getSessionsFor(UUID userId, int limit) {
            return sessions.values().stream()
                    .filter(s -> s.getUserId().equals(userId))
                    .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<SwipeSession> getSessionsInRange(UUID userId, Instant start, Instant end) {
            return sessions.values().stream()
                    .filter(s -> s.getUserId().equals(userId))
                    .filter(s -> !s.getStartedAt().isBefore(start)
                            && !s.getStartedAt().isAfter(end))
                    .toList();
        }

        @Override
        public SessionAggregates getAggregates(UUID userId) {
            List<SwipeSession> userSessions = sessions.values().stream()
                    .filter(s -> s.getUserId().equals(userId))
                    .toList();

            if (userSessions.isEmpty()) {
                return SessionAggregates.empty();
            }

            int totalSwipes =
                    userSessions.stream().mapToInt(SwipeSession::getSwipeCount).sum();
            int totalLikes =
                    userSessions.stream().mapToInt(SwipeSession::getLikeCount).sum();
            int totalPasses =
                    userSessions.stream().mapToInt(SwipeSession::getPassCount).sum();
            int totalMatches =
                    userSessions.stream().mapToInt(SwipeSession::getMatchCount).sum();

            return new SessionAggregates(
                    userSessions.size(), totalSwipes, totalLikes, totalPasses, totalMatches, 0.0, 0.0, 0.0);
        }

        @Override
        public int endStaleSessions(Duration timeout) {
            int count = 0;
            for (SwipeSession session : sessions.values()) {
                if (session.isActive() && session.isTimedOut(timeout)) {
                    session.end();
                    count++;
                }
            }
            return count;
        }

        @Override
        public int deleteExpiredSessions(Instant cutoff) {
            return 0; // Not needed for session tests
        }
    }
}
