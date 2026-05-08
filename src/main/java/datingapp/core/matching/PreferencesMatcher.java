package datingapp.core.matching;

import datingapp.core.EnumSetUtil;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for comparing user preferences and checking lifestyle compatibility.
 *
 * <p>This class is stateless and all methods are static.
 */
public final class PreferencesMatcher {

    private static final int DEFAULT_SHARED_INTERESTS_PREVIEW_COUNT = 3;

    private PreferencesMatcher() {
        // Utility class - prevent instantiation.
    }

    /**
     * Result of comparing two interest sets.
     *
     * @param shared the interests both users have in common
     * @param sharedCount number of shared interests (convenience)
     * @param overlapRatio shared / min(a.size, b.size), range [0.0, 1.0]
     * @param jaccardIndex shared / union, range [0.0, 1.0]
     */
    public static record MatchResult(Set<Interest> shared, int sharedCount, double overlapRatio, double jaccardIndex) {
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

    public static String formatSharedInterests(Set<Interest> shared) {
        return formatSharedInterests(shared, DEFAULT_SHARED_INTERESTS_PREVIEW_COUNT);
    }

    /**
     * Formats shared interests as a human-readable string.
     *
     * @param shared set of shared interests
     * @param previewCount maximum number of interests to show before truncating with "and X more"
     * @return formatted string like "Hiking, Coffee, and 2 more" or empty string if none
     */
    public static String formatSharedInterests(Set<Interest> shared, int previewCount) {
        if (shared == null || shared.isEmpty()) {
            return "";
        }
        if (previewCount < 1) {
            throw new IllegalArgumentException("previewCount must be at least 1");
        }

        List<String> names = shared.stream()
                .sorted(Comparator.comparing(Interest::getDisplayName))
                .limit(previewCount)
                .map(Interest::getDisplayName)
                .toList();

        int remaining = shared.size() - previewCount;

        if (remaining > 0) {
            return names.size() == 1
                    ? names.getFirst() + " and " + remaining + " more"
                    : String.join(", ", names) + ", and " + remaining + " more";
        }

        return joinWithOxfordComma(names);
    }

    private static String joinWithOxfordComma(List<String> names) {
        return switch (names.size()) {
            case 0 -> "";
            case 1 -> names.getFirst();
            case 2 -> names.getFirst() + " and " + names.get(1);
            default -> String.join(", ", names.subList(0, names.size() - 1)) + ", and " + names.getLast();
        };
    }

    /**
     * Formats shared interests as a list for display. Returns display names sorted alphabetically.
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

    /** Check if a lifestyle value is acceptable given a set of allowed values. */
    public static <E extends Enum<E>> boolean isAcceptable(E value, Set<E> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        return value != null && allowed.contains(value);
    }

    /** Check if two users have the same smoking habit. */
    public static boolean isMatch(Lifestyle.Smoking a, Lifestyle.Smoking b) {
        return a != null && b != null && a == b;
    }

    /** Check if two users have the same drinking habit. */
    public static boolean isMatch(Lifestyle.Drinking a, Lifestyle.Drinking b) {
        return a != null && b != null && a == b;
    }

    /** Check if two users have the same relationship goal. */
    public static boolean isMatch(Lifestyle.LookingFor a, Lifestyle.LookingFor b) {
        return a != null && b != null && a == b;
    }

    /** Check if two users have the same kids stance (exact equality only; use areKidsStancesCompatible for fuzzy). */
    public static boolean isMatch(Lifestyle.WantsKids a, Lifestyle.WantsKids b) {
        return a != null && b != null && a == b;
    }

    /** Check if two users have the same education level. */
    public static boolean isMatch(Lifestyle.Education a, Lifestyle.Education b) {
        return a != null && b != null && a == b;
    }

    /**
     * Checks if two kids stances are compatible.
     * Logic:
     * - Exact match is compatible
     * - OPEN is compatible with everything
     * - SOMEDAY and HAS_KIDS are compatible usually (mixed families)
     */
    public static boolean areKidsStancesCompatible(Lifestyle.WantsKids a, Lifestyle.WantsKids b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b) {
            return true;
        }
        if (a == Lifestyle.WantsKids.OPEN || b == Lifestyle.WantsKids.OPEN) {
            return true;
        }
        return (a == Lifestyle.WantsKids.SOMEDAY && b == Lifestyle.WantsKids.HAS_KIDS)
                || (a == Lifestyle.WantsKids.HAS_KIDS && b == Lifestyle.WantsKids.SOMEDAY);
    }
}
