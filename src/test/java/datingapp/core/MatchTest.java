package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchState;
import datingapp.core.testutil.TestClock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;

/** Unit tests for Match domain model. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
@SuppressWarnings("unused")
class MatchTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUpClock() {
        TestClock.setFixed(FIXED_INSTANT);
    }

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("Graceful Exit")
    class GracefulExitTests {

        @Test
        @DisplayName("should throw when user is not a participant")
        void gracefulExitRejectsNonParticipant() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            UUID outsider = UUID.randomUUID();

            Match match = Match.create(userA, userB);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> match.gracefulExit(outsider),
                    "gracefulExit should reject non-participant");
        }

        @Test
        @DisplayName("should succeed for actual participant")
        void gracefulExitAcceptsParticipant() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();

            Match match = Match.create(userA, userB);
            assertDoesNotThrow(() -> match.gracefulExit(userA));
            assertEquals(MatchState.GRACEFUL_EXIT, match.getState());
        }
    }

    @Nested
    @DisplayName("Match Creation")
    class CreationTests {

        @Test
        @DisplayName("should generate deterministic ID")
        void deterministicId() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();

            Match m1 = Match.create(a, b);
            Match m2 = Match.create(b, a);

            assertEquals(m1.getId(), m2.getId(), "Match ID should be deterministic regardless of order");
        }

        @Test
        @DisplayName("should start in ACTIVE state")
        void startsActive() {
            Match match = Match.create(UUID.randomUUID(), UUID.randomUUID());
            assertEquals(MatchState.ACTIVE, match.getState());
        }
    }

    @Nested
    @DisplayName("Match timestamps")
    class TimestampTests {

        @Test
        @DisplayName("created match starts with matching updatedAt")
        void createInitializesUpdatedAt() {
            Match match = Match.create(UUID.randomUUID(), UUID.randomUUID());

            assertEquals(FIXED_INSTANT, match.getCreatedAt());
            assertEquals(FIXED_INSTANT, match.getUpdatedAt());
        }

        @Test
        @DisplayName("mutating operations refresh updatedAt")
        void mutationsRefreshUpdatedAt() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();

            assertMutationRefreshesUpdatedAt(match -> match.unmatch(userA), MatchState.UNMATCHED, userA, userB);
            assertMutationRefreshesUpdatedAt(match -> match.block(userA), MatchState.BLOCKED, userA, userB);
            assertMutationRefreshesUpdatedAt(
                    match -> match.transitionToFriends(userA), MatchState.FRIENDS, userA, userB);
            assertMutationRefreshesUpdatedAt(
                    match -> {
                        match.transitionToFriends(userA);
                        match.revertToActive();
                    },
                    MatchState.ACTIVE,
                    userA,
                    userB);
            assertMutationRefreshesUpdatedAt(
                    match -> match.gracefulExit(userA), MatchState.GRACEFUL_EXIT, userA, userB);
        }

        @Test
        @DisplayName("markDeleted refreshes updatedAt")
        void markDeletedRefreshesUpdatedAt() {
            Match match = Match.create(UUID.randomUUID(), UUID.randomUUID());
            Instant deletedAt = FIXED_INSTANT.plusSeconds(45);
            TestClock.setFixed(deletedAt);

            match.markDeleted(deletedAt);

            assertEquals(deletedAt, match.getDeletedAt());
            assertEquals(deletedAt, match.getUpdatedAt());
        }

        private void assertMutationRefreshesUpdatedAt(
                Consumer<Match> mutation, MatchState expectedState, UUID userA, UUID userB) {
            Match match = Match.create(userA, userB);
            Instant updatedAt = FIXED_INSTANT.plusSeconds(30);
            TestClock.setFixed(updatedAt);

            mutation.accept(match);

            assertEquals(expectedState, match.getState());
            assertEquals(updatedAt, match.getUpdatedAt());
        }
    }
}
