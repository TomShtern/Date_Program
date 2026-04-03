package datingapp.ui.viewmodel;

import datingapp.app.support.UserPresentationSupport;
import datingapp.app.usecase.profile.ProfileNormalizationSupport;
import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.ui.NavigationService;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.Nullable;

/**
 * ViewModel for the Login screen.
 * Handles user listing, selection, login, and account creation.
 */
public class LoginViewModel extends BaseViewModel {

    private final AppConfig config;
    private final AppSession session;
    private final UiUserStore userStore;
    private final ProfileActivationPolicy activationPolicy;
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObservableList<User> filteredUsers = FXCollections.observableArrayList();
    private final ObservableList<User> readOnlyUsers = FXCollections.unmodifiableObservableList(users);
    private final ObservableList<User> readOnlyFilteredUsers = FXCollections.unmodifiableObservableList(filteredUsers);
    private final BooleanProperty loginDisabled = new SimpleBooleanProperty(true);
    private final StringProperty filterText = new SimpleStringProperty("");

    // For account creation dialog
    private final StringProperty errorMessage = new SimpleStringProperty("");

    private User selectedUser;

    public LoginViewModel(UiUserStore userStore, AppConfig config, AppSession session) {
        this(userStore, config, session, new JavaFxUiThreadDispatcher(), new ProfileActivationPolicy());
    }

    public LoginViewModel(
            UiUserStore userStore, AppConfig config, AppSession session, UiThreadDispatcher uiDispatcher) {
        this(userStore, config, session, uiDispatcher, new ProfileActivationPolicy());
    }

    public LoginViewModel(
            UiUserStore userStore,
            AppConfig config,
            AppSession session,
            UiThreadDispatcher uiDispatcher,
            ProfileActivationPolicy activationPolicy) {
        super("login", uiDispatcher);
        this.userStore = Objects.requireNonNull(userStore, "userStore cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.activationPolicy = Objects.requireNonNull(activationPolicy, "activationPolicy cannot be null");

        this.filterText.addListener((obs, oldVal, newVal) -> applyFilter(newVal));
        loadUsers();
    }

    public void setErrorHandler(ViewModelErrorSink handler) {
        // LoginViewModel primarily reports inline validation through errorMessageProperty(),
        // but still delegates to BaseViewModel for consistency with other ViewModels.
        setErrorSink(handler);
    }

    /**
     * Loads the list of available users from storage.
     */
    private void loadUsers() {
        if (isDisposed()) {
            return;
        }

        asyncScope.runLatest("login-users", "load users", userStore::findAll, allUsers -> {
            if (isDisposed()) {
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
        return readOnlyUsers;
    }

    public ObservableList<User> getFilteredUsers() {
        return readOnlyFilteredUsers;
    }

    public BooleanProperty loginDisabledProperty() {
        return loginDisabled;
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

        // Set user in the global UI session
        session.setCurrentUser(selectedUser);

        return true;
    }

    public NavigationService.ViewType resolvePostLoginDestination() {
        if (selectedUser == null) {
            return NavigationService.ViewType.PROFILE;
        }

        var activationDecision = activationPolicy.canActivate(selectedUser);
        if (selectedUser.getState() == UserState.ACTIVE
                || (activationDecision instanceof datingapp.core.workflow.WorkflowDecision.Denied denied
                        && "ALREADY_ACTIVE".equals(denied.reasonCode()))) {
            return NavigationService.ViewType.DASHBOARD;
        }
        return NavigationService.ViewType.PROFILE;
    }

    /**
     * Creates a new user account with complete profile data to enable activation.
     * Returns the created user, or null if creation failed.
     */
    @Nullable
    public User createUser(String name, int age, Gender gender, Gender interestedIn) {
        errorMessage.set("");

        if (!validateCreateInput(name, age, gender, interestedIn)) {
            return null;
        }

        try {
            User newUser = new User(UUID.randomUUID(), name.trim());
            ProfileNormalizationSupport.applyMinimalBootstrap(newUser, config, age, gender, interestedIn);
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
            logError("Failed to create user: {}", e);
            errorMessage.set("Failed to create user: " + e.getMessage());
            return null;
        }
    }

    private boolean validateCreateInput(String name, int age, Gender gender, Gender interestedIn) {
        if (name == null || name.trim().isEmpty()) {
            errorMessage.set("Name cannot be empty");
            return false;
        }

        if (age < config.validation().minAge() || age > config.validation().maxAge()) {
            errorMessage.set("Age must be between " + config.validation().minAge() + " and "
                    + config.validation().maxAge());
            return false;
        }

        if (gender == null) {
            errorMessage.set("Gender must be provided");
            return false;
        }

        if (interestedIn == null) {
            errorMessage.set("InterestedIn must be provided");
            return false;
        }

        return true;
    }

    /**
     * Clears the account creation form fields.
     */
    public void clearCreateForm() {
        errorMessage.set("");
    }

    @Override
    protected void onDispose() {
        users.clear();
        filteredUsers.clear();
    }

    private void applyFilter(String text) {
        String normalized = normalizeFilter(text);
        if (normalized.isBlank()) {
            filteredUsers.setAll(users);
            return;
        }

        // Pass the injected config's timezone so age calculation is consistent
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
     */
    private static String buildSearchable(User user, java.time.ZoneId zone) {
        String name = user.getName() == null ? "" : user.getName().toLowerCase(Locale.ROOT);
        String state = user.getState() == null ? "" : user.getState().name().toLowerCase(Locale.ROOT);
        int age = UserPresentationSupport.safeAge(user, zone);
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
