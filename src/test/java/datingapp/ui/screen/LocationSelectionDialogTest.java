package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.model.LocationModels.City;
import datingapp.core.model.LocationModels.Country;
import datingapp.core.model.LocationModels.ResolvedLocation;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.ValidationService;
import datingapp.ui.JavaFxTestSupport;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocationSelectionDialog")
class LocationSelectionDialogTest {

    private LocationService locationService;

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

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

    @Test
    @DisplayName("selecting a city keeps the selection active")
    void selectingCityKeepsTheSelectionActive() throws Exception {
        ComboBox<Country> countryCombo = JavaFxTestSupport.callOnFxAndWait(ComboBox::new);
        TextField citySearchField = JavaFxTestSupport.callOnFxAndWait(TextField::new);
        ListView<City> cityListView = JavaFxTestSupport.callOnFxAndWait(ListView::new);
        TextField zipField = JavaFxTestSupport.callOnFxAndWait(TextField::new);
        CheckBox approximateFallbackCheck = JavaFxTestSupport.callOnFxAndWait(CheckBox::new);
        Label previewLabel = JavaFxTestSupport.callOnFxAndWait(() -> new Label(""));
        Label errorLabel = JavaFxTestSupport.callOnFxAndWait(Label::new);
        Button confirmButton = JavaFxTestSupport.callOnFxAndWait(Button::new);
        AtomicReference<ResolvedLocation> seededLocation = new AtomicReference<>();
        AtomicReference<ResolvedLocation> pendingLocation = new AtomicReference<>();

        JavaFxTestSupport.runOnFxAndWait(() -> {
            countryCombo.getItems().setAll(locationService.getAvailableCountries());
            countryCombo.setValue(locationService.getDefaultCountry());
            cityListView
                    .getItems()
                    .setAll(locationService.searchCities(countryCombo.getValue().code(), "Tel", 10));

            LocationSelectionDialog.DialogControls controls = new LocationSelectionDialog.DialogControls(
                    countryCombo,
                    citySearchField,
                    cityListView,
                    zipField,
                    approximateFallbackCheck,
                    previewLabel,
                    errorLabel,
                    confirmButton);
            LocationSelectionDialog.bindDialogInteractions(locationService, controls, seededLocation, pendingLocation);
        });

        City selectedCity = JavaFxTestSupport.callOnFxAndWait(() -> cityListView.getItems().stream()
                .filter(city -> "Tel Aviv".equals(city.name()))
                .findFirst()
                .orElseGet(() -> cityListView.getItems().getFirst()));

        JavaFxTestSupport.runOnFxAndWait(() -> cityListView.getSelectionModel().select(selectedCity));

        assertEquals(selectedCity, JavaFxTestSupport.callOnFxAndWait(() -> cityListView
                .getSelectionModel()
                .getSelectedItem()));
        assertEquals(selectedCity.name(), JavaFxTestSupport.callOnFxAndWait(citySearchField::getText));
        assertFalse(JavaFxTestSupport.callOnFxAndWait(confirmButton::isDisabled));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(previewLabel::getText).contains(selectedCity.name()));
        assertNotNull(pendingLocation.get());
    }
}
