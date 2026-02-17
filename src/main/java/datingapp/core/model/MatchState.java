package datingapp.core.model;

/** Represents the current state of a match. */
public enum MatchState {
    ACTIVE, // Both users are matched
    FRIENDS, // Mutual transition to platonic friendship
    UNMATCHED, // One user ended the match
    GRACEFUL_EXIT, // One user ended the match kindly
    BLOCKED // One user blocked the other
}
