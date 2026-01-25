package datingapp.core;

import datingapp.core.Preferences.PacePreferences;

/**
 * Service for calculating compatibility between users' pace preferences. Uses a weighted ordinal
 * distance with wildcard logic for flexibility.
 */
public class PaceCompatibilityService {

    private static final int LOW_COMPATIBILITY_THRESHOLD = 50;
    private static final int WILDCARD_SCORE = 20;

    /**
     * Calculates a compatibility score (0-100) between two users' pace preferences.
     *
     * @param a first user's preferences
     * @param b second user's preferences
     * @return score from 0 to 100, or -1 if either is null
     */
    public int calculateCompatibility(PacePreferences a, PacePreferences b) {
        if (a == null || b == null || !a.isComplete() || !b.isComplete()) {
            return -1; // Compatibility unknown
        }

        int score = 0;

        // Dimension 1: Messaging Frequency
        score += dimensionScore(a.messagingFrequency(), b.messagingFrequency(), false);

        // Dimension 2: Time to First Date
        score += dimensionScore(a.timeToFirstDate(), b.timeToFirstDate(), false);

        // Dimension 3: Communication Style (Wildcard: MIX_OF_EVERYTHING)
        boolean commStyleWildcard = isCommunicationStyleWildcard(a.communicationStyle())
                || isCommunicationStyleWildcard(b.communicationStyle());
        score += dimensionScore(a.communicationStyle(), b.communicationStyle(), commStyleWildcard);

        // Dimension 4: Depth Preference (Wildcard: DEPENDS_ON_VIBE)
        boolean depthWildcard =
                isDepthPreferenceWildcard(a.depthPreference()) || isDepthPreferenceWildcard(b.depthPreference());
        score += dimensionScore(a.depthPreference(), b.depthPreference(), depthWildcard);

        return score;
    }

    /** Calculates a compatibility score as a double (0.0-1.0). */
    public double calculatePaceScore(PacePreferences a, PacePreferences b) {
        int comp = calculateCompatibility(a, b);
        if (comp == -1) {
            return 0.5; // Neutral
        }
        return (double) comp / 100.0;
    }

    private int dimensionScore(Enum<?> a, Enum<?> b, boolean hasWildcard) {
        if (hasWildcard) {
            return WILDCARD_SCORE;
        }

        int distance = Math.abs(a.ordinal() - b.ordinal());
        return switch (distance) {
            case 0 -> 25; // Perfect match
            case 1 -> 15; // Close enough
            default -> 5; // Quite different
        };
    }

    private boolean isCommunicationStyleWildcard(PacePreferences.CommunicationStyle style) {
        return style == PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING;
    }

    private boolean isDepthPreferenceWildcard(PacePreferences.DepthPreference preference) {
        return preference == PacePreferences.DepthPreference.DEPENDS_ON_VIBE;
    }

    /** Checks if a score is considered low compatibility. */
    public boolean isLowCompatibility(int score) {
        return score >= 0 && score < LOW_COMPATIBILITY_THRESHOLD;
    }

    /** Gets the warning message for low compatibility. */
    public String getLowCompatibilityWarning() {
        return "Your pacing styles differ significantly. Worth discussing early!";
    }
}
