package datingapp.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        int matches = 0;
        int total = 0;

        // Smoking
        if (me.getSmoking() != null && them.getSmoking() != null) {
            total++;
            if (me.getSmoking() == them.getSmoking()) {
                matches++;
            }
        }

        // Drinking
        if (me.getDrinking() != null && them.getDrinking() != null) {
            total++;
            if (me.getDrinking() == them.getDrinking()) {
                matches++;
            }
        }

        // Wants Kids
        if (me.getWantsKids() != null && them.getWantsKids() != null) {
            total++;
            if (areKidsStancesCompatible(me.getWantsKids(), them.getWantsKids())) {
                matches++;
            }
        }

        // Looking For
        if (me.getLookingFor() != null && them.getLookingFor() != null) {
            total++;
            if (me.getLookingFor() == them.getLookingFor()) {
                matches++;
            }
        }

        // If no lifestyle data, return neutral
        if (total == 0) {
            return 0.5;
        }

        return (double) matches / total;
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

        // Non-smokers highlight
        if (me.getSmoking() != null && me.getSmoking() == them.getSmoking()) {
            if (me.getSmoking() == Lifestyle.Smoking.NEVER) {
                matches.add("Both non-smokers");
            } else if (me.getSmoking() == Lifestyle.Smoking.SOMETIMES) {
                matches.add("Both occasional smokers");
            }
        }

        // Drinking habits
        if (me.getDrinking() != null && me.getDrinking() == them.getDrinking()) {
            if (me.getDrinking() == Lifestyle.Drinking.NEVER) {
                matches.add("Neither drinks");
            } else if (me.getDrinking() == Lifestyle.Drinking.SOCIALLY) {
                matches.add("Both social drinkers");
            }
        }

        // Kids stance
        if (me.getWantsKids() != null && them.getWantsKids() != null) {
            if (me.getWantsKids() == them.getWantsKids()) {
                matches.add("Same stance on kids");
            } else if (areKidsStancesCompatible(me.getWantsKids(), them.getWantsKids())) {
                matches.add("Compatible on kids");
            }
        }

        // Relationship goals
        if (me.getLookingFor() != null && me.getLookingFor() == them.getLookingFor()) {
            matches.add(
                    "Both looking for " + me.getLookingFor().getDisplayName().toLowerCase());
        }

        return matches;
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
