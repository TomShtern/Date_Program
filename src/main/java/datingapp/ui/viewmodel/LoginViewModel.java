package datingapp.ui.viewmodel;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.ui.async.AsyncErrorRouter;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.async.ViewModelAsyncScope;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final AppConfig config;
    private final AppSession session;
    private final UiUserStore userStore;
    private final ViewModelAsyncScope asyncScope;
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

    public LoginViewModel(UiUserStore userStore, AppConfig config, AppSession session) {
        this(userStore, config, session, new JavaFxUiThreadDispatcher());
    }

    public LoginViewModel(
            UiUserStore userStore, AppConfig config, AppSession session, UiThreadDispatcher uiDispatcher) {
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.asyncScope = createAsyncScope(uiDispatcher);
        this.filterText.addListener((obs, oldVal, newVal) -> applyFilter(newVal));
        loadUsers();
    }

    /**
     * Disposes resources held by this ViewModel.
     * Should be called when the ViewModel is no longer needed.
     */
    public void dispose() {
        disposed.set(true);
        asyncScope.dispose();
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

        asyncScope.runLatest("login-users", "load users", userStore::findAll, allUsers -> {
            if (disposed.get()) {
                return;
            }
            users.clear();
            users.addAll(allUsers);
            applyFilter(filterText.get());
            logInfo("Loaded {} users for login selection.", users.size());
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

    /** Returns the minimum allowed age from configuration. */
    public int getMinAge() {
        return config.validation().minAge();
    }

    /** Returns the maximum allowed age from configuration. */
    public int getMaxAge() {
        return config.validation().maxAge();
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
        session.setCurrentUser(selectedUser);

        return true;
    }

    /**
     * Fills in missing profile fields with defaults to help user become
     * activatable.
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
        if (!user.hasLocation()) {
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

        if (age < config.validation().minAge() || age > config.validation().maxAge()) {
            errorMessage.set("Age must be between " + config.validation().minAge() + " and "
                    + config.validation().maxAge());
            return null;
        }

        if (gender == null) {
            errorMessage.set("Gender must be provided");
            return null;
        }

        if (interestedIn == null) {
            errorMessage.set("InterestedIn must be provided");
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
            int minAge = Math.max(config.validation().minAge(), age - 5);
            int maxAge = Math.min(config.validation().maxAge(), age + 5);
            newUser.setAgeRange(
                    minAge,
                    maxAge,
                    config.validation().minAge(),
                    config.validation().maxAge());

            // Set default distance preference
            newUser.setMaxDistanceKm(50, config.matching().maxDistanceKm());

            // Set default photo placeholder - required for profile completion
            newUser.setPhotoUrls(List.of("placeholder://default-avatar"));

            // Set default pace preferences - required for profile completion
            PacePreferences pacePrefs = new PacePreferences(
                    PacePreferences.MessagingFrequency.OFTEN,
                    PacePreferences.TimeToFirstDate.FEW_DAYS,
                    PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                    PacePreferences.DepthPreference.DEPENDS_ON_VIBE);
            newUser.setPacePreferences(pacePrefs);

            // Set default dealbreakers (none) - marks section as reviewed for profile
            // completion
            newUser.setDealbreakers(Dealbreakers.none());

            // Now attempt to activate the user since profile is complete
            if (newUser.isComplete()) {
                newUser.activate();
                logInfo("User {} is complete and activated. MatchState: {}", newUser.getName(), newUser.getState());
            } else {
                logWarn(
                        "User {} created but not complete. MatchState: {} (isComplete={})",
                        newUser.getName(),
                        newUser.getState(),
                        newUser.isComplete());
            }
            userStore.save(newUser);

            // Refresh the user list
            refreshUsers();

            logInfo(
                    "Created new user: {} with ID {}, MatchState: {}",
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
        asyncScope.dispatchToUi(() -> errorHandler.onError(message));
    }

    private void setLoadingState(boolean isLoading) {
        if (loading.get() != isLoading) {
            loading.set(isLoading);
        }
    }

    private ViewModelAsyncScope createAsyncScope(UiThreadDispatcher uiDispatcher) {
        UiThreadDispatcher dispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher cannot be null");
        ViewModelAsyncScope scope = new ViewModelAsyncScope(
                "login", dispatcher, new AsyncErrorRouter(logger, dispatcher, () -> errorHandler));
        scope.setLoadingStateConsumer(this::setLoadingState);
        return scope;
    }

    private void applyFilter(String text) {
        String normalized = normalizeFilter(text);
        if (normalized.isBlank()) {
            filteredUsers.setAll(users);
            return;
        }

        // Pass the injected config's timezone so age calculation is consistent
        // with the rest of the application and never falls back to
        // AppConfig.defaults().
        java.time.ZoneId zone = config.safety().userTimeZone();
        List<User> matches = users.stream()
                .filter(user -> matchesFilter(user, normalized, zone))
                .toList();
        filteredUsers.setAll(matches);
    }

    private static String normalizeFilter(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesFilter(User user, String normalized, java.time.ZoneId zone) {
        if (user == null) {
            return false;
        }

        String searchable = buildSearchable(user, zone);
        return containsAllTerms(searchable, normalized);
    }

    /**
     * Builds a lowercase searchable string for the given user.
     *
     * @param user the user to index
     * @param zone the timezone to use for age calculation — must come from the
     *             injected {@code config} so we never silently fall back to
     *             {@code AppConfig.defaults()}
     */
    private static String buildSearchable(User user, java.time.ZoneId zone) {
        String name = user.getName() == null ? "" : user.getName().toLowerCase(Locale.ROOT);
        String state = user.getState() == null ? "" : user.getState().name().toLowerCase(Locale.ROOT);
        int age = user.getAge(zone);
        String ageText = age > 0 ? String.valueOf(age) : "";
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
