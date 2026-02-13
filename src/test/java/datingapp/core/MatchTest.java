package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.model.Match;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/** Unit tests for Match domain model. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
@SuppressWarnings("unused")
class MatchTest {

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
            assertEquals(Match.State.GRACEFUL_EXIT, match.getState());
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
            assertEquals(Match.State.ACTIVE, match.getState());
        }
    }
}
