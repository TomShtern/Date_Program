package datingapp.core.workflow;

/** Typed allow/deny result from a workflow policy evaluation. */
public sealed interface WorkflowDecision {

    record Allowed() implements WorkflowDecision {}

    record Denied(String reasonCode, String message) implements WorkflowDecision {}

    default boolean isAllowed() {
        return this instanceof Allowed;
    }

    default boolean isDenied() {
        return this instanceof Denied;
    }

    static WorkflowDecision allow() {
        return new Allowed();
    }

    static WorkflowDecision deny(String reasonCode, String message) {
        return new Denied(reasonCode, message);
    }
}
