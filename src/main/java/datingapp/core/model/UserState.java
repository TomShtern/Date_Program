package datingapp.core.model;

/**
 * Lifecycle state of a user account.
 * Valid transitions: INCOMPLETE → ACTIVE ↔ PAUSED → BANNED
 */
public enum UserState {
    INCOMPLETE,
    ACTIVE,
    PAUSED,
    BANNED
}
