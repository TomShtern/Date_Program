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

/**
 * Default implementation of {@link DailyLimitService}.
 */
public final class DefaultDailyLimitService implements DailyLimitService {

    private final InteractionStorage interactionStorage;
    private final AppConfig config;
    private final Clock clock;

    public DefaultDailyLimitService(InteractionStorage interactionStorage, AppConfig config) {
        this(interactionStorage, config, AppClock.clock());
    }

    public DefaultDailyLimitService(InteractionStorage interactionStorage, AppConfig config, Clock clock) {
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public boolean canLike(UUID userId) {
        Instant startOfDay = getStartOfToday();
        int likesUsed = interactionStorage.countLikesToday(userId, startOfDay);
        return canPerform(config.hasUnlimitedLikes(), config.matching().dailyLikeLimit(), likesUsed);
    }

    @Override
    public boolean canPass(UUID userId) {
        Instant startOfDay = getStartOfToday();
        int passesUsed = interactionStorage.countPassesToday(userId, startOfDay);
        return canPerform(config.hasUnlimitedPasses(), config.matching().dailyPassLimit(), passesUsed);
    }

    @Override
    public DailyStatus getStatus(UUID userId) {
        Instant startOfDay = getStartOfToday();
        Instant resetTime = getResetTime();
        LocalDate today = getToday();

        int likesUsed = interactionStorage.countLikesToday(userId, startOfDay);
        int passesUsed = interactionStorage.countPassesToday(userId, startOfDay);

        int likesRemaining =
                remainingFor(config.hasUnlimitedLikes(), config.matching().dailyLikeLimit(), likesUsed);
        int passesRemaining =
                remainingFor(config.hasUnlimitedPasses(), config.matching().dailyPassLimit(), passesUsed);

        return new DailyStatus(likesUsed, likesRemaining, passesUsed, passesRemaining, today, resetTime);
    }

    @Override
    public Duration getTimeUntilReset() {
        Instant now = Instant.now(clock);
        Instant resetTime = getResetTime();
        return Duration.between(now, resetTime);
    }

    @Override
    public String formatDuration(Duration duration) {
        if (duration == null) {
            return "00:00:00";
        }

        boolean negative = duration.isNegative();
        Duration normalized = negative ? duration.abs() : duration;
        long hours = normalized.toHours();
        int minutes = normalized.toMinutesPart();
        int seconds = normalized.toSecondsPart();

        String formatted = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        return negative ? "-" + formatted : formatted;
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
}
