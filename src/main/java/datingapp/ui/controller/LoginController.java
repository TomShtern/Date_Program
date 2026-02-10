package datingapp.ui.controller;

import datingapp.core.AppConfig;
import datingapp.core.ProfileCompletionService;
import datingapp.core.User;
import datingapp.core.User.Gender;
import datingapp.ui.NavigationService;
import datingapp.ui.util.Toast;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.viewmodel.LoginViewModel;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Login screen (login.fxml).
 * Handles user selection, login, and account creation dialog.
 * Extends BaseController for automatic subscription cleanup.
 */
public class LoginController extends BaseController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    private static final String SECONDARY_TEXT_STYLE = "-fx-text-fill: -fx-text-secondary;";

    // CSS Class Names
    private static final String CSS_LOGIN_HINT = "login-hint";
    private static final String CSS_DIALOG_PANE = "dialog-pane";

    // Placeholder Messages
    private static final String MSG_NO_PROFILES_YET = "No profiles yet. Create one to get started.";
    private static final String MSG_NO_PROFILES_MATCH = "No profiles match \"";
    private static final String MSG_NO_PROFILES_TO_SHOW = "No profiles to show.";

    // Dialog Labels
    private static final String DIALOG_TITLE = "Create New Account";
    private static final String BUTTON_CREATE = "Create";
    private static final String LABEL_NAME = "Name:";
    private static final String LABEL_AGE = "Age:";
    private static final String LABEL_GENDER = "Gender:";
    private static final String LABEL_INTERESTED_IN = "Interested In:";
    private static final String PROMPT_ENTER_NAME = "Enter your name";
    private static final AppConfig CONFIG = AppConfig.defaults();
    private static final int AGE_MIN = CONFIG.minAge();
    private static final int AGE_MAX = CONFIG.maxAge();
    private static final int AGE_DEFAULT = 25;

    // Log Messages
    private static final String LOG_LOGIN_SUCCESS = "Login successful, navigating to Dashboard";
    private static final String LOG_OPENING_DIALOG = "Opening account creation dialog";
    private static final String LOG_USER_CREATED = "Created new user: {}";
    private static final String LOG_STYLESHEET_NOT_FOUND = "Stylesheet not found: {}";

    // Paths
    private static final String STYLESHEET_PATH = "/css/theme.css";

    // CSS Styles
    private static final String STYLE_SURFACE_DARK = "-fx-background-color: -fx-surface-dark;";
    private static final String STYLE_TITLE =
            "-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -fx-text-primary;";
    private static final String STYLE_FIELD_BACKGROUND =
            "-fx-background-color: -fx-surface-elevated; -fx-text-fill: white; "
                    + "-fx-prompt-text-fill: -fx-text-muted; -fx-background-radius: 8;";
    private static final String STYLE_COMBO_BACKGROUND = "-fx-background-color: #1e293b; -fx-background-radius: 8;";
    private static final String STYLE_CELL_BACKGROUND = "-fx-background-color: #1e293b;";
    private static final String STYLE_CELL_WITH_TEXT =
            "-fx-background-color: #1e293b; -fx-text-fill: white; -fx-padding: 8 12;";
    private static final String STYLE_ERROR_LABEL = "-fx-text-fill: #ef4444; -fx-font-size: 13px;";

    @FXML
    private StackPane rootPane;

    @FXML
    private ListView<User> userListView;

    @FXML
    private Button loginButton;

    @FXML
    private Button createAccountButton;

    @FXML
    private TextField filterField;

    private final LoginViewModel viewModel;
    private final ProfileCompletionService profileCompletionService;
    private final Label emptyListPlaceholder = new Label();

    public LoginController(LoginViewModel viewModel, ProfileCompletionService profileCompletionService) {
        this.viewModel = viewModel;
        this.profileCompletionService = profileCompletionService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(Toast::showError);

        bindUserList();
        setupUserListInteractions();
        setupFilterField();
        setupSelectionDefaults();
        setupGlobalShortcuts();

        wireCreateAccountButton();

        UiAnimations.fadeIn(rootPane, 800);
        updatePlaceholderText();
    }

    private void bindUserList() {
        userListView.setItems(viewModel.getFilteredUsers());
        userListView.setCellFactory(lv -> UserListCellFactory.create(profileCompletionService));
        addSubscription(userListView.getSelectionModel().selectedItemProperty().subscribe(viewModel::setSelectedUser));
        loginButton.disableProperty().bind(viewModel.loginDisabledProperty());

        emptyListPlaceholder.getStyleClass().add(CSS_LOGIN_HINT);
        userListView.setPlaceholder(emptyListPlaceholder);
    }

    private void setupUserListInteractions() {
        userListView.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2) {
                return;
            }
            if (userListView.getSelectionModel().getSelectedItem() == null) {
                return;
            }
            if (!loginButton.isDisabled()) {
                handleLogin();
            }
        });

        userListView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                attemptLogin();
                event.consume();
            }
        });

        viewModel.getFilteredUsers().addListener((InvalidationListener) obs -> {
            Objects.requireNonNull(obs, "filtered users listener source cannot be null");
            updatePlaceholderText();
            if (userListView.getSelectionModel().getSelectedItem() == null
                    && !viewModel.getFilteredUsers().isEmpty()) {
                userListView.getSelectionModel().selectFirst();
            }
        });
    }

    private void setupFilterField() {
        filterField.textProperty().bindBidirectional(viewModel.filterTextProperty());
        filterField.textProperty().addListener((obs, oldVal, newVal) -> {
            Objects.requireNonNull(obs, "filter text listener source cannot be null");
            if (!Objects.equals(oldVal, newVal)) {
                updatePlaceholderText();
            }
        });
        filterField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                moveSelection(1);
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.UP) {
                moveSelection(-1);
                event.consume();
            }
        });
    }

    private void setupSelectionDefaults() {
        if (!viewModel.getFilteredUsers().isEmpty()) {
            userListView.getSelectionModel().selectFirst();
        }
    }

    private void setupGlobalShortcuts() {
        rootPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                attemptLogin();
                event.consume();
            }
        });
    }

    private void wireCreateAccountButton() {
        if (createAccountButton == null) {
            return;
        }
        createAccountButton.setOnAction(event -> {
            event.consume();
            handleCreateAccount();
        });
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleLogin() {
        if (viewModel.login()) {
            logInfo(LOG_LOGIN_SUCCESS);
            NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
        }
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleCreateAccount() {
        logInfo(LOG_OPENING_DIALOG);
        showCreateAccountDialog();
    }

    private void attemptLogin() {
        if (!loginButton.isDisabled()) {
            handleLogin();
        }
    }

    private void updatePlaceholderText() {
        String filter = viewModel.filterTextProperty().get();
        if (viewModel.getUsers().isEmpty()) {
            emptyListPlaceholder.setText(MSG_NO_PROFILES_YET);
            return;
        }

        if (filter != null && !filter.isBlank() && viewModel.getFilteredUsers().isEmpty()) {
            emptyListPlaceholder.setText(MSG_NO_PROFILES_MATCH + formatFilter(filter) + "\".");
            return;
        }

        emptyListPlaceholder.setText(MSG_NO_PROFILES_TO_SHOW);
    }

    private String formatFilter(String filter) {
        String trimmed = filter.trim();
        if (trimmed.length() <= 24) {
            return trimmed;
        }
        return trimmed.substring(0, 21) + "...";
    }

    private void moveSelection(int delta) {
        int size = viewModel.getFilteredUsers().size();
        if (size == 0) {
            return;
        }

        int current = userListView.getSelectionModel().getSelectedIndex();
        double candidate = (double) current + delta;
        int target = (int) Math.clamp(candidate, 0.0, size - 1.0);
        userListView.getSelectionModel().select(target);
        userListView.scrollTo(target);
    }

    private void showCreateAccountDialog() {
        // Create the dialog
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle(DIALOG_TITLE);
        dialog.setHeaderText(null); // No header for cleaner look

        // Apply dark styling to dialog
        String themeStylesheet = resolveStylesheet(STYLESHEET_PATH);
        if (themeStylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(themeStylesheet);
        }
        dialog.getDialogPane().getStyleClass().add(CSS_DIALOG_PANE);

        // Set the button types
        ButtonType createButtonType = new ButtonType(BUTTON_CREATE, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Create the form with VBox for better spacing
        VBox content = new VBox(20);
        content.setPadding(new Insets(30, 40, 20, 40));
        content.setStyle(STYLE_SURFACE_DARK);

        // Title
        Label titleLabel = new Label(DIALOG_TITLE);
        titleLabel.setStyle(STYLE_TITLE);

        // Name field
        VBox nameBox = new VBox(8);
        Label nameLabel = new Label(LABEL_NAME);
        nameLabel.setStyle(SECONDARY_TEXT_STYLE);
        TextField nameField = new TextField();
        nameField.setPromptText(PROMPT_ENTER_NAME);
        nameField.setPrefWidth(280);
        nameField.setStyle(STYLE_FIELD_BACKGROUND);
        nameBox.getChildren().addAll(nameLabel, nameField);

        // Age spinner
        VBox ageBox = new VBox(8);
        Label ageLabel = new Label(LABEL_AGE);
        ageLabel.setStyle(SECONDARY_TEXT_STYLE);
        Spinner<Integer> ageSpinner = new Spinner<>();
        SpinnerValueFactory<Integer> ageValueFactory = new SpinnerValueFactory<>() {
            @Override
            public void decrement(int steps) {
                int current = getValue() == null ? AGE_DEFAULT : getValue();
                int next = Math.max(AGE_MIN, current - steps);
                setValue(next);
            }

            @Override
            public void increment(int steps) {
                int current = getValue() == null ? AGE_DEFAULT : getValue();
                int next = Math.min(AGE_MAX, current + steps);
                setValue(next);
            }
        };
        ageValueFactory.setValue(AGE_DEFAULT);
        ageSpinner.setValueFactory(ageValueFactory);
        ageSpinner.setEditable(true);
        ageSpinner.setPrefWidth(120);
        ageBox.getChildren().addAll(ageLabel, ageSpinner);

        // Gender combo
        VBox genderBox = new VBox(8);
        Label genderLabel = new Label(LABEL_GENDER);
        genderLabel.setStyle(SECONDARY_TEXT_STYLE);
        ComboBox<Gender> genderCombo = new ComboBox<>();
        genderCombo.getItems().addAll(Gender.values());
        genderCombo.setValue(Gender.OTHER);
        genderCombo.setPrefWidth(200);
        genderCombo.setStyle(STYLE_COMBO_BACKGROUND);
        genderCombo.setCellFactory(lv -> createStyledCell());
        genderCombo.setButtonCell(createStyledCell());
        genderBox.getChildren().addAll(genderLabel, genderCombo);

        // Interested In combo
        VBox interestedBox = new VBox(8);
        Label interestedLabel = new Label(LABEL_INTERESTED_IN);
        interestedLabel.setStyle(SECONDARY_TEXT_STYLE);
        ComboBox<Gender> interestedInCombo = new ComboBox<>();
        interestedInCombo.getItems().addAll(Gender.values());
        interestedInCombo.setValue(Gender.OTHER);
        interestedInCombo.setPrefWidth(200);
        interestedInCombo.setStyle(STYLE_COMBO_BACKGROUND);
        interestedInCombo.setCellFactory(lv -> createStyledCell());
        interestedInCombo.setButtonCell(createStyledCell());
        interestedBox.getChildren().addAll(interestedLabel, interestedInCombo);

        // Error label
        Label errorLabel = new Label();
        errorLabel.setStyle(STYLE_ERROR_LABEL);
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());

        content.getChildren().addAll(titleLabel, nameBox, ageBox, genderBox, interestedBox, errorLabel);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(360);
        dialog.getDialogPane().setMinHeight(450);

        // Enable/disable create button based on input
        Button createButton = (Button) dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);

        nameField
                .textProperty()
                .addListener((obs, oldVal, newVal) ->
                        createButton.setDisable(newVal.trim().isEmpty()));

        // Focus on name field when dialog opens
        Platform.runLater(nameField::requestFocus);

        // Convert result to User
        dialog.setResultConverter(dialogButton -> {
            if (Objects.equals(dialogButton, createButtonType)) {
                String name = nameField.getText().trim();
                int age = ageSpinner.getValue();
                Gender gender = genderCombo.getValue();
                Gender interestedIn = interestedInCombo.getValue();

                return viewModel.createUser(name, age, gender, interestedIn);
            }
            return null;
        });

        Optional<User> result = dialog.showAndWait();
        result.ifPresent(user -> {
            logInfo(LOG_USER_CREATED, user.getName());
            // Select the newly created user
            userListView.getSelectionModel().select(user);
        });

        // Clear form on close
        viewModel.clearCreateForm();
    }

    /**
     * Creates a styled ListCell for ComboBox dropdowns with dark theme colors.
     */
    private ListCell<Gender> createStyledCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Gender item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle(STYLE_CELL_BACKGROUND);
                } else {
                    setText(item.name());
                    setStyle(STYLE_CELL_WITH_TEXT);
                }
            }
        };
    }

    private String resolveStylesheet(String path) {
        URL resource = getClass().getResource(path);
        if (resource == null) {
            logWarn(LOG_STYLESHEET_NOT_FOUND, path);
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
}
