package datingapp.ui;

import java.util.UUID;

/**
 * Typed onboarding navigation payload for the profile screen.
 */
public record OnboardingContext(UUID userId, EntryReason entryReason, boolean firstRun) {

    public enum EntryReason {
        NEW_ACCOUNT,
        INCOMPLETE_LOGIN
    }

    public static OnboardingContext newAccount(UUID userId) {
        return new OnboardingContext(userId, EntryReason.NEW_ACCOUNT, true);
    }

    public static OnboardingContext incompleteLogin(UUID userId) {
        return new OnboardingContext(userId, EntryReason.INCOMPLETE_LOGIN, false);
    }
}
