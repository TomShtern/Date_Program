package datingapp.core.model;

/**
 * Reasons why a relationship/match was archived or ended.
 *
 * <p>
 * Note: Some reasons overlap with terminal {@link MatchState} values. We keep
 * both for analytics/history without changing the state machine.
 */
public enum MatchArchiveReason {
    FRIEND_ZONE,
    GRACEFUL_EXIT,
    UNMATCH,
    BLOCK
}
