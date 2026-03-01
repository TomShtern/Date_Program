package datingapp.core.workflow;

import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchState;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import java.util.Map;
import java.util.Set;

/**
 * Central authority for relationship state transitions.
 *
 * <p>All match-state transition checks must go through this policy.
 * The policy never mutates state — it only decides whether a transition is allowed.
 */
public final class RelationshipWorkflowPolicy {

    private static final Map<MatchState, Set<MatchState>> ALLOWED_TRANSITIONS = Map.of(
            MatchState.ACTIVE,
                    Set.of(MatchState.FRIENDS, MatchState.UNMATCHED, MatchState.GRACEFUL_EXIT, MatchState.BLOCKED),
            MatchState.FRIENDS, Set.of(MatchState.UNMATCHED, MatchState.GRACEFUL_EXIT, MatchState.BLOCKED));

    public WorkflowDecision canTransition(Match match, MatchState targetState) {
        if (match == null) {
            return WorkflowDecision.deny("MATCH_NOT_FOUND", "Match cannot be null");
        }
        if (targetState == null) {
            return WorkflowDecision.deny("NULL_TARGET", "Target state cannot be null");
        }
        MatchState current = match.getState();
        if (current == targetState) {
            return WorkflowDecision.deny("SAME_STATE", "Already in state " + targetState);
        }
        Set<MatchState> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(targetState)) {
            return WorkflowDecision.deny(
                    "INVALID_TRANSITION", "Cannot transition from " + current + " to " + targetState);
        }
        return WorkflowDecision.allow();
    }

    public WorkflowDecision canRequestFriendZone(Match match) {
        if (match == null) {
            return WorkflowDecision.deny("MATCH_NOT_FOUND", "Match cannot be null");
        }
        if (match.getState() != MatchState.ACTIVE) {
            return WorkflowDecision.deny("NOT_ACTIVE", "Friend zone requires active match");
        }
        return WorkflowDecision.allow();
    }

    public WorkflowDecision canGracefulExit(Match match) {
        return canTransition(match, MatchState.GRACEFUL_EXIT);
    }

    public WorkflowDecision canUnmatch(Match match) {
        return canTransition(match, MatchState.UNMATCHED);
    }

    public WorkflowDecision canBlock(Match match) {
        return canTransition(match, MatchState.BLOCKED);
    }

    public WorkflowDecision canSendMessage(Match match, User sender, User recipient) {
        if (sender == null || sender.getState() != UserState.ACTIVE) {
            return WorkflowDecision.deny("SENDER_NOT_ACTIVE", "Sender must be an active user");
        }
        if (recipient == null || recipient.getState() != UserState.ACTIVE) {
            return WorkflowDecision.deny("RECIPIENT_NOT_ACTIVE", "Recipient must be an active user");
        }
        if (match == null || !match.canMessage()) {
            return WorkflowDecision.deny("MATCH_NOT_MESSAGEABLE", "Match does not allow messaging");
        }
        return WorkflowDecision.allow();
    }

    /** Returns the set of states reachable from the given state. */
    public Set<MatchState> allowedTransitionsFrom(MatchState state) {
        return ALLOWED_TRANSITIONS.getOrDefault(state, Set.of());
    }

    /** Returns true if the given state has no outgoing transitions. */
    public boolean isTerminal(MatchState state) {
        return !ALLOWED_TRANSITIONS.containsKey(state);
    }
}
