package datingapp.core.matching;

/**
 * Tiny accumulator for weighted score aggregation.
 */
public record WeightedScore(double weightedSum, double totalWeight) {

    public static WeightedScore empty() {
        return new WeightedScore(0.0, 0.0);
    }

    public WeightedScore add(double score, double weight) {
        return new WeightedScore(weightedSum + (score * weight), totalWeight + weight);
    }

    public double normalized() {
        return totalWeight > 0.0 ? weightedSum / totalWeight : 0.0;
    }
}
