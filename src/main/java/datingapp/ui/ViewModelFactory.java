package datingapp.ui;

import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.ui.controller.ChatController;
import datingapp.ui.controller.DashboardController;
import datingapp.ui.controller.LoginController;
import datingapp.ui.controller.MatchesController;
import datingapp.ui.controller.MatchingController;
import datingapp.ui.controller.PreferencesController;
import datingapp.ui.controller.ProfileController;
import datingapp.ui.controller.StatsController;
import datingapp.ui.viewmodel.ChatViewModel;
import datingapp.ui.viewmodel.DashboardViewModel;
import datingapp.ui.viewmodel.LoginViewModel;
import datingapp.ui.viewmodel.MatchesViewModel;
import datingapp.ui.viewmodel.MatchingViewModel;
import datingapp.ui.viewmodel.PreferencesViewModel;
import datingapp.ui.viewmodel.ProfileViewModel;
import datingapp.ui.viewmodel.StatsViewModel;
import datingapp.ui.viewmodel.data.UiDataAdapters.StorageUiMatchDataAccess;
import datingapp.ui.viewmodel.data.UiDataAdapters.StorageUiUserStore;
import datingapp.ui.viewmodel.data.UiDataAdapters.UiMatchDataAccess;
import datingapp.ui.viewmodel.data.UiDataAdapters.UiUserStore;
import java.lang.reflect.InvocationTargetException;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating ViewModels and Controllers with proper dependency
 * injection.
 * This is the bridge between the core ServiceRegistry and the UI layer.
 */
public class ViewModelFactory {

    private static final Logger logger = LoggerFactory.getLogger(ViewModelFactory.class);

    private final ServiceRegistry services;

    /**
     * JavaFX-compatible wrapper for AppSession.
     * Provides an ObjectProperty that synchronizes with the global AppSession
     * singleton.
     */
    private final ObjectProperty<User> currentUserProperty = new SimpleObjectProperty<>();

    // Cached ViewModels (lazy-initialized singletons within UI context)
    private LoginViewModel loginViewModel;
    private DashboardViewModel dashboardViewModel;
    private ProfileViewModel profileViewModel;
    private MatchingViewModel matchingViewModel;
    private MatchesViewModel matchesViewModel;
    private ChatViewModel chatViewModel;
    private StatsViewModel statsViewModel;
    private PreferencesViewModel preferencesViewModel;

    public ViewModelFactory(ServiceRegistry services) {
        this.services = services;
        initializeSessionBinding();
    }

    /**
     * Binds the JavaFX currentUserProperty to the global AppSession.
     * Updates are pushed to the UI thread for thread-safety.
     */
    private void initializeSessionBinding() {
        // Listen to AppSession changes and update UI property on JavaFX thread
        AppSession.getInstance().addListener(user -> Platform.runLater(() -> currentUserProperty.set(user)));
        // Sync initial state
        currentUserProperty.set(AppSession.getInstance().getCurrentUser());
    }

    /**
     * Gets the current user property for JavaFX binding.
     * This property automatically synchronizes with AppSession.
     */
    public ObjectProperty<User> currentUserProperty() {
        return currentUserProperty;
    }

    /**
     * Creates controllers for FXML loading.
     * Maps controller classes to their initialized instances with ViewModels.
     */
    public Object createController(Class<?> controllerClass) {
        logDebug("Creating controller: {}", controllerClass.getSimpleName());

        if (controllerClass == LoginController.class) {
            return new LoginController(getLoginViewModel(), services.getProfileService());
        }

        if (controllerClass == DashboardController.class) {
            return new DashboardController(getDashboardViewModel());
        }

        if (controllerClass == ProfileController.class) {
            return new ProfileController(getProfileViewModel());
        }

        if (controllerClass == MatchingController.class) {
            return new MatchingController(getMatchingViewModel());
        }

        if (controllerClass == MatchesController.class) {
            return new MatchesController(getMatchesViewModel());
        }

        if (controllerClass == ChatController.class) {
            return new ChatController(getChatViewModel());
        }

        if (controllerClass == StatsController.class) {
            return new StatsController(getStatsViewModel());
        }

        if (controllerClass == PreferencesController.class) {
            return new PreferencesController(getPreferencesViewModel());
        }

        // Fallback for controllers that don't need DI or are not yet mapped
        return createFallbackController(controllerClass);
    }

    private Object createFallbackController(Class<?> controllerClass) {
        try {
            return controllerClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            String controllerName = controllerClass.getName();
            logError("Failed to create fallback controller: {}", controllerName, e);
            throw new IllegalStateException("Failed to create fallback controller: " + controllerName, e);
        }
    }

    // --- ViewModel Accessors (lazy initialization) ---

    public synchronized LoginViewModel getLoginViewModel() {
        if (loginViewModel == null) {
            loginViewModel = new LoginViewModel(createUiUserStore());
        }
        return loginViewModel;
    }

    public synchronized DashboardViewModel getDashboardViewModel() {
        if (dashboardViewModel == null) {
            dashboardViewModel = new DashboardViewModel(
                    services.getRecommendationService(),
                    createUiMatchDataAccess(),
                    services.getProfileService(),
                    services.getConnectionService(),
                    services.getProfileService());
        }
        return dashboardViewModel;
    }

    public synchronized ProfileViewModel getProfileViewModel() {
        if (profileViewModel == null) {
            profileViewModel = new ProfileViewModel(createUiUserStore(), services.getProfileService());
        }
        return profileViewModel;
    }

    public synchronized MatchingViewModel getMatchingViewModel() {
        if (matchingViewModel == null) {
            matchingViewModel = new MatchingViewModel(
                    services.getCandidateFinder(), services.getMatchingService(), services.getUndoService());
        }
        return matchingViewModel;
    }

    public synchronized MatchesViewModel getMatchesViewModel() {
        if (matchesViewModel == null) {
            matchesViewModel = new MatchesViewModel(
                    createUiMatchDataAccess(),
                    createUiUserStore(),
                    services.getMatchingService(),
                    services.getRecommendationService());
        }
        return matchesViewModel;
    }

    public synchronized ChatViewModel getChatViewModel() {
        if (chatViewModel == null) {
            // ChatViewModel takes only ConnectionService
            chatViewModel = new ChatViewModel(services.getConnectionService());
        }
        return chatViewModel;
    }

    public synchronized StatsViewModel getStatsViewModel() {
        if (statsViewModel == null) {
            statsViewModel = new StatsViewModel(services.getProfileService(), services.getActivityMetricsService());
        }
        return statsViewModel;
    }

    public synchronized PreferencesViewModel getPreferencesViewModel() {
        if (preferencesViewModel == null) {
            preferencesViewModel = new PreferencesViewModel(createUiUserStore());
        }
        return preferencesViewModel;
    }

    /**
     * Resets all cached ViewModels. Useful when logging out.
     * Disposes each ViewModel before clearing to prevent memory leaks (UI-04).
     */
    public synchronized void reset() {
        // Dispose ViewModels that have dispose() method before nulling references
        if (loginViewModel != null) {
            loginViewModel.dispose();
            loginViewModel = null;
        }
        if (dashboardViewModel != null) {
            dashboardViewModel.dispose();
            dashboardViewModel = null;
        }
        if (profileViewModel != null) {
            profileViewModel.dispose();
            profileViewModel = null;
        }
        if (matchingViewModel != null) {
            matchingViewModel.dispose();
            matchingViewModel = null;
        }
        if (matchesViewModel != null) {
            matchesViewModel.dispose();
            matchesViewModel = null;
        }
        if (chatViewModel != null) {
            chatViewModel.dispose();
            chatViewModel = null;
        }
        if (statsViewModel != null) {
            statsViewModel.dispose();
            statsViewModel = null;
        }
        if (preferencesViewModel != null) {
            preferencesViewModel.dispose();
            preferencesViewModel = null;
        }
        logDebug("All ViewModels disposed and reset");
    }

    private UiUserStore createUiUserStore() {
        return new StorageUiUserStore(services.getUserStorage());
    }

    private UiMatchDataAccess createUiMatchDataAccess() {
        return new StorageUiMatchDataAccess(services.getInteractionStorage(), services.getTrustSafetyStorage());
    }

    private void logDebug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, args);
        }
    }

    private void logError(String message, Object... args) {
        if (logger.isErrorEnabled()) {
            logger.error(message, args);
        }
    }
}
