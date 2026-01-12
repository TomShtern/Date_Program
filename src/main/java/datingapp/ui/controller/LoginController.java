package datingapp.ui.controller;

import datingapp.core.User;
import datingapp.core.User.Gender;
import datingapp.ui.NavigationService;
import datingapp.ui.ViewFactory;
import datingapp.ui.viewmodel.LoginViewModel;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.application.Platform;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Login screen (login.fxml).
 * Handles user selection, login, and account creation dialog.
 */
public class LoginController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML
    private VBox root;

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
        userListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(user.getName());
                    sb.append(", ").append(user.getAge());
                    sb.append(" (").append(user.getState()).append(")");

                    if (Boolean.TRUE.equals(user.isVerified())) {
                        sb.append(" ✓");
                    }

                    setText(sb.toString());
                    getStyleClass().add("user-list-cell");
                }
            }
        });

        // Listen for selection changes
        userListView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> viewModel.setSelectedUser(newVal));

        // Bind button disable state to ViewModel property
        loginButton.disableProperty().bind(viewModel.loginDisabledProperty());

        // Select first user if available
        if (!viewModel.getUsers().isEmpty()) {
            userListView.getSelectionModel().selectFirst();
        }

        // Suppress unused warnings for FXML-injected fields
        if (root != null && createAccountButton != null) {
            // Fields are used by FXML
        }
    }

    @SuppressWarnings("unused") // Called by FXML
    @FXML
    private void handleLogin() {
        if (viewModel.login()) {
            logger.info("Login successful, navigating to Dashboard");
            NavigationService.getInstance().navigateTo(ViewFactory.ViewType.DASHBOARD);
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
        dialog.setHeaderText("Enter your details to create an account");

        // Apply dark styling to dialog
        dialog.getDialogPane()
                .getStylesheets()
                .add(getClass().getResource("/css/theme.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        // Set the button types
        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Create the form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Your name");
        nameField.setPrefWidth(200);

        Spinner<Integer> ageSpinner = new Spinner<>();
        ageSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(18, 100, 25));
        ageSpinner.setEditable(true);
        ageSpinner.setPrefWidth(100);

        ComboBox<Gender> genderCombo = new ComboBox<>();
        genderCombo.getItems().addAll(Gender.values());
        genderCombo.setValue(Gender.OTHER);
        genderCombo.setPrefWidth(150);

        ComboBox<Gender> interestedInCombo = new ComboBox<>();
        interestedInCombo.getItems().addAll(Gender.values());
        interestedInCombo.setValue(Gender.OTHER);
        interestedInCombo.setPrefWidth(150);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #ff6b6b;");
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Age:"), 0, 1);
        grid.add(ageSpinner, 1, 1);
        grid.add(new Label("Gender:"), 0, 2);
        grid.add(genderCombo, 1, 2);
        grid.add(new Label("Interested In:"), 0, 3);
        grid.add(interestedInCombo, 1, 3);
        grid.add(errorLabel, 0, 4, 2, 1);

        dialog.getDialogPane().setContent(grid);

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
}
