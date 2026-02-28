package datingapp.core.workflow;

import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import java.util.ArrayList;
import java.util.List;

/**
 * Central authority for profile activation eligibility.
 *
 * <p>Centralizes the "can this user be activated?" check that was previously
 * duplicated across ProfileUseCases, LoginViewModel, and ProfileViewModel.
 */
public final class ProfileActivationPolicy {

    public WorkflowDecision canActivate(User user) {
        if (user == null) {
            return WorkflowDecision.deny("NULL_USER", "User cannot be null");
        }
        UserState state = user.getState();
        if (state == UserState.BANNED) {
            return WorkflowDecision.deny("BANNED", "Banned users cannot activate");
        }
        if (state == UserState.ACTIVE) {
            return WorkflowDecision.deny("ALREADY_ACTIVE", "User is already active");
        }
        if (state == UserState.PAUSED) {
            return WorkflowDecision.deny("PAUSED", "Paused users reactivate differently");
        }
        if (state != UserState.INCOMPLETE) {
            return WorkflowDecision.deny("WRONG_STATE", "Unexpected state: " + state);
        }
        if (!user.isComplete()) {
            List<String> missing = missingFields(user);
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

    // Mirrors User.isComplete() logic — if isComplete() changes, update this too.
    List<String> missingFields(User user) {
        List<String> missing = new ArrayList<>();
        if (user.getName() == null || user.getName().isBlank()) {
            missing.add("name");
        }
        if (user.getBio() == null || user.getBio().isBlank()) {
            missing.add("bio");
        }
        if (user.getBirthDate() == null) {
            missing.add("birthDate");
        }
        if (user.getGender() == null) {
            missing.add("gender");
        }
        if (user.getInterestedIn() == null || user.getInterestedIn().isEmpty()) {
            missing.add("interestedIn");
        }
        if (user.getMaxDistanceKm() <= 0) {
            missing.add("maxDistanceKm");
        }
        if (user.getMinAge() <= 0) {
            missing.add("minAge");
        }
        if (user.getMaxAge() < user.getMinAge()) {
            missing.add("maxAge");
        }
        if (user.getPhotoUrls() == null || user.getPhotoUrls().isEmpty()) {
            missing.add("photoUrls");
        }
        if (!user.hasCompletePace()) {
            missing.add("pacePreferences");
        }
        return missing;
    }
}
