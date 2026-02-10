package datingapp.core;

import datingapp.core.CandidateFinder.GeoUtils;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.UserInteractions.Like;
import datingapp.core.constants.ScoringConstants;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.UserStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for computing match quality/compatibility. Pure Java - no framework
 * dependencies.
 */
public class MatchQualityService {

    private final UserStorage userStorage;
    private final LikeStorage likeStorage;
    private final AppConfig config;

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
            if (compatibilityScore >= ScoringConstants.MatchQuality.STAR_EXCELLENT_THRESHOLD) {
                return 5;
            }
            if (compatibilityScore >= ScoringConstants.MatchQuality.STAR_GREAT_THRESHOLD) {
                return 4;
            }
            if (compatibilityScore >= ScoringConstants.MatchQuality.STAR_GOOD_THRESHOLD) {
                return 3;
            }
            if (compatibilityScore >= ScoringConstants.MatchQuality.STAR_FAIR_THRESHOLD) {
                return 2;
            }
            return 1;
        }

        /** Get compatibility label. */
        public String getCompatibilityLabel() {
            if (compatibilityScore >= ScoringConstants.MatchQuality.STAR_EXCELLENT_THRESHOLD) {
                return ScoringConstants.MatchQuality.LABEL_EXCELLENT;
            }
            if (compatibilityScore >= ScoringConstants.MatchQuality.STAR_GREAT_THRESHOLD) {
                return ScoringConstants.MatchQuality.LABEL_GREAT;
            }
            if (compatibilityScore >= ScoringConstants.MatchQuality.STAR_GOOD_THRESHOLD) {
                return ScoringConstants.MatchQuality.LABEL_GOOD;
            }
            if (compatibilityScore >= ScoringConstants.MatchQuality.STAR_FAIR_THRESHOLD) {
                return ScoringConstants.MatchQuality.LABEL_FAIR;
            }
            return ScoringConstants.MatchQuality.LABEL_LOW;
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
                return first.length() > ScoringConstants.MatchQuality.SUMMARY_MAX_LENGTH
                        ? first.substring(0, ScoringConstants.MatchQuality.SUMMARY_TRUNCATE_LENGTH) + "..."
                        : first;
            }
            return getCompatibilityLabel();
        }
    }

    /**
     * Utility class for comparing interest sets between users. Used by
     * MatchQualityService to
     * compute interest-based compatibility.
     *
     * <p>
     * This class computes two metrics:
     *
     * <ul>
     * <li><b>Overlap Ratio</b>: shared / min(a.size, b.size) - rewards having all
     * interests match
     * <li><b>Jaccard Index</b>: shared / union - standard similarity metric
     * </ul>
     *
     * <p>
     * Thread-safe: This class is stateless and all methods are static.
     */
    public static final class InterestMatcher {

        private InterestMatcher() {
            // Utility class - prevent instantiation
        }

        /**
         * Result of comparing two interest sets.
         *
         * @param shared       the interests both users have in common
         * @param sharedCount  number of shared interests (convenience)
         * @param overlapRatio shared / min(a.size, b.size), range [0.0, 1.0]
         * @param jaccardIndex shared / union, range [0.0, 1.0]
         */
        public static record MatchResult(
                Set<Interest> shared, int sharedCount, double overlapRatio, double jaccardIndex) {
            public MatchResult {
                Objects.requireNonNull(shared, "shared cannot be null");
                if (sharedCount < 0) {
                    throw new IllegalArgumentException("sharedCount cannot be negative");
                }
                if (overlapRatio < 0.0 || overlapRatio > 1.0) {
                    throw new IllegalArgumentException("overlapRatio must be 0.0-1.0");
                }
                if (jaccardIndex < 0.0 || jaccardIndex > 1.0) {
                    throw new IllegalArgumentException("jaccardIndex must be 0.0-1.0");
                }
            }

            /** Returns true if there are any shared interests. */
            public boolean hasSharedInterests() {
                return sharedCount > 0;
            }
        }

        /**
         * Compares two sets of interests and returns detailed match metrics.
         *
         * @param a first interest set (may be null or empty)
         * @param b second interest set (may be null or empty)
         * @return MatchResult with overlap metrics
         */
        public static MatchResult compare(Set<Interest> a, Set<Interest> b) {
            // Handle null/empty cases
            Set<Interest> setA = EnumSetUtil.safeCopy(a, Interest.class);
            Set<Interest> setB = EnumSetUtil.safeCopy(b, Interest.class);

            // Empty set case
            if (setA.isEmpty() || setB.isEmpty()) {
                return new MatchResult(EnumSet.noneOf(Interest.class), 0, 0.0, 0.0);
            }

            // Compute intersection
            Set<Interest> shared = EnumSet.copyOf(setA);
            shared.retainAll(setB);
            int sharedCount = shared.size();

            // Compute union for Jaccard
            Set<Interest> union = EnumSet.copyOf(setA);
            union.addAll(setB);
            int unionSize = union.size();

            // Calculate ratios
            int minSize = Math.min(setA.size(), setB.size());
            double overlapRatio = (double) sharedCount / minSize;
            double jaccardIndex = (double) sharedCount / unionSize;

            return new MatchResult(shared, sharedCount, overlapRatio, jaccardIndex);
        }

        /**
         * Formats shared interests as a human-readable string. Shows up to 3 interests,
         * with "and X
         * more" if exceeded.
         *
         * @param shared set of shared interests
         * @return formatted string like "Hiking, Coffee, and 2 more" or empty string if
         *         none
         */
        public static String formatSharedInterests(Set<Interest> shared) {
            if (shared == null || shared.isEmpty()) {
                return "";
            }

            List<String> names = shared.stream()
                    .limit(ScoringConstants.MatchQuality.SHARED_INTERESTS_PREVIEW_COUNT)
                    .map(Interest::getDisplayName)
                    .toList();

            int remaining = shared.size() - ScoringConstants.MatchQuality.SHARED_INTERESTS_PREVIEW_COUNT;

            if (remaining > 0) {
                return String.join(", ", names) + ", and " + remaining + " more";
            } else if (names.size() == 1) {
                return names.getFirst();
            } else if (names.size() == 2) {
                return names.getFirst() + " and " + names.get(1);
            } else {
                return names.getFirst() + ", " + names.get(1) + ", and " + names.get(2);
            }
        }

        /**
         * Formats shared interests as a list for display. Returns display names sorted
         * alphabetically.
         *
         * @param shared set of shared interests
         * @return list of display names (never null)
         */
        public static List<String> formatAsList(Set<Interest> shared) {
            if (shared == null || shared.isEmpty()) {
                return List.of();
            }
            return shared.stream().map(Interest::getDisplayName).sorted().toList();
        }
    }

    public MatchQualityService(UserStorage userStorage, LikeStorage likeStorage, AppConfig config) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Compute match quality from one user's perspective.
     *
     * @param match             The match to compute quality for
     * @param perspectiveUserId The user whose perspective we're computing from
     * @return Computed match quality
     */
    public MatchQuality computeQuality(Match match, UUID perspectiveUserId) {
        UUID otherUserId = match.getOtherUser(perspectiveUserId);

        User me = userStorage.get(perspectiveUserId);
        User them = userStorage.get(otherUserId);

        if (me == null) {
            throw new IllegalArgumentException("User not found: " + perspectiveUserId);
        }
        if (them == null) {
            throw new IllegalArgumentException("User not found: " + otherUserId);
        }

        // === Calculate Individual Scores ===

        // Distance Score
        double distanceKm;
        if (me.hasLocationSet() && them.hasLocationSet()) {
            distanceKm = GeoUtils.distanceKm(me.getLat(), me.getLon(), them.getLat(), them.getLon());
        } else {
            distanceKm = -1;
        }
        double distanceScore = distanceKm >= 0
                ? calculateDistanceScore(distanceKm, me.getMaxDistanceKm())
                : ScoringConstants.MatchQuality.NEUTRAL_SCORE;

        // Age Score
        int ageDiff = Math.abs(me.getAge() - them.getAge());
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
        double weightedScore = distanceScore * config.distanceWeight()
                + ageScore * config.ageWeight()
                + interestScore * config.interestWeight()
                + lifestyleScore * config.lifestyleWeight()
                + paceScore * config.paceWeight()
                + responseScore * config.responseWeight();

        int compatibilityScore = (int) Math.round(weightedScore * 100);

        // === Generate Highlights ===
        List<String> highlights = generateHighlights(
                me, them, distanceKm, sharedInterests, lifestyleMatches, paceScore, timeBetweenLikes);

        return new MatchQuality(
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
                highlights);
    }

    // === Score Calculation Methods ===

    private double calculateDistanceScore(double distanceKm, int maxDistanceKm) {
        if (distanceKm <= ScoringConstants.MatchQuality.VERY_CLOSE_DISTANCE_KM) {
            return 1.0; // Very close
        }
        if (distanceKm >= maxDistanceKm) {
            return 0.0; // At limit
        }

        // Linear decay from 1.0 to 0.0
        return 1.0 - (distanceKm / maxDistanceKm);
    }

    private double calculateAgeScore(int ageDiff, User me, User them) {
        // Perfect score if within 2 years
        if (ageDiff <= ScoringConstants.MatchQuality.AGE_SIMILAR_YEARS) {
            return 1.0;
        }

        // Calculate how well they fit in each other's ranges
        int myRange = me.getMaxAge() - me.getMinAge();
        int theirRange = them.getMaxAge() - them.getMinAge();
        int avgRange = (myRange + theirRange) / 2;

        if (avgRange == 0) {
            return 1.0; // No range = no penalty
        }

        // Score based on how close to ideal vs range
        double normalizedDiff = (double) ageDiff / avgRange;
        return Math.max(0.0, 1.0 - normalizedDiff);
    }

    private double calculateInterestScore(InterestMatcher.MatchResult match, User me, User them) {
        // If neither has interests, neutral score
        if (me.getInterests().isEmpty() && them.getInterests().isEmpty()) {
            return ScoringConstants.MatchQuality.NEUTRAL_SCORE; // Unknown = neutral
        }

        if (me.getInterests().isEmpty() || them.getInterests().isEmpty()) {
            return ScoringConstants.MatchQuality.INTEREST_MISSING_SCORE; // One has interests, other doesn't
        }

        return match.overlapRatio();
    }

    private double calculateLifestyleScore(User me, User them) {
        int total = countLifestyleFactors(me, them);
        if (total == 0) {
            return ScoringConstants.MatchQuality.NEUTRAL_SCORE;
        }

        int matches = countLifestyleMatches(me, them);
        return (double) matches / total;
    }

    private int countLifestyleFactors(User me, User them) {
        int total = 0;
        if (me.getSmoking() != null && them.getSmoking() != null) {
            total++;
        }
        if (me.getDrinking() != null && them.getDrinking() != null) {
            total++;
        }
        if (me.getWantsKids() != null && them.getWantsKids() != null) {
            total++;
        }
        if (me.getLookingFor() != null && them.getLookingFor() != null) {
            total++;
        }
        return total;
    }

    private int countLifestyleMatches(User me, User them) {
        int matches = 0;
        if (me.getSmoking() != null && them.getSmoking() != null && me.getSmoking() == them.getSmoking()) {
            matches++;
        }
        if (me.getDrinking() != null && them.getDrinking() != null && me.getDrinking() == them.getDrinking()) {
            matches++;
        }
        if (me.getWantsKids() != null
                && them.getWantsKids() != null
                && areKidsStancesCompatible(me.getWantsKids(), them.getWantsKids())) {
            matches++;
        }
        if (me.getLookingFor() != null && them.getLookingFor() != null && me.getLookingFor() == them.getLookingFor()) {
            matches++;
        }
        return matches;
    }

    private boolean areKidsStancesCompatible(Lifestyle.WantsKids a, Lifestyle.WantsKids b) {
        // Compatible if same
        if (a == b) {
            return true;
        }
        // OPEN is compatible with everything
        if (a == Lifestyle.WantsKids.OPEN || b == Lifestyle.WantsKids.OPEN) {
            return true;
        }
        // SOMEDAY and HAS_KIDS are compatible
        return (a == Lifestyle.WantsKids.SOMEDAY && b == Lifestyle.WantsKids.HAS_KIDS)
                || (a == Lifestyle.WantsKids.HAS_KIDS && b == Lifestyle.WantsKids.SOMEDAY);
    }

    private String getPaceSyncLevel(double score) {
        if (score >= ScoringConstants.MatchQuality.PACE_SYNC_PERFECT) {
            return "Perfect Sync";
        }
        if (score >= ScoringConstants.MatchQuality.PACE_SYNC_GOOD) {
            return "Good Sync";
        }
        if (score >= ScoringConstants.MatchQuality.PACE_SYNC_FAIR) {
            return "Fair Sync";
        }
        if (score >= ScoringConstants.MatchQuality.PACE_SYNC_LAG) {
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
        } else if (areKidsStancesCompatible(me.getWantsKids(), them.getWantsKids())) {
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
        Optional<Like> myLike = likeStorage.getLike(userId, otherId);
        Optional<Like> theirLike = likeStorage.getLike(otherId, userId);

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
            return ScoringConstants.MatchQuality.NEUTRAL_SCORE; // Unknown
        }

        long hours = timeBetween.toHours();

        // Within excellent threshold = excellent
        if (hours < config.responseTimeExcellentHours()) {
            return ScoringConstants.MatchQuality.RESPONSE_SCORE_EXCELLENT;
        }
        // Within great threshold = great
        if (hours < config.responseTimeGreatHours()) {
            return ScoringConstants.MatchQuality.RESPONSE_SCORE_GREAT;
        }
        // Within good threshold = good
        if (hours < config.responseTimeGoodHours()) {
            return ScoringConstants.MatchQuality.RESPONSE_SCORE_GOOD;
        }
        // Within a week = okay
        if (hours < config.responseTimeWeekHours()) {
            return ScoringConstants.MatchQuality.RESPONSE_SCORE_OK;
        }
        // Within a month = low
        if (hours < config.responseTimeMonthHours()) {
            return ScoringConstants.MatchQuality.RESPONSE_SCORE_LOW;
        }
        // Longer = very low
        return ScoringConstants.MatchQuality.RESPONSE_SCORE_VERY_LOW;
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

        // Distance highlight
        if (distanceKm >= 0 && distanceKm < ScoringConstants.MatchQuality.NEARBY_DISTANCE_KM) {
            highlights.add(String.format("Lives nearby (%.1f km away)", distanceKm));
        } else if (distanceKm >= 0 && distanceKm < ScoringConstants.MatchQuality.MID_DISTANCE_KM) {
            highlights.add(String.format("%.0f km away", distanceKm));
        }

        // Interest highlights
        if (!sharedInterests.isEmpty()) {
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

        // Lifestyle highlights
        highlights.addAll(lifestyleMatches);

        // Pace highlights
        if (paceScore >= ScoringConstants.MatchQuality.PACE_SYNC_PERFECT) {
            highlights.add("Total Pace Sync! ⚡");
        } else if (paceScore >= ScoringConstants.MatchQuality.PACE_SYNC_GOOD) {
            highlights.add("Great communication sync");
        }

        // Response time highlight
        if (timeBetween != null
                && !timeBetween.isZero()
                && timeBetween.toHours() < ScoringConstants.MatchQuality.QUICK_MUTUAL_INTEREST_HOURS) {
            highlights.add("Quick mutual interest!");
        }

        // Age highlight
        int ageDiff = Math.abs(me.getAge() - them.getAge());
        if (ageDiff <= ScoringConstants.MatchQuality.AGE_SIMILAR_YEARS) {
            highlights.add("Similar age");
        }

        // Limit to top 5 highlights
        if (highlights.size() > ScoringConstants.MatchQuality.HIGHLIGHT_MAX_COUNT) {
            highlights = new ArrayList<>(highlights.subList(0, ScoringConstants.MatchQuality.HIGHLIGHT_MAX_COUNT));
        }

        return highlights;
    }

    /** Render a progress bar for display. */
    public static String renderProgressBar(double score, int width) {
        int filled = (int) Math.round(score * width);
        int empty = width - filled;
        return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
    }

    // === Pace Compatibility Methods (formerly PaceCompatibilityService) ===

    /**
     * Calculates a compatibility score (0-100) between two users' pace preferences.
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
            return ScoringConstants.MatchQuality.NEUTRAL_SCORE; // Neutral
        }
        return comp / 100.0;
    }

    private int dimensionScore(Enum<?> a, Enum<?> b, boolean hasWildcard) {
        if (hasWildcard) {
            return ScoringConstants.MatchQuality.WILDCARD_SCORE;
        }

        int distance = Math.abs(a.ordinal() - b.ordinal());
        return switch (distance) {
            case 0 -> ScoringConstants.MatchQuality.PACE_SCORE_EXACT; // Perfect match
            case 1 -> ScoringConstants.MatchQuality.PACE_SCORE_CLOSE; // Close enough
            default -> ScoringConstants.MatchQuality.PACE_SCORE_FAR; // Quite different
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
        return score >= 0 && score < config.paceCompatibilityThreshold();
    }

    /** Gets the warning message for low pace compatibility. */
    public String getLowPaceCompatibilityWarning() {
        return "Your pacing styles differ significantly. Worth discussing early!";
    }
}
