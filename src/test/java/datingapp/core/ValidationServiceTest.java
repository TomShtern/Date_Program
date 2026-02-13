package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.profile.*;
import datingapp.core.profile.ValidationService.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for ValidationService. */
class ValidationServiceTest {

    private ValidationService validator;

    @BeforeEach
    void setUp() {
        validator = new ValidationService();
    }

    @Nested
    @DisplayName("Name Validation")
    class NameValidation {

        @Test
        @DisplayName("Valid name passes validation")
        void validName() {
            ValidationResult result = validator.validateName("John Doe");
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("Null name fails validation")
        void nullName() {
            ValidationResult result = validator.validateName(null);
            assertFalse(result.valid());
            assertEquals(1, result.errors().size());
            assertEquals("Name cannot be empty", result.errors().get(0));
        }

        @Test
        @DisplayName("Blank name fails validation")
        void blankName() {
            ValidationResult result = validator.validateName("   ");
            assertFalse(result.valid());
            assertEquals("Name cannot be empty", result.errors().get(0));
        }

        @Test
        @DisplayName("Name over 100 chars fails validation")
        void nameTooLong() {
            String longName = "a".repeat(101);
            ValidationResult result = validator.validateName(longName);
            assertFalse(result.valid());
            assertEquals("Name too long (max 100 chars)", result.errors().get(0));
        }

        @Test
        @DisplayName("Name exactly 100 chars passes validation")
        void nameAtLimit() {
            String name = "a".repeat(100);
            ValidationResult result = validator.validateName(name);
            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("Age Validation")
    class AgeValidation {

        @Test
        @DisplayName("Valid age passes validation")
        void validAge() {
            ValidationResult result = validator.validateAge(25);
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("Age 18 passes validation")
        void minimumAge() {
            ValidationResult result = validator.validateAge(18);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Age under 18 fails validation")
        void underAge() {
            ValidationResult result = validator.validateAge(17);
            assertFalse(result.valid());
            assertEquals("Must be 18 or older", result.errors().get(0));
        }

        @Test
        @DisplayName("Age 120 passes validation")
        void maximumAge() {
            ValidationResult result = validator.validateAge(120);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Age over 120 fails validation")
        void overMaxAge() {
            ValidationResult result = validator.validateAge(121);
            assertFalse(result.valid());
            assertEquals("Invalid age", result.errors().get(0));
        }
    }

    @Nested
    @DisplayName("Height Validation")
    class HeightValidation {

        @Test
        @DisplayName("Valid height passes validation")
        void validHeight() {
            ValidationResult result = validator.validateHeight(175);
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("Height 50cm passes validation")
        void minimumHeight() {
            ValidationResult result = validator.validateHeight(50);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Height under 50cm fails validation")
        void heightTooShort() {
            ValidationResult result = validator.validateHeight(49);
            assertFalse(result.valid());
            assertEquals("Height too short (min 50cm)", result.errors().get(0));
        }

        @Test
        @DisplayName("Height 300cm passes validation")
        void maximumHeight() {
            ValidationResult result = validator.validateHeight(300);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Height over 300cm fails validation")
        void heightTooTall() {
            ValidationResult result = validator.validateHeight(301);
            assertFalse(result.valid());
            assertEquals("Height too tall (max 300cm)", result.errors().get(0));
        }
    }

    @Nested
    @DisplayName("Distance Validation")
    class DistanceValidation {

        @Test
        @DisplayName("Valid distance passes validation")
        void validDistance() {
            ValidationResult result = validator.validateDistance(50);
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("Distance 1km passes validation")
        void minimumDistance() {
            ValidationResult result = validator.validateDistance(1);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Distance 0km fails validation")
        void zeroDistance() {
            ValidationResult result = validator.validateDistance(0);
            assertFalse(result.valid());
            assertEquals("Distance must be at least 1km", result.errors().get(0));
        }

        @Test
        @DisplayName("Distance 500km passes validation")
        void maximumDistance() {
            ValidationResult result = validator.validateDistance(500);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Distance over 500km fails validation")
        void distanceTooFar() {
            ValidationResult result = validator.validateDistance(501);
            assertFalse(result.valid());
            assertEquals("Distance too far (max 500km)", result.errors().get(0));
        }

        @Test
        @DisplayName("Negative distance fails validation")
        void negativeDistance() {
            ValidationResult result = validator.validateDistance(-10);
            assertFalse(result.valid());
            assertEquals("Distance must be at least 1km", result.errors().get(0));
        }
    }

    @Nested
    @DisplayName("Bio Validation")
    class BioValidation {

        @Test
        @DisplayName("Valid bio passes validation")
        void validBio() {
            ValidationResult result = validator.validateBio("I love hiking and coffee.");
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("Null bio passes validation")
        void nullBio() {
            ValidationResult result = validator.validateBio(null);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Empty bio passes validation")
        void emptyBio() {
            ValidationResult result = validator.validateBio("");
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Bio at max limit passes validation")
        void bioAtLimit() {
            int maxBio = AppConfig.defaults().maxBioLength();
            String bio = "a".repeat(maxBio);
            ValidationResult result = validator.validateBio(bio);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Bio over max limit fails validation")
        void bioTooLong() {
            int maxBio = AppConfig.defaults().maxBioLength();
            String bio = "a".repeat(maxBio + 1);
            ValidationResult result = validator.validateBio(bio);
            assertFalse(result.valid());
            assertEquals(
                    "Bio too long (max " + maxBio + " chars)", result.errors().get(0));
        }
    }

    @Nested
    @DisplayName("Age Range Validation")
    class AgeRangeValidation {

        @Test
        @DisplayName("Valid age range passes validation")
        void validAgeRange() {
            ValidationResult result = validator.validateAgeRange(25, 35);
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("Age range exactly 5 years passes validation")
        void minimumRangeWidth() {
            ValidationResult result = validator.validateAgeRange(25, 30);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Age range less than 5 years fails validation")
        void rangeTooNarrow() {
            ValidationResult result = validator.validateAgeRange(25, 29);
            assertFalse(result.valid());
            assertTrue(result.errors().contains("Age range too narrow (min 5 years)"));
        }

        @Test
        @DisplayName("Min age under 18 fails validation")
        void minAgeTooLow() {
            ValidationResult result = validator.validateAgeRange(17, 30);
            assertFalse(result.valid());
            assertTrue(result.errors().contains("Minimum age must be 18+"));
        }

        @Test
        @DisplayName("Max age over 120 fails validation")
        void maxAgeTooHigh() {
            ValidationResult result = validator.validateAgeRange(18, 121);
            assertFalse(result.valid());
            assertTrue(result.errors().contains("Maximum age invalid"));
        }

        @Test
        @DisplayName("Min greater than max fails validation")
        void minGreaterThanMax() {
            ValidationResult result = validator.validateAgeRange(40, 35);
            assertFalse(result.valid());
            assertTrue(result.errors().contains("Min age cannot exceed max age"));
        }

        @Test
        @DisplayName("Multiple errors reported together")
        void multipleErrors() {
            ValidationResult result = validator.validateAgeRange(17, 121);
            assertFalse(result.valid());
            assertTrue(result.errors().size() > 1);
            assertTrue(result.errors().contains("Minimum age must be 18+"));
            assertTrue(result.errors().contains("Maximum age invalid"));
        }
    }

    @Nested
    @DisplayName("Location Validation")
    class LocationValidation {

        @Test
        @DisplayName("Valid coordinates pass validation")
        void validLocation() {
            ValidationResult result = validator.validateLocation(32.0853, 34.7818);
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("Latitude at -90 passes validation")
        void minLatitude() {
            ValidationResult result = validator.validateLocation(-90, 0);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Latitude at 90 passes validation")
        void maxLatitude() {
            ValidationResult result = validator.validateLocation(90, 0);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Latitude below -90 fails validation")
        void latitudeTooLow() {
            ValidationResult result = validator.validateLocation(-91, 0);
            assertFalse(result.valid());
            assertTrue(result.errors().contains("Invalid latitude (must be -90 to 90)"));
        }

        @Test
        @DisplayName("Latitude above 90 fails validation")
        void latitudeTooHigh() {
            ValidationResult result = validator.validateLocation(91, 0);
            assertFalse(result.valid());
            assertTrue(result.errors().contains("Invalid latitude (must be -90 to 90)"));
        }

        @Test
        @DisplayName("Longitude at -180 passes validation")
        void minLongitude() {
            ValidationResult result = validator.validateLocation(0, -180);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Longitude at 180 passes validation")
        void maxLongitude() {
            ValidationResult result = validator.validateLocation(0, 180);
            assertTrue(result.valid());
        }

        @Test
        @DisplayName("Longitude below -180 fails validation")
        void longitudeTooLow() {
            ValidationResult result = validator.validateLocation(0, -181);
            assertFalse(result.valid());
            assertTrue(result.errors().contains("Invalid longitude (must be -180 to 180)"));
        }

        @Test
        @DisplayName("Longitude above 180 fails validation")
        void longitudeTooHigh() {
            ValidationResult result = validator.validateLocation(0, 181);
            assertFalse(result.valid());
            assertTrue(result.errors().contains("Invalid longitude (must be -180 to 180)"));
        }

        @Test
        @DisplayName("Multiple location errors reported together")
        void multipleLocationErrors() {
            ValidationResult result = validator.validateLocation(100, 200);
            assertFalse(result.valid());
            assertEquals(2, result.errors().size());
            assertTrue(result.errors().contains("Invalid latitude (must be -90 to 90)"));
            assertTrue(result.errors().contains("Invalid longitude (must be -180 to 180)"));
        }
    }
}
