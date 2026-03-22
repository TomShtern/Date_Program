package datingapp.core.matching;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Default implementation of CompatibilityCalculator using configured weights and business rules. */
public final class DefaultCompatibilityCalculator implements CompatibilityCalculator {

    private final AppConfig config;
    private final Clock clock;

    // Local baseline score policy constants.
    private static final double NEUTRAL_SCORE = 0.5;
    private static final double INTEREST_MISSING_SCORE = 0.3;
    private static final int AGE_SIMILAR_YEARS = 2;

    // Response Score Constants
    private static final double RESPONSE_SCORE_EXCELLENT = 1.0;
    private static final double RESPONSE_SCORE_GREAT = 0.9;
    private static final double RESPONSE_SCORE_GOOD = 0.7;
    private static final double RESPONSE_SCORE_OK = 0.5;
    private static final double RESPONSE_SCORE_LOW = 0.3;
    private static final double RESPONSE_SCORE_VERY_LOW = 0.1;

    // Pace Score Constants
    private static final int WILDCARD_SCORE = 20;
    private static final int PACE_SCORE_EXACT = 25;
    private static final int PACE_SCORE_CLOSE = 15;
    private static final int PACE_SCORE_FAR = 5;

    // Activity score thresholds owned by this calculator.
    private static final long ACTIVITY_VERY_RECENT_HOURS = 1;
    private static final long ACTIVITY_RECENT_HOURS = 24;
    private static final long ACTIVITY_MODERATE_HOURS = 72;
    private static final long ACTIVITY_WEEKLY_HOURS = 168;
    private static final long ACTIVITY_MONTHLY_HOURS = 720;

    // Activity score values owned by this calculator.
    private static final double ACTIVITY_SCORE_VERY_RECENT = 1.0;
    private static final double ACTIVITY_SCORE_RECENT = 0.9;
    private static final double ACTIVITY_SCORE_MODERATE = 0.7;
    private static final double ACTIVITY_SCORE_WEEKLY = 0.5;
    private static final double ACTIVITY_SCORE_MONTHLY = 0.3;
    private static final double ACTIVITY_SCORE_INACTIVE = 0.1;
    private static final double ACTIVITY_SCORE_UNKNOWN = 0.5;

    public DefaultCompatibilityCalculator(AppConfig config) {
        this(config, AppClock.clock());
    }

    public DefaultCompatibilityCalculator(AppConfig config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public double calculateAgeScore(User me, User them) {
        if (me.getAge(config.safety().userTimeZone()).isEmpty()
                || them.getAge(config.safety().userTimeZone()).isEmpty()) {
            return NEUTRAL_SCORE;
        }
        int ageDiff = Math.abs(me.getAge(config.safety().userTimeZone()).orElseThrow()
                - them.getAge(config.safety().userTimeZone()).orElseThrow());
        return calculateAgeScore(ageDiff, me, them, AGE_SIMILAR_YEARS);
    }

    private double calculateAgeScore(int ageDiff, User first, User second, int perfectSimilarityYears) {
        if (perfectSimilarityYears > 0 && ageDiff <= perfectSimilarityYears) {
            return 1.0;
        }

        int firstRange = first.getMaxAge() - first.getMinAge();
        int secondRange = second.getMaxAge() - second.getMinAge();
        double avgRange = (firstRange + secondRange) / 2.0;
        if (avgRange <= 0) {
            return 0.5;
        }
        return Math.max(0.0, 1.0 - (ageDiff / avgRange));
    }

    @Override
    public double calculateInterestScore(User me, User them) {
        InterestMatcher.MatchResult match = InterestMatcher.compare(me.getInterests(), them.getInterests());
        return calculateInterestScore(
                me.getInterests(), them.getInterests(), match.overlapRatio(), NEUTRAL_SCORE, INTEREST_MISSING_SCORE);
    }

    private double calculateInterestScore(
            java.util.Set<datingapp.core.profile.MatchPreferences.Interest> firstInterests,
            java.util.Set<datingapp.core.profile.MatchPreferences.Interest> secondInterests,
            double overlapRatio,
            double neutralScore,
            double missingScore) {
        boolean firstEmpty = firstInterests == null || firstInterests.isEmpty();
        boolean secondEmpty = secondInterests == null || secondInterests.isEmpty();

        if (firstEmpty && secondEmpty) {
            return neutralScore;
        }
        if (firstEmpty || secondEmpty) {
            return missingScore;
        }
        return overlapRatio;
    }

    @Override
    public double calculateLifestyleScore(User me, User them) {
        int total = lifestyleComparableFactorCount(me, them);
        if (total == 0) {
            return NEUTRAL_SCORE;
        }
        int matches = lifestyleMatchCount(me, them);
        return (double) matches / total;
    }

    private int lifestyleComparableFactorCount(User first, User second) {
        int total = 0;
        if (first.getSmoking() != null && second.getSmoking() != null) {
            total++;
        }
        if (first.getDrinking() != null && second.getDrinking() != null) {
            total++;
        }
        if (first.getWantsKids() != null && second.getWantsKids() != null) {
            total++;
        }
        if (first.getLookingFor() != null && second.getLookingFor() != null) {
            total++;
        }
        return total;
    }

    private int lifestyleMatchCount(User first, User second) {
        int matches = 0;
        if (LifestyleMatcher.isMatch(first.getSmoking(), second.getSmoking())) {
            matches++;
        }
        if (LifestyleMatcher.isMatch(first.getDrinking(), second.getDrinking())) {
            matches++;
        }
        if (first.getWantsKids() != null
                && second.getWantsKids() != null
                && LifestyleMatcher.areKidsStancesCompatible(first.getWantsKids(), second.getWantsKids())) {
            matches++;
        }
        if (LifestyleMatcher.isMatch(first.getLookingFor(), second.getLookingFor())) {
            matches++;
        }
        return matches;
    }

    @Override
    public double calculateDistanceScore(double distanceKm, int maxDistanceKm) {
        if (distanceKm < 0) {
            return NEUTRAL_SCORE;
        }
        if (distanceKm <= 1.0) {
            return 1.0; // Very close
        }
        if (distanceKm >= maxDistanceKm) {
            return 0.0;
        }
        return 1.0 - (distanceKm / maxDistanceKm);
    }

    @Override
    public double calculateResponseScore(Duration timeBetweenLikes) {
        if (timeBetweenLikes == null || timeBetweenLikes.isZero()) {
            return NEUTRAL_SCORE;
        }

        long hours = timeBetweenLikes.toHours();
        if (hours < config.algorithm().responseTimeExcellentHours()) {
            return RESPONSE_SCORE_EXCELLENT;
        }
        if (hours < config.algorithm().responseTimeGreatHours()) {
            return RESPONSE_SCORE_GREAT;
        }
        if (hours < config.algorithm().responseTimeGoodHours()) {
            return RESPONSE_SCORE_GOOD;
        }
        if (hours < config.algorithm().responseTimeWeekHours()) {
            return RESPONSE_SCORE_OK;
        }
        if (hours < config.algorithm().responseTimeMonthHours()) {
            return RESPONSE_SCORE_LOW;
        }
        return RESPONSE_SCORE_VERY_LOW;
    }

    @Override
    public double calculatePaceScore(PacePreferences a, PacePreferences b) {
        if (a == null || b == null || !a.isComplete() || !b.isComplete()) {
            return NEUTRAL_SCORE;
        }

        int score = 0;
        score += dimensionScore(a.messagingFrequency(), b.messagingFrequency(), false);
        score += dimensionScore(a.timeToFirstDate(), b.timeToFirstDate(), false);

        boolean commStyleWildcard = isCommunicationStyleWildcard(a.communicationStyle())
                || isCommunicationStyleWildcard(b.communicationStyle());
        score += dimensionScore(a.communicationStyle(), b.communicationStyle(), commStyleWildcard);

        boolean depthWildcard =
                isDepthPreferenceWildcard(a.depthPreference()) || isDepthPreferenceWildcard(b.depthPreference());
        score += dimensionScore(a.depthPreference(), b.depthPreference(), depthWildcard);

        return score / 100.0;
    }

    @Override
    public double calculateActivityScore(User user) {
        if (user.getUpdatedAt() == null) {
            return ACTIVITY_SCORE_UNKNOWN;
        }
        Duration sinceUpdate = Duration.between(user.getUpdatedAt(), clock.instant());
        long hours = sinceUpdate.toHours();

        if (hours < ACTIVITY_VERY_RECENT_HOURS) {
            return ACTIVITY_SCORE_VERY_RECENT;
        }
        if (hours < ACTIVITY_RECENT_HOURS) {
            return ACTIVITY_SCORE_RECENT;
        }
        if (hours < ACTIVITY_MODERATE_HOURS) {
            return ACTIVITY_SCORE_MODERATE;
        }
        if (hours < ACTIVITY_WEEKLY_HOURS) {
            return ACTIVITY_SCORE_WEEKLY;
        }
        if (hours < ACTIVITY_MONTHLY_HOURS) {
            return ACTIVITY_SCORE_MONTHLY;
        }
        return ACTIVITY_SCORE_INACTIVE;
    }

    private int dimensionScore(Enum<?> a, Enum<?> b, boolean hasWildcard) {
        if (hasWildcard) {
            return WILDCARD_SCORE;
        }
        int distance = Math.abs(a.ordinal() - b.ordinal());
        return switch (distance) {
            case 0 -> PACE_SCORE_EXACT;
            case 1 -> PACE_SCORE_CLOSE;
            default -> PACE_SCORE_FAR;
        };
    }

    private boolean isCommunicationStyleWildcard(PacePreferences.CommunicationStyle style) {
        return style == PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING;
    }

    private boolean isDepthPreferenceWildcard(PacePreferences.DepthPreference preference) {
        return preference == PacePreferences.DepthPreference.DEPENDS_ON_VIBE;
    }
}
