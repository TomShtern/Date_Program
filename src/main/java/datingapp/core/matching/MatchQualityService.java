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

    // ── Match quality thresholds (inlined from deleted ScoringConstants) ──
    private static final int STAR_EXCELLENT_THRESHOLD = 90;
    private static final int STAR_GREAT_THRESHOLD = 75;
    private static final int STAR_GOOD_THRESHOLD = 60;
    private static final int STAR_FAIR_THRESHOLD = 40;
    private static final String LABEL_EXCELLENT = "Excellent Match";
    private static final String LABEL_GREAT = "Great Match";
    private static final String LABEL_GOOD = "Good Match";
    private static final String LABEL_FAIR = "Fair Match";
    private static final String LABEL_LOW = "Low Compatibility";
    private static final int SUMMARY_MAX_LENGTH = 40;
    private static final int SUMMARY_TRUNCATE_LENGTH = 37;
    private static final int HIGHLIGHT_MAX_COUNT = 5;
    private static final int VERY_CLOSE_DISTANCE_KM = 1;
    private static final int NEARBY_DISTANCE_KM = 5;
    private static final int MID_DISTANCE_KM = 15;
    private static final int AGE_SIMILAR_YEARS = 2;
    private static final int QUICK_MUTUAL_INTEREST_HOURS = 24;
    private static final double NEUTRAL_SCORE = 0.5;
    private static final double INTEREST_MISSING_SCORE = 0.3;
    private static final double PACE_SYNC_PERFECT = 0.95;
    private static final double PACE_SYNC_GOOD = 0.8;
    private static final double PACE_SYNC_FAIR = 0.6;
    private static final double PACE_SYNC_LAG = 0.4;
    private static final double RESPONSE_SCORE_EXCELLENT = 1.0;
    private static final double RESPONSE_SCORE_GREAT = 0.9;
    private static final double RESPONSE_SCORE_GOOD = 0.7;
    private static final double RESPONSE_SCORE_OK = 0.5;
    private static final double RESPONSE_SCORE_LOW = 0.3;
    private static final double RESPONSE_SCORE_VERY_LOW = 0.1;
    private static final int WILDCARD_SCORE = 20;
    private static final int PACE_SCORE_EXACT = 25;
    private static final int PACE_SCORE_CLOSE = 15;
    private static final int PACE_SCORE_FAR = 5;

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
            List<String> highlights // Human-readable highlights
            ) {
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
            if (compatibilityScore >= STAR_EXCELLENT_THRESHOLD) {
                return 5;
            }
            if (compatibilityScore >= STAR_GREAT_THRESHOLD) {
                return 4;
            }
            if (compatibilityScore >= STAR_GOOD_THRESHOLD) {
                return 3;
            }
            if (compatibilityScore >= STAR_FAIR_THRESHOLD) {
                return 2;
            }
            return 1;
        }

        /** Get compatibility label. */
        public String getCompatibilityLabel() {
            if (compatibilityScore >= STAR_EXCELLENT_THRESHOLD) {
                return LABEL_EXCELLENT;
            }
            if (compatibilityScore >= STAR_GREAT_THRESHOLD) {
                return LABEL_GREAT;
            }
            if (compatibilityScore >= STAR_GOOD_THRESHOLD) {
                return LABEL_GOOD;
            }
            if (compatibilityScore >= STAR_FAIR_THRESHOLD) {
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
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
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
        UUID otherUserId = match.getOtherUser(perspectiveUserId);

        User me = userStorage.get(perspectiveUserId).orElse(null);
        User them = userStorage.get(otherUserId).orElse(null);

        if (me == null || them == null) {
            logger.warn("computeQuality: user not found — perspective={} other={}", perspectiveUserId, otherUserId);
            return Optional.empty();
        }

        // === Calculate Individual Scores ===

        // Distance Score
        double distanceKm;
        if (me.hasLocationSet() && them.hasLocationSet()) {
            distanceKm = GeoUtils.distanceKm(me.getLat(), me.getLon(), them.getLat(), them.getLon());
        } else {
            distanceKm = -1;
        }
        double distanceScore =
                distanceKm >= 0 ? calculateDistanceScore(distanceKm, me.getMaxDistanceKm()) : NEUTRAL_SCORE;

        // Age Score
        int ageDiff = Math.abs(me.getAge(config.safety().userTimeZone())
                - them.getAge(config.safety().userTimeZone()));
        double ageScore = calculateAgeScore(ageDiff, me, them);

        // Interest Score
        InterestMatcher.MatchResult interestMatch = InterestMatcher.compare(me.getInterests(), them.getInterests());
        double interestScore = calculateInterestScore(interestMatch, me, them);
        List<String> sharedInterests = InterestMatcher.formatAsList(interestMatch.shared());

        // Lifestyle Score
        List<String> lifestyleMatches = findLifestyleMatches(me, them);
        double lifestyleScore = calculateLifestyleScore(me, them);

        // Response Score
        Duration timeBetweenLikes = calculateTimeBetweenLikes(perspectiveUserId, otherUserId);
        double responseScore = calculateResponseScore(timeBetweenLikes);

        // Pace Score (Phase 2)
        double paceScore = calculatePaceScore(me.getPacePreferences(), them.getPacePreferences());
        String paceSyncLevel = getPaceSyncLevel(paceScore);

        // === Calculate Overall Score ===
        double totalWeight = config.matching().distanceWeight()
                + config.matching().ageWeight()
                + config.matching().interestWeight()
                + config.matching().lifestyleWeight()
                + config.matching().paceWeight()
                + config.matching().responseWeight();

        double weightedScore = distanceScore * config.matching().distanceWeight()
                + ageScore * config.matching().ageWeight()
                + interestScore * config.matching().interestWeight()
                + lifestyleScore * config.matching().lifestyleWeight()
                + paceScore * config.matching().paceWeight()
                + responseScore * config.matching().responseWeight();

        double normalizedScore = totalWeight > 0 ? (weightedScore / totalWeight) : 0.0;
        int compatibilityScore = (int) Math.round(normalizedScore * 100);

        // === Generate Highlights ===
        List<String> highlights = generateHighlights(
                me, them, distanceKm, sharedInterests, lifestyleMatches, paceScore, timeBetweenLikes);

        return Optional.of(new MatchQuality(
                match.getId(),
                perspectiveUserId,
                otherUserId,
                AppClock.now(),
                distanceScore,
                ageScore,
                interestScore,
                lifestyleScore,
                paceScore,
                responseScore,
                distanceKm,
                ageDiff,
                sharedInterests,
                lifestyleMatches,
                timeBetweenLikes,
                paceSyncLevel,
                compatibilityScore,
                highlights));
    }

    // === Score Calculation Methods ===

    private double calculateDistanceScore(double distanceKm, int maxDistanceKm) {
        if (distanceKm <= VERY_CLOSE_DISTANCE_KM) {
            return 1.0; // Very close
        }
        if (distanceKm >= maxDistanceKm) {
            return 0.0; // At limit
        }

        // Linear decay from 1.0 to 0.0
        return 1.0 - (distanceKm / maxDistanceKm);
    }

    private double calculateAgeScore(int ageDiff, User me, User them) {
        return CompatibilityScoring.ageScore(ageDiff, me, them, AGE_SIMILAR_YEARS);
    }

    private double calculateInterestScore(InterestMatcher.MatchResult match, User me, User them) {
        return CompatibilityScoring.interestScore(
                me.getInterests(), them.getInterests(), match.overlapRatio(), NEUTRAL_SCORE, INTEREST_MISSING_SCORE);
    }

    private double calculateLifestyleScore(User me, User them) {
        return CompatibilityScoring.lifestyleScore(me, them, NEUTRAL_SCORE);
    }

    private String getPaceSyncLevel(double score) {
        if (score >= PACE_SYNC_PERFECT) {
            return "Perfect Sync";
        }
        if (score >= PACE_SYNC_GOOD) {
            return "Good Sync";
        }
        if (score >= PACE_SYNC_FAIR) {
            return "Fair Sync";
        }
        if (score >= PACE_SYNC_LAG) {
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

    private double calculateResponseScore(Duration timeBetween) {
        if (timeBetween == null || timeBetween.isZero()) {
            return NEUTRAL_SCORE; // Unknown
        }

        long hours = timeBetween.toHours();

        // Within excellent threshold = excellent
        if (hours < config.algorithm().responseTimeExcellentHours()) {
            return RESPONSE_SCORE_EXCELLENT;
        }
        // Within great threshold = great
        if (hours < config.algorithm().responseTimeGreatHours()) {
            return RESPONSE_SCORE_GREAT;
        }
        // Within good threshold = good
        if (hours < config.algorithm().responseTimeGoodHours()) {
            return RESPONSE_SCORE_GOOD;
        }
        // Within a week = okay
        if (hours < config.algorithm().responseTimeWeekHours()) {
            return RESPONSE_SCORE_OK;
        }
        // Within a month = low
        if (hours < config.algorithm().responseTimeMonthHours()) {
            return RESPONSE_SCORE_LOW;
        }
        // Longer = very low
        return RESPONSE_SCORE_VERY_LOW;
    }

    // === Highlight Generation ===

    private List<String> generateHighlights(
            User me,
            User them,
            double distanceKm,
            List<String> sharedInterests,
            List<String> lifestyleMatches,
            double paceScore,
            Duration timeBetween) {
        List<String> highlights = new ArrayList<>();
        addDistanceHighlight(highlights, distanceKm);
        addInterestHighlight(highlights, me, them, sharedInterests);
        highlights.addAll(lifestyleMatches);
        addPaceHighlight(highlights, paceScore);
        addResponseHighlight(highlights, timeBetween);
        addAgeHighlight(
                highlights,
                Math.abs(me.getAge(config.safety().userTimeZone())
                        - them.getAge(config.safety().userTimeZone())));
        if (highlights.size() > HIGHLIGHT_MAX_COUNT) {
            highlights = new ArrayList<>(highlights.subList(0, HIGHLIGHT_MAX_COUNT));
        }
        return highlights;
    }

    private void addDistanceHighlight(List<String> highlights, double distanceKm) {
        if (distanceKm >= 0 && distanceKm < NEARBY_DISTANCE_KM) {
            highlights.add(String.format("Lives nearby (%.1f km away)", distanceKm));
        } else if (distanceKm >= 0 && distanceKm < MID_DISTANCE_KM) {
            highlights.add(String.format("%.0f km away", distanceKm));
        }
    }

    private void addInterestHighlight(List<String> highlights, User me, User them, List<String> sharedInterests) {
        if (sharedInterests.isEmpty()) {
            return;
        }
        if (sharedInterests.size() == 1) {
            highlights.add("You both enjoy " + sharedInterests.getFirst());
        } else {
            InterestMatcher.MatchResult result = InterestMatcher.compare(me.getInterests(), them.getInterests());
            highlights.add("You share "
                    + sharedInterests.size()
                    + " interests: "
                    + InterestMatcher.formatSharedInterests(result.shared()));
        }
    }

    private void addPaceHighlight(List<String> highlights, double paceScore) {
        if (paceScore >= PACE_SYNC_PERFECT) {
            highlights.add("Total Pace Sync! ⚡");
        } else if (paceScore >= PACE_SYNC_GOOD) {
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

    // === Pace Compatibility Methods (formerly PaceCompatibilityService) ===

    /**
     * Calculates a compatibility score (0-100) between two users' pace
     * MatchPreferences.
     *
     * @param a first user's preferences
     * @param b second user's preferences
     * @return score from 0 to 100, or -1 if either is null/incomplete
     */
    public int calculatePaceCompatibility(PacePreferences a, PacePreferences b) {
        if (a == null || b == null || !a.isComplete() || !b.isComplete()) {
            return -1; // Compatibility unknown
        }

        int score = 0;

        // Dimension 1: Messaging Frequency
        score += dimensionScore(a.messagingFrequency(), b.messagingFrequency(), false);

        // Dimension 2: Time to First Date
        score += dimensionScore(a.timeToFirstDate(), b.timeToFirstDate(), false);

        // Dimension 3: Communication Style (Wildcard: MIX_OF_EVERYTHING)
        boolean commStyleWildcard = isCommunicationStyleWildcard(a.communicationStyle())
                || isCommunicationStyleWildcard(b.communicationStyle());
        score += dimensionScore(a.communicationStyle(), b.communicationStyle(), commStyleWildcard);

        // Dimension 4: Depth Preference (Wildcard: DEPENDS_ON_VIBE)
        boolean depthWildcard =
                isDepthPreferenceWildcard(a.depthPreference()) || isDepthPreferenceWildcard(b.depthPreference());
        score += dimensionScore(a.depthPreference(), b.depthPreference(), depthWildcard);

        return score;
    }

    /**
     * Calculates a pace compatibility score as a normalized double (0.0-1.0).
     *
     * @param a first user's preferences
     * @param b second user's preferences
     * @return score from 0.0 to 1.0, or 0.5 (neutral) if unknown
     */
    public double calculatePaceScore(PacePreferences a, PacePreferences b) {
        int comp = calculatePaceCompatibility(a, b);
        if (comp == -1) {
            return NEUTRAL_SCORE; // Neutral
        }
        return comp / 100.0;
    }

    private int dimensionScore(Enum<?> a, Enum<?> b, boolean hasWildcard) {
        if (hasWildcard) {
            return WILDCARD_SCORE;
        }

        int distance = Math.abs(a.ordinal() - b.ordinal());
        return switch (distance) {
            case 0 -> PACE_SCORE_EXACT; // Perfect match
            case 1 -> PACE_SCORE_CLOSE; // Close enough
            default -> PACE_SCORE_FAR; // Quite different
        };
    }

    private boolean isCommunicationStyleWildcard(PacePreferences.CommunicationStyle style) {
        return style == PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING;
    }

    private boolean isDepthPreferenceWildcard(PacePreferences.DepthPreference preference) {
        return preference == PacePreferences.DepthPreference.DEPENDS_ON_VIBE;
    }

    /** Checks if a pace score is considered low compatibility. */
    public boolean isLowPaceCompatibility(int score) {
        return score >= 0 && score < config.algorithm().paceCompatibilityThreshold();
    }

    /** Gets the warning message for low pace compatibility. */
    public String getLowPaceCompatibilityWarning() {
        return "Your pacing styles differ significantly. Worth discussing early!";
    }
}
