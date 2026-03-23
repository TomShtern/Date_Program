package datingapp.ui.screen;

import datingapp.core.AppClock;
import datingapp.core.profile.ValidationService;
import java.time.LocalDate;
import java.util.Objects;

final class ProfileFormValidator {

    private final ValidationService validationService;

    ProfileFormValidator(ValidationService validationService) {
        this.validationService = Objects.requireNonNull(validationService, "validationService cannot be null");
    }

    String validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }

        var result = validationService.validateBirthDate(birthDate);
        if (result.valid()) {
            return null;
        }

        if (birthDate.isAfter(AppClock.today())) {
            return "Birth date cannot be in the future.";
        }
        return result.errors().isEmpty()
                ? "Invalid birth date."
                : result.errors().getFirst();
    }

    String validateHeight(String heightText, int minHeight, int maxHeight) {
        if (heightText == null || heightText.isBlank()) {
            return null;
        }

        final int height;
        try {
            height = Integer.parseInt(heightText.trim());
        } catch (NumberFormatException _) {
            return "Height must be a whole number in centimeters.";
        }

        var result = validationService.validateHeight(height);
        return result.valid() ? null : "Height must be between " + minHeight + " and " + maxHeight + " cm.";
    }

    String validateSearchPreferences(String minAgeText, String maxAgeText, String distanceText) {
        final int minAge;
        final int maxAge;
        final int distance;

        try {
            minAge = Integer.parseInt(minAgeText.trim());
            maxAge = Integer.parseInt(maxAgeText.trim());
            distance = Integer.parseInt(distanceText.trim());
        } catch (Exception _) {
            return "Search preferences must use whole numbers.";
        }

        var ageResult = validationService.validateAgeRange(minAge, maxAge);
        if (!ageResult.valid()) {
            return ageResult.errors().isEmpty()
                    ? "Invalid age range."
                    : ageResult.errors().getFirst();
        }

        var distanceResult = validationService.validateDistance(distance);
        if (!distanceResult.valid()) {
            return distanceResult.errors().isEmpty()
                    ? "Distance must be greater than zero."
                    : distanceResult.errors().getFirst();
        }

        return null;
    }

    String validatePacePreferences(boolean anySet, boolean allSet) {
        return anySet && !allSet ? "Complete all four pace preferences to save them." : null;
    }
}
