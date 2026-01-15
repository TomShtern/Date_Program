package datingapp.ui.controller;

import datingapp.ui.NavigationService;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.viewmodel.PreferencesViewModel;
import datingapp.ui.viewmodel.PreferencesViewModel.GenderPreference;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Preferences/Filter screen.
 */
public class PreferencesController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(PreferencesController.class);

    @FXML
    private BorderPane rootPane;

    // Age Controls
    @FXML
    private Slider minAgeSlider;

    @FXML
    private Slider maxAgeSlider;

    @FXML
    private Label ageStartLabel;

    @FXML
    private Label ageEndLabel;

    // Distance Controls
    @FXML
    private Slider distanceSlider;

    @FXML
    private Label distanceValueLabel;

    // Gender Controls
    @FXML
    private ToggleGroup genderGroup;

    @FXML
    private ToggleButton menToggle;

    @FXML
    private ToggleButton womenToggle;

    @FXML
    private ToggleButton everyoneToggle;

    private final PreferencesViewModel viewModel;

    public PreferencesController(PreferencesViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.initialize();

        setupAgeControls();
        setupDistanceControls();
        setupGenderControls();

        UiAnimations.fadeIn(rootPane, 600);
    }

    private void setupAgeControls() {
        // Bind sliders bi-directionally or via listeners
        minAgeSlider.setValue(viewModel.minAgeProperty().get());
        maxAgeSlider.setValue(viewModel.maxAgeProperty().get());

        // Update labels
        updateAgeLabels();

        // Listeners
        minAgeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int val = newVal.intValue();
            // Enforce min < max
            if (val > maxAgeSlider.getValue()) {
                maxAgeSlider.setValue(val);
            }
            viewModel.minAgeProperty().set(val);
            updateAgeLabels();
        });

        maxAgeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int val = newVal.intValue();
            // Enforce max > min
            if (val < minAgeSlider.getValue()) {
                minAgeSlider.setValue(val);
            }
            viewModel.maxAgeProperty().set(val);
            updateAgeLabels();
        });
    }

    private void updateAgeLabels() {
        ageStartLabel.setText(String.valueOf((int) minAgeSlider.getValue()));
        ageEndLabel.setText(String.valueOf((int) maxAgeSlider.getValue()));
    }

    private void setupDistanceControls() {
        distanceSlider.setValue(viewModel.maxDistanceProperty().get());
        updateDistanceLabel();

        distanceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.maxDistanceProperty().set(newVal.intValue());
            updateDistanceLabel();
        });
    }

    private void updateDistanceLabel() {
        distanceValueLabel.setText((int) distanceSlider.getValue() + " km");
    }

    private void setupGenderControls() {
        // Set initial toggle
        GenderPreference pref = viewModel.interestedInProperty().get();
        switch (pref) {
            case MEN -> menToggle.setSelected(true);
            case WOMEN -> womenToggle.setSelected(true);
            case EVERYONE -> everyoneToggle.setSelected(true);
            default -> everyoneToggle.setSelected(true);
        }

        // Add listener
        genderGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                // Prevent deselecting all - reselect old
                oldVal.setSelected(true);
                return;
            }

            if (newVal == menToggle) {
                viewModel.interestedInProperty().set(GenderPreference.MEN);
            } else if (newVal == womenToggle) {
                viewModel.interestedInProperty().set(GenderPreference.WOMEN);
            } else {
                viewModel.interestedInProperty().set(GenderPreference.EVERYONE);
            }
        });
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleSave() {
        logger.info("Saving preferences...");
        viewModel.savePreferences();
        NavigationService.getInstance().goBack();
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleBack() {
        logger.info("Canceling preferences changes...");
        // Just go back without saving
        NavigationService.getInstance().goBack();
    }
}
