package datingapp.ui.viewmodel;

import datingapp.core.AppSession;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import datingapp.ui.NavigationService;
import datingapp.ui.UiPreferencesStore;
import datingapp.ui.UiThemeService;
import datingapp.ui.async.JavaFxUiThreadDispatcher;
import datingapp.ui.async.UiThreadDispatcher;
import datingapp.ui.screen.ChatController;
import datingapp.ui.screen.DashboardController;
import datingapp.ui.screen.LoginController;
import datingapp.ui.screen.MatchesController;
import datingapp.ui.screen.MatchingController;
import datingapp.ui.screen.NotesController;
import datingapp.ui.screen.PreferencesController;
import datingapp.ui.screen.ProfileController;
import datingapp.ui.screen.ProfileViewController;
import datingapp.ui.screen.SafetyController;
import datingapp.ui.screen.SocialController;
import datingapp.ui.screen.StandoutsController;
import datingapp.ui.screen.StatsController;
import datingapp.ui.viewmodel.UiDataAdapters.UiProfileNoteDataAccess;
import datingapp.ui.viewmodel.UiDataAdapters.UiUserStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
    private final AppSession session;
    private final UiThreadDispatcher uiDispatcher;
    private final UiPreferencesStore uiPreferencesStore;

    /**
     * JavaFX-compatible wrapper for AppSession.
     * Provides an ObjectProperty that synchronizes with the global AppSession
     * singleton.
     */
    private final ObjectProperty<User> currentUserProperty = new SimpleObjectProperty<>();

    private Consumer<User> sessionListener;

    // Generic cache for ViewModels (lazy-initialized singletons within UI context)
    private final Map<Class<?>, Object> viewModelCache = new HashMap<>();

    // Adapter cache — delegates lifecycle management of shared UI adapters
    private final UiAdapterCache uiAdapterCache = new UiAdapterCache();

    private final Map<Class<?>, Supplier<Object>> controllerFactories;

    public ViewModelFactory(ServiceRegistry services) {
        this(services, AppSession.getInstance());
    }

    public ViewModelFactory(ServiceRegistry services, AppSession session) {
        this.services = Objects.requireNonNull(services, "services cannot be null");
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.uiDispatcher = new JavaFxUiThreadDispatcher();
        this.uiPreferencesStore = new UiPreferencesStore();
        this.controllerFactories = buildControllerFactories();
        initializeSessionBinding();
    }

    /**
     * Binds the JavaFX currentUserProperty to the global AppSession.
     * Updates are pushed to the UI thread for thread-safety.
     */
    private void initializeSessionBinding() {
        // Listen to AppSession changes and update UI property on JavaFX thread
        sessionListener = user -> uiDispatcher.dispatch(() -> currentUserProperty.set(user));
        session.addListener(sessionListener);
        // Sync initial state
        currentUserProperty.set(session.getCurrentUser());
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
        map.put(
                LoginController.class,
                () -> new LoginController(
                        getLoginViewModel(),
                        services.getProfileService(),
                        services.getConfig().safety().userTimeZone()));
        map.put(DashboardController.class, () -> new DashboardController(getDashboardViewModel()));
        map.put(ProfileController.class, () -> new ProfileController(getProfileViewModel()));
        map.put(ProfileViewController.class, () -> new ProfileViewController(getProfileReadOnlyViewModel()));
        map.put(
                MatchingController.class,
                () -> new MatchingController(
                        getMatchingViewModel(), services.getConfig().safety().userTimeZone()));
        map.put(MatchesController.class, () -> new MatchesController(getMatchesViewModel()));
        map.put(
                ChatController.class,
                () -> new ChatController(
                        getChatViewModel(), services.getConfig().safety().userTimeZone()));
        map.put(StatsController.class, () -> new StatsController(getStatsViewModel()));
        map.put(PreferencesController.class, () -> new PreferencesController(getPreferencesViewModel()));
        map.put(StandoutsController.class, () -> new StandoutsController(getStandoutsViewModel()));
        map.put(
                SocialController.class,
                () -> new SocialController(
                        getSocialViewModel(), services.getConfig().safety().userTimeZone()));
        map.put(SafetyController.class, () -> new SafetyController(getSafetyViewModel()));
        map.put(NotesController.class, () -> new NotesController(getNotesViewModel()));
        return Collections.unmodifiableMap(map);
    }

    public UiPreferencesStore getPreferencesStore() {
        return uiPreferencesStore;
    }

    public Object createController(Class<?> controllerClass) {
        logDebug("Creating controller: {}", controllerClass.getSimpleName());
        Supplier<Object> factory = controllerFactories.get(controllerClass);
        if (factory == null) {
            String controllerName = controllerClass.getName();
            throw new IllegalStateException("No controller factory registered for: " + controllerName);
        }
        return factory.get();
    }

    // --- ViewModel Accessors (lazy initialization) ---

    @SuppressWarnings("unchecked")
    private synchronized <T> T getViewModel(Class<T> type, Supplier<T> factory) {
        return (T) viewModelCache.computeIfAbsent(type, k -> factory.get());
    }

    public LoginViewModel getLoginViewModel() {
        return getViewModel(
                LoginViewModel.class,
                () -> new LoginViewModel(
                        getUiUserStore(), services.getConfig(), session, uiDispatcher, services.getActivationPolicy()));
    }

    public DashboardViewModel getDashboardViewModel() {
        return getViewModel(
                DashboardViewModel.class,
                () -> new DashboardViewModel(
                        new DashboardViewModel.Dependencies(services.getDashboardUseCases()),
                        session,
                        uiDispatcher,
                        uiPreferencesStore));
    }

    public ProfileViewModel getProfileViewModel() {
        return getViewModel(
                ProfileViewModel.class,
                () -> new ProfileViewModel(new ProfileViewModel.Dependencies(
                        getUiUserStore(),
                        services.getProfileService(),
                        services.getProfileMutationUseCases(),
                        services.getProfileUseCases(),
                        services.getConfig(),
                        session,
                        services.getValidationService(),
                        services.getLocationService(),
                        uiDispatcher,
                        services.getActivationPolicy())));
    }

    public ProfileReadOnlyViewModel getProfileReadOnlyViewModel() {
        return getViewModel(
                ProfileReadOnlyViewModel.class,
                () -> new ProfileReadOnlyViewModel(getUiUserStore(), services.getConfig(), uiDispatcher));
    }

    public MatchingViewModel getMatchingViewModel() {
        return getViewModel(
                MatchingViewModel.class,
                () -> new MatchingViewModel(
                        new MatchingViewModel.Dependencies(
                                services.getCandidateFinder(),
                                services.getMatchingService(),
                                services.getUndoService(),
                                services.getTrustSafetyService(),
                                services.getMatchingUseCases(),
                                services.getSocialUseCases(),
                                getUiProfileNoteDataAccess()),
                        session,
                        uiDispatcher));
    }

    public MatchesViewModel getMatchesViewModel() {
        return getViewModel(
                MatchesViewModel.class,
                () -> new MatchesViewModel(
                        new MatchesViewModel.Dependencies(
                                services.getRecommendationService(),
                                services.getMatchingUseCases(),
                                services.getProfileUseCases(),
                                services.getSocialUseCases(),
                                services.getConfig()),
                        session,
                        uiDispatcher));
    }

    public ChatViewModel getChatViewModel() {
        return getViewModel(
                ChatViewModel.class,
                () -> new ChatViewModel(
                        services.getMessagingUseCases(),
                        services.getSocialUseCases(),
                        session,
                        uiDispatcher,
                        java.time.Duration.ofSeconds(
                                services.getConfig().validation().chatBackgroundPollSeconds()),
                        java.time.Duration.ofSeconds(
                                services.getConfig().validation().chatActivePollSeconds()),
                        new ChatViewModel.ChatUiDependencies(getUiProfileNoteDataAccess(), getUiPresenceDataAccess())));
    }

    public StatsViewModel getStatsViewModel() {
        return getViewModel(
                StatsViewModel.class,
                () -> new StatsViewModel(
                        services.getActivityMetricsService(),
                        services.getConnectionService(),
                        services.getProfileUseCases(),
                        session,
                        datingapp.core.AppClock.clock(),
                        uiDispatcher));
    }

    public PreferencesViewModel getPreferencesViewModel() {
        return getViewModel(
                PreferencesViewModel.class,
                () -> new PreferencesViewModel(
                        services.getProfileMutationUseCases(),
                        new UiThemeService(uiPreferencesStore, NavigationService.getInstance()),
                        services.getConfig(),
                        session,
                        uiDispatcher));
    }

    public StandoutsViewModel getStandoutsViewModel() {
        return getViewModel(
                StandoutsViewModel.class,
                () -> new StandoutsViewModel(
                        services.getRecommendationService(), services.getMatchingUseCases(), session, uiDispatcher));
    }

    public SocialViewModel getSocialViewModel() {
        return getViewModel(
                SocialViewModel.class,
                () -> new SocialViewModel(
                        services.getConnectionService(),
                        uiAdapterCache.socialDataAccess(services),
                        getUiUserStore(),
                        services.getSocialUseCases(),
                        session,
                        uiDispatcher));
    }

    public SafetyViewModel getSafetyViewModel() {
        return getViewModel(
                SafetyViewModel.class,
                () -> new SafetyViewModel(
                        services.getSocialUseCases(),
                        services.getVerificationUseCases(),
                        services.getProfileMutationUseCases(),
                        session,
                        uiDispatcher));
    }

    public NotesViewModel getNotesViewModel() {
        return getViewModel(
                NotesViewModel.class,
                () -> new NotesViewModel(
                        services.getProfileNotesUseCases(),
                        getUiUserStore(),
                        session,
                        services.getConfig().safety().userTimeZone(),
                        uiDispatcher));
    }

    /**
     * Resets all cached ViewModels. Useful when logging out.
     * Disposes each ViewModel before clearing to prevent memory leaks (UI-04).
     */
    public synchronized void reset() {
        unbindSessionListener();
        disposeCachedViewModels();
        initializeSessionBinding();
        logDebug("All ViewModels disposed and cache cleared");
    }

    /**
     * Permanently disposes this factory instance.
     *
     * <p>Removes the AppSession listener and disposes cached ViewModels without
     * re-binding. Intended for application shutdown.
     */
    public synchronized void dispose() {
        unbindSessionListener();
        disposeCachedViewModels();
        currentUserProperty.set(null);
        logDebug("ViewModelFactory disposed");
    }

    private void unbindSessionListener() {
        if (sessionListener != null) {
            session.removeListener(sessionListener);
            sessionListener = null;
        }
    }

    private void disposeCachedViewModels() {
        viewModelCache.values().forEach(vm -> {
            if (vm instanceof BaseViewModel bvm) {
                bvm.dispose();
                return;
            }

            String typeName = vm.getClass().getName();
            throw new IllegalStateException("Cached ViewModel does not extend BaseViewModel: " + typeName);
        });

        viewModelCache.clear();
        uiAdapterCache.reset();
    }

    private UiUserStore getUiUserStore() {
        return uiAdapterCache.userStore(services);
    }

    private UiProfileNoteDataAccess getUiProfileNoteDataAccess() {
        return uiAdapterCache.profileNotes(services);
    }

    private UiDataAdapters.UiPresenceDataAccess getUiPresenceDataAccess() {
        return uiAdapterCache.presence(services);
    }

    private void logDebug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug(message, args);
        }
    }
}
