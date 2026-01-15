package datingapp.ui;

import java.io.IOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton service handling navigation between different screens in the
 * application.
 * Manages the primary stage and root layout.
 */
public class NavigationService {
    private static final Logger logger = LoggerFactory.getLogger(NavigationService.class);
    private static NavigationService instance;

    private Stage primaryStage;
    private BorderPane rootLayout;
    private ViewModelFactory viewModelFactory;

    private NavigationService() {}

    public static synchronized NavigationService getInstance() {
        if (instance == null) {
            instance = new NavigationService();
        }
        return instance;
    }

    public void setViewModelFactory(ViewModelFactory viewModelFactory) {
        this.viewModelFactory = viewModelFactory;
    }

    /**
     * Initializes the service with the primary stage.
     * Sets up the root layout (BorderPane) which acts as a wrapper for all screens.
     */
    public void initialize(Stage stage) {
        this.primaryStage = stage;
        this.rootLayout = new BorderPane();

        Scene scene = new Scene(rootLayout, 900, 700);
        // Load CSS theme
        String css =
                Objects.requireNonNull(getClass().getResource("/css/theme.css")).toExternalForm();
        scene.getStylesheets().add(css);

        primaryStage.setScene(scene);
    }

    /**
     * Handles transitions to different views by loading FXML and injecting
     * ViewModels.
     */
    public void navigateTo(ViewFactory.ViewType viewType) {
        try {
            logger.info("Navigating to: {}", viewType);

            FXMLLoader loader = new FXMLLoader(getClass().getResource(viewType.getFxmlPath()));

            // Controller factory allows us to inject ViewModels created by our
            // ViewModelFactory
            loader.setControllerFactory(param -> viewModelFactory.createController(param));

            Parent view = loader.load();

            // For now, we replace the center of the root layout
            // Login screen typically replaces the entire content, other screens might keep
            // a sidebar/header
            rootLayout.setCenter(view);

            // Future: add sidebar/header navigation widgets here if not LOGIN

        } catch (IOException e) {
            logger.error("Failed to navigate to {}: {}", viewType, e.getMessage(), e);
            // Stack trace already logged above
        }
    }

    /**
     * Navigates back to the previous screen.
     * Currently simple implementation: always goes to Dashboard (or could track
     * history).
     * For now, effectively "Close" or "Dashboard".
     */
    public void goBack() {
        // Simple fallback: Go to Matchng screen or Dashboard.
        // Since Preferences is accessed from Matching, let's go there.
        // A better implementation would use a Stack<ViewType>.
        // Check if we want to go back to Matching or Dashboard.
        // Let's safe default to DASHBOARD for generic "Back",
        // or specifically MATCHING since we came from there.
        // The user request context implies coming from Matching.
        navigateTo(ViewFactory.ViewType.MATCHING);
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public ViewModelFactory getViewModelFactory() {
        return viewModelFactory;
    }
}
