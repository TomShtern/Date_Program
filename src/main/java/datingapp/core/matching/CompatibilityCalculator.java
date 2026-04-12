package datingapp.core.matching;

import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import java.time.Duration;
import java.util.Optional;

/**
 * Service for calculating compatibility scores between users.
 * Consolidates scoring logic used by recommendation and quality services.
 */
public interface CompatibilityCalculator {

    /** Calculates age similarity score (0.0-1.0). */
    double calculateAgeScore(User me, User them);

    /**
     * Calculates age similarity score using already-resolved ages when available.
     * Implementations may override this to avoid repeated age lookups on hot paths.
     */
    default double calculateAgeScore(User me, User them, Optional<Integer> meAge, Optional<Integer> themAge) {
        return calculateAgeScore(me, them);
    }

    /** Calculates interest overlap score (0.0-1.0). */
    double calculateInterestScore(User me, User them);

    /** Calculates lifestyle compatibility score (0.0-1.0). */
    double calculateLifestyleScore(User me, User them);

    /** Calculates distance-based score (0.0-1.0). */
    double calculateDistanceScore(double distanceKm, int maxDistanceKm);

    /** Calculates response time score (0.0-1.0) based on how quickly mutual interest happened. */
    double calculateResponseScore(Duration timeBetweenLikes);

    /** Calculates communication pace compatibility score (0.0-1.0). */
    double calculatePaceScore(PacePreferences a, PacePreferences b);

    /** Calculates activity recentness score (0.0-1.0). */
    double calculateActivityScore(User candidate);
}
