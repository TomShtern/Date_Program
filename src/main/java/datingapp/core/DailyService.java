package datingapp.core;

import datingapp.core.CandidateFinder.GeoUtils;
import datingapp.core.UserInteractions.BlockStorage;
import datingapp.core.UserInteractions.LikeStorage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** Consolidated daily limit and daily pick workflows. */
public class DailyService {

    private final User.Storage userStorage;
    private final LikeStorage likeStorage;
    private final BlockStorage blockStorage;
    private final DailyPickStorage dailyPickStorage;
    private final AppConfig config;

    public DailyService(LikeStorage likeStorage, AppConfig config) {
        this(null, likeStorage, null, null, config);
    }

    public DailyService(
            User.Storage userStorage,
            LikeStorage likeStorage,
            BlockStorage blockStorage,
            DailyPickStorage dailyPickStorage,
            AppConfig config) {
        this.userStorage = userStorage;
        this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
        this.blockStorage = blockStorage;
        this.dailyPickStorage = dailyPickStorage;
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

        List<User> candidates = userStorage.findActive().stream()
                .filter(u -> !u.getId().equals(seeker.getId()))
                .filter(u -> !blockStorage.isBlocked(seeker.getId(), u.getId()))
                .filter(u -> !likeStorage.exists(seeker.getId(), u.getId()))
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        long seed = today.toEpochDay() + seeker.getId().hashCode();
        Random random = new Random(seed);
        User picked = candidates.get(random.nextInt(candidates.size()));
        String reason = generateReason(seeker, picked, random);
        boolean alreadySeen = dailyPickStorage.hasViewed(seeker.getId(), today);

        return Optional.of(new DailyPick(picked, today, reason, alreadySeen));
    }

    /** Check whether user has viewed today's daily pick. */
    public boolean hasViewedDailyPick(UUID userId) {
        ensureDailyPickDependencies();
        return dailyPickStorage.hasViewed(userId, getToday());
    }

    /** Mark today's daily pick as viewed for a user. */
    public void markDailyPickViewed(UUID userId) {
        ensureDailyPickDependencies();
        dailyPickStorage.markViewed(userId, getToday());
    }

    private void ensureDailyPickDependencies() {
        if (userStorage == null || blockStorage == null || dailyPickStorage == null) {
            throw new IllegalStateException("Daily pick dependencies are not configured");
        }
    }

    private String generateReason(User seeker, User picked, Random random) {
        List<String> reasons = new ArrayList<>();

        double distance = GeoUtils.distanceKm(seeker.getLat(), seeker.getLon(), picked.getLat(), picked.getLon());
        if (distance < 5) {
            reasons.add("Lives nearby!");
        } else if (distance < 10) {
            reasons.add("Close enough for coffee!");
        }

        int ageDiff = Math.abs(seeker.getAge() - picked.getAge());
        if (ageDiff <= 2) {
            reasons.add("Similar age");
        } else if (ageDiff <= 5) {
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
        if (sharedInterests >= 3) {
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

        public boolean hasUnlimitedLikes() {
            return likesRemaining < 0;
        }

        public boolean hasUnlimitedPasses() {
            return passesRemaining < 0;
        }
    }

    /** Daily pick payload. */
    public record DailyPick(User user, LocalDate date, String reason, boolean alreadySeen) {}

    // ========== STORAGE INTERFACE ==========

    /**
     * Storage interface for tracking when users view their daily picks.
     */
    public interface DailyPickStorage {

        /**
         * Mark that a user has viewed their daily pick for a specific date.
         *
         * @param userId the user who viewed the pick
         * @param date the date of the pick
         */
        void markViewed(java.util.UUID userId, LocalDate date);

        /**
         * Check if a user has already viewed their daily pick for a date.
         *
         * @param userId the user to check
         * @param date the date to check
         * @return true if the user has viewed their pick for this date
         */
        boolean hasViewed(java.util.UUID userId, LocalDate date);

        /**
         * Remove view records older than a specified date. Used for cleanup/maintenance.
         *
         * @param before delete records before this date (exclusive)
         * @return number of records deleted
         */
        int cleanup(LocalDate before);
    }
}
