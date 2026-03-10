package datingapp.ui.screen;

import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.ui.ImageCache;
import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiConstants;
import datingapp.ui.UiDialogs;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.UiUtils;
import datingapp.ui.viewmodel.ProfileViewModel;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Profile Editor screen (profile.fxml).
 * Extends BaseController for automatic subscription cleanup.
 */
@SuppressWarnings("unused") // FXML-injected members and handlers are referenced from FXML.
public class ProfileController extends BaseController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);
    private static final String GENDER_OTHER_LABEL = "Other / Non-binary";
    private static final String DEALBREAKER_HEADER = "Only show people who:";
    private static final String DARK_PANEL_STYLE = "-fx-background-color: #1e293b;";
    private static final String PRIMARY_ACCENT_COLOR = "#667eea";
    private static final String SUCCESS_ACCENT_COLOR = "#10b981";

    @FXML
    private javafx.scene.layout.BorderPane rootPane;

    private void wireAuxiliaryActions() {
        wirePhotoActions();
        wireLocationActions();
        wireDealbreakersAction();
        wirePreviewActions();
    }

    private void wirePhotoActions() {
        if (cameraButton != null) {
            cameraButton.setOnAction(event -> {
                event.consume();
                handleUploadPhoto();
            });
        }
        if (setPrimaryPhotoButton != null) {
            setPrimaryPhotoButton.setOnAction(event -> {
                event.consume();
                handleSetPrimaryPhoto();
            });
        }
        if (deletePhotoButton != null) {
            deletePhotoButton.setOnAction(event -> {
                event.consume();
                handleDeletePhoto();
            });
        }
        if (avatarInner != null) {
            avatarInner.setOnMouseClicked(event -> {
                event.consume();
                handleUploadPhoto();
            });
        }
    }

    private void wireLocationActions() {
        if (setLocationButton != null) {
            setLocationButton.setOnAction(event -> {
                event.consume();
                handleSetLocation();
            });
        }
        if (locationField != null) {
            locationField.setOnMouseClicked(event -> {
                event.consume();
                handleSetLocation();
            });
        }
    }

    private void wireDealbreakersAction() {
        if (editDealbreakersBtn != null) {
            editDealbreakersBtn.setOnAction(event -> {
                event.consume();
                handleEditDealbreakers();
            });
        }
    }

    private void wirePreviewActions() {
        if (previewButton != null) {
            previewButton.setOnAction(event -> {
                event.consume();
                handlePreview();
            });
        }
        if (profileScoreButton != null) {
            profileScoreButton.setOnAction(event -> {
                event.consume();
                handleProfileScore();
            });
        }
    }

    @FXML
    private Label nameLabel;

    @FXML
    private Label completionLabel;

    @FXML
    private Label completionDetailsLabel;

    @FXML
    private TextArea bioArea;

    @FXML
    private TextField locationField;

    @FXML
    private Button setLocationButton;

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

    @FXML
    private Button cameraButton;

    @FXML
    private Button setPrimaryPhotoButton;

    @FXML
    private Button deletePhotoButton;

    @FXML
    private StackPane avatarInner;

    @FXML
    private Button prevPhotoButton;

    @FXML
    private Button nextPhotoButton;

    @FXML
    private Label photoIndicatorLabel;

    @FXML
    private Label photoCountLabel;

    @FXML
    private Label interestCountLabel;

    // Gender and preferences fields
    @FXML
    private ComboBox<Gender> genderCombo;

    @FXML
    private FlowPane interestedInFlow;

    @FXML
    private Label interestedInLabel;

    @FXML
    private DatePicker birthDatePicker;

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

    @FXML
    private Button editDealbreakersBtn;

    @FXML
    private Button previewButton;

    @FXML
    private Button profileScoreButton;

    @FXML
    private Label saveStatusLabel;

    @FXML
    private Button saveButton;

    @FXML
    private Button cancelButton;

    private static final int BIO_MAX_LENGTH = 500;
    private static final int BIO_WARNING_THRESHOLD = 400;

    private final ProfileViewModel viewModel;

    public ProfileController(ProfileViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);

        // Bind basic form fields to ViewModel properties
        if (nameLabel != null) {
            nameLabel.textProperty().bind(viewModel.nameProperty());
        }
        if (completionLabel != null) {
            completionLabel.textProperty().bind(viewModel.completionStatusProperty());
        }
        if (completionDetailsLabel != null) {
            completionDetailsLabel.textProperty().bind(viewModel.completionDetailsProperty());
        }
        if (bioArea != null) {
            bioArea.textProperty().bindBidirectional(viewModel.bioProperty());
        }
        if (locationField != null) {
            locationField.textProperty().bind(viewModel.locationDisplayProperty());
            locationField.setEditable(false);
            locationField.setFocusTraversable(false);
        }
        if (interestsField != null) {
            interestsField.textProperty().bindBidirectional(viewModel.interestsProperty());
        }
        if (birthDatePicker != null) {
            birthDatePicker.valueProperty().bindBidirectional(viewModel.birthDateProperty());
        }

        // Setup lifestyle combo boxes
        setupLifestyleComboBoxes();

        // Setup gender and interested in
        setupGenderPreferences();

        // Wire auxiliary UI actions (photo upload, dealbreakers button)
        wireAuxiliaryActions();

        // Bind lifestyle fields to ViewModel
        bindHeightField();
        bindCombo(smokingCombo, viewModel.smokingProperty());
        bindCombo(drinkingCombo, viewModel.drinkingProperty());
        bindCombo(wantsKidsCombo, viewModel.wantsKidsProperty());
        bindCombo(lookingForCombo, viewModel.lookingForProperty());

        // Bind search preference fields
        bindSearchPreferenceFields();

        // Bind dealbreakers status label
        if (dealbreakersStatusLabel != null) {
            dealbreakersStatusLabel.textProperty().bind(viewModel.dealbreakersStatusProperty());
        }

        // Load current user data
        viewModel.loadCurrentUser();

        // Sync profile photo from ViewModel and keep it updated
        updateProfilePhoto(viewModel.primaryPhotoUrlProperty().get());
        addSubscription(viewModel.primaryPhotoUrlProperty().subscribe(this::updateProfilePhoto));

        addSubscription(viewModel.currentPhotoIndexProperty().subscribe(index -> updatePhotoControlsVisibility()));

        viewModel.getPhotoUrls().addListener((javafx.collections.ListChangeListener<String>)
                c -> updatePhotoControlsVisibility());
        updatePhotoControlsVisibility();
        updatePhotoCountLabel(viewModel.photoCountProperty().get());
        addSubscription(viewModel.photoCountProperty().subscribe(this::updatePhotoCountLabel));

        // Refresh interested in buttons AFTER user data is loaded (they show the actual
        // preferences)
        if (interestedInFlow != null) {
            populateInterestedInButtons();
        }

        // Populate interest chips from the interests string
        populateInterestChips();

        // Update interest count label
        updateInterestCountLabel();

        // Listen for interests changes to update chips using Subscription API
        // (memory-safe)
        if (interestsField != null) {
            addSubscription(interestsField.textProperty().subscribe(text -> {
                populateInterestChips();
                updateInterestCountLabel();
            }));
        }

        // Setup character counter for bio using Subscription API (memory-safe)
        setupCharacterCounter();

        if (saveButton != null) {
            saveButton.disableProperty().bind(viewModel.savingProperty());
        }
        if (cancelButton != null) {
            cancelButton.disableProperty().bind(viewModel.savingProperty());
        }
        updateSavingState(viewModel.savingProperty().get());
        addSubscription(viewModel.savingProperty().subscribe(this::updateSavingState));

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);

        // Add pulsing glow to avatar container
        StackPane avatarContainer = (StackPane) rootPane.lookup(".profile-avatar-container");
        if (avatarContainer != null) {
            UiAnimations.addPulsingGlow(avatarContainer, Color.web(PRIMARY_ACCENT_COLOR));
        }
    }

    /**
     * Sets up lifestyle ComboBoxes with enum values and display converters.
     */
    private void setupLifestyleComboBoxes() {
        // Smoking ComboBox
        if (smokingCombo != null) {
            smokingCombo.setItems(FXCollections.observableArrayList(Lifestyle.Smoking.values()));
            smokingCombo.setConverter(UiUtils.createEnumStringConverter(Lifestyle.Smoking::getDisplayName));
            smokingCombo.setButtonCell(UiUtils.createDisplayCell(Lifestyle.Smoking::getDisplayName));
        }

        // Drinking ComboBox
        if (drinkingCombo != null) {
            drinkingCombo.setItems(FXCollections.observableArrayList(Lifestyle.Drinking.values()));
            drinkingCombo.setConverter(UiUtils.createEnumStringConverter(Lifestyle.Drinking::getDisplayName));
            drinkingCombo.setButtonCell(UiUtils.createDisplayCell(Lifestyle.Drinking::getDisplayName));
        }

        // Wants Kids ComboBox
        if (wantsKidsCombo != null) {
            wantsKidsCombo.setItems(FXCollections.observableArrayList(Lifestyle.WantsKids.values()));
            wantsKidsCombo.setConverter(UiUtils.createEnumStringConverter(Lifestyle.WantsKids::getDisplayName));
            wantsKidsCombo.setButtonCell(UiUtils.createDisplayCell(Lifestyle.WantsKids::getDisplayName));
        }

        // Looking For ComboBox
        if (lookingForCombo != null) {
            lookingForCombo.setItems(FXCollections.observableArrayList(Lifestyle.LookingFor.values()));
            lookingForCombo.setConverter(UiUtils.createEnumStringConverter(Lifestyle.LookingFor::getDisplayName));
            lookingForCombo.setButtonCell(UiUtils.createDisplayCell(Lifestyle.LookingFor::getDisplayName));
        }
    }

    /**
     * Sets up gender ComboBox and Interested In selection buttons.
     * Critical for matching - users must have compatible gender preferences to see
     * each other.
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
                        case OTHER -> GENDER_OTHER_LABEL;
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
                                    case OTHER -> GENDER_OTHER_LABEL;
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
                        case OTHER -> GENDER_OTHER_LABEL;
                    });

            boolean isSelected = viewModel.getInterestedInGenders().contains(g);
            UiUtils.updateToggleStyle(
                    btn, isSelected, "-fx-background-radius: 20; -fx-padding: 8 16; -fx-font-size: 13px;");

            btn.setOnAction(event -> {
                event.consume();
                boolean nowSelected = viewModel.toggleInterestedIn(g);
                UiUtils.updateToggleStyle(
                        btn, nowSelected, "-fx-background-radius: 20; -fx-padding: 8 16; -fx-font-size: 13px;");
                updateInterestedInLabel();
            });

            interestedInFlow.getChildren().add(btn);
        }

        updateInterestedInLabel();
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
     * Binds lifestyle ComboBoxes to ViewModel properties.
     */
    private void bindHeightField() {
        addSubscription(heightField.textProperty().subscribe(newVal -> {
            if (newVal == null || newVal.isBlank()) {
                viewModel.heightProperty().set(null);
                return;
            }
            try {
                int height = Integer.parseInt(newVal.trim());
                viewModel.heightProperty().set(height);
            } catch (NumberFormatException _) {
                assert true; // Non-numeric input ignored; user is still typing
            }
        }));

        addSubscription(heightField.focusedProperty().subscribe(focused -> {
            if (Boolean.FALSE.equals(focused)) {
                validateHeightRange();
            }
        }));
    }

    private void validateHeightRange() {
        String heightText = heightField.getText();
        try {
            int height = Integer.parseInt(heightText);
            // Use centralized config bounds for validation
            int minHeight = viewModel.getMinHeightCm();
            int maxHeight = viewModel.getMaxHeightCm();
            if (height < minHeight || height > maxHeight) {
                heightField.setText("");
                viewModel.heightProperty().set(null);
                UiFeedbackService.showWarning("Please enter a height between " + minHeight + "-" + maxHeight + " cm");
            }
        } catch (NumberFormatException _) {
            heightField.setText("");
            viewModel.heightProperty().set(null);
        }
    }

    private <T> void bindCombo(ComboBox<T> comboBox, ObjectProperty<T> property) {
        comboBox.valueProperty().bindBidirectional(property);
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

    private void updateProfilePhoto(String photoUrl) {
        if (profileImageView == null) {
            return;
        }

        if (photoUrl == null || photoUrl.isBlank()) {
            profileImageView.setImage(null);
            profileImageView.setVisible(false);
            if (avatarPlaceholderIcon != null) {
                avatarPlaceholderIcon.setVisible(true);
            }
            return;
        }

        Image image = ImageCache.getImage(photoUrl, 200, 200);
        profileImageView.setImage(image);
        profileImageView.setVisible(true);
        if (avatarPlaceholderIcon != null) {
            avatarPlaceholderIcon.setVisible(false);
        }
    }

    private void updatePhotoControlsVisibility() {
        int count = viewModel.getPhotoUrls().size();
        if (count > 1) {
            if (prevPhotoButton != null) {
                prevPhotoButton.setVisible(true);
                prevPhotoButton.setManaged(true);
            }
            if (nextPhotoButton != null) {
                nextPhotoButton.setVisible(true);
                nextPhotoButton.setManaged(true);
            }
            if (photoIndicatorLabel != null) {
                photoIndicatorLabel.setText(
                        (viewModel.currentPhotoIndexProperty().get() + 1) + "/" + count);
                photoIndicatorLabel.setVisible(true);
                photoIndicatorLabel.setManaged(true);
            }
        } else {
            if (prevPhotoButton != null) {
                prevPhotoButton.setVisible(false);
                prevPhotoButton.setManaged(false);
            }
            if (nextPhotoButton != null) {
                nextPhotoButton.setVisible(false);
                nextPhotoButton.setManaged(false);
            }
            if (photoIndicatorLabel != null) {
                photoIndicatorLabel.setVisible(false);
                photoIndicatorLabel.setManaged(false);
            }
        }
    }

    private void updatePhotoCountLabel(Number countValue) {
        if (photoCountLabel == null) {
            return;
        }
        int count = countValue == null ? 0 : countValue.intValue();
        photoCountLabel.setText("Photos " + count + "/" + viewModel.getMaxPhotos());
    }

    private void updateSavingState(Boolean savingNow) {
        if (saveStatusLabel == null) {
            return;
        }
        if (Boolean.TRUE.equals(savingNow)) {
            saveStatusLabel.setText("Saving…");
            saveStatusLabel.setVisible(true);
            saveStatusLabel.setManaged(true);
            return;
        }
        if ("Saving…".equals(saveStatusLabel.getText())) {
            clearSaveStatus();
        }
    }

    private void showSaveFailureStatus() {
        if (saveStatusLabel == null) {
            return;
        }
        saveStatusLabel.setText("Save failed. Please fix any issues and try again.");
        saveStatusLabel.setVisible(true);
        saveStatusLabel.setManaged(true);
    }

    private void clearSaveStatus() {
        if (saveStatusLabel == null) {
            return;
        }
        saveStatusLabel.setText("");
        saveStatusLabel.setVisible(false);
        saveStatusLabel.setManaged(false);
    }

    @FXML
    private void handlePrevPhoto() {
        viewModel.showPreviousPhoto();
    }

    @FXML
    private void handleNextPhoto() {
        viewModel.showNextPhoto();
    }

    @FXML
    private void handleSetPrimaryPhoto() {
        if (viewModel.getPhotoUrls().isEmpty()) {
            return;
        }
        viewModel.setPrimaryPhoto(viewModel.currentPhotoIndexProperty().get());
    }

    @FXML
    private void handleDeletePhoto() {
        if (viewModel.getPhotoUrls().isEmpty()) {
            return;
        }
        UiDialogs.confirmAndExecute(
                "Remove Photo",
                "Remove the current photo?",
                "This deletes the local managed copy from your profile gallery.",
                () -> viewModel.deletePhoto(
                        viewModel.currentPhotoIndexProperty().get()),
                null);
    }

    /**
     * Setup the character counter binding for the bio text area using Subscription
     * API.
     */
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
        clearSaveStatus();
        viewModel.saveAsync(success -> {
            if (Boolean.TRUE.equals(success)) {
                UiFeedbackService.showSuccess("Profile saved!");
                cleanup();
                NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
                return;
            }
            showSaveFailureStatus();
        });
    }

    @FXML
    private void handleCancel() {
        cleanup(); // Clean up subscriptions before navigating away
        NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
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
        VBox content = new VBox(UiConstants.SPACING_LARGE);
        content.setPadding(new Insets(UiConstants.PADDING_XLARGE));
        content.setStyle(DARK_PANEL_STYLE);

        // Group interests by category
        for (Interest.Category category : Interest.Category.values()) {
            VBox categoryBox = new VBox(UiConstants.SPACING_SMALL);

            Label categoryLabel = new Label(category.getDisplayName());
            categoryLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 14px;");
            categoryBox.getChildren().add(categoryLabel);

            FlowPane interestPane = new FlowPane(UiConstants.SPACING_SMALL, UiConstants.SPACING_SMALL);
            for (Interest interest : Interest.byCategory(category)) {
                Button chipBtn = new Button(interest.getDisplayName());
                boolean isSelected = viewModel.getSelectedInterests().contains(interest);
                UiUtils.updateChipStyle(chipBtn, isSelected, PRIMARY_ACCENT_COLOR);

                chipBtn.setOnAction(event -> {
                    event.consume();
                    boolean nowSelected = viewModel.toggleInterest(interest);
                    UiUtils.updateChipStyle(chipBtn, nowSelected, PRIMARY_ACCENT_COLOR);
                    updateInterestCountLabel();
                });

                interestPane.getChildren().add(chipBtn);
            }
            categoryBox.getChildren().add(interestPane);
            content.getChildren().add(categoryBox);
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setStyle(DARK_PANEL_STYLE);
        dialog.getDialogPane()
                .lookupButton(ButtonType.CLOSE)
                .setStyle("-fx-background-color: " + PRIMARY_ACCENT_COLOR + "; -fx-text-fill: white;");

        dialog.showAndWait();

        // Update display after dialog closes
        populateInterestChips();
        updateInterestCountLabel();
    }

    /**
     * Handles profile photo upload via FileChooser.
     * Saves the photo via ViewModel for persistence.
     */
    @FXML
    private void handleUploadPhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Photo");
        fileChooser
                .getExtensionFilters()
                .setAll(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));

        // Show file chooser dialog
        File selectedFile = fileChooser.showOpenDialog(rootPane.getScene().getWindow());

        if (selectedFile != null) {
            if (viewModel.photoCountProperty().get() >= viewModel.getMaxPhotos()
                    && !viewModel.getPhotoUrls().isEmpty()) {
                boolean replaceCurrent = UiFeedbackService.showConfirmation(
                        "Replace current photo?",
                        "You already have " + viewModel.getMaxPhotos() + " photos.",
                        "Replace the currently selected photo with " + selectedFile.getName() + "?");
                if (!replaceCurrent) {
                    return;
                }
                viewModel.replacePhoto(viewModel.currentPhotoIndexProperty().get(), selectedFile);
                return;
            }

            logInfo("Saving profile photo: {}", selectedFile.getName());
            viewModel.savePhoto(selectedFile);
        }
    }

    @FXML
    private void handleSetLocation() {
        Dialog<ButtonType> dialog = createThemedDialog("Set Location", "Enter your coordinates");

        VBox content = new VBox(UiConstants.SPACING_MEDIUM);
        content.setPadding(new Insets(UiConstants.PADDING_XLARGE));

        Label helperLabel = new Label("Latitude must be between -90 and 90. Longitude must be between -180 and 180.");
        helperLabel.getStyleClass().add("text-secondary");
        helperLabel.setWrapText(true);

        TextField latitudeField = new TextField();
        latitudeField.setPromptText("Latitude");
        latitudeField.getStyleClass().add("location-dialog-field");

        TextField longitudeField = new TextField();
        longitudeField.setPromptText("Longitude");
        longitudeField.getStyleClass().add("location-dialog-field");

        if (viewModel.hasLocationSet()) {
            latitudeField.setText(String.format(java.util.Locale.ROOT, "%.6f", viewModel.getLatitude()));
            longitudeField.setText(String.format(java.util.Locale.ROOT, "%.6f", viewModel.getLongitude()));
        }

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("dialog-error-label");
        errorLabel.setWrapText(true);
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        content.getChildren().addAll(helperLabel, latitudeField, longitudeField, errorLabel);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        confirmButton.setText("Save");
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                double latitude = Double.parseDouble(latitudeField.getText().trim());
                double longitude = Double.parseDouble(longitudeField.getText().trim());
                viewModel.setLocationCoordinates(latitude, longitude);
                errorLabel.setText("");
                errorLabel.setManaged(false);
                errorLabel.setVisible(false);
            } catch (NumberFormatException e) {
                errorLabel.setText("Please enter numeric latitude and longitude values.");
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
                event.consume();
            } catch (IllegalArgumentException e) {
                errorLabel.setText(e.getMessage());
                errorLabel.setManaged(true);
                errorLabel.setVisible(true);
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    /**
     * Opens a dialog to configure dealbreakers.
     * Users can set hard filters like smoking, drinking, height, etc.
     */
    @FXML
    private void handleEditDealbreakers() {
        Dialog<Dealbreakers> dialog = new Dialog<>();
        dialog.setTitle("Configure Dealbreakers");
        dialog.setHeaderText("Set hard filters to exclude profiles that don't match your preferences");

        // Get current dealbreakers
        Dealbreakers current = viewModel.getDealbreakers();
        if (current == null) {
            current = Dealbreakers.none();
        }

        // Create scrollable content
        VBox content = new VBox(UiConstants.SPACING_LARGE);
        content.setPadding(new Insets(UiConstants.PADDING_XLARGE));
        content.setStyle(DARK_PANEL_STYLE);
        content.setPrefWidth(450);

        // Track selected values for each category using EnumSet for performance
        java.util.EnumSet<Lifestyle.Smoking> selectedSmoking =
                datingapp.core.EnumSetUtil.safeCopy(current.acceptableSmoking(), Lifestyle.Smoking.class);
        java.util.EnumSet<Lifestyle.Drinking> selectedDrinking =
                datingapp.core.EnumSetUtil.safeCopy(current.acceptableDrinking(), Lifestyle.Drinking.class);
        java.util.EnumSet<Lifestyle.WantsKids> selectedKids =
                datingapp.core.EnumSetUtil.safeCopy(current.acceptableKidsStance(), Lifestyle.WantsKids.class);
        java.util.EnumSet<Lifestyle.LookingFor> selectedLookingFor =
                datingapp.core.EnumSetUtil.safeCopy(current.acceptableLookingFor(), Lifestyle.LookingFor.class);

        // --- Smoking Section ---
        content.getChildren()
                .add(UiUtils.createSelectionSection(
                        "Smoking Preferences",
                        DEALBREAKER_HEADER,
                        Lifestyle.Smoking.values(),
                        selectedSmoking,
                        Lifestyle.Smoking::getDisplayName,
                        SUCCESS_ACCENT_COLOR));

        // --- Drinking Section ---
        content.getChildren()
                .add(UiUtils.createSelectionSection(
                        "Drinking Preferences",
                        DEALBREAKER_HEADER,
                        Lifestyle.Drinking.values(),
                        selectedDrinking,
                        Lifestyle.Drinking::getDisplayName,
                        SUCCESS_ACCENT_COLOR));

        // --- Kids Section ---
        content.getChildren()
                .add(UiUtils.createSelectionSection(
                        "Kids Preferences",
                        DEALBREAKER_HEADER,
                        Lifestyle.WantsKids.values(),
                        selectedKids,
                        Lifestyle.WantsKids::getDisplayName,
                        SUCCESS_ACCENT_COLOR));

        // --- Looking For Section ---
        content.getChildren()
                .add(UiUtils.createSelectionSection(
                        "Relationship Goals",
                        "Only show people looking for:",
                        Lifestyle.LookingFor.values(),
                        selectedLookingFor,
                        Lifestyle.LookingFor::getDisplayName,
                        SUCCESS_ACCENT_COLOR));

        // --- Clear All Button ---
        Button clearAllBtn = new Button("Clear All Dealbreakers");
        clearAllBtn.setStyle(
                "-fx-background-color: #64748b; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 16;");
        clearAllBtn.setOnAction(event -> {
            event.consume();
            boolean confirmed = UiFeedbackService.showConfirmation(
                    "Clear All Dealbreakers",
                    "Remove all dealbreaker preferences?",
                    "This will reset all your dealbreaker filters. You can set them again anytime.");
            if (!confirmed) {
                return;
            }
            selectedSmoking.clear();
            selectedDrinking.clear();
            selectedKids.clear();
            selectedLookingFor.clear();
            // Rebuild dialog to show cleared state
            UiFeedbackService.showSuccess("All dealbreakers cleared");
        });
        content.getChildren().add(clearAllBtn);

        // Wrap in ScrollPane
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setStyle(DARK_PANEL_STYLE);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle(DARK_PANEL_STYLE);

        // Style buttons
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setStyle("-fx-background-color: " + PRIMARY_ACCENT_COLOR + "; -fx-text-fill: white;");
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
            logInfo("Dealbreakers updated");
        });
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

    private void logError(String message, Throwable error) {
        if (logger.isErrorEnabled()) {
            logger.error(message, error);
        }
    }

    @FXML
    private void handlePreview() {
        try {
            ProfileViewModel.ProfilePreviewSnapshot snapshot = viewModel.buildPreviewSnapshot();

            Dialog<ButtonType> dialog = createThemedDialog("Profile Preview", "How your profile appears to others");
            VBox content = new VBox(UiConstants.SPACING_XLARGE - UiConstants.SPACING_XSMALL);
            content.setPadding(new Insets(UiConstants.PADDING_XXLARGE));
            content.setPrefWidth(420);

            StackPane photoContainer = new StackPane();
            photoContainer.getStyleClass().add("card-photo-placeholder");
            photoContainer.setPrefHeight(220);

            if (!snapshot.photoUrls().isEmpty()) {
                ImageView imageView =
                        new ImageView(ImageCache.getImage(snapshot.photoUrls().getFirst(), 360, 220));
                imageView.setFitWidth(360);
                imageView.setFitHeight(220);
                imageView.setPreserveRatio(true);
                photoContainer.getChildren().add(imageView);
            } else {
                FontIcon placeholderIcon = new FontIcon("mdi2a-account-circle");
                placeholderIcon.setIconSize(72);
                placeholderIcon.setIconColor(Color.web("#94a3b8"));
                photoContainer.getChildren().add(placeholderIcon);
            }

            Label nameAndAgeLabel = new Label(snapshot.name() + ", " + snapshot.age());
            nameAndAgeLabel.getStyleClass().add("card-name");
            Label completionChip = new Label(snapshot.completionText());
            completionChip.getStyleClass().add("notification-badge");

            HBox titleRow = new HBox(UiConstants.SPACING_MEDIUM, nameAndAgeLabel, completionChip);
            titleRow.setFillHeight(true);

            Label locationLabel = new Label("📍 " + snapshot.location());
            locationLabel.getStyleClass().add("card-distance");

            Label lookingForLabel = new Label("Looking for: " + snapshot.lookingFor());
            lookingForLabel.getStyleClass().add("text-secondary");

            Label bioPreviewLabel = new Label(snapshot.bio());
            bioPreviewLabel.setWrapText(true);
            bioPreviewLabel.getStyleClass().add("text-secondary");

            FlowPane interestPane = new FlowPane(UiConstants.SPACING_SMALL, UiConstants.SPACING_SMALL);
            interestPane.getStyleClass().add("interests-container");
            if (snapshot.interests().isEmpty()) {
                Label placeholderChip = new Label("No interests added yet");
                placeholderChip.getStyleClass().add("interest-chip");
                interestPane.getChildren().add(placeholderChip);
            } else {
                snapshot.interests().forEach(interest -> {
                    Label chip = new Label(interest);
                    chip.getStyleClass().add("interest-chip");
                    interestPane.getChildren().add(chip);
                });
            }

            content.getChildren()
                    .addAll(photoContainer, titleRow, locationLabel, lookingForLabel, bioPreviewLabel, interestPane);

            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            UiAnimations.fadeIn(content, 300);
            dialog.showAndWait();
        } catch (Exception e) {
            logError("Failed to build profile preview", e);
            UiFeedbackService.showError("Unable to build profile preview: " + e.getMessage());
        }
    }

    @FXML
    private void handleProfileScore() {
        try {
            var completion = viewModel.calculateCurrentCompletion();

            Dialog<ButtonType> dialog = createThemedDialog("Profile Score", "Your profile quality breakdown");
            VBox scoreContent = new VBox(UiConstants.SPACING_LARGE);
            scoreContent.setPadding(new Insets(UiConstants.PADDING_XXLARGE));
            scoreContent.setPrefWidth(460);

            Label scoreLabel =
                    new Label(completion.getTierEmoji() + " " + completion.score() + "% " + completion.tier());
            scoreLabel.getStyleClass().add("subheading");

            ProgressBar totalProgress = new ProgressBar(completion.score() / 100.0);
            totalProgress.setPrefWidth(380);

            VBox breakdownBox = new VBox(UiConstants.SPACING_MEDIUM);
            completion.breakdown().forEach(category -> {
                VBox categoryBox = new VBox(UiConstants.SPACING_SMALL - UiConstants.SPACING_XSMALL);
                Label categoryLabel = new Label(category.category() + " • " + category.score() + "%");
                categoryLabel.getStyleClass().add("stat-label-primary");
                ProgressBar categoryProgress = new ProgressBar(category.score() / 100.0);
                categoryProgress.setPrefWidth(360);
                VBox missingItems = new VBox(UiConstants.SPACING_XSMALL);
                if (category.missingItems().isEmpty()) {
                    Label doneLabel = new Label("Fully completed");
                    doneLabel.getStyleClass().add("text-secondary");
                    missingItems.getChildren().add(doneLabel);
                } else {
                    category.missingItems().forEach(item -> {
                        Label itemLabel = new Label("• " + item);
                        itemLabel.getStyleClass().add("text-secondary");
                        missingItems.getChildren().add(itemLabel);
                    });
                }
                categoryBox.getChildren().addAll(categoryLabel, categoryProgress, missingItems);
                breakdownBox.getChildren().add(categoryBox);
            });

            VBox nextStepsBox = new VBox(UiConstants.SPACING_SMALL - UiConstants.SPACING_XSMALL);
            if (!completion.nextSteps().isEmpty()) {
                Label nextStepsTitle = new Label("Next steps");
                nextStepsTitle.getStyleClass().add("stat-label-primary");
                nextStepsBox.getChildren().add(nextStepsTitle);
                completion.nextSteps().forEach(step -> {
                    Label stepLabel = new Label("• " + step);
                    stepLabel.setWrapText(true);
                    stepLabel.getStyleClass().add("text-secondary");
                    nextStepsBox.getChildren().add(stepLabel);
                });
            }

            VBox rootBox = new VBox(
                    UiConstants.SPACING_XLARGE - UiConstants.SPACING_XSMALL,
                    scoreLabel,
                    totalProgress,
                    breakdownBox,
                    nextStepsBox);
            ScrollPane scrollPane = new ScrollPane(rootBox);
            scrollPane.setFitToWidth(true);
            scrollPane.getStyleClass().add("transparent-scroll");

            dialog.getDialogPane().setContent(scrollPane);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
        } catch (Exception e) {
            logError("Failed to calculate profile score", e);
            UiFeedbackService.showError("Unable to calculate profile score: " + e.getMessage());
        }
    }

    private Dialog<ButtonType> createThemedDialog(String title, String headerText) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(headerText);
        dialog.initModality(Modality.WINDOW_MODAL);
        if (rootPane != null
                && rootPane.getScene() != null
                && rootPane.getScene().getWindow() != null) {
            dialog.initOwner(rootPane.getScene().getWindow());
            dialog.getDialogPane().getStylesheets().setAll(rootPane.getScene().getStylesheets());
        } else {
            URL themeUrl = getClass().getResource("/css/theme.css");
            if (themeUrl != null) {
                dialog.getDialogPane().getStylesheets().add(themeUrl.toExternalForm());
            }
        }
        return dialog;
    }
}
