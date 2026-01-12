package datingapp.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.ServiceRegistryBuilder;
import datingapp.storage.DatabaseManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Main JavaFX Application entry point for the Dating App.
 * Handles bootstrapping of core services and initialization of the primary
 * stage.
 */
public class DatingApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(DatingApp.class);

    private ServiceRegistry serviceRegistry;
    private NavigationService navigationService;
    private ViewModelFactory viewModelFactory;

    @Override
    public void init() throws Exception {
        logger.info("Initializing Dating App GUI...");

        // 1. Initialize core infrastructure (same as Main.java)
        logger.info("[DEBUG] Step 1: Loading AppConfig...");
        AppConfig config = AppConfig.defaults();
        logger.info("[DEBUG] Step 1: AppConfig loaded.");

        logger.info("[DEBUG] Step 2: Getting DatabaseManager instance...");
        DatabaseManager dbManager = DatabaseManager.getInstance();
        logger.info("[DEBUG] Step 2: DatabaseManager obtained.");

        // 2. Wire up all services
        logger.info("[DEBUG] Step 3: Building ServiceRegistry...");
        this.serviceRegistry = ServiceRegistryBuilder.buildH2(dbManager, config);
        logger.info("[DEBUG] Step 3: ServiceRegistry built.");

        // 3. Initialize UI framework components
        logger.info("[DEBUG] Step 4: Creating ViewModelFactory...");
        this.viewModelFactory = new ViewModelFactory(serviceRegistry);
        logger.info("[DEBUG] Step 4: ViewModelFactory created.");

        logger.info("[DEBUG] Step 5: Getting NavigationService...");
        this.navigationService = NavigationService.getInstance();
        this.navigationService.setViewModelFactory(viewModelFactory);
        logger.info("[DEBUG] Step 5: NavigationService ready.");

        logger.info("Legacy services and UI foundation warmed up.");
    }

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting JavaFX Primary Stage...");

        // Setup the navigation service with our primary stage
        navigationService.initialize(primaryStage);

        // Show the initial screen (Login)
        navigationService.navigateTo(ViewFactory.ViewType.LOGIN);

        primaryStage.setTitle("🌹 Dating App");
        primaryStage.show();
    }

    @Override
    public void stop() {
        logger.info("Shutting down Dating App GUI...");
        // Cleanup resources if necessary
    }

    public static void main(String[] args) {
        launch(args);
    }
}
