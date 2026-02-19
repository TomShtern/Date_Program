package datingapp.core.matching;

import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Interest;
import java.util.Set;

/** Shared compatibility score primitives used by recommendation and quality services. */
final class CompatibilityScoring {

    private CompatibilityScoring() {
        // Utility class
    }

    static double ageScore(int ageDiff, User first, User second, int perfectSimilarityYears) {
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

    static double interestScore(
            Set<Interest> firstInterests,
            Set<Interest> secondInterests,
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

    static double lifestyleScore(User first, User second, double neutralScore) {
        int total = lifestyleComparableFactorCount(first, second);
        if (total == 0) {
            return neutralScore;
        }
        int matches = lifestyleMatchCount(first, second);
        return (double) matches / total;
    }

    private static int lifestyleComparableFactorCount(User first, User second) {
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

    private static int lifestyleMatchCount(User first, User second) {
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
}
