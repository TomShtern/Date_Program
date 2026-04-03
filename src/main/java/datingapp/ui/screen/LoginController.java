package datingapp.ui.screen;

import datingapp.core.AppClock;
import datingapp.core.model.User;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.ProfileService;
import datingapp.ui.NavigationService;
import datingapp.ui.UiAnimations;
import datingapp.ui.UiConstants;
import datingapp.ui.UiFeedbackService;
import datingapp.ui.viewmodel.LoginViewModel;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
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

    // CSS Class Names
    private static final String CSS_LOGIN_HINT = "login-hint";

    // Placeholder Messages
    private static final String MSG_NO_PROFILES_YET = "No profiles yet. Create one to get started.";
    private static final String MSG_NO_PROFILES_MATCH = "No profiles match \"";
    private static final String MSG_NO_PROFILES_TO_SHOW = "No profiles to show.";

    // Log Messages
    private static final String LOG_LOGIN_SUCCESS = "Login successful, navigating to {}";
    private static final String LOG_OPENING_DIALOG = "Opening account creation dialog";
    private static final String LOG_USER_CREATED = "Created new user: {}";

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
    private final ProfileService profileCompletionService;
    private final ZoneId userTimeZone;
    private final Label emptyListPlaceholder = new Label();

    public LoginController(LoginViewModel viewModel, ProfileService profileCompletionService, ZoneId userTimeZone) {
        this.viewModel = viewModel;
        this.profileCompletionService = profileCompletionService;
        this.userTimeZone = Objects.requireNonNull(userTimeZone, "userTimeZone cannot be null");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.setErrorHandler(UiFeedbackService::showError);

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
        userListView.setCellFactory(lv -> UserListCellRenderer.create(profileCompletionService, userTimeZone));
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
            NavigationService.ViewType destination = viewModel.resolvePostLoginDestination();
            logInfo(LOG_LOGIN_SUCCESS, destination);
            NavigationService.getInstance().navigateTo(destination);
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
        if (trimmed.length() <= UiConstants.NAME_FORMAT_MAX_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, UiConstants.NAME_FORMAT_MAX_CHARS - 3) + "...";
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
        Dialog<User> dialog = CreateAccountDialogFactory.create(rootPane, viewModel);

        Optional<User> result = dialog.showAndWait();
        result.ifPresent(user -> {
            logInfo(LOG_USER_CREATED, user.getName());
            // Select the newly created user
            userListView.getSelectionModel().select(user);
        });

        // Clear form on close
        viewModel.clearCreateForm();
    }

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    // --- User list cell rendering (inlined from UserListCellFactory) ---

    private static final class UserListCellRenderer {
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

        @SuppressWarnings("PMD.UnnecessaryConstructor")
        private UserListCellRenderer() {}

        static ListCell<User> create(ProfileService profileCompletionService, ZoneId userTimeZone) {
            return new UserListCell(profileCompletionService, userTimeZone);
        }

        private static final class UserListCell extends ListCell<User> {
            private static final double AVATAR_SIZE = 44;
            private static final double SELECT_SCALE = 1.03;

            private final ProfileService profileCompletionService;
            private final ZoneId userTimeZone;
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

            private UserListCell(ProfileService profileCompletionService, ZoneId userTimeZone) {
                this.profileCompletionService = profileCompletionService;
                this.userTimeZone = userTimeZone;
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
                container.setPadding(new Insets(
                        UiConstants.PADDING_SMALL,
                        UiConstants.PADDING_MEDIUM,
                        UiConstants.PADDING_SMALL,
                        UiConstants.PADDING_MEDIUM));
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
                    int age = user.getAge(userTimeZone).orElse(0);
                    nameLabel.setText(user.getName() + ", " + age);

                    StringBuilder sb = new StringBuilder(formatState(user.getState()));
                    if (user.isVerified()) {
                        sb.append(TEXT_VERIFIED);
                    }
                    detailsLabel.setText(sb.toString());

                    updateCompletionBadge(user);
                    activityBadge.setText(formatActivity(user.getUpdatedAt()));
                    avatarView.setImage(UiFeedbackService.getAvatar(resolveAvatarPath(user), AVATAR_SIZE));

                    setGraphic(container);
                }
            }

            private void updateCompletionBadge(User user) {
                ProfileService.CompletionResult result = profileCompletionService.calculate(user);
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
                String raw = state.name().toLowerCase(Locale.ROOT);
                return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            }

            private static String formatActivity(Instant updatedAt) {
                if (updatedAt == null) {
                    return TEXT_ACTIVE_RECENTLY;
                }

                java.time.Duration duration = java.time.Duration.between(updatedAt, AppClock.now());
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
    }
}
