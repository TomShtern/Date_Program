package datingapp.ui.controller;

import datingapp.core.User;
import datingapp.core.User.Gender;
import datingapp.ui.NavigationService;
import datingapp.ui.util.UiAnimations;
import datingapp.ui.viewmodel.LoginViewModel;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.application.Platform;
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
import javafx.scene.layout.HBox;
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

    @FXML
    private javafx.scene.layout.StackPane rootPane;

    @FXML
    private ListView<User> userListView;

    @FXML
    private Button loginButton;

    @FXML
    private Button createAccountButton;

    private final LoginViewModel viewModel;

    public LoginController(LoginViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Bind ListView items to ViewModel's observable list
        userListView.setItems(viewModel.getUsers());

        // Custom cell factory to show user name, status, and completion
        userListView.setCellFactory(lv -> new UserListCell());

        // Listen for selection changes using Subscription API
        addSubscription(userListView.getSelectionModel().selectedItemProperty().subscribe(viewModel::setSelectedUser));

        // Bind button disable state to ViewModel property
        loginButton.disableProperty().bind(viewModel.loginDisabledProperty());

        // Select first user if available
        if (!viewModel.getUsers().isEmpty()) {
            userListView.getSelectionModel().selectFirst();
        }

        // Apply fade-in animation
        UiAnimations.fadeIn(rootPane, 800);

        // Suppress unused warnings for FXML-injected fields
        if (rootPane != null && createAccountButton != null) {
            // Fields are used by FXML
        }
    }

    /** Custom list cell for displaying user accounts with avatar icons. */
    private static class UserListCell extends ListCell<User> {
        private final HBox container = new HBox(15);
        private final org.kordamp.ikonli.javafx.FontIcon avatarIcon =
                new org.kordamp.ikonli.javafx.FontIcon("mdi2a-account-circle");
        private final VBox textBox = new VBox(2);
        private final Label nameLabel = new Label();
        private final Label detailsLabel = new Label();

        public UserListCell() {
            avatarIcon.setIconSize(32);
            avatarIcon.getStyleClass().add("icon-primary");
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
            detailsLabel.getStyleClass().add("text-secondary");
            detailsLabel.setStyle("-fx-font-size: 12px;");
            textBox.getChildren().addAll(nameLabel, detailsLabel);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(8, 12, 8, 12));
            container.getChildren().addAll(avatarIcon, textBox);
        }

        @Override
        protected void updateItem(User user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setGraphic(null);
            } else {
                nameLabel.setText(user.getName() + ", " + user.getAge());

                StringBuilder sb = new StringBuilder();
                sb.append(user.getState());
                if (Boolean.TRUE.equals(user.isVerified())) {
                    sb.append(" • Verified ✓");
                }
                detailsLabel.setText(sb.toString());

                setGraphic(container);
            }
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

    private void showCreateAccountDialog() {
        // Create the dialog
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Create New Account");
        dialog.setHeaderText(null); // No header for cleaner look

        // Apply dark styling to dialog
        dialog.getDialogPane()
                .getStylesheets()
                .add(getClass().getResource("/css/theme.css").toExternalForm());
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
            if (dialogButton == createButtonType) {
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
            if (user != null) {
                logger.info("Created new user: {}", user.getName());
                // Select the newly created user
                userListView.getSelectionModel().select(user);
            }
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
}
