package datingapp.ui.controller;

import datingapp.core.Dealbreakers;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.User.Gender;
import datingapp.ui.NavigationService;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.util.UiServices;
import datingapp.ui.viewmodel.ProfileViewModel;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Profile Editor screen (profile.fxml).
 * Extends BaseController for automatic subscription cleanup.
 */
public class ProfileController extends BaseController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    @FXML
    private javafx.scene.layout.BorderPane rootPane;

    @FXML
    private Label nameLabel;

    @FXML
    private Label completionLabel;

    @FXML
    private TextArea bioArea;

    @FXML
    private TextField locationField;

    @FXML
    private TextField interestsField;

    @FXML
    private FlowPane interestsFlow;

    @FXML
    private Label charCountLabel;

    @FXML
    private ImageView profileImageView;

    @FXML
    private FontIcon avatarPlaceholderIcon;

    @SuppressWarnings("unused")
    @FXML
    private Button cameraButton;

    @SuppressWarnings("unused")
    @FXML
    private StackPane avatarInner;

    @FXML
    private Label interestCountLabel;

    // Gender and preferences fields
    @FXML
    private ComboBox<Gender> genderCombo;

    @FXML
    private FlowPane interestedInFlow;

    @FXML
    private Label interestedInLabel;

    // Lifestyle fields
    @FXML
    private TextField heightField;

    @FXML
    private ComboBox<Lifestyle.Smoking> smokingCombo;

    @FXML
    private ComboBox<Lifestyle.Drinking> drinkingCombo;

    @FXML
    private ComboBox<Lifestyle.WantsKids> wantsKidsCombo;

    @FXML
    private ComboBox<Lifestyle.LookingFor> lookingForCombo;

    // Search preference fields
    @FXML
    private TextField minAgeField;

    @FXML
    private TextField maxAgeField;

    @FXML
    private TextField maxDistanceField;

    // Dealbreakers fields
    @FXML
    private Label dealbreakersStatusLabel;

    @SuppressWarnings("unused")
    @FXML
    private Button editDealbreakersBtn;

    private static final int BIO_MAX_LENGTH = 500;
    private static final int BIO_WARNING_THRESHOLD = 400;

    private final ProfileViewModel viewModel;

    public ProfileController(ProfileViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Bind basic form fields to ViewModel properties
        if (nameLabel != null) {
            nameLabel.textProperty().bind(viewModel.nameProperty());
        }
        if (completionLabel != null) {
            completionLabel.textProperty().bind(viewModel.completionStatusProperty());
        }
        if (bioArea != null) {
            bioArea.textProperty().bindBidirectional(viewModel.bioProperty());
        }
        if (locationField != null) {
            locationField.textProperty().bindBidirectional(viewModel.locationProperty());
        }
        if (interestsField != null) {
            interestsField.textProperty().bindBidirectional(viewModel.interestsProperty());
        }

        // Setup lifestyle combo boxes
        setupLifestyleComboBoxes();

        // Setup gender and interested in
        setupGenderPreferences();

        // Bind lifestyle fields to ViewModel
        bindLifestyleFields();

        // Bind search preference fields
        bindSearchPreferenceFields();

        // Bind dealbreakers status label
        if (dealbreakersStatusLabel != null) {
            dealbreakersStatusLabel.textProperty().bind(viewModel.dealbreakersStatusProperty());
        }

        // Load current user data
        viewModel.loadCurrentUser();

        // Refresh interested in buttons AFTER user data is loaded (they show the actual preferences)
        if (interestedInFlow != null) {
            populateInterestedInButtons();
        }

        // Populate interest chips from the interests string
        populateInterestChips();

        // Update interest count label
        updateInterestCountLabel();

        // Listen for interests changes to update chips using Subscription API (memory-safe)
        if (interestsField != null) {
            addSubscription(interestsField.textProperty().subscribe(text -> {
                populateInterestChips();
                updateInterestCountLabel();
            }));
        }

        // Setup character counter for bio using Subscription API (memory-safe)
        setupCharacterCounter();

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);

        // Add pulsing glow to avatar container
        StackPane avatarContainer = (StackPane) rootPane.lookup(".profile-avatar-container");
        if (avatarContainer != null) {
            UiAnimations.addPulsingGlow(avatarContainer, Color.web("#667eea"));
        }
    }

    /**
     * Sets up lifestyle ComboBoxes with enum values and display converters.
     */
    private void setupLifestyleComboBoxes() {
        // Smoking ComboBox
        if (smokingCombo != null) {
            smokingCombo.setItems(FXCollections.observableArrayList(Lifestyle.Smoking.values()));
            smokingCombo.setConverter(createEnumStringConverter(Lifestyle.Smoking::getDisplayName));
            smokingCombo.setButtonCell(createDisplayCell(Lifestyle.Smoking::getDisplayName));
        }

        // Drinking ComboBox
        if (drinkingCombo != null) {
            drinkingCombo.setItems(FXCollections.observableArrayList(Lifestyle.Drinking.values()));
            drinkingCombo.setConverter(createEnumStringConverter(Lifestyle.Drinking::getDisplayName));
            drinkingCombo.setButtonCell(createDisplayCell(Lifestyle.Drinking::getDisplayName));
        }

        // Wants Kids ComboBox
        if (wantsKidsCombo != null) {
            wantsKidsCombo.setItems(FXCollections.observableArrayList(Lifestyle.WantsKids.values()));
            wantsKidsCombo.setConverter(createEnumStringConverter(Lifestyle.WantsKids::getDisplayName));
            wantsKidsCombo.setButtonCell(createDisplayCell(Lifestyle.WantsKids::getDisplayName));
        }

        // Looking For ComboBox
        if (lookingForCombo != null) {
            lookingForCombo.setItems(FXCollections.observableArrayList(Lifestyle.LookingFor.values()));
            lookingForCombo.setConverter(createEnumStringConverter(Lifestyle.LookingFor::getDisplayName));
            lookingForCombo.setButtonCell(createDisplayCell(Lifestyle.LookingFor::getDisplayName));
        }
    }

    /**
     * Sets up gender ComboBox and Interested In selection buttons.
     * Critical for matching - users must have compatible gender preferences to see each other.
     */
    private void setupGenderPreferences() {
        // Gender ComboBox
        if (genderCombo != null) {
            genderCombo.setItems(FXCollections.observableArrayList(Gender.values()));
            genderCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(Gender g) {
                    if (g == null) {
                        return "";
                    }
                    return switch (g) {
                        case MALE -> "Male";
                        case FEMALE -> "Female";
                        case OTHER -> "Other / Non-binary";
                    };
                }

                @Override
                public Gender fromString(String s) {
                    return null;
                }
            });
            genderCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(Gender item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(
                                switch (item) {
                                    case MALE -> "Male";
                                    case FEMALE -> "Female";
                                    case OTHER -> "Other / Non-binary";
                                });
                    }
                }
            });
            // Bind to ViewModel
            genderCombo.valueProperty().bindBidirectional(viewModel.genderProperty());
        }

        // Interested In - FlowPane with toggle buttons
        if (interestedInFlow != null) {
            populateInterestedInButtons();
        }
    }

    /**
     * Populates the interested in FlowPane with toggle buttons for each gender.
     */
    private void populateInterestedInButtons() {
        interestedInFlow.getChildren().clear();

        for (Gender g : Gender.values()) {
            Button btn = new Button(
                    switch (g) {
                        case MALE -> "Men";
                        case FEMALE -> "Women";
                        case OTHER -> "Other / Non-binary";
                    });

            boolean isSelected = viewModel.getInterestedInGenders().contains(g);
            updateInterestedInButtonStyle(btn, isSelected);

            btn.setOnAction(_ -> {
                boolean nowSelected = viewModel.toggleInterestedIn(g);
                updateInterestedInButtonStyle(btn, nowSelected);
                updateInterestedInLabel();
            });

            interestedInFlow.getChildren().add(btn);
        }

        updateInterestedInLabel();
    }

    /**
     * Updates the style of an interested-in toggle button.
     */
    private void updateInterestedInButtonStyle(Button btn, boolean selected) {
        String baseStyle = "-fx-background-radius: 20; -fx-padding: 8 16; -fx-font-size: 13px;";
        if (selected) {
            btn.setStyle("-fx-background-color: #667eea; -fx-text-fill: white; " + baseStyle);
        } else {
            btn.setStyle("-fx-background-color: #334155; -fx-text-fill: #94a3b8; " + baseStyle);
        }
    }

    /**
     * Updates the interested in label to show selection count.
     */
    private void updateInterestedInLabel() {
        if (interestedInLabel != null) {
            int count = viewModel.getInterestedInGenders().size();
            if (count == 0) {
                interestedInLabel.setText("Select at least one");
                interestedInLabel.setStyle("-fx-text-fill: #f87171;"); // Red warning
            } else {
                interestedInLabel.setText(count + " selected");
                interestedInLabel.setStyle("-fx-text-fill: #94a3b8;"); // Normal
            }
        }
    }

    /**
     * Creates a StringConverter for enum types using a display name function.
     */
    private <T extends Enum<T>> StringConverter<T> createEnumStringConverter(
            java.util.function.Function<T, String> displayNameFunc) {
        return new StringConverter<>() {
            @Override
            public String toString(T object) {
                return object == null ? "" : displayNameFunc.apply(object);
            }

            @Override
            public T fromString(String string) {
                return null; // Not needed for ComboBox
            }
        };
    }

    /**
     * Creates a ListCell that displays enum values using their display name.
     */
    private <T extends Enum<T>> ListCell<T> createDisplayCell(java.util.function.Function<T, String> displayNameFunc) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayNameFunc.apply(item));
            }
        };
    }

    /**
     * Binds lifestyle ComboBoxes to ViewModel properties.
     */
    private void bindLifestyleFields() {
        // Height field - bind bidirectionally with conversion
        if (heightField != null) {
            addSubscription(viewModel.heightProperty().subscribe(height -> {
                if (height != null) {
                    heightField.setText(String.valueOf(height));
                } else {
                    heightField.setText("");
                }
            }));
            heightField.textProperty().addListener((_, _, newVal) -> {
                try {
                    if (newVal != null && !newVal.isBlank()) {
                        int h = Integer.parseInt(newVal.trim());
                        if (h > 0 && h < 300) {
                            viewModel.heightProperty().set(h);
                        }
                    } else {
                        viewModel.heightProperty().set(null);
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid height input - field will keep previous value
                    LoggerFactory.getLogger(ProfileController.class).trace("Invalid height input: {}", e.getMessage());
                }
            });
        }

        // Smoking
        if (smokingCombo != null) {
            smokingCombo.valueProperty().bindBidirectional(viewModel.smokingProperty());
        }

        // Drinking
        if (drinkingCombo != null) {
            drinkingCombo.valueProperty().bindBidirectional(viewModel.drinkingProperty());
        }

        // Wants Kids
        if (wantsKidsCombo != null) {
            wantsKidsCombo.valueProperty().bindBidirectional(viewModel.wantsKidsProperty());
        }

        // Looking For
        if (lookingForCombo != null) {
            lookingForCombo.valueProperty().bindBidirectional(viewModel.lookingForProperty());
        }
    }

    /**
     * Binds search preference fields to ViewModel properties.
     */
    private void bindSearchPreferenceFields() {
        if (minAgeField != null) {
            minAgeField.textProperty().bindBidirectional(viewModel.minAgeProperty());
        }
        if (maxAgeField != null) {
            maxAgeField.textProperty().bindBidirectional(viewModel.maxAgeProperty());
        }
        if (maxDistanceField != null) {
            maxDistanceField.textProperty().bindBidirectional(viewModel.maxDistanceProperty());
        }
    }

    /**
     * Updates the interest count label.
     */
    private void updateInterestCountLabel() {
        if (interestCountLabel != null) {
            int count = viewModel.getSelectedInterests().size();
            interestCountLabel.setText(count + "/" + Interest.MAX_PER_USER + " selected");
        }
    }

    /** Setup the character counter binding for the bio text area using Subscription API. */
    private void setupCharacterCounter() {
        if (charCountLabel == null || bioArea == null) {
            return;
        }

        // Initial update
        updateCharCounter(bioArea.getText());

        // Use Subscription API for memory-safe listener
        addSubscription(bioArea.textProperty().subscribe(this::updateCharCounter));
    }

    /** Updates the character counter label with appropriate styling. */
    private void updateCharCounter(String text) {
        int length = text == null ? 0 : text.length();
        charCountLabel.setText(length + "/" + BIO_MAX_LENGTH);

        // Remove existing style classes
        charCountLabel.getStyleClass().removeAll("char-counter-warning", "char-counter-limit");

        // Add appropriate warning class
        if (length >= BIO_MAX_LENGTH) {
            charCountLabel.getStyleClass().add("char-counter-limit");
        } else if (length >= BIO_WARNING_THRESHOLD) {
            charCountLabel.getStyleClass().add("char-counter-warning");
        }
    }

    /**
     * Populates the FlowPane with interest chips parsed from the interests string.
     */
    private void populateInterestChips() {
        if (interestsFlow == null) {
            return;
        }

        interestsFlow.getChildren().clear();

        String interests = viewModel.interestsProperty().get();
        if (interests == null || interests.isBlank()) {
            Label placeholder = new Label("No interests set");
            placeholder.getStyleClass().add("interest-chip");
            placeholder.setStyle("-fx-opacity: 0.6;");
            interestsFlow.getChildren().add(placeholder);
            return;
        }

        // Split by comma and create chips
        String[] interestArray = interests.split(",");
        for (String interest : interestArray) {
            String trimmed = interest.trim();
            if (!trimmed.isEmpty()) {
                Label chip = new Label(trimmed);
                chip.getStyleClass().add("interest-chip");
                interestsFlow.getChildren().add(chip);
            }
        }
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleSave() {
        cleanup(); // Clean up subscriptions before navigating away
        viewModel.save();
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleCancel() {
        cleanup(); // Clean up subscriptions before navigating away
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
    }

    @FXML
    @SuppressWarnings("unused")
    private void handleBack() {
        handleCancel(); // Delegate to avoid duplicate code
    }

    /**
     * Opens a dialog to select interests.
     */
    @FXML
    @SuppressWarnings("unused")
    private void handleEditInterests() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Select Interests");
        dialog.setHeaderText("Choose up to " + Interest.MAX_PER_USER + " interests");

        // Create content
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #1e293b;");

        // Group interests by category
        for (Interest.Category category : Interest.Category.values()) {
            VBox categoryBox = new VBox(8);

            Label categoryLabel = new Label(category.getDisplayName());
            categoryLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 14px;");
            categoryBox.getChildren().add(categoryLabel);

            FlowPane interestPane = new FlowPane(8, 8);
            for (Interest interest : Interest.byCategory(category)) {
                Button chipBtn = new Button(interest.getDisplayName());
                boolean isSelected = viewModel.getSelectedInterests().contains(interest);
                updateInterestChipStyle(chipBtn, isSelected);

                chipBtn.setOnAction(_ -> {
                    boolean nowSelected = viewModel.toggleInterest(interest);
                    updateInterestChipStyle(chipBtn, nowSelected);
                    updateInterestCountLabel();
                });

                interestPane.getChildren().add(chipBtn);
            }
            categoryBox.getChildren().add(interestPane);
            content.getChildren().add(categoryBox);
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setStyle("-fx-background-color: #1e293b;");
        dialog.getDialogPane()
                .lookupButton(ButtonType.CLOSE)
                .setStyle("-fx-background-color: #667eea; -fx-text-fill: white;");

        dialog.showAndWait();

        // Update display after dialog closes
        populateInterestChips();
        updateInterestCountLabel();
    }

    /**
     * Updates the style of an interest chip button based on selection state.
     */
    private void updateInterestChipStyle(Button chip, boolean selected) {
        if (selected) {
            chip.setStyle(
                    "-fx-background-color: #667eea; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 6 12;");
        } else {
            chip.setStyle(
                    "-fx-background-color: #334155; -fx-text-fill: #94a3b8; -fx-background-radius: 20; -fx-padding: 6 12;");
        }
    }

    /**
     * Handles profile photo upload via FileChooser.
     * Saves the photo via ViewModel for persistence.
     */
    @FXML
    @SuppressWarnings("unused")
    private void handleUploadPhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Photo");
        fileChooser
                .getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                        new FileChooser.ExtensionFilter("All Files", "*.*"));

        // Show file chooser dialog
        File selectedFile = fileChooser.showOpenDialog(rootPane.getScene().getWindow());

        if (selectedFile != null) {
            try {
                // Load image preview from file
                Image image = new Image(selectedFile.toURI().toString(), 200, 200, true, true);

                if (!image.isError()) {
                    // Set image to ImageView (immediate preview)
                    profileImageView.setImage(image);
                    profileImageView.setVisible(true);

                    // Hide placeholder icon
                    if (avatarPlaceholderIcon != null) {
                        avatarPlaceholderIcon.setVisible(false);
                    }

                    logger.info("Profile photo loaded: {}", selectedFile.getName());

                    // Save photo via ViewModel (persists to storage)
                    viewModel.savePhoto(selectedFile);
                } else {
                    logger.warn("Failed to load image: {}", selectedFile.getAbsolutePath());
                    UiServices.Toast.getInstance().showError("Failed to load image");
                }
            } catch (Exception e) {
                logger.error("Error loading profile photo", e);
                UiServices.Toast.getInstance().showError("Error loading photo: " + e.getMessage());
            }
        }
    }

    /**
     * Opens a dialog to configure dealbreakers.
     * Users can set hard filters like smoking, drinking, height, etc.
     */
    @FXML
    @SuppressWarnings("unused")
    private void handleEditDealbreakers() {
        Dialog<Dealbreakers> dialog = new Dialog<>();
        dialog.setTitle("Configure Dealbreakers");
        dialog.setHeaderText("Set hard filters to exclude profiles that don't match your preferences");

        // Get current dealbreakers
        Dealbreakers current = viewModel.getDealbreakers();
        Dealbreakers.Builder builder = current != null ? current.toBuilder() : Dealbreakers.builder();

        // Create scrollable content
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #1e293b;");
        content.setPrefWidth(450);

        // Track selected values for each category
        java.util.Set<Lifestyle.Smoking> selectedSmoking = new java.util.HashSet<>(current.acceptableSmoking());
        java.util.Set<Lifestyle.Drinking> selectedDrinking = new java.util.HashSet<>(current.acceptableDrinking());
        java.util.Set<Lifestyle.WantsKids> selectedKids = new java.util.HashSet<>(current.acceptableKidsStance());
        java.util.Set<Lifestyle.LookingFor> selectedLookingFor =
                new java.util.HashSet<>(current.acceptableLookingFor());

        // --- Smoking Section ---
        content.getChildren()
                .add(createDealbreakersSection(
                        "Smoking Preferences",
                        "Only show people who:",
                        Lifestyle.Smoking.values(),
                        selectedSmoking,
                        Lifestyle.Smoking::getDisplayName));

        // --- Drinking Section ---
        content.getChildren()
                .add(createDealbreakersSection(
                        "Drinking Preferences",
                        "Only show people who:",
                        Lifestyle.Drinking.values(),
                        selectedDrinking,
                        Lifestyle.Drinking::getDisplayName));

        // --- Kids Section ---
        content.getChildren()
                .add(createDealbreakersSection(
                        "Kids Preferences",
                        "Only show people who:",
                        Lifestyle.WantsKids.values(),
                        selectedKids,
                        Lifestyle.WantsKids::getDisplayName));

        // --- Looking For Section ---
        content.getChildren()
                .add(createDealbreakersSection(
                        "Relationship Goals",
                        "Only show people looking for:",
                        Lifestyle.LookingFor.values(),
                        selectedLookingFor,
                        Lifestyle.LookingFor::getDisplayName));

        // --- Clear All Button ---
        Button clearAllBtn = new Button("Clear All Dealbreakers");
        clearAllBtn.setStyle(
                "-fx-background-color: #64748b; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 16;");
        clearAllBtn.setOnAction(_ -> {
            selectedSmoking.clear();
            selectedDrinking.clear();
            selectedKids.clear();
            selectedLookingFor.clear();
            // Rebuild dialog to show cleared state
            UiServices.Toast.getInstance().showSuccess("All dealbreakers cleared");
        });
        content.getChildren().add(clearAllBtn);

        // Wrap in ScrollPane
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setStyle("-fx-background-color: #1e293b;");

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle("-fx-background-color: #1e293b;");

        // Style buttons
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setStyle("-fx-background-color: #667eea; -fx-text-fill: white;");
        okBtn.setText("Save");

        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelBtn.setStyle("-fx-background-color: #64748b; -fx-text-fill: white;");

        // Convert result
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return Dealbreakers.builder()
                        .acceptSmoking(selectedSmoking.toArray(new Lifestyle.Smoking[0]))
                        .acceptDrinking(selectedDrinking.toArray(new Lifestyle.Drinking[0]))
                        .acceptKidsStance(selectedKids.toArray(new Lifestyle.WantsKids[0]))
                        .acceptLookingFor(selectedLookingFor.toArray(new Lifestyle.LookingFor[0]))
                        .build();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newDealbreakers -> {
            viewModel.setDealbreakers(newDealbreakers);
            logger.info("Dealbreakers updated");
        });
    }

    /**
     * Creates a dealbreakers section with checkable items.
     */
    private <T extends Enum<T>> VBox createDealbreakersSection(
            String title,
            String subtitle,
            T[] values,
            java.util.Set<T> selected,
            java.util.function.Function<T, String> displayNameFunc) {

        VBox section = new VBox(8);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 14px;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        FlowPane itemsPane = new FlowPane(8, 8);
        for (T value : values) {
            Button chip = new Button(displayNameFunc.apply(value));
            boolean isSelected = selected.contains(value);
            updateDealbreakersChipStyle(chip, isSelected);

            chip.setOnAction(_ -> {
                if (selected.contains(value)) {
                    selected.remove(value);
                    updateDealbreakersChipStyle(chip, false);
                } else {
                    selected.add(value);
                    updateDealbreakersChipStyle(chip, true);
                }
            });

            itemsPane.getChildren().add(chip);
        }

        section.getChildren().addAll(titleLabel, subtitleLabel, itemsPane);
        return section;
    }

    /**
     * Updates the style of a dealbreakers chip button based on selection state.
     */
    private void updateDealbreakersChipStyle(Button chip, boolean selected) {
        if (selected) {
            chip.setStyle(
                    "-fx-background-color: #10b981; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 6 12;");
        } else {
            chip.setStyle(
                    "-fx-background-color: #334155; -fx-text-fill: #94a3b8; -fx-background-radius: 20; -fx-padding: 6 12;");
        }
    }
}
