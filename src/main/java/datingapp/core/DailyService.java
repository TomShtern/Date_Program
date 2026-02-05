package datingapp.core;

import datingapp.core.CandidateFinder.GeoUtils;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.UserStorage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
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
    private final CandidateFinder candidateFinder;
    private final AppConfig config;

    /** In-memory tracking of daily pick views (userId -> set of dates viewed). */
    private final Map<UUID, Set<LocalDate>> dailyPickViews = new ConcurrentHashMap<>();

    public DailyService(LikeStorage likeStorage, AppConfig config) {
        this(null, likeStorage, null, null, config);
    }

    public DailyService(
            UserStorage userStorage,
            LikeStorage likeStorage,
            BlockStorage blockStorage,
            CandidateFinder candidateFinder,
            AppConfig config) {
        this.userStorage = userStorage;
        this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
        this.blockStorage = blockStorage;
        this.candidateFinder = candidateFinder;
        this.config = Objects.requireNonNull(config, "config cannot be null");
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
        LocalDate today = LocalDate.now(config.userTimeZone());

        int likesUsed = likeStorage.countLikesToday(userId, startOfDay);
        int passesUsed = likeStorage.countPassesToday(userId, startOfDay);

        int likesRemaining = remainingFor(config.hasUnlimitedLikes(), config.dailyLikeLimit(), likesUsed);
        int passesRemaining = remainingFor(config.hasUnlimitedPasses(), config.dailyPassLimit(), passesUsed);

        return new DailyStatus(likesUsed, likesRemaining, passesUsed, passesRemaining, today, resetTime);
    }

    /** Time remaining until next daily reset (midnight local time). */
    public Duration getTimeUntilReset() {
        Instant now = Instant.now();
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
        // This filters by: self, active state, blocks, already-interacted, mutual gender,
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

        // Deterministic random selection based on date + user ID
        long seed = today.toEpochDay() + seeker.getId().hashCode();
        Random random = new Random(seed);
        User picked = candidates.get(random.nextInt(candidates.size()));
        String reason = generateReason(seeker, picked, random);
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
        Set<LocalDate> viewedDates = dailyPickViews.get(userId);
        return viewedDates != null && viewedDates.contains(date);
    }

    /** Internal: Mark daily pick as viewed for a specific date. */
    private void markDailyPickViewed(UUID userId, LocalDate date) {
        dailyPickViews
                .computeIfAbsent(userId, _ -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(date);
    }

    /** Cleanup old daily pick view records (for maintenance). */
    public int cleanupOldDailyPickViews(LocalDate before) {
        int removed = 0;
        for (Set<LocalDate> dates : dailyPickViews.values()) {
            long count = dates.stream().filter(date -> date.isBefore(before)).count();
            dates.removeIf(date -> date.isBefore(before));
            removed += Math.toIntExact(count);
        }
        return removed;
    }

    private void ensureDailyPickDependencies() {
        if (userStorage == null || blockStorage == null) {
            throw new IllegalStateException("Daily pick dependencies are not configured");
        }
    }

    private String generateReason(User seeker, User picked, Random random) {
        List<String> reasons = new ArrayList<>();

        double distance = GeoUtils.distanceKm(seeker.getLat(), seeker.getLon(), picked.getLat(), picked.getLon());
        if (distance < config.nearbyDistanceKm()) {
            reasons.add("Lives nearby!");
        } else if (distance < config.closeDistanceKm()) {
            reasons.add("Close enough for coffee!");
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

        reasons.add("Our algorithm thinks you might click!");
        reasons.add("Something different today!");
        reasons.add("Expand your horizons!");
        reasons.add("Why not give them a chance?");
        reasons.add("Could be a pleasant surprise!");

        return reasons.get(random.nextInt(reasons.size()));
    }

    private int remainingFor(boolean unlimited, int limit, int used) {
        return unlimited ? -1 : Math.max(0, limit - used);
    }

    private boolean canPerform(boolean unlimited, int limit, int used) {
        return unlimited || used < limit;
    }

    private Instant getStartOfToday() {
        ZoneId zone = config.userTimeZone();
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    private Instant getResetTime() {
        ZoneId zone = config.userTimeZone();
        return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
    }

    private LocalDate getToday() {
        return LocalDate.now(config.userTimeZone());
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
