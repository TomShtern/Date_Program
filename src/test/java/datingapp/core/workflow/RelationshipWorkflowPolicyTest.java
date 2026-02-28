package datingapp.core.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchState;
import datingapp.core.testutil.TestUserFactory;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RelationshipWorkflowPolicy")
class RelationshipWorkflowPolicyTest {

    private RelationshipWorkflowPolicy policy;
    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        policy = new RelationshipWorkflowPolicy();
    }

    private Match activeMatch() {
        return Match.create(userA, userB);
    }

    private Match friendsMatch() {
        Match m = activeMatch();
        m.transitionToFriends(userA);
        return m;
    }

    private Match unmatchedMatch() {
        Match m = activeMatch();
        m.unmatch(userA);
        return m;
    }

    private Match gracefulExitMatch() {
        Match m = activeMatch();
        m.gracefulExit(userA);
        return m;
    }

    private Match blockedMatch() {
        Match m = activeMatch();
        m.block(userA);
        return m;
    }

    @Nested
    @DisplayName("canTransition")
    class CanTransition {

        @Test
        @DisplayName("ACTIVE -> FRIENDS is allowed")
        void activeCanTransitionToFriends() {
            assertTrue(policy.canTransition(activeMatch(), MatchState.FRIENDS).isAllowed());
        }

        @Test
        @DisplayName("ACTIVE -> UNMATCHED is allowed")
        void activeCanTransitionToUnmatched() {
            assertTrue(policy.canTransition(activeMatch(), MatchState.UNMATCHED).isAllowed());
        }

        @Test
        @DisplayName("ACTIVE -> GRACEFUL_EXIT is allowed")
        void activeCanTransitionToGracefulExit() {
            assertTrue(policy.canTransition(activeMatch(), MatchState.GRACEFUL_EXIT)
                    .isAllowed());
        }

        @Test
        @DisplayName("ACTIVE -> BLOCKED is allowed")
        void activeCanTransitionToBlocked() {
            assertTrue(policy.canTransition(activeMatch(), MatchState.BLOCKED).isAllowed());
        }

        @Test
        @DisplayName("FRIENDS -> UNMATCHED is allowed")
        void friendsCanTransitionToUnmatched() {
            assertTrue(
                    policy.canTransition(friendsMatch(), MatchState.UNMATCHED).isAllowed());
        }

        @Test
        @DisplayName("FRIENDS -> GRACEFUL_EXIT is allowed")
        void friendsCanTransitionToGracefulExit() {
            assertTrue(policy.canTransition(friendsMatch(), MatchState.GRACEFUL_EXIT)
                    .isAllowed());
        }

        @Test
        @DisplayName("FRIENDS -> BLOCKED is allowed")
        void friendsCanTransitionToBlocked() {
            assertTrue(policy.canTransition(friendsMatch(), MatchState.BLOCKED).isAllowed());
        }

        @Test
        @DisplayName("FRIENDS -> ACTIVE is denied")
        void friendsCannotTransitionToActive() {
            WorkflowDecision d = policy.canTransition(friendsMatch(), MatchState.ACTIVE);
            assertTrue(d.isDenied());
            assertInstanceOf(WorkflowDecision.Denied.class, d);
        }

        @Test
        @DisplayName("UNMATCHED is terminal")
        void unmatchedIsTerminal() {
            Match m = unmatchedMatch();
            for (MatchState target : MatchState.values()) {
                if (target == MatchState.UNMATCHED) continue;
                assertTrue(policy.canTransition(m, target).isDenied());
            }
        }

        @Test
        @DisplayName("GRACEFUL_EXIT is terminal")
        void gracefulExitIsTerminal() {
            Match m = gracefulExitMatch();
            for (MatchState target : MatchState.values()) {
                if (target == MatchState.GRACEFUL_EXIT) continue;
                assertTrue(policy.canTransition(m, target).isDenied());
            }
        }

        @Test
        @DisplayName("BLOCKED is terminal")
        void blockedIsTerminal() {
            Match m = blockedMatch();
            for (MatchState target : MatchState.values()) {
                if (target == MatchState.BLOCKED) continue;
                assertTrue(policy.canTransition(m, target).isDenied());
            }
        }

        @Test
        @DisplayName("same state is denied")
        void sameStateIsDenied() {
            WorkflowDecision d = policy.canTransition(activeMatch(), MatchState.ACTIVE);
            assertTrue(d.isDenied());
            assertEquals("SAME_STATE", ((WorkflowDecision.Denied) d).reasonCode());
        }
    }

    @Nested
    @DisplayName("canRequestFriendZone")
    class CanRequestFriendZone {

        @Test
        @DisplayName("allowed from ACTIVE")
        void canRequestFriendZoneOnlyFromActive() {
            assertTrue(policy.canRequestFriendZone(activeMatch()).isAllowed());
        }

        @Test
        @DisplayName("denied from FRIENDS")
        void cannotRequestFriendZoneFromFriends() {
            assertTrue(policy.canRequestFriendZone(friendsMatch()).isDenied());
        }
    }

    @Nested
    @DisplayName("canBlock")
    class CanBlock {

        @Test
        @DisplayName("allowed from ACTIVE")
        void canBlockFromActive() {
            assertTrue(policy.canBlock(activeMatch()).isAllowed());
        }

        @Test
        @DisplayName("allowed from FRIENDS")
        void canBlockFromFriends() {
            assertTrue(policy.canBlock(friendsMatch()).isAllowed());
        }

        @Test
        @DisplayName("denied when already BLOCKED")
        void cannotBlockAlreadyBlocked() {
            WorkflowDecision d = policy.canBlock(blockedMatch());
            assertTrue(d.isDenied());
            assertEquals("ALREADY_BLOCKED", ((WorkflowDecision.Denied) d).reasonCode());
        }
    }

    @Nested
    @DisplayName("canSendMessage")
    class CanSendMessage {

        @Test
        @DisplayName("requires active sender")
        void canSendMessageRequiresActiveSender() {
            WorkflowDecision d = policy.canSendMessage(activeMatch(), null, TestUserFactory.createActiveUser("Bob"));
            assertTrue(d.isDenied());
            assertEquals("SENDER_NOT_ACTIVE", ((WorkflowDecision.Denied) d).reasonCode());
        }

        @Test
        @DisplayName("requires active recipient")
        void canSendMessageRequiresActiveRecipient() {
            WorkflowDecision d = policy.canSendMessage(activeMatch(), TestUserFactory.createActiveUser("Alice"), null);
            assertTrue(d.isDenied());
            assertEquals("RECIPIENT_NOT_ACTIVE", ((WorkflowDecision.Denied) d).reasonCode());
        }

        @Test
        @DisplayName("requires messageable match")
        void canSendMessageRequiresMessageableMatch() {
            WorkflowDecision d = policy.canSendMessage(
                    blockedMatch(), TestUserFactory.createActiveUser("Alice"), TestUserFactory.createActiveUser("Bob"));
            assertTrue(d.isDenied());
            assertEquals("MATCH_NOT_MESSAGEABLE", ((WorkflowDecision.Denied) d).reasonCode());
        }

        @Test
        @DisplayName("allowed when all conditions met")
        void canSendMessageWhenAllConditionsMet() {
            assertTrue(policy.canSendMessage(
                            activeMatch(),
                            TestUserFactory.createActiveUser("Alice"),
                            TestUserFactory.createActiveUser("Bob"))
                    .isAllowed());
        }
    }

    @Nested
    @DisplayName("query methods")
    class QueryMethods {

        @Test
        @DisplayName("allowedTransitionsFrom ACTIVE returns 4")
        void allowedTransitionsFromActiveReturns4() {
            Set<MatchState> allowed = policy.allowedTransitionsFrom(MatchState.ACTIVE);
            assertEquals(4, allowed.size());
        }

        @Test
        @DisplayName("allowedTransitionsFrom terminal returns empty")
        void allowedTransitionsFromTerminalReturnsEmpty() {
            assertTrue(policy.allowedTransitionsFrom(MatchState.BLOCKED).isEmpty());
            assertTrue(policy.allowedTransitionsFrom(MatchState.UNMATCHED).isEmpty());
            assertTrue(policy.allowedTransitionsFrom(MatchState.GRACEFUL_EXIT).isEmpty());
        }

        @Test
        @DisplayName("isTerminal for BLOCKED, UNMATCHED, GRACEFUL_EXIT")
        void isTerminalForBlockedUnmatchedGracefulExit() {
            assertTrue(policy.isTerminal(MatchState.BLOCKED));
            assertTrue(policy.isTerminal(MatchState.UNMATCHED));
            assertTrue(policy.isTerminal(MatchState.GRACEFUL_EXIT));
            assertFalse(policy.isTerminal(MatchState.ACTIVE));
            assertFalse(policy.isTerminal(MatchState.FRIENDS));
        }
    }
}
