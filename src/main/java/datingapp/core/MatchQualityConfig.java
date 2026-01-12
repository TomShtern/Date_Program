package datingapp.core;

/** Configuration for match quality score weights. Weights must sum to 1.0 (normalized). */
public record MatchQualityConfig(
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
