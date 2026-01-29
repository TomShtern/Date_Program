package datingapp.ui.controller;

import datingapp.core.ProfileCompletionService;
import datingapp.core.User;
import datingapp.core.User.Gender;
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
import javafx.collections.ListChangeListener;
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
        // Bind ListView items to ViewModel's filtered list
        userListView.setItems(viewModel.getFilteredUsers());

        // Custom cell factory to show user name, status, and completion
        userListView.setCellFactory(lv -> new UserListCell());

        // Listen for selection changes using Subscription API
        addSubscription(userListView.getSelectionModel().selectedItemProperty().subscribe(viewModel::setSelectedUser));

        // Bind button disable state to ViewModel property
        loginButton.disableProperty().bind(viewModel.loginDisabledProperty());

        emptyListPlaceholder.getStyleClass().add("login-hint");
        userListView.setPlaceholder(emptyListPlaceholder);

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

        rootPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                attemptLogin();
                event.consume();
            }
        });

        filterField.textProperty().bindBidirectional(viewModel.filterTextProperty());
        filterField.textProperty().addListener((obs, oldVal, newVal) -> updatePlaceholderText());
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

        viewModel.getFilteredUsers().addListener((ListChangeListener<User>) change -> {
            updatePlaceholderText();
            if (userListView.getSelectionModel().getSelectedItem() == null
                    && !viewModel.getFilteredUsers().isEmpty()) {
                userListView.getSelectionModel().selectFirst();
            }
        });

        // Select first user if available
        if (!viewModel.getFilteredUsers().isEmpty()) {
            userListView.getSelectionModel().selectFirst();
        }

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);
        updatePlaceholderText();
    }

    /** Custom list cell for displaying user accounts with avatars, badges, and selection animation. */
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

            avatarContainer.getStyleClass().add("login-avatar-container");
            avatarContainer.getChildren().add(avatarView);

            container.getStyleClass().add("login-user-cell");
            nameLabel.getStyleClass().add("login-user-name");
            detailsLabel.getStyleClass().addAll("text-secondary", "login-user-details");

            badgeRow.getStyleClass().add("login-badge-row");
            completionBadge.getStyleClass().addAll("login-badge", "login-badge-primary");
            activityBadge.getStyleClass().addAll("login-badge", "login-badge-muted");
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
                    sb.append(" • Verified");
                }
                detailsLabel.setText(sb.toString());

                updateCompletionBadge(user);
                activityBadge.setText(formatActivity(user.getUpdatedAt()));
                avatarView.setImage(UiServices.ImageCache.getAvatar(resolveAvatarPath(user), AVATAR_SIZE));

                setGraphic(container);
            }
        }

        private void updateCompletionBadge(User user) {
            ProfileCompletionService.CompletionResult result = ProfileCompletionService.calculate(user);
            int score = result.score();
            completionBadge.setText("Profile " + score + "%");

            completionBadge
                    .getStyleClass()
                    .removeAll("login-badge-primary", "login-badge-success", "login-badge-warning");
            if (score >= 90) {
                completionBadge.getStyleClass().add("login-badge-success");
            } else if (score >= 60) {
                completionBadge.getStyleClass().add("login-badge-primary");
            } else {
                completionBadge.getStyleClass().add("login-badge-warning");
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

        private static String formatState(User.State state) {
            if (state == null) {
                return "Unknown";
            }
            String raw = state.name().toLowerCase(java.util.Locale.ROOT);
            return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }

        private static String formatActivity(Instant updatedAt) {
            // Use updatedAt as a lightweight proxy for recent activity.
            if (updatedAt == null) {
                return "Active recently";
            }

            java.time.Duration duration = java.time.Duration.between(updatedAt, Instant.now());
            if (duration.isNegative()) {
                duration = java.time.Duration.ZERO;
            }

            long minutes = duration.toMinutes();
            if (minutes < 1) {
                return "Active just now";
            }
            if (minutes < 60) {
                return "Active " + minutes + "m ago";
            }

            long hours = duration.toHours();
            if (hours < 24) {
                return "Active " + hours + "h ago";
            }

            long days = duration.toDays();
            if (days < 7) {
                return "Active " + days + "d ago";
            }

            long weeks = days / 7;
            if (weeks < 5) {
                return "Active " + weeks + "w ago";
            }

            long months = days / 30;
            return "Active " + months + "mo ago";
        }
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleLogin() {
        if (viewModel.login()) {
            logger.info("Login successful, navigating to Dashboard");
            NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
        }
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleCreateAccount() {
        logger.info("Opening account creation dialog");
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
            emptyListPlaceholder.setText("No profiles yet. Create one to get started.");
            return;
        }

        if (filter != null && !filter.isBlank() && viewModel.getFilteredUsers().isEmpty()) {
            emptyListPlaceholder.setText("No profiles match \"" + formatFilter(filter) + "\".");
            return;
        }

        emptyListPlaceholder.setText("No profiles to show.");
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
        int target = Math.max(0, Math.min(size - 1, current + delta));
        userListView.getSelectionModel().select(target);
        userListView.scrollTo(target);
    }

    private void showCreateAccountDialog() {
        // Create the dialog
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Create New Account");
        dialog.setHeaderText(null); // No header for cleaner look

        // Apply dark styling to dialog
        String themeStylesheet = resolveStylesheet("/css/theme.css");
        if (themeStylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(themeStylesheet);
        }
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        // Set the button types
        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Create the form with VBox for better spacing
        VBox content = new VBox(20);
        content.setPadding(new Insets(30, 40, 20, 40));
        content.setStyle("-fx-background-color: -fx-surface-dark;");

        // Title
        Label titleLabel = new Label("Create New Account");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: -fx-text-primary;");

        // Name field
        VBox nameBox = new VBox(8);
        Label nameLabel = new Label("Name:");
        nameLabel.setStyle(SECONDARY_TEXT_STYLE);
        TextField nameField = new TextField();
        nameField.setPromptText("Enter your name");
        nameField.setPrefWidth(280);
        nameField.setStyle("-fx-background-color: -fx-surface-elevated; -fx-text-fill: white; "
                + "-fx-prompt-text-fill: -fx-text-muted; -fx-background-radius: 8;");
        nameBox.getChildren().addAll(nameLabel, nameField);

        // Age spinner
        VBox ageBox = new VBox(8);
        Label ageLabel = new Label("Age:");
        ageLabel.setStyle(SECONDARY_TEXT_STYLE);
        Spinner<Integer> ageSpinner = new Spinner<>();
        ageSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(18, 100, 25));
        ageSpinner.setEditable(true);
        ageSpinner.setPrefWidth(120);
        ageBox.getChildren().addAll(ageLabel, ageSpinner);

        // Gender combo
        VBox genderBox = new VBox(8);
        Label genderLabel = new Label("Gender:");
        genderLabel.setStyle(SECONDARY_TEXT_STYLE);
        ComboBox<Gender> genderCombo = new ComboBox<>();
        genderCombo.getItems().addAll(Gender.values());
        genderCombo.setValue(Gender.OTHER);
        genderCombo.setPrefWidth(200);
        genderCombo.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 8;");
        genderCombo.setCellFactory(lv -> createStyledCell());
        genderCombo.setButtonCell(createStyledCell());
        genderBox.getChildren().addAll(genderLabel, genderCombo);

        // Interested In combo
        VBox interestedBox = new VBox(8);
        Label interestedLabel = new Label("Interested In:");
        interestedLabel.setStyle(SECONDARY_TEXT_STYLE);
        ComboBox<Gender> interestedInCombo = new ComboBox<>();
        interestedInCombo.getItems().addAll(Gender.values());
        interestedInCombo.setValue(Gender.OTHER);
        interestedInCombo.setPrefWidth(200);
        interestedInCombo.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 8;");
        interestedInCombo.setCellFactory(lv -> createStyledCell());
        interestedInCombo.setButtonCell(createStyledCell());
        interestedBox.getChildren().addAll(interestedLabel, interestedInCombo);

        // Error label
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 13px;");
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
            logger.info("Created new user: {}", user.getName());
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
                    setStyle("-fx-background-color: #1e293b;");
                } else {
                    setText(item.name());
                    setStyle("-fx-background-color: #1e293b; -fx-text-fill: white; -fx-padding: 8 12;");
                }
            }
        };
    }

    private String resolveStylesheet(String path) {
        URL resource = getClass().getResource(path);
        if (resource == null) {
            logger.warn("Stylesheet not found: {}", path);
            return null;
        }
        return resource.toExternalForm();
    }
}
