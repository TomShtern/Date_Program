package datingapp.ui.controller;

import datingapp.core.Gender;
import datingapp.core.ProfileCompletionService;
import datingapp.core.User;
import datingapp.core.UserState;
import datingapp.ui.NavigationService;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.util.UiServices;
import datingapp.ui.viewmodel.LoginViewModel;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
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
    private static final String CSS_LOGIN_AVATAR_CONTAINER = "login-avatar-container";
    private static final String CSS_LOGIN_USER_CELL = "login-user-cell";
    private static final String CSS_LOGIN_USER_NAME = "login-user-name";
    private static final String CSS_TEXT_SECONDARY = "text-secondary";
    private static final String CSS_LOGIN_USER_DETAILS = "login-user-details";
    private static final String CSS_LOGIN_BADGE_ROW = "login-badge-row";
    private static final String CSS_LOGIN_BADGE = "login-badge";
    private static final String CSS_LOGIN_BADGE_PRIMARY = "login-badge-primary";
    private static final String CSS_LOGIN_BADGE_MUTED = "login-badge-muted";
    private static final String CSS_LOGIN_BADGE_SUCCESS = "login-badge-success";
    private static final String CSS_LOGIN_BADGE_WARNING = "login-badge-warning";
    private static final String CSS_DIALOG_PANE = "dialog-pane";

    // UI Text
    private static final String TEXT_UNKNOWN = "Unknown";
    private static final String TEXT_ACTIVE_RECENTLY = "Active recently";
    private static final String TEXT_ACTIVE_JUST_NOW = "Active just now";
    private static final String TEXT_VERIFIED = " • Verified";
    private static final String TEXT_PROFILE_PREFIX = "Profile ";
    private static final String TEXT_PERCENT_SUFFIX = "%";
    private static final String TEXT_ACTIVE_PREFIX = "Active ";
    private static final String TEXT_MINUTES_SUFFIX = "m ago";
    private static final String TEXT_HOURS_SUFFIX = "h ago";
    private static final String TEXT_DAYS_SUFFIX = "d ago";
    private static final String TEXT_WEEKS_SUFFIX = "w ago";
    private static final String TEXT_MONTHS_SUFFIX = "mo ago";

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
    private static final int AGE_MIN = 18;
    private static final int AGE_MAX = 100;
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
    private final Label emptyListPlaceholder = new Label();

    public LoginController(LoginViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
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
        userListView.setCellFactory(lv -> new UserListCell());
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

    /**
     * Custom list cell for displaying user accounts with avatars, badges, and
     * selection animation.
     */
    private static class UserListCell extends ListCell<User> {
        private static final double AVATAR_SIZE = 44;
        private static final double SELECT_SCALE = 1.03;
        private final HBox container = new HBox(15);
        private final StackPane avatarContainer = new StackPane();
        private final ImageView avatarView = new ImageView();
        private final Circle avatarClip = new Circle(AVATAR_SIZE / 2);
        private final VBox textBox = new VBox(2);
        private final Label nameLabel = new Label();
        private final Label detailsLabel = new Label();
        private final HBox badgeRow = new HBox(6);
        private final Label completionBadge = new Label();
        private final Label activityBadge = new Label();

        public UserListCell() {
            avatarView.setFitWidth(AVATAR_SIZE);
            avatarView.setFitHeight(AVATAR_SIZE);
            avatarView.setPreserveRatio(true);
            avatarClip.setCenterX(AVATAR_SIZE / 2);
            avatarClip.setCenterY(AVATAR_SIZE / 2);
            avatarView.setClip(avatarClip);

            avatarContainer.getStyleClass().add(CSS_LOGIN_AVATAR_CONTAINER);
            avatarContainer.getChildren().add(avatarView);

            container.getStyleClass().add(CSS_LOGIN_USER_CELL);
            nameLabel.getStyleClass().add(CSS_LOGIN_USER_NAME);
            detailsLabel.getStyleClass().addAll(CSS_TEXT_SECONDARY, CSS_LOGIN_USER_DETAILS);

            badgeRow.getStyleClass().add(CSS_LOGIN_BADGE_ROW);
            completionBadge.getStyleClass().addAll(CSS_LOGIN_BADGE, CSS_LOGIN_BADGE_PRIMARY);
            activityBadge.getStyleClass().addAll(CSS_LOGIN_BADGE, CSS_LOGIN_BADGE_MUTED);
            badgeRow.getChildren().addAll(completionBadge, activityBadge);

            textBox.getChildren().addAll(nameLabel, detailsLabel, badgeRow);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(8, 12, 8, 12));
            container.getChildren().addAll(avatarContainer, textBox);

            selectedProperty().addListener((obs, oldVal, newVal) -> animateSelection(newVal));
        }

        @Override
        protected void updateItem(User user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setText(null);
                setGraphic(null);
                container.setScaleX(1.0);
                container.setScaleY(1.0);
            } else {
                setText(null);
                nameLabel.setText(user.getName() + ", " + user.getAge());

                StringBuilder sb = new StringBuilder(formatState(user.getState()));
                if (Boolean.TRUE.equals(user.isVerified())) {
                    sb.append(TEXT_VERIFIED);
                }
                detailsLabel.setText(sb.toString());

                updateCompletionBadge(user);
                activityBadge.setText(formatActivity(user.getUpdatedAt()));
                avatarView.setImage(UiServices.getAvatar(resolveAvatarPath(user), AVATAR_SIZE));

                setGraphic(container);
            }
        }

        private void updateCompletionBadge(User user) {
            ProfileCompletionService.CompletionResult result = ProfileCompletionService.calculate(user);
            int score = result.score();
            completionBadge.setText(TEXT_PROFILE_PREFIX + score + TEXT_PERCENT_SUFFIX);

            completionBadge
                    .getStyleClass()
                    .removeAll(CSS_LOGIN_BADGE_PRIMARY, CSS_LOGIN_BADGE_SUCCESS, CSS_LOGIN_BADGE_WARNING);
            if (score >= 90) {
                completionBadge.getStyleClass().add(CSS_LOGIN_BADGE_SUCCESS);
            } else if (score >= 60) {
                completionBadge.getStyleClass().add(CSS_LOGIN_BADGE_PRIMARY);
            } else {
                completionBadge.getStyleClass().add(CSS_LOGIN_BADGE_WARNING);
            }
        }

        private void animateSelection(boolean selected) {
            double target = selected ? SELECT_SCALE : 1.0;
            ScaleTransition transition = new ScaleTransition(Duration.millis(140), container);
            transition.setInterpolator(Interpolator.EASE_OUT);
            transition.setToX(target);
            transition.setToY(target);
            transition.play();
        }

        private static String resolveAvatarPath(User user) {
            List<String> urls = user.getPhotoUrls();
            if (urls == null || urls.isEmpty()) {
                return null;
            }
            String first = urls.get(0);
            if (first == null || first.isBlank()) {
                return null;
            }
            if (first.startsWith("placeholder://")) {
                return null;
            }
            return first;
        }

        private static String formatState(UserState state) {
            if (state == null) {
                return TEXT_UNKNOWN;
            }
            String raw = state.name().toLowerCase(java.util.Locale.ROOT);
            return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }

        private static String formatActivity(Instant updatedAt) {
            // Use updatedAt as a lightweight proxy for recent activity.
            if (updatedAt == null) {
                return TEXT_ACTIVE_RECENTLY;
            }

            java.time.Duration duration = java.time.Duration.between(updatedAt, Instant.now());
            if (duration.isNegative()) {
                duration = java.time.Duration.ZERO;
            }

            long minutes = duration.toMinutes();
            if (minutes < 1) {
                return TEXT_ACTIVE_JUST_NOW;
            }
            if (minutes < 60) {
                return TEXT_ACTIVE_PREFIX + minutes + TEXT_MINUTES_SUFFIX;
            }

            long hours = duration.toHours();
            if (hours < 24) {
                return TEXT_ACTIVE_PREFIX + hours + TEXT_HOURS_SUFFIX;
            }

            long days = duration.toDays();
            if (days < 7) {
                return TEXT_ACTIVE_PREFIX + days + TEXT_DAYS_SUFFIX;
            }

            long weeks = days / 7;
            if (weeks < 5) {
                return TEXT_ACTIVE_PREFIX + weeks + TEXT_WEEKS_SUFFIX;
            }

            long months = days / 30;
            return TEXT_ACTIVE_PREFIX + months + TEXT_MONTHS_SUFFIX;
        }
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleLogin() {
        if (viewModel.login()) {
            logger.info(LOG_LOGIN_SUCCESS);
            NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
        }
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleCreateAccount() {
        logger.info(LOG_OPENING_DIALOG);
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
            logger.info(LOG_USER_CREATED, user.getName());
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
            logger.warn(LOG_STYLESHEET_NOT_FOUND, path);
            return null;
        }
        return resource.toExternalForm();
    }
}
