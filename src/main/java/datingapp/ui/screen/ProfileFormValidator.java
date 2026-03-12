package datingapp.ui.screen;

import datingapp.core.AppClock;
import java.time.LocalDate;

final class ProfileFormValidator {

    String validateBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return birthDate.isAfter(AppClock.today()) ? "Birth date cannot be in the future." : null;
    }

    String validateHeight(String heightText, int minHeight, int maxHeight) {
        if (heightText == null || heightText.isBlank()) {
            return null;
        }
        try {
            int height = Integer.parseInt(heightText);
            if (height < minHeight || height > maxHeight) {
                return "Height must be between " + minHeight + " and " + maxHeight + " cm.";
            }
            return null;
        } catch (NumberFormatException _) {
            return "Height must be a whole number in centimeters.";
        }
    }

    String validateSearchPreferences(String minAgeText, String maxAgeText, String distanceText) {
        try {
            int minAge = Integer.parseInt(minAgeText.trim());
            int maxAge = Integer.parseInt(maxAgeText.trim());
            int distance = Integer.parseInt(distanceText.trim());
            if (minAge > maxAge) {
                return "Minimum age cannot be greater than maximum age.";
            }
            if (distance <= 0) {
                return "Distance must be greater than zero.";
            }
            return null;
        } catch (Exception _) {
            return "Search preferences must use whole numbers.";
        }
    }

    String validatePacePreferences(boolean anySet, boolean allSet) {
        return anySet && !allSet ? "Complete all four pace preferences to save them." : null;
    }
}
