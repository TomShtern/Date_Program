package datingapp.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.model.LocationModels.Country;
import datingapp.core.model.LocationModels.Precision;
import datingapp.core.model.LocationModels.ResolvedLocation;
import datingapp.core.profile.GeocodingService;
import datingapp.core.profile.LocalGeocodingService;
import datingapp.core.profile.LocationService;
import datingapp.core.profile.ValidationService;
import datingapp.ui.JavaFxTestSupport;
import java.util.List;
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
    private LocalGeocodingService geocodingService;

    @BeforeAll
    static void initJfx() throws InterruptedException {
        JavaFxTestSupport.initJfx();
    }

    @BeforeEach
    void setUp() {
        locationService = new LocationService(new ValidationService(AppConfig.defaults()));
        geocodingService = new LocalGeocodingService(locationService);
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
                "",
                "9999999",
                true,
                Optional.empty());

        assertTrue(evaluation.saveEnabled());
        assertTrue(evaluation.pendingLocation().isPresent());
        assertTrue(evaluation.usingApproximateFallback());
        assertTrue(evaluation.previewText().contains("Approximate"));
    }

    @Test
    @DisplayName("selecting a local city geocoding result keeps the selection active")
    void selectingLocalCityGeocodingResultKeepsTheSelectionActive() throws Exception {
        ComboBox<Country> countryCombo = JavaFxTestSupport.callOnFxAndWait(ComboBox::new);
        TextField citySearchField = JavaFxTestSupport.callOnFxAndWait(TextField::new);
        ListView<GeocodingService.GeocodingResult> cityListView = JavaFxTestSupport.callOnFxAndWait(ListView::new);
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
            cityListView.getItems().setAll(geocodingService.search("Tel", 10));

            LocationSelectionDialog.DialogControls controls = new LocationSelectionDialog.DialogControls(
                    countryCombo,
                    citySearchField,
                    cityListView,
                    zipField,
                    approximateFallbackCheck,
                    previewLabel,
                    errorLabel,
                    confirmButton);
            LocationSelectionDialog.bindDialogInteractions(
                    locationService, geocodingService, controls, seededLocation, pendingLocation);
        });

        GeocodingService.GeocodingResult selectedCity =
                JavaFxTestSupport.callOnFxAndWait(() -> cityListView.getItems().stream()
                        .filter(city -> city.displayName().contains("Tel Aviv"))
                        .findFirst()
                        .orElseGet(() -> cityListView.getItems().getFirst()));

        JavaFxTestSupport.runOnFxAndWait(() -> cityListView.getSelectionModel().select(selectedCity));

        assertEquals(selectedCity, JavaFxTestSupport.callOnFxAndWait(() -> cityListView
                .getSelectionModel()
                .getSelectedItem()));
        assertFalse(JavaFxTestSupport.callOnFxAndWait(confirmButton::isDisabled));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(previewLabel::getText).contains("Tel Aviv"));
        assertNotNull(pendingLocation.get());
        assertEquals(Precision.CITY, pendingLocation.get().precision());
    }

    @Test
    @DisplayName("searching and selecting an address-level geocode result updates the pending location")
    void searchingAndSelectingAddressLevelGeocodeResultUpdatesPendingLocation() throws Exception {
        ComboBox<Country> countryCombo = JavaFxTestSupport.callOnFxAndWait(ComboBox::new);
        TextField citySearchField = JavaFxTestSupport.callOnFxAndWait(TextField::new);
        ListView<GeocodingService.GeocodingResult> cityListView = JavaFxTestSupport.callOnFxAndWait(ListView::new);
        TextField zipField = JavaFxTestSupport.callOnFxAndWait(TextField::new);
        CheckBox approximateFallbackCheck = JavaFxTestSupport.callOnFxAndWait(CheckBox::new);
        Label previewLabel = JavaFxTestSupport.callOnFxAndWait(() -> new Label(""));
        Label errorLabel = JavaFxTestSupport.callOnFxAndWait(Label::new);
        Button confirmButton = JavaFxTestSupport.callOnFxAndWait(Button::new);
        AtomicReference<ResolvedLocation> seededLocation = new AtomicReference<>();
        AtomicReference<ResolvedLocation> pendingLocation = new AtomicReference<>();
        GeocodingService fakeGeocodingService = (query, maxResults) -> query != null && query.contains("Rothschild")
                ? List.of(new GeocodingService.GeocodingResult(
                        "Rothschild Boulevard, Tel Aviv-Yafo, Israel", 32.0651, 34.7778, Precision.ADDRESS))
                : List.of();

        JavaFxTestSupport.runOnFxAndWait(() -> {
            countryCombo.getItems().setAll(locationService.getAvailableCountries());
            countryCombo.setValue(locationService.getDefaultCountry());

            LocationSelectionDialog.DialogControls controls = new LocationSelectionDialog.DialogControls(
                    countryCombo,
                    citySearchField,
                    cityListView,
                    zipField,
                    approximateFallbackCheck,
                    previewLabel,
                    errorLabel,
                    confirmButton);
            LocationSelectionDialog.bindDialogInteractions(
                    locationService, fakeGeocodingService, controls, seededLocation, pendingLocation);
            citySearchField.setText("Rothschild");
        });

        assertTrue(JavaFxTestSupport.waitUntil(
                () -> {
                    try {
                        return !JavaFxTestSupport.callOnFxAndWait(cityListView.getItems()::isEmpty);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                },
                5000));

        GeocodingService.GeocodingResult selectedResult =
                JavaFxTestSupport.callOnFxAndWait(() -> cityListView.getItems().getFirst());
        JavaFxTestSupport.runOnFxAndWait(() -> cityListView.getSelectionModel().select(selectedResult));

        assertFalse(JavaFxTestSupport.callOnFxAndWait(confirmButton::isDisabled));
        assertTrue(JavaFxTestSupport.callOnFxAndWait(previewLabel::getText).contains("Rothschild Boulevard"));
        assertEquals(Precision.ADDRESS, pendingLocation.get().precision());
    }
}
