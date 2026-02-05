package datingapp.core;

import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.UserStorage;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds candidate users for matching based on preferences and filters.
 *
 * <p>
 * <strong>Architectural Note:</strong> This class intentionally stays in the
 * {@code core/}
 * package (not {@code service/}) because it is a <strong>pure stateless
 * algorithm</strong> with
 * zero infrastructure dependencies. Unlike services that depend on storage
 * interfaces or maintain
 * state, CandidateFinder is a pure function:
 * {@code (User, List<User>, Set<UUID>) -> List<User>}.
 *
 * <h3>Why This Design?</h3>
 *
 * <ul>
 * <li><strong>Zero Framework Dependencies:</strong> Operates only on in-memory
 * collections passed
 * by callers. Never queries storage directly, maintaining the "core stays pure"
 * architectural
 * rule.
 * <li><strong>Strategy Pattern:</strong> Designed for easy algorithm swapping
 * (e.g., ML-based
 * ranking, A/B testing different filters) via dependency injection or adapter
 * interfaces.
 * <li><strong>Stateless Transform:</strong> No mutable state, no persistence
 * concerns. Each call
 * is independent and deterministic given the same inputs.
 * <li><strong>Performance:</strong> Operates on in-memory lists for speed.
 * Callers are
 * responsible for fetching active users from storage and passing them in.
 * </ul>
 *
 * <h3>Naming Rationale</h3>
 *
 * <p>
 * This class does NOT follow the {@code *Service} naming convention (e.g.,
 * {@code
 * MatchingService}, {@code DailyService}) because it is not a service in the
 * architectural
 * sense. Services in this codebase:
 *
 * <ul>
 * <li>Depend on storage interfaces (e.g., {@code User.Storage},
 * {@code LikeStorage})
 * <li>Manage state or coordinate persistence operations
 * <li>Handle business workflows with side effects
 * </ul>
 *
 * <p>
 * CandidateFinder is a pure filter/transform utility, more similar to
 * {@code GeoUtils} or {@code
 * InterestMatcher} than to services like {@code MatchingService}. The
 * {@code Finder} suffix
 * emphasizes its role as a stateless search algorithm.
 *
 * <h3>Related Naming Inconsistencies</h3>
 *
 * <p>
 * For historical consistency, {@code Dealbreakers.Evaluator} and
 * {@code InterestMatcher} should
 * also be renamed to follow either the {@code *Service} pattern or remain as
 * utilities. This is
 * tracked in <a href="file:///DEVELOPMENT_PLAN.md">DEVELOPMENT_PLAN.md</a>
 * items #6 and #15.
 *
 * <h3>Future Improvements</h3>
 *
 * <ul>
 * <li><strong>Pagination:</strong> Currently returns all matches in memory.
 * Future versions could
 * support cursor-based pagination for large result sets.
 * <li><strong>Caching Layer:</strong> Filter results could be cached
 * (Redis/in-memory) to avoid
 * recomputing for repeated queries.
 * <li><strong>Scoring/Ranking:</strong> Beyond distance sorting, could
 * incorporate match quality
 * scores for intelligent ranking.
 * </ul>
 *
 * @see Dealbreakers.Evaluator
 * @see MatchQualityService.InterestMatcher
 * @see GeoUtils
 */
public class CandidateFinder {

    private static final Logger logger = LoggerFactory.getLogger(CandidateFinder.class);

    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final BlockStorage blockStorage;

    /**
     * Constructs a CandidateFinder with the required storage dependencies.
     *
     * @param userStorage the user storage implementation
     * @param likeStorage the like storage implementation
     * @param blockStorage the block storage implementation
     * @throws NullPointerException if any parameter is null
     */
    public CandidateFinder(UserStorage userStorage, LikeStorage likeStorage, BlockStorage blockStorage) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
        this.blockStorage = Objects.requireNonNull(blockStorage, "blockStorage cannot be null");
    }

    /** Geographic utility functions. Pure Java - no external dependencies. */
    public static final class GeoUtils {

        private static final double EARTH_RADIUS_KM = 6371.0;

        private GeoUtils() {
            // Utility class
        }

        /**
         * Calculates the distance in kilometers between two geographic points using the
         * Haversine
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

    /**
     * Finds candidates for the given seeker from a list of active users.
     *
     * <p>
     * Filtering rules (ALL must be true for a candidate): 1. Not self 2. Not
     * already interacted
     * (liked/passed) 3. Mutual gender preferences (both ways) 4. Mutual age
     * preferences (both ways)
     * 5. Within seeker's distance preference 6. Passes seeker's dealbreakers (Phase
     * 0.5b)
     *
     * <p>
     * Results are sorted by distance (closest first).
     */
    public List<User> findCandidates(User seeker, List<User> allActive, Set<UUID> alreadyInteracted) {
        logDebug(
                "Finding candidates for {} (state={}, gender={}, interestedIn={}, age={}, minAge={}, maxAge={})",
                seeker.getName(),
                seeker.getState(),
                seeker.getGender(),
                seeker.getInterestedIn(),
                seeker.getAge(),
                seeker.getMinAge(),
                seeker.getMaxAge());
        logDebug("Total active users to filter: {}", allActive.size());
        logDebug("Already interacted with: {} users", alreadyInteracted.size());

        List<User> candidates = allActive.stream()
                .filter(candidate -> {
                    boolean notSelf = !candidate.getId().equals(seeker.getId());
                    if (!notSelf) {
                        logTrace("Rejecting {}: IS SELF", candidate.getName());
                    }
                    return notSelf;
                })
                .filter(candidate -> {
                    boolean isActive = candidate.getState() == UserState.ACTIVE;
                    if (!isActive) {
                        logDebug(
                                "Rejecting {} ({}): NOT ACTIVE (state={})",
                                candidate.getName(),
                                candidate.getId(),
                                candidate.getState());
                    }
                    return isActive;
                })
                .filter(candidate -> {
                    boolean notInteracted = !alreadyInteracted.contains(candidate.getId());
                    if (!notInteracted) {
                        logTrace("Rejecting {}: ALREADY INTERACTED", candidate.getName());
                    }
                    return notInteracted;
                })
                .filter(candidate -> {
                    boolean genderMatch = hasMatchingGenderPreferences(seeker, candidate);
                    if (!genderMatch) {
                        logDebug(
                                "Rejecting {} ({}): GENDER MISMATCH - seeker({})→interestedIn({}), candidate({})→interestedIn({})",
                                candidate.getName(),
                                candidate.getId(),
                                seeker.getGender(),
                                seeker.getInterestedIn(),
                                candidate.getGender(),
                                candidate.getInterestedIn());
                    }
                    return genderMatch;
                })
                .filter(candidate -> {
                    boolean ageMatch = hasMatchingAgePreferences(seeker, candidate);
                    if (!ageMatch) {
                        logDebug(
                                "Rejecting {} ({}): AGE MISMATCH - seeker(age={}, range={}-{}), candidate(age={}, range={}-{})",
                                candidate.getName(),
                                candidate.getId(),
                                seeker.getAge(),
                                seeker.getMinAge(),
                                seeker.getMaxAge(),
                                candidate.getAge(),
                                candidate.getMinAge(),
                                candidate.getMaxAge());
                    }
                    return ageMatch;
                })
                .filter(candidate -> {
                    boolean inDistance = isWithinDistance(seeker, candidate);
                    if (!inDistance) {
                        if (logger.isDebugEnabled()) {
                            double dist = distanceTo(seeker, candidate);
                            logDebug(
                                    "Rejecting {} ({}): TOO FAR - distance={}km, max={}km",
                                    candidate.getName(),
                                    candidate.getId(),
                                    String.format("%.1f", dist),
                                    seeker.getMaxDistanceKm());
                        }
                    }
                    return inDistance;
                })
                .filter(candidate -> {
                    boolean passesDb = Dealbreakers.Evaluator.passes(seeker, candidate);
                    if (!passesDb) {
                        logDebug("Rejecting {} ({}): DEALBREAKER HIT", candidate.getName(), candidate.getId());
                    }
                    return passesDb;
                })
                .sorted(Comparator.comparingDouble(c -> distanceTo(seeker, c))) // Sort by distance
                .toList();

        logInfo(
                "CandidateFinder: Found {} candidates for {} (from {} active users)",
                candidates.size(),
                seeker.getName(),
                allActive.size());

        return candidates;
    }

    /**
     * Convenience method to find candidates for the given user by fetching active users and
     * exclusions from storage.
     *
     * @param currentUser the user searching for candidates
     * @return list of candidate users sorted by distance
     */
    public List<User> findCandidatesForUser(User currentUser) {
        try (var ignored = PerformanceMonitor.startTimer("CandidateFinder.findCandidatesForUser")) {
            List<User> activeUsers = userStorage.findActive();
            Set<UUID> excluded = new HashSet<>(likeStorage.getLikedOrPassedUserIds(currentUser.getId()));
            excluded.addAll(blockStorage.getBlockedUserIds(currentUser.getId()));
            return findCandidates(currentUser, activeUsers, excluded);
        }
    }

    /**
     * Checks if gender preferences match both ways: - Seeker is interested in
     * candidate's gender -
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
     * Checks if age preferences match both ways: - Candidate's age is within
     * seeker's age range -
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
        if (!hasLocation(seeker) || !hasLocation(candidate)) {
            logDebug(
                    "Skipping distance filter for {} ({}): missing location (seekerLatLon={}, candidateLatLon={}).",
                    candidate.getName(),
                    candidate.getId(),
                    formatLatLon(seeker),
                    formatLatLon(candidate));
            return true;
        }
        double distance = distanceTo(seeker, candidate);
        return distance <= seeker.getMaxDistanceKm();
    }

    /** Calculates distance between two users. */
    private double distanceTo(User a, User b) {
        if (!hasLocation(a) || !hasLocation(b)) {
            return Double.MAX_VALUE;
        }
        return GeoUtils.distanceKm(a.getLat(), a.getLon(), b.getLat(), b.getLon());
    }

    private boolean hasLocation(User user) {
        return user.getLat() != 0.0 || user.getLon() != 0.0;
    }

    private String formatLatLon(User user) {
        if (!hasLocation(user)) {
            return "missing";
        }
        return String.format("%.4f, %.4f", user.getLat(), user.getLon());
    }

    private void logDebug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, args);
        }
    }

    private void logTrace(String message, Object... args) {
        if (logger.isTraceEnabled()) {
            logger.trace(message, args);
        }
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }
}
