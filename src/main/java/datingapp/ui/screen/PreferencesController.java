package datingapp.ui.screen;

import datingapp.ui.UiAnimations;
import datingapp.ui.UiPreferencesStore.ThemeMode;
import datingapp.ui.viewmodel.PreferencesViewModel;
import datingapp.ui.viewmodel.PreferencesViewModel.GenderPreference;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
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
        setupThemeControls();
        wireActionButtons();
        setupAccessibilityMetadata();

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
        addSubscription(viewModel.minAgeProperty().subscribe(newVal -> {
            if (newVal != null && minAgeSlider.getValue() != newVal.doubleValue()) {
                minAgeSlider.setValue(newVal.doubleValue());
            }
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
        addSubscription(viewModel.maxAgeProperty().subscribe(newVal -> {
            if (newVal != null && maxAgeSlider.getValue() != newVal.doubleValue()) {
                maxAgeSlider.setValue(newVal.doubleValue());
            }
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
        addSubscription(viewModel.maxDistanceProperty().subscribe(newVal -> {
            if (newVal != null && distanceSlider.getValue() != newVal.doubleValue()) {
                distanceSlider.setValue(newVal.doubleValue());
            }
        }));
    }

    private void updateDistanceLabel() {
        distanceValueLabel.setText((int) distanceSlider.getValue() + " km");
    }

    private void setupGenderControls() {
        syncGenderToggle(viewModel.interestedInProperty().get());
        addSubscription(viewModel.interestedInProperty().subscribe(this::syncGenderToggle));

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

    private void setupThemeControls() {
        ThemeMode mode = viewModel.themeModeProperty().get();
        themeToggle.setSelected(mode == null || mode.isDark());
        addSubscription(viewModel.themeModeProperty().subscribe(newMode -> {
            ThemeMode resolvedMode = newMode == null ? ThemeMode.DARK : newMode;
            if (themeToggle.isSelected() != resolvedMode.isDark()) {
                themeToggle.setSelected(resolvedMode.isDark());
            }
        }));
    }

    private void syncGenderToggle(GenderPreference pref) {
        GenderPreference resolvedPreference = pref == null ? GenderPreference.EVERYONE : pref;
        switch (resolvedPreference) {
            case MEN -> menToggle.setSelected(true);
            case WOMEN -> womenToggle.setSelected(true);
            case EVERYONE -> everyoneToggle.setSelected(true);
            default -> {
                logWarn("Unknown gender preference: {}", resolvedPreference);
                everyoneToggle.setSelected(true);
            }
        }
    }

    @FXML
    private void handleSave() {
        logInfo("Saving MatchPreferences...");
        viewModel.savePreferences();
        datingapp.ui.NavigationService.getInstance().goBack();
    }

    @FXML
    private void handleThemeToggle() {
        ThemeMode selectedTheme = ThemeMode.fromDarkMode(themeToggle.isSelected());
        logInfo("Toggling theme to: {}", selectedTheme);
        viewModel.updateThemeMode(selectedTheme);
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

    private void setupAccessibilityMetadata() {
        if (minAgeSlider != null) {
            minAgeSlider.setAccessibleText("Minimum preferred age");
        }
        if (maxAgeSlider != null) {
            maxAgeSlider.setAccessibleText("Maximum preferred age");
        }
        if (distanceSlider != null) {
            distanceSlider.setAccessibleText("Maximum preferred distance");
        }
        if (menToggle != null) {
            menToggle.setAccessibleText("Show men");
        }
        if (womenToggle != null) {
            womenToggle.setAccessibleText("Show women");
        }
        if (everyoneToggle != null) {
            everyoneToggle.setAccessibleText("Show everyone");
        }
        if (themeToggle != null) {
            themeToggle.setAccessibleText("Toggle dark mode");
        }
        if (saveButton != null) {
            saveButton.setAccessibleText("Save discovery settings");
        }
        if (backButton != null) {
            backButton.setAccessibleText("Go back");
        }
        if (rootPane != null) {
            rootPane.setOnKeyPressed(event -> {
                if (event.isAltDown() && event.getCode() == KeyCode.S) {
                    handleSave();
                    event.consume();
                }
            });
        }
    }
}
