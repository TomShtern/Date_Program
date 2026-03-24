package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
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
                () -> new AppConfig.MatchingConfig(10, 2, 10, 100, 3.0, 0.5, 0.5, 0.5, 0.0, 0.0, 0.0, 1, 3, 500));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("sum to 1.0"));
    }

    @Test
    @DisplayName("validateMatching rejects preview counts below 1")
    void validateMatchingRejectsInvalidPreviewCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.MatchingConfig(10, 2, 10, 100, 3.0, 0.3, 0.3, 0.2, 0.1, 0.05, 0.05, 1, 0, 500));
    }

    @Test
    @DisplayName("validateMatching accepts configured preview count")
    void validateMatchingAcceptsPreviewCount() {
        assertDoesNotThrow(
                () -> new AppConfig.MatchingConfig(10, 2, 10, 100, 3.0, 0.3, 0.3, 0.2, 0.1, 0.05, 0.05, 1, 3, 500));
    }

    @Test
    @DisplayName("validateValidation rejects invalid age range")
    void validateValidationRejectsInvalidAgeRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.ValidationConfig(
                        40,
                        20,
                        100,
                        220,
                        500,
                        500,
                        50,
                        Message.MAX_LENGTH,
                        ProfileNote.MAX_LENGTH,
                        2,
                        1,
                        50,
                        10,
                        User.MAX_PHOTOS,
                        50,
                        15,
                        5,
                        10));
    }

    @Test
    @DisplayName("validateValidation rejects maxPhotos drift from User.MAX_PHOTOS")
    void validateValidationRejectsInvalidPhotoLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.ValidationConfig(
                        18,
                        50,
                        100,
                        220,
                        500,
                        500,
                        50,
                        Message.MAX_LENGTH,
                        ProfileNote.MAX_LENGTH,
                        2,
                        1,
                        50,
                        10,
                        8,
                        50,
                        15,
                        5,
                        10));
    }

    @Test
    @DisplayName("validateValidation accepts the entity photo limit")
    void validateValidationAcceptsEntityPhotoLimit() {
        assertDoesNotThrow(() -> new AppConfig.ValidationConfig(
                18,
                50,
                100,
                220,
                500,
                500,
                50,
                Message.MAX_LENGTH,
                ProfileNote.MAX_LENGTH,
                2,
                1,
                50,
                10,
                User.MAX_PHOTOS,
                50,
                15,
                5,
                10));
    }

    @Test
    @DisplayName("validateValidation rejects minDistanceKm greater than maxDistanceKm")
    void validateValidationRejectsInvalidDistanceOrdering() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.ValidationConfig(
                        18,
                        50,
                        100,
                        220,
                        500,
                        500,
                        50,
                        Message.MAX_LENGTH,
                        ProfileNote.MAX_LENGTH,
                        2,
                        100,
                        50,
                        10,
                        User.MAX_PHOTOS,
                        50,
                        15,
                        5,
                        10));
    }

    @Test
    @DisplayName("validateValidation accepts valid distance ordering")
    void validateValidationAcceptsValidDistanceOrdering() {
        assertDoesNotThrow(() -> new AppConfig.ValidationConfig(
                18,
                50,
                100,
                220,
                500,
                500,
                50,
                Message.MAX_LENGTH,
                ProfileNote.MAX_LENGTH,
                2,
                1,
                500,
                10,
                User.MAX_PHOTOS,
                50,
                15,
                5,
                10));
    }

    @Test
    @DisplayName("validateValidation rejects message length below minimum")
    void validateValidationRejectsInvalidMessageLengthLowerBound() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.ValidationConfig(
                        18,
                        50,
                        100,
                        220,
                        500,
                        500,
                        50,
                        0,
                        ProfileNote.MAX_LENGTH,
                        2,
                        1,
                        50,
                        10,
                        User.MAX_PHOTOS,
                        50,
                        15,
                        5,
                        10));
    }

    @Test
    @DisplayName("validateValidation rejects message length above hard cap")
    void validateValidationRejectsInvalidMessageLengthUpperBound() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.ValidationConfig(
                        18,
                        50,
                        100,
                        220,
                        500,
                        500,
                        50,
                        Message.MAX_LENGTH + 1,
                        ProfileNote.MAX_LENGTH,
                        2,
                        1,
                        50,
                        10,
                        User.MAX_PHOTOS,
                        50,
                        15,
                        5,
                        10));
    }

    @Test
    @DisplayName("validateValidation rejects profile note length below minimum")
    void validateValidationRejectsInvalidProfileNoteLengthLowerBound() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.ValidationConfig(
                        18,
                        50,
                        100,
                        220,
                        500,
                        500,
                        50,
                        Message.MAX_LENGTH,
                        0,
                        2,
                        1,
                        50,
                        10,
                        User.MAX_PHOTOS,
                        50,
                        15,
                        5,
                        10));
    }

    @Test
    @DisplayName("validateValidation rejects profile note length above hard cap")
    void validateValidationRejectsInvalidProfileNoteLengthUpperBound() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.ValidationConfig(
                        18,
                        50,
                        100,
                        220,
                        500,
                        500,
                        50,
                        Message.MAX_LENGTH,
                        ProfileNote.MAX_LENGTH + 1,
                        2,
                        1,
                        50,
                        10,
                        User.MAX_PHOTOS,
                        50,
                        15,
                        5,
                        10));
    }

    @Test
    @DisplayName("validateStorage accepts valid query timeout")
    void validateStorageAcceptsValidQueryTimeout() {
        assertDoesNotThrow(() -> AppConfigValidator.validateStorage(30));
    }

    @Test
    @DisplayName("validateStorage rejects out-of-range query timeout")
    void validateStorageRejectsInvalidQueryTimeout() {
        assertThrows(IllegalArgumentException.class, () -> AppConfigValidator.validateStorage(0));
    }

    @Test
    @DisplayName("validateAlgorithm rejects nearbyDistanceKm greater than closeDistanceKm")
    void validateAlgorithmRejectsInvalidDistanceOrdering() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.AlgorithmConfig(
                        100, 50, 2, 5, 50, 1, 24, 72, 168, 720, 3, 40, 90, 75, 60, 40, 0.20, 0.15, 0.25, 0.20, 0.10,
                        0.10));
    }

    @Test
    @DisplayName("validateAlgorithm rejects invalid standout policy ranges")
    void validateAlgorithmRejectsInvalidStandoutPolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.AlgorithmConfig(
                        5, 10, 2, 5, 50, 1, 24, 72, 168, 720, 0, 101, 90, 75, 60, 40, 0.20, 0.15, 0.25, 0.20, 0.10,
                        0.10));
    }

    @Test
    @DisplayName("validateAlgorithm accepts valid standout policy ranges")
    void validateAlgorithmAcceptsValidStandoutPolicy() {
        assertDoesNotThrow(() -> new AppConfig.AlgorithmConfig(
                5, 10, 2, 5, 50, 1, 24, 72, 168, 720, 3, 40, 90, 75, 60, 40, 0.20, 0.15, 0.25, 0.20, 0.10, 0.10));
    }

    @Test
    @DisplayName("validateAlgorithm rejects non-descending star thresholds")
    void validateAlgorithmRejectsNonDescendingStarThresholds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.AlgorithmConfig(
                        5, 10, 2, 5, 50, 1, 24, 72, 168, 720, 3, 40, 80, 85, 60, 40, 0.20, 0.15, 0.25, 0.20, 0.10,
                        0.10));
    }

    @Test
    @DisplayName("validateSafety accepts valid safety parameters")
    void validateSafetyAcceptsValidValues() {
        ZoneId utc = ZoneId.of("UTC");
        assertDoesNotThrow(
                () -> new AppConfig.SafetyConfig(3, utc, 30, 60, 1, 5, 10, 25, 50, 20, 0.35, 0.65, 100, 4, 30, 90));
    }
}
