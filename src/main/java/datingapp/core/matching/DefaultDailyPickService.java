package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.matching.CandidateFinder.GeoUtils;
import datingapp.core.model.User;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Default implementation of {@link DailyPickService}.
 */
public final class DefaultDailyPickService implements DailyPickService {

    private final AnalyticsStorage analyticsStorage;
    private final CandidateFinder candidateFinder;
    private final AppConfig config;
    private final Clock clock;

    public DefaultDailyPickService(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            AnalyticsStorage analyticsStorage,
            CandidateFinder candidateFinder,
            AppConfig config) {
        this(userStorage, interactionStorage, analyticsStorage, candidateFinder, config, AppClock.clock());
    }

    public DefaultDailyPickService(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            AnalyticsStorage analyticsStorage,
            CandidateFinder candidateFinder,
            AppConfig config,
            Clock clock) {
        Objects.requireNonNull(userStorage, "userStorage cannot be null");
        Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.analyticsStorage = Objects.requireNonNull(analyticsStorage, "analyticsStorage cannot be null");
        this.candidateFinder = Objects.requireNonNull(candidateFinder, "candidateFinder cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public Optional<DailyPick> getDailyPick(User seeker) {
        LocalDate today = LocalDate.now(clock);

        List<User> candidates = candidateFinder.findCandidatesForUser(seeker);

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        long seed = today.toEpochDay() + seeker.getId().hashCode();
        Random pickRandom = new Random(seed);

        UUID pickedId = analyticsStorage
                .getDailyPickUser(seeker.getId(), today)
                .filter(cachedId -> candidates.stream()
                        .anyMatch(candidate -> candidate.getId().equals(cachedId)))
                .orElseGet(() -> {
                    UUID generatedPickId = candidates
                            .get(pickRandom.nextInt(candidates.size()))
                            .getId();
                    analyticsStorage.saveDailyPickUser(seeker.getId(), generatedPickId, today);
                    return generatedPickId;
                });

        User picked = candidates.stream()
                .filter(candidate -> candidate.getId().equals(pickedId))
                .findFirst()
                .orElseGet(() -> {
                    UUID generatedPickId = candidates
                            .get(pickRandom.nextInt(candidates.size()))
                            .getId();
                    analyticsStorage.saveDailyPickUser(seeker.getId(), generatedPickId, today);
                    return candidates.stream()
                            .filter(candidate -> candidate.getId().equals(generatedPickId))
                            .findFirst()
                            .orElseThrow();
                });

        long reasonSeed =
                seed ^ picked.getId().getMostSignificantBits() ^ picked.getId().getLeastSignificantBits();
        Random reasonRandom = new Random(reasonSeed);
        String reason = generateReason(seeker, picked, reasonRandom);
        boolean alreadySeen = hasViewedDailyPick(seeker.getId(), today);

        return Optional.of(new DailyPick(picked, today, reason, alreadySeen));
    }

    @Override
    public boolean hasViewedDailyPick(UUID userId) {
        return hasViewedDailyPick(userId, LocalDate.now(clock));
    }

    @Override
    public void markDailyPickViewed(UUID userId) {
        markDailyPickViewed(userId, LocalDate.now(clock));
    }

    private boolean hasViewedDailyPick(UUID userId, LocalDate date) {
        return analyticsStorage.isDailyPickViewed(userId, date);
    }

    private void markDailyPickViewed(UUID userId, LocalDate date) {
        analyticsStorage.markDailyPickAsViewed(userId, date);
    }

    @Override
    public int cleanupOldDailyPickViews(LocalDate before) {
        return analyticsStorage.deleteDailyPickViewsOlderThan(before);
    }

    private String generateReason(User seeker, User picked, Random random) {
        List<String> reasons = new ArrayList<>();
        addLocationReasons(reasons, seeker, picked);
        addAgeReasons(reasons, seeker, picked);
        addCompatibilityReasons(reasons, seeker, picked);
        addInterestReasons(reasons, seeker, picked);
        if (reasons.isEmpty()) {
            reasons.add("Our algorithm thinks you might click!");
            reasons.add("Something different today!");
            reasons.add("Expand your horizons!");
            reasons.add("Why not give them a chance?");
            reasons.add("Could be a pleasant surprise!");
        }
        return reasons.get(random.nextInt(reasons.size()));
    }

    private void addLocationReasons(List<String> reasons, User seeker, User picked) {
        if (!seeker.hasLocationSet() || !picked.hasLocationSet()) {
            return;
        }
        double distance = GeoUtils.distanceKm(seeker.getLat(), seeker.getLon(), picked.getLat(), picked.getLon());
        if (distance < config.algorithm().nearbyDistanceKm()) {
            reasons.add("Lives nearby!");
        } else if (distance < config.algorithm().closeDistanceKm()) {
            reasons.add("Close enough for coffee!");
        }
    }

    private void addAgeReasons(List<String> reasons, User seeker, User picked) {
        ZoneId tz = config.safety().userTimeZone();
        if (seeker.getAge(tz).isEmpty() || picked.getAge(tz).isEmpty()) {
            return;
        }
        int ageDiff =
                Math.abs(seeker.getAge(tz).orElseThrow() - picked.getAge(tz).orElseThrow());
        if (ageDiff <= config.algorithm().similarAgeDiff()) {
            reasons.add("Similar age");
        } else if (ageDiff <= config.algorithm().compatibleAgeDiff()) {
            reasons.add("Age-appropriate match");
        }
    }

    private void addCompatibilityReasons(List<String> reasons, User seeker, User picked) {
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
    }

    private void addInterestReasons(List<String> reasons, User seeker, User picked) {
        long sharedInterests = seeker.getInterests().stream()
                .filter(picked.getInterests()::contains)
                .count();
        if (sharedInterests >= config.matching().minSharedInterests()) {
            reasons.add("Many shared interests!");
        } else if (sharedInterests >= 1) {
            reasons.add("Some shared interests");
        }
    }

    private static <T> boolean sameNonNull(T left, T right) {
        return left != null && left.equals(right);
    }
}
