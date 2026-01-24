package datingapp.core;

import datingapp.core.CandidateFinder.GeoUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
    private final PaceCompatibilityService paceService;
    private final MatchQualityConfig config;

    /** Configuration for match quality score weights. Weights must sum to 1.0 (normalized). */
    public static record MatchQualityConfig(
            double distanceWeight, // How much distance matters
            double ageWeight, // How much age alignment matters
            double interestWeight, // How much shared interests matter
            double lifestyleWeight, // How much lifestyle alignment matters
            double paceWeight, // How much pace alignment matters (Phase 2)
            double responseWeight // How much response speed matters
            ) {
        public MatchQualityConfig {
            // Weights should sum to 1.0 (normalized)
            double total = distanceWeight + ageWeight + interestWeight + lifestyleWeight + paceWeight + responseWeight;
            if (Math.abs(total - 1.0) > 0.001) {
                throw new IllegalArgumentException("Weights must sum to 1.0, got: " + total);
            }

            // Validate individual weights are non-negative
            if (distanceWeight < 0
                    || ageWeight < 0
                    || interestWeight < 0
                    || lifestyleWeight < 0
                    || paceWeight < 0
                    || responseWeight < 0) {
                throw new IllegalArgumentException("Weights cannot be negative");
            }
        }

        /** Default weights emphasizing interests and lifestyle. */
        public static MatchQualityConfig defaults() {
            return new MatchQualityConfig(
                    0.15, // distance
                    0.10, // age
                    0.25, // interests
                    0.25, // lifestyle
                    0.15, // pace
                    0.10 // response
                    );
        }

        /** Weights for users who prioritize proximity. */
        public static MatchQualityConfig proximityFocused() {
            return new MatchQualityConfig(
                    0.35, // distance
                    0.10, // age
                    0.20, // interests
                    0.20, // lifestyle
                    0.10, // pace
                    0.05 // response
                    );
        }

        /** Weights prioritizing lifestyle alignment. */
        public static MatchQualityConfig lifestyleFocused() {
            return new MatchQualityConfig(
                    0.10, // distance
                    0.10, // age
                    0.20, // interests
                    0.40, // lifestyle
                    0.15, // pace
                    0.05 // response
                    );
        }
    }

    /**
     * Utility class for comparing interest sets between users. Used by MatchQualityService to
     * compute interest-based compatibility.
     *
     * <p>This class computes two metrics:
     *
     * <ul>
     *   <li><b>Overlap Ratio</b>: shared / min(a.size, b.size) - rewards having all interests match
     *   <li><b>Jaccard Index</b>: shared / union - standard similarity metric
     * </ul>
     *
     * <p>Thread-safe: This class is stateless and all methods are static.
     */
    public static final class InterestMatcher {

        private InterestMatcher() {
            // Utility class - prevent instantiation
        }

        /**
         * Result of comparing two interest sets.
         *
         * @param shared the interests both users have in common
         * @param sharedCount number of shared interests (convenience)
         * @param overlapRatio shared / min(a.size, b.size), range [0.0, 1.0]
         * @param jaccardIndex shared / union, range [0.0, 1.0]
         */
        public record MatchResult(Set<Interest> shared, int sharedCount, double overlapRatio, double jaccardIndex) {
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
            Set<Interest> setA = (a == null || a.isEmpty()) ? EnumSet.noneOf(Interest.class) : EnumSet.copyOf(a);
            Set<Interest> setB = (b == null || b.isEmpty()) ? EnumSet.noneOf(Interest.class) : EnumSet.copyOf(b);

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
         * Formats shared interests as a human-readable string. Shows up to 3 interests, with "and X
         * more" if exceeded.
         *
         * @param shared set of shared interests
         * @return formatted string like "Hiking, Coffee, and 2 more" or empty string if none
         */
        public static String formatSharedInterests(Set<Interest> shared) {
            if (shared == null || shared.isEmpty()) {
                return "";
            }

            List<String> names =
                    shared.stream().limit(3).map(Interest::getDisplayName).toList();

            int remaining = shared.size() - 3;

            if (remaining > 0) {
                return String.join(", ", names) + ", and " + remaining + " more";
            } else if (names.size() == 1) {
                return names.get(0);
            } else if (names.size() == 2) {
                return names.get(0) + " and " + names.get(1);
            } else {
                return names.get(0) + ", " + names.get(1) + ", and " + names.get(2);
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

    public MatchQualityService(UserStorage userStorage, LikeStorage likeStorage, PaceCompatibilityService paceService) {
        this(userStorage, likeStorage, paceService, MatchQualityConfig.defaults());
    }

    public MatchQualityService(
            UserStorage userStorage,
            LikeStorage likeStorage,
            PaceCompatibilityService paceService,
            MatchQualityConfig config) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.likeStorage = Objects.requireNonNull(likeStorage, "likeStorage cannot be null");
        this.paceService = Objects.requireNonNull(paceService, "paceService cannot be null");
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

        if (me == null || them == null) {
            throw new IllegalArgumentException("User not found");
        }

        // === Calculate Individual Scores ===

        // Distance Score
        double distanceKm = GeoUtils.distanceKm(
                me.getLat(), me.getLon(),
                them.getLat(), them.getLon());
        double distanceScore = calculateDistanceScore(distanceKm, me.getMaxDistanceKm());

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
        double paceScore = paceService.calculatePaceScore(me.getPacePreferences(), them.getPacePreferences());
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
                Instant.now(),
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
        if (distanceKm <= 1) {
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
        if (ageDiff <= 2) {
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
            return 0.5; // Unknown = neutral
        }

        if (me.getInterests().isEmpty() || them.getInterests().isEmpty()) {
            return 0.3; // One has interests, other doesn't
        }

        return match.overlapRatio();
    }

    private double calculateLifestyleScore(User me, User them) {
        int total = countLifestyleFactors(me, them);
        if (total == 0) {
            return 0.5;
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
        if (score >= 0.95) {
            return "Perfect Sync";
        }
        if (score >= 0.8) {
            return "Good Sync";
        }
        if (score >= 0.6) {
            return "Fair Sync";
        }
        if (score >= 0.4) {
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
                    "Both looking for " + me.getLookingFor().getDisplayName().toLowerCase());
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
            return 0.5; // Unknown
        }

        long hours = timeBetween.toHours();

        // Within 1 hour = excellent
        if (hours < 1) {
            return 1.0;
        }
        // Within 24 hours = great
        if (hours < 24) {
            return 0.9;
        }
        // Within 3 days = good
        if (hours < 72) {
            return 0.7;
        }
        // Within a week = okay
        if (hours < 168) {
            return 0.5;
        }
        // Within a month = low
        if (hours < 720) {
            return 0.3;
        }
        // Longer = very low
        return 0.1;
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
        if (distanceKm < 5) {
            highlights.add(String.format("Lives nearby (%.1f km away)", distanceKm));
        } else if (distanceKm < 15) {
            highlights.add(String.format("%.0f km away", distanceKm));
        }

        // Interest highlights
        if (!sharedInterests.isEmpty()) {
            if (sharedInterests.size() == 1) {
                highlights.add("You both enjoy " + sharedInterests.get(0));
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
        if (paceScore >= 0.95) {
            highlights.add("Total Pace Sync! ⚡");
        } else if (paceScore >= 0.8) {
            highlights.add("Great communication sync");
        }

        // Response time highlight
        if (timeBetween != null && !timeBetween.isZero() && timeBetween.toHours() < 24) {
            highlights.add("Quick mutual interest!");
        }

        // Age highlight
        int ageDiff = Math.abs(me.getAge() - them.getAge());
        if (ageDiff <= 2) {
            highlights.add("Similar age");
        }

        // Limit to top 5 highlights
        if (highlights.size() > 5) {
            highlights = new ArrayList<>(highlights.subList(0, 5));
        }

        return highlights;
    }

    /** Render a progress bar for display. */
    public static String renderProgressBar(double score, int width) {
        int filled = (int) Math.round(score * width);
        int empty = width - filled;
        return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
    }
}
