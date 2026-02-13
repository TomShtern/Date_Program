package datingapp.ui;

import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.core.ServiceRegistry;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main JavaFX Application entry point for the Dating App.
 * Handles bootstrapping of core services and initialization of the primary
 * stage.
 */
public class DatingApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(DatingApp.class);

    private NavigationService navigationService;

    @Override
    public void init() throws Exception {
        logger.info("Initializing Dating App GUI...");

        // Initialize application with centralized bootstrap
        logger.info("[DEBUG] Initializing application services...");
        ServiceRegistry serviceRegistry = ApplicationStartup.initialize();
        logger.info("[DEBUG] ServiceRegistry initialized.");

        // Initialize UI framework components
        logger.info("[DEBUG] Creating ViewModelFactory...");
        ViewModelFactory viewModelFactory = new ViewModelFactory(serviceRegistry);
        logger.info("[DEBUG] ViewModelFactory created.");

        logger.info("[DEBUG] Setting up NavigationService...");
        this.navigationService = NavigationService.getInstance();
        this.navigationService.setViewModelFactory(viewModelFactory);
        logger.info("[DEBUG] NavigationService ready.");

        logger.info("Application services and UI foundation initialized.");
    }

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting JavaFX Primary Stage...");

        // Setup the navigation service with our primary stage
        navigationService.initialize(primaryStage);

        // Set window constraints
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        primaryStage.setMaxWidth(1600);
        primaryStage.setMaxHeight(1000);

        // Default size
        primaryStage.setWidth(1000);
        primaryStage.setHeight(760);

        // Show the initial screen (Login)
        navigationService.navigateTo(NavigationService.ViewType.LOGIN);

        primaryStage.setTitle("🌹 Dating App");
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    @Override
    public void stop() {
        logger.info("Shutting down Dating App GUI...");
        ApplicationStartup.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
