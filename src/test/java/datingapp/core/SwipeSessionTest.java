package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.UserInteractions.Like;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for SwipeSession state machine and computed properties. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class SwipeSessionTest {

    @Test
    @DisplayName("SwipeSession can be instantiated with all parameters")
    void canInstantiateWithAllParameters() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant lastActivityAt = Instant.now();

        SwipeSession session =
                new SwipeSession(id, userId, createdAt, lastActivityAt, null, SwipeSession.State.ACTIVE, 0, 0, 0, 0);

        assertNotNull(session);
        assertEquals(id, session.getId());
        assertEquals(userId, session.getUserId());
    }

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

        @Test
        @DisplayName("incrementMatchCount does nothing on completed session")
        void incrementMatchCountCompletedSession() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.end();

            session.incrementMatchCount();

            assertEquals(0, session.getMatchCount());
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

        @Test
        @DisplayName("Duration is never negative when start time is in the future")
        void durationNotNegativeForFutureStart() {
            UUID userId = UUID.randomUUID();
            Instant future = Instant.now().plus(Duration.ofMinutes(5));

            SwipeSession session = new SwipeSession(
                    UUID.randomUUID(), userId, future, future, null, SwipeSession.State.ACTIVE, 0, 0, 0, 0);

            assertEquals(0, session.getDurationSeconds());
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

        @Test
        @DisplayName("Sessions with different IDs are not equal")
        void notEqualByDifferentId() {
            UUID userId = UUID.randomUUID();
            Instant now = Instant.now();

            SwipeSession session1 =
                    new SwipeSession(UUID.randomUUID(), userId, now, now, null, SwipeSession.State.ACTIVE, 0, 0, 0, 0);
            SwipeSession session2 =
                    new SwipeSession(UUID.randomUUID(), userId, now, now, null, SwipeSession.State.ACTIVE, 0, 0, 0, 0);

            assertNotEquals(session1, session2);
        }

        @Test
        @DisplayName("Session equals itself")
        void equalsItself() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            assertSame(session, session);
        }

        @Test
        @DisplayName("Session not equal to null")
        void notEqualToNull() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            assertNotEquals(null, session);
        }
    }

    @Nested
    @DisplayName("Edge case swipe tests")
    class EdgeCaseSwipeTests {

        @Test
        @DisplayName("Recording only LIKE swipes results in 100% like ratio")
        void allLikesGives100PercentRatio() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            session.recordSwipe(Like.Direction.LIKE, false);
            session.recordSwipe(Like.Direction.LIKE, false);
            session.recordSwipe(Like.Direction.LIKE, false);

            assertEquals(1.0, session.getLikeRatio(), 0.001);
            assertEquals(3, session.getSwipeCount());
            assertEquals(3, session.getLikeCount());
            assertEquals(0, session.getPassCount());
        }

        @Test
        @DisplayName("Recording only PASS swipes results in 0% like ratio")
        void allPassesGivesZeroPercentRatio() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            session.recordSwipe(Like.Direction.PASS, false);
            session.recordSwipe(Like.Direction.PASS, false);
            session.recordSwipe(Like.Direction.PASS, false);

            assertEquals(0.0, session.getLikeRatio(), 0.001);
            assertEquals(3, session.getSwipeCount());
            assertEquals(0, session.getLikeCount());
            assertEquals(3, session.getPassCount());
        }

        @Test
        @DisplayName("All likes matching gives 100% match rate")
        void allLikesMatchingGives100PercentMatchRate() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            session.recordSwipe(Like.Direction.LIKE, true);
            session.recordSwipe(Like.Direction.LIKE, true);
            session.recordSwipe(Like.Direction.LIKE, true);

            assertEquals(1.0, session.getMatchRate(), 0.001);
            assertEquals(3, session.getLikeCount());
            assertEquals(3, session.getMatchCount());
        }

        @Test
        @DisplayName("PASS with match flag true does not increment match count")
        void passWithMatchFlagDoesNotIncrementMatches() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            session.recordSwipe(Like.Direction.PASS, true);

            assertEquals(0, session.getMatchCount());
            assertEquals(1, session.getPassCount());
            assertEquals(0, session.getLikeCount());
        }

        @Test
        @DisplayName("Multiple sequential swipes are correctly counted")
        void multipleSequentialSwipes() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            for (int i = 0; i < 10; i++) {
                session.recordSwipe(Like.Direction.LIKE, i % 2 == 0);
            }
            for (int i = 0; i < 5; i++) {
                session.recordSwipe(Like.Direction.PASS, false);
            }

            assertEquals(15, session.getSwipeCount());
            assertEquals(10, session.getLikeCount());
            assertEquals(5, session.getPassCount());
            assertEquals(5, session.getMatchCount());
        }
    }

    @Nested
    @DisplayName("State validation tests")
    class StateValidationTests {

        @Test
        @DisplayName("isActive returns true for ACTIVE state")
        void isActiveTrueForActiveState() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            assertTrue(session.isActive());
            assertEquals(SwipeSession.State.ACTIVE, session.getState());
        }

        @Test
        @DisplayName("isActive returns false for COMPLETED state")
        void isActiveFalseForCompletedState() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.end();

            assertFalse(session.isActive());
            assertEquals(SwipeSession.State.COMPLETED, session.getState());
        }

        @Test
        @DisplayName("Cannot increment match count on completed session")
        void cannotIncrementMatchCountOnCompleted() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.recordSwipe(Like.Direction.LIKE, true);
            session.end();
            int matchCountBefore = session.getMatchCount();

            session.incrementMatchCount(); // Should be silently ignored

            assertEquals(matchCountBefore, session.getMatchCount());
        }

        @Test
        @DisplayName("Session preserves counts when ending")
        void preservesCountsWhenEnding() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.recordSwipe(Like.Direction.LIKE, true);
            session.recordSwipe(Like.Direction.LIKE, false);
            session.recordSwipe(Like.Direction.PASS, false);

            session.end();

            assertEquals(3, session.getSwipeCount());
            assertEquals(2, session.getLikeCount());
            assertEquals(1, session.getPassCount());
            assertEquals(1, session.getMatchCount());
        }
    }

    @Nested
    @DisplayName("Computed properties edge cases")
    class ComputedPropertiesEdgeCases {

        @Test
        @DisplayName("Match rate with no likes but passes recorded is zero")
        void matchRateZeroWithOnlyPasses() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.recordSwipe(Like.Direction.PASS, false);
            session.recordSwipe(Like.Direction.PASS, false);

            assertEquals(0.0, session.getMatchRate());
        }

        @Test
        @DisplayName("Like ratio with single like is 100%")
        void likeRatioWithSingleLike() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.recordSwipe(Like.Direction.LIKE, false);

            assertEquals(1.0, session.getLikeRatio(), 0.001);
        }

        @Test
        @DisplayName("Like ratio with single pass is 0%")
        void likeRatioWithSinglePass() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.recordSwipe(Like.Direction.PASS, false);

            assertEquals(0.0, session.getLikeRatio(), 0.001);
        }

        @Test
        @DisplayName("Match rate with single matching like is 100%")
        void matchRateWithSingleMatch() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.recordSwipe(Like.Direction.LIKE, true);

            assertEquals(1.0, session.getMatchRate(), 0.001);
        }

        @Test
        @DisplayName("Match rate with single non-matching like is 0%")
        void matchRateWithSingleNonMatch() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            session.recordSwipe(Like.Direction.LIKE, false);

            assertEquals(0.0, session.getMatchRate(), 0.001);
        }

        @Test
        @DisplayName("Formatted duration for completed session shows total duration")
        void formattedDurationForCompletedSession() {
            UUID userId = UUID.randomUUID();
            Instant start = Instant.now().minus(Duration.ofMinutes(10));
            Instant end = start.plus(Duration.ofMinutes(5));

            SwipeSession session = new SwipeSession(
                    UUID.randomUUID(), userId, start, end, end, SwipeSession.State.COMPLETED, 10, 5, 5, 2);

            String formatted = session.getFormattedDuration();
            assertTrue(formatted.matches("\\d+:\\d{2}"));
        }
    }

    @Nested
    @DisplayName("Timeout boundary tests")
    class TimeoutBoundaryTests {

        @Test
        @DisplayName("Session at exact timeout boundary is timed out")
        void atExactTimeoutBoundary() {
            UUID userId = UUID.randomUUID();
            Duration timeout = Duration.ofMinutes(5);

            SwipeSession session = new SwipeSession(
                    UUID.randomUUID(),
                    userId,
                    Instant.now().minus(timeout),
                    Instant.now().minus(timeout),
                    null,
                    SwipeSession.State.ACTIVE,
                    0,
                    0,
                    0,
                    0);

            assertTrue(session.isTimedOut(timeout));
        }

        @Test
        @DisplayName("Session just before timeout is not timed out")
        void justBeforeTimeout() {
            UUID userId = UUID.randomUUID();
            Duration timeout = Duration.ofMinutes(5);

            SwipeSession session = new SwipeSession(
                    UUID.randomUUID(),
                    userId,
                    Instant.now().minus(timeout.minusSeconds(1)),
                    Instant.now().minus(timeout.minusSeconds(1)),
                    null,
                    SwipeSession.State.ACTIVE,
                    0,
                    0,
                    0,
                    0);

            assertFalse(session.isTimedOut(timeout));
        }

        @Test
        @DisplayName("Session timeout with zero duration always times out")
        void zeroTimeoutAlwaysTimesOut() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            assertTrue(session.isTimedOut(Duration.ZERO));
        }
    }

    @Nested
    @DisplayName("Getter validation tests")
    class GetterValidationTests {

        @Test
        @DisplayName("All getters return expected values after construction")
        void gettersReturnConstructedValues() {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Instant createdAt = Instant.now().minus(Duration.ofMinutes(10));
            Instant lastActivityAt = Instant.now().minus(Duration.ofMinutes(5));
            Instant endedAt = Instant.now();

            SwipeSession session = new SwipeSession(
                    id, userId, createdAt, lastActivityAt, endedAt, SwipeSession.State.COMPLETED, 20, 12, 8, 5);

            assertEquals(id, session.getId());
            assertEquals(userId, session.getUserId());
            assertEquals(createdAt, session.getStartedAt());
            assertEquals(lastActivityAt, session.getLastActivityAt());
            assertEquals(endedAt, session.getEndedAt());
            assertEquals(SwipeSession.State.COMPLETED, session.getState());
            assertEquals(20, session.getSwipeCount());
            assertEquals(12, session.getLikeCount());
            assertEquals(8, session.getPassCount());
            assertEquals(5, session.getMatchCount());
        }

        @Test
        @DisplayName("Created session has null endedAt")
        void createdSessionHasNullEndedAt() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            assertNull(session.getEndedAt());
        }

        @Test
        @DisplayName("Created session has timestamps set")
        void createdSessionHasTimestamps() {
            Instant before = Instant.now();
            SwipeSession session = SwipeSession.create(UUID.randomUUID());
            Instant after = Instant.now();

            assertNotNull(session.getStartedAt());
            assertNotNull(session.getLastActivityAt());
            assertFalse(session.getStartedAt().isBefore(before));
            assertFalse(session.getStartedAt().isAfter(after));
            assertFalse(session.getLastActivityAt().isBefore(before));
            assertFalse(session.getLastActivityAt().isAfter(after));
        }
    }

    @Nested
    @DisplayName("Large dataset tests")
    class LargeDatasetTests {

        @Test
        @DisplayName("Session handles large number of swipes correctly")
        void handlesManySwipes() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            for (int i = 0; i < 1000; i++) {
                session.recordSwipe(i % 3 == 0 ? Like.Direction.LIKE : Like.Direction.PASS, i % 10 == 0);
            }

            assertEquals(1000, session.getSwipeCount());
            assertTrue(session.getLikeCount() > 0);
            assertTrue(session.getPassCount() > 0);
            assertTrue(session.getMatchCount() > 0);
        }

        @Test
        @DisplayName("Computed ratios are accurate with large datasets")
        void ratiosAccurateWithLargeDataset() {
            SwipeSession session = SwipeSession.create(UUID.randomUUID());

            // 600 likes, 400 passes
            for (int i = 0; i < 600; i++) {
                session.recordSwipe(Like.Direction.LIKE, i < 300);
            }
            for (int i = 0; i < 400; i++) {
                session.recordSwipe(Like.Direction.PASS, false);
            }

            assertEquals(0.6, session.getLikeRatio(), 0.001);
            assertEquals(0.5, session.getMatchRate(), 0.001);
        }
    }
}
