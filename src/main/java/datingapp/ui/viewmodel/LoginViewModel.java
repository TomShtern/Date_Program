package datingapp.ui.viewmodel;

import datingapp.core.Dealbreakers;
import datingapp.core.Preferences.PacePreferences;
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
import javafx.collections.transformation.FilteredList;
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
    private final FilteredList<User> filteredUsers;
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty loginDisabled = new SimpleBooleanProperty(true);
    private final StringProperty filterText = new SimpleStringProperty("");

    // For account creation dialog
    private final StringProperty newUserName = new SimpleStringProperty("");
    private final StringProperty newUserAge = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    private User selectedUser;

    public LoginViewModel(User.Storage userStorage) {
        this.userStorage = userStorage;
        this.filteredUsers = new FilteredList<>(users, user -> true);
        this.filterText.addListener((obs, oldVal, newVal) -> applyFilter(newVal));
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

    public FilteredList<User> getFilteredUsers() {
        return filteredUsers;
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

    public StringProperty filterTextProperty() {
        return filterText;
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
     * If the user is INCOMPLETE but has required fields, attempts activation.
     * Returns true if successful.
     */
    public boolean login() {
        if (selectedUser == null) {
            return false;
        }

        logger.info(
                "Logging in as user: {} ({}) - state={}, isComplete={}",
                selectedUser.getName(),
                selectedUser.getId(),
                selectedUser.getState(),
                selectedUser.isComplete());

        // Attempt to auto-complete incomplete users by filling missing fields
        if (selectedUser.getState() == User.State.INCOMPLETE) {
            autoCompleteUserProfile(selectedUser);

            // Try to activate if now complete
            if (selectedUser.isComplete() && selectedUser.getState() == User.State.INCOMPLETE) {
                try {
                    selectedUser.activate();
                    userStorage.save(selectedUser);
                    logger.info("Auto-activated user {} after completing profile", selectedUser.getName());
                } catch (IllegalStateException e) {
                    logger.warn("Could not auto-activate user {}: {}", selectedUser.getName(), e.getMessage());
                }
            }
        }

        // Set user in the global UI session
        UISession.getInstance().setCurrentUser(selectedUser);

        return true;
    }

    /**
     * Fills in missing profile fields with defaults to help user become activatable.
     */
    private void autoCompleteUserProfile(User user) {
        boolean modified = false;

        // Bio default
        if (user.getBio() == null || user.getBio().isBlank()) {
            user.setBio("New to the app! 👋");
            modified = true;
            logger.debug("Auto-filled bio for user {}", user.getName());
        }

        // Photo URL default
        if (user.getPhotoUrls() == null || user.getPhotoUrls().isEmpty()) {
            user.setPhotoUrls(List.of("placeholder://default-avatar"));
            modified = true;
            logger.debug("Auto-filled photo URL for user {}", user.getName());
        }

        // Location default (Tel Aviv)
        if (user.getLat() == 0 && user.getLon() == 0) {
            user.setLocation(32.0853, 34.7818);
            modified = true;
            logger.debug("Auto-filled location for user {}", user.getName());
        }

        // Pace preferences default
        if (user.getPacePreferences() == null || !user.getPacePreferences().isComplete()) {
            PacePreferences pacePrefs = new PacePreferences(
                    PacePreferences.MessagingFrequency.OFTEN,
                    PacePreferences.TimeToFirstDate.FEW_DAYS,
                    PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                    PacePreferences.DepthPreference.DEPENDS_ON_VIBE);
            user.setPacePreferences(pacePrefs);
            modified = true;
            logger.debug("Auto-filled pace preferences for user {}", user.getName());
        }

        // Dealbreakers default (none) - marks section as reviewed
        if (user.getDealbreakers() == null) {
            user.setDealbreakers(Dealbreakers.none());
            modified = true;
            logger.debug("Auto-filled dealbreakers (none) for user {}", user.getName());
        }

        if (modified) {
            userStorage.save(user);
            logger.info("Auto-completed profile fields for user {}", user.getName());
        }
    }

    /**
     * Creates a new user account with complete profile data to enable activation.
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

            // Create a new user with minimal constructor, then set all required fields
            User newUser = new User(UUID.randomUUID(), name.trim());
            newUser.setBirthDate(birthDate);
            newUser.setGender(gender);
            newUser.setInterestedIn(EnumSet.of(interestedIn));

            // Set default location (Tel Aviv area as a reasonable default)
            newUser.setLocation(32.0853, 34.7818);

            // Set default bio placeholder - required for profile completion
            newUser.setBio("New to the app! 👋");

            // Set default age range preferences (±5 years from user's age)
            int minAge = Math.max(18, age - 5);
            int maxAge = Math.min(100, age + 5);
            newUser.setAgeRange(minAge, maxAge);

            // Set default distance preference
            newUser.setMaxDistanceKm(50);

            // Set default photo placeholder - required for profile completion
            newUser.setPhotoUrls(List.of("placeholder://default-avatar"));

            // Set default pace preferences - required for profile completion
            PacePreferences pacePrefs = new PacePreferences(
                    PacePreferences.MessagingFrequency.OFTEN,
                    PacePreferences.TimeToFirstDate.FEW_DAYS,
                    PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                    PacePreferences.DepthPreference.DEPENDS_ON_VIBE);
            newUser.setPacePreferences(pacePrefs);

            // Set default dealbreakers (none) - marks section as reviewed for profile completion
            newUser.setDealbreakers(Dealbreakers.none());

            // Save to storage
            userStorage.save(newUser);

            // Now attempt to activate the user since profile is complete
            if (newUser.isComplete()) {
                newUser.activate();
                userStorage.save(newUser);
                logger.info("User {} is complete and activated. State: {}", newUser.getName(), newUser.getState());
            } else {
                logger.warn(
                        "User {} created but not complete. State: {} (isComplete={})",
                        newUser.getName(),
                        newUser.getState(),
                        newUser.isComplete());
            }

            // Refresh the user list
            refreshUsers();

            logger.info(
                    "Created new user: {} with ID {}, State: {}",
                    newUser.getName(),
                    newUser.getId(),
                    newUser.getState());

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

    private void applyFilter(String text) {
        String normalized = text == null ? "" : text.trim().toLowerCase();
        if (normalized.isBlank()) {
            filteredUsers.setPredicate(user -> true);
            return;
        }

        filteredUsers.setPredicate(user -> {
            if (user == null) {
                return false;
            }

            String name = user.getName() == null ? "" : user.getName().toLowerCase();
            String state = user.getState() == null ? "" : user.getState().name().toLowerCase();
            String ageText = user.getAge() > 0 ? String.valueOf(user.getAge()) : "";
            String verifiedTag = Boolean.TRUE.equals(user.isVerified()) ? "verified" : "";

            String searchable = String.join(" ", name, state, ageText, verifiedTag);
            for (String term : normalized.split("\\s+")) {
                if (!searchable.contains(term)) {
                    return false;
                }
            }
            return true;
        });
    }
}
