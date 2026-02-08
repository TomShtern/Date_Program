package datingapp.core;

import datingapp.core.CandidateFinder.GeoUtils;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.DailyPickViewStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.UserStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Consolidated daily limit and daily pick workflows. */
public class DailyService {

    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final BlockStorage blockStorage;
    private final DailyPickViewStorage dailyPickViewStorage;
    private final CandidateFinder candidateFinder;
    private final AppConfig config;
    private final Clock clock;

    private final Map<String, UUID> cachedDailyPicks = new ConcurrentHashMap<>();

    public DailyService(LikeStorage likeStorage, AppConfig config) {
        this(null, likeStorage, null, null, null, config, null);
    }

    public DailyService(
            UserStorage userStorage,
            LikeStorage likeStorage,
            BlockStorage blockStorage,
            DailyPickViewStorage dailyPickViewStorage,
            CandidateFinder candidateFinder,
            AppConfig config) {
        this(userStorage, likeStorage, blockStorage, dailyPickViewStorage, candidateFinder, config, null);
    }

    public DailyService(
            UserStorage userStorage,
            LikeStorage likeStorage,
            BlockStorage blockStorage,
            DailyPickViewStorage dailyPickViewStorage,
            CandidateFinder candidateFinder,
            AppConfig config,
            Clock clock) {
        this.userStorage = userStorage;
        this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
        this.blockStorage = blockStorage;
        this.dailyPickViewStorage = dailyPickViewStorage;
        this.candidateFinder = candidateFinder;
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.clock = clock != null ? clock : Clock.system(config.userTimeZone());
    }

    /** Whether the user can perform a like action today. */
    public boolean canLike(UUID userId) {
        Instant startOfDay = getStartOfToday();
        int likesUsed = likeStorage.countLikesToday(userId, startOfDay);
        return canPerform(config.hasUnlimitedLikes(), config.dailyLikeLimit(), likesUsed);
    }

    /** Whether the user can perform a pass action today. */
    public boolean canPass(UUID userId) {
        Instant startOfDay = getStartOfToday();
        int passesUsed = likeStorage.countPassesToday(userId, startOfDay);
        return canPerform(config.hasUnlimitedPasses(), config.dailyPassLimit(), passesUsed);
    }

    /** Current daily status including counts and time to reset. */
    public DailyStatus getStatus(UUID userId) {
        Instant startOfDay = getStartOfToday();
        Instant resetTime = getResetTime();
        LocalDate today = getToday();

        int likesUsed = likeStorage.countLikesToday(userId, startOfDay);
        int passesUsed = likeStorage.countPassesToday(userId, startOfDay);

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
            Set<UUID> alreadyInteracted = new HashSet<>(likeStorage.getLikedOrPassedUserIds(seeker.getId()));
            candidates = allActive.stream()
                    .filter(u -> !u.getId().equals(seeker.getId()))
                    .filter(u -> !blockStorage.isBlocked(seeker.getId(), u.getId()))
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
        return dailyPickViewStorage != null && dailyPickViewStorage.isViewed(userId, date);
    }

    /** Internal: Mark daily pick as viewed for a specific date. */
    private void markDailyPickViewed(UUID userId, LocalDate date) {
        if (dailyPickViewStorage != null) {
            dailyPickViewStorage.markAsViewed(userId, date);
        }
    }

    /** Cleanup old daily pick view records (for maintenance). */
    public int cleanupOldDailyPickViews(LocalDate before) {
        int removed = 0;
        if (dailyPickViewStorage != null) {
            removed = dailyPickViewStorage.deleteOlderThan(before);
        }
        String todaySuffix = "_" + LocalDate.now(clock);
        cachedDailyPicks.entrySet().removeIf(entry -> !entry.getKey().endsWith(todaySuffix));
        return removed;
    }

    private void ensureDailyPickDependencies() {
        if (userStorage == null || blockStorage == null || dailyPickViewStorage == null) {
            throw new IllegalStateException("Daily pick dependencies are not configured");
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
}
