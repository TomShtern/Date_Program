package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.ValidationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocationSelectionDialog")
class LocationSelectionDialogTest {

    private LocationService locationService;

    @BeforeEach
    void setUp() {
        locationService = new LocationService(new ValidationService(AppConfig.defaults()));
    }

    @Test
    @DisplayName("initial selection seeds known saved coordinates")
    void initialSelectionSeedsKnownSavedCoordinates() {
        LocationSelectionDialog.SeededSelection selection = LocationSelectionDialog.initialSelection(
                        locationService, 32.3215, 34.8532)
                .orElseThrow();

        assertEquals("IL", selection.country().code());
        assertTrue(selection.selectedCity().isPresent());
        assertEquals("Netanya", selection.selectedCity().orElseThrow().name());
        assertEquals("Netanya, Central District", selection.pendingLocation().label());
    }

    @Test
    @DisplayName("evaluation can use approximate fallback for unsupported ZIPs")
    void evaluationCanUseApproximateFallbackForUnsupportedZips() {
        LocationSelectionDialog.SelectionEvaluation evaluation = LocationSelectionDialog.evaluateSelection(
                locationService,
                locationService.getDefaultCountry(),
                Optional.empty(),
                "9999999",
                true,
                Optional.empty());

        assertTrue(evaluation.saveEnabled());
        assertTrue(evaluation.pendingLocation().isPresent());
        assertTrue(evaluation.usingApproximateFallback());
        assertTrue(evaluation.previewText().contains("Approximate"));
    }
}
