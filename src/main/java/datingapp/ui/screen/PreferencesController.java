package datingapp.ui.screen;

import datingapp.ui.NavigationService;
import datingapp.ui.animation.UiAnimations;
import datingapp.ui.viewmodel.screen.PreferencesViewModel;
import datingapp.ui.viewmodel.screen.PreferencesViewModel.GenderPreference;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Preferences/Filter screen.
 * Extends BaseController for automatic subscription cleanup.
 */
public class PreferencesController extends BaseController implements Initializable {
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

    @FXML
    private ToggleButton themeToggle;

    @FXML
    private Button backButton;

    @FXML
    private Button saveButton;

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
        wireActionButtons();

        UiAnimations.fadeIn(rootPane, 600);
    }

    private void setupAgeControls() {
        // Bind sliders bi-directionally or via listeners
        minAgeSlider.setValue(viewModel.minAgeProperty().get());
        maxAgeSlider.setValue(viewModel.maxAgeProperty().get());

        // Update labels
        updateAgeLabels();

        // Listeners using Subscription API
        addSubscription(minAgeSlider.valueProperty().subscribe(newVal -> {
            int val = newVal.intValue();
            // Enforce min < max
            if (val > maxAgeSlider.getValue()) {
                maxAgeSlider.setValue(val);
            }
            viewModel.minAgeProperty().set(val);
            updateAgeLabels();
        }));

        addSubscription(maxAgeSlider.valueProperty().subscribe(newVal -> {
            int val = newVal.intValue();
            // Enforce max > min
            if (val < minAgeSlider.getValue()) {
                minAgeSlider.setValue(val);
            }
            viewModel.maxAgeProperty().set(val);
            updateAgeLabels();
        }));
    }

    private void updateAgeLabels() {
        ageStartLabel.setText(String.valueOf((int) minAgeSlider.getValue()));
        ageEndLabel.setText(String.valueOf((int) maxAgeSlider.getValue()));
    }

    private void setupDistanceControls() {
        distanceSlider.setValue(viewModel.maxDistanceProperty().get());
        updateDistanceLabel();

        addSubscription(distanceSlider.valueProperty().subscribe(newVal -> {
            viewModel.maxDistanceProperty().set(newVal.intValue());
            updateDistanceLabel();
        }));
    }

    private void updateDistanceLabel() {
        distanceValueLabel.setText((int) distanceSlider.getValue() + " km");
    }

    private void setupGenderControls() {
        // Set initial toggle
        GenderPreference pref = viewModel.interestedInProperty().get();
        if (pref == null) {
            everyoneToggle.setSelected(true);
        } else {
            switch (pref) {
                case MEN -> menToggle.setSelected(true);
                case WOMEN -> womenToggle.setSelected(true);
                case EVERYONE -> everyoneToggle.setSelected(true);
                default -> {
                    logWarn("Unknown gender preference: {}", pref);
                    everyoneToggle.setSelected(true);
                }
            }
        }

        // Add listener using Subscription API
        addSubscription(genderGroup.selectedToggleProperty().subscribe((oldVal, newVal) -> {
            if (newVal == null && oldVal != null) {
                // Prevent deselecting all - reselect old
                oldVal.setSelected(true);
                return;
            }

            if (Objects.equals(newVal, menToggle)) {
                viewModel.interestedInProperty().set(GenderPreference.MEN);
            } else if (Objects.equals(newVal, womenToggle)) {
                viewModel.interestedInProperty().set(GenderPreference.WOMEN);
            } else {
                viewModel.interestedInProperty().set(GenderPreference.EVERYONE);
            }
        }));
    }

    @FXML
    private void handleSave() {
        logInfo("Saving MatchPreferences...");
        viewModel.savePreferences();
        NavigationService.getInstance().goBack();
    }

    @FXML
    private void handleThemeToggle() {
        boolean isDarkMode = themeToggle.isSelected();
        logInfo("Toggling theme to: {}", isDarkMode ? "Dark" : "Light");

        Scene scene = rootPane.getScene();
        if (scene == null) {
            logWarn("Scene not available for theme toggle");
            return;
        }

        String darkTheme = resolveStylesheet("/css/theme.css");
        String lightTheme = resolveStylesheet("/css/light-theme.css");

        if (isDarkMode) {
            // Remove light theme, ensure dark theme is present
            if (lightTheme != null) {
                scene.getStylesheets().remove(lightTheme);
            }
            if (darkTheme != null && !scene.getStylesheets().contains(darkTheme)) {
                scene.getStylesheets().add(darkTheme);
            }
        } else {
            // Add light theme on top (it overrides dark theme)
            if (lightTheme != null && !scene.getStylesheets().contains(lightTheme)) {
                scene.getStylesheets().add(lightTheme);
            }
        }
    }

    private String resolveStylesheet(String path) {
        URL resource = getClass().getResource(path);
        if (resource == null) {
            logWarn("Stylesheet not found: {}", path);
            return null;
        }
        return resource.toExternalForm();
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    private void wireActionButtons() {
        if (backButton != null) {
            backButton.setOnAction(event -> {
                event.consume();
                handleBack();
            });
        }
        if (saveButton != null) {
            saveButton.setOnAction(event -> {
                event.consume();
                handleSave();
            });
        }
        if (themeToggle != null) {
            themeToggle.setOnAction(event -> {
                event.consume();
                handleThemeToggle();
            });
        }
    }
}
