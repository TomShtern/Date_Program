package datingapp.core;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Finds candidate users for matching based on preferences and filters.
 *
 * <p><strong>Architectural Note:</strong> This class intentionally stays in the {@code core/}
 * package (not {@code service/}) because it is a <strong>pure stateless algorithm</strong> with
 * zero infrastructure dependencies. Unlike services that depend on storage interfaces or maintain
 * state, CandidateFinder is a pure function: {@code (User, List<User>, Set<UUID>) -> List<User>}.
 *
 * <h3>Why This Design?</h3>
 *
 * <ul>
 *   <li><strong>Zero Framework Dependencies:</strong> Operates only on in-memory collections passed
 *       by callers. Never queries storage directly, maintaining the "core stays pure" architectural
 *       rule.
 *   <li><strong>Strategy Pattern:</strong> Designed for easy algorithm swapping (e.g., ML-based
 *       ranking, A/B testing different filters) via dependency injection or adapter interfaces.
 *   <li><strong>Stateless Transform:</strong> No mutable state, no persistence concerns. Each call
 *       is independent and deterministic given the same inputs.
 *   <li><strong>Performance:</strong> Operates on in-memory lists for speed. Callers are
 *       responsible for fetching active users from storage and passing them in.
 * </ul>
 *
 * <h3>Naming Rationale</h3>
 *
 * <p>This class does NOT follow the {@code *Service} naming convention (e.g., {@code
 * MatchingService}, {@code DailyService}) because it is not a service in the architectural
 * sense. Services in this codebase:
 *
 * <ul>
 *   <li>Depend on storage interfaces (e.g., {@code UserStorage}, {@code LikeStorage})
 *   <li>Manage state or coordinate persistence operations
 *   <li>Handle business workflows with side effects
 * </ul>
 *
 * <p>CandidateFinder is a pure filter/transform utility, more similar to {@code GeoUtils} or {@code
 * InterestMatcher} than to services like {@code MatchingService}. The {@code Finder} suffix
 * emphasizes its role as a stateless search algorithm.
 *
 * <h3>Related Naming Inconsistencies</h3>
 *
 * <p>For historical consistency, {@code DealbreakersEvaluator} and {@code InterestMatcher} should
 * also be renamed to follow either the {@code *Service} pattern or remain as utilities. This is
 * tracked in <a href="file:///DEVELOPMENT_PLAN.md">DEVELOPMENT_PLAN.md</a> items #6 and #15.
 *
 * <h3>Future Improvements</h3>
 *
 * <ul>
 *   <li><strong>Pagination:</strong> Currently returns all matches in memory. Future versions could
 *       support cursor-based pagination for large result sets.
 *   <li><strong>Caching Layer:</strong> Filter results could be cached (Redis/in-memory) to avoid
 *       recomputing for repeated queries.
 *   <li><strong>Scoring/Ranking:</strong> Beyond distance sorting, could incorporate match quality
 *       scores for intelligent ranking.
 * </ul>
 *
 * @see DealbreakersEvaluator
 * @see MatchQualityService.InterestMatcher
 * @see GeoUtils
 */
public class CandidateFinder {

    /** Geographic utility functions. Pure Java - no external dependencies. */
    public static final class GeoUtils {

        private static final double EARTH_RADIUS_KM = 6371.0;

        private GeoUtils() {
            // Utility class
        }

        /**
         * Calculates the distance in kilometers between two geographic points using the Haversine
         * formula.
         *
         * @param lat1 Latitude of point 1 in degrees
         * @param lon1 Longitude of point 1 in degrees
         * @param lat2 Latitude of point 2 in degrees
         * @param lon2 Longitude of point 2 in degrees
         * @return Distance in kilometers
         */
        public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
            double deltaLatitude = Math.toRadians(lat2 - lat1);
            double deltaLongitude = Math.toRadians(lon2 - lon1);

            double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                    + Math.cos(Math.toRadians(lat1))
                            * Math.cos(Math.toRadians(lat2))
                            * Math.sin(deltaLongitude / 2)
                            * Math.sin(deltaLongitude / 2);

            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

            return EARTH_RADIUS_KM * c;
        }
    }

    private final DealbreakersEvaluator dealbreakersEvaluator;

    public CandidateFinder() {
        this.dealbreakersEvaluator = new DealbreakersEvaluator();
    }

    /**
     * Finds candidates for the given seeker from a list of active users.
     *
     * <p>Filtering rules (ALL must be true for a candidate): 1. Not self 2. Not already interacted
     * (liked/passed) 3. Mutual gender preferences (both ways) 4. Mutual age preferences (both ways)
     * 5. Within seeker's distance preference 6. Passes seeker's dealbreakers (Phase 0.5b)
     *
     * <p>Results are sorted by distance (closest first).
     */
    public List<User> findCandidates(User seeker, List<User> allActive, Set<UUID> alreadyInteracted) {
        return allActive.stream()
                .filter(candidate -> !candidate.getId().equals(seeker.getId())) // Not self
                .filter(candidate -> candidate.getState() == User.State.ACTIVE) // Must be active
                .filter(candidate -> !alreadyInteracted.contains(candidate.getId())) // Not already interacted
                .filter(candidate -> hasMatchingGenderPreferences(seeker, candidate)) // Mutual gender
                .filter(candidate -> hasMatchingAgePreferences(seeker, candidate)) // Mutual age
                .filter(candidate -> isWithinDistance(seeker, candidate)) // Within distance
                .filter(candidate -> dealbreakersEvaluator.passes(seeker, candidate)) // Dealbreakers (Phase 0.5b)
                .sorted(Comparator.comparingDouble(c -> distanceTo(seeker, c))) // Sort by distance
                .toList();
    }

    /**
     * Checks if gender preferences match both ways: - Seeker is interested in candidate's gender -
     * Candidate is interested in seeker's gender.
     */
    private boolean hasMatchingGenderPreferences(User seeker, User candidate) {
        if (seeker.getGender() == null || candidate.getGender() == null) {
            return false;
        }
        if (seeker.getInterestedIn() == null || candidate.getInterestedIn() == null) {
            return false;
        }

        boolean seekerInterestedInCandidate = seeker.getInterestedIn().contains(candidate.getGender());
        boolean candidateInterestedInSeeker = candidate.getInterestedIn().contains(seeker.getGender());

        return seekerInterestedInCandidate && candidateInterestedInSeeker;
    }

    /**
     * Checks if age preferences match both ways: - Candidate's age is within seeker's age range -
     * Seeker's age is within candidate's age range.
     */
    private boolean hasMatchingAgePreferences(User seeker, User candidate) {
        int seekerAge = seeker.getAge();
        int candidateAge = candidate.getAge();

        if (seekerAge == 0 || candidateAge == 0) {
            return false; // Missing birth date
        }

        boolean candidateInSeekerRange = candidateAge >= seeker.getMinAge() && candidateAge <= seeker.getMaxAge();
        boolean seekerInCandidateRange = seekerAge >= candidate.getMinAge() && seekerAge <= candidate.getMaxAge();

        return candidateInSeekerRange && seekerInCandidateRange;
    }

    /** Checks if the candidate is within the seeker's max distance preference. */
    private boolean isWithinDistance(User seeker, User candidate) {
        double distance = distanceTo(seeker, candidate);
        return distance <= seeker.getMaxDistanceKm();
    }

    /** Calculates distance between two users. */
    private double distanceTo(User a, User b) {
        return GeoUtils.distanceKm(a.getLat(), a.getLon(), b.getLat(), b.getLon());
    }
}
