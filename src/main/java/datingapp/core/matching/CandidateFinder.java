package datingapp.core.matching;

import datingapp.core.LoggingSupport;
import datingapp.core.PerformanceMonitor;
import datingapp.core.model.Gender;
import datingapp.core.model.User;
import datingapp.core.model.UserState;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CandidateFinder implements LoggingSupport {

    private static final Logger logger = LoggerFactory.getLogger(CandidateFinder.class);

    private final UserStorage userStorage;
    private final InteractionStorage interactionStorage;
    private final TrustSafetyStorage trustSafetyStorage;

    /**
     * Constructs a CandidateFinder with the required storage dependencies.
     *
     * @param userStorage the user storage implementation
     * @param interactionStorage the interaction storage implementation
     * @param trustSafetyStorage the trust safety storage implementation
     * @throws NullPointerException if any parameter is null
     */
    public CandidateFinder(
            UserStorage userStorage, InteractionStorage interactionStorage, TrustSafetyStorage trustSafetyStorage) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
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
        Set<Gender> seekerInterestedIn = seeker.getInterestedIn();
        logDebug(
                "Finding candidates for {} (state={}, gender={}, interestedIn={}, age={}, minAge={}, maxAge={})",
                seeker.getName(),
                seeker.getState(),
                seeker.getGender(),
                seekerInterestedIn,
                seeker.getAge(),
                seeker.getMinAge(),
                seeker.getMaxAge());
        logDebug("Total active users to filter: {}", allActive.size());
        logDebug("Already interacted with: {} users", alreadyInteracted.size());

        List<User> candidates = allActive.stream()
                .filter(candidate -> isNotSelf(seeker, candidate))
                .filter(this::isActiveCandidate)
                .filter(candidate -> notAlreadyInteracted(candidate, alreadyInteracted))
                .filter(candidate -> matchesGenderPreferences(seeker, candidate, seekerInterestedIn))
                .filter(candidate -> matchesAgePreferences(seeker, candidate))
                .filter(candidate -> isWithinDistanceWithLogging(seeker, candidate))
                .filter(candidate -> passesDealbreakers(seeker, candidate))
                .sorted(Comparator.comparingDouble(c -> distanceTo(seeker, c)))
                .toList();

        if (candidates.isEmpty()) {
            logDebug(
                    "CandidateFinder: Found 0 candidates for {} (from {} active users)",
                    seeker.getName(),
                    allActive.size());
        } else {
            logInfo(
                    "CandidateFinder: Found {} candidates for {} (from {} active users)",
                    candidates.size(),
                    seeker.getName(),
                    allActive.size());
        }

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
        try (PerformanceMonitor.Timer timer = PerformanceMonitor.startTimer("CandidateFinder.findCandidatesForUser")) {
            Set<UUID> excluded = new HashSet<>(interactionStorage.getLikedOrPassedUserIds(currentUser.getId()));
            excluded.addAll(trustSafetyStorage.getBlockedUserIds(currentUser.getId()));

            // Use SQL pre-filtering for primary criteria to reduce the candidate set
            // before the more-expensive in-memory filters run.
            int distanceKm = currentUser.hasLocationSet() ? currentUser.getMaxDistanceKm() : 50_000;
            List<User> preFiltered = userStorage.findCandidates(
                    currentUser.getId(),
                    currentUser.getInterestedIn(),
                    currentUser.getMinAge(),
                    currentUser.getMaxAge(),
                    currentUser.getLat(),
                    currentUser.getLon(),
                    distanceKm);

            List<User> candidates = findCandidates(currentUser, preFiltered, excluded);
            if (logger.isTraceEnabled()) {
                logger.trace("CandidateFinder.findCandidatesForUser completed in {}ms", timer.elapsedMs());
            }
            timer.markSuccess();
            return candidates;
        }
    }

    private boolean isNotSelf(User seeker, User candidate) {
        boolean notSelf = !candidate.getId().equals(seeker.getId());
        if (!notSelf) {
            logTrace("Rejecting {}: IS SELF", candidate.getName());
        }
        return notSelf;
    }

    private boolean isActiveCandidate(User candidate) {
        boolean isActive = candidate.getState() == UserState.ACTIVE;
        if (!isActive) {
            logDebug(
                    "Rejecting {} ({}): NOT ACTIVE (state={})",
                    candidate.getName(),
                    candidate.getId(),
                    candidate.getState());
        }
        return isActive;
    }

    private boolean notAlreadyInteracted(User candidate, Set<UUID> alreadyInteracted) {
        boolean notInteracted = !alreadyInteracted.contains(candidate.getId());
        if (!notInteracted) {
            logTrace("Rejecting {}: ALREADY INTERACTED", candidate.getName());
        }
        return notInteracted;
    }

    private boolean matchesGenderPreferences(User seeker, User candidate, Set<Gender> seekerInterestedIn) {
        boolean genderMatch = hasMatchingGenderPreferences(seeker, candidate, seekerInterestedIn);
        if (!genderMatch) {
            logDebug(
                    "Rejecting {} ({}): GENDER MISMATCH - seeker({})→interestedIn({}), candidate({})→interestedIn({})",
                    candidate.getName(),
                    candidate.getId(),
                    seeker.getGender(),
                    seekerInterestedIn,
                    candidate.getGender(),
                    candidate.getInterestedIn());
        }
        return genderMatch;
    }

    private boolean matchesAgePreferences(User seeker, User candidate) {
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
    }

    private boolean isWithinDistanceWithLogging(User seeker, User candidate) {
        boolean inDistance = isWithinDistance(seeker, candidate);
        if (!inDistance && logger.isDebugEnabled()) {
            double dist = distanceTo(seeker, candidate);
            logDebug(
                    "Rejecting {} ({}): TOO FAR - distance={}km, max={}km",
                    candidate.getName(),
                    candidate.getId(),
                    String.format("%.1f", dist),
                    seeker.getMaxDistanceKm());
        }
        return inDistance;
    }

    private boolean passesDealbreakers(User seeker, User candidate) {
        boolean passesDb = Dealbreakers.Evaluator.passes(seeker, candidate);
        if (!passesDb) {
            logDebug("Rejecting {} ({}): DEALBREAKER HIT", candidate.getName(), candidate.getId());
        }
        return passesDb;
    }

    /**
     * Checks if gender preferences match both ways: - Seeker is interested in
     * candidate's gender -
     * Candidate is interested in seeker's gender.
     */
    private boolean hasMatchingGenderPreferences(User seeker, User candidate, Set<Gender> seekerInterestedIn) {
        if (seeker.getGender() == null || candidate.getGender() == null) {
            return false;
        }
        if (seekerInterestedIn == null || candidate.getInterestedIn() == null) {
            return false;
        }

        boolean seekerInterestedInCandidate = seekerInterestedIn.contains(candidate.getGender());
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
        return user.hasLocationSet();
    }

    private String formatLatLon(User user) {
        if (!hasLocation(user)) {
            return "missing";
        }
        return String.format("%.4f, %.4f", user.getLat(), user.getLon());
    }

    @Override
    public Logger logger() {
        return logger;
    }
}
