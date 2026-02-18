package datingapp.core.matching;

import datingapp.core.profile.MatchPreferences.Lifestyle;
import java.util.Set;

/**
 * Utility for comparing lifestyle preferences and checking dealbreakers.
 * Consolidates logic from MatchQualityService and MatchPreferences.
 */
public final class LifestyleMatcher {

    private LifestyleMatcher() {
        // Utility class
    }

    // ========================================================================
    // Dealbreaker Checks (Set Containment)
    // ========================================================================

    /** Check if a lifestyle value is acceptable given a set of allowed values. */
    public static <E extends Enum<E>> boolean isAcceptable(E value, Set<E> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true; // No dealbreaker set
        }
        return value != null && allowed.contains(value);
    }

    // ========================================================================
    // Similarity Checks (Equality)
    // ========================================================================

    /** Check if two users have the same smoking habit. */
    public static boolean isMatch(Lifestyle.Smoking a, Lifestyle.Smoking b) {
        return a != null && b != null && a == b;
    }

    /** Check if two users have the same drinking habit. */
    public static boolean isMatch(Lifestyle.Drinking a, Lifestyle.Drinking b) {
        return a != null && b != null && a == b;
    }

    /** Check if two users have the same relationship goal. */
    public static boolean isMatch(Lifestyle.LookingFor a, Lifestyle.LookingFor b) {
        return a != null && b != null && a == b;
    }

    // ========================================================================
    // Compatibility Checks (Business Logic)
    // ========================================================================

    /**
     * Checks if two kids stances are compatible.
     * Logic:
     * - Exact match is compatible
     * - OPEN is compatible with everything
     * - SOMEDAY and HAS_KIDS are compatible usually (mixed families)
     */
    public static boolean areKidsStancesCompatible(Lifestyle.WantsKids a, Lifestyle.WantsKids b) {
        if (a == null || b == null) {
            return false;
        }
        // Compatible if same
        if (a == b) {
            return true;
        }
        // OPEN is compatible with everything
        if (a == Lifestyle.WantsKids.OPEN || b == Lifestyle.WantsKids.OPEN) {
            return true;
        }
        // SOMEDAY and HAS_KIDS are compatible
        return (a == Lifestyle.WantsKids.SOMEDAY && b == Lifestyle.WantsKids.HAS_KIDS)
                || (a == Lifestyle.WantsKids.HAS_KIDS && b == Lifestyle.WantsKids.SOMEDAY);
    }
}
