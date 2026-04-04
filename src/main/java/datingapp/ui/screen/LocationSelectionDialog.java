package datingapp.ui.screen;

import datingapp.core.model.LocationModels.City;
import datingapp.core.model.LocationModels.Country;
import datingapp.core.model.LocationModels.Precision;
import datingapp.core.model.LocationModels.ResolvedLocation;
import datingapp.core.profile.LocationService;
import datingapp.ui.UiConstants;
import datingapp.ui.UiUtils;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Shared JavaFX dialog for human-friendly location selection. */
public final class LocationSelectionDialog {

    private static final String LOCATION_SERVICE_REQUIRED = "locationService cannot be null";
    private static final String PENDING_LOCATION_REQUIRED = "pendingLocation cannot be null";

    private LocationSelectionDialog() {}

    public static Optional<ResolvedLocation> show(
            Node ownerNode,
            LocationService locationService,
            boolean hasCurrentLocation,
            double latitude,
            double longitude) {
        Objects.requireNonNull(locationService, LOCATION_SERVICE_REQUIRED);

        Optional<SeededSelection> seed =
                hasCurrentLocation ? initialSelection(locationService, latitude, longitude) : Optional.empty();
        Optional<ResolvedLocation> currentLocation = seed.map(SeededSelection::pendingLocation);
        if (currentLocation.isEmpty() && hasCurrentLocation) {
            Precision precision = locationService
                    .reverseLookup(latitude, longitude)
                    .map(ResolvedLocation::precision)
                    .orElse(Precision.CITY);
            currentLocation = Optional.of(new ResolvedLocation(
                    latitude, longitude, locationService.formatForDisplay(latitude, longitude), precision));
        }
        AtomicReference<ResolvedLocation> seededLocation = new AtomicReference<>(currentLocation.orElse(null));

        Dialog<ButtonType> dialog =
                UiUtils.createThemedDialog(ownerNode, "Set Location", "Choose where you want to discover people");

        VBox content = new VBox(UiConstants.SPACING_LARGE);
        content.setPadding(new Insets(UiConstants.PADDING_XLARGE));

        Label helperLabel = UiUtils.createSecondaryLabel("Choose a country, then select a city or enter a ZIP code.");
        Label exampleLabel = UiUtils.createSecondaryLabel(
                "We use city and ZIP-based labels for discovery and keep coordinates as an internal detail.");
        Label currentLocationLabel = UiUtils.createSecondaryLabel(currentLocation
                .map(location -> "Current location: " + location.label())
                .orElse("Current location: not set yet"));

        ComboBox<Country> countryCombo = createCountryCombo(
                locationService, seed.map(SeededSelection::country).orElseGet(locationService::getDefaultCountry));

        TextField citySearchField = new TextField();
        citySearchField.setPromptText("Search city (for example, Tel Aviv)");

        ListView<City> cityListView = createCityListView();
        TextField zipField = new TextField();
        zipField.setPromptText("Israeli ZIP code (7 digits)");

        CheckBox approximateFallbackCheck =
                new CheckBox("Use an approximate supported area if this ZIP is not supported yet");
        approximateFallbackCheck.setWrapText(true);
        approximateFallbackCheck.getStyleClass().add("text-secondary");

        Label previewLabel = UiUtils.createSecondaryLabel("");
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("dialog-error-label");
        UiUtils.setLabelMessage(errorLabel, null);

        content.getChildren()
                .addAll(
                        helperLabel,
                        exampleLabel,
                        currentLocationLabel,
                        new Label("Country"),
                        countryCombo,
                        new Label("City"),
                        citySearchField,
                        cityListView,
                        new Label("ZIP code (optional fallback)"),
                        zipField,
                        approximateFallbackCheck,
                        previewLabel,
                        errorLabel);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        confirmButton.setText("Save Location");
        DialogControls controls = new DialogControls(
                countryCombo,
                citySearchField,
                cityListView,
                zipField,
                approximateFallbackCheck,
                previewLabel,
                errorLabel,
                confirmButton);

        if (seed.isPresent() && seed.orElseThrow().selectedCity().isPresent()) {
            City selectedCity = seed.orElseThrow().selectedCity().orElseThrow();
            controls.citySearchField().setText(selectedCity.name());
            controls.cityListView()
                    .getItems()
                    .setAll(locationService.searchCities(countryCombo.getValue().code(), selectedCity.name(), 10));
            controls.cityListView().getSelectionModel().select(selectedCity);
        } else {
            controls.cityListView()
                    .getItems()
                    .setAll(locationService.getPopularCities(
                            countryCombo.getValue().code(), 10));
        }

        AtomicReference<ResolvedLocation> pendingLocation = new AtomicReference<>(seededLocation.get());
        bindDialogInteractions(locationService, controls, seededLocation, pendingLocation);

        confirmButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (pendingLocation.get() == null) {
                UiUtils.setLabelMessage(
                        controls.errorLabel(), "Please choose a supported city or ZIP option before saving.");
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.orElseThrow() == ButtonType.OK && pendingLocation.get() != null) {
            return Optional.of(pendingLocation.get());
        }
        return Optional.empty();
    }

    static void bindDialogInteractions(
            LocationService locationService,
            DialogControls controls,
            AtomicReference<ResolvedLocation> seededLocation,
            AtomicReference<ResolvedLocation> pendingLocation) {
        Objects.requireNonNull(locationService, LOCATION_SERVICE_REQUIRED);
        Objects.requireNonNull(controls, "controls cannot be null");
        Objects.requireNonNull(seededLocation, "seededLocation cannot be null");
        Objects.requireNonNull(pendingLocation, PENDING_LOCATION_REQUIRED);

        Runnable refreshCitySuggestions = () -> refreshCitySuggestions(locationService, controls);
        Runnable refreshEvaluation =
                () -> refreshEvaluation(locationService, controls, seededLocation, pendingLocation);

        bindCountrySelection(locationService, controls, seededLocation, refreshCitySuggestions, refreshEvaluation);
        bindCitySearch(controls, seededLocation, refreshCitySuggestions, refreshEvaluation);
        bindCitySelection(controls, seededLocation, refreshEvaluation);
        bindZipChanges(controls, seededLocation, refreshEvaluation);
        controls.approximateFallbackCheck()
                .selectedProperty()
                .addListener((obs, oldVal, newVal) -> refreshEvaluation.run());

        refreshEvaluation.run();
    }

    private static void refreshCitySuggestions(LocationService locationService, DialogControls controls) {
        Country selectedCountry = controls.countryCombo().getValue();
        if (selectedCountry == null) {
            controls.cityListView().getItems().clear();
            return;
        }
        controls.cityListView()
                .getItems()
                .setAll(locationService.searchCities(
                        selectedCountry.code(), controls.citySearchField().getText(), 10));
    }

    private static void refreshEvaluation(
            LocationService locationService,
            DialogControls controls,
            AtomicReference<ResolvedLocation> seededLocation,
            AtomicReference<ResolvedLocation> pendingLocation) {
        SelectionEvaluation evaluation = evaluateSelection(
                locationService,
                controls.countryCombo().getValue(),
                Optional.ofNullable(controls.cityListView().getSelectionModel().getSelectedItem()),
                controls.zipField().getText(),
                controls.approximateFallbackCheck().isSelected(),
                Optional.ofNullable(seededLocation.get()));
        pendingLocation.set(evaluation.pendingLocation().orElse(null));
        controls.previewLabel().setText(evaluation.previewText());
        UiUtils.setLabelMessage(controls.errorLabel(), evaluation.errorText());
        controls.confirmButton().setDisable(!evaluation.saveEnabled());
    }

    private static void bindCountrySelection(
            LocationService locationService,
            DialogControls controls,
            AtomicReference<ResolvedLocation> seededLocation,
            Runnable refreshCitySuggestions,
            Runnable refreshEvaluation) {
        controls.countryCombo().valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.available()) {
                controls.countryCombo()
                        .getSelectionModel()
                        .select(oldVal != null ? oldVal : locationService.getDefaultCountry());
                UiUtils.setLabelMessage(
                        controls.errorLabel(), newVal.name() + " is coming soon. Please choose Israel for now.");
                return;
            }
            seededLocation.set(null);
            controls.citySearchField().clear();
            controls.zipField().clear();
            controls.cityListView().getSelectionModel().clearSelection();
            refreshCitySuggestions.run();
            refreshEvaluation.run();
        });
    }

    private static void bindCitySearch(
            DialogControls controls,
            AtomicReference<ResolvedLocation> seededLocation,
            Runnable refreshCitySuggestions,
            Runnable refreshEvaluation) {
        controls.citySearchField().textProperty().addListener((obs, oldVal, newVal) -> {
            seededLocation.set(null);
            if (!matchesSelectedCitySearch(controls.cityListView(), newVal)) {
                controls.cityListView().getSelectionModel().clearSelection();
                refreshCitySuggestions.run();
            }
            refreshEvaluation.run();
        });
    }

    private static void bindCitySelection(
            DialogControls controls, AtomicReference<ResolvedLocation> seededLocation, Runnable refreshEvaluation) {
        controls.cityListView().getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            seededLocation.set(null);
            if (newVal != null) {
                if (!Objects.equals(controls.citySearchField().getText(), newVal.name())) {
                    controls.citySearchField().setText(newVal.name());
                }
                controls.zipField().clear();
            }
            refreshEvaluation.run();
        });
    }

    private static void bindZipChanges(
            DialogControls controls, AtomicReference<ResolvedLocation> seededLocation, Runnable refreshEvaluation) {
        controls.zipField().textProperty().addListener((obs, oldVal, newVal) -> {
            seededLocation.set(null);
            if (newVal != null && !newVal.isBlank()) {
                controls.cityListView().getSelectionModel().clearSelection();
            }
            refreshEvaluation.run();
        });
    }

    private static boolean matchesSelectedCitySearch(ListView<City> cityListView, String citySearchText) {
        City selectedCity = cityListView.getSelectionModel().getSelectedItem();
        return selectedCity != null
                && Objects.equals(selectedCity.name(), citySearchText == null ? "" : citySearchText.trim());
    }

    static Optional<SeededSelection> initialSelection(
            LocationService locationService, double latitude, double longitude) {
        Objects.requireNonNull(locationService, LOCATION_SERVICE_REQUIRED);
        return locationService
                .seedSelection(latitude, longitude)
                .map(seed -> new SeededSelection(
                        seed.country(), seed.city(), seed.zipPrefix().orElse(""), seed.resolvedLocation()));
    }

    static SelectionEvaluation evaluateSelection(
            LocationService locationService,
            Country selectedCountry,
            Optional<City> selectedCity,
            String zipText,
            boolean useApproximateFallback,
            Optional<ResolvedLocation> existingLocation) {
        Objects.requireNonNull(locationService, LOCATION_SERVICE_REQUIRED);
        Objects.requireNonNull(selectedCity, "selectedCity cannot be null");
        Objects.requireNonNull(existingLocation, "existingLocation cannot be null");

        if (selectedCountry == null) {
            return SelectionEvaluation.invalid(
                    "Choose a supported country first.", "Choose a supported country first.");
        }
        if (!selectedCountry.available()) {
            return SelectionEvaluation.invalid(
                    "This country will be available in a future update.",
                    "This country is coming soon. Please choose Israel for now.");
        }
        if (selectedCity.isPresent()) {
            ResolvedLocation resolvedLocation = locationService.resolveCity(selectedCity.orElseThrow());
            return SelectionEvaluation.selected(resolvedLocation, "Selected city: " + resolvedLocation.label(), false);
        }

        String normalizedZip = zipText == null ? "" : zipText.trim();
        if (normalizedZip.isBlank()) {
            return existingLocation
                    .map(location ->
                            SelectionEvaluation.selected(location, "Current selection: " + location.label(), false))
                    .orElseGet(() -> SelectionEvaluation.invalid(
                            "Search for a city or enter a ZIP code.", "Search for a city or enter a ZIP code."));
        }

        LocationService.ZipLookupResult lookupResult = locationService.lookupZip(selectedCountry.code(), normalizedZip);
        if (!lookupResult.valid()) {
            return SelectionEvaluation.invalid("Enter a valid supported ZIP code to continue.", lookupResult.message());
        }
        if (lookupResult.resolvedLocation().isPresent()) {
            ResolvedLocation resolvedLocation = lookupResult.resolvedLocation().orElseThrow();
            return SelectionEvaluation.selected(resolvedLocation, "ZIP preview: " + resolvedLocation.label(), false);
        }
        if (useApproximateFallback) {
            LocationService.ResolveSelectionResult approximateResult =
                    locationService.resolveSelection(selectedCountry.code(), null, normalizedZip, true);
            if (approximateResult.valid()
                    && approximateResult.resolvedLocation().isPresent()) {
                ResolvedLocation resolvedLocation =
                        approximateResult.resolvedLocation().orElseThrow();
                return SelectionEvaluation.selected(
                        resolvedLocation,
                        "Approximate ZIP fallback: " + resolvedLocation.label(),
                        approximateResult.approximate());
            }
            return SelectionEvaluation.invalid("ZIP fallback is unavailable right now.", approximateResult.message());
        }
        return SelectionEvaluation.invalid(
                "ZIP format is valid, but this area is not supported yet.", lookupResult.message());
    }

    private static ComboBox<Country> createCountryCombo(LocationService locationService, Country selectedCountry) {
        ComboBox<Country> countryCombo =
                new ComboBox<>(FXCollections.observableArrayList(locationService.getAvailableCountries()));
        countryCombo.setMaxWidth(Double.MAX_VALUE);
        countryCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Country country) {
                return country == null ? "" : country.displayName();
            }

            @Override
            public Country fromString(String string) {
                return null;
            }
        });
        countryCombo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Country item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setDisable(false);
                    return;
                }
                setText(item.displayName());
                setDisable(!item.available());
            }
        });
        countryCombo.getSelectionModel().select(selectedCountry);
        return countryCombo;
    }

    private static ListView<City> createCityListView() {
        ListView<City> cityListView = new ListView<>();
        cityListView.setPrefHeight(180);
        cityListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(City item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        return cityListView;
    }

    record SeededSelection(
            Country country, Optional<City> selectedCity, String zipText, ResolvedLocation pendingLocation) {
        SeededSelection {
            Objects.requireNonNull(country, "country cannot be null");
            Objects.requireNonNull(selectedCity, "selectedCity cannot be null");
            zipText = zipText == null ? "" : zipText;
            Objects.requireNonNull(pendingLocation, PENDING_LOCATION_REQUIRED);
        }
    }

    record DialogControls(
            ComboBox<Country> countryCombo,
            TextField citySearchField,
            ListView<City> cityListView,
            TextField zipField,
            CheckBox approximateFallbackCheck,
            Label previewLabel,
            Label errorLabel,
            Button confirmButton) {
        DialogControls {
            Objects.requireNonNull(countryCombo, "countryCombo cannot be null");
            Objects.requireNonNull(citySearchField, "citySearchField cannot be null");
            Objects.requireNonNull(cityListView, "cityListView cannot be null");
            Objects.requireNonNull(zipField, "zipField cannot be null");
            Objects.requireNonNull(approximateFallbackCheck, "approximateFallbackCheck cannot be null");
            Objects.requireNonNull(previewLabel, "previewLabel cannot be null");
            Objects.requireNonNull(errorLabel, "errorLabel cannot be null");
            Objects.requireNonNull(confirmButton, "confirmButton cannot be null");
        }
    }

    record SelectionEvaluation(
            Optional<ResolvedLocation> pendingLocation,
            String previewText,
            String errorText,
            boolean saveEnabled,
            boolean usingApproximateFallback) {
        SelectionEvaluation {
            Objects.requireNonNull(pendingLocation, PENDING_LOCATION_REQUIRED);
            previewText = previewText == null ? "" : previewText;
            errorText = errorText == null ? "" : errorText;
        }

        static SelectionEvaluation selected(
                ResolvedLocation location, String previewText, boolean usingApproximateFallback) {
            return new SelectionEvaluation(Optional.of(location), previewText, "", true, usingApproximateFallback);
        }

        static SelectionEvaluation invalid(String previewText, String errorText) {
            return new SelectionEvaluation(Optional.empty(), previewText, errorText, false, false);
        }
    }
}
