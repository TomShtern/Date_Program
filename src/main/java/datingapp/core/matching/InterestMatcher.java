package datingapp.core.matching;

import datingapp.core.EnumSetUtil;
import datingapp.core.profile.MatchPreferences.Interest;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for comparing interest sets between users. Used by MatchQualityService to compute
 * interest-based compatibility.
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
public final class InterestMatcher {

    private static final int SHARED_INTERESTS_PREVIEW_COUNT = 3;

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

        List<String> names = shared.stream()
                .sorted(Comparator.comparing(Interest::getDisplayName))
                .limit(SHARED_INTERESTS_PREVIEW_COUNT)
                .map(Interest::getDisplayName)
                .toList();

        int remaining = shared.size() - SHARED_INTERESTS_PREVIEW_COUNT;

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
}
