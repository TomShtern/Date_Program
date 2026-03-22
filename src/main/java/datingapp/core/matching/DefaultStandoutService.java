package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.matching.CandidateFinder.GeoUtils;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.storage.UserStorage;
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

/**
 * Default implementation of {@link StandoutService}.
 */
public final class DefaultStandoutService implements StandoutService {

    private final CompatibilityCalculator calculator;
    private final UserStorage userStorage;
    private final CandidateFinder candidateFinder;
    private final Standout.Storage standoutStorage;
    private final ProfileService profileService;
    private final AppConfig config;
    private final Clock clock;

    public DefaultStandoutService(
            CompatibilityCalculator calculator,
            UserStorage userStorage,
            CandidateFinder candidateFinder,
            Standout.Storage standoutStorage,
            ProfileService profileService,
            AppConfig config) {
        this(calculator, userStorage, candidateFinder, standoutStorage, profileService, config, AppClock.clock());
    }

    public DefaultStandoutService(
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

    @Override
    public Result getStandouts(User seeker) {
        LocalDate today = LocalDate.now(clock.withZone(config.safety().userTimeZone()));

        List<Standout> cached = standoutStorage.getStandouts(seeker.getId(), today);
        if (!cached.isEmpty()) {
            return Result.of(cached, cached.size(), true);
        }

        return generateStandouts(seeker, today);
    }

    @Override
    public void markInteracted(UUID seekerId, UUID standoutUserId) {
        LocalDate today = LocalDate.now(clock.withZone(config.safety().userTimeZone()));
        standoutStorage.markInteracted(seekerId, standoutUserId, today);
    }

    @Override
    public Map<UUID, User> resolveUsers(List<Standout> standouts) {
        List<UUID> ids = standouts.stream().map(Standout::standoutUserId).toList();
        return userStorage.findByIds(Set.copyOf(ids));
    }

    private Result generateStandouts(User seeker, LocalDate date) {
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
                    scoredCandidate.reason()));
        }

        standoutStorage.saveStandouts(seeker.getId(), standouts, date);
        return Result.of(standouts, candidates.size(), false);
    }

    private ScoredCandidate scoreCandidate(User seeker, User candidate) {
        double distanceKm;
        if (seeker.hasLocationSet() && candidate.hasLocationSet()) {
            distanceKm = GeoUtils.distanceKm(seeker.getLat(), seeker.getLon(), candidate.getLat(), candidate.getLon());
        } else {
            distanceKm = -1;
        }
        double distanceScore = distanceKm >= 0 && seeker.getMaxDistanceKm() > 0
                ? Math.max(0, 1.0 - (distanceKm / seeker.getMaxDistanceKm()))
                : 0.5;

        double ageScore = calculator.calculateAgeScore(seeker, candidate);

        InterestMatcher.MatchResult interests =
                InterestMatcher.compare(seeker.getInterests(), candidate.getInterests());
        double interestScore = calculator.calculateInterestScore(seeker, candidate);
        double lifestyleScore = calculator.calculateLifestyleScore(seeker, candidate);
        double completenessScore = profileService.calculate(candidate).score() / 100.0;
        double activityScore = calculator.calculateActivityScore(candidate);

        double composite = distanceScore * config.algorithm().standoutDistanceWeight()
                + ageScore * config.algorithm().standoutAgeWeight()
                + interestScore * config.algorithm().standoutInterestWeight()
                + lifestyleScore * config.algorithm().standoutLifestyleWeight()
                + completenessScore * config.algorithm().standoutCompletenessWeight()
                + activityScore * config.algorithm().standoutActivityWeight();

        int score = (int) Math.round(composite * 100);
        String reason = generateStandoutReason(seeker, candidate, interests, distanceKm, lifestyleScore);

        return new ScoredCandidate(candidate, score, reason);
    }

    private String generateStandoutReason(
            User seeker, User candidate, InterestMatcher.MatchResult interests, double distanceKm, double lifestyle) {
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
        Set<UUID> recent = new HashSet<>();
        for (int i = 1; i <= config.algorithm().standoutDiversityDays(); i++) {
            standoutStorage.getStandouts(seekerId, today.minusDays(i)).stream()
                    .map(Standout::standoutUserId)
                    .forEach(recent::add);
        }
        return Set.copyOf(recent);
    }

    private record ScoredCandidate(User user, int score, String reason) {}
}
