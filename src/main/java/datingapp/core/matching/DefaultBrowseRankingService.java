package datingapp.core.matching;

import datingapp.core.AppConfig;
import datingapp.core.matching.CandidateFinder.GeoUtils;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Default recommendation-ranked browse ordering.
 *
 * <p>Candidate filtering remains owned by {@link CandidateFinder}. This service only reorders the
 * already-eligible candidates for presentation.
 */
public final class DefaultBrowseRankingService implements BrowseRankingService {

    private static final double UNKNOWN_DISTANCE_SCORE = 0.5;
    private static final Comparator<ScoredCandidate> RANK_ORDER = Comparator.comparingDouble(ScoredCandidate::score)
            .reversed()
            .thenComparing(Comparator.comparingDouble(ScoredCandidate::completenessScore)
                    .reversed())
            .thenComparing(
                    Comparator.comparingDouble(ScoredCandidate::activityScore).reversed())
            .thenComparing(
                    Comparator.comparing(ScoredCandidate::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .thenComparing(scoredCandidate -> scoredCandidate.candidate().getId());

    private final CompatibilityCalculator calculator;
    private final ProfileService profileService;
    private final AppConfig config;

    public DefaultBrowseRankingService(
            CompatibilityCalculator calculator, ProfileService profileService, AppConfig config) {
        this.calculator = Objects.requireNonNull(calculator, "calculator cannot be null");
        this.profileService = Objects.requireNonNull(profileService, "profileService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    @Override
    public List<User> rankCandidates(User seeker, List<User> candidates) {
        if (seeker == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .filter(Objects::nonNull)
                .map(candidate -> scoreCandidate(seeker, candidate))
                .sorted(RANK_ORDER)
                .map(ScoredCandidate::candidate)
                .toList();
    }

    private ScoredCandidate scoreCandidate(User seeker, User candidate) {
        double distanceScore = calculateDistanceScore(seeker, candidate);
        double ageScore = calculator.calculateAgeScore(seeker, candidate);
        double interestScore = calculator.calculateInterestScore(seeker, candidate);
        double lifestyleScore = calculator.calculateLifestyleScore(seeker, candidate);
        double paceScore = calculator.calculatePaceScore(seeker.getPacePreferences(), candidate.getPacePreferences());
        double completenessScore = profileService.calculate(candidate).score() / 100.0;
        double activityScore = calculator.calculateActivityScore(candidate);

        WeightedScore weightedScore = WeightedScore.empty()
                .add(distanceScore, config.matching().distanceWeight())
                .add(ageScore, config.matching().ageWeight())
                .add(interestScore, config.matching().interestWeight())
                .add(lifestyleScore, config.matching().lifestyleWeight())
                .add(paceScore, config.matching().paceWeight())
                .add(completenessScore, config.algorithm().standoutCompletenessWeight())
                .add(activityScore, config.algorithm().standoutActivityWeight());

        return new ScoredCandidate(
                candidate, weightedScore.normalized(), completenessScore, activityScore, candidate.getUpdatedAt());
    }

    private double calculateDistanceScore(User seeker, User candidate) {
        if (!seeker.hasLocationSet() || !candidate.hasLocationSet() || seeker.getMaxDistanceKm() <= 0) {
            return UNKNOWN_DISTANCE_SCORE;
        }
        double distanceKm =
                GeoUtils.distanceKm(seeker.getLat(), seeker.getLon(), candidate.getLat(), candidate.getLon());
        return calculator.calculateDistanceScore(distanceKm, seeker.getMaxDistanceKm());
    }

    private record ScoredCandidate(
            User candidate,
            double score,
            double completenessScore,
            double activityScore,
            java.time.Instant updatedAt) {}
}
