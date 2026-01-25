package datingapp.ui.viewmodel;

import datingapp.core.User;
import datingapp.core.User.Gender;
import datingapp.ui.ViewModelFactory.UISession;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Login screen.
 * Handles user listing, selection, login, and account creation.
 */
public class LoginViewModel {
    private static final Logger logger = LoggerFactory.getLogger(LoginViewModel.class);

    private final User.Storage userStorage;
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty loginDisabled = new SimpleBooleanProperty(true);

    // For account creation dialog
    private final StringProperty newUserName = new SimpleStringProperty("");
    private final StringProperty newUserAge = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    private User selectedUser;

    public LoginViewModel(User.Storage userStorage) {
        this.userStorage = userStorage;
        loadUsers();
    }

    /**
     * Loads the list of available users from storage.
     */
    private void loadUsers() {
        loading.set(true);
        users.clear();
        List<User> allUsers = userStorage.findAll();
        users.addAll(allUsers);
        loading.set(false);
        logger.info("Loaded {} users for login selection.", users.size());
    }

    /**
     * Refreshes the user list.
     */
    public void refreshUsers() {
        loadUsers();
    }

    public ObservableList<User> getUsers() {
        return users;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public BooleanProperty loginDisabledProperty() {
        return loginDisabled;
    }

    public StringProperty newUserNameProperty() {
        return newUserName;
    }

    public StringProperty newUserAgeProperty() {
        return newUserAge;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public void setSelectedUser(User user) {
        this.selectedUser = user;
        this.loginDisabled.set(user == null);
    }

    public User getSelectedUser() {
        return selectedUser;
    }

    /**
     * Performs login with the selected user.
     * Sets the user in the global UISession.
     * Returns true if successful.
     */
    public boolean login() {
        if (selectedUser == null) {
            return false;
        }

        logger.info("Logging in as user: {} ({})", selectedUser.getName(), selectedUser.getId());

        // Set user in the global UI session
        UISession.getInstance().setCurrentUser(selectedUser);

        return true;
    }

    /**
     * Creates a new user account.
     * Returns the created user, or null if creation failed.
     */
    public User createUser(String name, int age, Gender gender, Gender interestedIn) {
        errorMessage.set("");

        if (name == null || name.trim().isEmpty()) {
            errorMessage.set("Name cannot be empty");
            return null;
        }

        if (age < 18 || age > 120) {
            errorMessage.set("Age must be between 18 and 120");
            return null;
        }

        try {
            // Calculate birth date from age
            LocalDate birthDate = LocalDate.now().minusYears(age);

            // Create a new user with minimal constructor, then set fields
            User newUser = new User(UUID.randomUUID(), name.trim());
            newUser.setBirthDate(birthDate);
            newUser.setGender(gender);
            newUser.setInterestedIn(EnumSet.of(interestedIn));
            // Set default location (0,0 means no location set)
            newUser.setLocation(0, 0);

            // Save to storage
            userStorage.save(newUser);

            // Refresh the user list
            refreshUsers();

            logger.info("Created new user: {} with ID {}", newUser.getName(), newUser.getId());

            return newUser;
        } catch (Exception e) {
            logger.error("Failed to create user: {}", e.getMessage(), e);
            errorMessage.set("Failed to create user: " + e.getMessage());
            return null;
        }
    }

    /**
     * Clears the account creation form fields.
     */
    public void clearCreateForm() {
        newUserName.set("");
        newUserAge.set("");
        errorMessage.set("");
    }
}
