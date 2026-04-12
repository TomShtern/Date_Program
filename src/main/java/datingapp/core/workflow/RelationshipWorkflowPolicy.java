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

    public enum MessageSendDeniedReason {
        SENDER_NOT_ACTIVE("Sender must be an active user"),
        RECIPIENT_NOT_ACTIVE("Recipient must be an active user"),
        MATCH_NOT_MESSAGEABLE("Match does not allow messaging");

        private final String message;

        MessageSendDeniedReason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    public static record MessageSendDecision(boolean allowed, MessageSendDeniedReason deniedReason) {
        public MessageSendDecision {
            if (allowed && deniedReason != null) {
                throw new IllegalArgumentException("deniedReason must be null when allowed");
            }
            if (!allowed && deniedReason == null) {
                throw new IllegalArgumentException("deniedReason is required when denied");
            }
        }

        public static MessageSendDecision allow() {
            return new MessageSendDecision(true, null);
        }

        public static MessageSendDecision deny(MessageSendDeniedReason deniedReason) {
            return new MessageSendDecision(false, deniedReason);
        }
    }

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

    public MessageSendDecision evaluateMessageSend(Match match, User sender, User recipient) {
        if (sender == null || sender.getState() != UserState.ACTIVE) {
            return MessageSendDecision.deny(MessageSendDeniedReason.SENDER_NOT_ACTIVE);
        }
        if (recipient == null || recipient.getState() != UserState.ACTIVE) {
            return MessageSendDecision.deny(MessageSendDeniedReason.RECIPIENT_NOT_ACTIVE);
        }
        if (match == null || !match.canMessage()) {
            return MessageSendDecision.deny(MessageSendDeniedReason.MATCH_NOT_MESSAGEABLE);
        }
        return MessageSendDecision.allow();
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
