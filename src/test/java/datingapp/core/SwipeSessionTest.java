package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for SwipeSession state machine and computed properties. */
class SwipeSessionTest {

    @Nested
    @DisplayName("Creation tests")
    class CreationTests {

        @Test
        @DisplayName("Factory method creates active session with zero counts")
        void factoryCreatesActiveSession() {
            UUID userId = UUID.randomUUID();
            SwipeSession session = SwipeSession.create(userId);

            assertNotNull(session.getId());
            assertEquals(userId, session.getUserId());
            assertEquals(SwipeSession.State.ACTIVE, session.getState());
            assertTrue(session.isActive());
            assertEquals(0, session.getSwipeCount());
            assertEquals(0, session.getLikeCount());
            assertEquals(0, session.getPassCount());
            assertEquals(0, session.getMatchCount());
            assertNull(session.getEndedAt());
        }
    }

    @Nested
    @DisplayName("Swipe recording tests")
    class SwipeRecordingTests {

        @Test
        @DisplayName("Recording LIKE increments swipe and like counts")
        void recordLikeIncrementsCounts() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            session.recordSwipe(Like.Direction.LIKE, false);

            assertEquals(1, session.getSwipeCount());
            assertEquals(1, session.getLikeCount());
            assertEquals(0, session.getPassCount());
            assertEquals(0, session.getMatchCount());
        }

        @Test
        @DisplayName("Recording PASS increments swipe and pass counts")
        void recordPassIncrementsCounts() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            session.recordSwipe(Like.Direction.PASS, false);

            assertEquals(1, session.getSwipeCount());
            assertEquals(0, session.getLikeCount());
            assertEquals(1, session.getPassCount());
        }

        @Test
        @DisplayName("Recording LIKE with match increments match count")
        void recordLikeWithMatch() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            session.recordSwipe(Like.Direction.LIKE, true);

            assertEquals(1, session.getSwipeCount());
            assertEquals(1, session.getLikeCount());
            assertEquals(1, session.getMatchCount());
        }

        @Test
        @DisplayName("Cannot record swipe on completed session")
        void cannotRecordOnCompleted() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.end();

            var exception =
                    assertThrows(IllegalStateException.class, () -> session.recordSwipe(Like.Direction.LIKE, false));
            assertNotNull(exception);
        }

        @Test
        @DisplayName("incrementMatchCount updates match count")
        void incrementMatchCount() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.recordSwipe(Like.Direction.LIKE, false);

            session.incrementMatchCount();

            assertEquals(1, session.getMatchCount());
        }
    }

    @Nested
    @DisplayName("Session ending tests")
    class EndingTests {

        @Test
        @DisplayName("Ending session changes state to COMPLETED")
        void endChangesState() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            session.end();

            assertEquals(SwipeSession.State.COMPLETED, session.getState());
            assertFalse(session.isActive());
            assertNotNull(session.getEndedAt());
        }

        @Test
        @DisplayName("Ending already-ended session is idempotent")
        void endIsIdempotent() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.end();
            Instant firstEndedAt = session.getEndedAt();

            session.end(); // Second call

            assertEquals(firstEndedAt, session.getEndedAt());
        }
    }

    @Nested
    @DisplayName("Timeout tests")
    class TimeoutTests {

        @Test
        @DisplayName("Session is not timed out when recently active")
        void notTimedOutWhenRecent() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            assertFalse(session.isTimedOut(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("Completed session is never timed out")
        void completedNeverTimesOut() {
            UUID userId = UUID.randomUUID();
            // Create session with old activity time
            SwipeSession session = new SwipeSession(
                    UUID.randomUUID(),
                    userId,
                    Instant.now().minus(Duration.ofHours(1)),
                    Instant.now().minus(Duration.ofHours(1)),
                    Instant.now().minus(Duration.ofMinutes(30)),
                    SwipeSession.State.COMPLETED,
                    5,
                    3,
                    2,
                    1);

            assertFalse(session.isTimedOut(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("Session is timed out when inactive longer than timeout")
        void timedOutWhenInactive() {
            UUID userId = UUID.randomUUID();
            // Create session with old activity time
            SwipeSession session = new SwipeSession(
                    UUID.randomUUID(),
                    userId,
                    Instant.now().minus(Duration.ofMinutes(10)),
                    Instant.now().minus(Duration.ofMinutes(10)), // 10 min ago
                    null,
                    SwipeSession.State.ACTIVE,
                    5,
                    3,
                    2,
                    1);

            assertTrue(session.isTimedOut(Duration.ofMinutes(5)));
        }
    }

    @Nested
    @DisplayName("Computed properties tests")
    class ComputedPropertiesTests {

        @Test
        @DisplayName("Like ratio is correctly calculated")
        void likeRatioCalculation() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            session.recordSwipe(Like.Direction.LIKE, false);
            session.recordSwipe(Like.Direction.LIKE, false);
            session.recordSwipe(Like.Direction.PASS, false);
            session.recordSwipe(Like.Direction.PASS, false);

            assertEquals(0.5, session.getLikeRatio(), 0.01);
        }

        @Test
        @DisplayName("Like ratio is zero when no swipes")
        void likeRatioZeroWhenEmpty() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            assertEquals(0.0, session.getLikeRatio());
        }

        @Test
        @DisplayName("Match rate is correctly calculated")
        void matchRateCalculation() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            session.recordSwipe(Like.Direction.LIKE, true);
            session.recordSwipe(Like.Direction.LIKE, false);

            assertEquals(0.5, session.getMatchRate(), 0.01);
        }

        @Test
        @DisplayName("Match rate is zero when no likes")
        void matchRateZeroWhenNoLikes() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            assertEquals(0.0, session.getMatchRate());
        }

        @Test
        @DisplayName("Formatted duration is correct format")
        void formattedDuration() {
            UUID userId = UUID.randomUUID();
            // Create session that started 5 minutes and 30 seconds ago
            Instant now = Instant.now();
            SwipeSession session = new SwipeSession(
                    UUID.randomUUID(),
                    userId,
                    now.minus(Duration.ofMinutes(5).plusSeconds(30)),
                    now,
                    null,
                    SwipeSession.State.ACTIVE,
                    0,
                    0,
                    0,
                    0);

            String formatted = session.getFormattedDuration();
            // Should be something like "5:30" or "5:31" depending on timing
            assertTrue(formatted.matches("\\d+:\\d{2}"));
        }
    }

    @Nested
    @DisplayName("Equality tests")
    class EqualityTests {

        @Test
        @DisplayName("Sessions with same ID are equal")
        void equalById() {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Instant now = Instant.now();

            SwipeSession session1 = new SwipeSession(id, userId, now, now, null, SwipeSession.State.ACTIVE, 0, 0, 0, 0);
            SwipeSession session2 = new SwipeSession(id, userId, now, now, null, SwipeSession.State.ACTIVE, 5, 3, 2, 1);

            assertEquals(session1, session2);
            assertEquals(session1.hashCode(), session2.hashCode());
        }
    }
}
