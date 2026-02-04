package datingapp.core;

/**
 * Represents the lifecycle state of a user account. Valid transitions:
 * INCOMPLETE → ACTIVE ↔ PAUSED → BANNED
 */
public enum UserState {
    INCOMPLETE,
    ACTIVE,
    PAUSED,
    BANNED
}
