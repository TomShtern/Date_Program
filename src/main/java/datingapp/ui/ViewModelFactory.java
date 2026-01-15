package datingapp.ui;

import datingapp.core.ServiceRegistry;
import datingapp.ui.controller.ChatController;
import datingapp.ui.controller.DashboardController;
import datingapp.ui.controller.LoginController;
import datingapp.ui.controller.MatchesController;
import datingapp.ui.controller.MatchingController;
import datingapp.ui.controller.ProfileController;
import datingapp.ui.controller.StatsController;
import datingapp.ui.viewmodel.ChatViewModel;
import datingapp.ui.viewmodel.DashboardViewModel;
import datingapp.ui.viewmodel.LoginViewModel;
import datingapp.ui.viewmodel.MatchesViewModel;
import datingapp.ui.viewmodel.MatchingViewModel;
import datingapp.ui.viewmodel.ProfileViewModel;
import datingapp.ui.viewmodel.StatsViewModel;
import java.lang.reflect.InvocationTargetException;
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

    // Cached ViewModels (lazy-initialized singletons within UI context)
    private LoginViewModel loginViewModel;
    private DashboardViewModel dashboardViewModel;
    private ProfileViewModel profileViewModel;
    private MatchingViewModel matchingViewModel;
    private MatchesViewModel matchesViewModel;
    private ChatViewModel chatViewModel;
    private StatsViewModel statsViewModel;

    public ViewModelFactory(ServiceRegistry services) {
        this.services = services;
    }

    /**
     * Creates controllers for FXML loading.
     * Maps controller classes to their initialized instances with ViewModels.
     */
    public Object createController(Class<?> controllerClass) {
        logger.debug("Creating controller: {}", controllerClass.getSimpleName());

        if (controllerClass == LoginController.class) {
            return new LoginController(getLoginViewModel());
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

        // Fallback for controllers that don't need DI or are not yet mapped
        return createFallbackController(controllerClass);
    }

    @SuppressWarnings("java:S1166") // We log and rethrow with context
    private Object createFallbackController(Class<?> controllerClass) {
        try {
            return controllerClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            logger.error("Failed to create controller {}", controllerClass, e);
            throw new IllegalStateException("Failed to create controller: " + controllerClass, e);
        }
    }

    // --- ViewModel Accessors (lazy initialization) ---

    public LoginViewModel getLoginViewModel() {
        if (loginViewModel == null) {
            loginViewModel = new LoginViewModel(services.getUserStorage());
        }
        return loginViewModel;
    }

    public DashboardViewModel getDashboardViewModel() {
        if (dashboardViewModel == null) {
            dashboardViewModel = new DashboardViewModel(
                    services.getDailyPickService(),
                    services.getMatchStorage(),
                    services.getDailyLimitService(),
                    services.getAchievementService());
        }
        return dashboardViewModel;
    }

    public ProfileViewModel getProfileViewModel() {
        if (profileViewModel == null) {
            // ProfileViewModel takes (UserStorage, ProfileCompletionService)
            // ProfileCompletionService methods are static, so we pass null
            profileViewModel = new ProfileViewModel(services.getUserStorage(), null);
        }
        return profileViewModel;
    }

    public MatchingViewModel getMatchingViewModel() {
        if (matchingViewModel == null) {
            // MatchingViewModel takes 6 params (no MatchQualityService)
            matchingViewModel = new MatchingViewModel(
                    services.getCandidateFinder(),
                    services.getMatchingService(),
                    services.getUserStorage(),
                    services.getLikeStorage(),
                    services.getBlockStorage(),
                    services.getUndoService());
        }
        return matchingViewModel;
    }

    public MatchesViewModel getMatchesViewModel() {
        if (matchesViewModel == null) {
            matchesViewModel = new MatchesViewModel(services.getMatchStorage(), services.getUserStorage());
        }
        return matchesViewModel;
    }

    public ChatViewModel getChatViewModel() {
        if (chatViewModel == null) {
            // ChatViewModel takes only MessagingService
            chatViewModel = new ChatViewModel(services.getMessagingService());
        }
        return chatViewModel;
    }

    public StatsViewModel getStatsViewModel() {
        if (statsViewModel == null) {
            // StatsViewModel with AchievementService, LikeStorage, and MatchStorage for
            // real stats
            statsViewModel = new StatsViewModel(
                    services.getAchievementService(), services.getLikeStorage(), services.getMatchStorage());
        }
        return statsViewModel;
    }

    /**
     * Resets all cached ViewModels. Useful when logging out.
     */
    public void reset() {
        loginViewModel = null;
        dashboardViewModel = null;
        profileViewModel = null;
        matchingViewModel = null;
        chatViewModel = null;
        matchesViewModel = null;
        statsViewModel = null;
    }
}
