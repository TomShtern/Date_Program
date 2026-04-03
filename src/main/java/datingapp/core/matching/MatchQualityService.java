package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.CandidateFinder.GeoUtils;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.storage.InteractionStorage;
import datingapp.core.storage.UserStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchQualityService {

    private static final Logger logger = LoggerFactory.getLogger(MatchQualityService.class);

    private final UserStorage userStorage;
    private final InteractionStorage interactionStorage;
    private final AppConfig config;
    private final CompatibilityCalculator calculator;
    private final StarThresholdPolicy starThresholdPolicy;
    private final int nearbyDistanceKm;
    private final int closeDistanceKm;
    private static final String LABEL_EXCELLENT = "Excellent Match";
    private static final String LABEL_GREAT = "Great Match";
    private static final String LABEL_GOOD = "Good Match";
    private static final String LABEL_FAIR = "Fair Match";
    private static final String LABEL_LOW = "Low Compatibility";
    private static final int SUMMARY_MAX_LENGTH = 40;
    private static final int SUMMARY_TRUNCATE_LENGTH = 37;
    private static final int HIGHLIGHT_MAX_COUNT = 5;
    private static final int AGE_SIMILAR_YEARS = 2;
    private static final int QUICK_MUTUAL_INTEREST_HOURS = 24;

    public static record StarThresholdPolicy(
            int excellentThreshold, int greatThreshold, int goodThreshold, int fairThreshold) {
        public static StarThresholdPolicy from(AppConfig.AlgorithmConfig algorithm) {
            return new StarThresholdPolicy(
                    algorithm.starExcellentThreshold(),
                    algorithm.starGreatThreshold(),
                    algorithm.starGoodThreshold(),
                    algorithm.starFairThreshold());
        }
    }

    /**
     * Immutable record representing the quality/compatibility of a match. Computed
     * from one user's
     * perspective (scores may differ slightly between perspectives).
     */
    public static record MatchQuality(
            String matchId,
            UUID perspectiveUserId, // Whose perspective (for directional metrics)
            UUID otherUserId,
            Instant computedAt,

            // === Individual Scores (0.0 - 1.0) ===
            double distanceScore,
            double ageScore,
            double interestScore,
            double lifestyleScore,
            double paceScore,
            double responseScore, // How quickly mutual like happened

            // === Raw Data ===
            double distanceKm,
            int ageDifference,
            List<String> sharedInterests,
            List<String> lifestyleMatches, // e.g., "Both want kids someday"
            Duration timeBetweenLikes,

            // === Aggregates ===
            String paceSyncLevel, // e.g., "Perfect Sync", "Good Sync", etc.
            int compatibilityScore, // 0-100
            List<String> highlights, // Human-readable highlights
            StarThresholdPolicy starThresholdPolicy) {
        public MatchQuality {
            Objects.requireNonNull(matchId, "matchId cannot be null");
            Objects.requireNonNull(perspectiveUserId, "perspectiveUserId cannot be null");
            Objects.requireNonNull(otherUserId, "otherUserId cannot be null");
            Objects.requireNonNull(computedAt, "computedAt cannot be null");

            // Validate scores are 0.0-1.0
            validateScore(distanceScore, "distanceScore");
            validateScore(ageScore, "ageScore");
            validateScore(interestScore, "interestScore");
            validateScore(lifestyleScore, "lifestyleScore");
            validateScore(paceScore, "paceScore");
            validateScore(responseScore, "responseScore");

            // Validate compatibility is 0-100
            if (compatibilityScore < 0 || compatibilityScore > 100) {
                throw new IllegalArgumentException("compatibilityScore must be 0-100, got: " + compatibilityScore);
            }

            if (distanceKm < -1) {
                throw new IllegalArgumentException("distanceKm cannot be less than -1");
            }
            if (ageDifference < 0) {
                throw new IllegalArgumentException("ageDifference cannot be negative");
            }
            Objects.requireNonNull(timeBetweenLikes, "timeBetweenLikes cannot be null");
            Objects.requireNonNull(paceSyncLevel, "paceSyncLevel cannot be null");
            Objects.requireNonNull(starThresholdPolicy, "starThresholdPolicy cannot be null");

            // Defensive copies
            sharedInterests = sharedInterests == null ? List.of() : List.copyOf(sharedInterests);
            lifestyleMatches = lifestyleMatches == null ? List.of() : List.copyOf(lifestyleMatches);
            highlights = highlights == null ? List.of() : List.copyOf(highlights);
        }

        private static void validateScore(double score, String name) {
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException(name + " must be 0.0-1.0, got: " + score);
            }
        }

        /** Get star rating (1-5 stars based on compatibility). */
        public int getStarRating() {
            if (compatibilityScore >= starThresholdPolicy.excellentThreshold()) {
                return 5;
            }
            if (compatibilityScore >= starThresholdPolicy.greatThreshold()) {
                return 4;
            }
            if (compatibilityScore >= starThresholdPolicy.goodThreshold()) {
                return 3;
            }
            if (compatibilityScore >= starThresholdPolicy.fairThreshold()) {
                return 2;
            }
            return 1;
        }

        /** Get compatibility label. */
        public String getCompatibilityLabel() {
            if (compatibilityScore >= starThresholdPolicy.excellentThreshold()) {
                return LABEL_EXCELLENT;
            }
            if (compatibilityScore >= starThresholdPolicy.greatThreshold()) {
                return LABEL_GREAT;
            }
            if (compatibilityScore >= starThresholdPolicy.goodThreshold()) {
                return LABEL_GOOD;
            }
            if (compatibilityScore >= starThresholdPolicy.fairThreshold()) {
                return LABEL_FAIR;
            }
            return LABEL_LOW;
        }

        /** Render star icons for display. */
        public String getStarDisplay() {
            return "⭐".repeat(getStarRating());
        }

        /** Get a formatted display of the compatibility score. */
        public String getCompatibilityDisplay() {
            return compatibilityScore + "%";
        }

        /** Get a short summary for list views. */
        public String getShortSummary() {
            if (!highlights.isEmpty()) {
                String first = highlights.getFirst();
                return first.length() > SUMMARY_MAX_LENGTH
                        ? first.substring(0, SUMMARY_TRUNCATE_LENGTH) + "..."
                        : first;
            }
            return getCompatibilityLabel();
        }
    }

    // === Constructor ===

    public MatchQualityService(UserStorage userStorage, InteractionStorage interactionStorage, AppConfig config) {
        this(userStorage, interactionStorage, config, new DefaultCompatibilityCalculator(config));
    }

    public MatchQualityService(
            UserStorage userStorage,
            InteractionStorage interactionStorage,
            AppConfig config,
            CompatibilityCalculator calculator) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.calculator = Objects.requireNonNull(calculator, "calculator cannot be null");
        this.starThresholdPolicy = StarThresholdPolicy.from(this.config.algorithm());
        this.nearbyDistanceKm = this.config.algorithm().nearbyDistanceKm();
        this.closeDistanceKm = this.config.algorithm().closeDistanceKm();
    }

    // === Public API ===

    /**
     * Compute match quality from one user's perspective.
     *
     * @param match             The match to compute quality for
     * @param perspectiveUserId The user whose perspective we're computing from
     * @return Computed match quality
     */
    public Optional<MatchQuality> computeQuality(Match match, UUID perspectiveUserId) {
        Optional<Participants> participants = loadParticipants(match, perspectiveUserId);
        if (participants.isEmpty()) {
            return Optional.empty();
        }
        MatchComputation computation = computeMatchComputation(participants.get());
        int compatibilityScore = calculateCompatibilityScore(computation);
        List<String> highlights = generateHighlights(computation);

        return Optional.of(new MatchQuality(
                match.getId(),
                perspectiveUserId,
                participants.get().otherUserId(),
                AppClock.now(),
                computation.distanceScore(),
                computation.ageScore(),
                computation.interestScore(),
                computation.lifestyleScore(),
                computation.paceScore(),
                computation.responseScore(),
                computation.distanceKm(),
                computation.ageDifference(),
                computation.sharedInterests(),
                computation.lifestyleMatches(),
                computation.timeBetweenLikes(),
                computation.paceSyncLevel(),
                compatibilityScore,
                highlights,
                starThresholdPolicy));
    }

    private Optional<Participants> loadParticipants(Match match, UUID perspectiveUserId) {
        UUID otherUserId = match.getOtherUser(perspectiveUserId);
        User me = userStorage.get(perspectiveUserId).orElse(null);
        User them = userStorage.get(otherUserId).orElse(null);

        if (me == null || them == null) {
            logger.warn("computeQuality: user not found — perspective={} other={}", perspectiveUserId, otherUserId);
            return Optional.empty();
        }

        return Optional.of(new Participants(me, them, otherUserId));
    }

    private MatchComputation computeMatchComputation(Participants participants) {
        User me = participants.me();
        User them = participants.them();

        double distanceKm = calculateDistanceKm(me, them);
        double distanceScore =
                distanceKm >= 0 ? calculator.calculateDistanceScore(distanceKm, me.getMaxDistanceKm()) : 0.5;

        Optional<Integer> meAge = me.getAge(config.safety().userTimeZone());
        Optional<Integer> themAge = them.getAge(config.safety().userTimeZone());
        boolean comparableAge = meAge.isPresent() && themAge.isPresent();
        int ageDiff = comparableAge ? Math.abs(meAge.get() - themAge.get()) : 0;
        double ageScore = comparableAge ? calculator.calculateAgeScore(me, them) : 0.5;

        InterestMatcher.MatchResult interestMatch = InterestMatcher.compare(me.getInterests(), them.getInterests());
        double interestScore = calculator.calculateInterestScore(me, them);
        List<String> sharedInterests = InterestMatcher.formatAsList(interestMatch.shared());
        String sharedInterestSummary = sharedInterests.size() > 1
                ? InterestMatcher.formatSharedInterests(
                        interestMatch.shared(), config.matching().sharedInterestsPreviewCount())
                : null;

        List<String> lifestyleMatches = findLifestyleMatches(me, them);
        double lifestyleScore = calculator.calculateLifestyleScore(me, them);

        Duration timeBetweenLikes = calculateTimeBetweenLikes(me.getId(), them.getId());
        double responseScore = calculator.calculateResponseScore(timeBetweenLikes);

        double paceScore = calculator.calculatePaceScore(me.getPacePreferences(), them.getPacePreferences());

        return new MatchComputation(
                distanceKm,
                distanceScore,
                ageDiff,
                comparableAge,
                ageScore,
                sharedInterests,
                sharedInterestSummary,
                interestScore,
                lifestyleMatches,
                lifestyleScore,
                timeBetweenLikes,
                responseScore,
                paceScore,
                getPaceSyncLevel(paceScore));
    }

    private double calculateDistanceKm(User me, User them) {
        if (!me.hasLocationSet() || !them.hasLocationSet()) {
            return -1;
        }
        return GeoUtils.distanceKm(me.getLat(), me.getLon(), them.getLat(), them.getLon());
    }

    private int calculateCompatibilityScore(MatchComputation computation) {
        double totalWeight = config.matching().distanceWeight()
                + config.matching().ageWeight()
                + config.matching().interestWeight()
                + config.matching().lifestyleWeight()
                + config.matching().paceWeight()
                + config.matching().responseWeight();

        double weightedScore = computation.distanceScore() * config.matching().distanceWeight()
                + computation.ageScore() * config.matching().ageWeight()
                + computation.interestScore() * config.matching().interestWeight()
                + computation.lifestyleScore() * config.matching().lifestyleWeight()
                + computation.paceScore() * config.matching().paceWeight()
                + computation.responseScore() * config.matching().responseWeight();

        double normalizedScore = totalWeight > 0 ? (weightedScore / totalWeight) : 0.0;
        return (int) Math.round(normalizedScore * 100);
    }

    // === Score Calculation Methods ===

    private String getPaceSyncLevel(double score) {
        if (score >= 0.95) { // PACE_SYNC_PERFECT
            return "Perfect Sync";
        }
        if (score >= 0.8) { // PACE_SYNC_GOOD
            return "Good Sync";
        }
        if (score >= 0.6) { // PACE_SYNC_FAIR
            return "Fair Sync";
        }
        if (score >= 0.4) { // PACE_SYNC_LAG
            return "Pace Lag";
        }
        return "Mismatched Pace";
    }

    private List<String> findLifestyleMatches(User me, User them) {
        List<String> matches = new ArrayList<>();
        addSmokingHighlight(me, them, matches);
        addDrinkingHighlight(me, them, matches);
        addKidsHighlight(me, them, matches);
        addRelationshipGoalsHighlight(me, them, matches);
        return matches;
    }

    private void addSmokingHighlight(User me, User them, List<String> matches) {
        if (me.getSmoking() == null || me.getSmoking() != them.getSmoking()) {
            return;
        }
        if (me.getSmoking() == Lifestyle.Smoking.NEVER) {
            matches.add("Both non-smokers");
        } else if (me.getSmoking() == Lifestyle.Smoking.SOMETIMES) {
            matches.add("Both occasional smokers");
        }
    }

    private void addDrinkingHighlight(User me, User them, List<String> matches) {
        if (me.getDrinking() == null || me.getDrinking() != them.getDrinking()) {
            return;
        }
        if (me.getDrinking() == Lifestyle.Drinking.NEVER) {
            matches.add("Neither drinks");
        } else if (me.getDrinking() == Lifestyle.Drinking.SOCIALLY) {
            matches.add("Both social drinkers");
        }
    }

    private void addKidsHighlight(User me, User them, List<String> matches) {
        if (me.getWantsKids() == null || them.getWantsKids() == null) {
            return;
        }
        if (me.getWantsKids() == them.getWantsKids()) {
            matches.add("Same stance on kids");
        } else if (LifestyleMatcher.areKidsStancesCompatible(me.getWantsKids(), them.getWantsKids())) {
            matches.add("Compatible on kids");
        }
    }

    private void addRelationshipGoalsHighlight(User me, User them, List<String> matches) {
        if (me.getLookingFor() != null && me.getLookingFor() == them.getLookingFor()) {
            matches.add(
                    "Both looking for " + me.getLookingFor().getDisplayName().toLowerCase(Locale.ROOT));
        }
    }

    private Duration calculateTimeBetweenLikes(UUID userId, UUID otherId) {
        // Get the two likes that created this match
        Optional<Like> myLike = interactionStorage.getLike(userId, otherId);
        Optional<Like> theirLike = interactionStorage.getLike(otherId, userId);

        if (myLike.isEmpty() || theirLike.isEmpty()) {
            return Duration.ZERO; // Shouldn't happen, but handle gracefully
        }

        Instant first = myLike.get().createdAt().isBefore(theirLike.get().createdAt())
                ? myLike.get().createdAt()
                : theirLike.get().createdAt();
        Instant second = myLike.get().createdAt().isAfter(theirLike.get().createdAt())
                ? myLike.get().createdAt()
                : theirLike.get().createdAt();

        return Duration.between(first, second);
    }

    public int calculatePaceCompatibility(PacePreferences first, PacePreferences second) {
        if (first == null || second == null || !first.isComplete() || !second.isComplete()) {
            return -1;
        }
        return (int) Math.round(calculator.calculatePaceScore(first, second) * 100);
    }

    // === Highlight Generation ===

    private List<String> generateHighlights(MatchComputation computation) {
        List<String> highlights = new ArrayList<>();
        addDistanceHighlight(highlights, computation.distanceKm());
        addInterestHighlight(highlights, computation.sharedInterests(), computation.sharedInterestSummary());
        highlights.addAll(computation.lifestyleMatches());
        addPaceHighlight(highlights, computation.paceScore());
        addResponseHighlight(highlights, computation.timeBetweenLikes());
        if (computation.comparableAge()) {
            addAgeHighlight(highlights, computation.ageDifference());
        }
        if (highlights.size() > HIGHLIGHT_MAX_COUNT) {
            highlights = new ArrayList<>(highlights.subList(0, HIGHLIGHT_MAX_COUNT));
        }
        return highlights;
    }

    private void addDistanceHighlight(List<String> highlights, double distanceKm) {
        if (distanceKm >= 0 && distanceKm < nearbyDistanceKm) {
            highlights.add(String.format("Lives nearby (%.1f km away)", distanceKm));
        } else if (distanceKm >= 0 && distanceKm < closeDistanceKm) {
            highlights.add(String.format("%.0f km away", distanceKm));
        }
    }

    private void addInterestHighlight(
            List<String> highlights, List<String> sharedInterests, String sharedInterestSummary) {
        if (sharedInterests.isEmpty()) {
            return;
        }
        if (sharedInterests.size() == 1) {
            highlights.add("You both enjoy " + sharedInterests.getFirst());
        } else {
            highlights.add("You share "
                    + sharedInterests.size()
                    + " interests: "
                    + Objects.requireNonNull(sharedInterestSummary, "sharedInterestSummary cannot be null"));
        }
    }

    private void addPaceHighlight(List<String> highlights, double paceScore) {
        if (paceScore >= 0.95) { // PACE_SYNC_PERFECT
            highlights.add("Total Pace Sync! ⚡");
        } else if (paceScore >= 0.8) { // PACE_SYNC_GOOD
            highlights.add("Great communication sync");
        }
    }

    private void addResponseHighlight(List<String> highlights, Duration timeBetween) {
        if (timeBetween != null && !timeBetween.isZero() && timeBetween.toHours() < QUICK_MUTUAL_INTEREST_HOURS) {
            highlights.add("Quick mutual interest!");
        }
    }

    private void addAgeHighlight(List<String> highlights, int ageDiff) {
        if (ageDiff <= AGE_SIMILAR_YEARS) {
            highlights.add("Similar age");
        }
    }

    /** Checks if a pace score is considered low compatibility. */
    public boolean isLowPaceCompatibility(int score) {
        return score >= 0 && score < config.algorithm().paceCompatibilityThreshold();
    }

    private record Participants(User me, User them, UUID otherUserId) {}

    private record MatchComputation(
            double distanceKm,
            double distanceScore,
            int ageDifference,
            boolean comparableAge,
            double ageScore,
            List<String> sharedInterests,
            String sharedInterestSummary,
            double interestScore,
            List<String> lifestyleMatches,
            double lifestyleScore,
            Duration timeBetweenLikes,
            double responseScore,
            double paceScore,
            String paceSyncLevel) {}
}
