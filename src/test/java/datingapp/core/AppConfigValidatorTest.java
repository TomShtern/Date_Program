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
                () -> AppConfigValidator.validateMatching(10, 2, 10, 100, 3.0, 0.5, 0.5, 0.5, 0.0, 0.0, 0.0, 1, 50));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("sum to 1.0"));
    }

    @Test
    @DisplayName("validateValidation rejects invalid age range")
    void validateValidationRejectsInvalidAgeRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfigValidator.validateValidation(
                        40, 20, 100, 220, 500, 500, 50, 2, 1, 10, 6, 50, 15, 5, 10));
    }

    @Test
    @DisplayName("validateSafety accepts valid safety parameters")
    void validateSafetyAcceptsValidValues() {
        assertDoesNotThrow(() -> AppConfigValidator.validateSafety(
                3, ZoneId.of("UTC"), 30, 60, 1, 5, 10, 25, 50, 20, 0.35, 0.65, 100, 4, 30, 90));
    }
}
