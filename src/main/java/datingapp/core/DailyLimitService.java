package datingapp.core;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Service to track and enforce daily like/pass limits. Limits reset at midnight in the configured
 * user timezone.
 */
public class DailyLimitService {

  private final LikeStorage likeStorage;
  private final AppConfig config;

  public DailyLimitService(LikeStorage likeStorage, AppConfig config) {
    this.likeStorage = Objects.requireNonNull(likeStorage);
    this.config = Objects.requireNonNull(config);
  }

  /** Status record containing current daily limit information. */
  public record DailyStatus(
      int likesUsed,
      int likesRemaining,
      int passesUsed,
      int passesRemaining, // -1 if unlimited
      LocalDate date,
      Instant resetsAt) {
    /** Check if likes are unlimited. */
    public boolean hasUnlimitedLikes() {
      return likesRemaining < 0;
    }

    /** Check if passes are unlimited. */
    public boolean hasUnlimitedPasses() {
      return passesRemaining < 0;
    }
  }

  /** Get current daily limit status for a user. */
  public DailyStatus getStatus(UUID userId) {
    Instant startOfDay = getStartOfToday();
    Instant resetTime = getResetTime();
    LocalDate today = LocalDate.now(config.userTimeZone());

    int likesUsed = likeStorage.countLikesToday(userId, startOfDay);
    int passesUsed = likeStorage.countPassesToday(userId, startOfDay);

    int likesRemaining;
    if (config.hasUnlimitedLikes()) {
      likesRemaining = -1;
    } else {
      likesRemaining = Math.max(0, config.dailyLikeLimit() - likesUsed);
    }

    int passesRemaining;
    if (config.hasUnlimitedPasses()) {
      passesRemaining = -1;
    } else {
      passesRemaining = Math.max(0, config.dailyPassLimit() - passesUsed);
    }

    return new DailyStatus(
        likesUsed, likesRemaining, passesUsed, passesRemaining, today, resetTime);
  }

  /** Check if user can like (has remaining daily likes). */
  public boolean canLike(UUID userId) {
    if (config.hasUnlimitedLikes()) {
      return true;
    }
    Instant startOfDay = getStartOfToday();
    int likesUsed = likeStorage.countLikesToday(userId, startOfDay);
    return likesUsed < config.dailyLikeLimit();
  }

  /** Check if user can pass (has remaining daily passes). Usually unlimited (returns true). */
  public boolean canPass(UUID userId) {
    if (config.hasUnlimitedPasses()) {
      return true;
    }
    Instant startOfDay = getStartOfToday();
    int passesUsed = likeStorage.countPassesToday(userId, startOfDay);
    return passesUsed < config.dailyPassLimit();
  }

  /** Get time remaining until daily limits reset. */
  public Duration getTimeUntilReset() {
    Instant now = Instant.now();
    Instant resetTime = getResetTime();
    return Duration.between(now, resetTime);
  }

  /** Calculate start of today in user's timezone. */
  public Instant getStartOfToday() {
    ZoneId zone = config.userTimeZone();
    return LocalDate.now(zone).atStartOfDay(zone).toInstant();
  }

  /** Calculate when limits reset (midnight tomorrow in user's timezone). */
  public Instant getResetTime() {
    ZoneId zone = config.userTimeZone();
    return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
  }

  /** Format a duration in human-readable form (e.g., "4h 32m"). */
  public static String formatDuration(Duration duration) {
    long hours = duration.toHours();
    long minutes = duration.toMinutesPart();
    if (hours > 0) {
      return String.format("%dh %02dm", hours, minutes);
    } else {
      return String.format("%dm", minutes);
    }
  }
}
