package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.storage.InteractionStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/** Manages daily interaction limits (likes, passes) and resets. */
public class DailyLimitService {

    private final InteractionStorage interactionStorage;
    private final AppConfig config;
    private final Clock clock;

    protected DailyLimitService() {
        this.interactionStorage = null;
        this.config = null;
        this.clock = null;
    }

    public DailyLimitService(InteractionStorage interactionStorage, AppConfig config) {
        this(interactionStorage, config, AppClock.clock());
    }

    public DailyLimitService(InteractionStorage interactionStorage, AppConfig config, Clock clock) {
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public boolean canLike(UUID userId) {
        Instant startOfDay = getStartOfToday();
        int likesUsed = interactionStorage.countLikesToday(userId, startOfDay);
        return canPerform(config.hasUnlimitedLikes(), config.matching().dailyLikeLimit(), likesUsed);
    }

    public boolean canSuperLike(UUID userId) {
        Instant startOfDay = getStartOfToday();
        int superLikesUsed = interactionStorage.countSuperLikesToday(userId, startOfDay);
        return canPerform(false, config.matching().dailySuperLikeLimit(), superLikesUsed);
    }

    public boolean canPass(UUID userId) {
        Instant startOfDay = getStartOfToday();
        int passesUsed = interactionStorage.countPassesToday(userId, startOfDay);
        return canPerform(config.hasUnlimitedPasses(), config.matching().dailyPassLimit(), passesUsed);
    }

    public DailyStatus getStatus(UUID userId) {
        Instant startOfDay = getStartOfToday();
        Instant resetTime = getResetTime();
        LocalDate today = getToday();
        int likesUsed = interactionStorage.countLikesToday(userId, startOfDay);
        int superLikesUsed = interactionStorage.countSuperLikesToday(userId, startOfDay);
        int passesUsed = interactionStorage.countPassesToday(userId, startOfDay);
        int likesRemaining =
                remainingFor(config.hasUnlimitedLikes(), config.matching().dailyLikeLimit(), likesUsed);
        int superLikesRemaining = remainingFor(false, config.matching().dailySuperLikeLimit(), superLikesUsed);
        int passesRemaining =
                remainingFor(config.hasUnlimitedPasses(), config.matching().dailyPassLimit(), passesUsed);
        return new DailyStatus(
                likesUsed,
                likesRemaining,
                superLikesUsed,
                superLikesRemaining,
                passesUsed,
                passesRemaining,
                today,
                resetTime);
    }

    public Duration getTimeUntilReset() {
        Instant now = clock.instant();
        Instant resetTime = getResetTime();
        return Duration.between(now, resetTime);
    }

    private int remainingFor(boolean unlimited, int limit, int used) {
        return unlimited ? -1 : Math.max(0, limit - used);
    }

    private boolean canPerform(boolean unlimited, int limit, int used) {
        return unlimited || used < limit;
    }

    private Instant getStartOfToday() {
        ZoneId zone = configuredZone();
        return getToday().atStartOfDay(zone).toInstant();
    }

    private Instant getResetTime() {
        ZoneId zone = configuredZone();
        return getToday().plusDays(1).atStartOfDay(zone).toInstant();
    }

    private LocalDate getToday() {
        return LocalDate.ofInstant(clock.instant(), configuredZone());
    }

    private ZoneId configuredZone() {
        return config.safety().userTimeZone();
    }

    public record DailyStatus(
            int likesUsed,
            int likesRemaining,
            int superLikesUsed,
            int superLikesRemaining,
            int passesUsed,
            int passesRemaining,
            LocalDate date,
            Instant resetsAt) {

        public DailyStatus {
            if (likesUsed < 0 || superLikesUsed < 0 || passesUsed < 0) {
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

        public boolean hasUnlimitedSuperLikes() {
            return superLikesRemaining < 0;
        }
    }
}
