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

    private LocationSelectionDialog() {}

    public static Optional<ResolvedLocation> show(
            Node ownerNode,
            LocationService locationService,
            boolean hasCurrentLocation,
            double latitude,
            double longitude) {
        Objects.requireNonNull(locationService, "locationService cannot be null");

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
        ResolvedLocation[] seededLocation = new ResolvedLocation[] {currentLocation.orElse(null)};

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

        if (seed.isPresent() && seed.orElseThrow().selectedCity().isPresent()) {
            City selectedCity = seed.orElseThrow().selectedCity().orElseThrow();
            citySearchField.setText(selectedCity.name());
            cityListView
                    .getItems()
                    .setAll(locationService.searchCities(countryCombo.getValue().code(), selectedCity.name(), 10));
            cityListView.getSelectionModel().select(selectedCity);
        } else {
            cityListView
                    .getItems()
                    .setAll(locationService.getPopularCities(
                            countryCombo.getValue().code(), 10));
        }

        ResolvedLocation[] pendingLocation = new ResolvedLocation[] {seededLocation[0]};

        Runnable refreshCitySuggestions = () -> {
            Country selectedCountry = countryCombo.getValue();
            if (selectedCountry == null) {
                cityListView.getItems().clear();
                return;
            }
            cityListView
                    .getItems()
                    .setAll(locationService.searchCities(selectedCountry.code(), citySearchField.getText(), 10));
        };
        Runnable refreshEvaluation = () -> {
            SelectionEvaluation evaluation = evaluateSelection(
                    locationService,
                    countryCombo.getValue(),
                    Optional.ofNullable(cityListView.getSelectionModel().getSelectedItem()),
                    zipField.getText(),
                    approximateFallbackCheck.isSelected(),
                    Optional.ofNullable(seededLocation[0]));
            pendingLocation[0] = evaluation.pendingLocation().orElse(null);
            previewLabel.setText(evaluation.previewText());
            UiUtils.setLabelMessage(errorLabel, evaluation.errorText());
            confirmButton.setDisable(!evaluation.saveEnabled());
        };

        countryCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.available()) {
                countryCombo.getSelectionModel().select(oldVal != null ? oldVal : locationService.getDefaultCountry());
                UiUtils.setLabelMessage(errorLabel, newVal.name() + " is coming soon. Please choose Israel for now.");
                return;
            }
            seededLocation[0] = null;
            citySearchField.clear();
            zipField.clear();
            cityListView.getSelectionModel().clearSelection();
            refreshCitySuggestions.run();
            refreshEvaluation.run();
        });
        citySearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            seededLocation[0] = null;
            cityListView.getSelectionModel().clearSelection();
            refreshCitySuggestions.run();
            refreshEvaluation.run();
        });
        cityListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            seededLocation[0] = null;
            if (newVal != null) {
                citySearchField.setText(newVal.name());
                zipField.clear();
            }
            refreshEvaluation.run();
        });
        zipField.textProperty().addListener((obs, oldVal, newVal) -> {
            seededLocation[0] = null;
            if (newVal != null && !newVal.isBlank()) {
                cityListView.getSelectionModel().clearSelection();
            }
            refreshEvaluation.run();
        });
        approximateFallbackCheck.selectedProperty().addListener((obs, oldVal, newVal) -> refreshEvaluation.run());

        refreshEvaluation.run();

        confirmButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (pendingLocation[0] == null) {
                UiUtils.setLabelMessage(errorLabel, "Please choose a supported city or ZIP option before saving.");
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.orElseThrow() == ButtonType.OK && pendingLocation[0] != null) {
            return Optional.of(pendingLocation[0]);
        }
        return Optional.empty();
    }

    static Optional<SeededSelection> initialSelection(
            LocationService locationService, double latitude, double longitude) {
        Objects.requireNonNull(locationService, "locationService cannot be null");
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
        Objects.requireNonNull(locationService, "locationService cannot be null");
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
            Objects.requireNonNull(pendingLocation, "pendingLocation cannot be null");
        }
    }

    record SelectionEvaluation(
            Optional<ResolvedLocation> pendingLocation,
            String previewText,
            String errorText,
            boolean saveEnabled,
            boolean usingApproximateFallback) {
        SelectionEvaluation {
            Objects.requireNonNull(pendingLocation, "pendingLocation cannot be null");
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
