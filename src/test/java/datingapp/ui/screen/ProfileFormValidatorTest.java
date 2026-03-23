package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.profile.ValidationService;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileFormValidatorTest {

    private final ProfileFormValidator validator =
            new ProfileFormValidator(new ValidationService(AppConfig.defaults()));

    @Test
    @DisplayName("validateBirthDate rejects future date")
    void validateBirthDateRejectsFutureDate() {
        LocalDate futureDate = AppClock.today().plusDays(1);

        assertEquals("Birth date cannot be in the future.", validator.validateBirthDate(futureDate));
    }

    @Test
    @DisplayName("validateHeight keeps UI parse errors in the controller layer")
    void validateHeightRejectsNonNumericInput() {
        assertEquals("Height must be a whole number in centimeters.", validator.validateHeight("abc", 120, 230));
    }

    @Test
    @DisplayName("validateHeight delegates bounds rule to ValidationService")
    void validateHeightDelegatesBoundsValidation() {
        assertEquals("Height must be between 120 and 230 cm.", validator.validateHeight("10", 120, 230));
        assertNull(validator.validateHeight("180", 120, 230));
    }

    @Test
    @DisplayName("validateSearchPreferences keeps number parsing in UI layer")
    void validateSearchPreferencesRejectsNonNumeric() {
        assertEquals(
                "Search preferences must use whole numbers.", validator.validateSearchPreferences("18", "abc", "25"));
    }

    @Test
    @DisplayName("validateSearchPreferences delegates business rules to ValidationService")
    void validateSearchPreferencesDelegatesBusinessRules() {
        assertEquals("Min age cannot exceed max age", validator.validateSearchPreferences("40", "20", "10"));
        assertEquals("Distance must be at least 1km", validator.validateSearchPreferences("20", "40", "0"));
    }

    @Test
    @DisplayName("validatePacePreferences remains UI-level completeness check")
    void validatePacePreferencesBehavior() {
        assertEquals(
                "Complete all four pace preferences to save them.", validator.validatePacePreferences(true, false));
        assertNull(validator.validatePacePreferences(false, false));
        assertNull(validator.validatePacePreferences(true, true));
    }
}
