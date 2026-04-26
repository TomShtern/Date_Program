package datingapp.core.matching;

import datingapp.core.AppConfig;
import datingapp.core.LoggingSupport;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchArchiveReason;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final java.time.ZoneId timezone;
    private final Clock clock;
    private final Duration rematchCooldown;

    /**
     * Constructs a CandidateFinder with the required storage dependencies and
     * timezone.
     *
     * @param userStorage        the user storage implementation
     * @param interactionStorage the interaction storage implementation
     * @param trustSafetyStorage the trust safety storage implementation
     * @param timezone           the timezone to use for age calculations
     * @throws NullPointerException if any parameter is null
     */
    public CandidateFinder(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            java.time.ZoneId timezone) {
        this(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                timezone,
                datingapp.core.AppClock.clock(),
                Duration.ofHours(AppConfig.DEFAULT_REMATCH_COOLDOWN_HOURS));
    }

    public CandidateFinder(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            java.time.ZoneId timezone,
            Duration rematchCooldown) {
        this(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                timezone,
                datingapp.core.AppClock.clock(),
                rematchCooldown);
    }

    public CandidateFinder(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            java.time.ZoneId timezone,
            Clock clock) {
        this(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                timezone,
                clock,
                Duration.ofHours(AppConfig.DEFAULT_REMATCH_COOLDOWN_HOURS));
    }

    public CandidateFinder(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            java.time.ZoneId timezone,
            Clock clock,
            Duration rematchCooldown) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
        this.timezone = Objects.requireNonNull(timezone, "timezone cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.rematchCooldown = Objects.requireNonNull(rematchCooldown, "rematchCooldown cannot be null");
    }

    public java.time.ZoneId getTimezone() {
        return timezone;
    }

    public Duration getRematchCooldown() {
        return rematchCooldown;
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
        Set<UUID> recentlyUnmatchedCounterpartIds = recentlyUnmatchedCounterpartIds(seeker.getId());
        Map<UUID, Double> distanceCache = new HashMap<>();
        logDebug(
                "Finding candidates for {} (state={}, gender={}, interestedIn={}, age={}, minAge={}, maxAge={})",
                userRef(seeker),
                seeker.getState(),
                seeker.getGender(),
                seekerInterestedIn,
                seeker.getAge(timezone).orElse(null),
                seeker.getMinAge(),
                seeker.getMaxAge());
        logDebug("Total active users to filter: {}", allActive.size());
        logDebug("Already interacted with: {} users", alreadyInteracted.size());

        List<User> candidates = allActive.stream()
                .filter(candidate -> isNotSelf(seeker, candidate))
                .filter(this::isActiveCandidate)
                .filter(candidate -> notAlreadyInteracted(candidate, alreadyInteracted))
                .filter(candidate -> notBlockedEitherDirection(seeker, candidate))
                .filter(candidate -> notInRecentUnmatchCooldown(candidate, recentlyUnmatchedCounterpartIds))
                .filter(candidate -> matchesGenderPreferences(seeker, candidate, seekerInterestedIn))
                .filter(candidate -> matchesAgePreferences(seeker, candidate))
                .filter(candidate -> isWithinDistanceCached(seeker, candidate, distanceCache))
                .filter(candidate -> passesDealbreakers(seeker, candidate))
                .sorted(Comparator.comparingDouble(c -> distanceCache.getOrDefault(c.getId(), Double.MAX_VALUE)))
                .toList();

        if (candidates.isEmpty()) {
            logDebug(
                    "CandidateFinder: Found 0 candidates for {} (from {} active users)",
                    userRef(seeker),
                    allActive.size());
        } else {
            logInfo(
                    "CandidateFinder: Found {} candidates for {} (from {} active users)",
                    candidates.size(),
                    userRef(seeker),
                    allActive.size());
        }

        return candidates;
    }

    /**
     * Convenience method to find candidates for the given user by fetching active
     * users and
     * exclusions from storage.
     *
     * @param currentUser the user searching for candidates
     * @return list of candidate users sorted by distance
     */
    public List<User> findCandidatesForUser(User currentUser) {
        if (!currentUser.hasLocationSet()) {
            if (logger.isTraceEnabled()) {
                logger.trace(
                        "CandidateFinder.findCandidatesForUser skipped for {} due to missing location",
                        currentUser.getId());
            }
            return List.of();
        }

        Set<UUID> excluded = new HashSet<>(interactionStorage.getLikedOrPassedUserIds(currentUser.getId()));

        return findFreshCandidates(currentUser, excluded);
    }

    private List<User> findFreshCandidates(User currentUser, Set<UUID> excluded) {
        List<User> preFiltered = findFreshPrefilteredCandidates(currentUser);
        List<User> candidates = findCandidates(currentUser, preFiltered, excluded);
        if (logger.isTraceEnabled()) {
            logger.trace(
                    "CandidateFinder refreshed candidates for {} with {} results",
                    currentUser.getId(),
                    candidates.size());
        }
        return candidates;
    }

    private List<User> findFreshPrefilteredCandidates(User currentUser) {
        // When a seeker is open to everyone (all genders selected), pass the full
        // Gender enum to the DB pre-filter so the SQL `gender IN (...)` clause does
        // not exclude candidates by gender before the in-memory check runs.
        Set<Gender> gendersForQuery =
                currentUser.isInterestedInEveryone() ? User.matchableGenders() : currentUser.getInterestedIn();

        return userStorage.findCandidates(
                currentUser.getId(),
                gendersForQuery,
                currentUser.getMinAge(),
                currentUser.getMaxAge(),
                currentUser.getLat(),
                currentUser.getLon(),
                currentUser.getMaxDistanceKm());
    }

    public void invalidateCacheFor(UUID userId) {
        // No-op: candidate browsing is deliberately freshness-first for Phase 1.
    }

    public void clearCache() {
        // No-op: candidate browsing is deliberately freshness-first for Phase 1.
    }

    private boolean isNotSelf(User seeker, User candidate) {
        boolean notSelf = !candidate.getId().equals(seeker.getId());
        if (!notSelf) {
            logTrace("Rejecting {}: IS SELF", userRef(candidate));
        }
        return notSelf;
    }

    private boolean isActiveCandidate(User candidate) {
        boolean isActive = candidate.getState() == UserState.ACTIVE;
        if (!isActive) {
            logDebug("Rejecting {}: NOT ACTIVE (state={})", userRef(candidate), candidate.getState());
        }
        return isActive;
    }

    private boolean notAlreadyInteracted(User candidate, Set<UUID> alreadyInteracted) {
        boolean notInteracted = !alreadyInteracted.contains(candidate.getId());
        if (!notInteracted) {
            logTrace("Rejecting {}: ALREADY INTERACTED", userRef(candidate));
        }
        return notInteracted;
    }

    private boolean notBlockedEitherDirection(User seeker, User candidate) {
        boolean notBlocked = !trustSafetyStorage.isBlocked(seeker.getId(), candidate.getId());
        if (!notBlocked) {
            logDebug("Rejecting {}: BLOCKED IN EITHER DIRECTION", userRef(candidate));
        }
        return notBlocked;
    }

    private boolean notInRecentUnmatchCooldown(User candidate, Set<UUID> recentlyUnmatchedCounterpartIds) {
        boolean outsideCooldown = !recentlyUnmatchedCounterpartIds.contains(candidate.getId());
        if (!outsideCooldown) {
            logDebug("Rejecting {}: RECENTLY UNMATCHED DURING COOLDOWN", userRef(candidate));
        }
        return outsideCooldown;
    }

    private boolean matchesGenderPreferences(User seeker, User candidate, Set<Gender> seekerInterestedIn) {
        boolean genderMatch = hasMatchingGenderPreferences(seeker, candidate, seekerInterestedIn);
        if (!genderMatch) {
            logDebug(
                    "Rejecting {}: GENDER MISMATCH - seeker({})->interestedIn({}), candidate({})->interestedIn({})",
                    userRef(candidate),
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
                    "Rejecting {}: AGE MISMATCH - seeker(age={}, range={}-{}), candidate(age={}, range={}-{})",
                    userRef(candidate),
                    seeker.getAge(timezone).orElse(null),
                    seeker.getMinAge(),
                    seeker.getMaxAge(),
                    candidate.getAge(timezone).orElse(null),
                    candidate.getMinAge(),
                    candidate.getMaxAge());
        }
        return ageMatch;
    }

    private boolean isWithinDistanceCached(User seeker, User candidate, Map<UUID, Double> distanceCache) {
        if (!hasLocation(seeker) || !hasLocation(candidate)) {
            return true;
        }
        double distance = GeoUtils.distanceKm(seeker.getLat(), seeker.getLon(), candidate.getLat(), candidate.getLon());
        distanceCache.put(candidate.getId(), distance);
        if (distance > seeker.getMaxDistanceKm()) {
            if (logger.isDebugEnabled()) {
                logDebug(
                        "Rejecting {}: TOO FAR - distance={}km, max={}km",
                        userRef(candidate),
                        String.format("%.1f", distance),
                        seeker.getMaxDistanceKm());
            }
            return false;
        }
        return true;
    }

    private boolean passesDealbreakers(User seeker, User candidate) {
        boolean passesDb = Dealbreakers.Evaluator.passes(seeker, candidate, timezone);
        if (!passesDb) {
            logDebug("Rejecting {}: DEALBREAKER HIT", userRef(candidate));
        }
        return passesDb;
    }

    /**
     * Checks if gender preferences match both ways:
     * <ul>
     *   <li>Seeker is interested in candidate's gender
     *   <li>Candidate is interested in seeker's gender
     * </ul>
     *
     * <p>A user with all genders selected ({@link User#isInterestedInEveryone()}) is
     * treated as compatible with any gender on their side of the check, so "open to
     * everyone" users are never excluded solely because of gender.
     */
    private boolean hasMatchingGenderPreferences(User seeker, User candidate, Set<Gender> seekerInterestedIn) {
        if (seeker.getGender() == null || candidate.getGender() == null) {
            return false;
        }
        if (seekerInterestedIn == null || candidate.getInterestedIn() == null) {
            return false;
        }

        // "Open to everyone" short-circuit: if a user has all genders selected,
        // they are interested in any gender — skip the set membership check for
        // their side and only verify the other side's preference.
        boolean seekerInterestedInCandidate =
                seeker.isInterestedInEveryone() || seekerInterestedIn.contains(candidate.getGender());
        boolean candidateInterestedInSeeker = candidate.isInterestedInEveryone()
                || candidate.getInterestedIn().contains(seeker.getGender());

        return seekerInterestedInCandidate && candidateInterestedInSeeker;
    }

    /**
     * Checks if age preferences match both ways: - Candidate's age is within
     * seeker's age range -
     * Seeker's age is within candidate's age range.
     */
    private boolean hasMatchingAgePreferences(User seeker, User candidate) {
        Integer seekerAge = seeker.getAge(timezone).orElse(null);
        Integer candidateAge = candidate.getAge(timezone).orElse(null);

        if (seekerAge == null || candidateAge == null) {
            return false; // Missing birth date
        }

        boolean candidateInSeekerRange = candidateAge >= seeker.getMinAge() && candidateAge <= seeker.getMaxAge();
        boolean seekerInCandidateRange = seekerAge >= candidate.getMinAge() && seekerAge <= candidate.getMaxAge();

        return candidateInSeekerRange && seekerInCandidateRange;
    }

    private boolean hasLocation(User user) {
        return user.hasLocationSet();
    }

    private Set<UUID> recentlyUnmatchedCounterpartIds(UUID seekerId) {
        if (rematchCooldown.isZero()) {
            return Set.of();
        }
        Instant cutoff = clock.instant().minus(rematchCooldown);
        return interactionStorage.getAllMatchesFor(seekerId).stream()
                .filter(match -> match.getEndReason() == MatchArchiveReason.UNMATCH)
                .filter(match -> match.getEndedAt() != null)
                .filter(match -> match.getEndedAt().isAfter(cutoff))
                .map(match -> counterpartId(match, seekerId))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static UUID counterpartId(Match match, UUID seekerId) {
        if (match.getUserA().equals(seekerId)) {
            return match.getUserB();
        }
        if (match.getUserB().equals(seekerId)) {
            return match.getUserA();
        }
        return null;
    }

    private String userRef(User user) {
        String id = user.getId().toString();
        return "user-" + id.substring(0, 8);
    }

    @Override
    public Logger logger() {
        return logger;
    }
}
