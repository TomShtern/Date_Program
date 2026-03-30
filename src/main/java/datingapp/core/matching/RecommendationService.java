package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.profile.ProfileService;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stable coordination facade for recommendation-related features.
 *
 * <p>It composes the daily limit, daily pick, and standout services, then adapts their
 * result types into the legacy RecommendationService records used by callers.
 */
public final class RecommendationService {

    private final DailyLimitService dailyLimitService;
    private final DailyPickService dailyPickService;
    private final StandoutService standoutService;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UserStorage userStorage;
        private InteractionStorage interactionStorage;
        private TrustSafetyStorage trustSafetyStorage;
        private AnalyticsStorage analyticsStorage;
        private CandidateFinder candidateFinder;
        private Standout.Storage standoutStorage;
        private ProfileService profileService;
        private AppConfig config;
        private Clock clock;

        public Builder userStorage(UserStorage userStorage) {
            this.userStorage = userStorage;
            return this;
        }

        public Builder interactionStorage(InteractionStorage interactionStorage) {
            this.interactionStorage = interactionStorage;
            return this;
        }

        public Builder trustSafetyStorage(TrustSafetyStorage trustSafetyStorage) {
            Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
            this.trustSafetyStorage = trustSafetyStorage;
            return this;
        }

        public Builder analyticsStorage(AnalyticsStorage analyticsStorage) {
            this.analyticsStorage = analyticsStorage;
            return this;
        }

        public Builder candidateFinder(CandidateFinder candidateFinder) {
            this.candidateFinder = candidateFinder;
            return this;
        }

        public Builder standoutStorage(Standout.Storage standoutStorage) {
            this.standoutStorage = standoutStorage;
            return this;
        }

        public Builder profileService(ProfileService profileService) {
            this.profileService = profileService;
            return this;
        }

        public Builder config(AppConfig config) {
            this.config = config;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public RecommendationService build() {
            AppConfig resolvedConfig = Objects.requireNonNull(config, "config cannot be null");
            Clock resolvedClock = clock != null ? clock : AppClock.clock();
            CandidateFinder resolvedCandidateFinder = candidateFinder;
            if (resolvedCandidateFinder == null) {
                resolvedCandidateFinder = new CandidateFinder(
                        Objects.requireNonNull(userStorage, "userStorage cannot be null"),
                        Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null"),
                        Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null"),
                        resolvedConfig.safety().userTimeZone(),
                        Duration.ofHours(resolvedConfig.matching().rematchCooldownHours()));
            }
            CompatibilityCalculator calculator = new DefaultCompatibilityCalculator(resolvedConfig, resolvedClock);
            DailyLimitService dailyLimitService =
                    new DefaultDailyLimitService(interactionStorage, resolvedConfig, resolvedClock);
            DailyPickService dailyPickService = new DefaultDailyPickService(
                    userStorage,
                    interactionStorage,
                    analyticsStorage,
                    resolvedCandidateFinder,
                    resolvedConfig,
                    resolvedClock);
            StandoutService standoutService = new DefaultStandoutService(
                    calculator,
                    userStorage,
                    resolvedCandidateFinder,
                    standoutStorage,
                    profileService,
                    resolvedConfig,
                    resolvedClock);
            return new RecommendationService(dailyLimitService, dailyPickService, standoutService);
        }
    }

    public RecommendationService(
            DailyLimitService dailyLimitService, DailyPickService dailyPickService, StandoutService standoutService) {
        this.dailyLimitService = Objects.requireNonNull(dailyLimitService, "dailyLimitService cannot be null");
        this.dailyPickService = Objects.requireNonNull(dailyPickService, "dailyPickService cannot be null");
        this.standoutService = Objects.requireNonNull(standoutService, "standoutService cannot be null");
    }

    /** Whether the user can perform a like action today. */
    public boolean canLike(UUID userId) {
        return dailyLimitService.canLike(userId);
    }

    /** Whether the user can perform a super-like action today. */
    public boolean canSuperLike(UUID userId) {
        return dailyLimitService.canSuperLike(userId);
    }

    /** Whether the user can perform a pass action today. */
    public boolean canPass(UUID userId) {
        return dailyLimitService.canPass(userId);
    }

    /** Current daily status including counts and time to reset. */
    public DailyStatus getStatus(UUID userId) {
        return toDailyStatus(dailyLimitService.getStatus(userId));
    }

    /** Time remaining until next daily reset (midnight local time). */
    public Duration getTimeUntilReset() {
        return dailyLimitService.getTimeUntilReset();
    }

    /**
     * Format duration as HH:mm:ss.
     */
    public static String formatDuration(Duration duration) {
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

    /** Get the daily pick for a user if available. */
    public Optional<DailyPick> getDailyPick(User seeker) {
        return dailyPickService.getDailyPick(seeker).map(RecommendationService::toDailyPick);
    }

    /** Check whether user has viewed today's daily pick. */
    public boolean hasViewedDailyPick(UUID userId) {
        return dailyPickService.hasViewedDailyPick(userId);
    }

    /** Mark today's daily pick as viewed for a user. */
    public void markDailyPickViewed(UUID userId) {
        dailyPickService.markDailyPickViewed(userId);
    }

    /** Cleanup old daily pick view records (for maintenance). */
    public int cleanupOldDailyPickViews(LocalDate before) {
        return dailyPickService.cleanupOldDailyPickViews(before);
    }

    /** Get today's standouts for a user. */
    public Result getStandouts(User seeker) {
        return toResult(standoutService.getStandouts(seeker));
    }

    /** Mark a standout as interacted after like/pass. */
    public void markInteracted(UUID seekerId, UUID standoutUserId) {
        standoutService.markInteracted(seekerId, standoutUserId);
    }

    /** Resolve standout user IDs to User objects. */
    public Map<UUID, User> resolveUsers(List<Standout> standouts) {
        return standoutService.resolveUsers(standouts);
    }

    // Keep legacy records for backward compatibility if needed,
    // but preferred to use the ones from the new services.
    public static record DailyPick(User user, LocalDate date, String reason, boolean alreadySeen) {
        public DailyPick {
            Objects.requireNonNull(user, "user cannot be null");
            Objects.requireNonNull(date, "date cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
        }
    }

    public static record DailyStatus(
            int likesUsed,
            int likesRemaining,
            int superLikesUsed,
            int superLikesRemaining,
            int passesUsed,
            int passesRemaining,
            LocalDate date,
            java.time.Instant resetsAt) {
        public DailyStatus {
            if (likesUsed < 0) {
                throw new IllegalArgumentException("likesUsed cannot be negative");
            }
            if (superLikesUsed < 0) {
                throw new IllegalArgumentException("superLikesUsed cannot be negative");
            }
            if (passesUsed < 0) {
                throw new IllegalArgumentException("passesUsed cannot be negative");
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

    private static DailyStatus toDailyStatus(DailyLimitService.DailyStatus status) {
        return new DailyStatus(
                status.likesUsed(),
                status.likesRemaining(),
                status.superLikesUsed(),
                status.superLikesRemaining(),
                status.passesUsed(),
                status.passesRemaining(),
                status.date(),
                status.resetsAt());
    }

    private static DailyPick toDailyPick(DailyPickService.DailyPick pick) {
        return new DailyPick(pick.user(), pick.date(), pick.reason(), pick.alreadySeen());
    }

    private static Result toResult(StandoutService.Result result) {
        return new Result(result.standouts(), result.totalCandidates(), result.fromCache(), result.message());
    }
}
