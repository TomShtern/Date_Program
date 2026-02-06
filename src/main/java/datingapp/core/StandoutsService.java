package datingapp.core;

import datingapp.core.CandidateFinder.GeoUtils;
import datingapp.core.MatchQualityService.InterestMatcher;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.storage.UserStorage;
import java.time.Duration;
import java.time.Instant;
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
 * Service for managing daily standout profiles - the top 10 ranked matches.
 * Uses CandidateFinder for filtering and custom scoring for ranking.
 */
public class StandoutsService {

    private static final int MAX_STANDOUTS = 10;
    private static final int DIVERSITY_DAYS = 3;

    private final UserStorage userStorage;
    private final Standout.Storage standoutStorage;
    private final CandidateFinder candidateFinder;
    private final AppConfig config;

    public StandoutsService(
            UserStorage userStorage,
            Standout.Storage standoutStorage,
            CandidateFinder candidateFinder,
            AppConfig config) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage required");
        this.standoutStorage = Objects.requireNonNull(standoutStorage, "standoutStorage required");
        this.candidateFinder = Objects.requireNonNull(candidateFinder, "candidateFinder required");
        this.config = Objects.requireNonNull(config, "config required");
    }

    /** Get today's standouts for a user. Returns cached if available. */
    public Result getStandouts(User seeker) {
        LocalDate today = LocalDate.now(config.userTimeZone());

        // Check for cached standouts
        List<Standout> cached = standoutStorage.getStandouts(seeker.getId(), today);
        if (!cached.isEmpty()) {
            return Result.of(cached, cached.size(), true);
        }

        // Generate fresh standouts
        return generateStandouts(seeker, today);
    }

    /** Generate fresh standouts for a user. */
    private Result generateStandouts(User seeker, LocalDate date) {
        // Get candidates using existing 7-stage filter pipeline
        List<User> candidates = candidateFinder.findCandidatesForUser(seeker);

        if (candidates.isEmpty()) {
            return Result.empty("No standouts available. Try adjusting your preferences!");
        }

        // Get recent standouts for diversity filtering
        Set<UUID> recentStandoutIds = getRecentStandoutIds(seeker.getId(), date);

        // Score and rank candidates
        List<ScoredCandidate> scored = candidates.stream()
                .filter(c -> !recentStandoutIds.contains(c.getId()))
                .map(c -> scoreCandidate(seeker, c))
                .sorted(Comparator.comparingInt(ScoredCandidate::score).reversed())
                .limit(MAX_STANDOUTS)
                .toList();

        if (scored.isEmpty()) {
            return Result.empty("Check back tomorrow for fresh standouts!");
        }

        // Convert to Standout records
        List<Standout> standouts = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            ScoredCandidate sc = scored.get(i);
            standouts.add(Standout.create(seeker.getId(), sc.user.getId(), date, i + 1, sc.score, sc.reason));
        }

        // Cache standouts
        standoutStorage.saveStandouts(seeker.getId(), standouts, date);

        return Result.of(standouts, candidates.size(), false);
    }

    /** Score a candidate for standout ranking. */
    private ScoredCandidate scoreCandidate(User seeker, User candidate) {
        // 1. Distance score (20%)
        double distanceKm;
        if (seeker.hasLocationSet() && candidate.hasLocationSet()) {
            distanceKm = GeoUtils.distanceKm(seeker.getLat(), seeker.getLon(), candidate.getLat(), candidate.getLon());
        } else {
            distanceKm = -1;
        }
        double distanceScore = distanceKm >= 0 && seeker.getMaxDistanceKm() > 0
                ? Math.max(0, 1.0 - (distanceKm / seeker.getMaxDistanceKm()))
                : 0.5;

        // 2. Age score (15%)
        int ageDiff = Math.abs(seeker.getAge() - candidate.getAge());
        double avgAgeRange =
                (seeker.getMaxAge() - seeker.getMinAge() + candidate.getMaxAge() - candidate.getMinAge()) / 2.0;
        double ageScore = avgAgeRange > 0 ? Math.max(0, 1.0 - (ageDiff / avgAgeRange)) : 0.5;

        // 3. Interest score (25%)
        InterestMatcher.MatchResult interests =
                InterestMatcher.compare(seeker.getInterests(), candidate.getInterests());
        double interestScore = calculateInterestScore(interests, seeker, candidate);

        // 4. Lifestyle score (20%)
        double lifestyleScore = calculateLifestyleScore(seeker, candidate);

        // 5. Profile completeness (10%)
        double completenessScore = ProfileCompletionService.calculate(candidate).score() / 100.0;

        // 6. Activity score (10%)
        double activityScore = calculateActivityScore(candidate);

        // Compute composite score using configurable weights
        double composite = distanceScore * config.standoutDistanceWeight()
                + ageScore * config.standoutAgeWeight()
                + interestScore * config.standoutInterestWeight()
                + lifestyleScore * config.standoutLifestyleWeight()
                + completenessScore * config.standoutCompletenessWeight()
                + activityScore * config.standoutActivityWeight();

        int score = (int) Math.round(composite * 100);
        String reason = generateReason(seeker, candidate, interests, distanceKm, lifestyleScore);

        return new ScoredCandidate(candidate, score, reason);
    }

    private double calculateInterestScore(InterestMatcher.MatchResult match, User seeker, User candidate) {
        Set<Interest> seekerInterests = seeker.getInterests();
        Set<Interest> candidateInterests = candidate.getInterests();

        if ((seekerInterests == null || seekerInterests.isEmpty())
                && (candidateInterests == null || candidateInterests.isEmpty())) {
            return 0.5; // Neutral - neither has interests
        }
        if (seekerInterests == null
                || seekerInterests.isEmpty()
                || candidateInterests == null
                || candidateInterests.isEmpty()) {
            return 0.3; // Penalty for missing interests
        }
        return match.overlapRatio();
    }

    private double calculateLifestyleScore(User seeker, User candidate) {
        int total = countLifestyleFactors(seeker, candidate);
        if (total == 0) {
            return 0.5;
        }

        int matches = countLifestyleMatches(seeker, candidate);
        return (double) matches / total;
    }

    private int countLifestyleFactors(User seeker, User candidate) {
        int total = 0;
        if (seeker.getSmoking() != null && candidate.getSmoking() != null) {
            total++;
        }
        if (seeker.getDrinking() != null && candidate.getDrinking() != null) {
            total++;
        }
        if (seeker.getWantsKids() != null && candidate.getWantsKids() != null) {
            total++;
        }
        if (seeker.getLookingFor() != null && candidate.getLookingFor() != null) {
            total++;
        }
        return total;
    }

    private int countLifestyleMatches(User seeker, User candidate) {
        int matches = 0;
        if (seeker.getSmoking() != null
                && candidate.getSmoking() != null
                && seeker.getSmoking() == candidate.getSmoking()) {
            matches++;
        }
        if (seeker.getDrinking() != null
                && candidate.getDrinking() != null
                && seeker.getDrinking() == candidate.getDrinking()) {
            matches++;
        }
        if (seeker.getWantsKids() != null
                && candidate.getWantsKids() != null
                && areKidsCompatible(seeker.getWantsKids(), candidate.getWantsKids())) {
            matches++;
        }
        if (seeker.getLookingFor() != null
                && candidate.getLookingFor() != null
                && seeker.getLookingFor() == candidate.getLookingFor()) {
            matches++;
        }
        return matches;
    }

    private boolean areKidsCompatible(Lifestyle.WantsKids a, Lifestyle.WantsKids b) {
        if (a == b) {
            return true;
        }
        if (a == Lifestyle.WantsKids.OPEN || b == Lifestyle.WantsKids.OPEN) {
            return true;
        }
        return (a == Lifestyle.WantsKids.SOMEDAY && b == Lifestyle.WantsKids.HAS_KIDS)
                || (b == Lifestyle.WantsKids.SOMEDAY && a == Lifestyle.WantsKids.HAS_KIDS);
    }

    private double calculateActivityScore(User candidate) {
        if (candidate.getUpdatedAt() == null) {
            return 0.5;
        }
        Duration sinceUpdate = Duration.between(candidate.getUpdatedAt(), Instant.now());
        long hours = sinceUpdate.toHours();

        if (hours < 1) {
            return 1.0;
        }
        if (hours < 24) {
            return 0.9;
        }
        if (hours < 72) {
            return 0.7;
        }
        if (hours < 168) {
            return 0.5;
        }
        if (hours < 720) {
            return 0.3;
        }
        return 0.1;
    }

    private String generateReason(
            User seeker, User candidate, InterestMatcher.MatchResult interests, double distanceKm, double lifestyle) {
        List<String> reasons = new ArrayList<>();

        if (interests.sharedCount() >= config.minSharedInterests()) {
            reasons.add("Many shared interests");
        } else if (interests.sharedCount() >= 1) {
            reasons.add("Shared interests");
        }

        if (distanceKm >= 0 && distanceKm < config.nearbyDistanceKm()) {
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
        for (int i = 1; i <= DIVERSITY_DAYS; i++) {
            List<Standout> past = standoutStorage.getStandouts(seekerId, today.minusDays(i));
            for (Standout s : past) {
                recent.add(s.standoutUserId());
            }
        }
        return recent;
    }

    /** Mark a standout as interacted after like/pass. */
    public void markInteracted(UUID seekerId, UUID standoutUserId) {
        LocalDate today = LocalDate.now(config.userTimeZone());
        standoutStorage.markInteracted(seekerId, standoutUserId, today);
    }

    /** Resolve standout user IDs to User objects. */
    public Map<UUID, User> resolveUsers(List<Standout> standouts) {
        List<UUID> ids = standouts.stream().map(Standout::standoutUserId).toList();
        return userStorage.findByIds(new HashSet<>(ids));
    }

    /** Internal scored candidate record. */
    private static record ScoredCandidate(User user, int score, String reason) {}

    /** Result record for standouts query. */
    public static record Result(List<Standout> standouts, int totalCandidates, boolean fromCache, String message) {

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
