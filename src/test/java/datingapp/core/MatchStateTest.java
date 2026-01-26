package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Extended unit tests for Match domain model, focusing on state transitions.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class MatchStateTest {

    @Nested
    @DisplayName("Match State Transitions")
    class StateTransitions {

        @Test
        @DisplayName("New match starts in ACTIVE state")
        void newMatchStartsActive() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();

            Match match = Match.create(a, b);

            assertEquals(Match.State.ACTIVE, match.getState(), "New match should be ACTIVE");
            assertTrue(match.isActive(), "isActive should return true for new match");
            assertNull(match.getEndedAt(), "endedAt should be null for active match");
            assertNull(match.getEndedBy(), "endedBy should be null for active match");
        }

        @Test
        @DisplayName("Active match can be unmatched")
        void activeMatchCanBeUnmatched() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);

            match.unmatch(a);

            assertEquals(Match.State.UNMATCHED, match.getState(), "State should be UNMATCHED");
            assertFalse(match.isActive(), "isActive should return false after unmatch");
            assertNotNull(match.getEndedAt(), "endedAt should be set");
            assertEquals(a, match.getEndedBy(), "endedBy should be the user who unmatched");
        }

        @Test
        @DisplayName("Active match can be blocked")
        void activeMatchCanBeBlocked() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);

            match.block(b);

            assertEquals(Match.State.BLOCKED, match.getState(), "State should be BLOCKED");
            assertFalse(match.isActive(), "isActive should return false after block");
            assertNotNull(match.getEndedAt(), "endedAt should be set");
            assertEquals(b, match.getEndedBy(), "endedBy should be the user who blocked");
            assertEquals(Match.ArchiveReason.BLOCK, match.getEndReason(), "endReason should be BLOCK");
        }

        @Test
        @DisplayName("Active match can transition to FRIENDS")
        void canTransitionToFriends() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);

            match.transitionToFriends(a);

            assertEquals(Match.State.FRIENDS, match.getState());
            assertTrue(match.canMessage());
            assertNull(match.getEndedAt());
        }

        @Test
        @DisplayName("Friends match can gracefully exit")
        void friendsCanGracefulExit() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);
            match.transitionToFriends(a);

            match.gracefulExit(b);

            assertEquals(Match.State.GRACEFUL_EXIT, match.getState());
            assertFalse(match.canMessage());
            assertEquals(Match.ArchiveReason.GRACEFUL_EXIT, match.getEndReason());
            assertEquals(b, match.getEndedBy());
        }

        @Test
        @DisplayName("Friends match can be unmatched")
        void friendsCanBeUnmatched() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);
            match.transitionToFriends(a);

            match.unmatch(a);

            assertEquals(Match.State.UNMATCHED, match.getState());
            assertEquals(Match.ArchiveReason.UNMATCH, match.getEndReason());
        }

        @Test
        @DisplayName("Friends match can be blocked")
        void friendsCanBeBlocked() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);
            match.transitionToFriends(a);

            match.block(b);

            assertEquals(Match.State.BLOCKED, match.getState());
            assertEquals(Match.ArchiveReason.BLOCK, match.getEndReason());
        }

        @Test
        @DisplayName("Cannot transition from UNMATCHED to FRIENDS")
        void cannotTransitionFromUnmatchedToFriends() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);
            match.unmatch(a);

            assertThrows(IllegalStateException.class, () -> match.transitionToFriends(b));
        }

        @Nested
        @DisplayName("Match.generateId")
        class GenerateIdTests {

            @Test
            @DisplayName("Throws when UUIDs are the same")
            void throwsOnSameUuid() {
                UUID id = UUID.randomUUID();
                assertThrows(IllegalArgumentException.class, () -> Match.generateId(id, id));
            }
        }

        @Test
        @DisplayName("Cannot unmatch an already unmatched match")
        void cannotUnmatchTwice() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);
            match.unmatch(a);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> match.unmatch(b),
                    "Should throw when trying to unmatch an inactive match");
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Cannot unmatch a blocked match")
        void cannotUnmatchBlockedMatch() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);
            match.block(a);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> match.unmatch(b),
                    "Should throw when trying to unmatch a blocked match");
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Only involved users can unmatch")
        void onlyInvolvedUsersCanUnmatch() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID outsider = UUID.randomUUID();
            Match match = Match.create(a, b);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> match.unmatch(outsider),
                    "Should throw when outsider tries to unmatch");
            assertNotNull(ex);
        }

        @Test
        @DisplayName("Only involved users can block")
        void onlyInvolvedUsersCanBlock() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID outsider = UUID.randomUUID();
            Match match = Match.create(a, b);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> match.block(outsider),
                    "Should throw when outsider tries to block");
            assertNotNull(ex);
        }
    }

    @Nested
    @DisplayName("Match Reconstruction")
    class Reconstruction {

        @Test
        @DisplayName("Match can be reconstructed from storage with state")
        void matchCanBeReconstructed() {
            UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
            String id = a + "_" + b;
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant endedAt = Instant.now();

            Match match =
                    new Match(id, a, b, createdAt, Match.State.UNMATCHED, endedAt, a, Match.ArchiveReason.UNMATCH);

            assertEquals(id, match.getId());
            assertEquals(a, match.getUserA());
            assertEquals(b, match.getUserB());
            assertEquals(createdAt, match.getCreatedAt());
            assertEquals(Match.State.UNMATCHED, match.getState());
            assertEquals(endedAt, match.getEndedAt());
            assertEquals(a, match.getEndedBy());
        }

        @Test
        @DisplayName("Reconstruction validates UUID ordering")
        void reconstructionValidatesOrdering() {
            UUID a = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
            UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
            String id = b + "_" + a; // Wrong order (a > b lexicographically)

            // a is larger, so passing (a, b) as (userA, userB) should fail
            Instant now = Instant.now();
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Match(id, a, b, now, Match.State.ACTIVE, null, null, null),
                    "Should throw when userA is not lexicographically smaller");
            assertNotNull(ex);
        }
    }

    @Nested
    @DisplayName("Match Utility Methods")
    class UtilityMethods {

        @Test
        @DisplayName("involves returns true for both users")
        void involvesReturnsTrueForBothUsers() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);

            assertTrue(match.involves(a), "Should involve user A");
            assertTrue(match.involves(b), "Should involve user B");
        }

        @Test
        @DisplayName("involves returns false for outsider")
        void involvesReturnsFalseForOutsider() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID outsider = UUID.randomUUID();
            Match match = Match.create(a, b);

            assertFalse(match.involves(outsider), "Should not involve outsider");
        }

        @Test
        @DisplayName("getOtherUser throws for non-participant")
        void getOtherUserThrowsForNonParticipant() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID outsider = UUID.randomUUID();
            Match match = Match.create(a, b);

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> match.getOtherUser(outsider));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("generateId is consistent with Match ID")
        void generateIdIsConsistent() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();

            Match match = Match.create(a, b);
            String generatedId = Match.generateId(a, b);
            String reversedId = Match.generateId(b, a);

            assertEquals(match.getId(), generatedId, "Generated ID should match Match ID");
            assertEquals(generatedId, reversedId, "Order should not matter for generateId");
        }
    }
}
