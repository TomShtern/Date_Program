package datingapp.core.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WeightedScore")
class WeightedScoreTest {

    @Test
    @DisplayName("normalizes weighted sums by total weight")
    void normalizesWeightedSumsByTotalWeight() {
        WeightedScore score = WeightedScore.empty().add(0.75, 2.0).add(0.25, 2.0);

        assertEquals(0.5, score.normalized(), 0.0001);
    }

    @Test
    @DisplayName("returns zero when total weight is zero")
    void returnsZeroWhenTotalWeightIsZero() {
        WeightedScore score = WeightedScore.empty();

        assertEquals(0.0, score.normalized(), 0.0001);
    }
}
