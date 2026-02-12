package datingapp.ui.viewmodel;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.MatchPreferences.Dealbreakers;
import datingapp.core.model.MatchPreferences.PacePreferences;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.ui.viewmodel.data.UiDataAdapters.UiUserStore;
import datingapp.ui.viewmodel.shared.ViewModelErrorSink;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the Login screen.
 * Handles user listing, selection, login, and account creation.
 */
public class LoginViewModel {
    private static final Logger logger = LoggerFactory.getLogger(LoginViewModel.class);
    private static final AppConfig CONFIG = AppConfig.defaults();

    private final UiUserStore userStore;
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObservableList<User> filteredUsers = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty loginDisabled = new SimpleBooleanProperty(true);
    private final StringProperty filterText = new SimpleStringProperty("");

    // For account creation dialog
    private final StringProperty newUserName = new SimpleStringProperty("");
    private final StringProperty newUserAge = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");

    private ViewModelErrorSink errorHandler;

    private User selectedUser;

    /** Track disposed state to prevent operations after cleanup. */
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public LoginViewModel(UiUserStore userStore) {
        this.userStore = userStore;
        this.filterText.addListener((obs, oldVal, newVal) -> applyFilter(newVal));
        loadUsers();
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        users.clear();
        filteredUsers.clear();
    }

    public void setErrorHandler(ViewModelErrorSink handler) {
        this.errorHandler = handler;
    }

    /**
     * Loads the list of available users from storage.
     */
    private void loadUsers() {
        if (disposed.get()) {
            return;
        }
        loading.set(true);
        Thread.ofVirtual().start(() -> {
            try {
                List<User> allUsers = userStore.findAll();
                if (disposed.get()) {
                    return;
                }
                Platform.runLater(() -> {
                    users.clear();
                    users.addAll(allUsers);
                    applyFilter(filterText.get());
                    loading.set(false);
                    logInfo("Loaded {} users for login selection.", users.size());
                });
            } catch (Exception e) {
                Platform.runLater(() -> loading.set(false));
                logError("Failed to load users for login: {}", e.getMessage(), e);
                notifyError("Failed to load users", e);
            }
        });
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

    public ObservableList<User> getFilteredUsers() {
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

    @Nullable
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

        logInfo(
                "Logging in as user: {} ({}) - state={}, isComplete={}",
                selectedUser.getName(),
                selectedUser.getId(),
                selectedUser.getState(),
                selectedUser.isComplete());

        // Attempt to auto-complete incomplete users by filling missing fields
        if (selectedUser.getState() == UserState.INCOMPLETE) {
            autoCompleteUserProfile(selectedUser);

            // Try to activate if now complete
            if (selectedUser.isComplete() && selectedUser.getState() == UserState.INCOMPLETE) {
                try {
                    selectedUser.activate();
                    userStore.save(selectedUser);
                    logInfo("Auto-activated user {} after completing profile", selectedUser.getName());
                } catch (IllegalStateException e) {
                    logWarn("Could not auto-activate user {}: {}", selectedUser.getName(), e.getMessage());
                }
            }
        }

        // Set user in the global UI session
        AppSession.getInstance().setCurrentUser(selectedUser);

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
            logDebug("Auto-filled bio for user {}", user.getName());
        }

        // Photo URL default
        if (user.getPhotoUrls() == null || user.getPhotoUrls().isEmpty()) {
            user.setPhotoUrls(List.of("placeholder://default-avatar"));
            modified = true;
            logDebug("Auto-filled photo URL for user {}", user.getName());
        }

        // Location default (Tel Aviv)
        if (user.getLat() == 0 && user.getLon() == 0) {
            user.setLocation(32.0853, 34.7818);
            modified = true;
            logDebug("Auto-filled location for user {}", user.getName());
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
            logDebug("Auto-filled pace preferences for user {}", user.getName());
        }

        // Dealbreakers default (none) - marks section as reviewed
        if (user.getDealbreakers() == null) {
            user.setDealbreakers(Dealbreakers.none());
            modified = true;
            logDebug("Auto-filled dealbreakers (none) for user {}", user.getName());
        }

        if (modified) {
            userStore.save(user);
            logInfo("Auto-completed profile fields for user {}", user.getName());
        }
    }

    /**
     * Creates a new user account with complete profile data to enable activation.
     * Returns the created user, or null if creation failed.
     */
    @Nullable
    public User createUser(String name, int age, Gender gender, Gender interestedIn) {
        errorMessage.set("");

        if (name == null || name.trim().isEmpty()) {
            errorMessage.set("Name cannot be empty");
            return null;
        }

        if (age < CONFIG.minAge() || age > CONFIG.maxAge()) {
            errorMessage.set("Age must be between " + CONFIG.minAge() + " and " + CONFIG.maxAge());
            return null;
        }

        try {
            // Calculate birth date from age
            LocalDate birthDate = AppClock.today().minusYears(age);

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

            // Now attempt to activate the user since profile is complete
            if (newUser.isComplete()) {
                newUser.activate();
                logInfo("User {} is complete and activated. State: {}", newUser.getName(), newUser.getState());
            } else {
                logWarn(
                        "User {} created but not complete. State: {} (isComplete={})",
                        newUser.getName(),
                        newUser.getState(),
                        newUser.isComplete());
            }
            userStore.save(newUser);

            // Refresh the user list
            refreshUsers();

            logInfo(
                    "Created new user: {} with ID {}, State: {}",
                    newUser.getName(),
                    newUser.getId(),
                    newUser.getState());

            return newUser;
        } catch (Exception e) {
            logError("Failed to create user: {}", e.getMessage(), e);
            errorMessage.set("Failed to create user: " + e.getMessage());
            notifyError("Failed to create user", e);
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

    private void logInfo(String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(message, args);
        }
    }

    private void logDebug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, args);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(message, args);
        }
    }

    private void logError(String message, Object... args) {
        if (logger.isErrorEnabled()) {
            logger.error(message, args);
        }
    }

    private void notifyError(String userMessage, Exception e) {
        if (errorHandler == null) {
            return;
        }
        String detail = e.getMessage();
        String message = detail == null || detail.isBlank() ? userMessage : userMessage + ": " + detail;
        if (Platform.isFxApplicationThread()) {
            errorHandler.onError(message);
        } else {
            Platform.runLater(() -> errorHandler.onError(message));
        }
    }

    private void applyFilter(String text) {
        String normalized = normalizeFilter(text);
        if (normalized.isBlank()) {
            filteredUsers.setAll(users);
            return;
        }

        List<User> matches =
                users.stream().filter(user -> matchesFilter(user, normalized)).toList();
        filteredUsers.setAll(matches);
    }

    private static String normalizeFilter(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesFilter(User user, String normalized) {
        if (user == null) {
            return false;
        }

        String searchable = buildSearchable(user);
        return containsAllTerms(searchable, normalized);
    }

    private static String buildSearchable(User user) {
        String name = user.getName() == null ? "" : user.getName().toLowerCase(Locale.ROOT);
        String state = user.getState() == null ? "" : user.getState().name().toLowerCase(Locale.ROOT);
        String ageText = user.getAge() > 0 ? String.valueOf(user.getAge()) : "";
        String verifiedTag = user.isVerified() ? "verified" : "";
        return String.join(" ", name, state, ageText, verifiedTag);
    }

    private static boolean containsAllTerms(String searchable, String normalized) {
        for (String term : normalized.split("\\s+")) {
            if (!searchable.contains(term)) {
                return false;
            }
        }
        return true;
    }
}
