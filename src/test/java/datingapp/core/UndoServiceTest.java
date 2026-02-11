package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.model.*;
import datingapp.core.model.UserInteractions.Like;
import datingapp.core.service.*;
import datingapp.core.service.UndoService.UndoResult;
import datingapp.core.testutil.TestStorages;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Tests for UndoService - time-windowed undo functionality for swipe actions.
 */
@SuppressWarnings("unused")
@DisplayName("UndoService")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class UndoServiceTest {

    private TestStorages.Interactions interactionStorage;
    private TestStorages.Undos undoStorage;
    private AppConfig config;
    private UndoService undoService;
    private TestClock clock;

    private UUID userId;
    private UUID targetUserId;

    @BeforeEach
    void setUp() {
        interactionStorage = new TestStorages.Interactions();
        undoStorage = new TestStorages.Undos();
        config = AppConfig.builder().undoWindowSeconds(30).build(); // 30 second undo window
        clock = new TestClock(Instant.parse("2026-01-26T00:00:00Z"));
        undoService = new UndoService(interactionStorage, undoStorage, config, clock);

        userId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("recordSwipe")
    class RecordSwipe {

        @Test
        @DisplayName("Records swipe enabling undo")
        void recordsSwipeEnablesUndo() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);

            undoService.recordSwipe(userId, like, null);

            assertTrue(undoService.canUndo(userId));
        }

        @Test
        @DisplayName("Records swipe with match")
        void recordsSwipeWithMatch() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            Match match = Match.create(userId, targetUserId);

            undoService.recordSwipe(userId, like, match);

            assertTrue(undoService.canUndo(userId));
        }

        @Test
        @DisplayName("Overwrites previous undo state")
        void overwritesPreviousUndoState() {
            Like firstLike = Like.create(userId, targetUserId, Like.Direction.LIKE);
            Like secondLike = Like.create(userId, UUID.randomUUID(), Like.Direction.PASS);
            interactionStorage.save(firstLike);
            interactionStorage.save(secondLike);

            undoService.recordSwipe(userId, firstLike, null);
            undoService.recordSwipe(userId, secondLike, null);

            // Undo should affect the second like, not the first
            UndoResult result = undoService.undo(userId);

            assertTrue(result.success());
            assertEquals(secondLike.id(), result.undoneSwipe().id());
        }
    }

    @Nested
    @DisplayName("canUndo")
    class CanUndo {

        @Test
        @DisplayName("Returns false when no state exists")
        void returnsFalseWhenNoState() {
            assertFalse(undoService.canUndo(userId));
        }

        @Test
        @DisplayName("Returns true within window")
        void returnsTrueWithinWindow() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            undoService.recordSwipe(userId, like, null);

            assertTrue(undoService.canUndo(userId));
        }

        @Test
        @DisplayName("Returns true at exact expiry boundary")
        void returnsTrueAtExpiryBoundary() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            undoService.recordSwipe(userId, like, null);

            clock.advanceSeconds(config.undoWindowSeconds());

            assertTrue(undoService.canUndo(userId));
            assertEquals(0, undoService.getSecondsRemaining(userId));
        }

        @Test
        @DisplayName("Returns false for different user")
        void returnsFalseForDifferentUser() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            undoService.recordSwipe(userId, like, null);

            assertFalse(undoService.canUndo(UUID.randomUUID()));
        }

        @Test
        @DisplayName("Returns false after expiry (with short window)")
        void returnsFalseAfterExpiry() {
            // Use 1 second undo window for fast test
            AppConfig shortConfig = AppConfig.builder().undoWindowSeconds(1).build();
            TestClock shortClock = new TestClock(clock.instant());
            TestStorages.Undos shortUndoStorage = new TestStorages.Undos();
            UndoService shortUndoService =
                    new UndoService(interactionStorage, shortUndoStorage, shortConfig, shortClock);

            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            shortUndoService.recordSwipe(userId, like, null);

            // Advance time beyond expiry
            shortClock.advanceSeconds(2);

            assertFalse(shortUndoService.canUndo(userId));
        }
    }

    @Nested
    @DisplayName("getSecondsRemaining")
    class GetSecondsRemaining {

        @Test
        @DisplayName("Returns 0 when no state exists")
        void returnsZeroWhenNoState() {
            assertEquals(0, undoService.getSecondsRemaining(userId));
        }

        @Test
        @DisplayName("Returns positive value within window")
        void returnsPositiveWithinWindow() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            undoService.recordSwipe(userId, like, null);

            int remaining = undoService.getSecondsRemaining(userId);

            assertTrue(remaining > 0);
            assertTrue(remaining <= 30);
        }

        @Test
        @DisplayName("Returns approximate configured window")
        void returnsApproximateWindow() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            undoService.recordSwipe(userId, like, null);

            int remaining = undoService.getSecondsRemaining(userId);

            // Should be close to 30 seconds (allow 1 second margin)
            assertTrue(remaining >= 29 && remaining <= 30, "Expected ~30s, got " + remaining);
        }
    }

    @Nested
    @DisplayName("undo")
    class Undo {

        @Test
        @DisplayName("Returns failure when no state exists")
        void failsWhenNoState() {
            UndoResult result = undoService.undo(userId);

            assertFalse(result.success());
            assertEquals("No swipe to undo", result.message());
            assertNull(result.undoneSwipe());
        }

        @Test
        @DisplayName("Succeeds within window")
        void succeedsWithinWindow() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            interactionStorage.save(like);
            undoService.recordSwipe(userId, like, null);

            UndoResult result = undoService.undo(userId);

            assertTrue(result.success());
            assertNotNull(result.undoneSwipe());
            assertEquals(like.id(), result.undoneSwipe().id());
        }

        @Test
        @DisplayName("Deletes like from storage")
        void deletesLikeFromStorage() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            interactionStorage.save(like);
            undoService.recordSwipe(userId, like, null);

            undoService.undo(userId);

            assertFalse(interactionStorage.exists(userId, targetUserId));
        }

        @Test
        @DisplayName("Deletes match when present")
        void deletesMatchWhenPresent() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            Match match = Match.create(userId, targetUserId);
            interactionStorage.save(like);
            interactionStorage.save(match);
            undoService.recordSwipe(userId, like, match);

            UndoResult result = undoService.undo(userId);

            assertTrue(result.success());
            assertTrue(result.matchDeleted());
            assertFalse(interactionStorage.exists(match.getId()));
        }

        @Test
        @DisplayName("Reports match not deleted when no match")
        void reportsNoMatchDeleted() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            interactionStorage.save(like);
            undoService.recordSwipe(userId, like, null);

            UndoResult result = undoService.undo(userId);

            assertTrue(result.success());
            assertFalse(result.matchDeleted());
        }

        @Test
        @DisplayName("Clears undo state after successful undo")
        void clearsStateAfterSuccess() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            interactionStorage.save(like);
            undoService.recordSwipe(userId, like, null);

            undoService.undo(userId);

            assertFalse(undoService.canUndo(userId));
        }

        @Test
        @DisplayName("Cannot undo twice")
        void cannotUndoTwice() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            interactionStorage.save(like);
            undoService.recordSwipe(userId, like, null);

            UndoResult first = undoService.undo(userId);
            UndoResult second = undoService.undo(userId);

            assertTrue(first.success());
            assertFalse(second.success());
        }

        @Test
        @DisplayName("Fails after expiry (with short window)")
        void failsAfterExpiry() {
            // Use 1 second undo window for fast test
            AppConfig shortConfig = AppConfig.builder().undoWindowSeconds(1).build();
            TestClock shortClock = new TestClock(clock.instant());
            TestStorages.Undos shortUndoStorage = new TestStorages.Undos();
            UndoService shortUndoService =
                    new UndoService(interactionStorage, shortUndoStorage, shortConfig, shortClock);

            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            interactionStorage.save(like);
            shortUndoService.recordSwipe(userId, like, null);

            // Advance time beyond expiry
            shortClock.advanceSeconds(2);

            UndoResult result = shortUndoService.undo(userId);

            assertFalse(result.success());
            assertEquals("Undo window expired", result.message());
        }
    }

    @Nested
    @DisplayName("clearUndo")
    class ClearUndo {

        @Test
        @DisplayName("Clears existing undo state")
        void clearsExistingState() {
            Like like = Like.create(userId, targetUserId, Like.Direction.LIKE);
            undoService.recordSwipe(userId, like, null);

            undoService.clearUndo(userId);

            assertFalse(undoService.canUndo(userId));
        }

        @Test
        @DisplayName("No-op when no state exists")
        void noOpWhenNoState() {
            // Should not throw
            undoService.clearUndo(userId);

            assertFalse(undoService.canUndo(userId));
        }
    }

    /**
     * Mutable clock for deterministic time control in tests.
     */
    private static final class TestClock extends Clock {
        private Instant current;

        private TestClock(Instant start) {
            this.current = start;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        public void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }
    }
}
