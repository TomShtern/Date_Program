package datingapp.ui.viewmodel;

import datingapp.core.workflow.WorkflowDecision;
import java.util.List;

/**
 * Immutable onboarding snapshot for the profile screen.
 */
public record ProfileOnboardingState(
        boolean active, String headline, String summary, String primaryActionLabel, List<String> checklist) {

    public ProfileOnboardingState {
        checklist = checklist != null ? List.copyOf(checklist) : List.of();
    }

    public static ProfileOnboardingState hidden() {
        return new ProfileOnboardingState(false, "", "", "Save changes", List.of());
    }

    public static ProfileOnboardingState from(
            boolean active, WorkflowDecision activationDecision, List<String> nextSteps) {
        if (!active) {
            return hidden();
        }

        boolean activationReady = activationDecision != null && activationDecision.isAllowed();
        String headline = activationReady ? "Your profile is ready" : "Finish your profile to start matching";
        String summary = activationReady
                ? "Save once more to activate this profile and continue to the dashboard."
                : "Complete the remaining sections below to unlock activation.";
        return new ProfileOnboardingState(true, headline, summary, "Finish onboarding", nextSteps);
    }
}
