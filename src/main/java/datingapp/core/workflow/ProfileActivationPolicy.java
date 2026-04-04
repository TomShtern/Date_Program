package datingapp.core.workflow;

import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import java.util.List;

/**
 * Central authority for profile activation eligibility.
 *
 * <p>Centralizes the "can this user be activated?" check that was previously
 * duplicated across ProfileUseCases, LoginViewModel, and ProfileViewModel.
 */
public final class ProfileActivationPolicy {
    public static final String REASON_ALREADY_ACTIVE = "ALREADY_ACTIVE";

    public WorkflowDecision canActivate(User user) {
        if (user == null) {
            return WorkflowDecision.deny("NULL_USER", "User cannot be null");
        }
        UserState state = user.getState();
        if (state == UserState.BANNED) {
            return WorkflowDecision.deny("BANNED", "Banned users cannot activate");
        }
        if (state == UserState.ACTIVE) {
            return WorkflowDecision.deny(REASON_ALREADY_ACTIVE, "User is already active");
        }
        if (state != UserState.INCOMPLETE && state != UserState.PAUSED) {
            return WorkflowDecision.deny("WRONG_STATE", "Unexpected state: " + state);
        }
        List<String> missing = missingFields(user);
        if (!missing.isEmpty()) {
            return WorkflowDecision.deny(
                    "INCOMPLETE_PROFILE", "Profile is not complete. Missing: " + String.join(", ", missing));
        }
        return WorkflowDecision.allow();
    }

    public ActivationResult tryActivate(User user) {
        WorkflowDecision decision = canActivate(user);
        if (decision.isDenied()) {
            return ActivationResult.notActivated(decision);
        }
        user.activate();
        return ActivationResult.activated(user);
    }

    public record ActivationResult(boolean activated, User user, WorkflowDecision decision) {
        public static ActivationResult activated(User user) {
            return new ActivationResult(true, user, WorkflowDecision.allow());
        }

        public static ActivationResult notActivated(WorkflowDecision decision) {
            return new ActivationResult(false, null, decision);
        }
    }

    List<String> missingFields(User user) {
        return user.getMissingProfileFields();
    }
}
