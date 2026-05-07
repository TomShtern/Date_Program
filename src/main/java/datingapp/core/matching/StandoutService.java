package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.storage.UserStorage;
import datingapp.location.GeoUtils;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Identifies and manages "Standout" profile recommendations. */
public class StandoutService {

    private static final double UNKNOWN_DISTANCE_SCORE = 0.5;

    private final CompatibilityCalculator calculator;
    private final UserStorage userStorage;
    private final CandidateFinder candidateFinder;
    private final Standout.Storage standoutStorage;
    private final ProfileService profileService;
    private final AppConfig config;
    private final Clock clock;

    /**
     * Protected no-arg constructor for anonymous test subclasses only.
     * <p>Do not call the public standout API on a base instance created this way; the service
     * dependencies are intentionally uninitialized and those methods will fail fast.
     */
    protected StandoutService() {
        this.calculator = null;
        this.userStorage = null;
        this.candidateFinder = null;
        this.standoutStorage = null;
        this.profileService = null;
        this.config = null;
        this.clock = null;
    }

    public StandoutService(
            CompatibilityCalculator calculator,
            UserStorage userStorage,
            CandidateFinder candidateFinder,
            Standout.Storage standoutStorage,
            ProfileService profileService,
            AppConfig config) {
        this(calculator, userStorage, candidateFinder, standoutStorage, profileService, config, AppClock.clock());
    }

    public StandoutService(
            CompatibilityCalculator calculator,
            UserStorage userStorage,
            CandidateFinder candidateFinder,
            Standout.Storage standoutStorage,
            ProfileService profileService,
            AppConfig config,
            Clock clock) {
        this.calculator = Objects.requireNonNull(calculator, "calculator cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.candidateFinder = Objects.requireNonNull(candidateFinder, "candidateFinder cannot be null");
        this.standoutStorage = Objects.requireNonNull(standoutStorage, "standoutStorage cannot be null");
        this.profileService = Objects.requireNonNull(profileService, "profileService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public Result getStandouts(User seeker) {
        requireDependencies();
        LocalDate today = LocalDate.now(clock.withZone(config.safety().userTimeZone()));
        List<Standout> cached = standoutStorage.getStandouts(seeker.getId(), today);
        if (!cached.isEmpty()) {
            return Result.of(cached, cached.size(), true);
        }
        return generateStandouts(seeker, today);
    }

    public void markInteracted(UUID seekerId, UUID standoutUserId) {
        requireStandoutStorage();
        requireConfig();
        requireClock();
        LocalDate today = LocalDate.now(clock.withZone(config.safety().userTimeZone()));
        standoutStorage.markInteracted(seekerId, standoutUserId, today);
    }

    public Map<UUID, User> resolveUsers(List<Standout> standouts) {
        requireUserStorage();
        List<UUID> ids = standouts.stream().map(Standout::standoutUserId).toList();
        return userStorage.findByIds(Set.copyOf(ids));
    }

    private Result generateStandouts(User seeker, LocalDate date) {
        requireCandidateFinder();
        requireStandoutStorage();
        requireConfig();
        List<User> candidates = candidateFinder.findCandidatesForUser(seeker);
        if (candidates.isEmpty()) {
            return Result.empty("No standouts available. Try adjusting your preferences!");
        }
        Set<UUID> recentStandoutIds = getRecentStandoutIds(seeker.getId(), date);
        List<ScoredCandidate> scored = candidates.stream()
                .filter(candidate -> !recentStandoutIds.contains(candidate.getId()))
                .map(candidate -> scoreCandidate(seeker, candidate))
                .filter(scoredCandidate ->
                        scoredCandidate.score() >= config.algorithm().standoutMinScore())
                .sorted(Comparator.comparingInt(ScoredCandidate::score).reversed())
                .limit(config.validation().maxStandouts())
                .toList();
        if (scored.isEmpty()) {
            return Result.empty("Check back tomorrow for fresh standouts!");
        }
        List<Standout> standouts = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            ScoredCandidate scoredCandidate = scored.get(i);
            standouts.add(Standout.create(
                    seeker.getId(),
                    scoredCandidate.user().getId(),
                    date,
                    i + 1,
                    scoredCandidate.score(),
                    scoredCandidate.reason(),
                    clock.instant()));
        }
        standoutStorage.saveStandouts(seeker.getId(), standouts, date);
        return Result.of(standouts, candidates.size(), false);
    }

    private ScoredCandidate scoreCandidate(User seeker, User candidate) {
        requireCalculator();
        requireProfileService();
        requireConfig();
        double distanceKm = calculateDistanceKm(seeker, candidate);
        double distanceScore = calculateDistanceScore(seeker, distanceKm);
        double ageScore = calculator.calculateAgeScore(seeker, candidate);
        InterestMatcher.MatchResult interests =
                InterestMatcher.compare(seeker.getInterests(), candidate.getInterests());
        double interestScore = calculator.calculateInterestScore(seeker, candidate);
        double lifestyleScore = calculator.calculateLifestyleScore(seeker, candidate);
        double completenessScore = profileService.calculate(candidate).score() / 100.0;
        double activityScore = calculator.calculateActivityScore(candidate);
        WeightedScore composite = WeightedScore.empty()
                .add(distanceScore, config.algorithm().standoutDistanceWeight())
                .add(ageScore, config.algorithm().standoutAgeWeight())
                .add(interestScore, config.algorithm().standoutInterestWeight())
                .add(lifestyleScore, config.algorithm().standoutLifestyleWeight())
                .add(completenessScore, config.algorithm().standoutCompletenessWeight())
                .add(activityScore, config.algorithm().standoutActivityWeight());
        int score = (int) Math.round(composite.weightedSum() * 100);
        String reason = generateStandoutReason(seeker, candidate, interests, distanceKm, lifestyleScore);
        return new ScoredCandidate(candidate, score, reason);
    }

    private double calculateDistanceKm(User seeker, User candidate) {
        if (!seeker.hasLocationSet() || !candidate.hasLocationSet()) {
            return -1;
        }
        return GeoUtils.distanceKm(seeker.getLat(), seeker.getLon(), candidate.getLat(), candidate.getLon());
    }

    private double calculateDistanceScore(User seeker, double distanceKm) {
        requireCalculator();
        if (distanceKm < 0 || seeker.getMaxDistanceKm() <= 0) {
            return UNKNOWN_DISTANCE_SCORE;
        }
        return calculator.calculateDistanceScore(distanceKm, seeker.getMaxDistanceKm());
    }

    private String generateStandoutReason(
            User seeker, User candidate, InterestMatcher.MatchResult interests, double distanceKm, double lifestyle) {
        requireConfig();
        List<String> reasons = new ArrayList<>();
        if (interests.sharedCount() >= config.matching().minSharedInterests()) {
            reasons.add("Many shared interests");
        } else if (interests.sharedCount() >= 1) {
            reasons.add("Shared interests");
        }
        if (distanceKm >= 0 && distanceKm < config.algorithm().nearbyDistanceKm()) {
            reasons.add("Lives nearby");
        }
        if (lifestyle >= 0.75) {
            reasons.add("Compatible lifestyle");
        }
        if (seeker.getLookingFor() != null && seeker.getLookingFor() == candidate.getLookingFor()) {
            reasons.add("Same relationship goals");
        }
        return reasons.isEmpty() ? "Top match for you" : reasons.getFirst();
    }

    private Set<UUID> getRecentStandoutIds(UUID seekerId, LocalDate today) {
        requireStandoutStorage();
        requireConfig();
        Set<UUID> recent = new HashSet<>();
        for (int i = 1; i <= config.algorithm().standoutDiversityDays(); i++) {
            standoutStorage.getStandouts(seekerId, today.minusDays(i)).stream()
                    .map(Standout::standoutUserId)
                    .forEach(recent::add);
        }
        return Set.copyOf(recent);
    }

    private record ScoredCandidate(User user, int score, String reason) {}

    private void requireDependencies() {
        requireCalculator();
        requireUserStorage();
        requireCandidateFinder();
        requireStandoutStorage();
        requireProfileService();
        requireConfig();
        requireClock();
    }

    private void requireCalculator() {
        if (calculator == null) {
            throw new IllegalStateException("StandoutService calculator is not initialized");
        }
    }

    private void requireUserStorage() {
        if (userStorage == null) {
            throw new IllegalStateException("StandoutService userStorage is not initialized");
        }
    }

    private void requireCandidateFinder() {
        if (candidateFinder == null) {
            throw new IllegalStateException("StandoutService candidateFinder is not initialized");
        }
    }

    private void requireStandoutStorage() {
        if (standoutStorage == null) {
            throw new IllegalStateException("StandoutService standoutStorage is not initialized");
        }
    }

    private void requireProfileService() {
        if (profileService == null) {
            throw new IllegalStateException("StandoutService profileService is not initialized");
        }
    }

    private void requireConfig() {
        if (config == null) {
            throw new IllegalStateException("StandoutService config is not initialized");
        }
    }

    private void requireClock() {
        if (clock == null) {
            throw new IllegalStateException("StandoutService clock is not initialized");
        }
    }

    public record Result(List<Standout> standouts, int totalCandidates, boolean fromCache, String message) {
        public boolean isEmpty() {
            return standouts == null || standouts.isEmpty();
        }

        public int count() {
            return standouts != null ? standouts.size() : 0;
        }

        public static Result empty(String message) {
            return new Result(List.of(), 0, false, message);
        }

        public static Result of(List<Standout> standouts, int total, boolean cached) {
            return new Result(standouts, total, cached, null);
        }
    }
}
