package datingapp.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Service to select and manage daily "wild card" picks. Provides serendipity by showing users
 * profiles outside their normal criteria.
 */
public class DailyPickService {

  private final UserStorage userStorage;
  private final LikeStorage likeStorage;
  private final BlockStorage blockStorage;
  private final DailyPickStorage dailyPickStorage;
  private final AppConfig config;

  /**
   * Represents a daily pick for a user.
   *
   * @param user the picked user
   * @param date the date of this pick
   * @param reason why this user was selected
   * @param alreadySeen whether this pick has been viewed before today
   */
  public record DailyPick(User user, LocalDate date, String reason, boolean alreadySeen) {}

  public DailyPickService(
      UserStorage userStorage,
      LikeStorage likeStorage,
      BlockStorage blockStorage,
      DailyPickStorage dailyPickStorage,
      AppConfig config) {
    this.userStorage = Objects.requireNonNull(userStorage);
    this.likeStorage = Objects.requireNonNull(likeStorage);
    this.blockStorage = Objects.requireNonNull(blockStorage);
    this.dailyPickStorage = Objects.requireNonNull(dailyPickStorage);
    this.config = Objects.requireNonNull(config);
  }

  /**
   * Get the daily pick for a user. Deterministic based on date. Returns empty if no valid
   * candidates or pick was already swiped.
   *
   * @param seeker the user requesting their daily pick
   * @return the daily pick, or empty if none available
   */
  public Optional<DailyPick> getDailyPick(User seeker) {
    LocalDate today = LocalDate.now(config.userTimeZone());

    // 1. Get all active users except self, blocked, and already interacted
    List<User> candidates =
        userStorage.findActive().stream()
            .filter(u -> !u.getId().equals(seeker.getId()))
            .filter(u -> !blockStorage.isBlocked(seeker.getId(), u.getId()))
            .filter(u -> !likeStorage.exists(seeker.getId(), u.getId()))
            .toList();

    if (candidates.isEmpty()) {
      return Optional.empty();
    }

    // 2. Use date + seeker ID as deterministic seed
    long seed = today.toEpochDay() + seeker.getId().hashCode();
    Random random = new Random(seed);

    // 3. Pick one candidate deterministically
    User picked = candidates.get(random.nextInt(candidates.size()));

    // 4. Generate a reason
    String reason = generateReason(seeker, picked, random);

    // 5. Check if already viewed
    boolean alreadySeen = dailyPickStorage.hasViewed(seeker.getId(), today);

    return Optional.of(new DailyPick(picked, today, reason, alreadySeen));
  }

  /** Generate a contextual reason for why this user was picked. */
  private String generateReason(User seeker, User picked, Random random) {
    List<String> reasons = new ArrayList<>();

    // Distance-based
    double distance =
        GeoUtils.distanceKm(
            seeker.getLat(), seeker.getLon(),
            picked.getLat(), picked.getLon());
    if (distance < 5) {
      reasons.add("Lives nearby!");
    } else if (distance < 10) {
      reasons.add("Close enough for coffee!");
    }

    // Age-based
    int ageDiff = Math.abs(seeker.getAge() - picked.getAge());
    if (ageDiff <= 2) {
      reasons.add("Similar age");
    } else if (ageDiff <= 5) {
      reasons.add("Age-appropriate match");
    }

    // Lifestyle matches
    if (seeker.getLookingFor() != null && seeker.getLookingFor() == picked.getLookingFor()) {
      reasons.add("Looking for the same thing");
    }

    if (seeker.getWantsKids() != null && seeker.getWantsKids() == picked.getWantsKids()) {
      reasons.add("Same stance on kids");
    }

    if (seeker.getDrinking() != null && seeker.getDrinking() == picked.getDrinking()) {
      reasons.add("Compatible drinking habits");
    }

    if (seeker.getSmoking() != null && seeker.getSmoking() == picked.getSmoking()) {
      reasons.add("Compatible smoking habits");
    }

    // Interest overlap
    long sharedInterests =
        seeker.getInterests().stream().filter(picked.getInterests()::contains).count();
    if (sharedInterests >= 3) {
      reasons.add("Many shared interests!");
    } else if (sharedInterests >= 1) {
      reasons.add("Some shared interests");
    }

    // Random fun reasons (always available as fallback)
    reasons.add("Our algorithm thinks you might click!");
    reasons.add("Something different today!");
    reasons.add("Expand your horizons!");
    reasons.add("Why not give them a chance?");
    reasons.add("Could be a pleasant surprise!");

    // Pick one reason randomly (but deterministically based on user seed)
    return reasons.get(random.nextInt(reasons.size()));
  }

  /**
   * Check if user has seen their daily pick today.
   *
   * @param userId the user to check
   * @return true if they've viewed their pick today
   */
  public boolean hasViewedDailyPick(UUID userId) {
    LocalDate today = LocalDate.now(config.userTimeZone());
    return dailyPickStorage.hasViewed(userId, today);
  }

  /**
   * Mark the daily pick as viewed.
   *
   * @param userId the user who viewed their pick
   */
  public void markDailyPickViewed(UUID userId) {
    LocalDate today = LocalDate.now(config.userTimeZone());
    dailyPickStorage.markViewed(userId, today);
  }
}
