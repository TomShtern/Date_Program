package datingapp.ui.viewmodel;

import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.ui.screen.ChatController;
import datingapp.ui.screen.DashboardController;
import datingapp.ui.screen.LoginController;
import datingapp.ui.screen.MatchesController;
import datingapp.ui.screen.MatchingController;
import datingapp.ui.screen.PreferencesController;
import datingapp.ui.screen.ProfileController;
import datingapp.ui.screen.StatsController;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiMatchDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.StorageUiUserStore;
import datingapp.ui.viewmodel.UiDataAdapters.UiMatchDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

    private Consumer<User> sessionListener;

    // Cached ViewModels (lazy-initialized singletons within UI context)
    private LoginViewModel loginViewModel;
    private DashboardViewModel dashboardViewModel;
    private ProfileViewModel profileViewModel;
    private MatchingViewModel matchingViewModel;
    private MatchesViewModel matchesViewModel;
    private ChatViewModel chatViewModel;
    private StatsViewModel statsViewModel;
    private PreferencesViewModel preferencesViewModel;
    private final Map<Class<?>, Supplier<Object>> controllerFactories;

    public ViewModelFactory(ServiceRegistry services) {
        this.services = services;
        this.controllerFactories = buildControllerFactories();
        initializeSessionBinding();
    }

    /**
     * Binds the JavaFX currentUserProperty to the global AppSession.
     * Updates are pushed to the UI thread for thread-safety.
     */
    private void initializeSessionBinding() {
        // Listen to AppSession changes and update UI property on JavaFX thread
        sessionListener = user -> Platform.runLater(() -> currentUserProperty.set(user));
        AppSession.getInstance().addListener(sessionListener);
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
    private Map<Class<?>, Supplier<Object>> buildControllerFactories() {
        Map<Class<?>, Supplier<Object>> map = new HashMap<>();
        map.put(LoginController.class, () -> new LoginController(getLoginViewModel(), services.getProfileService()));
        map.put(DashboardController.class, () -> new DashboardController(getDashboardViewModel()));
        map.put(ProfileController.class, () -> new ProfileController(getProfileViewModel()));
        map.put(MatchingController.class, () -> new MatchingController(getMatchingViewModel()));
        map.put(MatchesController.class, () -> new MatchesController(getMatchesViewModel()));
        map.put(ChatController.class, () -> new ChatController(getChatViewModel()));
        map.put(StatsController.class, () -> new StatsController(getStatsViewModel()));
        map.put(PreferencesController.class, () -> new PreferencesController(getPreferencesViewModel()));
        return Collections.unmodifiableMap(map);
    }

    public Object createController(Class<?> controllerClass) {
        logDebug("Creating controller: {}", controllerClass.getSimpleName());
        Supplier<Object> factory = controllerFactories.get(controllerClass);
        return factory != null ? factory.get() : createFallbackController(controllerClass);
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
            loginViewModel = new LoginViewModel(createUiUserStore(), services.getConfig());
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
            profileViewModel =
                    new ProfileViewModel(createUiUserStore(), services.getProfileService(), services.getConfig());
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
            preferencesViewModel = new PreferencesViewModel(createUiUserStore(), services.getConfig());
        }
        return preferencesViewModel;
    }

    /**
     * Resets all cached ViewModels. Useful when logging out.
     * Disposes each ViewModel before clearing to prevent memory leaks (UI-04).
     */
    private static <T> T quietly(T vm, Consumer<T> disposer) {
        if (vm != null) {
            disposer.accept(vm);
        }
        return null;
    }

    public synchronized void reset() {
        if (sessionListener != null) {
            AppSession.getInstance().removeListener(sessionListener);
            sessionListener = null;
        }

        loginViewModel = quietly(loginViewModel, LoginViewModel::dispose);
        dashboardViewModel = quietly(dashboardViewModel, DashboardViewModel::dispose);
        profileViewModel = quietly(profileViewModel, ProfileViewModel::dispose);
        matchingViewModel = quietly(matchingViewModel, MatchingViewModel::dispose);
        matchesViewModel = quietly(matchesViewModel, MatchesViewModel::dispose);
        chatViewModel = quietly(chatViewModel, ChatViewModel::dispose);
        statsViewModel = quietly(statsViewModel, StatsViewModel::dispose);
        preferencesViewModel = quietly(preferencesViewModel, PreferencesViewModel::dispose);
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
