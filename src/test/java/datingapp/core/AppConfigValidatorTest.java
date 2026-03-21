package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AppConfigValidator")
class AppConfigValidatorTest {

    @Test
    @DisplayName("validateMatching rejects weight sums far from 1.0")
    void validateMatchingRejectsInvalidWeightSum() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AppConfigValidator.validateMatching(10, 2, 10, 100, 3.0, 0.5, 0.5, 0.5, 0.0, 0.0, 0.0, 1));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("sum to 1.0"));
    }

    @Test
    @DisplayName("validateValidation rejects invalid age range")
    void validateValidationRejectsInvalidAgeRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfigValidator.validateValidation(
                        40, 20, 100, 220, 500, 500, 50, 2, 1, 50, 10, 6, 50, 15, 5, 10));
    }

    @Test
    @DisplayName("validateValidation rejects minDistanceKm greater than maxDistanceKm")
    void validateValidationRejectsInvalidDistanceOrdering() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfigValidator.validateValidation(
                        18, 50, 100, 220, 500, 500, 50, 2, 100, 50, 10, 6, 50, 15, 5, 10));
    }

    @Test
    @DisplayName("validateValidation accepts valid distance ordering")
    void validateValidationAcceptsValidDistanceOrdering() {
        assertDoesNotThrow(() ->
                AppConfigValidator.validateValidation(18, 50, 100, 220, 500, 500, 50, 2, 1, 500, 10, 6, 50, 15, 5, 10));
    }

    @Test
    @DisplayName("validateAlgorithm rejects nearbyDistanceKm greater than closeDistanceKm")
    void validateAlgorithmRejectsInvalidDistanceOrdering() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfigValidator.validateAlgorithm(
                        100, 50, 2, 5, 50, 1, 24, 72, 168, 720, 0.20, 0.15, 0.25, 0.20, 0.10, 0.10));
    }

    @Test
    @DisplayName("validateAlgorithm accepts valid distance ordering")
    void validateAlgorithmAcceptsValidDistanceOrdering() {
        assertDoesNotThrow(() -> AppConfigValidator.validateAlgorithm(
                5, 10, 2, 5, 50, 1, 24, 72, 168, 720, 0.20, 0.15, 0.25, 0.20, 0.10, 0.10));
    }

    @Test
    @DisplayName("validateSafety accepts valid safety parameters")
    void validateSafetyAcceptsValidValues() {
        assertDoesNotThrow(() -> AppConfigValidator.validateSafety(
                3, ZoneId.of("UTC"), 30, 60, 1, 5, 10, 25, 50, 20, 0.35, 0.65, 100, 4, 30, 90));
    }
}
