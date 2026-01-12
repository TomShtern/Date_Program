package datingapp.core;

/** Exception thrown when a relationship transition is invalid. */
public class TransitionValidationException extends RuntimeException {
    public TransitionValidationException(String message) {
        super(message);
    }
}
