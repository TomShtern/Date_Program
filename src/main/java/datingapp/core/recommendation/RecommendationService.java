package datingapp.core.recommendation;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.CandidateFinder.GeoUtils;
import datingapp.core.matching.MatchQualityService.InterestMatcher;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.ProfileService;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RecommendationService {

    private static final int MAX_STANDOUTS = 10;
    private static final int DIVERSITY_DAYS = 3;

    private final UserStorage userStorage;
    private final InteractionStorage interactionStorage;
    private final TrustSafetyStorage trustSafetyStorage;
    private final AnalyticsStorage analyticsStorage;
    private final CandidateFinder candidateFinder;
    private final Standout.Storage standoutStorage;
    private final ProfileService profileService;
    private final AppConfig config;
    private final Clock clock;

    private final Map<String, UUID> cachedDailyPicks = new ConcurrentHashMap<>();

    public RecommendationService(InteractionStorage interactionStorage, AppConfig config) {
        this(null, interactionStorage, null, null, null, null, null, config, null);
    }

    public RecommendationService(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            AnalyticsStorage analyticsStorage,
            CandidateFinder candidateFinder,
            AppConfig config) {
        this(userStorage, interactionStorage, null, analyticsStorage, candidateFinder, null, null, config, null);
    }

    public RecommendationService(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            AnalyticsStorage analyticsStorage,
            CandidateFinder candidateFinder,
            AppConfig config,
            Clock clock) {
        this(
                userStorage,
                interactionStorage,
                trustSafetyStorage,
                analyticsStorage,
                candidateFinder,
                null,
                null,
                config,
                clock);
    }

    public RecommendationService(
            UserStorage userStorage,
            Standout.Storage standoutStorage,
            CandidateFinder candidateFinder,
            ProfileService profileService,
            AppConfig config) {
        this(
                Objects.requireNonNull(userStorage, "userStorage cannot be null"),
                null,
                null,
                null,
                Objects.requireNonNull(candidateFinder, "candidateFinder cannot be null"),
                Objects.requireNonNull(standoutStorage, "standoutStorage cannot be null"),
                Objects.requireNonNull(profileService, "profileService cannot be null"),
                Objects.requireNonNull(config, "config cannot be null"),
                null);
    }

    public RecommendationService(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            TrustSafetyStorage trustSafetyStorage,
            AnalyticsStorage analyticsStorage,
            CandidateFinder candidateFinder,
            Standout.Storage standoutStorage,
            ProfileService profileService,
            AppConfig config,
            Clock clock) {
        this.userStorage = userStorage;
        this.interactionStorage = interactionStorage;
        this.trustSafetyStorage = trustSafetyStorage;
        this.analyticsStorage = analyticsStorage;
        this.candidateFinder = candidateFinder;
        this.standoutStorage = standoutStorage;
        this.profileService = profileService;
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.clock = clock != null ? clock : AppClock.clock();
    }

    /** Whether the user can perform a like action today. */
    public boolean canLike(UUID userId) {
        ensureDailyLimitDependencies();
        Instant startOfDay = getStartOfToday();
        int likesUsed = interactionStorage.countLikesToday(userId, startOfDay);
        return canPerform(config.hasUnlimitedLikes(), config.dailyLikeLimit(), likesUsed);
    }

    /** Whether the user can perform a pass action today. */
    public boolean canPass(UUID userId) {
        ensureDailyLimitDependencies();
        Instant startOfDay = getStartOfToday();
        int passesUsed = interactionStorage.countPassesToday(userId, startOfDay);
        return canPerform(config.hasUnlimitedPasses(), config.dailyPassLimit(), passesUsed);
    }

    /** Current daily status including counts and time to reset. */
    public DailyStatus getStatus(UUID userId) {
        ensureDailyLimitDependencies();
        Instant startOfDay = getStartOfToday();
        Instant resetTime = getResetTime();
        LocalDate today = getToday();

        int likesUsed = interactionStorage.countLikesToday(userId, startOfDay);
        int passesUsed = interactionStorage.countPassesToday(userId, startOfDay);

        int likesRemaining = remainingFor(config.hasUnlimitedLikes(), config.dailyLikeLimit(), likesUsed);
        int passesRemaining = remainingFor(config.hasUnlimitedPasses(), config.dailyPassLimit(), passesUsed);

        return new DailyStatus(likesUsed, likesRemaining, passesUsed, passesRemaining, today, resetTime);
    }

    /** Time remaining until next daily reset (midnight local time). */
    public Duration getTimeUntilReset() {
        Instant now = Instant.now(clock);
        Instant resetTime = getResetTime();
        return Duration.between(now, resetTime);
    }

    /** Format duration in HH:mm:ss. */
    public static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    /** Get the daily pick for a user if available. */
    public Optional<DailyPick> getDailyPick(User seeker) {
        ensureDailyPickDependencies();
        LocalDate today = getToday();

        // Use CandidateFinder to get filtered candidates
        // This filters by: self, active state, blocks, already-interacted, mutual
        // gender,
        // mutual age, distance, dealbreakers
        List<User> candidates;
        if (candidateFinder != null) {
            candidates = candidateFinder.findCandidatesForUser(seeker);
        } else {
            // Fallback for tests/configurations without CandidateFinder
            List<User> allActive = userStorage.findActive();
            Set<UUID> alreadyInteracted = new HashSet<>(interactionStorage.getLikedOrPassedUserIds(seeker.getId()));
            candidates = allActive.stream()
                    .filter(u -> !u.getId().equals(seeker.getId()))
                    .filter(u -> !isBlockedFallback(seeker.getId(), u.getId()))
                    .filter(u -> !alreadyInteracted.contains(u.getId()))
                    .toList();
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Deterministic random selection based on date + user ID (TS-006: atomic cache
        // update)
        long seed = today.toEpochDay() + seeker.getId().hashCode();
        Random pickRandom = new Random(seed);

        String cacheKey = seeker.getId() + "_" + today;

        // Use computeIfAbsent for atomic check-and-populate (eliminates race condition)
        UUID pickedId = cachedDailyPicks.computeIfAbsent(
                cacheKey,
                _ -> candidates.get(pickRandom.nextInt(candidates.size())).getId());

        // Find the cached user in current candidates (may have been filtered out since
        // caching)
        User picked = candidates.stream()
                .filter(candidate -> candidate.getId().equals(pickedId))
                .findFirst()
                .orElse(null);

        if (picked == null) {
            // Cached pick no longer in candidate list (e.g., user was blocked since
            // caching)
            cachedDailyPicks.remove(cacheKey);
            picked = candidates.get(pickRandom.nextInt(candidates.size()));
            cachedDailyPicks.put(cacheKey, picked.getId());
        }

        long reasonSeed =
                seed ^ picked.getId().getMostSignificantBits() ^ picked.getId().getLeastSignificantBits();
        Random reasonRandom = new Random(reasonSeed);
        String reason = generateReason(seeker, picked, reasonRandom);
        boolean alreadySeen = hasViewedDailyPick(seeker.getId(), today);

        return Optional.of(new DailyPick(picked, today, reason, alreadySeen));
    }

    /** Check whether user has viewed today's daily pick. */
    public boolean hasViewedDailyPick(UUID userId) {
        ensureDailyPickDependencies();
        return hasViewedDailyPick(userId, getToday());
    }

    /** Mark today's daily pick as viewed for a user. */
    public void markDailyPickViewed(UUID userId) {
        ensureDailyPickDependencies();
        markDailyPickViewed(userId, getToday());
    }

    /** Internal: Check if user has viewed daily pick for a specific date. */
    private boolean hasViewedDailyPick(UUID userId, LocalDate date) {
        return analyticsStorage != null && analyticsStorage.isDailyPickViewed(userId, date);
    }

    /** Internal: Mark daily pick as viewed for a specific date. */
    private void markDailyPickViewed(UUID userId, LocalDate date) {
        if (analyticsStorage != null) {
            analyticsStorage.markDailyPickAsViewed(userId, date);
        }
    }

    /** Cleanup old daily pick view records (for maintenance). */
    public int cleanupOldDailyPickViews(LocalDate before) {
        int removed = 0;
        if (analyticsStorage != null) {
            removed = analyticsStorage.deleteDailyPickViewsOlderThan(before);
        }
        String todaySuffix = "_" + LocalDate.now(clock);
        cachedDailyPicks.entrySet().removeIf(entry -> !entry.getKey().endsWith(todaySuffix));
        return removed;
    }

    private void ensureDailyPickDependencies() {
        if (userStorage == null || analyticsStorage == null || interactionStorage == null) {
            throw new IllegalStateException("Daily pick dependencies are not configured");
        }
    }

    private void ensureDailyLimitDependencies() {
        if (interactionStorage == null) {
            throw new IllegalStateException("Daily limit dependencies are not configured");
        }
    }

    private String generateReason(User seeker, User picked, Random random) {
        List<String> reasons = new ArrayList<>();

        if (seeker.hasLocationSet() && picked.hasLocationSet()) {
            double distance = GeoUtils.distanceKm(seeker.getLat(), seeker.getLon(), picked.getLat(), picked.getLon());
            if (distance < config.nearbyDistanceKm()) {
                reasons.add("Lives nearby!");
            } else if (distance < config.closeDistanceKm()) {
                reasons.add("Close enough for coffee!");
            }
        }

        int ageDiff = Math.abs(seeker.getAge() - picked.getAge());
        if (ageDiff <= config.similarAgeDiff()) {
            reasons.add("Similar age");
        } else if (ageDiff <= config.compatibleAgeDiff()) {
            reasons.add("Age-appropriate match");
        }

        if (sameNonNull(seeker.getLookingFor(), picked.getLookingFor())) {
            reasons.add("Looking for the same thing");
        }

        if (sameNonNull(seeker.getWantsKids(), picked.getWantsKids())) {
            reasons.add("Same stance on kids");
        }

        if (sameNonNull(seeker.getDrinking(), picked.getDrinking())) {
            reasons.add("Compatible drinking habits");
        }

        if (sameNonNull(seeker.getSmoking(), picked.getSmoking())) {
            reasons.add("Compatible smoking habits");
        }

        long sharedInterests = seeker.getInterests().stream()
                .filter(picked.getInterests()::contains)
                .count();
        if (sharedInterests >= config.minSharedInterests()) {
            reasons.add("Many shared interests!");
        } else if (sharedInterests >= 1) {
            reasons.add("Some shared interests");
        }

        if (reasons.isEmpty()) {
            reasons.add("Our algorithm thinks you might click!");
            reasons.add("Something different today!");
            reasons.add("Expand your horizons!");
            reasons.add("Why not give them a chance?");
            reasons.add("Could be a pleasant surprise!");
        }

        return reasons.get(random.nextInt(reasons.size()));
    }

    private int remainingFor(boolean unlimited, int limit, int used) {
        return unlimited ? -1 : Math.max(0, limit - used);
    }

    private boolean canPerform(boolean unlimited, int limit, int used) {
        return unlimited || used < limit;
    }

    private Instant getStartOfToday() {
        ZoneId zone = clock.getZone();
        return LocalDate.now(clock).atStartOfDay(zone).toInstant();
    }

    private Instant getResetTime() {
        ZoneId zone = clock.getZone();
        return LocalDate.now(clock).plusDays(1).atStartOfDay(zone).toInstant();
    }

    private LocalDate getToday() {
        return LocalDate.now(clock);
    }

    private static <T> boolean sameNonNull(T left, T right) {
        return left != null && left.equals(right);
    }

    private boolean isBlockedFallback(UUID seekerId, UUID candidateId) {
        return trustSafetyStorage != null && trustSafetyStorage.isBlocked(seekerId, candidateId);
    }

    /** Get today's standouts for a user. Returns cached if available. */
    public Result getStandouts(User seeker) {
        ensureStandoutDependencies();
        LocalDate today = AppClock.today(config.userTimeZone());

        List<Standout> cached = standoutStorage.getStandouts(seeker.getId(), today);
        if (!cached.isEmpty()) {
            return Result.of(cached, cached.size(), true);
        }

        return generateStandouts(seeker, today);
    }

    /** Mark a standout as interacted after like/pass. */
    public void markInteracted(UUID seekerId, UUID standoutUserId) {
        ensureStandoutDependencies();
        LocalDate today = AppClock.today(config.userTimeZone());
        standoutStorage.markInteracted(seekerId, standoutUserId, today);
    }

    /** Resolve standout user IDs to User objects. */
    public Map<UUID, User> resolveUsers(List<Standout> standouts) {
        ensureStandoutDependencies();
        List<UUID> ids = standouts.stream().map(Standout::standoutUserId).toList();
        return userStorage.findByIds(new HashSet<>(ids));
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
                .sorted(Comparator.comparingInt(ScoredCandidate::score).reversed())
                .limit(MAX_STANDOUTS)
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

        int ageDiff = Math.abs(seeker.getAge() - candidate.getAge());
        double avgAgeRange =
                (seeker.getMaxAge() - seeker.getMinAge() + candidate.getMaxAge() - candidate.getMinAge()) / 2.0;
        double ageScore = avgAgeRange > 0 ? Math.max(0, 1.0 - (ageDiff / avgAgeRange)) : 0.5;

        InterestMatcher.MatchResult interests =
                InterestMatcher.compare(seeker.getInterests(), candidate.getInterests());
        double interestScore = calculateInterestScore(interests, seeker, candidate);
        double lifestyleScore = calculateLifestyleScore(seeker, candidate);
        double completenessScore = profileService.calculate(candidate).score() / 100.0;
        double activityScore = calculateActivityScore(candidate);

        double composite = distanceScore * config.standoutDistanceWeight()
                + ageScore * config.standoutAgeWeight()
                + interestScore * config.standoutInterestWeight()
                + lifestyleScore * config.standoutLifestyleWeight()
                + completenessScore * config.standoutCompletenessWeight()
                + activityScore * config.standoutActivityWeight();

        int score = (int) Math.round(composite * 100);
        String reason = generateStandoutReason(seeker, candidate, interests, distanceKm, lifestyleScore);

        return new ScoredCandidate(candidate, score, reason);
    }

    private double calculateInterestScore(InterestMatcher.MatchResult match, User seeker, User candidate) {
        Set<Interest> seekerInterests = seeker.getInterests();
        Set<Interest> candidateInterests = candidate.getInterests();

        if ((seekerInterests == null || seekerInterests.isEmpty())
                && (candidateInterests == null || candidateInterests.isEmpty())) {
            return 0.5;
        }
        if (seekerInterests == null
                || seekerInterests.isEmpty()
                || candidateInterests == null
                || candidateInterests.isEmpty()) {
            return 0.3;
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

    private boolean areKidsCompatible(Lifestyle.WantsKids left, Lifestyle.WantsKids right) {
        if (left == right) {
            return true;
        }
        if (left == Lifestyle.WantsKids.OPEN || right == Lifestyle.WantsKids.OPEN) {
            return true;
        }
        return (left == Lifestyle.WantsKids.SOMEDAY && right == Lifestyle.WantsKids.HAS_KIDS)
                || (right == Lifestyle.WantsKids.SOMEDAY && left == Lifestyle.WantsKids.HAS_KIDS);
    }

    private double calculateActivityScore(User candidate) {
        if (candidate.getUpdatedAt() == null) {
            return 0.5;
        }
        Duration sinceUpdate = Duration.between(candidate.getUpdatedAt(), AppClock.now());
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

    private String generateStandoutReason(
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
            for (Standout standout : past) {
                recent.add(standout.standoutUserId());
            }
        }
        return recent;
    }

    private void ensureStandoutDependencies() {
        if (userStorage == null || standoutStorage == null || candidateFinder == null || profileService == null) {
            throw new IllegalStateException("Standout dependencies are not configured");
        }
    }

    /** Daily pick payload. */
    public static record DailyPick(User user, LocalDate date, String reason, boolean alreadySeen) {

        public DailyPick {
            Objects.requireNonNull(user, "user cannot be null");
            Objects.requireNonNull(date, "date cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason cannot be blank");
            }
        }
    }

    /** Status snapshot for daily limits. */
    public record DailyStatus(
            int likesUsed, int likesRemaining, int passesUsed, int passesRemaining, LocalDate date, Instant resetsAt) {

        public DailyStatus {
            if (likesUsed < 0 || passesUsed < 0) {
                throw new IllegalArgumentException("Usage counts cannot be negative");
            }
            Objects.requireNonNull(date, "date cannot be null");
            Objects.requireNonNull(resetsAt, "resetsAt cannot be null");
        }

        public boolean hasUnlimitedLikes() {
            return likesRemaining < 0;
        }

        public boolean hasUnlimitedPasses() {
            return passesRemaining < 0;
        }
    }

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

    private static record ScoredCandidate(User user, int score, String reason) {}
}
